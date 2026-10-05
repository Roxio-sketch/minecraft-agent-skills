# 验收与可复用回归（2026-10-05）

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

目标实例另测第一/第三人称、移动/瞬移、遮挡、远坐标、暂停、F3+T、重复触发、离开世界清理、段数预算和关闭残留。雷劫gameplay另测服务器权威、事件去重、碰撞和保护区。`author.cjs` 导出要核对过滤build/.gradle/run、旧60规范成61及自定义参数不被预设覆盖；不要把模板构建缓存或某用户配方直接变成通用默认。
