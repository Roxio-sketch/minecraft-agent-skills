// Analytic water material evidence. This is not an FFT or Minecraft runtime test.
const fs=require('node:fs'),path=require('node:path');
const {options,startServer,stopChild,selectBrowser,verifyService,assetDigest}=require('./preview_validation_support.cjs');
const settings=options(process.argv.slice(2)),checks=[];
const check=(name,passed,detail)=>{checks.push({name,passed:!!passed,detail});if(!passed)process.exitCode=1;};
fs.mkdirSync(settings.out,{recursive:true});
async function run(){
  const {chromium}=require('playwright');let browser,owned,service,record;
  try{
    if(!settings.url){owned=await startServer(settings.python,'fantasy');settings.url=owned.url;}
    service=await verifyService(settings.url,'fantasy');
    browser=await chromium.launch({executablePath:selectBrowser(chromium,settings.browser),headless:true,args:['--enable-webgl']});
    const page=await browser.newPage({viewport:{width:1380,height:1100},acceptDownloads:true});page.setDefaultTimeout(60000);
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(service.url);await page.waitForFunction(()=>window.fantasyLab!==undefined);
    check('shader_ready',await page.evaluate(()=>fantasyLab.ready),await page.evaluate(()=>fantasyLab.error));
    await page.evaluate(()=>{
      fantasyLab.selectPreset('tidal-water');
      const delta=(a,b)=>{let changed=0,sum=0,max=0;for(let i=0;i<a.length;i+=4){let any=false;for(let c=0;c<3;c++){const d=Math.abs(a[i+c]-b[i+c]);any||=d>0;sum+=d;max=Math.max(max,d);}if(any)changed++;}return{changedPixels:changed,maxChannelDifference:max,meanChannelDifference:sum/(a.length/4*3)};};
      const stats=a=>{let sum=0,lit=0,clipped=0;for(let i=0;i<a.length;i+=4){sum+=a[i]*.2126+a[i+1]*.7152+a[i+2]*.0722;if(a[i]+a[i+1]+a[i+2])lit++;if(Math.max(a[i],a[i+1],a[i+2])===255)clipped++;}return{meanLuma:sum/(a.length/4),lit,clippedFraction:clipped/(a.length/4)};};
      const png=()=>document.querySelector('#scene').toDataURL('image/png').split(',')[1];
      window.waterTest={delta,stats,png,defaults:fantasyLab.recipe().parameters};
    });
    for(const [label,height] of [['平静浅潭',.10],['深海急浪',.50],['青碧风浪',.30]]){
      await page.getByRole('button',{name:label,exact:true}).click();
      check('water_variant_button:'+label,await page.evaluate(height=>fantasyLab.recipe().parameters.waveHeight===height,height));
    }
    const result=await page.evaluate(async()=>{
      const {delta,stats,png,defaults}=waterTest;
      fantasyLab.render({parameters:defaults,time:1.25,view:[0,0],shape:0});
      const baseline=fantasyLab.capture(),responses=[];
      for(const [key,value] of Object.entries({waveHeight:.08,waveLength:4.8,windAngle:160,choppiness:0,foam:0,roughness:.5,waterOpacity:.35,waterLightAzimuth:40,waterLightElevation:.2})){
        fantasyLab.render({parameters:{...defaults,[key]:value},time:1.25});
        responses.push({key,value,...delta(baseline,fantasyLab.capture())});
      }
      fantasyLab.render({parameters:{...defaults,waveHeight:0},time:0});const flat=fantasyLab.capture();
      fantasyLab.render({time:7.5});const flatFreeze=delta(flat,fantasyLab.capture());
      fantasyLab.render({parameters:{...defaults,windAngle:0},time:2});const wind=fantasyLab.capture();
      fantasyLab.render({parameters:{windAngle:360}});const windWrap=delta(wind,fantasyLab.capture());
      const extremes=[];
      for(const high of [false,true])for(const view of [[0,0],[1.2,-1.2],[-1.2,1.2]]){
        fantasyLab.render({parameters:{...defaults,waveHeight:high?.65:0,waveLength:high?.55:6,choppiness:high?1:0,foam:high?1:0,roughness:high?.04:.55,scale:high?3:.3,depth:high?.5:6},time:3.4,view});
        extremes.push({high,view,stats:stats(fantasyLab.capture()),errors:fantasyLab.diagnostics().errors});
      }
      const variants=[];
      for(const [id,p] of Object.entries({calm:{waveHeight:.1,waveLength:3.8,choppiness:.2,foam:.04,roughness:.12,depth:1.2,speed:.35},windy:{},storm:{waveHeight:.5,waveLength:1.4,choppiness:1,foam:.85,roughness:.3,depth:3.4,speed:.8,accent:[.055,.18,.21],base:[.004,.015,.03]}})){
        fantasyLab.render({parameters:{...defaults,...p},time:1.5,view:[0,0],shape:0});
        variants.push({id,recipe:fantasyLab.recipe(),png:png(),stats:stats(fantasyLab.capture())});
      }
      fantasyLab.render({parameters:defaults,time:0,view:[0,0],shape:0});
      const canvas=document.createElement('canvas');canvas.width=320;canvas.height=216;
      const ctx=canvas.getContext('2d',{willReadFrequently:true}),frames=[],deltas=[];let previous;
      for(let i=0;i<120;i++){
        fantasyLab.render({time:i/20});ctx.drawImage(document.querySelector('#scene'),0,0,320,216);
        const pixels=ctx.getImageData(0,0,320,216).data;
        if(previous)deltas.push({time:i/20,...delta(previous,pixels)});
        previous=pixels;frames.push(canvas.toDataURL('image/png').split(',')[1]);
        if(i%12===0)await new Promise(r=>requestAnimationFrame(r));
      }
      return{responses,flatFreeze,windWrap,extremes,variants,frames,deltas,recipe:fantasyLab.recipe(),diagnostics:fantasyLab.diagnostics()};
    });
    for(const response of result.responses)check('independent_parameter:'+response.key,response.changedPixels>1000&&response.meanChannelDifference>.05,response);
    check('zero_wave_height_is_static',result.flatFreeze.changedPixels===0,{tolerance:0,...result.flatFreeze});
    check('wind_0_equals_360',result.windWrap.changedPixels===0,{tolerance:0,...result.windWrap});
    for(const [i,e] of result.extremes.entries())check('extreme_render:'+i,e.stats.lit>400000&&e.stats.meanLuma>2&&e.stats.clippedFraction<.25&&e.errors.every(v=>v===0),e);
    check('continuous_wave_sequence',result.deltas.length===119&&result.deltas.every(d=>d.changedPixels>1000&&d.meanChannelDifference>.03),result.deltas);
    const dir=path.join(settings.out,'tidal-water');fs.mkdirSync(dir,{recursive:true});
    result.frames.forEach((png,i)=>fs.writeFileSync(path.join(dir,String(i).padStart(4,'0')+'.png'),Buffer.from(png,'base64')));delete result.frames;
    for(const v of result.variants){fs.writeFileSync(path.join(settings.out,v.id+'.png'),Buffer.from(v.png,'base64'));fs.writeFileSync(path.join(settings.out,v.id+'.json'),JSON.stringify(v.recipe,null,2));delete v.png;}
    fs.writeFileSync(path.join(settings.out,'motion.json'),JSON.stringify({scope:'Fixed-time 6-second water clip. Not a performance benchmark or Minecraft capture; review continuous motion visually.',fps:20,frames:120,source:{assetDigest:assetDigest('fantasy')},records:[{id:'tidal-water',name:'澄浪水镜',recipe:result.recipe,deltas:result.deltas,diagnostics:result.diagnostics}]},null,2));
    await page.evaluate(()=>fantasyLab.render({parameters:{...waterTest.defaults,waveHeight:.41,waveLength:4.2,windAngle:270,choppiness:.33,foam:.22,roughness:.42},time:2,shape:1}));
    const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#export').click()]);
    const recipePath=path.join(settings.out,'water-roundtrip.json');await download.saveAs(recipePath);
    const exported=JSON.parse(fs.readFileSync(recipePath,'utf8'));
    await page.evaluate(()=>fantasyLab.selectPreset('rune-vault'));await page.locator('#import-file').setInputFiles(recipePath);
    await page.waitForFunction(()=>fantasyLab.recipe().parameters.id==='tidal-water');
    const imported=await page.evaluate(()=>fantasyLab.recipe());
    check('all_water_parameters_roundtrip',JSON.stringify(imported)===JSON.stringify(exported),{exported,imported});
    const before=await page.evaluate(()=>fantasyLab.recipe());
    for(const key of ['waveHeight','waveLength','windAngle','choppiness','foam','roughness']){
      const bad=structuredClone(exported);delete bad.parameters[key];
      const filename=path.join(settings.out,'invalid-'+key+'.json');fs.writeFileSync(filename,JSON.stringify(bad));
      await page.locator('#import-file').setInputFiles(filename);await page.waitForFunction(()=>document.querySelector('#status').classList.contains('error'));
      check('missing_parameter_rejected:'+key,JSON.stringify(await page.evaluate(()=>fantasyLab.recipe()))===JSON.stringify(before));
    }
    await page.locator('#import-file').setInputFiles(recipePath);await page.waitForFunction(()=>!document.querySelector('#status').classList.contains('error'));
    for(const [name,width,height] of [['desktop',1380,1100],['mobile',390,844]]){
      await page.setViewportSize({width,height});await page.screenshot({path:path.join(settings.out,name+'.png'),fullPage:true});
      const layout=await page.evaluate(()=>({width:innerWidth,documentWidth:document.documentElement.scrollWidth,waterVisible:!document.querySelector('#water-sliders').hidden,controls:document.querySelectorAll('#water-sliders input').length}));
    check('water_controls_layout:'+name,layout.documentWidth<=layout.width&&layout.waterVisible&&layout.controls===9,layout);
    }
    check('javascript_errors',errors.length===0,errors);record=result;
  }catch(e){check('execution_exception',false,{message:e.message,stack:e.stack});}
  finally{
    if(browser)await browser.close();if(owned)await stopChild(owned.child);
    const validation={status:checks.every(c=>c.passed)?'passed':'failed',scope:'Standalone WebGL analytic water. Original Cosmic P3 remains unresolved.',assetDigest:assetDigest('fantasy'),service:service?.identity,checks,record};
    fs.writeFileSync(path.join(settings.out,'water-validation.json'),JSON.stringify(validation,null,2));
    console.log(JSON.stringify({status:validation.status,checks:checks.length,failed:checks.filter(c=>!c.passed).map(c=>c.name),out:settings.out}));
  }
}
run().catch(e=>{console.error(e);process.exitCode=1;});
