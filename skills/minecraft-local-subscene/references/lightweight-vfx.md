# 程序场景、轻量几何与边界 VFX

## 无需第二世界的独立场景

SceneSource 持有自己的对象、时间和 camera 输入，输出独立颜色/可选深度；不遍历原世界地形来替换材质。最低可用场景也应能用固定时间和眼位重复生成同一幅结果，便于分离 camera 变化、动画变化与 bug。

按目的组合，而非固定使用某个 Cosmic shader：

| 场景 | 组成 | Camera / Depth |
|---|---|---|
| 星空与银河 | 远场方向星点、连续球面噪声、银河密度带、可选近场星尘/碎片 | 远星主要随朝向变化；近几何产生视差；不能靠滚动主表面 UV 冒充空间 |
| 云海 | 低频密度场、分层 cloud mesh 或有限步数体积积分、内部光照 | 射线限定子空间有效区间；明确 transmittance 与实体遮挡 |
| 时间河流 | 沿曲线生成带状河道、时间切片/碎片 mesh、独立流速与 phase | Scene 时间可暂停/倒流而主世界不受影响；河道是几何，不只是屏幕滤镜 |
| 黑洞内部 | 中心 aperture 连接原创星域/几何场；外部偏折和吸积盘另画 | 黑洞暗核本身不证明存在子场景；对子场景的射线映射才改变所见空间 |
| 虚空/遗迹 | 背景梯度、远星、分层 Mesh、局部体积雾 | 即使没有 ClientLevel，也可有真实 camera 视差、深度和独立动画 |

用可配置 seed、palette、密度、结构方向、尺度和速度生成设计。星点要处理解析 footprint 或 derivative 抗锯齿，防止远处闪烁；方向噪声避免经纬 UV 接缝。数值精度、raymarch 步数、噪声 octave、是否使用纹理是独立质量维度。

## 轻量 pass 图

```text
owned scene target
  → clear own color/depth
  → independent sky (no geometry depth write)
  → opaque meshes / opaque procedural surfaces (child depth test/write)
  → volumetric / translucent layers (explicit order and alpha convention)
  → optional child-local postprocess
parent target
  → aperture visibility + color composition + parent entrance depth
  → shell/rim emission or direct boundary draw
  → parent remaining passes according to the chosen integration stage
```

raymarch 的发光体积不必产生硬的首表面 depth。用累计不透明度/透射率定义合成，必要时区分 nearest opaque depth 与体积信息。将所有非零雾密度写为入口深度会错误遮挡内部几何。

不同 scene source 可以在同一离屏场景里组合：程序背景 + 几何星环 + 粒子。资源共享不等于渲染状态共享；每个 pass 明确 blend、depth mask、cull、shader、textures。

## 边界与开口分工

边界只表现“这里发生了空间变化”；coverage/depth 决定实际哪里能看另一场景。不要让 emission 或 bloom 控制 window mask。

| 边界设计 | 几何或算法 | 需要控制 |
|---|---|---|
| 球形能量膜 | sphere shell，Fresnel `pow(1-abs(N·V),p)`，带状噪声 | 内外双面朝向；膜 opacity；保留背景信息；前景 depth test |
| 环形边界 | torus/ribbon，周向能量流，分段符号 mesh | 透视厚度、面朝向、退化三角形、screen-size LOD |
| 裂缝 | 曲线的双侧 ribbon、尖端亮点、少量飘散颗粒 | 与 aperture 同源动画；分叉/自交；透明排序 |
| 剑击撕裂 | 两挂点或轨迹 ribbon，结束后固定裂口形状 | trail 是开口形成过程，不能代替固定窗口内容 |
| 黑洞边缘 | photon-ring 风格亮环、盘面、偏折背景 | 独立材质和层次，亮环不覆盖应可见的父前景 |

Lodestone `WorldVFXBuilder` 的 sphere/quad/trail/beam 是有用的几何组织样本；`TrailPointBuilder` 提供时间序列方式。迁移这些概念到 Forge 1.20.1 自有 builder，不为使用一条 ribbon 强行引入整套 1.21 依赖。

## Bloom 与独立 emission

Lodestone pb_bloom 的多尺度思路适合边缘：把亮环写到 emission target，half/quarter/eighth downsample + separable blur，再合成。要给 emission 相应的可见性/深度约束，防止墙后整圈光直接出现在前面。宽光晕是否少量越过轮廓是艺术选择，与几何遮挡是否正确分开测试。

shader pack 有自己的 HDR/tonemap/bloom 时，优先交给 pack-compatible emission 路径，或仅输出未模糊的边框。不能在未知 pipeline 上反复模糊已 tone-map 的场景。关闭 bloom 应只影响边缘发光，不改变窗口是否成立。

## 临时领域控制器

使用状态 `inactive → entering → active → leaving → inactive`，保存当前 session/world generation 和效果参数，避免永久修改主世界。过渡可让 sphere radius、coverage feather、scene opacity、场景 camera path 联动，但 hand/UI 保留规则独立处理。

死亡、换维度或断线会终止旧代次的领域；资源重载重建 shader/FBO，但是否保留领域动画进度由控制器决定。没有服务器玩法需求时，领域开关不必换玩家 ClientLevel。需要同伴可见时同步效果描述和时间基准，而不是每帧网络传送 FBO 图像。
