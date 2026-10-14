# 算法与数据契约

以下“参考”特指 Re-Avaritia Forge/Neo 的主 cosmic shader；源码位置见 sources.md。算法可迁移，数值和接口不应当被当成所有 Cosmic 实现的标准。

## 视觉机制

参考顶点阶段计算 `fPos=(ModelViewMat*vec4(Position,1)).xyz`；片元阶段取 `normalize(-fPos)`，再按 pitch、-yaw 旋转。负号和 GLSL 的行向量乘法是参考的一部分，复现时不应随意换成另一个矩阵约定。

Position 是传入 GPU 的顶点，CPU PoseStack 通常已作用于它。不要从变量名推断它仍在物品局部坐标。先追踪 CPU 变换，再判断 ModelViewMat 的额外变换；重复应用 model/view 会导致漂移和双倍旋转。

每层将方向绕不同伪随机轴旋转，转经纬度：

```text
u0 = 0.5 + atan(ray.z, ray.x)/(2π)
v0 = 0.5 + asin(ray.y)/π
mult = 16 - layer
layerScale = 0.5*mult + 2.75
u = u0 * layerScale * externalScale
v = (v0 + time*0.0002/externalScale) * layerScale * 0.6 * externalScale
```

共 16 层；每层用 16×16 周期网格，整数 hash 选 101 个槽中的一个，只有 0..9 采样对应 sprite。再做四向旋转与翻转，从 sprite 的图集矩形采样。不同旋转、尺度、亮度与滚动速度叠加出空间纵深感。`10/101` 是槽位占比，不能当成精确屏幕星点覆盖率；hash、图标形状和层重叠都会影响结果。

这是方向驱动的多层图集采样，使用了经纬度 UV，也包含滚动，但不是直接让一张星空贴图沿物品 UV 滚动；没有有限体积射线积分。无法据此声称有真实体积视差、真实星系距离或严格全天球无缝。两极通过 smoothstep 衰减；多层旋转减轻一致接缝观感，但不证明不存在接缝。

## 遮罩与亮度

- `mask=texture(Sampler0,texCoord0)`，最终 alpha 乘 **mask.r** 和 opacity。
- 星体亮度也用 **tcol.r**，不使用星体 alpha。因此把星体换成“透明底白色图”但保留透明区白色 RGB，会出现方块亮斑。
- 背景 RGB 随 time 周期变化；星体 RGB 来自每层程序配色，彩色 PNG 的 G/B 并不直接成为星体颜色。要彩色星云必须改 shader 的颜色来源。
- 光照混合为 `vertexColor.rgb*0.2+0.8`，视觉上弱受光照影响，不等于实际发光照亮环境，也不等于 bloom。
- 源码 `col += vec4(r,g,b,1)*a` 同时累计 alpha，再乘 mask.r/opacity 并 clamp。因此灰阶遮罩和 opacity 不一定呈线性视觉强度。新设计可将 RGB 累计和覆盖 alpha 分开，但应标记为行为改变。
- Forge 无尽剑的 mask 是 16×32 动画图，alpha 全为 255，红通道才是区域权重；不能通过“看透明轮廓”理解该 mask。其二维壳需要深度和底层模型共同约束。

## 参数表

| 输入 | 来源和单位 | 实现约束 |
|---|---|---|
| UV0 | 遮罩在 block atlas 的 UV | 不是效果方向；非图集纹理不可直接使用此 UV |
| Sampler0 | block atlas，包含 mask 和 10 个星体 sprite | 纹理存在不等于已 stitch 到这个 atlas |
| Sampler2 / UV2 | lightmap / packed light | 不要与 NEW_ENTITY 的 UV1 overlay 混淆 |
| cosmicuvs | 10 个 mat2，总共 40 float | 每个按 `[u0,v0,u1,v1]`；GLSL 按列索引 |
| time | Forge/Neo 物品传 game ticks；legacy 用 CCL render time | tick 与秒显式转换；使用 partial tick 可改善平滑度 |
| yaw/pitch | 弧度，参考物品来自玩家，盔甲来自被绘制实体 | 不能宣称等于实际 camera 的姿态 |
| externalScale | 世界通常 1；现代 GUI 100；legacy GUI 25 | 必须 >0，JSON 默认 0 不能直接 draw |
| opacity | 物品通常 1；matter cluster 取容量比 | 不应复制业务物品硬编码到通用渲染器 |
| ModelViewMat/ProjMat | 当前 draw 的矩阵 | GUI、手持和世界物品可能不同 |

`externalScale` 改变空间频率；在非零条件下上式 v 中的时间项展开为 `time*0.0002*layerScale*0.6`，不受 externalScale 影响，不能把 scale 直接当作滚动速度。

## 忠实复现与改良

先固定时间、相机、分辨率，获得参考基准；再单独改变参数。参考使用 `pow(tu,float(tv))` 决定旋转，存在 0^0、精度和跨 GPU 一致性风险。改良可以用显式整数 hash；不要在声称逐像素复现时悄悄替换。

可把逐层常量旋转、配色预计算；不要先把所有参数都变成每物品独立 uniform 再期待自动批处理。质量预算按覆盖像素×层数×采样成本评估；大面积护甲与满屏物品格都要测。提前遮罩裁剪可减少工作，但 discard、透明边缘和纹理导数的行为需一起检查。

新方向实现可以选：世界方向固定、物品局部固定、屏幕固定、有限深度局部视差。世界方向模式只逆变换方向的旋转部分；局部模式需要清楚的 inverse model/normal 契约。GUI 可采用稳定的虚拟观察方向场。它们是设计选项，并非参考 shader 已经提供的开关。
