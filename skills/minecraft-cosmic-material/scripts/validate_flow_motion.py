"""Validate real-clock Forge motion and frozen bookends; preserve capture timing."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("run", type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--installed", required=True, type=Path)
    args = parser.parse_args()
    data = json.loads((args.run / "results.json").read_text(encoding="utf-8"))
    checks = []
    def check(name, passed, detail):
        checks.append({"name": name, "passed": bool(passed), "detail": detail})
    groups = {name: [f for f in data["frames"] if f["phase"] == name]
              for name in ["frozen-before", "live", "frozen-after"]}
    check("completed_and_restored", data["status"] == "completed" and data["restored"], data["status"])
    dimensions = sorted({(f["width"], f["height"]) for f in data["frames"]})
    check("high_resolution", len(dimensions) == 1 and dimensions[0][0] >= 2500 and dimensions[0][1] >= 1400, dimensions)
    check("sampled_gl_errors", all(f["glError"] == 0 for f in data["frames"]), [f["glError"] for f in data["frames"]])
    check("all_phases_captured", all(len(f) >= (12 if phase == "live" else 4) for phase, f in groups.items()),
          {phase: len(f) for phase, f in groups.items()})
    for phase in ["frozen-before", "frozen-after"]:
        frames = groups[phase]
        differences = []
        if frames:
            first = np.array(Image.open(args.run / frames[0]["file"]).convert("RGB"))
            for frame in frames[1:]:
                pixels = np.array(Image.open(args.run / frame["file"]).convert("RGB"))
                delta = np.abs(pixels.astype(np.int16) - first.astype(np.int16))
                differences.append({"file": frame["file"], "changedPixels": int(np.any(delta != 0, axis=2).sum()),
                                    "maxChannelDifference": int(delta.max())})
        check(phase + ":zero_pixel_difference", bool(differences) and all(d["changedPixels"] == 0 for d in differences), differences)
        check(phase + ":fixed_uniform", bool(frames) and len({f["shaderSeconds"] for f in frames}) == 1 and
              all(f["frozenTime"] >= 0 for f in frames), [f["shaderSeconds"] for f in frames])
    live = groups["live"]
    motion, panels, gaps = [], [], []
    if live:
        check("actual_game_clock", all(f["frozenTime"] == -1 and
              np.float32(f["shaderSeconds"]) == np.float32(f["shaderTicks"]) / np.float32(20) for f in live),
              {"firstShaderSeconds": live[0]["shaderSeconds"], "lastShaderSeconds": live[-1]["shaderSeconds"]})
        wall_span = live[-1]["wallSeconds"] - live[0]["wallSeconds"]
        shader_span = live[-1]["shaderSeconds"] - live[0]["shaderSeconds"]
        check("six_second_live_window", wall_span >= 4.5 and 0.7 < shader_span / max(wall_span, 0.001) < 1.3,
              {"capturedWallSpan": wall_span, "shaderSpan": shader_span})
        previous = None
        for index, frame in enumerate(live):
            image = Image.open(args.run / frame["file"]).convert("RGB")
            pixels = np.array(image)
            if previous is not None:
                delta = np.abs(pixels.astype(np.int16) - previous.astype(np.int16))
                motion.append({"file": frame["file"], "changedPixels": int(np.any(delta != 0, axis=2).sum()),
                               "maxChannelDifference": int(delta.max())})
                gaps.append(frame["wallSeconds"] - live[index-1]["wallSeconds"])
            previous = pixels
            panel = Image.new("RGB", (960, 576), "#101416")
            panel.paste(image.resize((960, 533)), (0, 43))
            ImageDraw.Draw(panel).text((16, 15),
                f"Forge 1.20.1 | {frame['width']}x{frame['height']} | LIVE {frame['wallSeconds']-live[0]['wallSeconds']:.2f}s | shader {frame['shaderSeconds']:.3f}s", fill="white")
            panels.append(panel)
        check("continuous_interior_change", bool(motion) and all(d["changedPixels"] > 5000 for d in motion), motion)
        check("monotonic_live_uniform", all(b["shaderSeconds"] > a["shaderSeconds"] for a, b in zip(live, live[1:])),
              [f["shaderSeconds"] for f in live])
        if gaps:
            durations = [max(1, round(gap*1000)) for gap in gaps] + [max(1, round(float(np.median(gaps))*1000))]
            panels[0].save(args.run / "live-motion.webp", save_all=True, append_images=panels[1:], duration=durations, loop=0, lossless=True)
            panels[len(panels)//2].save(args.run / "live-poster.png")
            sheet = Image.new("RGB", (960, 3*576), "#101416")
            for row, index in enumerate([0, len(panels)//2, len(panels)-1]):
                sheet.paste(panels[index], (0, row*576))
            sheet.save(args.run / "live-sheet.png")
    hashes = {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in [("artifact", args.jar), ("installed", args.installed)]}
    check("deployed_jar", hashes["artifact"] == hashes["installed"], hashes)
    result = {"status": "passed" if all(c["passed"] for c in checks) else "failed", "checks": checks,
              "capture": {"liveFrames": len(live), "medianSampleGap": float(np.median(gaps)) if gaps else None,
                          "maxSampleGap": max(gaps) if gaps else None},
              "scope": "Fixed cube and camera, actual game clock, native high-resolution PNGs; WebP resized with measured sample durations.",
              "limits": ["Capture rate is not game FPS; GPU readback and background PNG encoding can affect frame pacing", "Not a seamless loop or aesthetic-quality verdict"]}
    (args.run / "validation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "checks": len(checks), "capture": result["capture"],
                      "failed": [c for c in checks if not c["passed"]]}))
    raise SystemExit(0 if result["status"] == "passed" else 1)


if __name__ == "__main__":
    main()
