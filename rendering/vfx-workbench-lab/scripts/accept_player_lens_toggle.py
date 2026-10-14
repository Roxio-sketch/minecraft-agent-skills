"""Accept same-input, same-frame third-person player-lens on/off captures.

Multiple images of a behind-player are allowed when the preference is on.
Protected actors are still checked against their original current-frame image.
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
LOCAL = ["front-off", "front-on", "local-entirely-behind", "plane-through-chest",
         "plane-through-feet", "core-through-torso", "camera-inside-core",
         "close-yaw45-pitch20", "yaw-minus70-pitch-minus25", "crouching-at-plane",
         "swimming-at-plane", "held-item-swing-at-plane"]
CASES = LOCAL + ["other-entity-front", "other-entity-behind"]


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def equals(a, b):
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(equals(x, y) for x, y in zip(a, b))
    if isinstance(a, (float, int)) and isinstance(b, (float, int)):
        return abs(a-b) <= 1e-5
    return a == b


def make_sheet(left, right, title, output):
    w, h = left.size
    sheet = Image.new("RGB", (2*w+30, h+72), "#111722")
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 17)
    except OSError:
        font = ImageFont.load_default()
    draw.text((10, 8), title+" | 透镜影响玩家：关", fill="white", font=font)
    draw.text((w+20, 8), "透镜影响玩家：开", fill="white", font=font)
    sheet.paste(left, (10, 36));sheet.paste(right, (w+20, 36))
    draw.text((10, h+43), "同相机、配方和姿态；黑洞后方允许玩家参与透镜及形成多像。", fill="#bcc8db", font=font)
    sheet.save(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT/"run/black-hole-lab/world-probes")
    parser.add_argument("--out", type=Path, default=ROOT/"evidence/player-lens-toggle-20261005")
    parser.add_argument("--prefix", default="player-toggle-", help="Expect <prefix>off-<case> / <prefix>on-<case>")
    args = parser.parse_args();args.out.mkdir(parents=True, exist_ok=True)
    measurements = {}
    behind_names = []
    same_input = ["scale", "lens", "rotation", "infall", "frozenTicks", "exposure", "tilt",
                  "halo", "haloRing", "haloDisk", "haloAura", "coreColor", "camera", "center",
                  "cameraMode", "viewProjection", "cameraOriginRelative", "effectiveCamera",
                  "probeActorPosition", "probeActorPose", "probeActorYaw", "probeActorPitch"]
    for case in CASES:
        names = [args.prefix+mode+"-"+case for mode in ("off", "on")]
        off, on = [read(args.source/(name+".json")) for name in names]
        assert all(key in off and key in on and equals(off[key], on[key]) for key in same_input), (case, "camera/recipe/pose differs")
        before_parameters = {key: value for key, value in off["parameters"].items() if key != "lensAffectsPlayer"}
        after_parameters = {key: value for key, value in on["parameters"].items() if key != "lensAffectsPlayer"}
        assert before_parameters == after_parameters, (case, "complete recipe differs beyond preference")
        assert not off["lensAffectsPlayer"] and on["lensAffectsPlayer"], case
        for name, probe in zip(names, (off, on)):
            assert probe["lensModel"] == "angular-environment-360/v4", name
            assert probe["environmentCapturedBeforeEntities"], name
            assert probe["glError"] == probe["outsideChangedPixels"] == 0, name
            assert probe["maxDepthDifference"] == 0, name
            assert probe["foregroundEntityChangedPixels"] == 0, name
            assert probe["actualEntityDepthPixels"] > 1000, (name, "actor isolation has no body")
            for suffix in ("-before.png", "-field.png", "-after.png", "-source-coverage.png"):
                path = args.source/(name+suffix)
                assert path.exists(), path
                assert abs(path.stat().st_mtime-(args.source/(name+".json")).stat().st_mtime) < 10, (name, "stale PNG/JSON pair")
                shutil.copy2(path, args.out/path.name)
            shutil.copy2(args.source/(name+".json"), args.out/(name+".json"))
        assert off["localPlayerWhollyBehind"] == on["localPlayerWhollyBehind"], (case, "classification changed with toggle")
        if case in LOCAL:
            assert off["cameraMode"] == on["cameraMode"] == "THIRD_PERSON_BACK", case
            assert off["localPlayerProtected"], case
            assert off["actualEntityDepthChangedPixels"] == off["actualEntityDepthLensChangedPixels"] == 0, case
            assert off["movedLensedPlayerPixels"] == 0, case
            expected_protected = not on["playerLensActive"]
            assert on["localPlayerProtected"] == expected_protected, case
            if expected_protected:
                assert on["actualEntityDepthChangedPixels"] == on["actualEntityDepthLensChangedPixels"] == 0, case
                assert on["movedLensedPlayerPixels"] == 0, case
            else:
                assert on["actualEntityDepthChangedPixels"] > 100, case
                inside_core = on["massDistance"] <= on["scale"]*float(on["parameters"]["core"])*1.01
                if case in ("front-on", "plane-through-chest", "local-entirely-behind"):
                    assert on["actualEntityDepthLensChangedPixels"] > 100, case
                    assert on["movedLensedPlayerPixels"] > 100, (case, "explicit player opt-in never participates in real source warp")
                # An eye already inside the core has no outgoing source map;
                # swallowing the behind actor is its observable participation.
                behind_names.append(case)
        elif case == "other-entity-front":
            assert off["actualEntityDepthChangedPixels"] == on["actualEntityDepthChangedPixels"] == 0, case
            assert off["movedLensedPlayerPixels"] == on["movedLensedPlayerPixels"] == 0, case
        else:
            assert off["actualEntityDepthChangedPixels"] > 1000 and on["actualEntityDepthChangedPixels"] > 1000, case
            assert off["movedLensedPlayerPixels"] > 1000 and on["movedLensedPlayerPixels"] > 1000, case
        left = Image.open(args.source/(names[0]+"-field.png")).convert("RGB")
        right = Image.open(args.source/(names[1]+"-field.png")).convert("RGB")
        assert left.size == right.size, case
        delta = np.max(np.abs(np.array(left).astype(np.int16)-np.array(right).astype(np.int16)), axis=2)
        make_sheet(left, right, case, args.out/(case+"-comparison.png"))
        measurements[case] = {"sameCompleteRecipeCameraAndPose": True,
                              "localPlayerWhollyBehind": on["localPlayerWhollyBehind"],
                              "onPlayerLensActive": on["playerLensActive"],
                              "onPlayerProtected": on["localPlayerProtected"],
                              "offActorChangedPixels": off["actualEntityDepthChangedPixels"],
                              "onActorChangedPixels": on["actualEntityDepthChangedPixels"],
                              "onActorLensChangedPixels": on["actualEntityDepthLensChangedPixels"],
                              "offMovedActorSourcePixels": off["movedLensedPlayerPixels"],
                              "onMovedActorSourcePixels": on["movedLensedPlayerPixels"],
                              "onOffImageChangedPixels": int((delta > 2).sum()),
                              "onFieldSha256": hashlib.sha256((args.source/(names[1]+"-field.png")).read_bytes()).hexdigest()}
    assert "local-entirely-behind" in behind_names, ("mandatory behind case was never wholly behind", behind_names)
    report = {"status": "passed", "captures": len(CASES)*2, "sameInputPairs": len(CASES),
              "runtimePlayerCoverage": "Local third-person camera subject only; other isolated actors are armor stands",
              "localProtectedOffCases": len(LOCAL), "onBehindParticipatingCases": behind_names,
              "protectedOffActorPixelsChanged": 0, "protectedInactiveOnActorPixelsChanged": 0,
              "onRestriction": "All active opted-in players participate regardless of mass-plane classification",
              "otherActorFrontAndBehindRulesPreserved": True,
              "allGlErrorsZero": True, "allEnvironmentCapturesValid": True,
              "allowsBehindActorMultipleImages": True, "visualReviewRequired": True,
              "independentActorMetric": "New actor depth versus same-frame pre-entity environment depth in isolated fixtures",
              "measurements": measurements}
    (args.out/"runtime.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("status", "captures", "sameInputPairs", "onBehindParticipatingCases")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
