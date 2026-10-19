#!/usr/bin/env python3
"""Read-only video sampling with timestamp evidence and labelled contact sheets.

Requires FFmpeg, FFprobe and Pillow. Output must be new or completely empty.
Uniform sampling includes both endpoints; --count 1 samples --start.
--times is an alternative to --start/--end/--count, in seconds from the first
decoded video frame. A request at the end of the video maps to the last frame.
No source or pre-existing output is overwritten, moved or deleted.
"""

from __future__ import annotations

import argparse
import bisect
import hashlib
import json
import math
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from fractions import Fraction
from pathlib import Path


def finite_seconds(value: str) -> float:
    try:
        number = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("time must be a finite number") from exc
    if not math.isfinite(number) or number < 0:
        raise argparse.ArgumentTypeError("time must be finite and non-negative")
    return number


def positive_int(value: str) -> int:
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be at least 1")
    return number


def executable(value: str) -> str:
    found = shutil.which(value)
    if found is None:
        raise ValueError(f"executable not found: {value}")
    return str(Path(found).resolve())


def run(command: list[str], *, timeout: int = 180) -> subprocess.CompletedProcess:
    completed = subprocess.run(
        command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        encoding="utf-8", errors="replace", timeout=timeout, check=False,
    )
    if completed.returncode:
        raise RuntimeError(
            f"command failed ({completed.returncode}): {command!r}\n"
            f"{completed.stderr[-8000:]}"
        )
    return completed


def optional_float(value) -> float | None:
    try:
        number = float(value)
    except (ValueError, TypeError):
        return None
    return number if math.isfinite(number) else None


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json_new(path: Path, data) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")


def make_sheets(out: Path, frames: list[dict], width: int) -> list[str]:
    from PIL import Image, ImageDraw, ImageFont

    cell_width = min(width, 480)
    padding, label_height, columns = 12, 36, 3
    font = ImageFont.load_default()
    sheets = []
    for offset in range(0, len(frames), 12):
        batch = frames[offset:offset + 12]
        thumbs = []
        for frame in batch:
            with Image.open(out / frame["file"]) as picture:
                picture = picture.convert("RGB")
                height = max(1, round(picture.height * cell_width / picture.width))
                thumbs.append(picture.resize((cell_width, height), Image.Resampling.LANCZOS))
        cell_height = max(picture.height for picture in thumbs) + label_height
        rows = math.ceil(len(batch) / columns)
        canvas = Image.new(
            "RGB", (columns * (cell_width + padding) + padding,
                    rows * (cell_height + padding) + padding), (20, 22, 28),
        )
        draw = ImageDraw.Draw(canvas)
        for index, (frame, thumb) in enumerate(zip(batch, thumbs)):
            x = padding + index % columns * (cell_width + padding)
            y = padding + index // columns * (cell_height + padding)
            canvas.paste(thumb, (x, y))
            draw.text(
                (x + 4, y + cell_height - label_height + 4),
                f'#{frame["index"]:03d} requested {frame["requested_time_s"]:.3f}s'
                f' | frame {frame["actual_time_s"]:.3f}s',
                fill=(245, 245, 245), font=font,
            )
        name = f"contact-sheet-{len(sheets) + 1:02d}.jpg"
        path = out / name
        # Exclusive creation preserves the same no-overwrite contract as FFmpeg -n.
        with path.open("xb") as stream:
            canvas.save(stream, format="JPEG", quality=91)
        sheets.append(name)
    return sheets


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--ffprobe", default="ffprobe")
    parser.add_argument("--start", type=finite_seconds, default=None)
    parser.add_argument("--end", type=finite_seconds, default=None)
    parser.add_argument("--count", type=positive_int, default=None)
    parser.add_argument("--width", type=positive_int, default=960)
    parser.add_argument("--times", help="comma-separated seconds; excludes start/end/count")
    args = parser.parse_args()
    try:
        # Check the dependency before creating any outputs.
        import PIL  # noqa: F401

        source, out = args.input.resolve(strict=True), args.out.resolve()
        if not source.is_file():
            raise ValueError("input must be a regular file")
        if source == out or source in out.parents:
            raise ValueError("output must not be inside the source file path")
        if out.exists() and (not out.is_dir() or any(out.iterdir())):
            raise ValueError("output directory already exists and is not empty")
        if args.width > 8192:
            raise ValueError("width must be between 1 and 8192")
        ffmpeg, ffprobe = executable(args.ffmpeg), executable(args.ffprobe)
        if args.times is not None and any(
            value is not None for value in (args.start, args.end, args.count)
        ):
            raise ValueError("--times cannot be combined with --start/--end/--count")
        probe = json.loads(run([
            ffprobe, "-v", "error", "-show_streams", "-show_format", "-of", "json", str(source),
        ]).stdout)
        videos = [stream for stream in probe.get("streams", [])
                  if stream.get("codec_type") == "video"
                  and not stream.get("disposition", {}).get("attached_pic", 0)]
        if not videos:
            raise ValueError("no decodable video stream in input")
        video = videos[0]
        # Full frame timestamps avoid frame-count caps and an invalid EOF seek.
        # They also handle VFR sources without inventing a constant frame rate.
        frame_probe = json.loads(run([
            ffprobe, "-v", "error", "-select_streams", str(video["index"]),
            "-show_frames", "-show_entries", "frame=best_effort_timestamp_time,duration_time",
            "-of", "json", str(source),
        ]).stdout)
        frame_rows = frame_probe.get("frames", [])
        pts = sorted({value for frame in frame_rows
                      if (value := optional_float(frame.get("best_effort_timestamp_time")))
                      is not None})
        if not pts:
            raise ValueError("video has no frames with usable timestamps")
        origin = pts[0]
        relative_pts = [value - origin for value in pts]
        stream_start = optional_float(video.get("start_time"))
        stream_duration = optional_float(video.get("duration"))
        format_duration = optional_float(probe.get("format", {}).get("duration"))
        duration = None
        if stream_duration is not None:
            duration = stream_duration + (stream_start or 0.0) - origin
        if duration is None or duration <= 0:
            duration = format_duration
        if duration is None or duration <= 0 or duration < relative_pts[-1] - 0.001:
            raise ValueError("ffprobe did not report a valid video duration")
        if args.times is not None:
            try:
                requested = [finite_seconds(value.strip()) for value in args.times.split(",")]
            except argparse.ArgumentTypeError as exc:
                raise ValueError(str(exc)) from exc
            mode = "explicit_times"
        else:
            start = 0.0 if args.start is None else args.start
            end = duration if args.end is None else args.end
            count = 24 if args.count is None else args.count
            if start > end:
                raise ValueError("start must not exceed end")
            requested = ([start] if count == 1 else
                         [start + (end - start) * index / (count - 1)
                          for index in range(count)])
            mode = "uniform_inclusive"
        if not requested or any(value < 0 or value > duration + 1e-8 for value in requested):
            raise ValueError(f"every requested time must be within [0, {duration:.9f}] seconds")
        # Resolve every parameter before mutating the output directory.
        source_stat = source.stat()
        source_hash = sha256(source)
        out.mkdir(parents=True, exist_ok=True)
        write_json_new(out / "source-probe.json", probe)
        manifest = {
            "schema": "video-samples/1", "status": "in_progress",
            "created_utc": datetime.now(timezone.utc).isoformat(),
            "source": {"path": str(source), "size_bytes": source_stat.st_size,
                       "mtime_ns": source_stat.st_mtime_ns, "sha256": source_hash},
            "video": {"stream_index": video["index"], "duration_s": duration,
                      "first_frame_pts_s": origin, "last_frame_time_s": relative_pts[-1],
                      "timestamped_frame_count": len(pts), "width": video.get("width"),
                      "height": video.get("height"), "avg_frame_rate": video.get("avg_frame_rate")},
            "sampling": {"mode": mode, "width": args.width,
                         "requested_times_s": requested,
                         "time_origin": "first decoded video frame",
                         "selection": "first frame at or after request; EOF maps to last frame",
                         "count_one": "start", "explicit_order_preserved": True},
            "tools": {"ffmpeg": ffmpeg, "ffprobe": ffprobe},
            "frames": [], "contact_sheets": [],
        }
        # Keep a durable intent record even if a subsequent extraction is interrupted.
        write_json_new(out / "sampling-intent.json", manifest)
        for index, requested_time in enumerate(requested, 1):
            chosen = min(bisect.bisect_left(relative_pts, requested_time), len(pts) - 1)
            absolute_pts = pts[chosen]
            # FFmpeg rounds seek positions to microseconds. Seek half a microsecond
            # early so the requested frame is retained at a rounding boundary.
            seek_pts = absolute_pts - 0.0000005
            name = f"frame-{index:03d}.jpg"
            result = run([
                ffmpeg, "-hide_banner", "-nostdin", "-loglevel", "info", "-n", "-copyts",
                "-seek_timestamp", "1", "-ss", f"{seek_pts:.9f}", "-i", str(source),
                "-map", f'0:{video["index"]}', "-an", "-sn", "-dn",
                "-vf", f"scale={args.width}:-2,showinfo", "-frames:v", "1",
                "-fps_mode", "passthrough", "-q:v", "2", str(out / name),
            ])
            found = re.search(r"\bn:\s*0\s+pts:\s*([-\d]+)\s+pts_time:([-+\deE.]+)", result.stderr)
            if found is None or not (out / name).is_file() or not (out / name).stat().st_size:
                raise RuntimeError(f"frame {index}: no verified decoded output")
            filter_time_base = re.search(
                r"config in time_base:\s*(\d+)/(\d+)", result.stderr
            )
            time_base = (Fraction(int(filter_time_base.group(1)), int(filter_time_base.group(2)))
                         if filter_time_base else Fraction(video["time_base"]))
            # pts_time is rounded in FFmpeg logs, especially for long recordings.
            # The integer PTS and rational time base retain the exact timestamp.
            actual_pts_integer = int(found.group(1))
            actual_pts = float(actual_pts_integer * time_base)
            if not math.isfinite(actual_pts) or abs(actual_pts - absolute_pts) > 0.002:
                raise RuntimeError(
                    f"frame {index}: decoded PTS {actual_pts} differs from target {absolute_pts}"
                )
            frame = {"index": index, "file": name, "requested_time_s": requested_time,
                     "selected_source_frame_index": chosen, "selected_frame_pts_s": absolute_pts,
                     "input_seek_pts_s": seek_pts, "actual_pts_s": actual_pts,
                     "actual_pts_integer": actual_pts_integer,
                     "decoded_time_base": str(time_base),
                     "actual_time_s": actual_pts - origin,
                     "endpoint_clamped_to_last_frame": requested_time > relative_pts[-1]}
            manifest["frames"].append(frame)
            print(f"[{index}/{len(requested)}] requested={requested_time:.6f}s "
                  f"decoded={frame['actual_time_s']:.6f}s", flush=True)
        manifest["contact_sheets"] = make_sheets(out, manifest["frames"], args.width)
        # Detect source changes without modifying or deleting anything.
        end_stat = source.stat()
        if (end_stat.st_size, end_stat.st_mtime_ns) != (source_stat.st_size, source_stat.st_mtime_ns):
            raise RuntimeError("source changed while sampling; outputs retained for inspection")
        manifest["status"] = "complete"
        manifest["completed_utc"] = datetime.now(timezone.utc).isoformat()
        write_json_new(out / "manifest.json", manifest)
        print(f"Complete: {out / 'manifest.json'}", flush=True)
        return 0
    except (ValueError, OSError, RuntimeError, subprocess.TimeoutExpired, ImportError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
