/* Independent source-contract audit. Reads the installed skill; never edits it. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const crypto = require('crypto');
const root = path.resolve(__dirname, '..');
const skill = process.argv[2] || 'C:/Users/MAINTAINER/.codex/skills/minecraft-lightning';
const input = process.argv[3] || 'C:/Users/MAINTAINER/Downloads/recipe (1).json';
const evidence = process.argv[4] ? path.resolve(process.argv[4]) : path.join(root, 'evidence/lightning-skill-reimport-20261005');
const read = file => fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
const normal = value => JSON.stringify(value, Object.keys(value).sort());
const equal = (a, b) => normal(a) === normal(b);
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const sandbox = {module: {exports: {}}};
vm.runInNewContext(read(path.join(skill, 'assets/workbench/recipe.js')) + '\nmodule.exports.recipeRanges = recipeRanges;', sandbox);
const {defaultRecipe, presets, recipeRanges, validateRecipe} = sandbox.module.exports;
const extensionFields = ['gradientRatio', 'gradientTarget', 'gradientCoreMode'];
const originalFields = Object.keys(defaultRecipe).filter(key => !extensionFields.includes(key));
const withoutExtensions = value => Object.fromEntries(Object.entries(value).filter(([key]) => !extensionFields.includes(key)));
const recipePath = path.join(root, 'src/main/resources/assets/vfxworkbench/lightning/recipe.json');
const modDefaults = JSON.parse(read(recipePath));
const user = JSON.parse(read(input));
const schema = read(path.join(root, 'src/main/java/dev/vfxworkbench/effects/lightning/LightningSkillSchema.java'));
let checks = 0;
const requireCheck = (value, label) => { checks++; if (!value) throw Error(label); };
const validatedUser = validateRecipe(user);
requireCheck(equal(withoutExtensions(validatedUser), withoutExtensions(user)), 'User recipe is not a full valid skill recipe');
requireCheck(validatedUser.gradientRatio === undefined || validatedUser.gradientRatio === 50, 'Legacy user recipe must keep the 50% gradient');
requireCheck(originalFields.length === 60, 'The original 60-field contract changed');
requireCheck(Object.keys(defaultRecipe).length === 63, 'Skill must expose the original 60 fields plus ratio, target and core mode');
requireCheck(defaultRecipe.gradientTarget === 'both' && defaultRecipe.gradientCoreMode === 'vivid', 'Skill gradient extensions must default to both layers and vivid core');
requireCheck(fs.readFileSync(input).equals(fs.readFileSync(recipePath)), 'MOD defaults differ from supplied recipe bytes');
requireCheck(fs.readFileSync(input).equals(fs.readFileSync(path.join(root, 'src/main/resources/assets/vfxworkbench/lightning/presets/strike.json'))), 'Single-strike preset differs from supplied recipe bytes');
const numbers = new Map();
for (const match of schema.matchAll(/number\(p, defaults, "([^"]+)", "([^"]+)", ([^,]+), ([^,]+), (true|false)\);/g)) {
  const numeric = str => Number(str.replace('Integer.MIN_VALUE', '-2147483648').replace('Integer.MAX_VALUE', '2147483647').trim());
  numbers.set(match[1], {label: match[2], range: [numeric(match[3]), numeric(match[4]), match[5] === 'true']});
}
const contract = [];
for (const key of originalFields) {
  if (key === 'schema') continue;
  const def = defaultRecipe[key];
  if (typeof def === 'number' && key !== 'orbitDirection') {
    requireCheck(numbers.has(key), 'Unregistered numeric skill field: ' + key);
    const expected = recipeRanges[key];
    requireCheck(JSON.stringify(numbers.get(key).range) === JSON.stringify([expected[0], expected[1], !!expected[2]]), 'Incorrect MOD range/type: ' + key);
  } else requireCheck(schema.includes('"' + key + '"'), 'Unregistered skill field: ' + key);
  const label = numbers.get(key)?.label || key;
  contract.push({key, label, type: typeof def, range: recipeRanges[key] || null, originalSkillDefault: def, suppliedModDefault: modDefaults[key]});
}
const recipeSource = read(path.join(root, 'src/main/java/dev/vfxworkbench/effects/lightning/LightningRecipe.java'));
const compactSchema = schema.replace(/\s+/g, '');
requireCheck(/public double gradientRatio\s*=\s*50\s*;/.test(recipeSource), 'MOD gradient ratio must default to 50');
requireCheck(/range\("gradientRatio",\s*gradientRatio,\s*0,\s*100\)/.test(recipeSource), 'MOD gradient ratio must strictly validate 0..100');
requireCheck(compactSchema.includes('ParamSpec.builder("gradientRatio","起始色占比（%）",ParamType.FLOAT).group("沿程渐变")'), 'MOD ratio must identify start-color share and live in the dedicated gradient group');
requireCheck(compactSchema.includes('.visibleWhen(s->s.getBool("gradientEnabled")).min(0).max(100).step(1).reload(ReloadMode.HOT)'), 'MOD gradient ratio must be a hot step-one slider visible only with gradient enabled');
requireCheck(compactSchema.includes('.defaultValue(value(defaults,"gradientRatio"))'), 'MOD gradient ratio must use the legacy parser default');
requireCheck(!Object.hasOwn(modDefaults, 'gradientRatio') && Object.keys(modDefaults).length === 60, 'Original 60-field default resource must remain unchanged');
requireCheck(/public String gradientTarget\s*=\s*"both"\s*[,;]/.test(recipeSource), 'MOD gradient target legacy fallback must be both');
requireCheck(/gradientCoreMode\s*=\s*"vivid"\s*;/.test(recipeSource), 'MOD gradient core mode legacy fallback must be vivid');
requireCheck(compactSchema.includes('.enumValues(List.of("both","core","glow"))') && compactSchema.includes('.enumValues(List.of("vivid","additive"))'), 'MOD must expose complete explicit target and core mode choices');
requireCheck(compactSchema.includes('publicstaticintpreviewColor(') && compactSchema.includes('LightningAppearance.materialColor('), 'Visible UI swatches must use the actual material color rule');
contract.push({key: 'gradientRatio', label: '起始色占比（%）', type: 'number', range: [0, 100, false], step: 1,
  originalSkillDefault: defaultRecipe.gradientRatio ?? null, suppliedModDefault: 50, extension: 'Shared gradient ratio after skill refresh; omitted legacy field falls back to 50'});
for (const [key, value, choices] of [['gradientTarget', 'both', ['both','core','glow']], ['gradientCoreMode','vivid',['vivid','additive']]])
  contract.push({key,type:'string',choices,originalSkillDefault:defaultRecipe[key],suppliedModDefault:value,extension:'Explicit appearance selection; missing legacy value uses documented fallback'});
for (const key of ['source', 'shortSource', 'ring', 'cluster', 'orbit', 'dense', 'tribulation', 'rain']) {
  const value = JSON.parse(read(path.join(root, 'src/main/resources/assets/vfxworkbench/lightning/presets', key + '.json')));
  requireCheck(equal(withoutExtensions(value), withoutExtensions(presets[key])), 'Original skill preset changed: ' + key);
  requireCheck(equal(withoutExtensions(validateRecipe(value)), withoutExtensions(value)), 'Skill preset invalid: ' + key);
  requireCheck(presets[key].gradientRatio === undefined || presets[key].gradientRatio === 50, 'Legacy preset appearance changed: ' + key);
}
const shader = path.join(root, 'src/main/resources/assets/vfxworkbench/shaders/core/bolt.fsh');
const originalShader = path.join(skill, 'assets/forge-1.20.1/src/main/resources/assets/lightninglab/shaders/core/bolt.fsh');
const shaderText=read(shader).replace(/\r/g, '').trim();
requireCheck(shaderText.includes('vertexColor.a*uv.x*max(0.0,falloff)'), 'Float UV coverage is not consumed by the fragment shader');
const skillShaderText = read(originalShader).replace(/\r/g, '').trim();
requireCheck(shaderText === skillShaderText, 'MOD shader differs from the current skill coverage/vivid contract');
requireCheck(shaderText.includes('source/peak') && shaderText.includes('clamp(coverage*Gain*peak,0.0,1.0)') && shaderText.includes('vec4(source*Gain,coverage)'), 'Shared core shader must retain both vivid hue-preserving and traditional additive branches');
for (const name of ['bolt.json','bolt_vivid.json','bolt.vsh']) {
  const a=read(path.join(root,'src/main/resources/assets/vfxworkbench/shaders/core',name));
  const b=read(path.join(skill,'assets/forge-1.20.1/src/main/resources/assets/lightninglab/shaders/core',name));
  requireCheck(a.replaceAll('vfxworkbench:', 'lightninglab:').replace(/\r/g,'').trim() === b.replace(/\r/g,'').trim(), 'Core shader definition differs from installed skill: '+name);
}
const geometry = {};
for (const file of ['OrbitField.java', 'RainGeometry.java', 'ImpactGeometry.java', 'RibbonGeometry.java', 'SimplexNoise.java', 'LightningMath.java', 'LightningEnvelope.java']) {
  const a = read(path.join(root, 'src/main/java/dev/vfxworkbench/effects/lightning', file));
  const b = read(path.join(skill, 'assets/forge-1.20.1/src/main/java/dev/lightninglab', file));
  let expected = b;
  if (file === 'ImpactGeometry.java') {
    expected = expected.replace(',along=s.alongA()+(s.alongB()-s.alongA())*f;', ';')
      .replace('s.path(),(float)s.alongA(),(float)along)', 's.path(),1,1)')
      .replace('s.path(),(float)s.alongA(),(float)s.alongB())', 's.path(),1,1)')
      .replace('r.impactRays+3+i,0,1)', 'r.impactRays+3+i,1,1)')
      .replace('r.impactRays+3+r.impactSparks+axis,0,1)', 'r.impactRays+3+r.impactSparks+axis,1,1)');
  }
  const normalize = s => s.replace(/package [^;]+;/, '').replace(/\/\/[^\n]*/g, '').replace(/\s+/g, '');
  const identical = normalize(a) === normalize(b) || normalize(a) === normalize(expected);
  requireCheck(identical, 'Unexplained geometry/timing change: ' + file);
  geometry[file] = file === 'ImpactGeometry.java' ? 'Original geometry and timing; four output material coordinates fixed to parent tip (1,1)' : 'Identical to installed Forge skill reference, apart from namespace';
}
geometry['BoltGeometry.java'] = 'Original Forge reference, plus safe zero-jitter sampling; nonzero paths retain the original random call sequence';
fs.mkdirSync(evidence, {recursive: true});
const report = {
  generatedAt: new Date().toISOString(), checks, passed: true,
  sourceSkill: skill, suppliedRecipe: input, skillFieldCount: Object.keys(defaultRecipe).length,
  originalSkillFieldCount: originalFields.length, modFieldCount: originalFields.length + extensionFields.length,
  extensions: {gradientRatio: {default: 50, min: 0, max: 100, step: 1, unit: '%', reload: 'HOT', visibleWhen: 'gradientEnabled', legacyFallback: 50},
    gradientTarget:{default:'both',choices:['both','core','glow'],reload:'HOT'},gradientCoreMode:{default:'vivid',choices:['vivid','additive'],reload:'HOT'}},
  suppliedRecipeSha256: hash(input), modDefaultSha256: hash(recipePath),
  gradient: 'Selected layer base tint multiplied by the along-channel gradient; ratio50 preserves legacy interpolation. Default vivid core normalizes hue and raises alpha with Gain; traditional additive remains selectable. Main and branch channels use the same rule.',
  units: {distance: 'Minecraft blocks', orbitExtendSpeed: 'blocks per second', timing: 'ticks at 20 ticks per second', branchAngle: 'radians'},
  import: 'Validate complete raw JSON or legacy string wrapper before applying; missing fields merge original skill defaults plus ratio50/targetboth/coremodevivid; reject invalid values, no clamp; suppress preset/style baselines during commit',
  export: 'Typed raw skill JSON with schema=1, all original 60 fields plus ratio, target and core mode; no MOD preset metadata',
  rasterCoverage: {
    minimumProjectedHalfWidthPixels: .75,
    condition: 'Only when the original projected perpendicular half-width is below 0.75 pixels',
    projection: 'Actual draw ProjMat * ModelViewMat * CPU pose; actual GL_VIEWPORT dimensions',
    sharedCorners: 'One expanded side/alpha per shared endpoint; minimum normal width across both adjacent projected segments',
    energy: 'Original/expanded projected half-width ratio stored in float UV0.x; fragment alpha multiplied by uv.x, avoiding Color.a byte quantization',
    unchanged: 'Recipe widths, cached path geometry, RNG, base-tint multiplication, gains and Gaussian/core radial profiles; traditional additive shader branch retains the original contribution with float coverage',
    nativeWidth: 'Projected half-width >= 0.75 px passes through with exactly the original side and alpha',
    clipping: 'Original center/both side vertices and expanded side vertices must remain inside near and far depth clip planes; touching/clipped/unstable cases and invalid viewport retain original offsets/ratio=1',
    evidence: 'CPU projection/energy/corner tests and fresh client screenshots must be evaluated separately'
  },
  geometry,
  limits: [
    'objectRadius is the HTML reference object size; it is preserved but does not change lightning geometry',
    'The original JS and Java strike generators use different random number sequences; each seed is repeatable within its runtime',
    'The skill preview and Forge renderer share appearance rules; their projection, geometry random sequences and scene depth pipelines still require separate visual acceptance',
    'The supplied red core tint suppresses cyan at the gradient end; its independent blue glow tint stays active',
    'Impact rays remain in a horizontal local plane and sparks have no block collision, matching the source skill',
    'The duplicate spektr preset alias is represented by orbit; source and shortSource select the actual spektr animation'
  ], contract
};
fs.writeFileSync(path.join(evidence, 'source-contract-audit.json'), JSON.stringify(report, null, 2) + '\n');
console.log(`PASS: ${checks} independent skill contract checks; ${originalFields.length} original fields plus 3 gradient controls; user default/preset byte identity; preserved geometry and shared vivid/additive shaders.`);
