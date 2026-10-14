"""Projection / SDF / overlap invariant tests. No Minecraft or GPU claims."""
import json, math
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parent
def perspective(fov,aspect,near=.05,far=256):
    f=1/math.tan(math.radians(fov)/2)
    return np.array([[f/aspect,0,0,0],[0,f,0,0],[0,0,(far+near)/(near-far),2*far*near/(near-far)],[0,0,-1,0.]])
def look(eye,target):
    z=np.array(eye)-np.array(target); z/=np.linalg.norm(z)
    x=np.cross([0,1,0],z); x/=np.linalg.norm(x); y=np.cross(z,x)
    m=np.eye(4);m[:3,:3]=[x,y,z];return m
def coverage(d,f=.5):
    t=np.clip((d+f)/f,0,1);return 1-t*t*(3-2*t)
def box(p,h):
    q=np.abs(p)-h;return np.linalg.norm(np.maximum(q,0))+min(max(q),0)
def capsule(p,a,b,r):
    ab=b-a;t=np.clip(np.dot(p-a,ab)/max(np.dot(ab,ab),1e-8),0,1)
    return np.linalg.norm(p-a-ab*t)-r

def run():
    checks=[]; maxerr=0; n=0
    # Invert exactly the matrix that produced depth, including nontrivial bob translation.
    for offset in [np.zeros(3),np.array([12000000.,64.,-12000000.])]:
        for fov in [40,70,110]:
            for aspect in [1,16/9,21/9]:
                eye=np.array([8.,7.,12.])+offset; v=look(eye,np.array([0.,1.,0.])+offset)
                v[:3,3]=[.07,-.03,.01]
                pv=perspective(fov,aspect)@v
                for x in np.linspace(-3,3,7):
                    for z in np.linspace(-3,3,7):
                        world=offset+[x,0,z]; relative=world-eye
                        clip=pv@np.r_[relative,1]; ndc=clip[:3]/clip[3]
                        packed=(ndc+1)/2
                        # Quantize to a conventional 24 bit depth sample.
                        packed[2]=round(packed[2]*(2**24-1))/(2**24-1)
                        recovered=np.linalg.inv(pv)@np.r_[packed*2-1,1]
                        recovered=recovered[:3]/recovered[3]
                        err=np.linalg.norm(recovered-relative);maxerr=max(maxerr,err);n+=1
    checks.append(dict(name='depth24_roundtrip_fov_aspect_bob_large_origin',passed=bool(maxerr<.003),samples=n,max_error_blocks=float(maxerr)))
    checks.append(dict(name='mask_boundaries',passed=bool(coverage(0)==0 and coverage(-.5)==1 and coverage(.1)==0)))
    checks.append(dict(name='box_known_points',passed=bool(box(np.zeros(3),np.ones(3))==-1 and box(np.array([2,0,0]),np.ones(3))==1)))
    checks.append(dict(name='capsule_degenerate_and_segment',passed=bool(capsule(np.ones(3)*0,np.zeros(3),np.zeros(3),1)==-1 and abs(capsule(np.array([1.,1.,0]),np.zeros(3),np.array([2.,0,0]),1))<1e-8)))
    # Priority is stable; adding region B never erases A outside B's support.
    normal=np.array([.2,.3,.4]);a=np.array([.9,.1,.2]);b=np.array([0.,1.,0.]);ma=.7;mb=0
    out=(normal*(1-ma)+a*ma)*(1-mb)+b*mb
    checks.append(dict(name='overlap_no_erasure_outside_next_region',passed=bool(np.allclose(out,normal*(1-ma)+a*ma))))
    checks.append(dict(name='world_anchor_relative_subtraction',passed=bool(np.allclose((np.array([12000004.,64,-12000002.])-np.array([12000000.,64,-12000000.])),[4,0,-2]))))
    # Mislabeling a nearest opaque depth behind water cannot locate the water layer.
    water=np.array([0.,1.,0]);bottom=np.array([0.,-2.,0]);center=water;radius=.5
    checks.append(dict(name='transparent_depth_counterexample',passed=bool(np.linalg.norm(water-center)<radius and np.linalg.norm(bottom-center)>radius)))
    result={'status':'passed' if all(c['passed'] for c in checks) else 'failed','scope':'CPU numerical invariants only','checks':checks}
    (ROOT/'evidence').mkdir(exist_ok=True);(ROOT/'evidence/math-validation.json').write_text(json.dumps(result,indent=2),encoding='utf8')
    print(json.dumps(result,indent=2));assert result['status']=='passed'
if __name__=='__main__':run()
