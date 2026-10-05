// Focused UI, import/export and thin-disk convergence tests. Requires the preview server.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(k,f)=>{let i=args.indexOf(k);return i<0?f:args[i+1];};
const url=arg('--url','http://127.0.0.1:8771/'),output=path.resolve(arg('--output','black-hole-regressions'));
const beforePath=arg('--before',null),checks=[],shots=[];
const compare=(a,b)=>{let error=0,large=0,max=0;for(let i=0;i<a.length;i+=4){let peak=0;for(let c=0;c<3;c++){const d=Math.abs(a[i+c]-b[i+c]);error+=d;peak=Math.max(peak,d);}if(peak>25)large++;max=Math.max(max,peak);}return {meanAbsolute:error/(a.length/4*3),fractionOver25:large/(a.length/4),max};};
const check=(name,passed,detail)=>checks.push({name,passed,detail});
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE),args:['--enable-webgl']});
 try{
  const errors=[],page=await browser.newPage({viewport:{width:1150,height:1100}});
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url);await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
  const initializationError=await page.evaluate(()=>window.blackHoleError);if(initializationError)throw Error(initializationError);
  const imported=await page.evaluate(()=>{
   const api=blackHolePreview,s=api.snapshot();s.parameters.name='Imported orbit';s.parameters.yaw=1;s.parameters.pitch=.8;
   api.importSnapshot(s);api.set({yaw:-.5,pitch:-.3});document.getElementById('reset').click();
   return {state:api.snapshot(),selected:document.getElementById('recipe').value,label:document.getElementById('recipe').selectedOptions[0].textContent};
  });
  check('imported_view_reset',imported.state.parameters.yaw===1&&imported.state.parameters.pitch===.8&&imported.selected==='custom',imported);
  await page.selectOption('#recipe',{label:'层叠光带 · Unity 研究'});
  const preset=await page.evaluate(()=>{blackHolePreview.set({yaw:1,pitch:1});document.getElementById('reset').click();return blackHolePreview.snapshot().parameters;});
  const expected=await page.evaluate(()=>blackHolePreview.catalog.recipes.find(r=>r.name==='层叠光带 · Unity 研究'));
  check('preset_view_reset_after_import',preset.yaw===expected.yaw&&preset.pitch===expected.pitch);
  const invalid=await page.evaluate(()=>{
   const api=blackHolePreview,start=JSON.stringify(api.snapshot()),out=[];
   for(const [kind,key] of [['parameter','bned'],['root','extra']]){
    const s=api.snapshot();if(kind==='parameter')s.parameters[key]=1;else s[key]=1;
    try{api.importSnapshot(s);out.push({kind,rejected:false});}catch(e){out.push({kind,rejected:e.message.includes(key),message:e.message,unchanged:JSON.stringify(api.snapshot())===start});}
   }
   try{api.set({dnesity:2});out.push({kind:'patch',rejected:false});}catch(e){out.push({kind:'patch',rejected:e.message.includes('dnesity'),unchanged:JSON.stringify(api.snapshot())===start});}
   return out;
  });check('unknown_fields_rejected_atomically',invalid.every(r=>r.rejected&&r.unchanged),invalid);
  await page.evaluate(()=>{blackHolePreview.set({},99999.98);document.getElementById('play').click();});
  await page.waitForFunction(()=>blackHolePreview.snapshot().time===100000,{},{timeout:5000});
  const cap=await page.evaluate(()=>{const s=blackHolePreview.snapshot();const paused=document.getElementById('play').textContent==='播放';blackHolePreview.importSnapshot(s);return {paused,time:s.time,roundtrip:JSON.stringify(s)===JSON.stringify(blackHolePreview.snapshot())};});
  check('playback_clamped_and_roundtrips',cap.paused&&cap.time===100000&&cap.roundtrip,cap);
  await page.click('#play');await page.waitForTimeout(150);
  const atLimit=await page.evaluate(()=>({time:blackHolePreview.snapshot().time,label:document.getElementById('play').textContent}));
  check('cannot_play_past_limit',atLimit.time===100000&&atLimit.label==='播放',atLimit);
  // Exercise browser file input, error presentation, and the actual exported download.
  const beforeInvalid=await page.evaluate(()=>blackHolePreview.snapshot());
  const bad={...beforeInvalid,parameters:{...beforeInvalid.parameters,bned:1.8}};
  await page.setInputFiles('#import',{name:'invalid.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(bad))});
  await page.waitForFunction(()=>document.getElementById('status').textContent.includes('bned'));
  check('unknown_field_visible_in_ui',JSON.stringify(await page.evaluate(()=>blackHolePreview.snapshot()))===JSON.stringify(beforeInvalid));
  await page.evaluate(()=>{const s=blackHolePreview.snapshot();s.parameters.yaw=1;s.parameters.pitch=.8;blackHolePreview.importSnapshot(s);});
  const downloadPromise=page.waitForEvent('download');await page.click('#export');const download=await downloadPromise;
  const exportPath=path.join(output,'exported-recipe.json');await download.saveAs(exportPath);
  await page.setInputFiles('#import',exportPath);await page.waitForFunction(()=>document.getElementById('recipe').value==='custom'&&!document.getElementById('status').textContent.includes('未知字段'));
  const exported=JSON.parse(fs.readFileSync(exportPath,'utf8'));
  check('download_file_reimports_at_limit',JSON.stringify(exported)===JSON.stringify(await page.evaluate(()=>blackHolePreview.snapshot())));
  await page.evaluate(()=>{blackHolePreview.set({},0);document.getElementById('play').click();});
  await page.setInputFiles('#import',{name:'invalid-during-play.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(bad))});
  await page.waitForFunction(()=>document.getElementById('status').textContent.includes('bned'));
  await page.waitForTimeout(200);
  check('import_error_survives_animation',await page.evaluate(()=>document.getElementById('status').textContent.includes('bned')));
  await page.evaluate(()=>blackHolePreview.set({},1.7));
  if(!args.includes('--ui-only')) {
  // Compare against the same artistic model integrated at 1024 path steps, not physical truth.
  const fixedText=await (await page.request.get(new URL('black-hole.glsl',url).href)).text();
  const referenceText=fixedText.replace('float ds=3.0*bound/float(uSteps);','float ds=3.0*bound/float(uSteps*4);').replace('i<512','i<2048').replace('if(i>=uSteps*2)','if(i>=uSteps*8)');
  if(referenceText===fixedText)throw Error('Cannot create numerical reference');
  const reference=await browser.newPage(),legacy=beforePath?await browser.newPage():null;
  await reference.route('**/black-hole.glsl',r=>r.fulfill({contentType:'text/plain',body:referenceText}));
  // Match the background seed function so before/after measures integration,
  // rather than an intentional change in the placement of background stars.
  const currentHash=fixedText.match(/float bhHash\(vec3 p\) \{[\s\S]*?\n\}/)?.[0];
  if(legacy)await legacy.route('**/black-hole.glsl',r=>{let text=fs.readFileSync(beforePath,'utf8');if(currentHash)text=text.replace(/float bhHash\(vec3 p\) \{[\s\S]*?\n\}/,currentHash);return r.fulfill({contentType:'text/plain',body:text});});
  for(const p of [reference,legacy].filter(Boolean)){await p.goto(url);await p.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});const err=await p.evaluate(()=>window.blackHoleError);if(err)throw Error(err);}
  const render=async(p,params,file)=>{
   const f=await p.evaluate(params=>{const c=document.getElementById('scene');c.width=c.height=320;blackHolePreview.set({...blackHolePreview.catalog.recipes.find(r=>r.diskStyle==='classic'),...params},1.7);const f=blackHolePreview.frame();return {...f,png:c.toDataURL()};},params);
   fs.writeFileSync(path.join(output,file),Buffer.from(f.png.split(',')[1],'base64'));shots.push(file);if(f.glError)throw Error(`GL error ${f.glError}`);return f;
  };
  for(const [id,params] of [
   ['wide-thin',{thickness:.045,outer:3.5,tilt:0,pitch:.9}],
   ['strong-bend',{core:.2,inner:.55,bend:2,thickness:.045}],
   ['thin-edge',{thickness:.045,tilt:0,pitch:0,yaw:0}]
  ]){
   const low=await render(page,{...params,steps:96},`${id}-fixed-96.png`);
   const high=await render(page,{...params,steps:256},`${id}-fixed-256.png`);
   const ref=await render(reference,{...params,steps:256},`${id}-reference-1024.png`);
   const lowDiff=compare(low.pixels,ref.pixels),highDiff=compare(high.pixels,ref.pixels);
   const details={low:lowDiff,high:highDiff,reference:'same model, 1024 path steps; no physical truth claim'};
   if(legacy){const old=await render(legacy,{...params,steps:96},`${id}-before-96.png`);details.before={...compare(old.pixels,ref.pixels),scope:'Original integrator, matched current background hash'};}
   // Detect local errors that an overall brightness average hides; also inspect saved images.
   check(`${id}_local_convergence`,lowDiff.fractionOver25<.01&&highDiff.fractionOver25<.005,details);
  }
  }
  check('no_page_errors',errors.length===0,errors);
 }finally{await browser.close();}
 const result={status:checks.every(c=>c.passed)?'passed':'failed',scope:'UI regressions and numerical/visual preview checks; not Forge certification',checks,shots};
 fs.writeFileSync(path.join(output,'regressions.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));if(result.status!=='passed')process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
