# Cosmic Material Lab — Forge 1.20.1

这是从 Avaritia / Re-Avaritia Cosmic 方向场提炼的可运行物品材质实验，并加入 ArcaneVortex 式八角几何描边与穿戴防具、原版盾牌接入的原创 Forge 1.20.1 实验。Cosmic 物品基线于 2026-09-28 实测；描边及装备于 2026-10-02 在 AXO 的 ASTRA-TEST / ASTRA_VFX_TEST 中实测。描边的几何机制取自对原 JAR 的静态研究，shader 颜色函数是原创实现，未声称像素级复刻。

完整结果见 [运行验收报告](E:/着色器渲染/COSMIC_MATERIAL_RUNTIME_VALIDATION.md)，原始测量见 [results.json](E:/着色器渲染/cosmic-material-lab/evidence/20260928-235201/results.json)。

## 安装与试用

- Minecraft **1.20.1**，实测 Forge **47.4.10**，Java **17**。
- 当前 JAR：[cosmic-material-lab-0.4.0.jar](E:/着色器渲染/cosmic-material-lab/build/libs/cosmic-material-lab-0.4.0.jar)。只保留一个版本，安装后重启客户端。
- 当前已安装到 `D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/mods/`。本 mod 没有额外的材质库依赖；MCP-HELPER 仅用于此次启动和自动化检查。
- 使用客户端聊天命令 `/cosmicmaterial showcase` 打开不改背包的 12 格材质样例，Esc 关闭。
- 使用 `/cosmicmaterial outline` 打开真实 Forge 描边调参屏，成对预览六类物品，并实时调整宽度、模式和纯色 ARGB。
- 使用 `/cosmicmaterialequipment showcase` 预览四件防具、普通盾牌、纹章盾和未启用材质的对照；`/cosmicmaterialequipment verify` 在指定测试存档自动实测穿戴和举盾。
- 普通物品默认不变；`CosmicMaterial` 与 `OutlineMaterial` 是两个独立复合 NBT，前者启用内部材质，后者启用描边。

在允许命令的存档中可以手动运行以下 Minecraft 1.20.1 指令：

```mcfunction
/give @s minecraft:diamond_sword{CosmicMaterial:{Palette:"violet",Mask:"blade"}} 1
/give @s minecraft:diamond_block{CosmicMaterial:{Palette:"cyan"}} 1
/give @s minecraft:oak_stairs{CosmicMaterial:{Palette:"gold"}} 1
/give @s minecraft:bow{CosmicMaterial:{Palette:"violet"}} 1
/give @s minecraft:diamond_helmet{CosmicMaterial:{Palette:"violet"}} 1
/give @s minecraft:diamond_chestplate{CosmicMaterial:{Palette:"gold"}} 1
/give @s minecraft:diamond_leggings{CosmicMaterial:{Palette:"cyan"}} 1
/give @s minecraft:diamond_boots{CosmicMaterial:{Palette:"violet"}} 1
/give @s minecraft:shield{CosmicMaterial:{Palette:"cyan"}} 1
/give @s minecraft:shield{CosmicMaterial:{Palette:"cyan",ShieldStyle:"framed"}} 1
```

这是 1.20.1 的 NBT 语法，不是 1.20.5+ 组件语法。`Palette` 当前只有 `violet`、`gold`、`cyan` 三种实现；未知值回退原始蓝青配色。`violet` 是历史样例名称，实际继承参考 shader 的蓝青底色，并非固定紫色。`Mask:"blade"` 当前仅用于钻石剑，其他物品使用全覆盖。遮罩按原版剑的青色区域生成，包含小块柄尾；要精确限定刀刃，可编辑黑底白区的 `textures/mask/blade.png`。

## 穿戴防具、盾牌与预设工作台

0.4.0 将同一 Cosmic shader 用于玩家实际穿戴的头盔、胸甲、护腿和靴子，以及原版盾牌的 GUI、第一/第三人称和举盾姿态。只有带 `CosmicMaterial` 复合 NBT 的装备启用；普通装备保持原有材质。防具重用 `HumanoidArmorLayer` 的模型姿态和纹理，保留原生纹饰与 glint；盾牌重用原版 BEWLR 的模型、显示变换和 foil 路径。纹章盾以 Cosmic 替代不透明底色，再绘制原版图案层。

盾牌缺省为全覆盖；`ShieldStyle:"framed"` 让原版纹理中的金属边框留在 Cosmic 盾面之外，也适用于带纹章的盾牌。打开 [盾牌预设 HTML 工作台](E:/着色器渲染/cosmic-material-lab/assets/shield-presets/index.html) 可看四种组合、切换三套配色并复制对应 `/give` 指令。它是独立 Canvas 预览；真实效果和实测范围见 [装备运行报告](E:/着色器渲染/COSMIC_EQUIPMENT_RUNTIME_VALIDATION.md)。

## 八角几何描边

`/cosmicmaterial outline` 是游戏内真实模型预览；`skill/minecraft-cosmic-material/assets/outline-lab/index.html` 仍是二维配色草图，两者不是同一渲染器。描边由 `OutlineMaterial` NBT opt-in，例如：

```mcfunction
/give @s minecraft:diamond_sword{OutlineMaterial:{Width:0.008f,Type:0,Color:-11147521}} 1
```

Width 是模型空间位移，GUI 加 0.0125，第一人称保持原值，其他场景加 0.0035；Color 是 AARRGGBB 有符号整数。Type 0 为纯色、1 为原创星海、3 为原创三色流动、4 为整体彩虹。Type 2 的原 JAR 屏幕折射及 Type 5 原纹理色尚未接入。完整方法、实测范围与边界见 [描边运行记录](E:/着色器渲染/skill/minecraft-cosmic-material/references/item-outline-runtime.md)。

2026-10-02 的 [真实客户端截图](E:/着色器渲染/cosmic-material-lab/evidence/outline-20261002/showcase-final.png) 与 [10 场景结果](E:/着色器渲染/cosmic-material-lab/evidence/outline-20261002/runtime/results.json) 可复核。重新检查已保存证据：

```powershell
C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe scripts/validate_outline_runtime.py
```

## 构建

在本目录设置 JDK 17 后运行：

```powershell
.\gradlew.bat --console plain build
```

工程固定 ForgeGradle 6.0.54、Gradle 8.8、official 1.20.1 mappings 和 Forge 47.4.10。`jar` 后执行 `reobfJar`。不要拿开发环境中未重混淆的类当作启动器生产包。

本机重建使用：

```powershell
$env:JAVA_HOME='D:\MC\meta\java_versions\mojang-java-runtime-gamma-windows-x64-89ce85ccb518'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/Users/MAINTAINER/.codex/tmp/cosmiclab -Dfile.encoding=UTF-8'
.\gradlew.bat --offline --console plain build
```

`JAVA_TOOL_OPTIONS` 的短临时路径是本机环境设置，不是模组依赖。最后一次在线重建遇到 Forge Maven 证书检查失败，使用完整的本地缓存离线成功；没有关闭 TLS 校验。新机器首次构建需要可用网络和依赖下载。

## 实现入口

| 文件 | 职责 |
|---|---|
| `CosmicModels.java` | 在烘焙后包装 inventory 模型，保留 transform、overrides 和原 quads；按 NBT 启用 |
| `CosmicRender.java` | 注册 shader，获取 10 sprite 与 mask 的 atlas UV，缓存材质 RenderType，在实际 draw 绑定参数 |
| `CosmicEquipment.java`、两个 Mixin | 将 shader 接到穿戴防具层与原版盾牌 BEWLR，保留原几何、姿态、变换及纹章 |
| `cosmic.vsh / cosmic.fsh / cosmic.json` | NEW_ENTITY 格式、视线方向、16 层图案、三种配色、原 alpha 与红通道 mask 融合 |
| `OutlineRender.java / outline.vsh / outline.fsh / outline.json` | 八角平移 pass 的材质、atlas alpha 裁边和 draw 时配色 |
| `OutlineScreen.java` | 使用实际 Forge renderer 的描边调参屏 |
| `CosmicAudit.java` | 可选展示屏和测试存档专用验收驱动，不属于材质算法 |
| `EquipmentAudit.java` | 装备展示与九场景验收，结束后恢复玩家装备、位置、模式及显示选项 |

Cosmic 材质走原几何单 pass，采用 LEQUAL 和颜色/深度写入；不是参考工程的基础层加 EQUAL 覆盖层。基础纹理 alpha 决定 cutout 轮廓，mask 红通道决定 Cosmic 覆盖比例。描边另以八角平移 quads 绘制，排在基础 pass 前，使原纹理盖住内部。内置附魔光效仍由 ItemRenderer 的 foil 路径绘制。

## 实测与复跑

`/cosmicmaterial verify`、`/cosmicmaterial verifyoutline` 和 `/cosmicmaterialequipment verify` **只接受名为 ASTRA_VFX_TEST 的本地单人存档**。它们临时修改背包、模式、位置和显示选项；物品脚本还生成两个掉落物与一个展示框。完成后恢复记录的玩家状态并移除自己创建的实体。复跑前备份测试存档；自动获得物品可能解锁配方/进度，整个存档并非字节级回滚。

运行输出在 `<gameDir>/cosmic-material-lab/runs/<时间>/`。最终完整跑次为 `20260928-235201`。独立证据检查：

```powershell
python scripts/validate_runtime.py
```

这个历史脚本检查 2026-09-28 的 Cosmic 运行记录、10 张截图、时间变化和当时 0.1.0 的生产包哈希；当前已部署 0.4.0，因此其旧部署身份检查不能直接在当前实例通过。它不会启动游戏，也不代替人工看图。描边的 0.2.0 检查使用上文的 `validate_outline_runtime.py`；0.4.0 装备检查见装备运行报告。

也可向 `<gameDir>/cosmic-material-lab/request.json` 写入 `{"action":"outline-run"}`、`{"action":"outline-showcase"}`、`{"action":"run"}` 或 `{"action":"showcase"}`。这是本实验的本地文件桥，客户端读取后删除请求文件。不要以 MCP 的 server `execute_command` 来替代客户端命令分派。

装备验收单独读取 `<gameDir>/cosmic-material-lab/equipment-request.json`，其中 `{"action":"run"}` 或 `{"action":"showcase"}` 分别启动自动验收和展示屏。输出在 `<gameDir>/cosmic-material-lab/equipment-runs/<时间>/`。

## 已支持与边界

Cosmic 基线实测样本包括钻石剑、斧、苹果、弓、钻石方块和橡木阶梯。完成 GUI、第一人称左/右主手、第三人称正/背视角、掉落物、固定展示框、满弓、原生 glint、三配色同屏和资源重载检查。描边 0.2.0 也完成六种 GUI 物品及十个相同场景的独立运行；不继承 Cosmic 专有的三配色与 glint 验证。

0.4.0 已增加原版盾牌 BEWLR 与使用 `HumanoidArmorLayer` 的穿戴防具接入；**其他** `isCustomRenderer=true` 物品、第三方盔甲层和骨骼动画仍保留原路径。边框预设基于原版盾牌纹理的中低亮度中性色像素识别；更换盾牌贴图的资源包需要重新验收。第三方半透明、多 pass、带特殊 RenderType 过滤的模型，以及 Oculus/Iris 光影兼容均未认证。第三人称左手、弩的所有状态、改变 FOV/GUI 比例和资源包改变 atlas 排布不在本轮证据内。没有 FPS 或 GPU 耗时基准。

星体素材与 shader 来源和许可见 [THIRD_PARTY_NOTICES.md](E:/着色器渲染/cosmic-material-lab/THIRD_PARTY_NOTICES.md)。五份用户参考源码保持原样；本实验不代表原项目均已构建。
