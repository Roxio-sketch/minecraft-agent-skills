# 药铺、营堡、陵墓与小住宅的分层学习

来源仍为 [Ancient Structures: Chinese](ancient-structures-study.md) 固定仓库快照，建筑署名 Jackie、开发署名 TheGoldenWorld。这里增加同一合集四个文件的深入测量，不增加独立作者或地域风格数。含模组构件，离线预览对未知形状使用整格回退。

精确选区与结果分别在 [组件标注](functional-buildings-annotations.json)、[组件测量](functional-buildings-measurements.json)、[布局标注](functional-layout-annotations.json) 和 [布局测量](functional-layout-measurements.json)。坐标是各结构文件局部 XYZ，含端点。

| 来源文件 | 可复算结果 | 用于新设计的关系与限制 |
|---|---|---|
| `apothecary_house` | 下部指定屋瓦 205 格、15×20；上部 214 格、11×16。Y7..9 柱行 X=29/32/35，Z=8/12/15/19，即 3/3 与 4/3/4。西向双扇门在 X29、Z13/14 | 区分前场、侧向入口与上下层，不把整栋材料频率当店铺语法。3格柱段不直接等于完整层高；现厅堂生成器的4格最低柱高不能无损复现这个柱段 |
| `chinese_fort` | 下部指定屋瓦带 97 格、9×9；高处查询含 327 格、整体包围盒32×33。13对门上下半、16格梯子，容器分布在Y8/12/16/21 | 高处32×33是分离屋面集合的包围盒，不能据此生成一块32×33屋顶。围合防御、角部高点和内部交通分别设计；未证明源梯子及门能实际使用 |
| `chinese_emperor_tomb` | 地上选定柱行间距6，连续柱段Y13..15；屋瓦13×13、240格。容器有13格位于Y2/3、34格位于Y8；两处门底分别Y8与Y13 | 地表小建筑不足以描述整个作品。必须同时查看地下Y3/Y8平面与地上Y13/Y15平面；墓制和礼制并无历史测绘依据 |
| `small_house` | 屋瓦带16×17、214格；两对相邻门半块；6格容器均在Y7 | 门的上下半配对只能统计门扇。相邻双扇是一个组合入口，不应计为两栋建筑或两个房间 |

陵墓选定下层横向区域 `[6,2,8,28,4,9]` 共138格，其中129格在导入模型中为空气，**不是完整净空的138格通道**。余下9格需按状态和实际形状判断；这个例子说明不能看一张浅色切片就宣称通路已验收。Java结构省略的位置在分析NPZ中也可能成为空气，见 [格式边界](formats.md)。

## 复跑与应用

```text
python scripts/measure_components.py --samples-dir analysis/additional/aschinese --annotations references/functional-buildings-annotations.json --out measurements.json
python scripts/measure_layout.py --samples-dir analysis/additional/aschinese --annotations references/functional-layout-annotations.json --out layout.json
python scripts/render_sections.py analysis/additional/aschinese/chinese_emperor_tomb.npz --axis y --at 3 8 13 15 --out tomb-sections.png
```

`measure_layout.py` 的查询是明确盒区和正则材料过滤，输出计数、Y层分布、状态直方图与包围盒。它检查标注的源哈希，记录分析NPZ的哈希，拒绝反向或越界盒区。水方块用 `^minecraft:water(?:\[|$)`，不能用 `water` 混入 `waterlogged=false`。

门配对要求上下半的材质与其他属性一致；缺半或状态不同留在 `unpaired_cells`。这不恢复箱子内容、不执行方块实体、不推断房间用途，也不验证红石机关。材料切片的颜色仅为名称分类，空白可能是室外、室内或省略格位。

可复用的设计顺序是：先标出使用层和入口，再给店面/储物、营堡/角楼、地上/地下分别分配空间，最后连接并验证交通。本文件保留来源研究；新的[可居住功能建筑](functional-generation.md)与[营堡、地下墓室原型](infrastructure-generation.md)已实现这一设计顺序，尺寸和几何为原创，没有复制本来源。
