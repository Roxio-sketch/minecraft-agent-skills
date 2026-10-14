"""Strict shared flame ramps. Applying a ramp returns a copy; it never writes a recipe."""
from __future__ import annotations
import json
from pathlib import Path
from typing import Any

CATALOG_SCHEMA = 'flame-palettes/1'
PALETTE_IDS = tuple(f'slot{i}' for i in range(1, 10))
COLOR_KEYS = ('fireCore', 'fireMid', 'fireEdge')
ITEM_KEYS = ('id', 'name', 'style', *COLOR_KEYS)
MAPS = {
    'alchemy-vfx/2': {'fireCore':'fireCore','fireMid':'fireMid','fireEdge':'fireEdge'},
    'alchemy-vfx/1': dict(zip(COLOR_KEYS, COLOR_KEYS)),
    'fire-coil/1': dict(zip(COLOR_KEYS, ('coreColor', 'midColor', 'edgeColor'))),
    'fire-coil/2': dict(zip(COLOR_KEYS, ('coreColor', 'midColor', 'edgeColor'))),
}

def _object(value: Any, label: str) -> None:
    if not isinstance(value, dict):
        raise ValueError(f'{label}须为 JSON 对象。')

def _exact(value: dict, keys: tuple[str, ...], label: str) -> None:
    unknown, missing = set(value) - set(keys), set(keys) - set(value)
    if unknown or missing:
        raise ValueError(f'{label}字段不匹配：多余 {sorted(unknown)}；缺少 {sorted(missing)}。')

def _colour(value: Any, key: str) -> str:
    if not isinstance(value, str) or len(value) != 7 or value[0] != '#' or any(c not in '0123456789abcdefABCDEF' for c in value[1:]):
        raise ValueError(f'{key}须为 #RRGGBB。')
    return value.lower()

def validate_palette(value: Any) -> dict:
    _object(value, '色板')
    _exact(value, ITEM_KEYS, '色板')
    if value['id'] not in PALETTE_IDS:
        raise ValueError('色板 ID 须为 slot1–slot9。')
    result = {'id': value['id']}
    for key in ('name', 'style'):
        item = value[key]
        if not isinstance(item, str) or not item.strip() or len(item) > 120:
            raise ValueError(f'{key}须为 1–120 字的文本。')
        result[key] = item.strip()
    for key in COLOR_KEYS:
        result[key] = _colour(value[key], key)
    if len({result[k] for k in COLOR_KEYS}) != 3:
        raise ValueError('火核、中层和外焰须保留三个不同色阶。')
    return result

def validate_catalog(value: Any) -> dict:
    _object(value, '色板目录')
    _exact(value, ('schema', 'defaultId', 'palettes'), '色板目录')
    if value['schema'] != CATALOG_SCHEMA:
        raise ValueError(f'只支持 {CATALOG_SCHEMA}。')
    if value['defaultId'] not in PALETTE_IDS:
        raise ValueError('默认色板 ID 须为 slot1–slot9。')
    if not isinstance(value['palettes'], list) or len(value['palettes']) != 9:
        raise ValueError('须提供完整九种色板。')
    cleaned = [validate_palette(p) for p in value['palettes']]
    by_id = {p['id']: p for p in cleaned}
    if len(by_id) != 9 or any(i not in by_id for i in PALETTE_IDS):
        raise ValueError('九个色板 ID 须各出现一次。')
    return {'schema': CATALOG_SCHEMA, 'defaultId': value['defaultId'], 'palettes': [by_id[i] for i in PALETTE_IDS]}

def load_catalog(path: str | Path | None = None) -> dict:
    source = Path(path) if path is not None else Path(__file__).with_name('nine-flames.json')
    return validate_catalog(json.loads(source.read_text(encoding='utf-8')))

def get_palette(catalog: Any, palette_id: str | None = None) -> dict:
    clean = validate_catalog(catalog)
    palette_id = clean['defaultId'] if palette_id is None else palette_id
    found = next((p for p in clean['palettes'] if p['id'] == palette_id), None)
    if found is None:
        raise ValueError(f'未知色板 ID：{palette_id}')
    return dict(found)

def apply_palette(recipe: Any, palette: Any) -> dict:
    _object(recipe, '特效配方')
    schema = recipe.get('schema')
    if not isinstance(schema, str) or schema not in MAPS:
        raise ValueError(f'不支持的配方 schema：{schema}')
    clean = validate_palette(palette)
    result = dict(recipe)
    for source, target in MAPS[schema].items():
        _colour(recipe.get(target), target)
        result[target] = clean[source]
    return result

def apply_palette_id(recipe: Any, catalog: Any, palette_id: str | None = None) -> dict:
    return apply_palette(recipe, get_palette(catalog, palette_id))
