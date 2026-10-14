// Run with NODE_PATH pointing to a Playwright installation and a local workbench URL.
const {chromium}=require('playwright');
const crypto=require('crypto');
const fs=require('fs');
const path=require('path');
const url=process.argv[2];
if(!url)throw Error('usage: node validate.cjs http://127.0.0.1:<port>/workbench/');
const edge='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const output=process.argv[3]||path.join(__dirname,'validation.png');
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:edge,args:['--use-gl=angle','--use-angle=swiftshader']});
  try{
    const page=await browser.newPage({viewport:{width:1600,height:1000}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(url,{waitUntil:'networkidle'});
    await page.waitForFunction(()=>document.getElementById('preview').width===1280 && document.getElementById('stops').children.length===5);
    const digest=()=>page.evaluate(()=>document.getElementById('preview').toDataURL());
    const before=crypto.createHash('sha256').update(await digest()).digest('hex');
    await page.locator('#angleDeg').fill('45');await page.locator('#angleDeg').dispatchEvent('input');
    await page.locator('#scale').fill('1.4');await page.locator('#scale').dispatchEvent('input');
    const after=crypto.createHash('sha256').update(await digest()).digest('hex');
    if(before===after)throw Error('gradient controls did not change the WebGL preview');
    const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#export').click()]);
    const exported=JSON.parse(fs.readFileSync(await download.path(),'utf8'));
    if(exported.gradient.angleDeg!==45||exported.gradient.scale!==1.4||exported.schema!=='olk-sketch/v1')throw Error('export did not preserve controls');
    await page.locator('#reset').click();
    if(await page.locator('#angleDeg').inputValue()!=='0')throw Error('reset failed');
    if(errors.length)throw Error(errors.join('\n'));
    await page.screenshot({path:output,fullPage:true});
    console.log(JSON.stringify({url,initialHash:before,adjustedHash:after,exportedAngle:45,exportedScale:1.4,consoleErrors:errors,screenshot:output},null,2));
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
