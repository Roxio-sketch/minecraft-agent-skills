// Original procedural scene, world-depth compositor and cache host.
import {PACKS,COLORS} from './recipe.js';
import {createAtmosphereLuts} from './atmosphere-luts.js';
import {createWeatherData} from './weather-data.js';

// Fix selected branches at compilation; preserve surface equations and actual
// uniform strengths. Forge keeps its uniform path. ANGLE otherwise expands
// all seven giant branches together with both black-hole routes.
export function specializeGiantStyle(source,style,blackHoleRoute) {
  if(!Number.isInteger(style)||style<0||style>7)throw new Error('无效巨型天体样式');
  if(!Number.isInteger(blackHoleRoute)||blackHoleRoute<0||blackHoleRoute>2)throw new Error('无效黑洞渲染路线');
  const dispatch='if(GiantBodySettings.x>0.5)',selector='int style=int(floor(GiantBodySettings.x+0.5));';
  const appearance='BlackHoleAppearance.x',active='BlackHoleSettings.x>0.0',inactive='BlackHoleSettings.x<=0.0';
  for(const [anchor,count]of[[dispatch,1],[selector,1],[appearance,3],[active,1],[inactive,3]])
    if(source.split(anchor).length!==count+1)throw new Error('天体编译入口不匹配');
  const specialized=source.replace(dispatch,`if(${style}.0>0.5)`).replace(selector,`const int style=${style};`)
    .replaceAll(appearance,blackHoleRoute===2?'2.0':'1.0');
  // Preserve active GLSL guards: uploaded subnormals may flush to zero in GPU ALU.
  return blackHoleRoute===0?specialized.replaceAll(active,'false').replaceAll(inactive,'true'):specialized;
}

export async function createEnvironmentRenderer(gl) {
  const sources=await Promise.all(['sky-core.glsl','sky-extra.glsl','atmosphere-physical.glsl','weather-core.glsl','environment-core.glsl'].map(async name=>{
    const response=await fetch('./'+name,{cache:'no-store'});
    if(!response.ok)throw new Error(`无法读取 ${name}（HTTP ${response.status}）`);return response.text();
  }));
  const bundle=sources.join('\n'),luts=createAtmosphereLuts(gl,sources[2]),weather=createWeatherData(gl);
  const floatTargets=Boolean(gl.getExtension('EXT_color_buffer_float'));
  const timer=gl.getExtension('EXT_disjoint_timer_query_webgl2'),queries=[],gpuMs={};
  const vertex=`#version 300 es
precision highp float;out vec2 vUV;
void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);vUV=p;gl_Position=vec4(p*2.-1.,0.,1.);}`;
  const common=`#version 300 es
precision highp float;precision highp int;in vec2 vUV;out vec4 fragColor;
uniform vec2 Viewport;uniform vec2 ViewAngles;uniform float Fov;uniform vec3 CameraPosition;uniform int PreviewTransparent;
${bundle}
vec3 previewRay(){vec2 p=vUV*2.-1.;float tangent=tan(Fov*.5);
vec3 r=normalize(vec3(-p.x*Viewport.x/Viewport.y*tangent,p.y*tangent,1.));
float cp=cos(ViewAngles.y),sp=sin(ViewAngles.y);r=vec3(r.x,cp*r.y-sp*r.z,sp*r.y+cp*r.z);
float cy=cos(ViewAngles.x),sy=sin(ViewAngles.x);return vec3(cy*r.x-sy*r.z,r.y,sy*r.x+cy*r.z);}
vec3 previewForward(){float cp=cos(ViewAngles.y),sp=sin(ViewAngles.y);return vec3(-sin(ViewAngles.x)*cp,-sp,cos(ViewAngles.x)*cp);}
float previewSurface(vec3 ray,int layer,out vec4 color){color=vec4(0.);float t=1e9;
if(PreviewTransparent==1&&abs(ray.z)>.00001){float z=layer==0?28.:19.;t=(z-CameraPosition.z)/ray.z;vec3 p=CameraPosition+ray*t;
 if(t<=0.||abs(p.x)>13.||p.y<60.||p.y>84.)return 1e9;
 float frame=step(12.7,abs(p.x))+step(p.y,60.3)+step(83.7,p.y);
 color=vec4(layer==0?vec3(.15,.35,.44):vec3(.35,.52,.55),frame>0.?1.:(layer==0?.24:.32));
}else if(PreviewTransparent==2&&layer==0&&ray.y<-.00001){t=(62.-CameraPosition.y)/ray.y;vec3 p=CameraPosition+ray*t;
 if(t<=0.||abs(p.x)>40.||p.z<4.||p.z>100.)return 1e9;
 float wave=sin(p.x*.31+Time*.7)*sin(p.z*.27-Time*.5);color=vec4(vec3(.10,.28,.34)+wave*.018,.48);
}return t;}
`;
  const sceneFragment=common+`
uniform int PreviewScene;
float boxHit(vec3 ro,vec3 rd,vec3 lower,vec3 upper,out vec3 normal){
vec3 safe=vec3(abs(rd.x)<1e-6?1e-6:rd.x,abs(rd.y)<1e-6?1e-6:rd.y,abs(rd.z)<1e-6?1e-6:rd.z);
vec3 a=(lower-ro)/safe,b=(upper-ro)/safe,nearP=min(a,b),farP=max(a,b);
float start=max(max(nearP.x,nearP.y),nearP.z),end=min(min(farP.x,farP.y),farP.z);
normal=vec3(0.);if(end<max(start,0.))return 1e9;
float t=start>0.?start:end;vec3 p=ro+rd*t;
vec3 center=(lower+upper)*.5,q=abs((p-center)/max((upper-lower)*.5,vec3(.001)));
if(q.x>q.y&&q.x>q.z)normal=vec3(sign(p.x-center.x),0.,0.);
else if(q.y>q.z)normal=vec3(0.,sign(p.y-center.y),0.);else normal=vec3(0.,0.,sign(p.z-center.z));return t;}
void main(){vec3 ray=previewRay();vec3 color=renderSky(ray);float hit=1e9;vec3 normal=vec3(0.,1.,0.);vec3 material=vec3(.2,.28,.22);
if(PreviewScene==1){
if(ray.y<-.00001){float t=(60.-CameraPosition.y)/ray.y;if(t>0.&&t<1600.){hit=t;vec3 p=CameraPosition+ray*t;float tile=mod(floor(p.x/4.)+floor(p.z/4.),2.);material=mix(vec3(.25,.34,.25),vec3(.29,.38,.29),tile);}}
vec3 n;float t=boxHit(CameraPosition,ray,vec3(-22.,60.,36.),vec3(-4.,79.,56.),n);if(t<hit){hit=t;normal=n;material=vec3(.54,.51,.42);}
t=boxHit(CameraPosition,ray,vec3(15.,60.,54.),vec3(36.,70.,83.),n);if(t<hit){hit=t;normal=n;material=vec3(.31,.42,.48);}
t=boxHit(CameraPosition,ray,vec3(-52.,60.,98.),vec3(-33.,88.,130.),n);if(t<hit){hit=t;normal=n;material=vec3(.43,.32,.29);}}
if(hit<1e8){vec3 sun,moon,side,ms;skyDayDirections(sun,moon,side,ms);float daylight=smoothstep(-.06,.16,sun.y);
color=material*(.22+daylight*(.35+.5*max(dot(normal,sun),0.)));}
if(PipelineSettings.x<.5){for(int i=0;i<2;i++){vec4 surface;float t=previewSurface(ray,i,surface);if(t<hit)color=mix(color,surface.rgb,surface.a);}}
if(hit<1e8){float z=max(dot(ray,previewForward())*hit,.050001);gl_FragDepth=clamp(4096./(4096.-.05)-4096.*.05/((4096.-.05)*z),0.,1.);}else gl_FragDepth=1.;fragColor=vec4(color,1.);}`;
  const environmentFragment=common+`
uniform sampler2D SceneSampler;uniform sampler2D DepthSampler;
uniform int OutputDeltaEncoding;
void main(){vec3 scene=texture(SceneSampler,vUV).rgb;float depth=texture(DepthSampler,vUV).r;
vec3 ray=previewRay();bool hit=depth<.999999;float z=.05*4096./max(4096.-depth*(4096.-.05),.0001);
float distance=hit?z/max(dot(ray,previewForward()),.00001):max(VolumeCloudGeometry.z,max(FogMotion.w,LightingDetail.y));
vec3 color=skyComposeEnvironment(scene,CameraPosition,ray,distance,hit);
if(OutputDeltaEncoding==1)color-=scene;
else if(OutputDeltaEncoding==2)color=(color-scene)*(127./255.)+128./255.;
fragColor=vec4(color,1.);}`;
  const surfaceFragment=common+`
uniform sampler2D DepthSampler;uniform int PreviewLayer;
void main(){vec3 ray=previewRay();vec4 surface;float t=previewSurface(ray,PreviewLayer,surface);
if(t>1e8||surface.a<=0.)discard;
float depth=texture(DepthSampler,vUV).r;float z=.05*4096./max(4096.-depth*(4096.-.05),.0001);
float limit=depth<.999999?z/max(dot(ray,previewForward()),.00001):1e9;if(t>=limit)discard;
fragColor=vec4(skyComposeEnvironment(surface.rgb,CameraPosition,ray,t,true),surface.a);}`;
  // Full-resolution scene textures remain sharp; only the environment delta is interpolated.
  const upsampleFragment=`#version 300 es
precision highp float;in vec2 vUV;out vec4 fragColor;
uniform sampler2D SceneSampler;uniform sampler2D DepthSampler;uniform sampler2D EffectSampler;
uniform vec2 LowSize;uniform float DepthTolerance;
uniform int OutputDeltaEncoding;
float metric(float d){return d>=.999999?4096.:.05*4096./max(4096.-d*(4096.-.05),.0001);}
void main(){vec3 base=texture(SceneSampler,vUV).rgb;float center=metric(texture(DepthSampler,vUV).r);
vec2 pos=vUV*LowSize-.5,cell=floor(pos),f=fract(pos);vec3 delta=vec3(0.);float total=0.;
for(int y=0;y<2;y++)for(int x=0;x<2;x++){vec2 uv=(cell+vec2(float(x),float(y))+.5)/LowSize;
 float depth=metric(texture(DepthSampler,uv).r),difference=abs(depth-center)/max(min(depth,center),1.);
 vec3 d=texture(EffectSampler,uv).rgb;
 if(OutputDeltaEncoding==2)d=(d-128./255.)*(255./127.);
 float weight=(x==0?1.-f.x:f.x)*(y==0?1.-f.y:f.y)*exp(-difference/max(DepthTolerance,.001));
 if(difference<max(DepthTolerance,.001)*4.){delta+=d*weight;total+=weight;}}
fragColor=vec4(clamp(base+(total>1e-6?delta/total:vec3(0.)),0.,1.),1.);}`;
  function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const e=gl.getShaderInfoLog(s);gl.deleteShader(s);throw new Error(e);}return s;}
  function program(fragment){const p=gl.createProgram();let vs=null,fs=null;
    try{vs=compile(gl.VERTEX_SHADER,vertex);fs=compile(gl.FRAGMENT_SHADER,fragment);
    gl.attachShader(p,vs);gl.attachShader(p,fs);gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p));
    const names=['Viewport','ViewAngles','Fov','CameraPosition','WorldWeather','Time','Seed','Quality','PreviewScene','PreviewTransparent','PreviewLayer','SceneSampler','DepthSampler','EffectSampler','LowSize','DepthTolerance','OutputDeltaEncoding','EnvironmentStage',
      'PhysicalObserverHeight','AtmosphereLutReady','AtmosphereSkyViewReady','AtmosphereLutSun','AtmosphereTransmittance','AtmosphereMultipleScattering','AtmosphereSkyViewSampler',
      'WorldPrecipOrigin','WorldPrecipPresence','WorldPrecipGeometry','WorldSurfaceGrid','WeatherVolumeSampler','SurfaceSampler',...Object.keys(PACKS),...Object.keys(COLORS)];
    return{program:p,uniforms:Object.fromEntries(names.map(n=>[n,gl.getUniformLocation(p,n)]))};
    }catch(error){gl.deleteProgram(p);throw error;}finally{if(vs)gl.deleteShader(vs);if(fs)gl.deleteShader(fs);}}
  const scenePrograms=new Map(),sceneFailures=new Map(),programBuilds=[];let disposed=false;
  function sceneProgram(style,blackHoleRoute) {
    if(disposed)throw new Error('预览渲染器已释放');
    if(!Number.isInteger(style)||style<0||style>7)throw new Error('无效巨型天体样式');
    if(!Number.isInteger(blackHoleRoute)||blackHoleRoute<0||blackHoleRoute>2)throw new Error('无效黑洞渲染路线');
    const key=`${style}:${blackHoleRoute}`;
    if(sceneFailures.has(key))throw sceneFailures.get(key);
    if(!scenePrograms.has(key)) {
      const start=performance.now();
      try{const compiled=program(specializeGiantStyle(sceneFragment,style,blackHoleRoute));
        scenePrograms.set(key,compiled);programBuilds.push({giantStyle:style,blackHoleRoute,cpuCompileMs:performance.now()-start});
      }catch(error){sceneFailures.set(key,error);throw error;}
    }
    return scenePrograms.get(key);
  }
  let environment,upsample,surface;
  try{environment=program(environmentFragment);upsample=program(upsampleFragment);surface=program(surfaceFragment);}
  catch(error){for(const p of [...scenePrograms.values(),environment,upsample,surface])if(p)gl.deleteProgram(p.program);
    scenePrograms.clear();sceneFailures.clear();luts.dispose();weather.dispose();throw error;}
  let target=null,effect=null,size=[0,0],effectSize=[0,0],lastState=null,lutError=null;
  function disposeTarget(t){if(!t)return;gl.deleteTexture(t.color);if(t.depth)gl.deleteTexture(t.depth);gl.deleteFramebuffer(t.framebuffer);}
  function texture(w,h,depth=false,hdr=false){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);
    gl.texImage2D(gl.TEXTURE_2D,0,depth?gl.DEPTH_COMPONENT24:hdr?gl.RGBA16F:gl.RGBA8,w,h,0,depth?gl.DEPTH_COMPONENT:gl.RGBA,depth?gl.UNSIGNED_INT:hdr?gl.HALF_FLOAT:gl.UNSIGNED_BYTE,null);
    for(const k of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,k,gl.NEAREST);
    for(const k of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,k,gl.CLAMP_TO_EDGE);return t;}
  function allocate(w,h,depth,hdr){const framebuffer=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,framebuffer);
    const color=texture(w,h,false,hdr);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,color,0);
    const d=depth?texture(w,h,true):null;if(d)gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.TEXTURE_2D,d,0);
    if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw new Error('环境颜色/深度缓冲不完整');return{framebuffer,color,depth:d};}
  function resize(w,h,scale){if(!target||size[0]!==w||size[1]!==h){disposeTarget(target);target=allocate(w,h,true,false);size=[w,h];}
    const ew=Math.max(1,Math.round(w*scale)),eh=Math.max(1,Math.round(h*scale));if(scale<1&&(!effect||effectSize[0]!==ew||effectSize[1]!==eh)){disposeTarget(effect);effect=allocate(ew,eh,false,floatTargets);effectSize=[ew,eh];}}
  function upload(p,state){gl.useProgram(p.program);const u=p.uniforms,v=state.values;
    gl.uniform2f(u.Viewport,...state.resolution);gl.uniform2f(u.ViewAngles,state.camera.yaw*Math.PI/180,state.camera.pitch*Math.PI/180);
    gl.uniform1f(u.Fov,state.camera.fov*Math.PI/180);gl.uniform3fv(u.CameraPosition,state.position);gl.uniform2fv(u.WorldWeather,state.gameWeather);
    gl.uniform1f(u.Time,state.time);gl.uniform1f(u.Seed,v.Seed);gl.uniform1i(u.Quality,v.Quality);gl.uniform1i(u.EnvironmentStage,0);
    gl.uniform1i(u.PreviewTransparent,state.transparentScene==='water'?2:state.transparentScene?1:0);
    for(const name of [...Object.keys(PACKS),...Object.keys(COLORS)])gl['uniform'+v[name].length+'fv'](u[name],v[name]);luts.bind(u,2);weather.bind(u,5);
  }
  function poll(){if(!timer)return;const disjoint=gl.getParameter(timer.GPU_DISJOINT_EXT);for(let i=queries.length-1;i>=0;i--){const q=queries[i];if(!gl.getQueryParameter(q.query,gl.QUERY_RESULT_AVAILABLE))continue;
    if(!disjoint)gpuMs[q.name]=gl.getQueryParameter(q.query,gl.QUERY_RESULT)/1e6;gl.deleteQuery(q.query);queries.splice(i,1);}}
  function draw(name){let q;if(timer&&queries.length<24&&!gl.getQuery(timer.TIME_ELAPSED_EXT,gl.CURRENT_QUERY)){q=gl.createQuery();gl.beginQuery(timer.TIME_ELAPSED_EXT,q);}gl.drawArrays(gl.TRIANGLES,0,3);if(q){gl.endQuery(timer.TIME_ELAPSED_EXT);queries.push({name,query:q});}}
  function inputs(p){gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,target.color);gl.uniform1i(p.uniforms.SceneSampler,0);
    gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,target.depth);gl.uniform1i(p.uniforms.DepthSampler,1);}
  return{render(state){const [w,h]=state.resolution,p=state.parameters,blackHoleRoute=Math.fround(p.blackHoleStrength)>0?p.blackHoleStyle:0,scene=sceneProgram(p.giantBodyStyle,blackHoleRoute);poll();
    try{luts.update(state);lutError=null;}catch(error){lutError=String(error.message||error);}weather.update(state);
    const rain=p.weatherFollowWorld?state.gameWeather[0]:p.rainAmount;
    const snowPresent=p.precipitationMode===3||(p.precipitationMode===1&&weather.telemetry().presence?.[1]>0);
    const protectSmallSnow=rain>0&&snowPresent&&p.snowBrightness>0&&p.snowSize<.04;
    const scale=protectSmallSnow?1:p.environmentRenderScale;
    const composite=state.scene||state.transparentScene||p.volumeCloudStrength>0||p.fogStrength>0||p.cloudShadowStrength>0||p.lightShaftStrength>0||rain>0;
    lastState={scene:state.scene,transparentScene:state.transparentScene||false,composition:p.nativeTransparentMode?'per-surface environment':'final opaque-depth environment',composite,resolution:[w,h],environmentResolution:scale<1?[Math.round(w*scale),Math.round(h*scale)]:[w,h],position:[...state.position],gameWeather:[...state.gameWeather],renderScale:scale,requestedRenderScale:p.environmentRenderScale,scaleProtection:protectSmallSnow?'细小雪花保持完整分辨率':null};
    gl.viewport(0,0,w,h);gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);if(composite)resize(w,h,scale);
    gl.bindFramebuffer(gl.FRAMEBUFFER,composite?target.framebuffer:null);upload(scene,state);gl.uniform1i(scene.uniforms.PreviewScene,state.scene?1:0);
    if(composite){gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.ALWAYS);gl.depthMask(true);gl.clearDepth(1);gl.clear(gl.DEPTH_BUFFER_BIT);}else gl.disable(gl.DEPTH_TEST);
    draw('skyAndScene');gl.disable(gl.DEPTH_TEST);gl.depthMask(false);
    const deltaEncoding=floatTargets?1:2;
    if(composite){gl.bindFramebuffer(gl.FRAMEBUFFER,scale<1?effect.framebuffer:null);if(scale<1)gl.viewport(0,0,...effectSize);upload(environment,state);inputs(environment);gl.uniform1i(environment.uniforms.OutputDeltaEncoding,scale<1?deltaEncoding:0);draw('environment');
      if(scale<1){gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,w,h);gl.useProgram(upsample.program);inputs(upsample);
        gl.activeTexture(gl.TEXTURE7);gl.bindTexture(gl.TEXTURE_2D,effect.color);gl.uniform1i(upsample.uniforms.EffectSampler,7);gl.uniform2f(upsample.uniforms.LowSize,...effectSize);gl.uniform1f(upsample.uniforms.DepthTolerance,p.environmentDepthTolerance);gl.uniform1i(upsample.uniforms.OutputDeltaEncoding,deltaEncoding);draw('upsample');}}
    if(state.transparentScene&&p.nativeTransparentMode){gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,w,h);upload(surface,state);inputs(surface);
      gl.enable(gl.BLEND);gl.blendEquation(gl.FUNC_ADD);gl.blendFuncSeparate(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);
      for(let i=0;i<(state.transparentScene==='water'?1:2);i++){gl.uniform1i(surface.uniforms.PreviewLayer,i);draw('transparentSurface'+i);}gl.disable(gl.BLEND);}
    gl.activeTexture(gl.TEXTURE0);
  },getTelemetry(){if(!disposed)poll();return{...structuredClone(lastState),atmosphere:luts.getTelemetry(),weather:weather.telemetry(),gpuMs:{...gpuMs},timerSupported:Boolean(timer),floatTargets,lutError,pendingQueries:queries.length,programBuilds:structuredClone(programBuilds),failedGiantStyles:[...new Set([...sceneFailures.keys()].map(key=>Number(key.split(':')[0])))],failedGiantRoutes:[...sceneFailures.keys()],disposed};},sources:()=>[...sources],dispose(){if(disposed)return;disposed=true;luts.dispose();weather.dispose();disposeTarget(target);disposeTarget(effect);target=effect=null;for(const p of [...scenePrograms.values(),environment,upsample,surface])gl.deleteProgram(p.program);scenePrograms.clear();sceneFailures.clear();for(const q of queries)gl.deleteQuery(q.query);queries.length=0;}};
}
