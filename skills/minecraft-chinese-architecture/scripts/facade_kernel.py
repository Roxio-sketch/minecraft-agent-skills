"""Block-scale infill for a clear bay, retaining glazing in narrow panels."""
from __future__ import annotations

import numpy as np


LEGACY_PATTERNS=('cross','plain','lattice','solid')
BOUNDED_PATTERNS=('framed','framed_pair','framed_round','framed_arch','framed_shutter')
PATTERNS=LEGACY_PATTERNS+BOUNDED_PATTERNS


def bounded_roles(width,height,pattern='framed'):
    """A finite window group with a real wall border, independent of bay size.

    Tiny bays cannot fit a wall border; reject them rather than silently making
    either an empty wall or wall-sized glass. Narrow valid bays omit the timber
    rim when necessary, keeping one clear aperture and the outer wall intact.
    """
    if any(type(n) is not int or n<3 for n in (width,height)):
        raise ValueError('Bounded windows need width and height >=3; choose plain/solid for a narrower panel')
    if pattern not in BOUNDED_PATTERNS:raise ValueError('Unknown bounded window')
    result=np.zeros((height,width),np.uint8)
    # Maximum GLASS envelope, not an ever-expanding lattice across the bay.
    gw=min(7,width-4 if width>=5 else width-2)
    gh=min(5,height-4 if height>=5 else height-2)
    x0=(width-gw)//2;y0=(height-gh)//2
    yy,xx=np.indices((gh,gw));cx=(gw-1)/2;cy=(gh-1)/2
    mask=np.ones((gh,gw),bool)
    if pattern=='framed_pair' and gw>=3:
        mask[:,gw//2]=False
        if gw%2==0:mask[:,gw//2-1]=False
    elif pattern=='framed_round':
        mask=((xx-cx)/max(.5,gw/2))**2+((yy-cy)/max(.5,gh/2))**2<=1
    elif pattern=='framed_arch' and gw>=3 and gh>=3:
        cap=gh-1-np.rint((np.abs(xx-cx)/max(1,cx))**2*max(1,gh//3)).astype(int)
        mask=yy<=cap
    elif pattern=='framed_shutter' and gw>=5:
        mask[:,0]=False;mask[:,-1]=False
    result[y0:y0+gh,x0:x0+gw][mask]=1
    for y,x in np.argwhere(result==1):
        for dy,dx in ((1,0),(-1,0),(0,1),(0,-1)):
            iy,ix=int(y+dy),int(x+dx)
            if 0<iy<height-1 and 0<ix<width-1 and result[iy,ix]==0:
                result[iy,ix]=2
    assert np.any(result==1) and not np.any(result[[0,-1]]) and not np.any(result[:,[0,-1]])
    return result


def panel_roles(width,height,pattern='cross',sill_rows=2,head_rows=2):
    """Return rows from bottom up: 0 wall, 1 infill, 2 frame.

    Width excludes adjacent columns; height excludes the top beam. Small
    apertures omit bars that would consume the complete infill. The pattern
    is an authored voxel abstraction, not a historic joinery classification.
    """
    if any(type(n) is not int or n<1 for n in (width,height)):
        raise ValueError('Panel width and height must be positive integers')
    if pattern not in PATTERNS:raise ValueError(f'Unknown panel pattern: {pattern}')
    if any(type(n) is not int or n<0 for n in (sill_rows,head_rows)):
        raise ValueError('Sill and head row counts must be nonnegative integers')
    if pattern in BOUNDED_PATTERNS:return bounded_roles(width,height,pattern)
    result=np.zeros((height,width),dtype=np.uint8)
    if pattern=='solid':return result
    inner_height=height-sill_rows-head_rows
    if inner_height<1:raise ValueError('Sill and head leave no window infill')
    aperture=result[sill_rows:height-head_rows if head_rows else height]
    aperture[:]=1
    if pattern=='cross':
        if width>=3:aperture[:,(width-1)//2]=2
        if inner_height>=3:aperture[(inner_height-1)//2,:]=2
    elif pattern=='lattice':
        # Interior bars only; edge cells remain infill at any valid scale.
        for x in range(1,width-1,2):aperture[:,x]=2
        for y in range(1,inner_height-1,2):aperture[y,:]=2
    return result
