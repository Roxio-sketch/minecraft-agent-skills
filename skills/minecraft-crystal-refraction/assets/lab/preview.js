const $=id=>document.getElementById(id);
const ranges={cells:[2,14,0.1,'晶片频率'],jitter:[0,1,0.01,'晶片不规则度'],bendPx:[0,160,1,'折射尺度 / px'],dispersion:[0,0.15,0.005,'RGB 折射率差'],ior:[1,2.4,0.01,'基准折射率'],bevelPx:[0.25,3,0.05,'接缝宽度 / px'],edgeLight:[0,0.3,0.005,'边缘亮度'],tintMix:[0,0.6,0.01,'底色比例'],speed:[0,1,0.01,'晶面摆动速度'],blurPx:[0,3,0.1,'背景柔化 / px']};
const schema='crystal-refraction-preview/v1';
const MAX_TIME=1e7;
const header='#version 300 es\nprecision highp float;\nprecision highp int;\n';
const vertex=header+`out vec2 texCoord;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));texCoord=p;gl_Position=vec4(p*2.0-1.0,0,1);}`;
try {
const files=await Promise.all(['crystal.glsl','material.frag','source.frag','recipes.json'].map(async n=>{const r=await fetch(n);if(!r.ok)throw Error(n+': '+r.status);return r.text();}));
const presets=JSON.parse(files[3]).presets;
const canvas=$('view'), gl=canvas.getContext('webgl2',{antialias:false,alpha:false,preserveDrawingBuffer:true});
if(!gl) throw Error('此浏览器无法创建 WebGL 2 上下文');
function program(v,f){
 const p=gl.createProgram();
 for(const [type,text] of [[gl.VERTEX_SHADER,v],[gl.FRAGMENT_SHADER,f]]){const s=gl.createShader(type);gl.shaderSource(s,text);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));gl.attachShader(p,s);gl.deleteShader(s);}
 gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;
}
const material=program(vertex,header+files[1].replace('/*KERNEL*/',files[0]));
const source=program(vertex,header+files[2]);
const locations=new Map();function loc(p,n){let m=locations.get(p);if(!m){m=new Map();locations.set(p,m);}if(!m.has(n))m.set(n,gl.getUniformLocation(p,n));return m.get(n);}
const texture=gl.createTexture(), fbo=gl.createFramebuffer();let targetSize='';
gl.bindTexture(gl.TEXTURE_2D,texture);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
let state={...presets[0],time:0,view:[0,0],debug:0,amount:1}, showOriginal=false,playing=false,last=performance.now(),tinyAt=-1;
function validate(s){
 if(!s||typeof s!=='object'||Array.isArray(s))throw Error('配方必须为对象');
 const allowed=new Set(['name','seed','shape','background','tint','time','view','debug','amount',...Object.keys(ranges)]);
 for(const k of Object.keys(s))if(!allowed.has(k))throw Error('未知字段: '+k);
 for(const [k,[min,max]] of Object.entries(ranges))if(typeof s[k]!=='number'||!Number.isFinite(s[k])||s[k]<min||s[k]>max)throw Error('参数越界: '+k);
 for(const [k,min,max] of [['seed',0,65535],['shape',0,2],['background',0,1],['debug',0,3]])if(!Number.isInteger(s[k])||s[k]<min||s[k]>max)throw Error('无效整数: '+k);
 if(typeof s.tint!=='string'||!/^#[0-9a-f]{6}$/i.test(s.tint))throw Error('底色应为 #RRGGBB');
 if(typeof s.name!=='string'||s.name.length>100)throw Error('名称应不超过 100 字符');
 if(!Number.isFinite(s.time)||s.time<0||s.time>MAX_TIME||!Number.isFinite(s.amount)||s.amount<0||s.amount>1)throw Error('时间或混合比例无效');
 if(!Array.isArray(s.view)||s.view.length!==2||s.view.some(v=>!Number.isFinite(v)||Math.abs(v)>0.45))throw Error('视角范围为 ±0.45');
 return structuredClone(s);
}
function draw(w,h,s){
 if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 gl.viewport(0,0,w,h);gl.disable(gl.BLEND);gl.disable(gl.DEPTH_TEST);gl.disable(gl.DITHER);
 gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
 if(targetSize!==`${w}x${h}`){gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,null);targetSize=`${w}x${h}`;}
 gl.bindFramebuffer(gl.FRAMEBUFFER,fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);
 if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('离屏 framebuffer 不完整');
 // Detach texture from the sampling binding during the background pass.
 gl.bindTexture(gl.TEXTURE_2D,null);gl.useProgram(source);gl.uniform2f(loc(source,'Resolution'),w,h);gl.uniform1i(loc(source,'Background'),s.background);gl.drawArrays(gl.TRIANGLES,0,3);
 gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.bindTexture(gl.TEXTURE_2D,texture);gl.useProgram(material);
 gl.uniform1i(loc(material,'Source'),0);gl.uniform2f(loc(material,'Resolution'),w,h);
 const uniforms={Time:s.time,Seed:s.seed,Cells:s.cells,Jitter:s.jitter,BendPx:s.bendPx*h/600,Dispersion:s.dispersion,Ior:s.ior,BevelPx:s.bevelPx,EdgeLight:s.edgeLight,TintMix:s.tintMix,Speed:s.speed,BlurPx:s.blurPx*h/600,Amount:s.amount};
 for(const [k,v] of Object.entries(uniforms))gl.uniform1f(loc(material,k),v);
 gl.uniform3fv(loc(material,'Tint'),[1,3,5].map(i=>parseInt(s.tint.slice(i,i+2),16)/255));gl.uniform2fv(loc(material,'View'),s.view);gl.uniform1i(loc(material,'Shape'),s.shape);gl.uniform1i(loc(material,'Debug'),s.debug);
 gl.drawArrays(gl.TRIANGLES,0,3);
}
function pixels(w,h,s){draw(w,h,s);const a=new Uint8Array(w*h*4);gl.readPixels(0,0,w,h,gl.RGBA,gl.UNSIGNED_BYTE,a);return a;}
// Comparison is a display override; the recipe and its export remain unchanged.
function previewState(){return showOriginal?{...state,amount:0}:state;}
function drawPreview(w=900,h=600){draw(w,h,previewState());}
function tiny(){const visible=previewState();for(const n of [16,32]){const a=pixels(n,n,visible),out=new Uint8ClampedArray(a.length);for(let y=0;y<n;y++)out.set(a.subarray((n-y-1)*n*4,(n-y)*n*4),y*n*4);$('mini'+n).getContext('2d').putImageData(new ImageData(out,n,n),0,0);}drawPreview();}
function render(){tiny();tinyAt=performance.now();}
function syncPlayback(){$('pause').textContent=playing?'暂停动画':state.time>=MAX_TIME?'已到时间上限':'播放动画';}
function sync(){for(const k of Object.keys(ranges)){$(k).value=state[k];$('out-'+k).textContent=state[k];}for(const k of ['seed','tint','shape','background','debug'])$(k).value=state[k];$('before').textContent=showOriginal?'返回折射':'查看原图';syncPlayback();}
for(const [k,[min,max,step,label]] of Object.entries(ranges)){
 const el=document.createElement('label');el.textContent=label;const out=document.createElement('output');out.id='out-'+k;el.append(out);
 const input=document.createElement('input');Object.assign(input,{id:k,type:'range',min,max,step});input.oninput=()=>{state[k]=Number(input.value);out.textContent=state[k];render();};el.append(input);$('controls').append(el);
}
presets.forEach((p,i)=>$('preset').add(new Option(p.name,i)));
$('preset').onchange=()=>{state=validate({...presets[Number($('preset').value)],time:0,view:[0,0],debug:0,amount:1});showOriginal=false;sync();render();};
for(const k of ['seed','tint','shape','background','debug'])$(k).onchange=()=>{try{const v=k==='tint'?$(k).value:Number($(k).value);state=validate({...state,[k]:v});$('error').textContent='';sync();render();}catch(e){$('error').textContent=e.message;sync();}};
$('pause').onclick=()=>{playing=!playing&&state.time<MAX_TIME;syncPlayback();last=performance.now();};
$('reset').onclick=()=>{state.time=0;state.view=[0,0];syncPlayback();render();};
$('before').onclick=()=>{showOriginal=!showOriginal;sync();render();};
function download(blob,name){const a=document.createElement('a'),url=URL.createObjectURL(blob);a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function exportRecipe(){return {schema,state:validate(state)};}
function importRecipe(data){if(data?.schema!==schema)throw Error('不支持的配方版本');const next=validate(data.state);state=next;showOriginal=false;playing=false;sync();render();}
$('save').onclick=()=>download(new Blob([JSON.stringify(exportRecipe(),null,2)],{type:'application/json'}),'crystal-recipe.json');
$('load').onclick=()=>$('file').click();$('file').onchange=async()=>{try{const f=$('file').files[0];if(!f)return;if(f.size>32000)throw Error('配方超过 32 KB');importRecipe(JSON.parse(await f.text()));$('error').textContent='';}catch(e){$('error').textContent=e.message;}finally{$('file').value='';}};
$('png').onclick=()=>{drawPreview();canvas.toBlob(b=>download(b,'crystal-refraction.png'));};
let pointer=null;canvas.onpointerdown=e=>{pointer=[e.clientX,e.clientY,...state.view];canvas.setPointerCapture(e.pointerId);};canvas.onpointermove=e=>{if(!pointer)return;state.view=[Math.max(-0.45,Math.min(0.45,pointer[2]+(e.clientX-pointer[0])/500)),Math.max(-0.45,Math.min(0.45,pointer[3]-(e.clientY-pointer[1])/500))];render();};canvas.onpointerup=canvas.onpointercancel=()=>pointer=null;
const ext=gl.getExtension('WEBGL_debug_renderer_info');const renderer=ext?gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER);
window.crystalLab={
 getState:()=>structuredClone(state),presets:structuredClone(presets),exportRecipe,importRecipe,
 setState:patch=>{state=validate({...state,...patch});showOriginal=false;playing=false;sync();render();},
 capture:(w=192,h=128,patch={})=>{if(!Number.isInteger(w)||!Number.isInteger(h)||w<1||h<1||w>2048||h>2048)throw Error('Invalid capture size');const s=validate({...state,...patch});const p=pixels(w,h,s);const error=gl.getError();drawPreview();return {width:w,height:h,pixels:Array.from(p),error,renderer};},
 png:(w=900,h=600)=>{drawPreview(w,h);const url=canvas.toDataURL('image/png');drawPreview();return url;},
 compileGLSL:(v,f)=>{const p=program(v,f);gl.deleteProgram(p);return true;}
};
sync();render();$('status').textContent='着色器编译成功 · '+renderer;
function loop(now){
 if(playing){
  state.time=Math.min(MAX_TIME,state.time+Math.max(0,Math.min((now-last)/1000,0.1)));
  if(state.time>=MAX_TIME){playing=false;syncPlayback();render();}
  else if(now-tinyAt>250){tiny();tinyAt=now;}
  else drawPreview();
 }
 last=now;requestAnimationFrame(loop);
}requestAnimationFrame(loop);
window.addEventListener('beforeunload',()=>{gl.deleteFramebuffer(fbo);gl.deleteTexture(texture);gl.deleteProgram(source);gl.deleteProgram(material);});
} catch(e){$('error').textContent=e.stack||e.message;$('status').textContent='初始化失败';window.crystalLabError=String(e);}
