"""Original moon openings, stepped bridges and voxel sight-line checks."""
from __future__ import annotations

import math
import numpy as np

from generate_courtyard import walk_paths
from voxel_model import Model


def make_garden_wall(half_length=24, radius=4, gate_x=-8, windows=(12,)):
    if any(type(v) is not int for v in (half_length, radius, gate_x)) or not 12 <= half_length <= 80 or not 3 <= radius <= 6:
        raise ValueError('Wall half length 12..80 and moon radius 3..6 are required')
    if abs(gate_x) + radius + 2 >= half_length: raise ValueError('Moon opening too close to wall end')
    if not isinstance(windows, (list, tuple)) or any(type(v) is not int for v in windows): raise ValueError('Integer window centers required')
    for x in windows:
        if abs(x) + 4 >= half_length or abs(x-gate_x) <= radius+4: raise ValueError('Window intersects opening or wall end')
    if len(set(windows)) != len(windows) or any(abs(a-b) < 8 for i,a in enumerate(windows) for b in windows[i+1:]):
        raise ValueError('Windows overlap')
    h=2*radius+1
    m=Model((-half_length,0,-1),(half_length,h+1,1))
    m.box((-half_length,0,0),(half_length,h,0),'minecraft:white_concrete')
    m.box((-half_length,0,0),(half_length,0,0),'minecraft:stone_bricks')
    for y in range(1,h+1):
        for x in range(gate_x-radius,gate_x+radius+1):
            if (x-gate_x)**2+(y-radius)**2 <= radius**2:m.set(x,y,0,'minecraft:air')
    for x in windows:
        m.box((x-2,3,0),(x+2,5,0),'minecraft:air')
        # Small opaque lattice; holes are real air, not simulated transparency.
        m.box((x,3,0),(x,5,0),'minecraft:dark_oak_planks')
        m.box((x-2,4,0),(x+2,4,0),'minecraft:dark_oak_planks')
    m.box((-half_length,h+1,-1),(half_length,h+1,1),'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')
    return m,{'opening_center_floor_xyz':[gate_x,0,0],'radius':radius,'height':h,
              'window_centers_x':list(windows),'clearance_three_wide':all(m.get(x,y,0)=='minecraft:air' for x in range(gate_x-1,gate_x+2) for y in (1,2)),
              'limits':'Block-scale circular opening and authored lattice; no surveyed historic joinery.'}


def make_bridge(half_span=12, clear_width=3, rise=3):
    if type(half_span) is not int or not 5 <= half_span <= 40:raise ValueError('Bridge half_span must be 5..40')
    if type(clear_width) is not int or clear_width not in (3,5):raise ValueError('Bridge clear_width must be 3 or 5')
    if type(rise) is not int or not 0 <= rise <= min(6,half_span//2):raise ValueError('Bridge rise exceeds bounded step slope')
    hw=(clear_width+1)//2
    m=Model((-half_span,-2,-hw),(half_span,rise+4,hw))
    heights={x:int(round(rise*(1-(abs(x)/half_span)**2))) for x in range(-half_span,half_span+1)}
    if any(abs(heights[x+1]-heights[x])>1 for x in range(-half_span,half_span)):raise ValueError('Deck has an unwalkable step')
    for x,top in heights.items():
        m.box((x,max(-2,top-2),-hw),(x,top,hw),'minecraft:stone_bricks')
        higher=[xx for xx in (x-1,x+1) if xx in heights and heights[xx]>top]
        if len(higher)==1:
            facing='east' if higher[0]>x else 'west'
            m.box((x,top,-hw+1),(x,top,hw-1),f'minecraft:stone_brick_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]')
        for z in (-hw,hw):
            m.set(x,top+1,z,'minecraft:stone_bricks')
            m.set(x,top+2,z,'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
    paths=walk_paths(m,[-half_span,0,0],{'exit':[half_span,0,0]})
    if not paths['all_targets_reached']:raise AssertionError('Bridge deck is disconnected')
    return m,{'span':2*half_span+1,'clear_width':clear_width,'deck_y_by_x':[[x,y] for x,y in heights.items()],
              'endpoints_floor_xyz':[[-half_span,0,0],[half_span,0,0]],'routes':paths,
              'limits':'Stepped voxel arch profile, not structural engineering or live collision validation.'}


def line_of_sight(model, first, last):
    """Conservative solid-cell visibility; samples at most 1/8 cell apart."""
    a,b=np.asarray(first,float),np.asarray(last,float)
    if a.shape!=(3,) or b.shape!=(3,) or not np.all(np.isfinite([a,b])):raise ValueError('Finite XYZ eye points required')
    steps=max(1,int(math.ceil(np.linalg.norm(b-a)*8)))
    if steps>20000:raise ValueError('Sight segment too long')
    visited=set()
    # The last point can be a visible solid target; test the space before it.
    target=tuple(np.floor(b).astype(int))
    for t in np.linspace(0,1,steps+1)[:-1]:
        cell=tuple(np.floor(a+(b-a)*t).astype(int))
        if cell in visited or cell==target:continue
        visited.add(cell)
        if np.any(np.array(cell)<model.origin) or np.any(np.array(cell)>model.maximum):raise ValueError('Sight segment outside model')
        state=model.get(*map(int,cell))
        if state!='minecraft:air':return {'visible':False,'first_blocker_xyz':list(map(int,cell)),'state':state,'sampled_cells':len(visited)}
    return {'visible':True,'first_blocker_xyz':None,'sampled_cells':len(visited)}
