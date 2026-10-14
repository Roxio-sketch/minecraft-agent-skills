// Isolate star transport from luminosity changes; preserve full-size temporal evidence.
const fs=require('node:fs'),path=require('node:path');
const {options,startServer,stopChild,selectBrowser,verifyService,assetDigest,difference}=require('./preview_validation_support.cjs');
const settings=options(process.argv.slice(2));
if(settings.help){console.log('validate_star_motion.cjs [--python PATH] [--browser PATH] [--out DIR]');process.exit(0);}
const checks=[],records=[];
const check=(name,passed,detail)=>checks.push({name,passed:!!passed,detail});
(async()=>{
  let owned,browser;
  fs.mkdirSync(settings.out,{recursive:true});
  try{
    if(!settings.url){owned=await startServer(settings.python);settings.url=owned.url;}
    const service=await verifyService(settings.url);
    const {chromium}=require('playwright');
    browser=await chromium.launch({executablePath:selectBrowser(chromium,settings.browser),headless:true,args:['--enable-webgl']});
    const page=await browser.newPage({viewport:{width:1380,height:1120}}),errors=[];
    page.setDefaultTimeout(120000);page.on('pageerror',e=>errors.push(e.message));
    await page.goto(service.url);await page.waitForFunction(()=>window.cosmicLab!==undefined);
    if(!await page.evaluate(()=>cosmicLab.ready))throw Error(await page.evaluate(()=>cosmicLab.error));
    for(const id of ['deep-field','violet-nebula','ember-rift']){
      const samples=await page.evaluate(id=>{
        cosmicLab.selectPreset(id);
        cosmicLab.render({time:0,layers:{starsOn:1,cloudsOn:0,dustOn:0},parameters:{speed:0,twinkle:0}});
        const before=[0,1,2].map(i=>cosmicLab.capture(i));cosmicLab.render({time:4});
        return {before,after:[0,1,2].map(i=>cosmicLab.capture(i))};
      },id);
      for(let i=0;i<3;i++){
        const delta=difference(samples.before[i],samples.after[i]);
        check(id+':stars_only_motion:'+i,delta.changedPixels>0&&delta.maxChannelDifference>2,delta);
      }
    }
    const cases=[
      {id:'drift',name:'Star drift / clouds off',parameters:{speed:0,twinkle:0},layers:{starsOn:1,cloudsOn:0,dustOn:0}},
      {id:'twinkle',name:'Star twinkle / drift off',parameters:{speed:0,starSpeed:0},layers:{starsOn:1,cloudsOn:0,dustOn:0}},
      {id:'combined',name:'Deep field / all layers',parameters:{},layers:{starsOn:1,cloudsOn:1,dustOn:1}}
    ];
    for(const scenario of cases){
      const dir=path.join(settings.out,scenario.id);fs.mkdirSync(dir,{recursive:true});
      const result=await page.evaluate(async scenario=>{
        cosmicLab.selectPreset('deep-field');cosmicLab.render({...scenario,time:0,view:[0,0],shape:0});
        const canvas=document.getElementById('scene'),w=canvas.width,h=canvas.height;
        const luma=a=>{const b=new Float32Array(w*h);for(let i=0;i<b.length;i++)b[i]=(a[i*4]*.2126+a[i*4+1]*.7152+a[i*4+2]*.0722);return b;};
        const first=luma(cosmicLab.capture()),candidates=[];
        for(let y=14;y<h-14;y++)for(let x=14;x<w-14;x++){
          const value=first[y*w+x];if(value<90)continue;
          let peak=true;for(let dy=-2;dy<=2;dy++)for(let dx=-2;dx<=2;dx++)if((dx||dy)&&first[(y+dy)*w+x+dx]>=value)peak=false;
          if(peak)candidates.push({x,y,value});
        }
        // Prefer isolated bright peaks so neighboring stars cannot fake a trajectory.
        const tracks=candidates.filter(a=>!candidates.some(b=>a!==b&&Math.hypot(a.x-b.x,a.y-b.y)<14))
          .sort((a,b)=>b.value-a.value).slice(0,120).map(a=>({x:a.x,y:a.y,origin:[a.x,a.y],samples:[]}));
        const frames=[],deltas=[];let previous=first;
        for(let frame=0;frame<120;frame++){
          cosmicLab.render({time:frame/20});const values=luma(cosmicLab.capture());
          let sum=0,max=0;for(let i=0;i<values.length;i++){const d=Math.abs(values[i]-previous[i]);sum+=d;max=Math.max(max,d);}
          deltas.push({time:frame/20,meanLumaDelta:sum/values.length,maxLumaDelta:max});previous=values;
          for(const track of tracks){
            const tx=scenario.id==='drift'?track.x:track.origin[0],ty=scenario.id==='drift'?track.y:track.origin[1];
            const cx=Math.round(tx),cy=Math.round(ty);let energy=0,sx=0,sy=0;
            for(let dy=-3;dy<=3;dy++)for(let dx=-3;dx<=3;dx++){
              const x=cx+dx,y=cy+dy;if(x<0||x>=w||y<0||y>=h)continue;
              const weight=Math.max(0,values[y*w+x]-22);energy+=weight;sx+=weight*x;sy+=weight*y;
            }
            if(energy>0){track.x=sx/energy;track.y=sy/energy;}
            track.samples.push({time:frame/20,x:track.x,y:track.y,energy});
          }
          frames.push(canvas.toDataURL('image/png').split(',')[1]);
          if(frame%10===0)await new Promise(resolve=>requestAnimationFrame(resolve));
        }
        return {frames,tracks,deltas,recipe:cosmicLab.recipe(),diagnostics:cosmicLab.diagnostics(),width:w,height:h};
      },scenario);
      result.frames.forEach((data,i)=>fs.writeFileSync(path.join(dir,String(i).padStart(4,'0')+'.png'),Buffer.from(data,'base64')));
      delete result.frames;
      const summaries=result.tracks.map(t=>{
        const a=t.samples[0],b=t.samples.at(-1),energies=t.samples.map(s=>s.energy);
        return {origin:t.origin,distance:Math.hypot(b.x-a.x,b.y-a.y),maxStep:Math.max(...t.samples.slice(1).map((s,i)=>Math.hypot(s.x-t.samples[i].x,s.y-t.samples[i].y))),relativePulse:(Math.max(...energies)-Math.min(...energies))/Math.max(...energies)};
      });
      if(scenario.id==='drift')check('persistent_stars_have_continuous_trajectories',summaries.filter(t=>t.distance>3&&t.maxStep<1.25).length>=15,{tracked:summaries.length,continuousMoving:summaries.filter(t=>t.distance>3&&t.maxStep<1.25).length,summaries});
      if(scenario.id==='twinkle')check('stationary_stars_visibly_pulse',summaries.filter(t=>t.relativePulse>.2&&t.distance<.8&&t.maxStep<.8).length>=15,{tracked:summaries.length,stationaryPulsing:summaries.filter(t=>t.relativePulse>.2&&t.distance<.8&&t.maxStep<.8).length,summaries});
      check(scenario.id+':no_gl_errors',result.diagnostics.errors.every(e=>e===0),result.diagnostics);
      records.push({...scenario,...result});console.log('Recorded '+scenario.id+': 120 frames, 6 seconds at 20 samples/s');
    }
    const frozen=await page.evaluate(()=>{
      cosmicLab.render({time:0,parameters:{speed:0,starSpeed:0,twinkleRate:0}});
      const a=cosmicLab.capture();cosmicLab.render({time:93});return {a,b:cosmicLab.capture(),uniforms:cosmicLab.diagnostics().materialTime};
    });
    const delta=difference(frozen.a,frozen.b);
    check('zero_rates_freeze_with_nonzero_twinkle_strength',delta.changedPixels===0&&frozen.uniforms.every(t=>t===0),{...delta,uniforms:frozen.uniforms});
    // Also exercise the wall-clock animation loop, with the cloud/dust disabled.
    const live=new URL(service.url);live.searchParams.delete('test');await page.goto(live.href);
    await page.waitForFunction(()=>window.cosmicLab?.ready);
    await page.evaluate(()=>cosmicLab.render({time:0,layers:{starsOn:1,cloudsOn:0,dustOn:0},parameters:{speed:0,twinkle:0}}));
    if(!await page.evaluate(()=>cosmicLab.diagnostics().playing))await page.locator('#motion').click();
    const before=await page.evaluate(()=>({pixels:cosmicLab.capture(),time:cosmicLab.recipe().preview.time}));
    await page.waitForTimeout(1600);
    await page.locator('#motion').click();
    const after=await page.evaluate(()=>({pixels:cosmicLab.capture(),time:cosmicLab.recipe().preview.time}));
    check('live_loop_moves_stars_without_clouds',after.time>before.time+.5&&difference(before.pixels,after.pixels).changedPixels>100,{before:before.time,after:after.time,...difference(before.pixels,after.pixels)});
    await page.waitForTimeout(250);const paused=await page.evaluate(()=>cosmicLab.capture());
    check('pause_freezes_star_motion',difference(after.pixels,paused).changedPixels===0,difference(after.pixels,paused));
    check('no_page_errors',errors.length===0,errors);
  }catch(e){check('execution_exception',false,{message:e.message,stack:e.stack});}
  finally{
    if(browser)await browser.close();if(owned)await stopChild(owned.child);
    const status=checks.length&&checks.every(c=>c.passed)?'passed':'failed';
    fs.writeFileSync(path.join(settings.out,'motion.json'),JSON.stringify({scope:'WebGL study. Fixed-time six-second clips, not a game recording, seamless loop or FPS benchmark. Live-loop checks are recorded separately.',fps:20,frames:120,source:{assetDigest:assetDigest()},records},null,2));
    fs.writeFileSync(path.join(settings.out,'validation.json'),JSON.stringify({status,checks,checkedAt:new Date().toISOString(),assetDigest:assetDigest()},null,2));
    console.log(JSON.stringify({status,checks:checks.length,failed:checks.filter(c=>!c.passed).map(c=>c.name),directory:settings.out}));
    if(status!=='passed')process.exitCode=1;
  }
})().catch(e=>{console.error(e);process.exitCode=1;});
