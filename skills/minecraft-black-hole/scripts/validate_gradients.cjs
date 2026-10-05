const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(key,fallback)=>{const i=args.indexOf(key);return i<0?fallback:args[i+1];};
const output=path.resolve(arg('--output','gradient-evidence'));
const checks=[];let failure=null;
function check(name,passed,detail){checks.push({name,passed:!!passed,detail});if(!passed)throw Error(name+': '+JSON.stringify(detail));}
function difference(a,b){
  let count=0,sum=0;
  for(let i=0;i<a.pixels.length;i+=4){
    const d=Math.max(Math.abs(a.pixels[i]-b.pixels[i]),Math.abs(a.pixels[i+1]-b.pixels[i+1]),Math.abs(a.pixels[i+2]-b.pixels[i+2]));
    if(d>1)count++;sum+=d;
  }
  return {count,sum};
}
(async()=>{
  fs.mkdirSync(output,{recursive:true});
  const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
  try{
    const page=await browser.newPage({viewport:{width:1480,height:1000},deviceScaleFactor:1});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(arg('--url','http://127.0.0.1:8771/?v=3.2'));
    await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
    check('initialized',await page.evaluate(()=>!!window.blackHolePreview?.ready),await page.evaluate(()=>window.blackHoleError));
    await page.evaluate(()=>{const c=document.getElementById('scene');c.width=320;c.height=200;});
    const set=(p,t=1.7)=>page.evaluate(({p,t})=>blackHolePreview.set(p,t),{p,t});
    const frame=()=>page.evaluate(()=>blackHolePreview.frame());
    const recipe=await page.evaluate(()=>blackHolePreview.catalog.recipes.find(r=>r.name==='虹彩视界 · 全域渐变'));
    check('gradient_recipe',!!recipe&&recipe.haloGradientMix===1&&recipe.diskGradientPosition>0&&recipe.skyGradientPosition>0);
    const quiet={...recipe,density:0,particles:false,halo:1,haloRing:1,haloDisk:0,haloAura:0,skyStrength:0,backgroundStyle:'black',bloom:0,coreColor:'#000000',coreEdgeColor:'#000000',coreGradientStrength:0,speed:0,infallSpeed:0,backgroundSpin:0};
    await set({...quiet,halo:0});const black=await frame();
    await set(quiet);const ring=await frame();check('ring_visible',difference(ring,black).count>100,difference(ring,black));
    await page.evaluate(()=>{const c=document.getElementById('scene');c.width=960;c.height=600;blackHolePreview.set({});});
    await page.locator('#scene').screenshot({path:path.join(output,'halo-ring-isolated.png')});
    await page.evaluate(()=>{const c=document.getElementById('scene');c.width=320;c.height=200;blackHolePreview.set({});});
    await set({haloGradientStart:'#ff0000',haloGradientEnd:'#0000ff'});const ringA=await frame();
    await set({haloGradientStart:'#0000ff',haloGradientEnd:'#ff0000'});const ringB=await frame();check('ring_inner_outer_colors',difference(ringA,ringB).count>20,difference(ringA,ringB));
    await set({haloRadius:.82});const radiusA=await frame();await set({haloRadius:1.5});const radiusB=await frame();check('ring_radius_changes_shape',difference(radiusA,radiusB).count>100,difference(radiusA,radiusB));
    await set({haloWidth:.05});const widthA=await frame();await set({haloWidth:.35});const widthB=await frame();check('ring_width_changes_shape',difference(widthA,widthB).count>100,difference(widthA,widthB));
    await set({haloSharpness:.6});const soft=await frame();await set({haloSharpness:7});const sharp=await frame();check('ring_sharpness_changes_shape',difference(soft,sharp).count>20,difference(soft,sharp));
    await set({haloRing:0,haloDisk:1,haloAura:0,haloDiskGradientMix:0,haloDiskInnerColor:'#ff0000',haloDiskOuterColor:'#0000ff'});const diskOld=await frame();
    await set({haloDiskInnerColor:'#00ff00',haloDiskOuterColor:'#ffff00'});check('disk_halo_old_palette_when_mix_zero',difference(diskOld,await frame()).count===0);
    await set({haloDiskGradientMix:1});check('disk_halo_gradient',difference(diskOld,await frame()).count>20);
    await set({haloDisk:0,haloAura:1,haloAuraGradientMix:0,haloAuraInnerColor:'#ff0000',haloAuraOuterColor:'#0000ff'});const auraOld=await frame();
    await set({haloAuraInnerColor:'#00ff00',haloAuraOuterColor:'#ffff00'});check('aura_old_palette_when_mix_zero',difference(auraOld,await frame()).count===0);
    await set({haloAuraGradientMix:1});check('aura_gradient',difference(auraOld,await frame()).count>20);
    await set({...quiet,halo:0,density:6,hot:'#ffffaa',cool:'#330088',diskGradientPosition:0,diskMid:'#ff0000'});const diskTwo=await frame();
    await set({diskMid:'#00ff00'});check('disk_old_palette_when_position_zero',difference(diskTwo,await frame()).count===0);
    await set({diskGradientPosition:.5});check('disk_three_color_gradient',difference(diskTwo,await frame()).count>100);
    await set({...quiet,halo:0,coreColor:'#140d27',coreEdgeColor:'#ff0000',coreGradientStrength:0});const coreFlat=await frame();
    await set({coreEdgeColor:'#00ff00'});check('core_old_palette_when_strength_zero',difference(coreFlat,await frame()).count===0);
    await set({coreGradientStrength:1});check('core_edge_gradient',difference(coreFlat,await frame()).count>20);
    await set({...quiet,halo:0,particles:true,particleCount:1024,particleBrightness:3,particleSize:.05,particleGradientPosition:0,particleMid:'#ff0000'});const particleTwo=await frame();
    await set({particleMid:'#00ff00'});check('particle_old_palette_when_position_zero',difference(particleTwo,await frame()).count===0);
    await set({particleGradientPosition:.5});check('particle_three_color_gradient',difference(particleTwo,await frame()).count>20);
    await set({...quiet,halo:0,particles:false,skyStrength:1,backgroundStyle:'nebula',lensBackground:0,skyGradientPosition:0,skyColorFar:'#ff0000'});const skyFlat=await frame();
    await set({skyColorFar:'#00ff00'});check('sky_old_palette_when_position_zero',difference(skyFlat,await frame()).count===0);
    await set({skyGradientPosition:.55});check('sky_radial_gradient',difference(skyFlat,await frame()).count>500);
    await set(recipe,277.85);const long=await frame();check('long_time_gl_ok',long.glError===0);
    await page.evaluate(()=>{const c=document.getElementById('scene');c.width=960;c.height=600;blackHolePreview.set({},277.85);});
    await page.locator('#scene').screenshot({path:path.join(output,'gradient-277.85.png')});
    await set({},100000);const limit=await frame();check('limit_time_gl_ok',limit.glError===0);
    await set(recipe,1.7);
    await page.locator('#scene').screenshot({path:path.join(output,'gradient-main.png')});
    const snap=await page.evaluate(()=>blackHolePreview.snapshot());
    await page.evaluate(s=>blackHolePreview.importSnapshot(s),snap);
    check('recipe_roundtrip',JSON.stringify(snap)===JSON.stringify(await page.evaluate(()=>blackHolePreview.snapshot())));
    const old=await page.evaluate(()=>{const p={...blackHolePreview.catalog.recipes.find(r=>r.diskStyle==='classic')};for(const k of ['diskMid','diskGradientPosition','coreEdgeColor','coreGradientStrength','particleMid','particleGradientPosition','skyColorFar','skyGradientPosition','haloRadius','haloWidth','haloSharpness','haloGradientStart','haloGradientEnd','haloGradientMix','haloDiskInnerColor','haloDiskOuterColor','haloDiskGradientMix','haloAuraInnerColor','haloAuraOuterColor','haloAuraGradientMix'])delete p[k];blackHolePreview.importSnapshot({schema:'black-hole-preview/v1',parameters:p,time:1.7});return blackHolePreview.snapshot().parameters;});
    check('legacy_gradients_disabled',old.diskGradientPosition===0&&old.coreGradientStrength===0&&old.haloGradientMix===0&&old.haloDiskGradientMix===0&&old.haloAuraGradientMix===0&&old.particleGradientPosition===0&&old.skyGradientPosition===0);
    check('unknown_field_rejected',await page.evaluate(()=>{try{blackHolePreview.set({alienGradient:1});return false;}catch{return true;}}));
    check('no_page_errors',errors.length===0,errors);
  }catch(e){failure=e.message;throw e;}
  finally{await browser.close();fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify({status:failure?'failed':'passed',failure,checks},null,2));}
  console.log(JSON.stringify({status:'passed',checks:checks.length}));
})().catch(e=>{console.error(e);process.exitCode=1;});
