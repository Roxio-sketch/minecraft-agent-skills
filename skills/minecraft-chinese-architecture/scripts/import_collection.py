"""Read a local directory of structure files and keep successes and failures distinct.

Exact geometry fingerprints ignore paste offsets and unused palette entries;
they do not imply equivalent entities, unspecified positions, or licensing.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from import_structure import load_structure


def file_sha256(path):
    digest=hashlib.sha256()
    with open(path,'rb') as stream:
        while block:=stream.read(1024*1024):digest.update(block)
    return digest.hexdigest()


def geometry_fingerprint(blocks, palette):
    used=np.unique(blocks)
    states=sorted(str(palette[int(i)]) for i in used)
    ranks={state:i for i,state in enumerate(states)}
    mapping=np.zeros(len(palette),np.uint32)
    for i in used:mapping[i]=ranks[str(palette[int(i)])]
    digest=hashlib.sha256()
    digest.update(json.dumps({'shape_yzx':list(blocks.shape),'states':states},separators=(',',':')).encode('utf8'))
    digest.update(mapping[blocks].astype('<u4').tobytes())
    return digest.hexdigest()


def run(source, output, recursive=False, max_voxels=8_000_000):
    source,output=Path(source).resolve(),Path(output).resolve()
    if not source.is_dir():raise ValueError('Source must be a local structure directory')
    candidates=sorted(p for p in (source.rglob('*') if recursive else source.iterdir())
                      if p.is_file() and p.suffix.lower() in ('.nbt','.schem','.litematic'))
    if not candidates:raise ValueError('No supported structure files found')
    output.mkdir(parents=True,exist_ok=True)
    entries=[];groups={}
    for path in candidates:
        relative=path.relative_to(source)
        entry={'relative_source':relative.as_posix(),'source_sha256':file_sha256(path)}
        try:
            a,p,o,report=load_structure(path,max_voxels=max_voxels)
            # Keep the source extension to distinguish house.nbt and house.schem.
            dest=output/relative.parent/(relative.name+'.npz');dest.parent.mkdir(parents=True,exist_ok=True)
            np.savez_compressed(dest,blocks=a,palette=p,origin=o)
            report_path=dest.with_suffix('.import.json')
            report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
            fingerprint=geometry_fingerprint(a,p)
            groups.setdefault(fingerprint,[]).append(relative.as_posix())
            entry.update(status='imported',model=str(dest),import_report=str(report_path),size_xyz=report['size_xyz'],
                         data_version=report['data_version'],nonair_blocks=report['nonair_blocks'],
                         nonair_namespace_counts=report['nonair_namespace_counts'],geometry_sha256=fingerprint)
        except (ValueError,KeyError,TypeError,IndexError,OSError,EOFError) as error:
            entry.update(status='rejected',error_type=type(error).__name__,reason=str(error))
        entries.append(entry)
    rejected=sum(v['status']=='rejected' for v in entries)
    result={'schema':'architecture-collection-import/1','source_directory':str(source),'files_found':len(entries),
            'files_imported':len(entries)-rejected,'files_rejected':rejected,'distinct_geometry_count':len(groups),
            'exact_geometry_duplicates':[v for v in groups.values() if len(v)>1],'files':entries,
            'limits':['File counts are not independent architectural styles or measured buildings.',
                      'Geometry deduplication ignores offsets, entities, block-entity payloads and unspecified-position semantics.',
                      'Attribution and permission must be registered separately; importer does not infer authorization from embedded metadata.',
                      'Multi-region or multi-palette files needing a selection are rejected here; import them explicitly with import_structure.py.']}
    (output/'collection.report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
    return result


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path)
    p.add_argument('--out-dir',type=Path,required=True);p.add_argument('--recursive',action='store_true')
    p.add_argument('--max-voxels',type=int,default=8_000_000);args=p.parse_args()
    r=run(args.source,args.out_dir,args.recursive,args.max_voxels)
    print(json.dumps({k:r[k] for k in ('files_found','files_imported','files_rejected','distinct_geometry_count','exact_geometry_duplicates')}))
    if r['files_rejected']:raise SystemExit(1)
