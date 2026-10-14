"""Audit recorded Forge 1.20.1 outline evidence without launching the client."""
from pathlib import Path
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "evidence" / "outline-20261002"
RUN = EVIDENCE / "runtime"
data = json.loads((RUN / "results.json").read_text(encoding="utf-8-sig"))
cases = {entry["case"]: entry for entry in data["results"]}
expected = {"gui-frozen", "gui-time200", "gui-reloaded", "hand-right",
            "hand-left", "third-front", "third-back", "bow-idle",
            "bow-pulling", "ground-and-frame"}
checks = {}
checks["completed_suite"] = data["status"] == "completed" and data["suite"] == "eight-corner-outline"
checks["target_platform"] = data["minecraft"] == "1.20.1" and data["forge"] == "47.4.10" and data["world"] == "ASTRA_VFX_TEST"
checks["ten_scenarios"] = set(cases) == expected
checks["shader_reloaded"] = cases["gui-reloaded"]["generationAfter"] > cases["gui-reloaded"]["generationBefore"]
checks["draws_recorded"] = int(data["outlineDraws"].get("0", 0)) > 0
checks["bow_override"] = cases["bow-pulling"]["playerUsingItem"] and cases["bow-pulling"]["ticksUsingItem"] >= 20 and "minecraft:item/bow_pulling_2" in cases["bow-pulling"]["resolvedSprites"]
checks["sampled_gl_clean"] = all(entry["glError"] == 0 for entry in cases.values())
checks["player_restored"] = len(data["restoration"]) == 5 and all(data["restoration"].values())
for name in expected:
    png = RUN / f"{name}.png"
    header = png.read_bytes()[:24] if png.exists() else b""
    checks[f"frame_{name}"] = (len(header) == 24 and header[:8] == b"\x89PNG\r\n\x1a\n"
                               and struct.unpack(">II", header[16:24]) == (1280, 720))
artifact = ROOT / "build" / "libs" / "cosmic-material-lab-0.2.0.jar"
installed = Path("D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/mods/cosmic-material-lab-0.2.0.jar")
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None
checks["production_jar_installed"] = sha(artifact) == sha(installed) and sha(artifact) is not None
record = {"status": "passed" if all(checks.values()) else "failed", "checks": checks,
          "artifactSha256": sha(artifact), "installedSha256": sha(installed),
          "scope": "Recorded client frames, draw activity, resource reload, and test-state restoration. Visual similarity to ArcaneVortex requires human comparison."}
(EVIDENCE / "validation.json").write_text(json.dumps(record, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(json.dumps({"status": record["status"], "checks": len(checks),
                  "failed": [name for name, ok in checks.items() if not ok]}, ensure_ascii=False))
raise SystemExit(0 if all(checks.values()) else 1)
