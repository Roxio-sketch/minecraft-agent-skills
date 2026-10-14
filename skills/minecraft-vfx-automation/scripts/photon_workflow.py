"""Local Forge Photon asset preparation, deployment, capture and immutable revisions.

Requires Pillow and nbtlib. No image generation service is invoked by this script.
"""
import argparse
import base64
import hashlib
import json
import math
import re
import shutil
import time
import urllib.request
import zipfile
from pathlib import Path

from PIL import Image, ImageOps
import nbtlib


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_json(path, data):
    Path(path).write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8')


def resource(value):
    if not re.fullmatch(r'[a-z0-9._-]+:[a-z0-9/._-]+', value) or '..' in value or value.split(':')[1].startswith('/'):
        raise ValueError('Invalid resource ID')
    return value.split(':', 1)


class Helper:
    def __init__(self, url='http://127.0.0.1:9876'):
        self.url = url.rstrip('/')

    def call(self, method, **fields):
        request = urllib.request.Request(self.url + '/api/cmd',
            json.dumps({'cmd': method, **fields}).encode(), {'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.load(response)
        if not isinstance(result, dict) or result.get('ok') is False or result.get('error'):
            raise RuntimeError(f'{method}: {result}')
        return result

    def reload(self):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            result = self.call('photon_status')
            if result['reload'] == 'complete':
                if not result['selected']:
                    raise RuntimeError('Generated pack not selected')
                time.sleep(3)
                self.call('photon_clear_fx_cache')
                return
            if result['reload'].startswith('failed'):
                raise RuntimeError(result)
            time.sleep(.4)
        raise TimeoutError('Resource reload')


def prepare(inputs, output, cell=128, columns=None, concept=None, prompt='', key=None):
    inputs = list(map(Path, inputs)); output = Path(output)
    if not 1 <= len(inputs) <= 256 or not 8 <= cell <= 2048:
        raise ValueError('Expected 1..256 frames; cell 8..2048')
    columns = columns or math.ceil(math.sqrt(len(inputs)))
    rows = math.ceil(len(inputs) / columns)
    if columns < 1 or columns > 32 or rows > 32 or max(columns, rows) * cell > 2048:
        raise ValueError('Atlas exceeds supported grid or 2048 pixels')
    output.mkdir(parents=True, exist_ok=False)
    (output / 'sources').mkdir(); (output / 'normalized').mkdir()
    atlas = Image.new('RGBA', (columns * cell, rows * cell))
    manifest = {'schema': 1, 'columns': columns, 'rows': rows, 'frames': len(inputs),
                'cell': cell, 'order': 'row-major, explicit input order', 'prompt': prompt,
                'alphaResize': 'Pillow premultiplied alpha Lanczos', 'chromaKey': key,
                'playback': 'once over particle lifetime', 'sources': [], 'warnings': []}
    for index, path in enumerate(inputs):
        source = output / 'sources' / f'{index:04d}{path.suffix.lower()}'
        shutil.copyfile(path, source)
        with Image.open(path) as loaded:
            if loaded.width * loaded.height > 16777216:
                raise ValueError('Source exceeds 16 million pixels')
            image = ImageOps.exif_transpose(loaded).convert('RGBA')
        if key:
            rgb = tuple(bytes.fromhex(key.lstrip('#')))
            if len(rgb) != 3:
                raise ValueError('Chroma key must be RRGGBB')
            image.putdata([(r, g, b, 0 if (r, g, b) == rgb else a) for r, g, b, a in image.getdata()])
        if image.getchannel('A').getextrema() == (255, 255):
            manifest['warnings'].append(f'Frame {index}: opaque background; no implicit removal')
        if image.getchannel('A').getextrema()[1] == 0:
            raise ValueError(f'Frame {index} is fully transparent')
        width, height = image.size
        scale = min((cell - 4) / width, (cell - 4) / height)
        # RGBa is premultiplied, so invisible RGB cannot contaminate visible edges.
        resized = image.convert('RGBa').resize((max(1, round(width * scale)), max(1, round(height * scale))), Image.Resampling.LANCZOS).convert('RGBA')
        frame = Image.new('RGBA', (cell, cell))
        frame.paste(resized, ((cell - resized.width) // 2, (cell - resized.height) // 2))
        frame.save(output / 'normalized' / f'{index:04d}.png')
        atlas.paste(frame, ((index % columns) * cell, (index // columns) * cell))
        manifest['sources'].append({'file': source.relative_to(output).as_posix(), 'sha256': sha(source)})
    atlas.save(output / 'atlas.png')
    if (output / 'atlas.png').stat().st_size > 4194304:
        raise ValueError('Atlas exceeds import limit of 4 MiB')
    if concept:
        destination = output / ('concept' + Path(concept).suffix.lower())
        shutil.copyfile(concept, destination)
        manifest['concept'] = {'file': destination.name, 'sha256': sha(destination)}
    manifest['atlasSha256'] = sha(output / 'atlas.png')
    write_json(output / 'asset.json', manifest)
    return manifest


def deploy(helper, prepared, effect, texture, mode='create', document=None, editor=False):
    resource(effect); resource(texture)
    prepared = Path(prepared); manifest = json.loads((prepared / 'asset.json').read_text(encoding='utf-8'))
    if sha(prepared / 'atlas.png') != manifest['atlasSha256']:
        raise ValueError('Atlas hash changed')
    helper.call('photon_import_texture', id=texture, mode=mode,
                png=base64.b64encode((prepared / 'atlas.png').read_bytes()).decode())
    helper.reload()
    document = document or {'emitters': [{'type': 'particle', 'name': 'Imported atlas',
        'color': '#FFFFFFFF', 'size': 1, 'rate': .05, 'lifetime': 80, 'speed': 0, 'maxParticles': 8}]}
    helper.call('photon_write_fx', id=effect, mode=mode, document=document)
    helper.reload()
    result = helper.call('photon_bind_texture', id=effect, texture=texture,
        columns=manifest['columns'], rows=manifest['rows'], frames=manifest['frames'])
    helper.reload()
    diagnosis = helper.call('photon_diagnose_fx', id=effect)
    if editor:
        try:
            helper.call('photon_editor_state')
        except RuntimeError:
            helper.call('execute_command', command='photon_editor')
            time.sleep(2)
        helper.call('photon_editor_load', id=effect)
        helper.call('photon_editor_bind_texture', texture=texture, columns=manifest['columns'],
                    rows=manifest['rows'], frames=manifest['frames'])
    write_json(prepared / 'deployment.json', {'effect': effect, 'texture': texture, 'document': document,
                                            'binding': result, 'diagnosis': diagnosis})
    return result


def capture(helper, effect, output, frames=40, fps=8, position=None, roi=None):
    output = Path(output); output.mkdir(parents=True, exist_ok=False)
    handle = None
    try:
        helper.call('client_resume')
        handle = helper.call('photon_start_fx', id=effect, maxTicks=3000, **(position or {}))['handle']
        helper.call('photon_capture_start', frames=frames, fps=fps)
        deadline = time.monotonic() + frames / fps * 4 + 30
        telemetry = []
        while time.monotonic() < deadline:
            state = helper.call('photon_capture_status')
            telemetry.append(helper.call('photon_runtime_status', handle=handle))
            if state['status'] != 'running':
                break
            time.sleep(.25)
        else:
            raise TimeoutError('Capture timeout')
        if state['status'] != 'complete':
            raise RuntimeError(state)
        source = Path(state['directory'])
        images = []
        for entry in state['frames']:
            path = source / entry['file']
            if sha(path) != entry['sha256']:
                raise ValueError('Captured frame hash mismatch')
            shutil.copyfile(path, output / path.name)
            with Image.open(path) as image:
                images.append(image.convert('RGB'))
        durations = [max(10, round(b['timeMs'] - a['timeMs'])) for a, b in zip(state['frames'], state['frames'][1:])]
        durations.append(durations[-1])
        previews = [ImageOps.contain(image, (800, 450)) for image in images]
        previews[0].save(output / 'preview.gif', save_all=True, append_images=previews[1:], duration=durations, loop=0)
        sheet = Image.new('RGB', (4 * 320, math.ceil(min(len(images), 16) / 4) * 180))
        for i in range(min(len(images), 16)):
            sample = images[round(i * (len(images) - 1) / max(1, min(len(images), 16) - 1))]
            sheet.paste(ImageOps.contain(sample, (320, 180)), ((i % 4) * 320, (i // 4) * 180))
        sheet.save(output / 'contact.png')
        hashes = [hashlib.sha256((im.crop(roi) if roi else im).tobytes()).hexdigest() for im in images]
        report = {'capture': state, 'telemetry': telemetry, 'roi': roi, 'uniqueRegionFrames': len(set(hashes)),
                  'actualFps': (len(images) - 1) * 1000 / (state['frames'][-1]['timeMs'] - state['frames'][0]['timeMs']),
                  'interpretation': 'Changing pixels alone do not prove intended VFX motion; inspect ROI and native telemetry.'}
        write_json(output / 'report.json', report)
        return report
    finally:
        helper.call('photon_capture_stop')
        if handle:
            helper.call('photon_stop_fx', handle=handle)


def references(tag):
    if isinstance(tag, dict):
        for key, value in tag.items():
            if key in ('texture', 'shader') and isinstance(value, str) and ':' in value:
                yield str(value)
            yield from references(value)
    elif isinstance(tag, (list, nbtlib.List)):
        for value in tag:
            yield from references(value)


def project_content(snbt):
    tag = nbtlib.parse_nbt(snbt)
    fx = tag['fx']
    for graph in [fx['mainFX'], *fx.get('subFXs', {}).values()]:
        objects = graph['fxObjects']
        ids = {str(obj['transform']['id']) for obj in objects}
        for obj in objects:
            transform = obj['transform']
            # Native FXRuntime attaches top-level objects to a fresh, unsaved preview root.
            if '_parentId' in transform and str(transform['_parentId']) not in ids:
                del transform['_parentId']
    return tag


def snapshot(helper, effect, game, output, prepared=None, evidence=None, editor=False):
    resource(effect); game = Path(game); output = Path(output)
    runtime = helper.call('photon_read_fx', id=effect)['snbt']
    project = helper.call('photon_editor_state')['snbt'] if editor else None
    output.mkdir(parents=True, exist_ok=False)
    (output / 'runtime.snbt').write_text(runtime, encoding='utf-8')
    ns, name = resource(effect)
    pack = game / 'resourcepacks/mcpmod-generated-vfx/assets'
    shutil.copyfile(pack / ns / 'fx' / (name + '.fx'), output / 'runtime.fx')
    if project:
        (output / 'project.snbt').write_text(project, encoding='utf-8')
        backup = helper.call('photon_editor_save')['backup']
        shutil.copyfile(backup, output / 'project.fxproj')
    refs = set(references(nbtlib.parse_nbt(runtime)))
    if project:
        refs.update(references(nbtlib.parse_nbt(project)))
    managed, external = [], []
    for ref in sorted(refs):
        namespace, path = resource(ref)
        if path.startswith('textures/mcp/') and path.endswith('.png'):
            source = pack / namespace / path
            destination = output / 'assets' / namespace / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            managed.append({'id': namespace + ':' + path[len('textures/mcp/'):-4],
                            'file': destination.relative_to(output).as_posix()})
        else:
            external.append(ref)
    if prepared:
        shutil.copytree(prepared, output / 'prepared')
    if evidence:
        shutil.copytree(evidence, output / 'evidence')
    manifest = {'schema': 1, 'effect': effect, 'managedAssets': managed, 'externalResources': external,
                'dependencies': {'minecraft': '1.20.1', 'loader': 'Forge', 'photon': '1.1.17', 'helper': 'test18'},
                'files': {p.relative_to(output).as_posix(): sha(p) for p in sorted(output.rglob('*')) if p.is_file()}}
    write_json(output / 'revision.json', manifest)
    return manifest


def verify_revision(folder):
    folder = Path(folder).resolve()
    manifest = json.loads((folder / 'revision.json').read_text(encoding='utf-8'))
    resource(manifest['effect'])
    if manifest.get('schema') != 1:
        raise ValueError('Unknown revision schema')
    for name, digest in manifest['files'].items():
        path = (folder / name).resolve()
        if not path.is_relative_to(folder) or path == folder or sha(path) != digest:
            raise ValueError('Revision path/hash validation failed: ' + name)
    required = {'runtime.snbt', 'runtime.fx'} | {a['file'] for a in manifest['managedAssets']}
    if (folder / 'project.snbt').exists():
        required.add('project.snbt')
    if not required.issubset(manifest['files']):
        raise ValueError('Revision contains unverified required files')
    seen = set()
    for asset in manifest['managedAssets']:
        ns, name = resource(asset['id'])
        if asset['id'] in seen or asset['file'] != f'assets/{ns}/textures/mcp/{name}.png':
            raise ValueError('Invalid or duplicate managed asset mapping')
        seen.add(asset['id'])
        with Image.open(folder / asset['file']) as image:
            if image.format != 'PNG' or image.mode != 'RGBA' or max(image.size) > 2048:
                raise ValueError('Invalid archived texture')
    nbtlib.parse_nbt((folder / 'runtime.snbt').read_text(encoding='utf-8'))
    if 'project.snbt' in manifest['files']:
        nbtlib.parse_nbt((folder / 'project.snbt').read_text(encoding='utf-8'))
    return manifest


def restore(helper, revision, game, backup, editor=False):
    revision = Path(revision); game = Path(game)
    manifest = verify_revision(revision)
    if editor and 'project.snbt' not in manifest['files']:
        raise ValueError('Revision has no project to restore')
    # Preserve the actual current runtime/project and all referenced managed textures first.
    snapshot(helper, manifest['effect'], game, backup, editor=editor)
    for asset in manifest['managedAssets']:
        ns, name = resource(asset['id'])
        path = game / 'resourcepacks/mcpmod-generated-vfx/assets' / ns / 'textures/mcp' / (name + '.png')
        params = {'expectedSha256': sha(path)} if path.exists() else {}
        helper.call('photon_import_texture', id=asset['id'], mode='replace' if path.exists() else 'create',
                    png=base64.b64encode((revision / asset['file']).read_bytes()).decode(), **params)
        helper.reload()
    ns, name = resource(manifest['effect'])
    path = game / 'resourcepacks/mcpmod-generated-vfx/assets' / ns / 'fx' / (name + '.fx')
    helper.call('photon_restore_fx', id=manifest['effect'], mode='replace', expectedSha256=sha(path),
                snbt=(revision / 'runtime.snbt').read_text(encoding='utf-8'))
    helper.reload()
    if editor and (revision / 'project.snbt').exists():
        helper.call('photon_editor_restore', snbt=(revision / 'project.snbt').read_text(encoding='utf-8'))
        actual = helper.call('photon_editor_state')['snbt']
        if project_content(actual) != project_content((revision / 'project.snbt').read_text(encoding='utf-8')):
            raise RuntimeError('Restored authored project data differs; backup retained')
    if nbtlib.parse_nbt(helper.call('photon_read_fx', id=manifest['effect'])['snbt']) != nbtlib.parse_nbt((revision / 'runtime.snbt').read_text(encoding='utf-8')):
        raise RuntimeError('Restored runtime readback differs; backup retained')
    for asset in manifest['managedAssets']:
        ns, name = resource(asset['id'])
        path = game / 'resourcepacks/mcpmod-generated-vfx/assets' / ns / 'textures/mcp' / (name + '.png')
        if sha(path) != manifest['files'][asset['file']]:
            raise RuntimeError('Restored texture hash differs; backup retained')
    return helper.call('photon_diagnose_fx', id=manifest['effect'])


def bundle(revision, output):
    revision = Path(revision); manifest = verify_revision(revision)
    with zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(set(manifest['files']) | {'revision.json'}):
            archive.write(revision / name, name)
    return {'file': str(output), 'sha256': sha(output)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:9876')
    commands = parser.add_subparsers(dest='command', required=True)
    p = commands.add_parser('prepare'); p.add_argument('inputs', nargs='+', type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--cell', type=int, default=128)
    p.add_argument('--columns', type=int); p.add_argument('--concept', type=Path); p.add_argument('--prompt', default=''); p.add_argument('--key')
    p = commands.add_parser('deploy'); p.add_argument('--prepared', type=Path, required=True)
    p.add_argument('--effect', required=True); p.add_argument('--texture', required=True); p.add_argument('--mode', choices=['create', 'replace'], default='create')
    p.add_argument('--editor', action='store_true')
    p = commands.add_parser('capture'); p.add_argument('--effect', required=True); p.add_argument('--output', type=Path, required=True)
    p.add_argument('--frames', type=int, default=40); p.add_argument('--fps', type=int, default=8)
    p.add_argument('--position', type=float, nargs=3); p.add_argument('--roi', type=int, nargs=4)
    for command in ('snapshot', 'restore'):
        p = commands.add_parser(command); p.add_argument('--game', type=Path, required=True); p.add_argument('--editor', action='store_true')
        if command == 'snapshot':
            p.add_argument('--effect', required=True); p.add_argument('--output', type=Path, required=True)
            p.add_argument('--prepared', type=Path); p.add_argument('--evidence', type=Path)
        else:
            p.add_argument('--revision', type=Path, required=True); p.add_argument('--backup', type=Path, required=True)
    p = commands.add_parser('bundle'); p.add_argument('--revision', type=Path, required=True); p.add_argument('--output', type=Path, required=True)
    args = vars(parser.parse_args()); command = args.pop('command'); helper = Helper(args.pop('url'))
    if command == 'capture' and args['position'] is not None:
        args['position'] = dict(zip(('x', 'y', 'z'), args['position']))
    result = globals()[command](**args) if command in ('prepare', 'bundle') else globals()[command](helper, **args)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
