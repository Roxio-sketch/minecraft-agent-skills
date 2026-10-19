const canvas=document.querySelector('canvas'),gl=canvas.getContext('webgl2',{alpha:false,antialias:true});if(!gl)throw Error('需要 WebGL2');
function program(v,f){const p=gl.createProgram();for(const [type,src] of [[gl.VERTEX_SHADER,v],[gl.FRAGMENT_SHADER,f]]){const sh=gl.createShader(type);gl.shaderSource(sh,src);gl.compileShader(sh);if(!gl.getShaderParameter(sh,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(sh));gl.attachShader(p,sh)}gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p}
const vs=`#version 300 es
precision highp float;layout(location=0) in vec3 pos;layout(location=1) in vec2 tex;uniform float angle,aspect;out vec2 uv;
void main(){vec3 p=pos;float c=cos(angle),s=sin(angle);p.xz=mat2(c,-s,s,c)*p.xz;p.yz=mat2(.94,-.342,.342,.94)*p.yz;p.z+=30.;gl_Position=vec4(p.x*1.6/aspect,p.y*1.6,p.z*.98-.2,p.z);uv=tex;}`;
const effect=program(vs,`#version 300 es
precision highp float;in vec2 uv;uniform sampler2D atlas;uniform float frame,fade,gain;out vec4 frag;
void main(){vec4 t=texture(atlas,vec2(uv.x,(uv.y+frame)/8.));frag=vec4(t.rgb*gain,t.a*fade);}`);
const ground=program(vs,`#version 300 es
precision highp float;in vec2 uv;out vec4 frag;void main(){vec2 p=uv*24.;vec2 g=abs(fract(p-.5)-.5)/fwidth(p);float line=1.-min(min(g.x,g.y),1.);frag=vec4(mix(vec3(.035,.055,.08),vec3(.09,.14,.19),line),1);}`);
const vao=gl.createVertexArray();gl.bindVertexArray(vao);const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,3,gl.FLOAT,false,20,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,2,gl.FLOAT,false,20,12);
const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);for(const p of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,p,gl.LINEAR);for(const p of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,p,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([0,0,0,0]));const img=new Image();img.onload=()=>{gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img)};img.src='sword-qi-atlas.png';
const wakeTexture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,wakeTexture);for(const p of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,p,gl.LINEAR);for(const p of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,p,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([0,0,0,0]));const wakeImage=new Image();wakeImage.onload=()=>{gl.bindTexture(gl.TEXTURE_2D,wakeTexture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,wakeImage)};wakeImage.src='gold-wake-atlas.png';
function draw(p,data,fade=1,frame=0,gain=1){gl.useProgram(p);gl.uniform1f(gl.getUniformLocation(p,'angle'),Number(document.querySelector('#angle').value));gl.uniform1f(gl.getUniformLocation(p,'aspect'),canvas.width/canvas.height);gl.uniform1f(gl.getUniformLocation(p,'fade'),fade);gl.uniform1f(gl.getUniformLocation(p,'frame'),frame);gl.uniform1f(gl.getUniformLocation(p,'gain'),gain);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);gl.drawArrays(gl.TRIANGLES,0,data.length/5)}
let start=performance.now(),paused=false,held=0;document.querySelector('#replay').onclick=()=>{start=performance.now();held=0;};document.querySelector('#pause').onclick=()=>{paused=!paused;if(paused)held=(performance.now()-start)/1000;else start=performance.now()-held*1000;document.querySelector('#pause').textContent=paused?'继续':'暂停';};
function render(now){const dpr=Math.min(2,devicePixelRatio);canvas.width=Math.round(canvas.clientWidth*dpr);canvas.height=Math.round(canvas.clientHeight*dpr);gl.viewport(0,0,canvas.width,canvas.height);gl.clearColor(.025,.035,.06,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.enable(gl.DEPTH_TEST);gl.depthMask(true);gl.disable(gl.BLEND);
draw(ground,[-22,-4,-14,0,0,22,-4,-14,1,0,22,-4,30,1,1,-22,-4,-14,0,0,22,-4,30,1,1,-22,-4,30,0,1]);
let age=paused?held:(now-start)/1000;if(age>3.8&&document.querySelector('#loop').checked){start=now;age=0}const t=age*20,fade=Math.min(1,t/4)*Math.max(0,Math.min(1,(60-t)/18));document.querySelector('#phase').textContent=t<60?'动画结束 → 剑气前行 → 消散':'已消散';
if(fade>0){
 const data=[],travel=-9+t*.30;
 function quad(points){for(const k of [0,1,2,0,2,3])data.push(...points[k]);}
 for(let i=0;i<96;i++){
  const pts=[];for(const [j,v] of [[i,0],[i,1],[i+1,1],[i+1,0]]){
   const u=j/96,x=u*2-1,taper=Math.pow(Math.sin(Math.PI*u),.65);
   pts.push([x*9,x*.6+3.5*(1-x*x)-v*2.2*taper,travel+.8*(1-x*x)-v*.35,u,v]);
  }quad(pts);
 }
 gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE);gl.depthMask(false);
 gl.bindTexture(gl.TEXTURE_2D,texture);draw(effect,data,fade,Math.floor(t/2)%8,1.4);
 // Trailing geometry samples earlier tip positions; it cannot extend ahead of the wave.
 const tails=[];
 for(const side of [-1,1])for(let strand=0;strand<3;strand++)for(let i=0;i<24;i++){
  const pts=[];for(const [j,v] of [[i,0],[i,1],[i+1,1],[i+1,0]]){
   const a=j/24,delay=a*Math.min(t,22+strand*3),past=t-delay,width=(.55-strand*.12)*Math.pow(1-a,1.4);
   const x=side*(8.94+.22*Math.sin(past*.35+strand)*a)+(v-.5)*width;
   const y=side*.596+.045+.35*Math.sin(past*.28+strand*1.7)*a-strand*.12*a;
   pts.push([x,y,-9+past*.30,.50+.18*a,v]);
  }for(const k of [0,1,2,0,2,3])tails.push(...pts[k]);
 }
 draw(effect,tails,fade,Math.floor(t/2)%8,1.6);
 const wake=[];
 for(let i=0;i<64;i++)for(let j=0;j<12;j++){
  const points=[];for(const [ix,iy] of [[i,j],[i,j+1],[i+1,j+1],[i+1,j]]){
   const u=ix/64,d=iy/12,x=u*2-1,past=t-d*Math.min(t,24);
   points.push([x*8.9*(1-.12*d),x*.6+3.5*(1-x*x)-1.5*d+.12*Math.sin(u*20+past*.28)*d,-9+past*.30+.8*(1-x*x)-.04,u,d]);
  }for(const k of [0,1,2,0,2,3])wake.push(...points[k]);
 }
 gl.bindTexture(gl.TEXTURE_2D,wakeTexture);draw(effect,wake,fade,Math.floor(t/2)%8,1.7);gl.bindTexture(gl.TEXTURE_2D,texture);gl.depthMask(true);
 }requestAnimationFrame(render)}requestAnimationFrame(render);
