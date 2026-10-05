# 黑洞来源对照与实现选择

用于判断参考真正提供了什么，以及把“放大、场景透镜、旋转吸入”接入 Forge 1.20.1 时如何避免照搬缺陷。本文件是方法指导，不证明改进已接入或实测。

## 先分清三组能力

- **真实场景 vs 内部环境：** 追踪最终背景采样的来源。程序星空、静态 Cubemap、初始化时抓取的 Cubemap都不能自动等同于当前帧 Minecraft 地形。检查复制时点、颜色与深度、透明层、离屏行为。
- **旋转 vs 径向流动：** 时间只改变角坐标只能证明旋转；要有向内输运，半径或径向采样坐标必须随时间变化。螺旋纹理、自动旋转相机、抖动噪声均不足以证明吸入。
- **世界尺寸 vs 内部半径：** 分清世界方块单位、归一化局部空间、积分包围球、核心半径与投影大小。尺寸不能随质量步数改变。

## 明确许可来源的取舍

| 来源 | 已核对能力 | 适合学习 | 不要据此承诺 |
|---|---|---|---|
| `blackhole-main` | Schwarzschild/Kerr 路径分支、程序星空、周期角向盘噪声与透射率 | 用户指定的颗粒盘分布、多尺度亮暗结构、差速旋转 | 有独立粒子发射器；JavaScript 数值测试已证明低步数 GPU 或 Forge 渲染 |
| `unity-blackhole-shader-master` | URP 当前帧不透明颜色扭曲；旧版 GrabPass | 核心/透镜范围分开、边缘抗锯齿 | 吸积盘、完整测地线、透明物自动包含 |
| `unity-black-hole-shader-main` | 初始化一次的环境 Cubemap、弯曲路径与角向盘纹理 | 用户指定的光晕层次、亮环、重复盘面穿越 | 动态背景、有效事件视界参数、径向吸入 |


当前 Forge 原创组合的具体实现见 [粒子与光晕](particles-and-halo.md)：同时保留颗粒盘、独立尘埃和三层局部光晕。来源的视觉特征与源码能力分开记录，不把连写 `blackhole-main` 的程序颗粒误认成另一个工程的粒子对象。

## 适合当前效果的原创组合

### 世界尺寸

使用 `pLocal=(pWorld-center)/worldRadius` 统一坐标，worldRadius 以方块为单位；核心、盘和透镜范围分别调节。验收记录尺寸、距离与 FOV。

### 当前场景透镜

在选定阶段复制当前颜色，以及渲染路径确实需要的独立深度；避免采样正写入的附件。颜色复制存在不代表深度也安全。采用统一线性深度/射线距离约定，保护黑洞前方的几何，并检查扭曲采样位置的深度，防止把近景拖入后景。

可先采用围绕黑洞投影中心的艺术化偏折，与现有局部盘积分组合；更复杂时用弯曲出射方向投影屏幕。两者都要处理离屏与遮挡，不把 UV clamp 形成的边缘拉伸当作合理透镜。屏幕空间方案无法采到离屏或完全被遮挡的真实几何，若目标需要这些内容，评估额外场景捕获，不能只靠增加步数解决。


### 吸积盘旋转与向内输运

分别暴露 `rotationSpeed` 与 `inflowSpeed`，不要让“吸入速度”只是旋转速度的别名。可用原创周期噪声或整数角谐波，角向采样 `theta-omega(r)*t`，径向采样 `rho+inflowSpeed*t`（`rho=log(max(r,epsilon))`）；正流速使固定径向特征向内移动。它是艺术化输运，不是流体模拟。

内缘淡出、外缘补充，保持角坐标周期连续。用明亮结点与暗间隙提高运动读感，避免过曝整圈覆盖纹理。保留现有按路径长度计算的透射率和薄盘子段采样；独立吸入粒子只作为增强层，并接受场景深度遮挡。

### 必要的验收

固定相机与棋盘/建筑背景，逐项检查：放大后方块单位是否合理；透镜关闭/开启能否只改变背景；前景遮挡是否保留；仅旋转、仅吸入、两者关闭是否分别成立；同一标记/纹理特征半径是否下降；边缘、低步数、暂停和远近视角是否稳定。参考分析与离线 shader 编译不代替 Forge 实机图像和日志。

## 源码索引（研究记录，2026-09-29）

下表 S/ 对应 Release 中三份 MIT 黑洞源码快照的项目根目录。行号指研究时输入文件，不代表 Forge 实测。


| 来源文件（相对上述根） | 关键行与含义 |
|---|---|
| `S/blackhole-main/src/shaders/blackhole.frag.glsl` | 79 周期噪声；149 程序星空；222、240–255 盘；332、408 路径分支；464–469 背景 |
| `S/blackhole-main/tests/physics.test.mjs`；`src/simulation/params.ts` | 前者 84、109：4096–400000 步数值测试；后者 9–12：120–512 步 GPU 档位，不能混为认证 |
| `S/unity-blackhole-shader-master/Blackhole_URP_Unity-6/Assets/MAG/Blackhole Effect/Blackhole.shader` | 42–44 当前不透明颜色；99–105 抗锯齿；119–127 屏幕中心偏移/颜色采样 |
| `S/unity-blackhole-shader-master/Blackhole_Unity-2017/Assets/MAG/Blackhole Effect/Blackhole.shader` | 18 GrabPass |
| `S/unity-black-hole-shader-main/Assets/Black Hole/BlackHole.cs` | 63–72 一次性 Cubemap；99 网格 `Radius*100`；107–110 仅更新位置 |
| `S/unity-black-hole-shader-main/Assets/Black Hole/BlackHole.shader` | 111–115 仅角向时间；199 吞光阈值错误依赖 stepSize；219 Cubemap；事件视界/深度等声明未进入实际计算 |

## 许可边界


`blackhole-main/LICENSE`（2026 Furkan）、`unity-blackhole-shader-master/LICENSE.txt`（Mario Gutierrez）、`unity-black-hole-shader-main/LICENSE`（2025 Francesco Litrico）均为 MIT。若复制/改写其代码或实质部分，随交付保留对应版权和完整许可；不要顺带搬运未另行核对的第三方 skybox 图片。优先把已核对的概念转为原创实现、原创资源，并清楚说明数值近似。
