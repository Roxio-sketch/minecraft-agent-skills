const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
(async()=>{const url=process.argv[2]||'http://127.0.0.1:8783/lab/',out=path.resolve(process.argv[3]||'rim-evidence');fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.env.BROWSER_EXE?{executablePath:process.env.BROWSER_EXE}:{})});const checks=[],errors=[];try{
const page=await browser.newPage({viewport:{width:1280,height:800}});page.on('pageerror',e=>errors.push(e.message));await page.goto(url);await page.waitForFunction(()=>window.rimLab);
const capture=async(p={},s=128)=>{const r=await page.evaluate(({p,s})=>rimLab.capture(s,s,p),{p,s});if(r.error)throw Error('GL '+r.error);return r.pixels;};
const diff=(a,b)=>a.reduce((n,v,i)=>n+(Math.abs(v-b[i])>1),0),check=(name,ok)=>{checks.push({name,passed:!!ok});};
const a=await capture();check('fixed_time_deterministic',diff(a,await capture())===0);check('gain_changes_surface',diff(a,await capture({gain:0}))>100);check('power_changes_band',diff(a,await capture({power:8}))>100);
check('zero_alpha_removes_rim',diff(await capture({baseAlpha:0,rimAlpha:0,gain:5}),await capture({baseAlpha:0,rimAlpha:0,gain:0}))===0);
check('zero_pulse_static',diff(await capture({time:0}),await capture({time:2}))===0);check('pulse_changes_color',diff(await capture({pulseAmount:.8,time:0}),await capture({pulseAmount:.8,time:.5}))>100);
check('hard_faces_rotate',diff(await capture({shape:'box',angle:0}),await capture({shape:'box',angle:.7}))>100);
for(const s of [16,32])check('small_'+s,diff(await capture({},s),await capture({baseAlpha:0,rimAlpha:0},s))>4);
await page.screenshot({path:path.join(out,'desktop.png')});await page.setViewportSize({width:390,height:844});await page.waitForTimeout(200);check('mobile_no_horizontal_overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'mobile.png'),fullPage:true});check('no_browser_errors',errors.length===0);
fs.writeFileSync(path.join(out,'validation.json'),JSON.stringify({checks,errors,scope:'Independent Three.js preview, not Forge runtime'},null,2));if(checks.some(c=>!c.passed))throw Error(JSON.stringify(checks));console.log(JSON.stringify(checks));
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
