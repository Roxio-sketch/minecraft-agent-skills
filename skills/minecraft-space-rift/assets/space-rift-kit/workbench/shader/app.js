import {controls,defaults,presets as classicPresets,presetInfo as classicInfo,horizonPresets,horizonInfo,validateRecipe} from './parameters.js';
import {geometry} from './geometry.js';
const $=s=>document.querySelector(s),canvas=$('#canvas'),viewer=$('#viewer');
const gl=canvas.getContext('webgl2',{alpha:false,antialias:false,preserveDrawingBuffer:true});
let params={...defaults},program,background,locations,scene,fbo,geometryKey='',points;
let paused=false,previous=performance.now(),animation=null,pan=[0,0],gpuMs=null;
const query=new URLSearchParams(location.search),test=query.has('test');
const horizon=()=>params.u_horizon>.5;
const presets={...horizonPresets,...classicPresets},presetInfo={...horizonInfo,...classicInfo};
let backgroundMode=0,backgroundImage=null,backgroundHasImage=false;
if(test)paused=true;
function report(e){$('#error').hidden=false;$('#error').textContent=e.message||String(e);}
function sync(){
 for(const [key,,, ,step] of controls){const input=$('#'+key);input.value=params[key];input.previousElementSibling.querySelector('output').textContent=Number(params[key]).toFixed(step>=1?0:step>=.01?2:3);}
 for(const id of ['u_edgeColor','u_insideColor'])$('#'+id).value=params[id];
 $('#mode').value=params.u_mode;$('#quality').value=params.u_quality;$('#debug').value=params.u_debug;
 for(const [key]of controls){const hidden=key==='u_horizon'||(horizon()&&['u_branches','u_branchLength','u_debris','u_interiorMode'].includes(key));$('#'+key).closest('label').hidden=hidden;}
 $('#style').closest('details').hidden=horizon();
 $('#sequence').textContent=horizon()?'▶ 划开 → 张开 → 悬停 → 收拢':'▶ 裂缝 → 玻璃破碎 → 巨大裂隙';
 $('#viewLabel').textContent=`${params.u_zoom.toFixed(2)}× · 种子 ${params.u_seed}`;
 $('#phase').textContent=params.u_sequenceEnabled<.5?'独立形状调试':params.u_horizon>.5?(params.u_sequence<.28?'细线划开并延伸':params.u_sequence<.6?'中部张开':params.u_sequence<.82?'裂隙悬停':'收拢消失'):params.u_sequence<.28?'第一步 · 空间出现细裂缝':params.u_sequence<.64?'第二步 · 空间像玻璃一样破碎':'第三步 · 巨大的空间裂隙';
 const key=Object.keys(presets).find(k=>presets[k].u_style===params.u_style&&k===($('#stylePreset').value||'slash'));
 if(key)$('#styleDescription').textContent=presetInfo[key][1];
}
function apply(raw){const next=validateRecipe(raw);if(next.u_horizon>.5)Object.assign(next,{u_horizon:1,u_debris:0,u_branches:0,u_style:0});params=next;geometryKey='';const exact=Object.keys(presets).find(k=>Object.keys(presets[k]).every(field=>presets[k][field]===next[field]));const current=presets[$('#stylePreset').value];if(exact)$('#stylePreset').value=exact;else if(!current||current.u_horizon!==next.u_horizon||current.u_style!==next.u_style)$('#stylePreset').value=Object.keys(presets).find(k=>presets[k].u_horizon===next.u_horizon&&presets[k].u_style===next.u_style)||'horizon-giant';sync();$('#error').hidden=true;}
for(const [key,label,min,max,step,group] of controls){
 const el=document.createElement('label');el.className='control';
 el.innerHTML=`<div><span>${label}</span><output></output></div><input id="${key}" type="range" min="${min}" max="${max}" step="${step}">`;
 el.querySelector('input').addEventListener('input',e=>{params[key]=+e.target.value;if(['u_progress','u_shrink','u_sequence'].includes(key))animation=null;sync();});$('#'+group).append(el);

}
for(const id of ['u_edgeColor','u_insideColor'])$('#'+id).addEventListener('input',e=>params[id]=e.target.value);
for(const [id,key] of [['mode','u_mode'],['quality','u_quality'],['debug','u_debug']])$('#'+id).addEventListener('change',e=>{params[key]=+e.target.value;});
for(const [label,items] of [['横向星河 · 无杂物',horizonInfo],['原有裂缝与材质样式',classicInfo]]){const group=document.createElement('optgroup');group.label=label;for(const [key,info]of Object.entries(items)){const option=document.createElement('option');option.value=key;option.textContent=info[0];group.append(option);}$('#stylePreset').append(group);}
function selectPreset(key){if(!presets[key])throw Error('未知样式');animation=null;apply(presets[key]);pan=[0,0];$('#stylePreset').value=key;$('#styleDescription').textContent=presetInfo[key][1];}
document.querySelectorAll('[data-preset]').forEach(b=>b.onclick=()=>selectPreset(b.dataset.preset));
$('#stylePreset').onchange=e=>selectPreset(e.target.value);
$('#pause').onclick=()=>{paused=!paused;$('#pause').textContent=paused?'继续':'暂停';};
$('#sequence').onclick=()=>{const p=horizon()?params:params.u_style===0?presets.sky:params;apply({...p,u_mode:1,u_width:horizon()?p.u_width:Math.max(.14,p.u_width),u_sequenceEnabled:1,u_sequence:0});paused=false;animation='sequence';$('#pause').textContent='暂停';pan=[0,0];sync();};
$('#grow').onclick=()=>{params.u_sequenceEnabled=0;params.u_progress=0;params.u_shrink=0;params.u_open=Math.max(.2,params.u_open);paused=false;animation='grow';$('#pause').textContent='暂停';sync();};
$('#close').onclick=()=>{paused=false;animation='close';$('#pause').textContent='暂停';};
function save(blob,name){const a=document.createElement('a'),url=URL.createObjectURL(blob);a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
$('#export').onclick=()=>save(new Blob([JSON.stringify(recipe(),null,2)],{type:'application/json'}),'space-rift.json');
$('#snapshot').onclick=()=>canvas.toBlob(b=>b&&save(b,'space-rift.png'));
$('#import').onchange=async e=>{try{const f=e.target.files[0];if(f)apply(JSON.parse(await f.text()));}catch(err){report(err);}e.target.value='';};
if($('#backgroundMode'))$('#backgroundMode').onchange=e=>{backgroundMode=+e.target.value;};
if($('#backgroundUpload'))$('#backgroundUpload').onchange=async e=>{try{const file=e.target.files[0];if(!file)return;const bitmap=await createImageBitmap(file);if(backgroundImage)gl.deleteTexture(backgroundImage);backgroundImage=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,backgroundImage);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,bitmap);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);bitmap.close();backgroundHasImage=true;backgroundMode=2;$('#backgroundMode').value='2';}catch(error){report(error);}};
function recipe(){return {format:'space-rift/2',parameters:{...params}};}
canvas.addEventListener('wheel',e=>{e.preventDefault();params.u_zoom=Math.max(.1,Math.min(12,params.u_zoom*Math.exp(-e.deltaY*.001)));sync();},{passive:false});
let dragging=null;canvas.onpointerdown=e=>{dragging=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);};
canvas.onpointerup=()=>dragging=null;canvas.onpointermove=e=>{if(!dragging)return;pan[0]-=(e.clientX-dragging[0])*2/viewer.clientHeight/params.u_zoom;pan[1]+=(e.clientY-dragging[1])*2/viewer.clientHeight/params.u_zoom;dragging=[e.clientX,e.clientY];};
function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function link(v,f){const a=compile(gl.VERTEX_SHADER,v),b=compile(gl.FRAGMENT_SHADER,f),p=gl.createProgram();gl.attachShader(p,a);gl.attachShader(p,b);gl.linkProgram(p);gl.deleteShader(a);gl.deleteShader(b);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
function resize(){const scale=[.7,1,Math.min(devicePixelRatio||1,1.5)][params.u_quality];const w=Math.max(2,Math.round(viewer.clientWidth*scale)),h=Math.max(2,Math.round(viewer.clientHeight*scale));if(w===canvas.width&&h===canvas.height&&fbo)return;
 canvas.width=w;canvas.height=h;if(scene)gl.deleteTexture(scene);if(fbo)gl.deleteFramebuffer(fbo);
 scene=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,scene);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,null);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
 fbo=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,scene,0);if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('测试背景帧缓冲不完整');gl.bindFramebuffer(gl.FRAMEBUFFER,null);
}
function render(time=params.u_time){
 resize();gl.viewport(0,0,canvas.width,canvas.height);gl.bindFramebuffer(gl.FRAMEBUFFER,fbo);gl.useProgram(background);
 gl.uniform2f(gl.getUniformLocation(background,'resolution'),canvas.width,canvas.height);gl.uniform1f(gl.getUniformLocation(background,'grid'),params.u_grid);
 gl.uniform1f(gl.getUniformLocation(background,'backgroundMode'),backgroundMode===2&&!backgroundHasImage?1:backgroundMode);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,backgroundImage);gl.uniform1i(gl.getUniformLocation(background,'backgroundImage'),1);gl.drawArrays(gl.TRIANGLES,0,3);
 gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.useProgram(program);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,scene);gl.uniform1i(locations.SceneColor,0);
 gl.uniform2f(locations.u_resolution,canvas.width,canvas.height);gl.uniform2f(locations.u_pan,...pan);gl.uniform1f(locations.u_time,time);
 for(const [key,value]of Object.entries(params)){const l=locations[key];if(l==null)continue;if(key.endsWith('Color')){const h=value.slice(1);gl.uniform3f(l,...[0,2,4].map(i=>parseInt(h.slice(i,i+2),16)/255));}else if(key!=='u_time')gl.uniform1f(l,value);}
 const k=JSON.stringify([params.u_seed,params.u_length,params.u_width,params.u_bend,params.u_branchLength,params.u_mode]);
 if(k!==geometryKey){points=geometry(params);geometryKey=k;}
 gl.uniform4fv(locations['u_spine[0]'],points.spine);gl.uniform4fv(locations['u_branchA[0]'],points.branchA);gl.uniform4fv(locations['u_branchB[0]'],points.branchB);
 gl.drawArrays(gl.TRIANGLES,0,3);
 return {error:gl.getError(),width:canvas.width,height:canvas.height};
}
const ready=(async()=>{
 if(!gl)throw Error('需要支持 WebGL2 的浏览器');
 gl.disable(gl.DITHER);
 const load=async p=>{const r=await fetch(p);if(!r.ok)throw Error('加载失败 '+p);return r.text();};
 const [v,f]=await Promise.all([load('shader/rift.vert'),load('shader/rift.frag')]);program=link(v,f);
 backgroundImage=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,backgroundImage);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([0,0,0,255]));gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
 background=link(v,`#version 300 es
 precision highp float;uniform vec2 resolution;uniform float grid,backgroundMode;uniform sampler2D backgroundImage;out vec4 outColor;
 void main(){vec2 uv=gl_FragCoord.xy/resolution,p=(gl_FragCoord.xy-.5*resolution)*2./resolution.y;vec2 q=abs(fract(p*3.)-.5)/max(fwidth(p*3.),vec2(.0001));float line=1.-smoothstep(.4,1.4,min(q.x,q.y));float horizon=exp(-abs(p.y+.35)*3.);vec3 bg=vec3(.026,.037,.065)+vec3(.05,.07,.10)*horizon+line*vec3(.12,.16,.22)*grid;
 if(backgroundMode>.5){bg=mix(vec3(.36,.24,.36),vec3(.035,.10,.23),smoothstep(0.,1.,uv.y));bg+=vec3(.50,.23,.10)*exp(-abs(uv.y-.18)*8.);}
 if(backgroundMode>1.5)bg=texture(backgroundImage,vec2(uv.x,1.-uv.y)).rgb;
 if(backgroundMode<-.5)bg=vec3(0.);outColor=vec4(bg,1.);}`);
 locations=Object.fromEntries([...Object.keys(params),'u_resolution','u_pan','SceneColor','u_spine[0]','u_branchA[0]','u_branchB[0]'].map(n=>[n,gl.getUniformLocation(program,n)]));
 gl.bindVertexArray(gl.createVertexArray());selectPreset('horizon-giant');backgroundMode=1;if(query.has('preset'))selectPreset(query.get('preset'));sync();render();$('#state').textContent='运行正常 · 两通道';
 const ext=gl.getExtension('EXT_disjoint_timer_query_webgl2');let pending=null,frames=0,fpsStart=performance.now();
 function frame(now){try{
  const dt=Math.min(.05,(now-previous)/1000);previous=now;if(viewer.hidden){requestAnimationFrame(frame);return;}if(!paused){params.u_time=(params.u_time+dt)%86400;if(animation==='sequence'){params.u_sequence=Math.min(1,params.u_sequence+dt*params.u_speed/8);if(params.u_sequence===1)animation=null;sync();}if(animation==='grow'){params.u_progress=Math.min(1,params.u_progress+dt*params.u_speed*.8);if(params.u_progress===1)animation=null;sync();}if(animation==='close'){params.u_shrink=Math.min(1,params.u_shrink+dt*params.u_speed*.65);if(params.u_shrink===1)animation=null;sync();}}
  if(ext&&pending&&gl.getQueryParameter(pending,gl.QUERY_RESULT_AVAILABLE)){if(!gl.getParameter(ext.GPU_DISJOINT_EXT))gpuMs=gl.getQueryParameter(pending,gl.QUERY_RESULT)/1e6;gl.deleteQuery(pending);pending=null;}
  let q=null;if(ext&&!pending){q=gl.createQuery();gl.beginQuery(ext.TIME_ELAPSED_EXT,q);}render();if(q){gl.endQuery(ext.TIME_ELAPSED_EXT);pending=q;}
  frames++;if(now-fpsStart>1000){$('#fps').textContent=`${(frames*1000/(now-fpsStart)).toFixed(0)} FPS · ${gpuMs==null?'GPU 计时不可用':gpuMs.toFixed(2)+' ms GPU'} · ${canvas.width}×${canvas.height}`;fpsStart=now;frames=0;}
  requestAnimationFrame(frame);
 }catch(e){report(e);$('#state').textContent='渲染停止';}}
 if(!test)requestAnimationFrame(frame);return true;
})();ready.catch(report);
async function benchmark(n=8){
 const ext=gl.getExtension('EXT_disjoint_timer_query_webgl2'),results=[];let error=0;
 for(let i=0;i<n;i++){
  const q=ext?gl.createQuery():null,start=performance.now();if(q)gl.beginQuery(ext.TIME_ELAPSED_EXT,q);
  error=render(2).error;if(q)gl.endQuery(ext.TIME_ELAPSED_EXT);gl.finish();
  if(q){while(!gl.getQueryParameter(q,gl.QUERY_RESULT_AVAILABLE))await new Promise(r=>setTimeout(r,1));if(!gl.getParameter(ext.GPU_DISJOINT_EXT))results.push(gl.getQueryParameter(q,gl.QUERY_RESULT)/1e6);gl.deleteQuery(q);}else results.push(performance.now()-start);
 }
 results.sort((a,b)=>a-b);return {error,width:canvas.width,height:canvas.height,medianMs:results[Math.floor(results.length/2)]??null,measurement:ext?'GPU timer query':'CPU draw+finish wall time'};
}
window.riftLab={benchmark,ready,getRecipe:recipe,setRecipe:apply,render,setPaused:v=>paused=v,resetView:()=>pan=[0,0],panTo:(x,y)=>{if(!Number.isFinite(x)||!Number.isFinite(y))throw Error('Invalid view center');pan=[x,y];},getGeometry:()=>points,compileTest:(v,f)=>{const p=link(v,f);gl.deleteProgram(p);return true;}};

function showPanel(name){
 const panel=['preview','library','research'].includes(name)?name:'preview';
 document.querySelectorAll('[data-panel]').forEach(el=>el.hidden=el.dataset.panel!==panel);
 document.querySelectorAll('[data-tab]').forEach(el=>{el.classList.toggle('primary',el.dataset.tab===panel);el.setAttribute('aria-selected',String(el.dataset.tab===panel));});
 const frame=document.querySelector(`[data-panel="${panel}"] iframe`);if(frame&&!frame.src)frame.src=panel==='library'?'library.html?embed=1':'five-cases.html?embed=1';
 history.replaceState(null,'',location.pathname+location.search+(panel==='preview'?'':'#'+panel));
}
for(const button of document.querySelectorAll('[data-tab]'))button.onclick=()=>showPanel(button.dataset.tab);
window.addEventListener('message',event=>{const frame=document.querySelector('#catalogFrame');if(event.origin!==location.origin||![...document.querySelectorAll('.documentPanel iframe')].some(f=>f.contentWindow===event.source))return;if(event.data?.type==='rift:preview'){showPanel('preview');return;}if(event.source===frame?.contentWindow&&event.data?.type==='rift:preset'&&presets[event.data.key]){selectPreset(event.data.key);showPanel('preview');render();}});
showPanel(location.hash.slice(1));
