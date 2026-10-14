/* Detached 3-D smoke packets; fixed 20 Hz RK2. No billboard or shared cloud rotation. */
function auraPackets(recipe,time,moving=false,isolated=false){
 const p=recipe.params,positions=new Float32Array(128),strengths=new Float32Array(32),flows=new Float32Array(128),seeds=new Float32Array(32),min=[1e6,1e6,1e6],max=[-1e6,-1e6,-1e6];
 const body=[[-.24,-.45,-.15,.24,.36,.15],[-.22,.35,-.22,.22,.8,.22],[-.47,-.58,-.12,-.26,.32,.12],[.26,-.58,-.12,.47,.32,.12],[-.23,-1.15,-.12,-.02,-.45,.12],[.02,-1.15,-.12,.23,-.45,.12]];
 const gear=[[-.28,-.45,-.19,.28,.36,.19],[-.25,.34,-.25,.25,.84,.25],[-.24,-1.12,-.14,.24,-.5,.14],[.54,-.35,-.05,.66,.72,.05]];
 const sources=p.attachment===1?body.map(b=>({b,w:p.bodyWeight})):p.attachment===2?gear.map(b=>({b,w:p.gearWeight})):[...body.map(b=>({b,w:p.bodyWeight})),...gear.map(b=>({b,w:p.gearWeight}))];
 const active=isolated?[{b:[-.04,-.35,-.04,.04,-.27,.04],w:1}]:sources.filter(s=>s.w>0);
 if(!p.attachment||!active.length)return {positions,strengths,flows,seeds,count:0,min:[0,0,0],max:[0,0,0],drift:0};
 const end=Math.floor(time*p.puffRate),start=Math.max(0,Math.floor((time-p.puffLife)*p.puffRate),end-31);let count=0,drift=0;
 for(let serial=start;serial<=end;serial++){
  const birth=serial/p.puffRate,age=time-birth;if(age<0||age>=p.puffLife)continue;
  let state=(p.seed^Math.imul(serial,1103515245))|0;const rand=()=>{state=(Math.imul(state,1664525)+1013904223)|0;return (state>>>0)/4294967296;};
  const source=active[Math.floor(rand()*active.length)],b=source.b,point=[0,1,2].map(i=>b[i]+rand()*(b[i+3]-b[i])),axis=Math.floor(rand()*3),side=rand()<.5?-1:1;
  point[axis]=side<0?b[axis]:b[axis+3];point[0]+=moving?Math.sin(birth*.9)*.7:0;const initial=point.slice();
  const y=rand(),theta=rand()*Math.PI*2,h=Math.sqrt(1-y*y),normal=[0,0,0];normal[axis]=side;
  let direction=[h*Math.cos(theta)*.7+normal[0]*.35,y*.7+normal[1]*.35+.25,h*Math.sin(theta)*.7+normal[2]*.35];
  const norm=Math.hypot(...direction);direction=direction.map(v=>v/norm);const speed=p.outflow*(.8+rand()*.4),base=direction.map(v=>v*speed);
  const inherit=[moving?Math.cos(birth*.9)*.63*p.inertia:0,0,0],phase=[rand()*Math.PI*2,rand()*Math.PI*2,rand()*Math.PI*2];
  const bias=[Math.cos(phase[0]),.15+.15*Math.sin(phase[1]),Math.sin(phase[2])].map(v=>v*p.outflow*.18),riseFactor=.7+rand()*.45;
  const velocity=(x,elapsed)=>{
   const t=(birth+elapsed)*p.speed,c1=[Math.cos(x[1]*1.6+t+phase[0]),.25*Math.sin(x[2]*1.6+.8*t+phase[1]),Math.sin(x[0]*1.6+.7*t+phase[2])],c2=[Math.sin(x[2]*2.3-.6*t+phase[2]),.2*Math.cos(x[0]*2.1-.75*t+phase[0]),Math.cos(x[1]*2.3+.55*t+phase[1])];
   return [0,1,2].map(i=>base[i]*Math.exp(-elapsed*.12)+inherit[i]*Math.exp(-elapsed*1.2)+bias[i]+(i===1?p.rise*.7*riseFactor:0)+c1[i]*p.swirl*.14+c2[i]*p.warp*.10);
  };
  let elapsed=0;while(elapsed<age-.00001){const dt=Math.min(.05,age-elapsed),a=velocity(point,elapsed),mid=point.map((v,i)=>v+a[i]*dt*.5),c=velocity(mid,elapsed+dt*.5);for(let i=0;i<3;i++)point[i]+=c[i]*dt;elapsed+=dt;}
  const fraction=age/p.puffLife,radius=Math.max(.01,(.035+p.spread*(.18+fraction*.58))*p.radius);
  positions.set([...point,radius],count*4);flows.set([...velocity(point,age),age],count*4);seeds[count]=phase[0];strengths[count]=Math.min(1,age/.12)*Math.pow(1-fraction,1.5)*source.w;
  drift=Math.max(drift,Math.hypot(...point.map((v,i)=>v-initial[i])));
  const extent=radius*2.65*Math.max(1,p.stretch);for(let i=0;i<3;i++){min[i]=Math.min(min[i],point[i]-extent);max[i]=Math.max(max[i],point[i]+extent);}count++;
 }
 return {positions,strengths,flows,seeds,count,min:count?min:[0,0,0],max:count?max:[0,0,0],drift};
}
