# 五行仙途：境界御空 / 流星遁光现有实现基线

2026-10-15，Asia/Shanghai。宿主工程 `E:/五行仙途`，仅静态读取源码、说明、既有报告和截图；本轮没有启动游戏、构建、重装或改动宿主。用户将这套效果视为本机已有成熟实现，本记录把它纳入新 Skill 的可学习基线；“现有实现”与“本次重新实机认证”分别记述。源码与资源不复制进 Skill。42 份源文件的完整 SHA-256、既有报告摘要及截图身份见 [wuxing-realm-evidence.json](E:/着色器渲染/research/dunguang-flight-20261015/wuxing-realm-evidence.json)。未发现宿主根目录或所查源码/文档树中的 AGENTS.md，沿用用户本轮给出的废弃文件规则；本轮无清理操作。

## 可以直接学习的工程结构

这套系统的高级境界不是“给人套一个独立球，再接相机朝向的尾带”。当前实际路径是：服务器同步御空视觉状态 → 客户端每玩家固定 tick 记录世界中心线 → 在中心线的运输框架上形成三维收束截面 → 同一截面向前延伸并包住人物 → 程序流纹、短时弯道残雾和火星 → 世界深度约束的半透明绘制及独立 Bloom。元婴仍走较轻的条带尾迹与独立解析头部路线。

| 职责 | 当前源码入口与精确定位 | Skill 可提取的原则 |
|---|---|---|
| 状态权威与同步 | [RealmFlightTracking.java:19](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmFlightTracking.java:19)，`active`；`:27` 服务端 END tick，状态变化才向 tracking+self 广播；`:34` 开始跟踪时补状态，`:38`–`:41` 登出/重生/维度/停服清理 | 视觉跟随真实飞行状态，不改移动权限；本地显示与远端显示都需独立 UUID 状态。 |
| 客户端实例 | [RealmFlightEffects.java:25](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:25)，最多32个 `Flight`；`:31` 每 UUID 的 `RealmMeteorPath`、方向、相位、强度与长度快照；`:81` `accept` | 32 是容量上限，不是本轮32人性能验收。满额优先保留本机及近处状态。 |
| 固定采样与插值 | [RealmFlightEffects.java:110](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:110)，END tick；`:120` 记录玩家身体中点；`:175` `clock-1+partialTick` | 游戏刻写状态，渲染帧只插值；不能按显示帧数追加路径或生成火星。 |
| 世界历史 | [RealmMeteorPath.java:9](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmMeteorPath.java:9)，64样本；`:45` `sample`；`:85` `renderAdaptive`；`:115` 沿平滑几何重算弧长；`:142` 从储存运输锚点恢复截面 | 尾迹留在走过的世界轨迹，不把整根尾巴绑到当前朝向。相机转动不应改变截面相位。 |
| 身体与尾根衔接 | [RealmFlightEffects.java:331](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:331)，`drawAdvancedTrail`；`:338` `RealmMeteorJoin.attachHead`；[RealmMeteorJoin.java:72](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmMeteorJoin.java:72) | 将末段历史逐渐转到滤波航向，再延伸前端；不要只替换一个身体环，产生拼接处翻转。 |
| 急弯宽度限制 | [RealmMeteorJoin.java:25](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmMeteorJoin.java:25)，`RadiusSupport`；[RealmFlightEffects.java:352](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:352) | 以稳定的世界距离支撑限制曲率半径，降低局部内折。源码本身不承诺分离两条远处互相穿越的历史路径。 |
| 局部形状与境界差别 | [RealmMeteorFlow.java:17](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmMeteorFlow.java:17)，4格后逐渐出现细节；`:23` `width`；`:67` `section`；`:125` `residualWeight` | 头/身体附近共享轮廓，4–10格后再引入不同卷曲、片层、弯道伸展；细节不能在头尾交界突然切换。 |
| 人物动作 | [RealmFlightVisuals.java:95](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightVisuals.java:95)，`forwardShellWeight`；`:112` 骨盆根旋转；`:127` 模型姿势；[FlightVisualDynamics.java:7](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/FlightVisualDynamics.java:7) | 姿态、前端包裹权重和轨迹方向联动；保留持物/使用动作。 |

## 当前视觉构成与境界参数

当前 [RealmFlightEffects.java:235](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:235) 明确说明高级境界由流动尾迹自身包裹；独立 `MeteorHeadRenderer.create` 只在 `active && !advanced && !firstPerson` 时调用。高级主尾只提交 `layer=1,2`（`:366`），元婴只提交 `layer=2,1`（`:297`）；中央细长箭矢层0已经取消。不是当前每帧都绘制旧文档所述的三层完整管加独立球头。

[RealmFlightAppearance.java:5](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmFlightAppearance.java:5) 的 `Profile` 与 `profile(realm)` 是当前配置依据；[RealmFlightSelection.java:11](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmFlightSelection.java:11) 限制只能选已解锁境界的外观。外观境界与服务器决定的移动境界分开：选择低阶外观不会把速度改为低阶。

| 境界 / style | 当前基础色 | 设计普通 / 加速尾长（格） | 配置半径 | 实际形状层次 |
|---|---|---:|---:|---|
| 元婴 3 / 0 | `#59DCFF` | 18 / 30 | 0.85 | 较轻的条带尾迹、独立解析头部、近景少量火星。 |
| 化神 4 / 1 | `#65D9F2` | 28 / 44 | 1.38 | 共用头身包裹＋基础连续三维流纹。 |
| 炼虚 5 / 2 | `#A78BFA` | 32 / 50 | 1.43 | 尾部内卷截面与卷曲流纹。 |
| 合体 6 / 3 | `#FFD278` | 36 / 56 | 1.48 | 尾部片层/成束合并变化。 |
| 大乘 7 / 4 | `#DC9CFF` | 40 / 62 | 1.53 | 弯道内外侧差异与短时外侧残雾。 |
| 地仙 8 / 5 | `#7CF4DE` | 44 / 68 | 1.58 | 大尺度低频卷曲、片层与较快细流并存。 |

表中半径是外观档位，不能理解为所有境界都有一个该半径的独立球。当前前端共用化神参考半径；`headRadius`/`headLength` 由 [RealmMeteorTuning.java:17](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmMeteorTuning.java:17) 根据速度计算，尾部再逐渐过渡到等级半径。实际长度还受真实速度、平滑滤波和已积累历史限制，不会凭空填满68格。鬼仙未手动选外观时有 `#BC9EFF` 覆盖色，见 [RealmFlightEffects.java:203](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:203)。

当前人物模型俯仰目标为70/78/85度（正常/快速/冲刺），即水平飞行参考下上仰约20/12/5度；臂展15→7度，常规侧倾上限35度、高速45度，见 [FlightVisualDynamics.java:7](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/FlightVisualDynamics.java:7)。不能把旧说明83/88/90度当作当前常量。

## 生命周期与坐标空间

- **开始**：服务端有效状态进入 `Flight`；前端/长度用0.30秒起步、0.35秒结束、0.25秒长度滤波；速度和转向另用0.15秒滤波，见 [RealmFlightEffects.java:123](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:123) 与 [RealmMeteorTuning.java:12](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmMeteorTuning.java:12)。没有新增静止悬浮光环的要求。
- **飞行**：身体中心为采样挂点；每 tick 更新世界位置、速度、运输方向。流动相位按 tick 积分 `flowPhase += speedWeight * .125`，渲染只插值前后相位，避免直接把总时间乘当前速度造成纹理跳动（Effects`:131`）。
- **转向**：历史仍沿旧路，头部转向只通过接合区渐变；`RealmMeteorFlow.section` 改变同一中心线周围的截面。大乘/地仙的残雾只在3–20 tick旧的弯曲历史上出现，不是第二根长尾（Effects`:388`、Flow`:125`）。
- **停下/退出**：历史最大年龄为高级60 tick，容器上限64 tick；但停下还有18 tick全局 `stopOpacity`，即约0.9秒淡尽，不能只照参数“3秒”承诺停止后始终停留3秒。见 [RealmMeteorPath.java:11](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/RealmMeteorPath.java:11)、`:59`、`:93`。停止的历史固定在世界，不跟随随后步行的玩家（`:106`）。
- **异常/卸载**：非有限位置/时间、时间倒退、单步超过4格或采样间隔超过64 tick会清空路径（Path`:45`–`:53`）；换客户端世界清全部状态/待绘制缓冲（Effects`:72`），离开实体清 UUID（`:101`）；缺失玩家超过20 tick移除（`:119`）；关闭特效清历史并释放缓冲（`:112`）。
- **容量与细节**：近/中/远距离32/64/128格，径向8/6/4段；112–128格统一淡出（Effects`:197`）。火星以整数出生 tick 与 seed 决定，年龄2–24 tick、离体较短，避免按渲染帧生粒子（Effects`:410`）。这是一组资源上界，不是性能测试结果。

## 两条世界渲染路径与发光

[RealmFlightEffects.java:157](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:157) 在 `AFTER_PARTICLES` 收集几何；普通路径使用半透明、无剔除、`LEQUAL`、仅写颜色的 RenderType（`:67`），不会把半透明尾焰写入世界深度。

检测到外部光影包、强制post或等待绘制的雷电效果时，几何缓存为后绘制路径（`:166`），采用四边形排序，再在 GUI 前提交；隐藏HUD则由RenderTick END提交（`:140`–`:154`）。先执行雷电的前景刷新，再画尾迹/元婴头部/发光，防止前景顺序互相污染。

[TrailShaderBridge.java:91](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/TrailShaderBridge.java:91) 捕获当前世界深度；`realm_meteor_post.fsh` [第295行](E:/五行仙途/src/main/resources/assets/wuxingxiantu/shaders/core/realm_meteor_post.fsh:295) 根据真实场景深度裁剪。Native与post的材质函数本轮逐字比较一致（从 `WHITE_CORE` 到 `void main` 之前），差别为宿主/深度与发射输出；完整文件哈希当然不同。

高级材质由 `commonFlow`、`curledFlow`、`layeredFlow`、`turnFlow`、`tubeTrail` 等组成，见 [realm_meteor.fsh:141](E:/五行仙途/src/main/resources/assets/wuxingxiantu/shaders/core/realm_meteor.fsh:141)。它用弧长与角向程序场在同一中心线上生成流纹；角度用sin/cos构成圆周坐标；头部预算限制重合层alpha与亮度，掠射边缘羽化，明确不加会形成玻璃环的Fresnel亮边（`:226`–`:260`）。这是程序表面/截面效果，不是流体求解器或大规模体积烟模拟。

真正的外扩亮晕由 [MeteorBloomRenderer.java:50](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/MeteorBloomRenderer.java:50) 建立RGBA32F发光/乒乓缓冲，半分辨率，alpha储存线性视深度，`:68` 按选中几何输出发光，`:98` 两轮横/纵模糊，`:107` RGB加法合成，保持场景alpha。[realm_meteor_bloom.fsh:23](E:/五行仙途/src/main/resources/assets/wuxingxiantu/shaders/core/realm_meteor_bloom.fsh:23) 再做目标深度约束；GL状态由 `MeteorBloomRenderer.State` 保存与恢复（`:126`）。不能把普通alpha尾纹称为Bloom，也不能仅凭半分辨率推断帧率收益。

## 第一人称：当前源码与已有截图分别记录

当前有两个不同空间的机制：

1. **视野四周流气**：[FirstPersonFlightFlow.java:28](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/FirstPersonFlightFlow.java:28) 固定tick更新，使用当前视图边缘的4个互不重叠矩形条、16顶点，中央矩形不提交（`:80`）。仅本机第一人称、无其他UI、非骑乘、两个客户端开关开启且正在有效御空时绘制。颜色取所选境界；移动/冲刺强度与相位由 [FlightFlowMotion.java:8](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/FlightFlowMotion.java:8) 连续更新；启用/维持阈值分别0.025/0.015格/tick，静止短时淡出。其方向直接跟随画面，不读取世界历史；御剑骑乘不会调用这一境界覆盖层。
2. **侧看/回看时选取旧世界尾迹**：[RealmFlightEffects.java:188](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightEffects.java:188) 使用实际渲染视角，`FirstPersonRealmTrail.viewWeight` 正看航向时归零、偏离航向后恢复旧历史；`:212` 去掉贴脸头身段、近眼段与中央视线锥。数学依据为 [FirstPersonRealmTrail.java:8](E:/五行仙途/src/main/java/cn/blockforge/generated/generatedmod/realmflight/FirstPersonRealmTrail.java:8)。自身独立元婴头部始终不画；高级前包裹 `shellWeight=0`，只可能看较旧历史。

10/14第一人称稳定性报告的正视截图记录 `renderedVertices=0`、`firstPersonFlowVertices=16`；它证明当时四周流气路径的画面/状态检查，不证明本轮当前侧看/回看分支已实测。本轮只读分析没有补跑新截图。

## 本轮亲眼查看的已有实机证据

| 日期与原图 | 实际可见内容 | 适用范围 |
|---|---|---|
| 2026-10-14，[化神侧视](E:/五行仙途/reports/flight-hover-flow-20261014/third-person-final/realm-4-huashen-local-night-side.png) | 人物水平前伸、仍可看清；青蓝透明包裹绕前端接入向左延伸的宽长弯尾。 | 原生客户端既有图；不能单帧证明流纹运动、全长或急弯无缝。 |
| 同日，[炼虚侧视](E:/五行仙途/reports/flight-hover-flow-20261014/third-person-final/realm-5-lianxu-local-night-side.png) | 相似身体/尾迹连接，外壳与宽尾呈浅紫，人物清楚。 | 支持实际境界颜色差异，非旧说明“高级全青蓝”。 |
| 同日，[地仙前视](E:/五行仙途/reports/flight-hover-flow-20261014/third-person-final/realm-8-dixian-local-night-front.png) | 前视人物外露，周围亮青色多边截面光壳。 | 该视角只展示截面；不以它判断尾长、完整尾部卷曲或其他境界。 |
| 同日，[第一人称化神正视](E:/五行仙途/reports/flight-flow-stability-20261014/verified-realm-ui/realm-4-huashen-local-day-front.png) | 画面边缘与四角有青色曲线气流，中部世界可读，没有本地第三人称人物。 | 支持视野覆盖层的外观；命名的“front”不等于第三人称前视，JSON中camera为FIRST_PERSON。 |

既有报告 [third-person-final/result.json](E:/五行仙途/reports/flight-hover-flow-20261014/third-person-final/result.json) 声称12图/334检查、PASS、无GL错误，原生渲染，化神/炼虚/地仙昼夜；`scope`说明独立生存世界真实本地双击/W输入，侧图用了DEV相机偏置，没有远端玩家或多人/FPS主张。[verified-realm-ui/result.json](E:/五行仙途/reports/flight-flow-stability-20261014/verified-realm-ui/result.json) 声称12图/2321检查、PASS、无GL错误，包括连续转向/升降/冲刺/减速的相位连续；[调整说明](E:/五行仙途/reports/flight-flow-stability-20261014/调整说明.md) 明确未启用外部光影包、未测GPU耗时。

10/09曾有原生/Oculus关闭/Solas三个环境报告；它们作为已有历史测试记录保存。本轮将42份当前源文件与10/09 reference-native-final的tested-source-hashes比较，8份相同、21份不同、13份未收录；与后续flow-native-final为15/15/12，与10/10 front-soft-native为17/15/10。因此旧报告不能当作当前源码同源认证，完整对照在证据JSON。

## 说明文档漂移与 Skill 使用方式

`流星御空说明.md` 首注已经标记“长尾优化候选”；下方正文仍混有旧版球头、12/22至16/28格尾长、全青蓝递进、7度上仰、不画自身第一人称等表述。新 Skill 引用时应优先读取当前 `RealmFlightAppearance`、`RealmMeteorTuning`、`RealmFlightEffects`、`FlightVisualDynamics` 与当前第一人称分支；旧表只作为演进背景。

新 Skill 应把“本机五行仙途境界流星基线”列为一个现有工程案例，按入口符号与哈希定位供按需阅读，不打包宿主源码/资产。不把它固化为自由遁光的唯一形状；其可复用经验是历史坐标、头尾同源接合、tick相位、视角分层、半透明排序、真深度与选定发光缓冲。视频中红黑烟遁、光针、羽丝、硬边锥尾仍可选择不同几何和材质。

本轮未建立当前源码→安装JAR→新运行截图的绑定，也未测LAN/独服/多玩家压力、GPU耗时、全部光影/第三方装备组合。无需因此否定用户对既有系统的成熟判断；只应准确标注本轮新增的证据层级。

## 核心源码身份（完整SHA-256）

| 文件 | SHA-256 |
|---|---|
| RealmFlightEffects.java | `68d853d25fe3a3bed5c9994d5bafe601c7b04b781bd4fd417b36123d7135cd9c` |
| RealmFlightAppearance.java | `f1958d78686a4c71ffa14c762a64c4c972bb722ac0760bea1476c789367424e5` |
| RealmMeteorTuning.java | `9e362c644b7c1f8a1419d3319f2e1ae9a914b707d0d3ce43418536e73df88ebe` |
| RealmMeteorPath.java | `f62e5450c489f7395b768e6b1f660394b8387b6bbf3cf17bddf462f9c16e42e0` |
| FirstPersonFlightFlow.java | `42aa75c0d714563c2b077adffc03ecc6367b43119d81a96495239c4a81ee75a9` |
| realm_meteor.fsh | `a9294a978216fdc11dbe0d1de6071504d41ceb6c4c7e2d9efd6f5deef6a06ac2` |
| realm_meteor_post.fsh | `31c5ff8e3b273ad9ad2725aaa8e118ae448cc0121309ec5f4a4c95570daf0c94` |

证据收集前后重新计算42文件哈希一致。本记录描述的是该快照；后续宿主修改时应刷新身份和定位。
