/* Executes the complete production GLSL in real WebGL2. This synthetic
 * scene fixture is separate from Forge integration and hardware performance. */
const fs=require('fs'),path=require('path'),crypto=require('crypto');
let chromium;try{({chromium}=require('playwright'));}catch{({chromium}=require('C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));}
const root=path.resolve(__dirname,'..');
const output=path.join(root,'evidence/white-hole-default-dust-ui-20261007/gpu');
const shaderDir=path.join(root,'src/main/resources/assets/vfxworkbench/shaders/core');
const baselineDir=path.join(root,'evidence/white-hole-outflow-20261007/source-before/src/main/resources/assets/vfxworkbench/shaders/core');
fs.mkdirSync(output,{recursive:true});
const load=(dir,names=['world','particles'])=>Object.fromEntries(names.map(name=>{
  const json=fs.readFileSync(path.join(dir,`bh_${name}.json`),'utf8');
  return[name,{vs:fs.readFileSync(path.join(dir,`bh_${name}.vsh`),'utf8'),fs:fs.readFileSync(path.join(dir,`bh_${name}.fsh`),'utf8'),json,uniforms:JSON.parse(json).uniforms}];
}));
const defaultRecipeText=fs.readFileSync(path.join(root,'src/main/resources/assets/vfxworkbench/recipes/white-ring-default.json'),'utf8');
const payload={current:load(shaderDir,['world','particles','core_blur','core_composite']),baseline:load(baselineDir),defaultRecipe:JSON.parse(defaultRecipeText)};
(async()=>{
  const browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=swiftshader','--enable-unsafe-swiftshader']});
  try{
    const page=await browser.newPage();page.on('console',m=>console.log('[GPU] '+m.text()));
    const result=await page.evaluate(payload=>{
      const canvas=document.createElement('canvas');canvas.width=256;canvas.height=160;
      const gl=canvas.getContext('webgl2',{antialias:false,preserveDrawingBuffer:true});
      if(!gl||!gl.getExtension('EXT_color_buffer_float'))throw Error('WebGL2 float readback is unavailable');
      const checks=[],captures=[],details={};
      const check=(name,pass,data={})=>{checks.push({name,pass,...data});if(!pass)throw Error(name+': '+JSON.stringify(data));if(!name.includes('GL error'))console.log(name+' PASS');};
      const adapt=src=>src.replace('#version 150','#version 300 es\nprecision highp float;precision highp int;');
      const compile=(type,src,name)=>{const s=gl.createShader(type);gl.shaderSource(s,adapt(src));gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(name+': '+gl.getShaderInfoLog(s));return s;};
      const link=(s,name,feedback=false)=>{const p=gl.createProgram();gl.attachShader(p,compile(gl.VERTEX_SHADER,s.vs,name+' vertex'));gl.attachShader(p,compile(gl.FRAGMENT_SHADER,s.fs,name+' fragment'));if(feedback)gl.transformFeedbackVaryings(p,['ParticleLocal','ParticleLife','ParticleAlpha','ParticleRadius','ParticlePeriod'],gl.INTERLEAVED_ATTRIBS);gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(name+': '+gl.getProgramInfoLog(p));return p;};
      const world=link(payload.current.world,'production world'),baselineWorld=link(payload.baseline.world,'delivered baseline world');
      const particle=link(payload.current.particles,'production particles',true),baselineParticle=link(payload.baseline.particles,'delivered baseline particles',true);
      check('Complete production and delivered baseline shader pairs compile and link',true);
      check('Every new outflow and core-corona uniform is consumed by the production linked shader',['uJetClock','uShellClock','uShellStrength','uShellRadius','uShellWidth','uShellColor','uCoreGlow','uCoreGlowRadius'].every(n=>gl.getUniformLocation(world,n)!==null));
      const types=p=>{const r={};for(let i=0;i<gl.getProgramParameter(p,gl.ACTIVE_UNIFORMS);i++){const u=gl.getActiveUniform(p,i);r[u.name]=u.type;}return r;};
      const typeCache=new Map();
      const set=(p,s,override)=>{gl.useProgram(p);if(!typeCache.has(p))typeCache.set(p,types(p));const all={...Object.fromEntries(s.uniforms.map(u=>[u.name,u.values])),...override};for(const[name,type]of Object.entries(typeCache.get(p))){const loc=gl.getUniformLocation(p,name);let v=all[name];if(type===gl.SAMPLER_2D){gl.uniform1i(loc,Number(name.slice(7)));continue;}if(v===undefined)v=type===gl.FLOAT_MAT4?[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]:type===gl.FLOAT_VEC2?[0,0]:type===gl.FLOAT_VEC3?[0,0,0]:type===gl.FLOAT_VEC4?[0,0,0,0]:0;if(!Array.isArray(v))v=[v];if(type===gl.FLOAT)gl.uniform1f(loc,v[0]);else if(type===gl.INT)gl.uniform1i(loc,v[0]);else if(type===gl.FLOAT_VEC2)gl.uniform2fv(loc,v);else if(type===gl.FLOAT_VEC3)gl.uniform3fv(loc,v);else if(type===gl.FLOAT_VEC4)gl.uniform4fv(loc,v);else if(type===gl.FLOAT_MAT4)gl.uniformMatrix4fv(loc,false,v);else throw Error('Unhandled uniform type '+name+': '+type);}};
      const bind=(p,vertices)=>{const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(vertices),gl.STATIC_DRAW);for(const[name,n,offset]of[['Position',3,0],['UV0',2,12]]){const a=gl.getAttribLocation(p,name);if(a>=0){gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,n,gl.FLOAT,false,20,offset);}}return b;};
      const quad=[-1,-1,0,0,0,1,-1,0,1,0,1,1,0,1,1,-1,-1,0,0,0,1,1,0,1,1,-1,1,0,0,1];
      const difference=(a,b)=>{let max=0,sum=0,changed=0;for(let i=0;i<a.length;i++){const d=Math.abs(a[i]-b[i]);max=Math.max(max,d);sum+=d;if(d>1e-7)changed++;}return{max,mean:sum/a.length,changed};};
      function particles(p,s,overrides){const count=256,ids=Array.from({length:count},(_,i)=>[i,0,0,0,0]).flat(),tf=gl.createTransformFeedback(),out=gl.createBuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,null);set(p,s,{WorldScale:1,uTilt:0,CenterRelative:[0,0,0],CameraRight:[1,0,0],CameraUp:[0,1,0],ParticleSize:.022,ParticleDebug:0,...overrides});const b=bind(p,ids);gl.bindTransformFeedback(gl.TRANSFORM_FEEDBACK,tf);gl.bindBuffer(gl.TRANSFORM_FEEDBACK_BUFFER,out);gl.bufferData(gl.TRANSFORM_FEEDBACK_BUFFER,count*7*4,gl.DYNAMIC_READ);gl.bindBufferBase(gl.TRANSFORM_FEEDBACK_BUFFER,0,out);gl.enable(gl.RASTERIZER_DISCARD);gl.beginTransformFeedback(gl.POINTS);gl.drawArrays(gl.POINTS,0,count);gl.endTransformFeedback();gl.disable(gl.RASTERIZER_DISCARD);const data=new Float32Array(count*7);gl.getBufferSubData(gl.TRANSFORM_FEEDBACK_BUFFER,0,data);gl.bindBufferBase(gl.TRANSFORM_FEEDBACK_BUFFER,0,null);gl.bindTransformFeedback(gl.TRANSFORM_FEEDBACK,null);gl.deleteBuffer(out);gl.deleteBuffer(b);gl.deleteTransformFeedback(tf);check('Particle transform-feedback readback has no GL error',gl.getError()===gl.NO_ERROR);return data;}
      details.particleBaseline=[];
      for(const time of[0,3.5]){const o={uObjectMode:0,uTime:time,uParticleClock:time};const d=difference(particles(particle,payload.current.particles,o),particles(baselineParticle,payload.baseline.particles,o));details.particleBaseline.push({mode:0,time,...d});check('Delivered black particle state remains exact at '+time,d.max===0,d);}
      const whiteParticlesA=particles(particle,payload.current.particles,{uObjectMode:1,uParticleClock:1.2}),whiteParticlesB=particles(particle,payload.current.particles,{uObjectMode:1,uParticleClock:1.25});let whiteTracks=0,maxWhiteAngleError=0,minWhiteRadialDelta=1e9;
      for(let i=0;i<256;i++){const k=i*7,lifeA=whiteParticlesA[k+3],lifeB=whiteParticlesB[k+3];if(lifeA>.05&&lifeB<.94&&lifeB>lifeA){const rA=whiteParticlesA[k+5],rB=whiteParticlesB[k+5],qA=Math.pow(rA,1.5),qB=Math.pow(rB,1.5),dq=(qB-qA)/(lifeB-lifeA),expected=1.15*whiteParticlesA[k+6]/dq*Math.log(qB/qA),a=Math.atan2(whiteParticlesA[k+2],whiteParticlesA[k]),b=Math.atan2(whiteParticlesB[k+2],whiteParticlesB[k]),measured=Math.atan2(Math.sin(b-a),Math.cos(b-a));minWhiteRadialDelta=Math.min(minWhiteRadialDelta,rB-rA);maxWhiteAngleError=Math.max(maxWhiteAngleError,Math.abs(measured-expected));whiteTracks++;}}
      check('Faster white particles still follow actual same-ID outward radial tracks',whiteTracks>100&&minWhiteRadialDelta>0,{whiteTracks,minWhiteRadialDelta});
      check('White spiral angle integrates the 1.15 orbital rate over its actual radial path',maxWhiteAngleError<.002,{whiteTracks,maxWhiteAngleError});
      const frozenParticles=particles(particle,payload.current.particles,{uObjectMode:1,uParticleClock:1.2,uTime:123,ParticleSpeed:0});const frozenParticleDiff=difference(whiteParticlesA,frozenParticles);check('Freezing white particle clock retains existing full GPU state',frozenParticleDiff.max===0,frozenParticleDiff);
      const width=canvas.width,height=canvas.height;
      function texture(unit,internal,format,type,data,w=width,h=height){const t=gl.createTexture();gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,t);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,internal,w,h,0,format,type,data);return t;}
      const background=new Uint8Array(width*height*4);for(let y=0;y<height;y++)for(let x=0;x<width;x++){const k=(y*width+x)*4,g=(Math.floor(x/16)+Math.floor(y/16))%2;background[k]=g?58:18;background[k+1]=24+y*.15;background[k+2]=44+x*.15;background[k+3]=255;}
      const skyDepth=new Float32Array(width*height).fill(1);
      const units=[texture(0,gl.RGBA8,gl.RGBA,gl.UNSIGNED_BYTE,background),texture(1,gl.R32F,gl.RED,gl.FLOAT,skyDepth),texture(2,gl.RGBA32F,gl.RGBA,gl.FLOAT,new Float32Array(8),2,1),texture(3,gl.RGBA8,gl.RGBA,gl.UNSIGNED_BYTE,background),texture(4,gl.R32F,gl.RED,gl.FLOAT,skyDepth)];
      const framebuffer=gl.createFramebuffer(),probeTex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,probeTex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,width,height,0,gl.RGBA,gl.FLOAT,null);gl.bindFramebuffer(gl.FRAMEBUFFER,framebuffer);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,probeTex,0);check('Float field-probe framebuffer is complete',gl.checkFramebufferStatus(gl.FRAMEBUFFER)===gl.FRAMEBUFFER_COMPLETE);
      const length=v=>Math.hypot(...v),normalize=v=>v.map(n=>n/length(v)),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],dot=(a,b)=>a.reduce((s,n,i)=>s+n*b[i],0);
      const multiply=(a,b)=>Array.from({length:16},(_,k)=>{const col=Math.floor(k/4),row=k%4;return[0,1,2,3].reduce((s,i)=>s+a[i*4+row]*b[col*4+i],0);});
      const invert=m=>{const a=Array.from({length:4},(_,r)=>[...Array.from({length:4},(_,c)=>m[c*4+r]),...Array.from({length:4},(_,c)=>r===c?1:0)]);for(let c=0;c<4;c++){let k=c;for(let r=c+1;r<4;r++)if(Math.abs(a[r][c])>Math.abs(a[k][c]))k=r;[a[c],a[k]]=[a[k],a[c]];const p=a[c][c];for(let j=0;j<8;j++)a[c][j]/=p;for(let r=0;r<4;r++)if(r!==c){const q=a[r][c];for(let j=0;j<8;j++)a[r][j]-=q*a[c][j];}}return Array.from({length:16},(_,k)=>a[k%4][4+Math.floor(k/4)]);};
      const near=.1,far=100,f=1/Math.tan(60*Math.PI/360),projection=[f/(width/height),0,0,0,0,f,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0];
      const camera=(eye,target=[0,0,0],up=[0,1,0])=>{const z=normalize(eye.map((n,i)=>n-target[i])),x=normalize(cross(up,z)),y=cross(z,x),view=[x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1],vp=multiply(projection,view);return{ViewProjection:vp,InvViewProjection:invert(vp),CenterRelative:[0,0,0],CameraOriginRelative:eye};};
      const common={...camera([0,3.5,11.2]),WorldScale:1,uTilt:0,uSteps:160,ForegroundCount:0,EnvironmentReady:0,LensProbeMode:0,uTime:1.75,uOrbitPhase:.45,uRadialPhase:.4,uClassicRadialPhase:.4,uShearLocal:.6,uShearParity:0,uDustDriftA:.2,uDustDriftB:.3,uObjectMode:0,uCoreEmission:1,uCoreGlow:0,uCoreGlowRadius:1.8,uCoreColor:[0,0,0],uCoreEdgeColor:[0,0,0],uHot:[1,.9,.65],uCool:[1,.13,.02],uDiskMid:[.25,.08,1],JetStrength:0,uJetClock:.85,uShellClock:.8,uShellStrength:0,uShellRadius:5,uShellWidth:.08,uShellColor:[.3,.6,1]};
      function setDepth(data){for(const unit of[1,4]){gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,units[unit]);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,width,height,gl.RED,gl.FLOAT,data);}}
      function render(p,s,overrides={},float=false,name){for(let i=0;i<5;i++){gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,units[i]);}gl.bindFramebuffer(gl.FRAMEBUFFER,float?framebuffer:null);gl.viewport(0,0,width,height);gl.disable(gl.BLEND);gl.disable(gl.DEPTH_TEST);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);set(p,s,{...common,...overrides});const b=bind(p,quad);gl.drawArrays(gl.TRIANGLES,0,6);const data=float?new Float32Array(width*height*4):new Uint8Array(width*height*4);gl.readPixels(0,0,width,height,gl.RGBA,float?gl.FLOAT:gl.UNSIGNED_BYTE,data);gl.deleteBuffer(b);check('World draw/readback has no GL error'+(name?' ('+name+')':''),gl.getError()===gl.NO_ERROR);if(name)captures.push({name,png:canvas.toDataURL('image/png').split(',')[1]});return data;}
      const styleNames=['classic','granular','ribbon','dust','cinematic'];details.worldBaseline=[];
      for(const style of[0,1,2,4])for(const jets of[0,1.45]){const o={uGranular:style,JetStrength:jets,uObjectMode:0};const a=render(baselineWorld,payload.baseline.world,o),b=render(world,payload.current.world,o,false,jets===0?'black-'+styleNames[style]:undefined),d=difference(a,b);details.worldBaseline.push({style:styleNames[style],legacyJets:jets,...d});check('Black '+styleNames[style]+' with legacy jet '+jets+' is pixel-exact to delivered baseline',d.max===0,d);}

      // Dust is a procedural volume, independent of the particle VBO toggle.
      details.dust=[];
      for(const mode of[0,1])for(const outer of[2.9,5.27])for(const time of[8,37862.8]){
        const o={...camera([0,5,16]),uGranular:3,uObjectMode:mode,uInner:.94,uOuter:outer,uThickness:.055,uDensity:5,uDustFill:.375,uDustScale:4,uTime:time,uOrbitPhase:time*.8%(2*Math.PI),uRadialPhase:time*.22%128,uDustDriftA:time*.28%12,uDustDriftB:time*.2%12,LensStrength:0,HaloStrength:0,JetStrength:0,uShellStrength:0,uCoreGlow:0};
        const a=render(world,payload.current.world,o,false,'dust-'+mode+'-'+outer+'-'+time),empty=render(world,payload.current.world,{...o,uDustFill:0}),low=render(world,payload.current.world,{...o,uDensity:0}),scale=render(world,payload.current.world,{...o,uDustScale:1});
        const d=difference(a,empty),density=difference(a,low),structure=difference(a,scale);
        check('Dust volume visible at mode/span/time '+[mode,outer,time],d.changed>100&&d.max>10,{...d});
        check('Dust density zero removes the procedural volume '+[mode,outer,time],difference(low,empty).max===0);
        check('Dust scale changes spatial structure '+[mode,outer,time],structure.changed>100,structure);
        details.dust.push({mode,outer,time,density,fill:d,structure});
      }
      // Every pass below executes the production world/blur/composite program.
      // The half-size linear RGBA16F targets and full-size base copy mirror the
      // Java host. Source selection never applies a bright-scene threshold.
      const blur=link(payload.current.core_blur,'production core blur');
      const composite=link(payload.current.core_composite,'production core composite');
      check('New production diffusion shaders compile and link',true);
      check('Every new diffusion uniform is active',
        ['Direction'].every(n=>gl.getUniformLocation(blur,n)!==null)&&
        ['Intensity','Exposure','GlareCenter','GlareScale'].every(n=>gl.getUniformLocation(composite,n)!==null));
      const hw=Math.ceil(width/2),hh=Math.ceil(height/2),passes=[];
      function target(name,w,h,hdr){
        gl.activeTexture(gl.TEXTURE0+7);const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
        gl.texImage2D(gl.TEXTURE_2D,0,hdr?gl.RGBA16F:gl.RGBA8,w,h,0,gl.RGBA,hdr?gl.FLOAT:gl.UNSIGNED_BYTE,null);
        const fb=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,t,0);
        check(name+' framebuffer has the requested '+(hdr?'RGBA16F':'RGBA8')+' renderable storage',gl.checkFramebufferStatus(gl.FRAMEBUFFER)===gl.FRAMEBUFFER_COMPLETE);
        return{name,texture:t,framebuffer:fb,width:w,height:h,hdr};
      }
      const source=target('half HDR core source',hw,hh,true),ping=target('half HDR ping',hw,hh,true),
        nearGlow=target('half HDR near',hw,hh,true),wideGlow=target('half HDR wide',hw,hh,true),base=target('full base copy',width,height,false);
      function drawTo(p,s,t,o,inputs=units,name=t?t.name:'final canvas'){
        check('No sampling/write feedback ('+name+')',!t||!inputs.includes(t.texture));
        for(let i=0;i<inputs.length;i++){gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,inputs[i]);}
        gl.bindFramebuffer(gl.FRAMEBUFFER,t?t.framebuffer:null);gl.viewport(0,0,t?t.width:width,t?t.height:height);
        gl.disable(gl.BLEND);gl.disable(gl.DEPTH_TEST);gl.disable(gl.SCISSOR_TEST);gl.disable(gl.STENCIL_TEST);gl.disable(gl.CULL_FACE);
        gl.colorMask(true,true,true,true);set(p,s,o);const b=bind(p,quad);gl.drawArrays(gl.TRIANGLES,0,6);gl.deleteBuffer(b);
        check('Production render pass has no GL error ('+name+')',gl.getError()===gl.NO_ERROR);passes.push({name,width:t?t.width:width,height:t?t.height:height,hdr:!!t?.hdr});
      }
      function pixels(t){
        gl.bindFramebuffer(gl.FRAMEBUFFER,t?t.framebuffer:null);const w=t?t.width:width,h=t?t.height:height;
        const a=t?.hdr?new Float32Array(w*h*4):new Uint8Array(w*h*4);
        gl.readPixels(0,0,w,h,gl.RGBA,t?.hdr?gl.FLOAT:gl.UNSIGNED_BYTE,a);
        check('Readback has no GL error ('+(t?t.name:'final canvas')+')',gl.getError()===gl.NO_ERROR);return a;
      }
      function rgbStats(a){let max=0,sum=0,nonzero=0;for(let i=0;i<a.length;i+=4){let active=false;for(let c=0;c<3;c++){const v=a[i+c];if(!Number.isFinite(v))throw Error('Nonfinite radiance');max=Math.max(max,v);sum+=v;active||=v>1e-7;}if(active)nonzero++;}return{max,sum,nonzero,pixelCount:a.length/4};}
      const saveCanvas=name=>captures.push({name,png:canvas.toDataURL('image/png').split(',')[1]});
      function saveHdr(name,a,t){
        const c=document.createElement('canvas');c.width=t.width;c.height=t.height;const ctx=c.getContext('2d'),im=ctx.createImageData(t.width,t.height);
        for(let y=0;y<t.height;y++)for(let x=0;x<t.width;x++){const i=(y*t.width+x)*4,j=((t.height-y-1)*t.width+x)*4;for(let k=0;k<3;k++)im.data[j+k]=255*Math.pow(1-Math.exp(-Math.max(0,a[i+k])),1/2.2);im.data[j+3]=255;}
        ctx.putImageData(im,0,0);captures.push({name,png:c.toDataURL('image/png').split(',')[1],diagnostic:'Selected HDR source visualized with 1-exp(-radiance) and gamma 2.2; linear radiance statistics are retained in report'});
      }
      const coreOnly={...common,...camera([0,0,8]),uObjectMode:1,uGranular:2,LensStrength:0,uBend:0,uDensity:0,HaloStrength:0,
        JetStrength:0,uShellStrength:0,uCore:.58,uTilt:0,uCoreEmission:4,uCoreGlow:1.2,uCoreGlowRadius:2.6,uExposure:.55,
        uCoreColor:[.855,.956,1],uCoreEdgeColor:[.098,.521,1],uCoreGradientStrength:.65};
      function select(o=coreOnly){drawTo(world,payload.current.world,source,{...o,LensProbeMode:3});return pixels(source);}
      const selected=select(),selectedStats=rgbStats(selected);saveHdr('selected-core-linear-hdr',selected,source);
      check('Actual selected core remains HDR before exposure',selectedStats.max>1&&selectedStats.nonzero>30&&selectedStats.nonzero<hw*hh*.06,selectedStats);
      details.source={selected: selectedStats,policy:'Actual BH_CORE_CAPTURE radiance after disk transmission; same foreground and artistic terrain policy as visible core',width:hw,height:hh,format:'RGBA16F'};
      for(const[name,o]of[['black object mode',{uObjectMode:0}],['glow disabled',{uCoreGlow:0}],['core gain disabled',{uCoreEmission:0}],['black RGB with colored edge',{uCoreColor:[0,0,0],uCoreEdgeColor:[0,1,1]}]]){
        const a=select({...coreOnly,...o}),stats=rgbStats(a);check('Selected source is exactly dark for '+name,stats.max===0,stats);details.source[name]=stats;
      }
      const brightScene=new Uint8Array(width*height*4).fill(255);
      function setColor(a){for(const unit of[0,3]){gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,units[unit]);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,width,height,gl.RGBA,gl.UNSIGNED_BYTE,a);}}
      setColor(brightScene);const brightSource=select(),brightDiff=difference(selected,brightSource);
      check('Fully white scene cannot contaminate selected core HDR',brightDiff.max===0,brightDiff);
      const noCoreBright=select({...coreOnly,uCoreEmission:0});check('Bright scene with disabled core creates zero selected radiation',rgbStats(noCoreBright).max===0);setColor(background);
      const unrelatedSource=select({...coreOnly,JetStrength:4,uShellStrength:4,HaloStrength:4,uShellColor:[1,0,0],uJetBaseColor:[0,1,0],uHot:[0,0,1],uCool:[1,0,1]}),unrelatedDiff=difference(selected,unrelatedSource);
      check('Disk additive color halo jets and shells are excluded from core selection',unrelatedDiff.max===0,unrelatedDiff);
      const staticSource=select({...coreOnly,uTime:999,uJetClock:123,uShellClock:456,uOrbitPhase:5,uShearLocal:7}),staticDiff=difference(selected,staticSource);
      check('Stationary isolated core source does not animate with transport clocks',staticDiff.max===0,staticDiff);
      const tint=select({...coreOnly,uCoreColor:[1,0,0],uCoreEdgeColor:[1,0,0],uCoreGradientStrength:0});let wrongTint=0;for(let i=0;i<tint.length;i+=4)wrongTint=Math.max(wrongTint,tint[i+1],tint[i+2]);
      check('Source spectrum follows only the actual selected core material',rgbStats(tint).max>1&&wrongTint===0,{wrongTint});
      const zDepth=d=>((far+near)/(far-near)-2*far*near/((far-near)*d))*.5+.5;
      setDepth(new Float32Array(width*height).fill(zDepth(3)));
      gl.activeTexture(gl.TEXTURE0+2);gl.bindTexture(gl.TEXTURE_2D,units[2]);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,2,1,gl.RGBA,gl.FLOAT,new Float32Array([-100,-100,-100,1,100,100,100,1]));
      const actorSource=select({...coreOnly,ForegroundCount:1}),actorStats=rgbStats(actorSource);
      check('Classified foreground entity fully hides selected core source',actorStats.max===0,actorStats);
      const terrainSource=select({...coreOnly,ForegroundCount:0}),terrainDiff=difference(selected,terrainSource);
      check('Terrain source inherits the existing artistic core visibility policy',terrainDiff.max===0,terrainDiff);
      details.source.foreground=actorStats;details.source.terrainPolicy=terrainDiff;setDepth(skyDepth);
      const diskEye={...coreOnly,...camera([0,1.3,8]),uTilt:0,uThickness:.35,uInner:.62,uOuter:3.4,uGranular:0};
      const unobscured=select({...diskEye,uDensity:0}),attenuated=select({...diskEye,uDensity:8});let sourceGrowth=0;for(let i=0;i<unobscured.length;i+=4)for(let c=0;c<3;c++)sourceGrowth=Math.max(sourceGrowth,attenuated[i+c]-unobscured[i+c]);
      const attenuationDiff=difference(unobscured,attenuated);check('Selected HDR captures actual disk transmission before core light',sourceGrowth<.002&&attenuationDiff.max>.05,{sourceGrowth,...attenuationDiff});details.source.diskTransmission={sourceGrowth,...attenuationDiff};
      function opticalParams(o){
        const vp=o.ViewProjection,eye=o.CameraOriginRelative,center=o.CenterRelative,point=[...center,1],clip=Array.from({length:4},(_,r)=>point.reduce((a,n,k)=>a+vp[k*4+r]*n,0));
        const distance=Math.hypot(...center.map((v,i)=>v-eye[i])),corePixels=Math.abs(vp[5])*height*.5*o.uCore*o.WorldScale/Math.max(distance,.01);
        const radiusPixels=Math.max(.6,Math.min(height*.28,corePixels*(o.uCoreGlowRadius-1)*.32));
        return{radiusPixels,corePixels,center:[clip[0]/clip[3]*.5+.5,clip[1]/clip[3]*.5+.5],scale:[corePixels*o.uCoreGlowRadius*1.6/width,corePixels*o.uCoreGlowRadius*1.6/height]};
      }
      function diffuse(o,sourceOverride=null,name){
        // Full original field is preserved in its own base target. No pass
        // reads from the target it is writing, including the final composite.
        drawTo(world,payload.current.world,base,{...o,LensProbeMode:0});const baseData=pixels(base);
        if(sourceOverride){gl.activeTexture(gl.TEXTURE0+7);gl.bindTexture(gl.TEXTURE_2D,source.texture);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,hw,hh,gl.RGBA,gl.FLOAT,sourceOverride);}
        else select(o);
        const optical=opticalParams(o),d=[optical.radiusPixels/width,optical.radiusPixels/height];
        drawTo(blur,payload.current.core_blur,ping,{Direction:[d[0],0]},[source.texture],'near horizontal');
        drawTo(blur,payload.current.core_blur,nearGlow,{Direction:[0,d[1]]},[ping.texture],'near vertical');
        drawTo(blur,payload.current.core_blur,ping,{Direction:[d[0]*2.5,0]},[source.texture],'wide horizontal');
        drawTo(blur,payload.current.core_blur,wideGlow,{Direction:[0,d[1]*2.5]},[ping.texture],'wide vertical');
        const nearData=pixels(nearGlow),wideData=pixels(wideGlow);
        drawTo(composite,payload.current.core_composite,null,{Intensity:o.uCoreGlow,Exposure:o.uExposure,GlareCenter:optical.center,GlareScale:optical.scale},[base.texture,nearGlow.texture,wideGlow.texture]);
        const data=pixels(null);if(name)saveCanvas(name);return{data,baseData,nearData,wideData,optical};
      }
      const corePipeline=diffuse(coreOnly,null,'core-radiance-composited'),coreDiff=difference(corePipeline.baseData,corePipeline.data);
      const nearStats=rgbStats(corePipeline.nearData),wideStats=rgbStats(corePipeline.wideData);
      check('Both blur scales spread selected HDR outside core pixels',nearStats.nonzero>selectedStats.nonzero&&wideStats.nonzero>nearStats.nonzero,{selected:selectedStats,near:nearStats,wide:wideStats});
      function connectedIslands(a,threshold){
        const active=Array.from({length:hw*hh},(_,i)=>Math.max(a[i*4],a[i*4+1],a[i*4+2])>threshold),visited=new Uint8Array(hw*hh),sizes=[];
        for(let i=0;i<active.length;i++)if(active[i]&&!visited[i]){const queue=[i];visited[i]=1;let count=0;while(queue.length){const k=queue.pop(),x=k%hw,y=Math.floor(k/hw);count++;for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy,n=yy*hw+xx;if(xx>=0&&xx<hw&&yy>=0&&yy<hh&&active[n]&&!visited[n]){visited[n]=1;queue.push(n);}}}sizes.push(count);}
        return{threshold,count:sizes.length,sizes};
      }
      const wideIslands=connectedIslands(corePipeline.wideData,wideStats.max*.07);
      check('Wide diffusion produces one smooth source envelope without copied bright islands',wideIslands.count===1,wideIslands);
      check('Complete selected HDR pipeline visibly contributes to the world layer',coreDiff.max>20&&coreDiff.changed>500,coreDiff);
      let spill=0,corners=0,changedPixels=0;for(let y=0;y<height;y++)for(let x=0;x<width;x++){
        const k=(y*width+x)*4,src=(Math.floor(y/2)*hw+Math.floor(x/2))*4,changed=[0,1,2].some(c=>corePipeline.data[k+c]-corePipeline.baseData[k+c]>1);
        if(changed){changedPixels++;if(selected[src]+selected[src+1]+selected[src+2]===0)spill++;if((x<16||x>=width-16)&&(y<16||y>=height-16))corners++;}
      }
      check('Optical diffusion produces visible light beyond selected core silhouette',spill>100,{spill,changedPixels});
      check('Core diffusion is local and does not bleach the screen corners',corners===0&&changedPixels<width*height*.25,{corners,changedPixels,totalPixels:width*height});
      const staticPipeline=diffuse({...coreOnly,uTime:999,uJetClock:123,uShellClock:456,uOrbitPhase:5},null,'core-radiance-static-clocks');
      const staticPipelineDiff=difference(corePipeline.data,staticPipeline.data);check('Full isolated core source blur and composite remain static as clocks advance',staticPipelineDiff.max===0,staticPipelineDiff);
      details.diffusion={coreDiff,near:nearStats,wide:wideStats,wideIslands,spill,changedPixels,corners,optical:corePipeline.optical,staticPipelineDiff};
      for(const[name,o]of[['glow disabled',{uCoreGlow:0}],['core gain disabled',{uCoreEmission:0}],['black RGB source',{uCoreColor:[0,0,0]}],['black object mode',{uObjectMode:0}],['zero exposure',{uExposure:0}]]){
        const r=diffuse({...coreOnly,...o},null,'pipeline-'+name.replaceAll(' ','-')),d=difference(r.data,r.baseData);check('Complete diffusion preserves base exactly for '+name,d.max===0,d);
      }
      setColor(brightScene);const brightDisabled=diffuse({...coreOnly,uCoreEmission:0},null,'bright-world-without-selected-core'),brightDisabledDiff=difference(brightDisabled.data,brightDisabled.baseData);
      check('Fully bright background creates no diffusion or aperture rays without selected source',brightDisabledDiff.max===0,brightDisabledDiff);setColor(background);
      setDepth(new Float32Array(width*height).fill(zDepth(3)));const actorPipeline=diffuse({...coreOnly,ForegroundCount:1},null,'foreground-actor-hides-source'),actorPipelineDiff=difference(actorPipeline.data,actorPipeline.baseData);
      check('Fully hidden core produces neither diffusion nor independent aperture flare',actorPipelineDiff.max===0,actorPipelineDiff);setDepth(skyDepth);
      const smallRadius=diffuse({...coreOnly,uCoreGlowRadius:1.2}),largeRadius=diffuse({...coreOnly,uCoreGlowRadius:3.8}),radiusSmall=rgbStats(smallRadius.wideData),radiusLarge=rgbStats(largeRadius.wideData);
      check('Glow radius changes actual optical blur support',radiusLarge.nonzero>radiusSmall.nonzero*1.5,{small:radiusSmall,large:radiusLarge});
      // A selected-source-only synthetic impulse tests kernel normalization
      // and source isolation independent of the scene geometry.
      const constantHdr=new Float32Array(hw*hh*4);for(let i=0;i<constantHdr.length;i+=4){constantHdr[i]=.15;constantHdr[i+1]=.35;constantHdr[i+2]=.7;constantHdr[i+3]=1;}
      const constant=diffuse(coreOnly,constantHdr);const middle=(Math.floor(hh/2)*hw+Math.floor(hw/2))*4;
      check('Each two-pass blur preserves constant linear RGB away from image boundary',Math.abs(constant.nearData[middle]-.15)<.002&&Math.abs(constant.nearData[middle+1]-.35)<.002&&Math.abs(constant.nearData[middle+2]-.7)<.002&&Math.abs(constant.wideData[middle+2]-.7)<.002,{near:Array.from(constant.nearData.slice(middle,middle+3)),wide:Array.from(constant.wideData.slice(middle,middle+3))});
      // Actual exported defaults, frozen only at t=8. These GPU images render
      // the production world and new optical layers; Forge particles are not
      // included in this synthetic image (their GPU state was tested above).
      const dp=payload.defaultRecipe.parameters,linearByte=b=>{const c=b/255;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);},linearHex=h=>[1,3,5].map(i=>linearByte(parseInt(h.slice(i,i+2),16)));
      const cap=s=>s[0].toUpperCase()+s.slice(1),phase=(v,p)=>((v%p)+p)%p;
      const defaultWorld={...common,...camera([0,5,16]),WorldScale:dp.scale,uObjectMode:1,uGranular:['classic','granular','ribbons','dust','cinematic'].indexOf(dp.diskStyle),LensStrength:dp.lens?dp.lensStrength:0,LensRadius:dp.lensRadius,HaloStrength:dp.halo,HaloRing:dp.haloRing,HaloDisk:dp.haloDisk,HaloAura:dp.haloAura,uInfall:dp.infallSpeed};
      for(const k of['core','inner','outer','thickness','tilt','bend','density','speed','seed','steps','exposure','coreEmission','coreGlow','coreGlowRadius','beaming','diskGradientPosition','coreGradientStrength','bandDetail','dustScale','dustFill','filament','spiral','haloRadius','haloWidth','haloSharpness','haloGradientMix','haloDiskGradientMix','haloAuraGradientMix','shellStrength','shellRadius','shellWidth'])defaultWorld['u'+cap(k)]=dp[k];
      defaultWorld.uShellStrength=dp.shellEnabled?dp.shellStrength:0;
      for(const k of['jetStrength','jetLength','jetWidth'])defaultWorld[cap(k)]=dp[k];
      for(const k of['hot','cool','coreColor','coreEdgeColor','diskMid','haloGradientStart','haloGradientEnd','haloDiskInnerColor','haloDiskOuterColor','haloAuraInnerColor','haloAuraOuterColor','jetBaseColor','jetTipColor','shellColor'])defaultWorld['u'+cap(k)]=linearHex(dp[k]);
      const defaultAt=t=>({...defaultWorld,uTime:t,uOrbitPhase:phase(t*dp.speed,2*Math.PI),uRadialPhase:-phase(t*dp.infallSpeed,128),uClassicRadialPhase:-phase(t*dp.infallSpeed,2*Math.PI),uShearLocal:phase(Math.abs(t*dp.speed),8),uShearParity:Math.floor(Math.abs(t*dp.speed)/8)%2,uDustDriftA:phase(t*dp.speed*.35,12),uDustDriftB:phase(-t*dp.speed*.25,12),uJetClock:t*dp.jetSpeed,uShellClock:t*dp.shellSpeed});
      const actualDefault=diffuse(defaultAt(8),null,'white-default-radiance-t8'),actualDefaultSource=select(defaultAt(8));saveHdr('white-default-selected-source-t8',actualDefaultSource,source);
      const actualDefaultLater=diffuse(defaultAt(8.35),null,'white-default-radiance-t8-35'),defaultDiff=difference(actualDefault.data,actualDefaultLater.data),defaultGlowDiff=difference(actualDefault.data,actualDefault.baseData);
      check('Actual exported preset uses the exact supplied defaults and independent shell switch',dp.coreGlow===.8&&dp.coreGlowRadius===1.05&&dp.time===37862.8&&dp.jetStrength===0&&dp.shellEnabled===false&&Object.keys(dp).length===75,{coreGlow:dp.coreGlow,coreGlowRadius:dp.coreGlowRadius,fieldCount:Object.keys(dp).length});
      check('Actual default selected core stays HDR and optical diffusion contributes',rgbStats(actualDefaultSource).max>1&&defaultGlowDiff.changed>0,{source:rgbStats(actualDefaultSource),diffusion:defaultGlowDiff});
      check('Actual default outer flows retain visible transport motion',defaultDiff.changed>100,defaultDiff);
      details.defaultRecipe={schema:payload.defaultRecipe.schema,parameterCount:Object.keys(dp).length,elapsedSeconds:[8,8.35],uniformsAt8:defaultAt(8),worldAndCoreOpticalLayersOnly:true,particlesValidatedSeparatelyByTransformFeedback:true,source:rgbStats(actualDefaultSource),diffusion:defaultGlowDiff,motion:defaultDiff};
      details.passes=passes;
      const debug=gl.getExtension('WEBGL_debug_renderer_info');
      return{status:'passed',fixture:'white-hole-radiance-production-gpu/1',checks,details,captures,gpu:{vendor:debug?gl.getParameter(debug.UNMASKED_VENDOR_WEBGL):gl.getParameter(gl.VENDOR),renderer:debug?gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION)},scope:{productionGlslUsed:true,sourceAdaptation:'GLSL150 version/precision adaptation only; production main and complete half-resolution HDR source/blur/composite pipeline execute unchanged',forgeRuntimeVerified:false,limitations:'SwiftShader software WebGL2 with synthetic color/depth textures and camera matrices. Verifies source selection and shader passes; does not verify NVIDIA driver performance, Forge GL state restoration, or in-game appearance. Terrain follows the existing artistic core compositor; optical spill may cover foreground.'}};
    },payload);
    for(const c of result.captures){fs.writeFileSync(path.join(output,c.name+'.png'),Buffer.from(c.png,'base64'));delete c.png;c.path=c.name+'.png';}
    result.generatedAt=new Date().toISOString();result.sources=Object.fromEntries(['current','baseline'].map(group=>[group,Object.fromEntries(Object.entries(payload[group]).flatMap(([name,s])=>['vs','fs','json'].map(kind=>[name+'.'+kind,{sha256:crypto.createHash('sha256').update(s[kind]).digest('hex'),bytes:Buffer.byteLength(s[kind])}])))]));result.defaultRecipeSource={path:'src/main/resources/assets/vfxworkbench/recipes/white-ring-default.json',sha256:crypto.createHash('sha256').update(defaultRecipeText).digest('hex'),bytes:Buffer.byteLength(defaultRecipeText)};
    fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(result,null,2));if(fs.existsSync(path.join(output,'failure.json')))fs.unlinkSync(path.join(output,'failure.json'));
    const cards=result.captures.map(c=>`<figure><img src="${c.path}" alt="${c.name}"><figcaption>${c.name}</figcaption></figure>`).join('');
    fs.writeFileSync(path.join(output,'index.html'),`<!doctype html><meta charset="utf-8"><title>White-hole radiance GPU fixture</title><style>body{background:#101622;color:#dde5f4;font:15px system-ui;margin:32px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(256px,1fr));gap:16px}figure{margin:0;padding:12px;background:#1b2537}img{width:100%}figcaption{margin-top:8px}a{color:#97c6ff}</style><h1>White-hole radiance GPU fixture</h1><p>${result.checks.length} checks passed in software SwiftShader WebGL2. These synthetic production-shader checks execute the selected core HDR, half-size RGBA16F blur and full-size composite; they are separate from Forge runtime and hardware performance evidence.</p><p><a href="report.json">Exact report and source hashes</a></p><main>${cards}</main>`);
    console.log(JSON.stringify({status:result.status,checks:result.checks.length,captures:result.captures.length,gpu:result.gpu,report:path.join(output,'report.json'),forgeRuntimeVerified:false},null,2));
  }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({status:'failed',message:error.message,stack:error.stack},null,2));console.error(error);process.exitCode=1;});
