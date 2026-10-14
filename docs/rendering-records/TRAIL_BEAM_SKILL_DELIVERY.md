# 武器拖尾与能量束 skill 交付

## 2026-10-02：离线工作台与丰富特效

个人技能 `minecraft-trails-beams` 已扩展为可见设计流程。打开 [HTML 工作台](skill/minecraft-trails-beams/assets/workbench/index.html) 即可离线编辑、旋转、缩放、播放或拖动时间轴，并导入/导出完整设计。七个可改起点为霜蓝双刃、棱彩双芯束、金色螺旋、熔火鞭痕、紫电鞭束、符文牵引、分叉雷束。截面、六种路径、最多四条分叉、图案、混合、层、HEX 色带、宽度节点、流速、采样都能独立调整。预览为 Canvas 近似画面，不冒充游戏 shader 截图。

工作台导出的 `recipe.json` 与 `path.json` 均有 Forge 1.20.1 的真实解析与绘制入口。可用 `scripts/author_effect.py --recipe ... --path ... --out ...` 生成完整工程，或将两个文件放进实例 `config/trailbeam/` 后运行 `/trailbeam reload`。真实动画刀刃仍须提供每帧刃根、刃尖世界坐标；分叉束的服务端命中、骨骼和第一人称由目标 MOD adapter 接入。

验收结果：配方/工程 24 项、HTML 浏览器 33 项、Java core 50 项，Forge 47.4.10 生产 build/reobf 成功。ASTRA-TEST / ASTRA_VFX_TEST 加载工作台 storm 配方与路径，五场景 2560×1440 截图、25 项记录检查通过；世界截图可见蓝白分叉束，GL 采样点无错误，资源重载 generation 1→2。看图后仅更改画廊路径文字，最终 JAR 重新构建成功并安装到已关闭的测试实例；这个最终文字变动没有另做游戏截图。

| 新证据 | 入口 |
|---|---|
| HTML 分叉雷束 | [workbench-storm.png](trail-beam-lab/evidence/workbench/workbench-storm.png) |
| Forge 世界分叉雷束 | [world-effects.png](trail-beam-lab/evidence/20261002-094831/world-effects.png) |
| Forge 画廊 | [gallery-t0.png](trail-beam-lab/evidence/20261002-094831/gallery-t0.png) |
| 实机记录 | [runtime-validation-expanded.json](trail-beam-lab/evidence/runtime-validation-expanded.json) |
| 构建与源码同步 | [build-expanded-final.log](trail-beam-lab/evidence/build-expanded-final.log)、[source-comparison-expanded.json](trail-beam-lab/evidence/source-comparison-expanded.json) |

最终 JAR SHA256：`F3725051FC555EE8A95B2E2B11CE0A4C4E37CC7EA6C4E94182163E6456154546`。Epic Fight 1.20.1 与 Lodestone 公开源码仅作架构研究；具体来源、许可证和未认证范围见 [skill 证据](skill/minecraft-trails-beams/references/evidence.md)与[来源说明](skill/minecraft-trails-beams/references/sources.md)。

实机结束后测试实例被另一任务重新启动；为了避免运行中改配置，本次 storm `config/trailbeam/recipe.json` 与 `path.json` 暂留。原配置已备份在 `trail-beam-lab/evidence/preflight-expanded-20261002-094706/`，已向同实例任务说明。最终 JAR 已在客户端关闭时部署。

## 2026-09-29：基础内核与第一次游戏验收

日期：2026-09-29。目标：Minecraft **Forge 1.20.1 / Java 17**，本机验证 Forge **47.4.10**。

技能名：`minecraft-trails-beams`。个人安装位置：`C:/Users/MAINTAINER/.codex/skills/minecraft-trails-beams/`。工作区源文件：[SKILL.md](skill/minecraft-trails-beams/SKILL.md)；完整参考工程：[trail-beam-lab](trail-beam-lab/README.md)。

## 自由设计能力

| 维度 | 已实现的可编辑能力 | 更复杂设计的扩展入口 |
|---|---|---|
| 轨迹 | 双挂点刃根/刃尖、单点历史、任意世界路径点列 | 实际动画/骨骼挂点、Bezier/寻敌/分叉路径生成器 |
| 截面 | blade、相机 ribbon、cross、开口 tube | 自定义 mesh、封端、扇面、多棱形 |
| 宽度 | 沿程分段线性曲线、收尾/鼓包 | Java Curve 及速度/蓄力函数 |
| 颜色 | 任意 HEX 多段色带、每节点 alpha | GLSL 时间色带、Cosmic/噪声场 |
| 材质 | 多层 alpha/additive、软边/双边/分节、任意资源纹理 | 符文、侵蚀、折射、独立发光 pass |
| 时间 | 秒单位寿命、年龄渐隐、正反向纹理流动 | 节拍脉冲、分叉刷新、确定性抖动 |
| 接入 | Effects.sample / Effects.beam、客户端生命周期与清理 | 第一人称、骨骼动作库、服务端触发与命中 |

青色刀光、紫青束和金色螺旋是三个起点，不是风格限制。JSON 字段必须由解析与渲染真实消费；超出既有字段的需求通过扩展代码实现，不加无效开关。生成器拒绝覆盖现有工程，模板无 Lodestone / Veil / Photon 依赖。

调用示例：

> 用 minecraft-trails-beams 做一条黑金符文弯曲束：暗色半透明外壳、细金色核心，沿路径三段鼓包，符文反向缓慢流动，末端渐细；接入 Forge 1.20.1。

## 构建与验收

- 配方/生成器：19 项检查通过。
- 纯几何/历史/解析：44 项断言通过。
- 生产 `build`、`reobfJar`：成功。
- 实机：ASTRA-TEST / ASTRA_VFX_TEST，RTX 4060 Ti。
- 最终跑次：`20260929-084031`，854×480，5 场景、25 项记录检查通过，并逐类看图。
- 各截图采样点 GL error=0，无遮罩；shader generation 1→2；固定时间材质区域重载前后 0 像素差；关闭几何后的背景正常。
- 模板 27 个文件与构建工程对应一致；安装结果记录在 `trail-beam-lab/evidence/skill-installation.json`。

最终 JAR：[trail-beam-lab-0.1.0.jar](trail-beam-lab/build/libs/trail-beam-lab-0.1.0.jar)。SHA256：`85a598c7ff91507a7cc3c72de0e493975093a7217755a41e7fd3a620f8fbe865`。

| 证据 | 入口 |
|---|---|
| 重载后展示 | [reloaded.png](trail-beam-lab/evidence/20260929-084031/reloaded.png) |
| 世界空间效果 | [world-effects.png](trail-beam-lab/evidence/20260929-084031/world-effects.png) |
| 五场景记录 | [results.json](trail-beam-lab/evidence/20260929-084031/results.json) |
| 图片与记录检查 | [runtime-validation.json](trail-beam-lab/evidence/runtime-validation.json) |
| 构建及断言 | [build-final.log](trail-beam-lab/evidence/build-final.log) |
| 完整边界 | [evidence.md](skill/minecraft-trails-beams/references/evidence.md) |

## 已修正的实际问题

ShaderInstance 会应用 JSON 的混合状态，早期仅配置 RenderType 导致透明边缘错误；现在 alpha/additive 使用各自一致的 shader 配置。GUI 的布局与绘制深度也已修正。

重载验证曾截到 Mojang 淡出遮罩，虽然当时 GL error=0、shader 代次已更新；修正后等待 overlay 退出，并比较固定画面和控制背景。`20260929-083101` 保留作为被新版验证器拒绝的跑次。早期失败不计入最终通过结论。

同实例任务已按用户授权协调为顺序部署/测试；完成后将客户端交回场景窗口任务，本任务不再修改实例。一次启动超时后客户端迟到进入标题界面，经 MCP-HELPER 画面确认后选择已有 ASTRA_VFX_TEST；未新建世界。

## 适用边界

这次证明参考渲染内核在 Forge 1.20.1 实际绘制成功。示例刀刃由数学函数产生两挂点，不能说明任意武器已经精确贴合；实际模型、骨骼和第一人称需要单独 adapter。自然消散有核心行为检查，`expired.png` 是主动关闭几何的控制图，不是自然消散录像。光影包、联机伤害、动态照明、屏幕 bloom 和性能矩阵尚未认证。

使用方式与本机 JDK 构建命令见工程 README；可复用契约、设计方法和 Forge 接入都保存在已安装 skill 内，无需依赖本机证据文件才能生成新工程。
