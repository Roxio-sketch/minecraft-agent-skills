/* Original world-volume workbench. No external packages or image textures. */
import {makeVortexNoise,uploadVortexNoise} from './vortex-noise.js';
const $ = id => document.getElementById(id);
const clone = value => JSON.parse(JSON.stringify(value));
let schema, contract, presets, recipe, vortexSchema;
let vortex={};const vortexUI=new Map();
function validateVortex(candidate){
  if(!candidate||typeof candidate!=='object'||Array.isArray(candidate)||Object.keys(candidate).length!==Object.keys(vortexSchema.fields).length)throw Error('必须提供完整涡旋参数');
  for(const [key,spec] of Object.entries(vortexSchema.fields)){const v=candidate[key];if(typeof v!=='number'||!Number.isFinite(v)||v<spec.min||v>spec.max||(key==='mode'&&!Number.isInteger(v)))throw Error(`无效涡旋参数 ${key}`);}
  if(candidate.topRadius<candidate.bottomRadius)throw Error('顶部半径不能小于底部半径');return clone(candidate);
}
function exportVortex(){return {schema:'vortex-cloud/1',recipe:clone(recipe),vortex:clone(vortex),preview:{time:elapsed,camera:clone(camera)}};}
function syncVortex(){for(const [key,inputs] of vortexUI){inputs.range.value=vortex[key];if(document.activeElement!==inputs.number)inputs.number.value=vortex[key];}document.querySelectorAll('[data-mode]').forEach(el=>el.classList.toggle('active',Number(el.dataset.mode)===vortex.mode));$('effectTitle').textContent=['龙卷风 · 体积云漏斗','龙吸水 · 水片与飞溅','水龙卷 · 旋转液态漏斗'][vortex.mode];}
function applyVortex(candidate){vortex=validateVortex(candidate);syncVortex();if(ready)draw();}
function importVortex(candidate){
  if(candidate?.schema!=='vortex-cloud/1')throw Error('需要完整 vortex-cloud/1 作品JSON');
  if(Object.keys(candidate).length!==4||Object.keys(candidate).some(k=>!['schema','recipe','vortex','preview'].includes(k)))throw Error('作品须包含 schema、recipe、vortex、preview 四个字段');
  const base=validateRecipe(candidate.recipe),v=validateVortex(candidate.vortex);if(base.parameters.shape!==0||base.parameters.densityModel!==0)throw Error('涡旋需要有限圆柱区域与独立漏斗密度');
  const a=candidate.preview;if(!a||Array.isArray(a)||Object.keys(a).length!==2||Object.keys(a).some(k=>!['time','camera'].includes(k))||!Number.isFinite(a.time)||a.time<0||a.time>86400)throw Error('预览时间须在 0 至 86400 秒之间');
  const c=a.camera;if(!c||Array.isArray(c)||Object.keys(c).length!==3||Object.keys(c).some(k=>!['position','yaw','pitch'].includes(k))||!Array.isArray(c.position)||c.position.length!==3||!c.position.every(Number.isFinite)||!Number.isFinite(c.yaw)||!Number.isFinite(c.pitch)||Math.abs(c.pitch)>1.5)throw Error('预览相机无效');
  return {base,v,preview:candidate.preview};
}
function buildVortexFields(){
  for(const [key,spec] of Object.entries(vortexSchema.fields)){
    if(key==='mode')continue;
    const row=document.createElement('div');row.className='field';const label=document.createElement('label');label.textContent=spec.label;label.htmlFor='vortex-'+key;
    const number=document.createElement('input');number.type='number';number.id='vortex-'+key;
    const range=document.createElement('input');range.type='range';range.setAttribute('aria-label',spec.label);
    for(const el of [number,range]){el.min=spec.min;el.max=spec.max;el.step=spec.step;}
    row.append(label,number,range);$('vortexFields').append(row);vortexUI.set(key,{number,range});
    for(const el of [number,range])el.addEventListener('input',()=>{try{applyVortex({...vortex,[key]:el.value===''?NaN:Number(el.value)});clearRecipeError();}catch(e){displayError(e.message);}});
  }
  syncVortex();
}

let legacyFieldNames=[], newFieldNames=[];
let gl, program, vao, canvas, noiseData, noiseTexture, ready = false, failed = false, lastRendered = false;
let paused = false, elapsed = 0, previousFrame = 0, lastPaint = 0, frameMs = 0;
let timeInputDirty = false;
let renderScale = 1, lastError = null, cameraView = 'outside';
const camera = {position: [230, 118, 260], yaw: -2.42, pitch: -0.17};
const keys = new Set();
const fieldsUI = new Map(), uniformLocations = new Map();
const pendingValues = new Map();
const rememberedGains = {emission: 2, mediumGain: 1, surfaceGain: 0.5};
const exportIds = ['jsonButton', 'pngButton', 'zipButton'];
const endUniformNames = new Set(['EndWarp','EndCarve','EndDepth']);
const fieldCount = () => Object.keys(schema.fields).length;

const vertexSource = `#version 300 es
precision highp float;
const vec2 vertices[3] = vec2[3](vec2(-1.,-1.),vec2(3.,-1.),vec2(-1.,3.));
void main(){gl_Position=vec4(vertices[gl_VertexID],0.,1.);}`;

const sceneSource = `
uniform vec2 Resolution;
uniform vec3 CameraPosition;
uniform vec3 CameraForward;
uniform vec3 CameraRight;
uniform vec3 CameraUp;
uniform vec4 WaterDrops[96];
out vec4 fragColor;
struct SceneHit { float distance; vec3 normal; vec3 color; };
void sceneBox(vec3 ro, vec3 rd, vec3 low, vec3 high, vec3 color, inout SceneHit hit) {
    vec3 safeDir = mix(vec3(0.000001), rd, greaterThan(abs(rd),vec3(0.000001)));
    vec3 a=(low-ro)/safeDir, b=(high-ro)/safeDir;
    vec3 nearV=min(a,b), farV=max(a,b);
    float tn=max(max(nearV.x,nearV.y),nearV.z);
    float tf=min(min(farV.x,farV.y),farV.z);
    if(tf<max(tn,0.0)) return;
    float t=tn>0.001?tn:tf;
    if(t<0.001 || t>=hit.distance) return;
    vec3 p=ro+rd*t, center=(low+high)*0.5, halfSize=(high-low)*0.5;
    vec3 boundary=abs((p-center)/halfSize);
    vec3 n=vec3(0.0);
    if(boundary.x>boundary.y && boundary.x>boundary.z) n.x=sign(p.x-center.x);
    else if(boundary.y>boundary.z) n.y=sign(p.y-center.y);
    else n.z=sign(p.z-center.z);
    hit=SceneHit(t,n,color);
}
void sceneIsland(vec3 ro,vec3 rd,vec3 center,vec2 size,inout SceneHit hit) {
    vec3 sand=vec3(0.39,0.37,0.235);
    sceneBox(ro,rd,center+vec3(-size.x,-8.0,-size.y),center+vec3(size.x,0.0,size.y),sand,hit);
    sceneBox(ro,rd,center+vec3(-size.x*0.76,-23.0,-size.y*0.74),center+vec3(size.x*0.76,-8.0,size.y*0.74),sand*0.89,hit);
    sceneBox(ro,rd,center+vec3(-size.x*0.49,-38.0,-size.y*0.46),center+vec3(size.x*0.49,-23.0,size.y*0.46),sand*0.81,hit);
    sceneBox(ro,rd,center+vec3(-size.x*0.20,-49.0,-size.y*0.18),center+vec3(size.x*0.20,-38.0,size.y*0.18),sand*0.73,hit);
}
SceneHit traceEndScene(vec3 ro,vec3 rd) {
    SceneHit hit=SceneHit(10000.0,vec3(0.0,1.0,0.0),vec3(0.0));
    vec3 offset=vec3(Bounds.x,0.0,Bounds.z);
    sceneIsland(ro,rd,offset+vec3(0.0,65.0,0.0),vec2(57.0,48.0),hit);
    sceneIsland(ro,rd,offset+vec3(-145.0,88.0,-135.0),vec2(34.0,25.0),hit);
    sceneIsland(ro,rd,offset+vec3(162.0,44.0,-92.0),vec2(44.0,31.0),hit);
    sceneIsland(ro,rd,offset+vec3(-172.0,52.0,175.0),vec2(31.0,25.0),hit);
    sceneIsland(ro,rd,offset+vec3(118.0,100.0,165.0),vec2(27.0,23.0),hit);
    sceneBox(ro,rd,offset+vec3(-38.0,65.0,-23.0),offset+vec3(-31.0,142.0,-16.0),vec3(0.06,0.052,0.07),hit);
    sceneBox(ro,rd,offset+vec3(29.0,65.0,-12.0),offset+vec3(36.0,118.0,-5.0),vec3(0.06,0.052,0.07),hit);
    return hit;
}

vec3 sceneSky(vec3 rd);
float liquidMatureHeight(){return max(Layer.y*(VortexLife.w>1.5?.84:.55),4.0);}
float liquidWinding(){return VortexLife.w>1.5?9.0:7.5;}
float liquidWindingRate(){return liquidWinding()/liquidMatureHeight();}
float liquidPatternOmega(){return VortexFlow.x-liquidWindingRate()*VortexFlow.y;}
float vortexWaterHeight(vec2 pos){
  // Independent directional wave trains: broad displacement plus sharper crests.
  // These are an artistic height field, not horizontally displaced Gerstner/FFT waves.
  vec2 p=pos-Bounds.xz;float r=length(p);float theta=r>0.00001?atan(p.y,p.x):0.0;
  float t=Time*VortexWater.z;float gain=smoothstep(0.0,.4,vortexProgress());
  float phase0=dot(p,vec2(.038,.018))-t*.47+Seed*.017;
  float phase1=dot(p,vec2(.059,-.036))-t*.62+1.71;
  float phase2=dot(p,vec2(.107,.062))-t*.86+3.27;
  float phase3=dot(p,vec2(-.163,.13))-t*1.12+.53;
  float phase4=dot(p,vec2(.278,-.079))-t*1.43+2.11;
  float swell=.83*(sin(phase0)-.22*cos(phase0*2.0))
             +.40*(sin(phase1)-.22*cos(phase1*2.0))
             +.20*(sin(phase2)-.18*cos(phase2*2.0))
             +.12*sin(phase3)+.055*sin(phase4);
  float ringR=max(VortexRing.x,1.0);float inner=exp(-pow(r/(ringR*.62),2.0));
  float centerFade=smoothstep(0.0,5.0,r);
  float warp=sin(p.x*.055-Time*.19)+sin(p.y*.073+Time*.13);
  float spiralPhase=theta*2.0+r*.11-Time*liquidPatternOmega()*2.0+warp*.34;
  float spiral=sin(spiralPhase)*exp(-r/(ringR*2.6))*centerFade;
  // The disturbance is an uneven, broken shoulder with spiral ridges, not a white ring.
  float rimRadius=ringR*(1.0+.17*sin(theta*3.0+Time*.11)+.11*cos(theta*5.0-Time*.08));
  float rimDistance=(r-rimRadius)/max(VortexRing.y*1.2,1.0);
  float rim=exp(-rimDistance*rimDistance);
  rim*=centerFade*(.25+.75*pow(.5+.5*sin(spiralPhase+1.5),2.0));
  float chop=sin(dot(p,vec2(.12,.21))-t*.79+warp*.5)*exp(-r/(ringR*3.8))*centerFade;
  float rootRadius=max(VortexShape.x*(VortexLife.w>1.5?3.35:3.0),9.0);
  float shoulderOffset=(r-rootRadius*.83)/max(rootRadius*.30,2.0);
  float shoulder=exp(-shoulderOffset*shoulderOffset);
  float drawnShoulder=shoulder*(.70+.30*sin(spiralPhase))*(1.8+VortexLife.y*3.5)*centerFade*smoothstep(0.0,.2,VortexWater.y);
  return Layer.x+VortexWater.y*swell+gain*(-VortexWater.x*inner+drawnShoulder+VortexWater.y*(spiral*.46+rim*.26+chop*.30));
}
vec2 vortexWaterSurfaceVelocity(vec2 p){
  float r=length(p),ringR=max(VortexRing.x,1.0);
  vec2 radial=p/max(r,.001),tangent=vec2(-radial.y,radial.x);
  float nearFlow=exp(-r/(ringR*3.0))*smoothstep(0.0,3.0,r);
  float gain=smoothstep(0.0,.4,vortexProgress());
  return vec2(.90,-.34)*VortexWater.z*1.6
       +gain*nearFlow*(tangent*VortexFlow.x*min(r,80.0)*.65-radial*VortexLife.y*12.0);
}
vec4 waterSurfaceFlowData(vec3 p,vec3 velocity,float frequency){
  // Original flow field, sampled through two short, offset transport phases.
  // A resetting phase has zero weight. Its displacement never accumulates.
  float speed=length(velocity),period=2.8;
  vec3 direction=velocity/max(speed,.001);
  vec3 stretched=p-direction*dot(p,direction)*(.68*smoothstep(.5,3.0,speed));
  vec3 coordinate=stretched*frequency+vec3(Seed*.017,.31,.73);
  vec3 transport=velocity*frequency;
  transport*=min(1.0,.32/max(length(transport)*period,.0001));
  float offset=textureLod(VortexNoiseTex,p*.007+vec3(.17,Seed*.013,.41),0.0).a*.83;
  float clock=Time/period*step(.0001,speed)+offset;
  float phase0=fract(clock),phase1=fract(clock+.5);
  float weight0=1.0-abs(phase0*2.0-1.0);
  vec4 first=textureLod(VortexNoiseTex,coordinate-transport*((phase0-.5)*period),0.0);
  vec4 second=textureLod(VortexNoiseTex,coordinate+vec3(.41,.73,.19)-transport*((phase1-.5)*period),0.0);
  return mix(second,first,weight0);
}
vec2 vortexWaterMicroSlope(vec2 pos,float distance){
  // Sub-pixel wave lengths fade smoothly; unresolved waves become rough reflection.
  vec2 p=pos-Bounds.xz;float t=Time*VortexWater.z;
  float detailFade=1.0-smoothstep(120.0,650.0,distance);
  float a=dot(p,vec2(.62,.27))-t*2.31;
  float b=dot(p,vec2(-.44,.82))-t*2.87+2.13;
  float c=dot(p,vec2(1.28,-.38))-t*3.56+.78;
  vec2 velocity=vortexWaterSurfaceVelocity(p),along=velocity/max(length(velocity),.001);
  vec2 across=vec2(-along.y,along.x);
  vec4 flow=waterSurfaceFlowData(vec3(p.x,0.0,p.y),vec3(velocity.x,0.0,velocity.y),.095);
  vec2 transported=across*(flow.g-.5)*.42+along*(flow.b-.5)*.16;
  return VortexWater.y*detailFade*(vec2(.62,.27)*cos(a)*.12+vec2(-.44,.82)*cos(b)*.075+vec2(1.28,-.38)*cos(c)*.032+transported);
}
vec3 vortexWaterReflection(vec3 rd,vec3 p){
  // Procedural overcast environment; no claim of reflecting actual scene geometry.
  vec3 base=sceneSky(rd)*vec3(.74,.83,.82);
  vec3 cp=rd*6.4+vec3(Time*.019,0.0,Time*.008)+vec3(Seed*.013);
  float broad=textureLod(VortexNoiseTex,cp*.11+vec3(.17,.53,.29),0.0).a;
  float fine=textureLod(VortexNoiseTex,cp*.18+vec3(.73,.21,.41),0.0).g;
  float cloud=smoothstep(.26,.68,broad+.1*fine);
  float horizon=pow(max(1.0-abs(rd.y),0.0),4.0);
  vec3 cloudy=mix(vec3(.075,.096,.105),vec3(.025,.037,.046),cloud);
  vec3 reflected=mix(base,cloudy,.83)+vec3(.019,.024,.025)*horizon;
  // A broad dark under-cloud region follows the world-space storm position.
  float cover=exp(-dot(p.xz-Bounds.xz,p.xz-Bounds.xz)/max(Bounds.w*Bounds.w*2.0,1.0));
  return reflected*(1.0-cover*.20*smoothstep(0.0,.6,vortexProgress()));
}
float vortexWaterFoam(vec2 pos,float height,vec2 slope){
  vec2 p=pos-Bounds.xz;float r=length(p);
  float gain=smoothstep(0.0,.4,vortexProgress());float ringR=max(VortexRing.x,1.0);
  vec2 velocity=vortexWaterSurfaceVelocity(p);
  vec3 local=vec3(p.x,0.0,p.y),transport=vec3(velocity.x,0.0,velocity.y);
  vec4 broad=waterSurfaceFlowData(local,transport,.043),fine=waterSurfaceFlowData(local,transport,.13);
  float flecks=smoothstep(.56,.76,fine.g)*smoothstep(.40,.65,broad.a);
  float crest=smoothstep(.58,1.12,(height-Layer.x)/max(VortexWater.y,.01));
  crest*=smoothstep(.11,.32,length(slope));
  float shoulderDistance=(r-ringR*(.82+.35*broad.r))/max(VortexRing.y*1.7,1.0);
  float shoulder=exp(-shoulderDistance*shoulderDistance)*smoothstep(.38,.64,broad.r);
  float trails=smoothstep(.46,.67,broad.g)*exp(-r/(ringR*3.0));
  float drawing=smoothstep(.5,3.0,length(velocity))*exp(-r/(ringR*2.8));
  float localized=gain*smoothstep(0.0,5.0,r)*(shoulder*.48+trails*.42+drawing*.23);
  float amount=crest*.60+localized;
  return clamp(VortexWater.w*amount*flecks*1.4,0.0,.88);
}
SceneHit traceWater(vec3 ro,vec3 rd){
  SceneHit hit=SceneHit(10000.0,vec3(0,1,0),vec3(0));
  if(abs(rd.y)<1e-6)return hit;
  float range=VortexWater.x+VortexWater.y*3.3+2.0+VortexLife.y*3.5;
  vec2 ts=(vec2(Layer.x-range,Layer.x+range)-ro.y)/rd.y;
  float lo=max(min(ts.x,ts.y),.001),hi=min(max(ts.x,ts.y),2500.0);if(hi<=lo)return hit;
  float prev=lo;vec3 a=ro+rd*prev;float fv=a.y-vortexWaterHeight(a.xz);
  for(int i=1;i<=28;i++){
    float next=mix(lo,hi,float(i)/28.0);vec3 b=ro+rd*next;float fn=b.y-vortexWaterHeight(b.xz);
    if(fv*fn<=0.0){
      float l=prev,u=next,fl=fv;
      for(int j=0;j<8;j++){float m=(l+u)*.5;vec3 p=ro+rd*m;float fm=p.y-vortexWaterHeight(p.xz);if(fl*fm<=0.0)u=m;else{l=m;fl=fm;}}
      float distance=(l+u)*.5;vec3 p=ro+rd*distance;float e=.18;
      float dx=(vortexWaterHeight(p.xz+vec2(e,0))-vortexWaterHeight(p.xz-vec2(e,0)))/(2.0*e);
      float dz=(vortexWaterHeight(p.xz+vec2(0,e))-vortexWaterHeight(p.xz-vec2(0,e)))/(2.0*e);
      vec2 geometricSlope=vec2(dx,dz),slope=geometricSlope+vortexWaterMicroSlope(p.xz,distance);
      vec3 n=normalize(vec3(-slope.x,1,-slope.y));float foam=vortexWaterFoam(p.xz,p.y,geometricSlope);
      float ndv=max(dot(n,-rd),0.0),fresnel=.0204+.9796*pow(1.0-ndv,5.0);
      vec3 reflectionRay=reflect(rd,n);reflectionRay.y=max(reflectionRay.y,.018);
      vec3 reflected=vortexWaterReflection(normalize(reflectionRay),p);
      float spec=pow(max(dot(reflect(-normalize(SunDirection),n),-rd),0.0),38.0);
      float scatter=pow(clamp((p.y-Layer.x)/max(VortexWater.y,.01)*.27+.38,0.0,1.0),2.0);
      vec3 below=vec3(.004,.022,.026)+vec3(.008,.022,.020)*scatter;
      vec3 color=mix(below,reflected,fresnel)+SunColor*spec*.022;
      color+=vec3(.006,.010,.010)*(1.0-ndv)*scatter;
      color=mix(color,vec3(.24,.29,.28)*(1.0+.22*max(dot(n,normalize(SunDirection)),0.0)),foam);
      return SceneHit(distance,n,color);
    }
    prev=next;fv=fn;
  }
  return hit;
}

float vortexLiquidHeight(){
  return liquidMatureHeight()*smoothstep(.06,.88,vortexProgress());
}
float vortexLiquidRadius(float h){
  float base=max(VortexShape.x*1.9,6.0),neck=max(VortexShape.x*.68,2.4);
  if(VortexLife.w>1.5)return mix(max(VortexShape.x*1.65,5.0),VortexShape.y*.80,pow(clamp(h,0.,1.),VortexShape.z*.80))+max(VortexShape.x*1.7,5.0)*exp(-max(h,0.0)*19.0);
  return neck+(base-neck)*exp(-max(h,0.0)*3.0)+max(VortexShape.x*.9,3.0)*exp(-max(h,0.0)*20.0);
}
vec2 vortexLiquidAxis(float h){return vortexAxis(clamp(h,0.0,1.0)*.65);}
struct LiquidBasis {vec3 center;vec3 u;vec3 v;vec3 tangent;};
struct LiquidPoint {vec2 uv;float s;float h;vec3 u;vec3 v;vec3 tangent;};
vec3 liquidCurveParameter(float s){
  float mature=liquidMatureHeight();
  float q=clamp(s/mature,0.0,1.0)*.65;
  float dq=step(0.0,s)*step(s,mature)*.65/mature;
  float ddq=0.0;
  if(VortexLife.w>1.5){
    // The mouth extends beyond mature height. Continue both position and its
    // derivative smoothly there; abruptly setting dq=0 splits the liquid frame.
    float x=max(s/mature,0.0),e=exp(-8.0*max(x-1.0,0.0));
    float turn=(1.0-e)/(1.0+e),slope=1.0-turn*turn;
    q=(x<=1.0?x:1.0+.25*turn)*.65;
    dq=step(0.0,s)*.65/mature*slope;
    ddq=-8.0*.65/(mature*mature)*turn*slope;
  }
  return vec3(q,dq,ddq);
}
LiquidBasis liquidBasisAt(float s,float clock){
  // The transverse frame never crosses world-UP with a vertical path. Its
  // horizontal u axis stays well-defined even at zero bend or at the root.
  vec3 curve=liquidCurveParameter(s);float q=curve.x,dq=curve.y;
  float a=q*4.0+clock*.13,b=q*3.0+clock*.11;
  float bend=VortexFlow.z*sin(q*3.14159265);
  vec2 axis=bend*vec2(sin(a),cos(b));
  vec2 derivative=VortexFlow.z*dq*(3.14159265*cos(q*3.14159265)*vec2(sin(a),cos(b))+sin(q*3.14159265)*vec2(4.0*cos(a),-3.0*sin(b)));
  vec3 tangent=normalize(vec3(derivative.x,1.0,derivative.y));
  vec3 u=normalize(vec3(tangent.y,-tangent.x,0.0)),v=cross(u,tangent);
  return LiquidBasis(vec3(Bounds.x+axis.x,Layer.x+s,Bounds.z+axis.y),u,v,tangent);
}
vec3 liquidWorldFromLocal(float s,vec2 uv,float clock){
  LiquidBasis b=liquidBasisAt(s,clock);return b.center+b.u*uv.x+b.v*uv.y;
}
LiquidPoint liquidLocalPoint(vec3 world){
  // Two bounded projection corrections locate the bent centerline. The curve
  // is prescribed, not an integrated fluid path or a parallel-transport solve.
  float s=world.y-Layer.x;
  for(int i=0;i<2;i++){
    LiquidBasis b=liquidBasisAt(s,Time);
    s+=dot(world-b.center,b.tangent)*b.tangent.y;
  }
  LiquidBasis b=liquidBasisAt(s,Time);vec3 d=world-b.center;
  return LiquidPoint(vec2(dot(d,b.u),dot(d,b.v)),s,s/max(vortexLiquidHeight(),.01),b.u,b.v,b.tangent);
}
float liquidSmoothUnion(float a,float b,float k){
  float h=clamp(.5+.5*(b-a)/k,0.0,1.0);return mix(b,a,h)-k*h*(1.0-h);
}
float liquidPhaseAt(LiquidPoint p){
  float theta=length(p.uv)>.0001?atan(p.uv.y,p.uv.x):0.0;
  // dtheta/dt=rotation and ds/dt=rise preserve this phase in the moving frame.
  // Fixed mature-height pitch avoids changing winding speed during formation.
  return theta-liquidWindingRate()*p.s-liquidPatternOmega()*Time;
}
float liquidSpiralPhase(vec3 world){return liquidPhaseAt(liquidLocalPoint(world));}
vec4 liquidFlowAt(LiquidPoint p,float frequency){
  float phase=liquidPhaseAt(p),r=length(p.uv);
  // sin/cos embedding is periodic through the atan branch; no angular seam.
  vec3 q=vec3(r*cos(phase),(p.s-Time*VortexFlow.y)*.74,r*sin(phase));
  return textureLod(VortexNoiseTex,q*frequency+vec3(Seed*.017,.31,.73),0.0);
}
vec4 liquidFlowData(vec3 world,float frequency){return liquidFlowAt(liquidLocalPoint(world),frequency);}
float liquidCrestAt(LiquidPoint p,vec4 broad){return liquidPhaseAt(p)+.24*(broad.a-.5);}
float liquidCrestPhase(vec3 world){LiquidPoint p=liquidLocalPoint(world);return liquidCrestAt(p,liquidFlowAt(p,.016));}
vec4 liquidBowlProfileAt(LiquidPoint p){
  // A concave open mouth has increasing radius all the way to the rim. It is
  // a thin liquid interface, not a solid cone with a rounded closing cap.
  float phase=liquidPhaseAt(p),height=max(vortexLiquidHeight(),.01);
  vec4 broad=liquidFlowAt(p,.016);
  float lipH=1.035+.018*sin(phase*2.0+.6)+.011*cos(phase*3.0-1.1);
  float neck=vortexLiquidRadius(.56)*.76;
  float lipR=max(VortexShape.y*.96,neck+7.0);
  lipR*=1.0+.045*cos(phase*2.0)+.026*sin(phase*3.0+.7)+.016*(broad.r-.5)*VortexFlow.w;
  float q=clamp((p.h-.56)/max(lipH-.56,.1),0.0,1.0);
  float radius=mix(neck,lipR,pow(q,2.15));
  float slope=(lipR-neck)*2.15*pow(max(q,.0001),1.15)/(height*max(lipH-.56,.1));
  return vec4(radius,lipH,slope,lipR);
}
float vortexLiquidDistance(vec3 world){
  if(VortexLife.w<.5||vortexProgress()<=.06)return 10000.0;
  LiquidPoint p=liquidLocalPoint(world);float height=vortexLiquidHeight(),h=max(p.h,0.0),radius=vortexLiquidRadius(h);
  vec4 broad=liquidFlowAt(p,.016),fold=liquidFlowAt(p,.046);
  float amp=clamp(VortexFlow.w,0.0,2.0),r=length(p.uv),phase=liquidCrestAt(p,broad);
  float crest=smoothstep(-.48,.74,cos(phase));
  // A smaller finite carrier stays connected in every trough. Thin outer skins
  // carry the visible folds; angular masks only trim those auxiliary films.
  float crestShape=.34+.045*crest+.012*(broad.r-.5)*amp+.003*(fold.g-.5)*amp;
  float tip=.28+.08*cos(phase*3.0+fold.a*2.0)+.06*(broad.a-.5);
  float topPosition=max(h-.75,0.0)/tip;
  float envelope=sqrt(max(1.0-topPosition*topPosition,0.0));
  float coreRadius=radius*crestShape*envelope;
  float body=r-coreRadius;
  float topBeyond=(h-(.75+tip))*height;
  body=max(body,topBeyond+(topPosition>1.0?.18:0.0));
  // Smaller carrier; visible water is several overlapping thin, torn skins.
  // Skin cutouts preserve the lower carrier. Mode2 opens its mouth with one
  // shared cavity after all auxiliary layers have been combined.
  float liquid=body;
  for(int skin=0;skin<3;skin++){
    float j=float(skin),angle=phase+j*1.94+(fold.a-.5)*.65;
    float wrinkled=sin(angle*3.0+(p.s-Time*VortexFlow.y)*.033+fold.r*1.2);
    float shoulder=smoothstep(-.30,.75,cos(angle));
    float skinTip=tip+.025*j+.028*sin(angle*2.0);
    float skinTop=max(h-.76,0.0)/skinTip;
    float skinEnvelope=sqrt(max(1.0-skinTop*skinTop,0.0));
    float skinR=radius*(crestShape+.30+j*.095+.072*shoulder+.019*wrinkled+.009*(fold.g-.5)*amp)*skinEnvelope;
    float thickness=.32+.20*broad.a+.06*j;
    float sheet=abs(r-skinR)-thickness;
    float torn=cos(angle)+.31*(fold.b-.5)+.12*sin(angle*3.0+(p.s-Time*VortexFlow.y)*.035);
    sheet=max(sheet,(-.25-torn)*2.6);
    sheet=max(sheet,(h-(.76+skinTip))*height);
    liquid=liquidSmoothUnion(liquid,sheet,.24);
  }
  // Three neighboring finite wide fans overlap the mass. They are not sparse
  // vertical ribbons and cannot delete the occupied core beneath them.
  float advectedS=p.s-Time*VortexFlow.y,cellSize=39.0,slot=floor(advectedS/cellSize);
  float basePhase=liquidPhaseAt(p),angle=basePhase-(r>.0001?atan(p.uv.y,p.uv.x):0.0);
  vec2 q=vec2(cos(angle)*p.uv.x-sin(angle)*p.uv.y,sin(angle)*p.uv.x+cos(angle)*p.uv.y);
  for(int j=-1;j<=1;j++){
    float cell=slot+float(j),a=veHash(vec3(cell,Seed,31.0)),b=veHash(vec3(cell,Seed,67.0));
    float cy=(cell+.35+b*.3)*cellSize+Time*VortexFlow.y,dy=p.s-cy;
    vec2 direction=vec2(cos(a*6.2831853),sin(a*6.2831853)),side=vec2(-direction.y,direction.x);
    float x=dot(q,side),z=dot(q,direction)-coreRadius*1.1-radius*.12;
    float width=max(radius*(.65+b*.25)*envelope,2.0),lengthS=cellSize*(.55+a*.18);
    float foldOffset=sin(dy*.13+b*5.0)*1.6+x*x/max(width*2.5,1.0)*(.45+b*.32);
    float film=abs(z-foldOffset)-(.34+b*.36);
    float outline=(length(vec2(x/width,dy/lengthS))-1.0)*min(width,lengthS);
    film=max(film,outline);
    film=max(film,(.29-fold.g)*1.4*smoothstep(.25,1.0,h));
    film=max(film,topBeyond-.9);
    liquid=liquidSmoothUnion(liquid,film,.28);
  }
  if(VortexLife.w>1.5){
    vec4 bowl=liquidBowlProfileAt(p);
    float wallThickness=.72+.18*broad.a+.08*crest;
    float wall=abs(r-bowl.x)/sqrt(1.0+bowl.z*bowl.z)-wallThickness;
    wall=max(wall,(.56-h)*height);
    wall=max(wall,(h-bowl.y)*height);
    // The rolling lip stays joined around the mouth; low-frequency radius and
    // height variation avoid a rigid perfect torus or serrated crown.
    float lipThickness=.90+.15*broad.a+.12*crest;
    float lip=length(vec2(r-bowl.w,p.s-bowl.y*height))-lipThickness;
    liquid=liquidSmoothUnion(liquid,min(wall,lip),.30);
    // All pre-existing skins/fans share the same upper cavity. The lower
    // occupied kernel remains, while its tip cannot plug the bowl opening.
    float inner=max(bowl.x-(wallThickness+1.1)*sqrt(1.0+bowl.z*bowl.z),0.0);
    float cavityR=inner*smoothstep(.57,.76,h);
    if(h>.57)liquid=max(liquid,(cavityR-r)/sqrt(1.0+bowl.z*bowl.z));
    // Remove old detached cap pieces beyond the rolling mouth.
    liquid=max(liquid,(h-bowl.y)*height-lipThickness);
  }
  // Submerged support is hidden by the actual sea intersection. There is no
  // water-height clipping plane that can leave a flat exposed cylinder root.
  return max(liquid,-p.s-(VortexWater.x+VortexWater.y*3.3+2.0));
}
vec3 vortexLiquidNormal(vec3 p){
  float e=.14;
  vec3 a=vec3(1,-1,-1),b=vec3(-1,-1,1),c=vec3(-1,1,-1),d=vec3(1,1,1);
  vec3 gradient=a*vortexLiquidDistance(p+a*e)+b*vortexLiquidDistance(p+b*e)+c*vortexLiquidDistance(p+c*e)+d*vortexLiquidDistance(p+d*e);
  return normalize(gradient+vec3(0.0,.000001,0.0));
}
vec3 liquidSurfaceVelocityAt(LiquidPoint p){
  // Analytic frame derivatives add center/frame motion before local rotation
  // and rise. Reusing this frame avoids five extra projection/basis evaluations.
  vec3 curve=liquidCurveParameter(p.s);float q=curve.x,dq=curve.y;
  float Q=q*3.14159265,a=q*4.0+Time*.13,b=q*3.0+Time*.11;
  float sn=sin(Q),cs=cos(Q),sa=sin(a),ca=cos(a),sb=sin(b),cb=cos(b),B=VortexFlow.z;
  vec2 axisS=B*dq*vec2(3.14159265*cs*sa+4.0*sn*ca,3.14159265*cs*cb-3.0*sn*sb);
  vec2 axisSS=B*dq*dq*vec2(-(9.8696044+16.0)*sn*sa+8.0*3.14159265*cs*ca,-(9.8696044+9.0)*sn*cb-6.0*3.14159265*cs*sb);
  axisSS+=B*curve.z*vec2(3.14159265*cs*sa+4.0*sn*ca,3.14159265*cs*cb-3.0*sn*sb);
  vec2 axisST=B*dq*vec2((3.14159265*cs*ca-4.0*sn*sa)*.13,(-3.14159265*cs*sb-3.0*sn*cb)*.11);
  vec3 D=vec3(axisS.x,1.0,axisS.y),Ds=vec3(axisSS.x,0.0,axisSS.y),Dt=vec3(axisST.x,0.0,axisST.y);
  vec3 Ts=(Ds-p.tangent*dot(p.tangent,Ds))/length(D),Tt=(Dt-p.tangent*dot(p.tangent,Dt))/length(D);
  float normU=sqrt(1.0+axisS.x*axisS.x);
  vec3 Us=(vec3(0.0,-axisSS.x,0.0)+p.u*p.u.y*axisSS.x)/normU;
  vec3 Ut=(vec3(0.0,-axisST.x,0.0)+p.u*p.u.y*axisST.x)/normU;
  vec3 Vs=cross(Us,p.tangent)+cross(p.u,Ts),Vt=cross(Ut,p.tangent)+cross(p.u,Tt);
  vec3 drift=vec3(B*sn*ca*.13,0.0,-B*sn*sb*.11)+Ut*p.uv.x+Vt*p.uv.y;
  vec3 rise=(D+Us*p.uv.x+Vs*p.uv.y)*VortexFlow.y;
  vec3 turning=(p.u*(-p.uv.y)+p.v*p.uv.x)*VortexFlow.x;
  vec3 radial=p.u*p.uv.x+p.v*p.uv.y;
  float inflow=VortexLife.y*3.0*exp(-max(p.h,0.0)*10.0);
  return drift+rise+turning-radial/max(length(radial),.001)*inflow;
}
vec3 liquidSurfaceVelocity(vec3 world){return liquidSurfaceVelocityAt(liquidLocalPoint(world));}
vec4 liquidSurfaceFlowData(vec3 p,float frequency){return liquidFlowData(p,frequency);}
float liquidSpiralMaterialPhase(vec3 p,vec4 flow,vec4 fine){return liquidCrestPhase(p);}
vec3 liquidSpiralAcrossAt(LiquidPoint p,vec3 n){
  vec3 direction=(p.u*(-p.uv.y)+p.v*p.uv.x)/max(dot(p.uv,p.uv),1.0)-p.tangent*liquidWindingRate();
  direction-=n*dot(direction,n);return direction/max(length(direction),.001);
}
vec3 liquidSpiralAcross(vec3 world,vec3 n){return liquidSpiralAcrossAt(liquidLocalPoint(world),n);}
float liquidFrothAt(LiquidPoint local,vec3 n){
  vec4 flow=liquidFlowAt(local,.032),fine=liquidFlowAt(local,.13);
  vec4 broad=liquidFlowAt(local,.016),fold=liquidFlowAt(local,.046);
  float phase=liquidCrestAt(local,broad);
  float radius=vortexLiquidRadius(max(local.h,0.0));
  float exposed=smoothstep(.48,.65,length(local.uv)/max(radius,.01));
  float edge=0.0;
  for(int skin=0;skin<3;skin++){
    float angle=phase+float(skin)*1.94+(fold.a-.5)*.65;
    float torn=cos(angle)+.31*(fold.b-.5)+.12*sin(angle*3.0+(local.s-Time*VortexFlow.y)*.035);
    float edgeOffset=(torn+.25)/.095;
    edge=max(edge,exp(-edgeOffset*edgeOffset));
  }
  // Aeration breaks into transported patches. A helical crest alone must not
  // paint a broad white band around the whole liquid carrier.
  float coverage=smoothstep(.51,.72,flow.a)*smoothstep(.43,.70,fine.r);
  float breakage=smoothstep(.32,.72,abs(n.y))*smoothstep(.49,.72,flow.r)*smoothstep(.40,.67,fine.g);
  float root=exp(-max(local.h,0.0)*18.0)*smoothstep(.46,.66,fine.a);
  float foamPatch=(edge*coverage*.82+breakage*.19)*exposed+root*.38;
  if(VortexLife.w>1.5){
    vec4 bowl=liquidBowlProfileAt(local);
    float lipOffset=(local.h-bowl.y)*max(vortexLiquidHeight(),1.0)/2.4;
    float lipBand=exp(-lipOffset*lipOffset);
    foamPatch=mix(foamPatch,breakage*.06+lipBand*coverage*.60,smoothstep(.60,.78,local.h));
  }
  return clamp(foamPatch*VortexWater.w*1.35,0.0,.68);
}
float liquidFroth(vec3 p,vec3 n){return liquidFrothAt(liquidLocalPoint(p),n);}
struct LiquidOptics {vec3 normal;float fresnel;float foam;};
vec4 liquidFilmData(LiquidPoint local,float frequency){
  float phase=liquidPhaseAt(local),r=length(local.uv);
  // Fine water striations are elongated along the transported sheet. Cloud
  // cellular detail must not turn the whole liquid into isotropic scales.
  vec3 q=vec3(r*cos(phase),(local.s-Time*VortexFlow.y)*.15,r*sin(phase));
  return textureLod(VortexNoiseTex,q*frequency+vec3(Seed*.017,.31,.73),0.0);
}
LiquidOptics liquidOpticsAt(vec3 p,vec3 n,vec3 rd){
  LiquidPoint local=liquidLocalPoint(p);
  vec4 flow=liquidFilmData(local,.044),micro=liquidFilmData(local,.12),ripples=liquidFilmData(local,.29);
  vec3 velocity=liquidSurfaceVelocityAt(local),projected=velocity-n*dot(velocity,n);
  vec3 along=projected/max(length(projected),.001),across=cross(n,along);across/=max(length(across),.001);
  float phase=liquidCrestAt(local,liquidFlowAt(local,.016)),strength=.12+.09*smoothstep(.5,8.0,length(velocity));
  float mouth=VortexLife.w>1.5?smoothstep(.62,.84,local.h):0.0;
  strength*=mix(1.0,.40,mouth);
  float windingSlope=sin(phase*6.0+flow.g*4.0)*.032+sin(phase*13.0+micro.r*5.0)*.014;
  vec3 fineN=normalize(n+liquidSpiralAcrossAt(local,n)*windingSlope
    +across*((flow.g-.5)*strength+(ripples.a-.5)*mix(.08,.025,mouth))
    +along*((micro.b-.5)*strength*.28+(ripples.r-.5)*mix(.045,.015,mouth)));
  float ndv=clamp(abs(dot(fineN,-rd)),0.0,1.0),F=.0204+.9796*pow(1.0-ndv,5.0);
  return LiquidOptics(fineN,F,liquidFrothAt(local,n));
}
vec3 vortexLiquidColor(vec3 p,vec3 n,vec3 rd){
  LiquidOptics optical=liquidOpticsAt(p,n,rd);
  vec3 direction=reflect(rd,optical.normal);
  vec3 reflected=vortexWaterReflection(direction,p);
  // Downward-facing column facets see the dark sea rather than the sky.
  vec4 ripple=textureLod(VortexNoiseTex,vec3(direction.x*2.4+Time*.016,.27,direction.z*2.4),0.0);
  vec3 sea=vec3(.007,.014,.016)+vec3(.020,.027,.029)*smoothstep(.40,.72,ripple.g);
  reflected=mix(reflected,sea,smoothstep(.02,.16,-direction.y));
  float key=pow(max(dot(reflect(-normalize(SunDirection),optical.normal),-rd),0.0),76.0);
  vec3 water=reflected*optical.fresnel+SunColor*key*.075;
  vec3 foam=vec3(.28,.31,.32)*(.65+.35*max(dot(n,normalize(SunDirection)),0.0));
  // Reflection/foam only; the same interface's transmission is composed below.
  return water*(1.0-optical.foam)+foam*optical.foam;
}
SceneHit traceVortexDrops(vec3 ro,vec3 rd,float limit){
  SceneHit hit=SceneHit(10000.0,vec3(0,1,0),vec3(0));
  if(VortexLife.w<.5||vortexProgress()<=.06)return hit;
  for(int i=0;i<96;i++){
    if(i>=min(max(ViewSteps,32),96))break;
    vec4 drop=WaterDrops[i];if(drop.w<=.001)continue;
    vec3 size=vec3(drop.w,drop.w*1.25,drop.w),o=(ro-drop.xyz)/size,d=rd/size;
    float a=dot(d,d),b=dot(o,d),c=dot(o,o)-1.0,disc=b*b-a*c;
    if(disc<0.0)continue;
    float t=(-b-sqrt(disc))/a;if(t<.001)t=(-b+sqrt(disc))/a;
    if(t<.001||t>=min(limit,hit.distance))continue;
    vec3 p=ro+rd*t,n=normalize((p-drop.xyz)/(size*size));
    LiquidOptics optical=liquidOpticsAt(p,n,rd);
    vec3 transmitted=refract(rd,dot(n,rd)<0.0?n:-n,.75019);
    vec3 background=mix(sceneSky(transmitted),vec3(.015,.027,.030),smoothstep(.0,.3,-transmitted.y));
    vec3 water=vortexLiquidColor(p,n,rd)+background*exp(-vec3(.022,.016,.013)*drop.w*1.5)*(1.0-optical.fresnel)*(1.0-optical.foam);
    hit=SceneHit(t,n,water);
  }
  return hit;
}
SceneHit traceVortexLiquid(vec3 ro,vec3 rd,float limit){
  SceneHit hit=SceneHit(10000.0,vec3(0,1,0),vec3(0));
  if(VortexLife.w<.5||vortexProgress()<=.06)return hit;
  float reach=max(max(VortexShape.x*4.0,VortexLife.w>1.5?VortexShape.y*1.4:0.0)+abs(VortexFlow.z)+7.0,12.0);
  vec3 low=vec3(Bounds.x-reach,Layer.x-VortexWater.x-VortexWater.y*3.3-.2,Bounds.z-reach);
  vec3 high=vec3(Bounds.x+reach,Layer.x+vortexLiquidHeight()*1.2+35.0,Bounds.z+reach);
  vec3 safeDir=mix(vec3(.000001),rd,greaterThan(abs(rd),vec3(.000001)));
  vec3 a=(low-ro)/safeDir,b=(high-ro)/safeDir,nearV=min(a,b),farV=max(a,b);
  float nearT=max(max(nearV.x,nearV.y),nearV.z),farT=min(min(farV.x,farV.y),farV.z);
  nearT=max(nearT,.001);farT=min(farT,min(limit,2500.0));
  hit=traceVortexDrops(ro,rd,min(limit,2500.0));if(farT<=nearT)return hit;
  farT=min(farT,hit.distance);
  float t=nearT,previousT=t,previousD=vortexLiquidDistance(ro+rd*t);
  float gradientBound=2.2+VortexFlow.w*.65+max(VortexShape.x*4.0,VortexShape.y)*liquidWindingRate()*.65;
  int raySteps=max(ViewSteps,48)*(VortexLife.w>1.5?2:1);
  for(int i=0;i<192;i++){
    if(i>=raySteps||t>farT)break;
    vec3 p=ro+rd*t;float d=vortexLiquidDistance(p);
    if(d*previousD<0.0){
      float lo=previousT,hi=t,dl=previousD;
      for(int j=0;j<12;j++){if(j>=(VortexLife.w>1.5?12:7))break;float mid=(lo+hi)*.5,dm=vortexLiquidDistance(ro+rd*mid);if(dm*dl<=0.0)hi=mid;else{lo=mid;dl=dm;}}
      float surfaceT=(lo+hi)*.5;vec3 surface=ro+rd*surfaceT,n=vortexLiquidNormal(surface);
      return SceneHit(surfaceT,n,vortexLiquidColor(surface,n,rd));
    }
    if(abs(d)<.075){vec3 n=vortexLiquidNormal(p);return SceneHit(t,n,vortexLiquidColor(p,n,rd));}
    previousT=t;previousD=d;
    // This is a bounded implicit field rather than an exact SDF. Conservative
    // steps plus a sign-change bracket retain entrances/exits of folded films.
    float bound=VortexLife.w>1.5&&liquidLocalPoint(p).h>.60?2.4:gradientBound;
    t+=clamp(abs(d)/bound,.024,6.0);
  }
  return hit;
}

SceneHit traceScene(vec3 ro,vec3 rd) {
    if(VortexLife.w>.5){
        SceneHit water=traceWater(ro,rd),liquid=traceVortexLiquid(ro,rd,water.distance);
        if(liquid.distance<water.distance)return liquid;
        return water;
    }
    if(EndWarp.x>0.5)return traceEndScene(ro,rd);
    SceneHit hit=SceneHit(10000.0,vec3(0.0,1.0,0.0),vec3(0.0));
    if(abs(rd.y)>0.000001) {
        float groundT=-ro.y/rd.y;
        if(groundT>0.001 && groundT<hit.distance) {
            vec3 p=ro+rd*groundT;
            float rough=textureLod(VortexNoiseTex,vec3(p.x*.009,.3,p.z*.009),0.0).a;
            float fine=textureLod(VortexNoiseTex,vec3(p.x*.071,.4,p.z*.071),0.0).b;
            vec3 grass=mix(vec3(.028,.035,.021),vec3(.075,.078,.048),rough)*( .85+.30*fine);
            hit=SceneHit(groundT,vec3(0.0,1.0,0.0),grass);
        }
    }
    vec3 offset=vec3(Bounds.x,0.0,Bounds.z);
    sceneBox(ro,rd,offset+vec3(-115.,0.,18.),offset+vec3(-90.,17.,41.),vec3(.10,.105,.095),hit);
    sceneBox(ro,rd,offset+vec3(-108.,17.,23.),offset+vec3(-96.,30.,35.),vec3(.12,.12,.11),hit);
    sceneBox(ro,rd,offset+vec3(85.,0.,-90.),offset+vec3(115.,21.,-69.),vec3(.11,.12,.105),hit);
    sceneBox(ro,rd,offset+vec3(125.,0.,52.),offset+vec3(146.,12.,72.),vec3(.09,.105,.085),hit);
    return hit;
}
vec3 sceneSky(vec3 rd) {
    if(EndWarp.x>0.5){
        float h=clamp(rd.y*0.7+0.3,0.0,1.0);
        return mix(vec3(0.013,0.008,0.026),vec3(0.0025,0.0015,0.007),h);
    }
    float h=clamp(rd.y*1.1+.13,0.0,1.0);
    vec3 sky=mix(vec3(.125,.155,.17),vec3(.027,.038,.053),h);
    float overcast=textureLod(VortexNoiseTex,vec3(rd.x*.8,rd.y*.45,rd.z*.8)+vec3(.31,.7,.29),0.0).r;
    sky*=.75+.40*overcast;
    vec3 sun=normalize(SunDirection);
    float disc=smoothstep(0.9986,0.9997,dot(rd,sun));
    float haze=pow(max(dot(rd,sun),0.0),24.0);
    return sky+SunColor*(disc*.5+haze*.014)*min(Optics.w,2.0);
}
vec3 tonemap(vec3 x) {
    x=max(x,vec3(0.0));
    vec3 mapped=(x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14);
    return pow(clamp(mapped,0.0,1.0),vec3(1.0/2.2));
}
vec4 vortexLiquidBackdrop(vec3 origin,vec3 direction,float limit){
    // Background seen through a small liquid interface uses a short ambient
    // integration. Keep the main front-cloud lighting pass independent.
    vec2 span;if(!veInterval(origin,direction,limit,span))return vec4(0,0,0,1);
    float ds=(span.y-span.x)/12.0,T=1.0;vec3 L=vec3(0);
    vec3 source=MediumColor*(vec3(Optics.z*.22)+SunColor*Optics.w*.075);
    for(int i=0;i<12;i++){
        vec3 p=origin+direction*(span.x+(float(i)+.5)*ds);
        float stepT=exp(-veDensity(p)*Optics.x*ds);
        L+=T*source*(1.0-stepT);T*=stepT;
    }
    return vec4(L,T);
}
float liquidOpticalThickness(vec3 p,vec3 direction){
    float h=clamp((p.y-Layer.x)/max(vortexLiquidHeight(),.01),0.0,1.0);
    float span=vortexLiquidRadius(h)*2.5+12.0;
    float ds=span/24.0,path=0.0;
    for(int i=0;i<24;i++){
        float d=vortexLiquidDistance(p+direction*((float(i)+.5)*ds));
        path+=(1.0-smoothstep(-.15,.15,d))*ds;
    }
    return path;
}
void main() {
    vec2 uv=(gl_FragCoord.xy/Resolution*2.0-1.0);
    uv.x*=Resolution.x/Resolution.y;
    vec3 ray=normalize(CameraForward+(CameraRight*uv.x+CameraUp*uv.y)*0.52);
    SceneHit hit=traceScene(CameraPosition,ray);
    vec3 linearScene=sceneSky(ray);
    if(hit.distance<9999.0) {
        vec3 world=CameraPosition+ray*hit.distance;
        float diffuse=max(dot(hit.normal,normalize(SunDirection)),0.0);
        linearScene=hit.color*(vec3(0.19)+SunColor*diffuse*min(Optics.w,3.0)*0.65);
        if(VortexLife.w>.5){
            linearScene=hit.color;
            bool liquid=world.y>Layer.x+.4&&abs(vortexLiquidDistance(world))<.15;
            if(liquid){
                // Separate the front cloud segment, liquid interface and background.
                // A single air/water interface bends the background ray. It is
                // not a complete entry/exit or multiple-interface light path.
                float h=clamp((world.y-Layer.x)/max(vortexLiquidHeight(),.01),0.0,1.0);
                LiquidOptics optical=liquidOpticsAt(world,hit.normal,ray);
                float fresnel=optical.fresnel;
                vec3 orientedNormal=dot(optical.normal,ray)<0.0?optical.normal:-optical.normal;
                vec3 transmittedRay=normalize(refract(ray,orientedNormal,.75019));
                vec3 start=world+transmittedRay*.18;
                // A second full height-field trace greatly expands the first shader
                // compilation. The transmitted background uses the mean sea plane.
                float behindDistance=10000.0;vec3 background=sceneSky(transmittedRay);
                if(transmittedRay.y<-.000001&&start.y>Layer.x){
                    behindDistance=(Layer.x-start.y)/transmittedRay.y;
                    vec3 p=start+transmittedRay*behindDistance;
                    float seaF=.0204+.9796*pow(1.0-abs(transmittedRay.y),5.0);
                    background=mix(vec3(.004,.022,.026),vortexWaterReflection(reflect(transmittedRay,vec3(0,1,0)),p),seaF);
                    float seaHaze=VortexLife.w>1.5?mix(.002,.00035,smoothstep(.60,.84,h)):.002;
                    background=mix(background,sceneSky(transmittedRay),1.0-exp(-behindDistance*seaHaze));
                }
                vec4 tail=vortexLiquidBackdrop(start,transmittedRay,behindDistance);
                background=background*tail.a+tail.rgb;
                float thickness=liquidOpticalThickness(world,transmittedRay);
                float foam=optical.foam;
                vec3 absorption=exp(-vec3(.022,.016,.013)*thickness);
                vec3 transmission=absorption*(1.0-fresnel)*(1.0-foam);
                linearScene=background*transmission+hit.color
                           +vec3(.004,.010,.012)*(vec3(1.0)-absorption)*(1.0-fresnel)*(1.0-foam);
            }
        }
        else linearScene=mix(linearScene,vec3(.10,.125,.135),1.0-exp(-hit.distance*.0015));
        linearScene+=veSurfaceLight(world,hit.normal);
    }
    vec4 volume=veIntegrate(CameraPosition,ray,hit.distance);
    vec3 result=linearScene*clamp(volume.a,0.0,1.0)+volume.rgb;
    fragColor=vec4(tonemap(result),1.0);
}`;

function displayError(message, fatal = false) {
  lastError = String(message);
  const node = $(fatal ? 'fatalError' : 'recipeError');
  node.textContent = lastError; node.hidden = false;
  if (fatal) { failed = true; lastRendered = false; $('rendererStatus').textContent = '渲染失败'; $('rendererStatus').classList.add('bad'); }
  updateExports();
}
function clearRecipeError() { $('recipeError').hidden = true; if (!failed) lastError = null; updateExports(); }
function showNotice(message) { $('notice').textContent = message; $('notice').hidden = false; }
function updateExports() { for (const id of exportIds) $(id).disabled = id==='zipButton' || !ready || failed || !lastRendered || !$('recipeError').hidden; }

function parseStrictJSON(source) {
  // JSON.parse verifies syntax; this second pass rejects duplicated object keys.
  source=source.replace(/^\uFEFF/,'');
  const parsed=JSON.parse(source);let cursor=0;
  const whitespace=()=>{while(/\s/.test(source[cursor]||'')&&cursor<source.length)cursor++;};
  function readString(){
    const start=cursor++;
    while(cursor<source.length){if(source[cursor]==='\\'){cursor+=2;continue;}if(source[cursor++]==='"')break;}
    return JSON.parse(source.slice(start,cursor));
  }
  function readValue(){
    whitespace();const token=source[cursor];
    if(token==='"'){readString();return;}
    if(token==='{'){
      cursor++;whitespace();const keys=new Set();
      if(source[cursor]==='}'){cursor++;return;}
      while(cursor<source.length){
        whitespace();const key=readString();
        if(keys.has(key))throw new Error(`重复 JSON 字段：${key}`);keys.add(key);
        whitespace();cursor++;readValue();whitespace();
        if(source[cursor++]==='}')break;
      }
      return;
    }
    if(token==='['){
      cursor++;whitespace();if(source[cursor]===']'){cursor++;return;}
      while(cursor<source.length){readValue();whitespace();if(source[cursor++]===']')break;}
      return;
    }
    while(cursor<source.length&&!/[\s,}\]]/.test(source[cursor]))cursor++;
  }
  readValue();return parsed;
}

function validateRecipe(candidate,expectedSchema=schema.schema,expectedFields=Object.keys(schema.fields)) {
  if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) throw new Error('配方必须是 JSON 对象。');
  const outer = Object.keys(candidate);
  const unknown = outer.filter(k => !['schema', 'name', 'parameters'].includes(k));
  if (unknown.length) throw new Error(`未知顶层字段：${unknown.join('、')}`);
  if (candidate.schema !== expectedSchema) throw new Error(`schema 必须严格为 ${expectedSchema}`);
  if ('name' in candidate && (typeof candidate.name !== 'string' || [...candidate.name].length > 120)) throw new Error('name 必须是长度不超过 120 的字符串。');
  const params = candidate.parameters;
  if (!params || typeof params !== 'object' || Array.isArray(params)) throw new Error('缺少 parameters 对象。');
  const expected = expectedFields, actual = Object.keys(params);
  const missing = expected.filter(k => !Object.hasOwn(params,k));
  const allowed = new Set(expected);
  const extra = actual.filter(k => !allowed.has(k));
  if (missing.length || extra.length) throw new Error([missing.length ? `缺少字段：${missing.join('、')}` : '', extra.length ? `未知字段：${extra.join('、')}` : ''].filter(Boolean).join('\n'));
  for (const key of expected) {
    const spec=schema.fields[key];
    const value = params[key];
    if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${key} 必须是有限数值，不能使用字符串。`);
    if (spec.type === 'integer' && !Number.isInteger(value)) throw new Error(`${key} 必须是整数。`);
    if (value < spec.min || value > spec.max) throw new Error(`${key} 超出范围 [${spec.min}, ${spec.max}]。`);
  }
  if (params.shape === 2 && params.innerRadius >= params.radius) throw new Error('环形区域的 innerRadius 必须小于 radius。');
  if (params.flashDuration > params.flashPeriod) throw new Error('flashDuration 不能大于 flashPeriod。');
  if (Math.hypot(params.sunX,params.sunY,params.sunZ) <= 1e-6) throw new Error('太阳方向长度必须大于 0.000001。');
  return clone(candidate);
}
function upgradeLegacyRecipe(candidate) {
  const legacy=validateRecipe(candidate,'world-volume/1',legacyFieldNames);
  const parameters={...legacy.parameters};
  for(const key of newFieldNames)parameters[key]=schema.fields[key].default;
  parameters.densityModel=0;
  return validateRecipe({...legacy,schema:schema.schema,parameters});
}
function acceptRecipe(candidate) {
  return candidate?.schema==='world-volume/1'?upgradeLegacyRecipe(candidate):validateRecipe(candidate);
}
function upgradeNotice() { return `已明确将 world-volume/1 升级到 ${schema.schema}：原 50 个字段完整保留，新字段取默认值，densityModel=0。`; }

function compileShader(type, source, deferred=false) {
  const shader = gl.createShader(type); gl.shaderSource(shader, source); gl.compileShader(shader);
  if (!deferred&&!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(shader); gl.deleteShader(shader);
    throw new Error(`着色器编译失败\n${log}`);
  }
  return shader;
}
async function createProgram(core) {
  const parallel=gl.getExtension('KHR_parallel_shader_compile');
  const vs = compileShader(gl.VERTEX_SHADER, vertexSource,Boolean(parallel));
  const fs = compileShader(gl.FRAGMENT_SHADER, `#version 300 es\nprecision highp float;\nprecision highp int;\n${core}\n${sceneSource}`,Boolean(parallel));
  const linked = gl.createProgram(); gl.attachShader(linked,vs); gl.attachShader(linked,fs); gl.linkProgram(linked);
  if(parallel){while(!gl.getProgramParameter(linked,parallel.COMPLETION_STATUS_KHR))await new Promise(resolve=>setTimeout(resolve,40));}
  const shaderError=[vs,fs].filter(shader=>!gl.getShaderParameter(shader,gl.COMPILE_STATUS)).map(shader=>gl.getShaderInfoLog(shader)).join('\n');
  gl.deleteShader(vs); gl.deleteShader(fs);
  if (!gl.getProgramParameter(linked,gl.LINK_STATUS)) { const error = shaderError||gl.getProgramInfoLog(linked); gl.deleteProgram(linked); throw new Error(`着色器链接失败\n${error}`); }
  return linked;
}
function loc(name) { if (!uniformLocations.has(name)) uniformLocations.set(name,gl.getUniformLocation(program,name)); return uniformLocations.get(name); }
function forwardVector() { const c=Math.cos(camera.pitch); return [Math.sin(camera.yaw)*c,Math.sin(camera.pitch),Math.cos(camera.yaw)*c]; }
function cross(a,b) { return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]; }
function normalize(v) { const length=Math.hypot(...v)||1; return v.map(n=>n/length); }
function makeWaterDrops(){
  const data=new Float32Array(384),progress=Math.max(0,Math.min(1,elapsed/vortex.duration));
  if(!vortex.mode||progress<=.06)return data;
  const seed=recipe.parameters.seed|0;
  const random=i=>{let x=(Math.imul(i+1,1580030173)^Math.imul(seed+17,59260789))>>>0;x=Math.imul(x^(x>>>16),741103597)>>>0;x=Math.imul(x^(x>>>13),1597334677)>>>0;return ((x^(x>>>16))>>>0)/4294967296;};
  const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
  const mature=Math.max(recipe.parameters.height*(vortex.mode===2?.84:.55),4),k=(vortex.mode===2?9:7.5)/mature,omega=vortex.rotation-k*vortex.rise;
  const basis=(s,time)=>{
    let q=Math.max(0,Math.min(1,s/mature))*.65,dq=s>=0&&s<=mature?.65/mature:0;
    if(vortex.mode===2){const x=Math.max(s/mature,0),turn=Math.tanh(4*Math.max(x-1,0));q=(x<=1?x:1+.25*turn)*.65;dq=(s>=0?.65/mature:0)*(1-turn*turn);}
    const a=q*4+time*.13,b=q*3+time*.11,bend=vortex.bend*Math.sin(q*Math.PI);
    const dx=vortex.bend*dq*(Math.PI*Math.cos(q*Math.PI)*Math.sin(a)+Math.sin(q*Math.PI)*4*Math.cos(a));
    const dz=vortex.bend*dq*(Math.PI*Math.cos(q*Math.PI)*Math.cos(b)-Math.sin(q*Math.PI)*3*Math.sin(b));
    const tangent=normalize([dx,1,dz]),u=normalize([tangent[1],-tangent[0],0]),v=cross(u,tangent);
    return {c:[recipe.parameters.centerX+bend*Math.sin(a),recipe.parameters.bottom+s,recipe.parameters.centerZ+bend*Math.cos(b)],u,v};
  };
  const embedded=(s,uv,time)=>{const b=basis(s,time);return b.c.map((n,j)=>n+b.u[j]*uv[0]+b.v[j]*uv[1]);};
  for(let i=0;i<96;i++){
    const a=random(i*4),b=random(i*4+1),c=random(i*4+2),d=random(i*4+3),life=1.5+c*1.1;
    const age=((Math.max(elapsed,0)/life+a)%1)*life,birth=elapsed-age,upper=i>=36;
    const birthProgress=Math.max(0,Math.min(1,birth/vortex.duration));if(birthProgress<=.06)continue;
    const height=mature*smooth((birthProgress-.06)/.82),bowlDrop=vortex.mode===2&&i>=56;
    let h=upper?.58+b*.44:.015+b*.065;
    const bowlPhase=a*Math.PI*2;
    if(bowlDrop)h=1.035+.018*Math.sin(bowlPhase*2+.6)+.011*Math.cos(bowlPhase*3-1.1);
    const source=height*h;
    const root=Math.max(vortex.bottomRadius*1.9,6),neck=Math.max(vortex.bottomRadius*.68,2.4);
    const radius=vortex.mode===2?Math.max(vortex.bottomRadius*1.65,5)+(vortex.topRadius*.80-Math.max(vortex.bottomRadius*1.65,5))*Math.pow(Math.min(h,1),vortex.profile*.8)+Math.max(vortex.bottomRadius*1.7,5)*Math.exp(-h*19):neck+(root-neck)*Math.exp(-h*3)+Math.max(vortex.bottomRadius*.9,3)*Math.exp(-h*20);
    const theta=k*source+omega*birth+(bowlDrop?bowlPhase:(c-.5)*.60)+(i%3===0?Math.PI*2:0);
    const tip=.34+.065*Math.cos((c-.5)*.60),envelope=Math.sqrt(Math.max(1-Math.pow(Math.max(h-.75,0)/tip,2),0));
    const bowlNeck=(Math.max(vortex.bottomRadius*1.65,5)+(vortex.topRadius*.80-Math.max(vortex.bottomRadius*1.65,5))*Math.pow(.56,vortex.profile*.8)+Math.max(vortex.bottomRadius*1.7,5)*Math.exp(-.56*19))*.76;
    const bowlRim=Math.max(vortex.topRadius*.96,bowlNeck+7)*(1+.045*Math.cos(bowlPhase*2)+.026*Math.sin(bowlPhase*3+.7));
    const r=bowlDrop?bowlRim:radius*.98*envelope+1.5,uv=[Math.cos(theta)*r,Math.sin(theta)*r],frame=basis(source,birth),position=embedded(source,uv,birth);
    const e=.04,driftA=embedded(source,uv,birth+e),driftB=embedded(source,uv,birth-e),riseA=embedded(source+e,uv,birth),riseB=embedded(source-e,uv,birth);
    const radial=frame.u.map((n,j)=>n*Math.cos(theta)+frame.v[j]*Math.sin(theta));
    const turn=frame.u.map((n,j)=>n*(-uv[1])+frame.v[j]*uv[0]);
    const launch=bowlDrop?5.0+d*5.5:upper?3.0+d*3.5:7.0+d*5.0,outward=bowlDrop?5.0+d*5.0:1.4+d*3.0;
    const velocity=position.map((_,j)=>(driftA[j]-driftB[j])/(2*e)+(riseA[j]-riseB[j])/(2*e)*vortex.rise+turn[j]*vortex.rotation+radial[j]*outward+(j===1?launch:0));
    const xyz=position.map((n,j)=>n+velocity[j]*age-(j===1?4.9*age*age:0));
    const size=(i%12===0?.60+d*.35:.14+d*.34)*smooth((progress-.06)/.25)*smooth(age/.10)*smooth((life-age)/.25);
    data.set([...xyz,xyz[1]>recipe.parameters.bottom-.3?size:0],i*4);
  }
  return data;
}
function uploadUniforms() {
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_3D,noiseTexture);gl.uniform1i(loc('VortexNoiseTex'),0);
  gl.uniform1f(loc('Time'),Math.max(0,elapsed));
  gl.uniform4fv(loc('WaterDrops[0]'),makeWaterDrops());
  gl.uniform4f(loc('VortexShape'),vortex.bottomRadius,vortex.topRadius,vortex.profile,vortex.hollow);
  gl.uniform4f(loc('VortexFlow'),vortex.rotation,vortex.rise,vortex.bend,vortex.turbulence);
  gl.uniform4f(loc('VortexRing'),vortex.ringRadius,vortex.ringWidth,vortex.sprayDensity,vortex.sprayHeight);
  gl.uniform4f(loc('VortexWater'),vortex.waterDepth,vortex.waveAmp,vortex.waveSpeed,vortex.foam);
  gl.uniform4f(loc('VortexLife'),vortex.duration,vortex.inflow,vortex.canopyDensity,vortex.mode);
  for (const [name, fieldNames] of Object.entries(contract.uniforms)) {
    const values=fieldNames.map(key=>recipe.parameters[key]);
    if(name==='Bounds')values[3]=Math.max(values[3],vortex.topRadius+vortex.bend+20,vortex.ringRadius+vortex.ringWidth*3);
    if (name==='ViewSteps' || name==='LightSteps') gl.uniform1i(loc(name),values[0]);
    else if (values.length===1) gl.uniform1f(loc(name),values[0]);
    else if (values.length===3) gl.uniform3fv(loc(name),values);
    else if (values.length===4) gl.uniform4fv(loc(name),values);
  }
  const forward=forwardVector(), right=normalize(cross(forward,[0,1,0])), up=normalize(cross(right,forward));
  gl.uniform2f(loc('Resolution'),canvas.width,canvas.height);
  gl.uniform3fv(loc('CameraPosition'),camera.position); gl.uniform3fv(loc('CameraForward'),forward);
  gl.uniform3fv(loc('CameraRight'),right); gl.uniform3fv(loc('CameraUp'),up);
}
function resizeCanvas() {
  const bounds=canvas.getBoundingClientRect();
  const ratio=Math.min(1,960/Math.max(1,bounds.width),640/Math.max(1,bounds.height))*renderScale;
  const width=Math.max(16,Math.round(bounds.width*ratio)),height=Math.max(16,Math.round(bounds.height*ratio));
  if (canvas.width!==width || canvas.height!==height) { canvas.width=width;canvas.height=height; }
}
function draw() {
  if (!ready || failed) return false;
  try {
    resizeCanvas(); gl.viewport(0,0,canvas.width,canvas.height); gl.useProgram(program); gl.bindVertexArray(vao);
    uploadUniforms(); gl.drawArrays(gl.TRIANGLES,0,3);
    const error=gl.getError();
    if (error!==gl.NO_ERROR) throw new Error(`WebGL 渲染错误：0x${error.toString(16)}`);
    lastRendered=true; updateExports();$('timeline').value=Math.min(elapsed,90);$('phase').textContent=elapsed===0?'尚未形成':elapsed<vortex.duration*.35?'云帽 / 底部喷环形成':elapsed<vortex.duration?'漏斗逐渐连通':'成熟涡旋';
    return true;
  } catch(error) { displayError(error.message,true); return false; }
}
function syncCameraInputs() { ['cameraX','cameraY','cameraZ'].forEach((id,i)=>{ if (document.activeElement!==$(id)) $(id).value=camera.position[i].toFixed(1); }); }
function syncScenePresentation() {
  const endScene=recipe.parameters.densityModel>0;
  const labels=endScene?{outside:'末地内景',inside:'穿行云墙',ground:'岛屿近景',liquid:'水片近景',mouth:'顶部碗口',above:'俯瞰浮岛'}:{outside:'外部观察',inside:'进入云雾',ground:'贴近地面',liquid:'水片近景',mouth:'顶部碗口',above:'俯瞰区域'};
  $('viewLabel').textContent=labels[cameraView]||labels.outside;
  $('sceneCaption').textContent='世界坐标 · 云雾体积 · 海面与吸水柱实际深度';
  canvas.setAttribute('aria-label',endScene?'可拖动旋转的三维云雾浮岛预览':'可拖动旋转的三维云雾地形预览');
  document.querySelectorAll('[data-view]').forEach(button=>{button.textContent=labels[button.dataset.view];button.classList.toggle('active',button.dataset.view===cameraView);});
}
function setView(view) {
  cameraView=view;
  const p=recipe.parameters,cx=p.centerX,cz=p.centerZ,r=p.radius;
  const top=p.shape===1?p.centerY+r:p.bottom+p.height;
  const middle=p.shape===1?p.centerY:p.bottom+p.height*.45;
  const endScene=p.densityModel>0;
  const views=endScene?{
    outside:{position:[cx+200,120,cz+240],target:[cx,72,cz],label:'末地内景'},
    inside:{position:[cx+70,105,cz+85],target:[cx-90,95,cz-145],label:'穿行云墙'},
    ground:{position:[cx+95,78,cz+115],target:[cx,65,cz],label:'岛屿近景'},
    liquid:{position:[cx+78,p.bottom+38,cz+105],target:[cx,p.bottom+38,cz],label:'水片近景'},
    mouth:{position:[cx+115,p.bottom+p.height*1.18,cz+138],target:[cx,p.bottom+p.height*.73,cz],label:'顶部碗口'},
    above:{position:[cx+170,240,cz+200],target:[cx,65,cz],label:'俯瞰浮岛'},
  }:{
    outside:{position:[cx+195,78,cz+295],target:[cx,84,cz],label:'外部观察'},
    inside:{position:[cx-r*.2,middle,cz+r*.18],target:[cx+r*.4,middle+4,cz-r*.6],label:'进入云雾'},
    ground:{position:[cx+95,30,cz+150],target:[cx,40,cz],label:'近看喷环 / 水面'},
    liquid:{position:[cx+78,p.bottom+38,cz+105],target:[cx,p.bottom+38,cz],label:'水片近景'},
    mouth:{position:[cx+115,p.bottom+p.height*1.18,cz+138],target:[cx,p.bottom+p.height*.73,cz],label:'顶部碗口'},
    above:{position:[cx+r*.35,top+r*.7,cz+r*.45],target:[cx,middle,cz],label:'俯瞰区域'},
  };
  const v=views[view]||views.outside; camera.position=v.position;
  const direction=normalize(v.target.map((x,i)=>x-v.position[i]));
  camera.yaw=Math.atan2(direction[0],direction[2]); camera.pitch=Math.asin(direction[1]);
  syncScenePresentation();
  syncCameraInputs(); draw();
}
function syncToggles() {
  for (const [id,key] of [['emissionToggle','emission'],['mediumToggle','mediumGain'],['surfaceToggle','surfaceGain']]) {
    $(id).checked=recipe.parameters[key]>0;
    if (recipe.parameters[key]>0) rememberedGains[key]=recipe.parameters[key];
  }
}
function syncFields(preserveActive=false) {
  for (const [key,inputs] of fieldsUI) {
    const value=recipe.parameters[key];
    if(!preserveActive||document.activeElement!==inputs.number)inputs.number.value=value;
    inputs.range.value=value;inputs.row.classList.remove('invalid');
  }
  $('recipeName').value=recipe.name||'';syncDensity(preserveActive);syncToggles();syncScenePresentation();
}
function syncDensity(preserveActive=false){$('densityQuick').value=recipe.parameters.density;if(!preserveActive||document.activeElement!==$('densityNumber'))$('densityNumber').value=recipe.parameters.density;}
function changeField(key,value) {
  pendingValues.set(key,value);
  const candidate=clone(recipe);
  for(const [pendingKey,pendingValue] of pendingValues)candidate.parameters[pendingKey]=pendingValue;
  try {
    const oldModel=recipe.parameters.densityModel;
    recipe=validateRecipe(candidate);pendingValues.clear();clearRecipeError();syncFields(true);
    if(key==='densityModel'&&value!==oldModel)setView(cameraView);else draw();
  } catch(error) { fieldsUI.get(key).row.classList.add('invalid');displayError(error.message); }
}
function buildFields() {
  const groups=new Map();
  for (const [key,spec] of Object.entries(schema.fields)) {
    if(!new Set(['centerX','centerZ','bottom','height','radius','density','scale','extinction','anisotropy','ambient','sunGain','mediumR','mediumG','mediumB','seed','viewSteps','lightSteps','flashEnabled','flashStrength','flashRadius','flashPeriod','flashDuration','flashPlacement','mediumGain','surfaceGain','emission']).has(key))continue;
    if (!groups.has(spec.group)) {
      const detail=document.createElement('details');detail.dataset.group=spec.group;detail.open=['区域','密度'].includes(spec.group)||(spec.group==='末地分布'&&recipe.parameters.densityModel>0);
      const summary=document.createElement('summary');summary.textContent=spec.group;detail.append(summary);$('parameterGroups').append(detail);groups.set(spec.group,detail);
    }
    const row=document.createElement('div');row.className='field';
    const label=document.createElement('label');label.htmlFor=`field-${key}`;label.textContent=spec.label;
    const number=document.createElement('input');number.id=`field-${key}`;number.type='number';number.min=spec.min;number.max=spec.max;number.step=spec.step;number.dataset.field=key;number.title=`${key} · ${spec.min} 至 ${spec.max}`;
    const range=document.createElement('input');range.type='range';range.min=spec.min;range.max=spec.max;range.step=spec.step;range.setAttribute('aria-label',spec.label);
    if (key==='centerX'||key==='centerZ') { range.min=-400;range.max=400; }
    if (key==='centerY'||key==='bottom') { range.min=-100;range.max=300; }
    row.append(label,number,range);groups.get(spec.group).append(row);fieldsUI.set(key,{number,range,row});
    const editNumber=()=>changeField(key,number.value===''?NaN:Number(number.value));
    number.addEventListener('input',editNumber);number.addEventListener('change',editNumber);
    range.addEventListener('input',()=>changeField(key,Number(range.value)));
  }
}
function setRecipe(candidate,resetCamera=false) {
  const legacy=candidate?.schema==='world-volume/1';
  const checked=acceptRecipe(candidate);if(checked.parameters.shape!==0||checked.parameters.densityModel!==0)throw Error('涡旋需要有限圆柱区域与独立漏斗密度');
  recipe=checked;pendingValues.clear();clearRecipeError();syncFields();
  const matched=presets.find(p=>p.name===recipe.name && JSON.stringify(p.parameters)===JSON.stringify(recipe.parameters));
  $('presetSelect').value=matched?.id||'custom';
  if (resetCamera) setView(cameraView); else draw();
  if(legacy)showNotice(upgradeNotice());
  return clone(recipe);
}
function saveBlob(blob,name) {
  const link=document.createElement('a'),url=URL.createObjectURL(blob);link.href=url;link.download=name;document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
function filename(extension) { const safe=(recipe.name||'world-volume').replace(/[<>:"/\\|?*\u0000-\u001f]/g,'_');return `${safe}.${extension}`; }
function checkExport() {
  if (!ready||failed||!lastRendered) throw new Error('尚未成功渲染，禁止导出。');
  if (!$('recipeError').hidden) throw new Error('请先修正无效字段，再导出。');
  validateRecipe(recipe);
}
function bindEvents() {
  for(const id of ['densityQuick','densityNumber'])$(id).addEventListener('input',()=>changeField('density',$(id).value===''?NaN:Number($(id).value)));
  document.querySelectorAll('[data-mode]').forEach(el=>el.addEventListener('click',()=>{vortex.mode=Number(el.dataset.mode);recipe.name=['龙卷风 · 灰黑漏斗','龙吸水 · 不规则水片与飞溅','水龙卷 · 旋转液态漏斗'][vortex.mode];Object.assign(recipe.parameters,[{mediumR:.47,mediumG:.49,mediumB:.52,ambient:.45,density:3.5},{mediumR:.64,mediumG:.68,mediumB:.71,ambient:.65,density:3.0},{mediumR:.40,mediumG:.64,mediumB:.69,ambient:.65,density:1.6}][vortex.mode]);syncFields();syncVortex();draw();}));
  $('timeline').addEventListener('input',()=>{paused=true;syncPause();setTime(Number($('timeline').value));});
  $('replay').addEventListener('click',()=>{setTime(0);paused=false;syncPause();});

  $('presetSelect').addEventListener('change',()=>{
    const preset=presets.find(p=>p.id===$('presetSelect').value);
    if(preset) setRecipe({schema:preset.schema,name:preset.name,parameters:preset.parameters},true);
  });
  $('recipeName').addEventListener('input',()=>{recipe.name=$('recipeName').value;});
  $('pauseButton').addEventListener('click',()=>{paused=!paused;syncPause();});
  $('timeInput').addEventListener('input',()=>{timeInputDirty=true;});
  $('setTimeButton').addEventListener('click',()=>setTime(Number($('timeInput').value)));
  $('zeroTimeButton').addEventListener('click',()=>setTime(0));
  $('resolutionSelect').addEventListener('change',()=>{renderScale=Number($('resolutionSelect').value);draw();});
  document.querySelectorAll('[data-view]').forEach(button=>button.addEventListener('click',()=>setView(button.dataset.view)));
  $('resetCameraButton').addEventListener('click',()=>setView(cameraView));
  ['cameraX','cameraY','cameraZ'].forEach((id,i)=>$(id).addEventListener('input',()=>{
    if($(id).value==='')return;
    const value=Number($(id).value);if(Number.isFinite(value)){camera.position[i]=value;draw();}else syncCameraInputs();
  }));
  for (const [id,key] of [['emissionToggle','emission'],['mediumToggle','mediumGain'],['surfaceToggle','surfaceGain']]) {
    $(id).addEventListener('change',()=>{
      if ($(id).checked) changeField(key,rememberedGains[key]);
      else {rememberedGains[key]=recipe.parameters[key]||rememberedGains[key];changeField(key,0);}
    });
  }
  $('importButton').addEventListener('click',()=>$('importFile').click());
  $('importFile').addEventListener('change',async()=>{
    try {
      const file=$('importFile').files[0];if(!file)return;
      if(file.size>65536)throw new Error('配方文件不能超过 65536 字节。');
      const imported=parseStrictJSON(await file.text());const full=importVortex(imported);vortex=full.v;setRecipe(full.base,true);syncVortex();if(full.preview){await window.volumeLab.setCamera(full.preview.camera);setTime(full.preview.time);}showNotice("已恢复涡旋参数、云、水面与相机时间。");
    }catch(error){displayError(error.message);}finally{$('importFile').value='';}
  });
  $('jsonButton').addEventListener('click',()=>{try{checkExport();saveBlob(new Blob([JSON.stringify(exportVortex(),null,2)+'\n'],{type:'application/json'}),filename('json'));showNotice('完整作品JSON已导出，包含云、水面、涡旋和相机时间。');}catch(error){displayError(error.message);}});
  $('pngButton').addEventListener('click',()=>{
    try{checkExport();if(!draw())return;gl.finish();const captureTime=elapsed;canvas.toBlob(blob=>{if(blob){saveBlob(blob,filename('png'));showNotice(`PNG 已生成：${canvas.width} × ${canvas.height}，时间 ${captureTime.toFixed(2)} 秒。`);}else displayError('截图生成失败。');},'image/png');}catch(error){displayError(error.message);}
  });
  $('zipButton').addEventListener('click',async()=>{
    try {
      checkExport();$('zipButton').disabled=true;showNotice('正在校验配方并打包共享 GLSL、参数与接入资源…');
      const response=await fetch('/api/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(recipe)});
      if(!response.ok){let message=`资源导出失败（HTTP ${response.status}）`;try{message=(await response.json()).error||message;}catch{}throw new Error(message);}
      saveBlob(await response.blob(),filename('zip'));showNotice('资源 ZIP 已生成。');
    }catch(error){displayError(error.message);}finally{updateExports();}
  });
  let dragging=false,lastPointer=[0,0];
  canvas.addEventListener('pointerdown',event=>{dragging=true;lastPointer=[event.clientX,event.clientY];canvas.setPointerCapture(event.pointerId);canvas.focus();});
  canvas.addEventListener('pointermove',event=>{
    if(!dragging)return;camera.yaw-=(event.clientX-lastPointer[0])*.006;camera.pitch=Math.max(-1.5,Math.min(1.5,camera.pitch+(event.clientY-lastPointer[1])*.006));lastPointer=[event.clientX,event.clientY];draw();
  });
  const stopDrag=()=>{dragging=false;};canvas.addEventListener('pointerup',stopDrag);canvas.addEventListener('pointercancel',stopDrag);
  canvas.addEventListener('wheel',event=>{event.preventDefault();const forward=forwardVector();const distance=event.deltaY*.08;camera.position=camera.position.map((p,i)=>p-forward[i]*distance);syncCameraInputs();draw();},{passive:false});
  canvas.addEventListener('keydown',event=>{if(['KeyW','KeyA','KeyS','KeyD','KeyQ','KeyE','ShiftLeft','ShiftRight'].includes(event.code)){event.preventDefault();keys.add(event.code);}});
  window.addEventListener('keyup',event=>keys.delete(event.code));window.addEventListener('blur',()=>keys.clear());canvas.addEventListener('blur',()=>keys.clear());
  canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();displayError('WebGL 上下文已丢失。请刷新工作台后重新载入配方。',true);});
  window.addEventListener('resize',()=>draw());
}
function syncPause() { $('pauseButton').textContent=paused?'继续':'暂停'; }
function setTime(time) {
  if(!Number.isFinite(time)||time<0||time>86400){displayError('时间须在 0 至 86400 秒之间。');return false;}
  elapsed=time;paused=true;timeInputDirty=false;syncPause();$('timeInput').value=time.toFixed(2);return draw();
}
function advanceCamera(dt) {
  if(!keys.size)return false;
  const forward=forwardVector(),right=normalize(cross(forward,[0,1,0]));
  const speed=(keys.has('ShiftLeft')||keys.has('ShiftRight')?95:30)*dt;
  let delta=[0,0,0];
  for(const [code,v,sign] of [['KeyW',forward,1],['KeyS',forward,-1],['KeyD',right,1],['KeyA',right,-1],['KeyE',[0,1,0],1],['KeyQ',[0,1,0],-1]])if(keys.has(code))delta=delta.map((x,i)=>x+v[i]*sign*speed);
  camera.position=camera.position.map((x,i)=>x+delta[i]);syncCameraInputs();return true;
}
function animationFrame(now) {
  const dt=previousFrame?Math.min((now-previousFrame)/1000,.1):0;previousFrame=now;
  if(ready&&!failed){
    if(!paused)elapsed=Math.min(elapsed+dt,86400);
    const moved=advanceCamera(dt);
    if((!paused||moved)&&now-lastPaint>33){const start=performance.now();draw();frameMs=performance.now()-start;lastPaint=now;}
    if(!timeInputDirty&&document.activeElement!==$('timeInput'))$('timeInput').value=elapsed.toFixed(2);
    $('renderStats').textContent=`${canvas.width} × ${canvas.height} · ${recipe.parameters.viewSteps*2}/${recipe.parameters.lightSteps} 采样 · ${paused?'已暂停':`${Math.round(1000/Math.max(33,frameMs))} fps 上限`}`;
  }
  requestAnimationFrame(animationFrame);
}
async function fetchResource(path,textMode=false) {
  const response=await fetch(path);if(!response.ok)throw new Error(`无法读取 ${path}（HTTP ${response.status}）。请用 serve.py 启动。`);
  return textMode?response.text():response.json();
}
async function start() {
  canvas=$('viewport');
  try {
    [schema,contract,presets,vortexSchema]=await Promise.all([fetchResource('schema.json'),fetchResource('contract.json'),fetchResource('presets.json'),fetchResource('vortex-schema.json')]);vortex=Object.fromEntries(Object.entries(vortexSchema.fields).map(([key,spec])=>[key,spec.default]));
    if(schema.schema!=='world-volume/2'||contract.schema!==schema.schema)throw new Error('工作台要求一致的 world-volume/2 schema 与 contract。');
    legacyFieldNames=Object.entries(schema.fields).filter(([,spec])=>!endUniformNames.has(spec.uniform)).map(([key])=>key);
    newFieldNames=Object.keys(schema.fields).filter(key=>!legacyFieldNames.includes(key));
    if(legacyFieldNames.length!==50||newFieldNames.length!==12)throw new Error('升级契约需要完整的旧 50 字段与新增 12 字段。');
    const mappedFields=Object.values(contract.uniforms).flat();
    if(mappedFields.length!==fieldCount()||new Set(mappedFields).size!==fieldCount()||mappedFields.some(key=>!Object.hasOwn(schema.fields,key)))throw new Error('uniform 契约与 schema 字段不一致。');
    $('schemaTag').textContent='VORTEX CLOUD / 1';
    $('contractSummary').textContent=`20 个涡旋参数 · 独立密度与水面`;
    let initialRecipe=null;
    const initialResponse=await fetch('initial-recipe.json');
    if(initialResponse.ok)initialRecipe=parseStrictJSON(await initialResponse.text());
    else if(initialResponse.status!==404)throw new Error(`初始配方读取失败（HTTP ${initialResponse.status}）。`);
    recipe=acceptRecipe(initialRecipe||{schema:presets[0].schema,name:presets[0].name,parameters:presets[0].parameters});
    for(const preset of presets)validateRecipe({schema:preset.schema,name:preset.name,parameters:preset.parameters});
    for(const preset of presets){const option=document.createElement('option');option.value=preset.id;option.textContent=preset.name;$('presetSelect').append(option);}
    const custom=document.createElement('option');custom.value='custom';custom.textContent='自定义配方';$('presetSelect').append(custom);
    buildFields();syncFields();buildVortexFields();bindEvents();
    if(initialRecipe){$('presetSelect').value='custom';showNotice(initialRecipe.schema==='world-volume/1'?`已载入 initial-recipe.json。${upgradeNotice()}`:`已载入 initial-recipe.json，全部 ${fieldCount()} 个字段通过严格验证。`);}
    gl=canvas.getContext('webgl2',{alpha:false,antialias:false,preserveDrawingBuffer:true,powerPreference:'high-performance'});
    if(!gl)throw new Error('当前浏览器不支持 WebGL2，无法运行三维体积预览。');
    $('rendererStatus').textContent='生成三维云块与侵蚀数据';
    noiseData=makeVortexNoise();noiseTexture=uploadVortexNoise(gl,noiseData);
    $('rendererStatus').textContent='正在载入水体与云雾';
    program=await createProgram(await fetchResource('volume-core.glsl',true));vao=gl.createVertexArray();ready=true;
    elapsed=vortex.duration*1.25;
    $('rendererStatus').textContent='立体水柱 · 云块 · 碎浪';setView('outside');
    requestAnimationFrame(animationFrame);
    return true;
  }catch(error){displayError(error.message,true);return false;}
}
const initialization=start();
window.volumeLab={
  ready:initialization,
  getRecipe:()=>recipe?clone(recipe):null,
  getWork:()=>exportVortex(),
  getNoiseData:()=>noiseData,
  getLiquidDrops:()=>Array.from(makeWaterDrops()),
  setVortex:async(candidate)=>{await initialization;applyVortex(candidate);},
  setMode:async(mode)=>{await initialization;document.querySelector(`[data-mode="${mode}"]`).click();},
  capture:()=>{draw();gl.finish();return canvas.toDataURL('image/png');},
  validateRecipe:candidate=>validateRecipe(candidate),
  upgradeRecipe:candidate=>upgradeLegacyRecipe(candidate),
  parseRecipe:text=>acceptRecipe(parseStrictJSON(text)),
  setRecipe:async(candidate,options={})=>{if(!await initialization)throw new Error(lastError);return setRecipe(candidate,Boolean(options.resetCamera));},
  render:async(time)=>{if(!await initialization)throw new Error(lastError);if(time!==undefined&&!setTime(time))throw new Error(lastError);if(!draw())throw new Error(lastError);gl.finish();return window.volumeLab.getState();},
  setCamera:async(state)=>{
    if(!await initialization)throw new Error(lastError);
    if(state.position){if(!Array.isArray(state.position)||state.position.length!==3||!state.position.every(Number.isFinite))throw new Error('camera.position 必须含三个有限数字。');camera.position=[...state.position];}
    if(state.yaw!==undefined){if(!Number.isFinite(state.yaw))throw new Error('yaw 必须为有限数。');camera.yaw=state.yaw;}
    if(state.pitch!==undefined){if(!Number.isFinite(state.pitch))throw new Error('pitch 必须为有限数。');camera.pitch=Math.max(-1.5,Math.min(1.5,state.pitch));}
    syncCameraInputs();draw();return clone(camera);
  },
  setView:async(view)=>{if(!await initialization)throw new Error(lastError);setView(view);return clone(camera);},
  pause:value=>{paused=Boolean(value);syncPause();},
  getState:()=>({ready,failed,rendered:lastRendered,time:elapsed,paused,fieldCount:schema?Object.keys(schema.fields).length:0,presetCount:presets?.length||0,camera:clone(camera),resolution:canvas?[canvas.width,canvas.height]:null,error:lastError,composition:'linearScene + surfaceLight; scene * transmittance + radiance; tonemap'}),
  selfTest:async()=>{
    if(!await initialization)throw new Error(lastError);
    const mappedFields=Object.values(contract.uniforms).flat();
    const results={fields:new Set(mappedFields).size===fieldCount()&&mappedFields.every(key=>Object.hasOwn(schema.fields,key)),presets:presets.every(p=>Object.keys(p.parameters).length===fieldCount())};
    const base=clone(recipe);
    for(const [label,mutation] of Object.entries({missing:p=>delete p.parameters.density,extra:p=>p.parameters.unknown=1,string:p=>p.parameters.density='1',integer:p=>p.parameters.viewSteps=8.5,nan:p=>p.parameters.density=NaN,outer:p=>p.extra=1,wrongSchema:p=>p.schema='other/1'})){
      const invalid=clone(base);mutation(invalid);let rejected=false;try{validateRecipe(invalid);}catch{rejected=true;}results[`reject_${label}`]=rejected;
    }
    for(const [label,text] of Object.entries({duplicate:'{"x":1,"x":2}',nestedDuplicate:'{"parameters":{"density":1,"density":2}}',escapedDuplicate:'{"x":1,"\\u0078":2}'})){
      let rejected=false;try{parseStrictJSON(text);}catch{rejected=true;}results[`reject_${label}`]=rejected;
    }
    const legacy={schema:'world-volume/1',name:'旧配方升级自检',parameters:Object.fromEntries(legacyFieldNames.map(key=>[key,base.parameters[key]]))};
    const upgraded=upgradeLegacyRecipe(legacy);
    results.legacyPreserved=legacyFieldNames.every(key=>upgraded.parameters[key]===legacy.parameters[key]);
    results.legacyDefaults=newFieldNames.every(key=>upgraded.parameters[key]===(key==='densityModel'?0:schema.fields[key].default));
    for(const [label,mutation] of Object.entries({missing:p=>delete p.parameters.density,extra:p=>p.parameters.densityModel=1,invalid:p=>p.parameters.viewSteps=0})){
      const invalid=clone(legacy);mutation(invalid);let rejected=false;try{upgradeLegacyRecipe(invalid);}catch{rejected=true;}results[`reject_legacy_${label}`]=rejected;
    }
    results.render=draw();results.webglError=gl.getError()===gl.NO_ERROR;results.exportEnabled=['jsonButton','pngButton'].every(id=>!$(id).disabled)&&$('zipButton').disabled;
    return {passed:Object.values(results).every(Boolean),results,state:window.volumeLab.getState()};
  }
};
