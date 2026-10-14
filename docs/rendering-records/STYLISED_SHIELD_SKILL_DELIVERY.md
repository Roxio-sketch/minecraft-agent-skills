# Stylised Shield FX 交付说明

本交付根据 `shaders-stylised-shield-main` 的 Unity 2021 URP Shader Graph 和 `EnergyShield.cs` 行为改写，保留 MIT 许可。原图的主要模块是 Edge Glow、Scanlines、Big Scanline、Hexagon Cloud/Heightmap、Glow Noise、Collision Ripples 与 Intersection。

交付内容：

- `skill/minecraft-stylised-shield/SKILL.md`：可复用的 Minecraft Forge 1.20.1 特效 skill。
- `skill/minecraft-stylised-shield/assets/forge-1.20.1/`：Java 17 / Forge 47 工程，包含独立矩形护盾网格、透明 RenderType、GLSL 150、配置读取和客户端命令。
- `skill/minecraft-stylised-shield/assets/forge-1.20.1/workbench/index.html`：无需依赖的 中文 HTML Canvas 调参器；右侧护盾支持拖动旋转、滚轮缩放和形状预设，每个滑块都会直接影响矩形护盾预览，可编辑颜色、Fresnel、六边形、扫描线、发光噪声、波纹并导出同格式 `recipe.json`。
- `skill/minecraft-stylised-shield/assets/forge-1.20.1/recipe.example.json`：示例配置。

使用：把 `recipe.example.json` 复制到实例 `config/shieldfx/recipe.json`，启动客户端后执行 `/shieldfx demo`；`/shieldfx reload` 重载配置，`/shieldfx hit` 重置受击闪白、扩散冲击环、放射火花和短暂亮度脉冲；工作台还会显示 3D 火星粒子，`/shieldfx off` 清除。

实现说明：护盾由 32×24 矩形护盾网格（带轻微弧面）绘制，法线编码在 Color 通道供 shader 使用。片元使用 `abs(dot(N,V))` 的 Fresnel 边缘、程序六边形网格、两层扫描线、发光噪声和受击闪白、扩散冲击环、放射火花和短暂亮度脉冲；工作台还会显示 3D 火星粒子。透明状态为无背面剔除、LEQUAL 深度测试、只写颜色。Intersection 参数已保留，当前版本未执行场景深度采样；这需要单独的深度纹理 pass。

验证状态：已完成源码解包、属性映射、HTML/JSON 结构和 Java/GLSL 文件静态检查。当前环境未发现可用 JDK 17，因此未宣称 `gradlew build` 或实际游戏运行通过；在安装 JDK 17 的 Forge 1.20.1 环境执行工程构建即可继续验证。






