const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const lab=path.resolve(__dirname,'..'),out=path.join(lab,'evidence/vortex-revision9/dev');
fs.mkdirSync(out,{recursive:true});
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const label=process.env.VORTEX_DEV_LABEL||'bowl9a';
const sourceHash=sha(fs.readFileSync(path.join(lab,'workbench/vortex/preview.js')));
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  page.setDefaultTimeout(180000);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8774/vortex/?dev=bowl9');
  await page.waitForFunction(()=>window.volumeLab?.ready);
  await page.evaluate(()=>window.volumeLab.pause(true));
  const frames=[];
  for(const [mode,view,time] of [[2,'outside',22],[2,'mouth',22],[2,'above',22],[2,'liquid',22],[1,'outside',22]]){
   await page.evaluate(async({mode,view,time})=>{await volumeLab.setMode(mode);await volumeLab.setView(view);await volumeLab.render(time);},{mode,view,time});
   const png=await page.evaluate(()=>volumeLab.capture()),file=`${label}-mode${mode}-${view}.png`;
   fs.writeFileSync(path.join(out,file),Buffer.from(png.split(',')[1],'base64'));
   frames.push({mode,view,time,file,state:await page.evaluate(()=>volumeLab.getState())});
  }
  fs.writeFileSync(path.join(out,`${label}.json`),JSON.stringify({previewSha256:sourceHash,sourceStayedFixed:sourceHash===sha(fs.readFileSync(path.join(lab,'workbench/vortex/preview.js'))),errors,frames},null,2));
  console.log(JSON.stringify({errors,frames:frames.map(f=>f.file)}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
