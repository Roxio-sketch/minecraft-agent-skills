/* Focused acceptance from an installed skill; run only after the final install. */
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto'),net=require('net');
const {spawn,execFileSync}=require('child_process');
const lab=path.resolve(__dirname,'..');
const installed=process.argv[2]||'C:/Users/MAINTAINER/.codex/skills/minecraft-volumetric-environment';
const python=process.argv[3]||'C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const out=path.join(lab,'evidence/portable-vortex-revision9'),workDir=path.join(out,'authored-work');
const port=8775,url=`http://127.0.0.1:${port}/vortex/`;
const sha=value=>crypto.createHash('sha256').update(value).digest('hex');
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const checks=[],screenshots={},hashes={},errors=[];
let server,browser,serverLog,work,helperExited=false;
const check=(name,passed,data)=>checks.push({name,passed:!!passed,...(data===undefined?{}:{data})});
const tracked=['workbench/vortex/volume-core.glsl','workbench/vortex/preview.js','workbench/vortex/vortex-noise.js','scripts/fixtures/frozen-vortex-core-iteration1.glsl'];
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function portOccupied(){return new Promise(resolve=>{const client=net.createConnection({host:'127.0.0.1',port});client.once('connect',()=>{client.destroy();resolve(true);});client.once('error',()=>resolve(false));client.setTimeout(1000,()=>{client.destroy();resolve(false);});});}
async function waitForServer(){
  for(let attempt=0;attempt<80;attempt++){
    if(server.exitCode!==null)throw Error('Portable server exited before becoming available');
    try{const response=await fetch(url);if(response.ok)return;}catch{}
    await delay(150);
  }
  throw Error('Portable server did not become available');
}
const inspectZip=String.raw`
import hashlib,json,pathlib,sys,zipfile
archive,work,skill=map(pathlib.Path,sys.argv[1:])
prefix='vortex-waterspout-workbench/'
tracked=['workbench/vortex/volume-core.glsl','workbench/vortex/preview.js','workbench/vortex/vortex-noise.js','scripts/fixtures/frozen-vortex-core-iteration1.glsl']
digest=lambda data:hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(archive) as z:
    names=z.namelist()
    manifest=json.loads(z.read(prefix+'manifest.json'))
    manifest_ok=all(len(z.read(prefix+name))==meta['bytes'] and digest(z.read(prefix+name))==meta['sha256'] for name,meta in manifest.items())
    exact={name:digest(z.read(prefix+name))==digest((work/name).read_bytes()) for name in tracked}
    report=json.loads(z.read(prefix+'evidence/vortex/report.json'))
    bound=report['coreSha256']==digest(z.read(prefix+tracked[0])) and report['previewSha256']==digest(z.read(prefix+tracked[1])) and report['noiseGeneratorSha256']==digest(z.read(prefix+tracked[2]))
    pictures={}
    for mode in range(3):
        name=f'mode-{mode}-time-20.png'
        data=z.read(prefix+'evidence/vortex/'+name)
        pictures[name]=data.startswith(bytes([137,80,78,71,13,10,26,10])) and report['screenshots'][name]==digest(data)
    study='references/research/vortex-waterspout-20261011/WATER_FUNNEL_RESEARCH.md'
    revision='references/research/vortex-waterspout-20261011/WATER_BODY_REVISION.md'
    study_ok=z.read(prefix+study)==(skill/study).read_bytes() and z.read(prefix+revision)==(skill/revision).read_bytes()
    readme=z.read(prefix+'README.md').decode('utf-8')
    excluded=all(not any(part in {'primary-pdf','shaderpacks','__pycache__','node_modules','.gradle','build','run'} for part in pathlib.PurePosixPath(name).parts) for name in names)
    print(json.dumps(dict(crc=z.testzip() is None,manifestExact=manifest_ok,sourceExact=exact,reportSourceBound=bound,threeModeEvidence=pictures,selectedResearchExact=study_ok,threeModesDescribed=all(f'mode={mode}' in readme for mode in range(3)),excluded=excluded,entries=len(names)),ensure_ascii=False))
`;
function savePreviousReports(){
  const current=path.join(lab,'evidence/vortex/portable.json');
  const history=[];
  const iteration1=path.join(lab,'evidence/vortex/iteration1/portable.json');
  if(fs.existsSync(iteration1))history.push({role:'iteration1',path:path.relative(lab,iteration1).replaceAll('\\','/'),sha256:sha(fs.readFileSync(iteration1))});
  if(fs.existsSync(current)){
    const old=fs.readFileSync(current),fingerprint=sha(old);
    const destination=path.join(lab,'evidence/vortex/history',`portable-${fingerprint.slice(0,16)}.json`);
    fs.mkdirSync(path.dirname(destination),{recursive:true});
    if(!fs.existsSync(destination))fs.writeFileSync(destination,old);
    if(!fs.readFileSync(destination).equals(old))throw Error('Existing portable history differs from its source');
    history.push({role:'previousUnchanged',path:path.relative(lab,destination).replaceAll('\\','/'),sha256:fingerprint});
  }
  return history;
}
(async()=>{
  fs.mkdirSync(out,{recursive:true});
  try{
    if(await portOccupied())throw Error('8775 is already occupied; the existing process was left untouched');
    if(fs.existsSync(workDir)&&fs.readdirSync(workDir).length)throw Error('The independent authored output must be new or empty');
    const authored=JSON.parse(execFileSync(python,['-X','utf8',path.join(installed,'scripts/author.py'),'--out',workDir,'--preset','ground-mist','--name','独立三模式水片便携验收'],{encoding:'utf8',windowsHide:true}).trim());
    check('installed skill authors a new independent work',authored.ok&&path.resolve(authored.root)===workDir,{installed,workDir});
    const finalReport=JSON.parse(fs.readFileSync(path.join(lab,'evidence/vortex/report.json'),'utf8'));
    for(const relative of tracked){
      const canonical=fs.readFileSync(path.join(lab,relative));
      const bundled=fs.readFileSync(path.join(installed,'assets/world-volume-lab',relative));
      const portable=fs.readFileSync(path.join(workDir,relative));
      hashes[relative]={canonical:sha(canonical),installed:sha(bundled),authored:sha(portable)};
      check('canonical installed authored bytes: '+relative,canonical.equals(bundled)&&bundled.equals(portable),hashes[relative]);
    }
    check('final GPU report binds portable source',finalReport.coreSha256===hashes[tracked[0]].authored&&finalReport.previewSha256===hashes[tracked[1]].authored&&finalReport.noiseGeneratorSha256===hashes[tracked[2]].authored);
    const schema=JSON.parse(fs.readFileSync(path.join(workDir,'workbench/vortex/schema.json'),'utf8'));
    const vortexSchema=JSON.parse(fs.readFileSync(path.join(workDir,'workbench/vortex/vortex-schema.json'),'utf8'));
    check('portable schemas expose 62 base plus 20 event fields',Object.keys(schema.fields).length===62&&Object.keys(vortexSchema.fields).length===20&&vortexSchema.fields.mode.max===2);
    serverLog=fs.openSync(path.join(out,'server.log'),'a');
    server=spawn(python,['-X','utf8',path.join(workDir,'workbench/serve.py'),'--port',String(port)],{cwd:workDir,windowsHide:true,stdio:['ignore',serverLog,serverLog]});
    server.once('exit',()=>{helperExited=true;});
    await waitForServer();
    browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
    const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});
    page.setDefaultTimeout(180000);page.on('pageerror',error=>errors.push(error.message));
    await page.goto(url,{waitUntil:'domcontentloaded',timeout:180000});
    await page.evaluate(()=>window.volumeLab.ready);await page.evaluate(()=>window.volumeLab.pause(true));
    const state=await page.evaluate(()=>window.volumeLab.getState());
    check('independent portable WebGL page renders',state.ready&&!state.failed&&state.rendered,state);
    for(const relative of tracked.slice(0,3)){
      const response=await page.request.get(`http://127.0.0.1:${port}/`+relative.slice('workbench/'.length));
      const served=await response.body();hashes[relative].served=sha(served);
      check('served bytes match authored source: '+relative,response.ok()&&sha(served)===hashes[relative].authored);
    }
    const noiseDataSha256=await page.evaluate(async()=>{const data=window.volumeLab.getNoiseData().data;const hash=await crypto.subtle.digest('SHA-256',data);return [...new Uint8Array(hash)].map(x=>x.toString(16).padStart(2,'0')).join('');});
    hashes.noiseData={browser:noiseDataSha256,finalReport:finalReport.noiseDataSha256};
    check('portable generated noise data matches final GPU report',noiseDataSha256===finalReport.noiseDataSha256,hashes.noiseData);
    const encoded={};
    for(const mode of [0,1,2]){
      await page.evaluate(mode=>window.volumeLab.setMode(mode),mode);await page.evaluate(()=>window.volumeLab.setView('outside'));await page.evaluate(()=>window.volumeLab.render(20));
      const captured=await page.evaluate(()=>window.volumeLab.capture()),bytes=Buffer.from(captured.split(',')[1],'base64');
      const name=`mode-${mode}-time-20.png`;fs.writeFileSync(path.join(out,name),bytes);screenshots[name]=sha(bytes);encoded[mode]=captured;
      const current=await page.evaluate(()=>window.volumeLab.getWork());
      check('mode '+mode+' renders complete portable effect',current.vortex.mode===mode&&Object.keys(current.vortex).length===20&&Object.keys(current.recipe.parameters).length===62&&bytes.length>1000&&bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])));
    }
    check('three modes produce distinct actual PNGs',new Set(Object.values(screenshots)).size===3);
    await page.evaluate(()=>window.volumeLab.setView('liquid'));await page.evaluate(()=>window.volumeLab.render(20));
    const near=await page.evaluate(()=>window.volumeLab.capture()),nearBytes=Buffer.from(near.split(',')[1],'base64');
    fs.writeFileSync(path.join(out,'mode-2-near.png'),nearBytes);screenshots['mode-2-near.png']=sha(nearBytes);
    check('mode 2 near view is an actual rendered PNG',nearBytes.length>1000&&near!==encoded[2]);
    await page.evaluate(()=>window.volumeLab.setView('mouth'));await page.evaluate(()=>window.volumeLab.render(20));
    const mouth=await page.evaluate(()=>window.volumeLab.capture()),mouthBytes=Buffer.from(mouth.split(',')[1],'base64');
    fs.writeFileSync(path.join(out,'mode-2-mouth.png'),mouthBytes);screenshots['mode-2-mouth.png']=sha(mouthBytes);
    check('installed mode 2 mouth view renders actual distinct PNG',mouthBytes.length>1000&&mouth!==near&&mouth!==encoded[2]);
    await page.evaluate(()=>window.volumeLab.setView('outside'));await page.evaluate(()=>window.volumeLab.render(20));
    const outside=await page.evaluate(()=>window.volumeLab.capture()),outsideBytes=Buffer.from(outside.split(',')[1],'base64');
    fs.writeFileSync(path.join(out,'mode-2-outside.png'),outsideBytes);screenshots['mode-2-outside.png']=sha(outsideBytes);
    check('mode 2 outside camera reset reproduces frame',outside===encoded[2]);
    work=await page.evaluate(()=>window.volumeLab.getWork());
    check('mode 2 has all schema keys and fixed time',work.vortex.mode===2&&work.preview.time===20&&same(Object.keys(work.recipe.parameters).sort(),Object.keys(schema.fields).sort())&&same(Object.keys(work.vortex).sort(),Object.keys(vortexSchema.fields).sort()));
    const [jsonDownload]=await Promise.all([page.waitForEvent('download'),page.locator('#jsonButton').click()]);
    const jsonPath=path.join(out,'mode-2-download.json');await jsonDownload.saveAs(jsonPath);
    const exported=JSON.parse(fs.readFileSync(jsonPath,'utf8'));
    check('actual JSON download includes full recipe event and camera',same(exported,work));
    const [pngDownload]=await Promise.all([page.waitForEvent('download'),page.locator('#pngButton').click()]);
    const pngPath=path.join(out,'mode-2-download.png');await pngDownload.saveAs(pngPath);
    check('actual PNG export matches mode 2 frame',sha(fs.readFileSync(pngPath))===screenshots['mode-2-time-20.png']);
    await page.evaluate(()=>window.volumeLab.setMode(0));await page.evaluate(()=>window.volumeLab.render(5));
    await page.locator('#importFile').setInputFiles(jsonPath);
    await page.waitForFunction(()=>window.volumeLab.getWork().vortex.mode===2&&window.volumeLab.getWork().preview.time===20&&document.getElementById('recipeError').hidden);
    const restored=await page.evaluate(()=>window.volumeLab.getWork());
    check('actual import restores complete mode 2 work',same(restored,work));
    await page.evaluate(()=>window.volumeLab.render(20));
    const replay=await page.evaluate(()=>window.volumeLab.capture());
    fs.writeFileSync(path.join(out,'mode-2-replay.png'),Buffer.from(replay.split(',')[1],'base64'));
    check('mode 2 import has exact PNG replay',replay===encoded[2]);
    const [sourceDownload]=await Promise.all([page.waitForEvent('download'),page.locator('a[href="vortex-workbench-source.zip"]').click()]);
    const sourcePath=path.join(out,'actual-source-download.zip');await sourceDownload.saveAs(sourcePath);
    const archiveSha256=sha(fs.readFileSync(sourcePath));hashes.sourceArchive={download:archiveSha256,authored:sha(fs.readFileSync(path.join(workDir,'workbench/vortex/vortex-workbench-source.zip')))};
    check('actual source ZIP download equals independent file',hashes.sourceArchive.download===hashes.sourceArchive.authored,hashes.sourceArchive);
    const zip=JSON.parse(execFileSync(python,['-X','utf8','-c',inspectZip,sourcePath,workDir,installed],{encoding:'utf8',windowsHide:true}).trim());
    check('source ZIP CRC and manifest bytes',zip.crc&&zip.manifestExact,zip);
    check('source ZIP ships exact core preview noise and fixture',Object.values(zip.sourceExact).every(Boolean),zip.sourceExact);
    check('source ZIP contains bound three mode evidence',zip.reportSourceBound&&Object.values(zip.threeModeEvidence).every(Boolean));
    check('source ZIP ships new research and three mode guidance',zip.selectedResearchExact&&zip.threeModesDescribed&&zip.excluded);
    check('new effect game resource export remains unavailable',await page.locator('#zipButton').isDisabled());
    await page.screenshot({path:path.join(out,'page.png'),fullPage:true});
    check('no portable browser errors',errors.length===0,errors);
  }catch(error){check('portable acceptance completed',false,{error:error.stack||String(error)});}
  finally{
    if(browser)await browser.close();
    if(server&&server.exitCode===null){server.kill();await Promise.race([new Promise(resolve=>server.once('exit',resolve)),delay(2000)]);}
    if(serverLog!==undefined)fs.closeSync(serverLog);
    const stopped=!server||helperExited||server.exitCode!==null||server.signalCode!==null;check('owned 8775 helper stopped',stopped,{pid:server?.pid,exitCode:server?.exitCode??null,signalCode:server?.signalCode??null});
    const history=savePreviousReports();
    const report={date:new Date().toISOString(),iteration:9,coreSha256:hashes[tracked[0]]?.canonical,previewSha256:hashes[tracked[1]]?.canonical,noiseGeneratorSha256:hashes[tracked[2]]?.canonical,ok:checks.every(item=>item.passed),passed:checks.every(item=>item.passed),scope:'Installed skill creates independent work; actual portable three-mode WebGL PNG/JSON replay and source ZIP download; no Minecraft certification',installedSkill:installed,authoredWork:workDir,url,checks,hashes,screenshots,work,history};
    const text=JSON.stringify(report,null,2)+'\n';fs.writeFileSync(path.join(out,'report.json'),text);fs.writeFileSync(path.join(lab,'evidence/vortex/portable.json'),text);
    console.log(JSON.stringify({ok:report.ok,checks:checks.length,failed:checks.filter(item=>!item.passed),output:out}));if(!report.ok)process.exitCode=1;
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
