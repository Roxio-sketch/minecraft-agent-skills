# XorDev Accretion：湍流折光盘

用户于 2026-10-15 提供 ShaderToy WcKXDV 源码与截图。原作署名 @XorDev，题名 Accretion。
来源：https://www.shadertoy.com/view/WcKXDV
作者说明：https://x.com/XorDev/status/1936884244128661986

## 使用入口

当用户要求该参考的彩色湍流吸积盘、暗核周围的翻卷亮丝和环绕光带时，可使用维护者本机研究副本中的 assets/preview/accretion/index.html（许可未核实，公开 Skill 不含该目录）。运行现有 `python scripts/serve_preview.py --port 8771` 后访问 `/accretion/`；也可直接打开 HTML。保持现有黑洞默认预设与 Forge 渲染路径不变。

预览提供速度、曝光、湍流幅度、环半径、纵向比例、色带相位、暂停与配方导出。默认 960×540、时间 12.96 秒，20 步与 7 层扰动固定。导出的 `xordev-accretion-preview/v1` 仅描述本预览，不被现有黑洞配方或 Forge 自动消费；目前无配方导入接口。

## 算法与移植边界

极坐标重映射构造环形结构，7 层正弦域扭曲叠加 `0.3*i` 步进相位，再以距离倒数累积彩光、tanh 压缩。它没有背景 sampler，没有真实场景折射或引力测地线。步进序号参与场计算，因此不是与采样预算无关的固定三维密度场；改变步数不能宣称同画质质量档。

`accretion.frag` 是带 uniform 的参考适配，HTML 内嵌同份源码以支持离线打开；修改时保持两份一致。适配显式初始化 z、累积色与循环变量，距离钳制到 1e-5，以 exp 实现 WebGL2 可用的 tanh，固定输出 alpha=1。原参考未初始化局部变量的写法不照搬。不是现有透镜求解器的盘面参数替代品。

世界落地需要另行设计世界中心、射线、有限作用范围、深度遮挡、透明合成与 HDR 显示；物品内部可由 Cosmic 的窗口载体接入，但不能把此屏幕射线预览称为已完成物体局部体积。未实现 Minecraft 接入。

## 来源许可

保留作者和链接。用户提供的参考代码没有包含许可，尚未核实上游再分发或商用授权；此本地研究适配不标记为原创/MIT，不直接纳入公开发布包。需要发布时先核实来源许可，或独立重写并明确区别。

## 本地验证（2026-10-15）

WebGL2 在本机无头 Edge 完成编译/链接与出图，GL error=0；12.96→13.46 秒有 515025 个像素发生变化；speed=0 时不同时间逐字节一致。已目视对照用户截图，暗核、环带和两侧翻卷盘面保持参考结构。skill 元数据校验通过。证据位于 `E:/着色器渲染/evidence/accretion-20261015/preview.png` 与 `verification.json`，这些为本机测试记录，不作为 Forge 或其他硬件认证。

## 现有工作台的实机实验

2026-10-15 已另行接入本机 `E:/着色器渲染/vfx-workbench-lab` 的 Forge 1.20.1 工作台，形态 ID 为 accretion（uGranular=5），F6 可选或 `/blackhole form accretion`。采用固定世界中心、面向相机的有限平面效果，场景原深度在平面处裁剪；这是忠实视觉预览路线，不是沿任意观察角度成立的三维体积。投影由世界中心、尺寸及实际相机射线决定，默认不做环境透镜和独立粒子；盘外半径控制支撑范围。tilt 在此为面内旋转。

宿主新增 accretionTurbulence/Thickness/Hue 三字段；速度、曝光和共享播放时钟进入实际 shader。固定 20×7，不随旧 steps 字段变化。world/v2 已在本地宿主支持新增字段，原 skill 内嵌独立 Forge 模板没有同步此实验，浏览器独立配方也不自动导入宿主。

实机 shader 加载与截图通过，参考帧 changedPixels=90446、outsideChangedPixels=0、maxDepthDifference=0；并保留另一时刻截图。证据为工作台 `run/black-hole-lab/world-probes/accretion-reference*`、`accretion-motion*`。未验证第三方光影、全部相机位置和前景实体覆盖。
