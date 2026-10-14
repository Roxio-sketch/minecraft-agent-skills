/* Executes the complete production GLSL in real WebGL2. This synthetic
 * scene fixture is separate from Forge integration and hardware performance. */
const fs=require('fs'),path=require('path'),crypto=require('crypto');
let chromium;try{({chromium}=require('playwright'));}catch{({chromium}=require('C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));}
const root=path.resolve(__dirname,'..');
const output=path.join(root,'evidence/white-hole-outflow-20261007/gpu');
const shaderDir=path.join(root,'src/main/resources/assets/vfxworkbench/shaders/core');
const baselineDir=path.join(root,'evidence/white-hole-outflow-20261007/source-before/src/main/resources/assets/vfxworkbench/shaders/core');
fs.mkdirSync(output,{recursive:true});
const load=dir=>Object.fromEntries(['world','particles'].map(name=>{
  const json=fs.readFileSync(path.join(dir,`bh_${name}.json`),'utf8');
  return[name,{vs:fs.readFileSync(path.join(dir,`bh_${name}.vsh`),'utf8'),fs:fs.readFileSync(path.join(dir,`bh_${name}.fsh`),'utf8'),json,uniforms:JSON.parse(json).uniforms}];
}));
const defaultRecipeText=fs.readFileSync(path.join(root,'src/main/resources/assets/vfxworkbench/recipes/white-ring-default.json'),'utf8');
const payload={current:load(shaderDir),baseline:load(baselineDir),defaultRecipe:JSON.parse(defaultRecipeText)};
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
      for(let style=0;style<5;style++)for(const jets of[0,1.45]){const o={uGranular:style,JetStrength:jets,uObjectMode:0};const a=render(baselineWorld,payload.baseline.world,o),b=render(world,payload.current.world,o,false,jets===0?'black-'+styleNames[style]:undefined),d=difference(a,b);details.worldBaseline.push({style:styleNames[style],legacyJets:jets,...d});check('Black '+styleNames[style]+' with legacy jet '+jets+' is pixel-exact to delivered baseline',d.max===0,d);}
      // The probe only replaces main. Field functions, branch logic, color,
      // lifecycle and all uniforms remain those of the production fragment.
      const fieldSource={...payload.current.world,fs:payload.current.world.fs.replace(/\bvoid\s+main\s*\(\s*\)/,'void productionMain()')+'\nuniform int ProbeKind;uniform vec2 ProbeRange;uniform float ProbeTransverse;uniform vec3 ProbePoint;\nvoid main(){float x=mix(ProbeRange.x,ProbeRange.y,texCoord.x);vec3 q=ProbeKind==0?vec3(ProbeTransverse,x,0):vec3(x,0,0);if(ProbeKind==2||ProbeKind==4)q=ProbePoint;vec3 c=ProbeKind==1?whiteShellEmission(q):(ProbeKind>=3?whiteCoreGlowEmission(q):whiteJetEmission(q));fragColor=vec4(c,1);}\n'};
      const field=link(fieldSource,'same-production-source outflow fields');
      const fieldCommon={uObjectMode:1,JetStrength:2,JetLength:6.2,JetWidth:.32,uShellStrength:1.2,uShellRadius:5,uShellWidth:.065,uJetBaseColor:[1,1,1],uJetTipColor:[1,1,1],uShellColor:[1,1,1],ProbeKind:0,ProbeRange:[.7,6],ProbeTransverse:0};
      const profile=(o)=>{const a=render(field,fieldSource,{...fieldCommon,...o},true);return Array.from({length:width},(_,i)=>({x:(o.ProbeRange||fieldCommon.ProbeRange)[0]+(i+.5)/width*((o.ProbeRange||fieldCommon.ProbeRange)[1]-(o.ProbeRange||fieldCommon.ProbeRange)[0]),rgb:Array.from(a.slice(i*4,i*4+3)),value:a[i*4]+a[i*4+1]+a[i*4+2]}));};
      const peaks=rows=>rows.filter((r,i)=>i>0&&i<rows.length-1&&r.value>rows[i-1].value&&r.value>=rows[i+1].value&&r.value>.02);
      const point=(kind,q,o={})=>{const a=render(field,fieldSource,{...fieldCommon,...o,ProbeKind:kind,ProbePoint:q},true);return Array.from(a.slice(0,3));};
      details.transport={};
      const jetA=profile({uJetClock:.8}),jetB=profile({uJetClock:1.05});
      const interior=peaks(jetA).filter(p=>p.x>1.3&&p.x<4.5),later=peaks(jetB);
      const tracks=interior.map(a=>{const b=later.reduce((best,p)=>Math.abs(p.x-a.x-.25)<Math.abs(best.x-a.x-.25)?p:best,later[0]);return{from:a.x,to:b.x,delta:b.x-a.x,error:Math.abs(b.x-a.x-.25)};});
      details.transport.jets={deltaClock:.25,tracks,profileA:jetA,profileB:jetB};
      check('Same jet-density maxima travel outward through fixed local space',tracks.length>=2&&tracks.every(t=>t.delta>0&&t.error<.08),{tracks});
      check('Changing jet clock actually changes the fixed-space emission field',difference(jetA.flatMap(r=>r.rgb),jetB.flatMap(r=>r.rgb)).max>.05);
      const shellA=profile({ProbeKind:1,ProbeRange:[.55,5.25],uShellClock:.8}),shellB=profile({ProbeKind:1,ProbeRange:[.55,5.25],uShellClock:1.05});
      const shellPeaksA=peaks(shellA),shellPeaksB=peaks(shellB);
      const shellTracks=shellPeaksA.filter(a=>a.x>1&&a.x<4.5).map(a=>{const b=shellPeaksB.reduce((best,p)=>Math.abs(p.x-a.x-.25)<Math.abs(best.x-a.x-.25)?p:best,shellPeaksB[0]);return{from:a.x,to:b.x,delta:b.x-a.x,error:Math.abs(b.x-a.x-.25)};});
      details.transport.shells={deltaClock:.25,tracks:shellTracks,profileA:shellA,profileB:shellB};
      check('Two shell-density maxima expand by accumulated local radial distance',shellTracks.length===2&&shellTracks.every(t=>t.delta>0&&t.error<.04),{shellTracks});
      details.transport.shellWrap=[];
      const shellSpan=5-.58*1.08;
      for(const boundary of[0,shellSpan*.5,shellSpan]){const a=profile({ProbeKind:1,ProbeRange:[.55,5.25],uShellClock:boundary-.00001}),b=profile({ProbeKind:1,ProbeRange:[.55,5.25],uShellClock:boundary+.00001}),d=difference(a.flatMap(r=>r.rgb),b.flatMap(r=>r.rgb));details.transport.shellWrap.push({boundary,...d});check('Real two-layer shell field is continuous at lifecycle boundary '+boundary,d.max<.004,d);}
      const bipolarPlus=profile({uJetClock:.8,ProbeRange:[1.2,5.5]}),bipolarMinus=profile({uJetClock:.8,ProbeRange:[-1.2,-5.5]});
      const bipolarDifference=difference(bipolarPlus.flatMap(r=>r.rgb),bipolarMinus.flatMap(r=>r.rgb));
      check('Both polar lobes use the same outward longitudinal emission state',bipolarDifference.max<.0001,bipolarDifference);
      const tintBase=[1,0,0],tintTip=[0,0,1],nearTint=point(2,[0,1.4,0],{uJetBaseColor:tintBase,uJetTipColor:tintTip}),farTint=point(2,[0,5,0],{uJetBaseColor:tintBase,uJetTipColor:tintTip});
      const ratio=c=>c[2]/Math.max(c[0]+c[2],1e-9);check('Jet color follows source-to-tip spatial position',ratio(farTint)>ratio(nearTint)+.5,{nearTint,farTint});
      const samePointLater=point(2,[0,3,0],{uJetClock:2.15,uJetBaseColor:tintBase,uJetTipColor:tintTip}),samePointEarlier=point(2,[0,3,0],{uJetClock:.8,uJetBaseColor:tintBase,uJetTipColor:tintTip});
      check('Jet advection changes brightness while preserving fixed-space gradient color',Math.abs(ratio(samePointLater)-ratio(samePointEarlier))<.00001,{samePointEarlier,samePointLater});
      const shellColor=profile({ProbeKind:1,ProbeRange:[.55,5.25],uShellColor:[.12,.34,.89]});const shellPeak=peaks(shellColor)[0];
      check('Shell color comes from the dedicated shell spatial material',Math.abs(shellPeak.rgb[0]/shellPeak.rgb[2]-.12/.89)<.0001&&Math.abs(shellPeak.rgb[1]/shellPeak.rgb[2]-.34/.89)<.0001,{shellPeak});
      const jetOff=profile({JetStrength:0}),shellOff=profile({ProbeKind:1,ProbeRange:[.55,5.25],uShellStrength:0});
      check('Zero jet strength exactly disables every real field sample',jetOff.every(r=>r.value===0));check('Zero shell strength exactly disables every real field sample',shellOff.every(r=>r.value===0));
      // Full composition, independent of the same-source field probes.
      const outflow={...camera([0,0,14]),uObjectMode:1,uGranular:2,LensStrength:0,uBend:0,uDensity:0,HaloStrength:0,uCoreEmission:0,JetStrength:2.8,JetLength:6.2,JetWidth:.38,uJetBaseColor:[.8,.95,1],uJetTipColor:[.03,.35,1],uShellStrength:1.8,uShellRadius:5,uShellWidth:.075,uShellClock:1.45,uJetClock:.85};
      const disabled={JetStrength:0,uShellStrength:0};
      const full=render(world,payload.current.world,outflow,false,'white-outflow-oblique'),off=render(world,payload.current.world,{...outflow,...disabled},false);
      const fullDiff=difference(full,off);check('Production main composes visible jet and shell radiation',fullDiff.changed>1500&&fullDiff.max>30,fullDiff);
      const zDepth=distance=>((far+near)/(far-near)-2*far*near/((far-near)*distance))*.5+.5;
      const wall=new Float32Array(width*height).fill(zDepth(5));setDepth(wall);
      const blocked=render(world,payload.current.world,outflow,false,'white-outflow-wall-blocked'),blockedOff=render(world,payload.current.world,{...outflow,...disabled},false);const blockedDiff=difference(blocked,blockedOff);
      check('A real reconstructed foreground depth plane completely blocks new external outflow',blockedDiff.max===0,blockedDiff);
      const halfWall=new Float32Array(width*height);for(let y=0;y<height;y++)for(let x=0;x<width;x++)halfWall[y*width+x]=x<width/2?1:zDepth(5);setDepth(halfWall);
      const partial=render(world,payload.current.world,outflow,false,'white-outflow-half-wall'),partialOff=render(world,payload.current.world,{...outflow,...disabled},false);let clearChanged=0,blockedChanged=0;
      for(let y=0;y<height;y++)for(let x=0;x<width;x++)for(let c=0;c<3;c++){const i=(y*width+x)*4+c;if(partial[i]!==partialOff[i]){if(x<width/2)clearChanged++;else blockedChanged++;}}
      check('Depth clipping is local: clear pixels emit while wall pixels remain exact',clearChanged>500&&blockedChanged===0,{clearChanged,blockedChanged});setDepth(skyDepth);
      details.depth={full:fullDiff,fullyBlocked:blockedDiff,halfWall:{clearChanged,blockedChanged}};
      const inside={...outflow,...camera([0,1.5,0],[0,1.5,-1]),JetStrength:0,uShellStrength:1.8,uShellClock:1.45};
      const insideOn=render(world,payload.current.world,inside,false,'white-shell-camera-inside'),insideOff=render(world,payload.current.world,{...inside,uShellStrength:0},false);const insideDiff=difference(insideOn,insideOff);
      check('Camera inside an expanding shell receives finite visible shell radiation',insideDiff.changed>500&&insideDiff.max>10,insideDiff);details.cameraInside=insideDiff;
      const inCore={...outflow,...camera([0,.2,0],[0,.2,-1])},coreOn=render(world,payload.current.world,inCore),coreOff=render(world,payload.current.world,{...inCore,...disabled}),coreDiff=difference(coreOn,coreOff);
      check('An observer inside the solid core receives no external ejecta through it',coreDiff.max===0,coreDiff);
      const axial={...outflow,...camera([0,12,0],[0,0,0],[0,0,1]),uShellStrength:0},axialOn=render(world,payload.current.world,axial),axialOff=render(world,payload.current.world,{...axial,JetStrength:0}),axialDiff=difference(axialOn,axialOff);
      check('Looking down the jet axis retains finite radiation through analytic cylinder support',axialDiff.changed>20&&axialDiff.max>5,axialDiff);
      const narrow={...outflow,JetWidth:.008,uShellWidth:.002},narrowFloat=render(world,payload.current.world,narrow,true);
      check('Minimum-width jet and shell full composition stays finite',Array.from(narrowFloat).every(Number.isFinite));
      const narrowOn=render(world,payload.current.world,narrow),narrowOff=render(world,payload.current.world,{...narrow,...disabled}),narrowDiff=difference(narrowOn,narrowOff);
      check('Thin-layer reconstruction preserves visible emission instead of missing the support',narrowDiff.changed>100&&narrowDiff.max>5,narrowDiff);
      details.edgeCases={insideCore:coreDiff,axialJet:axialDiff,narrowSupport:narrowDiff};
      const tilted=[];
      for(const tilt of[0,45]){const o={...outflow,...camera([14,0,0]),uTilt:tilt,uShellStrength:0};const a=render(world,payload.current.world,o,false,'white-bipolar-tilt-'+tilt),b=render(world,payload.current.world,{...o,JetStrength:0},false);let xx=0,yy=0,xy=0,weight=0,pos=0,neg=0;for(let py=0;py<height;py++)for(let px=0;px<width;px++){const k=(py*width+px)*4,w=Math.max(0,a[k]-b[k])+Math.max(0,a[k+1]-b[k+1])+Math.max(0,a[k+2]-b[k+2]),x=px+.5-width/2,y=py+.5-height/2;xx+=w*x*x;yy+=w*y*y;xy+=w*x*y;weight+=w;if(y>0)pos+=w;else neg+=w;}const angle=.5*Math.atan2(2*xy,xx-yy);tilted.push({tilt,axisDegrees:angle*180/Math.PI,xx,yy,xy,weight,upper:pos,lower:neg});}
      check('Full composed bipolar axis is vertical at zero disk tilt',tilted[0].weight>1000&&Math.abs(tilted[0].axisDegrees)>85,tilted[0]);
      check('Full composed bipolar axis rotates with the same disk orthonormal basis',Math.abs(Math.abs(tilted[1].axisDegrees)-45)<6&&tilted[1].upper>500&&tilted[1].lower>500,tilted[1]);details.basis=tilted;
      const frozen=render(world,payload.current.world,{...outflow,uTime:123,uJetClock:.85,uShellClock:1.45}),frozenDiff=difference(full,frozen);
      check('Fixed jet and shell clocks keep full outflow composition paused',frozenDiff.max===0,frozenDiff);
      const blackShell=render(world,payload.current.world,{...outflow,uObjectMode:0,uShellStrength:4}),blackNoShell=render(world,payload.current.world,{...outflow,uObjectMode:0,uShellStrength:0});check('Black mode ignores new shell effects even with nonzero shell controls',difference(blackShell,blackNoShell).max===0);
      // Core-corona probes call the real production field and compositor.
      const corona={ProbeKind:3,ProbeRange:[.56,1.2],uCore:.58,uCoreEmission:4,uCoreGlow:.8,uCoreGlowRadius:1.9,uCoreColor:[1,.25,.05],uCoreEdgeColor:[.04,.4,1],uCoreGradientStrength:.65};
      const coronaProfile=profile(corona),radiating=coronaProfile.filter(r=>r.x>.60&&r.x<1.085),coronaBound=.58*1.9;
      check('Core-corona field is positive and naturally decreases away from the core surface',radiating.length>50&&radiating.every((r,i)=>r.value>0&&(i===0||r.value<radiating[i-1].value)),{first:radiating[0],last:radiating.at(-1)});
      check('Configured corona radius is a strict finite support outside the solid core',coronaProfile.filter(r=>r.x<.58||r.x>=coronaBound).every(r=>r.value===0),{core:.58,bound:coronaBound});
      details.coreCorona={profile:coronaProfile};
      for(const [name,o] of [['zero strength',{uCoreGlow:0}],['zero core emission',{uCoreEmission:0}],['black source with colored edge',{uCoreColor:[0,0,0],uCoreEdgeColor:[0,1,1]}],['black object mode',{uObjectMode:0}]]){const a=profile({...corona,...o});check('Core-corona '+name+' produces exact zero radiation',a.every(r=>r.value===0));}
      const sourceTint=point(4,[.64,0,0],corona),recoloredOtherLayers=point(4,[.64,0,0],{...corona,uShellColor:[1,0,0],uJetBaseColor:[0,1,0],uJetTipColor:[0,1,0],uHot:[0,0,1],uCool:[1,0,1],uHaloGradientStart:[1,1,0],uHaloGradientEnd:[1,1,0]});
      check('Core-corona tint is independent of disk halo jet and shell colors',difference(sourceTint,recoloredOtherLayers).max===0,{sourceTint,recoloredOtherLayers});
      const edgeTintA=point(4,[.64,0,0],{...corona,uCoreColor:[1,0,0],uCoreEdgeColor:[0,0,1],uCoreGradientStrength:0}),edgeTintB=point(4,[.64,0,0],{...corona,uCoreColor:[1,0,0],uCoreEdgeColor:[0,0,1],uCoreGradientStrength:1});
      check('Core and core-edge source colors both contribute to the corona material',edgeTintA[0]>0&&edgeTintA[2]===0&&edgeTintB[2]>0&&edgeTintB[0]<edgeTintA[0],{edgeTintA,edgeTintB});
      const boundBefore=point(4,[1.3,0,0],corona),boundAfter=point(4,[1.3,0,0],{...corona,uCoreGlowRadius:3});
      check('Increasing core-corona radius expands the actual field support',boundBefore.every(v=>v===0)&&boundAfter.some(v=>v>0),{boundBefore,boundAfter});
      const noAnimation=profile({...corona,uTime:999,uOrbitPhase:3.5,uJetClock:123,uShellClock:456});const noAnimationDiff=difference(coronaProfile.flatMap(r=>r.rgb),noAnimation.flatMap(r=>r.rgb));
      check('Core-corona remains stationary while time and transport clocks advance',noAnimationDiff.max===0,noAnimationDiff);
      const coreOnly={...outflow,...camera([0,0,8]),JetStrength:0,uShellStrength:0,HaloStrength:0,uDensity:0,uCoreColor:[.855,.956,1],uCoreEdgeColor:[.098,.521,1],uCoreEmission:4,uCoreGlow:.8,uCoreGlowRadius:1.9,uCoreGradientStrength:.65};
      const coreWithCorona=render(world,payload.current.world,coreOnly,false,'white-core-body-and-corona'),coreBodyOnly=render(world,payload.current.world,{...coreOnly,uCoreGlow:0},false,'white-core-body-corona-off'),noCoreEmission=render(world,payload.current.world,{...coreOnly,uCoreGlow:0,uCoreEmission:0});
      const coronaDiff=difference(coreWithCorona,coreBodyOnly),bodyDiff=difference(coreBodyOnly,noCoreEmission),centerPixel=(Math.floor(height/2)*width+Math.floor(width/2))*4;
      check('Corona remains visibly integrated when disk halo jets and shells are all disabled',coronaDiff.changed>60&&coronaDiff.max>12,coronaDiff);
      check('Disabling corona preserves a bright independently emitting core body',coreBodyOnly[centerPixel]>220&&bodyDiff.max>150,{centerRgb:Array.from(coreBodyOnly.slice(centerPixel,centerPixel+3)),bodyDiff});
      let changedPixels=0,changedCorners=0;for(let y=0;y<height;y++)for(let x=0;x<width;x++){const k=(y*width+x)*4,changed=[0,1,2].some(c=>coreWithCorona[k+c]!==coreBodyOnly[k+c]);if(changed){changedPixels++;if((x<16||x>=width-16)&&(y<16||y>=height-16))changedCorners++;}}
      check('External core-corona stays local and does not whiten the whole frame',changedPixels<width*height*.10&&changedCorners===0,{changedPixels,totalPixels:width*height,changedCorners});
      setDepth(new Float32Array(width*height).fill(zDepth(3)));const wallCorona=render(world,payload.current.world,coreOnly),wallNoCorona=render(world,payload.current.world,{...coreOnly,uCoreGlow:0}),wallCoronaDiff=difference(wallCorona,wallNoCorona);
      check('Original scene depth blocks core-corona radiation independently of core-body policy',wallCoronaDiff.max===0,wallCoronaDiff);setDepth(skyDepth);
      const insideCorona={...coreOnly,...camera([.8,0,0],[.8,0,-1])},insideCoronaFloat=render(world,payload.current.world,insideCorona,true),insideCoronaOn=render(world,payload.current.world,insideCorona),insideCoronaOff=render(world,payload.current.world,{...insideCorona,uCoreGlow:0}),insideCoronaDiff=difference(insideCoronaOn,insideCoronaOff);
      check('Observer inside the corona receives finite locally integrated source radiation',Array.from(insideCoronaFloat).every(Number.isFinite)&&insideCoronaDiff.changed>500,insideCoronaDiff);
      const coreTimeChanged=render(world,payload.current.world,{...coreOnly,uTime:120,uJetClock:80,uShellClock:40}),coreTimeDiff=difference(coreWithCorona,coreTimeChanged);
      check('Full core-only composition stays stationary as clocks advance',coreTimeDiff.max===0,coreTimeDiff);
      const blackCoronaOptions={uObjectMode:0,uGranular:2,uCoreGlow:3,uCoreGlowRadius:4,uCoreEmission:4,uCoreColor:[1,1,1],uCoreEdgeColor:[0,1,1]};const blackCoronaNew=render(world,payload.current.world,blackCoronaOptions),blackCoronaOld=render(baselineWorld,payload.baseline.world,blackCoronaOptions),blackCoronaDiff=difference(blackCoronaNew,blackCoronaOld);
      check('Nonzero new corona controls keep the black-hole compositor pixel-exact to delivered baseline',blackCoronaDiff.max===0,blackCoronaDiff);
      details.coreCorona.composition={coronaDiff,bodyDiff,wallCoronaDiff,insideCoronaDiff,coreTimeDiff,blackCoronaDiff,changedPixels,changedCorners};
      // Convert the actual exported default recipe exactly as the Java host
      // sends material values. Time 8 is a fixed preview instant, not a recipe
      // modification. Particles are validated above by transform feedback;
      // these default captures show the complete world shader layer only.
      const dp=payload.defaultRecipe.parameters;
      check('Default world recipe contains the new explicit core emission controls',dp.coreGlow!==undefined&&dp.coreGlowRadius!==undefined&&dp.objectMode==='white',{fieldCount:Object.keys(dp).length,coreEmission:dp.coreEmission,coreGlow:dp.coreGlow,coreGlowRadius:dp.coreGlowRadius});
      const linearByte=b=>{const c=b/255;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);},linearHex=h=>[1,3,5].map(i=>linearByte(parseInt(h.slice(i,i+2),16)));
      const cap=s=>s[0].toUpperCase()+s.slice(1),phase=(v,p)=>((v%p)+p)%p;
      const defaultWorld={...camera([0,5,16]),WorldScale:dp.scale,uObjectMode:1,uGranular:['classic','granular','ribbons','dust','cinematic'].indexOf(dp.diskStyle),LensStrength:dp.lens?dp.lensStrength:0,LensRadius:dp.lensRadius,HaloStrength:dp.halo,HaloRing:dp.haloRing,HaloDisk:dp.haloDisk,HaloAura:dp.haloAura,uInfall:dp.infallSpeed};
      for(const k of ['core','inner','outer','thickness','tilt','bend','density','speed','seed','steps','exposure','coreEmission','coreGlow','coreGlowRadius','beaming','diskGradientPosition','coreGradientStrength','bandDetail','dustScale','dustFill','filament','spiral','haloRadius','haloWidth','haloSharpness','haloGradientMix','haloDiskGradientMix','haloAuraGradientMix','shellStrength','shellRadius','shellWidth'])defaultWorld['u'+cap(k)]=dp[k];
      for(const k of ['jetStrength','jetLength','jetWidth'])defaultWorld[cap(k)]=dp[k];
      for(const k of ['hot','cool','coreColor','coreEdgeColor','diskMid','haloGradientStart','haloGradientEnd','haloDiskInnerColor','haloDiskOuterColor','haloAuraInnerColor','haloAuraOuterColor','jetBaseColor','jetTipColor','shellColor'])defaultWorld['u'+cap(k)]=linearHex(dp[k]);
      const defaultAt=t=>({...defaultWorld,uTime:t,uOrbitPhase:phase(t*dp.speed,2*Math.PI),uRadialPhase:-phase(t*dp.infallSpeed,128),uClassicRadialPhase:-phase(t*dp.infallSpeed,2*Math.PI),uShearLocal:phase(Math.abs(t*dp.speed),8),uShearParity:Math.floor(Math.abs(t*dp.speed)/8)%2,uDustDriftA:phase(t*dp.speed*.35,12),uDustDriftB:phase(-t*dp.speed*.25,12),uJetClock:t*dp.jetSpeed,uShellClock:t*dp.shellSpeed});
      const actualDefault=render(world,payload.current.world,defaultAt(8),false,'white-default-recipe-world-t8'),actualDefaultLater=render(world,payload.current.world,defaultAt(8.35),false,'white-default-recipe-world-t8-35'),defaultMotion=difference(actualDefault,actualDefaultLater);
      check('Actual default recipe composes visible motion and a bright central core',defaultMotion.changed>300&&actualDefault[centerPixel]>220,{defaultMotion,centerRgb:Array.from(actualDefault.slice(centerPixel,centerPixel+3))});
      details.defaultRecipe={schema:payload.defaultRecipe.schema,parameterCount:Object.keys(dp).length,elapsedSeconds:[8,8.35],uniformsAt8:defaultAt(8),worldLayerOnly:true,particlesValidatedSeparatelyByTransformFeedback:true,defaultMotion};
      const debug=gl.getExtension('WEBGL_debug_renderer_info');
      return{status:'passed',fixture:'white-hole-outflow-production-gpu/1',checks,details,captures,gpu:{vendor:debug?gl.getParameter(debug.UNMASKED_VENDOR_WEBGL):gl.getParameter(gl.VENDOR),renderer:debug?gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION)},scope:{productionGlslUsed:true,sourceAdaptation:'GLSL150 version/precision adaptation only; field probes replace main while retaining production functions',forgeRuntimeVerified:false,limitations:'SwiftShader software WebGL2. Synthetic color/depth textures and camera matrices; does not verify NVIDIA driver performance, Forge event integration or in-game appearance.'}};
    },payload);
    for(const c of result.captures){fs.writeFileSync(path.join(output,c.name+'.png'),Buffer.from(c.png,'base64'));delete c.png;c.path=c.name+'.png';}
    result.generatedAt=new Date().toISOString();result.sources=Object.fromEntries(['current','baseline'].map(group=>[group,Object.fromEntries(Object.entries(payload[group]).flatMap(([name,s])=>['vs','fs','json'].map(kind=>[name+'.'+kind,{sha256:crypto.createHash('sha256').update(s[kind]).digest('hex'),bytes:Buffer.byteLength(s[kind])}])))]));result.defaultRecipeSource={path:'src/main/resources/assets/vfxworkbench/recipes/white-ring-default.json',sha256:crypto.createHash('sha256').update(defaultRecipeText).digest('hex'),bytes:Buffer.byteLength(defaultRecipeText)};
    fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(result,null,2));if(fs.existsSync(path.join(output,'failure.json')))fs.unlinkSync(path.join(output,'failure.json'));
    const cards=result.captures.map(c=>`<figure><img src="${c.path}" alt="${c.name}"><figcaption>${c.name}</figcaption></figure>`).join('');
    fs.writeFileSync(path.join(output,'index.html'),`<!doctype html><meta charset="utf-8"><title>White-hole outflow GPU fixture</title><style>body{background:#101622;color:#dde5f4;font:15px system-ui;margin:32px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(256px,1fr));gap:16px}figure{margin:0;padding:12px;background:#1b2537}img{width:100%}figcaption{margin-top:8px}a{color:#97c6ff}</style><h1>White-hole outflow GPU fixture</h1><p>${result.checks.length} checks passed in software SwiftShader WebGL2. These are synthetic full-production-shader/field checks, not Forge runtime or hardware performance evidence.</p><p><a href="report.json">Exact report and source hashes</a></p><main>${cards}</main>`);
    console.log(JSON.stringify({status:result.status,checks:result.checks.length,captures:result.captures.length,gpu:result.gpu,report:path.join(output,'report.json'),forgeRuntimeVerified:false},null,2));
  }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({status:'failed',message:error.message,stack:error.stack},null,2));console.error(error);process.exitCode=1;});
