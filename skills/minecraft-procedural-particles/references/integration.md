# Forge 1.20.1 接入

ParticleEngine.java 独立于 Minecraft，Config/Point/tick/reset/clear 可用于目标项目。示例 client-local anchor 位于使用者前方，ParticleEngine 坐标相对该固定锚点，颜色/尺寸在 Particles renderer 中求值。

Particles.render 在 AFTER_PARTICLES 绘制 camera-facing quads，原主深度测试、关闭深度写入，径向 alpha shader。没有透明排序、软粒子场景深度淡出或碰撞；多个重叠透明粒子的正确排序需要新增策略。基础 demo 禁用 Fabulous 和已知未适配 renderer，不能删除 gate 后称已兼容。

Veil ParticleEmitter 及 Lodestone builder/pool 是模块与批次的架构参考；逐步闭合 spawn/update/render/remove，不把 TODO 列表视为现成功能。池化只有在减少实际分配并测得收益时才引入，slot 重用要重置全部历史状态。

自定义纹理/网格时替换径向 fragment 并增加 sampler、atlas 或材质 ID；与 trails/beam skill 共用挂点，但粒子不必记录整条轨迹。与 Bloom 组合时按资格另输出 emission。大量粒子先测 CPU 更新、上传与 overdraw，不能只看 draw call 数。


## 试用入口

项目 mod ID 为 `particlelab`。安装生产 JAR 后，世界示例使用 `/particlelab start`、`/particlelab status`、`/particlelab off`，不修改方块。材质适配例子使用 `/give @s adapterlab:articulated` 查看实际物品；start 不是给任意物品安装适配器。

资源配方位于 `assets/particlelab/rendercraft/recipe.json`。仅使用 design.md 列出的字段。示例采用独立 package `dev.rendercraft.particlelab`。四个样例类和资源 ID 隔离；同时安装的渲染顺序和效果组合尚未实测。
