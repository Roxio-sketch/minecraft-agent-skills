#!/usr/bin/env python3
"""Independent /4 Forge checks of actual sky/world programs and framebuffer/depth readback."""
import argparse
import json
import math
from pathlib import Path
from PIL import Image

NAMES = ('full-off full-cloud-time0 full-cloud-time8 full-cloud-translate full-cloud-inside full-cloud-above '
         'full-cloud-low full-cloud-high full-shadow-off full-shadow-on full-shafts-off full-shafts-on '
         'full-fog-off full-fog-on full-rain-off full-rain-time0 full-rain-time2 full-rain-world '
         'full-blackhole2d-off full-blackhole2d-time0 full-blackhole2d-time4 '
         'full-rainbow-off full-rainbow full-halo-off full-halo full-special-off full-special '
         'full-occlusion full-resize full-fov100 full-reload').split()
PACKS = {
    'WeatherSettings': ['rainAmount', 'thunderAmount', 'weatherSpeed', 'stormDarkening'],
    'RainSettings': ['rainDensity', 'rainSpeed', 'rainLength', 'rainBrightness'],
    'WeatherMotion': ['rainWind', 'weatherFollowWorld', 'lightningFrequency', 'lightningDuration'],
    'FogSettings': ['fogStrength', 'fogDensity', 'fogHeightFalloff', 'fogBaseHeight'],
    'FogMotion': ['fogNoise', 'fogScale', 'fogSpeed', 'fogDistance'],
    'VolumeCloudSettings': ['volumeCloudStrength', 'volumeCloudCoverage', 'volumeCloudDensity', 'volumeCloudScale'],
    'VolumeCloudGeometry': ['volumeCloudBase', 'volumeCloudThickness', 'volumeCloudDistance', 'volumeCloudErosion'],
    'VolumeCloudMotion': ['volumeCloudSpeed', 'volumeCloudWind', 'volumeCloudEvolution', 'volumeCloudAnisotropy'],
    'CloudLighting': ['volumeCloudAmbient', 'volumeCloudSunlight', 'cloudShadowStrength', 'lightShaftStrength'],
    'LightingDetail': ['lightShaftDensity', 'lightShaftDistance', 'cloudShadowSoftness', 'lightShaftAnisotropy'],
    'RainbowSettings': ['rainbowStrength', 'rainbowWidth', 'secondaryRainbow', 'rainbowWetness'],
    'HaloSettings': ['haloStrength', 'haloWidth', 'haloIce', 'haloMoon'],
    'SpecialSettings': ['specialStrength', 'specialStyle', 'specialScale', 'specialSpeed'],
    'SpecialShape': ['specialDistortion', 'specialBandWidth', 'birdDensity', 'birdSpeed'],
    'BlackHoleAppearance': ['blackHoleStyle', 'blackHoleRingBrightness', 'blackHoleRingWidth', 'blackHoleGlow'],
    'BlackHoleCinematic': ['blackHoleArc', 'blackHoleStreak', 'blackHoleContrast', 'blackHoleNoise'],
}
COLORS = {'FogColor': 'fogColor', 'VolumeCloudColor': 'volumeCloudColor', 'RainColor': 'rainColor',
          'LightningColor': 'lightningColor', 'SpecialColorA': 'specialColorA', 'SpecialColorB': 'specialColorB'}
ANGLES = {'rainWind', 'volumeCloudWind', 'rainbowWidth', 'haloWidth'}


def finite(v):
    return type(v) in (int, float) and math.isfinite(v)


def strict(path):
    def pairs(items):
        result = {}
        for k, v in items:
            if k in result:
                raise ValueError('duplicate JSON key ' + k)
            result[k] = v
        return result
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=pairs,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError('nonfinite ' + x)))


def inverse4(values):
    # Java/JOML values are column-major; perform independent row elimination.
    matrix = [[values[c*4+r] for c in range(4)]+[float(r == c) for c in range(4)] for r in range(4)]
    for c in range(4):
        pivot = max(range(c, 4), key=lambda r: abs(matrix[r][c]))
        matrix[c], matrix[pivot] = matrix[pivot], matrix[c]
        scale = matrix[c][c]; matrix[c] = [x/scale for x in matrix[c]]
        for r in range(4):
            if r != c:
                factor = matrix[r][c]; matrix[r] = [a-factor*b for a, b in zip(matrix[r], matrix[c])]
    return [matrix[r][c+4] for c in range(4) for r in range(4)]


def pixels(image):
    return getattr(image, 'get_flattened_data', image.getdata)()


def check(folder):
    folder = Path(folder)
    errors, comparisons = [], []
    def require(ok, message):
        if not ok:
            errors.append(message)
    try:
        records, summary = strict(folder/'results.json'), strict(folder/'summary.json')
        fields = {f['key']: f for f in strict(Path(__file__).with_name('schema-v4-validation.json'))['fields']}
        by = {r['name']: r for r in records}
        require(len(by) == len(records) == len(NAMES) and set(by) == set(NAMES), 'expected exactly 31 unique full-sky cases')
        require(summary.get('validationProtocol') == 'cosmic-full-sky-forge/1', 'wrong validation protocol')
        require(summary.get('modVersion') == '0.5.0' and summary.get('schema') == 'cosmic-sky/4', 'wrong mod/schema version')
        require(summary.get('parameters') == len(fields) == 160 and summary.get('casesCompleted') == 31, 'wrong fields/count')
        require(summary.get('minecraft') == '1.20.1' and summary.get('forge') == '47.4.22', 'wrong game/loader')
        require(summary.get('gpu') and summary.get('world', '').startswith('COSMIC_LAB_'), 'missing real GPU/isolated world')
    except (OSError, ValueError, TypeError, KeyError) as e:
        return {'ok': False, 'errors': [str(e)]}

    def im(name, suffix):
        with Image.open(folder/f'{name}-{suffix}.png') as source:
            return source.convert('RGBA')

    def gpu(r, program_key, bound_key, uniforms_key, require_names):
        name = r['name']
        require(type(r.get(program_key)) is int and r[program_key] > 0 and r[program_key] == r.get(bound_key), name+': actual draw program mismatch')
        gpu = r.get(uniforms_key, {})
        p = r['recipe']['parameters']
        for name_u, u in gpu.items():
            require(type(u.get('location')) is int, name+': invalid uniform location '+name_u)
            if name_u in require_names:
                require(u.get('location', -1) >= 0, name+': inactive required uniform '+name_u)
            if u.get('location', -1) < 0:
                continue
            v = u.get('values', [])
            require(isinstance(v, list) and v and all(map(finite, v)), name+': invalid GPU values '+name_u)
            expected = None
            if name_u in PACKS:
                expected = [math.radians(p[k]) if k in ANGLES else p[k] for k in PACKS[name_u]]
            elif name_u in COLORS:
                c = p[COLORS[name_u]][1:]; expected = [int(c[i:i+2], 16)/255 for i in (0, 2, 4)]
            elif name_u == 'CameraPosition':
                expected = r.get('cameraPosition')
            elif name_u == 'Time':
                expected = [r.get('actualTime')]
            elif name_u == 'Quality':
                expected = [r.get('quality')]
            elif name_u == 'InverseViewRotation':
                expected = r.get('inverseViewRotation')
            elif name_u == 'InverseProjection':
                expected = inverse4(r.get('projection'))
            elif name_u == 'WorldWeather':
                require(len(v) == 2 and all(0 <= x <= 1 for x in v), name+': weather is not actual 0..1 input')
            if expected is not None:
                require(len(v) == len(expected) and all(abs(a-b) <= 1e-4*max(1, abs(b)) for a, b in zip(v, expected)), name+': uploaded value mismatch '+name_u)

    for name in NAMES:
        if name not in by:
            continue
        r = by[name]; p = r.get('recipe', {}).get('parameters', {})
        size = (800, 600) if name == 'full-resize' else (960, 540)
        require(r.get('recipe', {}).get('schema') == 'cosmic-sky/4' and set(p) == set(fields), name+': strict /4 field set mismatch')
        for k, f in fields.items():
            if k not in p:
                continue
            v = p[k]
            if f.get('color'):
                valid = isinstance(v, str) and len(v) == 7 and v[0] == '#' and all(c in '0123456789abcdef' for c in v[1:])
            else:
                valid = finite(v) and f['min'] <= v <= f['max'] and (not f.get('integer') or v == int(v))
            require(valid, name+': invalid recipe parameter '+k)
        require((r.get('width'), r.get('height')) == size, name+': wrong framebuffer size')
        require(r.get('glErrorAfterSky') == 0 and r.get('skyFrames', 0) >= 90, name+': failed sky draw')
        require(r.get('actualTime') == r.get('requestedTime') and finite(r.get('actualTime')), name+': time not fixed')
        require(r.get('quality') == (1 if name.endswith('low') else 3 if name.endswith('high') else 2), name+': quality differs')
        for key, count in [('cameraPosition', 3), ('cameraLook', 3), ('projection', 16), ('inverseViewRotation', 9)]:
            require(isinstance(r.get(key), list) and len(r[key]) == count and all(map(finite, r[key])), name+': invalid camera '+key)
        fov = 100 if name == 'full-fov100' else 90 if name.startswith('full-rainbow') or name.startswith('full-halo') else 70
        scale = 1/math.tan(math.radians(fov)/2)
        require(abs(r['projection'][5]-scale) < 1e-5 and abs(r['projection'][0]-scale*size[1]/size[0]) < 1e-5,
                name+': projection differs from real FOV/aspect')
        # Eye-space -Z must reconstruct the independently recorded Camera look.
        rotation = r['inverseViewRotation']
        ray = [-rotation[6], -rotation[7], -rotation[8]]
        length = math.sqrt(sum(v*v for v in ray)); ray = [v/length for v in ray]
        require(all(abs(a-b) < 1e-5 for a, b in zip(ray, r['cameraLook'])), name+': reconstructed center ray differs from actual Camera')
        gpu(r, 'skyDrawProgram', 'skyBoundProgramAtDraw', 'skyGpuUniforms',
            {'WorldWeather', 'WeatherSettings', 'WeatherMotion', 'RainbowSettings', 'HaloSettings', 'SpecialSettings', 'SpecialShape', 'BlackHoleAppearance', 'BlackHoleCinematic'})
        try:
            for suffix in ['sky', 'world', 'scene', 'post']:
                with im(name, suffix) as image:
                    require(image.size == size, name+': wrong decoded PNG size '+suffix)
        except (OSError, ValueError) as e:
            errors.append(name+': PNG failure '+str(e))
        if r.get('environmentSkipped') is False:
            gpu(r, 'environmentProgram', 'environmentBoundProgram', 'environmentGpuUniforms',
                {'InverseProjection', 'InverseViewRotation', 'CameraPosition', 'WorldWeather', 'Time', 'Quality',
                 'WeatherSettings', 'WeatherMotion', 'RainSettings', 'FogSettings', 'FogMotion', 'VolumeCloudSettings',
                 'VolumeCloudGeometry', 'VolumeCloudMotion', 'CloudLighting', 'LightingDetail', 'FogColor', 'VolumeCloudColor', 'RainColor', 'SceneSampler', 'DepthSampler'})
            require(r.get('environmentProgram') != r.get('skyDrawProgram'), name+': environment reused sky program')
            require(r.get('snapshotFbo') != r.get('mainFbo') and r.get('sceneTexture') != r.get('mainColorTexture'), name+': read/write framebuffer feedback')
            require(r.get('maxDepthDifference') == 0, name+': environment changed scene depth')
            require(r.get('environmentStateRestored') is True and r.get('glErrorAfterEnvironment') == 0, name+': state/GL error')
            require(r.get('geometryPixels', -1)+r.get('skyPixels', -1) == size[0]*size[1], name+': invalid depth classification')
            with im(name, 'scene') as a, im(name, 'post') as b:
                count = sum(x != y for x, y in zip(pixels(a), pixels(b)))
                require(count == r.get('postChangedPixels'), name+': actual framebuffer changed count mismatch')
        else:
            require(r.get('environmentSkipped') is True, name+': missing post gate proof')

    def pair(a, b, suffix='post', minimum=10, same_recipe=True, identical=False):
        if a not in by or b not in by:
            return
        try:
            with im(a, suffix) as ia, im(b, suffix) as ib:
                count = sum(x != y for x, y in zip(pixels(ia), pixels(ib))) if ia.size == ib.size else -1
            require(count == 0 if identical else count >= minimum, f'{a}/{b}: effect comparison failed ({count})')
            if same_recipe:
                require(by[a]['recipe'] == by[b]['recipe'], b+': animation/translation unexpectedly changed recipe')
            comparisons.append({'first': a, 'second': b, 'suffix': suffix, 'changedPixels': count})
        except (OSError, ValueError, KeyError) as e:
            errors.append(f'{a}/{b}: '+str(e))
    pair('full-off', 'full-cloud-time0', minimum=1000, same_recipe=False)
    pair('full-cloud-time0', 'full-cloud-time8', minimum=1000)
    pair('full-cloud-time0', 'full-cloud-translate', minimum=1000)
    pair('full-cloud-time0', 'full-reload', identical=True)
    pair('full-shadow-off', 'full-shadow-on', same_recipe=False)
    pair('full-shafts-off', 'full-shafts-on', same_recipe=False)
    pair('full-fog-off', 'full-fog-on', same_recipe=False)
    pair('full-rain-off', 'full-rain-time0', same_recipe=False)
    pair('full-rain-time0', 'full-rain-time2')
    pair('full-blackhole2d-off', 'full-blackhole2d-time0', 'sky', minimum=1000, same_recipe=False)
    pair('full-blackhole2d-time0', 'full-blackhole2d-time4', 'sky')
    for feature in ['rainbow', 'halo', 'special']:
        pair('full-'+feature+'-off', 'full-'+feature, 'sky', minimum=100, same_recipe=False)
    for name in ['full-cloud-inside', 'full-cloud-above', 'full-cloud-low', 'full-cloud-high']:
        require(by.get(name, {}).get('postChangedPixels', 0) > 1000, name+': cloud is absent')
    occlusion = by.get('full-occlusion', {})
    require(occlusion.get('geometryPixels', 0) > 1000 and occlusion.get('skyChangedPixels', 0) > 1000 and occlusion.get('geometryChangedPixels') == 0, 'foreground wall must occlude farther cloud, sky cloud must remain')
    for name in ['full-shadow-on', 'full-fog-on', 'full-rain-time0']:
        require(by.get(name, {}).get('geometryChangedPixels', 0) > 10, name+': world surface effect absent')
    world_rain = by.get('full-rain-world', {}).get('environmentGpuUniforms', {}).get('WorldWeather', {}).get('values', [])
    require(len(world_rain) == 2 and world_rain[0] > 0, 'actual client rain input was not enabled')
    require(by.get('full-reload', {}).get('generationAfter', 0) > by.get('full-reload', {}).get('generationBefore', 0), 'sky resource reload did not replace shader')
    require(by.get('full-reload', {}).get('environmentGeneration', 0) > by.get('full-cloud-time0', {}).get('environmentGeneration', 0), 'environment resource reload did not replace shader')
    return {'ok': not errors, 'errors': errors, 'cases': len(records), 'gpu': summary.get('gpu'), 'protocol': summary.get('validationProtocol'),
            'decodedPixelComparisons': comparisons, 'scope': 'Actual distinct sky/world programs, strict 160-field /4 recipes, GL uniform readback, copied color/depth framebuffer, cloud world motion/parallax/interior, surface shadows, shafts, fog/rain, directional phenomena, reload/resize/FOV and restored GL state. Existing 39/26 historical evidence is preserved separately.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path); parser.add_argument('--output', type=Path)
    args = parser.parse_args(); result = check(args.directory)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(text+'\n', encoding='utf-8')
    print(text); raise SystemExit(0 if result['ok'] else 1)
