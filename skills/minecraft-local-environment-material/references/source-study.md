# 四个项目的源码证据与可迁移结论

研究对象为用户提供的本地归档快照，不代表项目最新发布版本。2026-09-29 完成相关数据流的静态追踪；未对四个 1.21.1 项目逐个启动验收。44 份关键源码/配置文件的身份与符号行号记录在 [source-evidence.json](source-evidence.json)，原归档哈希保留于研究工程的 `research/render-frameworks/archives.json`。以下路径相对于各项目根；Java 路径可按类名定位。

## 版本边界

| 快照 | 声明版本/平台 | 本研究用途 |
|---|---|---|
| Veil-1.21 | Veil 4.5.1；MC 1.21.1；Fabric/NeoForge；Java 21 | 主要管线、附件生产、矩阵、后处理与生命周期 |
| veil-example-mod-1.21 | Fabric MC 1.21.1；依赖 Veil 4.2.1 | 深度位置重建与高度雾的小型消费者 |
| Lodestone-1.21 | Lodestone 1.8.3；NeoForge 21.1.228；Java 21 | 辅助几何、bloom、深度时机、效果实例 |
| ImmersivePortalsMod-1.21 | 6.0.6；Fabric MC 1.21.1 | stencil、depth、状态边界交叉参考 |
| 目标适配 | Forge 47.4.10 / MC 1.20.1 / Java 17 | 独立原创 terrain-stage 后端 |

Example 的 Veil 依赖版本与提供的 Veil 库版本不同，不能假定两份归档组合即能编译运行。Lodestone 的 `minecraft_version_range=[1.21,1.21.1)` 与声明的 1.21.1 存在排除端点问题；这是快照元数据观察，不是本轮修复目标。

## Veil：从信息生产者追到最终合成

核心 Java 路径前缀：`common/src/main/java/foundry/veil/`；资源前缀：`common/src/main/resources/assets/veil/pinwheel/`。

| 证据文件/符号 | 观察 | 对本 Skill 的设计影响 |
|---|---|---|
| `api/client/render/framebuffer/AdvancedFbo.java`, `FramebufferManager.java`, `FramebufferDefinition.java` | 多颜色附件、纹理/渲染缓冲、resolve 的显式 mask；尺寸变化重建，屏幕相关尺寸和手动目标分开管理 | 明确 format/尺寸/采样数、附件所有权、销毁/重建次序；不能把全部 framebuffer 当颜色纹理 |
| `impl/client/render/framebuffer/AdvancedFboMutableTextureAttachment.java` | 外部纹理包装，`create/releaseId` 不创建/销毁借用纹理 | 借用 Minecraft 深度必须有非拥有引用；不能跟随自建颜色目标一起释放 |
| `api/client/render/CameraMatrices.java` | 上传投影/逆投影、视图/逆视图、相机与 bob；处理实际渲染投影中的 bob 变换 | 深度与矩阵作为同一视图的数据包；不能只从设置里的 FOV 重建 P |
| `shaders/include/space_helper.glsl`：`screenToWorldSpace`, `screenToLocalSpace` | 屏幕 UV 和非线性 depth 变 NDC，乘逆 P、除 w，再逆视图，按需加 camera | 可重建可见原几何的位置；采用 camera-relative 计算避开大世界 float 消减 |
| `api/client/render/dynamicbuffer/DynamicBufferType.java` | 声明 Albedo、Normal、LightUV、LightColor、Debug 等输出 | 这些是额外几何信息通道；未提供通用“方块/实体”类别 ID |
| `impl/client/render/dynamicbuffer/DynamicBufferProcessor.java` | 解析/修改 shader，依顶点格式找到 UV、法线、lightmap 等并写额外输出 | 创建 FBO 不能凭空获得 normal/light；必须有相应 producer 和空间/格式契约 |
| `impl/client/render/dynamicbuffer/DynamicBufferManager.java` | 按启用位管理附件、framebuffer、尺寸与 shader 重编译；Iris 路径拒绝启用 | 附件按需申请；激活存在延迟；兼容性是后端能力而非仅判断 jar 存在 |
| `mixin/dynamicbuffer/client/DynamicBufferLevelRendererMixin.java` | 为不同世界阶段切换 dynamic buffer，涉及地形、粒子、天气 | 必须记录某附件在哪些 draw 中有效；不能把全帧都当同一生产阶段 |
| `api/client/render/post/PostProcessingManager.java` | 按渲染阶段、优先级调度，等待动态缓冲有效，再 apply/resolve | frame snapshot、稳定顺序与“该帧尚无数据则跳过”应成为管理器职责 |
| `api/client/render/post/stage/CompositePostPipeline.java` | 有序 stage、命名纹理/FBO、动态缓冲依赖、resize | 借用多阶段资源描述与生命周期；不是自动任意图调度器 |
| `api/client/render/post/stage/FramebufferPostStage.java` | 将输入目标的 color/depth 暴露为 sampler | 资源 ID、附件、实际采样来源应可诊断 |
| `api/client/render/post/stage/BlitPostStage.java` | 检查同输入/输出 ID；设置目标并全屏绘制 | 使用独立输入快照或 ping-pong，避免读写同附件；别名目标也需检查 |
| `impl/client/render/pipeline/VeilBloomRenderer.java` | HDR 颜色目标、借用主深度、堆栈/阶段管理，活动 Iris pack 时跳过 | 边缘 emission 可以独立 bloom；借用深度不可由自己的资源管理器销毁 |

由此得到的通用数据流：

```text
当前视图的原世界绘制
  → scene color + depth（可选 normal/light/category 的真实生产者）
  → 世界空间重建与区域资格
  → 可替换的 MaterialDefinition
  → 替代颜色 / emission / distortion
  → 有序 bloom 或边缘处理
  → 对原场景合成并保留深度
```

本研究不采用 Veil 的镜面/多视角世界渲染作为主路线：任务所需几何已经存在于当前世界。重新渲染另一个世界增加了错误的语义与成本。

静态阅读的两个警示：`space_helper.glsl` 中若干其他齐次坐标 helper 与宏不适合作为任意 point/direction 的通用模板，不能把点的除 w 规则套给方向；DynamicBufferLevelRendererMixin 的 setup/clear wrapper 存在同样 target 文本，需按实际版本验证调用语义。这些是源码风险线索，未宣称本轮观察到对应项目的运行故障。

## Veil Example：最小链路与反例

前缀 `src/main/resources/resourcepacks/fog/assets/veil/pinwheel/`：

- `post/space_test.json` → `shaders/program/space_test.json` → `space_test.fsh`：连接 scene depth、空间 helper 与输出颜色。它证明示例明确消费深度来展示重建位置；不能仅因存在 JSON 就宣称资源包已启用或运行通过。
- `post/height_fog.json` 与对应 shader：相机射线与平面求交，使用相机/bob 数据。启发是将几何位置与局部体积关联，而非套用全屏颜色曲线。
- `src/main/java/foundry/veil/example/VeilExampleModClient.java` 注册内置资源包；注册、选择、加载、执行是四种不同状态。

高度雾示例中的 Intersection 命中标记和近平行分母缺少稳健处理路径，不能复制为产品算法。重写时对未命中、近平行、相机在体内、天空清深度和近平面作明确处理。

## Lodestone：辅助轮廓与后处理工程经验

Java 前缀 `src/main/java/team/lodestar/lodestone/`：

| 文件 | 可学习内容 | 不应混淆的边界 |
|---|---|---|
| `modules/rendering/postprocess/PostProcessor.java` | 临时 depthMain、副本尺寸、shader uniform、关闭旧 postChain、resize/reload | `time += partialTicks/20` 是按帧累积局部插值，不是正确 elapsed time；本 Skill 改用 tick 时钟 |
| 同目录 `PostProcessHandler.java` | AFTER_PARTICLES 捕获矩阵、AFTER_LEVEL 执行，避免重复深度 copy | 最终后处理位置不自动满足“只地形” |
| `mixin/client/LevelRendererMixin.java` | 在特定 PostChain process 前复制深度，表明透明 resolve 时机很重要 | 该 mixin 目标是 1.21 快照，不能直接移植到 Forge 1.20.1 |
| `effects/PhysicallyBasedBloomPostProcessor.java` 与 `assets/lodestone/shaders/post/pb_bloom.json` | 多级降采样与横纵模糊、深度/目标管理 | 这份资源最终使用 blurY8；不能泛称所有 mip 已按任意权重融合 |
| `systems/rendering/builder/WorldVFXBuilder.java` | sphere/ring/beam/trail 的几何构造与颜色/透明度装饰 | 绘制球壳只能作为边界提示，不能代替内部原表面的空间资格 |
| `systems/rendering/trail/TrailPointBuilder.java` | 点列和年龄淘汰 | 年龄有界不等于采样数量有界；应另加容量、最小距离和路径简化 |

迁移方式：主区域系统提供稳定形状、年龄与 seed；可选边界渲染器消费同一参数生成 ring/sphere/拖尾；emission 进入共享 bloom，结束时跟随区域注销。不要为每个技能另建一个全屏后处理链。

## Immersive Portals：仅研究底层约束

`src/main/java/qouteall/imm_ptl/core/render/renderer/RendererUsingStencil.java` 展示 stencil 层级、视面 mask、深度约束与状态恢复；`render/FrontClipping.java` 展示裁剪平面与作用域；`context_management/PortalRendering.java` 用于理解渲染嵌套状态。

可迁移：屏幕空间 stencil 适合作粗覆盖/排除优化；depth 决定可见性；进入退出作用域时成对恢复状态。

不可迁移为本任务：portal 的重绘世界、清除/重建门户内部深度、跨维度相机语义。球壳投影 stencil 只表明像素射线穿过球投影，不表明深度对应的原场景表面在球内。真正资格仍须世界点 SDF 或原几何材质判断。

## Forge 1.20.1 的独立核对

从本地 Forge 47.4.10 sources.jar 提取 `RenderLevelStageEvent.java`、`LevelRenderer.java.patch`、`GameRenderer.java.patch`，另对映射后的 1.20.1 LevelRenderer class 做研究用反编译。核对 chunk shader 的矩阵设置、ChunkOffset 和 Forge stage dispatch 后，才选择 AFTER_CUTOUT_BLOCKS。详情及实际接入限制见 [forge-1.20.1.md](forge-1.20.1.md)。反编译文本留在研究目录，不打包进可复用 Skill。

## 原创实现与许可证记录

### 透明层专项补充（0.2.0）

继续跟踪 Veil `PipelineLevelRendererMixin` 中 Fabulous target 的注册/移除、`DynamicBufferProcessor` 的 BLOCK/lightmap/normal 生产；`VeilClient` 的 translucent target shard 表明目标切换属于原 draw 契约。Example 的 Sodium `block_layer_opaque.vsh.txt` 展示结构化 shader modifier，而非最终颜色覆盖。

Veil NeoForge `DefaultFluidRendererMixin` 专门补齐 Sodium 流体 normal；`ShaderChunkRendererMixin` 按 vertex type、pass、fog 重编译 variant；`VeilForgeIrisCompat` 从 Iris 当前 pipeline 获取 render targets 和 loaded shaders。这些证明加速/光影适配不能复用原版 BLOCK 格式或只替换一个 getShader。

交叉核对 IP `RendererUsingStencil.onBeforeTranslucentRendering` 与 state restore，Lodestone `LodestoneRenderType` 的深度写掩码；它们不提供可直接用于本任务的透明厚度/折射信息。Forge 1.20.1 另核对 LiquidBlockRenderer、IClientFluidTypeExtensions、RenderType、ShaderInstance、GameRenderer resource cache，以及 vanilla translucent core shader。LiquidBlockRenderer 的所有顶点 Normal=(0,1,0)，真实法线必须另取。

实测发现 Moonlight 2.16.35 动态 light.glsl 覆盖；反编译它的动态资源生成器确认修改的是 minecraft_mix_light，translucent 使用的 minecraft_sample_lightmap 不变，才允许该确切 SHA。未知 include/core 内容继续回退。新增选定文件/符号和指纹见 [surface-source-evidence.json](surface-source-evidence.json)，详细设计见 [transparent-surfaces.md](transparent-surfaces.md)。原始第三方/反编译源不进入技能资产。

本 Skill 的公式组织、GLSL 原理样例、验证代码和 Forge 后端是新写的实现，不打包参考项目代码。Veil LICENSE 为 LGPL-3.0，Example 为 CC0，Immersive Portals 为 Apache-2.0；Lodestone LICENSE.txt 为 LGPLv3、gradle 属性写 GPLv3，元数据不一致应在将来真正复用代码时进一步核对。不要把“已阅读并理解”误作任意复制许可。

源码给出可行机制，独立数值/GPU 验证证明算法与本地实现的部分性质，游戏测试验证指定 Forge 后端；这三类证据不能相互代替。
