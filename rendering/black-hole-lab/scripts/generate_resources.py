"""Package the installed original kernel in Forge GLSL 150 host shaders."""
from pathlib import Path
import argparse, json, hashlib

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--kernel',type=Path)
args=parser.parse_args()
kernel_path=args.kernel or next((p for p in [root/'scripts/black-hole.glsl',root.parent/'skill/minecraft-black-hole/assets/preview/black-hole.glsl'] if p.is_file()),None)
if kernel_path is None:parser.error('Pass --kernel with the original black-hole.glsl path')
kernel=kernel_path.read_text(encoding='utf-8')
structure=(root/'scripts/disk_structure.glsl').read_text(encoding='utf-8')
out=root/'src/main/resources/assets/blackholelab/shaders/core'
out.mkdir(parents=True,exist_ok=True)
identity=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
def uniform(name,values,kind='float'):
    if not isinstance(values,list): values=[values]
    return dict(name=name,type=kind,count=len(values),values=values)
common=[uniform('ModelViewMat',identity,'matrix4x4'),uniform('ProjMat',identity,'matrix4x4')]
for name,value in dict(uTime=0,uCore=.58,uInner=.94,uOuter=2.9,uThickness=.105,uTilt=-12,uBend=1.1,uDensity=6,uSpeed=.55,uInfall=.22,uSeed=17,uExposure=1.1).items():common.append(uniform(name,value))
common += [uniform('uSteps',160,'int'),uniform('uHot',[1,1,1]),uniform('uCool',[1,.1,0]),uniform('uCoreColor',[0,0,0])]
display='''
vec3 bhDisplay(vec3 linearColor){
 vec3 c=1.0-exp(-max(linearColor,vec3(0))*uExposure);
 return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-.055,step(vec3(.0031308),c));
}
vec3 bhView(vec2 uv){
 vec3 ro=7.8*vec3(sin(.2)*cos(.15),sin(.15),cos(.2)*cos(.15));
 vec3 forward=normalize(-ro),right=normalize(cross(forward,vec3(0,1,0))),up=cross(right,forward);
 return bhDisplay(bhCompositeInternal(bhTrace(ro,normalize(forward+(right*uv.x+up*uv.y)*.47))));
}
'''
(out/'item.vsh').write_text('''#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position; in vec4 Color; in vec2 UV0; in ivec2 UV1; in ivec2 UV2;
uniform mat4 ModelViewMat,ProjMat; uniform int FogShape;
uniform sampler2D Sampler1,Sampler2;
out vec2 texCoord;out vec4 baseColor,overlayColor;out float vertexDistance;
void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1);texCoord=UV0;
 baseColor=Color*minecraft_sample_lightmap(Sampler2,UV2);overlayColor=texelFetch(Sampler1,UV1,0);
 vertexDistance=fog_distance(ModelViewMat,Position,FogShape);}
''',encoding='utf-8')
(out/'item.fsh').write_text('''#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;uniform vec4 ColorModulator,uRect,FogColor;
uniform float uExposure,FogStart,FogEnd;uniform int uMask;
in vec2 texCoord;in vec4 baseColor,overlayColor;in float vertexDistance;out vec4 fragColor;
'''+kernel+display+'''
void main(){
 vec4 base=texture(Sampler0,texCoord);if(base.a<.1)discard;
 vec2 uv=(texCoord-uRect.xy)/max(uRect.zw-uRect.xy,vec2(.000001));
 uv=vec2(uv.x*2-1,1-uv.y*2);
 float mask=uMask==0?1.0:step(base.r*1.3,base.g)*step(base.r*1.4,base.b);
 vec3 rgb=mix(base.rgb*baseColor.rgb,bhView(uv),mask);
 rgb=mix(overlayColor.rgb,rgb,overlayColor.a);
 fragColor=linear_fog(vec4(rgb,base.a*baseColor.a)*ColorModulator,vertexDistance,FogStart,FogEnd,FogColor);
}
''',encoding='utf-8')
(out/'scene.vsh').write_text('''#version 150
in vec3 Position;in vec2 UV0;uniform mat4 ModelViewMat,ProjMat;out vec2 texCoord;
void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1);texCoord=UV0;}
''',encoding='utf-8')
(out/'scene.fsh').write_text('''#version 150
uniform float uExposure;uniform int uWorld;uniform vec3 uOrigin,uRight,uUp;
in vec2 texCoord;out vec4 fragColor;
'''+kernel+display+'''
void main(){
 vec2 uv=texCoord*2-1;
 if(uWorld==0){fragColor=vec4(bhView(uv),1);return;}
 vec3 point=(uRight*uv.x+uUp*uv.y)*(uOuter*1.5);
 BHResult hit=bhTrace(uOrigin,normalize(point-uOrigin));
 float alpha=1.0-hit.transmission;if(alpha<.001)discard;
 // Premultiplied emission/absorption against the existing Minecraft world.
 // It does not sample or bend real-world scene color.
 fragColor=vec4(bhDisplay(hit.light),alpha);
}
''',encoding='utf-8')
item=dict(vertex='blackholelab:item',fragment='blackholelab:item',attributes=['Position','Color','UV0','UV1','UV2','Normal'],samplers=[dict(name=n) for n in ['Sampler0','Sampler1','Sampler2']],uniforms=common+[
 uniform('ColorModulator',[1,1,1,1]),uniform('uRect',[0,0,1,1]),uniform('uMask',0,'int'),uniform('FogStart',0),uniform('FogEnd',1),uniform('FogColor',[0,0,0,0]),uniform('FogShape',0,'int')],blend=dict(func='add',srcrgb='srcalpha',dstrgb='1-srcalpha'))
scene=dict(vertex='blackholelab:scene',fragment='blackholelab:scene',attributes=['Position','UV0'],samplers=[],uniforms=common+[
 uniform('uWorld',0,'int'),uniform('uOrigin',[0,0,7.8]),uniform('uRight',[1,0,0]),uniform('uUp',[0,1,0])],blend=dict(func='add',srcrgb='one',dstrgb='1-srcalpha',srcalpha='one',dstalpha='1-srcalpha'))
for name,data in [('item',item),('scene',scene)]:
 (out/(name+'.json')).write_text(json.dumps(data,indent=2),encoding='utf-8')
# The world compositor has a separate fullscreen input contract. Scene/panel and
# items keep their native vertex layouts and do not sample the world framebuffer.
(out/'world.vsh').write_text('''#version 150
in vec3 Position;in vec2 UV0;out vec2 texCoord;
void main(){gl_Position=vec4(Position,1);texCoord=UV0;}
''',encoding='utf-8')
(out/'world.fsh').write_text((root/'scripts/world_header.glsl').read_text(encoding='utf-8')+kernel+structure+display+
 (root/'scripts/world_main.glsl').read_text(encoding='utf-8'),encoding='utf-8')
world=dict(vertex='blackholelab:world',fragment='blackholelab:world',attributes=['Position','UV0'],
 samplers=[dict(name='Sampler0'),dict(name='Sampler1')],uniforms=common[2:]+[
 uniform('InvViewProjection',identity,'matrix4x4'),uniform('ViewProjection',identity,'matrix4x4'),
 uniform('CenterRelative',[0,0,-14]),uniform('WorldScale',1.6),uniform('LensStrength',1),
 uniform('HaloStrength',1),uniform('HaloRing',1),uniform('HaloDisk',1),uniform('HaloAura',1)])
(out/'world.json').write_text(json.dumps(world,indent=2),encoding='utf-8')
(out/'particles.vsh').write_text((root/'scripts/particles.vsh').read_text(encoding='utf-8'),encoding='utf-8')
(out/'particles.fsh').write_text((root/'scripts/particles_header.glsl').read_text(encoding='utf-8')+kernel+structure+
 (root/'scripts/particles_main.glsl').read_text(encoding='utf-8'),encoding='utf-8')
particle_common=[u for u in common[2:] if u['name'] not in ['uExposure','uHot','uCool','uCoreColor']]
particles=dict(vertex='blackholelab:particles',fragment='blackholelab:particles',attributes=['Position','UV0'],samplers=[],
 uniforms=particle_common+[
 uniform('ViewProjection',identity,'matrix4x4'),uniform('CenterRelative',[0,0,14]),uniform('WorldScale',1.6),
 uniform('CameraRight',[1,0,0]),uniform('CameraUp',[0,1,0]),uniform('ParticleSpeed',1),uniform('ParticleSize',.022),
 uniform('ParticleOuter',5.4),uniform('ParticleBrightness',1.25),uniform('ParticleDebug',0,'int'),
 uniform('ParticleCold',[1,.2,.03]),uniform('ParticleHot',[1,.9,.65])])
(out/'particles.json').write_text(json.dumps(particles,indent=2),encoding='utf-8')
print('Packaged kernel SHA-256:',hashlib.sha256(kernel.encode()).hexdigest())
