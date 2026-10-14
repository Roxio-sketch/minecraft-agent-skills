// Original GPU LUT lifecycle. Medium LUTs do not depend on the camera or light.
export const ATMOSPHERE_LUT_SPEC=Object.freeze({
  transmittance:{width:256,height:64,steps:64,format:'RGBA16F',bytes:131072},
  multiple:{width:32,height:32,directions:32,steps:24,format:'RGBA16F',bytes:8192},
  skyView:{width:512,height:128,halfWidth:256,steps:[24,40,64],format:'RGBA16F',bytes:524288},
  totalBytes:663552,
  samplerNames:['AtmosphereTransmittance','AtmosphereMultipleScattering','AtmosphereSkyViewSampler'],
});

const PASS_FUNCTIONS={transmittance:'apGenerateTransmittance',multiple:'apGenerateMultiple',skyView:'apGenerateSkyView'};
const PASSES={transmittance:'ATMOSPHERE_TRANSMITTANCE_PASS',multiple:'ATMOSPHERE_MULTIPLE_PASS',skyView:'ATMOSPHERE_SKY_VIEW_PASS'};
const PASS_UNIFORMS=['Quality','DaySettings','SunSettings','SunColor','SkyTint','AtmosphereGeometry','AtmosphereComposition','AtmospherePhysical','PhysicalObserverHeight','AtmosphereLutSun',
  'AtmosphereLutReady','AtmosphereSkyViewReady',...ATMOSPHERE_LUT_SPEC.samplerNames];

// This helper is also the contract for Forge's generated GLSL 150 wrappers.
// Native fullscreen vertices must supply clip-space coordinates themselves.
export function buildAtmospherePassFragment(physicalSource,pass,version='webgl2'){
  if(!PASS_FUNCTIONS[pass])throw new Error(`Unknown atmosphere pass ${pass}`);
  const header=version==='glsl150'?'#version 150\nout vec4 fragColor;':
    '#version 300 es\nprecision highp float;precision highp int;out vec4 fragColor;';
  return `${header}\n#define ${PASSES[pass]} 1\nuniform int Quality;\nuniform vec4 DaySettings;\nuniform vec4 SunSettings;\nuniform vec3 SunColor;\nuniform vec3 SkyTint;\nuniform vec4 AtmosphereGeometry;\nuniform vec4 AtmosphereComposition;\nuniform vec4 AtmospherePhysical;\n${physicalSource}\nvoid main(){fragColor=${PASS_FUNCTIONS[pass]}(gl_FragCoord.xy);}\n`;
}

export function createAtmosphereLuts(gl,sourcesOrPhysicalSource){
  const source=typeof sourcesOrPhysicalSource==='string'?sourcesOrPhysicalSource:
    sourcesOrPhysicalSource?.physical||sourcesOrPhysicalSource?.['atmosphere-physical.glsl'];
  if(typeof source!=='string')throw new Error('atmosphere-physical.glsl source is required');
  const extension=gl.getExtension('EXT_color_buffer_float');
  const timer=gl.getExtension('EXT_disjoint_timer_query_webgl2');
  const telemetry={supported:Boolean(extension),ready:false,skyViewReady:false,reason:extension?null:'缺少浮点颜色缓冲支持，使用解析天空',
    spec:structuredClone(ATMOSPHERE_LUT_SPEC),builds:{transmittance:0,multiple:0,skyView:0},cpuSubmitMs:{},gpuMs:{},pendingQueries:0,
    mediumKey:null,viewKey:null,observerHeightKm:0,sunElevationCosine:0};
  const vertex='#version 300 es\nprecision highp float;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}';
  const programs={},targets={},queries=[];
  let mediumKey=null,viewKey=null,lastState=null,disposed=false;
  function compile(type,text){const shader=gl.createShader(type);gl.shaderSource(shader,text);gl.compileShader(shader);
    if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS)){const error=gl.getShaderInfoLog(shader);gl.deleteShader(shader);throw new Error(error);}return shader;}
  function makeProgram(pass){const program=gl.createProgram(),vs=compile(gl.VERTEX_SHADER,vertex),fs=compile(gl.FRAGMENT_SHADER,buildAtmospherePassFragment(source,pass));
    gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);gl.deleteShader(vs);gl.deleteShader(fs);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS)){const error=gl.getProgramInfoLog(program);gl.deleteProgram(program);throw new Error(error);}
    return{program,uniforms:Object.fromEntries(PASS_UNIFORMS.map(name=>[name,gl.getUniformLocation(program,name)]))};}
  function allocate(name){const spec=ATMOSPHERE_LUT_SPEC[name],texture=gl.createTexture(),framebuffer=gl.createFramebuffer();
    gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA16F,spec.width,spec.height,0,gl.RGBA,gl.HALF_FLOAT,null);
    for(const key of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,key,gl.LINEAR);
    for(const key of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,key,gl.CLAMP_TO_EDGE);
    gl.bindFramebuffer(gl.FRAMEBUFFER,framebuffer);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);
    if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE){gl.deleteTexture(texture);gl.deleteFramebuffer(framebuffer);throw new Error(`${name} RGBA16F framebuffer incomplete`);}
    return{texture,framebuffer};}
  function poll(){if(!timer||queries.length===0)return;
    const disjoint=gl.getParameter(timer.GPU_DISJOINT_EXT);
    for(let i=queries.length-1;i>=0;i--){const item=queries[i];if(!gl.getQueryParameter(item.query,gl.QUERY_RESULT_AVAILABLE))continue;
      if(!disjoint)telemetry.gpuMs[item.pass]=gl.getQueryParameter(item.query,gl.QUERY_RESULT)/1e6;
      gl.deleteQuery(item.query);queries.splice(i,1);}
    telemetry.pendingQueries=queries.length;}
  function upload(p,state){const u=p.uniforms,v=state.values;gl.useProgram(p.program);
    gl.uniform1i(u.Quality,v.Quality);for(const name of ['DaySettings','SunSettings','AtmosphereGeometry','AtmosphereComposition','AtmospherePhysical'])gl.uniform4fv(u[name],v[name]);
    for(const name of ['SunColor','SkyTint'])gl.uniform3fv(u[name],v[name]);
    gl.uniform1f(u.PhysicalObserverHeight,state.observer);gl.uniform3fv(u.AtmosphereLutSun,state.sun);
    gl.uniform1i(u.AtmosphereLutReady,1);gl.uniform1i(u.AtmosphereSkyViewReady,0);
    for(let i=0;i<ATMOSPHERE_LUT_SPEC.samplerNames.length;i++){gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,targets[['transmittance','multiple','skyView'][i]]?.texture||null);gl.uniform1i(u[ATMOSPHERE_LUT_SPEC.samplerNames[i]],i);}}
  function draw(pass,state){const spec=ATMOSPHERE_LUT_SPEC[pass];upload(programs[pass],state);gl.bindFramebuffer(gl.FRAMEBUFFER,targets[pass].framebuffer);gl.viewport(0,0,spec.width,spec.height);
    gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);gl.depthMask(false);
    let query;if(timer&&queries.length<32&&!gl.getQuery(timer.TIME_ELAPSED_EXT,gl.CURRENT_QUERY)){query=gl.createQuery();gl.beginQuery(timer.TIME_ELAPSED_EXT,query);}
    const started=performance.now();gl.drawArrays(gl.TRIANGLES,0,3);telemetry.cpuSubmitMs[pass]=performance.now()-started;
    if(query){gl.endQuery(timer.TIME_ELAPSED_EXT);queries.push({pass,query});}
    telemetry.builds[pass]++;}
  function snapshot(){const activeTexture=gl.getParameter(gl.ACTIVE_TEXTURE),units=[gl.TEXTURE0,gl.TEXTURE1,gl.TEXTURE2];if(!units.includes(activeTexture))units.push(activeTexture);
    const textures=units.map(unit=>{gl.activeTexture(unit);return[unit,gl.getParameter(gl.TEXTURE_BINDING_2D)];});gl.activeTexture(activeTexture);
    return{framebuffer:gl.getParameter(gl.FRAMEBUFFER_BINDING),viewport:gl.getParameter(gl.VIEWPORT),program:gl.getParameter(gl.CURRENT_PROGRAM),textures,
    activeTexture,depth:gl.isEnabled(gl.DEPTH_TEST),blend:gl.isEnabled(gl.BLEND),cull:gl.isEnabled(gl.CULL_FACE),depthMask:gl.getParameter(gl.DEPTH_WRITEMASK)};}
  function restore(s){gl.bindFramebuffer(gl.FRAMEBUFFER,s.framebuffer);gl.viewport(...s.viewport);gl.useProgram(s.program);for(const [unit,texture]of s.textures){gl.activeTexture(unit);gl.bindTexture(gl.TEXTURE_2D,texture);}gl.activeTexture(s.activeTexture);gl.depthMask(s.depthMask);
    for(const [cap,value]of[[gl.DEPTH_TEST,s.depth],[gl.BLEND,s.blend],[gl.CULL_FACE,s.cull]])value?gl.enable(cap):gl.disable(cap);}
  function valuesFromParameters(p){return{Quality:p.quality??2,DaySettings:[p.cycleSpeed??0,p.mieAnisotropy??.8,p.nightAmbient??.04,p.twilightStrength??1],
    SunSettings:[p.sunBrightness??1,(p.sunElevation??30)*Math.PI/180,(p.sunYaw??0)*Math.PI/180,(p.sunSize??.5)*Math.PI/180],
    SunColor:[1,1,1],SkyTint:[1,1,1],AtmosphereGeometry:[p.planetRadius??6360,p.atmosphereHeight??100,p.rayleighHeight??8,p.mieHeight??1.2],
    AtmosphereComposition:[p.rayleighDensity??1,p.mieDensity??1,p.ozoneDensity??1,p.groundAlbedo??.1],
    AtmospherePhysical:[p.atmosphereStyle??1,p.observerAltitude??.07,p.multipleScattering??1,p.groundLevel??64]};}
  return{
    update({parameters={},values,position=[0,64,0],time=0,sun}){
      if(disposed)throw new Error('Atmosphere LUTs have been disposed');poll();
      const v=values||valuesFromParameters(parameters);
      const observer=Math.max(v.AtmospherePhysical[1]+((position[1]??64)-v.AtmospherePhysical[3])*.001,0.001);
      const solarPhase=v.SunSettings[1]+time*v.DaySettings[0]*.035;
      const sunMu=sun?Math.max(-1,Math.min(1,sun[1])):Math.sin(solarPhase);
      // A canonical horizontal solar direction makes yaw changes pure lookups.
      const solar=[0,sunMu,Math.sqrt(Math.max(1-sunMu*sunMu,0))];
      lastState={values:v,observer,sun:solar};telemetry.observerHeightKm=observer;telemetry.sunElevationCosine=sunMu;
      if(v.AtmospherePhysical[0]!==2||!extension){telemetry.ready=false;telemetry.skyViewReady=false;return false;}
      const nextMedium=JSON.stringify([v.AtmosphereGeometry,v.AtmosphereComposition,v.DaySettings[1]]);
      const nextView=JSON.stringify([nextMedium,observer,sunMu,v.Quality,v.AtmospherePhysical[2],v.SunSettings[0],v.SunColor,v.SkyTint]);
      if(nextMedium===mediumKey&&nextView===viewKey){telemetry.ready=true;telemetry.skyViewReady=true;return false;}
      const saved=snapshot();try{
        if(!programs.transmittance)for(const name of ['transmittance','multiple','skyView']){programs[name]=makeProgram(name);targets[name]=allocate(name);}
        if(nextMedium!==mediumKey){draw('transmittance',lastState);draw('multiple',lastState);mediumKey=nextMedium;}
        draw('skyView',lastState);viewKey=nextView;telemetry.ready=true;telemetry.skyViewReady=true;telemetry.reason=null;
        telemetry.mediumKey=mediumKey;telemetry.viewKey=viewKey;
      }catch(error){telemetry.ready=false;telemetry.skyViewReady=false;telemetry.reason=String(error?.message||error);throw error;}
      finally{restore(saved);poll();}
      return true;
    },
    bind(locations,firstTextureUnit=0){
      gl.uniform1i(locations.AtmosphereLutReady??null,telemetry.ready?1:0);gl.uniform1i(locations.AtmosphereSkyViewReady??null,telemetry.skyViewReady?1:0);
      gl.uniform1f(locations.PhysicalObserverHeight??null,lastState?.observer??.07);
      for(let i=0;i<ATMOSPHERE_LUT_SPEC.samplerNames.length;i++){gl.activeTexture(gl.TEXTURE0+firstTextureUnit+i);gl.bindTexture(gl.TEXTURE_2D,targets[['transmittance','multiple','skyView'][i]]?.texture||null);
        gl.uniform1i(locations[ATMOSPHERE_LUT_SPEC.samplerNames[i]]??null,firstTextureUnit+i);}
      return ATMOSPHERE_LUT_SPEC.samplerNames.length;
    },
    getTelemetry(){poll();return structuredClone(telemetry);},
    // Explicit diagnostics only; no per-frame readback is done by the renderer.
    getTargets(){return Object.fromEntries(Object.entries(targets).map(([name,target])=>[name,{...target,...ATMOSPHERE_LUT_SPEC[name]}]));},
    dispose(){if(disposed)return;disposed=true;for(const item of Object.values(targets)){gl.deleteTexture(item.texture);gl.deleteFramebuffer(item.framebuffer);}for(const item of Object.values(programs))gl.deleteProgram(item.program);for(const item of queries)gl.deleteQuery(item.query);queries.length=0;}
  };
}
