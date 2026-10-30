"""Measure explicitly annotated components; annotations supply semantics, not this script."""
import argparse,json,re,collections
from pathlib import Path
import numpy as np
from block_shapes import shape_for


def shape_line_profile(voxels, palette, selected, box, axis, fixed_cell):
    """Upper surface at a half-block section line, compressed into 1/16 runs.

    A run's end is exclusive; height is the upper face, not the block's Y.
    Unsupported shape states are exposed in the result instead of hidden.
    """
    count=voxels.shape[2] if axis=='x' else voxels.shape[1]
    missing=-(1<<60)
    heights=np.full(count*16,missing,np.int64);fallbacks=set()
    for along in range(count):
        iz,ix=(fixed_cell,along) if axis=='x' else (along,fixed_cell)
        for iy in range(voxels.shape[0]):
            if not selected[iy,iz,ix]:continue
            state=str(palette[voxels[iy,iz,ix]])
            shape,status=shape_for(state)
            if status!='supported':fallbacks.add(state)
            section=shape[:,8,:] if axis=='x' else shape[:,:,8]
            top=np.where(section,np.arange(1,17)[:,None],-32768).max(axis=0)
            present=top>0
            row=heights[along*16:(along+1)*16]
            row[present]=np.maximum(row[present],(box[1]+iy)*16+top[present])
    start=box[0] if axis=='x' else box[2]
    runs=[]
    for i,h in enumerate(heights):
        if h==missing:continue
        coordinate=start*16+i
        if runs and runs[-1][1]==coordinate and runs[-1][2]==int(h):runs[-1][1]+=1
        else:runs.append([coordinate,coordinate+1,int(h)])
    return {'axis':axis,'fixed_coordinate_1_16':(box[2] if axis=='x' else box[0])*16+fixed_cell*16+8,
            'runs_start_end_exclusive_surface_y_1_16':runs,'approximate_shape_states':sorted(fallbacks)}

def run(samples_dir,annotations,out):
    root=Path(samples_dir);items=json.loads(Path(annotations).read_text(encoding='utf-8'))['samples'];results=[]
    for item in items:
        path=root/item.get('model',item['id']+'.npz');d=np.load(path,allow_pickle=False);a=d['blocks'];p=d['palette'];o=d['origin']
        def crop(box):
            x0,y0,z0,x1,y1,z1=box
            if x0<o[0] or y0<o[1] or z0<o[2] or x1>=o[0]+a.shape[2] or y1>=o[1]+a.shape[0] or z1>=o[2]+a.shape[1]:raise ValueError('Annotation outside crop: '+item['id'])
            return a[y0-o[1]:y1-o[1]+1,z0-o[2]:z1-o[2]+1,x0-o[0]:x1-o[0]+1]
        box=item['roof_box'];v=crop(box);valid=np.array([bool(re.search(item['roof_pattern'],str(s))) for s in p])[v]
        yy,zz,xx=np.where(valid)
        if len(yy)==0:raise ValueError('Empty selected roof material: '+item['id'])
        hist=collections.Counter()
        for i,n in zip(*np.unique(v[valid],return_counts=True)):hist[str(p[i]).split('[')[0]]+=int(n)
        top=np.where(valid,np.arange(v.shape[0])[:,None,None]+box[1],-32768).max(axis=0)
        center_x=(int(xx.min())+int(xx.max()))//2;center_z=(int(zz.min())+int(zz.max()))//2
        def profile(vals,start):return [[int(start+i),int(y)] for i,y in enumerate(vals) if y>-32768]
        roof={'material_filter':item['roof_pattern'],'bounds_xyz_inclusive':[int(xx.min()+box[0]),int(yy.min()+box[1]),int(zz.min()+box[2]),int(xx.max()+box[0]),int(yy.max()+box[1]),int(zz.max()+box[2])],
            'span_xz':[int(xx.max()-xx.min()+1),int(zz.max()-zz.min()+1)],'y_range':[int(yy.min()+box[1]),int(yy.max()+box[1])],
            'material_counts':dict(hist.most_common()),'material_fraction':{s:round(n/len(yy),6) for s,n in hist.most_common()},
            'center_x_coordinate':int(center_x+box[0]),'center_z_coordinate':int(center_z+box[2]),
            'x_profile':profile(top[center_z],box[0]),'z_profile':profile(top[:,center_x],box[2])}
        if item.get('shape_profiles',False):
            roof['shape_surface_profiles']=[shape_line_profile(v,p,valid,box,'x',center_z),shape_line_profile(v,p,valid,box,'z',center_x)]
        wood=np.array([bool(re.search(r'(_log|_wood|_stem|_hyphae)(\[|$)',str(s))) and ('axis=y' in str(s) or 'axis=' not in str(s)) for s in p])
        rows={}
        for key in ['column_row','depth_row']:
            if key not in item:continue
            row=item[key];lo,hi=row['range'];y0,y1=row['y_range'];coords=[]
            selected_material=wood if 'material_pattern' not in row else np.array([bool(re.search(row['material_pattern'],str(s))) for s in p])
            for pos in range(lo,hi+1):
                x,z=(pos,row['fixed']) if row['axis']=='x' else (row['fixed'],pos)
                vox=crop([x,y0,z,x,y1,z]).ravel()
                if selected_material[vox].all():coords.append(pos)
            rows[key]={'axis':row['axis'],'fixed_coordinate':row['fixed'],'wood_segment_y_range':[y0,y1],
                'wood_segment_height':y1-y0+1,'positions':coords,'spacings':np.diff(coords).tolist(),
                'material_filter':row.get('material_pattern','vertical log/wood/stem/hyphae'),
                'interpretation':'Continuous selected material over annotated height; role supplied by the annotation.'}
            if 'material_pattern' in row:
                rows[key]['material_segment_y_range']=rows[key].pop('wood_segment_y_range')
                rows[key]['material_segment_height']=rows[key].pop('wood_segment_height')
        provenance_path=path.with_suffix('.import.json')
        if not provenance_path.exists():provenance_path=path.with_suffix('.json')
        provenance=json.loads(provenance_path.read_text(encoding='utf-8'))
        if item.get('source_sha256',provenance['sha256'])!=provenance['sha256']:
            raise ValueError('Source hash differs from annotation: '+item['id'])
        results.append({'id':item['id'],'label':item['label'],'source':item['source'],'source_sha256':provenance['sha256'],
            'coordinate_space':item.get('coordinate_space','source world XYZ'),
            'sample_box':item['box'],'roof_query_box':box,'roof_material_envelope':roof,'column_rows':rows,'interpretation':item['interpretation']})
    result={'schema':'architecture-measured-components/1','method':'Explicit boxes, material filters, continuous selected segments and optional shape surfaces; no automatic semantic claims.','samples':results}
    Path(out).parent.mkdir(parents=True,exist_ok=True);Path(out).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(out)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--samples-dir',required=True);p.add_argument('--annotations',required=True);p.add_argument('--out',required=True);a=p.parse_args();run(a.samples_dir,a.annotations,a.out)
