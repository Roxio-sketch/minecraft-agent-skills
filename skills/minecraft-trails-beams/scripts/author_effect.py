"""Create a self-contained Forge 1.20.1 project from an editable trail/beam recipe.
No network, launch, or deployment. Existing output directories are refused.
Python 3.10+; template already contains its white texture and Gradle wrapper.
"""
from __future__ import annotations
import argparse, json, math, pathlib, re, shutil

ROOT = pathlib.Path(__file__).resolve().parents[1]

def fields(o, names):
    if not isinstance(o, dict) or set(o) != set(names.split()):
        raise ValueError(f"Expected exactly: {names}; received {list(o) if isinstance(o,dict) else type(o).__name__}")

def number(o, key, lo, hi, integer=False):
    n=o[key]
    if isinstance(n,bool) or not isinstance(n,(int,float)) or not math.isfinite(n) or not lo<=n<=hi or (integer and int(n)!=n):
        raise ValueError(f"Invalid {key}; expected {'integer' if integer else 'number'} in [{lo}, {hi}]")

def validate(o):
    fields(o,'schema name section sides sampling layers')
    number(o,'schema',1,1,True);number(o,'sides',3,64,True)
    if not isinstance(o['name'],str) or not o['name'].strip():raise ValueError('name')
    if o['section'] not in ('ribbon','cross','tube','blade'):raise ValueError('section')
    s=o['sampling'];fields(s,'lifetime minDistance maxStep breakDistance maxPoints')
    for k,lo,hi in [('lifetime',.05,30),('minDistance',0,5),('maxStep',.001,10),('breakDistance',.001,10000)]:number(s,k,lo,hi)
    number(s,'maxPoints',2,4096,True)
    if not s['minDistance']<s['maxStep']<=s['breakDistance']:raise ValueError('sampling interval ordering')
    if not isinstance(o['layers'],list) or not 1<=len(o['layers'])<=16:raise ValueError('1..16 layers')
    for l in o['layers']:
        fields(l,'width blend profile texture repeat scroll gain fadePower taper colors')
        for k,lo,hi in [('width',.001,64),('repeat',0,100),('scroll',-100,100),('gain',0,16),('fadePower',.01,16)]:number(l,k,lo,hi)
        if l['blend'] not in ('alpha','additive') or l['profile'] not in ('soft','solid','double','dash','pulse','plasma','rune'):raise ValueError('blend/profile')
        if not isinstance(l['texture'],str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+',l['texture']) or '..' in l['texture']:raise ValueError('texture resource id')
        for kind in ('taper','colors'):
            stops=l[kind]
            if not isinstance(stops,list) or not 2<=len(stops)<=32:raise ValueError('2..32 curve stops')
            prev=-1
            for stop in stops:
                fields(stop,'at value' if kind=='taper' else 'at color alpha');number(stop,'at',0,1)
                if stop['at']<=prev:raise ValueError('stops must increase strictly')
                prev=stop['at']
                if kind=='taper':number(stop,'value',0,8)
                else:
                    number(stop,'alpha',0,1)
                    if not isinstance(stop['color'],str) or not re.fullmatch(r'#[0-9a-fA-F]{6}',stop['color']):raise ValueError('HEX color')
            if stops[0]['at']!=0 or stops[-1]['at']!=1:raise ValueError('curves must cover 0..1')
    return o

def validate_path(o):
    fields(o,'schema kind length amplitude cycles speed seed samples branches branchLength')
    number(o,'schema',1,1,True)
    if o['kind'] not in ('straight','arc','sine','helix','zigzag','lightning'):raise ValueError('path kind')
    for k,lo,hi in [('length',.1,32),('amplitude',0,8),('cycles',0,32),('speed',-20,20),('branchLength',.05,1)]:number(o,k,lo,hi)
    for k,lo,hi in [('seed',-1000000000,1000000000),('samples',8,256),('branches',0,4)]:number(o,k,lo,hi,True)
    return o

def create(recipe: pathlib.Path, output: pathlib.Path, path: pathlib.Path | None = None):
    o=validate(json.loads(recipe.read_text(encoding='utf-8')))
    template=ROOT/'assets/forge-1.20.1'
    path=path or template/'src/main/resources/assets/trailbeam/paths/custom.json'
    p=validate_path(json.loads(path.read_text(encoding='utf-8')))
    if output.exists():raise FileExistsError(f'Output already exists: {output}')
    shutil.copytree(template,output)
    # The custom lane retains its original internal resource name for compatibility.
    target=output/'src/main/resources/assets/trailbeam/recipes/helix.json'
    target.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (output/'design-recipe.json').write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    target=output/'src/main/resources/assets/trailbeam/paths/custom.json'
    target.write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (output/'design-path.json').write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    shutil.copytree(ROOT/'assets/workbench',output/'workbench')
    return output

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--recipe',type=pathlib.Path,required=True)
    p.add_argument('--path',type=pathlib.Path)
    p.add_argument('--out',type=pathlib.Path)
    p.add_argument('--validate-only',action='store_true')
    a=p.parse_args()
    validate(json.loads(a.recipe.read_text(encoding='utf-8')))
    if a.path:validate_path(json.loads(a.path.read_text(encoding='utf-8')))
    if a.validate_only:print('Recipe valid');return
    if a.out is None:p.error('--out required unless --validate-only')
    print(create(a.recipe,a.out,a.path).resolve())

if __name__=='__main__':main()
