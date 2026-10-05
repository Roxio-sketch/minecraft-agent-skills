'use strict';
(async () => {
  const $ = id => document.getElementById(id);
  try {
    const load = async name => {const r=await fetch(name);if(!r.ok)throw Error(`${name}: ${r.status}`);return r;};
    const [kernel, catalog,layers] = await Promise.all([(await load('black-hole.glsl')).text(),(await load('recipes.json')).json(),window.blackHoleLayers]);
    const bounds = {
      core:[0.2,0.95,0.01,'暗核半径'],inner:[0.55,3,0.01,'盘内半径'],outer:[2,8,0.05,'盘外半径'],viewDistance:[5,24,.01,'观察距离'],
      thickness:[0.045,0.35,0.005,'盘厚度'],tilt:[-90,90,1,'盘倾角'],bend:[0,2,0.005,'偏折强度'],lensRadius:[.2,16,.05,'背景透镜范围'],
      density:[0,10,0.1,'盘密度'],speed:[0,3,0.005,'旋转速度'],infallSpeed:[0,2,0.01,'向内流速'],seed:[0,65535,1,'固定种子'],exposure:[0.01,2.5,0.01,'曝光'],
      particleCount:[0,4096,1,'粒子数量'],particleSpeed:[0,4,.05,'粒子速度'],particleSize:[.005,.15,.001,'粒子尺寸'],particleOuter:[3.2,8,.1,'补给半径'],particleBrightness:[0,5,.01,'粒子亮度'],
      halo:[0,3,.01,'光晕总量'],haloRing:[0,3,.01,'内缘亮环'],haloDisk:[0,3,.01,'盘侧辐射'],haloAura:[0,3,.01,'外围柔光'],
      jetStrength:[0,5,.005,'极轴喷流强度'],jetLength:[.5,16,.05,'喷流长度'],jetWidth:[.01,2,.005,'喷流宽度'],
      haloRadius:[.1,4,.005,'光圈半径倍率'],haloWidth:[.001,1,.005,'光圈宽度'],haloSharpness:[.1,12,.1,'光圈边缘锐度'],haloGradientMix:[0,1,.005,'光圈渐变力度'],
      haloDiskGradientMix:[0,1,.005,'盘侧渐变力度'],haloAuraGradientMix:[0,1,.005,'外围渐变力度'],
      diskGradientPosition:[0,1,.005,'盘中段位置（0＝旧配色）'],coreGradientStrength:[0,1,.005,'核心边缘渐变'],particleGradientPosition:[0,1,.005,'粒子中段位置（0＝旧配色）'],skyGradientPosition:[0,1,.005,'背景渐变位置（0＝单色）'],
      bandDetail:[1,24,1,'光带层数'],dustScale:[.1,12,.1,'尘埃细度'],dustFill:[0,1,.025,'尘埃覆盖'],backgroundSpin:[-1,1,.01,'星域旋转'],orbitSpeed:[-0.3,.3,.01,'观察环绕'],
      filament:[0,3,.005,'等离子细丝'],spiral:[0,3,.005,'螺旋结构'],beaming:[0,3,.005,'盘面方向明暗'],bloom:[0,2,.005,'镜头柔光'],bloomRadius:[.5,3,.05,'柔光范围'],skyStrength:[0,2,.005,'背景亮度'],lensBackground:[0,1,.005,'背景透镜强度']
    };
    const legacyLayers={diskStyle:'classic',particles:false,particleCount:640,particleSpeed:1,particleSize:.022,particleOuter:5.4,particleBrightness:1.25,particleCold:'#f26318',particleMid:'#ffae69',particleHot:'#ffefb1',particleGradientPosition:0,halo:0,haloRing:1,haloDisk:1,haloAura:1,haloRadius:1,haloWidth:.11,haloSharpness:2,haloGradientStart:'#ffd697',haloGradientEnd:'#8853ca',haloGradientMix:0,haloDiskInnerColor:'#ffdfad',haloDiskOuterColor:'#a15add',haloDiskGradientMix:0,haloAuraGradientMix:0,haloAuraInnerColor:'#f0b070',haloAuraOuterColor:'#516bc4',jetStrength:0,jetLength:5.5,jetWidth:.24,jetBaseColor:'#8bd8ff',jetTipColor:'#6852e3',bandDetail:12,dustScale:1,dustFill:.7,backgroundSpin:0,orbitSpeed:0,viewDistance:7.8,backgroundStyle:'stars',skyColor:'#314866',skyColorFar:'#172a56',skyGradientPosition:0,diskMid:'#e39a68',diskGradientPosition:0,coreEdgeColor:'#000000',coreGradientStrength:0,filament:0,spiral:0,beaming:0,bloom:0,bloomRadius:1,skyStrength:1,lensBackground:1,lensRadius:12,lensEnabled:true};
    // Keep the landing view on one of the four public algorithm families.  The
    // catalog still carries older compatibility recipes for imports and saved
    // URLs, but an unqualified workbench URL should never open on a hidden
    // legacy variant.
    const defaultRecipe=catalog.recipes.find(r=>r.name==='体积尘埃 · BlackHole 研究')??catalog.recipes[0];
    const defaults={coreColor:'#000000',infallSpeed:0,...legacyLayers,...defaultRecipe};
    const MAX_TIME=100000;
    const colorKeys=['hot','diskMid','cool','coreColor','coreEdgeColor','particleCold','particleMid','particleHot','haloGradientStart','haloGradientEnd','haloDiskInnerColor','haloDiskOuterColor','haloAuraInnerColor','haloAuraOuterColor','jetBaseColor','jetTipColor','skyColor','skyColorFar'];
    const parameterKeys=['name',...Object.keys(bounds),...colorKeys,'steps','yaw','pitch','mode','diskStyle','particles','backgroundStyle','lensEnabled'];
    function assertKeys(input,allowed,label) {
      if(!input || typeof input!=='object' || Array.isArray(input))throw Error(`${label}必须为对象`);
      const unknown=Object.keys(input).filter(k=>!allowed.includes(k));
      if(unknown.length)throw Error(`${label}包含未知字段：${unknown.join(', ')}`);
    }
    function validateTime(t) {
      if(typeof t!=='number'||!Number.isFinite(t)||t<0||t>MAX_TIME)throw Error(`时间必须在 0–${MAX_TIME} 秒之间`);
      return t;
    }
    function validate(input) {
      assertKeys(input,parameterKeys,'配方参数');
      // Old v1 recipes had no radial motion: omission preserves their appearance.
      const s={...defaults,...legacyLayers,...input,infallSpeed:Object.hasOwn(input,'infallSpeed')?input.infallSpeed:0};
      for(const [k,[min,max]] of Object.entries(bounds)) {
        if(typeof s[k]!=='number'||!Number.isFinite(s[k])||s[k]<min||s[k]>max)throw Error(`${k} 必须位于 ${min}–${max}`);
      }
      if(!Number.isInteger(s.seed))throw Error('seed 必须是整数');
      if(!Number.isInteger(s.particleCount))throw Error('particleCount 必须是整数');
      if(typeof s.particles!=='boolean')throw Error('particles 必须为布尔值');
      if(typeof s.lensEnabled!=='boolean')throw Error('lensEnabled 必须为布尔值');
      if(!['classic','granular','ribbons','dust','cinematic'].includes(s.diskStyle))throw Error('未知盘面结构');
      if(!['stars','nebula','black'].includes(s.backgroundStyle))throw Error('未知背景结构');
      if(!Number.isInteger(s.bandDetail))throw Error('光带层数必须是整数');
      if(s.inner<s.core+0.12)throw Error('盘内半径必须至少比暗核大 0.12');
      if(s.outer<s.inner+0.5)throw Error('盘外半径必须至少比内半径大 0.5');
      if(![96,160,256].includes(s.steps))throw Error('质量必须是 96、160 或 256');
      for(const k of colorKeys)if(typeof s[k]!=='string'||!/^#[0-9a-f]{6}$/i.test(s[k]))throw Error(`${k} 颜色必须是 #RRGGBB`);
      for(const k of ['yaw','pitch'])if(typeof s[k]!=='number'||!Number.isFinite(s[k]))throw Error('观察角度必须为有限数字');
      if(Math.abs(s.pitch)>1.3||Math.abs(s.yaw)>Math.PI)throw Error('观察角度超出预览范围');
      if(typeof s.name!=='string'||s.name.length>120)throw Error('配方名称过长');
      if(!['scene','blade'].includes(s.mode??'scene'))throw Error('不支持的视图');
      return Object.fromEntries(parameterKeys.map(k=>[k,k==='mode'?(s[k]??'scene'):s[k]]));
    }
    catalog.recipes=catalog.recipes.map(validate);
    const requestedStyle=new URLSearchParams(location.search).get('preset');
    const requestedName=requestedStyle==='unity'?'层叠光带 · Unity 研究':requestedStyle==='gradient'?'虹彩视界 · 全域渐变':null;
    const granularName='颗粒吸积 · 完整组合';
    const presetPreviewTime=name=>name===granularName?validateTime(catalog.previewTimes?.[name]??1.7):1.7;
    let state=validate(catalog.recipes.find(r=>r.name===requestedName)??catalog.recipes.find(r=>r.diskStyle===requestedStyle)??defaults), time=presetPreviewTime(state.name), playing=false, last=performance.now(), lastDraw=0, uiError='';
    let resetView={yaw:state.yaw,pitch:state.pitch};
    function setPlaying(value){playing=Boolean(value)&&time<MAX_TIME;last=performance.now();$('play').textContent=playing?'暂停':'播放';}
    const query=new URLSearchParams(location.search);
    const solver=query.get('solver')==='verlet'?'verlet':'artistic';
    const solverDefine=solver==='verlet'?'#define BH_USE_GEODESIC_VERLET 1\n':'';
    const vert=`#version 300 es
precision highp float;
out vec2 vUV;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));vUV=p*2.0-1.0;gl_Position=vec4(vUV,0,1);}`;
    const frag=`#version 300 es
precision highp float;
precision highp int;
in vec2 vUV; out vec4 color;
uniform vec2 uResolution; uniform float uYaw,uPitch,uExposure,uBackgroundPhase,uViewDistance,uSkyStrength,uLensBackground,uLensRadius,uSkyGradientPosition;
uniform bool uLensEnabled;
uniform vec3 uSkyColor,uSkyColorFar;
uniform int uMode,uDebug,uBackgroundMode;
float backgroundFootprint=0.0;
#define BH_BACKGROUND_FOOTPRINT backgroundFootprint
${solverDefine}${layers.header}
${kernel}
${layers.functions}
// A direction-space deep field inspired by the supplied observatory study:
// broad Milky-Way structure, locally textured nebulae and dust lanes.  It is
// evaluated after ray bending, so the scene remains a background field rather
// than a screen-space decal.  High-frequency terms are footprint filtered.
vec3 deepFieldPatch(vec3 d,vec3 center,float radius,vec3 cool,vec3 warm,
                    float coarse,float medium,float fine){
 float angular=max(0.0,1.0-dot(d,center));
 float envelope=exp(-angular/max(.001,radius*radius*.52));
 if(envelope<.002)return vec3(0.0);
 // Reuse three footprint-filtered all-sky fields. Sampling four more noise
 // octaves per patch made the ray-traced preview prohibitively expensive.
 float body=clamp(.27*coarse+.53*medium+.20*fine,0.0,1.0);
 float filament=1.0-abs(2.0*fine-1.0);
 float wisps=smoothstep(.51,.78,body)*(.32+.68*smoothstep(.42,.88,filament));
 float holes=smoothstep(.58,.84,medium);
 vec3 tint=mix(cool,warm,smoothstep(.46,.82,body));
 return tint*envelope*wisps*(1.0-.5*holes);
}
vec3 previewBackground(vec3 d){
 float screenRadius=clamp(length(vUV*vec2(uResolution.x/uResolution.y,1.0))/1.65,0.0,1.0);
 float gradientMix=uSkyGradientPosition<=.001?0.0:smoothstep(max(.0,uSkyGradientPosition-.25),min(1.0,uSkyGradientPosition+.25),screenRadius);
 vec3 skyColor=mix(uSkyColor,uSkyColorFar,gradientMix);
 if(uBackgroundMode==0){
   float brightness=max(dot(skyColor,vec3(.2126,.7152,.0722)),.01);
   return bhBackground(d)*mix(vec3(1.0),skyColor/brightness,.45);
 }
 if(uBackgroundMode==2)return vec3(0);
 // Three angular scales cover the full sky. The last two fade to their mean
 // when the lensed ray footprint grows, so no unresolved texture is wrapped
 // around the critical curve.
 float coarse=volumeNoise(d*3.3+vec3(uSeed*.07,5.0,19.0),vec3(128.0));
 float mediumResolution=exp(-pow(backgroundFootprint*8.5,2.0));
 float fineResolution=exp(-pow(backgroundFootprint*19.0,2.0));
 float medium=mix(.5,volumeNoise(d*8.5+vec3(29.0,uSeed*.11,7.0),vec3(128.0)),mediumResolution);
 float fine=mix(.5,volumeNoise(d*19.0+vec3(11.0,31.0,uSeed*.17),vec3(128.0)),fineResolution);
 float structure=.46*coarse+.38*medium+.16*fine;
 // A slanted, all-direction stellar river. Its broad wings reach the edges of
 // the camera frustum, while the narrow heart and dark lane give it depth.
 float latitude=dot(d,normalize(vec3(.46,.84,.18)))
     +.085*(coarse-.5)+.026*(medium-.5);
 float river=exp(-pow(latitude/.23,2.0));
 float outskirts=exp(-pow(latitude/.57,2.0));
 float pockets=smoothstep(.31,.66,structure);
 float darkLane=exp(-pow((latitude+.018*(medium-.5))/.053,2.0))
     *smoothstep(.32,.72,medium);
 // Four overlapping patches keep the sky dimensional when the camera zooms
 // far out.  Their palette is derived from the recipe colors, with a restrained
 // warm bias so background changes remain visible without competing with disk
 // emission.
 // A zero gradient position keeps legacy single-colour recipes independent
 // of the unused distant colour endpoint.
 vec3 farTint=uSkyGradientPosition<=.001?skyColor:mix(uSkyColor,uSkyColorFar,.5);
 vec3 coolTint=mix(skyColor,farTint,.58);
 vec3 warmTint=mix(skyColor,mix(vec3(.58,.22,.11),farTint,.48),.31);
 vec3 patches=deepFieldPatch(d,normalize(vec3(-.66,.19,-.76)),.48,coolTint,warmTint,coarse,medium,fine)
    +deepFieldPatch(d,normalize(vec3(.72,-.31,-.68)),.53,farTint,coolTint,medium,fine,coarse)
    +deepFieldPatch(d,normalize(vec3(.08,.58,.81)),.45,coolTint,mix(coolTint,warmTint,.5),fine,coarse,medium)
    +deepFieldPatch(d,normalize(vec3(.86,.14,.42)),.38,warmTint,coolTint,coarse,fine,medium);
 vec3 stars=bhStars(d,backgroundFootprint)*(1.0-.58*darkLane);
 vec3 nebula=skyColor*(.055+.34*outskirts+1.28*river*(.22+1.35*pockets));
 nebula+=patches*(.40+.66*river);
 nebula+=farTint*(.11*outskirts);
 nebula*=1.0-.76*darkLane;
 float farAmount=uSkyGradientPosition<=.001?0.0:clamp(.5+.5*d.y,0.0,1.0);
 farTint=mix(uSkyColor,uSkyColorFar,farAmount);
 nebula+=farTint*(.035+.12*outskirts);
 return vec3(.0005,.0008,.002)+nebula+stars*(.55+.55*river+.35*pockets);
}
void main(){
 vec2 uv=vUV; uv.x*=uResolution.x/uResolution.y;
 if(uMode==1){
   float width=0.24*clamp((0.85-uv.y)/0.23,0.0,1.0);
   bool metal=abs(uv.x)<width+0.028 && uv.y>-.84 && uv.y<.89;
   bool inside=abs(uv.x)<width && uv.y>-.8 && uv.y<.85;
   if(!inside){color=vec4(metal?vec3(.26,.3,.36):vec3(.035,.045,.065),1);return;}
 }
 vec3 origin=uViewDistance*vec3(sin(uYaw)*cos(uPitch),sin(uPitch),cos(uYaw)*cos(uPitch));
 vec3 forward=normalize(-origin), right=normalize(cross(forward,vec3(0,1,0))), up=cross(right,forward);
 vec3 direction=normalize(forward+(right*uv.x+up*uv.y)*0.47);
 BHResult hit=bhTrace(origin,direction);
 if(uDebug==1){color=vec4(hit.captured,hit.exhausted,hit.transmission,1);return;}
 if(uDebug==2){color=vec4(hit.light,1);return;}
 if(uDebug==4){color=vec4(vec3(clamp(hit.closestRadius/(4.0*uCore),0.0,1.0)),1);return;}
 float cs=cos(uBackgroundPhase),sn=sin(uBackgroundPhase);
 vec3 baseD=vec3(cs*direction.x-sn*direction.z,direction.y,sn*direction.x+cs*direction.z);
 vec3 d=hit.direction;d=vec3(cs*d.x-sn*d.z,d.y,sn*d.x+cs*d.z);
 // Keep derivatives outside divergent control flow, then stop opaque rays
 // before sampling the sky. Multiplying an invalid sky value by zero can still
 // yield NaN and leave tile-shaped artifacts inside the event horizon.
 float baseFootprint=max(length(dFdx(baseD)),length(dFdy(baseD)));
 float lensedFootprint=max(length(dFdx(d)),length(dFdy(d)));
 float skyTransmission=hit.captured>0.5||hit.exhausted>0.5?0.0:hit.transmission;
 if(skyTransmission<=0.00001){color=vec4(uDebug==3?vec3(0):hit.light,hit.captured>0.5?0.0:1.0);return;}
 backgroundFootprint=clamp(baseFootprint,0.00001,0.5);
 vec3 baseBackground=previewBackground(baseD);
 backgroundFootprint=clamp(lensedFootprint,0.00001,0.5);
 vec3 lensedBackground=previewBackground(d);
 // A budget-exhausted path has no resolved background direction. Retain its
 // foreground emission and diagnostic status, without inventing escaping light.
 float impact=length(cross(origin,direction));
 float lensFade=1.0-smoothstep(uLensRadius*.65,uLensRadius,impact);
 float lensMix=uLensEnabled?uLensBackground*lensFade:0.0;
 vec3 background=uSkyStrength*mix(baseBackground,lensedBackground,lensMix);
 color=vec4((uDebug==3?vec3(0):hit.light)+skyTransmission*background,1);
}`;
    const linear = hex => [1,3,5].map(i=>{const s=parseInt(hex.slice(i,i+2),16)/255;return s<=0.04045?s/12.92:Math.pow((s+0.055)/1.055,2.4);});
    function makeRenderer(canvas) {
      const gl=canvas.getContext('webgl2',{alpha:false,antialias:false,preserveDrawingBuffer:true});
      if(!gl)throw Error('此浏览器不支持 WebGL 2');
      gl.disable(gl.DITHER);
      const compile=(type,code)=>{const s=gl.createShader(type);gl.shaderSource(s,code);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
      const p=gl.createProgram(),vs=compile(gl.VERTEX_SHADER,vert),fs=compile(gl.FRAGMENT_SHADER,frag);
      gl.attachShader(p,vs);gl.attachShader(p,fs);gl.linkProgram(p);
      if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));
      gl.deleteShader(vs);gl.deleteShader(fs);gl.useProgram(p);
      const post=window.createBlackHolePost(gl);
      const loc={};const L=n=>loc[n]??(loc[n]=gl.getUniformLocation(p,n));
      const drawParticles=layers.particles(gl,kernel,linear);
      // Own the render attachment instead of depending on the composited default buffer.
      const target=gl.createFramebuffer(),colorBuffer=gl.createRenderbuffer();
      const sampleTarget=gl.createFramebuffer(),sampleTexture=gl.createTexture();
      let targetWidth=0,targetHeight=0,sampleWidth=0,sampleHeight=0,lastRenderKey='';
      function bindTarget(scale) {
        gl.bindFramebuffer(gl.FRAMEBUFFER,target);
        if(targetWidth!==canvas.width||targetHeight!==canvas.height) {
          gl.bindRenderbuffer(gl.RENDERBUFFER,colorBuffer);
          gl.renderbufferStorage(gl.RENDERBUFFER,gl.RGBA8,canvas.width,canvas.height);
          gl.framebufferRenderbuffer(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.RENDERBUFFER,colorBuffer);
          if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('预览颜色缓冲不完整');
          targetWidth=canvas.width;targetHeight=canvas.height;
        }
        gl.bindFramebuffer(gl.FRAMEBUFFER,sampleTarget);
        if(sampleWidth!==canvas.width*scale||sampleHeight!==canvas.height*scale){
          sampleWidth=canvas.width*scale;sampleHeight=canvas.height*scale;
          gl.bindTexture(gl.TEXTURE_2D,sampleTexture);
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
          gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA16F,sampleWidth,sampleHeight,0,gl.RGBA,gl.HALF_FLOAT,null);
          gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,sampleTexture,0);
          if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('预览采样缓冲不完整');
        }
      }
      function render(s,t,debug=0) {
        const stationary=s.speed===0&&s.infallSpeed===0&&s.backgroundSpin===0&&s.orbitSpeed===0&&(!s.particles||s.particleSpeed===0);
        const renderKey=JSON.stringify([s,stationary?0:t,debug,canvas.width,canvas.height]);
        // Paused/zero-motion frames reuse their exact pixels; a UI clock update is
        // not a material change. Resize, diagnostics and every parameter invalidate it.
        if(renderKey===lastRenderKey)return;
        // Four spatial samples resolve subpixel critical images. Diagnostics stay
        // single-sample so capture/exhaustion flags remain directly measurable.
        const hdr=debug!==1;
        bindTarget(hdr&&['ribbons','dust','cinematic'].includes(s.diskStyle)?2:1);
        gl.bindFramebuffer(gl.FRAMEBUFFER,hdr?sampleTarget:target);
        gl.useProgram(p);gl.viewport(0,0,hdr?sampleWidth:canvas.width,hdr?sampleHeight:canvas.height);
        for(const k of Object.keys(bounds)) {
          if(k==='exposure'||k==='infallSpeed')continue;
          gl.uniform1f(L('u'+k[0].toUpperCase()+k.slice(1)),k==='skyStrength'&&debug===2?0:s[k]);
        }
        // With all time motion disabled, keep GPU inputs identical as UI time changes.
        const phase=(v,n)=>((v%n)+n)%n,clock=t*Math.abs(s.speed),tau=Math.PI*2;
        const cameraYaw=phase(s.yaw+t*s.orbitSpeed,tau);
        gl.uniform1f(L('uTime'),stationary?0:t);gl.uniform1f(L('uInfall'),s.infallSpeed);gl.uniform1f(L('uYaw'),cameraYaw);gl.uniform1f(L('uPitch'),s.pitch);
        for(const [n,v] of Object.entries({uOrbitPhase:phase(t*s.speed,tau),uRadialPhase:phase(t*s.infallSpeed,128),uClassicRadialPhase:phase(t*s.infallSpeed,tau),uShearLocal:phase(clock,8),uShearParity:Math.floor(clock/8)%2,uDustDriftA:phase(t*s.speed*.35,12),uDustDriftB:phase(-t*s.speed*.25,12)}))gl.uniform1f(L(n),stationary?0:v);
        gl.uniform1f(L('uBackgroundPhase'),phase(t*s.backgroundSpin,tau));
        gl.uniform1i(L('uBackgroundMode'),{stars:0,nebula:1,black:2}[s.backgroundStyle]);
        gl.uniform1i(L('uLensEnabled'),s.lensEnabled?1:0);
        gl.uniform1i(L('uSteps'),s.steps);gl.uniform1i(L('uMode'),s.mode==='blade'?1:0);gl.uniform1i(L('uDebug'),debug);
        for(const [name,key] of Object.entries({uHot:'hot',uCool:'cool',uDiskMid:'diskMid',uCoreColor:'coreColor',uCoreEdgeColor:'coreEdgeColor',uSkyColor:'skyColor',uSkyColorFar:'skyColorFar',uHaloGradientStart:'haloGradientStart',uHaloGradientEnd:'haloGradientEnd',uHaloDiskInnerColor:'haloDiskInnerColor',uHaloDiskOuterColor:'haloDiskOuterColor',uHaloAuraInnerColor:'haloAuraInnerColor',uHaloAuraOuterColor:'haloAuraOuterColor',uJetBaseColor:'jetBaseColor',uJetTipColor:'jetTipColor'}))gl.uniform3fv(L(name),linear(s[key]));
        gl.uniform2f(L('uResolution'),sampleWidth,sampleHeight);
        gl.uniform1i(L('uGranular'),layers.styleIndex(s));
        for(const [n,v] of Object.entries({HaloStrength:s.halo,HaloRing:s.haloRing,HaloDisk:s.haloDisk,HaloAura:s.haloAura,JetStrength:s.jetStrength,JetLength:s.jetLength,JetWidth:s.jetWidth}))gl.uniform1f(L(n),v);
        gl.drawArrays(gl.TRIANGLES,0,3);
        if(debug===0)drawParticles({...s,yaw:cameraYaw},stationary?0:t,sampleWidth,sampleHeight);
        if(hdr)post.render(sampleTexture,sampleWidth,sampleHeight,{exposure:s.exposure,bloom:s.bloom,bloomRadius:s.bloomRadius,maskBloomWithSourceAlpha:true,outputWidth:canvas.width,outputHeight:canvas.height},target);
        gl.bindFramebuffer(gl.READ_FRAMEBUFFER,target);gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,null);
        gl.blitFramebuffer(0,0,canvas.width,canvas.height,0,0,canvas.width,canvas.height,gl.COLOR_BUFFER_BIT,gl.NEAREST);
        gl.bindFramebuffer(gl.FRAMEBUFFER,null);
        lastRenderKey=renderKey;
      }
      return {canvas,gl,render,pixels:()=>{const v=new Uint8Array(canvas.width*canvas.height*4);gl.bindFramebuffer(gl.READ_FRAMEBUFFER,target);gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,v);gl.bindFramebuffer(gl.READ_FRAMEBUFFER,null);return v;}};
    }
    const renderers=['scene','small16','small32'].map(id=>makeRenderer($(id)));
    let diagnostic=0;
    function draw(){renderers.forEach((r,i)=>r.render(state,time,i===0?diagnostic:0));$('status').textContent=uiError||`${state.name} · ${state.steps} 步 · 时间 ${time.toFixed(2)} s · WebGL 2 预览${solver==='verlet'?' · Verlet 研究求解器':''}${time===MAX_TIME?' · 已到时间上限，请调低时间后继续播放':''}`;}
    function ui(){for(const k of Object.keys(bounds)){$(k).value=state[k];$(`${k}Value`).textContent=state[k];}for(const k of [...colorKeys,'steps','mode','diskStyle','backgroundStyle'])$(k).value=state[k];$('particles').checked=state.particles;$('lensEnabled').checked=state.lensEnabled;$('time').value=time.toFixed(2);}
    function change(patch){try{state=validate({...state,...patch});uiError='';ui();draw();}catch(e){uiError=e.message;$('status').textContent=uiError;ui();}}
    const compositionKeys=new Set(['core','inner','outer','viewDistance','thickness','tilt','bend','lensRadius','lensBackground','coreGradientStrength']);
    const motionKeys=new Set(['speed','infallSpeed','backgroundSpin','orbitSpeed']);
    const postKeys=new Set(['exposure','bloom','bloomRadius','skyStrength']);
    const skyKeys=new Set(['skyGradientPosition']);
    for(const [key,[min,max,step,label]] of Object.entries(bounds)) {
      const row=document.createElement('label');row.textContent=label;
      const input=document.createElement('input');input.type='range';input.id=key;input.min=min;input.max=max;input.step=step;
      const out=document.createElement('output');out.id=key+'Value';row.append(input,out);
      const container=key.startsWith('particle')?'particleControls':key.startsWith('halo')?'haloControls':key.startsWith('jet')?'jetControls':compositionKeys.has(key)?'compositionControls':motionKeys.has(key)?'motionControls':skyKeys.has(key)?'skyControls':postKeys.has(key)?'postControls':'controls';
      $(container).append(row);
      input.oninput=()=>change({[key]:Number(input.value)});
    }
    // Keep the public catalog focused on the four distinct algorithms.  Older
    // color/compatibility variants remain in recipes.json so saved URLs and
    // imported recipes continue to resolve, but they are intentionally hidden
    // from the main selector.
    const mainRecipeNames=new Set(['体积尘埃 · BlackHole 研究','层叠光带 · Unity 研究','程序光带 · 基础结构','颗粒吸积 · 完整组合']);
    catalog.recipes.forEach((r,i)=>{if(!mainRecipeNames.has(r.name))return;const o=document.createElement('option');o.value=i;o.textContent=r.name;$('recipe').append(o);});
    const customOption=document.createElement('option');customOption.value='custom';customOption.disabled=true;customOption.hidden=true;$('recipe').append(customOption);
    const stateIndex=catalog.recipes.findIndex(r=>r.name===state.name);
    if(mainRecipeNames.has(state.name))$('recipe').value=String(stateIndex);
    else {customOption.textContent=`兼容变体：${state.name}`;customOption.hidden=false;$('recipe').value='custom';}
    $('recipe').onchange=()=>{if($('recipe').value==='custom')return;state=validate(catalog.recipes[Number($('recipe').value)]);if(state.name===granularName){time=presetPreviewTime(state.name);setPlaying(false);}resetView={yaw:state.yaw,pitch:state.pitch};customOption.hidden=true;uiError='';ui();draw();};
    for(const key of [...colorKeys,'mode','diskStyle','backgroundStyle'])$(key).oninput=()=>change({[key]:$(key).value});
    $('particles').onchange=()=>change({particles:$('particles').checked});
    $('lensEnabled').onchange=()=>change({lensEnabled:$('lensEnabled').checked});
    $('steps').onchange=()=>change({steps:Number($('steps').value)});
    $('aspect').onchange=()=>{const size={wide:[960,600],cinema:[960,540],square:[640,640]}[$('aspect').value];$('scene').width=size[0];$('scene').height=size[1];draw();};
    $('diagnostic').onchange=()=>{diagnostic=Number($('diagnostic').value);draw();};
    $('time').onchange=()=>{try{time=validateTime(Number($('time').value));if(time===MAX_TIME)setPlaying(false);last=performance.now();uiError='';draw();}catch(e){uiError=e.message;$('status').textContent=uiError;ui();}};
    $('reset').onclick=()=>change(resetView);
    $('play').onclick=()=>{setPlaying(!playing);draw();};
    const download=(name,url)=>{const a=document.createElement('a');a.download=name;a.href=url;a.click();};
    // Keep world-only settings across preview edits and preset switches.
    // They do not pretend to render a player or replace the real world here.
    let worldExtensions=null;
    const worldKeys=['scale','distance','lens','lensStrength','lensAffectsPlayer','form','time','playing'];
    const worldNumberRanges={scale:[.2,8],distance:[2,128],lensStrength:[0,1],time:[0,MAX_TIME]};
    function validateWorldExtensions(input){
      const result={};
      for(const key of worldKeys)if(Object.hasOwn(input,key)){
        const value=input[key];
        if(worldNumberRanges[key]){const [lo,hi]=worldNumberRanges[key];if(typeof value!=='number'||!Number.isFinite(value)||value<lo||value>hi)throw Error(`世界 ${key} 必须位于 ${lo}–${hi}`);}
        else if(['lens','lensAffectsPlayer','playing'].includes(key)){if(typeof value!=='boolean')throw Error(`世界 ${key} 必须为布尔值`);}
        else if(key==='form'&&(!['granular','dust','cinematic','ribbons','classic','recipe','gold','ice','violet','lens','crimson'].includes(value)))throw Error('未知世界形态');
        result[key]=value;
      }
      return result;
    }
    const snapshot=()=>worldExtensions
      ?{schema:'black-hole-world/v1',parameters:{...state,...worldExtensions,lens:state.lensEnabled,lensStrength:state.lensBackground},time:validateTime(time),playing:false}
      :{schema:'black-hole-preview/v2',parameters:{...state},time:validateTime(time),playing:false};
    $('export').onclick=()=>{const u=URL.createObjectURL(new Blob([JSON.stringify(snapshot(),null,2)],{type:'application/json'}));download('black-hole-recipe.json',u);setTimeout(()=>URL.revokeObjectURL(u),1000);};
    $('capture').onclick=()=>{draw();download('black-hole.png',$('scene').toDataURL('image/png'));};
    function importSnapshot(data){
      assertKeys(data,['schema','parameters','time','playing'],'配方文件');
      if(!['black-hole-preview/v1','black-hole-preview/v2','black-hole-world/v1'].includes(data.schema))throw Error('未知配方格式');
      let extensions=null,input=data.parameters,t;
      if(data.schema==='black-hole-world/v1'){
        assertKeys(input,[...parameterKeys,...worldKeys],'世界配方参数');
        extensions=validateWorldExtensions(input);
        const shared=Object.fromEntries(Object.entries(input).filter(([key])=>parameterKeys.includes(key)));
        if(Object.hasOwn(extensions,'lensStrength'))shared.lensBackground=extensions.lensStrength;
        if(Object.hasOwn(extensions,'lens'))shared.lensEnabled=extensions.lens;
        input=shared;t=validateTime(data.time??extensions.time??0);
        delete extensions.time;delete extensions.playing;
      }else t=validateTime(data.time);
      const p=validate(input);
      if(data.playing!==undefined&&typeof data.playing!=='boolean')throw Error('playing 必须为布尔值');
      // Validate the entire import before changing the active state or reset baseline.
      state=p;worldExtensions=extensions;time=t;setPlaying(false);resetView={yaw:p.yaw,pitch:p.pitch};uiError='';
      customOption.textContent=`自定义：${p.name}`;customOption.hidden=false;$('recipe').value='custom';ui();draw();
    }
    $('import').onchange=async()=>{try{const f=$('import').files[0];if(!f)return;if(f.size>65536)throw Error('配方超过 64 KB');importSnapshot(JSON.parse(await f.text()));}catch(e){uiError=e.message;$('status').textContent=uiError;}};
    let drag=null;
    $('scene').onpointerdown=e=>{drag=[e.clientX,e.clientY,state.yaw,state.pitch];$('scene').setPointerCapture(e.pointerId);};
    $('scene').onpointermove=e=>{if(drag)change({yaw:Math.max(-Math.PI,Math.min(Math.PI,drag[2]+(e.clientX-drag[0])*.005)),pitch:Math.max(-1.3,Math.min(1.3,drag[3]+(e.clientY-drag[1])*.005))});};
    $('scene').onpointerup=$('scene').onpointercancel=()=>{drag=null;};
    let wheelPixels=0,wheelScheduled=false;
    $('scene').addEventListener('wheel',e=>{
      e.preventDefault();
      const unit=e.deltaMode===1?16:e.deltaMode===2?$('scene').clientHeight:1;
      wheelPixels+=e.deltaY*unit;
      if(wheelScheduled)return;
      wheelScheduled=true;
      requestAnimationFrame(()=>{
        wheelScheduled=false;
        const distance=state.viewDistance*Math.exp(wheelPixels*.0012);
        wheelPixels=0;
        change({viewDistance:Math.max(bounds.viewDistance[0],Math.min(bounds.viewDistance[1],Math.round(distance*100)/100))});
      });
    },{passive:false});
    function tick(now){if(playing&&now-lastDraw>1000/30){time=Math.min(MAX_TIME,time+Math.max(0,now-last)/1000);last=now;lastDraw=now;if(time===MAX_TIME)setPlaying(false);draw();$('time').value=time.toFixed(2);}requestAnimationFrame(tick);}
    ui();draw();if(new URLSearchParams(location.search).get('play')==='1')setPlaying(true);requestAnimationFrame(tick);
    // Narrow, deterministic interface for evidence generation and regression checks.
    window.blackHolePreview={ready:true,catalog,validate,snapshot,importSnapshot,
      set:(patch,t=time)=>{validateTime(t);assertKeys(patch,parameterKeys,'参数修改');const next=validate({...state,...patch});setPlaying(false);state=next;time=t;uiError='';ui();draw();},
      frame:(index=0,debug=0)=>{const r=renderers[index];r.render(state,time,debug);const pixels=Array.from(r.pixels());const error=r.gl.getError();r.render(state,time);return {width:r.canvas.width,height:r.canvas.height,pixels,glError:error};},
      renderer:()=>{const gl=renderers[0].gl,e=gl.getExtension('WEBGL_debug_renderer_info');return e?gl.getParameter(e.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER);}
    };
  } catch(e) {document.getElementById('status').textContent='初始化失败：'+e.message;window.blackHoleError=e.message;}
})();
