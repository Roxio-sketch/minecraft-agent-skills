# Forge 1.20.1 实现路线与版本桥接

## 已确认的版本边界

目标：MinecraftForge 1.20.1、Java 17、Mojang official mappings。随附探针固定 Forge **47.4.10** / ForgeGradle **6.0.54** / Gradle **8.8**，这是本次实际编译组合，不代表最新推荐版本。对实际工程沿用用户已选版本，变更后重新构建验证。

所给 IP/Veil/Lodestone 大部分是 MC 1.21.1 / Java 21 代码：`DeltaTracker`、`Matrix4fStack`、较新 BufferBuilder/ResourceLocation 入口、Fabric/NeoForge 事件和 mixin 不能直接复制。Veil Example 还依赖另一 Veil 版本。以能力需求映射 API，不以同名类假定行为相同。

## 已编译的锚点

见 `assets/forge-api-probe/src/main/java/research/subscene/ForgeRouteProbe.java`：

- `TextureTarget(width,height,true,Minecraft.ON_OSX)` 创建独立含深度目标；`checkStatus()`、`resize(...)`、`destroyBuffers()` 可用。
- `enableStencil()` 是 Forge 增强，**会 resize/reallocate**；仅用于自己刚创建或受控重建的目标。不要在 opaque 完成后临时给主 FBO 开 stencil。
- `RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES` 可作为默认 opaque 边界候选，但 modded pipeline 下必须用实际执行顺序验证。
- `Camera.setup(...)` 与子类中 protected `setPosition/setRotation` 可用；该探针只覆盖位置/yaw/pitch，任意 roll/mirror/general transform 仍需完整矩阵适配。
- `LevelRenderer.renderLevel(PoseStack,float,long,boolean,Camera,GameRenderer,LightTexture,Matrix4f)` 签名可编译。

调用这个 renderLevel 签名**不等于**完成上下文隔离，不能把 `renderPreparedWorld` 方法直接当真实二次世界 renderer。探针没有 mods.toml、没有注册事件、没有游戏入口。

## 从已验证工程起步

新建轻量平面窗口/可进入视觉领域时，先读 [Ink Realm 实测参考](runtime-inkrealm.md)，运行 `scripts/scaffold_inkrealm.py <新的空目录>`。模板含 `mods.toml`、client 入口、Shader 注册、owned FBO、内外相机映射、生命周期和测试控制入口。它与下述仅签名探针不同，可以构建并部署。

保留集成壳层，优先替换 `landscape.fsh` 中的 scene source 和艺术参数。入口外形在 `aperture.fsh`；修改外形还需同步穿越 mask、depth probe 和 culling，不能只改视觉 discard。需要球体/第二世界时依据相应架构扩展，不硬套平面门的 UV 和 side 判定。

Java/事件/生命周期更改需重新构建、退出目标客户端、替换单一同 ID JAR、重启。仅 shader/resource 修改可通过已启用资源包的同命名空间 override + F3+T 重载迭代；直接改源码目录不会改变运行中的 JAR。示例的 `time/resolution` 请求即时生效；`reload` 只重载已部署资源，没有源码热编译器。

每次先验证功能合同，再提升画质。记录构建 JAR 与部署 JAR 的 hash，确认实例仅加载一个 `inkrealm`；模板生成不会自动重命名 mod ID。轻量示例实测通过不升级为世界重入/第三方管线认证。

## 从既有工程接入轻量原型

1. 建立专用 client-only renderer/controller，服务端类加载路径不引用 client/GL 类；真实可安装 MOD 配置 Forge mods.toml、资源目录、版本和重载监听器。
2. 通过目标 Forge 支持的 shader 注册/资源重载途径加载程序，维护 resourceGeneration。独立 source 接收实际 Camera/Projection 和相对坐标，不共享主世界 mesh。
3. render thread 创建 owned FBO；首版选 color+depth，无需 stencil 时不启用。明确 shader 的颜色空间、采样器和 RenderSystem 状态所有权。
4. 订阅合适 `RenderLevelStageEvent`，在主 opaque 可用后进行子场景渲染/入口合成。用 scoped depth 和 parent projection 保留前景。事件本身只给时机，不提供完整副世界能力。
5. 注册客户端命令/调试开关，生成可重复的独立测试场景及 before/after 截图。单个窗口跑通，再增加球内、透明、嵌套。
6. 窗口 resize、重载、世界退出、玩家实体替换都需走生命周期代码。以实际 API 编译和游戏日志确认，不以伪代码完成度替代。

Forge 阶段注释明确：AFTER_TRANSLUCENT_BLOCKS 有透明排序/Fabulous caveat，AFTER_PARTICLES/AFTER_WEATHER 可能位于 Fabulous 专用目标，AFTER_LEVEL 已在 level 绘制结束。晚合成不能消除已混进颜色的所有主世界透明层；不要为方便统一放最后再宣称正确遮挡。

## 真二次世界的工程分层

- 第一层：SceneSource 与 Compositor 的普通 Java 协议，不依赖特定 loader。
- 第二层：Forge 事件/重载/命令适配，以及目标版本 GL/ShaderInstance/RenderTarget。
- 第三层：引擎可重入 adapter，按需 accessor/mixin 访问真实 camera、target、renderer、buffers 和派发器。所有注入点用目标映射检查，避开静态初始化提前触碰客户端对象。
- 第四层：RemoteWorldRegistry + 订阅协议 + chunk/entity/time 同步及生命周期。验证单个远端世界后再启用递归。
- 第五层：Embeddium/Oculus/具体 pack 专门 adapter。没有稳定 adapter 时拒绝启用相应高阶后端，并保留明确轻量能力。

同世界第二视角可先证明 context scope；第二 ClientLevel 验证还要检查远端数据是否真实更新、两个天空/实体是否独立、退出后是否释放，不能拿第一层的画面宣称第二层完成。

## 在用户选定实例中验收

可使用已安装 axolotl-launcher skill 发现并启动用户已选择的实例；Ink Realm 实测使用 ASTRA-TEST / ASTRA_VFX_TEST。这个名称属于该次证据，不作为未来任务自动选择实例的默认值。

构建 JAR 完成后校验 hash，再在客户端完全退出、不会扫描该文件的时机部署；不与启动并行更新 JAR。当前客户端已运行时先判断其归属和任务使用状况，按已有授权协调，禁止为了测试顺手终止无关游戏。

实际画面 + 控制/状态日志 + 被测 JAR hash 共同证明渲染。MCP-HELPER 的进程、端口、命令成功只证明控制链路，不证明画面正确。测试可借助已有接口，不能借用其他 Skill 的实测认证本实现。
