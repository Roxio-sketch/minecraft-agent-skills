// Original synthetic geometry validation. Real WebGL depth, no Minecraft runtime claim.
const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright');
const root=__dirname,out=process.env.LE_EVIDENCE_DIR||path.join(root,'evidence');fs.mkdirSync(out,{recursive:true});
const kernel=fs.readFileSync(path.join(root,'kernel.glsl'),'utf8');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.LE_BROWSER||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true,args:['--enable-webgl']});
 try {
  const page=await browser.newPage({viewport:{width:960,height:590}});
  await page.setContent('<body style="margin:0;background:#10141c;color:#ddd;font:16px monospace"><div style="height:36px;padding:7px 12px;box-sizing:border-box">SYNTHETIC GEOMETRY / DEPTH RECONSTRUCTION — NOT MINECRAFT</div><canvas id="c" width="960" height="540"></canvas>');
  await page.evaluate(kernel=>{
   const canvas=document.getElementById('c'),gl=canvas.getContext('webgl2',{antialias:false,preserveDrawingBuffer:true});
   if(!gl||!gl.getExtension('EXT_color_buffer_float'))throw Error('Float framebuffer unavailable');
   const W=canvas.width,H=canvas.height;
   const compile=(kind,src)=>{const s=gl.createShader(kind);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
   const program=(vs,fs)=>{const p=gl.createProgram();gl.attachShader(p,compile(gl.VERTEX_SHADER,vs));gl.attachShader(p,compile(gl.FRAGMENT_SHADER,fs));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;};
   const pre='#version 300 es\nprecision highp float;precision highp int;\n';
   const geo=program(pre+`layout(location=0) in vec3 p;layout(location=1) in vec3 normal;layout(location=2) in vec3 color;layout(location=3) in float kind;
uniform mat4 VP;uniform vec3 Eye;out vec3 wp,n,c;flat out float k;
void main(){wp=p;n=normal;c=color;k=kind;gl_Position=VP*vec4(p-Eye,1);}`,
pre+`in vec3 wp,n,c;flat in float k;layout(location=0) out vec4 C;layout(location=1) out vec4 P;layout(location=2) out vec4 N;
void main(){C=vec4(c*(.68+.32*max(dot(normalize(n),normalize(vec3(.4,1,.25))),0.0)),1);P=vec4(wp,1);N=vec4(n,k);}`);
   const full=pre+'out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.-1.,0,1);}';
   const effect=program(full,pre+`in vec2 uv;uniform sampler2D ColorTex,DepthTex,NormalTex;uniform mat4 InvVP;uniform vec3 Eye;
uniform int Count,Shape[8],Material[8],Target;uniform vec3 Center[8],Extent[8],Endpoint[8];uniform float Time;
layout(location=0) out vec4 C;layout(location=1) out vec4 D;
`+kernel+`void main(){vec4 base=texture(ColorTex,uv);float depth=texture(DepthTex,uv).r;vec4 nk=texture(NormalTex,uv);C=base;D=vec4(0);
if(depth>=1.0||nk.a<.5)return;
vec4 h=InvVP*vec4(uv*2.0-1.0,depth*2.0-1.0,1);vec3 relative=h.xyz/h.w;vec3 p=relative+Eye;float mask=0.;
for(int i=0;i<8;i++){if(i>=Count)break;if(Target==1&&nk.a>1.5)continue;
 vec3 q=relative-(Center[i]-Eye);float d=leSdf(q,Shape[i],Extent[i],Endpoint[i]);float m=leCoverage(d,.45);
 C.rgb=mix(C.rgb,leMaterial(p,normalize(nk.xyz),Material[i],Time),m);mask=max(mask,m);}
D=vec4(p,mask);}`);
   const display=program(full,pre+'in vec2 uv;uniform sampler2D Image;out vec4 c;void main(){c=texture(Image,uv);}');
   function texture(format,type){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,format,W,H,0,format===gl.DEPTH_COMPONENT24?gl.DEPTH_COMPONENT:gl.RGBA,type,null);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);return t;}
   function fbo(spec,depth){const f=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,f);const tex=spec.map((x,i)=>{const t=texture(x,x===gl.RGBA8?gl.UNSIGNED_BYTE:gl.FLOAT);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0+i,gl.TEXTURE_2D,t,0);return t;});let dt=null;if(depth){dt=texture(gl.DEPTH_COMPONENT24,gl.UNSIGNED_INT);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.DEPTH_ATTACHMENT,gl.TEXTURE_2D,dt,0);}gl.drawBuffers(spec.map((_,i)=>gl.COLOR_ATTACHMENT0+i));if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('FBO incomplete');return {f,tex,dt};}
   const scene=fbo([gl.RGBA8,gl.RGBA32F,gl.RGBA32F],true),post=fbo([gl.RGBA8,gl.RGBA32F],false);
   let verts=[];
   function quad(a,b,c,d,n,col,kind=1){for(const v of [a,b,c,a,c,d])verts.push(...v,...n,...col,kind);}
   function box(x,y,z,sx,sy,sz,col,kind=1){let X=x+sx,Y=y+sy,Z=z+sz;
    quad([x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z],[0,0,1],col,kind);quad([X,y,z],[x,y,z],[x,Y,z],[X,Y,z],[0,0,-1],col,kind);
    quad([x,y,z],[x,y,Z],[x,Y,Z],[x,Y,z],[-1,0,0],col,kind);quad([X,y,Z],[X,y,z],[X,Y,z],[X,Y,Z],[1,0,0],col,kind);
    quad([x,Y,Z],[X,Y,Z],[X,Y,z],[x,Y,z],[0,1,0],col,kind);}
   for(let x=-9;x<9;x++)for(let z=-9;z<9;z++)quad([x,0,z+1],[x+1,0,z+1],[x+1,0,z],[x,0,z],[0,1,0],[(x+z)%2?.16:.19,.35,.14]);
   for(let x=-4;x<=3;x++)box(x,0,-3,1,2,1,[.39,.4,.43]);
   for(let y=0;y<4;y++)box(-4,y,-2,1,1,1,[.48,.43,.34]);
   for(let i=0;i<4;i++)box(1+i*.8,0,1.5,.8,.4+i*.4,1.5,[.52,.51,.46]);
   box(-1.4,0,2.1,.85,1.9,.85,[.85,.16,.1],2); // entity proxy: must remain unchanged in terrain-only mode
   box(3,0,-1,.5,2.6,.5,[.27,.17,.1]);box(2.2,2,-1.8,2.1,1.5,2.1,[.13,.39,.15]);
   const vao=gl.createVertexArray();gl.bindVertexArray(vao);const buf=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buf);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(verts),gl.STATIC_DRAW);
   for(let i=0;i<4;i++){gl.enableVertexAttribArray(i);gl.vertexAttribPointer(i,i===3?1:3,gl.FLOAT,false,40,i*12);}gl.bindVertexArray(null);
   function mul(a,b){let c=new Float32Array(16);for(let j=0;j<4;j++)for(let i=0;i<4;i++)for(let k=0;k<4;k++)c[j*4+i]+=a[k*4+i]*b[j*4+k];return c;}
   function inverse(a){let rows=Array.from({length:4},(_,i)=>[a[i],a[4+i],a[8+i],a[12+i],...Array.from({length:4},(_,j)=>+(i===j))]);
    for(let i=0;i<4;i++){let k=i;for(let j=i+1;j<4;j++)if(Math.abs(rows[j][i])>Math.abs(rows[k][i]))k=j;[rows[k],rows[i]]=[rows[i],rows[k]];let d=rows[i][i];for(let t=0;t<8;t++)rows[i][t]/=d;for(let j=0;j<4;j++)if(j!==i){let v=rows[j][i];for(let t=0;t<8;t++)rows[j][t]-=v*rows[i][t];}}
    return new Float32Array(Array.from({length:16},(_,n)=>rows[n%4][4+Math.floor(n/4)]));}
   const norm=a=>{let l=Math.hypot(...a);return a.map(x=>x/l);},cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
   function matrices(eye,fov){let z=norm(eye.map((v,i)=>v-[0,1,0][i])),x=norm(cross([0,1,0],z)),y=cross(z,x);let view=new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,0,0,0,1]);let f=1/Math.tan(fov*Math.PI/360),n=.1,r=128;let p=new Float32Array([f/(W/H),0,0,0,0,f,0,0,0,0,(r+n)/(n-r),-1,0,0,2*r*n/(n-r),0]);return mul(p,view);}
   function bind(p,name,tex,index){gl.activeTexture(gl.TEXTURE0+index);gl.bindTexture(gl.TEXTURE_2D,tex);gl.uniform1i(gl.getUniformLocation(p,name),index);}
   function read(f,attachment,float){gl.bindFramebuffer(gl.FRAMEBUFFER,f);gl.readBuffer(gl.COLOR_ATTACHMENT0+attachment);let a=float?new Float32Array(W*H*4):new Uint8Array(W*H*4);gl.readPixels(0,0,W,H,gl.RGBA,float?gl.FLOAT:gl.UNSIGNED_BYTE,a);return a;}
   window.runCase=opts=>{
    const eye=opts.eye||[9,8,13],vp=matrices(eye,opts.fov||65),regs=opts.regions||[];
    gl.viewport(0,0,W,H);gl.bindFramebuffer(gl.FRAMEBUFFER,scene.f);gl.drawBuffers([gl.COLOR_ATTACHMENT0,gl.COLOR_ATTACHMENT1,gl.COLOR_ATTACHMENT2]);
    gl.clearBufferfv(gl.COLOR,0,[.13,.19,.26,1]);gl.clearBufferfv(gl.COLOR,1,[0,0,0,0]);gl.clearBufferfv(gl.COLOR,2,[0,0,0,0]);gl.clearBufferfv(gl.DEPTH,0,[1]);gl.enable(gl.DEPTH_TEST);gl.depthMask(true);gl.disable(gl.BLEND);
    gl.useProgram(geo);gl.uniformMatrix4fv(gl.getUniformLocation(geo,'VP'),false,vp);gl.uniform3fv(gl.getUniformLocation(geo,'Eye'),eye);gl.bindVertexArray(vao);gl.drawArrays(gl.TRIANGLES,0,verts.length/10);gl.bindVertexArray(null);
    const source=read(scene.f,0,false),truth=read(scene.f,1,true),kinds=read(scene.f,2,true);
    gl.bindFramebuffer(gl.FRAMEBUFFER,post.f);gl.drawBuffers([gl.COLOR_ATTACHMENT0,gl.COLOR_ATTACHMENT1]);gl.disable(gl.DEPTH_TEST);gl.useProgram(effect);
    bind(effect,'ColorTex',scene.tex[0],0);bind(effect,'DepthTex',scene.dt,1);bind(effect,'NormalTex',scene.tex[2],2);
    const L=n=>gl.getUniformLocation(effect,n);gl.uniformMatrix4fv(L('InvVP'),false,inverse(vp));gl.uniform3fv(L('Eye'),eye);gl.uniform1f(L('Time'),opts.time||0);gl.uniform1i(L('Count'),regs.length);gl.uniform1i(L('Target'),opts.target??1);
    regs.forEach((r,i)=>{gl.uniform3fv(L(`Center[${i}]`),r.center||[0,0,0]);gl.uniform3fv(L(`Extent[${i}]`),r.extent||[5,2,3]);gl.uniform3fv(L(`Endpoint[${i}]`),r.endpoint||[3,0,0]);gl.uniform1i(L(`Shape[${i}]`),r.shape||0);gl.uniform1i(L(`Material[${i}]`),r.material||0);});
    gl.drawArrays(gl.TRIANGLES,0,3);const result=read(post.f,0,false),diag=read(post.f,1,true);
    let maxError=0,geometry=0,changed=0,outsideChanged=0,excludedChanged=0,excluded=0,maxMaskError=0;
    for(let i=0;i<truth.length;i+=4){let delta=Math.max(...[0,1,2].map(k=>Math.abs(source[i+k]-result[i+k])));let type=kinds[i+3];if(type>1.5){excluded++;if(delta)excludedChanged++;}if(diag[i+3]===0&&delta)outsideChanged++;if(delta)changed++;
     if(truth[i+3]){geometry++;maxError=Math.max(maxError,Math.hypot(...[0,1,2].map(k=>truth[i+k]-diag[i+k])));
      if(regs.length===1&&(!regs[0].shape)){let r=regs[0],d=Math.hypot(...[0,1,2].map(k=>truth[i+k]-(r.center||[0,0,0])[k]))-(r.extent||[5,2,3])[0];let t=Math.min(1,Math.max(0,(d+.45)/.45));let mask=(opts.target??1)===1&&type>1.5?0:1-t*t*(3-2*t);maxMaskError=Math.max(maxMaskError,Math.abs(mask-diag[i+3]));}}
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.useProgram(display);bind(display,'Image',post.tex[0],0);gl.drawArrays(gl.TRIANGLES,0,3);
    let ext=gl.getExtension('WEBGL_debug_renderer_info');return {geometry,changed,outsideChanged,excluded,excludedChanged,maxError,maxMaskError,glError:gl.getError(),renderer:ext?gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER)};
   };
  },kernel);
  const cases=[
   {name:'baseline',regions:[]},
   {name:'cosmic-sphere',regions:[{shape:0}]},
   {name:'cosmic-camera-b',eye:[-7,5,12],regions:[{shape:0}]},
   {name:'cosmic-fov-100',fov:100,regions:[{shape:0}]},
   {name:'ink-path',regions:[{shape:4,center:[-4,0,4],endpoint:[8,0,-8],extent:[1.9,0,0],material:1}]},
   {name:'lava-box',regions:[{shape:2,center:[0,1,0],extent:[3,3,4],material:2}]},
   {name:'cylinder',regions:[{shape:1,extent:[4,2,0]}]},
   {name:'plane',regions:[{shape:3,extent:[0,.8,0]}]},
   {name:'noise-boundary',regions:[{shape:5,extent:[5,3,0]}]},
   {name:'overlap',regions:[{shape:0},{shape:2,center:[2,0,1],extent:[2,3,3],material:2}]},
   {name:'include-entity',target:0,regions:[{shape:0}]},
   {name:'growth-small',regions:[{shape:0,extent:[2,0,0]}]},
  ];
  const results=[];
  for(const c of cases){const record=await page.evaluate(o=>window.runCase(o),c);await page.locator('body').screenshot({path:path.join(out,c.name+'.png')});results.push({...c,...record});}
  const passed=results.every(x=>x.glError===0&&x.maxError<.01&&x.outsideChanged===0&&x.maxMaskError<.01&&((x.target??1)===0||x.excludedChanged===0))&&results[1].changed>1000&&results.at(-1).changed<results[1].changed&&results.find(x=>x.name==='include-entity').excludedChanged>100;
  fs.writeFileSync(path.join(out,'gpu-validation.json'),JSON.stringify({status:passed?'passed':'failed',scope:'Synthetic WebGL2 geometry/depth/MRT validation, not Minecraft or Forge runtime',results},null,2));
  console.log(JSON.stringify({passed,results:results.map(({name,geometry,changed,outsideChanged,excludedChanged,maxError,maxMaskError,glError})=>({name,geometry,changed,outsideChanged,excludedChanged,maxError,maxMaskError,glError}))},null,2));if(!passed)process.exitCode=1;
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
