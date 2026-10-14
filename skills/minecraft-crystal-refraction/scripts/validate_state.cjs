// Regression checks for comparison-only UI state and bounded playback time.
// Uses a separate browser page, never the user's active preview.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {chromium}=require('playwright');
const args=Object.fromEntries(process.argv.slice(2).reduce((a,v,i,all)=>{if(v.startsWith('--'))a.push([v.slice(2),all[i+1]]);return a;},[]));
if(!args.out)throw Error('--out required');
const url=args.url||'http://127.0.0.1:8771';
if(!['localhost','127.0.0.1'].includes(new URL(url).hostname))throw Error('Use the local preview server');
const out=path.resolve(args.out);fs.mkdirSync(out,{recursive:true});
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
(async()=>{
 const browser=await chromium.launch({headless:true,...(args.browser?{executablePath:args.browser}:{}),args:['--enable-webgl']});
 const checks=[],errors=[];const check=(name,passed,detail)=>checks.push({name,passed:!!passed,detail});
 try{
  const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url,{waitUntil:'networkidle'});await page.waitForFunction(()=>window.crystalLab||window.crystalLabError);
  if(await page.evaluate(()=>window.crystalLabError))throw Error(await page.evaluate(()=>window.crystalLabError));
  const set=patch=>page.evaluate(p=>window.crystalLab.setState(p),patch);
  const state=()=>page.evaluate(()=>window.crystalLab.getState());
  const frames=()=>page.evaluate(()=>Promise.resolve().then(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))));
  const images=async()=>Object.fromEntries(Object.entries(await page.evaluate(()=>Object.fromEntries(['view','mini16','mini32'].map(id=>[id,document.getElementById(id).toDataURL()])))).map(([id,s])=>[id,hash(s)]));
  await set({time:2,amount:0});const original=await images();
  for(const amount of [0.4,0,1]){
   await set({time:2,amount});const before=await images();
   await page.click('#before');const compared=await images();
   const during=await state(), exported=await page.evaluate(()=>window.crystalLab.exportRecipe());
   check(`compare_${amount}_preserves_recipe`,during.amount===amount&&exported.state.amount===amount,{amount:during.amount,exportedAmount:exported.state.amount});
   check(`compare_${amount}_shows_original_all_sizes`,JSON.stringify(compared)===JSON.stringify(original));
   await page.click('#before');const after=await images();
   check(`compare_${amount}_restores_pixels_and_amount`,(await state()).amount===amount&&JSON.stringify(before)===JSON.stringify(after));
   await page.click('#before');await page.click('#before');
   check(`compare_${amount}_repeat_stable`,(await state()).amount===amount&&JSON.stringify(before)===JSON.stringify(await images()));
  }
  await set({amount:0.4});const fractional=await page.evaluate(()=>window.crystalLab.exportRecipe());
  await page.click('#before');await page.evaluate(r=>window.crystalLab.importRecipe(r),fractional);
  check('import_exits_comparison_preserving_amount',(await state()).amount===0.4&&await page.locator('#before').textContent()==='查看原图');
  await page.click('#before');await page.selectOption('#preset','1');
  check('preset_exits_comparison',await page.locator('#before').textContent()==='查看原图');
  await set({time:1e7-0.001,amount:0.4});await page.click('#pause');
  await page.waitForFunction(()=>window.crystalLab.getState().time>=1e7);await frames();
  const cap=await state();check('playback_clamps_at_maximum',cap.time===1e7,{time:cap.time});
  await frames();check('playback_stops_at_maximum',(await state()).time===cap.time&&await page.locator('#pause').textContent()!=='暂停动画');
  const roundtrip=await page.evaluate(()=>{try{const e=window.crystalLab.exportRecipe();window.crystalLab.importRecipe(e);return {ok:true,state:window.crystalLab.getState()};}catch(e){return {ok:false,error:e.message};}});
  check('maximum_time_export_import',roundtrip.ok&&roundtrip.state.time===1e7&&roundtrip.state.amount===0.4,roundtrip);
  await set({time:1e7});await page.click('#pause');await frames();
  check('play_at_maximum_remains_valid',(await state()).time===1e7&&await page.locator('#pause').textContent()!=='暂停动画');
  const invalid=await page.evaluate(()=>{const before=window.crystalLab.getState();let rejected=false;try{window.crystalLab.importRecipe({schema:'crystal-refraction-preview/v1',state:{...before,time:1e7+1}});}catch{rejected=true;}return rejected&&JSON.stringify(before)===JSON.stringify(window.crystalLab.getState());});
  check('invalid_time_import_still_rejected',invalid);
  await page.click('#reset');check('reset_from_maximum',(await state()).time===0);
  await page.click('#pause');await page.waitForFunction(()=>window.crystalLab.getState().time>0);await page.click('#pause');
  const resumed=await state();check('play_after_reset_resumes',resumed.time>0&&resumed.time<1e7);
  check('no_javascript_errors',errors.length===0,errors);
 }finally{await browser.close();}
 fs.writeFileSync(path.join(out,'state-validation.json'),JSON.stringify({scope:'Browser UI/state regression checks for P2 amount preservation and P3 playback cap',checks},null,2));
 const failed=checks.filter(c=>!c.passed);console.log(JSON.stringify({passed:checks.length-failed.length,total:checks.length,failed}));if(failed.length)process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
