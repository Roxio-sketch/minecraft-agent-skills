"""Measure ground arcs below the main bolt in deterministic runtime images."""
import json, shutil
from pathlib import Path
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parents[1]
src=root/'run/lightning-lab/game-probes'
out=root/'evidence/horizontal-presets-20261005/impact'
out.mkdir(parents=True,exist_ok=True)
report=[]
for name,tip in [('impact-end-cyan','cyan'),('impact-ratio100-red','red'),('impact-gradient-off-white','white')]:
    p=json.loads((src/(name+'.json')).read_text(encoding='utf-8-sig'))
    assert p['haloGlError']==0 and p['visibleImpactSegments']>100
    a=np.array(Image.open(src/(name+'.png')).convert('RGB')).astype(int)
    # Ground-only region below the strike junction (image row 250).
    roi=a[255:320,330:520]
    r,g,b=roi[:,:,0],roi[:,:,1],roi[:,:,2]
    if tip=='cyan': good=(g>110)&(b>110)&(g-r>50)&(b-r>50)
    elif tip=='red': good=(r>110)&(r-g>50)&(r-b>50)
    else: good=(r>110)&(g>110)&(b>110)&(roi.max(axis=2)-roi.min(axis=2)<10)
    assert good.sum()>100,(name,int(good.sum()))
    report.append(dict(name=name,groundColor=tip,groundPixels=int(good.sum()),region=[330,255,520,320]))
    for ext in ('.json','.png'): shutil.copy2(src/(name+ext),out/(name+ext))
(out/'runtime.json').write_text(json.dumps(dict(status='passed',cases=report),indent=2),encoding='utf-8')
print(json.dumps(report))
