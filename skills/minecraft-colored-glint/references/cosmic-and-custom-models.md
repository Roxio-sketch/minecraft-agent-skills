# 与 Cosmic、动态模型及护甲共存

在本项目内，星空材质和附魔流光是两条渲染职责：Cosmic 产生物品表面的宇宙画面，glint 根据表面几何叠加移动的彩色纹理。用户只要求彩色附魔时无需把 Cosmic 算法移植进 glint shader。

## 先追踪实际绘制

参考 `CosmicBakeModel.renderItem` 先调用 `renderWrapped`，全量结束当前批次，再写 Cosmic uniform 并绘制 mask 壳。`WrappedItemModel.renderWrapped` 直接获取模型基础 RenderType buffer，没有创建 foil 组合 consumer。`ItemRendererMixin` 的自定义模型调用也未在该注入点取消原方法。由此只能得出“存在多段绘制，应追踪”，不能断言自定义路径已经兼容、必然消失或必然重复。

在目标工程画出实际链路：`最终模型/override → 基础几何 → 目标 consumer → enqueue → flush → shader 状态 → framebuffer`。逐一记录 Cosmic 和 glint 发生在哪个环节、使用哪份几何、谁写深度。只根据函数名称判断会漏掉绕过 foil helper 的模型。

## 普通 baked 模型

复用原模型 quads、tint、display transform 和最终 override。若现有 draw 已通过原版 foil helper，替换对应 glint 层即可；若没有，需在实际绘制入口增加与原模型相匹配的 foil consumer 或第二次几何绘制。

不要为了添加流光再次套 `ItemModelGenerator.processFrames` 平面壳。三维物品的侧面、薄片和不同渲染 pass 必须来自正确的几何。加入覆盖层时保留模型状态和弓拉伸等 property/override 结果，不能重新从默认模型开始。

当 Cosmic 与 glint 是独立 pass 时，根据用户视觉目标明确顺序，例如基础→Cosmic→glint。此顺序需要通过 BufferSource 的实际 flush 与深度契约实现，仅调整 Java 调用顺序不保证 GPU 顺序。若 Cosmic 最终覆盖了先前 glint，不应简单提高亮度掩盖问题。

## BEWLR、动画与特殊物品

BEWLR、第三方骨骼动画、盾牌/三叉戟/特殊 foil 路径可能自行选择 consumer。分别检查实际 renderer 是否调用已接入的 helper，以及有没有 stack 上下文；需要时在它们的材质/渲染回调适配，不以普通 baked item 的成功宣称支持。

嵌套渲染是上下文作用域的重要用例：红色外层物品绘制蓝色内部物品后，外层剩余部分仍必须为红色。通过 finally 恢复外层配置，同时保证 draw 时颜色状态与已缓存顶点一致。

## 穿戴护甲

“背包内护甲物品有彩色流光”与“穿在实体身上有彩色流光”是不同路径。源快照虽然创建 ARMOR_LAYERS，但没有接上 `processArmor`。实现穿戴效果时取得实际装备 stack，在护甲绘制层选用 armor 对应 shader、纹理状态、layering 和缓冲，provider 分支也必须返回 armor 层。

按用户范围验证人物/盔甲架、已附魔/强制、不同部位同时使用不同颜色。若只要求物品形态，明确此范围，不扩展到额外装备渲染器。

## 透明、光影与多 pass

对 EQUAL 深度流光，先验证基础层在该 framebuffer 中已经按相同几何写深度。半透明物品、透明输出目标、MSAA/光影改写和后处理可能改变条件。选择与目标版本原分支相同的状态开始排查，不能以 NO_DEPTH_TEST 或 LEQUAL 作为无条件修复。

Oculus/Iris/其他渲染替换模组的支持需要对应整合环境的证据。项目曾跑过无光影 Cosmic 不足以证明彩色 glint 在光影下有效；记录版本、配置、实际支持范围即可。
