"""Serve the bundled, offline material workbench on loopback only."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse
import hashlib
import json
import socket
import sys
from urllib.parse import urlsplit


APPLICATION = "minecraft-cosmic-material"
ASSET_FILES = ("index.html", "material.glsl", "preview.js", "recipes.json")
ASSETS = Path(__file__).resolve().parent.parent / "assets" / "authoring-lab"
LAB_ASSETS = {"cosmic": ASSETS, "fantasy": ASSETS.with_name("fantasy-lab")}


def asset_digest(root=ASSETS):
    digest = hashlib.sha256()
    for name in ASSET_FILES:
        digest.update(name.encode("utf-8") + b"\0")
        digest.update((root / name).read_bytes())
        digest.update(b"\0")
    if root.name == "fantasy-lab":
        digest.update(b"ocean-spectrum.js\0")
        digest.update((root / "ocean-spectrum.js").read_bytes())
        digest.update(b"\0")
        for name in ASSET_FILES:
            digest.update(("cosmic/" + name).encode("utf-8") + b"\0")
            digest.update((root.with_name("authoring-lab") / name).read_bytes())
            digest.update(b"\0")
    return digest.hexdigest()


class PreviewServer(ThreadingHTTPServer):
    # HTTPServer's address reuse is unsafe for exclusive listeners on Windows.
    allow_reuse_address = False
    allow_reuse_port = False

    def server_bind(self):
        if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        super().server_bind()


class PreviewHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_GET(self):
        path = urlsplit(self.path).path
        if self.server.lab == "fantasy" and (path == "/cosmic" or path.startswith("/cosmic/")):
            # The fantasy workbench embeds the cosmic workbench in an iframe.
            # Treat the mount point and a trailing slash as its index instead
            # of letting SimpleHTTPRequestHandler look for a real directory.
            name = path[len("/cosmic"):].lstrip("/") or "index.html"
            if name not in ASSET_FILES:
                self.send_error(404)
                return
            source = Path(self.directory).with_name("authoring-lab") / name
            body = source.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", self.guess_type(str(source)) + "; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if urlsplit(self.path).path == "/__cosmic__/health":
            record = {"application": APPLICATION, "protocol": 1,
                      "assetDigest": asset_digest(Path(self.directory))}
            if self.server.lab != "cosmic":
                record["lab"] = self.server.lab
            body = json.dumps(record).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()


def create_server(port=0, assets=ASSETS, lab="cosmic"):
    asset_digest(assets)  # Diagnose an incomplete package before binding.
    server = PreviewServer(("127.0.0.1", port), partial(PreviewHandler, directory=str(assets)))
    server.lab = lab
    return server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=0,
                        help="0 lets the OS allocate a free port (default).")
    parser.add_argument("--json", action="store_true", help="Print a machine-readable startup record.")
    parser.add_argument("--lab", choices=tuple(LAB_ASSETS), default="cosmic")
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error("port must be between 0 and 65535")
    try:
        server = create_server(args.port, LAB_ASSETS[args.lab], args.lab)
    except OSError as error:
        print(f"Cannot bind the requested preview port {args.port}: {error}. "
              "Use --port 0 or another port; no existing server was stopped.", file=sys.stderr)
        return 2
    with server:
        url = f"http://127.0.0.1:{server.server_address[1]}/"
        if args.json:
            print(json.dumps({"application": APPLICATION, "lab": args.lab, "url": url,
                              "assetDigest": asset_digest(LAB_ASSETS[args.lab])}), flush=True)
        else:
            print(f"{args.lab.title()} authoring preview: {url}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
