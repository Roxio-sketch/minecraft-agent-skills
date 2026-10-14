"""Index selected upstream files without importing or running any of them."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parent
PROJECTS = [
    {
        'id': 'three-water2', 'folder': 'three.js', 'repo': 'mrdoob/three.js',
        'commit': '9b4a2ac29c63ccb43fd51c5661f2f873ac2c39b8',
        'selectedRef': 'refs/heads/master', 'license': 'MIT',
        'licenseFile': 'LICENSE', 'category': 'surface-flow-planar-water',
        'notices': ['Copyright © 2010-2026 three.js authors',
                    'Water2 cites Vlachos SIGGRAPH10 water flow and graphicsrunner.',
                    'Reflector/Refractor retain oblique clipping and projection references.'],
        'read': {
            'LICENSE': [[1,21]], 'README.md': [],
            'examples/jsm/objects/Water2.js': [[1,403]],
            'examples/jsm/objects/Reflector.js': [[112,254]],
            'examples/jsm/objects/Refractor.js': [[107,285]],
            'examples/jsm/shaders/WaterRefractionShader.js': [[1,106]],
        },
        'anchors': [
            ['examples/jsm/objects/Water2.js', 'updateFlow', 'function updateFlow()'],
            ['examples/jsm/objects/Water2.js', 'flow-normal-blend', 'float flowLerp ='],
            ['examples/jsm/objects/Water2.js', 'Fresnel', 'float reflectance ='],
            ['examples/jsm/objects/Reflector.js', 'reflection-pass', 'this.onBeforeRender ='],
            ['examples/jsm/objects/Refractor.js', 'refraction-pass', 'function render( renderer, scene, camera )'],
        ],
        'omitted': ['textures/water/*.jpg', 'three core runtime', 'demo scenes'],
        'acquisition': 'fixed-commit raw.githubusercontent.com selected files',
    },
    {
        'id': 'waterways', 'folder': 'Waterways', 'repo': 'Arnklit/Waterways',
        'commit': 'cd2e2193fe2c7792efb4c35118ade3ad868538b4',
        'selectedRef': 'refs/heads/main', 'license': 'MIT; retain file notices',
        'licenseFile': 'LICENSE', 'category': 'surface-flow-baked-obstacles',
        'notices': ['Copyright (c) 2021 Kasper Arnklit Frandsen',
                    'river.shader retains comment that ease came from math_funcs.cpp; upstream identity is not pinned by that comment.',
                    'File headers say LICENSE.md; actual pinned root file is LICENSE. No upstream text changed.'],
        'read': {
            'LICENSE': [[1,21]],
            'addons/waterways/shaders/river.shader': [[1,233]],
            'addons/waterways/river_manager.gd': [[596,691]],
            'addons/waterways/water_helper_methods.gd': [[1,273]],
            'addons/waterways/shaders/filters/normal_to_flow_filter.shader': [[1,16]],
            'addons/waterways/shaders/filters/normal_map_pass.shader': [[1,31]],
            'addons/waterways/shaders/filters/flow_pressure_pass.shader': [[1,17]],
            'addons/waterways/shaders/filters/foam_pass.shader': [[1,26]],
        },
        'anchors': [
            ['addons/waterways/shaders/river.shader', 'FlowUVW', 'vec3 FlowUVW('],
            ['addons/waterways/shaders/river.shader', 'flow-strength', 'float flow_force ='],
            ['addons/waterways/shaders/river.shader', 'refraction-foreground-check', 'if (surface_dist2 < -VERTEX.z)'],
            ['addons/waterways/river_manager.gd', 'bake-flowmap', 'func _generate_flowmap('],
            ['addons/waterways/water_helper_methods.gd', 'mesh', 'static func generate_river_mesh('],
            ['addons/waterways/shaders/filters/normal_to_flow_filter.shader', 'nm2flow', 'vec3 nm2flow('],
            ['addons/waterways/shaders/filters/flow_pressure_pass.shader', 'occupancy-pressure-heuristic', 'value += textureLod'],
        ],
        'omitted': ['binary textures', 'plugin scenes/resources', 'project and runtime'],
        'acquisition': 'fixed-commit codeload archive; ZIP CRC checked in memory; selected LICENSE and .gd/.shader files retained',
        'archiveBytes': 3101784,
    },
    {
        'id': 'tiled-directional-flow', 'folder': 'Tiled-Directional-Flow',
        'repo': 'Scrawk/Tiled-Directional-Flow',
        'commit': '386892cc3f6e21b4694b2777a9e7e66d47a3dd67',
        'selectedRef': 'HEAD', 'license': 'MIT root + retained Frans van Hoesel Creditware notice',
        'licenseFile': 'LICENSE', 'category': 'surface-tiled-direction-flow',
        'notices': ['Copyright (c) 2017 Justin', 'Frans van Hoesel (c) 2010 Creditware at shader lines 49-59 and root LICENSE'],
        'read': {'LICENSE': [[1,34]], 'README.md': [[1,200]],
                 'Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader': [[1,248]]},
        'anchors': [
            ['Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader', 'Creditware', 'This software is Creditware:'],
            ['Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader', 'surf', 'void surf ('],
            ['Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader', 'blend-weights', 'float2 ff ='],
            ['Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader', 'rotation-scale', 'float2x2 rotmat ='],
            ['Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader', 'normal-normalization-compensation', 'NormalT = (NormalT - 0.5) / (ffscale.y * ffscale.x);'],
        ],
        'omitted': ['Water normal texture', 'hpcvrock texture', 'skybox', 'Unity scenes'],
        'acquisition': 'fixed-commit codeload archive; ZIP CRC checked in memory; selected source retained',
        'archiveBytes': 17407778,
    },
]

for project in PROJECTS:
    base = ROOT / project['folder']
    project['url'] = 'https://github.com/' + project['repo']
    project['files'] = []
    for path in sorted(base.rglob('*')):
        if not path.is_file():
            continue
        rel = path.relative_to(base).as_posix()
        data = path.read_bytes()
        lines = data.decode('utf-8-sig').splitlines()
        ranges = [[a,min(b,len(lines))] for a,b in project['read'].get(rel,[]) if a <= len(lines)]
        project['files'].append({
            'path': rel, 'localPath': path.relative_to(ROOT).as_posix(),
            'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'sourceUrl': f"https://github.com/{project['repo']}/blob/{project['commit']}/{rel}",
            'rawUrl': f"https://raw.githubusercontent.com/{project['repo']}/{project['commit']}/{rel}",
            'lineCount': len(lines), 'readRanges': ranges,
            'readStatus': 'body-read' if ranges else 'acquired-only',
        })
    actual_anchors = []
    for rel,symbol,needle in project['anchors']:
        lines = (base / rel).read_text(encoding='utf-8-sig').splitlines()
        found = [i for i,line in enumerate(lines,1) if needle in line]
        if not found:
            raise ValueError(f'missing anchor: {project["id"]}/{symbol}: {needle}')
        actual_anchors.append({'path': rel, 'symbol': symbol, 'line': found[0], 'needle': needle,
            'url': f"https://github.com/{project['repo']}/blob/{project['commit']}/{rel}#L{found[0]}"})
    project['anchors'] = actual_anchors
    del project['read']

out = {'schema': 'github-source-study/1', 'accessedDate': '2026-10-11',
       'timezone': 'Asia/Shanghai', 'scope': 'static source study; downloaded projects not executed',
       'upstreamCodeModified': False, 'projects': PROJECTS}
(ROOT / 'manifest.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Indexed',len(PROJECTS),'projects,',sum(len(p['files']) for p in PROJECTS),'files')
