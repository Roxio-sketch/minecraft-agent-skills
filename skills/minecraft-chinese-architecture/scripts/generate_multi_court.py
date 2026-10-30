"""Original serial courtyards with an offset outer gate and enforced gate sequence.

Partitions close the sides of open gate pavilions. Separate wings face each courtyard;
the main hall terminates the axis. No source structure geometry is reused.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_building import make_building, verify as verify_building
from generate_courtyard import DEFAULT as COURT, transform, walk_paths
from generate_hall import write_schematic
from voxel_model import Model, join_state, split_state


DEFAULT = {
    'schema':'chinese-multi-court/1', 'court_width':40, 'court_depths':[32,36],
    'outer_gate_offset_x':8, 'row_gap':2,
    'main':copy.deepcopy(COURT['main']), 'wing':copy.deepcopy(COURT['wing']),
    'gate':copy.deepcopy(COURT['gate']),
    'ground':COURT['ground'], 'path':COURT['path'],
    'boundary_wall':COURT['boundary_wall'], 'boundary_base':COURT['boundary_base'],
    'boundary_cap':COURT['boundary_cap'], 'wall_height':4, 'evidence':[],
}


def make_parts(config):
    parts={}
    for name in ('main','wing','gate'):
        model,design,c=make_building(config[name])
        verify_building(model,design,c)
        parts[name]=(model,design,c)
    gate,d,c=parts['gate']
    if c['kind']!='pavilion' or c['platform_height']!=1:
        raise ValueError('Pass-through gates require an open pavilion and platform_height=1')
    center=d['doorway']['aisle_x'];half=c['stair_width']//2
    for step in range(2):
        z=d['platform_half_z']+1-step
        if step:gate.box((center-half,0,z),(center+half,step-1,z),c['palette']['foundation'])
        gate.box((center-half,step,z),(center+half,step,z),
                 c['palette']['access_stairs']+'[facing=north,half=bottom,shape=straight,waterlogged=false]')
    return parts


def config_for(user):
    if not isinstance(user,dict) or set(user)-set(DEFAULT):raise ValueError('Unknown multi-court keys')
    c=copy.deepcopy(DEFAULT);c.update(user)
    if c['schema']!=DEFAULT['schema']:raise ValueError('Unsupported multi-court schema')
    for name in ('main','wing','gate'):
        value=user.get(name,{})
        if not isinstance(value,dict) or not isinstance(value.get('roof',{}),dict):raise ValueError(f'Invalid {name} configuration')
        c[name]={**DEFAULT[name],**value}
        c[name]['roof']={**DEFAULT[name]['roof'],**value.get('roof',{})}
    if type(c['court_width']) is not int or c['court_width']%2 or not 28<=c['court_width']<=80:
        raise ValueError('court_width must be even, 28..80')
    if not isinstance(c['court_depths'],list) or not 1<=len(c['court_depths'])<=4:
        raise ValueError('court_depths needs 1..4 courtyard depths')
    if any(type(d) is not int or d%2 or not 28<=d<=64 for d in c['court_depths']):
        raise ValueError('Each courtyard depth must be even, 28..64')
    for key,lo,hi in (('outer_gate_offset_x',-40,40),('row_gap',1,6),('wall_height',3,6)):
        if type(c[key]) is not int or not lo<=c[key]<=hi:raise ValueError(f'{key} must be integer {lo}..{hi}')
    return c


def assemble(user):
    c=config_for(user);parts=make_parts(c);half_x=c['court_width']//2
    gate,gd,gc=parts['gate']
    if abs(c['outer_gate_offset_x'])+max(abs(int(gate.origin[0])),abs(int(gate.maximum[0])))>half_x:
        raise ValueError('Outer gate footprint must fit inside the courtyard width')
    # Coordinates are model-design XYZ. Each next row is separated by its
    # complete bounding box, not by a guessed shared roof center.
    placements=[{'id':'outer_gate','part':'gate','offset':[c['outer_gate_offset_x'],0,0],'turns':0}]
    gates=[{'id':'outer_gate','center_x':c['outer_gate_offset_x'],'z':0}]
    previous_north=int(gate.origin[2]);courts=[]
    for i,depth in enumerate(c['court_depths']):
        south=previous_north-c['row_gap'];north=south-depth+1;center=(north+south)//2
        courts.append({'index':i+1,'open_x_range':[-half_x,half_x],'open_z_range':[north,south],
                       'center_xyz':[0,-1,center],'depth':depth})
        for side,turn in (('east',3),('west',1)):
            wing=parts['wing'][0].rotated(turn)
            if int(wing.maximum[2]-wing.origin[2]+1)>depth-2:
                raise ValueError('Wing length does not fit courtyard depth with clearance')
            offset_x=half_x+1-int(wing.origin[0]) if side=='east' else -half_x-1-int(wing.maximum[0])
            placements.append({'id':f'court_{i+1}_{side}_wing','part':'wing','offset':[offset_x,0,center],'turns':turn})
        last=i==len(c['court_depths'])-1
        typ,turn=('main',2) if last else ('gate',0)
        next_model=parts[typ][0].rotated(turn)
        z=north-c['row_gap']-int(next_model.maximum[2])
        ident='main_hall' if last else f'inner_gate_{i+1}'
        placements.append({'id':ident,'part':typ,'offset':[0,0,z],'turns':turn})
        if not last:gates.append({'id':ident,'center_x':0,'z':z})
        previous_north=z+int(next_model.origin[2])
    bounds=[]
    for entry in placements:
        m=parts[entry['part']][0].rotated(entry['turns']);offset=np.array(entry['offset'])
        bounds.append((m.origin+offset,m.maximum+offset))
    west=min(int(a[0]) for a,b in bounds)-2;east=max(int(b[0]) for a,b in bounds)+2
    north=previous_north-2;south=int(gate.maximum[2])+4
    height=max(int(b[1]) for a,b in bounds)
    model=Model((west-1,-1,north-1),(east+1,max(height,c['wall_height']+2),south))
    model.box(tuple(model.origin),(east+1,-1,south),c['ground'])
    model.box((-2,-1,previous_north),(2,-1,courts[0]['center_xyz'][2]),c['path'])
    gx=c['outer_gate_offset_x'];fore=courts[0]['center_xyz'][2]
    model.box((gx-2,-1,fore),(gx+2,-1,south),c['path'])
    model.box((min(0,gx)-2,-1,fore-2),(max(0,gx)+2,-1,fore+2),c['path'])
    for court in courts:
        z=court['center_xyz'][2]
        model.box((west+2,-1,z-2),(east-2,-1,z+2),c['path'])
    targets={f"court_{court['index']}":court['center_xyz'] for court in courts};receipts={}
    for entry in placements:
        ident,typ,offset,turn=entry['id'],entry['part'],entry['offset'],entry['turns']
        part,d,pc=parts[typ]
        receipts[ident]=model.paste(part,offset=offset,turns=turn)
        door=d['doorway']
        local=[door['aisle_x'],door['floor_y'],0 if typ=='gate' else door['z']+2]
        targets[ident]=transform(local,offset,turn)
    joints=[]
    def wall_cell(x,z,gate_joint=None):
        for y in range(c['wall_height']+1):
            value=c['boundary_base'] if y==0 else c['boundary_cap'] if y==c['wall_height'] else c['boundary_wall']
            old=model.get(x,y,z)
            if old=='minecraft:air':model.set(x,y,z,value)
            elif gate_joint is not None and (x-gate_joint['center_x'],y) in gate_joint_cells:
                joints.append([x,y,z])
            elif old!=value:
                raise ValueError(f'Wall collides with an existing component at {(x,y,z)}')
    for z in range(north,1):
        wall_cell(west,z);wall_cell(east,z)
    for x in range(west,east+1):wall_cell(x,north)
    # The wall plane runs through the middle of each gate. Front/back posts
    # need not occupy that plane, so include the outer column X lines in the
    # wall. Preserve only identified platform and stem cells at the joint.
    gate_joint_cells=set()
    canonical=lambda state:join_state(*split_state(state))
    for x in range(int(gate.origin[0]),int(gate.maximum[0])+1):
        for y in range(gc['platform_height']+1):
            if gate.get(x,y,0) in {canonical(gc['palette'][key]) for key in ('floor','foundation')}:
                gate_joint_cells.add((x,y))
    for x,z in gd['columns_xz']:
        if z!=0:continue
        for y in range(gc['platform_height']+1,gd['column_wood_y'][1]+1):
            expected=gc['palette']['foundation'] if y==gc['platform_height']+1 else gc['palette']['column']
            if gate.get(x,y,0)==canonical(expected):gate_joint_cells.add((x,y))
    column_x=gd['columns_x'] if 'columns_x' in gd else gd['column_x']
    for g in gates:
        left=g['center_x']+min(column_x);right=g['center_x']+max(column_x)
        g['clear_between_posts_x']=[left+1,right-1]
        for x in range(west,east+1):
            if left<x<right:continue
            wall_cell(x,g['z'],gate_joint=g)
    start=[gx,-1,south]
    paths=walk_paths(model,start,targets)
    main_route=paths['routes_floor_xyz'].get('main_hall',[]);crossings=[]
    for g in gates:
        visits=[i for i,(x,y,z) in enumerate(main_route) if z==g['z'] and g['clear_between_posts_x'][0]<=x<=g['clear_between_posts_x'][1]]
        crossings.append(min(visits) if visits else None)
    checks={'outside_to_every_court_gate_and_room':paths['all_targets_reached'],
            'main_route_crosses_gates_in_order':all(v is not None for v in crossings) and crossings==sorted(v for v in crossings if v is not None),
            'face_connected_geometry':label(model.blocks!=0)[1]==1}
    if not all(checks.values()):raise AssertionError(checks)
    return model,{'schema':'chinese-multi-court-design/1','config':c,'courts':courts,'gates_south_to_north':gates,
                  'placements':placements,'placement_receipts':receipts,'preserved_platform_joint_cells':joints,
                  'start_floor_xyz':start,'target_floors_xyz':targets,'main_route_gate_crossing_indexes':crossings,
                  'routes':paths,'checks':checks,'source_structure_reuse':False,'historical_reconstruction':False}


def export(config,output,name='multi-court',render=True):
    if not name or Path(name).name!=name or any(v in name for v in '/\\:'):raise ValueError('Simple filename stem required')
    m,d=assemble(config);out=Path(output);out.mkdir(parents=True,exist_ok=True)
    schem,npz=out/f'{name}.schem',out/f'{name}.npz'
    write_schematic(m,schem,name,generator='minecraft-chinese-architecture/chinese-multi-court-1')
    from import_structure import load_structure
    a,p,_,_=load_structure(schem)
    d['checks']['schematic_state_roundtrip']=np.array_equal(np.array(p)[a],np.array(m.palette)[m.blocks])
    if not all(d['checks'].values()):raise AssertionError(d['checks'])
    m.save(npz)
    report={'schema':'chinese-multi-court-report/1','name':name,'design':d,
            'dimensions_xyz':[m.blocks.shape[2],m.blocks.shape[0],m.blocks.shape[1]],'nonair_voxels':int(np.count_nonzero(m.blocks)),
            'schematic_sha256':hashlib.sha256(schem.read_bytes()).hexdigest(),
            'coordinate_convention':{'npz_origin_xyz':m.origin.tolist(),'schematic_offset_xyz':[0,0,0],
                                     'mapping':'schematic cell XYZ = design XYZ - NPZ origin'},
            'limits':['Original serial courts with open gate pavilions; not a reconstructed historical siheyuan.',
                      'No continuous side galleries, offset courtyards, gardens or room furniture.',
                      'Conservative whole-voxel paths, not live Minecraft player collision.']}
    (out/f'{name}.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    if render:
        from render_shapes import render as preview
        for view in ('isometric','top','front'):
            preview(npz,out/f'{name}_{view}.png',view=view,size=(1800,1400),camera_turns=0 if view=='top' else 2)
    return report


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=Path)
    p.add_argument('--out-dir',type=Path,required=True);p.add_argument('--name',default='multi-court');p.add_argument('--no-render',action='store_true')
    args=p.parse_args();r=export(json.loads(args.config.read_text(encoding='utf-8-sig')) if args.config else {},args.out_dir,args.name,not args.no_render)
    print(json.dumps({k:r[k] for k in ('name','dimensions_xyz','nonair_voxels')}))
