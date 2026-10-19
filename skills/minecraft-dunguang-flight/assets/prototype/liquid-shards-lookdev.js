/* Original liquid light and fragmented-energy flow study.
 * Only water/swords are replaced. No opaque meshes, models or fluid-solver claims. */
(() => {
 'use strict';
 const TAU=Math.PI*2,clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
 const smooth=(a,b,x)=>{const t=clamp((x-a)/(b-a));return t*t*(3-2*t);};
 const hash=x=>{const f=Math.sin(x*127.131+173.71)*43758.5453;return f-Math.floor(f);};
 const color=(a,b,t,g=1)=>a.map((v,i)=>(v*(1-t)+b[i]*t)*g);
 function frame(ctx,F){F=ctx.norm(F);const N=ctx.norm(ctx.cross(F,Math.abs(F[1])>.96?[0,0,1]:[0,1,0]));return [F,N,ctx.norm(ctx.cross(N,F))];}
 function W(ctx,p,A,x,y,z){return ctx.add(p,ctx.add(ctx.mul(A[0],x),ctx.add(ctx.mul(A[1],y),ctx.mul(A[2],z))));}
 function historyGroups(ctx){
  const cache=ctx.state.secondBirths||(ctx.state.secondBirths={}),keep=new Set(),groups=[];
  for(const h of ctx.history){
   if(ctx.time-h.t>=ctx.params.life)continue;
   const key=h.seg+':'+h.t.toFixed(7);keep.add(key);
   if(!cache[key])cache[key]={...h,p:h.p.slice(),dir:h.dir.slice(),A:frame(ctx,h.dir),speed:ctx.params.speed,spread:ctx.params.spread,width:ctx.params.width,life:ctx.params.life};
   const b=cache[key];if(!groups.length||groups.at(-1).at(-1).seg!==h.seg)groups.push([]);groups.at(-1).push(b);
  }
  for(const k of Object.keys(cache))if(!keep.has(k))delete cache[k];
  const last=groups.at(-1)?.at(-1);
  if(last&&ctx.moving&&ctx.params.speed>0){const d=Math.hypot(...ctx.sub(ctx.pos,last.p));if(d>1e-5&&d<Math.max(.8,ctx.params.speed*.22))groups.at(-1).push({p:ctx.pos.slice(),t:ctx.time,dir:ctx.dir.slice(),seg:last.seg,arc:last.arc+d,A:frame(ctx,ctx.dir),speed:ctx.params.speed,spread:ctx.params.spread,width:ctx.params.width,life:ctx.params.life,currentEndpoint:true});}
  return groups;
 }
 // Complete-run caps; the fine trails share one position/opacity at each join.
 function ribbon(ctx,out,rows,width,rgb,opacity){
  if(rows.length<3)return;
  const arc=[0];for(let i=1;i<rows.length;i++)arc.push(arc.at(-1)+Math.hypot(...ctx.sub(rows[i].p,rows[i-1].p)));
  const total=arc.at(-1),span=Math.min(total*.15,Math.max(.015,width*2));let lastN;
  const mesh=rows.map((r,i)=>{
   const tangent=ctx.norm(ctx.sub(rows[Math.min(i+1,rows.length-1)].p,rows[Math.max(i-1,0)].p));
   let N=ctx.cross(tangent,ctx.sub(ctx.eye,r.p));if(Math.hypot(...N)<1e-7)N=frame(ctx,tangent)[1];N=ctx.norm(N);
   if(lastN&&ctx.dot(N,lastN)<0)N=ctx.mul(N,-1);lastN=N;
   const n=ctx.norm(ctx.cross(tangent,N)),fade=smooth(0,span,arc[i])*smooth(0,span,total-arc[i]);
   return [-1,0,1].map(u=>({p:ctx.add(r.p,ctx.mul(N,width*(r.w??1)*u)),n,uv:[u,r.s??arc[i]],c:[...(r.color||rgb),opacity*(r.a??1)*fade]}));
  });
  for(let i=1;i<mesh.length;i++)for(let j=0;j<2;j++){
   const quad=[mesh[i-1][j],mesh[i-1][j+1],mesh[i][j+1],mesh[i][j]];
   for(const k of [0,1,2,0,2,3]){const v=quad[k];ctx.vertex(out,v.p,v.c,v.uv,v.n);}
  }
 }
 function sprite(ctx,out,p,A,sx,sy,rgb,alpha){
  const n=ctx.norm(ctx.sub(ctx.eye,p)),right=ctx.norm(ctx.cross(Math.abs(n[1])>.98?[0,0,1]:[0,1,0],n)),up=ctx.norm(ctx.cross(n,right));
  const row=[[-1,-1],[1,-1],[1,1],[-1,1]].map(([u,v])=>({p:ctx.add(p,ctx.add(ctx.mul(right,sx*u),ctx.mul(up,sy*v))),uv:[u,v]}));
  for(const k of [0,1,2,0,2,3])ctx.vertex(out,row[k].p,[...rgb,alpha],row[k].uv,n);
 }
 const consumers={speed:'host distance history and rearward material transport',life:'independent age decay of sheets, droplets and shards',width:'actual sheet / splinter and short trail widths',strands:'sheet count or persistent shard births per interval',spread:'birth-space flow offsets and independent transverse motion',shellLength:'sheet unfolding reach or energy splinter extent',glow:'caustic and secondary emission amount',coreColor:'thin highlights and shard ridges',edgeColor:'transparent fluid and aged fragment wakes'};
 function finish(alpha,additive,details){return {opaque:[],alpha,additive,diagnostics:{modelsIncluded:false,worldAnchored:true,closedShell:false,parameterConsumers:consumers,unsupportedParameters:[],birthFrameCached:true,softLongitudinalCaps:true,flowRevision:6,...details}};}

 function sheetAt(ctx,h,j,age,u=0){
  const p=ctx.params,life=h.life*(.60+.085*(j%4)),v=clamp(1-age/life),open=smooth(.015,.16+p.shellLength*.085,age);
  const phase=j*2.399+.45*Math.sin(h.arc*.34+j)+.38*Math.sin(age*.95+j*.8);
  const radius=h.spread*(.008+open*(.28+.16*hash(j+23)))*(1+age*.07);
  const wide=(.075+h.width*(j===0?1.9:.70+.55*hash(j+7)))*smooth(0,.16,age)*(.24+.76*open)*Math.pow(v,.70);
  const roll=phase+1.12*Math.sin(h.arc*.30+j*1.7)-age*.25;
  const side=ctx.add(ctx.mul(h.A[1],Math.cos(roll)),ctx.mul(h.A[2],Math.sin(roll))),normal=ctx.norm(ctx.cross(h.A[0],side));
  const middle=W(ctx,h.p,h.A,.47*Math.exp(-age*8),Math.cos(phase)*radius,Math.sin(phase)*radius*.78);
  const camber=wide*(u*u-.32)*(.35+.40*Math.sin(h.arc*.48+j+age*.32));
  const wrinkle=.010*Math.sin(h.arc*2.2+u*3.4-age*1.6+j)+.004*Math.sin(h.arc*5.4-u*7.0-age*.8);
  return {p:ctx.add(middle,ctx.add(ctx.mul(side,wide*u),ctx.mul(normal,camber+wrinkle*open))),w:wide,a:Math.pow(v,1.55),s:h.arc,normal,life};
 }
 function liquidMesh(ctx,out,group,j){
  if(group.length<4)return;
  const visible=group.filter(h=>ctx.time-h.t<h.life*(.60+.085*(j%4)));
  if(visible.length<4)return;
  const arc=[0];for(let i=1;i<visible.length;i++)arc.push(arc.at(-1)+Math.hypot(...ctx.sub(visible[i].p,visible[i-1].p)));
  const total=arc.at(-1),cap=Math.min(total*.12,.24),mesh=[];
  for(let i=0;i<visible.length;i++){
   const h=visible[i],age=Math.max(0,ctx.time-h.t),rows=[];
   for(let k=0;k<=12;k++){
    const u=k/6-1,at=sheetAt(ctx,h,j,age,u),back=visible[Math.max(0,i-1)],front=visible[Math.min(visible.length-1,i+1)];
    const du=ctx.sub(sheetAt(ctx,h,j,age,clamp(u+.002,-1,1)).p,sheetAt(ctx,h,j,age,clamp(u-.002,-1,1)).p);
    const ds=ctx.sub(sheetAt(ctx,front,j,Math.max(0,ctx.time-front.t),u).p,sheetAt(ctx,back,j,Math.max(0,ctx.time-back.t),u).p);
    let n=ctx.cross(ds,du);if(Math.hypot(...n)<1e-9)n=at.normal;
    const edgeWindow=Math.pow(Math.max(0,1-u*u),.65),end=smooth(0,cap,arc[i])*smooth(0,cap,total-arc[i]);
    rows.push({p:at.p,n:ctx.norm(n),uv:[u,h.arc+j*1.53],c:[...color(ctx.edge,ctx.core,.14+.14*(1-age/h.life),1.25),at.a*end*edgeWindow*.57]});
   }
   mesh.push(rows);
  }
  for(let i=1;i<mesh.length;i++)for(let k=0;k<12;k++){
   const q=[mesh[i-1][k],mesh[i-1][k+1],mesh[i][k+1],mesh[i][k]];
   for(const n of [0,1,2,0,2,3]){const v=q[n];ctx.vertex(out,v.p,v.c,v.uv,v.n);}
  }
 }
 function water(ctx){
  const p=ctx.params,groups=historyGroups(ctx),alpha=[],caustic=[],threads=[],drops=[],dropTrails=[];
  const sheets=clamp(Math.round(p.strands*.5),3,6);let dropletCount=0,liveSheets=0;
  for(const group of groups){
   for(let j=0;j<sheets;j++){
    const data=[];liquidMesh(ctx,data,group,j);
    if(data.length){const h=group[Math.floor(group.length*.60)];alpha.push({data,mode:16,emission:.025,center:h.p});
     // A separate, sparse caustic pass brightens ridges rather than the full film.
     const hot=data.map((v,i)=>{const k=i%12;if(k>=3&&k<=5)return color(ctx.edge,ctx.core,.60,2.5)[k-3];if(k===6)return v*p.glow*1.35;return v;});
     for(const v of hot)caustic.push(v);liveSheets++;
    }
    for(const u of [-.72,.57]){
     const rows=group.filter(h=>ctx.time-h.t<h.life*(.60+.085*(j%4))).map(h=>{
      const age=Math.max(0,ctx.time-h.t),at=sheetAt(ctx,h,j,age,u+.05*Math.sin(h.arc*.65+j));
      return {p:at.p,s:h.arc+j,a:at.a*(.55+.45*hash(j+u*7)),w:.3+.7*Math.pow(clamp(1-age/h.life),.7)};
     });
     ribbon(ctx,threads,rows,.003+p.width*.014,color(ctx.edge,ctx.core,.60,1.8),p.glow*.15);
    }
   }
   // Births are selected by immutable world-distance serial, never the live index.
   for(const h of group){
    const serial=Math.round(h.arc/.14);if(h.currentEndpoint||serial%7!==0)continue;
    const j=serial%sheets,release=h.life*(.38+.05*(j%3)),age=ctx.time-h.t-release;
    if(age<0||age>Math.min(1.6,h.life-release))continue;
    const sign=hash(serial+4)>.5?1:-1,birth=sheetAt(ctx,h,j,release,sign*.90),d=hash(serial+13);
    function position(t){const at=W(ctx,birth.p,h.A,-t*(.12+h.speed*.025),sign*t*(.16+d*.20),t*(.12+d*.16));at[1]-=.38*t*t;return at;}
    const fade=Math.pow(clamp(1-age/1.6),1.4)*smooth(0,.10,age),at=position(age),size=.010+p.width*(.014+d*.028);
    sprite(ctx,drops,at,h.A,size*(1.2+d),size,color(ctx.edge,ctx.core,.73,1.7),fade*p.glow*.48);
    const rows=[];for(let k=0;k<=12;k++){const q=k/12,t=Math.max(0,age-(1-q)*(.12+d*.12));rows.push({p:position(t),s:t,a:fade*Math.pow(q,.6),w:Math.sin(q*Math.PI)*.8+.1});}
    ribbon(ctx,dropTrails,rows,size*.45,color(ctx.edge,ctx.core,.50,1.5),p.glow*.35);dropletCount++;
   }
  }
  return finish(alpha,[{data:caustic,mode:17},{data:threads,mode:19},{data:dropTrails,mode:19},{data:drops,mode:9}],{topology:'unequal-twisting-world-fluid-silks-with-released-water-streaks',openMembranes:liveSheets,sheetsPerGroup:sheets,historyGroups:groups.length,droplets:dropletCount,continuousSharedNormals:true,asymmetricSheets:true,gravity:[0,-.76,0],dropletsReleasedFromSheetEdges:true,sceneRefraction:false,fluidSolver:false,localMotionFade:ctx.motionFade});
 }

 function shardBirths(ctx,groups){
  const store=ctx.state.shardPackets||(ctx.state.shardPackets={}),seen=new Set(),out=[];
  for(const g of groups)for(const h of g){if(h.currentEndpoint)continue;const bucket=Math.floor((h.t+1e-8)/.12),key=h.seg+':'+bucket;seen.add(key);
   if(!store[key])store[key]={...h,p:h.p.slice(),A:h.A.map(x=>x.slice()),bucket};
  }
  for(const [key,b] of Object.entries(store)){const age=ctx.time-b.t;if(age>=0&&age<b.life)out.push(b);else if(!seen.has(key))delete store[key];}
  return out;
 }
 function shardPosition(ctx,b,j,age){
  const seed=b.bucket*17.31+b.seg*73+j*6.17,phase=hash(seed+1)*TAU,rad=(.10+hash(seed+3)*.60)*b.spread;
  const spin=phase+age*(.14+.30*hash(seed+5)),gather=.35+.65*Math.exp(-age*(.30+.35*hash(seed+7)));
  const dist=b.speed*(.19+.12*hash(seed+8))*(1-Math.exp(-age*.66))/.66;
  const curl=Math.sin(age*1.7+phase)*age*.055;
  return W(ctx,b.p,b.A,.12+dist,Math.cos(spin)*rad*gather+curl,Math.sin(spin)*rad*gather+age*.025);
 }
 function splinter(ctx,out,center,A,L,width,rgb,alpha,phase){
  // A curved open emissive patch; soft fragment erosion shapes its silhouette.
  const rows=[];
  for(let i=0;i<=10;i++){
   const q=i/10,x=(q-.47)*L,w=width*Math.pow(Math.sin(Math.PI*q),.72)*(1-.38*q),roll=phase+q*.32;
   const side=ctx.add(ctx.mul(A[1],Math.cos(roll)),ctx.mul(A[2],Math.sin(roll))),n=ctx.norm(ctx.cross(A[0],side));
   const p=ctx.add(center,ctx.add(ctx.mul(A[0],x),ctx.mul(n,Math.sin(q*Math.PI)*width*.22)));
   rows.push([-1,0,1].map(u=>({p:ctx.add(p,ctx.mul(side,w*u)),uv:[u,q],n,c:[...rgb,alpha*Math.pow(Math.sin(Math.PI*q),.65)]})));
  }
  for(let i=1;i<rows.length;i++)for(let j=0;j<2;j++){
   const q=[rows[i-1][j],rows[i-1][j+1],rows[i][j+1],rows[i][j]];
   for(const k of [0,1,2,0,2,3]){const v=q[k];ctx.vertex(out,v.p,v.c,v.uv,v.n);}
  }
 }
 function swords(ctx){
  const p=ctx.params,groups=historyGroups(ctx),births=shardBirths(ctx,groups),pieces=[],ridges=[],tails=[],soft=[],bridges=[];
  const perBirth=clamp(Math.round(p.strands*.55),2,7);let shards=0,shortTrails=0;
  for(const b of births)for(let j=0;j<perBirth;j++){
   const age=ctx.time-b.t,seed=b.bucket*17.31+b.seg*73+j*6.17,life=b.life*(.63+.37*hash(seed+12));
   if(age<0||age>=life)continue;
   const fade=smooth(0,.065,age)*Math.pow(clamp(1-age/life),1.45),center=shardPosition(ctx,b,j,age);
   const before=shardPosition(ctx,b,j,Math.max(0,age-.01)),after=shardPosition(ctx,b,j,age+.01),A=frame(ctx,ctx.sub(after,before));
   const L=(.065+p.shellLength*(.038+.078*hash(seed+14)))*(.65+.35*fade),width=(.012+p.width*(.13+.24*hash(seed+17)))*(.5+.5*fade),phase=hash(seed+19)*TAU+age*.28;
   splinter(ctx,pieces,center,A,L,width,color(ctx.edge,ctx.core,.16+.40*hash(seed+21),1.8),fade*.65,phase);
   splinter(ctx,ridges,center,A,L*.94,width*.18,color(ctx.edge,ctx.core,.84,3.1),fade*p.glow*.78,phase);
   const duration=.28+.54*hash(seed+26),rows=[];
   for(let k=0;k<=18;k++){
    const q=k/18,t=age-duration*(1-q);if(t<0)continue;
    rows.push({p:shardPosition(ctx,b,j,t),s:(b.arc||0)+t*(1.2+b.speed*.18),a:fade*Math.pow(q,.8),w:Math.pow(q,.8)*(.55+.45*fade)});
   }
   if(rows.length>=3){ribbon(ctx,soft,rows,width*2.7,ctx.edge,fade*p.glow*.09);ribbon(ctx,tails,rows,.004+p.width*.028,color(ctx.edge,ctx.core,.42,2.1),p.glow*.52);shortTrails++;}
   shards++;
  }
  // Sparse, dim path wisps associate the fragments without filling their gaps.
  for(const g of groups)for(let j=0;j<3;j++){
   const phase=j*2.399,rows=g.map(h=>{const age=ctx.time-h.t,v=clamp(1-age/h.life),r=(.12+age*.06)*h.spread;
    return {p:W(ctx,h.p,h.A,0,Math.cos(phase)*r,Math.sin(phase)*r),s:h.arc,a:Math.pow(v,2.2)*(.3+.7*smooth(-.2,.8,Math.sin(h.arc*.5+j))),w:.4+.6*v};});
   ribbon(ctx,bridges,rows,.006+p.width*.025,ctx.edge,p.glow*.045);
  }
  return finish([],[{data:bridges,mode:19},{data:soft,mode:19},{data:tails,mode:19},{data:pieces,mode:18},{data:ridges,mode:18}],{topology:'world-born-fragmented-energy-stream-with-individual-analytic-short-histories',shards,shortTrails,persistentBirthPackets:births.length,shardsPerBirth:perBirth,historyGroups:groups.length,localFormation:false,ownPastPositions:true,independentBallisticDrift:true,localMotionFade:ctx.motionFade});
 }
 window.DunguangV5SecondBaseline={water:window.DunguangExtraForms.water,swords:window.DunguangExtraForms.swords};
 window.DunguangExtraForms.water=water;window.DunguangExtraForms.swords=swords;
 window.DunguangLiquidShardsV6={shaderBranch:`
 if(uMode==16||uMode==17){
  float axis=vUV.y+uTime*(.55+uFlowSpeed*.18);
  vec2 uv=vec2(vUV.x*3.2,axis*.38);
  vec2 warp=flow(uv,uTime*.35);
  float n=fbm(uv+warp*.85);
  float ridge=pow(max(0.,1.-abs(n-.50)*10.),5.0);
  float filament=pow(.5+.5*sin(vUV.x*18.+n*5.5-axis*.48),12.0);
  vec3 N=normalize(vNormal),V=normalize(uEye-vWorld);
  float rim=pow(1.-abs(dot(N,V)),2.4);
  float spec=pow(abs(dot(N,normalize(V+normalize(vec3(-.3,.8,.5))))),32.);
  float erosion=smoothstep(.18,.55,fbm(uv*.75+warp)+edge*.26);
  if(uMode==16){a*=pow(edge,1.2)*erosion*(.28+rim*.54+ridge*.23);c*=.65+.25*n;c+=vec3(.22,.36,.42)*(rim*.45+spec*.65);}
  if(uMode==17){a*=pow(edge,1.4)*erosion*(ridge*.90+filament*.12+spec*.55+rim*.12);c*=.80+ridge*.40;}
 }
 if(uMode==18){
  float body=smoothstep(0.,.14,vUV.y)*smoothstep(0.,.20,1.-vUV.y);
  float n=fbm(vec2(vUV.x*3.0,vUV.y*5.0)+vec2(vWorld.x*.08,-uTime*.26));
  float slit=.38+.62*smoothstep(.025,.18,abs(vUV.x+.12*sin(vUV.y*5.)));
  a*=pow(edge,.85)*body*slit*smoothstep(.16,.59,n+edge*.24);
 }
 if(uMode==19){
  float axis=vUV.y+uTime*(.9+uFlowSpeed*.13);
  a*=pow(edge,2.0)*(.72+.28*sin(axis*2.4+sin(axis*.7)));
 }
 `};
})();
