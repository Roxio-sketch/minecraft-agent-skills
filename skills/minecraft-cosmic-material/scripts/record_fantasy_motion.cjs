// Fixed-time sequences for visual motion review, not a real-time FPS benchmark.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {options,startServer,stopChild,selectBrowser,verifyService,assetDigest}=require('./preview_validation_support.cjs');
const argv=process.argv.slice(2);
const baselineIndex=argv.indexOf('--baseline-dir');
let baseline;
if(baselineIndex>=0){baseline=path.resolve(argv[baselineIndex+1]);argv.splice(baselineIndex,2);}
const presetIndex=argv.indexOf('--preset');
let selectedPreset;
if(presetIndex>=0){selectedPreset=argv[presetIndex+1];if(!selectedPreset||selectedPreset.startsWith('--'))throw Error('--preset needs an ID');argv.splice(presetIndex,2);}
const settings=options(argv);
if(settings.help){console.log('record_fantasy_motion.cjs --out DIR [--python PATH] [--url URL] [--baseline-dir DIR] [--preset ID[,ID...]]');process.exit(0);}
async function run(){
  const {chromium}=require('playwright');let browser,owned;
  fs.mkdirSync(settings.out,{recursive:true});
  const records=[];
  try{
    if(!settings.url){owned=await startServer(settings.python,'fantasy');settings.url=owned.url;}
    const service=await verifyService(settings.url,'fantasy');
    browser=await chromium.launch({executablePath:selectBrowser(chromium,settings.browser),headless:true,args:['--enable-webgl']});
    const page=await browser.newPage({viewport:{width:1200,height:900}});page.setDefaultTimeout(120000);
    const sources={};
    if(baseline)for(const name of ['material.glsl','recipes.json']){
      const content=fs.readFileSync(path.join(baseline,name));
      sources[name]=crypto.createHash('sha256').update(content).digest('hex');
      await page.route('**/'+name,route=>route.fulfill({body:content,contentType:name.endsWith('json')?'application/json':'text/plain'}));
    }
    await page.goto(service.url);await page.waitForFunction(()=>window.fantasyLab!==undefined);
    if(!(await page.evaluate(()=>fantasyLab.ready)))throw Error(await page.evaluate(()=>fantasyLab.error));
    let ids=await page.evaluate(()=>fantasyLab.presets);
    if(selectedPreset){const chosen=selectedPreset.split(',');for(const id of chosen)if(!ids.includes(id))throw Error('Unknown preset: '+id);ids=chosen;}
    for(const id of ids){
      const dir=path.join(settings.out,id);fs.mkdirSync(dir,{recursive:true});
      const result=await page.evaluate(async id=>{
        fantasyLab.selectPreset(id);fantasyLab.render({time:0,view:[0,0],shape:0});
        const canvas=document.createElement('canvas');canvas.width=320;canvas.height=216;
        const ctx=canvas.getContext('2d',{willReadFrequently:true});
        const frames=[],deltas=[];let previous;
        for(let i=0;i<120;i++){
          fantasyLab.render({time:i/20});
          ctx.drawImage(document.getElementById('scene'),0,0,320,216);
          const pixels=ctx.getImageData(0,0,320,216).data;
          if(previous){let sum=0,changed=0,max=0;for(let k=0;k<pixels.length;k+=4){let pd=0;for(let c=0;c<3;c++){const d=Math.abs(pixels[k+c]-previous[k+c]);sum+=d;pd=Math.max(pd,d);max=Math.max(max,d);}if(pd>2)changed++;}deltas.push({time:i/20,meanChannelDelta:sum/(320*216*3),fractionOver2:changed/(320*216),maxChannelDelta:max});}
          previous=pixels;
          frames.push(canvas.toDataURL('image/png').split(',')[1]);
          if(i%12===0)await new Promise(resolve=>requestAnimationFrame(resolve));
        }
        return {id,name:fantasyLab.recipe().parameters.name,recipe:fantasyLab.recipe(),frames,deltas,diagnostics:fantasyLab.diagnostics()};
      },id);
      result.frames.forEach((png,i)=>fs.writeFileSync(path.join(dir,String(i).padStart(4,'0')+'.png'),Buffer.from(png,'base64')));
      delete result.frames;records.push(result);console.log('Recorded '+id+': 120 frames at 20 samples/s');
    }
    fs.writeFileSync(path.join(settings.out,'motion.json'),JSON.stringify({scope:'Fixed-time 6-second clips; not seamless loops, not a performance benchmark. Visual review required.',fps:20,frames:120,source:baseline?{baseline,sources}:{assetDigest:assetDigest('fantasy')},records},null,2));
  }finally{if(browser)await browser.close();if(owned)await stopChild(owned.child);}
}
run().catch(e=>{console.error(e);process.exitCode=1;});
