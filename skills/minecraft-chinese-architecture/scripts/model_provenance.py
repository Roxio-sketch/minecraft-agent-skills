"""Carry declared source attribution between local analysis models.

NPZ stores geometry only. Adjacent receipts remain untrusted declarations, not
proof of authorship or permission; their hashes make the chain inspectable.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path


def source_record(path):
    path = Path(path).resolve()
    result = {'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'receipts': []}
    for suffix in ('.import.json', '.report.json', '.source.json'):
        sidecar = path.with_suffix(suffix)
        if not sidecar.is_file():
            continue
        raw = sidecar.read_bytes()
        record = {'path': str(sidecar), 'sha256': hashlib.sha256(raw).hexdigest()}
        try:
            document = json.loads(raw)
            if not isinstance(document, dict):
                raise ValueError('Receipt is not an object')
            keys = ('schema', 'source', 'source_sha256', 'input', 'sha256', 'format', 'data_version', 'declared_name', 'declared_author',
                    'author', 'authors', 'attribution', 'license', 'url', 'provenance',
                    'mapping_metadata', 'source_region_reports')
            declarations = {key: document[key] for key in keys if key in document}
            design = document.get('design', {})
            if isinstance(design, dict):
                for key in ('inputs', 'source_model', 'source_structure_reuse'):
                    if key in design:
                        declarations[key] = design[key]
            record['declarations'] = declarations
        except (ValueError, UnicodeError) as exc:
            record['unparsed_receipt'] = str(exc)
        result['receipts'].append(record)
    result['attribution_status'] = ('Preserved declarations; verify authorship and redistribution rights separately.'
                                    if result['receipts'] else 'No adjacent provenance receipt; authorship and rights are unknown.')
    return result


def guard_input_outputs(config, out_dir, name):
    """Reject an export that would replace an explicitly supplied data file."""
    output = Path(out_dir).resolve()
    destinations = {output / (name + suffix) for suffix in
                    ('.npz', '.schem', '.report.json', '.config.json', '.complex.json', '.heightmap.npz')}
    def visit(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key in ('model_file', 'heightmap') and isinstance(item, (str, Path)):
                    source = Path(item).resolve()
                    if source in destinations or any(target.exists() and source.samefile(target) for target in destinations if source.exists()):
                        raise ValueError('Export would replace an input model/heightmap; choose a new name or directory: ' + str(source))
                else:
                    visit(item)
        elif isinstance(value, list):
            for item in value:
                visit(item)
    visit(config)
