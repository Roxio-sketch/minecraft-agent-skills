"""Independent PNG silhouette check for ground-hit preview placement."""
import argparse
import json
from pathlib import Path
import shutil
import numpy as np
from PIL import Image
import sphere_oracle as motion

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=root / 'run/black-hole-lab/world-probes')
parser.add_argument('--out', type=Path, default=root / 'evidence/ground')
args = parser.parse_args()
source, out = args.source, args.out
out.mkdir(parents=True, exist_ok=True)
results = []
for path in sorted(source.glob('ground-motion-*-fixture.json')):
    name = path.name.removesuffix('-fixture.json')
    f = motion.read_json(path)
    p = motion.read_json(source / (name+'.json'))
    assert f['centerWasFixed']
    motion.CENTER = motion.vec(f['fixedCenter'])
    motion.RADIUS = f['radius']
    assert np.allclose(motion.CENTER, motion.vec(f['expectedHit'])+[0,f['radius']+.05,0])
    before = np.array(Image.open(source/(name+'-before.png')).convert('RGB'))
    field = np.array(Image.open(source/(name+'-field.png')).convert('RGB'))
    expected, _, _ = motion.predicted_sphere(f, field.shape[1], field.shape[0])
    delta = np.abs(field.astype(int)-before.astype(int)).max(axis=2)
    actual = motion.keep_components((field.max(axis=2)<=3)&(delta>=8))
    a, e = motion.boundary(actual), motion.boundary(expected)
    error = max(motion.nearest_distances(np.argwhere(a),np.argwhere(e)).max(),
                motion.nearest_distances(np.argwhere(e),np.argwhere(a)).max())
    assert error <= 1.5, (name,error)
    assert p['glError']==p['maxDepthDifference']==0
    results.append(dict(name=name, maximumContourErrorPixels=float(error),actualCorePixels=int(actual.sum())))
    for suffix in ('-fixture.json','.json','-before.png','-field.png','-after.png'):
        shutil.copy2(source/(name+suffix),out/(name+suffix))
assert len(results)==6
(out/'runtime.json').write_text(json.dumps(dict(status='passed',cases=results),indent=2),encoding='utf-8')
print(json.dumps(results))
