"""Control and capture only the explicitly authorized ASTRA_VFX_TEST client."""
from pathlib import Path
import argparse, json, time, urllib.request, base64, os

GAME=Path(os.environ['MINECRAFT_GAME_DIR']).expanduser().resolve() if os.environ.get('MINECRAFT_GAME_DIR') else None
def api(route, body=None):
    data=None if body is None else json.dumps(body).encode()
    request=urllib.request.Request('http://127.0.0.1:9876/api/'+route,data=data,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=12) as response:return json.load(response)

def verify():
    world=api('cmd',{'cmd':'get_world_info'})
    if world.get('world_name')!='ASTRA_VFX_TEST':raise RuntimeError('Wrong world: '+str(world))
    return world

def command(action, **fields):
    control=GAME/'black-hole-lab';result=control/'command-result.json'
    stamp=result.stat().st_mtime_ns if result.exists() else 0
    request=control/'command.json';temp=control/'command.tmp'
    if request.exists():raise RuntimeError('Previous command still pending')
    temp.write_text(json.dumps(dict(action=action,**fields)),encoding='utf-8');temp.replace(request)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        if result.exists() and result.stat().st_mtime_ns!=stamp and not request.exists():
            r=json.loads(result.read_text(encoding='utf-8'))
            if not r.get('ok') or r.get('action')!=action:raise RuntimeError(r)
            if r.get('failure'):raise RuntimeError(r)
            return r
        time.sleep(.1)
    raise TimeoutError(action)

def probe(name, out):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    source=GAME/'black-hole-lab/world-probes';p=source/(name+'.json')
    stamp=p.stat().st_mtime_ns if p.exists() else 0
    command('probe',name=name)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        if p.exists() and p.stat().st_mtime_ns!=stamp:
            r=json.loads(p.read_text(encoding='utf-8'))
            for suffix in ['.json','-before.png','-field.png','-after.png']:
                (out/(name+suffix)).write_bytes((source/(name+suffix)).read_bytes())
            if r['glError'] or r['maxDepthDifference'] or r['foregroundChangedPixels'] or r['outsideChangedPixels']:raise AssertionError(r)
            return r
        time.sleep(.1)
    raise TimeoutError('probe '+name)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action');parser.add_argument('--fields',default='{}');parser.add_argument('--out')
    args=parser.parse_args()
    if GAME is None or not (GAME / 'mods').is_dir():parser.error('Set MINECRAFT_GAME_DIR to an explicitly selected game instance containing mods.')
    verify();fields=json.loads(args.fields)
    result=probe(fields['name'],args.out) if args.action=='probe' else command(args.action,**fields)
    print(json.dumps(result,ensure_ascii=False,indent=2))
