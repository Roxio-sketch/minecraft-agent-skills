"""Apply a fixed recipe and optionally request a narrow client command with a nonce."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import json
import math
import os
import shutil
import time
import uuid
from validate_recipe import read_recipe

VERSION = '0.2.5'
PRESET_COUNT = 20
parser = argparse.ArgumentParser(description='应用 EnergyVFX 配方并通过固定文件桥重新加载；不修改 MCP control_mode。')
parser.add_argument('--recipe')
parser.add_argument('--game-dir', required=True)
parser.add_argument('--install-runtime', action='store_true')
commands = parser.add_mutually_exclusive_group()
commands.add_argument('--reload', action='store_true', help='写入 action=reload 并等待同 nonce 的游戏结果')
commands.add_argument('--action', choices=['reload', 'start', 'off', 'status', 'preset'])
parser.add_argument('--preset', type=int, choices=range(1, PRESET_COUNT + 1))
parser.add_argument('--timeout', type=float, default=30, help='桥结果等待秒数，0.1–120，默认 30')
args = parser.parse_args()
action = 'reload' if args.reload else args.action
if args.preset is not None and action != 'preset':
    parser.error('--preset 只用于 --action preset')
if action == 'preset' and args.preset is None:
    parser.error('--action preset 需要 --preset 1..20')
if not args.recipe and not args.install_runtime and action is None:
    parser.error('至少指定 --recipe、--install-runtime 或 --action/--reload')
if not math.isfinite(args.timeout) or not .1 <= args.timeout <= 120:
    parser.error('--timeout 需为 0.1–120 的有限秒数')

# Validate all input before replacing the last valid recipe or runtime.
recipe = read_recipe(args.recipe) if args.recipe else None
game = Path(args.game_dir).resolve()
if not game.is_dir() or not any((game / key).exists() for key in ('mods', 'options.txt', 'saves')):
    raise SystemExit('请提供实际 Forge 游戏目录，不能使用启动器根目录。')
stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
config = game / 'config/energyvfx'


def write(path, data, backup=True):
    path.parent.mkdir(parents=True, exist_ok=True)
    if backup and path.exists():
        shutil.copy2(path, path.with_name(path.name + '.backup-' + stamp))
    temporary = path.with_name(path.name + '.tmp-energyvfx-' + uuid.uuid4().hex)
    try:
        temporary.write_bytes(data)
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


if args.install_runtime:
    jar = Path(__file__).resolve().parents[1] / f'assets/runtime/energyvfx-{VERSION}.jar'
    if not jar.is_file():
        raise SystemExit('v0.2.5 生产 JAR 缺失，请先构建并打包模板。')
    conflicts = [path for path in (game / 'mods').glob('energyvfx*.jar') if path.name != jar.name]
    unsupported = [path for path in conflicts if path.name not in {'energyvfx-0.1.0.jar', 'energyvfx-0.2.0.jar', 'energyvfx-0.2.1.jar', 'energyvfx-0.2.2.jar', 'energyvfx-0.2.3.jar', 'energyvfx-0.2.4.jar'}]
    if unsupported:
        raise SystemExit('存在未识别的 EnergyVFX JAR，避免重复 mod ID：' + ', '.join(str(path) for path in unsupported))
    # Migrate this Skill's known old version into a fixed backup directory, outside mods.
    for old in conflicts:
        backup = config / 'backups' / (old.name + '.backup-' + stamp)
        backup.parent.mkdir(parents=True, exist_ok=True)
        os.replace(old, backup)
    write(game / 'mods' / jar.name, jar.read_bytes())
    print(f'已安装：{game / "mods" / jar.name}；新 JAR 需重启客户端。')

if recipe is not None:
    path = config / 'recipe.json'
    write(path, json.dumps(recipe, ensure_ascii=False, indent=2).encode('utf-8'))
    print(f'已保存完整配方：{path}')

if action is None:
    print('世界内运行 /energyvfx start 或 /energyvfx reload；脚本 --reload 可请求已运行客户端重新加载。')
    raise SystemExit(0)

nonce = uuid.uuid4().hex
command = {'nonce': nonce, 'action': action}
if action == 'preset':
    command['preset'] = args.preset
write(config / 'command.json', json.dumps(command, ensure_ascii=False).encode('utf-8'), backup=False)
deadline = time.monotonic() + args.timeout
while time.monotonic() < deadline:
    try:
        response = json.loads((config / 'result.json').read_text(encoding='utf-8'))
        if isinstance(response, dict) and response.get('nonce') == nonce:
            if type(response.get('ok')) is not bool:
                raise SystemExit('游戏桥结果缺少有效 ok 布尔字段。')
            if not response['ok']:
                raise SystemExit('游戏拒绝命令：' + str(response.get('error', '未知错误')))
            print('游戏已执行：' + action)
            status = response.get('status')
            if not isinstance(status, dict):
                try:
                    status = json.loads((config / 'status.json').read_text(encoding='utf-8'))
                except (OSError, ValueError):
                    status = None
            if isinstance(status, dict):
                print(json.dumps(status, ensure_ascii=False, indent=2))
            raise SystemExit(0)
    except (OSError, ValueError):
        pass
    time.sleep(.1)
raise SystemExit(f'等待 {args.timeout:g} 秒后未收到本次 nonce 的游戏结果。配方已保留；检查实例是否运行 v0.2.5，首次安装需重启。')
