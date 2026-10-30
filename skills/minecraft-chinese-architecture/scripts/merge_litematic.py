"""Merge explicitly selected Litematica regions at their stored relative positions."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from import_structure import read_nbt, load_structure, volume
from voxel_model import Model


def merge_regions(source, regions, conflict='error', include_air=False, max_voxels=8_000_000):
    if not isinstance(regions, list) or not regions or len(regions) > 128 or len(set(regions)) != len(regions):
        raise ValueError('Select 1..128 distinct region names in the intended precedence order')
    if conflict not in ('error', 'keep', 'replace') or type(include_air) is not bool: raise ValueError('Invalid conflict/include_air')
    root = read_nbt(source); available = root.get('Regions', {})
    if root.get('Version') not in (5, 6) or not available: raise ValueError('Litematica v5/v6 regions required')
    if any(name not in available for name in regions): raise ValueError('Selected region is absent')
    parts = []; bounds = []; total = 0
    for name in regions:
        raw = available[name]; size = [abs(int(raw['Size'][axis])) for axis in 'xyz']
        total += volume(size, max_voxels)
        if total > max_voxels: raise ValueError('Sum of decoded region volumes exceeds budget')
        a, states, origin, report = load_structure(source, region=name, max_voxels=max_voxels)
        end = origin + [a.shape[2] - 1, a.shape[0] - 1, a.shape[1] - 1]
        model = Model(origin, end, max_voxels=max_voxels)
        mapping = np.array([model.pid(str(s)) for s in states], dtype=np.uint16); model.blocks = mapping[a]
        parts.append((name, model, report)); bounds.append((origin, end))
    merged = Model(np.min([a for a, b in bounds], axis=0), np.max([b for a, b in bounds], axis=0), max_voxels=max_voxels)
    receipts = {}
    for name, model, report in parts: receipts[name] = merged.paste(model, conflict=conflict, include_air=include_air)
    report = {'schema': 'architecture-region-merge/1', 'source': str(Path(source).resolve()),
              'source_sha256': hashlib.sha256(Path(source).read_bytes()).hexdigest(), 'ordered_regions': regions,
              'conflict': conflict, 'include_air': include_air, 'placements': receipts,
              'origin_xyz': merged.origin.tolist(), 'size_xyz': (merged.maximum - merged.origin + 1).tolist(),
              'data_version': int(root['MinecraftDataVersion']), 'source_region_reports': [r for n, m, r in parts],
              'limits': 'Stored paste-relative positions preserved; not world coordinates. Geometry only, with entity and block-entity payloads omitted. Later regions win only with explicit replace.'}
    return merged, report


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('source', type=Path); ap.add_argument('--regions', nargs='+', required=True)
    ap.add_argument('--out', type=Path, required=True); ap.add_argument('--conflict', choices=('error', 'keep', 'replace'), default='error')
    ap.add_argument('--include-air', action='store_true'); a = ap.parse_args()
    if a.out.suffix != '.npz' or a.out.resolve() == a.source.resolve(): ap.error('Write a separate NPZ analysis file')
    m, r = merge_regions(a.source, a.regions, a.conflict, a.include_air)
    a.out.parent.mkdir(parents=True, exist_ok=True); m.save(a.out)
    a.out.with_suffix('.merge.json').write_text(json.dumps(r, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(r, ensure_ascii=False))
