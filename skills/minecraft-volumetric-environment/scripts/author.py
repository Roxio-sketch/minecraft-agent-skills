"""Create an editable standalone world-volume work from this skill's assets."""
import argparse
import json
import shutil
import sys
from pathlib import Path

SKILL=Path(__file__).resolve().parents[1]
ASSETS=SKILL/"assets/world-volume-lab"
sys.path.insert(0,str(ASSETS/"scripts"))
import recipe

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out",required=True)
    parser.add_argument("--preset",default="alien-storm")
    parser.add_argument("--name")
    parser.add_argument("--set",action="append",default=[],metavar="FIELD=NUMBER")
    args=parser.parse_args()
    try:
        presets=recipe.load(ASSETS/"workbench/presets.json")
        selected=next((p for p in presets if p["id"]==args.preset),None)
        if selected is None: raise ValueError("unknown preset: "+args.preset)
        r={k:v for k,v in selected.items() if k!="id"}
        if args.name is not None:r["name"]=args.name
        for assignment in args.set:
            key,sep,value=assignment.partition("=")
            if not sep or key not in recipe.FIELDS:raise ValueError("unknown field assignment: "+assignment)
            r["parameters"][key]=json.loads(value)
        recipe.validate(r)
        out=Path(args.out).resolve()
        if out.exists() and (not out.is_dir() or any(out.iterdir())):
            raise ValueError("output must be a new or empty directory")
        shutil.copytree(ASSETS,out,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','.gradle','build','run','evidence'))
        for target in (out/"recipe.json",out/"workbench/initial-recipe.json",out/"forge/run/config/world-volume.json"):
            recipe.write_json(target,r)
        print(json.dumps(dict(ok=True,root=str(out),recipe=str(out/"recipe.json"),preview=f'python "{out / "workbench/serve.py"}" --port 8774'),ensure_ascii=False))
        return 0
    except (ValueError,OSError,json.JSONDecodeError) as e:
        print(json.dumps(dict(ok=False,error=str(e)),ensure_ascii=False),file=sys.stderr);return 1

if __name__=="__main__":sys.exit(main())
