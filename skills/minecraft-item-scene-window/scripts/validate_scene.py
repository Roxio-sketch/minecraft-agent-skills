"""Validate the fields actually consumed by the scene-window template."""
import argparse, json, math, re
from pathlib import Path

ID=re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')
ROOT={'version','background','frame','camera','eye','resolution','aperture','mask','nodes'}
NODE={'type','position','scale','rotation','spin','color','vertices','children'}

def finite(x):return type(x) in (int,float) and math.isfinite(x)
def vector(x,name):
    if not isinstance(x,list) or len(x)!=3 or not all(finite(v) for v in x):raise ValueError(name+' requires three finite numbers')

def validate(scene):
    if not isinstance(scene,dict):raise ValueError('Root must be an object')
    unknown=set(scene)-ROOT
    if unknown:raise ValueError('Unknown root fields: '+str(sorted(unknown)))
    if type(scene.get('version')) is not int or scene['version']!=1:raise ValueError('version must be 1')
    for key in ['background','frame','eye']:
        if key in scene:vector(scene[key],key)
    if scene.get('eye',[0,0,2.5])[2]<.1:raise ValueError('eye.z must be >= 0.1')
    if scene.get('camera','offaxis') not in ('offaxis','fixed','orbit'):raise ValueError('Unknown camera')
    resolution=scene.get('resolution',256)
    if type(resolution) is not int or not 64<=resolution<=1024:raise ValueError('resolution must be integer 64..1024')
    shape=scene.get('aperture','rectangle')
    if shape not in ('rectangle','circle','diamond','mask'):raise ValueError('Unknown aperture')
    if shape=='mask':
        mask=scene.get('mask','')
        if not ID.fullmatch(mask) or not mask.endswith('.png') or '..' in mask:raise ValueError('mask must be a PNG resource ID')
    elif 'mask' in scene:raise ValueError('mask is unused unless aperture=mask')
    count=triangles=0
    def nodes(items,depth):
        nonlocal count,triangles
        if not isinstance(items,list) or depth>32:raise ValueError('nodes/children must be arrays; depth <=32')
        for node in items:
            count+=1
            if count>4096:raise ValueError('Template budget: at most 4096 nodes')
            if not isinstance(node,dict) or set(node)-NODE:raise ValueError('Invalid node or unknown fields')
            kind=node.get('type','box')
            if kind not in ('box','mesh','group'):raise ValueError('Unknown node type')
            for key in ['position','scale','rotation','spin','color']:
                if key in node:vector(node[key],key)
            if kind=='mesh':
                v=node.get('vertices')
                if not isinstance(v,list) or len(v)%9 or not all(finite(x) for x in v):raise ValueError('vertices must be a flat triangle list')
                triangles+=len(v)//9
            elif 'vertices' in node:raise ValueError('vertices only applies to mesh')
            if kind=='box':triangles+=12
            if triangles>100000:raise ValueError('Template budget: at most 100000 triangles')
            nodes(node.get('children',[]),depth+1)
    nodes(scene.get('nodes'),0)
    return {'nodes':count,'triangles':triangles,'resolution':resolution,'camera':scene.get('camera','offaxis'),'aperture':shape}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('files',nargs='+',type=Path);args=p.parse_args()
    failed=False
    for path in args.files:
        try: print(json.dumps({'file':str(path),'status':'valid',**validate(json.loads(path.read_text(encoding='utf-8-sig')))},ensure_ascii=False))
        except (ValueError,TypeError,OSError) as e:print(json.dumps({'file':str(path),'status':'invalid','error':str(e)},ensure_ascii=False));failed=True
    raise SystemExit(int(failed))

if __name__=='__main__':main()
