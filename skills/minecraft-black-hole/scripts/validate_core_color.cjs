// Core-color behavior in a real WebGL browser. Requires the bundled preview server.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(k,f)=>{const i=args.indexOf(k);return i<0?f:args[i+1];};
const url=arg('--url','http://127.0.0.1:8771/'),output=path.resolve(arg('--output','core-color-evidence'));
const before=arg('--before',null),checks=[],shots=[],errors=[];
const check=(name,passed,detail)=>checks.push({name,passed,detail});
const difference=(a,b)=>{let n=0;for(let i=0;i<a.length;i+=4)if(a[i]!==b[i]||a[i+1]!==b[i+1]||a[i+2]!==b[i+2])n++;return n;};
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE),args:['--enable-webgl']});
 let renderer;
 try{
  const page=await browser.newPage({viewport:{width:1180,height:1150},deviceScaleFactor:1});
  const ready=async p=>{p.on('pageerror',e=>errors.push(e.message));await p.goto(url);await p.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});const error=await p.evaluate(()=>window.blackHoleError);if(error)throw Error(error);await p.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=320;});};
  await ready(page);
  renderer=await page.evaluate(()=>blackHolePreview.renderer());
  const recipes=await page.evaluate(()=>blackHolePreview.catalog.recipes.filter(r=>r.diskStyle==='classic'));
  const set=p=>page.evaluate(p=>blackHolePreview.set(p,1.7),p);
  const frame=(index=0,debug=0)=>page.evaluate(([i,d])=>blackHolePreview.frame(i,d),[index,debug]);
  const shot=async(name,id='scene')=>{const file=name+'.png';const png=await page.locator('#'+id).evaluate(c=>c.toDataURL());fs.writeFileSync(path.join(output,file),Buffer.from(png.split(',')[1],'base64'));shots.push(file);};
  const first=recipes[0];
  check('all_presets_default_black',recipes.every(r=>r.coreColor==='#000000'));
  if(before){
   const legacy=await browser.newPage();
   await legacy.route('**/black-hole.glsl',r=>r.fulfill({contentType:'text/plain',body:fs.readFileSync(before,'utf8')}));await ready(legacy);
   for(let i=0;i<recipes.length;i++){
    await set({...recipes[i],mode:'scene'});const a=await frame();
    const b=await legacy.evaluate(p=>{blackHolePreview.set({...p,mode:'scene'},1.7);return blackHolePreview.frame();},recipes[i]);
    const changed=difference(a.pixels,b.pixels);check('legacy_black_pixels_'+i,changed===0&&a.glError===0&&b.glError===0,{changedPixels:changed});
   }
   await legacy.close();
  }
  await set({...first,mode:'scene'});
  const black=await frame(),mask=await frame(0,1);await shot('core-black');
  await page.locator('#coreColor').fill('#7d3cff');
  const chosen=await page.evaluate(()=>blackHolePreview.snapshot().parameters);
  check('picker_updates_only_core_color',chosen.coreColor==='#7d3cff'&&chosen.hot===first.hot&&chosen.cool===first.cool,chosen);
  const purple=await frame();let changedInside=0,changedOutside=0;
  for(let i=0;i<black.pixels.length;i+=4){const changed=black.pixels.slice(i,i+3).some((v,k)=>v!==purple.pixels[i+k]);if(changed){if(mask.pixels[i]>127)changedInside++;else changedOutside++;}}
  check('color_changes_only_captured_core',changedInside>100&&changedOutside===0,{changedInside,changedOutside});await shot('core-purple');
  for(const [name,hex] of [['cyan','#21d9ff'],['red','#ff244c'],['white','#ffffff']]){await page.locator('#coreColor').fill(hex);await shot('core-'+name);}
  await set({coreColor:'#ff0000'});const red=await frame();let foreground=0,changedForeground=0;
  for(let i=0;i<black.pixels.length;i+=4)if(mask.pixels[i]>127&&black.pixels[i+1]>40){foreground++;if(black.pixels[i+1]!==red.pixels[i+1]||black.pixels[i+2]!==red.pixels[i+2])changedForeground++;}
  check('foreground_disk_light_preserved',foreground>100&&changedForeground===0,{foregroundPixels:foreground,changedGreenBlue:changedForeground});
  // With disk emission disabled, the remaining core signal measures foreground absorption.
  await set({coreColor:'#ffffff',hot:'#000000',cool:'#000000',density:0});const clear=await frame(),clearMask=await frame(0,1);
  await set({density:first.density});const absorbed=await frame(),absorbedMask=await frame(0,1);let attenuated=0,brightened=0;
  for(let i=0;i<clear.pixels.length;i+=4)if(clearMask.pixels[i]>127&&absorbedMask.pixels[i]>127){if(clear.pixels[i]-absorbed.pixels[i]>30)attenuated++;if(absorbed.pixels[i]>clear.pixels[i])brightened++;}
  check('foreground_absorbs_core_source',attenuated>100&&brightened===0,{attenuatedPixels:attenuated,brightenedPixels:brightened});
  await set({...first,mode:'scene'});const smallBlack=[await frame(1),await frame(2)];
  await set({coreColor:'#7d3cff'});
  for(let i=1;i<=2;i++){const f=await frame(i),changed=difference(smallBlack[i-1].pixels,f.pixels),size=i===1?16:32;check('core_color_native_'+size,changed>0&&f.width===size&&f.height===size&&f.glError===0,{changedPixels:changed,width:f.width,height:f.height});await shot('core-purple-'+size,'small'+size);}
  await set({mode:'blade',coreColor:'#000000'});const bladeBlack=await frame();
  await set({coreColor:'#7d3cff'});const bladePurple=await frame();let outsideMask=0;
  for(let y=0;y<bladeBlack.height;y++)for(let x=0;x<bladeBlack.width;x++){
   const uvX=(x+.5)/bladeBlack.width*2-1,uvY=(y+.5)/bladeBlack.height*2-1,width=.24*Math.max(0,Math.min(1,(.85-uvY)/.23));
   if(Math.abs(uvX)<width&&uvY>-.8&&uvY<.85)continue;
   const i=(y*bladeBlack.width+x)*4;if(bladeBlack.pixels.slice(i,i+3).some((v,k)=>v!==bladePurple.pixels[i+k]))outsideMask++;
  }
  check('blade_mask_limits_core_color',outsideMask===0&&difference(bladeBlack.pixels,bladePurple.pixels)>0,{outsideMask});await shot('core-purple-blade');
  await set({...first,mode:'scene',coreColor:'#7d3cff'});
  const exportedBefore=await page.evaluate(()=>blackHolePreview.snapshot());
  const downloading=page.waitForEvent('download');await page.click('#export');const download=await downloading,exportPath=path.join(output,'purple-core-recipe.json');await download.saveAs(exportPath);
  await set({coreColor:'#ffffff'});await page.setInputFiles('#import',exportPath);await page.waitForFunction(()=>blackHolePreview.snapshot().parameters.coreColor==='#7d3cff');
  const reimported=await page.evaluate(()=>({snapshot:blackHolePreview.snapshot(),picker:document.getElementById('coreColor').value}));
  check('color_file_export_import',JSON.stringify(exportedBefore)===JSON.stringify(reimported.snapshot)&&reimported.picker==='#7d3cff'&&JSON.parse(fs.readFileSync(exportPath,'utf8')).parameters.coreColor==='#7d3cff');
  const legacyRecipe=structuredClone(exportedBefore);delete legacyRecipe.parameters.coreColor;
  await page.setInputFiles('#import',{name:'legacy.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(legacyRecipe))});await page.waitForFunction(()=>blackHolePreview.snapshot().parameters.coreColor==='#000000');
  const legacyDefault=await frame();check('legacy_missing_color_defaults_black',difference(black.pixels,legacyDefault.pixels)===0&&await page.locator('#coreColor').inputValue()==='#000000');
  const invalid=await page.evaluate(()=>{
   const api=blackHolePreview,out=[];
   for(const value of [null,3,'red','#fff','#ff00ff80','#zz0000',{}])for(const method of ['set','import']){
    const before=JSON.stringify(api.snapshot());let message='';try{if(method==='set')api.set({coreColor:value});else{const s=api.snapshot();s.parameters.coreColor=value;api.importSnapshot(s);}}catch(e){message=e.message;}
    out.push({value,method,rejected:message.includes('coreColor'),unchanged:JSON.stringify(api.snapshot())===before});
   }
   return out;
  });check('invalid_colors_rejected_atomically',invalid.every(c=>c.rejected&&c.unchanged),invalid);
  await set({coreColor:'#7d3cff'});await page.selectOption('#recipe',{label:'层叠光带 · Unity 研究'});
  const selectedCore=await page.evaluate(()=>blackHolePreview.catalog.recipes.find(r=>r.name==='层叠光带 · Unity 研究').coreColor);
  check('preset_resets_picker_to_own_core_color',await page.locator('#coreColor').inputValue()===selectedCore);
  await page.selectOption('#recipe',{label:'体积尘埃 · BlackHole 研究'});await page.locator('#coreColor').fill('#7d3cff');await page.screenshot({path:path.join(output,'workbench-core-color.png')});shots.push('workbench-core-color.png');
  check('no_page_errors',errors.length===0,errors);
 }finally{await browser.close();}
 const report={status:checks.every(c=>c.passed)?'passed':'failed',scope:'WebGL 2 preview only; no Forge runtime certification',created:new Date().toISOString(),renderer,checks,shots};
 fs.writeFileSync(path.join(output,'core-color.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));if(report.status!=='passed')process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
