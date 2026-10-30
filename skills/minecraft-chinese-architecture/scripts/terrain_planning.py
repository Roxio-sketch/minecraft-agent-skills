"""Bounded heightfield grading and slope-constrained paths for local scene exports."""
from __future__ import annotations

import hashlib
import heapq
import math
from pathlib import Path

import numpy as np
from scipy.ndimage import binary_dilation, maximum_filter, minimum_filter


def heightfield(origin_xz, size_xz, config):
    if not isinstance(origin_xz,list) or len(origin_xz)!=2 or any(type(v) is not int or abs(v)>100000 for v in origin_xz):
        raise ValueError('Site origin_xz needs two bounded integers')
    if not isinstance(size_xz,list) or len(size_xz)!=2 or any(type(v) is not int or not 32<=v<=512 for v in size_xz):
        raise ValueError('Site size_xz needs two integers 32..512; final model also has an 8-million-voxel budget')
    if not isinstance(config,dict) or set(config)-{'base','grade_z','hills','wave','heightmap'}:raise ValueError('Unknown terrain config')
    w,d=size_xz;x0,z0=origin_xz
    if config.get('heightmap'):
        path=Path(config['heightmap'])
        with np.load(path,allow_pickle=False) as data:
            a=np.asarray(data['height_zx']);o=data['origin_xz']
        if a.shape!=(d,w) or a.dtype.kind not in 'iu' or not np.array_equal(o,origin_xz):raise ValueError('Heightmap dimensions/origin differ from the site')
        if np.min(a)<-64 or np.max(a)>192:raise ValueError('Heightmap surface outside -64..192')
        return a.astype(np.int64),{'type':'heightmap','path':str(path.resolve()),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
    base,grade,wave=config.get('base',3),config.get('grade_z',.08),config.get('wave',1.2)
    for name,v,limit in [('base',base,64),('grade_z',grade,1),('wave',wave,8)]:
        if type(v) not in (int,float) or not math.isfinite(v) or abs(v)>limit:raise ValueError('Invalid terrain '+name)
    z,x=np.mgrid[z0:z0+d,x0:x0+w]
    surface=base+(z-z0)*grade+wave*(np.sin(x/17)+np.cos(z/23))
    hills=config.get('hills',[])
    if not isinstance(hills,list) or len(hills)>8:raise ValueError('At most eight hills')
    for hill in hills:
        if not isinstance(hill,dict) or set(hill)!={'x','z','radius','height'}:raise ValueError('Hill needs x,z,radius,height')
        if any(type(v) not in (int,float) or not math.isfinite(v) for v in hill.values()):raise ValueError('Finite hill parameters required')
        if not 8<=hill['radius']<=160 or not -40<=hill['height']<=64:raise ValueError('Hill radius/height outside supported range')
        surface+=hill['height']*np.exp(-((x-hill['x'])**2+(z-hill['z'])**2)/(2*hill['radius']**2))
    surface=np.rint(surface).astype(np.int64)
    if surface.min()<-64 or surface.max()>192:raise ValueError('Generated surface outside -64..192')
    return surface,{'type':'authored_heightfield','parameters':config,'actual_y_range':[int(surface.min()),int(surface.max())]}


def grade_route(surface, fixed, blocked, start, end, width=5, max_grade=6, node_budget=300000):
    """Find a supported path in (x,z,height), preserving fixed pads and earlier paths.

    Start/end use local array X,Z plus absolute surface Y. Every footprint cell
    must fit the grading allowance; a strict fixed neighborhood may reject tight
    slope transitions rather than silently break an existing component.
    """
    a=np.asarray(surface);fixed=np.asarray(fixed);blocked=np.asarray(blocked)
    if a.ndim!=2 or fixed.shape!=a.shape or blocked.shape!=a.shape or a.dtype.kind not in 'iu':raise ValueError('Aligned integer height and boolean reservation arrays required')
    if a.size==0 or a.min()<-64 or a.max()>192:raise ValueError('Terrain surface outside -64..192')
    a=a.astype(np.int64)
    if fixed.dtype.kind!='b' or blocked.dtype.kind!='b':raise ValueError('Reservations must be boolean arrays')
    if type(width) is not int or width not in (3,5,7):raise ValueError('Path width must be 3,5 or 7')
    if type(max_grade) is not int or not 0<=max_grade<=16:raise ValueError('max_grade must be 0..16')
    if type(node_budget) is not int or not 1<=node_budget<=1000000:raise ValueError('Invalid path search budget')
    r=width//2;h,w=a.shape
    lo=maximum_filter(np.where(fixed,a,a-max_grade),size=width,mode='constant',cval=100000)
    hi=minimum_filter(np.where(fixed,a,a+max_grade),size=width,mode='constant',cval=-100000)
    excluded=maximum_filter(blocked,size=width,mode='constant',cval=True) | (lo>hi)
    def node(value):
        if not isinstance(value,(list,tuple)) or len(value)!=3 or any(type(v) is not int for v in value):raise ValueError('Path node needs local x,z and absolute y integers')
        return tuple(value)
    start,end=node(start),node(end)
    def valid(p):
        x,z,y=p
        return 0<=x<w and 0<=z<h and not excluded[z,x] and lo[z,x]<=y<=hi[z,x]
    if not valid(start) or not valid(end):raise ValueError('Path endpoint conflicts with a reservation or grading limit')
    def heuristic(p):return 10*max(abs(p[0]-end[0])+abs(p[1]-end[1]),abs(p[2]-end[2]))
    queue=[(heuristic(start),0,start)];cost={start:0};prior={start:None};expanded=0
    while queue:
        _,g,p=heapq.heappop(queue)
        if g!=cost[p]:continue
        expanded+=1
        if expanded>node_budget:raise ValueError('Path search budget exhausted; adjust layout or slope constraints')
        if p==end:break
        x,z,y=p
        for dx,dz in ((0,1),(1,0),(0,-1),(-1,0)):
            for dy in (0,1,-1):
                q=(x+dx,z+dz,y+dy)
                if not valid(q):continue
                weight=10+3*abs(dy)+abs(q[2]-int(a[q[1],q[0]]))
                ng=g+weight
                if ng<cost.get(q,1<<60):
                    cost[q]=ng;prior[q]=p;heapq.heappush(queue,(ng+heuristic(q),ng,q))
    else:raise ValueError('No route satisfies terrain, footprint and slope constraints')
    route=[];p=end
    while p is not None:route.append(p);p=prior[p]
    route.reverse()
    if len({(x,z) for x,z,y in route})!=len(route):raise ValueError('Route revisits an XZ column at different elevations; enlarge switchback space')
    center=np.zeros_like(fixed);levels=np.zeros_like(a)
    for x,z,y in route:center[z,x]=True;levels[z,x]=y
    road=binary_dilation(center,structure=np.ones((width,width),bool))
    # Solve a discrete slope envelope over the entire road. Nearest-center
    # assignment can jump two blocks across a Voronoi seam at a rising corner.
    # Propagate upper bounds first, clamp the original surface into feasible
    # bounds, then propagate raises. This keeps already-flat roads unchanged.
    lower=np.where(fixed,a,a-max_grade);upper=np.where(fixed,a,a+max_grade)
    lower[center]=levels[center];upper[center]=levels[center]
    cap=upper.copy();pending=[(int(cap[z,x]),int(x),int(z)) for z,x in zip(*np.where(road))]
    heapq.heapify(pending)
    while pending:
        level,x,z=heapq.heappop(pending)
        if level!=cap[z,x]:continue
        for nx,nz in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if 0<=nx<w and 0<=nz<h and road[nz,nx] and cap[nz,nx]>level+1:
                cap[nz,nx]=level+1;heapq.heappush(pending,(level+1,nx,nz))
    if np.any(road&(lower>cap)):raise ValueError('Widened road folds across incompatible elevations')
    graded=np.maximum(lower,np.minimum(a,cap));pending=[(-int(graded[z,x]),int(x),int(z)) for z,x in zip(*np.where(road))]
    heapq.heapify(pending)
    while pending:
        negative,x,z=heapq.heappop(pending);level=-negative
        if level!=graded[z,x]:continue
        for nx,nz in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if 0<=nx<w and 0<=nz<h and road[nz,nx] and graded[nz,nx]<level-1:
                if level-1>upper[nz,nx]:raise ValueError('Widened road folds across incompatible elevations')
                graded[nz,nx]=level-1;heapq.heappush(pending,(1-level,nx,nz))
    if np.any(road & fixed & (graded!=a)):raise ValueError('Widened road conflicts with a fixed height')
    if np.any(road & ~fixed & (np.abs(graded-a)>max_grade)):raise ValueError('Widened road exceeds grading budget')
    for axis in (0,1):
        adj=np.diff(graded,axis=axis)
        pair=(road[:-1]&road[1:]) if axis==0 else (road[:,:-1]&road[:,1:])
        if np.any(pair & (np.abs(adj)>1)):raise ValueError('Widened road folds across incompatible elevations')
    changed=graded[road]-a[road]
    return {'route_local_xzy':[list(p) for p in route],'mask':road,'heights':graded,
            'cut_cells':int(np.maximum(-changed,0).sum()),'fill_cells':int(np.maximum(changed,0).sum()),
            'expanded_nodes':expanded,'steps':len(route)-1,'max_step':max((abs(b[2]-a[2]) for a,b in zip(route,route[1:])),default=0)}
