# Forge 1.20.1 接入设计

本文件是实现路线，没有声称已编译或运行。本任务只实现程序特效，不新增人物、武器或载体模型；真实宿主已有模型可用作挂点来源。Java 17、Forge 47.x；在目标项目上核对具体补丁和映射，不能套用 1.20.6 或现代 NeoForge 的签名。

## 可替换接口

建议宿主飞行系统提供以下概念接口，名称可按工程调整：

```
FlightState(entityUUID, instanceNonce, dimension, active, startServerTick, seed, style)
FlightPose(positionWorld, velocityWorld, forwardFallback, optionalActualModelAnchors)
FlightInstance: localEnvelope + historySegments + detachedPuffs + lifecycle
```

`FlightState` 是状态，`FlightPose` 来自真实实体。浏览器原型的预设 path 和虚拟发射中心不能当成这两个接口已完成。区分实体位置锚点、渲染姿态挂点和相机坐标；需要贴合肢体/装备时取真实模型矩阵，不用固定玩家偏移声称精确表面覆盖。

服务端决定技能开启/关闭、资源消耗及移动权限，客户端模拟视觉。同步激活、样式/必要参数、seed、开始 tick 和 instanceNonce；追踪范围内新客户端需状态快照，停追踪/离开世界清理。仅靠实体UUID无法区分同一玩家反复施法留下的旧实例。seed 不等于不同网络轨迹会逐像素一致。

## 事件和线程

- `TickEvent.ClientTickEvent` 在 END 阶段固定推进；暂停/空世界时不访问玩家。不要在 `RenderLevelStageEvent` 中追加历史或发射粒子。
- `RenderLevelStageEvent` 从 Forge 主事件总线在客户端发出；从事件取 PoseStack、投影、camera、partialTick、frustum。一次渲染用 partialTick 插值头部与历史末端。
- `AFTER_PARTICLES` 可作为特殊世界效果的候选阶段；`AFTER_TRANSLUCENT_BLOCKS` 的源码明确提示透明排序风险。阶段选择还要结合具体 target、Fabulous 与用户着色器管线，不能认为某个阶段万能。
- 使用 SimpleChannel 的 1.20.1 接口；网络 handler 把需要游戏状态的工作 enqueue 到主线程，物理客户端 renderer 与专用服务端隔离。不要让公共包类静态引用 `Minecraft`。
- `RegisterShadersEvent`、资源重载、窗口 resize 和退出世界均应有对应资源生命周期；时间 uniform 不作为 RenderType 缓存键。

官方依据：[RenderLevelStageEvent 1.20.1 源码](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/src/main/java/net/minecraftforge/client/event/RenderLevelStageEvent.java)、[SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/)、[Sides](https://docs.minecraftforge.net/en/1.20.1/concepts/sides/)、[Particles](https://docs.minecraftforge.net/en/1.20.1/gameeffects/particles/)。以上正文与事件文件已在2026-10-15实际阅读；分支链接可变，发布工程时记录实际提交。

## 绘制分工

| 层 | 建议起点 | 不能省略的接入 |
|---|---|---|
| 人物梭形气衣 | 世界网格＋独立 shader | 真实位姿、速度标架、局部裁剪/近眼淡出 |
| 历史亮筋/薄尾 | 客户端历史＋ribbon/tube | 弧长、断带、多实例、过期、远距裁剪 |
| 离体小烟包 | 世界中固定步模拟；需要时局部体积 | 出生惯性、深度限制、数量与屏幕覆盖预算 |
| 暗烟 | alpha/吸收路径 | 排序与实体遮挡，不能只改发光色 |
| 零星碎点 | 原生粒子或自批次 | 客户端 factory/atlas注册与发射预算 |
| Bloom | 分离发光缓冲 | 深度/target正确、resize、状态恢复 |

普通中心线尾迹无需 compute/SSBO。若选择体积或缓存，先查实际OpenGL能力，提供可运行的低阶路径；不能从Minecraft版本推断OpenGL 4.3。

世界顶点先保留双精度，仅在提交顶点前减一次 cameraPosition；PoseStack 若已经减过，则不重复。实际目标渲染器的矩阵含义须通过固定世界点检查。每层 draw 绑定自己的参数并明确 flush，避免共用 BufferSource 时下一个实例覆盖前一个的uniform。

渲染前后恢复 depth mask/test、blend、cull、shader、framebuffer、viewport、active texture和必要纹理绑定。采样深度/场景颜色时取得当前阶段正确副本；Fabulous粒子target与主target不总是同一个。没有兼容路径时应报告该配置不支持，不能把错误画面称通过。

## 参数与消费者

网页导出的 `dunguang-study/1` 是浏览器研究参数。要进游戏，先定义真实schema和单位，再实现Java parser、shader/几何消费者、默认值和无效输入处理；建立字段→消费者→可见变化的表。不要直接重命名成 `recipe.json` 就声称现有能量/尾迹MOD已支持。

最小游戏里程碑：真实玩家开启/停止 → 不同视角的局部气衣 → 世界历史尾迹 → 急转/停飞/瞬移 → 两个实体并存 → 深度遮挡 → 近眼 → 可选体积与Bloom。每阶段记录真实构建和截图，不能继承其他Skill的认证。
