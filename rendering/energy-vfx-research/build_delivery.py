from pathlib import Path
import json, hashlib, subprocess, urllib.parse, copy
from catalog import SOURCES, LAYERS, ROADMAP
R=Path(__file__).parent
records=copy.deepcopy(SOURCES)
manifest=[]
for s in records:
    p=R/'sources'/s['folder']
    if s['id']=='effekseer':
        sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=p,text=True).strip()
        meta={'repo':'effekseer/Effekseer','branch':'master','commit':sha}
    else: meta=json.loads((p/'metadata.json').read_text('utf8'))
    s.update(commit=meta.get('commit'),branch=meta.get('branch','论文配套归档'),checked_date='2026-10-02',repo=meta.get('repo'),stars=meta.get('stars'))
    s['url']='https://github.com/'+s['repo'] if s['commit'] else 'https://www.cs.ubc.ca/~rbridson/'
    s['code']=[]
    for f,symbol in s.pop('files'):
        path=p/f
        if not path.exists():raise RuntimeError(f'Missing {s["id"]}: {f}')
        body=path.read_text('utf8',errors='replace')
        lines=body.splitlines()
        found=next((i+1 for i,line in enumerate(lines) if symbol in line),None)
        if found is None:print('No symbol anchor; file verified:',s['id'],f,symbol)
        url=f"https://github.com/{s['repo']}/blob/{s['commit']}/{urllib.parse.quote(f)}" if s['commit'] else meta['source_url']
        if found and s['commit']:url+=f'#L{found}'
        s['code'].append({'path':f,'symbol':symbol,'line':found,'url':url,'local':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        manifest.append({'id':s['id'],'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size})
    if s.get('license_file'):
        path=p/s['license_file']
        if not path.exists():raise RuntimeError(f'Missing license {path}')
        s['license_url']=f"https://github.com/{s['repo']}/blob/{s['commit']}/{urllib.parse.quote(s['license_file'])}"
    if s.get('version_file'):
        path=p/s['version_file']
        if not path.exists():raise RuntimeError(f'Missing version {path}')
        s['version_url']=f"https://github.com/{s['repo']}/blob/{s['commit']}/{urllib.parse.quote(s['version_file'])}"
papers=[
 {'title':'Curl-Noise for Procedural Fluid Flow · SIGGRAPH 2007','url':'https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf','why':'势函数、旋度、边界与空间调制；代码许可由作者主页独立确认。'},
 {'title':'Tiling Simplex Noise and Flow Noise in Two and Three Dimensions · JCGT 2022','url':'https://jcgt.org/published/0011/01/02/','why':'psrdnoise 的旋转梯度、解析导数与周期；GLSL 文件内有 MIT。'},
 {'title':'GPU Gems · Chapter 38 · Fast Fluid Dynamics Simulation on the GPU','url':'https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu','why':'二维稳定流体的方程与 GPU ping-pong 组织；公开阅读不代表任意样例复制授权。'},
 {'title':'GPU Gems 3 · Chapter 30 · Real-Time Simulation and Rendering of 3D Fluids','url':'https://developer.nvidia.com/gpugems/gpugems3/part-v-physics-simulation/chapter-30-real-time-simulation-and-rendering-3d-fluids','why':'三维模拟、局部域、深度融合与渲染；与 Scrawk 实现配对阅读。'},
 {'title':'OpenGL 4.3 Core Specification · Khronos','url':'https://registry.khronos.org/OpenGL/specs/gl/glspec43.core.pdf','why':'计算着色器、SSBO、同步与能力边界。Forge 1.20.1 的版本不自动保证玩家机器有该能力。'},
 {'title':'Forge 1.20.1 Documentation','url':'https://docs.minecraftforge.net/en/1.20.1/','why':'固定目标版本。渲染框架源码仍需对照实际 MOD 的 build 配置。'}
]
segments=[
 {'time':50,'label':'00:50 · 灰度能量体','text':'卷动薄片、空洞和稀薄边缘；用它评估关闭发光后的结构。'},
 {'time':58,'label':'00:58 · 细丝与尾部','text':'观察内部与外沿运动尺度是否不同，避免把所有噪声同速移动。'},
 {'time':64,'label':'01:04 · 冲击环素材','text':'环状传播与球形主体分开制作；本段是合成素材展示。'},
 {'time':78,'label':'01:18 · 火星辅助','text':'粒子提供速度和方向；关闭后主体仍应成立。'},
 {'time':110,'label':'01:50 · 烟雾层','text':'烟的稀疏、遮蔽和灰度层次与自发光能量区分。'},
 {'time':132,'label':'02:12 · 合成与调色','text':'画面展示 Screen 混合和色相调整；不据此推断素材的原始生成算法。'},
 {'time':142,'label':'02:22 · 核心与外围','text':'高亮中心、细丝和低密度外晕共同构成层次；注意过曝可能掩盖细节。'}
]
payload={'schema':'energy-vfx-research/v1','date':'2026-10-02','scope':'研究阶段；不包含可运行 Minecraft 实现或能量渲染器','sources':records,'layers':LAYERS,'roadmap':ROADMAP,'papers':papers,'segments':segments}
(R/'catalog.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf8')
(R/'evidence'/'source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
html=(R/'workbench.template.html').read_text('utf8').replace('__CATALOG__',json.dumps(payload,ensure_ascii=False).replace('</','<\\/'))
(R/'index.html').write_text(html,encoding='utf8')
intro=(R/'report-intro.md').read_text('utf8')
byid={s['id']:s for s in records}
report=[intro,'\n## 效果组成 → 技术 → 阅读入口\n','| 效果组成 | 对应技术 | 推荐源码 | 验收方向 |\n|---|---|---|---|']
for l in LAYERS:
    links='、'.join(f"[{byid[i]['name'].split(' / ')[-1]}]({byid[i]['url']})" for i in l['sources'])
    report.append(f"| {l['name']} | {l['tech']} | {links} | {l['accept']} |")
report.append('\n## 17 组源码阅读档案\nP0 表示优先读懂的基础；P1 为第二轮深入；P2 为补充或带有明确限制的对照。优先级针对本任务，不是对项目总体质量的评分。所有条目只做静态源码检查，未运行外部 Unity / C++ / Minecraft 项目。\n')
for s in records:
    report.extend([f"\n### {s['priority']} · {s['name']}\n",f"- 原项目：[{s['repo']}]({s['url']})；分支 `{s['branch']}`；"+(f"固定提交 `{s['commit']}`。" if s['commit'] else '作者示例归档，SHA256 见 evidence/source-manifest.json。'),f"- 参考价值：{s['why']}",f"- 学什么：{s['learn']}",f"- 迁移判断：{s['migrate']}",f"- 边界：{s['limit']}",f"- 许可：{s['license']}。"+(f"[依据]({s['license_url']})" if s.get('license_url') else '未找到整体授权，保持仅研究；不能推定允许复制。'),'- 已核查的源码入口：'])
    for c in s['code']:report.append(f"  - [{c['path']}]({c['url']})"+(f" — `{c['symbol']}`，第 {c['line']} 行。" if c['line'] else ' — 文件已存在，未提供符号锚点。'))
    if s.get('version_url'):report.append(f"- 版本依据：[构建或 MOD 元数据]({s['version_url']})。")
report.append('\n## 权威资料配对阅读\n')
for p in papers:report.append(f"- [{p['title']}]({p['url']})：{p['why']}")
report.append('\n## 建议的技术学习顺序\n')
for step in ROADMAP:
    report.extend([f"\n### {step['title']}\n",step['goal'],f"\n阅读："+'、'.join(f"[{byid[i]['name']}]({byid[i]['url']})" for i in step['ids']),f"\n完成标准：{step['done']}"])
report.append((R/'report-end.md').read_text('utf8'))
(R/'研究报告.md').write_text('\n'.join(report),encoding='utf8')
print('Built',len(records),'source dossiers;',len(manifest),'verified entry files;',len(html),'HTML characters')
