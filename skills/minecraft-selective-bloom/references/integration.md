# Forge 1.20.1 接入

样例入口 Bloom.render 在 AFTER_ENTITIES，仅允许原生主 target 与非 Fabulous；选择一个客户端标记。它复制主颜色及深度到独立资源，RGBA8 TextureTarget 不提供 HDR。模糊目标半分辨率，pass 后主深度保持不写。

它是独立 emission target 的接入基线：目标物品需在正确变换与模型状态下重放选定几何/输出 emission。不要把 baked wrapper 硬套 BEWLR；不要在每个物品渲染时处理整屏模糊。

框架参考：VeilBloomRenderer 的 RGBA16F 与 depth wrapper、Lodestone pb_bloom 的多尺寸分离模糊。参考 1.21.1 API 不用于当前模板。生产流程须确定 HDR/linear、alpha、tone-map 和 shader pack 的责任；样例在主颜色上做风格化 LDR 加法，无物理 HDR 保证。

输入与输出不能是同一正在写入附件；清空 emission 不得清主深度。重载/尺寸变化/退出世界释放资源；异常停止该效果并在 finally 恢复 framebuffer、viewport 和触及状态。样例 PassState 只涵盖其实际修改集合，新 pass 修改其他状态时扩展。


## 试用入口

项目 mod ID 为 `bloomlab`。安装生产 JAR 后，世界示例使用 `/bloomlab start`、`/bloomlab status`、`/bloomlab off`，不修改方块。材质适配例子使用 `/give @s adapterlab:articulated` 查看实际物品；start 不是给任意物品安装适配器。

资源配方位于 `assets/bloomlab/rendercraft/recipe.json`。仅使用 design.md 列出的字段。示例采用独立 package `dev.rendercraft.bloomlab`。四个样例类和资源 ID 隔离；同时安装的渲染顺序和效果组合尚未实测。
