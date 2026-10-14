const $ = id => document.getElementById(id);
const rangeDefinitions = {
  scale: ["结构尺度", 0.3, 3, 0.01], detail: ["细节密度", 0.2, 2, 0.01],
  warp: ["形变", 0, 2, 0.01], depth: ["层间深度", 0.5, 6, 0.05],
  parallax: ["观察响应", 0, 1.5, 0.01], speed: ["动态速度", 0, 1, 0.01],
  emission: ["亮部强度", 0, 3, 0.01], exposure: ["曝光", 0.3, 2.5, 0.01]
};
const colorDefinitions = {base:"主体", accent:"次色", highlight:"亮部"};
const waterDefinitions={waveHeight:["波高",0,0.65,0.005],waveLength:["波长",0.55,6,0.05],windAngle:["风向（度）",0,360,1],choppiness:["浪尖挤压",0,1,0.01],foam:["泡沫覆盖",0,1,0.01],roughness:["水面粗糙度",0.04,0.55,0.005],waterOpacity:["水面不透明度",0,1,0.01],waterLightAzimuth:["光照方位",0,360,1],waterLightElevation:["光照仰角",-0.2,1.4,0.01]};
const waterDefaults={waveHeight:0.30,waveLength:2.1,windAngle:35,choppiness:0.78,foam:0.55,roughness:0.16,waterOpacity:.90,waterTilt:.85,waterLightAzimuth:250,waterLightElevation:.62};
const oceanSpectrum=new FantasyOcean.OceanSpectrum();
const waterVariants=[
  ["平静浅潭",{waveHeight:.10,waveLength:3.8,choppiness:.2,foam:.04,roughness:.12,depth:1.2,speed:.35}],
  ["青碧风浪",{}],
  ["深海急浪",{waveHeight:.50,waveLength:1.4,choppiness:1,foam:.85,roughness:.30,depth:3.4,speed:.8,accent:[.055,.18,.21],base:[.004,.015,.03]}]
];
const families = ["GEOMETRY / 符文层", "REFLECTION / 液态金属", "ABSORPTION / 玉质", "FRACTURE / 釉面", "INTERFERENCE / 织物", "VOLUME / 折叠流层", "INTERIOR / 幻流云廊", "CURTAIN / 极光幕", "DISCHARGE / 雷晶", "FILM / 泡壳", "MECHANISM / 机芯", "BIOLOGY / 菌脉", "WATER / 水波", "SIGNAL / 电视雪花"];
const modeLabels = [
  {detail:"符文刻度",depth:"封印层距",speed:"封印旋流",emission:"刻线亮度"},
  {detail:"拉丝密度",depth:"法线起伏",speed:"流体形变",emission:"反射强度"},
  {detail:"细脉宽度",depth:"玉层厚度",speed:"脉络流动",emission:"脉光强度"},
  {detail:"裂缝宽度",depth:"偏移深度",speed:"金缝流动",emission:"金缝亮度"},
  {detail:"经纬密度",depth:"偏移深度",speed:"褶皱流动",emission:"丝光强度"},
  {detail:"热流聚集",depth:"流层深度",speed:"流层穿行",emission:"熔光强度"},
  {detail:"光脉聚集",depth:"内部纵深",speed:"云壁翻卷",emission:"云壁辉光"},
  {detail:"幕丝亮度",depth:"幕层纵深",speed:"极幕翻卷",emission:"幕光强度"},
  {detail:"电弧粗细",depth:"晶层纵深",speed:"放电节奏",emission:"电弧强度"},
  {detail:"泡体尺寸",depth:"泡层纵深",speed:"上浮速度",emission:"壳面亮部"},
  {detail:"轮齿数量",depth:"机芯层距",speed:"轮轴转速",emission:"铆钉亮部"},
  {detail:"菌丝粗细",depth:"菌层纵深",speed:"输光节奏",emission:"菌脉亮度"},
  {detail:"细浪强度",warp:"风浪散角",depth:"光学水深",speed:"波面速度",emission:"日光强度"},
  {detail:"雪花密度",warp:"信号抖动",depth:"扫描层距",speed:"帧噪速度",emission:"亮度增益"}
];
const testing = new URLSearchParams(location.search).has("test");
let presets, params, renderers, view = [0, 0], time = 0, shape = 0;
let playing = !testing && !matchMedia("(prefers-reduced-motion: reduce)").matches;
let activeFamily="fantasy";
function selectFamily(family){
  if(!["fantasy","cosmic"].includes(family))throw Error("未知材质类别");
  activeFamily=family;
  $("fantasy-workspace").hidden=family!=="fantasy";
  $("fantasy-actions").hidden=family!=="fantasy";
  const frame=$("cosmic-workspace");frame.hidden=family!=="cosmic";
  if(family==="cosmic"&&!frame.getAttribute("src"))frame.src="cosmic/index.html?embedded=1"+(testing?"&test=1":"");
  frame.contentWindow?.postMessage({type:"cosmic-workbench-active",active:family==="cosmic"},location.origin);
  document.querySelectorAll("[data-family]").forEach(button=>button.setAttribute("aria-pressed",button.dataset.family===family));
  const url=new URL(location.href);url.searchParams.set("family",family);history.replaceState(null,"",url);
}
document.querySelectorAll("[data-family]").forEach(button=>button.onclick=()=>selectFamily(button.dataset.family));
window.addEventListener("message",event=>{
  const frame=$("cosmic-workspace");
  if(event.origin!==location.origin||event.source!==frame.contentWindow)return;
  if(event.data?.type==="cosmic-workbench-height"&&Number.isFinite(event.data.height))
    frame.style.height=Math.max(600,Math.min(6000,event.data.height))+"px";
});
const vertex = `#version 300 es
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0.0,1.0);}`;
const host = `
uniform vec2 uResolution, uView;
uniform int uShape;
uniform float uExposure;
out vec4 result;
vec3 displayColor(vec3 c){
  c=1.0-exp(-max(c,vec3(0.0))*uExposure);
  return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-0.055,step(vec3(0.0031308),c));
}
vec3 materialOver(vec3 entry,vec3 ray,float fp,vec3 backing){
  vec3 color=displayColor(fantasyMaterial(entry,ray,fp));
  return fmMode==12?mix(backing,color,clamp(fmWaterOpacity,0.0,1.0)):color;
}
void main(){
  vec2 p=(gl_FragCoord.xy-uResolution*0.5)/min(uResolution.x,uResolution.y)*2.0;
  vec3 entry=vec3(p*1.5,0.0), ray=normalize(entry-vec3(uView*1.4,-3.0));
  float fp=max(length(dFdx(entry.xy)),length(dFdy(entry.xy)));
  float aa=max(fwidth(p.x),fwidth(p.y));
  vec3 bg=vec3(0.046,0.055,0.053), color=bg;
  if(uShape==0){color=materialOver(entry,ray,fp,bg);}
  else if(uShape==1){
    float w=min(0.17,max(0.0,(0.89-p.y)*0.72));
    bool blade=p.y>-0.15&&p.y<0.89&&abs(p.x)<w;
    bool guard=p.y>-0.27&&p.y<-0.16&&abs(p.x)<0.37;
    bool grip=p.y>-0.66&&p.y<=-0.27&&abs(p.x)<0.069;
    bool pommel=length((p-vec2(0.0,-0.73))*vec2(1.0,0.8))<0.117;
    if(blade){
      float inset=min(w-abs(p.x),min(p.y+0.15,0.89-p.y));
      float aperture=smoothstep(0.014,0.014+min(aa,0.03),inset);
      vec3 metal=mix(vec3(0.28,0.33,0.31),vec3(0.65,0.70,0.64),step(0.0,p.x));
      color=mix(metal,materialOver(entry,ray,fp,metal),aperture);
    }else if(guard||pommel){color=vec3(0.36,0.40,0.36)*(0.95+p.x*0.5);}
    else if(grip){color=vec3(0.11,0.14,0.12)*(0.8+0.2*sin(p.y*90.0));}
  }else{
    float r=length(p),rim=smoothstep(0.78,0.85,r);
    if(r<0.86){
      color=materialOver(entry,ray,fp,bg);
      color=mix(color,vec3(0.19,0.24,0.22)*(1.3+p.y*0.5),rim);
    }
  }
  result=vec4(color,1.0);
}`;

function makeRenderer(canvas, kernel) {
  const gl=canvas.getContext("webgl2", {alpha:false, antialias:false, preserveDrawingBuffer:true});
  if(!gl) throw Error("WebGL 2 不可用");
  gl.getExtension("EXT_color_buffer_float");gl.getExtension("OES_texture_float_linear");
  gl.disable(gl.DITHER);
  const compile=(kind, source)=>{
    const shader=gl.createShader(kind); gl.shaderSource(shader, source); gl.compileShader(shader);
    if(!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(shader));
    return shader;
  };
  const program=gl.createProgram();
  const vs=compile(gl.VERTEX_SHADER,vertex);
  const fs=compile(gl.FRAGMENT_SHADER,"#version 300 es\nprecision highp float;\nprecision highp int;\n"+kernel+host);
  gl.attachShader(program,vs); gl.attachShader(program,fs); gl.linkProgram(program);
  if(!gl.getProgramParameter(program,gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(program));
  gl.deleteShader(vs); gl.deleteShader(fs);
  const locations=new Map();
  const loc=name=>{if(!locations.has(name))locations.set(name,gl.getUniformLocation(program,name));return locations.get(name);};
  const oceanTextures=[0,1].map(i=>{
    const texture=gl.createTexture();gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,texture);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.REPEAT);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.REPEAT);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA16F,128,128,0,gl.RGBA,gl.FLOAT,new Float32Array(128*128*4));gl.generateMipmap(gl.TEXTURE_2D);
    return texture;
  });let oceanVersion=-1;
  const draw=(state)=>{
    if(gl.isContextLost()) throw Error("图形上下文已丢失，请刷新");
    const p=state.params;
    gl.viewport(0,0,canvas.width,canvas.height); gl.useProgram(program);
    gl.uniform2f(loc("uResolution"),canvas.width,canvas.height);
    gl.uniform2fv(loc("uView"),state.view); gl.uniform1i(loc("uShape"),state.shape);
    gl.uniform1f(loc("uExposure"),p.exposure); gl.uniform1i(loc("fmMode"),p.mode);
    const ocean=p.mode===12?oceanSpectrum.get(p,state.time):null;
    for(let i=0;i<2;i++){
      gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,oceanTextures[i]);gl.uniform1i(loc("fmOcean"+i),i);
      if(ocean&&ocean.version!==oceanVersion){gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,128,128,gl.RGBA,gl.FLOAT,ocean.cascades[i].map);gl.generateMipmap(gl.TEXTURE_2D);}
    }
    if(ocean){gl.uniform2f(loc("fmOceanLengths"),ocean.cascades[0].length,ocean.cascades[1].length);oceanVersion=ocean.version;}
    gl.uniform1f(loc("fmTime"),p.speed===0?0:state.time);
    for(const k of ["seed","scale","speed","detail","warp","emission","depth","parallax"])
      gl.uniform1f(loc("fm"+k[0].toUpperCase()+k.slice(1)),p[k]);
    for(const k of Object.keys(waterDefinitions))gl.uniform1f(loc("fm"+k[0].toUpperCase()+k.slice(1)),p[k]??waterDefaults[k]);
    for(const k of Object.keys(colorDefinitions)) gl.uniform3fv(loc("fm"+k[0].toUpperCase()+k.slice(1)),p[k]);
    gl.drawArrays(gl.TRIANGLES,0,3);
  };
  const pixels=()=>{const a=new Uint8Array(canvas.width*canvas.height*4);gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,a);return a;};
  return {canvas,gl,program,draw,pixels,materialTime:()=>gl.getUniform(program,loc("fmTime"))};
}
function toHex(rgb){return "#"+rgb.map(v=>Math.round((v<=0.0031308?v*12.92:1.055*Math.pow(v,1/2.4)-0.055)*255).toString(16).padStart(2,"0")).join("");}
function fromHex(hex){return [1,3,5].map(i=>{const v=parseInt(hex.slice(i,i+2),16)/255;return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);});}
function status(message,error=false){$("status").textContent=message;$("status").classList.toggle("error",error);}
function reflect(){
  $("water-sliders").hidden=params.mode!==12;
  for(const k of Object.keys(waterDefinitions)){$(k).value=params[k]??waterDefaults[k];$(k+"Value").textContent=Number(params[k]??waterDefaults[k]).toFixed(k==="windAngle"?0:2);}
  for(const k of Object.keys(rangeDefinitions)){$(k).value=params[k];$(k+"Value").textContent=Number(params[k]).toFixed(2);$(k+"Label").textContent=modeLabels[params.mode]?.[k]||rangeDefinitions[k][0];}
  for(const k of Object.keys(colorDefinitions))$(k).value=toHex(params[k]);
  $("seed").value=params.seed;$("material-name").textContent=params.name;$("mechanism").textContent=params.mechanism;
  $("motion").textContent=playing?"Ⅱ":"▶";$("motion").title=playing?"暂停":"播放";$("motion").setAttribute("aria-label",playing?"暂停":"播放");
  document.querySelectorAll("[data-preset]").forEach(el=>el.setAttribute("aria-pressed",el.dataset.preset===params.id));
  document.querySelectorAll("[data-shape]").forEach(el=>el.setAttribute("aria-pressed",Number(el.dataset.shape)===shape));
  $("state-readout").textContent=params.id+" · SEED "+params.seed;
}
function drawAll(){renderers.forEach((r,i)=>r.draw({params,view,time,shape:i?1:shape}));}
function selectPreset(id){
  const found=presets.find(p=>p.id===id);if(!found)throw Error("未知材质："+id);
  params=structuredClone(found);time=0;reflect();drawAll();status("已就绪");
}
function recipe(){return {schema:"fantasy-material/v1",parameters:structuredClone(params),preview:{view:[...view],time,shape},integration:"Preview recipe. Forge uses the documented CosmicMaterial Material selector; this JSON is not item NBT."};}
function validatedRecipe(value){
  if(!value||value.schema!=="fantasy-material/v1"||!value.parameters||!value.preview)throw Error("配方格式不匹配");
  const p=value.parameters, preset=presets.find(v=>v.id===p.id);
  if(!preset||p.mode!==preset.mode)throw Error("材质标识与算法不匹配");
  const clean=structuredClone(preset);
  for(const [k,[label,min,max]] of Object.entries({...rangeDefinitions,seed:["种子",0,65535]})){
    if(typeof p[k]!=="number"||!Number.isFinite(p[k])||p[k]<min||p[k]>max||(k==="seed"&&!Number.isInteger(p[k])))throw Error(label+"超出范围");
    clean[k]=p[k];
  }
  if(p.mode===12)for(const [k,[label,min,max]] of Object.entries(waterDefinitions)){
    const value=p[k]??(["waterOpacity","waterLightAzimuth","waterLightElevation"].includes(k)?waterDefaults[k]:undefined);
    if(typeof value!=="number"||!Number.isFinite(value)||value<min||value>max)throw Error(label+"超出范围");
    clean[k]=value;
  }
  for(const k of Object.keys(colorDefinitions)){
    if(!Array.isArray(p[k])||p[k].length!==3||p[k].some(v=>typeof v!=="number"||!Number.isFinite(v)||v<0||v>1))throw Error("色值必须为 0–1 线性 RGB");
    clean[k]=[...p[k]];
  }
  const v=value.preview;
  if(!Array.isArray(v.view)||v.view.length!==2||v.view.some(n=>typeof n!=="number"||!Number.isFinite(n)||Math.abs(n)>1.2)||!Number.isFinite(v.time)||v.time<0||![0,1,2].includes(v.shape))throw Error("预览状态不合法");
  return {params:clean,view:[...v.view],time:v.time,shape:v.shape};
}
function controls(){
  const waterPanel=document.createElement("div");waterPanel.id="water-sliders";waterPanel.className="water-sliders";waterPanel.hidden=true;
  const waterTitle=document.createElement("h2");waterTitle.textContent="水波参数";waterPanel.append(waterTitle);$("sliders").after(waterPanel);
  const waterChoices=document.createElement("div");waterChoices.className="water-choices";
  for(const [label,overrides] of waterVariants){
    const button=document.createElement("button");button.textContent=label;
    button.onclick=()=>{const original=presets.find(p=>p.id==="tidal-water");Object.assign(params,structuredClone(original),structuredClone(overrides));reflect();drawAll();status("已应用："+label);};
    waterChoices.append(button);
  }
  waterPanel.append(waterChoices);
  for(const [key,[label,min,max,step]] of Object.entries(waterDefinitions)){
    const el=document.createElement("div");el.className="control";
    el.innerHTML=`<label for="${key}"><span>${label}</span><output id="${key}Value"></output></label><input id="${key}" type="range" min="${min}" max="${max}" step="${step}">`;
    waterPanel.append(el);$(key).oninput=()=>{params[key]=Number($(key).value);reflect();drawAll();};
  }
  for(const [key,[label,min,max,step]] of Object.entries(rangeDefinitions)){
    const el=document.createElement("div");el.className="control";
    el.innerHTML=`<label for="${key}"><span id="${key}Label">${label}</span><output id="${key}Value"></output></label><input id="${key}" type="range" min="${min}" max="${max}" step="${step}">`;
    $("sliders").append(el);$(key).oninput=()=>{params[key]=Number($(key).value);reflect();drawAll();};
  }
  for(const [key,label] of Object.entries(colorDefinitions)){
    const el=document.createElement("label");el.htmlFor=key;el.innerHTML=`<input id="${key}" type="color">${label}`;$("colors").append(el);
    $(key).oninput=()=>{params[key]=fromHex($(key).value);drawAll();};
  }
  for(const p of [...presets].sort((a,b)=>(b.mode===6)-(a.mode===6))){
    const el=document.createElement("button");el.className="material";el.dataset.preset=p.id;el.setAttribute("aria-pressed","false");
    const img=document.createElement("img");img.alt="";img.id="thumb-"+p.id;
    const name=document.createElement("span");name.className="name";name.textContent=p.name;
    const family=document.createElement("span");family.className="family";family.textContent=families[p.mode];
    el.append(img,name,family);el.onclick=()=>selectPreset(p.id);$("presets").append(el);
  }
  $("seed").onchange=()=>{params.seed=Math.max(0,Math.min(65535,Math.round(Number($("seed").value)||0)));reflect();drawAll();};
  document.querySelectorAll("[data-shape]").forEach(el=>el.onclick=()=>{shape=Number(el.dataset.shape);reflect();drawAll();});
  $("reset-view").onclick=()=>{view=[0,0];drawAll();};
  $("reset-material").onclick=()=>selectPreset(params.id);
  $("motion").onclick=()=>{playing=!playing;reflect();drawAll();};
  $("export").onclick=()=>{
    const url=URL.createObjectURL(new Blob([JSON.stringify(recipe(),null,2)],{type:"application/json"}));
    const a=document.createElement("a");a.href=url;a.download=params.id+".json";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  };
  $("import").onclick=()=>$("import-file").click();
  $("import-file").onchange=async()=>{
    const file=$("import-file").files[0];if(!file)return;
    try{
      if(file.size>65536)throw Error("配方文件超过 64 KB");
      const value=validatedRecipe(JSON.parse(await file.text()));
      ({params,view,time,shape}=value);playing=false;reflect();drawAll();status("配方已载入");
    }catch(e){status("导入失败："+e.message,true);}finally{$("import-file").value="";}
  };
  let drag=null;
  $("scene").onpointerdown=e=>{drag=[e.clientX,e.clientY,...view];e.target.setPointerCapture(e.pointerId);};
  $("scene").onpointermove=e=>{if(drag){const size=$("scene").clientHeight;view=[Math.max(-1.2,Math.min(1.2,drag[2]+(e.clientX-drag[0])*2/size)),Math.max(-1.2,Math.min(1.2,drag[3]-(e.clientY-drag[1])*2/size))];drawAll();}};
  $("scene").onpointerup=$("scene").onpointercancel=()=>{drag=null;};
}

try{
  const [kernel,catalog]=await Promise.all([fetch("material.glsl").then(r=>{if(!r.ok)throw Error("材质文件不可用");return r.text();}),fetch("recipes.json").then(r=>{if(!r.ok)throw Error("配方文件不可用");return r.json();})]);
  presets=catalog.presets;renderers=["scene","icon16","icon32"].map(id=>makeRenderer($(id),kernel));
  controls();
  $("catalog-count").textContent="FANTASY & COSMIC / "+(presets.length+3);
  $("fantasy-count").textContent=String(presets.length).padStart(2,"0");
  const requestedPreset=new URLSearchParams(location.search).get("preset");
  selectPreset(presets.some(p=>p.id===requestedPreset)?requestedPreset:presets.some(p=>p.id==="fantasy-flow")?"fantasy-flow":presets[0].id);
  const thumbnail=document.createElement("canvas");thumbnail.width=240;thumbnail.height=144;
  const thumbRenderer=makeRenderer(thumbnail,kernel);
  for(const preset of presets){thumbRenderer.draw({params:preset,view:[0,0],time:0,shape:0});$("thumb-"+preset.id).src=thumbnail.toDataURL("image/png");}
  thumbRenderer.gl.getExtension("WEBGL_lose_context")?.loseContext();
  const debug=renderers[0].gl.getExtension("WEBGL_debug_renderer_info");
  window.fantasyLab={
    ready:true,presets:presets.map(p=>p.id),recipe,selectPreset,selectFamily,
    render(options={}){
      if(options.parameters)Object.assign(params,options.parameters);
      if(options.time!==undefined)time=options.time;
      if(options.view)view=[...options.view];
      if(options.shape!==undefined)shape=options.shape;
      reflect();drawAll();return recipe();
    },
    capture(index=0){return Array.from(renderers[index].pixels());},
    diagnostics(){return {
      errors:renderers.map(r=>r.gl.getError()),dither:renderers.map(r=>r.gl.isEnabled(r.gl.DITHER)),
      materialTime:renderers.map(r=>r.materialTime()),playing,testMode:testing,captureTarget:"default-framebuffer",
      renderer:debug?renderers[0].gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):"unavailable",width:800,height:540
    };}
  };
  let last=performance.now(),lastIconDraw=last;
  function tick(now){
    if(playing&&!document.hidden&&activeFamily==="fantasy")time+=Math.min((now-last)/1000,0.1);
    last=now;
    if(playing&&!document.hidden&&activeFamily==="fantasy"&&params.speed>0){
      renderers[0].draw({params,view,time,shape});
      if(now-lastIconDraw>80){renderers.slice(1).forEach(r=>r.draw({params,view,time,shape:1}));lastIconDraw=now;}
    }
    requestAnimationFrame(tick);
  }
  selectFamily(new URLSearchParams(location.search).get("family")==="cosmic"?"cosmic":"fantasy");
  if(!testing)requestAnimationFrame(tick);
}catch(e){status("预览无法运行："+e.message,true);window.fantasyLab={ready:false,error:e.message};console.error(e);}
