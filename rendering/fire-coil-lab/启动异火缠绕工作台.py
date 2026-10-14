"""双击打开异火缠绕参数编辑台。"""
from pathlib import Path
from http.server import ThreadingHTTPServer
import argparse,importlib.util,threading,webbrowser

WORKSPACE = Path(__file__).resolve().parent
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--no-browser',action='store_true');args=parser.parse_args()
 adjacent=Path(__file__).resolve().parent
 root=adjacent if (adjacent/'serve.py').is_file() else WORKSPACE
 required=('index.html','editor.js','preview.js','geometry.js','schema.js','shaders.js','serve.py','recipe.py')
 missing=[name for name in required if not (root/name).is_file()]
 if missing:raise RuntimeError('工作台资源缺失：'+', '.join(missing))
 spec=importlib.util.spec_from_file_location('firecoil_server',root/'serve.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 server=ThreadingHTTPServer(('127.0.0.1',0),module.Handler);server.daemon_threads=True
 url=f'http://127.0.0.1:{server.server_port}/'
 print('异火缠绕 · 参数编辑台\n'+url+'\n保留此窗口；Ctrl+C停止服务。',flush=True)
 if not args.no_browser:threading.Thread(target=lambda:webbrowser.open_new_tab(url),daemon=True).start()
 try:server.serve_forever()
 except KeyboardInterrupt:pass
 finally:server.server_close()
if __name__=='__main__':
 try:main()
 except Exception as error:
  print('启动失败：'+str(error))
  try:input('按回车退出…')
  except EOFError:pass
