# 《五行仙途》现有御剑剑气：原地学习基线

本次审阅日期为 2026-10-15，目标是本机 `E:/五行仙途` 的当前 `src/main`。只读源码、报告和截图，没有改宿主文件、构建、启动游戏或重新验证旧 JAR。完整文件 SHA256、历史版本比对、证据日期见 [本机完整证据](E:/着色器渲染/research/dunguang-flight-20261015/wuxing-sword-evidence.json)；便携的文件身份见 [源码快照](wuxing-source-snapshot.json)。本机项目及其子目录未找到 `AGENTS.md`；遵守用户本次提供的垃圾目录等约定。`src/main/resources/META-INF/mods.toml:3` 声明 MIT；此处仍以原地源码作为学习基线，不复制宿主源码、模型或专属资产到 Skill。

## 结论和外观边界

这套御剑效果已经有真实 Minecraft 客户端历史证据，适合作为“飞剑后缘真实挂点 → 世界历史剑气 → 程序材质 → 深度约束光晕”的成熟学习基线。它的主体是单条有宽度和薄纱细节的剑气，强化状态再附加螺旋、风环与稀疏碎光。不是影视参考里的完整人物包裹遁光，也不是本次新建 WebGL 研究台的运行后端。

本次实际查看了以下既存游戏 PNG；日期均为其报告目录中的历史测试日期，不是本次重跑：

|既存画面|本次目视观察|
|---|---|
|[2026-10-10 原生 BASIC 侧面](E:/五行仙途/reports/realm-flight-front-20261010/front-soft-native/flight-tiers/sword-basic-side.png)|人物直立站在细长飞剑上；剑尾向后延伸一条很细的青白光线，远距离强度克制，人物本身没有被大气团包住。|
|[2026-10-10 原生 JIYU 转向](E:/五行仙途/reports/realm-flight-front-20261010/front-soft-native/flight-tiers/sword-jiyu-turn.png)|近剑尾亮、后端细，青白带略弯，剑与人物轮廓仍清楚。这一张不能单独证明整段转弯动画质量。|
|[2026-10-10 原生 DUNXU 近景夜间](E:/五行仙途/reports/realm-flight-front-20261010/front-soft-native/flight-tiers/sword-dunxu-close-night.png)|青白尾迹从剑后方弯出，近端白芯更亮、向远端渐细；飞剑和人物保持独立实体外形。|
|[2026-10-10 原生 JIYU 近景白天](E:/五行仙途/reports/realm-flight-front-20261010/front-soft-native/flight-tiers/sword-jiyu-close-day.png)|亮尾紧贴剑后，远端逐步淡出；这一视角主要读成单条细长剑气，不能把材质内部卷曲误说成多条独立世界长尾。|
|[2026-10-10 Solas DUNXU 转向](E:/五行仙途/reports/realm-flight-front-20261010/front-soft-solas/flight-tiers/sword-dunxu-turn.png)|Solas 天光、雾与青白尾迹共存，人物有透明表现；仅证明此历史场景的画面存在，不推断所有光影包兼容。|
|[2026-10-14 最终御剑第一人称](E:/五行仙途/reports/flight-flow-stability-20261014/sword-ui/02-flight-native-trail.png)|HUD、准星和手臂可见，天空中央和边缘没有青色速度流；对应 JSON 的本人历史和第一人称气流顶点均为 0。|

另查看了 [sword-flight-fast-20261011.jpg](E:/五行仙途/reports/sword-flight-fast-20261011.jpg)。画面明确写着“仅工作台预览，确认后再安装”，属于动作设计工作台，不能当成 Minecraft 游戏截图证据。

## 当前实际代码链

下列行号绑定本次读取的当前文件；完整路径 SHA256 在 JSON。宿主后续修改后需要重新定位。

|责任|当前入口与准确行号|
|---|---|
|固定 tick 发现飞剑、采样真实后缘|[FlyingSwordEffects.sampleTick](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/FlyingSwordEffects.java:52)，52–74|
|模型空间后端测量与世界变换|[SwordFlightAttachment.tail / measure](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/SwordFlightAttachment.java:17)，17–47|
|固定历史与异常位移识别|[SwordFlightTrailHistory.sample](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/SwordFlightTrailHistory.java:25)，25–54|
|共享插值姿态|[InterpolatedFlyingSwordTransform.get](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/InterpolatedFlyingSwordTransform.java:22)，每帧每实体一个缓存，22–42|
|飞剑模型实际渲染变换|[ControlledSwordRenderer.render](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/ControlledSwordRenderer.java:15)，15–35|
|世界空间剑气、插值头段、强化螺旋|[FlyingSwordEffects.render](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/FlyingSwordEffects.java:118)，118–213；`spiral` 229–246|
|材质注册、正常透明路径、晚合成路径|[SwordFlightTrailRenderer](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/SwordFlightTrailRenderer.java:28)，`register` 28–31；RenderType 35–38；`drawPost` 46–62|
|光影检测与世界深度快照|[TrailShaderBridge.shaderPackDetected / captureDepth](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/TrailShaderBridge.java:60)，60–79 / 91–125|
|独立 Bloom 的御剑入口与恢复状态|[MeteorBloomRenderer.drawSword](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/MeteorBloomRenderer.java:65)，65–116；`State` 126–169|
|统一空间/时间相位|[SwordTrailPhase](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/SwordTrailPhase.java:5)，`TickClock` 9–25；`origin/coordinate/time` 27–36|
|第一人称剑面眼位|[YujianClientController.cameraAngles](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/YujianClientController.java:131)，164–182|
|本人第一人称明确排除境界气流|[FirstPersonFlightFlow.tick / draw](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/FirstPersonFlightFlow.java:31)，31–33 / 51–54|
|客户端开关与通道枚举|[FlyingSwordConfig](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/yujian/FlyingSwordConfig.java:33)，`TRAILS` 33；`TrailPath` 37；`TRAIL_PATH` 41|

实际御剑材质是 `sword_flight` 和 `sword_flight_post`，顶点格式 `POSITION_TEX_COLOR`。不要只看到旧的 `sword_trail_post` 名称，就将通用 `POSITION_COLOR` 桥当成当前剑气的全部实现。当前树未找到名为 `PostPass` 的御剑生产类；晚提交职责由 `FlyingSwordEffects.submitPending / postShader`、`SwordFlightTrailRenderer.drawPost` 与共享 Bloom 后端承担。

## 可以直接迁移的工程经验

### 1. 挂点来自当前模型后缘

`SwordFlightAttachment` 按当前显示物品取得世界模型或物品烘焙模型，缓存键含模型身份、模型 yaw/pitch/roll 和自定义模型标记。测量时应用模型旋转，普通物品应用 FIXED 显示变换，再居中 `(-.5,-.5,-.5)`；遍历所有方向及无方向 baked quads，用固定随机种子 42，取变换后 z 最小的一端，后端 2.5% 长度范围里的 x/y 均值作为挂点。

外层再应用 `2.35 * visualScale` 和 `FlightFrame.rotation(yaw,pitch,roll)`，与 `ControlledSwordRenderer` 的飞行尺度和旋转约定一致。`FlightFrame` 约定 +Z 为前、+Y 为剑面法线。缓存超过 32 项会清空，空模型仍有 `-.85/2.35` 的明确备用值；不能说所有模型已经有手工专属挂点。

固定 tick 历史读取真实 tick 姿态；渲染头部读取同帧插值姿态。它们共用同一个 `tail` 函数，但历史绝不从可能属于上一帧的插值缓存取样，避免随帧率出现起点抖动。

### 2. 历史受寿命、长度、容量三重约束

`SwordFlightTrailHistory:15–16`：目标采样间隔 0.09 格、寿命 22 tick（20 TPS 下 1.1 秒）、最大长度 22 格、容量 256。每个客户端 tick 取真实后缘位移，再将该段等分为 `ceil(distance/.09)` 份，储存世界位置、采样 tick 与累计弧长；因此是每 tick 的有界距离细分，不是另外一个 60 Hz 模拟器，也不是严格跨 tick 等间距累积器。

重复 tick 不增加节点，时间倒退清历史；静止位移小于 .002 不刷新老节点年龄。生命周期和长尾裁剪继续执行，停止后不会留下永不消失的旧尾。换世界/断线重置、删除实体/非 root/超过 64 格移除，最多跟踪 32 把。

异常位移阈值由调用方用已同步的飞行速度估算，`max(8, max(flightSpeed, renderSpeed) * 1.5 + 2)`，乘实际 tick 间隔。这样合法遁虚高速超过每 tick 8 格时不会被固定常数误判为瞬移。真正超阈值会清空历史并从新点开始。这里不是研究台那种“给旧尾迹保留独立段并继续淡出”的策略，也没有独立的 120° 急转断段字段。

高速增加很多采样点时，一次性计算裁掉的前缀，避免反复 `ArrayList.remove(0)` 搬移；不能将这个静态优化直接换算成实测 FPS 提升。

### 3. 世界历史与局部强化螺旋分开

`FlyingSwordEffects.render:157–194` 对历史用 Catmull-Rom 细分，近处每原段 2 份、超过 32 格则 1 份；U 用累计弧长，宽度、透明度同时乘长度衰减与年龄衰减。剑尾附近通过 1.4 格 smoothstep 把宽度从 10% 平滑张开。最后一小段只连接插值后缘，不写入历史；过长连接（平方距离 ≥64）不提交。

强化强度来自宿主 `advancedPhase().trailIntensity`、惊鸿、冲刺和横滚因子；长度上限 22 格，半宽上限 .85。`spiral:229–246` 在当前剑姿态基底里向后生成 1–2 股、每股 72 分段的螺旋，高因子再加风环。它是局部当前姿态附加形状，不能当作世界历史的另一条持久曲线。

带朝向相机；视线与切线接近平行时使用备用垂直轴，防止归一化零向量炸出巨大面。稀疏碎光按历史弧长分格及 UUID 种子确定，最多 9 个，不是每帧随机堆叠。

### 4. 材质用单条宽带承载白芯、卷曲丝与薄纱

[sword_flight.fsh:20](E:/五行仙途/src/main/resources/assets/wuxingxiantu/shaders/core/sword_flight.fsh:20) 的 `swordMaterial` 将通道放在 `trailCoord.y`：0–1 主带、2–3 螺旋、4–5 碎光。主带内有轻微游移白色 spine、较宽 curl veins、veil、ragged edge 与 breaks；`fwidth` 给白芯一个抗锯齿宽度。近端亮、外侧透明、白芯/青蓝外围由同一程序材质表达。普通与 post shader 共享同一材质算法，但源码为两份，需要后续维护时同步。

这比把许多恒宽平行激光线堆一起更能产生一股剑气的整体感，仍是带状程序纹理，不是体积烟流求解器。

### 5. 云层、光影与前景遮挡要分别处理

`AFTER_TRANSLUCENT_BLOCKS` 在云层写深度之前保存地形、实体及玻璃的世界深度。实际剑气几何在 `AFTER_PARTICLES` 准备。设置 `AUTO` 时检测 Iris/Oculus 是否开启光影；显式 NORMAL 可以选正常路径；两种兼容枚举都选 post。**此外，只要原版云不为 OFF，即使显式 NORMAL 也会被提升到 post 路径**（`FlyingSwordEffects:130`），避免把云深度当成不透明墙。

正常路径是 LEQUAL、关闭剔除、只写颜色的透明 RenderType。post 路径把 mesh 暂存，到 `RenderGuiEvent.Pre`（最高优先级，先 flush GUI）提交，隐藏 GUI 时由 RenderTick.END 的 pending 检查兜底。`drawPost` 关闭固定管线深度测试，但 [sword_flight_post.fsh:61](E:/五行仙途/src/main/resources/assets/wuxingxiantu/shaders/core/sword_flight_post.fsh:61) 采样已捕获深度，超出 `sceneDepth + 1e-7` 的片元 discard。不能因为看见 `disableDepthTest` 就误说“穿墙绘制”，也不能把屏幕深度比较写成真实透明介质透射。

第三方体积云的透明度/独立深度没有被重建；历史三管线通过也不意味着任何光影包都已验证。

### 6. 这里确实有局部屏幕 Bloom

共享 `MeteorBloomRenderer.drawSword` 将选定剑气重绘到半分辨率 RGBA32F emission；RGB 预乘发光量相加，alpha 不是透明度，而是最近发光贡献的线性视空间深度（MIN）。两轮水平/垂直模糊，共 4 次，核采样和双线性重建均检查目的像素世界深度；最终 RGB-only 加法合成再次检查遮挡，保护主画面 alpha。

这是宿主已实现的深度约束局部 Bloom，与本次独立 WebGL 研究原型仅用宽窄带/薄壳模拟柔光的能力不同。不要将宿主能力自动赋予研究台。

`MeteorBloomRenderer.State` 恢复 FBO、viewport、纹理单元/绑定、shader、blend 函数/方程、深度写入、剔除、scissor、颜色 mask/clear color，连 `BlendMode.lastApplied` 缓存也恢复；GL 状态恢复和引擎缓存恢复都需要，不能只保存一个 blend 开关。

### 7. 长时间/大弧长必须共同重定位且周期严格闭合

`SwordTrailPhase` 的空间周期为 256 格、时间周期为 2560 tick / 128 秒。整个 mesh 共用一个 origin，不能逐顶点取模后跨一整个周期插值。时间先对整数 tick 取模，再转 float，避免世界年龄超过 float 有效整数范围后抖动。shader 的空间/时间谐波和 periodicNoise 与这两个周期对应。

`TickClock` 在换 level 身份时重置，初始化一次后每个未暂停客户端 tick 单调 +1，不直接追随随后到达的网络世界时间校正。采样年龄、渲染 now 和材质时间使用同一个轨迹时钟。

### 8. 当前御剑第一人称不要加回境界流光

2026-10-14 较早的 [flight-view-flow 修改说明](E:/五行仙途/reports/flight-view-flow-20261014/修改说明.md:7) 曾写“御剑第一人称也采用同一套视野流光”。随后同日最终 [flight-flow-stability 调整说明](E:/五行仙途/reports/flight-flow-stability-20261014/调整说明.md:10) 明确移除了这个入口，当前源码同样排除骑乘。

当前 `FlyingSwordEffects:60/64/154` 不采样/绘制本地玩家第一人称的剑世界历史；`FirstPersonFlightFlow:31/53` 又排除任何坐骑，因此御剑第一人称既不出现自己的历史尾迹，也不出现境界第一人称速度气流。其他旁观者/第三人称仍保留世界剑气。

镜头本身仍取剑的局部上方向眼位 `frame.local(0, riderOffset + eyeHeight, 0)`，跟随剑面俯仰横滚；第一人称不应用第三人称跟随距离、螺旋抬高或八条碰撞射线。这是明确分离“视角适配”和“流光材质”的成熟结果。

## 证据链与本次验证范围

- 2026-10-09 [implementation-validation.json](E:/五行仙途/reports/sword-flight-20261009/implementation-validation.json)：早期 Java 17 独立历史检查和生产 GLSL 在 WebGL2 合成几何上的检查；其自身明确 Minecraft/Oculus runtime pending，不能单独当游戏实装验收。
- 2026-10-10 [delivery-verification.json](E:/五行仙途/reports/realm-flight-front-20261010/delivery-verification.json)：旧版本 native / Oculus关闭 / Solas V3.7b 三管线，各 89 主截图、580 检查、180 转向序列帧；其中御剑专项每管线 12 图 / 53 检查 / 2600 真实客户端 tick，包含 1024/2048 弧长重定位与自然 128 秒时间环绕相邻帧。该专项使用真实注册 root 实体与 RemotePlayer 骑乘的受控客户端视觉场景，不是服务器高速授权或 LAN 运输证明。
- 2026-10-14 [flight-first-person 修复说明](E:/五行仙途/reports/flight-first-person-20261014/修复说明.md)：后缘挂点从固定 .85 偏移改成读取当前 baked 模型；后续 first-person 行为已被下述版本替代。
- 2026-10-14 最终 [sword-ui/captures.json](E:/五行仙途/reports/flight-flow-stability-20261014/sword-ui/captures.json)：6 个真实客户端截图，已学习功法、实际服务端批准骑乘飞剑、速度/位置记录；本人 `history=[]`、`normalVertices=0`、`firstPersonFlowVertices=0`，全部 GL error 0。截图名沿用 native/post/cloud，但实际 `path="未绘制"`，不能把文件名当成三种世界拖尾路径在第一人称被渲染的证明。
- 同一最终报告记载当时安装 JAR SHA256 `f7e3d7232617ffa2bb637d8446ee729b9e1dbfd22a42ce6d1c99f7929882d9c6`；本次未检查或替换安装目录，不宣称该文件仍是当前游戏安装版本。

当前 `FlyingSwordEffects`、`History`、共享 Bloom 等文件与 2026-10-10 final manifest 已存在 SHA 差异；新增 `Attachment` 不在该旧 manifest，不能把“旧清单里没有”误记为哈希不一致。`SwordFlightTrailRenderer` 与 `SwordTrailPhase` 逐字节匹配旧 manifest。逐文件比较结果已记录 JSON。历史 PASS 只对应旧清单，本次只重新核对当前结构、哈希及既存图像，不给当前完整工程新增游戏运行认证。

适合写入遁光 Skill 的能力是：读取用户现有成熟宿主 → 识别真实入口与视角边界 → 复用挂点/历史/后处理合同 → 独立设计新的包裹与流丝层。不要仅复制一个 shader 或把历史截图、研究台和当前运行包混成同一个“已验证效果”。
