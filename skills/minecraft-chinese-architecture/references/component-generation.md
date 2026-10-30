# 异距柱网、屋顶与组件组合

`generate_building.py` 的 `chinese-building/1` 是新增入口；旧 `generate_hall.py` 与 `chinese-hall/1` 保留，已有预设无需迁移。新入口输出单层封闭厅堂或开放方亭，并共享六种屋顶形态。所有形态目前是原创体素构造；术语表达轮廓关系，不能凭名称宣称符合某朝代营造法式。

## 按真实结构区别选择屋顶

| `roof.form` | 表达的形态 | 选用条件和当前边界 |
|---|---|---|
| `hip` | 四面坡、长向正脊的庑殿形态 | `ridge_axis` 沿较长边；正脊长随长宽差变化。未制作垂脊、戗脊、吻兽完整构件 |
| `hip_and_gable` | 下部四坡裙檐，上部两坡及两个山花的歇山形态 | `gable_inset` 控制上部山墙内收；山花用墙材填实。此变量不是宋式收山的完整术语映射 |
| `overhanging_gable` | 两坡屋面伸出山墙的悬山形态 | 山墙在柱网外缘，屋面沿正脊方向继续外伸 |
| `flush_gable` | 两坡屋面端部与山墙对齐的硬山形态 | 沿脊方向出檐为0，檐面方向仍有出檐；当前要求 `uplift=0`。尚无封火山墙或马头墙 |
| `pyramidal` | 四面坡汇于一个中心顶点的方攒尖形态 | 当前限定正方形屋面；不能表示六角、八角、圆亭或宝顶细部 |
| `barrel_gable` | 圆弧状顶部、不设置正脊的卷棚形态 | 抛物线是设计选择；端部仍外伸山墙，当前要求 `uplift=0`。不把尖脊改名为卷棚 |

悬山和硬山的两坡高度函数相同，区别由**屋面端部与山墙的位置**形成；不应仅用两条不同曲线冒充两种构造。卷棚使用平滑冠部曲线并取消脊条。庑殿与攒尖的长宽相同时可趋于同样的四坡轮廓，需要靠正脊长度和顶点形式区分。

## 配置与尺度

```json
{
  "schema": "chinese-building/1",
  "kind": "hall",
  "bays_x": [6, 6, 12, 6, 6],
  "bays_z": [6, 6],
  "column_height": 11,
  "door_bay": 2,
  "roof": {
    "form": "hip_and_gable", "ridge_axis": "x",
    "rise": 7, "curve": 1.5, "uplift": 1, "gable_inset": 3
  }
}
```

- `bays_x/bays_z` 是相邻柱中心的整数距离，每项3～16，每轴1～11开间；**当前每轴总跨度必须为偶数**，保证屋面围绕方块中心。支持3/4/3、7/7和6/12/6；暂不支持总跨度21的7/7/7。
- 柱高为连续柱材数，不含柱础、托臂和梁。台基1～8、柱高4～24、出檐1～8；这些是本程序尺寸范围，不是历史建筑规范。
- `door_bay` 为正立面（-Z）开口的0起始开间序号。省略时取中间偏左开间；偶数开间建筑的门不会自动改到被柱子占据的轴线上。通路按实际门中心检查。
- `kind=pavilion` 去掉墙窗和内柱，保留周边柱。当前只有方形/矩形平面，不能借此声称已生成六角亭。
- `roof.ridge_axis=z` 将屋顶朝向改为Z，正立面入口仍为-Z；转整栋需用组件旋转。长宽、正脊与门向不是同一个概念。
- `roof_mix` 沿用 `{weight,block,stairs,slab}`。混色按坐标及种子确定，不复制源存档的拼色排列。
- `palette` 使用和旧大殿相同的材料角色。导出不携带箱子内容、命令方块指令等方块实体；自定义方块状态仍应在目标版本注册表中验证。
- `facade_pattern` 可为 `cross`（横竖分隔）、`plain`（无格条窗面）、`lattice`（隔格条）或 `solid`（实墙）。窗格按每开间净宽计算；窄窗、矮窗自动省去会填满窗面的格条，`solid` 明确没有窗面。这些是体素图案，未对应具体历史隔扇分类。
- 需要独立窗洞与真实墙边时使用 `framed`、`framed_pair`、`framed_round`、`framed_arch` 或 `framed_shutter`。玻璃组最大包络为7×5格，四边保留墙材，开间增大不会让窗格扩成整墙。净宽或高不足3格会拒绝生成；容不下双窗、拱或窗扇细节时保留单个小开口，不能把类型名当成该尺度下已表现出的细节。
- `facade_overrides` 可逐面逐开间覆盖图案，例如 `[{"face":"left","bay":1,"pattern":"framed_pair"}]`。`front/back/left/right` 分别是 -Z/+Z/-X/+X；开间从该轴负端起0计数。覆盖只作用于柱间，拒绝重复、越界、正门开间和开放亭的墙窗覆盖。使用双窗时检查实际净宽；7格净宽可表达中梃，5格净宽会退化为单窗。

紧凑起点 `assets/compact-measured-hall.json` 采用合集楼塔实测的3/4/3外柱行节奏与4格连续柱材，生成17×16×18、1,386格的单层厅堂。内柱、门窗、原版配色、台基与屋面为原创。它不能代表原楼塔的完整复原；药铺的3格柱段和4/3/4奇数总跨度也未被自动等同于这个起点。

有测量依据的起点：`assets/nonuniform-xutian-hall.json` 采用6、6、12、6、6节奏与11格柱高；`assets/nonuniform-temple-hall.json` 采用7、7与6、12、6两条外框节奏。后者把不同外框组成新平面，证据**没有**证明原建筑各交点都是承重柱。

```text
python scripts/generate_building.py --config assets/nonuniform-xutian-hall.json --out-dir generated/xutian-new --name xutian-new
python scripts/generate_building.py --config assets/square-pavilion.json --out-dir generated/pavilion --name pavilion
python scripts/test_components.py
```

## 可重用组件接口

`voxel_model.Model` 使用XYZ坐标、`blocks[y,z,x]`数组、含端点边界。设置、填盒和粘贴均先检查边界；越界抛错，不允许NumPy负索引回绕。默认最多800万格。

```python
from voxel_model import Model
from generate_building import make_building

part, design, config = make_building({"kind": "pavilion"})
scene = Model((-80, 0, -80), (80, 60, 80))
receipt = scene.paste(part, offset=(30, 0, 20), turns=1)
scene.save("scene.npz")
```

`turns=1` 从上方看顺时针90度，北→东；先围绕局部原点旋转，再加XYZ偏移。模型原点也随旋转改变。方块 `facing`、原木 `axis`、16向 `rotation`、四向连接键、铁轨形状、拼图块 `orientation` 一起转换；楼梯的内外左右、门铰链和箱子的左右都是相对方向，旋转时保留。未推断模组自定义方向属性。

`paste` 默认跳过空气，允许同状态重叠，拒绝不同状态的已占用格；边界和冲突检查成功后才改目标。确实需要覆盖时指定 `conflict="replace"`；保留目标用 `"keep"`；`include_air=True` 加 `replace` 可明确挖空。收据给出写入数和冲突数，便于组装时发现屋檐、平台或通道碰撞。

`roof_kernel.make_roof` 返回独立模型和 `surface_y_rows_zx` 表。楼体支撑从报告读取屋面高度，不重复发明一套高度函数。半砖、楼梯高度尚未用于屋面碰撞求交；屋面连通指体素六邻域连通。

## 当前验收与尚待补充

已检查六种形态、X/Z方向等价、1/8/20/40格屋面升高、多种柱网、门口及两格净空、模型连通、Sponge回读与固定配置确定性。极端曲率和抬角虽通过几何检查，不代表比例美观。新入口默认采用 [shape-preview.md](shape-preview.md) 中的细块形状预览，仍不能替代真实纹理和游戏走行验收。

多层建筑应分离楼板、檐廊、顶层屋顶和竖向交通；不可把带实心屋顶的单层房子逐层堆叠。组件已用于 [单进院落](courtyard-generation.md)、[多进院](multi-court-generation.md) 和 [楼阁与连廊](circulation-generation.md)。
