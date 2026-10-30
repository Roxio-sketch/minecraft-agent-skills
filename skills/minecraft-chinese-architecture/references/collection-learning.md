# 批量学习结构库

有多个独立结构文件时，使用目录批量导入；世界ZIP仍走 `archive_scan.py`。先记录合集作者、版本、下载出处和许可，再逐文件核对。不要运行结构库附带的构建脚本、数据包函数或实体命令来完成几何分析。

```text
python scripts/import_collection.py SOURCE_DIRECTORY --out-dir analysis/collection
python scripts/import_collection.py SOURCE_DIRECTORY --recursive --out-dir analysis/collection
```

支持 `.nbt`、`.schem`、`.litematic`；默认仅当前目录，`--recursive` 才扫描子目录。每个文件沿用独立导入器的格式、解压和体积限制。输出保留来源扩展名，例如 `temple.nbt.npz` 与 `temple.schem.npz` 不冲突，旁边有各自的 `.import.json`。

`collection.report.json` 分别列出成功和拒绝。存在拒绝项时CLI返回1，但已经成功的其他模型和总报告仍保留。查看具体原因：Gzip校验失败、格式不支持、体积超限、多region或多palette未选择属于不同问题。后两者应使用 `import_structure.py` 明确选择，不是损坏文件。

几何指纹规范化已使用调色板，按模型尺寸和每个完整方块状态计算。它忽略粘贴原点、未用调色板、实体、方块NBT和省略格位施工语义，只报告**相同尺寸下完全相同的几何**。增添基座、裁剪边界或改变朝向会成为不同指纹；这些变化不能自动算新风格或新建筑传统。统计文件、版本、独立作品和已测组件时分别说明。

## 合集学习的下一步

导入只是索引。先以俯视和分层图确认布局，再挑选土楼的环状房间/公共走道、塔的层檐/柱列、稻田的台地/水体等实际问题。为每个选区单独写来源哈希、坐标和材料过滤器，用已有测量器复算。

材料匹配优先匹配基础方块ID。搜索字符串 `water` 会同时匹配 `waterlogged=false`；统计独立水方块时应限定 `^minecraft:water(?:\[|$)`。含水方块另按状态属性统计。双格植物的上下半部也应先配对，再计算植株数，不能直接拿体素数替代植株数。

模组命名空间和细形状要一起检查。`custom_slab`、`custom_wall` 这些名称不能证明其几何等于原版；当前预览对未经实现的模组状态回退为整格，并列出状态。原版形状的246项验证不覆盖模组模型。名称像屋瓦的完整方块也可能实际是斜面模型，不能用立方体预览宣称已恢复准确檐口。

版本故障实例和已测来源见 [ancient-structures-study.md](ancient-structures-study.md)。新增解析或目录处理后运行 `test_collection.py`；改动形状时另运行 `test_shapes.py`。
