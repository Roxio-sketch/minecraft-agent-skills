"""Verify artifact integrity; report rendering acceptance separately and unchanged."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

LAB=Path(__file__).resolve().parents[1]
WORKSPACE=LAB.parent
SKILL=WORKSPACE/"skill/minecraft-volumetric-environment"
INSTALLED=Path.home()/".codex/skills/minecraft-volumetric-environment"
OUTPUT=WORKSPACE/"deliverables/world-volume"
checks=[]
def check(name,passed):checks.append(dict(name=name,passed=bool(passed)))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
core=LAB/"workbench/volume-core.glsl"
build=json.loads((LAB/"evidence/forge/build-manifest.json").read_text(encoding="utf-8"))
check("core source bound to final Forge manifest",sha(core)==build["coreSHA256"])
jar=LAB/build["jar"]["path"]
check("production jar hash",sha(jar)==build["jar"]["sha256"])
with zipfile.ZipFile(jar) as z:
    check("production jar CRC",z.testzip() is None)
    shader=z.read("assets/worldvolume/shaders/core/world_volume.fsh")
    check("production shader contains exact shared core",core.read_bytes() in shader)
    check("production shader hash",hashlib.sha256(shader).hexdigest()==build["combinedShaderSHA256"])
    jar_json=json.loads(z.read("assets/worldvolume/shaders/core/world_volume.json"))
with zipfile.ZipFile(LAB/"evidence/workbench/download.zip") as z:
    check("actual HTTP resource download CRC",z.testzip() is None)
    check("actual HTTP ZIP is pack root",{"pack.mcmeta","world-volume.json"}<=set(z.namelist()))
    check("actual HTTP shader equals production jar",z.read("assets/worldvolume/shaders/core/world_volume.fsh")==shader)
    check("actual HTTP uniform JSON equals production jar",json.loads(z.read("assets/worldvolume/shaders/core/world_volume.json"))==jar_json)
for relative in ("gpu/report.json","protocol/report.json","workbench/report.json"):
    r=json.loads((LAB/"evidence"/relative).read_text(encoding="utf-8"))
    check(relative+" passes and binds final core",r["ok"] and r["coreSha256"]==sha(core))
metadata=json.loads((LAB/"evidence/delivery/metadata-export.json").read_text(encoding="utf-8"))
check("final /2 HTTP metadata export passes and binds core",metadata["ok"] and metadata["coreSha256"]==sha(core))
with zipfile.ZipFile(LAB/"evidence/delivery/current-resource-export.zip") as z:
    check("final /2 HTTP ZIP CRC",z.testzip() is None)
    check("final /2 pack metadata", "world-volume/2" in json.loads(z.read("pack.mcmeta"))["pack"]["description"])
    check("final /2 resource shader equals production jar",z.read("assets/worldvolume/shaders/core/world_volume.fsh")==shader)
    check("final /2 resource descriptor equals production jar",json.loads(z.read("assets/worldvolume/shaders/core/world_volume.json"))==jar_json)
forward=json.loads((LAB/"evidence/forward-v2/report.json").read_text(encoding="utf-8"))
check("independent /2 forward authoring passes",forward["ok"])
vortex=json.loads((LAB/"evidence/vortex/report.json").read_text(encoding="utf-8"))
portable=json.loads((LAB/"evidence/vortex/portable.json").read_text(encoding="utf-8"))
revision=json.loads((LAB/"evidence/vortex-revision9/verify/report.json").read_text(encoding="utf-8"))
visual=json.loads((LAB/"evidence/vortex-revision9/review/report.json").read_text(encoding="utf-8"))
check("event verification result is consistent with its checks",vortex["passed"]==all(c["passed"] for c in vortex["checks"]))
check("portable verification result is consistent with its checks",portable["ok"]==all(c["passed"] for c in portable["checks"]))
check("revision GPU result is consistent with its checks",revision["passed"]==all(c["passed"] for c in revision["checks"]))
check("revision visual result is consistent with its checks",visual["passed"]==all(c["passed"] for c in visual["checks"]))
for key,name in (("coreSha256","volume-core.glsl"),("previewSha256","preview.js"),("noiseGeneratorSha256","vortex-noise.js")):
    current=LAB/"workbench/vortex"/name
    check("event report binds current "+name,vortex[key]==sha(current))
    check("portable report binds current "+name,portable[key]==sha(current))
    check("revision GPU report binds current "+name,revision[key]==sha(current))
    if key in visual:
        check("revision visual report binds current "+name,visual[key]==sha(current))
    check("staged event asset exact "+name,(SKILL/"assets/world-volume-lab/workbench/vortex"/name).read_bytes()==current.read_bytes())
with zipfile.ZipFile(OUTPUT/"vortex-waterspout-workbench-and-research-20261011.zip") as z:
    prefix="vortex-waterspout-workbench/"
    check("standalone event archive CRC",z.testzip() is None)
    for name in ("volume-core.glsl","preview.js","vortex-noise.js"):
        check("standalone event source exact "+name,z.read(prefix+"workbench/vortex/"+name)==(LAB/"workbench/vortex"/name).read_bytes())
    check("standalone event report exact",z.read(prefix+"evidence/vortex/report.json")== (LAB/"evidence/vortex/report.json").read_bytes())
    for folder in ("verify", "review"):
        relative="evidence/vortex-revision9/"+folder+"/report.json"
        check("standalone revision report exact "+folder,z.read(prefix+relative)==(LAB/relative).read_bytes())
    research="references/research/vortex-waterspout-20261011/WATER_BODY_REVISION.md"
    check("standalone new liquid research exact",z.read(prefix+research)==(SKILL/research).read_bytes())
for file in SKILL.rglob("*.md"):
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)',file.read_text(encoding="utf-8")):
        if not target.startswith(("http:","https:","#","app:","codex:")):
            check("link "+str(file.relative_to(SKILL))+" "+target,(file.parent/target.split("#")[0]).exists())
for file in SKILL.rglob("*"):
    if file.is_file() and '__pycache__' not in file.parts:
        rel=file.relative_to(SKILL);target=INSTALLED/rel
        check("installed exact "+str(rel),target.exists() and target.read_bytes()==file.read_bytes())
for name,source in (("minecraft-volumetric-environment-skill.zip",SKILL),("world-volume-lab-source.zip",SKILL/"assets/world-volume-lab")):
    with zipfile.ZipFile(OUTPUT/name) as z:
        check(name+" CRC",z.testzip() is None)
        check(name+" excludes build/run/third-party shaderpacks",all(not set(Path(n).parts)&{'build','run','.gradle','shaderpacks','node_modules'} for n in z.namelist()))
        for entry in z.namelist():
            if not entry.endswith('/'):
                rel=Path(*Path(entry).parts[1:]);file=source/rel
                check(name+" exact "+str(rel),file.exists() and file.read_bytes()==z.read(entry))
rendering=dict(eventPassed=vortex["passed"],portablePassed=portable["ok"],
               revisionGpuPassed=revision["passed"],
               revisionGpuFailed=[c["name"] for c in revision["checks"] if not c["passed"]],
               revisionVisualPassed=visual["passed"],
               revisionVisualFailed=[c["name"] for c in visual["checks"] if not c["passed"]],
               eventFailed=[c["name"] for c in vortex["checks"] if not c["passed"]],
               portableFailed=[c["name"] for c in portable["checks"] if not c["passed"]])
report=dict(ok=all(c['passed'] for c in checks),scope="artifact-integrity",checks=checks,renderingVerification=rendering,coreSha256=sha(core),jarSha256=sha(jar))
(LAB/"evidence/delivery").mkdir(parents=True,exist_ok=True)
(LAB/"evidence/delivery/report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(json.dumps(dict(ok=report['ok'],scope=report['scope'],checks=len(checks),failed=[c['name'] for c in checks if not c['passed']],renderingVerification=rendering),ensure_ascii=False))
sys.exit(0 if report['ok'] else 1)
