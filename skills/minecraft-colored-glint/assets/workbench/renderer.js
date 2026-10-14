import {PATTERN_GLSL} from './patterns.js';
import {GUARD_LOOK_GLSL} from './guard-look.js';
import {GUARD_VARIANTS_GLSL} from './guard-variants.js';

const VERTEX = `#version 300 es
in vec3 aPosition; in vec3 aNormal; in vec2 aUV; in float aLayer;
uniform mat4 uProjection, uModel;
out vec3 vNormal; out vec2 vUV; out vec3 vLocalPosition; out vec3 vLocalNormal; flat out int vLayer;
void main(){vec4 p=uModel*vec4(aPosition,1.);vNormal=mat3(uModel)*aNormal;vUV=aUV;vLocalPosition=aPosition;vLocalNormal=aNormal;vLayer=int(aLayer);gl_Position=uProjection*p;}`;

const FRAGMENT = `#version 300 es
precision highp float;
in vec3 vNormal; in vec2 vUV; in vec3 vLocalPosition; in vec3 vLocalNormal; flat in int vLayer;
out vec4 fragColor;
uniform sampler2D uGlint, uArmor1, uArmor2;
uniform int uStyle, uMaterial, uEnabled, uHasArmor;
uniform vec3 uColor, uColor2;
uniform vec3 uHitPosition;
uniform vec2 uSheenFrame;
uniform float uIntensity, uSpeed, uScale, uWidth, uAngle, uSecondary, uTime, uHitAge;
${PATTERN_GLSL}
${GUARD_LOOK_GLSL}
${GUARD_VARIANTS_GLSL}
void main(){
 vec2 tile=floor(vUV*32.);
 float noise=fract(sin(dot(tile,vec2(127.1,311.7)))*43758.5453);
 vec3 base=uMaterial==1?vec3(.16,.18,.22):(uMaterial==2?vec3(.57,.41,.18):vec3(.42,.46,.51));
 base*=.86+noise*.22;
 float bevel=step(.96,max(abs(vUV.x-.5),abs(vUV.y-.5))*2.);
 base=mix(base,base*1.16,bevel);
 if(uHasArmor==1){vec4 texel=vLayer==0?texture(uArmor1,vUV):texture(uArmor2,vUV);if(texel.a<.1)discard;base=texel.rgb;}
 float light=.55+.45*max(dot(normalize(vNormal),normalize(vec3(-.4,.7,1.))),0.);
 vec3 rgb=base*light;
 if(uEnabled==1){
   vec3 sheenPosition=vLocalPosition;sheenPosition.y=(sheenPosition.y-uSheenFrame.x)*uSheenFrame.y;
   vec3 glow=uStyle>=9?guardRichSheen(uStyle,sheenPosition,uTime):(uStyle==8?guardWaterSheen(sheenPosition,uTime):glintColor(vUV,uTime));
   // Surface-only preview: no glow on the air, no replacement of the base texture.
   if(uStyle>=8){rgb=guardSurfaceLight(rgb,glow);}else{rgb=1.-(1.-rgb)*exp(-max(glow,vec3(0.)));}
   rgb+=uStyle>=9?guardRichResponse(uStyle,vLocalPosition,vLocalNormal,uHitPosition,uHitAge):guardImpact(vLocalPosition,vLocalNormal,uHitPosition,uHitAge);
 }
 fragColor=vec4(clamp(rgb,0.,1.),1.);
}`;

// Coordinates and UV rectangles follow vanilla HumanoidModel cuboids.
// Geometry is centered on the torso; y/z are inverted from Minecraft model space.
function armorBoxes(shape){
 const body={o:[-4,0,-2],s:[8,12,4],uv:[16,16],inflate:1};
 const arms=[{o:[-8,0,-2],s:[4,12,4],uv:[40,16],inflate:1},{o:[4,0,-2],s:[4,12,4],uv:[40,16],inflate:1,mirror:true}];
 const head={o:[-4,-8,-4],s:[8,8,8],uv:[0,0],inflate:1};
 const legs=[{o:[-3.9,12,-2],s:[4,12,4],uv:[0,16],inflate:1},{o:[-.1,12,-2],s:[4,12,4],uv:[0,16],inflate:1,mirror:true}];
 const leggings=[{...body,inflate:.5,layer:1},...legs.map(b=>({...b,inflate:.5,layer:1}))];
 let boxes=shape==='chest'?[body,...arms]:shape==='helmet'?[head]:shape==='boots'?legs:shape==='legs'?leggings:[head,body,...arms,...leggings,...legs];
 const offset=shape==='helmet'?-.80:shape==='boots'?.86:shape==='legs'?.28:0;
 return boxes.map(b=>({...b,offset}));
}
function mesh(shape){
 const vertices=[];
 const boxes=shape==='sword'?[
  {o:[-1.05,-5,-.5],s:[2.1,18,1],uv:[16,16],inflate:0},
  {o:[-4.5,12,-.8],s:[9,1.5,1.6],uv:[16,16],inflate:0},
  {o:[-.8,13,-.75],s:[1.6,6,1.5],uv:[16,16],inflate:0},
  {o:[-1.3,19,-1],s:[2.6,1.4,2],uv:[16,16],inflate:0},
 ]:armorBoxes(shape);
 for(const b of boxes){
  const [x,y,z]=b.o,[w,h,d]=b.s,i=b.inflate,[u,v]=b.uv;
  const x0=x-i,x1=x+w+i,y0=y-i,y1=y+h+i,z0=z-i,z1=z+d+i;
  const faces=[
   {n:[0,0,1],corners:[[x0,y1,z0],[x1,y1,z0],[x1,y0,z0],[x0,y0,z0]],r:[u+d,v+d,w,h]},
   {n:[0,0,-1],corners:[[x1,y1,z1],[x0,y1,z1],[x0,y0,z1],[x1,y0,z1]],r:[u+d+w+d,v+d,w,h]},
   {n:[1,0,0],corners:[[x1,y1,z0],[x1,y1,z1],[x1,y0,z1],[x1,y0,z0]],r:[b.mirror?u:u+d+w,v+d,d,h]},
   {n:[-1,0,0],corners:[[x0,y1,z1],[x0,y1,z0],[x0,y0,z0],[x0,y0,z1]],r:[b.mirror?u+d+w:u,v+d,d,h]},
   {n:[0,1,0],corners:[[x0,y0,z0],[x1,y0,z0],[x1,y0,z1],[x0,y0,z1]],r:[u+d,v,w,d]},
   {n:[0,-1,0],corners:[[x0,y1,z1],[x1,y1,z1],[x1,y1,z0],[x0,y1,z0]],r:[u+d+w,v+d,w,-d]},
  ];
  for(const face of faces){
   const [ru,rv,rw,rh]=face.r;let uv=[[ru,rv+rh],[ru+rw,rv+rh],[ru+rw,rv],[ru,rv]];
   if(b.mirror)uv=uv.map(([uu,vv])=>[ru+rw-(uu-ru),vv]);
   for(const j of [0,1,2,0,2,3]){const c=face.corners[j];vertices.push(c[0]*.08,(6-c[1])*.08+(b.offset||0),-c[2]*.08,...face.n,uv[j][0]/64,uv[j][1]/32,b.layer||0);}
  }
 }
 return new Float32Array(vertices);
}

function ortho(width,height,shape){
 const aspect=width/height,minHeight={chest:.78,helmet:.72,boots:.80,legs:1.25,suit:1.55,sword:1.25}[shape]||1.43,halfH=Math.max(minHeight,(shape==='suit'?.98:.81)/aspect),halfW=halfH*aspect;
 return new Float32Array([1/halfW,0,0,0,0,1/halfH,0,0,0,0,-.25,0,0,0,0,1]);
}
function rotation(yaw,pitch){
 const c=Math.cos(yaw),s=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
 return new Float32Array([c,0,-s,0,s*sp,cp,c*sp,0,s*cp,-sp,c*cp,0,0,0,0,1]);
}
function rgb(hex){return [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255);}

export function createRenderer(canvas){
 const gl=canvas.getContext('webgl2',{alpha:true,antialias:true,preserveDrawingBuffer:true});
 if(!gl)throw new Error('浏览器不支持 WebGL2，请使用支持硬件加速的浏览器。');
 const shaders=[];
 function compile(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s));shaders.push(s);return s;}
 const program=gl.createProgram();gl.attachShader(program,compile(gl.VERTEX_SHADER,VERTEX));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,FRAGMENT));gl.linkProgram(program);
 if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
 shaders.forEach(s=>gl.deleteShader(s));gl.useProgram(program);
 const uniforms={};for(const key of ['Projection','Model','Glint','Armor1','Armor2','Style','Material','Enabled','HasArmor','Color','Color2','Intensity','Speed','Scale','Width','Angle','Secondary','Time','HitAge','HitPosition','SheenFrame'])uniforms[key]=gl.getUniformLocation(program,'u'+key);
 const vao=gl.createVertexArray();gl.bindVertexArray(vao);const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
 for(const [name,size,offset] of [['aPosition',3,0],['aNormal',3,12],['aUV',2,24],['aLayer',1,32]]){const loc=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,36,offset);}
 const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([0,0,0,255]));
 gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
 gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.REPEAT);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.REPEAT);
 const image=new Image();const ready=new Promise((resolve,reject)=>{image.onload=()=>{gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);resolve();};image.onerror=()=>reject(new Error('无法读取流光纹理 glint.png'));});image.src='glint.png';
 const armorTextures={};const textureTasks=[];
 for(const material of ['iron','diamond','netherite','gold']){armorTextures[material]=[];for(const layer of [1,2]){const armor=gl.createTexture();armorTextures[material].push(armor);gl.bindTexture(gl.TEXTURE_2D,armor);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([128,128,128,255]));gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);const img=new Image();textureTasks.push(new Promise(resolve=>{img.onload=()=>{gl.bindTexture(gl.TEXTURE_2D,armor);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);resolve(true);};img.onerror=()=>resolve(false);img.src=`textures/${material}_${layer}.png`;}));}}
 let hasArmor=false;const allReady=Promise.all([ready,...textureTasks]).then(results=>{hasArmor=results.slice(1).every(Boolean);});
 let currentShape='',count=0,sheenFrame=[0,1];
 gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.enable(gl.CULL_FACE);gl.cullFace(gl.BACK);
 function render(config,scene,time,hitAge){
   const r=canvas.getBoundingClientRect(),dpr=Math.min(devicePixelRatio||1,2);const w=Math.round(r.width*dpr),h=Math.round(r.height*dpr);
   if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
   gl.useProgram(program);gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
   if(scene.shape!==currentShape){const data=mesh(scene.shape);gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);count=data.length/9;currentShape=scene.shape;let minY=Infinity,maxY=-Infinity;for(let j=1;j<data.length;j+=9){minY=Math.min(minY,data[j]);maxY=Math.max(maxY,data[j]);}sheenFrame=[(minY+maxY)/2,1.12/Math.max(maxY-minY,.001)];}
   const material=scene.material==='dark'?'netherite':scene.material;const armor=armorTextures[material]||armorTextures.iron;gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,armor[0]);gl.activeTexture(gl.TEXTURE2);gl.bindTexture(gl.TEXTURE_2D,armor[1]);gl.uniform1i(uniforms.Armor1,1);gl.uniform1i(uniforms.Armor2,2);gl.uniform1i(uniforms.HasArmor,hasArmor&&scene.shape!=='sword'?1:0);gl.uniform1i(uniforms.Glint,0);gl.uniform1i(uniforms.Style,config.style);gl.uniform1i(uniforms.Material,{iron:0,dark:1,netherite:1,diamond:0,gold:2}[scene.material]??0);
   gl.uniform3fv(uniforms.Color,rgb(config.color));gl.uniform3fv(uniforms.Color2,rgb(config.color2));
   gl.uniform2fv(uniforms.SheenFrame,sheenFrame);
   for(const k of ['intensity','speed','scale','width','angle','secondary'])gl.uniform1f(uniforms[k[0].toUpperCase()+k.slice(1)],config[k]);
   gl.uniform1f(uniforms.Time,time);gl.uniform1f(uniforms.HitAge,hitAge);gl.uniform3fv(uniforms.HitPosition,({chest:[.18,.16,.24],suit:[.18,.16,.24],helmet:[.10,.10,.40],boots:[-.15,-.26,.24],legs:[-.14,-.10,.20],sword:[0,0,.04]}[scene.shape]||[.18,.16,.24]));
   gl.uniformMatrix4fv(uniforms.Model,false,rotation(scene.yaw+(scene.orbit?time*.13:0),scene.pitch));
   gl.clearColor(0,0,0,0);gl.viewport(0,0,w,h);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
   const left=Math.floor(w/2);
   for(let i=0;i<2;i++){
     const viewWidth=i?w-left:left;gl.viewport(i?left:0,0,viewWidth,h);
     gl.uniformMatrix4fv(uniforms.Projection,false,ortho(viewWidth,h,scene.shape));gl.uniform1i(uniforms.Enabled,i);gl.drawArrays(gl.TRIANGLES,0,count);
   }
 }
 return {ready:allReady,render,hasArmor:()=>hasArmor,glError:()=>gl.getError(),sources:{vertex:VERTEX,fragment:FRAGMENT}};
}
