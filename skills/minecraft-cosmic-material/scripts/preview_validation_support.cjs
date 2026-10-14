const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const crypto=require('node:crypto');
const {spawn}=require('node:child_process');
function assetDigest(lab='cosmic'){
  if(!['cosmic','fantasy'].includes(lab))throw Error('Unknown lab: '+lab);
  const assets=path.resolve(__dirname,'../assets',lab==='fantasy'?'fantasy-lab':'authoring-lab');
  const files=['index.html','material.glsl','preview.js','recipes.json'],h=crypto.createHash('sha256');
  for(const f of files)h.update(f+'\0').update(fs.readFileSync(path.join(assets,f))).update('\0');
  if(lab==='fantasy')h.update('ocean-spectrum.js\0').update(fs.readFileSync(path.join(assets,'ocean-spectrum.js'))).update('\0');
  if(lab==='fantasy')for(const f of files)h.update('cosmic/'+f+'\0').update(fs.readFileSync(path.join(assets,'../authoring-lab',f))).update('\0');
  return h.digest('hex');
}
function options(argv){
  const o={out:path.join(os.tmpdir(),'cosmic-validation-'+new Date().toISOString().replace(/[:.]/g,'-')),python:process.env.COSMIC_PYTHON||(process.platform==='win32'?'python':'python3'),browser:process.env.COSMIC_BROWSER,url:process.env.COSMIC_PREVIEW_URL,freezeRuns:30};
  for(let i=0;i<argv.length;i++){
    if(argv[i]==='--help'){o.help=true;continue;}
    const key={'--out':'out','--url':'url','--python':'python','--browser':'browser','--freeze-runs':'freezeRuns'}[argv[i]];
    if(!key||!argv[i+1]||argv[i+1].startsWith('--'))throw Error('Unknown or incomplete argument: '+argv[i]);
    o[key]=argv[++i];
  }
  o.freezeRuns=Number(o.freezeRuns);
  if(!Number.isInteger(o.freezeRuns)||o.freezeRuns<1||o.freezeRuns>200)throw Error('--freeze-runs must be 1..200');
  o.out=path.resolve(o.out);return o;
}
function difference(a,b){
  if(a.length!==b.length)throw Error('Framebuffer dimensions differ');
  let count=0,total=0,max=0,changedChannels=0;
  for(let i=0;i<a.length;i+=4){let changed=false;for(let c=0;c<3;c++){const d=Math.abs(a[i+c]-b[i+c]);max=Math.max(max,d);total+=d;if(d){changed=true;changedChannels++;}}if(changed)count++;}
  return {changedPixels:count,maxChannelDifference:max,changedChannels,meanChannelDifference:total/(a.length/4*3)};
}
async function stopChild(child){
  if(!child||child.exitCode!==null||child.signalCode!==null)return;
  await new Promise(resolve=>{const timer=setTimeout(resolve,3000);child.once('exit',()=>{clearTimeout(timer);resolve();});child.kill();});
}
async function startServer(python,lab='cosmic'){
  const child=spawn(python,[path.join(__dirname,'serve_authoring.py'),'--port','0','--json','--lab',lab],{windowsHide:true,stdio:['ignore','pipe','pipe'],env:{...process.env,PYTHONDONTWRITEBYTECODE:'1',PYTHONUTF8:'1'}});
  let stderr='',stdout='';child.stderr.on('data',d=>{stderr=(stderr+d).slice(-4000);});
  try{
    const startup=await new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(Error('Preview server startup timed out: '+stderr)),15000);
      const fail=e=>{clearTimeout(timer);reject(e);};
      child.once('error',e=>fail(Error('Cannot start Python; pass --python with an existing interpreter. '+e.message)));
      child.once('exit',code=>fail(Error('Preview server exited '+code+': '+stderr)));
      child.stdout.on('data',d=>{stdout+=d;const newline=stdout.indexOf('\n');if(newline>=0){try{const record=JSON.parse(stdout.slice(0,newline));clearTimeout(timer);resolve(record);}catch(e){fail(e);}}});
    });
    return {child,...startup};
  }catch(e){await stopChild(child);throw e;}
}
function selectBrowser(chromium,explicit){
  if(explicit){if(!fs.existsSync(explicit))throw Error('Browser executable missing: '+explicit);return explicit;}
  const candidates=process.platform==='win32'?[
    path.join(process.env['ProgramFiles(x86)']||'C:/Program Files (x86)','Microsoft/Edge/Application/msedge.exe'),
    path.join(process.env.ProgramFiles||'C:/Program Files','Google/Chrome/Application/chrome.exe')
  ]:[];
  candidates.push(chromium.executablePath());
  const found=candidates.find(p=>fs.existsSync(p));
  if(!found)throw Error('No installed Chromium browser found. Pass --browser; none will be installed automatically.');
  return found;
}
async function verifyService(url,lab='cosmic'){
  const parsed=new URL(url);
  if(parsed.protocol!=='http:'||!['127.0.0.1','localhost','[::1]'].includes(parsed.hostname))throw Error('--url must identify a local HTTP preview');
  const response=await fetch(new URL('/__cosmic__/health',parsed),{signal:AbortSignal.timeout(5000)});
  if(!response.ok)throw Error('Preview identity endpoint returned '+response.status);
  const identity=await response.json();
  if(identity.application!=='minecraft-cosmic-material'||identity.protocol!==1||(identity.lab||'cosmic')!==lab||identity.assetDigest!==assetDigest(lab))throw Error('Wrong preview service or different asset version; refusing to validate this URL.');
  parsed.searchParams.set('test','1');return {url:parsed.href,identity};
}
module.exports={assetDigest,options,difference,startServer,stopChild,selectBrowser,verifyService};
