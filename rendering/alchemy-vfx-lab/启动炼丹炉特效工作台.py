"""双击直接打开实际炉体的炼丹炉参数编辑台。"""
from pathlib import Path
from http.server import ThreadingHTTPServer
import argparse,importlib.util,threading,webbrowser
WORKSPACE = Path(__file__).resolve().parent
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--no-browser',action='store_true');a=parser.parse_args()
 local=Path(__file__).resolve().parent;root=local if (local/'serve.py').is_file() else WORKSPACE
 spec=importlib.util.spec_from_file_location('alchemy_server',root/'serve.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 server=ThreadingHTTPServer(('127.0.0.1',0),module.Handler);server.daemon_threads=True
 url=f'http://127.0.0.1:{server.server_port}/';print('炼丹炉特效参数编辑台\n'+url+'\n保留此窗口；Ctrl+C停止。',flush=True)
 if not a.no_browser:threading.Thread(target=lambda:webbrowser.open_new_tab(url),daemon=True).start()
 try:server.serve_forever()
 except KeyboardInterrupt:pass
 finally:server.server_close()
if __name__=='__main__':main()
