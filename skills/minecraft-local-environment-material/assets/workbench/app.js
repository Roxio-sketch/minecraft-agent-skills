const $ = id => document.getElementById(id);
const bounds = {
  angleDeg: [-180, 180], scale: [0.25, 3], offset: [-0.5, 0.5],
  edgeSensitivity: [0, 1], detailSuppression: [0, 1], depthStrength: [0, 1],
  shadeAmount: [0, 1], lineWeight: [0.5, 2.5]
};
const presetUrl = 'one-last-kiss.json';
const shaderUrl = 'sketch.fsh';
const canvas = $('preview');
const gl = canvas.getContext('webgl2', { alpha: false, antialias: false, preserveDrawingBuffer: true });
let original, state, program, sceneTexture, depthTexture, maskTexture, sceneWidth = 1280, sceneHeight = 550;

function fail(message) { $('error').textContent = message; $('error').style.display = 'block'; throw Error(message); }
function compile(type, source) {
  const shader = gl.createShader(type); gl.shaderSource(shader, source); gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) fail(gl.getShaderInfoLog(shader));
  return shader;
}
function initGL(fragment) {
  if (!gl) fail('此浏览器不支持 WebGL 2');
  const vertex = `#version 300 es
  layout(location=0) in vec2 Position; layout(location=1) in vec2 UV0;
  out vec2 uv; void main(){uv=UV0;gl_Position=vec4(Position,0.0,1.0);}`;
  const source = fragment.replace(/^#version 150\s*/, '#version 300 es\nprecision highp float;\n');
  program = gl.createProgram();
  gl.attachShader(program, compile(gl.VERTEX_SHADER, vertex));
  gl.attachShader(program, compile(gl.FRAGMENT_SHADER, source));
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) fail(gl.getProgramInfoLog(program));
  const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
  const data = new Float32Array([-1,-1,0,0, 3,-1,2,0, -1,3,0,2]);
  const buffer = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0,2,gl.FLOAT,false,16,0);
  gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1,2,gl.FLOAT,false,16,8);
  sceneTexture = gl.createTexture();
  gl.activeTexture(gl.TEXTURE1); depthTexture = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, depthTexture);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([255,255,255,255]));
  gl.activeTexture(gl.TEXTURE5);maskTexture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,maskTexture);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([0,0,0,255]));
  gl.useProgram(program);
  gl.uniform1i(gl.getUniformLocation(program,'Sampler0'),0);
  gl.uniform1i(gl.getUniformLocation(program,'Sampler1'),1);
  gl.uniform1i(gl.getUniformLocation(program,'Sampler2'),2);
  gl.uniform1i(gl.getUniformLocation(program,'Sampler3'),3);
  gl.uniform1i(gl.getUniformLocation(program,'Sampler4'),4);
  gl.uniform1i(gl.getUniformLocation(program,'Sampler5'),5);
}
function uploadScene(image) {
  const factor = Math.min(1, 1280/image.width);
  sceneWidth = Math.max(1, Math.round(image.width*factor));
  sceneHeight = Math.max(1, Math.round(image.height*factor));
  canvas.width = sceneWidth; canvas.height = sceneHeight;
  const source = document.createElement('canvas');source.width=sceneWidth;source.height=sceneHeight;
  source.getContext('2d').drawImage(image,0,0,sceneWidth,sceneHeight);
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,sceneTexture);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,source);
  render();
}
function imageFrom(src) { return new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=reject;image.src=src;}); }
function color(rgb) {return [1,3,5].map(n=>parseInt(rgb.slice(n,n+2),16)/255);}
function set1(name,value){gl.uniform1f(gl.getUniformLocation(program,name),value);}
function render(){
  if(!gl||!program||!state||!sceneTexture)return;
  gl.viewport(0,0,sceneWidth,sceneHeight);gl.useProgram(program);
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,sceneTexture);
  gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,depthTexture);
  gl.activeTexture(gl.TEXTURE2);gl.bindTexture(gl.TEXTURE_2D,depthTexture);
  gl.activeTexture(gl.TEXTURE3);gl.bindTexture(gl.TEXTURE_2D,depthTexture);
  gl.activeTexture(gl.TEXTURE4);gl.bindTexture(gl.TEXTURE_2D,depthTexture);
  gl.activeTexture(gl.TEXTURE5);gl.bindTexture(gl.TEXTURE_2D,maskTexture);
  gl.uniform2f(gl.getUniformLocation(program,'TexelSize'),1/sceneWidth,1/sceneHeight);
  state.gradient.stops.forEach((stop,i)=>{const c=color(stop.color);gl.uniform4f(gl.getUniformLocation(program,`Stop${i}`),...c,stop.at);});
  const angle=state.gradient.angleDeg*Math.PI/180;
  gl.uniform2f(gl.getUniformLocation(program,'GradientAxis'),Math.cos(angle),Math.sin(angle));
  set1('GradientScale',state.gradient.scale);set1('GradientOffset',state.gradient.offset);
  for(const key of Object.keys(state.line))set1(key[0].toUpperCase()+key.slice(1),state.line[key]);
  gl.drawArrays(gl.TRIANGLES,0,3);
  const error=gl.getError();if(error!==gl.NO_ERROR)$('error').textContent=`WebGL error ${error}`;
}
function validate(value){
  if(value?.schema!=='olk-sketch/v1')throw Error('预设 schema 不匹配');
  const stops=value.gradient?.stops;
  if(!Array.isArray(stops)||stops.length!==5)throw Error('必须有五个色标');
  stops.forEach((s,i)=>{
    if(!/^#[0-9a-fA-F]{6}$/.test(s.color)||typeof s.at!=='number'||s.at<0||s.at>1)throw Error(`第 ${i+1} 个色标无效`);
    if(i>0&&s.at<=stops[i-1].at)throw Error('色标位置必须递增');
  });
  if(stops[0].at!==0||stops[4].at!==1)throw Error('首尾色标必须是 0 和 1');
  for(const [key,[lo,hi]] of Object.entries(bounds)){
    const n=key in value.gradient?value.gradient[key]:value.line?.[key];
    if(typeof n!=='number'||!Number.isFinite(n)||n<lo||n>hi)throw Error(`${key} 超出范围`);
  }
  return JSON.parse(JSON.stringify(value));
}
function controls(){
  const labels=['起点','暖红','玫紫','蓝紫','终点'];$('stops').replaceChildren();
  state.gradient.stops.forEach((stop,i)=>{
    const row=document.createElement('div');row.className='stop-row';
    const title=document.createElement('span');title.textContent=labels[i];
    const pick=document.createElement('input');pick.type='color';pick.className='swatch';pick.value=stop.color;
    const range=document.createElement('input');range.type='range';range.min=i===0?0:state.gradient.stops[i-1].at+0.01;
    range.max=i===4?1:state.gradient.stops[i+1].at-0.01;range.step='0.01';range.value=stop.at;range.disabled=i===0||i===4;
    const value=document.createElement('output');value.textContent=stop.at.toFixed(2);
    pick.addEventListener('input',()=>{stop.color=pick.value;updateStrip();render();});
    range.addEventListener('input',()=>{
      stop.at=Number(range.value);value.textContent=stop.at.toFixed(2);
      const rows=[...$('stops').children];
      if(i>0)rows[i-1].querySelector('input[type=range]').max=(stop.at-0.01).toFixed(2);
      if(i<4)rows[i+1].querySelector('input[type=range]').min=(stop.at+0.01).toFixed(2);
      updateStrip();render();
    });
    row.append(title,pick,range,value);$('stops').append(row);
  });
  for(const key of Object.keys(bounds)){
    const input=$(key), group=key in state.gradient?state.gradient:state.line;
    input.value=group[key];$(key+'Value').textContent=Number(group[key]).toFixed(key==='angleDeg'?0:2);
    input.oninput=()=>{group[key]=Number(input.value);$(key+'Value').textContent=Number(input.value).toFixed(key==='angleDeg'?0:2);render();};
  }
  updateStrip();render();
}
function updateStrip(){
  $('gradientStrip').style.background=`linear-gradient(90deg,${state.gradient.stops.map(s=>`${s.color} ${s.at*100}%`).join(',')})`;
}
async function start(){
  try{
    const [rawPreset,fragment]=await Promise.all([fetch(presetUrl).then(r=>r.json()),fetch(shaderUrl).then(r=>r.text())]);
    original=validate(rawPreset);state=structuredClone(original);initGL(fragment);controls();
    const sample=document.createElement('canvas');sample.width=1280;sample.height=550;const ctx=sample.getContext('2d');
    const gradient=ctx.createLinearGradient(0,0,1280,550);gradient.addColorStop(0,'#81bddd');gradient.addColorStop(1,'#263847');ctx.fillStyle=gradient;ctx.fillRect(0,0,1280,550);
    for(let i=0;i<15;i++){ctx.fillStyle=i%2?'#52866e':'#c5b996';ctx.fillRect(i*100,230+(i%4)*40,80,320);}uploadScene(sample);
    $('sceneUpload').onchange=async e=>{const file=e.target.files[0];if(file){const url=URL.createObjectURL(file);try{uploadScene(await imageFrom(url));}finally{URL.revokeObjectURL(url);}}};
    $('reset').onclick=()=>{state=structuredClone(original);controls();};
    $('export').onclick=()=>{const blob=new Blob([JSON.stringify(validate(state),null,2)+'\n'],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='one-last-kiss.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
    $('presetUpload').onchange=async e=>{const file=e.target.files[0];if(!file)return;try{state=validate(JSON.parse(await file.text()));controls();$('error').style.display='none';}catch(error){$('error').textContent=error.message;$('error').style.display='block';}};
  }catch(error){$('error').textContent=error.message;$('error').style.display='block';console.error(error);}
}
start();
