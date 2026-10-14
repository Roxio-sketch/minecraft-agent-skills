"""Capture an already-running, explicitly selected ASTRA test client's demo."""
from pathlib import Path
import argparse, json, time, urllib.request, base64

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--game-dir',required=True);p.add_argument('--output',required=True)
args=p.parse_args();game=Path(args.game_dir);out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
control=game/'black-hole-lab';results=[]

def api(route,body=None):
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request('http://127.0.0.1:9876/api/'+route,data=data,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=8) as r:return json.load(r)

world=api('cmd',{'cmd':'get_world_info'})
if world.get('world_name')!='ASTRA_VFX_TEST':raise RuntimeError('Wrong world; refusing demo actions')

def command(action,**fields):
    result=control/'command-result.json'
    stamp=result.stat().st_mtime_ns if result.exists() else 0
    temp=control/'command.tmp';temp.write_text(json.dumps(dict(action=action,**fields)),encoding='utf-8');temp.replace(control/'command.json')
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        if result.exists() and result.stat().st_mtime_ns!=stamp and not (control/'command.json').exists():
            data=json.loads(result.read_text());assert data['ok'] and data['action']==action,data
            return data
        time.sleep(.1)
    raise TimeoutError('Client did not acknowledge '+action)

def capture(name):
    # The Helper screenshot endpoint caches for two seconds.
    time.sleep(2.4)
    data=api('screenshot');png=data['original'];assert png.startswith('data:image/png;base64,')
    (out/(name+'.png')).write_bytes(base64.b64decode(png.split(',',1)[1]))
    status=command('status');results.append(dict(case=name,**status));print(name,json.dumps(status),flush=True)
    assert not status['failure'] and status['glError']==0,status

command('close');command('off');capture('world-baseline')
command('world',color='000000');capture('world-black')
command('color',color='7d3cff');capture('world-purple')
before=command('status')['generation'];command('reload')
deadline=time.monotonic()+45
while time.monotonic()<deadline:
    status=command('status')
    if status['generation']>before:break
    time.sleep(.5)
else:raise TimeoutError('Resource reload did not finish')
capture('world-purple-reloaded')
command('off');capture('world-disabled')
command('color',color='7d3cff');command('showcase');capture('showcase-purple')
report=dict(status='passed',world=world,results=results,scope='Actual Minecraft framebuffer; artistic kernel, approximate center-plane world occlusion, no scene-background lensing')
(out/'world-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
