"""Bounded y,z,x voxel models, Y-axis rotation, and atomic component placement.

Quarter turns are clockwise viewed from above: north -> east -> south -> west.
Rotation is about local coordinate (0,0,0); paste offset is a translation after
rotation. Air is index zero. Geometry only: no entities or block-entity payloads.
"""
from __future__ import annotations

import re
from pathlib import Path

import numpy as np


CARDINALS = ("north", "east", "south", "west")
STATE = re.compile(r"^([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\[([^\[\]]+)\])?$")


def split_state(state):
    if not isinstance(state, str) or not (match := STATE.fullmatch(state)):
        raise ValueError(f"Invalid block state: {state!r}")
    props = {}
    if match[2]:
        for part in match[2].split(","):
            pair = part.split("=")
            if len(pair) != 2 or not all(pair) or pair[0] in props:
                raise ValueError(f"Invalid or duplicate property: {state!r}")
            props[pair[0]] = pair[1]
    return match[1], props


def join_state(base, props):
    return base + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")


def rotate_state(state, turns):
    """Rotate Java directional properties; relative left/right and hinges stay.

    Supports facing, axis, 16-way rotation, cardinal connection keys, rail shape,
    and jigsaw orientation. Does not infer arbitrary mod-specific properties.
    """
    if type(turns) is not int:
        raise ValueError("turns must be an integer")
    base, props = split_state(state)
    turns %= 4
    rotate = lambda value: CARDINALS[(CARDINALS.index(value) + turns) % 4] if value in CARDINALS else value
    out = {}
    for key, value in props.items():
        if key in CARDINALS:
            key = rotate(key)
        elif key == "facing":
            value = rotate(value)
        elif key == "axis" and turns % 2 and value in ("x", "z"):
            value = "z" if value == "x" else "x"
        elif key == "rotation":
            if not value.isdigit() or not 0 <= int(value) < 16:
                raise ValueError(f"Invalid 16-way rotation: {state}")
            value = str((int(value) + 4 * turns) % 16)
        elif key == "orientation":
            value = "_".join(rotate(part) for part in value.split("_"))
        elif key == "shape" and base.endswith("rail"):
            if value.startswith("ascending_"):
                value = "ascending_" + rotate(value.removeprefix("ascending_"))
            elif value in ("north_south", "east_west", "north_east", "north_west", "south_east", "south_west"):
                ends = {rotate(v) for v in value.split("_")}
                value = next(candidate for candidate in ("north_south", "east_west", "north_east", "north_west", "south_east", "south_west") if set(candidate.split("_")) == ends)
        out[key] = value
    return join_state(base, out)


def vector3(value, name):
    raw = np.asarray(value)
    if raw.shape != (3,) or raw.dtype.kind not in "iu" or raw.dtype.kind == "b":
        raise ValueError(f"{name} must contain three integers")
    values = tuple(int(v) for v in raw)
    if any(abs(v) > 30_000_000 for v in values):
        raise ValueError(f"{name} exceeds supported coordinate range")
    return np.array(values, dtype=np.int64)


class Model:
    def __init__(self, minimum, maximum, max_voxels=8_000_000):
        self.origin = vector3(minimum, "minimum")
        end = vector3(maximum, "maximum")
        size = end - self.origin + 1
        if np.any(size <= 0) or np.any(size > 32767) or int(np.prod(size)) > max_voxels:
            raise ValueError(f"Invalid or excessive model dimensions: {size.tolist()}")
        self.blocks = np.zeros((size[1], size[2], size[0]), dtype=np.uint16)
        self.palette = ["minecraft:air"]
        self.index = {self.palette[0]: 0}

    @property
    def maximum(self):
        h, d, w = self.blocks.shape
        return self.origin + [w - 1, h - 1, d - 1]

    def pid(self, state):
        base, props = split_state(state)
        if base in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
            if props:
                raise ValueError("Air cannot have block properties")
            return 0
        state = join_state(base, props)
        if state not in self.index:
            if len(self.palette) >= 65536:
                raise ValueError("uint16 palette capacity exceeded")
            self.index[state] = len(self.palette)
            self.palette.append(state)
        return self.index[state]

    def _position(self, xyz):
        pos = vector3(xyz, "position") - self.origin
        if np.any(pos < 0) or np.any(pos > self.maximum - self.origin):
            raise IndexError(f"Position outside model: {xyz}")
        return pos

    def set(self, x, y, z, state):
        ix, iy, iz = self._position((x, y, z))
        self.blocks[iy, iz, ix] = self.pid(state)

    def get(self, x, y, z):
        ix, iy, iz = self._position((x, y, z))
        return self.palette[int(self.blocks[iy, iz, ix])]

    def box(self, a, b, state):
        ax, ay, az = self._position(a)
        bx, by, bz = self._position(b)
        if bx < ax or by < ay or bz < az:
            raise ValueError("Box maximum precedes minimum")
        self.blocks[ay:by + 1, az:bz + 1, ax:bx + 1] = self.pid(state)

    def rotated(self, turns):
        if type(turns) is not int:
            raise ValueError("turns must be an integer")
        turns %= 4
        x0, y0, z0 = self.origin
        x1, y1, z1 = self.maximum
        corners = np.array([[x, z] for x in (x0, x1) for z in (z0, z1)])
        for _ in range(turns):
            corners = np.column_stack((-corners[:, 1], corners[:, 0]))
        low, high = corners.min(axis=0), corners.max(axis=0)
        out = Model((int(low[0]), int(y0), int(low[1])), (int(high[0]), int(y1), int(high[1])))
        mapping = np.array([out.pid(rotate_state(s, turns)) for s in self.palette], dtype=np.uint16)
        out.blocks = mapping[np.rot90(self.blocks, k=-turns, axes=(1, 2))].copy()
        return out

    def paste(self, source, offset=(0, 0, 0), turns=0, conflict="error", include_air=False):
        """Preflight all overlap/bounds before changing destination geometry.

        Equal-state overlaps are allowed. error rejects differing nonair states;
        keep preserves occupied destination; replace explicitly overwrites it.
        include_air + replace may carve space. All coordinates are inclusive.
        """
        if conflict not in ("error", "keep", "replace"):
            raise ValueError("conflict must be error, keep, or replace")
        shifted = source.rotated(turns)
        delta = vector3(offset, "offset")
        lower = shifted.origin + delta
        upper = shifted.maximum + delta
        ax, ay, az = self._position(lower)
        bx, by, bz = self._position(upper)
        target = self.blocks[ay:by + 1, az:bz + 1, ax:bx + 1]
        incoming = shifted.blocks
        selected = np.ones(incoming.shape, dtype=bool) if include_air else incoming != 0
        # Compare canonical states without adding a palette entry until safe.
        prior_ids = np.array([self.index.get(s, -1) for s in shifted.palette], dtype=np.int32)
        collisions = selected & (target != 0) & (target != prior_ids[incoming])
        count = int(collisions.sum())
        if conflict == "error" and count:
            raise ValueError(f"Component overlaps {count} differing occupied voxels")
        if conflict == "keep":
            selected &= target == 0
        missing = set(shifted.palette) - set(self.palette)
        if len(self.palette) + len(missing) > 65536:
            raise ValueError("uint16 palette capacity exceeded")
        mapping = np.array([self.pid(s) for s in shifted.palette], dtype=np.uint16)
        target[selected] = mapping[incoming[selected]]
        return {"written_voxels": int(selected.sum()), "differing_occupied_overlaps": count,
                "origin": lower.tolist(), "maximum": upper.tolist(), "quarter_turns": turns % 4}

    def save(self, path):
        np.savez_compressed(path, blocks=self.blocks, palette=np.array(self.palette), origin=self.origin)

    @classmethod
    def load(cls, path):
        with np.load(Path(path), allow_pickle=False) as data:
            a, states = data["blocks"], data["palette"].tolist()
            origin = vector3(data["origin"], "origin")
        if a.ndim != 3 or a.dtype.kind not in "iu" or not all(a.shape):
            raise ValueError("Model needs a nonempty integer y,z,x block array")
        if not isinstance(states, list) or not states or int(a.min()) < 0 or int(a.max()) >= len(states):
            raise ValueError("Palette indices outside range")
        h, d, w = a.shape
        result = cls(origin, origin + [w - 1, h - 1, d - 1])
        mapping = np.array([result.pid(s) for s in states], dtype=np.uint16)
        result.blocks = mapping[a]
        return result
