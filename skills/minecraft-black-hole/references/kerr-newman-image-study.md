# 用户 Kerr–Newman Image pass 研究（2026-10-15）

后续更新：用户已提供作品链接 https://www.shadertoy.com/view/wXdfzj 及 Common、Buffer A–D，见 [核心与多 pass 研究](kerr-newman-core-study.md)。下文“未提供”描述首次仅收到 Image 时的证据范围，不代表当前仍缺源码。

## 证据与完整性

用户提供一份 718 行的 ShaderToy Image pass，首部自述 Kerr–Newman 时空渲染及最大延拓。本文仅依据实际函数，不将简介宣称作为已实现或科学正确的证据。原始文件保存在本机 `E:/着色器渲染/research/kerr-newman-20261015/user-image-pass.glsl`，SHA-256: `3f5dd8e0823d0169d99082cbdef73fcb914efa0a58a7d3c6fb9badd073c04480`；原作者与作品链接未提供。代码注明 Buffer B/C/D 与 Image 改编自 sonicether 的 Gargantua With HDR Bloom；许可未提供，不作为原创或授权公开分发源码。

实际文件包含 `MapKerrSchildRadius`、`RenderTopologyMap`、`PenTortoise`、`PenCameraPoint`、`PenInverse`、`RenderPenroseMap`、双三次采样与 `GetBloom/mainImage`。文件明确写明光追核心在 Common，全分辨率重建/合成在 Buffer A。没有给出度规/测地线积分、光子四动量、观者 tetrad、频移或 Walker–Penrose 常数的计算核心，也没有完整输入通道配置。不要把该单文件作为完整可运行 KN 渲染器。

## 可复用的具体方法

- 稳定 Kerr–Schild 半径：令 b=rho²+y²−a²，det=sqrt(b²+4a²y²)。b>=0 用 (b+det)/2；b<0 用 2a²y²/(det−b)，避免近环区域两大数相减。符号 r 分支由宿主状态提供，不从普通欧氏距离猜测。
- 本实现无量纲单位为 M=1/2：a=spin/2，Q²=q2/4，视界 r±=(1±sqrt(1−spin²−q2))/2。判别式为负时无这对实视界，不能用 sqrt(max(0,disc)) 画成真实视界。图中分支另外检查 disc。
- 拓扑图以侧视和俯视分别绘制视界、能层、奇环与观者；显示诊断层和主体渲染分离。真实科学标注须与求解器单位/坐标一致，现有美术黑洞没有 KN 物理参数，不直接挂上这套标签。
- `PenTortoise` 分非极端、极端、无视界三种解析式；紧致化使用 atan(exp(clamp(logValue,−25,25)))，并用 log/tan 逆映射。局部分支是轴向 (t,r) 截面的图示，不代表整个旋转时空被无损压成二维。
- 彭罗斯图按区域/宇宙 tile 滚动，利用相邻像素的逆映射差分控制等时/等半径线的线宽，在无穷远边界渐隐。可学习防止高密网格闪烁的方法；极端/裸奇点等分支的物理解释仍需独立检验。
- Bloom 从 iChannel3 的打包多尺度模糊图重建，八档权重 1/1.5/1/1.5/1.8/1/1/1，最终乘 .08；双三次插值合并为四次纹理读取。没有 B/C/D 的布局与生成代码，单独复制 CalcOffset/GetBloom 无法重建输入。
- 主色来自 iChannel0；iChannel1 用固定 texel 存相机、宇宙分支、rolling time 等状态：2/3 为相机位置/方向，5 为状态，14 为参数，17 为地图元数据，18 为图示事件。这是跨 pass 数据契约，不能当普通颜色纹理过滤。
- Image 的 pow/有理压缩/对比曲线/逐通道 grading 属于最终显示处理。Minecraft 若已有曝光或显示变换，不机械串联整套曲线，避免二次 tone map。

## 已做检查与应用边界

对 Schwarzschild、亚极端、极端、超极端和负 q2 五组参数，数值核对 PenTortoise 的导数与 (r²+a²)/(r²−r+a²+Q²)，误差小于 1e-7。记录见本机 `study-checks.json`。该小检查只确认所抄解析式的局部一致性，不验证完整最大延拓、积分收敛、偏振或物理准确性；未运行此 shader。

当前归入黑洞 skill 的研究参考，不替换默认美术内核，也不并入 Accretion。用户要求进一步复现时，需要同一作品的 Common、Buffer A/B/C/D、通道绑定和参数默认值，再建立零测地线约束、守恒量、已知极限和分辨率/步长收敛基准。负质量、虚电荷、跨宇宙属于理想化数学扩展，不等同于观测证实的天体行为。
