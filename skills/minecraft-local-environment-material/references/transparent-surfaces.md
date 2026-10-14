# 可选透明表面与流体材质化

适用：保持原世界几何，在指定区域改变水面、流水或透明区块的表面材质。目标后端 Forge 1.20.1；Cosmic 仅为材质函数之一。实现与验证状态分开记录于 [透明层验收](surface-validation.md)。

## 先选模式，再选后端

| 模式 | 表面职责 | 背景、几何与合成 | 本研究工程 |
|---|---|---|---|
| 基础 basic，默认 | 不接管透明表面 | 水下不透明地形可材质化，之后由原 renderer 绘制水/玻璃；最终水像素仍会因背景变化而变化 | 实现 |
| 材质化 material，可选 | 在原透明几何 fragment 中评价区域和材质，改变表面 RGB | 原网格、深度、排序、alpha、纹理帧/流动 UV、光照和雾继续使用；不额外绘制一层水 | 0.2.0 的原版区块后端；water sprite 和 entire layer 两种明确范围 |
| 完全异化 alien，显式高级能力 | 可更换介质、吸收、散射、折射、反射、波形、透明度策略 | 必须拥有对应 fluid draw、背景快照、前后界面及 resolve 契约；若位移，深度/法线/阴影/速度缓冲必须同步 | 架构设计，未实现；请求时明确拒绝，不伪装成全屏覆盖 |

`material` 保留透明度意味着仍能看到水下世界：水面和水底可共享同一材质场，但不能保证最终颜色完全等同于不透明岸边。需要更不透明或改变折射介质时属于 alien 的艺术选择，不能偷偷更改 alpha。材质波动、流动 UV 与真实顶点位移是三个不同需求。

基础模式也不是“兼容一切”的承诺：透明表面可以保持 renderer 原状，但不透明地形 backend 仍需自己通过能力验证。当前工程在 Embeddium/Rubidium/Oculus 存在时保守禁用实验效果；不是这些项目原则上不可能支持。

## 数据流与具体 Forge 接入

原路径：流体状态/方块模型 → chunk mesh → 排序 → `renderChunkLayer(translucent)` → shader → 原目标及混合。

接入点在 `setupRenderState()` 之后，获取该层 `ShaderInstance` 时。替换这个局部引用，仍由 LevelRenderer 填写原 Sampler、MODEL_VIEW、PROJECTION、ChunkOffset、Fog，绘制原 VBO。不是在 `AFTER_TRANSLUCENT` 或 `AFTER_LEVEL` 覆盖最终颜色。

`surfacePosition = Position + ChunkOffset` 为相机相对世界位置。CPU double 计算 `regionCenter - actualCamera`；fragment 中相减得到稳定材质坐标。区域 Mask 评价的是每个流体/玻璃片元自己的表面位置，水底只属于另一条地形路径。透视插值自动跟随真实斜面与流体高度。

对覆盖率 w：

```
original = atlas(UV) * interpolatedVertexColor * ColorModulator
specialRGB = Material(surfacePosition, geometricNormal, time) * lighting * flowDetail
rgb = mix(original.rgb, specialRGB, w)
alpha = original.a
output = originalFog(vec4(rgb, alpha))
```

保留直通式 alpha 契约和原混合函数，不能混入预乘 alpha 算法。区域外和未选中片元执行原颜色计算。不要 `discard`、写 `gl_FragDepth` 或额外双面叠加来制造边界；使用 coverage、材质内部噪声和边缘发光。大规模 bloom 是另一个附件与合成契约。

Forge `LiquidBlockRenderer` 使用 `IClientFluidTypeExtensions` 的颜色和 alpha、`ForgeHooksClient.getFluidSprites` 的 still/flow/overlay。流动方向已编码进 UV；流体高度和坡度已进入几何。该版本输出的 Normal 总是 (0,1,0)，所以侧面/坡面用位置导数恢复几何法线，且在分歧分支前计算。光照可从 UV2 取得；原顶点颜色混合了生物群系 tint 和面阴影，若要精确拆分必须在 mesh producer 增加独立数据，不能从蓝色猜法线或光照。

原版水没有 Shader Pack 意义上的波浪顶点位移和场景折射。本后端保留实际存在的网格、流动纹理、透明背景；不会把“未增加折射”描述成“保留了真实折射”。

## 对象选择与 Mod 流体契约

0.2.0 有两个选择：

- `water`：重载后从当前方块 atlas 查 water_still、water_flow、water_overlay 的矩形，用原 UV 选择。是明确的 **sprite adapter**，不是精确 Fluid ID。使用相同水 sprite 的自定义方块也可能被选中；改用其他 sprite 的 Mod 水不会自动被选中。无 sprite 时回退。
- `layer`：用户显式允许整个原版 `RenderType.translucent()` 区块层，包括其中的染色玻璃和标准流体。不是“只水”，也不包括 entity/BE 自绘透明物、粒子、手部、tripwire、cutout 普通玻璃。

要严格“某种流体”“流体而非玻璃”，在 `LiquidBlockRenderer` / block tessellation 生产阶段写入 material ID、或拆分独立 mesh bucket，重新设计排序与目标。shader 中按最终颜色、alpha 或 normal 识别对象均不可靠。sprite adapter 适合受控验证，不能推广成无条件通用识别。

Mod 流体按行为而非模组名分级：

1. 标准 chunk BLOCK format、原版 translucent shader、标准 atlas：可显式采用 layer 路径；精确筛选需独占 sprite 或生产者标记。
2. 自定义 IClientFluidTypeExtensions tint/alpha/贴图，但仍标准路径：保留实际顶点 alpha 与动画；验证 still/flow/overlay、发光、侧面、水下视角。
3. 自绘 BE/罐体流体、自定义 RenderType 或 shader、特殊混合/光照：必须独立 adapter，默认排除。
4. 熔岩常用 solid 路径；“所有流体”不是“所有半透明”。透明 lava 由实际 RenderType 与材质契约判断。

## 完全异化的必要契约

只有实际需要折射/吸收/波形变化才引入这些成本：

1. 透明层前捕获只读 opaque scene color/depth；确定是否包括实体、分辨率、颜色空间、MSAA resolve 和纹理所有权。不能从当前正在写的附件采样。
2. 流体 surface pass 提供同帧 world position、介质类型、法线（包括 normal map/波浪）、surface depth、alpha/IOR/absorption。world Mask 仍评价真实表面，不评价 refracted background。
3. 将采样点沿折射方向投影；用背景深度检查是否跑到表面前方、岸线遮挡或屏幕外。无有效命中时回退未偏移背景，不能夹取边缘造成拉丝。空间厚度用一致坐标计算；`opaqueDepth - waterDepth` 只是有遮挡条件的近似，不是每层介质厚度。
4. Beer–Lambert `T = exp(-sigma * thickness)` 在约定线性空间计算，Fresnel、散射和反射分开。若 fragment 已把背景包含在输出颜色中，不能再按普通 alpha 把相同背景混合一次。
5. 多层水/玻璃、交叠不同介质需要前后界面或有限 depth peeling/OIT/分层 resolve。单不透明快照无法表现折射后的透明背景和嵌套介质；明确层数上限、排序误差和 fallback。
6. 几何波浪若引入顶点位移，需 region 边缘平滑位移、同样的深度/normal/阴影/velocity，避免 mask 在未位移几何上评价。用户要求几何不变时仅允许 shading normal 扰动。

若 Shader Pack 已拥有折射、时域抖动、TAA、雾、PBR 或波浪，优先在其材质输入处分区域修改 albedo/emission/介质参数。不得在它之后重新覆盖 scene color，或用原版 shader 擦除其自有契约。

## 后端与失效条件

| 后端 | 策略 |
|---|---|
| Forge 1.20.1 原版 Fast/Fancy | 检查 BLOCK 格式、原 shader 所有者、主 FBO 和原版核心资源指纹；已验证项见验收记录 |
| Fabulous | translucentTarget + transparencyChain 与主 FBO 不同；0.2.0 主动保持 basic，新增适配后才能开放 |
| Embeddium/Rubidium | 需适配 packed vertex、chunk origin、材质 ID、shader compile/variant、透明排序；不能依赖原版 renderChunkLayer 内部调用仍存在 |
| Oculus 无启用光影 | 单独验证；“安装但关闭”不等于原版管线，当前保守禁用 |
| Oculus + Shader Pack | pack-specific hook/共同协议，保留 gbuffer、normal、water ID、fog、refraction、jitter/TAA；没有 adapter 就 basic/禁用，不试图强行替换 shader |
| 核心 Shader 资源包覆盖 | 检测代码/依赖 include 内容变化并回退，避免抹除自定义波浪/透明规则；只有纹理替换则保持 atlas 契约并重测 alpha |

Veil 1.21 的 Sodium vertex/normal producer、shader variant recompilation、Iris render target access 是“单独适配”的源码证据，不是 Forge 1.20.1 兼容认证。Veil Example 的 shader modifier 展示有结构的 shader 注入；不能对任意 pack 用字符串替换 main。Immersive Portals 的深度/Stencil/FBO 生命周期只借鉴状态职责；Stencil 无法提供流体层、厚度或折射。Lodestone 的透明 VFX/bloom 可做领域边缘，不替代水面材质路径。

## 预算、生命周期与降级

material 不增加常规表面 draw，不额外捕获 scene color；只增加位置插值、区域测试和区域内材质成本。先 Mask 再昂贵材质；透明 overdraw 和层数比方块半径更接近成本。多区域 CPU 剔除后按固定容量传入，定义 priority/覆盖率归并，不能每个区域重画整个透明层。

alien 预算包括快照带宽、normal/ID/thickness 附件、分层 pass、折射采样和时域历史。逐级降低层数、采样数、法线频率或仅对受控水体开启；最终回退 material，再到 basic。不能用降低屏幕分辨率修复错误的世界坐标。

资源重载重建 shader 与 sprite rectangles；透明排序/VBO 生命周期由原 renderer 管理，不跨帧缓存 mesh 指针。世界切换、死亡、退出、off 清掉区域与诊断队列；快照只由 owning render thread 销毁。GPU A/B probe 是显式诊断，会分配/回读附件与重复原几何，不计入常规性能。

至少验收：表面与水底分离、静水/流水/侧面、水下、范围边界、玻璃前后叠水、实体遮挡、纹理重载、resize、off、切世界。原版排序瑕疵应与同帧原版对照区分，不能把既有排序限制误称新材质缺陷，也不能用旧地形 probe 证明透明层正确。
