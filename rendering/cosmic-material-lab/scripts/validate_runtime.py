"""Verify saved live-run evidence and image changes; does not launch Minecraft."""
from pathlib import Path
import hashlib
import json
import sys
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "evidence" / "20260928-235201"
data = json.loads((RUN / "results.json").read_text(encoding="utf-8-sig"))
deployment = json.loads((ROOT / "evidence/deployment-final.json").read_text(encoding="utf-8-sig"))
checks = []

def check(name, passed, detail):
    checks.append(dict(name=name, passed=bool(passed), detail=detail))

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def changed(a, b):
    return sum(pixel != (0, 0, 0) for pixel in ImageChops.difference(a, b).getdata())

cases = {case["case"]: case for case in data["results"]}
check("completed_live_run", data["status"] == "completed" and len(cases) == 10,
      {"status": data["status"], "count": len(cases)})
check("target_platform", data["minecraft"] == "1.20.1" and data["forge"] == "47.4.10",
      {key: data[key] for key in ["minecraft", "forge", "gpu", "glVersion"]})
check("sampled_gl_errors", all(case["glError"] == 0 for case in cases.values()),
      {name: case["glError"] for name, case in cases.items()})
reload = cases["gui-reloaded"]
check("shader_recreated", reload["generationAfter"] > reload["generationBefore"],
      {"before": reload["generationBefore"], "after": reload["generationAfter"]})
bow = cases["bow-pulling"]
check("bow_full_draw", bow["playerUsingItem"] and bow["ticksUsingItem"] >= 20
      and "minecraft:item/bow_pulling_2" in bow["resolvedSprites"],
      {"ticks": bow["ticksUsingItem"], "sprites": bow["resolvedSprites"]})
check("bow_idle_override", cases["bow-idle"]["resolvedSprites"] == ["minecraft:item/bow"],
      cases["bow-idle"]["resolvedSprites"])
contexts = ["GUI", "GROUND", "FIXED", "FIRST_PERSON_LEFT_HAND",
            "FIRST_PERSON_RIGHT_HAND", "THIRD_PERSON_RIGHT_HAND"]
check("material_context_draws", all(data["contexts"].get(k, 0) > 0 for k in contexts),
      {k: data["contexts"].get(k, 0) for k in contexts})
check("test_state_restoration", len(data["restoration"]) == 5 and all(data["restoration"].values()),
      data["restoration"])
images = {}
for name, case in cases.items():
    path = RUN / (name + ".png")
    with Image.open(path) as source:
        source.load()
        images[name] = source.convert("RGB")
    check("frame:" + name, images[name].size == (1280, 720),
          {"size": list(images[name].size), "sha256": digest(path)})

# Exact captures of the same GUI: labels, cell backgrounds and vanilla controls
# should remain static while Cosmic pixels change. This is not an FPS benchmark.
a, b = images["gui-frozen"], images["gui-time200"]
regions = {
    "vanilla_sword": (60, 58, 260, 242),
    "cosmic_sword": (380, 58, 580, 242),
    "vanilla_cube": (60, 272, 260, 456),
    "cosmic_cube": (380, 272, 580, 456),
    "background": (6, 56, 56, 240),
}
differences = {name: changed(a.crop(box), b.crop(box)) for name, box in regions.items()}
check("cosmic_time_changes_with_static_controls",
      differences["cosmic_sword"] > 500 and differences["cosmic_cube"] > 500
      and differences["vanilla_sword"] == differences["vanilla_cube"] == differences["background"] == 0,
      differences)

# Source and installed production JAR must be exactly the tested deployment.
jar_hashes = {key: digest(deployment[key]) for key in ["artifact", "installed"]}
check("production_deployment_identity",
      len(set(jar_hashes.values())) == 1 and jar_hashes["artifact"] == deployment["sha256"].lower(),
      jar_hashes)

# A labeled collage is only a presentation aid; raw screenshots remain intact.
order = ["gui-frozen", "hand-right", "hand-left", "bow-pulling", "third-front", "ground-and-frame"]
sheet = Image.new("RGB", (1280, 1152), "#10151e")
draw = ImageDraw.Draw(sheet)
for i, name in enumerate(order):
    x, y = (i % 2) * 640, (i // 2) * 384
    sheet.paste(images[name].resize((640, 360), Image.Resampling.LANCZOS), (x, y + 24))
    draw.text((x + 8, y + 5), name, fill="#e4eeff")
sheet.save(RUN / "contact-sheet.png")

record = {
    "status": "passed" if all(item["passed"] for item in checks) else "failed",
    "run": RUN.name,
    "scope": "Recorded Forge client integration evidence, selected pixels and deployment hashes; not exhaustive compatibility.",
    "checks": checks,
    "limits": ["No BEWLR, worn armor or skeletal adapter test", "No third-person left-hand capture",
               "No intermediate bow stage capture", "No Oculus/Iris shaderpack test",
               "No performance benchmark", "No byte-identical whole-save restoration claim"],
}
path = ROOT / "evidence/runtime-validation.json"
path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"status": record["status"], "checks": len(checks), "differences": differences,
                  "failed": [item["name"] for item in checks if not item["passed"]]}, ensure_ascii=False))
sys.exit(0 if record["status"] == "passed" else 1)
