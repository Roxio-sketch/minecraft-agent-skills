# Forge 1.20.1 最小接入与升级路径

## 已核对的生产顺序

本地 Forge 47.4.10 sources.jar、LevelRenderer mapped class 和 GameRenderer patch 已检查，非依据 1.21 API 推断：

1. 地形 `solid → cutoutMipped → cutout`；随后实体、方块实体和后续透明绘制。
2. `renderChunkLayer` 为对应 shader 设置 `MODEL_VIEW_MATRIX = event pose.last().pose()`、`PROJECTION_MATRIX = 该层 projection`，ChunkOffset 为 chunk origin-camera。
3. 层末尾 shader.clear/VertexBuffer.unbind 之后、RenderType.clearRenderState 之前 dispatch Forge stage。
4. 因此 `AFTER_CUTOUT_BLOCKS` 可用捕获颜色/深度和事件矩阵处理截至此时的地形；后续实体、BE、透明/粒子保持自己的绘制。
5. `AFTER_LEVEL` 在世界绘制结束后、第一人称手部清深度之前，适合有限的最终世界合成；却无法单靠最终颜色深度筛出方块类别。

`RenderLevelStageEvent` 本身明确提醒 AFTER_TRANSLUCENT_BLOCKS 在 Fabulous 下存在排序/目标问题。不能因为事件名为 after 就假设主 framebuffer 上已合并所有层。

## 最小研究工程

`assets/forge-1.20.1-lab` 是完整 Java 17 / ForgeGradle 6.0.54 / Gradle 8.8 / Forge 47.4.10 工程。构建后 `reobfJar`；单独使用时将工程复制到用户项目目录，勿直接把已安装 skill 当游戏工作目录。

已在 ASTRA-TEST / ASTRA_VFX_TEST 实机通过所列地形后端测试（Forge 47.4.10、RTX 4060 Ti、Fancy）；原生附件读回与截图见 validation.md。不是所有 renderer、对象类别或透明层的通用认证。运行其他实例前按相同流程核对。

代码实现：

- opt-in 客户端 `/localmaterial sphere 0|1|2`，释放点取玩家脚底的固定位置。
- 0 为世界局部 Cosmic，1 水墨，2 熔岩，均为原创原理样例。
- 4 秒扩张到 20 格，后保持，再在末尾 4 秒收缩，总计 20 秒；`off` 立即关闭，`status` 输出计数。
- `hold 0|1|2` 固定半径/动画时刻作视觉对照；`probe` 为下一次有效 draw 排队一次颜色/深度读回，带 GPU 同步成本，只作诊断。
- 单活动球，不包含多区域/路径/网络同步管理器。持有玩家移动后中心不跟随；用户要求跟随时需增加 binding。
- AFTER_CUTOUT_BLOCKS 捕获独立颜色和深度副本，使用 `inverse(P*Vrel)` 和 CPU double 相机相对中心，屏幕三角仅写颜色；主深度不改。
- 从深度导数估计法线；新材质应用一次当前 fog 后与已带 fog 原颜色混合。它没有恢复精确 AO、lightmap 或实体法线。
- 资源重载释放自有 target，shader 注册更新实例；退出/世界卸载/死亡/时间结束清理。异常禁用而非无限重试。
- 检测到未知加速/光影 renderer 时禁用；FBO 不是主目标时跳过。

工程使用 Forge 原生 RegisterShadersEvent、RegisterClientCommandsEvent、RegisterClientReloadListenersEvent、RenderLevelStageEvent，均为 net.minecraftforge。它不是 Veil 的类名重命名移植。

## 借用与修改边界

### 0.2.0 可选透明几何后端

`TranslucentLayerMixin` 仅在原版 `renderChunkLayer` 获取 shader 的位置选择 namespaced BLOCK ShaderInstance，不替换网格、排序、深度状态或 alpha blend。`TranslucentMaterial` 提供原版资源/主目标/Shader 所有者检查、重载后 sprite bounds、命令开关与诊断。原 `LiquidBlockRenderer` 提供的高度、流动 UV、IClientFluidTypeExtensions alpha 继续生效；实际几何法线由位置导数计算。

命令：`/localmaterial surface basic`（默认）、`surface material water`（三种水 sprite）、`surface material layer`（整个透明区块层）、`surface status`、`surface probe`。`surface alien` 明确拒绝，保持当前模式。仍需用 `hold` 或 `sphere` 激活区域；`terrain false/true` 用于单独检查水面/水底。详见 [透明模式与兼容契约](transparent-surfaces.md)。

Mixin annotation processor 使用 ForgeGradle 的 official→SRG 映射，经 srgutils 转为 SRG 后生成 refmap，并在 JAR Manifest 注册 mixin config。`RenderSystem.getShader` 的名称不变，但 `VertexBuffer.draw` 会重映射，不能都写 remap=false。重混淆产物中检查方法 selector 和 refmap，真正启动才能认证注入生效。可选 hook 未命中时不得把启动成功当作材质成功。

透明 probe 只用于显式诊断：保存层前附件，记录该帧原 VBO/ChunkOffset，完成新材质后保存结果，再用原版 shader 重放同一批几何并比较 RGBA/depth，最后恢复新材质结果。不重跑世界事件或实体。VBO 引用只在这一 draw 生命周期内持有。无真实 draw 拒绝结果，不能用空 replay 证明 alpha/depth 出错。

### 原地形后端规则

完整颜色/深度 copy 避免在写入当前目标时采样其附件；颜色回写不需要清主深度。ShaderInstance、VertexFormat 和顶点输出一致。保存自己触碰的目标、纹理、viewport、写掩码等，在 finally 恢复。GPU 资源在 render thread 创建/销毁。

实际工程应扩展 capability 验证：确认附件格式、采样数、read buffer、GL errors、viewport 来源、是否处于 shadow/额外视图；带 stencil/颜色空间状态的环境要增加对应恢复。研究样例不能作为所有第三方 draw 状态组合的已认证模板。

扩展 B/C 的顺序：

1. 为地形、entity、BE、water、particle 明确可用入口和分类位，先做纯色 debug pass。
2. 如需分类重绘，重放真实可见几何、同姿态、同 alpha discard、同投影且遵守主深度；不要重绘所有 block cube 替代真实模型。
3. 注入原 shader 输出 normal/light/category 时，分别适配 BLOCK、NEW_ENTITY、particle、加速 renderer；normal 所属空间统一。
4. 水/玻璃在原透明合成前计算新材质；多层透明需要每层语义或专用分层方案，最终单深度不足。
5. 从单球命令替换为 RegionStore/FrameSnapshot，保留资源复用和清理，不为每个技能 new PostChain。

## 最低游戏验证

记录真实版本、GPU、分辨率、图形模式、renderer/pack。准备草地、台阶、建筑、cutout 叶子、普通实体、箱子、水/玻璃；对照开/关效果。转头/平移/FOV/bob/第三人称、近远边界、暂停、重载、切世界和退出；检查区域中心是世界位置而非屏幕坐标。开发期允许诊断坐标和 Mask 图，但最终要有真实材质画面和恢复后截图。

不要把本轮独立 WebGL 的分类附件当作 Forge 已有对象 ID，也不要把旧 Cosmic 物品的运行结果当作此后处理接入的证据。
