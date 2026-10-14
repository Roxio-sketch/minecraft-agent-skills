// Run with NODE_PATH pointing to an installed Playwright package directory.
// Tests the browser study only. Does not launch or certify Minecraft.
const fs=require('node:fs');
const path=require('node:path');
const {options,assetDigest,difference,startServer,stopChild,selectBrowser,verifyService}=require('./preview_validation_support.cjs');
const {runFreezeSuite}=require('./freeze_suite.cjs');
const settings=options(process.argv.slice(2));
if(settings.help){console.log('node scripts/validate_preview.cjs [--python PATH] [--browser PATH] [--url LOOPBACK_URL] [--out DIR] [--freeze-runs 30]\nWithout --url, starts its own exclusive server on an OS-assigned port. Freeze checks use ZERO tolerance.');process.exit(0);}
const evidence=settings.out;fs.mkdirSync(evidence,{recursive:true});
const checks=[];
function check(name,condition,detail){checks.push({name,passed:!!condition,detail});if(!condition)process.exitCode=1;}
function stats(a){let lum=0,clip=0,nonzero=0;const colors=new Set();for(let i=0;i<a.length;i+=4){lum+=a[i]*0.2126+a[i+1]*0.7152+a[i+2]*0.0722;if(Math.max(a[i],a[i+1],a[i+2])===255)clip++;if(a[i]+a[i+1]+a[i+2]>0)nonzero++;colors.add(`${a[i]},${a[i+1]},${a[i+2]}`);}return {meanLuma:lum/(a.length/4),clippedFraction:clip/(a.length/4),nonzeroPixels:nonzero,uniqueColors:colors.size};}
(async()=>{
let browser,owned,diag,browserVersion;
try{
  let chromium;
  try{({chromium}=require('playwright'));}catch(e){throw Error('Playwright is required. Use an existing Node environment or NODE_PATH containing playwright. '+e.message);}
  if(!settings.url){owned=await startServer(settings.python);settings.url=owned.url;}
  const target=await verifyService(settings.url);check('service_identity_and_assets',true,target);
  browser=await chromium.launch({executablePath:selectBrowser(chromium,settings.browser),headless:true,args:['--enable-webgl']});
  browserVersion=browser.version();
  const page=await browser.newPage({viewport:{width:1380,height:1120},deviceScaleFactor:1,acceptDownloads:true});
  page.setDefaultTimeout(60000);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(target.url);
  await page.waitForFunction(()=>window.cosmicLab!==undefined);
  const ready=await page.evaluate(()=>({ready:cosmicLab.ready,error:cosmicLab.error}));
  check('webgl_compile_link',ready.ready,ready);
  if(!ready.ready)throw new Error(ready.error);
  const render=async o=>page.evaluate(o=>cosmicLab.render(o),o);
  const capture=async i=>page.evaluate(i=>cosmicLab.capture(i),i||0);
  const preset=async id=>page.evaluate(id=>cosmicLab.selectPreset(id),id);
  for(const id of ['deep-field','violet-nebula','ember-rift']){
    await preset(id);await render({view:[0,0],time:0,shape:0});
    const s=stats(await capture());check('visible_unclipped:'+id,s.uniqueColors>1000&&s.clippedFraction<0.02,s);
    await page.locator('#scene').screenshot({path:path.join(evidence,id+'.png')});
  }
  await preset('violet-nebula');await render({time:0,view:[0,0],shape:0});
  const a=await capture();await render({time:0});const b=await capture();
  check('fixed_state_deterministic',difference(a,b).changedPixels===0,difference(a,b));
  await render({time:12});const c=await capture();
  check('slow_animation_changes_image',difference(a,c).changedPixels>1000,difference(a,c));
  const frozen=await runFreezeSuite(page,settings.freezeRuns,evidence);
  check('zero_motion_is_static',frozen.zeroMotion.every(d=>d.changedPixels===0),{tolerance:0,samples:frozen.zeroMotion,firstFailure:frozen.firstFailure});
  check('repeated_fixed_state_deterministic',frozen.sameState.every(d=>d.changedPixels===0),{tolerance:0,samples:frozen.sameState});
  check('static_material_receives_canonical_time',frozen.uniforms.every(v=>v.length===3&&v.every(t=>t===0)),frozen.uniforms);
  await preset('violet-nebula');
  await render({time:0,view:[-0.6,0],parameters:{parallax:0}});const noParallax=await capture();
  await render({view:[0.6,0]});const noParallax2=await capture();
  check('disabled_parallax_ignores_view_position',difference(noParallax,noParallax2).changedPixels===0,difference(noParallax,noParallax2));
  await render({view:[-0.6,0],parameters:{parallax:0.85}});const left=await capture();
  await page.locator('#scene').screenshot({path:path.join(evidence,'parallax-left.png')});
  await render({view:[0.6,0]});const right=await capture();
  check('finite_depth_view_response',difference(left,right).changedPixels>1000,difference(left,right));
  await page.locator('#scene').screenshot({path:path.join(evidence,'parallax-right.png')});
  await render({view:[0,0],layers:{starsOn:1,cloudsOn:0,dustOn:1},parameters:{dust:0}});const clear=await capture();
  await render({parameters:{dust:2}});const obscured=await capture();
  let increases=0,darkens=0;for(let i=0;i<clear.length;i+=4){if(obscured[i]>clear[i]||obscured[i+1]>clear[i+1]||obscured[i+2]>clear[i+2])increases++;if(obscured[i]<clear[i]||obscured[i+1]<clear[i+1]||obscured[i+2]<clear[i+2])darkens++;}
  check('dust_attenuates_instead_of_adding_black_color',increases===0&&darkens>1000,{increases,darkens});
  await preset('violet-nebula');await render({shape:1,time:0,view:[0,0],layers:{starsOn:1,cloudsOn:1,dustOn:1}});const sword=await capture();
  await page.locator('#scene').screenshot({path:path.join(evidence,'sword-aperture.png')});
  for(const i of [1,2]){const s=stats(await capture(i));check('small_icon:'+i,s.uniqueColors>10&&s.clippedFraction<0.05,s);}
  await preset('ember-rift');const sword2=await capture();let exteriorChanged=0,interiorChanged=0;
  for(let y=0;y<540;y++)for(let x=0;x<800;x++){const i=(y*800+x)*4;if(sword[i]!==sword2[i]||sword[i+1]!==sword2[i+1]||sword[i+2]!==sword2[i+2]){if(x<280||x>520)exteriorChanged++;else interiorChanged++;}}
  check('material_stays_inside_sword_aperture',exteriorChanged===0&&interiorChanged>100,{exteriorChanged,interiorChanged});
  await preset('violet-nebula');await render({shape:0});
  const lumas=[];for(const steps of [8,16,24]){await render({parameters:{steps}});lumas.push({steps,...stats(await capture())});}
  check('step_count_not_brightness_multiplier',Math.max(...lumas.map(x=>x.meanLuma))/Math.min(...lumas.map(x=>x.meanLuma))<1.3,lumas);
  await render({parameters:{steps:16},shape:0});
  const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#export').click()]);
  const downloadPath=path.join(evidence,'exported-recipe.json');await download.saveAs(downloadPath);
  const exported=JSON.parse(fs.readFileSync(downloadPath,'utf8'));check('recipe_export',exported.schema==='cosmic-authoring-preview/v1'&&exported.parameters.id==='violet-nebula',exported);
  await page.screenshot({path:path.join(evidence,'workbench.png'),fullPage:true});
  diag=await page.evaluate(()=>cosmicLab.diagnostics());check('gl_errors',diag.errors.every(n=>n===0),diag);
  check('explicit_capture_state',diag.testMode&&diag.dither.every(x=>x===false)&&diag.captureTarget==='default-framebuffer',diag);
  // Exercise the actual animation loop and user's Pause button separately.
  const live=new URL(target.url);live.searchParams.delete('test');await page.goto(live.href);await page.waitForFunction(()=>window.cosmicLab?.ready===true);
  if(!(await page.evaluate(()=>cosmicLab.diagnostics().playing)))await page.locator('#motion').click();
  await page.waitForFunction(()=>cosmicLab.recipe().preview.time>0.15);await page.locator('#motion').click();
  const paused=await page.evaluate(async()=>{const a=cosmicLab.capture(),state=cosmicLab.recipe();await new Promise(resolve=>setTimeout(resolve,200));return {a,b:cosmicLab.capture(),before:state.preview.time,after:cosmicLab.recipe().preview.time,playing:cosmicLab.diagnostics().playing,uniforms:cosmicLab.diagnostics().materialTime};});
  check('user_pause_holds_time_and_pixels',!paused.playing&&paused.before===paused.after&&difference(paused.a,paused.b).changedPixels===0,{before:paused.before,after:paused.after,...difference(paused.a,paused.b)});
  check('pause_synchronizes_all_canvas_clocks',paused.uniforms.length===3&&paused.uniforms.every(t=>t===paused.uniforms[0])&&Math.abs(paused.uniforms[0]-paused.after)<0.00001,{time:paused.after,uniforms:paused.uniforms});
  check('javascript_errors',errors.length===0,errors);
}catch(e){check('execution_exception',false,{message:e.message,stack:e.stack});}
finally{
  if(browser)await browser.close();
  if(owned)await stopChild(owned.child);
  const result={assetDigest:assetDigest(),url:settings.url,freezeTolerance:0,browserVersion,status:checks.length>0&&checks.every(c=>c.passed)?'passed':'failed',checkedAt:new Date().toISOString(),scope:'Standalone WebGL 2 material-authoring study; no Minecraft runtime validation or performance certification.',diagnostics:diag,checks};
  fs.writeFileSync(path.join(evidence,'validation.json'),JSON.stringify(result,null,2));
  console.log(JSON.stringify({status:result.status,checks:checks.length,failed:checks.filter(c=>!c.passed).map(c=>c.name),record:path.join(evidence,'validation.json')}));
}
})().catch(e=>{console.error(e.message);process.exitCode=1;});
