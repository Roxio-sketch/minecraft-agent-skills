# ArcaneVortex 物品勾边：八方向几何重绘

可直接使用 [描边工作台](item-outline-workbench.md) 制作二维配色草稿；其二维 alpha 描边与本文三维参考算法不同，配方暂不直接驱动 Forge。真实的八角几何实验及其 Forge 1.20.1 运行证据见 [item-outline-runtime.md](item-outline-runtime.md)。

## 证据与结论

来源为 ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge.jar，SHA-256 `27c5d799a488a418a588b978bb77da6bd48bbe48d8bc5c6a11cfb7b6f60a8b97`。本轮重新反编译 CosmicBakeModelNeo，并用 javap 检查关键字节码。证据在 `E:/着色器渲染/research/item-outline/`。仅静态分析，未启动原 JAR 或新增游戏验收；包内 Neo 类名不代表该 JAR 使用 NeoForge。

**其描边由 Java 重绘几何产生，glow_edge shader 为几何上色。** 不是 Sobel 屏幕边缘检测、不是邻域 alpha 膨胀，也不是单纯按法线外扩的 inverted hull。与 GlowHelper 的背景 Halo 是两条独立路径。

## 从物品到绘制的链路

1. 物品模型 JSON 指定 `loader: arcanevortex:cosmic_neo`。例如 van_sh、sky_ripper、nebula_bow、prismal_abyss。loader 去掉专用字段后烘焙原模型，包装为 CosmicBakeModelNeo。
2. COSMIC_EFFECTS 是按 Item 配置的 Java 表，指定 glowEdge、颜色、宽度、类型、colors、all。JSON 中 cosmic_neo.mask 用于 Cosmic 层，不是独立描边 mask；本快照不是任意 NBT 配色接口。
3. ItemRendererMixin 在 Helper.coreMod 未启用且模型为包装类型时取消原入口、应用显示变换，进入普通路径或收集延迟绘制。另有 core 渲染 helper，本轮不把单一 Mixin 宣称为所有环境唯一入口。
4. 普通路径依次处理装饰、基础物品、renderGlowEdge、可选 Cosmic。GUI 的 fullGlow 背景来自 GlowHelper，不负责贴边轮廓。
5. renderGlowEdge 再次解析 overrides，使用当前状态的模型 getRenderPasses，写入 ModRenderType.glow_edge 的 VertexConsumer。
6. ModShaders 注册 glow_edge，ModRenderType 使用其 getter；GLSL 的 Sampler0 采原 block atlas UV，alpha 小于 0.1 则 discard。

光影检测分支、队列与第一人称深度清理是原工程的接入策略，不是跨 shader pack 兼容认证。移植普通描边不应默认复制全局清深度操作。

## 几何算法

设模型空间偏移为 d。生成立方体八个角方向 `(±d, ±d, ±d)`，每个方向将 PoseStack 平移，再提交选中的原 quads。

普通物品只遍历该 pass 的 general quads（side=null）。对每个 quad，取离散 Direction 法线 n，仅保留 `dot(n, offset) > 0`。这不是逐顶点真实法线外扩。方块物品遍历六个方向的 quads 并做类似筛选；general quads 另外无条件提交。

轮廓来自平移后的带 alpha 图形超出原轮廓的区域，以及与基础几何的深度关系。算法没有显式计算“扩张 mask 减原 mask”，不能保证仅有外轮廓、没有内部覆盖或凹角缺陷。立方体角点偏移也不等于圆形等距膨胀。

每个 pass 被遍历八次，筛选会减少输出面；不能简单称八次 draw call，实际 draw 取决于缓冲批次。多层纹理、随机 quads、透明模型、第三方按 RenderType 过滤的模型都需要专项测试。

## 宽度与上下文

默认 glowEdgeWidth=0.008。getVector3fs 在 GUI 中再加 0.0125；普通 renderItem/renderItemDirect 对非第一人称场景先加 0.0035，而 renderGuiItem 不加这一项。

因此普通 renderItem 的 GUI 分支默认可能使用 `0.008+0.0035+0.0125=0.024`，专用 renderGuiItem 默认为 `0.0205`；第一人称为 0.008。两种入口不能未经区分就宣称视觉厚度完全一致。

单位是当前 PoseStack 下的模型位移，受模型 scale、投影、距离影响，不是像素。设计固定屏幕宽度时需另实现屏幕域方法；想保留参考模型空间风格则给 GUI、手持、掉落物明确各自宽度规则，不用一串隐含加数。

## 描边里填什么

| uType | shader 实际输出 | Java 配置例子 |
|---|---|---|
| 0/默认 | uColor 纯色，包含其 alpha | van_sh 淡色；black_golden_sword 暖金色 |
| 1 | 屏幕坐标下的两层银河样噪声 | odin、van_sh_radiant_sun |
| 2 | 采样 ScreenTexture 的碎晶折射/色散 | sky_ripper，配置 all=true |
| 3 | color1/2/3 循环流动渐变 + 扰动 | nebula_bow、stellar_vortex、overlord |
| 4 | hue 随时间改变的整体彩虹色 | prismal_abyss、black_meteor_rainbow_trace |
| 5 | 原 Sampler0 纹理颜色 | shader 有分支；本轮未确认表内实例 |

上述配置不是游戏画面验收。all 参数在所读方法中只控制非 GUI 场景下是否提前 endBatch；没有切换筛面或全覆盖算法。不要根据 glowEdgeAll 名称发明“全模型描边”的行为。

time 写入为 TimeSystem.getShaderTimeValue()*200；不是直接以秒解释倍率。类型 1/2/3 依赖 gl_FragCoord，移动物品在屏幕中的位置会改变采样；类型 4 在同一时刻整体彩虹同色，不能称沿刀刃流动彩虹。类型 1/3/4 输出 alpha=1，不继承 uColor.a；渐变分支不会自动把 uColor 当总染色。

## 绘制状态：以真正 draw 时为准

用本机 Forge 1.20.1 mappings 核对，glow_edge 的 ShaderInstance 与 RenderType 均为 NEW_ENTITY；RenderType 配置 TRANSLUCENT_TRANSPARENCY、NO_CULL、LIGHTMAP、COLOR_WRITE、LEQUAL_DEPTH_TEST。shader 只使用 Position/Color/UV0 的部分输入，不代表 Java 应改成另一顶点布局。

renderGlowEdge 临时用原始 GL 禁用深度测试，写完顶点后在 finally 恢复深度启用与 depthFunc，但方法没有在 finally 之前 endBatch(glow_edge)。之后的 Cosmic 路径开头或外层才可能 flush；同类型缓冲也可能延后。因而不能从这段 glDisable 就判定实际片元关闭深度，更不能保证描边穿墙。还要考虑 RenderSystem 缓存和实际 RenderType setup。

提炼规则：先决定深度策略，再让 RenderType 管理该策略；按材质参数分批，并保证参数、纹理副本在实际 draw 期间稳定。局部 GL 状态更改应覆盖真实提交范围而非只覆盖顶点生成；避免缓存状态与原始 GL 状态分离。

## 已确认的问题与待测风险

- **三色数组越界条件**：Java 与字节码均显示 `colors.length >= 2` 后读取 colors[2]，恰好两个颜色时可能抛异常。默认配置为三个颜色，不能据此宣称所有内置物品会崩溃。新实现要求至少三色或明确定义二色插值。
- **参数延迟提交**：所有描边共用 ShaderInstance/RenderType，在提交前设置 uniform，尾部不单独 flush。多物品串色是需要验证的风险，不是本轮已观察到的故障。
- **不必要的场景复制**：该方法所有类型均执行 SceneCopyTarget.copyMainColor 并绑定 map_1；fsh 只有折射分支需要 ScreenTexture，TargetTexture 声明后没有实际采样。按需要才复制场景，纯色描边不必付这个成本。
- **状态与异常恢复**：局部 finally 仅恢复深度部分；内层 push/pop 未各自 finally 保护，colorMask 被强设全开。重写时按实际修改项与作用域恢复。
- **几何覆盖范围**：普通物品只收 general quads、法线是离散面向；不能承诺任意三维/骨骼模型。原生 overrides 解析可借鉴，但挂接 BEWLR 与穿戴模型仍需专门适配。

## 如何提炼为可复用描边能力

从用户描述拆成：范围（外轮廓/孔洞/局部边）、宽度单位（模型/像素）、边内材质（纯色/渐变/星云/折射）、遮挡、动画和显示上下文。

| 目标 | 合适起点 | 必须另外解决 |
|---|---|---|
| 复现 ArcaneVortex 的平面物品勾边 | 八方向偏移 + 原 alpha + 面筛选 | 深度/批次、窄角、不同模型尺度 |
| 任意模型的屏幕等宽轮廓 | 隔离物品轮廓到 FBO，做 mask 膨胀减原 mask | 深度、重叠物品身份、透明与采样边界 |
| 光滑三维外壳轮廓 | 法线外扩/反面壳 | 硬边法线、非均匀缩放、平面 alpha 图案 |
| 轮廓外柔光 | 描边 emission + 独立 bloom | 发光选择、模糊预算与生命周期 |

后面三种是迁移建议，不是原 JAR 所读方法的实现。不要用模型整体中心缩放冒充该八方向方法；RenderBlender 的 GlowEdgeBakedModel 正是另一种 translate+scale 重绘，二者需分开描述。

推荐数据契约是 `OutlineStyle(width, widthSpace, colorMode, palette, timePolicy, alpha, depthPolicy, contexts)`。这只是新接口设计，不是原版已有 NBT/JSON。描边材质与轮廓几何分离，内部 Cosmic、Halo、原生 glint 与 bloom 各自开关。

验收：固定时间比较轮廓；16/32px GUI、GUI scale、左右手、第三人称、掉落、展示框；弓不同 override；凹角/孔洞/透明像素；两种颜色 A-B-A 同屏；地形遮挡；重载与窗口尺寸；无 player 的预览；多物品场景复制次数和 CPU/GPU 成本。记录不同上下文真实宽度，不能以 GUI 一张截图宣称全部模型支持。

## 源码定位

- 新反编译：research/item-outline/decompiled/com/erchien/arcanevortex/CosmicRender/Neo/CosmicBakeModelNeo.java；关键方法 renderGlowEdge、getVector3fs、shouldRenderQuad、EffectConfig.Builder。
- javap：research/item-outline/CosmicBakeModelNeo.bytecode.txt，renderGlowEdge 中字节码偏移 155–210 为三色条件和读取。
- 既有反编译：research/evidence/java 下 ItemRendererMixin、GameRendererMixin、CosmicModelLoaderNeo、ModRenderType、ModShaders、AvaritiaShadersNeo。
- 原 GLSL：JAR assets/arcanevortex/shaders/core/glow_edge.{vsh,fsh,json}，本地 extracted 副本在 research/cosmic-material/authoring/arcanevortex。

以工作区 E:/着色器渲染 为上述相对路径根。许可沿用原 JAR 的 All Rights Reserved 声明；skill 仅保存方法、结论与定位，不分发原反编译源码/素材。
