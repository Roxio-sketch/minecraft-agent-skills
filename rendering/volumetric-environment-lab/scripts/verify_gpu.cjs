/* Independent analytic contracts and actual RGBA32F rendering of shared GLSL. */
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..'),out=path.join(root,'evidence','gpu'),extendedOut=path.join(root,'evidence','end-distributions-gpu');
fs.mkdirSync(out,{recursive:true});
fs.mkdirSync(extendedOut,{recursive:true});
const core=fs.readFileSync(path.join(root,'workbench','volume-core.glsl'),'utf8');
const schema=JSON.parse(fs.readFileSync(path.join(root,'workbench','schema.json'),'utf8'));
const contract=JSON.parse(fs.readFileSync(path.join(root,'workbench','contract.json'),'utf8'));
const frozenCorePath=path.join(root,'scripts','fixtures','frozen-volume-core-0.1.0.glsl');
const frozenCore=fs.readFileSync(frozenCorePath,'utf8');
const frozenSource=JSON.parse(fs.readFileSync(path.join(root,'scripts','fixtures','frozen-source.json'),'utf8'));
const sha256=b=>crypto.createHash('sha256').update(b).digest('hex');
if(sha256(fs.readFileSync(frozenCorePath))!==frozenSource.entrySha256)throw Error('Frozen 0.1.0 core hash mismatch');
(async()=>{
  const browser=await chromium.launch({headless:true,channel:process.argv[2]||'msedge',args:['--use-angle=d3d11']});
  try {
    const page=await browser.newPage();
    await page.goto(process.env.WORLD_VOLUME_URL||'http://127.0.0.1:8774/');
    const report=await page.evaluate(({core,schema,contract,frozenCore})=>{
      const canvas=document.createElement('canvas');canvas.width=1;canvas.height=1;
      const gl=canvas.getContext('webgl2',{antialias:false});
      if(!gl||!gl.getExtension('EXT_color_buffer_float'))throw Error('RGBA32F WebGL2 support required');
      function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
      const vs=shader(gl.VERTEX_SHADER,'#version 300 es\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}');
      function makeProgram(source,newModelHelpers){
      const fragment=shader(gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;precision highp int;\n'+source+`
uniform vec3 TestOrigin;uniform vec3 TestDirection;uniform vec3 TestNormal;uniform float TestLimit;uniform int TestMode;out vec4 Color;
void main(){vec2 span;if(TestMode==0)Color=veIntegrate(TestOrigin,normalize(TestDirection),TestLimit);
else if(TestMode==1)Color=vec4(veDensity(TestOrigin),veSupport(TestOrigin),0,1);
else if(TestMode==2)Color=veFlashState();
else if(TestMode==3)Color=vec4(veSurfaceLight(TestOrigin,normalize(TestNormal)),1);
else if(TestMode==5)Color=vec4(veLightTransmission(TestOrigin,normalize(TestDirection),TestLimit),0,0,1);
${newModelHelpers?'else if(TestMode==6)Color=vec4(vePurpleBrightness(TestOrigin),veDensity(TestOrigin),0,1);':''}
else {bool hit=veInterval(TestOrigin,normalize(TestDirection),TestLimit,span);Color=vec4(span,hit?1.:0.,1);}}
`);
      const program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fragment);gl.linkProgram(program);
      if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
      const locations={};return {program,fragment,loc(name){if(!Object.hasOwn(locations,name))locations[name]=gl.getUniformLocation(program,name);return locations[name];}};
      }
      const current=makeProgram(core,true),frozenProgram=makeProgram(frozenCore,false);
      const vao=gl.createVertexArray();gl.bindVertexArray(vao);
      const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,1,1,0,gl.RGBA,gl.FLOAT,null);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
      const fbo=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);
      if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('Incomplete float framebuffer');
      const defaults=Object.fromEntries(Object.entries(schema.fields).map(([k,v])=>[k,v.default]));
      const checks=[],witnesses={};let group='legacy-contracts';
      function check(name,passed,actual,expected){checks.push({group,name,passed:Boolean(passed),actual,expected});}
      function draw(overrides={},time=0,mode=0,origin=[0,60,200],direction=[0,0,-1],limit=1000,normal=[0,1,0],entry=current){
        gl.useProgram(entry.program);const loc=entry.loc;
        const p={...defaults,...overrides};
        for(const [name,keys] of Object.entries(contract.uniforms)){
          const values=keys.map(k=>p[k]),l=loc(name);if(l===null)continue;
          if(name==='ViewSteps'||name==='LightSteps')gl.uniform1i(l,values[0]);
          else if(values.length===1)gl.uniform1f(l,values[0]);
          else if(values.length===3)gl.uniform3fv(l,values);
          else gl.uniform4fv(l,values);
        }
        gl.uniform1f(loc('Time'),time);gl.uniform1i(loc('TestMode'),mode);gl.uniform1f(loc('TestLimit'),limit);
        gl.uniform3fv(loc('TestOrigin'),origin);gl.uniform3fv(loc('TestDirection'),direction);gl.uniform3fv(loc('TestNormal'),normal);
        gl.viewport(0,0,1,1);gl.drawArrays(gl.TRIANGLES,0,3);const data=new Float32Array(4);gl.readPixels(0,0,1,1,gl.RGBA,gl.FLOAT,data);
        const a=Array.from(data);if(!a.every(Number.isFinite))throw Error('Non-finite output '+JSON.stringify({overrides,time,mode,a}));
        return a;
      }
      const eq=(a,b)=>a.every((v,i)=>Object.is(v,b[i])),near=(a,b)=>a.every((v,i)=>Math.abs(v-b[i])<1e-4);
      const sphere={shape:1,radius:65,centerY:75,flashEnabled:0};
      const interval=draw(sphere,0,4,[0,75,200]);check('sphere analytic entry / exit',near(interval,[135,265,1,1]),interval,[135,265,1,1]);
      const clipped=draw(sphere,0,4,[0,75,200],[0,0,-1],170);check('opaque depth clips interval',near(clipped,[135,170,1,1]),clipped,[135,170,1,1]);
      const identity=[0,0,0,1];
      const front=draw(sphere,0,0,[0,75,200],[0,0,-1],100);check('near opaque wall excludes all volume',eq(front,identity),front,identity);
      const miss=draw(sphere,0,0,[200,75,200],[0,0,-1]);check('finite sphere ray miss identity',eq(miss,identity),miss,identity);
      const zero=draw({density:0,emission:8,flashStrength:30});check('zero density exact identity',eq(zero,identity),zero,identity);
      const coverage=draw({coverage:0});check('zero coverage exact identity',eq(coverage,identity),coverage,identity);
      const slab=draw({bottom:35,height:100,radius:180},0,4,[0,60,200]);check('horizontal finite slab interval',near(slab,[20,380,1,1]),slab,[20,380,1,1]);
      const vertical=draw({bottom:35,height:100,radius:180},0,4,[0,0,0],[0,1,0]);check('vertical slab interval',near(vertical,[35,135,1,1]),vertical,[35,135,1,1]);
      const hole=draw({shape:2,innerRadius:42},0,1,[0,60,0]);check('annulus center exact vacuum',hole[0]===0&&hole[1]===0,hole);
      const outside=draw(sphere,0,1,[66,75,0]);check('sphere outside density zero',outside[0]===0&&outside[1]===0,outside);
      const baseline=draw({...sphere,coverage:1,density:0.5});
      check('medium attenuates',baseline[3]>=0&&baseline[3]<1,baseline);
      const glow=draw({...sphere,coverage:1,density:0.5,emission:3,emissionThreshold:0});
      check('emission raises radiance only',glow.slice(0,3).some((v,i)=>v>baseline[i])&&glow[3]===baseline[3],glow,baseline);
      const vacuumEmission=draw({...sphere,coverage:1,density:0.5,extinction:0,emission:3,emissionThreshold:0});
      check('zero extinction emissive transfer finite',vacuumEmission[3]===1&&vacuumEmission.slice(0,3).some(v=>v>0),vacuumEmission);
      const frozen={...sphere,speed:0,emissionSpeed:0,emission:1};
      check('zero speed freezes wind and emission',eq(draw(frozen,1),draw(frozen,9)));
      const moveA=draw({...sphere,speed:5},1),moveB=draw({...sphere,speed:5},9);
      check('world wind changes density integral',!eq(moveA,moveB),moveA,moveB);
      const rebased={...sphere,centerX:10000,centerY:10075,centerZ:-10000};
      const translated=draw(rebased,0,0,[10000,10075,-9800]);
      check('common world translation preserves anchored density',near(translated,draw(sphere,0,0,[0,75,200])),translated);
      const flashRecipe={shape:0,bottom:8,height:105,coverage:1,density:0.3,flashPeriod:4,flashDuration:0.8,flashStrength:10,flashRadius:180,flashPlacement:0.2,speed:0,emissionSpeed:0,ambient:0.1,sunGain:0};
      let peak={time:0,state:[0,0,0,0]};
      for(let i=0;i<=160;i++){const time=i/40,state=draw(flashRecipe,time,2);if(state[3]>peak.state[3])peak={time,state};}
      check('intermittent event has finite positive peak',peak.state[3]>0&&peak.state[3]<=10,peak);
      check('same seed/time exact event replay',eq(peak.state,draw(flashRecipe,peak.time,2)));
      check('negative time clamps to zero',eq(draw(flashRecipe,-1,2),draw(flashRecipe,0,2)));
      const ringNear=draw({...flashRecipe,shape:2,innerRadius:40,flashPlacement:0},peak.time,2);
      const ringFar=draw({...flashRecipe,shape:2,innerRadius:40,flashPlacement:0.9},peak.time,2);
      check('ring event placement control changes radial position',!eq(ringNear,ringFar)&&ringNear[3]===ringFar[3],ringNear,ringFar);
      const disabled=draw({...flashRecipe,flashEnabled:0},peak.time,2);check('event disabled exact zero intensity',disabled[3]===0,disabled);
      const dark=draw({...flashRecipe,mediumGain:0,surfaceGain:0},peak.time);
      const lit=draw({...flashRecipe,mediumGain:2,surfaceGain:0},peak.time);
      check('local flash raises cloud radiance without opacity change',lit.slice(0,3).some((v,i)=>v>dark[i])&&lit[3]===dark[3],lit,dark);
      check('surface gain does not alter medium integral',eq(lit,draw({...flashRecipe,mediumGain:2,surfaceGain:8},peak.time)));
      const point=[peak.state[0],0,peak.state[2]],surface=draw({...flashRecipe,surfaceGain:1},peak.time,3,point);
      check('local flash lights facing surface',surface.slice(0,3).some(v=>v>0),surface);
      check('medium gain does not alter surface light',eq(surface,draw({...flashRecipe,surfaceGain:1,mediumGain:0},peak.time,3,point)));
      const surfaceOff=draw({...flashRecipe,surfaceGain:0},peak.time,3,point);check('surface switch exact zero',eq(surfaceOff,[0,0,0,1]),surfaceOff);
      const away=draw({...flashRecipe,surfaceGain:1},peak.time,3,point,[0,0,-1],1000,[0,-1,0]);check('surface Lambert facing-away zero',eq(away,[0,0,0,1]),away);
      const far=draw({...flashRecipe,surfaceGain:1},peak.time,3,[10000,0,10000]);check('local light finite support',eq(far,[0,0,0,1]),far);
      for(const shape of [0,1,2])for(const viewSteps of [8,96]){
        const value=draw({shape,viewSteps,lightSteps:12,density:5,coverage:1,extinction:0.5,emission:8,flashStrength:30,mediumGain:8,curl:4},peak.time);
        check(`extreme finite shape ${shape} steps ${viewSteps}`,value.every(Number.isFinite)&&value[3]>=0&&value[3]<=1,value);
      }
      check('GL error zero',gl.getError()===gl.NO_ERROR);
      witnesses.peak=peak;witnesses.baseline=baseline;witnesses.glow=glow;
      group='end-distributions';
      const endFixture={densityModel:1,shape:1,radius:300,centerY:75,density:1,coverage:0.9,erosion:0,
        cavityRadius:0,torusWidth:0,voidDensity:0,nearClearRadius:0,twistAmount:0,twistSpeed:0,
        speed:0,emission:0,flashEnabled:0};
      const columns=[[30,20],[70,-40],[-60,55],[-100,-30],[110,15]],heights=[-80,-60,-40,-20,0,20,40,60,80];
      function verticalProfile(stretch){return columns.map(([x,z])=>heights.map(y=>draw({...endFixture,verticalStretch:stretch},0,1,[x,75+y,z])[0]));}
      function variation(profiles){return profiles.reduce((total,profile)=>total+profile.slice(1).reduce((sum,v,i)=>sum+Math.abs(v-profile[i]),0),0);}
      const isotropic=verticalProfile(1),stretched=verticalProfile(48),isotropicVariation=variation(isotropic),stretchedVariation=variation(stretched);
      check('End vertical stretch preserves coherent columns',stretchedVariation<isotropicVariation*0.2&&stretched.flat().some(v=>v>0),
        {isotropicVariation,stretchedVariation,isotropic,stretched},{maximumRatio:0.2,columns,heights});
      const twistPoints=[[60,145,20],[-65,25,45],[90,115,-40],[30,10,-85]];
      const straight=twistPoints.map(p=>draw({...endFixture,verticalStretch:48},0,1,p));
      const twisted=twistPoints.map(p=>draw({...endFixture,verticalStretch:48,twistAmount:0.023},0,1,p));
      check('End height-dependent twist changes raw density',straight.some((a,i)=>!eq(a,twisted[i])),{straight,twisted,points:twistPoints});
      const rotating={...endFixture,verticalStretch:48,twistAmount:0.023,twistSpeed:0.07};
      const rotationA=twistPoints.map(p=>draw(rotating,1,1,p)),rotationB=twistPoints.map(p=>draw(rotating,9,1,p));
      check('End twist speed remains active with zero advection speed',rotationA.some((a,i)=>!eq(a,rotationB[i])),{rotationA,rotationB,recipe:rotating});
      const frozenEnd={...endFixture,verticalStretch:48,twistAmount:0.023};
      const frozenDensityA=twistPoints.map(p=>draw(frozenEnd,1,1,p)),frozenDensityB=twistPoints.map(p=>draw(frozenEnd,9,1,p));
      const frozenIntegralA=draw(frozenEnd,1,0,[0,75,200]),frozenIntegralB=draw(frozenEnd,9,0,[0,75,200]);
      check('End zero twist and advection speeds freeze density and transfer',frozenDensityA.every((a,i)=>eq(a,frozenDensityB[i]))&&eq(frozenIntegralA,frozenIntegralB),
        {frozenDensityA,frozenDensityB,frozenIntegralA,frozenIntegralB});
      const centerUncarved=draw(endFixture,0,1,[0,75,0]),centerCarved=draw({...endFixture,cavityRadius:60},0,1,[0,75,0]);
      check('End center ellipsoid subtracts density without altering finite support',centerUncarved[0]>0&&centerCarved[0]===0&&centerCarved[1]===centerUncarved[1],{centerUncarved,centerCarved});
      const tallCavityPoint=[0,145,0],cavityRound=draw({...endFixture,cavityRadius:60,cavityAspect:1},0,1,tallCavityPoint),
        cavityTall=draw({...endFixture,cavityRadius:60,cavityAspect:0.3},0,1,tallCavityPoint);
      check('End ellipsoid aspect changes vertical carving',cavityRound[0]>cavityTall[0]&&cavityTall[1]===cavityRound[1],{point:tallCavityPoint,cavityRound,cavityTall});
      const torusRecipe={...endFixture,torusRadius:70,torusWidth:25,cavityAspect:1},torusPoint=[70,75,0],aboveTorusPoint=[70,125,0];
      const torusRaw=draw(endFixture,0,1,torusPoint),torusCut=draw(torusRecipe,0,1,torusPoint),
        torusAxis=draw(torusRecipe,0,1,[0,75,0]),torusAbove=draw(torusRecipe,0,1,aboveTorusPoint),aboveUncut=draw(endFixture,0,1,aboveTorusPoint);
      check('End torus groove carves a finite ring cross-section',torusRaw[0]>torusCut[0]&&torusCut[1]===torusRaw[1]&&torusAxis[0]>0&&eq(torusAbove,aboveUncut),
        {torusRaw,torusCut,torusAxis,torusAbove,aboveUncut,torusPoint,aboveTorusPoint});
      const annulusRecipe={...endFixture,densityModel:2,shape:2,innerRadius:60,bottom:-100,height:350};
      const annulusAxis=draw(annulusRecipe,0,1,[0,75,0]),annulusBand=draw(annulusRecipe,0,1,torusPoint);
      check('Torus groove differs from the ordinary annular support hole',annulusAxis[0]===0&&annulusAxis[1]===0&&annulusBand[0]>0&&torusAxis[0]>0,
        {annulusAxis,annulusBand,torusAxis,torusCut});
      const voidRecipe={...endFixture,cavityRadius:1024,cavityAspect:1,voidDensity:0.6,voidFloor:75,voidFalloff:0.2},voidHeights=[55,75,91,139];
      const voidProfiles=voidHeights.map(y=>draw(voidRecipe,0,1,[0,y,0]));
      const voidExpected=voidHeights.map(y=>0.6*Math.pow(2,-Math.sqrt(Math.max(y-75,0))*0.2));
      const noVoid=draw({...voidRecipe,voidDensity:0},0,1,[0,75,0]);
      check('End lower fog survives carving and decays above its floor',voidProfiles.every((p,i)=>Math.abs(p[0]-voidExpected[i])<1e-6)&&voidProfiles[0][0]===voidProfiles[1][0]&&
        voidProfiles[1][0]>voidProfiles[2][0]&&voidProfiles[2][0]>voidProfiles[3][0]&&voidProfiles[3][0]>0&&noVoid[0]===0,
        {voidProfiles,noVoid,heights:voidHeights},voidExpected);
      const nearRecipe={...endFixture,voidDensity:0.6,voidFloor:500,ambient:0.4,sunGain:0},nearOrigin=[30,75,0],nearDirection=[1,0,0],nearLimit=60;
      const nearRawOff=draw(nearRecipe,0,1,nearOrigin),nearRawOn=draw({...nearRecipe,nearClearRadius:100},0,1,nearOrigin),
        nearLightOff=draw(nearRecipe,0,5,nearOrigin,nearDirection,nearLimit),nearLightOn=draw({...nearRecipe,nearClearRadius:100},0,5,nearOrigin,nearDirection,nearLimit),
        nearViewOff=draw(nearRecipe,0,0,nearOrigin,nearDirection,nearLimit),nearViewOn=draw({...nearRecipe,nearClearRadius:100},0,0,nearOrigin,nearDirection,nearLimit);
      check('End near clear modifies only view transfer',eq(nearRawOff,nearRawOn)&&eq(nearLightOff,nearLightOn)&&nearViewOn[3]>nearViewOff[3],
        {nearRawOff,nearRawOn,nearLightOff,nearLightOn,nearViewOff,nearViewOn,origin:nearOrigin,direction:nearDirection,limit:nearLimit});
      const purpleFixture={densityModel:2,shape:1,radius:80,centerY:75,density:0.35,coverage:0.7,ambient:0.4,sunGain:0,flashEnabled:0,emission:0,speed:1.5};
      const purpleDensityA=draw(purpleFixture,0,1,[20,85,-30]),purpleDensityB=draw(purpleFixture,7,1,[20,85,-30]),
        purpleA=draw(purpleFixture,0,0,[0,75,200]),purpleB=draw(purpleFixture,7,0,[0,75,200]);
      check('Purple light fog animates radiance with fixed density and transmittance',eq(purpleDensityA,purpleDensityB)&&purpleA[3]===purpleB[3]&&purpleA.slice(0,3).some((v,i)=>v!==purpleB[i]),
        {purpleDensityA,purpleDensityB,purpleA,purpleB,recipe:purpleFixture});
      const purpleFrozenA=draw({...purpleFixture,speed:0},1),purpleFrozenB=draw({...purpleFixture,speed:0},9);
      check('Purple zero speed freezes brightness and transfer',eq(purpleFrozenA,purpleFrozenB),{purpleFrozenA,purpleFrozenB});
      const purpleBrightness=[];
      for(const time of [0,1,7,19])for(const point of [[0,75,0],[40,85,-30],[-20,100,25]])purpleBrightness.push({time,point,value:draw(purpleFixture,time,6,point)});
      check('Purple brightness modulation stays within declared bounds',purpleBrightness.every(p=>p.value[0]>=0.35&&p.value[0]<=0.65),purpleBrightness,{minimum:0.35,maximum:0.65});
      for(const densityModel of [1,2]){
        const p={...sphere,densityModel};
        const zeroNew=draw({...p,density:0,emission:8}),noCoverage=draw({...p,coverage:0}),frontNew=draw(p,0,0,[0,75,200],[0,0,-1],100),outsideNew=draw(p,0,1,[66,75,0]);
        check(`model ${densityModel} preserves zero / support / opaque identity`,eq(zeroNew,identity)&&eq(noCoverage,identity)&&eq(frontNew,identity)&&outsideNew[0]===0&&outsideNew[1]===0,
          {zeroNew,noCoverage,frontNew,outsideNew});
        for(const viewSteps of [8,96]){
          const value=draw({...p,viewSteps,lightSteps:12,density:5,coverage:1,extinction:0.5,emission:8,flashEnabled:1,flashStrength:30,mediumGain:8,
            verticalStretch:64,twistAmount:0.15,twistSpeed:-0.2,cavityRadius:0,torusWidth:0,voidDensity:5,voidFloor:2048,voidFalloff:4,nearClearRadius:0},peak.time);
          check(`extreme finite model ${densityModel} steps ${viewSteps}`,value.every(Number.isFinite)&&value[3]>=0&&value[3]<=1,value);
        }
      }
      witnesses.endFixture=endFixture;witnesses.purpleFixture=purpleFixture;
      group='legacy-core-compatibility';
      const legacyFixtures=[
        {name:'slab-transfer',recipe:{densityModel:0,shape:0,flashEnabled:0},time:2,mode:0,origin:[0,60,200]},
        {name:'sphere-transfer',recipe:{...sphere,densityModel:0,coverage:1,density:0.5},time:0,mode:0,origin:[0,75,200]},
        {name:'annulus-transfer',recipe:{densityModel:0,shape:2,innerRadius:42,coverage:1,flashEnabled:0},time:5,mode:0,origin:[30,60,200]},
        {name:'emission-transfer',recipe:{...sphere,densityModel:0,coverage:1,emission:3,emissionThreshold:0},time:3,mode:0,origin:[0,75,200]},
        {name:'flash-transfer',recipe:{...flashRecipe,densityModel:0,mediumGain:2,surfaceGain:1},time:peak.time,mode:0,origin:[0,60,200]},
        {name:'raw-density',recipe:{...sphere,densityModel:0,coverage:1},time:9,mode:1,origin:[20,85,-30]},
        {name:'flash-state',recipe:{...flashRecipe,densityModel:0},time:peak.time,mode:2,origin:[0,60,200]},
        {name:'ring-flash-state',recipe:{...flashRecipe,densityModel:0,shape:2,innerRadius:40,flashPlacement:0.9},time:peak.time,mode:2,origin:[0,60,200]}
      ];
      const legacyComparisons=legacyFixtures.map(fixture=>{
        const latest=draw(fixture.recipe,fixture.time,fixture.mode,fixture.origin),previous=draw(fixture.recipe,fixture.time,fixture.mode,fixture.origin,[0,0,-1],1000,[0,1,0],frozenProgram);
        const differences=latest.map((v,i)=>Math.abs(v-previous[i]));
        return {...fixture,latest,previous,differences,maxAbsoluteDifference:Math.max(...differences),strictFloatExact:eq(latest,previous)};
      });
      for(const comparison of legacyComparisons)check(`mode 0 frozen 0.1.0 ${comparison.name} within 1e-6`,comparison.maxAbsoluteDifference<=1e-6,comparison,{maximumAbsoluteDifference:1e-6});
      const legacyCompatibility={strictFloatExact:legacyComparisons.every(c=>c.strictFloatExact),maximumAbsoluteDifference:Math.max(...legacyComparisons.map(c=>c.maxAbsoluteDifference)),
        tolerance:1e-6,comparisons:legacyComparisons,scope:'Eight fixed fixtures compiled from the frozen 0.1.0 source in the same WebGL2 context; exactness and numeric compatibility reported separately.'};
      group='context';check('GL error zero after both programs and new models',gl.getError()===gl.NO_ERROR);
      const dbg=gl.getExtension('WEBGL_debug_renderer_info');
      const renderer=dbg?gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER);
      gl.deleteFramebuffer(fbo);gl.deleteTexture(texture);gl.deleteVertexArray(vao);
      for(const entry of [current,frozenProgram]){gl.deleteProgram(entry.program);gl.deleteShader(entry.fragment);}gl.deleteShader(vs);
      const groups=Object.fromEntries([...new Set(checks.map(c=>c.group))].map(name=>{const selected=checks.filter(c=>c.group===name);return [name,{passed:selected.filter(c=>c.passed).length,total:selected.length,ok:selected.every(c=>c.passed)}];}));
      return {ok:checks.every(c=>c.passed),checks,groups,witnesses,legacyCompatibility,renderer,format:'RGBA32F',dimensions:[1,1],scope:'Shared core analytic transfer and effect contracts; no Minecraft runtime certification'};
    },{core,schema,contract,frozenCore});
    report.coreSha256=sha256(fs.readFileSync(path.join(root,'workbench','volume-core.glsl')));
    report.schemaSha256=sha256(fs.readFileSync(path.join(root,'workbench','schema.json')));
    report.contractSha256=sha256(fs.readFileSync(path.join(root,'workbench','contract.json')));
    report.frozenSource=frozenSource;report.schema=schema.schema;report.version=schema.version;
    report.createdAt=new Date().toISOString();
    const archivePath=path.join(extendedOut,`run-${report.createdAt.replace(/[:.]/g,'-')}.json`);
    fs.writeFileSync(archivePath,JSON.stringify(report,null,2));
    fs.writeFileSync(path.join(extendedOut,'report.json'),JSON.stringify(report,null,2));
    fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify({ok:report.ok,groups:report.groups,checks:report.checks.length,legacyStrictFloatExact:report.legacyCompatibility.strictFloatExact,failed:report.checks.filter(c=>!c.passed),report:path.join(extendedOut,'report.json'),archive:archivePath}));
    if(!report.ok)process.exitCode=1;
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
