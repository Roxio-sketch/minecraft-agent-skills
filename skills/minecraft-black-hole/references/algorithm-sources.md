# 原创内核、研究来源与边界

需要扩大世界黑洞、接入真实场景透镜或区分盘面旋转与径向吸入时，先读 [source-comparison.md](source-comparison.md)。它汇总 2026-09-29 对 ArcaneVortex 和五份补充黑洞源码的实际调用链、可复用方法、许可及不可照搬的缺陷；静态审查不等于改进已经通过 Forge 实测。

## 原创内核的数据流

`black-hole.glsl` 是无 main、无版本声明的 GLSL 内核；WebGL host 包装为 GLSL ES 300。它不复制 ArcaneVortex shader。

```text
局部相机射线 → 与计算包围球求交 → 有限步长偏折
    → 核心截断 / 盘面密度与辐射积分
    → 剩余透射率 × 最终射线方向的内部星域
    → host 显示转换 / 模型覆盖合成
```

包围球只限制计算范围，不是物品真实表面。方向每段向中心的垂直分量偏转，使用显式中点法求方向和路径位置，再归一化；偏折强度是美术参数。它不是 Schwarzschild/Kerr 度规下的零测地线积分，没有证明能还原光子球、事件视界尺寸或相对论影像。

`BHResult` 保存线性辐射 light、transmission、最终 direction、captured 与 exhausted。captured 是几何暗核命中；exhausted 只是路径预算耗尽，不能当成物理吸收。预览预算耗尽时仍用最后方向采背景，可能产生近临界偏差；诊断图 G 通道标记该区域。

盘坐标是倾斜后的局部位置。径向包络规定内/外边界，垂直高斯规定厚度；整数角谐波叠加径向相位形成连续纹理，时间推进相位。内外颜色插值是美术色带，不是温度求解。背景使用方向域低频场和三维稀疏星格，没有外部星空贴图。星格种子采用整数 hash，避免 sin/fract 放大浮点舍入；相同数字 seed 的背景布局因此与初版有差别。

积分采用每段常系数近似：`a=1-exp(-sigma*ds)`，`C+=T*a*source`，`T*=1-a`。这里 source 相当于发射/消光比，保持 sigma=0 时盘发光为零；只模拟发射/吸收，不模拟散射。由 ds 修正 alpha 只能避免直接按步数堆叠不透明度，不能消除薄盘漏采和弯曲路径离散误差。

核心使用线段最近距离检查以减少大步跨越漏检；命中时先执行 `C += T * uCoreColor`，再令 `T=0` 截断后景。黑色核心增加零辐射；彩色核心受前景盘透射率衰减，已有前景 C 保留，两种命中分支使用相同合成规则。这是美术源颜色，不是物理黑洞发射模型。射线路径仍使用固定步长；路径段与 ±6 倍 thickness 的盘层相交时，按不大于 thickness/2 的子段积分盘密度，并对每个子段单独计算光学厚度。当前参数范围每段最多需要 8 个样本，循环硬上限 16。这样避免 96 步最薄盘被单个中点漏采；同时中点路径积分减小质量档之间的偏折差异。增加厚度/范围的新接口时重新核对这个预算。

这不是解析薄层解，也不是自适应测地线积分；极端接近临界路径仍有近似。普通背景星核未做完整亚像素过滤；小图有闪烁或星点丢失时优先减少背景细节，不能承诺当前预览有完善抗锯齿。

## 六份本地参考如何贡献方法

| 输入 | 学习内容 | 本 skill 的使用方式 |
|---|---|---|
| 1.18.2 Avaritia Cosmic/Halo 示例 | 表面窗口、方向图案、装饰层 | 理解载体与内部场分离；不移植旧 API |
| Re-Avaritia Forge 1.20.1 | 模型/override、shader 注册、mask、护甲入口 | Forge 接入设计参考，不宣称黑洞已接入 |
| Re-Avaritia NeoForge 1.21.1 | 延迟队列、状态和版本差异 | 不混用平台；独立验收延迟路径 |
| Avaritia-Render 片段 | 不完整代码仍需追踪完整链路 | 不把片段存在当成运行证据 |
| RenderBlender | 渲染库拆分、Halo 结构、shader 实例归属 | 参数/格式/实例分开归属 |
| ArcaneVortex 0.6.8 Forge JAR | 黑洞主体、吸积盘、背景偏折、场景采样 | 静态研究后原创实现简化内核 |

ArcaneVortex 输入：`ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge.jar`，SHA-256 `27c5d799a488a418a588b978bb77da6bd48bbe48d8bc5c6a11cfb7b6f60a8b97`。其 mods.toml 声明 All Rights Reserved。本 skill 不包含该 JAR、反编译文件、原 shader 或原素材；研究方法不等于取得再分发授权。

可复核路径：`Content/Entity/BlackHole/BlackHoleRenderer` → `ModShaders.getBlackHoleShader` → `assets/arcanevortex/shaders/core/black_hole`；renderer 调 `WarpSystem/SceneCopyTarget.copyMainColor`，随后全屏绘制。本地静态证据位于 `E:/着色器渲染/research/cosmic-material/authoring/arcanevortex/bytecode`。跨机器没有这些文件时，从用户提供的相同哈希资料重新核对，不虚构结果。

需要吸收的反例：原参考部分遮挡比较使用 length(viewPos) 对比 -viewPos.z；弯曲命中位置用直线近似重建；颜色副本不代表深度也已经独立复制。源码中的远近降级与固定 MAX_ITERATIONS 权重也应重新验证。它们是静态发现，不能写成已经观察到原 mod 崩溃或花屏。

本次无需外部资料即可复核的依据是本地源码与字节码。若用户要求物理准确性，另外研究正式的相对论求解资料并建立数值基准；不要把当前美术模型改名后当成科学模型。
