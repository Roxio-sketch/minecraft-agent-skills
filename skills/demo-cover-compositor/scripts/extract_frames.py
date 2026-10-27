"""Extract explicit video times, retaining source frames and a contact sheet."""
import argparse
import json
import math
import subprocess
from pathlib import Path


def run(args):
    result = subprocess.run(args, capture_output=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(result.stderr[-3000:])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--times', required=True, type=float, nargs='+')
    parser.add_argument('--output-dir', required=True, type=Path)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--columns', type=int, default=4)
    args = parser.parse_args()
    source = args.input.resolve(strict=True)
    target = args.output_dir.resolve()
    if target.exists() and any(target.iterdir()):
        raise FileExistsError('Use a new or empty output directory.')
    if args.columns < 1 or any(t < 0 or not math.isfinite(t) for t in args.times):
        raise ValueError('Times must be finite and nonnegative; columns must be positive.')
    target.mkdir(parents=True, exist_ok=True)
    frames = []
    for index, seconds in enumerate(args.times):
        raw = target / f'source-{index:03d}.png'
        preview = target / f'frame-{index:03d}.png'
        run([args.ffmpeg, '-v', 'error', '-ss', str(seconds), '-i', str(source), '-frames:v', '1', '-y', str(raw)])
        if not raw.is_file():
            raise ValueError(f'No frame at {seconds} seconds; check the clip duration.')
        run([args.ffmpeg, '-v', 'error', '-i', str(raw), '-vf', 'scale=480:270:force_original_aspect_ratio=decrease,pad=480:270:(ow-iw)/2:(oh-ih)/2', '-frames:v', '1', '-y', str(preview)])
        frames.append({'index':index, 'seconds':seconds, 'sourceFrame':str(raw)})
    columns = min(args.columns, len(frames))
    rows = math.ceil(len(frames) / columns)
    sheet = target / 'contact-sheet.jpg'
    run([args.ffmpeg, '-v', 'error', '-framerate', '1', '-i', str(target/'frame-%03d.png'),
         '-vf', f'tile={columns}x{rows}:nb_frames={len(frames)}:padding=4:margin=4', '-frames:v', '1', '-q:v', '2', '-y', str(sheet)])
    report = {'input':str(source), 'frames':frames, 'contactSheet':str(sheet)}
    (target/'frames.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
