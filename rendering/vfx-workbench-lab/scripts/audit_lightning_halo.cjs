/*
 * Static source and final JAR contract audit for the replacement halo path.
 * Usage: node scripts/audit_lightning_halo.cjs [--jar path] [--recipe path]
 *        [--evidence directory]
 * Complements audit_lightning_skill.cjs; neither audit certifies GPU visuals.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const zlib = require('zlib');
const root = path.resolve(__dirname, '..');
const options = {
  jar: path.join(root, 'build/libs/vfx-workbench-forge-1.20.1-0.1.0.jar'),
  recipe: 'C:/Users/MAINTAINER/Downloads/recipe (1).json',
  evidence: path.join(root, 'evidence/camera-glow-fix-20261005')
};
for (let i = 2; i < process.argv.length; i += 2) {
  const key = process.argv[i].replace(/^--/, '');
  if (!Object.hasOwn(options, key) || !process.argv[i + 1])
    throw Error('Expected --jar path, --recipe path or --evidence directory');
  options[key] = path.resolve(process.argv[i + 1]);
}
const read = name => fs.readFileSync(path.join(root, name));
const text = name => read(name).toString('utf8').replace(/^\uFEFF/, '');
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const compact = value => value.replace(/\/\*[\s\S]*?\*\/|\/\/[^\r\n]*/g, '').replace(/\s+/g, '');
const checks = [];
function check(value, category, label) {
  checks.push({category, label, passed: !!value});
  if (!value) throw Error(label);
}
function body(source, marker) {
  const start = source.indexOf(marker);
  if (start < 0) throw Error('Missing source block: ' + marker);
  const brace = source.indexOf('{', start);
  let depth = 0;
  for (let i = brace; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}' && --depth === 0) return source.slice(brace + 1, i);
  }
  throw Error('Unclosed source block: ' + marker);
}
function ordered(source, markers) {
  let previous = -1;
  return markers.every(marker => {
    const current = source.indexOf(marker, previous + 1);
    if (current < 0) return false;
    previous = current;
    return true;
  });
}

// Use the ZIP central directory rather than shell extraction or optional tools.
function jarEntries(file) {
  const zip = fs.readFileSync(file);
  let end = -1;
  for (let p = zip.length - 22; p >= Math.max(0, zip.length - 65557); p--)
    if (zip.readUInt32LE(p) === 0x06054b50 && p + 22 + zip.readUInt16LE(p + 20) === zip.length) { end = p; break; }
  if (end < 0) throw Error('JAR ZIP directory is missing or incomplete: ' + file);
  if (zip.readUInt16LE(end + 4) || zip.readUInt16LE(end + 6)) throw Error('Multi-volume JAR is unsupported');
  const count = zip.readUInt16LE(end + 10), entries = new Map();
  let cursor = zip.readUInt32LE(end + 16);
  for (let i = 0; i < count; i++) {
    if (zip.readUInt32LE(cursor) !== 0x02014b50) throw Error('Invalid JAR central entry');
    const flags = zip.readUInt16LE(cursor + 8), method = zip.readUInt16LE(cursor + 10);
    const size = zip.readUInt32LE(cursor + 20), originalSize = zip.readUInt32LE(cursor + 24);
    const nameLength = zip.readUInt16LE(cursor + 28), extraLength = zip.readUInt16LE(cursor + 30), commentLength = zip.readUInt16LE(cursor + 32);
    const name = zip.toString('utf8', cursor + 46, cursor + 46 + nameLength);
    const offset = zip.readUInt32LE(cursor + 42);
    if (entries.has(name)) throw Error('Duplicate JAR entry: ' + name);
    entries.set(name, {flags, method, size, originalSize, offset});
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return {
    sha256: sha256(zip), size: zip.length, count,
    get(name) {
      const entry = entries.get(name);
      if (!entry) return null;
      if (entry.flags & 1 || zip.readUInt32LE(entry.offset) !== 0x04034b50) throw Error('Invalid/encrypted JAR entry: ' + name);
      const offset = entry.offset + 30 + zip.readUInt16LE(entry.offset + 26) + zip.readUInt16LE(entry.offset + 28);
      const packed = zip.subarray(offset, offset + entry.size);
      const unpacked = entry.method === 0 ? packed : entry.method === 8 ? zlib.inflateRawSync(packed) : null;
      if (!unpacked || unpacked.length !== entry.originalSize) throw Error('Invalid JAR entry size/compression: ' + name);
      return unpacked;
    }
  };
}

function classConstants(bytes) {
  if (bytes.readUInt32BE(0) !== 0xcafebabe) throw Error('Invalid JVM class file');
  const strings = new Set(), integers = new Set();
  const count = bytes.readUInt16BE(8);
  let cursor = 10;
  for (let i = 1; i < count; i++) {
    const tag = bytes[cursor++];
    if (tag === 1) {
      const length = bytes.readUInt16BE(cursor); cursor += 2;
      strings.add(bytes.toString('utf8', cursor, cursor + length)); cursor += length;
    } else if (tag === 3) { integers.add(bytes.readInt32BE(cursor)); cursor += 4;
    } else if (tag === 4) cursor += 4;
    else if (tag === 5 || tag === 6) { cursor += 8; i++;
    } else if ([7, 8, 16, 19, 20].includes(tag)) cursor += 2;
    else if ([9, 10, 11, 12, 17, 18].includes(tag)) cursor += 4;
    else if (tag === 15) cursor += 3;
    else throw Error('Unsupported class constant tag: ' + tag);
  }
  return {strings, integers, majorVersion: bytes.readUInt16BE(6)};
}

const java = 'src/main/java/dev/vfxworkbench/effects/lightning/';
const resources = 'src/main/resources/';
const shaders = 'assets/vfxworkbench/shaders/core/';
const sourceNames = [
  java + 'HaloGeometry.java', java + 'LightningHalo.java', java + 'LightningRender.java',
  java + 'LightningAppearance.java',
  java + 'LightningRecipe.java', java + 'LightningSkillSchema.java',
  java + 'LightningClient.java',
  ...['json','vsh','fsh'].map(ext => resources + shaders + 'bolt.' + ext),
  resources + shaders + 'bolt_vivid.json',
  ...['bolt_halo', 'bolt_halo_composite'].flatMap(name => ['json', 'vsh', 'fsh'].map(ext => resources + shaders + name + '.' + ext)),
  resources + 'assets/vfxworkbench/lightning/recipe.json',
  resources + 'assets/vfxworkbench/lightning/presets/strike.json'
];
const renderer = compact(text(java + 'LightningRender.java'));
const halo = compact(text(java + 'LightningHalo.java'));
const geometry = compact(text(java + 'HaloGeometry.java'));
const appearance = compact(text(java + 'LightningAppearance.java'));
const recipeSource = compact(text(java + 'LightningRecipe.java'));
const schemaSource = compact(text(java + 'LightningSkillSchema.java'));
const client = compact(text(java + 'LightningClient.java'));
const coreFsh = compact(text(resources + shaders + 'bolt.fsh'));
const bridge = body(renderer, 'privatestaticvoidhalos(');
const draw = body(halo, 'staticvoiddraw(');
const pathDraw = body(draw, 'for(varpath:paths)');
const profileFsh = compact(text(resources + shaders + 'bolt_halo.fsh'));
const profileVsh = compact(text(resources + shaders + 'bolt_halo.vsh'));
const compositeFsh = compact(text(resources + shaders + 'bolt_halo_composite.fsh'));
const compositeVsh = compact(text(resources + shaders + 'bolt_halo_composite.vsh'));

check(appearance.includes('returnhalo?r.glowWidth/baseWidth:2;'), 'parameters', 'Original physical halo/core width ratio is preserved');
check(bridge.includes('double scale='.replace(/\s/g, '') + 'LightningAppearance.ribbonScale(r,true,baseWidth);'), 'parameters', 'Halo bridge consumes the original width contract');
check(bridge.includes('newHaloGeometry.Point(node.p().subtract(camera),node.width()*scale,node.along(),node.alpha())'), 'parameters', 'Native node width, along and envelope are passed without recipe overrides');
check(geometry.includes('LightningAppearance.colorAt(recipe,true,'), 'parameters', 'Halo still uses its independent base tint and along gradient');
check(!/recipe\.\w+\s*(?:=(?!=)|\+=|-=|\*=|\/=|\+\+|--)/.test(geometry), 'parameters', 'Geometry does not mutate recipe fields');
check(!/\b(?:SplittableRandom|Random)\b/.test(geometry), 'parameters', 'Halo tessellation introduces no RNG or topology reseeding');
check((renderer.match(/halos\(paths\.values\(\)/g) || []).length === 2, 'integration', 'Both orbit and bolt rendering use the new halo bridge');
check(!/ribbon\([^;]*,true,/.test(renderer), 'integration', 'Shared miter ribbons are not used for halo rendering');
check((renderer.match(/ribbon\(path,2,pose,camera,false,r,view\)/g) || []).length === 2, 'integration', 'Core remains on its existing width-two ribbon path');
check((renderer.match(/BUFFER.endBatch\(type\(false,r\)\)/g) || []).length === 2 && renderer.includes('BUFFER.getBuffer(type(halo,r))'), 'integration', 'Core vertices and both batch flushes select the same recipe-aware render type');
check(renderer.includes('booleanuseVivid=!halo&&LightningAppearance.vividCore(recipe)') && renderer.includes('.setTransparencyState(useVivid?VIVID_TRANSPARENCY:ADDITIVE_TRANSPARENCY)'), 'integration', 'Vivid only selects the hue-preserving core path, traditional additive remains explicit');
check(renderer.includes('GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ZERO,GlStateManager.DestFactor.ONE'), 'integration', 'Vivid core uses matching color alpha-over blend while preserving scene alpha');
check(renderer.includes('selected.safeGetUniform("Vivid").set(useVivid?1f:0f)'), 'shaders', 'The selected shader receives the matching vivid branch uniform');
check(bridge.includes('LightningHalo.draw(strokes,r,view.clip(),pose.last().pose(),view.width(),view.height())'), 'integration', 'Halo receives the actual clip, CPU pose and viewport');
check(!geometry.includes('RibbonGeometry') && !geometry.toLowerCase().includes('miter'), 'geometry', 'Halo geometry has no shared miter dependency');
check(geometry.includes('newMatrix4d(actualClip)') && geometry.includes('newMatrix4d(clip).invert()'), 'geometry', 'Halo uses actual inverse VP for depth-preserving offsets');
check(geometry.includes('clipSegment(originalA,originalB,clip)') && geometry.includes('convexAfterFloatTransport(positions,clip,width,height)'), 'geometry', 'Clipping and float-transport convexity guards protect emitted geometry');
check(geometry.includes('staticfinaldoubleMIN_HALF_PIXELS=.75;') && geometry.includes('nativeA/renderedA') && geometry.includes('nativeB/renderedB'), 'geometry', 'Subpixel strip coverage retains the one-dimensional energy ratio');
check(geometry.includes('(nativeX/renderedX)*(nativeY/renderedY)'), 'geometry', 'Round joins retain the two-dimensional area energy ratio');
check(geometry.includes('for(inti=1;i+1<points.size();i++)') && geometry.includes('if(forward>0&&Math.abs(turn)<1e-7)continue;'), 'geometry', 'Terminal nodes and straight subdivisions do not acquire round disks');
check(halo.includes('"bolt_halo"),DefaultVertexFormat.POSITION_COLOR_TEX)') && halo.includes('"bolt_halo_composite"),DefaultVertexFormat.POSITION_TEX)'), 'shaders', 'Both new shader IDs register with their matching vertex formats');
check(halo.includes('@Mod.EventBusSubscriber(') && halo.includes('@SubscribeEventpublicstaticvoidshaders('), 'shaders', 'Halo shader registration is attached to the Forge MOD bus');
check(pathDraw.includes('HaloGeometry.build(path,clip,width,height,recipe)'), 'composition', 'Each individual path is tessellated and merged separately');
check(ordered(pathDraw, ['glScissor(bounds.x(),bounds.y(),bounds.width(),bounds.height())', 'glClear(GL11.GL_COLOR_BUFFER_BIT)', 'glBlendEquationSeparate(GL14.GL_MAX,GL14.GL_MAX)', 'vertices(mesh.body(),pose)', 'vertices(mesh.round(),pose)', 'main.bindWrite(true)', 'glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD)', 'BufferUploader.drawWithShader(BUFFER.end())']), 'composition', 'Each path clears its bounds, unions MAX coverage, then adds to the main scene');
check(pathDraw.includes('blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE)'), 'composition', 'Composite adds emission while preserving scene alpha');
check(draw.indexOf('GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST') >= 0 && draw.indexOf('GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST') < draw.indexOf('for(varpath:paths)'), 'composition', 'Scene depth is copied before rendering individual halo paths');
check(pathDraw.includes('RenderSystem.enableDepthTest()') && pathDraw.includes('RenderSystem.depthFunc(GL11.GL_LEQUAL)') && pathDraw.includes('RenderSystem.depthMask(false)'), 'composition', 'Halo uses scene depth without writing it');
check(draw.includes('scratch.width!=main.width||scratch.height!=main.height') && draw.includes('scratch.isStencilEnabled()!=main.isStencilEnabled()'), 'composition', 'Scratch allocation follows main target resize and stencil mode');
check(draw.includes('try(Statestate=newState())') && halo.includes('glBlendEquationSeparate(eqr,eqa)') && halo.includes('BLEND_CACHE.set(null,previousBlend)'), 'composition', 'Drawing restores saved GL blend state and the ShaderInstance blend cache');
check(profileFsh.includes('exp(-5.0*d*d)-exp(-5.0)'), 'shaders', 'Replacement halo retains the original Gaussian profile');
check(profileFsh.includes('clamp(vertexColor.rgb*Gain,0.0,1.0)*vertexColor.a*coverage*falloff') && profileFsh.includes('fragColor=vec4(emission,0.0)'), 'shaders', 'MAX stores the original fixed-point additive contribution rather than raw RGB');
check(profileVsh.includes('flatoutfloatroundCoverage;') && profileVsh.includes('roundCoverage=abs(UV0.x)') && profileFsh.includes('flatinfloatroundCoverage;') && profileFsh.includes('uv.x/max(roundCoverage,1e-20)'), 'shaders', 'Round coverage is flat and the signed disk coordinate is reconstructed');
check(profileVsh.includes('gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0)'), 'shaders', 'Halo shader matches actual clip matrix convention');
check(compositeFsh.includes('vec4(texture(Sampler0,uv).rgb,0.0)') && compositeVsh.includes('gl_Position=vec4(Position,1.0)'), 'shaders', 'Composite samples the emission target with clip-space fullscreen geometry');
check(coreFsh.includes('floatcoverage=vertexColor.a*uv.x*max(0.0,falloff)') && coreFsh.includes('fragColor=vec4(source*Gain,coverage)'), 'shaders', 'Core retains float coverage and the original additive contribution');
check(coreFsh.includes('source/peak') && coreFsh.includes('clamp(coverage*Gain*peak,0.0,1.0)'), 'shaders', 'Vivid core preserves RGB ratios and converts gain into bounded opacity');
check(appearance.includes('returnusesGradient(r,false)&&"vivid".equals(r.gradientCoreMode)') && appearance.includes('enabled&&("both".equals(target)||(halo?"glow":"core").equals(target))'), 'parameters', 'Target gating matches explicit core/glow/both selection and disabled gradients retain additive behavior');
for(const name of ['bolt','bolt_vivid']) {
  const definition=JSON.parse(text(resources+shaders+name+'.json'));
  check(definition.vertex==='vfxworkbench:bolt'&&definition.fragment==='vfxworkbench:bolt', 'shaders', name+' shares the exact common bolt shader source');
  check(definition.uniforms.some(u=>u.name==='Vivid'&&u.values[0]===(name==='bolt_vivid'?1:0)), 'shaders', name+' includes the matching vivid uniform default');
  check(definition.blend.srcrgb==='srcalpha'&&definition.blend.dstrgb===(name==='bolt_vivid'?'1-srcalpha':'one')&&definition.blend.srcalpha==='zero'&&definition.blend.dstalpha==='one', 'shaders', name+' ShaderInstance blend agrees with its RenderType');
}

for (const [name, attributes, samplers, uniforms] of [
  ['bolt_halo', ['Position', 'Color', 'UV0'], [], ['ModelViewMat', 'ProjMat', 'Gain', 'RoundJoint']],
  ['bolt_halo_composite', ['Position', 'UV0'], ['Sampler0'], []]
]) {
  const definition = JSON.parse(text(resources + shaders + name + '.json'));
  check(definition.vertex === 'vfxworkbench:' + name && definition.fragment === 'vfxworkbench:' + name, 'shaders', name + ' JSON references its registered VSH/FSH');
  check(JSON.stringify(definition.attributes) === JSON.stringify(attributes), 'shaders', name + ' attribute format matches renderer registration');
  check(JSON.stringify(definition.samplers.map(item => item.name)) === JSON.stringify(samplers), 'shaders', name + ' sampler bindings are complete');
  check(JSON.stringify(definition.uniforms.map(item => item.name)) === JSON.stringify(uniforms), 'shaders', name + ' uniforms are complete');
}

const supplied = fs.readFileSync(options.recipe);
const defaultBytes = read(resources + 'assets/vfxworkbench/lightning/recipe.json');
const presetBytes = read(resources + 'assets/vfxworkbench/lightning/presets/strike.json');
const recipe = JSON.parse(defaultBytes.toString('utf8').replace(/^\uFEFF/, ''));
check(defaultBytes.equals(supplied), 'defaults', 'Complete MOD default recipe remains byte-identical to the supplied recipe');
check(presetBytes.equals(supplied), 'defaults', 'Complete strike preset remains byte-identical to the supplied recipe');
check(Object.keys(recipe).length === 60 && recipe.schema === 1, 'defaults', 'All 60 original recipe fields and schema remain present');
check(recipe.glowWidth === .07 && recipe.strikeWidth === .038 && recipe.coreGain === 3.8 && recipe.glowGain === 2.4 && recipe.seed === -1468807610 && recipe.strikeHeight === 51, 'defaults', 'Supplied widths, gains, seed and 51-block height remain unchanged');
check(!Object.hasOwn(recipe, 'gradientRatio') && recipeSource.includes('publicdoublegradientRatio=50;'), 'extension', 'Original 60-field JSON remains unchanged and omitted gradientRatio defaults to 50');
check(recipeSource.includes('range("gradientRatio",gradientRatio,0,100)'), 'extension', 'Gradient ratio strictly validates the full 0..100 range');
check(schemaSource.includes('ParamSpec.builder("gradientRatio","起始色占比（%）",ParamType.FLOAT).group("沿程渐变")') && schemaSource.includes('.visibleWhen(s->s.getBool("gradientEnabled")).min(0).max(100).step(1).reload(ReloadMode.HOT)'), 'extension', 'Gradient ratio is an immediate start-color percent slider in its explicit group');
check(recipeSource.includes('gradientTarget="both",gradientCoreMode="vivid"') && recipeSource.includes('Set.of("both","core","glow").contains(gradientTarget)') && recipeSource.includes('Set.of("vivid","additive").contains(gradientCoreMode)'), 'extension', 'Targets and core modes have documented legacy defaults and strict enum validation');
check(!Object.hasOwn(recipe,'gradientTarget')&&!Object.hasOwn(recipe,'gradientCoreMode'), 'defaults', 'New target/mode controls do not rewrite the original supplied 60-field resources');
const appearanceFields=['coreColor','glowColor','coreGain','glowGain','gradientEnabled','gradientStart','gradientEnd','gradientRatio','gradientTarget','gradientCoreMode'];
const copy=body(client,'privatestaticvoidcopyAppearance(');
const fieldDeclaration=client.slice(client.indexOf('APPEARANCE_FIELDS='),client.indexOf(';',client.indexOf('APPEARANCE_FIELDS=')));
check(appearanceFields.every(field=>fieldDeclaration.includes('"'+field+'"')&&copy.includes('target.'+field+'=source.'+field)), 'hot', 'All 10 appearance fields are classified HOT and copied to rain children');
const refresh=body(client,'privatestaticvoidrefresh(');
check(refresh.includes('if(visualOnly){if(rain!=null)for(varb:rain)copyAppearance(recipe,b.recipe());return;}'), 'hot', 'Actual HOT refresh returns before geometry generation while updating every rain child');

const jar = jarEntries(options.jar);
const packaged = [];
const constants = new Map();
const packagePath = 'dev/vfxworkbench/effects/lightning/';
const classNames = [
  'HaloGeometry', 'HaloGeometry$Point', 'HaloGeometry$Vertex', 'HaloGeometry$Bounds', 'HaloGeometry$Mesh',
  'HaloGeometry$Screen', 'HaloGeometry$Segment', 'HaloGeometry$Extent', 'LightningHalo', 'LightningHalo$State',
  'LightningRender', 'LightningRender$Node', 'LightningRender$CoverageView', 'LightningAppearance', 'LightningClient',
  'LightningRecipe', 'LightningSkillSchema', 'RibbonGeometry', 'RibbonCoverage', 'RibbonCoverage$Join'
];
for (const name of classNames) {
  const entry = packagePath + name + '.class';
  const bytes = jar.get(entry);
  check(!!bytes, 'jar', 'JAR includes ' + entry);
  const pool = classConstants(bytes); constants.set(name, pool);
  check(pool.majorVersion === 61, 'jar', name + ' targets the Java 17 class format');
  packaged.push({entry, sha256: sha256(bytes), bytes: bytes.length, majorVersion: pool.majorVersion});
}
for (const source of sourceNames.filter(name => name.startsWith(resources))) {
  const entry = source.slice(resources.length), bytes = jar.get(entry);
  check(!!bytes && bytes.equals(read(source)), 'jar', 'JAR resource exactly matches current source: ' + entry);
  packaged.push({entry, sha256: sha256(bytes), bytes: bytes.length});
}
check(constants.get('LightningRender').strings.has(packagePath + 'LightningHalo') && constants.get('LightningRender').strings.has(packagePath + 'HaloGeometry$Point'), 'jar', 'Packaged renderer actually calls the replacement halo bridge');
check(constants.get('LightningHalo').strings.has('bolt_halo') && constants.get('LightningHalo').strings.has('bolt_halo_composite'), 'jar', 'Packaged halo class registers both new shader IDs');
check(constants.get('LightningHalo').integers.has(32776) && constants.get('LightningHalo').integers.has(32774), 'jar', 'Packaged halo bytecode contains GL_MAX and GL_FUNC_ADD equations');
check(constants.get('HaloGeometry').strings.has('org/joml/Matrix4d') && !constants.get('HaloGeometry').strings.has(packagePath + 'RibbonGeometry'), 'jar', 'Packaged geometry uses actual inverse projection and has no shared miter dependency');
check(constants.get('LightningRecipe').strings.has('gradientRatio') && constants.get('LightningSkillSchema').strings.has('gradientRatio'), 'jar', 'Packaged recipe and schema include the new ratio field');
check(constants.get('LightningAppearance').strings.has('gradientRatio'), 'jar', 'Packaged appearance consumes the new ratio rather than only exporting it');
check(constants.get('LightningRender').strings.has('bolt_vivid')&&constants.get('LightningRender').strings.has('Vivid'), 'jar', 'Packaged renderer registers and controls the vivid shader');
check(['LightningRecipe','LightningSkillSchema','LightningAppearance','LightningClient'].every(name=>['gradientTarget','gradientCoreMode'].every(field=>constants.get(name).strings.has(field))), 'jar', 'Packaged parser/schema/render material/HOT client all consume both new controls');

const report = {
  generatedAt: new Date().toISOString(), passed: true, checkCount: checks.length,
  scope: 'Static source contracts and content of the exact final JAR; complements the original skill audit and CPU/GPU regression evidence.',
  commands: {default: 'node scripts/audit_lightning_halo.cjs', explicitJar: 'node scripts/audit_lightning_halo.cjs --jar <artifact.jar>'},
  recipe: {supplied: options.recipe, fieldCount: Object.keys(recipe).length, originalFieldCount: Object.keys(recipe).length, modFieldCount: Object.keys(recipe).length + 3,
    sha256: sha256(supplied), defaultAndStrikePresetByteIdentical: true,
    extension: {gradientRatio: {default: 50, min: 0, max: 100, step: 1, unit: '%', reload: 'HOT', legacyFallback: 50},
      gradientTarget:{default:'both',choices:['both','core','glow'],reload:'HOT'},gradientCoreMode:{default:'vivid',choices:['vivid','additive'],reload:'HOT'}}},
  contracts: {
    physicalWidth: 'Native channel width times the original glowWidth/baseWidth ratio; recipe, RNG, along gradient and envelope are preserved.',
    geometry: 'Independent projected convex trapezoids; actual near/far clipping and inverse VP; no halo miter; interior round joints with float coverage.',
    energy: 'Strips use native/rendered half-width; round joints use the product of the two axis ratios; fixed-point RGB Gain clamps before multiplying envelope and Gaussian.',
    composition: 'Each path clears a bounded scratch region, MAX merges its coverage with copied scene depth, then emission is added to the main target; different paths still add.',
    core: 'Existing core ribbon, radial profile and physical width. Vivid uses alpha-over hue preservation; traditional additive branch remains selectable and is used for disabled/core-excluded gradients.',
    hot: 'All 10 appearance fields return before geometry generation; rain child recipes copy the same field set.'
  },
  sources: sourceNames.map(name => ({file: name, sha256: sha256(read(name))})),
  artifact: {file: options.jar, sha256: jar.sha256, bytes: jar.size, totalEntries: jar.count, auditedEntries: packaged},
  checks,
  limits: ['Source patterns and class/resource presence do not prove shader compilation, runtime GL state restoration, performance or visual quality. Evaluate the recorded client captures and probes separately.']
};
fs.mkdirSync(options.evidence, {recursive: true});
const output = path.join(options.evidence, 'halo-contract-audit.json');
fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
console.log(`PASS: ${checks.length} halo/vivid/HOT source/JAR contract checks; ${packaged.length} audited entries; ${Object.keys(recipe).length} unchanged original fields plus 3 gradient controls.`);
console.log(`JAR SHA256: ${jar.sha256}`);
console.log(`Report: ${output}`);
