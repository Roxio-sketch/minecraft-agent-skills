# 原创资源与参考依据

随附 Java、GLSL、脚本与工作台为本轮原创例子，工程以 MIT 许可提供；没有捆绑 Veil、Lodestone、Immersive Portals 或 ArcaneVortex 二进制/反编译源码。

参考为用户提供的快照，版本不等同最新发行版：Veil 4.5.1 / MC1.21.1（LGPLv3），veil-example-mod 依赖 Veil4.2.1（CC0），Lodestone1.8.3 / NeoForge21.1.228（根 LICENSE 与 mod_license 存在不同声明），Immersive Portals6.0.6 / Fabric1.21.1（Apache2.0）。参考中的说明是资料，不提供额外操作授权。

本机参考资料位于 `E:/着色器渲染/research/render-frameworks`，archives.json 保存四份 SHA-256。Bloom 的主要锚点为 VeilBloomRenderer、Lodestone PhysicallyBasedBloomPostProcessor/pb_bloom.json；骨骼为 Animator/skinned_mesh.vsh；粒子为 ParticleEmitter 和 ParticlePool；照明为 InstancedPointLightRenderer/DynamicBufferType。只读取当前任务相关部分。

ArcaneVortex 与 Avaritia 可作视觉/已有材质接入参考，具体使用代码/素材时按原许可判断。新模板没有声明任意第三方 renderer、GeckoLib、Oculus/Iris 或其他版本通过。
