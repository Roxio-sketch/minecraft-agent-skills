# AstraLex V93.0 末地紫雾：分层机制与原创实现建议

本轮对用户本机原 ZIP、解压源码、实际调用与噪声纹理做独立静态追踪。紫色漫云、岛屿与点星是一幅画面的不同成分。源码支持“距离雾 + 世界噪声调制的体积光 + 天空极光/龙战纹理 + 天空星点”的复合解释；不能由截图颜色或 `END_CLOUDS` 的名称认定它是一个有限云体。

## 版本与证据范围

来源元数据：当时本机原 ZIP 为 `D:/MC/库/red.ghs.axolotl/profiles/测试 (1)/shaderpacks/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip`；解压源码根为 `E:/着色器渲染/research/shaderpack-atmosphere/sources/astralex/`。这些路径只记录研究来源，制作原创作品不依赖它们。下文源码锚点均为该 ZIP 内相对路径及原始行号，不是随 skill 发行的源码文件。

- 文件名含 V93.0；ZIP 内 `Changelog.txt` 第 5 行为 `#Version 93.0`。
- 大小 3,184,524 字节，共 382 个 ZIP 成员；完整 CRC 检查通过。
- ZIP SHA256：`70b6d8127a6733f0a19bd1d351b92709d6ee7496967ff49c4793d2c28c7a6c58`。
- 与历史来源记录中的 ZIP 摘要一致。26 个选定文件均与 ZIP 对应成员逐字节相同。
- 完整文件摘要、行数、真实行锚点与区间摘要见 [随 skill 的源码审计](validation/astralex-study.json)。

原始研究记录路径作为元数据保留：历史摘要 `E:/着色器渲染/research/shaderpack-atmosphere/manifest.json`，本轮审计 `E:/着色器渲染/volumetric-environment-lab/evidence/astralex-study/manifest.json`，审计脚本 `E:/着色器渲染/volumetric-environment-lab/evidence/astralex-study/audit.py`。再次审计第三方 ZIP 需要另行取得原包及研究脚本；随 skill 的报告可直接阅读。

这是静态源码证据；没有认证 AstraLex 本轮客户端外观、性能或用户截图的具体设置。截图可能来自不同设置、曝光、龙战状态或版本。新 skill 只保留元数据、分析与原创代码，不带 AstraLex 源码、纹理或 ZIP。包内 Credits 给出 BSL、LightShaft 等贡献者名单，不能据此推导出再分发授权。

## 当前 0.2 实现对应

`world-volume/2` 的 `purple-light-fog` 已作为可制作起点实现，使用 `densityModel=2`：有限区域支撑、density 与 coverage 先确定基础密度；原创世界噪声和平流/时间波动再把散射源亮度调制到 [0.35, 0.65]，亮度调制不改变原始 density 或透射率 T。随后用共享 Beer 积分输出 L/T。自发光沿用独立 emission 合同，density=0 时严格无贡献。它学习 smoker 的世界亮度分布，没有照搬原包的加权累积与阴影贴图。

制作入口为 `python <skill目录>/scripts/author.py --out <新作品目录> --preset purple-light-fog`，其后启动作品内的 `workbench/serve.py`。完整配方、初始自动加载与 PNG/JSON/ZIP 可按 [制作与配方](authoring.md) 使用；亮度/密度分离的固定输入证据见 [新分布 GPU 检查](validation/end-distributions-gpu.json)。原包距离雾、天空极光、龙战方向背景和点星在本文中属于分类研究；当前世界介质预设没有实现这些天空层，也不声称复刻 AstraLex 的整幅画面。

## 实际层次

| 层 | 实际入口 | 坐标与分布 | 深度/密度性质 | 与截图的可能关系 |
|---|---|---|---|---|
| 岛屿表面受光 | `forwardLighting.glsl` 179 | 几何法线、阴影与末地颜色 | 实体场景；独立于云噪声 | 岛屿保持立体明暗和材质 |
| 末地距离雾 | `deferred → Fog → NormalFog` | 相机到表面的距离；可加远边界淡出 | 解析指数混色；没有噪声采样或有限高度云体 | 远岛被紫灰色同化，近岛相对清晰 |
| 末地 smoker 体积光 | `composite → getVolumetricRays` | 真正沿屏幕视线重建世界样本；绝对位置调制 | 固定 10 个非均匀视深度样本、场景深度截断、阴影图调制；艺术加权累积，非 Beer–Lambert | 可在地形前方形成有空间起伏的紫光薄雾 |
| 末地极光 | `deferred → DrawAurora` | 上半球方向 `xz/y` 投影，平面样本 + 少量相机 XZ 平移 | 天空分支中的 30 次纹理层累加；没有世界高度边界、沿途不透明深度或透过率 | 大片上空青紫漫云、丝带与渐变光墙 |
| 龙战 FBM 紫纹 | `deferred → doFullSkyFBM` | 归一化方向，6 个旋转扇区；2D FBM 扭曲 | 天空辐射纹理；龙战 gate；没有世界体积密度 | 龙战紫色电光条纹，不能作为常态漫云主体 |
| 末地点星 | `deferred → DrawEndStars` | 方向投影，量化网格，3 次程序 hash 乘积阈值 | 天空点光；`GetNoise` 是程序 hash，不是 `noisetex` | 可见稀疏粉蓝/白点星 |

`REALISTIC_CLOUDS` 的真实 include guard（`shaders/program/deferred.glsl:239`）要求 `OVERWORLD`。`gbuffers_clouds.glsl` 的绘制分支（`shaders/program/gbuffers_clouds.glsl:133`）也要求 `OVERWORLD`，其他分支在第 217 行 discard。本快照中 `END_CLOUDS` 只找到设置定义，未找到参与实际公式或调用的读取。不能把主世界的 `DrawCloud` 直接归到末地。

## pass 顺序与几何遮挡

实际 `shaders/world1/deferred.fsh:3` 定义 `END` 并 include `program/deferred.glsl`。

1. `deferred` 第 268 行读 `depthtex0`，第 280 行逆投影重建 `viewPos`。第 296 行 `z < 1` 进入几何分支；几何末尾第 481 行调用 `Fog`。
2. 天空分支第 515 行设置末地底色，第 526 行 `DrawAurora`，第 530 行叠星，第 549 行叠可选龙战 FBM。这些没有直接画在不透明岛屿像素上。
3. `shaders/world1/composite.fsh:3` 进入 `composite`，第 243–245 行将两张深度转成视深度并调用体积光；第 254 行把结果写到 `colortex1`。
4. `shaders/world1/composite1.fsh:3` 进入 `composite1`，第 104 行起取 4 邻点平均，第 111 行平方，第 189 行乘末地光束颜色，第 203–209 行在加法与混色两条合成路径之间插值。

所以“岛屿可见”首先来自天空分支的深度分类与岛屿自己的照明。`smoker` 后处理则真正按场景视深度停止，可以在岛屿前积累。它的透明层策略有 `depthtex0/depthtex1` 和 `vlAlbedo` 专门逻辑；单张深度的原创 demo 不能据此声称支持相同的全部透明层。

## 末地 smoker：世界分布、运动与累积

真实世界采样与阴影重建（`shaders/lib/atmospherics/volumetricLight.glsl:9`）使用逆投影、逆 ModelView，然后变换到阴影空间。`composite` 的 `GetDepth` 返回线性视 Z 深度，第 123 行的 `GetDistX` 反向映射为深度值。因此循环的 `minDist` 是用来重建屏幕视线上样本的视深度，不应直接误写成欧氏射线长度。

采样结构见 `shaders/lib/atmospherics/volumetricLight.glsl:81`：

- 10 个样本，空气中样本深度与 `(i + dither + addition)^1.5` 成正比；步距受 `far`、FOV 和屏幕 X 位置影响。
- 最大距离为 `min(3072, shadowDistance)`；当前设置 `shadowDistance=512`，低质量 profile 可覆盖它。
- 第 107 行用 `depth1` 或特殊 `depth0/vlAlbedo` 条件中止；第 115 行起采样阴影图和可选有色阴影。
- END visibility 固定为 0.14285。没有主世界那条基于视线和光方向的相位权重，也没有任意世界云盒的上下/半径边界。

噪声主体见 `shaders/lib/atmospherics/volumetricLight.glsl:143`。用数学结构表示：

```text
p = reconstructedWorldRelativePosition + cameraPosition + (t × smokerSpeed, 0, 0)
k = floor(p.y / 5)
a = redNoiseTexture(p.xz / 384 + 0.95 × k)
b = redNoiseTexture(p.xz / 384 + 0.95 × (k + 1))
n = interpolate(a, b, fract(p.y / 5))
m = 0.5 + 0.15 × sin(12 × n + t × smokerSpeed)
shadowedSample *= m
```

这里是把同一张 2D 纹理按 Y 切片偏移并插值，得到随世界位置变化的拟 3D 场。5 是垂直插值间隔，384 是 XZ 尺度；没有“最低/最高雾高度”或云的实际空洞阈值。`m` 总在 [0.35, 0.65]，本质是给已有体积光调亮暗，而非把介质在某些世界位置清零。运动同时含 X 平流和噪声值的时间振荡，速度默认 5。

在空气中，亮度累积近似 `sum(shadowedSample × sqrt(sampleDepth/maxDepth) × 1.5)`。后面还有平方根、幂与 dither。没有返回 `T`、没有沿途 `sigma × ds` 的指数消光，不能称为已实现的物理单次散射介质。

最终紫色取 `(192,140,255)/255 × 0.30` 的独立 lightshaft 颜色。`LIGHT_SHAFT_STRENGTH_END` 在第 189、190 行各乘一次，综合强度近似平方关系，同时有阴影淡入、天气、屏幕眼亮度与失明因子。不要把该 slider 当线性密度。

## 末地极光：天空漫云候选

实际 `shaders/lib/atmospherics/aurora.glsl:38` 只在向上的视线执行。归一化方向做 `xz/y` 投影，再用 `cameraPosition.xz × 0.0075` 加少量平移和时间 X 平流。它不引用相机 Y 来决定是否穿过一个世界高度层。

每一层的投影距离来自“距离 + 样本序号 × 大小”的平方。这是艺术投影空间参数，不能把菜单的 Aurora Distance 写成真实云底高度（格）。分布主体是：

```text
directionPlane = worldDirection.xz / worldDirection.y
plane = (directionPlane × layerProjection + cameraXZTranslation) × smallScale
ridge = [1 − 2 × abs(redNoise(plane) − 0.5)]^16
mask = ridge × blueNoise(plane × 3 + timeOffset)
             × blueNoise(plane × 5 − timeOffset)
color = interpolate(lowColor, highColor, gradientAcrossLayers)
backgroundRadiance += mask × color × exp2(−6 × layerIndex/sampleCount)
```

16 次幂把噪声 0.5 附近筛为窄带，再被两尺度蓝通道打散，得到曲折片条而不是单一平滑渐变。实际第 89 行的地平线衰减在循环内重复乘到整个累积值，也会改变层次强弱。它在 `deferred` 天空分支以 30 次样本调用，没有做岛屿之前的沿途介质积分。

默认 End 低色是青蓝 `(0,160,255)`、强度 1.5；高色是紫 `(180,0,172)`、强度 2.5。颜色先平方再参与辐射累加。距离 1、大小 1、可见度 1.5、速度 3。菜单与实际源码有一处差异：第 25 行声明的 `auroraGradient=COLOR_END_GRADIENT` 没有被使用；第 63 行真正给 mix 指数赋值的是共享 `COLOR_GRADIENT`。复现参数学习应以实际公式为准。

## 龙战紫纹与点星

`shaders/lib/atmospherics/fbm.glsl:67` 将归一化方向划为 6 个旋转扇区。每扇区 2D 八 octave 噪声先扭曲坐标，再用 `1/abs(warpedX)` 塑造紫色细带，带 fade 与亮度截断；各扇区使用略异速度。`gl_Fog.start/far < 0.5` 决定龙战 gate。`en_US.lang` 第 1189 行把 `FBM` 标成 “Lighting Bolt Boss Battle”。这是龙战方向背景，既不是通常的噪声云密度，也没有亮雾照亮地形的机制。

`shaders/lib/atmospherics/endstars.glsl:1` 将方向投到平面并按 1024 网格量化。三个带偏移的 hash 相乘，仅超过 0.780 或第二层的 0.840 阈值后产生星亮度；可加方向相关的时间闪烁。`DrawEndStars` 调的 GetNoise（`shaders/program/deferred.glsl:114`）是 `fract(sin(dot(...)) × constant)`，不是纹理噪声。新模式加粉/蓝两色；旧模式白色。它依赖天空方向，不是有限范围粒子，也不证明雾有三维空间位置。

## 距离雾与纹理资源

End 距离雾的核心 `shaders/lib/atmospherics/fog.glsl:150` 等价于：

```text
fogOpticalShape = 2 × (viewLength × EndFogDensity / 256)^1.5
fogAlpha = 1 − exp(−3 × fogOpticalShape)
scene = interpolate(scene, EndFogColor, fogAlpha)
```

配置启用远边界淡出时另加 6 次幂项。默认密度 0.50，仅看基本项，64/128/256/512 格对应 alpha 约 0.233/0.528/0.880/0.998。它与几何距离相关，没有世界高度云分布。颜色使用独立 `endCol × 0.04`；如果关闭 `LIGHT_SHAFT_END`，此处 fogColor 被改成黑色。`END_FOG_DENSITY` 与光束开关耦合，这是原包艺术风格的实际结果，原创模块应让距离雾颜色与散射开关独立。

真正噪声绑定在 `shaders/shaders.properties:525`：`lib/tex/noise.png`。实核为 128×128、8 bit、RGB PNG；红通道供 smoker/aurora 主纹，蓝通道供 aurora 细节及其他 dither。只记录摘要和采样用途，没有复制纹理，也不假定原采样器状态能直接移植到 Forge。

## 原创实现与 UI 的具体取舍

用户参考构图里的目标可以归纳为：远处紫色大块翻卷/扭曲墙、近处仍可辨识的岛屿、局部有开孔或低密度路径，天空有少量点星。保持真实世界体积的目标时，参考原包的**层次和分布策略**，重新设计三维密度，不能把方向层公式改名为体积云。

建议把可调内容拆成明确的职责：

| 控制组 | 可调内容 | 预期可见效果与验收 |
|---|---|---|
| 世界区域 | 中心、外半径、底部/高度、岛屿附近通路 | 相机进入同一场；有限支持域外密度严格为零 |
| 末地形态 | 各向异性尺度、水平扭卷、垂直拉丝、域扰动 | 出现宽窄不同的旋涡墙/流带，移动视点有视差 |
| 开孔与层次 | 宏观阈值、侵蚀阈值、通路半径、上/下高度渐隐 | 天空或外岛能从低密度间隙可见，避免整屏紫色均匀填满 |
| 密度与遮蔽 | density、coverage、extinction | 近岛清晰、远岛渐隐；遮蔽不依赖发光强度 |
| 介质颜色/发光 | mediumColor、emissionColor、emissionThreshold、emission | 有紫色亮雾但几何不无条件变亮；`L` 与 `T` 分开 |
| 世界流动 | wind、speed、curl、种子、固定时间 | 平流与内部翻卷独立，暂停重放仍逐帧确定 |
| 演示场景 | 真正悬浮阶梯岛屿、相机内景与高度 | 每个岛屿实际有命中距离；体积积分截止于它的近侧表面 |

当前 0.2 已版本化新增十二字段并保留普通密度路径；扭卷与雕空的 `densityModel=1` 可按 [Bliss 形态学习](bliss-end-density.md) 制作。`densityModel=2` 单独实现紫雾源亮度分布。上述表格描述制作职责与进一步可扩展方向，不代表所有建议都已加入。原包的 `[0.35,0.65]` 光束亮度调制在已实现模型中用于 source，不能当密度空洞；独立 emission mask 也服从 density=0 恒等合同。

预览构图优先使用 `[200,120,240]` 左右的区域内部相机，朝内侧岛屿与远处厚云墙看；大半径体积若总从半径外的远处看，只能看到整体球壳，难以验证参考图的岛屿、旋涡与间隙。相机 Y 应可改；近景岛/外岛应有不同深度，且保留深度截断。

AstraLex 极光天空和点星可以在另一份明确的天空/场景契约中实现；它们不属于世界介质的 density、emission 或 flash 参数。此次世界体积 skill 不必为了参考图而将它们塞进有限介质内，也不应把背景噪声装成介质分布。若以后加入，独立开关、独立导出状态并注明方向背景，保持 `linearScene × T + L` 的职责。

最少验收应比较：同一时刻的区域内/区域外、近岛/远岛、density=0、emission=0，以及相机抬升/下移。输出 `L`、`T` 分别检查，再核对合成图；好看的紫色截图不能替代这些世界分布证据。

## 关键文件 SHA256

以下摘要来自本轮原 ZIP，不来自二手资料；所有 26 个文件和区间摘要都在审计 manifest 中。

| 文件 | SHA256 |
|---|---|
| `volumetricLight.glsl` | `a65e8166c1d86e75c5a4968584c68cb4c13a52bacc7611b4bbb7df77fbfead3c` |
| `aurora.glsl` | `7a30d69a0bba76db8767bb1c5c1b4324cf076e1e8e364ba26caeee96e4c2d78a` |
| `fbm.glsl` | `caec210e0235e78af9e26b663cdd095d93dda2a438db7764b46136a3ffd648c8` |
| `endstars.glsl` | `118e2799828191e9861f7d1072e257cbdaad26cfdd61d7509253d42aa90d7d55` |
| `fog.glsl` | `679ab77cf8c7ab59208744768c5f003dc1ce97fcf67b6079b1749fc312032732` |
| `deferred.glsl` | `846c3fdf2cc66dd02d0f9bd87acf847118ae9f485490351af26d281c556c2ee4` |
| `composite.glsl` | `5e327bf6dc5fd7145c2e4e424e24effc61301ced189c0d20a75b78665cd97ecb` |
| `composite1.glsl` | `32cc43e7514c8f3bcc3f253b29066afd8676acd2c3152b23bb272f14c5a9a79e` |
| `noise.png` | `088069cccc98e24be9e76f4414cbadc76263a96889abe64bdf5ae97d3c2ecf72` |
