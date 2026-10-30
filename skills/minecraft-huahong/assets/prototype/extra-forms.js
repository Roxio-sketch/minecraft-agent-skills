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







  root.DunguangExtraForms = { smoke };
})(typeof window !== 'undefined' ? window : globalThis);
