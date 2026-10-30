"""Editable original raised/incised relief and truly pierced lattice panels."""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from ornament_kernel import relief_mask, screen_mask, depth_field
from scene_output import export_scene
from voxel_model import Model, split_state, join_state


DEFAULTS = {
    'relief_panel': {'width': 35, 'height': 23, 'frame': 2, 'depth': 3, 'backing': 2,
                     'motif': 'lotus', 'mode': 'raised', 'stroke_width': 3, 'repeats': 3},
    'lattice_screen': {'width': 31, 'height': 23, 'frame': 2, 'depth': 2,
                       'pattern': 'diagonal', 'spacing': 7, 'bar_width': 1, 'seed': 0},
}
PALETTE = {'body': 'minecraft:stone_bricks', 'frame': 'minecraft:polished_andesite',
           'carving': 'minecraft:chiseled_stone_bricks'}


def integer(p, key, low, high):
    value = p[key]
    if type(value) is not int or not low <= value <= high:
        raise ValueError(f'{key} must be an integer {low}..{high}')
    return value


def make_ornament(user):
    if not isinstance(user, dict) or set(user) - {'schema', 'kind', 'parameters', 'palette', 'evidence'}:
        raise ValueError('Unknown ornament configuration keys')
    kind = user.get('kind', 'relief_panel'); params = user.get('parameters', {}); palette = user.get('palette', {})
    if user.get('schema', 'chinese-ornament/1') != 'chinese-ornament/1' or kind not in DEFAULTS:
        raise ValueError('Unknown ornament schema or kind')
    if not isinstance(params, dict) or set(params) - set(DEFAULTS[kind]): raise ValueError('Unknown ornament parameters')
    if not isinstance(palette, dict) or set(palette) - set(PALETTE): raise ValueError('Unknown ornament palette roles')
    c = {'schema': 'chinese-ornament/1', 'kind': kind, 'parameters': {**DEFAULTS[kind], **params},
         'palette': {**PALETTE, **palette}, 'evidence': copy.deepcopy(user.get('evidence', []))}
    p, materials = c['parameters'], c['palette']
    for role, state in materials.items():
        base, props = split_state(state)
        if base.endswith(('air', '_stairs', '_slab', '_fence', '_wall', '_pane', '_door', '_trapdoor')) or base in ('minecraft:water', 'minecraft:lava'):
            raise ValueError(f'palette.{role} needs a full solid block for carving geometry')
        materials[role] = join_state(base, props)
    w = integer(p, 'width', 15, 81); h = integer(p, 'height', 13, 65)
    f = integer(p, 'frame', 1, 4); dep = integer(p, 'depth', 1, 8)
    if 2*f + 7 > min(w, h): raise ValueError('Frame leaves insufficient motif space')
    iw, ih = w - 2*f, h - 2*f; lo = -(w // 2)
    if kind == 'relief_panel':
        backing = integer(p, 'backing', 1, 5); sw = integer(p, 'stroke_width', 1, 5); repeat = integer(p, 'repeats', 1, 8)
        if sw % 2 != 1: raise ValueError('stroke_width must be odd')
        if p['mode'] not in ('raised', 'incised'): raise ValueError('mode must be raised or incised')
        mask = relief_mask(iw, ih, p['motif'], sw, repeat); depths = depth_field(mask, dep)
        rear = backing - 1 + (dep if p['mode'] == 'incised' else 0)
        m = Model((lo, 0, -dep), (lo + w - 1, h - 1, rear))
        m.box((lo, 0, 0), (lo + w - 1, h - 1, rear), materials['body'])
        for yy, xx in zip(*np.where(mask)):
            x, y, amount = lo + f + int(xx), f + int(yy), int(depths[yy, xx])
            if p['mode'] == 'raised': m.box((x, y, -amount), (x, y, -1), materials['carving'])
            else:
                m.box((x, y, 0), (x, y, amount - 1), 'minecraft:air')
                m.set(x, y, amount, materials['carving'])
        front_frame = -1 if p['mode'] == 'raised' else 0
        checks = {'solid_backing_survives': bool(np.all(m.blocks[:, -1, :] != 0)),
                  'motif_contains_depth': bool(np.max(depths) == dep and np.min(depths) == 0)}
        detail = {'mode': p['mode'], 'motif': p['motif'], 'depth_rows_yx': depths.tolist(),
                  'preserved_backing_layers': backing, 'motif_cells': int(mask.sum())}
    else:
        spacing = integer(p, 'spacing', 4, 14); bars = integer(p, 'bar_width', 1, 3); integer(p, 'seed', 0, 0xffffffff)
        if bars % 2 != 1 or bars >= spacing-1: raise ValueError('bar_width must be odd and leave open cells')
        mask = screen_mask(iw, ih, p['pattern'], spacing, bars, p['seed'])
        m = Model((lo, 0, 0), (lo + w - 1, h - 1, dep - 1))
        for yy, xx in zip(*np.where(mask)):
            m.box((lo + f + int(xx), f + int(yy), 0), (lo + f + int(xx), f + int(yy), dep - 1), materials['carving'])
        rear = dep - 1; front_frame = 0
        empty = ~mask
        checks = {'through_openings_exist': bool(np.any(empty)),
                  'openings_not_backed_by_glass': all(np.all(m.blocks[f+yy, :, f+xx] == 0) for yy, xx in zip(*np.where(empty)))}
        detail = {'pattern': p['pattern'], 'bar_mask_rows_yx': mask.astype(int).tolist(),
                  'open_projected_cells': int(empty.sum()), 'clear_aperture_fraction': round(float(empty.mean()), 4),
                  'opening_regions': int(label(empty)[1]), 'backing': 'none'}
    for y0, y1 in ((0, f - 1), (h - f, h - 1)):
        m.box((lo, y0, front_frame), (lo + w - 1, y1, rear), materials['frame'])
    for x0, x1 in ((lo, lo+f-1), (lo+w-f, lo+w-1)):
        m.box((x0, f, front_frame), (x1, h-f-1, rear), materials['frame'])
    checks['face_connected_geometry'] = label(m.blocks != 0)[1] == 1
    if not all(checks.values()): raise AssertionError(checks)
    return m, {'schema': 'chinese-ornament-design/1', 'config': c, 'checks': checks, **detail,
               'panel_bounds_xyz': [m.origin.tolist(), m.maximum.tolist()], 'front_direction': 'north',
               'routes': {'status': 'not_requested_geometry_component'}, 'historical_reconstruction': False,
               'source_structure_reuse': False,
               'scale': 'Voxel carving study; each depth layer is one block. Resize deliberately before mounting on a normal-scale house.',
               'limits': 'Original geometric motifs and solid-block cut/raise operations, not a surveyed artisan pattern or structural strength calculation.'}


def export(user, out_dir, name='ornament', render=True):
    m, d = make_ornament(user); return export_scene(m, d, out_dir, name, 'chinese-ornament', render)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--config', type=Path); ap.add_argument('--out-dir', required=True, type=Path)
    ap.add_argument('--name', default='ornament'); ap.add_argument('--no-render', action='store_true'); a = ap.parse_args()
    r = export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {}, a.out_dir, a.name, not a.no_render)
    print(json.dumps({k: r[k] for k in ('name', 'dimensions_xyz', 'nonair_voxels')}, ensure_ascii=False))
