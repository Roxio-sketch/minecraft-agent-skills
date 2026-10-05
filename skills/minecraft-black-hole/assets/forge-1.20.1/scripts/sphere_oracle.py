"""Measure the actual black core against an independent, same-frame sphere.

Reusable independent sphere projection and actual PNG silhouette helpers.  Production
projectedCenter is only a diagnostic; it is never the image acceptance oracle.
"""
from __future__ import annotations

import argparse
from collections import deque
import hashlib
import json
from pathlib import Path
import shutil

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
CENTER = np.array([.5, 121.5, 14.5])
RADIUS = .58 * 1.6


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def vec(value):
    return np.array([value[k] for k in ("x", "y", "z")] if isinstance(value, dict) else value,
                    dtype=np.float64)


def erode(mask):
    padded = np.pad(mask, 1)
    h, w = mask.shape
    return np.logical_and.reduce([padded[y:y+h, x:x+w] for y in range(3) for x in range(3)])


def dilate(mask, radius=1):
    padded = np.pad(mask, radius)
    h, w = mask.shape
    return np.logical_or.reduce([padded[y:y+h, x:x+w]
                                 for y in range(2*radius+1) for x in range(2*radius+1)])


def boundary(mask):
    return mask & ~erode(mask)


def nearest_distances(points, targets):
    """Exact Euclidean pixel distances, without an optional SciPy dependency."""
    if not len(points) or not len(targets):
        return np.full(len(points), np.inf)
    return np.concatenate([np.sqrt(((part[:, None] - targets[None])**2).sum(axis=2).min(axis=1))
                           for part in np.array_split(points, max(1, (len(points)+255)//256))])


def keep_components(mask, minimum=4):
    """Discard single dark raster specks; keep separate core lobes around actors."""
    remaining = mask.copy()
    result = np.zeros_like(mask)
    h, w = mask.shape
    while remaining.any():
        y, x = np.argwhere(remaining)[0]
        remaining[y, x] = False
        queue, component = deque([(int(y), int(x))]), []
        while queue:
            py, px = queue.popleft()
            component.append((py, px))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = py+dy, px+dx
                    if 0 <= yy < h and 0 <= xx < w and remaining[yy, xx]:
                        remaining[yy, xx] = False
                        queue.append((yy, xx))
        if len(component) >= minimum:
            yy, xx = zip(*component)
            result[yy, xx] = True
    return result


def predicted_sphere(fixture, width, height):
    vp = np.array(fixture["terrainViewProjection"], dtype=np.float64).reshape(4, 4, order="F")
    inv = np.linalg.inv(vp)
    eye_h = inv @ np.array([0., 0., 1., 0.])
    assert abs(eye_h[3]) > 1e-10, "fixture must contain a perspective projection"
    origin = eye_h[:3] / eye_h[3]
    # PNG uses a top-left origin; fixture marker coordinates use bottom-left.
    yy, xx = np.indices((height, width), dtype=np.float64)
    clip = np.stack(((xx+.5)*2/width-1, 1-(yy+.5)*2/height,
                     np.ones_like(xx), np.ones_like(xx)), axis=-1)
    far_h = clip @ inv.T
    rays = far_h[..., :3] / far_h[..., 3, None] - origin
    rays /= np.linalg.norm(rays, axis=-1)[..., None]
    relative_center = CENTER - vec(fixture["rawCamera"])
    offset = relative_center - origin
    along = rays @ offset
    discriminant = along*along - offset @ offset + RADIUS*RADIUS
    mask = (along > 0) & (discriminant >= 0)
    return mask, origin, vp
