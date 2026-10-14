#!/usr/bin/env python3
"""Read-only checks for the bundled lab's fixed 15-case validation protocol.

Uses only Python's standard library. Checks recorded samples and PNG headers,
not decoded image content, artistic quality, or arbitrary shader correctness.
"""
import argparse
import json
import math
import struct
from pathlib import Path

DIRECTIONS = (
    'direction-base', 'direction-translate', 'direction-yaw90',
    'direction-pitch-up', 'direction-fov40', 'direction-fov100',
    'direction-resize', 'direction-reload',
)
EFFECTS = tuple(f'{mode}-{quality}' for mode in ('stars', 'nebula')
                for quality in ('low', 'medium', 'high'))
NAMES = DIRECTIONS + EFFECTS + ('terrain-occlusion',)
TOLERANCE = 3 / 255


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def check(directory):
    directory = Path(directory)
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    try:
        records = json.loads((directory / 'results.json').read_text(encoding='utf-8-sig'))
        summary = json.loads((directory / 'summary.json').read_text(encoding='utf-8-sig'))
        if not isinstance(records, list) or not isinstance(summary, dict):
            raise ValueError('results must be a list and summary must be an object')
        if not all(isinstance(r, dict) and isinstance(r.get('name'), str) for r in records):
            raise ValueError('each record needs a string name')
    except (OSError, ValueError) as exc:
        return {'ok': False, 'errors': [str(exc)]}

    names = [r['name'] for r in records]
    require(len(names) == len(set(names)), 'duplicate scenario names')
    require(set(names) == set(NAMES), 'missing or unexpected scenarios')
    require(len(records) == 15, 'expected 15 records')
    require(summary.get('casesCompleted') == 15, 'summary casesCompleted must be 15')
    require(summary.get('directionCasesPassed') == 8, 'summary directionCasesPassed must be 8')
    require(summary.get('minecraft') == '1.20.1', 'unexpected Minecraft baseline')
    require(summary.get('forge') == '47.4.22', 'unexpected Forge baseline')
    for field in ('world', 'gpu', 'glVersion', 'glslVersion'):
        require(isinstance(summary.get(field), str) and bool(summary[field].strip()),
                f'missing summary {field}')

    by_name = {r['name']: r for r in records}
    timings = []
    for name in NAMES:
        if name not in by_name:
            continue
        r = by_name[name]
        size = (800, 600) if name == 'direction-resize' else (960, 540)
        require((r.get('width'), r.get('height')) == size, f'{name}: framebuffer size mismatch')
        require(type(r.get('glErrorAfterSky')) is int and r['glErrorAfterSky'] == 0,
                f'{name}: missing/nonzero GL error')
        require(type(r.get('skyFrames')) is int and r['skyFrames'] >= 90,
                f'{name}: insufficient sky frames')
        quality = 1 if name.endswith('-low') else 3 if name.endswith('-high') else 2
        mode = 0 if name in DIRECTIONS else 1 if name.startswith('stars-') else 2
        require(r.get('quality') == quality and r.get('mode') == mode, f'{name}: wrong mode/quality')
        fov = 40 if name == 'direction-fov40' else 100 if name == 'direction-fov100' else 70
        require(r.get('requestedFov') == fov, f'{name}: wrong requested FOV')
        for field, length in (('cameraPosition', 3), ('cameraLook', 3),
                              ('projection', 16), ('inverseViewRotation', 9)):
            value = r.get(field)
            require(isinstance(value, list) and len(value) == length and all(map(finite, value)),
                    f'{name}: invalid {field}')
        for suffix in ('-sky.png', '-world.png'):
            try:
                with (directory / (name + suffix)).open('rb') as file:
                    header = file.read(24)
                require(len(header) == 24 and header[:8] == b'\x89PNG\r\n\x1a\n'
                        and header[12:16] == b'IHDR' and struct.unpack('>II', header[16:24]) == size,
                        f'{name}{suffix}: invalid PNG header/size')
            except OSError as exc:
                errors.append(f'{name}{suffix}: {exc.strerror}')
        n, median, p95 = (r.get(k) for k in ('gpuSampleCount', 'skyGpuMedianMs', 'skyGpuP95Ms'))
        valid_time = (type(n) is int and n > 0 and finite(median) and finite(p95)
                      and 0 <= median <= p95)
        require(valid_time, f'{name}: missing/invalid GPU statistics')
        if valid_time and name in EFFECTS:
            timings.append({'name': name, 'samples': n, 'medianMs': median, 'p95Ms': p95})
        before, after = r.get('generationBefore'), r.get('generationAfter')
        valid_gen = type(before) is int and type(after) is int and before > 0 and after >= before
        require(valid_gen, f'{name}: invalid shader generations')
        if name == 'direction-reload':
            require(valid_gen and after > before, f'{name}: shader instance was not replaced')
        if name in ('direction-translate', 'direction-reload'):
            require(r.get('identicalToBase') is True and type(r.get('differentPixelsFromBase')) is int
                    and r['differentPixelsFromBase'] == 0, f'{name}: recorded pixels differ from base')
        if name in DIRECTIONS:
            samples = r.get('directionSamples')
            try:
                if not isinstance(samples, list) or len(samples) != 9:
                    raise ValueError('expected 9 samples')
                expected_pixels = {(int(u * size[0]), int(v * size[1]))
                                   for u in (0.1, 0.5, 0.9) for v in (0.1, 0.5, 0.9)}
                if {tuple(s['pixel']) for s in samples} != expected_pixels:
                    raise ValueError('wrong pixel locations')
                differences = []
                for s in samples:
                    for field in ('actual', 'expected'):
                        if len(s[field]) != 3 or not all(finite(x) and 0 <= x <= 1 for x in s[field]):
                            raise ValueError('invalid RGB sample')
                    differences.extend(abs(a - b) for a, b in zip(s['actual'], s['expected']))
                maximum = max(differences)
                require(maximum <= TOLERANCE, f'{name}: direction error exceeds 3/255')
                reported = r.get('directionMaxError')
                require(finite(reported) and abs(reported - maximum) <= 1e-8,
                        f'{name}: reported error disagrees with samples')
            except (ValueError, KeyError, TypeError) as exc:
                errors.append(f'{name}: invalid direction samples ({exc})')
            require(r.get('directionPass') is True, f'{name}: directionPass is not true')

    wall = by_name.get('terrain-occlusion', {})
    try:
        sky, world = int(wall['skyCenterPixelABGR']), int(wall['worldCenterPixelABGR'])
        require(0 <= sky <= 0xFFFFFFFF and 0 <= world <= 0xFFFFFFFF and sky != world,
                'terrain-occlusion: center color did not change or is invalid')
    except (KeyError, ValueError, TypeError):
        errors.append('terrain-occlusion: missing/invalid center pixels')
    return {'ok': not errors, 'errors': errors, 'cases': len(records), 'gpu': summary.get('gpu'),
            'timings': timings,
            'scope': 'Recorded numerical checks and PNG headers only; inspect rendered images separately.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, help='validation output directory')
    result = check(parser.parse_args().directory)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result['ok'] else 1)
