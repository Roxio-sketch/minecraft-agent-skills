const core=require('./core.js');
const r={schema:1,mode:'orbit',seed:1337,strands:16,samples:48,objectRadius:1.35,orbitRadius:1.52,orbitHeight:0,rodLength:8.5,rodRadius:.15,emitterSpread:.1,orbitAnimation:'grow',orbitDirection:1,orbitWobble:.28,orbitFlow:1.8,orbitNoiseFrequency:.75,arcLength:2.2,lengthRandomness:.65,pulseInterval:6,throttle:.25,currentWidth:.014,glowWidth:.07,coreGain:3.8,glowGain:2.4,strikeHeight:18,strikeWidth:.055,strikeDisplacement:1.5,strikeRoughness:.53,branchProbability:.16,branchDecay:.5,branchLength:.3,branchAngle:.72,branchJitter:.18,progressionTicks:1.2,durationTicks:14,restrikeCount:2,flicker:.22,strikeGenerations:7,maxBranchDepth:2,maxSegments:1200,branchLimit:64,rainCount:5,rainSpread:12,rainDepthSpread:4,rainStagger:1.2,rainMaxBranchDepth:1,rainMaxBranches:1,rainBranchProbability:.04,coreColor:'#ffffff',glowColor:'#87cfff',gradientEnabled:false,gradientStart:'#ffffff',gradientEnd:'#ffffff'};
const arcs=core.orbit(r,0),arcsLater=core.orbit(r,.35);if(!arcs.length||arcs.length>r.strands*r.samples||!arcsLater.length)throw Error('orbit pulse count');if(JSON.stringify(arcs)===JSON.stringify(arcsLater))throw Error('orbit pulse is static');if(!arcs.every(s=>s.a.every(Number.isFinite)))throw Error('orbit finite');
const a=core.bolt({...r,mode:'strike'}),b=core.bolt({...r,mode:'strike'});if(!a.length||a.length>r.maxSegments)throw Error('bolt budget');if(JSON.stringify(a)!==JSON.stringify(b))throw Error('seed repeat');
const c=core.bolt({...r,mode:'strike',seed:r.seed+1});if(JSON.stringify(a)===JSON.stringify(c))throw Error('seed variation');
if(a[0].a[1]!==r.strikeHeight)throw Error('endpoint');for(const s of a)for(const p of [s.a,s.b])if(p.some(v=>!Number.isFinite(v)))throw Error('nonfinite');
const rain=core.rain({...r,rainCount:5,rainSpread:12,rainDepthSpread:4,rainStagger:1.2,rainMaxBranchDepth:1,rainBranchProbability:.04});if(rain.length!==5)throw Error('rain count');for(const b of rain)for(const s of b.segments)if(s.depth>1)throw Error('rain branch depth');
if(core.brightness({...r,mode:'strike'},99)!==0)throw Error('envelope');
const {presets,validateRecipe}=require('./recipe.js');validateRecipe(presets.source);
for(const [x,y,expected] of [[0,0,0],[.1,.2,.8285183685716719],[1.25,-3.75,.7282445621599388],[289.2,7.1,-.6433033650825536],[-500,120,-.2022023529315641]])if(Math.abs(core.simplex(x,y)-expected)>1e-12)throw Error('simplex reference');
for(const mode of ['grow','ring'])for(const direction of [1,-1]){
 const roots=new Map(),params={...r,orbitAnimation:mode,orbitDirection:direction,throttle:1};
 for(let tick=0;tick<48;tick+=.35)for(const s of core.orbit(params,tick)){
  if(s.along!==0)continue;const root=JSON.stringify(s.a);if(roots.has(s.path)&&roots.get(s.path)!==root)throw Error(mode+' root drift');roots.set(s.path,root);
 }
 if(roots.size!==r.strands)throw Error('unobserved root');
}
const interval=r.pulseInterval*(.7+.6*core.unit(r.seed,17)),start=interval*(2-core.unit(r.seed,18));
const growing={...r,throttle:1,orbitFlow:0},early=core.orbit(growing,start+interval*.1).filter(s=>s.path===0),late=core.orbit(growing,start+interval*.45).filter(s=>s.path===0);
if(!early.length||late.length<=early.length||early[0].a[0]!==late[0].a[0]||late.at(-1).b[0]<=early.at(-1).b[0])throw Error('arc fails to extend from fixed root');
for(let seed=0;seed<16;seed++){
 const params={...r,seed,strikeRoughness:.85,strikeDisplacement:12,strikeGenerations:9,maxSegments:1600};
 const segments=core.bolt(params);if(segments.length>params.maxSegments)throw Error('extreme budget');
 for(const s of segments){if([...s.a,...s.b].some(v=>!Number.isFinite(v)))throw Error('extreme finite');if(s.depth===0&&s.b[1]>=s.a[1])throw Error('main path folds back');}
 const shower=core.rain({...params,branchProbability:1,rainBranchProbability:.12,rainMaxBranches:1});
 for(const bolt of shower)if(new Set(bolt.segments.filter(s=>s.depth>0).map(s=>s.path)).size>1)throw Error('rain hard branch cap');
}
const source={...presets.source,throttle:1},src=core.orbit(source,3.75);if(JSON.stringify(src)!==JSON.stringify(core.orbit(source,3.75))||src.length!==source.strands*source.samples)throw Error('source replay');
for(const mode of ['grow','ring'])for(const direction of [1,-1])for(const speed of [.1,2,20,120])for(const pulse of [.5,6,20]){
 const params={...presets.orbit,orbitAnimation:mode,orbitDirection:direction,orbitExtendSpeed:speed,pulseInterval:pulse,lengthRandomness:0,orbitWobble:0,throttle:1};
 const timing=core.orbitTiming(params,params.seed),start=timing.interval*(2-core.unit(params.seed,18)),travel=mode==='ring'?Math.min(Math.PI*1.85*params.orbitRadius,params.arcLength):Math.min(params.rodLength*.95,params.arcLength),age=timing.onset+travel*20/speed*.25,dt=Math.min(1,travel*20/speed*.1);
 const path=t=>core.orbit(params,start+t).filter(s=>s.path===0),first=path(age),second=path(age+dt),p=first.at(-1).b,q=second.at(-1).b;
 const distance=mode==='grow'?Math.abs(q[0]-p[0]):Math.abs(Math.atan2(p[0]*q[2]-p[2]*q[0],p[0]*q[0]+p[2]*q[2]))*params.orbitRadius;
 if(Math.abs(distance/dt*20-speed)>1e-7)throw Error('tip speed does not match setting');
 if(JSON.stringify(first[0].a)!==JSON.stringify(second[0].a))throw Error('speed moves root');
 const full=path(timing.onset+travel*20/speed);if(full.length!==params.samples||Math.abs(full.at(-1).alongB-1)>1e-9)throw Error('slow arc expires before full extension');
}
const cluster=validateRecipe({...presets.cluster,impactDuration:40}),bundle=core.rain(cluster);
if(JSON.stringify(bundle)!==JSON.stringify(core.rain(cluster)))throw Error('cluster seed replay');
for(const b of bundle){
 if((b.end[0]/(cluster.rainSpread*.5))**2+(b.end[2]/(cluster.rainDepthSpread*.5))**2>1+1e-9)throw Error('cluster outside bounds');
 if(b.segments.some(s=>s.depth>0))throw Error('cluster branches enabled by default');
 if(core.impact(cluster,b,cluster.progressionTicks-.001).length||core.impact(cluster,b,cluster.progressionTicks+cluster.impactDuration).length)throw Error('impact timing');
 const hits=core.impact(cluster,b,cluster.progressionTicks+2);
 if(!hits.length||hits.length>cluster.impactRays*8+48+cluster.impactSparks+2)throw Error('impact budget');
 if(hits.some(s=>[...s.a,...s.b,s.w,s.alpha].some(v=>!Number.isFinite(v))))throw Error('impact finite');
 if(core.impact({...cluster,impactEnabled:false},b,cluster.progressionTicks+2).length)throw Error('impact disable');
}
console.log('PASS: existing geometry/seed/branch checks; physical tip speed across both arc modes/directions/pulse intervals, full slow extension, bounded deterministic cluster, impact arrival/lifetime/disable/finite/budget');
