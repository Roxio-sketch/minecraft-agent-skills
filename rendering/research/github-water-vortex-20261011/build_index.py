"""Build an offline reader. Upstream text is data, never imported or executed."""
from pathlib import Path
import json
import hashlib

ROOT = Path(__file__).resolve().parent
DESCRIPTIONS = {
    'Habrador/Unity-Tornado-Simulator': ('云与漏斗', '弯曲中心线、按高度取半径、主体绕轴运动与顶部云层衔接', 'Unity / C# + shader', '粒子龙卷，不是液体表面求解器'),
    'SebLague/Clouds': ('云与漏斗', '基形密度、边缘侵蚀、Worley 噪声和云内光照积分', 'Unity / C# + HLSL compute', '云盒，无漏斗或螺旋域'),
    'dli/fluid': ('液体与表面', 'PIC/FLIP 网格与粒子速度传递、压力投影和粒子绘制', 'WebGL / fragment passes', '液体模拟参考，粒子渲染不是闭合水片重建'),
    'matsuoka-601/Particles4All': ('液体与表面', 'PBF 密度约束、邻域搜索与各向异性屏幕空间液体表面', 'WebGPU / WGSL', '移植至现有 WebGL/Forge 需要另做后端'),
    'InteractiveComputerGraphics/splashsurf': ('液体与表面', '粒子密度场、等值面网格重建、网格后处理', 'CPU / Rust', '离线/CPU 表面重建，不模拟粒子或直接渲染折射'),
    'mrdoob/three.js': ('表面流动', '双相位流动法线、平面反射/折射与 Fresnel', 'three.js / WebGL', '已有网格的材质，曲面水柱需改坐标与厚度方案'),
    'Arnklit/Waterways': ('表面流动', '河面载体、碰撞烘焙方向图、泡沫与双相位水纹', 'Godot 3 / GDScript + shader', '河流构形不能直接沿竖直轴复制'),
    'Scrawk/Tiled-Directional-Flow': ('表面流动', '四组方向瓦片混合、局部波长和运动速度', 'Unity / surface shader', '根 MIT + 文件 Creditware；不是水体模拟'),
}

projects = []
for group in ['cloud', 'liquid', 'flow']:
    source = json.loads((ROOT/group/'manifest.json').read_text(encoding='utf-8'))
    for original in source.get('repositories',source.get('projects',[])):
        repo = original.get('repo') or original['repoUrl'].removeprefix('https://github.com/')
        commit = original.get('commit') or original['commitSHA']
        files = []
        anchors = []
        for f in original['files']:
            rel = f.get('localPath') or f.get('local_path')
            repository_path = f.get('repositoryPath') or f.get('path')
            local_path = group + '/' + rel
            path = ROOT/local_path
            data = path.read_bytes()
            assert hashlib.sha256(data).hexdigest() == f['sha256'], local_path
            assert len(data) == f['bytes'], local_path
            read_scope = f.get('readScope') or f.get('read_scope')
            ranges = f.get('readRanges',f.get('read_ranges',[]))
            if isinstance(read_scope,dict) and read_scope.get('startLine'):
                ranges = [[read_scope['startLine'],read_scope['endLine']]]
            elif isinstance(read_scope,dict) and read_scope.get('line_ranges'):
                ranges = read_scope['line_ranges']
            entry = {
                'path': repository_path, 'localPath': local_path,
                'bytes': len(data), 'sha256': f['sha256'],
                'sourceUrl': f.get('sourceUrl') or f.get('pinnedUrl') or f.get('github_url'),
                'rawUrl': f.get('rawUrl') or f.get('raw_url'),
                'lineCount': len(data.decode('utf-8-sig').splitlines()),
                'readRanges': ranges,
                'readScope': read_scope,
                'readStatus': f.get('readStatus') or ('body-read' if ranges else 'see-group-findings'),
            }
            files.append(entry)
            for a in f.get('anchors',[]):
                anchors.append({'path':repository_path,'localPath':local_path,
                    'symbol':a.get('symbol'), 'line':a['line'],
                    'needle':a.get('needle') or a.get('lineText'),
                    'url':a.get('url') or a.get('pinnedUrl') or a.get('github_url')})
        for a in original.get('anchors',[]):
            match = next(f for f in files if f['path']==a['path'])
            anchors.append({**a,'localPath':match['localPath']})
        for a in original.get('key_anchors',[]):
            match = next(f for f in files if f['path']==a['path'])
            line = a['start_line']
            line_text = (ROOT/match['localPath']).read_text(encoding='utf-8-sig').splitlines()[line-1]
            anchors.append({'path':a['path'],'localPath':match['localPath'],
                'symbol':a['symbol'],'line':line,'endLine':a['end_line'],
                'needle':line_text,'url':a['url']})
        license_file = next(f['localPath'] for f in files if f['path']=='LICENSE')
        category,purpose,platform,limitation = DESCRIPTIONS[repo]
        projects.append({'id':original['id'],'repo':repo,'commit':commit,
            'url':'https://github.com/'+repo,'license':original['license'],
            'licenseLocalPath':license_file,'category':category,
            'purpose':purpose,'platform':platform,'limitation':limitation,
            'findings':group+'/findings.md','groupManifest':group+'/manifest.json',
            'files':files,'anchors':anchors})

catalog = {'schema':'github-water-vortex-catalog/1','accessedDate':'2026-10-11',
    'scope':'selected static source; no upstream execution or renderer changes',
    'projectCount':len(projects),'upstreamFileCount':sum(len(p['files']) for p in projects),
    'upstreamBytes':sum(f['bytes'] for p in projects for f in p['files']),
    'projects':projects}
(ROOT/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

texts = {f['localPath']:(ROOT/f['localPath']).read_text(encoding='utf-8-sig')
         for p in projects for f in p['files']}
payload = json.dumps({'catalog':catalog,'texts':texts},ensure_ascii=False).replace('</',r'<\/')
template = r'''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; base-uri 'none'; object-src 'none'">
<title>水龙卷 · GitHub 源码研究</title>
<style>
:root{color-scheme:dark;font-family:system-ui,"Microsoft YaHei",sans-serif;background:#0f1720;color:#dae5ea}
body{margin:0}header{padding:26px 5vw 20px;border-bottom:1px solid #33404c;background:#152330}
h1{font-size:25px;margin:0 0 10px}p{line-height:1.75;margin:8px 0;overflow-wrap:anywhere}.muted{color:#a2b8c4;font-size:13px}a{color:#88d8e8;text-underline-offset:4px}
main{padding:22px 5vw;max-width:1480px;margin:auto}.toolbar{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:18px}
input,select,button{color:inherit;border:1px solid #4b6474;border-radius:7px;background:#192e3b;padding:10px;font:inherit;min-width:0}input{flex:1;min-width:150px}
button{cursor:pointer;max-width:100%;overflow-wrap:anywhere}button:hover{background:#264b5b}button.selected{border-color:#87d4cd;background:#285252}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:13px}.card{padding:17px;border:1px solid #3b5666;background:#182b37;border-radius:10px}
.card h2{font-size:17px;margin:6px 0;overflow-wrap:anywhere}.pill{font-size:12px;color:#9cd6d3}.card p{font-size:14px}.card button{margin-right:7px;margin-top:7px}
section{margin-top:24px;border:1px solid #3b5666;border-radius:10px;padding:18px;background:#132530}
#readtitle{font-size:18px;margin:0 0 12px}.reader-tools{display:flex;flex-wrap:wrap;gap:10px;align-items:center}#files{flex:1;max-width:100%}
#anchors{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}#anchors button{font-size:12px;padding:7px}
#code{font:13px/1.6 Consolas,monospace;white-space:pre;overflow:auto;max-height:70vh;padding:14px;background:#081720;border-radius:7px;tab-size:4}
#code span{display:block}#code span.target{background:#304d3a}#code b{display:inline-block;width:48px;color:#658494;user-select:none;font-weight:400}
@media(max-width:500px){header,main{padding:18px}h1{font-size:21px}section{padding:12px}#code{font-size:12px}}
</style></head><body>
<header><h1>水龙卷 · GitHub 源码研究</h1><p>为“螺旋感弱、间隙太多”整理：漏斗骨架 → 连续水体 → 表面流动 → 飞溅。</p>
<p class="muted" id="summary"></p><p class="muted">离线资料索引。源码作为文本展示；本页没有运行所收录的项目，也不是渲染效果工作台。</p>
<p><a href="README.md">研究路线</a> · <a href="cloud/findings.md">云与漏斗</a> · <a href="liquid/findings.md">液体与表面</a> · <a href="flow/findings.md">表面流动</a> · <a href="catalog.json">来源清单</a></p></header>
<main><div class="toolbar"><input id="search" type="search" aria-label="查找源码项目" placeholder="搜索项目、用途或平台"><select id="category" aria-label="研究分类"><option value="">全部分类</option><option>云与漏斗</option><option>液体与表面</option><option>表面流动</option></select></div>
<div class="grid" id="projects"></div>
<section id="reader" hidden><h2 id="readtitle"></h2><div class="reader-tools"><select id="files" aria-label="选择源码文件"></select><a id="source" target="_blank" rel="noopener noreferrer">固定提交源码 ↗</a></div><p class="muted" id="meta"></p><div id="anchors"></div><pre id="code"></pre></section>
</main><script type="application/json" id="data">__DATA__</script><script>
'use strict';
const data=JSON.parse(document.getElementById('data').textContent), cat=data.catalog;
let chosen=null;
const el=id=>document.getElementById(id);
el('summary').textContent=`2026-10-11 · ${cat.projectCount} 个项目 · ${cat.upstreamFileCount} 个精选原始文件 · ${(cat.upstreamBytes/1024).toFixed(1)} KiB 源码与文档 · 固定提交 / SHA256 / 原始许可`;
function node(tag,text,cls){const x=document.createElement(tag);if(text!==undefined)x.textContent=text;if(cls)x.className=cls;return x}
function showProjects(){
  const q=el('search').value.toLowerCase(), category=el('category').value;
  el('projects').replaceChildren();
  for(const p of cat.projects){
    if(category&&category!==p.category)continue;
    if(q&&!`${p.repo} ${p.purpose} ${p.platform}`.toLowerCase().includes(q))continue;
    const c=node('article',undefined,'card');
    c.append(node('div',p.category,'pill'),node('h2',p.repo),node('p',p.purpose),node('p',p.platform,'muted'),node('p',p.limitation,'muted'),node('p',`${p.license} · ${p.files.length} 文件 · ${p.commit.slice(0,12)}`,'muted'));
    const read=node('button','读源码');read.onclick=()=>selectProject(p,false);
    const license=node('button','原始许可证');license.onclick=()=>selectProject(p,true);
    const link=node('a','GitHub ↗');link.href=`${p.url}/tree/${p.commit}`;link.target='_blank';link.rel='noopener noreferrer';
    c.append(read,license,node('p'));c.lastChild.append(link);el('projects').append(c);
  }
}
function selectProject(p,license){
  chosen=p;el('reader').hidden=false;el('readtitle').textContent=p.repo;
  el('files').replaceChildren();
  const ordered=[...p.files].sort((a,b)=>a.path.localeCompare(b.path));
  for(const f of ordered){const o=node('option',f.path);o.value=f.localPath;el('files').append(o)}
  const first=license?p.licenseLocalPath:(p.anchors[0]?.localPath||ordered.find(f=>!['LICENSE','README.md'].includes(f.path))?.localPath||ordered[0].localPath);
  el('files').value=first;showFile();el('reader').scrollIntoView({behavior:'smooth',block:'start'});
}
function showFile(targetLine){
  const f=chosen.files.find(f=>f.localPath===el('files').value);el('source').href=f.sourceUrl;
  const ranges=f.readRanges.map(x=>x.join('–')).join(', ');
  const read=ranges?`实际阅读行 ${ranges}`:'阅读范围详见分组 findings / manifest';
  el('meta').textContent=`${f.bytes.toLocaleString()} 字节 · ${f.lineCount} 行 · ${read} · SHA256 ${f.sha256}`;
  el('anchors').replaceChildren();
  for(const a of chosen.anchors.filter(a=>a.localPath===f.localPath)){
    const b=node('button',`${a.symbol} · L${a.line}`);b.onclick=()=>showFile(a.line);el('anchors').append(b);
  }
  el('code').replaceChildren();
  data.texts[f.localPath].split(/\r?\n/).forEach((text,i)=>{const line=node('span');line.append(node('b',String(i+1)),document.createTextNode(text));if(i+1===targetLine)line.className='target';el('code').append(line)});
  if(targetLine)el('code').querySelector('.target')?.scrollIntoView({block:'center'});
}
el('files').onchange=()=>showFile();el('search').oninput=showProjects;el('category').onchange=showProjects;
showProjects();
</script></body></html>'''
(ROOT/'index.html').write_text(template.replace('__DATA__',payload),encoding='utf-8')
print(json.dumps({k:v for k,v in catalog.items() if k!='projects'},ensure_ascii=False))
