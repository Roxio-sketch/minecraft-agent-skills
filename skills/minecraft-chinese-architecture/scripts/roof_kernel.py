"""Original voxel roof morphology; not a historical carpentry reconstruction.

Roof bounding box is symmetric about x=z=0; ridge_axis rotates canonical X
forms. Heights count whole-block surface cells, not slab collision elevations.
"""
from __future__ import annotations

import math

from voxel_model import Model


FORMS = ("hip", "hip_and_gable", "overhanging_gable", "flush_gable", "pyramidal", "barrel_gable")


def make_roof(*, half_x=16, half_z=10, base_y=0, rise=6, curve=1.5,
              uplift=1, form="hip", ridge_axis="x", gable_inset=4,
              family=None, gable_block="minecraft:white_terracotta",
              trim="minecraft:cut_copper"):
    """Return a roof-only Model and report with world-local surface samples.

    hip_and_gable has a four-sided lower skirt and two triangular gable infills
    above it. flush_gable and overhanging_gable share a two-slope surface; their
    wall-plane/eave relation is set by the building assembler, not this kernel.
    barrel_gable is a rounded ridge-free two-sided profile (卷棚形态原型).
    """
    for name, value in (("half_x", half_x), ("half_z", half_z), ("base_y", base_y),
                        ("rise", rise), ("uplift", uplift), ("gable_inset", gable_inset)):
        if type(value) is not int:
            raise ValueError(f"{name} must be an integer")
    if form not in FORMS or ridge_axis not in ("x", "z"):
        raise ValueError("Unknown roof form or ridge axis")
    if not 3 <= half_x <= 120 or not 3 <= half_z <= 120 or not 1 <= rise <= 40:
        raise ValueError("Roof half-spans must be 3..120 and rise 1..40")
    if type(curve) not in (int, float) or not math.isfinite(curve) or not 1 <= curve <= 2.5:
        raise ValueError("curve must be finite, 1..2.5")
    if not 0 <= uplift <= 4:
        raise ValueError("uplift must be 0..4")
    if form in ("flush_gable", "barrel_gable") and uplift:
        raise ValueError(f"{form} currently requires uplift=0")
    a, b = (half_x, half_z) if ridge_axis == "x" else (half_z, half_x)
    if form == "hip" and a < b:
        raise ValueError("hip ridge_axis must follow the longer footprint dimension")
    if form == "pyramidal" and half_x != half_z:
        raise ValueError("pyramidal currently supports a square footprint")
    if form == "hip_and_gable" and not 2 <= gable_inset < min(a, b):
        raise ValueError("gable_inset must be >=2 and less than both canonical half-spans")
    if family is None:
        family = lambda x, z: {"block": "minecraft:deepslate_tiles", "stairs": "minecraft:deepslate_tile_stairs", "slab": "minecraft:deepslate_tile_slab"}
    m = Model((-half_x, base_y - 1, -half_z), (half_x, base_y + rise + uplift + 2, half_z))
    samples, heights = {}, {}
    def uv(x, z):
        return (x, z) if ridge_axis == "x" else (z, -x)
    def profile(t):
        return rise * max(0, min(1, t)) ** curve
    ridge_half = a - (b if form == "hip" else gable_inset if form == "hip_and_gable" else 0)
    for z in range(-half_z, half_z + 1):
        for x in range(-half_x, half_x + 1):
            u, v = uv(x, z)
            side = b - abs(v)
            if form == "hip":
                height = profile(min(a - abs(u), side) / b)
            elif form == "pyramidal":
                height = profile(min(1 - abs(u) / a, 1 - abs(v) / b))
            elif form == "hip_and_gable" and abs(u) > ridge_half:
                height = profile(min(a - abs(u), side) / b)
            elif form == "barrel_gable":
                # Parabolic arch has horizontal tangent at the rounded crown;
                # no ridge cap is added. Not an archaeological curve formula.
                height = rise * (1 - (abs(v) / b) ** 2)
            else:
                height = profile(side / b)
            height += uplift * (abs(u) / a) ** 6 * (abs(v) / b) ** 6
            samples[x, z] = base_y + height
            heights[x, z] = base_y + int(round(height))

    slope_directions = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}
    gable_fill = 0
    for (x, z), y in heights.items():
        u, v = uv(x, z)
        f = family(x, z)
        neighbors = []
        uphill = {}
        for facing, (dx, dz) in slope_directions.items():
            key = x + dx, z + dz
            if key not in heights:
                continue
            nu, nv = uv(*key)
            # The x discontinuity at a gable is wall infill, not a vertical
            # staircase of roofing. Keep the upper and lower shells separate.
            if form == "hip_and_gable" and (abs(u) <= ridge_half) != (abs(nu) <= ridge_half) and v == nv:
                continue
            neighbors.append(heights[key])
            uphill[facing] = samples[key]
        low = min([y] + neighbors) - 1
        m.box((x, low, z), (x, y, z), f["block"])
        if uphill and max(uphill.values()) > samples[x, z] + 1e-8:
            highest = max(uphill.values())
            options = [k for k, h in uphill.items() if abs(h - highest) < 1e-8]
            # Leave tied hip/diagonal slopes as full blocks for state symmetry.
            if len(options) == 1:
                m.set(x, y, z, f["stairs"] + f"[facing={options[0]},half=bottom,shape=straight,waterlogged=false]")
        if abs(x) == half_x or abs(z) == half_z:
            m.set(x, low, z, trim)
        if form == "hip_and_gable" and abs(u) == ridge_half:
            lower = base_y + int(round(profile(min(gable_inset, b - abs(v)) / b))) - 1
            if lower < low:
                m.box((x, lower, z), (x, low - 1, z), gable_block)
                gable_fill += low - lower

    if form == "pyramidal":
        ridge_coords = [(0, 0)]
    elif form == "barrel_gable":
        ridge_coords = []
    else:
        ridge_coords = [(u, 0) if ridge_axis == "x" else (0, u) for u in range(-ridge_half, ridge_half + 1)]
    for x, z in ridge_coords:
        f = family(x, z)
        m.set(x, heights[x, z] + 1, z, f["slab"] + "[type=bottom,waterlogged=false]")
    # Coordinates are included as arrays so other components can connect to the
    # shell without reverse-engineering palette roles or duplicating formulas.
    surface_rows = [[heights[x, z] for x in range(-half_x, half_x + 1)] for z in range(-half_z, half_z + 1)]
    report = {"schema": "chinese-roof/1", "form": form, "ridge_axis": ridge_axis,
              "half_x": half_x, "half_z": half_z, "base_y": base_y, "rise": rise,
              "curve": curve, "uplift": uplift, "gable_inset": gable_inset if form == "hip_and_gable" else None,
              "surface_y_rows_zx": surface_rows, "surface_min_y": min(heights.values()),
              "surface_max_y": max(heights.values()), "ridge_cells": len(ridge_coords),
              "gable_infill_voxels": gable_fill, "authorship": "original procedural morphology",
              "historical_reconstruction": False}
    return m, report
