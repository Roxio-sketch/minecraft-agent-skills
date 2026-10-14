from pathlib import Path
import argparse,subprocess,sys,json,urllib.request
parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8796);args=parser.parse_args()
if not 1<=args.port<=65535:parser.error('--port 需为 1–65535 的整数')
script=Path(__file__).resolve().parents[1]/'assets/lab/serve.py'
print(f'能量特效编辑工作台：http://127.0.0.1:{args.port}/',flush=True)
try:
 with urllib.request.urlopen(f'http://127.0.0.1:{args.port}/api/recipe',timeout=1) as response:existing=json.load(response)
 if isinstance(existing,dict) and isinstance(existing.get('recipe'),dict) and existing['recipe'].get('schema')=='energy-vfx/2':
  print('复用已运行的本地工作台；保留当前配方。');sys.exit(0)
except (OSError,ValueError):pass
subprocess.run([sys.executable,'-X','utf8',str(script),'--port',str(args.port)],check=True)
