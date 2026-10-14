"""Assemble review GIFs only from the fixed-time WebGL PNG captures."""
import hashlib
import json
from pathlib import Path

from PIL import Image

LAB = Path(__file__).resolve().parents[1]
OUT = LAB / "evidence/water-sheets-iteration"
report = json.loads((OUT / "report.json").read_text(encoding="utf-8"))
assert report["ok"]
assert report["previewSha256"] == hashlib.sha256((LAB / "workbench/vortex/preview.js").read_bytes()).hexdigest()
artifacts = []
for folder, filename, expected in (
    ("motion-1", "mode-1-motion.gif", 24),
    ("motion-2", "mode-2-motion.gif", 24),
    ("surface-flow-2", "bowl-water9-near.gif", 30),
    ("bowl-mouth-9", "bowl-mouth9.gif", 24),
):
    paths = sorted((OUT / folder).glob("*.png"))
    assert len(paths) == expected, (folder, len(paths))
    frames = [Image.open(path).convert("RGB") for path in paths]
    frames[0].save(OUT / filename, save_all=True, append_images=frames[1:], duration=100, loop=0, optimize=False)
    artifacts.append({
        "file": filename,
        "sha256": hashlib.sha256((OUT / filename).read_bytes()).hexdigest(),
        "fps": 10,
        "frames": [{"file": str(path.relative_to(OUT)).replace("\\", "/"), "time": 20 + i / 10,
                    "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for i, path in enumerate(paths)],
    })
manifest = {"coreSha256": report["coreSha256"], "previewSha256": report["previewSha256"],
            "scope": "GIFs assembled from actual WebGL fixed-time PNGs; GIF palette quantization is separate from source PNG replay checks",
            "artifacts": artifacts}
(OUT / "animation-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"ok": True, "animations": [{"file": a["file"], "frames": len(a["frames"])} for a in artifacts]}))
