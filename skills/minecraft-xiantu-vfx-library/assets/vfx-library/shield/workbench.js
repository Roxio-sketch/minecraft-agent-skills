(() => {
'use strict';
const $=id=>document.getElementById(id);
const defaults={schema:2,shape:'sphere',segments:56,depth:.8,color:'#268eff',edgeColor:'#a2d4ff',opacity:.88,centerOpacity:.24,edgeOpacity:1,fresnelStrength:2.2,fresnelPower:2.2,life:1,pattern:'hex',hexScale:3.2,hexOpacity:.68,lineWidth:.045,flashSpeed:.7,flashIntensity:.13,flowScale:2.4,flowSpeed:.65,flowIntensity:2.1,showHex:true,showFlow:true,reveal:1,noiseScale:1.7,noiseEdgeWidth:.07,noiseEdgeIntensity:2.2,showDissolve:true,scanSpeed:.25,scanStrength:.3,scanWidth:.05,showScan:false,distortion:.035,distortionScale:2.2,distortionSpeed:.5,pulseAmplitude:.015,pulseFrequency:.35,hitSpeed:2.6,hitWidth:.11,hitDuration:1.8,hitIntensity:2.6,hitRadius:3.4,sparkCount:65,sparkSpeed:3.2,sparkLife:.7,sparkColor:'#ffb44e',damage:.08,shatterDuration:2.4,shardCount:110,shatterSpeed:2.8,shatterSpin:3,shatterGravity:1.8,shatterFlash:1.8,wireframe:false,background:true};
let p={...defaults};
const lifecycle=new ShieldFracture.ShieldLifecycle();let shards=null;
const groups=[
 ['几何与空间',[['shape','护盾形状',{sphere:'完整球形',dome:'半球穹顶',convex:'弧面矩形',flat:'平面矩形'}],['depth','弧面深度 / 球体纵深',.15,1.6,.01],['segments','网格细分',16,80,8],['wireframe','显示网格线'],['background','显示空间参照']]],
 ['颜色与透明度',[['color','主体颜色','color'],['edgeColor','边缘颜色','color'],['opacity','整体不透明度',0,1,.01],['centerOpacity','中心填充',0,1,.01],['edgeOpacity','边缘不透明度',0,1,.01],['fresnelStrength','边缘亮度',0,5,.05],['fresnelPower','边缘锐度',.3,8,.1],['life','生命值（低值变红）',0,1,.01]]],
 ['图案与能量流',[['pattern','表面图案',{hex:'蜂窝六边形',diamond:'菱形网格',dots:'全息点阵',grid:'方格电路',none:'无图案'}],['hexScale','图案密度',.6,12,.1],['lineWidth','图案线宽',.005,.15,.005],['hexOpacity','图案亮度',0,2,.02],['flashSpeed','单元闪烁速度',0,4,.05],['flashIntensity','单元闪烁亮度',0,1,.01],['flowScale','三维噪声尺度',.2,8,.1],['flowSpeed','能量流速度',0,4,.05],['flowIntensity','能量流亮度',0,5,.05],['showHex','启用图案层'],['showFlow','启用能量流']]],
 ['出现、溶解与扫描',[['reveal','护盾展开进度',0,1,.01],['noiseScale','溶解噪声尺度',.3,6,.1],['noiseEdgeWidth','溶解边宽度',.01,.25,.01],['noiseEdgeIntensity','溶解边亮度',0,5,.1],['showDissolve','启用噪声溶解'],['scanSpeed','扫描带速度',0,2,.01],['scanStrength','扫描带亮度',0,2,.01],['scanWidth','扫描带宽度',.005,.2,.005],['showScan','启用扫描带']]],
 ['顶点形变与脉动',[['distortion','法线畸变幅度',0,.4,.005],['distortionScale','畸变噪声密度',.3,6,.1],['distortionSpeed','畸变速度',0,3,.05],['pulseAmplitude','呼吸形变幅度',0,.2,.005],['pulseFrequency','呼吸频率',0,2,.05]]],
 ['撞击、波前与火星',[['hitSpeed','冲击波速度',.3,7,.1],['hitWidth','冲击波宽度',.03,.45,.01],['hitDuration','冲击持续时间',.3,4,.1],['hitIntensity','撞击亮度',0,7,.1],['hitRadius','波前最大半径',.5,6,.1],['sparkCount','火星数量',0,160,5],['sparkSpeed','火星飞散速度',.3,7,.1],['sparkLife','火星寿命',.2,2,.05],['sparkColor','火星颜色','color'],['damage','每次撞击损耗',0,.5,.01]]],
 ['破碎与碎片',[['shatterDuration','破碎消退时间',.6,5,.1],['shardCount','碎片数量（下次破碎生效）',24,240,1],['shatterSpeed','碎片飞散速度',.3,7,.1],['shatterSpin','碎片旋转速度',0,8,.1],['shatterGravity','碎片下坠强度',0,6,.1],['shatterFlash','破裂闪光亮度',0,5,.1]]]
];
let meshDirty=true;
for(const [name,items] of groups){const h=document.createElement('h2');h.textContent=name;$('controls').append(h);for(const def of items){const [key,text,type,minOrMax,step]=def;const label=document.createElement('label'),row=document.createElement('span');row.className='row';const title=document.createElement('span');title.textContent=text;row.append(title);const input=document.createElement(typeof type==='object'?'select':'input');input.id=key;
 if(typeof type==='object'){for(const [v,n] of Object.entries(type)){const option=document.createElement('option');option.value=v;option.textContent=n;input.append(option)}row.append(input);label.append(row)}
 else if(type==='color'){input.type='color';row.append(input);label.append(row)}
 else if(type===undefined){input.type='checkbox';label.className='toggle';label.append(input,title)}
 else{input.type='range';input.min=type;input.max=minOrMax;input.step=step;const out=document.createElement('output');out.id=key+'Out';row.append(out);label.append(row,input)}
 input.addEventListener('input',()=>{p[key]=input.type==='checkbox'?input.checked:input.type==='range'?Number(input.value):input.value;if(['shape','depth','segments'].includes(key)){meshDirty=true;resetSimulation()}if(key==='life'&&p.life>0&&lifecycle.state!=='intact')resetSimulation();syncUI()});$('controls').append(label);
}}
function syncUI(){for(const [,items] of groups)for(const [key] of items){const el=$(key);if(el.type==='checkbox')el.checked=p[key];else el.value=p[key];const out=$(key+'Out');if(out)out.textContent=['life','damage'].includes(key)?Math.round(p[key]*100)+'%':Number(p[key]).toFixed(Number(el.step)<.01?3:Number(el.step)<1?2:0)}}syncUI();
const canvas=$('c'),fx=$('fx'),bg=$('bg'),ctx=fx.getContext('2d'),bctx=bg.getContext('2d');
const gl=canvas.getContext('webgl',{alpha:true,antialias:true,premultipliedAlpha:false});
function fail(e){$('error').textContent='渲染器错误：'+e.message;$('hud').textContent='渲染失败，请查看左侧错误说明';console.error(e)}
if(!gl){fail(new Error('无法创建 WebGL 上下文。请使用启用了硬件加速的浏览器。'));return}
let program,shardProgram;
function createProgram(vertex,fragment){function compile(type,source){const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));return shader}const result=gl.createProgram();gl.attachShader(result,compile(gl.VERTEX_SHADER,vertex));gl.attachShader(result,compile(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(result);if(!gl.getProgramParameter(result,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(result));return result}
try{program=createProgram(SHIELD_VERTEX,SHIELD_FRAGMENT);shardProgram=createProgram(SHARD_VERTEX,SHARD_FRAGMENT);}catch(e){fail(e);return}
gl.useProgram(program);
const attrs={position:gl.getAttribLocation(program,'aPos'),normal:gl.getAttribLocation(program,'aNormal'),uv:gl.getAttribLocation(program,'aUV')};
let currentProgram=program;const uniforms=new Map();function useProgram(value){currentProgram=value;gl.useProgram(value)}function loc(name){let cache=uniforms.get(currentProgram);if(!cache){cache=new Map();uniforms.set(currentProgram,cache)}if(!cache.has(name))cache.set(name,gl.getUniformLocation(currentProgram,name));return cache.get(name)}
function uf(name,value){gl.uniform1f(loc(name),value)}
const buffers={position:gl.createBuffer(),normal:gl.createBuffer(),uv:gl.createBuffer(),indices:gl.createBuffer(),lines:gl.createBuffer()};let positions=[],normals=[],uvs=[],indices=[],lineIndices=[];
function geometry(u,v){if(p.shape==='sphere'||p.shape==='dome'){const ph=v*Math.PI*(p.shape==='dome'?.5:1),th=u*Math.PI*2;const q=[2*Math.sin(ph)*Math.cos(th),2*Math.cos(ph),2*Math.sin(ph)*Math.sin(th)*p.depth];return [q,normalize([q[0],q[1],q[2]/(p.depth*p.depth)])]}
 const x=(u-.5)*4.4,y=(.5-v)*2.8,k=p.shape==='convex'?p.depth:0;const z=k*(Math.cos((u-.5)*Math.PI)*Math.cos((v-.5)*Math.PI));const dx=-k*Math.PI/4.4*Math.sin((u-.5)*Math.PI)*Math.cos((v-.5)*Math.PI),dy=k*Math.PI/2.8*Math.cos((u-.5)*Math.PI)*Math.sin((v-.5)*Math.PI);return [[x,y,z],normalize([-dx,-dy,1])]}
function buildMesh(){positions=[];normals=[];uvs=[];indices=[];lineIndices=[];const cols=p.segments,rows=Math.round(cols*.55);for(let j=0;j<=rows;j++)for(let i=0;i<=cols;i++){const [q,n]=geometry(i/cols,j/rows);positions.push(...q);normals.push(...n);uvs.push(i/cols,j/rows)}for(let j=0;j<rows;j++)for(let i=0;i<cols;i++){const a=j*(cols+1)+i,b=a+1,c=a+cols+1,d=c+1;if(p.shape==="sphere"||p.shape==="dome")indices.push(a,b,c,b,d,c);else indices.push(a,c,b,b,c,d);lineIndices.push(a,b,a,c)}for(const key of ['position','normal','uv']){gl.bindBuffer(gl.ARRAY_BUFFER,buffers[key]);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(key==='position'?positions:key==='normal'?normals:uvs),gl.STATIC_DRAW)}for(const [key,data] of [['indices',indices],['lines',lineIndices]]){gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,buffers[key]);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(data),gl.STATIC_DRAW)}meshDirty=false}
function normalize(q){const len=Math.hypot(...q)||1;return q.map(v=>v/len)}function dot(a,b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]}function sub(a,b){return a.map((v,i)=>v-b[i])}function cross(a,b){return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]}
let yaw=-.35,pitch=.16,distance=8.4,time=0,lastTime=0,paused=false;let rotation,mvp,camera;
function mul(a,b){const out=new Float32Array(16);for(let j=0;j<4;j++)for(let i=0;i<4;i++)for(let k=0;k<4;k++)out[j*4+i]+=a[k*4+i]*b[j*4+k];return out}
function matrices(){const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=-Math.sin(pitch);rotation=[cy,-sp*sy,-cp*sy,0,cp,-sp,sy,sp*cy,cp*cy];const view=new Float32Array([rotation[0],rotation[1],rotation[2],0,rotation[3],rotation[4],rotation[5],0,rotation[6],rotation[7],rotation[8],0,0,0,-distance,1]);const f=1/Math.tan(Math.PI/8),a=canvas.width/canvas.height,n=.1,far=100,proj=new Float32Array([f/a,0,0,0,0,f,0,0,0,0,(far+n)/(n-far),-1,0,0,2*far*n/(n-far),0]);mvp=mul(proj,view);camera=[rotation[2]*distance,rotation[5]*distance,rotation[8]*distance]}
function project(q){let x=mvp[0]*q[0]+mvp[4]*q[1]+mvp[8]*q[2]+mvp[12],y=mvp[1]*q[0]+mvp[5]*q[1]+mvp[9]*q[2]+mvp[13],w=mvp[3]*q[0]+mvp[7]*q[1]+mvp[11]*q[2]+mvp[15];return [(x/w*.5+.5)*canvas.width,(.5-y/w*.5)*canvas.height,w]}
function resize(){const rect=$('stage').getBoundingClientRect(),dpr=Math.min(devicePixelRatio,2);for(const c of [canvas,fx,bg]){c.width=Math.round(rect.width*dpr);c.height=Math.round(rect.height*dpr)}gl.viewport(0,0,canvas.width,canvas.height)}window.addEventListener('resize',resize);resize();
let hits=Array.from({length:6},()=>({pos:[0,0,2],age:99})),slot=0,sparks=[];
function emitSparks(q,normal,count,speed=1){for(let i=0;i<count;i++){const direction=normalize([Math.random()*2-1,Math.random()*2-1,Math.random()*2-1]);const vel=direction.map((v,j)=>(v+normal[j]*.7)*p.sparkSpeed*speed*(.4+Math.random()*.65));sparks.push({pos:q.slice(),prev:q.slice(),velocity:vel,age:0,life:p.sparkLife*(.6+Math.random()*.8)})}if(sparks.length>1200)sparks.splice(0,sparks.length-1200)}
function impact(q){
 if(!q)return;const result=lifecycle.hitAt(p.life,p.damage,q,p.shatterDuration);if(!result.accepted)return;
 p.life=result.life;
 const normal=p.shape==='flat'||p.shape==='convex'?[0,0,1]:normalize(q);
 emitSparks(q,normal,p.sparkCount,result.shattered?1.5:1);
 if(result.shattered){
  if(meshDirty)buildMesh();
  const surface=ShieldFracture.freezeSurface(positions,normals,p,time);
  shards=ShieldFracture.fractureMesh(surface,normals,uvs,indices,q,p);
  shards.speed=p.shatterSpeed;
  gl.bindBuffer(gl.ARRAY_BUFFER,shardBuffers.faces);gl.bufferData(gl.ARRAY_BUFFER,shards.vertices,gl.STATIC_DRAW);
  gl.bindBuffer(gl.ARRAY_BUFFER,shardBuffers.outlines);gl.bufferData(gl.ARRAY_BUFFER,shards.outlines,gl.STATIC_DRAW);
  // The intact shell stops drawing at once; the same surface is now fragments.
  hits.forEach(h=>h.age=99);burstLeft=0;
  for(let i=0;i<Math.min(80,p.sparkCount);i++){const point=geometry(Math.random(),p.shape==='dome'?Math.random():.15+Math.random()*.7)[0];emitSparks(point,normalize(point),1,1.2)}
  $('status').textContent='护盾已击碎：裂纹扩散 → 曲面碎片飞散 → 消退。点击“恢复护盾”可重播。';
 }else{hits[slot]={pos:q.slice(),age:0};slot=(slot+1)%6;if(p.life===0)$('status').textContent='护盾生命已耗尽，下一次命中将触发破碎。'}
 syncUI();
}
function resetSimulation(){lifecycle.reset();shards=null;hits.forEach(h=>h.age=99);slot=0;sparks=[];burstLeft=0;burstDelay=0;}
const shardBuffers={faces:gl.createBuffer(),outlines:gl.createBuffer()};
const shardAttributes=[['aPos',3,0],['aNormal',3,3],['aUV',2,6],['aCenter',3,8],['aVelocity',3,11],['aAxis',3,14],['aMotion',2,17]].map(([name,size,offset])=>({loc:gl.getAttribLocation(shardProgram,name),size,offset}));
function rayPick(px,py){if(meshDirty)buildMesh();matrices();const f=1/Math.tan(Math.PI/8),asp=canvas.width/canvas.height;const vr=normalize([(px/canvas.width*2-1)*asp/f,(1-py/canvas.height*2)/f,-1]);const ray=[dot(vr,[rotation[0],rotation[1],rotation[2]]),dot(vr,[rotation[3],rotation[4],rotation[5]]),dot(vr,[rotation[6],rotation[7],rotation[8]])];let nearest=Infinity,result=null;for(let k=0;k<indices.length;k+=3){const a=positions.slice(indices[k]*3,indices[k]*3+3),b=positions.slice(indices[k+1]*3,indices[k+1]*3+3),c=positions.slice(indices[k+2]*3,indices[k+2]*3+3),ab=sub(b,a),ac=sub(c,a),h=cross(ray,ac),det=dot(ab,h);if(Math.abs(det)<1e-7)continue;const inv=1/det,s=sub(camera,a),u=dot(s,h)*inv;if(u<0||u>1)continue;const q=cross(s,ab),v=dot(ray,q)*inv;if(v<0||u+v>1)continue;const d=dot(ac,q)*inv;if(d>0&&d<nearest){nearest=d;result=camera.map((v,j)=>v+ray[j]*d)}}return result}
let pointer=null;
canvas.addEventListener('pointerdown',e=>{pointer={x:e.clientX,y:e.clientY,startX:e.clientX,startY:e.clientY,moved:false};canvas.setPointerCapture(e.pointerId)});
canvas.addEventListener('pointermove',e=>{if(!pointer)return;const dx=e.clientX-pointer.x,dy=e.clientY-pointer.y;if(Math.hypot(e.clientX-pointer.startX,e.clientY-pointer.startY)>4)pointer.moved=true;if(pointer.moved){yaw+=dx*.007;pitch=Math.max(-1.45,Math.min(1.45,pitch+dy*.007))}pointer.x=e.clientX;pointer.y=e.clientY});
canvas.addEventListener('pointerup',e=>{if(pointer&&!pointer.moved){const rect=canvas.getBoundingClientRect();impact(rayPick((e.clientX-rect.left)*canvas.width/rect.width,(e.clientY-rect.top)*canvas.height/rect.height))}pointer=null});canvas.addEventListener('pointercancel',()=>pointer=null);
canvas.addEventListener('wheel',e=>{e.preventDefault();distance=Math.max(4,Math.min(13,distance+e.deltaY*.004))},{passive:false});
$('front').onclick=()=>{yaw=0;pitch=0};$('side').onclick=()=>{yaw=Math.PI*.46;pitch=.1};$('orbit').onclick=()=>{yaw=-.55;pitch=.27};
$('hit').onclick=()=>impact(rayPick(canvas.width*.5,canvas.height*.5)||geometry(.25,.5)[0]);
let burstLeft=0,burstDelay=0;$('burst').onclick=()=>{if(lifecycle.state!=='intact')return;burstLeft=p.damage>0?Math.ceil(p.life/p.damage)+1:6;burstDelay=0};
$('shatterDemo').onclick=()=>{resetSimulation();p.life=0;p.reveal=1;syncUI();impact(rayPick(canvas.width*.5,canvas.height*.5)||geometry(.25,.5)[0])};
$('breakPreview').onclick=$('shatterDemo').onclick;
$('reset').onclick=()=>{p.life=1;p.reveal=1;resetSimulation();$('status').textContent='护盾已恢复。持续受击耗尽生命后，下一击触发破碎。';syncUI()};$('pause').onclick=()=>{paused=!paused;$('pause').textContent=paused?'继续动画':'暂停动画'};
function color(hex){return [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255)}
function background(){bctx.clearRect(0,0,bg.width,bg.height);if(!p.background)return;bctx.strokeStyle='#3b648149';bctx.lineWidth=1;for(let i=-6;i<=6;i++){for(const [a,b] of [[[i,-2.05,-6],[i,-2.05,6]],[[-6,-2.05,i],[6,-2.05,i]]]){const pa=project(a),pb=project(b);bctx.beginPath();bctx.moveTo(pa[0],pa[1]);bctx.lineTo(pb[0],pb[1]);bctx.stroke()}}const center=project([0,-2.03,0]);bctx.fillStyle='#7898b190';bctx.font=`${12*canvas.width/canvas.clientWidth}px system-ui`;bctx.fillText('地面参照 · 材质透明度可直接观察',center[0]-90,center[1]+24)}
function particles(dt){ctx.clearRect(0,0,fx.width,fx.height);ctx.save();ctx.globalCompositeOperation='lighter';const [r,g,b]=color(p.sparkColor).map(v=>Math.round(v*255));for(const s of sparks){s.prev=s.pos.slice();s.age+=dt;for(let j=0;j<3;j++)s.pos[j]+=s.velocity[j]*dt;s.velocity[1]-=2.8*dt;const q=project(s.pos),trail=project(s.pos.map((v,j)=>v-s.velocity[j]*.035)),a=Math.max(0,1-s.age/s.life);ctx.shadowColor=p.sparkColor;ctx.shadowBlur=12*a;ctx.strokeStyle=`rgba(${r},${g},${b},${a})`;ctx.lineWidth=1+2*a;ctx.beginPath();ctx.moveTo(trail[0],trail[1]);ctx.lineTo(q[0],q[1]);ctx.stroke();ctx.fillStyle=`rgba(255,242,197,${a})`;ctx.beginPath();ctx.arc(q[0],q[1],.8+1.4*a,0,Math.PI*2);ctx.fill()}ctx.restore();sparks=sparks.filter(s=>s.age<s.life)}
const uniformMap={opacity:'Opacity',centerOpacity:'CenterOpacity',edgeOpacity:'EdgeOpacity',fresnelPower:'FresnelPower',fresnelStrength:'FresnelStrength',life:'Life',hexScale:'HexScale',hexOpacity:'HexOpacity',lineWidth:'LineWidth',flashSpeed:'FlashSpeed',flashIntensity:'FlashIntensity',flowScale:'FlowScale',flowSpeed:'FlowSpeed',flowIntensity:'FlowIntensity',reveal:'Reveal',noiseScale:'NoiseScale',noiseEdgeWidth:'NoiseEdgeWidth',noiseEdgeIntensity:'NoiseEdgeIntensity',scanSpeed:'ScanSpeed',scanStrength:'ScanStrength',scanWidth:'ScanWidth',distortion:'Distortion',distortionScale:'DistortionScale',distortionSpeed:'DistortionSpeed',pulseAmplitude:'PulseAmplitude',pulseFrequency:'PulseFrequency',hitSpeed:'HitSpeed',hitWidth:'HitWidth',hitDuration:'HitDuration',hitIntensity:'HitIntensity',hitRadius:'HitRadius',showHex:'ShowHex',showFlow:'ShowFlow',showDissolve:'ShowDissolve',showScan:'ShowScan'};
function materialUniforms(){
 gl.uniformMatrix4fv(loc('uMVP'),false,mvp);gl.uniform3fv(loc('uCamera'),camera);
 gl.uniform3fv(loc('uColor'),color(p.color));gl.uniform3fv(loc('uEdgeColor'),currentProgram===shardProgram?[1,.26,.10]:color(p.edgeColor));uf('uTime',time);
 for(const [key,name] of Object.entries(uniformMap))uf('u'+name,typeof p[key]==='boolean'?Number(p[key]):p[key]);
 gl.uniform1i(loc('uShape'),{sphere:0,dome:1,convex:2,flat:3}[p.shape]);gl.uniform1i(loc('uPattern'),{hex:0,diamond:1,dots:2,grid:3,none:4}[p.pattern]);
 gl.uniform3fv(loc('uHits[0]'),new Float32Array(hits.flatMap(h=>h.pos)));gl.uniform1fv(loc('uHitAge[0]'),new Float32Array(hits.map(h=>h.age)));
}
function disableAttributes(){for(let i=0;i<gl.getParameter(gl.MAX_VERTEX_ATTRIBS);i++)gl.disableVertexAttribArray(i)}
function drawIntact(){
 useProgram(program);disableAttributes();
 if(p.shape==='sphere'||p.shape==='dome'){gl.enable(gl.CULL_FACE);gl.cullFace(gl.BACK)}else gl.disable(gl.CULL_FACE);
 gl.depthMask(true);
 for(const [key,size] of [['position',3],['normal',3],['uv',2]])if(attrs[key]>=0){gl.bindBuffer(gl.ARRAY_BUFFER,buffers[key]);gl.enableVertexAttribArray(attrs[key]);gl.vertexAttribPointer(attrs[key],size,gl.FLOAT,false,0,0)}
 materialUniforms();gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,buffers.indices);gl.drawElements(gl.TRIANGLES,indices.length,gl.UNSIGNED_SHORT,0);
 if(p.wireframe){gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,buffers.lines);gl.drawElements(gl.LINES,lineIndices.length,gl.UNSIGNED_SHORT,0)}
}
function drawShards(){
 if(!shards)return;useProgram(shardProgram);disableAttributes();gl.disable(gl.CULL_FACE);gl.depthMask(true);materialUniforms();
 uf('uShatterAge',lifecycle.age);uf('uShatterDuration',p.shatterDuration);uf('uShatterGravity',p.shatterGravity);uf('uShatterFlash',p.shatterFlash);
 uf('uShatterSpeedFactor',p.shatterSpeed/shards.speed);uf('uShatterSpinFactor',p.shatterSpin);
 function bind(buffer){gl.bindBuffer(gl.ARRAY_BUFFER,buffer);for(const a of shardAttributes)if(a.loc>=0){gl.enableVertexAttribArray(a.loc);gl.vertexAttribPointer(a.loc,a.size,gl.FLOAT,false,19*4,a.offset*4)}}
 bind(shardBuffers.faces);uf('uShardOutline',0);gl.drawArrays(gl.TRIANGLES,0,shards.vertices.length/19);
 gl.depthMask(false);bind(shardBuffers.outlines);uf('uShardOutline',1);gl.drawArrays(gl.LINES,0,shards.outlines.length/19);gl.depthMask(true);
}
function render(ms){try{
 const dt=paused?0:Math.min(.04,lastTime?(ms-lastTime)/1000:0);lastTime=ms;time+=dt;
 if(meshDirty)buildMesh();matrices();for(const h of hits)h.age+=dt;
 if(lifecycle.state==='shattering'){lifecycle.duration=p.shatterDuration;lifecycle.tick(dt);if(lifecycle.state==='broken')shards=null}
 if(burstLeft>0&&!paused){burstDelay-=dt;if(burstDelay<=0){burstLeft--;burstDelay=.26;impact(geometry(Math.random(),p.shape==='dome'?.25+Math.random()*.7:.25+Math.random()*.5)[0])}}
 background();gl.clearColor(0,0,0,0);gl.depthMask(true);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
 gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
 if(lifecycle.state==='intact')drawIntact();else if(lifecycle.state==='shattering')drawShards();particles(dt);
 const state=lifecycle.state==='broken'?'已破碎 · 请恢复护盾':lifecycle.state==='shattering'?`破碎中 · ${shards?.count||0} 块碎片 · ${lifecycle.age.toFixed(2)}s`:p.life===0?'濒临破碎 · 下一击击碎':'完整';
 $('hud').textContent=`${{sphere:'球形',dome:'穹顶',convex:'弧面屏障',flat:'平面'}[p.shape]} · ${state} · 命中 ${hits.filter(h=>h.age<p.hitDuration).length}/6 · 火星 ${sparks.length} · 生命 ${Math.round(p.life*100)}%${paused?' · 已暂停':''}`;
 requestAnimationFrame(render);
}catch(e){fail(e)}}
const presets={flow:{...defaults},panel:{...defaults,shape:'convex',depth:.85,opacity:.68,centerOpacity:.22,color:'#4464ff',edgeColor:'#b9ccff',showScan:true,scanStrength:.28,distortion:.01,pulseAmplitude:0,flowIntensity:1},holo:{...defaults,shape:'dome',depth:1,pattern:'grid',color:'#46efc9',edgeColor:'#b7ffe3',opacity:.5,showScan:true,scanStrength:.55,distortion:.07,hexOpacity:.3,flowIntensity:1.9},danger:{...defaults,life:.16,hitIntensity:2.2,hitWidth:.09,color:'#19bcff',edgeColor:'#ffd897',distortion:.17,pulseAmplitude:.075,pulseFrequency:.9,flowIntensity:2.4}};
$('preset').onchange=()=>{p={...presets[$('preset').value]};meshDirty=true;resetSimulation();yaw=-.35;pitch=.16;distance=8.4;syncUI()};
function validate(data){if(!data||typeof data!=='object'||Array.isArray(data))throw new Error('JSON 必须是对象');const out={...defaults};for(const [,items] of groups)for(const [key,,type,max] of items){if(!(key in data))continue;const value=data[key];if(typeof type==='number'){if(typeof value!=='number'||!Number.isFinite(value)||value<type||value>max)throw new Error(key+' 超出范围 '+type+'—'+max);out[key]=value}else if(type==='color'){if(typeof value!=='string'||!/^#[0-9a-f]{6}$/i.test(value))throw new Error(key+' 需要 #RRGGBB');out[key]=value}else if(typeof type==='object'){if(!Object.hasOwn(type,value))throw new Error(key+' 不是支持的选项');out[key]=value}else{if(typeof value!=='boolean')throw new Error(key+' 需要布尔值');out[key]=value}}out.segments=Math.round(out.segments/8)*8;return out}
$('export').onclick=()=>{try{const json=JSON.stringify(validate(p),null,2),url=URL.createObjectURL(new Blob([json],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='recipe.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);$('status').textContent='已导出 v2 配方，包含所有材质、几何和火星参数。'}catch(e){$('status').textContent=e.message}};
$('import').onclick=()=>$('file').click();$('file').onchange=async()=>{try{const file=$('file').files[0];if(!file)return;const data=JSON.parse(await file.text());if(data.schema===1){Object.assign(data,{edgeColor:data.emission,fresnelPower:data.edgePower,fresnelStrength:data.edgeStrength,hexScale:Math.min(12,(data.hexScale||18)/5),hexOpacity:data.hexStrength,showHex:data.hexagons,showScan:data.scanlines,scanSpeed:data.scanlineSpeed,hitIntensity:data.impactStrength})}p=validate(data);meshDirty=true;resetSimulation();syncUI();$('status').textContent='配方已导入并应用。';}catch(e){$('status').textContent='导入失败：'+e.message}finally{$('file').value=''}};
buildMesh();requestAnimationFrame(render);
})();
