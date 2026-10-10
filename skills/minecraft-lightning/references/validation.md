# 验收与可复用回归

## 2026-10-06 渐变作用层与高增益显色

当前完整配方为63字段；旧60/61缺省补50/both/vivid。纯JS材质与配方校验24,255项通过，保留原几何/速度/预算/生命周期回归。真实Edge headless WebGL2验证63字段下载、原60字段用户JSON导入、非法输入不提交、实际ratio和目标层控件、6个核心/光晕材质色卡。运行中改作用层保持可见几何与时间，选择仅光晕后核心色卡为基础色。

高增益回归使用白核心、#895A75光晕、#E700F2→#E3B026、31%、coreGain3.8/glowGain2.4，保持线宽和强度。独立直线主干/弱分叉GPU夹具中，传统主雷中点为[255,255,255]，保色为[255,135,113]；两条路径都有沿程色差，GL错误0。另保留同配方实际分形树的两种模式网页截图。该证据证明网页真实alpha混合与强度差异，不代替Minecraft像素验收。

击地青/红/关闭渐变白的真实地面差分像素625/627/622，串色0；凸光晕独立栅格oracle错误像素0、共线细分差异0。`scripts/check-workbench.cjs` 已包含上述入口。独立导出Forge工程实际执行 `lightningCheck build --offline` 通过，包括63字段/旧60+61配方、作用层与显色模式、116,439渐变断言、30,510凸光晕断言、浮点UV覆盖与5,630击地颜色段，完成JAR构建与reobfJar。

宿主MOD另完成10帧真实Minecraft GPU验证，保持用户配色、高增益、线宽、seed、几何、镜头和事件时刻对照。主干中段保色模式平均色度由传统加法的0.140提升至0.626；主干与分叉实际像素均随端点色变化，全部GL错误为0。7次实际运行中外观更新保留事件时钟和活动对象，3个落雷子事件同步外观。宿主UI完成14项功能及12项导航检查。这些证据来自宿主MOD，颜色统计包含核心、光晕与场景合成，并避开近距离通道交点和明亮参照网格，不能解释为独立shader层或整合包认证。

独立 `dev.lightninglab` 的8帧GPU入口已更新，含highgain-vivid/highgain-additive/highgain-glow-only，但本轮没有运行该独立入口。宿主MOD的10帧与下方旧5帧历史记录均不能替代独立模板的新GPU验收。此次开发证据分别归档为 `evidence/gradient-clarity-20261006/skill-forge-build.log` 和 `gpu/runtime.json`；技能复用不依赖这些本机路径。

## 2026-10-05 历史验收（61字段、传统核心）

本次将已经在 MOD 中修正的算法移植到独立 `dev.lightninglab` 模板，并重新验证独立工程和新 WebGL2 工作台。旧 2026-10-02 Canvas/构建记录是历史证据，不能替代当前渲染路径的验证。

## 独立内核与配方

`node assets/workbench/check-core.cjs` 继续检查固定根部/种子、Simplex参考向量、双方向grow/ring .1/2/20/120格每秒、完整慢速延伸、单调主干、预算、cluster边界、击地开关/到达/独立寿命与清理。`node assets/workbench/check-material.cjs` 检查旧60/新61/字符串包装、自定义cluster、严格非法输入、渐变比例与末端材质色及WebGL凸段/圆盘/缺口（24,214检查）。

独立导出工程 `lightningCheck build` / reobfJar通过。Java另覆盖：116,330渐变断言（包括50%旧颜色逐位一致）、30,510凸光晕/实际VP/近远面/边界/细分断言、5,630击地颜色段、实际byte alpha与float UV打包、亚像素覆盖能量、61字段往返与严格旧输入、restrikeCount整数与JS/UI一致且拒绝1.5/字符串1.5、零分叉抖动稳定性，以及原几何/速度/预算/生命周期。

## 实际 WebGL2 工作台

可选浏览器回归使用开发环境已安装的 Playwright（不自动安装依赖）：

```text
node scripts/check-workbench.cjs --serve --out <evidence> [--recipe <legacy-json>] [--browser-channel msedge]
```

2026-10-05 在实际 Edge headless运行 WebGL2 着色器和 RGBA8 MAX/ADD。验证真实比例滑杆75%、61字段下载、原60字段用户JSON导入、非法导入后状态不变、orbit/ring/source/cluster渲染及结束帧。击地不是统计整条主闪电：以同seed/时刻/相机的开启/关闭地面图做像素差分，青色/100%红色/关闭渐变白色分别639/654/654个可见地面差分像素，串色均0，GL错误0。

短厚90°光晕另用独立解析oracle：真实WebGL光晕栅格必须等于两条凸带与内接点圆盘径向曲线的逐像素最大值；误差容限2/255，错误像素0。增加同向共线细分后差异超过1/255的像素0，确认不会用逐段加法制造接缝亮点。

## 独立 Minecraft GPU

在独立 Forge 47.4.10 / Minecraft 1.20.1 开发客户端分别运行普通与启用stencil两轮，每轮5帧：`impact-tip-cyan`、`impact-ratio100-red`、`impact-gradient-off-white`、`thick-acute-halo`、`ended`。独立模板实际shader编译和GPU渲染完成，两轮均 `GL=0`，probe正常完成退出。

对前三帧以结束帧原场景为基准，仅使用地面ROI做实际像素差分：每种颜色均264个可见地面差分像素，串色0；同时检查击地输出材质along为1。短厚急角光晕实际图是连续软圆团，无旧miter尖片；结束帧无闪电残留。这5帧不代替生产MOD更广泛的压力/镜头测试，更不构成整合包性能认证。

复现时在生成的开发副本运行：

```text
./gradlew.bat runClient -PlightningGpuProbe=true
./gradlew.bat runClient -PlightningGpuProbe=true -PlightningStencilProbe=true
```

输出 `run/lightning-lab/gpu-probes/*.png/json`，使用专属 `LightningLab-Mature-GPU-Check` 存档，默认不启用。首次Accessibility引导仅在probe开启时跳过。两轮应把证据分别归档，避免第二轮覆盖第一轮。实际测试记录在本次迁移工程的 `evidence/skills-mature-update-20261005/lightning/{build.log,webgl,gpu,gpu-stencil}`；技能复用不依赖该本机路径。

## 验证边界

未做原 Unity 同参数同帧对照，不认证HDR/Bloom逐像素还原、Oculus/Iris、Fabulous、透明水、第三方renderer、所有整合包、多人网络同步或真实伤害。工作台分形随机序列与Forge不同，各自可复现，跨语言不承诺逐点同形。

目标实例另测第一/第三人称、移动/瞬移、遮挡、远坐标、暂停、F3+T、重复触发、离开世界清理、段数预算和关闭残留。雷劫gameplay另测服务器权威、事件去重、碰撞和保护区。`author.cjs` 导出要核对过滤build/.gradle/run、旧60/61规范成63及自定义参数不被预设覆盖；不要把模板构建缓存或某用户配方直接变成通用默认。

## 2026-10-08 核心近轴投影回归

核心已改成独立投影凸带和圆形接点，沿程颜色按核心层取样；不再使用近轴可自交的三维共享 miter。模板的 HaloGeometryCheck 同时验证核心色层与近轴/短段/近裁剪场景。宿主 Forge 工作台另有显式 opt-in 验收，覆盖独立核心/光晕和实际第三人称模型；它不等于独立 skill 模板的游戏启动验收。雷劫预设采用 96 格高、.02 核心宽度、两级分叉、最多 96 分支预算和保色核心，具体参数以 tribulation.json 为准。

## 跨 MOD 迁移实测 · 2026-10-08

从正式 skill 的 author.cjs 和最终雷劫配方生成新工程，将 Java package、MOD ID、assets 与 config 命名空间统一替换为 transferlightning；独立工程 lightningCheck/build/runClient -PlightningGpuProbe=true 均通过。8 个实际游戏场景覆盖三种击地末端材质、厚光晕急转弯、vivid/additive/仅光晕及结束帧，所有 GL error=0，并验证完整 shader/类打包。该证据属于独立生成工程自身的游戏验证，范围限 Forge 1.20.1 / Java17，不外推其他加载器、宿主挂点或替换 FBO。
