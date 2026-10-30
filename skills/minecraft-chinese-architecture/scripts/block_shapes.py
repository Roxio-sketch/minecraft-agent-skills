"""Small offline shape vocabulary on a 1/16-block grid, not a game renderer.

Shapes represent visible solids, not collision boxes (fences collide above
their visible height). Unsupported blocks use a cube and are reported.
"""
from __future__ import annotations

from functools import lru_cache
import numpy as np

from voxel_model import CARDINALS, split_state


RESOLUTION = 16


@lru_cache(maxsize=4096)
def shape_for(state):
    base, p = split_state(state)
    name = base.split(":", 1)[1]
    a = np.zeros((16, 16, 16), dtype=bool)
    def box(x0, y0, z0, x1, y1, z1):
        a[y0:y1, z0:z1, x0:x1] = True
    status = "supported"
    if not base.startswith("minecraft:"):
        # A mod's *_slab or *_wall name does not establish vanilla geometry.
        # Keep a reported occupied-cell fallback until actual models are read.
        a[:] = True
        status = "cube_fallback"
    elif name in ("air", "cave_air", "void_air"):
        pass
    elif name.endswith("_slab"):
        kind = p.get("type", "bottom")
        if kind not in ("bottom", "top", "double"):
            raise ValueError(f"Unknown slab type: {state}")
        box(0, 8 if kind == "top" else 0, 0, 16, 8 if kind == "bottom" else 16, 16)
    elif name.endswith("_stairs"):
        facing, kind, half = p.get("facing", "north"), p.get("shape", "straight"), p.get("half", "bottom")
        if facing not in CARDINALS or kind not in ("straight", "inner_left", "inner_right", "outer_left", "outer_right") or half not in ("bottom", "top"):
            raise ValueError(f"Unknown stair properties: {state}")
        z, x = np.indices((16, 16))
        directional = {"north": z < 8, "south": z >= 8, "east": x >= 8, "west": x < 8}
        step = directional[facing].copy()
        if kind != "straight":
            turn = -1 if kind.endswith("left") else 1
            side = directional[CARDINALS[(CARDINALS.index(facing) + turn) % 4]]
            step = step | side if kind.startswith("inner") else step & side
        if half == "bottom":
            a[:8] = True
            a[8:] = step
        else:
            a[8:] = True
            a[:8] = step
    elif name.endswith("_fence"):
        box(6, 0, 6, 10, 16, 10)
        for direction in CARDINALS:
            if p.get(direction) != "true": continue
            for y0, y1 in ((6, 9), (12, 15)):
                if direction == "north": box(7, y0, 0, 9, y1, 6)
                if direction == "south": box(7, y0, 10, 9, y1, 16)
                if direction == "west": box(0, y0, 7, 6, y1, 9)
                if direction == "east": box(10, y0, 7, 16, y1, 9)
    elif name.endswith("_wall"):
        if p.get("up", "true") == "true": box(4, 0, 4, 12, 16, 12)
        for direction in CARDINALS:
            connection = p.get(direction, "none")
            if connection == "none": continue
            if connection not in ("low", "tall"): raise ValueError(f"Unknown wall connection: {state}")
            height = 14 if connection == "low" else 16
            if direction == "north": box(5, 0, 0, 11, height, 8)
            if direction == "south": box(5, 0, 8, 11, height, 16)
            if direction == "west": box(0, 0, 5, 8, height, 11)
            if direction == "east": box(8, 0, 5, 16, height, 11)
    elif name.endswith("_pane") or name == "iron_bars":
        if name == "iron_bars": status = "envelope_approximation"
        box(7, 0, 7, 9, 16, 9)
        for direction in CARDINALS:
            if p.get(direction) != "true": continue
            if direction == "north": box(7, 0, 0, 9, 16, 7)
            if direction == "south": box(7, 0, 9, 9, 16, 16)
            if direction == "west": box(0, 0, 7, 7, 16, 9)
            if direction == "east": box(9, 0, 7, 16, 16, 9)
    elif name.endswith("_carpet"):
        box(0, 0, 0, 16, 1, 16)
    elif name == "snow":
        layers = int(p.get("layers", "1"))
        if not 1 <= layers <= 8: raise ValueError(f"Invalid snow layers: {state}")
        box(0, 0, 0, 16, layers * 2, 16)
    else:
        a[:] = True
        status = "cube_fallback"
    a.flags.writeable = False
    return a, status


def rectangles(mask):
    """Partition a face mask into rectangles without enlarging the silhouette."""
    a = np.array(mask, dtype=bool, copy=True)
    found = []
    for row in range(a.shape[0]):
        col = 0
        while col < a.shape[1]:
            if not a[row, col]:
                col += 1
                continue
            end = col + 1
            while end < a.shape[1] and a[row, end]: end += 1
            bottom = row + 1
            while bottom < a.shape[0] and a[bottom, col:end].all(): bottom += 1
            found.append((col, row, end, bottom))
            a[row:bottom, col:end] = False
            col = end
    return found


@lru_cache(maxsize=4096)
def internal_faces(state):
    a, _ = shape_for(state)
    faces = []
    for step in range(15):
        for direction, mask, position in (
            ("up", a[step] & ~a[step + 1], step + 1),
            ("east", a[:, :, step] & ~a[:, :, step + 1], step + 1),
            ("north", a[:, step + 1, :] & ~a[:, step, :], step + 1),
        ):
            if mask.any(): faces.extend((direction, position, rect) for rect in rectangles(mask))
    return tuple(faces)


@lru_cache(maxsize=16384)
def boundary_faces(state, neighbor, direction):
    a, _ = shape_for(state)
    b, _ = shape_for(neighbor)
    if direction == "up": mask = a[-1] & ~b[0]; position = 16
    elif direction == "east": mask = a[:, :, -1] & ~b[:, :, 0]; position = 16
    elif direction == "north": mask = a[:, 0, :] & ~b[:, -1, :]; position = 0
    else: raise ValueError(direction)
    return tuple((direction, position, rect) for rect in rectangles(mask))


def face_vertices(direction, position, rect, xyz=(0, 0, 0)):
    u0, v0, u1, v1 = (n / 16 for n in rect)
    t = position / 16
    if direction == "up": values = ((u0, t, v0), (u1, t, v0), (u1, t, v1), (u0, t, v1))
    elif direction == "east": values = ((t, v0, u0), (t, v0, u1), (t, v1, u1), (t, v1, u0))
    else: values = ((u0, v0, t), (u1, v0, t), (u1, v1, t), (u0, v1, t))
    return tuple(tuple(a + b for a, b in zip(v, xyz)) for v in values)
