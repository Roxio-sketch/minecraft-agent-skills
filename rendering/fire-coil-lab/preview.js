import * as SharedShaders from './shaders.js';
import {Simulation,playerPosition,weaponFrames,add,mul,buildFireGeometry,sceneGeometry,gridGeometry,lerp,perspective,lookAt,matmul} from './geometry.js';

const SCENE_VERTEX=`#version 300 es
precision highp float;layout(location=0) in vec3 Position;layout(location=1) in vec3 Color;
uniform mat4 ViewProjection;out vec3 vertexColor;
void main(){gl_Position=ViewProjection*vec4(Position,1.0);vertexColor=Color;}`;
const SCENE_FRAGMENT=`#version 300 es
precision highp float;in vec3 vertexColor;out vec4 fragColor;
void main(){fragColor=vec4(vertexColor,1.0);}`;
const FULLSCREEN_VERTEX=`#version 300 es
precision highp float;out vec2 screenUV;
void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);screenUV=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);}`;
const COPY_FRAGMENT=`#version 300 es
precision highp float;in vec2 screenUV;out vec4 fragColor;
uniform sampler2D SourceColor;uniform sampler2D SourceDepth;
void main(){fragColor=texture(SourceColor,screenUV);gl_FragDepth=texture(SourceDepth,screenUV).r;}`;
const EMBER_VERTEX=`#version 300 es
precision highp float;layout(location=0) in vec3 Position;layout(location=1) in vec2 UV;layout(location=2) in vec4 Meta;
uniform mat4 ViewProjection;out vec2 texCoord;out vec4 vertexMeta;
void main(){gl_Position=ViewProjection*vec4(Position,1.0);texCoord=UV;vertexMeta=Meta;}`;
const EMBER_FRAGMENT=`#version 300 es
precision highp float;in vec2 texCoord;in vec4 vertexMeta;out vec4 fragColor;
${SharedShaders.FIRE_FRAGMENT_CORE}
void main(){fragColor=shadeFire(texCoord,2.0,vertexMeta.g,vertexMeta.b,vertexMeta.a);if(fragColor.a<.004)discard;}`;
const FALLBACK_VOLUME_CORE=`vec2 sampleFireVolume(vec3 p,float t){return vec2(0.0);}vec3 fireVolumeColor(float heat){return vec3(0.0);}`;
const VOLUME_FRAGMENT=`#version 300 es
precision highp float;precision highp int;
in vec2 screenUV;out vec4 fragColor;
uniform mat4 InvViewProjection;uniform vec3 Eye;uniform vec3 Origin;uniform vec3 BoxMin;uniform vec3 BoxMax;
uniform sampler2D SceneDepth;uniform int VolumeSteps;uniform float VolumeTime;uniform float RenderIntensity;uniform float RenderOpacity;
${SharedShaders.FIRE_VOLUME_CORE||FALLBACK_VOLUME_CORE}
vec3 unproject(vec2 uv,float depth){vec4 p=InvViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);return p.xyz/p.w;}
vec2 rayBox(vec3 eye,vec3 direction){vec3 inv=1.0/(direction+vec3(0.000001));vec3 a=(BoxMin-eye)*inv,b=(BoxMax-eye)*inv;vec3 lo=min(a,b),hi=max(a,b);return vec2(max(max(lo.x,lo.y),lo.z),min(min(hi.x,hi.y),hi.z));}
void main(){
 vec3 direction=normalize(unproject(screenUV,1.0)-Eye);vec2 span=rayBox(Eye,direction);
 float nearT=max(0.0,span.x),farT=span.y;float opaqueDepth=texture(SceneDepth,screenUV).r;
 if(opaqueDepth<.999999)farT=min(farT,dot(unproject(screenUV,opaqueDepth)-Eye,direction));
 if(farT<=nearT){fragColor=vec4(0.0);return;}
 float stepLength=(farT-nearT)/float(VolumeSteps);float distance=nearT+stepLength*.5;
 DensityStep=direction*stepLength;
 vec3 radiance=vec3(0.0);float transmission=1.0;
 for(int i=0;i<512;i++){
  if(i>=VolumeSteps||distance>=farT||transmission<.008)break;
  vec2 field=sampleFireVolume(Eye+direction*distance-Origin,VolumeTime);
  float alpha=1.0-exp(-max(0.0,field.x)*RenderOpacity*stepLength*8.0);
  radiance+=transmission*alpha*fireVolumeColor(field.y)*RenderIntensity;transmission*=1.0-alpha;distance+=stepLength;
 }
 fragColor=vec4(radiance,1.0-transmission);
}`;
const hexColor=s=>[1,3,5].map(i=>parseInt(s.slice(i,i+2),16)/255);
function shader(gl,type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s));return s;}
function program(gl,vertex,fragment){const p=gl.createProgram(),vs=shader(gl,gl.VERTEX_SHADER,vertex),fs=shader(gl,gl.FRAGMENT_SHADER,fragment);gl.attachShader(p,vs);gl.attachShader(p,fs);gl.linkProgram(p);gl.deleteShader(vs);gl.deleteShader(fs);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p));return p;}
function buffer(gl,stride,attributes,data=null){const vao=gl.createVertexArray(),vbo=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,vbo);for(const [index,size,offset] of attributes){gl.enableVertexAttribArray(index);gl.vertexAttribPointer(index,size,gl.FLOAT,false,stride*4,offset*4);}if(data)gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);gl.bindVertexArray(null);return {vao,vbo};}
function uniformLocations(gl,p){const out={};for(let i=0;i<gl.getProgramParameter(p,gl.ACTIVE_UNIFORMS);i++){const active=gl.getActiveUniform(p,i),name=active.name.replace(/\[0\]$/,'');out[name]={location:gl.getUniformLocation(p,active.name),type:active.type};}return out;}
function inverseMatrix(m){const a=Array.from({length:4},(_,r)=>[m[r],m[4+r],m[8+r],m[12+r],...Array.from({length:4},(_,c)=>r===c?1:0)]);for(let c=0;c<4;c++){let best=c;for(let r=c+1;r<4;r++)if(Math.abs(a[r][c])>Math.abs(a[best][c]))best=r;[a[c],a[best]]=[a[best],a[c]];const d=a[c][c];if(Math.abs(d)<1e-12)throw new Error('相机矩阵不可逆。');for(let k=0;k<8;k++)a[c][k]/=d;for(let r=0;r<4;r++){if(r===c)continue;const f=a[r][c];for(let k=0;k<8;k++)a[r][k]-=f*a[c][k];}}return Float32Array.from(Array.from({length:16},(_,i)=>a[i%4][4+Math.floor(i/4)]));}
function conservativeScissor(vp,eye,bounds,width,height){if(eye.every((v,i)=>v>=bounds.min[i]&&v<=bounds.max[i]))return [0,0,width,height];let minX=Infinity,minY=Infinity,maxX=-Infinity,maxY=-Infinity;for(let x=0;x<2;x++)for(let y=0;y<2;y++)for(let z=0;z<2;z++){const p=[x?bounds.max[0]:bounds.min[0],y?bounds.max[1]:bounds.min[1],z?bounds.max[2]:bounds.min[2],1],clip=Array.from({length:4},(_,r)=>p.reduce((sum,v,c)=>sum+vp[c*4+r]*v,0));if(clip[3]<=.001)return [0,0,width,height];const px=(clip[0]/clip[3]*.5+.5)*width,py=(clip[1]/clip[3]*.5+.5)*height;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);}const x=Math.max(0,Math.floor(minX)-2),y=Math.max(0,Math.floor(minY)-2),right=Math.min(width,Math.ceil(maxX)+2),top=Math.min(height,Math.ceil(maxY)+2);return [x,y,Math.max(0,right-x),Math.max(0,top-y)];}
function makeSceneTarget(gl,width,height){const color=gl.createTexture(),depth=gl.createTexture(),fbo=gl.createFramebuffer();for(const [texture,internal,format,type] of [[color,gl.RGBA8,gl.RGBA,gl.UNSIGNED_BYTE],[depth,gl.DEPTH_COMPONENT24,gl.DEPTH_COMPONENT,gl.UNSIGNED_INT]]){gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,internal,width,height,0,format,type,null);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);}gl.bindFramebuffer(gl.FRAMEBUFFER,fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,color,0);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.TEXTURE_2D,depth,0);if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw new Error('场景颜色/深度缓冲创建失败。');gl.bindFramebuffer(gl.FRAMEBUFFER,null);return {fbo,color,depth,width,height};}
export class FirePreview{
 constructor(canvas,recipe,onFrame){
  this.canvas=canvas;this.gl=canvas.getContext('webgl2',{alpha:false,antialias:false,powerPreference:'high-performance'});if(!this.gl)throw new Error('当前浏览器不支持 WebGL2。');
  const gl=this.gl;this.recipe=recipe;this.simulation=new Simulation(recipe);this.onFrame=onFrame;this.paused=false;this.moving=false;this.firstPerson=false;this.layers={main:true,wisp:true,ember:true,model:true};this.accumulator=0;this.lastFrame=null;this.fps=0;this.stats={particles:0,vertices:0,draws:0,glerror:0,spawnRejected:0,time:0};this.camera={yaw:.56,pitch:.15,distance:4.8};this.volumeReady=!!SharedShaders.FIRE_VOLUME_CORE;
  this.sceneProgram=program(gl,SCENE_VERTEX,SCENE_FRAGMENT);this.copyProgram=program(gl,FULLSCREEN_VERTEX,COPY_FRAGMENT);this.volumeProgram=program(gl,FULLSCREEN_VERTEX,VOLUME_FRAGMENT);this.emberProgram=program(gl,EMBER_VERTEX,EMBER_FRAGMENT);this.sceneBuffer=buffer(gl,6,[[0,3,0],[1,3,3]]);this.gridData=gridGeometry();this.gridBuffer=buffer(gl,6,[[0,3,0],[1,3,3]],this.gridData);this.emberBuffer=buffer(gl,9,[[0,3,0],[1,2,3],[2,4,5]]);this.emberCapacity=0;this.screenVAO=gl.createVertexArray();this.sceneVP=gl.getUniformLocation(this.sceneProgram,'ViewProjection');this.copyUniforms=uniformLocations(gl,this.copyProgram);this.volumeUniforms=uniformLocations(gl,this.volumeProgram);this.emberUniforms=uniformLocations(gl,this.emberProgram);this.sceneTarget=null;
  gl.disable(gl.CULL_FACE);gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.clearColor(.025,.031,.044,1);this.bindCamera();this.draw=this.draw.bind(this);requestAnimationFrame(this.draw);
 }
 bindCamera(){let dragging=false,last=[0,0];this.canvas.addEventListener('pointerdown',e=>{dragging=true;last=[e.clientX,e.clientY];this.canvas.setPointerCapture(e.pointerId);});this.canvas.addEventListener('pointermove',e=>{if(!dragging)return;this.camera.yaw-=(e.clientX-last[0])*.006;this.camera.pitch=Math.max(-.7,Math.min(1.15,this.camera.pitch+(e.clientY-last[1])*.006));last=[e.clientX,e.clientY];});const stop=()=>dragging=false;this.canvas.addEventListener('pointerup',stop);this.canvas.addEventListener('pointercancel',stop);this.canvas.addEventListener('wheel',e=>{e.preventDefault();this.camera.distance=Math.max(.18,Math.min(12,this.camera.distance*Math.exp(e.deltaY*.001)));},{passive:false});}
 setRecipe(recipe){this.recipe=recipe;this.simulation.setRecipe(recipe);}
 setMoving(value){this.moving=!!value;this.simulation.moving=this.moving;this.reset();}
 reset(){this.simulation.reset();this.accumulator=0;this.lastFrame=null;}
 setTime(seconds){if(!Number.isFinite(seconds)||seconds<0||seconds>300)throw new Error('预览时间须在 0–300 秒范围。');this.reset();this.simulation.firstPerson=this.firstPerson;const ticks=Math.floor(seconds/.05+1e-7);for(let i=0;i<=ticks;i++)this.simulation.tick();this.accumulator=Math.max(0,seconds-ticks*.05);this.render();}
 getStats(){return {...this.stats,fps:this.fps,paused:this.paused,moving:this.moving,firstPerson:this.firstPerson,camera:{...this.camera},layers:{...this.layers}};}
 setCamera(value){for(const key of ['yaw','pitch','distance'])if(Object.hasOwn(value,key)){if(!Number.isFinite(value[key]))throw new Error('相机参数须为有限数值。');this.camera[key]=key==='distance'?Math.max(.18,Math.min(12,value[key])):key==='pitch'?Math.max(-1.3,Math.min(1.3,value[key])):value[key];}this.render();}
 resetCamera(){this.camera={yaw:.56,pitch:.15,distance:4.8};}
 draw(now){if(this.lastFrame!==null){const elapsed=Math.max(0,(now-this.lastFrame)/1000);this.fps=this.fps?this.fps*.94+(1/Math.max(.001,elapsed))*.06:1/Math.max(.001,elapsed);if(!this.paused){this.accumulator+=Math.min(.25,elapsed);while(this.accumulator>=.05){this.simulation.firstPerson=this.firstPerson;this.simulation.tick();this.accumulator-=.05;}}}this.lastFrame=now;this.render();if(this.onFrame)this.onFrame(this.getStats());requestAnimationFrame(this.draw);}
 setUniform(uniforms,name,value){const u=uniforms[name];if(!u)return;const gl=this.gl;if(typeof value==='number'){if([gl.INT,gl.BOOL,gl.SAMPLER_2D].includes(u.type))gl.uniform1i(u.location,value);else gl.uniform1f(u.location,value);}else if(u.type===gl.FLOAT_MAT4)gl.uniformMatrix4fv(u.location,false,value);else if(u.type===gl.FLOAT_VEC4)gl.uniform4fv(u.location,value);else if(u.type===gl.FLOAT_VEC3)gl.uniform3fv(u.location,value);else if(u.type===gl.FLOAT_VEC2)gl.uniform2fv(u.location,value);}
 bindTexture(unit,texture){const gl=this.gl;gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,texture);}
 render(){
  const gl=this.gl,canvas=this.canvas,dpr=Math.min(2,devicePixelRatio||1),width=Math.max(1,Math.round(canvas.clientWidth*dpr)),height=Math.max(1,Math.round(canvas.clientHeight*dpr));if(!this.sceneTarget||this.sceneTarget.width!==width||this.sceneTarget.height!==height){if(this.sceneTarget){gl.deleteTexture(this.sceneTarget.color);gl.deleteTexture(this.sceneTarget.depth);gl.deleteFramebuffer(this.sceneTarget.fbo);}canvas.width=width;canvas.height=height;this.sceneTarget=makeSceneTarget(gl,width,height);}gl.viewport(0,0,width,height);
  const sim=this.simulation,alpha=this.accumulator/.05,time=sim.time>0?sim.previousTime+this.accumulator:0,origin=sim.time>0?lerp(sim.previousOrigin,sim.origin,alpha):playerPosition(0,this.moving),at=[0,1,0],c=this.camera,eye=[Math.sin(c.yaw)*Math.cos(c.pitch)*c.distance,1+Math.sin(c.pitch)*c.distance,Math.cos(c.yaw)*Math.cos(c.pitch)*c.distance],vp=matmul(perspective(Math.PI/4,width/height,.025,50),lookAt(eye,at)),invVP=inverseMatrix(vp);
  const weaponModels=this.recipe.attachment>0?weaponFrames(time,origin,this.moving):[];
  gl.bindFramebuffer(gl.FRAMEBUFFER,this.sceneTarget.fbo);gl.depthMask(true);gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.BLEND);gl.disable(gl.SCISSOR_TEST);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.useProgram(this.sceneProgram);gl.uniformMatrix4fv(this.sceneVP,false,vp);const scene=sceneGeometry(origin,this.layers.model,weaponModels);gl.bindVertexArray(this.sceneBuffer.vao);gl.bindBuffer(gl.ARRAY_BUFFER,this.sceneBuffer.vbo);gl.bufferData(gl.ARRAY_BUFFER,scene,gl.DYNAMIC_DRAW);gl.drawArrays(gl.TRIANGLES,0,scene.length/6);gl.bindVertexArray(this.gridBuffer.vao);gl.drawArrays(gl.LINES,0,this.gridData.length/6);
  gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.useProgram(this.copyProgram);this.bindTexture(0,this.sceneTarget.color);this.bindTexture(1,this.sceneTarget.depth);this.setUniform(this.copyUniforms,'SourceColor',0);this.setUniform(this.copyUniforms,'SourceDepth',1);gl.depthFunc(gl.ALWAYS);gl.bindVertexArray(this.screenVAO);gl.drawArrays(gl.TRIANGLES,0,3);gl.depthFunc(gl.LEQUAL);
  const selected=this.layers.wisp?sim.particles.filter(p=>p.kind===1).slice(-24):[],wisps=new Float32Array(24*4),velocities=new Float32Array(24*4),meta=new Float32Array(24*4),r=this.recipe,fp=this.firstPerson?r.firstPerson:1;
  const extent=r.radius+r.waveAmplitude+.3+r.width,bounds={min:[origin[0]-extent,origin[1]-.25,origin[2]-extent],max:[origin[0]+extent,origin[1]+r.height+r.width*2+.25,origin[2]+extent]};
  const weaponOrigins=new Float32Array(8),weaponAxes=new Float32Array(8),weaponSides=new Float32Array(8);
  for(let i=0;i<weaponModels.length;i++){const f=weaponModels[i];weaponOrigins.set([...f.base.map((v,k)=>v-origin[k]),f.length],i*4);weaponAxes.set([...f.axis,f.radius],i*4);weaponSides.set([...f.side,0],i*4);const margin=f.radius+r.weaponRadius+r.weaponWidth*2+r.waveAmplitude*.25+.1;for(const at of [f.base,add(f.base,mul(f.axis,f.length+.1))])for(let k=0;k<3;k++){bounds.min[k]=Math.min(bounds.min[k],at[k]-margin);bounds.max[k]=Math.max(bounds.max[k],at[k]+margin);}}
  for(let i=0;i<selected.length;i++){const p=selected[i],center=lerp(p.previous,p.position,alpha),age=Math.max(0,(p.age-.05+.05*alpha)/p.life),size=p.size;wisps.set([center[0]-origin[0],center[1]-origin[1],center[2]-origin[2],size],i*4);velocities.set([...p.velocity,p.seed],i*4);meta.set([age,fp*(p.effectStrength??1),0,0],i*4);for(let k=0;k<3;k++){const margin=size*(k===1?5:3.5);bounds.min[k]=Math.min(bounds.min[k],center[k]-margin);bounds.max[k]=Math.max(bounds.max[k],center[k]+margin);}}
  const scissor=conservativeScissor(vp,eye,bounds,width,height),volumeActive=this.volumeReady&&(this.layers.main||selected.length>0)&&scissor[2]>0&&scissor[3]>0;
  gl.useProgram(this.volumeProgram);for(const [name,value] of Object.entries({BodyEnabled:r.attachment===1?0:1,WeaponCount:weaponModels.length,WeaponOrigins:weaponOrigins,WeaponAxes:weaponAxes,WeaponSides:weaponSides}))this.setUniform(this.volumeUniforms,name,value);
  if(volumeActive){gl.useProgram(this.volumeProgram);for(const [name,value] of Object.entries({InvViewProjection:invVP,Eye:eye,Origin:origin,BoxMin:bounds.min,BoxMax:bounds.max,SceneDepth:1,VolumeSteps:r.segments,VolumeTime:time,RenderIntensity:r.intensity,RenderOpacity:r.opacity,WispCount:selected.length,Wisps:wisps,WispVelocity:velocities,WispMeta:meta,VolumeStrength:Math.min(1,time/r.rampTime)*fp}))this.setUniform(this.volumeUniforms,name,value);for(const f of Object.keys(r)){const name=f[0].toUpperCase()+f.slice(1);if(f==='schema'||f==='intensity'||f==='opacity')continue;this.setUniform(this.volumeUniforms,name,f==='strands'?(this.layers.main?r.strands:0):f.endsWith('Color')?hexColor(r[f]):r[f]);}this.setUniform(this.volumeUniforms,'Time',time);gl.disable(gl.DEPTH_TEST);gl.depthMask(false);gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE_MINUS_SRC_ALPHA);gl.enable(gl.SCISSOR_TEST);gl.scissor(...scissor);gl.bindVertexArray(this.screenVAO);gl.drawArrays(gl.TRIANGLES,0,3);gl.disable(gl.SCISSOR_TEST);}
  const data=buildFireGeometry(r,time,origin,sim.particles,alpha,{eye,at},{main:false,wisp:false,ember:this.layers.ember},this.firstPerson);if(data.length){gl.useProgram(this.emberProgram);this.setUniform(this.emberUniforms,'ViewProjection',vp);this.setUniform(this.emberUniforms,'Time',time);for(const [uniform,key] of [['RiseSpeed','riseSpeed'],['Turbulence','turbulence'],['TongueLength','tongueLength'],['Erosion','erosion'],['Intensity','intensity'],['Opacity','opacity']])this.setUniform(this.emberUniforms,uniform,r[key]);for(const [uniform,key] of [['CoreColor','coreColor'],['MidColor','midColor'],['EdgeColor','edgeColor']])this.setUniform(this.emberUniforms,uniform,hexColor(r[key]));gl.enable(gl.DEPTH_TEST);gl.depthMask(false);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.bindVertexArray(this.emberBuffer.vao);gl.bindBuffer(gl.ARRAY_BUFFER,this.emberBuffer.vbo);if(data.byteLength>this.emberCapacity){this.emberCapacity=Math.max(65536,data.byteLength);gl.bufferData(gl.ARRAY_BUFFER,this.emberCapacity,gl.DYNAMIC_DRAW);}gl.bufferSubData(gl.ARRAY_BUFFER,0,data);gl.drawArrays(gl.TRIANGLES,0,data.length/9);}
  gl.bindVertexArray(null);gl.depthMask(true);gl.enable(gl.DEPTH_TEST);gl.disable(gl.BLEND);this.stats={particles:sim.particles.length,vertices:data.length/9+(volumeActive?3:0),fireVertices:data.length/9+(volumeActive?3:0),sceneVertices:scene.length/6+this.gridData.length/6,draws:3+(volumeActive?1:0)+(data.length?1:0),glerror:gl.getError(),spawnRejected:sim.spawnRejected,time,renderer:'3d-volume',volumeReady:this.volumeReady,volumePasses:volumeActive?1:0,mainRendered:volumeActive&&this.layers.main,volumeSteps:volumeActive?r.segments:0,volumePixels:volumeActive?scissor[2]*scissor[3]:0,renderedWisps:selected.length,renderedEmbers:data.length/54,simulatedWisps:sim.particles.filter(p=>p.kind===1).length};
 }
}
