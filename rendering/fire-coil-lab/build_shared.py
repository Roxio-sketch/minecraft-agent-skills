from pathlib import Path
import json, hashlib
ROOT=Path(__file__).resolve().parent
core=(ROOT/'fire.frag.core.glsl').read_text(encoding='utf-8')
volume=(ROOT/'fire.volume.core.glsl').read_text(encoding='utf-8')
(ROOT/'shaders.js').write_text(
    'export const FIRE_FRAGMENT_CORE = '+json.dumps(core,ensure_ascii=False)+';\n'
    +'export const FIRE_VOLUME_CORE = '+json.dumps(volume,ensure_ascii=False)+';\n',encoding='utf-8')
print('ember:',hashlib.sha256(core.encode()).hexdigest())
print('volume:',hashlib.sha256(volume.encode()).hexdigest())
