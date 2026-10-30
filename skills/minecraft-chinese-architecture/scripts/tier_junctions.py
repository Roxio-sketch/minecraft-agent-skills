"""Declared storey/roof-drum interfaces, independent of overall connectivity.

Sills and floors meet a closed collar; upper posts bear on transfer beams tied
to explicit lower frame anchors. These are voxel construction checks, not
structural engineering. Roof tiles are never inferred to be bearing beams.
"""
from __future__ import annotations
import copy
from functools import lru_cache
import numpy as np
from voxel_model import split_state, join_state, vector3
from block_shapes import shape_for

AIR='minecraft:air'
DEFAULT={
    'schema':'chinese-tier-junction/1','id':'tier-junction','kind':'roof_drum',
    'lower_frame_xz':[-16,-10,16,10],'lower_beam_y':13,
    'upper_frame_xz':[-12,-7,12,7],'upper_floor_y':18,
    'roof_opening_xz':[-12,-7,12,7],
    'column_centers_xz':[[-12,-7],[0,-7],[12,-7],[-12,7],[0,7],[12,7]],
    'deck_margin':2,'collar_thickness':2,'floor_thickness':2,'post_radius':1,
    'beam_axis':'z','bearing_offsets_xz':[],
    'voids':[],'protected':[],
    'palette':{'beam':'minecraft:dark_oak_planks','collar':'minecraft:dark_oak_planks',
               'floor':'minecraft:dark_oak_planks'},
    'solid_blocks':['minecraft:dark_oak_planks','minecraft:oak_planks',
                    'minecraft:smooth_quartz','minecraft:quartz_block','minecraft:stone_bricks'],
    'shape_aliases':{},'evidence':[]}


def rect(value,name):
    a=np.asarray(value)
    if a.shape!=(4,) or a.dtype.kind not in 'iu' or a.dtype.kind=='b':raise ValueError(name+' needs four integers x0,z0,x1,z1')
    a=[int(v) for v in a]
    if a[0]>=a[2] or a[1]>=a[3]:raise ValueError(name+' is reversed or degenerate')
    return a


def config_for(user):
    if not isinstance(user,dict) or set(user)-set(DEFAULT):raise ValueError('Unknown tier junction keys')
    c=copy.deepcopy(DEFAULT);c.update(copy.deepcopy(user))
    c['palette']={**DEFAULT['palette'],**user.get('palette',{})}
    if c['schema']!=DEFAULT['schema'] or c['kind'] not in ('roof_drum','storey'):raise ValueError('Invalid junction schema/kind')
    if not isinstance(c['id'],str) or not c['id']:raise ValueError('Junction id required')
    for k in ('lower_frame_xz','upper_frame_xz','roof_opening_xz'):c[k]=rect(c[k],k)
    lo,up=c['lower_frame_xz'],c['upper_frame_xz']
    if not (lo[0]<=up[0]<up[2]<=lo[2] and lo[1]<=up[1]<up[3]<=lo[3]):raise ValueError('Upper frame must fit within lower frame; unsupported overhang needs a different explicit design')
    for k in ('lower_beam_y','upper_floor_y'):
        if type(c[k])!=int:raise ValueError(k+' must be integer block Y')
    if not 2<=c['upper_floor_y']-c['lower_beam_y']<=48:raise ValueError('Junction rise must be 2..48 blocks; resolve excessive level mismatch instead of filling a tall box')
    for k,low,high in [('deck_margin',1,6),('collar_thickness',1,6),('floor_thickness',1,3),('post_radius',0,2)]:
        if type(c[k])!=int or not low<=c[k]<=high:raise ValueError('Invalid '+k)
    if c['floor_thickness']>=c['upper_floor_y']-c['lower_beam_y']:raise ValueError('Floor thickness consumes the interface')
    cols=c['column_centers_xz']
    if not isinstance(cols,list) or not 4<=len(cols)<=128:raise ValueError('Declare 4..128 actual upper column centers')
    for p in cols:
        if len(p)!=2 or any(type(v)!=int for v in p) or not(up[0]<=p[0]<=up[2] and up[1]<=p[1]<=up[3]):raise ValueError('Upper column outside its frame')
    if len(set(map(tuple,cols)))!=len(cols):raise ValueError('Duplicate upper column centers')
    if c['beam_axis'] not in ('x','z','cross'):raise ValueError('beam_axis must be x, z or cross')
    offsets=c['bearing_offsets_xz']
    if offsets==[]:c['bearing_offsets_xz']=[[0,0] for _ in cols]
    elif not isinstance(offsets,list) or len(offsets)!=len(cols):raise ValueError('Provide one bearing offset per column')
    for (x,z),off in zip(cols,c['bearing_offsets_xz']):
        if len(off)!=2 or any(type(v)!=int or abs(v)>8 for v in off):raise ValueError('Bearing offsets must be integer XZ within +/-8')
        if not(lo[0]<=x+off[0]<=lo[2] and lo[1]<=z+off[1]<=lo[3]):raise ValueError('Offset bearing falls outside lower frame')
    if not isinstance(c['voids'],list):raise ValueError('voids must be explicit clearance boxes')
    for hole in c['voids']:
        if not isinstance(hole,dict) or set(hole)!={'id','min','max'}:raise ValueError('void needs id,min,max')
        a=vector3(hole['min'],'void min');b=vector3(hole['max'],'void max')
        if np.any(a>b):raise ValueError('Reversed void')
    if c['kind']=='storey' and not c['voids']:raise ValueError('An occupied upper storey needs a declared stairwell/entry clearance; roof drums are not walkable storeys')
    if set(c['palette'])!=set(DEFAULT['palette']):raise ValueError('Unknown junction palette roles')
    if not isinstance(c['solid_blocks'],list) or not c['solid_blocks']:raise ValueError('Declare verified full-cube solid_blocks')
    for b in c['solid_blocks']:
        base,props=split_state(b)
        if props or base in ('minecraft:air','minecraft:water','minecraft:lava') or base.endswith(('_stairs','_slab','_fence','_wall','_pane','_carpet')):
            raise ValueError('solid_blocks must be verified full-cube base IDs, not thin/partial/fluid blocks')
    for state in c['palette'].values():
        if split_state(state)[0] not in c['solid_blocks']:raise ValueError('Junction materials require explicit full-cube declarations')
    if not isinstance(c['shape_aliases'],dict):raise ValueError('shape_aliases must be an object')
    for src,dst in c['shape_aliases'].items():
        if split_state(src)[1] or split_state(dst)[1] or not dst.startswith('minecraft:'):raise ValueError('shape aliases map base IDs to verified vanilla base IDs')
    if not isinstance(c['protected'],list):raise ValueError('protected must declare occupied stair parts with exact states')
    for part in c['protected']:
        if not isinstance(part,dict) or set(part)!={'id','min','max','state'}:raise ValueError('protected part needs id,min,max,state')
        a=vector3(part['min'],'protected min');b=vector3(part['max'],'protected max')
        if np.any(a>b):raise ValueError('Reversed protected part')
        base,props=split_state(part['state'])
        alias=join_state(c['shape_aliases'].get(base,base),props)
        shape,status=shape_for(alias)
        full=base in c['solid_blocks'] or (status=='supported' and bool(shape.all()))
        stair=alias.split('[')[0].endswith('_stairs') and status=='supported' and props.get('half','bottom')=='bottom'
        if not full and not stair:raise ValueError('Protected circulation must be a verified full block or bottom stair, not air, a fluid or unknown fallback')
    return c


def geometry(c):
    """Return named inclusive boxes, and existing anchors which may not be made up."""
    lx0,lz0,lx1,lz1=c['lower_frame_xz'];ux0,uz0,ux1,uz1=c['upper_frame_xz']
    rx0,rz0,rx1,rz1=c['roof_opening_xz'];margin=c['deck_margin']
    x0=min(ux0-margin,rx0-1);x1=max(ux1+margin,rx1+1)
    z0=min(uz0-margin,rz0-1);z1=max(uz1+margin,rz1+1)
    if x1-x0>1024 or z1-z0>1024:raise ValueError('Excessive junction extent')
    low=c['lower_beam_y'];fy=c['upper_floor_y'];th=c['collar_thickness'];r=c['post_radius']
    if 2*th>=min(x1-x0,z1-z0):raise ValueError('Collar has no interior')
    regions=[];anchors=set()
    def add(role,a,b):
        if all(v<=w for v,w in zip(a,b)):regions.append({'role':role,'min':list(a),'max':list(b)})
    add('floor',(x0,fy-c['floor_thickness']+1,z0),(x1,fy,z1))
    add('collar',(x0,low+1,z0),(x1,fy-1,z0+th-1))
    add('collar',(x0,low+1,z1-th+1),(x1,fy-1,z1))
    add('collar',(x0,low+1,z0+th),(x0+th-1,fy-1,z1-th))
    add('collar',(x1-th+1,low+1,z0+th),(x1,fy-1,z1-th))
    for (x,z),(dx,dz) in zip(c['column_centers_xz'],c['bearing_offsets_xz']):
        bx,bz=x+dx,z+dz
        if c['beam_axis'] in ('z','cross'):
            add('beam',(max(lx0,bx-r),low,lz0),(min(lx1,bx+r),low,lz1))
            anchors.update(((bx,low,lz0),(bx,low,lz1)))
        if c['beam_axis'] in ('x','cross'):
            add('beam',(lx0,low,max(lz0,bz-r)),(lx1,low,min(lz1,bz+r)))
            anchors.update(((lx0,low,bz),(lx1,low,bz)))
        add('beam',(bx-r,low+1,bz-r),(bx+r,fy-1,bz+r))
        if dx or dz:
            # Explicit short transfer at the upper floor underside, allowing
            # an independently checked shaft to pass beside its bearing post.
            add('beam',(min(x,bx)-r,fy-c['floor_thickness'],min(z,bz)-r),
                       (max(x,bx)+r,fy-c['floor_thickness'],max(z,bz)+r))
    return regions,[list(p) for p in sorted(anchors)]


def _slice(m,a,b):
    a=np.array(a,dtype=int);b=np.array(b,dtype=int)
    if np.any(a<m.origin) or np.any(b>m.maximum):raise ValueError('Junction or clearance falls outside final model')
    p=a-m.origin;q=b-m.origin
    return m.blocks[p[1]:q[1]+1,p[2]:q[2]+1,p[0]:q[0]+1]


def _void_mask(a,b,voids):
    shape=(b[1]-a[1]+1,b[2]-a[2]+1,b[0]-a[0]+1)
    mask=np.ones(shape,dtype=bool)
    for v in voids:
        lo=np.maximum(a,v['min']);hi=np.minimum(b,v['max'])
        if np.any(lo>hi):continue
        p=lo-a;q=hi-a
        mask[p[1]:q[1]+1,p[2]:q[2]+1,p[0]:q[0]+1]=False
    return mask


def full_cube_flags(m,solid_blocks,shape_aliases=None):
    """Never silently treat a shape_for cube fallback as verified solid."""
    flags=[];solid=set(solid_blocks);aliases=shape_aliases or {}
    for state in m.palette:
        base,props=split_state(state)
        if base in solid:flags.append(True);continue
        if base in aliases:state=join_state(aliases[base],props)
        shape,status=shape_for(state)
        flags.append(status=='supported' and bool(shape.all()))
    return np.asarray(flags,dtype=bool)


def audit_tier_junction(m,user,limit=30):
    c=config_for(user);regions,anchors=geometry(c)
    flags=full_cube_flags(m,c['solid_blocks'],c['shape_aliases'])
    checks={'closed_perimeter_collar':True,'complete_declared_floor':True,
      'continuous_column_bearings':True,'lower_frame_anchors_solid':True,'declared_voids_clear':True,
      'protected_occupancy_intact':True}
    errors=[];totals={'collar':0,'floor':0,'beam':0};keys={'collar':'closed_perimeter_collar','floor':'complete_declared_floor','beam':'continuous_column_bearings'}
    for v in regions:
        a=np.array(v['min']);b=np.array(v['max']);region=_slice(m,a,b)
        # Partial stair surfaces may replace only declared deck/collar cells.
        # A beam still needs a full solid at every point of its bearing path.
        selected=_void_mask(a,b,c['voids']+(c['protected'] if v['role']!='beam' else []))
        bad=selected & ~flags[region];totals[v['role']]+=int(selected.sum())
        if np.any(bad):
            checks[keys[v['role']]]=False
            for y,z,x in np.argwhere(bad)[:max(0,limit-len(errors))]:
                errors.append({'role':v['role'],'xyz':(a+[x,y,z]).tolist(),'actual':m.palette[int(region[y,z,x])]})
    for xyz in anchors:
        q=np.array(xyz)-m.origin
        if not flags[int(m.blocks[q[1],q[2],q[0]])]:
            checks['lower_frame_anchors_solid']=False
            if len(errors)<limit:errors.append({'role':'lower_anchor','xyz':xyz,'actual':m.get(*xyz)})
    for hole in c['voids']:
        a=np.array(hole['min']);b=np.array(hole['max']);v=_slice(m,a,b)
        if np.any(v):
            checks['declared_voids_clear']=False
            if len(errors)<limit:errors.append({'role':'blocked_void','id':hole['id'],'nonair':int(np.count_nonzero(v))})
    for part in c['protected']:
        v=_slice(m,part['min'],part['max'])
        # Compare normalized complete states, never identity alone: reversed,
        # top-half or changed corner stairs can have the same registry ID.
        wanted=split_state(part['state'])
        matches=np.array([split_state(s)==wanted for s in m.palette],bool)
        count=int(np.count_nonzero(~matches[v]))
        if count:
            checks['protected_occupancy_intact']=False
            if len(errors)<limit:errors.append({'role':'changed_stair_part','id':part['id'],'wrong_cells':count,'expected':part['state']})
    return {'id':c['id'],'kind':c['kind'],'checks':checks,'passed':all(checks.values()),
      'checked_cells_by_role':totals,'errors':errors,'error_examples_limited_to':limit,
      'scope':'Complete declared floor minus explicit air/occupied staircase parts, exact protected states, closed collar and full-cube bearings. No physical load or live collision simulation.'}


def assemble_tier_junction(m,user):
    """Mutate a NEW/authorized model atomically after checking real anchor blocks.

The repair owns only its declared interface boxes. Existing roof blocks inside
the collar become full sill blocks to avoid half-block slots. Air clearances
are excluded and must already be clear; this function never seals a staircase.
"""
    c=config_for(user);regions,anchors=geometry(c)
    flags=full_cube_flags(m,c['solid_blocks'],c['shape_aliases'])
    for xyz in anchors:
        v=_slice(m,xyz,xyz)
        if not flags[int(v[0,0,0])]:raise ValueError(f'No verified lower-frame bearing at {xyz}; add/align the frame, not a floating collar')
    for hole in c['voids']:
        if np.any(_slice(m,hole['min'],hole['max'])):raise ValueError('Declared circulation void already blocked: '+hole['id'])
    for i,part in enumerate(c['protected']):
        out=_slice(m,part['min'],part['max']);wanted=split_state(part['state'])
        matches=np.array([split_state(s)==wanted for s in m.palette],bool)
        if not np.all(matches[out]):raise ValueError('Protected staircase part is already missing/changed: '+part['id'])
        a=np.array(part['min']);b=np.array(part['max'])
        if not _void_mask(a,b,c['voids']).all():raise ValueError('Occupied stair part conflicts with declared air: '+part['id'])
        for prior in c['protected'][:i]:
            if split_state(prior['state'])==wanted:continue
            if np.all(np.maximum(a,prior['min'])<=np.minimum(b,prior['max'])):
                raise ValueError('Conflicting protected staircase states')
    for v in regions:
        _slice(m,v['min'],v['max'])
        if v['role']=='beam' and not _void_mask(np.array(v['min']),np.array(v['max']),c['voids']).all():
            raise ValueError('Bearing beam/post conflicts with circulation void; reroute the structure explicitly')
        if v['role']=='beam':
            for part in c['protected']:
                a=np.maximum(v['min'],part['min']);b=np.minimum(v['max'],part['max'])
                if np.all(a<=b) and not np.all(flags[_slice(m,a,b)]):
                    raise ValueError('Bearing beam/post conflicts with partial stair surface; reroute the bearing explicitly')
    written=0
    for v in regions:
        a=np.array(v['min']);b=np.array(v['max']);out=_slice(m,a,b);mask=_void_mask(a,b,c['voids']+c['protected'])
        state=c['palette'][v['role']];new=m.pid(state);written+=int(np.count_nonzero(mask & (out!=new)))
        out[mask]=new
    audit=audit_tier_junction(m,c)
    if not audit['passed']:raise AssertionError(audit)
    return {'config':c,'regions':regions,'lower_anchor_xyz':anchors,'changed_cells_in_operations':written,'audit':audit}


def audit_floor_plate(m,footprint_xz,y,*,solid_blocks,void_xz=(),shape_aliases=None):
    """Check EVERY floor cell; a single reachable point does not prove a floor."""
    x0,z0,x1,z1=rect(footprint_xz,'floor footprint');a=np.array([x0,y,z0]);b=np.array([x1,y,z1])
    mask=np.ones((1,z1-z0+1,x1-x0+1),bool)
    for v in void_xz:
        hx0,hz0,hx1,hz1=rect(v,'floor opening')
        lo=np.maximum([hx0,hz0],[x0,z0]);hi=np.minimum([hx1,hz1],[x1,z1])
        if np.any(lo>hi):continue
        mask[:,lo[1]-z0:hi[1]-z0+1,lo[0]-x0:hi[0]-x0+1]=False
    flags=full_cube_flags(m,solid_blocks,shape_aliases);v=_slice(m,a,b)
    bad=mask & ~flags[v]
    return {'floor_y':y,'required_cells':int(mask.sum()),'missing_or_partial':int(bad.sum()),
      'passed':not np.any(bad),'bad_examples_xyz':[(a+[x,yy,z]).tolist() for yy,z,x in np.argwhere(bad)[:20]]}


def stair_clearance_boxes(stair_designs,min_y,max_y,extra_boxes=()):
    """Clip declared staircase head space to a particular floor interface.

Uses the repaired staircase kernel's rows rather than guessing a rectangular
shaft through every floor. Flat cross-landings can add explicit extra boxes;
their corners must not incorrectly include a departing rising tread.
"""
    if type(min_y)!=int or type(max_y)!=int or min_y>max_y:raise ValueError('Invalid interface height range')
    from stair_kernel import row_position
    holes=[]
    for j,d in enumerate(stair_designs):
        for row in d['rows']:
            lo=max(min_y,row['y']+1);hi=min(max_y,row['y']+d['config']['headroom'])
            if lo>hi:continue
            a=row_position(d['config'],row['t'],d['lanes'][0]);b=row_position(d['config'],row['t'],d['lanes'][1])
            holes.append({'id':f'flight-{j}-row-{row["t"]}','min':[min(a[0],b[0]),lo,min(a[1],b[1])],
                          'max':[max(a[0],b[0]),hi,max(a[1],b[1])]})
    for extra in extra_boxes:
        v=copy.deepcopy(extra);v['min'][1]=max(v['min'][1],min_y);v['max'][1]=min(v['max'][1],max_y)
        if v['min'][1]<=v['max'][1]:holes.append(v)
    return holes


def stair_protected_boxes(stair_designs,min_y,max_y,landings=(),include_support=True):
    """Declare exact occupied cells, separate from air headroom.

    Rows come from the staircase design, not from whatever happens to be in
    a possibly defective final model. Cross-landings provide min/max/state.
    This helper never authorizes an arbitrary unverified floor hole.
    """
    if type(min_y)!=int or type(max_y)!=int or min_y>max_y:raise ValueError('Invalid interface height range')
    from stair_kernel import row_position
    parts=[]
    def add(ident,a,b,state):
        a=list(a);b=list(b);a[1]=max(min_y,a[1]);b[1]=min(max_y,b[1])
        if a[1]<=b[1]:parts.append({'id':ident,'min':a,'max':b,'state':state})
    for j,d in enumerate(stair_designs):
        c=d['config'];step=c['palette']['stairs']+f'[facing={c["facing"]},half=bottom,shape=straight,waterlogged=false]'
        for row in d['rows']:
            a=row_position(c,row['t'],d['lanes'][0]);b=row_position(c,row['t'],d['lanes'][1]);y=row['y']
            x0,z0=min(a[0],b[0]),min(a[1],b[1]);x1,z1=max(a[0],b[0]),max(a[1],b[1])
            tag=f'flight-{j}-row-{row["t"]}'
            add(tag,[x0,y,z0],[x1,y,z1],step if row['kind']=='riser' else c['palette']['floor'])
            if include_support and c['foundation_y']<y:
                add(tag+'-support',[x0,c['foundation_y'],z0],[x1,y-1,z1],c['palette']['foundation'])
    for j,part in enumerate(landings):add(part.get('id',f'landing-{j}'),part['min'],part['max'],part['state'])
    return parts
