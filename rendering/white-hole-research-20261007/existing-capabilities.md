# 白洞扩展：现有能力、缺口与实施路线

调查日期：2026-10-07。范围为源码审读和方案整理；没有修改 skill、MOD 或启动游戏。下文“已有”表示发现实际消费者，不表示白洞已经实现或已通过新效果验收。物理来源和公开参考的证据由同目录总研究报告补充。

## 可复用的实际能力

| 层 | 已有世界实现 | 白洞复用方式及限制 |
|---|---|---|
| 固定世界中心 | `BlackHoleClient.preview()` 对地形射线命中，沿命中法线抬起核心，保存中心；`render()` 不逐帧重新放置 | 原样保留定位、世界切换/off 清理；不能恢复之前“随玩家移动”的错误 |
| 光学眼点与相机 | `BlackHoleWorld.render()` 捕获真实地形 VP，通过逆 VP 求光学眼点，并在相机相对世界轴中渲染 | 第一/第三人称、垂直/斜视、bob/hurt 等复用；白洞形状不应绑定屏幕圆片 |
| 核心 | `bhCoreEmission()` 按入射面/轮廓做双色边缘渐变；捕获核心时先保留前方盘辐射，再加入核心源色，终止后景透射 | 白色/蓝白核心已经能染色，但这只是发光表面基底；尚无独立核心发光倍率、表面喷发/脉冲或新白洞行为 |
| 盘 | classic/granular/ribbons/dust/cinematic 五种程序结构、线性 RGB 多色渐变、差速相位、径向输运 | 保留盘结构，新增向外输运；“白洞”为外流盘/喷发壳的美术解释，不能继续把一切都标为吸积 |
| 局部 halo | 环/盘/外层三层，沿弯曲射线以段长积累，考虑剩余透射；窄环有步长/像素足迹重建 | 可复用轮廓光、盘侧光和外层辉光；它是局部辐射，不是屏幕 Bloom 或场景动态照明 |
| 双极喷流 | `jetEmission()` 为极轴体积，具有 spine/sheath、宽度渐开、根部/末端颜色、旋转细丝；同一弯曲路径积分 | 可做持续双极喷流基底；当前只有旋转相位驱动细丝，没有独立轴向输运速度、喷口发射器、冲击结点或喷流生命周期 |
| 独立 sprite | 固定粒子 ID 的 GPU 轨迹、出生/死亡淡出、螺旋入流、按投影速度拉伸；实际地形深度测试，不写深度 | 可保留 ID/VBO、billboard、透明状态与暂停结构；出生域、轨迹、速度、死亡域和 alpha 必须一起重写 |
| 真实背景透镜 | 质量轴上的 360° 角向映射；实体前环境副本和四 tap 过滤；整实体保护、玩家参与开关 | 正质量白洞默认保留吸引式映射；外流运动与引力透镜符号分开控制 |
| 渲染状态/资源 | 场景独立副本，stencil 同步；保存/恢复 FBO、viewport、纹理、blend/depth/cull/scissor 和真实 `BlendMode.lastApplied` 对象 | 保留这些成熟合约；每增加浮点目标/模糊 pass，都要纳入 reload/resize/off/world switch 与异常恢复 |

精确源码入口：

- [BlackHoleClient.java：preview，21 行](<E:/着色器渲染/vfx-workbench-lab/src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleClient.java:21>)；固定位置只在该入口计算，渲染事件在 41 行。
- [BlackHoleWorld.java：render，126 行](<E:/着色器渲染/vfx-workbench-lab/src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleWorld.java:126>)；捕获 VP/眼点在 152 行，玩家保护入口在 74 行，完整状态保存类在 337 行。
- [bh_world.fsh：bhCoreEmission，309 行](<E:/着色器渲染/vfx-workbench-lab/src/main/resources/assets/vfxworkbench/shaders/core/bh_world.fsh:309>)；核心截断/前方辐射在 418 行。
- [bh_world.fsh：worldDiskField，713 行](<E:/着色器渲染/vfx-workbench-lab/src/main/resources/assets/vfxworkbench/shaders/core/bh_world.fsh:713>)；`radius+uRadialPhase` 是现有向内纹理输运；cinematic/ribbons/dust 消费者分别在 732/772/804 行。
- [bh_world.fsh：haloEmission，822 行](<E:/着色器渲染/vfx-workbench-lab/src/main/resources/assets/vfxworkbench/shaders/core/bh_world.fsh:822>)；喷流 `jetEmission` 在 882 行，段内 Simpson 积分钩子在 63 行。
- [bh_particles.vsh：GPU 轨迹，14 行](<E:/着色器渲染/vfx-workbench-lab/src/main/resources/assets/vfxworkbench/shaders/core/bh_particles.vsh:14>)；出生/核心 alpha 在 36 行。
- [BlackHoleParticles.java：render，21 行](<E:/着色器渲染/vfx-workbench-lab/src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleParticles.java:21>)；实际 sprite 深度在 45 行，ADD 在 51 行。
- [BlackHoleRender.java：material，27 行](<E:/着色器渲染/vfx-workbench-lab/src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleRender.java:27>)；颜色线性化在 24 行，世界专用 halo/jet 消费者在 40 行，径向/轨道相位在 49 行。
- [BlackHoleSkillSchema.java：register，15 行](<E:/着色器渲染/vfx-workbench-lab/src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleSkillSchema.java:15>)；参数范围目前拒绝负 `infallSpeed/particleSpeed`。默认倾角是 0°，76 行旧注释仍写“垂直”，以实际数值为准。

## 浏览器、Forge 和物品路径的差别

skill 的 [Forge 接入字段表](C:/Users/MAINTAINER/.codex/skills/minecraft-black-hole/references/forge-integration.md:37) 明确列出已实现与元数据。网页的 HDR Bloom、背景星域/天空色、网页相机、粒子中间色及渐变位置，未被现有 Forge 世界消费者实现。不能把改好的网页直接当游戏效果。

网页 HDR/Bloom 为 [postprocess.js](C:/Users/MAINTAINER/.codex/skills/minecraft-black-hole/assets/preview/postprocess.js:100)，需要 `EXT_color_buffer_float`，浮点目标和多尺度模糊。世界最终合成在 [bh_world.fsh，1032 行](<E:/着色器渲染/vfx-workbench-lab/src/main/resources/assets/vfxworkbench/shaders/core/bh_world.fsh:1032>)，现为 `1-exp(-light*exposure)` 后与场景线性色相加；它没有网页那条 HDR Bloom 链。

物品 `item.fsh` 是旧虚拟内部场，不含世界五盘/粒子/喷流/halo；白洞若先用于世界，不能自动承诺物品、护甲、BEWLR 和 ModelPart 全覆盖。已有世界入口也是单客户端单场，未有服务端技能、命中判定、物质/方块弹出或多角色同步。

另一个关键限制：世界主核在 [bh_world.fsh，1005 行](<E:/着色器渲染/vfx-workbench-lab/src/main/resources/assets/vfxworkbench/shaders/core/bh_world.fsh:1005>) 对非保护前景设 `sceneLimit=1e8`，以免透镜已重映射环境后仍用原像素地形深度裁出半个核心。新增向外喷雾不能因此宣称已具备正确墙体遮挡：直线外流体积应独立按场景深度截断；若体积同样走弯曲光路，要为重映射后源深度制定一致策略。sprite 的 GL_LEQUAL 不能代替体积遮挡设计。

## 白洞所需新消费者

以下是建议字段，不是已经存在的配方/API。优先复用共用核与相机层，在有版本号的新配方中加入白洞分支；老配方缺省保持黑洞原行为。不要把负值塞进旧强度参数作隐藏开关。

| 建议参数/行为 | 实际需要新增的消费者 |
|---|---|
| `objectMode=black/white` | schema、严格 parser、网页、Java→uniform、核心/盘/粒子选择；由外观模式解释源行为，透镜不随它自动反号 |
| `flowDirection=in/out`、`flowSpeed` | 每一种盘结构的径向采样符号；粒子独立出生/终止域，不能只共用 UI 标签 |
| `coreEmission`、表面细节/脉冲 | `bhCoreEmission` 和合成链，使白核高亮而保留边缘与前方结构；选择 emissive surface 或 shell 的明确语义 |
| 粒子发射面/半径、寿命、外域 | 内侧发射、径向/切向速度积分、出域渐隐、稳定重生与容量；支持薄盘外流或球面外流 |
| `jetFlowSpeed`、轴向结点/脉冲 | `height-v*t` 类轴向采样坐标与两极一致 outward 定义；当前 `jetLength` 实际是从中心起的轴向末端坐标，要统一文档单位 |
| `eruptionMode`、蓄能/爆发/消散时序 | 固定 tick 的 effect age、开始/停止/重触发、出生预算、shell 半径/厚度/alpha 曲线；保存 seed 与暂停/恢复连续性 |
| 可选 `bloomStrength/Radius` | 白洞专用 emission 资格、浮点附件、模糊、最后显示转换；不能从整个场景高亮提取或机械复制网页 gamma |

物理解释需分开：正质量白洞是相应黑洞时空的时间反演分支，外部引力不因为“物质向外”就变成负质量。白核、外流盘、喷流都是游戏美术选择；没有被观测的标准外观。若用户喜欢排斥式背景扭曲，可提供明确标为“幻想反向透镜”的独立选项，不把它作为科学白洞默认。

### 时间反转的实现要点

现有粒子在外域出生并使 `q=r^1.5` 随 life 从大到小。仅给时间/速度乘 −1 会倒放已有出生和死亡，导致从屏幕外跳回、出生在错误域、核心 alpha 抹掉新粒子；既有 parser 也拒绝负速度。

白洞建议内侧出生，`q0>0`、`q1>q0`，`q=mix(q0,q1,life)`，`r=q^(2/3)`。同一轨迹上的角速度要重新积分：若 `omega=k/q`、周期为 P，则 `phi=phi0+k*P/(q1-q0)*log(q/q0)`；不能写 `time*omega(currentRadius)`。速度为 0 要冻结，q0≈q1 要使用匀半径极限；重生者不参与同 ID 连续性统计。

alpha 必须同时实现喷口淡入、外域/寿命淡出，曲线与出生半径约束一致；外流尾迹长度、冷热配色、与盘/核心的前后遮挡也应按 outward 语义调整。盘纹“r+phase”为内流，外流用“r−phase”，但不同盘的相位长度/周期需分别保持无缝。几何出生和体积纹理输运是两种消费者，改一处不能宣布两处完成。

## 三条外观路线

| 路线 | 具体画面 | 可复用/新增 | 主要风险 |
|---|---|---|---|
| A 持续双极喷发 | 蓝白核心、青/紫薄盘，两个极向发光喷流；亮结点从根部沿轴外移，喷口周围少量球面喷尘 | 复用 jet 的 spine/sheath 和积分；补轴向输运、内侧粒子出生、喷口淡入 | 仅开现有 jet 很像黑洞/类星体喷流，白洞身份要靠清楚的外流与核心；长喷流扩大追踪体积和屏幕覆盖 |
| B 环形外流 | 白色核心、薄环/水平盘，纹理和小粒子从内缘螺旋向外；外缘渐隐，可加断续窄波环 | 共用盘、halo、VBO；新增盘向外相位与外流寿命；无需先接独立体积库 | 粗亮环容易被看成传送门；不能让 halo 遮掉向外运动，径向细纹长期剪切会混叠 |
| C 蓄能后爆发 | 小白核逐渐变亮，释放三维膨胀光壳/波环与外散粒子，余辉消退；可循环或单次 | 复用核心、halo 与固定中心；新增事件 age、半径/密度曲线、burst 出生和停止语义，可接能量体积 | 相机进入薄壳时近裁剪/透射变化大；短爆发不是持续白洞，单靠一帧截图不能验收运动 |

推荐先做 **B 环形外流** MVP：世界固定中心、默认水平盘、白/蓝白核心、清楚的 outward 盘纹和同向 sprite、原 360° 正质量透镜与玩家开关、独立播放/停止、两层冷暖渐变。先用现有局部 halo 控制层次，正式 Bloom 作为独立后续增强。A 的轴向输运、C 的事件/体积可在共享的“内生外散”粒子核稳定后扩展。MVP 仍须实际新增外流消费者；调白核心和反向播放网页不够。

## 可组合的本地资源

- [程序粒子 ParticleEngine](C:/Users/MAINTAINER/.codex/skills/minecraft-procedural-particles/assets/forge-1.20.1/src/main/java/dev/rendercraft/particlelab/ParticleEngine.java:21)：固定 0.05 秒 tick、age/life、birth accumulator、容量拒绝计数与 burst。适合事件/爆发预算；它的 orbit/burst/fountain/attract 是参考起点，尚未直接接入黑洞场。
- [能量体积 field.glsl](C:/Users/MAINTAINER/.codex/skills/minecraft-energy-vfx/assets/lab/field.glsl:70)：最多 32 puff、流向局部坐标、RK2 回溯、薄烟/细丝/环密度；Forge 同源 shader 已存在。适合外喷烟纱、球壳和蓄能爆发；不应完整搬入首版环盘，尤其别把 32 包模拟数当真实可见数或 FPS 保证。
- [能量 VFX 实现合同](C:/Users/MAINTAINER/.codex/skills/minecraft-energy-vfx/SKILL.md:31)：RGBA16F、分离 emission、Bloom、独立场景采样、主深度只读；psrdnoise 需保留 Gustavson/McEwan MIT 许可。其局部 curl/RK2 不是流体求解器。
- [选择性 Bloom 示例](C:/Users/MAINTAINER/.codex/skills/minecraft-selective-bloom/assets/forge-1.20.1/src/main/java/dev/rendercraft/bloomlab/Bloom.java:15)：只给选中 emission 做两向模糊与合成。参考 pass/资源/深度职责；该独立例子的事件是 AFTER_ENTITIES，不能原样复制到现有黑洞 AFTER_LEVEL 管线，否则云层/玩家合成顺序再度出错。

## MVP 验收与风险约束

1. 同 ID 外流数值/GPU 跟踪：排除本帧重生者后半径单调增大，角速度满足轨迹积分；出生/死亡无闪点，speed=0 暂停，恢复不换 seed、不瞬移。盘、粒子和喷流分别关停，证明各自消费者生效。
2. 固定 seed/time 相机对照：地面放置、左右移动、垂直、斜视、第三人称穿过作用域、bob/hurt；核心与中心固定、360° 透镜连续，玩家开/关实际可见且整模型不碎片化。
3. 遮挡专测：墙前/墙后、盘穿墙、核心前粒子/后粒子、近镜头喷雾；使用同帧环境、field、after 差分。主核现有无穷 `sceneLimit` 策略不能直接充当喷雾墙体遮挡证据。
4. 色彩/亮度：主核、盘、粒子分别显示实际材质色；高发光不把渐变全洗成白，高曝光不掩掉外流；Bloom 开关仅影响白洞指定 emission，远离效果的场景颜色保持稳定。
5. 配方和热更新：旧黑洞配方缺新字段保持原样；非法/未知参数保留上一有效状态；网页→Forge→网页往返不丢方向/时序/发光字段。外观热更新不重新放置、换种子或重触发短事件。
6. resize/reload/off/world switch、stencil on/off 的完整目标和 BlendMode cache 恢复；性能测覆盖屏幕占比、长喷流/相机入场/爆发峰值，不只测 draw count。Fabulous/Oculus/OptiFine、其他 GPU、多场和同步需新适配/新证据。

本研究只审读当前源码；历史六视点核心定位、浏览器 transform feedback 和黑洞实测可以作为回归夹具资源，不能自动为新增白洞外流、喷雾、Bloom 或爆发背书。
