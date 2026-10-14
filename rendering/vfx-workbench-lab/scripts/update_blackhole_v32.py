"""Adapt installed v3.2 originals to the existing Forge world host; no build."""
from pathlib import Path
import json,re,hashlib
import white_hole_adapter
root=Path(__file__).resolve().parents[1]
skill=Path('C:/Users/MAINTAINER/.codex/skills/minecraft-black-hole/assets/preview')
out=root/'src/main/resources/assets/vfxworkbench/shaders/core'
layers=(skill/'layers.js').read_text(encoding='utf-8')
pattern=layers.split('const patternHeader=`',1)[1].split('`;',1)[0]
pattern='uniform float uObjectMode;\n'+pattern
pattern=pattern.replace('#define BH_REFERENCE_RIBBONS (uGranular==2)','#define BH_REFERENCE_RIBBONS (uGranular==2 && uObjectMode<.5)')
halo=layers.split('const header=patternHeader+`',1)[1].split('`;',1)[0]
halo=halo.replace('JetStrength>0.0001?JetLength+uCore+.5:0.0',
                  '(uObjectMode<.5&&JetStrength>0.0001)?JetLength+uCore+.5:0.0')
kernel=(skill/'black-hole.glsl').read_text(encoding='utf-8')
kernel=white_hole_adapter.kernel(kernel)
functions=''.join((skill/n).read_text(encoding='utf-8') for n in ['disk_structure.glsl','styles.glsl','halo.glsl'])
functions=white_hole_adapter.functions(functions)
world=out/'bh_world.fsh'
host=(root/'scripts/world_header.glsl').read_text(encoding='utf-8')
main=(root/'scripts/world_main.glsl').read_text(encoding='utf-8')
world.write_text(host+pattern+halo+kernel+functions+white_hole_adapter.world_functions()+main,encoding='utf-8')
# Particle occlusion uses the same new disk/core field, retaining Forge VBOs.
particles=out/'bh_particles.fsh'
old=particles.read_text(encoding='utf-8')
prefix=old.split('// Original artistic black-hole field.',1)[0]
prefix=prefix.split('uniform int uGranular;',1)[0]
prefix=re.sub(r'#define BH_PATTERN[^\n]*\n','',prefix)
prefix=prefix.replace('uniform float uObjectMode;\n','')
body=old[old.index('void main(){'):]
white_particle_color='''    if(uObjectMode>.5){
        float outwardProgress=clamp((ParticleRadius-max(uInner,uCore*1.12))/max(ParticleOuter-max(uInner,uCore*1.12),.5),0.0,1.0);
        source=mix(ParticleHot,ParticleCold,smoothstep(.05,.90,outwardProgress))*ParticleBrightness*(1.30-.35*outwardProgress);
    }
'''
if white_particle_color not in body:
 body=body.replace('    vec3 light=1.0-exp(-source);',white_particle_color+'    vec3 light=1.0-exp(-source);')
particles.write_text(prefix+pattern+kernel+functions.split('vec3 haloEmission(',1)[0]+body,encoding='utf-8')
snapshot=json.loads((root/'src/main/resources/assets/vfxworkbench/recipes/black-hole-default.json').read_text(encoding='utf-8-sig'))
params=snapshot['parameters']
extra=['beaming','diskGradientPosition','coreGradientStrength','bandDetail','dustScale','dustFill','filament','spiral','haloRadius','haloWidth','haloSharpness','haloGradientMix','haloDiskGradientMix','haloAuraGradientMix','jetStrength','jetLength','jetWidth']
colors=['diskMid','coreEdgeColor','haloGradientStart','haloGradientEnd','haloDiskInnerColor','haloDiskOuterColor','haloAuraInnerColor','haloAuraOuterColor','jetBaseColor','jetTipColor']
phase=['OrbitPhase','RadialPhase','ClassicRadialPhase','ShearLocal','ShearParity','DustDriftA','DustDriftB']
for name in ['world','particles']:
 path=out/f'bh_{name}.json';doc=json.loads(path.read_text(encoding='utf-8-sig'));known={u['name'] for u in doc['uniforms']}
 if not any(s['name']=='RibbonNoise' for s in doc['samplers']):doc['samplers'].append(dict(name='RibbonNoise'))
 def add(n,v,kind='float'):
  if n not in known:doc['uniforms'].append(dict(name=n,type=kind,count=len(v),values=v));known.add(n)
 for k in extra:
  n=k[0].upper()+k[1:];add(n if k.startswith('jet') else 'u'+n,[params[k]])
 for k in colors:
  h=params[k].lstrip('#');v=[int(h[i:i+2],16)/255 for i in (0,2,4)];v=[x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4 for x in v];add('u'+k[0].upper()+k[1:],v)
 for k in phase:add('u'+k,[0])
 if name=='world':
  add('uCoreEmission',[1])
  add('uCoreGlow',[0]);add('uCoreGlowRadius',[1.8])
  add('LensRadius',[2.4]);add('LensProbeMode',[0])
  add('ProbeBoundsMin',[0,0,0]);add('ProbeBoundsMax',[0,0,0])
  add('ForegroundCount',[0],'int')
  add('EnvironmentReady',[0])
  for n,v in [('uJetClock',[0]),('uShellClock',[0]),('uShellStrength',[0]),('uShellRadius',[5]),('uShellWidth',[.12]),('uShellColor',[.1682694,.7379104,1.0])]:add(n,v)
  for sampler in ['Sampler2','Sampler3','Sampler4']:
   if not any(s['name']==sampler for s in doc['samplers']):doc['samplers'].append(dict(name=sampler))
 add('uGranular',[1],'int')
 # Behavior is explicit; negative legacy speed is never an implicit white switch.
 add('uObjectMode',[0]);path.write_text(json.dumps(doc,indent=2),encoding='utf-8')
 if name=='particles':
  add('uParticleClock',[0]);path.write_text(json.dumps(doc,indent=2),encoding='utf-8')
report=dict(kernelSha256=hashlib.sha256(kernel.encode()).hexdigest(),source=str(skill),buildExecuted=False,runtimeVisualVerified=False,migrated=['v3.2 integrator','five disk styles','core and disk gradients','layered halo and polar jets','bounded motion phases'],excluded=['browser background replacement','HDR bloom postprocessing','browser camera orbit'])
report['migrated']+=['white object mode','outward particle-compatible disk transport','white core emission gain','continuous outward ribbon/cinematic material renewal']
report['migrated']+=['white bilateral axial material transport on depth-clipped straight rays','two analytically intersected expanding thin spherical shells','screen-footprint conserving external material integration']
report['migrated']+=['white stationary finite soft core corona using core colors and emission gain independently of disk halo and shells']
(root/'evidence/blackhole-generation-latest.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
