# 局部环境材质化渲染：研究与实机交付

2026-09-29 · 目标 Minecraft Forge 1.20.1 / Java 17

## 0.2.0：可选水面与透明区块材质化

已增加真正位于原 `RenderType.translucent()` 区块绘制中的材质函数，不在最终屏幕覆盖星图。原 VBO、流体高度/坡度、流动 UV、alpha、排序和深度状态继续使用；可以与地形后端一起开，也可以只改水面验证表面位置。

| 模式 | 当前能力 |
|---|---|
| 基础（默认） | 保留原透明表面；水下地形可以改变 |
| 材质化（可选） | 原版水 sprite 或显式全部透明区块层，已实测 Cosmic、水墨、水/玻璃叠层 |
| 完全异化 | 已形成折射/介质/分层/波形架构；运行时明确拒绝，尚未实现 |

Forge 47.4.10 / RTX 4060 Ti / Fancy 实测接受 **15 份原透明 VBO 同帧对照**，alpha 附件差异、depth 差异、GL error 全部为 0。只水模式下纯玻璃画面 RGB 改动为 0；启用透明区块层后同视图改变 902,982 像素。区域完全位于可见表面之外时 RGB 改动为 0。水面单独材质化、流水侧面、水下、相机平移/旋转、重载和缩放都留有画面及读回。

方案与边界见 [透明表面三级方案](skill/minecraft-local-environment-material/references/transparent-surfaces.md)，详细结果见 [0.2.0 透明层验收](skill/minecraft-local-environment-material/references/surface-validation.md)，对照图见 [水面模式对照](research/local-environment/surface/evidence/surface-comparison.png)。

这是受限原版区块后端：`water` 识别的是 still/flow/overlay 的 atlas sprite，不是通用流体 ID；`layer` 也会处理染色玻璃。其他 Mod 的标准 chunk 流体属于条件适用，自绘流体、Embeddium、Oculus/Shader Pack 均未认证。Fabulous 主动回退。没有新增物理折射或真实波浪位移，水下雾保留原版。保持透明度意味着仍会看见水下背景，不能要求最终颜色与不透明岸边逐像素相同。

当前构建为 `local-environment-lab-0.2.0.jar`，已经安装在 ASTRA-TEST；默认关闭效果。使用 `/localmaterial hold 0` 再 `/localmaterial surface material water` 启用，`/localmaterial off` 关闭。完整源码同步至 Skill 资产及本机安装目录。新 JAR SHA、源指纹、15 份探针摘要见对应 JSON 证据。

测试前保存/备份存档。临时水槽在已扫描全空气的位置建立，清理后复查非空气方块为 0；玩家位置、视角、creative 模式、昼夜/天气恢复，效果关闭并退出 Helper 控制。0.1.0 源码与 JAR 保留在 `research/local-environment/history/0.1.0/`，下方是原地形研究记录。

## 原地形研究结论（0.1.0 历史记录）

该目标可实现。已经从四份参考源码提炼方法，完成独立数学/GPU 验证，写出原创 Forge 地形后端，并在获准的 ASTRA-TEST / ASTRA_VFX_TEST 成功运行。草地、树干、树叶和沙地保持原几何与位置，世界球形范围内的表面可被 Cosmic、水墨或熔岩材质函数替代。

核心是 **原场景表面数据 → 世界空间影响场 → 对象资格 → 可替换材质 → 分层合成**。它不需要门户或另一个世界；全屏三角是计算原可见表面的载体，影响范围与材质取样由世界位置决定。

正式 Skill：[minecraft-local-environment-material](skill/minecraft-local-environment-material/SKILL.md)。源码工程：[local-environment-lab](local-environment-lab/build.gradle)。实机对照：[原环境 / Cosmic / 水墨 / 熔岩](research/local-environment/evidence/minecraft-contact-sheet.png)。

## 技术路线

| 后端 | 选择依据 | 边界 |
|---|---|---|
| A：地形阶段深度重建 | 只处理普通地形，先做可靠最小版本；Forge 使用 AFTER_CUTOUT_BLOCKS | 不包括此后绘制的实体、方块实体、透明层 |
| B：分类与辅助缓冲 | 需要准确区分对象，或消费 normal/light/category | 必须真的实现对应几何信息输出；不是创建附件就自动有数据 |
| C：原绘制材质注入 | 需要精确 lightmap/AO/fog，或替换水/玻璃等透明层 | 按 draw/renderer 分别适配；不能从最终单深度恢复多层透明场景 |

长期 Skill 允许混合三种后端。0.1.0 实机认证的是 A 的单球演示；0.2.0 补充了上述受限 C 透明区块实现，通用 B 与其他 C adapters 仍是工程路线。

## 四个参考项目的贡献

- **Veil**：主要研究 framebuffer/附件所有权、相机与逆矩阵、动态缓冲生产者、后处理调度和多阶段资源流。
- **Veil Example Mod**：追踪 space_test 的 depth→位置链路，以及 height_fog 的空间求交与稳健性问题。
- **Lodestone**：提炼边界 sphere/ring/拖尾、bloom、生命周期和透明阶段深度捕获；边界几何不冒充内部材质 Mask。
- **Immersive Portals**：仅研究 stencil/depth/状态作用域。未采用跨维度、portal world render 或门户深度清除方案。

源码快照版本、具体类名、差异和 44 份文件指纹见 [源码研究](skill/minecraft-local-environment-material/references/source-study.md)。这些项目本身没有在本轮逐个启动；本轮运行的是面向 Forge 1.20.1 独立实现的后端。

## 十一个问题的落点

| 用户关注的问题 | 提炼结论与资源 |
|---|---|
| 1. 深度恢复世界位置 | 同帧 inverse(P×Vrel)，齐次除 w；不要再次线性化深度。[坐标与区域](skill/minecraft-local-environment-material/references/coordinates-regions.md) |
| 2. 玩家/实体/命中点/方块/路径 | binding、double 世界坐标、维度、UUID、插值、固定/跟随语义独立定义；不拿第三人称相机当玩家中心 |
| 3. 六类 Mask | 统一 SDF/influence：球、有限圆柱、Box、平面、胶囊路径、稳定 noise；定义边界厚度与 bounds |
| 4. 世界固定 | CPU double 做中心−相机，GPU 使用相对空间；区域锚点和材质锚点独立 |
| 5. Cosmic 推广 | 可复用方向星层，也可改为三维/三平面世界粘附场；环境资格不再取物品纹理红通道。[材质与过渡](skill/minecraft-local-environment-material/references/materials-transitions.md) |
| 6. 扩散/侵蚀等 | 半径/阈值/噪声前沿与生命周期分离；edge emission、bloom、折射各自有合成规则 |
| 7. 不同对象 | 逐阶段处理地形、实体、BE、水/玻璃、粒子、天空、雾；单 depth 无法识别类别或恢复透明层。[架构](skill/minecraft-local-environment-material/references/architecture.md) |
| 8. 多区域 | 不可变帧快照、稳定优先级、显式 overlap、空间剔除；不为每个区域复制全屏 |
| 9. 安全清理 | 区域逻辑与共享 GPU 资源分离；generation、重载、resize、卸载、死亡策略与异常恢复矩阵 |
| 10. 性能与降级 | 覆盖像素×候选区域×材质成本；tile/LOD/路径简化、共享捕获与分辨率预算；初始预算不是 FPS 保证 |
| 11. 加速/光影兼容 | capability 检查与独立 adapter；未验证的 Embeddium/Oculus/Rubidium 在样例中禁用。[运行与性能](skill/minecraft-local-environment-material/references/runtime-performance.md) |

## 已执行的验证

- CPU：7 项检查通过，882 个深度往返样本；最大误差约 0.000189417 方块。
- 独立 GPU：12 个合成几何案例通过；覆盖六种区域、三种材质、重叠和实体代理选择，GL error 0。**该组图片不是 Minecraft 截图。**
- Forge：Java 17、Forge 47.4.10，生产 build/reobfJar 成功；构建没有 JUnit test 源码。
- Minecraft：RTX 4060 Ti、Fancy 地形后端，三种材质、相机转向、第三人称、FOV 100、resize、资源重载、扩张/收缩/到期、退出重进测试通过。
- 14 份接受的原生附件记录均为：**范围外改动 0、sky-depth 改动 0、深度差 0、GL error 0**。首个 2560×1440 记录有 2,776,044 个像素被材质化。
- 854×480 的增长/衰减记录：r≈4.48 时 43,035 像素，r=20 时 252,334 像素，r≈8.78 时 139,028 像素。到期后区域停止绘制。

原生范围检查使用同一捕获矩阵，有 0.002 格数值容差；不是独立原世界位置真值。综合源代码阶段核对、独立 GPU 真值、实机画面和原附件比较确认此后端。完整证据范围与复现步骤见 [验证说明](skill/minecraft-local-environment-material/references/validation.md)，机器可读结果见 [runtime-validation.json](research/local-environment/evidence/runtime-validation.json)。

## 0.1.0 交付边界与旧证据

水面专项复核：对照图中的水仍使用原版蓝色透明层，其下的地形已经替换材质，因此会出现“蓝色水 + 水下星点/熔岩裂纹”的混合观感。这属于当前地形后端的限制，没有将水面本身纳入材质化；原 14 份读回发生在水层绘制前，也不能作为水面材质验收。详见 [水与透明层复核](skill/minecraft-local-environment-material/references/water-transparency-limits.md)。

已交付可长期扩展的 Skill、三类后端的选择方法、八种材质家族的设计方向，以及可编译/实机运行的最小地形工程。

没有将以下内容冒充已完成的产品功能：Forge 多区域/路径管理器、通用对象 ID 附件、所有实体和 BE、透明层材质注入、精准 AO/lightmap、五个额外材质家族、世界内 bloom 实测、shader pack/加速兼容、多人网络、死亡/跨维度专项及 GPU 毫秒性能基准。它们已有方法、接口和验收要求，实施时仍须补对应生产者/后端。

本轮测试未修改方块或生成实体。FOV 已恢复为 70；效果已关闭，原存档重新进入并恢复正常显示，MCP 控制模式已退出。测试 jar 保留在 ASTRA-TEST，可用 `/localmaterial sphere 0` 再现；0/1/2 分别为 Cosmic、水墨、熔岩。原存档与 options 备份在 `research/local-environment/backup/20260929-090838/`。

被测生产 jar SHA-256：`55f73f4e5ccc9cebd4d79d1d665a03b33dd669c5e4866ae408e76b4907310311`。
