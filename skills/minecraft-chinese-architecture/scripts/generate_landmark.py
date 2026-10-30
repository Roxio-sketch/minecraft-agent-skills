"""Original polygon pavilions, gates, extended roofs and structural teaching bays."""
from __future__ import annotations

import argparse
import copy
import json
import math
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_building import make_building
from generate_courtyard import walk_paths
from generate_regional import PALETTE, integer, roof_surface, stair
from roof_kernel import make_roof
from scene_output import export_scene
from voxel_model import Model


DEFAULTS={
 'polygon_pavilion':{'sides':8,'radius':12,'column_height':7,'roof_rise':6},
 'paifang':{'side_bay':8,'central_bay':10,'height':9},
 'city_gate':{'half_width':24,'half_depth':8,'wall_height':10,'arch_width':7,'arch_height':7},
 'roof_study':{'form':'lu_ding','half_x':16,'half_z':12,'arm_half_width':5,'rise':6,'flat_half':4,'fan_angle':120},
 'timber_frame':{'mode':'beam_lift','half_width':8,'half_depth':6,'column_height':8,'rise':6},
 'bracket_study':{'tiers':3,'column_height':6},
 'caisson':{'radius':9,'tiers':4,'base_height':6},
}


def config_for(user):
    if not isinstance(user,dict) or set(user)-{'schema','kind','parameters','evidence'}:raise ValueError('Unknown landmark configuration keys')
    kind=user.get('kind','polygon_pavilion')
    if kind not in DEFAULTS or user.get('schema','chinese-landmark/1')!='chinese-landmark/1':raise ValueError('Unknown landmark kind or schema')
    p=user.get('parameters',{})
    if not isinstance(p,dict) or set(p)-set(DEFAULTS[kind]):raise ValueError('Unknown parameters for '+kind)
    return {'schema':'chinese-landmark/1','kind':kind,'parameters':{**copy.deepcopy(DEFAULTS[kind]),**p},'evidence':user.get('evidence',[])}


def line(m,a,b,state):
    # Axis bridges at each rounded step make diagonal beams face-connected.
    a=np.array(a,int);b=np.array(b,int);last=a
    for t in np.linspace(0,1,int(np.max(np.abs(b-a)))*4+1):
        v=np.rint(a+t*(b-a)).astype(int)
        for axis in (0,1,2):
            step=1 if v[axis]>=last[axis] else -1
            while last[axis]!=v[axis]:m.set(*last.tolist(),state);last[axis]+=step
        m.set(*v.tolist(),state)


def gauge(x,z,sides,radius):
    if sides==0:return math.hypot(x,z)/radius
    return max(x*math.cos(-math.pi/2+i*2*math.pi/sides)+z*math.sin(-math.pi/2+i*2*math.pi/sides) for i in range(sides))/(radius*math.cos(math.pi/sides))


def polygon_pavilion(c):
    p=c['parameters'];sides=p['sides'];r=integer(p,'radius',8,24);h=integer(p,'column_height',5,12);rise=integer(p,'roof_rise',3,12)
    if type(sides) is not int or sides not in (0,4,6,8):raise ValueError('sides must be 0(circle),4,6,8')
    m=Model((-r-3,0,-r-4),(r+3,h+rise+7,r+3));m.box((-r-3,0,-r-4),(r+3,0,r+3),PALETTE['stone'])
    floor={(x,z) for x in range(-r,r+1) for z in range(-r,r+1) if gauge(x,z,sides,r-1)<=1}
    for x,z in floor:m.set(x,1,z,PALETTE['floor'])
    count=sides or 8;columns=[]
    for i in range(count):
        t=-math.pi/2+(i+.5)*2*math.pi/count;x,z=round((r-3)*math.cos(t)),round((r-3)*math.sin(t))
        columns.append([x,z]);m.box((x,2,z),(x,h+1,z),PALETTE['wood'])
    for a,b in zip(columns,columns[1:]+columns[:1]):line(m,[a[0],h+2,a[1]],[b[0],h+2,b[1]],PALETTE['beam'])
    heights={(x,z):h+4+round(rise*max(0,1-gauge(x,z,sides,r))**1.5) for x in range(-r,r+1) for z in range(-r,r+1) if gauge(x,z,sides,r)<=1}
    roof_surface(m,heights,PALETTE)
    for x,z in columns:
        for y in range(h+3,heights[x,z]):
            if m.get(x,y,z)=='minecraft:air':m.set(x,y,z,PALETTE['beam'])
    m.set(0,h+rise+5,0,'minecraft:gold_block')
    rail=[]
    for x,z in floor:
        if z<0 and abs(x)<=2:continue
        if any((x+dx,z+dz) not in floor for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]):
            if [x,z] not in columns:m.set(x,2,z,PALETTE['beam']);rail.append([x,2,z])
    front=min(z for x,z in floor if x==0);m.box((-1,0,front-1),(1,0,front-1),stair('minecraft:stone_brick_stairs','south'))
    targets={'center':[0,1,0]}
    for i,(x,z) in enumerate(columns):targets[f'bay_{i}']=[round(x*.6),1,round(z*.6)]
    return m,{'start_floor_xyz':[0,0,front-2],'target_floors_xyz':targets,'columns_xz':columns,'roof_footprint_cells':len(heights),
              'checks':{'continuous_columns':all(m.get(x,y,z)==PALETTE['wood'] for x,z in columns for y in range(2,h+2)),
                        'rail_positions_present':all(m.get(*v)!='minecraft:air' for v in rail)}}


def paifang(c):
    p=c['parameters'];side=integer(p,'side_bay',6,12);mid=integer(p,'central_bay',8,16);h=integer(p,'height',7,14)
    if side%2 or mid%2:raise ValueError('Even gate bay widths required')
    x0=mid//2;outer=x0+side;posts=[-outer,-x0,x0,outer]
    m=Model((-outer-3,0,-6),(outer+3,h+12,6));m.box((-outer-3,0,-6),(outer+3,0,6),PALETTE['stone'])
    for x in posts:m.box((x-1,1,-1),(x+1,1,1),PALETTE['stone'])
    m.box((-outer,h-1,0),(outer,h,0),PALETTE['beam'])
    roof_records=[]
    for center,span,base in [(-x0-side//2,side,h+1),(0,mid,h+5),(x0+side//2,side,h+1)]:
        roof,d=make_roof(half_x=span//2+1,half_z=3,base_y=base,rise=3,curve=1.4,uplift=0,form='overhanging_gable',trim=PALETTE['beam'])
        m.paste(roof,offset=[center,0,0]);roof_records.append({'center_x':center,'data':d})
    for x in posts:
        candidates=[q['data']['surface_y_rows_zx'][3][x-q['center_x']+q['data']['half_x']] for q in roof_records if abs(x-q['center_x'])<=q['data']['half_x']]
        top=max(candidates)-1;m.box((x,2,0),(x,top,0),PALETTE['wood'])
        m.box((x-1,h-2,0),(x+1,h-2,0),PALETTE['beam'])
    m.box((-3,h+1,-1),(3,h+3,-1),'minecraft:red_terracotta');m.box((-x0,h+4,0),(x0,h+4,0),PALETTE['beam'])
    centers=[-x0-side//2,0,x0+side//2]
    return m,{'start_floor_xyz':[0,0,-5],'target_floors_xyz':{f'bay_{i}_back':[x,0,5] for i,x in enumerate(centers)},
              'roof_components':roof_records,'post_x':posts,
              'checks':{'three_gate_clearances':all(m.get(x+dx,y,z)=='minecraft:air' for x in centers for dx in (-1,0,1) for y in (1,2,3) for z in range(-2,3))}}


def city_gate(c):
    p=c['parameters'];w=integer(p,'half_width',22,40);d=integer(p,'half_depth',8,12);h=integer(p,'wall_height',9,16)
    aw=integer(p,'arch_width',5,11);ah=integer(p,'arch_height',5,10)
    if aw%2!=1 or ah>h-3:raise ValueError('Odd arch width and at least three cover layers required')
    half=aw//2
    hall,detail,_=make_building({'kind':'pavilion','bays_x':[10],'bays_z':[6],'column_height':5,'platform_height':1,'platform_margin':2,'eave_overhang':2,'stair_width':3,'roof':{'form':'hip_and_gable','rise':4,'uplift':1,'gable_inset':2}})
    m=Model((-w-3,0,-d-5),(w+3,h+int(hall.maximum[1])+2,d+8));m.box((-w-3,0,-d-5),(w+3,0,d+8),PALETTE['stone'])
    m.box((-w,1,-d),(w,h,d),PALETTE['stone'])
    for x in range(-half,half+1):
        top=2+round((ah-2)*math.sqrt(1-(x/(half+.5))**2));m.box((x,1,-d),(x,top,d),'minecraft:air')
    for z in (-d,d):
        for x in range(-w,w+1):
            m.set(x,h+1,z,PALETTE['stone'])
            if x%4 in (0,1):m.set(x,h+2,z,PALETTE['stone'])
    for x in (-w,w):m.box((x,h+1,-d),(x,h+1,d),PALETTE['stone'])
    start_x=-w+2;stair_z=d+4;treads=[]
    for j in range(h+1):
        x=start_x+j
        if j>1:m.box((x,1,stair_z-1),(x,j-1,stair_z+1),PALETTE['stone'])
        m.box((x,j,stair_z-1),(x,j,stair_z+1),stair('minecraft:stone_brick_stairs','east'))
        treads.extend([[x,j,z] for z in range(stair_z-1,stair_z+2)])
    end=start_x+h;m.box((end,h,d),(end+2,h,stair_z+1),PALETTE['stone']);m.box((end,h+1,d),(end+2,h+2,d),'minecraft:air')
    allowed=[]
    for yy,zz,xx in zip(*np.where(hall.blocks!=0)):
        x,y,z=(hall.origin+np.array([xx,yy,zz])).tolist();y+=h;incoming=hall.palette[int(hall.blocks[yy,zz,xx])];old=m.get(x,y,z)
        if old!='minecraft:air' and old!=incoming:
            if old!=PALETTE['stone'] or y!=h:raise ValueError('Unexpected gatehouse overlap')
            allowed.append([x,y,z])
    receipt=m.paste(hall,offset=[0,h,0],conflict='replace')
    return m,{'start_floor_xyz':[0,0,-d-4],'target_floors_xyz':{'inside':[0,0,d+3],'gatehouse':[0,h+1,0],
              'wall_west':[-w+1,h,0],'wall_east':[w-1,h,0]},'allowed_platform_replacements_xyz':allowed,'paste_receipt':receipt,
              'checks':{'arch_cover':all(m.get(0,y,0)!='minecraft:air' for y in range(ah+1,h+1)),
                        'three_wide_arch_path_clear':all(m.get(x,y,z)=='minecraft:air' for x in (-1,0,1) for y in (1,2) for z in range(-d,d+1)),
                        'access_stair_headroom':all(m.get(x,y+k,z)=='minecraft:air' for x,y,z in treads for k in (1,2))}}


def roof_study(c):
    p=c['parameters'];hx=integer(p,'half_x',8,32);hz=integer(p,'half_z',8,32);arm=integer(p,'arm_half_width',3,8)
    rise=integer(p,'rise',3,14);flat=integer(p,'flat_half',2,10);angle=integer(p,'fan_angle',60,180);form=p['form']
    if form not in ('lu_ding','cross','t_shape','fan') or arm>=min(hx,hz) or flat>=min(hx,hz):raise ValueError('Invalid roof form or plateau/arm width')
    heights={}
    for x in range(-hx,hx+1):
        for z in range(-hz,hz+1):
            if form=='lu_ding':
                t=min(1,(hx-abs(x))/(hx-flat),(hz-abs(z))/(hz-flat));height=rise*max(0,t)**1.5
            elif form in ('cross','t_shape'):
                in_horizontal=abs(z)<=arm and (form=='cross' or z>=-arm)
                in_vertical=abs(x)<=arm and (form=='cross' or z>=0)
                if not(in_horizontal or in_vertical):continue
                height=max(rise*(1-abs(z)/arm)**1.5 if in_horizontal else 0,rise*(1-abs(x)/arm)**1.5 if in_vertical else 0)
            else:
                radius=math.hypot(x,z);theta=math.degrees(math.atan2(x,z))
                if not flat<=radius<=min(hx,hz) or abs(theta)>angle/2:continue
                t=(radius-flat)/(min(hx,hz)-flat);height=rise*(1-abs(2*t-1))
            heights[x,z]=2+round(height)
    m=Model((-hx,0,-hz),(hx,rise+5,hz));roof_surface(m,heights,PALETTE)
    if form=='lu_ding':
        for x in range(-flat,flat+1):
            for z in range(-flat,flat+1):
                if abs(x)==flat or abs(z)==flat:m.set(x,rise+3,z,PALETTE['beam'])
    return m,{'surface_xyz':[[x,y,z] for (x,z),y in heights.items()],
              'checks':{'one_connected_shell':label(m.blocks!=0)[1]==1,'whole_footprint_covered':all(m.get(x,y,z)!='minecraft:air' for (x,z),y in heights.items())},
              'limits':'Roof shell only; max-envelope junctions are original morphology, without framing, flashing or measured valley construction.'}


def timber_frame(c):
    p=c['parameters'];hx=integer(p,'half_width',6,12);hz=integer(p,'half_depth',4,10);h=integer(p,'column_height',6,12);rise=integer(p,'rise',4,8)
    mode=p['mode']
    if mode not in ('beam_lift','post_tie'):raise ValueError('Unknown frame study mode')
    m=Model((-hx-2,0,-hz-2),(hx+2,h+rise+3,hz+2));m.box((-hx-2,0,-hz-2),(hx+2,0,hz+2),PALETTE['stone']);purlins=[]
    if mode=='beam_lift':
        widths=[hx,hx*2//3,hx//3,0];ys=[h,h+rise//3,h+2*rise//3,h+rise]
        for z in (-hz,0,hz):
            for x in (-hx,hx):m.box((x,1,z),(x,h,z),PALETTE['wood'])
            for i,(width,y) in enumerate(zip(widths,ys)):
                m.box((-width,y,z),(width,y,z),PALETTE['beam'])
                if i:
                    for x in set([-width,width]):m.box((x,ys[i-1]+1,z),(x,y,z),PALETTE['wood'])
        purlins=[(x,y+1) for w,y in zip(widths,ys) for x in set([-w,w])]
    else:
        xs=sorted(set([-hx,-hx//2,0,hx//2,hx]));purlins=[(x,h+round(rise*(1-abs(x)/hx))+1) for x in xs]
        for z in (-hz,0,hz):
            for x,y in purlins:m.box((x,1,z),(x,y-1,z),PALETTE['wood'])
            for y in (h//2,h-1):m.box((-hx,y,z),(hx,y,z),PALETTE['beam'])
    for x,y in purlins:m.box((x,y,-hz-1),(x,y,hz+1),'minecraft:spruce_log[axis=z]')
    return m,{'purlins_xy':purlins,'checks':{'all_frame_geometry_connected':label(m.blocks!=0)[1]==1},
              'limits':'Comparison of beam-supported short posts and through-tied posts; no measured joints, historic dimensions or structural analysis.'}


def bracket_study(c):
    p=c['parameters'];tiers=integer(p,'tiers',1,4);h=integer(p,'column_height',4,10);r=tiers+2
    m=Model((-r,0,-r),(r,h+2*tiers+3,r));m.box((-r,0,-r),(r,0,r),PALETTE['stone']);m.box((0,1,0),(0,h,0),PALETTE['wood'])
    layers=[]
    for i in range(tiers):
        y=h+1+i*2;span=i+1;m.box((-1,y,-1),(1,y,1),PALETTE['wood'])
        m.box((-span,y+1,0),(span,y+1,0),PALETTE['beam']);m.box((0,y+1,-span),(0,y+1,span),PALETTE['beam'])
        for x,z in [(-span,0),(span,0),(0,-span),(0,span)]:m.set(x,y+2,z,PALETTE['wood'])
        layers.append({'tier':i+1,'beam_y':y+1,'half_span':span})
    m.box((-r,h+2*tiers+1,0),(r,h+2*tiers+1,0),PALETTE['beam'])
    return m,{'layers':layers,'checks':{'single_supported_assembly':label(m.blocks!=0)[1]==1},
              'limits':'Original stacked bracket teaching diagram; tiers do not encode Song puzuo or Qing cai counts, and no mortise-and-tenon geometry is present.'}


def caisson(c):
    p=c['parameters'];r=integer(p,'radius',7,15);tiers=integer(p,'tiers',2,6);h=integer(p,'base_height',5,10)
    if r-tiers<3:raise ValueError('Enough crown radius required')
    m=Model((-r-1,0,-r-1),(r+1,h+tiers+2,r+1));m.box((-r-1,0,-r-1),(r+1,0,r+1),PALETTE['stone'])
    for x in (-r,r):
        for z in (-r,r):m.box((x,1,z),(x,h,z),PALETTE['wood'])
    for x in range(-r,r+1):
        for z in range(-r,r+1):
            q=max(abs(x),abs(z))
            if q==r:m.set(x,h,z,PALETTE['beam'])
            y=h+min(tiers,r-q)
            m.set(x,y,z,'minecraft:cyan_terracotta' if (x+z)%3 else 'minecraft:orange_terracotta')
            if q>=r-tiers:m.set(x,y-1,z,PALETTE['beam'])
    return m,{'checks':{'ceiling_assembly_connected':label(m.blocks!=0)[1]==1,
                         'clear_center_volume':all(m.get(0,y,0)=='minecraft:air' for y in range(1,h+tiers))},
              'limits':'Square stepped inward ceiling diagram on four display posts, no carved imagery or historic caisson reconstruction.'}


def make_landmark(user):
    c=config_for(user);m,d=globals()[c['kind']](c)
    if 'start_floor_xyz' in d:
        routes=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz']);d['routes']=routes;d['checks']['outside_to_targets']=routes['all_targets_reached']
    if not all(d['checks'].values()):raise AssertionError({'checks':d['checks'],'unreachable':d.get('routes',{}).get('unreachable')})
    d.update(schema='chinese-landmark-design/1',config=c,historical_reconstruction=False,source_structure_reuse=False)
    return m,d


def export(user,out_dir,name='landmark',render=True):
    m,d=make_landmark(user);return export_scene(m,d,out_dir,name,'chinese-landmark',render)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--kind',choices=DEFAULTS);p.add_argument('--config',type=Path)
    p.add_argument('--out-dir',type=Path,required=True);p.add_argument('--name',default='landmark');p.add_argument('--no-render',action='store_true');a=p.parse_args()
    c=json.loads(a.config.read_text('utf-8-sig')) if a.config else {}
    if a.kind:c['kind']=a.kind
    r=export(c,a.out_dir,a.name,not a.no_render);print(json.dumps({k:r[k] for k in ('name','dimensions_xyz','nonair_voxels')},ensure_ascii=False))
