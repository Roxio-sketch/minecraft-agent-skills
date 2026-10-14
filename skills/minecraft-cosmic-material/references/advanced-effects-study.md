# 六份参考的进阶学习：Halo、黑洞、折射与绘制生命周期

用于已有 Cosmic 算法之外的设计或研究。根据效果选择本页的一部分；不是要求每个普通物品都增加后处理。来源为 sources.md 中 L/F/N/S/B 五份归档及 ArcaneVortex 0.6.8（A）。本轮为静态源码/字节码分析，没有新增 Forge 运行认证。

## 已有内容与增量

方向星层、mask、模型 overrides、shader 重载、分形星海、体积云、自然语言制作方法已在其他参考中。新的学习重点是：把效果拆成可组合模块，识别屏幕场景依赖，追踪真实绑定，并给每种模块制定专用验收。

| 设计要求 | 可学习的来源 | 推荐归属 |
|---|---|---|
| GUI 背后的光晕、旋转魔法环、脉动副本 | L/F/B Halo；A GlowHelper | Cosmic 的外部装饰层，与内部场分开 |
| 水晶外壳、玻璃边缘亮 | A rendertype_glass_sphere | 几何适配器 + rim 层 |
| 内部黑洞、吸积盘、弯曲星光 | A BlackHoleRenderer + black_hole | 中心天体算法；屏幕扭曲另设接入 |
| 棱镜、碎晶折射、背景扭曲 | A glow_edge 的 uType=2 | 场景采样 pass；不等同普通表面换色 |
| 多材质、多 pass 相互不污染 | A SceneCopyTarget / RenderStateSnapshot；N 队列；B shader 注册 | 绘制生命周期和状态所有权 |
| 物品栏大量材质仍可辨识 | A 距离分档与噪声简化；F/B 模型开销 | 覆盖面积预算和小图质量策略 |

不为每个效果名称创建一个 skill。材质内的算法与装饰层放入本 skill；彩色原生附魔光效沿用独立的 minecraft-colored-glint；只有独立完成场景后处理接入与实测后，再考虑单列后处理 skill。

## Halo：将形状、动画、混合与覆盖分开

L/F/B 的 Halo 可以学习 GUI 背景 quad 和主体装饰的分层。B `client/model/HaloBakedModel.java` 中，halo 仅在 GUI 分支绘制，pulse 使用随机缩放和透明主体副本，随后绘制原模型。这不是三维轮廓提取或屏幕 bloom。

A `CosmicRender/GlowHelper` 字节码确认：读取 EffectConfig.glowTexture / glowRenderType / glowAnimation，计算 scale、rotation、alpha，生成四顶点，选择 additive/darken/multiply/overlay 命名的 RenderType；BufferSource 路径会提交对应类型。具体混合方程必须继续查 RenderType，不能从命名推出 GL factors。外部调用上下文尚未在本轮完整闭合。

可提炼的接口是 `装饰几何 + 动画函数(time, seed) + 颜色/alpha + 混合/深度策略 + 生效场景`。使用稳定时间/seed 的正弦、包络或连续噪声制作可复现脉动；不要将逐 draw 随机抖动强制作为所有光晕的风格。沿用原模型姿态，不因加光晕把所有工具变成默认姿态。

验收：同 time/seed 固定图、低/高帧率下节奏、GUI 多槽位、主体遮住光晕的关系、窗口裁剪和手持是否按设计启用。Halo、rim、glint、屏幕 bloom 分别开关后检查组合，不把四者称为同一种发光。

## Rim：法线与视线是独立契约

A `rendertype_glass_sphere.fsh` 使用 normal 与 viewPos 点积构造边缘权重，再控制颜色和 alpha；读取其 vsh 可见法线直接乘 ModelViewMat，片元没有场景颜色折射采样。应称其为玻璃外观/边缘亮的参考，不称为已实现真实折射。

移植建议：把法线和视线转到同一空间；非均匀缩放时使用与模型变换对应的法线矩阵。generated 平面物品和圆滑球体的法线分布不同，不能预期同一个 rim 函数自然勾出剑的二维剪影。二维外扩轮廓需另一种 mask/几何方法。

验收：斜视、背面、非均匀缩放、硬边模型、左右手、透明排序；区分保留表面和只显示边缘两种意图。

## 黑洞：先选内部空间，后选场景畸变

已闭合静态链路：`Content/ModRender` 注册 BlackHoleRenderer；其 render 调 getBlackHoleShader；ModShaders 注册 `black_hole`。renderer 调 SceneCopyTarget.copyMainColor，绑定 MainColorSampler、MainDepthSampler 和两张素材，最后 drawFullscreenQuad。

A `black_hole.fsh` 值得学习的结构：

- raySphereIntersect 限定主体区域；倾斜矩阵决定盘面。
- WarpSpace 逐步偏折方向，GasDisc 单独构造吸积盘发光与覆盖，核心吞噬作为另一判断。
- 球外背景仍有 lensedDir→屏幕 UV 的采样；它是全屏场景效果，并非可直接塞进物品的 shader。
- 60–200 次主体迭代随距离调整，另一射线追踪分支预算不同；高频噪声有 lodBias 分支。
- 极角坐标有双采样接缝混合；这是可检查的处理思路，不是自动无缝的证明。

对“剑里有黑洞”，优先定义物体局部黑洞与内部星域，让偏折射线查询内部背景场。只有用户确实要剑扭曲背后的世界时，才增加场景颜色/深度路径。中心、盘面、吸收、星域分别参数化；不能只给原 Cosmic shader 换一张黑洞图。

不能直接照搬的地方：`reconstructWorldPos` 输出的 sceneViewDist 实为 length(viewPos)，后续 sphereEnterViewDist / nearestHitViewDist 是 -viewPos.z，屏幕边缘两者不同。最近命中由弯曲路径长度沿原直线近似重建，不是精确弯曲路径命中。GasDisc 的 coverage 缩放使用 MAX_ITERATIONS 常量，不能据此宣称实际迭代数改变时光学厚度不变。此处是静态问题与待测项，不是原 JAR 已观察到的画面故障。

验收：固定画面中心/屏幕边缘遮挡、近远、相机进入包围体、盘背面、极角接缝、质量切换亮度、多个主体重叠。记录这是美术偏折模型，不宣称广义相对论数值模拟。

## 折射：从 shader 文件名转向数据流

A `glow_edge.fsh` main 的 uType=1/2/3/4/5 分别走银河样噪声、折射、流彩、单色彩虹和原纹理。Sampler0 alpha 用于剔除；文件名本身不能证明轮廓检测或 bloom。uType=2 用 Voronoi 分区、扰动方向、RGB 不同偏移及屏幕颜色模糊制造碎晶感。本轮确认算法存在，未确认全部分支的 Java 实际选择。

可借鉴“形状场→偏移场→颜色分离→合成”的拆解。对世界折射，列清输入颜色来自哪个阶段、采样 UV 和输出 framebuffer；对内部星空折射，直接扰动内部背景场，可避免引入世界颜色采样。色散强度和整体偏移分别调节。

成本警示：gaussianBlur 是二维嵌套循环，最大 15×15 采样；分支中主颜色及红/蓝通道可各调用一次，静态最坏合计 675 次纹理采样，未含噪声。不能据此推算 FPS，但足以优先评估分离式模糊、共享低分辨率中间图和关闭小图模糊。优化须对比外观，并确认阶段依赖。

## 状态与资源：学习生命周期，不复制“万能快照”

SceneCopyTarget.copyMainColor 根据宽高变化销毁/创建 TextureTarget，用颜色 blit 得到独立颜色纹理，并重新绑定主 framebuffer；可学习尺寸驱动重建与资源复用。它没有在此方法中复制深度，也没有保存任意调用者原先的 framebuffer。不能说它已经解决所有反馈、嵌套渲染或光影兼容。

RenderStateSnapshot 捕获深度、混合、剔除、颜色 mask、polygon mode 与矩阵；字段中虽有 cullFace，restore 未使用它，也不包含 framebuffer、viewport、scissor、shader、活动纹理绑定。restore/cleanup 对模型视图栈有配对操作。因此不要仅因类名有 Snapshot 就认为状态完整或异常安全。

实施建议：每个 pass 声明实际读取/修改的状态及资源，明确捕获、draw、flush、恢复的所有者，异常路径使用 finally。仅复制所需资源；场景颜色副本存在时仍单独核查深度附件和当前写入目标。固定设 depthMask=true、disableBlend 不是恢复任意调用前状态。优先沿框架状态接口协调缓存，不盲目混用原始 GL 与缓存状态。

结合 N 的 deferred queue 和 B 的 shader 实例归属问题，draw packet 捕获本次几何、矩阵、参数与资源代次；不要长期保留可变上下文。重载、窗口变尺寸、离开世界、队列未消费和 pass 中途失败都要有明确定义。

## 性能与证据分级

原始 shader 的“距离降级”可作起点。移植物品后优先测覆盖像素、同屏件数、每片元步数/采样数、draw 次数、CPU 几何生成和 GPU 时间：GUI 距离并不能表达物品图标实际面积。降级顺序由画面决定，通常先减少高频细节而保留主形；体积步数变化时重新检查积分权重。

证据分开标记：资源存在 → 注册存在 → 调用链闭合 → 独立算法预览 → 目标 Forge 实测。现有 Cosmic Material Lab 的认证只覆盖已有 baked-item 实现；不能给本页黑洞、折射、rim 或新 Halo 继承认证。

## 本地证据位置

A 身份及既有算法研究见 arcanevortex-study.md。新的只读 javap 输出：`E:/着色器渲染/research/cosmic-material/authoring/arcanevortex/bytecode/BlackHoleRenderer.txt`、`GlowHelper.txt`、`SceneCopyTarget.txt`。GLSL 在同级 extracted/assets/arcanevortex/shaders/core；状态类反编译在 `research/evidence/jar-java/com/erchien/arcanevortex/Content/Render/RenderStateSnapshot.java`。B Halo 文件在五份参考解压目录中。

这些定位针对本地快照；重新取得资料时核对原归档哈希。本技能仅保存分析与原创工作方法，不内嵌 ArcaneVortex 的反编译源码或原素材。
