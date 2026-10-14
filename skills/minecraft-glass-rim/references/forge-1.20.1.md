# Forge 1.20.1 / Java 17 接入

本页为实施契约，未交付已运行 Forge mod。先检查目标工程的 shader 注册事件、RenderType 和具体模型输出。普通 baked quads、ModelPart、BEWLR/骨骼模型分别适配；保留原 overrides、动作、显示变换和 foil 路径。

1. 将 assets/rim.glsl 的函数拼入片元 shader。注册 ShaderInstance 时使用与 RenderType、VertexConsumer 相同的格式，例如带 Normal 的 NEW_ENTITY。JSON 中的 uniform 名称/类型与 Java 上传一致。
2. 顶点阶段输出视空间位置 P 和视空间法线 N。P 必须含当前物品 pose 与视图变换；不要将已经变到视空间的顶点再重复乘相机。N 使用对应模型视图矩阵的左上 3×3 逆转置；奇异缩放拒绝或回退。片元 `V=-P`，交给内核归一化。
3. GUI 可能使用正交投影，V 应使用恒定视方向而非把相机原点作为透视眼点；可通过独立参数选择。非 GUI 根据实际当前视图构造，不直接拿玩家 yaw/pitch 代替。
4. 原纹理 alpha 先做原模型 cutout 判定，局部 mask 再控制边缘强度/覆盖。原材质和壳材质是替换还是叠加需事先确定，不能随意重复绘制半透明表面。
5. 透明壳通常深度测试 LEQUAL、深度写入关闭、常规 alpha 混合；主体维持正常深度。完全封闭透明体需要排序方案，简单关闭 cull 会叠加前后面，且多物体交错不能靠单物体排序彻底解决。
6. 同批次参数保持稳定，按材质分组或 draw 时上传；flush 前矩阵有效。资源重载重新取得 shader 和 uniform；失败时保留基础模型。

嵌入方式（参数均来自本次 draw，而非全局可变的上一件物品）：

```glsl
vec4 glass = glassRim(viewNormal, -viewPosition, baseLinear, edgeLinear,
    power, gain, baseAlpha, rimAlpha, ior, fresnelMode, twoSided,
    timeSeconds, pulseAmount, pulseHz);
fragColor = vec4(glass.rgb, glass.a * coverageMask);
```

示例只展示内核调用，不包括原贴图混合、雾、光照、overlay 和 glint。若要求受环境光影响，单独决定 base 与 edge 各自是否受光；全亮材质不产生周围方块照明。

接入完成需在 1.20.1 Forge 生产 JAR 上看 GUI、左右手、第三人称、掉落物、展示框及遮挡。预览使用 Three.js，不能以预览通过宣称 Minecraft 顶点布局和状态已通过。
