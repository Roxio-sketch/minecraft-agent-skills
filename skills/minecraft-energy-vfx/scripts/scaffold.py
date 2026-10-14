from pathlib import Path
import argparse,shutil,json
from validate_recipe import read_recipe
parser=argparse.ArgumentParser();parser.add_argument('--out',required=True);parser.add_argument('--recipe',required=True);args=parser.parse_args()
recipe=read_recipe(args.recipe);out=Path(args.out).resolve()
if out.exists() and any(out.iterdir()):raise SystemExit('输出目录必须为空；不要覆盖已有项目。现有项目请按 integration.md 接入。')
source=Path(__file__).resolve().parents[1]/'assets/lab/forge'
shutil.copytree(source,out,dirs_exist_ok=True)
resource=out/'src/main/resources/assets/energyvfx/recipe.json';resource.write_text(json.dumps(recipe,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'已生成 Forge 1.20.1 工程：{out}\n设置 JAVA_HOME 为 JDK 17，运行 gradlew.bat build。')
