(()=>{
 const $=id=>document.getElementById(id),canvas=$('canvas'),ctx=canvas.getContext('2d'),clamp=LightningCore.clamp;
 let recipe={...defaultRecipe},paused=false,time=0,yaw=.45,pitch=.16,zoom=1.0,drag=null,active=[],geometryKey="",cachedBolt=[],cachedRain=[];
 const defs={
  orbit:[['strands','电弧条数',1,32,1],['samples','每条采样',16,256,1],['rodLength','棍状物长度',.5,32,.1],['rodRadius','棍状物半径',.01,.6,.01],['emitterSpread','根部径向分布',0,1,.01],['arcLength','电弧长度',.1,32,.1],['orbitExtendSpeed','延伸速度（格/秒）',.1,120,.1],['lengthRandomness','长度随机',0,.95,.01],['pulseInterval','脉冲间隔基准（tick）',.5,20,.1],['throttle','出现概率',0,1,.01],['orbitWobble','抖动幅度',0,1,.01],['orbitFlow','抖动速度',-8,8,.05],['orbitNoiseFrequency','噪声频率',.05,4,.05],['orbitDirection','电弧方向',-1,1,2],['currentWidth','核心宽度',.003,.12,.001],['glowWidth','光晕宽度',.01,.8,.01]],
  strike:[['strikeHeight','闪电高度',4,64,1],['strikeWidth','通道宽度',.004,.2,.001],['strikeGenerations','主干细分',3,9,1],['strikeDisplacement','位移幅度',.1,6,.05],['strikeRoughness','粗糙衰减',.35,.85,.01],['branchProbability','分叉概率',0,1,.01],['maxBranchDepth','分叉深度',0,4,1],['branchDecay','分叉衰减',.1,.95,.01],['branchLength','分叉长度',.05,1,.01],['progressionTicks','先行时间',.1,8,.1],['durationTicks','闪光时长',3,40,.1],['restrikeCount','重击次数',0,4,1],['flicker','闪烁量',0,1,.01],['maxSegments','段数预算',64,2400,16]],
  rain:[['strikeHeight','落雷高度',4,64,1],['strikeWidth','通道宽度',.004,.2,.001],['strikeGenerations','主干细分',3,8,1],['strikeDisplacement','位移幅度',.1,6,.05],['strikeRoughness','粗糙衰减',.35,.85,.01],['rainCount','落雷数量',1,12,1],['rainSpread','水平范围',1,32,.5],['rainDepthSpread','纵深范围',0,16,.1],['rainStagger','落雷间隔',0,8,.01],['rainBranchProbability','严格分叉概率',0,.12,.01],['rainMaxBranchDepth','最大分叉层数',0,1,1],['rainMaxBranches','分叉总数上限',0,2,1],['progressionTicks','先行时间',.1,8,.1],['durationTicks','闪光时长',3,40,.1],['flicker','闪烁量',0,1,.01],['maxSegments','每道段数预算',64,1600,16],['impactRadius','击地电弧范围（格）',.1,6,.1],['impactDuration','击地持续（tick）',2,40,.5],['impactRays','击地电弧条数',1,24,1],['impactSparks','击地火花数量',0,32,1]]
 };
 function controls(){return defs[recipe.mode].filter(([key])=>recipe.mode!=='orbit'||(recipe.orbitAnimation==='ring'?!['rodLength','rodRadius','emitterSpread'].includes(key):recipe.orbitAnimation==='spektr'?!['emitterSpread','orbitDirection','orbitExtendSpeed'].includes(key):true))}
 function readSeed(){const n=Number($('seed').value);return Number.isSafeInteger(n)?n:0}
 function rebuild(){
  const box=$('controls');box.innerHTML='';
  for(const [key,label,_min,_max,step] of controls()){let [min,max]=recipeRanges[key];if(key==='durationTicks')min=Math.max(min,recipe.progressionTicks+2);if(key==='progressionTicks')max=Math.min(max,recipe.durationTicks-2);const l=document.createElement('label');l.innerHTML=`<span>${label}<output id="out-${key}"></output></span><input id="ctl-${key}" type="range" min="${min}" max="${max}" step="${step}" value="${recipe[key]}">`;box.append(l);const input=l.querySelector('input');input.oninput=e=>{recipe[key]=+e.target.value;render()}}
  if(recipe.mode==='orbit'){const extra=recipe.orbitAnimation==='ring'?[['orbitRadius','环绕半径',.2,8,.05],['objectRadius','参照物半径',.1,4,.05]]:[];for(const [key,label,min,max,step] of extra){const l=document.createElement('label');l.innerHTML=`<span>${label}<output id="out-${key}">${recipe[key]}</output></span><input id="ctl-${key}" type="range" min="${min}" max="${max}" step="${step}" value="${recipe[key]}">`;box.prepend(l);l.querySelector('input').oninput=e=>{recipe[key]=+e.target.value;$(`out-${key}`).textContent=recipe[key];render()}}const l=document.createElement('label');l.innerHTML='<span>动画模型</span><select id="orbitAnimation"><option value="grow">局部根部延长</option><option value="spektr">Spektr 短段</option><option value="ring">环形电弧</option></select>';box.prepend(l);$('orbitAnimation').value=recipe.orbitAnimation;$('orbitAnimation').onchange=e=>{recipe.orbitAnimation=e.target.value;pitch=recipe.orbitAnimation==='ring'?.95:.16;rebuild();trigger()}}
  if(recipe.mode==='rain'){
   const l=document.createElement('label');l.innerHTML='<span>落雷样式</span><select id="rainStyle"><option value="spread">范围落雷</option><option value="cluster">集束落雷</option></select>';box.prepend(l);$('rainStyle').value=recipe.rainStyle;
   $('rainStyle').onchange=e=>{recipe.rainStyle=e.target.value;if(recipe.rainStyle==='cluster'){recipe.rainSpread=3;recipe.rainDepthSpread=2.4;recipe.rainCount=9;recipe.rainStagger=.22;recipe.rainMaxBranchDepth=0;recipe.rainMaxBranches=0;recipe.rainBranchProbability=0}rebuild();trigger()};
   const enabled=document.createElement('label');enabled.innerHTML='<input id="impactEnabled" type="checkbox"> 启用击地电弧 / 火花';box.append(enabled);$('impactEnabled').checked=recipe.impactEnabled;$('impactEnabled').onchange=e=>{recipe.impactEnabled=e.target.checked;render()};
  }
  $('mode').value=recipe.mode;$('modeOut').textContent=recipe.mode;$('statMode').textContent=recipe.mode;$('seed').value=recipe.seed;
  $('guideLabel').hidden=!(recipe.mode==='orbit'&&recipe.orbitAnimation==='ring');
  $('modeTitle').textContent=recipe.mode==='orbit'?(recipe.orbitAnimation==='spektr'?'Spektr 风格 · 轴向短段脉冲':recipe.orbitAnimation==='ring'?'环形电弧 · 极坐标扫掠':'棍状物 · 局部根部延长'):recipe.mode==='rain'?(recipe.rainStyle==='cluster'?'集束落雷 · 密集雷柱与击地放电':'落雷场 · 多道下落与击地放电'):'雷劫闪电 · 固定种子分形通道';
  $('modeHint').textContent=recipe.mode==='orbit'?(recipe.orbitAnimation==='spektr'?'源码短段窗口移动；控制延长请切换局部根部或环形电弧':recipe.orbitAnimation==='ring'?'固定根部延长；延伸速度控制弧头，抖动速度控制噪声':'每道电弧在棍身上固定自己的根部，从自身一端延长，棍子不是统一发射端'):recipe.mode==='rain'?'集束模式收拢落点；主干触地后才生成地面电弧、闪光和火花':'相同 seed 保持同一拓扑；换 seed 才改变形状';
  $('coreColor').value=recipe.coreColor;$('glowColor').value=recipe.glowColor;
  $('gradientEnabled').checked=recipe.gradientEnabled;$('gradientStart').value=recipe.gradientStart;$('gradientEnd').value=recipe.gradientEnd;
  render();
 }
 function project(p){const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch),ring=recipe.mode==='orbit'&&recipe.orbitAnimation==='ring',distance=recipe.mode==='orbit'?(ring?Math.max(4,recipe.orbitRadius*3):Math.max(6.4,recipe.rodLength*.75)):Math.max(8,recipe.strikeHeight*.65),targetY=recipe.mode==='orbit'?recipe.orbitHeight:recipe.strikeHeight*(recipe.mode==='rain'?.28:.45);
  const py=p[1]-targetY,x=p[0]*cy-p[2]*sy,z=p[0]*sy+p[2]*cy,y=py*cp-z*sp,zz=py*sp+z*cp,depth=zz+distance;
  if(!Number.isFinite(depth)||depth<.55)return null;const scale=canvas.height*(ring?.70:recipe.mode==='orbit'?.52:recipe.mode==='rain'?.38:.44)*zoom/depth,screen=[canvas.width/2+x*scale,canvas.height*.52-y*scale,zz,scale];return screen.every(Number.isFinite)?screen:null;
 }
 function hex(c){return [parseInt(c.slice(1,3),16),parseInt(c.slice(3,5),16),parseInt(c.slice(5,7),16)]}
 function colorHex(v){return '#'+v.map(x=>Math.round(clamp(x,0,255)).toString(16).padStart(2,'0')).join('')}
 function gradientColor(r,t,halo){const base=hex(halo?r.glowColor:r.coreColor);if(!r.gradientEnabled)return colorHex(base);const a=hex(r.gradientStart),b=hex(r.gradientEnd),g=a.map((x,i)=>x+(b[i]-x)*clamp(t,0,1));return colorHex(base.map((x,i)=>x*g[i]/255))}
 function drawLine(seg,color,width,alpha){const a=project(seg.a),b=project(seg.b);if(!a||!b||Math.max(Math.abs(a[0]),Math.abs(a[1]),Math.abs(b[0]),Math.abs(b[1]))>Math.max(canvas.width,canvas.height)*4)return;ctx.strokeStyle=`${color}${Math.round(clamp(alpha,0,1)*255).toString(16).padStart(2,'0')}`;ctx.lineWidth=width;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(a[0],a[1]);ctx.lineTo(b[0],b[1]);ctx.stroke()}
 // One stroke per visible connected path, including gradient mode. Never join
 // points across a near-plane rejection: doing that created long straight lines.
 function drawContinuous(segs,r,halo,widthScale,alphaScale){
  const groups=new Map();for(const s of segs){const key=s.path??0;if(!groups.has(key))groups.set(key,[]);groups.get(key).push(s)}
  for(const g of groups.values()){
   let run=[];const flush=()=>{
    if(run.length<2){run=[];return}const first=run[0],last=run[run.length-1];
    let color=gradientColor(r,(first.along+last.along)*.5,halo);
    if(r.gradientEnabled&&Math.hypot(last.p[0]-first.p[0],last.p[1]-first.p[1])>.1){color=ctx.createLinearGradient(first.p[0],first.p[1],last.p[0],last.p[1]);color.addColorStop(0,gradientColor(r,first.along,halo));color.addColorStop(1,gradientColor(r,last.along,halo))}
    ctx.strokeStyle=color;ctx.globalAlpha=clamp(Math.max(...run.map(n=>n.alpha))*alphaScale,0,1);ctx.lineWidth=run.reduce((sum,n)=>sum+n.w,0)/run.length*widthScale;ctx.lineCap='butt';ctx.lineJoin='round';ctx.beginPath();ctx.moveTo(first.p[0],first.p[1]);for(let i=1;i<run.length;i++)ctx.lineTo(run[i].p[0],run[i].p[1]);ctx.stroke();ctx.globalAlpha=1;run=[];
   };
   for(const s of g){const a=project(s.a),b=project(s.b);if(!a||!b||Math.max(Math.abs(a[0]),Math.abs(a[1]),Math.abs(b[0]),Math.abs(b[1]))>Math.max(canvas.width,canvas.height)*4){flush();continue}if(run.length&&Math.hypot(a[0]-run[run.length-1].p[0],a[1]-run[run.length-1].p[1])>.01)flush();if(!run.length)run.push({p:a,w:s.w,along:s.along,alpha:s.alpha});run.push({p:b,w:s.wb??s.w,along:s.alongB,alpha:s.alpha})}flush();
  }
 }
 function render(){
  if(!canvas.width)return;ctx.clearRect(0,0,canvas.width,canvas.height);const grd=ctx.createRadialGradient(canvas.width*.5,canvas.height*.42,10,canvas.width*.5,canvas.height*.5,canvas.height*.68);grd.addColorStop(0,'#1c4265');grd.addColorStop(1,'#040a12');ctx.fillStyle=grd;ctx.fillRect(0,0,canvas.width,canvas.height);
  const center=project([0,recipe.mode==='orbit'?recipe.orbitHeight:0,0]),rad=center?recipe.objectRadius*center[3]:8;
  const key=JSON.stringify(recipe);if(key!==geometryKey){geometryKey=key;cachedBolt=recipe.mode==='strike'?LightningCore.bolt(recipe):[];cachedRain=recipe.mode==='rain'?LightningCore.rain(recipe):[]}
  if(recipe.mode==='rain')drawGround();
  if(recipe.mode==='strike'&&center){ctx.fillStyle='#06101a';ctx.strokeStyle='#5d9ac2';ctx.lineWidth=2;ctx.beginPath();ctx.arc(center[0],center[1],Math.max(8,rad),0,Math.PI*2);ctx.fill();ctx.stroke()}
  if(recipe.mode==='orbit'){
   if(recipe.orbitAnimation==='ring'){
    if(center){ctx.fillStyle='#06101a';ctx.strokeStyle='#5d9ac2';ctx.lineWidth=2;ctx.beginPath();ctx.arc(center[0],center[1],Math.max(8,rad),0,Math.PI*2);ctx.fill();ctx.stroke()}
    if($('guide').checked){const pts=[];for(let i=0;i<=96;i++){const a=i/96*Math.PI*2;const p=project([Math.cos(a)*recipe.orbitRadius,recipe.orbitHeight,Math.sin(a)*recipe.orbitRadius]);if(p)pts.push(p)}if(pts.length>1){ctx.strokeStyle='#2c5069';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(pts[0][0],pts[0][1]);for(const p of pts.slice(1))ctx.lineTo(p[0],p[1]);ctx.stroke()}}
   } else {
   const ra=project([-recipe.rodLength*.5,recipe.orbitHeight,0]),rb=project([recipe.rodLength*.5,recipe.orbitHeight,0]);
   drawLine({a:[-recipe.rodLength*.5,recipe.orbitHeight,0],b:[recipe.rodLength*.5,recipe.orbitHeight,0]},'#07111a',Math.max(10,recipe.rodRadius*canvas.height*.24/6.4*2.6),.95);
   drawLine({a:[-recipe.rodLength*.5,recipe.orbitHeight,0],b:[recipe.rodLength*.5,recipe.orbitHeight,0]},'#2c5069',Math.max(4,recipe.rodRadius*canvas.height*.24/6.4*1.35),.95);
   for(const p of [ra,rb].filter(Boolean)){ctx.fillStyle='#6c9bb6';ctx.beginPath();ctx.arc(p[0],p[1],Math.max(3,recipe.rodRadius*canvas.height*.24/6.4*1.3),0,Math.PI*2);ctx.fill()}
   }
   active=LightningCore.orbit(recipe,time);drawEffect(active,recipe,canvas.height*.1);
  }
  else if(recipe.mode==='strike'){active=LightningCore.clip(recipe,cachedBolt,time);drawEffect(active,recipe,canvas.height*.055)}
  else {active=[];for(const b of cachedRain){const part=LightningCore.clip({...recipe,mode:'strike',seed:b.seed},b.segments,time-b.delay);active.push(...part);const ground=LightningCore.impact(recipe,b,time-b.delay);active.push(...ground);drawEffect(part,recipe,canvas.height*.055);drawEffect(ground,recipe,canvas.height*.1);drawImpactFlash(b,time-b.delay)}}
  $('statSegments').textContent=active.length;$('statTime').textContent=time.toFixed(2);$('statBudget').textContent=recipe.mode==='orbit'?'—':recipe.maxSegments;for(const [key] of controls()){$(`out-${key}`).textContent=recipe[key];if(key==='durationTicks')$(`ctl-${key}`).min=recipe.progressionTicks+2;if(key==='progressionTicks')$(`ctl-${key}`).max=Math.min(8,recipe.durationTicks-2)}$('scrub').max=Math.max(40,time,recipe.mode==='orbit'?20*recipe.arcLength/recipe.orbitExtendSpeed+30:Math.max(recipe.durationTicks,recipe.impactEnabled?recipe.progressionTicks+recipe.impactDuration:0)+(recipe.rainCount-1)*recipe.rainStagger);$('scrub').value=time;$('scrubOut').textContent=time.toFixed(2)+' tick';
 }
 function drawGround(){
  const extent=Math.min(20,Math.max(4,recipe.rainSpread*.5+recipe.impactRadius));
  for(let i=-Math.ceil(extent);i<=extent;i++)for(const axis of [0,1]){const a=axis?[i,-.03,-extent]:[-extent,-.03,i],b=axis?[i,-.03,extent]:[extent,-.03,i];drawLine({a,b},'#365b70',1,.23)}
 }
 function drawImpactFlash(b,t){
  const age=t-recipe.progressionTicks,power=LightningCore.impactPower(recipe,t)*Math.exp(-Math.max(0,age)*.9),p=project(b.end);
  if(!p||power<=0)return;const radius=Math.max(3,recipe.impactRadius*p[3]*.45),color=gradientColor(recipe,1,false),g=ctx.createRadialGradient(p[0],p[1],0,p[0],p[1],radius);g.addColorStop(0,'#ffffff');g.addColorStop(.2,color);g.addColorStop(1,color+'00');ctx.globalCompositeOperation='lighter';ctx.globalAlpha=Math.min(1,power);ctx.fillStyle=g;ctx.fillRect(p[0]-radius,p[1]-radius,radius*2,radius*2);ctx.globalAlpha=1;ctx.globalCompositeOperation='source-over';
 }
 function drawEffect(segs,r,coreScale){
  const base=r.mode==='orbit'?r.currentWidth:r.strikeWidth,haloScale=r.glowWidth/base*coreScale;
  ctx.globalCompositeOperation='lighter';
  for(const [scale,alpha] of [[2,.08],[1.2,.15],[.55,.32]])drawContinuous(segs,r,true,haloScale*scale,alpha*r.glowGain/2.4);
  drawContinuous(segs,r,false,coreScale,1.8*r.coreGain/3.8);ctx.globalCompositeOperation='source-over';
 }
 function trigger(){time=0;paused=false;$('pause').textContent='暂停';render()}
 $('scrub').oninput=e=>{time=+e.target.value;paused=true;$('pause').textContent='继续';render()};
 $('guide').onchange=render;
 function resize(){const d=devicePixelRatio||1,r=canvas.getBoundingClientRect();canvas.width=r.width*d;canvas.height=r.height*d;ctx.setTransform(1,0,0,1,0,0);render()}
 $('clusterPreset').onclick=()=>{recipe={...presets.cluster};yaw=.35;pitch=.28;zoom=1;rebuild();trigger()};$('shortSourcePreset').onclick=()=>{recipe={...presets.shortSource};yaw=.05;pitch=.16;zoom=1;rebuild();trigger()};$('sourcePreset').onclick=()=>{recipe={...presets.source};yaw=.05;pitch=.16;zoom=1;rebuild();trigger()};$('ringPreset').onclick=()=>{recipe={...presets.ring};yaw=.45;pitch=.95;zoom=1;rebuild();trigger()};
 $('mode').onchange=e=>{recipe.mode=e.target.value;if(recipe.mode==='rain'){yaw=.35;pitch=.28;zoom=1}time=0;rebuild()};$('seed').onchange=()=>{recipe.seed=readSeed();trigger()};$('trigger').onclick=trigger;$('randomSeed').onclick=()=>{recipe.seed=(crypto.getRandomValues(new Int32Array(1))[0]);$('seed').value=recipe.seed;trigger()};$('pause').onclick=()=>{paused=!paused;$('pause').textContent=paused?'继续':'暂停'};$('reset').onclick=()=>{recipe={...presets[recipe.mode==='orbit'?(recipe.orbitAnimation==='spektr'?'source':recipe.orbitAnimation==='ring'?'ring':'orbit'):recipe.mode==='rain'&&recipe.rainStyle==='cluster'?'cluster':recipe.mode]};time=0;rebuild()};$('coreColor').oninput=e=>{recipe.coreColor=e.target.value;render()};$('glowColor').oninput=e=>{recipe.glowColor=e.target.value;render()};$('gradientEnabled').onchange=e=>{recipe.gradientEnabled=e.target.checked;render()};$('gradientStart').oninput=e=>{recipe.gradientStart=e.target.value;render()};$('gradientEnd').oninput=e=>{recipe.gradientEnd=e.target.value;render()};
 $('export').onclick=()=>{const blob=new Blob([JSON.stringify(recipe,null,2)],{type:'application/json'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='recipe.json';a.click();$('status').textContent='已导出当前 seed 与参数'};$('import').onclick=()=>$('file').click();$('file').onchange=async e=>{try{const loaded=JSON.parse(await e.target.files[0].text());recipe=validateRecipe(loaded);time=0;rebuild();$('status').textContent='已导入 JSON；相同 seed 会复现相同闪电'}catch(err){$('status').textContent='导入失败: '+err.message}};$('json').onclick=()=>{$('jsonText').value=JSON.stringify(recipe,null,2);$('dialog').showModal()};$('copy').onclick=async()=>{await navigator.clipboard.writeText(JSON.stringify(recipe,null,2));$('status').textContent='JSON 已复制'};$('close').onclick=()=>$('dialog').close();
 canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId)};canvas.onpointermove=e=>{if(!drag)return;yaw+=(e.clientX-drag[0])*.01;pitch=clamp(pitch+(e.clientY-drag[1])*.01,-1.2,1.2);drag=[e.clientX,e.clientY];render()};canvas.onpointerup=()=>drag=null;canvas.onwheel=e=>{zoom=clamp(zoom-e.deltaY*.001,.65,2.3);render()};window.addEventListener('resize',resize);rebuild();resize();let previous=null;function loop(now){if(previous!==null&&!paused)time+=Math.min(.1,(now-previous)/1000)*20;previous=now;render();requestAnimationFrame(loop)}requestAnimationFrame(loop);
})();
