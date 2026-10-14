import json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parent
RANGES={
 'attachment':(0,2),'weaponRadius':(.01,.6),'weaponWidth':(.02,.5),'weaponTurns':(.25,5),'weaponStrength':(0,3),
 'radius':(.15,1.8),'height':(.2,3.5),'turns':(.25,5),'strands':(1,8),'width':(.04,.65),
 'rotationSpeed':(-2,2),'riseSpeed':(0,3),'waveAmplitude':(0,.3),'waveSpeed':(0,3),'turbulence':(0,2),
 'tongueLength':(.1,2),'erosion':(0,1),'intensity':(.2,4),'opacity':(0,1),'emberRate':(0,160),
 'emberLife':(.15,3),'emberSize':(.005,.1),'wispRate':(0,80),'wispLife':(.15,2),'wispSize':(.05,.6),
 'outwardSpeed':(0,2),'lift':(0,2.5),'motionInheritance':(0,1),'seed':(0,1000000),'segments':(24,512),
 'rampTime':(.05,2),'firstPerson':(0,1)}
COLORS={'coreColor','midColor','edgeColor'}
KEYS=set(RANGES)|COLORS|{'schema'}
def validate(value):
 if isinstance(value,dict) and value.get('schema')=='fire-coil/1':
  additions={'attachment':0,'weaponRadius':.022,'weaponWidth':.115,'weaponTurns':.88,'weaponStrength':1.35}
  if set(value)!=KEYS-set(additions):raise ValueError('旧版配方需要精确31个字段。')
  value={**additions,**value,'schema':'fire-coil/2'}
 if not isinstance(value,dict) or set(value)!=KEYS: raise ValueError('配方需要精确36个字段。')
 if value['schema']!='fire-coil/2':raise ValueError('配方版本必须为fire-coil/2。')
 result=dict(value)
 for k,(lo,hi) in RANGES.items():
  v=value[k]
  if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or not lo<=v<=hi:raise ValueError(f'{k}越界或不是有限数值。')
  if k in {'strands','seed','segments','attachment'} and v!=int(v):raise ValueError(f'{k}必须为整数。')
 for k in COLORS:
  v=value[k]
  if not isinstance(v,str) or len(v)!=7 or v[0]!='#' or any(c not in '0123456789abcdefABCDEF' for c in v[1:]):raise ValueError(f'{k}必须为#RRGGBB。')
  result[k]=v.lower()
 return result
def loads(text):
 def bad(value):raise ValueError(f'拒绝非有限常量{value}')
 return validate(json.loads(text,parse_constant=bad))
