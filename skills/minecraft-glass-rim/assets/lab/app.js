import * as T from '../vendor/three.module.js';
const defaults={shape:'sphere',mode:'art',projection:'perspective',base:'#123a45',edge:'#7fe6ff',power:3,gain:2,baseAlpha:0.06,rimAlpha:0.85,ior:1.5,pulseAmount:0,pulseHz:0.5,stretch:false,twoSided:true,rotate:true};
const state={...defaults}; let seconds=0, paused=false, angle=0;
const holder=document.querySelector('#scene'), renderer=new T.WebGLRenderer({antialias:true,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio,2));holder.append(renderer.domElement);
const scene=new T.Scene();scene.background=new T.Color('#151719');
const camera=new T.PerspectiveCamera(38,1,0.1,30), ortho=new T.OrthographicCamera(-2,2,2,-2,0.1,30);
for(const c of [camera,ortho]){c.position.set(0,0,4.8);c.lookAt(0,0,0);}
const grid=new T.GridHelper(12,24,0x71866b,0x343d3d);grid.position.y=-1.25;scene.add(grid);
const geo={sphere:new T.SphereGeometry(1,64,48),box:new T.BoxGeometry(1.6,1.6,1.6),plane:new T.PlaneGeometry(2,2)};
const kernel=await fetch('../rim.glsl').then(r=>{if(!r.ok)throw Error('Missing rim kernel');return r.text();});
const uniforms={base:{value:new T.Color()},edge:{value:new T.Color()}};
for(const k of ['power','gain','baseAlpha','rimAlpha','ior','pulseAmount','pulseHz','seconds','fresnelMode','twoSided','orthographic'])uniforms[k]={value:0};
const material=new T.ShaderMaterial({uniforms,transparent:true,depthWrite:false,side:T.FrontSide,
vertexShader:`varying vec3 vn; varying vec3 vp; void main(){vec4 p=modelViewMatrix*vec4(position,1.); vp=p.xyz;vn=normalMatrix*normal;gl_Position=projectionMatrix*p;}`,
fragmentShader:`uniform vec3 base,edge; uniform float power,gain,baseAlpha,rimAlpha,ior,pulseAmount,pulseHz,seconds; uniform bool fresnelMode,twoSided,orthographic;varying vec3 vn,vp;\n${kernel}\nvoid main(){gl_FragColor=glassRim(vn,orthographic?vec3(0,0,1):-vp,base,edge,power,gain,baseAlpha,rimAlpha,ior,fresnelMode,twoSided,seconds,pulseAmount,pulseHz);\n#include <colorspace_fragment>\n}`});
const mesh=new T.Mesh(geo.sphere,material);scene.add(mesh);
function sync(){mesh.geometry=geo[state.shape];mesh.scale.set(state.stretch?1.4:1,state.stretch?0.65:1,1);mesh.rotation.set(0.2,angle,0);uniforms.base.value.set(state.base);uniforms.edge.value.set(state.edge);for(const k of ['power','gain','baseAlpha','rimAlpha','ior','pulseAmount','pulseHz'])uniforms[k].value=state[k];uniforms.seconds.value=seconds;uniforms.fresnelMode.value=state.mode==='fresnel';uniforms.twoSided.value=state.twoSided;uniforms.orthographic.value=state.projection==='ortho';}
function active(w,h){camera.aspect=w/h;camera.updateProjectionMatrix();ortho.left=-1.65*w/h;ortho.right=1.65*w/h;ortho.top=1.65;ortho.bottom=-1.65;ortho.updateProjectionMatrix();return state.projection==='ortho'?ortho:camera;}
function draw(){const w=holder.clientWidth,h=holder.clientHeight;renderer.setSize(w,h,false);sync();renderer.render(scene,active(w,h));}
for(const [key,label,min,max,step] of [['power','边缘收窄',0.2,10,.1],['gain','边缘亮度',0,5,.1],['baseAlpha','中心透明度',0,1,.01],['rimAlpha','边缘覆盖',0,1,.01],['ior','折射率权重',1,2.5,.01],['pulseAmount','呼吸幅度',0,1,.01],['pulseHz','呼吸频率',0,2,.01]]){
const l=document.createElement('label');l.textContent=label;const input=document.createElement('input');Object.assign(input,{type:'range',id:key,min,max,step,value:state[key]});input.title=String(state[key]);input.oninput=()=>{state[key]=Number(input.value);input.title=input.value;};l.append(input);document.querySelector('#sliders').append(l);}
for(const k of ['shape','mode','projection','base','edge','stretch','twoSided','rotate'])document.getElementById(k).onchange=e=>state[k]=e.target.type==='checkbox'?e.target.checked:e.target.value;
document.querySelector('#pause').onclick=e=>{paused=!paused;e.target.textContent=paused?'播放':'暂停';};
document.querySelector('#reset').onclick=()=>{Object.assign(state,defaults);seconds=0;angle=0;for(const k in defaults){const el=document.getElementById(k);if(el){el.value=state[k];if(el.type==='checkbox')el.checked=state[k];}}};
document.querySelector('#export').onclick=()=>{const url=URL.createObjectURL(new Blob([JSON.stringify({schema:1,...state,time:seconds,angle},null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='glass-rim.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
let drag;holder.onpointerdown=e=>{drag=e.clientX;holder.setPointerCapture(e.pointerId);};holder.onpointermove=e=>{if(drag!==undefined){angle+=(e.clientX-drag)*.01;drag=e.clientX;}};holder.onpointerup=()=>drag=undefined;
window.rimLab={state,capture(w=128,h=128,patch={}){const old={...state},t=seconds,a=angle;Object.assign(state,patch);seconds=patch.time??0;angle=patch.angle??0;sync();const target=new T.WebGLRenderTarget(w,h);renderer.setRenderTarget(target);renderer.render(scene,active(w,h));const bytes=new Uint8Array(w*h*4);renderer.readRenderTargetPixels(target,0,0,w,h,bytes);renderer.setRenderTarget(null);target.dispose();Object.assign(state,old);seconds=t;angle=a;draw();return {pixels:Array.from(bytes),error:renderer.getContext().getError()};}};
let last=performance.now();function frame(now){const dt=Math.min((now-last)/1000,.1);last=now;if(!paused){seconds+=dt;if(state.rotate&&drag===undefined)angle+=dt*.22;}draw();requestAnimationFrame(frame);}requestAnimationFrame(frame);
