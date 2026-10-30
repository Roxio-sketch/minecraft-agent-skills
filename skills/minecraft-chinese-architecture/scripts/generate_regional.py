"""Original spatial prototypes informed by separately documented regional studies."""
from __future__ import annotations

import argparse
import copy
import math
from pathlib import Path
import json

import numpy as np

from generate_building import make_building, verify as verify_building
from generate_courtyard import walk_paths
from scene_output import export_scene
from voxel_model import Model, split_state, join_state


PARAMETERS={
 'stilt':{'lift':4,'bays_x':[6,8,6],'bays_z':[6,6],'column_height':6},
 'yurt':{'radius':11,'wall_height':4,'roof_rise':5,'crown_radius':2},
 'cave':{'chambers':3,'chamber_width':7,'pier_width':3,'depth':14,'vault_height':7},
 'tianjing':{'half_x':17,'half_z':15,'court_half_x':6,'court_half_z':5,'wall_height':7,'roof_rise':4},
 'tulou':{'shape':'round','radius':28,'court_radius':14,'storeys':3,'storey_height':6,'roof_rise':4},
}
PALETTE={'earth':'minecraft:packed_mud','stone':'minecraft:stone_bricks','wall':'minecraft:white_concrete',
 'wood':'minecraft:stripped_spruce_log[axis=y]','floor':'minecraft:spruce_planks','beam':'minecraft:dark_oak_planks',
 'stairs':'minecraft:spruce_stairs','slab':'minecraft:deepslate_tile_slab','roof':'minecraft:deepslate_tiles',
 'roof_stairs':'minecraft:deepslate_tile_stairs','cloth':'minecraft:white_wool','cloth_trim':'minecraft:orange_wool',
 'glass':'minecraft:light_gray_stained_glass','rail':'minecraft:spruce_fence'}


def config_for(user):
    if not isinstance(user,dict) or set(user)-{'schema','kind','parameters','palette','evidence'}:raise ValueError('Unknown regional configuration keys')
    kind=user.get('kind','stilt')
    if kind not in PARAMETERS or user.get('schema','chinese-regional/1')!='chinese-regional/1':raise ValueError('Unknown regional kind or schema')
    raw=user.get('parameters',{});pal=user.get('palette',{})
    if not isinstance(raw,dict) or set(raw)-set(PARAMETERS[kind]):raise ValueError('Unknown parameters for '+kind)
    if not isinstance(pal,dict) or set(pal)-set(PALETTE):raise ValueError('Unknown palette roles')
    c={'schema':'chinese-regional/1','kind':kind,'parameters':{**copy.deepcopy(PARAMETERS[kind]),**raw},
       'palette':{**PALETTE,**pal},'evidence':user.get('evidence',[])}
    if not isinstance(c['evidence'],list):raise ValueError('evidence must be a list')
    for role,state in c['palette'].items():
        base,props=split_state(state)
        if base.endswith(':air'):raise ValueError('Palette cannot contain air')
        suffix={'stairs':'_stairs','roof_stairs':'_stairs','slab':'_slab','rail':'_fence'}.get(role)
        if suffix and (props or not base.endswith(suffix)):raise ValueError('Invalid palette.'+role)
        c['palette'][role]=join_state(base,props)
    return c


def integer(p,key,lo,hi):
    v=p[key]
    if type(v) is not int or not lo<=v<=hi:raise ValueError(f'{key} must be integer {lo}..{hi}')
    return v


def stair(state,facing):return state+'[facing='+facing+',half=bottom,shape=straight,waterlogged=false]'


def roof_surface(m,heights,p):
    """Fill one layer below the lower neighbor to keep stepped roofs connected."""
    for (x,z),y in heights.items():
        near={f:heights[x+dx,z+dz] for f,dx,dz in [('north',0,-1),('east',1,0),('south',0,1),('west',-1,0)] if (x+dx,z+dz) in heights}
        bottom=min([y,*near.values()])-1;m.box((x,bottom,z),(x,y,z),p['roof'])
        high=[f for f,h in near.items() if h>y]
        if len(high)==1:m.set(x,y,z,stair(p['roof_stairs'],high[0]))


def stilt(c):
    p=c['parameters'];pal=c['palette'];lift=integer(p,'lift',2,8)
    part,d,bc=make_building({'kind':'hall','bays_x':p['bays_x'],'bays_z':p['bays_z'],'column_height':p['column_height'],
        'platform_height':1,'platform_margin':2,'eave_overhang':2,'stair_width':3,
        'roof':{'form':'overhanging_gable','rise':4,'uplift':0},
        'palette':{'foundation':pal['beam'],'floor':pal['floor'],'column':pal['wood'],'beam':pal['beam'],
                   'wall':pal['floor'],'window':pal['glass'],'roof':pal['roof'],'roof_stairs':pal['roof_stairs'],'roof_slab':pal['slab'],'access_stairs':pal['stairs']}})
    verify_building(part,d,bc)
    lo=part.origin.copy();hi=part.maximum.copy();lo[1]=0;lo[2]-=lift+2;hi[1]+=lift
    m=Model(lo,hi);m.box((int(lo[0]),0,int(lo[2])),(int(hi[0]),0,int(hi[2])),'minecraft:grass_block[snowy=false]')
    m.paste(part,offset=[0,lift,0]);supports=[]
    for x,z in d['columns_xz']:
        m.box((x,1,z),(x,lift-1,z),pal['wood']);supports.append([x,z])
    aisle=d['doorway']['aisle_x'];front=int(part.origin[2])
    for j in range(lift+1):
        z=front-lift+j
        if j:m.box((aisle-1,1,z),(aisle+1,j,z),pal['beam'])
        m.box((aisle-1,j,z),(aisle+1,j,z),pal['floor'] if j==0 else stair(pal['stairs'],'south'))
    px,pz,fy=d['platform_half_x'],d['platform_half_z'],lift+1
    guards={(x,z) for x in range(-px,px+1) for z in (-pz,pz) if not(z==-pz and abs(x-aisle)<=2)}
    guards|={(x,z) for x in (-px,px) for z in range(-pz+1,pz)}
    for x,z in guards:
        props={'waterlogged':'false'}
        for facing,dx,dz in [('north',0,-1),('east',1,0),('south',0,1),('west',-1,0)]:props[facing]=str((x+dx,z+dz) in guards).lower()
        m.set(x,fy+1,z,join_state(pal['rail'],props))
    start=[aisle,0,front-lift-1];door=d['doorway']
    targets={'interior':[aisle,door['floor_y']+lift,door['z']+2]}
    return m,{'start_floor_xyz':start,'target_floors_xyz':targets,'support_xz':supports,
              'checks':{'supports_to_ground':all(m.get(x,y,z)==pal['wood'] for x,z in supports for y in range(1,lift)),
                        'underfloor_open':m.get(1,1,1)=='minecraft:air',
                        'balcony_guard_positions':all(m.get(x,fy+1,z).split('[')[0]==pal['rail'] for x,z in guards)},'component_design':d}


def yurt(c):
    p=c['parameters'];pal=c['palette'];r=integer(p,'radius',8,18);h=integer(p,'wall_height',3,6)
    rise=integer(p,'roof_rise',3,8);crown=integer(p,'crown_radius',1,3)
    m=Model((-r-3,0,-r-5),(r+3,h+rise+3,r+3))
    m.box((-r-3,0,-r-5),(r+3,0,r+3),'minecraft:grass_block[snowy=false]')
    disk={(x,z) for x in range(-r,r+1) for z in range(-r,r+1) if x*x+z*z<=r*r}
    boundary={(x,z) for x,z in disk if any((x+dx,z+dz) not in disk for dx,dz in [(0,1),(0,-1),(1,0),(-1,0)])}
    for x,z in disk:m.set(x,0,z,pal['floor'])
    for x,z in boundary:
        m.box((x,1,z),(x,h,z),pal['cloth']);m.set(x,1,z,pal['cloth_trim']);m.set(x,h,z,pal['beam'])
        if (x+z)%5==0:m.box((x,1,z),(x,h,z),pal['wood'])
    heights={}
    for x in range(-r-1,r+2):
        for z in range(-r-1,r+2):
            dist=math.hypot(x,z)
            if crown<=dist<=r+1:heights[x,z]=h+1+round(rise*(r+1-dist)/(r+1-crown))
    roof_surface(m,heights,{**pal,'roof':pal['cloth'],'roof_stairs':'minecraft:smooth_quartz_stairs'})
    # The cloth shell uses whole wool blocks; no stone stair disguise remains.
    for (x,z),height in heights.items():
        m.set(x,height,z,pal['cloth_trim'] if abs(x)<=1 or abs(z)<=1 else pal['cloth'])
    for x in range(-crown,crown+1):
        for z in range(-crown,crown+1):
            if x*x+z*z<=crown*crown:m.set(x,h+rise+1,z,pal['glass'] if x*x+z*z<(crown-0.5)**2 else pal['beam'])
    for x in (-crown,crown):m.box((x,1,0),(x,h+rise,0),pal['wood'])
    m.box((-1,1,-r-1),(1,3,-r+2),'minecraft:air')
    for x in (-2,2):m.box((x,1,-r),(x,3,-r),pal['wood'])
    m.box((-2,4,-r),(2,4,-r),pal['beam']);m.box((-1,0,-r-4),(1,0,-r+2),pal['stone'])
    return m,{'start_floor_xyz':[0,0,-r-4],'target_floors_xyz':{'center':[0,0,0],'left':[-r+3,0,0],'right':[r-3,0,0]},
              'checks':{'closed_crown':m.get(0,h+rise+1,0)==pal['glass'],'wood_felt_roles':True},
              'construction_notes':'Timber and wool game abstraction with a glazed crown; not a measured portable ger frame.'}


def cave(c):
    p=c['parameters'];pal=c['palette'];n=integer(p,'chambers',1,5)
    width=integer(p,'chamber_width',5,9);pier=integer(p,'pier_width',3,6);depth=integer(p,'depth',8,24);height=integer(p,'vault_height',5,9)
    if n%2!=1 or width%2!=1:raise ValueError('Odd chamber count and width required')
    step=width+pier;centers=[(i-n//2)*step for i in range(n)];half=width//2;extent=centers[-1]+half+pier
    m=Model((-extent-2,-1,-9),(extent+2,height+5,depth+4));m.box((-extent-2,-1,-9),(extent+2,0,depth+4),pal['stone'])
    m.box((-extent,1,-1),(extent,height+3,depth+3),pal['earth'])
    m.box((-extent,height+4,0),(extent,height+4,depth+3),'minecraft:grass_block[snowy=false]')
    excavated=0;vaults=[]
    for cx in centers:
        profile=[]
        for dx in range(-half,half+1):
            top=2+round((height-2)*math.sqrt(max(0,1-(dx/(half+.5))**2)));profile.append(top)
            for z in range(0,depth+1):
                m.box((cx+dx,1,z),(cx+dx,top,z),'minecraft:air');excavated+=top
            m.box((cx+dx,1,-1),(cx+dx,top,-1),pal['glass'])
            m.set(cx+dx,top+1,-1,pal['stone'])
        for dx in (-half-1,half+1):m.box((cx+dx,1,-1),(cx+dx,height,-1),pal['stone'])
        m.box((cx-1,1,-1),(cx+1,3,0),'minecraft:air')
        for dx in (-2,2):m.box((cx+dx,1,-1),(cx+dx,3,-1),pal['wood'])
        m.box((cx-2,4,-1),(cx+2,4,-1),pal['beam'])
        m.set(cx,height-1,depth,'minecraft:sea_lantern')
        vaults.append({'center_x':cx,'top_y_by_x':profile,'inner_depth':depth+1})
    targets={f'chamber_{i+1}':[cx,0,depth-2] for i,cx in enumerate(centers)}
    checks={'vault_cover_at_least_three':all(m.get(v['center_x']+dx,v['top_y_by_x'][dx+half]+j,depth//2)!='minecraft:air' for v in vaults for dx in range(-half,half+1) for j in (1,2,3)),
            'separate_chambers':all(m.get((a+b)//2,2,depth//2)==pal['earth'] for a,b in zip(centers,centers[1:]))}
    return m,{'start_floor_xyz':[0,0,-8],'target_floors_xyz':targets,'checks':checks,'vaults':vaults,'explicit_excavated_cells':excavated,
              'construction_notes':'A new, fully specified soil block is carved; no implicit air or source-world excavation semantics.'}


def tianjing(c):
    p=c['parameters'];pal=c['palette'];hx=integer(p,'half_x',14,26);hz=integer(p,'half_z',13,24)
    ix=integer(p,'court_half_x',4,10);iz=integer(p,'court_half_z',4,9);h=integer(p,'wall_height',6,10);rise=integer(p,'roof_rise',3,6)
    if hx-ix<8 or hz-iz<8:raise ValueError('At least eight cells between court and exterior walls')
    m=Model((-hx-2,-2,-hz-4),(hx+2,h+rise+5,hz+2));m.box((-hx-2,-2,-hz-4),(hx+2,0,hz+2),pal['stone'])
    for z in range(-hz,hz+1):
        for x in range(-hx,hx+1):
            if abs(x)>ix or abs(z)>iz:m.set(x,0,z,pal['floor'])
    for x in (-hx,hx):m.box((x,1,-hz),(x,h,hz),pal['wall'])
    for z in (-hz,hz):m.box((-hx,1,z),(hx,h,z),pal['wall'])
    for x in (-ix,ix):m.box((x,1,-iz),(x,h-2,iz),pal['wall'])
    for z in (-iz,iz):m.box((-ix,1,z),(ix,h-2,z),pal['wall'])
    for x in (-ix,ix):m.box((x,1,-1),(x,3,1),'minecraft:air')
    for z in (-iz,iz):m.box((-1,1,z),(1,3,z),'minecraft:air')
    m.box((-1,1,-hz),(1,4,-hz),'minecraft:air')
    for x in (-ix,ix):
        for z in (-iz,iz):m.box((x,1,z),(x,h,z),pal['wood'])
    heights={}
    for x in range(-hx-1,hx+2):
        for z in range(-hz-1,hz+2):
            if abs(x)<ix-1 and abs(z)<iz-1:continue
            u=max((abs(x)-(ix-1))/(hx-ix+2),(abs(z)-(iz-1))/(hz-iz+2))
            heights[x,z]=h+round(rise*max(0,1-abs(2*u-1)))
    roof_surface(m,heights,pal)
    caps=[]
    for x in (-hx,hx):
        for z in range(-hz,hz+1):
            top=h+2+(rise if abs(z)<hz//3 else rise//2 if abs(z)<2*hz//3 else 0)
            m.box((x,h,z),(x,top,z),pal['wall']);m.set(x,top+1,z,pal['roof']);caps.append([x,top+1,z])
    water=[]
    for x in range(-ix+2,ix-1):
        for z in range(-iz+2,iz-1):
            if (abs(x)==ix-2 or abs(z)==iz-2) and abs(x)>1 and abs(z)>1:
                m.set(x,0,z,'minecraft:air');m.set(x,-1,z,'minecraft:water[level=0]');water.append([x,-1,z])
    targets={'court':[0,0,0],'rear':[0,0,(iz+hz)//2],'front':[0,0,-(iz+hz)//2],
             'west':[-(ix+hx)//2,0,0],'east':[(ix+hx)//2,0,0]}
    checks={'court_open_to_sky':all(m.get(0,y,0)=='minecraft:air' for y in range(1,int(m.maximum[1])+1)),
            'crossing_over_drain_dry':all(m.get(0,0,z)!='minecraft:air' for z in range(-iz,iz+1)),
            'stepped_gable_caps':all(m.get(*v)==pal['roof'] for v in caps)}
    return m,{'start_floor_xyz':[0,0,-hz-3],'target_floors_xyz':targets,'checks':checks,'water_cells_xyz':water,'gable_caps_xyz':caps,
              'construction_notes':'Original four-wing court and stepped firewall silhouette; no measured historic timber frame or drainage simulation.'}


def tulou(c):
    p=c['parameters'];pal=c['palette'];r=integer(p,'radius',24,38);inner=integer(p,'court_radius',10,22)
    levels=integer(p,'storeys',2,4);height=integer(p,'storey_height',6,10);rise=integer(p,'roof_rise',3,6)
    if p['shape'] not in ('round','square') or height%2 or r-inner<13:raise ValueError('Round/square, even storey height and a 13-cell ring required')
    dist=(lambda x,z:math.hypot(x,z)) if p['shape']=='round' else (lambda x,z:max(abs(x),abs(z)))
    top=levels*height;m=Model((-r-3,0,-r-5),(r+3,top+rise+3,r+3))
    m.box((-r-3,0,-r-5),(r+3,0,r+3),'minecraft:grass_block[snowy=false]')
    a=-r+3;sw=3;n=height//2
    core=lambda x,z:a<=x<=a+2*sw and -n<=z<=1
    floor_cells=[];guard=[]
    for x in range(-r,r+1):
        for z in range(-r,r+1):
            d=dist(x,z)
            if d<=r:m.set(x,0,z,pal['stone'])
            if r-2<d<=r:m.box((x,1,z),(x,top,z),pal['earth'])
            if inner<=d<=r-2:
                for level in range(levels):
                    fy=level*height
                    if not (level and core(x,z)):m.set(x,fy,z,pal['floor']);floor_cells.append([x,fy,z])
                    if level and inner<=d<inner+1:
                        m.set(x,fy+1,z,pal['beam']);guard.append([x,fy+1,z])
    # Shallow radial dividers end before the continuous inner gallery.
    for angle in range(0,360,30):
        theta=math.radians(angle)
        for radius in np.arange(inner+4,r-2,.25):
            x,z=round(radius*math.cos(theta)),round(radius*math.sin(theta))
            if a-1<=x<=a+2*sw+1 and -n-1<=z<=3:continue
            for level in range(levels):m.box((x,level*height+1,z),(x,(level+1)*height-1,z),pal['beam'])
    # A two-flight stair core crosses each explicit upper-floor opening.
    flights=[];treads=[]
    for level in range(levels-1):
        fy=level*height
        for j in range(n):
            z=-n+j;y=fy+j+1
            m.box((a,y-1,z),(a+sw-1,y-1,z),pal['beam']);m.box((a,y,z),(a+sw-1,y,z),stair(pal['stairs'],'south'))
            treads.extend([[x,y,z] for x in range(a,a+sw)])
            z=-1-j;y=fy+n+j+1
            m.box((a+sw+1,y-1,z),(a+2*sw,y-1,z),pal['beam']);m.box((a+sw+1,y,z),(a+2*sw,y,z),stair(pal['stairs'],'north'))
            treads.extend([[x,y,z] for x in range(a+sw+1,a+2*sw+1)])
        m.box((a,fy+n,0),(a+2*sw,fy+n,1),pal['floor'])
        # Outer rails and middle divider are above the treads, not walkable floors.
        for j in range(n):
            for x,y,z in [(a-1,fy+j+2,-n+j),(a+2*sw+1,fy+n+j+2,-1-j),(a+sw,fy+n+j+2,-1-j)]:
                m.set(x,y,z,pal['beam']);guard.append([x,y,z])
        m.box((a,fy+n+1,2),(a+2*sw,fy+n+1,2),pal['beam'])
        flights.append({'floor':level+1,'landing':[a+sw,fy+n,0],
                        'approach':[a+1,fy,-n-1],'exit':[a+sw+2,fy+height,-n-1]})
    for angle in range(0,360,45):
        theta=math.radians(angle);x,z=round((inner+1)*math.cos(theta)),round((inner+1)*math.sin(theta))
        if core(x,z):continue
        m.box((x,1,z),(x,top,z),pal['wood'])
    upper_windows=[]
    for x in range(-r,r+1):
        for z in range(-r,r+1):
            if not r-2<dist(x,z)<=r:continue
            theta=math.atan2(z,x);nearest=round(theta/(math.pi/4))*(math.pi/4)
            if abs(theta-nearest)>.045:continue
            for level in range(1,levels):
                for y in (level*height+2,level*height+3):m.set(x,y,z,pal['glass']);upper_windows.append([x,y,z])
    heights={}
    for x in range(-r-2,r+3):
        for z in range(-r-2,r+3):
            d=dist(x,z)
            if inner-1<=d<=r+2:
                t=(d-inner+1)/(r-inner+3);heights[x,z]=top+round(rise*(1-abs(2*t-1)))
    roof_surface(m,heights,pal)
    m.box((-2,1,-r),(2,4,-inner-1),'minecraft:air');m.box((-2,0,-r-4),(2,0,-inner),pal['stone'])
    targets={f'floor_{level+1}_{side}':[x,level*height,z] for level in range(levels) for side,x,z in [('north',2,inner+2),('east',inner+2,2),('south',2,-inner-2),('west',-inner-2,2)]}
    for level in range(levels):
        for angle in range(15,360,30):
            theta=math.radians(angle);radius=(inner+4+r-2)/2
            if p['shape']=='square':radius/=max(abs(math.cos(theta)),abs(math.sin(theta)))
            targets[f'room_sector_{angle}_floor_{level+1}']=[round(radius*math.cos(theta)),level*height,round(radius*math.sin(theta))]
    targets.update({f'landing_{v["floor"]}':v['landing'] for v in flights})
    checks={'court_open_to_sky':all(m.get(0,y,0)=='minecraft:air' for y in range(1,int(m.maximum[1])+1)),
            'stair_tread_headroom':all(m.get(x,y+j,z)=='minecraft:air' for x,y,z in treads for j in (1,2)),
            'inner_gallery_guards':all(m.get(*v)!='minecraft:air' for v in guard)}
    return m,{'start_floor_xyz':[0,0,-r-4],'target_floors_xyz':targets,'checks':checks,'flights':flights,'upper_window_cells_xyz':upper_windows,
              'construction_notes':'Original multi-storey inward dwelling with protected exterior, continuous galleries and shared court; no family allocation inferred.'}


def make_regional(user):
    c=config_for(user);m,d=globals()[c['kind']](c)
    paths=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz']);d['routes']=paths
    d['checks']['outside_to_every_target']=paths['all_targets_reached']
    if not all(d['checks'].values()):raise AssertionError({'checks':d['checks'],'unreachable':paths['unreachable']})
    d.update(schema='chinese-regional-design/1',config=c,historical_reconstruction=False,source_structure_reuse=False)
    return m,d


def export(user,out_dir,name='regional',render=True):
    m,d=make_regional(user);return export_scene(m,d,out_dir,name,'chinese-regional',render)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=Path);p.add_argument('--kind',choices=PARAMETERS)
    p.add_argument('--out-dir',type=Path,required=True);p.add_argument('--name',default='regional');p.add_argument('--no-render',action='store_true');a=p.parse_args()
    c=json.loads(a.config.read_text('utf-8-sig')) if a.config else {}
    if a.kind:c['kind']=a.kind
    r=export(c,a.out_dir,a.name,not a.no_render);print(json.dumps({k:r[k] for k in ('name','dimensions_xyz','nonair_voxels')},ensure_ascii=False))
