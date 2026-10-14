# 架构参考与可迁移结论

这些是用户提供的本地快照，不是随模板安装的依赖。快照目标均为 1.21.1；此 skill 的可执行代码单独以 Forge 1.20.1 构建和实测。

| 快照 / 入口 | 本 skill 提炼的决策 |
|---|---|
| ImmersivePortalsMod-1.21：`RendererUsingStencil.java`、`PortalRendering.java`、`PortalRenderInfo.java` | 视图递归需要显式上下文、可见性与深度策略；离屏纹理不能自动提供跨世界连续性。当前模板只采用禁止 renderer 重入和独立深度的单层路线。 |
| veil-example-mod-1.21：`MirrorBlockEntityRenderer.java` | 镜像相机、裁剪与表面采样是不同工作。需要真实镜面时必须实现反射相机；不能把自动绕场景旋转称为镜面。 |
| Veil-1.21：`VeilLevelPerspectiveRenderer.java` | 世界子视图会触及相机、矩阵、FBO、可见区、dispatcher、雾等全局状态。微缩 mesh 后端可避免完整世界状态切换；世界视图后端另行隔离。 |
| Veil：`CompositePostPipeline.java`、`DynamicBufferType.java` | 后处理输入、输出和资源生命周期要明确；颜色/深度/法线等附件按需求增加，不能把输出 FBO 同时作为当前写入采样目标。 |
| Lodestone：`WorldVFXBuilder.java`、`TrailPointBuilder.java` | 可借鉴独立几何生成与生命周期分层。粒子、拖尾是可选场景节点扩展，当前版本并未包含其实现。 |

以上是架构分析的结论，未复制其 Java 或 GLSL。原始来源身份：

| 用户归档 | SHA256 |
|---|---|
| ImmersivePortalsMod-1.21.zip | `2c9b46862d4f1e018565f4f5731ac407e6d3315acf22886eb70192e821ce26bd` |
| veil-example-mod-1.21.zip | `5128c7026e4d151d072996b4c06004c59dc01f3ff5826410b1aa132eef5635ee` |
| Veil-1.21.zip | `046abfa897c72bd36420438727c5cedea98c9b473fb0e7b5132b993dea569b2b` |
| Lodestone-1.21.zip | `87396f4508aac70c31a344532e22d04620834a6ad880bbe250676e021959c2b5` |

本机详细源码定位仍保留在 `E:/着色器渲染/research/render-frameworks/evidence-index.json`；此路径只用于追溯，不是使用 skill 的前置依赖。
