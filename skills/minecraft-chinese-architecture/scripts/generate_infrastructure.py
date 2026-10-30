"""Covered routes, agricultural terraces, underground tombs and pagoda prototypes."""
from __future__ import annotations

import argparse
import copy
import heapq
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from garden_components import make_bridge
from generate_building import make_building
from generate_courtyard import walk_paths
from generate_regional import PALETTE, integer, roof_surface, stair
from generate_tower import make_tower
from roof_kernel import make_roof
from scene_output import export_scene
from voxel_model import Model, vector3


DEFAULTS = {
    'covered_bridge': {'half_span': 14, 'clear_width': 3, 'rise': 3, 'clear_height': 6},
    'hillside_gallery': {'path_xyz': [[0, 2, 0], [0, 7, 30], [30, 12, 30]], 'clear_width': 3, 'clear_height': 6},
    'terraced_farm': {'half_width': 18, 'levels': 3, 'terrace_depth': 10, 'level_rise': 2},
    'tomb': {'depth': 9, 'half_width': 18, 'chamber_depth': 12},
    'pagoda': {'mode': 'storeyed', 'levels': 3},
    'fort': {'half_width': 30, 'half_depth': 26, 'wall_height': 10},
}


def config_for(user):
    if not isinstance(user, dict) or set(user) - {'schema', 'kind', 'parameters', 'evidence'}:
        raise ValueError('Unknown infrastructure keys')
    kind = user.get('kind', 'covered_bridge'); p = user.get('parameters', {})
    if kind not in DEFAULTS or user.get('schema', 'chinese-infrastructure/1') != 'chinese-infrastructure/1':
        raise ValueError('Unknown infrastructure kind or schema')
    if not isinstance(p, dict) or set(p) - set(DEFAULTS[kind]): raise ValueError('Unknown parameters for ' + kind)
    return {'schema': 'chinese-infrastructure/1', 'kind': kind,
            'parameters': {**copy.deepcopy(DEFAULTS[kind]), **p}, 'evidence': user.get('evidence', [])}


def covered_bridge(p):
    h = integer(p, 'clear_height', 5, 9)
    bridge, d = make_bridge(p['half_span'], p['clear_width'], p['rise'])
    span, hw = p['half_span'], (p['clear_width'] + 1) // 2
    m = Model((-span, -2, -hw - 2), (span, p['rise'] + h + hw + 4, hw + 2)); m.paste(bridge)
    heights = dict(d['deck_y_by_x']); roof = {}
    for x, fy in heights.items():
        for z in range(-hw - 1, hw + 2): roof[x, z] = fy + h + max(0, hw + 1 - abs(z))
        if (x + span) % 5 == 0 or x == span:
            for z in (-hw, hw): m.box((x, fy + 1, z), (x, fy + h, z), PALETTE['wood'])
            m.box((x, fy + h - 1, -hw), (x, fy + h - 1, hw), PALETTE['beam'])
    roof_surface(m, roof, PALETTE)
    return m, {'start_floor_xyz': [-span, 0, 0], 'target_floors_xyz': {'opposite_bank': [span, 0, 0], 'bridge_center': [0, heights[0], 0]},
               'deck': d, 'checks': {'entire_deck_headroom': all(m.get(x, fy + dy, z) == 'minecraft:air'
                   for x, fy in heights.items() for z in range(-hw + 1, hw) for dy in (1, 2))},
               'limits': 'Stepped arch deck and roofed passage; not a surveyed woven-timber arch or structural load model.'}


def hillside_gallery(p):
    width = integer(p, 'clear_width', 3, 7); h = integer(p, 'clear_height', 5, 9)
    if width % 2 != 1: raise ValueError('Odd gallery width required')
    points = p['path_xyz']
    if not isinstance(points, list) or not 2 <= len(points) <= 12: raise ValueError('path_xyz needs 2..12 vertices')
    vertices = [vector3(v, 'gallery path vertex').tolist() for v in points]
    if any(abs(x) > 120 or abs(z) > 120 or not 2 <= y <= 48 for x, y, z in vertices):
        raise ValueError('Gallery X/Z must be within +/-120 and floor Y within 2..48')
    line = []
    for a, b in zip(vertices, vertices[1:]):
        delta = np.array(b) - a; length = abs(delta[0]) + abs(delta[2])
        if (delta[0] == 0) == (delta[2] == 0) or length < 6 or abs(delta[1]) > length // 3:
            raise ValueError('Gallery segments must be orthogonal, >=6 cells, slope <=1/3')
        for j in range(int(length)):
            line.append(tuple((np.array(a) + np.rint(delta * (j / length)).astype(int)).tolist()))
    line.append(tuple(vertices[-1]))
    if len({(x, z) for x, y, z in line}) != len(line): raise ValueError('Gallery path intersects itself')
    r = width // 2 + 1
    def band(radius):
        near = {}
        for i, (x, y, z) in enumerate(line):
            for dx in range(-radius, radius + 1):
                for dz in range(-radius, radius + 1):
                    key = (x + dx, z + dz); priority = (dx * dx + dz * dz, i)
                    if key not in near or priority < near[key][0]: near[key] = (priority, y)
        return {v: item[1] for v, item in near.items()}
    floors, clear, outer = band(r), band(r - 1), band(r + 1)
    # A nearest-segment assignment jumps at the bisector of a rising corner.
    # Project the whole band to a one-Lipschitz integer surface while pinning
    # every specified centerline height. The lower envelope protects those
    # pins; a multi-source shortest-path envelope removes neighbor jumps.
    levels = {pt: max(raw, max(cy - abs(pt[0] - cx) - abs(pt[1] - cz) for cx, cy, cz in line))
              for pt, raw in outer.items()}
    queue = [(y, x, z) for (x, z), y in levels.items()]; heapq.heapify(queue)
    while queue:
        y, x, z = heapq.heappop(queue)
        if levels[x, z] != y: continue
        for pt in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
            if pt in levels and levels[pt] > y + 1:
                levels[pt] = y + 1; heapq.heappush(queue, (y + 1, *pt))
    if any(levels[x, z] != y for x, y, z in line): raise ValueError('Path heights conflict across nearby segments')
    floors = {pt: levels[pt] for pt in floors}; clear = {pt: levels[pt] for pt in clear}; outer = levels
    if any(abs(fy - floors[xx, zz]) > 1 for (x, z), fy in floors.items()
           for xx, zz in ((x + 1, z), (x, z + 1)) if (xx, zz) in floors):
        raise ValueError('Turn is too sharp for this rise/width; lengthen the approaches')
    xs, zs = zip(*outer)
    m = Model((min(xs), 0, min(zs)), (max(xs), max(outer.values()) + h + r + 2, max(zs)))
    for (x, z), fy in floors.items():
        m.box((x, fy - 1, z), (x, fy, z), PALETTE['floor'])
        if (x, z) not in clear:
            m.set(x, fy + 1, z, PALETTE['beam'])
            if (x + 3 * z) % 6 == 0:
                m.box((x, 0, z), (x, fy + h, z), PALETTE['wood'])
    roof = {}
    for (x, z), fy in outer.items():
        distance = min(max(abs(x - cx), abs(z - cz)) for cx, cy, cz in line)
        roof[x, z] = fy + h + max(0, r + 1 - distance)
    roof_surface(m, roof, PALETTE)
    # Portal frames at each endpoint attach the canopy without blocking the
    # clear band. Intermediate posts support the rest of the gallery.
    for endpoint, adjacent in ((line[0], line[1]), (line[-1], line[-2])):
        x, fy, z = endpoint; dx, dz = adjacent[0] - x, adjacent[2] - z
        for sign in (-1, 1):
            px, pz = x + sign * dz * r, z - sign * dx * r
            m.box((px, 0, pz), (px, roof[px, pz], pz), PALETTE['wood'])
    return m, {'start_floor_xyz': list(line[0]), 'target_floors_xyz': {'end': list(line[-1])},
               'centerline_floor_xyz': [list(v) for v in line], 'clear_band_floor_xyz': [[x, y, z] for (x, z), y in sorted(clear.items())],
               'checks': {'planned_width_headroom': all(m.get(x, y + dy, z) == 'minecraft:air' for (x, z), y in clear.items() for dy in (1, 2)),
                          'centerline_steps': all(abs(a[1] - b[1]) <= 1 for a, b in zip(line, line[1:]))},
               'limits': 'Original stepped covered gallery on posts. Explicit path elevations, not terrain sampling or surveyed hillside carpentry.'}


def terraced_farm(p):
    w = integer(p, 'half_width', 12, 32); count = integer(p, 'levels', 2, 6)
    depth = integer(p, 'terrace_depth', 8, 16); rise = integer(p, 'level_rise', 1, 3)
    stride = depth + rise + 2; end = count * stride - 1
    m = Model((-w - 2, -2, -3), (w + 2, count * rise + 5, end + 2))
    m.box(tuple(m.origin), (w + 2, -1, end + 2), 'minecraft:stone')
    targets = {}; water = []; crops = []; levels = []
    for i in range(count):
        z0, fy = i * stride, i * rise
        m.box((-w, 0, z0), (w, fy, z0 + depth), 'minecraft:dirt')
        channels = list(range(-w + 7, w - 1, 8))
        if not channels or (w - 1) - channels[-1] > 4: channels.append(w - 4)
        for z in range(z0 + 1, z0 + depth):
            for x in range(-w + 4, w):
                if x in channels:
                    m.set(x, fy, z, 'minecraft:water[level=0]'); water.append([x, fy, z])
                else:
                    m.set(x, fy, z, 'minecraft:farmland[moisture=7]')
                    m.set(x, fy + 1, z, 'minecraft:wheat[age=7]'); crops.append([x, fy, z])
        m.box((-w, fy, z0), (-w + 2, fy, z0 + depth), 'minecraft:stone_bricks')
        for z in (z0, z0 + depth): m.box((-w, fy, z), (w, fy, z), 'minecraft:stone_bricks')
        m.box((w, fy, z0), (w, fy, z0 + depth), 'minecraft:stone_bricks')
        if i < count - 1:
            for j in range(1, rise + 1):
                z = z0 + depth + j
                m.box((-w, -1, z), (-w + 2, fy + j - 1, z), 'minecraft:stone_bricks')
                m.box((-w, fy + j, z), (-w + 2, fy + j, z), stair('minecraft:stone_brick_stairs', 'south'))
            m.box((-w, -1, z0 + depth + rise + 1), (-w + 2, fy + rise, z0 + stride), 'minecraft:stone_bricks')
        targets[f'terrace_{i + 1}'] = [-w + 1, fy, z0 + depth // 2]
        levels.append({'index': i + 1, 'floor_y': fy, 'z_range': [z0, z0 + depth], 'water_x': channels})
    # Walk from the first terrace, not through the cultivated crop cells.
    return m, {'start_floor_xyz': [-w + 1, 0, 0], 'target_floors_xyz': targets, 'levels': levels,
               'water_cells_xyz': water, 'crop_soil_xyz': crops,
               'checks': {'every_crop_has_irrigation_within_four': all(any(abs(x - wx) <= 4 and fy == wy and abs(z - wz) <= 4 for wx, wy, wz in water) for x, fy, z in crops),
                          'water_has_solid_bottom': all(m.get(x, y - 1, z) != 'minecraft:air' for x, y, z in water)},
               'limits': 'Wheat and hydrated farmland are vanilla substitutes for a rice landscape; no rice species or live water/growth simulation claimed.'}


def tomb(p):
    depth = integer(p, 'depth', 6, 16); w = integer(p, 'half_width', 14, 26); length = integer(p, 'chamber_depth', 8, 18)
    front, back = -depth - 6, length + 8
    m = Model((-w - 2, -depth - 3, front - 3), (w + 2, 12, back + 2))
    m.box(tuple(m.origin), (w + 2, 0, back + 2), 'minecraft:stone_bricks')
    m.box((-w - 2, 0, front - 3), (w + 2, 0, back + 2), 'minecraft:grass_block[snowy=false]')
    for j in range(depth + 1):
        z, fy = front + j, -j
        m.box((-1, fy + 1, z), (1, fy + 4, z), 'minecraft:air')
        m.box((-1, fy, z), (1, fy, z), stair('minecraft:stone_brick_stairs', 'north'))
    # Antechamber, axial chamber and two side chambers with connecting necks.
    m.box((-2, -depth + 1, front + depth), (2, -depth + 4, 3), 'minecraft:air')
    m.box((-5, -depth + 1, 2), (5, -depth + 5, length + 3), 'minecraft:air')
    m.box((-w + 2, -depth + 1, 5), (w - 2, -depth + 3, 7), 'minecraft:air')
    for sign in (-1, 1):
        a, b = sorted((sign * (w - 2), sign * 8))
        m.box((a, -depth + 1, 3), (b, -depth + 4, length), 'minecraft:air')
        m.box((a + 1, -depth + 1, length - 2), (b - 1, -depth + 1, length - 1), 'minecraft:chiseled_stone_bricks')
    m.box((-2, -depth + 1, length + 1), (2, -depth + 1, length + 2), 'minecraft:chiseled_stone_bricks')
    # A modest entry canopy leaves the descending opening unobstructed.
    roof, _ = make_roof(half_x=5, half_z=3, base_y=5, form='overhanging_gable', rise=3, curve=1.3, uplift=0)
    for x in (-3, 3):
        for z in (front - 1, front + 2):
            underside = int(np.flatnonzero(roof.blocks[:, z - front - roof.origin[2], x - roof.origin[0]])[0] + roof.origin[1])
            m.box((x, 1, z), (x, underside - 1, z), PALETTE['wood'])
    m.paste(roof, offset=[0, 0, front])
    return m, {'start_floor_xyz': [0, 0, front - 2], 'target_floors_xyz': {'antechamber': [0, -depth, 0],
               'central_chamber': [0, -depth, 8], 'west_chamber': [-w + 4, -depth, 6], 'east_chamber': [w - 4, -depth, 6]},
               'descent_front_z': front, 'floor_y': -depth,
               'checks': {'underground_cover': all(m.get(x, 0, z) != 'minecraft:air' for x in range(-w, w + 1) for z in range(3, length + 4))},
               'limits': 'Authored underground chambers and symbolic stone furnishings; no claim of dynasty-specific tomb law, burial practice or excavated plan.'}


def pagoda(p):
    mode = p['mode']; levels = integer(p, 'levels', 2, 7)
    if mode not in ('storeyed', 'dense_eaves'): raise ValueError('pagoda mode must be storeyed or dense_eaves')
    if mode == 'storeyed':
        if levels > 5: raise ValueError('Walkable storeyed pagoda supports 2..5 actual floors')
        config = {'levels': [{'bays_x': [8, 8, 8], 'bays_z': [8, 8, 8], 'height': 8} for i in range(levels)],
                  'top_roof': {'form': 'pyramidal', 'rise': 7, 'uplift': 1}, 'platform_height': 1}
        part, d = make_tower(config); targets = {k: v[-1] for k, v in d['routes']['routes_floor_xyz'].items()}
        start = d['routes']['routes_floor_xyz']['floor_1'][0]
    else:
        c = {'bays_x': [12], 'bays_z': [12], 'column_height': 3 * levels + 2, 'platform_height': 1,
             'roof': {'form': 'pyramidal', 'rise': 5, 'uplift': 0}}
        part, d, conf = make_building(c)
        targets = {'single_chamber': [0, 1, 0]}; start = [0, 0, -d['platform_half_z'] - 1]
    m = Model((int(part.origin[0]) - 5, 0, int(part.origin[2])),
              (int(part.maximum[0]) + 5, int(part.maximum[1]) + 8, int(part.maximum[2]) + 5)); m.paste(part)
    if mode == 'dense_eaves':
        for i in range(levels):
            half = 11 - min(3, i // 2)
            roof, _ = make_roof(half_x=half, half_z=half, base_y=5 + i * 3, form='pyramidal', rise=3, curve=1.4, uplift=0)
            for yy, zz, xx in zip(*np.where(roof.blocks != 0)):
                x, y, z = (roof.origin + [xx, yy, zz]).tolist()
                if abs(x) <= 6 and abs(z) <= 6: continue
                if m.get(x, y, z) == 'minecraft:air': m.set(x, y, z, roof.palette[int(roof.blocks[yy, zz, xx])])
    top = int(np.flatnonzero(part.blocks[:, -part.origin[2], -part.origin[0]])[-1] + part.origin[1])
    m.box((0, top + 1, 0), (0, top + 6, 0), 'minecraft:cut_copper')
    m.box((-1, top + 2, -1), (1, top + 2, 1), 'minecraft:cut_copper_slab[type=bottom,waterlogged=false]')
    return m, {'start_floor_xyz': start, 'target_floors_xyz': targets, 'mode': mode,
               'actual_accessible_floors': levels if mode == 'storeyed' else 1, 'eave_bands': levels, 'base_design': d,
               'checks': {'spire_present': m.get(0, top + 6, 0) == 'minecraft:cut_copper'},
               'limits': 'Square game prototypes distinguish occupied storeys from decorative dense eaves. Spire is abstract; no Buddhist iconography, relic placement or historic structural system inferred.'}


def fort(p):
    w = integer(p, 'half_width', 26, 38); d = integer(p, 'half_depth', 24, 36); h = integer(p, 'wall_height', 8, 14)
    m = Model((-w - 5, 0, -d - 6), (w + 5, h + 17, d + 5)); m.box(tuple(m.origin), (w + 5, 0, d + 5), PALETTE['stone'])
    for x in (-w, w - 2): m.box((x, 1, -d), (x + 2, h, d), PALETTE['stone'])
    for z in (-d, d - 2): m.box((-w, 1, z), (w, h, z + 2), PALETTE['stone'])
    m.box((-2, 1, -d), (2, 5, -d + 2), 'minecraft:air')
    for x in range(-w, w + 1):
        for z in (-d, d):
            m.set(x, h + 1, z, PALETTE['stone'])
            if x % 4 < 2: m.set(x, h + 2, z, PALETTE['stone'])
    for z in range(-d, d + 1):
        for x in (-w, w):
            m.set(x, h + 1, z, PALETTE['stone'])
            if z % 4 < 2: m.set(x, h + 2, z, PALETTE['stone'])
    sx = -w + 6; sz = d - 6
    for j in range(h + 1):
        x = sx + j
        if j > 0: m.box((x, 1, sz - 1), (x, j, sz + 1), PALETTE['stone'])
        m.box((x, j, sz - 1), (x, j, sz + 1), stair('minecraft:stone_brick_stairs', 'east'))
    m.box((sx + h, h, sz - 1), (sx + h + 2, h, d - 1), PALETTE['stone'])
    targets = {'inner_court': [0, 0, -d + 5], 'wall_walk': [sx + h + 1, h, d - 1]}
    # Lightweight roofed lookouts occupy widened corner platforms.
    for index, (cx, cz) in enumerate(((-w + 1, -d + 1), (w - 1, -d + 1), (w - 1, d - 1), (-w + 1, d - 1))):
        m.box((cx - 3, h, cz - 3), (cx + 3, h, cz + 3), PALETTE['stone'])
        roof, _ = make_roof(half_x=4, half_z=4, base_y=h + 6, form='pyramidal', rise=4, curve=1.4, uplift=0)
        for x in (cx - 2, cx + 2):
            for z in (cz - 2, cz + 2):
                underside = int(np.flatnonzero(roof.blocks[:, z - cz - roof.origin[2], x - cx - roof.origin[0]])[0] + roof.origin[1])
                m.box((x, h + 1, z), (x, underside - 1, z), PALETTE['wood'])
        m.paste(roof, offset=[cx, 0, cz])
        # Clear a two-high walkway through the parapet where the lookout meets
        # the inner ring; the exterior edge remains raised.
        tx, tz = cx - (1 if cx > 0 else -1), cz - (1 if cz > 0 else -1)
        m.box((tx, h + 1, tz), (tx, h + 2, tz), 'minecraft:air')
        targets[f'lookout_{index + 1}'] = [tx, h, tz]
    from generate_inhabited import make_inhabited
    house, hd = make_inhabited({'use': 'workshop', 'building': {'bays_x': [6, 8, 6], 'bays_z': [6, 6], 'platform_height': 1}})
    m.paste(house, offset=[0, 1, 0])
    for key, point in hd['target_floors_xyz'].items(): targets['workshop_' + key] = [point[0], point[1] + 1, point[2]]
    return m, {'start_floor_xyz': [0, 0, -d - 4], 'target_floors_xyz': targets,
               'gate_z': -d, 'checks': {'gate_three_wide': all(m.get(x, y, z) == 'minecraft:air' for x in (-1, 0, 1) for y in (1, 2) for z in range(-d, -d + 3))},
               'limits': 'Original enclosure, workshop, wall stairs and corner lookouts; no historical defense simulation or siege engineering.'}


def make_infrastructure(user):
    c = config_for(user); m, d = globals()[c['kind']](c['parameters'])
    paths = walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz'])
    d['checks'].update(all_targets_reachable=paths['all_targets_reached'], face_connected_geometry=label(m.blocks != 0)[1] == 1)
    if not all(d['checks'].values()): raise AssertionError({**d['checks'], 'unreachable': paths['unreachable']})
    return m, {'schema': 'chinese-infrastructure-design/1', 'config': c, **d, 'routes': paths,
               'source_structure_reuse': False, 'historical_reconstruction': False}


def export(user, out_dir, name='infrastructure', render=True):
    m, d = make_infrastructure(user)
    return export_scene(m, d, out_dir, name, 'chinese-infrastructure', render)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--config', type=Path)
    ap.add_argument('--out-dir', required=True, type=Path); ap.add_argument('--name', default='infrastructure')
    ap.add_argument('--no-render', action='store_true'); a = ap.parse_args()
    r = export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {}, a.out_dir, a.name, not a.no_render)
    print(json.dumps({k: r[k] for k in ('name', 'dimensions_xyz', 'nonair_voxels')}, ensure_ascii=False))
