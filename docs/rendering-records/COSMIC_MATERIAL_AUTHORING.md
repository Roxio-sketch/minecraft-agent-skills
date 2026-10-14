# Cosmic：从文字描述到内部星空材质

本轮已把工作从“替换物品材质并保证能画出来”推进到“选择内部空间结构、制作星体/星云、调出可讨论的效果”。已有 Forge 1.20.1 接入基线继续保留；新增创作效果尚未接入游戏，不沿用旧运行认证。

## 现在可以怎样描述

你可以直接说：

> 剑刃里是一片很深的紫蓝宇宙，中段有断续的冰蓝云带，暗尘遮住一部分后面的亮光。近处只要几颗较亮的星，转动时前后会错开，流动很慢。金属外缘和握柄保留。

skill 会先确定内部的空间结构、构图、发光/遮挡层、视角响应、动态节奏和覆盖区域，再制作材质初稿。不会要求你预先给出 GLSL 参数，也不会把所有效果都简化成同一套星点换色。

更像无尽装备的方向星域、更厚的局部星云、无限延伸的分形空间、带主天体的旋涡，应选择不同算法。支持自然语言设计意味着能够完成这种选择和补充实现，不意味着一个现成 JSON 已经具备所有想象中的效果。

## ArcaneVortex 着色器方块的学习结果

输入是你提供的 `ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge.jar`。读取了 GLSL、注册与 renderer 字节码，没有安装或执行该模组。

| 已追到的调用/研究对象 | 学到的方法 | 用于哪类描述 |
|---|---|---|
| StarShaderBlock → star_sky | 沿射线采样折叠/反演分形，按距离累计与衰减 | 无限延伸、细节里还有细节、密集但有深暗背景 |
| VolumetricBlock → volumetric_shader | 方向驱动的程序空间、变步幅、颜色累计 | 内部在缓慢展开、流动，具有非平面结构 |
| FantasyBlock → chaos / useType=1 | 先设薄层大形，再用多尺度扰动形成云带 | 褶皱云层、能量裂隙、明暗相间的空间 |
| nebula_cube（调用链未闭合） | 包围盒求交、密度与边缘、场景深度合成 | 有明确边界、遮挡与体积的云团 |
| starfield / cosmic 的模式代码 | 把主星、普通星、彗星、泡泡、符文等分成模块 | 改形状与结构，不仅改变整张画面的颜色 |

关键发现：不能凭 shader 名称猜实际效果。FantasyBlock 实际使用 chaos 云层；StarShaderBlock 使用 star_sky 分形，而非 starfield。`nebula_cube` 虽已注册，本次未确认其实际调用者。

四个参考分支已放入独立 WebGL 环境观察，调整了版本声明/精度并提供合成坐标与矩阵；RTX 4060 Ti 下均编译、链接并出图，GL error=0。以下是**改编片元的独立观察图，不是 Minecraft 方块截图**。

![ArcaneVortex 片元结构研究](research/cosmic-material/authoring/arcanevortex/study-sheet.png)

- [详细源码与绑定分析](skill/minecraft-cosmic-material/references/arcanevortex-study.md)
- [输入哈希、150 个选取文件的来源](research/cosmic-material/authoring/arcanevortex/manifest.json)
- [四分支的编译与观察记录](research/cosmic-material/authoring/arcanevortex/previews/validation.json)

包内许可为 All Rights Reserved。研究素材留在本地证据目录；已安装 skill 不包含该 JAR 的 shader 源码、原素材或字节码。

## 独立的材质制作台

新增了原创、可调的制作台：深空星海、紫蓝星云、赤金裂隙三份练习。支持星点数量与尺寸、星云与暗尘、虚拟深度与视差、空间尺度、速度、微闪、曝光和四类配色；可以关闭各层排查、暂停时间、拖动视角、切换示意剑刃窗口，并以真实 16/32 px 分辨率重渲染。

![原创星云材质练习](research/cosmic-material/authoring/evidence/violet-nebula.png)

这份原创内核用四个星点深度层与简化发射/吸收组织前后关系，适合讨论材质，不是 ArcaneVortex shader 的重打包。工作台导出的 JSON 是制作参数，当前 Forge JAR 不会读取它。

启动方法见 [制作台说明](skill/minecraft-cosmic-material/references/authoring-workbench.md)。浏览器验证通过 17 项：编译/链接、稳定状态、动画停启、视差关闭/开启、暗尘后景衰减、区域遮罩、小图、质量档与导出等。自动检查不替代美术判断；小图里的细星可能不再可辨，需要按载体继续调构图或加入 GUI 专用细节策略。

- [制作台验证记录](research/cosmic-material/authoring/evidence/validation.json)
- [制作台完整界面](research/cosmic-material/authoring/evidence/workbench.png)
- [原创 GLSL 内核](skill/minecraft-cosmic-material/assets/authoring-lab/material.glsl)
- [示例参数](skill/minecraft-cosmic-material/assets/authoring-lab/recipes.json)

## 已提炼进 skill

项目副本在 [skill/minecraft-cosmic-material/SKILL.md](skill/minecraft-cosmic-material/SKILL.md)，安装副本在 [已安装 SKILL.md](C:/Users/MAINTAINER/.codex/skills/minecraft-cosmic-material/SKILL.md)。主要新增：

- [内部星空制作方法](skill/minecraft-cosmic-material/references/material-authoring.md)：空间选择、构图、星点、星云、暗尘、深度、颜色、坐标与缩小处理。
- [描述与配方](skill/minecraft-cosmic-material/references/material-recipes.md)：将“更深、更多、缓慢、近处亮星、向中心流动”等变成具体制作动作；区分可运行参数与待实现结构。
- [ArcaneVortex 研究](skill/minecraft-cosmic-material/references/arcanevortex-study.md)：方块的实际绑定、各算法角色、可学习之处和移植注意点。
- [制作台与 Forge 接入契约](skill/minecraft-cosmic-material/references/authoring-workbench.md)：从初稿到实际物品时需要补上的坐标、参数、采样和绘制状态。

两份 skill 的 17 个文件内容一致。原五份 ZIP 现位于桌面的“渲染”目录，静态验证脚本新增 `--archive-dir` 以显式指定资料位置；不改变已有来源哈希。当前复核命令为 `python research/cosmic-material/validate_artifacts.py --archive-dir C:/Users/MAINTAINER/Desktop/渲染`。

## 验证范围

1. 旧 Cosmic Material Lab：Forge 1.20.1 / 47.4.10 的 baked-item 材质路径已有真实游戏证据，见 [原验收报告](COSMIC_MATERIAL_RUNTIME_VALIDATION.md)。
2. ArcaneVortex：JAR 静态研究 + 四个改编片元的 WebGL 观察；没有本轮原模组游戏实测。
3. 新制作台：WebGL 编译、行为与画面检查；没有本轮新材质的 Forge 接入或性能基准。

接下来你描述具体物品和内部场景时，可以直接调用这个 skill 制作初稿，再将选定设计接入已有 Forge 框架并针对新效果实测。
