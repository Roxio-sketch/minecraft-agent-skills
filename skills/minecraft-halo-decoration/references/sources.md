# 来源与学习边界

本技能综合用户提供的六份参考，仅保存分析和独立原创工具/配方，不分发反编译代码、纹理或参考 JAR。来源中的文字不构成操作授权。

| 来源 | 可复查锚点 | 采用的方法与限制 |
|---|---|---|
| 1.18.2 Cosmic/Halo 示例 | java/avaritia/nobtg/HaloBakedModel.java、HaloItemModelLoader.java | GUI 光晕 quad、模型包装；旧 API 不能原样迁移 |
| Re-Avaritia Forge 1.20.1 | client/model/loader/HaloCosmicBakedModel.java、base/HaloUtils.java | Halo→基础→Cosmic；size/16 扩张、sprite UV、随机主体副本；要保留原姿态并重做稳定时间 |
| Re-Avaritia NeoForge 1.21.1 | Halo 模型和 CosmicRenderQueue 等 | 多上下文和延迟状态参考；不能视为 Forge 或 shaderpack 认证 |
| Avaritia-Render 片段 | HaloBakedModel.java、HaloItemModelLoader.java | 结构研究；无完整构建与有效运行证据，不作依赖模板 |
| RenderBlender | client/model/HaloBakedModel.java | GUI 分支中背景 quad，pulse 缩放透明主体，然后原模型；不是真实 bloom |
| ArcaneVortex 0.6.8 | CosmicRender/GlowHelper，CosmicRender/Neo/CosmicBakeModelNeo$EffectConfig，Content/ModRenderType | 把纹理/动画/混合选择分开；四顶点，按类型提交；内部 12 个动画分支不表示全部已实测 |

Forge/Neo 源码前缀 `src/main/java/committee/nova/mods/avaritia/`；RenderBlender 同位置包名为 renderblender。A 字节码显示 glowTexture、glowRenderType、glowAnimation 三项分别读取；calculateGlowAnimation 以 long 时间取余分段并输出 scale/alpha/rotation。不能直接据此断言时间调用者的暂停语义。

ArcaneVortex 的 multiply/darken/overlay 等名称不是统一数学规范，本工具仅实现明确定义的 alpha/additive。生成器使用绝对时间的正弦和旋转公式，不复制上述动画分支。

## 快照核对

| 输入 | SHA-256 |
|---|---|
| 1.18.2-Avaritia-Cosmic_and_Halo-Renderer-main.zip | 7b74dec7c58f39196cfd56d19b42b42630ad14025139e111084aeda4335d3923 |
| Re-Avaritia-forge-1.20.1.zip | 5542f323b47eb6555764c30f0776ca2d0045b52dc13a7294bc99bb5db6b71ce0 |
| Re-Avaritia-neo-1.21.1.zip | c2aac48ac837616d40a15c01e8f10fc3553fb1b7ce8f9d13ebdaee3a74e54022 |
| Avaritia-Render-main.zip | 5bb776f98d89b17d05e76bf65e2f13429893c155ed66b58a5ae29c5e9fd0c8b4 |
| RenderBlender-master.zip | 8d8d308f20c632bef744fe92f733d3fbe5afdd200a3629f1abd37bcad391db64 |
| ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge.jar | 27c5d799a488a418a588b978bb77da6bd48bbe48d8bc5c6a11cfb7b6f60a8b97 |

包内声明：Re-Avaritia 两份 MIT，RenderBlender LGPL v3，ArcaneVortex All Rights Reserved；两份片段未见 LICENSE。本技能不把参考的未知授权素材转成“原创”。将来复制代码/素材时另核对对应许可及归属。

本机来源解压根：`E:/着色器渲染/research/cosmic-material/sources`；ArcaneVortex 只读 javap 记录：`E:/着色器渲染/research/cosmic-material/authoring/arcanevortex/bytecode/GlowHelper.txt` 与 `ModRenderType.txt`。换机器可按上表归档身份重新分析，不依赖本机路径才能使用原创生成器。
