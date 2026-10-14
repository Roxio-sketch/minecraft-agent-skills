import json,math,re
from pathlib import Path
DEFAULTS=json.loads((Path(__file__).parent/'defaults-v2.json').read_text(encoding='utf-8'))
RANGES={'fireRadius': (1.46, 2.05), 'fireHeight': (0.2, 1.8), 'fireStrength': (0.0, 3.0), 'vaporRadius': (0.1, 0.28), 'vaporHeight': (0.3, 3.0), 'vaporSpread': (0.0, 1.2), 'vaporStrength': (0.0, 3.0), 'flowSpeed': (0.0, 2.0), 'turbulence': (0.0, 2.0), 'filamentWidth': (0.008, 0.12), 'filamentStrength': (0.0, 3.0), 'orbitRadius': (0.2, 1.1), 'orbitSpeed': (-0.5, 0.5), 'coreRadius': (0.03, 0.35), 'coreStrength': (0.0, 4.0), 'burstRadius': (0.3, 2.5), 'burstStrength': (0.0, 4.0), 'sigilRadius': (2.52, 4.0), 'sigilStrength': (0.0, 2.0), 'faceHeatStrength': (0.0, 2.0), 'faceHeatHeight': (0.2, 2.0), 'faceHeatSpread': (0.03, 0.55), 'faceSparkRate': (0.0, 24.0), 'faceSparkLife': (0.3, 3.0), 'faceSparkSize': (0.006, 0.06), 'faceSparkSpeed': (0.1, 1.3), 'aromaStrength': (0.0, 2.0), 'aromaHeight': (0.3, 2.5), 'ingredientCount': (0.0, 12.0), 'ingredientSize': (0.025, 0.16), 'ingredientStrength': (0.0, 3.0), 'gatherSpeed': (0.0, 2.0), 'energyRingRadius': (2.5, 4.2), 'energyRingHeight': (0.3, 3.2), 'energyRingThickness': (0.012, 0.12), 'energyRingStrength': (0.0, 2.0), 'energyRingSpeed': (-0.5, 0.5), 'intensity': (0.2, 3.0), 'opacity': (0.0, 1.0), 'segments': (24.0, 512.0), 'seed': (0.0, 1000000.0)}
COLORS={'energyRingColor', 'fireMid', 'aromaColor', 'vaporEdge', 'coreColor', 'ingredientColor', 'vaporCore', 'fireCore', 'fireEdge', 'vaporMid'}
LEGACY_KEYS={'seed', 'flowSpeed', 'vaporRadius', 'vaporEdge', 'burstStrength', 'coreColor', 'filamentWidth', 'fireStrength', 'vaporMid', 'opacity', 'burstRadius', 'turbulence', 'fireRadius', 'vaporCore', 'fireHeight', 'orbitSpeed', 'intensity', 'sigilRadius', 'filamentStrength', 'vaporStrength', 'fireMid', 'sigilStrength', 'vaporSpread', 'coreStrength', 'schema', 'coreRadius', 'fireCore', 'vaporHeight', 'segments', 'fireEdge', 'orbitRadius'}
def validate(v):
 if not isinstance(v,dict):raise ValueError('配方必须为JSON对象。')
 if v.get('schema')=='alchemy-vfx/1':
  if set(v)!=LEGACY_KEYS:raise ValueError('旧版必须精确包含31个字段。')
  # Validate legacy values before filling defaults; never upgrade a partial input.
  v={**DEFAULTS,**v,'schema':'alchemy-vfx/2'}
 if set(v)!=set(DEFAULTS) or v.get('schema')!='alchemy-vfx/2':raise ValueError('需要完整alchemy-vfx/2配方，无缺少或多余字段。')
 result=dict(v)
 for k,(lo,hi) in RANGES.items():
  x=v[k]
  if isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or not lo<=x<=hi or (k in {'seed','segments','ingredientCount'} and not float(x).is_integer()):raise ValueError(k+'越界或类型错误。')
 for k in COLORS:
  if not isinstance(v[k],str) or not re.fullmatch('#[0-9a-fA-F]{6}',v[k]):raise ValueError(k+'须为#RRGGBB。')
  result[k]=v[k].lower()
 return result
def loads(s):
 def bad(x):raise ValueError('拒绝非有限常量'+x)
 return validate(json.loads(s,parse_constant=bad))
