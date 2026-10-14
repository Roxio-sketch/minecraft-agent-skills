"""Strict world-volume/2 authoring, explicit legacy upgrade and resource export."""
import argparse
import json
import math
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = json.loads((ROOT/"workbench/schema.json").read_text(encoding="utf-8"))
FIELDS = SCHEMA["fields"]
END_FIELDS = {key for key,spec in FIELDS.items() if spec["uniform"] in ("EndWarp","EndCarve","EndDepth")}

def no_duplicates(pairs):
    result = {}
    for key,value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key]=value
    return result

def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"),object_pairs_hook=no_duplicates,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"non-finite JSON: {value}")))

def validate(recipe):
    if not isinstance(recipe,dict) or set(recipe)-{"schema","name","parameters"}:
        raise ValueError("recipe must contain only schema, optional name, parameters")
    if recipe.get("schema") != SCHEMA["schema"]:
        raise ValueError("expected schema world-volume/2; use the explicit upgrade command for /1")
    if "name" in recipe and (not isinstance(recipe["name"],str) or len(recipe["name"])>120):
        raise ValueError("name must be a string of at most 120 characters")
    p=recipe.get("parameters")
    if not isinstance(p,dict) or set(p)!=set(FIELDS):
        keys=set(p) if isinstance(p,dict) else set()
        raise ValueError(f"exact {len(FIELDS)} fields required; missing={sorted(set(FIELDS)-keys)}, unknown={sorted(keys-set(FIELDS))}")
    for key,f in FIELDS.items():
        v=p[key]
        if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v):
            raise ValueError(f"{key}: finite number required")
        if not f["min"]<=v<=f["max"]:
            raise ValueError(f"{key}: range [{f['min']}, {f['max']}]")
        if f["type"]=="integer" and v!=int(v):
            raise ValueError(f"{key}: integer required")
    if p["shape"]==2 and p["innerRadius"]>=p["radius"]:
        raise ValueError("annulus innerRadius must be smaller than radius")
    if p["flashDuration"]>p["flashPeriod"]:
        raise ValueError("flashDuration must not exceed flashPeriod")
    if sum(p[k]**2 for k in ("sunX","sunY","sunZ"))<=1e-12:
        raise ValueError("sun direction must be nonzero")
    return recipe

def upgrade_v1(recipe):
    if not isinstance(recipe,dict) or set(recipe)-{"schema","name","parameters"} or recipe.get("schema")!="world-volume/1":
        raise ValueError("upgrade input must be a world-volume/1 recipe")
    p=recipe.get("parameters")
    if not isinstance(p,dict) or set(p)!=set(FIELDS)-END_FIELDS:
        raise ValueError("upgrade requires exact complete 50 legacy fields")
    upgraded={**recipe,"schema":"world-volume/2","parameters":{**p,**{key:FIELDS[key]["default"] for key in END_FIELDS}}}
    return validate(upgraded)

def write_json(path,value):
    Path(path).parent.mkdir(parents=True,exist_ok=True)
    Path(path).write_text(json.dumps(value,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")

def export(recipe,out):
    validate(recipe)
    out=Path(out)
    if out.exists() and any(out.iterdir()):
        raise ValueError("export directory must be empty; use a fresh output path")
    out.mkdir(parents=True,exist_ok=True)
    resource=out/"resources"
    shader=resource/"assets/worldvolume/shaders/core"
    shader.mkdir(parents=True,exist_ok=True)
    core=(ROOT/"workbench/volume-core.glsl").read_bytes()
    shader.joinpath("volume-core.glsl").write_bytes(core)
    wrappers=ROOT/"forge/src/main/shader"
    if not (wrappers/"wrapper-head.glsl").exists() or not (wrappers/"wrapper-tail.glsl").exists():
        raise ValueError("Forge wrapper assets are missing; complete skill assets are required")
    shader.joinpath("world_volume.fsh").write_bytes((wrappers/"wrapper-head.glsl").read_bytes()+core+(wrappers/"wrapper-tail.glsl").read_bytes())
    shader.joinpath("world_volume.vsh").write_text('#version 150\nin vec3 Position;\nout vec2 screenUV;\nvoid main(){gl_Position=vec4(Position,1.0);screenUV=Position.xy*0.5+0.5;}\n',encoding="utf-8",newline="\n")
    uniforms=[dict(name="InverseProjection",type="matrix4x4",count=16,values=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]),
              dict(name="InverseViewRotation",type="matrix3x3",count=9,values=[1,0,0,0,1,0,0,0,1]),
              dict(name="InverseViewTranslation",type="float",count=3,values=[0,0,0]),
              dict(name="Time",type="float",count=1,values=[0])]
    contract=load(ROOT/"workbench/contract.json")
    for name,fields in contract["uniforms"].items():
        uniforms.append(dict(name=name,type="int" if name in ("ViewSteps","LightSteps") else "float",
                             count=len(fields),values=[FIELDS[key]["default"] for key in fields]))
    write_json(shader/"world_volume.json",dict(vertex="worldvolume:world_volume",fragment="worldvolume:world_volume",
               samplers=[dict(name="SceneSampler"),dict(name="DepthSampler")],attributes=["Position"],uniforms=uniforms))
    write_json(resource/"world-volume.json",recipe)
    write_json(resource/"pack.mcmeta",dict(pack=dict(pack_format=15,description=f"Original {SCHEMA['schema']} Forge 1.20.1 shader integration assets")))
    shutil.copy2(ROOT/"workbench/contract.json",resource/"contract.json")
    archive=out/"world-volume-resources.zip"
    with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED) as z:
        for file in sorted(resource.rglob("*")):
            if file.is_file(): z.write(file,file.relative_to(resource).as_posix())
    write_json(out/"recipe.json",recipe)
    return dict(recipe=str(out/"recipe.json"),zip=str(archive),members=len(zipfile.ZipFile(archive).namelist()),
                note="Install the companion Forge backend or adapt its integration; the resource pack alone does not add a render hook.")

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    subs=parser.add_subparsers(dest="command",required=True)
    init=subs.add_parser("init"); init.add_argument("--preset",default="alien-storm"); init.add_argument("--out",required=True)
    check=subs.add_parser("validate"); check.add_argument("input")
    exp=subs.add_parser("export"); exp.add_argument("--input",required=True); exp.add_argument("--out",required=True)
    up=subs.add_parser("upgrade"); up.add_argument("--input",required=True); up.add_argument("--out",required=True)
    args=parser.parse_args()
    try:
        if args.command=="init":
            presets=load(ROOT/"workbench/presets.json")
            match=next((p for p in presets if p["id"]==args.preset),None)
            if match is None: raise ValueError("unknown preset; choose: "+", ".join(p["id"] for p in presets))
            recipe={k:v for k,v in match.items() if k!="id"}
            write_json(args.out,validate(recipe)); result=dict(ok=True,path=str(Path(args.out).resolve()),fields=len(FIELDS))
        elif args.command=="validate":
            recipe=validate(load(args.input)); result=dict(ok=True,schema=recipe["schema"],fields=len(recipe["parameters"]))
        elif args.command=="upgrade":
            upgraded=upgrade_v1(load(args.input));write_json(args.out,upgraded)
            result=dict(ok=True,fromSchema="world-volume/1",schema="world-volume/2",path=args.out,legacyValuesPreserved=True)
        else:
            result=dict(ok=True,**export(load(args.input),args.out))
        print(json.dumps(result,ensure_ascii=False))
    except (ValueError,OSError,KeyError) as error:
        print(json.dumps(dict(ok=False,error=str(error)),ensure_ascii=False),file=sys.stderr)
        return 1
    return 0

if __name__=="__main__":
    sys.exit(main())
