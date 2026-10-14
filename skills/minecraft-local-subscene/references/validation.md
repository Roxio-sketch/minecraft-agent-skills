# 验收方法与研究基线

## 本次已执行的验证

日期 2026-09-29。原始结果随 Skill 保存，按 artifact scope 解读。

| 类别 | 结果 | 能证明什么 | 不能证明什么 |
|---|---|---|---|
| 原创 WebGL2 GPU | **29/29 通过**；Edge 154.0.4258.37、ANGLE D3D11、RTX 4060 Ti、stencil 8 bit、384×256 | 合成模型的 mask/depth/嵌套/球体入口与有限状态 scope | Minecraft OpenGL 集成、shader pack、全 GL 状态安全 |
| Java 数学与逻辑 | **20/20 通过** | 球射线、near 区间、projection/depth、camera/plane、镜像、异常栈与总预算契约 | 引擎全局状态恢复或实际远端数据同步 |
| Forge API | **compileJava 成功**；Forge 47.4.10 / Java 17 | 对目标版本的部分调用签名与类型成立 | 可安装 MOD、事件实际触发、画面成功 |
| 本方向 Minecraft runtime | **Ink Realm 0.2.0，16 个捕获案例** | Forge 47.4.10 原版管线中单平面窗口、视觉进入/返回、重载与单次异常恢复；见 [原始证据](runtime-inkrealm.md) | 不是第二 ClientLevel，不涵盖全部图形设置、手/UI、球体、递归或真实碰撞 |
| 真第二 ClientLevel / Embeddium / Oculus | **未认证** | 源码与路线研究 | 不能宣称跨世界渲染和全兼容已完成 |

原始记录：[GPU JSON](evidence/gpu-validation.json)、[Forge 日志](evidence/forge-probe.log)、[GPU 截图](evidence/pipeline.png)。截图为原创合成实验，不是 Minecraft。

安装后又从 Skill 自带文件运行 GPU，并复制其中的 Forge 探针到全新目录构建；同样获得 29 项 GPU 与 20 项数学通过、Forge compileJava 成功。记录：[安装包 GPU 复跑](evidence/gpu-reproduction.json)、[全新工程构建日志](evidence/forge-reproduction.log)。这验证资源可独立复用，没有提升为 Minecraft 实机认证。

GPU 检查包括四种 aperture（圆、平面、裂隙、带孔不规则），区域外像素零变化、前景保留、独立内容可见，FBO/stencil 对照、两层嵌套及父层继续、后绘制前后景、缩放目标释放、ray-sphere 在 FOV 40/70/110、内部领域全替换、shader 重建、声明的 FBO/viewport/blend/depth-function 异常恢复，以及 GL error=0。

其中两种后端逐像素相同只针对这组不透明 synthetic scene，不推广到任意 shader、透明、MSAA、GPU 或世界 renderer。sphere-inside 检查用内部主视野全替换策略，没有模拟手/UI。半/四分辨率检查主要验证目标生命周期，不是降采样视觉或 FPS 认证。

## 新实现分层验收

先按进入能力合同选用下列检查；通过本层后再投入艺术润色。测试集匹配实现范围，不强迫单窗口任务实现所有形状/递归。有进入要求时，不能在只完成窗口/视差后交付。

### 最早执行的行为门槛

| 阶段 | 必须观察到 | 失败优先定位 |
|---|---|---|
| 构建/加载 | 正确 JAR、Shader 代次、实际 scene 输出 | mod ID/部署、注册、uniform、FBO、当前 target |
| 外部窗口 | 独立内容、前景遮挡和相机平移视差 | aperture 深度、off-axis 投影、基向量、child texture |
| 可进入领域 | 靠近→跨越→转头→移动→返回→off | crossing 是否先于 near 剔除，内部射线是否来自真实相机 |
| 恢复 | reload 后继续当前模式，异常后恢复主渲染 | generation、FBO 生命周期、scope 与控制状态 |
| 画质/扩展 | 边界、分辨率、预算与所要求兼容组合 | 每个扩展单独留证据 |

窗口不允许进入时，也要确认近处和背面符合已声明行为。轻量视觉领域不要求玩家真的踩在 shader 山峰上；如果这是需求，合同应升级到独立玩法空间。

### A. 原理与资产

- 固定 seed/time/camera 的基准，验证 source 输出确实独立于主场景。
- 测投影/逆投影、portal transform/inverse、sphere inside/outside/miss、非单位 ray 和 mirror winding。
- mask 带孔、尖端、near-plane crossing、相机背面不应漏出包围矩形。

### B. 真实 Forge 集成

构建并部署真实 MOD，记录 artifact hash、MC/Forge/Java/GPU、实例/存档和 active backend。进世界观察并收集证据：

| 场景 | 通过标准 |
|---|---|
| 正常世界→启用→停用 | 区域内出现独立场景，外部恢复；HUD/手/原版物品未被状态污染 |
| 前景柱、后景墙、透明玻璃 | 更近主几何遮住入口，入口挡住后景；透明结果符合事先声明的策略 |
| 平面、裂隙、带孔形状、球体内外 | 真实 world-space aperture 与深度；不是 GUI 图；inside 行为匹配所选语义 |
| 走动、旋转、FOV/缩放、不同远近 | 视差与映射一致，near crossing 不出现意外全屏或消失；内部全视野模式按合同覆盖全屏 |
| overlap 与 nested | 有确定 owner，父/子状态和边界 depth 恢复，预算叶子可辨认 |
| resize、F3+T、shader reload | 不使用失效 uniform/FBO，尺寸正确，无持续泄漏 |
| 死亡重生、跨维度、退出重进 | 旧 player/world 引用失效；没有远端残影、旧回调或陈旧 framebuffer |
| 在 enter/render/postprocess 注入一次异常 | 其余正常渲染仍工作；被改状态逐项恢复；错误可定位到失败 view |

将视觉检查与数值检查结合：target completeness、错误日志、FBO/viewport/depth/stencil 等**实际检查集合**、资源分配/释放计数和像素差异。不能只用“回调触发 N 次”或“GL error 为 0”证明可见正确。

### 同帧证据与重试成本

- 每个新跑次使用新 ID，关联 JAR hash、世界/场景 generation、真实眼位及朝向、view/projection/FOV、viewport/FBO 尺寸、入口变换、seed/time、inside 状态。自动请求用 nonce，ack 与捕获完成分开等待。
- 保存同帧父 before/after 与独立 scene，再补最终屏幕确认手/UI。相机被用户移动、FOV变化、截图缓存均会使异步图不对应；先查元数据，不仅凭两个不同时刻画面猜 shader 错误。
- 外部对固定深度标志物检查预期视差；内部对同眼位转头、同朝向平移分别检查。固定全部输入后才要求 reload 前后逐像素一致。
- 参数变更即时请求；已部署资源通过 resource override/reload；Java 修改构建并重启。只修改一个可定位假设后重测对应层，保留失败记录。新 artifact 的通过不能由旧 artifact 图像替代。
- 不在每次改美术色值后重复完整 API 源码研究；也不因基准跑通就略过新的 camera、shape 或进入逻辑回归。

Ink Realm 离线复核命令：

```powershell
python scripts/verify_inkrealm_evidence.py --output path/to/verification.json
```

它读取包内历史基准，并重新计算图像变化/固定红帆位移；不是新一次实机测试。`--jar` 可额外对照原部署哈希，新构建的 ZIP 时间戳可能不同。任意新 scene 需要对应几何/交互验收，不照搬山水专用阈值。历史基准缺完整 camera 元数据的限制保留在结果中。

### C. 真实第二世界

在两个世界布置可辨识的不同几何/天空时间，验证第二 ClientLevel 的 chunks、实体和时间真实更新，camera 运动只影响对应 view，主玩家仍处于原世界。检查远端 unload、断线、维度删除和异步队列失效。没有这个证据不得写“真实二次世界渲染通过”。

### D. 性能与兼容

测定多窗口、递归最坏分支、CPU/GPU frame time、显存峰值和资源回落。指定具体 Embeddium/Oculus/pack 版本及配置，各自运行；其他组合状态为未测。性能测试不使用每帧 readback instrumentation 得到的开销当正式 FPS。

## 报告格式

每项给 `passed / failed / partial / untested`、实际版本、source/backend、案例、原始 artifact 位置与限制。失败跑次保留，修复后新跑次成为基准。避免用“应当支持”“编译没报错”替代结果，也不把已知有限 scope 扩写成“所有状态恢复”。

路由审查案例：球形星域优先 lightweight；裂隙实时查看真实维度需要 RemoteLevel 与数据同步；临时领域不自动传送玩家；球内有限替换必须考虑出口后景；原世界草地表面 Cosmic 换材质应转交另一 Skill。
