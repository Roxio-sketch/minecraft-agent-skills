/* Original first-batch flow study. Only needle/vessel consumers are overridden.
 * Birth positions/frames remain world anchored. No model assets or opaque meshes. */
(() => {
 'use strict';
 const TAU=Math.PI*2, clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
 const smooth=(a,b,x)=>{const t=clamp((x-a)/(b-a));return t*t*(3-2*t);};
 const tint=(a,b,t,gain=1)=>a.map((x,i)=>(x*(1-t)+b[i]*t)*gain);
 const hash=x=>{const f=Math.sin(x*127.13+173.7)*43758.5453;return f-Math.floor(f);};
 function axes(ctx,F){F=ctx.norm(F);const N=ctx.norm(ctx.cross(F,Math.abs(F[1])>.96?[0,0,1]:[0,1,0]));return [F,N,ctx.norm(ctx.cross(N,F))];}
 function W(ctx,p,A,x,y,z){return ctx.add(p,ctx.add(ctx.mul(A[0],x),ctx.add(ctx.mul(A[1],y),ctx.mul(A[2],z))));}
 function groups(ctx){
  const cache=ctx.state.flowBirths||(ctx.state.flowBirths={}),keep=new Set(),out=[];
  for(const h of ctx.history){
   if(ctx.time-h.t>=ctx.params.life)continue;
   const key=h.seg+':'+h.t.toFixed(7);keep.add(key);
   if(!cache[key])cache[key]={...h,p:h.p.slice(),dir:h.dir.slice(),A:axes(ctx,h.dir),spread:ctx.params.spread,width:ctx.params.width};
   const b=cache[key];if(!out.length||out.at(-1).at(-1).seg!==h.seg)out.push([]);out.at(-1).push(b);
  }
  for(const k of Object.keys(cache))if(!keep.has(k))delete cache[k];
  const last=out.at(-1)?.at(-1);
  if(last&&ctx.moving&&ctx.params.speed>0){const d=Math.hypot(...ctx.sub(ctx.pos,last.p));if(d>1e-5&&d<Math.max(.8,ctx.params.speed*.22))out.at(-1).push({p:ctx.pos.slice(),t:ctx.time,seg:last.seg,dir:ctx.dir.slice(),A:axes(ctx,ctx.dir),arc:last.arc+d,spread:ctx.params.spread,width:ctx.params.width});}
  return out;
 }
 // The soft cap is measured over a full connected run, never per quad.
 function strip(ctx,out,rows,width,baseColor,opacity,rowColors=true){
  if(rows.length<3)return;
  const lengths=[0];for(let i=1;i<rows.length;i++)lengths.push(lengths.at(-1)+Math.hypot(...ctx.sub(rows[i].p,rows[i-1].p)));
  const total=lengths.at(-1),cap=Math.min(total*.10,Math.max(.035,width*1.4));let prev=null;
  const mesh=rows.map((r,i)=>{
   const F=ctx.norm(ctx.sub(rows[Math.min(rows.length-1,i+1)].p,rows[Math.max(0,i-1)].p));
   let N=ctx.cross(F,ctx.sub(ctx.eye,r.p));if(Math.hypot(...N)<1e-6)N=axes(ctx,F)[1];N=ctx.norm(N);
   if(prev&&ctx.dot(prev,N)<0)N=ctx.mul(N,-1);prev=N;
   const normal=ctx.norm(ctx.cross(F,N)),c=(rowColors&&r.color)||baseColor,fade=smooth(0,cap,lengths[i])*smooth(0,cap,total-lengths[i]);
   const w=width*(r.w??1),points=[];
   for(let j=0;j<=6;j++){const u=j/3-1;points.push({p:ctx.add(r.p,ctx.mul(N,w*u)),n:normal,uv:[u,r.s??lengths[i]],c:[...c,opacity*(r.a??1)*fade]});}
   return points;
  });
  for(let i=1;i<mesh.length;i++)for(let j=0;j<6;j++){
   const q=[mesh[i-1][j],mesh[i-1][j+1],mesh[i][j+1],mesh[i][j]];
   for(const k of [0,1,2,0,2,3])ctx.vertex(out,q[k].p,q[k].c,q[k].uv,q[k].n);
  }
 }
 const consumers={speed:'world-history distance and shader rearward transport',life:'world-tail age and independent stop decay',width:'actual main/companion ribbon widths',strands:'number of subordinate filaments',spread:'birth-frame flow separation and later transverse expansion',shellLength:'longitudinal birth taper and concentrated head reach',glow:'soft emission layer opacity',coreColor:'central narrow ridges',edgeColor:'chromatic soft flow and aged tail'};
 function finish(data,details){return {opaque:[],alpha:[],additive:data.map(([d,mode])=>({data:d,mode})),diagnostics:{worldAnchored:true,modelsIncluded:false,closedShell:false,parameterConsumers:consumers,unsupportedParameters:[],softLongitudinalCaps:true,birthFrameCached:true,...details}};}
 function needle(ctx){
  const p=ctx.params,veil=[],body=[],core=[],threads=[],ashes=[],history=groups(ctx),A=axes(ctx,ctx.dir),local=Math.pow(ctx.motionFade,1.6);
  const half=.026+p.width*.20,companions=clamp(Math.round(p.strands*.8),2,9);let arcLength=0;
  for(const g of history){
   if(g.length<3)continue;arcLength+=Math.hypot(...ctx.sub(g.at(-1).p,g[0].p));
   const main=g.map(h=>{const age=ctx.time-h.t,life=clamp(1-age/p.life),pulse=.83+.17*Math.sin(h.arc*.5-h.t*.6);
    return {p:h.p,s:h.arc,a:Math.pow(life,1.25)*pulse,w:.12+.88*Math.pow(life,.9),color:tint(ctx.edge,ctx.core,Math.pow(life,2)*.62,1.5)};});
   strip(ctx,veil,main,half*4.8,ctx.edge,p.glow*.10);
   strip(ctx,body,main,half*1.35,ctx.edge,.40);
   strip(ctx,core,main,half*.23,tint(ctx.edge,ctx.core,.9,3.6),.63,false);
   for(let j=0;j<companions;j++){
    const phase=j*2.399,rows=g.map(h=>{
     const age=ctx.time-h.t,life=clamp(1-age/p.life),open=smooth(.03,.50,age),amp=(.035+.16*hash(j+4))*h.spread;
     const swing=ctx.noise1(h.arc*.36+j*3.7,91)*.45,reach=amp*(.15+open*(.8+age*.08));
     const line=W(ctx,h.p,h.A,0,Math.cos(phase+swing)*reach,Math.sin(phase+swing)*reach);
     const end=smooth(0,.3,life-(j%3)*.07),breath=.65+.35*smooth(-.7,.6,Math.sin(h.arc*.72+phase));
     return {p:line,s:h.arc+j*1.6,a:Math.pow(life,1.8)*end*breath,w:.3+.7*life};
    });
    strip(ctx,threads,rows,half*(.08+.10*hash(j+21)),tint(ctx.edge,ctx.core,.35,2.4),.32);
   }
   // Rare long, faint residual strokes are attached to old world samples.
   for(let i=3;i<g.length-9;i+=23){const h=g[i],age=ctx.time-h.t;if(age<.35)continue;
    const q=hash(i+h.seg*37),life=clamp(1-age/p.life),rows=[];
    for(let k=0;k<=12;k++){const t=k/12;rows.push({p:W(ctx,h.p,h.A,-t*(.12+q*.4),Math.sin(i)*(.12+age*.08)*h.spread,Math.cos(i)*(.12+age*.07)*h.spread),w:Math.sin(Math.PI*t),a:life*life,s:h.arc-t*.5});}
    strip(ctx,ashes,rows,half*.09,tint(ctx.edge,ctx.core,.25,1.6),.20);
   }
  }
  if(local>.00001){
   const L=1.0+p.shellLength*.76+p.speed*.025;
   const head=[];
   for(let i=0;i<=96;i++){const q=i/96,x=.68-q*L,shape=Math.pow(smooth(0,.15,q),.8)*Math.pow(1-q,1.8);
    head.push({p:W(ctx,ctx.pos,A,x,0,Math.sin(q*Math.PI)*.018),s:x,a:local*(1-q*.7),w:shape,color:tint(ctx.edge,ctx.core,.20+.80*Math.pow(1-q,1.7),1.8)});
   }
   strip(ctx,veil,head,(.12+p.width*.48)*1.9,ctx.edge,local*p.glow*.16);
   strip(ctx,body,head,.12+p.width*.48,ctx.edge,.50);
   strip(ctx,core,head,.018+p.width*.07,tint(ctx.edge,ctx.core,.93,4.0),.72,false);
   for(let j=0;j<5;j++){
    const rows=[],phase=j*2.399;
    for(let i=0;i<=52;i++){const q=i/52,reach=Math.sin(q*Math.PI)*(.026+hash(j+9)*.07)*p.spread;
     rows.push({p:W(ctx,ctx.pos,A,.56-q*L*(.70+hash(j+3)*.36),Math.cos(phase)*reach,Math.sin(phase)*reach),w:Math.pow(Math.sin(q*Math.PI),.8),a:local*(1-q),s:-q*L+j});}
    strip(ctx,threads,rows,.004+p.width*.013,tint(ctx.edge,ctx.core,.45,2.5),.32);
   }
  }
  return finish([[veil,13],[body,13],[threads,14],[core,14],[ashes,14]],{topology:'single-concentrated-long-comet-with-age-separated-companion-streams',historyLineCount:1,auxiliaryFilaments:companions,historyGroups:history.length,historyLength:arcLength,localMotionFade:local,flowRevision:5,continuousRearwardTransport:true});
 }
 function vessel(ctx){
  const p=ctx.params,veil=[],flow=[],ridges=[],hot=[],history=groups(ctx);const count=clamp(Math.round(p.strands*.7),3,10);
  let centerRows=0;
  function lane(h,sign,j=0){
   const age=ctx.time-h.t,life=clamp(1-age/p.life),open=smooth(.02,.60+p.shellLength*.045,age);
   const asym=sign>0?1:.76,phase=h.t*.73+(sign>0?.4:2.3),wander=ctx.noise1(h.arc*.21+sign*6,96)*.16;
   const angle=.20+Math.sin(h.t*.41)*.25+wander;
   const sep=(.26+h.spread*.42)*open*asym*(.88+.12*Math.sin(phase));
   const curl=ctx.noise1(h.arc*.65+sign*3.1+j*.3,97)*open*(.13+age*.024),f=(j-(count-1)*.5)*.022*open;
   return {p:W(ctx,h.p,h.A,0,sign*sep*(.45+angle)+curl+f,sign*sep*.86+Math.sin(h.arc*.43+sign)*open*.07+f*.6),s:h.arc+(sign>0?0:1.3)+j*.37,
    a:Math.pow(life,1.6),w:(.27+.73*Math.pow(life,.9))*(.16+.84*open)*asym*(.87+.13*ctx.noise1(h.arc*.53+sign*4,98)),life,age};
  }
  for(const g of history){if(g.length<3)continue;centerRows+=g.length;
   for(const sign of [-1,1]){
    const rows=g.map(h=>{const r=lane(h,sign);return {...r,color:tint(ctx.edge,ctx.core,.10+.22*r.life*r.life,1.4)};});
    const width=p.width*(sign>0?.95:.72);
    strip(ctx,veil,rows,width*2.4,ctx.edge.map(v=>Math.pow(v,1.7)*1.25),p.glow*.10,false);
    strip(ctx,flow,rows,width,ctx.edge,.46);
    strip(ctx,ridges,rows,width*.18,tint(ctx.edge,ctx.core,.64,2.2),.36,false);
    strip(ctx,hot,rows,width*.027,tint(ctx.edge,ctx.core,.92,3.2),.38,false);
    for(let j=0;j<count;j++){
     const fine=g.map(h=>{const r=lane(h,sign,j),phase=h.arc*.37+j*1.9;
      const offset=(j-(count-1)*.5)*width*.24*(.2+.8*smooth(.1,.8,r.age));
      return {...r,p:W(ctx,r.p,h.A,0,offset*.3,offset+Math.sin(phase)*width*.05),a:r.a*(.50+.50*smooth(-.4,.7,Math.sin(h.arc*.8+j*1.2))),w:r.w};});
     strip(ctx,ridges,fine,.004+p.width*.013,tint(ctx.edge,ctx.core,.25,1.8),.30);
    }
   }
  }
  const local=Math.pow(ctx.motionFade,2.2),A=axes(ctx,ctx.dir);
  if(local>.00001){
   // Only a compact, pinched birth flow precedes the two historical streams.
   // There are no detached pressure arcs or repeated vertical sheets.
   for(const sign of [-1,1]){
    const rows=[];
    for(let i=0;i<=48;i++){const q=i/48,shape=Math.pow(Math.sin(Math.PI*q),.9),x=.70-q*1.05;
     rows.push({p:W(ctx,ctx.pos,A,x,sign*q*q*.024,sign*q*q*.042),s:x,w:shape*(sign>0?1:.72),a:local*(1-q*.7)});}
    strip(ctx,veil,rows,p.width*.60,tint(ctx.edge,ctx.core,.22,1.6),p.glow*.15);
    strip(ctx,flow,rows,p.width*.25,tint(ctx.edge,ctx.core,.35,1.8),.35);
    strip(ctx,hot,rows,.010+p.width*.025,tint(ctx.edge,ctx.core,.95,3.7),.48);
   }
  }
  return finish([[veil,13],[flow,15],[ridges,13],[hot,14]],{topology:'two-asymmetric-continuous-world-shear-streams',historyTrails:2,historyAnchorPoses:centerRows,streamFilaments:count*2,independentPhases:true,repeatedPressureArcs:0,continuousRearwardTransport:true,localMotionFade:local,flowRevision:5});
 }
 window.DunguangV4FlowBaseline={needle:window.DunguangPrimaryForms.needle,vessel:window.DunguangCarrierForms.vessel};
 window.DunguangPrimaryForms.needle=needle;window.DunguangCarrierForms.vessel=vessel;
 window.DunguangFlowV5={shaderBranch:`
 if(uMode==13){
  float axis=vUV.y+uTime*(.8+uFlowSpeed*.20);
  float broad=fbm(vec2(axis*.34,vUV.x*1.9)+vec2(0.,uTime*.09));
  float warp=fbm(vec2(axis*.70,vUV.x*2.0))*1.4;
  float fibers=.68+.32*sin(vUV.x*17.0+warp*2.4-axis*.95);
  a*=pow(edge,1.75)*(.60+.40*broad)*fibers;
 }
 if(uMode==14){
  float axis=vUV.y+uTime*(1.0+uFlowSpeed*.18);
  float pulse=.83+.17*sin(axis*2.1+sin(axis*.49)*1.2);
  a*=pow(edge,2.7)*pulse;
 }
 if(uMode==15){
  float axis=vUV.y+uTime*(1.1+uFlowSpeed*.24);
  vec2 uv=vec2(vUV.x*2.3,axis*.44);
  float bend=fbm(uv+vec2(0.,uTime*.12));
  float ridge=pow(.5+.5*sin(vUV.x*14.+bend*4.2-axis*.68),3.0);
  float density=.43+.32*fbm(uv*1.7)+ridge*.28;
  float erosion=smoothstep(.20,.58,fbm(uv*.80+vec2(bend*.6,0.))+edge*.30);
  a*=pow(edge,1.4)*density*erosion;
  c*=.80+ridge*.38;
 }
 `};
})();
