# GitHub 研究到水龙卷工作台的应用

2026-10-11。本文记录原创 WebGL2 水龙卷的实现取舍。下载的 8 个项目、99 个精选文件另存于工作区 `research/github-water-vortex-20261011/`；它们保持固定提交及原始许可证，未复制到本 skill 的原创渲染资产。源码资料包的静态校验不认证当前效果，当前实现与验收以 [事件工作台](../../event-workbenches.md) 和绑定源码的 [涡旋报告](../../validation/vortex.json) 为准。

## 从源码得到的约束

| 阅读入口 | 学习到的分工 | 本工作台采用的路线 |
| --- | --- | --- |
| [Habrador `TornadoDust.RotateParticles`](https://github.com/Habrador/Unity-Tornado-Simulator/blob/46f0a76885203562d8203e037fd8f3660e931537/Assets/Scripts/Tornado/TornadoDust.cs#L96) | 按高度插值中心线与半径，再绕中心线更新角度；这是粒子漏斗 | 液体主体、粗螺旋、材质与飞溅共用中心线及局部坐标，另行定义上升与剥离 |
| [SebLague `sampleDensity`](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/Shaders/Clouds.shader#L165) | 低频主体与边缘侵蚀分开，密集内部受到较少侵蚀 | 保留下段较细连续内核，辅助缺口用于薄皮与局部片；水龙卷顶部另有造型内腔，统一裁上段水场 |
| [Particles4All `lambdaWGSL`](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/wgsl.js#L477) 与 [空深度处理](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/ssfr_wgsl.js#L242) | PBF 密度约束不会凭空补水；表面滤波遇到空深度直接返回 | 先检查实体横截面与纵向连通，再调法线、泡沫和透射；不靠屏幕模糊填主体孔洞 |
| [splashsurf `particle_support_loop`](https://github.com/InteractiveComputerGraphics/splashsurf/blob/56d3e6ac444cf8540c238a4391483955a9484210/splashsurf_lib/src/density_map.rs#L677) | 相邻粒子贡献累加为共同标量场，再提取共同表面 | 采用原创共同隐式实体承载主体与侧片；没有移植 CPU 重建器或粒子求解器 |
| [three.js `Water2.updateFlow`](https://github.com/mrdoob/three.js/blob/9b4a2ac29c63ccb43fd51c5661f2f873ac2c39b8/examples/jsm/objects/Water2.js#L181) 与 [双相位法线段](https://github.com/mrdoob/three.js/blob/9b4a2ac29c63ccb43fd51c5661f2f873ac2c39b8/examples/jsm/objects/Water2.js#L343) | 半周期错相减轻纹理重置跳变；已有 Mesh 不会因此变成液体求解器 | 固定世界秒数计算有界表面输运；主体形状和运动规律另行定义 |
| [Waterways `FlowUVW`](https://github.com/Arnklit/Waterways/blob/cd2e2193fe2c7792efb4c35118ade3ad868538b4/addons/waterways/shaders/river.shader#L56) | 纹理流动、泡沫分区与已有曲面载体分别处理 | 让细水纹沿主体局部标架运动，海面与水柱分别求交、着色及遮挡 |

以上是从固定源码得到的设计依据。上游项目没有提供可直接替换本页的完整水龙卷；本页不是这些项目的运行复现。

## 改造入口与判断方法

原创入口位于 `workbench/vortex/preview.js`。`liquidBasisAt` 建立指定弯曲中心线的局部标架，`liquidLocalPoint` 以两次投影获得局部坐标；`vortexLiquidDistance` 决定液体在哪里存在，`liquidBowlProfileAt` 定义水龙卷外张上口与平滑内腔，`liquidPhaseAt` 组织宏观绕轴相位，`liquidSurfaceVelocity` 组合标架移动、提升和周向速度。`vortexWaterHeight` 负责根部海面，`makeWaterDrops` 负责离体碎滴。单独提高泡沫覆盖或放大流动纹理，不会修复实体间隙。

柱面以成熟高度固定节距 `k`，采用 `phi=theta-k*s-(rotation-k*rise)*time`。在局部 `dtheta/dt=rotation`、`ds/dt=rise` 的指定运动中，该相位一致；轮廓、粗法线与泡沫共享 crest，噪声使用周期 `sin/cos` 嵌入跨过周向分支。根部向心驱动是另外的艺术分量，中心线映射仍是有限投影近似，不把上述局部关系宣称为全局流体守恒。海面保留有界双相位输运；水柱不再另外叠一个速度不一致的双相位时钟。

改动按以下顺序判断：

1. 载体与轮廓：在不同高度检查小内核的连续占据，再检查三层薄水皮与局部片的重叠、薄厚及缺口；下段内核不能被稀疏角向遮罩切成窄条，也不宜用粗光滑内核代替全部水片。水龙卷顶部按新碗口合同检查空腔与环壁，旧全高度实芯标准不适用。
2. 螺旋：外轮廓、粗水纹与运动共用旋向和节距；检查周向接缝。形状相位与速度必须保持一致，不能分别调整后只凭一帧高光判断。
3. 光学与接口：同一水柱界面反透射共用细法线、Fresnel 和片缘局部泡沫，细水纹沿输运方向拉长，颜色由环境和有限光程吸收共同产生；海面肩部持续卷入，龙吸水顶部渐缩，水龙卷顶部外张为薄壳碗口；嘴沿局部泡沫与有限飞溅各有职责，使用顶部视角查看前后沿和内壁。
4. 时间：以固定秒数直接求值，暂停冻结形变与表面流动；相邻固定秒数用于观察上升和绕轴轨迹。
5. 范围：保护 `mode=0` 的陆地云漏斗，以及完整 `vortex-cloud/1` 外层与 `world-volume/2` 基础字段。

以下为 revision8 历史结果，由 [旧水体专项 GPU 数据](../../validation/vortex-revision8.json)、[旧实际预览](../../validation/vortex-revision8-visual.json) 与当时页面导出/导入检查记录。该历史 `preview.js` SHA256 为 `9b9d02fd6bbbe169da7d17a62f4a111b9677caa2a75c01617d42c2df38f4ae46`。专项 GPU 49/49 只认证载体与孤立界面权重；[旧通用页面](../../validation/vortex-layered-water8-page.json) 75/76 保留实际 PNG 下载的严格失败，八帧与作品保持检查 26/28 保留水龙卷成熟近景的 PNG/RGBA 严格失败。总辐射、多层水片的完整光路与自然外观没有因这些数值通过而获认证。 独立截图和运动抽帧复核仅支持减少胶块与白带、保留旋卷的相对改进，灰布感和薄片透视仍有限。图像比较、造型判断与文件完整性分开；`continuity7` 的专项 38/38、页面 75/76、八帧 18/26 以及更早的严格失败保留为历史证据，不认证本次水皮与光学。

本轮 revision9 的 [专项几何 GPU 数据](../../validation/vortex-revision9.json)、[实际预览](../../validation/vortex-revision9-visual.json) 和 [通用页面](../../validation/vortex.json) 均绑定 `preview.js` SHA256 `564aded79203ef477d1977373a735f14ae69be69b2044f068aced39566d6bf7f`。上口薄壳、平滑空腔和不规则卷边是原创形状；上沿中心线采用 `tanh` 的 C2 饱和延伸，表面速度和 CPU 滴挂点同步。最多 192 个主步进与 mode2 的 12 次符号跨越精修适配薄壁；嘴沿相机射线用密集占据扫描检查预算漏过表面的情况。mode0/1 与冻结 revision8 的浮点比较属于原始回归结果，不能用单帧近似颜色一致代替。碗口薄壳可见、严格重放与材质自然感分别判断。

最终专项 GPU 为 29/31，碗口与下段占据合同通过，但 mode0/1 两项冻结 f32 严格回归失败，最大差分别为 `9.5367431640625e-7` 与 `2.6702880859375e-5`。通用页面为 77/78，唯一失败为实际 PNG 下载与固定帧逐字节比较。九帧实际预览及作品保持为 17/31，所有帧 GL 错误为零，已有 82 个参数、相机与固定秒数保持通过；七帧的 PNG/RGBA 各失败一项，共 14 项，单通道最大差为 1–60。三个报告均保留 `passed=false`，没有用容差、低平均差或文件完整性改判。

本次[安装版独立试作](../../validation/vortex-portable.json)为 29/32，绑定同一最终源码。三模式、近景与顶部碗口实际 PNG、完整 JSON 的真实导出/导回、规范/安装版/新作品/HTTP 源码字节匹配，以及研究源码 ZIP 的 CRC、清单和源码字节检查通过；专属 8775 辅助服务已停止。水龙卷外部相机重置后固定帧、实际 PNG 导出对照固定帧、导入后的 PNG 严格重放仍有三项失败，原始报告保留 `ok=false`、`passed=false`。该结果只检查独立作品与文件来源，不认证自然水感或 Minecraft；旧版本便携结果继续保留自身范围。

通用探针保留完整颜色及离体滴路径，四个程序编译/链接合计 57.6045 秒；这不是生产页面冷启动。实际采集[耗时记录](../../validation/vortex-revision9-timing.json)复用了已编译场景，`reusedScene=true`，24 毫秒初始化及 117–235 毫秒渲染与 PNG/RGBA 采集不能保证实时 30 fps。一次超过 120 秒的完整颜色初始化失败仍保留，没有替换为复用场景的初始化成绩。宽灰面、灰布感与薄片透射不足仍需改善；新增水体仅实现于 WebGL，未获自然水体或 Forge 游戏认证。

海面肩部、片缘泡沫和碗沿泡沫的三个有符号高斯偏移，改用 `offset*offset` 计算平方。[Khronos GLSL ES 规范 8.2](https://registry.khronos.org/OpenGL/specs/es/3.2/GLSL_ES_Specification_3.20.html#exponential-functions) 规定 `pow` 的负底数结果未定义；修正保持预期的高斯表达式并处理定义范围，尚不能据此断言它是严格重绘差异的根因。

## 能力与复用边界

本次采用程序实体及艺术速度，没有压力投影、PBF/FLIP、真实质量交换、粒子断裂/合并或曲率白水求解。现有云、水面、液体与水滴有各自表面/体积流程；透明透射采用单次界面折射及有限占据光程，仍不表达任意多层水片的完整入水/出水光路。新的事件页水体尚未接入 Forge，基础后端旧验收不认证它们。

本 skill 仅携带上述研究推导和原创代码。实际引用/复用第三方代码时须按其固定提交与文件声明保留许可；所下载的原始 LICENSE 不因收录于研究资料就改为本 skill 的许可。Waterways 源码注释中的 helper 来源还需单独追溯；Tiled Directional Flow 的 MIT 与 Creditware 声明均保留在独立资料包，未将其 helper 或素材移入原创资产。
