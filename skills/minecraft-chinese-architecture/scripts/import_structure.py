"""Read Sponge, Litematica or Java/Create structure NBT into analysis NPZ.

Geometry only: entities, inventories and command payloads are never exported.
The saved origin is paste-relative, NOT a recovered source-world coordinate.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from nbt_io import Reader, state_string

AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}


def read_nbt(path, max_bytes=256 * 1024**2):
    path = Path(path)
    if path.stat().st_size > max_bytes:
        raise ValueError('Input exceeds byte budget; split the source first')
    with path.open('rb') as stream:
        magic = stream.read(2)
    opener = gzip.open if magic == b'\x1f\x8b' else open
    with opener(path, 'rb') as stream:
        raw = stream.read(max_bytes+1)
    if len(raw) > max_bytes:
        raise ValueError('Decompressed NBT exceeds byte budget; use world-region extraction')
    reader = Reader(raw)
    root = reader.root()
    if reader.pos != len(raw):
        raise ValueError('Trailing data after NBT root')
    return root


def canonical(state):
    if '[' not in state:
        return state if ':' in state else 'minecraft:'+state
    base, props = state.split('[', 1)
    if not props.endswith(']'):
        raise ValueError('Malformed block state')
    return canonical(base)+'['+','.join(sorted(props[:-1].split(',')))+']'


def volume(size, limit):
    if len(size) != 3 or any(n <= 0 for n in size):
        raise ValueError('Dimensions must be positive XYZ')
    count = math.prod(size)
    if count > limit:
        raise ValueError(f'Volume {count:,} exceeds limit {limit:,}; select a smaller region')
    return count


def varints(data, count):
    """Bounded, nonnegative 32-bit palette indexes, exactly count entries."""
    out = np.empty(count, dtype=np.uint32)
    pos = val = shift = 0
    for raw in data:
        b = int(raw) & 255
        if shift == 28 and b > 7:
            raise ValueError('Palette VarInt overflows signed 32-bit index')
        val |= (b & 127) << shift
        if b & 128:
            shift += 7
        else:
            if pos >= count:
                raise ValueError('Excess block indexes')
            out[pos] = val
            pos += 1
            val = shift = 0
    if shift or pos != count:
        raise ValueError('Truncated block indexes')
    return out


def packed_litematic(data, count, palette_size):
    """Contiguous bit packing (values cross longs), unlike modern Anvil."""
    if palette_size < 1:
        raise ValueError('Empty palette')
    bits = max(2, (palette_size-1).bit_length())
    words = np.asarray(data).astype(np.uint64)
    if len(words) != (count*bits+63)//64:
        raise ValueError('Incorrect packed array length')
    result = np.empty(count, dtype=np.uint32)
    for start in range(0, count, 262144):
        end = min(count, start+262144)
        bit = np.arange(start, end, dtype=np.uint64)*bits
        indexes, shifts = bit//64, bit % 64
        vals = words[indexes] >> shifts
        cross = shifts+bits > 64
        vals[cross] |= words[indexes[cross]+1] << (64-shifts[cross])
        result[start:end] = vals & ((1 << bits)-1)
    if np.any(result >= palette_size):
        raise ValueError('Packed index absent from palette')
    return result


def normalize(ids, palette):
    """Handle sparse source IDs, sort properties and give all air states ID 0."""
    pairs = sorted((int(i), canonical(str(s))) for s, i in palette.items())
    keys = np.array([i for i, _ in pairs], dtype=np.int64)
    if not pairs or keys[0] < 0 or len(set(keys)) != len(keys):
        raise ValueError('Empty palette, duplicate IDs or negative ID')
    slots = np.searchsorted(keys, ids)
    if np.any(slots >= len(keys)) or np.any(keys[slots] != ids):
        raise ValueError('Block index absent from palette')
    names = ['minecraft:air']
    lookup = {names[0]: 0}
    remap = []
    for _, state in pairs:
        if state in AIR:
            state = 'minecraft:air'
        if state not in lookup:
            lookup[state] = len(names)
            names.append(state)
        remap.append(lookup[state])
    dtype = np.uint16 if len(names) <= 65536 else np.uint32
    return np.asarray(remap, dtype=dtype)[slots], np.array(names)


def load_structure(path, region=None, max_voxels=8_000_000, palette_index=None):
    root = read_nbt(path)
    original_palette_count = None
    is_java_structure = all(key in root for key in ('size', 'blocks')) and ('palette' in root or 'palettes' in root)
    if palette_index is not None and not is_java_structure:
        raise ValueError('--palette-index applies only to Java structure NBT')
    if 'Regions' in root:
        version = int(root['Version'])
        if version not in (5, 6):
            raise ValueError(f'Litematica version {version} is not supported')
        regions = root['Regions']
        if region is None:
            if len(regions) != 1:
                raise ValueError('Select --region from: '+', '.join(regions))
            region = next(iter(regions))
        if region not in regions:
            raise ValueError('Unknown region: '+region)
        r = regions[region]
        signed = [int(r['Size'][k]) for k in 'xyz']
        size = list(map(abs, signed))
        count = volume(size, max_voxels)
        position = [int(r['Position'][k]) for k in 'xyz']
        origin = [p+min(0, n+1) for p, n in zip(position, signed)]
        states = [state_string(s) for s in r['BlockStatePalette']]
        if len(set(states)) != len(states):
            raise ValueError('Duplicate state in litematic palette')
        ids = packed_litematic(r['BlockStates'], count, len(states))
        data_version = int(root['MinecraftDataVersion'])
        source_palette = {s: i for i, s in enumerate(states)}
        fmt = 'litematic'
        block_entities = r.get('TileEntities', [])
        entities = r.get('Entities', [])
        details = {'region': region, 'region_position_xyz': position, 'signed_size_xyz': signed}
        metadata = root.get('Metadata', {})
    elif is_java_structure:
        if region is not None:
            raise ValueError('--region applies only to Litematica')
        if 'palette' in root and 'palettes' in root:
            raise ValueError('Ambiguous structure contains both palette and palettes')
        size = root['size']
        if not isinstance(size, list) or any(type(n) is not int for n in size):
            raise ValueError('Structure size must contain integer XYZ')
        count = volume(size, max_voxels)
        options = root['palettes'] if 'palettes' in root else [root['palette']]
        if not isinstance(options, list) or not options:
            raise ValueError('Empty structure palette options')
        if palette_index is None:
            if len(options) != 1:
                raise ValueError(f'Select --palette-index 0..{len(options)-1}; multiple structure variants')
            palette_index = 0
        if type(palette_index) is not int or not 0 <= palette_index < len(options):
            raise ValueError('Invalid structure palette index')
        states = [state_string(s) for s in options[palette_index]]
        original_palette_count = len(states)
        if not states or len(set(states)) != len(states):
            raise ValueError('Empty or duplicate state in structure palette')
        records = root['blocks']
        if not isinstance(records, list) or len(records) > count:
            raise ValueError('Excess structure block records')
        # Omitted positions are represented as air for geometry analysis. The
        # source does not necessarily intend those positions to clear a world.
        source_palette = {s: i for i, s in enumerate(states)}
        air_id = next((i for i, s in enumerate(states) if s in AIR), None)
        if air_id is None:
            air_id = len(states)
            source_palette['minecraft:air'] = air_id
        ids = np.full(count, air_id, dtype=np.uint32)
        defined = np.zeros(count, dtype=bool)
        block_entities = []
        for item in records:
            pos, state = item['pos'], item['state']
            if not isinstance(pos, list) or len(pos) != 3 or any(type(n) is not int for n in pos):
                raise ValueError('Structure block pos must contain integer XYZ')
            if any(n < 0 or n >= bound for n, bound in zip(pos, size)):
                raise ValueError('Structure block outside declared size')
            if type(state) is not int or not 0 <= state < len(states):
                raise ValueError('Structure block state absent from selected palette')
            x, y, z = pos
            index = (y * size[2] + z) * size[0] + x
            if defined[index]:
                raise ValueError('Duplicate structure block position')
            defined[index] = True
            ids[index] = state
            if 'nbt' in item:
                block_entities.append(item['nbt'])
        origin = [0, 0, 0]
        data_version = int(root['DataVersion']) if 'DataVersion' in root else None
        version, fmt = None, 'java_structure'
        entities = root.get('entities', [])
        metadata = {'Name': root.get('name'), 'Author': root.get('author')}
        details = {'palette_index': palette_index, 'palette_options': len(options),
                   'defined_positions': len(records), 'omitted_positions': count-len(records),
                   'omitted_position_semantics': 'Represented as air for analysis; this does not establish that the source would clear these cells when pasted.',
                   'extra_root_keys': sorted(set(root) - {'size', 'blocks', 'palette', 'palettes', 'entities', 'DataVersion', 'author', 'name'})}
    else:
        if region is not None:
            raise ValueError('--region applies only to Litematica')
        r = root.get('Schematic', root)
        version = int(r.get('Version', 0))
        if version not in (2, 3):
            raise ValueError('Only Sponge v2/v3 supported; legacy numeric-ID .schematic needs conversion')
        size = [int(r[k]) & 65535 for k in ('Width', 'Height', 'Length')]
        count = volume(size, max_voxels)
        block_container = r['Blocks'] if version == 3 else r
        source_palette = block_container['Palette']
        ids = varints(block_container['Data' if version == 3 else 'BlockData'], count)
        origin = [int(v) for v in r.get('Offset', [0, 0, 0])]
        if len(origin) != 3:
            raise ValueError('Offset must contain exactly XYZ')
        data_version = int(r['DataVersion'])
        fmt = 'sponge'
        block_entities = block_container.get('BlockEntities', [])
        entities = r.get('Entities', [])
        metadata = r.get('Metadata', {})
        details = {'offset_xyz': origin}
    ids, palette = normalize(ids, source_palette)
    blocks = ids.reshape((size[1], size[2], size[0]))
    namespace_counts = {}
    for index, amount in zip(*np.unique(ids, return_counts=True)):
        if int(index) == 0:
            continue
        namespace = str(palette[int(index)]).split(':', 1)[0]
        namespace_counts[namespace] = namespace_counts.get(namespace, 0) + int(amount)
    report = {
        'schema': 'architecture-import/1', 'input': str(Path(path).resolve()),
        'sha256': hashlib.sha256(Path(path).read_bytes()).hexdigest(),
        'format': fmt, 'format_version': version, 'data_version': data_version,
        'size_xyz': size, 'origin_xyz': origin, 'nonair_blocks': int(np.count_nonzero(blocks)),
        'block_state_count': len(palette), 'source_palette_count': len(source_palette) if original_palette_count is None else original_palette_count,
        'declared_name': metadata.get('Name'), 'declared_author': metadata.get('Author'),
        'omitted_block_entities': len(block_entities), 'omitted_entities': len(entities),
        'coordinates': 'Paste-relative minimum; not source-world XYZ. Array order y,z,x.',
        'normalization': 'Air/cave_air/void_air merged to air ID 0; block properties sorted.',
        'details': details,
        'nonair_namespace_counts': namespace_counts,
    }
    return blocks, palette, np.array(origin, dtype=np.int64), report


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('source', type=Path)
    ap.add_argument('--out', type=Path)
    ap.add_argument('--region')
    ap.add_argument('--palette-index', type=int)
    ap.add_argument('--list-regions', action='store_true')
    ap.add_argument('--max-voxels', type=int, default=8_000_000)
    args = ap.parse_args()
    if args.list_regions:
        r = read_nbt(args.source)
        print(json.dumps({n: {'position': v['Position'], 'size': v['Size']}
                          for n, v in r.get('Regions', {}).items()}, ensure_ascii=False))
        return
    if args.out is None:
        ap.error('--out is required')
    if args.out.suffix.lower() != '.npz' or args.out.resolve() == args.source.resolve():
        ap.error('--out must be a separate .npz analysis file')
    a, p, o, report = load_structure(args.source, args.region, args.max_voxels, args.palette_index)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(args.out, blocks=a, palette=p, origin=o)
    args.out.with_suffix('.import.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
