# 来源与状态

本技能为原创方法与代码，不携带用户 JAR 的反编译实现。

## 研究参考

- ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge.jar，SHA-256 `27c5d799a488a418a588b978bb77da6bd48bbe48d8bc5c6a11cfb7b6f60a8b97`。包内声明 All Rights Reserved。学习线索来自 `glow_edge.fsh` 的 uType=2：Voronoi、屏幕采样偏移、色散与模糊；SceneCopyTarget 与 RenderStateSnapshot 用于发现接入和状态管理问题。没有复制原 shader、素材或 Java。
- 最初五份 Avaritia/Re-Avaritia/RenderBlender 归档提供 mask、原模型状态、uniform/flush、资源重载、Halo 和延迟队列的比较背景。源码快照身份记录在工作区 `skill/minecraft-cosmic-material/references/sources.md`；本技能的运行不依赖那些归档存在。

原创内核选择稳定法线 + 三个 refract 方向 + 有界五点采样，没有重打包 ArcaneVortex 的多层 Voronoi/refractionEffect/二维高斯实现。随附程序星域也是原创示意背景，不是其 Cosmic shader。

## 验证范围

本地验收：31/31 项 WebGL 2 与 UI 检查通过，渲染器报告 ANGLE / NVIDIA GeForce RTX 4060 Ti / D3D11。已人工检查星域碎镜和网格折射的大图与制作台小图；窄刃配方另有大图和 16/32 图供复核。GLSL 150 三件套已生成，本轮未运行桌面 GLSL 150 编译器或 Forge 客户端。

原图切换与时间上限修复后，31 项预览检查重跑通过，新增 22 项状态回归检查通过。证据分别位于 `research/crystal-refraction/state-fix/full-preview/validation.json` 与 `research/crystal-refraction/state-fix/after/state-validation.json`；修复前的失败记录保留在同目录的 before 中。

以实际输出 `validation.json` 为准，证据默认可放入 `E:/着色器渲染/research/crystal-refraction/evidence`。自动检查包含 WebGL 编译、framebuffer、像素不变量、小图和配方/UI，运行信息保存于记录。GLSL 150 资源生成与浏览器验证分别报告；没有 Forge Java 注册、生产 JAR 或客户端实测。

本技能支持进一步设计与接入，不承诺所有物品、所有 shaderpack 或所有 GPU 自动兼容。已有 Cosmic 实验的 Forge 运行认证不能覆盖这里新增的折射算法与场景采样。
