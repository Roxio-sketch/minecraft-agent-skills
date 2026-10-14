"""双击启动本聊天的水波参数工作台，支持本地参数导出。"""
from pathlib import Path
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlencode, urlsplit
import argparse
import json
import math
import threading
import time
import webbrowser

WORKSPACE = Path(__file__).resolve().parent
REQUIRED = ("index.html", "wave-core.js", "app.js", "recipe.js")
LIMITS = {"width": (2, 32), "speed": (0.1, 1), "damping": (0.9, 0.9995),
          "radius": (0.05, 2), "strength": (-0.5, 0.5), "maxHeight": (0.05, 1),
          "opacity": (0, 1), "normalStrength": (0, 4), "shininess": (8, 256),
          "reflection": (0, 2), "rainRate": (0, 20)}


def validate_recipe(recipe):
    if not isinstance(recipe, dict):
        raise ValueError("参数必须为 JSON 对象")
    for key, choices in {"schema": (1,), "grid": (33, 65, 97, 129),
                         "solver": ("velocity", "history"),
                         "boundary": ("absorb", "reflect")}.items():
        if isinstance(recipe.get(key), bool) or recipe.get(key) not in choices:
            raise ValueError(f"参数 {key} 无效")
    color = recipe.get("color", "")
    if not isinstance(color, str) or len(color) != 7 or not color.startswith("#") or any(c not in "0123456789abcdefABCDEF" for c in color[1:]):
        raise ValueError("颜色格式应为 #RRGGBB")
    for key, (minimum, maximum) in LIMITS.items():
        value = recipe.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not minimum <= value <= maximum:
            raise ValueError(f"{key} 应在 {minimum}–{maximum} 之间")
    return {key: recipe[key] for key in ("schema", "grid", "solver", "boundary", "color", *LIMITS)}


def make_handler(root):
    class Handler(SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(root), **kwargs)

        def end_headers(self):
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            super().end_headers()

        def do_POST(self):
            if urlsplit(self.path).path != "/api/export":
                self.send_error(404)
                return
            port = self.server.server_port
            if self.headers.get("Origin") not in (f"http://127.0.0.1:{port}", f"http://localhost:{port}"):
                self.send_error(403)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 32768:
                    raise ValueError("参数文件大小无效")
                recipe = validate_recipe(json.loads(self.rfile.read(length).decode("utf-8")))
                folder = root / "exports"
                folder.mkdir(exist_ok=True)
                target = folder / f"recipe-{time.time_ns()}.json"
                target.write_text(json.dumps(recipe, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                body = json.dumps({"path": str(target), "url": f"/exports/{target.name}"}, ensure_ascii=False).encode("utf-8")
                self.send_response(200)
            except (ValueError, OSError, UnicodeError) as error:
                body = json.dumps({"error": str(error)}, ensure_ascii=False).encode("utf-8")
                self.send_response(400)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
    return Handler


def main():
    parser = argparse.ArgumentParser(description="启动本聊天的水波参数工作台")
    parser.add_argument("--no-browser", action="store_true", help="只启动服务，不打开浏览器")
    args = parser.parse_args()
    adjacent = Path(__file__).resolve().parent
    root = adjacent if all((adjacent / name).is_file() for name in REQUIRED) else WORKSPACE
    missing = [name for name in REQUIRED if not (root / name).is_file()]
    if missing:
        raise RuntimeError(f"工作台资源不完整：{root}\n缺少：{', '.join(missing)}")
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(root))
    server.daemon_threads = True
    url = f"http://127.0.0.1:{server.server_port}/index.html?{urlencode({'_boot': time.time_ns()})}"
    print("水波材质 · 参数工作台", flush=True)
    print(f"工作台目录：{root}\n浏览器地址：{url}", flush=True)
    print("支持水波预设、实时调参和导入导出。导出参数保存在工作台 exports 目录。\n请保留此窗口；Ctrl+C 停止服务。", flush=True)
    if not args.no_browser:
        threading.Thread(target=lambda: webbrowser.open_new_tab(url), daemon=True).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n水波工作台已关闭。")
    finally:
        server.server_close()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"启动失败：{error}")
        try:
            input("按回车退出……")
        except EOFError:
            pass
        raise SystemExit(1)