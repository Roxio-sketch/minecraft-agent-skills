"""Serve only bundled preview assets on loopback. Python standard library only."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from functools import partial
import argparse
import os
import socket


class PreviewServer(ThreadingHTTPServer):
    # On Windows SO_REUSEADDR permits multiple live listeners on one address.
    allow_reuse_address = os.name != 'nt'
    allow_reuse_port = False

    def server_bind(self):
        if os.name == 'nt':
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        super().server_bind()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8771)
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error('port must be between 0 and 65535 (0 selects a free port)')
    root = Path(__file__).resolve().parents[1] / 'assets' / 'preview'
    class Handler(SimpleHTTPRequestHandler):
        def end_headers(self):
            self.send_header('Cache-Control', 'no-store')
            super().end_headers()
    try:
        server = PreviewServer(('127.0.0.1', args.port), partial(Handler, directory=str(root)))
    except OSError as exc:
        parser.exit(1, f'Cannot open loopback port {args.port}: {exc}\nUse another port.\n')
    print(f'http://127.0.0.1:{server.server_address[1]}/', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
