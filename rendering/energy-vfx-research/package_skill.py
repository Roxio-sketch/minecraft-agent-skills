from pathlib import Path
import json,re,shutil
R=Path(__file__).parent
SK=Path('C:/Users/MAINTAINER/.codex/skills/minecraft-energy-vfx-research')
data=json.loads((R/'catalog.json').read_text('utf8'))
for s in data['sources']:
    for c in s['code']:c.pop('local',None)
(SK/'references'/'catalog.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
template=(R/'workbench.template.html').read_text('utf8')
(SK/'assets'/'research-workbench.html').write_text(template.replace('__CATALOG__',json.dumps(data,ensure_ascii=False).replace('</','<\\/')),encoding='utf8')
report=(R/'研究报告.md').read_text('utf8').split('## 交付、证据与尚未验证的内容')[0]
report=re.sub(r'本地观察证据：.*?\n','案例原视频的帧图仅保存在原任务工作目录，不随 skill 分发。\n',report)
(SK/'references'/'source-map.md').write_text(report,encoding='utf8')
dest=R.parent/'skill'/'minecraft-energy-vfx-research'
if dest.exists():raise SystemExit('Workspace skill already exists; update explicitly rather than overwrite.')
shutil.copytree(SK,dest)
print('Packaged installed and workspace skills',dest)
