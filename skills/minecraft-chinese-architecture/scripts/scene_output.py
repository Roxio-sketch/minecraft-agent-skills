"""Shared serialization and evidence output for composed original scenes."""
import hashlib
import json
from pathlib import Path
import numpy as np
from generate_hall import write_schematic
from import_structure import load_structure


def export_scene(model,design,output,name,kind,render=True):
    if not isinstance(name,str) or not name or Path(name).name!=name or any(v in name for v in '/\\:'):raise ValueError('Simple output filename stem required')
    if not all(design['checks'].values()):raise AssertionError(design['checks'])
    out=Path(output);out.mkdir(parents=True,exist_ok=True)
    schem,npz=out/f'{name}.schem',out/f'{name}.npz'
    author = 'Derived from supplied model; see accompanying report' if design.get('source_structure_reuse') else 'Original procedural example'
    write_schematic(model,schem,name,generator='minecraft-chinese-architecture/'+kind,author=author)
    a,p,_,_=load_structure(schem)
    design['checks']['schematic_state_roundtrip']=np.array_equal(np.array(p)[a],np.array(model.palette)[model.blocks])
    if not all(design['checks'].values()):raise AssertionError(design['checks'])
    model.save(npz)
    result={'schema':kind+'-report/1','name':name,'dimensions_xyz':[model.blocks.shape[2],model.blocks.shape[0],model.blocks.shape[1]],
            'nonair_voxels':int(np.count_nonzero(model.blocks)),'design':design,
            'schematic_sha256':hashlib.sha256(schem.read_bytes()).hexdigest(),
            'coordinate_convention':{'npz_origin_xyz':model.origin.tolist(),'schematic_offset_xyz':[0,0,0],'mapping':'schematic XYZ = design XYZ - NPZ origin'},
            'limits':['Offline geometry and dry voxel paths only; no live game or physics validation.',
                      'Approximate colors; unsupported shapes use cubes. Liquids are not simulated.',
                      ('Contains supplied source geometry; see source receipts and preserve attribution and redistribution restrictions.'
                       if design.get('source_structure_reuse') else 'Original design, not a historical reconstruction. Source geometry not copied.'),
                      'Export DataVersion 4189; validate states against the intended game version.']}
    (out/f'{name}.report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
    if render:
        from render_shapes import render as preview
        for view in ('isometric','top','front'):preview(npz,out/f'{name}_{view}.png',view=view)
    return result
