const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(key,fallback)=>{const i=args.indexOf(key);return i<0?fallback:args[i+1];};
const output=path.resolve(arg('--output','workbench-v3-evidence'));
const checks=[];const errors=[];
function check(name,passed,detail){checks.push({name,passed,detail});if(!passed)throw Error(`${name}: ${JSON.stringify(detail)}`);}
function difference(a,b){let count=0;for(let i=0;i<a.pixels.length;i+=4)if(a.pixels[i]!==b.pixels[i]||a.pixels[i+1]!==b.pixels[i+1]||a.pixels[i+2]!==b.pixels[i+2])count++;return count;}
(async()=>{
  fs.mkdirSync(output,{recursive:true});
  const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1000}});page.on('pageerror',e=>errors.push(e.message));
    await page.goto(arg('--url','http://127.0.0.1:8771/?v=3.1'));
    await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
    check('initialized',await page.evaluate(()=>!!window.blackHolePreview?.ready),await page.evaluate(()=>window.blackHoleError));
    const initial=await page.evaluate(()=>({width:document.getElementById('scene').width,height:document.getElementById('scene').height,recipe:blackHolePreview.snapshot().parameters.name}));
    check('expanded_background_canvas',initial.width===960&&initial.height===600,initial);
    const full=await page.evaluate(()=>blackHolePreview.frame());
    let outerLit=0;for(let y=0;y<full.height;y++)for(let x=0;x<full.width;x++){
      if(x>full.width*.25&&x<full.width*.75&&y>full.height*.2&&y<full.height*.8)continue;
      const i=(y*full.width+x)*4;if(Math.max(...full.pixels.slice(i,i+3))>12)outerLit++;
    }
    check('background_reaches_wide_edges',outerLit>10000,{outerLit});
    await page.evaluate(()=>{const c=document.getElementById('scene');c.width=320;c.height=200;});
    await page.evaluate(()=>blackHolePreview.set({lensBackground:0},1.7));
    const base=await page.evaluate(()=>blackHolePreview.frame(0,3));
    await page.evaluate(()=>blackHolePreview.set({lensBackground:1},1.7));
    const lensed=await page.evaluate(()=>blackHolePreview.frame(0,3));
    check('background_lens_control_changes_output',difference(base,lensed)>1000,{changedPixels:difference(base,lensed)});
    await page.evaluate(()=>blackHolePreview.set({skyStrength:0},1.7));
    const diskOnly=await page.evaluate(()=>blackHolePreview.frame(0,2));
    await page.evaluate(()=>blackHolePreview.set({skyStrength:1},1.7));
    const diskAfterSky=await page.evaluate(()=>blackHolePreview.frame(0,2));
    const diskDifference=difference(diskOnly,diskAfterSky);
    check('disk_diagnostic_isolates_background',diskDifference===0,{diskDifference});
    const ray=await page.evaluate(()=>blackHolePreview.frame(0,1));
    let captured=0;for(let i=0;i<ray.pixels.length;i+=4)if(ray.pixels[i]>128)captured++;
    check('ray_diagnostic_has_capture',captured>100&&ray.glError===0,{captured,glError:ray.glError});
    await page.locator('#aspect').selectOption('square');
    check('square_view',await page.evaluate(()=>document.getElementById('scene').width===640&&document.getElementById('scene').height===640));
    await page.locator('#aspect').selectOption('cinema');
    check('cinema_view',await page.evaluate(()=>document.getElementById('scene').width===960&&document.getElementById('scene').height===540));
    const unit=await page.evaluate(()=>blackHolePreview.catalog.recipes.find(r=>r.name.startsWith('层叠光带')));
    check('unity_band_preset',unit?.diskStyle==='cinematic'&&unit.bandDetail<=6);
    await page.evaluate(p=>blackHolePreview.set(p,277.85),unit);
    const cyan=await page.evaluate(()=>blackHolePreview.frame());
    check('unity_band_long_playback',cyan.glError===0&&new Set(cyan.pixels).size>100);
    await page.locator('#scene').screenshot({path:path.join(output,'unity-cyan-277.85.png')});
    await page.locator('#aspect').selectOption('wide');
    await page.evaluate(()=>blackHolePreview.set(blackHolePreview.catalog.recipes[0],1.7));
    const fullSizeDiagnosticDifference=await page.evaluate(()=>{
      const result={};
      for(const bloom of [0,.62]){
        blackHolePreview.set({bloom,skyStrength:0},1.7);const a=blackHolePreview.frame(0,2);
        blackHolePreview.set({skyStrength:1},1.7);const b=blackHolePreview.frame(0,2);
        let changed=0,max=0,sum=0;const samples=[];
        for(let i=0;i<a.pixels.length;i+=4){const d=Math.max(Math.abs(a.pixels[i]-b.pixels[i]),Math.abs(a.pixels[i+1]-b.pixels[i+1]),Math.abs(a.pixels[i+2]-b.pixels[i+2]));if(d){changed++;max=Math.max(max,d);sum+=d;if(samples.length<10)samples.push({x:i/4%a.width,y:Math.floor(i/4/a.width),d});}}
        result[bloom]={changed,max,sum,samples};
      }
      blackHolePreview.set({skyStrength:.3,bloom:.62},1.7);return result;
    });
    check('full_size_disk_diagnostic_isolates_background',fullSizeDiagnosticDifference[0].changed===0&&fullSizeDiagnosticDifference[.62].changed===0,fullSizeDiagnosticDifference);
    await page.locator('#scene').screenshot({path:path.join(output,'cinematic-wide.png')});
    check('no_page_errors',errors.length===0,errors);
    console.log(JSON.stringify({status:'passed',checks:checks.length}));
  }finally{await browser.close();fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify({status:checks.every(c=>c.passed)&&errors.length===0?'passed':'failed',checks,errors},null,2));}
})().catch(e=>{console.error(e);process.exitCode=1;});
