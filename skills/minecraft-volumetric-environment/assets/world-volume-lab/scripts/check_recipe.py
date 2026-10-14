"""Observable protocol invariants, malformed imports and source export fidelity."""
import copy
import hashlib
import json
import tempfile
import zipfile
from pathlib import Path
import recipe

checks=[]
def check(label,condition):
    checks.append(dict(name=label,passed=bool(condition)))
def reject(label,value):
    try: recipe.validate(value)
    except ValueError: check(label,True)
    else: check(label,False)

presets=recipe.load(recipe.ROOT/"workbench/presets.json")
for preset in presets:
    p={key:value for key,value in preset.items() if key!="id"}
    check("preset "+preset["id"],recipe.validate(p)==p)
base={key:value for key,value in presets[0].items() if key!="id"}
legacy={**base,"schema":"world-volume/1","parameters":{k:v for k,v in base["parameters"].items() if k not in recipe.END_FIELDS}}
upgraded=recipe.upgrade_v1(legacy)
check("complete legacy upgrade preserves 50 values",all(upgraded["parameters"][k]==v for k,v in legacy["parameters"].items()) and upgraded["parameters"]["densityModel"]==0)
check("new fields explicit defaults",all(upgraded["parameters"][k]==recipe.FIELDS[k]["default"] for k in recipe.END_FIELDS))
try:
    missing=copy.deepcopy(legacy);missing["parameters"].pop("density");recipe.upgrade_v1(missing)
except ValueError:check("malformed legacy upgrade rejected",True)
else:check("malformed legacy upgrade rejected",False)
for label,mutate in [
    ("missing",lambda p:p["parameters"].pop("density")),
    ("unknown field",lambda p:p["parameters"].update(extra=1)),
    ("unknown outer",lambda p:p.update(extra=1)),
    ("old schema requires upgrade",lambda p:p.update(schema="world-volume/1")),
    ("NaN",lambda p:p["parameters"].update(density=float("nan"))),
    ("bool",lambda p:p["parameters"].update(flashEnabled=True)),
    ("fractional int",lambda p:p["parameters"].update(viewSteps=9.5)),
    ("out of range",lambda p:p["parameters"].update(extinction=-1)),
    ("empty sun",lambda p:p["parameters"].update(sunX=0,sunY=0,sunZ=0)),
    ("event exceeds cycle",lambda p:p["parameters"].update(flashDuration=4,flashPeriod=1)),
    ("invalid ring",lambda p:p["parameters"].update(shape=2,innerRadius=p["parameters"]["radius"]))]:
    p=copy.deepcopy(base); mutate(p); reject(label,p)
with tempfile.TemporaryDirectory(prefix="world-volume-protocol-") as task_temp:
    task_path=Path(task_temp)
    duplicate=task_path/"duplicate.json"; duplicate.write_text('{"schema":1,"schema":2}')
    try: recipe.load(duplicate)
    except ValueError: check("duplicate keys",True)
    else: check("duplicate keys",False)
    recipe.write_json(task_path/"input.json",base)
    check("JSON roundtrip",recipe.load(task_path/"input.json")==base)
    exported=recipe.export(base,task_path/"out")
    with zipfile.ZipFile(exported["zip"]) as z:
        check("ZIP CRC",z.testzip() is None)
        check("ZIP core exact",z.read("assets/worldvolume/shaders/core/volume-core.glsl")==
              (recipe.ROOT/"workbench/volume-core.glsl").read_bytes())
        check("ZIP recipe exact",json.loads(z.read("world-volume.json"))==base)
        check("safe member paths",all(not name.startswith("/") and ".." not in Path(name).parts for name in z.namelist()))
contract=recipe.load(recipe.ROOT/"workbench/contract.json")
names=[n for group in contract["uniforms"].values() for n in group]
check("uniform mapping complete unique",len(names)==len(set(names))==len(recipe.FIELDS) and set(names)==set(recipe.FIELDS))
report=dict(ok=all(c["passed"] for c in checks),checks=checks,
            coreSha256=hashlib.sha256((recipe.ROOT/"workbench/volume-core.glsl").read_bytes()).hexdigest())
target=recipe.ROOT/"evidence/protocol/report.json"; recipe.write_json(target,report)
print(json.dumps(dict(ok=report["ok"],checks=len(checks),report=str(target))))
raise SystemExit(0 if report["ok"] else 1)
