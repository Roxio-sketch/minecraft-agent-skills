# Forge 1.20.1 原型与工作台

`assets/forge-1.20.1-lab/` 是 Java 17、Forge 47.4.10、ForgeGradle 6、Gradle 8.8 工程。主 Mod 还包含先前局部环境材质演示；本预设独立用 `/localmaterial sketch on|off|reload|status` 控制。默认关闭，不要求开启 Cosmic 区域。

工作台：从工程根目录运行 `python workbench/serve.py`，打开打印的地址。预览直读 `src/main/resources/assets/localmaterial/shaders/core/sketch.fsh`，WebGL 2 host 只转换版本声明并绑定截图纹理；深度 sampler 是白色占位纹理，镂空覆盖图是黑色占位纹理，所以工作台**不能预览游戏深度轮廓或树冠类别效果**。可导入任意场景截图；默认示例为本机 Minecraft 截图的上半场景裁剪，不包含用户提供的人物插画。`node workbench/validate.cjs <工作台URL> [截图路径]` 验证 WebGL 编译、调参响应、重置与 JSON 导出，需已有 Playwright/Chromium。

导出的 `one-last-kiss.json` 放入 `<游戏目录>/local-environment-lab/presets/one-last-kiss.json`；进入世界后执行 `/localmaterial sketch on`，修改文件后执行 `/localmaterial sketch reload`。首尾色标固定 0/1；配置文件缺失时游戏使用内置相同默认值，格式错误时命令拒绝覆盖当前合法配方。`/localmaterial sketch off` 停止并释放缓冲。离开世界、资源重载、尺寸变化时释放重建。

Forge 版本证据：本机 47.4.10 mapped `GameRenderer` 中 `AFTER_LEVEL` 位于 `LevelRenderer.renderLevel` 之后、深度清除/第一人称手部之前；`ForgeGui.render` 的 `RenderGuiEvent.Pre` 已在手部之后，却在 `hideGui` 时不会触发，因此原型用可选的 `GameRenderer.renderLevel` TAIL Mixin 做最后合成。Mixin 和 refmap 必须出现在 reobf JAR 内；启动后还要观察 `sketch status` 绘制次数和实图，不能仅凭编译通过。

本次实机验收（2026-10-02）：ASTRA-TEST / ASTRA_VFX_TEST，Forge 47.4.10，正式 JAR 无调试资源包覆盖。2560×1440 和 854×480 实际截图分别为 `assets/evidence/edge-fix-final.png` 与 `edge-fix-final-small.png`。修复了草方块被误当叶片而大面积丢线的问题；树叶仍有碎纹，细线仍可能偏淡，不宣称全场景边缘完整。重载后状态 `generation=2 gl=0 failed=false debug=0`。旧 evidence 图片属于前一版，不用于证明本次质量。

限制：该原型仅对原版/Fancy 主目标作实机确认。已知的 Embeddium/Oculus/Rubidium 和 Fabulous 路线禁用，主 FBO 不匹配时跳过；其他未知 renderer/Shader Pack 不保证兼容。场景中的透明层已合成，不能从最终 RGB 重建完整流体层列表。UI 保持原样，参考图的手绘线条质量不能由单次空间滤波保证；要稳定补齐复杂场景内边界，应增加模型法线/材质类别辅助缓冲和时域线条稳定。每帧有三个世界深度快照、一次场景颜色/手部深度快照、半分辨率树冠 pass 和多采样最终 pass，高分辨率需测帧时间。

正式 JAR 验证：无调试资源包覆盖，2560×1440 原机位与转动视角均成功出图（refined-final-native.png、refined-rotated.png）。资源重载后 generation=2、draws=4799、gl=0、failed=false、debug=0；控制模式已释放。该验证确认出图与重载，未做动态逐帧抗闪烁或 GPU 计时验收。

硬线稿当前版本：正式 JAR 无调试资源包，2560×1440 实图见 assets/evidence/crisp-final-native.png；工作台编译、渐变调参和 JSON 导出通过。移除了覆盖等值线描边，减少表面软纹，保留原深度几何的尖角。
