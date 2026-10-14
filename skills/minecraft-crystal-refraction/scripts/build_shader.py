"""Generate original GLSL 150 resources for a Forge ShaderInstance adapter."""
import argparse
import json
from pathlib import Path

VERTEX = '''#version 150
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord;
void main(){texCoord=UV0;gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);}
'''

def generate(out):
    root = Path(__file__).resolve().parents[1] / 'assets' / 'lab'
    fragment = '#version 150\n' + (root / 'material.frag').read_text(encoding='utf-8').replace('/*KERNEL*/', (root / 'crystal.glsl').read_text(encoding='utf-8'))
    identity = [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
    uniforms = [{'name': n, 'type': 'matrix4x4', 'count': 16, 'values': identity} for n in ('ModelViewMat', 'ProjMat')]
    defaults = {'Time':0,'Seed':173,'Cells':5,'Jitter':0.85,'BendPx':58,'Dispersion':0.085,'Ior':1.52,'BevelPx':0.8,'EdgeLight':0.07,'TintMix':0.10,'Speed':0.18,'BlurPx':0,'Amount':1}
    uniforms += [{'name':n,'type':'float','count':1,'values':[v]} for n,v in defaults.items()]
    uniforms += [{'name':n,'type':'float','count':len(v),'values':v} for n,v in {'Resolution':[900,600],'Tint':[169/255,239/255,1],'View':[0,0]}.items()]
    uniforms += [{'name':n,'type':'int','count':1,'values':[v]} for n,v in {'Shape':1,'Debug':0}.items()]
    config = {'vertex':'crystalrefraction:crystal_refraction','fragment':'crystalrefraction:crystal_refraction','attributes':['Position','UV0'],'samplers':[{'name':'Source'}],'uniforms':uniforms}
    dest = Path(out) / 'assets' / 'crystalrefraction' / 'shaders' / 'core'
    dest.mkdir(parents=True, exist_ok=True)
    files = {'crystal_refraction.vsh':VERTEX,'crystal_refraction.fsh':fragment,'crystal_refraction.json':json.dumps(config,indent=2)+'\n'}
    # No silent overwrite of an adapter edited by the user.
    conflicts=[str(dest/n) for n in files if (dest/n).exists() and (dest/n).read_text(encoding='utf-8')!=files[n]]
    if conflicts:
        raise ValueError('Existing files differ; choose a new --out directory: '+', '.join(conflicts))
    for n,text in files.items():
        (dest/n).write_text(text,encoding='utf-8')
    print(json.dumps({'status':'generated','files':[str(dest/n) for n in files],'scope':'shader resources only; Java registration and runtime not supplied'},ensure_ascii=False))

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True,type=Path)
    generate(parser.parse_args().out)
