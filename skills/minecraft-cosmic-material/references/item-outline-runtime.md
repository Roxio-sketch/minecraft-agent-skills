# Forge 1.20.1 八角几何物品描边实测

此页是原创实现的运行记录，不是 ArcaneVortex 原 JAR 的运行认证。ArcaneVortex 的算法证据见 [item-outline-study.md](item-outline-study.md)。

## 可用工程与入口

- 工程：`E:/着色器渲染/cosmic-material-lab/`，Forge 1.20.1 / 47.4.10、Java 17。
- 生产包：`build/libs/cosmic-material-lab-0.2.0.jar`。模组 ID `cosmicmaterial`，客户端可选效果。
- 游戏内运行 `/cosmicmaterial outline`，打开真实渲染预览屏。屏上成对展示原版与描边剑、斧、弓、苹果、方块、阶梯；拖动 Width 滑条，点击模式按钮切换 Solid / Stars / Three colors / Rainbow，输入八位 `AARRGGBB` 颜色后 Apply。Esc 退出，不改物品栏。
- 在允许命令的 1.20.1 存档，可用 `/give @s minecraft:diamond_sword{OutlineMaterial:{Width:0.008f,Type:0,Color:-11147521}} 1`。`Color` 是带 alpha 的有符号 ARGB 整数，示例值对应 `FF55E6FF`；实际调参也可使用 `/cosmicmaterial outline`。

`OutlineMaterial` 是本实验的新接口，不是原 JAR 的配置字段。默认 Width 为 `0.008` 模型单位；GUI 再加 `0.0125`，第一人称不加，其他场景加 `0.0035`。Type `0` 纯色、`1` 原创星云、`3` 原创三色流动、`4` 整体彩虹；`Color1`、`Color2`、`Color3` 可供三色模式使用。模式 `2` 屏幕折射与 `5` 原纹理色尚未实现。游戏预览屏当前直接提供 Width、模式和纯色 ARGB，三色的三个颜色需经 NBT 设置；浏览器工作台的 JSON 尚未直接驱动游戏。

## 实现要点

`CosmicModels.java` 在原 baked 模型 pass 前加入 OutlinePass：对每个 pass 的原 quads 生成八组 `(±d,±d,±d)` 平移顶点，按离散面向筛选；BlockItem 的 side quads 逐面筛选，general quads 提交八次。`outline.fsh` 从原 atlas UV 采 alpha 并裁去 `<0.1` 的透明片元。`OutlineRender.java` 按 Style 缓存 RenderType，在 draw 时设置颜色和时间；使用 LEQUAL、COLOR_WRITE、NO_CULL。这里为了稳定保留内部原纹理，描边 pass 排在基础 pass 之前；这与 ArcaneVortex 所读代码的调用次序不同，因此不声称像素级复刻。

## 运行证据与边界

2026-10-02 在 ASTRA-TEST / ASTRA_VFX_TEST、RTX 4060 Ti 中实测。`evidence/outline-20261002/runtime/results.json` 记录 10 个场景：GUI 三帧（包含资源重载）、左右第一人称、第三人称正背面、弓静止与满弓、掉落物与展示框。10 张 PNG 都是客户端真实截图；`showcase-final.png` 是放大 GUI 对照，`stars-wide.png`、`rainbow-wide-final.png` 是可调宽度与模式的交互证据。独立记录检查运行 `scripts/validate_outline_runtime.py`。

结果：10 个场景完成，资源重载后 shader generation 1→2；所有截图点 GL 错误为 0；满弓模型含 `minecraft:item/bow_pulling_2`；背包、槽位、模式、位置和测试实体恢复均为 true。GUI、手持、掉落物与展示框目视可见贴边轮廓，主体纹理仍清楚；方块和阶梯未见整面染色。画面没有与 ArcaneVortex 原 JAR 同机同场景像素对比，因此结论是“复现其核心几何方法并在这些场景跑通”，不是“与原效果完全一致”。

未认证：BEWLR、自定义动态 renderer、穿戴护甲、GeckoLib、第三方透明/多 pass、Oculus/Iris shaderpack、任意 mod 物品、性能预算。Refraction（原 uType 2）与原 JAR 特有银河噪声、流色细节尚未按像素重建。为这些载体增加适配器时，先确认它们是否走 BakedModel/getRenderPasses，不能把本包装器称为“所有物品通用”。
