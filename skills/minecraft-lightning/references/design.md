# 设计与参数

## `orbit`

工作台中的 `rodLength`、`rodRadius` 是附着物示意；实际 Forge 接入时应把锚点替换成武器、法器或实体模型的棍状挂点。棍子只提供附着通道，不能把它的端点当成所有电流的统一发射端。`strands` 条 lane 都在这条固定轴向通道周围采样，每条有 `samples` 个采样段。默认 `orbitAnimation=grow` 时，每条 lane 在棍身上选定自己的固定局部根部，电弧从自身一端沿 `orbitDirection` 只延长已生长部分；`orbitWobble` 通过随轴向增大的门控抖动形状，不会让整条电流像蝌蚪一样整体移动。`orbitAnimation=spektr` 用 Spektr 的短段脉冲模型：`pulseInterval` 和 `throttle` 决定是否出现，`arcLength` 与 `lengthRandomness` 决定短段长度，短段在固定两端点通道上滑动，并叠加两档轴向噪声。核心宽度和光晕宽度独立；挂到实体时使用实体的世界锚点，模型空间挂点先转换到世界空间。 `orbitAnimation=ring` 改用物体周围的极坐标环弧：`orbitRadius` 是基准半径，`arcLength/orbitRadius` 是扫掠角，`orbitDirection` 控制顺逆方向；每条弧只显示一段，根部固定，避免闭环整圈同时发亮。

## `strike`

主干从 `start` 到 `end` 生成。每一代在相邻端点中点插入两个局部正交方向的高斯偏移，偏移幅度乘 `strikeRoughness`；首尾点永远保留。每次细分后把点投影限制在主方向的单调区间，，避免高粗糙度把闪电绕圈打结。分叉只从中段候选点采样，方向与父干保持正向夹角，再加入受限弯折；宽度、亮度和长度按深度衰减，最终以扁平 segment 列表喂给 ribbon renderer。远距离时可按距离降低 `strikeGenerations`、分叉概率和段数预算。

时序单位是 tick：`progressionTicks` 是先行通道到达时间，`durationTicks` 是总可见时间，`restrikeCount` 控制回击脉冲数量，`flicker` 是连续包络的调制量。渲染使用 `partialTick` 插值，暂停时不推进。相同 `seed`、端点和结构参数复现同一形状；需要变化时显式换 seed。

## `rain`

落雷场在 `rainSpread` 的水平范围内放置 `rainCount` 道近似竖直的独立主干，`rainDepthSpread` 控制纵深，`rainStagger` 以 tick 错开它们的先行时间。每道使用派生 seed，因此位置和形状可复现；`rainMaxBranchDepth` 强制不超过一层，`rainBranchProbability` 再与普通分叉概率取小值，避免落雷场变成树冠。

## 渐变

启用 `gradientEnabled` 后，用 `gradientStart` 和 `gradientEnd` 沿段的 `along` 坐标按 `gradientRatio` 插值，由 `gradientTarget=both/core/glow` 选择作用层，所选核心/光晕分别乘以各自基础色。主雷与分叉共用渐变。比例范围 0–100、默认 50；0 全终止色、100 全起始色、50 保留旧线性渐变，越高起始色占比越大。击地的所有材质坐标统一为主落雷末端 `along=1`，不会沿地面电弧重新渐变。渐变只改变颜色，不改变几何、seed 或时间包络。`gradientCoreMode=vivid` 默认以保色透明度混合显示渐变核心，防止高增益主雷中段截白；additive 提供传统加法。关闭渐变或仅光晕时该选择不改变核心。工作台分别显示两层起点/中点/终点实际材质色，色卡不含增益或场景混合。

## 预算起点

环绕电流默认最多 `16 × 48 = 768` 个采样段，但 throttle 和局部脉冲会显著减少实际发射；闪电默认最多 1200 段，核心绘制共享接缝四边形，光晕绘制凸段和内部圆接头并逐路径 MAX/ADD 合成。对象周围持续效果优先降低 `samples` 和 `strands`；单次雷劫优先降低分叉深度和 `maxSegments`。不要通过每帧生成新粒子解决亮度问题。



## 预览与渲染边界

工作台使用 20 tick/s 的真实时间推进；时间定位滑杆会暂停在指定 tick。`Spektr 参考场景` 接近原 Test.unity 的几何/时间参数（20 条、29 段、端点间距 20、长度比例 .8、长度随机 .9、出现概率 .1、间隔 .3 秒、噪声幅度 1.2、频率/速度 .1），核心/光晕宽度为预览匹配所调。`spektr` 模式会移动短段窗口，`grow`/`ring` 才固定每条弧的根部。

JS 与 Java 的环绕噪声和播种散列相同，但未复刻 Unity 的 float sin 散列；真实闪电的 Java 使用 SplittableRandom，JS 使用自己的随机序列，因此跨语言只保证各自种子可复现，不承诺逐点同形。

工作台使用 WebGL2，逐节点宽度与颜色、核心/光晕径向材质、亚像素能量补偿和单路径 MAX 后 ADD 与 Forge 对齐。场景参照仍是 Canvas2D，预览使用简化相机和 screen-space core miter；Forge 使用真实 VP、世界深度和原世界接缝。两者都不是原 Unity 场景的 HDR 后处理 bloom；背景曝光、随机树与遮挡仍可能不同。详见 [稳定渲染约束](mature-rendering.md)。

## 独立弧头速度

`orbitExtendSpeed` 范围 .1–120 格/秒，控制 `grow` 的轴向距离和 `ring` 的中心线弧长；不包含噪声造成的折线长度。弧头从每道电弧自己的根部出发，根部固定；`orbitFlow` 只改变噪声相位。每条 lane 有确定性的 onset、目标长度和周期差异，周期至少覆盖最大延长时间与淡出时间。慢速会降低实际脉冲频率，而不是把生长过程截断。源码 `spektr` 仍使用自身窗口时序。

## 集束落雷与击地参数

范围样式 `spread` 保留原分布。`cluster` 使用固定 seed 的黄金角分布，所有落点限制在以锚点为中心的椭圆内，X/Z 直径分别为 `rainSpread` / `rainDepthSpread`；仅限制落点，主干可在有限范围内弯折。`rainMaxBranches` 是每道主干的分叉总数硬上限（0–2），深度最多 1。集束预设默认 0 分叉，可通过控件再开启有限分叉。

| 字段 | 含义 | 默认 / 集束预设 |
| --- | --- | --- |
| impactEnabled | 击地效果总开关 | true / true |
| impactRadius | 径向电弧基准范围（格） | 1.25 / 1.6 |
| impactDuration | 从主干到达落点起计的寿命（tick） | 12 / 18 |
| impactRays | 径向短电弧数，1–24 | 9 / 10 |
| impactSparks | 抛物线火花数，0–32 | 14 / 18 |

每个落点预生成径向路径、3 段断环和火花初速度，沿径向先延长、然后淡出，断环随时间扩大。火花采用解析的固定 tick 弹道，数量与寿命有限。地面线段预算每道不超过 `8*impactRays + 48 + impactSparks + 2`（最大 274，最后两段为 Forge 闪光）；主干 `maxSegments` 另计。断环扩散最大略超过基准半径，火花也可飞出电弧范围，`impactRadius` 不是所有装饰的硬裁剪边界。

工作台和 Forge 的闪光都使用两道交叉发光 ribbon，并继承实际主落雷末端色；两者不承诺逐像素相同。新旧配方导入时合并默认字段，缺比例时补 50；旧配方会默认启用击地效果，可显式关闭。
