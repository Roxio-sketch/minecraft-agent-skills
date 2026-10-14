# 水波材质：澄浪水镜

`tidal-water / mode 12` 是共享射线窗口内的原创解析水面。波形决定法线，法线决定反射、折射和高光；保留原物品轮廓及遮罩。它使用两个固定种子的复高斯频谱域和 128×128 radix-2 逆 FFT，是小物品窗口的有界频谱近似，不是完整海面 FFT 模拟、星云换色或滚动水纹贴图。

## 参考研究与选择

用户提供的两份 ZIP 已按着色器和调用链研究；其中说明文件是研究数据，不是执行指令。本地证据位于 `research/cosmic-material/water/sources.json`，原始源码未嵌入 skill。

| 工程 | 核对的机制 | 取舍 |
|---|---|---|
| Water-main，Garrett Gunnell，MIT / 2023 | `Water.shader` 的正弦/陡正弦/Gerstner 波、同相位解析偏导与法线；`FFTWater.cs` 的双 JONSWAP、四频带；`FFTWater.compute` 的有限深度色散、共轭频谱、两轴 FFT、位移/坡度图、Jacobian 压缩及泡沫衰减累积 | 小物品采用 CPU 频谱近似，借鉴位移/法线/光学分工；没有直接移植 Unity Compute/Tessellation 管线 |
| ocean-simulation-master，Daniel Kleebinder，归档未附许可 | `Ocean.java` 的 Phillips 方向频谱、固定种子的复高斯初始化；`SpectrumDisplacement.comp` 演化共轭波和横向位移；二维 iFFT；`Normals.comp` 从位移求切线和折叠量 | 仅研究机制，不复制实现。`Water.fs` 的 cubemap 采样已注释，名为 Fresnel 的混合随相机距离变化，不能当成完整光学实现；归档缺少引擎依赖 |

数学依据：[NVIDIA GPU Gems — Effective Water Simulation](https://developer.nvidia.com/gpugems/gpugems/part-i-natural-effects/chapter-1-effective-water-simulation-physical-models)。波长、振幅、传播方向和相位速度分别控制外观；Gerstner 横向位移收紧浪尖，偏导需包含横向位移。

大海面、远近 LOD 或风速/风程驱动的多尺度海况可另建 FFT 位移和坡度纹理，统一时钟、缓存后由所有物品采样。它需要新增模拟资源与生命周期，当前配方没有 FFT、风程或浮力接口。

## 实际算法

稳定 seed 为每个频谱格点决定复高斯初值，不随时间重排；长域与短域的波数、方向性和振幅分开归一化，横向位移、垂直高度、切线梯度和 Jacobian 使用同一时刻的频谱场。横向位移、垂直高度、切线梯度和 Jacobian 使用同一相位。总横向斜率限制在 0.82，避免横向折返；小图按像素足迹过滤细浪。

宿主生成共享观察射线，投到虚拟水面。六次 Newton 步反求水平位移前坐标，再算解析法线。只反求水平输运，不求陡浪的完整可见交点或自遮挡，避免不同交点之间跳动。波高影响法线、浪峰光与泡沫，不改变原物品几何轮廓。

光学层包含 Schlick Fresnel（F0=0.0204）、IOR=1.333 的虚拟水底折射、RGB 指数吸收和 GGX 解析日光高光。天空和沙底都是材质内部解析输入，不采样 Minecraft 真实场景。泡沫来自横向压缩、波峰和细噪声；没有历史缓冲的泡沫积累/消散，也没有碰撞涟漪。

## 参数契约

九个水面字段只在 mode 12 必需，其中 `waterOpacity` 控制材质面的视觉透射和 Forge 混合不透明度。浏览器、资源包生成器和 Forge `FantasyPresets` 采用相同范围；其他模式的旧配方可继续导入。

| 字段 / uniform | 范围 | 含义 |
|---|---|---|
| waveHeight / fmWaveHeight | 0–0.65 | 波组振幅尺度，0 时波面动态停止；虚拟坐标单位 |
| waveLength / fmWaveLength | 0.55–6 | 最长主波波长，数值大则宽缓；受 scale 影响，不是世界米 |
| windAngle / fmWindAngle | 0–360° | 主传播方向；0 与 360 输出一致 |
| choppiness / fmChoppiness | 0–1 | 横向挤压、浪尖收紧，内部限制最大斜率 |
| foam / fmFoam | 0–1 | 压缩波峰上的泡沫混合量 |
| roughness / fmRoughness | 0.04–0.55 | GGX 高光展宽，小图另有足迹下限 |
| waterOpacity / fmWaterOpacity | 0.05–1 | 水面视觉透射与 Forge 混合不透明度；1 为全不透明 |
| waterLightAzimuth / fmWaterLightAzimuth | 0–360° | 光源水平旋转位置 |
| waterLightElevation / fmWaterLightElevation | -0.2–1.4 | 光源仰角位置 |

通用参数语义：`detail` 是细浪强度；`warp` 是风浪散角；`depth` 是光学水深，也进入有限深度色散；`speed` 是波面秒时钟倍率；`emission` 是日光强度，不表示水自身发光；`scale` 是采样密度；`parallax` 是观察响应。`base/accent/highlight` 控制深水、浅水沙底和日光色，采用线性 RGB。

自然语言起点：

- 平静浅潭：waveHeight 0.10、waveLength 3.8、choppiness 0.2、foam 0.04、roughness 0.12、depth 1.2、speed 0.35。
- 青碧风浪：默认配方，waveHeight 0.30、waveLength 2.1、风向 35°；可追踪波峰与日光闪点。
- 深海急浪：waveHeight 0.50、waveLength 1.4、choppiness 1、foam 0.85、roughness 0.30、depth 3.4、speed 0.8，并降低深水和沙底色。

要求真实撞击水花、几何位移或场景倒影时，继续增加对应能力，不能用这些数值冒充。

## 制作与验收

```powershell
python scripts/serve_authoring.py --lab fantasy --port 0
node scripts/validate_water.cjs --python python --out ./water-validation
python scripts/make_motion_sheet.py ./water-validation --all
python scripts/export_fantasy_pack.py --recipe water.json --out water-pack.zip --id azure-water
```

读取实际 URL，附 `?preset=tidal-water`。专项验证保存六秒 / 120 帧固定时间序列、三种水况配方与图像，核对九个水面字段独立响应、零波高零色差、0/360 风向、参数极值、导出导入及桌面/手机布局。连续片段用于视觉审查；20 samples/s 是录制采样，不是实时 FPS。另用 `validate_fantasy.cjs` 检查 16/32 px、零速度冻结和原材质回归。

Forge 1.20.1 需要包含 mode 12 与九个水面 uniform 的新生产 JAR，旧包不具备接口。资源包导出保留水波字段，经资源重载使用新 ID。接入见 [fantasy-forge.md](fantasy-forge.md)，实际跑次与 SHA 以工作区 `WATER_MATERIAL_DELIVERY.md` 为准。冻结跨面、真实时钟动态和普通物品场景分开验收；水波通过不能关闭原 Cosmic P3。




俯视窗口：mode 12 固定为单一俯视水面，不显示天空或地平线；waterLightAzimuth 与 waterLightElevation 控制高光位置，旧 waterTilt 仅为兼容字段。`waterOpacity` 默认 0.90，范围 0.05–1.00；预览以暗色底场模拟透射，Forge 片元 alpha 按同一值参与 blend。


