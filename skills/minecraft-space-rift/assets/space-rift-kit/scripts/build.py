"""Generate all shader wrappers from one maintained core. No network required."""
from pathlib import Path
import json,subprocess
ROOT=Path(__file__).resolve().parents[1]
SH=ROOT/'shader'
NAMES='length width angle bend roughness frequency detail branches branchLength seed glow edgeThickness rimWidth rimDepth rimRelief horizon tipPower domainWarp edgeReach interiorDepth starDensity galaxyX galaxyY galaxyScale interiorBrightness viewTiltX viewTiltY distortion dispersion insideSpeed opacity debris layers progress open shrink localClose dynamic speed zoom grid mode quality debug sequence sequenceEnabled corona filaments galaxies interiorMode style styleAmount styleScale edgeMotion'.split()
def build():
    core='\n'.join((SH/name).read_text(encoding='utf-8') for name in ['psrdnoise2.glsl','rift_core.glsl','rift_rim.glsl','styles.glsl','interior-sky.glsl','interior-adapter.glsl','rift_horizon.glsl'])
    uniforms='\n'.join('uniform float u_'+n+';' for n in NAMES)+'\nuniform vec3 u_edgeColor,u_insideColor;\nuniform vec4 u_spine[33],u_branchA[8],u_branchB[8];\n'
    web='''#version 300 es
precision highp float;
precision highp int;
uniform vec2 u_resolution,u_pan;
uniform float u_time;
uniform sampler2D SceneColor;
out vec4 outColor;
'''+uniforms+core+'''
void main(){
 vec2 uv=(gl_FragCoord.xy-.5*u_resolution)*2./u_resolution.y/u_zoom;
 mat2 rot=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));
 vec2 p=rot*(uv+u_pan); riftFootprint=max(length(dFdx(p)),length(dFdy(p)));
 float t=u_time*u_speed;
 RiftSample r=riftEvaluate(p,t);
 vec2 shift=transpose(rot)*r.warp*u_zoom*.5*vec2(u_resolution.y/u_resolution.x,1.);
 vec2 screen=gl_FragCoord.xy/u_resolution;
 vec3 bg=vec3(texture(SceneColor,clamp(screen+shift*(1.+u_dispersion),vec2(.001),vec2(.999))).r,
 texture(SceneColor,clamp(screen+shift,vec2(.001),vec2(.999))).g,
 texture(SceneColor,clamp(screen+shift*(1.-u_dispersion),vec2(.001),vec2(.999))).b);
 outColor=vec4(bg*(1.-r.surface.a)+r.surface.rgb,1.);
 if(u_debug>.5){
  if(u_debug<1.5)outColor=vec4(vec3(r.opening),1.);
  else if(u_debug<2.5)outColor=vec4(vec3(.5+r.distance*3.),1.);
  else if(u_debug<3.5)outColor=vec4(r.warp*12.+.5,0.,1.);
  else if(u_debug<4.5)outColor=vec4(u_horizon>.5?riftHorizonEdgeDebug(p,t):vec3(exp(-abs(r.distance)/max(u_edgeThickness,.0001))),1.);
  else outColor=vec4(riftInterior(p/riftEventScale(),t)*r.interiorVisibility,1.);
 }
}
'''
    (SH/'rift.frag').write_text(web,encoding='utf-8')
    # Shadertoy adapter computes deterministic midpoint points in constant bounded loops.
    toy='''// Paste into Image. No Common/Buffer/iChannel inputs needed.
// Edit constants below; same core as WebGL/Forge. No artwork copied from Shadertoy.
#define u_time iTime
'''
    values=dict(length=1.8,width=.16,angle=.12,bend=.6,roughness=1.6,frequency=18,detail=6,branches=5,branchLength=.35,seed=91,glow=1.5,edgeThickness=.003,distortion=.7,dispersion=.04,insideSpeed=.4,opacity=1,debris=.4,layers=3,progress=1,open=1,shrink=0,localClose=0,dynamic=.15,speed=1,zoom=1,grid=1,mode=1,quality=2,debug=0,sequence=.82,sequenceEnabled=1,corona=1.2,filaments=.7,galaxies=1.2,interiorMode=1,style=0,styleAmount=1,styleScale=1,edgeMotion=1)
    values.update(rimWidth=.045,rimDepth=.07,rimRelief=.8,viewTiltX=.28,viewTiltY=-.16)
    values.update(horizon=0,tipPower=1.65,domainWarp=.25,edgeReach=.032,interiorDepth=1.4,starDensity=1,galaxyX=0,galaxyY=0,galaxyScale=.65,interiorBrightness=1)
    toy+='\n'.join(f'const float u_{n}={float(values[n])};' for n in NAMES)
    toy+='\nconst vec3 u_edgeColor=vec3(.31,.86,.91),u_insideColor=vec3(.03,.15,.19);\nvec4 u_spine[33],u_branchA[8],u_branchB[8];\n'+core
    toy+='''
void mainImage(out vec4 fragColor,in vec2 fragCoord){
 u_spine[0]=vec4(0.,-.5*u_length,0.,0.);u_spine[32]=vec4(0.,.5*u_length,0.,0.);
 for(int level=0;level<5;level++){int stride=32/int(exp2(float(level)));for(int j=0;j<16;j++){int a=j*stride;if(a>=32)break;int b=a+stride,m=(a+b)/2;float x=(u_spine[a].x+u_spine[b].x)*.5+(riftHash11(float(m)+u_seed+float(level)*71.)-.5)*u_bend*1.65*pow(.53,float(level));u_spine[m]=vec4(x,(float(m)/32.-.5)*u_length,0.,0.);}}
 for(int i=0;i<8;i++){float fi=float(i),y=(fi/7.*1.6-.8)*u_length*.5;float cx=riftSpine(y);float side=mod(fi,2.)<.5?-1.:1.;float reach=u_branchLength*(.65+riftHash11(fi+u_seed)*.65);vec2 a=vec2(cx,y),b=a+vec2(side*(u_width*(1.+u_mode)+reach),reach*(riftHash11(fi+13.)-.5));u_branchA[i]=vec4(a,mix(a,b,.55));u_branchB[i]=vec4(b,.3,.4);}
 vec2 uv=(fragCoord-.5*iResolution.xy)*2./iResolution.y/u_zoom;
 mat2 rot=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));vec2 p=rot*uv;
 riftFootprint=max(length(dFdx(p)),length(dFdy(p)));RiftSample r=riftEvaluate(p,iTime*u_speed);
 vec2 q=uv+transpose(rot)*r.warp;vec3 bg=vec3(riftBackground(q*(1.+u_dispersion),iTime).r,riftBackground(q,iTime).g,riftBackground(q*(1.-u_dispersion),iTime).b);
 fragColor=vec4(bg*(1.-r.surface.a)+r.surface.rgb,1.);
}
'''
    (SH/'shadertoy.glsl').write_text(toy,encoding='utf-8')
    # Forge's shader import preprocessor gets fully expanded source; no ES precision qualifiers.
    forge=ROOT/'forge/src/main/resources/assets/spacerift/shaders/core';forge.mkdir(parents=True,exist_ok=True)
    fsh='''#version 150
uniform sampler2D SceneColor;
uniform vec2 ScreenSize;
uniform sampler2D InteriorColor;
uniform float u_hasInterior;
uniform float u_depthOnly;
uniform float u_time;
in vec2 LocalPlane;
out vec4 fragColor;
'''+uniforms+core+'''
void main(){
 mat2 rot=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));vec2 p=rot*LocalPlane;
 riftFootprint=max(length(dFdx(p)),length(dFdy(p)));
 if(u_depthOnly>.5){if(riftAperture(p,u_time*u_speed)<.98 || u_opacity<.98)discard;fragColor=vec4(0.);return;}
 RiftSample r=riftEvaluate(p,u_time*u_speed);
 if(r.surface.a<.0001 && length(r.warp)<.00001)discard;
 // Screen derivatives map local displacement to the projected plane, including arbitrary roll.
 vec2 dx=dFdx(p),dy=dFdy(p);float det=dx.x*dy.y-dy.x*dx.y;
 vec2 pixels=abs(det)>1e-10?vec2(dy.y*r.warp.x-dy.x*r.warp.y,-dx.y*r.warp.x+dx.x*r.warp.y)/det:vec2(0.);
 vec2 uv=gl_FragCoord.xy/ScreenSize,shift=clamp(pixels/ScreenSize,vec2(-.08),vec2(.08));
 vec3 bg=vec3(texture(SceneColor,clamp(uv+shift*(1.+u_dispersion),vec2(.001),vec2(.999))).r,texture(SceneColor,clamp(uv+shift,vec2(.001),vec2(.999))).g,texture(SceneColor,clamp(uv+shift*(1.-u_dispersion),vec2(.001),vec2(.999))).b);
 // Opaque snapshot replacement on the finite quad; hardware depth tests the original plane.
 if(u_interiorMode>1.5 && u_hasInterior>.5)r.surface.rgb+=texture(InteriorColor,uv).rgb*r.interiorVisibility*u_opacity*(1.-smoothstep(.91,1.,u_shrink));
 fragColor=vec4(bg*(1.-r.surface.a)+r.surface.rgb,1.);
}
'''
    (forge/'rift.fsh').write_text(fsh,encoding='utf-8')
    declarations=[{'name':n,'type':'float','count':1,'values':[1.]} for n in ['u_'+x for x in NAMES]+['u_time','u_depthOnly','u_hasInterior']]
    declarations +=[{'name':n,'type':'float','count':3,'values':[1.,1.,1.]} for n in ['u_edgeColor','u_insideColor']]
    declarations +=[{'name':n,'type':'float','count':count*4,'values':[0.]*(count*4)} for n,count in [('u_spine',33),('u_branchA',8),('u_branchB',8)]]
    # Array uniforms uploaded with direct GL calls; ShaderInstance's Uniform parser is scalar/vector/matrix only.
    declarations=[x for x in declarations if x['name'] not in ['u_spine','u_branchA','u_branchB']]
    declarations +=[{'name':'ScreenSize','type':'float','count':2,'values':[1.,1.]},{'name':'ViewProjection','type':'matrix4x4','count':16,'values':[1. if i%5==0 else 0. for i in range(16)]}]
    (forge/'rift.json').write_text(json.dumps({'vertex':'spacerift:rift','fragment':'spacerift:rift','attributes':['Position','UV0'],'samplers':[{'name':'SceneColor'},{'name':'InteriorColor'}],'uniforms':declarations},indent=2),encoding='utf-8')
    subprocess.run(['node',str(ROOT/'scripts/export_schema.mjs')],check=True)
    print('Generated WebGL2, Shadertoy and GLSL150 Forge from shared core.')
if __name__=='__main__':build()
