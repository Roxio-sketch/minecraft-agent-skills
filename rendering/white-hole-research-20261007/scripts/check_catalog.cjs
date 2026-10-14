/* Actual offline browser check for the research UI, not a VFX renderer test. */
const fs=require('fs'),path=require('path'),assert=require('assert'),{pathToFileURL}=require('url');
const {chromium}=require('C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),out=path.join(root,'qa');fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1040},deviceScaleFactor:1});
 const errors=[],requests=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>requests.push(r.url()));
 const checks=[];function check(name,value){assert(value,name);checks.push(name);}
 try{
  await page.goto(pathToFileURL(path.join(root,'index.html')).href);
  check('23 verified resource records embedded',await page.locator('.card').count()===23);
  check('initial route is ring',await page.evaluate(()=>researchCatalog.chosenPlan)==='ring');
  await page.getByRole('tab',{name:'双极喷发'}).click();
  check('jet plan detail and selected tab update',(await page.locator('#routeDescription').innerText()).includes('spine')&&await page.locator('#tab-jet').getAttribute('aria-selected')==='true');
  await page.locator('#tab-jet').press('ArrowRight');
  check('route keyboard navigation',await page.evaluate(()=>researchCatalog.chosenPlan)==='burst');
  await page.locator('#tab-burst').press('Home');
  check('route Home returns to recommended ring',await page.evaluate(()=>researchCatalog.chosenPlan)==='ring');
  await page.locator('#category').selectOption({label:'现有工程'});
  check('local resources filter has five actual files',await page.locator('.card').count()===5);
  await page.locator('#reset').click();
  await page.locator('#category').selectOption({label:'物理依据'});
  check('physics filter includes five records',await page.locator('.card').count()===5);
  await page.locator('#reset').click();
  await page.locator('#search').fill('PSRDNoise');
  check('text search returns PSRDNoise',await page.locator('[data-source-id="tech-psrdnoise3"]').count()===1);
  await page.locator('#priority').selectOption('P0');
  check('all current cards satisfy priority',await page.evaluate(()=>researchCatalog.filtered.every(s=>s.priority==='P0')));
  const expectedIds=await page.evaluate(()=>researchCatalog.filtered.map(s=>s.id));
  const downloadEvent=page.waitForEvent('download');await page.locator('#export').click();const download=await downloadEvent;
  const exportPath=path.join(out,'exported-research-plan.json');await download.saveAs(exportPath);
  const exported=JSON.parse(fs.readFileSync(exportPath,'utf8'));
  check('export is research-plan/1 with exact filtered records',exported.schema==='research-plan/1'&&JSON.stringify(exported.sources.map(s=>s.id))===JSON.stringify(expectedIds));
  await page.locator('#reset').click();await page.locator('#search').fill('不存在的检索关键词白洞zzz');
  check('empty result is readable',await page.locator('.empty').count()===1);
  await page.locator('#reset').click();
  await page.locator('[data-source-id="tech-psrdnoise3"] summary').click();
  check('read scope and license detail expands',await page.locator('[data-source-id="tech-psrdnoise3"] details').getAttribute('open')!==null);
  check('all online links are safe HTTP(S)',await page.evaluate(()=>Array.from(document.querySelectorAll('a[target="_blank"]')).every(a=>/^https?:/.test(a.href)&&a.rel.includes('noopener'))));
  check('desktop no horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:path.join(out,'desktop.png'),fullPage:false});
  await page.setViewportSize({width:390,height:844});
  check('mobile no horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:path.join(out,'mobile.png'),fullPage:false});
  await page.locator('#resources').scrollIntoViewIfNeeded();
  await page.screenshot({path:path.join(out,'mobile-resources.png'),fullPage:false});
  check('no external network dependency',requests.every(url=>url.startsWith('file:')||url.startsWith('blob:')));
  check('no browser runtime errors',errors.length===0);
  const report={status:'passed',scope:'offline research UI only; no white-hole renderer/game',browser:'Edge headless',checkedOn:'2026-10-07',sourceCount:23,checks,errors,networkDependencies:0,screenshots:['desktop.png','mobile.png','mobile-resources.png'],exportFile:'exported-research-plan.json'};
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify({status:report.status,checks:checks.length,sourceCount:23}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
