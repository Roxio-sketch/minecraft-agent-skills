"""Validate the saved Forge 1.20.1 Cosmic armor/shield acceptance run."""
from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "evidence" / "equipment-20261002" / "runtime"
JAR = ROOT / "build" / "libs" / "cosmic-material-lab-0.4.0.jar"
INSTALLED = Path("D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/mods/cosmic-material-lab-0.4.0.jar")
EXPECTED = (
    "equipment-gui", "armor-back", "armor-front", "shield-hand",
    "shield-blocking", "shield-patterned", "shield-framed",
    "shield-framed-patterned", "equipment-reloaded",
)


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as image:
        header = image.read(24)
    assert header[:8] == b"\x89PNG\r\n\x1a\n" and header[12:16] == b"IHDR", path
    return struct.unpack(">II", header[16:24])


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    data = json.loads((RUN / "results.json").read_text(encoding="utf-8"))
    checks: dict[str, bool] = {}
    checks["completed"] = data["status"] == "completed"
    checks["identity"] = (data["minecraft"], data["forge"], data["world"]) == (
        "1.20.1", "47.4.10", "ASTRA_VFX_TEST"
    )
    frames = data["frames"]
    checks["nine_cases"] = tuple(frame["case"] for frame in frames) == EXPECTED
    checks["png_dimensions"] = all(
        png_size(RUN / (frame["case"] + ".png")) == (frame["width"], frame["height"])
        for frame in frames
    )
    checks["gl_error_zero"] = all(frame["glError"] == 0 for frame in frames)
    by_case = {frame["case"]: frame for frame in frames}
    for case in ("armor-back", "armor-front"):
        for slot in ("HEAD", "CHEST", "LEGS", "FEET"):
            checks[f"{case}_{slot}"] = any(
                key.startswith(f"armor_{slot}/NONE/") and count > 0
                for key, count in by_case[case]["draws"].items()
            )
    for case in ("shield-hand", "shield-blocking", "shield-patterned"):
        checks[case + "_draw"] = any(
            key.startswith("shield/FIRST_PERSON_LEFT_HAND/") and count > 0
            for key, count in by_case[case]["draws"].items()
        )
    for case in ("shield-framed", "shield-framed-patterned"):
        checks[case + "_draw"] = any(
            key.startswith("shield_framed/FIRST_PERSON_LEFT_HAND/") and count > 0
            for key, count in by_case[case]["draws"].items()
        )
    checks["shield_blocking"] = (
        by_case["shield-blocking"]["usingShield"]
        and by_case["shield-blocking"]["ticksUsing"] >= 20
    )
    checks["resource_reload"] = (
        frames[0]["shaderGeneration"] == 1
        and by_case["equipment-reloaded"]["shaderGeneration"] == 2
        and data["shaderGeneration"] == 2
    )
    for field in ("inventory", "selectedSlot", "gameMode", "position"):
        checks["restored_" + field] = data["restoration"].get(field) is True
    checks["jar_installed_matches"] = JAR.is_file() and INSTALLED.is_file() and sha(JAR) == sha(INSTALLED)
    report = {"passed": all(checks.values()), "checks": checks, "jarSha256": sha(JAR)}
    (RUN / "runtime-validation.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(f"{sum(checks.values())}/{len(checks)} equipment runtime checks passed")
    if not report["passed"]:
        print("Failed: " + ", ".join(name for name, passed in checks.items() if not passed))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
