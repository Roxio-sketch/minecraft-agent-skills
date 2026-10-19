/* V4 original fire study: open turbulent membranes, absorbing char and detached combustion.
 * No model / video-derived texture / fluid solver. Load after primary-forms.js.
 * The host inserts shaderFunctions before main and shaderBranch inside main (mode 12).
 */
(() => {
  'use strict';
  const PI = Math.PI, TAU = PI * 2;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const hash = n => { const v = Math.sin(n * 127.13 + 23.43) * 43758.5453; return v - Math.floor(v); };
  const mix = (a, b, q) => a.map((v, i) => v * (1-q) + b[i] * q);
  const gain = (c, g) => c.map(v => v * g);
  const saturated = (c, power) => { const high = Math.max(...c, .001); return c.map(v => Math.pow(v / high, power) * high); };
  const smooth = (a, b, x) => { const q = clamp((x-a)/(b-a),0,1); return q*q*(3-2*q); };
  const localPoint = (ctx, o, b, x, y, z) => ctx.add(o, ctx.add(ctx.mul(b[0],x), ctx.add(ctx.mul(b[1],y),ctx.mul(b[2],z))));
  function basis(ctx,d) {
    const f=ctx.norm(d),s=ctx.norm(ctx.cross(f,Math.abs(f[1])>.96?[0,0,1]:[0,1,0]));
    return [f,s,ctx.norm(ctx.cross(s,f))];
  }

  // The transverse grid is real geometry: curled lips and ragged width do not rely on
  // hiding a smooth long strip behind a bright shader. Every sheet is open at its rear.
  function membrane(ctx,out,rows,columns,surface,tint,opacity) {
    const grid=[];
    for(let i=0;i<=rows;i++) {
      const q=i/rows,line=[];
      for(let j=0;j<=columns;j++) {
        const s=j/columns*2-1,v=surface(q,s);
        line.push({p:v.p,c:[...(v.color||tint),opacity*(v.alpha??1)],uv:[s,q],n:v.n||[0,1,0]});
      }
      grid.push(line);
    }
    for(let i=1;i<=rows;i++)for(let j=1;j<=columns;j++) {
      const a=grid[i-1][j-1],b=grid[i-1][j],c=grid[i][j],d=grid[i][j-1];
      const n=ctx.norm(ctx.cross(ctx.sub(b.p,a.p),ctx.sub(d.p,a.p)));
      for(const v of [a,b,c,a,c,d])ctx.vertex(out,v.p,v.c,v.uv,n);
    }
  }

  function ribbon(ctx,out,points,width,tint,opacity) {
    if(points.length<2)return;
    let distance=0;
    const source=points.map((v,i)=>{
      if(i)distance+=Math.hypot(...ctx.sub(v.p,points[i-1].p));
      return {...v,arc:distance,uv:i/(points.length-1)};
    });
    if(distance<1e-8)return;
    // Add short cap shoulders before fading, including on two-point ember streaks.
    // The middle 87% or more keeps its original width and opacity.
    const cap=Math.min(distance*.065,Math.max(width*.85,distance*.018));
    const sampleAt=arc=>{
      let i=1;while(i<source.length-1&&source[i].arc<arc)i++;
      const a=source[i-1],b=source[i],q=clamp((arc-a.arc)/Math.max(1e-8,b.arc-a.arc),0,1);
      return {p:ctx.add(ctx.mul(a.p,1-q),ctx.mul(b.p,q)),arc,uv:a.uv*(1-q)+b.uv*q,
        w:(a.w??1)*(1-q)+(b.w??1)*q,a:(a.a??1)*(1-q)+(b.a??1)*q,
        color:mix(a.color||tint,b.color||tint,q)};
    };
    const samples=source.concat([cap*.45,cap,distance-cap,distance-cap*.45].map(sampleAt))
      .sort((a,b)=>a.arc-b.arc).filter((v,i,a)=>!i||v.arc-a[i-1].arc>1e-8);
    let previous=null;
    const rows=samples.map((v,i)=>{
      const d=ctx.norm(ctx.sub(samples[Math.min(samples.length-1,i+1)].p,samples[Math.max(0,i-1)].p));
      let s=ctx.norm(ctx.cross(d,ctx.sub(ctx.eye,v.p)));
      if(previous&&ctx.dot(s,previous)<0)s=ctx.mul(s,-1);previous=s;
      const endFade=smooth(0,cap,v.arc)*smooth(0,cap,distance-v.arc),w=width*(v.w??1)*Math.sqrt(endFade);
      return {a:ctx.add(v.p,ctx.mul(s,-w)),b:ctx.add(v.p,ctx.mul(s,w)),
        c:[...(v.color||tint),opacity*(v.a??1)*endFade],uv:v.uv,n:ctx.norm(ctx.cross(d,s))};
    });
    for(let i=1;i<rows.length;i++)for(const [r,s] of [[rows[i-1],-1],[rows[i-1],1],[rows[i],1],[rows[i-1],-1],[rows[i],1],[rows[i],-1]])
      ctx.vertex(out,s<0?r.a:r.b,r.c,[s,r.uv],r.n);
  }

  function sprite(ctx,out,p,sx,sy,tint,opacity,rotation=0) {
    const n=ctx.norm(ctx.sub(ctx.eye,p)),r=ctx.norm(ctx.cross(Math.abs(n[1])>.98?[0,0,1]:[0,1,0],n)),u=ctx.norm(ctx.cross(n,r));
    const x=ctx.mul(ctx.add(ctx.mul(r,Math.cos(rotation)),ctx.mul(u,Math.sin(rotation))),sx);
    const y=ctx.mul(ctx.add(ctx.mul(u,Math.cos(rotation)),ctx.mul(r,-Math.sin(rotation))),sy);
    const corners=[ctx.sub(ctx.sub(p,x),y),ctx.add(ctx.sub(p,y),x),ctx.add(ctx.add(p,x),y),ctx.add(ctx.sub(p,x),y)],uv=[[-1,-1],[1,-1],[1,1],[-1,1]];
    for(const i of [0,1,2,0,2,3])ctx.vertex(out,corners[i],[...tint,opacity],uv[i],n);
  }

  function detachedBirths(ctx,life) {
    const store=ctx.state.flameV4Births||(ctx.state.flameV4Births={});
    const seen=new Set();
    for(const h of ctx.history||[]) {
      if(ctx.time-h.t>life)continue;
      const bucket=Math.floor((h.t+1e-8)/.072),key=h.seg+':'+bucket;seen.add(key);
      if(!store[key])store[key]={p:h.p.slice(),t:h.t,b:basis(ctx,h.dir),speed:ctx.params.speed,
        seed:hash(bucket*31.3+h.seg*97.1+ctx.seed),bucket,seg:h.seg};
    }
    const live=[];
    for(const key of Object.keys(store)) {
      const b=store[key],age=ctx.time-b.t;
      if(age>=0&&age<life)live.push(b);
      if(!seen.has(key)&&age>=life)delete store[key];
    }
    return live;
  }

  function flame(ctx) {
    const p=ctx.params,axes=basis(ctx,ctx.dir),time=ctx.time;
    const parts={opaque:[],alpha:[],additive:[],diagnostics:{}};
    const dim=[],burn=[],glow=[],core=[],sparks=[];
    const scarlet=saturated(ctx.edge,3.6);
    // Keep the temperature hierarchy orange/yellow rather than pink-white. The core
    // colour controls the warm islands, with blue restrained in this fire material.
    const yellow=[ctx.core[0]*2.35,ctx.core[1]*1.18,ctx.core[2]*.075];
    const orange=mix(gain(scarlet,1.25),yellow,.24);
    const localFade=Math.pow(ctx.motionFade,3.4)*smooth(.02,.45,p.speed);
    const length=(1.22+p.shellLength*.43)*(.92+p.speed*.012);
    const radius=(.29+p.width*.90)*(.65+p.spread*.45);
    const amount=clamp(Math.round(p.strands*1.9),10,28);
    const W=(x,y,z)=>localPoint(ctx,ctx.pos,axes,x,y,z);
    let tongueCount=0,coalPackets=0;

    if(localFade>.0001) {
      // Uneven layered combustion mass, intentionally much shorter and wider than the
      // feather form. Low-valued alpha carries dark red cavities between emitting lips.
      for(let j=0;j<6;j++) {
        const seed=ctx.seed+j*8.31,phase=j*2.399+hash(seed)*.45;
        const radial=ctx.add(ctx.mul(axes[1],Math.cos(phase)),ctx.mul(axes[2],Math.sin(phase)));
        const transverse=ctx.add(ctx.mul(axes[1],-Math.sin(phase)),ctx.mul(axes[2],Math.cos(phase)));
        const r=radius*(.05+.25*hash(seed+3)),L=length*(.32+.24*hash(seed+7));
        const sheet=(q,s)=>{
          const bell=Math.pow(Math.max(0,Math.sin(PI*q)),.53),flutter=ctx.noise1(q*5.5-time*1.7+seed,192);
          const notch=.76+.22*ctx.noise1(q*12.8-time*2.6+seed,193);
          const width=radius*(.31+.26*hash(seed+1))*bell*notch;
          const fold=Math.sin(s*2.8+q*7.2-time*1.65+phase)*radius*.12*bell;
          const bulge=r+flutter*radius*.08*bell+q*radius*.13;
          const center=W(.42-hash(seed+8)*length*.3-q*L,Math.cos(phase)*bulge,Math.sin(phase)*bulge+Math.sin(q*4.8+phase)*radius*.11*bell);
          const at=ctx.add(center,ctx.add(ctx.mul(transverse,s*width),ctx.mul(radial,fold)));
          return {p:at,alpha:localFade*smooth(0,.08,q)*smooth(1,.54,q)*(.8+.2*flutter),
            color:gain(mix(scarlet,orange,(1-q)*.20),.36)};
        };
        const body=[];membrane(ctx,body,36,8,sheet,scarlet,.20);
        parts.alpha.push({data:body,mode:12,center:W(.27-L*.5,0,0)});
        // A smaller contribution reuses the actual wrinkles as hot patches, without
        // turning every interior layer into a white light source.
        membrane(ctx,burn,32,6,(q,s)=>{
          const a=sheet(q,s);a.color=gain(orange,.88);a.alpha*=.58;return a;
        },orange,.085*p.glow);
      }

      // Three unequal dominant tongues carry the silhouette. The remaining shards are
      // short and dark: they cannot trace another ring around a smooth ellipsoid.
      for(let j=0;j<amount;j++) {
        const primary=j<3,seed=j*19.77+ctx.seed;
        const angle=primary?[.38,3.02,4.85][j]:(hash(seed)*2.1-.6);
        const R=ctx.add(ctx.mul(axes[1],Math.cos(angle)),ctx.mul(axes[2],Math.sin(angle)));
        const T=ctx.add(ctx.mul(axes[1],-Math.sin(angle)),ctx.mul(axes[2],Math.cos(angle)));
        const start=primary?[-.06,-.32,-.78][j]:-length*(.12+.57*hash(seed+1));
        const L=primary?length*[1.12,.69,.37][j]:length*(.09+.17*hash(seed+2));
        const width=radius*(primary?[.67,.95,.32][j]:(.12+.17*hash(seed+4)));
        const rootRadius=radius*(primary?[.11,.18,.05][j]:(.12+.37*hash(seed+5)));
        const curl=radius*(primary?[.37,.17,.61][j]:(.08+.13*hash(seed+6)));
        const rows=40,columns=6;
        const surface=(q,s)=>{
          const bell=Math.pow(Math.max(0,Math.sin(PI*q)),.64);
          const low=ctx.noise1(q*4.8-time*2.1+seed,194),fine=ctx.noise1(q*14-time*3.6+seed,195);
          // No return to the axis at the tip: each rear endpoint stays on a different
          // offset and height. This breaks the closed orange leaf seen in first V4.
          const roll=Math.sin(q*PI*.93)*curl;
          const radial=rootRadius+roll+q*radius*(primary?[.42,-.14,.73][j]:.15)+low*radius*.10*bell;
          const c=W(.43+start-q*L,Math.cos(angle)*radial+q*q*radius*(primary?[.12,-.43,.23][j]:0),
            Math.sin(angle)*radial+q*q*radius*(primary?[.18,-.38,.76][j]:.08));
          const taper=Math.pow(1-q,.43)*smooth(0,.055,q),lip=Math.sin(s*PI*.8+q*4.5-time*2.0+angle);
          const w=width*taper*(.7+.3*bell)*(.85+.10*fine+.12*Math.sin(q*13+seed));
          const depth=(lip*width*.32+(s*s)*width*.28)*bell;
          // Erode continuously at vertices instead of dropping every cell touching a
          // Boolean cut; that enlarged tiny notches into rectangular missing plates.
          const tear=primary?(j===0?smooth(.56,.61,q)*(1-smooth(.65,.70,q))*smooth(-.10,.22,s):
            j===1?smooth(.36,.42,q)*(1-smooth(.46,.51,q))*(1-smooth(-.35,.02,s)):0):0;
          return {p:ctx.add(c,ctx.add(ctx.mul(T,s*w),ctx.mul(R,depth))),
            alpha:localFade*smooth(0,.04,q)*Math.pow(1-q,.63)*(1-tear),
            color:gain(mix(primary?orange:scarlet,scarlet,Math.pow(q,.8)*.84),primary?.92:.38)};
        };
        membrane(ctx,burn,rows,columns,surface,orange,(primary?.33:.10)*p.glow);
        // A dark lower lip gives the curl weight; highlights occur on only some sheets.
        if(j%3===0)membrane(ctx,dim,30,4,(q,s)=>{
          const a=surface(q,s);a.color=gain(scarlet,.16);a.alpha*=.76;return a;
        },scarlet,.20);
        if(j===1) {
          const hotRows=[];
          for(let k=0;k<=24;k++) {
            const q=k/24*.58,s=.52+Math.sin(q*8+angle)*.12,a=surface(q,s);
            hotRows.push({p:a.p,w:Math.pow(Math.sin(PI*k/24),.7),a:localFade*(1-q)*.63,
              color:gain(mix(yellow,orange,q),1.35)});
          }
          ribbon(ctx,core,hotRows,p.width*.065,yellow,.16); // isolated curled lip, never a web of veins
        }
        tongueCount++;
      }

      // A few broad bright islands sit inside the front half, opening into orange
      // tongues and cooling to a thin red edge. No repeated little yellow wire veins.
      for(let j=0;j<3;j++) {
        const seed=j*5.37+ctx.seed,angle=.42+j*.73,x=.28-j*.42;
        const rad=radius*(.035+.08*hash(seed+2)),flicker=.80+.20*ctx.noise1(time*6+j,196);
        const center=W(x,Math.cos(angle)*rad,Math.sin(angle)*rad);
        const tiny=[];
        const radial=ctx.add(ctx.mul(axes[1],Math.cos(angle)),ctx.mul(axes[2],Math.sin(angle)));
        const tangent=ctx.add(ctx.mul(axes[1],-Math.sin(angle)),ctx.mul(axes[2],Math.cos(angle)));
        membrane(ctx,burn,30,8,(q,s)=>{
          const bell=Math.pow(Math.max(0,Math.sin(PI*q)),.64),L=[.66,.93,.38][j];
          const w=radius*[.36,.21,.12][j]*bell*Math.pow(1-q,.35);
          const curl=Math.sin(q*PI*1.5+angle)*radius*.12*bell;
          const pos=localPoint(ctx,center,axes,-q*L,0,q*q*radius*.14);
          const wrinkle=ctx.noise1(q*6-time*2.8+seed,197)*radius*.035*bell;
          return {p:ctx.add(pos,ctx.add(ctx.mul(tangent,s*w),ctx.mul(radial,curl+s*s*w*.3+wrinkle))),
            color:mix(j<2?yellow:orange,orange,smooth(.12,.90,q)),
            alpha:localFade*flicker*Math.pow(1-q,.55)*smooth(0,.06,q)};
        },yellow,j<2?.36:.16);
        sprite(ctx,tiny,center,.13+hash(seed+7)*.12,.10+hash(seed+8)*.07,gain(scarlet,.16),localFade*.09,angle);
        parts.alpha.push({data:tiny,mode:8,center,variant:j%4});coalPackets++;
      }

      // A deliberately restrained red aura. Bloom should carry the fire, not erase it.
      const aura=[];for(let k=0;k<=28;k++){
        const q=k/28;aura.push({p:W(.49-q*length*.94,0,0),w:Math.pow(Math.sin(PI*q),.65),a:localFade*(1-q)});
      }
      ribbon(ctx,glow,aura,radius*1.04,gain(scarlet,.65),p.glow*.09);
    }

    // Cached births never change anchor when the emitter moves or turns. The wake is
    // sparse, short combustion and soot rather than a second long luminous cable.
    const birthLife=Math.min(p.life,1.55),births=detachedBirths(ctx,birthLife);
    let detachedShreds=0,smokeCards=0,emberCount=0;
    for(const b of births) {
      const age=time-b.t,fade=Math.pow(clamp(1-age/birthLife,0,1),2.25);
      const phase=b.seed*TAU;
      if(age>.11&&age<.85) {
        const burnFade=Math.pow(1-age/.85,1.7),drift=.12+age*.36;
        const center=localPoint(ctx,b.p,b.b,-age*b.speed*.034,Math.cos(phase)*radius*drift,Math.sin(phase)*radius*.26+age*.26);
        const points=[];
        for(let k=0;k<=22;k++) {
          const q=k/22;points.push({p:localPoint(ctx,center,b.b,-q*(.17+.48*burnFade),
            Math.sin(q*4.9+phase+age*3)*q*.13,q*q*(.13+age*.13)),
            w:Math.pow(Math.sin(PI*q),.7)*(.8-.45*q),a:burnFade*(1-q)*.7,
            color:gain(mix(orange,scarlet,q),.9)});
        }
        ribbon(ctx,burn,points,p.width*(.14+.17*burnFade),orange,.12*p.glow);detachedShreds++;
      }
      if(age>.13&&age<1.1&&b.bucket%2===0) {
        const q=age/1.1,center=localPoint(ctx,b.p,b.b,-age*b.speed*.016,
          Math.cos(phase)*radius*(.2+age*.3),Math.sin(phase)*radius*.35+age*.31);
        const soot=[],a=Math.sin(PI*q)*Math.pow(1-q,1.2)*.12;
        sprite(ctx,soot,center,radius*(.24+q*.38),radius*(.17+q*.31),[.019,.007,.004],a,phase+age*.2);
        parts.alpha.push({data:soot,mode:8,center,variant:b.bucket%4});smokeCards++;
      }
      // Two or three sharp ash sparks per birth, with gravity and outward release.
      if(age>.09)for(let j=0;j<2+(b.bucket%3===0?1:0);j++) {
        const s=phase+j*2.399,v=.22+.34*hash(b.bucket*3+j);
        const anchor=localPoint(ctx,b.p,b.b,-age*b.speed*.032,Math.cos(s)*(.07+age*v),
          Math.sin(s)*(.07+age*v)+age*.23-age*age*.13);
        const prev=localPoint(ctx,anchor,b.b,.024+hash(j+b.bucket)*.035,
          -Math.cos(s)*.014,-Math.sin(s)*.014+.008);
        ribbon(ctx,sparks,[{p:prev,w:.35,a:fade*.65},{p:anchor,w:1,a:fade}],.0025+p.width*.004,
          gain(mix(orange,yellow,hash(b.bucket+j)),1.8),.42);
        emberCount++;
      }
    }
    // Alpha density first, thin emitting surfaces afterwards. There is deliberately no
    // opaque packet and no triangles sealing the fire into a rigid solid shell.
    if(dim.length)parts.alpha.push({data:dim,mode:12,center:W(-length*.42,0,0)});
    parts.additive.push({data:glow,mode:1},{data:burn,mode:12},{data:core,mode:1},{data:sparks,mode:1});
    parts.diagnostics={topology:'short-wide-burning-mass-with-open-rolled-flame-membranes',
      localTongues:tongueCount,dominantUnequalTongues:localFade>.0001?3:0,layeredCombustionSheets:localFade>.0001?6:0,hotIslands:coalPackets,
      detachedShreds,smokeCards,detachedEmbers:emberCount,localMotionFade:localFade,
      burnLength:length,burnRadius:radius,closedShell:false,modelsIncluded:false,worldAnchored:true,
      birthPositionsImmutable:true,birthOrientationCached:true,shaderMode:12,hdrEmission:true,
      parameterConsumers:{speed:'history spacing / compact combustion stretch',life:'detached residual age cap',
        width:'real sheet width / thermal island thickness',strands:'independent rolled tongues',
        spread:'combustion radial envelope',shellLength:'compact local body and tongue reach',
        glow:'hot membrane energy / restrained aura',coreColor:'small high temperature islands',edgeColor:'char and flame palette'},
      limitations:['layered open surfaces and alpha soot, no fluid simulation','no actual volumetric integration or dynamic scene lighting']};
    return parts;
  }

  const shaderFunctions = `
// Anisotropic advected combustion. Coarse billows control density; fine ridges only
// decorate their lips. Local UV y is normalized from hot root to cooling rear.
float flameV4Density(vec2 p,float t){
 vec2 adv=vec2(p.x*2.3,p.y*5.2-t*1.17);
 vec2 w=flow(adv*.68,t)*1.45;
 float billow=fbm(adv+w);
 float shred=fbm(adv*2.9-w*1.7+vec2(t*.27,-t*.72));
 return billow*.76+shred*.24;
}
`;
  const shaderBranch = `
 if(uMode==12){
  float rear=clamp(vUV.y,0.,1.);
  vec2 p=vec2(vUV.x,rear)+vec2(vWorld.x*.037+vWorld.z*.043,vWorld.y*.047);
  float n=flameV4Density(p,uTime);
  float detail=fbm(p*vec2(13.,24.)+flow(p*3.4,uTime)*2.-vec2(0.,uTime*3.2));
  float silhouette=pow(max(0.,edge),.44);
  float holes=smoothstep(.23+rear*.17,.47+rear*.16,n+edge*.11+detail*.075);
  float root=smoothstep(0.,.035,rear)*(1.-smoothstep(.86,1.,rear));
  float pocket=smoothstep(.36,.67,n+detail*.09);
  float flicker=.84+.16*sin(uTime*13.7+vWorld.y*3.7+vWorld.x*2.1);
  if(uAdditive==1){
   float heat=(.29+.86*pocket)*(1.-rear*.39);
   a*=silhouette*holes*root*heat*flicker;
   c*=.80+pocket*.46;
  }else{
   a*=silhouette*root*(.35+.65*holes);
   c*=.10+smoothstep(.39,.70,n)*.72;
  }
 }
`;
  window.DunguangFlameV4={shaderFunctions,shaderBranch,flame};
  window.DunguangPrimaryForms=window.DunguangPrimaryForms||{};
  window.DunguangPrimaryForms.flame=flame;
})();
