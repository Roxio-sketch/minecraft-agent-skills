"""Package the original workbench pages and selected research without shaderpacks."""
import hashlib
import json
import posixpath
import re
import shutil
import zipfile
from pathlib import Path

LAB = Path(__file__).resolve().parents[1]
SKILL = LAB.parent / 'skill/minecraft-volumetric-environment'
OUT = LAB.parent / 'deliverables/world-volume'
OUT.mkdir(parents=True, exist_ok=True)
payload = {}
for folder in ('workbench', 'references'):
    source = SKILL / folder if folder == 'references' else LAB / folder
    for file in source.rglob('*'):
        if file.is_file() and file.suffix != '.zip' and '__pycache__' not in file.parts:
            payload[folder + '/' + file.relative_to(source).as_posix()] = file.read_bytes()
for name in ('recipe.py', 'verify_vortex.cjs', 'verify_vortex_revision8.cjs', 'preview_vortex_revision8.cjs', 'verify_vortex_revision9.cjs', 'preview_vortex_revision9.cjs', 'preview_water_iteration.cjs', 'package_water_motion.py'):
    payload['scripts/' + name] = (LAB / 'scripts' / name).read_bytes()
for fixture in ('scripts/fixtures/frozen-vortex-core-iteration1.glsl',
                'scripts/fixtures/frozen-vortex-preview-revision8.js',
                'scripts/fixtures/vortex-bowl-camera-revision9.json'):
    payload[fixture] = (LAB / fixture).read_bytes()
for file in (LAB / 'forge/src/main/shader').glob('*.glsl'):
    payload[file.relative_to(LAB).as_posix()] = file.read_bytes()
for name in ('report.json', 'audit.md', 'replay-sampling-diagnosis.json', 'mode-0-time-20.png', 'mode-1-time-20.png', 'mode-2-time-20.png', 'mode-1-liquid.png', 'mode-2-liquid.png', 'download.json'):
    payload['evidence/vortex/' + name] = (LAB / 'evidence/vortex' / name).read_bytes()
payload['evidence/vortex-water-iteration/report.json'] = (LAB / 'evidence/vortex-water-iteration/report.json').read_bytes()
for name in ('report.json', 'animation-manifest.json', 'mode-1-motion.gif', 'mode-2-motion.gif', 'bowl-water9-near.gif', 'bowl-mouth9.gif'):
    payload['evidence/water-sheets-iteration/' + name] = (LAB / 'evidence/water-sheets-iteration' / name).read_bytes()
for iteration in (8, 9):
    for folder in ('verify', 'review'):
        for file in (LAB / f'evidence/vortex-revision{iteration}' / folder).iterdir():
            if file.is_file() and file.suffix in ('.json', '.png', '.md'):
                payload[file.relative_to(LAB).as_posix()] = file.read_bytes()
        for file in (LAB / f'evidence/vortex-revision{iteration}' / folder / 'history').rglob('*'):
            if file.is_file() and file.suffix in ('.json', '.md'):
                payload[file.relative_to(LAB).as_posix()] = file.read_bytes()
for file in (LAB / 'evidence/vortex-revision9').iterdir():
    if file.is_file() and file.suffix in ('.json', '.md'):
        payload[file.relative_to(LAB).as_posix()] = file.read_bytes()
for relative in ('evidence/vortex-revision8/visual-criteria.md',
                 'evidence/vortex/history/continuity7/workbench/vortex/preview.js',
                 'evidence/vortex/history/layered-water8/workbench/vortex/preview.js',
                 'evidence/vortex/history/layered-water8/report.json',
                 'evidence/vortex/history/layered-water8/portable.json',
                 'evidence/vortex/history/bowl9-before-signed-square/report.json',
                 'evidence/vortex/history/layered-water8/evidence/water-sheets-iteration/report.json',
                 'evidence/vortex/history/layered-water8/evidence/water-sheets-iteration/animation-manifest.json',
                 'evidence/vortex/history/layered-water8/evidence/water-sheets-iteration/layered-water8.gif',
                 'evidence/vortex-revision7/iab-final.png',
                 'evidence/vortex-revision7/review/mode-2-near.png',
                 'evidence/vortex-revision7/review/mode-1-mature.png'):
    payload[relative] = (LAB / relative).read_bytes()
for revision in ('v8a', 'v8d'):
    for frame in ('mode-2-outside', 'mode-2-liquid', 'mode-1-outside'):
        relative = f'evidence/vortex-revision8/dev/{revision}-{frame}.png'
        payload[relative] = (LAB / relative).read_bytes()
payload['evidence/vortex-revision8/change-manifest.json'] = (LAB / 'evidence/vortex-revision8/change-manifest.json').read_bytes()
diagnosis = (LAB / 'evidence/vortex-revision8/material-diagnosis.md').read_text(encoding='utf-8')
diagnosis = diagnosis.replace('../../workbench/vortex/preview.js', '../vortex/history/continuity7/workbench/vortex/preview.js')
payload['evidence/vortex-revision8/material-diagnosis.md'] = diagnosis.encode('utf-8')
payload['LICENSE'] = (LAB / 'LICENSE').read_bytes()
# Carry small documents/source files linked by staged references so the
# standalone research reader has the same usable links as the skill.
queue = list(payload)
for name in queue:
    if not name.endswith('.md'):
        continue
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', payload[name].decode('utf-8')):
        target = target.split('#')[0]
        if not target or target.startswith(('http:', 'https:', 'app:', 'codex:')):
            continue
        relative = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
        if relative in payload:
            continue
        source = SKILL / relative if relative.startswith(('references/', 'assets/')) else LAB / relative
        assert source.is_file(), 'Missing portable research link: ' + relative
        payload[relative] = source.read_bytes()
        queue.append(relative)
validation = json.loads((LAB / 'evidence/vortex/report.json').read_text(encoding='utf-8'))
revision = json.loads((LAB / 'evidence/vortex-revision9/verify/report.json').read_text(encoding='utf-8'))
visual = json.loads((LAB / 'evidence/vortex-revision9/review/report.json').read_text(encoding='utf-8'))
# Packaging certifies source/evidence identity. Preserve rendering failures in the
# bundled report and README rather than claiming a completely passing renderer.
for key, name in (('coreSha256', 'volume-core.glsl'), ('previewSha256', 'preview.js'), ('noiseGeneratorSha256', 'vortex-noise.js')):
    assert validation[key] == hashlib.sha256((LAB / 'workbench/vortex' / name).read_bytes()).hexdigest(), 'Stale renderer verification: ' + name
assert revision['previewSha256'] == validation['previewSha256'] and visual['previewSha256'] == validation['previewSha256'], 'Stale revision verification'
motion = json.loads((LAB / 'evidence/water-sheets-iteration/report.json').read_text(encoding='utf-8'))
assert motion['ok'] and motion['previewSha256'] == validation['previewSha256'], 'Motion review belongs to another liquid implementation'
check_count = len(validation['checks'])
passed_count = sum(check.get('passed') is True for check in validation['checks'])
failed_names = [check['name'] for check in validation['checks'] if not check.get('passed')]
revision_count = sum(c['passed'] for c in revision['checks'])
visual_count = sum(c['passed'] for c in visual['checks'])
replay_note = ('严格 PNG 字节比较仍有失败；独立逐帧 RGBA 比较的范围与差异按视觉报告分别记录。'
               '配方、GPU 输入和素材指纹可复核，本包保留失败记录。' if failed_names else '主页面本次列出的检查全部通过；独立视觉结果另见对应报告。')
payload['README.md'] = f'''# 陆地龙卷、龙吸水与水龙卷

运行 `python workbench/serve.py --port 8774`，打开 http://127.0.0.1:8774/vortex/。
同一服务提供 `/tribulation/` 渡劫云和 `/` 基础体积环境。

本包包括原创三模式工作台、精选研究及 WebGL/UI 验证记录（{passed_count}/{check_count} 项通过）。涡旋作品使用
vortex-cloud/1 JSON；形成、漏斗、云帽、喷环与程序水面均可调整。
`mode=0` 为陆地体积云漏斗；`mode=1` 为海面吸起的多层不规则液片、上段云雾与碎水；
`mode=2` 为下段连续、上段碗口张开的水龙卷，带重叠薄水片和飞溅，少量云雾辅助层次。
基础总密度范围 0–5，截图和 JSON 可直接导出。
本轮使用本地生成的三维云噪声、近处自遮光和不规则喷雾，水面采用多方向风浪与破碎泡沫。
较小连续水团连接三层薄皮、局部翻卷片和随水流上升的水片，96 个有限生命周期椭球滴表现甩水。
本轮将水龙卷顶部改为向上扩张的凹形薄壳与不规则卷边，内部空腔同时裁掉旧内核和辅助片，避免水柱尖顶堵住碗口；下段仍保持液体占据。
反射与透射共用细法线、Fresnel 和局部泡沫，去掉重复青色基底，碎滴加入简化透射背景。
水体、离散滴、海面与体积雾分别渲染。24 次占据采样估算透光路径；透射背景采用简化云积分和平均海面/天空近似。
柱面的细水纹、高光与泡沫沿旋转和上升方向流动，海面流痕结合风向、旋转与向心输运。
螺旋水痕和泡沫跟随轮廓的同一盘旋方向，局部噪声保持水片不规则。
水柱采用周期局部坐标，粗轮廓、水纹和泡沫共用相位，速度包含弯曲中心线与标架的运动；海面保留有界双相位输运。
旋转、上升、向心、波速和泡沫控件可调整流动表现，没有新增配方字段。
三模式的固定时间截图位于 evidence/vortex/mode-0-time-20.png 至 mode-2-time-20.png。
具体检查及源码指纹以 evidence/vortex/report.json 为准，检查通过不等于艺术效果获得认证。
实际帧与本轮独立复核记录位于 evidence/vortex-revision9/；旧版的果冻感、灰布感与严格重放失败保留于 revision8 及 history/layered-water8，不以技术通过替代本轮造型判断。
{replay_note}

研究与 skill 引导见 references/event-workbenches.md；原包源码和纹理没有再分发。
液片与破碎的一手阅读范围见 references/research/vortex-waterspout-20261011/WATER_FUNNEL_RESEARCH.md。
本轮对规则带状水片的重做见 references/research/vortex-waterspout-20261011/WATER_BODY_REVISION.md。
evidence/water-sheets-iteration/ 的 GIF 是固定时间实际渲染帧组成的动画。
bowl-mouth9.gif 是本轮顶部碗口视角的实际动画，bowl-water9-near.gif 为本轮水片近景；帧来源和文件指纹见 animation-manifest.json；layered-water8 的历史动画与原指纹另存 history/layered-water8。
本轮 GitHub 研究应用、固定源码入口与实现边界见 references/research/github-water-vortex-20261011/IMPLEMENTATION_NOTES.md。
新增 GPU 检查 {revision_count}/{len(revision['checks'])} 项，实际预览检查 {visual_count}/{len(visual['checks'])} 项，见 evidence/vortex-revision9/。
各类通过数按对应范围分别记录；严格像素重放失败没有改作容差通过。
Forge 文件仅为基础资源导出所需 GLSL wrapper，不是新效果的游戏实现。
完整基础 Forge 示例在另发的 world-volume-lab-source.zip 中；新增涡旋、液片、飞溅和水面尚未接入游戏。

验证脚本 scripts/verify_vortex.cjs 需要 Node、Playwright、Edge 与已启动的 8774 服务。
解压后的工作台源码下载链接可忽略；它们指向在线工作台提供的本包。
'''.encode('utf-8')
manifest = {name: {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()} for name, data in payload.items()}
payload['manifest.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
archive = OUT / 'vortex-waterspout-workbench-and-research-20261011.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for name, data in sorted(payload.items()):
        z.writestr('vortex-waterspout-workbench/' + name, data)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert all(z.read('vortex-waterspout-workbench/' + name) == data for name, data in payload.items())
shutil.copy2(archive, LAB / 'workbench/vortex/vortex-workbench-source.zip')
print(json.dumps({'ok': True, 'archive': str(archive), 'files': len(payload), 'bytes': archive.stat().st_size,
                  'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()}, ensure_ascii=False))
