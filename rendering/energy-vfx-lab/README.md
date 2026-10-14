# 特效编辑工作台 · v0.2.5

打开 [实时工作台](http://127.0.0.1:8796/)，或双击 index.html 离线编辑。无需 CDN、npm 或图片素材。当前保存配方优先于预设；用户之前的配方备份保留在 evidence/recipe-before-blue-ghost.json。

1. 从 20 个起点中选择，然后调整 48 个参数与两个颜色。原有空间能量效果之外，增加蓝色幽气、冷焰、薄雾、装备溢散，以及透明白/蓝折叠卷烟；起点 16 为“幽纱 · 冰蓝卷烟”。预设均可继续修改。
2. attachment=1 从身体发射，2 从装备发射，3 从两者发射，0 保留空间效果。调生成率、寿命、离体速度、上升、卷曲、烟纱厚度、侵蚀和颜色；拖动绕视，滚轮缩放，时间线用于观察与重放。
3. “保存当前配方”写入 current-recipe.json；也可导出完整 JSON。相机和预览场景不属于游戏配方。Skill 将保存的参数应用到实际游戏目录的 config/energyvfx/recipe.json。
4. 安装 energyvfx-0.2.3.jar 后重启 Forge 实例。仅改参数时可由 Skill 的 apply_recipe.py --reload 请求固定文件桥加载并等待同 nonce 结果；世界内也可运行 /energyvfx start、reload、off、status。

网页与 Forge 共用六份 GLSL 与字段顺序。每个气团出生后以固定 20 Hz、RK2 在世界坐标中独立漂移；内部则沿不同轴、不同相位的局部 curl 速度场回溯坐标，让烟片折叠和剪切。内部翻卷与气团整体漂移分别计算。shell 控制烟雾/烟纱混合，shellWidth 与 sharpness 调整薄层宽度；经拉伸与回溯变形后不保证严格恒定的世界厚度。

烟纱积分保留局部近似 signed distance，在每段中按距离线性变化，用误差函数解析积分 Gaussian 薄层，减少细薄烟片被步长漏采的颗粒。此路径不使用逐像素 jitter，也没有 3×3 屏幕平滑。起点 15“月白卷烟”和 16“冰蓝卷烟”均为 resolution=1、steps=128；持续发散将该步数换算为每气团 64 个积分段，空间模式仍按原光线步数求积。空间模式保留球、环、涡柱、束、蓄力爆发、吸收、局部 Bloom 与背景扰动。

游戏身体从实际动画 ModelPart 表面采样；普通盔甲与普通持有物品 baked quads 保留动作、手臂和显示变换，出生后的气团留在世界中继续漂散。网页人物为观察用代理模型。BEWLR/GeckoLib 动态模型及非标准盔甲使用明确计数的挂点 fallback，需要专用表面适配才能精确覆盖其几何。

最多 32 个气团；puffRate × puffLife 接近 32 时避免继续提高二者。第一人称建议低 firstPerson 自身强度，本次仅验证低强度分支执行和计数，未证明手部烟纱外观。steps/resolution 调整体积采样成本，背景仍全分辨率合成。局部 curl 与坐标回溯是程序化运动，不是完整流体求解器。

固定命令桥仅处理 VFX 的 reload/start/off/status/preset（整数 1..20），不接管玩家输入；保持 MCP-HELPER control_mode=false。模板目标 Minecraft 1.20.1 / Forge 47.4.10 / Java 17；独立 QA 与用户实例通过范围分别记录。

v0.2.3 加入逐气团保守屏幕裁剪，避免落到相机后方的拖烟扩大整个绘制区域。第一人称和被墙体推近的第三人称使用自身近眼可见性预算：至少 3 米完整绘制，低于 1.8 米减少近眼烟量，中间平滑过渡；这部分有外观取舍，全部气团继续模拟，保存参数不改。

本次两个 2560×1440 独立新世界各完成七阶段真实移动/站立对照，统一起点、80 tick 暖机与 200 tick 测量。近距移动 P95 38.706/38.406 → 6.856/6.540 ms；正常距离保持完整烟量，P95 14.236/14.414 → 12.389/12.514 ms。十二组关闭近眼策略的同帧图像逐像素一致，GL 状态恢复通过；网页回归与生产构建通过。原始数据和范围见 移动优化记录.md，用户实例验证另行记录。

ASTRA-TEST 已仅更新运行库并重启进入原 ASTRA_VFX_TEST，保存配方哈希不变。启动、Helper与9876监听 PID 8920 一致，/debug HTTP200，control_mode=false；两次新nonce回执为energyvfx/0.2.3、active=true、GL error=0、failed=false，draws 2147 → 4672。现场854×480、第一人称且无装备；这是运行验证，没有测量用户全屏整游戏FPS。证据见evidence/performance/astra-023-install.json。

交付输出目录为 E:/着色器渲染/deliverables；打包脚本在复制前核对源码、Forge 资源与生产 JAR 内六份共用 GLSL 及原生 flow-cache.glsl 的 SHA-256，发现旧 JAR 即拒绝打包。实际打包与安装结果见 Skill references/validation.md；原始日志和截图在 evidence。源码依据与许可见 幽气拓展源码.md。


## v0.2.4 第三人称积累起步修正

针对第三人称静止积累后起步时镜头穿过旧烟导致的体积积分尖峰，运行库保留全部气团模拟，只在本地玩家的缓存绘制包上按“实际包到相机”的流向椭球距离做平滑淡出。普通第三人称 body 使用 8–10 个包半径的淡出带、gear 使用 6–8 个包半径；切入近距视角时连续回到原 body 4–6、gear 2.8–4 的近眼预算。它会改变极贴近镜头的旧烟可见量，但不降低 resolution、steps、寿命、模拟容量或内部运动。

在 RTX 4060 Ti、2560×1440、第三人称背视角、连续 4 轮“静止 400 tick→移动 80 tick→停下 40 tick”独立 Forge 世界对照中，最慢起步 GPU 帧由基线 132–151 ms 降至约 32–36 ms，继续移动阶段约 7–12 ms。该测试是隐藏窗口的精确帧缓冲测试，不等同于用户现场全屏 FPS；详细数据见 `静止积累起步优化记录.md` 与 `evidence/performance/buildup-analysis.json`。


## v0.2.5 第三人称拉近预算

第三人称背视角在旧烟积累后拉近，会进入相机距离预算：3.25 米开始连续减少低贡献烟包，2 米以内达到更强裁剪；全部气团仍继续模拟，容量、resolution、steps 和内部运动不改。它会减少贴近镜头的烟量，作为性能与外观的明确取舍。

独立 2560×1440 隐藏窗口、第三人称背视角、400 tick 积累后连续测量 4/2.5/1.5/0.75/4 米，最新裁剪运行的 VFX GPU P95 为 20.57/14.02/8.12/0.79/24.27 ms，GL error=0、query 丢帧为 0。该 QA 使用仅测试用的 Camera.setPosition 覆盖，并不等同用户独占全屏总 FPS；详见 `third-person-zoom-performance.md` 与 `evidence/game/zoom-cull-run-2560/comparison.json`。
