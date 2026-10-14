"""Check bundle integrity and historical evidence; does not rerun GPU/Forge/Minecraft."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def verify(root):
    errors = []
    required = ['SKILL.md', 'agents/openai.yaml', 'references/source-evidence.json',
                'references/evidence/gpu-validation.json', 'references/evidence/forge-probe.log',
                'assets/gpu-probe/index.html', 'assets/gpu-probe/validate.cjs',
                'assets/forge-api-probe/gradlew.bat',
                'assets/forge-api-probe/gradle/wrapper/gradle-wrapper.jar',
                'assets/forge-inkrealm-example/build.gradle',
                'assets/forge-inkrealm-example/gradle/wrapper/gradle-wrapper.jar',
                'assets/forge-inkrealm-example/src/main/java/dev/inkrealm/InkClient.java',
                'assets/forge-inkrealm-example/src/main/resources/META-INF/mods.toml',
                'scripts/scaffold_inkrealm.py', 'scripts/verify_inkrealm_evidence.py',
                'references/runtime-inkrealm.md',
                'references/evidence/inkrealm/deployment.json',
                'references/evidence/inkrealm/verification.json',
                'references/evidence/inkrealm/scaffold-build.json',
                'references/evidence/inkrealm/scaffold-build.log']
    for name in required:
        if not (root / name).is_file():
            errors.append('Missing required file: ' + name)
    links = 0
    for doc in [root / 'SKILL.md', *sorted((root / 'references').glob('*.md'))]:
        if not doc.is_file():
            continue
        for raw in re.findall(r'\[[^\]]*\]\(([^)]+)\)', doc.read_text(encoding='utf-8')):
            if '://' in raw or raw.startswith('#'):
                continue
            target = raw.split('#', 1)[0]
            links += 1
            if not (doc.parent / target).is_file():
                errors.append(f'Broken local link in {doc.name}: {raw}')
    try:
        gpu = json.loads((root / 'references/evidence/gpu-validation.json').read_text(encoding='utf-8'))
        if len(gpu['checks']) != 29 or not gpu['passed'] or not all(c['passed'] for c in gpu['checks']):
            errors.append('Recorded GPU baseline is incomplete or failed')
        if gpu.get('pageErrors'):
            errors.append('Recorded GPU baseline has page errors')
        if 'NOT Minecraft' not in gpu['scope']:
            errors.append('GPU scope label missing')
        log = (root / 'references/evidence/forge-probe.log').read_text(encoding='utf-8')
        if 'CONTRACTS_PASSED=20' not in log or 'BUILD SUCCESSFUL' not in log:
            errors.append('Recorded Forge compile/math baseline missing')
        sources = json.loads((root / 'references/source-evidence.json').read_text(encoding='utf-8'))
        ids = [r['id'] for r in sources['files']]
        if len(ids) != 36 or len(set(ids)) != len(ids):
            errors.append('Source index count or identity mismatch')
        for record in sources['files']:
            for nums in record['anchors'].values():
                if any(n < 1 or n > record['line_count'] for n in nums):
                    errors.append('Invalid source anchor in ' + record['id'])
        manifest = json.loads((root / 'references/evidence/bundle-assets.json').read_text(encoding='utf-8'))
        names = [entry['path'] for entry in manifest['files']]
        expected_files = {p.relative_to(root).as_posix()
                          for folder in ('assets', 'references/evidence', 'scripts')
                          for p in (root / folder).rglob('*')
                          if p.is_file() and p.name != 'bundle-assets.json'
                          and '__pycache__' not in p.parts and p.suffix != '.pyc'}
        if set(names) != expected_files or len(names) != len(set(names)):
            errors.append('Bundle manifest inventory is incomplete, unexpected or duplicated')
        for entry in manifest['files']:
            path = (root / entry['path']).resolve()
            if root not in path.parents:
                errors.append('Manifest path escapes bundle: ' + entry['path'])
                continue
            if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
                errors.append('Bundled asset hash mismatch: ' + entry['path'])
        runtime_root = root / 'references/evidence/inkrealm'
        runtime = json.loads((runtime_root / 'verification.json').read_text(encoding='utf-8'))
        if not runtime['passed'] or runtime['cases'] != 16 or not runtime['checks'] or not all(c['passed'] for c in runtime['checks']):
            errors.append('Recorded Ink Realm runtime audit is incomplete or failed')
        checks = {c['name']: c for c in runtime['checks']}
        for name in ['independent red-sail parallax', 'crossing entry and return',
                     'inside independent pixel change', 'explicit off resets visual domain',
                     'injected failure restores recorded state']:
            if name not in checks:
                errors.append('Missing recorded runtime check: ' + name)
        build = json.loads((runtime_root / 'scaffold-build.json').read_text(encoding='utf-8'))
        if not build['passed'] or not build['source_files_identical'] or not build['jar_payloads_identical']:
            errors.append('Fresh scaffold build or payload reproduction failed')
        build_log = (runtime_root / 'scaffold-build.log').read_text(encoding='utf-8')
        if 'BUILD SUCCESSFUL' not in build_log:
            errors.append('Missing successful fresh scaffold build log')
        deployment = json.loads((runtime_root / 'deployment.json').read_text(encoding='utf-8-sig'))
        if build['original_tested_jar_sha256'] != deployment['sha256'].lower():
            errors.append('Reproduction references a different tested artifact')
    except (OSError, KeyError, ValueError, TypeError) as exc:
        errors.append(str(exc))
    return {'passed': not errors, 'local_links_checked': links, 'errors': errors,
            'scope': 'Bundle integrity and historical research/runtime evidence; not a new runtime test'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', nargs='?', type=Path, default=Path(__file__).resolve().parents[1])
    result = verify(parser.parse_args().root.resolve())
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result['passed'] else 1)
