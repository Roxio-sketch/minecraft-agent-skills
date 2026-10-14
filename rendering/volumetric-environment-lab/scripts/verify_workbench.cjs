/* Actual browser UI, deterministic frames and downloadable artifacts. */
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..'),out=path.join(root,'evidence','workbench');fs.mkdirSync(out,{recursive:true});
const presets=JSON.parse(fs.readFileSync(path.join(root,'workbench','presets.json'),'utf8'));
const gpu=JSON.parse(fs.readFileSync(path.join(root,'evidence','gpu','report.json'),'utf8'));
(async()=>{
  const b=await chromium.launch({headless:true,channel:process.argv[2]||'msedge',args:['--use-angle=d3d11']});
  const checks=[],errors=[];
  const check=(name,passed,actual)=>checks.push({name,passed:Boolean(passed),actual});
  try {
    const page=await b.newPage({viewport:{width:1440,height:900},acceptDownloads:true});
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(process.env.WORLD_VOLUME_URL||'http://127.0.0.1:8774/');
    check('workbench self-test',(await page.evaluate(async()=>await volumeLab.selfTest())).passed);
    async function capture(){return page.evaluate(()=>{
      const c=document.querySelector('#viewport'),g=c.getContext('webgl2'),pixels=new Uint8Array(c.width*c.height*4);
      g.readPixels(0,0,c.width,c.height,g.RGBA,g.UNSIGNED_BYTE,pixels);return Array.from(pixels);
    });}
    const hash=data=>crypto.createHash('sha256').update(Buffer.from(data)).digest('hex');
    const images=[];
    for(const preset of presets){
      const r={schema:preset.schema,name:preset.name,parameters:preset.parameters};
      await page.evaluate(async r=>{await volumeLab.setRecipe(r,{resetCamera:true});await volumeLab.setView('outside');await volumeLab.render(2);},r);
      const a=await capture();await page.evaluate(async()=>await volumeLab.render(2));const a2=await capture();
      check(preset.id+' deterministic paused frame',hash(a)===hash(a2));
      await page.locator('#viewport').screenshot({path:path.join(out,preset.id+'.png')});
      check(preset.id+' valid renderer',!(await page.evaluate(()=>volumeLab.getState())).failed);
      images.push({id:preset.id,name:preset.name,file:preset.id+'.png',sha256:hash(a)});
    }
    check('all actual preset frames distinct',new Set(images.map(i=>i.sha256)).size===presets.length);
    const flash=presets.find(p=>p.id==='cloud-flash');const p={...flash.parameters,speed:0,emissionSpeed:0};
    const time=gpu.witnesses.peak.time;
    await page.evaluate(async ({p,time})=>{await volumeLab.setRecipe({schema:volumeLab.getRecipe().schema,name:'独立闪光合同',parameters:p},{resetCamera:true});await volumeLab.setView('ground');await volumeLab.render(time);},{p,time});
    const lit=await capture();
    await page.evaluate(async ({p,time})=>{await volumeLab.setRecipe({schema:volumeLab.getRecipe().schema,parameters:{...p,mediumGain:0,surfaceGain:0}});await volumeLab.render(time);},{p,time});
    const off=await capture();check('flash changes actual composed frame',hash(lit)!==hash(off));
    await page.evaluate(async ({p,time})=>{await volumeLab.setRecipe({schema:volumeLab.getRecipe().schema,name:'蓝白云内闪光',parameters:p});await volumeLab.render(time);},{p,time});
    await page.locator('#viewport').screenshot({path:path.join(out,'flash-on.png')});
    const exported=await page.evaluate(()=>volumeLab.getRecipe());
    for(const [button,name] of [['#jsonButton','download.json'],['#pngButton','download.png'],['#zipButton','download.zip']]){
      const waiting=page.waitForEvent('download');await page.locator(button).click();const d=await waiting;await d.saveAs(path.join(out,name));
      check('download '+name,fs.statSync(path.join(out,name)).size>100);
    }
    check('downloaded JSON preserves every parameter',JSON.stringify(JSON.parse(fs.readFileSync(path.join(out,'download.json'),'utf8')))==JSON.stringify(exported));
    const changed={...exported,parameters:{...exported.parameters,seed:555}};
    await page.evaluate(async r=>await volumeLab.setRecipe(r),changed);
    await page.locator('#importFile').setInputFiles(path.join(out,'download.json'));
    check('real file import restores all parameters',JSON.stringify(await page.evaluate(()=>volumeLab.getRecipe()))===JSON.stringify(exported));
    await page.evaluate(async t=>await volumeLab.render(t),time);
    await page.screenshot({path:path.join(out,'workbench-desktop.png'),fullPage:true});
    const invalid=await page.request.post(new URL('/api/export',page.url()).href,{data:{...exported,parameters:{...exported.parameters,viewSteps:0}}});
    check('server rejects malformed export',invalid.status()===400,invalid.status());
    const mobile=await b.newPage({viewport:{width:390,height:844}});await mobile.goto(process.env.WORLD_VOLUME_URL||'http://127.0.0.1:8774/');
    await mobile.evaluate(async()=>{await volumeLab.ready;await volumeLab.render(0);});
    const layout=await mobile.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,controls:document.querySelectorAll('[data-field]').length}));
    check('390px no horizontal overflow',layout.scroll<=layout.width,layout);
    await mobile.screenshot({path:path.join(out,'workbench-mobile.png'),fullPage:true});await mobile.close();
    check('no uncaught browser errors',errors.length===0,errors);
    const report={ok:checks.every(c=>c.passed),checks,images,errors,
      coreSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'workbench','volume-core.glsl'))).digest('hex')};
    fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify({ok:report.ok,checks:checks.length,failed:checks.filter(c=>!c.passed),report:path.join(out,'report.json')}));
    if(!report.ok)process.exitCode=1;
  }finally{await b.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
