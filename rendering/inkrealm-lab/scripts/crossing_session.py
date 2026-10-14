"""Verify actual entry/exit and full-view rendering in Ink Realm 0.2.0."""
import ast,json,time
from pathlib import Path
import runtime_session as r

r.OUT=Path(__file__).resolve().parents[1]/'evidence/runtime-v02'
r.OUT.mkdir(parents=True,exist_ok=True)
assert r.api('get_world_info')['world_name']=='ASTRA_VFX_TEST'
r.api('enter_control_mode')
gui=r.api('gui_state')
if gui.get('screen'):r.api('client_resume')
player=r.api('get_player_info');original=r.status()
pos=ast.literal_eval(original['player']);yaw,pitch=map(float,player['rotation'].split())
(r.OUT/'restore.json').write_text(json.dumps(dict(player=player,exactPosition=pos,gui=gui),indent=2,ensure_ascii=False),encoding='utf8')
prefix=time.strftime('%Y%m%d-%H%M%S')
results=[]
try:
    r.api('client_window',width='1280',height='720')
    r.command('/gamemode spectator @s')
    assert r.api('get_player_info')['gamemode']=='spectator'
    x,y,z=pos;y+=22
    r.command(f'/tp @s {x} {y} {z} 0 0')
    r.request('open');r.request('time',value=0);time.sleep(.8)
    results.append(r.capture(prefix+'-window'))
    r.screenshot(prefix+'-window-overview')
    s=r.status();c=s['portalCenter'];n=s['portalNormal']
    def go(side,heading=0,lateral=0):
        # For the chosen yaw=0 gate, its local right is (-1,0,0).
        r.command(f'/tp @s {c[0]+n[0]*side-lateral} {c[1]-1.62} {c[2]+n[2]*side} {heading} 0')
        time.sleep(.7)
    go(.45);results.append(r.capture(prefix+'-near'))
    go(-.65);r.wait_for(lambda:r.status()['inside'])
    results.append(r.capture(prefix+'-inside'));r.screenshot(prefix+'-inside-overview')
    go(-.65,35);results.append(r.capture(prefix+'-inside-turn'))
    go(-5.65,0,2);results.append(r.capture(prefix+'-inside-move'))
    generation=r.status()['shaderGeneration'];r.request('reload')
    r.wait_for(lambda:r.status()['shaderGeneration']>generation,60);time.sleep(3)
    results.append(r.capture(prefix+'-inside-reload'));assert r.status()['inside']
    go(.75);r.wait_for(lambda:not r.status()['inside'])
    results.append(r.capture(prefix+'-returned'))
    r.request('off');r.screenshot(prefix+'-off')
    assert not r.status()['inside'] and not r.status()['enabled']
    result=dict(prefix=prefix,cases=[c['case'] for c in results],finalTestState=r.status(),
                scope='Enterable lightweight visual domain; player remains in the original Minecraft dimension')
    (r.OUT/'session.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf8')
finally:
    r.request('off')
    r.command(f'/tp @s {pos[0]} {pos[1]} {pos[2]} {yaw} {pitch}')
    r.command('/gamemode '+player['gamemode']+' @s')
    r.request('open');r.request('time',value=-1)
    (r.OUT/'restored.json').write_text(json.dumps(dict(player=r.api('get_player_info'),state=r.status()),indent=2,ensure_ascii=False),encoding='utf8')
print('CROSSING_SESSION_COMPLETE',flush=True)
