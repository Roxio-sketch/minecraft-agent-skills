# GitHub 体积云与龙卷源码精选

整理日期：2026-10-11。目标是为“螺旋感弱、间隙过多”的下一轮水龙卷改造提供可追溯源码。本目录只保存静态阅读材料，没有执行或移植下载代码，也没有修改工作台。

## 精选仓库

| 项目 | 固定版本 | 本地阅读入口 | 实际价值 |
| --- | --- | --- | --- |
| Habrador / Unity-Tornado-Simulator | `46f0a76885203562d8203e037fd8f3660e931537` | [TornadoDust.cs](unity-tornado-simulator/Assets/Scripts/Tornado/TornadoDust.cs)、[Tornado.cs](unity-tornado-simulator/Assets/Scripts/Tornado/Tornado.cs) | 弯曲中心线、按高度取半径、绕中心旋转、顶部扩散并接入云层 |
| SebLague / Clouds | `fcc997c40d36c7bedf95a294cd2136b8c5127009` | [Clouds.shader](seblague-clouds/Assets/Scripts/Clouds/Shaders/Clouds.shader)、[NoiseGenCompute.compute](seblague-clouds/Assets/Scripts/Clouds/Noise/Compute/NoiseGenCompute.compute) | 宏观密度与细节侵蚀分离、有限包围盒积分、云内自阴影、Worley 体积噪声生成 |

两个项目的所选源码均附仓库原始 MIT LICENSE。目录中没有第三方 Standard Assets、3rd Party、模型、预烘焙噪声、截图或视频。所选文件保持原始字节；路径、SHA256、字节数、阅读行数和函数锚点见 [manifest.json](manifest.json)。这是源码精选，不是可直接运行的完整 Unity 工程。

## 1. Unity Tornado Simulator：从弯曲骨架得到一致的旋转主体

[固定提交源码](https://github.com/Habrador/Unity-Tornado-Simulator/tree/46f0a76885203562d8203e037fd8f3660e931537)明确拆成主体粒子、地面尘、顶部粒子与背景云。`TornadoDynamics` 通过分层弹簧和阻尼让中心线随时间弯曲；`TornadoDust.RotateParticles` 在粒子的高度上插值中心线，按形状曲线取得目标半径，再沿圆周更新角度。顶部由独立 `TornadoDustTop` 放宽半径并逐渐减速。[主体函数](https://github.com/Habrador/Unity-Tornado-Simulator/blob/46f0a76885203562d8203e037fd8f3660e931537/Assets/Scripts/Tornado/TornadoDust.cs#L96)、[顶部函数](https://github.com/Habrador/Unity-Tornado-Simulator/blob/46f0a76885203562d8203e037fd8f3660e931537/Assets/Scripts/Tornado/TornadoDustTop.cs#L67)

对漏斗的可用部分是“中心线 C(y,t) + 半径 R(y) + 绕线角度”，尤其是移动中心线后粒子跟随位移的处理。螺旋纹理、外轮廓和飞溅应共享这个局部坐标，避免它们分别绕不同中心转动。

注意：这是粒子龙卷，不是体积密度漏斗或液体表面求解器。`RotateParticles` 保存原来的 Y；上升运动依赖未收录的粒子配置，不能仅凭此函数声称实现了水的上升。`CheckRadius` 先纠正半径，随后轨道函数仍将粒子放在指定圆周上。它不保证水体连续或体积守恒。`GetParticleVel` 本目录没有发现调用，不应把它当作已验证的速度场。背景 `Cloud.shader` 在龙卷附近增加底部云密度与颜色；它的噪声平移不是螺旋域变换。[云层关联](https://github.com/Habrador/Unity-Tornado-Simulator/blob/46f0a76885203562d8203e037fd8f3660e931537/Assets/Material/Weather/Cloud.shader#L308)

## 2. Sebastian Lague Clouds：保留宏观主体，控制侵蚀位置

[固定提交密度函数](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/Shaders/Clouds.shader#L165)先计算低频基形，再用随基形减弱的权重减去细节噪声。中心更密，边缘更容易被侵蚀。视线被深度和有限包围盒截断；向光源积分相同的密度，最终用背景透射加云散射合成。[光照积分](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/Shaders/Clouds.shader#L218)、[视线积分](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/Shaders/Clouds.shader#L280)

对云龙卷的可用部分是把噪声采样坐标先送入漏斗局部螺旋坐标，再计算密度；对水龙卷的可用部分是“主体与边缘细节分离”这一结构。液体主体应先有连续几何，再在外壳做小幅扰动，不能直接把云的密度减法当作水片的切洞规则。

局限：原项目的风是笛卡尔坐标平移，没有漏斗、螺旋或龙吸水逻辑；高度包络面向云盒，不适合原样用在柱状水体。密度函数可能给出负细节结果，调用点再筛选/夹到非负。Worley 代码只搜索本单元及 26 邻居；这是一种教学近似，不能宣称对任意随机点布局全局最近。[Worley 生成](https://github.com/SebLague/Clouds/blob/fcc997c40d36c7bedf95a294cd2136b8c5127009/Assets/Scripts/Clouds/Noise/Compute/NoiseGenCompute.compute#L67)

## 3. 下一轮应采用的组合逻辑（本研究推导，尚未实现）

以下是结合两类源码后针对当前效果的设计推导，不能归为上游已有水龙卷实现：

1. 先建立连续漏斗实体。围绕 C(y,t) 计算局部半径 r，用 R(y) 定义占据区。大部分主体保持完整；边缘水片与飞溅是附加形状，不能把主体整体乘以稀疏的角向遮罩。
2. 用有明确节距的螺旋相位 `φ = θ − k(y) − ωt`。螺旋感来自随高度变化的相位，而不只是让细碎噪声在水面移动。可用宽而连续的相位带调制外轮廓厚度、表面粗糙度和泡沫，减少窄条之间完全透明的缝隙。
3. 让上升和旋转遵守同一个相位。若希望纹理随速度场对流并维持上述相位，应满足 `vθ / r = ω + k′(y) vy`；否则几何、泡沫与纹理会出现相互滑动。这里是移动中心线/局部标架中的相对速度；更新世界位置还需加上中心线和标架自身运动。此式不是质量守恒条件。中心附近必须避免除以零，并限制速度。
4. 主体与剥离区使用不同规则。主体只接受有限幅度的表面扰动；较大的孔洞局限于外围薄片、顶部破碎区。云体版本可以侵蚀密度，但要保留宏观核心密度；水体版本必须保留连续 SDF 或网格实体。
5. 多层水片共享中心线和旋转方向，允许不同节距、相位和高度范围，并保证覆盖重叠。先看实体轮廓和螺旋高光是否成立，再增加稀疏飞溅。顶部逐步扩散、减速并接云层，避免一根硬水柱突然截止。

验收应分别观察主体连续性、螺旋节距、沿螺旋的移动、根部卷入和顶部剥离。上述两仓库没有水龙卷液体重建、折射多界面或海面质量交换，本轮资料不为这些能力背书。

## 读取与获取范围

2026-10-11 通过 GitHub 仓库主页和固定提交的源码页确认项目；GitHub API 返回限流后，用 `git ls-remote ... HEAD` 固定提交，通过固定 SHA 的 `raw.githubusercontent.com` 获取 15 个精选文件。两项目所收录源码、README 和 LICENSE 全文静态阅读；未运行 Unity、shader、C#、compute kernel 或任何下载的脚本。完整工程、未下载资源及其可运行效果没有验收。
