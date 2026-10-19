"""Serve the portable VFX gallery on loopback only."""
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
import argparse,webbrowser

def main():
 p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=0);p.add_argument('--no-browser',action='store_true');a=p.parse_args()
 root=Path(__file__).resolve().parents[1]/'assets/vfx-library'
 handler=partial(SimpleHTTPRequestHandler,directory=str(root))
 with ThreadingHTTPServer(('127.0.0.1',a.port),handler) as server:
  url=f'http://127.0.0.1:{server.server_port}/index.html';print(url,flush=True)
  if not a.no_browser:webbrowser.open_new_tab(url)
  try:server.serve_forever()
  except KeyboardInterrupt:pass
if __name__=='__main__':main()
