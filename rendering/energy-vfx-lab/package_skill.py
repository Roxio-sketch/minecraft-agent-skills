"""Package the verified v0.2.5 editor/runtime. Run only after final build and QA."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent
SKILL = ROOT.parent / 'skill/minecraft-energy-vfx'
ASSET = SKILL / 'assets/lab'
VERSION = '0.2.5'
PRESET_COUNT = 20
JAR_NAME = f'energyvfx-{VERSION}.jar'
SHADERS = ['noise.glsl', 'field.glsl', 'scene.glsl', 'volume.glsl', 'blur.glsl', 'composite.glsl']
EXTRA_SHADERS = ['flow-cache.glsl']
LAB_FILES = ['index.html', 'schema.js', 'shaders.js', 'editor.js', 'aura.js', 'recipe-schema.json',
             *SHADERS, *EXTRA_SHADERS, 'serve.py', 'README.md', '幽气拓展源码.md']

# Reject incomplete inputs before copying or installing anything.
fields = json.loads((ROOT / 'recipe-schema.json').read_text(encoding='utf-8'))
assert len(fields) == 48, 'energy-vfx/2 requires exactly 48 ordered fields'
expected_params = {field[0] for field in fields}
presets = ROOT / 'presets'
assert {path.name for path in presets.glob('*.json')} == {f'{index}.json' for index in range(1, PRESET_COUNT + 1)}, 'Expected presets 1..20'
for index in range(1, PRESET_COUNT + 1):
    recipe = json.loads((presets / f'{index}.json').read_text(encoding='utf-8'))
    assert recipe['schema'] == 'energy-vfx/2' and set(recipe['params']) == expected_params, f'Preset {index} is not a complete v2 recipe'
for name in LAB_FILES:
    assert (ROOT / name).is_file(), f'Missing editor source: {name}'
jar = ROOT / 'forge/build/libs' / JAR_NAME
assert jar.is_file(), f'Build the production {JAR_NAME} first'
manifest = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in SHADERS + EXTRA_SHADERS}
for name, digest in manifest.items():
    assert digest == hashlib.sha256((ROOT / 'forge/src/main/resources/assets/energyvfx/shaders' / name).read_bytes()).hexdigest(), f'Forge shader differs: {name}'
jar_manifest = {}
with zipfile.ZipFile(jar) as archive:
    for name, digest in manifest.items():
        resource = f'assets/energyvfx/shaders/{name}'
        try:
            packaged = archive.read(resource)
        except KeyError as error:
            raise RuntimeError(f'Production JAR is missing shader: {resource}') from error
        jar_digest = hashlib.sha256(packaged).hexdigest()
        if jar_digest != digest:
            raise RuntimeError(f'Production JAR contains an old or different shader: {name}; rebuild {JAR_NAME}')
        jar_manifest[name] = jar_digest

ASSET.mkdir(parents=True, exist_ok=True)
for name in LAB_FILES:
    shutil.copy2(ROOT / name, ASSET / name)
shutil.copytree(presets, ASSET / 'presets', dirs_exist_ok=True)
forge = ASSET / 'forge'
forge.mkdir(exist_ok=True)
for name in ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat']:
    shutil.copy2(ROOT / 'forge' / name, forge / name)
shutil.copytree(ROOT / 'forge/gradle', forge / 'gradle', dirs_exist_ok=True)
shutil.copytree(ROOT / 'forge/src', forge / 'src', dirs_exist_ok=True)
runtime = SKILL / 'assets/runtime'
runtime.mkdir(parents=True, exist_ok=True)
shutil.copy2(jar, runtime / JAR_NAME)
for old_version in ['0.1.0', '0.2.0', '0.2.1', '0.2.2', '0.2.3', '0.2.4']:
    old_runtime = runtime / f'energyvfx-{old_version}.jar'
    if old_runtime.is_file():
        old_runtime.unlink()

references = SKILL / 'references'
references.mkdir(exist_ok=True)
shutil.copy2(ROOT / '幽气拓展源码.md', references / 'ghost-sources.md')
shutil.copy2(ROOT / '性能优化记录.md', references / 'performance.md')
shutil.copy2(ROOT / '全屏优化记录.md', references / 'fullscreen-performance.md')
shutil.copy2(ROOT / '移动优化记录.md', references / 'motion-performance.md')
shutil.copy2(ROOT / 'third-person-zoom-performance.md', references / 'third-person-zoom-performance.md')
lines = [
    '# energy-vfx/2 配方', '',
    '运行版本 v0.2.5；配方对象只允许 schema、name、color、secondary、params。两个颜色为六位 HEX，按 sRGB 转线性后给体积发光。name 为 1–80 个 UTF-16 字符。', '',
    'v2 必须精确含全部 48 个参数。v1 必须精确含原来的前 39 个参数，校验通过后补上后 9 个默认值并升级为 v2；缺失、未知字段、布尔数值、非有限值、范围与整数错误均拒绝。不要把残缺 v2 当成 v1。', '',
    '|索引|字段|含义|范围|默认|实际消费者|', '|---:|---|---|---|---:|---|'
]
for index, (key, _group, label, low, high, _step, default) in enumerate(fields):
    consumer = '空间体积场 / 生命周期（持续发散模式不使用所有空间造型字段）'
    if key in ['emission', 'bloom', 'bloomRadius', 'threshold', 'exposure', 'distortion']:
        consumer = 'emission / Blur / 全分辨率合成'
    elif key == 'steps':
        consumer = '体积积分循环'
    elif key == 'resolution':
        consumer = 'WebGL / Forge FBO 尺寸'
    elif key == 'attachment':
        consumer = '空间/身体/装备来源选择与 auraField 分支'
    elif key in ['outflow', 'puffRate', 'puffLife', 'spread', 'bodyWeight', 'gearWeight', 'inertia']:
        consumer = 'aura.js 预览 / AuraEmitter 世界空间气团模拟与表面采样'
    elif key == 'firstPerson':
        consumer = 'AuraEmitter.upload 第一人称自身强度'
    elif key in ['radius', 'stretch', 'shell', 'shellWidth', 'core', 'scale', 'detail', 'speed', 'swirl', 'warp', 'rise', 'density', 'erosion', 'softness', 'filaments', 'sharpness', 'edge', 'absorption']:
        consumer = '空间场或 auraField；部分字段同时驱动气团运动/包络'
    lines.append(f'|{index}|`{key}`|{label}|{low}–{high}|{default}|{consumer}|')
lines += [
    '', 'shape：0 球、1 环、2 涡柱、3 束；mode：0 持续、1 循环蓄力→爆发→消散、2 单次消散。attachment：0 空间体积、1 身体、2 装备、3 两者。预览相机、场景和播放位置不属于游戏配方。', '',
    '身体/装备模式以 32 个三维密度包为全局容量；约 puffRate × puffLife 个包同时存活，超额会截断。每包以固定 20 Hz、RK2 在世界坐标独立漂移；内部沿不同轴和相位的局部 curl 场，以 RK2 回溯坐标形成折叠。空间细丝沿用等值带交集；均没有独立碰撞实体或历史几何轨迹。', '',
    '烟纱保留局部近似 signed distance，段内按距离线性变化，以误差函数解析积分 Gaussian 薄层；端点振幅和非线性距离仍为数值近似。烟纱路径没有像素 jitter 或 3×3 屏幕模糊。shell 混合烟雾/烟纱，shellWidth 与 sharpness 调整局部宽度，经拉伸与回溯后不保证世界厚度严格恒定；该运动不是完整流体求解器。', '',
    '白蓝烟纱起点 15/16 默认 resolution=1、steps=128；持续发散把 steps 换算为 round(clamp(steps × 0.5, 24, 64)) 个积分段，因此 steps=128 实际为每气团 64 段、65 个端点。空间模式仍按原光线步数积分。', '',
    '游戏从实际 ModelPart 动作与世界变换采样身体；普通 ArmorItem 采样动作一致的盔甲模型，普通持有物品采样 baked quad 并保留手臂与第三人称显示变换。网页人物为观察用代理模型，不宣称与任意游戏装备几何完全相同。', '',
    '非标准盔甲与 BEWLR/GeckoLib 动态装备会使用挂点 fallback 并计数，不能宣称已经精确覆盖其自定义几何；这些目标需专用表面提供器。第一人称默认低自身强度，并对本地自身做固定出生选择与近眼淡出。v0.2.5 将此策略扩展到贴墙等近距第三人称：相机距眼睛至少 3 米保留完整烟量，低于 1.8 米使用近距预算，中间平滑过渡；第三人称拉近再使用 3.25 米到 2 米的低贡献烟包连续裁剪。会改变近距可见烟量，保留全部气团模拟。分支计数不证明手部烟纱外观；具体取舍见 fullscreen-performance.md、motion-performance.md 与 third-person-zoom-performance.md。', '',
    '时间以秒计，radius/outflow/spread 按 Minecraft 米（方块单位）理解；bloomRadius 是体积目标的像素核尺度。更多参考见 ghost-sources.md，实际通过范围见 validation.md。'
]
(references / 'recipe.md').write_text('\n'.join(lines), encoding='utf-8')

for destination in [SKILL / 'LICENSE', ASSET / 'LICENSE', forge / 'LICENSE']:
    shutil.copy2(ROOT / 'LICENSE', destination)
(ROOT / 'evidence').mkdir(exist_ok=True)
(ROOT / 'evidence/shared-kernel-sha256.json').write_text(json.dumps({name:manifest[name] for name in SHADERS}, indent=2), encoding='utf-8')
package_manifest = {'version': VERSION, 'schema': 'energy-vfx/2', 'fields': len(fields),
                    'presets': PRESET_COUNT, 'runtime': JAR_NAME,
                    'runtimeSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                    'sharedShaders': {name:manifest[name] for name in SHADERS}, 'nativeShaders':{name:manifest[name] for name in EXTRA_SHADERS}, 'runtimeShaders': jar_manifest}
(SKILL / 'references/package-manifest.json').write_text(json.dumps(package_manifest, indent=2), encoding='utf-8')

installed = Path('C:/Users/MAINTAINER/.codex/skills/minecraft-energy-vfx')
shutil.copytree(SKILL, installed, dirs_exist_ok=True)
for old_version in ['0.1.0', '0.2.0', '0.2.1', '0.2.2', '0.2.3', '0.2.4']:
    installed_old = installed / f'assets/runtime/energyvfx-{old_version}.jar'
    if installed_old.is_file():
        installed_old.unlink()
out = ROOT.parent / 'deliverables'
out.mkdir(exist_ok=True)
excluded_parts = {'__pycache__', '.gradle', 'build', 'run', 'validation', 'evidence', 'ghost-references'}
for zipname, base in [('minecraft-energy-vfx-skill.zip', SKILL), ('energy-vfx-editor.zip', ASSET)]:
    with zipfile.ZipFile(out / zipname, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(base.rglob('*')):
            relative = path.relative_to(base)
            if path.is_file() and not any(part in excluded_parts for part in relative.parts):
                archive.write(path, path.relative_to(base.parent))
shutil.copy2(jar, out / JAR_NAME)
print(f'Skill v{VERSION} installed: {installed}\nArtifacts: {out}\n48 fields / 20 editable presets; six shared kernels and native cache shader SHA-256 identical.')
