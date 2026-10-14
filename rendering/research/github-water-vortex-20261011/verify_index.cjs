// Checks only this authored source reader; upstream projects stay text data.
const fs=require('fs'),path=require('path');
const {pathToFileURL}=require('url');
const {chromium}=require('playwright');
(async()=>{
  const root=__dirname,checks=[],errors=[];
  const browser=await chromium.launch({channel:'msedge',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1280,height:900}});
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(pathToFileURL(path.join(root,'index.html')).href);
    checks.push({name:'8 project cards',ok:await page.locator('.card').count()===8});
    await page.locator('#category').selectOption({label:'液体与表面'});
    checks.push({name:'liquid filter gives 3',ok:await page.locator('.card').count()===3});
    await page.locator('#search').fill('Particles4All');
    checks.push({name:'search gives 1',ok:await page.locator('.card').count()===1});
    await page.getByRole('button',{name:'读源码',exact:true}).click();
    checks.push({name:'reader opens and displays WGSL source as text',ok:(await page.locator('#readtitle').innerText()).includes('Particles4All')&&(await page.locator('#code').innerText()).length>1000});
    const anchor=page.locator('#anchors button').first();
    await anchor.click();
    checks.push({name:'anchor highlights one source line',ok:await page.locator('#code .target').count()===1});
    await page.getByRole('button',{name:'原始许可证',exact:true}).click();
    checks.push({name:'original MIT license text visible',ok:(await page.locator('#code').innerText()).includes('MIT License')});
    await page.locator('#category').selectOption('');await page.locator('#search').fill('');
    const cards=page.locator('.card');
    const titles=await cards.locator('h2').allTextContents();
    for(let i=0;i<titles.length;i++){
      await cards.nth(i).getByRole('button',{name:'读源码',exact:true}).click();
      const options=await page.locator('#files option').evaluateAll(xs=>xs.map(x=>x.value));
      let ok=true;
      for(const value of options){
        await page.locator('#files').selectOption(value);
        if(!(await page.locator('#code').innerText()).length)ok=false;
      }
      checks.push({name:'offline text files readable: '+titles[i],ok,fileCount:options.length});
    }
    await page.setViewportSize({width:390,height:844});
    checks.push({name:'390px no document overflow',ok:await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)});
    checks.push({name:'no page errors',ok:errors.length===0,errors});
  }finally{await browser.close()}
  const report={scope:'Authored offline research index only; no upstream code executed',checks,passed:checks.filter(x=>x.ok).length,total:checks.length,ok:checks.every(x=>x.ok)};
  fs.writeFileSync(path.join(root,'index-verification.json'),JSON.stringify(report,null,2)+'\n');
  process.stdout.write(JSON.stringify({ok:report.ok,passed:report.passed,total:report.total})+'\n');
  if(!report.ok)process.exitCode=1;
})().catch(e=>{process.stderr.write(e.stack+'\n');process.exitCode=1});
