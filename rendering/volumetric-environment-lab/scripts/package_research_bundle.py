"""Bundle selected research, installed-skill archive and current event workbench."""
import hashlib
import json
import posixpath
import re
import zipfile
from pathlib import Path

LAB = Path(__file__).resolve().parents[1]
WORKSPACE = LAB.parent
SKILL = WORKSPACE / 'skill/minecraft-volumetric-environment'
OUT = WORKSPACE / 'deliverables/world-volume'
report = json.loads((LAB / 'evidence/vortex/report.json').read_text(encoding='utf-8'))
portable = json.loads((LAB / 'evidence/vortex/portable.json').read_text(encoding='utf-8'))
revision = json.loads((LAB / 'evidence/vortex-revision9/verify/report.json').read_text(encoding='utf-8'))
visual = json.loads((LAB / 'evidence/vortex-revision9/review/report.json').read_text(encoding='utf-8'))
# Source binding is required; render failures remain visible in the original reports.
assert portable['coreSha256'] == report['coreSha256'], 'Portable proof belongs to another renderer'
assert portable['previewSha256'] == report['previewSha256'], 'Portable proof belongs to another liquid implementation'
assert revision['previewSha256'] == report['previewSha256'] and visual['previewSha256'] == report['previewSha256'], 'Revision proof belongs to another implementation'
for key, name in (('coreSha256', 'volume-core.glsl'), ('previewSha256', 'preview.js'), ('noiseGeneratorSha256', 'vortex-noise.js')):
    assert report[key] == hashlib.sha256((LAB / 'workbench/vortex' / name).read_bytes()).hexdigest(), 'Stale source binding: ' + name
passed_count = sum(check.get('passed') is True for check in report['checks'])
portable_passed = sum(check.get('passed') is True for check in portable['checks'])
payload = {}
for name in ('minecraft-volumetric-environment-skill.zip', 'vortex-waterspout-workbench-and-research-20261011.zip', 'github-water-vortex-source-study-20261011.zip'):
    payload[name] = (OUT / name).read_bytes()
for file in sorted((SKILL / 'references').rglob('*')):
    if file.is_file() and '__pycache__' not in file.parts:
        payload['research-and-validation/' + file.relative_to(SKILL / 'references').as_posix()] = file.read_bytes()
# Carry the small locally referenced integration documents so research links remain usable.
for name, data in list(payload.items()):
    if not name.endswith('.md'):
        continue
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', data.decode('utf-8')):
        target = target.split('#')[0]
        if not target or target.startswith(('http:', 'https:', 'app:', 'codex:')):
            continue
        bundled = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
        if bundled in payload:
            continue
        if bundled.startswith('assets/'):
            source = SKILL / bundled
            assert source.is_file(), source
            payload[bundled] = source.read_bytes()
        else:
            raise ValueError('Missing research link: ' + bundled)
payload['说明.md'] = f'''# 世界体积环境资料与 Skill（2026-10-11，水龙卷顶部碗口修订）

已包含 Bliss 末地密度、AstraLex 紫雾、iterationT 晴雨云研究，以及原创渡劫黑云、陆地龙卷风、龙吸水和水龙卷工作台。
本轮将规则水带重做为连续水团、不规则连接水片和有限生命周期甩水，具体变化见 research-and-validation/research/vortex-waterspout-20261011/WATER_BODY_REVISION.md。
水柱轮廓、旋转上行水纹与泡沫采用同一局部螺旋相位，海面向心流痕仍使用有界双相位输运。
当前修订保留下段连续水团，用三层重叠薄皮和局部翻卷片表现外层水；水龙卷上段张开碗口，内部空腔与薄壳分别检查。
反射和透射共用细法线、Fresnel 与局部泡沫，去掉重复青色基底，碎滴加入简化背景透射。
新增 GitHub 源码研究的应用说明见 research-and-validation/research/github-water-vortex-20261011/IMPLEMENTATION_NOTES.md。
`github-water-vortex-source-study-20261011.zip` 单独保留 8 个仓库的 99 份精选原始文件、固定提交、许可证与离线索引。
这些第三方源码属于各仓库许可范围，没有混入原创渲染内核。

`minecraft-volumetric-environment-skill.zip` 是完整 Skill；`vortex-waterspout-workbench-and-research-20261011.zip` 可独立解压。
启动 `python workbench/serve.py --port 8774`，访问 http://127.0.0.1:8774/vortex/。
新效果包含云体密度、形成时间线、漏斗/云帽/喷雾、多层不规则液片、三维飞溅水滴与程序海面，支持完整 JSON 和实际 PNG 导出。

当前涡旋页 WebGL/UI 检查通过 {passed_count}/{len(report['checks'])} 项；安装版 Skill 独立生成的便携作品复核通过 {portable_passed}/{len(portable['checks'])} 项。
专项载体 GPU 检查通过 {sum(c['passed'] for c in revision['checks'])}/{len(revision['checks'])} 项；实际预览检查通过 {sum(c['passed'] for c in visual['checks'])}/{len(visual['checks'])} 项。
固定帧 PNG 完全一致的检查结果和扩展 RGBA 差异记录均保留在报告中；不将资料打包成功视为渲染检查全部通过。
完整 Skill 内保留基础 Forge 1.20.1 后端；新增渡劫、涡旋和水柱的游戏接入尚待适配。
第三方光影包源码、纹理、PDF 阅读缓存和商业资产没有再分发。
'''.encode('utf-8')
manifest = {name: {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()} for name, data in payload.items()}
payload['manifest.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
archive = OUT / 'world-volume-research-and-skill-20261011.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for name, data in sorted(payload.items()):
        z.writestr(name, data)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert all(z.read(name) == data for name, data in payload.items())
# The combined archive is rebuilt after package_delivery.py. Refresh the
# inventory here so it cannot retain the previous combined ZIP fingerprint.
inventory_path = OUT / 'delivery.json'
inventory = json.loads(inventory_path.read_text(encoding='utf-8'))
inventory['archives'] = {
    file.name: {'bytes': file.stat().st_size, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()}
    for file in sorted(OUT.iterdir()) if file.is_file() and file.suffix in ('.zip', '.jar')
}
inventory['currentEventPreviewSha256'] = report['previewSha256']
integrity = json.loads((LAB / 'evidence/delivery/report.json').read_text(encoding='utf-8'))
inventory['artifactIntegrityChecks'] = {
    'passed': sum(c['passed'] for c in integrity['checks']), 'total': len(integrity['checks'])
}
inventory['renderChecks'] = {
    label: {'passed': sum(c['passed'] for c in result['checks']),
            'total': len(result['checks']),
            'failed': [c['name'] for c in result['checks'] if not c['passed']],
            'report': relative}
    for label, result, relative in (
        ('gpu', revision, 'evidence/vortex-revision9/verify/report.json'),
        ('workbench', report, 'evidence/vortex/report.json'),
        ('actualFrames', visual, 'evidence/vortex-revision9/review/report.json'),
        ('portable', portable, 'evidence/portable-vortex-revision9/report.json'))
}
inventory_path.write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'ok': True, 'archive': str(archive), 'files': len(payload), 'bytes': archive.stat().st_size,
                  'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()}, ensure_ascii=False))
