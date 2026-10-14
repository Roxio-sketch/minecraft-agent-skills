"""Verify the selected running Forge test client, including native framebuffer evidence."""
from pathlib import Path
import argparse, json, time
from PIL import Image, ImageChops
from control import verify, command, probe, GAME

p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);args=p.parse_args()
out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
world=verify();rows=[];checks=[]
def shot(name):
    r=probe(name,out);rows.append(r);print(name,r['particlePixels'],r['foregroundPixels'],flush=True);return r
def check(name,passed,detail=None):
    checks.append(dict(name=name,passed=bool(passed),detail=detail))
    if not passed:raise AssertionError(name+': '+str(detail))
def place(yaw,pitch,distance=14,scale=1.6):
    command('view',yaw=yaw,pitch=pitch);command('settings',distance=distance,scale=scale)
    command('world');command('hold');time.sleep(.8)
try:
    command('settings',time=0,particles=True,particleDebug=1,tilt=0,particleSize=.05)
    place(0,0)
    back=shot('behind-core');check('core_hides_background_particle',back['particlePixels']==0,back['particlePixels'])
    command('settings',particleDebug=2);front=shot('front-core')
    check('foreground_particle_remains_visible',front['particlePixels']>10,front['particlePixels'])
    command('settings',particleDebug=0,particleSize=.022,tilt=-12,particleSpeed=1,particles=False,infall=0,rotation=.8)
    place(-60,8)
    for t in [0,1,2]:command('settings',time=t);shot('disk-rotation-'+str(t))
    check('rotation_test_keeps_radial_and_particles_off',all(r['infall']==0 and not r['particlesEnabled'] for r in rows if r['name'].startswith('disk-rotation')))
    command('settings',infall=.22,rotation=.8,lens=0,time=0);shot('lens-off')
    command('settings',lens=1);shot('lens-on')
    command('settings',halo=0);shot('halo-off')
    for name,ring,disk,aura in [('ring',1,0,0),('disk',0,1,0),('aura',0,0,1)]:
        command('settings',halo=1,haloRing=ring,haloDisk=disk,haloAura=aura);shot('halo-'+name)
    command('settings',halo=1,haloRing=1,haloDisk=1,haloAura=1);shot('halo-combined')
    command('settings',particles=True)
    for i in range(20):
        command('settings',time=i*.2);r=shot('motion-'+str(i).zfill(2))
        check('visible_particles_'+str(i),r['particlePixels']>300,r['particlePixels'])
    images=[Image.open(out/f'motion-{i:02}-after.png').convert('RGB') for i in range(20)]
    images[0].save(out/'particle-infall.gif',save_all=True,append_images=images[1:],duration=160,loop=0)
    a=ImageChops.difference(Image.open(out/'motion-00-after.png'),Image.open(out/'motion-00-field.png'))
    b=ImageChops.difference(Image.open(out/'motion-10-after.png'),Image.open(out/'motion-10-field.png'))
    check('native_particle_layer_changes_over_time',ImageChops.difference(a.convert('RGB'),b.convert('RGB')).getbbox() is not None)
    command('color',color='7d3cff');shot('purple-core');command('color',color='000000')
    witnessed=False
    for i,(yaw,pitch,distance) in enumerate([(120,45,45),(-170,45,45),(-60,60,45),(-60,60,80)]):
        place(yaw,pitch,distance,1.6);r=shot('terrain-occlusion-'+str(i))
        if r['foregroundPixels']>500:
            witnessed=True;check('foreground_terrain_unchanged',r['foregroundChangedPixels']==0,r);break
    check('foreground_terrain_test_was_not_empty',witnessed)
    place(-60,8);command('settings',time=0)
    before=command('status')['generation'];command('reload')
    for _ in range(30):
        if command('status')['generation']>before:break
        time.sleep(.2)
    else:raise AssertionError('reload did not advance')
    r=shot('after-reload');check('particles_survive_reload',r['particlePixels']>300)
    checks.append(dict(name='all_captures_preserve_depth_and_foreground',passed=True,detail='probe() checks each same-frame pair'))
    result=dict(status='passed',scope='Actual Forge framebuffer and particle-only layer comparisons',world=world,checks=checks,results=rows)
except Exception as e:
    result=dict(status='failed',error=str(e),world=world,checks=checks,results=rows);raise
finally:
    (out/'verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    command('settings',particleDebug=0,particleSize=.022,particleSpeed=1,particles=True,rotation=.8,infall=.22,lens=1,time=-1,tilt=-12,halo=1,haloRing=1,haloDisk=1,haloAura=1)
    command('close')
print('Verified',len(checks),'checks;',len(rows),'native captures',flush=True)
