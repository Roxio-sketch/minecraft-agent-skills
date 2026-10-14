# 来源与独立实现

研究方向来自用户提供的 Lodestone-1.21、Veil-1.21、veil-example-mod-1.21，以及此前 Avaritia / ArcaneVortex 参考。引用快照而非最新版；本模板不是这些 MOD 的移植包。

2026-10-02 另查阅 [Epic Fight 官方仓库 1.20.1 分支](https://github.com/Antikythera-Studios/epicfight/tree/1.20.1)，本地稀疏快照 commit `9d25f19c121c5e51b1db9efe01833a563706eeee`，重点为 [TrailInfo](https://github.com/Antikythera-Studios/epicfight/blob/1.20.1/src/main/java/yesman/epicfight/api/client/animation/property/TrailInfo.java)、[AnimationTrailParticle](https://github.com/Antikythera-Studios/epicfight/blob/1.20.1/src/main/java/yesman/epicfight/client/particle/AnimationTrailParticle.java)、ProjectileTrailParticle、LaserParticle。其关节双挂点、动作发射区间和多姿态采样启发了 adapter 指南；GPLv3 源码未复制或并入 MIT 模板。也对照 [Lodestone 官方仓库](https://github.com/LodestarMC/Lodestone) 的路径、粒子与绘制职责分离；本地 1.21 快照不视作 Forge 1.20.1 API。平台事件接入按 [Forge 1.20.1 官方文档](https://docs.minecraftforge.net/en/1.20.1/concepts/events/)和本机实际编译核对。

- Lodestone 1.21.1 的 TrailPointBuilder / WorldVFXBuilder：学习点寿命、插值和条带职责分离。模板重新实现历史、弧长与 mesh，不带入 Lodestone Java 21 或 NeoForge 代码。
- Veil 的 trail 模块与离屏管线：学习路径/材料/绘制阶段分离；未带入 Veil 依赖或 shader。
- Avaritia 与 ArcaneVortex：学习材质和装饰层独立、不同材质在 draw 时绑定，不把材质替换等同动作轨迹。

本地原归档索引：`E:/着色器渲染/research/render-frameworks/archives.json`。Lodestone 快照的 LICENSE.txt 标 LGPL v3 而元数据 GPLv3，研究保留冲突；本技能不复制其源码来规避这项判断。

Geometry、Recipe、Paths、Effects、BeamRender、ClientLab、HTML/JS、脚本、纹理与配方均为本次独立创作，随附 MIT 许可。Gradle wrapper 来自既有 Gradle 8.8 工程，保持 wrapper 脚本头部与 Apache-2.0 来源声明；Minecraft/Forge 为构建依赖，不打包其源码。
