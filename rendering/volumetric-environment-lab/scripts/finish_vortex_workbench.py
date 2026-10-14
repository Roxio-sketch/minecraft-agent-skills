from pathlib import Path
out=Path('volumetric-environment-lab/workbench/vortex');p=out/'preview.js';s=p.read_text(encoding='utf-8')
s=s.replace('let schema, contract, presets, recipe;', '''let schema, contract, presets, recipe, vortexSchema;
let vortex={};const vortexUI=new Map();
function validateVortex(candidate){
  if(!candidate||typeof candidate!=='object'||Array.isArray(candidate)||Object.keys(candidate).length!==Object.keys(vortexSchema.fields).length)throw Error('必须提供完整涡旋参数');
  for(const [key,spec] of Object.entries(vortexSchema.fields)){const v=candidate[key];if(typeof v!=='number'||!Number.isFinite(v)||v<spec.min||v>spec.max||(key==='mode'&&!Number.isInteger(v)))throw Error(`无效涡旋参数 ${key}`);}
  if(candidate.topRadius<candidate.bottomRadius)throw Error('顶部半径不能小于底部半径');return clone(candidate);
}
function exportVortex(){return {schema:'vortex-cloud/1',recipe:clone(recipe),vortex:clone(vortex),preview:{time:elapsed,camera:clone(camera)}};}
function syncVortex(){for(const [key,inputs] of vortexUI){inputs.range.value=vortex[key];if(document.activeElement!==inputs.number)inputs.number.value=vortex[key];}document.querySelectorAll('[data-mode]').forEach(el=>el.classList.toggle('active',Number(el.dataset.mode)===vortex.mode));$('effectTitle').textContent=vortex.mode?'龙吸水 · 体积雾与水面':'龙卷风 · 体积云漏斗';}
function applyVortex(candidate){vortex=validateVortex(candidate);syncVortex();if(ready)draw();}
function importVortex(candidate){
  if(candidate?.schema!=='vortex-cloud/1')throw Error('需要完整 vortex-cloud/1 作品JSON');
  if(Object.keys(candidate).some(k=>!['schema','recipe','vortex','preview'].includes(k)))throw Error('未知作品字段');
  const base=validateRecipe(candidate.recipe),v=validateVortex(candidate.vortex);
  if(candidate.preview){const a=candidate.preview;if(!Number.isFinite(a.time)||a.time<0)throw Error('预览时间无效');const c=a.camera;if(!c||!Array.isArray(c.position)||c.position.length!==3||!c.position.every(Number.isFinite)||!Number.isFinite(c.yaw)||!Number.isFinite(c.pitch))throw Error('预览相机无效');}
  return {base,v,preview:candidate.preview};
}
function buildVortexFields(){
  for(const [key,spec] of Object.entries(vortexSchema.fields)){
    if(key==='mode')continue;
    const row=document.createElement('div');row.className='field';const label=document.createElement('label');label.textContent=spec.label;label.htmlFor='vortex-'+key;
    const number=document.createElement('input');number.type='number';number.id='vortex-'+key;
    const range=document.createElement('input');range.type='range';range.setAttribute('aria-label',spec.label);
    for(const el of [number,range]){el.min=spec.min;el.max=spec.max;el.step=spec.step;}
    row.append(label,number,range);$('vortexFields').append(row);vortexUI.set(key,{number,range});
    for(const el of [number,range])el.addEventListener('input',()=>{try{applyVortex({...vortex,[key]:el.value===''?NaN:Number(el.value)});clearRecipeError();}catch(e){displayError(e.message);}});
  }
  syncVortex();
}
''')
s=s.replace("gl.uniform1f(loc('Time'),Math.max(0,elapsed));", """gl.uniform1f(loc('Time'),Math.max(0,elapsed));
  gl.uniform4f(loc('VortexShape'),vortex.bottomRadius,vortex.topRadius,vortex.profile,vortex.hollow);
  gl.uniform4f(loc('VortexFlow'),vortex.rotation,vortex.rise,vortex.bend,vortex.turbulence);
  gl.uniform4f(loc('VortexRing'),vortex.ringRadius,vortex.ringWidth,vortex.sprayDensity,vortex.sprayHeight);
  gl.uniform4f(loc('VortexWater'),vortex.waterDepth,vortex.waveAmp,vortex.waveSpeed,vortex.foam);
  gl.uniform4f(loc('VortexLife'),vortex.duration,vortex.inflow,vortex.canopyDensity,vortex.mode);""")
s=s.replace("$(id).disabled = !ready || failed || !lastRendered || !$('recipeError').hidden;", "$(id).disabled = id==='zipButton' || !ready || failed || !lastRendered || !$('recipeError').hidden;")
s=s.replace("[schema,contract,presets]=await Promise.all([fetchResource('schema.json'),fetchResource('contract.json'),fetchResource('presets.json')]);", "[schema,contract,presets,vortexSchema]=await Promise.all([fetchResource('schema.json'),fetchResource('contract.json'),fetchResource('presets.json'),fetchResource('vortex-schema.json')]);vortex=Object.fromEntries(Object.entries(vortexSchema.fields).map(([key,spec])=>[key,spec.default]));")
s=s.replace('buildFields();syncFields();bindEvents();','buildFields();syncFields();buildVortexFields();bindEvents();')
s=s.replace('function bindEvents() {', '''function bindEvents() {
  document.querySelectorAll('[data-mode]').forEach(el=>el.addEventListener('click',()=>{vortex.mode=Number(el.dataset.mode);recipe.name=vortex.mode?'龙吸水 · 雾柱与喷雾环':'龙卷风 · 灰黑漏斗';Object.assign(recipe.parameters,vortex.mode?{mediumR:.53,mediumG:.61,mediumB:.7,ambient:.48,density:1.65}:{mediumR:.32,mediumG:.34,mediumB:.37,ambient:.35,density:2.3});syncFields();syncVortex();draw();}));
  $('timeline').addEventListener('input',()=>{paused=true;syncPause();setTime(Number($('timeline').value));});
  $('replay').addEventListener('click',()=>{setTime(0);paused=false;syncPause();});
''')
s=s.replace('const imported=parseStrictJSON(await file.text());setRecipe(imported,true);', 'const imported=parseStrictJSON(await file.text());const full=importVortex(imported);vortex=full.v;setRecipe(full.base,true);syncVortex();if(full.preview){await window.volumeLab.setCamera(full.preview.camera);setTime(full.preview.time);}showNotice("已恢复涡旋参数、云、水面与相机时间。");')
s=s.replace('JSON.stringify(recipe,null,2)', 'JSON.stringify(exportVortex(),null,2)')
s=s.replace("showNotice(`配方 JSON 已生成，包含全部 ${fieldCount()} 个字段。`)","showNotice('完整作品JSON已导出，包含云、水面、涡旋和相机时间。')")
s=s.replace("lastRendered=true; updateExports();", "lastRendered=true; updateExports();$('timeline').value=Math.min(elapsed,90);$('phase').textContent=elapsed===0?'尚未形成':elapsed<vortex.duration*.35?'云帽 / 底部喷环形成':elapsed<vortex.duration?'漏斗逐渐连通':'成熟涡旋';")
s=s.replace("$('rendererStatus').textContent='共享 GLSL · WebGL2';setView('outside');", "$('rendererStatus').textContent='体积积分 · 水面高度场';setView('outside');")
s=s.replace("outside:{position:[cx+r*1.35,Math.max(50,top*.8),cz+r*1.45],target:[cx,middle,cz],label:'外部观察'},", "outside:{position:[cx+230,90,cz+330],target:[cx,85,cz],label:'外部观察'},")
s=s.replace("ground:{position:[cx+95,7,cz+115],target:[cx,25,cz],label:'贴近地面'},", "ground:{position:[cx+95,30,cz+150],target:[cx,40,cz],label:'近看喷环 / 水面'},")
s=s.replace("$('sceneCaption').textContent=endScene?'世界坐标 · 悬浮阶梯岛屿的真实命中深度 · 有限介质':'世界坐标 · 解析地面与实体盒子深度 · 有限区域';", "$('sceneCaption').textContent='世界坐标 · 三维密度 · 地形 / 波动水面实际深度';")
s=s.replace('getRecipe:()=>recipe?clone(recipe):null,', '''getRecipe:()=>recipe?clone(recipe):null,
  getWork:()=>exportVortex(),
  setVortex:async(candidate)=>{await initialization;applyVortex(candidate);},
  setMode:async(mode)=>{await initialization;document.querySelector(`[data-mode="${mode}"]`).click();},
  capture:()=>canvas.toDataURL('image/png'),''')
s=s.replace('results.exportEnabled=exportIds.every(id=>!$(id).disabled);', "results.exportEnabled=['jsonButton','pngButton'].every(id=>!$(id).disabled)&&$('zipButton').disabled;")
# Insert original water height-field and actual intersection before scene tracing.
water='''
vec3 sceneSky(vec3 rd);
float vortexWaterHeight(vec2 pos){
  vec2 p=pos-Bounds.xz;float r=length(p);float theta=atan(p.y,p.x);float t=Time;float gain=smoothstep(0.0,.4,vortexProgress());
  float swell=sin(p.x*.045+t*.7)*.5+sin(dot(p,vec2(.075,.042))-t*.9)*.27+sin(dot(p,vec2(-.16,.11))+t*1.3)*.14;
  float ringR=VortexRing.x;float inner=exp(-pow(r/max(ringR*.62,1.0),2.0));
  float rim=exp(-pow((r-ringR)/max(VortexRing.y,1.0),2.0));
  float spiral=sin(theta*3.0+r*.19-t*VortexFlow.x*3.0)*exp(-r/max(ringR*3.0,1.0));
  float wake=sin(r*.32-t*VortexWater.z)*exp(-r/max(ringR*4.0,1.0))*(1.0-exp(-r*.08));
  return Layer.x+VortexWater.y*swell+gain*(-VortexWater.x*inner+VortexWater.y*(spiral*.7+rim*.8+wake*.6));
}
SceneHit traceWater(vec3 ro,vec3 rd){
  SceneHit hit=SceneHit(10000.0,vec3(0,1,0),vec3(0));
  if(abs(rd.y)<1e-6)return hit;
  float range=VortexWater.x+VortexWater.y*3.2+.1;
  vec2 ts=(vec2(Layer.x-range,Layer.x+range)-ro.y)/rd.y;
  float lo=max(min(ts.x,ts.y),.001),hi=min(max(ts.x,ts.y),2500.0);if(hi<=lo)return hit;
  float prev=lo;vec3 a=ro+rd*prev;float fv=a.y-vortexWaterHeight(a.xz);
  for(int i=1;i<=28;i++){
    float next=mix(lo,hi,float(i)/28.0);vec3 b=ro+rd*next;float fn=b.y-vortexWaterHeight(b.xz);
    if(fv*fn<=0.0){
      float l=prev,u=next,fl=fv;
      for(int j=0;j<8;j++){float m=(l+u)*.5;vec3 p=ro+rd*m;float fm=p.y-vortexWaterHeight(p.xz);if(fl*fm<=0.0)u=m;else{l=m;fl=fm;}}
      float distance=(l+u)*.5;vec3 p=ro+rd*distance;float e=.18;
      float dx=(vortexWaterHeight(p.xz+vec2(e,0))-vortexWaterHeight(p.xz-vec2(e,0)))/(2.0*e);
      float dz=(vortexWaterHeight(p.xz+vec2(0,e))-vortexWaterHeight(p.xz-vec2(0,e)))/(2.0*e);
      vec3 n=normalize(vec3(-dx,1,-dz));vec2 local=p.xz-Bounds.xz;float r=length(local);float theta=atan(local.y,local.x);
      float foam=pow(.5+.5*sin(theta*3.0+r*.19-Time*VortexFlow.x*3.0),6.0)*exp(-pow((r-VortexRing.x*1.35)/max(VortexRing.x*.9,1.0),2.0));
      foam*=VortexWater.w*smoothstep(0.0,.4,vortexProgress());
      float fresnel=.02+.98*pow(1.0-max(dot(n,-rd),0.0),5.0);
      vec3 reflected=sceneSky(reflect(rd,n));float spec=pow(max(dot(reflect(-normalize(SunDirection),n),-rd),0.0),80.0);
      vec3 color=mix(vec3(.008,.085,.12),reflected,fresnel*.8)+SunColor*spec*.25;
      color=mix(color,vec3(.48,.61,.63),foam);
      return SceneHit(distance,n,color);
    }
    prev=next;fv=fn;
  }
  return hit;
}
'''
s=s.replace('SceneHit traceScene(vec3 ro,vec3 rd) {',water+'\nSceneHit traceScene(vec3 ro,vec3 rd) {\n    if(VortexLife.w>.5)return traceWater(ro,rd);')
s=s.replace('linearScene+=veSurfaceLight(world,hit.normal);', 'if(VortexLife.w>.5)linearScene=hit.color;\n        linearScene+=veSurfaceLight(world,hit.normal);')
p.write_text(s,encoding='utf-8')
html=(out/'index.html').read_text(encoding='utf-8').replace('<title>世界体积环境 · 参数工作台</title>','<title>龙卷风与龙吸水 · 体积工作台</title>').replace('<h1>世界体积环境</h1>','<h1 id="effectTitle">龙吸水 · 体积雾与水面</h1>').replace('有限区域中的云雾、散射、自发光与间歇局部光','云漏斗 · 旋转上升 · 喷雾环 · 波动水面')
html=html.replace('<div class="stage-controls">','''<div class="stage-controls"><div class="row wrap"><button data-mode="0">陆地龙卷风</button><button data-mode="1">龙吸水</button><button id="replay">重新形成</button><strong id="phase">尚未形成</strong></div><label class="help">形成时间线（秒）<input id="timeline" type="range" min="0" max="90" step=".1" value="0"></label>''')
html=html.replace('资源 ZIP</button>','游戏接入待适配</button>')
html=html.replace('<div id="parameterGroups"></div>', '<details open><summary>漏斗 · 运动 · 喷环 · 水面</summary><div id="vortexFields"></div></details><div id="parameterGroups"></div>')
html=html.replace('<footer><strong','<p class="help">水面是具有实际交点与法线的程序高度场；喷雾与云漏斗为三维密度。当前没有完整流体求解、场景折射或 Forge 接入。</p><p class="help"><a href="../tribulation/">渡劫黑云</a> · <a href="../">环境工作台</a> · <a href="vortex-workbench-source.zip">本页源码</a></p><footer><strong')
html=html.replace('src="preview.js"','src="preview.js?v=vortex1"')
(out/'index.html').write_text(html,encoding='utf-8')
print('Vortex UI / water / JSON ready')
