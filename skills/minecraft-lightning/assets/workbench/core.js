/* Original bounded CPU geometry. Time unit: tick = 50 ms. */
const LightningCore=(()=>{
 const simplex=typeof module!=='undefined'?require('./simplex.js').noise:LightningSimplex.noise;
 const TAU=Math.PI*2,clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
 const smooth=x=>{x=clamp(x,0,1);return x*x*(3-2*x)};
 function unit(seed,salt=0){let z=BigInt.asUintN(64,BigInt(seed)+BigInt(salt)*0x9e3779b97f4a7c15n);z=BigInt.asUintN(64,(z^(z>>30n))*0xbf58476d1ce4e5b9n);z=BigInt.asUintN(64,(z^(z>>27n))*0x94d049bb133111ebn);z^=z>>31n;return Number(z>>11n)/2**53}
 const signed=(s,k)=>unit(s,k)*2-1;
 const add=(a,b)=>a.map((x,i)=>x+b[i]),sub=(a,b)=>a.map((x,i)=>x-b[i]),mul=(a,s)=>a.map(x=>x*s),dot=(a,b)=>a.reduce((s,x,i)=>s+x*b[i],0);
 const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
 const length=a=>Math.hypot(...a),normalize=a=>length(a)<1e-9?[1,0,0]:mul(a,1/length(a)),lerp=(a,b,t)=>add(mul(a,1-t),mul(b,t));
 function rng(seed){let salt=0;return()=>unit(seed,++salt)}
 function gaussian(r){return Math.sqrt(-2*Math.log(Math.max(1e-12,r())))*Math.cos(TAU*r())*.42}
 function noise(s,x){const c=Math.floor(x),t=smooth(x-c);return signed(s,c)*(1-t)+signed(s,c+1)*t}
 function noise2(seed,x,y){const row=Math.floor(y),t=smooth(y-row);return noise(seed+row*1013,x)*(1-t)+noise(seed+(row+1)*1013,x)*t}
 function perpendicular(d,r){const q=[r()*2-1,r()*.7-.35,r()*2-1],p=sub(q,mul(d,dot(q,d)));return length(p)>1e-6?normalize(p):normalize(cross(d,Math.abs(d[1])<.9?[0,1,0]:[1,0,0]))}
 function displacement(r,seed,distance,seconds){const offset=unit(seed,41)*289,f=r.orbitNoiseFrequency,m=seconds*r.orbitFlow;return simplex(distance*f+offset,m)+.1*simplex(distance*f*10+offset,m*10)}
 function orbitTiming(r,seed){
  const base=r.pulseInterval*(.7+.6*unit(seed,17)),onset=base*.02,fade=Math.max(.5,Math.min(4,base*.25)),distance=r.orbitAnimation==='ring'?Math.min(Math.PI*1.85*r.orbitRadius,r.arcLength):Math.min(r.rodLength*.95,r.arcLength);
  return {interval:Math.max(base,onset+20*distance/(r.orbitExtendSpeed??20)+fade),onset,fade};
 }
 function sourceOrbit(r,ticks){
  const out=[],seconds=ticks/20;
  for(let lane=0;lane<r.strands;lane++){
   const seed=r.seed+lane*7919,interval=.2+Math.max(.1,r.pulseInterval-.4)*unit(seed,17),raw=ticks/interval,phase=raw-Math.floor(raw),s=seed+(Math.floor(raw)%9973)*104729;
   if(unit(s,19)>=r.throttle)continue;
   const alpha=Math.max(0,unit(s,26)-.01),fraction=Math.min(1,r.arcLength/r.rodLength)*(1-r.lengthRandomness*unit(s,25)),p0=-r.rodLength*.5+r.orbitWobble*signed(s,2),p1=r.rodLength*.5+r.orbitWobble*signed(s,3);
   function point(t){const u=phase*(1-fraction)+t*fraction;return [p0+(p1-p0)*u,r.orbitHeight+r.orbitWobble*displacement(r,s+31,u*r.rodLength,seconds),r.orbitWobble*displacement(r,s+97,u*r.rodLength,seconds)]}
   for(let i=0;i<r.samples;i++){const a=i/r.samples,b=(i+1)/r.samples;out.push({a:point(a),b:point(b),w:r.currentWidth,wb:r.currentWidth,alpha,along:a,alongB:b,depth:0,path:lane})}
  }return out;
 }
 function orbit(r,ticks){
  if(r.orbitAnimation==='spektr')return sourceOrbit(r,ticks);
  const out=[],seconds=ticks/20;
  for(let lane=0;lane<r.strands;lane++){
   const seed=r.seed+lane*7919,timing=orbitTiming(r,seed),interval=timing.interval,raw=(ticks+unit(seed,18)*interval)/interval,cycle=Math.floor(raw),age=(raw-cycle)*interval,s=seed+cycle*104729;
   if(unit(s,19)>r.throttle)continue;
   const ring=r.orbitAnimation==='ring',env=smooth(age/Math.max(.1,timing.onset*1.5))*(1-smooth((age-interval+timing.fade)/timing.fade));if(env<.015)continue;
   const reservation=Math.min(r.rodLength*.95,r.arcLength),target=reservation*(1-r.lengthRandomness*unit(s,25)),direction=r.orbitDirection===-1?-1:1;
   const angle=unit(seed,21)*TAU,radius=r.rodRadius+r.emitterSpread*unit(seed,22),ringSpan=Math.min(Math.PI*1.85,r.arcLength/Math.max(.1,r.orbitRadius)*(1-r.lengthRandomness*unit(s,27))),ringStart=unit(seed,23)*TAU;
   const travel=ring?ringSpan*r.orbitRadius:target,growth=clamp((age-timing.onset)*(r.orbitExtendSpeed??20)/20/Math.max(1e-9,travel),0,1);
   const root=-r.rodLength*.5+(r.rodLength-reservation)*unit(seed,23)+(direction<0?reservation:0);
   function point(t){const x=root+direction*target*t,gate=smooth(t/.18);return [x,r.orbitHeight+Math.cos(angle)*radius+r.orbitWobble*gate*displacement(r,seed+31,target*t,seconds),Math.sin(angle)*radius+r.orbitWobble*gate*displacement(r,seed+97,target*t,seconds)]}
   function ringPoint(t){const a=ringStart+direction*ringSpan*t,d=ringSpan*t*r.orbitRadius,gate=smooth(t/.18),rad=r.orbitRadius+r.orbitWobble*gate*displacement(r,seed+401,d,seconds);return [Math.cos(a)*rad,r.orbitHeight+r.orbitWobble*.45*gate*displacement(r,seed+503,d,seconds),Math.sin(a)*rad]}
   const alpha=env*(.65+.35*unit(s,26));
   for(let i=0;i<r.samples;i++){const t0=i/r.samples,t1=Math.min(growth,(i+1)/r.samples);if(t0>=growth)break;out.push({a:ring?ringPoint(t0):point(t0),b:ring?ringPoint(t1):point(t1),w:r.currentWidth,wb:r.currentWidth,alpha,along:t0,alongB:t1,depth:0,path:lane})}
  }return out;
 }
 function bolt(r,startPoint=[0,r.strikeHeight,0],endPoint=[0,0,0]){
  const out=[];let budget=r.maxSegments,pathId=0,branches=0;
  function monotonicize(p,start,end){const axis=sub(end,start),span=length(axis);if(span<1e-9)return;const unitAxis=mul(axis,1/span);for(let i=1;i<p.length-1;i++){const t=i/(p.length-1),delta=sub(p[i],lerp(start,end,t)),side=sub(delta,mul(unitAxis,dot(delta,unitAxis))),limit=span*.25*Math.sin(Math.PI*t);p[i]=add(lerp(start,end,t),mul(side,Math.min(1,limit/Math.max(1e-9,length(side)))));}}
  function branch(start,end,seed,depth,as,ae){
   if(budget<8&&depth>0)return;let g=Math.max(depth>0?3:1,r.strikeGenerations-depth);while(2**g>budget&&g>1)g--;
   const random=rng(seed);let p=[start,end],span=length(sub(end,start)),amp=Math.max(r.strikeDisplacement*r.branchDecay**depth,depth>0?span*(.10+.08*random()):0);
   for(let k=0;k<g;k++){const next=[p[0]];for(let i=0;i<p.length-1;i++){
    const a=p[i],b=p[i+1],d=normalize(sub(b,a)),side=perpendicular(d,random),up=normalize(cross(d,side)),lim=length(sub(b,a))*.48;
    const safeLim=lim*.67;next.push(add(lerp(a,b,.5),add(mul(side,clamp(gaussian(random)*amp,-safeLim,safeLim)),mul(up,clamp(gaussian(random)*amp*.65,-safeLim,safeLim)))),b);
   }p=next;monotonicize(p,start,end);amp*=r.strikeRoughness}
   monotonicize(p,start,end);
   const scale=r.branchDecay**depth,id=pathId++;
   for(let i=0;i<p.length-1;i++){const a=i/(p.length-1),b=(i+1)/(p.length-1),width=t=>r.strikeWidth*scale*(depth===0?.82+.28*t:1-.82*t);
    out.push({a:p[i],b:p[i+1],w:Math.max(.002,width(a)),wb:Math.max(.001,width(b)),alpha:scale,along:as+(ae-as)*a,alongB:as+(ae-as)*b,depth,path:id});budget--;}
   if(depth>=r.maxBranchDepth)return;
   for(let i=3;i<p.length-2&&budget>=8&&branches<(r.branchLimit??64);i+=6){
    const t=i/(p.length-1),weight=(.3+.95*Math.sin(Math.PI*t))*(.6+.6*t);if(random()>r.branchProbability*weight)continue;
    const origin=p[i],d=normalize(sub(p[i+1],p[i-1])),side=perpendicular(d,random),angle=r.branchAngle*(.65+random()*.7);
    let dir=normalize(add(add(mul(d,Math.cos(angle)*.64),mul(side,Math.sin(angle))),[0,-.18,0]));if(dot(dir,d)<.28)dir=normalize(add(dir,mul(d,.45)));
    const len=Math.max(.65,length(sub(end,start))*(1-t)*r.branchLength*(.65+random()*.7)),j=r.branchJitter*len,curve=perpendicular(dir,random);
    const tip=add(add(add(origin,mul(dir,len)),mul(curve,len*(.08+.16*random()))),[(random()*2-1)*j,0,(random()*2-1)*j]);
    branches++;branch(origin,tip,Math.floor(random()*2147483647),depth+1,as+(ae-as)*t,Math.min(1,as+(ae-as)*(t+(1-t)*.6)));
   }
  }branch(startPoint,endPoint,r.seed,0,0,1);return out;
 }
 function impactPlan(r,seed,center){
  r={impactRadius:1.25,impactDuration:12,impactRays:9,impactSparks:14,impactEnabled:true,...r};
  const rays=[],rings=[],sparks=[];
  for(let ray=0;ray<r.impactRays;ray++){
   const angle=unit(seed,701+ray)*TAU,radius=r.impactRadius*(.45+.55*unit(seed,801+ray));let last=null;
   for(let j=0;j<=8;j++){const t=j/8,phi=angle+signed(seed,900+ray*31+j)*.18*smooth(t*5),p=[Math.cos(phi)*radius*t,.035+.035*Math.sin(Math.PI*t),Math.sin(phi)*radius*t];if(last)rays.push({a:last,b:p,path:ray,along:(j-1)/8,alongB:t});last=p}
  }
  for(let ring=0;ring<3;ring++){let last=null;const start=ring*TAU/3+unit(seed,2101+ring)*.4;
   for(let j=0;j<=16;j++){const t=j/16,angle=start+t*Math.PI*.38,radius=r.impactRadius*(.9+.07*signed(seed,2201+ring*31+j)),p=[Math.cos(angle)*radius,.04,Math.sin(angle)*radius];if(last)rings.push({a:last,b:p,path:r.impactRays+ring,along:(j-1)/16,alongB:t});last=p}
  }
  for(let i=0;i<r.impactSparks;i++){const angle=unit(seed,3101+i)*TAU,speed=r.impactRadius*(.045+.035*unit(seed,3201+i));sparks.push({velocity:[Math.cos(angle)*speed,.09+.13*unit(seed,3301+i),Math.sin(angle)*speed],delay:unit(seed,3401+i)*.8})}
  return {center,rays,rings,sparks};
 }
 function impactPower(r,ticks){const age=ticks-r.progressionTicks,duration=r.impactDuration??12;if(r.impactEnabled===false||age<0||age>=duration)return 0;const phase=age/duration;return Math.exp(-3.4*phase)*(1-smooth((phase-.65)/.35))}
 function impact(r,bolt,ticks){
  const power=impactPower(r,ticks);if(power<=0)return [];const age=ticks-r.progressionTicks,plan=bolt.impact,out=[],progress=clamp(age/Math.min(2,(r.impactDuration??12)*.25),0,1);
  for(const s of plan.rays){if(s.along>=progress)continue;const f=clamp((progress-s.along)/(s.alongB-s.along),0,1);out.push({...s,a:add(plan.center,s.a),b:add(plan.center,lerp(s.a,s.b,f)),along:1,alongB:1,w:Math.max(.003,r.strikeWidth*.27*(1-.7*s.along)),alpha:power*(1-.3*s.along)})}
  const wave=.35+.75*smooth(age/3);for(const s of plan.rings)out.push({...s,a:add(plan.center,[s.a[0]*wave,s.a[1],s.a[2]*wave]),b:add(plan.center,[s.b[0]*wave,s.b[1],s.b[2]*wave]),along:1,alongB:1,w:Math.max(.003,r.currentWidth*.45),alpha:power*.4});
  const point=(spark,t)=>add(mul(spark.velocity,t),[0,.035-.0125*t*t,0]);
  for(let i=0;i<plan.sparks.length;i++){const spark=plan.sparks[i],t=age-spark.delay;if(t<=0)continue;const p=point(spark,t);if(p[1]<.035)continue;out.push({a:add(plan.center,point(spark,Math.max(0,t-.65))),b:add(plan.center,p),w:Math.max(.003,r.strikeWidth*.12),alpha:power*.85,path:(r.impactRays??9)+3+i,along:1,alongB:1})}
  const flash=Math.exp(-age*.9)*power;
  if(flash>.0001)for(let axis=0;axis<2;axis++){const d=axis===0?[.08,0,0]:[0,0,.08],p=add(plan.center,[0,.06,0]);out.push({a:sub(p,d),b:add(p,d),w:r.strikeWidth*2.5,alpha:flash,path:(r.impactRays??9)+3+(r.impactSparks??14)+axis,along:1,alongB:1})}
  return out;
 }
 function rain(r){
  const out=[];
  for(let i=0;i<r.rainCount;i++){
   const seed=r.seed+(i+1)*104729,cluster=r.rainStyle==='cluster',angle=i*2.399963229728653+unit(r.seed,70)*TAU,radius=Math.sqrt((i+.5)/r.rainCount),u=(i+.5)/r.rainCount-.5;
   const x=cluster?Math.cos(angle)*radius*r.rainSpread*.5:u*r.rainSpread+signed(seed,1)*r.rainSpread/r.rainCount*.35,z=cluster?Math.sin(angle)*radius*r.rainDepthSpread*.5:signed(seed,2)*r.rainDepthSpread;
   const child={...r,mode:'strike',seed,branchProbability:Math.min(r.rainBranchProbability,r.branchProbability),maxBranchDepth:Math.min(r.rainMaxBranchDepth,1),branchLimit:r.rainMaxBranches??1,branchLength:Math.min(r.branchLength,.15),branchJitter:Math.min(r.branchJitter,.12)},end=[x+(cluster?0:signed(seed,3)*.35),0,z+(cluster?0:signed(seed,4)*.35)];
   out.push({seed,delay:i*r.rainStagger,end,segments:bolt(child,[x,r.strikeHeight,z],end),impact:impactPlan(child,seed,end)});
  }return out;
 }
 function brightness(r,t){
  if(t<0||t>=r.durationTicks)return 0;if(t<r.progressionTicks)return .32;
  let power=Math.exp(-Math.max(0,t-r.progressionTicks-.6)*.5);
  for(let i=0;i<r.restrikeCount;i++){
   const at=r.progressionTicks+.9+(i+.35*unit(r.seed,110+i))*(r.durationTicks-r.progressionTicks-2)/Math.max(1,r.restrikeCount);
   if(t>=at)power=Math.max(power,(.85+.15*unit(r.seed,210+i))*Math.exp(-Math.max(0,t-at-.35)*1.3));
  }
  return power*(1-smooth((t-r.durationTicks+1.5)/1.5))*(1-r.flicker*.2*(.5+.5*Math.sin(t*19+r.seed)));
 }
 function clip(r,segs,t){
  const progress=clamp(t/r.progressionTicks,0,1),power=brightness(r,t);if(power<=0)return [];
  return segs.filter(s=>s.along<progress).map(s=>{const f=clamp((progress-s.along)/Math.max(1e-9,s.alongB-s.along),0,1);return {...s,b:lerp(s.a,s.b,f),wb:s.w+(s.wb-s.w)*f,alongB:s.along+(s.alongB-s.along)*f,alpha:s.alpha*power}});
 }
 return {unit,signed,noise,noise2,simplex,orbitTiming,orbit,bolt,rain,impactPlan,impactPower,impact,brightness,clip,clamp};
})();
if(typeof module!=='undefined')module.exports=LightningCore;
