# Forge 1.20.1 接入

Lighting.render 在 AFTER_CUTOUT_BLOCKS 复制主颜色与深度到独立 snapshot；实际 view-projection 的逆矩阵重建 camera-relative 表面，近邻采样求近似法线，再合成附加项。只写颜色，保留主深度给后续绘制。它影响该阶段已经完成的 opaque/cutout 地形，不包括后绘制实体、透明和所有第三方层。

该基线不检查灯与表面之间的阴影，所以灯可照亮隔墙且在半径内的可见表面；屏幕最近深度无法完整恢复隐藏几何。需要阴影时使用 shadow map、已构建 voxel/ray 数据或目标框架的经验证路径，并重新定义成本。

扩大到多灯：Light(id,owner,position,color,radius,intensity,expiry)；更新位置不按渲染次数推进。先按范围与可见性剔除，按任务选择固定小数组、实例体积或 tiled/clustered。限制 maxLights 并测上传/覆盖开销。world unload/owner removal/资源重载删除对应灯或重建资源。

Veil LightRenderer / InstancedPointLightRenderer / DynamicBufferType 为 1.21.1 架构参考；不把灯光类存在解释成所有灯都有阴影。原生 lightmap 仍由目标游戏系统控制，当前 shader 没有更改服务器光传播。


## 试用入口

项目 mod ID 为 `lightlab`。安装生产 JAR 后，世界示例使用 `/lightlab start`、`/lightlab status`、`/lightlab off`，不修改方块。材质适配例子使用 `/give @s adapterlab:articulated` 查看实际物品；start 不是给任意物品安装适配器。

资源配方位于 `assets/lightlab/rendercraft/recipe.json`。仅使用 design.md 列出的字段。示例采用独立 package `dev.rendercraft.lightlab`。四个样例类和资源 ID 隔离；同时安装的渲染顺序和效果组合尚未实测。
