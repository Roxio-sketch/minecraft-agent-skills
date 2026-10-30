# 亭、牌楼、城门与构造拆解

`generate_landmark.py` 的 `chinese-landmark/1` 配置使用 `kind` 和对应的 `parameters`。各模式分别生成完整空间原型、屋面组件或教学构件；报告标记它们的类型与检查，不把孤立屋面称为一栋可用房屋。

```text
python scripts/generate_landmark.py --config assets/octagonal-pavilion.json --out-dir generated/pavilion --name pavilion
python scripts/generate_landmark.py --kind city_gate --out-dir generated/gate --name gate
python scripts/generate_landmark.py --config assets/post-tie-frame.json --out-dir generated/frame --name frame
```

| kind | 关键参数 | 输出与核验 |
|---|---|---|
| `polygon_pavilion` | `sides` 4/6/8/0（圆），半径8～24，连续柱材高5～12，屋面升高3～12 | 多角或圆屋面、周边柱、连梁、台基与入口；圆亭使用8根柱。外部至中心与各柱间目标可达；正方形外包角不误铺屋面 |
| `paifang` | 偶数侧间6～12、明间8～16，高7～14 | 四柱、三通道、三段有高差的屋面与无字牌面；逐间检查3×3格净空。是有屋面的原创牌楼形态，不包含文字、雕刻或礼制身份 |
| `city_gate` | 半宽22～40、半深8～12，墙高9～16，奇数券宽5～11、券高5～10，顶上至少留3层 | 厚墙、明确挖空券洞、女墙/垛口、外置登城梯及上部开放亭。检查三格洞道、登梯净空和墙顶两端/亭内可达；墙段可绕端部行走，不声称形成完整城防闭环 |
| `roof_study` | form=`lu_ding/cross/t_shape/fan`，半宽深8～32，升高3～14 | 盝顶平顶区、十字/T字交汇或环扇形屋面；验证壳体连接、 footprint留空与平顶范围。不是已做檩椽、泛水或谷沟排水的完整屋架 |
| `timber_frame` | mode=`beam_lift/post_tie`，半宽6～12、半深4～10，柱高6～12、升高4～8 | 三榀横架和纵向檩；比较梁上短柱与落地柱贯穿联系。方块级连接和檩方向检查；未做榫卯切口、力学模型及任何朝代的营造模数 |
| `bracket_study` | tiers=1～4、柱高4～10 | 立柱、逐级伸展交叉托臂、承托小块和顶梁；验证连成一体。tiers只是程序层数，**不是宋式铺作数或清式踩数** |
| `caisson` | 半径7～15、层级2～6、底面高5～10；半径至少比层级大3 | 四根展示柱上的方形层叠内凹天花，中心下方留空。顶视图看见背面，内部应查看正面/剖面；没有蟠龙等雕刻 |

`city_gate` 拼接上部亭时允许替换已知平台平面上的方块，以放置入口踏步；其他不同状态的重叠会拒绝。报告列出允许位置及实际覆盖回执，不能把它解释为任意组件碰撞都可忽略。

`test_landmarks.py` 覆盖四种亭顶、三组开间、两种城门、四类扩展屋面、两类木构与多个细部尺度。城门封洞后仍可绕有限墙段，但必须比直接穿洞更远；因此核验指定洞道，而不是把“仍可达”误判为门洞未封。所有输出逐状态往返检查。

## 使用顺序

先按总平面确定位置、开口、支撑、轮廓，再选部件。单独导出的屋面和细部应按报告的本地原点与实际包围盒组合；`Model.paste()` 默认检查冲突。`roof_study` 不自动替换 `generate_building.py` 的屋顶，因为十字/T形屋面需要相应复合柱网和室内布局。构造示意应明确标为设计解释图；未经具体测量不要把参数直接改名为官式材份、斗口或地方匠作比例。

概念与来源见 [构架和屋顶依据](carpentry-and-roof-evidence.md)。
