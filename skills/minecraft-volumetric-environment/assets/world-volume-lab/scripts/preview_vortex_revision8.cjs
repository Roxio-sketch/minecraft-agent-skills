/* Independent actual-frame review. Third-party reference code is not executed. */
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const lab = path.resolve(__dirname, '..');
const out = path.join(lab, 'evidence/vortex-revision8/review');
fs.mkdirSync(out, { recursive: true });
if (fs.existsSync(path.join(out, 'report.json'))) {
  const old = JSON.parse(fs.readFileSync(path.join(out, 'report.json'), 'utf8'));
  const archive = path.join(out, 'history', `${old.previewSha256.slice(0,12)}-${Date.now()}`);
  fs.mkdirSync(archive, { recursive: true });
  for (const entry of fs.readdirSync(out, { withFileTypes: true })) if (entry.isFile()) fs.copyFileSync(path.join(out, entry.name), path.join(archive, entry.name));
}
const sha = x => crypto.createHash('sha256').update(x).digest('hex');
const previewPath = path.join(lab, 'workbench/vortex/preview.js');
const sourceSha256 = sha(fs.readFileSync(previewPath));
const checks = [], frames = [];
const check = (name, passed, data) => checks.push({ name, passed: !!passed, ...(data === undefined ? {} : { data }) });
async function reviewPage(page, reusedScene=false) {
    page.setDefaultTimeout(120000); page.setDefaultNavigationTimeout(120000);
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    const started = Date.now();
    if(!reusedScene)await page.goto('http://127.0.0.1:8774/vortex/?verify=revision8');
    else await page.setViewportSize({width:1440,height:1000});
    await page.waitForFunction(() => window.volumeLab?.ready);
    await page.evaluate(() => window.volumeLab.pause(true));
    const initialMs = Date.now() - started;
    const frameTimings = [];
    const capture = async (mode, view, time, label) => {
      const frameStarted = Date.now();
      await page.evaluate(async ({ mode, view, time }) => {
        await window.volumeLab.setMode(mode); await window.volumeLab.setView(view);
        await window.volumeLab.render(time);
      }, { mode, view, time });
      const first = await page.evaluate(() => window.volumeLab.capture());
      const rgba = await page.evaluate(async () => {
        const c = document.getElementById('viewport'), g = c.getContext('webgl2');
        const bytes = new Uint8Array(c.width * c.height * 4);
        g.readPixels(0, 0, c.width, c.height, g.RGBA, g.UNSIGNED_BYTE, bytes);
        window.reviewRgba = bytes;
        const digest = await crypto.subtle.digest('SHA-256', bytes);
        return { width: c.width, height: c.height, sha256: [...new Uint8Array(digest)].map(n => n.toString(16).padStart(2, '0')).join(''), glError: g.getError() };
      });
      await page.evaluate(t => window.volumeLab.render(t), time);
      const second = await page.evaluate(() => window.volumeLab.capture());
      const replay = await page.evaluate(async () => {
        const c = document.getElementById('viewport'), g = c.getContext('webgl2');
        const bytes = new Uint8Array(c.width * c.height * 4), before = window.reviewRgba;
        g.readPixels(0, 0, c.width, c.height, g.RGBA, g.UNSIGNED_BYTE, bytes);
        let changedChannels = 0, changedPixels = 0, maxDifference = 0, sumAbsoluteDifference = 0;
        for (let i = 0; i < bytes.length; i += 4) {
          let pixelChanged = false;
          for (let j = 0; j < 4; j++) {
            const d = Math.abs(bytes[i+j] - before[i+j]);
            if (d) { changedChannels++; pixelChanged = true; }
            maxDifference = Math.max(maxDifference, d); sumAbsoluteDifference += d;
          }
          if (pixelChanged) changedPixels++;
        }
        const digest = await crypto.subtle.digest('SHA-256', bytes);
        return { changedChannels, changedPixels, maxDifference, sumAbsoluteDifference, meanAbsoluteChannelDifference: sumAbsoluteDifference / bytes.length, sha256: [...new Uint8Array(digest)].map(n => n.toString(16).padStart(2, '0')).join(''), glError: g.getError() };
      });
      const file = `${label}.png`; fs.writeFileSync(path.join(out, file), Buffer.from(first.split(',')[1], 'base64'));
      const state = await page.evaluate(() => window.volumeLab.getState());
      frames.push({ file, mode, view, time, pngSha256: sha(fs.readFileSync(path.join(out, file))), firstRgba: rgba, replayRgba: replay, state });
      check(`${label}: fixed-time PNG byte exact`, first === second);
      check(`${label}: fixed-time RGBA byte exact`, replay.changedChannels === 0, replay);
      check(`${label}: no GL error`, rgba.glError === 0 && replay.glError === 0);
      frameTimings.push({mode,view,time,renderCaptureMs:Date.now()-frameStarted});
    };
    for (const mode of [1, 2]) {
      await page.evaluate(m => window.volumeLab.setMode(m), mode);
      const duration = await page.evaluate(() => window.volumeLab.getWork().vortex.duration);
      for (const [fraction, name] of [[0, 'early'], [.45, 'middle'], [1.2, 'mature']])
        await capture(mode, 'outside', duration * fraction, `mode-${mode}-${name}`);
      await capture(mode, 'liquid', duration * 1.2, `mode-${mode}-near`);
    }
    // Preserve and exercise the user's last exported work with the revised renderer.
    const userWorkPath=path.join(lab,'evidence/vortex-revision8/user-work-before.json');
    if(fs.existsSync(userWorkPath)){
      const userWork=JSON.parse(fs.readFileSync(userWorkPath,'utf8'));
      await page.locator('#importFile').setInputFiles(userWorkPath);
      await page.waitForFunction(t=>window.volumeLab.getWork().preview.time===t,userWork.preview.time);
      await page.evaluate(t=>window.volumeLab.render(t),userWork.preview.time);
      const data=await page.evaluate(()=>window.volumeLab.capture());
      fs.writeFileSync(path.join(out,'user-work-revised.png'),Buffer.from(data.split(',')[1],'base64'));
      const restored=await page.evaluate(()=>window.volumeLab.getWork());
      check('user recipe and event fields retained',JSON.stringify(restored.recipe)===JSON.stringify(userWork.recipe)&&JSON.stringify(restored.vortex)===JSON.stringify(userWork.vortex));
      check('user camera and mature time retained',JSON.stringify(restored.preview)===JSON.stringify(userWork.preview));
    }
    // Record motion in the same full-scene browser when requested, avoiding a
    // second compilation solely to collect animation frames. Strict replay
    // checks above remain unchanged and separate from GIF assembly.
    if (process.env.VORTEX_RECORD_MOTION === '1') {
      const motionOut = path.join(lab, 'evidence/water-sheets-iteration');
      fs.mkdirSync(motionOut, { recursive: true });
      const record = async (mode, view, folder, count) => {
        await page.evaluate(m => window.volumeLab.setMode(m), mode);
        await page.evaluate(v => window.volumeLab.setView(v), view);
        const dir = path.join(motionOut, folder); fs.mkdirSync(dir, { recursive: true });
        for (let i = 0; i < count; i++) {
          await page.evaluate(t => window.volumeLab.render(t), 20 + i / 10);
          const png = await page.evaluate(() => window.volumeLab.capture());
          fs.writeFileSync(path.join(dir, `${String(i).padStart(3, '0')}.png`), Buffer.from(png.split(',')[1], 'base64'));
        }
      };
      await record(1, 'outside', 'motion-1', 24);
      await record(2, 'outside', 'motion-2', 24);
      await record(2, 'liquid', 'surface-flow-2', 30);
      const motion = { ok: errors.length === 0 && sourceSha256 === sha(fs.readFileSync(previewPath)),
        scope: 'Actual fixed-time WebGL PNGs captured after independent strict frame review in the same browser',
        initialMs, frames: frames.map(f => ({ mode: f.mode, view: f.view, time: f.time, file: '../vortex-revision8/review/' + f.file, sha256: f.pngSha256 })), errors,
        coreSha256: sha(fs.readFileSync(path.join(lab, 'workbench/vortex/volume-core.glsl'))), previewSha256: sourceSha256,
        motion: { modes: [1, 2], times: [20, 22.3], nearTimes: [20, 22.9], fps: 10 }, work: await page.evaluate(() => window.volumeLab.getWork()) };
      fs.writeFileSync(path.join(motionOut, 'report.json'), JSON.stringify(motion, null, 2) + '\n');
    }
    check('no page errors', errors.length === 0, errors);
    check('source did not change during review', sourceSha256 === sha(fs.readFileSync(previewPath)));
    const report = { date: new Date().toISOString(), scope: 'WebGL2 actual frames; strict byte equality retained; no Minecraft certification', previewSha256: sourceSha256, coreSha256: sha(fs.readFileSync(path.join(lab, 'workbench/vortex/volume-core.glsl'))), checks, frames, passed: checks.every(c => c.passed) };
    fs.writeFileSync(path.join(out, 'timing.json'), JSON.stringify({scope:'Edge headless actual render plus PNG and RGBA capture; not runtime FPS benchmark',previewSha256:sourceSha256,reusedScene,initialMs,frameTimings},null,2)+'\n');
    fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify({ passed: report.passed, checks: checks.length, failed: checks.filter(c => !c.passed), previewSha256: sourceSha256 }));
    if (!report.passed) process.exitCode = 1;
}
module.exports={reviewPage};
if(require.main===module)(async()=>{
  const browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
  try{const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});await reviewPage(page);}
  finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
