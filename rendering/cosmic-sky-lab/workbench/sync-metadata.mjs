// Development helper: keep Python's metadata and downloadable recipes aligned with the UI.
import { readFile, writeFile } from 'node:fs/promises';
import { SCHEMA, FIELDS, PACKS, COLORS, ANGLES, makeRecipe, PRESETS } from './recipe.js';

const outputs = {
  'schema.json': { schema: SCHEMA, fields: FIELDS, packs: PACKS, colors: COLORS, angles: ANGLES },
  'default.json': makeRecipe(),
  'presets.json': PRESETS,
};
for (const [name, value] of Object.entries(outputs)) {
  const file = new URL(name, import.meta.url);
  if (process.argv.includes('--check')) {
    if (JSON.stringify(JSON.parse(await readFile(file, 'utf8'))) !== JSON.stringify(value)) {
      throw new Error(`${name} differs from recipe.js; run sync-metadata.mjs`);
    }
  } else {
    await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
  }
}
console.log(process.argv.includes('--check') ? 'Metadata matches recipe.js.' : 'Metadata synchronized.');
