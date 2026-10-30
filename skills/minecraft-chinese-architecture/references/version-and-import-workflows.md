# 旧投影、多区域和目标版本

这些操作读取源文件，在新路径写分析或导出副本。实体、命令、库存、告示文本和生物群系均不作为完整世界载荷恢复。它们不能替代原件备份。

## 显式合并Litematica区域

先用`import_structure.py SOURCE.litematic --list-regions`列名称、位置与有符号尺寸，然后按希望的叠放次序指定区域：

```text
python scripts/merge_litematic.py SOURCE.litematic --regions "main" "wing" --out merged.npz
```

支持v5/v6；保留粘贴相对坐标和负尺寸方向。默认不同实体状态重叠报错；`--conflict keep`保留先放的格，`--conflict replace`以后放的格覆盖。默认空气不粘贴；只有显式`--include-air`才纳入空气，配合replace可能清除本次新模型里的格。区域顺序、冲突模式、位移、源元数据和哈希写入`.import.json`。

重复或未知区域拒绝。解码体积和合并后包围盒都受预算约束，远距区域不能靠稀疏原文件绕过限制。没有按作者拼图逻辑自动推断缺失连接。

## 旧数字ID `.schematic`

`import_legacy_schematic.py`支持经典Alpha的`Blocks/Data/AddBlocks`数组和WorldEdit偏移。必须提供**明确的ID:data → 完整状态映射**，包括空气；未映射的组合一律报错，不用最接近的现代方块猜。

```json
{
  "metadata":{"source":"Task-specific verified mapping","source_version":"declared by input evidence"},
  "blocks":{"0:0":"minecraft:air","1:0":"minecraft:stone","1:1":"minecraft:granite"}
}
```

```text
python scripts/import_legacy_schematic.py old.schematic --mapping verified-map.json --out old-analysis.npz
```

映射也可直接为ID:data字典。报告保留源文件和映射哈希、偏移、缺失内容的限制。`AddBlocks`的高4位已用独立NBT构造文件验证；这是文件行为测试，不等于已经核对所有历史方块映射。1.12及以前**数字ID Anvil世界仍不支持**，先用合适工具导出限定结构，再明确转换。

本地研究参考过[PrismarineJS/minecraft-data的legacy.json](https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/common/legacy.json)。2026-10-29下载哈希`0d7aec9a3107a62e322f8839ea2b96d24e2945c461c7aa29c6a77f854609a2e9`；master可能变化，应固定提交或保留该哈希核验。它不随Skill打包，也不是任意模组数字ID映射的授权答案。

## 注册表检查与改版导出

默认生成仍为Sponge v2 / Minecraft Java 1.21.4的DataVersion4189。`validate_registry.py`可下载1.21.4或1.20.1的版本标记数据，记录URL、版本和SHA-256：

```text
python scripts/validate_registry.py fetch --version 1.20.1 --out registry-1.20.1.json
python scripts/validate_registry.py check building.npz --registry registry-1.20.1.json --out states.json
python scripts/validate_registry.py retarget building.npz --registry registry-1.20.1.json --out building-1.20.1.schem
```

数据来源为[misode/mcmeta](https://github.com/misode/mcmeta)的版本summary。完整`registries/data.json`方块名录与`blocks/data.json`属性表共同组成检查表；旧版属性表省略无属性方块，不能将这个省略误判为石砖等方块不存在。两份上游数据和合成表的哈希分别保留。检查实际使用的方块ID、属性名和值；未写属性按该版本默认值解释。`retarget`只在所有状态有效且来源回执哈希匹配时写出1.20.1的3465或1.21.4的4189，并逐格核对回读状态。未知版本不会自行猜DataVersion。

发现新版本方块缺失时，先使用`chinese-finish/1`在新副本中明确换成同形状材料，再重查；不自动将未知块转为空气。玻璃透明度、模组模型、方块更新、邻接、流体、农作物、照明、库存与真实玩家碰撞仍需要单独游戏验证。注册表通过的正确表述是“目标版本状态检查通过”，不能说“已经在目标游戏导入验收”。

多区域、换材质、自由组合和改版导出都会保留可获得的来源声明、旁车回执哈希和输入模型哈希；缺少作者资料时明确保留未知，不用“原创”替代。来源声明本身仍须核实，不产生远端发布授权。
