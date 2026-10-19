/* Offline design preview. Path math mirrors Paths.java; pixels are an authoring approximation. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.TrailLab = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const kinds = ['straight', 'arc', 'sine', 'helix', 'zigzag', 'lightning'];
  const sections = ['ribbon', 'cross', 'tube', 'blade'];
  const profiles = ['soft', 'solid', 'double', 'dash', 'pulse', 'plasma', 'rune'];
  const keys = (value, expected, at) => {
    if (!value || Array.isArray(value) || typeof value !== 'object' || Object.keys(value).sort().join('|') !== expected.slice().sort().join('|'))
      throw new Error(`${at}: 字段必须为 ${expected.join(', ')}`);
  };
  const num = (value, lo, hi, at, integer = false) => {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < lo || value > hi || (integer && !Number.isInteger(value)))
      throw new Error(`${at}: 需要 ${lo}..${hi}${integer ? ' 的整数' : ''}`);
    return value;
  };
  const clone = value => JSON.parse(JSON.stringify(value));
  function validatePath(p) {
    keys(p, ['schema', 'kind', 'length', 'amplitude', 'cycles', 'speed', 'seed', 'samples', 'branches', 'branchLength'], 'path');
    num(p.schema, 1, 1, 'path.schema', true);
    if (!kinds.includes(p.kind)) throw new Error('path.kind 未实现');
    for (const [k, lo, hi] of [['length', .1, 32], ['amplitude', 0, 8], ['cycles', 0, 32], ['speed', -20, 20], ['branchLength', .05, 1]]) num(p[k], lo, hi, `path.${k}`);
    for (const [k, lo, hi] of [['seed', -1e9, 1e9], ['samples', 8, 256], ['branches', 0, 4]]) num(p[k], lo, hi, `path.${k}`, true);
    return p;
  }
  function stops(array, type) {
    if (!Array.isArray(array) || array.length < 2 || array.length > 32) throw new Error(`${type}: 需要 2..32 个节点`);
    let prev = -1;
    for (const stop of array) {
      keys(stop, type === 'taper' ? ['at', 'value'] : ['at', 'color', 'alpha'], type);
      num(stop.at, 0, 1, `${type}.at`);
      if (stop.at <= prev) throw new Error(`${type}: 节点位置必须递增`);
      prev = stop.at;
      if (type === 'taper') num(stop.value, 0, 8, 'taper.value');
      else {
        if (!/^#[0-9a-fA-F]{6}$/.test(stop.color)) throw new Error('colors.color 需要 #RRGGBB');
        num(stop.alpha, 0, 1, 'colors.alpha');
      }
    }
    if (array[0].at !== 0 || array[array.length - 1].at !== 1) throw new Error(`${type}: 首尾必须覆盖 0..1`);
  }
  function validateRecipe(r) {
    keys(r, ['schema', 'name', 'section', 'sides', 'sampling', 'layers'], 'recipe');
    num(r.schema, 1, 1, 'recipe.schema', true);
    if (typeof r.name !== 'string' || !r.name.trim()) throw new Error('recipe.name 不能为空');
    if (!sections.includes(r.section)) throw new Error('recipe.section 未实现');
    num(r.sides, 3, 64, 'recipe.sides', true);
    const s = r.sampling;
    keys(s, ['lifetime', 'minDistance', 'maxStep', 'breakDistance', 'maxPoints'], 'sampling');
    for (const [k, lo, hi] of [['lifetime', .05, 30], ['minDistance', 0, 5], ['maxStep', .001, 10], ['breakDistance', .001, 10000]]) num(s[k], lo, hi, `sampling.${k}`);
    num(s.maxPoints, 2, 4096, 'sampling.maxPoints', true);
    if (!(s.minDistance < s.maxStep && s.maxStep <= s.breakDistance)) throw new Error('sampling 距离阈值顺序错误');
    if (!Array.isArray(r.layers) || r.layers.length < 1 || r.layers.length > 16) throw new Error('layers 需要 1..16 层');
    r.layers.forEach((l, i) => {
      const at = `layers[${i}]`;
      keys(l, ['width', 'blend', 'profile', 'texture', 'repeat', 'scroll', 'gain', 'fadePower', 'taper', 'colors'], at);
      for (const [k, lo, hi] of [['width', .001, 64], ['repeat', 0, 100], ['scroll', -100, 100], ['gain', 0, 16], ['fadePower', .01, 16]]) num(l[k], lo, hi, `${at}.${k}`);
      if (!['alpha', 'additive'].includes(l.blend) || !profiles.includes(l.profile)) throw new Error(`${at}: 混合或图案未实现`);
      if (typeof l.texture !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(l.texture) || l.texture.includes('..')) throw new Error(`${at}: 纹理资源 ID 无效`);
      stops(l.taper, 'taper'); stops(l.colors, 'colors');
    });
    return r;
  }
  function validateProject(p) {
    keys(p, ['schema', 'recipe', 'path'], 'project');
    num(p.schema, 1, 1, 'project.schema', true);
    validateRecipe(p.recipe); validatePath(p.path);
    return p;
  }
  function hash(i, seed) {
    const mask = (1n << 64n) - 1n;
    let x = (BigInt(i) * 0x9e3779b9n + BigInt(seed) * 0x85ebca6bn) & mask;
    x = (x ^ (x >> 16n)) & mask; x = (x * 0x7feb352dn) & mask;
    x = (x ^ (x >> 15n)) & mask; x = (x * 0x846ca68bn) & mask;
    x = (x ^ (x >> 16n)) & mask;
    return Number(x & 0xffffn) / 32767.5 - 1;
  }
  function pathPoint(p, s, time) {
    const a = p.amplitude, w = s * p.cycles * Math.PI * 2 + time * p.speed * Math.PI * 2;
    let y = 0, z = 0;
    if (p.kind === 'arc') y = a * 4 * s * (1 - s);
    if (p.kind === 'sine') y = a * Math.sin(w) * Math.sin(Math.PI * s);
    if (p.kind === 'helix') { y = a * Math.cos(w); z = a * Math.sin(w); }
    if (p.kind === 'zigzag') y = a * (2 / Math.PI) * Math.asin(Math.sin(w)) * Math.sin(Math.PI * s);
    if (p.kind === 'lightning') {
      const slice = Math.floor(s * p.cycles * 8), frame = Math.floor(time * Math.abs(p.speed) * 5), fade = Math.sin(Math.PI * s);
      y = a * fade * hash(slice + frame * 199, p.seed);
      z = a * .36 * fade * hash(slice + 811 + frame * 199, p.seed);
    }
    return { x: p.length * s, y, z };
  }
  function paths(p, time) {
    validatePath(p);
    if (!Number.isFinite(time)) throw new Error('时间必须有限');
    const main = Array.from({ length: p.samples + 1 }, (_, i) => pathPoint(p, i / p.samples, time));
    const strands = [{ points: main, widthScale: 1 }];
    for (let b = 0; b < p.branches; b++) {
      const start = Math.max(1, Math.min(p.samples - 2, Math.round(p.samples * (.32 + .48 * (b + 1) / (p.branches + 1)))));
      const origin = main[start], direction = hash(b + 201, p.seed) >= 0 ? 1 : -1;
      const size = p.amplitude * Math.max(.18, p.branchLength);
      const points = Array.from({ length: 13 }, (_, j) => {
        const f = j / 12;
        return { x: origin.x + p.length * p.branchLength * .35 * f,
          y: origin.y + direction * size * f,
          z: origin.z + size * .38 * f * Math.sin((b + 1) * 2.3) };
      });
      strands.push({ points, widthScale: .45 });
    }
    return strands;
  }
  function interp(stops, t, key) {
    t = Math.max(0, Math.min(1, t));
    for (let i = 1; i < stops.length; i++) if (t <= stops[i].at) {
      const a = stops[i - 1], b = stops[i], f = (t - a.at) / (b.at - a.at);
      return a[key] + (b[key] - a[key]) * f;
    }
    return stops[stops.length - 1][key];
  }
  function colorAt(stops, t) {
    t = Math.max(0, Math.min(1, t));
    let a = stops[0], b = stops[stops.length - 1], f = 0;
    for (let i = 1; i < stops.length; i++) if (t <= stops[i].at) {
      a = stops[i - 1]; b = stops[i]; f = (t - a.at) / (b.at - a.at); break;
    }
    const ca = [1, 3, 5].map(k => parseInt(a.color.slice(k, k + 2), 16));
    const cb = [1, 3, 5].map(k => parseInt(b.color.slice(k, k + 2), 16));
    return { rgb: ca.map((v, i) => Math.round(v + (cb[i] - v) * f)), alpha: a.alpha + (b.alpha - a.alpha) * f };
  }
  return { kinds, sections, profiles, clone, validateRecipe, validatePath, validateProject, hash, pathPoint, paths, interp, colorAt };
});
