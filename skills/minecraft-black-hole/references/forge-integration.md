# Forge 1.20.1 接入

`assets/forge-1.20.1` 是自包含的 `dev.blackholelab` 示例源码：Java 17、Forge 47.4.10、官方 mappings、ForgeGradle 6.0.54、Gradle 8.8。世界示例是客户端固定坐标场，不是服务端同步实体。物品/面板保留旧内部场；世界/粒子使用 v3.2 场，不能把一种路径的验证扩展到另一种。

## 构建与命令

复制工程到工作目录后运行：

```powershell
python scripts/generate_resources.py
python scripts/audit_resources.py
./gradlew.bat --console plain blackHoleCheck build
```

得到 reobfuscated `build/libs/black-hole-lab-0.1.0.jar`。首次下载依赖需网络，已缓存可 `--offline`。`generate_resources.py --kernel <path>` 仅替换旧物品/面板内核；世界源是 `world-kernel.glsl`、`world-layers.js`、`world-*.glsl`、`world_header.glsl`、`world_main.glsl` 和 `mature-*.json/vsh`。不要只改生成后的 fsh，审计检查重生成一致。

世界生成段对未适配的原始世界内核和盘函数单次应用 `scripts/white_hole_adapter.py`，增加白洞行为。详情与验收边界见 [白洞外流](white-hole.md)；不要把 adapter 已处理过的 GLSL 再当作原始输入。

| 客户端命令 | 行为 |
|---|---|
| `/blackhole world` | 向视线射出放置段；命中方块表面沿法线抬起核心半径加 0.05 格，否则使用远点；之后中心固定 |
| `/blackhole white` | 应用当前用户的蓝白 `white-ring` 光团预设并在视线目标处预览 |
| `/blackhole black` | 恢复独立模板的默认黑洞并重新放置 |
| `/blackhole off` | 关闭并释放场资源 |
| `/blackhole size 1.6` | 世界尺寸 0.2–8；局部长度乘此值变为方块长度 |
| `/blackhole color 7D3CFF` | 核心颜色；000000 恢复黑色 |
| `/blackhole tilt 0` | 盘倾角 −90–90°；默认 0°，盘面与地面平行 |
| `/blackhole lens false` | 关闭背景映射；盘本身的弯曲射线仍保留 |
| `/blackhole lens_strength 0.7` | 背景强度 0–1，默认 0.7；与 bend 共同决定偏折 |
| `/blackhole lens_radius 2.4` | 背景范围 0.2–16 局部单位；与盘外半径独立 |
| `/blackhole lens_players true` | 允许所有玩家参与畸变；默认关闭，开启不受前后位置保护否决 |
| `/blackhole rotation 0.8` / `infall 0.22` | 角向运动 0–3 / 径向纹理输运 0–2，互不替代 |
| `/blackhole particles true` / `particle_speed 1` | 粒子开关与独立速度 0–4，0 冻结 |
| `/blackhole halo 1` | 三层局部光晕总量 0–3 |
| `/blackhole import` / `export` | 读取/写入 config/blackholelab/recipe.json |
| `/blackhole showcase` / `items` | 内部场与原生物品样例面板 |

水平只是初始预设，导入显式 −51° 等角度保留。放置后调尺寸不会重新寻找目标；需要重放置再执行 world，禁止每帧按玩家视线重算中心。

## 配方与工作台映射

支持导入 black-hole-preview/v1、v2 和独立宿主 black-hole-world/v1、v2。完整校验数值、颜色、布尔、质量及盘半径关系后原子应用，失败保留旧状态。新导出为 **black-hole-world/v2**，数字/布尔保持 JSON 原类型，新增 objectMode、coreEmission、coreGlow/Radius、jetSpeed 及 shellEnabled、shellStrength/Speed/Radius/Width/Color。历史 preview/v1/v2 和 world/v1 拒绝这些新增字段及 white-ring 预设，缺字段恢复 black/1 和关闭新层。部分 world/v2 的 form:white-ring 使用当前增强预设（核心倍率 3.95、光芒 0.8/1.05，其他值见默认配方）；完整旧 66/72 字段配方保留显式数值，并补缺省而不激活未给出的新层。旧白洞渲染路径升级，不能宣称像素恢复。

网页工作台保留历史 **world/v1 黑洞桥**，可以编辑并重导 scale、distance、lensAffectsPlayer 等共用世界字段；当前网页不接受 world/v2、没有白洞行为。新白洞配方使用 Forge 命令或接入宿主的参数 UI 调整，不用改 schema 名称伪装成网页旧格式。

| 字段 | 世界示例处理 |
|---|---|
| core/inner/outer/thickness/tilt/bend/density/seed/steps | 同局部单位和内核参数；内盘至少 core+0.12，外盘至少 inner+0.5 |
| objectMode、coreEmission | world/v2：black 内流、white 外流；核心辐射倍率 0–4，正质量透镜保持原符号 |
| diskStyle、hot/cool/diskMid、diskGradientPosition、coreColor/coreEdgeColor、coreGradientStrength | 五种盘及线性 RGB 渐变实际接入 |
| speed、infallSpeed、time、playing | 相位独立，时间秒，pause/resume 连续；明确给出 time/playing 时保留 |
| beaming、bandDetail、dustScale/dustFill、filament/spiral | 世界盘结构实际接入 |
| halo 和三层量、haloRadius/Width/Sharpness、各层色和渐变混合 | 同弯曲路径体积辐射实际接入 |
| jetStrength/Length/Width、jetBaseColor/TipColor、jetSpeed | 黑模式保留旧弯曲射线场；白模式沿两侧盘法线输运，真实场景深度截断；基础强度默认 0 |
| shellStrength/Speed/Radius/Width/Color | 白模式重复外扩球壳；最大半径为局部单位，薄层解析区间积分；基础强度 0 |
| coreGlow、coreGlowRadius | 白模式有限体积冠层与核心专用 HDR 屏幕扩散；强度 0–3，范围倍率 1.05–4 控制冠层及投影光芒宽度；使用核心/边缘色和 coreEmission，不受 halo/shell 开关控制；屏幕柔光尾部不受严格世界球边界限制 |
| particles、particleCount/Speed/Size/Outer/Brightness、particleCold/Hot | 独立 sprite 路径；中心直线投影，内核控制遮挡 |
| lensEnabled/lens、lensBackground/lensStrength、lensRadius、lensAffectsPlayer | 世界开关/强度/范围/玩家参与；缺玩家字段为 false |
| viewDistance | 无 distance 时乘当前 scale 成放置距离；scale/distance 为世界扩展，单位倍率/格 |
| exposure | 同源数值；Minecraft 与网页 HDR 管线不同，像素亮度不保证一致 |
| world/v1 的 scale/distance/lensAffectsPlayer 等 | 网页保留并重导，但不伪造玩家模型预览；背景开关/强度与共用字段编辑同步 |
| particleMid/particleGradientPosition、网页 sky/background、bloom/bloomRadius、yaw/pitch/orbitSpeed、mode | 只保留元数据；Forge 粒子当前为软 sprite、两端色，真实世界不替换网页背景、不用网页相机、不包含网页 HDR bloom |

旧 v1/v2 缺层保持 classic、particles=false、halo=0、infallSpeed=0。新独立世界展示默认颗粒盘/粒子/三层光晕，不能覆盖旧配方缺省。背景强度控制真实角向映射，与网页程序星域合成没有逐像素等价保证。

开发桥在 gameDir 的 black-hole-lab/command.json，一次写完整请求，读新 command-result.json 后再发。settings 保留 scale/distance/lens/lensRadius/rotation/infall/exposure/tilt/time/particles/particle*/halo*，支持 lensAffectsPlayer、objectMode、coreEmission、coreGlow/Radius、jetStrength/Length/Width/Speed 和 shellEnabled、shellStrength/Speed/Radius/Width/Color；white/black action 对应新命令。time=-1 退出固定截图旁路，其余非负数是截图秒数并绕过 FlowClock，不是连续暂停入口；这是单操作者开发桥，不是并发配方 API。probe 输出同帧 before/no-lens/field/after、源覆盖诊断和 JSON；名称限字母、数字、下划线、短横线。

正常世界白洞且 frozenTime<0 时可连续保持/恢复喷流和球壳相位。开发文件的完整请求为：

```json
{"action":"settings","time":-1,"jetSpeed":0,"shellSpeed":0}
```

随后等待 command-result.json，再写 `{"action":"settings","time":-1,"jetSpeed":1.15,"shellSpeed":0.7}` 恢复。不存在 jet_speed、shell_speed 或 pause 聊天命令；不能靠重复 import 停速，因为 import 会 resetClock。独立模板本次未启动 Minecraft，生产宿主实机不能认证这些命令的独立运行。

## 世界渲染契约

1. AFTER_CUTOUT_BLOCKS 保存实体之前环境颜色/深度；AFTER_PARTICLES 保存地形实际 projection/view、相机位置、partialTick；AFTER_LEVEL 在云层后合成。后者矩阵可能变化，禁止直接替代地形投影。
2. camera-relative **世界轴**重建深度。光学眼点为 inverseVP × (0,0,1,0) 除 w；bob/hurt/portal 仿射变换可能进入 projection，不能只用 raw camera 或 inverse(view)。像素射线从此眼点指向逆投影远点。
3. 角向透镜围绕质量轴在 360° 方位连续。地面/天空使用同一完整偏折；不要用表面前后分类截掉半圈地面或按地面深度减弱到不可见。范围/强度独立，屏外源平滑退回原像素。
4. 前景只保护比环境深度更近的实体像素。CPU 按**整实体**分类，跨质量平面的身体不能逐片元拆开；相机主体扩展边界覆盖动画手臂/装备。玩家关闭保护全部玩家，开启后直接参与；非玩家相机主体仍保护。参与可能出现艺术化多重像/核心覆盖，limb 被保护规则切碎是 bug。
5. 保护原实体像素，并在扭曲源中先恢复其后环境再做四 tap 双线性过滤；仅拒绝 sample 会产生洞/假重影。真实演员覆盖验收须比较实体前环境与同帧原场景深度，不能只看 CPU AABB。
6. 独立副本禁止采样正在写的附件，FBO stencil 与主目标一致。异常也恢复 read/draw FBO、viewport、textures/active unit、blend/depth/cull/scissor、clear color/depth、颜色写入。ShaderInstance BlendMode 缓存独立于手动 GL，保存原对象（含 null）再恢复，不能制造近似对象。
7. 前景 FloatBuffer 的 glTexImage2D/SubImage2D 上传不能继承图集的 UNPACK 子矩形状态或 PBO。局部 `UnpackState` 建立紧凑上传设置并在 try-with-resources 结束时恢复 7 个 pixel-store 值及 PBO，资源重载后也遵守同一上传契约。

弯曲路径积累盘/光晕和黑模式旧喷流，命中核心保留前方光；白模式喷流、球壳和核心辉光走独立直视路径，按原场景深度及核心近面截断。世界不改原深度。粒子地形深度测试后 ADD，不写深度。resize/reload/world switch/off 释放快照、环境、前景纹理、粒子 VBO。

## 普通物品能力保持

BlackHoleModels 保留非 custom-renderer baked model override/transform，逐 sprite 归一 UV。例：

```mcfunction
/give @s minecraft:diamond_sword{BlackHoleMaterial:{CoreColor:8207615,Mask:"blade"}}
/give @s minecraft:diamond_block{BlackHoleMaterial:{CoreColor:0}}
```

CoreColor 为24-bit RGB，可用 Palette=violet/cyan。Mask=blade 仅原版钻石剑颜色阈值示例。无标签沿用原路径，item.fsh 保留 alpha/overlay/fog/foil。此处仍是每 sprite 虚拟相机，不是整个模型连续场；世界五盘/粒子/光晕/喷流不会自动进入物品。BEWLR/护甲/ModelPart/骨骼/第三方透明排序需单独适配。

## 验证范围

2026-10-05 独立迁移后：build/reobfJar、180 组 fixed-anchor bob/hurt/off-axis/portal 眼点数学、配方类型/显式角度/老兼容/非法输入、12个生成资源一致和18项360角向数学通过。新增世界/粒子 shader WebGL2 接口链接、盘面输运/差速/换相8项、粒子 transform feedback8项、7个水平预设及 Forge→工作台→Forge 字段保留/非法metadata回归通过；这些GPU检查属于浏览器路径。**新鲜 Forge 47.4.10 开发客户端**从首次引导打开独立地面存档，六视点（朝地面放置、左右移动、垂直、斜视、hurt）核心实际 PNG 对独立固定球最大轮廓误差均0 px，GL error/depth difference 均0。它证明首次初始化、真实 shader 加载和地面核心定位，未认证所有开启透镜/玩家开关/粒子动画/物品组合。复跑见 [相机与透镜回归](world-regression.md)。

历史2026-09-29 ASTRA-TEST / RTX4060Ti：35同帧捕获/28世界检查、632粒子GPU跟踪和更早物品10项，属于迁移前版本。2026-10-04 AFTER_LEVEL 云层修复当时仅源码/候选JAR修改；本轮已加载新入口，不能把旧多场景截图当作新全路径认证。

屏幕空间不能恢复离屏/完全不可见几何。透明层依赖原顺序，Fabulous/OptiFine/Oculus/光影、其他GPU、多黑洞未认证。局部halo不是屏幕bloom；无科学GR、流体/物质吸取、服务端同步或性能基准。

新增核心光团采用 RGBA16F 核心专用 emission、双尺度 blur 和线性屏幕合成。源来自原核心捕获及盘透射，不采亮度阈值；源继承核心美术地形可见性，前景实体不进入源，但光学柔光可覆盖前景。coreGlow/Radius 同时驱动有限冠层与投影扩散，详见 white-hole.md 的核心光团升级节。该升级不是场景照明。

## 2026-10-07 用户默认与尘埃盘修复

当前白洞默认以 `assets/presets/white-ring-default.json` 为准，共 75 个 world/v2 字段。用户原有 74 值全部保留，包含 scale=1.8、distance=39、tilt=-1、classic 盘、density=0.6、core=0.28、coreEmission=3.95、coreGlow=0.8、coreGlowRadius=1.05、seed=46271、particles=false、lensAffectsPlayer=true、playing=true、time=37862.8、jetStrength=0。新增 shellEnabled=false；此用户预设不要求喷流或独立粒子默认开启。

shellEnabled 为独立布尔 HOT 开关。关闭只将传给世界 shader 的 uShellStrength 置零，保留 shellStrength/Speed/Radius/Width/Color，核心光团、盘面及透镜不变。缺少该字段的旧完整 74/72/66 字段 world/v2 导入缺省 true，已有显式 shellStrength=0 仍关闭；历史 preview/v1/v2 与 world/v1 拒绝该新字段。部分 white-ring 导入使用当前默认，显式字段最后覆盖。核心 Bloom 仍只选择核心源，光圈不进入发光源。

尘埃盘为程序体积，与 particles 的独立点粒子开关分开。旧内缘排除硬编码 +1.12/+1.72、外缘 -0.8 挤掉窄盘有效范围，四层噪声乘积及阈值再次消耗密度。现在内缘排除与外缘淡出按盘宽比例计算，噪声采用归一化加权八度与暗通道，修正尘埃专用光学密度；保留场景遮挡、步长积分和黑色核心吸收。变更会改变 dust 风格外观，其他四种盘风格保持原路径。dustFill=0 或 density=0 可以清空尘埃，dustScale 影响结构。

生产工作台项目入口为一个可滚动下拉按钮，新增项目无需扩张参数区。保留 Ctrl+Tab 切换与各项目 F6 滚动位置。独立 Forge skill 模板的入口仍是命令及 recipe/settings JSON，模板未包含生产工作台 UI。


2026-10-09 性能约束：复用 CPU 数值与上传缓冲，不通过降低采样精度、网格分辨率或粒子数量替代优化。黑洞／白洞前景遮挡缓冲每次使用前 clear 并完整覆盖，保证 GL/UNPACK 状态恢复；水面高度每帧每节点只采样一次，再复用邻点生成法线，保留原网格边界与插值语义。宿主参数缓存必须在单值修改、原子导入及重置默认时失效。
