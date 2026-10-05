const {chromium}=require('playwright');
const fs=require('node:fs');
const path=require('node:path');
const {PNG}=require('pngjs');
const args=process.argv.slice(2),arg=(key,fallback)=>{const i=args.indexOf(key);return i<0?fallback:args[i+1];};
const out=path.resolve(arg('--output','core-occlusion-evidence'));
fs.mkdirSync(out,{recursive:true});
function save(name,f){const png=new PNG({width:f.width,height:f.height});for(let y=0;y<f.height;y++)for(let x=0;x<f.width;x++){const a=((f.height-1-y)*f.width+x)*4,b=(y*f.width+x)*4;for(let k=0;k<4;k++)png.data[b+k]=f.pixels[a+k];}fs.writeFileSync(path.join(out,name+'.png'),PNG.sync.write(png));}
function interiorMask(rays){const {width:w,height:h,pixels:p}=rays,mask=new Uint8Array(w*h);for(let y=3;y<h-3;y++)for(let x=3;x<w-3;x++){let captured=true;for(let dy=-2;dy<=2&&captured;dy++)for(let dx=-2;dx<=2;dx++){const i=((y+dy)*w+x+dx)*4;if(p[i]<250||p[i+1]>5||p[i+2]>5){captured=false;break;}}if(captured)mask[y*w+x]=1;}return mask;}
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
 try{
  const page=await browser.newPage({viewport:{width:1000,height:700}}),pageErrors=[];page.on('pageerror',e=>pageErrors.push(String(e)));
  await page.goto(arg('--url','http://127.0.0.1:8771/'),{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
  if(await page.evaluate(()=>!!window.blackHoleError))throw Error(await page.evaluate(()=>window.blackHoleError));
  const names=['体积尘埃 · BlackHole 研究','层叠光带 · Unity 研究','程序光带 · 基础结构','颗粒吸积 · 完整组合'];
  const recipes=await page.evaluate(names=>names.map(name=>blackHolePreview.catalog.recipes.find(r=>r.name===name)),names);
  if(recipes.some(recipe=>!recipe))throw Error('A main recipe is missing');
  const jetBase=await page.evaluate(()=>blackHolePreview.catalog.recipes.find(r=>r.name==='日冕视界 · 电影质感'));
  if(!jetBase)throw Error('Stress-test base recipe is missing');
  const jet={...jetBase,name:'喷流与星云压力场景',jetStrength:1.4,jetLength:7,jetWidth:.27,halo:.65,haloRing:.7,haloDisk:.8,haloAura:.75,backgroundStyle:'nebula',bloom:1.2};
  const report=[];
  const cases=args.includes('--stress-only')?[[4,jet]]:[...recipes,jet].map((recipe,index)=>[index,recipe]);
  for(const [index,recipe] of cases)for(const time of [1.7,1163.11]){
   const width=320,height=200;
   const set=async s=>page.evaluate(({s,time,width,height})=>{const c=document.getElementById('scene');c.width=width;c.height=height;blackHolePreview.set(s,time);},{s,time,width,height});
   await set({...recipe,skyStrength:2});
   const rays=await page.evaluate(()=>blackHolePreview.frame(0,1));
   const sky=await page.evaluate(()=>blackHolePreview.frame(0,0));
   await set({...recipe,skyStrength:0});
   const dark=await page.evaluate(()=>blackHolePreview.frame(0,0));
   const mask=interiorMask(rays);let count=0,maxDiff=0,sumDiff=0;
   for(let i=0;i<mask.length;i++)if(mask[i]){count++;for(let c=0;c<3;c++){const d=Math.abs(sky.pixels[i*4+c]-dark.pixels[i*4+c]);maxDiff=Math.max(maxDiff,d);sumDiff+=d;}}
   if(index===4&&time===1.7){save('jet-sky',sky);save('jet-sky-off',dark);save('jet-rays',rays);}
   const row={name:recipe.name,time,count,maxDiff,meanDiff:count?sumDiff/(count*3):null,glErrors:[rays.glError,sky.glError,dark.glError]};report.push(row);console.log(JSON.stringify(row));
   if(!count||maxDiff>1||row.glErrors.some(Boolean))throw Error('Opaque core leaked sky in '+recipe.name+' at '+time);
  }
  if(pageErrors.length)throw Error(pageErrors.join('\n'));
  fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({report,pageErrors},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
