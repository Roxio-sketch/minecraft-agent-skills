# 九个新增功能样本的局部测量

本次复查的是已经下载的 **More Chinese Structures 1.1.1** 中九个文件，仍属同一个来源包，不新增九名作者或九种独立风格。作者记录为 **shaoxiongduan（Owner）、Silicon23（Member）**，来源与ZIP哈希见 [sources.json](sources.json)；143文件目录见 [regional-collection-catalog.json](regional-collection-catalog.json)。城堡上下段属于同一组拼装构件。

[标注文件](expanded-functional-annotations.json) 为每个选区记录模型路径、原始NBT哈希、包围盒和完整查询正则；[实测结果](expanded-functional-measurements.json) 记录NPZ哈希、逐层材料、状态计数、边界和门半配对。计数单位是**方块格**。`workstations`包含书架，床计数包含两半，双门包含两个门扇；均不自动换算为房间数、职业数或可交互设施数。

| 文件（省略`.nbt`） | 实际观测 | 可以用于新设计的关系 |
|---|---|---|
| `castle/castle_base` | 31×37×31；47格工作/书架材料、133格箱桶、36格床、24格梯子、32对门半 | 先分楼层与服务区，再安排储物和楼层交通；不要把箱子堆积直接当成133个库房 |
| `castle/castle_top` | 31×18×31；8格工作/书架材料和1格储物，储物位于Y8 | 上部模块仍有内部使用内容；与底段的连接须按拼图或坐标证据单独确定 |
| `temple/temple_hills` | 21×32×21；储物Y14有2格、Y22有1格，上部查询带含384格指定深色材料 | 多道高处屋面不能直接等同可用楼层；内部目标需要实际地板和交通 |
| `tower/tower_3` | 47×36×47；72对门半，14格储物集中Y0/1，上部指定材料108格 | 门可能服务外立面图案与围护；72对门半不代表72个独立房间 |
| `village/hui/plains_armorer_house_1` | 11×8×8；Y1有1格工作设施和1格储物；1对门半 | 紧凑空间保留入口到操作位，再放工作块和存储 |
| `village/hui/plains_butcher_shop_1` | 11×8×12；Y1有1格工作设施；3对门半 | 临街开口与后部空间分别考察；没有储物匹配也不能推断没有经营活动 |
| `village/hui/plains_library_1` | 15×15×17；42格工作/书架材料分布于Y1～3与Y8～10；6格储物在Y1/8；34对门半 | 书架与存储出现两组竖向带，新增藏书楼按实际楼板和楼梯组织使用区 |
| `village/hui/plains_stable_1` | 7×7×17；上述工作/储物/床查询均为0；1对门半，上部深色材料119格 | 不能用工作块数量否定马厩用途；栏舍、开口和实体载荷需要另查 |
| `village/yurt/buildings/yurt_stable_01` | 11×9×11；上述查询和门半配对均为0 | 帐形附属建筑与居住帐居分别看待；零匹配不是“空建筑”或类型错误 |

局部形状图与剖切用于核对遮蔽空间。对于藏书楼，重点看Y1/2与Y8/9，并用竖向剖面确认两组材料所在层；看到两个书架高度带，只能提出“两组使用空间”的候选，不能跳过楼梯检查。原模型的实体、库存和脚本未运行。

## 可复算命令

以下`MODELS`指批量导入后的根目录，目录下有`castle/castle_base.nbt.npz`等相对路径。安装后的脚本、标注和输出均解析成绝对路径。

```text
python scripts/measure_layout.py --samples-dir MODELS --annotations references/expanded-functional-annotations.json --out measurements.json
python scripts/render_sections.py MODELS/village/hui/plains_library_1.nbt.npz --axis y --at 1 2 8 9 --out library-floors.png
```

实际CLI参数以`--help`为准；`--at`使用模型坐标而非图像像素。本批没有借助素材复制生成新建筑。提炼的关系落实到 [可居住建筑与功能组团](functional-generation.md)：按用途提供不同设施、按层提供站立目标、放置前验证通路，并为楼梯保留交通带。预设中的尺寸和设施数量是新设计选择，不照搬上表坐标。
