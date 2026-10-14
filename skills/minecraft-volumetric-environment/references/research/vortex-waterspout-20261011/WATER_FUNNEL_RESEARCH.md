# 连续液态水漏斗与上段破碎

日期：2026-10-11。用户已选择“多层不规则水片与飞溅”。目标：海面卷起相互交叠的液片，整体形成可辨认的旋转水漏斗，向上逐渐收束、拉伸并出现细颈和碎水。此前几条等粗螺旋水丝不能承担主体形状。本轮不重讲已读 GPU Gems/Valve 的基础材质，也不以自然天气里的水汽比例否定用户的奇幻吸水目标。

本文只新增研究；未修改预览、体积核心、旧研究或 `WATER_ITERATION.md`。保持当前二十个事件字段，下面只提供内部形状与运动方法，不要求增加公共参数。

## 新的一手来源与读取范围

| 来源 | 实际读取范围 | 可迁移原则 |
| --- | --- | --- |
| [MIT 13.021 Marine Hydrodynamics Lecture 8](https://web.mit.edu/~fluids-modules/www/potential_flows/LecturesHTML/lec08/lecture8.html) | Vortex Lines and Vortex Tubes、Conservation of Vorticity Flux、Vortex Structures、Vortex Stretching 文字及网页公式说明 | 涡管是连续的物质结构；拉伸与截面收缩、旋转增强相联系。可用来设计一个连海面的连续体及沿高度变化的流动，不能据此宣称实时特效满足真实连续性方程。 |
| [Physics-Inspired Topology Changes for Thin Fluid Features](https://faculty.cc.gatech.edu/~turk/my_papers/thin_fluid_features.pdf)，Chris Wojtan、Nils Thürey、Markus Gross、Greg Turk，SIGGRAPH 2010；[作者出处索引](https://faculty.cc.gatech.edu/~turk/pubs.html.old06) | 物理第 1–2 页引言/贡献、第 5 页 §4.3 Topological Control；第 1–2 页已本地渲染并查看图 1–2 | 薄液片与细液丝需要不同的拓扑处理，不能用单一分辨率阈值一起消失。细颈可断成液滴；主体薄片应保持可追踪的连接与边缘。图示能看到片、边缘丝和滴的层次。 |
| [SideFX Whitewater (SOP)](https://www.sidefx.com/docs/houdini/fluid/sopwhitewater.html)、[Whitewater Source](https://www.sidefx.com/docs/houdini/nodes/sop/whitewatersource-.html) | Sources 与 Deformation sources 中 Curvature、Acceleration、Splash、Stretch、Squish、Scale 及发射原则 | 白水与碎水按局部变化产生：例如曲率与运动方向、加速、拉伸、压缩或稀疏区域。它们应继承主体运动，不是对整根水柱平均撒点。 |

另检查了 [Screen Space Fluid Rendering with Curvature Flow](https://wstahw.win.tue.nl/edu/2IV06/andrei/particle_rendering/provided/p91-van_der_laan.pdf) 的 §3.1–3.5 相关段落（深度、平滑、厚度、随动噪声与合成概述），以及 [SideFX Particle Fluid Surface](https://www.sidefx.com/docs/houdini/nodes/sop/particlefluidsurface.html) 的 Methods/Filtering。它们提醒我们：液面与孤立球滴不同，任意高斯平滑会损失轮廓或细节。本轮没有采纳整套屏幕空间重建，也没有声称执行这些软件。

曾查找 UTA 的 Free and Forced Vortices 实验页，但正文返回访问限制；不把搜索摘要当作已读推导。自由涡实验视频也没有观看。文中没有以这些资料支撑具体公式。

第三方论文与渲染页仅缓存于 `primary-pdf/` 阅读，交付包应排除该目录。只分发本研究、来源链接与读取范围，不分发全文、配图或作者代码。

## 三个可直接转为原创 GLSL 的要点

### 1. 主体由层叠不规则液片构成，旋转不是三根等粗几何绳

MIT 的关系支持一种构图思路：收束的截面和增强的流动组成一个连续体；本文给出的半径与速度函数是艺术拟合。[MIT Lecture 8](https://web.mit.edu/~fluids-modules/www/potential_flows/LecturesHTML/lec08/lecture8.html)

制作顺序先是海面—宽基座—细喉—上升段，再给表面旋转。可定义轴线 `C(y)` 与低频半径：

```text
localY = y - seaHeightNearAxis
R(localY) = neckRadius + baseFlare * exp(-localY / contractionHeight)
surfaceRadius = R(localY) + broadFold(localY, angle, time)
```

`baseFlare` 与 `contractionHeight` 决定根部是否像海面抬升，而不是管子插在海里。`broadFold` 是连续的较宽皱褶，可以来自本项目自有低频噪声；不把固定三个角度位置设置为整根水柱的存在区域。

每一层可使用不同的半径偏移、厚度、角向宽度和局部折叠；各层部分交叠并留不齐缺口，不强制做成封闭的圆形管壳。将根部的液体内部与现有海水内部做平滑并集，在交界范围改变曲率，使下段看起来从海面卷起。上段水片可以分离，不能用规则缺口把所有层切成等间距弹簧。

先只渲染这一连续液面，关闭滴和雾，以轮廓验证：从海面到上升段存在连续视觉关系；移动相机可读弯曲表面，而不是三股等间距亮线。

### 2. 上段先形成宽水片和边缘细颈，再断成碎水

薄特征论文区分了液片和细丝的行为；其网格拓扑算法不是本工作台的实现。可独立借用“保持片状连接，细颈才断开”的视觉规则。[Wojtan 等，2010](https://faculty.cc.gatech.edu/~turk/my_papers/thin_fluid_features.pdf)

- 中下段保持连续液体；大的表面折叠产生起伏，不用高频噪声直接把它挖成筛网。
- 随高度/拉伸增加，在外表面形成少量不均匀薄片。水片的宽度明显大于厚度；边缘允许加厚成短水丝，而非所有区域都管状化。
- 只在上段变薄且较强形变处开小裂口、产生细颈。裂口沿原流向增长，剩余边缘保持连片；避免整圈同时断裂或均匀三个孔。
- 一条细颈缩小后再以液滴替代，液滴从断开处继承位置、切向速度和上升速度。随机分布只决定局部差异，不替代连续的事件过程。

可在着色函数中保留一个内部 `sheetThickness` 和一个上段 `stretchMask`，用它们控制局部拓扑变化。对于隐式壳，厚度沿表面变化；对于实心水体，用局部细颈半径判断破碎。宏观皱褶、片状变薄和小滴是三个尺度，不应共享同一正弦幅度。

这比不断增加水丝条数更直接：从三股改成十股均匀绳仍然没有液面。

### 3. 旋转皱褶和碎水沿同一流动结构运动

SideFX 的形变/曲率等发射条件提供了来源定位的原则；本工作台可计算便宜的艺术代理，不需要先实现 FLIP。[SideFX Whitewater (SOP)](https://www.sidefx.com/docs/houdini/fluid/sopwhitewater.html)

对粗液面使用连续的逆流映射，低频皱褶沿旋转与上升一起移动。可让受限角速度随着收束增强，但它只驱动纹理和表面形变；水体的整体存在应继续是连续漏斗。噪声输入可用 `cos(angle)`、`sin(angle)` 和高度坐标，避免 `atan` 接缝处忽然跳变。

破碎位置由上段拉伸、边缘变薄或弯折较强的区域决定。简化的内部发射强度可以是：

```text
breakMask = upperGate * thinNeckGate * deformationGate
sprayAmount = max(edgeLeadingGate, accelerationProxy, breakMask)
```

这里的 `deformationGate` 与 `accelerationProxy` 是本项目艺术代理，不是假称从求解器取得的应变/加速度数据。它们可以来自已有形状导数或预先限定的流向区域。平稳、厚实的根部主要是透明液面，细碎水集中在破碎与抛甩区域。

孤立滴用种子和出生时间决定轨迹：初期继承主体上升/旋转，离开水片后才独立弯落或甩出。以 `age` 控制大小与消失，避免每帧重新随机位置。不要把所有滴排成恒定螺距的同一螺旋线。

## 实现时的限制

上述隐式函数一般不是精确 SDF。不能因为函数数值叫 `distance` 就按它进行任意大步的球面追踪；窄水片和细颈仍需要保守步长、区间捕获与交点细化。若一条薄片低于像素/采样能力，应平滑降低其贡献或转成滴状表达，不能产生稳定的棋盘漏孔和闪烁。

表面法线应来自最终变形后的函数，液体厚度与透射也应随实际形状变化。仅有法线纹理向上滑动、主体仍是三股圆管，不满足这一轮迭代目的。

## 本轮应看见的变化

- 关闭全部雾仍可辨认多层连海面的液片形成的整体漏斗，主体无需液滴才能成立，也不必封闭成圆管。
- 下段宽基座/上段收束在两个相机角度都可读，根部没有明显插管接口。
- 上段存在宽薄水片、短边缘水丝与不规则滴，细节没有固定三股分布。
- 三个相邻时间能看到片的拉伸、细颈收缩和滴离开，不能只看一张成熟静帧。

这些是研究转为作品后的验收目标，本文件未宣称渲染已达到它们。
