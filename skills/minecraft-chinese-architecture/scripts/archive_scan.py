"""Read-only ZIP/Anvil architecture inspection. Coordinates use inclusive boxes.

inspect SOURCE; scan SOURCE --out DIR; extract SOURCE --box X0 Y0 Z0 X1 Y1 Z1 --out FILE
Reads modern root sections/block_states and palette-based Level/Sections Anvil.
Numeric Blocks/Data sections (1.12 and older) are rejected, not treated as air.
"""
import argparse
import collections
import contextlib
import hashlib
import io
import json
import math
from pathlib import Path
import re
import struct
import time
import zipfile
import zlib
import gzip
import numpy as np
from nbt_io import loads, state_string, decode_states, StreamingMetadataReader

AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
SENTINEL = -32768
# This is a material heuristic, not semantic proof that a voxel is a building.
BUILT_TOKENS = ('_planks', '_stairs', '_slab', '_fence', '_wall', '_bricks',
                'brick_', '_concrete', '_terracotta', '_glass', '_wool', '_carpet',
                'polished_', 'chiseled_', 'smooth_', 'cut_', 'stripped_',
                '_door', '_trapdoor', '_button', '_pressure_plate', '_lantern',
                '_chain', '_bars', '_shulker_box', '_bed', '_banner', '_sign')
BUILT_EXACT = {'bricks','glass','terracotta','chain','iron_bars','lantern','soul_lantern',
               'quartz_block','quartz_pillar','gold_block','iron_block','diamond_block',
               'emerald_block','lapis_block','redstone_block','sea_lantern','end_rod',
               'scaffolding','beacon','enchanting_table','bookshelf','chiseled_bookshelf',
               'crafting_table','barrel','chest','trapped_chest','hopper','anvil',
               'respawn_anchor','lodestone','lightning_rod'}
SKIP_NAMES = {'biomes','Heightmaps','structures','block_ticks','fluid_ticks','PostProcessing',
              'BlockLight','SkyLight','block_entities','entities','CarvingMasks',
              'Biomes','Structures','TileEntities','Entities','TileTicks','LiquidTicks'}


def is_built(state):
    base = state.split('[',1)[0].split(':',1)[-1]
    return base in BUILT_EXACT or any(t in base for t in BUILT_TOKENS)


def display_name(info):
    s = info.filename
    if not info.flag_bits & 2048:
        try: s = s.encode('cp437').decode('gbk')
        except (UnicodeEncodeError, UnicodeDecodeError): pass
    return s


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        while block := f.read(4*1024*1024): h.update(block)
    return h.hexdigest()


@contextlib.contextmanager
def world_zip(source):
    with zipfile.ZipFile(source) as outer:
        if any(i.filename.endswith('/level.dat') or i.filename == 'level.dat' for i in outer.infolist()):
            yield outer, None
            return
        nested = [i for i in outer.infolist() if i.filename.lower().endswith('.zip')]
        if len(nested) != 1:
            raise ValueError('Expected exactly one world ZIP inside source; choose a single world archive')
        # Nested source is small here (~45 MB); all voxel data still decoded per chunk.
        with zipfile.ZipFile(io.BytesIO(outer.read(nested[0]))) as inner:
            yield inner, display_name(nested[0])


def regions(z):
    # Excludes other dimensions: world prefix immediately precedes region/.
    lev = [i for i in z.infolist() if i.filename.endswith('/level.dat') or i.filename == 'level.dat']
    if len(lev) != 1: raise ValueError('Expected exactly one level.dat')
    prefix = lev[0].filename[:-len('level.dat')]
    rx = re.compile(re.escape(prefix) + r'region/r\.(-?\d+)\.(-?\d+)\.mca$')
    return sorted([(int(m[1]),int(m[2]),i) for i in z.infolist() if (m := rx.fullmatch(i.filename))]), lev[0]


def region_locations(z, info):
    if info.file_size == 0: return []
    if info.file_size < 8192: raise ValueError(f'Truncated region {info.filename}')
    with z.open(info) as f: header = f.read(4096)
    return [(j, int.from_bytes(header[j*4:j*4+3], 'big'), header[j*4+3])
            for j in range(1024) if header[j*4:j*4+3] != b'\0\0\0']


def chunk_payload(raw, sector, sectors, member):
    start = sector*4096
    if sector < 2 or start + 5 > len(raw): raise ValueError('Invalid region location')
    length = int.from_bytes(raw[start:start+4], 'big')
    if length < 1 or length + 4 > sectors*4096 or start+4+length > len(raw):
        raise ValueError('Invalid region chunk length')
    kind = raw[start+4]
    if kind & 128: raise ValueError(f'External .mcc storage not supported: {member}')
    blob = raw[start+5:start+4+length]
    if kind == 1: return gzip.decompress(blob)
    if kind == 2: return zlib.decompress(blob)
    if kind == 3: return blob
    raise ValueError(f'Unsupported region compression {kind}')


def normalize_chunk(root):
    """Normalize each chunk independently so partially upgraded worlds work.

    DataVersion 2529 (20w17a) changed palette packing to padded longs. Earlier
    palette-based Anvil chunks use continuous packing, including 1.14.4.
    Never choose by array length: four-bit sections have identical lengths.
    """
    version = root.get('DataVersion')
    if type(version) is not int or version < 0: raise ValueError('Chunk DataVersion required')
    legacy = 'Level' in root
    body = root['Level'] if legacy else root
    section_key = 'Sections' if legacy else 'sections'
    if not isinstance(body, dict) or any(k not in body for k in ('xPos','zPos',section_key)):
        raise ValueError('Unsupported chunk schema: requires coordinates and sections')
    normalized = []
    for section in body[section_key]:
        if 'Y' not in section: raise ValueError('Section missing Y')
        s = {'Y': section['Y']}
        if any(k in section for k in ('Blocks','Data','Add')):
            raise ValueError('Numeric Blocks/Data Anvil sections (1.12 or older) are unsupported')
        if legacy:
            if 'BlockStates' in section and 'Palette' not in section:
                raise ValueError('Legacy BlockStates without Palette')
            if 'Palette' in section:
                s['block_states'] = {'palette':section['Palette'], 'data':section.get('BlockStates',[]),
                                     '_packing':'padded' if version >= 2529 else 'continuous'}
        elif 'block_states' in section:
            s['block_states'] = {**section['block_states'], '_packing':'padded'}
        normalized.append(s)
    return {'xPos':body['xPos'], 'zPos':body['zPos'], 'DataVersion':version, 'sections':normalized,
            '_source_schema':'legacy_Level' if legacy else 'modern_root'}


def iter_chunks(z, region_list, box=None):
    for rx,rz,info in region_list:
        if box and (rx*512 > box[3] or rx*512+511 < box[0] or rz*512 > box[5] or rz*512+511 < box[2]): continue
        locs = region_locations(z,info)
        if not locs: continue
        raw = z.read(info)
        for slot,sector,sectors in locs:
            cx,cz = rx*32+slot%32, rz*32+slot//32
            if box and (cx*16 > box[3] or cx*16+15 < box[0] or cz*16 > box[5] or cz*16+15 < box[2]): continue
            d = normalize_chunk(loads(chunk_payload(raw,sector,sectors,info.filename), SKIP_NAMES))
            if int(d['xPos']) != cx or int(d['zPos']) != cz:
                raise ValueError(f'Chunk position differs from region slot {cx},{cz}')
            yield cx,cz,d


def metadata(z, level_info):
    d = loads(z.read(level_info))['Data']
    out = {k:d.get(k) for k in ('LevelName','DataVersion','Version','SpawnX','SpawnY','SpawnZ')}
    out['level_member'] = display_name(level_info)
    out['player_pos'] = d.get('Player',{}).get('Pos')
    height = [i for i in z.infolist() if i.filename.endswith('/dimension_type/overworld.json')]
    out['overworld_datapacks'] = [{'member':display_name(i),'value':json.loads(z.read(i))} for i in height]
    return out


def inspect(source):
    with world_zip(source) as (z,nested):
        rs,li = regions(z)
        n=0; active=[]
        for rx,rz,info in rs:
            locs = region_locations(z,info)
            if locs: active.append({'rx':rx,'rz':rz,'chunks':len(locs),'member':display_name(info)})
            n += len(locs)
        return {'source':str(Path(source).resolve()),'sha256':sha256(source),'nested_member':nested,
                'world':metadata(z,li),'chunk_count':n,'active_regions':active,
                'schematics':inspect_schematics(source)}


def inspect_schematics(source):
    """Read outer ZIP .schem metadata; no BlockData array or geometry is retained."""
    reports=[]
    with zipfile.ZipFile(source) as z:
        for info in z.infolist():
            if not info.filename.lower().endswith('.schem'):continue
            with z.open(info) as member:
                with gzip.GzipFile(fileobj=member) as stream:
                    root=StreamingMetadataReader(stream).root()
            d=root.get('Schematic',root)
            palette=d.get('Palette',d.get('Blocks',{}).get('Palette',{}))
            report={k:d.get(k) for k in ('Version','DataVersion','Width','Height','Length','Offset','Metadata')}
            report.update({'member':display_name(info),'gzip_bytes':info.file_size,
                           'palette_count':len(palette),'palette_sample':list(palette)[:16],
                           'block_data':d.get('BlockData',d.get('Blocks',{}).get('Data')),
                           'geometry_decoded':False,'metadata_trust':'Unverified embedded author/source claims'})
            reports.append(report)
    return reports


def section_data(section):
    bs = section.get('block_states')
    if bs is None: return None
    pal = [state_string(v) for v in bs['palette']]
    ids = decode_states(pal,bs.get('data',[]),packing=bs.get('_packing','padded')).reshape(16,16,16)
    return pal,ids


def save_json(path,data):
    Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')


def scan(source,out):
    out = Path(out); out.mkdir(parents=True,exist_ok=True)
    start = time.time(); report = inspect(source)
    with world_zip(source) as (z,nested):
        rs,li = regions(z); coords=[]
        for rx,rz,info in rs:
            for slot,_,_ in region_locations(z,info): coords.append((rx*32+slot%32,rz*32+slot//32))
        if not coords: raise ValueError('No chunks')
        minx=min(v[0] for v in coords)*16; minz=min(v[1] for v in coords)*16
        width=(max(v[0] for v in coords)*16+16-minx); length=(max(v[1] for v in coords)*16+16-minz)
        if width*length > 120_000_000: raise ValueError('Map exceeds 120M columns; split source or use bounded extraction')
        top_y=np.full((length,width),SENTINEL,np.int16); top_id=np.zeros((length,width),np.uint16)
        artificial_y=np.full_like(top_y,SENTINEL); artificial_id=np.zeros_like(top_id)
        built_count=np.zeros_like(top_id)
        palette=['minecraft:air']; lookup={palette[0]:0}; hist=collections.Counter(); versions=collections.Counter()
        miny=32767; maxy=-32768; built_bounds=[None]*6; schemas=collections.Counter()
        for num,(cx,cz,d) in enumerate(iter_chunks(z,rs),1):
            versions[int(d['DataVersion'])]+=1
            schemas[d['_source_schema']]+=1
            ox=cx*16-minx; oz=cz*16-minz
            ty=top_y[oz:oz+16,ox:ox+16]; ti=top_id[oz:oz+16,ox:ox+16]
            ay=artificial_y[oz:oz+16,ox:ox+16]; ai=artificial_id[oz:oz+16,ox:ox+16]; bc=built_count[oz:oz+16,ox:ox+16]
            for section in d.get('sections',[]):
                sd=section_data(section)
                if sd is None: continue
                pal,ids=sd
                if len(pal)==1 and pal[0] in AIR: continue
                y0=int(section['Y'])*16
                global_ids=[]
                for s in pal:
                    if s not in lookup:
                        if len(palette)>=65536: raise ValueError('More than 65535 states; increase map ID dtype')
                        lookup[s]=len(palette);palette.append(s)
                    global_ids.append(lookup[s])
                counts=np.bincount(ids.ravel(),minlength=len(pal))
                for ix,n in enumerate(counts):
                    if n and pal[ix] not in AIR: hist[global_ids[ix]]+=int(n)
                nonair=np.array([s not in AIR for s in pal],bool)[ids]
                material=np.array([is_built(s) for s in pal],bool)[ids]
                gid=np.array(global_ids,np.uint16)
                for mask,yarr,iarr in [(nonair,ty,ti),(material,ay,ai)]:
                    present=mask.any(axis=0)
                    if not present.any(): continue
                    local=15-np.argmax(mask[::-1],axis=0)
                    heights=local+y0
                    update=present & (heights>yarr)
                    zz,xx=np.where(update)
                    yarr[zz,xx]=heights[zz,xx]
                    iarr[zz,xx]=gid[ids[local[zz,xx],zz,xx]]
                bc += material.sum(axis=0,dtype=np.uint16)
                if nonair.any():
                    yy=np.where(nonair.any(axis=(1,2)))[0];miny=min(miny,y0+int(yy[0]));maxy=max(maxy,y0+int(yy[-1]))
                if material.any():
                    yy,zz,xx=np.where(material)
                    bb=[cx*16+int(xx.min()),y0+int(yy.min()),cz*16+int(zz.min()),cx*16+int(xx.max()),y0+int(yy.max()),cz*16+int(zz.max())]
                    if built_bounds[0] is None: built_bounds=bb
                    else: built_bounds=[min(built_bounds[k],bb[k]) if k<3 else max(built_bounds[k],bb[k]) for k in range(6)]
            if num%5000==0: print(json.dumps({'progress_chunks':num,'total':len(coords),'elapsed_s':round(time.time()-start,1)}),flush=True)
        np.savez_compressed(out/'topmap.npz',min_x=np.int32(minx),min_z=np.int32(minz),top_y=top_y,top_id=top_id,
                            artificial_y=artificial_y,artificial_id=artificial_id,built_count=built_count,palette=np.array(palette))
        report.update({'schema':'architecture-scan/1','array_order':'z,x','missing_height':SENTINEL,
                       'map_origin_xz':[minx,minz],'map_shape_zx':[length,width],'chunk_versions':dict(versions),
                       'chunk_schemas':dict(schemas),
                       'nonair_y_range':[miny,maxy],'built_candidate_bounds_inclusive':built_bounds,
                       'palette':palette,'histogram_nonair':{palette[k]:v for k,v in hist.most_common()},
                       'nonair_voxels':sum(hist.values()),'candidate_voxels':int(built_count.sum(dtype=np.uint64)),
                       'heuristic':{'name':'manufactured-material candidates, not semantic building segmentation',
                                    'included_name_substrings':list(BUILT_TOKENS),'included_exact_base_names':sorted(BUILT_EXACT),
                                    'excluded':'All other blocks including raw stone, dirt, grass, foliage and unstripped logs. These can still be construction materials.',
                                    'false_positives':'Decorative terrain and scattered manufactured blocks remain candidates.'},
                       'errors':[],'elapsed_seconds':round(time.time()-start,2)})
        save_json(out/'summary.json',report)
        print(json.dumps({'complete':str(out),'chunks':len(coords),'states':len(palette),'seconds':report['elapsed_seconds']},ensure_ascii=False),flush=True)


def extract(source,box,out,max_voxels=8_000_000):
    x0,y0,z0,x1,y1,z1=box
    dims=(y1-y0+1,z1-z0+1,x1-x0+1)
    if any(n<=0 for n in dims) or math.prod(dims)>max_voxels: raise ValueError(f'Invalid or oversized box {dims}; limit {max_voxels}')
    blocks=np.zeros(dims,np.uint16);palette=['minecraft:air'];lookup={palette[0]:0};covered=np.zeros((dims[1],dims[2]),bool);chunks=0
    with world_zip(source) as (z,nested):
        rs,li=regions(z)
        for cx,cz,d in iter_chunks(z,rs,box):
            chunks+=1
            xa=max(x0,cx*16);xb=min(x1,cx*16+15);za=max(z0,cz*16);zb=min(z1,cz*16+15)
            covered[za-z0:zb-z0+1,xa-x0:xb-x0+1]=True
            for s in d.get('sections',[]):
                sy=int(s['Y'])*16
                if sy>y1 or sy+15<y0:continue
                sd=section_data(s)
                if sd is None:continue
                pal,ids=sd;gids=[]
                for p in pal:
                    if p not in lookup:
                        if len(palette)>=65536:raise ValueError('Too many states')
                        lookup[p]=len(palette);palette.append(p)
                    gids.append(lookup[p])
                ya=max(y0,sy);yb=min(y1,sy+15)
                blocks[ya-y0:yb-y0+1,za-z0:zb-z0+1,xa-x0:xb-x0+1]=np.array(gids,np.uint16)[ids[ya-sy:yb-sy+1,za-cz*16:zb-cz*16+1,xa-cx*16:xb-cx*16+1]]
        if not covered.all():raise ValueError(f'{int((~covered).sum())} columns are in absent chunks; refusing to silently treat as air')
    out=Path(out);out.parent.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(out,blocks=blocks,palette=np.array(palette),origin=np.array([x0,y0,z0],np.int32))
    save_json(out.with_suffix('.json'),{'source':str(Path(source).resolve()),'sha256':sha256(source),'box_inclusive':box,'shape_yzx':list(dims),'chunks':chunks,'palette_count':len(palette),'array_order':'y,z,x','source_geometry':True,'redistribution_authorization':'not established'})
    print(json.dumps({'extracted':str(out),'shape':dims,'states':len(palette),'chunks':chunks},ensure_ascii=False))


def main():
    p=argparse.ArgumentParser(description=__doc__);sub=p.add_subparsers(dest='command',required=True)
    q=sub.add_parser('inspect');q.add_argument('source');q.add_argument('--out')
    q=sub.add_parser('scan');q.add_argument('source');q.add_argument('--out',required=True)
    q=sub.add_parser('extract');q.add_argument('source');q.add_argument('--box',nargs=6,type=int,required=True);q.add_argument('--out',required=True);q.add_argument('--max-voxels',type=int,default=8_000_000)
    a=p.parse_args()
    if a.command=='scan':scan(a.source,a.out)
    elif a.command=='extract':extract(a.source,a.box,a.out,a.max_voxels)
    else:
        d=inspect(a.source)
        if a.out:save_json(a.out,d)
        else:print(json.dumps(d,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
