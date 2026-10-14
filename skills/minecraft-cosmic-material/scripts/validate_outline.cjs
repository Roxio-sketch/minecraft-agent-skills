const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {selectBrowser}=require('./preview_validation_support.cjs');
async function main(){
 const out=path.resolve(process.argv[2]||'outline-validation');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({executablePath:selectBrowser(chromium,process.env.COSMIC_BROWSER),headless:true});
 const checks=[],errors=[];
 function check(name,ok){checks.push({name,passed:!!ok});if(!ok)throw Error(name);}
 try{
 const page=await browser.newPage({viewport:{width:1366,height:960}});page.on('pageerror',e=>errors.push(e.message));
 await page.goto(pathToFileURL(path.resolve(__dirname,'../assets/outline-lab/index.html')).href);
 await page.waitForFunction(()=>window.outlineLab);await page.evaluate(()=>outlineLab.pause());
 const data=()=>page.evaluate(()=>document.querySelector('#preview').toDataURL());
 const pixels=()=>page.evaluate(()=>{const c=document.querySelector('#preview'),d=c.getContext('2d').getImageData(0,0,256,256).data;let n=0;for(let i=3;i<d.length;i+=4)if(d[i])n++;return n;});
 const first=await data();check('nonblank',await pixels()>1000);
 await page.waitForTimeout(180);check('pause_exact',first===await data());
 await page.locator('#width').fill('3');await page.locator('#width').dispatchEvent('input');check('width_changes_pixels',first!==await data());
 await page.locator('#body').uncheck();const edge=await pixels();check('outline_only',edge>0);
 await page.locator('#width').fill('0');await page.locator('#width').dispatchEvent('input');check('zero_width_transparent',await pixels()===0);
 await page.locator('#width').fill('1.5');await page.locator('#width').dispatchEvent('input');await page.locator('#body').check();
 for(const mode of ['solid','flow','stars','rainbow']){
  await page.selectOption('#mode',mode);await page.locator('#time').fill('0');await page.locator('#time').dispatchEvent('input');const a=await data();
  await page.locator('#time').fill('4');await page.locator('#time').dispatchEvent('input');check('time_'+mode,mode==='solid'?a===await data():a!==await data());
 }
 const saved=await page.evaluate(()=>outlineLab.recipe());const before=await data();
 await page.click('#reset');await page.evaluate(r=>outlineLab.importRecipe(r),saved);check('recipe_roundtrip',before===await data());
 check('invalid_palette_rejected',await page.evaluate(async r=>{r.parameters.colors=['#ffffff','#000000'];try{await outlineLab.importRecipe(r);return false;}catch{return true;}},saved));
 check('failed_import_preserves_pixels',before===await data());
 check('small_native_resolution',await page.evaluate(()=>document.querySelector('#small16').width===16&&document.querySelector('#small32').width===32));
 const download=page.waitForEvent('download');await page.click('#export');const f=await download;await f.saveAs(path.join(out,'recipe.json'));check('export_schema',JSON.parse(fs.readFileSync(path.join(out,'recipe.json'),'utf8')).schema===saved.schema);
 const pngDownload=page.waitForEvent('download');await page.click('#png');await(await pngDownload).saveAs(path.join(out,'preview.png'));
 await page.locator('#imageFile').setInputFiles(path.join(out,'preview.png'));await page.waitForFunction(()=>outlineLab.recipe().parameters.shape==='custom');
 const custom=await page.evaluate(()=>outlineLab.recipe());check('png_import',custom.parameters.image.startsWith('data:image/png;base64,'));await page.evaluate(r=>outlineLab.importRecipe(r),custom);check('embedded_png_roundtrip',await pixels()>0);
 await page.evaluate(r=>outlineLab.importRecipe(r),saved);await page.selectOption('#preset','flow');
 await page.screenshot({path:path.join(out,'desktop.png'),fullPage:true});
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(out,'mobile.png'),fullPage:true});
 check('mobile_no_horizontal_overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await page.click('#play');const t=await page.evaluate(()=>outlineLab.recipe().parameters.time);await page.waitForTimeout(220);check('play_advances_time',await page.evaluate(()=>outlineLab.recipe().parameters.time)>t);
 check('no_browser_errors',errors.length===0);
 }finally{await browser.close();fs.writeFileSync(path.join(out,'validation.json'),JSON.stringify({checks,errors},null,2));}
 console.log(JSON.stringify({status:'passed',checks:checks.length,out}));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
