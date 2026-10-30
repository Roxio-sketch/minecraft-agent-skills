"""Original raster-to-voxel relief and openwork, with explicit depth and scale.

Motifs are authored geometric studies, not traced historical carving patterns.
Rows run upward in Y; the visible face looks toward negative Z.
"""
from __future__ import annotations

import math

import numpy as np
from scipy.ndimage import distance_transform_edt


def stroke(mask, points, width=1):
    """Rasterize an axis-connected path; thin diagonals cannot float by corners."""
    height, columns = mask.shape
    radius = (width - 1) // 2
    def dot(x, y):
        for dx in range(-radius, radius + 1):
            for dy in range(-radius, radius + 1):
                if 0 <= x + dx < columns and 0 <= y + dy < height:
                    mask[y + dy, x + dx] = True
    previous = None
    for a, b in zip(points, points[1:]):
        steps = max(1, math.ceil(max(abs(b[0] - a[0]), abs(b[1] - a[1])) * 3))
        for i in range(steps + 1):
            t = i / steps
            x, y = round(a[0] * (1 - t) + b[0] * t), round(a[1] * (1 - t) + b[1] * t)
            if previous is not None and previous != (x, y):
                px, py = previous
                for xx in range(min(px, x), max(px, x) + 1): dot(xx, py)
                for yy in range(min(py, y), max(py, y) + 1): dot(x, yy)
            dot(x, y); previous = (x, y)


def relief_mask(width, height, motif, stroke_width=3, repeats=3):
    mask = np.zeros((height, width), dtype=bool)
    cx, cy = (width - 1) / 2, (height - 1) / 2
    if motif == 'lotus':
        # Pointed petal outlines preserve the divisions that a union of round
        # filled ellipses erased into one bulb-shaped mass in small previews.
        anchor=np.array([cx,height*.19]); length=min(height*.70,width*.42)
        for angle in (-55, -28, 0, 28, 55):
            radians=math.radians(angle); direction=np.array([math.sin(radians),math.cos(radians)])
            normal=np.array([direction[1],-direction[0]]);points=[]
            for sign,seq in ((1,np.linspace(0,1,35)),(-1,np.linspace(1,0,35))):
                for t in seq:
                    points.append((anchor+direction*length*t+normal*sign*math.sin(math.pi*t)*length*.17).tolist())
            stroke(mask,points,stroke_width)
        stroke(mask, [(cx, 0), (cx, height*.19)], stroke_width)
        stroke(mask, [(width * .16, height * .23), (width*.25,height*.09),(cx,height*.04),
                      (width*.75,height*.09),(width*.84,height*.23)], stroke_width)
    elif motif == 'meander':
        tile_width = (width - 1) / repeats
        for i in range(repeats):
            left, right = i * tile_width + 1, (i + 1) * tile_width - 1
            bottom, top = 1, height - 2
            if right - left < 5: raise ValueError('Meander repeat needs at least seven columns')
            points = [(left, bottom), (right, bottom), (right, top), (left, top),
                      (left, bottom + (top-bottom)*.32), (right - (right-left)*.35, bottom + (top-bottom)*.32),
                      (right - (right-left)*.35, top - (top-bottom)*.3),
                      (left + (right-left)*.3, top - (top-bottom)*.3)]
            stroke(mask, points, stroke_width)
    elif motif == 'cloud_scroll':
        for sign in (-1, 1):
            points = []
            for t in np.linspace(0, 1, 140):
                theta = t * 2.8 * math.pi
                radius = (1 - t) * min(width / 4.5, height / 2.5)
                points.append((cx + sign * (width * .21 + radius * math.cos(theta)), cy + radius * math.sin(theta)))
            stroke(mask, points, stroke_width)
        stroke(mask, [(width*.12, height*.18), (cx, height*.1), (width*.88, height*.18)], stroke_width)
    else:
        raise ValueError('motif must be lotus, meander or cloud_scroll')
    return mask


def screen_mask(width, height, pattern, spacing=6, bar_width=1, seed=0):
    mask = np.zeros((height, width), dtype=bool)
    if pattern == 'orthogonal':
        for x in range(0, width, spacing): stroke(mask, [(x, 0), (x, height - 1)], bar_width)
        for y in range(0, height, spacing): stroke(mask, [(0, y), (width - 1, y)], bar_width)
    elif pattern == 'diagonal':
        for b in range(-width, height + width, spacing):
            for sign in (-1, 1):
                stroke(mask, [(0, b), (width - 1, b + sign * (width - 1))], bar_width)
    elif pattern == 'ice_ray':
        # Convex Voronoi cells make an irregular connected divider network.
        # No implication that this reproduces a particular historic ice-ray grammar.
        rng = np.random.default_rng(seed); y, x = np.indices(mask.shape)
        sites = [(gx + rng.uniform(-spacing*.25, spacing*.25), gy + rng.uniform(-spacing*.25, spacing*.25))
                 for gy in np.arange(spacing/2, height, spacing) for gx in np.arange(spacing/2, width, spacing)]
        if len(sites) < 4: raise ValueError('ice_ray needs at least four cells; reduce spacing or enlarge panel')
        nearest = np.argmin(np.stack([(x-sx)**2+(y-sy)**2 for sx, sy in sites]), axis=0)
        mask[:, 1:] |= nearest[:, 1:] != nearest[:, :-1]
        mask[1:, :] |= nearest[1:, :] != nearest[:-1, :]
        # Connect diagonal divider contacts in both directions without filling cells.
        bridge = (mask[:-1, :-1] & mask[1:, 1:]) | (mask[:-1, 1:] & mask[1:, :-1])
        mask[:-1, :-1] |= bridge
        if bar_width > 1:
            from scipy.ndimage import maximum_filter
            mask = maximum_filter(mask, size=bar_width)
    else:
        raise ValueError('pattern must be orthogonal, diagonal or ice_ray')
    return mask


def depth_field(mask, maximum_depth):
    """Rounded levels stay attached to backing; outside strokes remains zero."""
    distance = distance_transform_edt(np.pad(mask, 1))[1:-1, 1:-1]
    if not np.any(mask): raise ValueError('Ornament mask is empty')
    scale = max(1., float(distance.max()))
    return np.where(mask, np.maximum(1, np.ceil(distance / scale * maximum_depth)), 0).astype(np.int16)
