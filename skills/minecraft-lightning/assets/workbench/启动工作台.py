import http.server
import socketserver
import webbrowser
import os
import threading
import time

PORT = 8771
URL = f"http://127.0.0.1:{PORT}/?v=2.2&preset=ribbons&play=1"

# 永远使用脚本自己所在的目录
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(BASE_DIR)

class Handler(http.server.SimpleHTTPRequestHandler):
    pass

def open_browser():
    time.sleep(0.8)
    webbrowser.open(URL)

threading.Thread(target=open_browser, daemon=True).start()

print("=" * 60)
print("工作台服务器已启动")
print(f"目录：{BASE_DIR}")
print(f"地址：{URL}")
print("")
print("不要关闭这个窗口。")
print("关闭窗口或按 Ctrl+C 即可停止服务器。")
print("=" * 60)

with socketserver.TCPServer(("127.0.0.1", PORT), Handler) as httpd:
    httpd.serve_forever()
