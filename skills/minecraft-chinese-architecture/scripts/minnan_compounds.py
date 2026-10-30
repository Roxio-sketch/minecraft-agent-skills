"""Original Minnan compound layouts with distinct front roofs and side lanes.

Kinmen type descriptions constrain relationships, not block dimensions.
The Hong-house survey is comparative evidence, not geometry copied here.
"""
from __future__ import annotations

import numpy as np

from generate_courtyard import transform
from voxel_model import Model


def _int(p,key,lo,hi,even=False):
    v=p[key]
    if type(v) is not int or not lo<=v<=hi or (even and v%2):
        raise ValueError(f'{key} must be '+('even ' if even else '')+f'{lo}..{hi}')
    return v


def _portal(part,detail,x,z,floor=1):
    """A second face opening joins an existing room or passage to another court."""
    part.box((x-1,floor+1,z),(x+1,floor+4,z),'minecraft:air')
    return {'x_range':[x-1,x+1],'z':z,'floor_y':floor}


def _remove_fixture(part,detail,room):
    for item in list(detail['fixtures']):
        if item['id'].startswith(room+'.'):
            for x,y,z in item['cells_xyz']:part.set(x,y,z,'minecraft:air')
            detail['fixtures'].remove(item)
            detail['target_floors_xyz'].pop(item['id'],None)


def _even_bays(length,count):
    """Fit several usable room spans to a fixed overall length, without padding."""
    edges=[2*round(i*length/(2*count)) for i in range(count+1)]
    widths=[b-a for a,b in zip(edges,edges[1:])]
    if min(widths)<6:raise ValueError('Too many hulong rooms for the compound depth; reduce hulong_rooms')
    return widths


def make_compound(p,materials,range_factory):
    form=p['form']
    if form not in ('san_gai_lang','two_luo','three_luo'):
        raise ValueError('form must be san_gai_lang, two_luo or three_luo')
    if p['ancestral_range'] not in ('rear','middle') or (p['ancestral_range']=='middle' and form!='three_luo'):
        raise ValueError('ancestral_range=middle requires three_luo')
    if p['hulong'] not in ('none','west','east','both'):raise ValueError('hulong must be none, west, east or both')
    if p['ridge'] not in ('round','swallowtail'):raise ValueError('ridge must be round or swallowtail')
    if type(p['split_court']) is not bool:raise ValueError('split_court must be boolean')
    bay=_int(p,'bay_width',10,16,True);depth=_int(p,'range_depth',10,18,True)
    wd=_int(p,'wing_depth',6,10,True);wb=_int(p,'wing_room_width',8,12,True)
    wc=_int(p,'wing_rooms',1,2);extra=_int(p,'court_extra',8,16,True)
    if form=='san_gai_lang' and wc!=1:
        raise ValueError('san_gai_lang retains one upper wing room and one lower/front room on each side; use wing_rooms=1')
    height=_int(p,'eave_height',8,12);rise=_int(p,'roof_rise',3,7)
    raised=_int(p,'ancestral_raise',2,5);hd=_int(p,'hulong_depth',6,12,True)
    lane=_int(p,'lane_width',8,14,True);hc=_int(p,'hulong_rooms',3,9)
    hx=3*bay//2;halfdepth=depth//2;gap=wc*wb+extra;step=depth+gap
    if hx-wd-3<4:raise ValueError('Wings leave less than seven blocks of open central court')
    rows=3 if form=='three_luo' else 2
    centers=[i*step for i in range(rows)]
    ancestor=1 if p['ancestral_range']=='middle' else rows-1
    front_z=-halfdepth;back_z=centers[-1]+halfdepth
    parts=[];range_records=[];portals=[]

    # Three separate front roofs are distinct from a continuous three-bay luo.
    if form=='san_gai_lang':
        for j,offsetx in enumerate((-bay,0,bay)):
            part,d=range_factory(bay,1,depth,height-(1 if j==1 else 2),rise-1,materials,furnish=j!=1)
            name=('front.west_lower','front.gate','front.east_lower')[j]
            if j==1:
                door=_portal(part,d,0,halfdepth)
                portals.append((name,door))
                d['target_floors_xyz']['through']=[0,1,halfdepth-1]
            parts.append((name,part,d,[offsetx,0,0],2))
            range_records.append({'id':name,'row':0,'continuous_bays':1,'eave_height':height-(1 if j==1 else 2),
                                  'contains_ancestral_hall':False})
    else:
        part,d=range_factory(bay,3,depth,height,rise,materials)
        _remove_fixture(part,d,'room_2')
        door=_portal(part,d,0,halfdepth);portals.append(('range_1',door))
        d['target_floors_xyz']['through']=[0,1,halfdepth-1]
        parts.append(('range_1',part,d,[0,0,0],2))
        range_records.append({'id':'range_1','row':0,'continuous_bays':3,'eave_height':height,'contains_ancestral_hall':False})
    for i in range(1,rows):
        h=height+(raised if i==ancestor else 0)
        part,d=range_factory(bay,3,depth,h,rise,materials,p['ridge'] if i==ancestor else 'round')
        name=f'range_{i+1}'
        if i<rows-1:
            # An ancestral hall in the middle retains its rear centre; lateral
            # room passages provide the onward route to the rear compound.
            passages=(-bay,bay) if i==ancestor else (0,)
            for x in passages:
                room='room_'+str((-bay,0,bay).index(x)+1)
                _remove_fixture(part,d,room)
                door=_portal(part,d,x,halfdepth);portals.append((name,door))
                d['target_floors_xyz']['onward_'+room]=[x,1,halfdepth-1]
        if i==ancestor:
            d['target_floors_xyz']['ancestral_hall']=[0,1,0]
        parts.append((name,part,d,[0,0,centers[i]],0))
        range_records.append({'id':name,'row':i,'continuous_bays':3,'eave_height':h,'contains_ancestral_hall':i==ancestor})

    courts=[]
    for i in range(rows-1):
        za=centers[i]+halfdepth;zb=centers[i+1]-halfdepth;cz=(za+zb)//2
        wing,d=range_factory(wb,wc,wd,height-3,max(2,rise-1),materials)
        for name,x,turn in [('west',-hx+wd//2,1),('east',hx-wd//2,3)]:
            parts.append((f'court_{i+1}.{name}',wing,d,[x,0,cz],turn))
        courts.append({'id':f'court_{i+1}','bounds_xz':[-hx+wd+3,za+3,hx-wd-3,zb-3],
                       'front_wall_z':za,'rear_wall_z':zb,'split_z':cz,
                       'lower_floor_y':0,'upper_floor_y':int(p['split_court'])})

    hulong_sides=[] if p['hulong']=='none' else [-1,1] if p['hulong']=='both' else [-1] if p['hulong']=='west' else [1]
    hulong_records=[]
    for sign in hulong_sides:
        name='hulong.west' if sign<0 else 'hulong.east'
        widths=_even_bays(back_z-front_z,hc)
        part,d=range_factory(widths[0],hc,hd,height-2,max(2,rise-1),materials,bay_widths=widths)
        # The facade faces the narrow side court; the independent outside gate
        # belongs to that court and is not an extra door punched through a bed.
        x=sign*(hx+lane+hd//2);turn=1 if sign<0 else 3
        parts.append((name,part,d,[x,0,(front_z+back_z)//2],turn))
        hulong_records.append({'id':name,'side_sign_x':sign,'inner_wall_x':sign*(hx+lane),
                               'outer_wall_x':sign*(hx+lane+hd),'room_bays':widths,
                               'narrow_court_width_including_porch':lane,'porch_width':3,
                               'storeys':1,'original_dimensions':True})

    low=np.min([part.rotated(turn).origin+at for _,part,_,at,turn in parts],axis=0)
    high=np.max([part.rotated(turn).maximum+at for _,part,_,at,turn in parts],axis=0)
    low[1]=-2;low[2]=min(low[2],front_z-5);high[1]+=1
    m=Model(low.tolist(),high.tolist())
    m.box(low.tolist(),(int(high[0]),0,int(high[2])),materials['stone'])
    for x in (-hx,hx):m.box((x,1,front_z),(x,5,back_z),materials['wall'])
    for court in courts:
        if p['split_court']:
            m.box((-hx+1,1,court['split_z']),(hx-1,1,court['rear_wall_z']),materials['stone'])
    for h in hulong_records:
        sign=h['side_sign_x'];outer=h['outer_wall_x'];inner=h['inner_wall_x']
        x0,x1=sorted((sign*hx,outer))
        for z in (front_z,back_z):m.box((x0,1,z),(x1,5,z),materials['wall'])
        m.box((outer,1,front_z),(outer,5,back_z),materials['wall'])
        if p['split_court']:
            a,b=sorted((sign*(hx+1),inner))
            m.box((a,1,(front_z+back_z)//2),(b,1,back_z),materials['stone'])

    targets={};rooms=[];fixtures=[];doors=[];placements={}
    placed={}
    for ident,part,d,at,turn in parts:
        placements[ident]=m.paste(part,offset=at,turns=turn,conflict='replace')
        placed[ident]=(d,at,turn)
        for name,pt in d['target_floors_xyz'].items():targets[ident+'.'+name]=transform(pt,at,turn)
        for room in d['rooms']:
            xa,za,xb,zb=room['bounds_xz']
            rooms.append({'id':ident+'.'+room['id'],'floor_y':1,
                          'corners_xyz':[transform([x,1,z],at,turn) for x in (xa,xb) for z in (za,zb)]})
        for f in d['fixtures']:
            fixtures.append({**f,'id':ident+'.'+f['id'],
                             'cells_xyz':[transform(pt,at,turn) for pt in f['cells_xyz']],
                             'standing_floor_xyz':transform(f['standing_floor_xyz'],at,turn)})
        for door in d['doors']:
            doors.append({'part':ident,'opening_xyz':[transform([x,2,door['z']],at,turn) for x in range(door['x_range'][0],door['x_range'][1]+1)]})
    onward=[]
    for ident,door in portals:
        _,at,turn=placed[ident]
        points=[transform([x,door['floor_y']+1,door['z']],at,turn) for x in range(door['x_range'][0],door['x_range'][1]+1)]
        onward.append({'part':ident,'opening_xyz':points,'clear_height':4})
    sky_points=[]
    for court in courts:
        xa,za,xb,zb=court['bounds_xz']
        for section,z,y in [('lower',za+1,0),('upper',zb-1,int(p['split_court']))]:
            point=[0,y,z];targets[court['id']+'.'+section]=point;sky_points.append(point)

    gates=[{'id':'main','opening_xyz':[[x,2,front_z] for x in (-1,0,1)],'floor_y':1}]
    guoshui=[];side_sky=[]
    for h in hulong_records:
        sign=h['side_sign_x'];inner=h['inner_wall_x']
        # Each opposite roof can project three cells into the side court.
        # Keep the probe and longitudinal path in the unroofed strip between
        # those eaves; eight cells between walls leaves one open column.
        lane_mid=sign*(hx+lane//2)
        # Separate street-facing gate for each side household.
        m.box((lane_mid-1,1,front_z),(lane_mid+1,5,front_z),'minecraft:air')
        gates.append({'id':h['id'],'opening_xyz':[[x,1,front_z] for x in range(lane_mid-1,lane_mid+2)],'floor_y':0})
        targets[h['id']+'.entry']=[lane_mid,1,front_z+1]
        for label,z in [('front',front_z+2),('rear',centers[-1]-halfdepth-1)]:
            a,b=sorted((sign*hx,inner))
            m.box((a,1,z-1),(b,1,z+1),materials['stone'])
            # Connect the core's outer wall/porch to the side-lane porch.
            m.box((sign*hx,2,z-1),(sign*hx,5,z+1),'minecraft:air')
            m.box((a,7,z-1),(b,7,z+1),materials['roof'])
            guoshui.append({'id':h['id']+'.'+label,'floor_y':1,'roof_y':7,
                            'bounds_xz':[a,z-1,b,z+1],'core_opening_xyz':[[sign*hx,2,k] for k in range(z-1,z+2)]})
            targets[h['id']+'.'+label+'_guoshui']=[lane_mid,1,z]
        z=(front_z+centers[-1]-halfdepth-1)//2
        y=int(p['split_court'] and z>=(front_z+back_z)//2)
        targets[h['id']+'.open_lane']=[lane_mid,y,z]
        side_sky.append([lane_mid,y,z])
    checks={'distinct_front_roof_system':sum(r['row']==0 for r in range_records)==(3 if form=='san_gai_lang' else 1),
            'one_highest_ancestral_range':sum(r['eave_height']==height+raised for r in range_records)==1,
            'expected_open_courts':len(courts)==rows-1,
            'core_courts_open_to_sky':all(m.get(x,y,z)=='minecraft:air' for x,f,z in sky_points for y in range(f+1,int(m.maximum[1])+1)),
            'side_courts_open_to_sky':all(m.get(x,y,z)=='minecraft:air' for x,f,z in side_sky for y in range(f+1,int(m.maximum[1])+1)),
            'independent_side_gates':len(gates)==len(hulong_records)+1}
    return m,{'start_floor_xyz':[0,0,front_z-4],'target_floors_xyz':targets,'checks':checks,
              'rooms':rooms,'fixtures':fixtures,'door_openings':doors,'placements':placements,
              'range_rows':range_records,'courts':courts,'onward_portals':onward,'external_gates':gates,
              'hulong':hulong_records,'guoshui':guoshui,'sky_probe_floors_xyz':sky_points+side_sky,
              'front_roof_system':'separate_lower_rooms_and_gate' if form=='san_gai_lang' else 'continuous_three_bay_front_range',
              'continuous_three_bay_roof_ranges':sum(r['continuous_bays']==3 for r in range_records),
              'actual_accessible_storeys':1,
              'spatial_basis':'Distinct front roof systems, serial courts, highest ancestral range, optional independent side household gates and covered cross-lane passages.',
              'limits':'Original dimensioned layout, not a surveyed Kinmen reconstruction. Hulong here is single-storey; the Hong survey documents later tower additions and alterations that are not generated. No measured craft, religious ranking or building physics asserted.'}
