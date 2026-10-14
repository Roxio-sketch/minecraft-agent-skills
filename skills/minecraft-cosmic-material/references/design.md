# 从艺术目标生成材质方案

不要把“任意设计”解释成已有 loader 的 JSON 可以设置任何效果。现有 `cosmic` loader 主要解析 mask；层数、配色、密度和速度写在 shader 内。先把设计翻译成参数与适配需求，再实现缺少的接口。

本页负责把材质创作对接模型和绘制系统。**具体内部星空怎样做**，先读 [material-authoring.md](material-authoring.md)；自然语言与算法/参数的对应关系见 [material-recipes.md](material-recipes.md)。不要只完成本页的参数表就宣布完成了材质设计。

## 设计记录

用短表或项目已有格式记录以下内容，无需强制创建新的配置系统：

| 维度 | 需要明确的决定 |
|---|---|
| 对象 | 物品 ID/模型类别、版本、loader、GUI/手持/掉落/穿戴范围 |
| 覆盖 | 全物品、局部 mask、模型材质区域；保留边框/握柄/宝石 |
| 锚定 | 参考视线驱动、世界方向固定、物体局部、屏幕或有限深度 |
| 图案 | 星点、星芒、符文、星云；图集 sprite/程序场/混合 |
| 层 | 层数、旋转 seed、尺度分布、亮度权重、密度 |
| 颜色 | 背景、星体 palette、亮度、色彩空间、是否参与环境光 |
| 动态 | 时间单位、滚动/脉动/闪烁、暂停、GUI 与世界的一致性 |
| 合成 | 覆盖 alpha、深度策略、透明物品、glint、Halo 或 bloom |
| 成本 | 最大覆盖面积、物品数、目标硬件、质量档与降级 |

Cosmic 核心与 Halo 分开设计。参考 Halo 是 GUI 背后的大 quad，配有抖动缩放的基础模型副本；不会自动成为三维轮廓光，也不包含屏幕模糊 bloom。若用户想要穿戴边缘光、屏幕外扩发光，要选择法线边缘层或后处理，并为它单独确定深度和颜色输出。

## 三个可复用设计例子

**紫蓝无尽剑。** 原握柄与刃边保留；剑刃内部用 R mask。先保留参考 16 层和方向机制，改背景与各层 RGB 分布。GUI 单独设尺度，世界使用实际相机策略。普通二维剑可用 loader；三维剑应重放原几何。保持原 glint 是独立合成选择，不能因 cosmic wrapper 绕开原 renderer 而意外丢失。

**金色符文魔导书。** 书封内部 mask；替换十种星体为灰阶符文 sprite，仍用红通道存亮度，黑底而非透明白底。增加稀疏阈值、暖色 palette、较慢时间系数。若希望字符始终正立，应限制 tile 旋转/翻转。普通物品图标和打开后的动态书本分别适配，不能用一个图标 mask 推断动态模型。

**红黑星云三维斧。** 原模型多个材质面分组，金属外圈保留；在方向域叠加低频三维噪声颜色场与稀疏星点。星云是新增算法，现有 cosmic PNG 改色并不会生成这种云。先用少量 octaves 的表面材质方案；只有用户明确需要体积遮挡/进出边界时再增加射线积分。工具动作和第三人称左右手保持原模型变换。

以上三个例子仍是设计蓝图。技能另外随附 [原创 WebGL 制作台](authoring-workbench.md)，有三份可调的星海/星云练习；它们不是这三个物品设计的已完成 Forge 实现。颜色与质量数值应按实际画面调整，不固定成跨项目规范。

## 推荐的通用结构（待在目标工程实现）

```text
MaterialSelection(ItemStack, resolved model, context)
    → MaterialDefinition(mask policy, field algorithm, parameters)
    → GeometryAdapter(baked quads / ModelPart / animated renderer)
    → DrawPacket(geometry, matrices, immutable params, textures, depth policy)
    → MaterialRenderer
```

MaterialDefinition 不应依赖无尽装备具体 Item 类。DrawPacket 捕获实际绘制需要的值，不长期持有可变 ItemStack、PoseStack 或旧 uniform。基础模型状态改变时更新几何选择；材质颜色改变时不必重烘焙全部模型。

全局参数适合按帧上传；同材质参数可分组；每物品变化量在 flush 之前保持稳定。设计几十种不同 palette 时必须同时考虑批次策略。更改 sampler 数量、sprite 数量或 shader 层预算，要同步更新 Java、JSON 和 GLSL，不能只把一个数字写进未经解析的 JSON。
