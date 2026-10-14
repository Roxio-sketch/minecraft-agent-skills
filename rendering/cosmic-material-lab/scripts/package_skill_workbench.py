"""Package the Cosmic skill and its standalone shield preset workbench."""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[2]
SKILL = WORKSPACE / "skill" / "minecraft-cosmic-material"
WORKBENCH = WORKSPACE / "cosmic-material-lab" / "assets" / "shield-presets" / "index.html"
VALIDATOR = Path.home() / ".codex" / "skills" / ".system" / "skill-creator" / "scripts" / "quick_validate.py"
DIST = WORKSPACE / "dist"
EXCLUDED_DIRS = {"__pycache__", "skill-validation", ".git"}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def add_bytes(archive: zipfile.ZipFile, name: str, contents: bytes) -> None:
    info = zipfile.ZipInfo(name, (2026, 10, 14, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    archive.writestr(info, contents)


def skill_files() -> list[Path]:
    return sorted(
        path for path in SKILL.rglob("*")
        if path.is_file()
        and not any(part in EXCLUDED_DIRS for part in path.relative_to(SKILL).parts)
        and path.suffix != ".pyc"
    )


def verify_skill(archive_path: Path, expected: list[Path]) -> None:
    with tempfile.TemporaryDirectory(prefix="cosmic-skill-check-") as directory:
        root = Path(directory)
        with zipfile.ZipFile(archive_path) as archive:
            assert archive.testzip() is None, "Corrupt skill ZIP"
            archive.extractall(root)
            names = set(archive.namelist())
        assert names == {
            (Path(SKILL.name) / path.relative_to(SKILL)).as_posix() for path in expected
        }, "Skill ZIP contents differ from selected source files"
        extracted = root / SKILL.name
        assert digest(extracted / "assets" / "shield-presets" / "index.html") == digest(WORKBENCH)
        result = subprocess.run(
            [sys.executable, "-X", "utf8", str(VALIDATOR), str(extracted)],
            capture_output=True, text=True, encoding="utf-8", check=False,
        )
        assert result.returncode == 0, result.stdout + result.stderr


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="Package label, e.g. 2026-10-14")
    tag = parser.parse_args().tag
    assert tag and all(char.isdigit() or char == "-" for char in tag), "Invalid package tag"
    assert SKILL.joinpath("SKILL.md").is_file() and WORKBENCH.is_file()
    DIST.mkdir(exist_ok=True)
    skill_zip = DIST / f"minecraft-cosmic-material-skill-{tag}.zip"
    bench_zip = DIST / f"cosmic-shield-workbench-{tag}.zip"
    files = skill_files()
    with zipfile.ZipFile(skill_zip, "w") as archive:
        for path in files:
            add_bytes(archive, (Path(SKILL.name) / path.relative_to(SKILL)).as_posix(), path.read_bytes())
    verify_skill(skill_zip, files)

    bench_readme = (
        "Cosmic Shield 盾牌预设工作台\n"
        "直接用浏览器打开 index.html；无需网络、服务器或外部依赖。\n"
        "提供完整 Cosmic、保留金属边框、纹章、边框加纹章四种预设，\n"
        "可切换 Forge 1.20.1 实验工程支持的三套配色并复制 /give 指令。\n"
        "Canvas 是设计预览；实际游戏效果以 0.4.0 客户端运行截图为准。\n"
    ).encode("utf-8")
    with zipfile.ZipFile(bench_zip, "w") as archive:
        add_bytes(archive, "cosmic-shield-workbench/index.html", WORKBENCH.read_bytes())
        add_bytes(archive, "cosmic-shield-workbench/README.txt", bench_readme)
    with zipfile.ZipFile(bench_zip) as archive:
        assert archive.testzip() is None, "Corrupt workbench ZIP"
        assert set(archive.namelist()) == {
            "cosmic-shield-workbench/index.html", "cosmic-shield-workbench/README.txt"
        }
        assert hashlib.sha256(archive.read("cosmic-shield-workbench/index.html")).hexdigest() == digest(WORKBENCH)

    manifest = {
        "tag": tag,
        "skill": {"file": skill_zip.name, "sha256": digest(skill_zip), "bytes": skill_zip.stat().st_size,
                  "entries": len(files), "validated": True},
        "workbench": {"file": bench_zip.name, "sha256": digest(bench_zip), "bytes": bench_zip.stat().st_size,
                      "entries": 2, "html_sha256": digest(WORKBENCH), "validated": True},
        "excluded": ["__pycache__", "*.pyc", "skill-validation", ".git"],
    }
    (DIST / f"cosmic-packages-{tag}.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
