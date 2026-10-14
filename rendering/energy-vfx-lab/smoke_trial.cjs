/* Read-only editor recipe trials. This never modifies shader or recipe source files. */
const fs=require('fs'),path=require('path');
const {chromium}=require('C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
 const page=await browser.newPage({viewport:{width:1440,height:980}});
 await page.goto('file:///'+__dirname.replace(/\\/g,'/')+'/index.html');
 await page.waitForFunction(()=>energyEditor.getStatus().ready);
 const trials=[
  ['baseline',{}],
  ['clear',{density:1.1,emission:1.6,absorption:.45,shellWidth:.035,sharpness:22,filaments:.12,scale:1.2,detail:1.2,stretch:1.15,bloom:0,resolution:1,steps:128}],
  ['fine',{density:1.8,emission:2.2,absorption:.35,shellWidth:.025,sharpness:30,filaments:.25,scale:1.8,detail:1.4,stretch:1.1,warp:1.35,speed:1.25,erosion:.15,softness:.32,bloom:0,resolution:1,steps:128}],
  ['large-curl',{density:1.6,emission:1.8,absorption:.35,shellWidth:.03,sharpness:24,filaments:.08,scale:.75,detail:1,stretch:1,warp:1.5,speed:2,swirl:1.8,puffRate:5,puffLife:3,spread:.25,erosion:.1,softness:.3,bloom:0,resolution:1,steps:128}],
  ['broken-curl',{density:2.2,emission:1.6,absorption:.4,shellWidth:.025,sharpness:22,filaments:.32,scale:1.5,detail:1.5,stretch:1,warp:1.4,speed:1.6,swirl:1.2,puffRate:6,puffLife:3,spread:.26,erosion:.36,softness:.12,bloom:0,resolution:1,steps:128}],
  ['wide-light',{density:1.5,emission:2,absorption:.25,shellWidth:.04,sharpness:12,filaments:.1,scale:1,detail:1.2,stretch:.85,warp:1.5,speed:1.7,swirl:1.5,puffRate:6,puffLife:3,spread:.29,erosion:.22,softness:.3,bloom:0,resolution:1,steps:128}],
  ['minimal-grain',{density:1.5,emission:2.2,absorption:.3,shellWidth:.055,sharpness:8,filaments:0,scale:.8,detail:1,stretch:1,warp:1.5,speed:1.8,swirl:1.2,puffRate:5,puffLife:3,spread:.25,erosion:0,softness:.5,bloom:0,resolution:1,steps:128}],
  ['bright-minimal',{density:1.5,emission:6,absorption:.3,shellWidth:.04,sharpness:8,filaments:0,scale:.8,detail:1,stretch:1,warp:1.5,speed:1.8,swirl:1.2,puffRate:5,puffLife:3,spread:.25,erosion:0,softness:.5,bloom:0,resolution:1,steps:128}],
  ['open-arcs',{density:1.5,emission:7,absorption:.35,shellWidth:.045,sharpness:8,filaments:.1,scale:1,detail:1,stretch:1,warp:1.5,speed:1.8,swirl:1.2,puffRate:5,puffLife:3,spread:.28,erosion:.59,softness:.07,bloom:0,resolution:1,steps:128}],
  ['bright-seed117',{density:1.5,emission:6,absorption:.3,shellWidth:.04,sharpness:8,filaments:0,scale:.8,detail:1,stretch:1,warp:1.5,speed:1.8,swirl:1.2,puffRate:5,puffLife:3,spread:.25,erosion:0,softness:.5,bloom:0,resolution:1,steps:128,seed:117}],
  ['bright-seed509',{density:1.5,emission:6,absorption:.3,shellWidth:.04,sharpness:8,filaments:0,scale:.8,detail:1,stretch:1,warp:1.5,speed:1.8,swirl:1.2,puffRate:5,puffLife:3,spread:.25,erosion:0,softness:.5,bloom:0,resolution:1,steps:128,seed:509}],
 ];
 const records=[];
 for(const [name,params] of trials){
  const result=await page.evaluate(({name,params})=>{
   const recipe=JSON.parse(JSON.stringify(energyEditor.presets[14]));recipe.name='Read-only trial '+name;recipe.color='#ecf0f2';recipe.secondary='#a8c4cc';Object.assign(recipe.params,params);
   energyEditor.applyRecipe(recipe);energyEditor.setIsolated(true);energyEditor.setCamera(.3,.08,4.5);energyEditor.setTime(3);energyEditor.draw();
   return {image:canvas.toDataURL(),recipe,status:energyEditor.getStatus(),packets:auraPackets(recipe,3,false,true)};
  },{name,params});
  const file=path.join(__dirname,'evidence','trial-'+name+'.png');fs.writeFileSync(file,Buffer.from(result.image.split(',')[1],'base64'));
  records.push({name,file,recipe:result.recipe,status:result.status,count:result.packets.count,drift:result.packets.drift});
  console.log(name,JSON.stringify(result.status));
 }
 // Report parameters inline; trial artifacts are limited to this script and its PNG images.
 console.log(JSON.stringify(records.map(({name,recipe,count,drift})=>({name,params:recipe.params,count,drift})),null,2));
 await browser.close();
})().catch(error=>{console.error(error);process.exitCode=1;});
