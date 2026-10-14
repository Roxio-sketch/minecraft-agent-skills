"""Check a saved client audit. Does not launch Minecraft or substitute for visual inspection."""
import argparse,hashlib,json,pathlib
from PIL import Image,ImageChops

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=pathlib.Path,required=True);p.add_argument('--out',type=pathlib.Path,required=True)
    a=p.parse_args();r=json.loads((a.run/'results.json').read_text(encoding='utf8'));checks=[]
    def check(name,value,detail=None):checks.append({'name':name,'passed':bool(value),'detail':detail})
    check('completed',r['status']=='completed');check('platform',r['minecraft']=='1.20.1' and r['forge'].startswith('47.'))
    c={i['case']:i for i in r['captures']};expected={'gallery-t0','gallery-t1','world-effects','reloaded','expired'}
    check('scenario_set',set(c)==expected)
    for name in sorted(expected):
        if name not in c:continue
        image=Image.open(a.run/(name+'.png'));image.load()
        check('frame_'+name,image.width==c[name]['width'] and image.height==c[name]['height'],{'sha256':hashlib.sha256((a.run/(name+'.png')).read_bytes()).hexdigest(),'size':image.size})
        check('gl_'+name,c[name]['glError']==0,c[name]['glError'])
        check('no_overlay_'+name,c[name].get('overlayVisible') is False)
    check('reload_generation',c['reloaded']['generation']>c['gallery-t0']['generation'])
    check('world_geometry',c['world-effects']['historyPoints']>=2 and c['world-effects']['quadsTotal']>c['gallery-t1']['quadsTotal'])
    check('pause_restored',r['pauseRestored'])
    # Material/geometry ROI excludes changing labels. This is an existence/change check, not perceptual grading.
    before=Image.open(a.run/'gallery-t0.png').convert('RGB');after=Image.open(a.run/'gallery-t1.png').convert('RGB');off=Image.open(a.run/'expired.png').convert('RGB')
    w,h=before.size;roi=(int(w*.1),int(h*.28),int(w*.92),int(h*.78))
    def difference(x,y):
        red,green,blue=ImageChops.difference(x.crop(roi),y.crop(roi)).split()
        maximum=ImageChops.lighter(ImageChops.lighter(red,green),blue)
        histogram=maximum.histogram()
        return {'changed_pixels':maximum.width*maximum.height-histogram[0],
                'max_channel_difference':max(i for i,count in enumerate(histogram) if count)}
    pixels=lambda x,y:difference(x,y)['changed_pixels']
    animation=pixels(before,after);visible=pixels(before,off)
    check('animated_pixels',animation>50,animation);check('visible_effect_pixels',visible>100,visible)
    reloaded=Image.open(a.run/'reloaded.png').convert('RGB');reload_diff=difference(before,reloaded)
    # Same fixed time and geometry: tolerate one 8-bit rounding step, never a loading overlay.
    check('reload_material_matches',reload_diff['max_channel_difference']<=1,reload_diff)
    probes=[(int(w*.5),int(h*.12)),(int(w*.05),int(h*.4)),(int(w*.95),int(h*.65))]
    check('control_background_matches',all(before.getpixel(p)==off.getpixel(p) for p in probes))
    result={'status':'passed' if all(i['passed'] for i in checks) else 'failed','checks':checks,'limits':['Analytic demo sockets, no real weapon attachment','No shaderpack compatibility or performance certification','expired.png explicitly disables geometry; natural expiration separately tested in coreCheck']}
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(result,indent=2),encoding='utf8');print(json.dumps({'status':result['status'],'checks':len(checks),'animation_pixels':animation,'effect_pixels':visible}));assert result['status']=='passed'

if __name__=='__main__':main()
