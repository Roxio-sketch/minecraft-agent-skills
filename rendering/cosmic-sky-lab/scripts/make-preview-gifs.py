"""Build compact animations from actual fixed-time GPU frame captures."""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def main():
    output = ROOT / 'evidence' / 'previews'
    output.mkdir(exist_ok=True)
    records = []
    for source, title in (('milkyway', '星云与星辰动态'), ('blackhole', '动态黑洞与巨行星')):
        folder = ROOT / 'evidence' / 'browser' / f'{source}-frames'
        paths = sorted(folder.glob('*.png'))
        if len(paths) != 33:
            raise ValueError(f'{source}: expected 33 real captures, got {len(paths)}')
        frames = [Image.open(p).convert('RGB') for p in paths]
        size = frames[0].size
        if size != (640, 360) or any(f.size != size for f in frames):
            raise ValueError('Unexpected frame size')
        # A shared palette avoids palette changes creating false frame-to-frame flicker.
        atlas = Image.new('RGB', (size[0]*4, size[1]*2))
        for i, frame in enumerate(frames[::4][:8]):
            atlas.paste(frame, ((i%4)*size[0], (i//4)*size[1]))
        palette = atlas.quantize(colors=256, method=Image.Quantize.MEDIANCUT)
        quantized = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
        target = output / f'{title}.gif'
        quantized[0].save(target, save_all=True, append_images=quantized[1:],
                          duration=250, loop=0, disposal=2, optimize=False)
        with Image.open(target) as check:
            if check.n_frames != 33 or check.size != size:
                raise ValueError('GIF output lost frames or size')
        records.append({'source': source, 'file': target.name, 'frames': 33,
                        'shaderTimeSeconds': [0, 8], 'timeStep': 0.25,
                        'frameDurationMs': 250, 'resolution': list(size),
                        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                        'sourceHashes': [hashlib.sha256(p.read_bytes()).hexdigest() for p in paths]})
    (output/'manifest.json').write_text(json.dumps(records, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps([{'file': r['file'], 'frames': r['frames']} for r in records], ensure_ascii=False))


if __name__ == '__main__':
    main()
