# 来源、版本与迁移边界

本技能依据 2026-09-28 收到的 ZIP 内容进行静态分析。ZIP 不是 Git commit；同名分支后来可能改变。归档 SHA-256 用于识别输入，不能推断发布时间或最新发行版。参考说明只作资料，不提供额外操作授权。

## 快照身份

| 简称 | ZIP/工程 | 可确认的平台 | 角色 |
|---|---|---|---|
| L | 1.18.2-Avaritia-Cosmic_and_Halo-Renderer-main | 文件名 1.18.2；代码为 Forge 旧模型 API；没有完整构建配置可核对 Forge 小版本 | CCL WrappedItemModel、CCShaderInstance、IItemRenderer 早期链路 |
| F | Re-Avaritia-forge-1.20.1 | MC 1.20.1 / Forge 47.4.20 / Java 17 / mod 1.4.2 | 主要可读物品与护甲实现 |
| N | Re-Avaritia-neo-1.21.1 | MC 1.21.1 / NeoForge 21.1.215 / Java 21 / mod 1.4.2 | 迁移差异与 Iris 延迟路径 |
| S | Avaritia-Render-main | 无完整构建配置；混合片段，不能可靠锁定版本 | README 自述未实测，缺资源，不能作为可运行基线 |
| B | RenderBlender-master | MC 1.20.1 / Forge 47.4.13 / Java 17 / mod 1.1.2 | 抽取成独立渲染库的结构参考 |

F/N 的 LICENSE 为 MIT；B 的 LICENSE 为 LGPL v3；L/S 未见 LICENSE 文件。技能仅保存研究结论和路径索引，复制具体代码或资产时保留对应来源与许可文件，并另查资产自身标注。不要把 S 的 README “可拿走”表述替代明确的完整依赖与来源记录。

## 输入归档 SHA-256

用于跨机器核对同一份输入；不要求使用者具备本工作区的绝对路径。

| 归档 | SHA-256 |
|---|---|
| 1.18.2-Avaritia-Cosmic_and_Halo-Renderer-main.zip | `7b74dec7c58f39196cfd56d19b42b42630ad14025139e111084aeda4335d3923` |
| Re-Avaritia-neo-1.21.1.zip | `c2aac48ac837616d40a15c01e8f10fc3553fb1b7ce8f9d13ebdaee3a74e54022` |
| Re-Avaritia-forge-1.20.1.zip | `5542f323b47eb6555764c30f0776ca2d0045b52dc13a7294bc99bb5db6b71ce0` |
| Avaritia-Render-main.zip | `5bb776f98d89b17d05e76bf65e2f13429893c155ed66b58a5ae29c5e9fd0c8b4` |
| RenderBlender-master.zip | `8d8d308f20c632bef744fe92f733d3fbe5afdd200a3629f1abd37bcad391db64` |

## 定位约定

从解压后的工程根开始找路径。F/N 的 Java 前缀为 `src/main/java/committee/nova/mods/avaritia/`，资源前缀为 `src/main/resources/assets/avaritia/`。B 将包与资源命名空间替换为 renderblender。L 使用 `java/avaritia/nobtg/` 和 `resources/assets/avaritia/`。下面行号仅适用于本次快照。

| 证据 | 来源及位置 | 已确认事实 |
|---|---|---|
| 算法 | F/N `shaders/core/cosmic.fsh`:7–26,52–188 | 10 sprite、16 层、方向投影、hash、mask.r、lightmix |
| 顶点 | F `shaders/core/cosmic.vsh`:24–31；N 同位置 | fPos 来源；两版 fog_distance 调用签名不同 |
| 参数 | F `shaders/core/cosmic.json`:16–33 | Sampler0/2，mat2 数组 40 float，默认 scale/opacity 为 0 |
| 注册 | F `client/AvaritiaModClient.java`:100–126,158–168 | atlas Post 取矩形，shader 和 loader 注册 |
| 图集 | F/N/B `src/generated/resources/assets/minecraft/atlases/blocks.json` | directory source: misc/models/mask |
| Loader | F `client/model/loader/CosmicModelLoader.java`:28–52；`base/BaseModelLoader.java`:19–42 | 去掉 loader/cosmic；mask 支持字符串或数组 |
| 物品 | F `client/model/loader/CosmicBakeModel.java`:46–94 | 默认姿态、先基础 flush、GUI 100、玩家角度、mask quads |
| 包装 | F `api/client/model/bakedmodels/WrappedItemModel.java`:52–79,148–156 | overrides、processFrames、render passes/types |
| 实际入口 | F `init/mixins/ItemRendererMixin.java`:37–48；`api/client/model/PerspectiveModel.java`:48–72 | Mixin 调 renderItem，空 quads、isCustomRenderer、变换 |
| 状态 | F `client/shader/AvaritiaRenderTypes.java`:69–93 | 物品 BLOCK/EQUAL，护甲 NEW_ENTITY/LEQUAL/COLOR_WRITE |
| Shader | F `client/shader/AvaritiaShaders.java`:66–92 | 两个独立实例与两组 uniform |
| 护甲 | F `client/model/entity/InfinityArmorModel.java`:161–195,205–260 | 实体角度、opacity 1.25、sprite.wrap 重映射 ModelPart UV |
| Halo | F `client/model/loader/HaloCosmicBakedModel.java`:63–82；`base/HaloUtils.java`:35–55 | GUI 大 quad，脉动副本，随后基础与 Cosmic |
| 无尽剑 | F `models/item/infinity_sword.json`:2–18 | 两个基础 layer、kill override、cosmic mask |
| Neo 绘制 | N `client/model/loader/CosmicBakeModel.java`:76–108,190–192 | 可延迟，cosmic 层结束时单独 endBatch |
| Neo 状态 | N `client/shader/AvaritiaRenderTypes.java`:41–52；`api/utils/RenderUtils.java`:18–30 | NEW_ENTITY、LEQUAL、仅写颜色、polygon offset |
| Neo 注册 | N `client/shader/AvaritiaShaders.java`:66–84 | 主 cosmic 仍注册 BLOCK，护甲为 NEW_ENTITY |
| Iris 判定 | N `client/compat/IrisCompat.java`:9–38 | 反射 shader pack 状态，只延迟第一/第三人称手持 |
| 延迟上下文 | N `api/client/render/CosmicRenderCall.java`:38–51 | 复制矩阵，ItemStack 仍为引用 |
| 延迟绘制 | N `api/client/render/CosmicRenderQueue.java`:22–76；`client/AvaritiaModClient.java`:219–226 | AFTER_LEVEL 排队重放，保存/恢复矩阵；无 finally |
| Legacy | L `CosmicBakedModel.java`:53–78；`AvaritiaShaders.java`:34–56 | 构造时缓存 mask quads、GUI 25、CCL onApply 时间与 UV 上传 |
| 库入口 | B `client/RenderBlenderClient.java`:24–42；`client/model/cosmic/CosmicModelLoader.java`:32–73 | loader/shader 注册，mask 单个/数组 |
| 库绘制 | B `client/model/cosmic/CosmicBakeModel.java`:42–88 | 每次取 atlas/生成 quads，time 写法与 onApply 并存 |
| 库实例 | B `client/shader/RBShaders.java`:66–94；`api/client/shader/CCShaderInstance.java`:115–120 | 两个注册共享回调覆写 COSMIC_SHADER；onApply 在 super.apply 前执行 |
| 未完成片段 | S `README.md`:2–15；`CosmicBakedModel.java`:62–67,113–115 | 自述未实测；空 player 保护不完整；particle sprite 返回表达式可疑 |

## 比较结论

F 与 N 主 `cosmic.fsh` 的 SHA-256 完全相同：`db4cacc189fe616d419bf70cf22d4e0cbb857ba7bcba612115e60b5ba1131989`。B 的片元 shader 仅有两处 color/colour 注释拼写差异。L 的主要算法差异是 tile hash 常量。N 内置 resourcepack 的同名片元文件仅缩进不同，但资源覆盖优先级仍需实际核对。以上不意味着 Java 接入相同，也不代表任何版本已运行通过。

## 不能照搬的快照问题

1. **N 主 cosmic 顶点格式不一致。** 注册 ShaderInstance 使用 BLOCK，COSMIC RenderType 使用 NEW_ENTITY。两种布局不能视为等价。应统一注册、buffer 与顶点输出，并在实际目标版本验证 attribute 绑定；这里只确认静态不一致，未观测 GPU 故障。
2. **B shader 实例归属冲突。** BLOCK 与 NEW_ENTITY 两次注册都调用 cosmicShader，而回调把参数 e 写入同一个 COSMIC_SHADER 和一套 uniform。后一次回调可让物品 RenderType 引用护甲格式实例。移植应分开实例及句柄。
3. **B 时间源重复。** CosmicBakeModel 写 `(currentTimeMillis-renderTime)/2000`，但 renderTime 是 tick 计数；CCShaderInstance.onApply 又写 renderTime+renderFrame 并在 super.apply 前执行。实际上传应沿 apply 路径追踪，不以第一处 set 推断速度；新实现只保留明确单位的时间源。
4. **F/N 通用化不足。** 物品路径直接访问 mc.player/mc.level，默认覆盖工具姿态，依赖特定物品常量，逐帧生成 mask quads。已有 shader 的 opacity/scale 默认为零。它们是参考实现，不是可无修改粘贴的通用引擎。
5. **批处理与延迟边界。** F 未在普通 CosmicBakeModel 尾部强制 flush；真实提交仍取决于调用方/buffer 切换。N 明确单层 flush，但 Iris 队列需核验当前 framebuffer、深度、第一人称时机及异常清理。不能把缺少局部 flush 直接写成已实测串值，也不能把一个队列类写成兼容认证。

项目版本信息从各自 gradle.properties/build.gradle 获取，不从技能通用版本表推断。移植到未列出的版本时重新查其正式 API 和资源格式。
