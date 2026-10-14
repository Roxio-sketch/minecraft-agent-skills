#!/usr/bin/env python3
"""Local-only, dependency-free workbench server with validated resource export."""
from __future__ import annotations
import argparse
import json
import pathlib
import subprocess
import sys
import tempfile
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = pathlib.Path(__file__).resolve().parent


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"重复 JSON 字段：{key}")
        result[key] = value
    return result


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()

    def do_POST(self):
        if self.path != "/api/export":
            self.send_error(404)
            return
        origin = self.headers.get("Origin")
        host = self.headers.get("Host", "")
        if origin and origin not in (f"http://{host}", f"https://{host}"):
            self.send_error(403, "Same-origin requests only")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 65536:
                raise ValueError("配方大小须在 1 至 65536 字节之间")
            data = json.loads(
                self.rfile.read(length).decode("utf-8-sig"), object_pairs_hook=strict_object,
                parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"非有限 JSON 数值：{value}")),
            )
            script = ROOT.parent / "scripts" / "recipe.py"
            if not script.is_file():
                raise RuntimeError("资源导出脚本尚未就绪：scripts/recipe.py")
            with tempfile.TemporaryDirectory(prefix="world-volume-export-") as temp:
                tempdir = pathlib.Path(temp)
                recipe_path = tempdir / "recipe.json"
                outdir = tempdir / "resources"
                recipe_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
                result = subprocess.run(
                    [sys.executable, str(script), "export", "--input", str(recipe_path), "--out", str(outdir)],
                    capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30,
                )
                if result.returncode != 0:
                    raise ValueError((result.stderr or result.stdout or "配方验证失败").strip())
                archive = outdir / "world-volume-resources.zip"
                if not archive.is_file():
                    raise RuntimeError("导出脚本未生成 world-volume-resources.zip")
                payload = archive.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "application/zip")
            self.send_header("Content-Disposition", 'attachment; filename="world-volume-resources.zip"')
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except (ValueError, UnicodeError, json.JSONDecodeError) as exc:
            self.respond_error(400, str(exc))
        except (RuntimeError, OSError, subprocess.TimeoutExpired) as exc:
            self.respond_error(503, str(exc))

    def respond_error(self, status, message):
        payload = json.dumps({"error": message}, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8774)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"世界体积环境工作台：http://127.0.0.1:{args.port}/", flush=True)
    print("按 Ctrl+C 停止；仅监听本机。", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
