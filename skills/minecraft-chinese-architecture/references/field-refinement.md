# 已有建筑的修缮检查

`scripts/field_refinement.py` 提供三种只读操作。输入为 `voxel_model.Model` 的 NPZ，坐标是模型原点所在坐标系中的 XYZ，边界含端点。每个模型遵守800万格上限；大型建筑分成有完整上下文的片区，重叠格的统计应说明。脚本不会连接 Minecraft，也不会放置方块。

## 精确改动与保护区

```text
python scripts/field_refinement.py audit --before before.npz --after planned.npz --config edit.json --out audit.json
```

`edit.json` 的 `changes` 和 `prior` 是同一坐标集合的 `[x,y,z,state]` 列表；不能缺旧状态、重复坐标或用无变化的条目充数。前后模型覆盖完全相同、包含空气的体积。审计拒绝未声明改动、旧状态不符、遗漏改动和保护区内的任何改动。

```json
{
  "changes": [[1,5,0,"minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"]],
  "prior": [[1,5,0,"minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"]],
  "protected": [{"id":"doorway","bounds":[[-1,1,-6],[1,4,-5]]}],
  "material_pairs": {"minecraft:oak_stairs":"minecraft:spruce_stairs"}
}
```

`protected` 和 `material_pairs` 可省略。纯换色时，明确旧→新材质对并要求状态属性相同；同碰撞形状仍须由注册表或源码核实，脚本不会凭名称推断。自动连接状态有变化时，单独记录与解释，不能用放宽整个审计掩盖未声明改动。NPZ只存几何；现场回滚还要完整含空气、方块实体的 NBT。

## 屋面投影和露天铺地

```text
python scripts/field_refinement.py measure --model site.npz --config measure.json --out metrics.json
```

配置字段为 `bounds_xz: [x0,z0,x1,z1]`、`floor_y`、`roof_bases` 和 `paving_bases`，两类材料均显式给基础ID列表，不写状态属性。模型应覆盖目标台基上方全部屋檐、树木与装饰。

屋面占比只计地坪上方指定屋瓦的平面投影；完全露天铺地要求该处是指定地坪、其上直到模型顶均为空气。树冠或亭饰不能冒充屋顶。结果注明每格分辨率与扫描上界；裁得过矮的模型不能证明露天。不同标高台基分别测量，再对照参考图与用途决定修正，不能把某座院子的比例变为通用密度指标。

## 从梁架规划吊灯

```text
python scripts/field_refinement.py lights --model room.npz --config lights.json --out lighting-plan.json
```

配置必填 `floor_xyz`（承托方块坐标，非脚部空气）、`anchor_bases`、`lamp_state`、`chain_state`。可选 `emission` 1～15（默认15）、`minimum`（默认3）、`clear_height`（默认3）、`shoulder`（默认2，向地坪周边搜梁）、`max_chain`（默认24）、`transparent_bases`（默认仅空气透光）。

地坪需由调用方先按实际碰撞与走行筛选；工具检查非空气承托及两格身体空间，不把任意非空气方块判成可站地板。挂点列表使用已核实可从下侧挂灯的整方块；灯与链不能互为结构挂点。显式透光材料必须有实际依据，未知材质按不透明处理。新增灯至少在地坪上第4格，灯和链只能占原空气，不侵入所声明地坪的净空。

规划使用六邻域传播，考虑墙体阻挡；候选包含路带以外的边梁，不要求每个挂点下面都是可站地坪。结果为 `changes`、`prior`、灯位与挂点，不修改输入模型。若无挂点或最终灯链互相挡光导致未覆盖，退出码为2并保存失败报告；参数非法直接拒绝。保留旧灯的贡献，使用**当前进程实际加载**的亮度，新JAR只装到磁盘时不能预先按新版亮度验收。

预测结果不是 Minecraft 光引擎结果。施工后读取实际 `BlockLight`，并用 Python 接口 `audit_blocklight(values, minimum=3)` 检查样本；缺失、非法数值或低于阈值都不能通过。地坪、楼梯、楼层接口及灯的完整支持链仍须在最终组合上复验。
