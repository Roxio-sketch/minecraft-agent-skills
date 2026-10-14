# 多区域、生命周期、预算与兼容

## Region 逻辑模型

建议记录 `id, revision, dimension, ownerUUID?, binding, anchorDouble, orientation, shape, bounds, startTick, durations, materialId, stableSeed, targetPolicy, priority, overlapMode, deathPolicy`。路径额外记录有界点列/段时间。用资源 ID 查 MaterialDefinition，不将每种主题硬编码为一套独立后处理管理器。

客户端从逻辑状态构建不可变 FrameSnapshot：按维度筛选、插值绑定点、计算 age、世界 bounds 剔除、double 原点相减、排序与上传。帧内 draw 只引用该 snapshot；不得后台线程直接修改 GL 资源或共享 uniform。

两类时间分开：游戏区域生命周期用权威 tick+partialTick；装饰素材动画用所选暂停策略。不要按每次 render 加 partialTick/20，这不是每帧真实 elapsed time。世界时间可能跳变，使用专门服务器 tick offset 或明确的本地开始时钟；区块重载不应无意重播技能。

默认 overlap 可选高优先级覆盖、稳定顺序 alpha 混合或同材质 union。相同 priority 用稳定 id 排序，不能依赖 HashMap 遍历顺序。不同材质的 SDF soft union 不自动定义颜色归属。累积 emission 设置曝光/能量上限，避免重叠数量越多越白。

网络只同步区域参数与 create/update/remove 和 server timestamp；客户端不按像素向服务器查询。旧 revision、错维度、已删除实例更新应丢弃。玩家晚加入时下发有效实例快照与剩余时间。持久化需求必须显式，否则以临时视觉处理。

## 清理矩阵

| 事件 | 逻辑与 GPU 行为 |
|---|---|
| 生命周期结束 | 移除 Region，队列不再引用；共享纹理/FBO 用引用计数或管理器保留复用 |
| owner 死亡/消失 | 按 cancel/freeze/continue 策略；不能所有命中点技能都无条件消失 |
| 切换维度/退出世界 | 清当前维度帧快照/路径/历史缓冲，断开实体引用；不复用旧深度 |
| 资源重载 | 新 generation 的 shader、uniform、材质资源就绪后原子替换；失败禁用该效果/保留合法旧资源，不能用已释放句柄 |
| resize/FBO 重建 | 新尺寸最少 1×1，附件格式/采样数匹配；销毁自有旧附件，不能销毁借用主深度 |
| death screen/pause | 按时钟策略继续或冻结，仍正确渲染背景；不是每帧重启技能 |
| draw 异常 | finally 恢复触碰状态，防重入标记复位；本帧回退原颜色，记录一次诊断并限制重试 |
| 退出客户端 | render thread 释放自有资源；不把清理动作排给已不存在的 GL context |

Renderer 必须明确 framebuffer 读/写绑定、viewport、scissor、depth test/write/function、blend、color mask、cull、shader、texture unit、矩阵与 stencil 所有权。按实际触碰的状态保存恢复；强制设置“默认状态”不是恢复原调用者。底层 GL 与 RenderSystem/GlStateManager 缓存保持一致。

## 成本模型与初始预算

开销约为 `覆盖像素 × 每像素候选区域 × 材质采样量 + 场景拷贝/额外附件带宽 + 分类/几何重放`。多区域不等于每个区域复制全屏颜色深度一次。

建议初版预算是可调整的测试起点，而非硬件保证：每帧最多 8 个活跃可见区域、每 tile 最多 4 个候选、路径简化后最多 32 段、先 1 次 scene capture 和 1 次 material composite；昂贵 bloom 可半/四分辨率。超过预算时按优先级/屏幕贡献淘汰或合并，不任意时变排序造成闪烁。

方案选择：

- 球/Box 粗剔除用世界 bounds 和视锥；投影 bounds 用 scissor/tiles，跨 near-plane 或相机在体内时保守处理。
- 先简单距离和类别筛选，再运行 Cosmic/噪声；多个相同材质区域可先 union coverage 后只评估一次材质。
- 长路径用空间网格/BVH/分段 bounds；CPU 每帧每像素结构不可行。
- shader feature 分组减少无关采样；normal/类别等仅在需要时启用，启用帧尚无有效数据时跳过并等待。
- 基础 Mask 保持清晰，降低材质噪声频率/层数、远景星点频率与 bloom 分辨率；低分辨率区域 Mask 需 depth-aware upsample 防止穿轮廓。
- 保持原世界位置重建的数值精度。不能通过加大羽化掩盖矩阵不匹配。

显存按实际附件计算。例如 1080p 的 RGBA8 颜色副本和 4 字节深度副本约 15.8 MiB；分类、normal、HDR emission 和 ping-pong 另计，4K 面积是四倍。GPU 独立测耗时，读 query 用延迟结果以免每帧同步卡顿；记录 CPU、GPU、拷贝、draw 和 region 数，不从层数猜 FPS。

## Embeddium / Oculus / shader pack

| 环境 | 主要风险 | 合理策略 |
|---|---|---|
| 原版 Forge 管线 | 阶段矩阵、Fabulous 多目标、其他 mod 插入 draw | 核对实际 FBO/事件/深度，先基线实测 |
| Embeddium | chunk renderer/顶点格式/shader 绑定改变；Forge 事件路径未必完全等价 | 独立版本适配和阶段探针；不能因未崩溃就认证 |
| Oculus，无活动 pack | 可能仍有管线/mixin 变化 | 单独测试；“pack 关闭”不是未安装 |
| Oculus + pack | gbuffer、jitter、shadow pass、透明 resolve、HDR/曝光/雾阶段变化 | 只有明确 adapter/pack 接入点才启用；明确 sky/depth/normal 来源 |
| 自定义 shader 替换/后处理 mod | 附件别名、执行顺序、统一状态污染 | capability negotiation；不抢占未知输出目标 |

检测到 mod 只是决定选哪个 backend 的条件，不是兼容证据。提供 `supported / unavailable / disabled-by-policy` 状态与原因；缺 category 时不要静默扩大目标对象。随附 Forge 样例对 Oculus/Embeddium/Rubidium 采取直接禁用，这是原理样例的保守边界，不是最终产品的兼容方案。
