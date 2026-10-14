"""Measure the actual black core against an independent, same-frame sphere.

Run after the opt-in 50-frame Minecraft motion fixture finishes.  Production
projectedCenter is only a diagnostic; it is never the image acceptance oracle.
"""
from __future__ import annotations

import argparse
from collections import deque
import hashlib
import json
from pathlib import Path
import shutil

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
CASES = ["static-bob-on", "walk-bob-on", "walk-bob-off", "sprint-bob-on",
         "sprint-bob-off", "hurt-static", "hurt-walk-bob-on",
         "third-strafe-bob-on", "third-strafe-bob-off", "third-rotate-strafe"]
PHASES = [20, 28, 36, 44, 52]
CENTER = np.array([.5, 121.5, 14.5])
RADIUS = .58 * 1.6


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def vec(value):
    return np.array([value[k] for k in ("x", "y", "z")] if isinstance(value, dict) else value,
                    dtype=np.float64)


def erode(mask):
    padded = np.pad(mask, 1)
    h, w = mask.shape
    return np.logical_and.reduce([padded[y:y+h, x:x+w] for y in range(3) for x in range(3)])


def dilate(mask, radius=1):
    padded = np.pad(mask, radius)
    h, w = mask.shape
    return np.logical_or.reduce([padded[y:y+h, x:x+w]
                                 for y in range(2*radius+1) for x in range(2*radius+1)])


def boundary(mask):
    return mask & ~erode(mask)


def nearest_distances(points, targets):
    """Exact Euclidean pixel distances, without an optional SciPy dependency."""
    if not len(points) or not len(targets):
        return np.full(len(points), np.inf)
    return np.concatenate([np.sqrt(((part[:, None] - targets[None])**2).sum(axis=2).min(axis=1))
                           for part in np.array_split(points, max(1, (len(points)+255)//256))])


def keep_components(mask, minimum=4):
    """Discard single dark raster specks; keep separate core lobes around actors."""
    remaining = mask.copy()
    result = np.zeros_like(mask)
    h, w = mask.shape
    while remaining.any():
        y, x = np.argwhere(remaining)[0]
        remaining[y, x] = False
        queue, component = deque([(int(y), int(x))]), []
        while queue:
            py, px = queue.popleft()
            component.append((py, px))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = py+dy, px+dx
                    if 0 <= yy < h and 0 <= xx < w and remaining[yy, xx]:
                        remaining[yy, xx] = False
                        queue.append((yy, xx))
        if len(component) >= minimum:
            yy, xx = zip(*component)
            result[yy, xx] = True
    return result


def predicted_sphere(fixture, width, height):
    vp = np.array(fixture["terrainViewProjection"], dtype=np.float64).reshape(4, 4, order="F")
    inv = np.linalg.inv(vp)
    eye_h = inv @ np.array([0., 0., 1., 0.])
    assert abs(eye_h[3]) > 1e-10, "fixture must contain a perspective projection"
    origin = eye_h[:3] / eye_h[3]
    # PNG uses a top-left origin; fixture marker coordinates use bottom-left.
    yy, xx = np.indices((height, width), dtype=np.float64)
    clip = np.stack(((xx+.5)*2/width-1, 1-(yy+.5)*2/height,
                     np.ones_like(xx), np.ones_like(xx)), axis=-1)
    far_h = clip @ inv.T
    rays = far_h[..., :3] / far_h[..., 3, None] - origin
    rays /= np.linalg.norm(rays, axis=-1)[..., None]
    relative_center = CENTER - vec(fixture["rawCamera"])
    offset = relative_center - origin
    along = rays @ offset
    discriminant = along*along - offset @ offset + RADIUS*RADIUS
    mask = (along > 0) & (discriminant >= 0)
    return mask, origin, vp


def measure(name, source, output, tolerance, require_terrain):
    probe = read_json(source / (name+".json"))
    fixture = read_json(source / (name+"-fixture.json"))
    before = np.array(Image.open(source / (name+"-before.png")).convert("RGB"))
    field = np.array(Image.open(source / (name+"-field.png")).convert("RGB"))
    assert before.shape == field.shape, name
    height, width, _ = field.shape
    assert (width, height) == (probe["width"], probe["height"]), name
    assert fixture["fixture"] == "fixed-world-camera-motion/v1", name
    assert fixture["centerWasFixed"] and np.allclose(vec(fixture["fixedCenter"]), CENTER, atol=1e-10), name
    assert probe["glError"] == probe["outsideChangedPixels"] == 0, name
    assert probe["maxDepthDifference"] == 0, name
    assert probe["cameraContract"] == "perspective-eye-from-inverse-vp/v1", name
    assert probe["lens"] == 0 and probe["halo"] == 0, name
    assert abs(probe["scale"]-1.6) < 1e-6 and probe["coreColor"] == "#000000", name
    assert probe["particleCount"] == 48 and probe["particleSpeed"] == 0, name
    assert probe["particlesEnabled"] and probe["particleDraws"] > 0, name
    assert probe["frozenTicks"] == fixture["particlesFrozenTicks"] == 35, name
    assert abs(probe["capturedPartialTick"]-fixture["partialTick"]) < 1e-6, name
    expected, origin, vp = predicted_sphere(fixture, width, height)
    assert np.max(np.abs(vp-np.array(probe["viewProjection"]).reshape(4,4,order="F"))) < 1e-5, name
    origin_error = float(np.max(np.abs(origin-vec(probe["cameraOriginRelative"]))))
    assert origin_error < 2e-5, (name, origin_error)
    yy, xx = np.where(expected)
    assert len(xx) > 40, (name, "no appreciable sphere in the frame")
    padding = max(24, int((xx.max()-xx.min())*.6))
    roi = np.zeros((height, width), dtype=bool)
    roi[max(0, yy.min()-padding):min(height, yy.max()+padding+1),
        max(0, xx.min()-padding):min(width, xx.max()+padding+1)] = True
    # Detection uses the actual black output and a same-frame color change,
    # never an intersection with the predicted sphere silhouette.
    delta = np.max(np.abs(field.astype(np.int16)-before.astype(np.int16)), axis=2)
    observed = keep_components(roi & (field.max(axis=2) <= 3) & (delta >= 8))
    assert observed.sum() > 25, (name, "no actual black core pixels")
    terrain_path = source / (name+"-terrain.png")
    terrain_available = terrain_path.exists()
    assert terrain_available or not require_terrain, (name, "missing independent terrain PNG")
    if terrain_available:
        terrain = np.array(Image.open(terrain_path).convert("RGB"))
        assert terrain.shape == before.shape, name
        actor = np.max(np.abs(terrain.astype(np.int16)-before.astype(np.int16)), axis=2) > 2
        # One-pixel expansion avoids texture/filter silhouette edge ambiguity.
        excluded = dilate(actor)
        exclusion_method = "same-frame pre-entity terrain versus unmodified scene color"
    else:
        # No aesthetic pass is issued without independent occlusion evidence.
        # This fallback is only useful to inspect the fixture while adding it.
        excluded = np.zeros_like(expected)
        exclusion_method = "none; missing terrain PNG means actor coverage is unverified"
    expected_edge = boundary(expected)
    measured_edge = boundary(observed)
    visible_expected = expected_edge & ~excluded
    visible_measured = measured_edge & ~excluded
    expected_points = np.argwhere(visible_expected)
    measured_points = np.argwhere(visible_measured)
    expected_dist = nearest_distances(expected_points, np.argwhere(measured_edge))
    measured_dist = nearest_distances(measured_points, np.argwhere(expected_edge))
    coverage = float(visible_expected.sum()/max(1, expected_edge.sum()))
    sample_count = len(measured_points)
    max_error = float(max(expected_dist.max(initial=0), measured_dist.max(initial=0)))
    sufficient = sample_count >= 8 and len(expected_points) >= 8
    image_pass = sufficient and max_error <= tolerance
    # Both directions are checked: a shifted/shrunken core cannot hide its
    # missing half by reporting that the surviving half agrees with the VP.
    assert image_pass, (name, "actual core contour disagrees", sample_count, max_error, coverage)
    sheet = Image.fromarray(field).convert("RGB")
    pixels = np.array(sheet)
    pixels[expected_edge] = [45, 255, 105]
    pixels[visible_measured] = [255, 70, 80]
    pixels[expected_edge & excluded] = [255, 200, 50]
    Image.fromarray(pixels).save(output / (name+"-contour.png"))
    for suffix in (".json", "-fixture.json", "-before.png", "-field.png", "-after.png", "-terrain.png"):
        path = source / (name+suffix)
        if path.exists():
            shutil.copy2(path, output / path.name)
    return {"name": name, "case": fixture["case"], "phaseTick": fixture["phaseTick"],
            "actualBlackPixels": int(observed.sum()), "predictedSpherePixels": int(expected.sum()),
            "actualContourSamples": sample_count, "predictedContourSamples": len(expected_points),
            "totalPredictedContourSamples": int(expected_edge.sum()),
            "visibleContourCoverage": coverage, "silhouetteFullyVisible": bool(coverage == 1),
            "maximumSymmetricContourErrorPixels": max_error,
            "meanObservedContourErrorPixels": float(measured_dist.mean()),
            "meanExpectedContourErrorPixels": float(expected_dist.mean()),
            "independentTerrainAvailable": terrain_available, "occlusionExclusion": exclusion_method,
            "rayOriginMetadataErrorBlocks": origin_error, "cameraOriginCorrectionBlocks": float(np.linalg.norm(origin)),
            "playerPosition": fixture["playerPosition"], "walkDist": fixture["walkDist"],
            "bob": fixture["bob"], "bobView": fixture["bobView"], "hurtTime": fixture["hurtTime"],
            "sprinting": fixture["sprinting"], "cameraMode": fixture["cameraMode"],
            "glError": probe["glError"], "depthDifference": probe["maxDepthDifference"],
            "outsideChangedPixels": probe["outsideChangedPixels"],
            "fieldSha256": hashlib.sha256((source/(name+"-field.png")).read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT/"run/black-hole-lab/world-probes")
    parser.add_argument("--out", type=Path, default=ROOT/"evidence/camera-glow-fix-20261005/motion")
    parser.add_argument("--tolerance-pixels", type=float, default=1.5)
    parser.add_argument("--allow-missing-terrain", action="store_true",
                        help="Diagnostic only: skip independently derived actor occlusion; may fail obscured cases")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    measurements = [measure(f"motion-{case}-t{phase}", args.source, args.out,
                            args.tolerance_pixels, not args.allow_missing_terrain)
                    for case in CASES for phase in PHASES]
    case_metrics = {}
    for name in CASES:
        frames = [m for m in measurements if m["case"] == name]
        positions = np.array([vec(m["playerPosition"]) for m in frames])
        travel = float(np.linalg.norm(positions[-1]-positions[0]))
        walk_change = frames[-1]["walkDist"]-frames[0]["walkDist"]
        moving = name not in ("static-bob-on", "hurt-static")
        if moving:
            assert travel > .5 and walk_change > .1, (name, "fixture did not actually move", travel, walk_change)
        if moving and frames[0]["bobView"]:
            assert max(abs(m["bob"]) for m in frames) > .005, name
            assert max(m["cameraOriginCorrectionBlocks"] for m in frames) > .005, name
        if name.startswith("sprint"):
            assert any(m["sprinting"] for m in frames), name
        if name.startswith("hurt"):
            assert sum(m["hurtTime"] > 0 for m in frames) >= 2, name
        if name.startswith("third"):
            assert all(m["cameraMode"] == "THIRD_PERSON_BACK" for m in frames), name
        case_metrics[name] = {"frames": len(frames), "playerTravelBlocks": travel,
                              "walkDistanceChange": walk_change,
                              "maximumBob": max(abs(m["bob"]) for m in frames),
                              "hurtFrames": sum(m["hurtTime"] > 0 for m in frames),
                              "maximumCameraOriginCorrectionBlocks": max(m["cameraOriginCorrectionBlocks"] for m in frames)}
    report = {"status": "passed", "runtime": "Forge 1.20.1 development client",
              "frames": len(measurements), "cases": len(CASES),
              "independentOracle": "Float64 inverse fixture terrain VP, pixel-center ray/sphere intersection",
              "sphereCenterBlocks": CENTER.tolist(), "sphereRadiusBlocks": RADIUS,
              "pixelPrecision": "Euclidean distance between measured and independent raster contour pixel centers",
              "maximumPermittedContourErrorPixels": args.tolerance_pixels,
              "maximumMeasuredContourErrorPixels": max(m["maximumSymmetricContourErrorPixels"] for m in measurements),
              "actualContourSamples": sum(m["actualContourSamples"] for m in measurements),
              "minimumVisibleContourCoverage": min(m["visibleContourCoverage"] for m in measurements),
              "incompletelyVisibleSilhouettes": sum(not m["silhouetteFullyVisible"] for m in measurements),
              "fullCentroidAgreementClaimed": False,
              "caseMetrics": case_metrics, "measurements": measurements,
              "limits": ["Partial actor-obscured silhouettes verify the visible contour only.",
                         "The no-bend/no-lens fixture isolates camera alignment, not lens aesthetics."]}
    (args.out/"runtime.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("status", "frames", "cases", "actualContourSamples",
                                           "maximumMeasuredContourErrorPixels", "minimumVisibleContourCoverage")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
