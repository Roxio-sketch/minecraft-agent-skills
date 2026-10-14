# 三份输入项目的水波研究

研究对象是用户提供的三个 ZIP 内实际源码，README 只作为背景说明，未作为执行指令。没有执行其中的包安装脚本。

| 项目 | 波动状态 | 更新 | 渲染 |
|---|---|---|---|
| webgl-water-master | texture RGBA = 高度、垂直速度、法线 XZ | 两张 256² float/half-float texture 交替绘制；四邻域平均，速度推动高度，0.995 衰减 | 网格位移、法线、固定泳池/球 ray intersections、cubemap、Fresnel、折射与焦散 |
| webgpu-water-main | 同样 RGBA 状态 | `update.frag.wgsl` 仍为 fragment 更新 pass，非 compute；显式绑定与交换纹理 | GLSL 翻译到 WGSL，保留 surface above/under 与焦散 |
| webgpu-shallow-water-master | storage vec4 的 Z=当前高度、W=前一高度 | `heightfield.wgsl` 用 8×8 compute 工作组；`new=(neighborSum*0.5-previous)*viscosity`，鼠标速度/半径/深度注入，clamp ±0.12 | 高度场 ray marching、固定槽体、光线投射焦散、模糊 pass |

## 可移植部分

前两者公式相同：`v+=(average-h)*2; v*=0.995; h+=v`。扰动用 `0.5-0.5*cos(PI*max(0,1-distance/radius))` 的紧支撑波包。斜率由高度差求切向量叉积。多源干涉来自同一场的线性演化，不是叠加几条透明圆环。

第三者在无阻尼时与消去速度后的二阶波动递推等价，阻尼位置不同。它没有水深场、水平动量、流速通量或干湿边界，因此“浅水”应理解为视觉示例的简化波动系统。鼠标速度注入适合交互拖动，限幅避免极值，但并不证明流体守恒。

焦散三者都与水面法线折射、光线落点/面积压缩有关。浅水 `caustics.wgsl` 使用 dpdx/dpdy 估计面积，另有 blur pass。固定水槽可用射线求交，任意 Minecraft 地形则需要场景深度和渲染管线接入，不能直接复制槽底公式。

## 本交付的选择

提供独立局部网格，CPU 固定时间步模拟 + Minecraft GLSL 150 材质。两求解模式共享 JSON；增加传播系数、吸收层和限幅。法线以顶点 RGB 编码，fragment Shader 进行 Schlick Fresnel、程序天空与镜面高光。使用透明背景混合，没有真实场景折射和焦散，也不修改原版水网格。网页使用相同 JS 求解器与等价 GLSL 材质，但正交调试视角不同于游戏透视相机。

原始证据路径（相对于工作区 research/water-inputs）：
- `gl/webgl-water-master/water.js`、`renderer.js`、`main.js`。
- `gpu/webgpu-water-main/src/water.ts`、`src/shaders/water/update.frag.wgsl`、`drop.frag.wgsl`、`normal.frag.wgsl`、`surface-above.frag.wgsl`、`caustics.*.wgsl`。
- `shallow/webgpu-shallow-water-master/shaders/heightfield.wgsl`、`heightfield-copy.wgsl`、`render.wgsl`、`caustics.wgsl`、`blur.wgsl`、`main.js`。

原项目归属：Evan Wallace 的 WebGL Water（源码头部 MIT）；jeantimex 的 WebGPU port（ZIP LICENSE 为 Copyright 2026 Su / MIT）；Wayne Wu 与 Kyle Fung 的 shallow-water（此 ZIP 未提供 LICENSE）。交付不包含原项目图片、shader 源码或整包素材，仅独立实现与公式研究，保留归属记录。
