"""Continuous ceremonial stair flights; floor XYZ names the supporting block.

One rising edge per riser. The rest of a deep tread is FULL flooring, never a
second stair at the same elevation. Checks sample vanilla shapes at half cells.
"""
from __future__ import annotations
import numpy as np
from voxel_model import Model, split_state, join_state, vector3
from block_shapes import shape_for

DIRECTIONS={'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}
DEFAULT={
 'schema':'chinese-staircase/1','start_floor_xyz':[0,0,0],'facing':'north',
 'width':9,'rises':[8,8],'tread_depth':3,'landing_depth':5,
 'bottom_landing':3,'top_landing':3,'foundation_y':None,'headroom':3,
 'palette':{'floor':'minecraft:smooth_quartz','foundation':'minecraft:stone_bricks',
            'stairs':'minecraft:smooth_quartz_stairs'},
 'shape_aliases':{},'solid_blocks':[],'evidence':[]}

def config_for(user):
    if not isinstance(user,dict) or set(user)-set(DEFAULT):raise ValueError('Unknown staircase keys')
    c={**DEFAULT,**user,'palette':{**DEFAULT['palette'],**user.get('palette',{})}}
    if c['schema']!='chinese-staircase/1' or c['facing'] not in DIRECTIONS:raise ValueError('Invalid staircase schema or facing')
    start=vector3(c['start_floor_xyz'],'start_floor_xyz');c['start_floor_xyz']=start.tolist()
    if not isinstance(c['rises'],list) or not c['rises'] or any(type(r)!=int or r<1 for r in c['rises']):raise ValueError('rises must be positive integer flights')
    if sum(c['rises'])>256:raise ValueError('Total rise exceeds 256 blocks')
    for k,lo,hi in [('width',1,511),('tread_depth',1,32),('landing_depth',1,256),('bottom_landing',1,256),('top_landing',1,256),('headroom',2,16)]:
        if type(c[k])!=int or not lo<=c[k]<=hi:raise ValueError(f'{k} must be integer {lo}..{hi}')
    if set(c['palette'])!=set(DEFAULT['palette']):raise ValueError('Unknown palette role')
    for role,state in c['palette'].items():
        base,p=split_state(state)
        if role=='stairs' and (p or not base.endswith('_stairs')):raise ValueError('stairs needs a base stair ID')
        if role!='stairs' and (base.endswith((':air','_stairs','_slab','_fence','_wall','_carpet')) or base in ('minecraft:water','minecraft:lava')):raise ValueError('floor and foundation need full supporting blocks')
    if not isinstance(c['shape_aliases'],dict):raise ValueError('shape_aliases must be an object')
    for src,dst in c['shape_aliases'].items():
        if split_state(src)[1] or split_state(dst)[1] or not dst.startswith('minecraft:'):raise ValueError('Shape aliases map base IDs to verified vanilla shape families')
    if not isinstance(c['solid_blocks'],list):raise ValueError('solid_blocks must explicitly list verified full-cube base IDs')
    for state in c['solid_blocks']:
        base,props=split_state(state)
        if props or base in ('minecraft:air','minecraft:water','minecraft:lava') or base.endswith(('_stairs','_slab','_fence','_wall','_pane','_carpet')):
            raise ValueError('solid_blocks cannot declare partial blocks or fluids as support')
    stairs=c['palette']['stairs']
    if not stairs.startswith('minecraft:'):
        alias=c['shape_aliases'].get(stairs,'')
        if not alias.startswith('minecraft:') or not alias.endswith('_stairs'):raise ValueError('Mod stairs require an explicit verified vanilla stair shape_alias')
    if c['foundation_y'] is None:c['foundation_y']=int(start[1])
    if type(c['foundation_y'])!=int or c['foundation_y']>int(start[1]):raise ValueError('foundation_y must not exceed bottom floor')
    return c

def row_position(c,t,lane):
    x,y,z=c['start_floor_xyz'];dx,dz=DIRECTIONS[c['facing']]
    return x+dx*t-dz*lane,z+dz*t+dx*lane

def top_landing_box(d,depth=2,cross_span=None):
    """A turn landing starts at the first flat row and extends away from risers.

    cross_span is an optional absolute inclusive interval on the transverse
    axis. It can join another flight without putting a full block over the
    incoming last stair. The caller still verifies the final composition.
    """
    c=d['config']
    if type(depth) is not int or not 1<=depth<=c['top_landing']:
        raise ValueError('Landing depth must stay within the existing top landing')
    rows=[r for r in d['rows'] if r['kind']=='top_landing']
    if len(rows)<depth:raise ValueError('Incomplete staircase top-landing rows')
    points=[row_position(c,t,lane) for t in (rows[0]['t'],rows[depth-1]['t']) for lane in d['lanes']]
    x0=min(x for x,z in points);x1=max(x for x,z in points)
    z0=min(z for x,z in points);z1=max(z for x,z in points)
    if cross_span is not None:
        if not isinstance(cross_span,(list,tuple)) or len(cross_span)!=2 or any(type(v) is not int for v in cross_span) or cross_span[0]>cross_span[1]:
            raise ValueError('cross_span is an inclusive absolute integer interval')
        if c['facing'] in ('north','south'):
            if cross_span[0]>x0 or cross_span[1]<x1:raise ValueError('Crossing must contain the incoming flight width')
            x0,x1=cross_span
        else:
            if cross_span[0]>z0 or cross_span[1]<z1:raise ValueError('Crossing must contain the incoming flight width')
            z0,z1=cross_span
    y=d['target_floors_xyz']['upper'][1]
    return {'min':[x0,y,z0],'max':[x1,y,z1],'floor_y':y,'depth':depth}

def make_staircase(user):
    c=config_for(user);rows=[];base=c['start_floor_xyz'][1];t=0;y=base
    for t in range(1-c['bottom_landing'],1):rows.append({'t':t,'y':y,'kind':'bottom_landing'})
    t=1
    for f,rise in enumerate(c['rises']):
        for step in range(rise):
            y+=1;rows.append({'t':t,'y':y,'kind':'riser'});t+=1
            for _ in range(c['tread_depth']-1):rows.append({'t':t,'y':y,'kind':'tread'});t+=1
        if f<len(c['rises'])-1:
            for _ in range(c['landing_depth']):rows.append({'t':t,'y':y,'kind':'mid_landing'});t+=1
    exit_t=t
    for _ in range(c['top_landing']):rows.append({'t':t,'y':y,'kind':'top_landing'});t+=1
    lanes=range(-(c['width']//2),c['width']-c['width']//2)
    ends=[row_position(c,t,u) for t in (rows[0]['t'],rows[-1]['t']) for u in (lanes.start,lanes.stop-1)]
    m=Model((min(p[0] for p in ends),c['foundation_y'],min(p[1] for p in ends)),
            (max(p[0] for p in ends),y+c['headroom'],max(p[1] for p in ends)))
    stair=c['palette']['stairs']+f'[facing={c["facing"]},half=bottom,shape=straight,waterlogged=false]'
    for row in rows:
        a=row_position(c,row['t'],lanes.start);b=row_position(c,row['t'],lanes.stop-1)
        lo=(min(a[0],b[0]),c['foundation_y'],min(a[1],b[1]));hi=(max(a[0],b[0]),row['y'],max(a[1],b[1]))
        m.box(lo,hi,c['palette']['foundation'])
        m.box((lo[0],row['y'],lo[2]),hi,stair if row['kind']=='riser' else c['palette']['floor'])
    ex,ez=row_position(c,exit_t,0)
    d={'config':c,'rows':rows,'lanes':[lanes.start,lanes.stop-1],
       'start_floor_xyz':c['start_floor_xyz'],'target_floors_xyz':{'upper':[ex,y,ez]},
       'riser_count':sum(c['rises']),'run_length':rows[-1]['t']-rows[0]['t']+1,
       'source_structure_reuse':False,'historical_reconstruction':False,
       'verification_scope':'Half-cell surface, support and voxel clearance under explicit vanilla-shape aliases; no live player traversal',
       'evidence':c['evidence']}
    d['checks']=verify_staircase(m,d)
    if not all(d['checks'].values()):raise AssertionError(d['checks'])
    return m,d

def verify_staircase(m,d):
    """Inspect actual geometry, so reversed/same-level stairs and blockers fail."""
    c=d['config'];dx,dz=DIRECTIONS[c['facing']]
    # Inspect supporting columns as array slices. Calling Model.get once for
    # every buried support voxel made monumental flights unnecessarily slow.
    from tier_junctions import full_cube_flags
    solids=[c['palette']['floor'].split('[')[0],c['palette']['foundation'].split('[')[0],*c.get('solid_blocks',[])]
    support_ids=full_cube_flags(m,solids,c['shape_aliases'])
    ox,oy,oz=(int(v) for v in m.origin);support_bottom=c['foundation_y']-oy
    checks={'surface_continuous_without_dips':True,'half_block_max_rise':True,
      'flat_treads_and_landings':True,'headroom_clear':True,'continuous_support':True,
      'endpoints_match_floor_heights':True}
    for lane in range(d['lanes'][0],d['lanes'][1]+1):
        samples=[]
        for row in d['rows']:
            x,z=row_position(c,row['t'],lane);y=row['y']
            ix,iy,iz=x-ox,y-oy,z-oz
            if not (0<=ix<m.blocks.shape[2] and 0<=iz<m.blocks.shape[1] and 0<=support_bottom<=iy and iy+c['headroom']<m.blocks.shape[0]):
                raise ValueError('Stair verification volume lies outside the supplied model')
            state=m.palette[int(m.blocks[iy,iz,ix])]
            base,props=split_state(state)
            alias=join_state(c['shape_aliases'].get(base,base),props)
            shape,_=shape_for(alias)
            heights=[]
            for along in (-.25,.25):
                xx=int((.5+dx*along)*16);zz=int((.5+dz*along)*16)
                col=shape[:,zz,xx];ys=np.flatnonzero(col)
                height=float(y)+(int(ys[-1])+1)/16 if len(ys) else float('-inf')
                heights.append(height);samples.append(height)
            if row['kind']!='riser' and heights!=[float(y+1)]*2:checks['flat_treads_and_landings']=False
            if np.any(m.blocks[iy+1:iy+c['headroom']+1,iz,ix]):checks['headroom_clear']=False
            if not np.all(support_ids[m.blocks[support_bottom:iy,iz,ix]]):checks['continuous_support']=False
        delta=np.diff(samples)
        if not np.isfinite(samples).all() or np.any(delta<0):checks['surface_continuous_without_dips']=False
        if np.any(delta>.5):checks['half_block_max_rise']=False
        if samples[0]!=c['start_floor_xyz'][1]+1 or samples[-1]!=d['target_floors_xyz']['upper'][1]+1:checks['endpoints_match_floor_heights']=False
    return checks
