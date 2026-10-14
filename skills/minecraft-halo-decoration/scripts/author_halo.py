#!/usr/bin/env python3
"""Original Halo asset authoring and CPU previews; no Minecraft runtime."""
import argparse
import json
import math
import re
from pathlib import Path
from PIL import Image, ImageDraw

SCHEMA = "halo-decoration-v1"
CONTEXTS = {"GUI", "FIRST_PERSON_LEFT_HAND", "FIRST_PERSON_RIGHT_HAND",
            "THIRD_PERSON_LEFT_HAND", "THIRD_PERSON_RIGHT_HAND", "GROUND", "FIXED"}


def number(value, label, lo, hi):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{label}: finite number required")
    if not math.isfinite(value) or not lo <= value <= hi:
        raise ValueError(f"{label}: expected {lo}..{hi}")
    return float(value)


def keys(obj, allowed, label):
    if not isinstance(obj, dict):
        raise ValueError(f"{label}: object required")
    extra = set(obj) - set(allowed)
    if extra:
        raise ValueError(f"{label}: unsupported fields {sorted(extra)}")


def normalize(raw):
    keys(raw, {"schema", "id", "size", "contexts", "layers"}, "recipe")
    if raw.get("schema") != SCHEMA:
        raise ValueError(f"schema must be {SCHEMA}")
    ident = raw.get("id", "")
    if not isinstance(ident, str) or not re.fullmatch(r"[a-z][a-z0-9_-]{0,47}", ident):
        raise ValueError("id: lowercase letter followed by up to 47 letters/digits/_/-")
    size = raw.get("size", 256)
    if type(size) is not int or size not in (64, 128, 256, 512):
        raise ValueError("size: 64, 128, 256 or 512")
    contexts = raw.get("contexts", ["GUI"])
    if not isinstance(contexts, list) or not contexts or any(type(c) is not str or c not in CONTEXTS for c in contexts):
        raise ValueError("contexts: nonempty supported item contexts required")
    layers = raw.get("layers")
    if not isinstance(layers, list) or not 1 <= len(layers) <= 8:
        raise ValueError("layers: 1..8 layers")
    result = {"schema": SCHEMA, "id": ident, "size": size,
              "contexts": list(dict.fromkeys(contexts)), "layers": []}
    for i, source in enumerate(layers):
        keys(source, {"shape", "color", "blend", "radius", "width", "softness", "segments",
                      "duty", "opacity", "scale", "motion", "inner_radius"}, f"layer {i}")
        shape = source.get("shape")
        if shape not in ("glow", "ring", "rays"):
            raise ValueError("shape: glow, ring or rays")
        color = source.get("color", "#FFFFFF")
        if not isinstance(color, str) or not re.fullmatch(r"#[0-9a-fA-F]{6}", color):
            raise ValueError("color: #RRGGBB required")
        blend = source.get("blend", "additive")
        if blend not in ("alpha", "additive"):
            raise ValueError("blend: alpha or additive")
        layer = {"shape": shape, "color": color.upper(), "blend": blend}
        defaults = {"radius": (.65, .05, .88), "width": (.025, .004, .2),
                    "softness": (.04, .005, .2), "duty": (.7, .05, 1),
                    "opacity": (.8, 0, 1), "scale": (1, .1, 2)}
        for k, (default, lo, hi) in defaults.items():
            layer[k] = number(source.get(k, default), k, lo, hi)
        if "inner_radius" in source:
            if shape != "rays":
                raise ValueError("inner_radius is only supported on rays")
            layer["inner_radius"] = number(source["inner_radius"], "inner_radius", 0, .8)
            if layer["inner_radius"] >= layer["radius"]:
                raise ValueError("inner_radius must be smaller than radius")
        segments = source.get("segments", 12 if shape == "rays" else 0)
        if type(segments) is not int or not 0 <= segments <= 96 or (shape == "rays" and segments < 2):
            raise ValueError("segments: integer 0..96; rays require >=2")
        layer["segments"] = segments
        motion = source.get("motion", {})
        md = {"rotation_deg": (0, -360, 360), "speed_deg_s": (0, -360, 360),
              "period_s": (4, .1, 3600), "phase_deg": (0, -360, 360),
              "scale_amplitude": (0, 0, .45), "alpha_amplitude": (0, 0, 1)}
        keys(motion, md, "motion")
        layer["motion"] = {k: number(motion.get(k, v[0]), k, v[1], v[2]) for k, v in md.items()}
        result["layers"].append(layer)
    return result


def pose(layer, seconds):
    if not math.isfinite(seconds):
        raise ValueError("time must be finite")
    m = layer["motion"]
    wave = math.sin(math.tau * ((seconds % m["period_s"]) / m["period_s"]) + math.radians(m["phase_deg"]))
    return {"scale": layer["scale"] * (1 + m["scale_amplitude"] * wave),
            "opacity": min(1, max(0, layer["opacity"] * (1 + m["alpha_amplitude"] * wave))),
            "rotation_deg": (m["rotation_deg"] + m["speed_deg_s"] * seconds) % 360}


def coverage(layer, x, y):
    r = math.hypot(x, y)
    if r >= 1:
        return 0.0
    radius, width, shape = layer["radius"], layer["width"], layer["shape"]
    if shape == "glow":
        value = math.exp(-3 * (r / radius) ** 2)
    elif shape == "ring":
        d = abs(r - radius)
        value = min(1, math.exp(-.5 * (d / width) ** 2) + .2 * math.exp(-.5 * (d / (width + layer["softness"])) ** 2))
        if layer["segments"] and layer["duty"] < 1:
            phase = (math.atan2(y, x) / math.tau * layer["segments"]) % 1
            value *= max(0, min(1, (layer["duty"] / 2 - abs(phase - .5)) / .035 + .5))
    else:
        a = (math.atan2(y, x) * layer["segments"] / math.tau + .5) % 1 - .5
        value = math.exp(-.5 * (a / (layer["duty"] * .18)) ** 2)
        value *= math.exp(-.5 * ((r - radius * .65) / (radius * .32)) ** 2) * min(1, r / .12)
        if layer.get("inner_radius", 0) > 0:
            start = max(0, min(1, (r - layer["inner_radius"]) / .12))
            value *= start * start * (3 - 2 * start)
    fade = max(0, min(1, (1 - r) / .1))
    return min(1, value * fade * fade * (3 - 2 * fade))


def srgb_to_linear(v):
    return v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4


def linear_to_srgb(v):
    v = min(1, max(0, v))
    return 12.92 * v if v <= .0031308 else 1.055 * v ** (1 / 2.4) - .055


def rgb(hex_color):
    return tuple(int(hex_color[i:i+2], 16) for i in (1,3,5))


def mix_pixel(dst, src, alpha, blend):
    return tuple(d + s * alpha if blend == "additive" else d * (1-alpha) + s * alpha for d,s in zip(dst,src))


def texture(layer, size):
    # Straight alpha; keep the same RGB in transparent texels to avoid dark fringes.
    im = Image.new("RGBA", (size,size))
    color, values = rgb(layer["color"]), []
    for j in range(size):
        for i in range(size):
            a = sum(coverage(layer, (i+sx)*2/size-1, 1-(j+sy)*2/size)
                    for sx in (.25,.75) for sy in (.25,.75)) / 4
            values.append((*color,round(255*a)))
    im.putdata(values)
    return im


def preview(recipe, size, seconds=0, background=(15,20,31), subject=False):
    prepared = []
    for layer in recipe["layers"]:
        p = pose(layer,seconds)
        a = math.radians(p["rotation_deg"])
        prepared.append((layer,p,math.cos(a),math.sin(a),tuple(srgb_to_linear(v/255) for v in rgb(layer["color"]))))
    bg, pixels = tuple(srgb_to_linear(v/255) for v in background), []
    for j in range(size):
        for i in range(size):
            sums = [0.,0.,0.]
            for sx in (.25,.75):
                for sy in (.25,.75):
                    x,y = ((i+sx)*2/size-1)*1.55,(1-(j+sy)*2/size)*1.55
                    col = bg
                    for layer,p,c,s,tint in prepared:
                        u,v = (x*c+y*s)/p["scale"],(-x*s+y*c)/p["scale"]
                        col = mix_pixel(col,tint,coverage(layer,u,v)*p["opacity"],layer["blend"])
                    if subject and abs(x)+abs(y)<.34:
                        col = (.045,.062,.09)  # Original placeholder, not a Minecraft item.
                    for k in range(3):
                        sums[k] += col[k]/4
            pixels.append(tuple(round(255*linear_to_srgb(c)) for c in sums))
    im = Image.new("RGB",(size,size))
    im.putdata(pixels)
    return im


def export(recipe, output, display_px=None):
    if display_px is not None and (type(display_px) is not int or not 8 <= display_px <= 512):
        raise ValueError("display_px: integer 8..512")
    output = Path(output)
    output.mkdir(parents=True,exist_ok=False)
    texdir = output/"textures"
    texdir.mkdir()
    records = []
    for i,layer in enumerate(recipe["layers"]):
        name = f'{recipe["id"]}_{i:02}.png'
        texture(layer,recipe["size"]).save(texdir/name)
        records.append({"texture":f"textures/{name}","layer":i,"blend":layer["blend"],
                        "base_rgb_baked":True,"motion_baked":False,"opacity_baked":False})
    (output/"recipe.json").write_text(json.dumps(recipe,indent=2,ensure_ascii=False),encoding="utf-8")
    contract = {"schema":SCHEMA,"minecraft_parser_included":False,
                "evidence":"CPU analytic preview; not a Minecraft screenshot",
                "alpha":"straight","rgb_encoding":"sRGB palette values",
                "preview_compositing":"linear RGB, clamped on display; analytic supersampling",
                "uv":"full texture 0..1; top is +Y; radius 1 = half quad extent",
                "transform_order":"pivot translate, +Z rotation, uniform XY scale, then quad",
                "positive_rotation":"counterclockwise seen from +Z toward XY plane",
                "layer_order":"listed back to front; example subject is after all layers",
                "contexts_requested_not_implemented":recipe["contexts"],
                "blend_rgb":{"alpha":["SRC_ALPHA","ONE_MINUS_SRC_ALPHA"],"additive":["SRC_ALPHA","ONE"]},
                "target_alpha":"preview is opaque; runtime must explicitly preserve or accumulate target alpha",
                "textures":records,
                "motion_samples":[{"seconds":t,"layers":[pose(l,t) for l in recipe["layers"]]} for t in (0.,1.,2.,3.)]}
    if display_px is not None:
        contract["target_preview"] = {"canvas_px":display_px,"view_half_extent":1.55,
                                      "recipe_selection":"explicit; no automatic runtime LOD"}
        preview(recipe,display_px).save(output/"preview-target.png")
    (output/"render-contract.json").write_text(json.dumps(contract,indent=2,ensure_ascii=False),encoding="utf-8")
    for size in (16,32,256):
        preview(recipe,size).save(output/f"preview-{size}.png")
    sheet = Image.new("RGB",(640,372),(15,20,31))
    draw = ImageDraw.Draw(sheet)
    draw.text((10,7),recipe["id"]+" / CPU DESIGN PREVIEW",fill=(222,230,243))
    for i,t in enumerate((0.,1.,2.,3.)):
        sheet.paste(preview(recipe,160,t),(i*160,28))
        draw.text((i*160+12,190),f"t = {t:g} s",fill=(170,184,201))
    sheet.paste(preview(recipe,144,0,subject=True),(0,220))
    draw.text((8,207),"occlusion mock",fill=(170,184,201))
    for i,size in enumerate((16,32)):
        small = preview(recipe,size)
        sheet.paste(small.resize((128,128),Image.Resampling.NEAREST),(168+i*148,228))
        draw.text((168+i*148,207),f"native {size}px x nearest",fill=(170,184,201))
    sheet.paste(preview(recipe,128,0,background=(230,230,230)),(504,228))
    draw.text((504,207),"light background",fill=(170,184,201))
    sheet.save(output/"contact-sheet.png")
    return contract


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recipe",type=Path)
    parser.add_argument("--out",required=True,type=Path,help="New directory; must not exist")
    parser.add_argument("--display-px",type=int,help="Target preview canvas, 8..512 pixels; choose the recipe explicitly")
    args = parser.parse_args()
    try:
        recipe = normalize(json.loads(args.recipe.read_text(encoding="utf-8-sig")))
        contract = export(recipe,args.out,args.display_px)
    except (ValueError,OSError) as exc:
        parser.exit(2,f"Error: {exc}\n")
    print(json.dumps({"id":recipe["id"],"output":str(args.out.resolve()),"textures":len(contract["textures"]),"runtime_verified":False},ensure_ascii=False))


if __name__ == "__main__":
    main()
