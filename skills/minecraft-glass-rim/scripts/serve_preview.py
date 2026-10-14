import argparse
from functools import partial
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=8783)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1] / 'assets'
server = ThreadingHTTPServer(('127.0.0.1', args.port), partial(SimpleHTTPRequestHandler, directory=str(root)))
print(f'http://127.0.0.1:{server.server_port}/lab/', flush=True)
server.serve_forever()
