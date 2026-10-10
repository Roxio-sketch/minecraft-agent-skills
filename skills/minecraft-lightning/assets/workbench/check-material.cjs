const assert=require('assert/strict');
const {defaultRecipe,presets,validateRecipe}=require('./recipe.js');
const {LightningMaterial:m}=require('./gpu.js');
const core=require('./core.js');
let checks=0;
const check=(v,label)=>{assert(v,label);checks++;};
check(Object.keys(defaultRecipe).length===63,'complete 63-field defaults');
const old={...defaultRecipe};delete old.gradientRatio;delete old.gradientTarget;delete old.gradientCoreMode;
assert.deepEqual(validateRecipe(old),defaultRecipe);checks++;
assert.deepEqual(validateRecipe({...old,gradientRatio:50}),defaultRecipe);checks++;
const strings=Object.fromEntries(Object.entries(old).map(([k,v])=>[k,String(v)]));strings.preset='custom';
assert.deepEqual(validateRecipe({values:strings}),defaultRecipe);checks++;
const custom={...presets.cluster,rainCount:7,rainSpread:8.4,rainDepthSpread:1.7,rainStagger:.9,gradientRatio:72.5};
assert.deepEqual(validateRecipe(custom),custom);checks++;
for(const bad of [{gradientTarget:'branches'},{gradientCoreMode:'auto'},{gradientTarget:null},{gradientCoreMode:1},{gradientRatio:null},{gradientRatio:101},{strands:'1.000000000000000001'},{seed:'2147483648'},{restrikeCount:1.5},{restrikeCount:'1.5'},{unknown:1},{glowGain:true},{gradientEnabled:1},{values:[]}])
 assert.throws(()=>validateRecipe(bad)),checks++;
for(const key of ['seed','strands','restrikeCount'])for(const text of ['1.0','1e0','0.0001e4'])check(validateRecipe({[key]:text})[key]===1,'exact integral decimal string');
for(const ratio of [0,25,50,75,100])for(let i=0;i<=1000;i++){
 const along=i/1000,t=m.gradientMix(along,ratio);
 check(Number.isFinite(t)&&t>=0&&t<=1,'finite ratio weight');
 if(ratio===50)check(t===along,'50% legacy interpolation');
}
for(const target of ['both','core','glow'])for(const enabled of [false,true])for(const mode of ['vivid','additive']){
 const r={...custom,gradientEnabled:enabled,gradientTarget:target,gradientCoreMode:mode,coreColor:'#8040c0',glowColor:'#206080',gradientStart:'#ff0000',gradientEnd:'#00ffff',gradientRatio:50};
 for(const halo of [false,true]){
  const active=enabled&&(target==='both'||target===(halo?'glow':'core'));
  assert.deepEqual(m.color(r,halo,.5),active?(halo?[16,48,64]:[64,32,96]).map(v=>v/255):(halo?[32,96,128]:[128,64,192]).map(v=>v/255));checks++;
 }
 check(m.vividCore(r)===(enabled&&target!=='glow'&&mode==='vivid'),'vivid is isolated to the selected gradient core');
}
for(const enabled of [false,true])for(const ratio of [0,25,50,75,100]){
 const r={...custom,gradientEnabled:enabled,gradientRatio:ratio,coreColor:'#ffffff',glowColor:'#87cfff',gradientStart:'#ff0000',gradientEnd:'#00ffff'};
 const b=core.rain(r)[0];
 for(const age of [.1,.8,2,5,10])for(const s of core.impact(r,b,r.progressionTicks+age)){
  check(s.along===1&&s.alongB===1,'all ground materials sample actual parent tip');
  assert.deepEqual(m.color(r,false,s.along),enabled?(ratio===100?[1,0,0]:[0,1,1]):[1,1,1]);checks++;
  assert.deepEqual(m.color(r,true,s.alongB),enabled?(ratio===100?[135/255,0,0]:[0,207/255,1]):[135/255,207/255,1]);checks++;
 }
}
const project=p=>[p[0]*100+300,p[1]*100+300,0,100];
const segs=[{a:[-.199,0,0],b:[0,0,0],w:.05,alpha:.8,along:0,alongB:.5,path:0},{a:[0,0,0],b:[0,.199,0],w:.05,alpha:.8,along:.5,alongB:1,path:0}];
const r={...defaultRecipe,mode:'strike',strikeWidth:.05,glowWidth:1.2};
const mesh=m.mesh(m.paths(segs,project)[0],r,true);
check(mesh.vertices.length/10===18,'two convex halo bodies and one disk');
check(mesh.vertices.every(Number.isFinite),'finite halo attributes');
for(let i=0;i<12;i+=6){
 const points=[0,1,2].map(n=>mesh.vertices.slice((i+n)*10,(i+n)*10+2));
 const area=(points[1][0]-points[0][0])*(points[2][1]-points[0][1])-(points[1][1]-points[0][1])*(points[2][0]-points[1][0]);
 check(area>0,'short thick halo body remains convex');
}
const gap=[...segs,{...segs[0],a:[2,0,0],b:[3,0,0]}];
check(m.paths(gap,project)[0].length===2,'disconnected paths never bridged');
console.log('PASS: 63-field portable JSON/old 60+61 compatibility, selected gradient layers, isolated vivid mode, exact 50% and ratio endpoints, actual ground tip tints, WebGL convex bodies/disk and gap isolation ('+checks+' checks)');
