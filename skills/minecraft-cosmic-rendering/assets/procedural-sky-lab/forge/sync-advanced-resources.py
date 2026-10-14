#!/usr/bin/env python3
"""Run after the root sky/environment sync; generate only native surface/LUT resources."""
from pathlib import Path
import copy
import json
import re
import subprocess
import argparse
import shutil

HERE = Path(__file__).resolve().parent
WORKBENCH = HERE.parent / 'workbench'
CORE = HERE / 'src/main/resources/assets/cosmiclab/shaders/core'
VANILLA = HERE / 'tools/vanilla-surface'


def write(name, value):
    (CORE / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n' if isinstance(value, dict) else value,
                             encoding='utf-8')


def uniform(name, count=1, kind='float', values=None):
    return dict(name=name, type=kind, count=count, values=values or [0] * count)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--node', help='Optional Node executable; otherwise generate the same GLSL 150 boilerplate in Python')
    args = parser.parse_args()
    # Only validation consumes these presets; the published default is unchanged.
    presets = json.loads((WORKBENCH / 'presets.json').read_text(encoding='utf-8'))
    validation_ids = {'gasgiant','icegiant','fragmentmoon','glowingdarkdisk','binarystars','flaringstar','vortexdarkstar','cinematicblackhole'}
    selected = {p['id']:p['recipe'] for p in presets if p['id'] in validation_ids}
    assert set(selected) == validation_ids
    preset_target = CORE.parent.parent / 'sky' / 'validation-presets.json'
    preset_target.parent.mkdir(parents=True, exist_ok=True)
    preset_target.write_text(json.dumps(selected, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    physical = WORKBENCH / 'atmosphere-physical.glsl'
    module = (WORKBENCH / 'atmosphere-luts.js').as_uri()
    script = f"import fs from 'node:fs';import {{buildAtmospherePassFragment}} from {json.dumps(module)};const source=fs.readFileSync(process.argv[1],'utf8');for(const p of ['transmittance','multiple','skyView'])fs.writeFileSync(process.argv[2]+'/atmosphere_'+(p==='skyView'?'sky_view':p)+'.fsh',buildAtmospherePassFragment(source,p,'glsl150'));"
    if args.node:
        subprocess.run([args.node, '--input-type=module', '-e', script, str(physical), str(CORE)], check=True)
    else:
        source = physical.read_text(encoding='utf-8')
        header = '#version 150\nout vec4 fragColor;\n'
        declarations = 'uniform int Quality;\nuniform vec4 DaySettings;\nuniform vec4 SunSettings;\nuniform vec3 SunColor;\nuniform vec3 SkyTint;\nuniform vec4 AtmosphereGeometry;\nuniform vec4 AtmosphereComposition;\nuniform vec4 AtmospherePhysical;\n'
        for filename, define, function in [('transmittance', 'TRANSMITTANCE', 'apGenerateTransmittance'),
                ('multiple', 'MULTIPLE', 'apGenerateMultiple'), ('sky_view', 'SKY_VIEW', 'apGenerateSkyView')]:
            write('atmosphere_' + filename + '.fsh', header + '#define ATMOSPHERE_' + define + '_PASS 1\n' + declarations + source + '\nvoid main(){fragColor=' + function + '(gl_FragCoord.xy);}\n')
    base_uniforms = [uniform('Quality', kind='int', values=[2])]
    base_uniforms += [uniform(n, 4) for n in ['DaySettings', 'SunSettings', 'AtmosphereGeometry', 'AtmosphereComposition', 'AtmospherePhysical']]
    base_uniforms += [uniform(n, 3) for n in ['SunColor', 'SkyTint', 'AtmosphereLutSun']]
    base_uniforms += [uniform('PhysicalObserverHeight'), uniform('AtmosphereLutReady', kind='int'), uniform('AtmosphereSkyViewReady', kind='int')]
    generated_defaults = {u['name']:u for u in json.loads((CORE/'environment.json').read_text(encoding='utf-8'))['uniforms']}
    for u in base_uniforms:
        if u['name'] in generated_defaults and generated_defaults[u['name']]['count'] == u['count']:
            u['values'] = copy.deepcopy(generated_defaults[u['name']]['values'])
    samplers = [dict(name=n) for n in ['AtmosphereTransmittance', 'AtmosphereMultipleScattering', 'AtmosphereSkyViewSampler']]
    vsh = '#version 150\nin vec3 Position;void main(){gl_Position=vec4(Position,1.0);}\n'
    for name in ['transmittance', 'multiple', 'sky_view']:
        program = 'atmosphere_' + name
        write(program + '.vsh', vsh)
        write(program + '.json', dict(vertex='cosmiclab:' + program, fragment='cosmiclab:' + program,
              attributes=['Position'], samplers=samplers, uniforms=base_uniforms))
    # The wrapper preserves the exact vanilla light/UV/alpha/fog calculation.
    original_vsh = (VANILLA / 'core/rendertype_translucent.vsh').read_text()
    original_fsh = (VANILLA / 'core/rendertype_translucent.fsh').read_text()
    vertex = original_vsh.replace('void main() {', 'out vec3 surfacePosition;\nvoid main() {')
    vertex = vertex.replace('vec3 pos = Position + ChunkOffset;', 'vec3 pos = Position + ChunkOffset;\n    surfacePosition = pos;')
    bundle = (CORE / 'environment.fsh').read_text(encoding='utf-8')
    bundle = bundle[:bundle.rfind('void main')]
    bundle = re.sub(r'^#version[^\n]*\n', '', bundle)
    bundle = re.sub(r'\b(?:in vec2 screenUV|out vec4 fragColor);', '', bundle)
    # Vanilla FogColor is vec4 and is set by LevelRenderer; the artistic medium has a separate vec3.
    bundle = re.sub(r'\bFogColor\b', 'EnvironmentFogColor', bundle)
    fragment = original_fsh.replace('void main() {', 'in vec3 surfacePosition;\nuniform vec3 OpticalEyeOffset;\n' + bundle + '\nvoid main() {')
    fragment = fragment.replace('fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);',
        'fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);\n'
        '    vec3 fromEye=surfacePosition-OpticalEyeOffset;\n'
        '    float distanceToSurface=length(fromEye);\n'
        '    vec3 ray=distanceToSurface>1e-5?fromEye/distanceToSurface:vec3(0.0,0.0,1.0);\n'
        '    fragColor.rgb=skyComposeEnvironment(fragColor.rgb,CameraPosition+OpticalEyeOffset,ray,distanceToSurface,true);')
    write('surface.vsh', vertex); write('surface.fsh', fragment)
    native = json.loads((VANILLA / 'core/rendertype_translucent.json').read_text())
    environment = json.loads((CORE / 'environment.json').read_text())
    native['vertex'] = native['fragment'] = 'cosmiclab:surface'
    native['uniforms'].append(uniform('OpticalEyeOffset',3))
    seen = {u['name'] for u in native['uniforms']}
    for source in environment['uniforms']:
        u = copy.deepcopy(source)
        if u['name'] == 'FogColor': u['name'] = 'EnvironmentFogColor'
        if u['name'] not in seen: native['uniforms'].append(u); seen.add(u['name'])
    names = {s['name'] for s in native['samplers']}
    for s in environment['samplers']:
        if s['name'] not in names and s['name'] not in ['SceneSampler', 'DepthSampler']:
            native['samplers'].append(s); names.add(s['name'])
    write('surface.json', native)
    write('environment_composite.vsh', '#version 150\nin vec3 Position;out vec2 screenUV;void main(){gl_Position=vec4(Position,1.0);screenUV=Position.xy*.5+.5;}\n')
    write('environment_composite.fsh', '''#version 150
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EnvironmentSampler;
uniform mat4 InverseProjection;
uniform vec2 LowSize;
uniform float DepthTolerance;
uniform int OutputDeltaEncoding;
in vec2 screenUV;out vec4 fragColor;
float sceneDistance(vec2 uv,float depth){vec4 p=InverseProjection*vec4(uv*2.-1.,depth*2.-1.,1.);vec4 eye=InverseProjection*vec4(0.,0.,1.,0.);return length(p.xyz/max(abs(p.w),1e-7)-eye.xyz/eye.w);}
void main(){
 vec3 original=texture(SceneSampler,screenUV).rgb;
 float depth=texture(DepthSampler,screenUV).r;bool sky=depth>=.999999;
 float distanceToScene=sceneDistance(screenUV,depth);
 vec2 pixel=screenUV*LowSize-.5,lower=floor(pixel),f=fract(pixel);
 vec3 difference=vec3(0.);float weights=0.;
 for(int y=0;y<2;y++)for(int x=0;x<2;x++){
   vec2 uv=(lower+vec2(x,y)+.5)/LowSize;
   float lowDepth=texture(DepthSampler,uv).r;bool lowSky=lowDepth>=.999999;
   float weight=(x==0?1.-f.x:f.x)*(y==0?1.-f.y:f.y);
   if(sky!=lowSky)weight=0.;
   else if(!sky){float lowDistance=sceneDistance(uv,lowDepth);float relative=abs(lowDistance-distanceToScene)/max(min(lowDistance,distanceToScene),1.);if(relative>4.*max(DepthTolerance,.001))weight=0.;else weight*=exp(-relative/max(DepthTolerance,.001));}
   vec3 delta=texture(EnvironmentSampler,uv).rgb;
   if(OutputDeltaEncoding==2)delta=(delta-128./255.)*(255./127.);
   difference+=delta*weight;weights+=weight;
 }
 fragColor=vec4(clamp(original+(weights>1e-5?difference/weights:vec3(0.)),0.,1.),1.);
}
''')
    write('environment_composite.json', dict(vertex='cosmiclab:environment_composite', fragment='cosmiclab:environment_composite',
          attributes=['Position'], samplers=[dict(name=n) for n in ['SceneSampler', 'DepthSampler', 'EnvironmentSampler']],
          uniforms=[uniform('InverseProjection', 16, 'matrix4x4', [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]),
                    uniform('LowSize',2),uniform('DepthTolerance'),uniform('OutputDeltaEncoding',kind='int',values=[2])]))
    hashes = json.loads((VANILLA / 'hashes.json').read_text())
    # Mojang light include differs in vanilla/Forge due to a Forge patch; both were audited.
    hashes['include/light.glsl'] += ',20da6f438411cd292b62d42745a59ee399c7ed1b8033806fbcf4d5f03fdcda53'
    (HERE / 'src/main/resources/cosmiclab-vanilla-contract.properties').write_text(
        '\n'.join(f'{name}={value}' for name, value in hashes.items()) + '\n', encoding='utf-8')
    resources = HERE / 'src/main/resources'
    support = WORKBENCH / 'forge-support'
    extras = [p for p in CORE.iterdir() if p.name.startswith(('atmosphere_', 'surface.', 'environment_composite.'))]
    extras += [resources/'cosmiclab-vanilla-contract.properties',resources/'cosmiclab.mixins.json',resources/'META-INF/MANIFEST.MF']
    for p in extras:
        target = support / p.relative_to(resources); target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(p,target)
    print('Generated 3 physical LUT programs + 1 native BLOCK surface wrapper')


if __name__ == '__main__': main()
