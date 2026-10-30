"""Spatial prototypes for flat-roof terraces, three-sided courts and canal streets.

Names describe geometry. A regional or ethnic attribution requires the selected
site's evidence, not merely this generator's colors or roof silhouette.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from garden_components import make_bridge
from generate_courtyard import assemble, transform, walk_paths
from generate_inhabited import make_inhabited
from generate_tower import make_tower
from scene_output import export_scene
from voxel_model import Model


DEFAULTS = {'flat_roof_house': {'enclosed_floors': 2}, 'three_sided_court': {'gate_offset': -14, 'wall_height': 5},
            'waterfront': {'houses_per_bank': 2, 'spacing': 42, 'bridge_rise': 2}}


def flat_roof_house(p):
    floors = p['enclosed_floors']
    if type(floors) is not int or not 1 <= floors <= 3: raise ValueError('enclosed_floors must be 1..3')
    levels = []
    for i in range(floors + 1):
        side = 8 + 2 * (floors - i)
        levels.append({'bays_x': [side, 8, side], 'bays_z': [side, 8, side], 'height': 8})
    part, d = make_tower({'levels': levels, 'platform_height': 1, 'eave_overhang': 2,
                         'palette': {'wall': 'minecraft:white_terracotta', 'column': 'minecraft:white_terracotta',
                                     'window': 'minecraft:gray_stained_glass'}})
    m = part; last = d['levels'][-1]; roof_y = last['floor_y']; saved_guards = [(pt, m.get(*pt)) for pt in d['guard_positions_xyz'] if pt[1] >= roof_y]
    roofing = np.array([s.split('[')[0] in ('minecraft:deepslate_tiles', 'minecraft:deepslate_tile_stairs', 'minecraft:deepslate_tile_slab') for s in m.palette])
    m.blocks[roofing[m.blocks]] = 0
    # Flat terrace slabs cover the lower room footprint. Preserve the exact
    # staircase void and landings when extending a slab across a setback.
    cx0, cz0, cx1, cz1 = d['stair_core_xz']
    for previous, upper in zip(d['levels'], d['levels'][1:]):
        fy = upper['floor_y']; ix0, ix1 = cx0 - m.origin[0], cx1 - m.origin[0]
        iz0, iz1 = cz0 - m.origin[2], cz1 - m.origin[2]
        saved = m.blocks[fy - m.origin[1], iz0:iz1 + 1, ix0:ix1 + 1].copy()
        m.box((-previous['half_x'], fy, -previous['half_z']), (previous['half_x'], fy, previous['half_z']), 'minecraft:white_terracotta')
        m.blocks[fy - m.origin[1], iz0:iz1 + 1, ix0:ix1 + 1] = saved
    # Remove only above-floor roof/eave envelopes, keeping the actual floors,
    # staircase core and lower entrance flight from the verified tower.
    envelopes = []
    for lev in d['levels'][:-1]:
        fy, hx, hz = lev['floor_y'], lev['half_x'], lev['half_z']
        for y in range(fy + 1, min(fy + 8, roof_y)):
            plane = m.blocks[y - m.origin[1]]; z, x = np.indices(plane.shape)
            plane[(abs(x + m.origin[0]) > hx) | (abs(z + m.origin[2]) > hz)] = 0
        # Strong corner piers and white envelope retain original narrow windows.
        for x in (-hx, hx):
            for z in (-hz, hz):
                m.box((x, fy + 1, z), (x, min(fy + 7, roof_y - 1), z), 'minecraft:white_terracotta')
        # The pitched tower left an eave/beam zone above its infill walls.
        # Removing the eaves must not leave that zone open to the exterior.
        perimeter = {(x, z) for x in range(-hx, hx + 1) for z in (-hz, hz)}
        perimeter.update((x, z) for z in range(-hz, hz + 1) for x in (-hx, hx))
        opening = {(x, y, -hz) for x in range(-2, 3) for y in range(fy + 1, fy + 5)} if lev['index'] == 0 else set()
        for x, z in sorted(perimeter):
            for y in range(fy + 1, fy + 8):
                if (x, y, z) not in opening and m.get(x, y, z) == 'minecraft:air':
                    m.set(x, y, z, 'minecraft:white_terracotta')
        closed = all(m.get(x, y, z) != 'minecraft:air' for x, z in perimeter for y in range(fy + 1, fy + 8) if (x, y, z) not in opening)
        envelopes.append({'floor_y': fy, 'ceiling_y': fy + 8, 'half_xz': [hx, hz],
                          'only_declared_ground_door_is_open': closed})
    m.blocks[roof_y - m.origin[1] + 1:] = 0
    for pt, state in saved_guards:
        if pt[1] <= roof_y + 2: m.set(*pt, state)
    hx, hz = last['half_x'], last['half_z']
    for x in range(-hx, hx + 1):
        for z in (-hz, hz): m.box((x, roof_y + 1, z), (x, roof_y + 2, z), 'minecraft:white_terracotta')
    for z in range(-hz, hz + 1):
        for x in (-hx, hx): m.box((x, roof_y + 1, z), (x, roof_y + 2, z), 'minecraft:white_terracotta')
    # Trim the unused air reserved for the former pitched roof.
    cropped = Model(m.origin, [int(m.maximum[0]), roof_y + 4, int(m.maximum[2])]); cropped.palette = m.palette; cropped.index = m.index
    cropped.blocks = m.blocks[:roof_y + 5 - m.origin[1]].copy(); m = cropped
    targets = {k: route[-1] for k, route in d['routes']['routes_floor_xyz'].items()}
    targets['roof_terrace'] = [2, roof_y, 2]
    return m, {'start_floor_xyz': d['routes']['routes_floor_xyz']['floor_1'][0], 'target_floors_xyz': targets,
               'enclosed_floors': floors, 'roof_terrace_y': roof_y, 'stair_core_xz': d['stair_core_xz'],
               'enclosed_level_envelopes': envelopes,
               'checks': {'terrace_open_to_sky': all(m.get(2, y, 2) == 'minecraft:air' for y in range(roof_y + 1, roof_y + 5)),
                          'walls_reach_flat_ceiling_except_declared_door': all(e['only_declared_ground_door_is_open'] for e in envelopes)},
               'limits': 'Stepped-mass flat-roof dwelling with a usable roof terrace. No claim of historic Tibetan battered masonry, Xinjiang room arrangement or seismic behavior.'}


def three_sided_court(p):
    offset, h = p['gate_offset'], p['wall_height']
    if type(offset) is not int or not 10 <= abs(offset) <= 20: raise ValueError('gate_offset needs an absolute value of 10..20')
    if type(h) is not int or not 4 <= h <= 6: raise ValueError('wall_height must be 4..6')
    m, d = assemble({'court_width': 32, 'court_depth': 32, 'boundary_wall': 'minecraft:white_concrete'})
    gate = d['parts']['gate']['placement']; lo, hi = gate['origin'], gate['maximum']
    m.box(lo, hi, 'minecraft:air')
    z = d['boundary_z'][1]; west, east = d['boundary_x']
    for x in range(west, east + 1):
        if abs(x - offset) <= 1: continue
        m.box((x, 0, z), (x, h, z), 'minecraft:white_concrete')
        m.set(x, h + 1, z, 'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')
    m.box((offset - 1, 0, z), (offset + 1, h + 1, z), 'minecraft:air')
    # The central white screen is an explicit fourth-side enclosure; access is
    # through the offset opening, with three houses facing the court.
    m.box((-5, 0, z - 1), (5, h + 1, z - 1), 'minecraft:white_concrete')
    m.box((-3, 2, z - 2), (3, h - 1, z - 2), 'minecraft:light_gray_terracotta')
    targets = {k: pt for k, pt in d['target_floors_xyz'].items() if k != 'gate'}
    targets['court_center'] = [0, -1, 0]
    start = [offset, -1, int(m.maximum[2])]
    return m, {'start_floor_xyz': start, 'target_floors_xyz': targets, 'entry_z': z,
               'house_count': 3, 'gate_offset': offset,
               'checks': {'offset_opening_clear': all(m.get(offset, y, z) == 'minecraft:air' for y in (0, 1, 2))},
               'limits': 'Original three-house court with a white screen wall. A three-sided court alone does not establish Bai, Naxi or any other regional attribution.'}


def waterfront(p):
    count, spacing, rise = p['houses_per_bank'], p['spacing'], p['bridge_rise']
    if type(count) is not int or not 1 <= count <= 4 or type(spacing) is not int or not 38 <= spacing <= 54:
        raise ValueError('houses_per_bank must be 1..4 and spacing 38..54')
    if type(rise) is not int or not 0 <= rise <= 3: raise ValueError('bridge_rise must be 0..3')
    z0, z1 = -28, (count - 1) * spacing + 28
    base, hd = make_inhabited({'use': 'house', 'building': {'bays_x': [6, 8, 6], 'bays_z': [6, 6],
                                  'roof': {'form': 'overhanging_gable', 'rise': 5, 'uplift': 0},
                                  'palette': {'wall': 'minecraft:white_concrete'}, 'platform_height': 1}})
    m = Model((-51, -4, z0), (51, int(base.maximum[1]) + 2, z1))
    m.box(tuple(m.origin), (51, 0, z1), 'minecraft:stone_bricks')
    m.box((-6, -2, z0 + 1), (6, 0, z1 - 1), 'minecraft:air')
    m.box((-6, -2, z0 + 1), (6, -2, z1 - 1), 'minecraft:water[level=0]')
    targets = {}; placements = {}
    for row in range(count):
        for sign in (-1, 1):
            ident = ('west' if sign < 0 else 'east') + f'_home_{row + 1}'; offset = [sign * 31, 1, row * spacing]; turn = 1 if sign < 0 else 3
            placements[ident] = m.paste(base, offset=offset, turns=turn)
            for k, pt in hd['target_floors_xyz'].items(): targets[ident + '.' + k] = transform(pt, offset, turn)
    bridge, bd = make_bridge(half_span=10, clear_width=3, rise=rise)
    # The bridge replaces only generated canal/walkway support in its declared
    # footprint; all dwellings were placed outside that footprint.
    for yy, zz, xx in zip(*np.where(bridge.blocks != 0)):
        x, y, z = (bridge.origin + [xx, yy, zz]).tolist()
        old = m.get(x, y, z)
        if y > 0 and old != 'minecraft:air': raise ValueError('Bridge would intersect a dwelling')
    placements['bridge'] = m.paste(bridge, conflict='replace')
    for x in (-9, 9): m.box((x - 1, 0, z0), (x + 1, 0, z1), 'minecraft:polished_andesite')
    targets['bridge_center'] = [0, rise, 0]
    return m, {'start_floor_xyz': [-9, 0, z0], 'target_floors_xyz': targets, 'placements': placements,
               'water_y': -2, 'canal_bounds_xz': [-6, z0 + 1, 6, z1 - 1],
               'checks': {'canal_has_bed': all(m.get(0, -3, z) != 'minecraft:air' for z in range(z0 + 1, z1)),
                          'homes_face_canal': len(placements) == count * 2 + 1},
               'limits': 'Original canal-bank settlement with source water, dry bank paths and a bridge. No water physics, exact Jiangnan town reconstruction or climate simulation.'}


def make_settlement(config):
    if not isinstance(config, dict) or set(config) - {'schema', 'kind', 'parameters', 'evidence'}: raise ValueError('Unknown settlement keys')
    k = config.get('kind', 'flat_roof_house'); params = config.get('parameters', {})
    if k not in DEFAULTS or config.get('schema', 'chinese-settlement/1') != 'chinese-settlement/1': raise ValueError('Unknown settlement kind/schema')
    if not isinstance(params, dict) or set(params) - set(DEFAULTS[k]): raise ValueError('Unknown settlement parameters')
    c = {'schema': 'chinese-settlement/1', 'kind': k, 'parameters': {**DEFAULTS[k], **params}, 'evidence': config.get('evidence', [])}
    m, d = globals()[k](c['parameters']); paths = walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz'])
    d['checks'].update(all_targets_reachable=paths['all_targets_reached'], face_connected_geometry=label(m.blocks != 0)[1] == 1)
    if not all(d['checks'].values()): raise AssertionError({**d['checks'], 'unreachable': paths['unreachable']})
    return m, {'schema': 'chinese-settlement-design/1', 'config': c, **d, 'routes': paths,
               'source_structure_reuse': False, 'historical_reconstruction': False}


def export(config, out_dir, name='settlement', render=True):
    m, d = make_settlement(config); return export_scene(m, d, out_dir, name, 'chinese-settlement', render)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--config', type=Path); ap.add_argument('--out-dir', required=True, type=Path)
    ap.add_argument('--name', default='settlement'); ap.add_argument('--no-render', action='store_true'); a = ap.parse_args()
    r = export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {}, a.out_dir, a.name, not a.no_render)
    print(json.dumps({k: r[k] for k in ('name', 'dimensions_xyz', 'nonair_voxels')}, ensure_ascii=False))
