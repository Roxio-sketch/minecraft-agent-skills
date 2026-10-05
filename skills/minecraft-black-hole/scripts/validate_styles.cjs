const {chromium}=require('playwright');const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(n,d)=>{const i=args.indexOf(n);return i<0?d:args[i+1];};
const output=path.resolve(arg('--output','styles-evidence'));const checks=[];let failure=null;
const check=(name,passed,detail)=>{checks.push({name,passed,detail});if(!passed)throw Error(name+': '+JSON.stringify(detail));};
(async()=>{
 fs.mkdirSync(output,{recursive:true});const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
 try{
  const page=await browser.newPage({viewport:{width:1360,height:1000}});await page.goto(arg('--url','http://127.0.0.1:8771/'));
  await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});check('initialized',await page.evaluate(()=>!!window.blackHolePreview?.ready),await page.evaluate(()=>window.blackHoleError||document.getElementById('status')?.textContent));
  check('no_default_sprite_overlay',await page.evaluate(()=>!blackHolePreview.snapshot().parameters.particles&&blackHolePreview.snapshot().parameters.diskStyle==='dust'));
  await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=320;});
  const set=(p,t=0)=>page.evaluate(({p,t})=>blackHolePreview.set(p,t),{p,t});
  const frame=()=>page.evaluate(()=>blackHolePreview.frame());
  const delta=(a,b)=>{const counts={upper:0,disk:0,lower:0,total:0};for(let y=0;y<a.height;y++)for(let x=0;x<a.width;x++){const i=(y*a.width+x)*4,d=Math.max(...a.pixels.slice(i,i+3).map((v,c)=>Math.abs(v-b.pixels[i+c])));if(d>2){counts.total++;if(y>a.height*.65)counts.upper++;else if(y<a.height*.35)counts.lower++;else counts.disk++;}}return counts;};
  for(const style of ['dust','ribbons','cinematic']){
   const preset=await page.evaluate(style=>blackHolePreview.catalog.recipes.find(r=>r.diskStyle===style),style);
   await set({...preset,backgroundSpin:0,orbitSpeed:0,particles:false,halo:0,infallSpeed:0,speed:.55},0);const first=await frame();await set({},.5);const later=await frame();const motion=delta(first,later);
   check(style+'_upper_disk_lower_move',motion.upper>5&&motion.disk>30&&motion.lower>30,motion);check(style+'_gl_ok',first.glError===0&&later.glError===0);
   await set({speed:0,infallSpeed:.12},0);const radial=await frame();await set({},.5);check(style+'_radial_independent',delta(radial,await frame()).total>100);
   await set({speed:0,infallSpeed:0},0);const frozen=await frame();await set({},3);check(style+'_static',delta(frozen,await frame()).total===0);
   await set(preset,1.7);const snap=await page.evaluate(()=>blackHolePreview.snapshot());await page.evaluate(s=>blackHolePreview.importSnapshot(s),snap);check(style+'_roundtrip',JSON.stringify(snap)===JSON.stringify(await page.evaluate(()=>blackHolePreview.snapshot())));
   await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=640;blackHolePreview.set({});});
   await page.locator('#scene').screenshot({path:path.join(output,style+'.png')});
   await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=320;});
  }
  await set({hot:'#000000',cool:'#000000',coreColor:'#000000',density:0,halo:0,speed:0,infallSpeed:0,backgroundSpin:.2,orbitSpeed:0},0);
  const bg=await frame();await set({},1);check('background_rotates_through_lens',delta(bg,await frame()).total>100);
  await set({backgroundSpin:0,orbitSpeed:.1},0);const orbit=await frame();await set({},1);check('orbit_changes_projection_independently',delta(orbit,await frame()).total>100);
  const old=await page.evaluate(()=>{const p={...blackHolePreview.catalog.recipes.find(r=>r.diskStyle==='classic')};for(const k of ['backgroundSpin','orbitSpeed','bandDetail','dustScale','dustFill'])delete p[k];blackHolePreview.importSnapshot({schema:'black-hole-preview/v1',parameters:p,time:0});return blackHolePreview.snapshot().parameters;});
  check('old_recipe_no_unrequested_camera_motion',old.backgroundSpin===0&&old.orbitSpeed===0);
  await page.reload();await page.waitForFunction(()=>window.blackHolePreview?.ready);await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});check('mobile_no_overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 }catch(e){failure=e.message;throw e;}finally{await browser.close();fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify({status:failure?'failed':'passed',failure,checks},null,2));}
 console.log(JSON.stringify({status:'passed',checks:checks.length}));
})().catch(e=>{console.error(e);process.exitCode=1;});
