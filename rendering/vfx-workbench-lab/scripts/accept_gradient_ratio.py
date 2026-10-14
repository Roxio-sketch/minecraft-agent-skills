"""Verify five completed Minecraft gradient-ratio captures using actual RGB pixels.

The fixed gradient-ratio/v1 camera aims at a dark calibration board. Only board
pixels whose endpoint difference follows the requested palette are measured;
sky, sun, unchanged grid and saturated strokes are excluded. Brightness bands
sample soft glow and bright strokes, and are not isolated render passes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RATIOS = [0, 25, 50, 75, 100]
START = np.array([255., 32., 64.])
END = np.array([32., 96., 255.])
COUNTS = ["generatedMainSegments", "visibleMainSegments", "visibleImpactSegments",
          "haloPaths", "haloBodyQuads", "haloRoundQuads"]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def font(size):
    try:
        return ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", size)
    except OSError:
        return ImageFont.load_default()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def metrics(array, mask, endpoint, delta):
    rgb = array[mask]
    red, _, blue = rgb.T
    projection = np.sum((array - endpoint) * delta, axis=2)[mask] / np.sum(delta * delta, axis=2)[mask]
    return {"pixels": int(mask.sum()), "meanRgb": rgb.mean(axis=0).tolist(),
            "meanRedBlueShare": float(np.mean(red / np.maximum(red + blue, 1))),
            "redDominantFraction": float(np.mean(red > blue + 5)),
            "blueDominantFraction": float(np.mean(blue > red + 5)),
            "meanEndpointMix": float(projection.mean()),
            "medianEndpointMix": float(np.median(projection))}


def contact_sheet(images, rows, bbox, output, cropped=False):
    samples = [im.crop(bbox).resize((2 * (bbox[2] - bbox[0]), 2 * (bbox[3] - bbox[1])), Image.Resampling.NEAREST)
               for im in images] if cropped else images
    columns = 5 if cropped else 3
    lines = 1 if cropped else 2
    cell_w, image_h = samples[0].size
    cell_h = image_h + 82
    sheet = Image.new("RGB", (columns * cell_w, lines * cell_h + 44), "#101722")
    draw = ImageDraw.Draw(sheet)
    for index, (ratio, sample, row) in enumerate(zip(RATIOS, samples, rows)):
        x, y = index % columns * cell_w, index // columns * cell_h
        draw.text((x + 12, y + 6), f"Ratio {ratio}%", fill="white", font=font(23))
        suffix = " | all end color" if ratio == 0 else " | all start color" if ratio == 100 else " | legacy default" if ratio == 50 else ""
        draw.text((x + 12, y + 38), f"R/(R+B): {row['meanRedBlueShare']:.2%}" + suffix,
                  fill="#c5d5e6", font=font(17))
        sheet.paste(sample, (x, y + 76))
    if not cropped:
        x, y = 2 * cell_w + 20, cell_h + 30
        for index, line in enumerate(["Actual Minecraft RGB captures", "Start: #ff2040  |  End: #2060ff",
                                      "Same recipe, seed, time, camera and geometry", "GL errors: 0 in all five frames",
                                      "Pixel measurements use only the dark board",
                                      "Soft glow and bright strokes both change color"]):
            draw.text((x, y + index * 38), line, fill="#c5d5e6", font=font(22))
    draw.text((12, lines * cell_h + 8), "Measured color share is R/(R+B), not the configured gradient ratio or a physical area fraction.",
              fill="#abb9c9", font=font(17))
    sheet.save(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--captures", type=Path, default=ROOT / "run/lightning-lab/game-probes")
    parser.add_argument("--log", type=Path, default=ROOT / "evidence/camera-glow-fix-20261005/gradient-ratio-run.log")
    parser.add_argument("--out", type=Path, default=ROOT / "evidence/gradient-ratio-20261005")
    args = parser.parse_args()
    log = args.log.read_text(encoding="utf-8-sig", errors="replace")
    require("COMPLETE: 5 fixed-peak captures; supplied default and options restored" in log,
            "The five-frame fixture did not finish normally")
    require("BUILD SUCCESSFUL" in log and "All dimensions are saved" in log,
            "The runtime did not close successfully")
    source_paths = [ROOT / "src/main/java/dev/vfxworkbench/effects/lightning" / name
                    for name in ["LightningAppearance.java", "LightningRecipe.java", "LightningDemo.java"]]
    newest_source = max(path.stat().st_mtime for path in source_paths)
    images, arrays, captures, hashes = [], [], [], []
    baseline_recipe, baseline_metadata = None, None
    for ratio in RATIOS:
        name = f"gradient-ratio-{ratio}"
        png, sidecar = args.captures / (name + ".png"), args.captures / (name + ".json")
        require(png.exists() and sidecar.exists(), f"Missing capture {name}")
        require(png.stat().st_mtime > newest_source and sidecar.stat().st_mtime > newest_source,
                f"Capture {name} predates the final fixture/renderer sources")
        require(abs(png.stat().st_mtime - sidecar.stat().st_mtime) < 5, f"PNG/JSON timestamp mismatch: {name}")
        require(log.count(f"[LIGHTNING-DEMO] capture={name} ") == 1, f"Capture not uniquely logged: {name}")
        data = read(sidecar)
        require(data["name"] == name and data["fixture"] == "gradient-ratio/v1", f"Wrong fixture: {name}")
        require(data["mode"] == "strike" and data["elapsedTicks"] == 1.2 and data["haloGlError"] == 0,
                f"Invalid fixed peak or GL result: {name}")
        recipe = data["recipe"]
        require(recipe["gradientEnabled"] is True and recipe["gradientRatio"] == ratio, f"Wrong live ratio: {name}")
        require(recipe["gradientStart"] == "#ff2040" and recipe["gradientEnd"] == "#2060ff", f"Wrong palette: {name}")
        require(recipe["coreColor"] == recipe["glowColor"] == "#ffffff"
                and recipe["coreGain"] == recipe["glowGain"] == 1, f"Confounding tint or gain: {name}")
        recipe_without_ratio = {k: v for k, v in recipe.items() if k != "gradientRatio"}
        # The only other varying input is the capture name; CPU time is an output.
        metadata = {k: v for k, v in data.items() if k not in ("name", "recipe", "haloCpuMillis")}
        if baseline_recipe is None:
            baseline_recipe, baseline_metadata = recipe_without_ratio, metadata
        require(recipe_without_ratio == baseline_recipe, f"Non-ratio recipe changed: {name}")
        require(metadata == baseline_metadata, f"Camera, fixture or geometry changed: {name}")
        require(all(data[k] > 0 for k in COUNTS if k != "visibleImpactSegments"), f"Missing geometry: {name}")
        require(data["visibleImpactSegments"] == 0 and data["haloCpuMillis"] >= 0, f"Unexpected effect/timing: {name}")
        image = Image.open(png).convert("RGB")
        require(not images or image.size == images[0].size, f"Image dimensions changed: {name}")
        images.append(image); arrays.append(np.asarray(image).astype(np.float64)); captures.append(data)
        hashes.append({"name": name, "pngSha256": sha(png), "jsonSha256": sha(sidecar),
                       "captureModifiedUtcEpoch": png.stat().st_mtime})
    require(len({row["pngSha256"] for row in hashes}) == 5, "PNG captures did not all change")

    first, last = arrays[0], arrays[-1]
    height, width = first.shape[:2]
    # The documented fixed far-view calibration board, inside its visible border.
    bbox = (int(width * .345), int(height * .16), int(width * .653), int(height * .65))
    roi = np.zeros((height, width), dtype=bool)
    roi[bbox[1]:bbox[3], bbox[0]:bbox[2]] = True
    delta, palette_delta = last - first, START - END
    norm = np.linalg.norm(delta, axis=2)
    alignment = np.sum(delta * palette_delta, axis=2) / np.maximum(norm * np.linalg.norm(palette_delta), 1e-10)
    intensity = np.sum(delta * palette_delta, axis=2) / np.dot(palette_delta, palette_delta)
    mask = (roi & (norm > 12) & (alignment > .97)
            & (first.max(axis=2) < 248) & (last.max(axis=2) < 248)
            & (np.min(arrays, axis=0).max(axis=2) < 100))
    require(mask.sum() >= 500, "Too few actual palette-colored, unsaturated stroke pixels")
    unchanged_board = float(np.mean(np.max(np.abs(delta[roi]), axis=1) <= 2))
    require(unchanged_board >= .85, "The calibration board/camera changed beyond the thin stroke")
    bands = {"allStroke": mask, "softHalo": mask & (intensity < .15), "brightStroke": mask & (intensity > .5)}
    measured = {}
    for band, selected in bands.items():
        require(selected.sum() >= 100, f"Too few {band} pixels")
        rows = [{"ratio": ratio, **metrics(array, selected, first, delta)} for ratio, array in zip(RATIOS, arrays)]
        for before, after in zip(rows, rows[1:]):
            require(after["meanRedBlueShare"] > before["meanRedBlueShare"] + .03, f"Color share failed to increase: {band}")
            require(after["meanEndpointMix"] > before["meanEndpointMix"] + .03, f"Intermediate PNGs failed to follow palette: {band}")
            require(after["meanRgb"][0] > before["meanRgb"][0] + 1
                    and after["meanRgb"][2] < before["meanRgb"][2] - 1, f"Actual red/blue channels failed to change: {band}")
        require(rows[0]["blueDominantFraction"] > .95 and rows[-1]["redDominantFraction"] > .75,
                f"Endpoint colors do not switch from end blue to start red: {band}")
        measured[band] = rows
    require(measured["brightStroke"][-1]["redDominantFraction"] > .95, "Bright core/stroke endpoint failed")
    transitions = []
    for before_ratio, after_ratio, before, after in zip(RATIOS, RATIOS[1:], arrays, arrays[1:]):
        changed = int(((np.max(np.abs(after - before), axis=2) > 3) & mask).sum())
        require(changed > mask.sum() * .4, "Too few actual changed stroke pixels between adjacent ratios")
        monotonic = ((after[:, :, 0] >= before[:, :, 0] - 2) & (after[:, :, 2] <= before[:, :, 2] + 2))[mask]
        require(float(monotonic.mean()) >= .98, "Per-pixel red/blue trend reverses")
        transitions.append({"beforeRatio": before_ratio, "afterRatio": after_ratio, "changedStrokePixels": changed,
                            "monotonicPixelFractionWithTwoByteTolerance": float(monotonic.mean())})

    args.out.mkdir(parents=True, exist_ok=True)
    saved = args.out / "captures"; saved.mkdir(exist_ok=True)
    for ratio in RATIOS:
        for suffix in (".png", ".json"):
            path = args.captures / (f"gradient-ratio-{ratio}" + suffix)
            shutil.copy2(path, saved / path.name)
    shutil.copy2(args.log, args.out / "gradient-ratio-run.log")
    contact_sheet(images, measured["allStroke"], bbox, args.out / "contact-sheet.png")
    contact_sheet(images, measured["allStroke"], bbox, args.out / "board-contact-sheet.png", cropped=True)
    overlay = np.asarray(images[2]).copy()
    overlay[mask] = [230, 240, 80]
    overlay[bands["softHalo"]] = [40, 220, 220]
    overlay[bands["brightStroke"]] = [255, 80, 120]
    Image.fromarray(overlay).save(args.out / "measured-pixels.png")
    report = {"status": "passed", "fixture": "gradient-ratio/v1", "ratios": RATIOS,
              "runtimeCompletedAndBuildSuccessful": True, "capturesNewerThanFinalSources": True,
              "allGlErrorsZero": True, "onlyRecipeDifference": "gradientRatio",
              "cameraAndOtherMetadataIdentical": True, "geometryCountsIdentical": {k: captures[0][k] for k in COUNTS},
              "imageSize": [width, height], "boardRoiPixels": list(bbox), "measuredStrokePixels": int(mask.sum()),
              "unchangedBoardPixelFraction": unchanged_board, "paletteStart": "#ff2040", "paletteEnd": "#2060ff",
              "pixelMask": {"minimumEndpointRgbDistance": 12, "minimumPaletteDeltaCosine": .97,
                            "maximumUnsaturatedEndpointChannelExclusive": 248, "maximumMinimumAcrossFramesChannelExclusive": 100},
              "measuredBands": measured, "transitions": transitions, "captureHashes": hashes,
              "sourceHashes": {str(path.relative_to(ROOT)): sha(path) for path in source_paths},
              "limits": ["R/(R+B) is a measured screen-color share, not a physical area fraction or the recipe percentage.",
                         "Soft halo and bright stroke use endpoint intensity bands; overlapping core/halo passes are not isolated.",
                         "The fixed camera board ROI excludes sky/sun; unchanged grid and saturated stroke pixels are omitted."]}
    (args.out / "runtime.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"status": report["status"], "captures": len(RATIOS), "measuredStrokePixels": int(mask.sum()),
                      "allGlErrorsZero": True, "redBlueShares": [row["meanRedBlueShare"] for row in measured["allStroke"]]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
