/* Shared editable colour ramps. This module does not fetch or save recipes. */
export const CATALOG_SCHEMA = 'flame-palettes/1';
export const PALETTE_IDS = Object.freeze(Array.from({length:9}, (_, i) => `slot${i + 1}`));
export const COLOR_KEYS = Object.freeze(['fireCore', 'fireMid', 'fireEdge']);
const ITEM_KEYS = Object.freeze(['id', 'name', 'style', ...COLOR_KEYS]);
const MAPS = Object.freeze({
  'alchemy-vfx/2': Object.freeze({fireCore:'fireCore', fireMid:'fireMid', fireEdge:'fireEdge'}),
  'alchemy-vfx/1': Object.freeze({fireCore:'fireCore', fireMid:'fireMid', fireEdge:'fireEdge'}),
  'fire-coil/1': Object.freeze({fireCore:'coreColor', fireMid:'midColor', fireEdge:'edgeColor'}),
  'fire-coil/2': Object.freeze({fireCore:'coreColor', fireMid:'midColor', fireEdge:'edgeColor'})
});
const own = (v, k) => Object.prototype.hasOwnProperty.call(v, k);
const object = (v, label) => { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error(`${label}须为 JSON 对象。`); };
const exact = (v, keys, label) => {
  const unknown = Object.keys(v).filter(k => !keys.includes(k));
  const missing = keys.filter(k => !own(v, k));
  if (unknown.length || missing.length) throw new Error(`${label}字段不匹配：多余 ${unknown.join('、') || '无'}；缺少 ${missing.join('、') || '无'}。`);
};
export function validatePalette(value) {
  object(value, '色板'); exact(value, ITEM_KEYS, '色板');
  if (!PALETTE_IDS.includes(value.id)) throw new Error('色板 ID 须为 slot1–slot9。');
  for (const key of ['name','style']) {
    if (typeof value[key] !== 'string' || !value[key].trim() || value[key].length > 120) throw new Error(`${key}须为 1–120 字的文本。`);
  }
  const result = {id:value.id, name:value.name.trim(), style:value.style.trim()};
  for (const key of COLOR_KEYS) {
    if (typeof value[key] !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(value[key])) throw new Error(`${key}须为 #RRGGBB。`);
    result[key] = value[key].toLowerCase();
  }
  if (new Set(COLOR_KEYS.map(k => result[k])).size !== 3) throw new Error('火核、中层和外焰须保留三个不同色阶。');
  return result;
}
export function validateCatalog(value) {
  object(value, '色板目录'); exact(value, ['schema','defaultId','palettes'], '色板目录');
  if (value.schema !== CATALOG_SCHEMA) throw new Error(`只支持 ${CATALOG_SCHEMA}。`);
  if (!PALETTE_IDS.includes(value.defaultId)) throw new Error('默认色板 ID 须为 slot1–slot9。');
  if (!Array.isArray(value.palettes) || value.palettes.length !== 9) throw new Error('须提供完整九种色板。');
  const byId = new Map(value.palettes.map(p => { const clean = validatePalette(p); return [clean.id, clean]; }));
  if (byId.size !== 9 || PALETTE_IDS.some(id => !byId.has(id))) throw new Error('九个色板 ID 须各出现一次。');
  return {schema:CATALOG_SCHEMA, defaultId:value.defaultId, palettes:PALETTE_IDS.map(id => byId.get(id))};
}
export function getPalette(catalog, id) {
  const clean = validateCatalog(catalog);
  const found = clean.palettes.find(p => p.id === (id ?? clean.defaultId));
  if (!found) throw new Error(`未知色板 ID：${id}`);
  return {...found};
}
export function applyPalette(recipe, palette) {
  object(recipe, '特效配方');
  const map = MAPS[recipe.schema];
  if (!own(MAPS, recipe.schema)) throw new Error(`不支持的配方 schema：${recipe.schema}`);
  const clean = validatePalette(palette);
  const result = {...recipe};
  for (const [source, target] of Object.entries(map)) {
    if (!own(recipe, target) || typeof recipe[target] !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(recipe[target])) throw new Error(`原配方缺少合法颜色字段 ${target}。`);
    result[target] = clean[source];
  }
  return result;
}
export function applyPaletteId(recipe, catalog, id) { return applyPalette(recipe, getPalette(catalog, id)); }
