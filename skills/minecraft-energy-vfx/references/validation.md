# 实现验证与许可

日期：2026-10-03。当前源码版本 v0.2.5，energy-vfx/2，48 字段、20 个可编辑起点。本记录分别区分网页、独立客户端和用户实例；未完成的部分不记为通过。下面 v0.2.2 及更早段落均为对应版本的历史证据，不能替代 v0.2.3 的本次验证。

## v0.2.5 第三人称拉近预算

第三人称拉近 QA 在新隔离世界中先积累 400 tick，再经过 4/2.5/1.5/0.75/4 米，连续 700 tick、2560×1440、容量 32、resolution=1、steps=128；recipe SHA-256 为 `8152da049e3f35f74b2a8d6cd4417bda08554713e2cfd5ee56f2d4c495c26956`，GL error=0、droppedQueryFrames=0。最新裁剪运行的 VFX GPU P95 为 20.569/14.024/8.116/0.793/24.269 ms，frame GPU P95 为 20.641/14.110/8.285/0.961/24.348 ms；阶段上传中位数约 24/8/3/0/24。

生产近距策略从 3.25 米开始降低低贡献包的可见性，2 米达到更强阈值；它保持模拟数量与内部运动，但会改变贴近镜头的可见烟量。上述是隐藏窗口和 QA-only 相机位置覆盖下的独立 VFX 趋势，不是用户现场独占全屏 FPS 保证。图片 `after-final.png` 是返回 4 米阶段，不能单独证明近距外观；近距取舍以 `nearEyeHidden`、`nearEyeFaded` 和相机距离记录。

## v0.2.3 移动与近距优化

逐包保守屏幕投影在密度积分前排除不覆盖当前像素的包，处理相机后方拖烟与跨越透视 w=0 的支撑范围，并为 Bloom 保留随体积目标比例缩放的采样边距。这一裁剪路径保留原积分、分辨率与气团数量。自身近距可见性策略另外处理第三人称相机被环境推近的情况：相机到玩家眼睛 ≥3 米保持完整绘制，≤1.8 米采用稳定出生子集与 nearFade，中间 smoothstep 过渡；全部气团继续模拟。近距策略有外观取舍，与保持画质的裁剪收益分别报告。

<!-- V023_FINAL_DATA_BEGIN -->
Minecraft 1.20.1 / Forge 47.4.10 / Java 17 / RTX 4060 Ti，两个实际 2560×1440 的独立新世界各完成七阶段（关闭、两轮旧/新移动、旧/新站立），completed=true。每阶段先确认相同客户端起点，再绑定发射器；80 tick 暖机与200 tick测量，CPU/GPU指标均限定测量期。所有案例GL error=0，无丢弃query；站立测量期位移为0。

贴墙相机约0.714米，两轮移动特效 GPU P95 为 38.706 → 6.856 ms；38.406 → 6.540 ms，上传中位数24 → 3，模拟数量约24–25不变。近距收益包含固定出生筛选和近眼淡出，截图显示贴脸站立时烟量大幅减少，不能称无画质损失加速。正常相机约4米，P95为 14.236 → 12.389 ms；14.414 → 12.514 ms，上传中位数24 → 24、近距权重0、近距排除0。数据不是用户整游戏FPS保证，详细方法见 [motion-performance.md](motion-performance.md)。

十二组同帧原解析参考/缓存与逐包裁剪画面对照，近眼策略关闭，RGB最大通道差全部0；Scissor、texture array unit4、image unit0恢复，GL error=0。新增相机后方拖烟、相机贴近拖烟及resolution=0.25/Bloom radius=0.1案例。不能用该图像对照宣称近距可见性策略无外观取舍。证据为evidence/game/flow-front-1280/quality-comparison.json。

网页十二个幽气起点、v1升级、容量与空来源、移动继承、相邻整数段预算和GL回归全部通过，见evidence/performance/motion-browser-regression.log。生产build/reobfJar通过，见evidence/performance/motion-production-build.log。

源码、Forge资源与生产JAR的七份shader哈希一致；两个ZIP的CRC、测试世界/参考素材排除及PacketBounds.class存在检查通过。运行库SHA-256：0a862ed1b74447269564b1a72307f42b63ee06d9a997f1f83a9a4786a70c2dca。

已安装 energyvfx-0.2.3.jar 并重启 ASTRA-TEST，进入原 ASTRA_VFX_TEST。PID 8920 与启动器、9876 监听及 Helper 状态匹配；Helper 调试入口返回 HTTP 200，control_mode=false，未改变玩家相机、装备或物品栏。固定命令桥新 nonce 确认 runtime=energyvfx/0.2.3、active=true、GL error=0、failed=false，draws 2147 → 4672，逐包裁剪已启用。现场主 target 为 854×480，相机 FIRST_PERSON，模拟 24 包、上传 2 包。近距权重 1.0，剔除 22 包；这些现场计数不代替移动帧时间测量。蓝色配方 SHA-256 安装前后一致：8152DA049E3F35F74B2A8D6CD4417BDA08554713E2CFD5EE56F2D4C495C26956。新 JAR SHA-256：0a862ed1b74447269564b1a72307f42b63ee06d9a997f1f83a9a4786a70c2dca。mods 中仅有新版 EnergyVFX，旧运行库保存在 mods 之外。未测量用户实例全屏整游戏 FPS；独立性能场景与用户现场验证分开记录。
<!-- V023_FINAL_DATA_END -->

## v0.2.2 全屏优化：历史验证

原生 RTX 4060 Ti / Forge 47.4.10，实际帧缓冲 2560×1440、resolution=1、steps=128、相同 25 个冻结包：第三人称两轮体积 GPU 中位数均值 60.102 → 18.727 ms，减少 68.8%，全部包仍绘制。第一人称新路径全部特效约 11 ms，包含固定出生选择与近眼淡出：25 个包继续模拟，3 个上传、20 个被稀疏选择排除、2 个近眼隐藏。第一人称收益包含外观取舍，不能称同画质的缓存加速，也不是用户整游戏 FPS 测量。方法、原始数据与中间未通过试验见 [fullscreen-performance.md](fullscreen-performance.md)。

九组同帧缓存/解析路径质量对照关闭第一人称可见性策略；八组 RGB 最大差 0，强流动组最大差 1/255，无超过一个色阶的像素。GL error=0，非默认 Scissor、texture array unit 4、image unit 0 均恢复。覆盖后续时刻、强流动、强制解析回退、空包及原前后视/近裁剪/Bloom 参数兼容案例。只在支持 OpenGL 4.3 的目标上实测缓存；无此能力走解析路径。

独立动态世界 ENERGY_GHOST_QA_1790939275678 完成九例，含移动离体拖烟、gear-only、第一人称、资源重载、白/蓝烟纱和第一人称蓝烟，均 GL error=0、failed=false。已查看第三人称蓝烟与持剑/HUD 完成后的第一人称截图；第一人称截图证明持剑画面正常且无遮挡，未据此证明手部有清晰可见烟纱。原始 evidence/game/ghost/game-results.json 与 summary.json；日志 evidence/performance/flow-ghost-regression.log。

空间六例 baseline、energy-blue、energy-orange、occlusion、reload、camera-inside 均 GL error=0、failed=false，最终 draws=572；日志 evidence/performance/flow-space-regression.log。网页 12 起点、v1 升级、容量、来源权重、移动继承、整数段相邻预算与 GL 检查完成；最后一次完整回归全部通过。第一次极限相邻步数哈希不一致，单独五次固定时刻复现与完整重跑均一致，未修改算法，暂未定位第一次差异原因；相关证据为 evidence/performance/odd-debug.json 与 flow-browser-regression.log。

v0.2.2 生产 build/reobfJar 已通过，日志 evidence/performance/flow-production-build.log。源码、Forge 资源、生产 JAR 内六份共用 GLSL 与 flow-cache.glsl 的七份 SHA-256 一致；两个 ZIP 的 CRC 与排除测试世界/参考素材检查通过。运行库 SHA-256 为 2b79fcf51e534976abe7c00ed3b364ba9a7dcbdfdb79257c50a4c1e9ba7e3a88。

已仅更新运行库、保留原配方，重启 ASTRA-TEST 进入 ASTRA_VFX_TEST；启动 PID 1916、9876 监听与 Helper 状态一致，[Helper 调试入口](http://127.0.0.1:9876/debug) HTTP 200，control_mode=false。两次新 nonce status 返回 energyvfx/0.2.2、active=true、GL error=0、failed=false，draws 1623 → 2245，flowCacheActive=true。当前 854×480 窗口、第一人称 25 个模拟包/2 个上传包；无穿戴或持有装备，不据此宣称用户此刻装备烟纱已视觉验收。配方 SHA-256 保持 8152DA049E3F35F74B2A8D6CD4417BDA08554713E2CFD5EE56F2D4C495C26956。旧 JAR 已移到 mods 外备份。实例 GPU 帧耗时与全屏 FPS 未测量；实际运行检查和完整独立压力测试分别报告。

## v0.2.1 性能与质量

独立原生 Forge 47.4.10 / RTX 4060 Ti，在 1280×720、resolution=1、steps=128、25 个冻结密度包上，两轮对照体积积分 GPU 中位耗时均值 20.341 → 14.744 ms，减少 27.5%。每例预热 100 帧、测量 260 帧。异步 timestamp query 无丢帧，不同步等待 GPU；生产运行不启用计时。分辨率、数量、采样位置和内部运动未降低。总 CPU 提交因区域计算略增加。整帧呈现波动不能换算为用户 FPS 保证。

六个同帧原始/优化渲染对照：蓝色前视、白色前视、蓝色后视、近裁剪面第一人称、已有低分辨率 Bloom 参数、空发射器，RGB 最大通道差均为 0；GL error=0，宿主非默认 Scissor box/开关均恢复。已查看对照截图。低分辨率案例仅为参数兼容性检查，并未修改用户全分辨率配方。详细方法与数据见 [performance.md](performance.md)。

最新空间效果六例（baseline、blue、orange、occlusion、reload、camera-inside）已在优化渲染器完成，GL error=0、failed=false。原始结果为 E:/着色器渲染/energy-vfx-lab/evidence/game/game-results.json，日志 evidence/performance/space-regression.log。人物动态发散在全新 ENERGY_GHOST_QA_1790934642551 完成八例（含移动、gear-only、第一人称、资源重载、白蓝烟纱），累计 650 次提交，均 GL error=0、failed=false；已查看移动与蓝色烟纱截图。日志 evidence/performance/ghost-regression-v2.log。12 个网页幽气起点、v1 升级、气团容量与相邻整数段测试全部通过。生产 build/reobfJar 已通过，见 evidence/performance/production-build.log。

v0.2.1 实装时，用户正常退出原会话后重启 ASTRA-TEST，进入原 ASTRA_VFX_TEST。当时 PID 14036 与 http://127.0.0.1:9876 的监听进程和 Helper 状态一致，/debug HTTP 200，control_mode=false，未修改相机、装备与物品栏。固定命令桥用当次 nonce 确认 energyvfx/0.2.1，active=true、GL error=0、failed=false、draws=3661，并继续增长；24–25 个密度包。蓝色配方 SHA-256 前后一致（8152DA049E3F35F74B2A8D6CD4417BDA08554713E2CFD5EE56F2D4C495C26956），resolution=1、steps=128。此段为旧会话证据，不能替代后续版本的新 nonce 检查；旧截图为游戏菜单，不作为烟纱外观证明。

## v0.2.0 已验证

网页新增 12 个幽气与白/蓝烟纱起点均实际渲染，独立图像不同；原始结果为 `E:/着色器渲染/energy-vfx-lab/evidence/ghost-browser.json`。已有空间效果的离线 GPU、严格导入、参数编辑、相机、深度、导出与保存证据仍保留在 browser.json 与 replay-stress.json。默认编辑服务端口为 8796。FPS 记录是浏览器提交/回调帧率，不能当作单 pass GPU 时间或所有硬件保证。

最新烟纱的气团以固定 20 Hz、RK2 在世界坐标独立漂移；内部用不同轴和相位的局部 curl 场回溯坐标，形成折叠。烟纱以局部近似 signed distance 定义 Gaussian 薄层，段内按距离线性变化作误差函数解析积分；非线性距离与端点振幅仍为近似。烟纱路径已去掉像素 jitter 和 3×3 屏幕模糊。这不构成完整流体求解器，也不保证拉伸和回溯后的世界厚度严格恒定。

Minecraft 1.20.1 / Forge 47.4.10 / Java 17 / NVIDIA RTX 4060 Ti 的独立 dev client，在全新 `ENERGY_GHOST_QA_1790929795353` 平坦世界完成八案例：effect-off、blue-body-equipment、movement-detached-trail、gear-only、first-person、resource-reload、moon-white-veil、ice-blue-veil。每例至少 90 渲染帧、32 tick；最终累计 755 次提交，全部 GL error=0、failed=false。白蓝烟纱截图已查看，显示实际人物、盔甲、钻石剑与连续折叠薄烟。

第三人称双来源记录 280 个表面三角形、5 个装备槽、renderedPose=true、1 个 baked 持有物来源、fallback=0，身体 13 包/装备 15 包；相对各包出生点的漂移峰值为 1.571 米。移动例相对出生点漂移峰值 1.533 米，到当前玩家中点距离峰值 2.886 米；两项度量分别报告。gear-only 中身体包为 0、装备包为 27、表面三角形 208。第一人称自身强度 0.08、renderedPose=false；本次仅证明低强度分支执行和计数，不证明手部烟纱外观。

白/蓝卷烟分别读取起点 15/16；均为 resolution=1、steps=128，身体/装备包均为 8/8，相对出生点漂移峰值分别 1.822 和 1.865 米。steps=128 在持续发散路径实际为每包 64 个积分段、65 个端点，不宣称每包采样 128 次。effect-off 基线 draws=0，资源重载后仍为冰蓝幽气、attachment=3，GL error=0。

原始结果：`E:/着色器渲染/energy-vfx-lab/evidence/game/ghost/game-results.json` 与 summary.json；八张 PNG 同目录。计数证明消费者执行，视觉结论来自截图检查。最新 v0.2.0 生产 build/reobfJar 已通过；日志 `evidence/sharp-smoke-forge-build.log` 为 BUILD SUCCESSFUL in 9s，独立运行日志为 `evidence/sharp-smoke-game-run.log`。打包脚本在复制前核对源码、Forge 资源与 JAR 内六份 GLSL 的 SHA-256，拒绝旧内核。

本段保留 v0.2.0 独立验证时的历史证据。v0.2.3 的构建、打包与 ASTRA 实装以本页新版记录为准；旧版证据不替代本次检查。

## 保留的 v0.1.0 历史证据

原 Minecraft 1.20.1 / Forge 47.4.22 独立空间效果六案例保留在 evidence/game/game-results.json：baseline、energy-blue、energy-orange、occlusion、reload、camera-inside。记录 573 次提交，GL error=0、failed=false；该证据对应原 39 字段空间效果，不冒充新版人物发散测试。旧 Skill 的 scaffold、备份与非法配方原子拒绝记录在 evidence/skill-checks.json。

## 许可与边界

原创编辑器、场函数、积分、气团模拟、真实挂点适配和 GL 宿主代码采用 MIT。Stefan Gustavson / Ian McEwan 的 [psrdnoise](https://github.com/stegu/psrdnoise) 原始 MIT 版权与许可保留在 noise.glsl、内嵌源码及 JAR。五个幽气技术参考及固定提交见 ghost-sources.md；研究参考不是已移植的引擎组件。参考视频、网页图片、测试世界与其他项目的素材不随 Skill 分发。

当前是单个客户端效果，没有网格流体、气团碰撞、照亮地形、多人技能同步或任意 BEWLR/骨骼装备完整表面解析的通过声明。Oculus/Fabulous 等组合应分别实测。
