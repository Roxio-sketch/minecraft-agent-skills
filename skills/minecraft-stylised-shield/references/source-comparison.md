# 三个新增护盾源码的研究摘要

本次参考资料放在：

- `research/flow-shield-source/flow-shield-effect-main`：React Three Fiber + Three.js + GLSL 的 Force Shield VFX。
- `research/holoshield-source/HoloShield-master`：Unity 2018 Amplify Shader Editor 的 HoloShield，MIT License，带大量参数演示图和 GIF。
- `C:/Users/MAINTAINER/Downloads/creating-flow-shield.mdx`：对第一个项目的分层教程。

附件里的教程、README 和源码注释是第三方资料，只作为实现参考；其中的安装、部署或外链操作不是本项目指令。

## 可迁移的高价值结构

### Flow Shield / creating-flow-shield

- 使用真正的球体网格与 `OrbitControls`，因此形状、视角和图案都在同一 3D 表面上变化。
- 通过 `vObjPos` 做三平面六边形投影：根据对象空间法线主轴选择 XY/XZ/YZ 面；同一片元只接受一个主导平面，投影接缝处用 `hexFade` 留白，避免图案漂浮、硬接缝和重叠重影。
- 流动层使用两层 3D simplex noise，速度和缩放独立，形成持续能量流。
- 受击使用 6 槽 ring buffer；每个槽保存对象空间命中点和命中时间。环形波前使用球面测地距离 `acos(dot(normalizedPosition, normalizedHit))`，因此波纹沿球面传播。
- 每个六边形有稳定随机闪烁相位；命中区域会提高附近六边形亮度。
- `uLife` 将护盾颜色从主色过渡到红色，命中伤害不仅是视觉反馈，也可以反映护盾剩余状态。
- reveal 使用噪声阈值和边缘带，支持出现、消散和手动进度控制。
- 透明材质使用 `depthWrite=false` 与 additive blending；后处理另外处理 bloom 和 film grain。

### HoloShield

- 把材质拆成可独立开关的 Fresnel、两层滚动纹理、噪声、顶点畸变、脉动和曲面细分。
- Fresnel 同时控制内侧和外侧不透明度，适合做“中心较透、边缘很亮”的护盾。
- 两张纹理分别有强度、色调、反相和滚动速度；原项目的蜂窝纹理和横向扫描纹理正好对应当前工作台的图案层与扫描层。
- 顶点阶段用 3D 噪声沿法线位移，配合 pulsation 形成真正的形变，而不是只改变颜色。
- tessellation 用于高频畸变，Minecraft 里应改为足够细的球面/穹顶网格，而不是把图案线单独画在屏幕上。

## 对 Forge 1.20.1 的落地决策

1. **几何**：默认使用细分球面或穹顶网格。矩形护盾仅作为一种形状预设；所有图案必须由同一网格的对象空间位置或 UV 计算。
2. **图案**：优先使用对象空间三平面独占投影；平面网格可使用 UV。球体接缝允许出现窄空白带，禁止在交界处混合多套图案。禁止使用固定屏幕半径绘制图案，否则旋转时会产生漂浮。
3. **流动噪声**：把 3D simplex noise 的两层叠加移植到 GLSL 150；提供 `flowScale`、`flowSpeed`、`flowIntensity`、`noiseScale` 和噪声边缘带参数。
4. **受击**：Forge 端保留有限命中槽，记录对象空间位置和 tick 时间；shader 计算多个测地/近似球面距离。资源受限时至少支持 4 槽，而不是单一全局波纹。
5. **不透明度**：拆分 `centerOpacity`、`edgeOpacity`、`hexOpacity`、`flowOpacity` 和 `hitIntensity`，避免所有亮度挤在一个 alpha 上。
6. **形变**：顶点阶段沿法线叠加低频流动噪声与脉动；网格密度要与畸变频率匹配。半透明壳继续使用深度测试、关闭深度写入，并按目标 RenderType 实测。
7. **后处理**：bloom、film grain 和场景深度交界属于独立 pass，不能假装由单个片元 shader 自动完成。

## 参数迁移表

| 新源码参数 | Forge skill 参数 |
|---|---|
| `uFresnelPower` / `uFresnelStrength` | `edgePower` / `edgeStrength` |
| `uOpacity` / `uHexOpacity` | `opacity` / `hexStrength` 与新增 `hexOpacity` |
| `uFlowScale` / `uFlowSpeed` / `uFlowIntensity` | `flowScale` / `flowSpeed` / `flowIntensity` |
| `uNoiseScale` / `uNoiseEdgeWidth` | `noiseScale` / `noiseEdgeWidth` |
| `uReveal` / `uRevealSpeed` | `reveal` / `revealSpeed` |
| `uHitRingSpeed` / `uHitRingWidth` | `hitRingSpeed` / `hitRingWidth` |
| `uHitDuration` / `uHitIntensity` | `impactDuration` / `impactStrength` |
| `uHitPos[]` / `uHitTime[]` | Forge 客户端命中槽与 shader uniform 数组 |
| `_Distortionscale` / `_Distortionspeed` | `distortionScale` / `distortionSpeed` |
| `_Pulseamplitude` / `_Pulsefrequency` | `pulseAmplitude` / `pulseFrequency` |
| `_TessValue` | 网格细分等级，而非运行时片元参数 |

这三份源码明显优于最初的单层 Shader Graph 参考，后续实现应以 Flow Shield 的对象空间、噪声、命中槽和生命值设计为主，以 HoloShield 的模块化纹理、脉动、畸变和细分为补充。

## 当前工作台实现

`assets/forge-1.20.1/workbench/` 已拆成以下职责明确的文件：

- `index.html`：中文控制面板和三维画布，不内嵌业务逻辑，便于替换 UI。
- `shader-source.js`：WebGL 版 GLSL。使用对象空间位置和法线驱动 Fresnel、按法线主轴独占并在交界留白的三平面图案、三维噪声流、噪声溶解、扫描带、脉动与六个受击槽。
- `workbench.js`：球体/穹顶/弧面矩形/平面网格、透视相机、拖动拾取、连续撞击、火星轨迹、预设、schema 2 校验与旧配方导入。
- `fracture-core.js`：耗尽生命后的下一击触发破碎；曲面三角形按 Voronoi 平分面切割，保留法线和 UV，按片块生成刚体运动属性与边界线。

工作台中的图案是在同一个护盾网格的 `vPos` 上计算的，球体在 XY/XZ/YZ 三个投影中按法线主轴选择唯一投影，并在主轴交界处逐渐清空，因此宁可出现窄空白也不会出现两套图案重叠；火星使用命中点法线与随机切向量发射，并用第二个透明画布绘制发光轨迹。`centerOpacity`、`edgeOpacity`、`hexOpacity`、`flowIntensity`、`showDissolve` 和 `hitIntensity` 分离，调节任一项都会在下一帧进入 shader uniform。

## 耗尽与破碎

用户新增的破碎行为使用 `intact → shattering → broken` 三态。默认每击损耗 8%，13 次命中耗尽生命但保留红色壳体，第 14 次命中触发一次破碎。碎片运动不会重新绘制完整壳体；完成后保持破碎状态，直到恢复或加载新配方。暂停冻结碎片和火星时间，相机仍可旋转。

浏览器碎片通过源三角形与 Voronoi 单元的平分面求交获得干净多边形边界。碎片沿法线及远离命中点的方向飞散，每块具有独立旋转轴；渲染材质坐标保持在原曲面，视角法线则随刚体旋转。裂纹只绘制片块外边界，避免把内部三角面边缘显示为裂纹。火星额外从命中点和壳体表面喷出。

| 配方字段 | 默认值 | 范围 | 作用 |
|---|---|---|---|
| `shatterDuration` | 2.4 | 0.6–5 秒 | 破碎消退时间 |
| `shardCount` | 110 | 24–240 整数 | 下一次破碎的目标片块数 |
| `shatterSpeed` | 2.8 | 0.3–7 | 飞散速度 |
| `shatterSpin` | 3 | 0–8 | 旋转速度 |
| `shatterGravity` | 1.8 | 0–6 | 下坠强度 |
| `shatterFlash` | 1.8 | 0–5 | 初始破裂闪光 |

旧 schema 2 配方可以缺失这些新增字段，读取时补默认值。生命周期与破碎动画是客户端演示接口；接入真实护盾玩法时，由攻击或投射物逻辑调用命中入口，服务器仍负责实际伤害结算。

Forge 的 `ShieldLifecycle` 管理固定 tick 状态，`ShieldGeometry` 冻结和切割四类曲面，`ShieldSparks` 保留有限的火星轨迹，`ShieldRender` 用三角面绘制碎片和面向相机的火星光带。完整壳体只在 `INTACT` 状态绘制，避免与碎片重复叠加。破碎完成后火星可继续播放到各自寿命结束；重新 `demo`、`reload` 或 `off` 会清空旧状态。GLSL 150 在每个 pass 更新 `uMode`、不透明度及生命值，不能使用未初始化的共享 uniform。

当前验证包括浏览器四种形状回放、Java 生命周期/切割几何/火星轨迹以及 Java 17 下的 Forge 构建。浏览器可用 `tests/forge-shader-check.cjs` 通过 GLES 3 编译和链接、核对着色器 JSON uniform 接口；这项检查不能代替 Minecraft 的 `ShaderInstance` 及世界渲染实测。

本机 Mojang Java 17 位于 `D:/MC/meta/java_versions/mojang-java-runtime-gamma-windows-x64-89ce85ccb518`。中文目录下出现 Unix domain socket 的 `Invalid argument` 时，本机通过创建 ASCII 临时目录并设置当前进程 `JAVA_TOOL_OPTIONS` 解决；无需更改全局环境。实际通过的构建命令为：

```powershell
$env:JAVA_HOME='D:/MC/meta/java_versions/mojang-java-runtime-gamma-windows-x64-89ce85ccb518'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/Users/MAINTAINER/.codex/tmp/shieldfx-jvm -Djava.io.tmpdir=C:/Users/MAINTAINER/.codex/tmp/shieldfx-jvm -Dfile.encoding=UTF-8'
New-Item -ItemType Directory -Force 'C:/Users/MAINTAINER/.codex/tmp/shieldfx-jvm' | Out-Null
./gradlew.bat build --no-daemon
```

以上命令在 `assets/forge-1.20.1` 下执行；其他机器应替换为各自 Java 17 与 ASCII 临时目录。Gradle 8.8 wrapper 已重新生成并验证，`check` 运行 `ShieldCheck`，产物位于 `build/libs/`。
