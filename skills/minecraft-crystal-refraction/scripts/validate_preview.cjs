// Run against the loopback server. Requires Playwright and an installed browser.
// Usage: node validate_preview.cjs --url http://127.0.0.1:8771 --out <evidence> [--browser <exe>]
const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
const args=Object.fromEntries(process.argv.slice(2).reduce((a,v,i,all)=>{if(v.startsWith('--'))a.push([v.slice(2),all[i+1]]);return a;},[]));
const url=args.url||'http://127.0.0.1:8771';
if(!['127.0.0.1','localhost'].includes(new URL(url).hostname))throw Error('Use the local preview server');
if(!args.out)throw Error('--out required');
const out=path.resolve(args.out);fs.mkdirSync(out,{recursive:true});
const difference=(a,b)=>{if(a.length!==b.length)throw Error('Unequal frames');let changed=0,max=0;for(let i=0;i<a.length;i++){const d=Math.abs(a[i]-b[i]);changed+=d>0;max=Math.max(max,d);}return {changed,max};};
(async()=>{
 const browser=await chromium.launch({headless:true,...(args.browser?{executablePath:args.browser}:{}),args:['--enable-webgl']});
 const checks=[],errors=[];let renderer;
 const check=(name,pass,detail)=>checks.push({name,passed:!!pass,detail});
 try{
  const page=await browser.newPage({viewport:{width:1380,height:1120},deviceScaleFactor:1});
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url,{waitUntil:'networkidle'});await page.waitForFunction(()=>window.crystalLab||window.crystalLabError);
  if(await page.evaluate(()=>window.crystalLabError))throw Error(await page.evaluate(()=>window.crystalLabError));
  const capture=async(patch={},w=192,h=128)=>{
   const r=await page.evaluate(({patch,w,h})=>window.crystalLab.capture(w,h,patch),{patch,w,h});
   if(r.error!==0)throw Error('WebGL error '+r.error);renderer=r.renderer;return r.pixels;
  };
  const plain=await capture({amount:0}), still=await capture({time:2});
  check('deterministic_seed_time_view',difference(still,await capture({time:2})).changed===0);
  const zero={bendPx:0,edgeLight:0,tintMix:0,blurPx:0};
  check('zero_effect_preserves_background',difference(plain,await capture(zero)).max<=1);
  check('index_one_without_dispersion_is_identity',difference(plain,await capture({ior:1,dispersion:0,edgeLight:0,tintMix:0,blurPx:0})).max<=1);
  const clear=await capture({dispersion:0,edgeLight:0,tintMix:0,blurPx:0});
  check('achromatic_refraction_moves_background',difference(plain,clear).changed>500);
  check('dispersion_has_independent_effect',difference(clear,await capture({dispersion:0.15,edgeLight:0,tintMix:0,blurPx:0})).changed>100);
  check('speed_zero_freezes_animation',difference(await capture({time:0,speed:0}),await capture({time:20,speed:0})).changed===0);
  check('nonzero_speed_animates_normals',difference(await capture({time:0,speed:0.5}),await capture({time:5,speed:0.5})).changed>100);
  check('cell_ids_do_not_animate',difference(await capture({debug:1,time:0}),await capture({debug:1,time:8})).changed===0);
  check('seed_changes_layout',difference(await capture({debug:1,seed:12}),await capture({debug:1,seed:13})).changed>500);
  const masked=await capture({shape:1});let outside=0,inside=0;
  for(let y=0;y<128;y++)for(let x=0;x<192;x++){
   const r=Math.hypot(((x+0.5)/192-0.5)*3,((y+0.5)/128-0.5)*2),i=(y*192+x)*4;
   const changed=plain.slice(i,i+3).some((v,j)=>v!==masked[i+j]);
   if(r>0.74&&changed)outside++;if(r<0.65&&changed)inside++;
  }
  check('mask_outside_is_exactly_unchanged',outside===0,{outside,inside});check('mask_inside_has_effect',inside>100);
  check('view_changes_refraction',difference(await capture({view:[0,0]}),await capture({view:[0.4,-0.3]})).changed>100);
  check('bounded_blur_changes_sampling',difference(await capture({blurPx:0}),await capture({blurPx:3})).changed>100);
  for(const [i,name] of ['ice','cosmic','amber'].entries()){
   await page.evaluate(i=>{const p=window.crystalLab.presets[i];window.crystalLab.setState({...p,time:2,view:[0,0],debug:0,amount:1});},i);
   for(const size of [[900,600],[16,16],[32,32]]){
    const a=await capture({},...size);const colors=new Set();for(let j=0;j<a.length;j+=4)colors.add(a.slice(j,j+3).join(','));
    check(`${name}_${size[0]}x${size[1]}_nonblank`,colors.size>10,{colors:colors.size});
    const uri=await page.evaluate(([w,h])=>window.crystalLab.png(w,h),size);
    fs.writeFileSync(path.join(out,`${name}-${size[0]}.png`),Buffer.from(uri.split(',')[1],'base64'));
   }
  }
  const exported=await page.evaluate(()=>window.crystalLab.exportRecipe());const roundtrip=await capture();
  await page.evaluate(()=>window.crystalLab.setState({seed:20,bendPx:0}));
  await page.evaluate(e=>window.crystalLab.importRecipe(e),exported);
  check('recipe_roundtrip_pixels',difference(roundtrip,await capture()).changed===0);
  const invalid=[{schema:'wrong',state:exported.state},{schema:exported.schema,state:{...exported.state,dispersion:-1}},{schema:exported.schema,state:{...exported.state,extra:1}}];
  for(let i=0;i<invalid.length;i++){
   const rejected=await page.evaluate(e=>{try{window.crystalLab.importRecipe(e);return false;}catch{return true;}},invalid[i]);
   check('invalid_import_'+i,rejected&&difference(roundtrip,await capture()).changed===0);
  }
  await page.selectOption('#preset','1');await page.click('#pause');await page.waitForTimeout(180);await page.click('#pause');
  check('play_pause_ui',await page.evaluate(()=>window.crystalLab.getState().time>0));
  await page.click('#reset');check('reset_ui',await page.evaluate(()=>window.crystalLab.getState().time===0));
  const compareAmount=await page.evaluate(()=>window.crystalLab.getState().amount);
  await page.click('#before');check('compare_ui',await page.locator('#before').textContent()==='返回折射'&&await page.evaluate(a=>window.crystalLab.getState().amount===a,compareAmount));await page.click('#before');
  await page.selectOption('#background','0');await page.selectOption('#shape','2');
  check('background_shape_ui',await page.evaluate(()=>window.crystalLab.getState().background===0&&window.crystalLab.getState().shape===2));
  await page.selectOption('#preset','1');await page.locator('#view').screenshot({path:path.join(out,'showcase.png')});await page.screenshot({path:path.join(out,'workbench.png'),fullPage:true});
  check('no_javascript_errors',errors.length===0,errors);
  fs.writeFileSync(path.join(out,'recipe.json'),JSON.stringify(await page.evaluate(()=>window.crystalLab.exportRecipe()),null,2));
 }finally{await browser.close();}
 const result={scope:'Original WebGL 2 preview and UI only. Not Forge, native OpenGL or performance certification.',renderer,checks};
 fs.writeFileSync(path.join(out,'validation.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify({passed:checks.filter(c=>c.passed).length,total:checks.length,failed:checks.filter(c=>!c.passed),renderer,out}));
 if(checks.some(c=>!c.passed))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
