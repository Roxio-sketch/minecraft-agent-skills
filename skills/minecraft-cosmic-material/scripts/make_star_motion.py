"""Package the star isolation clips without discarding the full-size source frames."""
from pathlib import Path
import argparse
import json
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    record = json.loads((args.directory / "motion.json").read_text(encoding="utf-8"))
    columns = []
    for case in record["records"]:
        frames = []
        for number in range(record["frames"]):
            with Image.open(args.directory / case["id"] / f"{number:04d}.png") as source:
                frames.append(source.convert("RGB"))
        frames[0].save(args.directory / f'{case["id"]}.webp', save_all=True,
                       append_images=frames[1:], duration=1000 // record["fps"],
                       quality=88, loop=0, method=4)
        columns.append((case, frames))
    comparison = []
    for number in range(record["frames"]):
        canvas = Image.new("RGB", (640 * len(columns), 464), "#111717")
        draw = ImageDraw.Draw(canvas)
        for col, (case, frames) in enumerate(columns):
            canvas.paste(frames[number].resize((640, 432), Image.Resampling.LANCZOS), (col * 640, 0))
            draw.text((col * 640 + 12, 443), case["name"], fill="#dcece1")
        comparison.append(canvas)
    comparison[0].save(args.directory / "comparison.webp", save_all=True,
                       append_images=comparison[1:], duration=1000 // record["fps"],
                       quality=88, loop=0, method=4)
    sheet = Image.new("RGB", (2400, len(columns) * 568), "#111717")
    draw = ImageDraw.Draw(sheet)
    for row, (case, frames) in enumerate(columns):
        for col, number in enumerate([0, 40, 80]):
            sheet.paste(frames[number], (col * 800, row * 568))
            draw.text((col * 800 + 12, row * 568 + 548), f'{case["name"]} / {number / record["fps"]:.1f}s', fill="#dcece1")
    sheet.save(args.directory / "filmstrip.png")
    print(args.directory / "comparison.webp")


if __name__ == "__main__":
    main()
