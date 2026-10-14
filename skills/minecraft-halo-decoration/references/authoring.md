# 用文字设计 Halo

## 设计选择

| 描述 | 制作起点 | 不能混淆的效果 |
|---|---|---|
| 低调的青色呼吸光 | 宽柔光 + 细环，4 秒周期，alpha 与 scale 轻微变化 | 主体放大不等于光晕呼吸 |
| 金色双层法阵 | 分段外环 + 反向内环 + 少量放射线 | 分段刻度不是有语义的符文 |
| 红色爆发感 | 较窄放射线 + 中央柔光 + 反相细环 | 当前脚本是循环装饰，不是一次性事件系统 |
| 手中悬浮的立体环 | 模型局部 quad/环网格，跟随握点 | 不是永远朝向相机的 billboard |
| 围绕任意物品轮廓的光 | 模型 alpha 膨胀、轮廓几何或后处理 | 方形柔光 PNG 不会自动识别轮廓 |

先确定物品可用窗口、留黑和焦点。窄剑适合细长外框/少量亮线；方形预览好看不保证放在剑背后合适。亮底上的加法光对比弱，可调整配色或增加单独 alpha 装饰层；不要悄悄将加法变成乘色。

## CLI

依赖 Python 3.10+ 和 Pillow。脚本无其他包；必要时从当前环境提供的 Python 运行库定位解释器。

```powershell
python <skill>/scripts/author_halo.py <skill>/assets/presets/gold-orbit.json --out <新输出目录>
python <skill>/scripts/verify_authoring.py
```

需要原生小图时优先读 [小尺寸配方](small-icons.md)。生成器增加 `--display-px 16` 可输出指定画布的 preview-target.png；范围 8–512，必须显式选择配方，参数本身不会自动加载 small。

输出目录必须不存在。调整方案时使用新的输出目录；脚本不递归删除、不覆盖已有资源。输出：分层静态 PNG、recipe.json、render-contract.json、preview-16/32/256.png、contact-sheet.png。小图以各自分辨率重新采样，不是大图缩小。

## 配方 v1：实际实现的字段

根对象：`schema="halo-decoration-v1"`，`id` 为小写字母起始的 1–48 个字母/数字/下划线/连字符，`size` 为 64/128/256/512，`contexts` 默认 `["GUI"]`，`layers` 为 1–8 层，从后往前。

| layer 字段 | 实际含义与取值 |
|---|---|
| shape | glow、ring、rays；分别是高斯柔光、环、角向放射 |
| color | 任意 #RRGGBB；写入 PNG RGB，不在预览中再次乘同色 |
| blend | alpha / additive；其他混合需另实现 |
| radius | .05–.88，以局部 quad 半边长为 1；glow 的衰减尺度、ring 中心半径、rays 长度尺度 |
| width / softness | .004–.2 / .005–.2；仅 ring 用作核宽与外晕宽，glow/rays 不读取它们 |
| segments / duty | 整数 0–96 / .05–1；ring 的分段数量与占空比，0 为整环；rays 至少 2 条，duty 控制角向宽度；glow 忽略 |
| inner_radius | 可选，仅 rays；0–.8 且小于 radius，从该半径往外以 .12 局部长度渐入；省略/0 保持旧算法 |
| opacity / scale | 0–1 / .1–2，运行变换，不烘焙进静态纹理 |
| motion | rotation_deg、speed_deg_s、period_s、phase_deg、scale_amplitude、alpha_amplitude |

motion 默认初始角与速度为 0，周期 4 秒，相位 0，振幅 0。旋转初始角 ±360°、速度 ±360°/秒、周期 .1–3600 秒、相位 ±360°、缩放振幅 0–.45、alpha 振幅 0–1。正旋转在 +Z 看向 XY 平面时逆时针，图像上方是 +Y；移植到 GUI 翻转坐标后核对方向。

```text
s = sin(2π * (t mod period)/period + radians(phase))
scale(t) = scale0 * (1 + scale_amplitude * s)
alpha(t) = clamp(opacity * (1 + alpha_amplitude * s), 0, 1)
angle(t) = (rotation_deg + speed_deg_s * t) mod 360
```

不要把 angle 突然从 359°→0°当作旋转倒退；位置连续。脉动周期闭合并不保证整层旋转同时闭合；组合循环长度由两者共同决定。高 opacity + 大振幅会触发钳制平台，这是当前数学语义，需要纯正弦时降低基准 opacity。超长运行时间应使用有界相位并保留双精度计时。

## 输出与游戏契约

PNG 只烘焙形状和颜色，**不含 opacity、scale、旋转或动画**；游戏上传顶点/矩阵时从 recipe 实施它们。颜色为 sRGB 色值，alpha 为线性覆盖；全透明区仍保留该层颜色，便于单色纹理线性过滤。默认只绘制一张静态 PNG 不会呼吸或旋转，也没有 `.mcmeta` 动画。

预览直接采样原创解析形状、每像素 2×2 样本、线性 RGB 合成后编码显示。实际游戏使用导出的 PNG 采样，过滤、光照、色彩空间和排序可能不同；预览不是运行截图。contact-sheet 的菱形只是原创遮挡示意，不是 Minecraft 模型。预览有 1.55 倍留边，极端 scale 可超出画面；缩小尺寸或扩大目标显示区，不通过覆盖邻格来强行容纳。

`contexts` 只记录接入要求，生成器不实现相机、模型挂点、第三人称或穿戴。想要倾斜环、非均匀形状、事件触发、径向渐变、真正符文或轮廓匹配时扩展工具/adapter，并补对应预览与测试；不要编造字段已经被游戏读取。
