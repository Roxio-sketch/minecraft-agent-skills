# Forge 1.20.1 接入

随附模板使用Java17、ForgeGradle6.0.54、Gradle8.8、official1.20.1和Forge47.4.10。`gradlew build`生成生产JAR并执行reobfJar。首次依赖下载需要可信网络；完整缓存可--offline。

## 实现链路

Item注册 → IClientItemExtensions → BEWLR.renderByItem → scene JSON → TextureTarget颜色/深度 → 三角形场景 → POSITION_TEX窗口shader → 当前物品表面。

SceneWindow.java为注册，Scenes.java解析/几何，WindowRenderer.java相机/FBO/合成/状态，WindowAudit.java可选测试。模板只注册一个展示牌物品，未实现任意baked items、穿戴或原生glint。

## 自由载体适配

- 二维工具：保留原模型解析与overrides，在指定区域提供窗口几何/材质；保留动作、原纹理与装饰。
- 三维模型：使用实际窗口面与局部坐标基，多扇窗明确各自投影；不能用普通UV壳代替。
- BEWLR/骨骼：在部件变换确定后绘制，明确flush边界；不能用整体手持矩阵替代骨骼挂点。
- 多窗口：模板共享一个FBO并立即消费，无缓存。延迟消费需独立输出或分配策略；cache key含scene、视图、时间、资源代次、尺寸。视点相关场景不能只按scene ID复用。

模板会提交收到的BufferSource。大型模组在已知批次边界组织离屏绘制，不能假定任意第三方多pass渲染都允许中途提交。

## Backend扩展

可增加纹理网格、方块模型、实体展示、粒子、程序场。各自补顶点格式/材质、atlas、light/tint、dispatcher和递归策略。状态scope只覆盖现有poscolor/posTex路线，改变灯光/雾/stencil要扩充。

视觉时间与游戏模拟分开，同一场景一帧绘制多次不能重复tick真实实体。模拟状态用稳定实例ID管理，资源定义可共享。默认不修改玩家真实世界。

## 世界视图/镜面：接口规划，未实现

同世界异地视角也需相机、projection、frustum、render lists、fog、dispatcher、target、lightmap和兼容层隔离；跨维度另有客户端世界/区块生命周期。禁止简单替换Minecraft.level后调用renderLevel就宣称可靠接入。

镜面需要反射相机和裁剪平面；窗口视差是眼位与开口的映射。子视图选择禁止重入/旧帧缓存/有限递归，冷缓存需回退。不要在每次renderByItem中无限重画完整世界。

架构来源与可迁移结论见 [参考快照](architecture-sources.md)。它们目标1.21.1，只借鉴状态、视图和生命周期方法，不复制API或源码进入模板。

## 生命周期

shader在MOD bus注册；reload清scene缓存并释放FBO；离开世界释放，尺寸变化重建。State的finally恢复触及状态。额外缓存要有资源代次，世界backend要处理disconnect。Oculus/Iris等具体管线重新实测，不继承普通环境认证。
