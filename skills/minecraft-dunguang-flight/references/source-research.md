# 人物飞行遁光：网络学习资源与 Forge 1.20.1 迁移研究

核查日期：2026-10-15。目标：Minecraft Forge 1.20.1 / Java 17。以下共 14 组主资源（11 组基础资料与文末 3 组多形态补读），均已打开原始来源。固定提交仅用于复查，不代表最新发行版。当前完成静态资料研究，未运行 Unity、C++ 或 Minecraft，不宣称已达到游戏画面或性能目标。

## 最短学习路线

先做好 **人物挂点 + 世界空间历史尾迹 + 连续截面 + 头尾曲线 + 程序侵蚀**，再加少量离体丝、软交界和指定对象 Bloom。只有当参考确实需要可绕视的厚雾内部层次，才做有界体积；只有需要持续输运或交互，才引入状态模拟。该建议是工程判断，不是对参考视频专有实现的确认。

| 顺序 | 要解决的问题 | 首选资源 | 阶段验收 |
|---|---|---|---|
| 1 | 飞过的位置留下连续路径 | Unity Trail + three.js Curve | 相同路径在不同帧率下的点距、纹理比例、寿命一致 |
| 2 | 急转时不翻面、从侧面不消失 | three.js 连续截面 + Effekseer ribbon | 直线、S 弯、螺旋、倒飞、相机绕行 |
| 3 | 气罩和尾迹有细节撕裂与卷动 | psrdnoise + Bridson | 关闭 Bloom，仍能追踪连续结构 |
| 4 | 方块遮挡、近相机与多透明层 | GPU Gems 23 + Effekseer | 墙角、树叶、进入气罩、明暗背景 |
| 5 | 亮核向外柔和扩散 | Shimmer | 只让遁光扩散，保留人物和细丝轮廓 |
| 6 | 特殊厚雾与持续状态 | Clouds / Pavel（可选） | 有界积分、相机入域、移动域重置和预算 |

P0 是原型必读，P1 是深入与工程对照，P2 是条件满足后才研究。工程接入可从原版渲染对象开始；Photon 和 Veil 是参考路线，不默认成为项目依赖。

## 逐项档案

### P0 · Trail Renderer component reference

作者：Unity Technologies。[官方文档](https://docs.unity3d.com/6000.0/Documentation/Manual/class-TrailRenderer.html)

实际阅读入口：Trail settings: Time / Min Vertex Distance / Alignment / Texture Mode / Width / Color。

**学什么：** 尾迹点按世界距离生成、按年龄消失；长度平铺 UV 与整条 Stretch 的区别；相机朝向与物体朝向条带的区别。

**Forge 迁移：** 把这些职责做成 Java 历史缓冲、距离采样器和 Shader 参数；不需要 Unity 运行库。高速移动每 tick 可以插入多个定距点，平滑曲线不能恢复未知的真实转弯轨迹。

**下一步实验：** 同一路径用 30/60/144 fps 回放，检查点距、纹理比例和消失时间一致；停车后旧尾迹自然消散。

**许可与复制：** 文档仅作阅读与链接；未查到对该页面全部文字和截图的通用复制许可。 不打包文档或截图；以原创实现表达相同功能。

**核查状态：** 已打开官方页面并阅读参数定义；未检查 Unity 引擎实现。

### P0 · three.js Curve.js：弧长映射与连续截面框架

作者：three.js authors。[作者仓库源码](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/src/extras/core/Curve.js)

固定提交：`a58e9ecf225b50e4a28a934442e854878bc2a959`。

实际阅读入口：src/extras/core/Curve.js: getSpacedPoints / getLengths / getUtoTmapping / computeFrenetFrames；LICENSE。

**学什么：** 通过累计弧长反查均匀位置；截面通过相邻切线之间的轴角旋转传播。函数名含 Frenet，但代码使用连续传递法向，避免直接依赖二阶导数构造严格 Frenet 框架。

**Forge 迁移：** 用 JOML 向量与四元数实现连续截面，供世界空间丝带或少边数细管使用。必须额外处理重复点、极短段和近 180 度反向；原源码不应直接当作完整异常处理方案。

**下一步实验：** 用直线、S 形、螺旋、急转和反向折返点测试法向连续性；相机绕行观察是否出现翻面。

**许可与复制：** MIT；Copyright 2010–2025 three.js authors。 允许按 MIT 复用；保留版权和完整许可声明。原创概念实现也应注明学习来源。 [许可依据](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/LICENSE)。

**核查状态：** 已读取固定提交源码和 LICENSE；未运行 three.js。

### P0 · Curl-Noise for Procedural Fluid Flow

作者：Robert Bridson / Jim Hourihan / Marcus Nordenstam。[原论文＋作者示例](https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf)

实际阅读入口：论文 §2：势函数旋度、空间调制与边界；curlnoise.tar.gz（23032 bytes，内存读取，未保存）：curlnoise.h / example_3dplume.cpp / main3.cpp；作者主页 Downloads 的 public domain 声明。

**学什么：** 速度由向量势的旋度得到；若需要空间幅度遮罩，先调制势函数再取旋度。示例使用中心差分求旋度，再积分粒子路径。

**Forge 迁移：** 用于少量离体丝与尾端碎光的扰动，在人物轨迹切线周围叠加有方向的主流。解析噪声导数可减少差分采样成本。

**下一步实验：** 固定 seed，在只有主流、主流＋curl 两种模式中跟踪同一批粒子；避免用每帧随机位移伪装流动。

**许可与复制：** 作者主页明确声明示例 public domain；论文文本和第三方依赖不自动采用同一许可。 作者示例可按 public domain 声明使用；依赖逐项核对；论文只引用链接。 [许可依据](https://www.cs.ubc.ca/~rbridson/)。

**核查状态：** 已读论文、作者声明及归档内 3 个关键文件；未编译示例。

- curl 无散场不会自行压缩、聚集粒子；添加吸引场后不再宣称最终场全局无散。
- main3.cpp 自己注明未在半步更新时间：它对时间变化场并非完整二阶 RK2；新实现应在 t+dt/2 求中点速度。

### P0 · Tiling Simplex Noise and Flow Noise / psrdnoise3.glsl

作者：Stefan Gustavson / Ian McEwan。[论文配套作者源码](https://jcgt.org/published/0011/01/02/)

固定提交：`419175a270862ce7ae692038fafafb42ec0427e9`。

实际阅读入口：src/psrdnoise3.glsl: float psrdnoise / rotating gradients / analytic gradient / 文件内 MIT。

**学什么：** alpha 控制格点梯度旋转，返回噪声及解析空间导数；用于有连续卷动外观的侵蚀或多尺度域扭曲。

**Forge 迁移：** 纯 GLSL 可迁移至人物周围壳层、短锥形气罩和尾迹材质。先控制层数与覆盖率；不把每个片元的多层解析噪声无条件放进几十次体积步进。

**下一步实验：** 对比静态噪声平移与旋转梯度；关闭 Bloom 后检查破口和细丝仍具有连贯运动。

**许可与复制：** GLSL 文件内 MIT，Copyright 2021 Gustavson / McEwan；论文与代码许可分开。 允许按 MIT 复用该 GLSL，保留文件头版权和许可。 [许可依据](https://github.com/stegu/psrdnoise/blob/419175a270862ce7ae692038fafafb42ec0427e9/src/psrdnoise3.glsl)。

**核查状态：** 已打开论文入口并阅读全文源码；论文入口正文未被抓取，不宣称通读论文 PDF。

- 旋转梯度的 flow 外观不等于物质平流或 Navier–Stokes 求解。
- 对 warp 后坐标返回的梯度，若当作原坐标解析导数使用，需要链式法则。

### P1 · Effekseer：RibbonRenderer 与 GL 背景扭曲/软交界

作者：Effekseer Project。[作者仓库源码](https://github.com/effekseer/Effekseer/tree/82b37081a302b9f9eff0bf14dc6c845fca8c3c54)

固定提交：`82b37081a302b9f9eff0bf14dc6c845fca8c3c54`。

实际阅读入口：Dev/Cpp/EffekseerRendererCommon/EffekseerRendererCommon/EffekseerRenderer.RibbonRendererBase.h: RenderSplines / ViewpointDependent / spline_left / spline_right；Dev/Cpp/EffekseerRendererGL/EffekseerRendererGL/Shader_3/model_distortion_ps.fx: SoftParticle / _main；LICENSE。

**学什么：** 左右边界分别组织连续条带；朝向模式与几何不同；背景扭曲显式读取 backTex；软交界先重建深度再比较，另有近相机渐隐。

**Forge 迁移：** 学习几何与 pass 分工；在 Java 中重新组织顶点。若需要热浪扭曲，必须准备稳定世界颜色副本和深度；纹理 UV 扭曲不能代替背景扭曲。

**下一步实验：** 给背景放棋盘方块，开关扭曲观察背景实际位移；用前景墙检验遮挡，进入气罩检验近相机渐隐。

**许可与复制：** 核心 MIT；第三方与示例资产独立核查。 核心代码按 MIT 保留声明；不自动下载或打包样例纹理、模型。 [许可依据](https://github.com/effekseer/Effekseer/blob/82b37081a302b9f9eff0bf14dc6c845fca8c3c54/LICENSE)。

**核查状态：** 已读固定提交条带和 GL shader 关键段及 LICENSE；未构建 C++ 运行库。

### P0 · GPU Gems 3 Chapter 23: High-Speed, Off-Screen Particles

作者：Iain Cantlay / NVIDIA。[作者技术章节](https://developer.nvidia.com/gpugems/gpugems3/part-iv-image-effects/chapter-23-high-speed-screen-particles)

实际阅读入口：§23.2 离屏渲染；§23.3 深度降采样；§23.4 soft particles；§23.5 alpha blending；§23.6 混合分辨率。

**学什么：** 软粒子基于片元与场景的深度差渐隐；低分辨率能减少大面积柔雾过绘，但深度降采样会产生光边或侵入；细丝应单独考虑分辨率。

**Forge 迁移：** 对雾层可采用低分辨率颜色目标与深度约束上采样；主条带保持足够清晰。两个深度必须同一空间、同一距离单位。

**下一步实验：** 全分辨率与半分辨率对比靠墙、树叶、远处山体和近相机边界，统计覆盖率与 GPU 时间。

**许可与复制：** 网页可公开阅读；本次未核实所链接 SDK/CD 示例的独立复制授权。 引用概念与链接，原创实现；不将在线章节可读等同于可整段复制代码或图。

**核查状态：** 已读官方章节；未下载 SDK。

- 网页 Listing 23-1 与 23-2 的深度差符号存在直观不一致，不直接照抄。对向前为正的线性距离定义，应使用 clamp((sceneDistance-fragmentDistance)/fadeDistance,0,1)，并用遮挡测试验证。
- 2007 年 GPU 实测不能外推到当前 Minecraft 性能。

### P1 · Sebastian Lague / Clouds：有界密度、细节侵蚀与深度裁剪

作者：Sebastian Lague。[作者仓库源码](https://github.com/SebLague/Clouds/tree/fcc997c40d36c7bedf95a294cd2136b8c5127009)

固定提交：`fcc997c40d36c7bedf95a294cd2136b8c5127009`。

实际阅读入口：Assets/Scripts/Clouds/Shaders/Clouds.shader: rayBoxDst / sampleDensity / lightmarch / frag；LICENSE。

**学什么：** 大形状和小尺度侵蚀分离；射线与局部域相交，再以场景深度限制积分区间；透射率使用与步长相关的指数衰减。

**Forge 迁移：** 只在需要厚雾/内部层次时做局部体积；把云高度分布换成人物气罩或尾端形状域。高速遁光不必默认使用完整云渲染或每人一套三维流体。

**下一步实验：** 相机从罩外进入罩内，前景墙遮挡一半；切换采样步数后平均亮度保持近似稳定。

**许可与复制：** MIT，Copyright 2019 Sebastian Lague；第三方资源另查。 可按 MIT 学习和移植代码，保留版权及许可；本次不复制资源。 [许可依据](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/LICENSE)。

**核查状态：** 已读固定提交核心 Shader 和 LICENSE；未运行 Unity。

### P0 · Shimmer 1.20：指定效果 Bloom 与后处理调度

作者：Low-Drag-MC / KilaBash / zomb_676。[Minecraft 对应旧分支源码](https://github.com/Low-Drag-MC/Shimmer/tree/49cb20e7406de63027f8666d6dde9b73d8444f80)

固定提交：`49cb20e7406de63027f8666d6dde9b73d8444f80`。

实际阅读入口：Common/src/main/java/com/lowdragmc/shimmer/client/postprocessing/PostProcessing.java；Common/src/main/resources/assets/shimmer/shaders/post/bloom_unreal.json；Forge/src/main/resources/META-INF/mods.toml；LICENSE。

**学什么：** 指定几何/粒子的输入通道与世界颜色/深度复制；1/2、1/4、1/8、1/16 目标和横纵可分离模糊；发光主体与外扩 Bloom 分工。

**Forge 迁移：** 最贴近目标的合成参考。用独立 emission 缓冲控制哪些遁光区域扩散；保留 alpha、深度与资源重载约定，不让整个天空随阈值一起泛白。

**下一步实验：** 同时放高亮白色方块和蓝色遁光，只让遁光 Bloom；关闭 Bloom 后人物轮廓、主尾迹仍应可读。

**许可与复制：** MIT；根 LICENSE 与 Forge mods.toml 均声明 MIT。 允许按 MIT 复用并保留声明；渲染 mod 组合要另做实机测试。 [许可依据](https://github.com/Low-Drag-MC/Shimmer/blob/49cb20e7406de63027f8666d6dde9b73d8444f80/LICENSE)。

**核查状态：** 已读固定提交 Java、后处理 JSON、MIT 与 Minecraft [1.20,1.21) 元数据；未运行 Minecraft。

- Bloom 不是地形动态照明。
- 元数据支持范围不等于已验证与所有光影、透明模式兼容。

### P1 · Photon 1.20.1：TrailParticle 的历史、年龄与材质接口

作者：Low-Drag-MC。[Minecraft 对应旧分支源码](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/common/src/main/java/com/lowdragmc/photon/client/gameobject/particle/TrailParticle.java)

固定提交：`507499fbe5a3fc72bb90993e059848cbb31f13ca`。

实际阅读入口：TrailParticle.java: updateChanges / updateTails / generateSmoothPath / generateSmoothPathFromShader；gradle.properties；LICENSE。

**学什么：** 头部 supplier、原始历史与平滑历史分离；每 tick 消耗旧点寿命，按距离加点；compute/SSBO 路径先查询能力。

**Forge 迁移：** 适合学习 Forge 1.20.1 实际特效对象生命周期与可选 Photon 工作流；不直接复制其细分计数公式作为性能最优方案。

**下一步实验：** 跟踪实体急转、暂停、移除后尾迹残留、世界切换与头部插值；计算最大历史点数及每次细分上限。

**许可与复制：** 该固定提交 GPLv3；根 LICENSE 与 gradle.properties 的 mod_license 相符。 可阅读算法；复制、改编或转译并分发时须履行 GPLv3 条件。不要套用其他新分支条款。 [许可依据](https://github.com/Low-Drag-MC/Photon/blob/507499fbe5a3fc72bb90993e059848cbb31f13ca/LICENSE)。

**核查状态：** 已读固定提交 TrailParticle、GPL 与元数据；未构建运行。

- 该版本 generateSmoothPath 使用 distSq/minDistanceSq 决定 steps，距离增长会二次增加细分；本任务应使用长度、最大段数与总容量预算。
- 仅按最小距离加原始点并不等于定距离重采样；应明确二者区别。

### P1 · Veil 1.20：Forge 渲染阶段与 FBO 架构对照

作者：FoundryMC / Veil contributors。[Minecraft 对应旧分支源码](https://github.com/FoundryMC/Veil/tree/3592dddfcfcf92b298ec933c0d9fb951a69aba51)

固定提交：`3592dddfcfcf92b298ec933c0d9fb951a69aba51`。

实际阅读入口：forge/src/main/java/foundry/veil/forge/ForgeRenderTypeStageHandler.java；gradle.properties；forge/src/main/resources/META-INF/mods.toml；LICENSE。

**学什么：** 按 RenderLevelStageEvent.Stage 注册和 endBatch 指定 RenderType；区块层使用相机位置；工程声明 Minecraft 1.20.1 / Forge 47.3.1。

**Forge 迁移：** 研究每帧在哪一阶段提交尾迹、如何恢复渲染状态和安排后处理；可先用原版 ShaderInstance / RenderType / RenderTarget 做独立原型。

**下一步实验：** 记录提交阶段与透明层顺序；检查 resize、F3+T、世界切换及异常中止后 GL 状态恢复。

**许可与复制：** 声明不一致：根 LICENSE 为 LGPLv3；同提交 gradle.properties 的 license=All Rights Reserved，mods.toml 通过变量引用它。 本次仅架构阅读。直接复制或作为分发依赖前核查实际版本与作者许可说明，不能只写成无条件 LGPLv3。 [许可依据](https://github.com/FoundryMC/Veil/blob/3592dddfcfcf92b298ec933c0d9fb951a69aba51/LICENSE)。

**核查状态：** 已读固定提交阶段处理、构建属性、mods.toml 与 LICENSE；许可冲突保持可见；未运行。

### P2 · WebGL Fluid Simulation：平流与 ping-pong 状态

作者：Pavel Dobryakov。[作者仓库源码](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation/blob/a2d292931f19d9b3b9f564e23e6c32729d2121c3/script.js)

固定提交：`a2d292931f19d9b3b9f564e23e6c32729d2121c3`。

实际阅读入口：script.js: advectionShader / vorticityShader / pressureShader / gradientSubtractShader / step；LICENSE。

**学什么：** 回溯采样输运密度，涡量增强、压力投影与速度/染料双缓冲；这才是有历史状态的平流，与平移噪声不同。

**Forge 迁移：** 作为可选局部材质流动贴图；二维结果贴在人物罩上仍是二维场，不称为三维烟雾模拟。普通遁光优先程序场和少量几何。

**下一步实验：** 先在 2D 小网格关闭 Bloom，只显示速度、散度和染料；检查移动域重置与注入/消散。

**许可与复制：** MIT，Copyright 2017 Pavel Dobryakov。 允许按 MIT 复用，保留声明；所用素材或依赖独立核对。 [许可依据](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation/blob/a2d292931f19d9b3b9f564e23e6c32729d2121c3/LICENSE)。

**核查状态：** 已读固定提交核心 shader、step 顺序与 LICENSE；未运行。

具体原创算法见 [算法与自由设计](design-and-algorithms.md)。

## 额外阅读与未完成项

- [Computation of rotation minimizing frames](https://hub.hku.hk/handle/10722/152386)：已读 HKU 作者机构摘要；搜索找到作者 PDF，但直接打开返回 404，未将全文核查列为完成。
- [Forge 1.20.1 Events](https://docs.minecraftforge.net/en/1.20.1/concepts/events/)：已打开官方版本文档，用于客户端事件总线与注册概念；不作为渲染结果实测。

## 核查结果中特别需要保留的边界

- Veil 该提交根许可与打包元数据不一致；本研究的许可摘要不能抹去这一点。
- Photon 1.20.1 该提交为 GPLv3；AI 转译、改名或换语言不自动解除复制代码的许可条件。
- Shimmer 元数据声明包含 1.20.1，但还没有本任务的游戏兼容、视觉效果或 GPU 测量。
- RMF 作者论文全文直接打开失败，只读到作者机构摘要；不能标记为全文已读。
- GitHub API 在查询最新分支提交时触发共享 IP 限流，因此保留可成功读取的固定提交，不声称核查到最新版本。
- 本研究没有把外部视频、商业贴图、模型或完整源码工程打包进 Skill。


## 多形态补读

补读日期：2026-10-15。下列映射是针对已有视频分析条目的技术候选，不证明原作者使用了这些工具。**蓝金环壳可以是飞行主体构型；旋涡、水化和重组成身可以承担启动、切换或退出相位**，都纳入遁光生命周期设计。

### P0 · Effekseer 官方：程序模型、流动 UV 与 Alpha Cutoff

作者：Effekseer Project。[主学习入口](https://effekseer.github.io/Helps/18x/Tool/en/ToolReference/proceduralModel.html)

已读章节：Procedural Model: Type / Shape Type / Noise / Vertex color / UV；The Introduction of Material: Shift an area / Distort the image / Move the distortion；Material Node (Model): VertexPosition / MovingUV / ObjectScale；Basic Render Settings (Advanced): Alpha Texture / UV Distortion Texture / Alpha Cutoff / Falloff。

相关原始页面：[入口 1](https://effekseer.github.io/Help_Tool/en/ToolTutorial/material_01.html)、[入口 2](https://effekseer.github.io/Help_Tool/en/MaterialToolReference/material_NodeModel.html)、[入口 3](https://effekseer.github.io/Helps/18x/Tool/en/ToolReference/rendererCommonAd.html)。

**学什么：** 用旋转截面、锥/柱/样条形状和缠绕 ribbon 设计主体，再分开控制顶点色、UV 扭曲和透明裁切；参数化形状不限于球壳。

**Forge 迁移：** 原创 Java 网格与 GLSL 可覆盖长虹、蓝紫旋涡、蓝金环壳、金团和螺旋落地。网格几何运动与纹理流动分别更新时间；程序模型页面的静态 Noise 参数本身不证明逐帧顶点动画。

**造型候选：** 橙金/绿色长虹：锥/样条轮廓＋纵向流动；蓝紫旋涡：缠绕 ribbon＋切换期旋转；蓝金环壳：飞行主体的环面/截面环，不预设为护盾；金团：受控旋转截面＋外缘侵蚀；青绿细线螺旋落地：螺旋路径＋离散尾迹生命周期。

**下一步实验：** 同一材质换锥体、样条旋转面、环带和碎片网格；逐项开关顶点形变、UV 流动和侵蚀，检查各自作用。

**许可/素材：** 本次只阅读官网教程；未将核心 MIT 自动套用于教程文字、图片或下载范例。 只链接学习入口，原创几何与贴图；不下载或打包教程素材。

**核查状态：** 已打开并阅读 4 个官方页面的明确章节；未使用 Effekseer 编辑器、未运行范例。

### P1 · SideFX 官方：Pyro workflow 与 Omar Zarifi 烟火造型课程

作者：SideFX / Omar Zarifi（SideFX 软件开发者）。[主学习入口](https://www.sidefx.com/docs/houdini/pyro/lookdev.html)

已读章节：Pyro workflow: Visualization / Source geometry / Flames parameters / Shape operators / Tips and troubleshooting；H18 Masterclass 网页章节索引：01:07:45 Fire sim from scratch / 01:34:26 Shaping / 01:46:04 Smoke trail。

相关原始页面：[入口 1](https://www.sidefx.com/tutorials/pyro-omar-zarifi-h18-masterclass/)。

**学什么：** 先定源几何和整体运动，再调火焰寿命、消散及大小尺度扰动；烟密度与火焰发射分别可视化。

**Forge 迁移：** 用于红黑烟遁、赤焰与停飞余烟的造型拆解。研究思路可指导低成本程序场或自制 Flipbook，不意味着将 Houdini 求解器移植到 Forge。

**造型候选：** 红黑烟遁：低发射的有色烟密度＋少量红亮核；赤焰：方向性火舌＋独立火焰寿命；飞行退出：停止注入后保留尾烟消散，不硬切所有图层。

**下一步实验：** 固定运动路径，以同一源轮廓分别做黑烟主密度与赤焰主发射，观察启动、巡航、停飞残留；先不加 Bloom。

**许可/素材：** SideFX 页面页脚 All Rights Reserved；Houdini 软件和场景示例有各自条款，本次未下载。 阅读并链接，不搬运课程视频、场景、贴图；自行制作素材时另核对所用 Houdini 授权。

**核查状态：** 已阅读官方工作流正文及课程网页时间索引；没有观看嵌入课程视频，不宣称已完成视频章节。

### P1 · Unity VFX Graph 14：Flipbook Player 与 Output Particle Mesh

作者：Unity Technologies。[主学习入口](https://docs.unity3d.com/Packages/com.unity.visualeffectgraph@14.0/manual/Block-FlipbookPlayer.html)

已读章节：Flipbook Player: Tex Index / Constant vs Curve frame rate / UV Mode；Output Particle Mesh: Mesh Count / meshIndex / LOD / Limitations / Performance considerations。

相关原始页面：[入口 1](https://docs.unity3d.com/Packages/com.unity.visualeffectgraph@14.0/manual/Context-OutputParticleMesh.html)。

**学什么：** Flipbook 通过年龄相关帧索引推进，可做帧间混合；网格粒子为碎片、短剑等提供真实形状，并需处理多网格的提交与排序。

**Forge 迁移：** 将自制序列用于短暂水化、烟火爆散或转换层，主飞行路径仍由真实历史尾迹负责。金色短剑/碎片可用小网格实例按相位聚拢。该重组编排是本任务推断，不是网页已提供的成品效果。

**造型候选：** 水化/重组成身：启动和退出相位的局部转换层候选；金色短剑/碎片组装：独立网格粒子按相位汇聚；蓝紫旋涡转换：旋转网格/流丝与短时序列可交叉淡化；蓝白分叉流丝及极细金线：优先真实曲线几何，Flipbook 只用于局部辅助。

**下一步实验：** 做原创 4×4 小序列检验帧顺序、播放速率与预乘 alpha；另用 12 个低面数碎片试验聚拢/分散并保持确定 seed。

**许可/素材：** 官方文档按其网站条款阅读；未获取统一的截图、样例纹理或整个 VFX Graph 包复制授权。 只引用概念与文档链接；不从参考视频截帧拼游戏序列，不打包 Unity 示例资产。

**核查状态：** 已打开并阅读 VFX Graph 14.0.12 的两份官方文档；未运行 Unity、未下载材质或图集。

### 组合时保留形态区别

| 视频分析中的形态 | 候选几何/材质结构 | 生命周期中应保留的内容 |
|---|---|---|
| 橙金、绿色长虹与极细金线 | 宽窄跨度很大的真实历史曲线，前段体量与细尾分开 | 启动拉长、巡航、急转、停飞残留 |
| 蓝紫旋涡、水化和重组成身 | 环绕路径、局部转换材质、短序列或粒子重组 | 转换的触发和角色可见性，不把所有过渡剔除 |
| 红黑烟遁与赤焰 | 低发射烟密度和高发射火舌采用不同主层 | 停止注入后继续消散，发射与密度错峰 |
| 蓝白分叉流丝、青绿螺旋落地 | 多条不同宽度的三维曲线，明确主枝与辅枝 | 飞行和落地两阶段的空间走向连续 |
| 蓝金环壳、金团冲刺 | 环/旋转截面与核心体量，加独立流动侵蚀 | 环壳随飞行变形、冲刺伸缩，不自动命名为护盾 |
| 金色短剑/碎片组装 | 真实低面数网格实例、相位目标与汇聚路径 | 聚拢、锁定、散开、实体恢复的时序 |

本轮只增加学习来源与工程候选，没有下载商用素材，没有观看尚未采样的网络课程视频，也没有扩展成新的 Forge 实测结论。
