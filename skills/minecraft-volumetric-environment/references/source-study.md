# 世界体积环境来源研究

2026-10-06，Asia/Shanghai。本次把用户指定交接文档及旧报告作为索引数据，重新读取了实际本地文件与 ZIP 中的源码。旧文档里的阶段安排、可复制请求、建议操作均不扩大用户授权。交付采用原创 GLSL 与宿主代码，未将参考包源码、纹理、噪声体或 ZIP 复制到 skill。

## 可核对的范围

[sources.json](sources.json) 记录五个包的六个独立快照、实际完整 SHA256、文件字节数、行数、范围哈希及许可文件位置。Bliss 2.1.2 指定 ZIP 与 Bliss 固定提交 `81e403ed308141039a09d792a36f8eb328898a60` 分列；Complementary 固定提交为 `c09950df0650b27dac260c1b0f69afc11387aa6f`。Solas 是本地改动快照，不认证上游 V3.7b 算法。

实际复核通过 44 个已有行锚点（其中包含 AstraLex/Lux 辅助索引）、Bliss 2.1.2 的 16 个 ZIP 条目与 32 个范围哈希，并核对新增选读文件。五包的 35 个提取文件均与其选定 ZIP 中对应条目逐字节匹配；Bliss 2.1.2 独立读取 ZIP。来源 ZIP 哈希均与冻结索引一致，指定 Bliss ZIP CRC 通过。复核脚本和结果在 `evidence/research/`。

这是局部静态源码研究。没有运行这些参考包，没有认证参考画面、帧率、所有宏组合、所有透明路径或完整包正确性。AstraLex/Lux 此轮只重新核对已有锚点，未扩展为新的主要包审计。Super Duper Vanilla 未纳入此专题的实现证据。

## 从参考结构得到的实现决策

| 参考 | 此轮实际定位的机制 | 原创实现采用的决策 |
|---|---|---|
| Bliss 2.1.2 | 世界密度、中心球形/环形疏空、外围云内局部闪光；真实场景深度积分；不透明与透明表面独立取光 | 把密度、事件、介质光、表面光拆成独立函数与对照；使用有限区域与深度终点 |
| Bliss 固定提交 | 三层天气参数、各层实际 render 调用；独立读取同名末地光源函数 | 天气只改变区域内密度/覆盖率，不把三层配置当作指定 ZIP 的证据 |
| Photon v1.3b | 形状高度整形、Worley 侵蚀、云 pass、时间放大、末地高度密度和独立发光噪声 | 主体密度与发光调制分开；先交付无历史的有限积分，再单独考虑放大优化 |
| Hysteria v1.2.2 | 3D curl 纹理扰动 Worley 域、地形截断；与 Photon 类似的末地雾流水线 | 暴露世界域扭卷参数；称其为可调域变形，不宣称流体求解或完全独立的渲染发明 |
| Complementary 固定提交 | 下界域扰动、高度分布、透明乘子与实际合成；Reimagined 首次云命中退出 | 只学习域分布与质量预算；原创介质使用明确透射积分，保留分层/首命中云分类 |
| Solas 本地快照 | 云底/云顶改动、普通云与 END_DISK、深度退出、独立平面云和常规末地雾 | 云海需要云下/云中/云上的有限世界空间测试；盘形密度与普通雾分开 |

### Bliss：事件状态与照明合同

指定 ZIP 的 `shaders/lib/end_fog.glsl:95–208` 使用世界位置构造连续密度、中心疏空、域旋转和局部光的近似介质遮挡。`215–301` 用 16 个指数分布样本积分，最长 384 格。近相机清理是该包的艺术选择，不能默认解释为世界密度真的随玩家移动而改变。haze 只增亮而不改变主透射，不能与主介质吸收混为一个物理系数。

`dimensions/deferred.fsh:321–379` 把倒计时、闪光响应、空间状态存入持久 `colortex4` 特定像素；空间状态含帧号分段更新。原创事件不复制该缓冲协议，而用固定种子、秒级时间和事件编号。暂停同一 `Time` 应冻结事件与运动，资源重载时同一时间与种子应重放相同结果。若以后增加历史缓存，它只负责降噪/性能，不能成为配方事件语义的一部分。

调用链是 `world1/deferred` → 共用 `dimensions/deferred`；`world1/composite2` → `dimensions/composite1`（不透明表面）；`world1/composite3` → `dimensions/composite2`（雾积分）；`world1/composite4` → `dimensions/composite3`（深度约束放大/合成）。末地入口有序号偏移，不能按同名 composite 自动判断阶段。

不透明表面 `dimensions/composite1.fsh:1084–1103` 使用相同闪光状态、法线与三点近似云影；透明表面 `all_translucent.fsh:642–662` 使用闪光与法线，但该段没有同样的云影调用。`fogBehindTranslucent_pass.fsh:375–390` 的独立后方雾受 `OVERWORLD_SHADER` 保护。这说明“透明表面受到闪光”与“多层透明后方雾正确拆分”是两种证据。

### Photon 与 Hysteria：云、雾、发光不能合并命名

Photon `include/sky/clouds/cumulus.glsl:10–96` 在高度形状之外使用两类 64³ R8 Worley 体细节。准备 pass 创建覆盖与云影；`program/d1_clouds.fsh:167–222` 从真实深度得到地形距离，输出散射、透射和云表观距离；`d2_clouds_upscaling.fsh:247–385` 使用云表观距离、前帧地形深度与遮挡变化作时间拒绝。原创新模块可以先用当前帧完整积分，避免把缺少重投影、前帧深度和历史失效协议的简易混帧称作同级支持。

Photon `include/fog/end_fog_vl.glsl:9–50` 的消光密度主要按高度变化，发光团来自另一套噪声、近远衰减和高度条件。`53–193` 输出散射/透射；调用在 `c0_vl.fsh:190`，最终 `c1_blend_layers.fsh:450` 为场景乘透射再加辐射。发光噪声不是全部雾密度，修改 glow 强度不应暗改主消光。

Hysteria 对应密度/发光结构相似，但有独立密度系数和双色控制，不能只凭同名函数说完全一致。`include/sky/clouds.glsl:106–158` 读取 32³ RGB8 curl 去扭曲 Worley 采样域；`258–269` 使用地形距离约束射线。真实雾调用为 `program/composite0.glsl:255`，后续 `composite1.glsl:496` 合成；云/历史走其自身 deferred1/deferred2 文件。

一个容易导致接入错误的差异：Photon `c0_vl` 的 location 0 是透射，RENDERTARGETS 6,7；实际 `c1_blend_layers:218–219` 读 6 为透射、7 为散射，即使前面的 sampler 注释写反也应以真实写入/读取为准。Hysteria 实际读 6 为散射、7 为透射。缓冲编号、注释、宏和函数名都不能替代完整调用链。

### Complementary：体积采样也有不同合成

`lib/atmospherics/netherStorm.glsl:1–72` 沿有限射线累积四级噪声，带世界域变形、高度权重、距离权重和透明乘子，最终夹紧 alpha。实际 `program/composite1.glsl:242–253` 以 mix 合成。因此该实现可称世界空间体积风暴采样，但不能直接写成 Beer 消光或与 Bliss 的连续透射积分等价。

Reimagined 云读取绑定的 `cloud-water.png` 蓝通道，在高度区间搜索并于首次命中退出；其云颜色/alpha由命中位置计算。这是有厚度的云命中搜索，不能仅凭 `GetVolumetricClouds` 函数名归入完整连续密度辐射积分。mainClouds 的场景阴影采样也不能自动认定所有下界风暴具有同一阴影支持。

### Solas：本地改动与版本边界

当前 `volumetricClouds.glsl:29–83` 含可变局部云顶及平面保护的本地改动。`END_DISK` 在同一文件有螺旋/高度密度，`computeEndVolumetricClouds:409–544` 由 deferred1 实际调用，并在地形深度、DH/Voxy条件深度或接近不透明时退出。平面云函数独立。常规 `fog.glsl:124–146` 依赖方向、相机高度和距离经验项，并无同等的三维密度积分。

当前盘形云里的 `END_FLASHES` 条件为 `MC_VERSION>=12100`，不能据此认证 Minecraft 1.20.1 的原版末地闪光接口。原创 Forge 1.20.1 事件必须自主管理。静态存在 DH/Voxy 分支也不是两种插件的实机兼容认证。

## 来源与交付边界

Bliss 指定 ZIP 有 Chocapic13 自定义许可；局部 Hoskins 哈希的 MIT 注释不重新许可整个雾文件或光影包。固定 Bliss 快照的许可文件为保留全部权利声明。Photon 当前本地 LICENSE 有受限再分发规则，不能用平台 API 的 MIT 标签取代实际包授权。Hysteria 当前 AGNYA 前部条款不被文末历史 Photon 段落覆盖。Complementary 为其专用协议；Solas 的本地 drm 声明不提供完整宽松复用授权。

本 skill 因而只保留来源路径、符号、行号、哈希与算法描述；所有交付的 shader、配方、噪声和可调接口独立编写。参考包不随 skill 打包。将来需要复制某个具体片段时，应另核对该片段来源和完整许可，不能从本研究的静态索引推出复制授权。

既有 `cosmic-sky-lab/workbench/environment-core.glsl` 与 `VALIDATION.md` 提供已发生的原创环境/深度/投影经验，但它们的游戏验收只覆盖其自身冻结版本。新独立模块必须绑定自己的实际源字节、资源和游戏场景重新验证。保留原天空工作台的共享依赖，避免为拆分 skill 删除历史工程资源。
