"""Explicit XYZ placement of generated or imported geometry in a NEW local model.

Unlike heightfield sites, scenes can place roofs, details and interior pieces at
arbitrary heights. Differing occupied overlaps fail unless a component states
replace/keep explicitly. No existing world is edited by this script.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_courtyard import transform, walk_paths
from scene_output import export_scene
from model_provenance import source_record, guard_input_outputs
from voxel_model import Model, vector3


def make_scene(config):
    if not isinstance(config, dict) or set(config) - {'schema', 'components', 'boxes', 'routes', 'require_connected', 'evidence', 'tier_junctions'}:
        raise ValueError('Unknown scene keys')
    if config.get('schema') != 'chinese-scene/1': raise ValueError('Unknown scene schema')
    entries = config.get('components', []); boxes = config.get('boxes', [])
    if not isinstance(entries, list) or not 1 <= len(entries) <= 32 or not isinstance(boxes, list) or len(boxes) > 128:
        raise ValueError('Scene needs 1..32 components and at most 128 explicit boxes')
    required = config.get('require_connected', True)
    if type(required) is not bool: raise ValueError('require_connected must be boolean')
    parts = []; bounds = []; anchors = {}; names = set(); evidence = []
    from architecture import build_component
    for e in entries:
        if not isinstance(e, dict) or set(e) - {'id', 'config', 'model_file', 'at', 'turns', 'conflict', 'include_air'}:
            raise ValueError('Unknown scene component keys')
        ident = e.get('id')
        if not isinstance(ident, str) or not ident or '.' in ident or ident in names: raise ValueError('Unique simple component id required')
        names.add(ident)
        if ('config' in e) == ('model_file' in e): raise ValueError('Exactly one of config and model_file is required')
        if 'config' in e:
            # Nested scenes would make aggregate complexity/side effects hard to
            # audit. Export one first and compose its explicit NPZ if needed.
            if e['config'].get('schema') == 'chinese-scene/1': raise ValueError('Export nested scenes to NPZ before composing them')
            part, d = build_component(e['config'])
            evidence.append({'id': ident, 'type': 'authored_generator', 'schema': e['config'].get('schema')})
        else:
            path = Path(e['model_file']); part = Model.load(path); d = {}
            evidence.append({'id': ident, 'type': 'input_model', **source_record(path)})
        offset = vector3(e.get('at', [0, 0, 0]), 'component at').tolist(); turns = e.get('turns', 0)
        if type(turns) is not int or not 0 <= turns <= 3: raise ValueError('turns must be 0..3')
        conflict = e.get('conflict', 'error'); air = e.get('include_air', False)
        if conflict not in ('error', 'keep', 'replace') or type(air) is not bool: raise ValueError('Explicit valid overlap mode and include_air required')
        rotated = part.rotated(turns); bounds.append((rotated.origin + offset, rotated.maximum + offset))
        if 'start_floor_xyz' in d: anchors[ident + '.entry'] = transform(d['start_floor_xyz'], offset, turns)
        for key, pt in d.get('target_floors_xyz', {}).items(): anchors[ident + '.' + key] = transform(pt, offset, turns)
        parts.append((ident, part, offset, turns, conflict, air))
    for b in boxes:
        if not isinstance(b, dict) or set(b) != {'min', 'max', 'block'}: raise ValueError('Box requires min/max/block')
        lo, hi = vector3(b['min'], 'box min'), vector3(b['max'], 'box max')
        if np.any(lo > hi): raise ValueError('Reversed box')
        bounds.append((lo, hi))
    minimum = np.min([lo for lo, hi in bounds], axis=0); maximum = np.max([hi for lo, hi in bounds], axis=0)
    # Keep head space for routes at the top level.
    maximum[1] += 3; m = Model(minimum, maximum)
    for b in boxes: m.box(b['min'], b['max'], b['block'])
    receipts = {}
    for ident, part, offset, turns, conflict, air in parts:
        receipts[ident] = m.paste(part, offset=offset, turns=turns, conflict=conflict, include_air=air)
        receipts[ident]['requested_conflict_mode'] = conflict
    junctions=config.get('tier_junctions',[])
    if not isinstance(junctions,list) or len(junctions)>32:raise ValueError('tier_junctions must contain at most 32 explicit interface plans')
    from tier_junctions import assemble_tier_junction, audit_tier_junction
    joint_reports=[assemble_tier_junction(m,joint) for joint in junctions]
    # Later overlapping interfaces must not seal an earlier stairwell.
    for joint,report in zip(junctions,joint_reports):report['audit']=audit_tier_junction(m,joint)
    checks = {'nonempty_model': bool(np.any(m.blocks))}; connectivity = int(label(m.blocks != 0)[1])
    if required: checks['face_connected_geometry'] = connectivity == 1
    if junctions:checks['declared_tier_junctions_complete']=all(r['audit']['passed'] for r in joint_reports)
    if any(j.get('kind')=='storey' for j in junctions) and config.get('routes') is None:
        raise ValueError('Occupied storey junctions require explicit scene routes to their floors')
    d = {'schema': 'chinese-scene-design/1', 'config': config, 'anchors_floor_xyz': anchors,
         'placements': receipts, 'inputs': evidence, 'geometry_components': connectivity, 'checks': checks,
         'source_structure_reuse': any(v['type'] == 'input_model' for v in evidence), 'historical_reconstruction': False}
    d['tier_junctions']=joint_reports
    route = config.get('routes')
    if route is not None:
        if not isinstance(route, dict) or set(route) != {'start', 'targets'} or not isinstance(route['targets'], dict): raise ValueError('routes needs start and targets')
        def point(value):
            if isinstance(value, str):
                if value not in anchors: raise ValueError('Unknown entrance/target anchor: ' + value)
                return anchors[value]
            return vector3(value, 'route floor').tolist()
        d['start_floor_xyz'] = point(route['start']); d['target_floors_xyz'] = {k: point(v) for k, v in route['targets'].items()}
        d['routes'] = walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz'])
        checks['requested_routes_reachable'] = d['routes']['all_targets_reached']
    else: d['route_validation'] = 'not_requested'
    if not all(checks.values()): raise AssertionError(checks)
    return m, d


def export(config, out_dir, name='scene', render=True):
    guard_input_outputs(config, out_dir, name)
    m, d = make_scene(config)
    report = export_scene(m, d, out_dir, name, 'chinese-scene', render)
    if d['source_structure_reuse']:
        # Override the generic authored-scene label when explicit source models
        # are present. A local composition does not grant publishing rights.
        report['limits'][2] = 'Contains explicitly supplied source models. Preserve source attribution and verify redistribution rights separately.'
        (Path(out_dir) / f'{name}.report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    return report
