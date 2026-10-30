# 遁光 V3：美术与材质学习资源

核查日期：2026-10-15。用户要求优先还原参考、提高画质，允许先增加层次再优化；仅制作着色器/程序特效，不加入人物、坐骑、飞舟、法器等模型。

这轮重点从“换轮廓的发光网格”转向**有层级的亮核、方向性流丝、柔烟密度、局部侵蚀和真实屏幕辉光**。以下 8 条是本轮实际访问的一手页面/源码；其中两包 CC0 素材已经下载、解包、阅读原许可并检查图像。资源建议与原型实际采用项分开记录，不能据此宣称已还原视频或接入 Forge。

## 先应用的五件事

1. 白蓝羽丝：一条清晰主流 + 少量有长短/宽度层次的分枝；尖头极亮，后缘薄纱低对比，尾迹沿世界曲线留存。不要每根线等粗等亮。
2. 烟和火用纹理密度，替换硬质低面团块。红黑烟大形主要吸收/alpha，亮核只占少量面积；白雾也保留透光间隙。
3. UV 流动用双相/错相解决回绕，流动、几何运动、侵蚀、出生寿命分别控制。主轮廓保持方向感。
4. HDR 发射在浮点缓冲中累积，多尺度模糊形成真正 Bloom，再统一 tone mapping。几何外壳、宽条带不当成 Bloom。
5. 素材允许多层叠加，但每层都服从大形与值域。灰度和关闭 Bloom 后仍看得出主次；“堆料”增加层次，不能只堆亮度。

## 已搜集的合法素材

本地目录：[v3-resources/kenney](E:/着色器渲染/research/dunguang-flight-20261015/v3-resources/kenney)。原始 zip、解包目录及包内原始 License 均保留；下载来源、日期、zip 与逐文件 SHA256 见 [sources-and-hashes.json](E:/着色器渲染/research/dunguang-flight-20261015/v3-resources/kenney/sources-and-hashes.json)。PNG 只是着色器的密度/发射遮罩，不是新增游戏模型。

| 包 | 实际归档 | 许可 | 本轮使用价值 |
|---|---|---|---|
| Particle Pack 1.1 | 15,001,764 bytes；透明基础形 80 张，另有旋转与黑底变体 | 官方页和归档 License.txt 均 CC0 | 软烟、细碎侵蚀、薄纱、细丝、闪光 |
| Smoke Particles | 6,019,666 bytes；PNG 目录 77 张形态/颜色变体 | 官方页和归档 license.txt 均 CC0 | 红黑烟与白烟次层、少量爆发/闪光 |

已将 11 张优先图原样复制到 [selected](E:/着色器渲染/research/dunguang-flight-20261015/v3-resources/kenney/selected)，并保留 [Kenney-License.txt](E:/着色器渲染/research/dunguang-flight-20261015/v3-resources/kenney/selected/Kenney-License.txt) 和 [逐图来源与 SHA](E:/着色器渲染/research/dunguang-flight-20261015/v3-resources/kenney/selected/selection-manifest.json)：

| 文件 | 已观察到的形态 | 应用建议 |
|---|---|---|
| smoke_07.png | 多团连接的软密度、低对比柔边 | 宽而薄的气态次层，低 alpha，多层错位 |
| smoke_08.png | 更清晰的卷云团边和凹空 | 主烟密度的一部分，避免高 alpha 一团团排队 |
| smoke_01.png | 细碎、稀疏、内部有孔的薄烟 | 主尾迹外缘的破碎纱层 |
| fire_01.png | 高低密度跨度较大的细碎噪形 | 高频侵蚀或局部燃烧密度，避免全屏均匀纹理 |
| flame_01.png / flame_02.png | 弯曲的薄纱与细亮边 | 气焰局部丝膜，配合二次贝塞尔/曲线 carrier |
| trace_01.png / trace_05.png | 细长柔亮线，后者有不规则弯曲 | 拉伸的短细丝；持续长尾仍使用历史带 |
| flare_01.png | 小中心亮点与横向细辉线 | 极小头部发射点，控制屏幕尺寸 |
| twirl_01.png | 细窄弧形亮纱 | 局部卷动辅助，不能每种遁光都套同一圆环 |
| spark_06.png | 纵向细碎电亮线 | 仅特定雷电/电弧形态的小面积辅助 |

实际检查了两包接触表和 smoke_07 / fire_01 / trace_05 单张原图。PNG 的 RGB 与 alpha 都含灰度；用 tint×RGB 后再乘 alpha 会再次压低密度。可先把 alpha 当作线性 density mask，再独立设置烟色/发射。预乘或普通 alpha 的混合约定必须与 shader 输出一致。文件编号与相似轮廓不证明是连续模拟帧，不能把这些静态变体宣传成已验证的烟雾 flipbook。

接触表：[Particle 透明形态](E:/着色器渲染/research/dunguang-flight-20261015/v3-resources/kenney/particle-transparent-contact.jpg)、[Smoke 全组](E:/着色器渲染/research/dunguang-flight-20261015/v3-resources/kenney/smoke-particles-contact.jpg)。没有把用户影视视频截帧加工成游戏素材。

## 已读来源及具体迁移

### 1. Riot Games · Clarity in League · P0

作者：Riot Games / bananaband1t。[原始入口](https://www.leagueoflegends.com/en-us/news/dev/clarity-in-league/)。

**实际阅读：** The Unbreakable Vows: gameplay / hierarchy / noise；Silhouettes: primary characteristic and facing direction；Abilities: bright/saturated attention hierarchy；Ability Considerations / Direction: projectile tail blur；Skins / Abilities and Maps。

**学什么：** 先把效果的大轮廓、朝向与主次亮度做清楚，再加入中小细节；尖头与衰减尾端即使在静帧也应传达前进方向。高亮与高饱和集中在值得注意的区域，不把每层都推到白。

**本轮如何应用：** 白蓝羽丝：亮核最细最亮，少数主枝次亮，宽烟纱最低对比；红黑烟遁：暗烟大形与少量红色发射区分。保留彩色、灰度、无 Bloom 三种截图审阅。

**复制/素材许可：** 网页未核实开放复制许可；阅读链接与原创总结，不能把 Riot 游戏图、视频、材质当可复用资产。

**核查边界：** 2017 VFX Style Guide 的官方 Nexus 页面与原 PDF URL 本次均重定向首页；本条是已读的一手替代文章，不能标记旧 Style Guide PDF 已读。

### 2. Catlike Coding · Texture Distortion · P0

作者：Jasper Flick / Catlike Coding。[原始入口](https://catlikecoding.com/unity/tutorials/flow/texture-distortion/)。

**实际阅读：** Flow Direction / Flow Maps；Sawtooth / Triangle Wave / Seesaw: phase A/B and complementary weights；FlowUVW final function: flowOffset / tiling / phaseOffset / jump；Flow Speed and Flow Strength；Code & Asset License and Tutorial License。

**学什么：** RG 流向由 0..1 解码到 -1..1；双相 UV 采样以半周期错相，并用互补三角权重相加，避免单一 frac 回绕跳变。噪声可偏移局部时间；flow strength 与周期速度分别调。

**本轮如何应用：** 在长条带的沿程 U / 横向 V 中加入小幅卷动，不让整个轮廓变形为均匀圆管。白蓝烟纱以两个相位的同一遮罩交替，烟层另外做低频扭曲和细频侵蚀。

**复制/素材许可：** 官方现行许可：代码、纹理及教程工程文件 MIT-0；教程正文、截图、图表、动画和 PDF 为 CC BY-NC-SA 4.0。 [许可依据](https://catlikecoding.com/unity/tutorials/license/)。

**核查边界：** 本次未下载作者 Unity 工程或纹理；已读函数与许可可指导原创 GLSL 移植。UV 扭曲不等于背景折射，也不等于三维流体平流。

### 3. Kenney · Particle Pack 1.1 · P0

作者：Kenney Vleugels；包内另列滤镜模板作者。[原始入口](https://kenney.nl/assets/particle-pack)。

**实际阅读：** 官方资源页：512×512 / 80 files / CC0；下载归档 License.txt 全文；PNG (Transparent) 80 张及 Rotated 16 张的接触表；逐张原图 smoke_07.png / fire_01.png / trace_05.png。

**学什么：** 现成的软烟密度、细节侵蚀、细长亮丝和光芒遮罩比少面多边形团块更适合气态表现；可由 Shader 改色、拉伸、扰动、分层。

**本轮如何应用：** 当前运行将 smoke_01 / smoke_07 / smoke_08 / smoke_04 合成烟图集，fire_01 用作侵蚀，trace_05 用作流丝遮罩；其余精选图保留作学习候选。主尾迹仍按真实历史曲线绘制，不能只在发射位置后面贴静态长图。

**复制/素材许可：** 官方页和归档 License.txt 均为 CC0，可用于个人及商业项目，署名非强制。本次仍保留原许可、作者与来源。 [许可依据](https://creativecommons.org/publicdomain/zero/1.0/)。

**核查边界：** 官方页面 80 指基本形状数；归档还有黑底版、透明版、旋转变体和预览，不把副本算作新特效。单张变体不是经确认的连续 flipbook。

### 4. Kenney · Smoke Particles · P1

作者：Kenney Vleugels。[原始入口](https://kenney.nl/assets/smoke-particles)。

**实际阅读：** 官方资源页 CC0；归档 license.txt 全文；PNG 下 Black smoke / White puff / Explosion / Flash / Fart 各组接触表。

**学什么：** 黑烟、白雾与发射火光是三种不同的密度/颜色职责；烟雾保留内部灰阶和柔边，少量闪光承担亮核。

**本轮如何应用：** 本包已下载、查看并保留作后续素材候选，尚未直接接入运行；当前烟图集来自 Particle Pack。后续可用 Black smoke 做暗色低发射主层、White puff 做低 alpha 气态次层；避免将 Explosion 或 Flash 等亮铺满。

**复制/素材许可：** 官方页及归档 license.txt 均 CC0，可用于个人及商业项目；保留来源记录。 [许可依据](https://creativecommons.org/publicdomain/zero/1.0/)。

**核查边界：** 官方页标 70 files；当前归档 PNG 文件夹实际是 25 黑烟 + 25 白烟 + 9 爆炸 + 9 绿烟 + 9 闪光。编号及相似轮廓不能证明是连续时间帧。没有将这些静态变体宣传为真实模拟 flipbook。

### 5. three.js · UnrealBloomPass 与 LuminosityHighPassShader · P0

作者：three.js authors。[原始入口](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/examples/jsm/postprocessing/UnrealBloomPass.js)。

源码快照：`a58e9ecf225b50e4a28a934442e854878bc2a959`。

**实际阅读：** UnrealBloomPass.js: HalfFloatType / nMips=5 / renderTargetsHorizontal + Vertical；kernelSizeArray=[6,10,14,18,22] / _getSeparableBlurMaterial；bloomFactors=[1,.8,.6,.4,.2] / _getCompositeMaterial / lerpBloomFactor；render: bright pass / horizontal+vertical blur / compositing；LuminosityHighPassShader: smoothstep(luminosityThreshold, luminosityThreshold+smoothWidth, luminance)；固定提交 LICENSE 全文 MIT。

**学什么：** 窄亮核和扩散光晕由两个职责产生：HDR 发射保留大于 1 的亮度；多尺度模糊产生近场亮晕、柔和远场辉光。宽几何条带无法替代屏幕 Bloom。

**本轮如何应用：** 来源实现采用 5 级模糊；当前 V3 实际采用独立发射缓冲和三尺度横纵模糊/合成。HDR 颜色在线性空间累积，再统一 tone mapping 和输出转换。只有遁光的 emission 进入 Bloom，烟密度不无条件送入。

**复制/素材许可：** MIT；保留 three.js 版权和完整许可。数学参考可原创实现，直接复制/改编代码时包含许可。 [许可依据](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/LICENSE)。

**核查边界：** 此为新增 Bloom 具体实现阅读；已有研究中的 three.js Curve 是不同文件和算法。WebGL 半浮点 FBO 与硬件扩展仍须检查；这不是 Minecraft Forge 已验证的接入。

补充原始文件：[文件 1](https://github.com/mrdoob/three.js/blob/a58e9ecf225b50e4a28a934442e854878bc2a959/examples/jsm/shaders/LuminosityHighPassShader.js)。

### 6. Godot 4.4.1 · 粒子材质、动画与透明约定源码 · P1

作者：Godot Engine contributors / Juan Linietsky / Ariel Manzur。[原始入口](https://github.com/godotengine/godot/blob/4.4.1-stable/scene/resources/material.cpp)。

源码快照：`4.4.1-stable tag`。

**实际阅读：** BaseMaterial3D::_update_shader: BILLBOARD_PARTICLES / MAIN_CAM_INV_VIEW_MATRIX；material.cpp L1249: particle_frame = floor(INSTANCE_CUSTOM.z * particle_total_frames); clamp vs mod; atlas UV；ParticleProcessMaterial::_update_shader: CUSTOM.z = params.animation_offset + lifetime_percent * params.animation_speed；material.cpp: BLEND_MODE_PREMULT_ALPHA and particle animation properties；同 tag LICENSE.txt 与源码文件头 MIT。

**学什么：** 发射时随机相位、寿命进度和帧速度独立；图集的行列选择与循环/终止方式显式定义；billboard 只负责烟层朝向，主历史曲线继续保留三维结构。

**本轮如何应用：** 先用 Kenney 单帧变体构建烟层，固定 seed 的出生角度、旋转和相位；将来有合法连续序列时再接年龄驱动图集。需要帧间混合时自行添加相邻两帧采样，已读这段本身是 floor 选帧，并未实现平滑帧间混合。

**复制/素材许可：** Godot 引擎此 tag 文件头和 LICENSE 均 MIT；移植/改编代码保留许可。第三方纹理与 demo 素材不自动继承此结论。 [许可依据](https://github.com/godotengine/godot/blob/4.4.1-stable/LICENSE.txt)。

**核查边界：** 未运行 Godot；官方 stable 文档有些路径 404 或后续 403，因此核查以可访问的固定 tag 源码为准。不引入 Godot 引擎或人物/法器模型。

补充原始文件：[文件 1](https://github.com/godotengine/godot/blob/4.4.1-stable/scene/resources/particle_process_material.cpp)。

### 7. GPU Gems Chapter 6 · Fire in the Vulcan Demo · P0

作者：Hubert Nguyen / NVIDIA。[原始入口](https://developer.nvidia.com/gpugems/gpugems/part-i-natural-effects/chapter-6-fire-vulcan-demo)。

**实际阅读：** 6.1 procedural / 2D distortion / video-textured sprites 的取舍；6.2.1 自制 smoke generator 和 rolling motion；6.2.2 体量、位置、寿命、动画起帧和透明度变化；6.2.3 volume texture 的帧间插值；6.2.4 alpha blending 与加法发白问题；6.3 age-weighted emitter binding；6.5.1 Glow / 6.5.2 Heat Shimmer / Example 6-1；页面 Copyright / All rights reserved。

**学什么：** 火和烟不要全部加法；当每层都高强度相加时，色彩与内部层次会消失。变体、错相、尺度/消散差异能减少贴图重复。年轻粒子与发射器较紧，年老粒子脱离；该机制需与真正世界历史尾迹分工。

**本轮如何应用：** 赤焰用连续的方向性主火舌 + 较软 alpha 烟纱 + 少量高发射核；尾部有不同寿命和旋转速度。与人物绑定的局部新生层渐退，已经写入世界历史的尾迹不重新拖着走。将 Bloom 与背景 heat shimmer 分开开关。

**复制/素材许可：** 网页 Copyright © 2004 NVIDIA Corporation / All rights reserved；本次未核实配套 CD demo/素材的独立复制许可。仅概念阅读、链接和原创总结，不搬运视频火焰或示例 shader。

**核查边界：** 2004 的显卡限制和性能数字不能外推本次。章节把 3D 纹理用于时间帧，不等于三维密度体积；此区别在实现时必须保留。

### 8. Unity VFX Toolbox · Image Sequencer（条件性工具参考） · P2

作者：Unity Technologies。[原始入口](https://github.com/Unity-Technologies/VFXToolbox)。

**实际阅读：** README: Image Sequencer / retiming / loop / assemble flipbook sheets / reusable templates；LICENSE.md: Unity Companion License for Unity-dependent projects。

**学什么：** 序列重定时、循环拼接和图集组织是独立的制作工序；不是只把若干静态烟形按编号排列就得到自然动画。

**本轮如何应用：** 本轮只借鉴工作流程；需要序列时可用原创 Python/Pillow 重排自制或已授权帧，材质端读图集。

**复制/素材许可：** Unity Companion License，明确面向 Unity-dependent projects；不能写成 MIT，不能当无条件可移植到 Forge 的通用库。 [许可依据](https://unity.com/legal/licenses/unity-companion-license)。

**核查边界：** 未下载工具、未运行 Unity、未核查完整 Companion License 的全部情形；本项目不复制该库代码。

补充原始文件：[文件 1](https://raw.githubusercontent.com/Unity-Technologies/VFXToolbox/master/README.md)、[文件 2](https://raw.githubusercontent.com/Unity-Technologies/VFXToolbox/master/LICENSE.md)。

## 落到下一版的学习顺序

| 视觉缺陷 | 学习来源 | 具体实施方向 | 判断依据 |
|---|---|---|---|
| 头尾近乎等亮、密集线条像刷子 | Riot；NVIDIA 6.2.4 | 亮核/主枝/薄纱分值域和面积；烟用 alpha，核用发射 | 灰度下主轮廓清楚，尾端不会盖住头部 |
| 烟像硬质球块、缺少细腻气态边 | Kenney 两包；NVIDIA 6.2 | 多片软密度、不同尺度/旋转/寿命、低中高频侵蚀 | 无 Bloom 时边缘仍有密度结构；明暗背景都不显方片 |
| 动画是统一 UV 平移、循环有跳变 | Catlike FlowUVW | 两相采样＋互补权重＋局部时间错相，流向遵从主形 | 固定镜头可追踪卷动；周期交界不突然闪变 |
| 发光只是把网格加宽，缺电影感 | three.js UnrealBloomPass | 浮点发射、多级模糊、分层合成、输出转换 | 亮核仍细，近晕和远晕有跨度；关闭 Bloom 不丢主体 |
| 所有烟粒子同时长大/同一张脸 | Godot 材质源码；NVIDIA 6.2.2 | 固定 seed 的初始变体/相位/角度；年龄曲线分离 | 回放稳定，局部少重复；停止发射后自然消散 |

以上是学习支持与迭代方向，不替代参考视频的逐段比对。当前不把鸟、狐狸、舟船、短剑等具象模型计作“着色器形态”；如果参考以具象轮廓为主体，只提取其光、烟、流丝和转换的材质规律。

## 未读取、不可误报或有条件的内容

- Riot 原 2017 Style Guide 页面和 PDF 本次都跳到首页；这里只把仍可访问的 Riot《Clarity in League》记为已读。不能写成通读了旧 PDF。
- GitHub API 查询 dev 提交遇到限流；three.js 以已能读取的固定提交为依据，不声称“最新代码”。Godot 使用明确版本 tag，未声称 tag 已解析为提交 SHA。
- Unity VFX Toolbox 不是通用 MIT 资源库；其 README 指向 Unity Companion License，本轮不复制或移植代码。
- NVIDIA 页面可读不等于配套火焰视频/图/代码已获分发许可，本轮只做原创总结。
- 没有购买或下载商业 VFX 包，没有下载出处不明的烟火图集，没有把静态 PNG 列成真实连续 flipbook；没有运行 Unity/Godot 或验证 Forge 游戏效果。

机器可读索引：[v3-resource-study.json](lookdev-resources.json)。原有 14 组基础学习来源保持有效，本轮新增的美术/材质阅读重点补在这里，不用文档数量代替视觉验收。
