"""Apply deterministic material finishes to a new NPZ copy without moving voxels."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re

import numpy as np

from block_shapes import shape_for
from scene_output import export_scene
from model_provenance import source_record, guard_input_outputs
from voxel_model import Model, split_state, join_state


def make_finish(config):
    if not isinstance(config, dict) or set(config) - {'schema', 'model_file', 'seed', 'rules', 'evidence'} or config.get('schema') != 'chinese-finish/1':
        raise ValueError('Invalid finish configuration')
    path = Path(config['model_file']); seed = config.get('seed', 0); rules = config.get('rules', [])
    if type(seed) is not int or not 0 <= seed <= 0xffffffff: raise ValueError('seed must be a uint32 integer')
    if not isinstance(rules, list) or not 1 <= len(rules) <= 32: raise ValueError('Need 1..32 ordered material rules')
    m = Model.load(path); original = m.blocks.copy(); original_palette = list(m.palette); occupied = original != 0
    exposed = np.zeros_like(occupied)
    for axis in range(3):
        for direction in (-1, 1):
            neighbor = np.roll(occupied, direction, axis=axis)
            edge = [slice(None)] * 3; edge[axis] = 0 if direction == 1 else -1; neighbor[tuple(edge)] = False
            exposed |= occupied & ~neighbor
    records = []; unverified = set()
    for index, rule in enumerate(rules):
        if not isinstance(rule, dict) or set(rule) - {'pattern', 'state', 'fraction', 'where', 'y_range', 'preserve_properties'}:
            raise ValueError('Unknown finish-rule keys')
        pattern = re.compile(rule['pattern']); dest_base, dest_props = split_state(rule['state'])
        fraction = rule.get('fraction', 1.0); region = rule.get('where', 'exposed'); preserve = rule.get('preserve_properties', False)
        if type(fraction) not in (int, float) or not np.isfinite(fraction) or not 0 <= fraction <= 1: raise ValueError('fraction must be finite 0..1')
        if region not in ('exposed', 'all') or type(preserve) is not bool: raise ValueError('Invalid surface or preserve_properties')
        if dest_base in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'): raise ValueError('Finish may not remove geometry')
        if preserve and dest_props: raise ValueError('With preserve_properties use an unqualified destination block ID')
        lookup = np.array([i != 0 and bool(pattern.search(state)) for i, state in enumerate(original_palette)])
        selected = lookup[original]
        if region == 'exposed': selected &= exposed
        if 'y_range' in rule:
            pair = rule['y_range']
            if not isinstance(pair, list) or len(pair) != 2 or any(type(v) is not int for v in pair) or pair[0] > pair[1]: raise ValueError('Ordered integer y_range required')
            yy = (np.arange(m.blocks.shape[0]) + m.origin[1])[:, None, None]; selected &= (yy >= pair[0]) & (yy <= pair[1])
        y, z, x = np.where(selected)
        worldx, worldy, worldz = x + m.origin[0], y + m.origin[1], z + m.origin[2]
        hashes = ((worldx * 73856093) ^ (worldy * 19349663) ^ (worldz * 83492791) ^ ((seed * 2654435761) & 0xffffffff) ^ (index * 97531)) & 0xffffffff
        for _ in range(2): hashes = ((hashes ^ (hashes >> 16)) * 0x45d9f3b) & 0xffffffff
        hashes ^= hashes >> 16
        chosen = hashes.astype(np.float64) / 4294967296 < fraction
        y, z, x = y[chosen], z[chosen], x[chosen]
        source_ids = original[y, z, x]; mapping = {}
        for pid in np.unique(source_ids):
            old = original_palette[int(pid)]; old_base, old_props = split_state(old)
            if preserve:
                # Properties transfer only within an explicit shape family.
                def family(base):
                    return next((s for s in ('_stairs', '_slab', '_fence', '_wall', '_pane', '_log', '_wood') if base.endswith(s)), None)
                if old_props and (not family(old_base) or family(old_base) != family(dest_base)):
                    raise ValueError('Cannot infer property compatibility between ' + old_base + ' and ' + dest_base)
            target = join_state(dest_base, old_props if preserve else dest_props)
            old_shape, old_kind = shape_for(old); new_shape, new_kind = shape_for(target)
            if not np.array_equal(old_shape, new_shape): raise ValueError('A finish changes the supported block shape: ' + old + ' -> ' + target)
            if old_kind == 'cube_fallback' or new_kind == 'cube_fallback': unverified.update((old, target))
            mapping[int(pid)] = m.pid(target)
        for pid, new in mapping.items():
            at = source_ids == pid; m.blocks[y[at], z[at], x[at]] = new
        records.append({'index': index, 'matched_original_voxels': int(selected.sum()), 'selected_voxels': len(y), 'rule': rule})
    checks = {'occupied_cells_unchanged': bool(np.array_equal(occupied, m.blocks != 0)),
              'source_file_unchanged': True}
    # Compare canonical strings because palette indices alone are not states.
    changed = int(np.count_nonzero(np.asarray(original_palette)[original] != np.asarray(m.palette)[m.blocks]))
    return m, {'schema': 'chinese-finish-design/1', 'config': config, 'input_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
               'source_model': source_record(path),
               'rules': records, 'changed_voxels': changed, 'checks': checks,
               'unverified_cube_fallback_states': sorted(unverified), 'historical_reconstruction': False,
               'source_structure_reuse': True,
               'limits': 'Rules match original states; later rules win. Supported shape masks and occupancy are preserved. Unknown shape fallbacks and target block registry need separate validation. Materials are artistic choices, not evidence of historical paint or weathering.'}


def export(config, out_dir, name='finished', render=True):
    guard_input_outputs(config, out_dir, name)
    source = Path(config['model_file']).resolve(); output = Path(out_dir).resolve()
    if source in (output / f'{name}.npz', output / f'{name}.schem'): raise ValueError('Write the finish to a new name/directory, preserving the source')
    before = hashlib.sha256(source.read_bytes()).hexdigest(); m, d = make_finish(config)
    report = export_scene(m, d, out_dir, name, 'chinese-finish', render)
    if hashlib.sha256(source.read_bytes()).hexdigest() != before: raise IOError('Source changed during export')
    report['limits'][2] = 'Derived from the explicitly supplied model. Preserve its authorship and redistribution restrictions; geometry positions are retained.'
    (Path(out_dir) / f'{name}.report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    return report
