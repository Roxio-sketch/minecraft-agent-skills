# 多落院落、护龙与过水

在`chinese-vernacular/1`中选择`kind: minnan_compound`。它通过不同屋顶、房区、独立入口和可达通路生成新的组团，来源对应见[金门类型说明](vernacular-evidence.md)与[洪氏古厝研究](hong-house-study.md)。不是把B14的门罩或院落简单改名。

## 不同院序

| `form` | 实际生成关系 | 预设 |
|---|---|---|
| `san_gai_lang` | 后排三开间；上攑侧房与前端下攑、门厅共同围合；前端有三个独立屋顶体 | `vernacular-minnan-san-gai-lang` |
| `two_luo` | 前排门厅与两边房区处于一个连续屋顶下，后排主屋较高，侧房连接两落 | `vernacular-minnan-two-luo` |
| `three_luo` | 三排主屋与两个开敞院；祖厅可置中落或后落，所在屋顶最高 | `vernacular-minnan-three-luo-middle` |

祖厅在中落时保留中轴后墙，通过左右房区的后门进入后一院；祖厅在后落时，中落有中轴贯通门。报告保存这些真实开口的位置。全部外门封闭后，外部不能再到达内部目标；把中落后门封闭，也会阻断后一院。

这些是简化的空间类型。房间家具、出入口选择与粗格燕尾为原创；没有推导通用礼制、构架等级或某座古厝的完整复原。

## 护龙

`hulong: none|west|east|both`增加独立侧房带，每侧有与外部相通的前门、面向窄院的檐廊、前后过水。`west/east`是本模型的X负/正方向，旋转组合时一同旋转，不等同原址地理朝向。单护龙和双护龙起点分别见`vernacular-minnan-two-luo-hulong`、`vernacular-minnan-three-luo-double-hulong`。

过水是跨窄院的实体地面与覆盖屋面；院中段保留上空，护龙前门独立于核心大门。可通过封闭核心与侧院间通道来验证两套入口的关系。当前护龙只有一个实际使用层，没有冒充洪氏古厝的后期叠楼。

## 参数与净空

| 参数 | 范围与作用 |
|---|---|
| `bay_width` / `range_depth` | 主屋开间10～16、进深10～18，均为偶数格 |
| `wing_depth` / `wing_room_width` | 侧房进深6～10、开间8～12，均为偶数格 |
| `wing_rooms` | 每院每侧1～2间；三盖廊固定1间上攑，前端另有1间下攑 |
| `court_extra` | 8～16偶数格，补足两端连接空间 |
| `eave_height` / `roof_rise` | 8～12格、3～7格 |
| `ancestral_range` / `ancestral_raise` | `rear`或`middle`；中落只用于三落，祖厅屋檐加高2～5格 |
| `ridge` | `round`或`swallowtail`；仅祖厅所在主屋使用所选脊型 |
| `split_court` | 布尔值；上院坪比下院坪高1格，关闭则同高 |
| `hulong_depth` / `hulong_rooms` | 6～12偶数格；3～9间，按院深分配偶数开间，每间至少6格 |
| `lane_width` | 两侧墙间8～14偶数格，包括檐廊；各檐可投出3格，因此最小值仍保留上空 |

中央天井净宽不足、护龙房数放不下、三盖廊多加上攑等配置会报出具体限制。缩小体量时应减房数或檐距，不能只把门和两格玩家空间挤没。

报告提供`range_rows`、`courts`、`external_gates`、`onward_portals`、`hulong`、`guoshui`和`sky_probe_floors_xyz`，并与实际体素通路一同检查。可通过自由场景旋转，或作为`chinese-complex/1`的`vernacular`组件加入地形。离线封门和路径检查不代替游戏碰撞、方块更新或结构安全验收。
