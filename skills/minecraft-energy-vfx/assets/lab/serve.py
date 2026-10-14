"""Local editor server with a fixed workspace recipe destination; no arbitrary file API."""
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from pathlib import Path
import json,math,argparse,os
ROOT=Path(__file__).parent.resolve()
FIELDS=json.loads((ROOT/'recipe-schema.json').read_text(encoding='utf-8'))
def validate(r):
 if not isinstance(r,dict) or set(r)!={'schema','name','color','secondary','params'}:raise ValueError('配方字段缺失或未知')
 if r['schema'] not in ['energy-vfx/1','energy-vfx/2']:raise ValueError('不支持的版本')
 if not isinstance(r['name'],str) or not r['name'].strip() or len(r['name'])>80:raise ValueError('名称无效')
 import re
 for k in ['color','secondary']:
  if not isinstance(r[k],str) or not re.fullmatch(r'#[0-9a-fA-F]{6}',r[k]):raise ValueError('色值无效')
 p=r['params']
 required=FIELDS[:39] if r['schema']=='energy-vfx/1' else FIELDS
 if not isinstance(p,dict) or set(p)!={f[0] for f in required}:raise ValueError('参数字段缺失或未知')
 for k,group,label,lo,hi,step,default in required:
  v=p[k]
  if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or not lo<=v<=hi or (step==1 and int(v)!=v):raise ValueError('无效参数：'+k)
 return {**r,'schema':'energy-vfx/2','params':{**{f[0]:f[6] for f in FIELDS},**p}}
class Handler(SimpleHTTPRequestHandler):
 def __init__(self,*a,**kw):super().__init__(*a,directory=str(ROOT),**kw)
 def json(self,data,status=200):
  body=json.dumps(data,ensure_ascii=False).encode('utf-8');self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def do_GET(self):
  if self.path=='/api/recipe':
   saved=ROOT/'current-recipe.json';self.json({'recipe':json.loads(saved.read_text(encoding='utf-8')) if saved.exists() else None});return
  super().do_GET()
 def do_POST(self):
  if self.path!='/api/recipe':self.send_error(404);return
  expected=f'http://127.0.0.1:{self.server.server_port}'
  if self.headers.get('Origin')!=expected or self.headers.get('Content-Type','').split(';')[0]!='application/json':self.json({'error':'同源 JSON 请求才可保存'},403);return
  try:
   length=int(self.headers.get('Content-Length','0'))
   if not 0<length<=100000:raise ValueError('文件大小无效')
   recipe=validate(json.loads(self.rfile.read(length)))
   text=json.dumps(recipe,ensure_ascii=False,indent=2)
   for path in [ROOT/'current-recipe.json',ROOT/'forge/run/config/energyvfx/recipe.json']:
    path.parent.mkdir(parents=True,exist_ok=True);tmp=path.with_suffix('.tmp');tmp.write_text(text,encoding='utf-8');os.replace(tmp,path)
   self.json({'ok':True,'name':recipe['name']})
  except (ValueError,KeyError,TypeError) as e:self.json({'error':str(e)},400)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8796);args=parser.parse_args();ThreadingHTTPServer(('127.0.0.1',args.port),Handler).serve_forever()
