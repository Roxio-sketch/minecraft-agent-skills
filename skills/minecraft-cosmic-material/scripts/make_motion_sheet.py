"""Assemble fixed-time motion frames into a labelled animated WebP and a filmstrip."""
from pathlib import Path
import argparse
import json
from PIL import Image, ImageDraw, ImageFont


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--font", type=Path)
    parser.add_argument("--all", action="store_true", help="Include every recorded material in the filmstrip.")
    args = parser.parse_args()
    record = json.loads((args.directory / "motion.json").read_text(encoding="utf-8"))
    candidates = [args.font, Path("C:/Windows/Fonts/msyh.ttc"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")]
    font_path = next((p for p in candidates if p and p.is_file()), None)
    font = ImageFont.truetype(str(font_path), 16) if font_path else ImageFont.load_default()
    frames = []
    for number in range(record["frames"]):
        canvas = Image.new("RGB", (960, ((len(record["records"]) + 2) // 3) * 248), "#121717")
        draw = ImageDraw.Draw(canvas)
        for i, material in enumerate(record["records"]):
            x, y = i % 3 * 320, i // 3 * 248
            with Image.open(args.directory / material["id"] / f"{number:04d}.png") as source:
                canvas.paste(source, (x, y))
            draw.text((x + 9, y + 220), material["name"], font=font, fill="#dcece1")
        frames.append(canvas)
    frames[0].save(args.directory / "motion-sheet.webp", save_all=True, append_images=frames[1:],
                   duration=1000 // record["fps"], loop=0, quality=85, method=4)
    # Temporal contact sheet makes changes inspectable without relying on playback.
    selected = (record["records"] if args.all or len(record["records"]) <= 3 else
                [record["records"][1], record["records"][2], record["records"][-1]])
    strip = Image.new("RGB", (960, len(selected) * 248), "#121717")
    draw = ImageDraw.Draw(strip)
    for row, material in enumerate(selected):
        for col, number in enumerate([0, 40, 80]):
            with Image.open(args.directory / material["id"] / f"{number:04d}.png") as source:
                strip.paste(source, (col * 320, row * 248))
            draw.text((col * 320 + 8, row * 248 + 220), f'{material["name"]} / {number / record["fps"]:.1f}s', font=font, fill="#dcece1")
    strip.save(args.directory / "motion-filmstrip.png")
    print(args.directory / "motion-sheet.webp")


if __name__ == "__main__":
    main()
