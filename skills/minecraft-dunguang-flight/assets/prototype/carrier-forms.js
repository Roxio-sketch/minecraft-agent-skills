/* Original procedural energy surfaces. No creature, vehicle or opaque model geometry. */
(() => {
  'use strict';
  const TAU = Math.PI * 2;
  const clamp = (v, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, v));
  const parameterConsumers = {
    speed: 'local flow speed, longitudinal stretch and host history transport',
    life: 'immutable world-trail age and host local stop-fade duration',
    width: 'continuous sheet thickness / transverse band width',
    strands: 'fan layers or independently delayed shear pulses',
    spread: 'fan radius or separation of the two open shear lobes',
    shellLength: 'longitudinal sheet reach',
    glow: 'emission opacity of the second geometric pass',
    coreColor: 'flow-ridge RGB',
    edgeColor: 'translucent sheet RGB'
  };
  function localPoint(ctx, x, y, z) {
    const [F, N, B] = ctx.basis;
    return ctx.add(ctx.pos, ctx.add(ctx.mul(F, x), ctx.add(ctx.mul(N, y), ctx.mul(B, z))));
  }
  function color(ctx, t) { return ctx.edge.map((v, i) => v * (1 - t) + ctx.core[i] * t); }
  function surface(ctx, out, fn, nu, nv, alpha, tint) {
    const rows = [];
    for (let i = 0; i <= nu; i++) {
      const u = i / nu, row = [];
      for (let j = 0; j <= nv; j++) {
        const v = j / nv, p = fn(u, v);
        const du = ctx.sub(fn(clamp(u + .0001), v), fn(clamp(u - .0001), v));
        const dv = ctx.sub(fn(u, clamp(v + .0001)), fn(u, clamp(v - .0001)));
        let n = ctx.cross(du, dv);
        if (Math.hypot(...n) < 1e-9) n = ctx.basis[2];
        const fade = Math.pow(Math.max(0, Math.sin(Math.PI * u)), .55) * Math.pow(Math.max(0, Math.sin(Math.PI * v)), .7);
        row.push({p, n:ctx.norm(n), uv:[u * 2 - 1, v], color:[...tint, alpha * fade]});
      }
      rows.push(row);
    }
    for (let i = 0; i < nu; i++) for (let j = 0; j < nv; j++) {
      const q = [rows[i][j], rows[i+1][j], rows[i+1][j+1], rows[i][j+1]];
      for (const k of [0,1,2,0,2,3]) ctx.vertex(out, q[k].p, q[k].color, q[k].uv, q[k].n);
    }
  }
  // Anchors are derived once when a history record is first consumed. They remain in
  // world space when the current emitter turns, stops, or changes width/spread.
  function cachedExhaust(ctx, width, offset, back) {
    const cache = ctx.state.carrierAnchors || (ctx.state.carrierAnchors = {});
    const keep = new Set(), result = [];
    let lastSide = null;
    for (const h of ctx.history) {
      if (ctx.time - h.t >= ctx.params.life) continue;
      const key = h.seg + ':' + h.t.toFixed(7); keep.add(key);
      if (!cache[key]) {
        const F = ctx.norm(h.dir), reference = Math.abs(ctx.dot(F, [0,1,0])) > .96 ? [0,0,1] : [0,1,0];
        let N = ctx.norm(ctx.cross(F, reference));
        if (lastSide && ctx.dot(N, lastSide) < 0) N = ctx.mul(N, -1);
        const U = ctx.norm(ctx.cross(N, F));
        const center = ctx.add(ctx.add(h.p, ctx.mul(F, -back)), ctx.mul(U, offset));
        cache[key] = {left:ctx.add(center, ctx.mul(N, -width)), right:ctx.add(center, ctx.mul(N, width)), N};
      }
      lastSide = cache[key].N; result.push({h, ...cache[key]});
    }
    for (const key of Object.keys(cache)) if (!keep.has(key)) delete cache[key];
    return result;
  }
  function exhaust(ctx, out, width, offset, back, strength) {
    const poses = cachedExhaust(ctx, width, offset, back);
    for (let i = 1; i < poses.length; i++) {
      const a = poses[i-1], b = poses[i];
      if (a.h.seg !== b.h.seg) continue;
      const fa = Math.pow(clamp(1 - (ctx.time - a.h.t) / ctx.params.life), 1.7);
      const fb = Math.pow(clamp(1 - (ctx.time - b.h.t) / ctx.params.life), 1.7);
      for (const side of ['left','right']) ctx.appendRibbon(out, a[side], b[side], .015 + ctx.params.width * .055,
        [...ctx.edge, fa * strength * ctx.params.glow], [...ctx.core, fb * strength * ctx.params.glow], a.h.t, b.h.t);
    }
    return poses.length;
  }
  function finish(alpha, emission, trail, details) {
    return {
      opaque: [], alpha,
      additive: [...(emission.length ? [{data:emission, mode:4}] : []), ...(trail.length ? [{data:trail, mode:1}] : [])],
      diagnostics: {shaderOnly:true, opaqueModel:false, closedShell:false, worldAnchored:true,
        parameterConsumers:{...parameterConsumers}, unsupportedParameters:[], ...details}
    };
  }
  // API key retained for saved study parameters. This is a flow fan, not a bird.
  function bird(ctx) {
    const p = ctx.params, speed = p.speed ?? 8, fade = clamp(ctx.motionFade), alpha = [], emission = [], trail = [];
    const layers = Math.max(2, Math.round(2 + p.strands * .35));
    const reach = p.shellLength * (.78 + speed * .035), radius = .72 + p.spread * 1.25;
    if (fade > .0001) for (const sign of [-1,1]) for (let k = 0; k < layers; k++) {
      const phase = ctx.time * (1.4 + speed * .08) + k * 1.36 + (sign < 0 ? 1.12 : 0);
      const sheet = [], layer = k / layers;
      const flow = (u,v) => {
        // All leaves are continuous, open warped patches. A central gap is present
        // for every layer; no head, neck, torso, shoulder or feather meshes exist.
        const r = .38 + u * radius * (1 - layer * .12);
        const sweep = (v - .5) * (1.02 + p.width * 1.5);
        const bend = Math.sin(phase - v * 3.8 + u * 1.4) * (.13 + u * .27);
        const x = .18 - reach * v - r * r * .17 + Math.sin(phase - v * 5) * .06 * u;
        const y = sign * (r + Math.cos(sweep) * p.width * .24);
        const z = r * Math.sin(sweep) * .75 + bend * r + (layer - .4) * (.18 + p.width * .45);
        return localPoint(ctx, x, y, z);
      };
      surface(ctx, sheet, flow, 12, 28, fade * (.19 / Math.sqrt(layers)), color(ctx, .08));
      alpha.push({data:sheet, mode:6, center:flow(.5,.5)});
      // Separate translucent/noise-emission pass; this is not screen-space bloom.
      surface(ctx, emission, flow, 12, 28, fade * p.glow * .30 / Math.sqrt(layers), color(ctx, .6));
    }
    const poses = exhaust(ctx, trail, .43, -.08, reach * .55, .42);
    return finish(alpha, emission, trail, {topology:'open-offset-fan-sheets', energyFans:2, sheetLayers:layers*2,
      centralGap:true, historyTrails:2, historyAnchorPoses:poses, animation:'counterphase-continuous-fan-flow'});
  }
  // API key retained for saved study parameters. Two open shear fronts, no vessel.
  function vessel(ctx) {
    const p = ctx.params, speed = p.speed ?? 8, fade = clamp(ctx.motionFade), alpha = [], emission = [], trail = [];
    const pulses = Math.max(2, Math.round(1 + p.strands * .35));
    const length = .8 + p.shellLength * .7, separation = .42 + p.spread * .68;
    if (fade > .0001) for (const sign of [-1,1]) for (let k = 0; k < pulses; k++) {
      const phase = ctx.time * (.50 + speed * .055) + k / pulses + (sign < 0 ? .31 : 0);
      const travel = phase - Math.floor(phase), pulseAlpha = Math.pow(Math.sin(Math.PI * travel), .7);
      const asymmetry = sign > 0 ? 1 : .79, radius = (.42 + p.spread * .52) * asymmetry;
      const sheet = [];
      const flow = (u,v) => {
        // Each curved front spans less than half a revolution. They are offset in
        // x/z and never complete an enclosing tube, hull, sail or rigid frame.
        const angle = (u - .5) * Math.PI * .94;
        const r = radius * (.65 + travel * .70) + (v - .5) * (.13 + p.width * .48);
        const x = length * (.52 - travel) - v * (.34 + p.width * .9) - Math.cos(angle) * .24 + (sign < 0 ? -.48 : .15);
        const y = sign * (separation + Math.cos(angle) * r);
        const z = Math.sin(angle) * r + sign * (.21 + Math.sin(phase * TAU) * .12) + Math.sin(v * Math.PI) * .09;
        return localPoint(ctx, x, y, z);
      };
      surface(ctx, sheet, flow, 30, 7, fade * pulseAlpha * .30 / Math.sqrt(pulses), color(ctx, .12));
      alpha.push({data:sheet, mode:6, center:flow(.5,.5)});
      surface(ctx, emission, flow, 30, 7, fade * pulseAlpha * p.glow * .55 / Math.sqrt(pulses), color(ctx, .68));
    }
    const poses = exhaust(ctx, trail, separation, .02, length * .25, .70);
    return finish(alpha, emission, trail, {topology:'separated-asymmetric-open-shear-arcs', shearLobes:2,
      independentlyDelayedPulses:pulses*2, negativeSpace:true, historyTrails:2, historyAnchorPoses:poses,
      animation:'countershifted-pressure-pulses'});
  }
  window.DunguangCarrierForms = {bird, vessel};
})();
