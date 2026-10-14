/* Original 2D design preview. This is not the Forge geometry/depth renderer. */
'use strict';
const $=id=>document.getElementById(id), schema='minecraft-item-outline-preview/v1';
const defaults={shape:'sword',mode:'solid',width:0.8,opacity:1,angle:0,scale:2,speed:0.6,colors:['#ffc85e','#ef6a76','#65dfd0'],body:true,edge:true,time:0,image:null};
let state=structuredClone(defaults), playing=true, custom=null, imageTicket=0;
const presets={gold:{mode:'solid',width:0.8,colors:['#ffc85e','#ef6a76','#65dfd0']},flow:{mode:'flow',width:1.1,colors:['#64ead4','#f27789','#fff0b5']},stars:{mode:'stars',width:1.5,colors:['#31677d','#729ded','#f1fffa']},rainbow:{mode:'rainbow',width:1,colors:['#ffc85e','#ef6a76','#65dfd0']}};
const make=n=>{const c=document.createElement('canvas');c.width=c.height=n;return c;};
const art={};
// Authored pixel geometry keeps the sample assets independent of the reference JAR.
for(const name of ['sword','axe','ring']){
 const c=make(32),x=c.getContext('2d');
 const polygon=(pts,color)=>{x.fillStyle=color;x.beginPath();pts.forEach(([a,b],i)=>i?x.lineTo(a,b):x.moveTo(a,b));x.closePath();x.fill();};
 if(name==='sword'){polygon([[7,26],[9,28],[16,21],[14,19]],'#986e99');polygon([[9,19],[11,17],[19,25],[17,27]],'#d7b670');polygon([[13,17],[24,6],[28,4],[26,9],[17,20]],'#55bfae');polygon([[15,17],[25,7],[27,5],[25,10],[17,18]],'#b6f3d4');}
 if(name==='axe'){polygon([[8,27],[11,28],[23,9],[20,7]],'#a28370');polygon([[10,7],[17,4],[26,8],[28,15],[24,18],[21,12],[14,15],[9,12]],'#6dc4c4');polygon([[10,7],[17,4],[26,8],[23,9],[17,7],[11,10]],'#c4ede3');}
 if(name==='ring'){x.strokeStyle='#c6a873';x.lineWidth=5;x.beginPath();x.arc(16,17,8,0,Math.PI*2);x.stroke();polygon([[16,3],[21,8],[16,13],[11,8]],'#6cd2bc');}
 art[name]=c;
}
function sync(){for(const k of ['shape','mode','width','opacity','angle','scale','speed'])$(k).value=state[k];for(const k of ['body','edge'])$(k).checked=state[k];state.colors.forEach((v,i)=>$('color'+i).value=v);for(const k of ['width','opacity','angle','scale','speed'])$(k+'Value').value=Number(state[k]).toFixed(k==='angle'?0:2);$('shape').querySelector('[value=custom]').disabled=!custom;syncTime();}
function syncTime(){$('time').value=state.time.toFixed(2);$('timeline').value=state.time;}
function icon(){ $('play').innerHTML='<i data-lucide="'+(playing?'pause':'play')+'"></i>';$('play').title=$('play').ariaLabel=playing?'暂停':'播放';lucide.createIcons();}
function setPlaying(v){playing=v;icon();}
function base(n){const c=make(n),x=c.getContext('2d');x.imageSmoothingEnabled=false;x.translate(n/2,n/2);x.rotate(state.angle*Math.PI/180);const a=state.shape==='custom'?custom:art[state.shape];if(a){const s=n*0.88/Math.max(a.width,a.height);x.drawImage(a,-a.width*s/2,-a.height*s/2,a.width*s,a.height*s);}return c;}
const rgb=s=>[1,3,5].map(i=>parseInt(s.slice(i,i+2),16));
const mix=(a,b,t)=>a.map((v,i)=>v+(b[i]-v)*t), fract=x=>x-Math.floor(x);
function hue(h){return [0,2/3,1/3].map(k=>255*(0.3+0.7*Math.max(0,Math.min(1,Math.abs(fract(h+k)*6-3)-1))));}
function render(canvas,n,original=false){
 const src=base(n),ctx=canvas.getContext('2d');ctx.clearRect(0,0,n,n);
 if(original){ctx.drawImage(src,0,0);return;}
 if(state.edge&&state.width>0){
  const mask=make(n),m=mask.getContext('2d'),d=state.width*n/32;
  for(const [a,b] of [[-1,-1],[0,-1],[1,-1],[-1,0],[1,0],[-1,1],[0,1],[1,1]])m.drawImage(src,a*d,b*d);
  m.globalCompositeOperation='destination-out';m.drawImage(src,0,0);
  const pixels=m.getImageData(0,0,n,n),p=pixels.data,colors=state.colors.map(rgb),t=state.time*state.speed;
  for(let y=0;y<n;y++)for(let x=0;x<n;x++){
   const i=(y*n+x)*4;if(!p[i+3])continue;
   const u=x/n,v=y/n;let c=colors[0];
   if(state.mode==='flow'){const q=fract((u+v)*state.scale-t*.22)*3,j=Math.floor(q),f=q-j;c=mix(colors[j],colors[(j+1)%3],f*f*(3-2*f));}
   if(state.mode==='rainbow')c=hue(t*.14);
   if(state.mode==='stars'){
    const w=.5+.25*Math.sin(u*state.scale*11+t)+.25*Math.sin(v*state.scale*17-t*.6);c=mix(colors[0],colors[1],w).map(v=>v*.65);
    const gx=u*state.scale*18+t*.25,gy=v*state.scale*18,ix=Math.floor(gx),iy=Math.floor(gy),h=fract(Math.sin(ix*127.1+iy*311.7)*43758.5453),r=Math.hypot(fract(gx)-.5,fract(gy)-.5);
    if(h>.63)c=mix(c,colors[2],Math.max(0,1-r/.22)*(.7+.3*Math.sin(t*2+h*9)));
   }
   p[i]=c[0];p[i+1]=c[1];p[i+2]=c[2];p[i+3]*=state.opacity;
  }m.putImageData(pixels,0,0);ctx.drawImage(mask,0,0);
 }if(state.body)ctx.drawImage(src,0,0);
}
function draw(){render($('preview'),256);render($('small16'),16);render($('small32'),32);render($('original'),64,true);}
function changed(){sync();draw();}
for(const k of ['shape','mode','width','opacity','angle','scale','speed'])$(k).addEventListener('input',()=>{state[k]=['shape','mode'].includes(k)?$(k).value:Number($(k).value);changed();});
for(const k of ['body','edge'])$(k).onchange=()=>{state[k]=$(k).checked;draw();};
state.colors.forEach((_,i)=>$('color'+i).oninput=()=>{state.colors[i]=$('color'+i).value;draw();});
$('preset').onchange=()=>{Object.assign(state,structuredClone(presets[$('preset').value]));changed();};
$('background').onchange=()=>{$('stage').className='stage '+$('background').value;};
$('play').onclick=()=>setPlaying(!playing);
for(const id of ['time','timeline'])$(id).oninput=()=>{setPlaying(false);state.time=Math.max(0,Math.min(60,Number($(id).value)||0));syncTime();draw();};
$('reset').onclick=()=>{imageTicket++;state=structuredClone(defaults);custom=null;$('preset').value='gold';$('status').textContent='';setPlaying(true);changed();};
function save(blob,name){const u=URL.createObjectURL(blob),a=document.createElement('a');a.href=u;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(u),2000);}
function recipe(){return {schema,scope:'2d-eight-neighbor-alpha-outline; not Forge model-space geometry',parameters:structuredClone(state)};}
$('export').onclick=()=>save(new Blob([JSON.stringify(recipe(),null,2)],{type:'application/json'}),'item-outline.json');
$('png').onclick=()=>$('preview').toBlob(b=>b&&save(b,'item-outline.png'));
function validate(r){
 if(!r||r.schema!==schema||!r.parameters)throw Error('配方格式不匹配');const s=r.parameters;
 for(const [k,lo,hi] of [['width',0,4],['opacity',0,1],['angle',-180,180],['scale',.5,6],['speed',0,3],['time',0,60]])if(typeof s[k]!=='number'||!Number.isFinite(s[k])||s[k]<lo||s[k]>hi)throw Error('参数超出范围：'+k);
 if(!['sword','axe','ring','custom'].includes(s.shape)||!['solid','flow','stars','rainbow'].includes(s.mode))throw Error('未知物品或材质');
 if(!Array.isArray(s.colors)||s.colors.length!==3||!s.colors.every(v=>typeof v==='string'&&/^#[0-9a-f]{6}$/i.test(v)))throw Error('需要三个 HEX 颜色');
 if(typeof s.body!=='boolean'||typeof s.edge!=='boolean')throw Error('图层开关无效');
 if(s.image!==null&&(typeof s.image!=='string'||!s.image.startsWith('data:image/png;base64,')||s.image.length>6000000))throw Error('PNG 数据无效');
 if(s.shape==='custom'&&!s.image)throw Error('缺少自定义 PNG');
 return Object.fromEntries(Object.keys(defaults).map(k=>[k,structuredClone(s[k])]));
}
function loadImage(data){return new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>{if(img.width>2048||img.height>2048||!img.width||!img.height)return reject(Error('图片边长需为 1–2048 像素'));resolve(img);};img.onerror=()=>reject(Error('PNG 解码失败'));img.src=data;});}
async function importRecipe(r){const ticket=++imageTicket,s=validate(r),img=s.image?await loadImage(s.image):null;if(ticket!==imageTicket)return;state=s;custom=img;setPlaying(false);changed();}
$('import').onclick=()=>$('recipeFile').click();$('texture').onclick=()=>$('imageFile').click();
$('recipeFile').onchange=async e=>{try{const f=e.target.files[0];if(!f)return;if(f.size>6500000)throw Error('配方文件过大');await importRecipe(JSON.parse(await f.text()));$('status').textContent='配方已载入';}catch(err){$('status').textContent=err.message;}finally{e.target.value='';}};
$('imageFile').onchange=async e=>{const ticket=++imageTicket;try{const f=e.target.files[0];if(!f)return;if(f.type!=='image/png'||f.size>4000000)throw Error('请选择 4 MB 以内的 PNG');const data=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(Error('图片读取失败'));reader.readAsDataURL(f);});const img=await loadImage(data);if(ticket!==imageTicket)return;custom=img;state.image=data;state.shape='custom';changed();$('status').textContent=f.name;}catch(err){$('status').textContent=err.message;}finally{e.target.value='';}};
let last=performance.now(),paint=last;
function tick(now){const dt=Math.min((now-last)/1000,.1);last=now;if(playing){state.time=(state.time+dt)%60;if(now-paint>=33){syncTime();draw();paint=now;}}requestAnimationFrame(tick);}
window.outlineLab={recipe,importRecipe,draw,pause:()=>setPlaying(false)};
sync();icon();draw();requestAnimationFrame(tick);
