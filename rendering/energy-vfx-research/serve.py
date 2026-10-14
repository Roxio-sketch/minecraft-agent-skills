"""Local-only research preview. Serves the user's exact reference without copying it."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import json,re,argparse,os
ROOT=Path(__file__).parent.resolve()
VIDEO=Path(os.environ.get('VFX_REFERENCE_VIDEO', str(ROOT / 'reference.mp4')))
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*a,**kw):super().__init__(*a,directory=str(ROOT),**kw)
    def do_GET(self):
        if self.path=='/api/reference':
            data=json.dumps({'available':VIDEO.is_file()}).encode()
            self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
        if self.path=='/reference.mp4':
            if not VIDEO.is_file():self.send_error(404);return
            size=VIDEO.stat().st_size;start=0;end=size-1
            match=re.fullmatch(r'bytes=(\d+)-(\d*)',self.headers.get('Range',''))
            if match:
                start=int(match[1]);end=min(int(match[2]),end) if match[2] else end
                if start>=size or end<start:self.send_error(416);return
            self.send_response(206 if match else 200)
            self.send_header('Content-Type','video/mp4');self.send_header('Accept-Ranges','bytes');self.send_header('Content-Length',str(end-start+1))
            if match:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
            self.end_headers()
            with VIDEO.open('rb') as f:
                f.seek(start);remaining=end-start+1
                try:
                    while remaining:
                        part=f.read(min(1024*1024,remaining))
                        if not part:break
                        self.wfile.write(part);remaining-=len(part)
                except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError):pass
            return
        super().do_GET()
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8794);args=parser.parse_args()
    ThreadingHTTPServer(('127.0.0.1',args.port),Handler).serve_forever()
