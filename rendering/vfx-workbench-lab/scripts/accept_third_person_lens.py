"""Accept isolated same-frame Forge actor captures, without reusing the actor mask."""
from pathlib import Path
import json
import shutil
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
source = root / 'run/black-hole-lab/world-probes'
out = root / 'evidence/third-person-lens-20261005'
after = out / 'after'
after.mkdir(parents=True, exist_ok=True)
local_names = ['front-off', 'front-on', 'local-entirely-behind', 'plane-through-chest',
               'plane-through-feet', 'core-through-torso', 'camera-inside-core',
               'close-yaw45-pitch20', 'yaw-minus70-pitch-minus25', 'crouching-at-plane',
               'swimming-at-plane', 'held-item-swing-at-plane']
names = local_names + ['other-entity-front', 'other-entity-behind']
cases = {}
for name in names:
    file = source / ('third-person-' + name + '.json')
    case = json.loads(file.read_text(encoding='utf-8-sig'))
    assert case['lensModel'] == 'angular-environment-360/v4', name
    assert case['environmentCapturedBeforeEntities'], name
    assert case['glError'] == case['outsideChangedPixels'] == 0, name
    assert case['maxDepthDifference'] == 0, name
    assert case['actualEntityDepthPixels'] > 1000, name
    assert case['foregroundEntityChangedPixels'] == 0, name
    if name != 'other-entity-behind':
        assert case['actualEntityDepthChangedPixels'] == 0, name
        assert case['actualEntityDepthLensChangedPixels'] == 0, name
        assert case['movedLensedPlayerPixels'] == 0, name
    if name not in ('front-off', 'camera-inside-core'):
        assert case['lensChangedPixels'] > 1000, name
    cases[name] = case
    for artifact in source.glob('third-person-' + name + '*'):
        shutil.copy2(artifact, after / artifact.name)

assert cases['front-off']['lensChangedPixels'] == 0
assert cases['other-entity-behind']['actualEntityDepthChangedPixels'] > 1000
assert cases['other-entity-behind']['movedLensedPlayerPixels'] > 1000
baseline = {}
for name in names:
    path = out / 'baseline-isolated' / ('third-person-' + name + '.json')
    baseline[name] = json.loads(path.read_text(encoding='utf-8-sig'))
for name in ['local-entirely-behind', 'close-yaw45-pitch20', 'swimming-at-plane']:
    assert baseline[name]['actualEntityDepthChangedPixels'] > 1000, name
    assert baseline[name]['movedLensedPlayerPixels'] > 1000, name

font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 23)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 18)
left = Image.open(out / 'baseline-isolated/third-person-close-yaw45-pitch20-field.png').convert('RGB')
right = Image.open(after / 'third-person-close-yaw45-pitch20-field.png').convert('RGB')
w, h = left.size
sheet = Image.new('RGB', (2*w+36, h+92), '#111722')
draw = ImageDraw.Draw(sheet)
draw.text((12, 8), '修复前：自身模型被拆分、复制', font=font, fill='white')
draw.text((w+24, 8), '修复后：玩家完整，环境仍有透镜', font=font, fill='white')
sheet.paste(left, (12, 42))
sheet.paste(right, (w+24, 42))
draw.text((12, h+53), '真实 Forge 客户端；相同相机/配方/姿态，关闭云层、阴影和粒子以隔离模型覆盖。', font=small, fill='#bac6d8')
sheet.save(out / 'third-person-comparison.png')

report = dict(status='passed', cases=len(cases), minecraftRuntimeVerified=True,
              lensModel='angular-environment-360/v4',
              protectedLocalActorCases=len(local_names), actorPixelsChanged=0,
              localActorMovedSourcePixels=0,
              behindOtherActorMovedSourcePixels=cases['other-entity-behind']['movedLensedPlayerPixels'],
              independentActorMetric='All post-terrain depth changes in an isolated scene with clouds, actor shadows and particles disabled; no production bounding-box filter.',
              validationScope='Forge 1.20.1 development client; user modpack video was inspected, not replaced by a simulated preview.',
              measurements=cases, baseline=baseline)
(out / 'runtime.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({k:report[k] for k in ['status','cases','actorPixelsChanged','localActorMovedSourcePixels','behindOtherActorMovedSourcePixels']}))
