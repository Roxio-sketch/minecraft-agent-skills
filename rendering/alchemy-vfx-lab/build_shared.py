from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parent
core=(ROOT/'alchemy.volume.core.glsl').read_text(encoding='utf-8')
(ROOT/'shaders.js').write_text('export const ALCHEMY_VOLUME_CORE='+json.dumps(core,ensure_ascii=False)+';\n',encoding='utf-8')
print(hashlib.sha256(core.encode()).hexdigest())
