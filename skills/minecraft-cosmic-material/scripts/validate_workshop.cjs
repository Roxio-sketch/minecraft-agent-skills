// Validate the combined workshop without merging its two recipe schemas.
const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
const {options,startServer,stopChild,selectBrowser,verifyService,difference}=require('./preview_validation_support.cjs');
const config=options(process.argv.slice(2)),checks=[];
const check=(name,passed,detail)=>checks.push({name,passed:!!passed,detail});
(async()=>{
  fs.mkdirSync(config.out,{recursive:true});let service,browser;
  try{
    if(!config.url)service=await startServer(config.python,'fantasy');
    const {url}=await verifyService(config.url||service.url,'fantasy');
    browser=await chromium.launch({executablePath:selectBrowser(chromium,config.browser),headless:true});
    const page=await browser.newPage({viewport:{width:1440,height:1050}}),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.goto(url);await page.waitForFunction(()=>window.fantasyLab?.ready);
    await page.evaluate(()=>fantasyLab.render({parameters:{seed:901},time:2}));
    const fantasy=await page.evaluate(()=>fantasyLab.recipe());
    await page.locator('[data-family="cosmic"]').click();
    const frame=page.frameLocator('#cosmic-workspace');
    await frame.locator('#status').filter({hasText:'已就绪'}).waitFor();
    const cosmic=page.frames().find(f=>new URL(f.url()).pathname==='/cosmic/index.html');
    check('three_original_presets',JSON.stringify(await cosmic.evaluate(()=>cosmicLab.presets))===JSON.stringify(['deep-field','violet-nebula','ember-rift']));
    for(const preset of ['deep-field','violet-nebula','ember-rift']){
      await frame.locator(`[data-preset="${preset}"]`).click();
      const before=await cosmic.evaluate(()=>{cosmicLab.render({time:0});return cosmicLab.capture();});
      const after=await cosmic.evaluate(()=>{cosmicLab.render({time:4});return cosmicLab.capture();});
      const delta=difference(before,after);
      check(preset+':animation',delta.changedPixels>100,delta);
      check(preset+':gpu',(await cosmic.evaluate(()=>cosmicLab.diagnostics().errors)).every(n=>n===0));
    }
    await frame.locator('#density').evaluate(input=>{input.value='1.21';input.dispatchEvent(new Event('input',{bubbles:true}));});
    for(const [id,value] of [['starSpeed','0.63'],['twinkle','0.88'],['twinkleRate','1.35']])
      await frame.locator('#'+id).evaluate((input,value)=>{input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));},value);
    await frame.locator('#starsOn').uncheck();
    const edited=await cosmic.evaluate(()=>cosmicLab.recipe());
    await page.locator('[data-family="fantasy"]').click();
    check('fantasy_state_preserved',JSON.stringify(fantasy)===JSON.stringify(await page.evaluate(()=>fantasyLab.recipe())));
    await page.locator('[data-family="cosmic"]').click();
    check('cosmic_state_preserved',JSON.stringify(edited)===JSON.stringify(await cosmic.evaluate(()=>cosmicLab.recipe())));
    const downloadPromise=page.waitForEvent('download');await frame.locator('#export').click();
    const download=await downloadPromise;const saved=path.join(config.out,download.suggestedFilename());await download.saveAs(saved);
    const exported=JSON.parse(fs.readFileSync(saved,'utf8'));
    check('original_recipe_export',exported.schema==='cosmic-authoring-preview/v1'&&exported.parameters.density===1.21&&exported.layers.starsOn===0);
    check('star_motion_controls_export',exported.parameters.starSpeed===0.63&&exported.parameters.twinkle===0.88&&exported.parameters.twinkleRate===1.35);
    await frame.locator('#starsOn').check();await frame.locator('[data-preset="violet-nebula"]').click();
    await page.screenshot({path:path.join(config.out,'workshop-desktop.png'),fullPage:true});
    for(const [name,width,height] of [['desktop',1440,1050],['mobile',390,844]]){
      await page.setViewportSize({width,height});
      await page.waitForTimeout(250);
      const metrics=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:innerWidth}));
      const inner=await cosmic.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:innerWidth,scene:document.getElementById('scene').getBoundingClientRect().width}));
      check(name+':no_horizontal_overflow',metrics.scroll<=metrics.width&&inner.scroll<=inner.width&&inner.scene>100,{metrics,inner});
      await page.screenshot({path:path.join(config.out,`workshop-${name}.png`),fullPage:true});
    }
    check('no_page_errors',errors.length===0,errors);
    const report={status:checks.every(c=>c.passed)?'passed':'failed',checks};
    fs.writeFileSync(path.join(config.out,'validation.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify({status:report.status,checks:checks.length,failed:checks.filter(c=>!c.passed)}));
    if(report.status!=='passed')process.exitCode=1;
  }finally{if(browser)await browser.close();if(service)await stopChild(service.child);}
})().catch(error=>{console.error(error);process.exitCode=1;});
