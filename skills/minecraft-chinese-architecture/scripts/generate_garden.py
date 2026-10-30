"""Original enclosed garden with a screen, moon gate, pond, bridge and galleries."""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

import numpy as np

from garden_components import make_bridge, make_garden_wall, line_of_sight
from generate_building import make_building, verify as verify_building
from generate_corridor import make_corridor
from generate_courtyard import walk_paths, can_support_dry_walk
from scene_output import export_scene
from voxel_model import Model


DEFAULT={'schema':'chinese-garden/1','width':96,'depth':88,'bridge_rise':3,
         'bridge_width':3,'moon_radius':4,'tree_count':3,'plant_clusters':20,'seed':17,
         'ground':'minecraft:grass_block[snowy=false]','path':'minecraft:polished_andesite',
         'wall':'minecraft:white_concrete','evidence':[]}


def place_on_ground(scene,part,offset=(0,0,0),turns=0):
    """Allow intended soil replacement at/below Y0, reject occupied above-ground joins."""
    rotated=part.rotated(turns)
    lo=rotated.origin+offset;hi=rotated.maximum+offset
    if np.any(lo<scene.origin) or np.any(hi>scene.maximum):raise ValueError('Garden component exceeds site')
    x0,y0,z0=lo-scene.origin;x1,y1,z1=hi-scene.origin
    target=scene.blocks[y0:y1+1,z0:z1+1,x0:x1+1]
    above=(np.arange(int(lo[1]),int(hi[1])+1)>0)[:,None,None]
    collisions=above & (target!=0) & (rotated.blocks!=0)
    if collisions.any():raise ValueError(f'Garden components overlap above ground at {int(collisions.sum())} cells')
    return scene.paste(part,offset=offset,turns=turns,conflict='replace')


def make_garden(user):
    if not isinstance(user,dict) or set(user)-set(DEFAULT):raise ValueError('Unknown garden config keys')
    c=copy.deepcopy(DEFAULT);c.update(user)
    if c['schema']!=DEFAULT['schema']:raise ValueError('Unknown garden schema')
    for key,lo,hi in [('width',84,140),('depth',80,140),('moon_radius',3,6),('tree_count',0,3),('plant_clusters',0,60),('seed',0,2**31-1)]:
        if type(c[key]) is not int or not lo<=c[key]<=hi:raise ValueError(f'{key} must be integer {lo}..{hi}')
    if c['width']%2 or c['depth']%2:raise ValueError('Garden width and depth must be even')
    for role in ('wall','ground','path'):
        if not can_support_dry_walk(c[role]):raise ValueError(f'{role} needs a dry solid material')
    # Site materials are parsed by Model.pid; defaults use stable vanilla states.
    w,d=c['width']//2,c['depth']//2
    m=Model((-w-3,-3,-d-4),(w+3,26,d+3))
    m.box(tuple(m.origin),(w+3,-1,d+3),'minecraft:dirt')
    m.box((-w-3,0,-d-4),(w+3,0,d+3),c['ground'])
    m.box((-w-2,0,-d-3),(w+2,0,-d+20),c['path'])
    for x in (-w,w):
        m.box((x,1,-d),(x,5,d),c['wall'])
        m.box((x,6,-d),(x,6,d),'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')
    for z in (-d,d):
        m.box((-w,1,z),(w,5,z),c['wall'])
        m.box((-w,6,z),(w,6,z),'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')
    m.box((-2,1,-d),(2,4,-d),'minecraft:air')
    screen_z=-d+10
    m.box((-6,1,screen_z),(6,5,screen_z),c['wall'])
    m.box((-7,6,screen_z),(7,6,screen_z),'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')
    # The internal screen must meet the boundary, including the end columns.
    gate_x=-w//2;inner_z=-d+22
    wall,wall_design=make_garden_wall(w-1,c['moon_radius'],gate_x,[w//3])
    wall_receipt=place_on_ground(m,wall,offset=(0,0,inner_z))
    # Close the one-cell end joins against the existing perimeter wall.
    for x in (-w,w):m.box((x,1,inner_z),(x,wall_design['height']+1,inner_z),c['wall'])

    pond_z=7;rx=w//3;rz=d//3
    pond=set()
    for z in range(pond_z-rz-2,pond_z+rz+3):
        for x in range(-rx-2,rx+3):
            angle=np.arctan2((z-pond_z)/rz,x/rx)
            perturb=1+0.07*np.sin(3*angle+c['seed']*.1)+0.025*np.cos(5*angle)
            if (x/rx)**2+((z-pond_z)/rz)**2<=perturb**2:pond.add((x,z))
    for x,z in sorted(pond):
        m.set(x,-2,z,'minecraft:stone_bricks')
        m.set(x,-1,z,'minecraft:water[level=0]')
        m.set(x,0,z,'minecraft:air')
    shore={(x+dx,z+dz) for x,z in pond for dx,dz in ((1,0),(-1,0),(0,1),(0,-1))} - pond
    for x,z in sorted(shore):m.set(x,0,z,'minecraft:mossy_stone_bricks')
    bridge,bridge_design=make_bridge(rx+3,c['bridge_width'],c['bridge_rise'])
    bridge_receipt=place_on_ground(m,bridge,offset=(0,0,pond_z))
    pavilion_x=rx+13
    pavilion,pd,pc=make_building({'kind':'pavilion','bays_x':[8],'bays_z':[8],
        'column_height':6,'platform_height':1,'platform_margin':2,'eave_overhang':2,
        'stair_width':3,'roof':{'form':'pyramidal','rise':4,'uplift':1}})
    verify_building(pavilion,pd,pc)
    pavilion_receipt=place_on_ground(m,pavilion,offset=(pavilion_x,0,pond_z),turns=3)
    # Paving is at ground level and does not fill the pond or the bridge arch.
    for x in range(rx+4,pavilion_x+1):
        for z in range(pond_z-1,pond_z+2):
            if m.get(x,0,z)==c['ground']:m.set(x,0,z,c['path'])
    corridor_x=-w+10;corridor_z=d-10
    corridor,cd=make_corridor({'path_xz':[[corridor_x,inner_z+7],[corridor_x,corridor_z],[-10,corridor_z]],
                             'column_height':5,'roof_rise':2,'eave_overhang':1})
    corridor_receipt=place_on_ground(m,corridor)
    # Island and planted corners are authored, outside circulation components.
    rock_center=(4,pond_z+rz//2)
    rock_cells=[]
    for x in range(rock_center[0]-2,rock_center[0]+3):
        for z in range(rock_center[1]-2,rock_center[1]+3):
            h=max(0,4-abs(x-rock_center[0])-abs(z-rock_center[1]))
            if h:
                for y in range(-2,h+1):
                    if y>0 and m.get(x,y,z)!='minecraft:air':raise ValueError('Rock intersects a built component')
                    m.set(x,y,z,'minecraft:andesite' if (x+z+y)%3 else 'minecraft:mossy_cobblestone')
                    rock_cells.append([x,y,z])
    trees=[]
    for i,(x,z) in enumerate([(w-11,d-11),(w-11,-d+11),(-w+11,-d+11)][:c['tree_count']]):
        trunk=6+(c['seed']+i)%3
        m.box((x,1,z),(x,trunk,z),'minecraft:oak_log[axis=y]')
        for dx in range(-3,4):
            for dz in range(-3,4):
                for y in range(trunk-2,trunk+3):
                    if dx*dx+dz*dz+2*(y-trunk)**2<=13 and m.get(x+dx,y,z+dz)=='minecraft:air':
                        m.set(x+dx,y,z+dz,'minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]')
        trees.append({'trunk_xz':[x,z],'height':trunk})
    start=[0,0,-d-3]
    targets={'forecourt':[10,0,-d+5],'moon_gate':[gate_x,0,inner_z],
             'bridge_west':[-rx-3,0,pond_z],'bridge_east':[rx+3,0,pond_z],
             'pavilion':[pavilion_x,1,pond_z],
             'gallery_start':cd['portals'][0]['center_floor_xyz'],
             'gallery_end':cd['portals'][1]['center_floor_xyz']}
    # Lay a continuous tour on the actual navigable support cells. This links
    # the gallery ends and bridge banks rather than leaving unrelated lawn objects.
    tour_order=['moon_gate','bridge_west','bridge_east','pavilion','gallery_end','gallery_start','moon_gate']
    tour_legs=[]
    for first,last in zip(tour_order,tour_order[1:]):
        leg=walk_paths(m,targets[first],{'next':targets[last]})
        if not leg['all_targets_reached']:raise AssertionError('Garden tour disconnected')
        route=leg['routes_floor_xyz']['next']
        tour_legs.append({'from':first,'to':last,'floor_xyz':route})
        for x,y,z in route:
            if y!=0:continue
            for dx in (-1,0,1):
                for dz in (-1,0,1):
                    if m.get(x+dx,0,z+dz)==c['ground'] and all(m.get(x+dx,yy,z+dz)=='minecraft:air' for yy in (1,2)):
                        m.set(x+dx,0,z+dz,c['path'])
    plants=[];rng=np.random.default_rng(c['seed'])
    for _ in range(c['plant_clusters']*30):
        if len(plants)>=c['plant_clusters']:break
        x=int(rng.integers(-w+8,w-7));z=int(rng.integers(inner_z+8,d-7))
        if any(abs(x-a)+abs(z-b)<6 for a,b in plants):continue
        if not all(m.get(x+dx,0,z+dz)==c['ground'] and m.get(x+dx,1,z+dz)=='minecraft:air'
                   for dx in (-1,0,1) for dz in (-1,0,1)):continue
        for dx,dz in ((0,0),(1,0),(-1,0),(0,1),(0,-1)):
            m.set(x+dx,1,z+dz,'minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]')
        m.set(x,2,z,'minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]')
        plants.append([x,z])
    routes=walk_paths(m,start,targets)
    sight_screen=line_of_sight(m,[.5,2.62,-d+3.5],[.5,2.62,inner_z+3.5])
    sight_gate=line_of_sight(m,[gate_x+.5,2.62,inner_z-3+.5],[gate_x+.5,2.62,inner_z+4+.5])
    # Check that every retained water cell has a solid bed and no exposed side.
    water_mask=np.array([s.startswith('minecraft:water[') for s in m.palette])[m.blocks]
    water_cells=[];contained=True
    for y,z,x in zip(*np.where(water_mask)):
        xx,yy,zz=map(int,m.origin+[x,y,z]);water_cells.append([xx,yy,zz])
        if m.get(xx,yy-1,zz)=='minecraft:air':contained=False
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            if m.get(xx+dx,yy,zz+dz)=='minecraft:air':contained=False
    bridge_clear=all(m.get(x,top+y,pond_z+z)=='minecraft:air'
                     for x,top in bridge_design['deck_y_by_x'] for y in (1,2)
                     for z in range(-(c['bridge_width']//2),c['bridge_width']//2+1))
    checks={'all_destinations_reached':routes['all_targets_reached'],'moon_gate_three_wide':wall_design['clearance_three_wide'],
            'bridge_clearance':bridge_clear,'pond_has_water':len(water_cells)>0,'water_basin_contained':contained,
            'entrance_screen_blocks_straight_view':not sight_screen['visible'],
            'moon_gate_frames_open_view':sight_gate['visible']}
    if not all(checks.values()):raise AssertionError(checks)
    return m,{'schema':'chinese-garden-design/1','config':c,'checks':checks,'start_floor_xyz':start,'target_floors_xyz':targets,
              'routes':routes,'moon_wall':{**wall_design,'z':inner_z},'screen_z':screen_z,
              'tour_legs':tour_legs,'plant_cluster_centers_xz':plants,
              'bridge':{**bridge_design,'center_z':pond_z},'water_cells_xyz':water_cells,
              'sight_lines':{'screen':sight_screen,'moon_opening':sight_gate},'trees':trees,'rock_center_xz':list(rock_center),
              'placement_receipts':{'wall':wall_receipt,'bridge':bridge_receipt,'pavilion':pavilion_receipt,'gallery':corridor_receipt},
              'historical_reconstruction':False,'source_structure_reuse':False,
              'composition':'A constrained original pond garden. Screen, gate, bridge, waterside pavilion and L gallery are distinct connected spaces, not copied source geometry.'}


def export(config,out_dir,name='garden',render=True):
    m,d=make_garden(config)
    return export_scene(m,d,out_dir,name,'chinese-garden',render)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=Path);p.add_argument('--out-dir',type=Path,required=True)
    p.add_argument('--name',default='garden');p.add_argument('--no-render',action='store_true');a=p.parse_args()
    r=export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {},a.out_dir,a.name,not a.no_render)
    print(json.dumps({k:r[k] for k in ('name','dimensions_xyz','nonair_voxels')},ensure_ascii=False))
