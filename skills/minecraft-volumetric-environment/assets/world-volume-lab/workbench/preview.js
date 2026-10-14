/* Original world-volume workbench. No external packages or image textures. */
const $ = id => document.getElementById(id);
const clone = value => JSON.parse(JSON.stringify(value));
let schema, contract, presets, recipe;
let legacyFieldNames=[], newFieldNames=[];
let gl, program, vao, canvas, ready = false, failed = false, lastRendered = false;
let paused = false, elapsed = 0, previousFrame = 0, lastPaint = 0, frameMs = 0;
let renderScale = 0.8, lastError = null, cameraView = 'outside';
const camera = {position: [230, 118, 260], yaw: -2.42, pitch: -0.17};
const keys = new Set();
const fieldsUI = new Map(), uniformLocations = new Map();
const pendingValues = new Map();
const rememberedGains = {emission: 2, mediumGain: 1, surfaceGain: 0.5};
const exportIds = ['jsonButton', 'pngButton', 'zipButton'];
const endUniformNames = new Set(['EndWarp','EndCarve','EndDepth']);
const fieldCount = () => Object.keys(schema.fields).length;

const vertexSource = `#version 300 es
precision highp float;
const vec2 vertices[3] = vec2[3](vec2(-1.,-1.),vec2(3.,-1.),vec2(-1.,3.));
void main(){gl_Position=vec4(vertices[gl_VertexID],0.,1.);}`;

const sceneSource = `
uniform vec2 Resolution;
uniform vec3 CameraPosition;
uniform vec3 CameraForward;
uniform vec3 CameraRight;
uniform vec3 CameraUp;
out vec4 fragColor;
struct SceneHit { float distance; vec3 normal; vec3 color; };
void sceneBox(vec3 ro, vec3 rd, vec3 low, vec3 high, vec3 color, inout SceneHit hit) {
    vec3 safeDir = mix(vec3(0.000001), rd, greaterThan(abs(rd),vec3(0.000001)));
    vec3 a=(low-ro)/safeDir, b=(high-ro)/safeDir;
    vec3 nearV=min(a,b), farV=max(a,b);
    float tn=max(max(nearV.x,nearV.y),nearV.z);
    float tf=min(min(farV.x,farV.y),farV.z);
    if(tf<max(tn,0.0)) return;
    float t=tn>0.001?tn:tf;
    if(t<0.001 || t>=hit.distance) return;
    vec3 p=ro+rd*t, center=(low+high)*0.5, halfSize=(high-low)*0.5;
    vec3 boundary=abs((p-center)/halfSize);
    vec3 n=vec3(0.0);
    if(boundary.x>boundary.y && boundary.x>boundary.z) n.x=sign(p.x-center.x);
    else if(boundary.y>boundary.z) n.y=sign(p.y-center.y);
    else n.z=sign(p.z-center.z);
    hit=SceneHit(t,n,color);
}
void sceneIsland(vec3 ro,vec3 rd,vec3 center,vec2 size,inout SceneHit hit) {
    vec3 sand=vec3(0.39,0.37,0.235);
    sceneBox(ro,rd,center+vec3(-size.x,-8.0,-size.y),center+vec3(size.x,0.0,size.y),sand,hit);
    sceneBox(ro,rd,center+vec3(-size.x*0.76,-23.0,-size.y*0.74),center+vec3(size.x*0.76,-8.0,size.y*0.74),sand*0.89,hit);
    sceneBox(ro,rd,center+vec3(-size.x*0.49,-38.0,-size.y*0.46),center+vec3(size.x*0.49,-23.0,size.y*0.46),sand*0.81,hit);
    sceneBox(ro,rd,center+vec3(-size.x*0.20,-49.0,-size.y*0.18),center+vec3(size.x*0.20,-38.0,size.y*0.18),sand*0.73,hit);
}
SceneHit traceEndScene(vec3 ro,vec3 rd) {
    SceneHit hit=SceneHit(10000.0,vec3(0.0,1.0,0.0),vec3(0.0));
    vec3 offset=vec3(Bounds.x,0.0,Bounds.z);
    sceneIsland(ro,rd,offset+vec3(0.0,65.0,0.0),vec2(57.0,48.0),hit);
    sceneIsland(ro,rd,offset+vec3(-145.0,88.0,-135.0),vec2(34.0,25.0),hit);
    sceneIsland(ro,rd,offset+vec3(162.0,44.0,-92.0),vec2(44.0,31.0),hit);
    sceneIsland(ro,rd,offset+vec3(-172.0,52.0,175.0),vec2(31.0,25.0),hit);
    sceneIsland(ro,rd,offset+vec3(118.0,100.0,165.0),vec2(27.0,23.0),hit);
    sceneBox(ro,rd,offset+vec3(-38.0,65.0,-23.0),offset+vec3(-31.0,142.0,-16.0),vec3(0.06,0.052,0.07),hit);
    sceneBox(ro,rd,offset+vec3(29.0,65.0,-12.0),offset+vec3(36.0,118.0,-5.0),vec3(0.06,0.052,0.07),hit);
    return hit;
}
SceneHit traceScene(vec3 ro,vec3 rd) {
    if(EndWarp.x>0.5)return traceEndScene(ro,rd);
    SceneHit hit=SceneHit(10000.0,vec3(0.0,1.0,0.0),vec3(0.0));
    if(abs(rd.y)>0.000001) {
        float groundT=-ro.y/rd.y;
        if(groundT>0.001 && groundT<hit.distance) {
            vec3 p=ro+rd*groundT;
            float check=mod(floor((p.x-Bounds.x)/16.0)+floor((p.z-Bounds.z)/16.0),2.0);
            vec3 grass=mix(vec3(0.075,0.12,0.105),vec3(0.10,0.15,0.12),check);
            float grid=1.0-smoothstep(0.0,0.12,min(abs(fract(p.x/16.0+0.5)-0.5),abs(fract(p.z/16.0+0.5)-0.5)));
            hit=SceneHit(groundT,vec3(0.0,1.0,0.0),grass*(1.0+grid*0.16));
        }
    }
    vec3 offset=vec3(Bounds.x,0.0,Bounds.z);
    sceneBox(ro,rd,offset+vec3(-75.,0.,-44.),offset+vec3(-27.,19.,12.),vec3(0.23,0.255,0.27),hit);
    sceneBox(ro,rd,offset+vec3(-66.,19.,-35.),offset+vec3(-40.,43.,-4.),vec3(0.26,0.285,0.30),hit);
    sceneBox(ro,rd,offset+vec3(20.,0.,-45.),offset+vec3(58.,9.,-4.),vec3(0.19,0.225,0.22),hit);
    sceneBox(ro,rd,offset+vec3(36.,9.,-36.),offset+vec3(47.,76.,-25.),vec3(0.29,0.305,0.32),hit);
    sceneBox(ro,rd,offset+vec3(32.,76.,-40.),offset+vec3(51.,81.,-21.),vec3(0.33,0.34,0.35),hit);
    sceneBox(ro,rd,offset+vec3(-5.,0.,40.),offset+vec3(12.,28.,62.),vec3(0.25,0.22,0.19),hit);
    sceneBox(ro,rd,offset+vec3(77.,0.,30.),offset+vec3(105.,52.,58.),vec3(0.225,0.26,0.285),hit);
    return hit;
}
vec3 sceneSky(vec3 rd) {
    if(EndWarp.x>0.5){
        float h=clamp(rd.y*0.7+0.3,0.0,1.0);
        return mix(vec3(0.013,0.008,0.026),vec3(0.0025,0.0015,0.007),h);
    }
    float h=clamp(rd.y*0.65+0.35,0.0,1.0);
    vec3 sky=mix(vec3(0.17,0.235,0.34),vec3(0.035,0.105,0.24),h);
    vec3 sun=normalize(SunDirection);
    float disc=smoothstep(0.9986,0.9997,dot(rd,sun));
    float haze=pow(max(dot(rd,sun),0.0),24.0);
    return sky+SunColor*(disc*4.0+haze*0.06)*min(Optics.w,2.0);
}
vec3 tonemap(vec3 x) {
    x=max(x,vec3(0.0));
    vec3 mapped=(x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14);
    return pow(clamp(mapped,0.0,1.0),vec3(1.0/2.2));
}
void main() {
    vec2 uv=(gl_FragCoord.xy/Resolution*2.0-1.0);
    uv.x*=Resolution.x/Resolution.y;
    vec3 ray=normalize(CameraForward+(CameraRight*uv.x+CameraUp*uv.y)*0.52);
    SceneHit hit=traceScene(CameraPosition,ray);
    vec3 linearScene=sceneSky(ray);
    if(hit.distance<9999.0) {
        vec3 world=CameraPosition+ray*hit.distance;
        float diffuse=max(dot(hit.normal,normalize(SunDirection)),0.0);
        linearScene=hit.color*(vec3(0.19)+SunColor*diffuse*min(Optics.w,3.0)*0.65);
        linearScene+=veSurfaceLight(world,hit.normal);
    }
    vec4 volume=veIntegrate(CameraPosition,ray,hit.distance);
    vec3 result=linearScene*clamp(volume.a,0.0,1.0)+volume.rgb;
    fragColor=vec4(tonemap(result),1.0);
}`;

function displayError(message, fatal = false) {
  lastError = String(message);
  const node = $(fatal ? 'fatalError' : 'recipeError');
  node.textContent = lastError; node.hidden = false;
  if (fatal) { failed = true; lastRendered = false; $('rendererStatus').textContent = '渲染失败'; $('rendererStatus').classList.add('bad'); }
  updateExports();
}
function clearRecipeError() { $('recipeError').hidden = true; if (!failed) lastError = null; updateExports(); }
function showNotice(message) { $('notice').textContent = message; $('notice').hidden = false; }
function updateExports() { for (const id of exportIds) $(id).disabled = !ready || failed || !lastRendered || !$('recipeError').hidden; }

function parseStrictJSON(source) {
  // JSON.parse verifies syntax; this second pass rejects duplicated object keys.
  source=source.replace(/^\uFEFF/,'');
  const parsed=JSON.parse(source);let cursor=0;
  const whitespace=()=>{while(/\s/.test(source[cursor]||'')&&cursor<source.length)cursor++;};
  function readString(){
    const start=cursor++;
    while(cursor<source.length){if(source[cursor]==='\\'){cursor+=2;continue;}if(source[cursor++]==='"')break;}
    return JSON.parse(source.slice(start,cursor));
  }
  function readValue(){
    whitespace();const token=source[cursor];
    if(token==='"'){readString();return;}
    if(token==='{'){
      cursor++;whitespace();const keys=new Set();
      if(source[cursor]==='}'){cursor++;return;}
      while(cursor<source.length){
        whitespace();const key=readString();
        if(keys.has(key))throw new Error(`重复 JSON 字段：${key}`);keys.add(key);
        whitespace();cursor++;readValue();whitespace();
        if(source[cursor++]==='}')break;
      }
      return;
    }
    if(token==='['){
      cursor++;whitespace();if(source[cursor]===']'){cursor++;return;}
      while(cursor<source.length){readValue();whitespace();if(source[cursor++]===']')break;}
      return;
    }
    while(cursor<source.length&&!/[\s,}\]]/.test(source[cursor]))cursor++;
  }
  readValue();return parsed;
}

function validateRecipe(candidate,expectedSchema=schema.schema,expectedFields=Object.keys(schema.fields)) {
  if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) throw new Error('配方必须是 JSON 对象。');
  const outer = Object.keys(candidate);
  const unknown = outer.filter(k => !['schema', 'name', 'parameters'].includes(k));
  if (unknown.length) throw new Error(`未知顶层字段：${unknown.join('、')}`);
  if (candidate.schema !== expectedSchema) throw new Error(`schema 必须严格为 ${expectedSchema}`);
  if ('name' in candidate && (typeof candidate.name !== 'string' || [...candidate.name].length > 120)) throw new Error('name 必须是长度不超过 120 的字符串。');
  const params = candidate.parameters;
  if (!params || typeof params !== 'object' || Array.isArray(params)) throw new Error('缺少 parameters 对象。');
  const expected = expectedFields, actual = Object.keys(params);
  const missing = expected.filter(k => !Object.hasOwn(params,k));
  const allowed = new Set(expected);
  const extra = actual.filter(k => !allowed.has(k));
  if (missing.length || extra.length) throw new Error([missing.length ? `缺少字段：${missing.join('、')}` : '', extra.length ? `未知字段：${extra.join('、')}` : ''].filter(Boolean).join('\n'));
  for (const key of expected) {
    const spec=schema.fields[key];
    const value = params[key];
    if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${key} 必须是有限数值，不能使用字符串。`);
    if (spec.type === 'integer' && !Number.isInteger(value)) throw new Error(`${key} 必须是整数。`);
    if (value < spec.min || value > spec.max) throw new Error(`${key} 超出范围 [${spec.min}, ${spec.max}]。`);
  }
  if (params.shape === 2 && params.innerRadius >= params.radius) throw new Error('环形区域的 innerRadius 必须小于 radius。');
  if (params.flashDuration > params.flashPeriod) throw new Error('flashDuration 不能大于 flashPeriod。');
  if (Math.hypot(params.sunX,params.sunY,params.sunZ) <= 1e-6) throw new Error('太阳方向长度必须大于 0.000001。');
  return clone(candidate);
}
function upgradeLegacyRecipe(candidate) {
  const legacy=validateRecipe(candidate,'world-volume/1',legacyFieldNames);
  const parameters={...legacy.parameters};
  for(const key of newFieldNames)parameters[key]=schema.fields[key].default;
  parameters.densityModel=0;
  return validateRecipe({...legacy,schema:schema.schema,parameters});
}
function acceptRecipe(candidate) {
  return candidate?.schema==='world-volume/1'?upgradeLegacyRecipe(candidate):validateRecipe(candidate);
}
function upgradeNotice() { return `已明确将 world-volume/1 升级到 ${schema.schema}：原 50 个字段完整保留，新字段取默认值，densityModel=0。`; }

function compileShader(type, source) {
  const shader = gl.createShader(type); gl.shaderSource(shader, source); gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(shader); gl.deleteShader(shader);
    throw new Error(`着色器编译失败\n${log}`);
  }
  return shader;
}
function createProgram(core) {
  const vs = compileShader(gl.VERTEX_SHADER, vertexSource);
  const fs = compileShader(gl.FRAGMENT_SHADER, `#version 300 es\nprecision highp float;\nprecision highp int;\n${core}\n${sceneSource}`);
  const linked = gl.createProgram(); gl.attachShader(linked,vs); gl.attachShader(linked,fs); gl.linkProgram(linked);
  gl.deleteShader(vs); gl.deleteShader(fs);
  if (!gl.getProgramParameter(linked,gl.LINK_STATUS)) { const error = gl.getProgramInfoLog(linked); gl.deleteProgram(linked); throw new Error(`着色器链接失败\n${error}`); }
  return linked;
}
function loc(name) { if (!uniformLocations.has(name)) uniformLocations.set(name,gl.getUniformLocation(program,name)); return uniformLocations.get(name); }
function forwardVector() { const c=Math.cos(camera.pitch); return [Math.sin(camera.yaw)*c,Math.sin(camera.pitch),Math.cos(camera.yaw)*c]; }
function cross(a,b) { return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]; }
function normalize(v) { const length=Math.hypot(...v)||1; return v.map(n=>n/length); }
function uploadUniforms() {
  gl.uniform1f(loc('Time'),Math.max(0,elapsed));
  for (const [name, fieldNames] of Object.entries(contract.uniforms)) {
    const values=fieldNames.map(key=>recipe.parameters[key]);
    if (name==='ViewSteps' || name==='LightSteps') gl.uniform1i(loc(name),values[0]);
    else if (values.length===1) gl.uniform1f(loc(name),values[0]);
    else if (values.length===3) gl.uniform3fv(loc(name),values);
    else if (values.length===4) gl.uniform4fv(loc(name),values);
  }
  const forward=forwardVector(), right=normalize(cross(forward,[0,1,0])), up=normalize(cross(right,forward));
  gl.uniform2f(loc('Resolution'),canvas.width,canvas.height);
  gl.uniform3fv(loc('CameraPosition'),camera.position); gl.uniform3fv(loc('CameraForward'),forward);
  gl.uniform3fv(loc('CameraRight'),right); gl.uniform3fv(loc('CameraUp'),up);
}
function resizeCanvas() {
  const bounds=canvas.getBoundingClientRect();
  const ratio=Math.min(1,960/Math.max(1,bounds.width),640/Math.max(1,bounds.height))*renderScale;
  const width=Math.max(16,Math.round(bounds.width*ratio)),height=Math.max(16,Math.round(bounds.height*ratio));
  if (canvas.width!==width || canvas.height!==height) { canvas.width=width;canvas.height=height; }
}
function draw() {
  if (!ready || failed) return false;
  try {
    resizeCanvas(); gl.viewport(0,0,canvas.width,canvas.height); gl.useProgram(program); gl.bindVertexArray(vao);
    uploadUniforms(); gl.drawArrays(gl.TRIANGLES,0,3);
    const error=gl.getError();
    if (error!==gl.NO_ERROR) throw new Error(`WebGL 渲染错误：0x${error.toString(16)}`);
    lastRendered=true; updateExports();
    return true;
  } catch(error) { displayError(error.message,true); return false; }
}
function syncCameraInputs() { ['cameraX','cameraY','cameraZ'].forEach((id,i)=>{ if (document.activeElement!==$(id)) $(id).value=camera.position[i].toFixed(1); }); }
function syncScenePresentation() {
  const endScene=recipe.parameters.densityModel>0;
  const labels=endScene?{outside:'末地内景',inside:'穿行云墙',ground:'岛屿近景',above:'俯瞰浮岛'}:{outside:'外部观察',inside:'进入云雾',ground:'贴近地面',above:'俯瞰区域'};
  $('viewLabel').textContent=labels[cameraView]||labels.outside;
  $('sceneCaption').textContent=endScene?'世界坐标 · 悬浮阶梯岛屿的真实命中深度 · 有限介质':'世界坐标 · 解析地面与实体盒子深度 · 有限区域';
  canvas.setAttribute('aria-label',endScene?'可拖动旋转的三维云雾浮岛预览':'可拖动旋转的三维云雾地形预览');
  document.querySelectorAll('[data-view]').forEach(button=>{button.textContent=labels[button.dataset.view];button.classList.toggle('active',button.dataset.view===cameraView);});
}
function setView(view) {
  cameraView=view;
  const p=recipe.parameters,cx=p.centerX,cz=p.centerZ,r=p.radius;
  const top=p.shape===1?p.centerY+r:p.bottom+p.height;
  const middle=p.shape===1?p.centerY:p.bottom+p.height*.45;
  const endScene=p.densityModel>0;
  const views=endScene?{
    outside:{position:[cx+200,120,cz+240],target:[cx,72,cz],label:'末地内景'},
    inside:{position:[cx+70,105,cz+85],target:[cx-90,95,cz-145],label:'穿行云墙'},
    ground:{position:[cx+95,78,cz+115],target:[cx,65,cz],label:'岛屿近景'},
    above:{position:[cx+170,240,cz+200],target:[cx,65,cz],label:'俯瞰浮岛'},
  }:{
    outside:{position:[cx+r*1.35,Math.max(50,top*.8),cz+r*1.45],target:[cx,middle,cz],label:'外部观察'},
    inside:{position:[cx-r*.2,middle,cz+r*.18],target:[cx+r*.4,middle+4,cz-r*.6],label:'进入云雾'},
    ground:{position:[cx+95,7,cz+115],target:[cx,25,cz],label:'贴近地面'},
    above:{position:[cx+r*.35,top+r*.7,cz+r*.45],target:[cx,middle,cz],label:'俯瞰区域'},
  };
  const v=views[view]||views.outside; camera.position=v.position;
  const direction=normalize(v.target.map((x,i)=>x-v.position[i]));
  camera.yaw=Math.atan2(direction[0],direction[2]); camera.pitch=Math.asin(direction[1]);
  syncScenePresentation();
  syncCameraInputs(); draw();
}
function syncToggles() {
  for (const [id,key] of [['emissionToggle','emission'],['mediumToggle','mediumGain'],['surfaceToggle','surfaceGain']]) {
    $(id).checked=recipe.parameters[key]>0;
    if (recipe.parameters[key]>0) rememberedGains[key]=recipe.parameters[key];
  }
}
function syncFields(preserveActive=false) {
  for (const [key,inputs] of fieldsUI) {
    const value=recipe.parameters[key];
    if(!preserveActive||document.activeElement!==inputs.number)inputs.number.value=value;
    inputs.range.value=value;inputs.row.classList.remove('invalid');
  }
  $('recipeName').value=recipe.name||''; syncToggles();syncScenePresentation();
}
function changeField(key,value) {
  pendingValues.set(key,value);
  const candidate=clone(recipe);
  for(const [pendingKey,pendingValue] of pendingValues)candidate.parameters[pendingKey]=pendingValue;
  try {
    const oldModel=recipe.parameters.densityModel;
    recipe=validateRecipe(candidate);pendingValues.clear();clearRecipeError();syncFields(true);
    if(key==='densityModel'&&value!==oldModel)setView(cameraView);else draw();
  } catch(error) { fieldsUI.get(key).row.classList.add('invalid');displayError(error.message); }
}
function buildFields() {
  const groups=new Map();
  for (const [key,spec] of Object.entries(schema.fields)) {
    if (!groups.has(spec.group)) {
      const detail=document.createElement('details');detail.dataset.group=spec.group;detail.open=['区域','密度'].includes(spec.group)||(spec.group==='末地分布'&&recipe.parameters.densityModel>0);
      const summary=document.createElement('summary');summary.textContent=spec.group;detail.append(summary);$('parameterGroups').append(detail);groups.set(spec.group,detail);
    }
    const row=document.createElement('div');row.className='field';
    const label=document.createElement('label');label.htmlFor=`field-${key}`;label.textContent=spec.label;
    const number=document.createElement('input');number.id=`field-${key}`;number.type='number';number.min=spec.min;number.max=spec.max;number.step=spec.step;number.dataset.field=key;number.title=`${key} · ${spec.min} 至 ${spec.max}`;
    const range=document.createElement('input');range.type='range';range.min=spec.min;range.max=spec.max;range.step=spec.step;range.setAttribute('aria-label',spec.label);
    if (key==='centerX'||key==='centerZ') { range.min=-400;range.max=400; }
    if (key==='centerY'||key==='bottom') { range.min=-100;range.max=300; }
    row.append(label,number,range);groups.get(spec.group).append(row);fieldsUI.set(key,{number,range,row});
    const editNumber=()=>changeField(key,number.value===''?NaN:Number(number.value));
    number.addEventListener('input',editNumber);number.addEventListener('change',editNumber);
    range.addEventListener('input',()=>changeField(key,Number(range.value)));
  }
}
function setRecipe(candidate,resetCamera=false) {
  const legacy=candidate?.schema==='world-volume/1';
  recipe=acceptRecipe(candidate);pendingValues.clear();clearRecipeError();syncFields();
  if(recipe.parameters.densityModel>0)document.querySelector('details[data-group="末地分布"]').open=true;
  const matched=presets.find(p=>p.name===recipe.name && JSON.stringify(p.parameters)===JSON.stringify(recipe.parameters));
  $('presetSelect').value=matched?.id||'custom';
  if (resetCamera) setView(cameraView); else draw();
  if(legacy)showNotice(upgradeNotice());
  return clone(recipe);
}
function saveBlob(blob,name) {
  const link=document.createElement('a'),url=URL.createObjectURL(blob);link.href=url;link.download=name;document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
function filename(extension) { const safe=(recipe.name||'world-volume').replace(/[<>:"/\\|?*\u0000-\u001f]/g,'_');return `${safe}.${extension}`; }
function checkExport() {
  if (!ready||failed||!lastRendered) throw new Error('尚未成功渲染，禁止导出。');
  if (!$('recipeError').hidden) throw new Error('请先修正无效字段，再导出。');
  validateRecipe(recipe);
}
function bindEvents() {
  $('presetSelect').addEventListener('change',()=>{
    const preset=presets.find(p=>p.id===$('presetSelect').value);
    if(preset) setRecipe({schema:preset.schema,name:preset.name,parameters:preset.parameters},true);
  });
  $('recipeName').addEventListener('input',()=>{recipe.name=$('recipeName').value;});
  $('pauseButton').addEventListener('click',()=>{paused=!paused;syncPause();});
  $('setTimeButton').addEventListener('click',()=>setTime(Number($('timeInput').value)));
  $('zeroTimeButton').addEventListener('click',()=>setTime(0));
  $('resolutionSelect').addEventListener('change',()=>{renderScale=Number($('resolutionSelect').value);draw();});
  document.querySelectorAll('[data-view]').forEach(button=>button.addEventListener('click',()=>setView(button.dataset.view)));
  $('resetCameraButton').addEventListener('click',()=>setView(cameraView));
  ['cameraX','cameraY','cameraZ'].forEach((id,i)=>$(id).addEventListener('input',()=>{
    if($(id).value==='')return;
    const value=Number($(id).value);if(Number.isFinite(value)){camera.position[i]=value;draw();}else syncCameraInputs();
  }));
  for (const [id,key] of [['emissionToggle','emission'],['mediumToggle','mediumGain'],['surfaceToggle','surfaceGain']]) {
    $(id).addEventListener('change',()=>{
      if ($(id).checked) changeField(key,rememberedGains[key]);
      else {rememberedGains[key]=recipe.parameters[key]||rememberedGains[key];changeField(key,0);}
    });
  }
  $('importButton').addEventListener('click',()=>$('importFile').click());
  $('importFile').addEventListener('change',async()=>{
    try {
      const file=$('importFile').files[0];if(!file)return;
      if(file.size>65536)throw new Error('配方文件不能超过 65536 字节。');
      const imported=parseStrictJSON(await file.text());setRecipe(imported,true);
      showNotice(imported.schema==='world-volume/1'?`已导入 ${file.name}。${upgradeNotice()}`:`已导入 ${file.name}，全部 ${fieldCount()} 个字段通过验证。`);
    }catch(error){displayError(error.message);}finally{$('importFile').value='';}
  });
  $('jsonButton').addEventListener('click',()=>{try{checkExport();saveBlob(new Blob([JSON.stringify(recipe,null,2)+'\n'],{type:'application/json'}),filename('json'));showNotice(`配方 JSON 已生成，包含全部 ${fieldCount()} 个字段。`);}catch(error){displayError(error.message);}});
  $('pngButton').addEventListener('click',()=>{
    try{checkExport();if(!draw())return;gl.finish();const captureTime=elapsed;canvas.toBlob(blob=>{if(blob){saveBlob(blob,filename('png'));showNotice(`PNG 已生成：${canvas.width} × ${canvas.height}，时间 ${captureTime.toFixed(2)} 秒。`);}else displayError('截图生成失败。');},'image/png');}catch(error){displayError(error.message);}
  });
  $('zipButton').addEventListener('click',async()=>{
    try {
      checkExport();$('zipButton').disabled=true;showNotice('正在校验配方并打包共享 GLSL、参数与接入资源…');
      const response=await fetch('/api/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(recipe)});
      if(!response.ok){let message=`资源导出失败（HTTP ${response.status}）`;try{message=(await response.json()).error||message;}catch{}throw new Error(message);}
      saveBlob(await response.blob(),filename('zip'));showNotice('资源 ZIP 已生成。');
    }catch(error){displayError(error.message);}finally{updateExports();}
  });
  let dragging=false,lastPointer=[0,0];
  canvas.addEventListener('pointerdown',event=>{dragging=true;lastPointer=[event.clientX,event.clientY];canvas.setPointerCapture(event.pointerId);canvas.focus();});
  canvas.addEventListener('pointermove',event=>{
    if(!dragging)return;camera.yaw-=(event.clientX-lastPointer[0])*.006;camera.pitch=Math.max(-1.5,Math.min(1.5,camera.pitch+(event.clientY-lastPointer[1])*.006));lastPointer=[event.clientX,event.clientY];draw();
  });
  const stopDrag=()=>{dragging=false;};canvas.addEventListener('pointerup',stopDrag);canvas.addEventListener('pointercancel',stopDrag);
  canvas.addEventListener('wheel',event=>{event.preventDefault();const forward=forwardVector();const distance=event.deltaY*.08;camera.position=camera.position.map((p,i)=>p-forward[i]*distance);syncCameraInputs();draw();},{passive:false});
  canvas.addEventListener('keydown',event=>{if(['KeyW','KeyA','KeyS','KeyD','KeyQ','KeyE','ShiftLeft','ShiftRight'].includes(event.code)){event.preventDefault();keys.add(event.code);}});
  window.addEventListener('keyup',event=>keys.delete(event.code));window.addEventListener('blur',()=>keys.clear());canvas.addEventListener('blur',()=>keys.clear());
  canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();displayError('WebGL 上下文已丢失。请刷新工作台后重新载入配方。',true);});
  window.addEventListener('resize',()=>draw());
}
function syncPause() { $('pauseButton').textContent=paused?'继续':'暂停'; }
function setTime(time) {
  if(!Number.isFinite(time)||time<0){displayError('时间必须是大于或等于 0 的有限数值。');return false;}
  elapsed=time;paused=true;syncPause();$('timeInput').value=time.toFixed(2);return draw();
}
function advanceCamera(dt) {
  if(!keys.size)return false;
  const forward=forwardVector(),right=normalize(cross(forward,[0,1,0]));
  const speed=(keys.has('ShiftLeft')||keys.has('ShiftRight')?95:30)*dt;
  let delta=[0,0,0];
  for(const [code,v,sign] of [['KeyW',forward,1],['KeyS',forward,-1],['KeyD',right,1],['KeyA',right,-1],['KeyE',[0,1,0],1],['KeyQ',[0,1,0],-1]])if(keys.has(code))delta=delta.map((x,i)=>x+v[i]*sign*speed);
  camera.position=camera.position.map((x,i)=>x+delta[i]);syncCameraInputs();return true;
}
function animationFrame(now) {
  const dt=previousFrame?Math.min((now-previousFrame)/1000,.1):0;previousFrame=now;
  if(ready&&!failed){
    if(!paused)elapsed+=dt;
    const moved=advanceCamera(dt);
    if((!paused||moved)&&now-lastPaint>33){const start=performance.now();draw();frameMs=performance.now()-start;lastPaint=now;}
    if(document.activeElement!==$('timeInput'))$('timeInput').value=elapsed.toFixed(2);
    $('renderStats').textContent=`${canvas.width} × ${canvas.height} · ${recipe.parameters.viewSteps}/${recipe.parameters.lightSteps} 步 · ${paused?'已暂停':`${Math.round(1000/Math.max(33,frameMs))} fps 上限`}`;
  }
  requestAnimationFrame(animationFrame);
}
async function fetchResource(path,textMode=false) {
  const response=await fetch(path);if(!response.ok)throw new Error(`无法读取 ${path}（HTTP ${response.status}）。请用 serve.py 启动。`);
  return textMode?response.text():response.json();
}
async function start() {
  canvas=$('viewport');
  try {
    [schema,contract,presets]=await Promise.all([fetchResource('schema.json'),fetchResource('contract.json'),fetchResource('presets.json')]);
    if(schema.schema!=='world-volume/2'||contract.schema!==schema.schema)throw new Error('工作台要求一致的 world-volume/2 schema 与 contract。');
    legacyFieldNames=Object.entries(schema.fields).filter(([,spec])=>!endUniformNames.has(spec.uniform)).map(([key])=>key);
    newFieldNames=Object.keys(schema.fields).filter(key=>!legacyFieldNames.includes(key));
    if(legacyFieldNames.length!==50||newFieldNames.length!==12)throw new Error('升级契约需要完整的旧 50 字段与新增 12 字段。');
    const mappedFields=Object.values(contract.uniforms).flat();
    if(mappedFields.length!==fieldCount()||new Set(mappedFields).size!==fieldCount()||mappedFields.some(key=>!Object.hasOwn(schema.fields,key)))throw new Error('uniform 契约与 schema 字段不一致。');
    $('schemaTag').textContent=schema.schema.toUpperCase().replace('WORLD-VOLUME','WORLD VOLUME');
    $('contractSummary').textContent=`${fieldCount()} 字段 · ${presets.length} 个起点`;
    let initialRecipe=null;
    const initialResponse=await fetch('initial-recipe.json');
    if(initialResponse.ok)initialRecipe=parseStrictJSON(await initialResponse.text());
    else if(initialResponse.status!==404)throw new Error(`初始配方读取失败（HTTP ${initialResponse.status}）。`);
    recipe=acceptRecipe(initialRecipe||{schema:presets[0].schema,name:presets[0].name,parameters:presets[0].parameters});
    for(const preset of presets)validateRecipe({schema:preset.schema,name:preset.name,parameters:preset.parameters});
    for(const preset of presets){const option=document.createElement('option');option.value=preset.id;option.textContent=preset.name;$('presetSelect').append(option);}
    const custom=document.createElement('option');custom.value='custom';custom.textContent='自定义配方';$('presetSelect').append(custom);
    buildFields();syncFields();bindEvents();
    if(initialRecipe){$('presetSelect').value='custom';showNotice(initialRecipe.schema==='world-volume/1'?`已载入 initial-recipe.json。${upgradeNotice()}`:`已载入 initial-recipe.json，全部 ${fieldCount()} 个字段通过严格验证。`);}
    gl=canvas.getContext('webgl2',{alpha:false,antialias:false,preserveDrawingBuffer:true,powerPreference:'high-performance'});
    if(!gl)throw new Error('当前浏览器不支持 WebGL2，无法运行三维体积预览。');
    program=createProgram(await fetchResource('volume-core.glsl',true));vao=gl.createVertexArray();ready=true;
    $('rendererStatus').textContent='共享 GLSL · WebGL2';setView('outside');
    requestAnimationFrame(animationFrame);
    return true;
  }catch(error){displayError(error.message,true);return false;}
}
const initialization=start();
window.volumeLab={
  ready:initialization,
  getRecipe:()=>recipe?clone(recipe):null,
  validateRecipe:candidate=>validateRecipe(candidate),
  upgradeRecipe:candidate=>upgradeLegacyRecipe(candidate),
  parseRecipe:text=>acceptRecipe(parseStrictJSON(text)),
  setRecipe:async(candidate,options={})=>{if(!await initialization)throw new Error(lastError);return setRecipe(candidate,Boolean(options.resetCamera));},
  render:async(time)=>{if(!await initialization)throw new Error(lastError);if(time!==undefined&&!setTime(time))throw new Error(lastError);if(!draw())throw new Error(lastError);gl.finish();return window.volumeLab.getState();},
  setCamera:async(state)=>{
    if(!await initialization)throw new Error(lastError);
    if(state.position){if(!Array.isArray(state.position)||state.position.length!==3||!state.position.every(Number.isFinite))throw new Error('camera.position 必须含三个有限数字。');camera.position=[...state.position];}
    if(state.yaw!==undefined){if(!Number.isFinite(state.yaw))throw new Error('yaw 必须为有限数。');camera.yaw=state.yaw;}
    if(state.pitch!==undefined){if(!Number.isFinite(state.pitch))throw new Error('pitch 必须为有限数。');camera.pitch=Math.max(-1.5,Math.min(1.5,state.pitch));}
    syncCameraInputs();draw();return clone(camera);
  },
  setView:async(view)=>{if(!await initialization)throw new Error(lastError);setView(view);return clone(camera);},
  pause:value=>{paused=Boolean(value);syncPause();},
  getState:()=>({ready,failed,rendered:lastRendered,time:elapsed,paused,fieldCount:schema?Object.keys(schema.fields).length:0,presetCount:presets?.length||0,camera:clone(camera),resolution:canvas?[canvas.width,canvas.height]:null,error:lastError,composition:'linearScene + surfaceLight; scene * transmittance + radiance; tonemap'}),
  selfTest:async()=>{
    if(!await initialization)throw new Error(lastError);
    const mappedFields=Object.values(contract.uniforms).flat();
    const results={fields:new Set(mappedFields).size===fieldCount()&&mappedFields.every(key=>Object.hasOwn(schema.fields,key)),presets:presets.every(p=>Object.keys(p.parameters).length===fieldCount())};
    const base=clone(recipe);
    for(const [label,mutation] of Object.entries({missing:p=>delete p.parameters.density,extra:p=>p.parameters.unknown=1,string:p=>p.parameters.density='1',integer:p=>p.parameters.viewSteps=8.5,nan:p=>p.parameters.density=NaN,outer:p=>p.extra=1,wrongSchema:p=>p.schema='other/1'})){
      const invalid=clone(base);mutation(invalid);let rejected=false;try{validateRecipe(invalid);}catch{rejected=true;}results[`reject_${label}`]=rejected;
    }
    for(const [label,text] of Object.entries({duplicate:'{"x":1,"x":2}',nestedDuplicate:'{"parameters":{"density":1,"density":2}}',escapedDuplicate:'{"x":1,"\\u0078":2}'})){
      let rejected=false;try{parseStrictJSON(text);}catch{rejected=true;}results[`reject_${label}`]=rejected;
    }
    const legacy={schema:'world-volume/1',name:'旧配方升级自检',parameters:Object.fromEntries(legacyFieldNames.map(key=>[key,base.parameters[key]]))};
    const upgraded=upgradeLegacyRecipe(legacy);
    results.legacyPreserved=legacyFieldNames.every(key=>upgraded.parameters[key]===legacy.parameters[key]);
    results.legacyDefaults=newFieldNames.every(key=>upgraded.parameters[key]===(key==='densityModel'?0:schema.fields[key].default));
    for(const [label,mutation] of Object.entries({missing:p=>delete p.parameters.density,extra:p=>p.parameters.densityModel=1,invalid:p=>p.parameters.viewSteps=0})){
      const invalid=clone(legacy);mutation(invalid);let rejected=false;try{upgradeLegacyRecipe(invalid);}catch{rejected=true;}results[`reject_legacy_${label}`]=rejected;
    }
    results.render=draw();results.webglError=gl.getError()===gl.NO_ERROR;results.exportEnabled=exportIds.every(id=>!$(id).disabled);
    return {passed:Object.values(results).every(Boolean),results,state:window.volumeLab.getState()};
  }
};
