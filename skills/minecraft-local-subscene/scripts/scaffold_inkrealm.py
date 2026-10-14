"""Copy the tested Forge 1.20.1 Ink Realm sources into an empty project directory."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path


def scaffold(destination, skill_root=None):
    root = (skill_root or Path(__file__).resolve().parents[1]).resolve()
    source = root / 'assets/forge-inkrealm-example'
    destination = Path(destination).resolve()
    # Never copy into the skill, its parents, or the template itself.
    if destination == root or root in destination.parents or destination in root.parents:
        raise ValueError('Destination must be outside the skill directory and its ancestors')
    if destination.exists() and (not destination.is_dir() or any(destination.iterdir())):
        raise ValueError('Destination must be a new or empty directory')
    manifest = json.loads((root / 'references/evidence/bundle-assets.json').read_text(encoding='utf-8'))
    prefix = 'assets/forge-inkrealm-example/'
    records = {r['path'][len(prefix):]: r['sha256'] for r in manifest['files'] if r['path'].startswith(prefix)}
    files = {p.relative_to(source).as_posix(): p for p in source.rglob('*') if p.is_file()}
    if not records or files.keys() != records.keys():
        raise ValueError('Template file inventory does not match the bundle manifest')
    for name, path in files.items():
        if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != records[name]:
            raise ValueError('Template integrity check failed: ' + name)
    # Verify everything before starting to copy. A partial I/O failure remains visible.
    shutil.copytree(source, destination, dirs_exist_ok=True)
    return {'created': str(destination), 'files': len(files), 'mod_id': 'inkrealm',
            'version': '0.2.0', 'next': 'Set JAVA_HOME to Java 17, then run gradlew.bat build',
            'scope': 'Copied tested source; new build and runtime validation still required'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(scaffold(args.destination), ensure_ascii=False, indent=2))
    except (OSError, ValueError, KeyError) as exc:
        parser.exit(1, f'Scaffold failed: {exc}\n')
