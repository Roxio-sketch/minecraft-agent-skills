# 九种异火共用色板

`nine-flames.json` 是炉底火焰、身体缠绕和武器附着火焰的共用颜色来源。九个稳定 ID 为 `slot1` 至 `slot9`，顺序对应项目的赤炎流火、熔金淬火、枯骨冷火、青木灵火、碧海沧火、紫霄雷火、大日圣火、九幽冥火、涅槃神火。每个色板包含火核、中层、外焰三个可编辑颜色。

色板取自真实项目 `weapon_flames/<异火ID>.json` 的 coreColor/bodyColor/edgeColor；文件路径和 SHA 记录在 `source-provenance.json`，没有混用 SpiritFirePalette 的注册基础色。色板只改变颜色，不覆盖已调好的火形、扰动、发射率、运动与采样参数。涅槃在这里使用原文件的青碧静态起点；宿主的轮转色仍需由宿主运行逻辑驱动。九幽保持项目的深紫颜色，不自动提亮。

浏览器模块无需依赖：

```js
import { validateCatalog, applyPaletteId } from './palettes.mjs';
const catalog = validateCatalog(await (await fetch('./nine-flames.json')).json());
const edited = applyPaletteId(existingRecipe, catalog, 'slot5');
// edited 应再由工作台自身 validateRecipe 验证并保存。
```

Python：

```python
from palettes import load_catalog, apply_palette_id
edited = apply_palette_id(existing_recipe, load_catalog(), 'slot5')
# edited 应再由目标 recipe.py 的 validate_recipe 验证并保存。
```

严格映射：

| 色板字段 | 炼丹炉 alchemy-vfx/1 或 /2 | 缠绕/武器 fire-coil/1 或 /2 |
| --- | --- | --- |
| fireCore | fireCore | coreColor |
| fireMid | fireMid | midColor |
| fireEdge | fireEdge | edgeColor |

`validateCatalog` / `validate_catalog` 要求完整九个 ID 各出现一次、无多余字段、三个合法且不同的 `#RRGGBB` 色阶。`applyPalette` / `apply_palette` 返回复制后的配方，原对象不会被修改。调用者负责完整配方验证；这些 helper 只验证目录及受影响的颜色字段，不执行文件保存、网络请求或游戏命令。

测试：`python test_palettes.py` 和 `node test_palettes.mjs`。
