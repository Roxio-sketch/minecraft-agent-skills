# 奇幻材质 Forge 1.20.1 适配

工程：`E:/着色器渲染/cosmic-material-lab`，Minecraft 1.20.1、Forge 47.4.10、Java 17。`build.gradle` 的 processResources 从 skill 同一份 fantasy GLSL / recipes.json 复制到 shader include 与 `assets/cosmicmaterial/fantasy-recipes.json`，避免预览与游戏内核漂移。其他机器须获取工程，不假定此绝对路径存在。

## 已实现接口

```mcfunction
/give @s minecraft:diamond_sword{CosmicMaterial:{Material:"liquid-mithril",Mask:"blade"}} 1
/give @s minecraft:diamond_block{CosmicMaterial:{Material:"folded-ember"}} 1
```

Material 对应资源 JSON 中的 ID；十三份默认 ID 见 [fantasy-materials.md](fantasy-materials.md)。原 Palette 路径继续保留。预览导出的 JSON 不是 NBT，Java 不读取嵌在 NBT 中的任意 preview 参数。使用 `scripts/export_fantasy_pack.py` 创建资源包覆盖/新增配方，启用后正常重载，再用新 ID 给物品。mode 7–11 需要扩展后的生产 JAR；mode 12 水波还需六个水波 uniform 与记录字段，不能使用旧包。

`FantasyPresets` 在 RegisterShadersEvent 加载配方并校验数字范围、颜色、整数和 ID；材质不可变记录与显示上下文、sprite atlas rect 一起组成 RenderType 缓存键。实际 draw 时绑定独立参数，资源重载清理缓存并重建 shader。时间从游戏 tick+partialTick 转为秒，speed=0 时传0。

mode 0–5 使用 primary-sprite 开口 UV 加有界视图视差，方块每面可能重复图案。mode 6 使用 `fmFantasyWindow`，mode 7–12 使用 `fmNewInteriorWindow`；二者均使用共享屏幕射线和单一观察点，UV 仅限制覆盖。mode 6 的历史实测见 [internal-flow.md](internal-flow.md)，后续材质的实际结果另见交付文档。这不等于物体局部实体体积。多 sprite 遮罩、BEWLR、穿戴护甲和骨骼模型仍未认证。

原 sprite alpha 保留轮廓，红通道 mask 决定替换区域。当前 `Mask:"blade"` 专用于钻石剑，覆盖原青色区域并含柄尾的小块青色装饰，不是严格仅刀刃。需要保留全部护手/柄尾时另制明确的遮罩。普通无 NBT 物品保留原材质，原生 glint 使用原 ItemRenderer foil 路径。

## 构建和验证

从工程目录设置已有 JDK 17 后运行 `./gradlew.bat --offline --console plain build`（离线需完整缓存）。本机短临时目录选项见工程 README；不要关闭 TLS 校验。部署需使用 reobfJar 后的生产包。

`/cosmicmaterial fantasy` 打开六种材质的剑、方块、苹果和原生附魔样例。`/cosmicmaterial verifyfantasy` 仅允许 ASTRA_VFX_TEST 单人测试存档；临时修改背包、位置和显示选项，结束后恢复记录并删除测试实体。运行前备份存档。它验证有限样本，不代表全部装备兼容。

运行证据位于 `<gameDir>/cosmic-material-lab/fantasy-runs/<timestamp>`。使用工程 `scripts/validate_fantasy_runtime.py RUN --jar JAR --installed INSTALLED` 核查截图、每种时间变化、shader重载、上下文、满弓和恢复记录。先读 results.json 的 status，不得将失败跑次的部分 GUI 成功写成全通过。当前最终动态版的完成情况以工作区 `FANTASY_MATERIAL_DELIVERY.md` 和运行 evidence 为准。

`record_fantasy_motion.cjs` 的浏览器证据不替代 Forge OpenGL 运行。没有 Oculus/Iris、真实场景反射、动态照明或批量物品性能认证。

## FantasyBlock 方向的新增内核

`fantasy-flow / mode 6` 的共享视线生产 JAR 已于2026-09-30在 ASTRA-TEST / ASTRA_VFX_TEST 实测。跨面验收版本SHA-256：`19EC0448982D49A2382869A06CB30125CDA6914F7DA4E43C96012DE20D05A640`。2560×1421跨面验收见 `continuity-runs/20260930-030546`：32个案例、16682478个内部像素零差异。普通10场景/21项回归、shader重载与玩家恢复记录以工程交付文档的最新跑次为准。旧 `20260930-015445` 是逐面UV版本，不可作为跨面证据。

```mcfunction
/give @s minecraft:diamond_sword{CosmicMaterial:{Material:"fantasy-flow",Mask:"blade"}} 1
/give @s minecraft:diamond_block{CosmicMaterial:{Material:"fantasy-flow"}} 1
```

新的文件测试动作 `flow-run` 在六种GUI载体及第一/三人称、弓两状态、掉落与展示框中固定使用此材质。`flow-showcase` 打开六种载体的动态样例。普通 `fantasy-run` 继续执行先前六模式回归。mode 6的共享内核及相机差异见 [internal-flow.md](internal-flow.md)。

这一结果验证了接入成功；参考动画是本地改编的片元运行，不是 ArcaneVortex 原mod同场景对比。美术接近程度需要用户观看连续动画判断。

增加后台录制后的当时JAR是 `23A54B1E614A902FF4551FE9CB1556D97355EE9276F3334BFC46493CBAC19369`。当时普通回归目录为 `fantasy-runs/20260930-032312`，10场景/21项通过，满弓与重载通过，玩家5项恢复均为true。高分动态见 `motion-runs/20260930-032132`，2560×1440真实游戏时钟，动态49帧，前后冻结零差异，13项通过。上面19EC版本的32案例仍是固定时间跨面证据，不能称为连续动态测试。

星空分组仅合并了原authoring-lab的创作预览；没有因工坊合并而新增三种星空配方的Forge适配。

## 新材质的测试入口

文件桥接受 `{"action":"material-run","material":"aurora-veil"}`、`material-continuity-hd`、`material-motion-hd`，必须是有效 ID 且处于已授权 ASTRA_VFX_TEST。前者执行十个实际物品场景，后两者分别检查冻结跨面与真实时钟连续动态。工程 `scripts/run_material_suite.py` 顺序请求并遇失败停止；验证脚本在 skill 内，普通运行验证器在工程内。读取实际 framebuffer 尺寸，不能因为请求了 HD 就声称录到了 HD；全屏窗口可能不接受 `glfwSetWindowSize`。跨面仍要求每个内部像素零差异，运动仍要求前后冻结零差异。

mode7–11的新生产包SHA-256为 `B94C3F4E452AF2129C2CB4B587399C2BB1BA5CFD9E4F34ED51429118D0B6143A`。2026-09-30在ASTRA-TEST / ASTRA_VFX_TEST，每种10场景/21项普通回归与13项真实时钟动态均通过。新材质实际动态分辨率2560×1421，各自前后冻结零差异；详情及五种run目录见工程交付文档。没有因这些通过而关闭原Cosmic P3。测试驱动临时退出全屏后设窗口尺寸，并在结束时恢复；仍核对实际截图，不依请求值认定分辨率。

同版本五种高分跨面测试全部通过：2560×1421，每种两个冻结时刻各16种原生方块旋转，32案例/16682478个内部像素零差异。共160案例/83412390个内部像素，内部面边缘包含在内，仅排除外轮廓2px；证据汇总 `research/cosmic-material/styles-wave2/continuity-suite-verified.json`。它验证共享视线窗口，不能称作物体局部体积或连续动态。

## 可调水波 mode 12

`tidal-water` 的六字段与算法见 [water-material.md](water-material.md)。2026-09-30 新生产 JAR SHA-256：`A3CE4FBA314F20361FFB61823ADA2E8A4BB44EFDB10F6B8C2683ADF132A01E33`。ASTRA-TEST / ASTRA_VFX_TEST 普通10场景/21项通过，满弓与重载通过，玩家五项恢复均true；真实动态13项通过，2560×1421、52动态帧、前后冻结零差异；跨面32案例/16682478内部像素零差异。详情以工作区 `WATER_MATERIAL_DELIVERY.md` 为准。

水波普通跑次 `fantasy-runs/20260930-190731`，动态 `motion-runs/20260930-190907`，跨面 `continuity-runs/20260930-191045`。此前普通跑次190048尺寸不足，保留为失败。测试驱动现在用 `Window.setWindowed` 同步 Minecraft 的目标窗口尺寸，并核对实际 framebuffer；不以设置值代替采集尺寸。没有关闭原Cosmic P3，也没有新增穿戴、BEWLR或真实场景反射认证。
