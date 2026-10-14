# Forge 1.20.1 接入

入口 Articulated.ITEM 的 IClientItemExtensions.getCustomRenderer 返回 BEWLR；builtin/entity 模型 JSON 提供显示变换。独立 NEW_ENTITY shader 与 RenderType 对齐，ModelPart 输出真实几何，自己的 BufferSource 立即 endBatch 以隔离每次材质参数，foil 使用原生路径。

示例是新物品，不覆盖目标 mod 的自定义 renderer。替换既有物品时应包装或调用其最终几何入口、保留 overrides/动作/渲染层；目标 renderer 无扩展入口时再选择明确的 mixin/adapter。

GeckoLib、Veil Necromancer 或其他库：先读取目标版本的 model pose、buffer 和 material 接口。Veil skinned_mesh.vsh 快照通过单 BoneIndex 与实例骨骼缓冲选变换，不代表任意多权重蒙皮。不能将 1.21.1 的缓冲布局复制进 1.20.1。

穿戴护甲：实体视角、装备槽、visibility、内外层、light/overlay、透明和 glint 均需逐项保留；物品图标通过不证明穿戴效果。 shader 新增 bind position、bone index 或参数属性时同步 vertex format、上传和资源 JSON。图集重载与 uniform 生命周期重新验证。


## 试用入口

项目 mod ID 为 `adapterlab`。安装生产 JAR 后，世界示例使用 `/adapterlab start`、`/adapterlab status`、`/adapterlab off`，不修改方块。材质适配例子使用 `/give @s adapterlab:articulated` 查看实际物品；start 不是给任意物品安装适配器。

资源配方位于 `assets/adapterlab/rendercraft/recipe.json`。仅使用 design.md 列出的字段。示例采用独立 package `dev.rendercraft.adapterlab`。四个样例类和资源 ID 隔离；同时安装的渲染顺序和效果组合尚未实测。
