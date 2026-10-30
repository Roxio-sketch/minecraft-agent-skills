"""Original four-sided courtyard assembly with inward doors and voxel path QA.

This is a playable courtyard layout prototype, not a measured historical
siheyuan reconstruction. The entry pavilion has a second exterior stair run.
"""
from __future__ import annotations

import argparse
from collections import deque
import copy
import hashlib
import json
from pathlib import Path

import numpy as np

from generate_building import make_building, verify as verify_building
from generate_hall import write_schematic
from voxel_model import Model, split_state


def can_support_dry_walk(state):
    """Exclude known non-floor blocks; unknown states remain voxel approximations."""
    base, _ = split_state(state)
    if base in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air',
                'minecraft:water', 'minecraft:lava', 'minecraft:bubble_column',
                'minecraft:fire', 'minecraft:soul_fire', 'minecraft:ladder',
                'minecraft:vine', 'minecraft:short_grass', 'minecraft:tall_grass',
                'minecraft:fern', 'minecraft:large_fern', 'minecraft:structure_void'):
        return False
    if base.endswith(('_fence', '_fence_gate', '_wall', '_door', '_trapdoor', '_pane',
                      '_sapling', '_flower', '_torch', '_sign', '_banner')):
        return False
    return True


DEFAULT = {
    "schema": "chinese-courtyard/1", "court_width": 28, "court_depth": 30,
    "main": {"bays_x": [6, 8, 6], "bays_z": [6, 6], "column_height": 9,
             "roof": {"form": "hip_and_gable", "rise": 6, "uplift": 1}},
    "wing": {"bays_x": [6, 6, 6], "bays_z": [6], "column_height": 6,
             "roof": {"form": "overhanging_gable", "rise": 4, "uplift": 0}},
    "gate": {"kind": "pavilion", "bays_x": [8], "bays_z": [4], "column_height": 5,
             "platform_height": 1, "platform_margin": 2, "eave_overhang": 2,
             "roof": {"form": "overhanging_gable", "rise": 3, "uplift": 0}},
    "ground": "minecraft:polished_andesite", "path": "minecraft:smooth_stone",
    "boundary_wall": "minecraft:white_terracotta", "boundary_base": "minecraft:stone_bricks",
    "boundary_cap": "minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]",
    "wall_height": 4, "evidence": [],
}


def transform(xyz, offset, turns):
    x, y, z = xyz
    for _ in range(turns % 4): x, z = -z, x
    return [x + offset[0], y + offset[1], z + offset[2]]


def walk_paths(model, start_floor, targets_floor):
    """Find paths over solid voxels with two air cells and <=1-block changes.

    Stairs/slabs are conservatively whole occupied cells here. This is not a
    simulation of Minecraft player collision, jumping or automatic connections.
    Node XYZ is the supporting floor voxel, not the player's feet position.
    """
    occupied = model.blocks != 0
    walkable = np.zeros_like(occupied)
    support = np.array([can_support_dry_walk(s) for s in model.palette])[model.blocks]
    walkable[:-2] = support[:-2] & ~occupied[1:-1] & ~occupied[2:]
    def local(point):
        x, y, z = np.array(point, dtype=int) - model.origin
        return int(y), int(z), int(x)
    def world(point):
        y, z, x = point
        return (model.origin + [x, y, z]).tolist()
    start = local(start_floor)
    targets = {key: local(p) for key, p in targets_floor.items()}
    h, d, w = occupied.shape
    def valid(point):
        y, z, x = point
        return 0 <= y < h - 2 and 0 <= z < d and 0 <= x < w and walkable[y, z, x]
    if not valid(start): raise ValueError("Outside path start has no supported two-block space")
    if any(not valid(v) for v in targets.values()): raise ValueError("A room target lacks supported two-block space")
    prior = {start: None}
    queue = deque([start])
    pending = set(targets.values())
    while queue and pending:
        current = queue.popleft()
        pending.discard(current)
        y, z, x = current
        for dz, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            nz, nx = z + dz, x + dx
            for ny in (y, y + 1, y - 1):
                point = ny, nz, nx
                if point in prior or not valid(point): continue
                # Stepping up/down must not sweep the head through a low lintel.
                if ny > y and occupied[y + 3, z, x]: continue
                if ny < y and occupied[y + 2, nz, nx]: continue
                prior[point] = current
                queue.append(point)
    paths = {}
    for name, end in targets.items():
        if end not in prior: continue
        route = []
        while end is not None:
            route.append(world(end)); end = prior[end]
        paths[name] = list(reversed(route))
    return {"all_targets_reached": len(paths) == len(targets), "routes_floor_xyz": paths,
            "route_steps": {key: len(value) - 1 for key, value in paths.items()},
            "unreachable": sorted(set(targets) - set(paths)), "visited_nodes": len(prior),
            "limits": "Whole-voxel clearance with known liquids, plants and thin barriers excluded as dry floors; unknown states remain approximate. One-block steps/jumps allowed; not live player physics."}


def assemble(user):
    if not isinstance(user, dict) or set(user) - set(DEFAULT): raise ValueError("Unknown courtyard config keys")
    c = copy.deepcopy(DEFAULT)
    c.update(user)
    for name in ("main", "wing", "gate"):
        if not isinstance(user.get(name, {}), dict): raise ValueError(f"{name} config must be an object")
        if not isinstance(user.get(name, {}).get("roof", {}), dict): raise ValueError(f"{name}.roof must be an object")
        c[name] = {**DEFAULT[name], **user.get(name, {})}
        c[name]["roof"] = {**DEFAULT[name]["roof"], **user.get(name, {}).get("roof", {})}
    if c["schema"] != "chinese-courtyard/1": raise ValueError("Unknown courtyard schema")
    for key in ("court_width", "court_depth"):
        if type(c[key]) is not int or not 24 <= c[key] <= 80 or c[key] % 2:
            raise ValueError(f"{key} must be an even integer 24..80")
    if type(c["wall_height"]) is not int or not 2 <= c["wall_height"] <= 5: raise ValueError("wall_height must be 2..5")
    if c["gate"]["platform_height"] != 1: raise ValueError("Two-sided entrance pavilion currently requires platform_height=1")
    if c["gate"].get("kind") != "pavilion": raise ValueError("Entrance must be an open pavilion")
    parts = {}
    for name in ("main", "wing", "gate"):
        m, d, config = make_building(c[name])
        verify_building(m, d, config)
        parts[name] = m, d, config
    gate, gd, gc = parts["gate"]
    # Opposing entry run connects the open pavilion to the exterior ground.
    center = gd["doorway"]["aisle_x"]
    half = gc["stair_width"] // 2
    for step in range(2):
        z = gd["platform_half_z"] + 1 - step
        if step: gate.box((center - half, 0, z), (center + half, step - 1, z), gc["palette"]["foundation"])
        surface=gc["palette"]["foundation"] if step==0 else gc["palette"]["access_stairs"] + "[facing=north,half=bottom,shape=straight,waterlogged=false]"
        gate.box((center - half, step, z), (center + half, step, z), surface)

    hx, hz = c["court_width"] // 2, c["court_depth"] // 2
    main_z = -(hz + parts["main"][1]["platform_half_z"] + parts["main"][2]["platform_height"])
    wing_x = hx + parts["wing"][1]["platform_half_z"] + parts["wing"][2]["platform_height"]
    gate_z = hz + gd["platform_half_z"] + 1
    placements = [("main", "main", [0, 0, main_z], 2),
                  ("east_wing", "wing", [wing_x, 0, 0], 3),
                  ("west_wing", "wing", [-wing_x, 0, 0], 1),
                  ("gate", "gate", [0, 0, gate_z], 0)]
    bounds = []
    for ident, typ, offset, turns in placements:
        rotated = parts[typ][0].rotated(turns)
        bounds.append((rotated.origin + offset, rotated.maximum + offset))
    minimum = np.min([lo for lo, hi in bounds], axis=0)
    maximum = np.max([hi for lo, hi in bounds], axis=0)
    outer_x = max(abs(int(minimum[0])), abs(int(maximum[0]))) + 2
    outer_north = int(minimum[2]) - 3
    model = Model((-outer_x - 1, -1, outer_north - 1),
                  (outer_x + 1, max(int(maximum[1]), c["wall_height"] + 1), int(maximum[2]) + 3))
    model.box(tuple(model.origin), (int(model.maximum[0]), -1, int(model.maximum[2])), c["ground"])
    # Cross paths are laid below component foundations, with no air carving.
    model.box((-2, -1, main_z), (2, -1, int(model.maximum[2])), c["path"])
    model.box((-wing_x, -1, -2), (wing_x, -1, 2), c["path"])
    receipts, targets, doors = {}, {}, {}
    for ident, typ, offset, turns in placements:
        part, design, config = parts[typ]
        receipts[ident] = model.paste(part, offset=offset, turns=turns)
        door = design["doorway"]
        targets[ident] = transform([door["aisle_x"], door["floor_y"], door["z"] + 2], offset, turns)
        doors[ident] = {"opening_center_xyz": transform([door["aisle_x"], door["floor_y"] + 1, door["z"]], offset, turns),
                        "faces": ("north", "east", "south", "west")[turns],
                        "local_config": config, "placement": receipts[ident]}
    # The outer wall meets the gate platform at either side; no wall crosses
    # its opening. The wall is outside the other buildings' bounding boxes.
    gate_gap = max(gd["platform_half_x"], gd["roof"]["half_x"]) + 1
    cells = {(x, outer_north) for x in range(-outer_x, outer_x + 1)}
    cells |= {(x, gate_z) for x in range(-outer_x, outer_x + 1) if abs(x) >= gate_gap}
    cells |= {(x, z) for x in (-outer_x, outer_x) for z in range(outer_north, gate_z + 1)}
    for x, z in sorted(cells):
        for y in range(c["wall_height"] + 1):
            if model.get(x, y, z) != "minecraft:air": raise ValueError("Boundary wall collides with a building; enlarge courtyard or revise components")
            state = c["boundary_base"] if y == 0 else c["boundary_cap"] if y == c["wall_height"] else c["boundary_wall"]
            model.set(x, y, z, state)
    start = [center, -1, gate_z + gd["platform_half_z"] + 3]
    routes = walk_paths(model, start, targets)
    if not routes["all_targets_reached"]: raise ValueError(f"Courtyard routes blocked: {routes['unreachable']}")
    return model, {"schema": "chinese-courtyard-design/1", "config": c,
                   "parts": doors, "routes": routes, "start_floor_xyz": start,
                   "target_floors_xyz": targets, "boundary_x": [-outer_x, outer_x],
                   "boundary_z": [outer_north, gate_z], "clear_court_dimensions_xz": [2 * hx, 2 * hz],
                   "source_structure_reuse": False, "historical_reconstruction": False}


def export(config, output, name="courtyard", render=True):
    if not isinstance(name, str) or not name or Path(name).name != name or any(v in name for v in '/\\:'):
        raise ValueError("name must be a simple filename stem")
    model, design = assemble(config)
    out = Path(output); out.mkdir(parents=True, exist_ok=True)
    schem = out / f"{name}.schem"; npz = out / f"{name}.npz"
    write_schematic(model, schem, name, generator="minecraft-chinese-architecture/chinese-courtyard-1")
    from import_structure import load_structure
    b, p, _, _ = load_structure(schem)
    roundtrip = np.array_equal(np.array(model.palette)[model.blocks], np.array(p)[b])
    if not roundtrip: raise AssertionError("Courtyard schematic roundtrip differs")
    model.save(npz)
    report = {"schema": "chinese-courtyard-report/1", "name": name,
              "dimensions_xyz": [model.blocks.shape[2], model.blocks.shape[0], model.blocks.shape[1]],
              "nonair_voxels": int(np.count_nonzero(model.blocks)), "design": design,
              "coordinate_convention": {"npz_origin_xyz": model.origin.tolist(), "schematic_offset_xyz": [0, 0, 0],
                                        "mapping": "schematic cell XYZ = design XYZ - NPZ origin; add actual paste origin for world coordinates"},
              "checks": {"no_differing_component_collisions": sum(p["placement"]["differing_occupied_overlaps"] for p in design["parts"].values()) == 0,
                         "schematic_state_roundtrip": roundtrip, "outside_to_all_rooms": design["routes"]["all_targets_reached"]},
              "schematic_sha256": hashlib.sha256(schem.read_bytes()).hexdigest(),
              "limits": ["Original courtyard composition, not a surveyed siheyuan reconstruction.",
                         "No live Minecraft paste or player traversal; route check uses full occupied voxels and <=1-block height changes.",
                         "No automatic terrain adaptation, drainage, multi-court generation, garden design or interior furnishing."]}
    (out / f"{name}.report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if render:
        from render_shapes import render as preview
        for view in ("isometric", "top", "front"):
            preview(npz, out / f"{name}_{view}.png", view=view, size=(1800, 1400), camera_turns=0 if view == "top" else 2)
    return report


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--config", type=Path)
    ap.add_argument("--out-dir", type=Path, required=True)
    ap.add_argument("--name", default="courtyard")
    ap.add_argument("--no-render", action="store_true")
    args = ap.parse_args()
    cfg = json.loads(args.config.read_text(encoding="utf-8-sig")) if args.config else {}
    result = export(cfg, args.out_dir, args.name, not args.no_render)
    print(json.dumps({k: result[k] for k in ("name", "dimensions_xyz", "nonair_voxels", "checks")}, ensure_ascii=False))


if __name__ == "__main__": main()
