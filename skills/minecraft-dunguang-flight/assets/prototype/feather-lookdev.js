/* V4 feather look development. Original program geometry, no scene or carrier models.
 * A small nose, broad broken membranes and sparse nested forks replace V3's line brush.
 * History centres and transverse offsets are evaluated from each stored birth frame. */
(() => {
  'use strict';
  const TAU=Math.PI*2,clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const lerp3=(a,b,t)=>a.map((x,i)=>x*(1-t)+b[i]*t);
  const gain=(a,g)=>a.map(x=>x*g);
  const hash=n=>{const a=Math.sin(n*137.71+91.13)*43758.5453;return a-Math.floor(a);};
  const smooth=(a,b,x)=>{const t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);};
  function axes(ctx,forward){const f=ctx.norm(forward),ref=Math.abs(ctx.dot(f,[0,1,0]))>.96?[0,0,1]:[0,1,0],n=ctx.norm(ctx.cross(f,ref));return[f,n,ctx.norm(ctx.cross(n,f))];}
  function point(ctx,o,A,x,y,z){return ctx.add(o,ctx.add(ctx.mul(A[0],x),ctx.add(ctx.mul(A[1],y),ctx.mul(A[2],z))));}
  function bez(ctx,a,b,c,q){return ctx.add(ctx.add(ctx.mul(a,(1-q)*(1-q)),ctx.mul(b,2*q*(1-q))),ctx.mul(c,q*q));}
  const fade=(ctx,t,p=1.2)=>Math.pow(clamp(1-(ctx.time-t)/Math.max(.001,ctx.params.life),0,1),p);

  // Joined triangle strips. Rows keep their own world centre; only history strip width
  // faces the eye. Films carry their explicit three-dimensional transverse direction.
  function strip(ctx,out,rows,width,color,alpha,film=false,phase=0){
    if(rows.length<2)return;
    let previousSide=null,arc=0;
    const arcLengths=[0];for(let i=1;i<rows.length;i++)arcLengths.push(arcLengths.at(-1)+Math.hypot(...ctx.sub(rows[i].p,rows[i-1].p)));
    const totalArc=arcLengths.at(-1),capLength=Math.min(totalArc*.12,Math.max(.025,Math.abs(width)*1.05));
    const longitudinalCaps=arcLengths.map(v=>rows.length<3||capLength<1e-8?1:smooth(0,capLength,v)*smooth(0,capLength,totalArc-v));
    const mesh=rows.map((r,i)=>{
      const tangent=ctx.norm(ctx.sub(rows[Math.min(rows.length-1,i+1)].p,rows[Math.max(0,i-1)].p));
      let side=r.side||ctx.cross(tangent,ctx.sub(ctx.eye,r.p));
      side=ctx.sub(side,ctx.mul(tangent,ctx.dot(side,tangent)));
      if(Math.hypot(...side)<.000001)side=axes(ctx,tangent)[1];
      side=ctx.norm(side);if(previousSide&&ctx.dot(side,previousSide)<0)side=ctx.mul(side,-1);previousSide=side;
      if(i)arc+=Math.hypot(...ctx.sub(r.p,rows[i-1].p));
      const cap=longitudinalCaps[i],normal=ctx.norm(ctx.cross(tangent,side)),w=width*(r.w??1)*(.65+.35*Math.sqrt(cap)),rgb=r.color||color;
      return {p:r.p,side,normal,w,alpha:alpha*(r.a??1)*cap,rgb,uv:(r.u??arc)+phase};
    });
    const columns=film?6:1;
    function vert(r,j){const s=-1+2*j/columns,fold=film?Math.sin(s*Math.PI)*r.w*.11:0;
      ctx.vertex(out,ctx.add(r.p,ctx.add(ctx.mul(r.side,r.w*s),ctx.mul(r.normal,fold))),[...r.rgb,r.alpha],[s,r.uv],r.normal);}
    for(let i=1;i<mesh.length;i++){
      if(rows[i].cut||Math.hypot(...ctx.sub(rows[i].p,rows[i-1].p))<1e-8)continue;
      for(let j=0;j<columns;j++){vert(mesh[i-1],j);vert(mesh[i-1],j+1);vert(mesh[i],j+1);vert(mesh[i-1],j);vert(mesh[i],j+1);vert(mesh[i],j);}
    }
  }
  function groups(ctx){const g=[];for(const h of ctx.history){if(ctx.time-h.t>ctx.params.life)continue;if(!g.length||g.at(-1).at(-1).seg!==h.seg)g.push([]);g.at(-1).push(h);}
    const end=g.at(-1)?.at(-1);if(end&&ctx.moving&&ctx.params.speed>0){const d=Math.hypot(...ctx.sub(ctx.pos,end.p));if(d>1e-5&&d<Math.max(.8,ctx.params.speed*.22))g.at(-1).push({p:ctx.pos.slice(),dir:ctx.dir.slice(),t:ctx.time,seg:end.seg,arc:(end.arc||0)+d});}return g;}

  // Deliberately irregular silhouettes: [relative length, lateral reach, elevation,
  // membrane width, bend strength]. Paired wings would read as a rigid object, so roots,
  // lengths and transverse planes are staggered instead of being mirrored.
  const lobes=[
    [1.00,-.84,.30,1.18,.85], [.68,.91,.40,1.38,1.10],
    [1.15,.38,-.16,.80,.64], [.51,-.55,-.70,1.12,1.35],
    [.84,.21,.88,1.05,.86], [.61,-1.03,.05,.84,1.21],
    [.96,.93,-.33,.73,.79], [.44,.43,-.58,1.24,.92],
    [1.24,-.31,.03,.58,1.06], [.72,-.35,.72,.95,.65],
    [.57,.73,.12,.87,1.30], [1.07,.10,-.65,.62,.88],
    [.81,-.84,-.44,.72,1.14], [.47,.12,.95,1.04,.80],
    [1.32,.66,.20,.48,.92], [.90,-.20,-.92,.61,1.06]
  ];

  function feather(ctx){
    const p=ctx.params,films=[],veils=[],ribs=[],hot=[],haze=[],dust=[];
    const A=axes(ctx,ctx.dir),W=(x,y,z)=>point(ctx,ctx.pos,A,x,y,z),G=groups(ctx);
    // Colour stays below the white knee across almost the entire membrane. The
    // white maximum belongs to the tiny nose and a few short split roots only.
    const peak=Math.max(...ctx.edge,.001),chromatic=ctx.edge.map(x=>Math.pow(x/peak,2.55)*peak);
    const cyan=lerp3(chromatic,[.10,.72,1.0],.24),ice=lerp3(ctx.core,cyan,.78),violet=lerp3(cyan,[.54,.24,1.0],.88);
    const span=.65+p.shellLength*.92,spread=p.spread,localFade=Math.pow(ctx.motionFade,2.1);
    const count=clamp(Math.round(7+p.strands*.55),8,lobes.length),membraneWidth=.055+p.width*.83;
    let secondaryForks=0;

    // Long tails stay much narrower than the feather shoulder. Three nonparallel
    // curves are enough to read layered history without turning it into a comb.
    for(const group of G){
      const base=group.map(h=>({p:h.p,a:fade(ctx,h.t,.85),w:.18+.82*fade(ctx,h.t,.5),u:h.arc||0}));
      strip(ctx,haze,base,.035+p.width*.28,gain(cyan,.88),p.glow*.09,false);
      strip(ctx,veils,base,.012+p.width*.06,gain(cyan,1.08),.16,false);
      strip(ctx,hot,base,.0024+p.width*.010,gain(ice,1.45),.14,false);
      for(let k=0;k<3;k++){
        const phase=k*2.23+.37,rows=[];
        for(const h of group){const B=axes(ctx,h.dir),age=ctx.time-h.t,f=fade(ctx,h.t,.95),r=(.012+.036*k)*(1+smooth(.04,1.1,age)*2.2)*spread;
          const drift=ctx.noise1(h.t*1.25+k*2.71,141)*r*.6;
          rows.push({p:point(ctx,h.p,B,0,Math.cos(phase)*r+drift,Math.sin(phase)*r*.65),a:f*(.74-.16*k),w:.3+.7*f,u:h.arc||0});}
        strip(ctx,ribs,rows,.003+p.width*(.009+.005*k),gain(k===1?violet:cyan,1.14),.17,false,k*.91);
      }
    }

    if(localFade>.0001){
      for(let j=0;j<count;j++){
        const L=lobes[j],length=span*L[0],sideSign=L[1]<0?-1:1;
        const sway=ctx.noise1(ctx.time*.24+j*.47,143),reach=spread*(.55+.24*hash(j+8));
        // The rear is an OPEN fan. Outermost endpoints keep their transverse
        // displacement; pulling every tip back toward the axis makes a spindle.
        const middleBorn=(j===3||j===6||j===9),vertical=2.05;
        const root=W(middleBorn?-.10-length*.17:.64-.055*(j%4),
          middleBorn?L[1]*reach*.28:L[1]*.023,
          middleBorn?L[2]*reach*vertical*.20:L[2]*.026);
        const control=W(.58-length*(.30+.14*hash(j+94)),L[1]*reach*(.82+L[4]*.35),L[2]*reach*vertical*(.70+.12*sway));
        const tip=W(.45-length,L[1]*reach*(1.02+.46*hash(j+36)),L[2]*reach*vertical*(1.12+.40*hash(j+91)));
        const radial=ctx.norm(ctx.add(ctx.mul(A[1],L[1]),ctx.mul(A[2],L[2]))),rows=[];
        const tone=(j===2||j===8||j===11)?violet:cyan;
        const width=membraneWidth*L[3]*(.96+.11*Math.sin(j*1.9));
        for(let i=0;i<=64;i++){
          const q=i/64,envelope=Math.sin(Math.PI*q),ripple=ctx.noise1(q*4.1-ctx.time*.33+j*1.39,145);
          // Bending accelerates in the rear half. Small edge flutter is separated
          // from the long quadratic arc rather than randomising the whole curve.
          const curl=Math.sin(q*4.7+j*.8+ctx.time*.40)*envelope*q*q*spread*.10;
          const rearHook=smooth(.55,1,q)*Math.sin(q*4.4+j*1.61)*spread*(j%3===1?.28:.11);
          const center=ctx.add(bez(ctx,root,control,tip,q),ctx.add(ctx.mul(A[2],ripple*envelope*spread*.055+rearHook),ctx.mul(radial,curl)));
          const taper=Math.pow(envelope,.67)*(.78+.22*Math.sin(q*3.6+j))*Math.pow(1-q,.34);
          const rowSide=ctx.norm(ctx.add(radial,ctx.mul(A[2],Math.sin(q*3+j*1.1)*.34)));
          rows.push({p:center,side:rowSide,w:taper,a:localFade*smooth(0,.10,q)*(.86-.46*q),u:q*length*1.25,
            color:gain(lerp3(ice,tone,.35+smooth(.05,.90,q)*.65),1.02)});
        }
        const heroBranch=(j===1||j===4||j===8),branchContrast=heroBranch?1.12:.43;
        strip(ctx,films,rows,width,cyan,(.28+p.glow*.10)*branchContrast,true,j*2.37);
        // A displaced broad veil defines one soft fold, never another full shell.
        if(j%3===0){const loose=rows.map((r,i)=>({...r,p:ctx.add(r.p,ctx.mul(A[2],Math.sin(i/64*Math.PI)*.055)),w:r.w*1.5,a:r.a*.63}));
          strip(ctx,veils,loose,width*1.20,gain(tone,.80),p.glow*.10,true,j*2.37+.8);}

        // One narrow, partial ridge follows a curved membrane edge. Root ranges,
        // bright lengths and lateral offsets vary so these never form equal teeth.
        const ridge=rows.filter((_,i)=>i>=3+j%5&&i<=36+j%12).map((r,i)=>({...r,p:ctx.add(r.p,ctx.mul(r.side,width*r.w*(j%2?.40:-.30))),w:Math.pow(1-i/56,.7),color:gain(lerp3(ice,tone,.28+i/70),1.18)}));
        strip(ctx,ribs,ridge,.0028+p.width*.019,ice,heroBranch?.32:.115,false,j*.67);
        if(j%3===1){const short=rows.slice(0,16).map(r=>({...r,w:r.w*.55,color:gain(ctx.core,1.7)}));strip(ctx,hot,short,.004+p.width*.023,ctx.core,.38,false,j);}

        // Real second-order forks start from the middle of selected membranes.
        // There are deliberately fewer of them than major branches.
        if(j%2===0){const startQ=.32+.12*hash(j+55),start=rows[Math.round(startQ*64)].p;
          const branchEnd=point(ctx,start,A,-length*(.28+.16*hash(j+3)),sideSign*spread*(.20+.18*hash(j+11)),L[2]*spread*.26);
          const branchControl=ctx.add(ctx.lerp(start,branchEnd,.56),ctx.mul(A[2],spread*(.10+.12*hash(j+19)))),branch=[];
          for(let k=0;k<=30;k++){const q=k/30;branch.push({p:bez(ctx,start,branchControl,branchEnd,q),side:radial,w:Math.pow(Math.sin(q*Math.PI),.7)*Math.pow(1-q,.4),
            a:localFade*smooth(0,.12,q)*(1-q)*.78,u:q*length*.65,color:gain(lerp3(ice,j%4===0?violet:cyan,q*.75),1.12)});}
          strip(ctx,films,branch,width*.48,ice,heroBranch?.28:.13,true,j*1.7+.4);
          strip(ctx,ribs,branch,width*.036,ice,heroBranch?.26:.12,false,j*1.7+.4);secondaryForks++;
        }
      }

      // Small pointed nose: only a short axial streak carries the white maximum.
      const head=[];for(let i=0;i<=24;i++){const q=i/24;head.push({p:W(.38+q*.48,0,.018),w:Math.pow(Math.sin(q*Math.PI),1.45),a:localFade*(.3+.7*Math.sin(q*Math.PI)),u:q});}
      strip(ctx,haze,head,.045+p.width*.32,gain(cyan,1.5),p.glow*.17,false);
      strip(ctx,hot,head,.007+p.width*.058,gain(ctx.core,2.7),.72,false);

      // Sparse detached flecks, concentrated behind the shoulder, with zero solid body.
      for(let j=0;j<22;j++){
        const f=hash(j+17),phi=j*2.399,center=W(-span*(.12+f*.92),Math.cos(phi)*spread*(.20+.58*f),Math.sin(phi)*spread*(.15+.46*f));
        const line=[{p:point(ctx,center,A,-.025-.03*f,0,0),w:0,a:0,u:0},{p:center,w:1,a:localFade*.45,u:.5},{p:point(ctx,center,A,.025+.03*f,0,0),w:0,a:0,u:1}];
        strip(ctx,dust,line,.0028+p.width*.006,gain(j%3?violet:ice,1.55),.31,false,j);
      }
    }
    return {opaque:[],alpha:[],additive:[{data:haze,mode:1},{data:veils,mode:11},{data:films,mode:11},{data:ribs,mode:1},{data:hot,mode:1},{data:dust,mode:1}],diagnostics:{
      worldAnchored:true,closedShell:false,modelsIncluded:false,hdrEmission:true,lookdevVersion:4,
      topology:'irregular-broad-curved-membranes-with-sparse-middle-born-secondary-forks',
      featherBranches:count,secondaryForks,historyGroups:G.length,historyLineCount:1,auxiliaryHistoryFilaments:3,
      localMotionFade:localFade,maximumBranchLength:span*Math.max(...lobes.slice(0,count).map(x=>x[0])),
      parameterConsumers:{speed:'host world-history velocity',life:'stored history age fade',width:'membrane breadth and ridge thickness',strands:'8–16 broad major membranes',spread:'three-dimensional branch reach',shellLength:'unequal quadratic branch lengths',glow:'soft veils and nose halo',coreColor:'small nose / pale membrane roots',edgeColor:'cyan folds blended with sparse violet rear films'},
      unsupportedParameters:[],historyUsesCurrentPose:false,localSpace:'broad 3D membrane branches',detachedSpace:'stored world positions and stored per-sample frames',
      noiseLayers:{geometry:'slow secondary curvature and small silhouette flutter',material:'nonperiodic stretched flow with soft open erosion'},
      limits:['surface films rather than volume integration','camera-facing transverse direction only for narrow history strips','no real refraction or optical motion blur']}};
  }
  window.DunguangPrimaryForms.feather=feather;
  window.DunguangFeatherV4={
    shaderFunctions:'',
    shaderBranch:`if(uMode==11){
      vec2 featherP=vec2(vUV.x*2.10,vUV.y*.58-uTime*.24);
      vec2 featherWarp=flow(featherP,uTime*.42);
      float featherCoarse=fbm(featherP+featherWarp*1.16);
      float featherFibres=fbm(vec2(vUV.x*15.0+featherWarp.y*2.8,vUV.y*.94-uTime*.46));
      float featherTear=smoothstep(.16,.56,featherCoarse*.70+featherFibres*.30+edge*.12);
      float featherFine=flowMask(vec2(vUV.x*.44+.5,vUV.y*.11),featherWarp*.18+vec2(0.,.10),uTime*.13);
      float featherFold=pow(max(0.,1.-abs(vUV.x+.17+featherWarp.x*.30)*2.3),2.6);
      float featherEdge=pow(edge,.70);
      a*=featherEdge*featherTear*(.49+.31*featherFibres+.20*featherFine);
      c*=.70+.27*featherCoarse+.26*featherFold;
      c+=vec3(.035,.065,.090)*featherFold;
    }`
  };
})();
