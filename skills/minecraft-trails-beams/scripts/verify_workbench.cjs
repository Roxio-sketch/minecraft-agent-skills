/* Optional browser test for the local HTML workbench. Requires Playwright and a Chromium browser. */
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict');
const core=require('../assets/workbench/core.js');
const presets=require('../assets/workbench/preset-data.js');
const root=path.resolve(__dirname,'../assets/workbench');
const output=process.argv[2]?path.resolve(process.argv[2]):null;
async function main(){
  let checks=0;
  for(const item of Object.values(presets)){
    core.validateProject({schema:1,recipe:item.recipe,path:item.path});checks++;
    const a=core.paths(item.path,1.25),b=core.paths(item.path,1.25);
    assert.deepEqual(a,b);assert.equal(a.length,1+item.path.branches);checks+=2;
  }
  let rejected=false;try{core.validatePath({...presets.storm.path,branches:5});}catch{rejected=true;}
  assert.ok(rejected);checks++;
  const {chromium}=require('playwright');
  const options={headless:true};if(process.env.TRAIL_BEAM_BROWSER)options.executablePath=process.env.TRAIL_BEAM_BROWSER;
  const browser=await chromium.launch(options);
  const temp=fs.mkdtempSync(path.join(os.tmpdir(),'trail-beam-workbench-'));
  try{
    const page=await browser.newPage({viewport:{width:1480,height:920},acceptDownloads:true,deviceScaleFactor:1});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto('file:///'+path.join(root,'index.html').replaceAll('\\','/'));
    await page.waitForTimeout(200);
    assert.equal(await page.locator('#status').innerText(),'配方有效');checks++;
    if(output){fs.mkdirSync(output,{recursive:true});await page.screenshot({path:path.join(output,'workbench-helix.png'),fullPage:true});}
    await page.locator('#preset').selectOption('storm');
    assert.match(await page.locator('#effectTitle').innerText(),/Forked storm beam/);checks++;
    const pixels=await page.locator('#preview').evaluate(canvas=>{
      const {data}=canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height);
      let bright=0;for(let i=0;i<data.length;i+=4)if(data[i]>100&&data[i+1]>100&&data[i+2]>100)bright++;return bright;
    });
    assert.ok(pixels>100,`preview lacks visible pixels: ${pixels}`);checks++;
    await page.locator('[data-tab="form"]').click();
    await page.locator('[data-key="path.branches"]').fill('4');
    assert.equal(await page.locator('#status').innerText(),'配方有效');checks++;
    await page.locator('[data-tab="material"]').click();
    await page.locator('[data-action="addLayer"]').click();
    assert.equal(await page.locator('#layers').innerText(),'3');checks++;
    await page.locator('[data-action="addStop"][data-kind="colors"]').click();
    assert.equal(await page.locator('.stop.colors').count(),4);checks++;
    await page.locator('[data-tab="files"]').click();
    const recipeDownload=page.waitForEvent('download');await page.locator('[data-action="exportRecipe"]').click();
    const recipe=JSON.parse(fs.readFileSync(await (await recipeDownload).path(),'utf8'));core.validateRecipe(recipe);
    assert.equal(recipe.layers.length,3);checks++;
    const pathDownload=page.waitForEvent('download');await page.locator('[data-action="exportPath"]').click();
    const designPath=JSON.parse(fs.readFileSync(await (await pathDownload).path(),'utf8'));core.validatePath(designPath);
    assert.equal(designPath.branches,4);checks++;
    const bundle={schema:1,recipe,path:designPath};const filename=path.join(temp,'bundle.json');fs.writeFileSync(filename,JSON.stringify(bundle));
    await page.locator('#preset').selectOption('frost');
    await page.locator('#fileInput').setInputFiles(filename);
    assert.match(await page.locator('#effectTitle').innerText(),/Forked storm beam/);checks++;
    assert.equal(await page.locator('#status').innerText(),'配方有效');checks++;
    assert.deepEqual(errors,[]);checks++;
    if(output)await page.screenshot({path:path.join(output,'workbench-storm.png'),fullPage:true});
    console.log(JSON.stringify({status:'passed',checks,previewBrightPixels:pixels,screenshots:output}));
  }finally{await browser.close();fs.rmSync(temp,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
