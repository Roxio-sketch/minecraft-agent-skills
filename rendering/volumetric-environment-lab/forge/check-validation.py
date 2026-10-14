#!/usr/bin/env python3
"""Independently check actual Forge readbacks and PNG pixels; does not launch Minecraft."""
import json, math, sys
from pathlib import Path
import struct, zlib

def png_rgb(path):
    """Decode the noninterlaced 8-bit RGB/RGBA PNGs produced by NativeImage."""
    data=Path(path).read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n', 'PNG signature'
    pos=8;packed=bytearray();width=height=channels=None
    while pos<len(data):
        count=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];chunk=data[pos+8:pos+8+count];pos+=12+count
        if kind==b'IHDR':
            width,height,bits,mode,compression,filtering,interlace=struct.unpack('>IIBBBBB',chunk)
            assert bits==8 and mode in (2,6) and compression==0 and filtering==0 and interlace==0, 'Unsupported PNG format'
            channels=3 if mode==2 else 4
        elif kind==b'IDAT':packed.extend(chunk)
        elif kind==b'IEND':break
    raw=zlib.decompress(packed);stride=width*channels;previous=bytearray(stride);rgb=bytearray();pos=0
    for _ in range(height):
        filter_kind=raw[pos];pos+=1;row=bytearray(raw[pos:pos+stride]);pos+=stride
        for x in range(stride):
            left=row[x-channels] if x>=channels else 0;up=previous[x];corner=previous[x-channels] if x>=channels else 0
            if filter_kind==0:predict=0
            elif filter_kind==1:predict=left
            elif filter_kind==2:predict=up
            elif filter_kind==3:predict=(left+up)//2
            elif filter_kind==4:
                estimate=left+up-corner;pa=abs(estimate-left);pb=abs(estimate-up);pc=abs(estimate-corner)
                predict=left if pa<=pb and pa<=pc else up if pb<=pc else corner
            else:raise AssertionError('Invalid PNG filter')
            row[x]=(row[x]+predict)&255
        if channels==3:rgb.extend(row)
        else:
            for x in range(0,stride,4):rgb.extend(row[x:x+3])
        previous=row
    return (width,height),rgb

def check(directory):
    d=Path(directory);root=json.loads((d/'results.json').read_text(encoding='utf-8'))
    project=Path(__file__).resolve().parent.parent
    schema=json.loads((project/'workbench/schema.json').read_text(encoding='utf-8'))
    contract=json.loads((project/'workbench/contract.json').read_text(encoding='utf-8'))
    schema_v1=json.loads((project/'forge/src/main/resources/assets/worldvolume/volume/schema-v1.json').read_text(encoding='utf-8'))
    names=['off','volume','emission','flash-medium','flash-surface','behind-wall','sphere-visible','annulus','reload','resize','end-raw','end-cavity','end-torus','end-twist-off','end-time8','end-void','end-near-clear','end-void-low','purple-time8','purple-off','purple-glow']
    assert root['complete'] is True, 'Incomplete runtime'
    assert [r['name'] for r in root['results']]==names, 'Missing, duplicate or reordered scenarios'
    records={r['name']:r for r in root['results']};assert list(records)==names, 'Wrong scenarios'
    for name,r in records.items():
        assert r['glError']==0 and r['stateRestored'] is True and r['maxDepthDifference']==0, (name,'GL/depth/state')
        assert r['sourceFbo']!=r['outputFbo'] and r['sceneTexture']!=r['outputColorTexture'],(name,'attachment feedback')
        assert r['sceneTexture']!=r['depthTexture'] and min(r['sceneTexture'],r['depthTexture'])>0,(name,'texture allocation')
        binding=r['drawBindings'];assert binding['program']==r['program'] and binding['drawFbo']==r['outputFbo'],(name,'actual draw')
        assert binding['SceneSamplerBoundTexture']==r['sceneTexture'] and binding['DepthSamplerBoundTexture']==r['depthTexture'],(name,'actual samplers')
        assert binding['SceneSamplerUnit']!=binding['DepthSamplerUnit'],(name,'sampler alias')
        assert r['Time']==(8.0 if name in ('end-time8','purple-time8') else 0.5) and r['draws']>=35 and r['gpuSamples']>0,(name,'time/samples')
        assert len(r['uploadedUniforms'])==19 and all(u['location']>=0 and all(math.isfinite(v) for v in u['value']) for u in r['uploadedUniforms'].values()),(name,'uniforms')
        source_size,source=png_rgb(d/(name+'-scene.png'));post_size,post=png_rgb(d/(name+'-post.png'))
        assert source_size==post_size==(r['width'],r['height']),(name,'PNG resolution')
        changed=sum(source[i:i+3]!=post[i:i+3] for i in range(0,len(source),3))
        assert changed==r['changedPixels'],(name,'independent PNG pixel count',changed,r['changedPixels'])
        absolute=sum(abs(a-b) for a,b in zip(source,post));assert absolute==r['sumAbsoluteRGBDelta'],(name,'absolute RGB sum')
        probe=r['centerRayProbe'];assert len(probe)==4 and all(math.isfinite(v) for v in probe) and 0<=probe[0]<=1 and probe[1]>=0 and probe[2]>=0 and probe[3]==1,(name,'float GPU probe')
        p=r['recipe']['parameters'];u=r['uploadedUniforms'];assert r['recipe']['schema']=='world-volume/2' and len(p)==62,(name,'schema')
        assert set(p)==set(schema['fields']) and set(u)==set(contract['uniforms']),(name,'exact fields/uniform groups')
        rebased={'centerX':r['camera'][0],'centerY':r['camera'][1],'centerZ':r['camera'][2],'bottom':r['camera'][1],'voidFloor':r['camera'][1]}
        for group,fields in contract['uniforms'].items():
            actual=u[group]['value'];assert len(actual)==len(fields),(name,group,'uniform length')
            for key,value in zip(fields,actual):
                expected=p[key]-rebased.get(key,0)
                assert abs(value-expected)<=1e-4+abs(expected)*1e-6,(name,group,key,'uniform mapping',value,expected)
        assert abs(u['EndDepth']['value'][1]-(p['voidFloor']-r['camera'][1]))<1e-4,(name,'voidFloor double rebase')
        assert r['geometryPixels']+r['skyPixels']==r['width']*r['height'],(name,'depth masks')
        assert r['geometryChangedPixels']+r['skyChangedPixels']==changed,(name,'changed masks')
    assert records['off']['changedPixels']==0,'Off is not identity'
    for name in ['volume','emission','flash-medium']:
        assert records[name]['geometryChangedPixels']>0 and records[name]['skyChangedPixels']>0,(name,'effect absent')
    assert records['emission']['recipe']['parameters']['extinction']==0 and records['emission']['recipe']['parameters']['flashEnabled']==0,'Emission not isolated'
    surface=records['flash-surface'];assert surface['recipe']['parameters']['density']==0 and surface['geometryChangedPixels']>0 and surface['skyChangedPixels']==0,'Surface light not isolated'
    assert records['behind-wall']['changedPixels']==0 and records['sphere-visible']['changedPixels']>0,'Depth clipping/control failed'
    assert records['annulus']['geometryChangedPixels']==0 and records['annulus']['skyChangedPixels']>0,'Annular cavity failed'
    assert records['reload']['generationAfter']>records['reload']['generationBefore'] and records['reload']['changedPixelsVsVolume']==0,'Reload replay failed'
    assert records['resize']['width']==800 and records['resize']['height']==450,'Resize failed'
    rejection_names={'unknown','missing','fractional-integer','out-of-range','zero-sun','annular-hole','duration','model-range','new-field-missing','unknown-envelope','boolean-number','long-name','long-unicode-name','duplicate-envelope','duplicate-parameter','nonfinite-json','comments-json','trailing-json','legacy-requires-explicit-upgrade','legacy-missing','legacy-unknown','legacy-fractional-integer','legacy-duplicate','legacy-duration'}
    strict=json.loads((d/'strict-recipe-checks.json').read_text(encoding='utf-8'));assert set(strict)==rejection_names and all(v is True for v in strict.values()),'Strict recipe rejection failed'
    migration=json.loads((d/'legacy-migration.json').read_text(encoding='utf-8'));assert migration['preservedOldFields']==50 and migration['addedDefaultFields']==12 and migration['preservedAllOldValues'] and migration['newDefaultsMatchSchema'] and migration['densityModel']==0,'Explicit legacy migration failed'
    source=migration['source'];upgraded=migration['upgraded'];old=source['parameters'];new=upgraded['parameters']
    assert source['schema']=='world-volume/1' and upgraded['schema']=='world-volume/2' and source.get('name')==upgraded.get('name'),'Migration envelope differs'
    assert set(old)==set(schema_v1['fields']) and set(new)==set(schema['fields']),'Migration field sets differ'
    assert all(new[key]==value for key,value in old.items()),'Migration changed legacy values'
    added=set(new)-set(old);assert len(added)==12 and all(new[key]==schema['fields'][key]['default'] for key in added) and new['densityModel']==0,'Migration defaults differ'
    assert records['end-raw']['changedPixels']>0 and records['end-cavity']['geometryChangedPixels']==0,'End density/cavity isolation failed'
    assert records['end-void']['geometryChangedPixels']>0 and records['end-void']['centerRayProbe'][0]<records['end-cavity']['centerRayProbe'][0],'Void did not refill the carved center'
    assert records['end-near-clear']['centerRayProbe'][0]>records['end-void']['centerRayProbe'][0] and records['end-near-clear']['centerRayProbe'][1:3]==records['end-void']['centerRayProbe'][1:3],'Near clear did not change integration only'
    assert records['end-void-low']['centerRayProbe'][0]>records['end-void']['centerRayProbe'][0],'Void height control failed'
    for a,b in [('end-cavity','end-raw'),('end-torus','end-raw'),('end-twist-off','end-raw'),('end-time8','end-raw'),('purple-glow','purple-off'),('purple-time8','purple-off')]:
        asize,ascene=png_rgb(d/(a+'-scene.png'));bsize,bscene=png_rgb(d/(b+'-scene.png'));assert asize==bsize and ascene==bscene,(a,b,'source scene drift')
        asize,apng=png_rgb(d/(a+'-post.png'));bsize,bpng=png_rgb(d/(b+'-post.png'));assert asize==bsize and apng!=bpng,(a,b,'feature isolation unchanged')
    assert abs(records['purple-glow']['centerRayProbe'][0]-records['purple-off']['centerRayProbe'][0])<1e-6 and abs(records['purple-time8']['centerRayProbe'][0]-records['purple-off']['centerRayProbe'][0])<1e-6,'Purple source changed opacity'
    assert records['purple-glow']['centerRayProbe'][1:3]==records['purple-off']['centerRayProbe'][1:3]==records['purple-time8']['centerRayProbe'][1:3],'Purple source changed raw density'
    assert records['purple-off']['centerRayProbe'][0]>0.6,'Purple fog control not thin'
    return {'passed':True,'scenarios':len(records),'strictRejections':len(strict),'legacyMigrationPassed':True,'floatProbePassed':True,'renderer':root['renderer'],'minecraft':root['minecraft'],'forge':root['forge'],'java':root['java'],'gpuMedianMs':{name:r['gpuMedianMs'] for name,r in records.items()}}

if __name__=='__main__':
    try:
        result=check(sys.argv[1] if len(sys.argv)>1 else '../evidence/forge/runtime')
        print(json.dumps(result,ensure_ascii=False,indent=2))
    except Exception as e:
        print(f'FAILED: {e}',file=sys.stderr);sys.exit(1)
