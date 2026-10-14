# 验证结果、复现与验收边界

验证日期：2026-09-29。证据分为源码研究、CPU 数值、独立 GPU、Forge 编译、Minecraft 实机五层；一种通过不自动推出其他层通过。

**版本边界：** 下方 14 份地形读回与对应画面是 0.1.0 的历史记录；当前资产已升级 0.2.0。新增 15 份原透明 VBO 同帧对照另见 [surface-validation.md](surface-validation.md)，不能把两批探针混为一个阶段或一个版本。

## 已执行的检查

| 层级 | 实际结果 | 不能据此推断 |
|---|---|---|
| 四份 1.21.1 源码 | 跟踪矩阵、FBO/附件生产、后处理消费、边界 VFX、stencil/depth 与生命周期 | 四个库都已编译/运行，或其 API 可直接用于 1.20.1 |
| CPU 数值 | 7 项通过；882 个 depth24 往返样本，含 FOV 40/70/110、宽高比、bob、大坐标；最大误差约 0.000189417 方块 | 实际游戏矩阵采集正确，或 float GPU 在任意距离都达到该精度 |
| 独立 WebGL2 | 12 个案例通过，实际几何 raster/depth/MRT；最大位置误差约 0.000868921 方块、球 Mask 误差约 0.001691862；区外/被排除实体代理改动 0，GL error 0 | 这些图片是 Minecraft；WebGL 分类附件在 Forge 中已存在 |
| Forge 生产构建 | Java 17、Gradle 8.8、ForgeGradle 6.0.54、Forge 47.4.10，build/reobfJar 成功 | test 任务有测试源码，或编译本身证明 shader 已在游戏执行 |
| Minecraft 实机 | ASTRA-TEST / ASTRA_VFX_TEST；Forge 47.4.10；RTX 4060 Ti；真实地形 Cosmic/水墨/熔岩、视角旋转、第三人称、FOV 100、resize、重载、扩张/收缩/到期、退出重进通过 | 全部对象、死亡/跨维度、高级透明层、加速 renderer、shader packs、多人同步均认证 |

独立 GPU 场景的 position attachment **仅用于误差真值检查**；材质通道只读取 color、depth、normal/category，自行通过 inverse VP 重建位置。测试覆盖球、圆柱、Box、有厚度平面、单胶囊路径、noise 边界、重叠和代理类别；不是无限路径/区域压力测试。

## Minecraft 实机证据

真实生产 jar 安装在已获授权的 ASTRA-TEST，原存档已正常保存并做文件备份。没有修改方块或生成实体。使用原版 Forge 地形后端、Fancy 图形；测试实例包含既有其他模组，但没有 Embeddium/Oculus/Rubidium。

- 草地、树干、树叶等保留原轮廓和位置，范围内使用替代材质；远处地形保持正常。
- 第三人称相机坐标与区域中心分离，玩家模型仍保持自己的材质；第一人称手部也正常。此项只认证已观察到的玩家/手部，不泛化成所有自定义实体渲染器。
- 水面沿用后续原版透明绘制，能显示已变色的水下背景；这不是水面自身材质化的通过项。未完成水/玻璃逐层替换，详见 [水与透明层限制](water-transparency-limits.md)。
- 在原生 framebuffer 读回中比较本 pass 前后颜色与深度。已接受的 14 份单帧记录（含首次 2560×1440）均为：区外改动 0、sky-depth 改动 0、最大深度差 0、GL error 0；实际区域内有非零像素变化。
- 854×480 下，扩张记录约 r=4.48/43,035 像素，满范围 r=20/252,334 像素，收缩 r=8.78/139,028 像素；22 秒后不再产生区域 draw 的 probe，日志 failed=false。
- 854×480 → 1280×720 → 原尺寸的附件重建通过；FOV 70→100→70 验证并恢复；资源重载令 shader generation 更新，之后再次通过原生读回。
- 活动区域期间退出存档，重进后正常环境恢复；重新开启效果可以再次绘制，关闭后恢复。控制模式已退出。

原生范围检查用 CPU 根据同一捕获矩阵重建距离，并有 0.002 方块边界数值容差；因此它不是独立的 Minecraft 世界坐标真值 oracle。真实画面、源码阶段核对与独立 GPU ground truth 一起支持该结论。零误差指这一 pass 的原附件比较，不是对两张不同时间的完整游戏截图做逐像素比较。

这些 probe 在后续水层绘制前完成，不能据此认证水面的材质、透明混合或对象筛选。水的最终 RGB 会受到改变后的水下背景影响，即使水 shader 完全没变。

可移植的 14 份原生记录摘要及被测生产 jar SHA-256 随附于 [runtime-evidence.json](runtime-evidence.json)。完整画面、日志、CPU/GPU JSON 和原始 API 记录保留在研究工程 `research/local-environment/evidence/`，不把个人存档/日志打包进 Skill。

首次一组自动化截图被暂停菜单遮挡，保存为 `attempt-menu-obstructed`，没有把它们当作第三人称/最终视觉通过证据；之后确认无菜单并重新测试。截图缓存间隔与资源重载完成状态已处理。

## 复现 CPU / 独立 GPU

将 `assets/validation` 复制到工作目录；需要 NumPy、Node 和 Playwright，浏览器要支持 WebGL2 与 EXT_color_buffer_float。示例（工具链由目标环境提供）：

```text
python validate_math.py
node validate_gpu.cjs
```

`LE_BROWSER` 指定 Chromium/Edge 可执行文件；`LE_EVIDENCE_DIR` 指定 GPU 输出目录。脚本默认的 Windows Edge 路径只是已有本地验证环境；其他环境显式覆盖。CPU 输出在脚本相邻 evidence。检查退出码、JSON 的 status 和各断言，不只查看文件生成。

## 复现 Forge

复制 `assets/forge-1.20.1-lab` 为独立工程，Java 17 下运行 `gradlew.bat build`（Windows）或 `./gradlew build`。构建首次可能需要解析 Forge/Gradle/Mixin AP 依赖。当前 `build/libs/local-environment-lab-0.2.0.jar` 是生产重混淆产物；不需要安装 Veil/Lodestone/IP。0.1.0 历史 probe 的 SHA 不适用于当前 JAR。

选定并备份测试存档，安装 jar 后通过实例启动；不能向运行中的客户端热加载新的 Java 模组。

```text
/localmaterial sphere 0
/localmaterial hold 0
/localmaterial hold 1
/localmaterial hold 2
/localmaterial probe
/localmaterial status
/localmaterial off
```

- sphere：20 秒 grow/hold/shrink；hold：固定释放点、20 格半径、固定材质时刻用于对照，直到 off/卸载/死亡。
- probe：为下一次实际区域 draw 排队一次附件读回，结果写 `<gameDir>/local-environment-lab/probes`。它会产生 GPU 同步停顿，仅用于诊断，不计入性能基准；没有活动区域时请求等待下一次 draw。
- status：draw 数、shader generation、GL error、renderer gate 和失败状态。draw 计数非零仍不是视觉证据。
- off：关闭区域并释放自有 snapshot；不要退出时遗留 hold 模式。

## 扩展功能的验收矩阵

新增 backend/类别时重新验证：固定世界棋盘、原始/重建深度、类别独立可视化、边界前后对照、穿插遮挡、相机进出区域、near-plane、FOV/bob、第一/第三人称、大坐标、resize、reload、死亡/维度/退出、重叠与透明层。

新增网络区域需要晚加入、乱序 revision、过期删除和维度隔离。新增材质需要检查尺度、世界锚点、色彩空间、远处闪烁、性能与必要光照信息。做 shader pack adapter 时单独测试 jitter、shadow view、HDR/tonemap、Fabulous 和每种透明目标。

仍未完成的明确范围：Forge 多区域管理器、通用 category MRT、地形精准原 lightmap/AO、任意透明 renderer/流体 ID、完全异化与折射、bloom/边缘发光实机、其余五种材质的产品实现、死亡/跨维度专项、bob 行走实机、加速/光影兼容、多人网络及毫秒级 GPU 性能基准。0.2.0 的受限透明区块后端不改变这些边界。
