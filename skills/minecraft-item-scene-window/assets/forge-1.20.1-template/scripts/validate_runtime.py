"""Independently validate the scene-window audit; requires Pillow and NumPy."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image


def validate(run, built=None, deployed=None):
    data = json.loads((run / 'results.json').read_text(encoding='utf-8'))
    rows = {r['case']: r for r in data['results']}
    checks, details = {}, {}
    required = ['front','eye-left','eye-right','time-4','resolution-64',
                'resolution-512','reloaded','hand-right','hand-left','third-person']
    checks['completed'] = data['status'] == 'completed' and all(k in rows for k in required)
    checks['target_version'] = data['minecraft'] == '1.20.1' and data['forge'] == '47.4.10'
    checks['restored'] = all(data.get('restoration', {}).get(k) is True for k in ['inventory','selectedSlot','options'])
    checks['gpu_health'] = len(rows) == 10 and all(
        r['glError'] == 0 and r['stateMismatches'] == 0 and r['fboStatus'] == 36053
        and r['sceneError'] == '' for r in rows.values())
    checks['real_depth'] = all(r['geometryDepthPixels'] > 100 and r['depthBins'] > 8 for r in rows.values())
    if not all(k in rows for k in required):
        return {'passed': False, 'checks': checks, 'details': {'error':'Missing audit cases'}}
    checks['resource_reload'] = rows['reloaded']['shaderGeneration'] > rows['front']['shaderGeneration']
    checks['resolution_switch'] = rows['resolution-64']['resolution'] == 64 and rows['resolution-512']['resolution'] == 512
    # Counters are cumulative. Require NEW actual item-context calls for each case.
    for case, prev, context in [('hand-right','reloaded','FIRST_PERSON_RIGHT_HAND'),
                              ('hand-left','hand-right','FIRST_PERSON_LEFT_HAND'),
                              ('third-person','hand-left','THIRD_PERSON_RIGHT_HAND')]:
        delta = rows[case]['contexts'].get(context,0) - rows[prev]['contexts'].get(context,0)
        checks[case+'_drawn'] = delta > 0
        details[case+'_draws'] = delta
    def img(case, scene=True):
        return np.asarray(Image.open(run / (case + ('-scene' if scene else '') + '.png')).convert('RGB'))
    front, left, right, timed, reload = [img(k) for k in ['front','eye-left','eye-right','time-4','reloaded']]
    checks['reload_pixels'] = np.array_equal(front,reload)
    # Original observatory's magenta cube is distant.
    def magenta(a):
        r,g,b = [a[:,:,i].astype(float) for i in range(3)]
        return (r > 90) & (r > g*2) & (b > g*1.4) & (b > r*.35) & (b < r*.85)
    centers=[]
    for a in [left,front,right]:
        ys,xs=np.where(magenta(a)); centers.append(float(xs.mean()/a.shape[1]-.5) if len(xs) else None)
    details['magenta_cube_window_x'] = centers
    checks['parallax_direction'] = all(x is not None for x in centers) and centers[0] < centers[1] < centers[2]
    # Plane projection x'=(ez*x-ex*z)/(ez-z), cube centered at z=-3.
    expected_span = 1.2*3/5.5
    details['expected_center_span'] = expected_span
    details['observed_center_span'] = None if None in centers else centers[2]-centers[0]
    checks['parallax_magnitude'] = None not in centers and abs((centers[2]-centers[0])-expected_span) < .07
    # Bottom 10% of this scene is a stationary floor and perspective lines.
    # The gold gate is outside the central view, so cannot be used as a time control.
    band=max(1,front.shape[0]//10)
    checks['static_floor_stable'] = bool(np.array_equal(front[-band:],timed[-band:]))
    details['animated_pixels'] = int(np.any(front!=timed,axis=2).sum())
    checks['animation_visible'] = details['animated_pixels'] > 100
    for case,size in [('resolution-64',64),('resolution-512',512)]:
        resized=img(case)
        checks[case+'_pixels'] = resized.shape[:2] == (size,size)
        ys,xs=np.where(magenta(resized))
        centroid=float(xs.mean()/size-.5) if len(xs) else None
        details[case+'_normalized_cube_x']=centroid
        checks[case+'_composition'] = centroid is not None and abs(centroid-centers[1]) < .025
    # Preview footer contains an ordinary vanilla item/text; effect must not alter it.
    footer=img('front',False)[-75:]
    checks['vanilla_footer_stable'] = all(np.array_equal(footer,img(k,False)[-75:]) for k in required[1:7])
    if built is not None and deployed is not None:
        hashes=[hashlib.sha256(p.read_bytes()).hexdigest() for p in (built,deployed)]
        details['jar_sha256']=hashes[0]
        checks['deployed_hash']=hashes[0]==hashes[1]
    return {'passed': all(checks.values()), 'checks':checks, 'details':details,
            'manual_review_required':['mask edges / occlusion / left and right hand / third person'],
            'scope':'Miniature mesh scene; no full world rendering or performance certification'}


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('run',type=Path);p.add_argument('--built',type=Path);p.add_argument('--deployed',type=Path)
    args=p.parse_args();result=validate(args.run,args.built,args.deployed)
    (args.run/'independent-validation.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf8')
    print(json.dumps(result,indent=2,ensure_ascii=False));raise SystemExit(0 if result['passed'] else 1)
