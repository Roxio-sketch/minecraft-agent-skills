#!/usr/bin/env python3
"""Check this lab's 39 cases, recorded math, strict recipes and decoded PNGs.

Requires Pillow for independent PNG pixel comparisons. Does not judge visual
quality or establish compatibility with third-party shader packs.
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image

DIRECTIONS = (
    'direction-base', 'direction-translate', 'direction-yaw90',
    'direction-pitch-up', 'direction-fov40', 'direction-fov100',
    'direction-resize', 'direction-reload',
)
EFFECTS = tuple(f'{mode}-{quality}' for mode in ('stars', 'nebula')
                for quality in ('low', 'medium', 'high'))
ORIGINAL_NAMES = DIRECTIONS + EFFECTS + ('terrain-occlusion',)
HORIZON = ('stars-horizon-low', 'stars-horizon-medium', 'stars-horizon-high')
ANIMATIONS = {'nebula': 4, 'aurora': 12, 'meteor': 4, 'twinkle': 2, 'celestial': 8, 'blackhole': 2}
NAMES = ORIGINAL_NAMES + tuple(name for feature, end in ANIMATIONS.items()
                              for name in (f'{feature}-animation-time0', f'{feature}-animation-time{end}')) + (
    'rich-sky-translate', 'celestial-off', 'celestial-translate', 'celestial-rock', 'celestial-star',
    'blackhole-off', 'blackhole-lensing-zero', 'blackhole-lensing-one', 'blackhole-translate',) + HORIZON
TOLERANCE = 3 / 255


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def strict_json(path):
    def object_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'duplicate JSON key: {key}')
            result[key] = value
        return result

    def reject_constant(value):
        raise ValueError(f'nonfinite JSON number: {value}')

    return json.loads(path.read_text(encoding='utf-8-sig'),
                      object_pairs_hook=object_pairs, parse_constant=reject_constant)


def check(directory):
    directory = Path(directory)
    errors, timings, decoded_diffs = [], [], []

    def require(condition, message):
        if not condition:
            errors.append(message)

    try:
        records = strict_json(directory / 'results.json')
        summary = strict_json(directory / 'summary.json')
        # This historical protocol remains pinned to its original 58-field schema.
        metadata = strict_json(Path(__file__).resolve().parent / 'schema-v2-baseline.json')
        if not isinstance(records, list) or not isinstance(summary, dict):
            raise ValueError('results must be a list; summary must be an object')
        if not all(isinstance(r, dict) and isinstance(r.get('name'), str) for r in records):
            raise ValueError('each record needs a string name')
    except (OSError, ValueError) as error:
        return {'ok': False, 'errors': [str(error)]}

    fields = {field['key']: field for field in metadata['fields']}
    by_name = {r['name']: r for r in records}
    require(len(by_name) == len(records), 'duplicate scenario names')
    require(set(by_name) == set(NAMES), 'missing/unexpected scenarios')
    require(len(records) == 39 and summary.get('casesCompleted') == 39, 'expected 39 completed scenarios')
    require(summary.get('directionCasesPassed') == 8, 'expected 8 direction passes')
    require(summary.get('animationCasesPassed') == 6, 'expected 6 animation passes')
    require(summary.get('richTranslationCasesPassed') == 1, 'expected rich sky translation pass')
    require(summary.get('schema') == 'cosmic-sky/2' and summary.get('parameters') == 58, 'expected 58-field schema /2')
    for key, count in (('celestialDisplayPassCount', 1), ('blackholeDisplayPassCount', 1),
                       ('blackholeLensingPassCount', 1), ('celestialTranslationPassCount', 1),
                       ('blackholeTranslationPassCount', 1), ('celestialSurfaceTypePassCount', 2)):
        require(summary.get(key) == count, f'missing/wrong summary {key}')
    require(summary.get('minecraft') == '1.20.1' and summary.get('forge') == '47.4.22', 'wrong version baseline')
    for field in ('world', 'gpu', 'glVersion', 'glslVersion'):
        require(isinstance(summary.get(field), str) and bool(summary[field].strip()), f'missing summary {field}')

    def image(name, suffix='sky'):
        with Image.open(directory / f'{name}-{suffix}.png') as source:
            source.load()
            return source.convert('RGBA')

    for name in NAMES:
        if name not in by_name:
            continue
        r = by_name[name]
        size = (800, 600) if name == 'direction-resize' else (960, 540)
        require((r.get('width'), r.get('height')) == size, f'{name}: framebuffer size mismatch')
        require(type(r.get('glErrorAfterSky')) is int and r['glErrorAfterSky'] == 0, f'{name}: nonzero/missing GL error')
        require(type(r.get('skyFrames')) is int and r['skyFrames'] >= 90, f'{name}: insufficient frames')
        quality = 1 if name.endswith('-low') else 3 if name.endswith('-high') else 2
        mode = 0 if name in DIRECTIONS else 1 if name.startswith(('stars-', 'twinkle-')) else 2
        require(r.get('quality') == quality and r.get('mode') == mode, f'{name}: wrong quality/mode')
        fov = 40 if name == 'direction-fov40' else 100 if name == 'direction-fov100' else 70
        require(r.get('requestedFov') == fov, f'{name}: wrong FOV')
        for field, length in (('cameraPosition', 3), ('cameraLook', 3), ('projection', 16), ('inverseViewRotation', 9)):
            value = r.get(field)
            require(isinstance(value, list) and len(value) == length and all(map(finite, value)), f'{name}: invalid {field}')
        if name in HORIZON:
            look = r.get('cameraLook')
            require(isinstance(look, list) and len(look) == 3 and finite(look[1]) and abs(look[1]) <= 1e-6,
                    f'{name}: camera was not at pitch zero')
        before, after = r.get('generationBefore'), r.get('generationAfter')
        valid_gen = type(before) is int and type(after) is int and before > 0 and after >= before
        require(valid_gen, f'{name}: invalid shader generations')
        if name == 'direction-reload':
            require(valid_gen and after > before, f'{name}: shader was not replaced on reload')

        recipe = r.get('recipe', {})
        p = recipe.get('parameters', {}) if isinstance(recipe, dict) else {}
        valid_recipe = (isinstance(recipe, dict) and set(recipe) == {'schema', 'parameters'}
                        and recipe.get('schema') == 'cosmic-sky/2' and isinstance(p, dict) and set(p) == set(fields))
        require(valid_recipe, f'{name}: incomplete/unknown recipe fields')
        if valid_recipe:
            for key, field in fields.items():
                value = p[key]
                if field.get('color'):
                    valid = isinstance(value, str) and len(value) == 7 and value[0] == '#' and all(c in '0123456789abcdefABCDEF' for c in value[1:])
                else:
                    valid = finite(value) and field['min'] <= value <= field['max'] and (not field.get('integer') or value == int(value))
                require(valid, f'{name}: invalid recipe parameter {key}')
            require(p['quality'] == quality, f'{name}: recipe/render quality disagree')
        requested = r.get('requestedTime')
        require(finite(requested) and r.get('actualTime') == requested and requested >= 0, f'{name}: invalid/floating sample time')
        n, median, p95 = (r.get(k) for k in ('gpuSampleCount', 'skyGpuMedianMs', 'skyGpuP95Ms'))
        valid_time = type(n) is int and n > 0 and finite(median) and finite(p95) and 0 <= median <= p95
        require(valid_time, f'{name}: invalid GPU statistics')
        if valid_time and name in EFFECTS + HORIZON:
            timings.append({'name': name, 'samples': n, 'medianMs': median, 'p95Ms': p95})
        try:
            with image(name) as sky, image(name, 'world') as world:
                require(sky.size == size and world.size == size, f'{name}: decoded PNG size mismatch')
                if name in DIRECTIONS:
                    samples = r.get('directionSamples')
                    if not isinstance(samples, list) or len(samples) != 9:
                        raise ValueError('expected 9 direction samples')
                    expected_pixels = {(int(u * size[0]), int(v * size[1])) for u in (.1, .5, .9) for v in (.1, .5, .9)}
                    require({tuple(s['pixel']) for s in samples} == expected_pixels, f'{name}: wrong sample locations')
                    differences = []
                    for s in samples:
                        for field in ('actual', 'expected'):
                            if len(s[field]) != 3 or not all(finite(v) and 0 <= v <= 1 for v in s[field]):
                                raise ValueError('invalid direction RGB')
                        png_rgb = [channel / 255 for channel in sky.getpixel(tuple(s['pixel']))[:3]]
                        require(max(abs(a - b) for a, b in zip(png_rgb, s['actual'])) < 1e-8, f'{name}: recorded RGB disagrees with PNG')
                        differences.extend(abs(a - b) for a, b in zip(s['actual'], s['expected']))
                    maximum = max(differences)
                    require(maximum <= TOLERANCE and r.get('directionPass') is True, f'{name}: direction failed')
                    require(finite(r.get('directionMaxError')) and abs(r['directionMaxError'] - maximum) <= 1e-8, f'{name}: wrong reported direction error')
                if name == 'terrain-occlusion':
                    require(sky.getpixel((size[0] // 2, size[1] // 2)) != world.getpixel((size[0] // 2, size[1] // 2)), 'terrain-occlusion: wall did not cover sky')
        except (OSError, ValueError, KeyError, TypeError) as error:
            errors.append(f'{name}: invalid PNG/math ({error})')

    def compare(first, second, expected, record_field, pass_field):
        if first not in by_name or second not in by_name:
            return
        try:
            with image(first) as a, image(second) as b:
                left_pixels = getattr(a, 'get_flattened_data', a.getdata)()
                right_pixels = getattr(b, 'get_flattened_data', b.getdata)()
                count = sum(left != right for left, right in zip(left_pixels, right_pixels)) if a.size == b.size else -1
            r = by_name[second]
            require(count == r.get(record_field), f'{second}: recorded diff disagrees with decoded PNGs')
            require((count == 0 if expected == 'same' else count > 0) and r.get(pass_field) is True, f'{second}: independent PNG comparison failed')
            decoded_diffs.append({'first': first, 'second': second, 'changedPixels': count})
        except (OSError, ValueError) as error:
            errors.append(f'{second}: cannot compare PNGs ({error})')

    compare('direction-base', 'direction-translate', 'same', 'differentPixelsFromBase', 'identicalToBase')
    compare('direction-base', 'direction-reload', 'same', 'differentPixelsFromBase', 'identicalToBase')
    compare('nebula-medium', 'rich-sky-translate', 'same', 'differentPixelsFromRichBase', 'richTranslationPass')
    for feature, end in ANIMATIONS.items():
        first, second = f'{feature}-animation-time0', f'{feature}-animation-time{end}'
        if first in by_name and second in by_name:
            a, b = by_name[first], by_name[second]
            require(a.get('actualTime') == 0 and b.get('actualTime') == end, f'{feature}: wrong animation times')
            require(a.get('recipe') == b.get('recipe'), f'{feature}: animation pair recipes disagree')
            for field in ('cameraLook', 'cameraPosition', 'projection', 'inverseViewRotation', 'quality', 'mode'):
                require(a.get(field) == b.get(field), f'{feature}: animation pair changed {field}')
            p = a.get('recipe', {}).get('parameters', {})
            for key in (('horizonGlow', 'galaxyStrength') if feature == 'twinkle' else
                        ('starDensity', 'starBrightness', 'horizonGlow', 'galaxyStrength')):
                require(p.get(key) == 0, f'{feature}: background {key} was not disabled')
            for other, strength in (('nebula', 'nebulaStrength'), ('aurora', 'auroraStrength'), ('meteor', 'meteorRate'),
                                    ('celestial', 'celestialStrength'), ('blackhole', 'blackHoleStrength')):
                if other != feature:
                    require(p.get(strength) == 0, f'{feature}: other effect {other} was enabled')
            if feature == 'meteor':
                require(p.get('meteorRate') == 8, 'meteor: expected rate 8')
        compare(first, second, 'different', 'differentPixelsFromAnimationBase', 'animationPass')
    if 'nebula-medium' in by_name and 'rich-sky-translate' in by_name:
        first, second = by_name['nebula-medium'], by_name['rich-sky-translate']
        for field in ('recipe', 'actualTime', 'quality', 'cameraLook', 'projection', 'inverseViewRotation'):
            require(first.get(field) == second.get(field), f'rich-sky-translate: changed {field}')
        require(first.get('cameraPosition') != second.get('cameraPosition'), 'rich-sky-translate: camera did not translate')
    for first, second, count_field, pass_field, identical in (
        ('celestial-off', 'celestial-animation-time0', 'differentPixelsFromCelestialOff', 'celestialDisplayPass', False),
        ('blackhole-off', 'blackhole-animation-time0', 'differentPixelsFromBlackholeOff', 'blackholeDisplayPass', False),
        ('celestial-animation-time0', 'celestial-translate', 'differentPixelsFromCelestialBase', 'celestialTranslationPass', True),
        ('blackhole-animation-time0', 'blackhole-translate', 'differentPixelsFromBlackholeBase', 'blackholeTranslationPass', True),
        ('blackhole-lensing-zero', 'blackhole-lensing-one', 'differentPixelsFromLensingZero', 'blackholeLensingPass', False),
        ('celestial-animation-time0', 'celestial-rock', 'differentPixelsFromGasBody', 'celestialSurfaceTypePass', False),
        ('celestial-animation-time0', 'celestial-star', 'differentPixelsFromGasBody', 'celestialSurfaceTypePass', False),
    ):
        compare(first, second, 'same' if identical else 'different', count_field, pass_field)
        if first in by_name and second in by_name:
            a, b = by_name[first], by_name[second]
            for field in ('actualTime', 'quality', 'cameraLook', 'projection', 'inverseViewRotation'):
                require(a.get(field) == b.get(field), f'{second}: comparison changed {field}')
            if identical:
                require(a.get('recipe') == b.get('recipe'), f'{second}: translation changed recipe')
                require(a.get('cameraPosition') != b.get('cameraPosition'), f'{second}: camera did not translate')
    if 'blackhole-lensing-zero' in by_name and 'blackhole-lensing-one' in by_name:
        a = by_name['blackhole-lensing-zero']['recipe']['parameters']
        b = by_name['blackhole-lensing-one']['recipe']['parameters']
        require({key for key in a if a[key] != b[key]} == {'lensingStrength'}, 'lensing pair changed another parameter')
        require(a['lensingStrength'] == 0 and b['lensingStrength'] == 1, 'lensing comparison must be 0 versus 1')
        require(a['diskColorA'] == a['diskColorB'] == '#000000', 'lensing pair has emission enabled')
    return {'ok': not errors, 'errors': errors, 'cases': len(records), 'gpu': summary.get('gpu'),
            'timings': timings, 'decodedPixelComparisons': decoded_diffs,
            'scope': 'Fixed lab protocol, math records, strict recipes and PNG pixel checks; inspect artistic quality separately.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check(args.directory)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(text + '\n', encoding='utf-8')
    print(text)
    raise SystemExit(0 if result['ok'] else 1)
