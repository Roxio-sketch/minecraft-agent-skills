import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {validateCatalog, getPalette, applyPaletteId} from './palettes.mjs';
const catalog = validateCatalog(JSON.parse(await readFile(new URL('./nine-flames.json', import.meta.url), 'utf8')));
let mapped = 0;
for (const name of ['alchemy-vfx-lab','fire-coil-lab']) {
  const raw = JSON.parse(await readFile(new URL(`../${name}/current-recipe.json`, import.meta.url), 'utf8'));
  const schemaText = await readFile(new URL(`../${name}/schema.js`, import.meta.url), 'utf8');
  const {validateRecipe} = await import(`data:text/javascript;base64,${Buffer.from(schemaText).toString('base64')}`);
  for (const palette of catalog.palettes) {
    const before = structuredClone(raw);
    const changed = applyPaletteId(raw, catalog, palette.id);
    const keys = raw.schema.startsWith('alchemy-vfx/') ? ['fireCore','fireMid','fireEdge'] : ['coreColor','midColor','edgeColor'];
    assert.deepEqual(before, raw);
    for (const k of Object.keys(raw)) if (!keys.includes(k)) assert.deepEqual(changed[k], raw[k]);
    assert.deepEqual(keys.map(k => changed[k]), [palette.fireCore, palette.fireMid, palette.fireEdge]);
    validateRecipe(changed);
    mapped++;
  }
}
const reject = [
  c => c.schema='flame-palettes/2', c => c.extra=true, c => c.defaultId='slot10',
  c => c.palettes.pop(), c => c.palettes[1].id='slot1', c => c.palettes[1].name=' ',
  c => c.palettes[1].fireCore='#fff', c => c.palettes[1].fireEdge=c.palettes[1].fireCore,
  c => c.palettes[1].extra=true
];
for (const mutate of reject) { const c = structuredClone(catalog); mutate(c); assert.throws(() => validateCatalog(c)); }
assert.throws(() => getPalette(catalog, 'slot10'));
assert.throws(() => applyPaletteId({schema:'__proto__'}, catalog, 'slot1'));
assert.throws(() => applyPaletteId({schema:'fire-coil/2', coreColor:'#ffffff'}, catalog, 'slot1'));
const reordered = structuredClone(catalog); reordered.palettes.reverse();
assert.deepEqual(validateCatalog(reordered), catalog);
console.log(JSON.stringify({catalogSchema:catalog.schema, palettes:catalog.palettes.length, mappedRecipes:mapped, rejectedCases:reject.length+3, nonColourParametersPreserved:true, inputUnchanged:true},null,2));
