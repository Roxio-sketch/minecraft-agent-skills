"""Shape-aware orthographic/isometric solid preview with declared cube fallbacks."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from block_shapes import boundary_faces, face_vertices, internal_faces, shape_for
from render_model import color, font, shade
from voxel_model import Model


def render(npz, path, title=None, view="isometric", size=(1600, 1100), camera_turns=0):
    with np.load(npz, allow_pickle=False) as data:
        a, palette = data["blocks"], data["palette"].tolist()
    if a.ndim != 3 or a.size > 8_000_000:
        raise ValueError("Shape preview requires a 3D model of at most 8 million voxels")
    if type(camera_turns) is not int:
        raise ValueError("camera_turns must be an integer")
    if camera_turns % 4:
        rotated = Model.load(npz).rotated(camera_turns)
        a, palette = rotated.blocks, rotated.palette
    h, d, w = a.shape
    used, counts = np.unique(a, return_counts=True)
    fallbacks = {palette[int(i)]: int(n) for i, n in zip(used, counts) if i and shape_for(palette[int(i)])[1] == "cube_fallback"}
    envelopes = {palette[int(i)]: int(n) for i, n in zip(used, counts) if i and shape_for(palette[int(i)])[1] == "envelope_approximation"}
    if view not in ("isometric", "front", "side", "top"):
        raise ValueError("Unknown view")
    faces = []
    def add(face, x, y, z, state):
        direction, position, rect = face
        if view == "front" and direction != "north": return
        if view == "side" and direction != "east": return
        if view == "top" and direction != "up": return
        vertices = face_vertices(direction, position, rect, (x, y, z))
        if view == "isometric": depth = sum(v[0] + v[1] - v[2] for v in vertices) / 4
        elif view == "front": depth = -sum(v[2] for v in vertices) / 4
        elif view == "side": depth = sum(v[0] for v in vertices) / 4
        else: depth = sum(v[1] for v in vertices) / 4
        faces.append((depth, vertices, state, direction))
    for y, z, x in zip(*np.nonzero(a)):
        y, z, x = int(y), int(z), int(x)
        state = palette[int(a[y, z, x])]
        local = list(internal_faces(state))
        for direction, neighbor in (("up", int(a[y + 1, z, x]) if y + 1 < h else 0),
                                    ("east", int(a[y, z, x + 1]) if x + 1 < w else 0),
                                    ("north", int(a[y, z - 1, x]) if z else 0)):
            local.extend(boundary_faces(state, palette[neighbor], direction))
        for face in local: add(face, x, y, z, state)

    def project(v):
        x, y, z = v
        if view == "isometric": return x + z, (x - z) / 2 - y
        if view == "front": return x, -y
        if view == "side": return -z, -y
        return x, z
    corners = [project((x, y, z)) for x in (0, w) for y in (0, h) for z in (0, d)]
    min_u, max_u = min(v[0] for v in corners), max(v[0] for v in corners)
    min_v, max_v = min(v[1] for v in corners), max(v[1] for v in corners)
    scale = min((size[0] - 100) / (max_u - min_u), (size[1] - 150) / (max_v - min_v))
    offset_u = (size[0] - (max_u - min_u) * scale) / 2 - min_u * scale
    offset_v = (size[1] - 140 - (max_v - min_v) * scale) / 2 - min_v * scale + 80
    canvas = Image.new("RGB", size, (244, 242, 235))
    draw = ImageDraw.Draw(canvas)
    draw.text((30, 20), title or f"{Path(npz).stem} | {view}", font=font(27), fill=(38, 52, 52))
    rgb_cache = {}
    for depth, vertices, state, direction in sorted(faces, key=lambda item: item[0]):
        key = state, direction
        if key not in rgb_cache:
            rgb_cache[key] = shade(color(state), {"up": 1.1, "east": .67, "north": .84}[direction])
        coords = [(offset_u + project(v)[0] * scale, offset_v + project(v)[1] * scale) for v in vertices]
        rgb = rgb_cache[key]
        draw.polygon(coords, fill=rgb, outline=shade(rgb, .8))
    draw.text((30, size[1] - 58), "Offline solid shapes / approximate colors / unsupported states use cubes / not in-game", font=font(18), fill=(91, 104, 101))
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(path)
    report = {"schema": "architecture-shape-preview/1", "model": str(Path(npz).resolve()),
              "view": view, "camera_quarter_turns": camera_turns % 4, "image": str(path.resolve()), "faces_rendered": len(faces),
              "occupied_voxels": int(np.count_nonzero(a)), "cube_fallback_voxels": sum(fallbacks.values()),
              "cube_fallback_states": fallbacks,
              "solid_envelope_approximations": envelopes,
              "supported_shapes": ["stairs including inner/outer and top/bottom", "slabs", "fences", "walls", "panes", "iron_bars", "carpets", "snow"],
              "limits": ["This shape vocabulary approximates visible block geometry, not Minecraft collision or physics.",
                         "No texture pack, transparency, lighting engine, resource-pack model loading or in-game block updates.",
                         "Other states use cubes; some are ordinary solid blocks, others may have an unimplemented non-cube shape.",
                         "Painter ordering is an offline projection; not a GPU depth-buffer renderer."]}
    path.with_suffix(".json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("model", type=Path)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--view", choices=("isometric", "front", "side", "top"), default="isometric")
    ap.add_argument("--camera-turns", type=int, default=0)
    args = ap.parse_args()
    report = render(args.model, args.out, view=args.view, camera_turns=args.camera_turns)
    print(json.dumps({k: report[k] for k in ("image", "faces_rendered", "occupied_voxels", "cube_fallback_voxels")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
