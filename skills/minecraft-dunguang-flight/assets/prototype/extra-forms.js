/* Original offline geometry studies. No textures, fluid solver or scene refraction. */
(function (root) {
  'use strict';
  const TAU = Math.PI * 2;
  const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
  const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
  const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const unit = a => { const n = Math.hypot(...a); return n > 1e-8 ? mul(a, 1 / n) : [0, 1, 0]; };
  const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
  const mix = (a, b, f) => a.map((v, i) => v * (1 - f) + b[i] * f);
  const fract = x => x - Math.floor(x);
  // Random identity is tied to a historical birth, never to the render frame.
  const random = (seed, born, salt = 0) => fract(Math.sin(seed * 17.131 + born * 127.17 + salt * 311.73) * 43758.5453123);
  const color = (rgb, a) => [rgb[0], rgb[1], rgb[2], clamp(a, 0, 1)];
  const offset = (p, b, x, y, z) => add(p, add(mul(b[0], x), add(mul(b[1], y), mul(b[2], z))));
  const basisFor = d => { const axis = unit(d), side = unit(cross(axis, Math.abs(axis[1]) > .97 ? [0, 0, 1] : [0, 1, 0])); return [axis, side, unit(cross(side, axis))]; };
  const ageFade = (age, life, power = 1.5) => Math.pow(clamp(1 - age / Math.max(.1, life), 0, 1), power);
  const newParts = () => ({ opaque: [], alpha: [], additive: [], diagnostics: {} });
  function emit(ctx, out, p, c, uv, n) { ctx.vertex(out, p, c, uv, n); }
  function triangle(ctx, out, a, b, c, tint) {
    const normal = unit(cross(sub(b, a), sub(c, a)));
    emit(ctx, out, a, tint, [0, 0], normal); emit(ctx, out, b, tint, [1, 0], normal); emit(ctx, out, c, tint, [1, 1], normal);
  }
  function face(ctx, out, a, b, c, d, tint) {
    triangle(ctx, out, a, b, c, tint); triangle(ctx, out, a, c, d, tint);
  }
  function panelQuad(ctx, out, points, tint, uv, vertexTints, vertexNormals) {
    const n = unit(cross(sub(points[1], points[0]), sub(points[3], points[0])));
    for (const i of [0, 1, 2, 0, 2, 3]) emit(ctx, out, points[i], vertexTints ? vertexTints[i] : tint, uv[i], vertexNormals ? vertexNormals[i] : n);
  }
  // Distance samples already originate on fixed simulation ticks. Modulo the
  // global arc picks persistent births even as the oldest history is pruned.
  function births(ctx, stride, maxCount, life = ctx.params.life) {
    const candidates = [];
    for (const h of ctx.history || []) {
      const age = ctx.time - h.t;
      if (age < -1e-5 || age >= life) continue;
      const serial = Math.round((h.arc || 0) / .14);
      if (serial % stride === 0) candidates.push(h);
    }
    return candidates.slice(-maxCount);
  }
  function stableStop(ctx, key) {
    const store = ctx.state || (ctx.state = {}), old = store[key];
    if (ctx.moving || !old || ctx.time < old.lastTime) {
      store[key] = { pos: ctx.pos.slice(), basis: ctx.basis.map(x => x.slice()), stopped: ctx.time, lastTime: ctx.time, moving: ctx.moving };
    } else if (old.moving) {
      old.pos = ctx.pos.slice(); old.basis = ctx.basis.map(x => x.slice());
      old.stopped = ctx.time - (1 - ctx.motionFade) * Math.max(.3, ctx.params.life * .65); old.moving = false;
    }
    const result = store[key]; result.lastTime = ctx.time;
    return { pos: ctx.moving ? ctx.pos : result.pos, basis: ctx.moving ? ctx.basis : result.basis, age: ctx.moving ? 0 : Math.max(0, ctx.time - result.stopped) };
  }
  function flowClock(ctx, key) {
    const state = ctx.state || (ctx.state = {}); let clock = state[key];
    if (!clock || ctx.time < clock.time) clock = state[key] = { time: ctx.time, value: ctx.time, fade: ctx.motionFade };
    const dt = Math.max(0, ctx.time - clock.time);
    clock.value += dt * (.05 + .95 * (clock.fade + ctx.motionFade) * .5);
    clock.time = ctx.time; clock.fade = ctx.motionFade;
    return clock.value;
  }
  // Analytic density / sparkle support: no low-poly closed smoke surfaces.
  function sprite(ctx, out, center, sizeX, sizeY, tint, rotation = 0) {
    const normal = unit(sub(ctx.eye || add(center, [3, 2, 4]), center));
    const right = unit(cross(Math.abs(normal[1]) > .98 ? [0, 0, 1] : [0, 1, 0], normal));
    const up = unit(cross(normal, right)), ca = Math.cos(rotation), sa = Math.sin(rotation);
    const x = mul(add(mul(right, ca), mul(up, sa)), sizeX), y = mul(add(mul(up, ca), mul(right, -sa)), sizeY);
    const points = [sub(sub(center, x), y), add(sub(center, y), x), add(add(center, x), y), add(sub(center, x), y)];
    const uv = [[-1, -1], [1, -1], [1, 1], [-1, 1]];
    for (const k of [0, 1, 2, 0, 2, 3]) emit(ctx, out, points[k], tint, uv[k], normal);
  }
  function linePath(ctx, out, points, width, rgb, alpha, endRgb = rgb) {
    for (let i = 0; i < points.length - 1; i++) {
      const a = i / (points.length - 1), b = (i + 1) / (points.length - 1);
      ctx.appendRibbon(out, points[i], points[i + 1], width, color(mix(rgb, endRgb, a), alpha * Math.sin(Math.PI * (.025 + .95 * a))), color(mix(rgb, endRgb, b), alpha * Math.sin(Math.PI * (.025 + .95 * b))), a, b);
    }
  }
  function smoke(ctx) {
    const out=newParts(),p=ctx.params,filaments=[],sparks=[];
    const density=clamp(Math.round(p.strands),2,12),stride=Math.max(1,Math.round(5-density*.30));
    // A short dense moving mass plus a sparse old wake, instead of filling the full history with fog.
    const bodyLife=Math.min(p.life,.18+p.shellLength*.07),samples=births(ctx,stride,190),layers=6;
    let cards=0,embers=0,hotPatches=0;
    for(const h of samples){
      const age=Math.max(0,ctx.time-h.t);if(age>bodyLife*1.8)continue;
      const b=basisFor(h.dir||ctx.dir),r0=random(ctx.seed,h.t,1),r1=random(ctx.seed,h.t,2),r2=random(ctx.seed,h.t,3);
      const tail=age/bodyLife,fade=ageFade(age,bodyLife*1.8,2.1),body=Math.exp(-tail*tail*1.9);
      const angle=r0*TAU+age*.28,radial=p.spread*(.055+r1*.10+age*.07);
      const origin=offset(h.p,b,-age*p.speed*.018,Math.cos(angle)*radial,Math.sin(angle)*radial+age*.025);
      const radius=(.21+p.width*.88)*(.76+r0*.42)*(1+age*.35);
      for(let j=0;j<layers;j++){
        const salt=random(ctx.seed,h.t,j+31),phi=salt*TAU+age*.10;
        const center=offset(origin,b,(salt-.5)*radius*.8,Math.cos(phi)*radius*(.10+j*.055),Math.sin(phi)*radius*.22);
        const tint=mix([.0035,.0012,.002],[.040,.003,.005],body*(.2+.5*salt));
        const data=[];sprite(ctx,data,center,radius*(1.0+salt*.35),radius*(.74+r1*.26),color(tint,fade*(.34+.16*body)*(j===0?1.22:1)),phi);
        out.alpha.push({data,mode:8,variant:(Math.floor(r0*4)+j)%4,center});cards++;
      }
      if(age<bodyLife*.85&&r2>.24&&p.glow>0){
        const glow=[];const at=offset(origin,b,-radius*.1,(r0-.5)*radius*.8,(r1-.5)*radius*.65);
        sprite(ctx,glow,at,radius*(.72+r0*.20),radius*.50,color(mul([1.8,.018,.004],.5+p.glow),body*p.glow*.085),angle);
        out.additive.push({data:glow,mode:8,variant:Math.floor(r1*4)});hotPatches++;
        if(r0>.58){
          const path=[];for(let k=0;k<=13;k++){const t=k/13;path.push(offset(at,b,radius*(.22-t*.54),Math.sin(t*3.1+r1*TAU)*radius*.10,Math.sin(t*5.2+r2)*radius*.12));}
          linePath(ctx,filaments,path,.006+p.width*.018,[2.2,.035,.007],body*p.glow*.27,[.58,.007,.002]);
        }
        if(r0>.76){const size=.012+r1*.022;sprite(ctx,sparks,at,size,size,color([2.0,.045,.009],body*p.glow*.44));embers++;}
      }
    }
    if(filaments.length)out.additive.push({data:filaments,mode:1});if(sparks.length)out.additive.push({data:sparks,mode:9});
    out.diagnostics={topology:'compact-crimson-black-density-with-localized-heat-cracks',cards,layersPerBirth:layers,hotPatches,embers,bodyLife,
      positionSpace:'historical world births with compact independent drift',shaderOnly:true,modelsIncluded:false,
      limitations:['CC0 textured density cards, not a physical participating volume','packet-center alpha sorting']};
    return out;
  }

  // Open, irregular light plates only: no blade, guard, grip or closed solid.
  // The fragment mode erodes each plane with animated procedural noise.
  function lightShard(ctx, out, center, b, extent, breadth, tint, skew) {
    // Two folded transparent lobes leave an actual long negative slit.
    for (const sign of [-1, 1]) {
      const points = [
        offset(center, b, -extent * .65, sign * breadth * .045, 0),
        offset(center, b, extent * .47, sign * breadth * .065, breadth * .025),
        offset(center, b, extent * .56, sign * breadth * .22, breadth * .10),
        offset(center, b, -extent * (.10 + skew * .15), sign * breadth, breadth * (.13 + skew * .14))
      ];
      panelQuad(ctx, out, points, tint, [[0, 0], [0, 1], [1, 1], [1, .38]]);
    }
  }
  function swords(ctx) {
    // Legacy API key retained; this is an abstract fragmented-light formation.
    const out = newParts(), p = ctx.params, stop = stableStop(ctx, 'shardsStop');
    const clock = flowClock(ctx, 'shardsClock'), count = clamp(Math.round(p.strands), 2, 12) * 5;
    const fade = ctx.moving ? 1 : Math.pow(ctx.motionFade, 1.35);
    const radius = (.30 + p.spread * .75) * (.52 + .48 * ctx.motionFade) + stop.age * .16;
    const light = [], edges = [], sparkles = [];
    if (fade > .001) for (let j = 0; j < count; j++) {
      const r0 = random(ctx.seed, 0, j + 201), r1 = random(ctx.seed, 0, j + 251), r2 = random(ctx.seed, 0, j + 301);
      const phase = j / count * TAU + clock * (.22 + p.speed * .021) * (j % 2 ? 1 : -.75) + r0;
      const radial = radius * (.53 + r1 * .70);
      const stagger = (r2 - .52) * p.shellLength * (1.30 + .35 * ctx.motionFade);
      const center = offset(stop.pos, stop.basis, stagger - stop.age * (.05 + r2 * .07), Math.cos(phase) * radial, Math.sin(phase) * radial);
      // Each sheet has its own roll and tilt, so the formation is spatial rather
      // than a camera-facing sprite stack or a repeated solid-object arrangement.
      const tilt = .06 + r1 * .28, d = unit(offset([0, 0, 0], stop.basis, 1, Math.cos(phase + r0) * tilt, Math.sin(phase + r0) * tilt));
      const b = basisFor(d), roll = r0 * TAU + clock * (.18 + r2 * .15);
      const side = add(mul(b[1], Math.cos(roll)), mul(b[2], Math.sin(roll))), local = [d, side, unit(cross(d, side))];
      const extent = (.30 + p.shellLength * .19) * (.58 + r0 * .75) * (1 + stop.age * .07);
      const breadth = (.055 + p.width * .65) * (.64 + r1 * .75);
      const pulse = .73 + .27 * Math.sin(clock * (1.2 + p.speed * .035) + r2 * TAU);
      const data = [], tint = mix(ctx.edge, ctx.core, .22 + r2 * .57);
      lightShard(ctx, data, center, local, extent, breadth, color(tint, fade * pulse * .48), r1);
      out.alpha.push({ data, mode: 4, center });
      if (p.glow > 0) {
        lightShard(ctx, light, center, local, extent * .97, breadth * .46, color(mul(mix(tint, ctx.core, .54), 1.4), fade * pulse * p.glow * .44), r1);
        const contour = [offset(center, local, -extent * (.56 + r1 * .12), 0, 0), offset(center, local, -extent * .06, breadth * (.68 + r1 * .23), 0), offset(center, local, extent * (.42 + r1 * .09), breadth * .18, 0)];
        linePath(ctx, edges, contour, .004 + p.width * .012, mul(ctx.core, 1.7), fade * p.glow * .80, ctx.edge);
        if (j % 3 === 0) sprite(ctx, sparkles, contour[1], .025 + r1 * .04, .025 + r1 * .04, color(mul(ctx.core, 2.1), fade * p.glow * .60));
      }
    }
    if (light.length) out.additive.push({ data: light, mode: 4 });
    if (edges.length) out.additive.push({ data: edges, mode: 1 });
    if (sparkles.length) out.additive.push({ data: sparkles, mode: 9 });
    out.diagnostics = { topology: 'dense-open-folded-light-sheets-with-negative-slits-and-fine-luminous-edges', legacyApiKey: 'swords', fragments: fade > .001 ? count : 0, positionSpace: 'separated 3D local formation; frozen basis and origin on stop', stopping: 'formation first gathers, then slowly disperses and all sheets fade out', carrierRemainsAfterStop: false, historyTrail: 'none', parameterUsage: { speed: 'counter-orbit rate + light modulation', life: 'stop fade duration in host', width: 'individual sheet breadth', strands: 'five times the strand value controls separated sheet count', spread: 'formation radius', shellLength: 'sheet extent + distributed axial spacing', glow: 'eroded interior, thin contour and small sparkles', coreColor: 'fine highlight edge', edgeColor: 'translucent plate tint' }, limitations: ['open procedural planes with animated fragment erosion, not modeled objects or true volumetric shards', 'no physical refraction', 'packet-center alpha sorting; intersecting planes remain an approximation'] };
    return out;
  }

  function water(ctx) {
    const out = newParts(), p = ctx.params, stop = stableStop(ctx, 'waterStop'), light = [], sparkles = [], count = clamp(Math.round(p.strands), 2, 12);
    const clock = flowClock(ctx, 'waterClock');
    const panels = Math.max(3, Math.ceil(count * .70)), fade = Math.pow(ctx.motionFade, 1.35), panelLength = p.shellLength * (.60 + p.speed * .020);
    let drops = 0;
    if (fade > .003) for (let j = 0; j < panels; j++) {
      const phase = random(ctx.seed, 0, j + 700) * TAU;
      const data = [], angle = j / panels * TAU + .17 * Math.sin(clock * .55 + phase), handed = j % 2 ? 1 : -1, radial = (.30 + p.spread * .48) * (1 + stop.age * .22);
      const anchor = add(stop.pos, [0, -.6 * stop.age * stop.age, 0]);
      function point(v, w) {
        const halfArc = Math.min(.30 + p.width * .95, TAU / panels * .37);
        const theta = angle + w * halfArc * (.40 + .60 * Math.sin(v * Math.PI)) + handed * Math.sin(v * Math.PI) * .24;
        const flow = v * 12 - clock * (2.6 + p.speed * .09) + phase;
        const wrinkles = .020 * Math.sin(flow + w * 3.1) + .009 * Math.sin(flow * 2.73 - w * 6) + .004 * Math.sin(flow * 6.3 + w * 13);
        const r = radial * (.60 + .42 * Math.sin(v * Math.PI)) + wrinkles * (.4 + .6 * Math.sin(v * Math.PI));
        const q = offset(anchor, stop.basis, panelLength * (.22 - v) + w * .10, Math.cos(theta) * r, Math.sin(theta) * r);
        q[1] -= v * v * (.18 + p.width * .5) + stop.age * v * .15;
        return q;
      }
      // Neighbouring cells evaluate the same colour, opacity and normal at a shared
      // parameter coordinate. Per-cell midpoint opacity/Fresnel left visible seams.
      const filmTint = v => {
        const envelope = Math.pow(Math.max(0, Math.sin(Math.PI * v)), .6);
        const thickness = .74 + .26 * Math.sin(v * 16 - clock * 1.9 + phase);
        return color(mix(ctx.edge, ctx.core, .18 + v * .45), fade * envelope * thickness * .35);
      };
      const filmNormal = (v, w) => {
        const du = sub(point(clamp(v + .0001, 0, 1), w), point(clamp(v - .0001, 0, 1), w));
        const dv = sub(point(v, clamp(w + .0001, -1, 1)), point(v, clamp(w - .0001, -1, 1)));
        return unit(cross(du, dv));
      };
      for (let v = 0; v < 40; v++) for (let w = 0; w < 8; w++) {
        const a = v / 40, b = (v + 1) / 40, c = w / 4 - 1, d = (w + 1) / 4 - 1;
        panelQuad(ctx, data, [point(a, c), point(b, c), point(b, d), point(a, d)], null, [[c, a], [c, b], [d, b], [d, a]],
          [filmTint(a), filmTint(b), filmTint(b), filmTint(a)],
          [filmNormal(a, c), filmNormal(b, c), filmNormal(b, d), filmNormal(a, d)]);
      }
      const center = point(.5, 0); out.alpha.push({ data, mode: 6, center });
      if (p.glow > 0) for (let thread = 0; thread < 7; thread++) {
        const line = [], w = thread / 6 * 2 - 1;
        for (let k = 0; k <= 36; k++) line.push(point(k / 36, w + .045 * Math.sin(k * .32 + phase + clock)));
        const tint = mix(ctx.edge, ctx.core, thread === 0 || thread === 6 ? .82 : .35);
        linePath(ctx, light, line, (.003 + p.width * .015) * (thread === 0 || thread === 6 ? 1.5 : .6), mul(tint, 1.8), fade * p.glow * (thread % 3 ? .20 : .48), ctx.edge);
        const at = point(fract(clock * (.20 + p.speed * .009) + j * .173 + thread * .117), w), size = .012 + p.width * .035;
        sprite(ctx, sparkles, at, size * 1.8, size, color(mul(ctx.core, 2.1), fade * p.glow * .45), phase);
      }
    }
    const dropLife = Math.min(p.life, 3.0);
    for (const h of births(ctx, Math.max(1, Math.round(5 - count * .25)), 120, dropLife)) {
      const age = ctx.time - h.t, b = basisFor(h.dir || ctx.dir), r0 = random(ctx.seed, h.t, 31), r1 = random(ctx.seed, h.t, 32);
      const a = r0 * TAU, radius = .30 + p.spread * .56;
      const center = offset(h.p, b, -age * p.speed * .075, Math.cos(a) * (radius + age * (.16 + r1 * .25)), Math.sin(a) * (radius + age * .27));
      center[1] += age * (.35 + r1 * .35) - .75 * age * age;
      if (center[1] < -.2) continue;
      const size = .007 + p.width * (.025 + r1 * .04), alpha = ageFade(age, dropLife, .8) * .54;
      // Tiny optical streaks instead of polygonal droplet models.
      sprite(ctx, sparkles, center, size * (1.2 + r1), size * (2 + age), color(mul(mix(ctx.edge, ctx.core, .72), 1.7), alpha * p.glow), a); drops++;
      if (p.glow > 0) {
        const streak = [];
        for (let k = 0; k < 4; k++) {
          const t = Math.max(0, age - k * (.013 + r1 * .012));
          const at = offset(h.p, b, -t * p.speed * .075, Math.cos(a) * (radius + t * (.16 + r1 * .25)), Math.sin(a) * (radius + t * .27));
          at[1] += t * (.35 + r1 * .35) - .75 * t * t; streak.push(at);
        }
        linePath(ctx, light, streak, size, mul(ctx.core, 1.5), alpha * p.glow * .70, ctx.edge);
      }
    }
    if (light.length) out.additive.push({ data: light, mode: 1 });
    if (sparkles.length) out.additive.push({ data: sparkles, mode: 9 });
    out.diagnostics = { topology: 'wrinkled-open-water-films-with-surface-flow-and-ballistic-microstreams', openMembranes: fade > .003 ? panels : 0, membraneGrid: [40, 8], droplets: drops, gravity: [0, -1.5, 0], oldDropsUseAge: true, stopping: 'film anchors freeze, open and fall; historical optical droplets keep ballistic motion', positionSpace: 'local open films + world-space birth-position light streams', parameterUsage: { speed: 'film axial stretch and surface advection', life: 'droplet lifetime up to 3 seconds and stopped film fade', width: 'sheet arc width and microstream width', strands: 'membrane and droplet density', spread: 'sheet radius and droplet outward velocity', shellLength: 'open sheet axial extent', glow: 'thin flowing highlights and small sparkles', coreColor: 'film edge and sparkle', edgeColor: 'transparent film body' }, limitations: ['surface transparency approximation; no scene refraction or caustics', 'ballistic optical streaks with prescribed gravity, not a liquid solver', 'packet-center alpha sorting'] };
    return out;
  }

  function vortex(ctx) {
    const out = newParts(), p = ctx.params, stop = stableStop(ctx, 'vortexStop'), clock = flowClock(ctx, 'vortexClock');
    const strips = Math.max(3, Math.round(clamp(Math.round(p.strands), 2, 12) * .42)), turns = .74 + Math.min(p.life, 8) * .075;
    const length = p.shellLength * (1.05 + p.speed * .035) * (1 + stop.age * .10), expansion = 1 + stop.age * .19;
    const fade = Math.pow(ctx.motionFade, 1.1), light = [], stars = [];
    if (fade > .001) {
      for (let j = 0; j < strips; j++) for (let layer = 0; layer < 3; layer++) {
        const alpha = [], layerScale = .87 + layer * .14;
        const phase = j * 2.399 - clock * (.53 + p.speed * .041) + layer * .21 + random(ctx.seed, 0, j + 90) * .13;
        function center(t) {
          const a = phase + t * (turns + .24 * Math.sin(j*1.7)) * TAU + Math.sin(t*5.1+j)*.19;
          const radial = expansion * layerScale * ((.28 + p.spread * .28) + (1 - t) * (.32 + p.spread * .5)) + .014 * Math.sin(t * 29 - clock * 2.4 + j * 1.7) + .007 * Math.sin(t * 67 + clock * 1.3);
          return offset(stop.pos, stop.basis, .60 - t * length, Math.cos(a) * radial, Math.sin(a) * radial);
        }
        function frame(t) {
          const t0 = Math.max(0, t - .002), t1 = Math.min(1, t + .002), a = phase + t * (turns + .24 * Math.sin(j*1.7)) * TAU + Math.sin(t*5.1+j)*.19;
          const normal = unit(add(mul(stop.basis[1], Math.cos(a)), mul(stop.basis[2], Math.sin(a))));
          const tangent = unit(sub(center(t1), center(t0))), side = unit(cross(tangent, normal));
          return { p: center(t), normal, side };
        }
        const halfWidth = (.14 + p.width * .75) * (layer === 1 ? .62 : 1.9) * expansion;
        function segment(data, a, b, opacity, tint, scale = 1) {
          const fa = frame(a), fb = frame(b), wa = halfWidth * scale * (.45 + .55 * Math.sin(Math.PI * a)), wb = halfWidth * scale * (.45 + .55 * Math.sin(Math.PI * b));
          const ca = color(tint, opacity * Math.pow(Math.max(0, Math.sin(Math.PI * a)), .4));
          const cb = color(tint, opacity * Math.pow(Math.max(0, Math.sin(Math.PI * b)), .4));
          for (let w = 0; w < 4; w++) {
            const lo = w * .5 - 1, hi = (w + 1) * .5 - 1;
            const q = [add(fa.p, mul(fa.side, lo * wa)), add(fb.p, mul(fb.side, lo * wb)), add(fb.p, mul(fb.side, hi * wb)), add(fa.p, mul(fa.side, hi * wa))];
            const uv = [[lo, a], [lo, b], [hi, b], [hi, a]], normals = [fa.normal, fb.normal, fb.normal, fa.normal];
            for (const k of [0, 1, 2, 0, 2, 3]) emit(ctx, data, q[k], k === 0 || k === 3 ? ca : cb, uv[k], normals[k]);
          }
        }
        for (let i = 0; i < 144; i++) {
          const a = i / 144, b = (i + 1) / 144;
          segment(alpha, a, b, fade * (layer === 1 ? .23 : .085), mix(ctx.edge, ctx.core, layer === 1 ? .31 : .05));
        }
        out.alpha.push({ data: alpha, mode: 4, center: center(.5) });
        if (layer !== 1) continue;
        // Broad veils establish the volume; continuous fine filaments establish
        // the entrained flow without making every veil a solid luminous spring.
        for (let strand = 0; strand < 2; strand++) {
          const curve = [], shift = (strand - .5) * halfWidth * .55;
          for (let k = 0; k <= 144; k++) {
            const t = k / 144, f = frame(t);
            curve.push(add(f.p, mul(f.side, shift + Math.sin(t * 17 + j) * halfWidth * .11)));
          }
          linePath(ctx, light, curve, .003 + p.width * .012, mul(mix(ctx.edge, ctx.core, .22), 1.4), fade * p.glow * .18, ctx.edge);
        }
        // Bright short sections really advance along the funnel axis in geometry.
        for (let k = 0; k < 4; k++) {
          const head = fract(clock * (.13 + p.speed * .009) + k / 4 + j * .071), f = frame(head), size = .016 + p.width * .035;
          sprite(ctx, stars, f.p, size, size, color(mul(ctx.core, 2), fade * p.glow * .35 * Math.sin(Math.PI * head)));
          const streak = [];
          for (let n = 0; n <= 12; n++) streak.push(center(clamp(head - n * .0027, 0, 1)));
          linePath(ctx, light, streak, .007 + p.width * .018, mul(ctx.core, 1.9), fade * p.glow * .72, ctx.edge);
        }
      }
      if (light.length) out.additive.push({ data: light, mode: 1 });
      if (stars.length) out.additive.push({ data: stars, mode: 9 });
    }
    out.diagnostics = { topology: 'layered-open-conical-swirl-veils-with-continuous-entrainment-filaments', helicalStrips: strips, layersPerStrip: 3, veilGrid: [144, 4], turnsPerStrip: turns, axialLength: length, openEnds: true, hollowAxis: true, positionSpace: 'local direction basis frozen on stop', stopping: 'angular and axial flow slows while soft veils expand and fade', historyTrail: 'none: this family is a local open tunnel', parameterUsage: { speed: 'axis length and entrainment advection', life: 'coil turns and stop duration', width: 'veil width and thin filament size', strands: 'separated coils and veil density', spread: 'front and back cone radii', shellLength: 'longitudinal funnel length', glow: 'entrainment fine strands and traveling sparks', coreColor: 'thin traveling highlights', edgeColor: 'broad translucent veil' }, limitations: ['layered procedural surfaces suggest volume; not a fluid solver', 'intertwining alpha sheets are approximate'] };
    return out;
  }

  root.DunguangExtraForms = { smoke, swords, water, vortex };
})(typeof window !== 'undefined' ? window : globalThis);
