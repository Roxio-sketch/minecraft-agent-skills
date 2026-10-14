# 源码依据与缺陷

## 定位与证据等级

从用户的星空材质研究项目定位 `research/cosmic-material/sources/RenderBlender-master/RenderBlender-master/`。目录迁移后搜索 `GlintLayerManager.java` 与 `RenderBuffersMixin.java`，不要把个人机器路径硬编码到实现中。

`gradle.properties` 7–23 行声明 Minecraft 1.20.1、Forge 47.4.13、Parchment 2023.09.03-1.20.1、模组版本 1.1.2；`build.gradle` 18 行指定 Java 17。使用的是源码归档快照，没有据此确认上游最新版本或 commit。归档原 SHA-256 及逐文件摘要记录在 [source-manifest.json](source-manifest.json)。

下表行号属于该快照。文件均相对上述根目录。Java 简写根为 `src/main/java/committee/nova/mods/renderblender/`。

| 文件与范围 | 已读取事实 | 用途 |
|---|---|---|
| `client/glint/GlintManager.java:13–49` | 10 个 holder，含无纹理 `default`；9 个实际颜色；`apply` 写两个 NBT 键 | 配置与颜色注册 |
| `client/glint/GlintProvider.java:6–9` | `getGlint(stack)` 可空，另有 `alwaysGlint(stack)` | 自定义物品选择外观 |
| `client/glint/GlintLayerManager.java:19–50` | 分别创建 DIRECT、ARMOR 两组 RenderType | 模式与状态配置 |
| 同文件 `:58–61` | `registerAll` 仅注册 DIRECT 缓冲 | 缓冲覆盖范围 |
| 同文件 `:64–92` | provider 优先；NBT 的选色分支要求 always=true | 原逻辑及缺陷 |
| `init/mixins/ItemRendererMixin.java:44–65` | 静态临时 stack；只修改 `getFoilBufferDirect` 的一个 boolean 和 `glintDirect()` 表达式 | 实际注入覆盖范围 |
| `init/mixins/RenderBuffersMixin.java:17–20` | 注入 `RenderBuffers.put` 后调用 `registerAll` | 缓冲注册入口 |
| `src/main/resources/renderblender.mixins.json:6–14` | client 列表含物品和缓冲 Mixin，`defaultRequire=1`，无护甲 Mixin | Mixin 是否登记 |
| `api/client/model/bakedmodels/WrappedItemModel.java:166–175` | 直接取基础 RenderType 的 buffer 绘制，无 foil consumer 组合 | 自定义模型绕过 helper |
| `client/model/cosmic/CosmicBakeModel.java:50–88` | 基础包装模型后全量 flush，再写 Cosmic uniform、渲染 mask 壳 | 星空与流光顺序风险 |

## 原始配置语义

NBT 键为 `renderblender:glint`（字符串）和 `renderblender:glint_always`（布尔）。ID 是 `red`、`yellow`、`blue`、`orange`、`green`、`purple`、`white`、`pink`、`aqua` 和 `default`。`default` 的 texture 为 null，含义是回到原调用的外观，不能等同于 `white`。`ChatFormatting` 存在于 holder 元数据，但该 RenderType 路径的颜色来自 PNG，不是名称颜色或该枚举自动给 shader 染色。

仅针对原 1.20.1 实现的复现输入：

```mcfunction
/give @s minecraft:diamond_sword{"renderblender:glint":"red","renderblender:glint_always":1b}
/give @s minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:1s}],"renderblender:glint":"blue","renderblender:glint_always":0b}
```

第二条用于暴露已附魔物品不能在 `always=false` 时通过 NBT 选色的缺陷，不是保证呈蓝色的成功示例。命令只有在对应模组、注入和渲染路径有效时才可能显示效果；不要直接把旧 NBT 命令用于现代组件格式。

## 不应照搬的实现

1. **选色与强制发光耦合。**`processStack` 78 行限制 NBT 选色必须 always=true。修正为独立解析颜色、独立判断 foil。provider 的现有优先级与 NBT 路径不同，需要明确保留还是按用户要求调整。
2. **物品 hook 覆盖有限。**只替换 `getFoilBufferDirect` 内 `glintDirect()` 的一次调用；同方法的其他实体分支、非 direct、透明、特殊 foil helper、穿戴护甲都需要核对目标版本入口。方法名存在不等于调用一定经过这里。
3. **护甲路径没有闭合。**全源搜索 `processArmor` 只发现声明，无调用者；Mixin 配置也无穿戴护甲入口。该方法的 provider 分支误取 DIRECT_LAYERS，NBT 失败回退为 `glintDirect()`，都不能作为正确护甲接入模板。
4. **仅缓存了部分缓冲。**`registerAll` 只处理 DIRECT，且从 `RenderBuffers.put` 进入时反复扫描。扩展时按目标 BufferSource 的实际调度核对注册、flush 和生命周期；不能断言未注册固定缓冲就永远不会绘制，也不能断言自动回退足够可靠。
5. **单一静态 stack 不能正确重入。**内层 render 结束会清掉外层 stack；TAIL 也不提供异常清理保证。使用带 finally 的作用域或传参。
6. **注册时序固定。**静态初始化只遍历当时 HOLDERS。后加 holder 不自动生成 RenderType。扩展注册应在冻结之前完成，或按 `(外观 ID, 模式)` 惰性建层并限制缓存规模。
7. **未知值回退丢分支。**NBT 部分直接返回 `RenderType.glintDirect()`。修正时保留传入 origin，避免把护甲、实体或透明分支误换成普通 direct。
8. **自定义 Cosmic 绘制不保证走 foil helper。**`renderWrapped` 直接用基础 buffer；现有模型调用了物品渲染器也不等于彩色流光已附着。按 [cosmic-and-custom-models.md](cosmic-and-custom-models.md) 追踪实际顶点消费者和批次。

这些是静态源码结论。这里只提供修正策略，没有将上述修正写回参考仓库，也没有声称 GPU 或某个整合包兼容测试已经通过。
