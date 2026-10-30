"""Original parameterized Chinese-inspired hall; no source structure is copied.

The baseline proportions are authored design hypotheses, not measured statistics.
Arrays use blocks[y, z, x]. The .schem buffer is x-fastest, then z, then y.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path

import nbtlib
import numpy as np


DEFAULT = {
    "schema": "chinese-hall/1",
    "rule_status": "authored_hypothesis",
    "source_structure_reuse": False,
    "width_bays": 5,
    "depth_bays": 3,
    "bay_spacing": 6,
    "column_height": 8,
    "platform_height": 3,
    "platform_margin": 4,
    "eave_overhang": 4,
    "roof_rise": 7,
    "roof_curve": 1.65,
    "corner_uplift": 2,
    "stair_width": 7,
    "seed": 0,
    "roof_mix": [],
    "palette": {
        "foundation": "minecraft:stone_bricks",
        "floor": "minecraft:smooth_stone",
        "column": "minecraft:stripped_dark_oak_log[axis=y]",
        "beam": "minecraft:dark_oak_planks",
        "wall": "minecraft:white_terracotta",
        "window": "minecraft:brown_stained_glass",
        "roof": "minecraft:deepslate_tiles",
        "roof_stairs": "minecraft:deepslate_tile_stairs",
        "roof_slab": "minecraft:deepslate_tile_slab",
        "trim": "minecraft:cut_copper",
        "access_stairs": "minecraft:stone_brick_stairs",
    },
    "evidence": [],
}


def merge_config(user):
    config = dict(DEFAULT)
    config.update(user)
    config["palette"] = {**DEFAULT["palette"], **user.get("palette", {})}
    return config


def validate(c):
    if c["schema"] != "chinese-hall/1":
        raise ValueError("Unsupported config schema")
    ints = ["width_bays", "depth_bays", "bay_spacing", "column_height",
            "platform_height", "platform_margin", "eave_overhang", "roof_rise",
            "corner_uplift", "stair_width"]
    for key in ints:
        if type(c[key]) is not int:
            raise ValueError(f"{key} must be an integer")
    if c["width_bays"] not in range(3, 12, 2):
        raise ValueError("width_bays must be odd, between 3 and 11")
    if not 1 <= c["depth_bays"] <= 7:
        raise ValueError("depth_bays must be 1..7")
    if c["bay_spacing"] not in range(4, 11, 2):
        raise ValueError("bay_spacing must be even, between 4 and 10")
    if not 5 <= c["column_height"] <= 20:
        raise ValueError("column_height must be 5..20")
    if not 1 <= c["platform_height"] <= 8:
        raise ValueError("platform_height must be 1..8")
    if not 2 <= c["eave_overhang"] <= 6:
        raise ValueError("eave_overhang must be 2..6")
    if not 2 <= c["platform_margin"] <= 8:
        raise ValueError("platform_margin must be 2..8")
    if not 3 <= c["roof_rise"] <= 15:
        raise ValueError("roof_rise must be 3..15")
    if not 1.0 <= c["roof_curve"] <= 2.5:
        raise ValueError("roof_curve must be 1..2.5")
    if not 0 <= c["corner_uplift"] <= 4:
        raise ValueError("corner_uplift must be 0..4")
    if c["stair_width"] < 3 or c["stair_width"] % 2 != 1:
        raise ValueError("stair_width must be an odd integer >=3")
    for key, block in c["palette"].items():
        if not isinstance(block, str) or not block.startswith("minecraft:"):
            raise ValueError(f"palette.{key} must use a minecraft: identifier")
    for key in ["roof_stairs", "access_stairs"]:
        if not c["palette"][key].endswith("_stairs"):
            raise ValueError(f"palette.{key} must be a stairs block without properties")
    if not c["palette"]["roof_slab"].endswith("_slab"):
        raise ValueError("palette.roof_slab must be a slab block without properties")
    if type(c["seed"]) is not int:
        raise ValueError("seed must be an integer")
    if not isinstance(c["roof_mix"],list):
        raise ValueError("roof_mix must be an array")
    for family in c["roof_mix"]:
        if not isinstance(family,dict) or set(family) != {"weight","block","stairs","slab"}:
            raise ValueError("Every roof_mix entry needs exactly weight, block, stairs, slab")
        weight=family["weight"]
        if type(weight) not in (int,float) or not math.isfinite(weight) or weight<=0:
            raise ValueError("roof_mix weight must be a finite positive number")
        for key in ("block","stairs","slab"):
            state=family[key]
            if not isinstance(state,str) or not state.startswith("minecraft:") or "[" in state:
                raise ValueError(f"roof_mix.{key} must be a minecraft: base identifier without properties")
        if not family["stairs"].endswith("_stairs") or not family["slab"].endswith("_slab"):
            raise ValueError("roof_mix stairs and slab must name matching block types")
    if c["roof_mix"] and not math.isfinite(sum(f["weight"] for f in c["roof_mix"])):
        raise ValueError("roof_mix weight sum overflow")


def roof_family(c,x,z):
    """Stable weighted color variation, mirrored across the x and z axes.

    Weights are expected frequencies, not exact material-count quotas. They do
    not describe source roof shapes or copy source voxel arrangements.
    """
    families=c["roof_mix"]
    if not families:
        p=c["palette"]
        return {"block":p["roof"],"stairs":p["roof_stairs"],"slab":p["roof_slab"]}
    key=f'{c["seed"]}:{abs(x)}:{abs(z)}'.encode("ascii")
    val=int.from_bytes(hashlib.blake2b(key,digest_size=8).digest(),"big")/(1<<64)
    target=val*sum(f["weight"] for f in families)
    for family in families:
        target-=family["weight"]
        if target<0:
            return family
    return families[-1]


def stairs(name, facing, half="bottom"):
    return f"{name}[facing={facing},half={half},shape=straight,waterlogged=false]"


class Model:
    def __init__(self, minimum, maximum):
        self.origin = np.array(minimum, dtype=np.int32)
        size = np.array(maximum) - self.origin + 1
        self.blocks = np.zeros((size[1], size[2], size[0]), dtype=np.uint16)
        self.palette = ["minecraft:air"]
        self.index = {self.palette[0]: 0}

    def pid(self, block):
        if block not in self.index:
            self.index[block] = len(self.palette)
            self.palette.append(block)
        return self.index[block]

    def set(self, x, y, z, block):
        ix, iy, iz = np.array([x, y, z]) - self.origin
        self.blocks[iy, iz, ix] = self.pid(block)

    def box(self, a, b, block):
        ax, ay, az = np.array(a) - self.origin
        bx, by, bz = np.array(b) - self.origin
        self.blocks[ay:by+1, az:bz+1, ax:bx+1] = self.pid(block)

    def get(self, x, y, z):
        ix, iy, iz = np.array([x, y, z]) - self.origin
        return self.palette[int(self.blocks[iy, iz, ix])]


def make_model(c):
    validate(c)
    p = c["palette"]
    s = c["bay_spacing"]
    hx, hz = c["width_bays"] * s // 2, c["depth_bays"] * s // 2
    px, pz = hx + c["platform_margin"], hz + c["platform_margin"]
    rx, rz = hx + c["eave_overhang"], hz + c["eave_overhang"]
    ph, ch = c["platform_height"], c["column_height"]
    # column_height counts the uninterrupted wood, excluding the stone foot
    # and cross arms. This matches the source measurements.
    wood_y0, wood_y1 = ph + 2, ph + ch + 1
    cap_y, beam_y, eave_y = wood_y1 + 1, wood_y1 + 2, wood_y1 + 3
    stair_half = c["stair_width"] // 2
    if stair_half > px - 2:
        raise ValueError("stair_width exceeds platform")
    max_y = eave_y + c["roof_rise"] + c["corner_uplift"] + 3
    m = Model((-max(px, rx)-1, 0, -max(rz, pz+ph)-1),
              (max(px, rx)+1, max_y, max(pz, rz)+1))
    m.box((-px, 0, -pz), (px, ph, pz), p["foundation"])
    m.box((-px+1, ph, -pz+1), (px-1, ph, pz-1), p["floor"])
    # A continuous stair run rises toward +z (south), reaching the platform.
    for step in range(ph+1):
        z = -pz-ph+step
        if step:
            m.box((-stair_half, 0, z), (stair_half, step-1, z), p["foundation"])
        m.box((-stair_half, step, z), (stair_half, step, z), p["foundation"] if step == 0 else stairs(p["access_stairs"], "south"))

    xs, zs = list(range(-hx, hx+1, s)), list(range(-hz, hz+1, s))
    # Walls sit between a regular structural column grid. The central front bay
    # stays open; all room volume behind the envelope remains available.
    door_half = s//2-1
    for x in range(-hx, hx+1):
        for z in (-hz, hz):
            for y in range(ph+1, wood_y1+1):
                if z == -hz and abs(x) <= door_half:
                    continue
                on_column = x in xs
                window = ph+3 <= y <= wood_y1-2 and not on_column
                frame = window and ((x+hx) % s == s//2 or y == ph+ch//2+1)
                m.set(x, y, z, p["beam"] if frame else p["window"] if window else p["wall"])
    for z in range(-hz+1, hz):
        for x in (-hx, hx):
            for y in range(ph+1, wood_y1+1):
                window = ph+3 <= y <= wood_y1-2 and z not in zs
                frame = window and ((z+hz) % s == s//2 or y == ph+ch//2+1)
                m.set(x, y, z, p["beam"] if frame else p["window"] if window else p["wall"])
    for x in xs:
        for z in zs:
            m.set(x, ph+1, z, p["foundation"])
            m.box((x, wood_y0, z), (x, wood_y1, z), p["column"])
            # Cross arms are construction approximations, not an identification
            # of any historical dougong type or a copied source component.
            m.box((x-1, cap_y, z), (x+1, cap_y, z), p["beam"])
            m.box((x, cap_y, z-1), (x, cap_y, z+1), p["beam"])
    for z in zs:
        m.box((-rx, beam_y, z), (rx, beam_y, z), p["beam"])
    for x in xs:
        m.box((x, beam_y, -rz), (x, beam_y, rz), p["beam"])
    # Continuous perimeter beams connect the wall envelope.
    for z in (-hz, hz):
        m.box((-hx, beam_y-1, z), (hx, beam_y-1, z), p["beam"])
    for x in (-hx, hx):
        m.box((x, beam_y-1, -hz), (x, beam_y-1, hz), p["beam"])

    roof_heights, roof_surface = {}, {}
    for z in range(-rz, rz+1):
        for x in range(-rx, rx+1):
            d = min(rx-abs(x), rz-abs(z))
            rise = c["roof_rise"] * (d / min(rx, rz))**c["roof_curve"]
            uplift = c["corner_uplift"] * (abs(x)/rx)**6 * (abs(z)/rz)**6
            roof_surface[x, z] = eave_y + rise + uplift
            roof_heights[x, z] = eave_y + int(round(rise + uplift))
    for (x, z), y in roof_heights.items():
        family=roof_family(c,x,z)
        neighbor_y = [roof_heights.get((x+dx, z+dz), y) for dx, dz in ((1,0),(-1,0),(0,1),(0,-1))]
        # Connected shell: vertical joining pieces span adjacent roof levels.
        low = max(eave_y-1, min(neighbor_y)-1)
        m.box((x, low, z), (x, y, z), family["block"])
        dx_edge, dz_edge = rx-abs(x), rz-abs(z)
        if y > min(neighbor_y) and dx_edge != dz_edge:
            if dx_edge < dz_edge:
                facing = "west" if x > 0 else "east"
            else:
                facing = "north" if z > 0 else "south"
            # The high half follows the actual uphill surface, including an
            # upturned corner whose local slope can point away from the ridge.
            directions = {"east":(1,0), "west":(-1,0), "south":(0,1), "north":(0,-1)}
            uphill = {k:roof_surface.get((x+dx,z+dz),-math.inf) for k,(dx,dz) in directions.items()}
            maximum = max(uphill.values())
            if maximum > roof_surface[x,z]+1e-9:
                if uphill[facing] < maximum-1e-9:
                    facing = max(uphill,key=uphill.get)
                m.set(x, y, z, stairs(family["stairs"], facing))
        # Warm lower fascia makes the eave layers legible without a second roof.
        if abs(x) == rx or abs(z) == rz:
            m.set(x, low, z, p["trim"])
    # Short posts under the shell connect every column to the roof where needed.
    for x in xs:
        for z in zs:
            top = roof_heights[x, z]-1
            if top >= beam_y+1:
                m.box((x, beam_y+1, z), (x, top, z), p["beam"])
    # Main ridge follows the longer axis; all ridge blocks sit on the roof.
    ridge_half = abs(rx-rz)
    ridge_y = eave_y+c["roof_rise"]+1
    if rx >= rz:
        for x in range(-ridge_half, ridge_half+1):
            m.set(x, ridge_y, 0, roof_family(c,x,0)["slab"]+"[type=bottom,waterlogged=false]")
        for x in (-ridge_half, ridge_half):
            m.set(x, ridge_y, 0, p["trim"])
    else:
        for z in range(-ridge_half, ridge_half+1):
            m.set(0, ridge_y, z, roof_family(c,0,z)["slab"]+"[type=bottom,waterlogged=false]")
        for z in (-ridge_half, ridge_half):
            m.set(0, ridge_y, z, p["trim"])
    details = {
        "column_x": xs, "column_z": zs,
        "column_wood": {"y_min": wood_y0, "y_max": wood_y1, "length": ch,
                        "block": p["column"], "excludes": ["stone base", "cross arms", "beams"]},
        "column_span_x": 2*hx, "column_span_z": 2*hz,
        "roof_span_x": 2*rx+1, "roof_span_z": 2*rz+1,
        "doorway": {"x_min": -door_half, "x_max": door_half, "z": -hz,
                    "floor_y": ph, "clear_y_min": ph+1, "clear_y_max": wood_y1},
        "roof_heights": {"min": min(roof_heights.values()), "max": max(roof_heights.values())},
        "ratios": {"eave_to_bay": c["eave_overhang"]/s,
                   "column_height_to_bay": ch/s,
                   "roof_rise_to_half_depth": c["roof_rise"]/min(rx,rz),
                   "platform_to_column_height": ph/ch},
        "roof_mixture": {"method":"deterministic coordinate hash, mirrored x/z", "seed":c["seed"],
                         "weight_interpretation":"approximate family frequencies; not exact count quotas",
                         "enabled":bool(c["roof_mix"])},
    }
    return m, details


def encode_varints(values):
    output = bytearray()
    for val in np.asarray(values).ravel(order="C"):
        val = int(val)
        while val >= 128:
            output.append((val & 127) | 128)
            val >>= 7
        output.append(val)
    return bytes(output)


def decode_varints(data):
    output, value, shift = [], 0, 0
    for byte in data:
        byte = int(byte) & 255
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
            if shift > 28:
                raise ValueError("Invalid VarInt")
        else:
            output.append(value)
            value, shift = 0, 0
    if shift:
        raise ValueError("Truncated VarInt")
    return np.array(output, dtype=np.uint32)


def write_schematic(model, path, name, generator="minecraft-chinese-architecture/chinese-hall-1", data_version=4189, author="Original procedural example"):
    if type(data_version) is not int or not 0 < data_version < 2**31:
        raise ValueError('data_version must be a positive signed 32-bit integer')
    h, length, width = model.blocks.shape
    data = encode_varints(model.blocks)
    root = nbtlib.File({
        "Version": nbtlib.Int(2), "DataVersion": nbtlib.Int(data_version),
        "Width": nbtlib.Short(width), "Height": nbtlib.Short(h), "Length": nbtlib.Short(length),
        "Offset": nbtlib.IntArray([0, 0, 0]),
        "PaletteMax": nbtlib.Int(len(model.palette)),
        "Palette": nbtlib.Compound({p: nbtlib.Int(i) for i,p in enumerate(model.palette)}),
        "BlockData": nbtlib.ByteArray(np.frombuffer(data, dtype=np.int8)),
        "BlockEntities": nbtlib.List[nbtlib.Compound](),
        "Entities": nbtlib.List[nbtlib.Compound](),
        "Metadata": nbtlib.Compound({"Name": nbtlib.String(name),
                                     "Author": nbtlib.String(author),
                                     "Generator": nbtlib.String(generator)}),
    }, root_name="Schematic")
    # Fixed gzip mtime and blank embedded filename make the binary deterministic.
    with open(path, "wb") as raw, gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0) as stream:
        root.write(stream)


def verify(model, details, schem_path):
    root = nbtlib.load(schem_path)
    values = decode_varints(root["BlockData"])
    roundtrip = np.array_equal(values.reshape(model.blocks.shape), model.blocks)
    palette_ok = {str(k): int(v) for k,v in root["Palette"].items()} == model.index
    occupancy = model.blocks != 0
    symmetric = np.array_equal(occupancy, occupancy[:,:,::-1])
    reflected_states = [s.replace("facing=east","facing=TEMP").replace("facing=west","facing=east").replace("facing=TEMP","facing=west") for s in model.palette]
    reflection = np.array([model.index.get(s,-1) for s in reflected_states])
    state_symmetry = np.array_equal(model.blocks,reflection[model.blocks[:,:,::-1]])
    d = details["doorway"]
    clear = all(model.get(x,y,d["z"]) == "minecraft:air"
                for x in range(d["x_min"], d["x_max"]+1)
                for y in range(d["clear_y_min"], d["clear_y_max"]+1))
    # A full two-block-high center aisle must reach the back interior wall.
    aisle_clear = all(model.get(0,y,z) == "minecraft:air"
                      for z in range(d["z"], -d["z"])
                      for y in (d["floor_y"]+1, d["floor_y"]+2))
    wood = details["column_wood"]
    columns_exact = all(model.get(x,y,z) == wood["block"]
                        for x in details["column_x"] for z in details["column_z"]
                        for y in range(wood["y_min"], wood["y_max"]+1))
    # Occupancy connectivity catches detached ornaments and unjoined roof pieces.
    # It does not approximate physics or the partial collision shape of stairs.
    from collections import deque
    start = tuple(int(v[0]) for v in np.nonzero(occupancy))
    visited = np.zeros(occupancy.shape,dtype=bool)
    visited[start] = True
    todo=deque([start]); connected_count=0
    h,l,w=occupancy.shape
    while todo:
        y,z,x=todo.popleft();connected_count+=1
        for ny,nz,nx in ((y-1,z,x),(y+1,z,x),(y,z-1,x),(y,z+1,x),(y,z,x-1),(y,z,x+1)):
            if 0<=ny<h and 0<=nz<l and 0<=nx<w and occupancy[ny,nz,nx] and not visited[ny,nz,nx]:
                visited[ny,nz,nx]=True;todo.append((ny,nz,nx))
    checks = {"schematic_voxel_roundtrip": bool(roundtrip), "schematic_palette_roundtrip": palette_ok,
              "left_right_occupancy_symmetry": symmetric, "front_doorway_clear": clear,
              "left_right_block_state_symmetry": state_symmetry,
              "center_aisle_two_block_clearance": aisle_clear,
              "continuous_column_material_matches_height": columns_exact,
              "face_connected_occupied_geometry": connected_count == int(occupancy.sum())}
    if not all(checks.values()):
        raise AssertionError(checks)
    return checks


def export(config, out_dir, name, render=True):
    config = merge_config(config)
    model, details = make_model(config)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    schem = out_dir / f"{name}.schem"
    write_schematic(model, schem, name)
    checks = verify(model, details, schem)
    npz = out_dir / f"{name}.npz"
    np.savez_compressed(npz, blocks=model.blocks, palette=np.array(model.palette), origin=model.origin)
    counts = np.bincount(model.blocks.ravel(), minlength=len(model.palette))
    report = {
        "schema": "chinese-hall-report/1", "generator_revision": 2, "name": name,
        "config": config, "dimensions_xyz": [model.blocks.shape[2], model.blocks.shape[0], model.blocks.shape[1]],
        "non_air_blocks": int(np.count_nonzero(model.blocks)),
        "palette_counts": {b:int(counts[i]) for i,b in enumerate(model.palette) if counts[i]},
        "design": details, "checks": checks,
        "schematic_sha256": hashlib.sha256(schem.read_bytes()).hexdigest(),
        "coordinate_convention": "blocks[y,z,x]; original local minimum stored in NPZ origin; schematic Offset is [0,0,0]",
        "validation_limits": ["Offline NBT and geometry checks only; not loaded in a running Minecraft client.",
                              "Block identifiers target Java 1.21.4; custom palette entries must be checked against the target registry.",
                              "Authored roof proportions are hypotheses until linked to measured evidence; cross arms do not reconstruct historical dougong.",
                              "Preview treats occupied blocks as cubes; real stairs, slabs, glass, textures, light and block updates differ."],
    }
    (out_dir/f"{name}.report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2), encoding="utf-8")
    if render:
        from render_model import render_views
        render_views(npz, out_dir, name)
    return report


def self_test():
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        a = export({}, d, "a", render=False)
        b = export({}, d, "b", render=False)
        # Metadata Name intentionally differs. Repeating the same name is byte-identical.
        original = Path(d,"a.schem").read_bytes()
        export({}, d, "a", render=False)
        assert original == Path(d,"a.schem").read_bytes()
        for cfg in [{"width_bays":3,"depth_bays":1,"corner_uplift":0},
                    {"width_bays":7,"depth_bays":5,"bay_spacing":4,"column_height":6,"roof_curve":2.2},
                    {"width_bays":3,"depth_bays":7,"bay_spacing":4,"eave_overhang":2}]:
            export(cfg, d, "variant", render=False)
        assert np.array_equal(decode_varints(encode_varints([0,127,128,255,300,16384])), [0,127,128,255,300,16384])
        mix=[{"weight":1,"block":"minecraft:blackstone","stairs":"minecraft:blackstone_stairs","slab":"minecraft:blackstone_slab"},
             {"weight":1,"block":"minecraft:polished_blackstone_bricks","stairs":"minecraft:polished_blackstone_brick_stairs","slab":"minecraft:polished_blackstone_brick_slab"},
             {"weight":1,"block":"minecraft:deepslate_tiles","stairs":"minecraft:deepslate_tile_stairs","slab":"minecraft:deepslate_tile_slab"},
             {"weight":1,"block":"minecraft:polished_blackstone","stairs":"minecraft:polished_blackstone_stairs","slab":"minecraft:polished_blackstone_slab"}]
        cfg=merge_config({"roof_mix":mix,"seed":17,"width_bays":11,"depth_bays":3})
        mixed,_=make_model(cfg)
        repeated,_=make_model(cfg)
        alternate,_=make_model({**cfg,"seed":18})
        assert np.array_equal(mixed.blocks,repeated.blocks) and mixed.palette==repeated.palette
        assert mixed.palette!=alternate.palette or not np.array_equal(mixed.blocks,alternate.blocks)
        used_states={mixed.palette[int(i)] for i in np.unique(mixed.blocks)}
        used_base={s.split("[")[0] for s in used_states}
        assert all(f[key] in used_base for f in mix for key in ("block","stairs","slab")), used_base
        export(cfg,d,"mixed",render=False)
        for bad in [[{}],[{**mix[0],"weight":0}],[{**mix[0],"weight":float("nan")}],[{**mix[0],"stairs":"minecraft:stone"}],"bad"]:
            try:validate(merge_config({"roof_mix":bad}))
            except ValueError:pass
            else:raise AssertionError(f"Malformed mix accepted: {bad}")
    print("PASS: deterministic export; voxel/palette roundtrip; state symmetry; doorway/aisle clearance; connected geometry; config variants; VarInt boundaries; seeded roof mixtures and malformed input rejection")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--config", type=Path)
    ap.add_argument("--out-dir", type=Path)
    ap.add_argument("--name", default="courtyard-hall")
    ap.add_argument("--no-render", action="store_true")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        self_test()
        return
    if args.out_dir is None:
        ap.error("--out-dir is required")
    if Path(args.name).name != args.name or any(c in args.name for c in '/\\:'):
        ap.error("--name must be a simple filename stem")
    config = json.loads(args.config.read_text(encoding="utf-8-sig")) if args.config else {}
    report = export(config,args.out_dir,args.name,not args.no_render)
    print(json.dumps({"output":str(args.out_dir.resolve()), "dimensions":report["dimensions_xyz"],
                      "non_air_blocks":report["non_air_blocks"],"checks":report["checks"]},ensure_ascii=False))


if __name__ == "__main__":
    main()
