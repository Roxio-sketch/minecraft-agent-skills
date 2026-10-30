"""Fit a uniform source-unit to Minecraft-block scale from paired landmarks.

Only quarter-turn yaw plus translation is fitted. Perspective drawings, mixed
scales and nonlinear deformations cannot be repaired by this calibration.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np


def calibrate(config):
    if not isinstance(config, dict) or set(config) - {'schema', 'source_units', 'pairs', 'quarter_turns', 'max_rms_blocks', 'source_evidence'}:
        raise ValueError('Unknown reference-calibration keys')
    if config.get('schema') != 'architecture-reference-calibration/1' or not isinstance(config.get('source_units'), str) or not config['source_units']:
        raise ValueError('Calibration schema and named source units required')
    pairs = config.get('pairs')
    if not isinstance(pairs, list) or not 3 <= len(pairs) <= 1000: raise ValueError('Provide 3..1000 paired landmarks')
    if any(not isinstance(p, dict) or set(p) != {'id', 'source_xyz', 'target_xyz'} for p in pairs): raise ValueError('Every pair needs id/source_xyz/target_xyz')
    if len({p['id'] for p in pairs}) != len(pairs): raise ValueError('Landmark IDs must be unique')
    source = np.asarray([p['source_xyz'] for p in pairs], dtype=float); target = np.asarray([p['target_xyz'] for p in pairs], dtype=float)
    if source.shape != (len(pairs), 3) or target.shape != source.shape or not np.all(np.isfinite([source, target])): raise ValueError('Finite XYZ landmarks required')
    centered = source - source.mean(axis=0)
    if np.linalg.matrix_rank(centered) < 2: raise ValueError('Source landmarks must not be collinear')
    supplied = config.get('quarter_turns'); turns = range(4) if supplied is None else [supplied]
    if supplied is not None and (type(supplied) is not int or not 0 <= supplied <= 3): raise ValueError('quarter_turns must be 0..3')
    tolerance = config.get('max_rms_blocks', .5)
    if type(tolerance) not in (int, float) or not np.isfinite(tolerance) or tolerance < 0: raise ValueError('max_rms_blocks must be finite and nonnegative')
    candidates = []
    for q in turns:
        rotated = source.copy()
        for _ in range(q): rotated[:, [0, 2]] = np.column_stack((-rotated[:, 2], rotated[:, 0]))
        src = rotated - rotated.mean(axis=0); dst = target - target.mean(axis=0)
        scale = float(np.sum(src * dst) / np.sum(src * src))
        if scale <= 0: continue
        translation = target.mean(axis=0) - scale * rotated.mean(axis=0)
        projected = rotated * scale + translation
        residuals = np.linalg.norm(projected - target, axis=1)
        candidates.append((float(np.sqrt(np.mean(residuals ** 2))), q, scale, translation, projected, residuals))
    if not candidates: raise ValueError('No positive scale fits the allowed orientation')
    rms, q, scale, offset, projected, errors = min(candidates, key=lambda v: v[0])
    return {'schema': 'architecture-reference-calibration-result/1', 'source_units': config['source_units'],
            'quarter_turns_clockwise': q, 'blocks_per_source_unit': scale, 'translation_blocks_xyz': offset.tolist(),
            'rms_blocks': rms, 'max_error_blocks': float(max(errors)), 'within_requested_tolerance': rms <= tolerance,
            'landmarks': [{'id': p['id'], 'projected_xyz': v.tolist(), 'nearest_grid_xyz': np.rint(v).astype(int).tolist(),
                           'error_blocks': float(error)} for p, v, error in zip(pairs, projected, errors)],
            'source_evidence': config.get('source_evidence', []),
            'limits': 'Landmark scale/orientation fit only. Does not infer perspective, lost dimensions, hidden structure, historic dates, joinery or authenticity.'}


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--pairs', required=True, type=Path); ap.add_argument('--out', required=True, type=Path); a = ap.parse_args()
    if a.out.resolve() == a.pairs.resolve(): ap.error('Preserve input landmarks; choose a separate output')
    result = calibrate(json.loads(a.pairs.read_text('utf-8-sig'))); result['input_sha256'] = hashlib.sha256(a.pairs.read_bytes()).hexdigest()
    a.out.parent.mkdir(parents=True, exist_ok=True); a.out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(result, ensure_ascii=False))
    if not result['within_requested_tolerance']: raise SystemExit(1)
