"""Coordinate-labelled material slices, useful when roofs or terrain hide rooms."""
from __future__ import annotations

import argparse,json,math,re
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image,ImageDraw
from render_model import font


COLORS={'air':(248,247,242),'terrain':(140,143,139),'masonry':(190,176,147),
        'wood':(144,94,56),'roof':(60,87,95),'plant':(107,155,89),'water':(94,167,199),
        'stairs':(108,133,185),'door':(211,83,110),'container':(167,100,182),
        'ladder':(237,171,68),'window':(162,205,207),'other':(209,202,187)}


def family(state):
    base=state.split('[')[0]
    if base in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):return 'air'
    if re.search(r'(chest|barrel)$',base):return 'container'
    if re.search(r'_door$',base):return 'door'
    if 'ladder' in base:return 'ladder'
    if 'roof' in base:return 'roof'
    if base.endswith('_stairs'):return 'stairs'
    if any(s in base for s in ('window','glass','lattice')):return 'window'
    if any(s in base for s in ('leaves','grass','bamboo','sapling','flower','rice','vine')):return 'plant'
    if base=='minecraft:water':return 'water'
    if re.search(r'(log|wood|planks|fence|support|timber|beam)',base):return 'wood'
    if base in ('minecraft:stone','minecraft:andesite','minecraft:granite','minecraft:diorite','minecraft:dirt','minecraft:gravel','minecraft:coarse_dirt'):return 'terrain'
    if any(s in base for s in ('brick','masonry','sandstone','mud','cobblestone','smooth_stone')):return 'masonry'
    return 'other'


def slice_plane(blocks,origin,axis,coordinate):
    if axis not in 'xyz' or len(axis)!=1:raise ValueError('Axis must be x, y or z')
    oi='xyz'.index(axis);index=coordinate-int(origin[oi])
    limit={'x':blocks.shape[2],'y':blocks.shape[0],'z':blocks.shape[1]}[axis]
    if not 0<=index<limit:raise ValueError(f'{axis}={coordinate} is outside model')
    if axis=='y':return blocks[index,:,:],{'horizontal':'x','vertical':'z','top_left_xyz':[int(origin[0]),coordinate,int(origin[2])],'vertical_step':1}
    if axis=='x':return blocks[::-1,:,index],{'horizontal':'z','vertical':'y','top_left_xyz':[coordinate,int(origin[1]+blocks.shape[0]-1),int(origin[2])],'vertical_step':-1}
    return blocks[::-1,index,:],{'horizontal':'x','vertical':'y','top_left_xyz':[int(origin[0]),int(origin[1]+blocks.shape[0]-1),coordinate],'vertical_step':-1}


def render(model,output,axis,coordinates,title=None):
    if not coordinates or len(coordinates)>12:raise ValueError('Choose 1..12 slices')
    with np.load(model,allow_pickle=False) as d:a,p,o=d['blocks'],d['palette'].tolist(),d['origin']
    planes=[slice_plane(a,o,axis,v) for v in coordinates]
    panel_w,panel_h=700,700;cols=min(2,len(planes));rows=math.ceil(len(planes)/cols)
    image=Image.new('RGB',(cols*panel_w,rows*panel_h+145),(250,249,246));draw=ImageDraw.Draw(image)
    draw.text((24,16),title or f'{Path(model).stem} | material sections',font=font(26),fill=(30,40,45))
    lookup=np.array([COLORS[family(s)] for s in p],np.uint8);records=[]
    for n,((plane,meta),coord) in enumerate(zip(planes,coordinates)):
        px=(n%cols)*panel_w;py=(n//cols)*panel_h+70
        h,w=plane.shape;scale=max(1,min(600//w,600//h));left=px+65;top=py+60
        tile=Image.fromarray(lookup[plane]).resize((w*scale,h*scale),Image.Resampling.NEAREST)
        image.paste(tile,(left,top));draw.text((px+30,py),f'{axis.upper()} = {coord}; {meta["horizontal"]} right / {meta["vertical"]} '+('down' if meta['vertical_step']==1 else 'up'),font=font(21),fill=(30,40,45))
        horizontal_origin=meta['top_left_xyz']['xyz'.index(meta['horizontal'])]
        vertical_origin=meta['top_left_xyz']['xyz'.index(meta['vertical'])]
        for x in range(w+1):
            if scale>=8:draw.line((left+x*scale,top,left+x*scale,top+h*scale),fill=(224,223,217),width=1)
            if x<w and (horizontal_origin+x)%5==0:draw.text((left+x*scale,top-20),str(horizontal_origin+x),font=font(12),fill=(60,60,60))
        for y in range(h+1):
            if scale>=8:draw.line((left,top+y*scale,left+w*scale,top+y*scale),fill=(224,223,217),width=1)
            value=vertical_origin+meta['vertical_step']*y
            if y<h and value%5==0:draw.text((left-30,top+y*scale),str(value),font=font(12),fill=(60,60,60))
        states={p[int(i)]:int(count) for i,count in zip(*np.unique(plane,return_counts=True))}
        records.append({'axis':axis,'coordinate':coord,**meta,'state_counts':states})
    footer=rows*panel_h+70
    for i,(key,rgb) in enumerate(COLORS.items()):
        x=20+(i%7)*min(180,(cols*panel_w-40)//7);y=footer+(i//7)*26
        draw.rectangle((x,y,x+14,y+14),fill=rgb);draw.text((x+19,y-3),key,font=font(14),fill=(40,40,40))
    output=Path(output);output.parent.mkdir(parents=True,exist_ok=True);image.save(output)
    report={'schema':'architecture-material-sections/1','model':str(Path(model).resolve()),'origin_xyz':o.tolist(),
            'slices':records,'limits':'Exact block-cell slices; colors are name-based material families, not semantic building parts or real texture/collision shapes. Source NPZ may represent omitted positions as air.'}
    output.with_suffix('.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    return report


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('model',type=Path);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--axis',choices=('x','y','z'),default='y');p.add_argument('--at',nargs='+',type=int,required=True);p.add_argument('--title')
    a=p.parse_args();r=render(a.model,a.out,a.axis,a.at,a.title);print(json.dumps({'image':str(a.out),'slice_count':len(r['slices'])}))
