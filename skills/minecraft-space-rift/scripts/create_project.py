"""Copy the bundled, self-contained shader modules and Forge source to a NEW folder."""
from pathlib import Path
import argparse,shutil
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('destination',type=Path);args=parser.parse_args()
 source=Path(__file__).resolve().parents[1]/'assets/space-rift-kit'
 target=args.destination.expanduser().resolve()
 if target.exists():raise SystemExit(f'Refusing to overwrite existing path: {target}')
 if source==target or source in target.parents:raise SystemExit('Destination must be outside the skill assets')
 shutil.copytree(source,target)
 print(f'Created {target}\nGenerate shaders: python "{target / "scripts/build.py"}"')
if __name__=='__main__':main()
