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
   let side=oriented&&r.side?r.side:c.cross(f,c.sub(c.eye,r.p));if(Math.hypot(...side)<1e-5)side=axes(c,f)[1];side=oriented?c.sub(side,c.mul(f,c.dot(side,f))):side;if(Math.hypot(...side)<1e-5)side=prev?c.sub(prev,c.mul(f,c.dot(prev,f))):axes(c,f)[1];side=c.norm(side);
   if(prev&&c.dot(prev,side)<0)side=c.mul(side,-1);prev=side;
   const normal=c.norm(c.cross(f,side)),alpha=opacity*(r.a??1)*(rows[0].luminousTip?1:smooth(0,cap,lengths[i]))*smooth(0,cap,total-lengths[i]);
   return Array.from({length:9},(_,j)=>{const u=j/4-1;return {p:c.add(c.add(r.p,c.mul(side,width*(r.w??1)*u)),c.mul(normal,oriented?width*(r.w??1)*.25*(1-u*u):0)),n:normal,uv:[u,r.s??lengths[i]],col:[...(r.color||color),alpha]};});
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
  // Curve local flame roots onto the actual latest world path.
  const newest=history.at(-1)||[];
  function headPoint(x,y,z){
   if(!warm||x>=0||newest.length<2)return point(c,c.pos,a,x,y,z);
   let remaining=-x,from=c.pos,frame=a;
   for(let k=newest.length-1;k>=0;k--){
    const h=newest[k],length=Math.hypot(...c.sub(from,h.p));
    if(length<1e-7)continue;
    if(remaining<=length){const t=remaining/length,center=c.add(from,c.mul(c.sub(h.p,from),t));const F=c.norm(c.sub(from,h.p));frame=axes(c,F);return point(c,center,frame,0,y,z);}
    remaining-=length;from=h.p;frame=axes(c,h.dir);
   }
   return point(c,from,frame,-remaining,y,z);
  }
  // Local head: compact white nucleus, asymmetric flame tongues, no closed shell.
  if(fade>1e-5){
   const len=.6+p.shellLength*(warm?1.25:.65),head=[];
   for(let i=0;i<=68;i++){const q=i/68;head.push({p:headPoint(.26-q*len,0,0),s:-q*len,luminousTip:!warm&&i===0,w:Math.max(warm?0:.012,Math.pow(Math.sin(Math.PI*q),.65)*Math.exp(-q*2.7)),a:fade*(1-q)});}
   ribbon(c,soft,head,p.width*2.1,c.edge,p.glow*.12);
   ribbon(c,hot,head,p.width*.19,mix(c.core,[1,1,1],.55,2.1),.68);
   for(let j=0;j<count;j++){
    const rows=[],phase=j*2.399;
    for(let i=0;i<=60;i++){
     const q=i/60,reach=Math.sin(Math.PI*q)*p.spread*(warm?.21:.075),angle=phase+q*(warm?2.6:.5)+Math.sin(q*7-c.time*1.3+j)*q*.22;
     rows.push({p:headPoint(.17-q*len*(.55+rnd(j+7)*.45),Math.cos(angle)*reach,Math.sin(angle)*reach),side:c.add(c.mul(a[1],Math.cos(angle)),c.mul(a[2],Math.sin(angle))),s:-q*len+j*.83,w:Math.pow(Math.sin(Math.PI*q),.65)*(1-q*.65)*(warm?.85+.15*Math.sin(q*11-c.time*.7+j):1),a:fade*(1-q),color:mix(c.edge,c.core,Math.pow(1-q,4)*.8,1.25)});
    }
    ribbon(c,body,rows,p.width*(warm?.75:.3),c.edge,warm?.34:.22,warm);
   }
  }
  // Retain the original full head; extend its soft emission over the nose.
  // The ellipse overlaps the old head instead of substituting a thin spine.
  const noseGlow=[];
  if(!warm&&fade>1e-5){
   const center=point(c,c.pos,a,.14,0,0),eye=c.norm(c.sub(c.eye,center));
   let along=c.sub(a[0],c.mul(eye,c.dot(a[0],eye)));
   if(Math.hypot(...along)<1e-5)along=c.cross(eye,Math.abs(eye[1])>.95?[0,0,1]:[0,1,0]);
   along=c.norm(along);const across=c.norm(c.cross(eye,along));
   const length=.46+p.width*.85,width=.075+p.width*.30;
   const corners=[[-1,-1],[1,-1],[1,1],[-1,1]];
   for(const k of [0,1,2,0,2,3]){const uv=corners[k];const pos=c.add(center,c.add(c.mul(along,uv[0]*length),c.mul(across,uv[1]*width)));c.vertex(noseGlow,pos,[...mix(c.edge,c.core,.80,1.8),fade*(.10+p.glow*.18)],uv,eye);}
  }
  const sparks=fragments(c,embers,history,warm);
  return {opaque:[],alpha:[],additive:[{data:soft,mode:1},{data:body,mode:warm?31:30},{data:hot,mode:14},{data:noseGlow,mode:9},{data:embers,mode:1}],diagnostics:{revision:9,topology:warm?'open-turbulent-combustion-tongues-with-birth-anchored-fragments':'concentrated-rainbow-meteor-core-and-sparse-world-filaments',worldAnchored:true,modelsIncluded:false,historyGroups:groupsCount,sparks,localMotionFade:fade,noExternalChannels:true,curvedFlameRoot:warm,camberedStrips:warm,parameterConsumers:{speed:'path length and material transport',life:'history lifetime',width:'ribbon width',strands:'flame or filament count and spark density',spread:'lateral flow and birth drift',shellLength:'local flame reach',glow:'soft layer',coreColor:'head and hot ridges',edgeColor:'tail and aged sparks'}}};
 }

 // Reference-driven V10: a point nucleus opens into broad cyan layered wake.
 // This is world-path geometry; it does not reproduce the reference's polar screen warp.



 function dense(c){
  const p=c.params,surface=[],nucleus=[],history=groups(c);let groupCount=0;
  for(const g of history){
   if(g.length<3)continue;groupCount++;
   // Connected luminous body. Its width opens behind the head, then fades in density.
   for(let layer=0;layer<Math.max(1,Math.min(4,Math.round(p.strands/3)));layer++){
    const rows=g.map(h=>{
     const age=c.time-h.t,q=clamp(age/p.life),a=axes(c,h.dir);
     const breadth=(.006+Math.pow(q,.78)*p.spread*3.5)*(1-smooth(.88,1,q)*.12);
     const bend=(Math.sin(h.arc*.39+layer*.8)*.08+Math.sin(h.arc*.93)*.035)*q;
     return {p:point(c,h.p,a,0,bend,layer===0?0:(layer-1.5)*breadth*.14),s:q,
      w:breadth*(layer===0?1:.82),a:Math.pow(1-smooth(.64,1,q),1.2),color:c.edge};
    });
    ribbon(c,surface,rows,p.width,c.edge,(layer===0?.30:.05)*(.5+p.glow*.8));
   }
  }
  if(c.motionFade>1e-5){
   const eye=c.norm(c.sub(c.eye,c.pos)),r=c.norm(c.cross(eye,Math.abs(eye[1])>.96?[0,0,1]:[0,1,0])),u=c.norm(c.cross(r,eye));
   const radius=.12+p.shellLength*.045+p.width*.10,points=[[-1,-1],[1,-1],[1,1],[-1,1]];
   for(const k of [0,1,2,0,2,3]){const v=points[k];c.vertex(nucleus,c.add(c.pos,c.add(c.mul(r,v[0]*radius),c.mul(u,v[1]*radius))),[...c.core,c.motionFade],[v[0],v[1]],eye);}
  }
  return {opaque:[],alpha:[],additive:[{data:surface,mode:34},{data:nucleus,mode:35}],diagnostics:{revision:11,worldAnchored:true,modelsIncluded:false,historyGroups:groupCount,topology:'continuous-dense-blue-white-flow-with-chromatic-ridges',localMotionFade:c.motionFade}};
 }

 window.HuahongMeteor={build:(c,key)=>key==='meteor_dense'?dense(c):meteor(c,key==='meteor_flame'),
 presets:{
  meteor_dense:{title:'蓝白虹流 / V13 源码材质重做',note:'亮核、云状主流与虹色细筋分布在真实三维截面内；旋转和追尾观察内部厚度。',speed:8.2,life:2.2,width:.52,strands:8,spread:1.25,shellLength:3.5,glow:.62,coreColor:'#e6fbff',edgeColor:'#398eff'},
  meteor_prism:{title:'虹核流星 / V9',note:'集中白核与细长虹流，沿弧长向后输运；碎芒留在出生位置，转弯不拖动旧尾。',speed:11.6,life:3.8,width:.19,strands:6,spread:.65,shellLength:2.5,glow:.45,coreColor:'#e8faff',edgeColor:'#8274ff'},
  meteor_flame:{title:'燃尾流星 / V9',note:'错长开放火舌、轴向噪声侵蚀和脱落短芒；没有天幕、地形或实体陨石模型。',speed:10.8,life:2.9,width:.32,strands:8,spread:1.25,shellLength:3.8,glow:.50,coreColor:'#fff1c8',edgeColor:'#ff6535'}
 },shaderFunctions:`
// LOCAL USER-SUPPLIED Meteor Test111 adaptation; not for public MIT release.
float hhSmooth(float a,float b,float x){float q=clamp((x-a)/(b-a),0.,1.);return q*q*(3.-2.*q);}
// colors
const vec3 C_BLACK   = vec3(0.);
const vec3 C_WHITE   = vec3(1.);
const vec3 C_BLUE    = vec3(.1, .6, 1.);
const vec3 C_CYAN    = vec3(.4, .8, 1.);
const vec3 C_PURPLE  = vec3(.6, .1, .9);
const vec3 C_YELLOW  = vec3(1., .82, .2);
const vec3 C_ORANGE  = vec3(1., .8, .4);
const vec3 C_BG      = vec3(.2, .23, .33);
const vec3 C_GRAY    = vec3(.5);

// hh_palette constants
const vec3 P_A = vec3(.5);
const vec3 P_B = vec3(.5);
const vec3 P_C = vec3(1.);
const vec3 P_D = vec3(0., .33, .67);

// ---------- noise ----------
vec3 hh_hash33(vec3 p) {
    p = vec3(
        dot(p, vec3(127.1, 311.7,  74.7)),
        dot(p, vec3(269.5, 183.3, 246.1)),
        dot(p, vec3(113.5, 271.9, 124.6))
    );
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float hh_hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float hh_perlin3D(vec3 p) {
    vec3 pi = floor(p), pf = p - pi,
    w = pf*pf*(3. - 2.*pf);
    vec2 e = vec2(1,0);

    return mix(
        mix(
            mix(dot(hh_hash33(pi), pf),
                dot(hh_hash33(pi + e.xyy), pf - e.xyy), w.x),
            mix(dot(hh_hash33(pi + e.yxy), pf - e.yxy),
                dot(hh_hash33(pi + e.xxy), pf - e.xxy), w.x),
            w.y
        ),
        mix(
            mix(dot(hh_hash33(pi + e.yyx), pf - e.yyx),
                dot(hh_hash33(pi + e.xyx), pf - e.xyx), w.x),
            mix(dot(hh_hash33(pi + e.yxx), pf - e.yxx),
                dot(hh_hash33(pi + e.xxx), pf - e.xxx), w.x),
            w.y
        ),
        w.z
    );
}

float hh_fbm3D(vec3 p) {
    float v = 0., a = .5;
    for (int i = 0; i < 3; i++) {
        v += a * hh_perlin3D(p);
        p *= 2.;
        a *= .5;
    }
    return v*.5 + .5;
}

float hh_renderMeteorLayer(vec2 uv, float timeOffset, float warpIntensity) {
    uv *= vec2(8., 1.);
    uv.y -= uTime*3. + timeOffset;

    vec3 p = vec3(uv, uTime*2.5);
    vec2 w = vec2(hh_fbm3D(p + 1.), hh_fbm3D(p - 1.)) - .5;
    float n = hh_fbm3D(p + vec3(w * warpIntensity, 0.));

    return clamp((n - .5)*3. + .35, 0., 1.);
}

// https://iquilezles.org/articles/palettes/
vec3 hh_palette(float t) {
    return P_A + P_B * cos(2. * 3.1415926535 * (P_C * t + P_D));
}

vec3 hh_renderLightning(vec2 uv, float timeOffset, vec3 glowColor) {
    float turbulence = hh_fbm3D(vec3(uv.y * 2., 0., uTime * 5. + timeOffset));
    float d = abs(uv.x + (turbulence - .5)*2.);

    vec3 c = C_GRAY * (.002 / (d + 1e-3)) + glowColor * (.02 / (d + 1e-3));
    return c * hhSmooth(1., .3, abs(uv.y));
}


`,shaderBranch:`



 if(uMode==34){
  float q=clamp(vUV.y,0.,1.);
  vec2 uv=vec2(vUV.x*.235,sqrt(max(.0001,q))*.94);
  uv.x+=sin(3.14159265*(hh_perlin3D(vec3(uv*vec2(3.,2.)-vec2(0.,uTime*5.),1.3))-.5-uv.y))*.025;
  float maskNoise=hh_fbm3D(vec3(uv*vec2(30.,10.)-vec2(0.,uTime*4.),uTime*5.));
  float mask=hhSmooth(.25,0.,abs(uv.x)+(maskNoise-.5)*pow(uv.y,5.)*.1);
  vec3 base=vec3(.4,.8,1.)*hh_renderMeteorLayer(uv,0.,1.5);
  vec2 colorUV=uv*vec2(15.,5.);colorUV.y-=uTime*4.;
  float raw=hh_fbm3D(vec3(colorUV,uTime*3.));
  float sharp=clamp((raw-.5)*50.+.5,0.,1.);
  vec3 neon=mix(vec3(.4,.8,1.),hh_palette(raw*2.-uTime),hhSmooth(.25,.55,sharp));
  vec2 coreUV=uv*vec2(30.,10.);coreUV.y-=uTime*5.;
  coreUV.x+=(maskNoise-.5)*pow(uv.y,10.)*5.;
  vec3 pp=vec3(coreUV,uTime*4.);
  float rawCore=hh_fbm3D(pp+vec3(hh_fbm3D(pp)*2.5,0.,0.));
  vec3 bright=vec3(2.)*hhSmooth(.55,.75,rawCore)+vec3(.4,.8,1.)*.5*hhSmooth(.3,.65,rawCore);
  bright+=.5*hhSmooth(0.,.3,rawCore)*hhSmooth(.1,0.,abs(uv.x))*hhSmooth(1.,0.,uv.y);
  c=(base*.8+neon*.2+bright+hh_renderLightning(uv,0.,vec3(.6,.1,.9))+hh_renderLightning(uv,11.4,vec3(.1,.6,1.)))*.85;
  a*=mask;
 }
 if(uMode==35){
  float r2=dot(vUV,vUV);
  float cross=exp(-abs(vUV.x)*65.-abs(vUV.y)*7.)+exp(-abs(vUV.y)*65.-abs(vUV.x)*7.);
  float core=exp(-r2*155.);
  c=vec3(.25,.68,1.)*(cross*.8+exp(-r2*24.)*.14)+vec3(.8,.96,1.)*core*3.;
  c*=vec3(.5)+vColor.rgb*.5;a*=1.-smoothstep(.75,1.,sqrt(r2));
 }
 if(uMode==32||uMode==33){
  float axis=vUV.y+uTime*(1.6+uFlowSpeed*.22);
  vec2 st=vec2(vUV.x*2.8,axis*.7);
  float broad=fbm(st*.58+vec2(0.,uTime*.18));
  float n=fbm(st+vec2((broad-.5)*2.4,0.));
  float ridges=pow(max(0.,1.-abs(n-.5)*5.5),3.);
  if(uMode==32){a*=pow(edge,1.8)*smoothstep(.25,.68,n)*(.35+.65*ridges);c*=.70+ridges*.7;}
  else {a*=pow(edge,2.3)*(.55+.45*sin(axis*1.7+broad*4.));}
 }
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
