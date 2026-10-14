import {createEnvironmentRenderer} from './environment-preview.js';
import {FIELDS, DEFAULT, PRESETS, makeRecipe, validateRecipe, parseRecipe, uniformValues, PACKS, COLORS} from './recipe.js';

const $ = id => document.getElementById(id);
const STORAGE = 'cosmic-sky-workbench/1';
const qualityNames = {1:'轻量采样',2:'中等采样',3:'精细采样'};
const groupIcons = {'整体':'◈','大气':'◒','物理大气':'◉','雨雪':'❄','合成与性能':'▧','太阳':'☀','昼夜':'◷','月亮':'☾','薄云':'☁','恒星':'✦','银河':'⌁','星云':'⌁','极光':'≋','流星':'↗','天体':'◉','巨型天体':'◉','黑洞':'◎','色彩':'◐'};
const presetColors = ['#759bcf','#6adcbf','#ca779f','#587caf','#dbbd85','#4aa8bc','#f29b57'];
const presetEnglish = ['MILKY WAY','POLAR NIGHT','ROSE NEBULA','DEEP SPACE','METEOR NIGHT','CYAN CLOUD'];
const controls = new Map();
const groupDetails = new Map();
const skyPresetIds = new Set(PRESETS.filter(p=>p.recipe.parameters.atmosphereStrength>0).map(p=>p.id));
const fieldNotes = {
  atmosphereStrength:'设为 0 可回到宇宙背景，同时隐藏太阳、月亮与薄云。',
  sunElevation:'昼夜速度为 0 时直接调整太阳高度；轮转时作为太阳的起始位置。',
  cycleSpeed:'0 为手动太阳高度。大于 0 时日月随时间轮转；可暂停并拖动时间条观察。',
  moonPhase:'0° 满月，±90° 半月，±180° 新月。正负值决定亮面朝向。',
  thinCloudStrength:'设为 0 可关闭薄云。薄云随风流动，需要先开启大气。',
  volumeCloudStrength:'真正的世界坐标云体。移动观察位置或飞入云层可看到视差和密度变化。',
  fogStrength:'按场景深度和世界高度积分。打开地面测试场景，观察近远遮挡与雾层。',
  cloudShadowStrength:'用同一世界云密度沿太阳方向计算透射，调制可见地面。',
  weatherFollowWorld:'网页用下方雨雷模拟输入；游戏读取当前天气，不修改世界天气。',
  rainAmount:'控制雨与雪的总体降水强度。设为 0 可停止降水。',
  weatherSpeed:'设为 0 可冻结新雨雪的下落、飘摆与转动。',
  blackHoleStyle:'曲率积分透镜保留原模式；二维电影光环带金白亮环、弯曲光弧与横向吸积盘。',
  lensingStrength:'两种黑洞都能弯曲背景。二维金环模式会拉伸附近的星点、银河与星云，0 可关闭该透镜。',
  giantBodyStyle:'选择独立程序天体外观，并用原「天体」分组调整位置、颜色、相位和自转。',
  giantBodyScale:'乘以原天体角半径，让巨大天体占据更多天空。方向天体没有平移视差。',
  giantBodySeparation:'调整双星间距、暗盘偏移或破碎月球的碎片展开。',
  giantBodyAtmosphere:'调整行星边缘气层或恒星外焰；发光不等于照亮地形。',
  atmosphereStyle:'球形大气按真实高度与路径长度计算散射。解析美术大气保留原作品的色彩控制。',
  observerAltitude:'世界观察高度以海平面为参照叠加到此值。超过大气层时，可观察弯曲行星边缘。',
  groundLevel:'设置与世界海平面一致的 Y，决定相机高度如何换算为大气海拔。',
  rayleighDensity:'分子散射更偏向蓝色短波光，密度会改变蓝天和远处日光消光。',
  mieDensity:'控制悬浮微粒散射与太阳附近的亮晕，也影响低太阳角度的暖色。',
  ozoneDensity:'高空臭氧吸收改变日落时的色彩；不会额外发光。',
  precipitationMode:'按生物群系时，游戏读取实际气候与高度；网页用下方的演示气候预览雨雪。',
  precipitationRoof:'根据地表与遮盖高度限制雨雪，可移动到测试屋顶下比较。',
  nativeTransparentMode:'按透明层分段时，分别计算玻璃或水前后的雨雾。下方可切换透明测试场景。',
  environmentRenderScale:'降低比例可减少云雨雾开销，也可能改变细节与亮度。细小雪花会保持完整分辨率，实际比例见渲染状态。',
};
const groupNotes = {
  大气:'开启大气后，可调晴空、地平线与晚霞；日月和薄云也会参与天空。',
  昼夜:'轮转速度独立于云层流动。播放时间决定日月位置，归零即可回到起始天空。',
  月亮:'调整亮度、方向和月相，可以制作满月、半月或月牙。',
  薄云:'程序薄云覆盖远处天空。覆盖率控制多少，纹理尺度控制形状，风向控制移动方向。',
  物理大气:'球形气层包含分子、微粒与臭氧，太阳和相机移动会更新天空缓存。全部参数可用于自己的行星天空。',
  雨雪:'雨量同时控制降雪强度。雪花可调整大小、下落速度和飘摆；演示气候可用来比较雨、雪、干燥区域与高度变化。',
  合成与性能:'在玻璃或水的测试场景中比较合成模式，再选择适合画面与设备的环境渲染比例。',
};
let recipe = makeRecipe();
let selectedPreset = 'milkyway';
let playing = true;
let elapsed = 0;
let camera = {yaw:0,pitch:-22,fov:70};
let previewScale = 1;
let renderer;
let preview={position:[0,64,0],scene:false,gameWeather:[0,0],transparentScene:false,biomePreset:'mixed'};
let gl, program, uniforms = {}, shaderReady = false, glLost = false;
let needsRender = true, lastFrame = 0, fpsStart = 0, fpsFrames = 0, fps = 0;
let resizeObserver;

function status(message, type='ok', link) {
  $('status').className = `status${type==='ok'?'':` ${type}`}`;
  $('statusText').replaceChildren(document.createTextNode(message));
  if (link) {
    const a = document.createElement('a'); a.href = link.url; a.textContent = link.label || '下载';
    a.download = ''; a.style.marginLeft = '8px'; $('statusText').append(a);
  }
}
function formatValue(field, value) {
  if (field.integer) return String(value);
  const decimal = (String(field.step).split('.')[1] || '').length;
  return Number(value).toFixed(Math.min(decimal,3));
}
function fillRange(element) {
  const p = (Number(element.value)-Number(element.min))/(Number(element.max)-Number(element.min));
  element.style.setProperty('--range-fill', `${Math.max(0,Math.min(100,p*100))}%`);
}
function setParameter(key, value) {
  try {
    recipe = validateRecipe({schema:recipe.schema,parameters:{...recipe.parameters,[key]:value}});
    selectedPreset = null;
    $('sceneTitle').textContent = '我的天空';
    $('presetDescription').textContent = '正在创作自己的天空。全部参数会自动保存在此浏览器。';
    syncControls(); save(); needsRender = true;
  } catch (error) { status(error.message, 'error'); syncControls(); }
}
function makeParameter(field) {
  const displayLabel=field.key==='rainAmount'?'降水量（雨 / 雪）':field.label;
  const row = document.createElement('div'); row.className = 'parameter';
  const top = document.createElement('div'); top.className = 'parameter-top';
  const label = document.createElement('label'); label.textContent = displayLabel; label.htmlFor = `param-${field.key}`; top.append(label); row.append(top);
  if (field.color) {
    const wrap = document.createElement('div'); wrap.className = 'color-control';
    const input = document.createElement('input'); input.type = 'color'; input.id = `param-${field.key}`; input.className = 'color-input'; input.setAttribute('aria-label',displayLabel);
    const text = document.createElement('input'); text.type = 'text'; text.className = 'value-input color-text'; text.maxLength = 7; text.spellcheck = false; text.setAttribute('aria-label',`${displayLabel} HEX 色值`);
    input.addEventListener('input',()=>setParameter(field.key,input.value));
    text.addEventListener('change',()=>setParameter(field.key,text.value));
    wrap.append(input,text); row.append(wrap); controls.set(field.key,{input,text});
  } else if (field.options || field.key === 'quality') {
    const select = document.createElement('select'); select.id = `param-${field.key}`; select.className = 'quality-select';
    for (const [value,name] of Object.entries(field.options||qualityNames)) { const option = document.createElement('option'); option.value = value; option.textContent = `${value} · ${name}`; select.append(option); }
    select.addEventListener('change',()=>setParameter(field.key,Number(select.value))); row.append(select); controls.set(field.key,{input:select});
    if(field.key==='quality') {const note = document.createElement('p'); note.className = 'field-note'; note.textContent = '改变程序采样层数与细节。高档更细腻，也会增加 GPU 开销。'; row.append(note);}
  } else if (field.key === 'seed') {
    const wrap = document.createElement('div'); wrap.className = 'seed-row';
    const input = document.createElement('input'); input.type='number'; input.id=`param-${field.key}`; input.min=field.min; input.max=field.max; input.step=field.step; input.className='value-input';
    input.addEventListener('change',()=>setParameter(field.key,Number(input.value)));
    const random = document.createElement('button'); random.className='random-button'; random.textContent='↻ 换一片'; random.id='randomSeed'; random.title='随机一个天空种子';
    random.addEventListener('click',()=>{ const number = new Uint32Array(1); crypto.getRandomValues(number); setParameter(field.key,number[0]%10000); });
    wrap.append(input,random); row.append(wrap); controls.set(field.key,{input});
  } else {
    const number = document.createElement('input'); number.type='number'; number.min=field.min; number.max=field.max; number.step=field.step; number.className='value-input'; number.setAttribute('aria-label',`${displayLabel}数值`); top.append(number);
    const range = document.createElement('input'); range.type='range'; range.id=`param-${field.key}`; range.min=field.min; range.max=field.max; range.step=field.step;
    range.addEventListener('input',()=>setParameter(field.key,Number(range.value)));
    number.addEventListener('change',()=>setParameter(field.key,Number(number.value)));
    row.append(range); controls.set(field.key,{input:range,text:number,field});
  }
  if(fieldNotes[field.key]) {
    const note=document.createElement('p'); note.className='field-note'; note.textContent=fieldNotes[field.key];row.append(note);
  }
  if(['moonPhase','cycleSpeed'].includes(field.key)) {
    const state=document.createElement('p');state.id=`${field.key}State`;state.className='field-note field-state';row.append(state);
  }
  return row;
}
function createControls() {
  $('fieldCount').textContent=`${FIELDS.length} 个参数`;
  $('presetCount').textContent=`${PRESETS.length} 个起点`;
  const groups = [...new Set(FIELDS.map(f=>f.group))];
  for (const group of groups) {
    const details = document.createElement('details'); details.className='parameter-group'; details.dataset.group=group; details.open = ['整体','大气','太阳','薄云'].includes(group);
    const summary = document.createElement('summary');
    const title = document.createElement('span'); title.className='group-label';
    const icon = document.createElement('span'); icon.className='group-icon'; icon.textContent=groupIcons[group]||'✧'; icon.setAttribute('aria-hidden','true');
    title.append(icon,document.createTextNode(group));
    const count = document.createElement('span'); count.className='group-count'; count.textContent=String(FIELDS.filter(f=>f.group===group).length).padStart(2,'0');
    const arrow=document.createElement('span'); arrow.className='disclosure-arrow'; arrow.setAttribute('aria-hidden','true');
    summary.append(title,count,arrow); const wrap=document.createElement('div'); wrap.className='group-controls';
    if(groupNotes[group]){const note=document.createElement('p');note.className='group-note';note.textContent=groupNotes[group];wrap.append(note);}
    for (const field of FIELDS.filter(f=>f.group===group)) wrap.append(makeParameter(field));
    details.append(summary,wrap); $('parameterGroups').append(details); groupDetails.set(group,details);
    const option=document.createElement('option'); option.value=group; option.textContent=`${group} · ${FIELDS.filter(f=>f.group===group).length}`; $('parameterJump').append(option);
  }
  PRESETS.forEach((preset,i)=>{
    const button=document.createElement('button'); button.className='preset-card'; button.dataset.preset=preset.id; button.style.setProperty('--preset-glow',preset.color||presetColors[i]||'#8093c9');
    const strong=document.createElement('strong'); strong.textContent=preset.name;
    const small=document.createElement('small'); small.textContent=preset.english||presetEnglish[i]||preset.id.toUpperCase().replaceAll('-',' '); button.append(strong,small);
    button.addEventListener('click',()=>applyRecipe(preset.recipe,{preset:preset.id,message:`已切换为「${preset.name}」`})); $('presets').append(button);
  });
}
function focusGroups(preset) {
  const daytime=skyPresetIds.has(preset)||(!preset&&recipe.parameters.atmosphereStrength>0);
  const preferred=daytime ? ['整体','大气','太阳','薄云',...(preset==='daycycle'?['昼夜']:[]),...(['twilight','moonlit'].includes(preset)?['月亮']:[])] : ['整体','恒星','银河',...(preset==='blackhole'?['黑洞']:[])];
  for(const [group,details] of groupDetails) details.open=preferred.includes(group)||(preset==='cinematicblackhole'&&group==='黑洞')||(recipe.parameters.atmosphereStyle===2&&group==='物理大气')||(recipe.parameters.precipitationMode>0&&group==='雨雪')||(recipe.parameters.nativeTransparentMode>0&&group==='合成与性能')||(recipe.parameters.giantBodyStyle>0&&['巨型天体','天体','色彩'].includes(group))||(recipe.parameters.volumeCloudStrength>0&&['体积云','云影与天光'].includes(group))||(recipe.parameters.fogStrength>0&&group==='雾与空气透视')||(recipe.parameters.rainAmount>0&&['天气','雨幕'].includes(group))||(recipe.parameters.rainbowStrength>0&&group==='彩虹')||(recipe.parameters.haloStrength>0&&group==='冰晶晕')||(recipe.parameters.specialStrength>0&&group==='异界天空');
  $('parameterJump').value='整体';
}
function syncControls() {
  for (const field of FIELDS) {
    const value=recipe.parameters[field.key], ui=controls.get(field.key); ui.input.value=value;
    if (ui.text) ui.text.value=field.color?value.toUpperCase():formatValue(field,value);
    if (ui.input.type==='range') fillRange(ui.input);
  }
  for (const button of $('presets').children) {
    const active=button.dataset.preset===selectedPreset; button.classList.toggle('active',active); button.setAttribute('aria-pressed',String(active));
  }
  $('qualityBadge').textContent=qualityNames[recipe.parameters.quality];
  const p=recipe.parameters;
  $('layerHint').hidden=p.atmosphereStrength!==0;
  $('layerHint').textContent='当前大气已关闭，日月与薄云也隐藏。选择晴空等起点，或提高「大气启用与强度」即可开启。';
  if($('moonPhaseState')) {
    const phase=Math.abs(p.moonPhase),lit=Math.round((1+Math.cos(phase*Math.PI/180))*50);
    const name=phase<1?'满月':phase>179?'新月':Math.abs(phase-90)<1?'半月':phase>90?'月牙':'凸月';
    $('moonPhaseState').textContent=`当前：${name} · 亮面约 ${lit}%`;
  }
  if($('cycleSpeedState')) $('cycleSpeedState').textContent=p.cycleSpeed>0?`当前：约 ${(179.52/p.cycleSpeed).toFixed(0)} 秒完成一轮昼夜`:'当前：手动日月位置';
}
function save() {
  try { localStorage.setItem(STORAGE,JSON.stringify(recipe)); $('saveState').textContent='参数已自动保存'; }
  catch { $('saveState').textContent='浏览器无法保存；请导出配方'; }
}
function applyRecipe(input,{preset=null,message='配方已载入'}={}) {
  const valid=validateRecipe(input); recipe=valid; selectedPreset=preset;
  const source=PRESETS.find(p=>p.id===preset);
  $('sceneTitle').textContent=source?.name || '我的天空';
  $('presetDescription').textContent=source?.description || '自己的天空已载入。调整任意参数，继续创作。';
  applyPreviewPreset(source);focusGroups(preset);
  syncControls(); save(); needsRender=true; status(message); return getRecipe();
}
function getRecipe() { return structuredClone(recipe); }
function setTime(value) {
  if(typeof value!=='number'||!Number.isFinite(value)||value<0) throw new Error('时间必须是非负有限数字');
  elapsed=value; needsRender=true; syncTime(); return elapsed;
}
function syncTime() {
  const minutes=Math.floor(elapsed/60), seconds=(elapsed%60).toFixed(1).padStart(4,'0');
  $('timeOutput').textContent=`${String(minutes).padStart(2,'0')}:${seconds}`;
  $('timeline').max=String(Math.max(120,Math.ceil((elapsed+1)/120)*120)); $('timeline').value=String(elapsed); fillRange($('timeline'));
}
function setPlaying(value) {
  playing=Boolean(value); $('playIcon').textContent=playing?'Ⅱ':'▶';
  $('playPause').setAttribute('aria-label',playing?'暂停动态天空':'播放动态天空'); $('playPause').title=playing?'暂停动态天空':'播放动态天空';
  $('playbackState').textContent=playing?'正在播放':'已暂停';
  $('playbackState').classList.toggle('paused',!playing);
  needsRender=true;
}
function normalizeYaw(value){ return ((value+180)%360+360)%360-180; }
function setCamera(input) {
  for (const [key,min,max] of [['yaw',-180,180],['pitch',-89,89],['fov',30,110]]) {
    if(input[key]!==undefined) {
      if(typeof input[key]!=='number'||!Number.isFinite(input[key])) throw new Error('视角参数需要有限数字');
      camera[key]=key==='yaw'?normalizeYaw(input[key]):Math.max(min,Math.min(max,input[key]));
    }
  }
  syncCamera(); needsRender=true; return {...camera};
}
function syncCamera() {
  $('yaw').value=String(camera.yaw); $('pitch').value=String(-camera.pitch); $('fov').value=String(camera.fov);
  $('yawValue').textContent=`${Math.round(camera.yaw)}°`; $('pitchValue').textContent=`${Math.round(-camera.pitch)}°`; $('fovValue').textContent=`${Math.round(camera.fov)}°`;
  ['yaw','pitch','fov'].forEach(id=>fillRange($(id)));
  const heading=((camera.yaw+360)%360), directions=['北','东北','东','东南','南','西南','西','西北'];
  // Minecraft yaw 0 faces +Z (south), positive yaw turns towards west.
  const cardinal=directions[Math.round((heading+180)/45)%8];
  $('viewHud').textContent=`${cardinal} · ${camera.yaw.toFixed(0)}° · 仰角 ${(-camera.pitch).toFixed(0)}°`;
}
function shaderError(error) {
  $('loading').hidden=true; $('shaderError').hidden=false; $('shaderErrorText').textContent=String(error?.message || error);
  status('预览未启动。请查看画面中的错误信息。','error'); shaderReady=false;setPlaying(false);needsRender=false;
}
function compile(type,source) {
  const shader=gl.createShader(type); gl.shaderSource(shader,source); gl.compileShader(shader);
  if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS)) { const log=gl.getShaderInfoLog(shader); gl.deleteShader(shader); throw new Error(`着色器编译失败\n${log}`); }
  return shader;
}
async function initRenderer() {
  shaderReady=false;
  $('shaderError').hidden=true; $('loading').hidden=false;
  try {
    renderer?.dispose();renderer=null;
    const canvas=$('sky');
    gl=canvas.getContext('webgl2',{alpha:false,antialias:false,depth:false,stencil:false,preserveDrawingBuffer:true,powerPreference:'high-performance'});
    if(!gl) throw new Error('此浏览器或显卡未提供 WebGL 2。请开启浏览器硬件加速，或使用支持 WebGL 2 的浏览器。');
    renderer=await createEnvironmentRenderer(gl);shaderReady=true;glLost=false;resizeCanvas();
    if(!renderNow())return false;
    $('loading').hidden=true;$('gpuBadge').textContent='WebGL 2 · 实时';
    status('预览已就绪。拖动画面环顾天空，选择起点或调整参数即时查看变化。');
    return true;
  }catch(error){shaderError(error);return false;}
}
function resizeCanvas() {
  const canvas=$('sky'), box=canvas.getBoundingClientRect();
  const pixelRatio=Math.min(window.devicePixelRatio||1,1.5);
  let width=Math.max(1,Math.round(Math.min(1440,box.width*pixelRatio)*previewScale));
  let height=Math.max(1,Math.round(width*box.height/Math.max(1,box.width)));
  if(height>1080){width=Math.max(1,Math.round(width*1080/height));height=1080;}
  if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height;needsRender=true;}
}
function renderNow() {
  if(!shaderReady||glLost) return false;
  try{
  resizeCanvas(); const canvas=$('sky');
  renderer.render({values:uniformValues(recipe),parameters:recipe.parameters,camera,position:preview.position,scene:preview.scene,transparentScene:preview.transparentScene,biomePreset:preview.biomePreset,gameWeather:preview.gameWeather,time:elapsed,resolution:[canvas.width,canvas.height]}); needsRender=false;
  $('renderStats').textContent=`${canvas.width} × ${canvas.height} · ${!playing?'已暂停':fps?`${fps} FPS`:'实时渲染'} · ${qualityNames[recipe.parameters.quality]}`;
  syncTelemetry();
  return true;
  }catch(error){shaderError(error);return false;}
}
function syncTelemetry(){
  const t=renderer?.getTelemetry(),a=t?.atmosphere,p=recipe.parameters;
  const physical=p.atmosphereStyle===2&&p.atmosphereStrength>0;
  $('atmosphereStatus').textContent=p.atmosphereStrength===0?'宇宙背景':physical?(a?.ready&&a?.skyViewReady?'球形大气 · 缓存已就绪':a?.reason?'球形大气 · 解析回退':'球形大气 · 准备缓存'):'解析美术大气';
  $('atmosphereDetail').textContent=physical?(a?.reason||`观察海拔 ${Number(a?.observerHeightKm??p.observerAltitude).toFixed(3)} km · 透射与多次散射`):'保留原天空的颜色与散射控制';
  const ms=value=>Number.isFinite(value)?`${value.toFixed(3)} ms`:'等待计时';
  $('lutGpuTime').textContent=physical?ms(a?.gpuMs?.skyView):'无需造表';
  $('lutBuildState').textContent=physical&&a?.builds?`介质 ${a.builds.transmittance} 次 · 天空 ${a.builds.skyView} 次`:'太阳与视角变化即时预览';
  const sceneNames={glass:'玻璃测试场景',water:'水面测试场景'};
  $('environmentStatus').textContent=sceneNames[preview.transparentScene]||(t?.composite?'深度环境合成':preview.scene?'地面场景':'仅天空');
  $('environmentGpuTime').textContent=t?.composite||preview.transparentScene?ms(t?.gpuMs?.environment):'未启用环境层';
  const r=t?.environmentResolution;
  const resolution=Array.isArray(r)?r.join(' × '):r?.width&&r?.height?`${r.width} × ${r.height}`:null;
  const actual=t?.renderScale??p.environmentRenderScale,requested=t?.requestedRenderScale??p.environmentRenderScale;
  const scale=`实际 ${Math.round(actual*100)}%${actual!==requested?` · 目标 ${Math.round(requested*100)}%`:''}`;
  $('environmentDetail').textContent=[preview.transparentScene?(p.nativeTransparentMode?'按透明层分段':'最终场景合成'):null,resolution,scale,t?.scaleProtection,t?.timerSupported===false?'当前设备不提供 GPU 计时':t?.gpuMs?.upsample!==undefined?`重建 ${ms(t.gpuMs.upsample)}`:null].filter(Boolean).join(' · ');
}
function frame(timestamp) {
  requestAnimationFrame(frame);
  try{
  if(!lastFrame)lastFrame=timestamp;
  const dt=Math.min(.1,(timestamp-lastFrame)/1000);lastFrame=timestamp;
  if(document.hidden)return;
  if(playing&&shaderReady&&!glLost){elapsed+=dt;needsRender=true;syncTime();}
  if(needsRender){if(renderNow())fpsFrames++;}
  if(!fpsStart)fpsStart=timestamp;
  if(timestamp-fpsStart>700){fps=Math.round(fpsFrames*1000/(timestamp-fpsStart));fpsFrames=0;fpsStart=timestamp;if(!playing)$('renderStats').textContent=`${$('sky').width} × ${$('sky').height} · 已暂停 · ${qualityNames[recipe.parameters.quality]}`;if(shaderReady&&!glLost)syncTelemetry();}
  }catch(error){shaderError(error);}
}
function capture() {
  if(!shaderReady||glLost)throw new Error('预览未就绪，暂时无法保存图片');
  if(!renderNow())throw new Error('预览绘制失败，暂时无法保存图片');return $('sky').toDataURL('image/png');
}
function download(data,filename,type) {
  const url=typeof data==='string'&&data.startsWith('data:')?data:URL.createObjectURL(new Blob([data],{type}));
  const a=document.createElement('a'); a.href=url;a.download=filename;document.body.append(a);a.click();a.remove();
  if(!url.startsWith('data:'))setTimeout(()=>URL.revokeObjectURL(url),1000);
}
async function postApi(endpoint,button) {
  button.disabled=true; status(endpoint==='/api/resources'?'正在生成 Minecraft 资源包…':'正在保存配方…','busy');
  try {
    const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(recipe)});
    const contentType=response.headers.get('content-type')||'';
    const result=contentType.includes('application/json')?await response.json():{error:await response.text()};
    if(!response.ok||result.error)throw new Error(result.error||`HTTP ${response.status}`);
    if(endpoint==='/api/resources') {
      if(typeof result.url!=='string')throw new Error('资源生成成功，但服务未返回下载地址');
      status(`Minecraft 资源已生成${result.path?`：${result.path}`:''}`,'ok',{url:result.url,label:'下载资源包 ZIP'});
      const a=document.createElement('a');a.href=result.url;a.download='';document.body.append(a);a.click();a.remove();
    }else status(`配方已保存${result.path?`：${result.path}`:''}`,'ok',result.url?{url:result.url,label:'下载已保存配方'}:undefined);
  }catch(error){status(`操作失败：${error.message}。请确认通过 Python 启动器访问工作台。`,'error');}
  finally{button.disabled=false;}
}
function syncPreview(){
  $('previewScene').value=preview.transparentScene|| (preview.scene?'scene':'sky');$('demoBiome').value=preview.biomePreset;$('previewX').value=preview.position[0];$('previewHeight').value=preview.position[1];$('previewZ').value=preview.position[2];
  $('previewHeightValue').textContent=preview.position[1].toFixed(0);$('gameRain').value=preview.gameWeather[0];$('gameThunder').value=preview.gameWeather[1];
}
function setPreviewState(input){
  const next=structuredClone(preview);
  if(input.position!==undefined){if(!Array.isArray(input.position)||input.position.length!==3||input.position.some(v=>typeof v!=='number'||!Number.isFinite(v))||Math.abs(input.position[0])>8192||Math.abs(input.position[2])>8192||input.position[1]<-128||input.position[1]>1024)throw new Error('观察位置 X/Z 需在 ±8192 内，高度需在 −128 至 1024 内');next.position=[...input.position];}
  if(input.scene!==undefined){next.scene=Boolean(input.scene);if(!next.scene)next.transparentScene=false;}
  if(input.transparentScene!==undefined){const transparent=input.transparentScene===true?'glass':input.transparentScene;if(![false,'glass','water'].includes(transparent))throw new Error('透明测试场景需为 glass、water 或 false');next.transparentScene=transparent;if(transparent)next.scene=true;}
  if(input.biomePreset!==undefined){if(!['mixed','rain','snow','dry','altitude'].includes(input.biomePreset))throw new Error('演示气候需为 mixed、rain、snow、dry 或 altitude');next.biomePreset=input.biomePreset;}
  if(input.gameWeather!==undefined){if(!Array.isArray(input.gameWeather)||input.gameWeather.length!==2||input.gameWeather.some(v=>typeof v!=='number'||!Number.isFinite(v)||v<0||v>1))throw new Error('模拟雨雷需在0–1内');next.gameWeather=[...input.gameWeather];}
  preview=next;
  syncPreview();needsRender=true;return structuredClone(preview);
}
function applyPreviewPreset(source){
  const p=recipe.parameters,world=p.volumeCloudStrength>0||p.fogStrength>0||p.cloudShadowStrength>0||p.lightShaftStrength>0||p.rainAmount>0;
  setPreviewState({position:source?.preview?.position||[0,64,0],scene:source?.preview?.scene??world,transparentScene:source?.preview?.transparentScene??false,biomePreset:source?.preview?.biomePreset??'mixed',...(source?.preview?.gameWeather?{gameWeather:source.preview.gameWeather}:{})});
  if(source?.preview)setCamera({pitch:source.preview.pitch});
}
function movePreview(code,distance=8){const a=camera.yaw*Math.PI/180,x=-Math.sin(a),z=Math.cos(a),p=[...preview.position];
  if(code==='KeyW'){p[0]+=x*distance;p[2]+=z*distance;}if(code==='KeyS'){p[0]-=x*distance;p[2]-=z*distance;}
  if(code==='KeyA'){p[0]+=z*distance;p[2]-=x*distance;}if(code==='KeyD'){p[0]-=z*distance;p[2]+=x*distance;}
  if(code==='KeyQ')p[1]-=distance;if(code==='KeyE')p[1]+=distance;
  p[0]=Math.max(-8192,Math.min(8192,p[0]));p[1]=Math.max(-128,Math.min(1024,p[1]));p[2]=Math.max(-8192,Math.min(8192,p[2]));setPreviewState({position:p});
}
function bindEvents() {
  $('previewScene').addEventListener('change',()=>{const value=$('previewScene').value;setPreviewState({scene:value!=='sky',transparentScene:['glass','water'].includes(value)?value:false});});
  $('demoBiome').addEventListener('change',()=>setPreviewState({biomePreset:$('demoBiome').value}));
  for(const [id,index]of[['previewX',0],['previewHeight',1],['previewZ',2]])$(id).addEventListener(id==='previewHeight'?'input':'change',()=>{try{const p=[...preview.position];p[index]=Number($(id).value);setPreviewState({position:p});}catch(e){status(e.message,'error');}});
  for(const id of ['gameRain','gameThunder'])$(id).addEventListener('input',()=>setPreviewState({gameWeather:[Number($('gameRain').value),Number($('gameThunder').value)]}));
  for(const [id,code]of[['flyForward','KeyW'],['flyBack','KeyS'],['flyUp','KeyE'],['flyDown','KeyQ']])$(id).addEventListener('click',()=>movePreview(code,16));
  $('presetFilter').addEventListener('change',()=>{const family=$('presetFilter').value;for(const button of $('presets').children){const id=button.dataset.preset,p=PRESETS.find(v=>v.id===id).recipe.parameters;const category=p.blackHoleStyle===2?'cosmic':p.specialStrength>0?'special':p.volumeCloudStrength>0||p.fogStrength>0||p.rainAmount>0?'world':p.atmosphereStrength>0?'day':'cosmic';button.hidden=family==='physical'?p.atmosphereStyle!==2:family!=='all'&&family!==category;}});
  $('parameterJump').addEventListener('change',()=>{const details=groupDetails.get($('parameterJump').value);if(details){details.open=true;details.querySelector('summary').scrollIntoView({block:'start',behavior:'smooth'});}});
  $('reset').addEventListener('click',()=>applyRecipe(makeRecipe(),{preset:'milkyway',message:'已恢复默认星空。'}));
  $('playPause').addEventListener('click',()=>setPlaying(!playing));
  $('resetTime').addEventListener('click',()=>setTime(0));
  $('timeline').addEventListener('input',()=>{setPlaying(false);setTime(Number($('timeline').value));});
  $('yaw').addEventListener('input',()=>setCamera({yaw:Number($('yaw').value)}));
  $('pitch').addEventListener('input',()=>setCamera({pitch:-Number($('pitch').value)}));
  $('fov').addEventListener('input',()=>setCamera({fov:Number($('fov').value)}));
  $('previewScale').addEventListener('change',()=>{previewScale=Number($('previewScale').value);needsRender=true;});
  $('capture').addEventListener('click',()=>{try{download(capture(),`天空-${recipe.parameters.seed}-${elapsed.toFixed(1)}s.png`,'image/png');status('PNG 图片已生成，保存当前参数与时间下的天空画面。');}catch(error){status(error.message,'error');}});
  $('exportRecipe').addEventListener('click',()=>{download(JSON.stringify(recipe,null,2)+ '\n',`cosmic-sky-${recipe.parameters.seed}.json`,'application/json');status('配方已导出。导入这份 JSON 即可还原全部天空参数。');});
  $('saveRecipe').addEventListener('click',()=>postApi('/api/export',$('saveRecipe')));
  $('exportResources').addEventListener('click',()=>postApi('/api/resources',$('exportResources')));
  $('importRecipe').addEventListener('click',()=>$('importFile').click());
  $('importFile').addEventListener('change',async()=>{
    const file=$('importFile').files[0];if(!file)return;
    try{
      if(file.size>1024*1024)throw new Error('配方文件超过 1 MB');
      const text=await file.text(), input=JSON.parse(text), upgraded=['cosmic-sky/1','cosmic-sky/2','cosmic-sky/3','cosmic-sky/4','cosmic-sky/5'].includes(input?.schema);
      applyRecipe(parseRecipe(text,{upgradeLegacy:true}),{message:upgraded?`已导入并升级旧版配方 ${file.name}；${legacyUpgradeNotice(input.schema)}`:`已导入 ${file.name}`});
    }
    catch(error){status(`导入失败：${error.message}`,'error');}finally{$('importFile').value='';}
  });
  $('fullscreen').addEventListener('click',async()=>{try{if(document.fullscreenElement)await document.exitFullscreen();else await $('viewport').requestFullscreen();}catch(error){status(`无法全屏：${error.message}`,'error');}});
  let drag=null;const canvas=$('sky');
  canvas.addEventListener('pointerdown',event=>{if(event.button!==0)return;drag={x:event.clientX,y:event.clientY,yaw:camera.yaw,pitch:camera.pitch};canvas.setPointerCapture(event.pointerId);canvas.focus({preventScroll:true});});
  canvas.addEventListener('pointermove',event=>{if(!drag)return;setCamera({yaw:drag.yaw-(event.clientX-drag.x)*.2,pitch:drag.pitch+(event.clientY-drag.y)*.2});});
  for(const event of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(event,()=>{drag=null;});
  canvas.addEventListener('wheel',event=>{event.preventDefault();setCamera({fov:camera.fov+Math.sign(event.deltaY)*2});},{passive:false});
  canvas.addEventListener('keydown',event=>{
    if(['KeyW','KeyA','KeyS','KeyD','KeyQ','KeyE'].includes(event.code)){movePreview(event.code,event.shiftKey?32:8);event.preventDefault();return;}
    const delta=event.shiftKey?10:3;if(event.key==='ArrowLeft')setCamera({yaw:camera.yaw-delta});else if(event.key==='ArrowRight')setCamera({yaw:camera.yaw+delta});else if(event.key==='ArrowUp')setCamera({pitch:camera.pitch-delta});else if(event.key==='ArrowDown')setCamera({pitch:camera.pitch+delta});else if(event.code==='Space')setPlaying(!playing);else return;event.preventDefault();
  });
  canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();glLost=true;shaderReady=false;status('显卡上下文已中断，等待浏览器恢复…','error');});
  canvas.addEventListener('webglcontextrestored',()=>initRenderer());
  window.addEventListener('resize',()=>{needsRender=true;});
  if('ResizeObserver'in window){resizeObserver=new ResizeObserver(()=>{needsRender=true;});resizeObserver.observe($('viewport'));}
  document.addEventListener('visibilitychange',()=>{lastFrame=0;fpsStart=0;fpsFrames=0;needsRender=true;});
}

createControls(); bindEvents(); syncCamera(); syncTime();
let restoreWarning=null,restoreNotice=null,restoreMigration=false;
function legacyUpgradeNotice(schema){
  if(schema==='cosmic-sky/5')return '保留原球形大气、气候雨雪与透明合成；新的二维巨型天体可按需启用。';
  if(schema==='cosmic-sky/4')return '保留原美术大气、雨幕与已设置的云雾；球形大气、气候雨雪和透明分层可按需开启。';
  if(schema==='cosmic-sky/3')return '保留原大气与薄云，新增天气和世界环境仍使用默认设置。';
  if(schema==='cosmic-sky/1')return '保留原星空，新增天体、大气和世界环境仍使用默认设置。';
  return '保留原星空与天体，新增大气和世界环境仍使用默认设置。';
}
try {
  const stored=localStorage.getItem(STORAGE);
  if(stored){
    const oldSchema=JSON.parse(stored)?.schema, upgraded=['cosmic-sky/1','cosmic-sky/2','cosmic-sky/3','cosmic-sky/4','cosmic-sky/5'].includes(oldSchema);
    recipe=parseRecipe(stored,{upgradeLegacy:true});selectedPreset=null;$('sceneTitle').textContent='我的天空';$('presetDescription').textContent='已恢复上次创作的参数。';
    if(upgraded){restoreNotice=`已升级浏览器中的旧版配方；${legacyUpgradeNotice(oldSchema)}`;restoreMigration=true;}
  }
  else $('presetDescription').textContent=PRESETS[0].description;
}catch(error){restoreWarning=`保存的配方无效，已使用默认星空：${error.message}`;recipe=makeRecipe();}
const requestedPreset=new URLSearchParams(window.location.search).get('preset');
if(requestedPreset!==null){
  const chosen=PRESETS.find(p=>p.id===requestedPreset);
  if(chosen){recipe=validateRecipe(chosen.recipe);selectedPreset=chosen.id;$('sceneTitle').textContent=chosen.name;$('presetDescription').textContent=chosen.description;restoreNotice=`已按链接打开「${chosen.name}」，动态预览自动播放。`;restoreWarning=null;save();}
  else restoreWarning=`链接中的预设「${requestedPreset}」不存在，已保留当前作品。`;
}
applyPreviewPreset(PRESETS.find(p=>p.id===selectedPreset));focusGroups(selectedPreset);syncControls();setPlaying(true);
if(restoreMigration)save();
window.skyWorkbench={getRecipe,setRecipe:input=>applyRecipe(input),setTime,getTime:()=>elapsed,setPlaying,isPlaying:()=>playing,renderNow,capture,getCamera:()=>({...camera}),setCamera,getResolution:()=>({width:$('sky').width,height:$('sky').height}),isReady:()=>shaderReady&&!glLost,getUniformValues:()=>uniformValues(recipe),getPreviewState:()=>structuredClone(preview),setPreviewState,getEnvironmentTelemetry:()=>renderer?.getTelemetry()};
initRenderer().then(ready=>{if(!ready)return;if(restoreWarning)status(restoreWarning,'error');else if(restoreNotice)status(restoreNotice);});requestAnimationFrame(frame);
