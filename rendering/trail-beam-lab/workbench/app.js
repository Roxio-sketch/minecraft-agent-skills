(() => {
  'use strict';
  const core = globalThis.TrailLab, presets = globalThis.TRAIL_PRESETS;
  const $ = id => document.getElementById(id);
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmt = n => Number(n).toFixed(Number.isInteger(Number(n)) ? 0 : 2);
  const pathNames = {straight:'直线',arc:'弧线',sine:'正弦波',helix:'螺旋',zigzag:'折线',lightning:'分形闪束'};
  const sectionNames = {ribbon:'朝向相机的光带',cross:'交叉光片',tube:'开口管束',blade:'双挂点刀光'};
  const profileNames = {soft:'柔边',solid:'实心',double:'双边',dash:'分节',pulse:'脉冲',plasma:'等离子',rune:'符文'};
  let project = {schema:1,recipe:core.clone(presets.helix.recipe),path:core.clone(presets.helix.path)};
  let selectedLayer = 0, tab = 'form', time = 0, playing = true, yaw = .23, pitch = -.16, zoom = 1, dragging = false, lastX = 0, lastY = 0, toastTimer;
  const canvas = $('preview'), ctx = canvas.getContext('2d');
  for (const [key, value] of Object.entries(presets)) {
    const option = document.createElement('option'); option.value = key; option.textContent = value.label; $('preset').appendChild(option);
  }
  $('preset').value = 'helix';
  function toast(message) {
    $('toast').textContent = message; $('toast').classList.add('show'); clearTimeout(toastTimer);
    toastTimer = setTimeout(() => $('toast').classList.remove('show'), 3400);
  }
  const options = (items, value) => items.map(([id,label]) => `<option value="${esc(id)}"${id === value ? ' selected' : ''}>${esc(label)}</option>`).join('');
  const select = (title,key,items,value) => `<div class="control"><label>${title}</label><select data-key="${key}">${options(items,value)}</select></div>`;
  const slider = (title,key,value,min,max,step,help='') => `<div class="control"><label>${title}<output>${fmt(value)}</output></label><input type="range" data-key="${key}" min="${min}" max="${max}" step="${step}" value="${value}">${help ? `<div class="help">${help}</div>` : ''}</div>`;
  const number = (title,key,value,min,max,step) => `<div class="control"><label>${title}</label><input type="number" data-key="${key}" min="${min}" max="${max}" step="${step}" value="${value}"></div>`;
  const text = (title,key,value,help='') => `<div class="control"><label>${title}</label><input type="text" data-key="${key}" value="${esc(value)}">${help ? `<div class="help">${help}</div>` : ''}</div>`;
  function stopRows(kind, stops) {
    return stops.map((stop, i) => `<div class="stop ${kind}" data-index="${i}">
      <input type="number" data-stop-kind="${kind}" data-stop-index="${i}" data-prop="at" min="0" max="1" step="0.01" value="${stop.at}" ${i === 0 || i === stops.length-1 ? 'disabled' : ''} title="沿程位置 0..1">
      ${kind === 'taper' ? `<input type="number" data-stop-kind="${kind}" data-stop-index="${i}" data-prop="value" min="0" max="8" step="0.05" value="${stop.value}" title="宽度倍率">` : `<input type="color" data-stop-kind="${kind}" data-stop-index="${i}" data-prop="color" value="${stop.color}" title="HEX 颜色"><input type="number" data-stop-kind="${kind}" data-stop-index="${i}" data-prop="alpha" min="0" max="1" step="0.05" value="${stop.alpha}" title="透明度">`}
      <button data-action="removeStop" data-kind="${kind}" data-index="${i}" ${i === 0 || i === stops.length-1 ? 'disabled' : ''} title="删除节点">×</button>
    </div>`).join('');
  }
  function controls() {
    const r = project.recipe, p = project.path, l = r.layers[selectedLayer];
    let content = '';
    if (tab === 'form') content = `<div class="group"><h3>轨迹形态</h3>
      ${select('截面','recipe.section',Object.entries(sectionNames),r.section)}
      ${select('路径算法','path.kind',Object.entries(pathNames),p.kind)}
      ${slider('长度 / 方块','path.length',p.length,.1,16,.1)}
      ${slider('偏移幅度','path.amplitude',p.amplitude,0,3,.01)}
      ${slider('空间周期','path.cycles',p.cycles,0,10,.05)}
      ${slider('相位速度 / 秒','path.speed',p.speed,-5,5,.05)}
      ${slider('分叉数量','path.branches',p.branches,0,4,1,'分叉会额外生成较细的路径，每条都复用当前材质层。')}
      ${slider('分叉长度','path.branchLength',p.branchLength,.05,1,.01)}
      ${number('路径种子','path.seed',p.seed,-1000000000,1000000000,1)}
      ${number('采样段数','path.samples',p.samples,8,256,1)}</div>
      <div class="group"><h3>几何接入</h3>${slider('管束边数','recipe.sides',r.sides,3,32,1)}
      <div class="help">刀光是两条真实挂点历史形成的扫掠面。画布用解析动作演示；导出的路径适用于光束模式，真实武器仍需把刃根与刃尖送入 Effects.sample。</div></div>`;
    if (tab === 'material') content = `<div class="group"><h3>材质层 <span class="help">${r.layers.length}/16</span></h3>
      <div class="layer-list">${r.layers.map((x,i) => `<button data-action="selectLayer" data-index="${i}" class="${i === selectedLayer ? 'active' : ''}">${String(i+1).padStart(2,'0')} ${esc(profileNames[x.profile])}</button>`).join('')}</div>
      <div class="small-actions"><button data-action="addLayer">＋ 新增层</button><button data-action="duplicateLayer">复制</button><button data-action="moveLayer" data-direction="-1">↑</button><button data-action="moveLayer" data-direction="1">↓</button><button data-action="removeLayer" class="danger">删除</button></div></div>
      <div class="group"><h3>第 ${selectedLayer+1} 层</h3>
      ${select('图案','layer.profile',Object.entries(profileNames),l.profile)}
      ${select('混合','layer.blend',[['alpha','半透明'],['additive','加法发光']],l.blend)}
      ${slider('宽度 / 方块','layer.width',l.width,.001,Math.max(2,l.width),.005)}
      ${slider('亮度倍率','layer.gain',l.gain,0,8,.05)}
      ${slider('沿程重复','layer.repeat',l.repeat,0,12,.05)}
      ${slider('纹理流速','layer.scroll',l.scroll,-5,5,.05)}
      ${slider('年龄淡出幂','layer.fadePower',l.fadePower,.01,8,.05)}
      ${text('纹理资源 ID','layer.texture',l.texture,'内建 trailbeam:textures/white.png 与 weave.png；自定义纹理需要放进资源目录。')}
      </div><div class="group"><h3>宽度曲线</h3><div class="help">位置 0 为起点/旧尾，1 为终点/新刃。</div>${stopRows('taper',l.taper)}<div class="small-actions"><button data-action="addStop" data-kind="taper">＋ 宽度节点</button></div></div>
      <div class="group"><h3>颜色曲线</h3>${stopRows('colors',l.colors)}<div class="small-actions"><button data-action="addStop" data-kind="colors">＋ 颜色节点</button></div></div>`;
    if (tab === 'sampling') { const s = r.sampling; content = `<div class="group"><h3>历史采样</h3>
      ${slider('轨迹存活 / 秒','sampling.lifetime',s.lifetime,.05,5,.05)}
      ${number('最短采样距离','sampling.minDistance',s.minDistance,0,5,.005)}
      ${number('最大细分步长','sampling.maxStep',s.maxStep,.001,10,.005)}
      ${number('瞬移断带距离','sampling.breakDistance',s.breakDistance,.001,10000,.1)}
      ${number('最多历史点','sampling.maxPoints',s.maxPoints,2,4096,1)}
      <div class="help">采样在模拟 tick 中完成；渲染 FPS 不应改变历史点数。光束寿命由 Effects.beam 的 expiresAt 控制。</div></div>`; }
    if (tab === 'files') content = `<div class="group"><h3>设计文件</h3>${text('设计名称','recipe.name',r.name)}
      <div class="file-actions"><button data-action="exportRecipe">导出 recipe.json</button><button data-action="exportPath">导出 path.json</button><button data-action="exportProject">导出完整设计</button><button data-action="import">导入 JSON</button><button data-action="copyProject">复制 JSON</button></div>
      <div class="notice">完整设计 JSON 用于工作台往返；Forge 实例分别读取 recipe.json 和 path.json。更新配置后执行 /trailbeam reload。</div></div>
      <div class="group"><h3>当前完整设计</h3><textarea class="json-preview" id="jsonPreview" readonly>${esc(JSON.stringify(project,null,2))}</textarea></div>`;
    $('controls').innerHTML = content;
    $('recipeName').textContent = r.name;
    $('effectTitle').textContent = r.name;
    $('effectMeta').textContent = `${sectionNames[r.section]} · ${r.layers.length} 层 · ${pathNames[p.kind]}`;
    $('layers').textContent = r.layers.length;
    document.querySelectorAll('[data-tab]').forEach(b => b.classList.toggle('active',b.dataset.tab === tab));
  }
  const atPath = key => { const parts = key.split('.'); let obj = project; for (const part of parts.slice(0,-1)) obj = part === 'layer' ? project.recipe.layers[selectedLayer] : part === 'sampling' ? project.recipe.sampling : obj[part]; return [obj,parts.at(-1)]; };
  function updateStatus() {
    try { core.validateProject(project); $('status').textContent = '配方有效'; $('status').style.color = '#70ecce'; return true; }
    catch (err) { $('status').textContent = '参数待修正'; $('status').style.color = '#ff9aa6'; $('status').title = err.message; return false; }
  }
  function updateInput(el) {
    const key = el.dataset.key; if (!key) return;
    const [obj,prop] = atPath(key); obj[prop] = el.type === 'number' || el.type === 'range' ? Number(el.value) : el.value;
    const out = el.parentElement.querySelector('output'); if (out) out.textContent = fmt(el.value);
    $('recipeName').textContent = project.recipe.name; $('effectTitle').textContent = project.recipe.name;
    updateStatus();
  }
  function updateStop(el) {
    const l = project.recipe.layers[selectedLayer], stop = l[el.dataset.stopKind][Number(el.dataset.stopIndex)];
    stop[el.dataset.prop] = el.type === 'number' ? Number(el.value) : el.value;
    updateStatus();
  }
  function download(name,content,mime='application/json') {
    const url = URL.createObjectURL(new Blob([content],{type:mime}));
    const link = document.createElement('a'); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url),1500);
  }
  function validExport() { core.validateProject(project); return JSON.stringify(project,null,2)+'\n'; }
  function act(action,button) {
    const r = project.recipe, l = r.layers[selectedLayer];
    if (action === 'selectLayer') selectedLayer = Number(button.dataset.index);
    else if (action === 'addLayer' && r.layers.length < 16) { r.layers.push(core.clone(l)); selectedLayer = r.layers.length-1; }
    else if (action === 'duplicateLayer' && r.layers.length < 16) { r.layers.splice(selectedLayer+1,0,core.clone(l)); selectedLayer++; }
    else if (action === 'removeLayer' && r.layers.length > 1) { r.layers.splice(selectedLayer,1); selectedLayer = Math.min(selectedLayer,r.layers.length-1); }
    else if (action === 'moveLayer') { const to = selectedLayer + Number(button.dataset.direction); if (to >= 0 && to < r.layers.length) { [r.layers[to],r.layers[selectedLayer]] = [r.layers[selectedLayer],r.layers[to]]; selectedLayer = to; } }
    else if (action === 'addStop') {
      const arr=l[button.dataset.kind]; if (arr.length < 32) {
        let best=0, gap=0; for(let i=0;i<arr.length-1;i++) if(arr[i+1].at-arr[i].at>gap){gap=arr[i+1].at-arr[i].at;best=i;}
        const at=Number(((arr[best].at+arr[best+1].at)/2).toFixed(4));
        arr.splice(best+1,0,button.dataset.kind==='taper'?{at,value:1}:{at,color:'#ffffff',alpha:.8});
      }
    } else if (action === 'removeStop') {
      const arr=l[button.dataset.kind],i=Number(button.dataset.index); if (i>0 && i<arr.length-1) arr.splice(i,1);
    } else if (action === 'exportRecipe') download('recipe.json',JSON.stringify((core.validateProject(project),r),null,2)+'\n');
    else if (action === 'exportPath') download('path.json',JSON.stringify((core.validateProject(project),project.path),null,2)+'\n');
    else if (action === 'exportProject') download('trail-beam-design.json',validExport());
    else if (action === 'import') $('fileInput').click();
    else if (action === 'copyProject') navigator.clipboard.writeText(validExport()).then(()=>toast('完整设计 JSON 已复制')).catch(()=>toast('复制失败，请使用导出按钮'));
    controls(); updateStatus();
  }
  document.querySelectorAll('[data-tab]').forEach(b => b.addEventListener('click',()=>{tab=b.dataset.tab;controls();}));
  $('controls').addEventListener('input', e => { if(e.target.dataset.key) updateInput(e.target); if(e.target.dataset.stopKind) updateStop(e.target); });
  $('controls').addEventListener('change', e => { if(e.target.dataset.key || e.target.dataset.stopKind) controls(); });
  $('controls').addEventListener('click', e => { const b=e.target.closest('[data-action]'); if(b) try{act(b.dataset.action,b);}catch(err){toast(err.message);} });
  $('preset').addEventListener('change',()=>{
    const selected=presets[$('preset').value]; project={schema:1,recipe:core.clone(selected.recipe),path:core.clone(selected.path)};
    selectedLayer=0; time=0; $('time').value='0'; controls();updateStatus();toast(`${selected.label} 已载入，可继续任意修改`);
  });
  $('play').addEventListener('click',()=>{playing=!playing;$('play').textContent=playing?'暂停':'播放';});
  $('newSeed').addEventListener('click',()=>{project.path.seed=Math.floor(Math.random()*2000000000)-1000000000;controls();toast('路径种子已更新');});
  $('exportProject').addEventListener('click',()=>{try{download('trail-beam-design.json',validExport());}catch(err){toast(err.message);}});
  $('exportImage').addEventListener('click',()=>download('trail-beam-preview.png',Uint8Array.from(atob(canvas.toDataURL('image/png').split(',')[1]),c=>c.charCodeAt(0)),'image/png'));
  $('time').addEventListener('input',()=>{playing=false;$('play').textContent='播放';time=Number($('time').value);$('timeValue').textContent=time.toFixed(2)+' s';});
  $('fileInput').addEventListener('change',async e=>{const file=e.target.files[0];if(!file)return;
    try {const data=JSON.parse(await file.text());
      if ('recipe' in data && 'path' in data) {core.validateProject(data);project=core.clone(data);}
      else if ('layers' in data) {core.validateRecipe(data);project.recipe=core.clone(data);}
      else {core.validatePath(data);project.path=core.clone(data);}
      selectedLayer=0;controls();updateStatus();toast(`${file.name} 已导入`);
    } catch(err){toast(`导入失败：${err.message}`);} e.target.value='';
  });
  canvas.addEventListener('pointerdown',e=>{dragging=true;lastX=e.clientX;lastY=e.clientY;canvas.setPointerCapture(e.pointerId);});
  canvas.addEventListener('pointerup',()=>{dragging=false;});
  canvas.addEventListener('pointermove',e=>{if(!dragging)return;yaw+=(e.clientX-lastX)*.006;pitch=Math.max(-1.1,Math.min(1.1,pitch+(e.clientY-lastY)*.006));lastX=e.clientX;lastY=e.clientY;});
  canvas.addEventListener('wheel',e=>{e.preventDefault();zoom=Math.max(.45,Math.min(2.7,zoom*Math.exp(-e.deltaY*.001)));},{passive:false});
  function rgba(c,a) {return `rgba(${c[0]},${c[1]},${c[2]},${Math.max(0,Math.min(1,a))})`;}
  function transformed(v,w,h,unit) {
    const x=v.x-project.path.length*.5,y=v.y,z=v.z;
    const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
    const x1=x*cy-z*sy,z1=x*sy+z*cy,y1=y*cp-z1*sp,z2=y*sp+z1*cp;
    const depth=1/(1+z2*.075);
    return {x:w*.5+x1*unit*depth,y:h*.53-y1*unit*depth,z:z2};
  }
  function gradientMaterial(l,t,age) { const c=core.colorAt(l.colors,t);return {rgb:c.rgb,alpha:c.alpha*Math.pow(1-age,l.fadePower)}; }
  function profile(l,u,v) {
    const f=((u*l.repeat-time*l.scroll)%1+1)%1,edge=Math.abs(v*2-1);
    if(l.profile==='solid')return 1;
    if(l.profile==='double')return Math.exp(-Math.pow((edge-.65)*9,2));
    if(l.profile==='dash')return edge<1 && Math.abs(f-.5)<.37 ? 1-edge : .04;
    if(l.profile==='pulse')return (1-edge)*(.14+.86*Math.exp(-Math.pow((f-.5)*6,2)));
    if(l.profile==='plasma')return (1-edge)*Math.max(.08,Math.min(1,.55+.32*Math.sin(u*21+time*2)+.26*core.hash(Math.floor(u*9),project.path.seed)));
    if(l.profile==='rune')return (1-edge)*(.14+.86*((Math.abs((u*3)%1-.5)<.08)?1:.15));
    return 1-edge;
  }
  function strokeSegment(a,b,width,material,blend) {
    if(width<.2||material.alpha<=.001)return;
    ctx.globalCompositeOperation=blend==='additive'?'lighter':'source-over';ctx.lineCap='butt';ctx.lineJoin='round';
    ctx.strokeStyle=rgba(material.rgb,material.alpha);ctx.lineWidth=width;
    ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(b.x,b.y);ctx.stroke();ctx.globalCompositeOperation='source-over';
  }
  function drawStrand(strand,l,w,h,unit) {
    const points=strand.points,scale=strand.widthScale,profileWidth=l.width*unit*scale;
    let distance=0;const lengths=[0];for(let i=1;i<points.length;i++){const a=points[i-1],b=points[i];distance+=Math.hypot(b.x-a.x,b.y-a.y,b.z-a.z);lengths.push(distance);}
    const glowColor=core.colorAt(l.colors,.5).rgb;
    ctx.globalCompositeOperation=l.blend==='additive'?'lighter':'source-over';
    ctx.strokeStyle=rgba(glowColor,.075);ctx.lineWidth=Math.min(48,Math.max(2,profileWidth*.85));
    ctx.lineCap='round';ctx.lineJoin='round';ctx.shadowColor=rgba(glowColor,.28);ctx.shadowBlur=14;
    ctx.beginPath();for(let i=0;i<points.length;i++){const v=transformed(points[i],w,h,unit);if(i===0)ctx.moveTo(v.x,v.y);else ctx.lineTo(v.x,v.y);}ctx.stroke();
    ctx.shadowBlur=0;ctx.globalCompositeOperation='source-over';
    for(let i=1;i<points.length;i++) {
      const f=(lengths[i]+lengths[i-1])/(2*Math.max(1e-6,distance));
      const u=lengths[i]/Math.max(1e-6,project.path.length);
      const taper=core.interp(l.taper,f,'value'),mat=gradientMaterial(l,f,0),p=profile(l,u,.5);
      mat.alpha*=Math.min(1,l.gain)*Math.max(.02,p)*.72;
      if(l.texture.endsWith('/weave.png'))mat.alpha*=.75+.25*Math.sin(u*40);
      const a=transformed(points[i-1],w,h,unit),b=transformed(points[i],w,h,unit),width=Math.min(48,Math.max(1,profileWidth*taper*.65));
      if(l.profile==='double'){
        const dx=b.x-a.x,dy=b.y-a.y,n=Math.hypot(dx,dy)||1,off=width*.23;
        strokeSegment({x:a.x-dy/n*off,y:a.y+dx/n*off},{x:b.x-dy/n*off,y:b.y+dx/n*off},width*.28,mat,l.blend);
        strokeSegment({x:a.x+dy/n*off,y:a.y-dx/n*off},{x:b.x+dy/n*off,y:b.y-dx/n*off},width*.28,mat,l.blend);
      }else{
        const outer={...mat,alpha:mat.alpha*.12};strokeSegment(a,b,width*1.35,outer,l.blend);
        strokeSegment(a,b,width*.42,mat,l.blend);
      }
    }
  }
  function drawBlade(l,w,h,unit) {
    const n=50,root=[],tip=[];
    for(let i=0;i<=n;i++){
      const s=i/n,a=-1.35+s*2.5+time*.30,age=(1-s)*project.recipe.sampling.lifetime;
      const center={x:project.path.length*.5-1.15,y:.10,z:0};
      root.push({x:center.x+.22*Math.cos(a),y:center.y+.22*Math.sin(a),z:0});
      tip.push({x:center.x+1.55*Math.cos(a),y:center.y+1.55*Math.sin(a),z:.08*Math.sin(a)});
    }
    for(let i=1;i<=n;i++){
      const s=i/n,fade=Math.pow(s,l.fadePower),taper=core.interp(l.taper,s,'value')*l.width,mat=gradientMaterial(l,s,1-s);
      const c0=transformed(root[i-1],w,h,unit),c1=transformed(tip[i-1],w,h,unit),d1=transformed(tip[i],w,h,unit),d0=transformed(root[i],w,h,unit);
      const cx=(c0.x+c1.x+d1.x+d0.x)/4,cy=(c0.y+c1.y+d1.y+d0.y)/4;
      const verts=[c0,c1,d1,d0].map(v=>({x:cx+(v.x-cx)*taper,y:cy+(v.y-cy)*taper}));
      ctx.globalCompositeOperation=l.blend==='additive'?'lighter':'source-over';
      const gradient=ctx.createLinearGradient(verts[0].x,verts[0].y,verts[1].x,verts[1].y);
      gradient.addColorStop(0,rgba(mat.rgb,0));gradient.addColorStop(.52,rgba(mat.rgb,mat.alpha*fade*Math.min(1,l.gain)*profile(l,s,.5)));gradient.addColorStop(1,rgba(mat.rgb,0));
      ctx.fillStyle=gradient;ctx.shadowColor=rgba(mat.rgb,.5);ctx.shadowBlur=l.width*unit*.35;
      ctx.beginPath();ctx.moveTo(verts[0].x,verts[0].y);for(let j=1;j<4;j++)ctx.lineTo(verts[j].x,verts[j].y);ctx.closePath();ctx.fill();
      ctx.shadowBlur=0;ctx.globalCompositeOperation='source-over';
    }
  }
  function background(w,h) {
    ctx.clearRect(0,0,w,h);
    ctx.strokeStyle='rgba(94,141,185,.075)';ctx.lineWidth=1;
    for(let y=h*.16;y<h;y+=34){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();}
    for(let x=w*.5%34;x<w;x+=34){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();}
    for(let i=0;i<55;i++){const x=((i*7919)%997)/997*w,y=((i*1459+371)%991)/991*h;
      ctx.fillStyle=`rgba(170,206,237,${.12+(i%5)*.035})`;ctx.fillRect(x,y,1.1,1.1);}
    ctx.fillStyle='rgba(160,190,215,.52)';ctx.font='11px ui-monospace, monospace';ctx.fillText('PREVIEW / WORLD SPACE',18,h-44);
  }
  function draw() {
    const dpr=Math.min(devicePixelRatio||1,2),w=canvas.clientWidth,h=canvas.clientHeight;
    if(canvas.width!==Math.round(w*dpr)||canvas.height!==Math.round(h*dpr)){canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);}
    ctx.setTransform(dpr,0,0,dpr,0,0);background(w,h);
    if(!updateStatus())return;
    const p=project.path,r=project.recipe,unit=Math.min(w*.72/p.length,h*.55/(Math.max(.7,p.amplitude*2)))*zoom;
    const strands=r.section==='blade'?[]:core.paths(p,time);
    for(const l of r.layers) {
      if(r.section==='blade')drawBlade(l,w,h,unit);else for(const strand of strands)drawStrand(strand,l,w,h,unit);
    }
    const count=r.section==='blade'?50:p.samples*(1+p.branches)+p.branches;
    const faces=r.section==='tube'?r.sides:r.section==='cross'?2:1;
    $('points').textContent=r.section==='blade'?'双点 × 51':String((p.samples+1)+p.branches*13);
    const quads=count*faces*r.layers.length;$('quads').textContent=quads.toLocaleString('zh-CN');
    $('quads').style.color=quads>30000?'#ffad82':'#eef5ff';
  }
  let last=performance.now();function frame(now){if(playing){time=(time+(now-last)/1000)%4;$('time').value=String(time);$('timeValue').textContent=time.toFixed(2)+' s';}last=now;draw();requestAnimationFrame(frame);}
  controls();updateStatus();requestAnimationFrame(frame);
})();
