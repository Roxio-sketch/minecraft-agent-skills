# 奇幻物品材质：十三种奇幻内核与内部星空工坊

最新增加可调水波 `tidal-water / mode 12`，当前入口为 [水波工坊](http://127.0.0.1:55937/?preset=tidal-water&rev=water)，十三种奇幻材质与三种内部星空共用一个服务。参数、研究、真实 Forge 动态及跨面验收见 [水波交付](WATER_MATERIAL_DELIVERY.md)。下方保留此前交付记录，其中旧端口与哈希为历史值。

## 五种新增结构与动态（2026-09-30）

当前工坊：[打开材质库](http://127.0.0.1:63618/?family=fantasy&preset=clockwork-core)。同一独占服务提供12种奇幻材质与3种内部星空。浏览器中用户正在调整的页面已保留，未重置参数。下方其余章节保留先前研究与实测历史；其中旧端口和旧JAR哈希不代表本次运行。

| 新材质 / ID | 主体与动态 |
|---|---|
| 流光极幕 / aurora-veil | 立体发光幕面翻卷、纵向幕丝与幕边起伏、前后吸收 |
| 雷晶裂隙 / storm-prism | 固定晶片层、锯齿分叉电弧摆动、沿弧行进的电荷峰 |
| 琉璃泡海 / aether-bubbles | 空间泡格持续上浮、稳定泡体侧摆、壳面虹彩与轮廓高光 |
| 时轮机芯 / clockwork-core | 四层黄铜轮阵、相邻轮轴反向旋转、轮辐和铆钉运动、前轮遮挡 |
| 荧孢菌脉 / living-mycelium | 六层分枝丝脉张弛、沿枝传光、末端孢结呼吸 |

全部为独立原创算法，没有加入参考JAR的着色器源码。泡海是解析球壳和近似层合成，不读取世界背景折射；机芯是美术机械，不保证齿轮精确啮合。五种内核共用屏幕射线作为内部窗口，UV仅裁剪物品轮廓，不随物体旋转的局部实体体积。

六秒连续WebGL预览：[动态总览](E:/着色器渲染/skill-validation/styles-wave2-motion-final/motion-sheet.webp)、[五种0/2/4秒对照](E:/着色器渲染/skill-validation/styles-wave2-motion-final/motion-filmstrip.png)。各120帧、20次固定时间采样/秒，原始PNG与配方保留；不是游戏FPS或首尾无缝动画。资源摘要为 `1e059e3199494868b990fdee80a369c1cfd69c005db11213830c70e33ec6fec1`。

WebGL源版与安装版分别113项通过，见 `skill-validation/styles-wave2-wheel-final/validation.json`、`styles-wave2-installed-final/validation.json`。包括12种材质、真实16/32px、小图与主图、观察/种子/时间响应、遮罩、配方往返、暂停和180对零容差冻结比较。安装版统一工坊14项通过，资源包导出器8项通过。原Cosmic P3的67/87像素、最大1/255偶发差异仍未关闭，不因新增材质通过而改变。

Forge生产包与安装包SHA-256：`B94C3F4E452AF2129C2CB4B587399C2BB1BA5CFD9E4F34ED51429118D0B6143A`。在已授权ASTRA-TEST / ASTRA_VFX_TEST运行，Minecraft1.20.1、Forge47.4.10、Java17、RTX4060Ti。五种材质各10场景/21项普通回归全部通过；实际普通截图1280×720，包含剑/斧/弓/苹果/方块/护甲GUI图标、左右手、第三人称、满弓、掉落/展示框、shader重载。六载体时间变化均有效，同时间重载裁切全部零差异，玩家5项恢复均通过。仅认证这些普通baked模型样本；不扩展到BEWLR、穿戴护甲、骨骼模型或光影兼容。

| 材质 | 普通回归 fantasy-runs | 真实动态 motion-runs |
|---|---|---|
| aurora-veil | 20260930-123033 | 20260930-123332 |
| storm-prism | 20260930-123102 | 20260930-123355 |
| aether-bubbles | 20260930-123130 | 20260930-123418 |
| clockwork-core | 20260930-123158 | 20260930-123441 |
| living-mycelium | 20260930-123226 | 20260930-123504 |

上述目录都在 `D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/cosmic-material-lab/`。汇总报告为 `research/cosmic-material/styles-wave2/runtime-suite-verified.json`、`motion-suite-verified.json`。每种动态运行13项全部通过，固定方块与相机，冻结2秒→真实游戏时钟6秒→冻结2秒；实际2560×1421，各段前后冻结零差异。动态采样数分别60、50、53、51、44帧，播放按实际采样间隔；这些采样数不是游戏FPS。每目录都有 `live-motion.webp`、`live-sheet.png`、原生PNG和逐帧真实时钟。

实测中发现并修正两种边缘空区：第一版有限泡群和机芯在某些GUI位置只露静止底色，改为空间泡格和轮阵后，六载体动态通过。普通验收器按实际framebuffer及展示布局裁切，不再假定固定1280×720；测试驱动临时退出全屏再请求窗口尺寸，结束恢复全屏。此前854×480跨面跑次仅是低分证据，不叫作HD；旧失败报告保留。一次菌脉中间测试与并行重构建相撞，产物/安装哈希不同，因此留为失败；最终版本在产物固定后全部重跑。

新内核、12份配方、导出器、113项检查、可按ID列表录制的脚本、动画合成器和设计说明已同步到安装skill。无需用户手写GLSL参数；根据主体结构、输运路径、深度和遮挡将描述转成配方，超出已有结构时扩展独立内核。旧maxMode=6的JAR不能使用mode7–11；当前新生产包已部署到测试实例。

五种高分辨率跨面测试全部通过：实际2560×1421，每种两个冻结时刻各16种原生方块旋转，32案例/16682478个内部像素零差异，共160案例/83412390个内部像素。公共面边缘纳入测试，只排除外轮廓2px。目录依次为 `continuity-runs/20260930-123559`、`123625`、`123652`、`123720`、`123746`（后三个也带同日 `20260930-` 前缀）；完整路径在 `research/cosmic-material/styles-wave2/continuity-suite-verified.json`。该检查始终冻结，真实动态仍以单独motion-runs为证。

安装版与源版48个有效文件逐一SHA-256一致，清单 `research/cosmic-material/styles-wave2/skill-manifest.json`，技能结构校验通过。仅同步本次改动，保留已有描边及其他研究资源。工坊当前健康摘要与最终内核、配方及UI一致；长期服务仅监听127.0.0.1:63618，验证所用临时独占服务均由脚本清理。

可下载skill包：[minecraft-cosmic-material-styles-20260930.zip](E:/着色器渲染/deliverables/minecraft-cosmic-material-styles-20260930.zip)。包含上述48份有效资源及自动验证脚本，逐项解包哈希核对通过，不包含游戏存档、研究截图、参考JAR或生产mod。ZIP的SHA-256为 `8f28ba4e43e651089ed616d597eb9d226f0c46bbe1a45c00f4bc7b0a87ed078a`。

日期：2026-09-30（Asia/Shanghai）。本次修正 `fantasy-flow / mode 6 / 幻流云廊` 的逐面 UV 重复问题，并按用户要求将原有三种内部星空并入奇幻材质工坊。技术验证不代表用户已认可美术效果。

## 星空星点动态补充

用户指出原工坊只能看见星云移动。检查确认原星点坐标不含时间，微闪幅度又偏小。现有三个 Cosmic 预设新增独立 `starSpeed`、`twinkleRate`，增强 `twinkle` 的可见包络；四层星体差速漂移和缓转，稳定身份避免随机重排，逐星相位使闪烁错开。主画布去掉原20Hz人为上限，小图单独节流。新增参数参与导出、分组状态保留和冻结判断。

参考重新核对：StarShaderBlock 绑定的是 `star_sky`，其亮点来自移动/旋转程序分形采样域；包内 `starfield` 存在显式星体偏移与闪烁，但此次没有将它当作已确认的方块绑定。本次原创星格实现未改为参考分形，也未增加 Forge 新接入认证。

WebGL 检查：`skill-validation/star-motion/validation.json` 18项通过，包含关闭云尘后的三预设大图/16/32px动态、同一批星峰的连续轨迹、固定位置的亮度起伏、真实动画循环与暂停。`star-motion-workshop/validation.json` 14项通过，包含新增控制导出、分组状态和桌面/手机截图。两份报告在补充暂停时三画布同步绘制前生成，各自保留实际资源哈希；该补丁不改内核或配方。

最终完整回归 `star-motion-preview/validation.json` 为22/23项通过、整体failed。新增暂停时钟同步与实际暂停零差异通过；180对冻结比较有1对失败：首轮深空星海800×540出现87个像素、88个通道、最大1/255差异，实际三个uTime均为0、DITHER均关闭。保存 `freeze-before.png`、`freeze-after.png`、`freeze-failure.json`，未把低幅差异接受为通过。此前一次22项全部通过不覆盖这次复现，P3根因仍未确认。

连续证据在 `skill-validation/star-motion/`：`drift.webp`、`twinkle.webp`、`combined.webp`、三栏 `comparison.webp` 和 `filmstrip.png`。每段120帧、800×540、6秒，20 samples/s按材质时间播放；原始PNG与逐帧轨迹保留。它们是固定时间WebGL片段，不是Forge录像、无缝循环或实时FPS。录制验收与合成器随skill提供，不依赖研究目录。

源skill与安装skill的42个有效交付文件逐一哈希一致，新增两份星点脚本和对应制作说明；结构校验通过。使用安装版服务身份验证器核对当前55563端口的8个资源文件摘要一致，未创建第二个长期预览服务。

## 统一工坊

当前地址 `http://127.0.0.1:55563/?family=cosmic`，启动记录 `research/cosmic-material/fantasy/combined-preview-service.jsonl`。同一服务、一个端口，两个材质分组：七种奇幻材质，以及原来的深空星海、紫蓝星云、赤金裂隙。星空直接使用 authoring-lab 的原始 GLSL、配方和控制项，保留星点/星云/暗尘、16/32px、观察响应与导出。分组切换保留编辑状态，并暂停隐藏分组的时钟。

原用户标签页有尚未导出的自定义幻流参数，因此保留原页，另开更新页；未强行刷新丢失状态。星空仍导出 `cosmic-authoring-preview/v1`，不等于 fantasy 资源包格式，也没有因 UI 合并而新增 Forge 接入认证。

`skill-validation/combined-workshop/validation.json`：13项通过，含三种旧预设动画、GL状态、参数/图层修改后的状态保留、原格式导出、1440px桌面和390px手机布局。截图同目录。统一工坊中的七种奇幻内核回归73项通过，记录 `skill-validation/combined-fantasy/validation.json`。

## 跨面修正

此前开口来自每面独立的 atlas UV，导致六个面复制。新 mode 6 由 framebuffer 像素坐标构造共享射线，单一观察点向三维场积分；世界上下文加入相机 yaw/pitch，GUI 固定虚拟相机。UV 仅用于 alpha 和覆盖遮罩。积分函数与浏览器制作台共用，独立窗口入口是 `fmFantasyWindow`。mode 0–5 的旧坐标路径没有获得这次连续性认证。

固定时间和相机旋转方块时，内部场保持原位，方块轮廓变化。这符合 FantasyBlock 的窗口机制；它不是随物体旋转的局部实体体积，也未重建 Minecraft 动态 FOV。

Forge 专项最终跑次 `continuity-runs/20260930-030546`：2560×1421，两个时刻各16种旋转，16682478个内部像素零差异。完整窗口与原生 baked cube 的内部像素逐一比较，包含公共面边缘，仅去掉外轮廓2px。验证脚本随skill提供 `scripts/validate_flow_continuity.py`，生成动画、接触图和零容差报告。

中间跑次 `20260930-025051` 在2560×1440存在每案例1–4个像素、最大1/255的偏差，报告保留为失败。随后完整覆盖时直接使用 mapped 颜色，绕过含面相关底色的 mix，并跳过恒等overlay混合；新高分辨率跑次零差异。窗口管理器将请求1440的高度限制为1421，记录实际尺寸，不宣传为同一分辨率逐像素A/B。原Cosmic P3历史问题没有因此关闭。

## 高分辨率连续动态

上面的跨面测试始终冻结时间，不构成连续动态证据。用户追问后，新增 `flow-motion-hd` 真实游戏时钟录制，固定原生方块及相机，冻结2秒、实时运行6秒、再冻结2秒。没有用人工递增时间来合成游戏实录。

最终证据：`D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/cosmic-material-lab/motion-runs/20260930-032132`。实际framebuffer为2560×1440，共84张原始PNG，其中动态49张；采样覆盖5.8135523秒，GPU的fmTime由23664.348递增至23670.162秒。动态每个相邻样本的内部像素变化均通过，两个冻结段逐像素零差异，13项检查通过，窗口与时间控制已恢复。

`live-motion.webp` 按每帧真实采样间隔播放，缩小为960px展示；原始PNG保留完整分辨率。中位采样间隔0.11745秒，最大0.22378秒，约8次/秒的录制不代表游戏FPS。第一版同步PNG写入约249ms/帧，已改为两个后台编码线程、最多4张待写图像；GPU读回和后台CPU负载仍影响采样，不作为性能基准。它验证有真实动态，不替用户判定美术是否达到FantasyBlock。

动态验证入口为随包 `scripts/validate_flow_motion.py`。当前测试JAR增加了录制入口，渲染内核未因录制修改；跨面32案例的历史JAR是19EC开头版本，新的动态与普通回归使用下述23A5开头版本，各validation.json分别记录实际哈希。

## 变化与依据

核对 `FantasyBlockEntityRender` 实际绑定 `chaos / useType=1`。公共时钟返回seconds/20，renderer乘100，因此默认真实秒对应5个uniform单位。使用该倍率录制用户提供片元的本地学习对照。

原创内核使用共享虚拟视点、发散射线、双侧云壁、六级三维剪切形变、沿深度输运、接近辉光和吸收。预览与Forge复制同一份GLSL和配方。原参考shader不随skill/JAR分发。此方案是程序化三维场，不是物理流体或第二个Minecraft世界。

## 可查看结果

- 旧单组服务记录为 `flow-preview-service.jsonl`；新统一服务记录见上。没有热重载，旧页有编辑时应先导出或保留旧页。
- 连续参考对比：`research/cosmic-material/fantasy/internal-flow-final/comparison.webp`。左侧为原片元经WebGL版本/精度与合成宿主改编，右侧为原创内核。6秒，20 samples/s，非无缝循环、非实时性能测试。
- 同目录 `filmstrip.png`、`comparison.json` 保留0/2/4秒、时间映射和配方。参考 yaw=0.25、pitch=0.15；原创默认视角，两者不作逐像素等价比较。
- 随包录制器生成的独立原创动画：`research/cosmic-material/fantasy/bundled-flow-motion/motion-sheet.webp`。
- 最新游戏截图：`D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/cosmic-material-lab/fantasy-runs/20260930-032312/`。最终JAR的10场景/21项检查通过，玩家5项恢复均为true。旧 `20260930-015445` 的UV适配不作为跨面证据。

## 技术验证

- WebGL：`research/cosmic-material/fantasy/browser/internal-flow-final/validation.json`，73项通过，含七模式、16/32px、暂停、导入导出和180次零容差冻结比较。
- Forge：1.20.1 / 47.4.10 / Java17，启动器进入已授权 ASTRA_VFX_TEST，10场景/21项通过。GUI剑、斧、弓、苹果、方块、护甲物品图标；第一/三人称、左右手、满弓、掉落、展示框与重载。
- 新内核所有案例采样GL error=0，满弓41 ticks；六处固定时间重载裁切均0像素差异。玩家恢复记录全部通过。
- 最新生产与安装JAR SHA-256相同：`23A54B1E614A902FF4551FE9CB1556D97355EE9276F3334BFC46493CBAC19369`。
- 原先六模式最终版另有 `fantasy-runs/20260930-012242`，不能把它作为新内核的证据。
- 旧安装版独立73项证据是 `skill-validation/fantasy-flow-installed/validation.json`；本次源工坊73项和统一分组13项记录见上。独占服务6项再次通过，继续保持端口冲突时报错的约束。
- 本次安装后的skill独立启动验证13项通过，见 `skill-validation/combined-installed/validation.json`；新增动态录制验证脚本后共39个交付文件逐一对齐哈希。包含 `validate_preview.cjs`、`validate_fantasy.cjs`、`validate_workshop.cjs`、`validate_flow_continuity.py` 和 `validate_flow_motion.py`，不是仅保留在研究目录。技能结构校验通过。
- 中间构建曾因 float uniform 调用整数重载崩溃，已改为显式float并重新部署。`20260930-023125` 没有完成报告，不能当作通过。该跑次的NBT备份恢复记录 `recovery.json` 确认背包、选中槽、模式、位置恢复；精确位置与材质筛选未找到残留测试实体。后续完整跑次的5项恢复均通过。

## Skill 和边界

源skill：`skill/minecraft-cosmic-material`；安装目标：`C:/Users/MAINTAINER/.codex/skills/minecraft-cosmic-material`。内核、七模式配方、预览、导出器、冻结验证、动画录制与合成脚本都在skill内。`references/internal-flow.md`记录坐标、时间、从自然语言改变结构的规则与验收方法。

Forge mode 6已改为共享屏幕窗口并验证跨面连续；其余六模式仍是primary-sprite开口。物体局部体积、多sprite遮罩、BEWLR、穿戴护甲、骨骼模型、shaderpack和批量物品性能未认证。16px物品只占共享场很小的视角，不承诺与大画布完全相同的细节。

原Cosmic P3的偶发67/87像素、1/255差异仍未定位，不能因新内核零差异就标记其已解决。服务保持独占动态端口，不停止不相关服务。
