"""Local, editable alchemy effect preview based on the actual Wuxing Xiantu OBJ."""
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from pathlib import Path
import argparse,json,os,tempfile,importlib.util
ROOT=Path(__file__).resolve().parent
_spec=importlib.util.spec_from_file_location('_alchemy_recipe',ROOT/'recipe.py')
_recipe=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(_recipe)
def write_json(path,value):
 fd,name=tempfile.mkstemp(prefix='.alchemy-',suffix='.tmp',dir=path.parent)
 try:
  with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump(value,f,ensure_ascii=False,indent=2);f.write('\n')
  os.replace(name,path)
 finally:
  if os.path.exists(name):os.unlink(name)
class Handler(SimpleHTTPRequestHandler):
 extensions_map={**SimpleHTTPRequestHandler.extensions_map,'.js':'text/javascript','.mjs':'text/javascript','.glsl':'text/plain; charset=utf-8'}
 def __init__(self,*a,**kw):super().__init__(*a,directory=str(ROOT),**kw)
 def log_message(self,*args):pass
 def end_headers(self):self.send_header('Cache-Control','no-store');super().end_headers()
 def send_json(self,code,value):
  body=json.dumps(value,ensure_ascii=False).encode('utf-8');self.send_response(code);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def do_GET(self):
  if self.path.split('?',1)[0]=='/api/flame-palettes':
   try:self.send_json(200,json.loads((ROOT.parent/'vfx-palettes/nine-flames.json').read_text(encoding='utf-8-sig')))
   except (OSError,ValueError) as e:self.send_json(400,{'ok':False,'error':str(e)})
   return
  if self.path.split('?',1)[0] in {'/api/recipe','/api/model'}:
   try:
    value=_recipe.loads((ROOT/'current-recipe.json').read_text(encoding='utf-8-sig')) if self.path.split('?',1)[0]=='/api/recipe' else json.loads((ROOT/'model-audit.json').read_text(encoding='utf-8'))
    self.send_json(200,value)
   except (OSError,ValueError) as e:self.send_json(400,{'ok':False,'error':str(e)})
  else:super().do_GET()
 def do_POST(self):
  try:
   hosts={f'127.0.0.1:{self.server.server_port}',f'localhost:{self.server.server_port}'}
   if self.headers.get('Host') not in hosts or self.headers.get('Origin') not in {None,*('http://'+x for x in hosts)}:raise ValueError('只接受本地工作台请求。')
   size=int(self.headers.get('Content-Length','0'))
   if not 1<=size<=32768:raise ValueError('配方请求大小无效。')
   if self.path!='/api/recipe':self.send_json(404,{'ok':False,'error':'接口不存在。'});return
   recipe=_recipe.loads(self.rfile.read(size).decode('utf-8'));current=ROOT/'current-recipe.json'
   if current.exists():write_json(ROOT/'last-recipe.json',_recipe.loads(current.read_text(encoding='utf-8-sig')))
   write_json(current,recipe);self.send_json(200,{'ok':True,'recipe':recipe})
  except (OSError,ValueError,UnicodeDecodeError) as e:self.send_json(400,{'ok':False,'error':str(e)})
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8820);a=parser.parse_args()
 for port in range(a.port,a.port+21):
  try:server=ThreadingHTTPServer(('127.0.0.1',port),Handler);break
  except OSError:continue
 else:raise OSError('没有可用端口。')
 server.daemon_threads=True;print(f'http://127.0.0.1:{server.server_port}/',flush=True);server.serve_forever()
if __name__=='__main__':main()
