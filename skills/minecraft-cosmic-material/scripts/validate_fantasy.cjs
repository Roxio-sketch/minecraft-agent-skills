// Standalone WebGL evidence only; this does not certify Minecraft runtime behavior.
const fs = require('node:fs');
const path = require('node:path');
const { options, assetDigest, startServer, stopChild, selectBrowser, verifyService } = require('./preview_validation_support.cjs');

const settings = options(process.argv.slice(2));
if (settings.help) {
  console.log('node scripts/validate_fantasy.cjs [--python PATH] [--browser PATH] [--url LOOPBACK_URL] [--out DIR] [--freeze-runs 30]\nStarts an exclusive fantasy preview on an OS-assigned port unless --url is provided. Frozen pixels use ZERO tolerance.');
  process.exit(0);
}
const evidence = settings.out;
fs.mkdirSync(evidence, { recursive: true });
const expected = ['rune-vault', 'liquid-mithril', 'jade-veins', 'gilded-porcelain', 'iridescent-silk', 'folded-ember', 'fantasy-flow', 'aurora-veil', 'storm-prism', 'aether-bubbles', 'clockwork-core', 'living-mycelium', 'tidal-water', 'crt-static'];
const checks = [];
function check(name, condition, detail) {
  checks.push({ name, passed: !!condition, detail });
  if (!condition) process.exitCode = 1;
}
function savePng(name, base64) {
  fs.writeFileSync(path.join(evidence, name + '.png'), Buffer.from(base64, 'base64'));
}

async function installInspectors(page) {
  await page.evaluate(() => {
    const sizes = [[800, 540], [16, 16], [32, 32]];
    const delta = (a, b) => {
      if (a.length !== b.length) throw Error('Framebuffer dimensions differ');
      let changedPixels = 0, changedChannels = 0, maxChannelDifference = 0, sum = 0;
      for (let i = 0; i < a.length; i += 4) {
        let changed = false;
        for (let c = 0; c < 3; c++) {
          const d = Math.abs(a[i + c] - b[i + c]);
          if (d) { changed = true; changedChannels++; }
          maxChannelDifference = Math.max(maxChannelDifference, d);
          sum += d;
        }
        if (changed) changedPixels++;
      }
      return { changedPixels, changedChannels, maxChannelDifference, meanChannelDifference: sum / (a.length / 4 * 3) };
    };
    const stats = a => {
      let sum = 0, clipped = 0, nonzero = 0;
      const colors = new Set();
      for (let i = 0; i < a.length; i += 4) {
        sum += a[i] * 0.2126 + a[i + 1] * 0.7152 + a[i + 2] * 0.0722;
        if (Math.max(a[i], a[i + 1], a[i + 2]) === 255) clipped++;
        if (a[i] + a[i + 1] + a[i + 2] > 0) nonzero++;
        colors.add(`${a[i]},${a[i + 1]},${a[i + 2]}`);
      }
      return { pixelCount: a.length / 4, meanLuma: sum / (a.length / 4), clippedFraction: clipped / (a.length / 4), nonzeroPixels: nonzero, uniqueColors: colors.size };
    };
    const png = (pixels, index = 0) => {
      const [width, height] = sizes[index], canvas = document.createElement('canvas');
      canvas.width = width; canvas.height = height;
      const flipped = new Uint8ClampedArray(pixels.length);
      for (let y = 0; y < height; y++) flipped.set(pixels.slice(y * width * 4, (y + 1) * width * 4), (height - 1 - y) * width * 4);
      canvas.getContext('2d').putImageData(new ImageData(flipped, width, height), 0, 0);
      return canvas.toDataURL('image/png').split(',')[1];
    };
    window.fantasyValidation = { delta, stats, png, sizes };
  });
}

async function freezeSuite(page, runs) {
  const result = await page.evaluate(async ({ runs, expected }) => {
    const { delta, png, sizes } = fantasyValidation;
    const summary = { zeroMotion: [], sameState: [], uniforms: [], firstFailure: null };
    const inspect = (kind, a, b, run, preset, recipes) => {
      for (let canvas = 0; canvas < 3; canvas++) {
        const difference = delta(a[canvas], b[canvas]);
        summary[kind].push({ run, preset, canvas, ...difference });
        if (difference.changedPixels && !summary.firstFailure) {
          summary.firstFailure = { kind, run, preset, canvas, width: sizes[canvas][0], height: sizes[canvas][1], difference, recipes, diagnostics: fantasyLab.diagnostics(), beforePng: png(a[canvas], canvas), afterPng: png(b[canvas], canvas) };
        }
      }
    };
    for (let run = 0; run < Math.max(runs, expected.length); run++) {
      const preset = expected[run % expected.length];
      fantasyLab.selectPreset(preset);
      fantasyLab.render({ time: 0, view: [0, 0], shape: 0, parameters: { speed: 0 } });
      const recipeA = fantasyLab.recipe(), a = [0, 1, 2].map(i => fantasyLab.capture(i));
      await new Promise(resolve => requestAnimationFrame(resolve));
      fantasyLab.render({ time: 30 + run * 13 });
      const recipeB = fantasyLab.recipe(), b = [0, 1, 2].map(i => fantasyLab.capture(i));
      inspect('zeroMotion', a, b, run, preset, [recipeA, recipeB]);
      summary.uniforms.push({ preset, run, values: fantasyLab.diagnostics().materialTime });
      await new Promise(resolve => requestAnimationFrame(resolve));
      fantasyLab.render({ time: 30 + run * 13 });
      inspect('sameState', b, [0, 1, 2].map(i => fantasyLab.capture(i)), run, preset, [recipeB, fantasyLab.recipe()]);
    }
    return summary;
  }, { runs, expected });
  if (result.firstFailure) {
    const failure = result.firstFailure;
    savePng('freeze-before', failure.beforePng); savePng('freeze-after', failure.afterPng);
    delete failure.beforePng; delete failure.afterPng;
    fs.writeFileSync(path.join(evidence, 'freeze-failure.json'), JSON.stringify(failure, null, 2));
  }
  return result;
}

async function contactSheet(page, images) {
  const png = await page.evaluate(async images => {
    const cellWidth = 400, cellHeight = 318, canvas = document.createElement('canvas');
    canvas.width = cellWidth * 3; canvas.height = cellHeight * Math.ceil(images.length / 3);
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#12181a'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    for (let index = 0; index < images.length; index++) {
      const source = new Image(); source.src = 'data:image/png;base64,' + images[index].png;
      await source.decode();
      const x = index % 3 * cellWidth, y = Math.floor(index / 3) * cellHeight;
      ctx.drawImage(source, x + 8, y + 8, 384, 259.2);
      ctx.fillStyle = '#f2f5f5'; ctx.font = '18px sans-serif'; ctx.fillText(images[index].id, x + 14, y + 297);
    }
    return canvas.toDataURL('image/png').split(',')[1];
  }, images);
  savePng('contact-sheet', png);
}

(async () => {
  let browser, owned, diagnostics, browserVersion, service;
  try {
    let chromium;
    try { ({ chromium } = require('playwright')); }
    catch (error) { throw Error('Playwright is required. Use an existing Node environment or NODE_PATH containing playwright. ' + error.message); }
    if (!settings.url) { owned = await startServer(settings.python, 'fantasy'); settings.url = owned.url; }
    service = await verifyService(settings.url, 'fantasy');
    check('service_identity_and_assets', true, service);
    browser = await chromium.launch({ executablePath: selectBrowser(chromium, settings.browser), headless: true, args: ['--enable-webgl'] });
    browserVersion = browser.version();
    const page = await browser.newPage({ viewport: { width: 1380, height: 1100 }, deviceScaleFactor: 1, acceptDownloads: true });
    page.setDefaultTimeout(60000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(service.url);
    await page.waitForFunction(() => window.fantasyLab !== undefined);
    const startup = await page.evaluate(() => ({ ready: fantasyLab.ready, error: fantasyLab.error, presets: fantasyLab.presets }));
    check('webgl_compile_link', startup.ready, startup);
    if (!startup.ready) throw Error(startup.error || 'Fantasy shader initialization failed');
    check('expected_presets_present', expected.every(id => startup.presets.some(p => (typeof p === 'string' ? p : p.id) === id)), startup.presets);
    await installInspectors(page);
    const images = [];
    for (const id of expected) {
      const result = await page.evaluate(id => {
        const { delta, stats, png } = fantasyValidation;
        fantasyLab.selectPreset(id);
        fantasyLab.render({ time: 0, view: [0, 0], shape: 0 });
        const parameters = fantasyLab.recipe().parameters, initial = fantasyLab.capture();
        const fieldPng = png(initial), field = stats(initial);
        fantasyLab.render({ time: 8 });
        const animation = delta(initial, fantasyLab.capture());
        fantasyLab.render({ time: 0, parameters: { seed: parameters.seed + 101 } });
        const seed = delta(initial, fantasyLab.capture());
        fantasyLab.render({ time: 0, parameters: { seed: parameters.seed }, view: [-0.65, 0.25] });
        const left = fantasyLab.capture();
        fantasyLab.render({ view: [0.65, -0.25] });
        const view = delta(left, fantasyLab.capture());
        fantasyLab.render({ parameters: { parallax: 0 }, view: [-0.65, 0.25] });
        const noParallaxLeft = fantasyLab.capture();
        fantasyLab.render({ view: [0.65, -0.25] });
        const noParallax = delta(noParallaxLeft, fantasyLab.capture());
        fantasyLab.render({ parameters: { parallax: parameters.parallax }, shape: 1, time: 0, view: [0, 0] });
        const swordPng = png(fantasyLab.capture());
        const icons = [1, 2].map(index => { const p = fantasyLab.capture(index); return { index, ...stats(p), png: png(p, index) }; });
        return { id, field, fieldPng, animation, seed, view, noParallax, swordPng, icons, diagnostics: fantasyLab.diagnostics() };
      }, id);
      const monochromeSnow=id==='crt-static' && result.field.uniqueColors===2 && result.field.nonzeroPixels>result.field.pixelCount*0.25;
      const variedMaterial=id!=='crt-static' && result.field.uniqueColors>128;
      check('visible_material:' + id, result.field.pixelCount === 800 * 540 && (monochromeSnow||variedMaterial) && result.field.meanLuma > 2 && result.field.clippedFraction < 0.25, result.field);
      check('animation_response:' + id, result.animation.changedPixels > 1000, result.animation);
      check('seed_response:' + id, result.seed.changedPixels > 1000, result.seed);
      check('view_response:' + id, result.view.changedPixels > 1000, result.view);
      check('disabled_parallax_ignores_view:' + id, result.noParallax.changedPixels === 0, { tolerance: 0, ...result.noParallax });
      check('gl_errors:' + id, result.diagnostics.errors.every(n => n === 0), result.diagnostics);
      savePng(id + '-field', result.fieldPng); savePng(id + '-sword', result.swordPng);
      images.push({ id, png: result.fieldPng });
      for (const icon of result.icons) {
        const size = icon.index === 1 ? 16 : 32;
        savePng(id + '-' + size + 'px', icon.png); delete icon.png;
        check('native_icon:' + id + ':' + size, icon.pixelCount === size * size && icon.uniqueColors > 8 && icon.nonzeroPixels > 0, icon);
      }
    }
    await contactSheet(page, images);
    const frozen = await freezeSuite(page, settings.freezeRuns);
    check('zero_motion_is_static', frozen.zeroMotion.every(s => s.changedPixels === 0), { tolerance: 0, samples: frozen.zeroMotion, firstFailure: frozen.firstFailure });
    check('repeated_fixed_state_deterministic', frozen.sameState.every(s => s.changedPixels === 0), { tolerance: 0, samples: frozen.sameState });
    check('static_material_receives_canonical_time', frozen.uniforms.every(s => s.values.length === 3 && s.values.every(value => value === 0)), frozen.uniforms);
    const apertures = await page.evaluate(() => {
      const results = [];
      for (const shape of [1, 2]) {
        fantasyLab.selectPreset('rune-vault'); fantasyLab.render({ shape, time: 0, view: [0, 0] });
        const a = fantasyLab.capture();
        fantasyLab.selectPreset('liquid-mithril'); fantasyLab.render({ shape, time: 0, view: [0, 0] });
        const b = fantasyLab.capture();
        let exteriorChanged = 0, interiorChanged = 0, exteriorPixels = 0;
        for (let y = 0; y < 540; y++) for (let x = 0; x < 800; x++) {
          const index = (y * 800 + x) * 4;
          const exterior = shape === 1 ? x < 160 || x >= 640 : (x < 80 || x >= 720) && (y < 54 || y >= 486);
          if (exterior) exteriorPixels++;
          const changed = a[index] !== b[index] || a[index + 1] !== b[index + 1] || a[index + 2] !== b[index + 2];
          if (changed) { if (exterior) exteriorChanged++; else interiorChanged++; }
        }
        results.push({ shape, exteriorChanged, interiorChanged, exteriorPixels });
      }
      return results;
    });
    for (const aperture of apertures) check('aperture_exterior_unchanged:' + aperture.shape, aperture.exteriorChanged === 0 && aperture.interiorChanged > 100, aperture);
    await page.evaluate(() => { fantasyLab.selectPreset('gilded-porcelain'); fantasyLab.render({ shape: 2, time: 3, view: [0.35, -0.2] }); });
    const [download] = await Promise.all([page.waitForEvent('download'), page.locator('#export').click()]);
    const recipePath = path.join(evidence, 'exported-recipe.json');
    await download.saveAs(recipePath);
    const exported = JSON.parse(fs.readFileSync(recipePath, 'utf8'));
    check('recipe_export', exported.schema === 'fantasy-material/v1' && exported.parameters.id === 'gilded-porcelain', exported);
    await page.evaluate(() => fantasyLab.selectPreset('jade-veins'));
    await page.locator('#import-file').setInputFiles(recipePath);
    await page.waitForFunction(() => fantasyLab.recipe().parameters.id === 'gilded-porcelain');
    const imported = await page.evaluate(() => fantasyLab.recipe());
    check('recipe_import_roundtrip', JSON.stringify(imported.parameters) === JSON.stringify(exported.parameters) && JSON.stringify(imported.preview) === JSON.stringify(exported.preview), { exported, imported });
    const invalidPath = path.join(evidence, 'rejected-recipe.json');
    fs.writeFileSync(invalidPath, JSON.stringify({ ...exported, parameters: { ...exported.parameters, mode: 999 } }, null, 2));
    await page.evaluate(() => { fantasyValidation.beforeInvalid = { recipe: fantasyLab.recipe(), pixels: fantasyLab.capture() }; });
    await page.locator('#import-file').setInputFiles(invalidPath);
    await page.waitForFunction(() => document.querySelector('#status').classList.contains('error'));
    const rejected = await page.evaluate(() => ({
      before: fantasyValidation.beforeInvalid.recipe,
      after: fantasyLab.recipe(),
      pixels: fantasyValidation.delta(fantasyValidation.beforeInvalid.pixels, fantasyLab.capture()),
      message: document.querySelector('#status').textContent
    }));
    check('invalid_import_preserves_recipe_and_pixels', JSON.stringify(rejected.before) === JSON.stringify(rejected.after) && rejected.pixels.changedPixels === 0, rejected);
    await page.locator('#import-file').setInputFiles(recipePath);
    await page.waitForFunction(() => !document.querySelector('#status').classList.contains('error'));
    await page.screenshot({ path: path.join(evidence, 'desktop.png'), fullPage: true });
    const layout = async () => page.evaluate(() => ({ width: innerWidth, documentWidth: document.documentElement.scrollWidth, bodyWidth: document.body.scrollWidth, scene: (() => { const r = document.querySelector('#scene').getBoundingClientRect(); return { left: r.left, right: r.right, width: r.width, height: r.height }; })() }));
    const desktop = await layout();
    check('desktop_no_horizontal_overflow', desktop.documentWidth <= desktop.width && desktop.bodyWidth <= desktop.width && desktop.scene.width > 0, desktop);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: path.join(evidence, 'mobile.png'), fullPage: true });
    const mobile = await layout();
    check('mobile_no_horizontal_overflow', mobile.documentWidth <= mobile.width && mobile.bodyWidth <= mobile.width && mobile.scene.left >= 0 && mobile.scene.right <= mobile.width, mobile);
    diagnostics = await page.evaluate(() => fantasyLab.diagnostics());
    check('explicit_capture_state', diagnostics.testMode && diagnostics.dither.every(enabled => enabled === false) && diagnostics.captureTarget === 'default-framebuffer', diagnostics);
    const live = new URL(service.url); live.searchParams.delete('test');
    await page.setViewportSize({ width: 1380, height: 1100 });
    await page.goto(live.href); await page.waitForFunction(() => window.fantasyLab?.ready === true);
    await installInspectors(page);
    await page.waitForFunction(() => fantasyLab.recipe().preview.time > 0.15);
    await page.locator('#motion').click();
    const paused = await page.evaluate(async () => {
      const a = fantasyLab.capture(), before = fantasyLab.recipe().preview.time;
      await new Promise(resolve => setTimeout(resolve, 250));
      return { before, after: fantasyLab.recipe().preview.time, difference: fantasyValidation.delta(a, fantasyLab.capture()), diagnostics: fantasyLab.diagnostics() };
    });
    check('user_pause_holds_time_and_pixels', paused.before === paused.after && paused.difference.changedPixels === 0, paused);
    check('paused_recipe_matches_rendered_time', paused.diagnostics.materialTime.every(value => value === Math.fround(paused.before)), { recipeTime: paused.before, uniformTime: paused.diagnostics.materialTime });
    check('javascript_errors', errors.length === 0, errors);
  } catch (error) {
    check('execution_exception', false, { message: error.message, stack: error.stack });
  } finally {
    if (browser) await browser.close();
    if (owned) await stopChild(owned.child);
    let digest;
    try { digest = assetDigest('fantasy'); } catch (error) { digest = { error: error.message }; }
    const result = { assetDigest: digest, url: settings.url, service: service?.identity, browserVersion, freezeTolerance: 0, checkedAt: new Date().toISOString(), status: checks.length > 0 && checks.every(c => c.passed) ? 'passed' : 'failed', scope: 'Standalone WebGL 2 fantasy material study. No Minecraft runtime validation or performance certification. Original Cosmic freeze issue remains separate and unresolved.', diagnostics, checks };
    fs.writeFileSync(path.join(evidence, 'validation.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify({ status: result.status, checks: checks.length, failed: checks.filter(c => !c.passed).map(c => c.name), record: path.join(evidence, 'validation.json') }));
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
