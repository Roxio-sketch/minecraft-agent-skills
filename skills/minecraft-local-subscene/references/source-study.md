# 固定源码快照的横向研究

用户提供四份归档。以 [source-evidence.json](source-evidence.json) 的归档/文件 SHA256 锁定样本，以 [source-index.md](source-index.md) 的文件路径和行号复查。下面区分**源码事实**、**设计提炼**和**验证范围**；不是对最新上游版本的评价。

## 版本与元数据

| 项目 | 快照信息 | 使用方式 |
|---|---|---|
| Immersive Portals | 6.0.6，Minecraft 1.21.1，Fabric loader 0.16.2；Apache-2.0 文本 | 第一核心：多世界调度、区域与嵌套渲染 |
| Veil | 4.5.1，Minecraft 1.21.1，Java 21，Fabric/NeoForge；LGPLv3 文本 | FBO、视角、postprocess 架构 |
| Veil Example | Minecraft 1.21.1 Fabric，依赖 Veil 4.2.1；CC0 文本 | 镜面使用实例，不假定与所给 4.5.1 已编译匹配 |
| Lodestone | 1.8.3，NeoForge 21.1.228，Java 21 | 几何 VFX 和 emission/bloom |

Lodestone 快照中目标版本与 `[1.21,1.21.1)` 声明、LICENSE.txt 的 LGPLv3 与 gradle 的 GPLv3 字段存在不一致，研究时记录事实，不自行裁定。此 Skill 的探针是原创；不分发参考项目的大段实现，不以笔记代替复用源码时所需的许可核查。

## Immersive Portals 主调用链

```text
主帧 hooks → prepareRendering
    → PortalRenderer 收集/筛选入口
    → RendererUsingStencil 或 RendererUsingFrameBuffer
    → renderPortalContent
    → WorldRenderInfo / PortalRendering 栈
    → ClientWorldLoader.getWorld(destination)
    → MyGameRenderer 切完整上下文并 renderLevel
    → 恢复父视图、入口深度、stencil/target
主帧 hooks → finishRendering
```

| 证据 ID | 源码事实 | 提炼到 Skill 的约束 |
|---|---|---|
| IP-STENCIL | 门面以 EQUAL 父层、depth pass 时 INCR；受 stencil 限制的绘制清深度；退出后重画门面，再 clamp 层值 | pixel ownership、子深度独立、父入口深度恢复必须成套设计 |
| IP-FBO / IP-TARGET | secondary TextureTarget 渲染，再重画 portal area 采颜色；此 backend 显式 single-layer guard | 不把该实现限制误当 FBO 理论限制；递归自行管理多目标生命周期 |
| IP-COMPOSE / IP-AREA | portal triangles 决定实际区域；合成写父门面深度 | mask、可见性、深度与颜色职责分离 |
| IP-RENDER / IP-STACK | 距离、frustum、层数/数量、逆门判断、层级 camera transform | 先预算后渲染；camera mapping 沿路径累计 |
| IP-CONTEXT / IP-WORLD-DRAW | view 信息含 world、camera、矩阵、距离、sky/fog/hand；切 renderer/light/particles/buffers 等 | 创建第二 Camera 不够；强制清晰的 context owner |
| IP-CAMERA / IP-CLIP | portal transformPoint 与方向变换分开；clipping plane 逆转置 | 源/目标空间、镜像、scale 和 camera-relative 精度显式化 |
| IP-WORLDS / IP-PACKETS | 多 ClientLevel/renderer/helper 映射；包处理切世界；withSwitchedWorld finally；动态 dispose/cleanup | 数据同步 scope 与绘制 scope 分开；世界实例有代次与寿命 |
| IP-CHUNK-LOAD / IP-CHUNKS / IP-ENTITIES / IP-WORLD-INFO | 远端 chunk loading/tracking、实体同步和时间天气 | 真世界不仅是渲染调用，还涉及服务端持续供给数据 |
| IP-SHAPE / IP-FLAT-MESH / IP-BOX | Mesh2D 定义特殊平面；shape 分离渲染、raytrace、碰撞；Box clipping 返回 null | 不规则平面可共享平面 clip；非平面需不同裁剪方案 |
| IP-IRIS / IP-IRIS-COMPAT / IP-SODIUM | Iris 专用多层/兼容单层路径；Sodium context 调整 | 第三方 pipeline 需要 adapter 和实测，不靠同名 FBO API |
| IP-HOOKS | 主帧准备/收尾、resize 与嵌套绘制有区别 | 子视图不能重复主帧生命周期操作 |

### 不照搬的恢复路径

IP `RendererUsingFrameBuffer.doRenderPortal` 与 `MyGameRenderer.renderWorldNew`/内部世界绘制存在顺序 push/swap/render/restore，没有完整 try/finally 覆盖所有 mutation。本轮未注入该项目游戏运行异常；这是静态结构事实。自己的实现从第一次状态修改起使用异常安全 LIFO transaction。

`SecondaryFrameBuffer` 主要解决创建/resize，不单独证明完整 dispose 生命周期。完整 owner 要延伸到世界退出、资源重载、失败清理，而不是只抄该类。

## Veil 与 Example

| 证据 ID | 学到的内容 | 边界 |
|---|---|---|
| VEIL-VIEW | 独立 camera、projection、frustum、尺寸、fog/light、Iris/Sodium 状态备份 | 有 reentry guard；主要 render try/finally 之外已有 mutation；不是自动多世界/递归引擎 |
| VEIL-FBO / VEIL-MANAGER | advanced attachment 组织、managed 与 manual 目标管理 | remove manual target 不等于 free；生命周期由 owner 明确承担 |
| VEIL-PIPELINE / VEIL-POST | ordered stages、尺寸相关资源更新、后处理调度 | catch/日志不等于所有异常路径都恢复 GL；合成前后各自状态需审计 |
| VEIL-STACK | 保存 framebuffer 和 viewport | 此快照 add 配 removeFirst 呈 FIFO，嵌套不满足 LIFO；主 FBO 分支不保证任意 viewport 恢复 |
| EXAMPLE-MIRROR / EXAMPLE-STAGE | 反射 camera position/look/up、oblique clip、按平面缓存、额外视图排除自身；AFTER_LEVEL 更新注册 | 这是反射同世界的例子；不能推出所有 portal 的最佳阶段 |

Veil FramebufferStack 的结论是此归档的静态风险，不宣称已在游戏里复现故障、也不声称最新版本仍如此。学习其“显式保存目标”的意图，自身实现采用真正 LIFO 并验证两层以上。

## Lodestone 的辅助位置

`LOD-VFX` 用 builder 组织 sphere/quad/trail/beam；`LOD-TRAIL` 管理轨迹点；`LOD-BLOOM` 配置 blur passes，`LOD-BLOOM-TARGETS` 负责 half/quarter/eighth 尺寸与独立 emission/depth 目标。它们服务于 shell、ribbon、rim、emission；不决定另一个世界的可见区域，也不取代 PortalRenderer。

Bloom 应有独立 emission 数据与遮挡策略。把主世界整张画面 blur 再叠回不是“异空间边界”；Veil/兼容实现的 shader-active 排除策略也不能替代目标 pack 的集成验证。

## 本轮研究范围

已沿相关渲染/世界/数据/兼容依赖链阅读，索引锁定 36 个核心源码文件；并查目标 Forge 的 RenderTarget patch、阶段事件和客户端生命周期 API。无关玩法、配方、全部传送交互不作为本 Skill 的实现要求。

当前证据层次见 [validation.md](validation.md)。源码架构参考、WebGL 模型成功、Java 数学成功、Forge 签名编译、Minecraft 实机成功是五件不同的事。
