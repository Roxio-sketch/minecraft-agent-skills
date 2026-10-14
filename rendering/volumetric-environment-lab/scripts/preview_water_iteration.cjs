/* Actual fixed-time renders for the water-sheet visual review. */
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.resolve(__dirname,'..'),out=path.join(root,'evidence/water-sheets-iteration');
const url=process.env.WATER_REVIEW_URL||'http://127.0.0.1:8774/vortex/';
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const b=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
 try{
  const page=await b.newPage({viewport:{width:1280,height:900}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  const begin=Date.now();await page.goto(url,{timeout:120000});
  await page.waitForFunction(()=>Boolean(window.volumeLab),{timeout:120000});
  await page.evaluate(()=>window.volumeLab.ready);await page.evaluate(()=>window.volumeLab.pause(true));
  const initialMs=Date.now()-begin;console.log(JSON.stringify({initialized:true,initialMs}));
  const frames=[];
  for(const time of [8,14,20,20.5,30]){
   const start=Date.now();await page.evaluate(t=>window.volumeLab.render(t),time);
   const bytes=Buffer.from((await page.evaluate(()=>window.volumeLab.capture())).split(',')[1],'base64');
   const file=`water-${time}.png`;fs.writeFileSync(path.join(out,file),bytes);
   frames.push({time,file,renderAndReadMs:Date.now()-start,sha256:crypto.createHash('sha256').update(bytes).digest('hex')});
  }
  for(const [mode,view] of [[1,'liquid'],[2,'outside'],[2,'liquid'],[2,'ground']]){
   await page.evaluate(m=>window.volumeLab.setMode(m),mode);await page.evaluate(v=>window.volumeLab.setView(v),view);await page.evaluate(()=>window.volumeLab.render(20));
   const bytes=Buffer.from((await page.evaluate(()=>window.volumeLab.capture())).split(',')[1],'base64');
   const file=`mode-${mode}-${view}.png`;fs.writeFileSync(path.join(out,file),bytes);frames.push({mode,view,time:20,file,sha256:crypto.createHash('sha256').update(bytes).digest('hex')});
  }
  for(const mode of [1,2]){
   await page.evaluate(m=>window.volumeLab.setMode(m),mode);await page.evaluate(()=>window.volumeLab.setView('outside'));
   const motionDir=path.join(out,`motion-${mode}`);fs.mkdirSync(motionDir,{recursive:true});
   for(let i=0;i<24;i++){
    const time=20+i/10;await page.evaluate(t=>window.volumeLab.render(t),time);
    const bytes=Buffer.from((await page.evaluate(()=>window.volumeLab.capture())).split(',')[1],'base64');
    fs.writeFileSync(path.join(motionDir,`${String(i).padStart(3,'0')}.png`),bytes);
   }
  }
  await page.evaluate(()=>window.volumeLab.setMode(2));await page.evaluate(()=>window.volumeLab.setView('liquid'));
  const surfaceDir=path.join(out,'surface-flow-2');fs.mkdirSync(surfaceDir,{recursive:true});
  for(let i=0;i<30;i++){
   await page.evaluate(t=>window.volumeLab.render(t),20+i/10);
   const bytes=Buffer.from((await page.evaluate(()=>window.volumeLab.capture())).split(',')[1],'base64');
   fs.writeFileSync(path.join(surfaceDir,`${String(i).padStart(3,'0')}.png`),bytes);
  }
  await page.screenshot({path:path.join(out,'page.png'),fullPage:true});
  const core=fs.readFileSync(path.join(root,'workbench/vortex/volume-core.glsl')),preview=fs.readFileSync(path.join(root,'workbench/vortex/preview.js'));
  const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
  const report={ok:errors.length===0,initialMs,frames,errors,coreSha256:sha(core),previewSha256:sha(preview),motion:{modes:[1,2],times:[20,22.3],fps:10},work:await page.evaluate(()=>window.volumeLab.getWork())};
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({ok:report.ok,initialMs,frames,errors}));
 }finally{await b.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
