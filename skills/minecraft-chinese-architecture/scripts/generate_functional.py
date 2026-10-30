"""Original functional compounds and shop streets built from editable components.

Profiles encode explicit game-use roles and adjacency, not a universal historic
temple, palace or administrative plan. The complete resulting complex config is
saved, so users can freely move, replace, resize or add components afterwards.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

from generate_complex import make_complex
from generate_courtyard import walk_paths
from scene_output import export_scene


KINDS = ('palace', 'ancestral_hall', 'buddhist_temple', 'daoist_temple', 'academy', 'government', 'market_street')
DEFAULT = {'schema': 'chinese-functional/1', 'kind': 'academy', 'spacing': 64,
           'shops_per_side': 3, 'enclosure': True, 'wall_height': 4,
           'landscape': True, 'palette': {}, 'overrides': {}, 'evidence': []}
HALL = {'bays_x': [6, 8, 6], 'bays_z': [6, 6], 'column_height': 7,
        'platform_height': 1, 'roof': {'form': 'hip_and_gable', 'rise': 5, 'uplift': 1}}
TOWER = {'levels': [{'bays_x': [8, 8, 8], 'bays_z': [8, 8], 'height': 8},
                    {'bays_x': [8, 8, 8], 'bays_z': [8, 8], 'height': 8}],
         'platform_height': 1, 'top_roof': {'rise': 5}}


def program_config(user):
    if not isinstance(user, dict) or set(user) - set(DEFAULT): raise ValueError('Unknown functional keys')
    c = copy.deepcopy(DEFAULT); c.update(user)
    if c['schema'] != DEFAULT['schema'] or c['kind'] not in KINDS: raise ValueError('Unknown functional kind')
    if type(c['spacing']) is not int or not 58 <= c['spacing'] <= 78: raise ValueError('spacing must be 58..78')
    if type(c['shops_per_side']) is not int or not 1 <= c['shops_per_side'] <= 5: raise ValueError('shops_per_side must be 1..5')
    if type(c['enclosure']) is not bool or type(c['wall_height']) is not int or not 3 <= c['wall_height'] <= 6:
        raise ValueError('enclosure must be boolean; wall_height must be 3..6')
    if not isinstance(c['palette'], dict) or not isinstance(c['overrides'], dict): raise ValueError('palette and overrides must be objects')
    if type(c['landscape']) is not bool: raise ValueError('landscape must be boolean')
    components = []; edges = []; s = c['spacing']; roles = {}

    def add(ident, role, use, x, z, turns=0, tower=False, partition=True, big=False):
        conf = copy.deepcopy(TOWER if tower else HALL)
        if big:
            conf.update(bays_x=[8, 12, 8], bays_z=[8, 8], column_height=10)
        conf['palette'] = c['palette']
        entry = {'id': ident, 'kind': 'inhabited', 'position_xz': [x, z], 'turns': turns,
                 'role': role, 'label': role,
                 'config': {'kind': 'tower' if tower else 'hall', 'use': use, 'building': conf, 'partition': partition}}
        components.append(entry); roles[ident] = role
        return ident + '.entry'

    gate = {'id': 'gate', 'kind': 'building', 'position_xz': [0, 0], 'turns': 0, 'through': True,
            'role': 'entrance', 'config': {'kind': 'pavilion', 'bays_x': [8], 'bays_z': [4],
            'column_height': 5, 'platform_height': 1, 'platform_margin': 2, 'eave_overhang': 2,
            'stair_width': 3, 'roof': {'form': 'overhanging_gable', 'rise': 3, 'uplift': 0}, 'palette': c['palette']}}
    if c['kind'] != 'market_street':
        components.append(gate); roles['gate'] = 'entrance'; edges.append(['entrance', 'gate.entry'])
    def axis(items):
        previous = 'gate.exit'
        for i, (ident, role, use, tower, big) in enumerate(items, 1):
            anchor = add(ident, role, use, 0, i * s, tower=tower, big=big, partition=use not in ('shrine', 'classroom'))
            edges.append([previous, anchor]); previous = anchor
    def side(ident, role, use, side_x, row, parent, tower=False):
        anchor = add(ident, role, use, side_x * s, round(row * s), turns=1 if side_x < 0 else 3, tower=tower)
        edges.append([parent + '.entry', anchor])
    k = c['kind']
    if k == 'palace':
        axis([('audience', 'ceremonial_audience', 'office', False, True),
              ('inner_hall', 'inner_audience', 'office', False, False),
              ('residence', 'residential_quarters', 'house', True, False)])
        side('west_office', 'administrative_wing', 'office', -1, 1, 'audience')
        side('east_office', 'administrative_wing', 'office', 1, 1, 'audience')
        side('kitchen', 'service_kitchen', 'kitchen', 1, 2.5, 'inner_hall')
    elif k == 'ancestral_hall':
        axis([('assembly', 'clan_assembly', 'refectory', False, True),
              ('offering', 'memorial_hall', 'shrine', False, False)])
        side('genealogy', 'records_and_genealogy', 'library', -1, 1.5, 'assembly')
        side('meeting', 'clan_meeting', 'office', 1, 1.5, 'assembly')
    elif k == 'buddhist_temple':
        axis([('front_hall', 'front_worship_hall', 'shrine', False, False),
              ('main_hall', 'principal_worship_hall', 'shrine', False, True),
              ('scripture', 'scripture_library', 'library', True, False)])
        side('bell', 'bell_tower', 'bell', -1, .7, 'front_hall', tower=True)
        side('drum', 'drum_tower', 'drum', 1, .7, 'front_hall', tower=True)
        side('dormitory', 'residential_quarters', 'house', -1, 2.1, 'main_hall')
        side('refectory', 'refectory', 'refectory', 1, 2.1, 'main_hall')
    elif k == 'daoist_temple':
        axis([('main_hall', 'principal_ritual_hall', 'shrine', False, True),
              ('rear_hall', 'rear_ritual_hall', 'shrine', False, False)])
        side('scripture', 'scripture_study', 'library', -1, 1.5, 'main_hall')
        side('lodging', 'residential_quarters', 'house', 1, 1.5, 'main_hall')
        side('service', 'kitchen_and_service', 'kitchen', 1, 2.6, 'rear_hall')
    elif k == 'academy':
        axis([('lecture', 'lecture_hall', 'classroom', False, True),
              ('library', 'library', 'library', True, False)])
        side('west_class', 'seminar_room', 'classroom', -1, 1, 'lecture')
        side('east_class', 'seminar_room', 'classroom', 1, 1, 'lecture')
        side('dormitory', 'student_lodging', 'house', -1, 2, 'library')
        side('kitchen', 'kitchen', 'kitchen', 1, 2, 'library')
    elif k == 'government':
        axis([('public_hall', 'public_audience', 'office', False, True),
              ('working_hall', 'administrative_office', 'office', False, False),
              ('residence', 'residential_quarters', 'house', False, False)])
        side('archives', 'records_storage', 'library', -1, 1.2, 'public_hall')
        side('registry', 'registration_office', 'office', 1, 1.2, 'public_hall')
        side('service', 'service_workshop', 'workshop', 1, 2.3, 'working_hall')
    else:
        kinds = ('shop', 'apothecary', 'workshop', 'kitchen', 'granary')
        for row in range(c['shops_per_side']):
            for sign in (-1, 1):
                ident = ('west' if sign < 0 else 'east') + f'_shop_{row + 1}'
                use = kinds[(row + (sign > 0)) % len(kinds)]
                anchor = add(ident, use, use, sign * 38, row * s, turns=1 if sign < 0 else 3)
                edges.append(['entrance', anchor])
        anchor = add('inn', 'lodging_and_reception', 'inn', 0, c['shops_per_side'] * s, tower=True)
        edges.append(['entrance', anchor])
    known = {e['id'] for e in components}
    if set(c['overrides']) - known: raise ValueError('overrides must name existing components')
    for entry in components:
        override = c['overrides'].get(entry['id'], {})
        if not isinstance(override, dict) or set(override) - {'config', 'position_xz', 'turns', 'role', 'label'}:
            raise ValueError('Overrides may change config, position_xz, turns, role or label')
        for key, value in override.items():
            entry[key] = {**entry[key], **value} if key == 'config' else value
    # The editable complex is the unrestricted composition interface. These
    # wrappers use flat sites to isolate functional design from terrain grading.
    minx = min(e['position_xz'][0] for e in components) - 44
    maxx = max(e['position_xz'][0] for e in components) + 44
    minz = min(e['position_xz'][1] for e in components) - 48
    maxz = max(e['position_xz'][1] for e in components) + 44
    site = {'schema': 'chinese-complex/1', 'origin_xz': [minx, minz], 'size_xz': [maxx - minx + 1, maxz - minz + 1],
            'terrain': {'base': 0, 'grade_z': 0, 'wave': 0}, 'pad_margin': 5, 'path_width': 3, 'max_path_grading': 0, 'max_pad_cut_fill': 0,
            'entrance_xz': [0, minz + 1], 'components': components, 'connections': edges,
            'program': {'kind': k, 'roles': roles, 'historic_plan': False}, 'evidence': c['evidence']}
    return c, site


def make_functional(user):
    c, site = program_config(user)
    m, d, original, graded = make_complex(site)
    start = d['anchors_floor_xyz']['entrance']; targets = d['targets_floor_xyz']
    landscape = {'paved_forecourts': [], 'trees_xyz': []}
    if c['landscape']:
        # Courtyard paving links the axis buildings spatially; the source
        # heightfield remains unchanged and all door/road elevations stay zero.
        axis_pads = sorted([pad for pad in d['pads'] if next(e for e in site['components'] if e['id'] == pad['id'])['position_xz'][0] == 0], key=lambda pad: pad['bounds_xz'][1])
        for first, second in zip(axis_pads, axis_pads[1:]):
            za, zb = first['bounds_xz'][3] + 1, second['bounds_xz'][1] - 1
            half = min(22, c['spacing'] // 2 - 9)
            if za <= zb:
                m.box((-half, 0, za), (half, 0, zb), 'minecraft:polished_andesite')
                landscape['paved_forecourts'].append([-half, za, half, zb])
        # Place small original trees only in untouched grass, clear of full
        # building pads and roads. Roof canopies may not clip existing geometry.
        xlo, zlo = site['origin_xz']; w, length = site['size_xz']
        for x in range(xlo + 14, xlo + w - 12, 24):
            for z in range(zlo + 16, zlo + length - 12, 28):
                if not all(m.get(x + dx, 0, z + dz).startswith('minecraft:grass_block') and
                           all(m.get(x + dx, y, z + dz) == 'minecraft:air' for y in range(1, 9))
                           for dx in range(-3, 4) for dz in range(-3, 4)): continue
                m.box((x, 1, z), (x, 7, z), 'minecraft:spruce_log[axis=y]')
                for y, radius in ((4, 3), (5, 2), (6, 2), (7, 1), (8, 0)):
                    for dx in range(-radius, radius + 1):
                        for dz in range(-radius, radius + 1):
                            if dx * dx + dz * dz <= radius * radius + 1 and m.get(x + dx, y, z + dz) == 'minecraft:air':
                                m.set(x + dx, y, z + dz, 'minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]')
                landscape['trees_xyz'].append([x, 0, z])
    boundary = None
    if c['enclosure']:
        x0, z0 = site['origin_xz']; w, depth = site['size_xz']; x1, z1 = x0 + w - 2, z0 + depth - 2
        x0 += 1; z0 += 2; h = c['wall_height']
        cells = {(x, z) for x in range(x0, x1 + 1) for z in (z0, z1)}
        cells |= {(x, z) for z in range(z0, z1 + 1) for x in (x0, x1)}
        for x, z in sorted(cells):
            if z == z0 and abs(x - start[0]) <= 2: continue
            for y in range(1, h + 1):
                if m.get(x, y, z) != 'minecraft:air': raise ValueError('Enclosure intersects a component')
                m.set(x, y, z, 'minecraft:stone_bricks' if y in (1, h) else 'minecraft:white_terracotta')
        boundary = {'bounds_xz': [x0, z0, x1, z1], 'gate_floor_xyz': [start[0], 0, z0], 'opening_half_width': 2, 'height': h}
    paths = walk_paths(m, start, targets)
    d['checks']['furnished_targets_after_enclosure'] = paths['all_targets_reached']
    if not all(d['checks'].values()): raise AssertionError(d['checks'])
    return m, {'schema': 'chinese-functional-design/1', 'config': c, 'editable_complex': site,
               'program': site['program'], 'boundary': boundary, 'landscape': landscape, 'component_design': d,
               'start_floor_xyz': start, 'target_floors_xyz': targets, 'routes': paths, 'checks': d['checks'],
               'source_structure_reuse': False, 'historical_reconstruction': False,
               'limits': 'Distinct original functional programs, not surveyed historic institutions. No inventory, ritual ranking, or cultural attribution inferred from fixtures.'}


def export(user, out_dir, name='functional', render=True):
    m, d = make_functional(user)
    report = export_scene(m, d, out_dir, name, 'chinese-functional', render)
    (Path(out_dir) / f'{name}.complex.json').write_text(json.dumps(d['editable_complex'], ensure_ascii=False, indent=2), encoding='utf8')
    return report


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--config', type=Path)
    ap.add_argument('--out-dir', required=True, type=Path); ap.add_argument('--name', default='functional')
    ap.add_argument('--no-render', action='store_true'); a = ap.parse_args()
    r = export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {}, a.out_dir, a.name, not a.no_render)
    print(json.dumps({k: r[k] for k in ('name', 'dimensions_xyz', 'nonair_voxels')}, ensure_ascii=False))
