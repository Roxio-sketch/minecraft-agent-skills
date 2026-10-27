"""Probe one delivered video and optionally decode the complete audio/video."""
import argparse
import json
import math
import os
import subprocess
from fractions import Fraction
from pathlib import Path


def run(args):
    return subprocess.run(args, capture_output=True, encoding='utf-8', errors='replace')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--video', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    parser.add_argument('--width', default=1920, type=int)
    parser.add_argument('--height', default=1080, type=int)
    parser.add_argument('--fps', default=30, type=float)
    parser.add_argument('--duration', type=float)
    parser.add_argument('--tolerance', default=0.1, type=float)
    parser.add_argument('--require-audio', action='store_true')
    parser.add_argument('--decode', action='store_true')
    parser.add_argument('--ffprobe', default='ffprobe')
    parser.add_argument('--ffmpeg', default='ffmpeg')
    args = parser.parse_args()
    video_path = args.video.resolve(strict=True)
    probe = run([args.ffprobe, '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(video_path)])
    if probe.returncode:
        raise RuntimeError(probe.stderr[-3000:])
    data = json.loads(probe.stdout)
    videos = [s for s in data['streams'] if s['codec_type'] == 'video']
    audio = [s for s in data['streams'] if s['codec_type'] == 'audio']
    issues = []
    if not videos:
        issues.append('No video stream')
    else:
        stream = videos[0]
        fps = float(Fraction(stream.get('avg_frame_rate') or stream['r_frame_rate']))
        if (stream['width'], stream['height']) != (args.width, args.height):
            issues.append(f"Unexpected resolution {stream['width']}x{stream['height']}")
        if abs(fps - args.fps) > 0.01:
            issues.append(f'Unexpected fps {fps}')
    duration = float(data['format'].get('duration', 'nan'))
    if not math.isfinite(duration) or duration <= 0:
        issues.append('Invalid duration')
    elif args.duration is not None and abs(duration - args.duration) > args.tolerance:
        issues.append(f'Unexpected duration {duration}; expected {args.duration}')
    if args.require_audio and not audio:
        issues.append('Required audio stream missing')
    decode_status = 'not_requested'
    if args.decode:
        decoded = run([args.ffmpeg, '-v', 'error', '-i', str(video_path), '-map', '0:v:0?', '-map', '0:a:0?', '-f', 'null', os.devnull])
        decode_status = 'passed' if decoded.returncode == 0 and not decoded.stderr.strip() else 'failed'
        if decode_status == 'failed':
            issues.append('Full decode failed: ' + decoded.stderr[-3000:])
    report = {'video':str(video_path), 'bytes':video_path.stat().st_size, 'duration':duration,
              'videoStreams':videos, 'audioStreams':audio, 'fullDecode':decode_status,
              'issues':issues, 'passed':not issues,
              'scope':'Technical media checks only; feature coverage, visual quality and speech need review.'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'passed':not issues, 'duration':duration, 'issues':issues, 'report':str(args.report.resolve())}, ensure_ascii=False))
    raise SystemExit(1 if issues else 0)


if __name__ == '__main__':
    main()
