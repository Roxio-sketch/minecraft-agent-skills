const {chromium}=require(process.argv[2]);
const fs=require('fs'),path=require('path'),assert=require('assert');
const root=__dirname,data=JSON.parse(fs.readFileSync(path.join(root,'sources.json'),'utf8'));
const checks=[],errors=[];const ok=name=>checks.push(name);
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
 const context=await browser.newContext({viewport:{width:1440,height:1080},acceptDownloads:true});
 const page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));
 await page.goto('file:///'+path.join(root,'index.html').replace(/\\/g,'/'));
 assert.strictEqual(await page.locator('#total').textContent(),String(data.sources.length));
 assert.strictEqual(await page.locator('[data-route]').count(),4);ok('离线打开、真实来源计数及四重点');
 for(const r of data.routes){await page.locator(`[data-route="${r.id}"]`).click();assert((await page.locator('#route-detail').textContent()).includes(r.goal));assert.strictEqual(await page.locator('#route-detail .recommend a').count(),r.sources.length);}
 ok('四重点的阶段、参数与推荐源切换');
 await page.locator('[data-route="fire"]').click();await page.screenshot({path:path.join(root,'evidence','重点路线.png')});
 await page.locator('[data-view="library"]').click();assert.strictEqual(await page.locator('.card').count(),data.sources.length);ok('全部86条卡片显示');
 for(const group of ['fire','sword','alchemy','extension','community']){await page.locator('#group').selectOption(group);assert.strictEqual(await page.locator('.card').count(),data.sources.filter(s=>s.group===group).length);}
 ok('五个方向筛选结果与 JSON 一致');
 await page.locator('#group').selectOption('all');await page.locator('#kind').selectOption('源码');assert.strictEqual(await page.locator('.card').count(),data.sources.filter(s=>s.kind==='源码').length);ok('源码类型筛选');
 await page.locator('#reset-filters').click();await page.locator('#search').fill('万剑归宗');assert(await page.locator('.card').count()>=4);ok('中文关键词检索');
 await page.locator('#search').fill('never-present-QA-98109');assert.strictEqual(await page.locator('.card').count(),0);assert(await page.locator('.empty').isVisible());ok('空检索提示');
 await page.locator('#reset-filters').click();
 const id=data.sources.find(s=>s.title==='StickWeaponTrailEffect').id;
 await page.locator(`[data-select="${id}"]`).click();await page.locator('#selected-only').check();assert.strictEqual(await page.locator('.card').count(),1);
 await page.locator('.card summary').click();await page.locator(`[data-note="${id}"]`).fill('QA: <img src=x onerror=alert(1)> 学双挂点和插值');
 await page.reload();await page.locator('[data-view="library"]').click();await page.locator('#selected-only').check();assert.strictEqual(await page.locator('.card').count(),1);assert((await page.locator(`[data-note="${id}"]`).inputValue()).includes('<img'));assert.strictEqual(await page.locator('.card img').count(),0);ok('选定来源与笔记持久化、笔记按纯文本渲染');
 await page.locator('[data-view="plan"]').click();await page.locator('#plan-note').fill('QA: 蓝白异火缠绕，薄烟散出。');
 const [dl]=await Promise.all([page.waitForEvent('download'),page.locator('#export').click()]);await dl.saveAs(path.join(root,'evidence','导出计划测试.json'));
 const plan=JSON.parse(fs.readFileSync(path.join(root,'evidence','导出计划测试.json'),'utf8'));assert.strictEqual(plan.kind,'learning-plan');assert.strictEqual(plan.sources.length,1);assert.strictEqual(plan.sources[0].id,id);assert(plan.sources[0].note.includes('双挂点'));assert(plan.goal.includes('异火'));ok('计划导出含选定来源、笔记与整体目标');
 await page.locator('[data-remove]').click();assert.strictEqual(await page.locator('#selected-count').textContent(),'0');ok('计划移出来源');
 await page.locator('[data-view="directions"]').click();assert.strictEqual(await page.locator('#direction-cards .tag').count(),48);ok('48个方向与分组数量一致');
 await page.locator('[data-view="library"]').click();await page.locator('#reset-filters').click();await page.screenshot({path:path.join(root,'evidence','社区资料库.png')});
 const links=await page.locator('.source-link').evaluateAll(xs=>xs.map(x=>({href:x.href,target:x.target,rel:x.rel})));
 assert.strictEqual(new Set(links.map(x=>x.href.replace(/\/$/,''))).size,data.sources.length);assert(links.every(x=>x.href.startsWith('https://')&&x.target==='_blank'&&x.rel.includes('noopener')));ok('86条HTTPS原始资料入口、无重复链接');
 await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth)<=390);await page.screenshot({path:path.join(root,'evidence','窄屏资料库.png')});ok('390px窄屏无横向溢出');
 assert.strictEqual(errors.length,0);ok('无页面脚本异常');
 await browser.close();const result={date:'2026-10-13',browser:'Microsoft Edge headless',checks,errors,sourceCount:data.sources.length,directions:48,externalPagesRechecked:false};fs.writeFileSync(path.join(root,'evidence','验证结果.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
})().catch(e=>{console.error(e);process.exit(1);});
