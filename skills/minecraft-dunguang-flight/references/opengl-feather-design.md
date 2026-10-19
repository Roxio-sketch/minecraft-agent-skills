# 尖亮梭头、羽状分叉与弯曲长尾：OpenGL 几何设计

版本说明：本文区分基础双次几何与额外Bloom；当前V3工作台已在前者之外实际加入独立发光FBO、三尺度模糊与合成。详见 [工作台](workbench.md)。这不改变对参考原作算法保持未知的结论。

核查日期：2026-10-15。目标：Minecraft Forge 1.20.1 / Java 17 / GLSL 150。本文是原创实现设计与静态资料核查，尚未构建或运行游戏。

## 图片中实际可见的目标

已通过图像工具查看用户提供的 368×197 图片：右下方是一束尖亮的白青色梭头；梭头后方出现数条长短、方向不完全一致的羽状尖分枝；几条窄亮尾线向左上方弯曲延伸，外围有更宽、更柔的青蓝光。单张图没有提供可验证的运动过程、三维绕视或渲染管线，不能据此确认原作用了二次 Bézier、双 pass 或某种噪声。以下按用户指定技术路线设计可实现的候选。

图片仅作本地参考，不复制、改作纹理或打包进 Skill。

## 1. 几何分成三个有实际职责的集合

1. **梭头和主支线**：1–3 条有明显主次的曲线，最前端收成尖点；梭头亮度集中在尖点稍后的短区间，不能用均匀宽直条代替。
2. **羽状短分枝**：根部集中在梭头后的肩部，向后外侧分开，长度、弯曲量和侧向间距错开。每条都是真实曲线，不在主条上画一张羽毛贴图。
3. **世界历史长尾**：保存飞过的位置，逐段变细、老化和侵蚀。转弯后旧尾迹留在原世界位置。

CPU 生成三角带或显式三角形，普通 vertex/fragment shader 足够。这里“几何特效”指网格承载形状，不要求 geometry shader；也不用受驱动限制的宽 `GL_LINE_STRIP`。各条支线合批，外带和内核各一次几何提交即可作为基础方案。

## 2. 二次 Bézier：定义曲线，不冒充历史记录

约定主曲线从尾到头，参数 `u∈[0,1]`：

```text
B(u)  = (1-u)^2 P0 + 2(1-u)u P1 + u^2 P2
B'(u) = 2(1-u)(P1-P0) + 2u(P2-P1)
```

`P0` 是后端，`P2` 是当前前尖，`P1` 控制中段侧弯。以实体前向 `F`、稳定侧轴 `N`、第三轴 `K` 构造局部梭头时，可选：

```text
P2 = anchor + headForward*F
P0 = anchor - headLength*F
P1 = mix(P0,P2,controlFraction) + bendSide*N + bendDepth*K
```

该公式与 [three.js 固定提交的逐轴二次插值](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/src/extras/core/Interpolations.js)一致；其 [QuadraticBezierCurve3](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/src/extras/curves/QuadraticBezierCurve3.js)展示三维包装方式。本文公式和伪代码为重新组织的设计，并未复制该实现。

**历史路径与 Bézier 是两类输入。** 真实尾迹记录固定 tick 的世界挂点、出生时间和累计距离；一个二次曲线只有一个控制点，无法表示任意多次转弯。允许把短历史窗口拟合为若干二次段，但须限制拟合误差和段长，失败就保留折线/分段插值。禁止每帧拿“人物当前位置减速度×长度”重画整条直尾，或用同一条 Bézier 覆盖完整飞行历史。传送、换维度和大跳跃须断段。

## 3. 用弧长采样控制密度与纹理尺度

等间隔 `u` 不等于等间隔世界距离。基础实现先密采样，再建立累计弧长表：

```text
table = [(u=0, s=0)]
for i = 1..M:
    u = i/M
    s += length(B(u) - previousPoint)
    append(table, u, s)
for desiredDistance in 0, ds, 2*ds, ..., totalLength:
    find bracketing s values by binary search
    interpolate u in that interval
    emit B(u)
append exact endpoint if not already present
```

先从 `M=64–128`、`ds=0.04–0.12` 格的短梭头试起；这是原型起点，不是实测质量保证。随后按曲率或中点误差细分，且设置每条最大点数。纹理 `U = arcDistance/tileLength - flowSpeed*time`，长度变化不会把纹理图案一起拉伸。保留未归一化弧长和归一化长度 `q=s/totalLength` 两种变量：前者用于世界单位平铺，后者用于头尾形状。

## 4. 稳定截面与可绕视厚度

切线来自曲线导数；加入几何侧偏后，重新从扰动后的相邻点求切线。选择与第一条切线最不平行的世界轴，叉积初始化法向。后续用最小切线旋转传播截面：

```text
axis = cross(Tprev,T)
c = clamp(dot(Tprev,T),-1,1)
if length(axis)>epsilon:
    N = rotate(Nprev, normalize(axis), atan2(length(axis),c))
else if c>0:
    N = Nprev
else:
    split segment or use an explicitly selected stable fallback axis
N = normalize(N - T*dot(N,T))
K = cross(T,N)
```

去除重复点，处理近零导数与近 180° 反向，不能直接归一化零叉积。传递思想可对照已核查的 [three.js Curve.computeFrenetFrames](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/src/extras/core/Curve.js)。

单带顶点为 `V± = center ± width*N`。世界截面固定的单片从侧面会变薄，质量档可改成 `N/K` 两片交叉带或 4–6 边细管；所有片放入同一批顶点，仍能在每个材质层合批。交叉带有交叠加亮，需要降低每片能量，且不称为真实体积。面向相机模式另设，不能让相机变化改变世界历史中心线。

## 5. 羽状短分枝的原创构造

对每个固定 seed 的分枝，选择主曲线肩部的根位置 `qj`（例如 0.62–0.94，头为 1）。在该处取 `R、T、N、K`，设向后长度 `Lj`、侧展 `Sj`、深度 `Dj` 和左右符号 `hj`：

```text
Q0 = R
Q1 = R - a*Lj*T + b*hj*Sj*N + b*Dj*K
Q2 = R - Lj*T   + hj*Sj*N   + Dj*K
branch(v) = QuadraticBezier(Q0,Q1,Q2,v)
```

`a、b` 控制羽枝弯曲而非把主尾迹简单复制平移。根部宽度与主线局部宽度连续，外端收尖：`branchWidth(v)=rootWidth*(1-v)^branchTaper`；根部 alpha 可在极短区间淡入，避免多个根重叠成硬白块。分枝 5–12 条、长度和角度有次序地错开，再加入小幅确定扰动；不要每帧随机重抽，从而产生跳闪。

主梭宽度可用归一化的 `q^p*(1-q)^r` 形成前肩饱满、前端尖细的包络，最大值在 `q=p/(p+r)`。例如 `p=4,r=0.5` 令体量靠近前方；最后收敛到唯一尖点并用终端三角形封口。亮度单独用 `headBoost*exp(-((q-headCenter)/headBand)^2)` 集中，宽度和亮度不绑死。

## 6. 三层噪声有三个不同消费者

| 层 | 具体输入与消费者 | 对形态的作用 | 禁止混淆 |
|---|---|---|---|
| 低频轮廓 | CPU 的 `P1` 侧弯及主宽度包络；缓慢变化的连续噪声 | 使主梭和羽肩轻微呼吸/弯曲 | 不用高频随机推控制点 |
| 中频侧偏 | 弧长重采样后的中心线，在 `N/K` 方向位移 | 形成沿线游走的细微波动 | 位移后须重算切线，不能沿用原导数作精确法向 |
| 高频侵蚀 | fragment 的 alpha/亮度遮罩，输入弧长 UV、横向 UV、时间、seed | 造成尾部撕裂、小断口和细纹 | 不增加几何抖动，也不把它称为流体平流 |

中频例式：`C(q)=B(u(q))+envelope(q)*midAmp*(N*n1(s*midFreq-time*midSpeed)+K*n2(...))`。包络在两个端点归零以保住尖点、根位置和段连接。噪声使用连续函数与固定 seed，可从已研究的 MIT psrdnoise 学习，也可写原创低成本 value noise。CPU 与 GPU 若使用不同噪声，不能声称二者完全同场。

片元侵蚀示意：`alpha = lateralProfile(v)*ageFade*smoothstep(threshold-aa,threshold+aa,noise)`，其中 `aa=max(fwidth(noise),minimumAA)`；尾部可提高 threshold，头部降低侵蚀。窄内核的侵蚀强度应小于外带，以保住连贯主线。高频细节接近像素尺寸时应衰减，避免远处闪烁。

## 7. 两次几何 draw：青蓝外带与白色窄核

这里“双 pass”明确指**同一组曲线的两次几何绘制**：

| 提交 | 实际输出 | 混合与深度 | 可见职责 |
|---|---|---|---|
| Draw A | 宽外带，边缘平滑降 alpha，青蓝色 | 预乘 RGB；RGB `ONE, ONE_MINUS_SRC_ALPHA`；深度测试开、深度写关 | 轮廓外的柔蓝包络 |
| Draw B | 窄内核和梭尖，近白青色 | RGB 先乘 mask，再 `ONE, ONE` 加法；深度测试开、深度写关 | 高亮尖头、羽状细枝和细尾 |

可用 `glBlendFuncSeparate` 明确 alpha 通道约定；直接合成主场景时，若宿主需要保留目标 alpha，可选择 alpha 因子 `ZERO,ONE`。输出到自有预乘中间层时，A 层 alpha 通常按 `ONE,ONE_MINUS_SRC_ALPHA` 累积。不能对已预乘的 RGB 再用 `SRC_ALPHA`，否则边缘会二次变暗。加法内核输出 `emission*mask`，不能 RGB 不乘 mask 却指望 alpha 自动限制加法。

基础路线 `depthFunc=LEQUAL`、`depthMask=false`。保留场景遮挡，不能为“发光”关闭深度测试。GLSL 150 的 ShaderInstance 可以承担普通属性、uniform 和片元遮罩；CPU 建网格避免 compute/SSBO/geometry shader 作为前置要求。Forge 接入时通过 RenderType/RenderSystem 管理状态，在约定渲染阶段提交并恢复 blend、depthMask、depthFunc、cull、shader、纹理与目标。水/玻璃/其他透明层的顺序仍需实机验证。

**这不是 FBO 真 Bloom。** 外带的柔光只在外带网格覆盖范围内，没有邻域模糊。真正选择性 Bloom 需将目标 emission 写入独立纹理，降采样/模糊后合成，通常超过两次总 draw。两者可以组合；应分别命名 `outerBandWidth` 和 `bloomRadius`。外带宽度在世界单位，Bloom 半径主要在屏幕像素/目标分辨率意义上，不互相冒名。Bloom 也不产生地形照明。

## 8. 参数 → 消费者计划

这些是拟实现字段，当前文档不声称已经有运行消费者。实现时先保证每行进入对应计算，再添加 UI。

| 参数 | 单位/范围约定 | 计划的实际消费者 |
|---|---|---|
| `headLength, headForward` | 格 | 主 Bézier 的 P0/P2 |
| `bendSide,bendDepth,controlFraction` | 格、0–1 | P1 及低频轮廓偏置 |
| `sampleSpacing,maxSamples` | 格、正整数 | 弧长反查与硬容量上限 |
| `featherCount,featherSpread,featherLength` | 条数、格 | Q0/Q1/Q2 和分枝批次 |
| `rootMin,rootMax` | 归一化弧长 0–1 | 羽枝肩部根位 |
| `mainWidth,tipPower,branchTaper` | 格、正数 | 两侧顶点与终端三角形 |
| `outlineAmp,outlineRate` | 格、Hz | P1/宽度低频变化 |
| `midAmp,midFreq,midSpeed` | 格、每格频率、相位每秒 | 重采样中心线侧偏 |
| `erosionAmount,erosionFreq,erosionSpeed` | 0–1、UV 频率、UV/秒 | fragment alpha 遮罩 |
| `tileLength,flowSpeed` | 格、UV/秒 | 弧长 U 与时间平移 |
| `outerWidthScale,outerColor,outerOpacity` | 倍数、线性 RGB、0–1 | Draw A 宽度与预乘颜色 |
| `coreWidthScale,coreColor,headBoost` | 倍数、线性 RGB、强度 | Draw B 几何和发射 |
| `historyLife,maxHistoryLength,teleportBreak` | 秒、格、格 | 世界历史保留、裁剪与断段 |
| `crossSection,seed` | 枚举、整数 | 平带/交叉带/细管与确定性形态 |

`bloomRadius` 只有实施额外 FBO Bloom 后才出现；基础两 draw 不放一个没有消费者的 Bloom 滑块。颜色空间按宿主合成目标确定，不能把 sRGB 界面色值未经处理当作线性发射强度。

## 9. 核心正确性与验收

先用固定时间/seed 记录画面，再验证：尖点不漂、羽枝不每帧重抽；相机绕行可识别支线结构；等弧长采样不因飞行快慢堆点；急转和近反向不翻面；停飞后历史尾部按年龄消散；传送不连地图；高频侵蚀关闭后仍保留梭头和羽枝几何；外带关闭后窄核仍连贯；白天/黑夜和亮暗背景都可读；靠墙保持遮挡。

成本按实际顶点数、覆盖像素和实例数记录。若 K 条曲线各有 N 个采样点，单片带每层约 `2N` 顶点与 `2(N-1)` 三角形，两层约翻倍；交叉带再乘片数。优先把各支线合批，避免“每根羽毛×两层”变成大量 draw。以上为算术规模，不是 FPS 测量。

## 已核查的原始来源

| 原始来源 | 实际阅读内容 | 核查/许可边界 |
|---|---|---|
| [three.js QuadraticBezierCurve3](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/src/extras/curves/QuadraticBezierCurve3.js) 与 [Interpolations.js](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/src/extras/core/Interpolations.js) | 三维逐轴调用、三项二次权重 | 已读固定提交；[MIT](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/LICENSE)，直接复制须保留声明 |
| [three.js Curve](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/src/extras/core/Curve.js) | 弧长表与相邻切线框架传递 | 同一 MIT 快照；异常段处理需自己补全 |
| [Khronos glBlendFunc](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glBlendFunc.xml) 与 [glBlendFuncSeparate](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glBlendFuncSeparate.xml) | 源/目标因子、RGB 与 alpha 独立配置 | 已读官方仓库 XML；网页抓取受限后使用官方源文件；仅概述 API，未复制文档 |
| [Khronos glDepthMask](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glDepthMask.xml) 与 [glDepthFunc](https://github.com/KhronosGroup/OpenGL-Refpages/blob/main/gl4/glDepthFunc.xml) | 写深度开关与测试函数是独立状态；LEQUAL 的含义 | 已读 Description/Notes；文件注明 SGI Free Software B，未将文档许可当作其他实现许可 |

来源用于算法和 API 行为核对，不为图片原作算法背书。本文不包含图片副本或外部商业素材，不依赖外部源码工程，也没有修改大型来源 JSON，避免与主任务并发写入。
