import argparse,json
from pathlib import Path
from validate_recipe import validate
p=argparse.ArgumentParser();p.add_argument('recipe',type=Path);p.add_argument('destination',type=Path);a=p.parse_args();data=validate(json.loads(a.recipe.read_text(encoding='utf-8-sig')))
root=Path(__file__).resolve().parents[1];mid='adapterlab';mode='adapter'
default=json.loads((root/f'assets/forge-1.20.1/src/main/resources/assets/{mid}/rendercraft/recipe.json').read_text(encoding='utf8'));default.update(data)
shaders={}
core=root/f'assets/forge-1.20.1/src/main/resources/assets/{mid}/shaders/core'
for name in []:shaders[name]={'vsh':(core/(name+'.vsh')).read_text(encoding='utf8'),'fsh':(core/(name+'.fsh')).read_text(encoding='utf8')}
js=(root/'assets/workbench.js').read_text(encoding='utf8');a.destination.mkdir(parents=True,exist_ok=True)
page='<!doctype html><meta charset="utf-8"><title>Rendercraft Workbench</title><style>body{background:#101725;color:#eaf2ff;font:16px system-ui;margin:24px}canvas{width:min(80vh,90vw);height:min(80vh,90vw)}button{padding:10px;margin:6px}pre{white-space:pre-wrap}</style><h1>'+mode+' workbench</h1><p>Original independent preview. Minecraft integration requires its own verification.</p><button id="pause">Pause / play</button><button id="reset">Reset seed and time</button><br><canvas width="640" height="640"></canvas><pre id="status"></pre><script>const MODE='+json.dumps(mode)+';const RECIPE='+json.dumps(default)+';const SHADERS='+json.dumps(shaders)+';'+js+'</script>'
(a.destination/'index.html').write_text(page,encoding='utf8');print((a.destination/'index.html').resolve())
