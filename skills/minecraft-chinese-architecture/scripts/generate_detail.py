"""Original block-scale envelope details and a single-storey double-eave hall.

Enlarged teaching samples are not joinery, engineering, decorative-rank or
dynasty reconstructions. Their scale is explicit and they remain separate from
normal-sized buildings unless the user deliberately composes them.
"""
from __future__ import annotations

import argparse
import copy
import json
import math
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_building import make_building
from generate_courtyard import walk_paths
from generate_landmark import line
from generate_regional import PALETTE, integer
from roof_kernel import make_roof
from scene_output import export_scene
from voxel_model import Model


DEFAULTS = {
    'double_eave_hall': {'column_height': 12, 'lower_eave_height': 8, 'overhang': 5},
    'column_base': {'radius': 3, 'height': 16, 'profile': 'entasis', 'inclination': 0},
    'bracket_panel': {'half_span': 9, 'height': 9, 'drop': 4},
    'ridge_end': {'half_span': 12, 'rise': 6, 'form': 'swallowtail'},
    'tile_course': {'radius': 3, 'courses': 4, 'length': 18},
    'wall_corner': {'length': 20, 'height': 8, 'thickness': 2},
    'drainage_strip': {'length': 32, 'drop': 3},
    'log_frame': {'half_width': 8, 'half_depth': 6, 'height': 10},
}


def double_eave_hall(p):
    h = integer(p, 'column_height', 10, 20); low = integer(p, 'lower_eave_height', 6, 12)
    over = integer(p, 'overhang', 4, 7)
    if low + 3 >= h + 5: raise ValueError('Lower eaves must be clearly below the main roof')
    part, d, conf = make_building({'bays_x': [6, 8, 6], 'bays_z': [6, 6], 'column_height': h,
                                 'platform_height': 1, 'eave_overhang': 3})
    hx, hz = d['wall_half_x'], d['wall_half_z']
    roof, rd = make_roof(half_x=hx + over, half_z=hz + over, base_y=low, rise=3,
                         form='hip', curve=1.4, uplift=0)
    m = Model((min(int(part.origin[0]), -hx - over - 1), 0, min(int(part.origin[2]), -hz - over - 1)),
              (max(int(part.maximum[0]), hx + over + 1), int(part.maximum[1]), max(int(part.maximum[2]), hz + over + 1)))
    m.box(tuple(m.origin), (int(m.maximum[0]), 0, int(m.maximum[2])), PALETTE['stone'])
    # The generated hall owns its base on this new display plinth.
    m.paste(part, conflict='replace')
    for yy, zz, xx in zip(*np.where(roof.blocks != 0)):
        x, y, z = (roof.origin + [xx, yy, zz]).tolist()
        if abs(x) <= hx and abs(z) <= hz: continue
        if m.get(x, y, z) != 'minecraft:air': raise ValueError('Lower eaves intersect upper envelope')
        m.set(x, y, z, roof.palette[int(roof.blocks[yy, zz, xx])])
    for x in (-hx - 2, hx + 2):
        for z in (-hz - 2, hz + 2):
            underside = int(np.flatnonzero(roof.blocks[:, z - roof.origin[2], x - roof.origin[0]])[0] + roof.origin[1])
            m.box((x, 1, z), (x, underside - 1, z), PALETTE['wood'])
    start = [d['doorway']['aisle_x'], 0, -d['platform_half_z'] - 1]
    return m, {'start_floor_xyz': start, 'target_floors_xyz': {'single_hall': [0, 1, 2]},
               'actual_accessible_floors': 1, 'eave_bands': 2, 'base_design': d,
               'checks': {'central_interior_open': all(m.get(0, y, 2) == 'minecraft:air' for y in (2, 3))}}


def column_base(p):
    r = integer(p, 'radius', 2, 5); h = integer(p, 'height', 10, 28); lean = integer(p, 'inclination', 0, 4)
    if p['profile'] not in ('straight', 'taper', 'entasis'): raise ValueError('Unknown column profile')
    m = Model((-r - 3, 0, -r - 3), (r + lean + 3, h + 5, r + 3))
    m.box(tuple(m.origin), (int(m.maximum[0]), 0, int(m.maximum[2])), PALETTE['stone'])
    for y in (1, 2):
        radius = r + 2 - y
        m.box((-radius, y, -radius), (radius, y, radius), 'minecraft:chiseled_stone_bricks')
    sections = []
    for j in range(h):
        t = j / max(1, h - 1); center = round(lean * t)
        radius = r if p['profile'] == 'straight' else max(1, r - round(t))
        if p['profile'] == 'entasis': radius = max(1, r - 1 + round(math.sin(math.pi * t)))
        for x in range(center - radius, center + radius + 1):
            for z in range(-radius, radius + 1):
                if (x - center) ** 2 + z ** 2 <= (radius + .25) ** 2: m.set(x, j + 3, z, PALETTE['wood'])
        sections.append({'y': j + 3, 'center_x': center, 'radius': radius})
    return m, {'sections': sections, 'scale': 'enlarged block diagram', 'checks': {'plinth_below_shaft': m.get(0, 2, 0) != 'minecraft:air'}}


def bracket_panel(p):
    w = integer(p, 'half_span', 7, 16); h = integer(p, 'height', 7, 14); drop = integer(p, 'drop', 2, 5)
    if drop > h - 3: raise ValueError('Decorative drop leaves insufficient opening')
    m = Model((-w - 2, 0, -2), (w + 2, h + 2, 2)); m.box(tuple(m.origin), (w + 2, 0, 2), PALETTE['stone'])
    for x in (-w, w): m.box((x, 1, 0), (x, h, 0), PALETTE['wood'])
    m.box((-w, h, 0), (w, h + 1, 0), PALETTE['beam'])
    for x in range(-w + 1, w):
        side_distance = w - abs(x); depth = max(0, drop - side_distance + 1)
        if depth: m.box((x, h - depth, 0), (x, h - 1, 0), PALETTE['beam'])
        elif x % 3 == 0: m.set(x, h - 1, 0, 'minecraft:dark_oak_slab[type=top,waterlogged=false]')
    return m, {'checks': {'central_opening': all(m.get(x, y, 0) == 'minecraft:air' for x in (-1, 0, 1) for y in (1, 2, 3))},
               'scale': 'enlarged abstract beam-end and hanging-panel study; no named historic carving motif'}


def ridge_end(p):
    w = integer(p, 'half_span', 8, 20); rise = integer(p, 'rise', 3, 10)
    if p['form'] not in ('swallowtail', 'abstract_end'): raise ValueError('Unknown ridge-end form')
    m = Model((-w - 4, 0, -4), (w + 4, rise + 3, 4)); m.box((-w, 0, -1), (w, 1, 1), 'minecraft:bricks')
    for sign in (-1, 1):
        if p['form'] == 'swallowtail':
            for side in (-1, 1): line(m, [sign * (w - 4), 1, 0], [sign * (w + 2), rise, side * 3], 'minecraft:brick_slab[type=double,waterlogged=false]')
        else:
            line(m, [sign * (w - 3), 1, 0], [sign * w, rise, 0], 'minecraft:chiseled_stone_bricks')
            line(m, [sign * w, rise, 0], [sign * (w - 2), rise + 1, 0], 'minecraft:chiseled_stone_bricks')
    return m, {'checks': {'ridge_continuous': all(m.get(x, 0, 0) != 'minecraft:air' for x in range(-w, w + 1))},
               'scale': 'enlarged original silhouette, not measured Minnan ridge craft or ranked roof animals'}


def tile_course(p):
    r = integer(p, 'radius', 2, 5); count = integer(p, 'courses', 2, 7); length = integer(p, 'length', 8, 28)
    end = count * 2 * r
    m = Model((-r, 0, 0), (end + r, r + 3, length + 2)); m.box(tuple(m.origin), (end + r, 0, length + 2), 'minecraft:bricks')
    heights = {dx: 1 + round(math.sqrt(max(0, r * r - dx * dx))) for dx in range(-r, r + 1)}
    for center in range(0, end + 1, 2 * r):
        for dx in range(-r, r + 1):
            y = heights[dx]
            lower = min(max(1, y - 1), heights.get(dx - 1, y), heights.get(dx + 1, y))
            m.box((center + dx, lower, 1), (center + dx, y, length), 'minecraft:deepslate_tiles')
            m.set(center + dx, y, 0, 'minecraft:polished_blackstone')
    return m, {'checks': {'rows_have_open_channels': all(m.get(x, 1, length // 2) == 'minecraft:air' for x in range(0, end + 1, 2 * r))},
               'scale': 'enlarged curved tile courses with schematic end caps; ordinary Minecraft roofs use slabs/stairs at a different scale'}


def wall_corner(p):
    length = integer(p, 'length', 12, 36); h = integer(p, 'height', 5, 14); thick = integer(p, 'thickness', 1, 3)
    m = Model((-1, 0, -1), (length + 1, h + 2, length + 1)); m.box(tuple(m.origin), (length + 1, 0, length + 1), PALETTE['stone'])
    for y in range(1, h + 1):
        for x in range(length + 1):
            state = 'minecraft:stone_bricks' if (x + y // 2) % 4 else 'minecraft:cracked_stone_bricks'
            m.box((x, y, 0), (x, y, thick - 1), state)
        for z in range(thick, length + 1): m.box((0, y, z), (thick - 1, y, z), 'minecraft:stone_bricks')
    m.box((-1, h + 1, -1), (length + 1, h + 1, thick), 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
    m.box((-1, h + 1, thick + 1), (thick, h + 1, length + 1), 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
    return m, {'checks': {'corner_tied': all(m.get(0, y, 0) != 'minecraft:air' for y in range(h + 2))},
               'scale': 'block-scale L wall and staggered color joints, not an actual brick-bond simulation'}


def drainage_strip(p):
    length = integer(p, 'length', 16, 64); drop = integer(p, 'drop', 0, 6)
    if drop > length // 6: raise ValueError('Drain drop exceeds shallow stepped profile')
    m = Model((-5, -drop - 2, 0), (5, 4, length)); m.box(tuple(m.origin), (5, -drop - 1, length), PALETTE['stone'])
    channel = []
    for z in range(length + 1):
        fy = -round(drop * z / length)
        m.box((-2, fy, z), (2, fy, z), 'minecraft:stone_bricks')
        for x in (-4, 4): m.set(x, fy - 1, z, 'minecraft:polished_andesite'); channel.append([x, fy - 1, z])
        for x in (-5, -3, 3, 5): m.box((x, fy - 1, z), (x, fy, z), PALETTE['stone'])
    return m, {'start_floor_xyz': [0, 0, 0], 'target_floors_xyz': {'lower_end': [0, -drop, length]},
               'channel_bottom_xyz': channel, 'checks': {'channel_falls_toward_outlet': all(channel[i][1] >= channel[i + 2][1] for i in range(len(channel) - 2))},
               'scale': 'dry geometric drain alongside a five-wide path; no fluid simulation or claim of rainfall capacity'}


def log_frame(p):
    w = integer(p, 'half_width', 6, 12); d = integer(p, 'half_depth', 5, 10); h = integer(p, 'height', 7, 16)
    m = Model((-w - 2, 0, -d - 2), (w + 2, h + 1, d + 2)); m.box(tuple(m.origin), (w + 2, 0, d + 2), PALETTE['stone'])
    for y in range(1, h + 1):
        for z in (-d, d): m.box((-w - (y % 2), y, z), (w + (y % 2), y, z), 'minecraft:spruce_log[axis=x]')
        for x in (-w, w): m.box((x, y, -d - (1 - y % 2)), (x, y, d + (1 - y % 2)), 'minecraft:spruce_log[axis=z]')
    m.box((-1, 1, -d), (1, 3, -d), 'minecraft:air')
    return m, {'start_floor_xyz': [0, 0, -d - 1], 'target_floors_xyz': {'interior': [0, 0, 0]},
               'checks': {'doorway_open': all(m.get(0, y, -d) == 'minecraft:air' for y in (1, 2, 3))},
               'scale': 'stacked-log corner relationship only, without notched joints or a complete regional house'}


def make_detail(user):
    if not isinstance(user, dict) or set(user) - {'schema', 'kind', 'parameters', 'evidence'}: raise ValueError('Unknown detail keys')
    k = user.get('kind', 'double_eave_hall'); params = user.get('parameters', {})
    if k not in DEFAULTS or user.get('schema', 'chinese-detail/1') != 'chinese-detail/1': raise ValueError('Unknown detail kind or schema')
    if not isinstance(params, dict) or set(params) - set(DEFAULTS[k]): raise ValueError('Unknown parameters for ' + k)
    c = {'schema': 'chinese-detail/1', 'kind': k, 'parameters': {**DEFAULTS[k], **params}, 'evidence': user.get('evidence', [])}
    m, d = globals()[k](c['parameters'])
    if 'start_floor_xyz' in d:
        d['routes'] = walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz']); d['checks']['targets_reachable'] = d['routes']['all_targets_reached']
    d['checks']['face_connected_geometry'] = label(m.blocks != 0)[1] == 1
    if not all(d['checks'].values()): raise AssertionError(d['checks'])
    return m, {'schema': 'chinese-detail-design/1', 'config': c, **d, 'historical_reconstruction': False,
               'source_structure_reuse': False, 'limits': 'Authored geometry with explicit scale; not surveyed joinery, historic proportions or physical simulation.'}


def export(user, out_dir, name='detail', render=True):
    m, d = make_detail(user)
    return export_scene(m, d, out_dir, name, 'chinese-detail', render)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--config', type=Path); ap.add_argument('--out-dir', required=True, type=Path)
    ap.add_argument('--name', default='detail'); ap.add_argument('--no-render', action='store_true'); a = ap.parse_args()
    r = export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {}, a.out_dir, a.name, not a.no_render)
    print(json.dumps({k: r[k] for k in ('name', 'dimensions_xyz', 'nonair_voxels')}, ensure_ascii=False))
