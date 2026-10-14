"""Export nine validated recipes for each supported target without changing active recipes."""
from pathlib import Path
import importlib.util,json,hashlib
from palettes import load_catalog,apply_palette_id
ROOT=Path(__file__).resolve().parent
def target(name):
 lab=ROOT.parent/name
 spec=importlib.util.spec_from_file_location(name.replace('-','_'),lab/'recipe.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 return lab,module,module.validate(json.loads((lab/'current-recipe.json').read_text(encoding='utf-8-sig')))
def main():
 catalog=load_catalog();outputs=[]
 for labname in ['fire-coil-lab','alchemy-vfx-lab']:
  lab,module,base=target(labname)
  modes=[('body',0),('weapon',1)] if labname=='fire-coil-lab' else [('furnace',None)]
  for mode,attachment in modes:
   for p in catalog['palettes']:
    recipe=dict(base)
    if attachment is not None:recipe['attachment']=attachment
    recipe=module.validate(apply_palette_id(recipe,catalog,p['id']))
    destination=ROOT/'recipes'/mode/(p['id']+'.json');destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text(json.dumps(recipe,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    outputs.append({'mode':mode,'id':p['id'],'name':p['name'],'schema':recipe['schema'],'file':str(destination),'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()})
 (ROOT/'recipes/manifest.json').write_text(json.dumps({'count':len(outputs),'activeRecipesChanged':False,'recipes':outputs},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(json.dumps({'exported':len(outputs),'activeRecipesChanged':False}))
if __name__=='__main__':main()
