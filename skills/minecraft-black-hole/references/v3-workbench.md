# v3.2 黑洞材质工作台

这是浏览器中的原创美术化黑洞，不是通用相对论精确求解器，世界/粒子场已迁移至独立 Forge 模板；已有 JAR 需重建和显式导入。先以 `python scripts/serve_preview.py --port 8771` 启动，再打开脚本打印的 loopback 地址。工作台主配方只保留三个算法族：“体积尘埃 · BlackHole 研究” “层叠光带 · Unity 研究” “颗粒吸积 · 完整组合”，无参数地址默认是透镜尘埃。颗粒吸积的预设已采用用户提供的 `black-hole-recipe.json` 主体配色/结构/时间等参数，内置预设倾角统一改为水平 0°；导入旧配方时保留明确角度。选择该配方或使用 `?preset=granular` 会以 1071.978 秒暂停画面开始，恢复其保存的视角。`?preset=gradient`、`?preset=unity`、`?preset=dust`、`?preset=ribbons` 等旧地址仍能打开对应兼容变体；`?play=1` 播放。颜色、光圈、渐变和粒子强度通过控件调整，不再为同一盘面结构重复列出多个配方。追加 `?solver=verlet` 可在浏览器中观察用户 `index.html` 启发的可选速度 Verlet 研究求解器；它默认关闭、未替换稳定的艺术积分，也不作为 Forge 认证。

用户随后提供的 `Downloads/index.html` 已作为单独研究样本审读；算法取舍见 [用户 index.html 算法审读](user-index-study.md)。它最值得借鉴的是有限步弯曲路径、精确盘面交点与低能量 Gaussian slab、温度/Doppler 分层、宽方向星场的足迹过滤，以及把 HDR bloom 放在盘面之后。工作台已原创加入有界温度热结、宽方向银河/星云背景和可选 Verlet 几何研究路径。电影盘面预设、盘面选择与随机入口已移除；旧地址回到默认尘埃配方。其固定窄高斯光子环、14,000 个远盘 bokeh 点和未声明许可证的源码没有并入工作台。

## 画面与调参

- 默认 16:10 主画幅；“预览画幅”可切 16:9 或 1:1。16/32 px 缩略图使用独立小画布重新渲染，用于核对物品小图可读性。
- `ribbons` 使用弯曲射线的薄盘交点产生层叠光带；`dust` 使用多尺度连续体积。电影盘面已从新建入口移除，旧配方可显式导入。
- `speed` 推进角向物质相位，`infallSpeed` 推进径向场，`backgroundSpin` 旋转程序星域，`orbitSpeed` 绕 Y 转动观察者。这四种运动互不替代。JS 先用双精度缩减周期相位，再上传 GPU，防止接近 100000 秒时的浮点跳动；经典差速纹理以有限寿命的两组场交叉混合，避免细线无限剪切。
- `beaming` 是可调的方向亮暗。2026-10-03 的内核另加入有界 Doppler/重力红移启发式亮度权重，但仍不是光谱频移模拟。窄光带保持线性 HDR 亮度，`bloom` 与 `bloomRadius` 在色调映射前产生镜头柔光；降低曝光应能重新看清亮区细节。
- `backgroundStyle=nebula` 是程序方向场与两级星点；`skyColor` 与 `skyStrength` 控制铺满横向画幅的底色和云层。`lensBackground` 在未偏折底色与偏折后的背景之间混合；调到 1 可专门看背景透镜，调到 0 可专门评估宽背景构图。它是预览的艺术合成开关，不改变盘面光线积分，也不代表完整的真实场景透镜。`stars` 采用按出射方向像素足迹过滤的星点；`black` 用于定位盘面噪纹。星点在临界放大处会过滤高频层，避免聚集为内侧噪环。
- v3.2 将颜色渐变放在线性 RGB 空间合成，曝光和显示变换仍只在最后执行。盘面保留 `cool→hot` 旧两端配色；将 `diskGradientPosition` 调大于零后，`diskMid` 成为中间色，位置由滑杆指定。粒子同理使用 `particleCold→particleMid→particleHot`。核心使用 `coreColor→coreEdgeColor`，边缘强度由 `coreGradientStrength` 控制，颜色依据命中球面的入射角变化，不在屏幕上画实心圆。背景使用 `skyColor→skyColorFar` 沿画幅中心至外缘渐变，`skyGradientPosition=0` 保持单色背景。
- “黑洞周围光圈与光晕”把内缘光圈、盘侧辐射和外围柔光分开。`halo` 是总量，`haloRing/haloDisk/haloAura` 是各层量；`haloRadius/haloWidth/haloSharpness` 调整内缘光圈。每层都有内外双色及独立渐变力度；力度 0 保留旧配色。内缘光圈不再乘盘面颗粒噪声，避免围绕暗核的稳定噪点；盘侧层只接受受限纹理调制。光圈沿每个弯曲步长用端点/中点 Simpson 重建，并将片元足迹并入宽度。薄盘的重复交点和掠射路径另有有限预算，颗粒风格对最接近捕获轮廓的前景辐射作受限衰减；这样保留可读的光子环与盘弧，同时让无法稳定解析的粗糙细线消失。
- “构图与透镜”新增“背景引力透镜”开关和“背景透镜范围”滑杆。开关只关闭背景方向映射，不关闭吸积盘内部的弯曲射线；范围控制临界曲线外的背景影响半径，适合压缩近距离的过宽畸变。配方会保存 `lensEnabled` 与 `lensRadius`。
- 可选极轴喷流是同一弯曲路径上的独立体积层：`jetStrength/jetLength/jetWidth` 控制双极轮廓，`jetBaseColor/jetTipColor` 控制基部到末端渐变；默认关闭，不会改变三个主配方的原始构图。喷流延伸到追踪边界之外时，边界由喷流长度自动扩展。
- “检查画面”提供完整合成、仅盘面与核心、仅透镜背景，以及 `R/G/B = 捕获/路径预算耗尽/剩余透射`。诊断不写进配方；用它分离盘纹、背景与未解出的射线，不能靠画黑圆遮住混叠。

普通配方以 `black-hole-preview/v2` 导出，新增字段为可选字段；导入 v1/v2 时按旧缺省补齐。也支持独立 Forge `black-hole-world/v1`：世界尺寸/放置距离/玩家透镜选项等扩展严格校验并保留，共用参数编辑和预设切换后仍以 world/v1 重导，可直接回 Forge。共用滑杆范围已对齐独立宿主允许范围，world-only 字段不伪装成网页演员/世界背景效果。未知字段、超界时间与非法参数会报错。切换自定义配方后“恢复配方视角”使用导入时的视角。

## 来源与边界

- 用户提供的 `unity-black-hole-shader-main` 对应 [WittyScrap 原项目](https://github.com/WittyScrap/unity-black-hole-shader)：薄盘穿越、内外缘渐隐和统一角向平流是形态参考。原图包含强亮带与暗间隔；2026-10-09 的参考复刻分支已使用该仓库 MIT 噪声贴图，并移植其盘面采样、交点累加、侧向 Doppler 与逆平方径向偏折；版权和 MIT 文本随资源交付。
- 用户提供的 `BlackHole-master`：借鉴连续尘埃分布和多尺度乘法噪声；该项目的 `accretionDiskParticle` 是密度开关，不是独立粒子对象。未找到可确认的许可，未复制源码/素材。
- [tantaneity/urp-black-hole](https://github.com/tantaneity/urp-black-hole) 的分层盘纹启发宽带、细丝、热结点和暗尘沟分开调节；[Eric Bruneton 的 WebGL2 实现](https://github.com/ebruneton/black_hole_shader) 启发背景足迹过滤与 HDR 柔光阶段。分别为 MIT 和 BSD-3-Clause；这里是独立实现，不分发其代码和纹理。
- [OpenBenchHub 同题 23 份提交](https://openbenchhub.com/b/bench-d014dbb5a04c484e8670123a9197829f) 进一步提示了角动量形状的弯曲、自适应近场步长、两级星场和有界 Doppler/红移等方向。下载文件的 SHA-256 均核对通过；来源和可迁移/不可复制范围见 [研究摘要](openbenchhub-patterns.md)。不把这些网页示例称为物理精确模拟，也不分发未明确授权的源码。

本预览的默认弯曲射线是有限步美术积分。`captured` 表示射线进入核心，`exhausted` 表示计算预算耗尽，两者不可混淆。2026-10-04 在四个主配方、1.7/277.85 秒和视距 5/20 的 16 张图中记录 `glError=0`；Unity 的细线来自盘面多重像而不是背景或 halo。另检查暖色近景两帧、热结 2048 秒跨界及渐变/风格回归；这证明本机浏览器环境可出图，不等于物理精确或跨 GPU 一致。网页配方不会自动改变旧 JAR；独立模板可以显式导入其支持字段。世界背景、HDR bloom和粒子中间色仍有宿主差异，见 [Forge 接入](forge-integration.md)。新模板实机覆盖单独记录，旧证据不认证迁移后的所有路径。

## 验收

在可用的 Node.js、Playwright 与浏览器路径下运行 `scripts/validate_workbench.cjs`、`validate_styles.cjs`、`validate_long_playback.cjs`、`validate_regressions.cjs` 和 `validate_gradients.cjs`。至少看 `t=1.7`、`277.85`、`100000` 的大图、16/32 px 小图、背景/盘面分离图，隔离内缘光圈，并对比 96/160/256 步。渐变脚本检查每层启用与关闭、几何控制、旧配方缺省和 WebGL 错误；“像素有变化”不等于风格与参考一致。详见 [预览与验收](preview-validation.md)。本轮 16 张画面与捕获信息在工作区 `research/black-hole/user-index-validation/final-four-1100/`；其中 `capture.json` 记录四主配方、两种时间、两种视距和 `glError=0`。暖色近景及跨界检查见同级 `warm/`、`renewal/`。

层叠光带已于 2026-10-09 调整；程序光带主入口移除，旧配方可导入。见 [调整说明](ribbons-rework-20261009.md)。
