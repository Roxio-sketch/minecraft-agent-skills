"""Compose original hillslope building groups with graded pads and connected paths."""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

import numpy as np

from generate_building import make_building, verify as verify_building
from generate_courtyard import transform, walk_paths
from generate_tower import make_tower
from scene_output import export_scene
from terrain_planning import heightfield, grade_route
from voxel_model import Model


DEFAULT={
 'schema':'chinese-complex/1','origin_xz':[-80,-100],'size_xz':[161,205],
 'terrain':{'base':2,'grade_z':.065,'wave':1.2,'hills':[{'x':8,'z':64,'radius':38,'height':19}]},
 'pad_margin':6,'path_width':5,'max_path_grading':8,'max_pad_cut_fill':24,
 'entrance_xz':[0,-94],
 'components':[
  {'id':'gate','kind':'building','position_xz':[0,-62],'turns':0,'through':True,
   'config':{'kind':'pavilion','bays_x':[8],'bays_z':[4],'column_height':5,'platform_height':1,'platform_margin':2,'eave_overhang':2,'stair_width':3,'roof':{'form':'overhanging_gable','rise':3,'uplift':0}}},
  {'id':'main_hall','kind':'building','position_xz':[0,0],'turns':0,
   'config':{'bays_x':[6,8,6],'bays_z':[6,6],'column_height':10}},
  {'id':'west_hall','kind':'building','position_xz':[-49,0],'turns':1,
   'config':{'bays_x':[6,6,6],'bays_z':[6],'column_height':6,'roof':{'form':'overhanging_gable','rise':4,'uplift':0}}},
  {'id':'east_hall','kind':'building','position_xz':[49,16],'turns':3,
   'config':{'bays_x':[6,6,6],'bays_z':[6],'column_height':6,'roof':{'form':'overhanging_gable','rise':4,'uplift':0}}},
  {'id':'upper_tower','kind':'tower','position_xz':[12,65],'turns':0,'config':{}}
 ],
 'connections':[['entrance','gate.entry'],['gate.exit','main_hall.entry'],
                ['main_hall.entry','west_hall.entry'],['main_hall.entry','east_hall.entry'],['east_hall.entry','upper_tower.entry']],
 'evidence':[],'program':{}}


def make_complex(user):
    if not isinstance(user,dict) or set(user)-set(DEFAULT):raise ValueError('Unknown complex configuration keys')
    c=copy.deepcopy(DEFAULT);c.update(user)
    if c['schema']!=DEFAULT['schema']:raise ValueError('Unknown complex schema')
    for key,lo,hi in [('pad_margin',5,10),('max_pad_cut_fill',0,64),('max_path_grading',0,16)]:
        if type(c[key]) is not int or not lo<=c[key]<=hi:raise ValueError(f'{key} must be integer {lo}..{hi}')
    if c['path_width'] not in (3,5,7) or type(c['path_width']) is not int:raise ValueError('Path width must be 3,5,7')
    if c['pad_margin']<c['path_width']//2+3:raise ValueError('Pad margin too small for approach and path width')
    original,terrain_provenance=heightfield(c['origin_xz'],c['size_xz'],c['terrain'])
    surface=original.copy();depth,width=surface.shape;x0,z0=c['origin_xz']
    def ixz(point):
        if not isinstance(point,list) or len(point)!=2 or any(type(v) is not int for v in point):raise ValueError('Integer XZ required')
        x,z=point[0]-x0,point[1]-z0
        if not 0<=x<width or not 0<=z<depth:raise ValueError('Position outside site')
        return x,z
    fixed=np.zeros(surface.shape,bool);blocked=fixed.copy();pads=[];parts={};anchors={};targets={};foundation_modes={}
    entries=c['components']
    if not isinstance(entries,list) or not 1<=len(entries)<=24:raise ValueError('Need 1..24 components')
    for entry in entries:
        if not isinstance(entry,dict) or set(entry)-{'id','kind','position_xz','turns','through','config','elevation','role','label'}:raise ValueError('Unknown component keys')
        ident=entry['id']
        if not isinstance(ident,str) or not ident or '.' in ident or ident=='entrance' or ident in parts:raise ValueError('Unique simple component IDs required')
        turns=entry.get('turns',0)
        if type(turns) is not int or not 0<=turns<=3:raise ValueError('turns must be 0..3')
        through=entry.get('through',False)
        if type(through) is not bool:raise ValueError('through must be boolean')
        own_foundation=False;entry_y=-1;entry_face='north'
        if entry['kind']=='building':
            part,d,conf=make_building(entry.get('config',{}));verify_building(part,d,conf)
            door=d['doorway'];local_targets={'inside':[door['aisle_x'],door['floor_y'],door['z']+2]};aisle=door['aisle_x']
            if through:
                if conf['kind']!='pavilion' or conf['platform_height']!=1:raise ValueError('Through gate requires a pavilion with platform_height=1')
                half=conf['stair_width']//2;pz=d['platform_half_z']
                for step in (0,1):
                    stair_surface=conf['palette']['foundation'] if step==0 else conf['palette']['access_stairs']+'[facing=north,half=bottom,shape=straight,waterlogged=false]'
                    part.box((aisle-half,step,pz+1-step),(aisle+half,step,pz+1-step),stair_surface)
        elif entry['kind']=='tower':
            if through:raise ValueError('Tower does not support through passage')
            part,d=make_tower(entry.get('config',{}));aisle=0
            local_targets={key:route[-1] for key,route in d['routes']['routes_floor_xyz'].items()}
        elif entry['kind'] in ('regional','landmark','garden','courtyard','multi_court','inhabited','infrastructure','settlement','vernacular'):
            if entry['kind']=='vernacular':
                from generate_vernacular import make_vernacular as builder
            elif entry['kind']=='settlement':
                from generate_settlement import make_settlement as builder
            elif entry['kind']=='inhabited':
                from generate_inhabited import make_inhabited as builder
            elif entry['kind']=='infrastructure':
                from generate_infrastructure import make_infrastructure as builder
            elif entry['kind']=='regional':
                from generate_regional import make_regional as builder
            elif entry['kind']=='landmark':
                from generate_landmark import make_landmark as builder
            elif entry['kind']=='garden':
                from generate_garden import make_garden as builder
            elif entry['kind']=='courtyard':
                from generate_courtyard import assemble as builder
            else:
                from generate_multi_court import assemble as builder
            part,d=builder(entry.get('config',{}))
            if 'start_floor_xyz' not in d or 'target_floors_xyz' not in d:raise ValueError('Only components with a verified entrance and interior targets may join a site')
            if through and not(entry['kind']=='landmark' and d['config']['kind'] in ('paifang','city_gate')):raise ValueError('This component has no through passage contract')
            local_targets={**d['target_floors_xyz'],'component_entry':d['start_floor_xyz']}
            entry_point=d['start_floor_xyz'];entry_y=entry_point[1];aisle=entry_point[0]
            distances={'north':entry_point[2]-int(part.origin[2]),'south':int(part.maximum[2])-entry_point[2],
                       'west':entry_point[0]-int(part.origin[0]),'east':int(part.maximum[0])-entry_point[0]}
            entry_face=min(distances,key=distances.get);own_foundation=True
        else:raise ValueError('Unknown complex component kind')
        rotated=part.rotated(turns)
        ixz(entry['position_xz']);cx,cz=entry['position_xz'];margin=c['pad_margin']
        lo=rotated.origin[[0,2]]+[cx,cz]-margin;hi=rotated.maximum[[0,2]]+[cx,cz]+margin
        a,b=ixz(lo.tolist()),ixz(hi.tolist());sx=slice(a[0],b[0]+1);sz=slice(a[1],b[1]+1)
        if fixed[sz,sx].any():raise ValueError('Building pads overlap; space components farther apart')
        y=entry.get('elevation',int(np.median(original[sz,sx])))
        if type(y) is not int or not -64<=y<=192:raise ValueError('Pad elevation outside -64..192')
        difference=y-original[sz,sx]
        if np.abs(difference).max()>c['max_pad_cut_fill']:raise ValueError('Pad exceeds cut/fill height budget')
        surface[sz,sx]=y;fixed[sz,sx]=True
        footprint_lo=rotated.origin[[0,2]]+[cx,cz];footprint_hi=rotated.maximum[[0,2]]+[cx,cz]
        fa,fb=ixz(footprint_lo.tolist()),ixz(footprint_hi.tolist());blocked[fa[1]:fb[1]+1,fa[0]:fb[0]+1]=True
        offset=[cx,y-entry_y,cz];r=c['path_width']//2
        local_anchor={'north':[aisle,entry_y,int(part.origin[2])-r-2],
                      'south':[aisle,entry_y,int(part.maximum[2])+r+2],
                      'west':[int(part.origin[0])-r-2,entry_y,entry_point[2] if own_foundation else 0],
                      'east':[int(part.maximum[0])+r+2,entry_y,entry_point[2] if own_foundation else 0]}[entry_face]
        ap=transform(local_anchor,offset,turns)
        anchors[ident+'.entry']=ap
        if through:anchors[ident+'.exit']=transform([aisle,entry_y,int(part.maximum[2])+r+2],offset,turns)
        for key,point in local_targets.items():targets[ident+'.'+key]=transform(point,offset,turns)
        pads.append({'id':ident,'bounds_xz':[int(lo[0]),int(lo[1]),int(hi[0]),int(hi[1])],'elevation':y,
                     'cut_cells':int(np.maximum(-difference,0).sum()),'fill_cells':int(np.maximum(difference,0).sum()),
                     'max_cut_or_fill':int(np.abs(difference).max()),'component_offset_xyz':offset,'turns':turns,
                     'kind':entry['kind'],'role':entry.get('role'),'label':entry.get('label'),'local_entrance_face':entry_face,
                     'owns_foundation_and_air':own_foundation})
        parts[ident]=(part,offset,turns,d)
        foundation_modes[ident]=(own_foundation,y)
    ex,ez=ixz(c['entrance_xz']);ey=int(surface[ez,ex]);anchors['entrance']=[ex+x0,ey,ez+z0]
    road=np.zeros_like(fixed);routes=[]
    if not isinstance(c['connections'],list) or len(c['connections'])>48:raise ValueError('At most 48 explicit connections')
    for pair in c['connections']:
        if not isinstance(pair,list) or len(pair)!=2 or any(p not in anchors for p in pair):raise ValueError('Connection needs two existing anchor names')
        def node(anchor):x,y,z=anchors[anchor];return [x-x0,z-z0,y]
        result=grade_route(surface,fixed,blocked,node(pair[0]),node(pair[1]),width=c['path_width'],max_grade=c['max_path_grading'])
        mask=result.pop('mask');levels=result.pop('heights')
        surface[mask]=levels[mask];fixed[mask]=True;road |= mask
        result['from'],result['to']=pair
        result['center_floor_xyz']=[[x+x0,y,z+z0] for x,z,y in result.pop('route_local_xzy')]
        routes.append(result)
    min_y=min(int(min(surface.min(),original.min()))-4,min(int(p.origin[1]+offset[1])-1 for p,offset,turns,d in parts.values()))
    max_y=max(int(surface.max())+10,max(int(p.maximum[1]+offset[1])+3 for p,offset,turns,d in parts.values()))
    m=Model((x0,min_y,z0),(x0+width-1,max_y,z0+depth-1))
    # Vectorized terrain columns; cut/fill never mutates the input heightmap.
    yy=np.arange(min_y,max_y+1)[:,None,None]
    occupied=yy<=surface[None]
    m.blocks[occupied]=m.pid('minecraft:stone')
    soil=(yy>surface[None]-3)&occupied;m.blocks[soil]=m.pid('minecraft:dirt')
    zz,xx=np.indices(surface.shape);m.blocks[surface-min_y,zz,xx]=m.pid('minecraft:grass_block[snowy=false]')
    retaining_cells=0
    # Exposed graded faces are faced with masonry rather than bare vertical fill.
    edited=surface!=original
    for z,x in zip(*np.where(edited)):
        top=int(surface[z,x]);neighbors=[int(surface[nz,nx]) for nz,nx in ((z-1,x),(z+1,x),(z,x-1),(z,x+1)) if 0<=nz<depth and 0<=nx<width]
        bottom=min(neighbors+[top])
        if top-bottom>1:
            m.box((x+x0,bottom+1,z+z0),(x+x0,top,z+z0),'minecraft:stone_bricks');retaining_cells+=top-bottom
    for pad in pads:
        xa,za,xb,zb=pad['bounds_xz'];m.box((xa,pad['elevation'],za),(xb,pad['elevation'],zb),'minecraft:smooth_stone')
    for z,x in zip(*np.where(road)):
        y=int(surface[z,x]);higher=[]
        for facing,dx,dz in [('east',1,0),('west',-1,0),('south',0,1),('north',0,-1)]:
            if 0<=x+dx<width and 0<=z+dz<depth and road[z+dz,x+dx] and surface[z+dz,x+dx]==y+1:higher.append(facing)
        state=('minecraft:stone_brick_stairs[facing='+higher[0]+',half=bottom,shape=straight,waterlogged=false]') if len(higher)==1 else 'minecraft:stone_bricks'
        m.set(int(x+x0),y,int(z+z0),state)
    receipts={}
    for ident,(part,offset,turns,d) in parts.items():
        own_foundation,pad_y=foundation_modes[ident]
        if own_foundation:
            # A generated scene owns its complete local foundation and explicit
            # air (e.g. a drain above buried water). Preflight every occupied
            # destination cell: only this isolated, newly created terrain below
            # its pad can be replaced. No neighboring building may be carved.
            rotated=part.rotated(turns);lo=rotated.origin+offset;hi=rotated.maximum+offset
            ax,ay,az=(lo-m.origin).tolist();bx,by,bz=(hi-m.origin).tolist()
            prior=m.blocks[ay:by+1,az:bz+1,ax:bx+1]
            yy=(np.arange(prior.shape[0])+int(lo[1]))[:,None,None]
            if np.any((prior!=0)&(yy>pad_y)):raise ValueError('Scene foundation replacement would overwrite above-pad geometry')
            cleared=int(np.count_nonzero((prior!=0)&(rotated.blocks==0)))
            receipt=m.paste(part,offset=offset,turns=turns,conflict='replace',include_air=True)
            receipt.update(authorized_terrain_replacement=True,cleared_terrain_cells=cleared,unexpected_conflicts=0)
        else:
            receipt=m.paste(part,offset=offset,turns=turns);receipt.update(authorized_terrain_replacement=False,unexpected_conflicts=receipt['differing_occupied_overlaps'])
        receipts[ident]=receipt
    paths=walk_paths(m,anchors['entrance'],targets)
    # Test the planned road band itself, not just some alternative across the hill.
    center_clear=all(m.get(x,y,z)!='minecraft:air' and m.get(x,y+1,z)=='minecraft:air' and m.get(x,y+2,z)=='minecraft:air'
                     for r in routes for x,y,z in r['center_floor_xyz'])
    band_clear=all(m.get(int(x+x0),int(surface[z,x])+dy,int(z+z0))=='minecraft:air' for z,x in zip(*np.where(road)) for dy in (1,2))
    checks={'all_buildings_and_storeys_reachable':paths['all_targets_reached'],'planned_centerlines_clear':center_clear,
            'full_path_width_clear':band_clear,'planned_grade_at_most_one':all(r['max_step']<=1 for r in routes),
            'all_component_pastes_without_unexpected_conflicts':all(r['unexpected_conflicts']==0 for r in receipts.values()),
            'all_pads_supported':all(np.all(surface[pad['bounds_xz'][1]-z0:pad['bounds_xz'][3]-z0+1,pad['bounds_xz'][0]-x0:pad['bounds_xz'][2]-x0+1]==pad['elevation']) for pad in pads)}
    if not all(checks.values()):raise AssertionError(checks)
    difference=surface-original
    design={'schema':'chinese-complex-design/1','config':c,'terrain_source':terrain_provenance,'pads':pads,'anchors_floor_xyz':anchors,
            'targets_floor_xyz':targets,'graded_connections':routes,'routes':paths,'checks':checks,'placements':receipts,
            'earthwork':{'net_cut_cells':int(np.maximum(-difference,0).sum()),'net_fill_cells':int(np.maximum(difference,0).sum()),
                         'changed_columns':int(np.count_nonzero(difference)),'retaining_face_cells':retaining_cells},
            'historical_reconstruction':False,'source_structure_reuse':False,
            'limits':'New local terrain artifact only. Heightfield represents surface, not source caves, fluids, entities or existing buildings. No world edits; historical masterplans and structural stability not inferred.'}
    return m,design,original,surface


def export(config,out_dir,name='complex',render=True):
    m,d,original,graded=make_complex(config)
    r=export_scene(m,d,out_dir,name,'chinese-complex',render)
    np.savez_compressed(Path(out_dir)/f'{name}.heightfields.npz',original_height_zx=original,height_zx=graded,origin_xz=d['config']['origin_xz'])
    return r


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=Path);p.add_argument('--out-dir',type=Path,required=True)
    p.add_argument('--name',default='complex');p.add_argument('--no-render',action='store_true');a=p.parse_args()
    r=export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {},a.out_dir,a.name,not a.no_render)
    print(json.dumps({k:r[k] for k in ('name','dimensions_xyz','nonair_voxels')},ensure_ascii=False))
