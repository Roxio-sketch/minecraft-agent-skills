"""Read classic numeric-ID schematic geometry using an explicit versioned map.

Unknown ID:data pairs are errors. No guessed mod mapping, entity execution or
automatic assertion of compatibility with a newer Minecraft registry.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from import_structure import read_nbt, volume
from voxel_model import Model


def import_legacy(source, mapping_path, max_voxels=8_000_000):
    root = read_nbt(source)
    if root.get('Materials', 'Alpha') != 'Alpha' or any(k not in root for k in ('Width', 'Height', 'Length', 'Blocks', 'Data')):
        raise ValueError('Classic Alpha schematic Width/Height/Length/Blocks/Data required')
    size = [int(root[k]) & 65535 for k in ('Width', 'Height', 'Length')]; count = volume(size, max_voxels)
    ids = np.asarray(root['Blocks'], dtype=np.int64).reshape(-1) & 255
    metadata = np.asarray(root['Data'], dtype=np.int64).reshape(-1) & 255
    if len(ids) != count or len(metadata) != count or np.any(metadata > 15): raise ValueError('Invalid block/data array lengths or metadata nibble')
    if 'AddBlocks' in root:
        high = np.asarray(root['AddBlocks'], dtype=np.int64).reshape(-1) & 255
        if len(high) != (count + 1) // 2: raise ValueError('Invalid AddBlocks nibble length')
        positions = np.arange(count); ids |= ((high[positions // 2] >> ((positions % 2) * 4)) & 15) << 8
    document = json.loads(Path(mapping_path).read_text('utf-8-sig'))
    mapping = document.get('blocks', document)
    if not isinstance(mapping, dict) or not mapping: raise ValueError('ID:data to state mapping required')
    pairs = ids * 16 + metadata; used = np.unique(pairs)
    missing = [f'{int(v) // 16}:{int(v) % 16}' for v in used if f'{int(v) // 16}:{int(v) % 16}' not in mapping]
    if missing: raise ValueError('Unmapped legacy states: ' + ', '.join(missing[:64]))
    offset = [int(root.get('WEOffset' + a.upper(), 0)) for a in 'xyz']
    m = Model(offset, np.array(offset) + np.array(size) - 1, max_voxels=max_voxels)
    lookup = np.zeros(int(used[-1]) + 1, dtype=np.uint16)
    for value in used:
        key = f'{int(value) // 16}:{int(value) % 16}'
        state = mapping[key]
        if not isinstance(state, str): raise ValueError('Mapping states must be strings: ' + key)
        m.pid(state); lookup[value] = m.pid(state)
    m.blocks = lookup[pairs].reshape(size[1], size[2], size[0])
    return m, {'schema': 'architecture-legacy-import/1', 'source': str(Path(source).resolve()),
               'source_sha256': hashlib.sha256(Path(source).read_bytes()).hexdigest(),
               'mapping': str(Path(mapping_path).resolve()), 'mapping_sha256': hashlib.sha256(Path(mapping_path).read_bytes()).hexdigest(),
               'size_xyz': size, 'origin_xyz': offset, 'numeric_state_count': len(used),
               'omitted_entities': len(root.get('Entities', [])), 'omitted_block_entities': len(root.get('TileEntities', [])),
               'mapping_metadata': document.get('metadata'),
               'limits': 'Classic schematic only; old numeric-ID Anvil worlds still require explicit conversion. Mapping version controls names/properties. Paired legacy block relationships are not reconstructed unless fully represented in the supplied map. WorldEdit origin tags are not verified original-world coordinates.'}


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('source', type=Path); ap.add_argument('--mapping', required=True, type=Path)
    ap.add_argument('--out', required=True, type=Path); a = ap.parse_args()
    if a.out.suffix != '.npz' or a.out.resolve() in (a.source.resolve(), a.mapping.resolve()): ap.error('Write a separate .npz analysis output')
    m, r = import_legacy(a.source, a.mapping); a.out.parent.mkdir(parents=True, exist_ok=True); m.save(a.out)
    a.out.with_suffix('.import.json').write_text(json.dumps(r, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(r, ensure_ascii=False))
