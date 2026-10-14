"""Package only source/assets; exclude saves, caches, user exports and reference packs."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SKILL = ROOT.parent / 'skill' / 'minecraft-cosmic-rendering'
WORKBENCH_FILES = ('index.html', 'style.css', 'app.js', 'recipe.js', 'sky-core.glsl',
                   'sky-extra.glsl', 'environment-core.glsl', 'environment-preview.js',
                   'atmosphere-physical.glsl', 'atmosphere-luts.js', 'weather-core.glsl', 'weather-data.js',
                   'schema.json', 'default.json', 'presets.json', 'launch.py',
                   'package.json', 'sync-metadata.mjs', 'README.md', 'LICENSE',
                   'sky-library.html', 'sky-library.css', 'sky-library.js', 'sky-library-data.json')
FORGE_FILES = ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew',
               'gradlew.bat', '.gitignore', 'LICENSE', 'LICENSE.txt', 'run-lab.ps1', 'check-validation.py',
               'check-atmosphere-validation.py', 'check-full-sky-validation.py',
               'schema-v2-baseline.json', 'schema-v3-baseline.json', 'schema-v4-validation.json')


def workbench_files():
    extras = tuple(p.relative_to(ROOT/'workbench').as_posix()
                   for p in sorted((ROOT/'workbench'/'forge-support').rglob('*'))
                   if p.is_file() and p.suffix in {'.fsh', '.vsh', '.json'})
    return WORKBENCH_FILES + extras


def forge_files():
    advanced = ('check-advanced-validation.py', 'check-advanced-validation-v6.py', 'sync-advanced-resources.py', 'schema-v5-validation.json', 'schema-v6-validation.json')
    return FORGE_FILES + tuple(n for n in advanced if (ROOT/'forge'/n).is_file())


def copy_assets():
    target = SKILL / 'assets' / 'procedural-sky-lab'
    for name in workbench_files():
        dest = target / 'workbench' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / 'workbench' / name, dest)
    for name in forge_files():
        dest = target / 'forge' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / 'forge' / name, dest)
    for name in ('src', 'gradle', 'tools'):
        if (ROOT/'forge'/name).is_dir():
            shutil.copytree(ROOT / 'forge' / name, target / 'forge' / name, dirs_exist_ok=True)
    shutil.copy2(ROOT / 'LICENSE', target / 'LICENSE')


def zip_files(path, files):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
        for source, name in files:
            archive.write(source, name)


def main():
    copy_assets()
    properties = dict(line.split('=', 1) for line in (ROOT/'forge'/'gradle.properties').read_text(encoding='utf-8').splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    version = properties['mod_version']
    folder = ROOT / 'deliverables'
    folder.mkdir(exist_ok=True)
    bench = folder / f'星空工坊-程序化天空工作台-{version}.zip'
    zip_files(bench, [(ROOT/'workbench'/n, f'workbench/{n}') for n in workbench_files()])
    skill = folder / f'minecraft-cosmic-rendering-{version}.zip'
    zip_files(skill, [(p, f'minecraft-cosmic-rendering/{p.relative_to(SKILL).as_posix()}')
                     for p in sorted(SKILL.rglob('*')) if p.is_file()])
    jar = folder / f'cosmic-sky-lab-{version}.jar'
    shutil.copy2(ROOT/'forge'/'build'/'libs'/jar.name, jar)
    manifest = {p.name: {'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
                for p in (bench, skill, jar)}
    (folder/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
