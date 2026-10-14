"""Keep the local design workbench available after its launching terminal closes."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys

root = Path(__file__).resolve().parent
log_dir = root / "evidence" / "dragon-wrap-20261013"
log_dir.mkdir(parents=True, exist_ok=True)
sys.stdout = sys.stderr = (log_dir / "preview-server.log").open("a", encoding="utf-8", buffering=1)


class PreviewHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    with ThreadingHTTPServer(("127.0.0.1", 8798), partial(PreviewHandler, directory=str(root))) as server:
        server.serve_forever()
