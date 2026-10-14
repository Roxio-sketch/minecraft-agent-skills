# Colored Glint Lab — Forge 1.20.1

独立彩色附魔流光实验模组。以 RenderBlender 的 9 色纹理为素材，在原生 ItemRenderer 的 foil 调用处按物品分派颜色，保留原几何与模型 override。目标环境为 Minecraft 1.20.1 / Forge 47.4.10 / Java 17。

## 试用

安装 `build/libs/colored-glint-lab-0.2.0.jar` 后重启客户端。

- `/coloredglint showcase`：打开 12 格实时展示，不修改背包；Esc 返回游戏。
- `/coloredglint gradients`：展示玫瑰金单色、极光空间渐变、6 秒彩虹循环、4 秒余烬流动及 Cosmic 组合。
- 普通物品默认不变。配置键是 `ColoredGlint`，不同于 RenderBlender 旧快照的两个 NBT 键。
- 红、蓝、紫、绿、青等共九色可配置；`default` 和未知 ID 回退原分支。未知 ID 的 Force 也无效。
- 新增 ID：`rose_gold`、`aurora`、`rainbow_cycle`、`ember_wave`。源配方在 `recipes/`；HEX 和色标由 skill 生成器处理，物品 NBT 的 `Color` 仍使用注册 ID。

以下为 Minecraft 1.20.1 命令：

```mcfunction
/give @s minecraft:diamond_sword{ColoredGlint:{Color:"red",Force:1b}} 1
/give @s minecraft:diamond_sword{Enchantments:[{id:"minecraft:unbreaking",lvl:1s}],ColoredGlint:{Color:"blue",Force:0b}} 1
/give @s minecraft:diamond_sword{CosmicMaterial:{Palette:"cyan",Mask:"blade"},ColoredGlint:{Color:"purple",Force:1b}} 1
/give @s minecraft:diamond_sword{ColoredGlint:{Color:"rainbow_cycle",Force:1b}} 1
/give @s minecraft:diamond_sword{ColoredGlint:{Color:"aurora",Force:1b}} 1
```

含 `CosmicMaterial` 的命令需要项目已有的 Cosmic Material Lab；其余不依赖它。Force=false 时原本附魔的物品仍可选色；没有附魔也没有 Force 则不增加流光。

## 构建与实现

JDK 17 下执行 `gradlew.bat --console plain build`。已有完整依赖缓存时可加 `--offline`。产物执行 `reobfJar`，Mixin AP 使用 ForgeGradle 的 `createMcpToSrg/output.tsrg` 生成发布用 refmap。启动失败时核对 JAR 的 `MixinConfigs` 清单项和内置 refmap。

- `GlintStyle.java`：只读解析有效颜色和 Force；不修改查询到的 ItemStack。
- `mixin/ItemRendererMixin.java`：在有 stack 参数的实际调用点包装四个 native foil helper，并在该 render 内组合原生 hasFoil 与 Force。没有静态 current-stack、ThreadLocal 或需要恢复的全局颜色。
- `GlintLayers.java`：11 套静态配方 × 5 种模式，加 2 套动态配方 × direct，共 57 个固定层。保留纹理矩阵、混合、EQUAL 深度与输出目标；顶点格式为 POSITION_TEX。
- `GlintShaders.java`：两套动态 shader 在资源加载时注册、重载时更换，真实 draw setup 时设置各自周期时间。世界 tick + partial tick 计时，单机暂停时保持，用 double 取周期余数后转 float。
- `mixin/RenderBuffersMixin.java`：在默认 RenderBuffers 构造完成后登记固定缓冲；彩色层在基础层之后 flush。
- `GlintAudit.java`：可选客户端展示、请求文件桥及专用测试存档验收，不属于材质算法。

使用其他模组自建的即时 BufferSource、穿戴护甲、BEWLR 或第三方骨骼渲染器需要额外适配。五个模式的缓存存在不等于五条分支均有实测证据。

动态 shader 当前仅替换 direct；其余分支保留原版流光。空间/流动渐变使用滚动的 glint UV，不能当作固定剑柄到剑尖的配色。

## 本地实测

本次在 AXO `ASTRA-TEST` / `ASTRA_VFX_TEST` 完成 12 个截图场景。详细证据见项目根的 `COLORED_GLINT_RUNTIME_VALIDATION.md` 和 `evidence/20260929-011858/`。

`/coloredglint verify` 只允许上述本地测试存档，会临时修改背包、位置、模式、视角和显示设置，并创建三个测试实体。退出验收时恢复玩家状态、移除自建实体；运行前备份存档。获得物品可能解锁配方/进度，正常世界时间也会推进，不承诺整个存档逐字节回滚。

`/coloredglint verify_gradients` 使用相同恢复机制验收四套新配方，增加完整周期时间采样和暂停/续播，共 15 个场景。先备份，证据与旧九色分开。

本地文件桥为 `<gameDir>/colored-glint-lab/request.json`，动作支持 `run`、`run_gradients`、`showcase`、`gradients`、`close` 和 `quit`。最后一项仅用于明确需要重启实验客户端时的正常退出。请求在客户端线程消费，运行记录位于同目录 `runs/<时间>/`。

证据复核示例：

```powershell
python scripts/validate_runtime.py evidence/20260929-011858 --deployment evidence/deployment.json
```

该命令只检查已有截图、像素、shader 实例变化和恢复记录，不重新启动游戏。材质外观还需查看原图。贴图的来源与许可保存在 `THIRD_PARTY_NOTICES.md` 和 `THIRD_PARTY_LICENSES/`。
