"""Validate used block IDs/properties against versioned mcmeta game-data reports.

Registry membership is separate from live import, collision and block updates.
An optional retarget exports compatible geometry with the target DataVersion.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

import numpy as np

from generate_hall import write_schematic
from import_structure import load_structure
from voxel_model import Model, split_state
from model_provenance import source_record


VERSIONS = {'1.20.1': 3465, '1.21.4': 4189}


def complete_registry(properties, block_ids):
    """Older summaries omit propertyless blocks; IDs come from the full registry."""
    if not isinstance(properties, dict) or not isinstance(block_ids, list) or 'air' not in block_ids:
        raise ValueError('Need a property table and complete block registry containing air')
    if any(not isinstance(key, str) for key in block_ids) or len(set(block_ids)) != len(block_ids):
        raise ValueError('Invalid or repeated registry IDs')
    if set(properties) - set(block_ids): raise ValueError('Property table contains IDs absent from block registry')
    for key, spec in properties.items():
        if not isinstance(spec, list) or len(spec) != 2 or not all(isinstance(v, dict) for v in spec):
            raise ValueError('Unexpected property specification for ' + key)
        available, defaults = spec
        if set(available) != set(defaults) or any(not isinstance(v, list) or not v or defaults[k] not in v for k, v in available.items()):
            raise ValueError('Incomplete property defaults for ' + key)
    return {key: properties.get(key, [{}, {}]) for key in sorted(block_ids)}


def fetch_registry(version, out):
    if version not in VERSIONS: raise ValueError('Supported named registries: ' + ', '.join(VERSIONS))
    out = Path(out)
    base = 'https://raw.githubusercontent.com/misode/mcmeta/' + version + '-summary/'
    sources = []; documents = []
    for relative in ('blocks/data.json', 'registries/data.json'):
        url = base + relative
        with urllib.request.urlopen(url, timeout=30) as stream: raw = stream.read(8_000_001)
        if len(raw) > 8_000_000: raise ValueError('Registry exceeds byte budget')
        documents.append(json.loads(raw)); sources.append({'url': url, 'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)})
    if not isinstance(documents[1], dict): raise ValueError('Unexpected registry ID document')
    document = complete_registry(documents[0], documents[1].get('block'))
    normalized = json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2).encode('utf8')
    out.parent.mkdir(parents=True, exist_ok=True); out.write_bytes(normalized)
    receipt = {'sources': sources, 'version': version, 'data_version': VERSIONS[version], 'sha256': hashlib.sha256(normalized).hexdigest(),
               'block_ids': len(document), 'stateful_table_entries': len(documents[0]),
               'normalization': 'Complete block ID registry joined with properties; omitted propertyless entries use empty properties/defaults.',
               'provenance': 'misode/mcmeta version-tagged summary of Minecraft game data, not a live game test'}
    out.with_suffix('.source.json').write_text(json.dumps(receipt, indent=2), encoding='utf8')
    return receipt


def validate(model, registry):
    registry = Path(registry); raw = registry.read_bytes(); document = json.loads(raw)
    if not isinstance(document, dict) or 'air' not in document:
        raise ValueError('Registry is incomplete (missing air). Fetch the complete ID and property registry with this script.')
    receipt_path = registry.with_suffix('.source.json'); receipt = json.loads(receipt_path.read_text('utf8')) if receipt_path.exists() else None
    digest = hashlib.sha256(raw).hexdigest()
    if receipt and receipt.get('sha256') != digest: raise ValueError('Registry source receipt hash does not match data')
    problems = []; used = []
    for index, count in zip(*np.unique(model.blocks, return_counts=True)):
        state = model.palette[int(index)]; base, props = split_state(state); key = base.removeprefix('minecraft:')
        if not base.startswith('minecraft:') or key not in document:
            problems.append({'state': state, 'count': int(count), 'error': 'unknown block ID'}); continue
        spec = document[key]
        if not isinstance(spec, list) or len(spec) != 2 or not all(isinstance(v, dict) for v in spec): raise ValueError('Unsupported registry entry format: ' + key)
        available, defaults = spec
        bad = {k: v for k, v in props.items() if k not in available or v not in available[k]}
        if bad: problems.append({'state': state, 'count': int(count), 'error': 'invalid properties', 'properties': bad})
        else: used.append({'state': state, 'count': int(count), 'effective_properties': {**defaults, **props}})
    return {'schema': 'architecture-registry-validation/1', 'valid': not problems,
            'used_states_checked': len(used) + len(problems), 'problems': problems,
            'registry_sha256': digest, 'registry_source': receipt, 'states': used,
            'limits': 'Used block IDs and property values only. No client/server loading, neighbor updates, fluid, crop, light, entity or player-collision validation.'}


def retarget(model_file, registry, output):
    output = Path(output); source = Path(model_file)
    if output.suffix != '.schem' or output.resolve() == source.resolve(): raise ValueError('Use a separate .schem output')
    m = Model.load(source); report = validate(m, registry); receipt = report['registry_source']
    if not report['valid']: raise ValueError('Target registry rejects states: ' + json.dumps(report['problems'][:5], ensure_ascii=False))
    if not receipt or receipt.get('version') not in VERSIONS: raise ValueError('Retarget requires a matching source receipt from fetch_registry')
    version = receipt['version']; data_version = VERSIONS[version]
    output.parent.mkdir(parents=True, exist_ok=True)
    write_schematic(m, output, output.stem, generator='minecraft-chinese-architecture/registry-checked-export',
                    data_version=data_version, author='Source model preserved; see accompanying report')
    a, states, _, parsed = load_structure(output)
    if not np.array_equal(np.asarray(states)[a], np.asarray(m.palette)[m.blocks]): raise AssertionError('Retargeted states differ')
    report.update(source_model=str(source.resolve()), source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  source_provenance=source_record(source),
                  output=str(output.resolve()), output_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),
                  target_version=version, output_data_version=parsed['data_version'], state_roundtrip=True)
    output.with_suffix('.registry.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    return report


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); subs = ap.add_subparsers(dest='command', required=True)
    fetch = subs.add_parser('fetch'); fetch.add_argument('--version', required=True, choices=tuple(VERSIONS)); fetch.add_argument('--out', required=True, type=Path)
    check = subs.add_parser('check'); check.add_argument('model', type=Path); check.add_argument('--registry', required=True, type=Path); check.add_argument('--out', type=Path)
    export = subs.add_parser('retarget'); export.add_argument('model', type=Path); export.add_argument('--registry', required=True, type=Path); export.add_argument('--out', required=True, type=Path)
    a = ap.parse_args()
    if a.command == 'fetch': r = fetch_registry(a.version, a.out)
    elif a.command == 'retarget': r = retarget(a.model, a.registry, a.out)
    else:
        r = validate(Model.load(a.model), a.registry)
        if a.out: a.out.parent.mkdir(parents=True, exist_ok=True); a.out.write_text(json.dumps(r, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(r, ensure_ascii=False))
    if not r.get('valid', True): raise SystemExit(1)
