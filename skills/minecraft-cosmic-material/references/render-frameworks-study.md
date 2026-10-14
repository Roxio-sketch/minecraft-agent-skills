# 从 Immersive Portals、Veil 与 Lodestone 学习材质之外的渲染

适用：用户需要物品内另一个场景、镜面、选择性 bloom、动态照明、动作拖尾或复杂多 pass。纯方向式 Cosmic 仍使用已有轻量路径，不因参考框架存在就引入完整世界渲染。

本页依据用户提供的四份 ZIP 静态分析。原始归档哈希见 `E:/着色器渲染/research/render-frameworks/archives.json`，代码解压在同目录 sources。没有构建、安装或游戏实测这些工程；原有 Forge 1.20.1 Cosmic 验收不覆盖本页新增效果。

## 版本身份先于 API

| 快照 | 工程声明 | 使用方式与注意点 |
|---|---|---|
| ImmersivePortalsMod-1.21 | MC 1.21.1、Fabric 0.16.2、mod 6.0.6 | 学习递归视图、裁剪、可见性；非 Forge 1.20.1 直接依赖 |
| Veil-1.21 | MC 1.21.1、Java 21、Veil 4.5.1；Fabric/NeoForge 模块 | 学习 framebuffer、阶段化后处理、多相机和光源 |
| veil-example-mod-1.21 | MC 1.21.1、Fabric，依赖 Veil 4.2.1 | 镜面/地图/实体范例；不是与本次 4.5.1 自动匹配的已编译工程 |
| Lodestone-1.21 | MC 1.21.1、Java 21、NeoForge 21.1.228、mod 1.8.3 | 学习 bloom、拖尾和构建器；不是 Forge 旧版 API |

以上来自归档内 gradle.properties，并非最新发行版声明。Lodestone 的 minecraft_version_range 写成 [1.21,1.21.1)，与目标 1.21.1 存在冲突；LICENSE.txt 标题 LGPL v3，但 mod_license 为 GPLv3。保留这一不一致，实际采用时核对元数据和授权依据，不替作者裁定。其他根许可：Immersive Portals Apache 2.0、Veil LGPL v3、示例 CC0。具体素材仍需查自身标注。

## 一、区分“宇宙材质”和“能看到另一个世界的窗口”

原 Avaritia 的方向场生成程序图案；真实场景窗口需要额外相机、被观察场景、可见区域和绘制上下文。先确定需求：

- 只看到程序星空：已有方向场/局部薄层即可。
- 看见自建的小型三维场景：离屏渲染小场景，再让原物品 mask 采样输出。
- 看见 Minecraft 世界的另一视角：多相机/世界渲染接入。
- 需要递归、跨维度和可穿越门户：独立门户系统，不能由一个片元 shader 完成。

### Immersive Portals 的具体增量

源码锚点（工程根下）：

- `src/main/java/qouteall/imm_ptl/core/render/renderer/RendererUsingStencil.java`：doRenderPortal、renderPortalViewAreaToStencil、clearDepthOfThePortalViewArea、restoreDepthOfPortalViewArea。
- `.../render/context_management/PortalRendering.java`：portal layer 栈、最大层数与嵌套裁剪。
- `.../portal/PortalRenderInfo.java`：renderAndDecideVisibility、上一帧/当前帧查询、预测与等待计数。

已读链路：门面写 stencil → 查询有无通过样本 → push 层 → 在区域中清深度 → 渲染目的场景 → pop 层 → 恢复门面深度 → 收拢 stencil。前裁剪约束目的空间中哪些部分能穿过窗口显示；它与普通物品纹理 alpha mask 职责不同。

可提炼的方法：按视图层持有上下文、为递归设置预算、先判断窗口是否值得渲染、在返回父视图时恢复深度与裁剪。查询路径并非永不阻塞；它会按预测条件决定是否读取当前帧结果。不要把“使用上一帧”夸大成无延迟且无闪烁的保证。

对于剑里的小宇宙，可学习窗口限制与视图契约，不必移植整套跨维度世界管理。需要真实跨维度场景时，还要单独研究客户端世界数据和区块生命周期，本轮未闭合这部分。

## 二、Veil 示例：从抽象接口到实际镜面调用链

示例静态链路：VeilExampleModClient 注册 MirrorBlockEntityRenderer，并在 AFTER_LEVEL 调 renderLevel；该方法反射相机位置/look/up，修改斜裁剪投影，再调 VeilLevelPerspectiveRenderer.render，最后复制结果到镜面缓存。实体 render 消费缓存纹理，并在额外视图中提前返回以阻止递归。

源码锚点：示例 `src/main/java/foundry/veil/example/VeilExampleModClient.java`、`client/render/MirrorBlockEntityRenderer.java`；库 `common/src/main/java/foundry/veil/api/client/render/VeilLevelPerspectiveRenderer.java`。

库 renderer 在进入子视图前 flush，备份相机矩阵、雾、尺寸、投影、剔除和部分兼容状态；renderLevel 的 try/finally 中恢复上下文。静态布尔 renderingPerspective 用于防重入，是单额外视图策略，不是递归镜面系统；try 之前也有状态修改，不能称所有准备异常都已得到保护。

值得迁移的设计：

1. 子相机、窗口投影、FBO 尺寸与材质采样明确配对，避免用主视角坐标偷代。
2. 相同平面/同一场景可共享输出，但缓存键需要包含足够的视图与场景身份。
3. 分开“本帧更新内容”和“物体上显示内容”，记录是否会消费上一帧纹理，检查快速转头时延迟。
4. 明确重复进入时跳过、用上帧缓存还是允许有限递归；冷启动缓存为空必须有回退。
5. 窗口变尺寸、离开世界、资源重载时清理 GPU 资源。

对物品的推荐起点：独立的小场景 FBO，而非每个背包格重画一次完整世界。普通 baked quads 负责外形与 mask，内部场景纹理由另一适配器生成。

## 三、从 Halo 升级到选择性 bloom

Veil `impl/client/render/pipeline/VeilBloomRenderer.java` 创建 RGBA16F 颜色附件并包装主深度纹理，FramebufferStack 管理目标切换，flush 后清空颜色；当 Iris shader pack 已加载时 setup/clear 路径直接返回。这是明确的兼容策略边界，不是“Iris 下 bloom 已兼容”。

Lodestone `modules/rendering/postprocess/effects/PhysicallyBasedBloomPostProcessor.java` 和 `assets/lodestone/shaders/post/pb_bloom.json` 提供另一条可读路线：

```text
选定的发光几何 → bloomColor
→ 水平/垂直模糊（1/2）→ 水平/垂直模糊（1/4）
→ 水平/垂直模糊（1/8）→ 与主颜色合成 → 写回
```

Java 调整各级尺寸、线性过滤、复制深度，并在处理后清理发光目标。JSON 中最终合成读取 blurY8；不能只凭多个尺寸或类名就称其为完整的物理多尺度散射模型。

这可以让剑刃的星体、符文、能量核产生屏幕外扩光，而普通金属/GUI 背景不参与。**独立 emission 选择**比对整屏所有亮像素统一泛光更适合可控设计。

Forge 1.20.1 迁移建议：保留可复用的发光目标、深度来源、分离式模糊和合成生命周期；重新实现目标版本的事件与 RenderTarget 入口。不把 Lodestone JSON 中扩展字段当原版 PostChain 已支持，也不将 Veil pinwheel 配置当普通资源包格式。

验收：发光对象被墙遮挡、透明边缘、多个发光物、关闭后无残留、极小窗口尺寸、重载、GUI 与世界是否分离、glint 是否意外被加倍。HDR、曝光与最终显示转换需单独定义；bloom 不等于照亮周围方块。

## 四、后处理数据流可成为通用能力

Veil `api/client/render/post/stage/CompositePostPipeline.java` 的 codec 包含 stages、textures、framebuffers、renderStage、dynamicBuffers、priority、replace；apply 按阶段顺序执行，并在目标尺寸改变时重建临时 FBO。这是显式的有序管线，不能说它自动解决任意依赖排序或资源别名冲突。

`api/client/render/dynamicbuffer/DynamicBufferType.java` 定义 Albedo、Normal、LightUV、LightColor、Debug 及各自格式。可学习把几何信息与最后颜色分开，支持按法线/深度工作的合成；不能仅列出 buffer 名就承诺包含完整 PBR 材质参数。

用于已有黑洞/碎晶 skill 的共同规则：每个 pass 写明输入图、输出图、格式、分辨率、产生时机、颜色空间、深度单位、所有者与清理点。读取场景副本后再写目标；明确共享/复制深度的差别。多个效果串联要决定折射看到的是 bloom 前还是 bloom 后的画面，而非随代码注册顺序碰运气。

新增技能方向可叫“渲染管线与离屏合成”，但正式实现型 skill 应至少带一个目标版本可运行的 ping-pong 与选择性 bloom 样例。这次先保存研究方法，不把静态参考包装成已完成工具。

## 五、动态照明：补足“亮但不照人”的差别

Veil `impl/client/render/light/InstancedPointLightRenderer.java` 将位置、颜色、半径等写入实例数据；动态缓冲提供另一条照明计算的数据入口。相较原 Avaritia 不受环境光变暗的材质，可以研究“武器颜色如何影响周围场景”。本轮只确认这些模块结构，没有闭合全部灯光/阴影接入及游戏画面。

应分别描述：表面自发光、屏幕 bloom、场景动态光、游戏方块光等级。这四种效果不自动互相实现。正式 skill 需要验证光源生命周期、持有者变化、灯数预算、遮挡/阴影能力及具体 shader pack 行为；不能让所有星点都变成独立灯。

## 六、Lodestone：把静态材质扩展到动作轨迹

锚点：`systems/rendering/trail/TrailPointBuilder.java`、`systems/rendering/builder/WorldVFXBuilder.java` 的 renderTrail/renderConnectedPoints。前者存点、tick 年龄并移除超期点，build 支持 partialTicks；后者按路径构造条带，宽度函数和颜色操作与点位分离。

可形成独立的“武器拖尾/能量束制作”工作流：

- 几何来源：轨迹历史，而非把物品模型再画一次。
- 更新：tick/距离驱动采样，渲染插值；瞬移、跨世界或姿态跳变时断开。
- 设计：路径形状、沿程宽度、沿程颜色/alpha、纹理推进、寿命和混合分别控制。
- 与 Cosmic 组合：条带可拥有自己的星空材质，武器表面、动作残影和 bloom 各自保留开关。

注意参考 trailLength 是年龄淘汰阈值，addTrailPoint 本身未限点数；若逐渲染帧加点，不能据此保证内存和厚度独立于 FPS。实现时明确采样策略、容量和稀疏化，测试静止、快速转弯、重复点、两点退化和暂停。BEWLR/骨骼武器的挂点采样仍需适配器，不从原版剑推断全部动作兼容。

## 学习优先级与 skill 归属

| 优先级 | 可复用工作流 | 原因 |
|---|---|---|
| 先做 | 离屏合成、选择性 bloom、资源与状态生命周期 | 可同时服务 Cosmic、Halo、黑洞、碎晶 |
| 随后 | 动作拖尾与能量束 | 与已有表面材质正交，增加动作表现 |
| 按明确设计需求 | 物品内场景窗口、镜面 | 需要多视图但可先用小场景控制成本 |
| 单独项目 | 递归跨维度门户、动态光照/阴影 | 接入范围大，不能塞进普通物品材质改色任务 |

未来验收必须包含原生与附加 pass 分别开关、同屏多对象、快速相机运动、尺寸变化、资源重载、离开/重进世界和目标版本性能记录。源码方法存在、示例调用链存在、构建成功和游戏显示成功是不同证据等级。
