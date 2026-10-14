const {chromium}=require(process.argv[2]);
const fs=require('fs'),path=require('path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',args:['--enable-webgl']});
 const page=await browser.newPage({viewport:{width:1440,height:980},deviceScaleFactor:1}),errors=[],checks=[];
 const check=(name,value)=>{checks.push({name,pass:!!value});if(!value)throw Error(name);};
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error'&&!/Failed to load resource.*(400|404)/.test(m.text()))errors.push(m.text());});
 await page.goto('file:///'+__dirname.replace(/\\/g,'/')+'/index.html');await page.waitForFunction(()=>window.energyEditor?.getStatus().ready,{timeout:30000});
 check('offline GPU initialization',await page.evaluate(()=>energyEditor.getStatus().ready));
 await page.waitForTimeout(3500);
 const performanceReport=await page.evaluate(()=>{const g=canvas.getContext('webgl2'),x=g.getExtension('WEBGL_debug_renderer_info');return {...energyEditor.getStatus(),renderer:x?g.getParameter(x.UNMASKED_RENDERER_WEBGL):g.getParameter(g.RENDERER)};});
 const deterministic=await page.evaluate(()=>{energyEditor.applyRecipe(energyEditor.presets[0]);energyEditor.setTime(1.75);energyEditor.draw();const a=canvas.toDataURL(),sizeA=[canvas.width,canvas.height];energyEditor.draw();const b=canvas.toDataURL();return {same:a===b,sizeA,sizeB:[canvas.width,canvas.height],a,b};});
 if(!deterministic.same){fs.writeFileSync(path.join(__dirname,'evidence','replay-a.png'),Buffer.from(deterministic.a.split(',')[1],'base64'));fs.writeFileSync(path.join(__dirname,'evidence','replay-b.png'),Buffer.from(deterministic.b.split(',')[1],'base64'));console.log(JSON.stringify({sizeA:deterministic.sizeA,sizeB:deterministic.sizeB}));}
 check('deterministic seed + time replay',deterministic.same);
 await page.screenshot({path:path.join(__dirname,'evidence','editor.png')});
 const hashes=[];
 for(let i=0;i<8;i++){const url=await page.evaluate(i=>{energyEditor.applyRecipe(energyEditor.presets[i]);energyEditor.setTime(i===1||i===3?2.2:1.75);energyEditor.draw();return canvas.toDataURL();},i);const buf=Buffer.from(url.split(',')[1],'base64');fs.writeFileSync(path.join(__dirname,'evidence',`preset-${i+1}.png`),buf);hashes.push(require('crypto').createHash('sha256').update(buf).digest('hex'));}
 check('8 distinct rendered presets',new Set(hashes).size===8);
 check('camera orbit changes actual sampling',await page.evaluate(()=>{energyEditor.applyRecipe(energyEditor.presets[0]);energyEditor.setTime(1.75);energyEditor.draw();const a=canvas.toDataURL();energyEditor.setCamera(1.5,.4,5.6);energyEditor.draw();return a!==canvas.toDataURL();}));
 check('camera inside volume remains valid',await page.evaluate(()=>{energyEditor.setCamera(0,0,.15);energyEditor.draw();return energyEditor.getStatus().glError===0;}));
 check('parameter edit changes rendered pixels',await page.evaluate(()=>{energyEditor.setCamera(.35,.16,5.6);energyEditor.draw();const a=canvas.toDataURL();const r=energyEditor.getRecipe();r.params.erosion=.8;r.params.warp=.1;energyEditor.applyRecipe(r);energyEditor.setTime(1.75);energyEditor.draw();return a!==canvas.toDataURL();}));
 check('invalid import preserves last valid recipe',await page.evaluate(()=>{const before=JSON.stringify(energyEditor.getRecipe()),r=energyEditor.getRecipe();r.params.typo=1;try{energyEditor.applyRecipe(r);return false;}catch(e){return before===JSON.stringify(energyEditor.getRecipe());}}));
 check('out-of-range parameter rejected',await page.evaluate(()=>{const r=energyEditor.getRecipe();r.params.radius=99;try{energyEditor.applyRecipe(r);return false;}catch(e){return true;}}));
 check('zero emission leaves scene independent of exposure',await page.evaluate(()=>{const r=energyEditor.getRecipe();r.params.density=0;r.params.ring=0;r.params.bloom=0;energyEditor.applyRecipe(r);energyEditor.setTime(1);energyEditor.draw();const a=canvas.toDataURL();r.params.exposure=3;energyEditor.applyRecipe(r);energyEditor.setTime(1);energyEditor.draw();return a===canvas.toDataURL();}));
 check('foreground depth cuts energy integration',await page.evaluate(()=>{const r=energyEditor.presets[0];r.params.bloom=0;energyEditor.applyRecipe(r);energyEditor.setCamera(0,0,5.6);energyEditor.setTime(1.75);environment.value=2;energyEditor.draw();const g=canvas.getContext('webgl2'),pixel=new Uint8Array(4);g.readPixels(canvas.width>>1,canvas.height>>1,1,1,g.RGBA,g.UNSIGNED_BYTE,pixel);const a=[...pixel];const off=energyEditor.getRecipe();off.params.density=0;off.params.ring=0;energyEditor.applyRecipe(off);energyEditor.setTime(1.75);energyEditor.draw();g.readPixels(canvas.width>>1,canvas.height>>1,1,1,g.RGBA,g.UNSIGNED_BYTE,pixel);return a.every((v,i)=>Math.abs(v-pixel[i])<=1);}));
 await page.goto('http://127.0.0.1:8795/');await page.waitForFunction(()=>window.energyEditor?.getStatus().ready);
 await page.evaluate(()=>{energyEditor.applyRecipe(energyEditor.presets[0]);energyEditor.setTime(1.75);});
 const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#export').click()]);const downloaded=JSON.parse(fs.readFileSync(await download.path(),'utf8'));
 check('export matches complete recipe',JSON.stringify(downloaded)===JSON.stringify(await page.evaluate(()=>energyEditor.getRecipe())));
 await page.locator('#save').click();await page.waitForFunction(()=>document.getElementById('message').textContent.includes('已保存到工作目录'));
 check('workspace save persists exact recipe',JSON.stringify(JSON.parse(fs.readFileSync(path.join(__dirname,'current-recipe.json'),'utf8')))===JSON.stringify(downloaded));
 const reject=await page.evaluate(async()=>{const r=energyEditor.getRecipe();r.params.radius=-5;const response=await fetch('/api/recipe',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(r)});return response.status;});check('server rejects invalid recipe',reject===400);
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(300);check('mobile has no horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(__dirname,'evidence','mobile.png')});
 check('no browser errors',errors.length===0);
 fs.writeFileSync(path.join(__dirname,'evidence','browser.json'),JSON.stringify({checks,performanceReport,errors},null,2));console.log(JSON.stringify({checks,performanceReport,errors}));await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
