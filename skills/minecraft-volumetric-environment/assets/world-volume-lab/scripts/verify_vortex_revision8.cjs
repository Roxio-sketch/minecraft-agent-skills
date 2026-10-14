/* Independent GPU contracts for revision8. Single-cos silhouette metrics are
   retained as observations, not recast as passing acceptance requirements. */
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const lab=path.resolve(__dirname,'..'),dir=path.join(lab,'evidence/vortex-revision8/verify');
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
  const fullScene=js.match(/const sceneSource = `([\s\S]*?)`;/)[1];
  const mainSource=fullScene.slice(fullScene.lastIndexOf('void main() {'));
  let scene=fullScene.split('void main() {')[0];
  const requireExpression=(source,pattern,label)=>{const match=source.match(pattern);if(!match)throw Error('missing production expression: '+label);return match[1];};
  const productionExpressions={
   reflectionNormal:requireExpression(scene,/vec3 direction=reflect\(rd,([^;]+)\);/,'reflection normal'),
   transmissionNormal:requireExpression(mainSource,/vec3 orientedNormal=([^;]+);/,'transmission normal').replace(/\bray\b/g,'rd'),
   absorption:requireExpression(mainSource,/vec3 absorption=([^;]+);/,'absorption'),
   transmission:requireExpression(mainSource,/vec3 transmission=([^;]+);/,'transmission'),
   scatter:requireExpression(mainSource,/\+vec3\([^;]+?\)\*(\(vec3\(1\.0\)-absorption\)[^;]+);/,'scatter weight')
  };
  const reflectionEntry=scene.match(/vec3 vortexLiquidColor\([^]*?LiquidOptics optical=([^;]+);/)[1];
  const transmissionEntry=mainSource.match(/LiquidOptics optical=([^;]+);/)[1];
  check('production reflection and transmission share the liquidOpticsAt interface',reflectionEntry.startsWith('liquidOpticsAt(')&&transmissionEntry.startsWith('liquidOpticsAt('),{reflectionEntry,transmissionEntry,productionExpressions});
  const isolate=(signature,body)=>{const start=scene.indexOf(signature);if(start<0)throw Error('missing probe fixture function '+signature);const open=scene.indexOf('{',start);let balance=1,end=open+1;for(;end<scene.length&&balance;end++){if(scene[end]==='{')balance++;else if(scene[end]==='}')balance--;}if(balance)throw Error('unclosed probe fixture function');scene=scene.slice(0,open+1)+body+scene.slice(end-1);};
  // Geometry probes omit color and detached-drop intersections. The actual
  // optical helper stays intact; production normal/weight expressions below
  // are extracted rather than maintaining a second independent implementation.
  isolate('vec3 vortexLiquidColor(', 'return vec3(0);');
  isolate('SceneHit traceVortexDrops(', 'return SceneHit(10000.0,vec3(0,1,0),vec3(0));');
  const contract=JSON.parse(fs.readFileSync(path.join(lab,'workbench/vortex/contract.json'),'utf8'));
  const probeSetup=await page.evaluate(({core,scene,contract,productionExpressions})=>{
   const canvas=document.createElement('canvas');canvas.width=canvas.height=1;
   const gl=canvas.getContext('webgl2',{antialias:false});
   if(!gl.getExtension('EXT_color_buffer_float'))throw Error('float attachment unavailable');
   function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
   const p=gl.createProgram();
   gl.attachShader(p,shader(gl.VERTEX_SHADER,'#version 300 es\nconst vec2 v[3]=vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));void main(){gl_Position=vec4(v[gl_VertexID],0,1);}'));
   gl.attachShader(p,shader(gl.FRAGMENT_SHADER,`#version 300 es\nprecision highp float;precision highp int;\n${core}\n${scene}\nuniform vec3 ProbePoint,ProbeOrigin,ProbeDirection,ProbeNormal;uniform float ProbeLimit,ProbeThickness;uniform int ProbeMode;
   void main(){
    if(ProbeMode==6){SceneHit h=traceVortexLiquid(ProbeOrigin,normalize(ProbeDirection),ProbeLimit);vec3 x=ProbeOrigin+normalize(ProbeDirection)*h.distance;fragColor=vec4(h.distance,length(h.normal),vortexLiquidDistance(x),1);}
    else if(ProbeMode==7)fragColor=vec4(vortexLiquidHeight(),vortexLiquidRadius(.2),vortexLiquidDistance(ProbePoint),1);
    else if(ProbeMode==13){float h=clamp(ProbePoint.y,0.,1.);vec2 a=vortexLiquidAxis(h);fragColor=vec4(vortexLiquidHeight(),vortexLiquidRadius(h),a);}
    else if(ProbeMode==14){vec4 f=liquidSurfaceFlowData(ProbePoint,.055),n=liquidSurfaceFlowData(ProbePoint,.14);fragColor=vec4(liquidSpiralPhase(ProbePoint),liquidSpiralMaterialPhase(ProbePoint,f,n),vortexLiquidDistance(ProbePoint),liquidFroth(ProbePoint,vec3(0,1,0)));}
    else if(ProbeMode==15){vec3 v=liquidSurfaceVelocity(ProbePoint);fragColor=vec4(v,liquidSpiralPhase(ProbePoint));}
    else if(ProbeMode==17){LiquidPoint p=liquidLocalPoint(ProbePoint);fragColor=vec4(p.uv,p.s,p.h);}
    else if(ProbeMode==18)fragColor=vec4(liquidWorldFromLocal(ProbePoint.y,ProbePoint.xz,Time),1);
    else if(ProbeMode==19){LiquidOptics optical=liquidOpticsAt(ProbePoint,normalize(ProbeNormal),normalize(ProbeDirection));fragColor=vec4(optical.normal,optical.fresnel);}
    else if(ProbeMode==20){
     vec3 rd=normalize(ProbeDirection);LiquidOptics optical=liquidOpticsAt(ProbePoint,normalize(ProbeNormal),rd);
     float fresnel=optical.fresnel,foam=optical.foam,thickness=ProbeThickness;
     vec3 absorption=${productionExpressions.absorption};
     vec3 transmission=${productionExpressions.transmission};
     vec3 scatterWeight=${productionExpressions.scatter};
     vec3 total=vec3(fresnel*(1.0-foam)+foam)+transmission+scatterWeight;
     fragColor=vec4(total,foam);
    }
    else if(ProbeMode==21){
     vec3 rd=normalize(ProbeDirection);LiquidOptics optical=liquidOpticsAt(ProbePoint,normalize(ProbeNormal),rd);
     vec3 reflectedNormal=${productionExpressions.reflectionNormal};
     vec3 transmittedNormal=${productionExpressions.transmissionNormal};
     vec3 aligned=dot(reflectedNormal,transmittedNormal)<0.0?-transmittedNormal:transmittedNormal;
     fragColor=vec4(length(reflectedNormal-aligned),length(reflectedNormal),length(transmittedNormal),abs(dot(reflectedNormal,transmittedNormal)));
    }
    else if(ProbeMode==22){
     LiquidOptics optical=liquidOpticsAt(ProbePoint,normalize(ProbeNormal),normalize(ProbeDirection));
     float fresnel=optical.fresnel,foam=optical.foam,thickness=ProbeThickness;
     vec3 absorption=${productionExpressions.absorption};vec3 transmission=${productionExpressions.transmission};
     fragColor=vec4(min(min(transmission.x,transmission.y),transmission.z),max(max(transmission.x,transmission.y),transmission.z),optical.fresnel,optical.foam);
    }
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
    gl.uniform3fv(loc('ProbeNormal'),options.normal??[1,0,0]);gl.uniform1f(loc('ProbeThickness'),options.thickness??0);
    gl.drawArrays(gl.TRIANGLES,0,3);const output=new Float32Array(4);gl.readPixels(0,0,1,1,gl.RGBA,gl.FLOAT,output);
    if(gl.getError()!==gl.NO_ERROR)throw Error('GPU probe error');return Array.from(output);
   };
   return {renderer:gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION)};
  },{core,scene,contract,productionExpressions});
  console.log(JSON.stringify({stage:'probe-ready',previewSha256:sha(js)}));
  const probe=options=>page.evaluate(({w,o})=>window.vortexProbe(w,o),{w:work,o:options});
  const noiseDataSha256=await page.evaluate(async()=>{const bytes=window.volumeLab.getNoiseData().data;const digest=await crypto.subtle.digest('SHA-256',bytes);return [...new Uint8Array(digest)].map(x=>x.toString(16).padStart(2,'0')).join('');});
  const wrap=x=>Math.atan2(Math.sin(x),Math.cos(x));
  const b=work.recipe.parameters;
  const observations={sections:[],spiral:[],transport:[],formation:[],rays:[],optics:[],legacySingleCrestCriteria:[]};
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
     // Revision8 keeps a smaller carrier below detached/overlapping skins.
     // The old .25/.50 samples remain raw measurements, not new promises.
     for(const fraction of [.18,.25,.50]){
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
    const coreSamples=section.bulk.filter(v=>v.fraction===.18||v.fraction===.25);
    check(`mode ${mode}, section ${h}: small central carrier covers every azimuth`,coreSamples.every(v=>Number.isFinite(v.distance)&&v.distance<0),{minimumInteriorMargin:Math.min(...coreSamples.map(v=>-v.distance)),samples:coreSamples.length,contractRadiusFractions:[.18,.25]});
    check(`mode ${mode}, section ${h}: all azimuths have a finite connected surface`,section.boundary.every(v=>Number.isFinite(v.radius)&&v.radius>radius*.2&&Math.abs(v.distance)<.035),{minimumRadius:Math.min(...section.boundary.map(v=>v.radius)),maximumRadius:Math.max(...section.boundary.map(v=>v.radius)),minimumSupportedFraction:.2});
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
   const turns=spiral.slice(1).map((s,i)=>wrap(s.peakAngle-spiral[i].peakAngle));
   observations.legacySingleCrestCriteria.push({mode,
    status:'Retired design criteria, observations only; no contribution to revision8 passed',
    broadHelicalSilhouetteWouldPassV7:spiral.every(s=>s.amplitude>.025)&&spiral.filter(s=>s.correlation>.55).length>=4,
    consistentlyTurningCrestWouldPassV7:turns.every(d=>d>.10&&d<Math.PI),
    observedPeakAngleSteps:turns,
    oldHalfEnvelopeInteriorWouldPassV7:sections.every(s=>s.bulk.filter(v=>v.fraction===.25||v.fraction===.50).every(v=>v.distance<0)),
    oldHalfEnvelopeSurfaceWouldPassV7:sections.every(s=>s.boundary.every(v=>v.radius>s.radius*.5&&Math.abs(v.distance)<.035))
   });
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
   // Optics are measured from the real liquidOpticsAt helper. The reflected
   // and oriented transmitted normals below use expressions extracted from
   // their production call sites; this is separate from full-scene radiance.
   const opticalSetup=await probe({mode:13,point:[0,.48,0],vortex:diagnostic,time:30});
   const opticalPoint=[b.centerX+opticalSetup[1]*.70,b.bottom+height*.48,b.centerZ];
   const initialOptical=await probe({mode:19,point:opticalPoint,direction:[-1,0,0],normal:[1,0,0],vortex:diagnostic,time:30});
   const normal=initialOptical.slice(0,3),cross=[-normal[2],0,normal[0]],crossLength=Math.hypot(...cross);
   const tangent=cross.map(x=>x/crossLength);
   const negativeNormal=normal.map(x=>-x),middle=normal.map((x,j)=>-.5*x+Math.sqrt(.75)*tangent[j]);
   const opticalDirections=[{name:'normal',direction:negativeNormal},{name:'middle',direction:middle},{name:'grazing',direction:tangent}];
   const opticalAngles=[];
   for(const direction of opticalDirections){
    const common={point:opticalPoint,direction:direction.direction,normal:[1,0,0],vortex:diagnostic,time:30};
    const optical=await probe({...common,mode:19}),shared=await probe({...common,mode:21});
    opticalAngles.push({name:direction.name,direction:direction.direction,optical,shared});
   }
   observations.optics.push({mode,opticalPoint,angles:opticalAngles});
   check(`mode ${mode}: reflected and transmitted normals are the same interface`,opticalAngles.every(v=>v.shared.every(Number.isFinite)&&v.shared[0]<1e-5&&Math.abs(v.shared[1]-1)<1e-5&&Math.abs(v.shared[2]-1)<1e-5&&Math.abs(v.shared[3]-1)<1e-5),opticalAngles);
   const [normalF,middleF,grazingF]=opticalAngles.map(v=>v.optical[3]);
   check(`mode ${mode}: actual Fresnel rises from water normal incidence to grazing`,Math.abs(normalF-.0204)<1e-5&&normalF<middleF&&middleF<grazingF&&grazingF>.999&&grazingF<=1.00001,{normalF,middleF,grazingF});
   const extrema=[];
   for(const foamSetting of [vortexSchema.fields.foam.min,vortexSchema.fields.foam.max]){
    for(const h of [.02,.25,.55,.80]){
     const setup=await probe({mode:13,point:[0,h,0],vortex:diagnostic,time:30});
     for(const theta of [0,.83,2.1,3.7]){
      const point=[b.centerX+setup[1]*.80*Math.cos(theta),b.bottom+height*h,b.centerZ+setup[1]*.80*Math.sin(theta)];
      const value=await probe({mode:22,point,normal:[Math.cos(theta),.25,Math.sin(theta)],direction:[-1,-.15,.2],thickness:3,vortex:{...diagnostic,foam:foamSetting},time:30});
      extrema.push({foamSetting,h,theta,value});
     }
    }
   }
   observations.optics.push({mode,foamExtrema:extrema});
   check(`mode ${mode}: foam settings remain finite and bounded`,extrema.every(v=>v.value.every(Number.isFinite)&&v.value[0]>=-1e-6&&v.value[1]<=1.00001&&v.value[2]>=0&&v.value[2]<=1.00001&&v.value[3]>=0&&v.value[3]<=1),extrema);
   check(`mode ${mode}: zero foam setting clears the foam layer`,extrema.filter(v=>v.foamSetting===0).every(v=>Math.abs(v.value[3])<1e-7));
   const partition=[];
   for(const foamSetting of [0,1])for(const item of opticalDirections){
    const sequence=[];
    for(const thickness of [0,.1,3,35,2500]){
     const common={point:opticalPoint,normal:[1,0,0],direction:item.direction,thickness,vortex:{...diagnostic,foam:foamSetting},time:30};
     const total=await probe({...common,mode:20}),transmission=await probe({...common,mode:22});
     sequence.push({thickness,total,transmission});
    }
    partition.push({foamSetting,angle:item.name,sequence});
   }
   observations.optics.push({mode,partition});
   check(`mode ${mode}: interface reflection transmission scatter and foam weights conserve unit share`,partition.every(p=>p.sequence.every(s=>s.total.every(Number.isFinite)&&s.total.slice(0,3).every(x=>Math.abs(x-1)<2e-5)&&s.total[3]>=0&&s.total[3]<=1)),partition);
   check(`mode ${mode}: actual transmission is bounded and falls with optical path`,partition.every(p=>p.sequence.every((s,i)=>s.transmission.every(Number.isFinite)&&s.transmission[0]>=-1e-6&&s.transmission[1]<=1.00001&&(i===0||s.transmission[1]<=p.sequence[i-1].transmission[1]+1e-6))),partition);
   const seamNormal=[-1,0,0],leftOptics=await probe({mode:19,point:seam(Math.PI-1e-5),normal:seamNormal,direction:[0,-.1,1],vortex:diagnostic,time:30});
   const rightOptics=await probe({mode:19,point:seam(-Math.PI+1e-5),normal:seamNormal,direction:[0,-.1,1],vortex:diagnostic,time:30});
   check(`mode ${mode}: fine optical normal stays continuous at angular branch`,leftOptics.every((v,j)=>Number.isFinite(v)&&Math.abs(v-rightOptics[j])<.01),{leftOptics,rightOptics});
  }
  check('no browser errors',errors.length===0,errors);
  check('source stayed fixed during GPU checks',sha(js)===sha(fs.readFileSync(path.join(lab,'workbench/vortex/preview.js'),'utf8')));
  const report={date:new Date().toISOString(),iteration:8,scope:'Independent WebGL2 GPU measurements of actual carrier distance, phase, froth, velocity, and shared optical helper. Production reflection/transmission normal and absorption/transmission/scatter expressions are extracted for isolated interface probes. Color function and detached-drop intersection are inert only to isolate carrier rays and omit full-scene compilation. Weight tests certify an interface partition, not total radiance, real multi-interface refraction, natural appearance, upstream solver execution, or Minecraft. Retired revision7 single-cos/Fourier metrics stay raw in observations and are excluded from passed.',schema:'vortex-cloud/1',coreSha256:sha(core),previewSha256:sha(js),probeSceneSha256:sha(scene),productionExpressions,initialRecipeSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/initial-recipe.json'))),vortexSchemaSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-schema.json'))),noiseGeneratorSha256:sha(fs.readFileSync(path.join(lab,'workbench/vortex/vortex-noise.js'))),noiseDataSha256,probeSetup,checks,observations,passed:checks.every(c=>c.passed)};
  fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({passed:report.passed,checks:checks.length,failed:checks.filter(c=>!c.passed),previewSha256:report.previewSha256}));
  if(!report.passed)process.exitCode=1;
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
