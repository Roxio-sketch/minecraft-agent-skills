/* Native WebGL2 editor. No CDN or network dependency. */
'use strict';
const $=id=>document.getElementById(id),canvas=$('canvas');
let recipe=defaultRecipe(),playing=true,time=0,yaw=.35,pitch=.16,distance=5.6,dirty=true,previewMoving=false,previewIsolated=false;
let renderer,frames=0,last=performance.now(),lastHud=last,fps=0;
const message=(s,error=false)=>{$('message').textContent=s;$('message').className=error?'error':'';};
const groups=[...new Set(FIELDS.map(f=>f[1]))];
for(const group of groups){const section=document.createElement('details');section.className='section';section.open=['形态','持续发散','密度与细丝'].includes(group);section.innerHTML='<summary>'+group+'</summary>';
 for(const f of FIELDS.filter(f=>f[1]===group)){const [key,,label,min,max,step]=f;const row=document.createElement('div');row.className='row';row.innerHTML=`<div class="label"><label for="range-${key}">${label}</label><input id="number-${key}" aria-label="${label}数值" type="number" min="${min}" max="${max}" step="${step}"></div><input id="range-${key}" aria-label="${label}" type="range" min="${min}" max="${max}" step="${step}">`;
 for(const kind of ['range','number'])row.querySelector('#'+kind+'-'+key).addEventListener('input',e=>{const v=Number(e.target.value);if(!Number.isFinite(v)||v<min||v>max||(step===1&&!Number.isInteger(v)))return;recipe.params[key]=v;$('range-'+key).value=v;$('number-'+key).value=v;dirty=true;});section.append(row);}
 $('controls').append(section);
}
PRESETS.forEach((p,i)=>{const o=document.createElement('option');o.textContent=p.name;o.value=i;$('preset').append(o);});
const customOption=document.createElement('option');customOption.value='custom';customOption.textContent='自定义配方';customOption.disabled=true;$('preset').append(customOption);
const avatarOption=document.createElement('option');avatarOption.value=4;avatarOption.textContent='玩家与装备';$('environment').append(avatarOption);
const motionButton=document.createElement('button');motionButton.textContent='移动人物';motionButton.onclick=()=>{previewMoving=!previewMoving;motionButton.textContent=previewMoving?'停止移动':'移动人物';dirty=true;};$('capture').before(motionButton);
const smokeButton=document.createElement('button');smokeButton.textContent='烟雾特写';smokeButton.onclick=()=>{previewIsolated=!previewIsolated;smokeButton.textContent=previewIsolated?'返回人物':'烟雾特写';$('environment').value=previewIsolated?0:4;distance=previewIsolated?4.5:4.1;time=2.6;dirty=true;};$('capture').before(smokeButton);
function sync(){for(const f of FIELDS){$('range-'+f[0]).value=recipe.params[f[0]];$('number-'+f[0]).value=recipe.params[f[0]];}for(const k of ['name','color','secondary'])$(k).value=recipe[k];dirty=true;}
function apply(r){recipe=validateRecipe(r);time=0;sync();const presetIndex=PRESETS.findIndex(p=>p.name===recipe.name);$('preset').value=presetIndex>=0?String(presetIndex):'custom';$('reset').disabled=presetIndex<0;if(recipe.params.attachment>0){$('environment').value=4;distance=4.1;yaw=.3;pitch=.12;}message('已载入：'+recipe.name+' · 全部 '+FIELDS.length+' 个参数已应用');}
for(const k of ['name','color','secondary'])$(k).addEventListener('input',()=>{recipe[k]=$(k).value;dirty=true;});
$('preset').onchange=()=>apply(PRESETS[Number($('preset').value)]);
$('reset').onclick=()=>apply(PRESETS[Number($('preset').value)]);
$('play').onclick=()=>{playing=!playing;$('play').textContent=playing?'暂停':'播放';};
$('restart').onclick=()=>{time=0;dirty=true;};
$('time').oninput=()=>{time=Number($('time').value);playing=false;$('play').textContent='播放';dirty=true;};
$('environment').onchange=()=>dirty=true;
$('cameraReset').onclick=()=>{yaw=.35;pitch=.16;distance=5.6;dirty=true;};
function download(data,name){const url=URL.createObjectURL(data),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);}
$('export').onclick=()=>{try{const r=validateRecipe(recipe);download(new Blob([JSON.stringify(r,null,2)],{type:'application/json'}),'energy-recipe.json');message('已导出 energy-vfx/2 游戏配方；包含全部参数和种子。');}catch(e){message(e.message,true);}};
$('import').onclick=()=>$('file').click();
$('save').onclick=()=>message('请使用导出按钮保存 JSON 配方；本预览不写入游戏配置。');
$('file').onchange=async()=>{try{const file=$('file').files[0];if(!file)return;if(file.size>100000)throw Error('配方文件超过 100 KB');apply(JSON.parse(await file.text()));}catch(e){message('导入失败，保留原配方：'+e.message,true);}finally{$('file').value='';}};
$('capture').onclick=()=>{try{renderer.draw();canvas.toBlob(blob=>{if(blob)download(blob,'energy-preview.png');},'image/png');}catch(e){message(e.message,true);}};
$('help').onclick=()=>$('helpDialog').showModal();$('closeHelp').onclick=()=>$('helpDialog').close();
let pointer;
canvas.onpointerdown=e=>{pointer={x:e.clientX,y:e.clientY};canvas.setPointerCapture(e.pointerId);};
canvas.onpointermove=e=>{if(!pointer)return;yaw-=(e.clientX-pointer.x)*.007;pitch=Math.max(-1.35,Math.min(1.35,pitch+(e.clientY-pointer.y)*.007));pointer={x:e.clientX,y:e.clientY};dirty=true;};
canvas.onpointerup=canvas.onpointercancel=()=>pointer=null;
canvas.onwheel=e=>{e.preventDefault();distance=Math.max(.15,Math.min(22,distance*Math.exp(e.deltaY*.001)));dirty=true;};
function color(s){return [1,3,5].map(i=>Math.pow(parseInt(s.slice(i,i+2),16)/255,2.2));}
const normalize=v=>{const l=Math.hypot(...v);return v.map(x=>x/l);};
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
class Renderer{
 constructor(){this.gl=canvas.getContext('webgl2',{alpha:false,antialias:false,preserveDrawingBuffer:true});if(!this.gl)throw Error('浏览器未提供 WebGL2');const gl=this.gl;if(!gl.getExtension('EXT_color_buffer_float'))throw Error('GPU 未提供 HDR 浮点渲染附件，请启用硬件加速');
 const vertex='#version 300 es\nprecision highp float;\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0,1);}';
 const compile=(type,src)=>{const shader=gl.createShader(type);gl.shaderSource(shader,src);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(shader));return shader;};
 const program=src=>{const p=gl.createProgram(),vs=compile(gl.VERTEX_SHADER,vertex),fs=compile(gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;\n'+src);gl.attachShader(p,vs);gl.attachShader(p,fs);gl.linkProgram(p);gl.deleteShader(vs);gl.deleteShader(fs);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;};
 this.volume=program(SHADERS.noise+SHADERS.field+SHADERS.scene+SHADERS.volume.replace('out vec4 volumeColor;','layout(location=0) out vec4 volumeColor;').replace('out vec4 emissionColor;','layout(location=1) out vec4 emissionColor;'));
 this.blur=program(SHADERS.blur);this.final=program(SHADERS.scene+SHADERS.composite);this.vao=gl.createVertexArray();gl.bindVertexArray(this.vao);this.targets=[];this.locations=new Map();
 }
 loc(p,k){const key=p===this.volume?'v':p===this.blur?'b':'c';if(!this.locations.has(key+k))this.locations.set(key+k,this.gl.getUniformLocation(p,k));return this.locations.get(key+k);}
 target(w,h,count){const gl=this.gl,f=gl.createFramebuffer(),textures=[];gl.bindFramebuffer(gl.FRAMEBUFFER,f);for(let i=0;i<count;i++){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA16F,w,h,0,gl.RGBA,gl.HALF_FLOAT,null);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0+i,gl.TEXTURE_2D,t,0);textures.push(t);}gl.drawBuffers(textures.map((_,i)=>gl.COLOR_ATTACHMENT0+i));if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('HDR framebuffer incomplete');return {f,textures,w,h};}
 texture(p,k,t,slot){const gl=this.gl;gl.activeTexture(gl.TEXTURE0+slot);gl.bindTexture(gl.TEXTURE_2D,t);gl.uniform1i(this.loc(p,k),slot);}
 pass(p,target){const gl=this.gl;gl.useProgram(p);gl.bindFramebuffer(gl.FRAMEBUFFER,target?.f||null);gl.viewport(0,0,target?.w||canvas.width,target?.h||canvas.height);gl.uniform2f(this.loc(p,'viewport'),target?.w||canvas.width,target?.h||canvas.height);}
 draw(){const gl=this.gl,rect=canvas.getBoundingClientRect(),scale=recipe.params.resolution;
 // Cap physical preview width; the recipe resolution remains the runtime ratio.
 const fullW=Math.max(1,Math.round(Math.min(1200,rect.width))),fullH=Math.max(1,Math.round(fullW*rect.height/rect.width)),w=Math.max(1,Math.round(fullW*scale)),h=Math.max(1,Math.round(fullH*scale));
 if(fullW!==canvas.width||fullH!==canvas.height||this.targets[0]?.w!==w||this.targets[0]?.h!==h){canvas.width=fullW;canvas.height=fullH;for(const t of this.targets){gl.deleteFramebuffer(t.f);t.textures.forEach(x=>gl.deleteTexture(x));}this.targets=[this.target(w,h,2),this.target(Math.max(1,w>>1),Math.max(1,h>>1),1),this.target(Math.max(1,w>>1),Math.max(1,h>>1),1)];}
 const [volume,a,b]=this.targets,p=this.volume;gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);gl.disable(gl.DITHER);this.pass(p,volume);
 const cam=[Math.sin(yaw)*Math.cos(pitch)*distance,Math.sin(pitch)*distance,Math.cos(yaw)*Math.cos(pitch)*distance],f=normalize(cam.map(x=>-x)),r=normalize(cross(f,[0,1,0])),up=cross(r,f);
 if(previewIsolated)cam[1]+=.65;
 gl.uniform1fv(this.loc(p,'u[0]'),Float32Array.from(FIELDS.map(x=>recipe.params[x[0]])));gl.uniform1f(this.loc(p,'clockTime'),time);gl.uniform3fv(this.loc(p,'colorA'),color(recipe.color));gl.uniform3fv(this.loc(p,'colorB'),color(recipe.secondary));
 const packets=auraPackets(recipe,time,previewMoving,previewIsolated);gl.uniform4fv(this.loc(p,'puffFlow[0]'),packets.flows);gl.uniform1fv(this.loc(p,'puffSeed[0]'),packets.seeds);gl.uniform4fv(this.loc(p,'puffs[0]'),packets.positions);gl.uniform1fv(this.loc(p,'puffStrength[0]'),packets.strengths);gl.uniform1i(this.loc(p,'puffCount'),packets.count);gl.uniform3fv(this.loc(p,'auraMin'),packets.min);gl.uniform3fv(this.loc(p,'auraMax'),packets.max);gl.uniform3f(this.loc(p,'noiseOrigin'),0,0,0);gl.uniform1f(this.loc(p,'auraOpacity'),distance<.9?recipe.params.firstPerson:1);gl.uniform3f(this.loc(p,'avatarOffset'),previewMoving?Math.sin(time*.9)*.7:0,0,0);
 for(const [k,v] of [['camera',cam],['rightVec',r],['upVec',up],['forwardVec',f]])gl.uniform3fv(this.loc(p,k),v);
 gl.uniform1f(this.loc(p,'tanHalfFov'),Math.tan(Math.PI/6));gl.uniform1i(this.loc(p,'gameScene'),0);gl.uniform1i(this.loc(p,'environment'),Number($('environment').value));
 // Bind a harmless texture for inactive game samplers; no read/write feedback.
 this.texture(p,'sceneColor',a.textures[0],0);this.texture(p,'sceneDepth',a.textures[0],1);gl.drawArrays(gl.TRIANGLES,0,3);
 this.pass(this.blur,a);this.texture(this.blur,'image',volume.textures[1],0);gl.uniform2f(this.loc(this.blur,'direction'),recipe.params.bloomRadius/w,0);gl.drawArrays(gl.TRIANGLES,0,3);
 this.pass(this.blur,b);this.texture(this.blur,'image',a.textures[0],0);gl.uniform2f(this.loc(this.blur,'direction'),0,recipe.params.bloomRadius/h);gl.drawArrays(gl.TRIANGLES,0,3);
 this.pass(this.final,null);this.texture(this.final,'image',volume.textures[0],0);this.texture(this.final,'glow',b.textures[0],1);this.texture(this.final,'sceneColor',a.textures[0],2);this.texture(this.final,'sceneDepth',a.textures[0],3);
 for(const [k,v] of [['camera',cam],['rightVec',r],['upVec',up],['forwardVec',f]])gl.uniform3fv(this.loc(this.final,k),v);gl.uniform3f(this.loc(this.final,'avatarOffset'),previewMoving?Math.sin(time*.9)*.7:0,0,0);
 gl.uniform1f(this.loc(this.final,'tanHalfFov'),Math.tan(Math.PI/6));gl.uniform1i(this.loc(this.final,'gameScene'),0);gl.uniform1i(this.loc(this.final,'environment'),Number($('environment').value));gl.uniform1f(this.loc(this.final,'bloom'),recipe.params.bloom);gl.uniform1f(this.loc(this.final,'distortion'),recipe.params.distortion);gl.uniform1f(this.loc(this.final,'clockTime'),time);gl.drawArrays(gl.TRIANGLES,0,3);
 this.error=gl.getError();if(this.error)throw Error('WebGL error '+this.error);
 }
}
sync();
try{renderer=new Renderer();}catch(e){message('渲染初始化失败：'+e.message,true);$('hud').textContent='GPU 初始化失败';console.error(e);}
function frame(now){const dt=Math.min(.5,(now-last)/1000);last=now;if(renderer){try{if(playing){time+=dt;dirty=true;}if(dirty){renderer.draw();dirty=false;frames++;}if(now-lastHud>1000){fps=frames*1000/(now-lastHud);frames=0;lastHud=now;$('hud').textContent=`WebGL2 · ${canvas.width} × ${canvas.height} · ${recipe.params.steps} 步 · ${fps.toFixed(0)} FPS`;}$('time').value=Math.min(time,20);$('timeText').textContent=time.toFixed(2)+' s';}catch(e){message('渲染停止：'+e.message,true);console.error(e);renderer=undefined;}}requestAnimationFrame(frame);}
requestAnimationFrame(frame);new ResizeObserver(()=>dirty=true).observe(canvas);
// Explicit inspectable API for QA and deterministic recipe consumption.
window.energyEditor={getRecipe:()=>JSON.parse(JSON.stringify(recipe)),applyRecipe:apply,setTime:t=>{time=t;playing=false;dirty=true;},setIsolated:v=>{previewIsolated=v;$('environment').value=v?0:4;dirty=true;},setCamera:(y,p,d)=>{yaw=y;pitch=p;distance=d;dirty=true;},draw:()=>renderer.draw(),getStatus:()=>({ready:!!renderer,time,fps,width:canvas.width,height:canvas.height,glError:renderer?.error||0}),presets:PRESETS};
