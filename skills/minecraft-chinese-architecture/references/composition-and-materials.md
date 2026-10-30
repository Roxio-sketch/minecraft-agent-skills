# 自由拼装与材质处理

## 两种组合入口

`chinese-complex/1` 适合地形场地：统一地面、平台削填、挡土与坡路。组件可选 building、tower、regional、landmark、garden、courtyard、multi_court、inhabited、infrastructure、settlement。除基础厅堂和楼阁外的组件必须提供明确入口及内部目标；没有交通契约的纯装饰不进入场地道路规划。

`chinese-scene/1` 适合任意 XYZ 放置、屋面与构件展场、既有模型组合。它不自动造地形。使用 [自由展场配置](../assets/free-composition.json) 起步，每个组件写 `id`、`config` 或 `model_file` 二选一、`at:[x,y,z]`、`turns:0..3`。旋转是俯视顺时针，围绕组件局部原点；平移发生在旋转之后。

`boxes` 是明确的 `min/max/block` 六面体，例如接地底板；之后按列表次序放组件。不同非空气状态重叠默认报错，等状态接触可以。只有显式的 `conflict:"replace"` 或 `"keep"` 才改变此行为；`include_air:true` 与 replace 会清除新场景里的对应格。它们都不会修改输入NPZ或用户原世界。

`require_connected` 默认 true。展览品确实需要悬空分离时可设 false，报告仍显示几何连通数量。`routes` 的 `start/targets` 可以是脚下方块坐标，也可以是报告中的 `组件ID.entry`、`组件ID.目标ID`。没有声明通路就记录 `not_requested`，不算通行通过。

`tier_junctions` 可声明最多32道 `chinese-tier-junction/1` 楼层/屋面接口，使用最终场景坐标。在所有组件放置后补入封檐、完整楼板和柱础承托，再复核全部接口。下层承托点必须已存在，梯井等 `voids` 必须已清空；接口不能凭空制造基础或堵住交通。可进入楼层还必须声明 scene `routes`。配置与局部剖面检查见[楼层接合](tier-junctions.md)。

组件拥有的自然空气和原始结构省略位置不是一回事。直接使用导入NPZ时只按当前几何模型处理；在报告中保留源路径、哈希和作者资料，不把组合结果标成全原创。嵌套场景先导出NPZ，避免难以核算的递归组合。

## 有部位依据的材质

`chinese-finish/1` 读取 `model_file`，输出新的文件名或目录。规则写 `pattern`（匹配原状态）、`state`（目标状态）、`fraction` 0～1、`where:exposed|all`，可限制 `y_range:[低,高]`。

```json
{
  "schema":"chinese-finish/1",
  "model_file":"hall.npz",
  "seed":741,
  "rules":[
    {"pattern":"^minecraft:stone_bricks$","state":"minecraft:mossy_stone_bricks","fraction":0.2,"where":"exposed","y_range":[0,3]},
    {"pattern":"^minecraft:deepslate_tile_stairs","state":"minecraft:polished_blackstone_brick_stairs","preserve_properties":true,"fraction":0.3}
  ]
}
```

规则始终匹配原状态，后规则覆盖前规则。坐标散列与 seed 保证复现；没有把随机混材频率冒充实测比例。`preserve_properties` 只允许显式兼容的楼梯、半砖、栅栏等同族传递，防止把 `facing` 塞进不支持它的方块。已支持形状的实体网格和全部占用格必须不变；未知形状回退另列，仍需目标注册表核验。

材质分工建议：柱梁、围护、瓦面、脊端、台基、铺地、栏杆分别选色。旧化优先在临地或露天材料上使用，不无差别污染祭祀中心、彩画和室内。彩画、砖雕、石雕、灰塑、木雕的题材与等级应按资料或原创主题说明；方块斑纹不是历史纹样鉴定。
