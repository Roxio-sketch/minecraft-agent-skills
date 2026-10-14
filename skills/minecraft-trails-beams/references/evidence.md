# 当前验证范围

## 2026-10-02 工作台与分叉路径扩展

工作区新增完全离线的 HTML/Canvas 工作台、七种可编辑起点、六类 Java/JS 路径和最多四个分叉、`pulse/plasma/rune` 三种 GLSL 图案。导出的 `recipe.json`、`path.json` 分别由 Forge 示例的 `Recipe`、`Paths` 真正解析。游戏实例通过 ASTRA-TEST / ASTRA_VFX_TEST，Forge 47.4.10、Minecraft 1.20.1、RTX 4060 Ti。

| 层级 | 结果 |
|---|---|
| 生成器/契约 | 24 项通过，含路径校验、默认路径与项目输出 |
| 离线工作台 | Edge + Playwright 33 项通过，检查七起点、参数操作、导入导出与非空画布；人工看过螺旋与分叉预览，修正了早期过曝 |
| Java 内核 | `TRAIL_BEAM_CORE_CHECKS=50 passed`，包括确定性路径/分叉预算和新 profile 解析 |
| Forge 生产构建 | Gradle 离线 `build` 成功，执行 reobfJar；模板 `src` 20 文件与构建工程哈希一致 |
| 游戏截图 | 跑次 `20261002-094831` 五场景，2560×1440；25 项记录检查通过，各截图采样点 GL error=0、overlayVisible=false |
| 实际设计 | 从工作台 storm 起点导出配置，游戏第三展示槽与世界空间出现蓝白分叉雷束；重载 generation 1→2，固定时间画面匹配 |

运行截图保存在 `E:/着色器渲染/trail-beam-lab/evidence/20261002-094831/`；浏览器截图在 `.../evidence/workbench/`；各项记录为 `runtime-validation-expanded.json`、`build-expanded-final.log`、`source-comparison-expanded.json`。实机使用的 JAR SHA256 是 `08849EA571740D3F177BD1084DAED8508D6B75EAE6B03176A6AB795151576A5E`。看图后仅把画廊文字 `HELIX` 改为动态显示实际路径类型，再做一次成功构建；当前生产/实例 JAR SHA256 是 `F3725051FC555EE8A95B2E2B11CE0A4C4E37CC7EA6C4E94182163E6456154546`。**这次最终文字变动后的 JAR 没有再单独启动截图**，不将两次 JAR 误称逐字节相同。

观察范围是 storm 分叉束及原有刀光/双层束；七个工作台起点都经过浏览器操作和数据校验，但没有逐个做游戏截图。实际武器骨骼、第一人称、寻敌碰撞、光影包和性能矩阵仍未认证。工作台 Canvas 不是 Minecraft GLSL 的逐像素复现。

## 2026-09-29 原始内核与材质基线

目标是 **Minecraft 1.20.1 / Forge 47.4.10 / Java 17**。下面是本技能自身的证据，不继承 Cosmic 或其他技能的认证。

## 已通过

| 层级 | 实际结果 |
|---|---|
| 配方与生成器 | 19 项通过；包含非预设 HEX 色带、宽度节点、反向流动、未知/无效字段拒绝、输出目录保护 |
| 几何/解析内核 | coreCheck 的 44 项断言通过；含采样细分、容量、重复时间、倒退、瞬移断带、停发后过期、UV 淘汰连续、重复点与端向视角退化、双挂点保持 |
| Forge 构建 | `gradlew --offline --console plain build` 成功，包含 coreCheck 与 reobfJar |
| 游戏 | ASTRA-TEST / ASTRA_VFX_TEST，RTX 4060 Ti，五场景，最终跑次 `20260929-084031` |
| 实机记录检查 | 25 项通过；最终 854×480，每个截图采样点 GL error=0、overlayVisible=false |
| 世界绘制 | 双挂点解析刀光、cross 曲线束、tube 螺旋束可见；历史点数 40 |
| 资源重载 | Shader generation 1→2；固定时间材质区域重载前后 0 像素变化、最大通道差 0 |
| 关闭几何 | 控制图无效果残留，背景相同；失焦暂停设置恢复 |
| 模板同步 | 随附模板 27 个文件与实际构建工程对应文件一致（生成配方按 JSON 内容比较） |

实际看图确认：刀光没有被展示页裁切，alpha 外壳与 additive 核心具有可见柔边，金色管形束连续；世界截图中紫色束末端受山体遮挡。这是单场景观测，不代表完整透明排序或遮挡矩阵。

## 证据位置

本机工作区：`E:/着色器渲染/trail-beam-lab/evidence/`。

- `20260929-084031/`：最终五张原始截图、results.json、筛选日志。
- `runtime-validation.json`：25 项结果及图片 SHA256。
- `build-final.log`、`authoring-validation.json`、`source-comparison.json`：构建、生成器、模板对应关系。
- 工作区 `E:/着色器渲染/TRAIL_BEAM_SKILL_DELIVERY.md`：交付和复用入口。

最终生产 JAR SHA256：`85a598c7ff91507a7cc3c72de0e493975093a7217755a41e7fd3a620f8fbe865`。

测试资料不装进 skill，避免缓存、截图、实例路径成为生成工程依赖。证据文件不存在时，以本页记录为历史范围；新修改应在当前机器重新验收。

## 实測中修正的缺陷

1. 早期 shader JSON 未正确设置混合，RenderType 的透明状态被覆盖；改为两个 shader 配置分别匹配 alpha/additive。
2. GUI 刀光裁切与管形束被底板遮挡；改为按窗口尺寸布局并在正确 GUI 深度绘制。
3. 资源重载 future 完成时 Mojang 遮罩仍在淡出，导致截图误判；现在等待 overlay 退出，检查固定材质区域和背景。旧跑次 `20260929-083101` 已被新版验证器明确拒绝，不能作为完整验收结果。

同实例并行换包曾导致类加载/ZIP 读取失败，后来在顺序部署后正常。保留失败记录，不把它们描述为已经被完整定位的 shader 问题。启动超时也不代表客户端不会迟到启动；部署前需再次确认进程状态。

## 尚未认证

- 世界刀刃挂点来自数学函数，**没有认证任何实际武器、GeckoLib 骨骼或左右手/第一人称适配器**。它们通过 Effects 接口实现后再看图。
- `expired.png` 主动关闭几何；自然消散由 CPU 行为检查覆盖，没有独立的自然消散 GPU 录像。
- 纯内核覆盖 ribbon/cross/tube/blade；游戏示例重点覆盖 blade/cross/tube。
- 没有专用服务端启动、联机同步/命中、光影包、端向/透明自交完整矩阵或 30/60/144 FPS 的游戏性能认证。
- 没有内置 bloom、场景动态照明、封闭管端、自动寻敌、完整模型残像或任意武器自动绑定。

这些边界不限制设计方向；需要时按 authoring.md 扩展对应接口，添加该需求的实际验证。
