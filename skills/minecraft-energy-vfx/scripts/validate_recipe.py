"""Strict energy-vfx/2 consumer; only exact 39-field v1 objects are upgraded."""
from pathlib import Path
import copy
import json
import math
import re
import sys

LAB = Path(__file__).resolve().parents[1] / 'assets/lab'
MAX_BYTES = 100000


def validate(recipe):
    fields = json.loads((LAB / 'recipe-schema.json').read_text(encoding='utf-8'))
    if len(fields) != 48:
        raise ValueError('Skill schema must contain exactly 48 fields; refresh the v0.2.0 assets')
    if not isinstance(recipe, dict) or set(recipe) != {'schema', 'name', 'color', 'secondary', 'params'}:
        raise ValueError('配方对象必须精确包含 schema、name、color、secondary、params')
    version = recipe['schema']
    if version not in ('energy-vfx/1', 'energy-vfx/2'):
        raise ValueError('不支持的配方版本')
    name = recipe['name']
    if not isinstance(name, str) or not name.strip() or len(name.encode('utf-16-le')) // 2 > 80:
        raise ValueError('名称需为 1–80 个 UTF-16 字符')
    for key in ('color', 'secondary'):
        if not isinstance(recipe[key], str) or re.fullmatch(r'#[0-9a-fA-F]{6}', recipe[key]) is None:
            raise ValueError(key + ' 需为六位 HEX 色值')
    params = recipe['params']
    required = fields[:39] if version == 'energy-vfx/1' else fields
    if not isinstance(params, dict) or set(params) != {field[0] for field in required}:
        raise ValueError('参数缺失或未知；v1 必须精确 39 个字段，v2 必须精确 48 个字段')
    for key, _group, label, minimum, maximum, step, _default in required:
        value = params[key]
        if type(value) not in (int, float) or not math.isfinite(value) or not minimum <= value <= maximum:
            raise ValueError(label + ' 超出范围或类型错误')
        if step == 1 and value != math.floor(value):
            raise ValueError(label + ' 必须为整数')
    upgraded = copy.deepcopy(recipe)
    upgraded['schema'] = 'energy-vfx/2'
    upgraded['params'] = {field[0]: copy.deepcopy(field[6]) for field in fields}
    upgraded['params'].update(params)
    return upgraded


def read_recipe(path):
    path = Path(path)
    if path.stat().st_size > MAX_BYTES:
        raise ValueError('配方超过 100 KB')
    return validate(json.loads(path.read_text(encoding='utf-8-sig')))


if __name__ == '__main__':
    try:
        result = read_recipe(sys.argv[1])
        print(f'有效配方：{result["name"]} · {result["schema"]} · {len(result["params"])} 个参数')
    except (ValueError, OSError, IndexError, OverflowError, UnicodeError) as error:
        print(f'配方被拒绝：{error}', file=sys.stderr)
        sys.exit(1)
