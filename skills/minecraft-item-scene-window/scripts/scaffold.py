"""Copy the portable, original Forge 1.20.1 scene-window template."""
from pathlib import Path
import argparse, shutil

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination',type=Path)
    args=parser.parse_args()
    source=Path(__file__).resolve().parents[1]/'assets/forge-1.20.1-template'
    target=args.destination.resolve()
    if not source.is_dir(): parser.error('Bundled template missing')
    if target==source or target.is_relative_to(source): parser.error('Choose a directory outside the template')
    if target.exists() and any(target.iterdir()): parser.error('Destination must be empty; no files overwritten')
    shutil.copytree(source,target,dirs_exist_ok=True)
    print(f'Created Forge 1.20.1 / Java 17 project: {target}')
    print('Review README.md, then build with gradlew build. No game or mod files were changed.')

if __name__=='__main__':main()
