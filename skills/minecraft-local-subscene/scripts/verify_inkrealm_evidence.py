"""Audit recorded Ink Realm 0.2.0 captures. Requires Pillow + numpy; never controls Minecraft.

This is a scene-specific regression checker, not a universal renderer certification.
Depth/mask/state claims are Java probe records; PNG changes and red-sail parallax are
independently checked here. A new scene needs its own landmark and tolerance model.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image

REGULAR = ('foreground', 'front', 'left', 'right', 'time4', 'low', 'high', 'reloaded', 'recovered')
CROSSING = ('window', 'near', 'inside', 'inside-turn', 'inside-move', 'inside-reload', 'returned')
PIXEL_PAIRS = {'foreground', 'front', 'near', 'inside', 'returned'}


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def verify(evidence_root, jar=None):
    root = Path(evidence_root)
    checks, records, scenes = [], {}, {}

    def check(name, passed, **details):
        checks.append(dict(name=name, passed=bool(passed), **details))

    try:
        deployment = read_json(root / 'deployment.json')
        check('artifact metadata', deployment['verified'] is True and deployment['version'] == '0.2.0'
              and len(deployment['sha256']) == 64)
        if jar is not None:
            digest = hashlib.sha256(Path(jar).read_bytes()).hexdigest()
            check('original tested jar', digest == deployment['sha256'].lower(), sha256=digest)
        sessions = {}
        for folder, expected in [('runtime', REGULAR), ('crossing', CROSSING)]:
            base = root / folder
            session = read_json(base / 'session.json')
            sessions[folder] = session
            prefix = session['prefix'] + '-'
            wanted = [prefix + name for name in expected]
            check(folder + ' case inventory', session['cases'] == wanted)
            for short in expected:
                name = prefix + short
                r = read_json(base / (name + '.json'))
                records[short] = r
                check(short + ' identity', r['case'] == name and r['minecraft'] == '1.20.1'
                      and r['forge'] == '47.4.10')
                check(short + ' recorded GL state', r['glError'] == 0 and r['glErrorAfterRestore'] == 0
                      and r['fboStatus'] == 36053 and r['stateMismatches'] == [])
                check(short + ' recorded geometry', r['sceneGeometryPixels'] > 100
                      and r['sceneDepthBins'] > 10 and r['changedPixels'] > 0)
                expected_mode = 'interior' if short.startswith('inside') else 'window'
                check(short + ' view mode', r['viewMode'] == expected_mode)
                if expected_mode == 'window':
                    check(short + ' recorded aperture/depth', r['outsideChangedPixels'] == 0
                          and r['foregroundChangedPixels'] == 0 and r['maxEntranceDepthError'] <= 1e-5)
                else:
                    check(short + ' recorded fullscreen', r['changedPixels'] == r['width'] * r['height'])
                with Image.open(base / (name + '-scene.png')) as im:
                    scene = np.asarray(im.convert('RGB')).copy()
                scenes[short] = scene
                check(short + ' scene image', scene.shape[:2] == (r['sceneHeight'], r['sceneWidth'])
                      and float(scene.std()) > 2)
                if short in PIXEL_PAIRS:
                    with Image.open(base / (name + '-before.png')) as im:
                        before = np.asarray(im.convert('RGBA')).copy()
                    with Image.open(base / (name + '-world.png')) as im:
                        after = np.asarray(im.convert('RGBA')).copy()
                    shape_ok = before.shape == after.shape == (r['height'], r['width'], 4)
                    changed = int(np.count_nonzero(np.any(before != after, axis=2))) if shape_ok else -1
                    check(short + ' independent pixel change', shape_ok and changed == r['changedPixels'],
                          measured=changed, recorded=r['changedPixels'])

        def difference(a, b):
            x, y = scenes[a], scenes[b]
            if x.shape != y.shape:
                raise ValueError(f'Incompatible images for comparison: {a}, {b}')
            return int(np.count_nonzero(np.any(x != y, axis=2)))

        centroids = {}
        for name in ('left', 'right'):
            rgb = scenes[name].astype(np.int32)
            mask = (rgb[:, :, 0] > 3 * rgb[:, :, 1]) & (rgb[:, :, 0] > 3 * rgb[:, :, 2]) & (rgb[:, :, 0] > 50)
            _, xs = np.where(mask)
            if xs.size < 30:
                raise ValueError('Missing red-sail landmark: ' + name)
            centroids[name] = float((xs + .5).mean() / rgb.shape[1])
        left, right = records['left'], records['right']
        # Ink Realm-specific geometry: aperture width 10, sail at approx local z=-17.
        z = left['eye'][2]
        expected_span = (right['eye'][0] - left['eye'][0]) * 17 / ((z + 17) * 10)
        measured_span = centroids['right'] - centroids['left']
        check('parallax fixture', left['time'] == right['time'] == 0 and z > 0
              and np.allclose(left['eye'][1:], right['eye'][1:]))
        check('independent red-sail parallax', expected_span > 0 and abs(measured_span - expected_span) < .01,
              measured_uv_span=measured_span, expected_uv_span=expected_span)
        check('time fixture', records['front']['time'] == 0 and records['time4']['time'] == 4
              and np.allclose(records['front']['eye'], records['time4']['eye']))
        for a, b in [('front', 'time4'), ('inside', 'inside-turn'), ('inside-turn', 'inside-move')]:
            changed = difference(a, b)
            check(a + ' to ' + b + ' image response', changed > 100, changed_pixels=changed)
        check('two resolution targets', records['low']['sceneWidth'] == 320 and records['high']['sceneWidth'] == 768)
        check('window reload generation', records['reloaded']['shaderGeneration'] > records['high']['shaderGeneration'])
        check('window reload image stability', difference('high', 'reloaded') == 0)
        check('interior reload state', records['inside-reload']['shaderGeneration'] > records['inside-move']['shaderGeneration']
              and records['inside-reload']['viewMode'] == 'interior')
        entry = records['inside']['entries'] - records['window']['entries']
        exit_count = records['returned']['exits'] - records['window']['exits']
        check('crossing entry and return', entry == 1 and exit_count == 1
              and records['returned']['viewMode'] == 'window')
        fault = sessions['runtime']['fault']
        check('injected failure restores recorded state', fault['failed'] is True and fault['stateErrors'] == 0
              and 'Intentional one-shot' in fault['error'])
        check('recovered output stable', difference('reloaded', 'recovered') == 0)
        end = sessions['crossing']['finalTestState']
        check('explicit off resets visual domain', end['enabled'] is False and end['inside'] is False
              and end['failed'] is False and end['stateErrors'] == 0)
        check('foreground occlusion exercised', records['foreground']['foregroundPixels'] > 1000)
    except (OSError, ValueError, KeyError, TypeError, IndexError) as exc:
        check('readable complete evidence', False, error=str(exc))
    return dict(passed=all(c['passed'] for c in checks), checks=checks, cases=len(records),
                scope='Offline audit of recorded Ink Realm 0.2.0 runtime evidence; does not launch or retest Minecraft',
                limitations=['Only selected before/after PNG pairs are independently compared.',
                             'Depth, outside-mask and state assertions rely on the Java probe records.',
                             'Historical captures omit complete orientation/FOV/projection metadata.',
                             'Interior reload pixel identity is not asserted; camera may have changed.',
                             'Visual entry only: no second ClientLevel, collision or remote gameplay certification.'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence_root', nargs='?', type=Path,
                        default=Path(__file__).resolve().parents[1] / 'references/evidence/inkrealm')
    parser.add_argument('--jar', type=Path, help='Optional original tested JAR to compare with deployment hash')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = verify(args.evidence_root, args.jar)
    output = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output + '\n', encoding='utf-8')
    print(output)
    raise SystemExit(0 if result['passed'] else 1)
