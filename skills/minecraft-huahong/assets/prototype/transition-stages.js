/* Original event-local flight transitions, using analytic curves and smooth age windows.
 * No Shadertoy source was available; no third-party shader was copied. */
(() => {
 'use strict';
 const TAU=Math.PI*2,clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
 const smooth=(a,b,x)=>{const q=clamp((x-a)/(b-a));return q*q*(3-2*q);};
 const hash=x=>{const v=Math.sin(x*127.13+173.31)*43758.5453;return v-Math.floor(v);};
 const add=(a,b)=>a.map((v,i)=>v+b[i]),mul=(a,b)=>a.map(v=>v*b),sub=(a,b)=>a.map((v,i)=>v-b[i]);
 const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],norm=a=>mul(a,1/(Math.hypot(...a)||1));
 const rgb=s=>[1,3,5].map(i=>parseInt(s.slice(i,i+2),16)/255);
 function basis(d){const F=norm(d),N=norm(cross(F,Math.abs(F[1])>.96?[0,0,1]:[0,1,0]));return [F,N,norm(cross(N,F))];}
 function W(e,x,y,z){return add(e.p,add(mul(e.A[0],x),add(mul(e.A[1],y),mul(e.A[2],z))));}
 function color(e,t,g=1){return e.edge.map((v,i)=>(v*(1-t)+e.core[i]*t)*g);}
 class Controller {
  constructor(){this.reset();}
  reset(){this.active=false;this.events=[];this.id=0;this.started=0;this.brakeAt=null;this.settled=false;this.style='rings';this.full=false;this.arrivalOnly=false;}
  event(kind,time,p,d,params){this.events.push({id:++this.id,kind,style:this.style,t:time,p:p.slice(),A:basis(d),edge:rgb(params.edgeColor),core:rgb(params.coreColor),spread:params.spread,width:params.width,glow:params.glow,seed:173+this.id*37,duration:kind==='launch'?2.5:2.05});}
  begin(time,p,d,params,style='rings',full=true){this.reset();this.active=true;this.started=time;this.style=style;this.full=full;this.brakeAt=full?time+4.2:null;this.event('launch',time,p,d,params);}
  beginArrival(time,p,d,params,style='rings'){this.reset();this.active=true;this.started=time;this.style=style;this.arrivalOnly=true;this.brakeAt=time;}
  requestArrival(time){if(this.active&&!this.settled)this.brakeAt=this.brakeAt===null?time:Math.min(time,this.brakeAt);}
  speedScale(time){if(!this.active)return 1;if(this.settled)return 0;let v=this.arrivalOnly?1:smooth(.60,1.30,time-this.started);if(this.brakeAt!==null)v*=1-smooth(0,.70,time-this.brakeAt);return v;}
  cruiseVisibility(time){return this.active&&!this.arrivalOnly?smooth(.55,1.16,time-this.started):1;}
  advance(time,p,d,params){this.events=this.events.filter(e=>time-e.t<e.duration);if(this.active&&!this.settled&&this.brakeAt!==null&&time>=this.brakeAt+.70-1e-8){this.event('arrival',time,p,d,params);this.settled=true;return true;}return false;}
  abort(){this.active=false;this.brakeAt=null;this.settled=false;}
  finished(time){return this.active&&this.settled&&time>this.brakeAt+9.1;}
  info(time){let phase='cruise',label='仅巡航 · 阶段效果未启用';if(this.active){const t=time-this.started;
   if(this.settled){phase=this.events.some(e=>e.kind==='arrival')?'resolve':'afterglow';label=phase==='resolve'?'到达 · 散光收束':'余迹消散 · 已停止发射';}
   else if(this.brakeAt!==null&&time>=this.brakeAt){phase='brake';label='到达 · 减速收束';}
   else if(!this.arrivalOnly&&t<.60){phase='charge';label='起遁 · 聚光蓄势';}
   else if(!this.arrivalOnly&&t<1.30){phase='launch';label='起遁 · 拉伸出发';}
   else {phase='cruise';label='巡航 · 世界尾迹接管';}
  }return {enabled:this.active,style:this.style,phase,label,speedScale:this.speedScale(time),eventCount:this.events.length,worldEvents:this.events.map(e=>({id:e.id,kind:e.kind,origin:e.p.slice(),born:e.t,age:time-e.t})),sequenceSeconds:time-this.started,fullSequence:this.full};}
 }
 function ribbon(ctx,out,rows,width,tint,alpha){
  if(rows.length<3||alpha<1e-7)return;
  const arc=[0];for(let i=1;i<rows.length;i++)arc.push(arc.at(-1)+Math.hypot(...sub(rows[i].p,rows[i-1].p)));
  const total=arc.at(-1);if(total<1e-6)return;const cap=Math.min(total*.18,Math.max(.03,width*2.2)),mesh=[];let previous;
  for(let i=0;i<rows.length;i++){
   const r=rows[i],F=norm(sub(rows[Math.min(i+1,rows.length-1)].p,rows[Math.max(0,i-1)].p));
   let N=cross(F,sub(ctx.eye,r.p));if(Math.hypot(...N)<1e-8)N=basis(F)[1];N=norm(N);
   if(previous&&ctx.dot(previous,N)<0)N=mul(N,-1);previous=N;
   const end=smooth(0,cap,arc[i])*smooth(0,cap,total-arc[i]),n=norm(cross(F,N));
   mesh.push([-1,0,1].map(u=>({p:add(r.p,mul(N,width*u*(r.w??1)*Math.sqrt(end))),uv:[u,r.s??arc[i]],n,c:[...tint,alpha*(r.a??1)*end]})));
  }
  for(let i=1;i<mesh.length;i++)for(let j=0;j<2;j++){
   const q=[mesh[i-1][j],mesh[i-1][j+1],mesh[i][j+1],mesh[i][j]];
   for(const k of [0,1,2,0,2,3]){const v=q[k];ctx.vertex(out,v.p,v.c,v.uv,v.n);}
  }
 }
 function ring(ctx,out,e,radius,x,age,strength,width,offset=0){
  for(let j=0;j<3;j++){
   const rows=[];for(let i=0;i<=90;i++){
    const q=i/90,theta=j*TAU/3+q*TAU*.29+offset,ruff=1+.045*Math.sin(theta*5+e.seed)+.024*Math.sin(theta*11-age*2);
    const r=radius*ruff;rows.push({p:W(e,x+.06*Math.sin(theta*3+age),Math.cos(theta)*r,Math.sin(theta)*r),s:theta*radius,a:.57+.43*Math.sin(q*Math.PI),w:.6+.4*Math.sin(q*Math.PI)});
   }
   ribbon(ctx,out,rows,width,color(e,.5,2.5),strength);
  }
 }
 function sparks(ctx,out,e,age,start,implosion=false){
  const t=age-start;if(t<0||t>1.25)return;
  const fade=smooth(0,.08,t)*Math.pow(clamp(1-t/1.25),1.8);
  for(let j=0;j<40;j++){
   const seed=e.seed+j*7.33,theta=hash(seed)*TAU,r0=.25+hash(seed+2)*.40,v=1.4+hash(seed+3)*3.0,rows=[];
   for(let i=0;i<=12;i++){
    const q=i/12,s=Math.max(0,t-(1-q)*(.10+hash(seed+4)*.17));
    const r=implosion?(.20+3.0*Math.exp(-s*3.4)):(r0+v*s),spin=theta+(implosion?s*1.6:s*.22),x=(hash(seed+5)-.5)*(implosion?.7:1.3)*s;
    rows.push({p:W(e,x,Math.cos(spin)*r,Math.sin(spin)*r),s:s*4,a:Math.pow(q,.65),w:.20+.80*q});
   }
   ribbon(ctx,out,rows,.008+hash(seed+6)*.016,color(e,.75,3),fade*e.glow*(.25+hash(seed+7)*.45));
  }
 }
 function ringLaunch(ctx,e,age,veil,bright){
  const charge=clamp(age/.62);
  if(age<.72){
   for(let j=0;j<5;j++){
    const rows=[];for(let i=0;i<=80;i++){
     const q=i/80,a=q*TAU*(.49+hash(j+5)*.33)+j*1.256+age*(1.6+hash(j+7)*.8),r=(1.60-charge*1.13)*(.60+q*.30+hash(j+11)*.24)*(1+.06*Math.sin(q*18+j)),x=(q-.5)*(.7-charge*.5)+Math.sin(a*2+j)*.08;
     rows.push({p:W(e,x,Math.cos(a)*r,Math.sin(a)*r),s:q*6+j,a:Math.sin(Math.PI*q)**.7,w:.5+.5*q});
    }
    ribbon(ctx,veil,rows,.10,color(e,.14),smooth(0,.15,age)*(1-smooth(.48,.72,age))*.29);
    ribbon(ctx,bright,rows,.014,color(e,.63,2.3),smooth(0,.15,age)*(1-smooth(.52,.72,age))*.5);
   }
  }
  const t=age-.59;if(t>=0&&t<1.3){const fade=smooth(0,.07,t)*Math.pow(1-t/1.3,1.8);
   ring(ctx,bright,e,.27+3.1*(1-Math.exp(-t*2.1)),-t*.35,age,fade*.55,.035+.11*t,age*.3);
   ring(ctx,veil,e,.35+2.4*(1-Math.exp(-t*2)),-.1-t*.55,age,fade*.12,.17+.20*t,1.1);
  }
  sparks(ctx,bright,e,age,.61);
 }
 function vortex(ctx,e,age,arrival,veil,bright){
  const life=arrival?1.85:2.30;if(age>life)return;
  const envelope=smooth(0,.13,age)*(1-smooth(arrival?.75:1.05,life,age));
  const contract=arrival?Math.exp(-age*1.8):1-smooth(.48,1.55,age)*.83;
  for(let j=0;j<4;j++){
   const rows=[];for(let i=0;i<=112;i++){
    const q=i/112,angle=j*2.399+q*TAU*(.52+hash(j+23)*.45)+(arrival?-age*2.1:age*2.6)+.20*Math.sin(q*9+j+age);
    const r=(.10+Math.pow(q,1.20)*(arrival?2.7:1.75))*contract*(.76+hash(j+2)*.38+.13*Math.sin(q*15+j-age));
    const length=(arrival?1.4:2.5)*(.7+hash(j+8)*.5),x=(arrival?1:-1)*(q*length)*(1+age*.12)+Math.sin(q*8+j+age)*.10*q;
    rows.push({p:W(e,x,Math.cos(angle)*r,Math.sin(angle)*r),s:q*8+j,a:Math.pow(Math.sin(q*Math.PI),.75),w:(.30+.70*q)*(1+.25*Math.sin(q*8+j))});
   }
   ribbon(ctx,veil,rows,.25+(arrival?.10:.16),color(e,.15,1.2),envelope*.29);
   ribbon(ctx,bright,rows,.023,color(e,.62,2.8),envelope*.40);
  }
  if(arrival){sparks(ctx,bright,e,age,0,true);if(age>.48)ring(ctx,bright,e,.26+(age-.48)*1.25,0,age,envelope*.20,.04,age);}
  else sparks(ctx,bright,e,age,.61);
 }
 function ringArrival(ctx,e,age,veil,bright){
  const fade=smooth(0,.07,age)*Math.pow(clamp(1-age/1.8),1.8);
  ring(ctx,bright,e,.30+3.30*(1-Math.exp(-age*2.2)),0,age,fade*.65,.04+.035*age,age*.35);
  ring(ctx,veil,e,.4+2.65*(1-Math.exp(-age*1.9)),-age*.12,age,fade*.15,.24+age*.18,1.3);
  sparks(ctx,bright,e,age,0);
 }
 function build(ctx,events){
  const veil=[],bright=[],cores=[];let live=0;
  for(const e of events){const age=ctx.time-e.t;if(age<0||age>=e.duration)continue;live++;
   if(e.style==='vortex')vortex(ctx,e,age,e.kind==='arrival',veil,bright);
   else if(e.kind==='launch')ringLaunch(ctx,e,age,veil,bright);else ringArrival(ctx,e,age,veil,bright);
   if(e.kind==='launch'&&age<1.25){
    const forward=norm(sub(ctx.eye,e.p)),right=norm(cross(Math.abs(forward[1])>.97?[0,0,1]:[0,1,0],forward)),up=norm(cross(forward,right));
    const flash=smooth(0,.18,age)*(1-smooth(.64,1.20,age)),r=.045+.09*smooth(.1,.6,age)+.12*Math.exp(-Math.pow((age-.63)/.12,2));
    const points=[[-1,-1],[1,-1],[1,1],[-1,1]].map(([u,v])=>({p:add(e.p,add(mul(right,r*u),mul(up,r*v))),uv:[u,v]}));
    for(const k of [0,1,2,0,2,3])ctx.vertex(cores,points[k].p,[...color(e,.9,4),flash*.7],points[k].uv,forward);
   }
  }
  return {alpha:veil.length?[{data:veil,mode:23,center:events[0]?.p,emission:.04}]:[],additive:[...(bright.length?[{data:bright,mode:24}]:[]),...(cores.length?[{data:cores,mode:9}]:[])],diagnostics:{liveEvents:live,modelsIncluded:false,eventWorldAnchored:true}};
 }
 window.DunguangTransitionsV8={Controller,build,shaderBranch:`
 if(uMode==23||uMode==24){
  vec2 p=vec2(vUV.x*2.0,vUV.y*.64-uTime*.5);vec2 warp=flow(p,uTime*.25);
  float n=fbm(p+warp*.7);float density=smoothstep(.17,.66,n+edge*.20);
  if(uMode==23){a*=pow(edge,1.35)*density;c*=.65+.35*n;}
  else {a*=pow(edge,1.8)*(.12+.88*smoothstep(.28,.75,n+edge*.12));}
 }
 `};
})();
