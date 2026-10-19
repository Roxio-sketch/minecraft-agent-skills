"""Copy the complete, portable nine-effect gallery to a new folder."""
from pathlib import Path
import argparse,shutil
p=argparse.ArgumentParser(description=__doc__);p.add_argument('destination',type=Path);a=p.parse_args();target=a.destination.expanduser().resolve()
if target.exists():raise SystemExit(f'Refusing to overwrite {target}')
shutil.copytree(Path(__file__).resolve().parents[1]/'assets/vfx-library',target)
print(f'Created {target}');print('Serve locally with: python -m http.server --bind 127.0.0.1 --directory <folder>')
