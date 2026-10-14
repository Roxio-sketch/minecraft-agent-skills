"""Exercise the installed Ink Realm in the explicitly selected local test world."""
import argparse, ast, base64, json, time, urllib.request, uuid
from pathlib import Path

PROFILE=Path('D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST')
ROOT=PROFILE/'inkrealm-lab'
OUT=Path(__file__).resolve().parents[1]/'evidence/runtime'
OUT.mkdir(parents=True,exist_ok=True)

def api(cmd,**params):
    body=json.dumps(dict(cmd=cmd,**params)).encode()
    with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:9876/api/cmd',body,{'Content-Type':'application/json'}),timeout=8) as r:
        obj=json.load(r)
    if isinstance(obj,dict) and obj.get('error'): raise RuntimeError(obj)
    return obj

def status():
    return json.loads((ROOT/'status.json').read_text(encoding='utf-8-sig'))

def wait_for(check,seconds=30):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            result=check()
            if result:return result
        except (FileNotFoundError,json.JSONDecodeError):pass
        time.sleep(.2)
    raise TimeoutError('Runtime condition timed out')

def request(action,**params):
    nonce=uuid.uuid4().hex
    temp=ROOT/'request.tmp'
    temp.write_text(json.dumps(dict(action=action,nonce=nonce,**params)),encoding='utf8')
    temp.replace(ROOT/'request.json')
    wait_for(lambda:status().get('nonce')==nonce)
    return status()

def capture(name):
    dest=ROOT/'captures'/f'{name}.json'
    if dest.exists():raise FileExistsError(dest)
    request('capture',name=name)
    wait_for(dest.exists)
    for p in (ROOT/'captures').glob(name+'*'):
        (OUT/p.name).write_bytes(p.read_bytes())
    result=json.loads(dest.read_text(encoding='utf8'))
    print(json.dumps({k:result[k] for k in ['case','changedPixels','outsideChangedPixels','foregroundPixels','foregroundChangedPixels','sceneDepthBins','glError','stateMismatches','eye']},ensure_ascii=False),flush=True)
    return result

def command(text):
    api('execute_command',command=text)
    time.sleep(.8)

def screenshot(name):
    time.sleep(2.2)
    with urllib.request.urlopen('http://127.0.0.1:9876/api/screenshot',timeout=8) as r: s=json.load(r)
    (OUT/(name+'.png')).write_bytes(base64.b64decode(s['original'].split(',',1)[1]))

def main():
    assert api('get_world_info')['world_name']=='ASTRA_VFX_TEST'
    assert status()['forge']=='47.4.10' and not status()['blockedRenderer']
    api('enter_control_mode')
    player=api('get_player_info');state=status();gui=api('gui_state')
    pos=ast.literal_eval(state['player']);yaw,pitch=map(float,player['rotation'].split())
    backup=dict(player=player,exactPosition=pos,gui=gui)
    (OUT/'restore.json').write_text(json.dumps(backup,indent=2,ensure_ascii=False),encoding='utf8')
    prefix=time.strftime('%Y%m%d-%H%M%S')
    results=[]
    try:
        request('time',value=0);results.append(capture(prefix+'-foreground'))
        command('/gamemode spectator @s')
        assert api('get_player_info')['gamemode']=='spectator'
        x,y,z=pos;y+=4
        command(f'/tp @s {x} {y} {z} 0 0')
        request('open');request('time',value=0);time.sleep(1)
        results.append(capture(prefix+'-front'))
        screenshot(prefix+'-world-overview')
        command(f'/tp @s {x+1} {y} {z} 0 0');time.sleep(.5)
        results.append(capture(prefix+'-left'))
        command(f'/tp @s {x-1} {y} {z} 0 0');time.sleep(.5)
        results.append(capture(prefix+'-right'))
        command(f'/tp @s {x} {y} {z} 0 0');time.sleep(.5)
        request('time',value=4);results.append(capture(prefix+'-time4'))
        request('time',value=0);request('resolution',value=320);results.append(capture(prefix+'-low'))
        request('resolution',value=768);results.append(capture(prefix+'-high'))
        generation=status()['shaderGeneration'];request('reload')
        wait_for(lambda:status()['shaderGeneration']>generation,60);time.sleep(3)
        results.append(capture(prefix+'-reloaded'))
        request('inject_error');wait_for(lambda:status()['failed']);screenshot(prefix+'-fault-restored')
        failure=status();assert failure['stateErrors']==0
        request('resume');time.sleep(.5);results.append(capture(prefix+'-recovered'))
        request('off');screenshot(prefix+'-off')
        manifest=dict(prefix=prefix,cases=[r['case'] for r in results],fault=failure,world='ASTRA_VFX_TEST',scope='Lightweight procedural secondary scene in real Forge; no second ClientLevel')
        (OUT/'session.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding='utf8')
    finally:
        command(f'/tp @s {pos[0]} {pos[1]} {pos[2]} {yaw} {pitch}')
        command('/gamemode '+player['gamemode']+' @s')
        request('resolution',value=768);request('open');request('time',value=-1)
        (OUT/'restored.json').write_text(json.dumps(dict(player=api('get_player_info'),state=status()),indent=2,ensure_ascii=False),encoding='utf8')
    print('RUNTIME_SESSION_COMPLETE',flush=True)

if __name__=='__main__':main()
