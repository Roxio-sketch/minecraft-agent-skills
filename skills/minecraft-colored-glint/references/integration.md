# Forge 1.20.1 接入与渲染状态

以下是根据参考快照提炼的实现设计，不是已编译模板。先在目标工程确认 signatures、mappings、MixinExtras 依赖和实际调用路径。其他版本需重新建立对应关系。

## 配置、判断、渲染分离

建议每个物品渲染作用域解析出不可变配置：`appearanceId`、`forceFoil` 和需要的渲染模式。把它绑定到本次物品，而不是保存成全局“当前颜色”。按用户要求确定 provider、物品数据和注册表的优先级；复现参考时 provider 优先，不要无意让 provider 的空返回继续拾取 NBT。

正常实现应满足：

| 原 vanilla foil | forceFoil | 有效自定义外观 | 结果 |
|---|---|---|---|
| false | false | 有或无 | 不添加流光 |
| true | false | 有 | 使用对应彩色流光 |
| false | true | 有 | 无附魔也显示该彩色流光 |
| true | true | 有 | 仍只绘制预期一次，不重复叠加 |
| true | 任意 | 未配置、未知或 default | 保留该调用原有 glint 分支 |

对于未知 ID 加 forceFoil，默认把无效配置整体回退，不能无意让普通物品发光；若产品明确允许“强制原版流光”，用有效的 `default` 外观单独表达。空 stack 返回无自定义配置。渲染查询不使用 `getOrCreateTag()` 创建或修改物品数据；1.20.1 的只读查询用可空 `getTag()` 和类型检查。数据修改放在实际配置写入流程。

`removeGlint` 的含义是移除自定义配置，不是删除原版附魔，也不是让附魔物品永久失去流光。

## 按原调用选择对应层

先找到目标模型真正使用的 foil helper，再匹配其分支：`getFoilBufferDirect`、非 direct helper、实体或透明模式、特殊道具以及护甲入口仅是核对线索。保留原方法给出的 foil boolean，附加 `forceFoil`；不要重复用 `stack.hasFoil()` 覆盖调用方已有的决定。

从目标版本原生 RenderType 复制**相同模式的状态契约**，只替换需求涉及的纹理或 shader。RenderBlender 参考状态为：

| 属性 | DIRECT_LAYERS | ARMOR_LAYERS |
|---|---|---|
| 格式/图元/初始缓冲 | POSITION_TEX / QUADS / 256 | 同左 |
| shader | RENDERTYPE_GLINT_DIRECT_SHADER | RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER |
| 纹理 | 所选 PNG，blur=true、mipmap=false | 同左 |
| 写入/背面/深度 | COLOR_WRITE / NO_CULL / EQUAL_DEPTH_TEST | 同左 |
| 混合 | GLINT_TRANSPARENCY | 同左 |
| 纹理变换 | GLINT_TEXTURING | ENTITY_GLINT_TEXTURING |
| layering | 未额外设置 | VIEW_OFFSET_Z_LAYERING |

这张表描述参考构造值，不能据此确认所有目标渲染模式都应使用它。EQUAL 依赖匹配的基础几何先写入深度；透明材质、偏移、不同变换与多 pass 顺序会改变前提。保留正确 output target、layering 和纹理矩阵复位，不能将 glint 当普通 alpha 混合半透明模型处理。

不要拿 `POSITION_TEX_COLOR` 顶点发送给要求 `POSITION_TEX` 的 shader/RenderType。若要自定义顶点色，先同时修改 shader 声明、注册格式、RenderType 与真实 consumer；多层 consumer 的顶点转换也要匹配。

## Mixin 与上下文

- 优先在持有 `ItemStack` 的调用点传递解析后的配置。必须桥接静态 helper 时，让外层作用域 `push(config)`，内层调用结束在 `finally` 中 `pop()`，恢复前一个值；异常也必须恢复。
- `ThreadLocal<Deque<...>>` 只能隔离线程，仍需要正确 push/pop；线程结束或栈为空时清理。查询空上下文返回原层。不要让逻辑服务器加载这个客户端上下文类。
- 可以采用目标依赖支持的整方法包装实现 finally，或控制调用方建立作用域。先核对包装 API 是否可用；不能仅把 HEAD/TAIL 改成另一组注入就声称异常安全。
- boolean 的 `ordinal` 是对应类型参数中的序号，不是所有参数的位置。参照目标签名确认；重载方法尽量明确描述符。确认 Mixin 配置、refmap、发布 JAR 清单和运行日志中的注入成功情况。
- 只变更 glint 分支，无需重写整个 ItemRenderer 或替换原模型 loader。

## 缓冲与状态

固定九色可按 `(appearanceId, mode)` 缓存稳定的 RenderType。颜色信息由纹理资源及层身份携带，即使多个物品同帧排队也可正确分组。不在每个物品、每帧创建新 RenderType。

若使用 uniform 染色/彩虹动画，配置写入发生于真实 draw 的正确 shader 实例；“写颜色 A → 提交 A 顶点 → 写 B → 最后一起 flush”不能保证 A 仍使用 A。可选方案是每组参数使用稳定层及 draw 时回调、顶点携带参数，或受控 flush。缓存连续颜色或时间时须限界，不能把每帧时间作为无限增长的 key。

检查 BufferSource 是否提供所选 glint 层的独立缓冲、何时 flush、与基础层的顺序。只 flush 确有依赖的层和作用域，避免复制参考模型无条件 `endBatch()` 影响其他排队内容。不要在还有顶点待绘制时销毁/更换其所依赖的状态。

## 资源重载与迁移

固定纹理使用稳定 ResourceLocation；同路径资源重载应更新纹理内容，不把此机制说成每次都需要重建 RenderType。若持有 shader 实例、uniform、图集 sprite 或 GPU 句柄，则在相应重载回调替换失效引用。自定义 glint PNG 是独立纹理，本身不必拼入 Cosmic/block atlas。

目标工程与参考版本不同时，检查物品数据是否改为组件、渲染是否转为其他提交模型、shader 注册和 buffer API 是否改变。先在目标版本取得一条可运行路径，再扩大材质功能；版本声明写宽不等于兼容已经成立。
