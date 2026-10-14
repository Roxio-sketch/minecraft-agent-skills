"""Stage the installed skill and portable source from this authoring workspace."""
import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

LAB=Path(__file__).resolve().parents[1]
WORKSPACE=LAB.parent
SKILL=WORKSPACE/"skill/minecraft-volumetric-environment"
EXCLUDE={".gradle","build","run","__pycache__",".git",".idea","node_modules"}
def copy_files(source,destination):
    for file in source.rglob("*"):
        rel=file.relative_to(source)
        if file.is_file() and not set(rel.parts)&EXCLUDE:
            target=destination/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,target)
def stage():
    asset=SKILL/"assets/world-volume-lab"
    for folder in ("workbench","forge"):
        copy_files(LAB/folder,asset/folder)
    for name in ("recipe.py","make_schema.py","check_recipe.py","verify_gpu.cjs","verify_workbench.cjs","verify_vortex.cjs","verify_vortex_revision7.cjs","preview_vortex_revision7.cjs","verify_vortex_revision8.cjs","preview_vortex_revision8.cjs","verify_vortex_revision9.cjs","preview_vortex_revision9.cjs"):
        if not (LAB/"scripts"/name).exists():continue
        target=asset/"scripts"/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(LAB/"scripts"/name,target)
    shutil.copy2(LAB/"LICENSE",asset/"LICENSE")
    if (LAB/"scripts/fixtures").exists():copy_files(LAB/"scripts/fixtures",asset/"scripts/fixtures")
    # Keep the original study reports and relative source-index relationships,
    # without copying the locally extracted third-party source or textures.
    for folder,names in (
        ("iterationT-storm-20261010",("ITERATIONT_STORM_CLOUD_STUDY.md","manifest.json","anchors.json","weather-parameters.csv","analysis-checks.json")),
        ("vortex-waterspout-20261011",("STUDY.md","sources.json","CLOUD_ITERATION_RESEARCH.md","cloud-iteration-source-manifest.json","WATER_ITERATION.md","VISIBLE_WATER_STUDY.md","visible-water-sources.json","WATER_FUNNEL_RESEARCH.md","WATER_BODY_REVISION.md","ITERATION2_RESULT.md")),
    ):
        for name in names:
            source=WORKSPACE/"research"/folder/name
            if source.exists():
                target=LAB/"references/research"/folder/name
                target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    copy_files(LAB/"references",SKILL/"references")
    records=SKILL/"references/validation";records.mkdir(parents=True,exist_ok=True)
    for relative,name in (("evidence/protocol/report.json","protocol.json"),("evidence/gpu/report.json","gpu.json"),
                          ("evidence/workbench/report.json","workbench.json"),("evidence/research/audit-results.json","sources.json"),
                          ("evidence/forge/acceptance.json","forge-acceptance.json"),("evidence/forge/build-manifest.json","forge-build.json"),
                          ("evidence/forge/runtime/results.json","forge-runtime.json"),("evidence/forward/report.json","forward.json"),
                          ("evidence/forward-v2/report.json","forward-v2.json"),
                          ("evidence/forward-v2/failed-uniform-assumption.json","forward-v2-fixture-assumption.json"),
                          ("evidence/portable-v2/evidence/gpu/report.json","portable-gpu.json"),
                          ("evidence/delivery/metadata-export.json","metadata-export.json"),
                          ("evidence/tribulation/report.json","tribulation.json"),
                          ("evidence/vortex/report.json","vortex.json"),
                          ("evidence/vortex/replay-sampling-diagnosis.json","vortex-sampling-diagnosis.json"),
                          ("evidence/portable-vortex-water4/report.json","vortex-portable-before-sampling-fix.json"),
                          ("evidence/vortex-water-iteration/report.json","vortex-water.json"),
                          ("evidence/vortex-revision7/verify/report.json","vortex-revision7.json"),
                          ("evidence/vortex-revision7/review/report.json","vortex-revision7-visual.json"),
                          ("evidence/vortex-revision7/review/timing.json","vortex-revision7-timing.json"),
                          ("evidence/vortex-revision8/verify/report.json","vortex-revision8.json"),
                          ("evidence/vortex-revision8/review/report.json","vortex-revision8-visual.json"),
                          ("evidence/vortex-revision8/review/timing.json","vortex-revision8-timing.json"),
                          ("evidence/vortex-revision9/verify/report.json","vortex-revision9.json"),
                          ("evidence/vortex-revision9/review/report.json","vortex-revision9-visual.json"),
                          ("evidence/vortex-revision9/review/timing.json","vortex-revision9-timing.json"),
                          ("evidence/vortex-revision9/final-visual-review.md","vortex-revision9-visual-review.md"),
                          ("evidence/vortex/history/layered-water8/report.json","vortex-layered-water8-page.json"),
                          ("evidence/vortex/history/layered-water8/portable.json","vortex-layered-water8-portable.json"),
                          ("evidence/vortex/history/bowl9-before-signed-square/report.json","vortex-bowl9-before-square-page.json"),
                          ("evidence/vortex/history/continuity7/evidence/vortex/report.json","vortex-continuity7-page.json"),
                          ("evidence/vortex/history/continuity7/evidence/vortex/portable.json","vortex-continuity7-portable.json"),
                          ("evidence/vortex/portable.json","vortex-portable.json")):
        source=LAB/relative
        if source.exists():shutil.copy2(source,records/name)
    for relative,name in (("evidence/bliss-density-study/manifest.json","bliss-density-study.json"),("evidence/astralex-study/manifest.json","astralex-study.json"),("evidence/end-distributions-gpu/report.json","end-distributions-gpu.json")):
        source=LAB/relative
        if source.exists():shutil.copy2(source,records/name)
    return asset
def archive(source,target):
    with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED) as z:
        for file in sorted(source.rglob("*")):
            if file.is_file() and not set(file.relative_to(source).parts)&EXCLUDE:
                z.write(file,source.name+"/"+file.relative_to(source).as_posix())
    with zipfile.ZipFile(target,"r") as check:
        if check.testzip() is not None:raise ValueError("ZIP CRC failure")
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--install",action="store_true")
    parser.add_argument("--skills-root",default=str(Path.home()/".codex/skills"));args=parser.parse_args()
    asset=stage();output=WORKSPACE/"deliverables/world-volume";output.mkdir(parents=True,exist_ok=True)
    archive(SKILL,output/"minecraft-volumetric-environment-skill.zip")
    archive(asset,output/"world-volume-lab-source.zip")
    manifest=LAB/"evidence/forge/build-manifest.json"
    production=LAB/json.loads(manifest.read_text(encoding="utf-8"))["jar"]["path"] if manifest.exists() else LAB/"forge/build/libs/world-volume-lab-0.2.0.jar"
    if production.exists():shutil.copy2(production,output/production.name)
    installed=None
    if args.install:
        installed=Path(args.skills_root)/SKILL.name
        copy_files(SKILL,installed)
    files={str(file.relative_to(output)):dict(bytes=file.stat().st_size,sha256=hashlib.sha256(file.read_bytes()).hexdigest()) for file in output.iterdir() if file.suffix in (".zip",".jar")}
    report=dict(skill=str(SKILL),asset=str(asset),installed=str(installed) if installed else None,archives=files)
    (output/"delivery.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,ensure_ascii=False))
if __name__=="__main__":main()
