"""Validate 15 real fixed-peak glow pairs and render reviewable PNG comparisons.

Pixel changes and successful GL calls establish capture/runtime integrity.
They do not establish that triangular sheets disappeared: inspect comparisons.
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
VIEWS = ["far", "near-side", "near-path", "upward"]
WIDTHS = {"normal": .07, "user": 1.154, "limit": 2.}
NAMES = [f"glow-{width}-{view}" for width in WIDTHS for view in VIEWS] + [
    "glow-sharp-user-near-path", "glow-sharp-limit-near-path", "glow-sharp-limit-near-side"]


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def close(a, b):
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(close(a[k], b[k]) for k in a)
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return abs(a-b) < 1e-5
    return a == b


def font(size):
    try:
        return ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", size)
    except OSError:
        return ImageFont.load_default()


def comparison(before, after, name, output):
    w, h = before.size
    sheet = Image.new("RGB", (2*w+30, h+72), "#101722")
    draw = ImageDraw.Draw(sheet)
    draw.text((10, 8), name + " | Before", font=font(18), fill="white")
    draw.text((w+20, 8), "After | real Minecraft capture", font=font(18), fill="white")
    sheet.paste(before, (10, 36))
    sheet.paste(after, (w+20, 36))
    draw.text((10, h+43), "Same seeded recipe, camera and elapsedTicks=1.2. Shape quality requires visual review.",
              font=font(15), fill="#bbc8db")
    sheet.save(output/(name+"-comparison.png"))
    return sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, default=ROOT/"evidence/camera-glow-fix-20261005/before-glow")
    parser.add_argument("--after", type=Path, default=ROOT/"run/lightning-lab/game-probes")
    parser.add_argument("--out", type=Path, default=ROOT/"evidence/camera-glow-fix-20261005/glow")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    saved = args.out/"after"
    saved.mkdir(exist_ok=True)
    measurements, thumbnails = [], []
    matching = ["mode", "elapsedTicks", "anchor", "recipe", "camera", "fixture", "view",
                "sharpStress", "focusPoint", "expectedCameraEye", "actualCameraEye", "yaw", "pitch",
                "mainCornerTurnDegrees", "mainCornerIndex", "mainCorner"]
    for name in NAMES:
        before_file, after_file = args.before/(name+".json"), args.after/(name+".json")
        old, new = read(before_file), read(after_file)
        # These captures predate the optional gradient controls. Preserve every
        # original value and compare against the explicitly historical additive path.
        for key, value in [("gradientRatio", 50), ("gradientTarget", "both"), ("gradientCoreMode", "additive")]:
            old["recipe"].setdefault(key, value)
            assert new["recipe"][key] == value, (name, "historical glow fixture changed gradient semantics")
        assert all(k in old and k in new and close(old[k], new[k]) for k in matching), (name, "capture inputs differ")
        assert new["fixture"] == "wide-glow/v1" and new["elapsedTicks"] == 1.2, name
        assert new["mode"] == "strike" and new["recipe"]["maxSegments"] == 1200, name
        assert 0 < new["generatedMainSegments"] <= 1200, name
        assert new["generatedMainSegments"] == old["generatedMainSegments"], name
        assert 0 < new["visibleMainSegments"] <= new["generatedMainSegments"], name
        assert new["visibleMainSegments"] == old["visibleMainSegments"], name
        expected_width = next(value for key, value in WIDTHS.items() if f"-{key}-" in name)
        assert abs(new["recipe"]["glowWidth"]-expected_width) < 1e-7, name
        assert new["recipe"]["coreGain"] == 16 and new["recipe"]["glowGain"] == 8.028, name
        assert new["haloGlError"] == 0 and new["haloPaths"] > 0, name
        assert new["haloBodyQuads"] > 0 and new["haloRoundQuads"] > 0, name
        assert new["haloCpuMillis"] >= 0, name
        old_png, new_png = args.before/(name+".png"), args.after/(name+".png")
        assert new_png.stat().st_mtime > old_png.stat().st_mtime, (name, "stale after capture")
        assert abs(new_png.stat().st_mtime-after_file.stat().st_mtime) < 10, (name, "PNG/JSON timestamp mismatch")
        left, right = Image.open(old_png).convert("RGB"), Image.open(new_png).convert("RGB")
        assert left.size == right.size, name
        a, b = np.array(left).astype(np.int16), np.array(right).astype(np.int16)
        delta = np.max(np.abs(a-b), axis=2)
        changed = int((delta > 2).sum())
        assert changed > 100, (name, "after image does not demonstrate a new render")
        sheet = comparison(left, right, name, args.out)
        sheet.thumbnail((1000, 400))
        thumbnails.append(sheet)
        for path in (after_file, new_png):
            shutil.copy2(path, saved/path.name)
        measurements.append({"name": name, "width": expected_width, "view": new["view"],
                             "sharpStress": new["sharpStress"], "mainCornerTurnDegrees": new["mainCornerTurnDegrees"],
                             "matchingRecipeCameraAndPeak": True, "generatedMainSegments": new["generatedMainSegments"],
                             "haloPaths": new["haloPaths"], "haloBodyQuads": new["haloBodyQuads"],
                             "haloRoundQuads": new["haloRoundQuads"], "haloGlError": new["haloGlError"],
                             "haloCpuMillis": new["haloCpuMillis"], "changedPixels": changed,
                             "meanAbsoluteRgbDifference": float(np.abs(a-b).mean()),
                             "afterPngSha256": hashlib.sha256(new_png.read_bytes()).hexdigest(),
                             "afterCaptureModifiedUtcEpoch": new_png.stat().st_mtime,
                             "comparison": name+"-comparison.png"})
    halo_source = ROOT/"src/main/java/dev/vfxworkbench/effects/lightning/LightningHalo.java"
    source = halo_source.read_text(encoding="utf-8")
    assert "GL20.glBlendEquationSeparate(GL14.GL_MAX,GL14.GL_MAX)" in source
    assert "GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD)" in source
    assert "frameRoundQuads+=mesh.round().size()/4" in source
    cell_w = max(img.width for img in thumbnails)
    cell_h = max(img.height for img in thumbnails)
    overview = Image.new("RGB", (cell_w*3, cell_h*5), "#101722")
    for index, thumbnail in enumerate(thumbnails):
        overview.paste(thumbnail, ((index%3)*cell_w, (index//3)*cell_h))
    overview.save(args.out/"glow-all-comparisons.png")
    geometry_log = ROOT/"evidence/camera-glow-fix-20261005/halo-geometry-check.log"
    geometry_check = geometry_log.exists() and "subdivision-stable maximum coverage (30510 assertions)" in geometry_log.read_text(encoding="utf-8-sig")
    report = {"status": "passed-runtime-checks-visual-review-required", "pairs": len(measurements),
              "widths": WIDTHS, "viewsPerWidth": VIEWS, "extraAcuteStressPairs": 3,
              "sameRecipeCameraAndPeak": True, "allPngCapturesFresh": True,
              "allGlErrorsZero": True, "positiveBodyAndRoundQuadMetrics": True,
              "pathCoverageUnion": "Source uses GL_MAX in isolated per-path target, then adds one composite per path",
              "sourceGlMaxUnionPresent": True, "geometryMaximumCoverageAssertionsPassed": geometry_check,
              "visualReviewRequired": True, "triangularSheetsDisappearanceAutomaticallyClaimed": False,
              "measurements": measurements,
              "limits": ["Framebuffer metrics establish runtime execution, not aesthetic correctness.",
                         "Inspect all 15 actual before/after PNG pairs for acute-joint sheets and clipping."]}
    (args.out/"runtime.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("status", "pairs", "allGlErrorsZero", "allPngCapturesFresh",
                                           "geometryMaximumCoverageAssertionsPassed")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
