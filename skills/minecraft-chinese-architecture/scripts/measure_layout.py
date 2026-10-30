"""Measure annotated material volumes and paired doors without inferring room use."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

import numpy as np

from voxel_model import Model, split_state


def query(model, box, pattern):
    if not isinstance(box, list) or len(box) != 6 or any(type(n) is not int for n in box):
        raise ValueError('Query box needs six integer XYZ bounds')
    lo, hi = np.array(box[:3]), np.array(box[3:])
    if np.any(lo > hi) or np.any(lo < model.origin) or np.any(hi > model.maximum):
        raise ValueError('Query box is reversed or outside model')
    if not isinstance(pattern, str) or not pattern:
        raise ValueError('Query needs an explicit nonempty material pattern')
    rule = re.compile(pattern)
    x0, y0, z0 = lo - model.origin
    x1, y1, z1 = hi - model.origin
    a = model.blocks[y0:y1+1, z0:z1+1, x0:x1+1]
    mask = np.array([bool(rule.search(s)) for s in model.palette])[a]
    y, z, x = np.where(mask)
    counts = Counter()
    for i, n in zip(*np.unique(a[mask], return_counts=True)):
        counts[model.palette[int(i)]] += int(n)
    return {'box_xyz_inclusive': box, 'pattern': pattern,
            'query_volume': int(a.size), 'matched_cells': len(y),
            'bounds_xyz_inclusive': [int(x.min()+lo[0]), int(y.min()+lo[1]), int(z.min()+lo[2]),
                                     int(x.max()+lo[0]), int(y.max()+lo[1]), int(z.max()+lo[2])] if len(y) else None,
            'layers_y': dict(sorted(Counter(map(int, y+lo[1])).items())),
            'state_counts': dict(sorted(counts.items()))}


def paired_doors(model):
    """Count matching lower/upper state pairs; this is not an operability test."""
    selected = []
    for state in model.palette:
        base, props = split_state(state)
        selected.append(base.endswith('_door') and props.get('half') in ('lower', 'upper'))
    y, z, x = np.where(np.array(selected)[model.blocks])
    cells = {}
    for iy, iz, ix in zip(y, z, x):
        pos = tuple(int(v) for v in model.origin + [ix, iy, iz])
        cells[pos] = model.palette[int(model.blocks[iy, iz, ix])]
    pairs, orphans, consumed = [], [], set()
    for pos, state in sorted(cells.items()):
        base, props = split_state(state)
        if props['half'] != 'lower': continue
        upper = (pos[0], pos[1]+1, pos[2])
        target = cells.get(upper)
        expected = {**props, 'half': 'upper'}
        if target is not None and split_state(target) == (base, expected):
            pairs.append({'lower_xyz': list(pos), 'upper_xyz': list(upper), 'lower_state': state})
            consumed.update((pos, upper))
    orphans = [{'xyz': list(pos), 'state': state} for pos, state in sorted(cells.items()) if pos not in consumed]
    return {'matched_pairs': len(pairs), 'pairs': pairs, 'unpaired_cells': orphans,
            'limits': 'State pairing only; two adjacent doors are two pairs, not two separate entrances. Mod shape and interaction are unverified.'}


def measure(samples_dir, annotations):
    root = Path(samples_dir)
    if annotations.get('schema') != 'architecture-layout-annotations/1':
        raise ValueError('Expected architecture-layout-annotations/1')
    results = []
    for item in annotations['samples']:
        path = root / item['model']
        provenance = json.loads(path.with_suffix('.import.json').read_text('utf8'))
        if not item.get('source_sha256') or item['source_sha256'] != provenance['sha256']:
            raise ValueError('Source hash differs from annotation: ' + item['id'])
        model = Model.load(path)
        ids = [q['id'] for q in item['queries']]
        if len(ids) != len(set(ids)): raise ValueError('Repeated query id')
        results.append({'id': item['id'], 'source': item['source'],
                        'source_sha256': provenance['sha256'],
                        'analysis_npz_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                        'origin_xyz': model.origin.tolist(), 'maximum_xyz': model.maximum.tolist(),
                        'queries': {q['id']: query(model, q['box'], q['pattern']) for q in item['queries']},
                        'doors': paired_doors(model), 'interpretation': item.get('interpretation', ''),
                        'coordinate_space': 'Imported structure coordinates; not source world coordinates'})
    return {'schema': 'architecture-layout-measurements/1', 'samples': results,
            'limits': 'Annotated cell statistics, not automatic room or building recognition. NPZ air may include omitted source positions. No payload, collision or redstone verification.'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--samples-dir', type=Path, required=True)
    p.add_argument('--annotations', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    result = measure(a.samples_dir, json.loads(a.annotations.read_text('utf8')))
    a.out.parent.mkdir(parents=True, exist_ok=True)
    a.out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps({'output': str(a.out), 'samples': len(result['samples'])}, ensure_ascii=False))


if __name__ == '__main__': main()
