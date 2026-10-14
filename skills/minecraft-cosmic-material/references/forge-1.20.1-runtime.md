# 已实测的 Forge 1.20.1 baked-item 基线

用于实现或修改 Forge 1.20.1 普通物品 Cosmic 材质。适配范围是原模型能提供 baked quads 的路径；穿戴模型和原版盾牌已有单独的 [装备适配器](forge-1.20.1-equipment.md)，其他 BEWLR 和骨骼框架仍需各自适配器。

## 实测依据与定位

2026-09-28 在 Java 17、Forge 47.4.10、RTX 4060 Ti 下，将 `reobfJar` 生产包安装到 AXO ASTRA-TEST，由用户选择的 ASTRA_VFX_TEST 存档验证。10 个截图点 GL error=0；shader generation 1→2；弓使用 41 tick 后解析到 `bow_pulling_2`。材质时间变化、GUI 配色、原生 glint、第一人称左右主手、第三人称正背面、掉落和展示框均有证据。

本机可定位的工程：[README](E:/着色器渲染/cosmic-material-lab/README.md)，报告：[运行验收](E:/着色器渲染/COSMIC_MATERIAL_RUNTIME_VALIDATION.md)，记录：[results.json](E:/着色器渲染/cosmic-material-lab/evidence/20260928-235201/results.json)。这些是历史样本位置，不是今后用户必须使用的工程、实例或存档。其他机器路径不存在时，使用下述接入契约并获取对应参考素材，不能假称已执行此跑次。

原始五项目没有因此变成“已构建”。NeoForge 1.21.1、Oculus/Iris、任意透明多 pass 和穿戴装备均不在此基线认证范围。

## 保留原模型的接入契约

1. 在 `ModelEvent.ModifyBakingResult` 中包装 inventory 根模型。保留 `BakedModelWrapper` 的几何委托；`isCustomRenderer` 的物品退出到原路径。原模型有自己的 loader 时，包装烘焙结果而不是覆盖 loader JSON。
2. 包装 `ItemOverrides.resolve` 实际返回的模型；`applyTransform` 调用原实现并保留其返回模型和显示上下文。否则弓拉伸等动态选择可能只在基础状态有效。
3. 未命中材质规则时返回原 `getRenderPasses`。命中时在原 pass 的几何上提供材质 RenderType。第三方模型若在 `getQuads` 中按 RenderType 严格过滤，需专门适配；不能由 vanilla 样本推断兼容。
4. 此方案以**原几何单 pass 合成**避开二维挤出壳不贴合的问题。原 sprite alpha <0.1 discard，保留原轮廓；mask 使用红通道混合原 RGB 与 Cosmic RGB。三维方块和阶梯无需重新生成平面覆盖模型。
5. 原生 ItemRenderer 负责 foil/glint。本实验没有自建 glint shader；修改渲染入口后仍要检查其是否保留。

相关实现：[CosmicModels.java](E:/着色器渲染/cosmic-material-lab/src/main/java/dev/cosmicmaterial/CosmicModels.java)。

## 绘制状态与资源生命周期

- `ShaderInstance`、RenderType 和真实顶点数据统一为 `DefaultVertexFormat.NEW_ENTITY`。
- RenderType 按 `ItemDisplayContext + palette + mask` 缓存不可变键。将材质 uniform 设置放在 shader state supplier 内，由实际 draw 调用；不在模型 resolve 阶段仅写一次共享 shader。
- 世界使用实际相机 yaw/pitch；GUI 的 yaw/pitch=0、externalScale=100。这里延续参考的方向场观感，没有验证所有相机/FOV，不把它称为完全物理准确坐标。
- 使用 block atlas Sampler0。atlas directory source 收录 10 张星体动画 sprite 和 mask；`TextureStitchEvent.Post` 获取实际 UV。重载时清理模型/类型缓存，`RegisterShadersEvent` 替换 shader 实例，每次 draw 从当前实例取 uniform。
- 单 pass 使用 LEQUAL、颜色和深度写入、原 alpha cutout；这不同于原参考的基础层加 EQUAL Cosmic 层。不要机械混用两套深度契约。
- 保留 sampler/lightmap/overlay/fog 的一致性；`externalScale` 默认值不能为 0。

相关实现：[CosmicRender.java](E:/着色器渲染/cosmic-material-lab/src/main/java/dev/cosmicmaterial/CosmicRender.java)、[片元 shader](E:/着色器渲染/cosmic-material-lab/src/main/resources/assets/cosmicmaterial/shaders/core/cosmic.fsh)。

## 当前已生效的设计接口

```mcfunction
/give @s minecraft:diamond_sword{CosmicMaterial:{Palette:"violet",Mask:"blade"}} 1
/give @s minecraft:diamond_axe{CosmicMaterial:{Palette:"cyan"}} 1
/give @s minecraft:oak_stairs{CosmicMaterial:{Palette:"gold"}} 1
```

`violet` 继承原始蓝青配色，名称不表示固定紫色；`gold` 和 `cyan` 用显式程序重映射。其他值回退第一种。`blade` 仅适用于钻石剑，按青色像素生成的样例 mask 包含柄尾部分，精准刀刃需要重画。原版基础纹理仅引用游戏资源，不打包到实验中。

任意设计的扩展点：

| 目标 | 实际要修改的层 |
|---|---|
| 精准局部覆盖 | mask 图及其模型 sprite→局部 UV→mask atlas UV 映射；多 sprite 模型按面选择 |
| 更多配色/透明度 | 明确材质参数模型、shader uniform 和 RenderType 缓存键，不能只增加一个未读取的 NBT 字段 |
| 不同星体/符文 | 修改 atlas 资源、sprite 数组长度和图案选择逻辑，保留红通道强度契约与许可 |
| 密度/速度/层数 | Java 参数、shader/循环预算和默认值一起实现；当前 NBT 没有这些字段 |
| 三维/状态物品 | 先确认原 quads 与 override 保留，再调整空间与覆盖；不需要把模型改成剑壳 |
| 穿戴/BEWLR/骨骼动画 | 新建对应顶点/渲染层适配器，再用同一方向场；当前 wrapper 不提供此能力 |

使用 [design.md](design.md) 定义设计，再在真实接口上实施；不要把设计 JSON 示例当作已注册配置。

## 编译和运行验证的经验

生产环境必须测试重混淆后的 JAR，不能只看 `runClient`。固定目标版本；本实验 ForgeGradle 6.0.54 / Gradle 8.8 / Java 17 / official 1.20.1 已验证。Forge Maven 不可用时，仅在缓存完整的机器使用 `--offline`；不要为解决构建失败关闭证书校验或假称成功。

Java 改动需退出并重启游戏；资源重载只验证 shader、模型、atlas 资源生命周期。客户端命令需走客户端 dispatcher，服务端 `sendCommand` 不等价。本实验的文件桥只是专用自动化入口，读取后删除请求，避免重启重跑。

测试前备份选定存档；临时物品、相机和实体在完成后恢复，并记录恢复结果。正常获取物品仍可能解锁配方/进度，因此“恢复背包”不能写成“世界完全未变化”。不预设用户同意启动某实例或进入某存档，按当前用户请求选择；本机历史跑次不能成为之后的默认授权。

本轮没有性能基准，也未检验所有动态模型、中间弓阶段、第三人称左手和光影包。范围扩展遵循 [validation.md](validation.md) 的对应样本矩阵。
