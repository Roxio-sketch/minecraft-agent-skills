"""Accept same-frame Forge captures; math-only tests are separate evidence."""
from pathlib import Path
from PIL import Image, ImageChops
import hashlib, json, math, shutil
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
source = root / 'run/black-hole-lab/world-probes'
out = root / 'evidence/lens-reimplementation-20261005'
out.mkdir(parents=True, exist_ok=True)
names = ['core-player-behind', 'player-front-off', 'player-front-on',
         'world-yaw-zero', 'world-yaw-25', 'world-pitch-20', 'foreground-wall',
         'near-fullscreen', 'near-edge', 'near-offscreen', 'player-behind-offaxis',
         'lens-outside-disk', 'default-recipe-front', 'horizon-first-person',
         'horizon-third-person']
cases = {}
for name in names:
    path = source / ('regression-' + name + '.json')
    case = json.loads(path.read_text(encoding='utf-8-sig'))
    assert case['lensModel'] == 'angular-environment-360/v3', name
    assert case['environmentCapturedBeforeEntities'], name
    assert case['glError'] == case['outsideChangedPixels'] == 0, name
    assert case['maxDepthDifference'] == 0, name
    assert case['foregroundEntityChangedPixels'] == 0, name
    assert case['playerFrontLensChangedPixels'] == 0, name
    if case['playerFrontPixels'] > 0:
        assert case['movedLensedPlayerPixels'] == 0, name
    cases[name] = case
    for file in source.glob('regression-' + name + '*'):
        shutil.copy2(file, out / file.name)

for name in ['player-front-on', 'default-recipe-front', 'horizon-third-person']:
    assert cases[name]['playerFrontPixels'] > 1000, name
    assert cases[name]['lensChangedPixels'] > 1000, name
assert cases['player-front-off']['lensChangedPixels'] == 0
behind = cases['player-behind-offaxis']
assert behind['playerFrontPixels'] == 0 and behind['playerPixels'] > 1000
assert behind['movedLensedPlayerPixels'] > 1000

# The first-person test is centered and has no entity on the critical circle.
# Read the real GLSL availability output at 36 azimuth sectors in a broad ring.
mask = Image.open(source / 'regression-horizon-first-person-source-kind.png').convert('RGB')
w, h = mask.size
sectors = [0] * 36
for y in range(h):
    for x in range(w):
        dx, dy = x + .5 - w/2, y + .5 - h/2
        radius = math.hypot(dx, dy)
        if .17*h <= radius <= .39*h and mask.getpixel((x,y))[0] > 200:
            sector = int((math.atan2(dy,dx) + math.pi) * 36 / (2*math.pi)) % 36
            sectors[sector] += 1
assert min(sectors) > 10, sectors

before = Image.open(source / 'regression-horizon-first-person-no-lens.png').convert('RGB')
after = Image.open(source / 'regression-horizon-first-person-field.png').convert('RGB')
diff = ImageChops.difference(before, after)
lower_changed = sum(max(diff.getpixel((x,y))) > 1 for y in range(h//2,h) for x in range(w))
assert lower_changed > 1000, lower_changed

shader = root / 'src/main/resources/assets/vfxworkbench/shaders/core/bh_world.fsh'
jar = root / 'build/libs/vfx-workbench-forge-1.20.1-0.1.0.jar'
with ZipFile(jar) as z:
    assert z.read('assets/vfxworkbench/shaders/core/bh_world.fsh') == shader.read_bytes()
    assert 'dev/vfxworkbench/effects/lightning/RainGeometry.class' in z.namelist()

report = dict(
    status='passed', lensModel='angular-environment-360/v3',
    minecraftRuntimeVerified=True, minecraft=behind['minecraft'], forge=behind['forge'],
    gpu=behind['gpu'], cases=len(cases),
    foregroundMasksNonempty=True, foregroundEntityChangedPixels=0,
    foregroundPlayerChangedPixels=0,
    behindPlayerMovedPixels=behind['movedLensedPlayerPixels'],
    fullCircleAvailabilitySectors=36, minimumAvailablePixelsPerSector=min(sectors),
    horizonLowerHalfLensChangedPixels=lower_changed,
    shaderSha256=hashlib.sha256(shader.read_bytes()).hexdigest(),
    builtJarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
    packagedShaderMatchesRuntimeSource=True,
    sameFrameNoLensBaseline=True, depthChanged=False,
    validationScope='Local Forge development client and isolated save, not the installed modpack',
    limits=['Artistic angular environment mapping, not a relativistic geodesic simulator.',
            'Offscreen source colors blend back to the original frame.',
            'Foreground bounds contain up to 64 nearest entities, with the player first.',
            'The hidden environment fallback captures opaque/cutout terrain before entities.'],
    measurements=cases)
(out / 'runtime.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({k: report[k] for k in ['status','cases','behindPlayerMovedPixels',
      'minimumAvailablePixelsPerSector','horizonLowerHalfLensChangedPixels','builtJarSha256']}, ensure_ascii=False))
