// Optional browser integration check. Requires Node.js and Playwright; preview server must be running.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),option=(key,fallback)=>{const i=args.indexOf(key);return i<0?fallback:args[i+1];};
const url=option('--url','http://127.0.0.1:8771/');
const output=path.resolve(option('--output','black-hole-evidence'));
const executablePath=option('--browser',process.env.BROWSER_EXECUTABLE);
const sum=(a)=>{let s=0;for(let i=0;i<a.length;i+=4)s+=a[i]+a[i+1]+a[i+2];return s/(a.length/4*3);};
const diff=(a,b)=>{let n=0,total=0;for(let i=0;i<a.length;i+=4){const d=Math.abs(a[i]-b[i])+Math.abs(a[i+1]-b[i+1])+Math.abs(a[i+2]-b[i+2]);if(d)n++;total+=d;}return {pixels:n,mean:total/(a.length/4*3)};};
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath,args:['--enable-webgl']});
 const checks=[],errors=[],shots=[];
 let renderer;
 const check=(name,passed,detail)=>checks.push({name,passed,detail});
 try{
  const page=await browser.newPage({viewport:{width:1180,height:1100},deviceScaleFactor:1});
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url);
  await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
  const error=await page.evaluate(()=>window.blackHoleError);if(error)throw Error(error);
  renderer=await page.evaluate(()=>window.blackHolePreview.renderer());
  const frame=(index=0,debug=0)=>page.evaluate(([i,d])=>window.blackHolePreview.frame(i,d),[index,debug]);
  const set=(patch,t=1.7)=>page.evaluate(({p,t})=>window.blackHolePreview.set(p,t),{p:patch,t});
  const recipes=await page.evaluate(()=>window.blackHolePreview.catalog.recipes.filter(r=>r.diskStyle==='classic'));
  for(let i=0;i<recipes.length;i++){
   await set(recipes[i]);const f=await frame();
   check('recipe_'+i,f.glError===0&&sum(f.pixels)>2,{glError:f.glError,mean:sum(f.pixels)});
   const file=`recipe-${i}.png`;await page.locator('#scene').screenshot({path:path.join(output,file)});shots.push(file);
  }
  await set(recipes[0]);const original=await frame();const repeat=await frame();
  check('fixed_state_repeat',diff(original.pixels,repeat.pixels).pixels===0,diff(original.pixels,repeat.pixels));
  await set({},5.7);const moving=await frame();check('disk_animation',diff(original.pixels,moving.pixels).pixels>100,diff(original.pixels,moving.pixels));
  await set({speed:0,infallSpeed:0},1.7);const still1=await frame();const stillPng1=await page.evaluate(()=>document.getElementById('scene').toDataURL());
  await set({},5.7);const still2=await frame();const stillPng2=await page.evaluate(()=>document.getElementById('scene').toDataURL());
  check('both_motion_speeds_zero_freezes',diff(still1.pixels,still2.pixels).pixels===0&&stillPng1===stillPng2,{...diff(still1.pixels,still2.pixels),exportedPngIdentical:stillPng1===stillPng2});
  await set(recipes[0]);const debug=await frame(0,1);let captured=0,exhausted=0;for(let i=0;i<debug.pixels.length;i+=4){if(debug.pixels[i]>127)captured++;if(debug.pixels[i+1]>127)exhausted++;}
  check('core_capture_present',captured>100,{capturedPixels:captured});
  check('default_path_budget',exhausted/debug.width/debug.height<0.01,{exhaustedPixels:exhausted,total:debug.width*debug.height});
  await set({bend:0});const unbent=await frame();check('bending_changes_image',diff(original.pixels,unbent.pixels).pixels>100,diff(original.pixels,unbent.pixels));
  await set({...recipes[0],pitch:0.9});const orbit=await frame();check('camera_changes_image',diff(original.pixels,orbit.pixels).pixels>100,diff(original.pixels,orbit.pixels));
  await page.locator('#scene').screenshot({path:path.join(output,'disk-above.png')});shots.push('disk-above.png');
  await set({...recipes[0],steps:96});const low=await frame();await set({steps:256});const high=await frame();
  const ratio=sum(low.pixels)/sum(high.pixels);check('quality_brightness',ratio>0.8&&ratio<1.25,{lowHighRatio:ratio,scope:'Whole-frame display mean; not pixelwise convergence or physical proof'});
  await set({...recipes[0],mode:'blade'});const blade1=await frame();await set({},5.7);const blade2=await frame();let outsideDiff=0;
  for(let y=0;y<blade1.height;y++)for(let x=0;x<blade1.width;x++){if(x>blade1.width*.32&&x<blade1.width*.68)continue;const i=(y*blade1.width+x)*4;if(blade1.pixels.slice(i,i+3).some((v,k)=>v!==blade2.pixels[i+k]))outsideDiff++;}
  check('mask_outside_stable',outsideDiff===0,{outsideDiff});
  await page.locator('#scene').screenshot({path:path.join(output,'blade-window.png')});shots.push('blade-window.png');
  await set({...recipes[0],mode:'scene'});
  for(const [i,size] of [[1,16],[2,32]]){const f=await frame(i);check('small_'+size,f.width===size&&f.height===size&&f.glError===0&&sum(f.pixels)>1,{width:f.width,height:f.height,glError:f.glError});await page.locator('#small'+size).screenshot({path:path.join(output,`small-${size}.png`)});shots.push(`small-${size}.png`);}
  const roundtrip=await page.evaluate(()=>{const api=window.blackHolePreview,a=api.snapshot();api.set({bend:0},0);api.importSnapshot(a);return JSON.stringify(a)===JSON.stringify(api.snapshot());});check('recipe_roundtrip',roundtrip);
  const rejected=await page.evaluate(()=>{const api=window.blackHolePreview,bad=[{core:1.2},{inner:0.55,core:0.7},{steps:100},{hot:'red'},{seed:1.5},{bend:NaN}];return bad.every(p=>{try{api.validate({...api.snapshot().parameters,...p});return false;}catch{return true;}});});check('invalid_parameters_rejected',rejected);
  check('no_page_errors',errors.length===0,errors);
  await page.screenshot({path:path.join(output,'workbench.png')});shots.push('workbench.png');
 } finally {await browser.close();}
 const result={status:checks.every(x=>x.passed)?'passed':'failed',scope:'Original WebGL 2 preview only; no Minecraft or physical accuracy certification',created:new Date().toISOString(),renderer,checks,shots};
 fs.writeFileSync(path.join(output,'validation.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify(result,null,2));if(result.status!=='passed')process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
