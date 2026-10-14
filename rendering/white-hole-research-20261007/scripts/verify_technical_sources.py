"""Read-only, bounded verification of public white-hole VFX references.

Run with Python 3. No repository clones, packages, assets or images are fetched.
Pinned raw files are read in memory; only bounded excerpts and checksums are saved.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import html
import io
import json
from pathlib import Path
import re
import urllib.request

OUT = Path(__file__).resolve().parents[1]
SNAP = OUT / "snapshots"
SNAP.mkdir(parents=True, exist_ok=True)
DATE = "2026-10-07 (Asia/Shanghai)"
COMMITS = {
    "stegu/psrdnoise": "419175a270862ce7ae692038fafafb42ec0427e9",
    "stegu/webgl-noise": "22434e04d7753f7e949e8d724ab3da2864c17a0f",
    "mrdoob/three.js": "f27bb5ae1ede1309841e6fc9586cce768a99f9ad",
    "pmndrs/postprocessing": "9cf03cff26615636a564d8bfddf765edb8917441",
    "ebruneton/black_hole_shader": "e72b3f293409893a6fa25528b29572c96fc57f57",
    "mmp/pbrt-v4": "b4ce9687e6c695f5582997c61b0c66cf064bdb4a",
    "keijiro/KinoBloom": "0a04c43fa47de12bb52edb325c8d3c0125d0b4ca",
}

def raw(repo, path):
    return f"https://raw.githubusercontent.com/{repo}/{COMMITS[repo]}/{path}"

# None means retain a complete short license. Other values select nearby source
# lines, capped at 120 lines per file, rather than redistributing complete files.
FILES = [
    ("psrd-readme", raw("stegu/psrdnoise", "README.md"), ["## LICENSE", "Tiling simplex"]),
    ("psrd3", raw("stegu/psrdnoise", "src/psrdnoise3.glsl"), ["Copyright", "with rotating gradients", "float psrdnoise(", "gradient ="]),
    ("noise-license", raw("stegu/webgl-noise", "LICENSE"), None),
    ("noise3", raw("stegu/webgl-noise", "src/noise3D.glsl"), ["float snoise("]),
    ("three-license", raw("mrdoob/three.js", "LICENSE"), None),
    ("three-lines", raw("mrdoob/three.js", "examples/jsm/lines/LineMaterial.js"), ["worldUnits", "worldPos.xyz +=", "clip.xy += offset", "lineDir", "#include <tonemapping_fragment>"]),
    ("three-bloom", raw("mrdoob/three.js", "examples/jsm/postprocessing/UnrealBloomPass.js"), ["HalfFloatType", "nMips", "_getSeparableBlurMaterial", "bloomRadius", "luminosityThreshold"]),
    ("three-tonemap", raw("mrdoob/three.js", "src/renderers/shaders/ShaderChunk/tonemapping_pars_fragment.glsl.js"), ["vec3 ReinhardToneMapping", "vec3 ACESFilmicToneMapping", "vec3 AgXToneMapping", "vec3 NeutralToneMapping"]),
    ("post-license", raw("pmndrs/postprocessing", "LICENSE.md"), None),
    ("shock-wave", raw("pmndrs/postprocessing", "src/effects/glsl/shock-wave.frag"), ["uniform", "void mainUv", "distance > radius"]),
    ("shock-effect", raw("pmndrs/postprocessing", "src/effects/ShockWaveEffect.js"), ["class ShockWaveEffect", "Calculate the screen position", "Update the shock wave radius"]),
    ("pbrt-license", raw("mmp/pbrt-v4", "LICENSE.txt"), None),
    ("pbrt-media", raw("mmp/pbrt-v4", "src/pbrt/media.h"), ["class HomogeneousMedium", "class GridMedium", "Compute grid emission", "T_maj *= FastExp"]),
    ("black-license", raw("ebruneton/black_hole_shader", "LICENSE"), None),
    ("black-readme", raw("ebruneton/black_hole_shader", "README.md"), ["WebGL2", "BSD"]),
    ("black-functions", raw("ebruneton/black_hole_shader", "black_hole/functions.glsl"), ["precomputed textures", "GetRayDeflectionTextureUFromEsquare", "Angle RayTrace("]),
    ("black-model", raw("ebruneton/black_hole_shader", "black_hole/model.glsl"), ["SceneColor", "DiscColor", "DefaultDoppler"]),
    ("kino-license", raw("keijiro/KinoBloom", "LICENSE.md"), None),
    ("kino-readme", raw("keijiro/KinoBloom", "README.md"), ["Soft Knee", "Anti Flicker", "Unity 5.5"]),
    ("kino-cs", raw("keijiro/KinoBloom", "Assets/Kino/Bloom/Bloom.cs"), ["RenderTextureFormat.DefaultHDR", "var knee", "thresholdLinear", "SetFloat(\"_Intensity\""]),
    ("kino-shader", raw("keijiro/KinoBloom", "Assets/Kino/Bloom/Shader/Bloom.shader"), ["Prefilter", "Downsampler", "Upsampler", "frag_upsample_final"]),
    ("kino-filter", raw("keijiro/KinoBloom", "Assets/Kino/Bloom/Shader/Bloom.cginc"), ["half Brightness", "SafeHDR", "half4 frag_prefilter", "m *= max", "half4 frag_upsample_final"]),
]

def fetch(url, limit=250000):
    req = urllib.request.Request(url, headers={"User-Agent": "white-hole-research/1.0"})
    with urllib.request.urlopen(req, timeout=30) as response:
        data = response.read(limit + 1)
        if len(data) > limit:
            raise ValueError(f"source exceeds bounded read limit {limit}: {url}")
        return data, response.status, response.headers.get("Content-Type", "")

def snapshot(item):
    key, url, needles = item
    try:
        data, status, mime = fetch(url)
        text = data.decode("utf-8", "replace")
        lines = text.splitlines()
        if needles is None:
            excerpt = text
        else:
            indexes = set()
            for needle in needles:
                matches = [n for n, line in enumerate(lines) if needle in line][:3]
                for n in matches:
                    indexes.update(range(max(0, n-3), min(len(lines), n+9)))
            indexes = sorted(indexes)[:120]
            excerpt = "\n".join(f"{n+1}: {lines[n]}" for n in indexes)
        path = SNAP / f"technical-{key}.txt"
        path.write_text(f"URL: {url}\nVerified: {DATE}\nHTTP: {status}\nBytes read: {len(data)}\nSHA256: {hashlib.sha256(data).hexdigest()}\nScope: {'complete license' if needles is None else 'bounded source/readme excerpts'}\n\n{excerpt}\n", encoding="utf-8")
        return key, {"url": url, "httpStatus": status, "bytesRead": len(data), "sha256": hashlib.sha256(data).hexdigest(), "snapshot": path.relative_to(OUT).as_posix(), "readScope": "complete license" if needles is None else "bounded source/readme excerpts", "totalLines": len(lines)}
    except Exception as exc:
        return key, {"url": url, "error": str(exc), "verificationStatus": "failed"}

records = dict(concurrent.futures.ThreadPoolExecutor(max_workers=6).map(snapshot, FILES))

def plain_html(data):
    text = data.decode("utf-8", "replace")
    text = re.sub(r"<(script|style)\b[^>]*>.*?</\1>", "", text, flags=re.S|re.I)
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", text)))

pages = {
    "bridson-author": ("https://www.cs.ubc.ca/~rbridson/", "Curl noise for procedural fluid flow", 500),
    "iq-warp": ("https://iquilezles.org/articles/warp/", "Warping, or domain distortion", 480),
    "pbrt-transmittance": ("https://pbr-book.org/4ed/Volume_Scattering/Transmittance", "transmittance is always", 400),
    "pbrt-scattering": ("https://pbr-book.org/4ed/Volume_Scattering/Volume_Scattering_Processes", "There are three main physical", 360),
}
for key, (url, needle, length) in pages.items():
    try:
        data, status, mime = fetch(url, 750000)
        plain = plain_html(data)
        start = plain.find(needle)
        if start < 0:
            raise ValueError(f"article phrase not found: {needle}")
        excerpt = plain[start:start+length]
        if key == "bridson-author":
            # Preserve publisher's explicit licensing statement, not an inference
            # from being freely downloadable.
            m = re.search(r"public domain.{0,120}", plain)
            if not m:
                raise ValueError("publisher public-domain statement not found")
            excerpt += "\nPublisher license statement: " + m.group(0)
        path = SNAP / f"technical-{key}.txt"
        path.write_text(f"URL: {url}\nVerified: {DATE}\nHTTP: {status}\nSHA256 of fetched HTML: {hashlib.sha256(data).hexdigest()}\nScope: short author-page excerpt; no visual assets\n\n{excerpt}\n", encoding="utf-8")
        records[key] = {"url": url, "httpStatus": status, "bytesRead": len(data), "sha256": hashlib.sha256(data).hexdigest(), "snapshot": path.relative_to(OUT).as_posix(), "readScope": "short author-page excerpt; page text reviewed in memory; no image/video fetched"}
    except Exception as exc:
        records[key] = {"url": url, "error": str(exc), "verificationStatus": "failed"}

try:
    from pypdf import PdfReader
    url = "https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf"
    data, status, mime = fetch(url, 500000)
    reader = PdfReader(io.BytesIO(data))
    text = "\n".join(page.extract_text() or "" for page in reader.pages)
    lines = text.splitlines()
    selected = []
    for term in ["Curl-Noise", "divergence-free", "vector potential", "boundaries"]:
        for n, line in enumerate(lines):
            if term.lower() in line.lower():
                selected.extend(lines[max(0,n-1):min(len(lines),n+3)])
                break
    excerpt = "\n".join(selected)[:1000]
    path = SNAP / "technical-bridson-paper.txt"
    path.write_text(f"URL: {url}\nVerified: {DATE}\nHTTP: {status}\nBytes: {len(data)}\nSHA256: {hashlib.sha256(data).hexdigest()}\nPages parsed: {len(reader.pages)}\nScope: short textual excerpts only; PDF not saved or redistributed\n\n{excerpt}\n", encoding="utf-8")
    records["bridson-paper"] = {"url": url, "httpStatus": status, "bytesRead": len(data), "sha256": hashlib.sha256(data).hexdigest(), "snapshot": path.relative_to(OUT).as_posix(), "readScope": f"{len(reader.pages)} PDF pages text parsed; short excerpt stored, no PDF/image download artifact"}
except Exception as exc:
    records["bridson-paper"] = {"error": str(exc), "verificationStatus": "failed"}

(OUT / "technical-verification.json").write_text(json.dumps({"verifiedOn": DATE, "fixedCommits": COMMITS, "commitVerification": "git ls-remote <public repository> HEAD; raw.githubusercontent.com fixed-commit file retrieval. GitHub API returned HTTP 403 rate-limit; no API result is used as successful evidence.", "files": records}, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"files":len(records), "failed":[k for k,v in records.items() if "error" in v]},ensure_ascii=False))
