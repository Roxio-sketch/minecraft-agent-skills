# Forge 1.20.1 接入路线

本页是待在目标工程实施的接入方法，不是可直接运行的 Java 插件。目标固定为 MC 1.20.1 / Forge 47.x / Java 17，具体 Forge 小版本、mappings、渲染加速和光影包从目标项目读取。

## A. 内部星空折射

优先级较高：窗口背后采样自己的内部场，不需要世界颜色/深度。

1. 模型先解析 overrides 和 display transform；普通 baked item 复用原 quads。BEWLR、ModelPart、骨骼动画使用其实际几何入口，不因物品栏图标成功就认为穿戴模型已适配。
2. 提供统一局部晶片坐标与内部背景坐标。多面立体物体若每面使用独立 0–1 UV，会出现晶片不连续；需要统一局部空间、选定投影或专门三维晶片场。
3. 选择背景实现：直接以偏折后的坐标/方向查询内部程序场，或提前绘制内部材质纹理再分色采样。直接程序场可能要计算三次完整星云，应评估成本；已有 2D 内核不是任意三维射线折射器。
4. 基础纹理 alpha 先决定轮廓，区域 mask 决定哪里换材质；底色、光照/overlay 和 glint 的保留策略显式实现。制作台的 Shape 只是合成 mask，必须替换成真实物品数据。
5. shader/RenderType/VertexConsumer 的 VertexFormat 相同。每次 draw 绑定当前参数，必要时按材质分组或有界 flush，资源重载后获取新实例与 sampler。

平面剑可以使用虚拟薄层；“剑刃内部空间”不要求真实模型有同等厚度。对于完整水晶实体的进出面光路、内部反射或焦散，需要另外的几何与光线算法。

## B. 世界场景折射

```text
已完成的背景阶段 → 颜色/所需深度快照
                         ↓
实际载体的 mask、深度、局部晶片坐标 → 折射 pass → 指定输出目标
```

先指定捕获时机。GUI、第一人称手、世界透明物体不能都默认使用 AFTER_LEVEL 的同一快照；是否包含云、粒子、水、其他透明对象取决于阶段。写进实现说明并实测。

- 采样输入与本次输出使用独立的纹理附件，不能把当前写入的颜色纹理当作 Source；如果采深度，同样核查附件别名与写入关系。复制颜色不表示深度也安全。
- 深度判断先重建到一致单位：沿射线距离比较沿射线距离，视空间 Z 比较 Z。禁止 `length(viewPos)` 与 `-viewPos.z` 混比。
- 以载体覆盖和未扭曲位置深度处理基本遮挡；偏移 UV 处的前景颜色可能被拉进背后物体，需要遮挡判断和回退。屏幕没有保存屏外/被遮挡的信息，clamp 只能防止越界，无法恢复内容。
- 颜色已在场景输入中时，注意避免源背景被 alpha 混合二次叠加。严格定义预乘/非预乘和最终替换/混合；不能同时做一次 RGB mix 再无意识 blend 一遍。
- 多个折射对象可共享背景快照，也可按层重拍；二者对重叠物体结果不同。先确定层语义再优化拷贝次数。
- 管理尺寸变化、窗口最小化后的零尺寸、资源重载、退出世界和关闭时清理。异常也恢复原 framebuffer、viewport、深度、blend、scissor、矩阵及修改过的状态。状态所有权依实际 pass 定义，不必盲目全量 glGet。

Oculus/Iris 和特定 shaderpack 单独验证目标 framebuffer、投影、深度格式与显示转换。当前技能不提供其兼容性保证。

## GLSL 150 生成资源的契约

`scripts/build_shader.py --out <目录>` 只输出 `assets/crystalrefraction/shaders/core/crystal_refraction.{vsh,fsh,json}`，不生成 mod、Mixin、注册事件或 RenderType。重复运行相同内容允许；已有文件不同则拒绝覆盖。

- 顶点输入 Position、UV0；注册选用匹配 POSITION_TEX 的格式，用 ModelViewMat/ProjMat 提供正确投影，UV0 覆盖背景 0–1。
- `Source` 为绑定的独立纹理；生成器不设置 sampler 对应的 Java 纹理 ID。
- `Resolution` 当前同时表示输出与源纹理尺寸，示例要求二者相同。半分辨率源需拆分 OutputSize 与 SourceSize 并修改像素尺度。
- 当前 fragment 的局部坐标由全屏 UV 生成，是全屏/离屏预览 host。物品接入需以真实材质坐标替换它，并加入真实 alpha/mask/depth；生成资源不能原样成为全物品通用 shader。
- 默认参数只是示例。Java 在真正绘制前上传 Time、Seed、Cells、Jitter、BendPx、Dispersion、Ior、BevelPx、EdgeLight、TintMix、Speed、BlurPx、Amount、Tint、View、Shape、Debug；由 JSON 声明不代表存在 NBT/配置文件读取逻辑。
- 版本检查、编译/链接、uniform 优化移除及资源重载均以目标客户端为准；WebGL ES 编译通过不证明桌面 GLSL 150 host 或生产包成功。

已有 Cosmic Material Lab 可参考其原几何包装和 draw 参数绑定方式，但新 sampler/pass/算法全部重新验收。
