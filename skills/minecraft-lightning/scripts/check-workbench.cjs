#!/usr/bin/env node
/* Optional real-browser check. Supply Playwright through your development environment. */
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require('playwright');
const args=process.argv.slice(2),arg=k=>{const i=args.indexOf(k);return i<0?null:args[i+1];};
const out=arg('--out');if(!out)throw Error('usage: node check-workbench.cjs --out <evidence> [--url http://localhost:8766] [--recipe <legacy JSON>]');
fs.mkdirSync(out,{recursive:true});
(async()=>{
 let server=null,url=arg('--url')||'http://localhost:8766';
 if(args.includes('--serve')){
  const root=path.resolve(__dirname,'../assets/workbench');
  server=require('http').createServer((req,res)=>{
   const file=path.resolve(root,'.'+decodeURIComponent(req.url.split('?')[0]==='/'?'/index.html':req.url.split('?')[0]));
   if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
   fs.readFile(file,(e,d)=>{res.writeHead(e?404:200,{'content-type':file.endsWith('.js')?'text/javascript':'text/html'});res.end(e?'not found':d);});
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));url='http://127.0.0.1:'+server.address().port;
 }
 const browser=await chromium.launch({headless:true,...(arg('--browser-channel')?{channel:arg('--browser-channel')}:{})});
 const page=await browser.newPage({viewport:{width:1440,height:1100},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(url);
 await page.waitForFunction(()=>window.LightningWorkbench);
 await page.locator('#pause').click();
 const initial=await page.evaluate(()=>LightningWorkbench.getRecipe());assert.equal(Object.keys(initial).length,63);
 if(arg('--recipe')){
  await page.locator('#file').setInputFiles(path.resolve(arg('--recipe')));
  await page.waitForFunction(()=>document.getElementById('status').textContent.startsWith('已导入'));
  const imported=await page.evaluate(()=>LightningWorkbench.getRecipe());
  const expected=require('../assets/workbench/recipe.js').validateRecipe(JSON.parse(fs.readFileSync(arg('--recipe'),'utf8')));
  assert.deepEqual(imported,expected);
 }
 await page.evaluate(()=>LightningWorkbench.setRecipe({...defaultRecipe,gradientEnabled:true}));
 await page.locator('#gradientRatio').fill('75');await page.locator('#gradientRatio').dispatchEvent('input');
 assert.equal(await page.evaluate(()=>LightningWorkbench.getRecipe().gradientRatio),75);
 assert.equal(await page.locator('#gradientRatioOut').textContent(),'75%');
 const downloadPromise=page.waitForEvent('download');await page.locator('#export').click();
 const download=await downloadPromise;await download.saveAs(path.join(out,'exported-63.json'));
 const exported=JSON.parse(fs.readFileSync(path.join(out,'exported-63.json'),'utf8'));assert.equal(Object.keys(exported).length,63);assert.equal(exported.gradientRatio,75);
 const beforeInvalid=await page.evaluate(()=>LightningWorkbench.getRecipe());
 await page.locator('#file').setInputFiles({name:'invalid.json',mimeType:'application/json',buffer:Buffer.from('{"gradientRatio":101,"coreColor":"#123456"}')});
 await page.waitForFunction(()=>document.getElementById('status').textContent.startsWith('导入失败'));
 assert.deepEqual(await page.evaluate(()=>LightningWorkbench.getRecipe()),beforeInvalid);
 await page.evaluate(()=>{LightningWorkbench.setRecipe({...presets.strike,gradientEnabled:true,gradientTarget:'both',gradientCoreMode:'vivid',coreColor:'#ffffff',glowColor:'#895a75',gradientStart:'#e700f2',gradientEnd:'#e3b026',gradientRatio:31});LightningWorkbench.setTime(3.2);});
 const colors=await page.evaluate(()=>({core:[...document.querySelectorAll('#corePreview code')].map(n=>n.textContent),glow:[...document.querySelectorAll('#glowPreview code')].map(n=>n.textContent)}));
 assert.deepEqual(colors,{core:['#E700F2','#E47965','#E3B026'],glow:['#7C006F','#7A2A2E','#793E11']});
 await page.screenshot({path:path.join(out,'highgain-user-vivid-ui.png'),fullPage:true});
 const unchanged=await page.evaluate(()=>({segments:LightningWorkbench.segments(),time:document.getElementById('statTime').textContent}));
 await page.locator('#gradientTarget').selectOption('glow');
 assert.deepEqual(await page.evaluate(()=>LightningWorkbench.segments()),unchanged.segments,'appearance change rebuilt visible geometry');
 assert.equal(await page.locator('#statTime').textContent(),unchanged.time,'layer selection restarted event time');
 assert.deepEqual(await page.locator('#corePreview code').allTextContents(),['#FFFFFF','#FFFFFF','#FFFFFF']);
 assert.deepEqual(await page.locator('#glowPreview code').allTextContents(),colors.glow);
 assert(await page.locator('#gradientCoreModeRow').isHidden());
 await page.locator('#gradientTarget').selectOption('both');
 await page.locator('#gradientCoreMode').selectOption('additive');
 await page.screenshot({path:path.join(out,'highgain-user-additive-ui.png'),fullPage:true});
 const vividOracle=await page.evaluate(()=>{
  const r={...defaultRecipe,mode:'strike',strikeWidth:.055,glowWidth:.23,coreGain:3.8,glowGain:2.4,gradientEnabled:true,gradientTarget:'both',coreColor:'#ffffff',glowColor:'#895a75',gradientStart:'#e700f2',gradientEnd:'#e3b026',gradientRatio:31};
  const segments=[];
  for(const [path,y,width,alpha] of [[0,0,.055,1],[1,1,.03,.35]])for(let i=0;i<4;i++)segments.push({a:[i/4,y,0],b:[(i+1)/4,y,0],w:width,alpha,path,along:i/4,alongB:(i+1)/4});
  const project=p=>[32+p[0]*448,64+p[1]*128,0,100];
  const draw=mode=>{
   const renderer=new LightningGpuPreview(),scene=document.createElement('canvas');scene.width=512;scene.height=256;const ctx=scene.getContext('2d');ctx.fillStyle='#24364c';ctx.fillRect(0,0,512,256);
   renderer.begin(512,256);renderer.seedScene(ctx);renderer.draw(segments,{...r,gradientCoreMode:mode},project);renderer.finish(ctx);
   const rgb=(x,y)=>[...ctx.getImageData(x,y,1,1).data].slice(0,3);
   return {main:[64,144,256,368,448].map(x=>rgb(x,64)),branch:[64,144,256,368,448].map(x=>rgb(x,192)),stats:renderer.stats,png:scene.toDataURL('image/png').split(',')[1]};
  };
  return {fixture:'highgain-main-branch-gradient/v1',recipe:r,vivid:draw('vivid'),additive:draw('additive')};
 });
 assert.deepEqual(vividOracle.additive.main[2],[255,255,255],'traditional main core no longer reproduces reported clipping');
 for(let i=0;i<3;i++)assert(Math.abs(vividOracle.vivid.main[2][i]-[255,135,113][i])<=2,'vivid high-gain core does not preserve expected purple/gold material hue');
 assert(vividOracle.vivid.main[0][2]>vividOracle.vivid.main[4][2]+100,'main trunk lost along-path purple-to-gold change');
 assert(vividOracle.vivid.branch[0][2]>vividOracle.vivid.branch[4][2]+40,'branches lost along-path gradient');
 for(const mode of ['vivid','additive']){assert.equal(vividOracle[mode].stats.glError,0);fs.writeFileSync(path.join(out,'highgain-main-branch-'+mode+'.png'),Buffer.from(vividOracle[mode].png,'base64'));delete vividOracle[mode].png;}
 const cases=[];
 for(const [name,ratio,enabled,hue] of [['impact-tip-cyan',50,true,'cyan'],['impact-ratio100-red',100,true,'red'],['impact-gradient-off-white',50,false,'white']]){
  await page.evaluate(({ratio,enabled})=>{
   LightningWorkbench.setRecipe({...presets.cluster,strikeHeight:18,rainCount:1,rainSpread:1,rainDepthSpread:0,impactRadius:2.4,coreColor:'#ffffff',glowColor:'#ffffff',strikeWidth:.09,glowWidth:.2,gradientEnabled:enabled,gradientRatio:ratio,gradientStart:'#ff0000',gradientEnd:'#00ffff',impactEnabled:false});
   LightningWorkbench.setView(.35,.6);LightningWorkbench.setTime(3.2);
   const c=document.getElementById('canvas');window._lightningBefore=c.getContext('2d').getImageData(0,0,c.width,c.height).data;
  },{ratio,enabled});
  await page.locator('#canvas').screenshot({path:path.join(out,name+'-without-ground.png')});
  await page.evaluate(()=>{LightningWorkbench.setRecipe({...LightningWorkbench.getRecipe(),impactEnabled:true});LightningWorkbench.setTime(3.2);});
  await page.locator('#canvas').screenshot({path:path.join(out,name+'.png')});
  const result=await page.evaluate(hue=>{
   const c=document.getElementById('canvas'),a=c.getContext('2d').getImageData(0,0,c.width,c.height).data,b=window._lightningBefore;
   let changed=0,wrongHue=0;
   for(let i=0;i<a.length;i+=4){
    const d=[a[i]-b[i],a[i+1]-b[i+1],a[i+2]-b[i+2]];
    if(Math.max(...d)<5)continue;changed++;
    if(hue==='cyan'&&d[0]>2||hue==='red'&&(d[1]>2||d[2]>2))wrongHue++;
   }
   return {changedGroundPixels:changed,wrongHuePixels:wrongHue,stats:LightningWorkbench.stats(),allGroundAlongTip:LightningWorkbench.segments().filter(s=>s.path>=0&&s.along===s.alongB).every(s=>s.along===1)};
  },hue);
  assert(result.changedGroundPixels>100,name+' has no actual ground raster');
  assert.equal(result.wrongHuePixels,0,name+' restarted gradient instead of inheriting parent tip');
  assert.equal(result.stats.glError,0);cases.push({name,ratio,enabled,...result});
 }
 for(const [name,preset] of Object.entries({orbit:'orbit',ring:'ring',source:'source',cluster:'cluster'})){
  await page.evaluate(p=>{LightningWorkbench.setRecipe(presets[p]);LightningWorkbench.setTime(6.25);},preset);
  const stats=await page.evaluate(()=>LightningWorkbench.stats());assert(stats.paths>0&&stats.vertices>0&&stats.glError===0,name+' preview missing');cases.push({name,...stats});
 }
 const haloOracle=await page.evaluate(()=>{
  const renderer=new LightningGpuPreview(),r={...defaultRecipe,mode:'strike',strikeWidth:.05,glowWidth:1.154,coreColor:'#000000',glowColor:'#ffffff',glowGain:2.4};
  const seg=(a,b,along,alongB)=>({a,b,w:.05,alpha:.8,path:0,along,alongB});
  const segments=[seg([-.199,0,0],[0,0,0],0,.5),seg([0,0,0],[0,.199,0],.5,1)];
  const project=p=>[256+p[0]*100,256-p[1]*100,0,100];
  const draw=list=>{renderer.begin(512,512);renderer.draw(list,r,project);const c=document.createElement('canvas');c.width=c.height=512;const ctx=c.getContext('2d');renderer.finish(ctx);return {c,pixels:ctx.getImageData(0,0,512,512).data};};
  const image=draw(segments),radius=115.4,length=19.9,profile=d=>d>=radius?0:.8*(Math.exp(-5*(d/radius)**2)-Math.exp(-5));
  let errorPixels=0,maxError=0,visible=0;
  for(let y=0;y<512;y++)for(let x=0;x<512;x++){
   const px=x+.5,py=y+.5;
   const a=px>=256-length&&px<=256?profile(Math.abs(py-256)):0;
   const b=py>=256-length&&py<=256?profile(Math.abs(px-256)):0;
   const disk=profile(Math.hypot(px-256,py-256)),expected=Math.max(a,b,disk)*255,actual=image.pixels[(y*512+x)*4];
   const error=Math.abs(actual-expected);maxError=Math.max(maxError,error);if(error>2)errorPixels++;if(actual>2)visible++;
  }
  const subdivided=draw([seg([-.199,0,0],[-.0995,0,0],0,.25),seg([-.0995,0,0],[0,0,0],.25,.5),seg([0,0,0],[0,.0995,0],.5,.75),seg([0,.0995,0],[0,.199,0],.75,1)]);
  let subdivisionPixels=0;for(let i=0;i<image.pixels.length;i++)if(Math.abs(image.pixels[i]-subdivided.pixels[i])>1)subdivisionPixels++;
  return {fixture:'independent-halo-raster-oracle/v1',errorPixels,maxError,visible,subdivisionPixels,png:image.c.toDataURL('image/png').split(',')[1]};
 });
 assert.equal(haloOracle.errorPixels,0,'WebGL halo is not the independent max of two convex strips and an interior disk');
 assert.equal(haloOracle.subdivisionPixels,0,'WebGL glow changes with collinear subdivision');
 assert(haloOracle.visible>1000,'wide halo is missing');
 fs.writeFileSync(path.join(out,'thick-acute-halo.png'),Buffer.from(haloOracle.png,'base64'));delete haloOracle.png;
 await page.evaluate(()=>LightningWorkbench.setRecipe({...presets.cluster,impactEnabled:false}));
 await page.evaluate(()=>LightningWorkbench.setTime(80));const ended=await page.evaluate(()=>LightningWorkbench.stats());assert.equal(ended.segments,0);
 assert.equal(errors.length,0,errors.join('\n'));
 const report={fixture:'lightning-workbench-webgl/v2',legacyRecipeImport:!!arg('--recipe'),typedExportFields:63,invalidImportAtomic:true,actualRatioControl:75,actualLayerControls:true,actualSixMaterialSwatches:colors,vividOracle,cases,haloOracle,ended,errors};
 fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));await browser.close();if(server)server.close();
 console.log('PASS: actual WebGL2 shaders/MAX-ADD/alpha core, 63-field export and legacy import, live ratio/layer controls, six material swatches, high-gain main+branch vivid/additive pixel oracle, atomic invalid input, ground-only cyan/red/white raster differences and all existing channels');
})().catch(e=>{console.error(e);process.exit(1);});
