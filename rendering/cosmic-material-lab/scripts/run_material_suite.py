"""Run explicitly selected materials through the ASTRA_VFX_TEST file bridge.

Requires a running matching lab client and a prior save backup. Leaves failure
evidence intact and stops dispatching on any failed run or verification.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-dir", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--materials", required=True)
    parser.add_argument("--phase", choices=["runtime", "continuity", "motion"], required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    root = args.game_dir / "cosmic-material-lab"
    specs = {
        "runtime": ("material-run", "fantasy-runs", Path(__file__).with_name("validate_fantasy_runtime.py")),
        "continuity": ("material-continuity-hd", "continuity-runs", Path(__file__).parents[2] / "skill/minecraft-cosmic-material/scripts/validate_flow_continuity.py"),
        "motion": ("material-motion-hd", "motion-runs", Path(__file__).parents[2] / "skill/minecraft-cosmic-material/scripts/validate_flow_motion.py"),
    }
    action, directory, validator = specs[args.phase]
    installed = args.game_dir / "mods" / args.jar.name
    records = []
    args.out.parent.mkdir(parents=True, exist_ok=True)
    for material in args.materials.split(","):
        request = root / "request.json"
        if request.exists():
            raise RuntimeError("An existing request is pending; refusing to overwrite it.")
        existing = set((root / directory).glob("*"))
        pending = root / "new-material-request.tmp"
        pending.write_text(json.dumps({"action": action, "material": material}), encoding="utf-8")
        pending.replace(request)
        print(f"Started {args.phase}: {material}", flush=True)
        deadline = time.monotonic() + 240
        run = None
        while time.monotonic() < deadline:
            new = sorted(set((root / directory).glob("*")) - existing)
            for candidate in new:
                try:
                    data = json.loads((candidate / "results.json").read_text(encoding="utf-8-sig"))
                except (OSError, json.JSONDecodeError):
                    continue
                if data.get("focusMaterial", data.get("material")) != material:
                    continue
                if data["status"] == "failed":
                    raise RuntimeError(f"{material} {args.phase} failed: {candidate}")
                if data["status"] == "completed":
                    run = candidate
                    break
            if run:
                break
            time.sleep(1)
        if run is None:
            raise TimeoutError(f"{material} {args.phase} did not complete; no further requests sent.")
        result = subprocess.run([sys.executable, str(validator), str(run), "--jar", str(args.jar),
                                 "--installed", str(installed)], capture_output=True, text=True,
                                encoding="utf-8", env={**os.environ, "PYTHONUTF8": "1"}, timeout=240)
        print(result.stdout, flush=True)
        report = json.loads((run / "validation.json").read_text(encoding="utf-8"))
        records.append({"material": material, "run": str(run), "status": report["status"]})
        args.out.write_text(json.dumps({"phase": args.phase, "records": records}, indent=2), encoding="utf-8")
        if result.returncode or report["status"] != "passed":
            if result.stderr:
                print(result.stderr, file=sys.stderr)
            raise RuntimeError(f"{material} {args.phase} verification failed; remaining requests cancelled.")
    print(json.dumps({"status": "passed", "phase": args.phase, "materials": len(records)}), flush=True)


if __name__ == "__main__":
    main()
