"""Furnish halls and towers with usable zones, partition openings and access QA.

Furniture is original Minecraft geometry. Containers have no inventory payload;
shrine fixtures are neutral pedestals, not reconstructed ritual arrangements.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

from generate_building import make_building, verify as verify_building
from generate_courtyard import walk_paths, can_support_dry_walk
from generate_tower import make_tower
from scene_output import export_scene
from voxel_model import join_state, split_state


USES = {
    'house': ['bed', 'storage'], 'shop': ['counter', 'storage'],
    'apothecary': ['shelf', 'counter'], 'inn': ['bed', 'storage'],
    'classroom': ['desk', 'shelf'], 'library': ['shelf', 'desk'],
    'office': ['desk', 'storage'], 'workshop': ['workbench', 'storage'],
    'kitchen': ['stove', 'storage'], 'granary': ['storage', 'storage'],
    'shrine': ['pedestal', 'bench'], 'refectory': ['table', 'bench'],
    'bell': ['bell', 'bench'], 'drum': ['drum', 'bench'],
}
DEFAULT = {'schema': 'chinese-inhabited/1', 'kind': 'hall', 'use': 'house',
           'building': {}, 'partition': True, 'furniture_per_zone': 2,
           'partition_block': 'minecraft:dark_oak_planks', 'evidence': []}


def fixture(kind, x, y, z):
    """Return explicit states and the adjacent standing position."""
    single = {
        'storage': 'minecraft:barrel[facing=north,open=false]',
        'desk': 'minecraft:lectern[facing=north,has_book=false,powered=false]',
        'workbench': 'minecraft:crafting_table',
        'stove': 'minecraft:furnace[facing=north,lit=false]',
        'bell': 'minecraft:bell[attachment=floor,facing=north,powered=false]',
        'drum': 'minecraft:note_block[instrument=basedrum,note=0,powered=false]',
        'bench': 'minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]',
        'table': 'minecraft:dark_oak_slab[type=top,waterlogged=false]',
        'counter': 'minecraft:dark_oak_planks',
        'pedestal': 'minecraft:chiseled_stone_bricks',
        'shelf': 'minecraft:bookshelf',
    }
    if kind == 'bed':
        cells = [(x, y, z, 'minecraft:red_bed[facing=south,occupied=false,part=foot]'),
                 (x, y, z + 1, 'minecraft:red_bed[facing=south,occupied=false,part=head]')]
    else:
        cells = [(x, y, z, single[kind])]
        if kind == 'shelf': cells.append((x, y + 1, z, single[kind]))
        if kind in ('table', 'counter', 'bench'):
            cells.append((x + 1, y, z, single[kind]))
    return cells, [x, y - 1, z - 1]


def make_inhabited(user):
    if not isinstance(user, dict) or set(user) - set(DEFAULT):
        raise ValueError('Unknown inhabited-building keys')
    c = copy.deepcopy(DEFAULT); c.update(user)
    if c['schema'] != DEFAULT['schema'] or c['kind'] not in ('hall', 'tower'):
        raise ValueError('Inhabited kind must be hall or tower')
    if c['use'] not in USES or not isinstance(c['building'], dict):
        raise ValueError('Unknown use or invalid building configuration')
    if type(c['partition']) is not bool or type(c['furniture_per_zone']) is not int or not 1 <= c['furniture_per_zone'] <= 8:
        raise ValueError('partition is boolean; furniture_per_zone must be 1..8')
    base, props = split_state(c['partition_block'])
    if not can_support_dry_walk(c['partition_block']) or base.endswith(('_stairs', '_slab')):
        raise ValueError('Use a full, solid partition material')
    partition_state = join_state(base, props)
    if c['kind'] == 'hall':
        # Inhabited rooms default to bounded windows. Explicit existing lattice,
        # plain or other facade choices retain their original meaning.
        c['building'] = {**c['building']}
        if 'facade_pattern' not in c['building']:
            c['building']['facade_pattern'] = 'framed'
        m, original, conf = make_building(c['building'])
        c['building'] = conf
        if conf['kind'] != 'hall': raise ValueError('A furnished hall needs enclosing walls')
        base_checks = verify_building(m, original, conf)
        door = original['doorway']; aisle = door['aisle_x']
        levels = [{'index': 0, 'floor_y': conf['platform_height'], 'height': conf['column_height'],
                   'half_x': original['wall_half_x'], 'half_z': original['wall_half_z']}]
        start = [aisle, 0, -original['platform_half_z'] - conf['platform_height']]
        targets = {}; core = None
    else:
        m, original = make_tower(c['building'])
        base_checks = original['checks'].copy(); levels = original['levels']; aisle = 2
        targets = {key: route[-1] for key, route in original['routes']['routes_floor_xyz'].items()}
        start = original['routes']['routes_floor_xyz']['floor_1'][0]
        core = original['stair_core_xz']
    zones = []; partition_cells = []
    for lev in levels:
        fy, hx, hz, index = lev['floor_y'], lev['half_x'], lev['half_z'], lev['index']
        if hx < 7 or hz < 5: raise ValueError('Furnished interiors require half spans X>=7 and Z>=5')
        if c['partition']:
            for x in range(-hx + 1, hx):
                if abs(x - aisle) <= 1 or (core and core[0] - 2 <= x <= core[2] + 2): continue
                for y in range(fy + 1, fy + min(lev['height'] - 1, 5) + 1):
                    if m.get(x, y, 0) == 'minecraft:air':
                        m.set(x, y, 0, partition_state); partition_cells.append([x, y, 0])
        for side, zlo, zhi in [('front', -hz + 1, -1), ('back', 1, hz - 1)]:
            ident = f'floor_{index + 1}_{side}'
            targets[ident] = [aisle, fy, (zlo + zhi) // 2]
            zones.append({'id': ident, 'floor': index + 1, 'use': c['use'],
                          'bounds_xz': [-hx + 1, zlo, hx - 1, zhi], 'floor_y': fy})
    before = walk_paths(m, start, targets)
    if not before['all_targets_reached']: raise ValueError('Partitions obstruct the floor or stair circulation')
    # Reserve the actual floor routes, a one-cell shoulder and all existing
    # stair treads. Never place a fixture on a stair landing or a doorway.
    reserved = set()
    for route in before['routes_floor_xyz'].values():
        for x, y, z in route:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1): reserved.add((x + dx, y, z + dz))
    records = []
    for zone in zones:
        xl, zl, xh, zh = zone['bounds_xz']; fy = zone['floor_y']
        candidates = [(x, z) for z in range(zl + 1, zh) for x in range(xl + 1, xh)]
        candidates.sort(key=lambda pt: (-abs(pt[0] - aisle), -abs(pt[1]), pt))
        for i in range(c['furniture_per_zone']):
            kind = USES[c['use']][i % len(USES[c['use']])]; found = None
            for x, z in candidates:
                cells, standing = fixture(kind, x, fy + 1, z)
                if any(not xl <= xx <= xh or not zl <= zz <= zh or
                       (xx, fy, zz) in reserved or m.get(xx, yy, zz) != 'minecraft:air'
                       for xx, yy, zz, state in cells): continue
                feet = {(xx, zz) for xx, yy, zz, state in cells}
                if any(not can_support_dry_walk(m.get(xx, fy, zz)) for xx, zz in feet): continue
                sx, sy, sz = standing
                if not zl <= sz <= zh or m.get(sx, sy + 1, sz) != 'minecraft:air' or m.get(sx, sy + 2, sz) != 'minecraft:air': continue
                if not can_support_dry_walk(m.get(sx, sy, sz)): continue
                # Keep both the interaction space and its approach clear.
                if any((sx, sy + dy, sz) == (xx, yy, zz) for xx, yy, zz, _ in cells for dy in (1, 2)): continue
                # A clear cell can still be trapped behind a stair guard or a
                # column. Probe the actual route before committing the fixture.
                for xx, yy, zz, state in cells: m.set(xx, yy, zz, state)
                try:
                    trial = walk_paths(m, start, {**targets, '_candidate': standing})
                    usable = trial['all_targets_reached']
                except ValueError:
                    usable = False
                finally:
                    for xx, yy, zz, state in cells: m.set(xx, yy, zz, 'minecraft:air')
                if not usable: continue
                found = cells, standing; break
            if found is None:
                raise ValueError(f'Cannot fit {kind} in {zone["id"]}; enlarge rooms or reduce furniture_per_zone')
            cells, standing = found
            for xx, yy, zz, state in cells: m.set(xx, yy, zz, state)
            ident = f'{zone["id"]}_{kind}_{i + 1}'
            targets[ident] = standing
            sx, sy, sz = standing
            for dz in (-1, 0): reserved.add((sx, sy, sz + dz))
            records.append({'id': ident, 'zone': zone['id'], 'kind': kind,
                            'cells': [list(v) for v in cells], 'use_floor_xyz': standing})
    paths = walk_paths(m, start, targets)
    checks = {'base_structure_verified': all(base_checks.values()),
              'all_zones_and_fixtures_reachable': paths['all_targets_reached'],
              'all_requested_fixtures_placed': len(records) == len(zones) * c['furniture_per_zone'],
              'partition_doorways_clear': all(m.get(aisle + dx, lev['floor_y'] + dy, 0) == 'minecraft:air'
                                             for lev in levels for dx in (-1, 0, 1) for dy in (1, 2)),
              'bed_halves_paired': all(len(r['cells']) == 2 and r['cells'][1][2] == r['cells'][0][2] + 1
                                      for r in records if r['kind'] == 'bed')}
    if not all(checks.values()): raise AssertionError(checks)
    return m, {'schema': 'chinese-inhabited-design/1', 'config': c,
               'start_floor_xyz': start, 'target_floors_xyz': targets, 'zones': zones,
               'fixtures': records, 'partition_cells_xyz': partition_cells,
               'base_design': original, 'routes': paths, 'checks': checks,
               'source_structure_reuse': False, 'historical_reconstruction': False,
               'limits': 'Original game-use furniture and open doorways. Containers are empty, books absent, shrine pedestals symbolic. Screens divide zones; stair galleries stay open. No live player interaction test.'}


def export(user, out_dir, name='inhabited', render=True):
    m, d = make_inhabited(user)
    return export_scene(m, d, out_dir, name, 'chinese-inhabited', render)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--config', type=Path)
    ap.add_argument('--out-dir', required=True, type=Path); ap.add_argument('--name', default='inhabited')
    ap.add_argument('--no-render', action='store_true'); a = ap.parse_args()
    r = export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {}, a.out_dir, a.name, not a.no_render)
    print(json.dumps({k: r[k] for k in ('name', 'dimensions_xyz', 'nonair_voxels')}, ensure_ascii=False))
