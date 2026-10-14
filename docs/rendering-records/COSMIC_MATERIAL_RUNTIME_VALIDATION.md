# Cosmic 物品材质：Forge 1.20.1 运行验收

**结论：独立 Cosmic Material Lab 已在启动器生产环境实际渲染成功。** 这次验证的是物品和装备模型表面的 Avaritia 风格方向场材质，不以天空效果作为证据。最终跑次：2026-09-28 23:52（Asia/Shanghai），`20260928-235201`。

## 环境与产物

| 项目 | 实际记录 |
|---|---|
| Minecraft / Forge | 1.20.1 / 47.4.10 |
| Java / 构建 | Java 17；Gradle 8.8；ForgeGradle 6.0.54；official 1.20.1；生产包 reobfJar |
| 启动与存档 | AXO ASTRA-TEST / 用户选定的 ASTRA_VFX_TEST |
| GPU | NVIDIA GeForce RTX 4060 Ti |
| OpenGL / 驱动字符串 | 4.6.0 NVIDIA 617.14 |
| 验收分辨率 | 1280 × 720，GUI scale 2，FOV 70 |
| 最终 JAR SHA-256 | `cb527fe5890bc38ee9728644f80d35e89ff246f4e85704e53260f21ec158507d` |

[工程与操作说明](E:/着色器渲染/cosmic-material-lab/README.md) · [生产 JAR](E:/着色器渲染/cosmic-material-lab/build/libs/cosmic-material-lab-0.1.0.jar) · [部署和哈希](E:/着色器渲染/cosmic-material-lab/evidence/deployment-final.json) · [成功构建日志](E:/着色器渲染/cosmic-material-lab/evidence/build-final-offline.log)

游戏通过 axolotl-launcher skill 的正常启动器/Forge quick-play 路径进入指定存档，MCP-HELPER 返回实时世界名称。其他已装 mod 保留。源码修改后先保存退出，替换单个 JAR，再重启；未尝试用资源重载热替换 Java 类。

在线重建曾因 Forge Maven 证书检查失败中止；完整依赖缓存下离线重建成功，没有关闭证书校验。`test NO-SOURCE` 表示无 JUnit 测试，不等于本轮游戏验收未运行。

## 实际画面

下图是游戏 framebuffer 原始截图，展示局部 mask、三配色、原生 glint、弓以及原三维几何：

![Forge 1.20.1 Cosmic 物品材质实测](E:/着色器渲染/cosmic-material-lab/evidence/20260928-235201/gui-frozen.png)

![满弓状态的 Cosmic 材质](E:/着色器渲染/cosmic-material-lab/evidence/20260928-235201/bow-pulling.png)

[六场景缩略总览](E:/着色器渲染/cosmic-material-lab/evidence/20260928-235201/contact-sheet.png) 由原截图缩放拼合，未替换游戏中的材质像素。第三人称物品占屏较小，只支持当前距离下的可见性和入口检查，不作为细节质量或所有相机参数的证据。

## 验收结果

| 检查 | 结果与证据 |
|---|---|
| GUI：剑/斧/苹果/弓/方块/阶梯 | 通过。原外形存在，材质覆盖真实 quads；GUI 同屏有普通物品对照 |
| 局部 mask | 通过当前示例。剑柄主体和外缘保留原图；青色柄尾也被示例 mask 覆盖，未宣称精确纯刀刃分割 |
| 同屏 A-B-A 配色 | 通过肉眼检查，金色与蓝青材质未相互污染。不同屏幕位置方向场本来不同，不要求两个 A 图案逐像素一致 |
| 时间变化 | 通过。time=0→200 的剑 ROI 改变 4,157 像素、方块 ROI 改变 22,752 像素；普通剑、普通方块和背景 ROI 均为 0 |
| 原生附魔 | GUI 叠加的 glint 可见，原生 foil 路径保留 |
| 左/右主手 | 通过两个第一人称截图；并非左右手同时持物压力测试 |
| 第三人称正/背面 | 通过当前场景的画面与材质 draw 记录；左主手第三人称未单独截图 |
| 掉落物 / 展示框 | 通过。两个掉落物和一个固定不可见框内的物品可见；记录 GROUND / FIXED |
| 弓模型 override | 通过静止与满弓。最终使用 41 tick，模型 sprite 为 `minecraft:item/bow_pulling_2` |
| 资源重载 | 通过。Shader generation 1→2，重载后样例仍可见；没有改变资源包 atlas 排布 |
| GL 错误采样 | 10 个截图点 `glError=0`；不是全帧 GPU 调试/全驱动兼容证明 |
| 测试恢复 | 背包、选中槽位、模式、位置和三个测试实体移除均记录为 true |

证据入口：[原始运行记录](E:/着色器渲染/cosmic-material-lab/evidence/20260928-235201/results.json)、[20 项证据检查](E:/着色器渲染/cosmic-material-lab/evidence/runtime-validation.json)、[可复跑检查脚本](E:/着色器渲染/cosmic-material-lab/scripts/validate_runtime.py)、[筛除启动账号参数的客户端日志](E:/着色器渲染/cosmic-material-lab/evidence/20260928-235201/client-sanitized.log)。

日志中没有发现 Cosmic shader 编译/加载错误。存在原版 `rendertype_entity_translucent_emissive` 和 Moonlight `text_alpha_color` 的 Sampler2 警告，因此不称整份日志“无警告”。

时间对比固定的是 shader 的 time uniform，没有冻结 atlas sprite 自身的动画。像素统计证明 Cosmic 区域在运行中动态变化且普通对照稳定，不用于分离两种动画各自的贡献，也不要求重载前后所有像素完全相同。

首次跑次 `20260928-234059` 已保留：它只用本地 `startUsingItem`，弓状态停在 0 tick / `bow_pulling_0`，不能证明满弓。最终版本改用客户端 `gameMode.useItem` 和真实按住使用键，完成上述满弓验证。中间阶段没有逐阶段截图，不声称覆盖了所有动作帧。

## 这套实现如何复用

`CosmicModels` 包装烘焙后的 inventory 根模型，随后继续包装 overrides 选出的真实模型，沿用原变换与 quads。只给具有 `CosmicMaterial` NBT 的物品更换材质 pass。最终运行记录包装了 1,424 个模型根；这个数字不是 1,424 种物品全部验收通过。

`CosmicRender` 在实际 RenderType shader supplier 执行时设置材质参数，缓存键包含显示上下文、palette 和 mask，防止先写全局 uniform 再延迟 flush 引起串色。ShaderInstance、RenderType 和顶点输出均使用 NEW_ENTITY；资源重载时刷新 shader、UV 和类型缓存。

片元 shader 保留参考的 16 层方向场、10 张星体 sprite 和球面映射。单 pass 中以原纹理 alpha 保留轮廓，mask 红通道控制表面混合。改良后的组合与参考的双层 EQUAL 深度方法不同；这里实测的是新适配实现，不能据此宣布五个原始项目都通过。

## 尚未覆盖的工作

- BEWLR、自定义动态 renderer、穿戴护甲、GeckoLib 等骨骼动画入口；目前遇到 `isCustomRenderer` 会保留原渲染，不强行覆盖。
- 任意第三方透明/多 pass/按 RenderType 过滤 quads 的模型；染色、多图集局部 mask 的广泛兼容矩阵。
- Oculus/Iris 及各具体 shader pack，其他显卡/驱动、FOV 和 GUI scale 矩阵，密集背包性能与 GPU 时间。
- 精确逐帧空间锚定/相机绕转分析、地形遮挡压力场景和资源包重排图集。

因此，可以把当前产物作为**已运行的 Forge 1.20.1 baked-item Cosmic 基础实现**，通过适配器逐类扩展。还不能把“材质可应用于广泛物品”写成“所有 MOD 的所有装备无条件兼容”。

## 存档与结束状态

启动前完整备份：[ASTRA_VFX_TEST-before.zip](E:/着色器渲染/cosmic-material-lab/evidence/preflight/ASTRA_VFX_TEST-before.zip)。验收另保存玩家 NBT 以便本地恢复。测试不修改方块，创建实体按自己的 UUID 清理。显示选项按进入测试前的值恢复，使用键已释放；请求文件自动消费，MCP control mode 为 false。

自动获得测试物品可能解锁配方/进度，世界时间也会正常推进，所以没有把存档整体回滚成测试前的字节状态。最终保留客户端供查看材质展示屏，Esc 可以返回已恢复的测试世界。

可复用方法已同步到 [已安装 skill](C:/Users/MAINTAINER/.codex/skills/minecraft-cosmic-material/SKILL.md) 和 [Forge 实测接入说明](E:/着色器渲染/skill/minecraft-cosmic-material/references/forge-1.20.1-runtime.md)。
