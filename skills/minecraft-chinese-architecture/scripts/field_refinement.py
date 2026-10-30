"""Read-only edit audits, full-resolution site metrics and hanging-light plans.

Geometry models have no block-entity payload or live light engine. Keep the
air-inclusive NBT backup and actual BlockLight verification in the world adapter.
All boxes are inclusive; floor_xyz points name the supporting block, not feet.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

import numpy as np
from scipy.ndimage import binary_dilation, generate_binary_structure

from voxel_model import Model, join_state, split_state, vector3

AIR = 'minecraft:air'
CROSS = generate_binary_structure(3, 1)


class RefinementError(ValueError):
    def __init__(self, report):
        self.report = report
        super().__init__(json.dumps(report, ensure_ascii=False))


def _canonical(state):
    base, props = split_state(state)
    return AIR if base in (AIR, 'minecraft:cave_air', 'minecraft:void_air') and not props else join_state(base, props)


def _bases(values, name):
    if not isinstance(values, (list, tuple, set)) or not values:
        raise ValueError(name + ' needs an explicit nonempty collection of block IDs')
    result = set()
    for value in values:
        base, props = split_state(value)
        if props:
            raise ValueError(name + ' uses base IDs without properties')
        result.add(base)
    return result


def _rows(rows, model, name):
    result = {}
    for row in rows:
        if not isinstance(row, (list, tuple)) or len(row) != 4:
            raise ValueError(name + ' rows must be [x,y,z,state]')
        q = tuple(int(v) for v in vector3(row[:3], name))
        model._position(q)
        if q in result:
            raise ValueError(name + ' has duplicate coordinates: ' + str(q))
        result[q] = _canonical(row[3])
    return result


def audit_edit(before, after, changes, prior, *, protected=(), material_pairs=None):
    """Reject any undeclared edit, stale prior state or change to a protected box.

    material_pairs optionally restricts this edit to explicit old/new base IDs
    with IDENTICAL state properties. The caller must verify collision equivalence
    from the registry; this function does not infer it from names.
    """
    if not np.array_equal(before.origin, after.origin) or before.blocks.shape != after.blocks.shape:
        raise ValueError('Before/after must cover the same full volume, including air')
    wanted = _rows(changes, before, 'changes')
    previous = _rows(prior, before, 'prior')
    if wanted.keys() != previous.keys():
        raise ValueError('Every change needs one prior state at the same coordinate')
    if material_pairs is not None:
        if not isinstance(material_pairs, dict) or not material_pairs:
            raise ValueError('material_pairs must be a nonempty base-ID mapping')
        _bases(list(material_pairs), 'material_pairs sources')
        _bases(list(material_pairs.values()), 'material_pairs destinations')
    mapped = np.array([before.index.get(s, -1) for s in after.palette], np.int32)
    actual = before.blocks != mapped[after.blocks]
    declared = np.zeros(actual.shape, bool)
    protected_mask = np.zeros(actual.shape, bool)
    errors = Counter()
    samples = []

    def issue(kind, q, **details):
        errors[kind] += 1
        if len(samples) < 20:
            samples.append({'kind': kind, 'xyz': list(q), **details})

    for q, state in wanted.items():
        x, y, z = (np.array(q) - before.origin).tolist()
        declared[y, z, x] = True
        if previous[q] == state:
            issue('declared_noop', q)
        old, new = before.get(*q), after.get(*q)
        if old != previous[q]:
            issue('prior_state_mismatch', q, expected=previous[q], actual=old)
        if new != state:
            issue('missing_or_wrong_change', q, expected=state, actual=new)
        if material_pairs is not None:
            ob, op = split_state(old)
            nb, np_ = split_state(new)
            if material_pairs.get(ob) != nb or op != np_:
                issue('material_or_property_change', q, before=old, after=new)
    for item in protected:
        if not isinstance(item, dict) or set(item) != {'id', 'bounds'} or len(item['bounds']) != 2:
            raise ValueError('Protected regions need id and bounds: [[min XYZ],[max XYZ]]')
        lo = before._position(item['bounds'][0])
        hi = before._position(item['bounds'][1])
        if np.any(hi < lo):
            raise ValueError('Protected maximum precedes minimum')
        ax, ay, az = lo
        bx, by, bz = hi
        protected_mask[ay:by+1, az:bz+1, ax:bx+1] = True
    for kind, mask in (('undeclared_change', actual & ~declared),
                       ('protected_change', actual & protected_mask)):
        count = int(mask.sum())
        errors[kind] += count
        if count and len(samples) < 20:
            for y, z, x in np.argwhere(mask)[:20-len(samples)]:
                samples.append({'kind': kind, 'xyz': (before.origin + [x, y, z]).tolist()})
    report = {'passed': not any(errors.values()), 'compared_cells': int(actual.size),
              'changed_cells': int(actual.sum()), 'declared_cells': len(wanted),
              'protected_cells': int(protected_mask.sum()),
              'occupied_and_air_mask_unchanged': bool(np.array_equal(before.blocks != 0, after.blocks != 0)),
              'errors': {k: v for k, v in errors.items() if v}, 'samples': samples}
    if not report['passed']:
        raise RefinementError(report)
    return report


def projection_metrics(model, bounds_xz, floor_y, roof_bases, paving_bases):
    """Measure one declared full-resolution terrace; no universal density target.

    Open paving has paving at floor_y and ONLY air above it in the model. Roof
    projection counts only declared roof IDs above floor_y. A tree or ornament
    closes open paving but cannot inflate the roof projection.
    """
    if len(bounds_xz) != 4 or any(type(v) is not int for v in bounds_xz) or type(floor_y) is not int:
        raise ValueError('Use integer [x0,z0,x1,z1] and floor_y')
    x0, z0, x1, z1 = bounds_xz
    ax, ay, az = model._position([x0, floor_y, z0])
    bx, _, bz = model._position([x1, floor_y, z1])
    if bx < ax or bz < az or floor_y >= model.maximum[1]:
        raise ValueError('Invalid site bounds or no above-floor volume')
    roof = _bases(roof_bases, 'roof_bases')
    paving = _bases(paving_bases, 'paving_bases')
    bases = [split_state(s)[0] for s in model.palette]
    roof_ids = np.array([s in roof for s in bases], bool)
    paving_ids = np.array([s in paving for s in bases], bool)
    above = model.blocks[ay+1:, az:bz+1, ax:bx+1]
    ground = paving_ids[model.blocks[ay, az:bz+1, ax:bx+1]]
    roofs = roof_ids[above].any(axis=0)
    exposed = ground & ~(above != 0).any(axis=0)
    n = int(ground.size)
    return {'bounds_xz': bounds_xz, 'floor_y': floor_y, 'area_cells': n,
            'roof_projection_cells': int(roofs.sum()), 'at_floor_paving_cells': int(ground.sum()),
            'open_paving_cells': int(exposed.sum()),
            'covered_or_occupied_paving_cells': int((ground & ~exposed).sum()),
            'roof_fraction': float(roofs.sum()/n), 'open_paving_fraction': float(exposed.sum()/n),
            'resolution_blocks': 1, 'above_floor_scanned_through_y': int(model.maximum[1])}


def _coverage(passable, target_ids, source_yzx, radius):
    """Six-neighbour propagation through the actual voxel mask, not distance-only."""
    low = np.maximum(np.array(source_yzx)-radius, 0)
    high = np.minimum(np.array(source_yzx)+radius+1, passable.shape)
    slices = tuple(slice(int(a), int(b)) for a, b in zip(low, high))
    mask = passable[slices].copy()
    local = tuple(np.array(source_yzx)-low)
    mask[local] = True  # an opaque emitter can still emit into neighbouring air
    reach = np.zeros(mask.shape, bool)
    reach[local] = True
    if radius:
        reach = binary_dilation(reach, structure=CROSS, iterations=radius, mask=mask)
    ids = target_ids[slices][reach]
    return set(int(v) for v in ids[ids >= 0])


def plan_hanging_lights(model, floor_xyz, anchor_bases, lamp_state, chain_state, *,
                        emission=15, minimum=3, clear_height=3, shoulder=2,
                        max_chain=24, transparent_bases=()):
    """Plan supported fixtures atomically; never change model, even on failure.

    Every supplied floor must have a support block and two clear body cells.
    Other traversal/collision checks belong to the final assembly audit. Unknown
    blocks are opaque. Use the currently RUNNING lamp emission, not an installed
    but unloaded jar's value. The prediction is not Minecraft BlockLight.
    """
    for name, value, low, high in (('emission', emission, 1, 15), ('minimum', minimum, 1, emission),
                                  ('clear_height', clear_height, 3, 16), ('shoulder', shoulder, 0, 8),
                                  ('max_chain', max_chain, 1, 64)):
        if type(value) is not int or not low <= value <= high:
            raise ValueError(f'{name} must be an integer in {low}..{high}')
    anchors = _bases(anchor_bases, 'anchor_bases')
    lamp, chain = _canonical(lamp_state), _canonical(chain_state)
    lamp_base, chain_base = split_state(lamp)[0], split_state(chain)[0]
    if AIR in (lamp, chain) or lamp_base == chain_base or anchors & {AIR, lamp_base, chain_base}:
        raise ValueError('Lamp, chain and solid anchors must be distinct nonair roles')
    transparent = {AIR} | (_bases(transparent_bases, 'transparent_bases') if transparent_bases else set())
    if anchors & transparent:
        raise ValueError('Anchor bases must be explicitly solid, not transparent')
    origin = model.origin.tolist()
    maximum = model.maximum.tolist()
    points = sorted({tuple(int(v) for v in vector3(q, 'floor_xyz')) for q in floor_xyz})
    if not points:
        raise ValueError('No usable floor points supplied')
    bases = [split_state(s)[0] for s in model.palette]
    passable = np.array([s in transparent for s in bases], bool)[model.blocks]
    targets = np.full(model.blocks.shape, -1, np.int32)
    reserved = set()

    def index(q):
        x, y, z = q
        if not all(a <= v <= b for a, v, b in zip(origin, q, maximum)):
            return None
        return y-origin[1], z-origin[2], x-origin[0]

    def state(q):
        p = index(q)
        return None if p is None else model.palette[int(model.blocks[p])]

    for i, (x, y, z) in enumerate(points):
        if state((x, y, z)) in (None, AIR) or any(state((x, y+dy, z)) != AIR for dy in (1, 2)):
            raise ValueError('Invalid or obstructed floor point: ' + str((x, y, z)))
        targets[index((x, y+1, z))] = i
        reserved.update((x, y+dy, z) for dy in range(1, clear_height+1))
    existing = [tuple(int(v) for v in q) for q in np.argwhere(np.array([s == lamp_base for s in bases])[model.blocks])]
    covered = set()
    for p in existing:
        covered |= _coverage(passable, targets, p, emission-minimum)
    missing = set(range(len(points))) - covered
    candidate_columns = sorted({(x+dx, y, z+dz) for x, y, z in points
                                for dx in range(-shoulder, shoulder+1)
                                for dz in range(-shoulder, shoulder+1)}) if missing else []
    candidates = []
    for x, floor_y, z in candidate_columns:
        lamp_y = floor_y+clear_height+1
        if state((x, lamp_y, z)) != AIR:
            continue
        anchor_y = None
        for y in range(lamp_y+1, min(lamp_y+max_chain+1, maximum[1])+1):
            current = state((x, y, z))
            if current != AIR:
                if current is not None and split_state(current)[0] in anchors:
                    anchor_y = y
                break
        if anchor_y is None:
            continue
        cells = tuple((x, y, z) for y in range(lamp_y, anchor_y))
        if reserved.intersection(cells):
            continue
        cover = _coverage(passable, targets, index((x, lamp_y, z)), emission-minimum) & missing
        if cover:
            candidates.append({'lamp': (x, lamp_y, z), 'anchor': (x, anchor_y, z),
                               'cells': cells, 'cover': cover})
    selected, used = [], set()
    while missing:
        best = max(candidates, key=lambda c: (len(c['cover'] & missing), -len(c['cells'])), default=None)
        if best is None or not best['cover'] & missing:
            raise RefinementError({'passed': False, 'reason': 'No supported fixture covers remaining floor',
                                   'uncovered_count': len(missing),
                                   'uncovered_floor_xyz': [list(points[i]) for i in sorted(missing)[:20]],
                                   'model_unchanged': True})
        selected.append(best)
        missing -= best['cover']
        used.update(best['cells'])
        candidates = [c for c in candidates if not used.intersection(c['cells'])]
    changes = []
    for item in selected:
        for q in item['cells']:
            changes.append([*q, lamp if q == item['lamp'] else chain])
    # Check the final plan too: opaque fixtures may obstruct another light path.
    final_passable = passable.copy()
    for x, y, z, s in changes:
        final_passable[index((x, y, z))] = split_state(s)[0] in transparent
    final_covered = set()
    for p in existing + [index(c['lamp']) for c in selected]:
        final_covered |= _coverage(final_passable, targets, p, emission-minimum)
    if len(final_covered) != len(points):
        raise RefinementError({'passed': False, 'reason': 'Final fixtures obstruct predicted coverage',
                               'uncovered_count': len(points)-len(final_covered), 'model_unchanged': True})
    return {'passed': True, 'model_unchanged': True, 'floor_points': len(points),
            'existing_lamps': len(existing), 'new_lamps': len(selected),
            'emission_used': emission, 'all_targets_predicted_at_least': minimum,
            'fixtures': [{'lamp': list(c['lamp']), 'anchor': list(c['anchor']),
                          'chain_blocks': len(c['cells'])-1} for c in selected],
            'changes': changes, 'prior': [[*r[:3], AIR] for r in changes],
            'verification': 'Conservative voxel propagation only; actual saved/live BlockLight still required.'}


def audit_blocklight(values, minimum=3):
    """Missing/invalid light data cannot silently count as a successful audit."""
    if type(minimum) is not int or not 0 <= minimum <= 15 or not len(values):
        raise ValueError('Need samples and a threshold in 0..15')
    unknown = [i for i, v in enumerate(values) if type(v) is not int or not 0 <= v <= 15]
    dark = [i for i, v in enumerate(values) if type(v) is int and 0 <= v < minimum]
    report = {'passed': not unknown and not dark, 'samples': len(values),
              'unknown_count': len(unknown), 'below_minimum_count': len(dark),
              'unknown_indices': unknown[:20], 'dark_indices': dark[:20],
              'minimum_required': minimum,
              'minimum_observed': min(values) if not unknown else None,
              'histogram': dict(Counter(v for v in values if type(v) is int and 0 <= v <= 15))}
    if not report['passed']:
        raise RefinementError(report)
    return report


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    commands = ap.add_subparsers(dest='command', required=True)
    audit = commands.add_parser('audit')
    audit.add_argument('--before', required=True, type=Path)
    audit.add_argument('--after', required=True, type=Path)
    for name in ('measure', 'lights'):
        commands.add_parser(name).add_argument('--model', required=True, type=Path)
    for parser in commands.choices.values():
        parser.add_argument('--config', required=True, type=Path)
        parser.add_argument('--out', required=True, type=Path)
    a = ap.parse_args()
    inputs = [a.config] + ([a.before, a.after] if a.command == 'audit' else [a.model])
    if a.out.resolve() in {p.resolve() for p in inputs}:
        raise ValueError('Output must not overwrite an input')
    config = json.loads(a.config.read_text('utf-8-sig'))
    a.out.parent.mkdir(parents=True, exist_ok=True)
    try:
        if a.command == 'audit':
            result = audit_edit(Model.load(a.before), Model.load(a.after), **config)
        elif a.command == 'measure':
            result = projection_metrics(Model.load(a.model), **config)
        else:
            result = plan_hanging_lights(Model.load(a.model), **config)
    except RefinementError as exc:
        a.out.write_text(json.dumps(exc.report, ensure_ascii=False, indent=2), 'utf8')
        raise SystemExit(2) from exc
    a.out.write_text(json.dumps(result, ensure_ascii=False, indent=2), 'utf8')
    print(json.dumps({k: v for k, v in result.items() if k not in ('changes', 'prior', 'fixtures')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
