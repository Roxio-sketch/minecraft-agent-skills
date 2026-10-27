# 建筑与结构库

## 建筑流程

确认用户要新增、复制还是替换；记录维度、坐标、实际包围盒、朝向、材料和实体策略。坐标以世界为准，截图仅作辅助。读取相关区域，检查地形、已有建筑、容器与方块实体，确定备份与回滚方案。普通施工不需要抢占玩家输入。

scan_area 支持 pos1/pos2 或 origin/size。端点都包含在区域内；最大体积 4,000,000 方块。include_air 默认 false；include_entities 可选，limit 可截断。需要备份时分块读取或保存模板，检查完整性；截断结果不是完整备份。回滚包含原空气时必须保存/恢复空气，否则新增方块不会被清除。方块实体和实体需要额外保留，普通 blockstate 列表不能代替完整 NBT 备份。

build_structure 的 blocks 使用相对 origin 的偏移，方块可写完整 blockstate。示例为小规模新增，只有用户授权对应区域时才执行：

```json
{"cmd":"build_structure","params":{"origin":[100,64,200],"blocks":[[0,0,0,"minecraft:stone"],[1,0,0,"minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"]]}}
```

每次最多 200,000 条，默认每 tick 4096，允许范围 1..65536；调整 batch 参数前核实运行版本实际字段。大负载保存为 JSON 文件发送，避免命令行长度与转义问题。一次只运行一个建筑任务；先确认没有其他人的任务。

记录 task_id，通过 get_build_progress 查询；只有 done 且 failed=0 才可报告完整成功。done_with_failures 需要处理失败项；unknown 不是完成。cancel_build 不回滚已经放置的方块。写入超时先查进度，不重新提交整栋建筑。

compare_structure 使用 origin + id 或 inline blocks，可选 anchor、include_air、limit。检查差异是否截断。原生比较没有旋转/镜像参数；变换后的模板不能直接套原始比较结果，应扫描实际区域与变换后预期逐项核对。最终根据任务结果、结构差异和适当画面确认，不仅依据请求成功。

## 保存与放置模板

操作前 list_structures 检查重名。name 使用小写命名空间 ID，默认命名空间 mcpmod。原生命令保存拒绝重名，HTTP save_structure 可以覆盖；使用明确版本化的新 ID，覆盖须属于用户请求。

```json
{"cmd":"save_structure","params":{"name":"mcpmod:house_v1","pos1":[100,64,200],"pos2":[110,74,210],"include_air":true,"include_entities":false,"prefab":true,"display_name":"House","category":"building","anchor":"bottom_center"}}
```

保存支持 display_name/category/anchor/export_to。NBT 模板保留方块实体；实体默认不保存。放置默认 include_entities=true，通常显式 false，防止复制生物/装饰实体。rotation 支持 0/90/180/270，mirror 支持 none/left_right/front_back，anchor 使用 min_corner/bottom_center/center；未指定时优先 prefab 的 anchor，否则 min_corner。先计算旋转后包围盒和目标覆盖区域。

```json
{"cmd":"place_structure","params":{"id":"mcpmod:house_v1","origin":[130,64,200],"rotation":"90","mirror":"none","anchor":"bottom_center","include_entities":false}}
```

主库：<gameDir>/mcp_structures/structures/<namespace>/<name>.nbt；另有 prefabs 和 index.json。同步到存档的 <save>/generated/<namespace>/structures/<name>.nbt。用于模组资源时导出到 src/main/resources/data/<modid>/structures/<name>.nbt，遵守正确命名空间。

原生 `/place template ns:id ~20 ~ ~` 表示 X+20，不是视线前方。需要按视线放置使用本模组的 ahead 命令，并确认执行者的视线。
