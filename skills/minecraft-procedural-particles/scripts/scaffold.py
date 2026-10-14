import argparse,shutil
from pathlib import Path
p=argparse.ArgumentParser(description='Copy the buildable Forge 1.20.1 example to an empty directory.');p.add_argument('destination',type=Path);a=p.parse_args()
source=Path(__file__).resolve().parents[1]/'assets'/'forge-1.20.1'
target=a.destination.resolve()
if target.exists() and any(target.iterdir()):raise SystemExit('Destination must be empty; existing work is preserved')
if target==source or source in target.parents:raise SystemExit('Destination must be outside source template')
shutil.copytree(source,target,dirs_exist_ok=True,ignore=shutil.ignore_patterns('build','.gradle','*.class','*.log'))
print(target)
