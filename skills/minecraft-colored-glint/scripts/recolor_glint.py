#!/usr/bin/env python3
"""Recolor glint PNGs while preserving dimensions, alpha and optional metadata."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path

from PIL import Image


def parse_color(value: str) -> tuple[str, tuple[int, int, int]]:
    match = re.fullmatch(r"([a-z0-9][a-z0-9_-]*)=#?([0-9a-fA-F]{6})", value)
    if not match:
        raise argparse.ArgumentTypeError("Use a resource-safe name and RGB, e.g. ice=#42CFFF")
    name, rgb = match.groups()
    if len(name) > 64:
        raise argparse.ArgumentTypeError("Color name must be at most 64 characters")
    return name, tuple(int(rgb[i:i + 2], 16) for i in (0, 2, 4))


def parse_strength(value: str) -> float:
    try:
        strength = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("strength must be a number") from exc
    if not math.isfinite(strength) or not 0 <= strength <= 4:
        raise argparse.ArgumentTypeError("strength must be finite and between 0 and 4")
    return strength


def recolor(image: Image.Image, rgb: tuple[int, int, int], strength: float) -> Image.Image:
    rgba = image.convert("RGBA")
    result = Image.new("RGBA", rgba.size)
    pixels = []
    source_pixels = rgba.load()
    for y in range(rgba.height):
        for x in range(rgba.width):
            r, g, b, alpha = source_pixels[x, y]
            intensity = max(r, g, b) / 255.0
            channels = tuple(min(255, max(0, round(intensity * c * strength))) for c in rgb)
            pixels.append((*channels, alpha))
    result.putdata(pixels)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--color", type=parse_color, action="append", required=True)
    parser.add_argument("--strength", type=parse_strength, default=1.0)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    names = [name for name, _ in args.color]
    if len(set(names)) != len(names):
        parser.error("Color names must be unique")

    try:
        source = args.source.resolve(strict=True)
        with Image.open(source) as image:
            if image.format != "PNG":
                parser.error("Source must be PNG")
            if getattr(image, "n_frames", 1) != 1:
                parser.error("APNG is not supported; use a PNG sprite sheet with .mcmeta")
            rgba = image.convert("RGBA")
        source_meta = Path(str(source) + ".mcmeta")
        metadata = source_meta.read_bytes() if source_meta.is_file() else None
        if metadata is not None and not isinstance(json.loads(metadata), dict):
            parser.error("Source .mcmeta must contain a JSON object")

        output_dir = args.output_dir.resolve()
        if output_dir.exists() and not output_dir.is_dir():
            parser.error("Output directory is not a directory")
        outputs = [(output_dir / f"glint_item_{name}.png", rgb) for name, rgb in args.color]
        for output, _ in outputs:
            sidecar = Path(str(output) + ".mcmeta")
            same_input = output.exists() and output.samefile(source)
            if same_input or output.resolve() == source or sidecar.resolve() == source_meta.resolve():
                parser.error("Cannot overwrite source PNG or source metadata")
            for target in (output, sidecar):
                if target.is_symlink() or (target.exists() and (not args.overwrite or not target.is_file())):
                    parser.error(f"Output exists or is unsafe to replace: {target}")
            if metadata is None and sidecar.exists():
                parser.error(f"Existing metadata would become stale; resolve it first: {sidecar}")

        output_dir.mkdir(parents=True, exist_ok=True)
        for output, rgb in outputs:
            result = recolor(rgba, rgb, args.strength)
            # Exclusive creation is the default even if another process raced preflight.
            with output.open("wb" if args.overwrite else "xb") as handle:
                result.save(handle, format="PNG")
            if metadata is not None:
                with Path(str(output) + ".mcmeta").open("wb" if args.overwrite else "xb") as handle:
                    handle.write(metadata)
            print(output)
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
