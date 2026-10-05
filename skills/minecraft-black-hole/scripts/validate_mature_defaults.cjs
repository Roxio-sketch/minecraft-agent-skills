// Fresh horizontal preset and explicit-angle compatibility checks in the real workbench.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),{chromium}=require('playwright');
const args=process.argv.slice(2),arg=(name,fallback)=>{const i=args.indexOf(name);return i<0?fallback:args[i+1];};
const root=path.resolve(__dirname,'../assets/preview'),output=path.resolve(arg('--output','mature-default-evidence'));
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const server=http.createServer((request,response)=>{
  const relative=decodeURIComponent(new URL(request.url,'http://localhost').pathname).replace(/^\/+/, '')||'index.html';
  const file=path.resolve(root,relative);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){response.writeHead(404);response.end();return;}
  response.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.json')?'application/json':file.endsWith('.html')?'text/html':'text/plain');response.end(fs.readFileSync(file));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('http://127.0.0.1:'+server.address().port+'/');
  await page.waitForFunction(()=>window.blackHolePreview?.ready||window.blackHoleError,undefined,{timeout:120000});
  const results=await page.evaluate(()=>{
   if(!window.blackHolePreview?.ready)throw Error(window.blackHoleError);
   const api=blackHolePreview,presets=api.catalog.recipes;
   if(!presets.every(recipe=>recipe.tilt===0))throw Error('A fresh preset is not horizontal');
   const start=api.snapshot();if(start.parameters.tilt!==0)throw Error('Landing preset is not horizontal');
   if(typeof start.parameters.core!=='number'||typeof start.parameters.particles!=='boolean')throw Error('Wrong JSON scalar types');
   api.set({...start.parameters,tilt:-51},12.5);const explicit=api.snapshot();
   api.set(explicit.parameters,explicit.time);const roundtrip=api.snapshot();
   if(roundtrip.parameters.tilt!==-51||roundtrip.time!==12.5)throw Error('Explicit imported angle/time was overwritten');
   const frame=api.frame();if(frame.glError!==0)throw Error('WebGL error '+frame.glError);
   api.set({...start.parameters,tilt:90},1.7);const vertical=api.snapshot();if(vertical.parameters.tilt!==90)throw Error('Manual 90-degree tilt is unavailable');
   api.set(start.parameters,start.time);
   return {presets:presets.length,horizontalDefault:true,explicitAnglePreserved:true,typedRoundtrip:true,manualVerticalAvailable:true,glError:frame.glError};
  });
  await page.screenshot({path:path.join(output,'horizontal-workbench.png')});
  if(arg('--world-recipe')){
   const recipe=JSON.parse(fs.readFileSync(arg('--world-recipe'),'utf8'));
   const worldResult=await page.evaluate(recipe=>{
    const api=blackHolePreview,start=api.snapshot();
    api.importSnapshot(recipe);let imported=api.snapshot();
    if(imported.schema!=='black-hole-world/v1'||imported.parameters.lensAffectsPlayer!==true||imported.parameters.scale!==1.6||imported.parameters.distance!==14||imported.parameters.tilt!==-51)throw Error('World bridge lost imported settings');
    api.set({tilt:-42,speed:2.2,hot:'#0044ff'},14);const edited=api.snapshot();api.importSnapshot(edited);
    const roundtrip=api.snapshot();if(JSON.stringify(roundtrip)!==JSON.stringify(edited))throw Error('World bridge export/import changed fields');
    const invalid=JSON.parse(JSON.stringify(edited));invalid.parameters.bloom='not-a-number';let rejected=false;
    try{api.importSnapshot(invalid);}catch(expected){rejected=true;}
    if(!rejected||JSON.stringify(api.snapshot())!==JSON.stringify(roundtrip))throw Error('Invalid world metadata mutated active state');
    const unknown=JSON.parse(JSON.stringify(start));unknown.parameters.lensAffectsPlayer=true;rejected=false;
    try{api.importSnapshot(unknown);}catch(expected){rejected=true;}if(!rejected)throw Error('Legacy strict unknown-field validation was weakened');
    const frame=api.frame();if(frame.glError!==0)throw Error('World bridge rendering failed');
    return roundtrip;
   },recipe);
   fs.writeFileSync(path.join(output,'world-roundtrip.json'),JSON.stringify(worldResult,null,2)+'\n');
   results.worldBridgePreserved=true;results.invalidWorldMetadataRejected=true;results.legacyUnknownFieldsRejected=true;
   await page.screenshot({path:path.join(output,'world-bridge.png')});
  }
  if(errors.length)throw Error(errors.join('\n'));
  fs.writeFileSync(path.join(output,'validation.json'),JSON.stringify({status:'passed',...results,minecraftRuntimeVerified:false},null,2)+'\n');
  console.log(JSON.stringify(results));
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
