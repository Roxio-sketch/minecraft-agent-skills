import os
import sys
import time
import threading
import webbrowser
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from urllib.parse import urlsplit, unquote


HOST = "127.0.0.1"

# ============================================================
# 自动锁定启动器所在目录
# ============================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
INDEX_FILE = os.path.join(BASE_DIR, "index.html")


# ============================================================
# 检查 index.html
# ============================================================

if not os.path.isfile(INDEX_FILE):
    print("=" * 70)
    print("【启动失败】当前目录没有找到 index.html")
    print()
    print("启动器目录：")
    print(BASE_DIR)
    print()
    print("请确保：")
    print("启动工作台.py")
    print("index.html")
    print("位于同一个目录。")
    print("=" * 70)

    input("\n按回车退出...")
    sys.exit(1)


# ============================================================
# 可选启动参数
#
# 如果同目录存在：
#
# 启动参数.txt
#
# 可以写：
#
# ?family=cosmic
#
# 或：
#
# ?v=2.2&preset=ribbons&play=1
#
# 没有这个文件则直接打开主页。
# ============================================================

QUERY_FILE = os.path.join(BASE_DIR, "启动参数.txt")
QUERY = ""

if os.path.isfile(QUERY_FILE):
    try:
        with open(QUERY_FILE, "r", encoding="utf-8-sig") as f:
            QUERY = f.read().strip()

        if QUERY and not QUERY.startswith("?"):
            QUERY = "?" + QUERY

    except Exception as e:
        print("读取 启动参数.txt 失败：")
        print(e)
        QUERY = ""


# ============================================================
# HTTP Handler
# ============================================================

class WorkbenchHandler(SimpleHTTPRequestHandler):

    def log_message(self, format, *args):
        message = format % args
        print(f"[HTTP] {message}")

    def end_headers(self):
        # 开发工作台禁用缓存，修改 JS / GLSL / JSON 后刷新即可看到新版本
        self.send_header(
            "Cache-Control",
            "no-store, no-cache, must-revalidate, max-age=0"
        )
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")

        super().end_headers()

    def do_GET(self):
        parsed = urlsplit(self.path)

        request_path = unquote(parsed.path)

        local_path = self.translate_path(request_path)

        # "/" 会自动对应 index.html，因此不当作缺失
        if request_path != "/" and not os.path.exists(local_path):

            print()
            print("=" * 70)
            print("【404：工作台请求了不存在的文件】")
            print()
            print("请求地址：")
            print(f"  {self.path}")
            print()
            print("请求资源：")
            print(f"  {request_path}")
            print()
            print("服务器实际查找位置：")
            print(f"  {local_path}")

            referer = self.headers.get("Referer")

            if referer:
                print()
                print("请求来源：")
                print(f"  {referer}")

            print()
            print("可能原因：")
            print("  1. HTML / JS 中的文件路径写错")
            print("  2. 文件被删除")
            print("  3. 文件被重命名")
            print("  4. 相对路径层级错误")
            print("  5. 工作台源码仍然引用旧文件")
            print("=" * 70)
            print()

        super().do_GET()


# ============================================================
# 让 Windows 自动选择空闲端口
# ============================================================

Handler = partial(
    WorkbenchHandler,
    directory=BASE_DIR
)

try:
    server = ThreadingHTTPServer(
        (HOST, 0),
        Handler
    )

    # 多个资源可同时响应
    server.daemon_threads = True

except Exception as e:

    print("=" * 70)
    print("【HTTP Server 启动失败】")
    print()
    print(repr(e))
    print("=" * 70)

    input("\n按回车退出...")
    sys.exit(1)


PORT = server.server_address[1]

URL = f"http://{HOST}:{PORT}/{QUERY}"


# ============================================================
# 打开浏览器
# ============================================================

def open_browser():
    time.sleep(0.5)

    print()
    print("正在打开工作台：")
    print(URL)
    print()

    webbrowser.open_new_tab(URL)


threading.Thread(
    target=open_browser,
    daemon=True
).start()


# ============================================================
# 显示信息
# ============================================================

print("=" * 70)
print("工作台服务器已启动")
print("=" * 70)

print()
print("工作台目录：")
print(BASE_DIR)

print()
print("主页：")
print(INDEX_FILE)

print()
print("自动分配端口：")
print(PORT)

print()
print("浏览器地址：")
print(URL)

if QUERY:
    print()
    print("已读取启动参数：")
    print(QUERY)

print()
print("功能：")
print("  √ 自动使用 py 所在目录")
print("  √ 自动寻找空闲端口")
print("  √ 多个工作台可以同时运行")
print("  √ 不需要 npm / npx")
print("  √ 不需要修改固定端口")
print("  √ 404 自动显示实际文件路径")
print("  √ 显示资源请求来源")
print("  √ 禁止浏览器缓存开发资源")
print()
print("不要关闭这个窗口。")
print("按 Ctrl+C 停止当前工作台。")
print("=" * 70)


# ============================================================
# 正式运行
# ============================================================

try:
    server.serve_forever()

except KeyboardInterrupt:
    print()
    print("正在关闭工作台...")

finally:
    server.server_close()
    print("工作台服务器已关闭。")