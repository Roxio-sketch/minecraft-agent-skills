# 园林、桥与月洞门生成

`generate_garden.py` 输出一座原创池庭：外门与前场、影壁、月洞门隔墙、带漏孔的窗格、池岸、石桥、临水方亭、L廊、岛石、树与低矮灌丛。布局有连续游览步道。它是受场景学习启发的可调原型，不复原某座历史园林，也不复制维园的体素。

设计依据分开使用：UNESCO的[苏州古典园林](https://whc.unesco.org/en/list/813/)介绍支持把建筑、植物、水石作为整体空间组织；[维园样本研究](garden-source-study.md)支持检查围合、竖向分层和游览遮蔽。生成器具体尺寸、池形、月门、桥曲线、种植与所有构件均为原创选择，未由该资料自动拟合。

```text
python scripts/generate_garden.py --config assets/moon-pond-garden.json --out-dir generated/garden --name garden
python scripts/generate_garden.py --config assets/wide-garden.json --out-dir generated/wide-garden --name wide-garden
python scripts/test_garden.py
```

## 配置

`schema=chinese-garden/1`。`width` 为84～140的偶数，`depth` 为80～140的偶数；它们是围墙中心线的尺寸，输出还包含墙外入口和边界。`moon_radius` 3～6，`bridge_width` 3或5，`bridge_rise` 0～6；桥的半跨由园宽决定，并限制每步最多升高1格。`tree_count` 0～3，`plant_clusters` 0～60；空间不足时报告实际灌丛数，不强行塞入通道。`seed` 是非负整数，固定配置固定输出。

`ground/path/wall` 是方块状态，分别用于庭地、游步道和墙体。使用正常的实体材料；禁止空气、水、门和栅栏作为这些角色。自定义模组或版本状态仍需另验注册表。`evidence` 保留来源说明，不会自动核实文字与修改后的参数是否一致。

内部月门隔墙把前后空间分开；步道连接月门、两岸、亭与连廊两端，报告保存 `tour_legs`。可达性检查没有声称桥是后园唯一通路，游客也可走池岸。测试单独切断桥中段，确认桥组件自身的连接确实中断。

## 可复用构件

`garden_components.make_garden_wall(half_length, radius, gate_x, windows)` 返回圆形开口的墙体模型与说明；窗格留下真实空气孔，避免把透明贴图当成几何开口。`make_bridge(half_span, clear_width, rise)` 返回沿X的桥模型、桥面逐X高度及两端通路。用 `Model.paste(..., turns=...)` 转向。

桥底是一条分格拱形轮廓，非连续石拱受力设计。墙上方的连接、桥边防护、楼梯形状和预览都仍需实机检查。`line_of_sight` 用不超过1/8格的采样间隔测整格遮挡，目标格自身不算挡视线；这能验证影壁挡住指定直线、月门中部放开指定视线，不等于镜头构图、透明材质或完整借景美学评分。

## 验收

默认示例103×30×96、46,189个非空气格，含地基与院墙。检查所有目标的地面支撑、两格净空、桥面、月门三格宽通行带、池水下方与四邻围合，以及两条指定视线；导出后逐状态回读。

`walk_paths` 现在排除水、熔岩、已知草类及门、栅栏、墙、玻璃板等薄构件作为干燥通路的地面。未识别状态仍近似整格；半砖、楼梯按整格高度处理，允许1格跳台，不模拟玩家碰撞或游泳。池水围合检查没有运行流体更新。植物使用持久树叶避免依赖自然树叶存续；未运行游戏更新验收。

封月门后前院可达、后园不可达；封外门后前院不可达。不同尺寸、桥宽、抬高、种子与月门尺度、桥体断裂、窗孔视线、往返和确定性均有行为测试。生成包仅含原创结构，不含第三方园林。
