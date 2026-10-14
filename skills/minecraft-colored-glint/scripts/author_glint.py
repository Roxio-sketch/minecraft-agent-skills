#!/usr/bin/env python3
"""Build custom/glowing gradient assets, GLSL and previews from a strict JSON recipe."""
from __future__ import annotations

import argparse
import colorsys
import hashlib
import json
import math
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


MODES = {"solid", "spatial", "temporal", "traveling"}
SPACES = {"srgb", "linear-rgb", "hsv-shortest"}
AXES = {"u", "v", "diagonal"}
WRAPS = {"repeat", "pingpong", "clamp"}
KEYS = {"version", "id", "mode", "stops", "interpolation", "wrap", "axis", "bands",
        "phase", "strength", "period_seconds", "tileable"}


def number(value, name, low, high):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not low <= value <= high or not math.isfinite(value):
        raise ValueError(f"{name} must be finite and between {low} and {high}")
    return float(value)


def identifier(value, name="id"):
    if not isinstance(value, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", value):
        raise ValueError(f"{name} must use 1-64 lowercase resource-safe characters")
    return value


def rgb(color):
    if not isinstance(color, str) or not re.fullmatch(r"#[0-9A-Fa-f]{6}", color):
        raise ValueError("Colors must be #RRGGBB; opacity is not a color stop")
    return tuple(int(color[i:i+2], 16) / 255.0 for i in (1, 3, 5))


def validate(raw):
    if not isinstance(raw, dict) or set(raw) - KEYS:
        raise ValueError("Recipe must be an object containing only documented fields")
    if type(raw.get("version")) is not int or raw.get("version") != 1:
        raise ValueError("version must be 1")
    style = dict(raw)
    style["id"] = identifier(style.get("id"))
    mode = style.get("mode")
    if not isinstance(mode, str) or mode not in MODES:
        raise ValueError(f"mode must be one of {sorted(MODES)}")
    stops = style.get("stops")
    if not isinstance(stops, list) or not (1 <= len(stops) <= 16):
        raise ValueError("Supply 1-16 stops")
    if (mode == "solid") != (len(stops) == 1):
        raise ValueError("solid requires exactly one stop; gradients require at least two")
    clean = []
    for stop in stops:
        if not isinstance(stop, dict) or set(stop) != {"at", "color"}:
            raise ValueError("Each stop requires exactly at and color")
        at = number(stop["at"], "stop.at", 0, 1)
        rgb(stop["color"])
        clean.append({"at": at, "color": stop["color"].upper()})
    if clean[0]["at"] != 0 or (len(clean) > 1 and clean[-1]["at"] != 1):
        raise ValueError("Stops must start at 0 and a gradient must end at 1")
    if any(a["at"] >= b["at"] for a, b in zip(clean, clean[1:])):
        raise ValueError("Stop positions must be strictly increasing; duplicates are not allowed")
    if any(b["at"] - a["at"] < 1e-6 for a, b in zip(clean, clean[1:])):
        raise ValueError("Adjacent stops must be at least 0.000001 apart for float shader precision")
    style["stops"] = clean
    for key, default, allowed in [("interpolation", "linear-rgb", SPACES), ("wrap", "pingpong", WRAPS), ("axis", "u", AXES)]:
        value = style.get(key, default)
        if not isinstance(value, str) or value not in allowed:
            raise ValueError(f"Invalid {key}: {value}")
        style[key] = value
    style["bands"] = number(style.get("bands", 1), "bands", 1, 16)
    style["phase"] = number(style.get("phase", 0), "phase", 0, 1)
    style["strength"] = number(style.get("strength", 1), "strength", 0, 4)
    style["period_seconds"] = number(style.get("period_seconds", 6), "period_seconds", 0.1, 3600)
    style["tileable"] = style.get("tileable", True)
    if not isinstance(style["tileable"], bool):
        raise ValueError("tileable must be boolean")
    if mode != "solid" and style["wrap"] == "repeat" and clean[0]["color"] != clean[-1]["color"]:
        raise ValueError("repeat requires identical endpoint colors; close the loop or use pingpong")
    if mode in {"temporal", "traveling"} and style["wrap"] == "clamp":
        raise ValueError("Animated recipes require repeat or pingpong; one-shot transitions need their own activation clock")
    if mode in {"spatial", "traveling"} and style["tileable"] and (style["wrap"] == "clamp" or not style["bands"].is_integer()):
        raise ValueError("Tileable spatial fields require integer bands and repeat/pingpong")
    return style


def to_linear(v):
    return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4


def to_srgb(v):
    return 12.92 * v if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055


def interpolate(a, b, t, space):
    if space == "srgb":
        return tuple(x + (y-x)*t for x, y in zip(a, b))
    if space == "linear-rgb":
        return tuple(to_srgb(to_linear(x) + (to_linear(y)-to_linear(x))*t) for x, y in zip(a, b))
    h0, s0, v0 = colorsys.rgb_to_hsv(*a)
    h1, s1, v1 = colorsys.rgb_to_hsv(*b)
    if s0 <= 1e-6:
        h0 = h1
    if s1 <= 1e-6:
        h1 = h0
    delta = (h1-h0+0.5) % 1.0 - 0.5
    return colorsys.hsv_to_rgb((h0+delta*t) % 1, s0+(s1-s0)*t, v0+(v1-v0)*t)


def palette(style, t):
    stops = style["stops"]
    if t <= stops[0]["at"]:
        return rgb(stops[0]["color"])
    for left, right in zip(stops, stops[1:]):
        if t <= right["at"]:
            return interpolate(rgb(left["color"]), rgb(right["color"]),
                               (t-left["at"]) / (right["at"]-left["at"]), style["interpolation"])
    return rgb(stops[-1]["color"])


def phase(style, u, v, seconds):
    mode = style["mode"]
    if mode == "solid":
        return 0.0
    q = style["phase"]
    if mode in {"spatial", "traveling"}:
        q += {"u": u, "v": v, "diagonal": u+v}[style["axis"]] * style["bands"]
    if mode in {"temporal", "traveling"}:
        q += seconds / style["period_seconds"]
    if style["wrap"] == "clamp":
        return max(0.0, min(1.0, q))
    q = q % 1.0
    return 1-abs(2*q-1) if style["wrap"] == "pingpong" else q


def color_at(style, u, v, seconds=0.0):
    return palette(style, phase(style, u, v, seconds))


def byte(v):
    return min(255, max(0, round(v * 255)))


def render(source, style, seconds=0.0):
    source = source.convert("RGBA")
    image = Image.new("RGBA", source.size)
    pixels = source.load()
    values = []
    for y in range(source.height):
        v = y / max(1, source.height-1)
        for x in range(source.width):
            u = x / max(1, source.width-1)
            r, g, b, alpha = pixels[x, y]
            intensity = max(r, g, b) / 255 * style["strength"]
            values.append((*[byte(c*intensity) for c in color_at(style, u, v, seconds)], alpha))
    image.putdata(values)
    return image


def fnum(value):
    # GLSL scalar literals remain floats even for integer-valued inputs.
    text = format(float(value), ".17g")
    return text if "." in text or "e" in text.lower() else text + ".0"


def vec(color):
    return "vec3(" + ", ".join(fnum(c) for c in rgb(color)) + ")"


def glsl(style):
    helpers = """float cgToLinear(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float cgToSrgb(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
vec3 cgRgbToHsv(vec3 c) {
    float hi = max(c.r, max(c.g, c.b));
    float lo = min(c.r, min(c.g, c.b));
    float d = hi - lo;
    float h = 0.0;
    if (d > 0.0000001) {
        if (hi == c.r) h = mod((c.g-c.b)/d, 6.0);
        else if (hi == c.g) h = (c.b-c.r)/d+2.0;
        else h = (c.r-c.g)/d+4.0;
        h /= 6.0;
    }
    return vec3(h, hi > 0.0 ? d/hi : 0.0, hi);
}
vec3 cgHsvToRgb(vec3 c) {
    vec3 p = clamp(abs(mod(c.x*6.0+vec3(0.0,4.0,2.0),6.0)-3.0)-1.0,0.0,1.0);
    return c.z * mix(vec3(1.0), p, c.y);
}
"""
    mix_body = {
        "srgb": "return mix(a,b,t);",
        "linear-rgb": "vec3 x=vec3(cgToLinear(a.r),cgToLinear(a.g),cgToLinear(a.b));\n    vec3 y=vec3(cgToLinear(b.r),cgToLinear(b.g),cgToLinear(b.b));\n    vec3 c=mix(x,y,t);\n    return vec3(cgToSrgb(c.r),cgToSrgb(c.g),cgToSrgb(c.b));",
        "hsv-shortest": "vec3 x=cgRgbToHsv(a), y=cgRgbToHsv(b);\n    if(x.y<=0.000001)x.x=y.x;\n    if(y.y<=0.000001)y.x=x.x;\n    float dh=mod(y.x-x.x+0.5,1.0)-0.5;\n    return cgHsvToRgb(vec3(fract(x.x+dh*t),mix(x.y,y.y,t),mix(x.z,y.z,t)));"
    }[style["interpolation"]]
    lines = ["// Generated palette math; effectUV must be supplied by the target rendering adapter.", helpers,
             "vec3 cgMix(vec3 a,vec3 b,float t) {\n    " + mix_body + "\n}", "vec3 cgPalette(float t) {"]
    first = style["stops"][0]
    lines.append(f"    if(t<={fnum(first['at'])})return {vec(first['color'])};")
    for left, right in zip(style["stops"], style["stops"][1:]):
        lines.append(f"    if(t<={fnum(right['at'])})return cgMix({vec(left['color'])},{vec(right['color'])},(t-{fnum(left['at'])})/{fnum(right['at']-left['at'])});")
    lines += [f"    return {vec(style['stops'][-1]['color'])};", "}", "float cgPhase(vec2 effectUV,float seconds) {"]
    if style["mode"] == "solid":
        lines.append("    return 0.0;")
    else:
        lines.append(f"    float q={fnum(style['phase'])};")
        if style["mode"] in {"spatial", "traveling"}:
            coordinate = {"u": "effectUV.x", "v": "effectUV.y", "diagonal": "(effectUV.x+effectUV.y)"}[style["axis"]]
            lines.append(f"    q+={coordinate}*{fnum(style['bands'])};")
        if style["mode"] in {"temporal", "traveling"}:
            lines.append(f"    q+=seconds/{fnum(style['period_seconds'])};")
        lines.append({"clamp": "    return clamp(q,0.0,1.0);", "repeat": "    return fract(q);", "pingpong": "    return 1.0-abs(2.0*fract(q)-1.0);"}[style["wrap"]])
    lines += ["}", "vec4 cgColorGlint(vec4 carrier,vec2 effectUV,float seconds) {",
              f"    float intensity=max(carrier.r,max(carrier.g,carrier.b))*{fnum(style['strength'])};",
              "    return vec4(clamp(cgPalette(cgPhase(effectUV,seconds))*intensity,0.0,1.0),carrier.a);", "}"]
    return "\n".join(lines) + "\n"


def shaders(style, namespace):
    shader_id = "glint_" + style["id"]
    moving = style["mode"] in {"temporal", "traveling"}
    vertex = """#version 150
#moj_import <fog.glsl>
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform mat4 TextureMat;
uniform int FogShape;
out float vertexDistance;
out vec2 texCoord0;
void main() {
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    vertexDistance=fog_distance(ModelViewMat,IViewRotMat*Position,FogShape);
    texCoord0=(TextureMat*vec4(UV0,0.0,1.0)).xy;
}
"""
    fragment = """#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float GlintAlpha;
in float vertexDistance;
in vec2 texCoord0;
out vec4 fragColor;
""" + ("uniform float EffectTimeSeconds;\n" if moving else "") + glsl(style)
    fragment += "void main() {\n    vec4 color=cgColorGlint(texture(Sampler0,texCoord0),texCoord0," + ("EffectTimeSeconds" if moving else "0.0") + ")*ColorModulator;\n"
    fragment += "    if(color.a<0.1)discard;\n    float fade=linear_fog_fade(vertexDistance,FogStart,FogEnd)*GlintAlpha;\n    fragColor=vec4(color.rgb*fade,color.a);\n}\n"
    uniforms = []
    for name, size in [("ModelViewMat", 4), ("ProjMat", 4), ("IViewRotMat", 3), ("TextureMat", 4)]:
        uniforms.append({"name": name, "type": f"matrix{size}x{size}", "count": size*size,
                         "values": [1.0 if i % (size+1) == 0 else 0.0 for i in range(size*size)]})
    for name, kind, values in [("ColorModulator", "float", [1., 1., 1., 1.]), ("FogStart", "float", [0.]),
                               ("FogEnd", "float", [1.]), ("GlintAlpha", "float", [1.]), ("FogShape", "int", [0])]:
        uniforms.append({"name": name, "type": kind, "count": len(values), "values": values})
    if moving:
        uniforms.append({"name": "EffectTimeSeconds", "type": "float", "count": 1, "values": [0.]})
    descriptor = {"blend": {"func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha"},
                  "vertex": f"{namespace}:{shader_id}", "fragment": f"{namespace}:{shader_id}",
                  "attributes": ["Position", "UV0"], "samplers": [{"name": "Sampler0"}], "uniforms": uniforms}
    return {shader_id+".vsh": vertex, shader_id+".fsh": fragment, shader_id+".json": json.dumps(descriptor, indent=2)+"\n"}


def build(source_path, style, output_dir, namespace="coloredglint", overwrite=False):
    style = validate(style)
    namespace = identifier(namespace, "namespace")
    source_path = Path(source_path).resolve(strict=True)
    sidecar = Path(str(source_path)+".mcmeta")
    if sidecar.exists():
        metadata = json.loads(sidecar.read_text(encoding="utf-8-sig"))
        if not isinstance(metadata, dict) or "animation" in metadata:
            raise ValueError("Use a static carrier PNG; animation metadata is not an independently loaded glint animation")
    with Image.open(source_path) as raw:
        if raw.format != "PNG" or getattr(raw, "n_frames", 1) != 1:
            raise ValueError("Carrier must be a static PNG")
        if not 1 <= raw.width <= 1024 or not 1 <= raw.height <= 1024:
            raise ValueError("Carrier dimensions must be within 1..1024")
        source = raw.convert("RGBA")
    out = Path(output_dir).resolve()
    target_root = out / style["id"]
    texture_root = Path("assets") / namespace / "textures/misc"
    shader_root = Path("assets") / namespace / "shaders/core"
    moving = style["mode"] in {"temporal", "traveling"}
    frame_name = "preview-phase0.png" if moving else "preview-texture.png"
    files = {Path("recipe.json"): json.dumps(style, indent=2)+"\n", Path("palette.glsl"): glsl(style)}
    files.update({shader_root/name: text for name, text in shaders(style, namespace).items()})
    images = {Path(frame_name): render(source, style)}
    # Shader inputs remain neutral: never apply a palette twice to an already tinted texture.
    r,g,b,a = source.split()
    intensity = ImageChops.lighter(ImageChops.lighter(r,g),b)
    gray = Image.merge("RGBA", (intensity,intensity,intensity,a))
    images[texture_root/f"glint_item_{style['id']}.png"] = gray if moving else images[Path(frame_name)]
    if not moving:
        images[texture_root/f"glint_carrier_{style['id']}.png"] = gray
    palette_image = Image.new("RGB", (512, 32))
    palette_image.putdata([tuple(byte(c) for c in palette(style, x/511)) for y in range(32) for x in range(512)])
    images[Path("palette.png")] = palette_image
    contact = Image.new("RGB", (4*192, 224), "#121722")
    draw = ImageDraw.Draw(contact)
    fractions = [0, .25, .5, .75] if moving else [0, 0, 0, 0]
    for i, frac in enumerate(fractions):
        tile = render(source, style, frac*style["period_seconds"]).resize((176,176),Image.Resampling.NEAREST)
        contact.paste(tile,(i*192+8,8),tile.getchannel("A"))
        draw.text((i*192+8,190),f"t={frac:g} period" if moving else ["texture", "repeat tile", "repeat tile", "repeat tile"][i],fill="white")
    images[Path("preview.png")] = contact
    descriptor = {"id": style["id"], "mode": style["mode"], "texture": f"{namespace}:textures/misc/glint_item_{style['id']}.png",
                  "shader_texture": f"{namespace}:textures/misc/glint_{'item' if moving else 'carrier'}_{style['id']}.png",
                  "shader": f"{namespace}:glint_{style['id']}", "shader_required": moving,
                  "static_texture_is_neutral_carrier": moving, "shader_coordinate": "transformed glint UV, not normalized blade coordinates",
                  "shader_uses_neutral_carrier": True, "period_seconds": style["period_seconds"],
                  "time_uniform": "EffectTimeSeconds" if moving else None,
                  "existing_lab_requires_registration": True, "mc_template": "Forge 1.20.1 / POSITION_TEX",
                  "preview_is_game_capture": False, "source_sha256": hashlib.sha256(source_path.read_bytes()).hexdigest(),
                  "tileable_applies_to": "palette field only; carrier seams must be checked separately"}
    files[Path("integration.json")] = json.dumps(descriptor, indent=2)+"\n"
    targets = [target_root/p for p in [*files, *images]]
    for target in targets:
        if not target.resolve().is_relative_to(out):
            raise ValueError(f"Output escapes its root through a link: {target}")
        for parent in target.parents:
            if parent == out:
                break
            if parent.is_symlink() or getattr(parent, "is_junction", lambda: False)():
                raise ValueError(f"Output parent must not be a link: {parent}")
            if parent.exists() and not parent.is_dir():
                raise ValueError(f"Output parent must be a directory: {parent}")
        if target.is_symlink() or (target.exists() and (not overwrite or not target.is_file())):
            raise ValueError(f"Output exists or cannot be replaced: {target}")
        if target.resolve() == source_path or (target.exists() and target.samefile(source_path)):
            raise ValueError("Cannot overwrite source carrier")
        if target.suffix == ".png" and Path(str(target)+".mcmeta").exists():
            raise ValueError(f"Remove or reconcile stale metadata before writing: {target}.mcmeta")
    for rel, content in files.items():
        target = target_root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w" if overwrite else "x", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
    for rel, image in images.items():
        target = target_root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("wb" if overwrite else "xb") as handle:
            image.save(handle, format="PNG")
    return target_root


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[1]/"assets/glint/glint_item_white.png")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--namespace", default="coloredglint")
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()
    try:
        spec_path = args.spec.resolve(strict=True)
        style = validate(json.loads(spec_path.read_text(encoding="utf-8-sig")))
        target = (args.output_dir/style["id"]).resolve()
        if target == spec_path.parent or target in spec_path.parents:
            raise ValueError("Input recipe must be outside its generated output folder")
        print(build(args.source, style, args.output_dir, args.namespace, args.overwrite))
    except (OSError, ValueError, TypeError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
