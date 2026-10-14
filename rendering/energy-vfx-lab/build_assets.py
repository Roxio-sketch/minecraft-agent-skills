"""Embed shaders for offline use, emit strict schema and shared Forge resource files."""
from pathlib import Path
import json, shutil, subprocess
ROOT=Path(__file__).parent
noise=ROOT.parent/'energy-vfx-research/sources/stegu--psrdnoise/src/psrdnoise3.glsl'
shutil.copy2(noise,ROOT/'noise.glsl')
shaders={k:(ROOT/(k+'.glsl')).read_text(encoding='utf-8') for k in ['noise','field','scene','volume','blur','composite']}
(ROOT/'shaders.js').write_text('/* Embedded, offline shader sources. See noise.glsl for MIT attribution. */\nconst SHADERS = '+json.dumps(shaders,ensure_ascii=False)+';\n',encoding='utf-8')
node='E:/nodejs/node.exe'
raw=subprocess.check_output([node,'-e',"const s=require(process.argv[1]);process.stdout.write(JSON.stringify({fields:s.FIELDS,presets:s.PRESETS}));",str(ROOT/'schema.js')])
data=json.loads(raw)
(ROOT/'recipe-schema.json').write_text(json.dumps(data['fields'],ensure_ascii=False,indent=2),encoding='utf-8')
for i,p in enumerate(data['presets']):
 (ROOT/'presets').mkdir(exist_ok=True)
 (ROOT/'presets'/f'{i+1}.json').write_text(json.dumps(p,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Embedded 5 shaders; emitted {len(data["fields"])}-field schema and {len(data["presets"])} presets.')
