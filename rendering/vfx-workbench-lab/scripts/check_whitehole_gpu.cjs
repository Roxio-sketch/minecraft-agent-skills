/* Real WebGL2 checks of the production Forge shaders. This is a GPU math and
 * composition fixture; it does not claim Minecraft/driver integration. */
const fs = require('fs'), path = require('path'), crypto = require('crypto');
let chromium;
try { ({chromium} = require('playwright')); }
catch { ({chromium} = require('C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')); }
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'evidence/white-hole-20261007/gpu');
const shaderDir = path.join(root, 'src/main/resources/assets/vfxworkbench/shaders/core');
fs.mkdirSync(output, {recursive:true});
const load = dir => Object.fromEntries(['world', 'particles'].map(name => {
  const json=fs.readFileSync(path.join(dir, `bh_${name}.json`),'utf8');
  return [name, {vs:fs.readFileSync(path.join(dir, `bh_${name}.vsh`),'utf8'),
    fs:fs.readFileSync(path.join(dir, `bh_${name}.fsh`),'utf8'),json,uniforms:JSON.parse(json).uniforms}];
}));
const payload = {current:load(shaderDir), baseline:load(path.join(output,'baseline'))};
(async()=>{
  const browser = await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=swiftshader','--enable-unsafe-swiftshader']});
  try {
    const page = await browser.newPage();
    page.on('console',message=>console.log('[GPU] '+message.text()));
    const result = await page.evaluate(payload => {
      const canvas=document.createElement('canvas');canvas.width=128;canvas.height=96;
      const gl=canvas.getContext('webgl2',{antialias:false,preserveDrawingBuffer:true});
      if(!gl)throw Error('WebGL2 is unavailable');
      if(!gl.getExtension('EXT_color_buffer_float'))throw Error('Float GPU readback is unavailable');
      const checks=[],captures=[],details={};
      const check=(name,pass,data={})=>{checks.push({name,pass,...data});if(!pass)throw Error(name+': '+JSON.stringify(data));if(!name.includes('error'))console.log(name+' PASS');};
      const adapt=src=>src.replace('#version 150','#version 300 es\nprecision highp float;precision highp int;');
      const compile=(type,src,name)=>{const s=gl.createShader(type);gl.shaderSource(s,adapt(src));gl.compileShader(s);
        if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(name+': '+gl.getShaderInfoLog(s));return s;};
      const link=(s,name,feedback=false)=>{const p=gl.createProgram();gl.attachShader(p,compile(gl.VERTEX_SHADER,s.vs,name+' vertex'));
        gl.attachShader(p,compile(gl.FRAGMENT_SHADER,s.fs,name+' fragment'));
        if(feedback)gl.transformFeedbackVaryings(p,['ParticleLocal','ParticleLife','ParticleAlpha','ParticleRadius','ParticlePeriod'],gl.INTERLEAVED_ATTRIBS);
        gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(name+': '+gl.getProgramInfoLog(p));return p;};
      const world=link(payload.current.world,'production world');
      const baselineWorld=link(payload.baseline.world,'baseline world');
      const particle=link(payload.current.particles,'production particles',true);
      const baselineParticle=link(payload.baseline.particles,'baseline particles',true);
      check('Both complete production shader pairs compile and link',true);
      check('World and particle white-hole uniforms are live',
        gl.getUniformLocation(world,'uObjectMode')!==null&&gl.getUniformLocation(world,'uCoreEmission')!==null&&gl.getUniformLocation(particle,'uObjectMode')!==null&&gl.getUniformLocation(particle,'uParticleClock')!==null);
      const uniformMap=s=>Object.fromEntries(s.uniforms.map(u=>[u.name,u.values]));
      const types=p=>{const r={};for(let i=0;i<gl.getProgramParameter(p,gl.ACTIVE_UNIFORMS);i++){const u=gl.getActiveUniform(p,i);r[u.name]=u.type;}return r;};
      const typeCache=new Map();
      const set=(p,s,override)=>{
        gl.useProgram(p);if(!typeCache.has(p))typeCache.set(p,types(p));
        const defaults=uniformMap(s),all={...defaults,...override};
        for(const [name,type] of Object.entries(typeCache.get(p))){const loc=gl.getUniformLocation(p,name);let v=all[name];
          if(type===gl.SAMPLER_2D){gl.uniform1i(loc,Number(name.slice(7)));continue;}
          if(v===undefined)v=type===gl.FLOAT_MAT4?[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]:type===gl.FLOAT_VEC2?[0,0]:type===gl.FLOAT_VEC3?[0,0,0]:type===gl.FLOAT_VEC4?[0,0,0,0]:0;
          if(!Array.isArray(v))v=[v];
          if(type===gl.FLOAT)gl.uniform1f(loc,v[0]);else if(type===gl.INT)gl.uniform1i(loc,v[0]);
          else if(type===gl.FLOAT_VEC2)gl.uniform2fv(loc,v);else if(type===gl.FLOAT_VEC3)gl.uniform3fv(loc,v);
          else if(type===gl.FLOAT_VEC4)gl.uniform4fv(loc,v);else if(type===gl.FLOAT_MAT4)gl.uniformMatrix4fv(loc,false,v);
          else throw Error('Unhandled uniform type '+name+': '+type);
        }
      };
      const bind=(p,vertices)=>{const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(vertices),gl.STATIC_DRAW);
        for(const [name,n,offset] of [['Position',3,0],['UV0',2,12]]){const a=gl.getAttribLocation(p,name);if(a>=0){gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,n,gl.FLOAT,false,20,offset);}}return b;};
      const quad=[-1,-1,0,0,0, 1,-1,0,1,0, 1,1,0,1,1, -1,-1,0,0,0, 1,1,0,1,1, -1,1,0,0,1];
      const ids=Array.from({length:256},(_,i)=>[i,0,0,0,0]).flat();
      const tf=gl.createTransformFeedback();
      function particles(p,s,overrides){
        const clock=overrides.uParticleClock??((overrides.uTime??0)*(overrides.ParticleSpeed??1));
        gl.bindFramebuffer(gl.FRAMEBUFFER,null);set(p,s,{WorldScale:1,uTilt:0,CenterRelative:[0,0,0],CameraRight:[1,0,0],CameraUp:[0,1,0],ParticleSize:.022,ParticleDebug:0,uParticleClock:clock,...overrides});
        const b=bind(p,ids),out=gl.createBuffer();gl.bindTransformFeedback(gl.TRANSFORM_FEEDBACK,tf);gl.bindBuffer(gl.TRANSFORM_FEEDBACK_BUFFER,out);
        gl.bufferData(gl.TRANSFORM_FEEDBACK_BUFFER,256*7*4,gl.DYNAMIC_READ);gl.bindBufferBase(gl.TRANSFORM_FEEDBACK_BUFFER,0,out);
        gl.enable(gl.RASTERIZER_DISCARD);gl.beginTransformFeedback(gl.POINTS);gl.drawArrays(gl.POINTS,0,256);gl.endTransformFeedback();gl.disable(gl.RASTERIZER_DISCARD);
        const data=new Float32Array(256*7);gl.getBufferSubData(gl.TRANSFORM_FEEDBACK_BUFFER,0,data);
        gl.bindBufferBase(gl.TRANSFORM_FEEDBACK_BUFFER,0,null);gl.bindTransformFeedback(gl.TRANSFORM_FEEDBACK,null);gl.deleteBuffer(out);gl.deleteBuffer(b);
        check('Particle GPU readback has no GL error',gl.getError()===gl.NO_ERROR);
        return Array.from({length:256},(_,i)=>({id:i,xyz:Array.from(data.slice(i*7,i*7+3)),life:data[i*7+3],alpha:data[i*7+4],radius:data[i*7+5],period:data[i*7+6]}));
      }
      const difference=(a,b)=>{let max=0,sum=0,changed=0;for(let i=0;i<a.length;i++){const d=Math.abs(a[i]-b[i]);max=Math.max(max,d);sum+=d;if(d>1e-7)changed++;}return {max,mean:sum/a.length,changed};};
      const flatten=rows=>rows.flatMap(r=>[...r.xyz,r.life,r.alpha,r.radius,r.period]);
      const black0=particles(particle,payload.current.particles,{uObjectMode:0,uTime:0,ParticleSpeed:1});
      const black1=particles(particle,payload.current.particles,{uObjectMode:0,uTime:.05,ParticleSpeed:1});
      const white0=particles(particle,payload.current.particles,{uObjectMode:1,uTime:0,ParticleSpeed:1});
      const white1=particles(particle,payload.current.particles,{uObjectMode:1,uTime:.05,ParticleSpeed:1});
      const old=particles(baselineParticle,payload.baseline.particles,{uTime:.05,ParticleSpeed:1});
      const regression=difference(flatten(old),flatten(black1));
      check('Black-hole particle trajectories preserve GPU baseline',regression.max<1e-6,regression);
      for(const [name,mode,a,b] of [['black',0,black0,black1],['white',1,white0,white1]]){
        const sameLife=a.filter((r,i)=>b[i].life>r.life&&r.life>.05&&b[i].life<.94);
        const radial= sameLife.map(r=>b[r.id].radius-r.radius);
        check(name+' same-ID radial direction excludes respawns',sameLife.length>100&&radial.every(d=>mode?d>0:d<0),{samples:sameLife.length,min:Math.min(...radial),max:Math.max(...radial)});
        let maxAngleError=0;
        for(const r of sameLife){const n=b[r.id],qA=Math.pow(r.radius,1.5),qB=Math.pow(n.radius,1.5),dq=(qB-qA)/(n.life-r.life);
          const expected=2.8*r.period/dq*Math.log(qB/qA);
          const angleA=Math.atan2(r.xyz[2],r.xyz[0]),angleB=Math.atan2(n.xyz[2],n.xyz[0]);
          const measured=Math.atan2(Math.sin(angleB-angleA),Math.cos(angleB-angleA));
          maxAngleError=Math.max(maxAngleError,Math.abs(expected-measured));}
        check(name+' orbital displacement integrates the same radial trajectory',maxAngleError<.002,{samples:sameLife.length,maxAngleErrorRadians:maxAngleError});
        const frozenA=particles(particle,payload.current.particles,{uObjectMode:mode,uTime:0,ParticleSpeed:0,uParticleClock:3.5});
        const frozenB=particles(particle,payload.current.particles,{uObjectMode:mode,uTime:123.4,ParticleSpeed:0,uParticleClock:3.5});
        const frozen=difference(flatten(frozenA),flatten(frozenB));
        check(name+' zero particle speed freezes position, life and alpha',frozen.max===0,frozen);
      }
      const sampleId=128,seed=white0[sampleId].life,period=white0[sampleId].period;
      const runningMidflight=particles(particle,payload.current.particles,{uObjectMode:1,uTime:3.5,ParticleSpeed:1,uParticleClock:3.5});
      const pausedMidflight=particles(particle,payload.current.particles,{uObjectMode:1,uTime:123.4,ParticleSpeed:0,uParticleClock:3.5});
      const pauseDifference=difference(flatten(runningMidflight),flatten(pausedMidflight));
      check('Pausing white particles preserves their existing mid-flight state',pauseDifference.max===0,pauseDifference);
      const atLife=life=>particles(particle,payload.current.particles,{uObjectMode:1,uTime:((life-seed+1)%1)*period,ParticleSpeed:1})[sampleId];
      const birth=atLife(.001),middle=atLife(.5),death=atLife(.999);
      check('White particles spawn outside the inner source and fade at both ends',
        birth.radius>=.94&&birth.radius<middle.radius&&middle.radius<death.radius&&birth.alpha<.002&&middle.alpha>.95&&death.alpha<.002,{birth,middle,death});
      check('White particle state is finite for all fixed IDs',white0.concat(white1).every(r=>flatten([r]).every(Number.isFinite)));
      details.particles={fixedIds:256,trajectoryRegression:regression,whiteSourceLifecycle:{birth,middle,death},exampleTracks:{black:sampleId,black0:black0[sampleId],black1:black1[sampleId],white0:white0[sampleId],white1:white1[sampleId]}};
      const width=canvas.width,height=canvas.height;
      function texture(unit,internal,format,type,data,w=width,h=height){
        const tex=gl.createTexture();gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,tex);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,internal,w,h,0,format,type,data);return tex;}
      const background=new Uint8Array(width*height*4),depth=new Float32Array(width*height).fill(1);
      for(let y=0;y<height;y++)for(let x=0;x<width;x++){const k=(y*width+x)*4,grid=(Math.floor(x/16)+Math.floor(y/16))%2;
        background[k]=grid?58:18;background[k+1]=Math.floor(24+y*.32);background[k+2]=Math.floor(44+x*.34);background[k+3]=255;}
      texture(0,gl.RGBA8,gl.RGBA,gl.UNSIGNED_BYTE,background);texture(1,gl.R32F,gl.RED,gl.FLOAT,depth);
      texture(2,gl.RGBA32F,gl.RGBA,gl.FLOAT,new Float32Array(8),2,1);texture(3,gl.RGBA8,gl.RGBA,gl.UNSIGNED_BYTE,background);texture(4,gl.R32F,gl.RED,gl.FLOAT,depth);
      const length=v=>Math.hypot(...v),normalize=v=>v.map(n=>n/length(v));
      const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],dot=(a,b)=>a.reduce((s,n,i)=>s+n*b[i],0);
      const eye=[0,3.5,0],center=[0,0,-11.2];
      const z=normalize(eye.map((n,i)=>n-center[i])),x=normalize(cross([0,1,0],z)),y=cross(z,x);
      const view=[x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1];
      const f=1/Math.tan(60*Math.PI/360),near=.1,far=100;
      const projection=[f/(width/height),0,0,0,0,f,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0];
      const multiply=(a,b)=>Array.from({length:16},(_,k)=>{const col=Math.floor(k/4),row=k%4;return [0,1,2,3].reduce((s,i)=>s+a[i*4+row]*b[col*4+i],0);});
      const invert=m=>{const a=Array.from({length:4},(_,r)=>[...Array.from({length:4},(_,c)=>m[c*4+r]),...Array.from({length:4},(_,c)=>r===c?1:0)]);
        for(let c=0;c<4;c++){let k=c;for(let r=c+1;r<4;r++)if(Math.abs(a[r][c])>Math.abs(a[k][c]))k=r;[a[c],a[k]]=[a[k],a[c]];const pivot=a[c][c];for(let j=0;j<8;j++)a[c][j]/=pivot;for(let r=0;r<4;r++)if(r!==c){const q=a[r][c];for(let j=0;j<8;j++)a[r][j]-=q*a[c][j];}}
        return Array.from({length:16},(_,k)=>a[k%4][4+Math.floor(k/4)]);};
      const vp=multiply(projection,view),ivp=invert(vp);
      const common={ViewProjection:vp,InvViewProjection:ivp,CenterRelative:center,CameraOriginRelative:eye,WorldScale:1.4,uTilt:0,uSteps:160,ForegroundCount:0,EnvironmentReady:0,LensProbeMode:0,
        uTime:1.75,uOrbitPhase:.45,uRadialPhase:.4,uClassicRadialPhase:.4,uShearLocal:.6,uShearParity:0,uDustDriftA:.2,uDustDriftB:.3,
        uCoreEmission:1,uCoreColor:[0,0,0],uCoreEdgeColor:[0,0,0],uHot:[1,.9,.65],uCool:[1,.13,.02],uDiskMid:[.25,.08,1],JetStrength:0};
      const framebuffer=gl.createFramebuffer(),probeTex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,probeTex);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,width,height,0,gl.RGBA,gl.FLOAT,null);gl.bindFramebuffer(gl.FRAMEBUFFER,framebuffer);
      gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,probeTex,0);
      check('Floating-point probe framebuffer is complete',gl.checkFramebufferStatus(gl.FRAMEBUFFER)===gl.FRAMEBUFFER_COMPLETE);
      // Restore the fifth depth texture after creating the probe texture on the
      // currently active unit. All units are rebound by the render helper.
      const units=[];for(let i=0;i<5;i++){gl.activeTexture(gl.TEXTURE0+i);units.push(gl.getParameter(gl.TEXTURE_BINDING_2D));}
      texture(4,gl.R32F,gl.RED,gl.FLOAT,depth);units[4]=gl.getParameter(gl.TEXTURE_BINDING_2D);
      function render(p,s,overrides={},float=false,name){
        for(let i=0;i<5;i++){gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,units[i]);}
        gl.bindFramebuffer(gl.FRAMEBUFFER,float?framebuffer:null);gl.viewport(0,0,width,height);gl.disable(gl.BLEND);gl.disable(gl.DEPTH_TEST);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);
        set(p,s,{...common,...overrides});const b=bind(p,quad);gl.drawArrays(gl.TRIANGLES,0,6);
        const data=float?new Float32Array(width*height*4):new Uint8Array(width*height*4);gl.readPixels(0,0,width,height,gl.RGBA,float?gl.FLOAT:gl.UNSIGNED_BYTE,data);
        gl.deleteBuffer(b);check('World GPU render has no GL error'+(name?' ('+name+')':''),gl.getError()===gl.NO_ERROR);
        if(name)captures.push({name,png:canvas.toDataURL('image/png').split(',')[1]});return data;
      }
      const styleNames=['classic','granular','ribbon','dust','cinematic'];details.worldBaseline=[];
      for(let style=0;style<5;style++){
        const before=render(baselineWorld,payload.baseline.world,{uGranular:style});
        const after=render(world,payload.current.world,{uGranular:style,uObjectMode:0},false,'black-'+styleNames[style]);
        const diff=difference(before,after);details.worldBaseline.push({style:styleNames[style],...diff});
        check('Black-hole '+styleNames[style]+' preserves the rendered production baseline',diff.max<=1,diff);
        const white=render(world,payload.current.world,{uGranular:style,uObjectMode:1,uRadialPhase:-.4,uClassicRadialPhase:-.4,uCoreEmission:2.2,uCoreColor:[.72,.88,1],uCoreEdgeColor:[.22,.52,1],uHot:[.72,.9,1],uCool:[.03,.16,.62],uDiskMid:[.1,.46,1],uHaloGradientStart:[.62,.88,1],uHaloGradientEnd:[.05,.27,1],uHaloDiskInnerColor:[.5,.75,1],uHaloDiskOuterColor:[.03,.2,.8],uHaloAuraInnerColor:[.2,.5,1],uHaloAuraOuterColor:[.02,.14,.46]},false,'white-'+styleNames[style]);
        const modeDiff=difference(after,white);check('White-hole '+styleNames[style]+' recipe renders a distinct finite result',modeDiff.mean>1&&white.some((v,i)=>i%4!==3&&v>220),modeDiff);
      }
      const coreDark=render(world,payload.current.world,{uObjectMode:1,uDensity:0,HaloStrength:0,JetStrength:0,uCoreColor:[.72,.88,1],uCoreEdgeColor:[.72,.88,1],uCoreEmission:0});
      const coreBright=render(world,payload.current.world,{uObjectMode:1,uDensity:0,HaloStrength:0,JetStrength:0,uCoreColor:[.72,.88,1],uCoreEdgeColor:[.72,.88,1],uCoreEmission:2.2});
      const k=(Math.floor(height/2)*width+Math.floor(width/2))*4;
      check('Core emission is consumed by the real world composition',coreBright[k]>coreDark[k]+120,{centerDark:Array.from(coreDark.slice(k,k+3)),centerBright:Array.from(coreBright.slice(k,k+3))});
      const probeSource=main=>({...payload.current.world,fs:payload.current.world.fs.replace(/\bvoid\s+main\s*\(\s*\)/,'void productionMain()')+'\n'+main});
      const patternSource=probeSource('uniform float ProbeRadiusOffset;\nvoid main(){float r=1.3+texCoord.x*1.0+ProbeRadiusOffset;float a=texCoord.y*6.28318530718;vec2 p=uGranular==3?vec2(lensedDustVolume(vec3(cos(a)*r,0.0,sin(a)*r))):previewDiskPattern(r,a);fragColor=vec4(p,0,1); }');
      const pattern=link(patternSource,'same-source disk field probe');
      const staticFlow={uOrbitPhase:0,uShearLocal:0,uShearParity:0,uDustDriftA:0,uDustDriftB:0,uDustScale:1,uDustFill:1,uSpiral:0,ProbeRadiusOffset:0,uRadialPhase:0,uClassicRadialPhase:0};
      details.diskTransport=[];
      for(let style=0;style<5;style++)for(let mode=0;mode<2;mode++){
        const phase=.15,sign=mode?-1:1,span=2.9-.94,delta=style===2?phase*.16*span/(12*1.2):phase;
        const a=render(pattern,patternSource,{...staticFlow,uGranular:style,uObjectMode:mode},true);
        const b=render(pattern,patternSource,{...staticFlow,uGranular:style,uObjectMode:mode,uRadialPhase:sign*phase,uClassicRadialPhase:sign*phase,ProbeRadiusOffset:-sign*delta},true);
        const diff=difference(a,b);details.diskTransport.push({style:styleNames[style],mode:mode?'white':'black',signedPhase:sign*phase,radiusDisplacement:-sign*delta,...diff});
        check((mode?'White outward ':'Black inward ')+styleNames[style]+' field transports actual GPU features',diff.max<.006,diff);
      }
      details.phaseWrap=[];
      for(let style=0;style<5;style++)for(const boundary of [0,64,128]){
        const e=.00001,wrap=x=>((x%128)+128)%128,classic=x=>((x%(2*Math.PI))+2*Math.PI)%(2*Math.PI);
        const a=render(pattern,patternSource,{...staticFlow,uGranular:style,uObjectMode:1,uRadialPhase:-wrap(boundary-e),uClassicRadialPhase:-classic(boundary-e)},true);
        const b=render(pattern,patternSource,{...staticFlow,uGranular:style,uObjectMode:1,uRadialPhase:-wrap(boundary+e),uClassicRadialPhase:-classic(boundary+e)},true);
        const diff=difference(a,b);details.phaseWrap.push({style:styleNames[style],boundary,...diff});
        check('White '+styleNames[style]+' radial phase is continuous around '+boundary,diff.max<.015,diff);
      }
      for(const style of [2,4]){
        const zero=render(pattern,patternSource,{...staticFlow,uGranular:style,uObjectMode:1,uRadialPhase:0},true);
        const exact128=render(pattern,patternSource,{...staticFlow,uGranular:style,uObjectMode:1,uRadialPhase:-128},true);
        const diff=difference(zero,exact128);
        check('White '+styleNames[style]+' exact float-rounded 128 closes to zero',diff.max===0,diff);
        details.phaseWrap.push({style:styleNames[style],boundary:'exact 128 versus 0',...diff});
      }
      const lensSource=probeSource('void main(){vec3 rd=normalize(scenePosition(texCoord,1.0)-CameraOriginRelative);resolveLensSource(rd,false);fragColor=vec4(lensSourceUv,lensAvailability,1.0); }');
      const lens=link(lensSource,'same-source lens map probe');
      const blackMap=render(lens,lensSource,{uObjectMode:0},true),whiteMap=render(lens,lensSource,{uObjectMode:1},true);
      const noLens=render(lens,lensSource,{uObjectMode:1,LensStrength:0},true);
      const modeMapDiff=difference(blackMap,whiteMap),enabledMapDiff=difference(whiteMap,noLens);
      check('Positive-mass white and black modes share the actual GPU lens map',modeMapDiff.max===0,modeMapDiff);
      check('The shared lens map actively remaps the test background',enabledMapDiff.max>.05&&enabledMapDiff.changed>200,enabledMapDiff);
      details.lensing={modeDifference:modeMapDiff,enabledDifference:enabledMapDiff};
      const debug=gl.getExtension('WEBGL_debug_renderer_info');
      return {status:'passed',fixture:'white-hole-production-gpu/1',checks,details,captures,
        gpu:{vendor:debug?gl.getParameter(debug.UNMASKED_VENDOR_WEBGL):gl.getParameter(gl.VENDOR),renderer:debug?gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION)},
        scope:{productionGlslUsed:true,sourceAdaptation:'GLSL150 to GLSL300es version/precision only; separate same-source probe mains',forgeRuntimeVerified:false,limitations:'Synthetic scene textures and camera. Does not verify Forge event timing, gameplay UI, transparency ordering or hardware-driver appearance.'}};
    },payload);
    for(const capture of result.captures){fs.writeFileSync(path.join(output,capture.name+'.png'),Buffer.from(capture.png,'base64'));delete capture.png;capture.path=capture.name+'.png';}
    result.generatedAt=new Date().toISOString();result.sources=Object.fromEntries(['current','baseline'].map(group=>[group,Object.fromEntries(Object.entries(payload[group]).flatMap(([name,s])=>['vs','fs','json'].map(kind=>{
      const src=s[kind];
      return [name+'.'+kind,{sha256:crypto.createHash('sha256').update(src).digest('hex'),bytes:Buffer.byteLength(src)}];
    })))]));
    if(fs.existsSync(path.join(output,'failure.json')))fs.unlinkSync(path.join(output,'failure.json'));
    fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(result,null,2));
    const cards=result.captures.map(c=>`<figure><img src="${c.path}" alt="${c.name}"><figcaption>${c.name}</figcaption></figure>`).join('');
    fs.writeFileSync(path.join(output,'index.html'),`<!doctype html><meta charset="utf-8"><title>White-hole production GPU fixture</title><style>body{background:#101622;color:#dde5f4;font:15px system-ui;margin:32px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(256px,1fr));gap:16px}figure{margin:0;padding:12px;background:#1b2537}img{width:100%;image-rendering:auto}figcaption{margin-top:8px}a{color:#97c6ff}</style><h1>White-hole production GPU fixture</h1><p>${result.checks.length} checks passed. The complete production GLSL shaders ran in WebGL2; this is a synthetic GPU fixture, not Forge runtime evidence.</p><p><a href="report.json">Exact report and source hashes</a></p><main>${cards}</main>`);
    console.log(JSON.stringify({status:result.status,checks:result.checks.length,captures:result.captures.length,gpu:result.gpu,report:path.join(output,'report.json'),forgeRuntimeVerified:false},null,2));
  } finally { await browser.close(); }
})().catch(error=>{fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({status:'failed',message:error.message,stack:error.stack},null,2));console.error(error);process.exitCode=1;});
