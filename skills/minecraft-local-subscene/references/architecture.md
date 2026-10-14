# 架构与需求路由

## 进入能力合同

空间外形与进入能力是独立决定；圆形/月洞门不自动可进入，球形也不自动有另一套碰撞。

| 用户想完成的动作 | 交付能力 | 首次验收 |
|---|---|---|
| 从外面观看另一套场景 | 观看窗口，可明确为单面或双面 | 遮挡、平移视差、转头、近入口稳定；说明不能进入 |
| 走入、被领域包围、技能期间身处另一片空间 | 可进入的轻量视觉领域 | 外→内→转头/移动→原路返回或明确退出；主世界碰撞/玩法仍然存在 |
| 踩在子世界地形上，与子世界方块/生物交互 | 独立玩法空间 | 渲染之外需服务端世界/实体/碰撞/玩家迁移方案；不能用全屏 shader 交付 |

第二 ClientLevel 的观看也不等于传送玩家；Camera 隔离和玩家所属世界分别管理。用户没有要求独立玩法时，不为了“可进入”就创建完整第二世界；用户明确要求行走在山峰上时，也不以视觉进入代替。

优先利用上下文决定合同，仅在缺失决定会实质改变范围且没有合理默认时澄清。把合同写入场景方案，并让测试覆盖它。实测模板及边界见 [Ink Realm](runtime-inkrealm.md)。

## 三种空间语义

| 语义 | 沿一条视线看到什么 | 必须决定的行为 |
|---|---|---|
| 入口窗口 | 入口之前是主场景；通过入口后看另一套可无限延伸的空间 | 两侧是否可见；从背面/内部看什么；入口后主场景是否一律隐藏 |
| 有限体积替换 | 进入区域前看主空间；区间内看第二场景；出区域后又回到主空间 | 区域内主世界几何被删除、保留或映射；子场景也要在区间终点裁剪 |
| 临时独立领域 | 相机在领域内时用独立场景替代部分或全部主视野 | 外部观察者看到什么；手、UI、队友是否保留；进出过渡与回到主世界 |

“球形领域”未指定有限体积时，可默认外部球壳入口、内部全视野进入子场景，写明这个假设。明确要求球后仍看到主世界时，就必须采用有限体积路线。不要把第二场景投到球面再称为完整体积替换。

窗口只替换可见像素，不代表传送、碰撞、攻击判定、声音或服务端维度变化。需要这些行为时单独定义网络和玩法语义。

## 场景来源与后端正交

| 来源 | 适用情形 | 不具备的能力 |
|---|---|---|
| ProceduralScene | 星空、银河、抽象时间河流、视线积分云海、黑洞内部背景 | 没有真实远端 chunk 和实体 |
| MeshScene | 独立岛屿、几何遗迹、碎片、轨道、可控微缩舞台 | 不自动获得 Minecraft block/entity 渲染与玩法 |
| CurrentLevelView | 同世界另一个观察点、镜面、摄像头 | 仍需 reentry 隔离；远处内容受当前客户端加载数据约束 |
| RemoteLevelView | 显示服务端实际另一维度的地形、实体、天空和天气 | 需要订阅/同步/生命周期；不是凭空打开另一服务器 |

| 后端 | 优点 | 代价和风险 | 优先情形 |
|---|---|---|---|
| owned FBO → 几何/mask 合成 | 容易独立分辨率、缓存、后处理和资源所有权；可局部失败 | 额外带宽/显存；采样边缘；主子深度映射；透明和历史数据仍复杂 | 首个轻量场景；多分辨率；后处理；有稳定适配的二次世界 |
| Stencil 直接绘入当前目标 | 精确限制子场景；天然表达父子像素层级；避免最终颜色采样 | 共用深度需局部清理与恢复；全管线重入；主 FBO stencil 所有权和兼容难 | 已掌控目标与渲染阶段、需要嵌套的受控管线 |
| FBO + stencil/mask | 离屏内容与区域归属分离；可用嵌套纹理和独立 stencil | 两套资源/状态管理；不能省略深度契约 | 需要降分辨率的真实场景、复杂窗口合成 |

Stencil 并不必需：单个平面窗口直接以 aperture mesh 绘 FBO 即可裁掉区域外像素。SDF discard 或独立 R8 mask 也能限制合成。硬区域 mask 与边缘光效是两套信息。

## 最小可组合模块

```text
Request → RegionShape + RegionSemantics + ViewMapping + SceneSource
        → VisibilityBudget → RenderContext → Compositor → BoundaryVFX
```

下列是协议设计，不是已经存在的 Minecraft API：

```text
RegionShape: bounds, surfaceMesh, contains, rayIntervals, planarClip?
ViewMapping: sourceToDestination, cameraPose, projection, mirrorParity
SceneSource: render(view, ownedTarget), dependencies, generation, timePolicy
ViewSpec: viewId, worldKey?, eye, basis, projection, viewport, clip, recursionPath
SceneImage: color, depth?, colorSpace, depthEncoding, inverseViewProjection, generation
Compositor: coverage → parentVisibility → childColor → parentDepthPolicy
BoundaryVFX: shell/ribbon/emission with independent blend and occlusion policy
ScopedRenderContext: acquire → snapshot → mutate → draw → restore → release
```

RegionShape 的渲染网格、raytrace、可见性包围盒和交互体积使用同一局部坐标规范。动画裂缝不要每条链路独立随机，使用共同 seed/time/version，避免看到裂口却点不中。

`SceneImage` 不能只有 color texture id：资源代次、viewport、depth 类型和颜色空间决定它能否被正确合成。扩展到 HDR 时显式标记 linear/sRGB、tone-map 责任，避免双重 gamma、重复 bloom 或错误 alpha 预乘。

## 可自行设计的配方

以下是路线示例，不是固定模板或通用性能承诺。

| 需求 | 默认组合 | 何时升级 |
|---|---|---|
| 球形星空领域 | sphere ray interval/入口 + procedural star volume + FBO + Fresnel 边界 | 球后需回到主世界 → 有限体积；内部有实体/地形 → Mesh 或 RemoteLevel |
| 剑劈裂缝里看到另一个世界 | 沿剑路生成带孔/分叉的平面 ribbon aperture + 映射 camera + RemoteLevel FBO | 只需抽象宇宙 → Procedural，省掉多世界同步 |
| 通往时间长河的窗口 | 不规则 aperture + Mesh 河道/漂浮片段 + 程序雾 + 独立时间 | 需要真正另一维度的河流生物时才 RemoteLevel |
| 终结技临时领域 | 场景控制器记录进入状态 + Mesh/Procedural + 内外过渡 mask | 有真实服务端维度玩法时另建同步与玩家迁移，不靠渲染强改 player.level |
| 黑洞中心独立空间 | 中心入口 mask + 子场景；偏折和吸积盘是外层场 | 需要引力多像/跨入口深度时另做射线映射；distortion 主颜色不是二次场景 |

设计至少包含：世界单位尺寸、父/子坐标映射、相机内外、场景时间、区域形状、可见对象、入口深度、边界颜色/宽度和预算。支持任意原创设计意味着这些模块可替换，而不是承诺任意效果自动兼容所有管线。
