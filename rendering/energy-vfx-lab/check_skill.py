from pathlib import Path
import json,subprocess,sys,hashlib,shutil
ROOT=Path(__file__).parent;SKILL=ROOT.parents[1]/'skills/minecraft-energy-vfx';FIX=ROOT/'evidence/skill-smoke-v025';shutil.rmtree(FIX, ignore_errors=True);FIX.mkdir(exist_ok=True)
results=[]
def run(script,*args,success=True):
 r=subprocess.run([sys.executable,'-X','utf8',str(SKILL/'scripts'/script),*map(str,args)],capture_output=True,text=True,encoding='utf-8')
 ok=(r.returncode==0)==success;results.append({'script':script,'pass':ok,'exit':r.returncode,'output':r.stdout+r.stderr});assert ok,r.stderr
valid=ROOT/'presets/1.json';run('validate_recipe.py',valid)
raw=json.loads(valid.read_text(encoding='utf-8'));raw['params']['typo']=1;bad=FIX/'bad.json';bad.write_text(json.dumps(raw),encoding='utf-8');run('validate_recipe.py',bad,success=False)
raw['params'].pop('typo');raw['params']['radius']=float('nan');bad.write_text(json.dumps(raw),encoding='utf-8');run('validate_recipe.py',bad,success=False)
run('scaffold.py','--out',FIX/'forge','--recipe',valid)
run('scaffold.py','--out',FIX/'forge','--recipe',valid,success=False)
game=FIX/'game';(game/'mods').mkdir(parents=True,exist_ok=True)
run('apply_recipe.py','--recipe',valid,'--game-dir',game,'--install-runtime')
target=game/'config/energyvfx/recipe.json';before=target.read_bytes();run('apply_recipe.py','--recipe',bad,'--game-dir',game,success=False);assert target.read_bytes()==before
run('apply_recipe.py','--recipe',ROOT/'presets/2.json','--game-dir',game)
assert len(list(target.parent.glob('recipe.json.backup-*')))==1
assert json.loads(target.read_text(encoding='utf-8'))==json.loads((ROOT/'presets/2.json').read_text(encoding='utf-8'))
runtime=SKILL/'assets/runtime/energyvfx-0.2.5.jar';assert hashlib.sha256(runtime.read_bytes()).digest()==hashlib.sha256((game/'mods'/runtime.name).read_bytes()).digest()
results.append({'check':'exact recipe, atomic rejection, previous-value backup and unchanged runtime JAR','pass':True})
(ROOT/'evidence/skill-checks.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'{len(results)} Skill checks passed. Fresh scaffold: {FIX/"forge"}')
