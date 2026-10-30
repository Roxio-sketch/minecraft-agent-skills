# 功能建筑、内部设施与组团

统一使用 `architecture.py generate --config CONFIG --out-dir OUTPUT --name NAME`。参数都是 Minecraft 格数。以下是原创游戏空间，不能由类型名推断朝代、宗教仪轨或具体机构的历史布局。

## 可使用的房屋内部

`chinese-inhabited/1` 支持 `kind: hall | tower`。`building` 接受原厅堂或楼阁配置；`use` 选择 house、shop、apothecary、inn、classroom、library、office、workshop、kitchen、granary、shrine、refectory、bell、drum。`partition` 控制横向隔断；楼梯边保留交通带。`furniture_per_zone` 为1～8，尺寸容不下会报错，不能把家具挤进入口。

单层有用途房屋在未指定 `building.facade_pattern` 时采用 `framed` 小窗，并把选择写入规范化配置。显式指定的旧窗型仍保留；楼阁不受此默认值影响。逐墙分窗见[组件窗墙参数](component-generation.md)，不要靠整屋同样的格栅墙区分书房、灶房和商铺。院落密度与实际光照可用[现场修缮工具](field-refinement.md)测量。

每层分前、后两个使用区。物件包括成对床、书架、讲台、柜台、储物桶、工作台、炉灶、桌凳及抽象基座。每个物件附相邻的站立目标，放置前检查实际通路，避免把物件摆在楼梯栏杆后面的孤立位置。床记录 foot/head 和方向；箱桶为空、讲台无书，神位或供台只是中性基座。钟、音符盒只代表游戏设施，未验收实际交互。

```json
{"schema":"chinese-inhabited/1","kind":"tower","use":"inn","partition":true,"furniture_per_zone":2,"building":{"platform_height":1}}
```

## 七类组团

`chinese-functional/1` 的 `kind`：

| kind | 实际空间差别 |
|---|---|
| palace | 前部接见、后部内堂、独立居住楼、左右办公配殿和后侧厨房 |
| ancestral_hall | 大型集会厅、后部纪念厅、谱牒与议事两翼 |
| buddhist_temple | 前堂、主堂、藏书楼的轴线，独立钟鼓楼、居住与斋堂 |
| daoist_temple | 前后仪式空间、侧向经籍与居住、后侧服务建筑 |
| academy | 讲堂、藏书楼、左右课堂、后部宿舍与厨房 |
| government | 公共接见、行政办公、后宅，档案、登记与工役服务区 |
| market_street | 两侧店铺朝街，药铺/作坊/厨房等轮换；尽端为带楼梯的客栈 |

这些是不同的可编辑功能方案，不是用同一张图改七个名字。没有根据通用历史常识编造精确的神像次序、祭祀等级、监牢制度或后宫制度。机构依据见 [历史资料](historical-evidence.md)，实际 Minecraft 室内材料学习见 [新增功能样本](expanded-functional-study.md)。

共用参数：`spacing` 58～78；街市 `shops_per_side` 1～5；`enclosure` 开关、`wall_height` 3～6；`landscape` 控制庭前铺地与只放在空闲草地上的小树；`palette` 传入厅堂材质角色。`overrides` 以组件ID为键，可以改该组件的 `config`、`position_xz`、`turns`、`role`、`label`。嵌套 `config` 以给定对象覆盖同名字段；尤其 `building` 要提供希望保留的完整局部设置。

每次输出额外的 `NAME.complex.json`，包含完整组件、坐标、连接关系。要跨院、斜向功能轴、替换成土楼、增加园林或地形，就编辑这个文件，改用 `chinese-complex/1`；不必受七个预设限制。场地可到512×512，但最终模型仍受800万格预算约束，大项目分区生成。

报告列出全部房区、家具、楼层目标和每条路径。封住外门后的测试必须使内部目标不可达，避免外墙留下绕路。预览正面通常遮住室内，用 `render_sections.py` 查看楼板以上1～2层；图中近似色和通路不等于真实玩家碰撞。

## 已有城池的内饰与街道细修

先从现场 NBT 量出每间房的真实地坪、墙内可用矩形和出入口，再放成组桌椅、床铺、柜架或工位。每组设施同时声明完整占位和相邻使用位置；整体必须落在房内、有地坪承托，不能只检查中心点为空。保留原门口、梯井、转折平台和通路，放完所有内饰后再验原有房区与新增设施全部可达。吊灯从真实梁顶下挂，平面切片之外再看竖向支撑。柜架的空库存与建筑空壳是不同问题，不自动填充物品。

检查占路时区分建筑实体、檐口投影、临街门阶、牌坊、桥梁及街边摊。道路按实际地坪和桥面升降检查连续宽度、转角及净空；禁止通过房间内部绕路来宣称街道畅通。先保持完整房屋尺度，在建筑间明确铺出绕行路或接桥；不能把房屋剖开当成隧道，也不能把正常的梯井、屋架空腔和可使用房间填成实心。修补前后在最终组合上验道路、门外接路和各层外围封檐，原来每个组件单独合格不足以证明组合合格。

用户反馈房屋仍占原路时，按[街道与场地收口](street-site-finish.md)检查整个路幅和废弃铺装，不能再以绕路可达代替修正。重复街市改造同时改变建筑体量、院落形式和用途；暗处按实际方块光照补灯，避免只增加远离使用区的装饰灯。
