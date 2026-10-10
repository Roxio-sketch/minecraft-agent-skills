# 白洞外喷分支

这是 Forge 1.20.1 世界路径的原创美术化白洞：发光核心之外，有径向外散的粒子、沿盘法线向两侧输运的喷流和重复扩张的球形光壳。白洞应当通过物质射出的运动与立体结构辨认，不能只把黑核变白。白洞尚无观测确认的外观；这里采用游戏 VFX 定义，不是广义相对论的精确求解器。正质量透镜保持原符号，不能借负质量或反引力误称真实白洞。

## 入口与默认配方

独立模板的 `/blackhole white` 应用 `white-ring` 并放置预览，`/blackhole black` 恢复默认黑洞，`/blackhole off` 停止。世界中心放置后保持固定；需要重放再执行 world。生产工作台中选择白洞 · 蓝白光团，再触发预览。网页制作台与 item/scene 内部材质仍保留原黑洞路径，不能宣称它们已支持世界外喷。

[默认白洞配方](../assets/presets/white-ring-default.json) 与生产资产同字节，含 75 项类型明确的 `black-hole-world/v2` 参数。当前默认采用用户配方：核心/内盘/外盘 0.28/1.65/5.27，厚度 0.15，倾角 −1°，scale 1.8、放置距离 39，classic 盘，density 0.6。核心发光倍率 3.95、核心光芒 0.8/1.05，seed 46271，playing true、time 37862.8；独立粒子和喷流关闭，透镜强度 0.5，允许玩家参与。光圈默认关闭，shellEnabled 可以独立切换，强度 0.28、速度 0.7、最大半径 4.8、宽度 0.1、颜色 #72dfff。所有原有显式值保留，不为截图重置默认动画时间。

独立模板可将 JSON 放到 `config/blackholelab/recipe.json`，执行 import，再 world。核心本身的辐射独立于盘晕与光圈开关。冠层有限世界范围，核心专用屏幕扩散则有投影视觉尾部；coreGlowRadius 同时控制两者，不是所有光芒的严格球形边界。settings 支持 shellEnabled 布尔开关和核心、喷流、光圈数值，纯材质设置不重置命令修改的 tilt、rotation、infall 和 particleSpeed。

## 配方兼容

`objectMode` 取 black/white，`coreEmission` 取 0–4。新增参数如下；它们只影响白洞外喷场。

|字段|范围|普通/旧配方缺省|
|---|---|---|
|coreGlow|0–3|0，关闭|
|coreGlowRadius|1.05–4 倍 core|1.8|
|jetSpeed|0–4|1|
|shellEnabled|布尔|true；显式强度为 0 时仍关闭|
|shellStrength|0–3|0，关闭|
|shellSpeed|0–4|0.7|
|shellRadius|1–8|5|
|shellWidth|0.02–1|0.12|
|shellColor|#RRGGBB|#72dfff|

导入先校验全部值，失败不改变实时状态。preview/v1、preview/v2、world/v1 拒绝 objectMode、coreEmission、这九个新增字段以及 white-ring。缺省为黑洞和关闭新外喷层。部分 v2 配方 `form: white-ring` 选择当前增强预设，显式字段随后覆盖；包含旧完整 66 项或 72 项参数的 v2 文件缺少新字段时，补普通缺省，未给出的光壳/核心辉光关闭。原 coreEmission、jetStrength 以及所有显式字段值保留；白洞粒子和已有喷流的渲染路径仍是升级后的实现，不宣称旧白洞逐像素恢复。现有网页只提供黑洞/world-v1 桥，不能仅换 schema 标签宣称支持 v2。

## 运动、积分与遮挡

- 核心颜色单独乘倍率，核心命中保留之前积累的盘辐射。独立核心 corona 使用核心/边缘颜色和 uCoreEmission，在 core × coreGlowRadius 内软衰减至零，不取 shellColor 或 HaloStrength、无时间动画；coreGlow 0、增益 0、黑模式或黑色核心时精确关闭。解析球区间后 16 段 Simpson 积分，不把辉光做成不透明环。盘纹负径向相位负责向外运动；光带/电影盘的 64/128 单位材质生命周期连续换种。
- 白洞粒子内缘出生，`q=r^1.5` 单调外增，4–7 单位生命周期，轨道角速度 `1.15/q` 沿同一路径积分；黑洞分支仍用原轨迹。白洞粒子增加径向伸展、有限离面扇开与外域亮度，出生/末端淡出，不是倒放旧 alpha。
- `uJetClock` 是沿两侧轴线的向外位移，单位为局部长度，Java 积累 jetSpeed。固定轴向特征取 `abs(y)-start-clock`，波结间距 0.90，兩端同时向外；颜色按所在空间从 base 到 tip 分布，不以整个物体闪烁冒充输运。倾角变更使用同一个正交盘空间。
- `uShellClock` 同样积累向外位移。出生半径 `core*1.08`，周期跨度 `max(shellRadius-core*1.08,0.35)`；两层错开半个周期，径向薄层随 clock 外扩，出生与外围归零淡出。它是视觉发光壳，不是造成地形伤害或推力的冲击波。
- 五个 `BlackHoleFlowClock` 分别负责旋转、盘纹、粒子、喷流和光壳；仅正常世界白洞播放且 `frozenTime < 0` 时，HOT 速度为 0 保持当前相位，恢复连续，playing false 也冻结。短暂 partialTick 回退不能复位。开发桥 settings.time ≥ 0 是固定截图秒数旁路，直接使用 t × speed，会绕过 FlowClock；它不是连续暂停入口。settings.time = -1 才退出该旁路。喷流/光壳传原累计长度，不能直接套用旧 128 单位取模，因为 0.90 波结和可调壳跨度不满足该闭合周期。
- 新 `whiteOutflowTrace` 仅在白模式运行，使用直视射线、原场景可见表面深度，以及实体/地形和核心近面的截断。它在前景实体前方也能绘制；地形遮挡不会继承主核/盘面忽略原深度的美术策略。旧 bent-ray jetEmission 仅对黑洞启用，白洞不重复画旧喷流。
- 喷流先解析有限圆柱区间，再最多 160 段 Simpson 长度积分；光壳分别求薄层内外球面交点，对前后带各 12 段积分。屏幕足迹扩宽重建并补偿 radiance，避免薄层在侧视、低质量或远距离漏采。摄像机在壳内、沿极轴与切线方向都应取有限结果。
- 世界支持范围至少覆盖核心辉光边界、喷流长度/宽度及 `shellRadius+3*shellWidth`。Java 开发探针半径也包含新形状；粒子仍独立使用真实深度测试。世界专用的新 uniform 不能泄漏到粒子 shader。
- 前景 AABB 上传保持局部 UnpackState，隔离并恢复 7 项 pixel-store 以及 pixel-unpack PBO；资源重载不能沿用图集子矩形偏移上传紧凑 FloatBuffer。

## 原始输入与验证边界

`white_hole_adapter.py` 保存白洞 field，`world_header/main.glsl` 保存宿主逻辑，world-kernel/layers/三份 world-*.glsl 保留原内核。`generate_resources.py` 在 mature 世界段插入 world_functions，particle_main 仅含粒子消费代码；mature world/particle 顶点与 JSON 是接口输入。新增世界 field 不污染 item/scene 旧输出。修改源后重生成并审计，不只改输出 fsh。

`audit_resources.py` 验证 12 文件可重生成、独立命名空间、新缺省和粒子未声明世界外喷 uniform。`BlackHoleCheck` 覆盖 74 字段增强预设、完整旧 66/72 字段 v2、部分预设、类型、范围边界、历史格式拒绝与解码原子性；时钟检查覆盖暂停/HOT/partialTick。Python 审计不代替 Java、GPU 或游戏检查。

本次新增外喷结构必须按新证据 `evidence/white-hole-outflow-20261007/` 的 GPU 与生产 Forge 报告独立取证。旧 `white-hole-20261007` 的 18 阶段/146 检查和 141 项 WebGL2 结果只认证旧环形外流版本，不能移用给新喷流/光壳。生产宿主的游戏验收不等于独立模板游戏验收；此轮模板同步未启动独立 Minecraft。模板 Java 构建由隔离副本执行，构建和生产新实机最终数值另记同步报告。

2026-10-07 旧外喷与核心体积辉光验收：生产 Forge 33/33 场景、287/287 检查通过；独立核心辉光开关贡献 2324 像素，零核心增益在核心轮廓外贡献为 0，墙后外喷贡献为 0。SwiftShader WebGL2 154/154 检查通过（61 项语义、93 项 GL 检查），其合成默认图仅含世界层，粒子另用 transform feedback 验证轨迹。正式模板 65 个输入文件与隔离副本同字节，`blackHoleCheck build` 通过；这不等于模板独立游戏验收。这些结果认证新增屏幕 Bloom 之前的版本。地面/浮空/倾斜截图使用实际默认外观，只固定截图时间和场景位置，没有放大粒子。

此版另有核心专用屏幕 Bloom；它不是照亮地形的动态光源。此版不含蓄能爆发、力学冲击、服务端同步、跨光影管线适配或科学相对论输运；核心/盘仍保留原美术透镜与特殊地形深度策略，外喷层独立真实深度截断。

## 单操作者开发桥的停速/恢复

运行中的独立模板读取 gameDir 下 `black-hole-lab/command.json`。先完成 white 放置，确保正常播放。将下面完整 JSON 写入该文件，等待新的 command-result.json，再写恢复请求。`time:-1` 明确退出固定截图旁路：

```json
{"action":"settings","time":-1,"jetSpeed":0,"shellSpeed":0}
```

```json
{"action":"settings","time":-1,"jetSpeed":1.15,"shellSpeed":0.7}
```

这是顶层 settings JSON 开发桥，不是聊天命令；不存在 jet_speed、shell_speed 或 pause 命令。暂停单独两种输运不会停止其他盘纹/粒子，也不会停止静态核心辉光。不要用反复 import 实现连续停速：每次 import 会重置 clock。普通配方 playing 可定义初始播放状态，但重新导入不是保持当前相位的暂停方法。

## 2026-10-07 核心光团升级

用户参考要求中心表现为亮白光团。新版保持 74 字段，复用 coreGlow/coreGlowRadius 控制体积冠层和核心专用屏幕扩散，白洞默认改为 1.2/2.6，核心辐射倍率仍为 4。旧完整配方缺省 0/1.8 保持关闭扩散。关闭 coreGlow 后仍保留球体自身辐射；增益 0、黑模式或黑色核心不产生新增光。

BH_CORE_CAPTURE 在原射线捕获的四处核心命中点取得 result.transmission × bhCoreEmission，严格只选择核心，盘、喷流、光壳、粒子与白色天空不是发光源。LensProbeMode=3 的源重放写入半分辨率 RGBA16F，亮度不先压成 LDR；分别归一化水平/垂直双尺度 blur，随后对独立最终场景副本在线性空间合成曝光映射后的光。微弱六向短星芒来自已选择的光源，不作为独立光源存在。

源沿用可见核心的整实体前景保护及美术地形策略，不继承新外喷层的原深度截断：核心的既有地形可见性也适用于其 Bloom，不能声称全墙真实遮挡。前景实体本身没有源，但邻近可见核心的屏幕光学柔光可溢到前景像素。冠层的世界半径仍有限，屏幕尾部是投影尺度上的光学扩散；coreGlowRadius 不再能宣称是整个效果的严格世界边界。探针支持范围已扩展。无透镜与最终 field 取证都应用相同扩散，诊断图不应用。

TextureTarget 的 HDR 附件分配保护 atlas UNPACK 与 PBO 状态；场景、源和 ping-pong 目标独立，不读写同一颜色附件。尺寸改变、资源重载、停止和退出释放资源，主深度保持不写。此管线专用于原生 Forge 1.20.1 主 target；没有认证第三方光影管线。生产与模板的验证记录见 evidence/white-hole-radiance-20261007/；旧33阶段与154检查认证上一版，不自动认证本轮 Bloom。模板独立 Minecraft 本轮仍未启动。

本轮最终结果：生产 Forge 39/39 场景、650/650 检查通过，HDR 源峰值大于 1，主深度保持不写；近/远/窗口重建时亮核 RGB 均为 255，来源范围外同帧扩散贡献分别 16350/7782/20775 像素，零增益外部贡献为 0，窗口恢复后图像差异为 0。SwiftShader WebGL2 391/391 检查通过（56 项语义、112 项无附件反馈、223 项 GL 检查）；宽光晕只有一个连续包络，旧稀疏采样的点状复制尝试已归档且不作为交付。18 资源重生成审计通过；78 个正式模板输入与隔离构建同字节，`blackHoleCheck build` 通过。模板没有独立游戏验收。

## 2026-10-07 用户默认与尘埃盘修复

当前白洞默认以 `assets/presets/white-ring-default.json` 为准，共 75 个 world/v2 字段。用户原有 74 值全部保留，包含 scale=1.8、distance=39、tilt=-1、classic 盘、density=0.6、core=0.28、coreEmission=3.95、coreGlow=0.8、coreGlowRadius=1.05、seed=46271、particles=false、lensAffectsPlayer=true、playing=true、time=37862.8、jetStrength=0。新增 shellEnabled=false；此用户预设不要求喷流或独立粒子默认开启。

shellEnabled 为独立布尔 HOT 开关。关闭只将传给世界 shader 的 uShellStrength 置零，保留 shellStrength/Speed/Radius/Width/Color，核心光团、盘面及透镜不变。缺少该字段的旧完整 74/72/66 字段 world/v2 导入缺省 true，已有显式 shellStrength=0 仍关闭；历史 preview/v1/v2 与 world/v1 拒绝该新字段。部分 white-ring 导入使用当前默认，显式字段最后覆盖。核心 Bloom 仍只选择核心源，光圈不进入发光源。

尘埃盘为程序体积，与 particles 的独立点粒子开关分开。旧内缘排除硬编码 +1.12/+1.72、外缘 -0.8 挤掉窄盘有效范围，四层噪声乘积及阈值再次消耗密度。现在内缘排除与外缘淡出按盘宽比例计算，噪声采用归一化加权八度与暗通道，修正尘埃专用光学密度；保留场景遮挡、步长积分和黑色核心吸收。变更会改变 dust 风格外观，其他四种盘风格保持原路径。dustFill=0 或 density=0 可以清空尘埃，dustScale 影响结构。

生产工作台项目入口为一个可滚动下拉按钮，新增项目无需扩张参数区。保留 Ctrl+Tab 切换与各项目 F6 滚动位置。独立 Forge skill 模板的入口仍是命令及 recipe/settings JSON，模板未包含生产工作台 UI。
