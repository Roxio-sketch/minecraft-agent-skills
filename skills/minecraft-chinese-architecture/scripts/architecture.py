"""One entry point for the architecture skill's editable JSON generators."""
from __future__ import annotations

import argparse
import copy
import importlib
import json
from pathlib import Path


ENGINES = {
    'chinese-hall/1': 'generate_hall', 'chinese-building/1': 'generate_building',
    'chinese-courtyard/1': 'generate_courtyard', 'chinese-multi-court/1': 'generate_multi_court',
    'chinese-tower/1': 'generate_tower', 'chinese-corridor/1': 'generate_corridor',
    'chinese-staircase/1': 'generate_staircase',
    'chinese-garden/1': 'generate_garden', 'chinese-complex/1': 'generate_complex',
    'chinese-regional/1': 'generate_regional', 'chinese-landmark/1': 'generate_landmark',
    'chinese-inhabited/1': 'generate_inhabited', 'chinese-functional/1': 'generate_functional',
    'chinese-infrastructure/1': 'generate_infrastructure', 'chinese-detail/1': 'generate_detail',
    'chinese-scene/1': 'compose_scene', 'chinese-finish/1': 'apply_finish',
    'chinese-settlement/1': 'generate_settlement',
    'chinese-vernacular/1': 'generate_vernacular', 'chinese-ornament/1': 'generate_ornament',
}


def load_config(path):
    """Resolve data paths relative to their config, never the shell's cwd."""
    path = Path(path).resolve()
    config = json.loads(path.read_text(encoding='utf-8-sig'))
    if not isinstance(config, dict): raise ValueError('Configuration must be a JSON object')
    def resolve(value):
        if isinstance(value, dict):
            result = {}
            for key, item in value.items():
                if key in ('heightmap', 'model_file') and isinstance(item, str):
                    p = Path(item); result[key] = str(p if p.is_absolute() else (path.parent / p).resolve())
                else: result[key] = resolve(item)
            return result
        return [resolve(v) for v in value] if isinstance(value, list) else value
    return resolve(config)


def generate(config, out_dir, name='building', render=True):
    if not isinstance(name, str) or not name or Path(name).name != name or any(v in name for v in '/\\:'):
        raise ValueError('Use a simple output name without path separators')
    if not isinstance(config, dict) or config.get('schema') not in ENGINES:
        raise ValueError('Specify a supported schema; use architecture.py catalog to list them')
    from model_provenance import guard_input_outputs
    guard_input_outputs(config, out_dir, name)
    module = importlib.import_module(ENGINES[config['schema']])
    result = module.export(copy.deepcopy(config), out_dir, name, render)
    # The input snapshot sits beside the result, so an editable starting point
    # remains available even when a preset is changed or moved later.
    (Path(out_dir) / (name + '.config.json')).write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding='utf8')
    return result


def build_component(config):
    """In-memory builders for scene composition, including entrance adapters."""
    if not isinstance(config, dict): raise ValueError('Component config must be an object')
    schema = config.get('schema')
    if schema == 'chinese-building/1':
        from generate_building import make_building, verify
        m, d, c = make_building(config); d['checks'] = verify(m, d, c)
        door = d['doorway']; x = door['aisle_x']
        d.update(start_floor_xyz=[x, 0, -d['platform_half_z'] - c['platform_height']],
                 target_floors_xyz={'inside': [x, door['floor_y'], door['z'] + 2]})
        return m, d
    if schema == 'chinese-complex/1':
        from generate_complex import make_complex
        m, d, _, _ = make_complex(config)
        d.update(start_floor_xyz=d['anchors_floor_xyz']['entrance'], target_floors_xyz=d['targets_floor_xyz'])
        return m, d
    factories = {
        'chinese-courtyard/1': 'assemble', 'chinese-multi-court/1': 'assemble',
        'chinese-tower/1': 'make_tower', 'chinese-corridor/1': 'make_corridor',
        'chinese-staircase/1': 'make_staircase',
        'chinese-garden/1': 'make_garden', 'chinese-regional/1': 'make_regional',
        'chinese-landmark/1': 'make_landmark', 'chinese-inhabited/1': 'make_inhabited',
        'chinese-functional/1': 'make_functional', 'chinese-infrastructure/1': 'make_infrastructure',
        'chinese-detail/1': 'make_detail', 'chinese-scene/1': 'make_scene',
        'chinese-settlement/1': 'make_settlement',
        'chinese-vernacular/1': 'make_vernacular', 'chinese-ornament/1': 'make_ornament',
    }
    if schema not in factories: raise ValueError('This schema has no composable builder; export/import its NPZ explicitly')
    m, d = getattr(importlib.import_module(ENGINES[schema]), factories[schema])(copy.deepcopy(config))
    if schema == 'chinese-tower/1':
        routes = d['routes']['routes_floor_xyz']; d['start_floor_xyz'] = next(iter(routes.values()))[0]
        d['target_floors_xyz'] = {key: path[-1] for key, path in routes.items()}
    if schema == 'chinese-corridor/1':
        routes = d['routes']['routes_floor_xyz']; d['start_floor_xyz'] = next(iter(routes.values()))[0]
        d['target_floors_xyz'] = {key: path[-1] for key, path in routes.items()}
    return m, d


def catalog():
    assets = Path(__file__).resolve().parents[1] / 'assets'
    presets = []
    for p in sorted(assets.glob('*.json')):
        data = json.loads(p.read_text('utf-8-sig'))
        if data.get('schema') in ENGINES: presets.append({'name': p.stem, 'schema': data['schema'], 'path': str(p)})
    return {'schemas': ENGINES, 'presets': presets}


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__); commands = ap.add_subparsers(dest='command', required=True)
    commands.add_parser('catalog', help='List installed generators and editable presets')
    run = commands.add_parser('generate', help='Generate and verify a local structure')
    run.add_argument('--config', required=True, type=Path); run.add_argument('--out-dir', required=True, type=Path)
    run.add_argument('--name', default='building'); run.add_argument('--no-render', action='store_true')
    a = ap.parse_args()
    if a.command == 'catalog': print(json.dumps(catalog(), ensure_ascii=False, indent=2))
    else:
        r = generate(load_config(a.config), a.out_dir, a.name, not a.no_render)
        print(json.dumps({k: r[k] for k in ('name', 'dimensions_xyz', 'nonair_voxels', 'non_air_blocks', 'schematic_sha256') if k in r}, ensure_ascii=False))
