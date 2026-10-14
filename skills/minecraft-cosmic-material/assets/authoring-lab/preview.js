const $ = (id) => document.getElementById(id);
const embedded=new URLSearchParams(location.search).has("embedded");
let workbenchActive=true;
if(embedded){
  document.body.classList.add("embedded");
  window.addEventListener("message",event=>{
    if(event.origin===location.origin&&event.source===parent&&event.data?.type==="cosmic-workbench-active")
      workbenchActive=event.data.active===true;
  });
  new ResizeObserver(()=>parent.postMessage({type:"cosmic-workbench-height",height:Math.ceil(document.querySelector("main").getBoundingClientRect().height)},location.origin)).observe(document.querySelector("main"));
}
const ranges = {
  density: ["星点数量",0,2,0.01], starSize: ["星点尺度",0.4,3,0.02],
  cloud: ["星云浓度",0,3,0.01], dust: ["暗尘遮挡",0,3,0.01],
  depth: ["虚拟深度",1,6,0.05], parallax: ["视差强度",0,1.5,0.01],
  scale: ["空间频率",0.4,2,0.01], speed: ["星云缓慢流动",0,0.8,0.005],
  starSpeed: ["星点漂移",0,1,0.01], twinkle: ["星点闪烁强度",0,1,0.01],
  twinkleRate: ["星点闪烁速度",0,3,0.05], exposure: ["整体曝光",0.4,2,0.01]
};
const colors = {void:"底场",cloudA:"云体暗部",cloudB:"云体亮部",starTint:"星点色调"};
let params, presets, renderers, activeId, shape = 0, view = [0,0], time = 0;
let playing = !matchMedia("(prefers-reduced-motion: reduce)").matches;
const testing = new URLSearchParams(location.search).has("test");
if (testing) playing = false;
const toggles = {starsOn:1,cloudsOn:1,dustOn:1};
const scalar = [...Object.keys(ranges),"seed","time",...Object.keys(toggles)];
const vs = `#version 300 es
void main(){ vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2)); gl_Position=vec4(p*2.0-1.0,0,1); }`;
const host = `
uniform vec2 uResolution, uView;
uniform int uShape;
out vec4 result;
vec3 toDisplay(vec3 v) {
  v = 1.0-exp(-max(v,vec3(0))*uExposure);
  return mix(12.92*v, 1.055*pow(v,vec3(1.0/2.4))-0.055,step(vec3(0.0031308),v));
}
void main(){
  vec2 p=(gl_FragCoord.xy-uResolution*0.5)/min(uResolution.x,uResolution.y)*2.0;
  vec3 entry=vec3(p*1.5,0);
  vec3 origin=vec3(uView*1.4,-3.0);
  vec3 ray=normalize(entry-origin);
  float footprint=max(length(dFdx(entry.xy)),length(dFdy(entry.xy)));
  float edge=max(fwidth(p.x),fwidth(p.y));
  vec3 col;
  if(uShape==0) { col=toDisplay(cosmicMaterial(entry,ray,footprint)); }
  else {
    float bladeWidth=min(0.14,max(0.0,(0.84-p.y)*0.72));
    bool blade=p.y>-0.10 && p.y<0.84 && abs(p.x)<bladeWidth;
    bool guard=p.y>-0.22 && p.y<-0.11 && abs(p.x)<0.34;
    bool grip=p.y>-0.64 && p.y<=-0.22 && abs(p.x)<0.066;
    bool pommel=length((p-vec2(0,-0.70))*vec2(1,0.8))<0.115;
    col=vec3(0.029,0.041,0.065);
    if(blade){
      float inset=min(bladeWidth-abs(p.x),min(p.y+0.10,0.84-p.y));
      float aperture=smoothstep(0.018,0.018+min(edge,0.035),inset);
      vec3 metal=vec3(0.25,0.36,0.48)*(0.85+p.x*1.3);
      col=mix(metal,toDisplay(cosmicMaterial(entry,ray,footprint)),aperture);
    } else if(guard || pommel) col=vec3(0.23,0.32,0.43)*(0.9+p.x*0.6);
    else if(grip) col=vec3(0.10,0.12,0.16)*(0.8+0.2*sin(p.y*90.0));
  }
  result=vec4(col,1);
}`;

function makeRenderer(canvas,kernel) {
  const gl=canvas.getContext("webgl2",{alpha:false,antialias:false,preserveDrawingBuffer:true});
  if (!gl) throw new Error("此浏览器无法建立 WebGL 2 上下文；请检查图形加速设置。");
  // Make the 8-bit capture pipeline explicit; this is not a claim about the
  // cause of previously reported one-LSB differences.
  gl.disable(gl.DITHER);
  const shader=(type,source)=>{
    const s=gl.createShader(type); gl.shaderSource(s,source); gl.compileShader(s);
    if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
    return s;
  };
  const p=gl.createProgram();
  const v=shader(gl.VERTEX_SHADER,vs);
  const f=shader(gl.FRAGMENT_SHADER,"#version 300 es\nprecision highp float;\nprecision highp int;\n"+kernel+host);
  gl.attachShader(p,v); gl.attachShader(p,f); gl.linkProgram(p);
  if(!gl.getProgramParameter(p,gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
  gl.deleteShader(v);gl.deleteShader(f);
  const loc={};
  const uniform=(name)=>loc[name]??(loc[name]=gl.getUniformLocation(p,name));
  const draw=(icon=false)=>{
    if(gl.isContextLost()) throw new Error("图形上下文已丢失，请刷新制作台。");
    gl.viewport(0,0,canvas.width,canvas.height);gl.useProgram(p);
    gl.uniform2f(uniform("uResolution"),canvas.width,canvas.height);
    gl.uniform2fv(uniform("uView"),view);
    gl.uniform1i(uniform("uShape"),icon?1:shape);
    gl.uniform1i(uniform("uSteps"),params.steps);
    // A fully static material has no time input. Preserve requested time in the
    // recipe, but do not perturb GPU uniforms with an irrelevant clock value.
    const staticTime=params.speed===0 && params.starSpeed===0 && (params.twinkle===0 || params.twinkleRate===0);
    const values={...params,...toggles,time:staticTime?0:time};
    for(const k of scalar) gl.uniform1f(uniform("u"+k[0].toUpperCase()+k.slice(1)),values[k]);
    for(const k of Object.keys(colors)) gl.uniform3fv(uniform("u"+k[0].toUpperCase()+k.slice(1)),params[k]);
    gl.drawArrays(gl.TRIANGLES,0,3);
  };
  const pixels=()=>{
    const a=new Uint8Array(canvas.width*canvas.height*4);
    gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,a);
    return a;
  };
  return {canvas,gl,draw,pixels};
}

function toHex(rgb){return "#"+rgb.map(v=>Math.round((v<=0.0031308?v*12.92:1.055*Math.pow(v,1/2.4)-0.055)*255).toString(16).padStart(2,"0")).join("");}
function fromHex(hex){return [1,3,5].map(i=>{const v=parseInt(hex.slice(i,i+2),16)/255;return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);});}
function reflect(){
  for(const k of Object.keys(ranges)){$(k).value=params[k];$(k+"Value").textContent=Number(params[k]).toFixed(2);}
  for(const k of Object.keys(colors))$(k).value=toHex(params[k]);
  $("steps").value=params.steps;$("seed").value=params.seed;$("brief").textContent=params.brief;
  $("motion").textContent=playing?"暂停动态":"继续动态";
  document.querySelectorAll("[data-preset]").forEach(b=>b.classList.toggle("active",b.dataset.preset===activeId));
  $("field").classList.toggle("active",shape===0);$("sword").classList.toggle("active",shape===1);
}
function drawAll(){renderers.forEach((r,i)=>r.draw(i>0));}
function selectPreset(id){
  const p=presets.find(p=>p.id===id);if(!p)throw new Error("Unknown preset: "+id);
  params=structuredClone(p);activeId=id;time=0;reflect();drawAll();
}
function recipe(){return {schema:"cosmic-authoring-preview/v1",parameters:structuredClone(params),layers:{...toggles},preview:{view:[...view],time,shape:shape?"blade-aperture":"field"},integration:"WebGL study only. Implement parameters, coordinate adapter and lifecycle before Forge use."};}
function controls(){
  for(const [key,[label,min,max,step]] of Object.entries(ranges)){
    const div=document.createElement("div");div.className="control";
    div.innerHTML=`<label for="${key}">${label}<output id="${key}Value"></output></label><input id="${key}" type="range" min="${min}" max="${max}" step="${step}">`;
    $("sliders").append(div);$(key).addEventListener("input",()=>{params[key]=Number($(key).value);reflect();drawAll();});
  }
  for(const [key,label] of Object.entries(colors)){
    const el=document.createElement("label");el.htmlFor=key;el.innerHTML=`${label}<input id="${key}" type="color">`;
    $("colors").append(el);$(key).addEventListener("input",()=>{params[key]=fromHex($(key).value);drawAll();});
  }
  for(const p of presets){const b=document.createElement("button");b.textContent=p.name;b.dataset.preset=p.id;b.onclick=()=>selectPreset(p.id);$("presets").append(b);}
  for(const k of Object.keys(toggles))$(k).addEventListener("change",()=>{toggles[k]=$(k).checked?1:0;drawAll();});
  $("steps").onchange=()=>{params.steps=Number($("steps").value);drawAll();};
  $("seed").onchange=()=>{params.seed=Math.max(0,Math.min(65535,Math.round(Number($("seed").value)||0)));reflect();drawAll();};
  $("field").onclick=()=>{shape=0;reflect();drawAll();};$("sword").onclick=()=>{shape=1;reflect();drawAll();};
  $("motion").onclick=()=>{playing=!playing;reflect();drawAll();};
  $("reset-view").onclick=()=>{view=[0,0];drawAll();};
  $("export").onclick=()=>{
    const url=URL.createObjectURL(new Blob([JSON.stringify(recipe(),null,2)],{type:"application/json"}));
    const a=document.createElement("a");a.href=url;a.download="cosmic-"+activeId+".json";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  };
  let start=null;
  $("scene").onpointerdown=e=>{start=[e.clientX,e.clientY,...view];e.target.setPointerCapture(e.pointerId);};
  $("scene").onpointermove=e=>{if(start){const s=$("scene").clientHeight;view=[Math.max(-1.2,Math.min(1.2,start[2]+(e.clientX-start[0])*2/s)),Math.max(-1.2,Math.min(1.2,start[3]-(e.clientY-start[1])*2/s))];drawAll();}};
  $("scene").onpointerup=()=>{start=null;};$("scene").onpointercancel=()=>{start=null;};
}

try{
  const [kernel,catalog]=await Promise.all([fetch("material.glsl").then(r=>{if(!r.ok)throw new Error("Shader file unavailable");return r.text();}),fetch("recipes.json").then(r=>r.json())]);
  presets=catalog.presets;
  $("scene").width=800;$("scene").height=540;
  renderers=["scene","icon16","icon32"].map(id=>makeRenderer($(id),kernel));
  controls();
  if(embedded)for(const preset of presets){
    selectPreset(preset.id);
    const button=document.querySelector(`[data-preset="${preset.id}"]`),img=document.createElement("img"),name=document.createElement("span");
    img.alt="";img.src=$("scene").toDataURL("image/png");name.textContent=preset.name;
    button.replaceChildren(img,name);
  }
  selectPreset("violet-nebula");
  $("status").textContent=embedded?"已就绪":"材质已编译。参数作用于真实 WebGL 着色器。暂停只停止时间，仍可拖动视角。";
  const debug=renderers[0].gl.getExtension("WEBGL_debug_renderer_info");
  window.cosmicLab={
    ready:true,presets:presets.map(p=>p.id),recipe,selectPreset,
    render(options={}){
      if(options.parameters)Object.assign(params,options.parameters);
      if(options.time!==undefined)time=options.time;
      if(options.view)view=[...options.view];
      if(options.shape!==undefined)shape=options.shape;
      if(options.layers)Object.assign(toggles,options.layers);
      reflect();drawAll();return recipe();
    },
    capture(index=0){return Array.from(renderers[index].pixels());},
    diagnostics(){return {
      errors:renderers.map(r=>r.gl.getError()),
      dither:renderers.map(r=>r.gl.isEnabled(r.gl.DITHER)),
      materialTime:renderers.map(r=>r.gl.getUniform(r.gl.getParameter(r.gl.CURRENT_PROGRAM),r.gl.getUniformLocation(r.gl.getParameter(r.gl.CURRENT_PROGRAM),"uTime"))),
      playing,testMode:testing,
      captureTarget:"default-framebuffer",
      renderer:debug?renderers[0].gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):"unavailable",width:800,height:540
    };}
  };
  let last=performance.now(),lastIcons=last;
  function tick(now){
    if(playing&&!document.hidden&&workbenchActive)time+=Math.min((now-last)/1000,0.1);
    last=now;
    if(playing&&!document.hidden&&workbenchActive){
      renderers[0].draw();
      if(now-lastIcons>=100){renderers.slice(1).forEach(r=>r.draw(true));lastIcons=now;}
    }
    requestAnimationFrame(tick);
  }
  if(!testing)requestAnimationFrame(tick);
}catch(e){$("status").classList.add("error");$("status").textContent="预览无法运行："+e.message;window.cosmicLab={ready:false,error:e.message};console.error(e);}
