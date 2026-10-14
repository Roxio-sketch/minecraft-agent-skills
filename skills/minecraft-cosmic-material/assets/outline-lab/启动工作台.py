import os
import sys
import time
import threading
import webbrowser
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from urllib.parse import urlsplit, unquote

HOST = "127.0.0.1"
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
INDEX_FILE = os.path.join(BASE_DIR, "index.html")
REQUIRED_FILES = ("preview.js", "lucide.min.js")


def stop_with_error(title, lines):
    print("=" * 70)
    print(title)
    print()
    for line in lines:
        print(line)
    print("=" * 70)
    try:
        input("\n按回车退出...")
    except EOFError:
        pass
    sys.exit(1)


if not os.path.isfile(INDEX_FILE):
    stop_with_error("【启动失败】当前目录没有找到 index.html", [
        "启动器目录：", BASE_DIR,
        "请确保 启动工作台.py 与 index.html 位于同一个目录。",
    ])

missing = [name for name in REQUIRED_FILES
           if not os.path.isfile(os.path.join(BASE_DIR, name))]
if missing:
    stop_with_error("【启动失败】工作台资源不完整", [
        "缺少文件：" + ", ".join(missing),
        "请重新解压完整的 outline-lab 文件夹。",
    ])

# 同目录可选启动参数.txt：?preset=flow 或 preset=flow&play=1
QUERY_FILE = os.path.join(BASE_DIR, "启动参数.txt")
QUERY = ""
if os.path.isfile(QUERY_FILE):
    try:
        with open(QUERY_FILE, "r", encoding="utf-8-sig") as f:
            QUERY = f.read().strip()
        if QUERY and not QUERY.startswith("?"):
            QUERY = "?" + QUERY
    except Exception as exc:
        print("读取 启动参数.txt 失败：", exc)
        QUERY = ""

# 每次启动追加时间戳，避免浏览器保留旧 HTML / JS。
cache_bust = "_boot=" + str(time.time_ns())
QUERY = (QUERY + "&" + cache_bust) if QUERY else ("?" + cache_bust)


class WorkbenchHandler(SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        print(f"[HTTP] {format % args}")

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def do_GET(self):
        parsed = urlsplit(self.path)
        request_path = unquote(parsed.path)
        if request_path not in ("", "/"):
            local_path = self.translate_path(request_path)
            if not os.path.exists(local_path):
                referer = self.headers.get("Referer", "（无）")
                print("\n" + "=" * 70)
                print("【404：工作台请求了不存在的文件】")
                print(f"请求地址：{self.path}")
                print(f"请求资源：{request_path}")
                print(f"服务器实际查找位置：{local_path}")
                print(f"请求来源：{referer}")
                print("可能原因：HTML / JS 路径错误、文件删除、重命名或相对路径层级错误。")
                print("=" * 70 + "\n")
        super().do_GET()


Handler = partial(WorkbenchHandler, directory=BASE_DIR)
try:
    server = ThreadingHTTPServer((HOST, 0), Handler)
    server.daemon_threads = True
except Exception as exc:
    stop_with_error("【HTTP Server 启动失败】", [repr(exc)])

PORT = server.server_address[1]
URL = f"http://{HOST}:{PORT}/index.html{QUERY}"


def open_browser():
    time.sleep(0.5)
    print("\n正在打开工作台：\n" + URL + "\n")
    webbrowser.open_new_tab(URL)


threading.Thread(target=open_browser, daemon=True).start()

print("=" * 70)
print("工作台服务器已启动")
print("=" * 70)
print(f"工作台目录：\n{BASE_DIR}")
print(f"主页：\n{INDEX_FILE}")
print(f"自动分配端口：\n{PORT}")
print(f"浏览器地址：\n{URL}")
if QUERY:
    print(f"已读取启动参数：\n{QUERY}")
print("功能：")
print("  √ 自动使用 py 所在目录")
print("  √ 自动寻找空闲端口")
print("  √ 读取同目录 启动参数.txt")
print("  √ 404 显示实际文件路径")
print("  √ 禁止浏览器缓存开发资源")
print("  √ 每次启动自动更新缓存参数")
print("\n不要关闭这个窗口。按 Ctrl+C 停止当前工作台。")
print("=" * 70)

try:
    server.serve_forever()
except KeyboardInterrupt:
    print("\n正在关闭工作台...")
finally:
    server.server_close()
    print("工作台服务器已关闭。")
