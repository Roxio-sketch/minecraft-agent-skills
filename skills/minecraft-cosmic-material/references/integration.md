# 让 Cosmic 适用于不同物品

“所有物品”应实现为共享材质内核加多种几何适配器。不能用一个二维模型 loader 证明所有 MOD 物品都能无损覆盖。

## 适配选择

| 目标 | 可行入口 | 保留的契约 |
|---|---|---|
| generated/handheld 二维物品 | 参考 custom loader + wrapped model，或在最终模型解析后包装 | display、左右手、tint、overrides、层、foil |
| 原版/第三方三维 baked model | 对最终 quads 做附加材质 pass，保留原几何 | 六面与 unculled quads、UV、面剔除、随机种子、RenderType、多 pass |
| 弓、弩、钟、罗盘等状态模型 | 先解析本帧模型状态，再选择/包装该结果 | 拉弓阶段、使用时间、实体/world/seed，不能缓存一个全局 ItemStack |
| BEWLR、箱子、盾牌、三叉戟或自定义物品 renderer | 目标 renderer 的 ModelPart/VertexConsumer 接口，或专用适配 | 动态几何、贴图来源、动画矩阵、顶点格式 |
| GeckoLib 等骨骼模型 | 按目标版本的材质/layer hook 接入 | 骨骼姿态、每子网格 UV、动画 tick，不能假设来自 getQuads |
| 穿戴护甲 | armor layer/model adapter，单独 shader 实例/格式 | 槽位、肢体姿态、overlay、lightmap、图集 UV |
| 无几何入口的 renderer | 原效果保留，并报告未适配 | 可选离屏 mask/depth 合成，属于额外实现而非默认万能方案 |

通用选择规则可以按物品 ID、tag、组件/NBT（版本决定）、模型或材质区域匹配。默认只包用户选定目标；覆盖全部注册物品的操作仍需保留按类别回退和诊断清单。

## 参考代码已经做了什么

Forge 1.20.1 的链路：`ModelEvent.RegisterGeometryLoaders` 注册 `avaritia:cosmic`；loader 去掉自有字段与 loader 后反序列化基础 BlockModel；bake 返回 CosmicBakeModel；ItemRenderer Mixin 识别 PerspectiveModel 并调用 renderItem；先画 wrapped 模型并 flush，再设置 uniform，画由 mask sprite 重新生成的 item quads。

这不是单靠 JSON 或单靠 isCustomRenderer 完成的。源码 Mixin 没有 cancel vanilla 分支；该模型又返回空 quads / isCustomRenderer=true。移植须核查后续 BEWLR 分支，避免原有自定义物品被重复绘制或被漏掉。原版/第三方 Item 类也不能通过资源包获得 Java `IToolTransform` 接口。

参考 WrappedItemModel 有硬编码 TransformUtils 默认姿态和状态委托，不能原封不动用作通用包装。新实现优先委托原 display/applyTransform；返回新的模型时保留包装与递归保护。在计算实际变换前准备本帧状态，不把下一帧需要的 parentState 写在 transform 之后。

## 三维几何覆盖方法（建议设计，尚非已完成通用实现）

对最终基础模型已有 quads 进行重放，保持 Position 完全一致，只改变材质采样输入。若 mask 与原 sprite 采用相同局部 UV 布局：

```text
localU = (atlasU - baseU0)/(baseU1-baseU0)
localV = (atlasV - baseV0)/(baseV1-baseV0)
maskU = maskU0 + localU*(maskU1-maskU0)
maskV = maskV0 + localV*(maskV1-maskV0)
```

原 quad 已编码旋转/镜像，复用其 UV 对应关系；mask 必须对齐该 sprite 内容。对于另一个 UV 展开或多个材质，建立显式 sprite→mask 映射；不以一张 16×16 剑 mask 覆盖任意模型。使用独立 mask sampler 时保留局部 UV，并在 shader 中改绑定。

全覆盖可以取原材质 alpha 作为 coverage，但参考 shader 是 R mask，不能直接把彩色原贴图当 mask。可以新增显式 alpha coverage 模式，或提供按 alpha 派生的灰阶 mask；两者均需匹配透明裁剪、动画帧与原图集布局。不能从普通图标自动判断“剑刃”“护手”等语义区域；局部艺术设计需要作者 mask 或模型材质分组。

透明基础物品通常没有可依赖的稳定深度：优先把 cosmic 融合到原材质 pass；否则设计自己的 coverage/depth pass 与排序。对不透明同几何叠加，EQUAL 最严格；LEQUAL+偏移容错更宽，但可能产生轮廓露出，应按实际几何选择。

## 绘制时序与状态

```text
解析最终 ItemStack 模型和材质
保存本帧显示上下文、姿态与效果参数
绘制/提交基础层（或在基础 shader 内合成 cosmic）
在仍有效的深度与 framebuffer 上：
  绑定匹配顶点格式的 cosmic shader、atlas 和 lightmap
  上传属于当前 draw 的参数
  提交该对象 cosmic 几何，并在参数改变前完成 draw
绘制可选 glint/halo，按选定视觉层序
恢复矩阵、depth/blend/texture 状态
```

不同 opacity、GUI/world scale、palette 的物品共用同一个 RenderType 时，仅依次调用 uniform.set 和写顶点可能使前面的物品用到后面的参数。可靠基线是受控 buffer 上按物品或按材质分组 flush；更高效方式是把变化量作为 per-vertex/instance 数据。不要在任意借来的 MultiBufferSource 上无差别 endBatch 全部内容。

资源重载同时使 shader、uniform、sprite 和模型缓存过期。缓存几何时以资源代次及模型状态为键；不是每帧重烘焙，也不是永不失效。所有渲染类限制在客户端，独立服务器不加载它们。

## 最小模型配置示例

下面是已有 Re-Avaritia loader 支持的字段，假设目标为 Forge 1.20.1 且 mod 已安装。`example` 资源需由项目实际提供：

```json
{
  "parent": "minecraft:item/handheld",
  "textures": {"layer0": "example:item/my_blade"},
  "loader": "avaritia:cosmic",
  "cosmic": {"mask": "example:mask/my_blade"}
}
```

对应 mask 位于 `assets/example/textures/mask/my_blade.png`，并需进入 block atlas。现有项目的 `src/generated/resources/assets/minecraft/atlases/blocks.json` 用 directory source 收集 misc、models、mask；确认 generated resources 被构建打包。RenderBlender 改用 `renderblender:cosmic`，但仍需完整库注册和资源，不是一个可独立运行的资源包方案。多个 mask 的数组表达多个 sprite 层，不会自动绑定三维子网格。

已有 custom loader（例如 OBJ）不能通过覆盖 `loader` 字段保留其能力；使用 loader 组合/委托或 bake 后包装。Forge 文档也说明自定义 loader 必须自行消费需要的模型内容：[模型加载器](https://docs.minecraftforge.net/en/1.20.x/rendering/modelloaders/)。

自有动态 Item 可按 Forge 官方的 BEWLR / IClientItemExtensions 入口接入：[Forge 1.20.x BEWLR](https://docs.minecraftforge.net/en/1.20.x/items/bewlr/)。NeoForge 必须使用其对应版本事件和签名：[NeoForge 1.21.1 模型加载器](https://docs.neoforged.net/docs/1.21.1/resources/client/models/modelloaders/)。这些接口文档只证明接入机制，不证明当前材质兼容所有物品。
