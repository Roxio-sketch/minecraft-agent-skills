"""Source-informed spatial mechanisms for original vernacular house studies.

These are editable Minecraft designs, not copies of surveyed heritage houses.
Each type exposes its circulation and feature geometry for independent checks.
"""
from __future__ import annotations

import argparse
import copy
import json
import math
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_courtyard import walk_paths, transform
from generate_inhabited import fixture
from generate_landmark import line
from roof_kernel import make_roof
from scene_output import export_scene
from voxel_model import Model, join_state, split_state


DEFAULTS = {
    'arcaded_shophouse': {'shops': 3, 'module_width': 12, 'depth': 24,
                         'floors': 3, 'floor_height': 7, 'arcade_depth': 5},
    'wok_ear_court': {'bay_width': 10, 'main_depth': 12, 'wing_depth': 6,
                      'wing_room_width': 8, 'court_extra': 8, 'eave_height': 9,
                      'roof_rise': 5, 'ear_rise': 4},
    'minnan_court': {'bay_width': 10, 'main_depth': 12, 'wing_depth': 6,
                     'wing_room_width': 8, 'wing_rooms': 2, 'court_extra': 8,
                     'eave_height': 9, 'roof_rise': 5, 'ridge': 'swallowtail'},
    'minnan_compound': {'form': 'two_luo', 'bay_width': 10, 'range_depth': 12,
                        'wing_depth': 6, 'wing_room_width': 8, 'wing_rooms': 1,
                        'court_extra': 8, 'eave_height': 8, 'roof_rise': 4,
                        'ancestral_range': 'rear', 'ancestral_raise': 3,
                        'ridge': 'swallowtail', 'split_court': True,
                        'hulong': 'none', 'hulong_depth': 8, 'lane_width': 8,
                        'hulong_rooms': 5},
}
PALETTE = {'wall': 'minecraft:smooth_sandstone', 'stone': 'minecraft:stone_bricks',
           'floor': 'minecraft:dark_oak_planks', 'wood': 'minecraft:dark_oak_wood[axis=y]',
           'trim': 'minecraft:polished_andesite', 'window': 'minecraft:light_blue_stained_glass',
           'roof': 'minecraft:deepslate_tiles', 'roof_stairs': 'minecraft:deepslate_tile_stairs',
           'roof_slab': 'minecraft:deepslate_tile_slab', 'stairs': 'minecraft:dark_oak_stairs'}


def integer(p, key, lo, hi):
    value = p[key]
    if type(value) is not int or not lo <= value <= hi: raise ValueError(f'{key} must be an integer {lo}..{hi}')
    return value


def add_fixture(m, name, kind, xyz, targets, records):
    x, y, z = xyz
    cells, standing = fixture(kind, x, y, z)
    if any(m.get(xx, yy, zz) != 'minecraft:air' for xx, yy, zz, _ in cells):
        raise ValueError(f'Fixture {name} overlaps house geometry')
    for xx, yy, zz, state in cells: m.set(xx, yy, zz, state)
    targets[name] = standing; records.append({'id': name, 'kind': kind, 'cells_xyz': [list(v[:3]) for v in cells], 'standing_floor_xyz': standing})


def arcaded_shophouse(p, materials):
    n = integer(p, 'shops', 1, 5); w = integer(p, 'module_width', 10, 18)
    dep = integer(p, 'depth', 20, 40); floors = integer(p, 'floors', 2, 4)
    h = integer(p, 'floor_height', 6, 9); setback = integer(p, 'arcade_depth', 4, 8)
    if dep < setback + h + 8: raise ValueError('depth must fit the arcade, stair run and rear living/storage space')
    total = n * w; roof_y = floors * h
    m = Model((-3, -1, -3), (total + 3, roof_y + 4, dep + 3))
    m.box(tuple(m.origin), (total + 3, 0, dep + 3), materials['stone'])
    targets = {'arcade_far_end': [total + 2, 0, setback // 2]}; rooms = []; fixtures = []; flights = []; holes = []; doors = []
    for storey in range(1, floors + 1):
        m.box((0, storey * h, 0), (total, storey * h, dep), materials['roof'] if storey==floors else materials['floor'])
    for unit in range(n):
        left, right = unit * w, (unit + 1) * w; middle = (left + right) // 2
        stair_x = right - 3; stair_start = setback + 3; stair_end = stair_start + h - 1
        for storey in range(floors):
            fy = storey * h; front = setback if storey == 0 else 0
            ident = f'unit_{unit+1}.floor_{storey+1}'
            for x in (left, right): m.box((x, fy + 1, front), (x, fy + h - 1, dep), materials['wall'])
            for z in (front, dep): m.box((left, fy + 1, z), (right, fy + h - 1, z), materials['wall'])
            # Shared ground-floor boundary walls stop at the shopfront, so the
            # sidewalk remains continuous under the occupied upper floors.
            for z in (front, dep):
                for x in (left+3, right-3):
                    m.box((x-1, fy+3, z), (x+1, fy+h-2, z), materials['window'])
                    m.box((x-1, fy+2, z), (x+1, fy+2, z), materials['trim'])
            if storey == 0:
                m.box((middle-1, 1, front), (middle+1, h-2, front), 'minecraft:air')
                doors.append({'unit': unit+1, 'x_range': [middle-1,middle+1], 'z': front, 'floor_y': 0})
                for x in (left, right): m.box((x, 1, 0), (x, h-1, 0), materials['trim'])
                m.box((left, h-1, 0), (right, h-1, 0), materials['trim'])
            else:
                m.box((left, fy, -1), (right, fy, -1), materials['trim'])
                for x in (left, right): m.box((x, fy+1, -1), (x, fy+h-1, -1), materials['trim'])
                # The upper slab must have an actual void for the stair below.
                m.box((stair_x, fy, stair_start), (stair_x+1, fy, stair_end-1), 'minecraft:air')
                holes.append({'unit': unit+1, 'storey': storey+1, 'y': fy,
                              'bounds_xz': [stair_x,stair_start,stair_x+1,stair_end-1]})
                # Solid guard on the room side, with the top landing open.
                m.box((stair_x-1, fy+1, stair_start), (stair_x-1, fy+1, stair_end-1), materials['wood'])
            m.box((left+1, fy, front+1), (right-1, fy, dep-1), materials['floor']) if storey == 0 else None
            targets[ident+'.front'] = [middle, fy, front+2]
            targets[ident+'.rear'] = [middle, fy, dep-3]
            rooms.append({'id': ident, 'use': 'shop' if storey == 0 else 'residence',
                          'bounds_xz': [left+1,front+1,right-1,dep-1], 'floor_y': fy})
            if storey == 0:
                add_fixture(m, ident+'.counter', 'counter', [left+2,fy+1,setback+5], targets, fixtures)
                add_fixture(m, ident+'.storage', 'storage', [left+2,fy+1,dep-2], targets, fixtures)
            else:
                add_fixture(m, ident+'.bed', 'bed', [left+2,fy+1,dep-4], targets, fixtures)
                add_fixture(m, ident+'.table', 'table', [left+2,fy+1,3], targets, fixtures)
        for storey in range(floors-1):
            fy = storey*h; treads = []
            for j in range(h):
                y, z = fy+j+1, stair_start+j
                for x in (stair_x,stair_x+1):
                    m.set(x,y-1,z,materials['wood'])
                    m.set(x,y,z,materials['stairs']+'[facing=south,half=bottom,shape=straight,waterlogged=false]')
                    treads.append([x,y,z])
            flights.append({'unit':unit+1,'from_storey':storey+1,'to_storey':storey+2,'treads_xyz':treads,
                            'bottom_floor_xyz':[stair_x,fy,stair_start-1], 'top_floor_xyz':[stair_x,fy+h,stair_end+1]})
        # Low stepped front pediment expresses a facade crown, not a specific
        # imported stylistic order or a surveyed historic hotel facade.
        for x in range(left,right+1):
            top = roof_y+1+(2 if abs(x-middle)<=w//4 else 0)
            m.box((x,roof_y+1,0),(x,top,0),materials['wall'])
        for x in (left,right): m.box((x,roof_y+1,0),(x,roof_y+1,dep),materials['trim'])
    m.box((0,roof_y+1,dep),(total,roof_y+1,dep),materials['trim'])
    public = [(x,z) for x in range(total+1) for z in range(1,setback)]
    checks = {'continuous_recessed_arcade': all(m.get(x,y,z)=='minecraft:air' for x,z in public for y in (1,2,3)),
              'arcade_is_under_upper_floor': all(m.get(x,h,z)!='minecraft:air' for x,z in public),
              'independent_stair_count': len(flights)==n*(floors-1),
              'upper_front_is_forward_of_shopfront': setback>0}
    return m, {'start_floor_xyz':[-2,0,setback//2], 'target_floors_xyz':targets, 'checks':checks,
               'rooms':rooms, 'fixtures':fixtures, 'stair_flights':flights, 'stair_voids':holes, 'shop_doors':doors,
               'arcade':{'x_range':[0,total], 'clear_z_range':[1,setback-1], 'shopfront_z':setback,
                         'upper_front_z':0,'ceiling_y':h,'clear_width_blocks':setback-1},
               'actual_accessible_storeys':floors,
               'spatial_basis':'Set-back street-level shops, continuous shared covered sidewalk, upper dwellings and separate internal stairs.',
               'limits':'Original shop modules and facade details. Source mentions a 2–3 metre setback; these editable block dimensions are not a survey conversion. Roof is not an accessible terrace.'}


def _gable_range(bay_width, room_count, depth, height, rise, materials, ridge='round', ear_rise=0, furnish=True, bay_widths=None):
    """Connected rooms with independent porch entrances under one gable roof."""
    widths=[bay_width]*room_count if bay_widths is None else list(bay_widths)
    if len(widths)!=room_count or any(type(v) is not int or v<6 or v%2 for v in widths):
        raise ValueError('Each range bay must have an even width of at least six blocks')
    hx, hz, porch = sum(widths)//2, depth//2, 3
    roof,rd=make_roof(half_x=hx,half_z=hz+porch,base_y=height+1,rise=rise,
                     form='flush_gable',uplift=0,curve=1.4,
                     family=lambda x,z:{'block':materials['roof'],'stairs':materials['roof_stairs'],'slab':materials['roof_slab']},
                     trim=materials['trim'],gable_block=materials['wall'])
    m=Model((-hx-4,0,-hz-porch-2),(hx+4,height+rise+ear_rise+8,hz+porch+1))
    m.box((-hx,0,-hz-porch),(hx,1,hz),materials['stone'])
    m.box((-hx+1,1,-hz-porch+1),(hx-1,1,hz-1),materials['floor'])
    surface=rd['surface_y_rows_zx']
    def under(x,z):return surface[z+rd['half_z']][x+rd['half_x']]-1
    for z in (-hz,hz):
        for x in range(-hx,hx+1): m.box((x,2,z),(x,under(x,z),z),materials['wall'])
    for x in (-hx,hx):
        for z in range(-hz,hz+1): m.box((x,2,z),(x,under(x,z),z),materials['wall'])
    centers=[];targets={};rooms=[];fixtures=[];doors=[]
    left=-hx
    for i in range(room_count):
        right=left+widths[i]; center=(left+right)//2; centers.append(center)
        if i:
            for z in range(-hz,hz+1): m.box((left,2,z),(left,under(left,z),z),materials['wood'])
        m.box((center-1,2,-hz),(center+1,5,-hz),'minecraft:air')
        m.box((center-1,4,hz),(center+1,min(height-1,6),hz),materials['window'])
        doors.append({'x_range':[center-1,center+1],'z':-hz,'floor_y':1})
        ident='room_'+str(i+1);targets[ident]=[center,1,-hz+2]
        rooms.append({'id':ident,'bounds_xz':[left+1,-hz+1,right-1,hz-1],'floor_y':1})
        for x in (left,right):
            m.box((x,2,-hz-porch+1),(x,under(x,-hz-porch+1),-hz-porch+1),materials['wood'])
        if furnish:
            if room_count==3 and i==1:
                add_fixture(m,ident+'.table','table',[center-1,2,hz-2],targets,fixtures)
            else:
                add_fixture(m,ident+'.storage','storage',[center,2,hz-2],targets,fixtures)
        left=right
    m.box((-hx,height,-hz-porch+1),(hx,height,-hz-porch+1),materials['wood'])
    m.paste(roof,conflict='replace')
    gable_profiles=[];tail_arms=[]
    if ear_rise:
        ez=rd['half_z']; base=height+1
        knots=[(0,base+rise+ear_rise),(.18,base+rise+ear_rise),(.32,base+rise+ear_rise-2),
               (.48,base+rise*.45+1),(.76,base+1),(1,base+2)]
        profile={}
        for z in range(-ez,ez+1):
            t=abs(z)/ez
            for (ta,ya),(tb,yb) in zip(knots,knots[1:]):
                if ta<=t<=tb:
                    u=(t-ta)/(tb-ta); eased=u*u*(3-2*u)
                    profile[z]=max(under(hx,z)+2,round(ya*(1-eased)+yb*eased));break
        for x in (-hx,hx):
            for z,y in profile.items():m.box((x,height,z),(x,y,z),materials['wall'])
            points=[[x,profile[z]+1,z] for z in sorted(profile)]
            for a,b in zip(points,points[1:]):line(m,a,b,materials['trim'])
            gable_profiles.append({'x':x,'top_y_by_z':[[z,profile[z]+1] for z in sorted(profile)]})
    elif ridge=='swallowtail':
        top=height+rise+3
        m.box((-hx,top,0),(hx,top,0),materials['roof'])
        # A fork at each ridge end rises in XYZ, rather than a painted V.
        for sign in (-1,1):
            for side in (-1,1):
                points=[]
                for t in np.linspace(0,1,17):
                    points.append([round(sign*(hx-3+6*t)),round(top+4*t*t),round(side*2*t*t)])
                for a,b in zip(points,points[1:]):line(m,a,b,materials['roof'])
                tail_arms.append({'end_sign_x':sign,'fork_sign_z':side,'points_xyz':points})
    elif ridge!='round':raise ValueError('ridge must be round or swallowtail')
    return m,{'target_floors_xyz':targets,'rooms':rooms,'fixtures':fixtures,'doors':doors,
              'bay_widths':widths,
              'roof':rd,'gable_profiles':gable_profiles,'swallowtail_arms':tail_arms,
              'porch_floor_bounds_xz':[-hx,-hz-porch,hx,-hz-1]}


def _court(p,materials,wok):
    bay=integer(p,'bay_width',8,14);depth=integer(p,'main_depth',10,18);wd=integer(p,'wing_depth',6,10)
    wb=integer(p,'wing_room_width',8,12);extra=integer(p,'court_extra',6,14);height=integer(p,'eave_height',8,13)
    rise=integer(p,'roof_rise',3,8); count=1 if wok else integer(p,'wing_rooms',1,2)
    if any(v%2 for v in (bay,depth,wd,wb,extra)):raise ValueError('Court lengths and bays must be even integers')
    hx=3*bay//2;court_depth=count*wb+extra;clear_half=hx-wd-3
    if clear_half<4:raise ValueError('Wings and porches leave less than seven blocks of open courtyard; enlarge bay_width or reduce wing_depth')
    if not wok and p['ridge'] not in ('round','swallowtail'):raise ValueError('ridge must be round or swallowtail')
    ear=integer(p,'ear_rise',2,7) if wok else 0
    main,md=_gable_range(bay,3,depth,height,rise,materials,'round' if wok else p['ridge'],ear)
    wing,wdsgn=_gable_range(wb,count,wd,height-3,max(2,rise-2),materials)
    main_at=[0,0,court_depth+3+depth//2];wing_z=court_depth//2
    parts=[('main',main,md,main_at,0),('west',wing,wdsgn,[-hx+wd//2,0,wing_z],1),
           ('east',wing,wdsgn,[hx-wd//2,0,wing_z],3)]
    rotated=[(ident,part.rotated(turns),d,at,turns) for ident,part,d,at,turns in parts]
    lows=np.array([part.origin+at for _,part,_,at,_ in rotated]);highs=np.array([part.maximum+at for _,part,_,at,_ in rotated])
    low=lows.min(axis=0);high=highs.max(axis=0);low[1]=-2;low[2]=min(-5,low[2]);high[1]+=1
    m=Model(low.tolist(),high.tolist());m.box(low.tolist(),(int(high[0]),0,int(high[2])),materials['stone'])
    back=main_at[2]+depth//2
    for x in (-hx,hx):m.box((x,1,0),(x,4,back),materials['wall'])
    for x in range(-hx,hx+1):
        if abs(x)>1:m.box((x,1,0),(x,4,0),materials['wall'])
    targets={'courtyard':[0,0,court_depth//2]};placements={};rooms=[];fixtures=[];entry_faces=[]
    for ident,part,d,at,turns in parts:
        placements[ident]=m.paste(part,offset=at,turns=turns,conflict='replace')
        for name,pt in d['target_floors_xyz'].items():targets[ident+'.'+name]=transform(pt,at,turns)
        for room in d['rooms']:
            x0,z0,x1,z1=room['bounds_xz'];corners=[transform([x,room['floor_y'],z],at,turns) for x in (x0,x1) for z in (z0,z1)]
            rooms.append({'id':ident+'.'+room['id'],'corners_xyz':corners,'floor_y':1})
        for f in d['fixtures']:
            fixtures.append({**f,'id':ident+'.'+f['id'],'cells_xyz':[transform(pt,at,turns) for pt in f['cells_xyz']],
                             'standing_floor_xyz':transform(f['standing_floor_xyz'],at,turns)})
        for door in d['doors']:
            left,right=door['x_range'];entry_faces.append({'room_range':ident,'opening_xyz':[transform([x,2,door['z']],at,turns) for x in range(left,right+1)]})
    # Entrance is below a small independent roof. This is a gate wall, not the
    # full front range of a two-luo house or a san-gai-lang reconstruction.
    for x in (-2,2):m.box((x,1,0),(x,5,0),materials['trim'])
    m.box((-2,5,0),(2,5,0),materials['wood'])
    gate,_=make_roof(half_x=4,half_z=3,base_y=6,rise=2,form='flush_gable',uplift=0,
                    family=lambda x,z:{'block':materials['roof'],'stairs':materials['roof_stairs'],'slab':materials['roof_slab']},trim=materials['trim'])
    m.paste(gate,conflict='replace')
    # A shallow dry drain has an actual open channel and outlet. It is not a
    # Minecraft water-system simulation or a design rainfall calculation.
    channel=[]
    dx=-clear_half+1
    for z in range(1,court_depth):
        m.set(dx,0,z,'minecraft:air');channel.append([dx,-1,z])
    check={'three_bay_main_range':len(md['rooms'])==3,'opposing_wing_room_count':len(rooms)==3+2*count,
           'open_to_sky_court':all(m.get(0,y,court_depth//2)=='minecraft:air' for y in range(1,int(m.maximum[1])+1)),
           'dry_channel_open':all(m.get(x,y+1,z)=='minecraft:air' for x,y,z in channel)}
    if wok:check['raised_end_gables']=len(md['gable_profiles'])==2
    else:check['selected_ridge_geometry']=len(md['swallowtail_arms'])==(4 if p['ridge']=='swallowtail' else 0)
    return m,{'start_floor_xyz':[0,0,-4],'target_floors_xyz':targets,'checks':check,
              'rooms':rooms,'fixtures':fixtures,'placements':placements,'door_openings':entry_faces,
              'main_range':md,'wing_rooms_each_side':count,'court_clear_half_width':clear_half,'court_depth':court_depth,
              'drain_floor_xyz':channel,'actual_accessible_storeys':1,
              'spatial_basis':('Three rear bays and two forward side rooms around a small court, with independent room entrances and raised end gables.' if wok else
                               'Three-bay main range and two or four projecting side rooms around an open-to-sky court; distinct independent room entrances and configurable ridge forks.'),
              'limits':'Original block dimensions and furniture. Not a measured copy of Daqitou or Kinmen. The front gate canopy does not constitute san-gai-lang or a complete second luo. No historic ranking, drainage capacity or fire performance asserted.'}


def wok_ear_court(p,materials):return _court(p,materials,True)


def minnan_court(p,materials):return _court(p,materials,False)


def minnan_compound(p,materials):
    from minnan_compounds import make_compound
    return make_compound(p,materials,_gable_range)


def make_vernacular(user):
    if not isinstance(user,dict) or set(user)-{'schema','kind','parameters','palette','evidence'}:
        raise ValueError('Unknown vernacular configuration keys')
    kind = user.get('kind','arcaded_shophouse'); params=user.get('parameters',{}); material=user.get('palette',{})
    if user.get('schema','chinese-vernacular/1')!='chinese-vernacular/1' or kind not in DEFAULTS:
        raise ValueError('Unknown vernacular schema or kind')
    if not isinstance(params,dict) or set(params)-set(DEFAULTS[kind]): raise ValueError('Unknown parameters for '+kind)
    if not isinstance(material,dict) or set(material)-set(PALETTE): raise ValueError('Unknown vernacular palette roles')
    c={'schema':'chinese-vernacular/1','kind':kind,'parameters':{**DEFAULTS[kind],**params},
       'palette':{**PALETTE,**material},'evidence':copy.deepcopy(user.get('evidence',[]))}
    for key,state in c['palette'].items():
        base,props=split_state(state)
        if base in ('minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:water','minecraft:lava'):
            raise ValueError('Palette materials must not be air or liquid')
        suffix='_stairs' if key in ('stairs','roof_stairs') else '_slab' if key=='roof_slab' else None
        if suffix and (props or not base.endswith(suffix)): raise ValueError('Invalid palette.'+key)
        c['palette'][key]=join_state(base,props)
    m,d=globals()[kind](c['parameters'],c['palette'])
    routes=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
    d['checks'].update(all_targets_reachable=routes['all_targets_reached'],face_connected_geometry=label(m.blocks!=0)[1]==1)
    if not all(d['checks'].values()): raise AssertionError({**d['checks'],'unreachable':routes['unreachable']})
    return m,{'schema':'chinese-vernacular-design/1','config':c,**d,'routes':routes,
              'historical_reconstruction':False,'source_structure_reuse':False}


def export(user,out_dir,name='vernacular',render=True):
    m,d=make_vernacular(user);return export_scene(m,d,out_dir,name,'chinese-vernacular',render)


if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--config',type=Path);ap.add_argument('--out-dir',type=Path,required=True)
    ap.add_argument('--name',default='vernacular');ap.add_argument('--no-render',action='store_true');a=ap.parse_args()
    r=export(json.loads(a.config.read_text('utf-8-sig')) if a.config else {},a.out_dir,a.name,not a.no_render)
    print(json.dumps({k:r[k] for k in ('name','dimensions_xyz','nonair_voxels')},ensure_ascii=False))
