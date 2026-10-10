"""Check committed shader regeneration and the standalone namespace, without launching Minecraft."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources/assets/blackholelab/shaders/core'
hashes=lambda:{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in resources.iterdir() if p.is_file()}
before=hashes()
subprocess.run([sys.executable,str(root/'scripts/generate_resources.py')],check=True)
assert before==hashes(), 'Committed GLSL/JSON differs from its source generator'
for path in (root/'src/main/java').rglob('*.java'):
    assert 'dev.vfxworkbench' not in path.read_text(encoding='utf-8'), path
for host,count in [('world',5),('particles',0)]:
    descriptor=json.loads((resources/(host+'.json')).read_text(encoding='utf-8'))
    assert descriptor['vertex']==descriptor['fragment']=='blackholelab:'+host
    assert len(descriptor['samplers'])==count
    assert len({u['name'] for u in descriptor['uniforms']})==len(descriptor['uniforms'])
    assert 'blend' not in descriptor, 'World owns manual blend/cache state'
    uniforms={u['name']:u for u in descriptor['uniforms']}
    assert uniforms['uObjectMode']['values']==[0], 'Fresh mode must remain black'
    if host=='world':
        assert uniforms['uCoreEmission']['values']==[1]
        assert uniforms['uCoreGlow']['values']==[0] and uniforms['uCoreGlowRadius']['values']==[1.8]
        assert uniforms['uJetClock']['values']==uniforms['uShellClock']['values']==[0]
        assert uniforms['uShellStrength']['values']==[0]
        for required in ['uShellRadius','uShellWidth','uShellColor']:assert required in uniforms
    else:
        assert uniforms['uParticleClock']['values']==[0]
        assert not any(name in uniforms for name in ['uJetClock','uShellClock','uShellStrength','uShellRadius','uShellWidth','uShellColor','uCoreGlow','uCoreGlowRadius'])
        source=(resources/(host+'.fsh')).read_text(encoding='utf-8')
        assert 'whiteOutflowTrace' not in source
world=(root/'src/main/java/dev/blackholelab/BlackHoleWorld.java').read_text(encoding='utf-8')
assert 'playerParticipates||(!alwaysProtectSubject&&whollyBehind)' in world
assert 'BlackHoleCamera.origin(inverse)' in world
print(json.dumps({'status':'passed','regeneratedShaderFiles':len(before),'independentNamespace':True,'whiteFlowInterfaces':True,'minecraftRuntimeVerified':False}))

for host in ['core_blur','core_composite']:
 d=json.loads((resources/(host+'.json')).read_text(encoding='utf-8'))
 assert d['vertex']==d['fragment']=='blackholelab:'+host
 assert 'blend' not in d
 assert 'sampler2D' in (resources/(host+'.fsh')).read_text(encoding='utf-8')
