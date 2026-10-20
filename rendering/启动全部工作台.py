"""本项目 HTML 工作台统一入口。双击，输入编号，再回车。"""
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import argparse
import importlib.util
import sys
import threading
import time
import webbrowser

PROJECT = Path(__file__).resolve().parent


# 名称、资源服务根目录、主页、专用服务类型。包含正式工作台、独立子工作台、研究资料台和基础验证预览。
ENTRIES = [['天空', 'cosmic-sky-lab/workbench', 'index.html', 'sky'], ['体积云雾', 'volumetric-environment-lab/workbench', 'index.html', 'volume'], ['闪电', '../skills/minecraft-lightning/assets/workbench', 'index.html', 'static'], ['黑洞', '../skills/minecraft-black-hole/assets/preview', 'index.html', 'static'], ['能量特效', 'energy-vfx-lab', 'index.html', 'energy'], ['水波', 'water-ripple-lab/workbench', 'index.html', 'water'], ['拖尾与光束', 'trail-beam-lab/workbench', 'index.html', 'static'], ['奇幻材质', '../skills/minecraft-cosmic-material/assets/fantasy-lab', 'index.html', 'fantasy'], ['星空材质', '../skills/minecraft-cosmic-material/assets/authoring-lab', 'index.html', 'cosmic'], ['描边', '../skills/minecraft-cosmic-material/assets/outline-lab', 'index.html', 'static'], ['能量护盾', '../skills/minecraft-stylised-shield/assets/forge-1.20.1/workbench', 'index.html', 'static'], ['区域材质', '../skills/minecraft-local-environment-material/assets/workbench', 'index.html', 'static'], ['碎晶折射', '../skills/minecraft-crystal-refraction/assets/lab', 'index.html', 'static'], ['玻璃边光', '../skills/minecraft-glass-rim/assets', 'lab/index.html', 'static'], ['原画线稿', '../skills/minecraft-illustration-render/assets/forge-1.20.1-lab', 'workbench/index.html', 'static'], ['天空预设', 'cosmic-sky-lab/workbench', 'sky-library.html', 'sky'], ['星空盾牌', '../skills/minecraft-cosmic-material/assets/shield-presets', 'index.html', 'static'], ['渡劫黑云', 'volumetric-environment-lab/workbench', 'tribulation/index.html', 'volume'], ['龙卷风', 'volumetric-environment-lab/workbench', 'vortex/index.html', 'volume'], ['能量研究', 'energy-vfx-research', 'index.html', 'research'], ['白洞研究', 'white-hole-research-20261007', 'index.html', 'static'], ['水龙卷研究', 'research/github-water-vortex-20261011', 'index.html', 'static'], ['异空间验证', '../skills/minecraft-local-subscene/assets/gpu-probe', 'index.html', 'static'], ['动态照明预览', '../skills/minecraft-dynamic-lighting/assets/workbench', 'index.html', 'static'], ['Bloom 预览', '../skills/minecraft-selective-bloom/assets/workbench', 'index.html', 'static'], ['粒子预览', '../skills/minecraft-procedural-particles/assets/workbench', 'index.html', 'static'], ['动画适配预览', '../skills/minecraft-animated-material-adapter/assets/workbench', 'index.html', 'static'], ['御体流光', 'spirit-guard-glint-lab', 'index.html', 'static'], ['异火缠绕', 'fire-coil-lab', 'index.html', 'firecoil'], ['炼丹炉特效', 'alchemy-vfx-lab', 'index.html', 'alchemy'], ['修仙特效参考库', 'energy-vfx-research/修仙特效参考库-20261013', 'index.html', 'static'], ['彩色流光工作台', '../skills/minecraft-colored-glint/assets/workbench', 'index.html', 'static'], ['遁光与飞行阶段', '../skills/minecraft-dunguang-flight/assets/prototype', 'index.html', 'static']]

# Portable project gallery plus nine independently selectable effect previews.
ENTRIES += [['仙途特效库', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'index.html', 'static']]
ENTRIES += [['刀光与能量束', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'trails/index.html', 'static'], ['灵气与能量场', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'energy/index.html', 'static'], ['护盾与破碎', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'shield/index.html', 'static'], ['闪电与雷劫', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'lightning/index.html', 'static'], ['空间裂隙', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'rift/index.html', 'static'], ['金白剑气', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'sword-qi/index.html', 'static'], ['焚空裂隙 · 视频参考', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'cataclysm-rift/index.html', 'static'], ['五行气海', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'qi-sea/index.html', 'static'], ['九霄剑决与万剑归宗', '../skills/minecraft-xiantu-vfx-library/assets/vfx-library', 'sword-formations/index.html', 'static']]


# Self-contained Skill workbenches and the previously omitted study pages.
ENTRIES += [['程序化空间裂缝', '../skills/minecraft-space-rift/assets/space-rift-kit/workbench', 'index.html', 'static'], ['闪电 fBm 研究', '../skills/minecraft-lightning/assets/workbench', 'fbm-study.html', 'static']]


def load_module(path):
    name = '_workbench_' + str(abs(hash(str(path))))
    if name in sys.modules:
        return sys.modules[name]
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class StaticHandler(SimpleHTTPRequestHandler):
    extensions_map = {**SimpleHTTPRequestHandler.extensions_map,
                      '.js': 'text/javascript', '.glsl': 'text/plain; charset=utf-8'}

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, *args):
        pass


def create_server(entry):
    name, folder, page, kind = entry
    root = PROJECT / folder
    if not (root / page).is_file():
        raise FileNotFoundError(f'找不到工作台：{root / page}')
    if kind in ('fantasy', 'cosmic'):
        module = load_module(PROJECT.parent / 'skills/minecraft-cosmic-material/scripts/serve_authoring.py')
        server = module.create_server(0, root, kind)
    else:
        scripts = {'sky': root / 'launch.py', 'volume': root / 'serve.py',
                   'energy': root / 'serve.py', 'alchemy': root / 'serve.py', 'firecoil': root / 'serve.py', 'water': root / 'serve.py',
                   'research': root / 'serve.py'}
        if kind == 'static':
            handler = partial(StaticHandler, directory=str(root))
        else:
            module = load_module(scripts[kind])
            handler = module.make_handler(root) if kind in ('sky', 'water') else module.Handler
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler)
    server.daemon_threads = True
    url = f'http://127.0.0.1:{server.server_port}/{page}?_boot={time.time_ns()}'
    return server, url


def check_all():
    from urllib.request import urlopen
    for i, entry in enumerate(ENTRIES, 1):
        server, url = create_server(entry)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with urlopen(url, timeout=5) as response:
                assert response.status == 200
                body = response.read().lower()
                assert b'<html' in body or b'<!doctype html' in body
            print(f'[通过] {i:2d}. {entry[0]}', flush=True)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='验证所有入口，不打开浏览器')
    args = parser.parse_args()
    if args.check:
        check_all()
        return
    running = {}
    print('\n========== 着色器渲染 · 工作台总入口 ==========')
    for i, entry in enumerate(ENTRIES, 1):
        print(f' {i:2d}. {entry[0]}')
    print('\n输入编号后回车即可打开，可连续选择多个工作台。')
    print('输入 0 退出。使用期间请保留此窗口。\n')
    try:
        while True:
            choice = input('请输入工作台编号：').strip()
            if choice == '0':
                break
            if not choice.isdecimal() or not 1 <= int(choice) <= len(ENTRIES):
                print(f'请输入 1–{len(ENTRIES)}，或输入 0 退出。')
                continue
            index = int(choice) - 1
            try:
                if index not in running:
                    server, url = create_server(ENTRIES[index])
                    threading.Thread(target=server.serve_forever, daemon=True).start()
                    running[index] = (server, url)
                url = running[index][1]
                webbrowser.open_new_tab(url)
                print(f'已打开：{ENTRIES[index][0]}\n地址：{url}\n')
            except Exception as error:
                print(f'启动失败：{error}\n可重新选择其他编号。')
    except (KeyboardInterrupt, EOFError):
        pass
    finally:
        for server, _ in running.values():
            server.shutdown()
            server.server_close()
        print('\n工作台服务已关闭。')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'启动失败：{error}')
        try:
            input('按回车退出……')
        except EOFError:
            pass
        sys.exit(1)
