/* Recipe material and convex coverage shared with the portable Forge reference. */
const LightningMaterial=(()=>{
 const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
 function gradientMix(along,percentage=50){
  const x=clamp(along,0,1),r=percentage/100;
  if(r<=0)return 1;if(r>=1)return 0;if(r===.5)return x;
  return x*(1-r)/(x*(1-r)+(1-x)*r);
 }
 const channels=hex=>[16,8,0].map(shift=>(parseInt(hex.slice(1),16)>>shift)&255);
 const usesGradient=(r,halo)=>r.gradientEnabled&&(r.gradientTarget==='both'||r.gradientTarget===(halo?'glow':'core'));
 const vividCore=r=>usesGradient(r,false)&&r.gradientCoreMode==='vivid';
 function color(r,halo,along){
  const base=channels(halo?r.glowColor:r.coreColor);if(!usesGradient(r,halo))return base.map(v=>v/255);
  const a=channels(r.gradientStart),b=channels(r.gradientEnd),t=gradientMix(along,r.gradientRatio);
  return base.map((v,i)=>Math.floor(v*Math.round(a[i]+(b[i]-a[i])*t)/255)/255);
 }
 function paths(segments,project){
  const groups=new Map();
  for(const s of segments){const key=s.path??0;if(!groups.has(key))groups.set(key,[]);groups.get(key).push(s);}
  const result=[];
  for(const list of groups.values()){
   const runs=[];let run=[];
   const flush=()=>{if(run.length>1)runs.push(run);run=[];};
   for(const s of list){
    const a=project(s.a),b=project(s.b);
    if(!a||!b||![...a,...b].every(Number.isFinite)){flush();continue;}
    if(Math.hypot(b[0]-a[0],b[1]-a[1])<1e-7){flush();continue;}
    if(run.length&&Math.hypot(a[0]-run.at(-1).p[0],a[1]-run.at(-1).p[1])>.01)flush();
    if(!run.length)run.push({p:a,w:s.w,along:s.along,alpha:s.alpha});
    run.push({p:b,w:s.wb??s.w,along:s.alongB,alpha:s.alpha});
   }flush();if(runs.length)result.push(runs);
  }return result;
 }
 function mesh(runs,r,halo){
  const vertices=[],extent=[Infinity,Infinity,-Infinity,-Infinity],base=r.mode==='orbit'?r.currentWidth:r.strikeWidth;
  const scale=halo?r.glowWidth/base:2,min=.75;
  const append=(p,n,u,v,coverage,round)=>{
   const rgb=color(r,halo,n.along),alpha=Math.floor(clamp(n.alpha,0,1)*255)/255;
   vertices.push(p[0],p[1],...rgb,alpha,u,v,coverage,round);
   extent[0]=Math.min(extent[0],p[0]);extent[1]=Math.min(extent[1],p[1]);
   extent[2]=Math.max(extent[2],p[0]);extent[3]=Math.max(extent[3],p[1]);
  };
  const quad=(corners,a,b,ra,rb,round=0)=>{for(const i of [0,1,2,0,2,3]){
   const end=i>=2,n=end?b:a,cov=end?rb:ra;
   append(corners[i],n,round?(i===0||i===3?-1:1):cov,round?(i<2?-1:1):(i===0||i===3?0:1),cov,round);
  }};
  for(const run of runs){
   const tangents=[],normals=[];
   for(let i=0;i+1<run.length;i++){
    const a=run[i].p,b=run[i+1].p,len=Math.hypot(b[0]-a[0],b[1]-a[1]),t=[(b[0]-a[0])/len,(b[1]-a[1])/len];
    tangents.push(t);normals.push([-t[1],t[0]]);
   }
   const radii=run.map(n=>{const native=n.w*scale*n.p[3],rendered=Math.max(min,native);return {native,rendered,coverage:native/rendered};});
   for(let i=0;i+1<run.length;i++){
    const a=run[i],b=run[i+1],sa=normals[i].map(v=>v*radii[i].rendered),sb=normals[i].map(v=>v*radii[i+1].rendered);
    const add=(p,s,k)=>[p[0]+s[0]*k,p[1]+s[1]*k];
    quad([add(a.p,sa,1),add(a.p,sa,-1),add(b.p,sb,-1),add(b.p,sb,1)],a,b,radii[i].coverage,radii[i+1].coverage);
   }
   for(let i=1;i+1<run.length;i++){
    const a=tangents[i-1],b=tangents[i],turn=a[0]*b[1]-a[1]*b[0],forward=a[0]*b[0]+a[1]*b[1];
    if(forward>0&&Math.abs(turn)<1e-7)continue;
    const n=run[i],p=n.p,rad=radii[i].rendered,cov=radii[i].coverage**2;
    quad([[p[0]-rad,p[1]-rad],[p[0]+rad,p[1]-rad],[p[0]+rad,p[1]+rad],[p[0]-rad,p[1]+rad]],n,n,cov,cov,1);
   }
  }return {vertices,extent};
 }
 return {gradientMix,usesGradient,vividCore,color,paths,mesh};
})();

/* RGBA8 scratch matches TextureTarget: MAX within each path, ADD between paths. */
class LightningGpuPreview {
 constructor(){
  this.canvas=document.createElement('canvas');
  const gl=this.gl=this.canvas.getContext('webgl2',{alpha:false,antialias:false,preserveDrawingBuffer:true});
  if(!gl)throw Error('工作台需要 WebGL2；请启用浏览器硬件加速。');
  const compile=(kind,source)=>{const s=gl.createShader(kind);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
  const program=(vs,fs)=>{const p=gl.createProgram();gl.attachShader(p,compile(gl.VERTEX_SHADER,vs));gl.attachShader(p,compile(gl.FRAGMENT_SHADER,fs));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;};
  const lines=a=>a.join('\n');
  this.stroke=program(lines(['#version 300 es',
   'layout(location=0)in vec2 Position;layout(location=1)in vec4 Color;layout(location=2)in vec2 UV;layout(location=3)in float Coverage;layout(location=4)in float Round;',
   'uniform vec2 Size;out vec4 color;out vec2 uv;out float coverage;flat out float roundJoint;',
   'void main(){gl_Position=vec4(Position.x/Size.x*2.-1.,1.-Position.y/Size.y*2.,0,1);color=Color;uv=UV;coverage=Coverage;roundJoint=Round;}']),
  lines(['#version 300 es',
   'precision highp float;in vec4 color;in vec2 uv;in float coverage;flat in float roundJoint;uniform float Gain;uniform float Halo;uniform float VividCore;out vec4 outColor;',
   'void main(){float d=roundJoint>.5?length(uv):abs(uv.y*2.-1.);if(d>=1.)discard;float profile=Halo>.5?max(0.,exp(-8.*d*d)-exp(-8.)):1.-smoothstep(.15,.75,d);float a=color.a*coverage*profile;if(Halo<.5&&VividCore>.5){float top=max(color.r,max(color.g,color.b));float opacity=clamp(a*Gain*top,0.,1.);outColor=vec4(top>0.?color.rgb/top*opacity:vec3(0.),opacity);}else{vec3 e=(Halo>.5?clamp(color.rgb*Gain,0.,1.):color.rgb*Gain)*a;outColor=vec4(e,0);}}']));
  this.copy=program(lines(['#version 300 es',
   'out vec2 uv;void main(){vec2 p=vec2(gl_VertexID==1?3.:-1.,gl_VertexID==2?3.:-1.);gl_Position=vec4(p,0,1);uv=p*.5+.5;}']),
  lines(['#version 300 es',
   'precision highp float;in vec2 uv;uniform sampler2D Image;uniform float Scene;out vec4 outColor;void main(){outColor=Scene<0.?texture(Image,uv):vec4(texture(Image,uv).rgb,Scene);}']));
  this.buffer=gl.createBuffer();this.vao=gl.createVertexArray();gl.bindVertexArray(this.vao);gl.bindBuffer(gl.ARRAY_BUFFER,this.buffer);
  for(const [location,count,offset] of [[0,2,0],[1,4,2],[2,2,6],[3,1,8],[4,1,9]]){gl.enableVertexAttribArray(location);gl.vertexAttribPointer(location,count,gl.FLOAT,false,40,offset*4);}
  this.texture=gl.createTexture();this.sceneTexture=gl.createTexture();this.fbo=gl.createFramebuffer();
 }
 begin(width,height){
  const gl=this.gl;
  if(!this.allocated||this.canvas.width!==width||this.canvas.height!==height){
   this.canvas.width=width;this.canvas.height=height;gl.bindTexture(gl.TEXTURE_2D,this.texture);
   gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
   gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
   gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
   gl.bindFramebuffer(gl.FRAMEBUFFER,this.fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,this.texture,0);
   if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('光晕缓冲不完整');
   this.allocated=true;
  }
  gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,width,height);gl.disable(gl.SCISSOR_TEST);gl.disable(gl.DEPTH_TEST);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);
  this.stats={paths:0,vertices:0,glError:0};this.sceneLoaded=false;
 }
 seedScene(ctx){
  if(this.sceneLoaded)return;
  const gl=this.gl;gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.disable(gl.BLEND);gl.useProgram(this.copy);
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,this.sceneTexture);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,ctx.canvas);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  gl.uniform1i(gl.getUniformLocation(this.copy,'Image'),0);gl.uniform1f(gl.getUniformLocation(this.copy,'Scene'),1);gl.drawArrays(gl.TRIANGLES,0,3);this.sceneLoaded=true;
 }
 upload(mesh,r,halo){
  const gl=this.gl;gl.useProgram(this.stroke);gl.bindVertexArray(this.vao);gl.bindBuffer(gl.ARRAY_BUFFER,this.buffer);
  gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(mesh.vertices),gl.STREAM_DRAW);
  gl.uniform2f(gl.getUniformLocation(this.stroke,'Size'),this.canvas.width,this.canvas.height);
  gl.uniform1f(gl.getUniformLocation(this.stroke,'Gain'),halo?r.glowGain:r.coreGain);
  gl.uniform1f(gl.getUniformLocation(this.stroke,'Halo'),halo?1:0);
  gl.uniform1f(gl.getUniformLocation(this.stroke,'VividCore'),!halo&&LightningMaterial.vividCore(r)?1:0);
  gl.drawArrays(gl.TRIANGLES,0,mesh.vertices.length/10);this.stats.vertices+=mesh.vertices.length/10;
 }
 draw(segments,r,project){
  const gl=this.gl,groups=LightningMaterial.paths(segments,project);
  gl.enable(gl.BLEND);
  // Union segment/joint overlaps before compositing either material.
  for(const halo of [true,false])for(const runs of groups){
   if((halo?r.glowGain:r.coreGain)<=0)continue;
   const mesh=LightningMaterial.mesh(runs,r,halo),e=mesh.extent,w=this.canvas.width,h=this.canvas.height;
   const x=Math.max(0,Math.floor(e[0]-1)),y=Math.max(0,Math.floor(h-e[3]-1)),right=Math.min(w,Math.ceil(e[2]+1)),top=Math.min(h,Math.ceil(h-e[1]+1));
   if(right<=x||top<=y)continue;
   gl.bindFramebuffer(gl.FRAMEBUFFER,this.fbo);gl.enable(gl.SCISSOR_TEST);gl.scissor(x,y,right-x,top-y);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);
   gl.blendFunc(gl.ONE,gl.ONE);gl.blendEquation(gl.MAX);this.upload(mesh,r,halo);
   gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.blendEquation(gl.FUNC_ADD);gl.useProgram(this.copy);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,this.texture);
   gl.blendFuncSeparate(gl.ONE,!halo&&LightningMaterial.vividCore(r)?gl.ONE_MINUS_SRC_ALPHA:gl.ONE,gl.ZERO,gl.ONE);
   gl.uniform1i(gl.getUniformLocation(this.copy,'Image'),0);gl.uniform1f(gl.getUniformLocation(this.copy,'Scene'),-1);gl.drawArrays(gl.TRIANGLES,0,3);gl.disable(gl.SCISSOR_TEST);
   if(!halo)this.stats.paths++;
  }
 }
 finish(ctx){
  const gl=this.gl;gl.disable(gl.SCISSOR_TEST);this.stats.glError=gl.getError();
  if(this.stats.glError)throw Error('WebGL 错误 '+this.stats.glError);
  ctx.globalCompositeOperation=this.sceneLoaded?'source-over':'lighter';ctx.drawImage(this.canvas,0,0);ctx.globalCompositeOperation='source-over';
 }
}
if(typeof module!=='undefined')module.exports={LightningMaterial};
