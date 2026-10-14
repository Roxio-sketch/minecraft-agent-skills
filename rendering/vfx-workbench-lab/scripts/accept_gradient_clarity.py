"""Measure actual main/branch pixels for the user's high-gain gradient fixture.

The masks come from the exact frame's projected channel segments, then exclude
crossings, bright board grid, unchanged scene pixels and weak raster edges.
This is a screen-color test; it does not claim scientific lightning accuracy.
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
NAMES = ["clarity-empty", "clarity-user-additive-both31", "clarity-user-vivid-both31",
         "clarity-user-vivid-core31", "clarity-user-vivid-glow31", "clarity-user-vivid-both75",
         "clarity-user-vivid-both0", "clarity-user-vivid-both100", "clarity-rain-before-hot", "clarity-rain-after-hot"]
FIELDS = ["coreColor", "glowColor", "coreGain", "glowGain", "gradientEnabled", "gradientStart",
          "gradientEnd", "gradientRatio", "gradientTarget", "gradientCoreMode"]
COUNTS = ["generatedMainSegments", "visibleMainSegments", "visibleImpactSegments", "haloPaths", "haloBodyQuads", "haloRoundQuads"]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def font(size):
    try:
        return ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", size)
    except OSError:
        return ImageFont.load_default()


def projected_masks(projection, with_distance=False):
    height, width = projection["height"], projection["width"]
    nearest = [np.full((height, width), np.inf), np.full((height, width), np.inf)]
    along = [np.zeros((height, width)), np.zeros((height, width))]
    expected = [np.zeros((height, width, 3)), np.zeros((height, width, 3))]
    for segment in projection["segments"]:
        a, b = segment["a"], segment["b"]
        if a["clipW"] <= 0 or b["clipW"] <= 0 or abs(a["ndcZ"]) > 1 or abs(b["ndcZ"]) > 1:
            continue
        dx, dy = b["pixelX"] - a["pixelX"], b["pixelY"] - a["pixelY"]
        square = dx*dx + dy*dy
        if square < 1e-10:
            continue
        x0=max(0,int(np.floor(min(a["pixelX"],b["pixelX"])-4)))
        x1=min(width,int(np.ceil(max(a["pixelX"],b["pixelX"])+4)))
        y0=max(0,int(np.floor(min(a["pixelY"],b["pixelY"])-4)))
        y1=min(height,int(np.ceil(max(a["pixelY"],b["pixelY"])+4)))
        if x0>=x1 or y0>=y1:
            continue
        yy,xx=np.mgrid[y0:y1,x0:x1];xx,yy=xx+.5,yy+.5
        t = np.clip(((xx-a["pixelX"])*dx+(yy-a["pixelY"])*dy)/square, 0, 1)
        distance = np.hypot(xx-a["pixelX"]-t*dx, yy-a["pixelY"]-t*dy)
        layer = 0 if segment["depth"] == 0 else 1
        nearest_roi=nearest[layer][y0:y1,x0:x1];closer=distance<nearest_roi
        nearest_roi[closer] = distance[closer]
        along[layer][y0:y1,x0:x1][closer] = (segment["alongA"] + t*(segment["alongB"]-segment["alongA"]))[closer]
        first, last = np.array(segment["coreMaterialA"]), np.array(segment["coreMaterialB"])
        expected[layer][y0:y1,x0:x1][closer] = (first[None, None, :] + t[:, :, None]*(last-first)[None, None, :])[closer]
    # A narrow center region measures core. Three-pixel clearance excludes mixed paths.
    masks = [(nearest[0] < .85) & (nearest[1] > 3), (nearest[1] < .85) & (nearest[0] > 3)]
    return (masks, along, expected, nearest) if with_distance else (masks, along, expected)


def chroma(rgb):
    return (rgb.max(axis=1)-rgb.min(axis=1))/np.maximum(rgb.max(axis=1), 1)


def color_metrics(array, empty, selected, expected):
    rgb = array[selected]
    contribution = np.maximum(rgb-empty[selected], 0)
    material = expected[selected]
    cosine = np.sum(contribution*material, axis=1) / np.maximum(np.linalg.norm(contribution, axis=1)*np.linalg.norm(material, axis=1), 1e-9)
    sat = chroma(contribution)
    return {"pixels": int(selected.sum()), "meanRgb": rgb.mean(axis=0).tolist(),
            "meanContributionRgb": contribution.mean(axis=0).tolist(), "meanChroma": float(sat.mean()),
            "medianChroma": float(np.median(sat)), "neutralFractionChromaBelow012": float(np.mean(sat < .12)),
            "meanMaterialHueCosine": float(cosine.mean())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--captures", type=Path, default=ROOT/"run/lightning-lab/game-probes")
    parser.add_argument("--log", type=Path, default=ROOT/"evidence/gradient-clarity-20261006/gradient-clarity-run.log")
    parser.add_argument("--out", type=Path, default=ROOT/"evidence/gradient-clarity-20261006/gpu")
    args = parser.parse_args()
    log = args.log.read_text(encoding="utf-8-sig", errors="replace")
    require("COMPLETE: 10 high-gain captures; 7 actual HOT refresh checks" in log, "Fixture did not complete its HOT assertions")
    require("BUILD SUCCESSFUL" in log and "All dimensions are saved" in log, "Runtime did not exit successfully")
    sources = [ROOT/"src/main/java/dev/vfxworkbench/effects/lightning"/name for name in
               ["LightningDemo.java", "LightningAppearance.java", "LightningClient.java", "LightningRender.java", "LightningHalo.java"]]
    sources += [ROOT/"src/main/resources/assets/vfxworkbench/shaders/core/bolt.fsh"]
    latest = max(path.stat().st_mtime for path in sources)
    captures, images, arrays, hashes = [], [], [], []
    for index, name in enumerate(NAMES):
        png, metadata = args.captures/(name+".png"), args.captures/(name+".json")
        require(png.is_file() and metadata.is_file(), "Missing capture "+name)
        require(png.stat().st_mtime > latest and metadata.stat().st_mtime > latest, "Stale capture "+name)
        require(abs(png.stat().st_mtime-metadata.stat().st_mtime) < 5, "PNG/JSON timestamp mismatch "+name)
        require(log.count("[LIGHTNING-DEMO] capture="+name+" ") == 1, "Capture is not uniquely logged "+name)
        data = read(metadata)
        require(data["name"] == name and data["fixture"] == "gradient-clarity/v1" and data["stageIndex"] == index, "Wrong fixture "+name)
        require(data["haloGlError"] == 0, "GL error "+name)
        require(data["projection"]["pixelOrigin"] == "top-left", "Incorrect pixel coordinate convention")
        image = Image.open(png).convert("RGB")
        require(image.size == (data["projection"]["width"], data["projection"]["height"]), "Projection/screenshot size mismatch")
        require(not images or image.size == images[0].size, "Screenshot dimensions changed")
        if index > 0:
            require(data["elapsedTicks"] == 1.2 and data["visibleMainSegments"] > 0 and data["haloPaths"] > 0, "Missing fixed-peak active geometry "+name)
        if 1 <= index <= 8:
            recipe = data["recipe"]
            require(recipe["coreColor"] == "#FFFFFF" and recipe["glowColor"] == "#895A75", "User base tints were replaced "+name)
            require(recipe["gradientStart"] == "#E700F2" and recipe["gradientEnd"] == "#E3B026", "User palette was replaced "+name)
            require(recipe["coreGain"] == 3.8 and recipe["glowGain"] == 2.4, "High gain was lowered "+name)
            require(recipe["strikeWidth"] == .038 and recipe["glowWidth"] == .07 and recipe["seed"] == -1468807610, "Width or seed changed "+name)
        if index in [2, 3, 4, 5, 6, 7, 9]:
            hot = data["runtimeHotUpdate"]
            require(all(hot.get(key) is True for key in ["passed", "actualRefreshInvoked", "geometryObjectIdentical", "anchorObjectIdentical", "elapsedClockIdentical"]), "Actual active-event HOT test failed "+name)
            require(hot["beforeTime"] == hot["afterTime"] == 7.25 and hot["beforeFixedTime"] == hot["afterFixedTime"] == 1.2, "Nonzero ordinary clock or fixed display clock was reset")
            require(hot["checkedFields"] == FIELDS, "HOT contract missed an appearance field")
            require(hot["rainChildrenAppearanceChecked"] == (3 if index == 9 else 0), "Rain child appearance updates were not checked")
        captures.append(data);images.append(image);arrays.append(np.asarray(image).astype(float))
        hashes.append({"name": name, "pngSha256": sha(png), "jsonSha256": sha(metadata)})
    expected_configs = [("additive", "both", 31), ("vivid", "both", 31), ("vivid", "core", 31),
                        ("vivid", "glow", 31), ("vivid", "both", 75), ("vivid", "both", 0), ("vivid", "both", 100)]
    for index, expected in enumerate(expected_configs, 1):
        r = captures[index]["recipe"]
        require((r["gradientCoreMode"], r["gradientTarget"], r["gradientRatio"]) == expected, "Wrong target/mode/ratio "+NAMES[index])
    for index in range(2, 8):
        require(all(captures[index][key] == captures[1][key] for key in COUNTS), "HOT changed geometry/tessellation counts "+NAMES[index])
        require(captures[index]["anchor"] == captures[1]["anchor"] and captures[index]["actualCameraEye"] == captures[1]["actualCameraEye"], "HOT moved event or camera")
    require(all(captures[9][key] == captures[8][key] for key in COUNTS), "Rain HOT changed geometry/tessellation counts")

    blank, additive, vivid = arrays[:3]
    masks, along, _, nearest = projected_masks(captures[2]["projection"], with_distance=True)
    main, branch = masks
    board = blank.max(axis=2) < 55
    # The failed main-channel middle, excluding weak raster edges selected by old brightness.
    middle = main & board & (along[0] > .28) & (along[0] < .65) & ((additive-blank).max(axis=2) > 45)
    require(middle.sum() >= 40, "Too few distinct main-core pixels for high-gain comparison")
    _, _, expected_additive = projected_masks(captures[1]["projection"])
    _, _, expected_vivid = projected_masks(captures[2]["projection"])
    old = color_metrics(additive, blank, middle, expected_additive[0])
    new = color_metrics(vivid, blank, middle, expected_vivid[0])
    require(new["meanChroma"] > old["meanChroma"] + .08, "Vivid main-core color did not improve over additive washout")
    require(new["meanMaterialHueCosine"] > old["meanMaterialHueCosine"] + .006, "Main-core hue did not approach the configured material hue")
    endpoints = {}
    start_image, end_image = arrays[7], arrays[6]
    for layer, base_mask in [("main", main), ("branch", branch)]:
        changed = np.max(np.abs(start_image-end_image), axis=2) > 12
        selected = base_mask & board & changed & ((np.maximum(start_image, end_image)-blank).max(axis=2) > 20)
        require(selected.sum() >= (80 if layer == "main" else 15), "Too few separately identified "+layer+" pixels for endpoint test")
        start_rgb, end_rgb = start_image[selected], end_image[selected]
        require(float((start_rgb[:, 2]-start_rgb[:, 1]).mean()) > 12, layer+" start should remain purple")
        require(float((end_rgb[:, 1]-end_rgb[:, 2]).mean()) > 12, layer+" end should remain gold")
        endpoints[layer] = {"pixels": int(selected.sum()), "startMeanRgb": start_rgb.mean(axis=0).tolist(),
                            "endMeanRgb": end_rgb.mean(axis=0).tolist(), "changedByActualRenderedEndpointPalette": True}
    # both -> core leaves the core material unchanged. Its actual RGB change
    # belongs to halo and low-alpha branches; an opaque main center hides it.
    both_segments=captures[2]["projection"]["segments"]
    core_segments=captures[3]["projection"]["segments"]
    require(len(both_segments)==len(core_segments), "Target core changed projected geometry")
    require(all(a[key]==b[key] for a,b in zip(both_segments,core_segments)
                for key in ["depth","path","alongA","alongB","a","b","coreMaterialA","coreMaterialB"]),
            "Target core changed the shared along-gradient core materials or channel positions")
    halo_material_changes=sum(a[key]!=b[key] for a,b in zip(both_segments,core_segments)
                              for key in ["glowMaterialA","glowMaterialB"])
    require(halo_material_changes>0, "Target core did not remove gradient from the halo material")
    halo_region=(np.minimum(nearest[0],nearest[1])<2) & board
    core_target_delta=np.max(np.abs(arrays[3]-vivid),axis=2)
    halo_changed=halo_region & (core_target_delta>5)
    require(int(halo_changed.sum())>=30, "Target core changed material metadata without visible halo/weak-branch pixels")
    halo_target={"comparison":"both31 to core31", "coreMaterialSamplesIdentical":True,
                 "haloMaterialEndpointSamplesChanged":int(halo_material_changes),
                 "projectedChannelRegionRadiusPixels":2,"changedActualHaloOrWeakBranchPixels":int(halo_changed.sum()),
                 "maximumRgbDifferenceInObjectRegion":float(core_target_delta[halo_region].max()),
                 "opaqueMainCenterMayHideHaloChange":True}
    target_deltas = {}
    for index in [4, 5]:
        count = int((main & board & (np.max(np.abs(arrays[index]-vivid), axis=2) > 5)).sum())
        require(count >= 30, "Main pixels did not respond to target/ratio "+NAMES[index])
        target_deltas[NAMES[index]] = count
    require(int((np.max(np.abs(arrays[9]-arrays[8]), axis=2) > 5).sum()) > 100, "Rain HOT appearance fields changed but actual pixels did not")
    unchanged = float(np.mean(np.max(np.abs(additive-vivid), axis=2)[~(main | branch)] <= 2))
    require(unchanged > .95, "Non-channel scene changed between main-core modes")

    args.out.mkdir(parents=True, exist_ok=True);saved=args.out/"captures";saved.mkdir(exist_ok=True)
    for name in NAMES:
        for suffix in [".png", ".json"]:
            path=args.captures/(name+suffix);shutil.copy2(path,saved/path.name)
    shutil.copy2(args.log,args.out/"gradient-clarity-run.log")
    w,h=images[0].size;sheet=Image.new("RGB",(w*3,h*3+54*3),"#101722");draw=ImageDraw.Draw(sheet)
    for j,index in enumerate(range(1,10)):
        x,y=j%3*w,j//3*(h+54);draw.text((x+10,y+8),NAMES[index],fill="white",font=font(18));sheet.paste(images[index],(x,y+48))
    sheet.save(args.out/"contact-sheet.png")
    overlay=np.asarray(images[2]).copy();overlay[middle]=[255,80,120];overlay[branch & board]=[40,220,220];Image.fromarray(overlay).save(args.out/"measured-main-and-branches.png")
    report={"status":"passed","fixture":"gradient-clarity/v1","captures":10,"allGlErrorsZero":True,
            "paletteAndHighGainMatchUser":True,"mainMiddleAdditive":old,"mainMiddleVivid":new,
            "distinctMainAndBranchEndpoints":endpoints,"targetCoreOnlyChangedHalo":halo_target,
            "targetGlowAndRatioChangedMainPixels":target_deltas,
            "unchangedNonChannelSceneFraction":unchanged,"activeHotRefreshChecks":7,"rainChildrenAppearanceChecked":3,
            "captureHashes":hashes,"sourceHashes":{str(p.relative_to(ROOT)):sha(p) for p in sources},
            "limits":["Projected path masks exclude close main/branch intersections and the bright board grid.",
                      "Core and halo both contribute to final scene RGB; measurements do not claim isolated shader passes.",
                      "both -> core retains identical core material samples; only halo/weak-alpha contributions should change. This is measured within two pixels of projected channels, not required at opaque main centers.",
                      "Main-core vivid/additive comparison keeps user tint, high gain, width, seed, geometry, camera and clock.",
                      "Fixed peak pauses the event clock only for the development verifier; seven actual refresh calls preserve both runtime clock fields and active object references."]}
    (args.out/"runtime.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps({"status":"passed","mainPixels":int(middle.sum()),"mainChromaBefore":old["meanChroma"],"mainChromaAfter":new["meanChroma"],"branchPixels":endpoints["branch"]["pixels"],"hotChecks":7},ensure_ascii=False))


if __name__ == "__main__":
    main()
