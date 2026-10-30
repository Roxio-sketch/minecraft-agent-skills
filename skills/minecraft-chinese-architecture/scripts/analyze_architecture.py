"""Offline maps and auditable voxel measurements; semantic labels need visual review."""
from __future__ import annotations
import argparse, json, re
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

def color(state):
    s=str(state).split('[')[0].split(':')[-1]
    if s in ('air','cave_air','void_air'): return (235,239,244)
    for word,c in [('water',(55,119,164)),('lava',(232,103,29)),('snow',(240,244,249)),('ice',(164,210,232)),
        ('blackstone',(49,51,61)),('deepslate',(68,73,83)),('dark_prismarine',(49,105,98)),('prismarine',(94,161,147)),
        ('quartz',(220,217,207)),('calcite',(218,215,205)),('gold',(213,176,65)),('copper',(172,111,77)),
        ('white',(226,223,214)),('light_gray',(171,174,177)),('gray',(106,111,119)),('black',(45,49,58)),
        ('cyan',(61,126,132)),('blue',(63,97,161)),('purple',(127,79,159)),('magenta',(176,87,164)),
        ('red',(164,64,59)),('orange',(211,130,62)),('yellow',(216,188,77)),('lime',(127,166,68)),
        ('green',(88,121,63)),('pink',(215,154,159)),('brown',(113,83,60)),('leaves',(72,126,64)),
        ('moss',(93,138,67)),('grass',(106,148,72)),('fern',(80,137,66)),('dirt',(133,103,77)),
        ('sand',(208,192,142)),('mud',(106,96,84)),('cherry',(211,164,158)),('birch',(199,182,136)),
        ('spruce',(111,82,55)),('dark_oak',(81,60,45)),('oak',(170,136,88)),('crimson',(123,61,83)),
        ('warped',(50,119,117)),('mangrove',(130,68,57)),('brick',(147,104,92)),('stone',(138,141,143)),
        ('andesite',(143,145,142)),('diorite',(187,188,184)),('tuff',(117,125,117)),('glass',(159,198,210)),
        ('lantern',(230,173,79))]:
        if word in s:return c
    return (154,142,126)

def save_json(path,data):
    Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')

def font(size):
    for p in ['C:/Windows/Fonts/msyh.ttc','C:/Windows/Fonts/arial.ttf']:
        if Path(p).exists():return ImageFont.truetype(p,size)
    return ImageFont.load_default()

def atlas(topmap,out_dir,title):
    out=Path(out_dir);out.mkdir(parents=True,exist_ok=True)
    d=np.load(topmap,allow_pickle=False);p=d['palette'];h=d['top_y'];ids=d['top_id']
    colors=np.array([color(s) for s in p],dtype=np.uint8)
    rgb=colors[ids]
    valid=h>-32768
    # Shade height variation only; no claim of physically correct illumination.
    heights=np.where(valid,h,0).astype(np.float32)
    dz,dx=np.gradient(heights)
    light=np.clip(1.0-0.035*dx-0.045*dz,0.65,1.18)
    rgb=np.clip(rgb.astype(np.float32)*light[:,:,None],0,255).astype(np.uint8)
    rgb[~valid]=(235,239,244)
    im=Image.fromarray(rgb);im.thumbnail((1800,1800),Image.Resampling.NEAREST)
    frame=Image.new('RGB',(im.width+120,im.height+145),'#eff3f7');frame.paste(im,(80,90));draw=ImageDraw.Draw(frame)
    draw.text((26,18),title,fill='#24364b',font=font(27))
    draw.text((26,55),'方块数据俯视图 · 近似材质色 · 北(-Z)向上 · 非游戏截图',fill='#4c6278',font=font(17))
    x0=int(d['min_x']);z0=int(d['min_z']);sx=im.width/h.shape[1];sz=im.height/h.shape[0]
    for x in range((x0//256+1)*256,x0+h.shape[1],256):
        px=80+int((x-x0)*sx);draw.line((px,90,px,90+im.height),fill='#a7adb3',width=1)
        if (x//256)%2==0:draw.text((px-12,91+im.height),str(x),fill='#33485c',font=font(12))
    for z in range((z0//256+1)*256,z0+h.shape[0],256):
        py=90+int((z-z0)*sz);draw.line((80,py,80+im.width,py),fill='#a7adb3',width=1)
        if (z//256)%2==0:draw.text((8,py-6),str(z),fill='#33485c',font=font(12))
    frame.save(out/'atlas.png')
    # Candidate patches of roof-like materials; candidates are not confirmed buildings.
    roof_ids=np.array([bool(re.search(r'(blackstone|deepslate|prismarine|stone_brick|brick|dark_oak|spruce).*(stairs|slab)|(?:cyan|blue|black|gray)_terracotta',str(s))) for s in p])
    roof=roof_ids[ids] & valid
    labels,n=ndimage.label(ndimage.binary_closing(roof,iterations=2))
    candidates=[]
    counts=np.bincount(labels.ravel())
    for idx,sl in enumerate(ndimage.find_objects(labels),1):
        if sl is None or counts[idx]<100:continue
        zz,xx=sl;mask=labels[sl]==idx;ys=h[sl][mask];w=xx.stop-xx.start;l=zz.stop-zz.start
        if w<8 or l<8:continue
        candidates.append({'id':idx,'classification':'roof_material_candidate_unconfirmed','box_xz':[x0+xx.start,z0+zz.start,x0+xx.stop-1,z0+zz.stop-1],
            'size_xz':[w,l],'mask_columns':int(counts[idx]),'top_y_range':[int(ys.min()),int(ys.max())]})
    candidates.sort(key=lambda q:q['mask_columns'],reverse=True)
    save_json(out/'candidates.json',{'topmap':str(Path(topmap).resolve()),'coordinate_origin_xz':[x0,z0],
        'warning':'Material and morphology candidates only; terrain, overlapping roofs and bridges can merge. Inspect before labeling.',
        'candidates':candidates[:150]})
    print(json.dumps({'atlas':str(out/'atlas.png'),'candidate_count':len(candidates)},ensure_ascii=False))

def measure(model,out_dir,roof_pattern):
    out=Path(out_dir);out.mkdir(parents=True,exist_ok=True)
    d=np.load(model,allow_pickle=False);a=d['blocks'];p=d['palette'];origin=d['origin'].astype(int)
    air=np.array([str(s).split('[')[0] in ('minecraft:air','minecraft:cave_air','minecraft:void_air') for s in p]);occupied=~air[a]
    c=np.bincount(a.ravel(),minlength=len(p));order=np.argsort(c)[::-1]
    material_ids={};base=[]
    for s in p:
        name=str(s).split('[')[0]
        if name not in material_ids:material_ids[name]=len(material_ids)
        base.append(material_ids[name])
    b=np.array(base)[a]
    den=max(1,int(occupied.sum()))
    ycounts=occupied.sum(axis=(1,2));layers=[{'y':int(origin[1]+y),'nonair':int(n)} for y,n in enumerate(ycounts) if n]
    roof_ids=np.array([bool(re.search(roof_pattern,str(s))) for s in p]);roof=roof_ids[a]&occupied
    # This is a material-labelled envelope, not an inferred roof component.
    roof_top=np.where(roof,np.arange(a.shape[0])[:,None,None]+origin[1],-32768).max(axis=0)
    zc=a.shape[1]//2;xc=a.shape[2]//2
    def profile(v,offset):return [[int(offset+i),int(y)] for i,y in enumerate(v) if y>-32768]
    vertical=np.array([bool(re.search(r'(_log|_wood|_stem|_hyphae)(\[|$)',str(s))) and ('axis=y' in str(s) or 'axis=' not in str(s)) for s in p])
    columns=[]
    for z,x in np.argwhere(vertical[a].sum(axis=0)>=5):
        v=vertical[a[:,z,x]];edges=np.diff(np.r_[False,v,False].astype(np.int8));starts=np.where(edges==1)[0];ends=np.where(edges==-1)[0]
        for start,end in zip(starts,ends):
            if end-start>=5:columns.append({'x':int(origin[0]+x),'z':int(origin[2]+z),'y0':int(origin[1]+start),'y1':int(origin[1]+end-1),'length':int(end-start)})
    cols_sorted=sorted(columns,key=lambda q:q['length'],reverse=True)
    result={'model':str(Path(model).resolve()),'axis_order':'y,z,x','origin_xyz':origin.tolist(),'shape_xyz':[a.shape[2],a.shape[0],a.shape[1]],
        'nonair_voxels':int(occupied.sum()),'material_palette_count':len(material_ids),'block_state_palette_count':len(p),
        'top_states':[{'state':str(p[i]),'count':int(c[i])} for i in order if c[i] and not air[i]][:80],
        'nonair_by_layer':layers,'material_mirror_mismatch_x':float(((b!=b[:,:,::-1]) & (occupied|occupied[:,:,::-1])).sum()/max(1,(occupied|occupied[:,:,::-1]).sum())),
        'material_mirror_mismatch_z':float(((b!=b[:,::-1,:]) & (occupied|occupied[:,::-1,:])).sum()/max(1,(occupied|occupied[:,::-1,:]).sum())),
        'roof_material_pattern':roof_pattern,'roof_material_voxels':int(roof.sum()),
        'roof_envelope_center_x_profile':profile(roof_top[zc,:],int(origin[0])),
        'roof_envelope_center_z_profile':profile(roof_top[:,xc],int(origin[2])),
        'vertical_wood_runs':cols_sorted[:1000],
        'limitations':['Roof envelope is selected material, possibly multiple roofs/terrain; label before deriving slope.',
        'Wood runs can be trees or decoration; column grid requires visual confirmation.',
        'Material symmetry ignores orientation properties, excludes only paired air, and is sensitive to crop centering.']}
    save_json(out/'measurements.json',result)
    colors=np.array([color(s) for s in p],dtype=np.uint8)
    # Six orthographic first-hit views; transparency approximated opaque, cubes only.
    def first_hit(v):
        mask=~air[v];ix=mask.argmax(axis=0);result=np.take_along_axis(v,ix[None,:,:],axis=0)[0];result[~mask.any(axis=0)]=np.where(air)[0][0];return colors[result]
    views={'top':first_hit(a[::-1]),'front':np.flipud(first_hit(a.transpose(1,0,2))),
           'back':np.flipud(first_hit(a[:,::-1,:].transpose(1,0,2))),
           'side':np.flipud(first_hit(a.transpose(2,0,1)))}
    for name,rgb in views.items():
        im=Image.fromarray(rgb);scale=max(1,min(8,1200//max(im.size)));im=im.resize((im.width*scale,im.height*scale),Image.Resampling.NEAREST);im.save(out/f'{name}.png')
    # Labelled selected material profile for visual measurement, using per-block rows.
    print(json.dumps({'measurements':str(out/'measurements.json'),'nonair':den,'wood_runs':len(columns)},ensure_ascii=False))

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);sp=ap.add_subparsers(dest='command',required=True)
    at=sp.add_parser('atlas');at.add_argument('--topmap',required=True);at.add_argument('--out-dir',required=True);at.add_argument('--title',default='建筑样本')
    me=sp.add_parser('measure');me.add_argument('--model',required=True);me.add_argument('--out-dir',required=True);me.add_argument('--roof-pattern',default='blackstone|deepslate|prismarine')
    args=ap.parse_args()
    if args.command=='atlas':atlas(args.topmap,args.out_dir,args.title)
    else:measure(args.model,args.out_dir,args.roof_pattern)
