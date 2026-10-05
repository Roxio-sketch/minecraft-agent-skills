// Visual regression coverage for unbounded shear and unresolved background rays.
const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(k,d)=>{const i=args.indexOf(k);return i<0?d:args[i+1];};
const output=path.resolve(arg('--output','long-playback-evidence'));
const checks=[];let failure=null;
function check(name,passed,detail){checks.push({name,passed,detail});if(!passed)throw Error(name+': '+JSON.stringify(detail));}
const delta=(a,b)=>a.pixels.reduce((s,v,i)=>s+(i%4===3?0:Math.abs(v-b.pixels[i])),0)/(a.width*a.height*3);
const roughness=f=>{let total=0;for(let y=1;y<f.height-1;y++)for(let x=1;x<f.width-1;x++){const i=(y*f.width+x)*4+1;total+=Math.abs(2*f.pixels[i]-f.pixels[i-4]-f.pixels[i+4])+Math.abs(2*f.pixels[i]-f.pixels[i-f.width*4]-f.pixels[i+f.width*4]);}return total/(f.width*f.height);};
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
 try{
  const page=await browser.newPage({viewport:{width:1360,height:1000}});
  await page.goto(arg('--url','http://127.0.0.1:8771/'));await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
  check('initialized',await page.evaluate(()=>!!window.blackHolePreview?.ready));
  await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=320;});
  const set=(p,t)=>page.evaluate(({p,t})=>blackHolePreview.set(p,t),{p,t});
  const frame=(debug=0)=>page.evaluate(debug=>blackHolePreview.frame(0,debug),debug);
  const capture=async name=>{const url=await page.evaluate(()=>document.getElementById('scene').toDataURL());fs.writeFileSync(path.join(output,name+'.png'),Buffer.from(url.split(',')[1],'base64'));};
  for(const style of ['ribbons','dust','cinematic']){
   const preset=await page.evaluate(style=>blackHolePreview.catalog.recipes.find(r=>r.diskStyle===style),style);
   let early;
   for(const t of [0,1.7,277.85,99999,100000]){
    await set(preset,t);const f=await frame();check(style+'_time_'+t,f.glError===0&&new Set(f.pixels).size>100);await capture(style+'-'+t);
    const r=roughness(f);if(t===0)early=r;
    if(['ribbons','cinematic'].includes(style)&&t>1.7)check(style+'_no_growing_spatial_frequency_'+t,r<early*1.7,{early,late:r});
   }
   await set({...preset,backgroundStyle:'black',backgroundSpin:0,orbitSpeed:0,infallSpeed:0,speed:.5},0);
   const first=await frame();await set({},.5);check(style+'_rotation_visible',delta(first,await frame())>.1);
   if(style==='ribbons'||style==='cinematic'){
    await set({},Math.PI*4*7957);const after=await frame();
    check(style+'_uniform_rotation_after_7957_turns',delta(first,after)<.35,{meanDifference:delta(first,after)});
   }
   for(const [steps,tilt] of [[96,-55],[160,25],[256,-22]]){
    await set({...preset,steps,tilt,thickness:.045},277.85);const f=await frame();check(style+'_quality_'+steps,f.glError===0&&new Set(f.pixels).size>100);await capture(style+'-quality-'+steps);
   }
  }
  const preset=await page.evaluate(()=>blackHolePreview.catalog.recipes.find(r=>r.diskStyle==='ribbons'));
  // Force a tiny budget to exercise the fallback even when production presets
  // converge. A test with zero exhausted rays would otherwise pass vacuously.
  const kernel=await (await page.request.get(new URL('black-hole.glsl',page.url()).href)).text();
  await page.route('**/black-hole.glsl',route=>route.fulfill({contentType:'text/plain',body:kernel.replace('if(i>=uSteps*2) break;','if(i>=uSteps/4) break;')}));
  await page.reload();await page.waitForFunction(()=>window.blackHolePreview?.ready,undefined,{timeout:120000});
  await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=320;});
  await set({...preset,inner:.94,outer:2.9,viewDistance:7.8,backgroundStyle:'stars',density:0,halo:0,particles:false},277.85);
  const diagnostic=await frame(1),background=await frame();let unresolved=0,leaked=0;
  // Only test interiors of exhausted regions: resolved neighbors legitimately
  // contribute at the supersampled boundary.
  for(let y=1;y<319;y++)for(let x=1;x<319;x++){
   const i=(y*320+x)*4;if([i,i-4,i+4,i-1280,i+1280].every(j=>diagnostic.pixels[j+1]>128)){
    unresolved++;if(Math.max(...background.pixels.slice(i,i+3))>1)leaked++;
   }
  }
  check('no_background_from_unresolved_rays',unresolved>100&&leaked===0,{unresolved,leaked});
  await page.unroute('**/black-hole.glsl');await page.reload();await page.waitForFunction(()=>window.blackHolePreview?.ready,undefined,{timeout:120000});
  await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=320;});
  await set({...preset,backgroundStyle:'black',density:0,halo:0,particles:false},0);
  check('pure_black_background', (await frame()).pixels.every((v,i)=>i%4===3||v===0));
  await set(preset,277.85);
  const snapshot=await page.evaluate(()=>blackHolePreview.snapshot());await page.evaluate(s=>blackHolePreview.importSnapshot(s),snapshot);
  check('background_recipe_roundtrip',JSON.stringify(snapshot)===JSON.stringify(await page.evaluate(()=>blackHolePreview.snapshot())));
  const legacy=await page.evaluate(()=>{const s=blackHolePreview.snapshot();delete s.parameters.backgroundStyle;blackHolePreview.importSnapshot(s);return blackHolePreview.snapshot();});
  check('legacy_background_remains_stars',legacy.parameters.backgroundStyle==='stars');
  await set(preset,277.85);await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=640;blackHolePreview.set({});});
  await capture('ribbons-desktop-640');await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
  check('mobile_no_overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 }catch(e){failure=e.message;throw e;}finally{await browser.close();fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify({status:failure?'failed':'passed',failure,checks},null,2));}
 console.log(JSON.stringify({status:'passed',checks:checks.length}));
})().catch(e=>{console.error(e);process.exitCode=1;});
