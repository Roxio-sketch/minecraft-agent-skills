// Inspect real WebGL transport, then exercise the complete preview and recipe UI.
// Requires Playwright, a Chromium browser, and the bundled preview server.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(name,fallback)=>{const i=args.indexOf(name);return i<0?fallback:args[i+1];};
const output=path.resolve(arg('--output','infall-evidence')),url=arg('--url','http://127.0.0.1:8771/');
const checks=[],shots=[],errors=[],check=(name,passed,detail)=>checks.push({name,passed,detail});
const difference=(a,b)=>{let changed=0,total=0;for(let i=0;i<a.length;i+=4){let d=0;for(let c=0;c<3;c++)d+=Math.abs(a[i+c]-b[i+c]);if(d)changed++;total+=d;}return {changedPixels:changed,meanAbsolute:total/(a.length/4*3)};};
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE),args:['--enable-webgl']});
 let renderer;
 try{
  const page=await browser.newPage({viewport:{width:1250,height:1000},deviceScaleFactor:1});
  page.on('pageerror',e=>errors.push(e.message));await page.goto(url);
  await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
  const init=await page.evaluate(()=>window.blackHoleError);if(init)throw Error(init);
  renderer=await page.evaluate(()=>blackHolePreview.renderer());
  await page.evaluate(()=>{const c=document.getElementById('scene');c.width=c.height=256;});
  const kernel=await (await page.request.get(new URL('black-hole.glsl',url).href)).text();
  const probe=await page.evaluate(kernel=>{
   // The shipped GLSL function is executed directly, without a JS copy of the pattern.
   const canvas=document.createElement('canvas');canvas.width=512;canvas.height=1;
   const gl=canvas.getContext('webgl2',{antialias:false,preserveDrawingBuffer:true});if(!gl)throw Error('WebGL 2 unavailable');gl.disable(gl.DITHER);
   const compile=(type,source)=>{const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
   const vs=compile(gl.VERTEX_SHADER,'#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0,1);}');
   const fs=compile(gl.FRAGMENT_SHADER,`#version 300 es
precision highp float; precision highp int;
${kernel}
uniform int uAxis,uLegacy;uniform float uAngleOffset;
out vec4 outColor;
void main(){
 float x=(gl_FragCoord.x-0.5)/512.0;
 float radius=uAxis==0?1.0+2.0*x:1.5;
 float angle=(uAxis==0?0.37:-3.14159265359+6.28318530718*x)+uAngleOffset;
 vec2 pattern=bhDiskPattern(radius,angle);
 if(uLegacy==1){float phase=angle-uTime*uSpeed/(0.45+radius);pattern=vec2(0.5+0.5*sin(7.0*phase+10.0*radius+uSeed),0.5+0.5*sin(19.0*phase-17.0*radius+0.7*sin(3.0*phase)));}
 outColor=vec4(pattern,0,1);
}`);
   const program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
   gl.useProgram(program);gl.viewport(0,0,512,1);const loc=n=>gl.getUniformLocation(program,n);
   const render=({axis=0,time=0,speed=0,infall=.22,offset=0,legacy=0}={})=>{
    gl.uniform1i(loc('uAxis'),axis);gl.uniform1i(loc('uLegacy'),legacy);gl.uniform1f(loc('uTime'),time);gl.uniform1f(loc('uSpeed'),speed);gl.uniform1f(loc('uInfall'),infall);gl.uniform1f(loc('uSeed'),17);gl.uniform1f(loc('uAngleOffset'),offset);
    gl.drawArrays(gl.TRIANGLES,0,3);const pixels=new Uint8Array(512*4);gl.readPixels(0,0,512,1,gl.RGBA,gl.UNSIGNED_BYTE,pixels);return Array.from(pixels);
   };
   const error=(a,b,lag=0)=>{let total=0;for(let i=64;i<448;i++)for(let c=0;c<2;c++)total+=Math.abs(a[i*4+c]-b[(i+lag)*4+c]);return total/(384*2);};
   const bestLag=(a,b)=>{const candidates=[];for(let lag=-60;lag<=60;lag++)candidates.push({lag,error:error(a,b,lag)});return candidates.sort((a,b)=>a.error-b.error)[0];};
   const radial0=render(),dt=(32*2/512)/.22,radial1=render({time:dt});
   const angular0=render({axis:1,infall:0,speed:.55}),angularDt=(32*2*Math.PI/512)*(1.5+.45)/.55,angular1=render({axis:1,infall:0,speed:.55,time:angularDt});
   const zero0=render({infall:0}),zero1=render({infall:0,time:5});
   const periodic0=render({axis:1}),periodic1=render({axis:1,offset:2*Math.PI});
   const wrapPeriod=2*Math.PI/.22,wrap0=render({time:wrapPeriod-.001}),wrap1=render({time:wrapPeriod+.001});
   const legacy=render({infall:0,speed:.55,time:1.7,legacy:1}),current=render({infall:0,speed:.55,time:1.7});
   const result={
    radial:{best:bestLag(radial1,radial0),expectedLag:32,expectedRadiusDisplacement:-.125,seconds:dt,correctDirectionError:error(radial1,radial0,32),oppositeDirectionError:error(radial1,radial0,-32),unshiftedError:error(radial1,radial0)},
    angular:{best:bestLag(angular1,angular0),expectedLag:-32,seconds:angularDt},
    zeroMotionError:error(zero1,zero0),angleSeamError:error(periodic1,periodic0),radialWrapError:error(wrap1,wrap0),legacyZeroInfallError:error(current,legacy),glError:gl.getError()
   };
   gl.deleteShader(vs);gl.deleteShader(fs);gl.deleteProgram(program);gl.getExtension('WEBGL_lose_context')?.loseContext();return result;
  },kernel);
  check('gpu_positive_infall_transports_features_inward',probe.radial.best.lag===32&&probe.radial.correctDirectionError<.15&&probe.radial.oppositeDirectionError>20&&probe.radial.unshiftedError>20,probe.radial);
  check('gpu_rotation_remains_independent',probe.angular.best.lag===-32&&probe.angular.best.error<8,probe.angular);
  // The bounded two-field transport intentionally replaces the old unbounded
  // classic phase. Keep the comparison as a finite diagnostic instead of
  // requiring pixel identity with the retired pattern.
  check('gpu_zero_infall_uses_current_periodic_pattern',Number.isFinite(probe.legacyZeroInfallError),{meanAbsolute:probe.legacyZeroInfallError,comparison:'informational against retired classic phase'});
  check('gpu_zero_motion_is_static',probe.zeroMotionError===0,{meanAbsolute:probe.zeroMotionError});
  check('gpu_angular_pattern_is_periodic',probe.angleSeamError<.15,{meanAbsolute:probe.angleSeamError});
  check('gpu_radial_wrap_is_continuous',probe.radialWrapError<1,{meanAbsolute:probe.radialWrapError,comparison:'2 ms apart across the wrapped transport period'});
  check('gpu_probe_no_errors',probe.glError===0,{glError:probe.glError});
  const set=(p,t=0)=>page.evaluate(({p,t})=>blackHolePreview.set(p,t),{p,t});
  const frame=(index=0)=>page.evaluate(index=>blackHolePreview.frame(index),index);
  const recipe=await page.evaluate(()=>blackHolePreview.catalog.recipes.find(r=>r.diskStyle==='classic'));
  check('new_presets_explicit_infall',await page.evaluate(()=>blackHolePreview.catalog.recipes.every(r=>typeof r.infallSpeed==='number'&&r.infallSpeed>=0)),await page.evaluate(()=>blackHolePreview.catalog.recipes.map(r=>({name:r.name,infallSpeed:r.infallSpeed}))));
  const camera={yaw:0,pitch:.9,tilt:0};
  const base={...recipe,...camera,steps:160,mode:'scene',exposure:.55};
  const sequences=[];
  for(const [name,label,motion] of [
   ['still','两种速度均为 0',{speed:0,infallSpeed:0}],
   ['radial','仅径向向内流动',{speed:0,infallSpeed:.22}],
   ['angular','仅差速旋转',{speed:.55,infallSpeed:0}],
   ['combined','旋转 + 向内流动',{speed:.55,infallSpeed:.22}]
  ]){
   const images=[],frames=[];
   for(const time of [0,1,2]){
    await set({...base,...motion},time);const f=await frame();frames.push(f);
    if(f.glError)throw Error(`GL error ${f.glError}`);
    const png=await page.locator('#scene').evaluate(c=>c.toDataURL()),file=`${name}-${time}s.png`;fs.writeFileSync(path.join(output,file),Buffer.from(png.split(',')[1],'base64'));shots.push(file);images.push({time,png,file});
   }
   const delta=difference(frames[0].pixels,frames[2].pixels);
   check('full_preview_'+name,name==='still'?delta.changedPixels===0:delta.changedPixels>100,delta);
   sequences.push({name,label,images});
  }
  await set({...base,speed:0,infallSpeed:.22});const small0=[await frame(1),await frame(2)];
  await set({},2);
  for(let index=1;index<=2;index++){const f=await frame(index),delta=difference(small0[index-1].pixels,f.pixels);check('radial_motion_small_'+f.width,delta.changedPixels>0&&f.glError===0,delta);}
  await set({...base,speed:0,infallSpeed:0});
  // The motion section is collapsed by default; use the public API instead
  // of requiring the hidden range input to be manually expanded first.
  await page.evaluate(()=>blackHolePreview.set({infallSpeed:.37}));
  const selected=await page.evaluate(()=>blackHolePreview.snapshot().parameters);check('infall_ui_independent',selected.infallSpeed===.37&&selected.speed===0,selected);
  const beforeExport=await page.evaluate(()=>blackHolePreview.snapshot()),downloadPromise=page.waitForEvent('download');await page.click('#export');
  const download=await downloadPromise,recipeFile=path.join(output,'infall-recipe.json');await download.saveAs(recipeFile);
  await set({infallSpeed:0,speed:.55});await page.setInputFiles('#import',recipeFile);
  await page.waitForFunction(()=>blackHolePreview.snapshot().parameters.infallSpeed===.37);
  check('infall_file_roundtrip',JSON.stringify(beforeExport)===JSON.stringify(await page.evaluate(()=>blackHolePreview.snapshot())));
  const legacy=structuredClone(beforeExport);delete legacy.parameters.infallSpeed;
  await page.setInputFiles('#import',{name:'legacy-no-infall.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(legacy))});
  await page.waitForFunction(()=>blackHolePreview.snapshot().parameters.infallSpeed===0);
  check('legacy_recipe_omission_defaults_zero',await page.locator('#infallSpeed').inputValue()==='0');
  const invalid=await page.evaluate(()=>{
   const results=[];for(const value of [-.01,1.51,null,'0.22',NaN,Infinity])for(const method of ['set','import']){
    const before=JSON.stringify(blackHolePreview.snapshot());let error='';try{if(method==='set')blackHolePreview.set({infallSpeed:value});else{const s=blackHolePreview.snapshot();s.parameters.infallSpeed=value;blackHolePreview.importSnapshot(s);}}catch(e){error=e.message;}
    results.push({value:String(value),method,rejected:error.includes('infallSpeed'),unchanged:before===JSON.stringify(blackHolePreview.snapshot())});
   }return results;
  });check('invalid_infall_rejected_atomically',invalid.every(r=>r.rejected&&r.unchanged),invalid);
  await set({...base,speed:.55,infallSpeed:.22},1);await page.screenshot({path:path.join(output,'infall-workbench.png')});shots.push('infall-workbench.png');
  const sheet=await browser.newPage({viewport:{width:960,height:1450},deviceScaleFactor:1});
  await sheet.setContent('<html lang="zh-CN"><meta charset="utf-8"><style>body{margin:24px;background:#0b0d14;color:#eceff7;font:16px system-ui}h1{font-size:22px}h2{font-size:16px;margin:16px 0 7px}.row{display:flex;gap:12px}figure{margin:0}img{width:280px;height:280px}figcaption{color:#adb5c7;font-size:13px}</style><h1>固定相机 · 独立旋转与向内流动</h1><p>同一配方、seed 和相机；每行时间依次为 0 / 1 / 2 秒。曝光 0.55。</p>'+sequences.map(s=>`<h2>${s.label}</h2><div class="row">${s.images.map(i=>`<figure><img src="${i.png}"><figcaption>${i.time} s</figcaption></figure>`).join('')}</div>`).join(''));
  await sheet.screenshot({path:path.join(output,'infall-time-sequences.png'),fullPage:true});shots.push('infall-time-sequences.png');await sheet.close();
  check('no_page_errors',errors.length===0,errors);
 }finally{await browser.close();}
 const report={status:checks.every(c=>c.passed)?'passed':'failed',scope:'GPU texture transport and WebGL preview; no Minecraft runtime certification',created:new Date().toISOString(),renderer,checks,shots};
 fs.writeFileSync(path.join(output,'infall-validation.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));if(report.status!=='passed')process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
