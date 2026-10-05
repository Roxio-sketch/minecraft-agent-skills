# 从描述到可制作的黑洞

## 把主形分开设计

| 用户描述 | 应调整的关系 | 不应混淆 |
|---|---|---|
| 黑洞压迫感强、暗核更大 | 暗核与整个可见盘的比例、周围留黑 | 增大黑色圆不自动增加偏折 |
| 巨大的薄吸积盘 | 盘宽、厚度、倾角及窗口构图 | 盘更薄需要更多采样，不只是 alpha 更低 |
| 火焰般金红盘面 | 内热外冷的配色、径向亮度、旋臂密度 | 内外色不同于物体两侧亮度不同 |
| 冰蓝奇点、紫色神秘漩涡 | 冷/热两端 HEX 和盘结构 | 不限制物理黑体配色；这是美术材质 |
| 黑洞本身为紫色、红色或白色 | coreColor 独立控制核心源颜色 | 不更改 hot/cool；前景盘仍遮挡核心 |
| 背面盘绕到黑洞上方 | 射线弯曲、观察角度、盘倾角共同作用 | 画一圈圆环不证明盘的背面被偏折 |
| 缓慢旋转、像流入核心 | speed 与 infallSpeed 分别控制角向与径向输运 | 旋转、纹理向内流动、粒子缩小半径是不同能力 |
| 剑里封住黑洞 | 局部内部场、mask 和焦点位置 | 扭曲内部星图与扭曲真实世界是两套接入 |

先留出暗核和盘内间隙，再决定盘面大形，最后加入纹理。低密度外盘应仍看见部分背景；提高曝光不能代替提高气体密度。背景星点只作偏折参照，不要压过主天体。

## 已实现参数

所有长度都是预览的无量纲局部单位；time 是秒。JSON 核心/热区/外盘颜色是 sRGB HEX，host 转为线性 RGB 后传给内核。

| JSON → GLSL | 范围/单位 | 作用 |
|---|---|---|
| core → uCore | 0.2–0.95 | 不透明核心半径，与颜色独立 |
| coreColor → uCoreColor | #RRGGBB；默认 #000000 | 核心源颜色；按前景盘的剩余透射率合成 |
| inner → uInner | 0.55–3 | 盘内半径；至少大于 core 0.12 |
| outer → uOuter | 2–8 | 盘外半径；至少大于 inner 0.5 |
| thickness → uThickness | 0.045–0.35 | 盘垂直高斯密度尺度 |
| tilt → uTilt | −90–90 度；内置新预设默认 0 | 盘面绕局部 X 轴旋转 |
| bend → uBend | 0–2 | 美术偏折强度，不是太阳质量或 Schwarzschild 半径 |
| density → uDensity | 0–10 | 消光/发光气体倍率 |
| speed → uSpeed | 0–3 | 差速角向旋转；不控制径向输运 |
| infallSpeed → uInfall | 0–2；材质单位/秒 | 正值使纹理特征向小半径输运；缺省为 0，内置配方为 0.22 |
| seed → uSeed | 整数 0–65535 | 稳定纹理/星点布局 |
| hot / cool → uHot / uCool | #RRGGBB | 内热区/外盘源颜色 |
| steps → uSteps | 96 / 160 / 256 | 射线路径预算；盘层内另有按厚度细分的密度采样，不改变整个物体半径 |
| exposure → uExposure | 0.01–2.5 | host 显示曝光，不改变物品 mask |
| yaw / pitch → uYaw / uPitch | 弧度；±π / ±1.3 | 预览相机观察角度，独立于盘倾角 |
| mode → uMode | scene / blade | 内部空间或示意剑刃窗口 |

导出形式：`{schema:"black-hole-preview/v2",parameters:{…},time:1.7,playing:false}`，仍接受 v1。这是工作台格式；自带 Forge 示例通过 /blackhole import 显式导入，支持字段与宿主差异见接入文档，不是任意 mod 的通用配置。time 范围 0–100000 秒，到上限暂停。speed/infallSpeed 均为 0，且粒子关闭或 particleSpeed=0 时固定 GPU 运动时间。

v2 包含 diskStyle（classic/granular/ribbons/dust/cinematic）、particles（布尔值）、particleCount（整数 0–4096）、particleSpeed（0–4）、particleSize（0.005–0.15）、particleOuter（3.2–8）、particleBrightness（0–5）、particleCold/particleHot（HEX），以及 halo/haloRing/haloDisk/haloAura（各 0–3）。旧配方缺省为 classic、particles=false、halo=0；完整组合预设显式启用全部层。具体分布、颜色与运动方法见 [粒子与光晕](particles-and-halo.md)。

径向纹理坐标采用 `r + uInfall*t`（按完整径向周期取模），固定相位特征满足 `r(t)=r(0)-uInfall*t`。这实现了盘面纹理向内输运；不是流体动力学、吸积质量守恒或吸取实体。内缘密度渐隐表示消散，参数也可独立设零验证。

`coreColor` 是 v1 的可选新增字段：旧配方省略时使用 `#000000`，新导出显式保存。它是源颜色，受前景吸收、盘辐射、曝光和显示转换影响，不保证最终像素与输入 HEX 完全一致。彩色核心是美术设定，不代表真实事件视界发光；不要在最终画面上涂圆覆盖前景盘。

导入后以该配方的 yaw/pitch 作为“恢复配方视角”的基准，下拉框显示自定义名称；切换预设后基准改为该预设。根对象和 parameters 的未知字段会带字段名报错，拒绝整份输入并保留当前状态；不静默删字段。playing 若出现必须为布尔值，导入始终从暂停状态开始。

## 需要额外开发的设计

Forge 世界示例和 v2 工作台已有独立螺旋粒子、颗粒盘和三层光晕。Forge 世界模板也已接入喷流；Forge 物品内部的这些层、实体物质吸取、倾角双轴控制、准确多普勒/引力红移、光影兼容和任意自定义模型遮罩仍需要额外接入。世界屏幕空间透镜边界见 [Forge 接入](forge-integration.md)，不能据此声称能看到离屏或原本遮挡的场景。

窄剑刃可能只容纳盘的一小段。先调整局部虚拟相机、缩放和焦点，使暗核/盘关系可读；真实模型适配时这三个参数需要新增 host 接口。16 px 图标可使用更粗主形或单独构图，但要标注与近景的差异。不要将高分辨率截图缩小后当成原生小图验证。
