"""Original hall/pavilion assembly with nonuniform bays and six roof morphologies."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_hall import DEFAULT as HALL_DEFAULT, roof_family, write_schematic
from facade_kernel import PATTERNS, panel_roles
from roof_kernel import FORMS, make_roof
from voxel_model import Model, join_state, split_state


DEFAULT = {
    "schema": "chinese-building/1", "kind": "hall",
    "bays_x": [6, 8, 6], "bays_z": [6, 6], "column_height": 8,
    "platform_height": 2, "platform_margin": 3, "eave_overhang": 3,
    "door_bay": None, "stair_width": 5, "facade_pattern": "cross", "facade_overrides": [],
    "roof": {"form": "hip_and_gable", "rise": 6, "curve": 1.5, "uplift": 1,
             "ridge_axis": "x", "gable_inset": 3},
    "palette": HALL_DEFAULT["palette"], "roof_mix": [], "seed": 0,
    "evidence": [], "rule_status": "authored_hypothesis",
}


def config_for(user):
    if not isinstance(user, dict):
        raise ValueError("Config must be an object")
    unknown = set(user) - set(DEFAULT)
    if unknown:
        raise ValueError(f"Unknown configuration keys: {sorted(unknown)}")
    c = copy.deepcopy(DEFAULT)
    c.update(user)
    for key in ("roof", "palette"):
        if not isinstance(user.get(key, {}), dict):
            raise ValueError(f"{key} must be an object")
        if set(user.get(key, {})) - set(DEFAULT[key]):
            raise ValueError(f"Unknown {key} keys")
        c[key] = {**DEFAULT[key], **user.get(key, {})}
    if c["schema"] != DEFAULT["schema"] or c["kind"] not in ("hall", "pavilion"):
        raise ValueError("Supported: chinese-building/1 hall or pavilion")
    for axis in ("x", "z"):
        bays = c[f"bays_{axis}"]
        if not isinstance(bays, list) or not 1 <= len(bays) <= 11 or any(type(n) is not int or not 3 <= n <= 16 for n in bays):
            raise ValueError(f"bays_{axis} needs 1..11 integer spacings of 3..16")
        if sum(bays) % 2:
            raise ValueError(f"bays_{axis} total must be even for the current block-centered roof kernel")
    if c['facade_pattern'] not in PATTERNS:raise ValueError('Unknown facade_pattern')
    for key, low, high in (("column_height", 4, 24), ("platform_height", 1, 8),
                          ("platform_margin", 2, 8), ("eave_overhang", 1, 8), ("stair_width", 3, 15)):
        if type(c[key]) is not int or not low <= c[key] <= high:
            raise ValueError(f"{key} must be integer {low}..{high}")
    if c["stair_width"] % 2 != 1:
        raise ValueError("stair_width must be odd")
    if c["door_bay"] is None:
        c["door_bay"] = (len(c["bays_x"]) - 1) // 2
    if type(c["door_bay"]) is not int or not 0 <= c["door_bay"] < len(c["bays_x"]):
        raise ValueError("door_bay must index bays_x")
    if not isinstance(c['facade_overrides'],list):raise ValueError('facade_overrides must be a list')
    selected=set()
    for panel in c['facade_overrides']:
        if not isinstance(panel,dict) or set(panel)!={'face','bay','pattern'}:raise ValueError('A facade override needs face/bay/pattern')
        face,bay=panel['face'],panel['bay']
        if face not in ('front','back','left','right'):raise ValueError('Unknown facade face')
        count=len(c['bays_x'] if face in ('front','back') else c['bays_z'])
        if type(bay) is not int or not 0<=bay<count:raise ValueError('Facade bay out of range')
        if panel['pattern'] not in PATTERNS:raise ValueError('Unknown facade override pattern')
        if (face,bay) in selected:raise ValueError('Duplicate facade override')
        if face=='front' and bay==c['door_bay']:raise ValueError('Facade override would replace the declared entrance')
        if c['kind']!='hall':raise ValueError('Open pavilions do not have facade panels')
        selected.add((face,bay))
    for name, state in c["palette"].items():
        base, props = split_state(state)
        if base in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
            raise ValueError(f"palette.{name} cannot be air")
        if name in ("roof_stairs", "access_stairs", "roof_slab"):
            if props or not base.endswith("_slab" if name == "roof_slab" else "_stairs"):
                raise ValueError(f"Invalid palette.{name}")
    # Reuse tested mixture and seed validation, without imposing hall/1 bays.
    from generate_hall import merge_config, validate
    validate(merge_config({"roof_mix": c["roof_mix"], "seed": c["seed"]}))
    return c


def column_lines(bays):
    result = [-sum(bays) // 2]
    for width in bays:
        result.append(result[-1] + width)
    return result


def make_building(user):
    c = config_for(user)
    p, r = c["palette"], c["roof"]
    xs, zs = column_lines(c["bays_x"]), column_lines(c["bays_z"])
    hx, hz = xs[-1], zs[-1]
    rx, rz = hx + c["eave_overhang"], hz + c["eave_overhang"]
    # Hard gables terminate at the gable wall planes; front/back still overhang.
    if r["form"] == "flush_gable":
        if r["ridge_axis"] == "x": rx = hx
        else: rz = hz
    px, pz = hx + c["platform_margin"], hz + c["platform_margin"]
    ph, ch = c["platform_height"], c["column_height"]
    y0, y1 = ph + 2, ph + ch + 1
    cap_y, beam_y, eave_y = y1 + 1, y1 + 2, y1 + 4
    roof, roof_info = make_roof(half_x=rx, half_z=rz, base_y=eave_y,
                               family=lambda x, z: roof_family(c, x, z),
                               gable_block=p["wall"], trim=p["trim"], **r)
    db = c["door_bay"]
    door_left, door_right = xs[db] + 1, xs[db + 1] - 1
    door_center = (door_left + door_right) // 2
    stair_half = c["stair_width"] // 2
    if door_center - stair_half < -px or door_center + stair_half > px:
        raise ValueError("Entrance stair width exceeds platform")
    m = Model((-max(px, rx) - 1, 0, -max(rz, pz + ph) - 1),
              (max(px, rx) + 1, int(roof.maximum[1]), max(pz, rz) + 1))
    m.box((-px, 0, -pz), (px, ph, pz), p["foundation"])
    m.box((-px + 1, ph, -pz + 1), (px - 1, ph, pz - 1), p["floor"])
    for step in range(ph + 1):
        z = -pz - ph + step
        if step:
            m.box((door_center - stair_half, 0, z), (door_center + stair_half, step - 1, z), p["foundation"])
        # At ground level this is the approach landing, not a descending half-step.
        surface = p["foundation"] if step == 0 else p["access_stairs"] + "[facing=south,half=bottom,shape=straight,waterlogged=false]"
        m.box((door_center - stair_half, step, z), (door_center + stair_half, step, z), surface)

    panels=[]
    if c["kind"] == "hall":
        def facade(lines, constant, x_run):
            for bay in range(len(lines) - 1):
                left, right = lines[bay], lines[bay + 1]
                if x_run and constant == -hz and bay == db:continue
                face=('front' if constant<0 else 'back') if x_run else ('left' if constant<0 else 'right')
                pattern=next((v['pattern'] for v in c['facade_overrides'] if v['face']==face and v['bay']==bay),c['facade_pattern'])
                roles=panel_roles(right-left-1,ch+1,pattern)
                panels.append({'axis':'x' if x_run else 'z','fixed_coordinate':constant,'bay_index':bay,
                               'interior_range':[left+1,right-1],'y_range':[ph+1,y1],
                               'face':face,'pattern':pattern,
                               'infill_voxels':int(np.count_nonzero(roles==1)),
                               'frame_voxels':int(np.count_nonzero(roles==2))})
                for u in range(left + 1, right):
                    for y in range(ph + 1, y1 + 1):
                        role=roles[y-ph-1,u-left-1]
                        block=p['beam'] if role==2 else p['window'] if role==1 else p['wall']
                        m.set(u if x_run else constant, y, constant if x_run else u, block)
        for z in (-hz, hz): facade(xs, z, True)
        for x in (-hx, hx): facade(zs, x, False)

    columns = [(x, z) for x in xs for z in zs if c["kind"] == "hall" or abs(x) == hx or abs(z) == hz]
    for x, z in columns:
        m.set(x, ph + 1, z, p["foundation"])
        m.box((x, y0, z), (x, y1, z), p["column"])
        m.box((x - 1, cap_y, z), (x + 1, cap_y, z), p["beam"])
        m.box((x, cap_y, z - 1), (x, cap_y, z + 1), p["beam"])
    # Perimeter and grid beams are separate from roof skin. Interior columns
    # are omitted for the open pavilion; this is a game design, not engineering.
    for z in zs:
        m.box((-rx, beam_y, z), (rx, beam_y, z), p["beam"])
    for x in xs:
        m.box((x, beam_y, -rz), (x, beam_y, rz), p["beam"])
    for z in (-hz, hz): m.box((-hx, y1 + 1, z), (hx, y1 + 1, z), p["beam"])
    for x in (-hx, hx): m.box((x, y1 + 1, -hz), (x, y1 + 1, hz), p["beam"])

    m.paste(roof)
    surface = roof_info["surface_y_rows_zx"]
    for x, z in columns:
        top = surface[z + rz][x + rx] - 1
        # Fill only air below the roof, preserving its shell and gable materials.
        for y in range(beam_y + 1, top + 1):
            if m.get(x, y, z) == "minecraft:air": m.set(x, y, z, p["beam"])
    gable_forms = ("overhanging_gable", "flush_gable", "barrel_gable")
    if c["kind"] == "hall" and r["form"] in gable_forms:
        # Triangular/rounded end-wall infill extends up to the underside; a
        # flush gable aligns these walls with the ends of the roofing.
        if r["ridge_axis"] == "x":
            walls = [(x, z) for x in (-hx, hx) for z in range(-hz, hz + 1)]
        else:
            walls = [(x, z) for z in (-hz, hz) for x in range(-hx, hx + 1)]
        for x, z in walls:
            top = surface[z + rz][x + rx] - 1
            for y in range(beam_y + 1, top + 1):
                if m.get(x, y, z) == "minecraft:air": m.set(x, y, z, p["wall"])
    details = {"column_x": xs, "column_z": zs, "columns_xz": columns,
               "column_wood_y": [y0, y1], "roof": roof_info,
               "doorway": {"x_min": door_left, "x_max": door_right, "z": -hz,
                           "floor_y": ph, "clear_y_max": y1, "aisle_x": door_center},
               "wall_half_x": hx, "wall_half_z": hz, "platform_half_x": px, "platform_half_z": pz,
               "gable_end_overhang": (rx - hx if r["ridge_axis"] == "x" else rz - hz),
               "facade_panels":panels,"historical_reconstruction": False, "source_structure_reuse": False}
    return m, details, c


def verify(m, details, c):
    d = details["doorway"]
    canonical_column = join_state(*split_state(c["palette"]["column"]))
    occupied = m.blocks != 0
    _, components = label(occupied)  # six-neighbor face connectivity
    checks = {
        "face_connected_occupied_geometry": components == 1,
        "measured_bay_spacing_x": np.diff(details["column_x"]).tolist() == c["bays_x"],
        "measured_bay_spacing_z": np.diff(details["column_z"]).tolist() == c["bays_z"],
        "continuous_columns": all(m.get(x, y, z) == canonical_column for x, z in details["columns_xz"] for y in range(details["column_wood_y"][0], details["column_wood_y"][1] + 1)),
        "entrance_clear": all(m.get(x, y, d["z"]) == "minecraft:air" for x in range(d["x_min"], d["x_max"] + 1) for y in range(d["floor_y"] + 1, d["clear_y_max"] + 1)),
        "two_block_aisle": all(m.get(d["aisle_x"], y, z) == "minecraft:air" for z in range(d["z"], -d["z"]) for y in (d["floor_y"] + 1, d["floor_y"] + 2)),
        "gable_end_relation": (details["gable_end_overhang"] == 0) if c["roof"]["form"] == "flush_gable" else details["gable_end_overhang"] == c["eave_overhang"],
    }
    if not all(checks.values()):
        raise AssertionError(checks)
    return checks


def export(user, out_dir, name, render=True):
    if not isinstance(name, str) or not name or Path(name).name != name or any(v in name for v in '/\\:'):
        raise ValueError("name must be a simple filename stem")
    m, details, c = make_building(user)
    checks = verify(m, details, c)
    output = Path(out_dir)
    output.mkdir(parents=True, exist_ok=True)
    path = output / f"{name}.schem"
    write_schematic(m, path, name, generator="minecraft-chinese-architecture/chinese-building-1")
    from import_structure import load_structure
    # Independent parser path verifies serialization without sharing the writer.
    imported_blocks, imported_palette, _, _ = load_structure(path)
    checks["schematic_state_roundtrip"] = np.array_equal(np.array(m.palette)[m.blocks], np.array(imported_palette)[imported_blocks])
    if not checks["schematic_state_roundtrip"]:
        raise AssertionError(checks)
    m.save(output / f"{name}.npz")
    report = {"schema": "chinese-building-report/1", "name": name, "config": c,
              "dimensions_xyz": [m.blocks.shape[2], m.blocks.shape[0], m.blocks.shape[1]],
              "non_air_blocks": int(np.count_nonzero(m.blocks)), "design": details, "checks": checks,
              "coordinate_convention": {"npz_origin_xyz": m.origin.tolist(), "schematic_offset_xyz": [0, 0, 0],
                                        "mapping": "schematic cell XYZ = design XYZ - NPZ origin; add actual paste origin for world coordinates"},
              "schematic_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
              "validation_limits": ["Offline block-state, connectivity and clearance checks; no in-game verification.",
                                    "Shape-aware preview supports common partial blocks; unsupported states use cubes, with approximate colors and no transparency.",
                                    "Six roof morphologies are original prototypes, not measured historical reconstructions.",
                                    "Schematic DataVersion 4189 targets Java 1.21.4; custom palette states need registry checks."]}
    (output / f"{name}.report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if render:
        from render_shapes import render as render_shape_view
        for view in ("isometric", "front", "side"):
            render_shape_view(output / f"{name}.npz", output / f"{name}_{view}.png", view=view)
    return report


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--config", type=Path)
    ap.add_argument("--out-dir", type=Path, required=True)
    ap.add_argument("--name", default="building")
    ap.add_argument("--no-render", action="store_true")
    args = ap.parse_args()
    user = json.loads(args.config.read_text(encoding="utf-8-sig")) if args.config else {}
    report = export(user, args.out_dir, args.name, not args.no_render)
    print(json.dumps({k: report[k] for k in ("name", "dimensions_xyz", "non_air_blocks", "checks")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
