'use strict';
const $=id=>document.getElementById(id),{defaults,validate,WaveField}=WaterCore;
let p=validate(window.WATER_RECIPE||defaults),field=new WaveField(p),running=true,raining=false,yaw=.35,pitch=.8,zoom=1,accumulator=0,ticks=0,rainAccumulator=0;
const specs=[['width','水面宽度',2,32,.5],['speed','传播速度系数',.1,1,.01],['damping','波动保留率',.9,.9995,.0005],['radius','扰动半径',.05,2,.05],['strength','扰动强度',-.5,.5,.01],['maxHeight','振幅上限',.05,1,.05]];
const material=[['opacity','透明混合强度',0,1,.01],['normalStrength','法线起伏',0,4,.05],['shininess','高光锐度',8,256,1],['reflection','天空反射强度',0,2,.05],['rainRate','雨滴 / 秒',0,20,.5]];
function controls(rows,id){for(const [key,title,min,max,step] of rows){const label=document.createElement('label');label.textContent=title;label.htmlFor=key;const output=document.createElement('output');output.id=key+'Value';label.append(output);const input=document.createElement('input');Object.assign(input,{type:'range',min,max,step:'any',id:key,value:p[key]});input.setAttribute('aria-label',title);$(id).append(label,input);input.oninput=()=>{p[key]=Number(Number(input.value).toFixed(6));output.textContent=p[key];if(key==='width')restart();else field.p=validate(p);};}}
controls(specs,'controls');controls(material,'material');
function sync(){for(const key of Object.keys(defaults)){if($(key))$(key).value=p[key];if($(key+'Value'))$(key+'Value').textContent=p[key];}$('modeTitle').textContent=p.solver==='history'?'浅水回波 · 两时刻高度':'涟漪 · 高度与速度';$('cells').textContent=`${p.grid} × ${p.grid}`;}
function restart(){field=new WaveField(p);accumulator=0;ticks=0;rainAccumulator=0;sync();}
for(const key of ['solver','boundary','grid'])$(key).onchange=()=>{p[key]=key==='grid'?Number($(key).value):$(key).value;restart();field.worldDrop(0,0);};
$('color').oninput=()=>{p.color=$('color').value;field.p=validate(p);};
const presets={default:{},rain:{radius:.16,strength:.065,rainRate:12,damping:.985,shininess:140},impact:{radius:.8,strength:.4,damping:.996,width:12},shallow:{solver:'history',boundary:'reflect',speed:.8,damping:.995,maxHeight:.12,radius:.35,strength:.08},magic:{color:'#20ddc5',opacity:.65,reflection:1.6,normalStrength:2,strength:.3,speed:.7}};
$('preset').onchange=()=>{p={...defaults,...presets[$('preset').value]};restart();field.worldDrop(0,0);};
$('drop').onclick=()=>field.worldDrop(0,0);
$('pair').onclick=()=>{field.worldDrop(-p.width*.16,0);field.worldDrop(p.width*.16,0);};
$('rain').onclick=()=>{raining=!raining;$('rain').textContent=raining?'关闭雨滴':'开启雨滴';};
$('pause').onclick=()=>{running=!running;$('pause').textContent=running?'暂停':'继续';};
$('clear').onclick=restart;
$('reset').onclick=()=>{p={...defaults};$('preset').value='default';restart();field.worldDrop(0,0);};
$('export').onclick=async()=>{
 const recipe=validate(p);
 if(location.protocol==='http:'&&['127.0.0.1','localhost'].includes(location.hostname)){
  try{const response=await fetch('/api/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(recipe)});
   if(response.ok){const result=await response.json();$('status').textContent='已保存本地参数：'+result.path+'。重命名为 recipe.json 放到 config/waterripple/。';return;}
  }catch(e){}
 }
 const blob=new Blob([JSON.stringify(recipe,null,2)+'\n'],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='recipe.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 $('status').textContent='已发起下载。若浏览器未保存文件，可点击「查看 JSON」复制保存为 recipe.json。';
};
$('import').onclick=()=>$('file').click();
$('file').onchange=async()=>{try{const file=$('file').files[0];if(!file)return;p=validate(JSON.parse(await file.text()));restart();field.worldDrop(0,0);$('status').textContent='参数导入成功。';}catch(e){$('status').textContent='导入失败：'+e.message;}finally{$('file').value='';}};
$('json').onclick=()=>{$('jsonText').value=JSON.stringify(validate(p),null,2);$('dialog').showModal();};$('close').onclick=()=>$('dialog').close();
const canvas=$('canvas'),gl=canvas.getContext('webgl',{alpha:true,antialias:true});
if(!gl){$('status').textContent='当前浏览器无法启用 WebGL，请使用支持 WebGL 的浏览器。';throw Error('WebGL unavailable');}
const vs=`attribute vec3 Position;attribute vec3 Normal;attribute vec2 UV;uniform float Yaw;uniform float Pitch;uniform float Scale;uniform float Aspect;varying vec3 viewPosition;varying vec3 viewNormal;varying vec2 uv;
vec3 rotate(vec3 p){float c=cos(Yaw),s=sin(Yaw);vec3 q=vec3(c*p.x-s*p.z,p.y,s*p.x+c*p.z);return vec3(q.x,cos(Pitch)*q.y-sin(Pitch)*q.z,sin(Pitch)*q.y+cos(Pitch)*q.z);}
void main(){vec3 q=rotate(Position);viewPosition=q+vec3(0.,0.,-12.);viewNormal=rotate(Normal);uv=UV;gl_Position=vec4(q.x*Scale,q.y*Scale*Aspect,-q.z*.02,1.);}`;
const fs=`precision highp float;varying vec3 viewPosition;varying vec3 viewNormal;varying vec2 uv;uniform vec3 Tint;uniform float Opacity;uniform float Shininess;uniform float Reflection;
void main(){vec3 n=normalize(viewNormal),v=normalize(-viewPosition);if(dot(n,v)<0.)n=-n;float f=.0204+.9796*pow(1.-max(dot(n,v),0.),5.);vec3 light=normalize(vec3(-.3,.8,.5));float shine=pow(max(dot(reflect(-light,n),v),0.),Shininess);vec3 r=reflect(-v,n);vec3 sky=mix(vec3(.12,.28,.40),vec3(.55,.85,.98),clamp(r.y*.5+.5,0.,1.));vec3 col=mix(Tint,sky,clamp(f*Reflection,0.,1.))+shine*vec3(.8,.95,1.);float edge=smoothstep(0.,.06,min(min(uv.x,uv.y),min(1.-uv.x,1.-uv.y)));gl_FragColor=vec4(col,edge*clamp(Opacity+f*.6*Reflection+shine*.25,0.,.95));}`;
function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
const program=gl.createProgram();gl.attachShader(program,compile(gl.VERTEX_SHADER,vs));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,fs));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
const vertexBuffer=gl.createBuffer(),indexBuffer=gl.createBuffer(),uniforms={};for(const name of ['Yaw','Pitch','Scale','Aspect','Tint','Opacity','Shininess','Reflection'])uniforms[name]=gl.getUniformLocation(program,name);
gl.bindBuffer(gl.ARRAY_BUFFER,vertexBuffer);for(const [name,count,offset] of [['Position',3,0],['Normal',3,12],['UV',2,24]]){const loc=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,count,gl.FLOAT,false,32,offset);}
gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);let builtGrid=0,vertices,indices;
function geometry(){const n=p.grid;vertices=new Float32Array(n*n*8);indices=new Uint16Array((n-1)*(n-1)*6);let k=0;for(let z=0;z<n-1;z++)for(let x=0;x<n-1;x++){const a=z*n+x;indices.set([a,a+n,a+n+1,a,a+n+1,a+1],k);k+=6;}gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,indexBuffer);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,indices,gl.STATIC_DRAW);builtGrid=n;}
function render(partial){const ratio=Math.min(devicePixelRatio,2),w=Math.round(canvas.clientWidth*ratio),h=Math.round(canvas.clientHeight*ratio);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}gl.viewport(0,0,w,h);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);if(builtGrid!==p.grid)geometry();const n=p.grid,cell=p.width/(n-1);let max=0;
for(let z=0;z<n;z++)for(let x=0;x<n;x++){const k=(z*n+x)*8,height=field.sample(x,z,partial),dx=(field.sample(x+1,z,partial)-field.sample(x-1,z,partial))/(2*cell)*p.normalStrength,dz=(field.sample(x,z+1,partial)-field.sample(x,z-1,partial))/(2*cell)*p.normalStrength,norm=Math.hypot(dx,1,dz);vertices[k]=(x/(n-1)-.5)*p.width;vertices[k+1]=height;vertices[k+2]=(z/(n-1)-.5)*p.width;vertices[k+3]=-dx/norm;vertices[k+4]=1/norm;vertices[k+5]=-dz/norm;vertices[k+6]=x/(n-1);vertices[k+7]=z/(n-1);max=Math.max(max,Math.abs(height));}
gl.bindBuffer(gl.ARRAY_BUFFER,vertexBuffer);gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.DYNAMIC_DRAW);gl.uniform1f(uniforms.Yaw,yaw);gl.uniform1f(uniforms.Pitch,pitch);gl.uniform1f(uniforms.Scale,1.3/p.width*zoom);gl.uniform1f(uniforms.Aspect,w/h);const rgb=parseInt(p.color.slice(1),16);gl.uniform3f(uniforms.Tint,((rgb>>16)&255)/255,((rgb>>8)&255)/255,(rgb&255)/255);for(const key of ['opacity','shininess','reflection'])gl.uniform1f(uniforms[key[0].toUpperCase()+key.slice(1)],p[key]);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,indexBuffer);gl.drawElements(gl.TRIANGLES,indices.length,gl.UNSIGNED_SHORT,0);$('amplitude').textContent=max.toFixed(3);}
let pointer=null,lastX=0,lastY=0;canvas.oncontextmenu=e=>e.preventDefault();
function inject(e){const r=canvas.getBoundingClientRect(),scale=1.3/p.width*zoom,xx=((e.clientX-r.left)/r.width*2-1)/scale,zz=- (1-(e.clientY-r.top)/r.height*2)/(scale*r.width/r.height*Math.sin(pitch)),x=xx*Math.cos(yaw)+zz*Math.sin(yaw),z=-xx*Math.sin(yaw)+zz*Math.cos(yaw);if(Math.abs(x)<p.width/2&&Math.abs(z)<p.width/2)field.worldDrop(x,z,p.radius,p.strength*(e.type==='pointermove'?.22:1));}
canvas.onpointerdown=e=>{pointer=e.button;lastX=e.clientX;lastY=e.clientY;canvas.setPointerCapture(e.pointerId);if(pointer===0)inject(e);};
canvas.onpointermove=e=>{if(pointer===2){yaw+=(e.clientX-lastX)*.008;pitch=Math.max(.3,Math.min(1.5,pitch+(e.clientY-lastY)*.006));}else if(pointer===0&&Math.hypot(e.clientX-lastX,e.clientY-lastY)>3)inject(e);lastX=e.clientX;lastY=e.clientY;};canvas.onpointerup=canvas.onpointercancel=()=>pointer=null;canvas.onwheel=e=>{e.preventDefault();zoom=Math.max(.5,Math.min(2,zoom*Math.exp(-e.deltaY*.001)));};
let last=performance.now(),frames=0,fpsTime=last;
function frame(now){const elapsed=Math.min((now-last)/1000,.1);last=now;if(running){accumulator+=elapsed;while(accumulator>=.05){if(raining)rainAccumulator+=p.rainRate/20;if(rainAccumulator>=1){rainAccumulator-=1;const a=ticks*2.399963,r=p.width*.34*Math.sqrt(((ticks*37)%101)/101);field.worldDrop(Math.cos(a)*r,Math.sin(a)*r);}field.tick();ticks++;accumulator-=.05;}}render(running?accumulator/.05:1);frames++;if(now-fpsTime>500){$('fps').textContent=Math.round(frames*1000/(now-fpsTime));frames=0;fpsTime=now;}requestAnimationFrame(frame);}
sync();field.worldDrop(0,0);requestAnimationFrame(frame);
// Read-only diagnostics for browser verification.
window.waterDiagnostics=()=>({recipe:{...p},ticks,running,raining,shaderLinked:gl.getProgramParameter(program,gl.LINK_STATUS),glError:gl.getError(),maxAmplitude:Math.max(...field.h.map(Math.abs))});

