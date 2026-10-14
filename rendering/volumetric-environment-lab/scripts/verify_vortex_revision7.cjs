/* Validate observable WebGL behavior of the independent vortex workbench. */
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const lab=path.resolve(__dirname,'..'),dir=path.join(lab,'evidence/vortex-revision7/verify',process.env.VORTEX_DIAGNOSTIC==='v7a'?'diagnostic-v7a':'');
fs.mkdirSync(dir,{recursive:true});
if(fs.existsSync(path.join(dir,'report.json'))){
 const old=JSON.parse(fs.readFileSync(path.join(dir,'report.json'),'utf8'));
 const archive=path.join(dir,'history',`${old.previewSha256.slice(0,12)}-${Date.now()}`);fs.mkdirSync(archive,{recursive:true});
 for(const entry of fs.readdirSync(dir,{withFileTypes:true}))if(entry.isFile())fs.copyFileSync(path.join(dir,entry.name),path.join(archive,entry.name));
}
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const checks=[];
function check(name,passed,data){checks.push({name,passed:!!passed,...(data===undefined?{}:{data})});}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  // Use a source-served inert document, not a second complete scene renderer.
  // The regular workbench suite independently certifies JSON/UI behavior.
  await page.goto('http://127.0.0.1:8774/vortex/vortex-schema.json');
  const vortexSchema=JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-schema.json'),'utf8'));
  const work={recipe:JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/initial-recipe.json'),'utf8')),vortex:Object.fromEntries(Object.entries(vortexSchema.fields).map(([k,s])=>[k,s.default]))};
  await page.evaluate(async()=>{const {makeVortexNoise}=await import('/vortex/vortex-noise.js');const noise=makeVortexNoise();window.volumeLab={getNoiseData:()=>noise};});
  const core=fs.readFileSync(path.join(lab,'workbench/vortex/volume-core.glsl'),'utf8');
  const js=fs.readFileSync(path.join(lab,'workbench/vortex/preview.js'),'utf8');
  let scene=js.match(/const sceneSource = `([\s\S]*?)`;/)[1].split('void main() {')[0];
  const isolate=(signature,body)=>{const start=scene.indexOf(signature);if(start<0)throw Error('missing probe fixture function '+signature);const open=scene.indexOf('{',start);let balance=1,end=open+1;for(;end<scene.length&&balance;end++){if(scene[end]==='{')balance++;else if(scene[end]==='}')balance--;}if(balance)throw Error('unclosed probe fixture function');scene=scene.slice(0,open+1)+body+scene.slice(end-1);};
  // Shading and detached drops cannot change the central-carrier distance or
  // normal. Removing those paths avoids compiling a second full scene shader.
  isolate('vec3 vortexLiquidColor(', 'return vec3(0);');
  isolate('SceneHit traceVortexDrops(', 'return SceneHit(10000.0,vec3(0,1,0),vec3(0));');
  const contract=JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/contract.json'),'utf8'));
  const probeSetup=await page.evaluate(({core,scene,contract})=>{
   const canvas=document.createElement('canvas');canvas.width=canvas.height=1;
   const gl=canvas.getContext('webgl2',{antialias:false});
   if(!gl.getExtension('EXT_color_buffer_float'))throw Error('float attachment unavailable');
   function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
   const p=gl.createProgram();
   gl.attachShader(p,shader(gl.VERTEX_SHADER,'#version 300 es\nconst vec2 v[3]=vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));void main(){gl_Position=vec4(v[gl_VertexID],0,1);}'));
   gl.attachShader(p,shader(gl.FRAGMENT_SHADER,`#version 300 es\nprecision highp float;precision highp int;\n${core}\n${scene}\nuniform vec3 ProbePoint,ProbeOrigin,ProbeDirection;uniform float ProbeLimit;uniform int ProbeMode;
   void main(){
    if(ProbeMode==6){SceneHit h=traceVortexLiquid(ProbeOrigin,normalize(ProbeDirection),ProbeLimit);vec3 x=ProbeOrigin+normalize(ProbeDirection)*h.distance;fragColor=vec4(h.distance,length(h.normal),vortexLiquidDistance(x),1);}
    else if(ProbeMode==7)fragColor=vec4(vortexLiquidHeight(),vortexLiquidRadius(.2),vortexLiquidDistance(ProbePoint),1);
    else if(ProbeMode==13){float h=clamp(ProbePoint.y,0.,1.);vec2 a=vortexLiquidAxis(h);fragColor=vec4(vortexLiquidHeight(),vortexLiquidRadius(h),a);}
    else if(ProbeMode==14){vec4 f=liquidSurfaceFlowData(ProbePoint,.055),n=liquidSurfaceFlowData(ProbePoint,.14);fragColor=vec4(liquidSpiralPhase(ProbePoint),liquidSpiralMaterialPhase(ProbePoint,f,n),vortexLiquidDistance(ProbePoint),liquidFroth(ProbePoint,vec3(0,1,0)));}
    else if(ProbeMode==15){vec3 v=liquidSurfaceVelocity(ProbePoint);fragColor=vec4(v,liquidSpiralPhase(ProbePoint));}
    else if(ProbeMode==17){LiquidPoint p=liquidLocalPoint(ProbePoint);fragColor=vec4(p.uv,p.s,p.h);}
    else if(ProbeMode==18)fragColor=vec4(liquidWorldFromLocal(ProbePoint.y,ProbePoint.xz,Time),1);
    else fragColor=vec4(0);
   }`));
   gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));
   gl.useProgram(p);gl.bindVertexArray(gl.createVertexArray());
   const noise=window.volumeLab.getNoiseData(),noiseTex=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_3D,noiseTex);gl.texImage3D(gl.TEXTURE_3D,0,gl.RGBA8,noise.size,noise.size,noise.size,0,gl.RGBA,gl.UNSIGNED_BYTE,noise.data);for(const axis of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T,gl.TEXTURE_WRAP_R])gl.texParameteri(gl.TEXTURE_3D,axis,gl.REPEAT);gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.uniform1i(gl.getUniformLocation(p,'VortexNoiseTex'),0);
   const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texStorage2D(gl.TEXTURE_2D,1,gl.RGBA32F,1,1);
   const fb=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,tex,0);
   if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('incomplete framebuffer');
   const loc=n=>gl.getUniformLocation(p,n);
   // Isolate the continuous carrier from detached droplets. The actual-frame
   // review retains normal drops; this fixture prevents a nearer drop from
   // being mistaken for a failed central-carrier ray intersection.
   gl.uniform4fv(loc('WaterDrops[0]'),new Float32Array(96*4));
   window.vortexProbe=(work,options={})=>{
    const base={...work.recipe.parameters,...options.base},v={...work.vortex,...options.vortex};
    gl.useProgram(p);gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.viewport(0,0,1,1);
    for(const [name,fields] of Object.entries(contract.uniforms)){
     const values=fields.map(k=>base[k]);if(name==='Bounds')values[3]=Math.max(values[3],v.topRadius+v.bend+20,v.ringRadius+v.ringWidth*3);
     if(name==='ViewSteps'||name==='LightSteps')gl.uniform1i(loc(name),values[0]);else if(values.length===1)gl.uniform1f(loc(name),values[0]);else if(values.length===3)gl.uniform3fv(loc(name),values);else gl.uniform4fv(loc(name),values);
    }
    for(const [name,values] of Object.entries({VortexShape:[v.bottomRadius,v.topRadius,v.profile,v.hollow],VortexFlow:[v.rotation,v.rise,v.bend,v.turbulence],VortexRing:[v.ringRadius,v.ringWidth,v.sprayDensity,v.sprayHeight],VortexWater:[v.waterDepth,v.waveAmp,v.waveSpeed,v.foam],VortexLife:[v.duration,v.inflow,v.canopyDensity,v.mode]}))gl.uniform4fv(loc(name),values);
    gl.uniform1f(loc('Time'),options.time??20);gl.uniform1i(loc('ProbeMode'),options.mode??0);
    gl.uniform3fv(loc('ProbePoint'),options.point??[12,80,5]);gl.uniform3fv(loc('ProbeOrigin'),options.origin??[0,80,200]);gl.uniform3fv(loc('ProbeDirection'),options.direction??[0,0,-1]);gl.uniform1f(loc('ProbeLimit'),options.limit??1000);
    gl.drawArrays(gl.TRIANGLES,0,3);const output=new Float32Array(4);gl.readPixels(0,0,1,1,gl.RGBA,gl.FLOAT,output);
    if(gl.getError()!==gl.NO_ERROR)throw Error('GPU probe error');return Array.from(output);
   };
   return {renderer:gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION)};
  },{core,scene,contract});
  console.log(JSON.stringify({stage:'probe-ready',previewSha256:sha(js)}));
  const probe=options=>page.evaluate(({w,o})=>window.vortexProbe(w,o),{w:work,o:options});
  const noiseDataSha256=await page.evaluate(async()=>{const bytes=window.volumeLab.getNoiseData().data;const digest=await crypto.subtle.digest('SHA-256',bytes);return [...new Uint8Array(digest)].map(x=>x.toString(16).padStart(2,'0')).join('');});
  const wrap=x=>Math.atan2(Math.sin(x),Math.cos(x));
  const b=work.recipe.parameters;
  const observations={sections:[],spiral:[],transport:[],formation:[],rays:[]};
  for(const mode of [1,2]){
   const diagnostic={mode,bend:0,turbulence:0};
   const matured=await probe({mode:13,point:[0,.5,0],vortex:diagnostic,time:30});
   const height=matured[0];
   for(const h of [.16,.32,.48,.64,.80]){
    const setup=await probe({mode:13,point:[0,h,0],vortex:diagnostic,time:30});
    const center=[b.centerX+setup[2],b.bottom+height*h,b.centerZ+setup[3]];
    const radius=setup[1],angles=32,section={mode,h,height,radius,bulk:[],boundary:[]};
    for(let i=0;i<angles;i++){
     const theta=i*Math.PI*2/angles,radial=[Math.cos(theta),0,Math.sin(theta)];
     const at=r=>center.map((x,j)=>x+radial[j]*r);
     for(const fraction of [.25,.50]){
      const v=await probe({mode:7,point:at(radius*fraction),vortex:diagnostic,time:30});
      section.bulk.push({theta,fraction,distance:v[2]});
     }
     // First connected negative-to-positive radial crossing: detached outer
     // sheets are deliberately not accepted as an intact central section.
     let lo=0,hi=radius*1.6+12;
     const outside=await probe({mode:7,point:at(hi),vortex:diagnostic,time:30});
     if(outside[2]<=0)throw Error('section search did not bracket liquid');
     const step=hi/40;let previous=0;
     for(let r=step;r<=hi+.001;r+=step){
      const v=await probe({mode:7,point:at(r),vortex:diagnostic,time:30});
      if(v[2]>=0){lo=previous;hi=r;break;}previous=r;
     }
     for(let j=0;j<11;j++){
      const mid=(lo+hi)/2,v=await probe({mode:7,point:at(mid),vortex:diagnostic,time:30});
      if(v[2]<0)lo=mid;else hi=mid;
     }
     const r=(lo+hi)/2,phase=await probe({mode:14,point:at(r),vortex:diagnostic,time:30});
     section.boundary.push({theta,radius:r,phase:phase[0],materialPhase:phase[1],distance:phase[2]});
    }
    check(`mode ${mode}, section ${h}: central bulk covers every azimuth`,section.bulk.every(v=>Number.isFinite(v.distance)&&v.distance<0),{minimumInteriorMargin:Math.min(...section.bulk.map(v=>-v.distance)),samples:section.bulk.length});
    check(`mode ${mode}, section ${h}: all azimuths have a finite connected surface`,section.boundary.every(v=>Number.isFinite(v.radius)&&v.radius>radius*.5&&Math.abs(v.distance)<.035),{minimumRadius:Math.min(...section.boundary.map(v=>v.radius)),maximumRadius:Math.max(...section.boundary.map(v=>v.radius))});
    observations.sections.push(section);
   }
   const sections=observations.sections.filter(s=>s.mode===mode);
   for(const section of sections){
    const mean=section.boundary.reduce((a,v)=>a+v.radius,0)/section.boundary.length;
    const meanS=section.boundary.reduce((a,v)=>a+Math.cos(v.phase),0)/section.boundary.length;
    let covariance=0,variance=0,phaseVariance=0,real=0,imag=0;
    for(const v of section.boundary){const r=v.radius-mean,s=Math.cos(v.phase)-meanS;covariance+=r*s;variance+=r*r;phaseVariance+=s*s;real+=r*Math.cos(v.theta);imag+=r*Math.sin(v.theta);}
    const correlation=covariance/Math.sqrt(Math.max(variance*phaseVariance,1e-12));
    const amplitude=Math.hypot(real,imag)*2/section.boundary.length/mean;
    observations.spiral.push({mode,h:section.h,correlation,amplitude,peakAngle:Math.atan2(imag,real),meanRadius:mean});
   }
   const spiral=observations.spiral.filter(s=>s.mode===mode);
   check(`mode ${mode}: actual surface has a broad helical silhouette`,spiral.every(s=>s.amplitude>.025)&&spiral.filter(s=>s.correlation>.55).length>=4,{sections:spiral});
   const turns=spiral.slice(1).map((s,i)=>wrap(s.peakAngle-spiral[i].peakAngle));
   check(`mode ${mode}: crest turns consistently along height`,turns.every(d=>d>.10&&d<Math.PI),{observedPeakAngleSteps:turns});
   for(const h of [.25,.55,.78]){
    const setup=await probe({mode:13,point:[0,h,0],vortex:diagnostic,time:30});
    for(const theta of [.31,2.17]){
     const point=[b.centerX+setup[2]+setup[1]*.7*Math.cos(theta),b.bottom+height*h,b.centerZ+setup[3]+setup[1]*.7*Math.sin(theta)];
     const v=await probe({mode:15,point,vortex:diagnostic,time:30}),dt=.01;
     const along=s=>point.map((p,j)=>p+v[j]*s);
     const [minus,plus,stillMinus,stillPlus]=await Promise.all([
      probe({mode:14,point:along(-dt),vortex:diagnostic,time:30-dt}),probe({mode:14,point:along(dt),vortex:diagnostic,time:30+dt}),
      probe({mode:14,point,vortex:diagnostic,time:30-dt}),probe({mode:14,point,vortex:diagnostic,time:30+dt})]);
     const advectiveDerivative=wrap(plus[0]-minus[0])/(2*dt),fixedDerivative=wrap(stillPlus[0]-stillMinus[0])/(2*dt);
     observations.transport.push({mode,h,theta,velocity:v.slice(0,3),advectiveDerivative,fixedDerivative});
    }
   }
   const transport=observations.transport.filter(v=>v.mode===mode);
   check(`mode ${mode}: actual velocity transports the same coarse spiral`,transport.every(v=>v.velocity.every(Number.isFinite)&&v.velocity[1]>0&&Math.abs(v.advectiveDerivative)<.01+Math.abs(v.fixedDerivative)*.1),transport);
   const bent=[];
   for(const h of [.25,.55,.78]){
    const radius=(await probe({mode:13,point:[0,h,0],vortex:{mode},time:30}))[1];
    const embedded=await probe({mode:18,point:[radius*.7,height*h,0],vortex:{mode},time:30});
    const point=embedded.slice(0,3),v=await probe({mode:15,point,vortex:{mode},time:30}),dt=.01;
    const local=await probe({mode:17,point,vortex:{mode},time:30});
    const minus=await probe({mode:14,point:point.map((p,j)=>p-v[j]*dt),vortex:{mode},time:30-dt});
    const plus=await probe({mode:14,point:point.map((p,j)=>p+v[j]*dt),vortex:{mode},time:30+dt});
    bent.push({h,point,local,velocity:v.slice(0,3),advectiveDerivative:wrap(plus[0]-minus[0])/(2*dt)});
   }
   check(`mode ${mode}: moving bent-frame transport stays finite and coherent`,bent.every(v=>v.velocity.every(Number.isFinite)&&Math.abs(v.advectiveDerivative)<.025),bent);
   // Periodicity uses points on both sides of the atan branch, not textual
   // equality of angle formulas. Geometric and material quantities are read.
   const setup=await probe({mode:13,point:[0,.48,0],vortex:diagnostic,time:30});
   const seam=(theta)=>[b.centerX+setup[2]+setup[1]*.85*Math.cos(theta),b.bottom+height*.48,b.centerZ+setup[3]+setup[1]*.85*Math.sin(theta)];
   const left=await probe({mode:14,point:seam(Math.PI-1e-5),vortex:diagnostic,time:30}),right=await probe({mode:14,point:seam(-Math.PI+1e-5),vortex:diagnostic,time:30});
   check(`mode ${mode}: angular branch is continuous in geometry and material`,Math.abs(wrap(left[0]-right[0]))<.001&&Math.abs(wrap(left[1]-right[1]))<.001&&Math.abs(left[2]-right[2])<.01&&Math.abs(left[3]-right[3])<.005,{left,right});
   const duration=work.vortex.duration;
   for(const time of [0,duration*.45,duration*1.2]){
    const size=await probe({mode:13,point:[0,.5,0],vortex:{mode},time});
    const density=await probe({mode:7,point:[b.centerX,b.bottom+Math.max(8,size[0]*.3),b.centerZ],vortex:{mode,bend:0},time});
    observations.formation.push({mode,time,height:size[0],distance:density[2]});
   }
   const formation=observations.formation.filter(v=>v.mode===mode);
   check(`mode ${mode}: formation begins absent and grows to mature height`,formation[0].distance>=9999&&formation[0].height===0&&formation[1].height>0&&formation[2].height>formation[1].height,formation);
   for(const h of [.25,.55,.78])for(const theta of [0,Math.PI*.5,Math.PI,Math.PI*1.5]){
    const setup=await probe({mode:13,point:[0,h,0],vortex:diagnostic,time:30});
    const origin=[b.centerX+setup[2]+180*Math.cos(theta),b.bottom+height*h,b.centerZ+setup[3]+180*Math.sin(theta)],direction=[-Math.cos(theta),0,-Math.sin(theta)];
    const hit=await probe({mode:6,origin,direction,vortex:diagnostic,time:30}),foreground=await probe({mode:6,origin,direction,limit:10,vortex:diagnostic,time:30});
    observations.rays.push({mode,h,theta,hit,foreground});
   }
   const rays=observations.rays.filter(v=>v.mode===mode);
   check(`mode ${mode}: real liquid surfaces hit at all observed heights and azimuths`,rays.every(v=>v.hit.every(Number.isFinite)&&v.hit[0]>0&&v.hit[0]<180&&Math.abs(v.hit[1]-1)<.005&&Math.abs(v.hit[2])<.12),rays);
   check(`mode ${mode}: opaque foreground truncates liquid surface rays`,rays.every(v=>v.foreground[0]>=9999));
  }
  check('no browser errors',errors.length===0,errors);
  check('source stayed fixed during GPU checks',sha(js)===sha(fs.readFileSync(path.join(lab,'workbench/vortex/preview.js'),'utf8')));
  const report={date:new Date().toISOString(),scope:'Independent WebGL2 GPU measurements of actual liquid distance, phase, material-phase/froth, and velocity; color function and detached-drop intersection replaced by inert returns only to isolate carrier rays and omit full-scene shading compilation; no upstream code execution or Minecraft certification',schema:'vortex-cloud/1',coreSha256:sha(core),previewSha256:sha(js),probeSceneSha256:sha(scene),initialRecipeSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/initial-recipe.json'))),vortexSchemaSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-schema.json'))),noiseGeneratorSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-noise.js'))),noiseDataSha256,probeSetup,checks,observations,passed:checks.every(c=>c.passed)};
  fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({passed:report.passed,checks:checks.length,failed:checks.filter(c=>!c.passed),previewSha256:report.previewSha256}));
  if(!report.passed)process.exitCode=1;
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
