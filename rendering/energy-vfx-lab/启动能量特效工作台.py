"""双击启动本聊天的能量特效编辑工作台，保留配方保存接口。"""
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.parse import urlencode
import argparse
import importlib.util
import threading
import time
import webbrowser

WORKSPACE = Path(__file__).resolve().parent
REQUIRED = ("index.html", "serve.py", "editor.js", "schema.js", "shaders.js", "aura.js", "recipe-schema.json")


def main():
    parser = argparse.ArgumentParser(description="启动能量特效编辑工作台")
    parser.add_argument("--no-browser", action="store_true", help="只启动服务，不打开浏览器")
    args = parser.parse_args()
    adjacent = Path(__file__).resolve().parent
    root = adjacent if all((adjacent / name).is_file() for name in REQUIRED) else WORKSPACE
    missing = [name for name in REQUIRED if not (root / name).is_file()]
    if missing:
        raise RuntimeError(f"工作台资源不完整：{root}\n缺少：{', '.join(missing)}")
    spec = importlib.util.spec_from_file_location("energy_workbench_server", root / "serve.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    class Handler(module.Handler):
        def end_headers(self):
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            self.send_header("Pragma", "no-cache")
            self.send_header("Expires", "0")
            super().end_headers()

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    server.daemon_threads = True
    query_file = adjacent / "启动参数.txt"
    query = query_file.read_text(encoding="utf-8-sig").strip().lstrip("?") if query_file.exists() else ""
    query = (query + "&" if query else "") + urlencode({"_boot": time.time_ns()})
    url = f"http://127.0.0.1:{server.server_port}/index.html?{query}"
    print("能量特效 · 编辑工作台", flush=True)
    print(f"工作台目录：{root}\n浏览器地址：{url}", flush=True)
    print("支持预设、参数调整及保存当前配方。\n请保留此窗口；Ctrl+C 停止服务。", flush=True)
    if not args.no_browser:
        def open_browser():
            time.sleep(0.5)
            webbrowser.open_new_tab(url)
        threading.Thread(target=open_browser, daemon=True).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n工作台已关闭。")
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