# 预算、降级与第三方渲染管线

## 多重预算

不要只限制递归层数。一个 view 是否值得绘制，先看区域 bounds/frustum、朝向、投影面积、可见距离，再考虑遮挡；通过后才分配昂贵目标/执行世界绘制。

预算至少覆盖：

- 最大递归层级、每主帧的总 view 数、同时更新的窗口数。
- 本帧总子视图像素数、单 view 分辨率、FBO 池总字节数。
- CPU world render 时间、GPU draw/shader 时间；远端世界的 chunk/实体/tick 成本。
- 服务端订阅 chunks、每 tick 发送量、客户端 pending decode/upload 队列。

可以从轻量原型“深度 1、最多 2 个更新视图、0.5 线性分辨率比例”开始调参；这只是保守起点，不是硬规则，也不是实测性能承诺。真实世界的预算单独测，屏占很小也可能触发大量 CPU chunk traversal。

```text
if invisible or stale generation: do not update
if depth >= maxDepth or totalViews >= maxViews: use declared leaf fallback
if estimated pixels/memory/time cannot fit remaining budget: reduce quality or defer
reserve global budget before descending (siblings share the same remaining budget)
render; record actual cost; update rolling estimate
```

## 分辨率、显存与缓存

linearScale=0.5 约为全分辨率四分之一像素，0.25 约为十六分之一。保留相同的相机/投影语义；不要缩 viewport 同时偷偷改变 FOV。根据窗口投影面积估分辨率，设置最小尺寸、最大尺寸与滞回，避免每帧 resize 抖动。

显存按 `width × height × bytesPerPixel × samples × simultaneouslyLiveTargets` 加上 ping-pong/history/mipmap/resolve 计算。例如 RGBA16F(8 B)+D24S8(4 B) 的单采样 1920×1080 目标约 23.7 MiB，尚未包含 bloom 和递归副本。实际 depth 格式或对齐不同需重新估算。

缓存 key 至少包含 source/world generation、scene version、camera/portal transform、projection/FOV、viewport mapping、shader generation、clip policy 和时间采样。静态场景也会随眼位移动产生视差，不能仅依据“场景对象没动”复用旧画面。

当内容不可见时停止渲染；远端订阅可采用短暂保留期避免走到门边时重复加载，但设内存上限和退订超时。复用旧帧设最大 stale age；快速 camera 运动、入口 crossing 或世界切换时立即失效。

## LOD 和降级顺序

优先降低视觉成本而保持空间语义：

1. 剔除真正不可见的 view，合并能共享的同源静态内容。
2. 降低子目标分辨率、raymarch steps/noise octave、边界粒子与 bloom 级数。
3. 缩短子场景 far/render distance，降低低优先级窗口更新频率。
4. 减少递归深度、同时更新数；对叶子使用明确的缓存/简化场景。
5. 超出能力时仅保留边框或清楚说明的替代画面，禁用不兼容 source/backend。

用户要求实时第二世界时，程序星空可作为显式 fallback，但不能宣称仍是实时世界。要保留有限体积语义时，降级也不能悄悄变成无限窗口。

occlusion query 尽量异步使用上一帧结果，加 conservative visible 与 hysteresis；同步读回会阻塞 CPU/GPU。诊断用 glReadPixels 和 glGetError 不留在每帧生产路径。测量时区分冷启动、shader 编译、chunk upload 和稳定帧时间，报告 P50/P95 或超预算帧，不只报单张 FPS。

## Embeddium / Oculus / Shader Pack

IP 对 Sodium 有 terrain render context 切换，对 Iris 有专门 portal backend、层级 deferred FBO/depth-stencil 流程和兼容降级。它们说明“需要专门适配”，不能证明 Forge Embeddium/Oculus 直接可用。

| 组合 | 重点核查 | 本研究状态 |
|---|---|---|
| Forge 原版 renderer | 事件阶段、主 target、depth、GL 缓存 | Ink Realm 0.2.0 单门/内部视觉领域已实测；未分别认证所有 Fast/Fancy 配置 |
| Fabulous | 透明/粒子/天气多目标与合成顺序 | 未实测 |
| Embeddium | chunk renderer context、可见性缓存、reentry、上传队列 | 未实测，不推断等同 Sodium |
| Oculus，无 shader pack | 注入点、目标生命周期、默认 fallback 路径 | 未实测 |
| Oculus + 指定 shader pack | HDR/gbuffer、depth 约定、TAA history/jitter、shadow pass、camera/world uniforms、hand 阶段 | 必须逐 pack/version 实测 |
| 其他需要 stencil 的 MOD | stencil 位所有权、clear时机、多层复用 | 无协调则优先 owned target/mask |

检测 MOD 存在只决定选择哪个 adapter；FBO completeness 成功也不代表 shader pack 的深度语义兼容。输入 capability 明确：是否可读主 depth、是否独占 stencil、是否有稳定二次 camera hook、能否控制透明层、是否支持 remote world。

随附 Ink Realm 示例检测到 Oculus、Embeddium 或 Rubidium 会直接禁用；也要求当前 draw target 为主 target。这是已声明的能力限制，不能为了让画面出现就去掉 guard 并宣称兼容。320/768 两档和近入口放大已取证，但没有正式帧时间/显存性能认证；同步 readback 仅用于诊断，示例每次绘制的状态查询也需要在生产工程中评估成本。

TAA 每个 view 的历史不能共享成一个静态目标；camera 瞬变、世界切换、projection 变化要失效。子视图 jitter 与最终采样坐标同步，不能在裁剪后的低分辨率纹理上照搬主屏幕像素偏移。

避免在 shader pack shadow pass 触发普通窗口递归；子世界天空、雾、阴影应使用子世界数据。后端不支持某 pass 时明确略过该 pass 或停用功能，别通过反复替换主 FBO 赌兼容。
