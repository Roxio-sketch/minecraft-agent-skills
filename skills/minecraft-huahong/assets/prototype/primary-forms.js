/* Original dense shader-carrier geometry. No imported textures or model assets.
 * HDR glow is composited by the host; ribbons are intentionally layered before optimization. */
(() => {
  'use strict';
  const TAU = Math.PI * 2;
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const mix = (a, b, t) => a.map((v, i) => v * (1 - t) + b[i] * t);
  const hash = n => { const f = Math.sin(n * 127.13 + 173.71) * 43758.5453; return f - Math.floor(f); };
  const parameterConsumers = {
    speed: 'host distance history; local elongation / rotation / transport',
    life: 'world-history age and detached-birth retention',
    width: 'actual ribbon half-width',
    strands: 'independent branches, open bands, tongues, or needle motes',
    spread: 'actual transverse geometry',
    shellLength: 'local curve length / ring axial spacing / concentrated needle tip',
    glow: 'outer-band emission strength',
    coreColor: 'hot inner geometry RGB',
    edgeColor: 'outer geometry RGB'
  };

  function frame(ctx, forward) {
    const F = ctx.norm(forward), ref = Math.abs(ctx.dot(F, [0, 1, 0])) > .96 ? [0, 0, 1] : [0, 1, 0];
    const N = ctx.norm(ctx.cross(F, ref));
    return [F, N, ctx.norm(ctx.cross(N, F))];
  }
  function pointAt(ctx, origin, axes, x, y, z) {
    return ctx.add(origin, ctx.add(ctx.mul(axes[0], x), ctx.add(ctx.mul(axes[1], y), ctx.mul(axes[2], z))));
  }
  function quadratic(ctx, a, b, c, t) {
    const s = 1 - t;
    return ctx.add(ctx.add(ctx.mul(a, s * s), ctx.mul(b, 2 * s * t)), ctx.mul(c, t * t));
  }
  function ageFade(ctx, birth, power = 1.45) {
    return Math.pow(clamp(1 - (ctx.time - birth) / Math.max(.001, ctx.params.life), 0, 1), power);
  }
  function diagnostics(extra) {
    return Object.assign({worldAnchored: true, closedShell: false, parameterConsumers: {...parameterConsumers}, unsupportedParameters: []}, extra);
  }
  function packets(outer, core, mode, details) {
    return {opaque: [], alpha: [], additive: [{data: outer, mode}, {data: core, mode: 1}], diagnostics: diagnostics(details)};
  }

  // Opacity is feathered along the whole continuous run, not on every quad.
  // A short world-distance window removes planar end caps without shortening trails.
  function ribbonCaps(ctx, points, width) {
    const caps=new Array(points.length).fill(1);
    const smooth=x=>{x=clamp(x,0,1);return x*x*(3-2*x);};
    let start=0;
    for(let end=1;end<=points.length;end++){
      if(end<points.length&&!points[end].cut)continue;
      const arc=[0];for(let i=start+1;i<end;i++)arc.push(arc.at(-1)+Math.hypot(...ctx.sub(points[i].p,points[i-1].p)));
      const total=arc.at(-1),span=Math.min(total*.12,Math.max(.025,Math.abs(width)*1.05));
      if(end-start>=3&&span>1e-8)for(let i=start;i<end;i++)caps[i]=smooth(arc[i-start]/span)*smooth((total-arc[i-start])/span);
      start=end;
    }
    return caps;
  }

  // A variable-width joined triangle ribbon. Its centreline never depends on the camera.
  // Only the strip's transverse direction follows the eye, avoiding edge-on disappearance.
  function ribbon(ctx, out, points, width, color, alpha, uvOffset = 0, useRowColor = true) {
    if (points.length < 2) return;
    let distance = uvOffset, previousSide = null;
    const caps=ribbonCaps(ctx,points,width);
    const rows = points.map((row, i) => {
      const before = points[Math.max(0, i - 1)].p, after = points[Math.min(points.length - 1, i + 1)].p;
      const tangent = ctx.norm(ctx.sub(after, before));
      let side = ctx.cross(tangent, ctx.sub(ctx.eye, row.p));
      if (Math.hypot(...side) < 1e-6) side = frame(ctx, tangent)[1];
      side = ctx.norm(side);
      if (previousSide && ctx.dot(side, previousSide) < 0) side = ctx.mul(side, -1);
      previousSide = side;
      if (i) distance += Math.hypot(...ctx.sub(row.p, points[i - 1].p));
      const cap=caps[i],w = width * (row.w ?? 1) * (.65+.35*Math.sqrt(cap)), n = ctx.norm(ctx.cross(tangent, side));
      return {a: ctx.add(row.p, ctx.mul(side, -w)), b: ctx.add(row.p, ctx.mul(side, w)), n,
        color: [...(useRowColor && row.color ? row.color : color), alpha * (row.a ?? 1) * cap], uv: distance};
    });
    for (let i = 1; i < rows.length; i++) {
      if (points[i].cut || Math.hypot(...ctx.sub(points[i].p, points[i - 1].p)) < 1e-7) continue;
      const a = rows[i - 1], b = rows[i];
      for (const [r, side] of [[a,-1],[a,1],[b,1],[a,-1],[b,1],[b,-1]]) {
        ctx.vertex(out, side < 0 ? r.a : r.b, r.color, [side, r.uv], r.n);
      }
    }
  }

  function historyGroups(ctx, includeCurrent = true) {
    const groups = [];
    for (const h of ctx.history) {
      if (ctx.time - h.t > ctx.params.life) continue;
      if (!groups.length || groups[groups.length - 1].at(-1).seg !== h.seg) groups.push([]);
      groups.at(-1).push(h);
    }
    // This endpoint is an extra NEW sample only; old world positions are never offset by pos.
    const last = groups.at(-1)?.at(-1);
    if (includeCurrent && ctx.moving && ctx.params.speed > 0 && last) {
      const gap = Math.hypot(...ctx.sub(ctx.pos, last.p));
      if (gap > 1e-5 && gap < Math.max(.8, ctx.params.speed * .22)) {
        groups.at(-1).push({p: ctx.pos.slice(), t: ctx.time, dir: ctx.dir.slice(), seg: last.seg,
          arc: (last.arc || 0) + gap});
      }
    }
    return groups;
  }

  // Detached particles are born at stored history samples. Cache immutable birth positions,
  // axes, speed and phase, so dropping old samples cannot slide an existing birth forward.
  function births(ctx, name, interval) {
    const cache = (ctx.state.primaryBirths || (ctx.state.primaryBirths = {}));
    const store = cache[name] || (cache[name] = {}), seen = new Set();
    for (const h of ctx.history) {
      if (ctx.time - h.t > ctx.params.life) continue;
      const bucket = Math.floor((h.t + 1e-8) / interval), key = h.seg + ':' + bucket;
      seen.add(key);
      if (!store[key]) store[key] = {p: h.p.slice(), t: h.t, axes: frame(ctx, h.dir), speed: ctx.params.speed,
        seg: h.seg, phase: hash(bucket * 13.1 + h.seg * 31.7 + ctx.seed) * TAU, bucket};
    }
    const live = [];
    for (const key of Object.keys(store)) {
      const b = store[key], age = ctx.time - b.t;
      if (age >= 0 && age < ctx.params.life) live.push(b);
      // Keep an expired anchor while its bucket still occurs in source history. Otherwise
      // the next sample in the same bucket would incorrectly respawn a younger particle.
      if (!seen.has(key) && age > ctx.params.life) delete store[key];
    }
    return live.sort((a, b) => a.t - b.t);
  }

  const energy = (rgb, gain) => rgb.map(x => x * gain);
  // Keep outer films chromatic when many light layers accumulate in HDR.
  const saturated = (rgb, power=2.4) => { const peak=Math.max(...rgb,.001); return rgb.map(x=>Math.pow(x/peak,power)*peak); };

  // Crossed, tapered light strokes only: these never become opaque model geometry.
  function glare(ctx, out, center, axes, length, width, color, alpha, phase=0) {
    for (let k=0;k<3;k++) {
      const angle=phase+k*Math.PI/3, side=ctx.add(ctx.mul(axes[1],Math.cos(angle)),ctx.mul(axes[2],Math.sin(angle)));
      const rows=[];
      for (let i=0;i<=16;i++) {
        const q=i/16, profile=Math.pow(Math.sin(Math.PI*q),1.5);
        rows.push({p:ctx.add(center,ctx.add(ctx.mul(axes[0],(q-.5)*length),ctx.mul(side,Math.sin(q*Math.PI)*width*.12))),
          w:profile,a:alpha*(.25+.75*profile)});
      }
      ribbon(ctx,out,rows,width,color,1,phase+k);
    }
  }

  function needle(ctx) {
    const {params:p,edge,core}=ctx, haze=[],fine=[],hot=[],sparks=[];
    const axes=frame(ctx,ctx.dir),groups=historyGroups(ctx),halfWidth=.012+p.width*.06;
    const localFade=Math.pow(ctx.motionFade,2.2),trailCount=5+Math.round(p.strands*1.6);
    let historyLength=0;
    for(const group of groups) {
      for(let i=1;i<group.length;i++)historyLength+=Math.hypot(...ctx.sub(group[i].p,group[i-1].p));
      const main=group.map(h=>({p:h.p,a:ageFade(ctx,h.t,.85),w:.18+.82*ageFade(ctx,h.t,.5)}));
      const origin=group[0]?.arc||0;
      ribbon(ctx,haze,main,halfWidth*(5+p.glow*4),energy(edge,.75),p.glow*.14,origin);
      ribbon(ctx,fine,main,halfWidth*1.8,energy(edge,1.7),.30,origin);
      ribbon(ctx,hot,main,halfWidth*.38,energy(core,3.2),.62,origin);
      // Closely packed parallel scintillation. The primary silhouette stays thin.
      for(let k=0;k<trailCount;k++) {
        const phase=hash(k+ctx.seed)*TAU,amp=(.018+hash(k+67)*.075)*(1+p.spread*.7),rows=[];
        for(let i=0;i<group.length;i++) {
          const h=group[i],a=frame(ctx,h.dir),life=ageFade(ctx,h.t,.8),tailAge=ctx.time-h.t;
          const drift=ctx.noise1(h.t*1.9+k*.7,61)*.035;
          const offset=(.12+.88*Math.min(1,tailAge*.65))*amp;
          rows.push({p:pointAt(ctx,h.p,a,0,Math.cos(phase)*offset+drift,Math.sin(phase)*offset),
            a:life*(.18+.44*hash(k+8)),w:.18+.82*life});
        }
        ribbon(ctx,fine,rows,halfWidth*(.10+.14*hash(k+24)),energy(mix(edge,core,.55),1.8),.40,origin+k*.7);
      }
    }
    if(localFade>.0001) {
      const head=pointAt(ctx,ctx.pos,axes,.38,0,.03),length=.45+p.shellLength*.37+p.speed*.018;
      glare(ctx,haze,head,axes,length*1.6,.12+p.width*.32,energy(edge,1.9),.16*p.glow*localFade);
      glare(ctx,hot,head,axes,length,.032+p.width*.16,energy(core,5.4),.48*localFade);
      // A short asymmetric fan of razor slivers behind the pointed light source.
      for(let j=0;j<18;j++) {
        const phase=j*2.399,reach=.45+hash(j+16)*1.25+p.shellLength*.28,rows=[];
        for(let i=0;i<=20;i++) {
          const q=i/20,r=Math.pow(q,1.5)*(.018+hash(j+2)*.13)*p.spread;
          rows.push({p:pointAt(ctx,head,axes,-q*reach,Math.cos(phase)*r,Math.sin(phase)*r),
            w:Math.pow(1-q,1.7),a:localFade*(1-q)*(.2+.5*hash(j+9))});
        }
        ribbon(ctx,fine,rows,.005+p.width*.045,energy(mix(core,edge,.3),2.3),.55,j*.6);
      }
    }
    const motes=births(ctx,'needle-motes',.075),moteLimit=26+Math.round(p.strands*9);
    let moteCount=0;
    for(const b of motes.slice(-moteLimit)) {
      const age=ctx.time-b.t,fade=ageFade(ctx,b.t,2);
      for(let j=0;j<2;j++) {
        const phase=b.phase+j*2.1,drift=(.10+age*.22)*p.spread;
        const center=pointAt(ctx,b.p,b.axes,-age*.22,Math.cos(phase)*drift,Math.sin(phase)*drift+age*.02);
        const length=(.015+hash(b.bucket+j)*.065)*(.25+fade);
        glare(ctx,sparks,center,b.axes,length,.003+p.width*.009,energy(core,2.8),fade*.28,b.phase);
        moteCount++;
      }
    }
    return {opaque:[],alpha:[],additive:[{data:haze,mode:1},{data:fine,mode:1},{data:hot,mode:1},{data:sparks,mode:1}],
      diagnostics:diagnostics({topology:'concentrated-comet-head-and-close-parallel-needle-filaments',historyLineCount:1,
        auxiliaryFilaments:trailCount,historyGroups:groups.length,historyLength,needleHalfWidth:halfWidth,
        detachedMotes:moteCount,localMotionFade:localFade,hdrEmission:true,modelsIncluded:false})};
  }

  function smoothedHistory(ctx, group) {
    if (group.length < 4) return group.map(h => ({p:h.p, a:ageFade(ctx,h.t), w:.3+.7*ageFade(ctx,h.t,.65)}));
    const raw = group.filter((_, i) => i % 4 === 0);
    if (raw.at(-1) !== group.at(-1)) raw.push(group.at(-1));
    if (raw.length < 3) return group.map(h => ({p:h.p, a:ageFade(ctx,h.t), w:1}));
    const rows = [];
    for (let i = 1; i < raw.length-1; i++) {
      const a = i === 1 ? raw[0] : {p:ctx.lerp(raw[i-1].p,raw[i].p,.5), t:(raw[i-1].t+raw[i].t)*.5};
      const c = i === raw.length-2 ? raw.at(-1) : {p:ctx.lerp(raw[i].p,raw[i+1].p,.5), t:(raw[i].t+raw[i+1].t)*.5};
      const axes = frame(ctx, ctx.sub(c.p,a.p));
      for (let j = i === 1 ? 0 : 1; j <= 8; j++) {
        const q = j/8, t = a.t+(c.t-a.t)*q, fade = ageFade(ctx,t), envelope = Math.sin(Math.PI*q);
        // Historical waviness uses birth time, never current pose or a moving local origin.
        const wave = (ctx.noise1(t*.85,71)*.035+ctx.noise1(t*5.3,72)*.012)*ctx.params.spread*envelope;
        rows.push({p:ctx.add(quadratic(ctx,a.p,raw[i].p,c.p,q),ctx.mul(axes[1],wave)),
          a:fade,w:.3+.7*ageFade(ctx,t,.65)});
      }
    }
    return rows;
  }



  // A genuinely open annular ribbon. No fan triangles or cap span the central aperture.
  function openRing(ctx,out,center,axes,radius,halfWidth,phase,sweep,twist,color,alpha,origin=0) {
    const steps=52, rows=[];
    for(let i=0;i<=steps;i++) {
      const q=i/steps, angle=phase+q*sweep, taper=Math.pow(Math.sin(Math.PI*q),.45);
      const axial=Math.sin(angle*1.7+phase)*twist;
      const p=pointAt(ctx,center,axes,axial,Math.cos(angle)*radius,Math.sin(angle)*radius);
      const radial=ctx.add(ctx.mul(axes[1],Math.cos(angle)),ctx.mul(axes[2],Math.sin(angle)));
      rows.push({a:ctx.add(p,ctx.mul(radial,-halfWidth*taper)),b:ctx.add(p,ctx.mul(radial,halfWidth*taper)),
        color:[...color,alpha*taper],uv:origin+q*radius*sweep});
    }
    for(let i=1;i<rows.length;i++) {
      const a=rows[i-1],b=rows[i];
      const normal=ctx.norm(ctx.cross(ctx.sub(b.a,a.a),ctx.sub(a.b,a.a)));
      for(const [r,side] of [[a,-1],[a,1],[b,1],[a,-1],[b,1],[b,-1]])ctx.vertex(out,side<0?r.a:r.b,r.color,[side,r.uv],normal);
    }
  }



  function flame(ctx) {
    const {params:p,edge,core}=ctx,red=[],fire=[],gold=[],hot=[],embers=[],axes=frame(ctx,ctx.dir);
    const redTone=saturated(edge,2.7),orangeTone=saturated(edge,1.6),goldTone=mix(saturated(edge,1.15),core,.12);
    const count=clamp(Math.round(p.strands*3.2),18,48),localFade=Math.pow(ctx.motionFade,3.6),speedStretch=.82+p.speed*.038;
    let forks=0;
    if(localFade>.0001) {
      for(let j=0;j<count;j++) {
        const seed=j*7.31+ctx.seed,phase=hash(seed)*TAU,side=j%2?1:-1;
        const length=(.55+p.shellLength*(.18+.87*hash(seed+1)))*speedStretch;
        const reach=(.10+.58*hash(seed+5))*p.spread;
        const root=pointAt(ctx,ctx.pos,axes,.48+hash(seed+4)*.16,Math.cos(phase)*reach*.72,Math.sin(phase)*reach*.62);
        const control=pointAt(ctx,root,axes,-length*.32,Math.cos(phase)*p.spread*.18,Math.sin(phase)*p.spread*.18+.09);
        const tip=pointAt(ctx,root,axes,-length,Math.cos(phase)*p.spread*(.24+.75*hash(seed+2)),Math.sin(phase)*p.spread*.6+.12);
        const rows=[];
        for(let i=0;i<=56;i++) {
          const q=i/56,window=Math.sin(Math.PI*q),middle=ctx.noise1(q*7.2-ctx.time*(2.5+p.speed*.04)+j*3.37,81);
          const flutter=ctx.noise1(q*13-ctx.time*4.5+j*2.7,82);
          const center=ctx.add(quadratic(ctx,root,control,tip,q),ctx.add(ctx.mul(axes[1],(middle*.14+flutter*.045)*window*p.spread),
            ctx.mul(axes[2],ctx.noise1(q*8.5-ctx.time*3.1+j*2.7,83)*window*p.spread*.15)));
          // Fragment erosion provides ragged tongues; never omit whole rows as rectangular holes.
          rows.push({p:center,w:Math.pow(window,.60)*Math.pow(1-q,.24)*(.44+.66*hash(seed+8))*(.76+.24*middle),
            a:localFade*(.96-.81*q),color:mix(core,edge,.06+.94*Math.pow(q,.65))});
        }
        const tongueWidth=p.width*(.48+.96*hash(seed+9));
        ribbon(ctx,red,rows,tongueWidth*2.1,energy(redTone,1.3),p.glow*.13,phase,false);
        ribbon(ctx,fire,rows,tongueWidth,energy(orangeTone,1.8),p.glow*.34,phase,false);
        // Broad golden roots terminate early; thin white roots never become uniform feather ribs.
        ribbon(ctx,gold,rows.slice(0,25),tongueWidth*.20,energy(goldTone,1.85),.25,phase,false);
        if(j%6===0)ribbon(ctx,hot,rows.slice(0,15),tongueWidth*.035,energy(core,2.8),.18,phase,false);
        if(j%3===0) {
          const rootRow=rows[32],tail=pointAt(ctx,rootRow.p,axes,-length*.34,side*p.spread*.31,.25),fork=[];
          for(let k=0;k<=22;k++) {
            const q=k/22;
            fork.push({p:ctx.add(ctx.lerp(rootRow.p,tail,q),ctx.mul(axes[2],Math.sin(q*5+ctx.time*3)*q*(1-q)*.3)),
              w:Math.pow(Math.sin(Math.PI*q),.8)*(.7-.5*q),a:localFade*(1-q),color:mix(core,edge,.4+.6*q)});
          }
          ribbon(ctx,fire,fork,p.width*.43,energy(orangeTone,1.6),p.glow*.28,phase+.7,false);forks++;
        }
      }
      glare(ctx,red,pointAt(ctx,ctx.pos,axes,.34,0,0),axes,.85,p.width*.9,energy(redTone,2.0),localFade*.11);
      glare(ctx,hot,pointAt(ctx,ctx.pos,axes,.43,0,0),axes,.32,p.width*.08,energy(core,3.0),localFade*.28);
    }
    const detached=births(ctx,'detached-fire-shreds',.10),perBirth=clamp(Math.round(p.strands*.55),3,7);let detachedShreds=0;
    for(const b of detached) {
      const age=ctx.time-b.t,fade=ageFade(ctx,b.t,2.0);if(age<.09)continue;
      for(let j=0;j<perBirth;j++) {
        const phase=b.phase+j*2.399,rise=age*(.27+.30*hash(b.bucket+j+5));
        const center=pointAt(ctx,b.p,b.axes,-.12-age*b.speed*(.035+.014*j),Math.cos(phase)*p.spread*(.12+age*.19),Math.sin(phase)*p.spread*.31+rise);
        const length=(.25+p.shellLength*.095)*(.40+fade),rows=[];
        for(let i=0;i<=20;i++) {
          const q=i/20;
          rows.push({p:pointAt(ctx,center,b.axes,-q*length,Math.sin(q*4+phase+age*2.1)*q*.21,q*(.17+age*.06)),
            w:Math.pow(Math.sin(Math.PI*q),.65)*(.85-.6*q),a:fade*(1-q*.84),color:mix(core,edge,.35+.65*q)});
        }
        ribbon(ctx,red,rows,p.width*(.40+.37*fade),energy(redTone,1.4),p.glow*.12,b.t*3+j,false);
        ribbon(ctx,fire,rows,p.width*(.16+.24*fade),energy(orangeTone,1.9),p.glow*.25,b.t*3+j,false);
        if(j%2===0) {
          const spark=pointAt(ctx,center,b.axes,-age*.14,Math.cos(phase)*age*.08,.14+age*.10);
          glare(ctx,embers,spark,b.axes,.025+.085*hash(b.bucket+j),.003+p.width*.007,energy(goldTone,2.8),fade*.30,phase);
        }
        detachedShreds++;
      }
    }
    return {opaque:[],alpha:[],additive:[{data:red,mode:1},{data:fire,mode:5},{data:gold,mode:5},{data:hot,mode:1},{data:embers,mode:1}],
      diagnostics:diagnostics({topology:'dense-broad-curling-torn-flame-films-and-drifting-embers',localTongues:count,forkedTips:forks,
        detachedShreds,localMotionFade:localFade,physicalGaps:false,continuousTongueGeometry:true,softLongitudinalCaps:true,erosionShaderMode:5,birthVelocityCached:true,
        localSpace:'fast-turbulent asymmetric fire tongues',detachedSpace:'cached birth frame plus age-driven rise',hdrEmission:true,modelsIncluded:false})};
  }

  window.DunguangPrimaryForms = {needle, flame};
})();
