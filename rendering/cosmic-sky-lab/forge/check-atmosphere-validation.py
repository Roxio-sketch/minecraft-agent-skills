#!/usr/bin/env python3
"""Independent PNG, camera and GPU uniform checks for cosmic-atmosphere-forge/1.

The historical 39-case /2 protocol has a separate checker and is not rerun here.
Requires Pillow. This measures the artistic analytic atmosphere and thin cloud
sheet, not physical atmospheric scattering, world illumination or volume clouds.
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image

NAMES = (
    'ring-offscreen-off', 'ring-offscreen-on', 'ring-visible-off', 'ring-visible-on',
    'atmo-day', 'atmo-sunset', 'atmo-twilight', 'atmo-night', 'atmo-moon-new',
    'atmo-moon-full', 'atmo-moon-quarter', 'atmo-cloud-off', 'atmo-cloud-time0',
    'atmo-cloud-time4', 'atmo-cloud-low', 'atmo-cloud-high', 'atmo-cycle-time0',
    'atmo-cycle-time30', 'atmo-translate', 'atmo-yaw90', 'atmo-fov40',
    'atmo-fov100', 'atmo-reload', 'atmo-wall', 'atmo-disabled-base', 'atmo-disabled-extreme',
)
PACKS = {
    'AtmosphereSettings': ('atmosphereStrength', 'turbidity', 'rayleighStrength', 'mieStrength'),
    'SunSettings': ('sunBrightness', 'sunElevation', 'sunYaw', 'sunSize'),
    'DaySettings': ('cycleSpeed', 'mieAnisotropy', 'nightAmbient', 'twilightStrength'),
    'MoonSettings': ('moonBrightness', 'moonYaw', 'moonElevation', 'moonSize'),
    'MoonSurface': ('moonPhase', 'moonDetail', 'moonHalo', 'moonSpeed'),
    'ThinCloudSettings': ('thinCloudStrength', 'thinCloudCoverage', 'thinCloudScale', 'thinCloudHeight'),
    'ThinCloudMotion': ('thinCloudSpeed', 'thinCloudWind', 'thinCloudEvolution', 'thinCloudSilver'),
    'SkyTint': ('skyTint',), 'SunColor': ('sunColor',), 'MoonColor': ('moonColor',),
    'ThinCloudColor': ('thinCloudColor',),
}
ANGLES = {'sunElevation', 'sunYaw', 'sunSize', 'moonYaw', 'moonElevation', 'moonSize', 'moonPhase', 'thinCloudWind'}
CAMERA_FIELDS = ('cameraLook', 'cameraPosition', 'projection', 'inverseViewRotation', 'quality', 'mode')


def finite(v):
    return type(v) in (int, float) and math.isfinite(v)


def strict_json(path):
    def pairs(items):
        out = {}
        for key, value in items:
            if key in out:
                raise ValueError(f'duplicate key: {key}')
            out[key] = value
        return out
    def constant(value):
        raise ValueError(f'nonfinite value: {value}')
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=pairs, parse_constant=constant)


def check(directory):
    directory = Path(directory)
    errors, comparisons, timings = [], [], []
    def require(ok, message):
        if not ok:
            errors.append(message)
    try:
        records = strict_json(directory / 'results.json')
        summary = strict_json(directory / 'summary.json')
        schema = strict_json(Path(__file__).resolve().parent / 'schema-v3-baseline.json')
        fields = {f['key']: f for f in schema['fields']}
        by_name = {r['name']: r for r in records}
        require(len(records) == len(by_name) == len(NAMES) and set(by_name) == set(NAMES), 'expected exactly 26 unique scenarios')
        require(summary.get('casesCompleted') == len(NAMES), 'incomplete summary')
        require(summary.get('validationProtocol') == 'cosmic-atmosphere-forge/1', 'wrong independent protocol')
        require(summary.get('schema') == schema['schema'] == 'cosmic-sky/3' and len(fields) == summary.get('parameters') == 90, 'expected strict 90-field /3 recipes')
        require(summary.get('modVersion') == '0.4.0', 'wrong mod version')
        require(summary.get('minecraft') == '1.20.1' and summary.get('forge') == '47.4.22', 'wrong loader/version')
        for key in ('world', 'gpu', 'glVersion', 'glslVersion'):
            require(isinstance(summary.get(key), str) and bool(summary[key].strip()), f'missing {key}')
    except (OSError, ValueError, KeyError, TypeError) as e:
        return {'ok': False, 'errors': [str(e)]}

    def image(name, suffix='sky'):
        with Image.open(directory / f'{name}-{suffix}.png') as source:
            source.load()
            return source.convert('RGBA')

    for name in NAMES:
        if name not in by_name:
            continue
        r = by_name[name]
        size = (1072, 602) if name.startswith('ring-') else (960, 540)
        quality = 1 if name.endswith('-low') else 3 if name.endswith('-high') else 2
        fov = 40 if name == 'atmo-fov40' else 100 if name == 'atmo-fov100' else 70
        require((r.get('width'), r.get('height')) == size, f'{name}: wrong framebuffer size')
        require(r.get('quality') == quality and r.get('mode') == 2 and r.get('requestedFov') == fov, f'{name}: wrong render setup')
        require(r.get('glErrorAfterSky') == 0 and type(r.get('glErrorAfterSky')) is int, f'{name}: GL error')
        require(type(r.get('skyFrames')) is int and r['skyFrames'] >= 90, f'{name}: insufficient draw frames')
        require(r.get('actualTime') == r.get('requestedTime') and finite(r.get('actualTime')) and r['actualTime'] >= 0, f'{name}: time not fixed')
        for key, length in (('cameraPosition', 3), ('cameraLook', 3), ('projection', 16), ('inverseViewRotation', 9)):
            v = r.get(key)
            require(isinstance(v, list) and len(v) == length and all(map(finite, v)), f'{name}: invalid {key}')
        yaw, pitch = (-68, -2) if name.startswith('ring-offscreen-') else (24, -30) if name.startswith('ring-visible-') else (
            90 if name == 'atmo-yaw90' else 0,
            0 if name == 'atmo-wall' else -5 if name in ('atmo-sunset', 'atmo-twilight') or name.startswith('atmo-cycle-') else -25)
        yr, pr = math.radians(yaw), math.radians(pitch)
        expected_look = (-math.sin(yr)*math.cos(pr), -math.sin(pr), math.cos(yr)*math.cos(pr))
        actual_look = r.get('cameraLook', [])
        require(len(actual_look) == 3 and all(finite(v) and abs(v-e) < 1e-6 for v, e in zip(actual_look, expected_look)), f'{name}: actual view does not match requested yaw/pitch')
        projection = r.get('projection', [])
        vertical_scale = 1/math.tan(math.radians(fov)/2)
        require(len(projection) == 16 and finite(projection[5]) and finite(projection[0]) and
                abs(projection[5]-vertical_scale) < 1e-5 and abs(projection[0]-vertical_scale*size[1]/size[0]) < 1e-5,
                f'{name}: actual projection differs from requested FOV/aspect')
        before, after = r.get('generationBefore'), r.get('generationAfter')
        require(type(before) is int and type(after) is int and 0 < before <= after, f'{name}: invalid generations')
        if name == 'atmo-reload':
            require(type(before) is int and type(after) is int and after > before, 'atmo-reload: shader instance not replaced')
        recipe = r.get('recipe', {})
        p = recipe.get('parameters', {})
        good = set(recipe) == {'schema', 'parameters'} and recipe.get('schema') == 'cosmic-sky/3' and set(p) == set(fields)
        require(good, f'{name}: incomplete/unknown recipe')
        if good:
            for key, field in fields.items():
                v = p[key]
                if field.get('color'):
                    valid = isinstance(v, str) and len(v) == 7 and v[0] == '#' and all(c in '0123456789abcdefABCDEF' for c in v[1:])
                else:
                    valid = finite(v) and field['min'] <= v <= field['max'] and (not field.get('integer') or v == int(v))
                require(valid, f'{name}: invalid parameter {key}')
            require(p['quality'] == quality, f'{name}: recipe quality differs')
        n, median, p95 = (r.get(k) for k in ('gpuSampleCount', 'skyGpuMedianMs', 'skyGpuP95Ms'))
        require(type(n) is int and n > 0 and finite(median) and finite(p95) and 0 <= median <= p95, f'{name}: missing timing')
        timings.append({'name': name, 'quality': quality, 'samples': n, 'medianMs': median, 'p95Ms': p95})
        if name.startswith('atmo-') and good:
            program = r.get('drawShaderProgram')
            require(type(program) is int and program > 0 and program == r.get('boundShaderProgramAtDraw'), f'{name}: wrong actual draw program')
            gpu = r.get('atmosphereGpuUniforms', {})
            require(set(gpu) == set(PACKS), f'{name}: missing GPU uniform records')
            for uniform, keys in PACKS.items():
                u = gpu.get(uniform, {})
                require(type(u.get('location')) is int and u['location'] >= 0, f'{name}: {uniform} inactive')
                if len(keys) == 1:
                    color = p[keys[0]][1:]
                    expected = [int(color[i:i+2], 16)/255 for i in (0, 2, 4)]
                else:
                    expected = [math.radians(p[k]) if k in ANGLES else p[k] for k in keys]
                values = u.get('values')
                require(isinstance(values, list) and len(values) == len(expected) and all(map(finite, values)) and
                        all(abs(a-b) <= 1e-5 for a, b in zip(values, expected)), f'{name}: {uniform} GPU values differ from recipe')
        try:
            with image(name) as sky, image(name, 'world') as world:
                require(sky.size == world.size == size, f'{name}: decoded PNG size mismatch')
                if name == 'atmo-wall':
                    x, y = size[0]//2, size[1]//2
                    a, b = sky.getpixel((x, y)), world.getpixel((x, y))
                    require(a != b and b[0] > b[2]*1.4 and b[1] > b[2]*1.3, 'atmo-wall: gold wall did not cover sky')
        except (OSError, ValueError) as e:
            errors.append(f'{name}: PNG failure: {e}')

    def pair(a, b, minimum, count_field=None, pass_field=None, parameter_changes=None, translation=False):
        if a not in by_name or b not in by_name:
            return
        ra, rb = by_name[a], by_name[b]
        try:
            with image(a) as ia, image(b) as ib:
                left = getattr(ia, 'get_flattened_data', ia.getdata)()
                right = getattr(ib, 'get_flattened_data', ib.getdata)()
                count = sum(x != y for x, y in zip(left, right)) if ia.size == ib.size else -1
            require(count == 0 if minimum == 0 else count >= minimum, f'{a}/{b}: decoded pixel comparison failed ({count})')
            if count_field:
                require(rb.get(count_field) == count and rb.get(pass_field) is True, f'{b}: recorded pixel diff/pass mismatch')
            for key in CAMERA_FIELDS:
                if key == 'cameraPosition' and translation:
                    require(ra.get(key) != rb.get(key), f'{b}: camera did not translate')
                else:
                    require(ra.get(key) == rb.get(key), f'{b}: pair changed {key}')
            if parameter_changes is not None:
                pa, pb = ra['recipe']['parameters'], rb['recipe']['parameters']
                changes = {k for k in pa if pa[k] != pb[k]}
                require(changes == set(parameter_changes), f'{b}: unexpected recipe changes {changes}')
            comparisons.append({'first': a, 'second': b, 'changedPixels': count})
        except (OSError, ValueError, KeyError) as e:
            errors.append(f'{b}: comparison failure: {e}')

    pair('ring-offscreen-off', 'ring-offscreen-on', 0, 'differentPixelsFromRingOff', 'ringRegressionPass', {'ringStrength'})
    pair('ring-visible-off', 'ring-visible-on', 100, 'differentPixelsFromRingOff', 'ringRegressionPass', {'ringStrength'})
    pair('atmo-day', 'atmo-translate', 0, 'differentPixelsFromAtmosphereBase', 'atmosphereInvariantPass', set(), True)
    pair('atmo-day', 'atmo-reload', 0, 'differentPixelsFromAtmosphereBase', 'atmosphereInvariantPass', set())
    pair('atmo-cloud-off', 'atmo-cloud-time0', 1000, 'differentPixelsFromCloudOff', 'thinCloudDisplayPass', {'thinCloudStrength'})
    pair('atmo-cloud-time0', 'atmo-cloud-time4', 1000, 'differentPixelsFromCloudTime0', 'thinCloudAnimationPass', set())
    pair('atmo-cycle-time0', 'atmo-cycle-time30', 1000, 'differentPixelsFromCycleTime0', 'dayCyclePass', set())
    pair('atmo-moon-new', 'atmo-moon-full', 100, 'differentPixelsFromNewMoon', 'moonPhasePass', {'moonPhase'})
    pair('atmo-moon-new', 'atmo-moon-quarter', 100, 'differentPixelsFromNewMoon', 'moonPhasePass', {'moonPhase'})
    pair('atmo-disabled-base', 'atmo-disabled-extreme', 0, 'differentPixelsFromDisabledBase', 'atmosphereDisabledPass')
    for name, seconds in (('atmo-cloud-time0', 0), ('atmo-cloud-time4', 4), ('atmo-cycle-time0', 0), ('atmo-cycle-time30', 30)):
        require(by_name.get(name, {}).get('actualTime') == seconds, f'{name}: wrong sample time')
    for name in ('atmo-disabled-base', 'atmo-disabled-extreme'):
        require(by_name.get(name, {}).get('recipe', {}).get('parameters', {}).get('atmosphereStrength') == 0, f'{name}: atmosphere gate enabled')
    return {'ok': not errors, 'errors': errors, 'protocol': summary.get('validationProtocol'), 'cases': len(records),
            'gpu': summary.get('gpu'), 'timings': timings, 'decodedPixelComparisons': comparisons,
            'scope': '26 targeted GPU cases; strict /3 recipes, actual active uniform/upload readback, decoded PNG comparisons, reload/camera/depth. Historical 39 captures are preserved, not rerun.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check(args.directory)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(text+'\n', encoding='utf-8')
    print(text)
    raise SystemExit(0 if result['ok'] else 1)
