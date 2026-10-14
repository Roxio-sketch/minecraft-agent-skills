# 实时能量 VFX：源码体系与 Minecraft 1.20.1 Forge 学习路线

核查日期：2026-10-02。范围：研究阶段、HTML 资料工作台、可复用研究 skill。尚未实现能量渲染器或 Forge MOD，也没有实际游戏性能、视觉复刻或兼容性结论。

## 核心判断

最值得先做的技术路线是：**可控局部三维密度场＋程序化多尺度流动＋发射/吸收积分＋少量三维细丝＋独立冲击波和选择性发光**。当技能需要持续卷吸、烟与障碍物互动时，再增加二维或三维状态模拟。这是基于参考画面与下文源码作出的工程建议，并不是对原视频生产管线的逆向确认。

这套体系比寻找单个“energy ball shader”更适合灵气、异火、护盾和 Boss 技能：形状域、流动场、发射与密度、生命周期、后处理可以独立组合。薄壳护盾与稀薄灵气也不必强制使用同一种渲染器。

## 参考视频的证据与可推断范围

用户参考视频为 1920×1080、约 23.98 fps、164.71 秒。已对全片以约 4 秒间隔抽帧，并对 00:50–00:54、01:04–01:08 以 4 fps 检查连续帧。抽帧分析覆盖全片，但不是逐帧完整播放；片段时间为观察窗口，不代表素材精确切点。

画面明确出现 **The Infinity Pack**、结尾 **TRIUNEDIGITAL.COM**，后半段展示在合成软件中叠加灰度烟状素材、调整色相、使用 Screen 混合。官方页面也说明该产品包含 Energy、Shockwaves、Smoke、Embers、Dust 五组黑底视频素材，面向合成工作。[Triune Digital 产品说明](https://www.triunedigital.com/products/the-infinity-pack-vfx-assets-collection)、[Energy 素材说明](https://www.triunedigital.com/products/energy-vfx-assets)。

因此，视频可以用来定义视觉目标，不能用来证明这些素材是实时程序 Shader、某种特定流体求解器，或能按同样成本在 Minecraft 实时运行。原始素材怎样生成，公开页面和画面不足以判断；其中实际烟雾还可能包含实拍来源。[Smoke 官方说明](https://www.triunedigital.com/products/smoke-vfx-assets)。

| 观察窗口 | 可以看到什么 | 对实时研发的启发（推断） |
|---|---|---|
| 00:00–00:07、02:20–02:31 | 高亮核心、外围云气和局部弯曲细丝 | 核心发射、低密度外晕、连续细丝分别控制 |
| 00:48–01:01 | 灰度烟状能量薄片、空洞、卷动轮廓 | 关闭调色/发光，先研究密度与形变；不能只靠噪声颜色 |
| 01:03–01:11 | 多种破碎环、扩张波与合成爆发 | 环/壳几何、传播寿命、侵蚀与主体分层 |
| 01:14–01:26 | 密集火星和漂移颗粒 | 少量辅助轨迹强化运动；不是主体方案 |
| 01:33–01:58 | 尘雾、烟团、落尘等不同尺度 | 稀薄层、遮蔽和速度分层，必要时状态模拟 |
| 02:08–02:17 | 合成软件中的素材叠加和调色 | 合成管线影响最终效果；灰度结构与发射颜色分离 |

案例原视频的帧图仅保存在原任务工作目录，不随 skill 分发。

## 为什么会出现流动、翻滚、撕裂、聚合与爆发

- **流动**需要时间连续性。平移噪声只会整体滑动；旋转梯度与导数反馈的 flow noise 可产生卷动外观；对速度场积分或平流密度则让结构随流场移动。算法入口是 psrdnoise、Bridson 和 Pavel 的示例。
- **翻滚**需要有方向的大尺度涡旋与不同尺度的扰动。噪声层不能全部同速同向；涡量约束帮助状态模拟保留涡旋，但不是任意提高细节振幅的同义词。
- **撕裂与再生**来自形状密度的局部侵蚀、阈值变化与连续补充。云渲染源码的 detail erosion 是适合迁移的起点；它通常是程序化密度变化，不是真正网格拓扑断裂。
- **聚合**需要径向吸入、吸引子或密度源汇。单纯 curl 场无散，不会凭空压缩物质；把吸引场加进去后，应按最终场描述其散度。
- **爆发**来自参数曲线的协同：短暂增强发射、核心扩张、向外速度、波面传播、尾部消散。它不是把同一个球突然放大。
- **体积感**来自视线在三维域中的采样、遮挡与透射率。几层 billboard、Fresnel 球壳或“在球上播放二维流体”可以作为局部装饰或 LOD，但不足以完成内部体积目标。

以上是组合机制分析。关键基础资料：[JCGT flow noise 论文与配套代码](https://jcgt.org/published/0011/01/02/)、[Bridson curl-noise 原论文](https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf)、[GPU Gems 二维流体章节](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu)。

## 先分清三个容易混淆的“扭曲”

1. **密度域扭曲**：改变输入坐标，再求能量的密度或颜色。影响能量本身，不会自动改变世界背景。
2. **材质 UV 扭曲**：偏移某张纹理的 UV。Lodestone 的 distorted_texture.fsh 属于这一类，Sampler0 未自动成为世界颜色。
3. **场景背景扭曲**：复制已渲染的背景颜色，并结合局部偏移和深度重新采样。Effekseer 的 GL distortion shader 给出实际背景采样入口；Veil 提供研究 FBO 调度的 Minecraft 路径。

同样，**emission 自发光、Bloom 外扩辉光、体积散射、地形动态照明**是四件事，应分别选择参考源码。


## 效果组成 → 技术 → 阅读入口

| 效果组成 | 对应技术 | 推荐源码 | 验收方向 |
|---|---|---|---|
| 能量主体 / 体积 | 局部三维密度场＋发射/吸收积分 | [Clouds](https://github.com/SebLague/Clouds)、[psrdnoise](https://github.com/stegu/psrdnoise)、[Veil · 1.20 分支](https://github.com/FoundryMC/Veil)、[GPU-GEMS-3D-Fluid-Simulation](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation) | 关闭 Bloom 后仍有内部层次；侧视和近距离观察没有明显纸片感；进入体积与被方块遮挡时成立。 |
| 内部流动 / 卷吸 | Flow noise、势函数旋度、平流、涡量约束 | [psrdnoise](https://github.com/stegu/psrdnoise)、[Curl-Noise（SIGGRAPH 2007）](https://www.cs.ubc.ca/~rbridson/)、[KvantSwarm](https://github.com/keijiro/KvantSwarm)、[WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation)、[GPU-GEMS-3D-Fluid-Simulation](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation) | 低亮度慢放仍可追踪结构连续运动；不是纹理整体滑动或逐帧随机闪烁。 |
| 边缘扰动 / 撕裂 | 域扭曲、Worley 侵蚀、阈值与薄层消散 | [Clouds](https://github.com/SebLague/Clouds)、[webgl-noise](https://github.com/stegu/webgl-noise)、[psrdnoise](https://github.com/stegu/psrdnoise)、[KvantWig](https://github.com/keijiro/KvantWig)、[NoiseBall](https://github.com/keijiro/NoiseBall) | 外围持续形成与消失局部孔洞；轮廓保持可读，不出现规则硬球边缘。 |
| 能量丝 / 连续带 | 三维轨迹、吸引子、历史点、网格带或细管 | [KvantSwarm](https://github.com/keijiro/KvantSwarm)、[Curl-Noise（SIGGRAPH 2007）](https://www.cs.ubc.ca/~rbridson/)、[KvantWig](https://github.com/keijiro/KvantWig)、[渲染运行库](https://github.com/effekseer/Effekseer)、[Photon · 1.20.1 分支](https://github.com/Low-Drag-MC/Photon) | 能围绕核心、聚拢和分叉；绕相机观察没有全程同一平面；细丝只负责局部强调。 |
| 烟雾 / 气态层 | 密度输运、形状细节分层、局部三维流体 | [GPU-GEMS-3D-Fluid-Simulation](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation)、[Clouds](https://github.com/SebLague/Clouds)、[webgl-noise](https://github.com/stegu/webgl-noise)、[WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation) | 外层较慢且稀薄，内部更紧凑；避免无方向白噪声和均匀发亮的棉花团。 |
| 冲击波 / 能量环 | 环带网格、薄球壳、半径和厚度寿命曲线 | [渲染运行库](https://github.com/effekseer/Effekseer)、[psrdnoise](https://github.com/stegu/psrdnoise) | 波面从核心向外传播且寿命有限；爆发起点、最大半径、厚度与消散可独立控制。 |
| 背景 / 屏幕扭曲 | 独立世界颜色副本＋有界偏移＋深度遮挡 | [渲染运行库](https://github.com/effekseer/Effekseer)、[Veil · 1.20 分支](https://github.com/FoundryMC/Veil)、[WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation) | 有纹理背景确实被偏移；前景方块没有被背景错误覆盖；读取目标不与写入目标相同。 |
| 自发光 / 外扩辉光 | 线性发射、对象发光通道、模糊金字塔、色调映射 | [Shimmer · 1.20 分支](https://github.com/Low-Drag-MC/Shimmer)、[KinoBloom](https://github.com/keijiro/KinoBloom) | 关掉 Bloom 主体仍成立；开后亮点柔和扩散但不抹去细丝和内部明暗。 |
| 蓄力 / 爆发 / 消散 | 生命周期状态机＋参数曲线＋层间错峰 | [渲染运行库](https://github.com/effekseer/Effekseer)、[Photon · 1.20.1 分支](https://github.com/Low-Drag-MC/Photon)、[Lodestone · 1.20 分支](https://github.com/LodestarMC/Lodestone) | 蓄力吸入、瞬间释放、传播和尾部消散可辨识；暂停、回放和不同帧率下结果可解释。 |
| 火星 / 碎屑辅助 | 少量轨迹粒子、局部速度场、统一容量 | [Curl-Noise（SIGGRAPH 2007）](https://www.cs.ubc.ca/~rbridson/)、[KvantSwarm](https://github.com/keijiro/KvantSwarm)、[Photon · 1.20.1 分支](https://github.com/Low-Drag-MC/Photon) | 关闭辅助粒子仍能认出能量主体；同屏数量增加时总容量受控。 |
| 质量 / 性能与时域 | 有界步进、降分辨率、跳空、历史拒绝、LOD | [VolumetricCloudsTutorial](https://github.com/TylerDodds/VolumetricCloudsTutorial)、[Clouds](https://github.com/SebLague/Clouds)、[Veil · 1.20 分支](https://github.com/FoundryMC/Veil) | 记录 GPU 时间、屏幕覆盖率、同屏实例和硬件；不用网页帧率替代 Minecraft 测量。 |
| Forge 渲染接入 | Shader / FBO / RenderType / 世界渲染阶段 | [Veil · 1.20 分支](https://github.com/FoundryMC/Veil)、[Shimmer · 1.20 分支](https://github.com/Low-Drag-MC/Shimmer)、[Photon · 1.20.1 分支](https://github.com/Low-Drag-MC/Photon)、[Lodestone · 1.20 分支](https://github.com/LodestarMC/Lodestone) | 普通/Fabulous 模式、窗口 resize、F3+T、世界切换和近远遮挡都验证；shader mod 组合单独记录。 |

## 17 组源码阅读档案
P0 表示优先读懂的基础；P1 为第二轮深入；P2 为补充或带有明确限制的对照。优先级针对本任务，不是对项目总体质量的评分。所有条目只做静态源码检查，未运行外部 Unity / C++ / Minecraft 项目。


### P0 · stegu / psrdnoise

- 原项目：[stegu/psrdnoise](https://github.com/stegu/psrdnoise)；分支 `main`；固定提交 `419175a270862ce7ae692038fafafb42ec0427e9`。
- 参考价值：JCGT 2022 论文配套实现；旋转梯度、可周期平铺和解析导数集中在小型 GLSL 函数中。
- 学什么：区分整体坐标平移与梯度旋转；使用解析导数扭曲后续噪声层，形成大尺度卷动和细节拉伸。它是程序化流动外观，不求解流体。
- 迁移判断：高。优先把纯 GLSL 算法移植至局部三维密度场；限制每个采样点的噪声层数，必要时预生成三维噪声纹理。
- 边界：单独播放噪声不会自动产生蓄力、爆发或真实流体交互；旋转梯度也不是物质守恒的平流。
- 许可：MIT（GLSL 文件内）。[依据](https://github.com/stegu/psrdnoise/blob/419175a270862ce7ae692038fafafb42ec0427e9/src/psrdnoise3.glsl)
- 已核查的源码入口：
  - [src/psrdnoise3.glsl](https://github.com/stegu/psrdnoise/blob/419175a270862ce7ae692038fafafb42ec0427e9/src/psrdnoise3.glsl#L93) — `float psrdnoise`，第 93 行。
  - [src/psrdnoise2.glsl](https://github.com/stegu/psrdnoise/blob/419175a270862ce7ae692038fafafb42ec0427e9/src/psrdnoise2.glsl#L67) — `float psrdnoise`，第 67 行。

### P0 · Bridson / Curl-Noise（SIGGRAPH 2007）

- 原项目：[Bridson curl noise](https://www.cs.ubc.ca/~rbridson/)；分支 `论文配套归档`；作者示例归档，SHA256 见 evidence/source-manifest.json。
- 参考价值：原论文作者发布的二维和三维完整示例，能从势函数一路追踪到速度与轨迹积分。
- 学什么：curlnoise.h 的旋度有限差分；example_3dplume.cpp 的空间调制和边界处理；main3.cpp 的中点积分。空间调制应施加在势函数上再取旋度。
- 迁移判断：高。用它指导能量丝与少量辅助粒子的速度场设计，或以解析导数替代多次有限差分采样。
- 边界：无散噪声不会吸入或压缩能量。聚集需叠加有散度的径向吸引场或密度源汇；不要把结果继续称为全局无散。
- 许可：作者主页声明 public domain；依赖单独核查。[依据](https://www.cs.ubc.ca/~rbridson/)
- 已核查的源码入口：
  - [curlnoise/curlnoise.h](https://www.cs.ubc.ca/~rbridson/download/curlnoise.tar.gz) — `struct CurlNoise3`，第 43 行。
  - [curlnoise/example_3dplume.cpp](https://www.cs.ubc.ca/~rbridson/download/curlnoise.tar.gz) — `potential`，第 65 行。
  - [curlnoise/main3.cpp](https://www.cs.ubc.ca/~rbridson/download/curlnoise.tar.gz) — `get_velocity`，第 68 行。

### P1 · stegu / webgl-noise

- 原项目：[stegu/webgl-noise](https://github.com/stegu/webgl-noise)；分支 `master`；固定提交 `22434e04d7753f7e949e8d724ab3da2864c17a0f`。
- 参考价值：广泛引用的 Ashima / Gustavson GLSL 基础噪声实现，补齐 psrdnoise 没有的 Worley 和四维噪声。
- 学什么：cellular3D 的 F1/F2 距离结构用于侵蚀和空洞；noise4D 提供连续时间维；noise3Dgrad 提供梯度。
- 迁移判断：高。适合生成形状场和侵蚀纹理；与 psrdnoise 对比运算成本，避免每个 raymarch 步都重复高成本 Worley 搜索。
- 边界：fBm 增加频率层次，但不会自行产生真实的卷吸；密度侵蚀还需要形状遮罩与阈值控制。
- 许可：MIT。[依据](https://github.com/stegu/webgl-noise/blob/22434e04d7753f7e949e8d724ab3da2864c17a0f/LICENSE)
- 已核查的源码入口：
  - [src/cellular3D.glsl](https://github.com/stegu/webgl-noise/blob/22434e04d7753f7e949e8d724ab3da2864c17a0f/src/cellular3D.glsl#L32) — `vec2 cellular`，第 32 行。
  - [src/noise4D.glsl](https://github.com/stegu/webgl-noise/blob/22434e04d7753f7e949e8d724ab3da2864c17a0f/src/noise4D.glsl#L53) — `float snoise`，第 53 行。
  - [src/noise3Dgrad.glsl](https://github.com/stegu/webgl-noise/blob/22434e04d7753f7e949e8d724ab3da2864c17a0f/src/noise3Dgrad.glsl#L30) — `snoise`，第 30 行。

### P0 · Sebastian Lague / Clouds

- 原项目：[SebLague/Clouds](https://github.com/SebLague/Clouds)；分支 `master`；固定提交 `fcc997c40d36c7bedf95a294cd2136b8c5127009`。
- 参考价值：完整 Unity 云渲染实验，主体 Shader 直接展示密度、细节侵蚀、深度裁剪和光线积分。
- 学什么：sampleDensity 的形状与细节分层、边缘侵蚀；ray-box 交点与场景深度限制；透射率指数衰减、lightmarch、提前终止。
- 迁移判断：高（算法），中（工程）。把云的高度分布换成球、环、柱或自定义局部域；把太阳散射改成自发光与少量散射。
- 边界：这是云测试，不是能量体成品。其固定步长和每步光照不能照搬到大量同时释放的技能。
- 许可：MIT；第三方资源另查。[依据](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/LICENSE)
- 已核查的源码入口：
  - [Assets/Scripts/Clouds/Shaders/Clouds.shader](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/Shaders/Clouds.shader#L165) — `float sampleDensity`，第 165 行。
  - [Assets/Scripts/Clouds/CloudMaster.cs](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/CloudMaster.cs#L68) — `OnRenderImage`，第 68 行。
  - [Assets/Scripts/Clouds/Noise/Compute/NoiseGenCompute.compute](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/Noise/Compute/NoiseGenCompute.compute#L1) — `Worley`，第 1 行。

### P1 · Tyler Dodds / VolumetricCloudsTutorial

- 原项目：[TylerDodds/VolumetricCloudsTutorial](https://github.com/TylerDodds/VolumetricCloudsTutorial)；分支 `master`；固定提交 `216f465b68c547068d08c029b9bd8788a5f0f1fd`。
- 参考价值：教程与源码一一对应；有逐帧版本和历史缓冲版本，便于研究体积质量与成本的关系。
- 学什么：低分辨率 raymarch、低差异采样偏移、平均体积深度、历史双缓冲、前帧投影和基于半衰期的混合。
- 迁移判断：中。可用于较稳定的能量场；移动、缩放、爆发和传送时需要历史拒绝与清空，不能直接照搬天空云假设。
- 边界：教程把云限制在远处；局部能量常在物体前后穿插。历史积累会拖影，透明排序和遮挡需要重新设计。
- 许可：MIT；引用的噪声资源另查。[依据](https://github.com/TylerDodds/VolumetricCloudsTutorial/blob/216f465b68c547068d08c029b9bd8788a5f0f1fd/LICENSE)
- 已核查的源码入口：
  - [Assets/VolumetricCloudsTutorial/ImageEffects/CloudsHistoryEffect.cs](https://github.com/TylerDodds/VolumetricCloudsTutorial/blob/216f465b68c547068d08c029b9bd8788a5f0f1fd/Assets/VolumetricCloudsTutorial/ImageEffects/CloudsHistoryEffect.cs#L33) — `PerformEffect`，第 33 行。
  - [Assets/VolumetricCloudsTutorial/Shaders/CloudsHistoryEffectShader.shader](https://github.com/TylerDodds/VolumetricCloudsTutorial/blob/216f465b68c547068d08c029b9bd8788a5f0f1fd/Assets/VolumetricCloudsTutorial/Shaders/CloudsHistoryEffectShader.shader#L1) — `Shader`，第 1 行。
  - [Assets/VolumetricCloudsTutorial/ImageEffects/LowDiscrepancySequence.cs](https://github.com/TylerDodds/VolumetricCloudsTutorial/blob/216f465b68c547068d08c029b9bd8788a5f0f1fd/Assets/VolumetricCloudsTutorial/ImageEffects/LowDiscrepancySequence.cs#L9) — `class`，第 9 行。

### P0 · Pavel Dobryakov / WebGL-Fluid-Simulation

- 原项目：[PavelDoGreat/WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation)；分支 `master`；固定提交 `a2d292931f19d9b3b9f564e23e6c32729d2121c3`。
- 参考价值：成熟 WebGL 示例；fragment shader、浮点 FBO 和模拟主循环都在 script.js 中，学习入口集中。
- 学什么：平流、旋度、涡量约束、散度、Jacobi 压力投影、减去压力梯度、密度输运，以及 ping-pong 纹理管理。
- 迁移判断：高（二维流动层），中（扩展到三维）。适合局部流动贴图、扭曲场、护盾表面实验；与真正三维体积主体分开评估。
- 边界：二维密度贴图叠在球上不会成为三维流体；默认大量注入和 Bloom 不能代替三维结构。
- 许可：MIT。[依据](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation/blob/a2d292931f19d9b3b9f564e23e6c32729d2121c3/LICENSE)
- 已核查的源码入口：
  - [script.js](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation/blob/a2d292931f19d9b3b9f564e23e6c32729d2121c3/script.js#L746) — `const advectionShader`，第 746 行。
  - [script.js](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation/blob/a2d292931f19d9b3b9f564e23e6c32729d2121c3/script.js#L835) — `const vorticityShader`，第 835 行。
  - [script.js](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation/blob/a2d292931f19d9b3b9f564e23e6c32729d2121c3/script.js#L1231) — `function step`，第 1231 行。

### P1 · Scrawk / GPU-GEMS-3D-Fluid-Simulation

- 原项目：[Scrawk/GPU-GEMS-3D-Fluid-Simulation](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation)；分支 `master`；固定提交 `6d676c61c85126f35246ebc90568f4957f0a2ce0`。
- 参考价值：完整三维烟和火模拟，计算核按物理步骤分文件；对应 GPU Gems 3 第 30 章，便于比较论文与实现。
- 学什么：三维平流与修正、浮力、涡量约束、压力投影、障碍物；FireFluidSim 驱动顺序；FireRayCast 的局部盒域采样。
- 迁移判断：中偏低（完整求解器），高（技术参考）。StructuredBuffer / compute 需改为 OpenGL SSBO / image 或纹理切片路径，并建立能力检测。
- 边界：每技能一个高分辨率三维网格代价高；旧火焰渲染器不是现代发射吸收积分的最终答案，也未提供 Minecraft 世界深度适配。
- 许可：MIT（仓库源码）；独立借入代码仍查原许可。[依据](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation/blob/6d676c61c85126f35246ebc90568f4957f0a2ce0/LICENSE)
- 已核查的源码入口：
  - [Assets/FluidSim3D/Scripts/FireFluidSim.cs](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation/blob/6d676c61c85126f35246ebc90568f4957f0a2ce0/Assets/FluidSim3D/Scripts/FireFluidSim.cs#L6) — `class FireFluidSim`，第 6 行。
  - [Assets/FluidSim3D/Shaders/ApplyAdvection.compute](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation/blob/6d676c61c85126f35246ebc90568f4957f0a2ce0/Assets/FluidSim3D/Shaders/ApplyAdvection.compute#L87) — `AdvectVelocity`，第 87 行。
  - [Assets/FluidSim3D/Shaders/ComputeConfinement.compute](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation/blob/6d676c61c85126f35246ebc90568f4957f0a2ce0/Assets/FluidSim3D/Shaders/ComputeConfinement.compute#L11) — `numthreads`，第 11 行。
  - [Assets/FluidSim3D/Shaders/FireRayCast.shader](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation/blob/6d676c61c85126f35246ebc90568f4957f0a2ce0/Assets/FluidSim3D/Shaders/FireRayCast.shader#L113) — `float4 frag`，第 113 行。

### P0 · Keijiro / KvantSwarm

- 原项目：[keijiro/KvantSwarm](https://github.com/keijiro/KvantSwarm)；分支 `master`；固定提交 `932bb30f70942143c591070be0d1d1e9504125a2`。
- 参考价值：完整三维流线渲染项目，用状态纹理维护头部、尾迹、速度和吸引子。
- 学什么：Kernel.shader 的解析梯度叉积无散场、吸引力、阻尼、历史尾点推进；Surface.shader 把历史点变成可见连续线。
- 迁移判断：高（思路），中（GPU 状态路径）。先用少量 CPU 轨迹＋网格条带验证，再考虑浮点纹理 ping-pong；主体仍由密度体积承担。
- 边界：默认是流线，不是烟火体积；无散场加吸引子后不再整体无散。细线太密会产生闪烁和带状伪影。
- 许可：MIT（README 内）；噪声保留原版权。[依据](https://github.com/keijiro/KvantSwarm/blob/932bb30f70942143c591070be0d1d1e9504125a2/README.md)
- 已核查的源码入口：
  - [Assets/Kvant/Swarm/Shaders/Kernel.shader](https://github.com/keijiro/KvantSwarm/blob/932bb30f70942143c591070be0d1d1e9504125a2/Assets/Kvant/Swarm/Shaders/Kernel.shader#L46) — `float3 dfnoise_field`，第 46 行。
  - [Assets/Kvant/Swarm/Shaders/Surface.shader](https://github.com/keijiro/KvantSwarm/blob/932bb30f70942143c591070be0d1d1e9504125a2/Assets/Kvant/Swarm/Shaders/Surface.shader#L9) — `Shader`，第 9 行。

### P1 · Keijiro / KvantWig

- 原项目：[keijiro/KvantWig](https://github.com/keijiro/KvantWig)；分支 `master`；固定提交 `a955fe2d7e53103472e4b5f07850f099b1a32d49`。
- 参考价值：完整非写实发丝系统，适合研究从核心或表面生长的细丝，而非自由飞行粒子。
- 学什么：Kernels 的根节点约束、长度约束、弹簧、阻尼与噪声力；Filament 的沿曲线局部基底和厚度。
- 迁移判断：中。用于异火外须、Boss 核心触须、护盾边缘丝带；移除不需要的毛发约束，保留稳定的根部挂点。
- 边界：毛发力学不等于等离子体；其材质和密度需重新设计，不能替代体积云雾。
- 许可：MIT；内含组件逐文件保留署名。[依据](https://github.com/keijiro/KvantWig/blob/a955fe2d7e53103472e4b5f07850f099b1a32d49/LICENSE.md)
- 已核查的源码入口：
  - [Assets/Kvant/Wig/Shader/Kernels.shader](https://github.com/keijiro/KvantWig/blob/a955fe2d7e53103472e4b5f07850f099b1a32d49/Assets/Kvant/Wig/Shader/Kernels.shader#L33) — `float3 DFNoiseField`，第 33 行。
  - [Assets/Kvant/Wig/Shader/Filament.cginc](https://github.com/keijiro/KvantWig/blob/a955fe2d7e53103472e4b5f07850f099b1a32d49/Assets/Kvant/Wig/Shader/Filament.cginc#L19) — `vert`，第 19 行。

### P1 · Keijiro / KinoBloom

- 原项目：[keijiro/KinoBloom](https://github.com/keijiro/KinoBloom)；分支 `master`；固定提交 `0a04c43fa47de12bb52edb325c8d3c0125d0b4ca`。
- 参考价值：小型、完整 Bloom 实现，阈值、soft knee、降采样与重建结构易读。
- 学什么：线性阈值与柔和过渡；多级模糊金字塔；扩散半径与强度独立控制。
- 迁移判断：高（算法）。从独立能量发光缓冲提取辉光，先保持主体细节，再合成 Bloom；FBO 格式与色彩空间另适配。
- 边界：亮度阈值是亮像素筛选，不是对象筛选；场景天空和白方块也可能被选中。选择性发光应看 Shimmer。
- 许可：MIT。[依据](https://github.com/keijiro/KinoBloom/blob/0a04c43fa47de12bb52edb325c8d3c0125d0b4ca/LICENSE.md)
- 已核查的源码入口：
  - [Assets/Kino/Bloom/Bloom.cs](https://github.com/keijiro/KinoBloom/blob/0a04c43fa47de12bb52edb325c8d3c0125d0b4ca/Assets/Kino/Bloom/Bloom.cs#L163) — `void OnRenderImage`，第 163 行。
  - [Assets/Kino/Bloom/Shader/Bloom.shader](https://github.com/keijiro/KinoBloom/blob/0a04c43fa47de12bb52edb325c8d3c0125d0b4ca/Assets/Kino/Bloom/Shader/Bloom.shader#L24) — `Shader`，第 24 行。

### P0 · Effekseer / 渲染运行库

- 原项目：[effekseer/Effekseer](https://github.com/effekseer/Effekseer)；分支 `master`；固定提交 `82b37081a302b9f9eff0bf14dc6c845fca8c3c54`。
- 参考价值：跨平台实时 VFX 编辑器和运行库，具有实际的环、带、模型和背景扭曲渲染路径。
- 学什么：RingRenderer 的内外半径、角度和颜色；RibbonRenderer 的连续带几何；GL distortion shader 的投影切线偏移、背景采样和软交界。
- 迁移判断：高（算法），低（整个 C++ 运行库直接集成）。在 Forge 自己实现 Java 几何与 GLSL，只研究调用顺序；JNI 集成是另一项工程。
- 边界：能量环不等于屏幕扭曲，也不等于真正空间透镜；已有原生渲染器不能直接作为 Forge Java 依赖。master 是开发分支，本研究只固定阅读，不建议直接用于生产发布。
- 许可：MIT（核心）；第三方库与示例资产分开核查。[依据](https://github.com/effekseer/Effekseer/blob/82b37081a302b9f9eff0bf14dc6c845fca8c3c54/LICENSE)
- 已核查的源码入口：
  - [Dev/Cpp/EffekseerRendererCommon/EffekseerRendererCommon/EffekseerRenderer.RingRendererBase.h](https://github.com/effekseer/Effekseer/blob/82b37081a302b9f9eff0bf14dc6c845fca8c3c54/Dev/Cpp/EffekseerRendererCommon/EffekseerRendererCommon/EffekseerRenderer.RingRendererBase.h#L359) — `const float outerRadius`，第 359 行。
  - [Dev/Cpp/EffekseerRendererCommon/EffekseerRendererCommon/EffekseerRenderer.RibbonRendererBase.h](https://github.com/effekseer/Effekseer/blob/82b37081a302b9f9eff0bf14dc6c845fca8c3c54/Dev/Cpp/EffekseerRendererCommon/EffekseerRendererCommon/EffekseerRenderer.RibbonRendererBase.h#L30) — `class`，第 30 行。
  - [Dev/Cpp/EffekseerRendererGL/EffekseerRendererGL/Shader_3/model_distortion_ps.fx](https://github.com/effekseer/Effekseer/blob/82b37081a302b9f9eff0bf14dc6c845fca8c3c54/Dev/Cpp/EffekseerRendererGL/EffekseerRendererGL/Shader_3/model_distortion_ps.fx#L57) — `vec4 _main`，第 57 行。

### P0 · FoundryMC / Veil · 1.20 分支

- 原项目：[FoundryMC/Veil](https://github.com/FoundryMC/Veil)；分支 `1.20`；固定提交 `3592dddfcfcf92b298ec933c0d9fb951a69aba51`。
- 参考价值：已核对 gradle.properties：Minecraft 1.20.1，Forge 47.3.1；具有完整 FBO、shader 和后处理管理源码。
- 学什么：AdvancedFbo 绑定、颜色/深度附件、释放；PostProcessingManager 的管线调度；ShaderProgram、Forge 渲染阶段接入。
- 迁移判断：高（工程参考）。优先研究世界颜色/深度如何交给局部体积、扭曲和发光通道；库接入需核对目标构建与依赖。
- 边界：这是框架，不能保证能量外观；复制库实现与通过公开 API 依赖使用是不同的许可情形。未在游戏中验证兼容性。
- 许可：LGPLv3。[依据](https://github.com/FoundryMC/Veil/blob/3592dddfcfcf92b298ec933c0d9fb951a69aba51/LICENSE)
- 已核查的源码入口：
  - [common/src/main/java/foundry/veil/api/client/render/framebuffer/AdvancedFbo.java](https://github.com/FoundryMC/Veil/blob/3592dddfcfcf92b298ec933c0d9fb951a69aba51/common/src/main/java/foundry/veil/api/client/render/framebuffer/AdvancedFbo.java#L36) — `interface AdvancedFbo`，第 36 行。
  - [common/src/main/java/foundry/veil/api/client/render/post/PostProcessingManager.java](https://github.com/FoundryMC/Veil/blob/3592dddfcfcf92b298ec933c0d9fb951a69aba51/common/src/main/java/foundry/veil/api/client/render/post/PostProcessingManager.java#L46) — `class PostProcessingManager`，第 46 行。
  - [forge/src/main/java/foundry/veil/forge/ForgeRenderTypeStageHandler.java](https://github.com/FoundryMC/Veil/blob/3592dddfcfcf92b298ec933c0d9fb951a69aba51/forge/src/main/java/foundry/veil/forge/ForgeRenderTypeStageHandler.java#L24) — `class`，第 24 行。
- 版本依据：[构建或 MOD 元数据](https://github.com/FoundryMC/Veil/blob/3592dddfcfcf92b298ec933c0d9fb951a69aba51/gradle.properties)。

### P0 · Low-Drag-MC / Shimmer · 1.20 分支

- 原项目：[Low-Drag-MC/Shimmer](https://github.com/Low-Drag-MC/Shimmer)；分支 `1.20`；固定提交 `49cb20e7406de63027f8666d6dde9b73d8444f80`。
- 参考价值：Minecraft 发光/彩色光工程；已核对 Forge 模块和声明的 Minecraft [1.20,1.21) 范围。
- 学什么：PostProcessing 对指定几何/粒子的输出组织；MRT、颜色/深度复制；bloom_unreal 多尺度模糊管线。
- 迁移判断：高（后处理参考）。先复用设计思想建立独立 emission 通道；与自定义体积渲染器的深度和 alpha 约定统一。
- 边界：Bloom 不会照亮地形；彩色动态光是另一路径。shader 注入与其他渲染 mod 组合需要实际验证。
- 许可：MIT。[依据](https://github.com/Low-Drag-MC/Shimmer/blob/49cb20e7406de63027f8666d6dde9b73d8444f80/LICENSE)
- 已核查的源码入口：
  - [Common/src/main/java/com/lowdragmc/shimmer/client/postprocessing/PostProcessing.java](https://github.com/Low-Drag-MC/Shimmer/blob/49cb20e7406de63027f8666d6dde9b73d8444f80/Common/src/main/java/com/lowdragmc/shimmer/client/postprocessing/PostProcessing.java#L56) — `public class PostProcessing`，第 56 行。
  - [Common/src/main/resources/assets/shimmer/shaders/post/bloom_unreal.json](https://github.com/Low-Drag-MC/Shimmer/blob/49cb20e7406de63027f8666d6dde9b73d8444f80/Common/src/main/resources/assets/shimmer/shaders/post/bloom_unreal.json#L76) — `"passes"`，第 76 行。
  - [Forge/src/main/resources/META-INF/mods.toml](https://github.com/Low-Drag-MC/Shimmer/blob/49cb20e7406de63027f8666d6dde9b73d8444f80/Forge/src/main/resources/META-INF/mods.toml#L24) — `modId="minecraft"`，第 24 行。
- 版本依据：[构建或 MOD 元数据](https://github.com/Low-Drag-MC/Shimmer/blob/49cb20e7406de63027f8666d6dde9b73d8444f80/Forge/src/main/resources/META-INF/mods.toml)。

### P1 · Low-Drag-MC / Photon · 1.20.1 分支

- 原项目：[Low-Drag-MC/Photon](https://github.com/Low-Drag-MC/Photon)；分支 `1.20.1`；固定提交 `507499fbe5a3fc72bb90993e059848cbb31f13ca`。
- 参考价值：Minecraft 真实特效编辑/运行系统，旧分支具有材料、发射器、条带、光束、曲线和 Bloom 接入。
- 学什么：CustomShaderMaterial 的 ShaderInstance 与 uniform 缓存；TrailParticle 的路径平滑与渲染；发射器配置与效果生命周期。
- 迁移判断：高（1.20.1 工程参考）。适合作为辅助丝带/火星/曲线系统的学习对象；自定义材质入口可用于进一步研究。
- 边界：源码许可必须固定到 1.20.1 提交；不要套用 1.21 README 的新功能或新许可。大量普通粒子无法承担本任务主体。
- 许可：GPLv3（该固定提交）；新分支条款不同。[依据](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/LICENSE)
- 已核查的源码入口：
  - [common/src/main/java/com/lowdragmc/photon/client/gameobject/emitter/data/material/CustomShaderMaterial.java](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/common/src/main/java/com/lowdragmc/photon/client/gameobject/emitter/data/material/CustomShaderMaterial.java#L45) — `class CustomShaderMaterial`，第 45 行。
  - [common/src/main/java/com/lowdragmc/photon/client/gameobject/particle/TrailParticle.java](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/common/src/main/java/com/lowdragmc/photon/client/gameobject/particle/TrailParticle.java#L221) — `generateSmoothPath`，第 221 行。
  - [common/src/main/java/com/lowdragmc/photon/client/postprocessing/BloomEffect.java](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/common/src/main/java/com/lowdragmc/photon/client/postprocessing/BloomEffect.java#L33) — `class BloomEffect`，第 33 行。
- 版本依据：[构建或 MOD 元数据](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/settings.gradle)。

### P1 · Lodestar / Lodestone · 1.20 分支

- 原项目：[LodestarMC/Lodestone](https://github.com/LodestarMC/Lodestone)；分支 `1.20`；固定提交 `1980158f99c4ed849753960354972b3c7b18dfcc`。
- 参考价值：Minecraft 渲染工具库；gradle.properties 明确 Minecraft 1.20.1 / Forge 47.1.3 / Java 17。
- 学什么：ExtendedShaderInstance、LodestoneRenderType、TrailPointBuilder 与 MultiInstancePostProcessor 的职责分离。
- 迁移判断：高（架构参考）。研究 uniform、RenderType 与多实例效果管理；直接复用前先解决许可声明不一致。
- 边界：distorted_texture.fsh 扭曲的是它自身的 Sampler0 纹理，不是已渲染世界背景。不能因此声称它已提供世界折射。
- 许可：冲突：LICENSE.txt 为 LGPLv3，modLicense 为 GPLv3。[依据](https://github.com/LodestarMC/Lodestone/blob/1980158f99c4ed849753960354972b3c7b18dfcc/LICENSE.txt)
- 已核查的源码入口：
  - [src/main/java/team/lodestar/lodestone/systems/postprocess/MultiInstancePostProcessor.java](https://github.com/LodestarMC/Lodestone/blob/1980158f99c4ed849753960354972b3c7b18dfcc/src/main/java/team/lodestar/lodestone/systems/postprocess/MultiInstancePostProcessor.java#L11) — `class`，第 11 行。
  - [src/main/java/team/lodestar/lodestone/systems/rendering/trail/TrailPointBuilder.java](https://github.com/LodestarMC/Lodestone/blob/1980158f99c4ed849753960354972b3c7b18dfcc/src/main/java/team/lodestar/lodestone/systems/rendering/trail/TrailPointBuilder.java#L11) — `class`，第 11 行。
  - [src/main/resources/assets/lodestone/shaders/core/screen/distorted_texture.fsh](https://github.com/LodestarMC/Lodestone/blob/1980158f99c4ed849753960354972b3c7b18dfcc/src/main/resources/assets/lodestone/shaders/core/screen/distorted_texture.fsh#L22) — `void main`，第 22 行。
  - [gradle.properties](https://github.com/LodestarMC/Lodestone/blob/1980158f99c4ed849753960354972b3c7b18dfcc/gradle.properties#L15) — `modLicense`，第 15 行。
- 版本依据：[构建或 MOD 元数据](https://github.com/LodestarMC/Lodestone/blob/1980158f99c4ed849753960354972b3c7b18dfcc/gradle.properties)。

### P2 · Keijiro / NoiseBall

- 原项目：[keijiro/NoiseBall](https://github.com/keijiro/NoiseBall)；分支 `master`；固定提交 `fbd56c01514231068f52ed906c9e9fdebc2ddd3f`。
- 参考价值：小型顶点变形示例，展示形状扰动与变形后法线重建的最小实现。
- 学什么：Surface.shader 采样变形后的邻近三点并叉积重建法线；Common.cginc 的多频噪声位移。
- 迁移判断：高（技术思想）。适合低成本外壳或 LOD；核心代码先保持仅阅读研究，不能根据作者其他项目推定 MIT。
- 边界：不透明表面没有内部体积与卷吸；位移网格需要足够细分，也不是参考视频目标的完整方案。
- 许可：核心未发现明确授权；内含噪声文件有独立 MIT。未找到整体授权，保持仅研究；不能推定允许复制。
- 已核查的源码入口：
  - [Assets/NoiseBall/Shader/Surface.shader](https://github.com/keijiro/NoiseBall/blob/fbd56c01514231068f52ed906c9e9fdebc2ddd3f/Assets/NoiseBall/Shader/Surface.shader#L24) — `void vert`，第 24 行。
  - [Assets/NoiseBall/Shader/Common.cginc](https://github.com/keijiro/NoiseBall/blob/fbd56c01514231068f52ed906c9e9fdebc2ddd3f/Assets/NoiseBall/Shader/Common.cginc#L8) — `displace`，第 8 行。

### P2 · haxiomic / GPU-Fluid-Experiments

- 原项目：[haxiomic/GPU-Fluid-Experiments](https://github.com/haxiomic/GPU-Fluid-Experiments)；分支 `master`；固定提交 `20210bd9e64884146a73e6cedc0c696ca50bd0fd`。
- 参考价值：Pavel 项目明确引用的上游二维流体实验；Haxe 控制代码与 GLSL 计算步骤分离。
- 学什么：GPUFluid.hx 的平流、压力投影与纹理双缓冲；独立 fluid/*.frag 便于比较同算法的另一个实现。
- 迁移判断：高（算法），中（工程）。用于交叉理解二维流体，Pavel 的 MIT 实现更适合直接移植原型。
- 边界：二维流体不是三维能量；复用、翻译或修改受 GPL 约束，不因 AI 转译语言而消失。
- 许可：GPLv3。[依据](https://github.com/haxiomic/GPU-Fluid-Experiments/blob/20210bd9e64884146a73e6cedc0c696ca50bd0fd/LICENSE.txt)
- 已核查的源码入口：
  - [Source/GPUFluid.hx](https://github.com/haxiomic/GPU-Fluid-Experiments/blob/20210bd9e64884146a73e6cedc0c696ca50bd0fd/Source/GPUFluid.hx#L120) — `public inline function advect`，第 120 行。

## 权威资料配对阅读

- [Curl-Noise for Procedural Fluid Flow · SIGGRAPH 2007](https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf)：势函数、旋度、边界与空间调制；代码许可由作者主页独立确认。
- [Tiling Simplex Noise and Flow Noise in Two and Three Dimensions · JCGT 2022](https://jcgt.org/published/0011/01/02/)：psrdnoise 的旋转梯度、解析导数与周期；GLSL 文件内有 MIT。
- [GPU Gems · Chapter 38 · Fast Fluid Dynamics Simulation on the GPU](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu)：二维稳定流体的方程与 GPU ping-pong 组织；公开阅读不代表任意样例复制授权。
- [GPU Gems 3 · Chapter 30 · Real-Time Simulation and Rendering of 3D Fluids](https://developer.nvidia.com/gpugems/gpugems3/part-v-physics-simulation/chapter-30-real-time-simulation-and-rendering-3d-fluids)：三维模拟、局部域、深度融合与渲染；与 Scrawk 实现配对阅读。
- [OpenGL 4.3 Core Specification · Khronos](https://registry.khronos.org/OpenGL/specs/gl/glspec43.core.pdf)：计算着色器、SSBO、同步与能力边界。Forge 1.20.1 的版本不自动保证玩家机器有该能力。
- [Forge 1.20.1 Documentation](https://docs.minecraftforge.net/en/1.20.1/)：固定目标版本。渲染框架源码仍需对照实际 MOD 的 build 配置。

## 建议的技术学习顺序


### 01 · 看清结构

用原视频的连续帧描述粗尺度翻滚、细丝和消散。阅读纯算法与密度积分，写出形状、流动、密度、发射四个独立字段。

阅读：[stegu / psrdnoise](https://github.com/stegu/psrdnoise)、[Bridson / Curl-Noise（SIGGRAPH 2007）](https://www.cs.ubc.ca/~rbridson/)、[Sebastian Lague / Clouds](https://github.com/SebLague/Clouds)

完成标准：得到逐层图和算法选择表；尚不需要 Minecraft Java。

### 02 · 原创体积原型

下一阶段制作 WebGL2 可绕视的局部能量场，关闭 Bloom 验证密度与流动，再加侵蚀和发射；先不引入完整流体。

阅读：[stegu / psrdnoise](https://github.com/stegu/psrdnoise)、[Sebastian Lague / Clouds](https://github.com/SebLague/Clouds)、[stegu / webgl-noise](https://github.com/stegu/webgl-noise)

完成标准：固定 seed/time 可回放；侧视、近距、明暗背景与几层同时关闭时均可检查。

### 03 · 丝、环与释放

加入少量三维连续丝、扩张薄环、独立自发光与明确的蓄力/爆发曲线；每层能单独显示。

阅读：[Keijiro / KvantSwarm](https://github.com/keijiro/KvantSwarm)、[Effekseer / 渲染运行库](https://github.com/effekseer/Effekseer)、[Keijiro / KinoBloom](https://github.com/keijiro/KinoBloom)

完成标准：每一新增层有可见作用，主体不靠火星数量或 Bloom 维持。

### 04 · Forge 1.20.1 工程验证

确认目标 MOD 构建后，再适配 shader、世界深度、颜色副本和生命周期；测试资源重载、透明遮挡和渲染状态。

阅读：[FoundryMC / Veil · 1.20 分支](https://github.com/FoundryMC/Veil)、[Low-Drag-MC / Shimmer · 1.20 分支](https://github.com/Low-Drag-MC/Shimmer)、[Low-Drag-MC / Photon · 1.20.1 分支](https://github.com/Low-Drag-MC/Photon)

完成标准：真实 Minecraft 测试记录、GPU 预算和已知兼容范围，而不是只有网页截图。

### 05 · 流体与质量分档

只对需要环境交互或长时间卷吸的技能研究二维/三维状态模拟；建立共享池和同屏预算，再评估历史积累。

阅读：[Pavel Dobryakov / WebGL-Fluid-Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation)、[Scrawk / GPU-GEMS-3D-Fluid-Simulation](https://github.com/Scrawk/GPU-GEMS-3D-Fluid-Simulation)、[Tyler Dodds / VolumetricCloudsTutorial](https://github.com/TylerDodds/VolumetricCloudsTutorial)

完成标准：普通技能有低成本程序化路径；高规格技能在明确预算内提供流体路径与降级。

## Forge 迁移的渲染路线（设计提议，尚未实现）

```text
客户端技能实例：world transform / seed / age / phase / 参数曲线
          ↓
局部形状域 + 多尺度流动场 → 密度 / 发射场
          ↓
带世界深度限制的局部体积采样 → 能量颜色 / 透射率
          +
少量三维能量丝 + 冲击环/薄球壳 + 辅助火星
          ↓
独立 emission 缓冲与局部 distortion 缓冲
          ↓
稳定的背景颜色副本 → 深度约束扭曲 → 发光模糊 → 场景合成
```

具体先后顺序由哪些几何应被折射、哪些能量应遮挡背景决定；上图不是可直接照搬的固定 pass 顺序。同一个 FBO 不能同时作为当前 pass 的采样输入和写入目标。半透明层与多个重叠体积还需要单独处理顺序或合成策略。

对于原型密度场，可以用 `rho(p,t)=shape(p,phase) * max(0, baseFlow(p,t)-erosion(p,t))` 表达职责分离。`shape` 决定球、环、柱、领域或自定义形状；`baseFlow` 表达大尺度连续结构；`erosion` 表达边缘和细节。它是设计示意，不是已验证 shader 配方。

体积积分的采样距离必须参与权重：`T *= exp(-sigma_t * rho * ds)`。对一个采样段内近似恒定的发射系数 `j`，可用 `C += T_before * j * (1-exp(-sigma_t*rho*ds))/(sigma_t*rho)`，在消光趋零时取极限 `T_before*j*ds`。定义 `j` 是否已经乘密度要保持一致。这样才能避免单纯增加步数就让能量整体变亮；最终算法仍需考虑 HDR、色调映射与混合约定。

如果使用 warp 后的坐标，不应把未经链式法则修正的梯度继续宣称为原空间的解析导数；如果把幅度空间函数直接乘在无散速度上，也不保证仍无散。这些数学边界比“看起来在动”更影响长期复用。

Minecraft 1.20.1 Forge 工程首先研究 `ShaderInstance`、`RenderType`、`RenderTarget/PostChain` 和世界渲染阶段。高级 compute/SSBO 路径要查询 OpenGL 能力，另设 fragment/FBO 或程序化退化路径；不能从游戏版本推出 OpenGL 4.3 可用。[Khronos OpenGL 4.3 规范](https://registry.khronos.org/OpenGL/specs/gl/glspec43.core.pdf)。

## 性能研究：预算可以估算，帧率不能预先承诺

体积成本近似与“覆盖像素 × 步数 × 每步密度评估成本”相关；复杂光照还要乘额外光线采样开销。举例：1920×1080、25% 覆盖、64 步约为 **3318 万次密度评估**；半宽半高对应约 **829 万次**。这些是算术估算，不是耗时或 FPS 测量。互相重叠的多个技能仍会叠加成本，提前终止只能减少实际执行的一部分。

三维状态网格的显存与边长三次方相关。例如仅计算 1 个 RGBA16F 速度场＋2 个 R16F 标量场的双缓冲，约 24 字节/体素：64³ 约 **6 MiB**，128³ 约 **48 MiB**。压力、旋度、障碍、临时修正、纹理对齐和渲染目标未计入；这不是 Scrawk 或最终 MOD 的实测分配。

| 质量路径 | 适合用途 | 先控制的成本 | 验证方式 |
|---|---|---|---|
| 程序化局部体积 | 蓄力球、异火核心、灵气聚集 | 屏幕覆盖、步数、噪声层数 | 关 Bloom；侧视、相机入体积、遮挡、同屏实例 |
| 表面/薄壳＋少量丝 | 护盾、法阵、远距离 LOD | 网格细分、透明 overdraw | 多角度、轮廓撕裂、近距切换 |
| 二维状态输运 | 护盾表面、局部流动/扭曲贴图 | 格网尺寸、Jacobi 迭代、FBO 格式 | 单独显示速度、散度与压力，不冒称三维 |
| 三维局部流体 | 大型持续 Boss 技能、环境卷吸 | 网格体素、迭代、共享池数量 | GPU 时间、显存与交互，低规格降级 |
| 历史重投影体积 | 稳定领域或缓慢场 | history 清空、重投影拒绝 | 传送、爆发、移动/缩放、遮挡突变和拖影 |

普通技能避免一人一个三维流体求解器。优先裁剪域、降低采样分辨率、限制噪声层数、跳过空密度和共享暂存资源；LOD 允许表现方式改变，只要关键轮廓与动作保持连续。

## 许可证与直接复用边界

| 情形 | 本研究中的项目 | 直接复用判断 |
|---|---|---|
| MIT | psrdnoise GLSL、webgl-noise、Pavel、Scrawk、Clouds、Tyler、KvantSwarm/Wig、KinoBloom、Shimmer、Effekseer 核心 | 保留版权和许可声明；项目内第三方代码、纹理与模型各自核查 |
| public domain 作者声明 | Bridson 示例归档 | 作者主页明确说明；图形依赖、论文文本和其他素材不自动具有相同授权 |
| LGPLv3 | Veil 1.20 | 通过 API 使用库与复制/改造库源码分开判断；分发时满足库修改源码、声明及相关组合/替换要求，不能把它当 MIT |
| GPLv3 | Photon 1.20.1、GPU-Fluid-Experiments | 复制、改编、翻译并分发衍生作品须按对应 GPL 条件处理；单独阅读算法不意味着必须复制实现 |
| 许可冲突 | Lodestone 1.20 | 根 LICENSE.txt 为 LGPLv3，但 gradle.properties 的 modLicense=GPLv3，mods.toml 使用该值；直接复用前向作者确认 |
| 整体授权未明确 | NoiseBall 核心 | 仅阅读思路；不能根据其他 Keijiro 项目推定整体 MIT。噪声子文件的许可不覆盖全部网格代码 |
| 自定义 / 非商业条款 | Photon 当前 1.21 README/LICENSE | 与本研究固定的 1.20.1 提交区别处理；未据此确认 1.21 新功能能回移植 |
| 商业视频素材 | Triune Infinity | 原视频仅用于本地分析。未获得素材分发授权；本次未把视频切成 MOD 贴图或打入 skill |

许可证文字以每条档案链接的固定版本为依据；上述是工程选材摘要，不是对最终 MOD 分发合规的法律结论。MIT 有条件，GPL 也不等于禁止商业使用。用 AI 改写、转译或重命名不会自动解除衍生源码的条件。公开源码不自动表示已授权模型训练；作为本地技术阅读资料与分发/训练是不同问题。[GPLv3 原文](https://www.gnu.org/licenses/gpl-3.0.html)、[LGPLv3 原文](https://www.gnu.org/licenses/lgpl-3.0.html)、[Photon 1.20.1 固定 LICENSE](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/LICENSE)。

## 哪些方向暂未作为主线

- ShaderToy 和个人代码片段：可能值得补充，但许可证逐作品不同；展示相似不意味着完整、可复用或可迁移。本轮优先完整工程与论文配套代码。
- Unreal Niagara、Unity VFX Graph 成品展示：可以学习编排与工具思想，不能把没有实际打开的 graph 或引擎代码宣称为已读源码。Unreal 源码还有独立 EULA，本轮不以下载并直接复用作为主线。
- 稠密 SPH、离线流体、神经辐射场：可以做后续专题，但它们不是本任务最短的 Minecraft 稳定实时路线。
- 只堆 Bloom、Fresnel 外壳、大量 billboard 或整张序列帧：可作为部分辅助或 LOD，不能替代内部流动与三维层次。

