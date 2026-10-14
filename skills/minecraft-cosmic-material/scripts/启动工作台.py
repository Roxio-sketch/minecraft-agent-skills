"""从 skill 自带资源启动奇幻材质与内部星空工作台。"""
from pathlib import Path
import argparse
import threading
import webbrowser
from serve_authoring import create_server, LAB_ASSETS

BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-browser', action='store_true', help='仅启动服务，不自动打开浏览器')
    parser.add_argument('--port', type=int, default=0, help='默认自动选择空闲端口')
    args = parser.parse_args()
    query_file = BASE_DIR / '启动参数.txt'
    query = query_file.read_text(encoding='utf-8-sig').strip() if query_file.exists() else ''
    if query and not query.startswith('?'):
        query = '?' + query
    assets = LAB_ASSETS['fantasy']
    with create_server(args.port, assets, 'fantasy') as server:
        server.daemon_threads = True
        url = f'http://127.0.0.1:{server.server_address[1]}/{query}'
        print('工作台服务器已启动', flush=True)
        print(f'奇幻材质 HTML：{assets / "index.html"}', flush=True)
        print(f'内部星空 HTML：{LAB_ASSETS["cosmic"] / "index.html"}', flush=True)
        print(f'浏览器地址：{url}', flush=True)
        print('在页面顶部切换奇幻材质 / 内部星空。按 Ctrl+C 停止服务。', flush=True)
        if not args.no_browser:
            threading.Thread(target=webbrowser.open_new_tab, args=(url,), daemon=True).start()
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print('\n工作台服务器已关闭。')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
