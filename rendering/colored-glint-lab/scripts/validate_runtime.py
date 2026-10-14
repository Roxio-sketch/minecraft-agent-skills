#!/usr/bin/env python3
"""Check real game captures and recorded state; never launches Minecraft."""

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageStat


CASES = ["gui-baseline", "gui-colors-a", "gui-colors-b", "gui-reloaded-once", "gui-reloaded-twice",
         "hand-red", "hand-blue", "hand-left-green", "third-front-purple", "bow-idle", "bow-pulling", "ground-and-frame"]


def changed(diff):
    channels = diff.split()
    maximum = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2])
    return sum(maximum.histogram()[3:])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path, help="Directory containing results.json and twelve raw PNGs")
    parser.add_argument("--deployment", type=Path)
    args = parser.parse_args()
    data = json.loads((args.run / "results.json").read_text(encoding="utf-8-sig"))
    gradients = data.get("suite") == "custom-gradients"
    checks = []
    def require(name, condition, detail=None):
        checks.append({"check": name, "passed": bool(condition), "detail": detail})

    require("audit_completed", data["status"] == "completed")
    records = data["results"]
    expected = CASES if not gradients else CASES[:5] + ["hand-rose-gold", "hand-rainbow", "hand-left-aurora",
        "third-front-ember"] + CASES[9:] + ["paused-a", "paused-b", "resumed"]
    require("expected_scenarios", [r["case"] for r in records] == expected)
    require("gl_error_sample_points", all(r["glError"] == 0 for r in records), [r["glError"] for r in records])
    require("five_restoration_checks", all(data["restoration"].get(k) is True for k in
            ["inventory", "selectedSlot", "gameMode", "position", "testEntitiesRemoved"]), data["restoration"])
    for r in records:
        image = args.run / (r["case"] + ".png")
        with Image.open(image) as im:
            require("capture_" + r["case"], im.size == (r["width"], r["height"]) == (1280, 720))
    by_case = {r["case"]: r for r in records}
    for name in ["gui-reloaded-once", "gui-reloaded-twice"]:
        r = by_case[name]
        require(name + "_shader_replaced", r["generationBefore"] != r["generationAfter"])
        if gradients:
            for shader in ("rainbow_cycle", "ember_wave"):
                require(name + "_" + shader + "_replaced", shader in r["animatedShadersBefore"] and
                        r["animatedShadersBefore"][shader] != r["animatedShadersAfter"].get(shader))
    bow = by_case["bow-pulling"]
    require("full_bow_override", bow["playerUsingItem"] and bow["ticksUsingItem"] >= 20 and
            "minecraft:item/bow_pulling_2" in bow["resolvedSprites"], bow["ticksUsingItem"])
    contexts = ["GUI/red", "GUI/blue", "GUI/purple", "GUI/green", "GUI/aqua",
                    "FIRST_PERSON_LEFT_HAND/green", "FIRST_PERSON_RIGHT_HAND/red",
                    "FIRST_PERSON_RIGHT_HAND/blue", "THIRD_PERSON_RIGHT_HAND/purple", "FIXED/green", "GROUND/red", "GROUND/blue"]
    mapping = {"red": "rose_gold", "blue": "rainbow_cycle", "purple": "ember_wave", "green": "aurora", "aqua": "rainbow_cycle"}
    if gradients:
        contexts = [prefix + "/" + mapping[color] for prefix, color in (c.split("/") for c in contexts)]
    for context in contexts:
        require("render_context_" + context, data["contexts"].get(context, 0) > 0)
    if gradients:
        for shader, period in (("rainbow_cycle", 6), ("ember_wave", 4)):
            phases = [s["seconds"][shader] for s in data["animationTrace"] if shader in s["seconds"]]
            require(shader + "_full_cycle", len(phases) >= 12 and min(phases) < period * .2 and max(phases) > period * .8
                    and any(a - b > period * .5 for a, b in zip(phases, phases[1:])), phases)
            a, b = by_case["paused-a"], by_case["paused-b"]
            require(shader + "_pause_holds", a["paused"] and b["paused"] and
                    a["animatedSeconds"][shader] == b["animatedSeconds"][shader])
            require(shader + "_resumes", not by_case["resumed"]["paused"] and
                    b["animatedSeconds"][shader] != by_case["resumed"]["animatedSeconds"][shader])

    def cell(image, index):
        x = (index % 4) * 320 + 68
        y = (index // 4) * 214 + 58
        return image.crop((x, y, x + 184, y + 184))

    baseline = Image.open(args.run / "gui-baseline.png").convert("RGB")
    first = Image.open(args.run / "gui-colors-a.png").convert("RGB")
    second = Image.open(args.run / "gui-colors-b.png").convert("RGB")
    measurements = []
    for index, name in [(0, "red"), (1, "blue"), (2, "purple"), (3, "green"), (4, "aqua_forced"),
                        (5, "vanilla_foil"), (6, "unknown_fallback"), (7, "color_without_foil"), (10, "red_bow"), (11, "plain")]:
        if gradients:
            name = mapping.get(name, {"aqua_forced": "rainbow_forced", "red_bow": "rainbow_bow"}.get(name, name))
        added = ImageChops.difference(cell(baseline, index), cell(first, index))
        motion = ImageChops.difference(cell(first, index), cell(second, index))
        mean = ImageStat.Stat(added).mean
        measurement = {"name": name, "changed_from_baseline": changed(added), "changed_over_time": changed(motion),
                       "mean_rgb_absolute_difference": [round(v, 4) for v in mean]}
        measurements.append(measurement)
        if index in (7, 11):
            require(name + "_unchanged", added.getbbox() is None and motion.getbbox() is None, measurement)
        else:
            require(name + "_visible_and_moving", changed(added) > 1000 and changed(motion) > 500, measurement)
        if not gradients:
            if index == 0: require("red_channel_isolated", mean[0] > 10 and mean[1] < 1 and mean[2] < 1)
            if index == 1: require("blue_channel_dominant", mean[2] > 10 * max(mean[0], mean[1], 0.1))
            if index == 2: require("purple_red_blue_channels", mean[0] > 5 and mean[2] > 10 and mean[1] < 1)
            if index == 3: require("green_channel_dominant", mean[1] > 4 * max(mean[0], mean[2], 0.1))
    if gradients:
        for left in range(4):
            for right in range(left + 1, 4):
                require(f"distinct_recipes_{left}_{right}", changed(ImageChops.difference(cell(first, left), cell(first, right))) > 500)
    fallback_diff = ImageChops.difference(cell(first, 5), cell(first, 6))
    # Use the same >2 threshold as other comparisons. Separate screen positions
    # can differ by 1-2 byte levels in glint sampling even with identical state.
    require("unknown_matches_vanilla_foil", changed(fallback_diff) == 0,
            {"rgb_tolerance": 2, "rgb_extrema": fallback_diff.getextrema(), "pixels_exceeding_tolerance": changed(fallback_diff)})
    for name in ["gui-reloaded-once", "gui-reloaded-twice"]:
        image = Image.open(args.run / (name + ".png")).convert("RGB")
        for index in range(5):
            require(name + "_visible_" + str(index), changed(ImageChops.difference(cell(baseline, index), cell(image, index))) > 1000)
    if args.deployment:
        deployment = json.loads(args.deployment.read_text(encoding="utf-8-sig"))
        for kind in ["jar", "installed"]:
            actual = hashlib.sha256(Path(deployment[kind]).read_bytes()).hexdigest()
            require(kind + "_sha256", actual == deployment["sha256"].lower())
    result = {"passed": all(c["passed"] for c in checks), "checks": checks, "pixel_measurements": measurements,
              "scope": "Saved evidence verification plus separate human image inspection; no new game launch or full-driver guarantee."}
    (args.run / "validation.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": result["passed"], "checks": len(checks), "failed": [c for c in checks if not c["passed"]]}, ensure_ascii=False))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
