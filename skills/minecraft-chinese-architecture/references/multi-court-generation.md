# 多进院落与偏门

`generate_multi_court.py` 生成1～4进串联院落，每进两侧各有一座内向厢房，尽端正房朝向院落，院与院之间用开放门亭和隔墙连通。外门可偏向一侧，内部回到中线。采用的来源关系见 [siheyuan-study.md](siheyuan-study.md)：来源是作者自述的示意四合院，不能用来证明历史礼制或具体实物建筑。

```text
python scripts/generate_multi_court.py --config assets/two-serial-courts.json --out-dir generated/multi-court --name two-courts
python scripts/generate_multi_court.py --config assets/three-serial-courts.json --out-dir generated/multi-court-three --name three-courts
```

## 参数与组合约束

| 参数 | 含义 |
|---|---|
| `schema` | `chinese-multi-court/1` |
| `court_width` | 两厢之间预留的平面带，偶数28～80；模型格点范围为 `[-width/2,width/2]`，含两端共 `width+1` 列 |
| `court_depths` | 从南到北各院预留深度列表，1～4项，各为偶数28～64，含端点格数等于该值 |
| `outer_gate_offset_x` | 外门中心相对中线的X偏移，整数-40～40；正值向东 |
| `row_gap` | 各建筑完整包围盒与院带之间的坐标间隔1～6，已含模型空气边界；不是实际檐口净距 |
| `wall_height` | 围墙从地面上方Y=0至盖顶的最高格索引，3～6；墙有 `height+1` 层方块 |
| `main` / `wing` / `gate` | 厅堂组件的局部配置；屋顶字段合并默认值，其他字段交给组件入口校验 |

深度下限不保证任意厢房都能放下。旋转后厢房完整包围盒长度必须不大于 `depth-2`；默认厢房需至少30格，28格小院可把 `wing.bays_x` 改为 `[4,6,4]`。外门完整包围盒必须落在院宽之内。遇到碰撞或净空失败，应调整尺寸或组件，不取消验证。

门亭当前要求 `kind=pavilion`、`platform_height=1`，两侧有接地台阶。隔墙位于门亭中部Z平面，覆盖其最外柱列X位置，只在两外柱之间留口；柱子若在前后排而不在隔墙平面，墙仍封住门侧空隙。遇到平台或该平面的已知柱础、柱材则保留为接点，其他冲突拒绝。它不是传统垂花门或如意门的细部复原。

院带、模型坐标、门顺序、各建筑旋转和平移、保留接点及逐点路径都写入 `*.report.json`。NPZ保持设计坐标；Schematic偏移为零，格坐标等于设计坐标减去NPZ原点。

## 检查与使用边界

检查院外到每院、每道门和每间房的路径，正房路线必须依次穿过各门口。行为测试进一步封住外门验证全部院不可达；封住内门验证第一院仍可达、尽端正房不可达。路径仍使用整格占用、两格空气和最多一格跨级，不是游戏玩家碰撞。

输出包含 `.schem`、`.npz`、报告和三视图；预览朝向以看见南入口为主。所有几何为原创，8格偏移是明确采用的样本测量；院数、尺寸、材料和门亭形态属于新设计。未生成倒座房、影壁、抄手游廊、排水、房间家具、园林或地形适配。需要这些内容时单独设计并验证，不能因有多进序列就称完整历史四合院。

运行 `python scripts/test_multi_court.py` 验证。组合可与单进入口 [courtyard-generation.md](courtyard-generation.md) 比较；平地折廊和多层楼阁见 [circulation-generation.md](circulation-generation.md)。
