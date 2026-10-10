# Forge 1.20.1 接入

参考工程使用 Forge `1.20.1-47.4.10`、Java 17、官方 mappings。客户端命令和 `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS` 都注册在 `Dist.CLIENT`；专用服务器不加载渲染类。

## 演示命令

```text
/lightninglab orbit       # 玩家上方持续环绕电流
/lightninglab strike      # 使用 recipe.seed 的可复现单次闪击
/lightninglab strike 123  # 指定 seed，便于复现同一形状
/lightninglab random-strike # 随机 seed 探索变体
/lightninglab rain          # 水平范围内多道落雷，严格限制分叉
/lightninglab random-rain   # 随机 seed 的落雷场
/lightninglab mode orbit|strike|rain
/lightninglab reload
/lightninglab off
```

## 替换锚点

演示把 `anchor` 放在玩家位置，工作台用水平棍状物表示附着物；接入物品或实体时，在动作/模型变换完成后的世界空间得到棍状物中心、朝向和局部锚点。`OrbitField.sample` 当前生成世界 X 轴/水平环弧；接入可转动模型时先在局部空间采样，再把每个点按模型朝向旋转并平移到世界空间。起止闪电直接传给 `BoltGeometry.generate`。不要用 GUI pose 代替第三人称或世界挂点；第一人称要单独定义视图空间策略。跨维度、瞬移和持有者消失时清理效果。

## 服务器边界

服务器只应决定“何时、从哪里到哪里、用哪个 seed、是否造成 gameplay 后果”。客户端可以按同一 seed 生成外观。伤害、点燃、雷劫阶段和方块修改必须在服务器事件中独立实现，不能从渲染段列表反推。

## Shader/混合

核心使用 POSITION_COLOR_TEX、实际相机投影的独立凸段/圆接点和浮点 UV coverage；`bolt.json` 是传统加法，`bolt_vivid.json` 是渐变核心保色 alpha 混合，各自绑定独立 ShaderInstance。只有开启渐变且作用层包含核心时才启用 vivid，halo 先于 core 绘制；光晕经 `HaloGeometry` 生成凸段/圆接头，并由 `LightningHalo` 在独立深度缓冲中逐路径 MAX 后 ADD 到主场景。主干和光晕不写世界深度，保留深度测试；加法混合不等于穿墙。scratch stencil/depth格式与主目标一致，并完整恢复 GL 状态及 Mojang BlendMode 缓存。完整约束见 [成熟渲染](mature-rendering.md)。Oculus/Iris、Fabulous、透明水和其它后处理须在目标实例实测。

## 地面命中与事件寿命

导入集束配方后仍用 `/lightninglab rain` 或 `/lightninglab random-rain`。客户端从每道雷的云端起点向世界最低高度做 `ClipContext.Block.COLLIDER` 射线，命中位置同时作为主干终点和 `ImpactGeometry` 中心；未命中时回退到配置落点平面。屋顶等碰撞物可成为落点。

击地从该道事件的 `progressionTicks` 开始，与 `rainStagger` 一起错开。`RainGeometry.finished` 必须等待 `max(durationTicks, progressionTicks+impactDuration)` 再加最后一道延迟，避免雷柱消失时把击地尾段提前清理。渲染地面段保留自己的线宽，光晕比例按 strikeWidth 归一化，避免宽度串用 orbit 的细核心而过曝。各段输出的材质 alongA/alongB 均为 1，颜色继承实际主落雷末端而非地面局部进度；核心/光晕独立底色与比例继续生效。

当前地面电弧在落点附近水平展开，不会逐段贴合复杂地形；火花轨迹没有方块碰撞。HTML 仅使用平面地面，真正命中、遮挡和 GPU 表现仍需目标游戏实例实测。

## 可复用验证入口

`lightningCheck` 包含完整配方、旧输入、渐变比例、亚像素 UV 打包、凸光晕、击地颜色和原几何/速度/预算/生命周期检查。开发副本可运行 `./gradlew.bat runClient -PlightningGpuProbe=true`，自动使用专属验证存档，在 `run/lightning-lab/gpu-probes/` 输出三种末端色、厚光晕、原始高增益紫→金的 vivid/additive/仅光晕及结束帧并退出；加 `-PlightningStencilProbe=true` 检查启用 stencil 的目标。入口默认关闭，只启动独立开发客户端，不应用在玩家当前游戏进程。

## 迁入其他 MOD 的完整清单

整体迁移几何、配方、时间包络、材质、LightningRender、LightningHalo 和它们依赖的类；不能只复制 bolt.fsh。同步 assets/<modid>/shaders/core 的 bolt、bolt_vivid、bolt_halo、bolt_halo_composite 相关 JSON/GLSL，尤其 RoundJoint uniform 和 flat roundCoverage。统一替换 Java package、MOD_ID、ResourceLocation、shader JSON 中 vertex/fragment 资源前缀、mods.toml 与 Gradle mods 注册名；配置路径和命令名由目标 MOD 自行选择，旧 63 字段语义保持不变。新生成工程须先执行 lightningCheck/build，再显式运行 lightningGpuProbe 检查实际 shader/合成和事件；服务器触发同步、实体挂点与第三方 FBO 适配属于宿主接入验证。工作台 UI 的模式筛选/分组不在独立 renderer 内，宿主参数页需另行映射。
