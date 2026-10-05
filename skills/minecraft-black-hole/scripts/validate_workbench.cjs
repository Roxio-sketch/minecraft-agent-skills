const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(n,d)=>{const i=args.indexOf(n);return i<0?d:args[i+1];};
const output=path.resolve(arg('--output','workbench-evidence'));
const checks=[],errors=[];let failure=null;
const check=(name,passed,detail)=>{checks.push({name,passed,detail});if(!passed)throw Error(name+': '+JSON.stringify(detail));};
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});page.on('pageerror',e=>errors.push(e.message));
  await page.goto(arg('--url','http://127.0.0.1:8771/'));await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
  check('initialized',await page.evaluate(()=>!!window.blackHolePreview?.ready),await page.evaluate(()=>window.blackHoleError));
  await page.evaluate(()=>blackHolePreview.set(blackHolePreview.catalog.recipes.find(r=>r.diskStyle==='granular')));
  const original=await page.evaluate(()=>blackHolePreview.snapshot());
  check('combined_default',original.schema==='black-hole-preview/v2'&&original.parameters.particles&&original.parameters.halo>0&&original.parameters.diskStyle==='granular');
  const set=(p,t=1.7)=>page.evaluate(({p,t})=>blackHolePreview.set(p,t),{p,t});
  await page.evaluate(()=>{document.getElementById('scene').width=256;document.getElementById('scene').height=256;});
  const frame=(i=0)=>page.evaluate(i=>blackHolePreview.frame(i),i);
  const difference=(a,b)=>{let count=0;for(let i=0;i<a.pixels.length;i+=4)if(a.pixels.slice(i,i+3).some((v,c)=>v!==b.pixels[i+c]))count++;return count;};
  const all=await frame();check('nonblank',new Set(all.pixels).size>100&&all.glError===0);
  await set({particles:false});const noDust=await frame();check('independent_particles_visible',difference(all,noDust)>30,difference(all,noDust));
  await set({halo:0});const noHalo=await frame();check('halo_contributes',difference(noDust,noHalo)>100,difference(noDust,noHalo));
  for(const [name,values] of [['ring',{haloRing:1,haloDisk:0,haloAura:0}],['disk',{haloRing:0,haloDisk:1,haloAura:0}],['aura',{haloRing:0,haloDisk:0,haloAura:1}]]){
   await set({halo:1,...values});const f=await frame();check('halo_'+name,difference(f,noHalo)>20&&f.glError===0,difference(f,noHalo));
  }
  await set({...original.parameters,speed:0,infallSpeed:0,halo:0},0);const dust0=await frame();await set({},.3);const dust1=await frame();check('particles_move_without_disk_motion',difference(dust0,dust1)>30);
  await set({particleSpeed:0},0);const frozen=await frame();await set({},3);check('all_speeds_zero_freezes',difference(frozen,await frame())===0);
  await set({particles:false,speed:.8},0);const rotation0=await frame();await set({},.5);check('disk_rotates_without_particles_or_inflow',difference(rotation0,await frame())>100);
  await set({speed:0,infallSpeed:.22},0);const inflow0=await frame();await set({},.5);check('disk_inflow_without_rotation',difference(inflow0,await frame())>100);
  await set({...original.parameters,mode:'blade',particles:false});const blade=await frame();await set({particles:true});const bladeDust=await frame();
  const outside=await page.evaluate(()=>{const a=blackHolePreview.frame();blackHolePreview.set({particles:false});const b=blackHolePreview.frame();let bad=0;for(let y=0;y<a.height;y++)for(let x=0;x<a.width;x++){const u=(x+.5)/a.width*2-1,v=(y+.5)/a.height*2-1,w=.24*Math.max(0,Math.min(1,(.85-v)/.23));if(!(Math.abs(u)<w&&v>-.8&&v<.85)){const i=(y*a.width+x)*4;if(a.pixels.slice(i,i+3).some((k,c)=>k!==b.pixels[i+c]))bad++;}}return bad;});
  check('blade_particle_mask',outside===0&&difference(blade,bladeDust)>0,{outside});
  await set({...original.parameters,particleCount:257,particleSpeed:.7,haloRing:.4,haloDisk:.6,haloAura:.8,particleHot:'#21d9ff'},2.3);
  const expected=await page.evaluate(()=>blackHolePreview.snapshot());
  const download=page.waitForEvent('download');await page.locator('#export').click();await(await download).saveAs(path.join(output,'recipe-v2.json'));
  await set({particleCount:1,halo:0});await page.setInputFiles('#import',path.join(output,'recipe-v2.json'));
  await page.waitForFunction(()=>blackHolePreview.snapshot().parameters.particleCount===257);
  check('v2_download_import_roundtrip',JSON.stringify(expected)===JSON.stringify(await page.evaluate(()=>blackHolePreview.snapshot())));
  await set({yaw:1,pitch:1});await page.locator('#reset').click();check('imported_view_reset',await page.evaluate(()=>blackHolePreview.snapshot().parameters.yaw)===expected.parameters.yaw);
  const legacy=await page.evaluate(()=>{const p={...blackHolePreview.catalog.recipes.find(r=>r.diskStyle==='classic')};for(const k of Object.keys(p))if(k.startsWith('particle')||k.startsWith('halo')||['diskStyle','backgroundSpin','orbitSpeed','bandDetail','dustScale','dustFill'].includes(k))delete p[k];return {schema:'black-hole-preview/v1',parameters:p,time:1.7,playing:false};});
  await page.evaluate(r=>blackHolePreview.importSnapshot(r),legacy);const old=await page.evaluate(()=>blackHolePreview.snapshot());
  check('legacy_keeps_classic_without_new_layers',old.parameters.diskStyle==='classic'&&!old.parameters.particles&&old.parameters.halo===0);
  const rejects=await page.evaluate(()=>{const cases=[{particleCount:1.5},{particles:1},{halo:4},{particleHot:'bad'},{unknownLayer:1}];return cases.map(p=>{const before=JSON.stringify(blackHolePreview.snapshot());try{blackHolePreview.set(p);return false;}catch(e){return before===JSON.stringify(blackHolePreview.snapshot());}});});check('invalid_fields_rejected_atomically',rejects.every(Boolean));
  await set(original.parameters,100000);await page.locator('#play').click();await page.waitForTimeout(150);check('time_cap',await page.evaluate(()=>blackHolePreview.snapshot().time)===100000);
  await set(original.parameters,1.7);for(const i of [1,2]){const f=await frame(i);check('small_canvas_'+f.width,f.glError===0&&new Set(f.pixels).size>10);}
  await page.evaluate(()=>{document.getElementById('scene').width=640;document.getElementById('scene').height=640;blackHolePreview.set({});});
  await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
  await page.locator('#play').click();await page.waitForTimeout(500);await page.locator('#play').click();check('play_advances',await page.evaluate(()=>blackHolePreview.snapshot().time)>1.7);
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
  check('mobile_no_horizontal_overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  check('no_page_errors',errors.length===0,errors);
 }catch(e){failure=e.message;throw e;}finally{await browser.close();fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify({status:!failure&&checks.every(c=>c.passed)?'passed':'failed',failure,checks},null,2));}
 console.log(JSON.stringify({checks:checks.length,status:'passed'}));
})().catch(e=>{console.error(e);process.exitCode=1;});
