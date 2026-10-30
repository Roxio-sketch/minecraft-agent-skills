# 楼层、重檐与屋面接合

适用于叠放楼层、檐下短墙/平坐层（roof drum）、重檐裙顶或修复“上层悬浮”。`tier_junctions.py` 提供可组合的接口内核；`chinese-scene/1` 的 `tier_junctions` 数组可在最终组件摆放后执行它。不是独立装饰件，也不是历史木构受力计算。

先区分可进入的楼层与只承托屋面的檐下层。楼层需要完整楼板、梯井和到上一层的实际路线；檐下层需要连续封檐、承托与屋面衔接，不能把层数都算成可进入楼层。

## 构造顺序与标高

1. 声明下层柱网/框架边界和真实圈梁标高 `lower_frame_xz/lower_beam_y`。声明上层柱心、框架边界和脚下楼板方块 Y。不能用模型包围盒底面、柱脚底面和楼板表面互相替代。
2. 下层环状屋顶的 `roof_opening_xz` 与上层外围一起决定接口，不按两个独立屋顶的固定间距猜高度。超出适配范围时调整楼层/屋面参数，不能靠拉长细柱或堆很高的实心箱体掩盖错误。
3. 上层柱础下有完整承托；横梁接到**已存在**的下层圈梁，再由短柱支承楼板。屋瓦、空气、半砖、栏杆或未知模组形状不能自动充当整面承重块。
4. 补完整楼板及外围封檐带，使四边和四角都接上。封檐拥有的交叠范围可把屋瓦转换成整块收口，避免楼梯瓦的低半面留下半格缝；范围外保持屋面轮廓。不要仅画圈梁而遗漏整张楼板。
5. 先声明 `voids`（含净空的 XYZ 包围盒），保留梯井、门口和通廊。承重梁与这些区域冲突时改变承托布置，内核会报错，不自动堵洞或挖掉梁。
6. 拼装、旋转、替换材质及清空气之后，在**最终组合**上再次验接口。各模块单独通过不能证明接起来正确；导出成功、非空气数量一致、整体连通都不能代替接口检查。

## 内存调用

```python
from tier_junctions import assemble_tier_junction, audit_tier_junction

joint = {
    "schema": "chinese-tier-junction/1", "id": "hall-upper-drum",
    "kind": "roof_drum",
    "lower_frame_xz": [-16, -10, 16, 10], "lower_beam_y": 13,
    "upper_frame_xz": [-12, -7, 12, 7], "upper_floor_y": 18,
    "roof_opening_xz": [-12, -7, 12, 7],
    "column_centers_xz": [[-12,-7],[0,-7],[12,-7],[-12,7],[0,7],[12,7]],
    "palette": {"beam":"minecraft:dark_oak_planks", "collar":"minecraft:dark_oak_planks", "floor":"minecraft:dark_oak_planks"},
    "solid_blocks": ["minecraft:dark_oak_planks"], "voids": []
}
result = assemble_tier_junction(final_model, joint)
assert audit_tier_junction(final_model, joint)["passed"]
```

全部坐标使用最终模型坐标。用于自由组合时先变换组件，再变换或重算接口；不能只旋转方块、不旋转柱心和净空范围。`kind=storey` 必须给 `voids`；它不自动生成楼梯，还需在 scene 的 `routes` 中声明楼层目标并验证可达。内存自定义组合也需复核楼梯末端标高、楼板洞口和净空。

`solid_blocks` 是已核实为完整实体立方体的基础方块 ID；未知模组方块不通过名称推断。`shape_aliases` 可声明源码/模型已证明等形的原版方块。部分形状不能通过渲染器的整方块回退偷过检查。示例使用普通实心木板；自定义模组需核实并记录 `evidence`。

`deck_margin` 为1～6格，`collar_thickness` 为1～6格，`floor_thickness` 为1～3格，`post_radius` 为0～2格。上层柱网必须落在下层框架范围内；悬挑、错层或大跨度转换需要另行明确设计。接口升高2～48格是计算边界，不是推荐楼层比例。

`beam_axis` 选择 `x/z/cross`，默认沿 Z 接到下层圈梁。梯井挨近柱础时可显式提供 `bearing_offsets_xz`，每根柱对应一个不超过8格的 XZ 支座偏移，再由楼板底面的短转换梁连接柱心。内核仍检查偏移支座落在下层框架内，整条承托不得穿过 `voids`。`stair_clearance_boxes` 可从 `stair_kernel` 的逐列结果生成接口高度范围内的净空；转折平台仍要单独声明，不能把正常上升踏步误列为应为空气的区域。

## 检查与修复

### 楼板与楼梯相交时保留真实踏步

净空通过不能证明梯跑完整：补楼板或封檐时，整块仍可能覆盖最后一级楼梯，使原来的半格上升变成整格台阶。接口使用两份独立声明：

- `voids`：明确应为空气的头部净空。
- `protected`：明确已存在的踏步、平平台及必要支撑，每项为 `{id,min,max,state}`。检查完整方块状态，包含楼梯朝向、上下半部和转角形态；这些位置不会被接口材料覆盖。不能用空气、流体、未知回退方块或栅栏占位来绕过楼板完整性检查。

从 `stair_kernel` 的设计结果生成，而不是从可能已经坏掉的最终模型反推期望：

```python
from tier_junctions import stair_clearance_boxes, stair_protected_boxes

interface["voids"] = stair_clearance_boxes(flights, low_y, upper_floor_y + 3, landing_air)
interface["protected"] = stair_protected_boxes(flights, low_y, upper_floor_y + 3, landing_blocks)
# landing_blocks 使用明确 min/max/state；模组台阶还需填 interface.shape_aliases。
assemble_tier_junction(model, interface)
for flight in flights:
    assert all(verify_staircase(model, flight).values())
assert audit_tier_junction(model, interface)["passed"]
```

`stair_protected_boxes` 默认也保护连续支撑；输入来自各跑逐列记录及单独声明的横向平台。楼板在排除这些占用格后仍须逐格完整，受保护状态自身必须完整匹配。承托梁遇到梯井或部分踏步会在写入前拒绝，需明确改线；它不能借“保护区”跳过自身连续性检查。已损坏或缺失的踏步也会在组装前拒绝，而不是以现状为正确模板。

折返平台用 `stair_kernel.top_landing_box` 从上平台第一排沿来梯方向延伸。上下两跑、横向平台、圈梁、楼板、栏杆和装饰全部组合完后，再查每跑全宽踏面、半格跃升、完整平台、支撑与净空。只查生成单跑时的结果不足以发现后续覆盖。

- `audit_tier_junction` 逐格检查四周封檐、楼板、柱础到圈梁的连续承托和声明的净空，报告问题坐标。整张楼板另可用 `audit_floor_plate`；只有明确列出的梯井可被排除。
- 看正面、侧面及每道接口的局部剖面，至少覆盖中部、柱心和转角。整楼远景及整方块近似图可能掩盖半格缝。施工后扫描这些位置的真实状态再检查，当前客户端画面只代表当时的玩家视角。
- 修复已有存档前备份接口范围内的空气、方块与方块实体，写入精确差量，复核屋顶轮廓、室内净高、楼板与梯井。不能只在截图可见一侧贴墙。
- 运行 `python -m unittest test_tier_junctions test_staircase -v`。回归故意注入封檐缺块、楼板洞、断承托、半砖/楼梯假支撑、未知方块、堵梯井、末级被覆盖和朝向反转；这些错误应分别失败。

这些检查证明声明范围内的体素接合及部分形状条件，不证明木结构力学、未声明的交通路线或真实玩家碰撞。

### 带内楼梯的普通楼阁也要检查外围

`generate_tower.py` 的完整楼板及 U 形梯路不能代替外围接缝检查。生成器从每层实际圈梁、上一层地坪和顶层屋面高度生成 `frame_seam_cells`：逐边补齐楼板下两层封檐，上层外围柱脚用短径向梁接到已有下层圈梁，顶层仅补到实测屋面下侧。它保留梯井、正常房间和阁顶空腔，不把所有空气填满。

最终 `audit_frame_seams` 分开报告封檐缺口和断开的原有圈梁锚点；半砖、楼梯及未知方块不能冒充整块封檐。已有存档采用现场 NBT 生成精确差量，不能重跑整栋生成器覆盖用户改动。住宅、商铺与阁楼都要查四边和转角，不能因为主殿接口已修就跳过重复小楼。

回归：`python -m unittest test_circulation test_tier_junctions test_staircase -v`。`test_circulation` 还检查“楼板与上楼路线均通过，但外侧少一格”的反例，以及顶层封檐和原有锚点断开。
