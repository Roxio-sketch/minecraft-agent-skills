"""Validate portable effect inventory, HTML dependencies and public-path hygiene."""
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from html.parser import HTMLParser
from urllib.request import urlopen
from urllib.parse import urljoin,urlsplit
import argparse,ast,json,subprocess,shutil,threading
ROOT=Path(__file__).resolve().parents[1]
class Resources(HTMLParser):
 def __init__(self):super().__init__();self.urls=[]
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if tag=='script' and a.get('src'):self.urls.append(a['src'])
  if tag=='link' and a.get('rel')=='stylesheet' and a.get('href'):self.urls.append(a['href'])
class Quiet(SimpleHTTPRequestHandler):
 def log_message(self,*args):pass

def main():
 p=argparse.ArgumentParser();p.add_argument('--output',type=Path);p.add_argument('--node',default=shutil.which('node'));args=p.parse_args();root=ROOT/'assets/vfx-library';catalog=json.loads((root/'catalog.json').read_text(encoding='utf-8'));fail=[];checked=[]
 assert len(catalog)==9 and len({e['id'] for e in catalog})==9
 server=ThreadingHTTPServer(('127.0.0.1',0),partial(Quiet,directory=str(root)));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();base=f'http://127.0.0.1:{server.server_port}/'
 try:
  for e in catalog:
   try:
    url=urljoin(base,e['entry']);text=urlopen(url,timeout=10).read().decode('utf-8-sig');parser=Resources();parser.feed(text)
    for resource in parser.urls:
     assert not urlsplit(resource).scheme,'External HTML dependency';assert urlopen(urljoin(url,resource),timeout=10).status==200
    checked.append(e['id']);print('PASS '+e['id'])
   except Exception as ex:fail.append(f"{e['id']}: {ex}")
 finally:server.shutdown();server.server_close();thread.join()
 js=0
 for file in root.rglob('*'):
  if not file.is_file():continue
  relative=file.relative_to(root).as_posix()
  if file.suffix in ['.js','.html','.json','.glsl','.fsh','.vsh','.md']:
   text=file.read_text(encoding='utf-8-sig')
   for marker in ['/asset/','/api/recipe','C:/Users/','C:\\Users\\','chengran_skin']:
    if marker in text:fail.append(relative+': private dependency '+marker)
  if file.suffix=='.json':
   try:json.loads(file.read_text(encoding='utf-8-sig'))
   except Exception as ex:fail.append(relative+': '+str(ex))
  if file.suffix=='.py':ast.parse(file.read_text(encoding='utf-8-sig'))
  if file.suffix=='.js' and args.node:
   result=subprocess.run([args.node,'--check',str(file)],capture_output=True)
   if result.returncode:fail.append(relative+': JS syntax error '+result.stderr.decode('utf-8','replace'))
   js+=1
 for name in ['qi-sea/shader/qi_sea.fsh','qi-sea/textures/qi_sea.png','qi-sea/textures/qi_sea_single.png','sword-formations/actual-sword.json','sword-formations/actual-sword.png','sword-formations/sword-distance.png','sword-formations/demo_skin.png','cataclysm-rift/textures/flow-noise.png','cataclysm-rift/textures/filaments.png','rift/NOTICE.md']:
  if not(root/name).is_file():fail.append('Missing '+name)
 result={'date':'2026-10-19','effects':checked,'javascript_syntax_files':js,'failures':fail,'ok':not fail,'scope':'Portable HTTP/HTML dependencies, JSON/Python/optional JS syntax, required dynamic assets, private-path scan; not browser GPU or Minecraft certification'}
 if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps(result,ensure_ascii=False));raise SystemExit(bool(fail))
if __name__=='__main__':main()
