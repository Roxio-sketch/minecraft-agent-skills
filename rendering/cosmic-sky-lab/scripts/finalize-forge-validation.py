"""Merge an explicitly recorded, camera-locked meteor recapture into 39 cases.

Preserves the replaced captures and JSON. The independent checker remains exact;
the final report states that two captures came from a separate isolated world.
"""
from pathlib import Path
import gzip
import hashlib
import importlib.util
import json
import shutil
import tempfile
import zipfile
from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[1]
FINAL = ROOT / 'forge/validation'
SUPPLEMENT = ROOT / 'forge/validation-meteor-supplement'
EVIDENCE = ROOT / 'evidence/forge'
NAMES = ('meteor-animation-time0', 'meteor-animation-time4')


def read(path):
    return json.loads(path.read_text('utf-8-sig'))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', 'utf-8')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    spec = importlib.util.spec_from_file_location('independent_forge_checker', ROOT / 'forge/check-validation.py')
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    original = read(FINAL / 'results.json')
    summary = read(FINAL / 'summary.json')
    supplemental_records = read(SUPPLEMENT / 'results.json')
    supplemental_summary = read(SUPPLEMENT / 'summary.json')
    assert [record['name'] for record in supplemental_records] == list(NAMES)
    assert supplemental_summary['casesCompleted'] == 2
    assert supplemental_summary['animationCasesPassed'] == 1
    left, right = supplemental_records
    for field in ('recipe', 'quality', 'mode', 'cameraPosition', 'cameraLook', 'projection', 'inverseViewRotation'):
        assert left[field] == right[field], f'Supplement changes {field}'
    replacements = {record['name']: record for record in supplemental_records}
    candidate = [replacements.get(record['name'], record) for record in original]
    # Check all 39 actual images and numeric records before replacing anything.
    with tempfile.TemporaryDirectory(prefix='cosmic-sky-validation-') as temporary:
        temporary = Path(temporary)
        for path in FINAL.glob('*.png'):
            shutil.copy2(path, temporary / path.name)
        for name in NAMES:
            for suffix in ('sky', 'world'):
                path = SUPPLEMENT / f'{name}-{suffix}.png'
                shutil.copy2(path, temporary / path.name)
        write(temporary / 'results.json', candidate)
        write(temporary / 'summary.json', summary)
        checked = checker.check(temporary)
        assert checked['ok'], checked['errors']
    archive = EVIDENCE / 'meteor-camera-input-original'
    if not archive.exists():
        archive.mkdir()
        for name in ('results.json', 'summary.json'):
            shutil.copy2(FINAL / name, archive / name)
        shutil.copy2(EVIDENCE / 'validation-star-support-check.json', archive / 'independent-check-before-recapture.json')
        for name in NAMES:
            for suffix in ('sky', 'world'):
                path = FINAL / f'{name}-{suffix}.png'
                shutil.copy2(path, archive / path.name)
        initial_by_name = {record['name']: record for record in original}
        initial_a, initial_b = (initial_by_name[name] for name in NAMES)
        deltas = {field: max(abs(a - b) for a, b in zip(initial_a[field], initial_b[field]))
                  for field in ('cameraLook', 'inverseViewRotation')}
        write(archive / 'reason.json', {
            'reason': 'Actual mouse-induced camera movement; exact animation isolation check correctly failed.',
            'maximumAbsoluteDeltas': deltas,
            'cameraLookDirectionDisplacementRadiansApprox': 0.002372709,
            'approximateScreenDisplacementPixelsAt960x540Fov70': 0.914916,
            'action': 'Recaptured only the complete meteor time0/time4 pair with mouse released and requested yaw/pitch held. No checker tolerance was relaxed.',
        })
    original_summary = read(archive / 'summary.json')
    provenance = {
        'fullRunWorld': original_summary['world'],
        'supplementRunWorld': supplemental_summary['world'],
        'originalFullRunCases': 39,
        'finalCapturesFromFullRun': 37,
        'replacedCases': list(NAMES),
        'supplementOutput': '../../forge/validation-meteor-supplement',
        'originalReplacedCaptureArchive': 'meteor-camera-input-original',
        'cameraComparison': 'Exact values retained for cameraLook, cameraPosition, projection and inverseViewRotation; no tolerance added.',
        'renderCoreUnchangedDuringSupplement': True,
        'renderCoreSha256': sha(ROOT / 'workbench/sky-core.glsl'),
        'fixtureChanges': ['Release mouse and hold requested yaw/pitch only during autorun.', 'Allow explicit case subset and isolated output path.'],
    }
    summary['captureProvenance'] = provenance
    write(FINAL / 'results.json', candidate)
    write(FINAL / 'summary.json', summary)
    for name in NAMES:
        for suffix in ('sky', 'world'):
            path = SUPPLEMENT / f'{name}-{suffix}.png'
            shutil.copy2(path, FINAL / path.name)
    write(EVIDENCE / 'capture-provenance.json', provenance)
    final_check = checker.check(FINAL)
    assert final_check['ok'], final_check['errors']
    write(EVIDENCE / 'validation-v2-check.json', final_check)
    write(EVIDENCE / 'validation-star-support-check.json', final_check)
    contrast = []
    for feature, end in checker.ANIMATIONS.items():
        first, second = f'{feature}-animation-time0', f'{feature}-animation-time{end}'
        with Image.open(FINAL / f'{first}-sky.png') as a, Image.open(FINAL / f'{second}-sky.png') as b:
            difference = ImageChops.difference(a.convert('RGB'), b.convert('RGB'))
            contrast.append({'first': first, 'second': second,
                             'meanAbsoluteRgbDelta255': ImageStat.Stat(difference).mean,
                             'maxAbsoluteChannelDelta255': [maximum for minimum, maximum in difference.getextrema()]})
    write(EVIDENCE / 'animation-v2-contrast.json', contrast)
    resources = ROOT / 'forge/src/main/resources/assets/cosmiclab'
    jar = ROOT / 'forge/build/libs/cosmic-sky-lab-0.3.0.jar'
    paths = {
        'jar': jar, 'sky-core.glsl': ROOT / 'workbench/sky-core.glsl',
        'sky.fsh': resources / 'shaders/core/sky.fsh',
        'sky.vsh': resources / 'shaders/core/sky.vsh',
        'sky.json': resources / 'shaders/core/sky.json',
        'default.json': resources / 'sky/default.json',
        'schema.json': ROOT / 'workbench/schema.json',
        'SkyRecipe.java': ROOT / 'forge/src/main/java/dev/cosmiclab/SkyRecipe.java',
        'LabSky.java': ROOT / 'forge/src/main/java/dev/cosmiclab/LabSky.java',
        'LabValidation.java': ROOT / 'forge/src/main/java/dev/cosmiclab/LabValidation.java',
        'check-validation.py': ROOT / 'forge/check-validation.py',
        'results.json': FINAL / 'results.json', 'summary.json': FINAL / 'summary.json',
    }
    with zipfile.ZipFile(jar) as packed:
        parity = {name: packed.read(str(path.relative_to(ROOT / 'forge/src/main/resources')).replace('\\', '/')) == path.read_bytes()
                  for name, path in paths.items() if name in ('sky.fsh', 'sky.vsh', 'sky.json', 'default.json')}
    assert all(parity.values())
    manifest = {'sha256': {name: sha(path) for name, path in paths.items()},
                'packagedResourceParity': parity, 'pngCount': len(list(FINAL.glob('*.png'))),
                'schema': 'cosmic-sky/2', 'parameters': 58, 'cases': 39,
                'captureProvenance': provenance}
    write(EVIDENCE / 'v2-manifest.json', manifest)
    # Minecraft rotates latest.log on startup; recover the completed 39-case run.
    full_log = ROOT / 'forge/run/logs/2026-10-06-1.log.gz'
    full_text = gzip.decompress(full_log.read_bytes()).decode('utf-8', errors='replace')
    assert original_summary['world'] in full_text
    (EVIDENCE / 'latest-v2.log').write_text(full_text, 'utf-8')
    shutil.copy2(ROOT / 'forge/run/logs/latest.log', EVIDENCE / 'meteor-supplement.log')
    print(json.dumps({'ok': True, 'cases': 39, 'pngCount': manifest['pngCount'],
                      'coreSha256': manifest['sha256']['sky-core.glsl'],
                      'jarSha256': manifest['sha256']['jar'],
                      'meteorChangedPixels': right['differentPixelsFromAnimationBase']}, indent=2))


if __name__ == '__main__':
    main()
