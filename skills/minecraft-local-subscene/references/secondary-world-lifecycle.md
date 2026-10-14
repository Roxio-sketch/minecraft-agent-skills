# 真实二次世界、上下文隔离与生命周期

## 先验证同世界第二视角，再增加第二 ClientLevel

只需要反射/远端摄像头时可复用当前 ClientLevel 数据，但 renderer 仍不是天然可重入。Veil 的 perspective guard 有意跳过再次进入；这是避免循环的策略，不是通用递归能力。

真实另一维度至少需要：

- dimension key/type 和 registry access，匹配的 level data、biome/seed 相关数据。
- 独立 ClientLevel、LevelRenderer、所需 DimensionRenderHelper/LightTexture 及可见性缓存。
- chunk load/unload 和区块更新，实体 spawn/move/remove，block entity 更新，维度 time/weather。
- 服务端知道哪些远端 chunks/实体需要发送，订阅与退订遵守距离、权限和带宽预算。
- 客户端把包发送到正确维度的对象，并在客户端线程按正确顺序处理。

IP 的证据链：`ClientWorldLoader → PlayerChunkLoading / ImmPtlChunkTracking → PacketRedirectionClient → EntitySync / WorldInfoSender → PortalRenderer / MyGameRenderer`。new ClientLevel 没有自动下载世界的能力。另一服务器需要独立连接、协议与权限，不能用第二 ClientLevel 代替。

本 Skill 不要求默认移植 IP 的交互、传送、碰撞体系。只读可视化的最低实现应保持玩家实际世界、服务端身份和游戏逻辑不变。

## 视图事务需要隔离什么

使用 viewId 和递归路径标识每次绘制。至少检查所选调用链会读写以下对象；列表是审计起点，具体模组环境可能更多：

| 状态 | 隔离原因 |
|---|---|
| Minecraft.level / levelRenderer / mainRenderTarget 引用 | 大量原版或 MOD 代码通过全局对象访问当前世界/目标 |
| GameRenderer 的 Camera、projection、render distance、light texture | FOV/光照/视点不能污染父层 |
| EntityRenderDispatcher / BlockEntityRenderDispatcher 的 level/camera/pick | 必须看到子世界对象；返回后还原父世界 |
| RenderBuffers / BufferSource / 固定 RenderType buffers | 递归重入未结束的 BufferBuilder 可出现 Already Building；子层 endBatch 不能乱结束父层 |
| LevelRenderer frustum / chunk visibility / terrain renderer context | terrain 缓存与当前 camera/world 强关联 |
| ParticleEngine world / fog context / world sky state | 子世界粒子、天空、雾不能使用父世界上下文 |
| hitResult / crosshair entity / outline / hand flags | 子视图不应重算并污染主交互，更不应多画手/UI |
| profiler / logical view stack / mirror parity / clip planes | 嵌套与异常要成对，主帧前后钩子不能每个子视图重复执行 |
| renderer-specific shader pipeline、temporal buffers、shadow state | 第三方 renderer 常用隐含全局状态 |

`WorldRenderInfo` 式数据对象传递 world、eye、矩阵、距离、sky/fog/hand 配置；不让任意函数临时改全局字段而没有明确 owner。每个世界的数据更新与每帧多次绘制分开：一个世界被多个窗口看见不代表 tick 多次。

## 包处理与绘制切换不是同一个 scope

渲染只临时提供目标世界上下文，不应随意改 `player.level()` 或网络 respawn 状态。维度重定向的数据包需要单独 scope：选择目标 ClientLevel，临时提供 handler 所需 world，执行包，finally 恢复；IP `withSwitchedWorld` 的 finally 值得保留。

不重定向登录/断线/respawn 等主会话生命周期包为普通远端数据包。确保 chunk 先到、依赖它的实体/方块实体后处理；任务在世界释放后不得继续写入。服务端和客户端订阅带 generation，防止相同 dimension key 重新加载后旧包污染新实例。

## 异常安全状态恢复

不要只在 `renderLevel` 外加 finally，却把之前的 camera/FBO/尺寸修改留在 try 外。也不要从参考源码“末尾恢复几行”推导出异常安全。

```java
// 架构伪代码：capture 只读取，不改变状态。
try (RenderScope scope = RenderScope.captureIncomingState()) {
    scope.enter(view);   // 每次成功修改立即记录相应 undo；enter 部分失败也由 close 恢复
    source.render(view);
    scope.finishOwnedBuffers();
}
```

scope 真正按 LIFO 逆序恢复；每个 undo 独立执行，收集 suppressed exceptions，避免第一个恢复失败导致剩余步骤永远不执行。恢复完成后才允许禁用本次效果/回退；不能简单吞掉错误继续用污染状态画主世界。调试模式记录失败 viewId、资源代次和状态差异，避免逐帧刷屏。

捕获实际修改的 GL 状态和 Minecraft 的缓存状态，二者必须一致：

- read/draw FBO、read/draw buffers、viewport、scissor 状态/矩形。
- depth enable/function/write mask/range/clear value；stencil enable/front/back func/ref/read-write masks/ops/clear value。
- color mask（若使用 MRT，逐 attachment）、blend enable/function/equation/color、cull mode/front face、polygon offset、depth clamp、framebuffer sRGB。
- current program、VAO/buffers、active texture unit，以及实际修改的纹理/采样器绑定。
- RenderSystem 的 shader、shader color、fog、lights、projection/modelview/texture matrices；不要只恢复底层 GL 而让 GlStateManager 缓存误以为状态没变。

这不是要求生产代码每帧 glGet 全部 GL 状态；优先使用管线显式 state tracker 和封装边界，审计模式核对重点。任何“全状态恢复通过”声明都应给出实际检查集合。WebGL 探针只检查声明的有限集合。

## 资源与世界生命周期

| 事件 | 动作 |
|---|---|
| 首次创建 | render thread 创建 owned attachments/program；检查完整性；不修改已绘制的主附件 |
| resize / 全屏切换 / GUI 尺寸变化 | 以实际 render target 尺寸计算 viewport；重建依赖尺寸的资源；旧消费者结束后释放旧对象 |
| F3+T / shader reload | 增加 resourceGeneration，重建 uniform handles、程序和依赖布局；事务式替换，失败时采用明确 fallback |
| 死亡/重生/玩家实体替换 | 失效旧 player/camera 引用；依据领域策略停止或重新绑定；不要固定保存旧实体 |
| 换维度/换存档 | worldGeneration 改变，缓存和远端请求重新判定；只释放自己拥有的 secondary 对象 |
| 退出世界/断线 | 退订远端数据、取消 pending tasks、清空视图栈和效果、关闭 targets/renderers/light textures；在可用 GL 上下文线程释放 |
| 模组停用或渲染失败 | 本帧恢复调用者，隔离失败 source；提供原世界/边框/明确占位等选择，避免伪装旧缓存为实时世界 |

`generation = (session, world, resources, scene)` 随任务保存，回调执行前比较。只取消 Future 不保证队列中任务不会运行；执行时仍要检查有效性。

资源分 `owned / borrowed / pooled`：borrowed 主 FBO 不 destroy；池资源的使用结束不等于永久 free；缓存纹理过期要释放。Veil 的 manual framebuffer 注册/移除不等于资源自动销毁；记录唯一 owner，避免泄漏和 double-free。

## 递归场景

把子窗口展开成 view tree，跨多个入口的 transform 连乘，clip/frustum 和预算逐层更新。每层有独立 context、可用附件和 source identity；不能用一个 static backup 覆盖所有祖先。

递归路径含 portal/view 标识及映射状态，用于识别直接逆门和重复视图。镜面奇偶影响剔除与相机；不能只凭 world key 相同就认定循环，因为同世界不同入口可以产生有效递归。

深度上限与全局 view 数共同约束：分支因子会让很浅的树也产生大量世界绘制。叶子可用上一帧稳定图、简化场景或可辨识的终止图案。缓存必须注明时效，不把无限递归的最后一层粗暴 clear 成随机显存。
