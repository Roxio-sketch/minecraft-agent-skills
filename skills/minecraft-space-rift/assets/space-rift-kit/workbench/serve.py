from functools import partial
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import argparse,webbrowser
def main():
 p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=8777);p.add_argument('--no-browser',action='store_true');p.add_argument('--page',choices=['index.html','giant.html','library.html','five-cases.html'],default='index.html');a=p.parse_args()
 server=ThreadingHTTPServer(('127.0.0.1',a.port),partial(SimpleHTTPRequestHandler,directory=str(Path(__file__).resolve().parent)))
 url=f'http://127.0.0.1:{server.server_port}/{a.page}';print(url,flush=True)
 if not a.no_browser:webbrowser.open(url)
 try:server.serve_forever()
 except KeyboardInterrupt:pass
 finally:server.server_close()
if __name__=='__main__':main()
