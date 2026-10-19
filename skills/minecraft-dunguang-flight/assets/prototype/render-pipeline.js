/* Original WebGL2 HDR scene + emission, three-scale separable bloom and tone mapping.
   This is a browser quality study, not a Minecraft post-chain or borrowed engine code. */
(() => {'use strict';
window.DunguangRenderPipeline=function(gl){
  const floatColor=!!gl.getExtension('EXT_color_buffer_float');
  const format=floatColor?gl.RGBA16F:gl.RGBA8,type=floatColor?gl.HALF_FLOAT:gl.UNSIGNED_BYTE;
  const objects=[];
  function shader(type,src){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));objects.push(['shader',s]);return s;}
  const vertex=`#version 300 es
  precision highp float;out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.-1.,0.,1.);}`;
  function program(fragment){const p=gl.createProgram();gl.attachShader(p,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));objects.push(['program',p]);return p;}
  const blur=program(`#version 300 es
  precision highp float;in vec2 uv;uniform sampler2D source;uniform vec2 direction;out vec4 color;
  void main(){vec3 c=texture(source,uv).rgb*.19648255;
  c+=(texture(source,uv+direction*1.4117647).rgb+texture(source,uv-direction*1.4117647).rgb)*.29690696;
  c+=(texture(source,uv+direction*3.2941176).rgb+texture(source,uv-direction*3.2941176).rgb)*.09447040;
  c+=(texture(source,uv+direction*5.1764706).rgb+texture(source,uv-direction*5.1764706).rgb)*.01038136;
  color=vec4(c,1.);}`);
  const composite=program(`#version 300 es
  precision highp float;in vec2 uv;uniform sampler2D scene;uniform sampler2D bloom0;uniform sampler2D bloom1;uniform sampler2D bloom2;uniform float bloomStrength;uniform float exposure;out vec4 color;
  vec3 filmic(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
  void main(){vec3 c=texture(scene,uv).rgb;vec3 b=texture(bloom0,uv).rgb*.50+texture(bloom1,uv).rgb*.34+texture(bloom2,uv).rgb*.26;
  c+=b*bloomStrength;float vignette=1.-.15*pow(length((uv-.5)*1.18),2.);c=filmic(c*exposure)*vignette;color=vec4(pow(max(c,vec3(0)),vec3(1./2.2)),1.);}`);
  const backdrop=program(`#version 300 es
  precision highp float;in vec2 uv;uniform int backgroundMode;
  layout(location=0) out vec4 color;layout(location=1) out vec4 glow;
  void main(){
    vec3 low=vec3(.016,.022,.031),high=vec3(.043,.055,.072);
    if(backgroundMode==1){low=vec3(.20,.23,.27);high=vec3(.31,.35,.39);}
    if(backgroundMode==2){low=vec3(.002,.004,.008);high=vec3(.008,.013,.023);}
    vec3 c=mix(low,high,smoothstep(0.,1.,uv.y));
    c+=vec3(.002)*exp(-dot(uv-.5,uv-.5)*4.);
    color=vec4(c,1.);glow=vec4(0.);
  }`);
  let depthSnapshot=null;
  function depthTarget(w,h){
    const f=gl.createFramebuffer(),t=gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,gl.DEPTH_COMPONENT24,w,h,0,gl.DEPTH_COMPONENT,gl.UNSIGNED_INT,null);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    gl.bindFramebuffer(gl.FRAMEBUFFER,f);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.TEXTURE_2D,t,0);
    gl.drawBuffers([gl.NONE]);gl.readBuffer(gl.NONE);
    if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('Incomplete depth snapshot');
    return {f,t};
  }
  const quad=gl.createVertexArray();objects.push(['vao',quad]);let width=0,height=0,scene=null,levels=[];
  function texture(w,h){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,format,w,h,0,gl.RGBA,type,null);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);return t;}
  function target(w,h,count=1,depth=false){const f=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,f);const textures=[];for(let i=0;i<count;i++){const t=texture(w,h);textures.push(t);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0+i,gl.TEXTURE_2D,t,0);}gl.drawBuffers(textures.map((_,i)=>gl.COLOR_ATTACHMENT0+i));let d=null;if(depth){d=gl.createRenderbuffer();gl.bindRenderbuffer(gl.RENDERBUFFER,d);gl.renderbufferStorage(gl.RENDERBUFFER,gl.DEPTH_COMPONENT24,w,h);gl.framebufferRenderbuffer(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.RENDERBUFFER,d);}if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('Incomplete HDR framebuffer');return {f,textures,d,w,h};}
  function drop(t){if(!t)return;for(const texture of t.textures)gl.deleteTexture(texture);if(t.d)gl.deleteRenderbuffer(t.d);gl.deleteFramebuffer(t.f);}
  function resize(w,h){if(width===w&&height===h)return;width=w;height=h;drop(scene);levels.flat().forEach(drop);scene=target(w,h,2,true);if(depthSnapshot){gl.deleteTexture(depthSnapshot.t);gl.deleteFramebuffer(depthSnapshot.f);}depthSnapshot=depthTarget(w,h);levels=[];for(let i=0;i<3;i++){const factor=2**(i+1);levels.push([target(Math.max(1,Math.ceil(w/factor)),Math.max(1,Math.ceil(h/factor))),target(Math.max(1,Math.ceil(w/factor)),Math.max(1,Math.ceil(h/factor)))]);}}
  function bindTexture(program,name,texture,unit){gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,texture);gl.uniform1i(gl.getUniformLocation(program,name),unit);}
  function runBlur(source,t,dx,dy){gl.bindFramebuffer(gl.FRAMEBUFFER,t.f);gl.viewport(0,0,t.w,t.h);gl.useProgram(blur);bindTexture(blur,'source',source,0);gl.uniform2f(gl.getUniformLocation(blur,'direction'),dx,dy);gl.drawArrays(gl.TRIANGLES,0,3);}
  this.begin=(w,h,backgroundMode=0)=>{resize(w,h);gl.bindFramebuffer(gl.FRAMEBUFFER,scene.f);gl.drawBuffers([gl.COLOR_ATTACHMENT0,gl.COLOR_ATTACHMENT1]);gl.viewport(0,0,w,h);gl.clearBufferfv(gl.COLOR,0,new Float32Array([.006,.012,.022,1]));gl.clearBufferfv(gl.COLOR,1,new Float32Array([0,0,0,0]));gl.depthMask(true);gl.clear(gl.DEPTH_BUFFER_BIT);
    gl.disable(gl.BLEND);gl.disable(gl.DEPTH_TEST);gl.bindVertexArray(quad);gl.useProgram(backdrop);
    gl.uniform1i(gl.getUniformLocation(backdrop,'backgroundMode'),backgroundMode);gl.drawArrays(gl.TRIANGLES,0,3);
  };
  this.captureDepth=()=>{
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER,scene.f);gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,depthSnapshot.f);
    gl.blitFramebuffer(0,0,width,height,0,0,width,height,gl.DEPTH_BUFFER_BIT,gl.NEAREST);
    gl.bindFramebuffer(gl.FRAMEBUFFER,scene.f);gl.drawBuffers([gl.COLOR_ATTACHMENT0,gl.COLOR_ATTACHMENT1]);gl.viewport(0,0,width,height);
    return depthSnapshot.t;
  };
  this.finish=({bloom=1.2,exposure=1.05}={})=>{gl.disable(gl.DEPTH_TEST);gl.depthMask(false);gl.disable(gl.BLEND);gl.bindVertexArray(quad);let input=scene.textures[1];for(const [a,b] of levels){runBlur(input,a,1.45/a.w,0);runBlur(a.textures[0],b,0,1.45/b.h);input=b.textures[0];}gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,width,height);gl.useProgram(composite);bindTexture(composite,'scene',scene.textures[0],0);levels.forEach((pair,i)=>bindTexture(composite,'bloom'+i,pair[1].textures[0],i+1));gl.uniform1f(gl.getUniformLocation(composite,'bloomStrength'),bloom);gl.uniform1f(gl.getUniformLocation(composite,'exposure'),exposure);gl.drawArrays(gl.TRIANGLES,0,3);gl.activeTexture(gl.TEXTURE0);gl.depthMask(true);};
  this.info=()=>({hdr:floatColor,format:floatColor?'RGBA16F':'RGBA8 fallback',depthSnapshot:true,background:'three neutral studio backgrounds',bloom:'independent emission MRT + three-scale separable blur',levels:levels.map(pair=>[pair[0].w,pair[0].h])});
  this.dispose=()=>{if(depthSnapshot){gl.deleteTexture(depthSnapshot.t);gl.deleteFramebuffer(depthSnapshot.f);}drop(scene);levels.flat().forEach(drop);for(const [kind,obj] of objects){if(kind==='shader')gl.deleteShader(obj);if(kind==='program')gl.deleteProgram(obj);if(kind==='vao')gl.deleteVertexArray(obj);}};
};
})();
