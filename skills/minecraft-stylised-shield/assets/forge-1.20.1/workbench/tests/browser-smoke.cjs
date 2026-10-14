const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path'),fs=require('node:fs');
(async()=>{
 const evidence=path.resolve(process.argv[2]||path.join(__dirname,'../evidence'));fs.mkdirSync(evidence,{recursive:true});
 const browser=await chromium.launch({channel:'msedge',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
 try{
  await page.goto(process.env.SHIELD_URL||'http://127.0.0.1:8766/');
  await page.waitForFunction(()=>document.getElementById('hud').textContent.includes('完整'));
  assert.equal(await page.locator('#error').textContent(),'');
  await page.locator('#damage').press('End');
  await page.locator('#hit').click();assert.equal(await page.locator('#life').inputValue(),'0.5');
  await page.locator('#hit').click();assert.equal(await page.locator('#life').inputValue(),'0');
  await page.waitForTimeout(450);assert.match(await page.locator('#hud').textContent(),/濒临破碎/);
  await page.screenshot({path:path.join(evidence,'01-depleted.png')});
  await page.locator('#hit').click();await page.waitForTimeout(240);await page.screenshot({path:path.join(evidence,'02a-rupture.png')});await page.waitForTimeout(220);await page.locator('#pause').click();
  assert.match(await page.locator('#hud').textContent(),/破碎中/);
  await page.screenshot({path:path.join(evidence,'02-shattering.png')});
  const frozen=await page.locator('#hud').textContent();await page.waitForTimeout(120);assert.equal(await page.locator('#hud').textContent(),frozen,'pause freezes lifecycle and sparks');
  await page.locator('#hit').click();assert.equal(await page.locator('#hud').textContent(),frozen,'no respawn during shatter');
  await page.locator('#side').click();await page.screenshot({path:path.join(evidence,'03-side-fragments.png')});
  await page.locator('#pause').click();await page.waitForTimeout(2500);assert.match(await page.locator('#hud').textContent(),/已破碎/);
  await page.locator('#hit').click();await page.waitForTimeout(50);assert.match(await page.locator('#hud').textContent(),/已破碎/);
  await page.screenshot({path:path.join(evidence,'04-broken.png')});
  await page.locator('#reset').click();await page.waitForTimeout(80);assert.match(await page.locator('#hud').textContent(),/完整/);assert.equal(await page.locator('#life').inputValue(),'1');
  await page.locator('#front').click();
  for(const shape of ['sphere','dome','convex','flat']){
   await page.locator('#shape').selectOption(shape);await page.locator('#shatterDemo').click();await page.waitForTimeout(350);await page.locator('#pause').click();
   assert.match(await page.locator('#hud').textContent(),/破碎中/);assert.equal(await page.locator('#error').textContent(),'');
   await page.screenshot({path:path.join(evidence,'shape-'+shape+'.png')});
   assert.equal(await page.locator('#c').evaluate(c=>c.getContext('webgl').getError()),0,'WebGL error for '+shape);
   await page.locator('#reset').click();await page.locator('#pause').click();
  }
  await page.locator('#shape').selectOption('sphere');await page.locator('#burst').click();await page.waitForTimeout(1200);assert.match(await page.locator('#hud').textContent(),/破碎中|已破碎/,'continuous burst breaks automatically');
  await page.locator('#reset').click();await page.waitForTimeout(750);assert.match(await page.locator('#hud').textContent(),/完整/,'restore cancels pending burst');assert.equal(await page.locator('#life').inputValue(),'1');
  const downloadPromise=page.waitForEvent('download');await page.locator('#export').click();const download=await downloadPromise;
  const exported=JSON.parse(fs.readFileSync(await download.path(),'utf8'));
  assert.equal(exported.schema,2);assert.equal(exported.shardCount,110);assert.equal(exported.shatterDuration,2.4);assert.equal(exported.shatterSpeed,2.8);assert.equal(exported.shatterSpin,3);assert.equal(exported.shatterGravity,1.8);assert.equal(exported.shatterFlash,1.8);
  const legacy=path.join(evidence,'legacy-v2.json');for(const key of Object.keys(exported))if(key.startsWith('shatter')||key==='shardCount')delete exported[key];fs.writeFileSync(legacy,JSON.stringify(exported));
  await page.locator('#file').setInputFiles(legacy);await page.waitForFunction(()=>document.getElementById('status').textContent==='配方已导入并应用。');
  assert.equal(await page.locator('#shardCount').inputValue(),'110');assert.equal(await page.locator('#shatterDuration').inputValue(),'2.4');assert.match(await page.locator('#hud').textContent(),/完整/);
  assert.deepEqual(errors,[]);console.log('PASS: automatic threshold, one-shot fracture, pause, side view, four shapes, continuous hits, reset, JSON export and legacy-v2 import; no browser/WebGL errors.');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
