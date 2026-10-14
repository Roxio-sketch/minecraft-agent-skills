import argparse,json,math
from pathlib import Path
FIELDS=['color', 'accent', 'rate', 'lifetime', 'capacity', 'size', 'speed', 'drag', 'gravity', 'orbit', 'seed', 'motion']
RANGES={'radius':(.1,128),'intensity':(0,16),'width':(.1,16),'passes':(1,8),'rate':(0,4096),'lifetime':(.05,60),'capacity':(1,16384),'size':(.001,4),'speed':(0,64),'drag':(0,64),'gravity':(-64,64),'orbit':(-64,64),'amplitude':(0,math.pi),'frequency':(0,16)}
def validate(data):
 if not isinstance(data,dict):raise ValueError('Recipe must be an object')
 unknown=set(data)-set(FIELDS)
 if unknown:raise ValueError('Unsupported fields: '+str(sorted(unknown)))
 for k,v in data.items():
  if k in ('color','accent'):
   if not isinstance(v,list) or len(v)!=3 or any(type(x) not in (float,int) or not math.isfinite(x) or x<0 or x>16 for x in v):raise ValueError(k+' needs three finite 0..16 floats')
  elif k=='motion':
   if v not in ['orbit','burst','fountain','attract']:raise ValueError('Unknown built-in motion; extend runtime before adding a name')
  elif k=='seed':
   if type(v)!=int or not -(2**63)<=v<2**63:raise ValueError('seed needs signed Java long')
  elif k in RANGES:
   lo,hi=RANGES[k]
   if type(v) not in (int,float) or not math.isfinite(v) or not lo<=v<=hi:raise ValueError(k+' out of range')
   if k in ('passes','capacity') and type(v)!=int:raise ValueError(k+' needs integer')
 return data
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('recipe',type=Path,nargs='+');a=p.parse_args()
 for f in a.recipe:validate(json.loads(f.read_text(encoding='utf-8-sig')));print(str(f)+': valid')
