"""Local fire-coil parameter editor and fixed-file game bridge."""
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from pathlib import Path
import argparse,json,os,time,uuid,tempfile,importlib.util

ROOT=Path(__file__).resolve().parent
GAME=Path(os.environ['MINECRAFT_GAME_DIR']).expanduser().resolve() if os.environ.get('MINECRAFT_GAME_DIR') else ROOT / '.unconfigured-game'
_spec=importlib.util.spec_from_file_location('_firecoil_recipe',ROOT/'recipe.py')
_recipe=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(_recipe)
loads,validate=_recipe.loads,_recipe.validate

def write_json(path,value):
 path.parent.mkdir(parents=True,exist_ok=True)
 fd,name=tempfile.mkstemp(prefix='.firecoil-',suffix='.tmp',dir=path.parent)
 try:
  with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump(value,f,ensure_ascii=False,indent=2);f.write('\n')
  os.replace(name,path)
 finally:
  if os.path.exists(name):os.unlink(name)

class Handler(SimpleHTTPRequestHandler):
 extensions_map={**SimpleHTTPRequestHandler.extensions_map,'.js':'text/javascript','.mjs':'text/javascript','.glsl':'text/plain; charset=utf-8'}
 def __init__(self,*a,**kw):super().__init__(*a,directory=str(ROOT),**kw)
 def log_message(self,*a):pass
 def end_headers(self):
  self.send_header('Cache-Control','no-store')
  self.send_header('X-Content-Type-Options','nosniff')
  super().end_headers()
 def send_json(self,status,data):
  body=json.dumps(data,ensure_ascii=False).encode('utf-8');self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def local_request(self):
  if self.headers.get('Host') not in {f'127.0.0.1:{self.server.server_port}',f'localhost:{self.server.server_port}'}:raise ValueError('只接受本地工作台请求。')
  origin=self.headers.get('Origin')
  if origin and origin not in {f'http://127.0.0.1:{self.server.server_port}',f'http://localhost:{self.server.server_port}'}:raise ValueError('请求来源不匹配。')
 def game_status(self):
  jars=list((GAME/'mods').glob('firecoil-*.jar')) if GAME.is_dir() else []
  result={'hasRuntime':bool(jars),'compatibleRuntime':any(p.name=='firecoil-0.2.0.jar' for p in jars),'instance':GAME.name if GAME.name != '.unconfigured-game' else '未配置','gameDir':str(GAME),'installedJar':jars[0].name if jars else None}
  p=GAME/'config/firecoil/result.json'
  if p.exists():
   try:result['lastResult']=json.loads(p.read_text(encoding='utf-8-sig'))
   except (OSError,ValueError):pass
  return result
 def do_GET(self):
  if self.path.split('?',1)[0]=='/api/flame-palettes':
   try:self.send_json(200,json.loads((ROOT.parent/'vfx-palettes/nine-flames.json').read_text(encoding='utf-8-sig')))
   except (OSError,ValueError) as e:self.send_json(400,{'ok':False,'error':str(e)})
   return
  if self.path.split('?',1)[0]=='/api/recipe':
   try:self.send_json(200,loads((ROOT/'current-recipe.json').read_text(encoding='utf-8-sig')))
   except Exception as e:self.send_json(400,{'ok':False,'error':str(e)})
  elif self.path.split('?',1)[0]=='/api/game-status':self.send_json(200,self.game_status())
  else:super().do_GET()
 def do_POST(self):
  try:
   self.local_request();size=int(self.headers.get('Content-Length','0'))
   if not 1<=size<=32768:raise ValueError('请求大小无效。')
   body=self.rfile.read(size).decode('utf-8')
   if self.path=='/api/recipe':
    recipe=loads(body)
    old=ROOT/'current-recipe.json';backup=ROOT/'last-recipe.json'
    if old.exists():
     try:write_json(backup,loads(old.read_text(encoding='utf-8-sig')))
     except ValueError:pass
    write_json(old,recipe);self.send_json(200,{'ok':True,'recipe':recipe});return
   if self.path=='/api/game':
    value=json.loads(body)
    if not isinstance(value,dict) or value.get('action') not in {'reload','start','off','status'}:raise ValueError('只支持reload/start/off/status。')
    if not self.game_status()['compatibleRuntime']:raise ValueError('当前武器配方需要firecoil 0.2.0；ASTRA-TEST运行库尚未升级。')
    if value['action']=='reload':
     recipe=validate(value.get('recipe'));path=GAME/'config/firecoil/recipe.json'
     if path.exists():
      try:write_json(path.with_name('last-recipe.json'),loads(path.read_text(encoding='utf-8-sig')))
      except ValueError:pass
     write_json(path,recipe)
    nonce=str(uuid.uuid4());write_json(GAME/'config/firecoil/command.json',{'action':value['action'],'nonce':nonce})
    deadline=time.monotonic()+3
    while time.monotonic()<deadline:
     try:
      result=json.loads((GAME/'config/firecoil/result.json').read_text(encoding='utf-8-sig'))
      if result.get('nonce')==nonce:self.send_json(200,result);return
     except (OSError,ValueError):pass
     time.sleep(.08)
    self.send_json(202,{'ok':True,'pending':True,'nonce':nonce,'message':'已写入游戏配置；等待游戏运行库读取。'});return
   self.send_json(404,{'ok':False,'error':'接口不存在。'})
  except (ValueError,UnicodeDecodeError) as e:self.send_json(400,{'ok':False,'error':str(e)})
  except OSError as e:self.send_json(500,{'ok':False,'error':str(e)})

def main():
 global GAME
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--port',type=int,default=8819);parser.add_argument('--game-dir',type=Path,default=GAME);args=parser.parse_args()
 GAME=args.game_dir.resolve()
 # The instance was selected by the user; keep writes confined to that instance.
 if args.game_dir != ROOT / '.unconfigured-game' and not (GAME / 'mods').is_dir():raise ValueError('--game-dir 必须指向包含 mods 的实际游戏目录。')
 server=None
 for port in range(args.port,args.port+21):
  try:server=ThreadingHTTPServer(('127.0.0.1',port),Handler);break
  except OSError:continue
 if server is None:raise OSError('没有可用本地端口。')
 print(f'http://127.0.0.1:{server.server_port}/',flush=True)
 server.serve_forever()

if __name__=='__main__':main()
