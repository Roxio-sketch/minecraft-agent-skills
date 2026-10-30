"""Original rectilinear covered corridor with shared corner roof and clear route."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import binary_dilation, distance_transform_cdt, label

from generate_courtyard import walk_paths
from generate_hall import write_schematic
from voxel_model import Model, split_state, join_state


DEFAULT = {
    "schema": "chinese-corridor/1", "path_xz": [[0, 0], [0, 18], [18, 18]],
    "clear_width": 3, "column_height": 6, "column_spacing": 6,
    "eave_overhang": 2, "roof_rise": 3, "roof_curve": 1.5,
    "railings": True,
    "palette": {"floor": "minecraft:stone_bricks", "column": "minecraft:stripped_dark_oak_log[axis=y]",
                "beam": "minecraft:dark_oak_planks", "railing": "minecraft:dark_oak_fence",
                "roof": "minecraft:deepslate_tiles", "roof_stairs": "minecraft:deepslate_tile_stairs",
                "roof_slab": "minecraft:deepslate_tile_slab", "trim": "minecraft:cut_copper"},
    "evidence": [],
}


def make_corridor(user):
    if not isinstance(user, dict) or set(user) - set(DEFAULT): raise ValueError("Unknown corridor config keys")
    c = copy.deepcopy(DEFAULT); c.update(user)
    if not isinstance(user.get("palette", {}), dict) or set(user.get("palette", {})) - set(DEFAULT["palette"]):
        raise ValueError("Invalid corridor palette")
    c["palette"] = {**DEFAULT["palette"], **user.get("palette", {})}
    if c["schema"] != DEFAULT["schema"]: raise ValueError("Unsupported corridor schema")
    for key, lo, hi in (("clear_width", 3, 7), ("column_height", 4, 12), ("column_spacing", 4, 10),
                        ("eave_overhang", 1, 4), ("roof_rise", 1, 6)):
        if type(c[key]) is not int or not lo <= c[key] <= hi: raise ValueError(f"{key} must be integer {lo}..{hi}")
    if c["clear_width"] % 2 == 0: raise ValueError("clear_width must be odd")
    if type(c["roof_curve"]) not in (int, float) or not np.isfinite(c["roof_curve"]) or not 1 <= c["roof_curve"] <= 2.5:
        raise ValueError("roof_curve must be finite 1..2.5")
    if type(c["railings"]) is not bool: raise ValueError("railings must be a boolean")
    for role, state in c["palette"].items():
        base, props = split_state(state)
        if base.endswith(":air"): raise ValueError("Materials cannot be air")
        suffix = {"roof_stairs": "_stairs", "roof_slab": "_slab", "railing": "_fence"}.get(role)
        if suffix and (props or not base.endswith(suffix)): raise ValueError(f"{role} requires a base {suffix} identifier")
        c["palette"][role] = join_state(base, props)
    points = c["path_xz"]
    if not isinstance(points, list) or not 2 <= len(points) <= 16: raise ValueError("path_xz needs 2..16 vertices")
    if any(not isinstance(pt, list) or len(pt) != 2 or any(type(v) is not int or abs(v) > 1000 for v in pt) for pt in points):
        raise ValueError("Path vertices must be integer XZ within +/-1000")
    fh = (c["clear_width"] + 1) // 2
    rh = fh + c["eave_overhang"]
    segments, line, column_candidates = [], set(), set()
    for first, last in zip(points, points[1:]):
        delta = np.array(last) - first
        if np.count_nonzero(delta) != 1: raise ValueError("Each path segment must be nonzero and axis aligned")
        length = int(abs(delta).sum())
        if not 2 * rh + 2 <= length <= 160: raise ValueError(f"Segment length must be {2 * rh + 2}..160")
        dx, dz = map(int, np.sign(delta))
        cells = [(first[0] + dx * i, first[1] + dz * i) for i in range(length + 1)]
        # Repeated center cells at the shared vertex are fine; self intersections
        # or retraced segments need an explicit junction design instead.
        duplicate = set(cells) & line
        allowed = {tuple(first)} if segments else set()
        if duplicate - allowed: raise ValueError("Self-intersecting or retraced path is not supported")
        line.update(cells)
        intervals = max(1, int(np.ceil(length / c["column_spacing"])))
        stations = sorted({round(i * length / intervals) for i in range(intervals + 1)})
        for station in stations:
            x, z = cells[station]
            for sign in (-1, 1): column_candidates.add((x - dz * fh * sign, z + dx * fh * sign))
        segments.append({"start": first, "end": last, "length": length, "direction": [dx, dz], "column_stations": stations})
    # Separate nonadjacent roofs as well as centerlines. Close return paths
    # otherwise silently merge into a broad canopy and create unintended exits.
    for i, a in enumerate(segments):
        for b in segments[i+2:]:
            gaps = [max(min(a['start'][k],a['end'][k])-max(b['start'][k],b['end'][k]),
                        min(b['start'][k],b['end'][k])-max(a['start'][k],a['end'][k]),0) for k in (0,1)]
            if max(gaps) < 2 * rh + 2:
                raise ValueError(f"Nonadjacent segments need centerline separation >= {2 * rh + 2} to keep roofs distinct")
    xs, zs = zip(*line)
    x0, z0 = min(xs) - rh - 1, min(zs) - rh - 1
    x1, z1 = max(xs) + rh + 1, max(zs) + rh + 1
    if (x1 - x0 + 1) * (z1 - z0 + 1) > 100_000: raise ValueError("Corridor footprint too large")
    center = np.zeros((z1 - z0 + 1, x1 - x0 + 1), dtype=bool)
    for x, z in line: center[z - z0, x - x0] = True
    square = lambda half: np.ones((half * 2 + 1, half * 2 + 1), dtype=bool)
    floor = binary_dilation(center, structure=square(fh))
    core = binary_dilation(center, structure=square(fh - 1))
    cover = binary_dilation(center, structure=square(rh))
    boundary = floor & ~core
    # The shared roof is distance-to-outside based. It stays one continuous skin
    # at elbows without pasting two overlapping capped roofs through each other.
    distances = distance_transform_cdt(cover, metric="chessboard")
    heights = c["column_height"] + 3 + np.rint(c["roof_rise"] * np.clip((distances - 1) / rh, 0, 1) ** c["roof_curve"]).astype(int)
    p = c["palette"]
    m = Model((x0, 0, z0), (x1, int(heights[cover].max()) + 2, z1))
    floor_id = m.pid(p["floor"])
    m.blocks[0][floor] = floor_id
    def in_mask(mask, x, z):
        return 0 <= z - z0 < mask.shape[0] and 0 <= x - x0 < mask.shape[1] and bool(mask[z - z0, x - x0])
    for x, z in points:
        for sx in (-1, 1):
            for sz in (-1, 1): column_candidates.add((x + sx * fh, z + sz * fh))
    columns = sorted((x, z) for x, z in column_candidates if in_mask(boundary, x, z))
    portals = []
    portal_cells = set()
    for endpoint, direction in ((points[0], [-v for v in segments[0]["direction"]]),
                                (points[-1], segments[-1]["direction"])):
        dx, dz = direction
        ex, ez = endpoint[0] + dx * fh, endpoint[1] + dz * fh
        opening = [(ex - dz * t, ez + dx * t) for t in range(-fh + 1, fh)]
        portal_cells.update(opening)
        portals.append({"center_floor_xyz": [ex, 0, ez], "outward_direction_xz": direction,
                        "clear_width": 2 * fh - 1})
    columns = [point for point in columns if point not in portal_cells]
    for x, z in columns:
        m.box((x, 1, z), (x, c["column_height"], z), p["column"])
    rail_cells = {(x + x0, z + z0) for z, x in zip(*np.nonzero(boundary))} - portal_cells - set(columns)
    # Boundary beams follow the footprint perimeter; inner elbow corners remain
    # above head level and the core walkway contains no post or railing.
    for iz, ix in zip(*np.nonzero(boundary)):
        m.set(int(ix + x0), c["column_height"] + 1, int(iz + z0), p["beam"])
    if c["railings"]:
        connected = rail_cells | set(columns)
        for x, z in sorted(rail_cells):
            props = {name: str((x + dx, z + dz) in connected).lower() for name, dx, dz in
                     (("east", 1, 0), ("north", 0, -1), ("south", 0, 1), ("west", -1, 0))}
            m.set(x, 1, z, p["railing"] + "[" + ",".join(f"{key}={value}" for key, value in sorted({**props, "waterlogged": "false"}.items())) + "]")
    for iz, ix in zip(*np.nonzero(cover)):
        x, z, y = int(ix + x0), int(iz + z0), int(heights[iz, ix])
        neighbors = {name: int(heights[iz + dz, ix + dx]) for name, dx, dz in
                     (("east", 1, 0), ("north", 0, -1), ("south", 0, 1), ("west", -1, 0)) if cover[iz + dz, ix + dx]}
        low = min([y, *neighbors.values()]) - 1
        m.box((x, low, z), (x, y, z), p["roof"])
        if neighbors and max(neighbors.values()) > y:
            uphill = [key for key, v in neighbors.items() if v == max(neighbors.values())]
            if len(uphill) == 1:
                m.set(x, y, z, p["roof_stairs"] + f"[facing={uphill[0]},half=bottom,shape=straight,waterlogged=false]")
        if distances[iz, ix] == 1: m.set(x, low, z, p["trim"])
        if distances[iz, ix] >= rh + 1: m.set(x, y + 1, z, p["roof_slab"] + "[type=bottom,waterlogged=false]")
    for x, z in columns:
        top = int(heights[z - z0, x - x0]) - 1
        for y in range(c["column_height"] + 2, top + 1):
            if m.get(x, y, z) == "minecraft:air": m.set(x, y, z, p["beam"])
    routes = walk_paths(m, portals[0]["center_floor_xyz"], {"exit": portals[1]["center_floor_xyz"]})
    clearance = all(m.get(x + x0, y, z + z0) == "minecraft:air" for z, x in zip(*np.nonzero(core)) for y in (1, 2))
    occupied = m.blocks != 0
    checks = {"continuous_core_clearance": clearance, "entry_to_exit": routes["all_targets_reached"],
              "face_connected_geometry": label(occupied)[1] == 1,
              "roof_covers_every_walkway_cell": bool(np.all(cover[floor])),
              "continuous_column_segments": all(m.get(x,y,z)==p["column"] for x,z in columns for y in range(1,c["column_height"]+1))}
    if not all(checks.values()): raise AssertionError(checks)
    return m, {"schema": "chinese-corridor-design/1", "config": c, "segments": segments,
               "columns_xz": [list(p) for p in columns], "portals": portals, "routes": routes,
               "checks": checks, "roof_method": "Shared chessboard distance-field canopy; original morphology, no historical joint reconstruction.",
               "source_structure_reuse": False, "historical_reconstruction": False}


def export(config, output, name="corridor", render=True):
    if not name or Path(name).name != name or any(v in name for v in '/\\:'): raise ValueError("Simple filename stem required")
    model, design = make_corridor(config)
    out = Path(output); out.mkdir(parents=True, exist_ok=True)
    schem, npz = out / f"{name}.schem", out / f"{name}.npz"
    write_schematic(model, schem, name, generator="minecraft-chinese-architecture/chinese-corridor-1")
    from import_structure import load_structure
    a, p, _, _ = load_structure(schem)
    design["checks"]["schematic_state_roundtrip"] = np.array_equal(np.array(p)[a], np.array(model.palette)[model.blocks])
    if not all(design["checks"].values()): raise AssertionError(design["checks"])
    model.save(npz)
    report = {"schema": "chinese-corridor-report/1", "name": name, "design": design,
              "dimensions_xyz": [model.blocks.shape[2], model.blocks.shape[0], model.blocks.shape[1]],
              "nonair_voxels": int(np.count_nonzero(model.blocks)), "schematic_sha256": hashlib.sha256(schem.read_bytes()).hexdigest(),
              "coordinate_convention": {"npz_origin_xyz": model.origin.tolist(), "schematic_offset_xyz": [0, 0, 0],
                                        "mapping": "schematic cell XYZ = design XYZ - NPZ origin"},
              "limits": ["Flat, rectilinear, open-ended path; no inclined corridor or live Minecraft traversal.",
                         "Roof elbow is an original continuous canopy, not a surveyed historic valley/ridge carpentry joint.",
                         "Endpoints require an external floor/landing to connect to a building."]}
    (out / f"{name}.report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if render:
        from render_shapes import render as preview
        for view in ("isometric", "top", "front"):
            preview(npz, out / f"{name}_{view}.png", view=view)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--name", default="corridor")
    parser.add_argument("--no-render", action="store_true")
    args = parser.parse_args()
    result = export(json.loads(args.config.read_text(encoding="utf-8-sig")) if args.config else {}, args.out_dir, args.name, not args.no_render)
    print(json.dumps({key: result[key] for key in ("name", "dimensions_xyz", "nonair_voxels")}, ensure_ascii=False))
