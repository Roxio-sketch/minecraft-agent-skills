# 白洞特效：算法与源码资源备忘

核查日期：2026-10-07（Asia/Shanghai）。目标为现有黑洞 skill、Forge 1.20.1、Java 17、GLSL150。这里只整理资源和迁移路线，没有实现或验收新的白洞特效。结构化资源卡见 `technical-sources.json`，每个固定源码文件的 URL、SHA256 和受限摘录见 `technical-verification.json` / `snapshots/technical-*`。

## 推荐的资源组合

| 层 | 优先资料 | 能直接用到什么 | 迁移成本 |
|---|---|---|---|
| 向外排出与卷曲 | PSRDNoise3 + Bridson Curl-Noise | 三维旋转梯度、解析导数、涡卷速度 | 低到中；Java 固定 tick 粒子需配等价场 |
| 排出盘 / 羽流结构 | IQ domain warping（算法学习）+ MIT PSRDNoise | 三维纹理域扭卷、连续气丝、细节密度 | 低到中；原创域变换 |
| 明亮但有轮廓的体积 | PBRT emission/absorption 关系 | 正确步长权重、发光 L 与透射 T 分离 | 中到高；复用本机有限体积合同 |
| 便宜的双向喷流核心 | three.js LineMaterial | 世界宽度、视向带状展开、端点处理 | 中；Java ribbon + GLSL 材质 |
| 受控发光与模糊 | three.js HDR bloom / 可选 KinoBloom | 浮点发光附件、soft knee、多分辨率过滤 | 中到高；先扩已有 selective bloom |
| 爆发脉冲 | postprocessing ShockWaveEffect | 半径、厚度、幅度、时间衰减 | 屏幕层低；360° 世界壳中 |
| 未来更精确的透镜 | Bruneton Black Hole Shader | 偏折查表、薄盘交点、星点足迹过滤 | 高；非首版必要 |

第一版建议只组合：现有黑洞定位/透镜基础 + 发光核心 + 向外流动盘/粒子 + PSRDNoise 细节 + 受控独立辉光。双喷流与爆发波作为可开关层。先不用新外部 Minecraft 渲染库，也不把白洞任务扩大成重写全部透镜。

## 重要核查结果

1. **Bruneton 并非 GPLv3。** 固定 commit `e72b3f293409893a6fa25528b29572c96fc57f57` 的 LICENSE 是 BSD-3-Clause，README 明确 BSD。源码可按该许可条件适配，但它并不是现成白洞；不要借其名字宣布科学准确。
2. **postprocessing 并非 MIT。** 本次固定 commit 的 LICENSE.md 是 zlib。改动后的源代码要明确标识，保留许可，不误报作者或源码来源。
3. **PSRDNoise3 比普通 fBm 更贴题。** 它提供旋转梯度与一阶解析导数，接口为 `psrdnoise(vec3, vec3 period, float alpha, out vec3 gradient)`。README 与 GLSL 头部是 MIT；根目录没有独立 LICENSE 文件。普通 `webgl-noise` 是轻量底噪备选，不能称其具有同样的旋转梯度/导数能力。
4. **Bridson 的许可必须分对象。** 作者主页将链接的 curl-noise example code 明确标成 public domain；论文与视频/图片并不自动获得相同复制权限。此次读取了论文 3 页文字，仅保存短摘录，没有取得样例源码包或影像。
5. **IQ 文章按算法学习处理。** 未核定文章样例代码的开放许可证，不能凭能浏览就复制。采用明确 MIT 的噪声，自行写域变换、包络、配色即可。
6. **KinoBloom 只是可选算法。** LICENSE.md 为 MIT；它是 Unity 5.5+ 的 image effect，本轮没有验证其与 Forge 的任何直接兼容性。只研究 `Kino/Bloom` 文本，示例工程内 skybox、纹理、图片与其他插件不在复用范围内。

GitHub API 返回速率限制 HTTP403，本轮没有把失败结果当证据；固定 commit 来自 `git ls-remote ... HEAD`，各关键 raw 文件与实际 LICENSE 均成功 HTTP200。证据总共 27 项，没有 clone 整仓或抓取纹理/视频。

## 能落地的算法骨架

### 1. 排出、旋转、卷曲各自可调

粒子位置始终使用效果固定的世界中心。源粒子从有限壳面或盘面稳定出生，沿年龄向外排出，角向速度独立；再叠加有上限的 curl 扰动。固定 seed 决定结构，tick/秒数决定推进，partial tick 只插值。不要以每帧随机抖动制造喷涌。

Curl 可以从三个独立标量噪声构成向量势 `A`，使用 PSRDNoise 解析导数计算 `curl(A)`。需要严格边界时应在势函数层施加调制；直接把已有 curl 乘上任意空间包络一般不再 divergence-free。实际白洞排放本就需要径向源项，因此首版可以明确采用美术化 `radial + swirl + bounded curl`，不宣称整个场是不可压流体。

纹理演化参数 `alpha` 控制梯度转动，不是粒子真实平流；盘角旋转、径向排出、噪声演化不能只用同一个 speed 表达。对噪声坐标缩放/旋转后计算 curl 时，导数必须应用链式法则。

### 2. 核心不应只是把黑色改成 RGB=10

核心可采用近白源色，但边缘、排出盘、粒子仍保留可辨颜色。体积结构采用有限球壳 / 盘 / 双锥包络与独立 emission、extinction。对每一小段：

```text
Tseg = exp(-sigmaT * ds)
L += T * j * (1 - Tseg) / sigmaT
T *= Tseg
```

其中 `j` 在此原创实时设计里是单位长度的发射源项；`sigmaT` 接近零时取连续极限 `j * ds`。不要把该自定义单位直接套在 PBRT 的每个 `Le` 字段上。最终合成为 `scene * T + L`，光与透射分开，才不会在提升步数后无故变亮。

先与有限域求交，再按场景最近不透明深度缩短射线；相机在域内也要积分。原版水/玻璃的分层深度不是一个不透明深度附件能恢复的，接入时沿用现有透明层限制。无需把 PBRT 的 spectral sampling、NanoVDB 或离线随机积分器直接迁到 GLSL150。

### 3. 双向喷流先做简单核心，再做体积外壳

固定轴正负方向各一条渐宽带，沿程强度/宽度有单独曲线。ribbon 朝向相机时，视线近乎平行喷流轴会导致叉积基底退化；需要稳定的替代轴或交叉带。远端柔和淡出、接头抗重叠，不让核心变成两条无限长的激光。

LineMaterial 的算法可参考，但 JS、three.js shader chunks 和宿主 uniforms 都要重新定义为 Forge 接口。外层才按质量档加有限双锥 emission 体积，低档保持带状核心与少量粒子。轴向喷流是美术选项，不是由白洞理论必然推得的固定形状。

### 4. HDR / Bloom 与显色分别处理

先在独立浮点 emission 附件积累核心、盘、喷流；Bloom 从该附件提取并过滤，不把整个原版世界重新曝光。参考 Kino 的软阈值 `soft knee`、一个标量缩放 RGB 的预过滤与多级模糊；保持三个阶段清楚：发光源、扩散、显示合成。

Bloom 不能恢复先前被 `clamp(0,1)` 截白的颜色。Kino 的 `SafeHDR(..., 65000)` 是 half-float 安全范围，不能误搬成 LDR clamp。three.js 的 ACES/AgX/Neutral 也会有自身高亮去饱和倾向，不能仅按“有 tone mapping”判定保色已解决。

如果当前 Minecraft 主附件仍为 LDR，优先只对发光贡献做受控显示压缩、把彩色边缘与近白核心分层，再与宿主场景合成；不要改变世界其余像素的显示映射。屏幕光晕与真实表面照明仍是两件事，有需要才额外接动态光。

GL_RGBA16F 是候选资源格式，不在本轮宣称目标 GPU/驱动已经实测支持。FBO resize、attachment 过滤、资源销毁、读写隔离、blend/scissor/viewport/texture 状态恢复都必须照既有合同实施。

### 5. 脉冲波与透镜保持独立

ShockWaveEffect 本身是投影中心周围的屏幕 UV 波，只能当短暂附加层。它不会自动成为任意角度连续的世界球壳，也不能拿来替换之前已修好的 360° 环境透镜。需要世界冲击波时，以原创有限球壳的密度或几何开始，屏幕折射按需开启。

白洞不是负质量或反重力。若保持 Schwarzschild 外部几何，黑洞与白洞的时间反演主要改变边界/源项和物质流向；不能简单把透镜强度取负而称科学白洞。艺术路线可以另提供“向外扭曲”风格，但名称与物理含义应明确区分。

## 将来实施时的最低验收

- 固定中心不随玩家位移；逆 VP 光学眼点、水平默认盘和玩家透镜参与开关保持原合同。
- 近 / 远、正面 / 侧面 / 喷流轴向、俯视 / 仰视、进入体积分别检查轮廓与深度。
- 零 emission 不发光、零 density 不遮世界、域外恒等；步数增加时用颜色/轮廓对照验证收敛，不能只比较平均亮度。
- 粒子 seed/time 固定重放；源头、寿命、淡出、环形接缝稳定。
- 明亮核心、彩色边缘与辉光分别采样；开关 Bloom、改曝光后检查没有全层一起冲白。
- 遮挡、玩家参与、云层绘制阶段和 GL 状态在真实 Forge 环境重新取证；本轮研究不继承任何旧版本的实机认证。

Shimmer、Veil、Lodestone、Photon 本轮没有必要作为外部依赖加入；没有核它们的具体 Forge 1.20.1 包、构建与许可，所以资源清单不声称它们兼容。已有黑洞/粒子/体积/选择性 Bloom 资源足够支持首版路线。
