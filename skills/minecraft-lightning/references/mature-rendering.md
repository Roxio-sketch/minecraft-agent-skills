# 稳定渲染与配方迁移

这份约束来自 Forge 1.20.1 接入中的实际故障，并已回写到本 skill 的独立工程。使用源码作为实现起点；不要只复制结论后继续用旧 renderer。

## 保持配方语义

Java `LightningRecipe.parse` 与工作台 `validateRecipe` 接受原始参数对象及旧 `{values:{...}}` 字符串包装，忽略旧选择器 `preset` 元数据。完整导出是 61 字段数字/布尔/字符串 JSON；旧 60 字段缺 `gradientRatio` 时补 50。范围、枚举、signed 32-bit seed、整数（包括restrikeCount计数）和有限数必须严格验证，未知字段须报错。导入先生成临时对象，再整体提交；后续预设监听器不能重置导入的自定义 cluster 范围、数量、分叉或颜色。

`gradientRatio` 越大，起始色所占路径越多。以 `x=clamp(along,0,1)`、`r=percentage/100` 定义终止色权重：

```text
r<=0: t=1; r>=1: t=0; r==0.5: t=x
otherwise: t=x*(1-r)/(x*(1-r)+(1-x)*r)
```

先将每个渐变通道按旧规则 `round(start+(end-start)*t)` 取整，再乘独立核心/光晕底色并向下取整。保持 50%分支以免改变旧半字节边界的颜色。底色为红时不可能得到青色核心；需要完整渐变就把相应底色设为白色，不能擅自忽略乘法。

击地模板的局部进度仍负责增长/宽度/寿命；输出材质坐标统一为主落雷 `along=1`。`ImpactGeometry` 的 rays/rings/sparks/flash 全部继承实际末端色，包括 0%/100%及关闭渐变。JS 击地闪光现在也使用两道交叉 ribbon。

## 光晕几何与合成

原来的共享 miter 接缝在短段、厚光晕、急转弯时会形成自交四边形，三角化后产生尖片和明暗缺口。`HaloGeometry` 在实际 `ProjMat*ModelViewMat*pose` 下投影每段，生成独立凸形带；只在两相邻可见段的转折内接点生成圆盘。首尾保持 butt cap；深度面剪裁插值位置、半径、alpha和沿程颜色，拒绝无效段，不跨缺口连线。

同一路径的凸带和圆盘有重叠，直接逐段加法会制造规则亮点。`LightningHalo` 先在独立 RGBA8 scratch 使用 `GL_MAX` 取路径覆盖并集，再一次以 `GL_FUNC_ADD` 加到主场景；不同路径仍独立加法。profile shader 输出已经乘 alpha/coverage 的真实贡献，因为 `MAX` 忽略 blend factors。两份 halo 着色器 JSON 不声明 blend，apply 后再显式设置实际混合公式；核心 bolt 保留原加法 RenderType。

保持配方宽度与增益：核心 half-side 是 `node.width*2`，光晕 half-radius 是 `node.width*glowWidth/baseWidth`，`baseWidth` 按实际通道选择。远处不足 .75px 的半宽只为栅格覆盖扩宽：带状贡献乘 `native/rendered`，圆盘乘面积比例。coverage 放浮点 UV；先乘入 byte-packed alpha 会量化成零。核心保留共享接缝，使用 `RibbonCoverage` 补偿；不要把修光晕改成重写主干物理宽度。

## GPU 状态是接口的一部分

scratch 有独立深度附件，先复制主场景深度，兼容普通 depth 与启用 stencil 的 depth-stencil 主目标。纹理分配/销毁固定在 texture unit 0。所有路径结束必须恢复 draw/read framebuffer、viewport、scissor、clear color/depth、active texture与texture binding、shader/program、depth mask/test/function、color mask、cull、blend factors/equations。

`BlendMode.lastApplied` 是 Mojang 的缓存，不等于当前 GL 状态；RenderType 可以只改变 GL。通过 Forge `ObfuscationReflectionHelper` 的 SRG 字段 `f_85499_` 保存/恢复原对象，包含 `null`。禁止从 GL flags 推造新的 BlendMode；否则下一次 shader.apply 可能跳过必要状态设置。

独立工程只在主 framebuffer 的全 viewport 路径合成。替换渲染器/Fabulous/第三方 FBO 的拥有者需要明确适配并实测。shader重载释放 scratch；离开世界或 `/lightninglab off` 释放效果和缓冲。

## 同步工作台与接入 UI

`gpu.js` 使用 WebGL2、RGBA8/MAX/ADD、逐节点颜色/宽度、相同径向曲线与 coverage 补偿。Canvas2D 仅画场景参照与合成 WebGL 结果，不再用平均线宽、首尾线性 Canvas 渐变或经验增益模拟材质。工作台的 screen-space core miter 和简化相机不替代 Forge 实际 VP/遮挡；分形 JS/Java 随机序列分别确定，跨语言不承诺逐点同形。

接入其它参数 UI 时使用 schema 的完整范围，保持渐变底色可编辑；比例只在渐变启用时显示。颜色、增益、比例的即时修改须保持 seed/当前时序，结构字段才需要重新生成缓存。自绘按钮与即时标记统一使用面板布局坐标；这属于宿主 UI，独立 skill 不复制整个 MOD 参数框架。

回归入口及实际测试边界见 [验收](validation.md)。生成新项目用 `scripts/author.cjs`：过滤 `.gradle/build/run`，旧配方规范输出 61 字段，保留配方自身 mode 和自定义值。
