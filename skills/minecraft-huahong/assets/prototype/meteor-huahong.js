/* Independent meteor study: sampled world paths, open combustion ribbons and
 * birth-anchored fragments. No supplied shader or third-party shader is embedded. */
(() => {
 'use strict';
 const tau=Math.PI*2,clamp=x=>Math.max(0,Math.min(1,x));
 const smooth=(a,b,x)=>{const q=clamp((x-a)/(b-a));return q*q*(3-2*q);};
 const mix=(a,b,t,g=1)=>a.map((v,i)=>(v*(1-t)+b[i]*t)*g);
 const rnd=n=>{const v=Math.sin(n*71.371+19.713)*15317.317;return v-Math.floor(v);};
 function axes(c,d){const f=c.norm(d),r=c.norm(c.cross(f,Math.abs(f[1])>.95?[0,0,1]:[0,1,0]));return [f,r,c.norm(c.cross(r,f))];}
 const point=(c,p,a,x,y,z)=>c.add(p,c.add(c.mul(a[0],x),c.add(c.mul(a[1],y),c.mul(a[2],z))));
 function groups(c){
  const out=[];
  for(const h of c.history){
   if(c.time-h.t>=c.params.life)continue;
   if(!out.length||out.at(-1).at(-1).seg!==h.seg)out.push([]);
   out.at(-1).push(h);
  }
  const g=out.at(-1),last=g?.at(-1);
  if(last&&c.moving&&c.params.speed>0&&Math.hypot(...c.sub(c.pos,last.p))<.8)g.push({p:c.pos.slice(),t:c.time,dir:c.dir.slice(),seg:last.seg,arc:last.arc+Math.hypot(...c.sub(c.pos,last.p))});
  return out;
 }
 // Shared-row mesh, continuous normals and whole-strip end caps avoid quad seams.
 function ribbon(c,out,rows,width,color,opacity,oriented=false){
  if(rows.length<3)return;
  const lengths=[0];for(let i=1;i<rows.length;i++)lengths.push(lengths.at(-1)+Math.hypot(...c.sub(rows[i].p,rows[i-1].p)));
  const total=lengths.at(-1);if(total<1e-6)return;
  const cap=Math.min(total*.12,Math.max(.025,width*.9));let prev;
  const mesh=rows.map((r,i)=>{
   const f=c.norm(c.sub(rows[Math.min(i+1,rows.length-1)].p,rows[Math.max(i-1,0)].p));
   let side=oriented&&r.side?r.side:c.cross(f,c.sub(c.eye,r.p));if(Math.hypot(...side)<1e-5)side=axes(c,f)[1];side=c.norm(side);
   if(prev&&c.dot(prev,side)<0)side=c.mul(side,-1);prev=side;
   const normal=c.norm(c.cross(f,side)),alpha=opacity*(r.a??1)*smooth(0,cap,lengths[i])*smooth(0,cap,total-lengths[i]);
   return Array.from({length:9},(_,j)=>{const u=j/4-1;return {p:c.add(r.p,c.mul(side,width*(r.w??1)*u)),n:normal,uv:[u,r.s??lengths[i]],col:[...(r.color||color),alpha]};});
  });
  for(let i=1;i<mesh.length;i++)for(let j=0;j<8;j++){
   const q=[mesh[i-1][j],mesh[i-1][j+1],mesh[i][j+1],mesh[i][j]];
   for(const k of [0,1,2,0,2,3])c.vertex(out,q[k].p,q[k].col,q[k].uv,q[k].n);
  }
 }
 function fragments(c,out,history,warm){
  const life=Math.min(1.65,c.params.life),p=c.params;let count=0;
  // Cache each birth orientation and controls once, never attach old sparks to head.
  const cache=c.state.meteorBirths||(c.state.meteorBirths={}),keep=new Set();
  for(const g of history)for(let i=0;i<g.length;i++){
   const h=g[i],tick=Math.round(h.t*60),age=c.time-h.t,key=h.seg+':'+tick;
   if(tick%3!==0||age<=.035||age>=life)continue;
   keep.add(key);
   const b=cache[key]||(cache[key]={p:h.p.slice(),a:axes(c,h.dir),seed:tick+h.seg*1009,spread:p.spread});
   if(rnd(b.seed)>Math.min(.85,p.strands/14))continue;
   const phase=rnd(b.seed+1)*tau,drift=(.18+rnd(b.seed+2)*.55)*b.spread;
   const position=t=>point(c,b.p,b.a,-(.3+rnd(b.seed+4))*t-.52*t*t,Math.cos(phase)*drift*t,Math.sin(phase)*drift*t-.11*t*t);
   const rows=[];
   for(let k=0;k<=8;k++){const t=Math.max(0,age-k*.016);rows.push({p:position(t),s:t*4,w:Math.sin(Math.PI*k/8),a:Math.pow(1-age/life,1.6)});}
   ribbon(c,out,rows,.008+p.width*.03,mix(c.edge,c.core,warm?.42:.65,2),.35);count++;
  }
  for(const key of Object.keys(cache))if(!keep.has(key))delete cache[key];
  return count;
 }
 function meteor(c,warm){
  const p=c.params,soft=[],body=[],hot=[],embers=[],history=groups(c),a=axes(c,c.dir),fade=Math.pow(c.motionFade,1.4);
  const count=Math.max(3,Math.round(p.strands*(warm?1:.7)));let groupsCount=0;
  for(const g of history){
   if(g.length<3)continue;groupsCount++;
   if(!warm){
    const rows=g.map(h=>{const age=c.time-h.t,left=clamp(1-age/p.life);return {p:h.p,s:h.arc,w:Math.pow(left,1.3)*(.22+.78*Math.exp(-age*.35)),a:Math.pow(left,1.7),color:mix(c.edge,c.core,Math.exp(-age*2),1.3)};});
    ribbon(c,soft,rows,p.width*1.4,c.edge,p.glow*.10);
    ribbon(c,body,rows,p.width*.42,c.edge,.35);
    ribbon(c,hot,rows,.008+p.width*.045,mix(c.edge,c.core,.92,2.2),.48);
   }
   for(let j=0;j<count;j++){
    const phase=j*2.399,rows=g.map(h=>{
     const age=c.time-h.t,left=clamp(1-age/p.life),ax=axes(c,h.dir),open=smooth(.025,.4,age);
     const curl=Math.sin(h.arc*.65+j*1.7)*.24+Math.sin(h.arc*1.73+j)*.11;
     const spread=open*p.spread*(warm?.10:.035)*(1+age*.24),taper=Math.pow(left,1.2);
     const b=point(c,h.p,ax,0,Math.cos(phase+curl)*spread,Math.sin(phase+curl)*spread);
     return {p:b,side:c.add(c.mul(ax[1],Math.cos(phase)),c.mul(ax[2],Math.sin(phase))),s:h.arc+j*.93,w:(.15+.85*taper)*(warm?.8:1),a:Math.pow(left,warm?2.1:1.5),color:mix(c.edge,c.core,Math.exp(-age*2)*.5,1.15)};
    });
    ribbon(c,body,rows,p.width*(warm?.75:.16),c.edge,warm?.24:.24,warm);
   }
  }
  // Local head: compact white nucleus, asymmetric flame tongues, no closed shell.
  if(fade>1e-5){
   const len=.6+p.shellLength*(warm?1.25:.65),head=[];
   for(let i=0;i<=68;i++){const q=i/68;head.push({p:point(c,c.pos,a,.26-q*len,0,0),s:-q*len,w:Math.pow(Math.sin(Math.PI*q),.65)*Math.exp(-q*2.7),a:fade*(1-q)});}
   ribbon(c,soft,head,p.width*2.1,c.edge,p.glow*.12);
   ribbon(c,hot,head,p.width*.19,mix(c.core,[1,1,1],.55,2.1),.68);
   for(let j=0;j<count;j++){
    const rows=[],phase=j*2.399;
    for(let i=0;i<=60;i++){
     const q=i/60,reach=Math.sin(Math.PI*q)*p.spread*(warm?.21:.075),angle=phase+q*(warm?2.6:.5)+Math.sin(q*7-c.time*1.3+j)*q*.22;
     rows.push({p:point(c,c.pos,a,.17-q*len*(.55+rnd(j+7)*.45),Math.cos(angle)*reach,Math.sin(angle)*reach),side:c.add(c.mul(a[1],Math.cos(angle)),c.mul(a[2],Math.sin(angle))),s:-q*len+j*.83,w:Math.pow(Math.sin(Math.PI*q),.65)*(1-q*.65),a:fade*(1-q),color:mix(c.edge,c.core,Math.pow(1-q,4)*.8,1.25)});
    }
    ribbon(c,body,rows,p.width*(warm?.75:.3),c.edge,warm?.34:.22,warm);
   }
  }
  const sparks=fragments(c,embers,history,warm);
  return {opaque:[],alpha:[],additive:[{data:soft,mode:1},{data:body,mode:warm?31:30},{data:hot,mode:14},{data:embers,mode:1}],diagnostics:{revision:9,topology:warm?'open-turbulent-combustion-tongues-with-birth-anchored-fragments':'concentrated-rainbow-meteor-core-and-sparse-world-filaments',worldAnchored:true,modelsIncluded:false,historyGroups:groupsCount,sparks,localMotionFade:fade,noExternalChannels:true,parameterConsumers:{speed:'path length and material transport',life:'history lifetime',width:'ribbon width',strands:'flame or filament count and spark density',spread:'lateral flow and birth drift',shellLength:'local flame reach',glow:'soft layer',coreColor:'head and hot ridges',edgeColor:'tail and aged sparks'}}};
 }
 window.HuahongMeteor={build:(c,key)=>meteor(c,key==='meteor_flame'),
 presets:{
  meteor_prism:{title:'虹核流星 / V9',note:'集中白核与细长虹流，沿弧长向后输运；碎芒留在出生位置，转弯不拖动旧尾。',speed:11.6,life:3.8,width:.19,strands:6,spread:.65,shellLength:2.5,glow:.45,coreColor:'#e8faff',edgeColor:'#8274ff'},
  meteor_flame:{title:'燃尾流星 / V9',note:'错长开放火舌、轴向噪声侵蚀和脱落短芒；没有天幕、地形或实体陨石模型。',speed:10.8,life:2.9,width:.32,strands:8,spread:1.25,shellLength:3.8,glow:.50,coreColor:'#fff1c8',edgeColor:'#ff6535'}
 },shaderBranch:`
 if(uMode==30||uMode==31){
  float axis=vUV.y+uTime*(1.6+uFlowSpeed*.24);
  vec2 st=vec2(vUV.x*3.1,axis*.82);
  float warp=fbm(st*.53+vec2(0.,uTime*.12));
  float density=fbm(st+vec2(warp*1.8,0.));
  if(uMode==30){
   float fibers=pow(.5+.5*sin(vUV.x*19.+warp*5.-axis*1.6),2.);
   vec3 spectrum=.5+.5*cos(6.2831853*(vec3(.83,.93,1.07)*(axis*.055+density*.27)+vec3(.07,.31,.58)));
   c=mix(c,spectrum*1.6,.52);a*=pow(edge,1.8)*(.48+.52*fibers)*(.60+.40*density);
  }else{
   float tongues=smoothstep(.22,.68,density+edge*.21);
   float ridges=pow(.5+.5*sin(vUV.x*12.+warp*6.+axis*.37),3.);
   a*=pow(edge,1.35)*tongues*(.50+.50*ridges);
   c*=.7+density*.55+ridges*.25;
  }
 }
 `};
})();
