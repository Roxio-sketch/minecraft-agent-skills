"""Render an existing Remotion still plus JPEG and list-sized visual preview."""
import argparse
import json
import subprocess
from pathlib import Path


def run(args, cwd=None):
    result = subprocess.run(args, cwd=cwd, capture_output=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError((result.stderr + result.stdout)[-5000:])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True, type=Path)
    parser.add_argument('--entry')
    parser.add_argument('--composition', default='SkillCover')
    parser.add_argument('--props', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--node', default='node')
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--overwrite', action='store_true')
    args = parser.parse_args()
    project = args.project.resolve(strict=True)
    props_path = args.props if args.props.is_absolute() else project / args.props
    props_path = props_path.resolve(strict=True)
    props = json.loads(props_path.read_text(encoding='utf-8-sig'))
    if not props.get('title') or not props.get('shots'):
        raise ValueError('Provide a title and actual source shots.')
    if props.get('mainSize', 252) <= props.get('secondarySize', 136):
        raise ValueError('Main subject text must be larger than the subtitle.')
    if props.get('secondaryTop', 655) < props.get('mainTop', 310) + props.get('mainSize', 252) * 1.12 + 24:
        raise ValueError('Leave a clear gap between the two text lines.')
    public = (project / 'public').resolve()
    for shot in props['shots']:
        source = (public / shot['src']).resolve(strict=True)
        if not source.is_relative_to(public):
            raise ValueError(f'Source outside public: {source}')
        for key in ('box', 'image'):
            values = shot[key]
            if len(values) != 4 or any(not isinstance(v, (int, float)) for v in values) or min(values[2:]) <= 0:
                raise ValueError(f'Invalid {key}: {values}')
    output = args.output.resolve()
    png, jpg = output.with_suffix('.png'), output.with_suffix('.jpg')
    thumb = output.with_name(output.stem + '-thumbnail').with_suffix('.jpg')
    for target in (png, jpg, thumb):
        if target.exists() and not args.overwrite:
            raise FileExistsError(f'Output exists; use --overwrite for an authorized update: {target}')
    cli = project / 'node_modules/@remotion/cli/remotion-cli.js'
    if not cli.is_file():
        raise FileNotFoundError(f'Remotion CLI not installed: {cli}')
    png.parent.mkdir(parents=True, exist_ok=True)
    command = [args.node, str(cli), 'still']
    if args.entry:
        entry = (project / args.entry).resolve(strict=True)
        command.append(str(entry))
    command += [args.composition, str(png), '--image-format=png', f'--props={props_path}']
    run(command, project)
    run([args.ffmpeg, '-v', 'error', '-i', str(png), '-q:v', '2', '-y', str(jpg)])
    run([args.ffmpeg, '-v', 'error', '-i', str(png), '-vf', 'scale=320:180', '-q:v', '2', '-y', str(thumb)])
    print(json.dumps({'png':str(png), 'jpg':str(jpg), 'thumbnail':str(thumb)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
