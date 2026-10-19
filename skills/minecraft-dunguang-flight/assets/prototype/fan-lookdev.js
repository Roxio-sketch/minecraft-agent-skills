/* Original open fan-flow geometry and GLSL. No creature / wing / carrier models. */
(() => {
 'use strict';
 const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
 const smooth=(a,b,x)=>{const q=clamp((x-a)/(b-a));return q*q*(3-2*q);};
 const tint=(a,b,t,g=1)=>a.map((v,i)=>(v*(1-t)+b[i]*t)*g);
 const hash=x=>{const n=Math.sin(x*127.13+173.71)*43758.5453;return n-Math.floor(n);};
 function frame(ctx,d){const F=ctx.norm(d),N=ctx.norm(ctx.cross(F,Math.abs(F[1])>.96?[0,0,1]:[0,1,0]));return [F,N,ctx.norm(ctx.cross(N,F))];}
 function world(ctx,h,x,y,z){return ctx.add(h.p,ctx.add(ctx.mul(h.A[0],x),ctx.add(ctx.mul(h.A[1],y),ctx.mul(h.A[2],z))));}
 function groups(ctx){
  const store=ctx.state.fanBirths||(ctx.state.fanBirths={}),keep=new Set(),out=[];
  for(const h of ctx.history){
   if(ctx.time-h.t>=ctx.params.life)continue;
   const key=h.seg+':'+h.t.toFixed(7);keep.add(key);
   if(!store[key])store[key]={...h,p:h.p.slice(),A:frame(ctx,h.dir),width:ctx.params.width,spread:ctx.params.spread,life:ctx.params.life,reach:ctx.params.shellLength};
   const b=store[key];if(!out.length||out.at(-1).at(-1).seg!==h.seg)out.push([]);out.at(-1).push(b);
  }
  for(const key of Object.keys(store))if(!keep.has(key))delete store[key];
  const last=out.at(-1)?.at(-1);
  if(last&&ctx.moving&&ctx.params.speed>0){const distance=Math.hypot(...ctx.sub(ctx.pos,last.p));
   if(distance>1e-5&&distance<Math.max(.8,ctx.params.speed*.22))out.at(-1).push({p:ctx.pos.slice(),t:ctx.time,arc:last.arc+distance,seg:last.seg,A:frame(ctx,ctx.dir),width:ctx.params.width,spread:ctx.params.spread,life:ctx.params.life,reach:ctx.params.shellLength});
  }
  return out;
 }
 function profile(h,j,side,count){
  const rank=(j+.55)/(count+.15),phase=j*1.79+(side<0?.94:0);
  return {rank,phase,life:h.life*(.43+.43*(1-rank))*(side>0?1:.92),gain:1-.22*rank};
 }
 function at(ctx,h,j,side,count,age,u){
  const c=profile(h,j,side,count),v=clamp(1-age/c.life),open=1-Math.exp(-age*(1.60+h.reach*.16));
  // Fan displacement grows after birth; there is no rigid sheet attached to a pose.
  const s=side*(.014+h.spread*(.26+c.rank*1.45)*open)*(1+.09*Math.sin(h.arc*.46+c.phase-age*.8)*open);
  const wave=(Math.sin(h.arc*.38+c.phase-age*.90)*(.16+h.spread*.12)+Math.sin(h.arc*.87+c.phase*1.7-age*1.25)*.07)*open;
  const half=(.045+h.width*(.50+.60*(1-c.rank)))*smooth(0,.21,age)*Math.pow(v,.72)*(1+.29*Math.sin(h.arc*.55+c.phase));
  const roll=.65+side*.20+Math.sin(h.arc*.29+c.phase-age*.62)*.24;
  const widthSide=ctx.add(ctx.mul(h.A[1],Math.cos(roll)),ctx.mul(h.A[2],Math.sin(roll)));
  const normal=ctx.norm(ctx.cross(h.A[0],widthSide));
  const middle=world(ctx,h,.12*Math.exp(-age*7),s*.79+wave*.48,s*.61+wave-age*.06);
  const curl=half*(u*u-.27)*(.45+.30*Math.sin(h.arc*.61+c.phase-age));
  const wrinkle=(.022*Math.sin(h.arc*2.2+u*2.5+c.phase-age*1.4)+.018*Math.sin(h.arc*1.3-u*3.2+c.phase*.6))*open;
  return {p:ctx.add(middle,ctx.add(ctx.mul(widthSide,half*u),ctx.mul(normal,curl+wrinkle))),n:normal,
   a:Math.pow(v,1.8)*c.gain,half,phase:c.phase,life:c.life};
 }
 function capArc(ctx,records){const arc=[0];for(let i=1;i<records.length;i++)arc.push(arc.at(-1)+Math.hypot(...ctx.sub(records[i].p,records[i-1].p)));return arc;}
 function film(ctx,group,j,side,count){
  const valid=group.filter(h=>ctx.time-h.t<profile(h,j,side,count).life);
  if(valid.length<4)return [];
  const arc=capArc(ctx,valid),total=arc.at(-1),cap=Math.min(.85,total*.22),mesh=[],data=[];
  const taper=i=>smooth(0,cap,arc[i])*smooth(0,cap,total-arc[i]);
  function point(i,u){const h=valid[i];return at(ctx,h,j,side,count,ctx.time-h.t,u*Math.pow(taper(i),.6));}
  for(let i=0;i<valid.length;i++){
   const h=valid[i],age=ctx.time-h.t,back=valid[Math.max(0,i-1)],front=valid[Math.min(valid.length-1,i+1)],row=[];
   for(let k=0;k<=8;k++){
    const u=k/4-1,q=point(i,u);
    const du=ctx.sub(point(i,clamp(u+.002,-1,1)).p,point(i,clamp(u-.002,-1,1)).p);
    const ds=ctx.sub(point(Math.min(valid.length-1,i+1),u).p,point(Math.max(0,i-1),u).p);
    let n=ctx.cross(ds,du);if(Math.hypot(...n)<1e-9)n=q.n;
    const fade=smooth(0,cap,arc[i])*smooth(0,cap,total-arc[i]);
    row.push({p:q.p,n:ctx.norm(n),uv:[u,h.arc+q.phase*2.2],c:[...tint(ctx.edge,ctx.core,.16+.20*(1-age/q.life),1.55),q.a*fade*.72]});
   }
   mesh.push(row);
  }
  for(let i=1;i<mesh.length;i++)for(let k=0;k<8;k++){
   const q=[mesh[i-1][k],mesh[i-1][k+1],mesh[i][k+1],mesh[i][k]];
   for(const n of [0,1,2,0,2,3]){const v=q[n];ctx.vertex(data,v.p,v.c,v.uv,v.n);}
  }
  return data;
 }
 function ribbon(ctx,out,rows,width,color,strength){
  if(rows.length<4)return;
  const arc=capArc(ctx,rows),total=arc.at(-1),cap=Math.min(.22,total*.14),mesh=[];let lastN;
  for(let i=0;i<rows.length;i++){
   const r=rows[i],F=ctx.norm(ctx.sub(rows[Math.min(i+1,rows.length-1)].p,rows[Math.max(0,i-1)].p));
   let N=ctx.cross(F,ctx.sub(ctx.eye,r.p));if(Math.hypot(...N)<1e-8)N=frame(ctx,F)[1];N=ctx.norm(N);
   if(lastN&&ctx.dot(lastN,N)<0)N=ctx.mul(N,-1);lastN=N;
   const fade=smooth(0,cap,arc[i])*smooth(0,cap,total-arc[i]),normal=ctx.norm(ctx.cross(F,N));
   mesh.push([-1,0,1].map(u=>({p:ctx.add(r.p,ctx.mul(N,width*u*(r.w??1))),n:normal,uv:[u,r.s],c:[...color,strength*r.a*fade]})));
  }
  for(let i=1;i<mesh.length;i++)for(let k=0;k<2;k++){
   const q=[mesh[i-1][k],mesh[i-1][k+1],mesh[i][k+1],mesh[i][k]];
   for(const n of [0,1,2,0,2,3]){const v=q[n];ctx.vertex(out,v.p,v.c,v.uv,v.n);}
  }
 }
 function bird(ctx){
  const p=ctx.params,runs=groups(ctx),count=clamp(Math.round(p.strands*.43),2,5),alpha=[],sheen=[],veins=[],wisps=[];let films=0;
  for(const g of runs){
   for(const side of [-1,1])for(let j=0;j<count;j++){
    const data=film(ctx,g,j,side,count);
    if(data.length){alpha.push({data,mode:20,center:g[Math.floor(g.length*.55)].p,emission:.035});
     for(let i=0;i<data.length;i++){const k=i%12;sheen.push(k>=3&&k<=5?data[i]*1.55:k===6?data[i]*p.glow*.72:data[i]);}films++;}
    // Two weak ridges are embedded within each broad gauze stream, never a frame.
    for(const u of [-.32,.41]){
     const rows=g.filter(h=>ctx.time-h.t<profile(h,j,side,count).life).map(h=>{const age=ctx.time-h.t,q=at(ctx,h,j,side,count,age,u+.09*Math.sin(h.arc*.42+j));
      return {p:q.p,s:h.arc+q.phase,a:q.a*(.5+.5*smooth(-.5,1,Math.sin(h.arc*.35+j))),w:.20+.80*clamp(1-age/q.life)};
     });
     ribbon(ctx,veins,rows,.005+p.width*.017,tint(ctx.edge,ctx.core,.60,2.2),p.glow*(j===0?.23:.15));
    }
   }
   // Longer diffuse threads inherit the same birth plane and diverge beyond films.
   for(const side of [-1,1])for(let j=0;j<2;j++){
    const rows=g.map(h=>{const age=ctx.time-h.t,v=clamp(1-age/h.life),q=at(ctx,h,j,side,count,age,.25);
     const drift=smooth(.65,2.2,age);return {p:ctx.add(q.p,ctx.mul(h.A[2],drift*(j===0?.24:-.22))),s:h.arc+j*1.4,a:Math.pow(v,2.4)*smooth(0,.10,age),w:.2+.8*v};});
    ribbon(ctx,wisps,rows,.009+p.width*.026,tint(ctx.edge,ctx.core,.3,1.4),p.glow*.13);
   }
  }
  return {opaque:[],alpha,additive:[{data:sheen,mode:22},{data:veins,mode:21},{data:wisps,mode:21}],diagnostics:{
   modelsIncluded:false,closedShell:false,worldAnchored:true,birthFrameCached:true,softLongitudinalCaps:true,
   topology:'world-history-open-gauze-fan-with-asymmetric-lengths',flowRevision:7,historyGroups:runs.length,
   gauzeStreams:films,streamsPerSide:count,rigidWings:false,continuousSharedNormals:true,fluidSolver:false,
   parameterConsumers:{speed:'host history transport and rearward shader flow',life:'cached stream and wisp lifetime',width:'gauze stream width and ridge thickness',strands:'gauze stream count',spread:'age-dependent fan opening',shellLength:'opening response time',glow:'embedded ridge and wisp intensity',coreColor:'thin warm highlights',edgeColor:'translucent gauze color'},unsupportedParameters:[]
  }};
 }
 window.DunguangFanBaseline=window.DunguangCarrierForms.bird;
 window.DunguangCarrierForms.bird=bird;
 window.DunguangFanV7={shaderBranch:`
 if(uMode==20){
  float axis=vUV.y+uTime*(.65+uFlowSpeed*.13);
  vec2 p=vec2(vUV.x*2.2,axis*.30);vec2 f=flow(p,uTime*.32);
  float n=fbm(p+f*.82),large=fbm(vec2(vUV.x*.90,axis*.15)+f*.35);
  float ragged=abs(vUV.x)+(.09+.14*(1.-edge))*sin(axis*1.1+n*6.);
  float featherEdge=(1.-smoothstep(.64,.99,ragged))*smoothstep(.22,.63,large+edge*.29);
  float fibers=pow(smoothstep(.30,.79,fbm(vec2(vUV.x*8.+n*.9,axis*.075)+f*.35)),2.8);
  float ridges=pow(max(0.,1.-abs(n-.49)*7.0),4.0);
  float rim=pow(1.-abs(dot(normalize(vNormal),normalize(uEye-vWorld))),1.7);
  a*=featherEdge*(.38+large*.50)*(.60+fibers*.22+ridges*.20);
  c*=.65+n*.25;c+=vec3(.20,.16,.07)*(rim*.44+fibers*.13);
 }
 if(uMode==21){
  float s=vUV.y+uTime*(.8+uFlowSpeed*.14);
  a*=pow(edge,2.3)*(.61+.39*smoothstep(-.7,.8,sin(s*.9+sin(s*.21))));
 }
 if(uMode==22){
  float s=vUV.y+uTime*(.65+uFlowSpeed*.13);
  vec2 p=vec2(vUV.x*2.2,s*.30);vec2 f=flow(p,uTime*.32);
  float n=fbm(p+f*.82);
  float fil=pow(smoothstep(.30,.79,fbm(vec2(vUV.x*8.+n*.9,s*.075)+f*.35)),2.8);
  float densityPatch=smoothstep(.34,.72,fbm(vec2(vUV.x*.9,s*.15)+f*.35));
  float ragged=abs(vUV.x)+(.09+.14*(1.-edge))*sin(s*1.1+n*6.);
  a*=(1.-smoothstep(.64,.99,ragged))*densityPatch*(fil*.64+.08);
 }
 `};
})();
