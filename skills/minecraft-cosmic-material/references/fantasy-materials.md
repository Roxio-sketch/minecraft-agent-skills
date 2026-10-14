# 奇幻材质制作

用户描述决定结构。先判断主体是内部空间、实体表面、半透矿物、纤维，还是几何图案，再决定发光方式；不要默认加星点、烟雾或漂移 UV。十四个原创内核共享随机数、噪声、线条过滤工具，主体算法分开。内部翻卷优先读 [internal-flow.md](internal-flow.md)，水波读 [water-material.md](water-material.md)，电视雪花读 [tv-snow-material.md](tv-snow-material.md)。

## 已实现的起点

| ID / mode | 视觉结构与算法 | 自然语言可推动的变化 |
|---|---|---|
| rune-vault / 0 封印回廊 | 六个深度平面的符文、圆环与中心封印；反向旋转、沿环输光、独立视差 | 远近封印错位、疏密刻度、绿金双层、齿轮般反向旋流 |
| liquid-mithril / 1 流汞秘银 | 多尺度连续剪切输运高度褶皱，求法线与解析条状反射 | 水银卷动、褶皱拉伸并合、冷色实体与白色亮脊；并非实时场景反射 |
| jade-veins / 2 苍玉灵脉 | 八层吸收玉质沿纵深输运，弯曲主脉与支脉、移动脉光 | 浓玉/薄玉、暖脉/冷脉、沿分支输光、前后遮掩；简化美术体层而非物理次表面散射 |
| gilded-porcelain / 3 金缮灵瓷 | 固定 Voronoi 瓷片、釉色分区，金缝里的移动亮流 | 白釉与青釉比例、裂片大小、金缝粗细与熔光流速；不是玻璃折射 |
| iridescent-silk / 4 虹蜃织锦 | 交叉传播的高度行波、经纬交织、随法线变化的薄膜干涉与丝光 | 绸缎翻卷、纬线粗细、移动褶皱峰、金属丝光；并非星云混色 |
| folded-ember / 5 熔流折界 | 三维剪切折叠的发光薄层、纵深穿行、64 步发射吸收积分 | 熔光卷入、前后流层遮挡、冷暗底场、持续形变；非流体物理求解器 |
| fantasy-flow / 6 幻流云廊 | 共享虚拟视点、六级三维扰动、双侧云壁、100 步自适应接近辉光与吸收 | 远近翻卷、内部洞隙、前壁变薄露出后壁、内部光脉；非真实流体模拟 |
| aurora-veil / 7 流光极幕 | 48 步采样垂直折叠发光幕面、纵向纤维、幕边与深度吸收 | 幕带翻卷、明暗丝流、绿紫渐变、前层透出后层；不是天空盒 |
| storm-prism / 8 雷晶裂隙 | 六层固定晶面和每层三条分叉电弧、沿路径行进的电荷峰 | 硬质晶面保持、锯齿弧路摆动与错开放电、冷紫底与白电光 |
| aether-bubbles / 9 琉璃泡海 | 五层空间泡格、每层检查邻近九个球壳的解析求交、上浮和侧摆、薄膜色与轮廓光 | 大小泡体交错、稳定泡格持续输运、壳厚视觉、前后遮掩；近似层合成，不是场景折射或严格透明排序 |
| clockwork-core / 10 时轮机芯 | 四层空间轮阵、黄铜轮齿、辐条、铆钉与前层遮挡 | 相邻反向转轮、不同深度转速、齿密和轮辐宽度；美术机械，不保证精确齿轮啮合 |
| living-mycelium / 11 荧孢菌脉 | 六层分叉丝脉与末端孢结、缓慢形变、沿支路行进亮包 | 菌丝张弛、端部呼吸、沿脉输光、远层暗化；不是真实生物生长模拟 |
| tidal-water / 12 澄浪水镜 | 双尺度固定种子复高斯频谱、128×128 逆 FFT、有限深度色散、共享高度场法线、Fresnel、日光高光与虚拟水底折射 | 波高/波长/风向/挤压/泡沫/粗糙度独立可调；不是完整海面 FFT 模拟或真实场景反射 |
| crt-static / 13 电视雪花屏 | 帧步进伪随机雪花、逐行扫描线衰减、水平同步带、RGB 延迟色差、偶发撕裂换色与 CRT 暗角 | 雪花密度、信号抖动、扫描层距、帧噪速度和亮度增益独立可调；是物品内部信号场，不读取视频或屏幕颜色 |

不要把“液态”简单翻译成速度，把“金属”简单翻译成灰色，把“玉”简单翻译成绿色。保留的判别线索分别是随角度移动的反射亮带、表面连续法线、内部吸收与前后脉络。设计变体先改变这些线索，再改色。

示例：“白瓷被赤金裂缝封住，瓷片安静，只有裂缝缓慢流光。”选 mode 3；浅色 base、少量深色 accent、暖 highlight，speed 很低。当前 speed 驱动金缝中传播的亮部，瓷片拓扑固定。若要求瓷片真实离散漂浮，则需新增几何或分层求交，不能声称一个参数已经实现。

示例：“暗银剑刃像活水，转动时白光沿褶皱滑过。”选 mode 1；降低主体色，保留高亮反射条，增加 warp；固定时间拖动观察点检查高光移动。要反射真实世界则需要场景颜色/立方体图等输入，解析亮带不具备该能力。

## 运行与资源

从 skill 根目录运行：

```powershell
python scripts/serve_authoring.py --lab fantasy
node scripts/validate_fantasy.cjs --python python --out ./fantasy-validation
```

服务默认独占绑定动态端口。打开实际打印的 URL；不要猜测或复用 8768。工坊有“奇幻材质 / 14”和“内部星空 / 03”两个分组；星空保留深空星海、紫蓝星云、赤金裂隙的原始内核、参数和配方格式。切换分组保留各自状态，隐藏分组停止推进动画。`?family=cosmic` 直接打开星空分组；`?preset=tidal-water` 或 `?preset=crt-static` 选择水波、电视雪花等有效 ID。

统一服务在 `/cosmic/` 提供原 authoring-lab 的四个资源，不另开端口、不复制另一套内核。健康摘要覆盖奇幻和星空共八个文件。部署须保留同级两个 assets 目录。`validate_workshop.cjs` 检查三种旧风格、导出、分组状态保留和桌面/手机布局；`validate_fantasy.cjs` 负责十四种奇幻材质的输出、动画、配方往返和桌面/手机验收，`validate_water.cjs` 另验水波参数及连续序列。验证只清理自己启动的服务与浏览器，依赖见 [authoring-workbench.md](authoring-workbench.md)。

星空导出仍是 `cosmic-authoring-preview/v1`，需要按旧制作台文档实现 Forge 接入；不能交给仅接受 `fantasy-material/v1` 的 `export_fantasy_pack.py`。合并 UI 没有把三种星空预览自动认证为新 Forge 材质。

- [material.glsl](../assets/fantasy-lab/material.glsl)：原创十四种内核，共享 GLSL 150 / ES 300 语法子集。
- [recipes.json](../assets/fantasy-lab/recipes.json)：十四种线性 RGB 配方，实际读取。
- [preview.js](../assets/fantasy-lab/preview.js)：WebGL host、观察点、材质面/剑刃/圆形窗口、真实 16/32 px 重渲染、配方导入导出。
- [validate_fantasy.cjs](../scripts/validate_fantasy.cjs)：十四种输出、动画/seed/视角、小图、遮罩外部采样、冻结、配方往返及桌面/手机验收，并保存对照图。
- [record_fantasy_motion.cjs](../scripts/record_fantasy_motion.cjs)：六秒固定时间序列，每种 120 帧，20 samples/s；不是实时 FPS 测量。
- [make_motion_sheet.py](../scripts/make_motion_sheet.py)：使用 Pillow 将序列组装成动画 WebP 与时间接触图。
- [export_fantasy_pack.py](../scripts/export_fantasy_pack.py)：从导出的配方生成 Forge 1.20.1 资源包；校验实际参数并保留其他配方。

浏览器里的“封存球”是圆形开口示意，不是真实折射玻璃球。金属亮带和发光线直接在材质里计算，没有屏幕 bloom 或周围世界照明。缩略图固定 time=0，主预览可动。

## 内核与参数契约

入口 `vec3 fantasyMaterial(vec3 entry, vec3 ray, float footprint)` 返回线性 RGB。`entry.z=0`，`ray.z>0` 朝向虚拟内部。观察响应统一乘 `fmParallax`。核心不读取贴图、世界深度或屏幕颜色；宿主负责遮罩、图元和显示转换。

| 字段 | 范围 | 实际含义 |
|---|---|---|
| mode | 0–13 整数 | 切换主体算法；与 ID 一一对应；mode 12 另含六个水波参数，mode 13 为电视雪花信号场 |
| seed | 0–65535 整数 | 稳定 glyph/矿物/域噪声/纤维偏移，不随时间重排 |
| scale | 0.3–3 | mode 0–5 增大结构密度；mode 6 缩小射线张角，使内部结构放大 |
| speed | 0–1 | 秒时钟倍率；0 时宿主传 `fmTime=0`，内核也门控时间 |
| detail | 0.2–2 | 依模式为符文数、拉丝频率、脉宽、裂缝宽、经纬密度、热流聚集程度 |
| warp | 0–2 | 角摆、流体剪切、主脉弯曲、胞元域扭曲、绸缎起伏、流层折叠 |
| depth | 0.5–6 | 依模式为层距、法线起伏、玉层厚度、视差偏移倍率或积分深度 |
| parallax | 0–1.5 | 观察响应；0 时视点变化不影响内核 |
| emission | 0–3 | 依模式控制刻线/反射/脉光/金缝/丝光/热纹亮部，不是统一物理辐射单位 |
| base / accent / highlight | 3 个 0–1 分量 | 线性 RGB；UI 色板转 sRGB 后显示，修改后再转回线性 |
| exposure | 0.3–2.5 | host 的指数映射与显示转换，不属于核心 uniforms |

全部 fm uniforms 与配方字段同名，首字母大写，如 seed→fmSeed。预览 recipe `schema: fantasy-material/v1` 包含 parameters、preview（view/time/shape）。导入先检查 ID/mode、数值、色值和预览状态，失败保留当前配方；导入成功暂停在保存的时间。

mode 7–11 的 `detail` 分别控制幕丝亮度、电弧宽度、泡体尺寸、轮齿数量、菌丝宽度；`warp` 分别控制幕面扭动、弧路摆动、泡体侧摆、轮辐宽度与菌丝张弛；`depth` 分别控制幕层行程、晶层间距、泡层间距、齿轮层距与菌层间距。它们沿用同一数值范围，但语义不同，UI 会切换主要参数名称，通用“形变”字段按此表解释。`speed` 驱动该主体的明确运动，不能理解为统一 UV 滚动。

这些五个模式的积分入口是 `fmNewInteriorRay(origin,direction,footprint,time)`，Forge 的 `fmNewInteriorWindow(screenUv,cameraAngles,footprint)` 使用跨面共享射线。UV 只裁剪覆盖区域，不在六个面上重新生成泡体或齿轮。它们是窗口内的虚拟场，不随载体一起旋转的局部实体体积。幕面、晶面、球壳、齿轮和菌层分别处理自身的深度与遮挡，不能互换同一密度函数冒充新风格。

材质不局限于预设按钮：已有结构的变化改配方；结构变化则新增独立函数、mode/ID、范围与实际参数语义，同时扩展调用与验证。不要向所有内核追加同一层噪声来假装新风格。

## 动态制作与审查

2026-09-30 用户指出第一版动态差。第一版的静态分型虽成立，多数时间项只是小相位摆动；“8 秒后有像素变化”的测试不足以验收流动质量。第二版分别实现反向旋转、连续剪切、纵深输运、沿缝传播与交叉行波，并取消主画布约 24 FPS 的人为限速。小图以约 12.5 FPS 更新，主画布随 requestAnimationFrame；暂停时立即重绘，使画面与配方时间一致。

参考 ArcaneVortex 的原则是让时间进入空间场和行进起点；学习方法及原始绑定见 [arcanevortex-study.md](arcanevortex-study.md)。其 `TimeSystem.getShaderTimeValue()` 实际返回 seconds/20；必须检查上游单位，不能照抄 `time * 10` 并声称速度一致。这里新内核的时间单位明确为秒，未复制它的 GLSL。

先定义“什么在流、沿哪里流、什么留在原处、前后如何遮挡”。同主题大结构应能跨帧追踪，细节允许在结构内部演化。直接增加全局 speed 会同时加快不相关层；新增时间项应有明确职责。固定 seed，避免逐帧换随机值。若用户要云幕卷动，采用柔性三维薄层；若用户要坚硬瓷面，只让缝内流光运动。

薄层体积分要检查沿射线的采样足迹。最初 40 步窄密度核产生移动条纹；当前 mode 5 以 64 步、相邻样本的相位跨度增宽核并补偿峰值，减少分层锯齿。不能将这种采样条纹宣传为精细流丝。

```powershell
node scripts/record_fantasy_motion.cjs --python python --out ./motion
python scripts/make_motion_sheet.py ./motion
```

只录新增方向可传 `--preset aurora-veil,storm-prism,aether-bubbles,clockwork-core,living-mycelium`；合成器加 `--all` 可在时间接触图中保留所有选中材质。

录制原速六秒连续片段，并看 0/2/4 秒的结构对照。输出是片段循环，不保证首尾无缝。采样帧的像素变化量用于定位异常，不能作为美术打分；同样不能以录制的固定 20 samples/s 宣称设备实际达到该帧率。必要时另测实际 requestAnimationFrame 间隔或 GPU 时间。

## 从配方落到物品

```powershell
python scripts/export_fantasy_pack.py --recipe exported.json --out custom-material.zip --id flowing-silver
```

不传 `--id` 时替换原材质 ID；传新 ID 时保留全部原配方并新增条目。现有输出默认拒绝覆盖，确需覆盖使用 `--force`。生成包使用 `pack_format:15`，含 `assets/cosmicmaterial/fantasy-recipes.json`，必须与支持这些算法的 Cosmic Material Lab mod 同时使用。脚本只生成包和对应 `/give` 命令，不修改实例、不自动重载。

## 验收与接入边界

固定时间、种子、视角导出主图；观察整体结构后再看剑刃裁切。16/32 px 必须实际渲染。比如封印圆环的完整轮廓不会装进窄剑刃，小图应读取金绿刻线与中心亮段，若用户要完整魔法阵需换宽载体或专门重构构图。

默认冻结检查 30 轮，十四模式轮换、三个画布、每轮零速度变时间和相同状态重绘，共 180 对。零容差保留；1/255 也记失败并留首个差异图。新材质检查成功不意味着原 Cosmic 的偶发 67/87 像素差异已解决。

WebGL host 的 entry 来自预览平面，不能复制到 Forge 后宣称是任意三维物品局部空间。Forge 1.20.1 的新增适配器、真实 NBT 与运行证据在 [fantasy-forge.md](fantasy-forge.md) 中单独记录；只有通过的类别可写成已验证。



