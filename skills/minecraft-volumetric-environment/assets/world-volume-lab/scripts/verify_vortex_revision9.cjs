/* Revision9 independent GPU geometry contracts. No full workbench renderer is
   loaded. Revision8 upper filled-core criteria remain in their original report;
   they do not certify the new deliberately hollow mode2 mouth. */
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const lab=path.resolve(__dirname,'..'),dir=path.join(lab,'evidence/vortex-revision9/verify');
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const checks=[],observations={sections:[],rays:[],normals:[],regression:[],seams:[],replay:[],mouthPixels:[],frameJoin:[],signedSquareFactors:[]};
function check(name,passed,data){checks.push({name,passed:!!passed,...(data===undefined?{}:{data})});}
function isolate(scene,signature,body){const start=scene.indexOf(signature);if(start<0)throw Error('missing probe fixture '+signature);const open=scene.indexOf('{',start);let balance=1,end=open+1;for(;end<scene.length&&balance;end++){if(scene[end]==='{')balance++;else if(scene[end]==='}')balance--;}if(balance)throw Error('unclosed probe function');return scene.slice(0,open+1)+body+scene.slice(end-1);}
function geometryScene(js){let scene=js.match(/const sceneSource = `([\s\S]*?)`;/)[1].split('void main() {')[0];scene=isolate(scene,'vec3 vortexLiquidColor(', 'return vec3(0);');scene=isolate(scene,'SceneHit traceVortexDrops(', 'return SceneHit(10000.0,vec3(0,1,0),vec3(0));');return scene;}
async function verify(){
 fs.mkdirSync(dir,{recursive:true});
 if(fs.existsSync(path.join(dir,'report.json'))){const old=JSON.parse(fs.readFileSync(path.join(dir,'report.json'),'utf8'));const archive=path.join(dir,'history',`${old.previewSha256.slice(0,12)}-${Date.now()}`);fs.mkdirSync(archive,{recursive:true});for(const entry of fs.readdirSync(dir,{withFileTypes:true}))if(entry.isFile())fs.copyFileSync(path.join(dir,entry.name),path.join(archive,entry.name));}
 const js=fs.readFileSync(path.join(lab,'workbench/vortex/preview.js'),'utf8');
 const baseline=fs.readFileSync(path.join(lab,'scripts/fixtures/frozen-vortex-preview-revision8.js'),'utf8');
 if(sha(baseline)!=='9b9d02fd6bbbe169da7d17a62f4a111b9677caa2a75c01617d42c2df38f4ae46')throw Error('revision8 frozen fixture hash mismatch');
 const noiseGeneratorPath=path.join(lab,'workbench/vortex/vortex-noise.js'),noiseGeneratorSha256=sha(fs.readFileSync(noiseGeneratorPath));
 const mouthFixturePath=path.join(lab,'scripts/fixtures/vortex-bowl-camera-revision9.json'),mouthCameraFixtureSha256=sha(fs.readFileSync(mouthFixturePath));
 const scenes=[geometryScene(js),geometryScene(baseline)];
 const required=(pattern,label)=>{const m=scenes[0].match(pattern);if(!m)throw Error('missing production factor '+label);return m[1];};
 const squareExpressions={shoulder:required(/float shoulder=([^;]+);/,'water shoulder'),edge:required(/edge=max\(edge,(.+?)\);/,'sheet edge'),lip:required(/float lipBand=([^;]+);/,'bowl lip')};
 const core=fs.readFileSync(path.join(lab,'workbench/vortex/volume-core.glsl'),'utf8');
 const vortexSchema=JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-schema.json'),'utf8'));
 const contract=JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/contract.json'),'utf8'));
 const work={recipe:JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/initial-recipe.json'),'utf8')),vortex:Object.fromEntries(Object.entries(vortexSchema.fields).map(([k,s])=>[k,s.default]))};
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
 try{
 const page=await browser.newPage({viewport:{width:1200,height:800}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:8774/vortex/vortex-schema.json');
 const setup=await page.evaluate(async({core,scenes,contract,squareExpressions})=>{
  const {makeVortexNoise}=await import('/vortex/vortex-noise.js');const noise=makeVortexNoise();
  const gl=document.createElement('canvas').getContext('webgl2',{antialias:false});
  if(!gl.getExtension('EXT_color_buffer_float'))throw Error('float target unavailable');
  const shader=(type,src)=>{const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
  const probes=`uniform sampler2D ProbePoints;uniform vec3 ProbeDirection,ProbeOrigin,ProbeNormal;uniform float ProbeLimit;uniform int ProbeMode;
   void main(){vec3 point=texelFetch(ProbePoints,ivec2(int(gl_FragCoord.x),0),0).xyz;
    if(ProbeMode==0)fragColor=vec4(vortexLiquidDistance(point),liquidSpiralPhase(point),liquidCrestPhase(point),veDensity(point));
    else if(ProbeMode==1)fragColor=vec4(vortexLiquidNormal(point),vortexLiquidDistance(point));
    else if(ProbeMode==2){SceneHit hit=traceVortexLiquid(ProbeOrigin,normalize(ProbeDirection),ProbeLimit);vec3 p=ProbeOrigin+normalize(ProbeDirection)*hit.distance;fragColor=vec4(hit.distance,length(hit.normal),vortexLiquidDistance(p),1);}
    else if(ProbeMode==3)fragColor=vec4(liquidWorldFromLocal(point.y,point.xz,Time),vortexLiquidHeight());
    else if(ProbeMode==4)fragColor=vec4(vortexLiquidHeight(),vortexLiquidRadius(point.y),0,1);
    else if(ProbeMode==5){LiquidBasis b=liquidBasisAt(point.y,Time);fragColor=vec4(b.tangent,dot(b.u,b.tangent));}
    else if(ProbeMode==6){float shoulderOffset=point.x,edgeOffset=point.y,lipOffset=point.z;fragColor=vec4(${squareExpressions.shoulder},${squareExpressions.edge},${squareExpressions.lip},1);}
    else fragColor=vec4(0);}`;
  const programs=scenes.map(scene=>{const p=gl.createProgram();gl.attachShader(p,shader(gl.VERTEX_SHADER,'#version 300 es\nconst vec2 v[3]=vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));void main(){gl_Position=vec4(v[gl_VertexID],0,1);}'));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,`#version 300 es\nprecision highp float;precision highp int;\n${core}\n${scene}\n${probes}`));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;});
  gl.bindVertexArray(gl.createVertexArray());
  const noiseTex=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_3D,noiseTex);gl.texImage3D(gl.TEXTURE_3D,0,gl.RGBA8,noise.size,noise.size,noise.size,0,gl.RGBA,gl.UNSIGNED_BYTE,noise.data);for(const axis of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T,gl.TEXTURE_WRAP_R])gl.texParameteri(gl.TEXTURE_3D,axis,gl.REPEAT);gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
  const pointTex=gl.createTexture();gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,pointTex);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
  const out=gl.createTexture();gl.activeTexture(gl.TEXTURE2);gl.bindTexture(gl.TEXTURE_2D,out);gl.texStorage2D(gl.TEXTURE_2D,1,gl.RGBA32F,2048,1);
  const fb=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,out,0);if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('incomplete float framebuffer');
  window.revision9Probe=(programIndex,work,options={})=>{
   const base={...work.recipe.parameters,...options.base},v={...work.vortex,...options.vortex},p=programs[programIndex],loc=n=>gl.getUniformLocation(p,n),points=options.points??[[0,0,0]],data=new Float32Array(points.length*4);
   if(points.length>2048)throw Error('probe batch too large');points.forEach((x,i)=>data.set(x,i*4));
   gl.useProgram(p);gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.viewport(0,0,points.length,1);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,pointTex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,points.length,1,0,gl.RGBA,gl.FLOAT,data);gl.uniform1i(loc('ProbePoints'),1);gl.uniform1i(loc('VortexNoiseTex'),0);
   for(const [name,fields] of Object.entries(contract.uniforms)){const values=fields.map(k=>base[k]);if(name==='Bounds')values[3]=Math.max(values[3],v.topRadius+v.bend+20,v.ringRadius+v.ringWidth*3);if(name==='ViewSteps'||name==='LightSteps')gl.uniform1i(loc(name),values[0]);else if(values.length===1)gl.uniform1f(loc(name),values[0]);else if(values.length===3)gl.uniform3fv(loc(name),values);else gl.uniform4fv(loc(name),values);}
   for(const [name,values] of Object.entries({VortexShape:[v.bottomRadius,v.topRadius,v.profile,v.hollow],VortexFlow:[v.rotation,v.rise,v.bend,v.turbulence],VortexRing:[v.ringRadius,v.ringWidth,v.sprayDensity,v.sprayHeight],VortexWater:[v.waterDepth,v.waveAmp,v.waveSpeed,v.foam],VortexLife:[v.duration,v.inflow,v.canopyDensity,v.mode]}))gl.uniform4fv(loc(name),values);
   gl.uniform1f(loc('Time'),options.time??30);gl.uniform1i(loc('ProbeMode'),options.mode??0);gl.uniform3fv(loc('ProbeOrigin'),options.origin??[0,80,200]);gl.uniform3fv(loc('ProbeDirection'),options.direction??[0,0,-1]);gl.uniform1f(loc('ProbeLimit'),options.limit??1000);gl.uniform4fv(loc('WaterDrops[0]'),new Float32Array(96*4));
   gl.drawArrays(gl.TRIANGLES,0,3);const values=new Float32Array(points.length*4);gl.readPixels(0,0,points.length,1,gl.RGBA,gl.FLOAT,values);if(gl.getError()!==gl.NO_ERROR)throw Error('GPU error');return Array.from({length:points.length},(_,i)=>Array.from(values.slice(i*4,i*4+4)));
  };
  const digest=await crypto.subtle.digest('SHA-256',noise.data);return {renderer:gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION),noiseDataSha256:[...new Uint8Array(digest)].map(x=>x.toString(16).padStart(2,'0')).join('')};
 },{core,scenes,contract,squareExpressions});
 console.log(JSON.stringify({stage:'probe-ready',previewSha256:sha(js)}));
 const probe=(options,programIndex=0)=>page.evaluate(({i,w,o})=>window.revision9Probe(i,w,o),{i:programIndex,w:work,o:options});
 const batch=async(options,programIndex=0)=>{const result=[];for(let i=0;i<options.points.length;i+=2048)result.push(...await probe({...options,points:options.points.slice(i,i+2048)},programIndex));return result;};
 const b=work.recipe.parameters,diagnostic={mode:2,bend:0,turbulence:0},metadata=(await probe({mode:4,points:[[0,.5,0]],vortex:diagnostic}))[0],height=metadata[0],angles=32;
 const world=(h,r,theta)=>[b.centerX+r*Math.cos(theta),b.bottom+height*h,b.centerZ+r*Math.sin(theta)];
 const offsets=[-1000,-10,-3,-.5,0,.5,3,10,1000],squareValues=await probe({mode:6,points:offsets.map(x=>[x,x,x]),vortex:{mode:2}});
 observations.signedSquareFactors=offsets.map((offset,i)=>({offset,values:squareValues[i]}));
 check('three extracted production Gaussian factors are finite and sign-symmetric for positive and negative offsets',squareValues.every((v,i)=>v.every(Number.isFinite)&&v.slice(0,3).every((x,j)=>x>=0&&x<=1&&Math.abs(x-squareValues[squareValues.length-1-i][j])<1e-7)),{productionExpressions:squareExpressions,samples:observations.signedSquareFactors});
 const joinEpsilon=1e-5,joinPairs=[];
 for(const radius of [12,30,45])for(const theta of [0,.7,2.1,4.2]){
  const local=[1-joinEpsilon,1+joinEpsilon].map(h=>[radius*Math.cos(theta),height*h,radius*Math.sin(theta)]),embedded=await probe({mode:3,points:local,vortex:{mode:2},time:30}),points=embedded.map(v=>v.slice(0,3)),field=await probe({points,vortex:{mode:2},time:30}),tangent=await probe({mode:5,points:local,vortex:{mode:2},time:30});
  joinPairs.push({radius,theta,local,points,field,tangent,positionDelta:Math.hypot(...points[0].map((x,j)=>x-points[1][j])),tangentDelta:Math.hypot(...tangent[0].slice(0,3).map((x,j)=>x-tangent[1][j])),fieldDelta:Math.abs(field[0][0]-field[1][0])});
 }
 observations.frameJoin=joinPairs;
 check('mode2 mature-height frame join has continuous embedded positions',joinPairs.every(p=>p.points.flat().every(Number.isFinite)&&p.positionDelta<height*joinEpsilon*8),{epsilon:joinEpsilon,maximumDelta:Math.max(...joinPairs.map(p=>p.positionDelta)),allowedDelta:height*joinEpsilon*8});
 check('mode2 mature-height frame join has continuous unit tangents',joinPairs.every(p=>p.tangent.flat().every(Number.isFinite)&&p.tangentDelta<.001&&p.tangent.every(t=>Math.abs(Math.hypot(...t.slice(0,3))-1)<.005)),{epsilon:joinEpsilon,maximumTangentDelta:Math.max(...joinPairs.map(p=>p.tangentDelta))});
 check('mode2 mature-height join keeps the actual bent liquid field continuous',joinPairs.every(p=>p.field.flat().every(Number.isFinite)&&p.fieldDelta<.05),{epsilon:joinEpsilon,maximumFieldDelta:Math.max(...joinPairs.map(p=>p.fieldDelta)),samples:joinPairs.length});
 // The specified hollow transition begins at .57; .54 remains below it.
 for(const h of [.16,.32,.48,.54]){
  const radius=(await probe({mode:4,points:[[0,h,0]],vortex:diagnostic}))[0][1];const points=[];for(let a=0;a<angles;a++)for(const fraction of [0,.18,.25])points.push(world(h,radius*fraction,a*Math.PI*2/angles));
  const values=await batch({points,vortex:diagnostic});check(`mode2 lower section ${h}: original small carrier remains filled at every azimuth`,values.every(v=>Number.isFinite(v[0])&&v[0]<0),{fractions:[0,.18,.25],samples:values.length,minimumInteriorMargin:Math.min(...values.map(v=>-v[0]))});
 }
 // These coordinates are fixed design contracts, not picked after GPU results.
 // The radial scan resolves individual water skins while requiring the upper
 // cavity to stay open at the axis. Full-scene captures certify its visibility.
 for(const h of [.62,.82,.90]){
  const radius=(await probe({mode:4,points:[[0,h,0]],vortex:diagnostic}))[0][1],maxR=work.vortex.topRadius*1.32+12,step=.20,count=Math.ceil(maxR/step)+1,points=[];
  for(let a=0;a<angles;a++)for(let i=0;i<count;i++)points.push(world(h,i*step,a*Math.PI*2/angles));
  const values=await batch({points,vortex:diagnostic}),section={h,height,radius,step,angles:[],centerDistance:values[0][0]};
  for(let a=0;a<angles;a++){const samples=values.slice(a*count,(a+1)*count),crossings=[];for(let i=1;i<count;i++)if(samples[i][0]*samples[i-1][0]<0)crossings.push({radius:(i-1+Math.abs(samples[i-1][0])/(Math.abs(samples[i-1][0])+Math.abs(samples[i][0])))*step,type:samples[i][0]<0?'enter-water':'exit-water'});const inside=samples.map((v,i)=>({d:v[0],r:i*step})).filter(v=>v.d<0);section.angles.push({theta:a*Math.PI*2/angles,crossings,minimumD:Math.min(...samples.map(v=>v[0])),innerRadius:inside.length?inside[0].r:null,outerRadius:inside.length?inside.at(-1).r:null,finite:samples.every(v=>v.every(Number.isFinite))});}
  observations.sections.push(section);
  check(`mode2 section ${h}: radial field finite at all azimuths`,section.angles.every(a=>a.finite));
  if(h>.8){check(`mode2 upper section ${h}: axis cavity is empty`,section.centerDistance>0,{distance:section.centerDistance});check(`mode2 upper section ${h}: every azimuth has a finite annular water wall`,section.angles.every(a=>a.innerRadius!==null&&a.innerRadius>step&&a.outerRadius>a.innerRadius&&a.crossings.some(c=>c.type==='enter-water')&&a.crossings.some(c=>c.type==='exit-water')),section.angles);}
 }
 const lower=observations.sections.find(s=>s.h===.62),upper=observations.sections.find(s=>s.h===.90);
 check('mode2 mouth visibly flares beyond the lower shoulder in every sampled azimuth',upper.angles.every((a,i)=>a.outerRadius>lower.angles[i].outerRadius),{lower:lower.angles.map(a=>a.outerRadius),upper:upper.angles.map(a=>a.outerRadius),minimumOutwardChange:Math.min(...upper.angles.map((a,i)=>a.outerRadius-lower.angles[i].outerRadius))});
 const above=(await probe({points:[world(1.2,0,0)],vortex:diagnostic}))[0];check('mode2 above-mouth center is empty and finite',Number.isFinite(above[0])&&above[0]>0,above);
 // Inner and outer surfaces receive the actual finite-difference normals from
 // production. Pick radial roots, rather than supplying synthetic normals.
 for(const section of observations.sections.filter(s=>s.h>.8)){
  const samples=[];
  for(const a of section.angles.filter((_,i)=>i%4===0))for(const type of ['enter-water','exit-water']){const root=type==='enter-water'?a.crossings.find(c=>c.type===type):a.crossings.filter(c=>c.type===type).at(-1);if(!root)continue;samples.push({theta:a.theta,type,radius:root.radius,point:world(section.h,root.radius,a.theta)});}
  const values=await batch({mode:1,points:samples.map(s=>s.point),vortex:diagnostic});samples.forEach((s,i)=>{s.value=values[i];s.radialDot=values[i][0]*Math.cos(s.theta)+values[i][2]*Math.sin(s.theta);});observations.normals.push({h:section.h,samples});
  check(`mode2 upper section ${section.h}: inner and outer normals face opposite sides`,samples.length===16&&samples.every(s=>s.value.every(Number.isFinite)&&Math.abs(Math.hypot(...s.value.slice(0,3))-1)<.005&&(s.type==='enter-water'?s.radialDot<0:s.radialDot>0)),samples);
 }
 for(const h of [.48,.82,.90])for(const theta of [0,Math.PI*.5,Math.PI,Math.PI*1.5]){
  const origin=world(h,180,theta),direction=[-Math.cos(theta),0,-Math.sin(theta)],hit=(await probe({mode:2,origin,direction,vortex:diagnostic}))[0],foreground=(await probe({mode:2,origin,direction,limit:10,vortex:diagnostic}))[0];observations.rays.push({h,theta,hit,foreground});
 }
 check('mode2 side rays hit lower body and upper shell at every observed azimuth',observations.rays.every(r=>r.hit.every(Number.isFinite)&&r.hit[0]>0&&r.hit[0]<180&&Math.abs(r.hit[1]-1)<.005&&Math.abs(r.hit[2])<.12),observations.rays);
 check('mode2 opaque foreground truncates all sampled liquid rays',observations.rays.every(r=>r.foreground[0]>=9999));
 // Fixed rays selected from the original bowl9a mouth screenshot's three dark
 // bands and adjacent visible water. A dense field scan independently asks
 // whether a wall actually exists along each ray. This detects budget misses
 // that normal horizontal section rays cannot reveal.
 {
  const fixture=JSON.parse(fs.readFileSync(mouthFixturePath,'utf8')),frame=fixture.frames.find(f=>f.mode===2&&f.view==='mouth'),c=frame.state.camera,[width,imageHeight]=frame.state.resolution;
  const normalize=v=>{const m=Math.hypot(...v);return v.map(x=>x/m);},cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],forward=[Math.sin(c.yaw)*Math.cos(c.pitch),Math.sin(c.pitch),Math.cos(c.yaw)*Math.cos(c.pitch)],right=normalize(cross(forward,[0,1,0])),up=normalize(cross(right,forward));
  const pixels=[{x:480,y:198,label:'water above far-rim dark line'},{x:480,y:202,label:'far-rim dark line'},{x:480,y:206,label:'water below far-rim dark line'},{x:480,y:252,label:'water above middle dark band'},{x:480,y:258,label:'middle dark band'},{x:480,y:266,label:'water below middle dark band'},{x:480,y:298,label:'water above lower dark band'},{x:480,y:304,label:'lower dark band'},{x:480,y:310,label:'dark shading within lower band'},{x:480,y:316,label:'water below lower dark band'}];
  for(const pixel of pixels){
   const ux=((pixel.x+.5)/width*2-1)*width/imageHeight,uy=1-(pixel.y+.5)/imageHeight*2,direction=normalize(forward.map((f,j)=>f+.52*(right[j]*ux+up[j]*uy))),origin=c.position;
   const hit=(await probe({mode:2,origin,direction,vortex:{mode:2},time:frame.time}))[0],step=.10,count=5001,points=Array.from({length:count},(_,i)=>origin.map((x,j)=>x+direction[j]*i*step)),values=await batch({points,vortex:{mode:2},time:frame.time});
   let firstInterior=null;const intervals=[];let current=null;
   for(let i=0;i<count;i++){if(values[i][0]<0){if(firstInterior===null)firstInterior=i*step;if(current===null)current={start:i*step,minimumD:values[i][0]};else current.minimumD=Math.min(current.minimumD,values[i][0]);}else if(current){current.end=(i-1)*step;intervals.push(current);current=null;}}
   if(current){current.end=500;intervals.push(current);}
   observations.mouthPixels.push({...pixel,fixturePreviewSha256:fixture.previewSha256,time:frame.time,origin,direction,hit,denseScan:{step,maximumDistance:500,firstInterior,intervals,minimumD:Math.min(...values.map(v=>v[0]))},budgetMiss:intervals.some(x=>x.minimumD<-.10)&&hit[0]>=9999});
  }
  check('mode2 mouth image rays do not miss water present in independent dense field samples',observations.mouthPixels.every(p=>!p.budgetMiss),observations.mouthPixels);
  check('mode2 mouth hits satisfy the production liquid classification distance threshold',observations.mouthPixels.filter(p=>p.hit[0]<9999).every(p=>Number.isFinite(p.hit[2])&&Math.abs(p.hit[2])<.15),{productionThreshold:.15,hits:observations.mouthPixels.filter(p=>p.hit[0]<9999).map(p=>({pixel:[p.x,p.y],label:p.label,distance:p.hit[0],surfaceDistance:p.hit[2]}))});
 }
 // Fixed bent-frame sample points include the upper mouth so atan seams and
 // replay apply to the newly added geometry, not only an unchanged lower part.
 for(const h of [.48,.82,.90]){
  const radius=(await probe({mode:4,points:[[0,h,0]],vortex:diagnostic}))[0][1],localPoints=[Math.PI-1e-5,-Math.PI+1e-5].map(t=>[radius*.9*Math.cos(t),height*h,radius*.9*Math.sin(t)]),embedded=await probe({mode:3,points:localPoints,vortex:{mode:2},time:30}),points=embedded.map(v=>v.slice(0,3)),values=await probe({points,vortex:{mode:2},time:30});
  const wrap=x=>Math.atan2(Math.sin(x),Math.cos(x));observations.seams.push({h,points,values});check(`mode2 section ${h}: angular seam stays continuous in distance and spiral`,Math.abs(values[0][0]-values[1][0])<.02&&Math.abs(wrap(values[0][1]-values[1][1]))<.002&&Math.abs(wrap(values[0][2]-values[1][2]))<.002,{points,values});
 }
 for(const mode of [0,1]){
  const points=[];for(const h of [.16,.48,.80,1.05])for(let a=0;a<8;a++)for(const r of [0,3,9,20,42,70])points.push([b.centerX+r*Math.cos(a*Math.PI/4),b.bottom+height*h,b.centerZ+r*Math.sin(a*Math.PI/4)]);
  const current=await batch({points,vortex:{mode},time:30}),frozen=await batch({points,vortex:{mode},time:30},1),maxDifference=Math.max(...current.flatMap((v,i)=>v.map((x,j)=>Math.abs(x-frozen[i][j])))),perChannelMaximumDifference=Object.fromEntries(['distance','spiralPhase','crestPhase','cloudDensity'].map((name,j)=>[name,Math.max(...current.map((v,i)=>Math.abs(v[j]-frozen[i][j])))]));observations.regression.push({mode,samples:points.length,maxDifference,perChannelMaximumDifference,comparison:'float-exact GPU distance, phase, crest phase and cloud density against frozen revision8'});check(`mode${mode}: frozen revision8 distance, phase and density samples remain float-exact`,maxDifference===0,{samples:points.length,maxDifference,perChannelMaximumDifference});
 }
 const replayPoints=[];for(const h of [.48,.82,.90])for(let a=0;a<8;a++)replayPoints.push(world(h,work.vortex.topRadius*.6,a*Math.PI/4));
 const replayA=await probe({points:replayPoints,vortex:{mode:2},time:30});await probe({points:replayPoints,vortex:{mode:2},time:37});const replayB=await probe({points:replayPoints,vortex:{mode:2},time:30}),difference=Math.max(...replayA.flatMap((v,i)=>v.map((x,j)=>Math.abs(x-replayB[i][j]))));observations.replay.push({times:[30,37,30],samples:replayPoints.length,maxDifference:difference});check('mode2 fixed-time geometry and density replay is float-exact after a different time',difference===0,observations.replay);
 check('no browser errors',errors.length===0,errors);check('source, noise and fixtures stayed fixed throughout GPU checks',sha(js)===sha(fs.readFileSync(path.join(lab,'workbench/vortex/preview.js'),'utf8'))&&noiseGeneratorSha256===sha(fs.readFileSync(noiseGeneratorPath))&&mouthCameraFixtureSha256===sha(fs.readFileSync(mouthFixturePath))&&sha(baseline)===sha(fs.readFileSync(path.join(lab,'scripts/fixtures/frozen-vortex-preview-revision8.js'))),{previewSha256:sha(js),noiseGeneratorSha256,mouthCameraFixtureSha256,baselinePreviewSha256:sha(baseline)});
 const report={date:new Date().toISOString(),iteration:9,scope:'Independent WebGL2 GPU geometry/normal/ray/density probes of the actual production liquid field, plus finite sign-symmetric probes of three extracted production Gaussian factors. Color and detached-drop hits are inert only to isolate carrier surfaces. Revision8 remains the frozen regression source for mode0/1. Center-filled upper mode2 criteria from revision8 are historical and do not apply to its new deliberately hollow bowl. These tests do not certify material naturalness, full multi-interface refraction, actual screenshot repeatability or Minecraft integration.',schema:'vortex-cloud/1',previewSha256:sha(js),baselinePreviewSha256:sha(baseline),coreSha256:sha(core),probeSceneSha256:scenes.map(sha),squareExpressions,initialRecipeSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/initial-recipe.json'))),vortexSchemaSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-schema.json'))),noiseGeneratorSha256,noiseDataSha256:setup.noiseDataSha256,mouthCameraFixtureSha256,setup,checks,observations,passed:checks.every(c=>c.passed)};
 fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({passed:report.passed,checks:checks.length,failed:checks.filter(c=>!c.passed),previewSha256:report.previewSha256}));if(!report.passed)process.exitCode=1;
 }finally{await browser.close();}
}
verify().catch(e=>{console.error(e);process.exitCode=1;});
