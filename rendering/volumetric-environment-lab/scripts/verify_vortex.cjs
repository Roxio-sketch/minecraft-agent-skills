/* Validate observable WebGL behavior of the independent vortex workbench. */
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const lab=path.resolve(__dirname,'..'),dir=path.join(lab,'evidence/vortex');
fs.mkdirSync(dir,{recursive:true});
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const checks=[];
const retiredObservations=[];
function check(name,passed,data){checks.push({name,passed:!!passed,...(data===undefined?{}:{data})});}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  page.on('console',message=>{if(message.text().startsWith('[vortex-probe]'))console.log(message.text());});
  await page.goto('http://127.0.0.1:8774/vortex/');
  check('initialization',await page.evaluate(()=>window.volumeLab.ready));
  await page.evaluate(()=>window.volumeLab.pause(true));
  const work=await page.evaluate(()=>window.volumeLab.getWork());
  const core=fs.readFileSync(path.join(lab,'workbench/vortex/volume-core.glsl'),'utf8');
  const js=fs.readFileSync(path.join(lab,'workbench/vortex/preview.js'),'utf8');
  const scene=js.match(/const sceneSource = `([\s\S]*?)`;/)[1].split('void main() {')[0];
  const contract=JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/contract.json'),'utf8'));
  const probeSetup=await page.evaluate(({core,scene,contract})=>{
   const canvas=document.createElement('canvas');canvas.width=canvas.height=1;
   const gl=canvas.getContext('webgl2',{antialias:false});
   if(!gl.getExtension('EXT_color_buffer_float'))throw Error('float attachment unavailable');
   function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
   // Keep every reachable production helper intact. Separating probe entry
   // points prevents ANGLE from repeatedly inlining density, sea, full-colored
   // carrier/drop rays and optical-path loops into one enormous branch shader.
   const vertex=shader(gl.VERTEX_SHADER,'#version 300 es\nconst vec2 v[3]=vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));void main(){gl_Position=vec4(v[gl_VertexID],0,1);}');
   const declarations='uniform vec3 ProbePoint,ProbeOrigin,ProbeDirection;uniform float ProbeLimit;uniform int ProbeMode;';
   const groups=[
    {name:'cloud-density-light',modes:[0,1,4,5],body:`
     if(ProbeMode==0)fragColor=vec4(veDensity(ProbePoint),veSupport(ProbePoint),vortexProgress(),length(vortexAxis(clamp((ProbePoint.y-Layer.x)/(Layer.y*.84),0.,1.))));
     else if(ProbeMode==1)fragColor=veIntegrate(ProbeOrigin,normalize(ProbeDirection),ProbeLimit);
     else if(ProbeMode==4)fragColor=vec4(veLightTransmission(ProbePoint,normalize(ProbeDirection),ProbeLimit),0,0,1);
     else fragColor=textureLod(VortexNoiseTex,ProbePoint,0.0);`},
    {name:'sea-surface-flow',modes:[2,3,10,12],body:`
     if(ProbeMode==2){float e=.18;vec2 x=ProbePoint.xz;fragColor=vec4(vortexWaterHeight(x),(vortexWaterHeight(x+vec2(e,0))-vortexWaterHeight(x-vec2(e,0)))/(2.*e),(vortexWaterHeight(x+vec2(0,e))-vortexWaterHeight(x-vec2(0,e)))/(2.*e),1);}
     else if(ProbeMode==3){SceneHit h=traceWater(ProbeOrigin,normalize(ProbeDirection));vec3 x=ProbeOrigin+normalize(ProbeDirection)*h.distance;fragColor=vec4(h.distance,h.normal.y,x.y-vortexWaterHeight(x.xz),1);}
     else if(ProbeMode==10){vec2 q=ProbePoint.xz-Bounds.xz,v=vortexWaterSurfaceVelocity(q);fragColor=waterSurfaceFlowData(vec3(q.x,0,q.y),vec3(v.x,0,v.y),.095);}
     else {vec2 v=vortexWaterSurfaceVelocity(ProbePoint.xz-Bounds.xz);fragColor=vec4(v,0,1);}`},
    {name:'colored-liquid-carrier-ray',modes:[6],body:`
     SceneHit h=traceVortexLiquid(ProbeOrigin,normalize(ProbeDirection),ProbeLimit);vec3 x=ProbeOrigin+normalize(ProbeDirection)*h.distance;fragColor=vec4(h.distance,length(h.normal),vortexLiquidDistance(x),dot(h.color,vec3(.2126,.7152,.0722)));`},
    {name:'liquid-field-optics-colored-drops',modes:[7,8,9,11],body:`
     if(ProbeMode==7)fragColor=vec4(vortexLiquidHeight(),vortexLiquidRadius(.2),vortexLiquidDistance(ProbePoint),1);
     else if(ProbeMode==8){SceneHit h=traceVortexDrops(ProbeOrigin,normalize(ProbeDirection),ProbeLimit);fragColor=vec4(h.distance,length(h.normal),dot(h.color,vec3(.2126,.7152,.0722)),1);}
     else if(ProbeMode==9)fragColor=vec4(liquidOpticalThickness(ProbePoint,normalize(ProbeDirection)),vortexLiquidDistance(ProbePoint),liquidFroth(ProbePoint,vec3(0,1,0)),1);
     else fragColor=liquidSurfaceFlowData(ProbePoint,.055);`}
   ];
   const compileStarted=performance.now();
   const programs=groups.map(group=>{
    const started=performance.now(),program=gl.createProgram();
    const fragment=shader(gl.FRAGMENT_SHADER,`#version 300 es\nprecision highp float;precision highp int;\n${core}\n${scene}\n${declarations}\nvoid main(){${group.body}}`);
    gl.attachShader(program,vertex);gl.attachShader(program,fragment);gl.linkProgram(program);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
    const compileLinkMs=performance.now()-started;
    console.log('[vortex-probe] '+JSON.stringify({name:group.name,modes:group.modes,compileLinkMs}));
    return {...group,program,locations:new Map(),compileLinkMs};
   });
   const compileLinkTotalMs=performance.now()-compileStarted;
   const locate=(entry,name)=>{if(!entry.locations.has(name))entry.locations.set(name,gl.getUniformLocation(entry.program,name));return entry.locations.get(name);};
   gl.bindVertexArray(gl.createVertexArray());
   const noise=window.volumeLab.getNoiseData(),noiseTex=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_3D,noiseTex);gl.texImage3D(gl.TEXTURE_3D,0,gl.RGBA8,noise.size,noise.size,noise.size,0,gl.RGBA,gl.UNSIGNED_BYTE,noise.data);for(const axis of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T,gl.TEXTURE_WRAP_R])gl.texParameteri(gl.TEXTURE_3D,axis,gl.REPEAT);gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
   const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texStorage2D(gl.TEXTURE_2D,1,gl.RGBA32F,1,1);
   const fb=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,tex,0);
   if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('incomplete framebuffer');
   const drops=new Float32Array(window.volumeLab.getLiquidDrops());
   for(const entry of programs){gl.useProgram(entry.program);gl.uniform1i(locate(entry,'VortexNoiseTex'),0);gl.uniform4fv(locate(entry,'WaterDrops[0]'),drops);}
   window.vortexProbe=(work,options={})=>{
    const base={...work.recipe.parameters,...options.base},v={...work.vortex,...options.vortex};
    const mode=options.mode??0,entry=programs.find(group=>group.modes.includes(mode));if(!entry)throw Error('Unknown GPU probe mode '+mode);
    const loc=name=>locate(entry,name);gl.useProgram(entry.program);gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.viewport(0,0,1,1);
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
   return {renderer:gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION),programs:programs.map(({name,modes,compileLinkMs})=>({name,modes,compileLinkMs})),compileLinkTotalMs,initialDropsCount:drops.length/4,initialDropsTime:window.volumeLab.getWork().preview.time,scope:'Four production-helper programs; all color and detached-drop paths intact; original initial-frame drop fixture and all probe outputs retained'};
  },{core,scene,contract});
  const probe=options=>page.evaluate(({w,o})=>window.vortexProbe(w,o),{w:work,o:options});
  const noiseDataSha256=await page.evaluate(async()=>{const bytes=window.volumeLab.getNoiseData().data;const digest=await crypto.subtle.digest('SHA-256',bytes);return [...new Uint8Array(digest)].map(x=>x.toString(16).padStart(2,'0')).join('');});
  const edge0=await probe({mode:5,point:[0,.4,.7]}),edge1=await probe({mode:5,point:[1,.4,.7]});check('3D cloud noise seamless repeat',JSON.stringify(edge0)===JSON.stringify(edge1),{edge0,edge1});
  check('3D noise channels have variation',await page.evaluate(()=>{const {size,data}=window.volumeLab.getNoiseData();return size===64&&data.length===64**3*4&&[0,1,2,3].every(channel=>{let low=255,high=0;for(let i=channel;i<data.length;i+=64){low=Math.min(low,data[i]);high=Math.max(high,data[i]);}return high-low>80;});}));
  const nearShadow=await page.evaluate(({current,legacy})=>{
   const canvas=document.createElement('canvas');canvas.width=canvas.height=1;const g=canvas.getContext('webgl2');g.getExtension('EXT_color_buffer_float');
   const texture=g.createTexture();g.bindTexture(g.TEXTURE_2D,texture);g.texStorage2D(g.TEXTURE_2D,1,g.RGBA32F,1,1);const fb=g.createFramebuffer();g.bindFramebuffer(g.FRAMEBUFFER,fb);g.framebufferTexture2D(g.FRAMEBUFFER,g.COLOR_ATTACHMENT0,g.TEXTURE_2D,texture,0);g.bindVertexArray(g.createVertexArray());
   const compile=(type,source)=>{const s=g.createShader(type);g.shaderSource(s,source);g.compileShader(s);if(!g.getShaderParameter(s,g.COMPILE_STATUS))throw Error(g.getShaderInfoLog(s));return s;};
   const read=source=>{const from=source.indexOf('float veDensity(vec3 world)'),to=source.indexOf('bool veSphereInterval',from);const synthetic=source.slice(0,from)+'float veDensity(vec3 world){return world.x>=0.0&&world.x<8.0?Density.x:0.0;}\n'+source.slice(to);const p=g.createProgram();g.attachShader(p,compile(g.VERTEX_SHADER,'#version 300 es\nvoid main(){vec2 v=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(v*2.-1.,0,1);}'));g.attachShader(p,compile(g.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;precision highp int;\n'+synthetic+'\nout vec4 outputColor;void main(){outputColor=vec4(veLightTransmission(vec3(0,10,0),vec3(1,0,0),128.),0,0,1);}'));g.linkProgram(p);if(!g.getProgramParameter(p,g.LINK_STATUS))throw Error(g.getProgramInfoLog(p));g.useProgram(p);const loc=n=>g.getUniformLocation(p,n);g.uniform4f(loc('Bounds'),64,0,0,64);g.uniform4f(loc('Layer'),0,190,0,0);g.uniform4f(loc('Density'),3,1,.075,.5);g.uniform4f(loc('Optics'),.06,.45,.65,2.5);g.uniform1i(loc('LightSteps'),source===legacy?4:7);g.viewport(0,0,1,1);g.drawArrays(g.TRIANGLES,0,3);const x=new Float32Array(4);g.readPixels(0,0,1,1,g.RGBA,g.FLOAT,x);if(g.getError()!==g.NO_ERROR)throw Error('near shadow probe GL error');return x[0];};
   return {legacy:read(legacy),current:read(current),analytic:Math.exp(-3*.06*8),scope:'Synthetic near-density slab replaces density only; real old/new light integrators on GPU'};
  },{current:core,legacy:fs.readFileSync(path.join(lab,'scripts/fixtures/frozen-vortex-core-iteration1.glsl'),'utf8')});
  check('near-light sampling captures narrow dense medium',nearShadow.legacy===1&&Math.abs(nearShadow.current-nearShadow.analytic)<.12&&Math.abs(nearShadow.current-nearShadow.analytic)<Math.abs(nearShadow.legacy-nearShadow.analytic),nearShadow);
  let values=await probe({time:0});check('formation starts with zero density',values[0]===0,values);
  values=await probe({time:0,mode:1});check('formation starts with identity composition',JSON.stringify(values)==='[0,0,0,1]',values);
  for(const point of [[300,80,0],[0,-3,0],[0,300,0]]){values=await probe({point});check('finite domain '+point,values[0]===0,values);}
  values=await probe({mode:1,base:{density:0}});check('zero density identity',JSON.stringify(values)==='[0,0,0,1]',values);
  values=await probe({mode:1,limit:1});check('foreground truncation excludes medium',JSON.stringify(values)==='[0,0,0,1]',values);
  const low=await probe({mode:1,base:{density:1}}),high=await probe({mode:1,base:{density:4}});
  check('density reduces transmittance',high[3]<low[3]&&low[3]<1,{low,high});
  const a=await probe({base:{density:1}}),b=await probe({base:{density:3}});check('density scales raw medium',a[0]>0&&Math.abs(b[0]-3*a[0])<2e-6,{a,b});
  check('fixed time float exact replay',JSON.stringify(b)===JSON.stringify(await probe({base:{density:3}})));
  const flat={waterDepth:0,waveAmp:0};values=await probe({mode:2,point:[0,0,0],vortex:flat});check('flat water exact height and normal',values[0]===work.recipe.parameters.bottom&&values[1]===0&&values[2]===0,values);
  values=await probe({mode:3,origin:[0,20,0],direction:[0,-1,0],vortex:flat});check('flat water ray intersection',Math.abs(values[0]-20)<.003&&values[1]===1&&Math.abs(values[2])<.003,values);
  const center=await probe({mode:2,point:[0,0,0],vortex:{waveAmp:0}});check('water center depression',Math.abs(center[0]+work.vortex.waterDepth)<1e-5,center);
  const near=await Promise.all([[.001,0,0],[0,0,.001],[-.001,0,0],[0,0,-.001]].map(point=>probe({mode:2,point})));const origin=await probe({mode:2,point:[0,0,0]});
  check('water center continuous in four directions',near.every(v=>Math.abs(v[0]-origin[0])<.001),{origin,near});
  const waterHit=await probe({mode:3,origin:[24,20,0],direction:[0,-1,0]});check('waved water intersection lies on heightfield',waterHit[0]<100&&waterHit[1]>0&&Math.abs(waterHit[2])<.03,waterHit);
  const liquid=await probe({mode:6,origin:[0,12,100],direction:[0,0,-1]});
  check('visible liquid column has actual finite surface hit',liquid.every(Number.isFinite)&&liquid[0]>0&&liquid[0]<200&&Math.abs(liquid[1]-1)<.003&&Math.abs(liquid[2])<.12&&liquid[3]>0,liquid);
  values=await probe({mode:6,time:0,origin:[0,12,100],direction:[0,0,-1]});check('liquid column absent before formation',values[0]>=9999,values);
  values=await probe({mode:6,vortex:{mode:0},origin:[0,12,100],direction:[0,0,-1]});check('land tornado has no liquid column',values[0]>=9999,values);
  values=await probe({mode:6,origin:[200,12,100],direction:[0,0,-1]});check('liquid bounded away from vortex',values[0]>=9999,values);
  values=await probe({mode:6,origin:[0,12,100],direction:[0,0,-1],limit:10});check('liquid respects nearer scene depth',values[0]>=9999,values);
  const h1=await probe({mode:7}),h2=await probe({mode:7,vortex:{mode:2}});check('water tornado has a taller broader liquid carrier',h2[0]>h1[0]&&h2[1]>h1[1],{h1,h2});
  const filledSamples1=await Promise.all([.12,.28,.48,.68,.82].map(h=>probe({mode:7,point:[1,h1[0]*h,0],vortex:{mode:1,bend:0,turbulence:2}})));
  check('mode 1 keeps a filled liquid core across height under strong turbulence',filledSamples1.every(v=>v.every(Number.isFinite)&&v[2]<-.25),filledSamples1);
  const lowerSamples2=await Promise.all([.12,.28,.48].map(h=>probe({mode:7,point:[1,h2[0]*h,0],vortex:{mode:2,bend:0,turbulence:2}})));
  check('mode 2 keeps its lower liquid core occupied under strong turbulence',lowerSamples2.every(v=>v.every(Number.isFinite)&&v[2]<-.25),lowerSamples2);
  const upperSamples2=await Promise.all([.82,.90].map(h=>probe({mode:7,point:[1,h2[0]*h,0],vortex:{mode:2,bend:0,turbulence:2}})));
  check('mode 2 upper bowl has an open central cavity',upperSamples2.every(v=>v.every(Number.isFinite)&&v[2]>.5),upperSamples2);
  const upperShellHits2=await Promise.all([.82,.90].flatMap(h=>[0,Math.PI/2,Math.PI,3*Math.PI/2].map(a=>probe({mode:6,vortex:{mode:2,bend:0,turbulence:2},origin:[Math.cos(a)*180,h2[0]*h,Math.sin(a)*180],direction:[-Math.cos(a),0,-Math.sin(a)]}))));
  check('mode 2 open upper cavity remains surrounded by actual liquid surfaces',upperShellHits2.every(v=>v.every(Number.isFinite)&&v[0]>0&&v[0]<180&&Math.abs(v[1]-1)<.01&&Math.abs(v[2])<.15),upperShellHits2);
  const historicalSamples2=await Promise.all([.12,.28,.48,.68,.82].map(h=>probe({mode:7,point:[1,h2[0]*h,0],vortex:{mode:2,bend:0,turbulence:2}})));
  retiredObservations.push({name:'mode 2 keeps a filled liquid core across height under strong turbulence',reason:'revision9 intentionally opens the upper bowl cavity; lower occupancy and upper cavity/surface checks replace this old all-height filled-core criterion',wouldPass:historicalSamples2.every(v=>v.every(Number.isFinite)&&v[2]<-.25),data:historicalSamples2});
  const waterTornadoHits=await Promise.all([0,1,2,3].map(i=>{const a=i*Math.PI/2;return probe({mode:6,vortex:{mode:2},origin:[Math.cos(a)*180,100,Math.sin(a)*180],direction:[-Math.cos(a),0,-Math.sin(a)]});}));
  check('water tornado sheets have actual surfaces from multiple sides',waterTornadoHits.every(v=>v.every(Number.isFinite))&&waterTornadoHits.filter(v=>v[0]<9999&&Math.abs(v[1]-1)<.01&&Math.abs(v[2])<.15).length>=2,waterTornadoHits);
  const dropData=await page.evaluate(()=>window.volumeLab.getLiquidDrops());
  const dropIndex=Array.from({length:96},(_,i)=>i*4).find(i=>dropData[i+3]>.02);
  const d=dropData.slice(dropIndex,dropIndex+4);values=await probe({mode:8,origin:[d[0],d[1],d[2]+12],direction:[0,0,-1]});check('detached water drop has real surface and normal',values[0]<12&&values[0]>0&&Math.abs(values[1]-1)<.01&&values[2]>0,values);
  values=await probe({mode:9,point:[0,12,0],direction:[0,0,1]});check('water optical path samples actual finite carrier',values.every(Number.isFinite)&&values[0]>0&&values[0]<200&&values[2]>=0&&values[2]<=1,values);
  values=await probe({mode:9,point:[200,12,0],direction:[0,0,1]});check('water optical path is zero outside carrier',values[0]===0,values);
  values=await probe({mode:9,time:0,point:[0,12,0],direction:[0,0,1]});check('water optical path is zero before formation',values[0]===0,values);
  for(const [mode,name] of [[10,'sea surface'],[11,'water column surface']]){
   const a=await probe({mode,point:[24,35,12],time:20}),b=await probe({mode,point:[24,35,12],time:20.15});
   check(name+' flow moves with shared time',a.every(Number.isFinite)&&b.every(Number.isFinite)&&a.some((x,i)=>Math.abs(x-b[i])>1e-5),{a,b});
  }
  values=await probe({mode:12,point:[24,0,0],vortex:{rotation:0,waveSpeed:0}});check('sea surface flow draws inward when wind and rotation are disabled',values[0]<0&&Math.abs(values[1])<1e-5,values);
  const stoppedFlow={rotation:0,rise:0,inflow:0,bend:0};
  const stoppedA=await probe({mode:11,point:[24,35,12],time:20,vortex:stoppedFlow}),stoppedB=await probe({mode:11,point:[24,35,12],time:21,vortex:stoppedFlow});
  check('water column flow freezes with zero velocity',JSON.stringify(stoppedA)===JSON.stringify(stoppedB),{stoppedA,stoppedB});
  const shell={bend:0,canopyDensity:0,sprayDensity:0};values=await probe({point:[0,80,0],vortex:{...shell,hollow:.5}});check('hollow axis density zero',values[0]===0,values);
  values=await probe({point:[0,80,0],vortex:{...shell,hollow:0}});check('filled axis finite positive density',values.every(Number.isFinite)&&values[0]>0,values);
  const finite=await Promise.all([0,1].flatMap(mode=>[0,5,20,80].flatMap(time=>[[0,0,0],[5,40,3],[14,80,5],[30,130,10],[0,159.6,0],[70,10,5]].map(point=>probe({time,point,vortex:{mode}})))));
  check('48 density samples finite nonnegative',finite.every(v=>v.every(Number.isFinite)&&v[0]>=0));
  const scan=(options)=>Promise.all([[8,40,8],[14,80,5],[30,130,10],[0,10,24],[30,159,10]].map(point=>probe({...options,point})));
  const baseline=await scan({});
  for(const [key,value] of Object.entries({rotation:0,rise:0,bend:0,inflow:0,turbulence:0,sprayDensity:0,canopyDensity:0})){
   const changed=await scan({vortex:{[key]:value}});check('GPU parameter affects density: '+key,changed.some((v,i)=>Math.abs(v[0]-baseline[i][0])>1e-5));
  }
  for(const [key,value] of Object.entries({waterDepth:0,waveAmp:0,waveSpeed:0})){
   const changed=await probe({mode:2,point:[23,0,12],vortex:{[key]:value}}),base=await probe({mode:2,point:[23,0,12]});check('GPU parameter affects water: '+key,Math.abs(changed[0]-base[0])>1e-5);
  }
  for(const mode of [0,1,2]){
   await page.evaluate(m=>window.volumeLab.setMode(m),mode);await page.evaluate(()=>window.volumeLab.render(20));
   const first=await page.evaluate(()=>window.volumeLab.capture());await page.evaluate(()=>window.volumeLab.render(20));const second=await page.evaluate(()=>window.volumeLab.capture());
   check('mode '+mode+' PNG exact replay',first===second);
   fs.writeFileSync(path.join(dir,`mode-${mode}-time-20.png`),Buffer.from(first.split(',')[1],'base64'));
  }
  await page.evaluate(async()=>{await window.volumeLab.render(6);window.volumeLab.pause(false);});
  await page.locator('#timeInput').fill('20');
  await page.locator('#setTimeButton').focus();
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  await page.locator('#setTimeButton').click();
  const committedTime=await page.evaluate(()=>window.volumeLab.getState());
  check('real time input survives focus transfer while playing',committedTime.time===20&&committedTime.paused,{time:committedTime.time,paused:committedTime.paused});
  for(const mode of [1,2]){
   await page.evaluate(m=>window.volumeLab.setMode(m),mode);await page.evaluate(()=>window.volumeLab.setView('liquid'));await page.evaluate(()=>window.volumeLab.render(20));
   const first=await page.evaluate(()=>window.volumeLab.capture());await page.evaluate(()=>window.volumeLab.render(20));
   check('mode '+mode+' liquid close view PNG exact replay',first===await page.evaluate(()=>window.volumeLab.capture()));
   fs.writeFileSync(path.join(dir,`mode-${mode}-liquid.png`),Buffer.from(first.split(',')[1],'base64'));
  }
  await page.evaluate(()=>window.volumeLab.setView('outside'));
  await page.evaluate(()=>window.volumeLab.setMode(2));await page.evaluate(()=>window.volumeLab.render(20));
  const outsideBeforeNear=await page.evaluate(()=>window.volumeLab.capture());
  await page.evaluate(()=>window.volumeLab.setView('liquid'));await page.evaluate(()=>window.volumeLab.render(20));
  await page.evaluate(()=>window.volumeLab.setView('outside'));await page.evaluate(()=>window.volumeLab.render(20));
  check('mode 2 outside replay after near camera switch',outsideBeforeNear===await page.evaluate(()=>window.volumeLab.capture()));
  await page.evaluate(()=>window.volumeLab.setMode(1));await page.evaluate(()=>window.volumeLab.render(20));
  const drops20=await page.evaluate(()=>window.volumeLab.getLiquidDrops());check('detached drops deterministic finite replay',drops20.length===384&&drops20.every(Number.isFinite)&&JSON.stringify(drops20)===JSON.stringify(await page.evaluate(()=>window.volumeLab.getLiquidDrops())));
  await page.evaluate(()=>window.volumeLab.render(20.1));check('detached drops move with shared time',JSON.stringify(drops20)!==JSON.stringify(await page.evaluate(()=>window.volumeLab.getLiquidDrops())));await page.evaluate(()=>window.volumeLab.render(20));
  await page.locator('#densityNumber').fill('4.5');await page.locator('#densityNumber').dispatchEvent('input');
  check('density front and side controls synchronize',await page.evaluate(()=>document.getElementById('densityQuick').value==='4.5'&&document.getElementById('field-density').value==='4.5'&&window.volumeLab.getRecipe().parameters.density===4.5));
  const before=await page.evaluate(()=>window.volumeLab.capture());await page.locator('#densityNumber').fill('0');await page.locator('#densityNumber').dispatchEvent('input');check('zero density changes rendered image',before!==await page.evaluate(()=>window.volumeLab.capture()));
  await page.locator('#densityNumber').fill('3');await page.locator('#densityNumber').dispatchEvent('input');
  const saved=await page.evaluate(()=>window.volumeLab.getWork());
  const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#jsonButton').click()]);const jsonPath=path.join(dir,'download.json');await download.saveAs(jsonPath);
  const exported=JSON.parse(fs.readFileSync(jsonPath,'utf8'));check('real JSON download contains complete effect',JSON.stringify(exported)===JSON.stringify(saved)&&Object.keys(exported.vortex).length===20&&Object.keys(exported.recipe.parameters).length===62);
  await page.evaluate(()=>window.volumeLab.setMode(0));await page.evaluate(()=>window.volumeLab.render(5));await page.locator('#importFile').setInputFiles(jsonPath);await page.waitForFunction(()=>window.volumeLab.getWork().vortex.mode===1&&window.volumeLab.getWork().preview.time===20);
  check('real import restores all parameters camera and time',JSON.stringify(await page.evaluate(()=>window.volumeLab.getWork()))===JSON.stringify(saved));
  for(const [name,mutate] of Object.entries({missing:x=>delete x.vortex.rise,extra:x=>x.vortex.unrecognized=1,wrongMode:x=>x.vortex.mode=.5,invertedRadii:x=>{x.vortex.topRadius=10;x.vortex.bottomRadius=20;},invalidBase:x=>x.recipe.parameters.densityModel=1,invalidCamera:x=>x.preview.camera.pitch=2,extraPreview:x=>x.preview.extra=1,missingPreview:x=>delete x.preview})){
   const bad=JSON.parse(JSON.stringify(saved));mutate(bad);await page.locator('#importFile').setInputFiles({name:'invalid.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(bad))});await page.waitForFunction(()=>!document.getElementById('recipeError').hidden);
   check('reject '+name+' without mutating effect',JSON.stringify(await page.evaluate(()=>window.volumeLab.getWork()))===JSON.stringify(saved));await page.locator('#importFile').setInputFiles(jsonPath);await page.waitForFunction(()=>document.getElementById('recipeError').hidden);
  }
  const beforeDownload=await page.evaluate(()=>window.volumeLab.capture());
  const [png]=await Promise.all([page.waitForEvent('download'),page.locator('#pngButton').click()]);await png.saveAs(path.join(dir,'download.png'));check('real PNG download',fs.statSync(path.join(dir,'download.png')).size>1000);
  check('real PNG download exactly matches fixed frame',Buffer.from(beforeDownload.split(',')[1],'base64').equals(fs.readFileSync(path.join(dir,'download.png'))));
  check('game resource export unavailable',await page.locator('#zipButton').isDisabled());
  check('base workbench self test',(await page.evaluate(()=>window.volumeLab.selfTest())).passed);
  await page.screenshot({path:path.join(dir,'page.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>window.volumeLab.render(20));
  check('390px layout has no horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));await page.screenshot({path:path.join(dir,'mobile.png'),fullPage:true});
  check('no browser errors',errors.length===0,errors);
  const report={date:new Date().toISOString(),iteration:9,scope:'WebGL2 workbench; no Minecraft/Forge certification',schema:'vortex-cloud/1',coreSha256:sha(core),previewSha256:sha(js),noiseGeneratorSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-noise.js'))),noiseDataSha256,probeSetup,checks,retiredObservations,passed:checks.every(c=>c.passed),screenshots:Object.fromEntries(['mode-0-time-20.png','mode-1-time-20.png','mode-2-time-20.png','mode-1-liquid.png','mode-2-liquid.png','mobile.png'].map(f=>[f,sha(fs.readFileSync(path.join(dir,f)))]))};
  fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({passed:report.passed,checks:checks.length,failed:checks.filter(c=>!c.passed),coreSha256:report.coreSha256}));if(!report.passed)process.exitCode=1;
  if(process.env.VORTEX_REVIEW_CURRENT==='1')await require('./preview_vortex_revision9.cjs').reviewPage(page,true);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
