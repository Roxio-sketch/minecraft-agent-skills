// Run a loopback HTTP server at the repository root before this browser smoke check.
const {chromium} = require('playwright');
const fs = require('node:fs');
const base = process.env.WORKBENCH_BASE || 'http://127.0.0.1:18879';
const paths = [
  'minecraft-space-rift/assets/space-rift-kit/workbench/index.html',
  'minecraft-dynamic-lighting/assets/workbench/index.html',
  'minecraft-selective-bloom/assets/workbench/index.html',
  'minecraft-procedural-particles/assets/workbench/index.html',
  'minecraft-animated-material-adapter/assets/workbench/index.html',
  'minecraft-local-environment-material/assets/workbench/index.html',
  'minecraft-lightning/assets/workbench/fbm-study.html',
];
(async () => {
  const browser = await chromium.launch({channel:'msedge',headless:true});
  const results = [];
  try {
    for (const path of paths) {
      const page = await browser.newPage({acceptDownloads:true});
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      page.on('response', r => {if (r.status() >= 400 && !r.url().endsWith('/favicon.ico')) errors.push(`${r.status()} ${r.url()}`);});
      await page.goto(`${base}/skills/${path}?test`);
      await page.waitForTimeout(1200);
      if (path.startsWith('minecraft-space-rift/')) {
        await page.evaluate(() => riftLab.ready);
        if (await page.locator('#stylePreset option').count() !== 14) errors.push('Expected 14 presets');
        for (const preset of ['horizon-slash', 'horizon-giant', 'crystal']) {
          await page.selectOption('#stylePreset', preset);
          await page.waitForTimeout(150);
        }
        const download = page.waitForEvent('download');
        await page.click('#export');
        const file = await download;
        JSON.parse(fs.readFileSync(await file.path(), 'utf8'));
        await page.click('[data-tab=library]');
        await page.frameLocator('#catalogFrame').locator('a[href="index.html?preset=crystal"]').waitFor();
        await page.click('[data-tab=research]');
        await page.frameLocator('[data-panel=research] iframe').locator('article').first().waitFor();
        await page.click('[data-tab=preview]');
      }
      if (!await page.locator('canvas').count()) errors.push('Missing canvas');
      for (const el of await page.locator('#error').all()) {
        if (await el.isVisible() && (await el.textContent()).trim()) errors.push(await el.textContent());
      }
      results.push({path,ok:!errors.length,errors});
      console.log(`${errors.length ? 'FAIL' : 'PASS'} ${path}`, errors);
      await page.close();
    }
  } finally {await browser.close();}
  fs.mkdirSync('docs/validation',{recursive:true});
  fs.writeFileSync('docs/validation/skill-workbench-browser-20261021.json',JSON.stringify({scope:'Headless Edge page/runtime/resource smoke checks; rift presets, export and embedded tabs. No Minecraft verification.',results},null,2));
  if (results.some(r => !r.ok)) process.exitCode = 1;
})().catch(e => {console.error(e);process.exitCode=1;});
