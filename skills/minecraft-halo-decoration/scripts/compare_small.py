#!/usr/bin/env python3
"""Reproduce standard/small Halo comparisons and sampled 16px separation metrics."""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw
import author_halo as halo

PRESETS = Path(__file__).resolve().parents[1] / "assets/presets"


def load(name):
    return halo.normalize(json.loads((PRESETS / f"{name}.json").read_text(encoding="utf-8")))


def luminance(im):
    return [[sum(w * halo.srgb_to_linear(c / 255) for w,c in zip((.2126,.7152,.0722),im.getpixel((x,y))))
             for x in range(im.width)] for y in range(im.height)]


def radial_mean(values, low, high):
    center = (len(values)-1)/2
    band = [values[y][x] for y in range(len(values)) for x in range(len(values))
            if low <= math.hypot(x-center,y-center) < high]
    return sum(band)/len(band)


def bright_components(values, threshold=.02):
    points = {(x,y) for y,row in enumerate(values) for x,v in enumerate(row) if v > threshold}
    sizes = []
    while points:
        stack, size = [points.pop()], 1
        while stack:
            x,y = stack.pop()
            for dx in (-1,0,1):
                for dy in (-1,0,1):
                    p = (x+dx,y+dy)
                    if p in points:
                        points.remove(p); stack.append(p); size += 1
        if size >= 2:
            sizes.append(size)
    return sizes


def measure(recipe, seconds):
    values = luminance(halo.preview(recipe,16,seconds))
    return {"seconds":seconds,
            "inner_band":radial_mean(values,2.4,3.6),
            "gap_band":radial_mean(values,4,4.8),
            "outer_band":radial_mean(values,5.4,6.6),
            "center":radial_mean(values,0,1.5),
            "bright_components":len(bright_components(values))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True, help="New directory, must not exist")
    args = parser.parse_args()
    names = ["gold-orbit","gold-orbit-small","crimson-rays","crimson-rays-small"]
    recipes = {name:load(name) for name in names}
    args.out.mkdir(parents=True,exist_ok=False)
    sheet = Image.new("RGB",(960,820),(15,20,31))
    draw = ImageDraw.Draw(sheet)
    draw.text((16,12),"STANDARD / SMALL RECIPES - CPU ANALYTIC PREVIEW, NOT GAMEPLAY",fill=(222,230,243))
    views = [(16,0),(16,1.4),(24,0),(32,0)]
    for row,name in enumerate(names):
        y=42+row*190
        draw.text((12,y+28),name,fill=(222,230,243))
        draw.text((12,y+47),"small recipe" if name.endswith("small") else "standard recipe",fill=(149,169,194))
        for col,(size,t) in enumerate(views):
            im=halo.preview(recipes[name],size,t)
            im.save(args.out/f"{name}-{size}px-t{t:g}.png")
            factor=160//size
            x=205+col*186
            sheet.paste(im.resize((size*factor,size*factor),Image.Resampling.NEAREST),(x,y))
            sheet.paste(im,(x+143,y+160))
            draw.text((x,y+162),f"{size}px x{factor} / t={t:g}s",fill=(149,169,194))
    sheet.save(args.out/"comparison.png")
    samples={name:[measure(recipes[name],i*.37) for i in range(120)] for name in names}
    gold=samples["gold-orbit-small"]; red=samples["crimson-rays-small"]
    result={"scope":"CPU 16px, 120 sampled phases (0..44.03s), dark background; no game or all-time proof",
            "canvas_half_extent":1.55,"gold_gap_pixel_radius":[4,4.8],
            "component_threshold_linear_luminance":.02,"minimum_component_pixels":2,
            "component_connectivity":8,
            "gold_min_peak_to_gap":min(min(s["inner_band"],s["outer_band"])/s["gap_band"] for s in gold),
            "red_component_counts":sorted(set(s["bright_components"] for s in red)),
            "samples":samples}
    (args.out/"metrics.json").write_text(json.dumps(result,indent=2),encoding="utf-8")
    print(json.dumps({k:v for k,v in result.items() if k!="samples"},indent=2))


if __name__ == "__main__":
    main()
