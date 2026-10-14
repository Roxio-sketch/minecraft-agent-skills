"""Compare Forge cube interiors to the production shader's full-window reference.

Requires Pillow and numpy. Run the Forge lab's flow-continuity action first.
Outer silhouette pixels are excluded; all internal face boundaries remain tested.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("run", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--installed", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads((args.run / "results.json").read_text(encoding="utf-8-sig"))
    references = {}
    checks = []
    frames = []
    for entry in data["frames"]:
        image = Image.open(args.run / entry["file"]).convert("RGB")
        pixels = np.array(image)
        time = entry["timeTicks"]
        if entry["reference"]:
            references[time] = pixels
            continue
        reference = references[time]
        # No antialiasing tolerance: erosion removes only the silhouette outline.
        mask = np.any(pixels != np.array([16, 20, 22]), axis=2)
        mask = np.array(Image.fromarray(mask.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(5))) > 0
        count = int(mask.sum())
        diff = np.abs(pixels.astype(np.int16) - reference.astype(np.int16))
        changed = int(np.count_nonzero(np.any(diff != 0, axis=2) & mask))
        max_error = int(diff[mask].max()) if count else 255
        unique = len(np.unique(pixels[mask], axis=0)) if count else 0
        checks.append({"file": entry["file"], "pixels": count, "changedPixels": changed,
                       "maxChannelDifference": max_error, "uniqueColors": unique,
                       "timeTicks": time, "rotationDegrees": entry["rotationDegrees"],
                       "passed": count > 5000 and changed == 0 and unique > 100 and entry["glError"] == 0})
        w, h = image.size
        crop = (int(w * 0.2), int(h * 0.12), int(w * 0.8), int(h * 0.88))
        left = Image.fromarray(reference).crop(crop).resize((480, 342))
        right = image.crop(crop).resize((480, 342))
        panel = Image.new("RGB", (960, 390), "#101416")
        panel.paste(left, (0, 48)); panel.paste(right, (480, 48))
        draw = ImageDraw.Draw(panel)
        draw.text((16, 15), f"Shared field | frozen tick {time:g}", fill="white")
        draw.text((496, 15), f"Native cube | rotation {entry['rotationDegrees']:g} deg | {changed} changed pixels", fill="white")
        frames.append(panel)
    hashes = {name: hashlib.sha256(path.read_bytes()).hexdigest()
              for name, path in [("artifact", args.jar), ("installed", args.installed)]}
    completed = (data["status"] == "completed" and data["restored"] and len(checks) == 32
                 and set(references) == {0, 200} and all(c["passed"] for c in checks)
                 and hashes["artifact"] == hashes["installed"])
    result = {"status": "passed" if completed else "failed", "checks": checks, "hashes": hashes,
              "testedPixels": sum(c["pixels"] for c in checks),
              "scope": "Fixed GUI camera, 16 native cube rotations at each of two frozen times. All interior face edges included.",
              "limits": ["Not object-local volume anchoring", "Does not certify world camera/FOV behavior", "Not a performance test"]}
    (args.run / "validation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    if frames:
        frames[0].save(args.run / "continuity.webp", save_all=True, append_images=frames[1:], duration=180, loop=0, lossless=True)
        sheet = Image.new("RGB", (960, 390 * 3), "#101416")
        for row, index in enumerate([0, 5, 11]):
            sheet.paste(frames[index], (0, row * 390))
        sheet.save(args.run / "continuity-sheet.png")
    print(json.dumps({"status": result["status"], "cases": len(checks), "testedPixels": result["testedPixels"],
                      "failed": [c for c in checks if not c["passed"]]}))
    raise SystemExit(0 if completed else 1)


if __name__ == "__main__":
    main()
