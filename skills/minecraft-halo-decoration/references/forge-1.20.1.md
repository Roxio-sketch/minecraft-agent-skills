# Forge 1.20.1 接入契约

本页是实施指导，不是已编译的 Halo 模组。目标 Java 17；核对实际 mappings、Forge 小版本和模型入口。先选几何与场景，再选格式与 RenderType，不把 GLSL/RenderType 独立存在视为接入完成。

## 几何入口

- GUI 背景 quad：保持物品 GUI 锚点与原姿态，独立决定装饰是否随物品转动；主体、数量、耐久条、提示框的 UI 顺序分别核验。GUI 平面不是模型某个随机 face。
- baked item：确认最终 override 模型、applyTransform 返回值与原 passes；可装饰已解析模型但不能递归调用同一个 wrapper。加装饰不能吞掉原 glint 或让弓模型停在待机。
- 模型固定环：在正确模型局部空间构造 quad/环带，不直接复制 GUI 坐标和深度偏移。double-sided/cull、左右手镜像和非均匀缩放需要明确处理。
- billboard：明确朝向真实相机还是 GUI 虚拟相机；不要重复施加 camera rotation。模型固定与相机固定是不同意图。
- BEWLR、骨骼动画、穿戴：在该 renderer/骨骼/实体层上挂接。未取得有效入口则保留原行为并报告未支持；物品栏图标成功不等于穿戴成功。

## 纹理与顶点

独立纹理使用 0–1 UV；放进 block atlas 则注册 source 并用对应 sprite UV，重载后刷新。不能用 atlas UV 采独立 PNG。导出颜色已烘焙：顶点 RGB 默认白色，顶点 alpha 可放动态不透明度。

`ShaderInstance` 注册、RenderType 和 VertexConsumer 三者格式一致。简单无光照装饰可考虑 `POSITION_COLOR_TEX` 配套自有 shader；若使用实体 shader/NEW_ENTITY，必须提供该格式所需 UV1/UV2/normal 等数据，不能因为画面是四边形就省略。VertexConsumer 属性顺序按目标格式确认。资源重载替换实例及 uniform 引用。

先在 CPU 用已采样时间生成局部变换，绕明确 pivot 旋转缩放后输出顶点。基础 PNG不烘焙变换，因此动态只施加一次。矩阵应在调用点复制或栈 push/pop 范围内使用，不留可变 PoseStack 给以后帧。

## Blend、深度和顺序

以下定义是新实现的推荐契约，不代表原参考所有同名 RenderType 的行为。`Cs` 为 straight RGB，`a=textureAlpha*vertexAlpha`。

| 模式 | RGB 方程 | RGB factors |
|---|---|---|
| alpha | Cs*a + Cd*(1-a) | SRC_ALPHA, ONE_MINUS_SRC_ALPHA |
| additive | Cs*a + Cd | SRC_ALPHA, ONE |

单独决定目标 alpha：例如保留目标 alpha 可用 `glBlendFuncSeparate(srcRGB,dstRGB,ZERO,ONE)`；累积覆盖则 alpha factors 为 ONE、ONE_MINUS_SRC_ALPHA。不要直接用 RGB factors 推断 alpha。若 shader 已输出 RGB*a，则需 premultiplied 对应 factors，否则会出现 alpha 平方/暗边。所有方式还要核对混合方程为预期的 ADD，及 framebuffer 的 sRGB 转换/格式。

材质自发光不意味着 RGB 必須超出 1，也不意味着环境照明。导出 PNG 是 LDR，不含 HDR；需真实 bloom 时另加 HDR/后处理流程和验证。

常见基线：世界光片 LEQUAL 深度测试、仅颜色写入、透明区不写深度；基础物品按原流程正常写深度。根据选择的顺序使主体遮住背光。装饰先画再被主体覆盖或在主体之后利用正确背面深度，都需核验实际 GPU draw 顺序；Java 调用先后不保证不同 buffer 立即提交。

EQUAL 适合重放完全相同的表面，不适合扩展到轮廓之外的 Halo。GUI 和世界的深度朝向可能不同，不能硬写一个通用 z=±0.01 偏移。使用分开的上下文测试；不要以关闭深度解决穿透。

与 Cosmic/glint 共存：先为实际目标写下 pass 表（装饰背层、主体/Cosmic、原生 glint、需要的前景装饰），明确每层 reads/writes、格式和 flush。不是所有项目都需要四层；只绘制用户选择的层。顶点色优先携带每物品动态，uniform 参数绑定在真正提交时；避免 static currentStack 或每帧新建 RenderType。只 flush 所属类型/受控 buffer，避免无条件全局 endBatch 打乱调用者。

## 生命周期与缓存

RenderType 缓存按纹理、blend、depth、cull、格式等有限键，不能按 angle/time/每帧 RGB 增长。动画时间按帧采样，明确暂停/GUI 无世界时 fallback；两个方向相反的环不能因共享 mutable uniform 变成同方向。

重载、世界退出、窗口缩放、主体被移除、shader 失败均保持可恢复状态；try/finally 对称还原栈和自有状态。优先使用框架状态接口，避免原始 GL 调用绕过状态缓存。无后处理需求就不复制整个 framebuffer。

纯资源包可以替换现有 Halo 纹理；不能凭空增加新 layer、动画参数 parser 或 renderer。新 Java 代码需重新构建生产 JAR并重启，资源重载不热替换 Java。
