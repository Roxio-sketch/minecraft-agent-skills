# 格式与实现边界

## 支持范围

世界扫描器支持包含调色板式Java Anvil世界的ZIP，以及一层嵌套ZIP。实测版本为1.21.4 / DataVersion4189和1.14.4 / DataVersion1976；只分析主世界方块状态，不修改世界。自定义高度从实际section Y读取，不按原版高度截断。

现代根部 `sections/block_states` 与旧 `Level/Sections/Palette/BlockStates` 分别规范化。旧调色板版本在DataVersion2529（20w17a）之前用连续位流，之后用每long填充；根据每区块版本选择，兼顾部分升级世界。四位打包时长度可能相同，不能仅凭数组长度猜测。省略方块数据的光照section可跳过；有BlockStates无Palette、未知schema、缺版本和旧数字ID `Blocks/Data` 明确拒绝。1.14.4真实数据与两种位流构造测试通过，不等于所有历史版本都已有真实存档验证。

`archive_scan.py inspect` 读取归档和 `level.dat`；`scan` 遍历主世界已保存区块；`extract` 按世界坐标抽取限定区域。原始实体、告示文字、箱子库存、命令块、红石运行状态和生物群系不属于建筑几何分析输出。不要把提取的 NPZ 当作完整世界备份。

NPZ 是内部数值格式，使用 `allow_pickle=False` 读取。全局地图数组按 `[z,x]`，局部模型按 `[y,z,x]`。调色板存储完整状态字符串；原点是世界 `[x,y,z]`。缺失区块不能当作空气；抽取工具遇到缺失列会失败。

对超大 `.schem` 优先使用同包的世界存档分区分析。黄枫谷投影的包围盒为 1372×530×1186，BlockData 字节数组超过 8 亿字节，不能按压缩文件的 12.5 MB 大小估计内存。旧数字ID `.schematic`可通过独立入口和明确映射导入；任意历史世界仍不能自动兼容。详见[旧投影与版本流程](version-and-import-workflows.md)。

## 独立结构导入

`import_structure.py` 支持 Sponge `.schem` v2/v3、Litematica `.litematic` v5/v6、Java结构/常见Create `.nbt`，默认上限 800 万体素、解压后 NBT 256 MiB。只读输入，输出 NPZ 与 `.import.json`；不携带实体、命令载荷、箱子库存、告示文本和生物群系。外形用到的告示、活板门等方块状态仍保留。不能把 NPZ 当完整备份。

- Sponge v2 使用根部 `Palette/BlockData`；v3 使用 `Schematic/Blocks/Palette` 和 `Data`。两者均是 VarInt，索引 X 最快、Z 次之、Y 最慢。调色板 ID 可以稀疏，但不能重复或引用不存在的 ID。
- Litematica 用 `max(2,ceil(log2(paletteSize)))` 位连续打包，**允许跨 64 位边界**，不能复用现代 Anvil 的填充式解码器。数组尺寸是 `abs(Size)`；负方向的区域最小点是 `Position + min(0, Size+1)`，数组本身不用反转。
- 多 region 必须明确选择 `--region`；`--list-regions` 可列出名称、位置、尺寸。自动导入不合并区域；明确选择后可用`merge_litematic.py`按顺序合并，保留冲突和总体积检查，见[多区域流程](version-and-import-workflows.md)。
- Java结构使用 `size`、`palette`（或`palettes`）及稀疏 `blocks[{pos,state,nbt}]`。直接按XYZ坐标放入分析数组；不把记录顺序当格位索引。多套调色板须选 `--palette-index`；拒绝重复位置、越界坐标和不存在的状态。Create的额外根标签记录名称但不执行或恢复其功能。
- Java结构省略的位置在几何NPZ里表示为空气，报告另记 `defined_positions/omitted_positions`。**这不等于源结构粘贴时会清除这些格位**；NPZ不保留逐格“未指定”的施工语义，不用于源结构的无损重新粘贴。
- NPZ 的 `origin` 是粘贴相对坐标。Sponge 的 `Offset` 不是原世界坐标；网站格式转换也可能重置它。比较格式时先按局部体素对应，另核对位置元数据。
- 导入时将三种空气规范为 ID 0、排序属性键，保留其他完整状态；不会根据目标版本擅自替换方块。导入 1.21.8 数据不等于目标客户端已兼容。
- `nonair_namespace_counts` 统计实际非空气格的命名空间。保留模组状态，不因本机未安装对应模组将它变成空气。分析文件可读不表示目标游戏已装齐依赖；材质替换应作为明确的新设计另存。

格式依据：[Sponge v3](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md)、[Litematica](https://github.com/maruohon/litematica)。实际新样本 v3/v6 已与独立库交叉校验；v2/v5、稀疏 ID 和负方向由构造样本验证。

## 导出

默认生成器输出 Sponge Schematic v2，DataVersion 4189；目标注册表检查通过后可用独立入口导出1.20.1的3465，详见[版本流程](version-and-import-workflows.md)。体素索引中 X 最快、Z 次之、Y 最慢，即 `x + z*Width + y*Width*Length`，BlockData 是调色板索引的 VarInt 序列。生成的示例只使用原版方块，不携带原地图实体或命令。

格式依据：[Sponge Schematic v2 规范](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md)。NBT 读写依赖：[nbtlib](https://github.com/vberlier/nbtlib)。依赖通过 requirements.txt 安装，未复制第三方库源码。

## 环境

本次在 Windows / Python 3.10 验证。建议为分析目录建立虚拟环境，执行 `python -m pip install -r <skill>/requirements.txt`。生成器用 numpy、nbtlib、Pillow，地图候选区域分析还需 scipy。输入输出路径使用带引号的绝对路径，中文路径可用。
