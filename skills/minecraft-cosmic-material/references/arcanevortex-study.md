# ArcaneVortex 着色器方块：材料制作研究

用户提供 `ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge.jar`，SHA-256：`27c5d799a488a418a588b978bb77da6bd48bbe48d8bc5c6a11cfb7b6f60a8b97`。`META-INF/mods.toml` 标注 mod 0.6.8、Forge `[47,)`、Minecraft `[1.20.1,1.21)`、作者 ErChien、`All Rights Reserved`。这是包内声明和特定快照，不是兼容性实测或最新版声明。

本次将 JAR 当 ZIP 读取 GLSL/JSON，并用 JDK `javap -p -c` 查看选定 renderer 的字节码；不执行 mod、内嵌 agent 或 native library。原始/改编 shader 只保留在用户本地研究目录，技能只保存结论和定位信息。未经授权不要将这些源码/素材打包进可分发 skill 或 mod。

本机证据根目录为 `E:/着色器渲染/research/cosmic-material/authoring/arcanevortex`；缺失时从上述精确快照重新读取，不从文件名推测算法。共 1,493 个 JAR 条目；本次选取 150 项，含 32 个 core fragment 与 2 个 program fragment。完整路径/哈希在 `manifest.json`。

## 先核对真正绑定

Java 前缀：`com/erchien/arcanevortex/Content/`；shader 前缀：`assets/arcanevortex/shaders/core/`。

| 对象 | 字节码确认的入口 | 学习价值 |
|---|---|---|
| StarShaderBlock | `Block/StarShaderBlock/StarShaderBlockEntityRender.render` → `ModShaders.getStarSkyShader()` → 注册 `star_sky` | 分形星海；不是 `starfield.fsh` |
| VolumetricBlock | `Block/VolumetricBlock/VolumetricBlockEntityRender.render` → `getVolumetricShader()` → `volumetric_shader` | 从方向射线进入程序空间并累计颜色 |
| FantasyBlock | `Block/FantasyBlock/FantasyBlockEntityRender.render` → `getChaosShader()`，`useType=1`，RenderType `chaos_pcn` | 曲折发光云层；不是按名称猜出的 `fantasy_block.fsh` |
| SkyBlock | `Block/SkyBlock/SkyBlockEntityRender.render` → `getSkyShader()` → `sky_block` | 噪声驱动的云密度；天空外观本身不是本任务交付 |
| nebula_cube | `ModShaders` 注册与 getter 可见；本次引用检索尚未闭合实际外部调用路径 | 有限体积、场景深度与边界；不能称已确认某个方块使用 |
| starfield / cosmic / cosmic_neo | 包中 shader 与参数可读；本次不把所有变体的实际调用都视为已确认 | 稀疏星点、主星、模式化内部场景的可配置方式 |

`CosmicBlock` 没有找到对应名称的独立 `*EntityRender` 类，不能凭有模型文件就虚构 Java 入口。已经加载的 shader 也可能未被当前玩法使用。

## 1. star_sky：分形星海

文件锚点：`star_sky.fsh` 的 defines 与 main；配套 `star_sky.vsh`；上述 StarShaderBlock renderer 的 render 与 renderCosmicCube。

- 外层 20 个 ray steps；每步对折叠后的点做 17 次反演迭代，核心是取绝对值、按模长平方缩放、偏移。
- 相邻迭代的长度变化累积成强度；沿路径按不同距离幂次赋 RGB 权重，再用衰减与 darkmatter 项抑制部分区域。
- 动态移动程序空间起点并旋转射线/起点，密集结构因此随观察方向展开。其视觉细节来自程序分形，不需要一张高分辨率星空贴图。
- 此处没有独立的逐星正弦亮度包络；观看到的细点明暗变化可由移动穿过分形高频结构产生。不能把包内另一个 `starfield.fsh` 的算法误记为 StarShaderBlock 的实现。
- 射线输入来自投影后的屏幕位置，再应用 yaw/pitch；`cameraPos` 虽有声明/写入，不等于片元真正拿它产生有限平移视差。

**如何用于描述**：“无限延伸、细节里面还有细节、暗底有稀疏高亮结构”适合这类场；“清晰可辨的几颗恒星”应另加可控主星层。调递归强度和衰减会改变结构，不能只作为亮度旋钮。

**移植关注**：反演分母接近零会产生极大值；对新实现加数值保护并检查其视觉变化。20×17 是每个覆盖片元的主要循环预算，不能复制到满屏 GUI 后未经测量承诺流畅。源 renderer 将相机角度乘 `π/360`，不同 renderer 的符号与倍率不一致；重做坐标契约，而非统一复制角度常量。

补充核对：`starfield.fsh` 的 `starField` 确有基于稳定随机属性的局部星点偏移、逐星闪烁频率与特殊星类别；主循环还对深度层处理方向。它可以作为“位置和亮度分开制作”的研究依据，但本次仍没有闭合其具体方块 renderer 绑定。原创制作台用自己的哈希星格、分层输运与包络实现可控星点，不打包上述参考源码，也不声称复现了 `star_sky` 的分形机制。

## 2. volumetric_shader：沿射线采样程序空间

文件锚点：`dot_noise`、`dist`、main。

从 `fPos` 归一化得到方向，结合 yaw/pitch；起点沿 time 的 z 方向移动；循环 80 次，根据三角噪声的绝对值决定步幅，再累计位置相关颜色，最后做强度压缩和显示转换。

这是“穿过程序场”的演示。`dist()` 并非经证明的几何有符号距离；不能把它当通用 sphere tracing 距离估计器。片元最终 alpha=1，没有有限箱体求交、场景透射率或密度对应吸收；文件名含 volumetric 不足以说明它是完整体积介质。

**可学习**：步幅、起点运动、频率和颜色节奏可以构成内部空间感。
**不能继承**：方块的方向式画面不自动具备物品局部锚定和前后星体遮挡。

## 3. chaos：云层、银河平面与洞穴是不同分支

文件锚点：`chaos.fsh` 的 main、`cloudRaymarch`、`gyroidCave`、`galaxy`。GLSL 声明是 400，不是已有实验基线的 150。

- `useType=1`：100 步方向累计。反复以正弦扰动空间；围绕一个薄层形状估计步幅，并按位置/步长累计不同颜色，形成折叠云带。FantasyBlock 确认选择这一分支。
- `useType=2`：79 步 gyroid 洞穴式结构，空间沿轴扭转，距离相关亮度累计。源码存在此分支，但上述方块 renderer 并未选择它。
- 默认分支：在屏幕坐标上叠加两种尺度和速度的噪声银河。可以研究颜色分段与噪声轮廓，但它不满足所有“内部三维空间”的要求。

**可学习**：先有薄层/洞穴的大形，再用多尺度扰动刻出细节；把颜色分布关联到形状。不要仅因 shader 内有一个 galaxy 函数，就把所有分支解释为宇宙体积。

## 4. nebula_cube：有限体积与背景遮挡

文件锚点：`rayBoxIntersect`、`volumeDensity`、`nebulaColor`、`renderVolumeCube`、main。

- 相机射线与包围盒求交；圆角箱体距离给边缘衰减。
- 六层 fBm 与八次 spiralNoise 组合密度，用密度与离中心距离选择紫色发光。
- 最大 128 步，名义步长 0.08，带位置扰动；累计 alpha 后提前退出。
- 读取场景颜色/深度，通过投影逆矩阵重建位置，合成体积并写 `gl_FragDepth`。

**可学习**：有限边界、密度、色彩、消光与场景遮挡分别有职责。
**需复核**：重建函数传出的 `sceneViewDist` 使用 `length(viewPos)`，行进点却以 `-viewPos.z` 比较，屏幕边缘两者不等价。源码的累计还含固定每步增量，不能当作采样数无关的严格透射率积分。移植时统一比较单位、依据步长修正 alpha；不能把截图看起来正确当作这些细节已无问题。

## 5. starfield 与 cosmic：设计词汇怎样变成可选模块

`starfield.fsh`：普通 8 层/高级 12 层，球面映射、星群密度、不同星点属性、单独大星系统；另有超新星、虫洞、脉冲束与彗星函数。关注 main 中实际调用和开关，不将“函数存在”写成默认显示全部。

`StarMask` 在此处控制大星层数，**不是物品覆盖遮罩**。最终表面覆盖另从 `Sampler0` 的红通道得到。星点数量、大小、分层数、材质覆盖不能混用同一个“密度”概念。

`cosmic.fsh` 的 `useType` 提供流彩、梦幻泡泡/萤火虫、混沌、黑金、幻境森林等分支：它的启示是**换结构与形状生成器，再配置色彩和动态**。功能存在不表示配方能直接用于当前独立实验 JAR，也不代表每个源码分支已有有效调用或可接受成本。

## 独立片元观察与证据边界

本次对 `star_sky`、`volumetric_shader`、`chaos` 的 1/2 两分支进行了独立 WebGL 2 出图：将版本声明改为 GLSL ES 300 并补精度声明，用全屏三角和合成坐标/矩阵供给输入，固定 time=1.7、yaw=0.25、pitch=0.15。四者编译/链接成功，采样 GL error=0。

证据：本机研究目录 `previews/validation.json` 与四张 PNG。渲染器是 ANGLE / RTX 4060 Ti / D3D11。这说明片元算法能在该改编测试环境产生可观察画面，**不说明原 JAR 已在 Forge 加载、方块深度正确或物品接入完成**。没有运行 `nebula_cube` 的场景深度路径；没有把时间统一解释成原游戏秒数。

这些参考图是学习用的局部改编输出，不放进已安装 skill。技能随附的 [原创制作台](authoring-workbench.md) 是独立薄层方案，不是上述代码的重打包，也不声称逐像素复现。

## 提炼成后续工作方式

1. 收到形容词先决定几何之外的空间结构；使用 [制作单](material-authoring.md) 确定主次与观察响应。
2. 选方向分层、分形路径、薄层发射/吸收或有限体积，不拿一种算法硬套全部设计。
3. 写明哪几个模块影响形状、哪几个只改颜色、时间、覆盖与成本。
4. 对当前目标重新处理坐标、采样足迹、uniform、图集和绘制状态；不要把方块全屏 shader 直接贴到剑 UV。
5. 独立看图 → 实际物品 → 目标 Forge 环境，分级记录证据。

## 2026-09-30 动态复查

FantasyBlock 的时间还要追到 renderer：字节码 offset 77 调用 `getShaderTimeValue()`，offset 80–82 再乘100。因此此方块的 `chaos / useType=1` 在默认 timeScale=1 时，uniform 每真实秒增加5；其中形变相位 `0.2*time` 每秒约1弧度。只读公共时钟会把该方块速度低估100倍。`fantasy_block.fsh` 不在此方块的实际绑定链上。

此分支从共享视点发出射线，100 步接近扭曲薄层，多尺度扰动进入三维采样点，接近层面时累计细褶皱辉光。它不是刚性纹理随 UV 滚动。新版原创 `fantasy-flow` 学习这种空间组织，使用独立的耦合剪切域、双色场、有限深度、吸收和像素足迹；不声称原 shader 的逐像素复刻。具体创作与坐标契约见 [internal-flow.md](internal-flow.md)。

用户对原创材质的第一版动态不满意后，重新查看了 `chaos.cloudRaymarch`、`volumetric_shader.main` 和 `TimeSystem` 字节码。后者默认 useRealTime=true；`getShaderTimeValue()` 取所选 TimeOutput 的 seconds 后除以20。因而原 volumetric 的起点 `time*10` 对应约0.5程序单位/真实秒（timeScale=1），不是10单位/秒。原 chaos 不只是移动最终 UV，而在多尺度空间扰动与颜色分布中使用时间。

据此提炼的原创奇幻动态见 [fantasy-materials.md](fantasy-materials.md)：连续剪切输运、移动褶皱法线、沿裂缝输光、有限深度软薄层积分。其代码不是 ArcaneVortex 代码改名；速度、构图和坐标也不声称逐像素一致。原始 shader 仍只在本机研究目录，不随 skill 分发。
