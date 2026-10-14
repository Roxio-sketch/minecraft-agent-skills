"""Validate saved Forge fantasy evidence; does not start a Minecraft client."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops

parser = argparse.ArgumentParser()
parser.add_argument("run", type=Path)
parser.add_argument("--jar", required=True, type=Path)
parser.add_argument("--installed", required=True, type=Path)
args = parser.parse_args()
data = json.loads((args.run / "results.json").read_text(encoding="utf-8-sig"))
cases = {case["case"]: case for case in data["results"]}
checks = []

def check(name, passed, detail):
    checks.append({"name": name, "passed": bool(passed), "detail": detail})

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

check("completed_fantasy_run", data.get("suite") == "fantasy-materials" and
      data["status"] == "completed" and len(cases) == 10, {"status": data["status"], "cases": len(cases)})
check("target_platform", data["minecraft"] == "1.20.1" and data["forge"] == "47.4.10",
      {key: data[key] for key in ["minecraft", "forge", "gpu", "glVersion"]})
check("sampled_gl_errors", all(case["glError"] == 0 for case in cases.values()),
      {name: case["glError"] for name, case in cases.items()})
check("resource_reload", cases["gui-reloaded"]["generationAfter"] > cases["gui-reloaded"]["generationBefore"],
      cases["gui-reloaded"]["generationAfter"])
ids = ["rune-vault", "liquid-mithril", "jade-veins", "gilded-porcelain", "iridescent-silk", "folded-ember"]
focus = data.get("focusMaterial")
if focus:
    ids = [focus]
counts = {material: sum(count for key, count in data["draws"].items() if key.endswith("/" + material)) for material in ids}
check("selected_draw_parameters", all(count > 0 for count in counts.values()), counts)
if focus:
    check("focused_material_in_every_case", all(case.get("material") == focus for case in cases.values()), focus)
check("player_state_restored", len(data["restoration"]) == 5 and all(data["restoration"].values()), data["restoration"])
check("full_bow_override", cases["bow-pulling"]["ticksUsingItem"] >= 20 and
      "minecraft:item/bow_pulling_2" in cases["bow-pulling"]["resolvedSprites"], cases["bow-pulling"]["resolvedSprites"])
images = {}
for name, case in cases.items():
    path = args.run / (name + ".png")
    images[name] = Image.open(path).convert("RGB")
    expected = (case["width"], case["height"])
    check("frame:" + name, images[name].size == expected and expected[0] >= 1280 and expected[1] >= 720,
          {"size": images[name].size, "recordedFramebuffer": expected, "sha256": sha(path)})

time_differences = {}
reload_differences = {}
for i, material in enumerate(ids if not focus else [focus + ":" + str(i) for i in range(6)]):
    # FantasyShowcase uses GUI scale 2 and lays out cells from the actual GUI
    # dimensions. Fullscreen can ignore glfwSetWindowSize(1280, 720).
    width, height = images["gui-frozen"].size
    cell_w, cell_h = (width // 2 // 3) * 2, ((height // 2 - 30) // 2) * 2
    x, y = (i % 3) * cell_w, 56 + (i // 3) * cell_h
    size = int(min(cell_w * .52, cell_h - 68))
    region = (x + 12, y + 14, x + 12 + size, y + 14 + size)
    a = images["gui-frozen"].crop(region)
    b = images["gui-time200"].crop(region)
    c = images["gui-reloaded"].crop(region)
    time_differences[material] = sum(p != (0, 0, 0) for p in ImageChops.difference(a,b).getdata())
    reload_differences[material] = sum(p != (0, 0, 0) for p in ImageChops.difference(a,c).getdata())
check("animated_material_samples", all(n > 100 for n in time_differences.values()), time_differences)
# Record exact repeated-frame changes without silently accepting a channel tolerance.
check("same_time_after_reload", all(n == 0 for n in reload_differences.values()), reload_differences)
hashes = {"artifact": sha(args.jar), "installed": sha(args.installed)}
check("deployed_production_jar", hashes["artifact"] == hashes["installed"], hashes)
record = {"status": "passed" if all(c["passed"] for c in checks) else "failed", "checks": checks,
          "scope": (focus or "Six original kernels") + " on selected vanilla baked models in Forge 1.20.1; modes 6-12 use shared screen-ray fields, modes 0-5 use primary-sprite apertures.",
          "limits": ["No multi-sprite adapter certification", "No BEWLR/worn armor/skeletal or shaderpack certification",
                     "No performance benchmark", "Native glint is animated independently of frozen material time"]}
(args.run / "validation.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"status": record["status"], "checks": len(checks),
                  "failed": [c["name"] for c in checks if not c["passed"]],
                  "timeDifferences": time_differences, "reloadDifferences": reload_differences}, ensure_ascii=False))
raise SystemExit(0 if record["status"] == "passed" else 1)
