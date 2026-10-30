# 渲染与特效完整目录

快照日期：2026-10-14。23 个渲染相关 Skill、32 个工作台/预览/研究入口；原有 AXO 与 Photon 自动化 Skill 继续保留。每项说明以随包 `SKILL.md`、references 和实际资源为准。研究索引、浏览器设计稿与游戏消费者分别列明，不互相继承认证。

## 2026-10-19 增量：仙途特效库

新增[minecraft-xiantu-vfx-library](../skills/minecraft-xiantu-vfx-library/SKILL.md)，包含9个独立效果入口和1个总览入口；统一启动器现为43项。九组入口、必要动态资源和私有路径检查通过，另有焚空裂隙GPU验证，未声称九组都经过Minecraft验收。

[效果列表](../skills/minecraft-xiantu-vfx-library/references/effects.md) · [检查报告](../skills/minecraft-xiantu-vfx-library/references/validation/library-check.json)

## 动态模型与材质挂点 · `minecraft-animated-material-adapter`

为 Minecraft 动态物品、ModelPart 和骨骼载体适配程序材质与动画挂点，保留原动作、法线与显示变换，附 Forge 1.20.1 BEWLR 层级模型示例。用于 Cosmic、黑洞和碎晶的动态几何接入。

[完整 Skill](../skills/minecraft-animated-material-adapter/SKILL.md)
工具脚本：

- [scripts/preview.py](../skills/minecraft-animated-material-adapter/scripts/preview.py)
- [scripts/scaffold.py](../skills/minecraft-animated-material-adapter/scripts/scaffold.py)
- [scripts/validate_recipe.py](../skills/minecraft-animated-material-adapter/scripts/validate_recipe.py)
说明与验证：

- [references/design.md](../skills/minecraft-animated-material-adapter/references/design.md)
- [references/integration.md](../skills/minecraft-animated-material-adapter/references/integration.md)
- [references/sources.md](../skills/minecraft-animated-material-adapter/references/sources.md)
- [references/validation.md](../skills/minecraft-animated-material-adapter/references/validation.md)
随附资源：`forge-1.20.1`, `workbench.js`（22 个文件）。

## 黑洞与白洞 · `minecraft-black-hole`

从自然语言制作 Minecraft 黑洞、白洞双极喷流与外扩光壳、旋转盘面、螺旋粒子与可调渐变光晕，含原创 GLSL、黑洞工作台和 Forge 1.20.1 世界透镜及物品示例。用于近距离天体主体、盘面、粒子与背景畸变；不替代普通星空或科学相对论模拟。

[完整 Skill](../skills/minecraft-black-hole/SKILL.md)
工具脚本：

- [scripts/serve_preview.py](../skills/minecraft-black-hole/scripts/serve_preview.py)
- [scripts/validate_core_color.cjs](../skills/minecraft-black-hole/scripts/validate_core_color.cjs)
- [scripts/validate_core_occlusion.cjs](../skills/minecraft-black-hole/scripts/validate_core_occlusion.cjs)
- [scripts/validate_gradients.cjs](../skills/minecraft-black-hole/scripts/validate_gradients.cjs)
- [scripts/validate_infall.cjs](../skills/minecraft-black-hole/scripts/validate_infall.cjs)
- [scripts/validate_long_playback.cjs](../skills/minecraft-black-hole/scripts/validate_long_playback.cjs)
- [scripts/validate_mature_defaults.cjs](../skills/minecraft-black-hole/scripts/validate_mature_defaults.cjs)
- [scripts/validate_preview.cjs](../skills/minecraft-black-hole/scripts/validate_preview.cjs)
- [scripts/validate_regressions.cjs](../skills/minecraft-black-hole/scripts/validate_regressions.cjs)
- [scripts/validate_server.py](../skills/minecraft-black-hole/scripts/validate_server.py)
- [scripts/validate_styles.cjs](../skills/minecraft-black-hole/scripts/validate_styles.cjs)
- [scripts/validate_workbench.cjs](../skills/minecraft-black-hole/scripts/validate_workbench.cjs)
- [scripts/validate_workbench_v3.cjs](../skills/minecraft-black-hole/scripts/validate_workbench_v3.cjs)
说明与验证：

- [references/algorithm-sources.md](../skills/minecraft-black-hole/references/algorithm-sources.md)
- [references/design.md](../skills/minecraft-black-hole/references/design.md)
- [references/disk-compat-20261009.md](../skills/minecraft-black-hole/references/disk-compat-20261009.md)
- [references/forge-integration.md](../skills/minecraft-black-hole/references/forge-integration.md)
- [references/openbenchhub-patterns.md](../skills/minecraft-black-hole/references/openbenchhub-patterns.md)
- [references/particles-and-halo.md](../skills/minecraft-black-hole/references/particles-and-halo.md)
- [references/preview-validation.md](../skills/minecraft-black-hole/references/preview-validation.md)
- [references/reference-styles.md](../skills/minecraft-black-hole/references/reference-styles.md)
- [references/ribbon-reference-port-20261009.md](../skills/minecraft-black-hole/references/ribbon-reference-port-20261009.md)
- [references/ribbons-rework-20261009.md](../skills/minecraft-black-hole/references/ribbons-rework-20261009.md)
- [references/source-comparison.md](../skills/minecraft-black-hole/references/source-comparison.md)
- [references/user-index-study.md](../skills/minecraft-black-hole/references/user-index-study.md)
- [references/v3-workbench.md](../skills/minecraft-black-hole/references/v3-workbench.md)
- [references/white-hole.md](../skills/minecraft-black-hole/references/white-hole.md)
- [references/world-regression.md](../skills/minecraft-black-hole/references/world-regression.md)
随附资源：`forge-1.20.1`, `presets`, `preview`（100 个文件）。

## 彩色附魔流光与御体护纹 · `minecraft-colored-glint`

从自然语言、HEX 色值或色板制作 Minecraft 物品彩色附魔流光，支持任意自定义单色、多色渐变、整体彩虹变色与流动渐变，生成贴图、配方、GLSL 和 Forge 着色器接入资源。处理按物品选色、Glint RenderType、foil 入口、缓冲串色、循环接缝及 Cosmic 叠加；不用于普通天空星场、粒子特效或只改物品名称颜色。

[完整 Skill](../skills/minecraft-colored-glint/SKILL.md)
工具脚本：

- [scripts/author_glint.py](../skills/minecraft-colored-glint/scripts/author_glint.py)
- [scripts/extract_armor_textures.py](../skills/minecraft-colored-glint/scripts/extract_armor_textures.py)
- [scripts/recolor_glint.py](../skills/minecraft-colored-glint/scripts/recolor_glint.py)
说明与验证：

- [references/authoring.md](../skills/minecraft-colored-glint/references/authoring.md)
- [references/cosmic-and-custom-models.md](../skills/minecraft-colored-glint/references/cosmic-and-custom-models.md)
- [references/forge-1.20.1-runtime.md](../skills/minecraft-colored-glint/references/forge-1.20.1-runtime.md)
- [references/gradient-integration.md](../skills/minecraft-colored-glint/references/gradient-integration.md)
- [references/integration.md](../skills/minecraft-colored-glint/references/integration.md)
- [references/source-audit.md](../skills/minecraft-colored-glint/references/source-audit.md)
- [references/source-manifest.json](../skills/minecraft-colored-glint/references/source-manifest.json)
- [references/textures.md](../skills/minecraft-colored-glint/references/textures.md)
- [references/validation.md](../skills/minecraft-colored-glint/references/validation.md)
- [references/workbench.md](../skills/minecraft-colored-glint/references/workbench.md)
随附资源：`LICENSE-RenderBlender.txt`, `NOTICE.md`, `glint`, `presets`, `workbench`（40 个文件）。

## 物品内部星空与奇幻材质 · `minecraft-cosmic-material`

从自然语言制作 Minecraft 物品、穿戴防具和盾牌的 Cosmic 与奇幻材质，涵盖内部星空及多种奇幻质感，提供原创 GLSL、盾牌预设工作台、Forge 1.20.1 接入与验证方法。不用于普通天空盒。

[完整 Skill](../skills/minecraft-cosmic-material/SKILL.md)
工具脚本：

- [scripts/export_fantasy_pack.py](../skills/minecraft-cosmic-material/scripts/export_fantasy_pack.py)
- [scripts/freeze_suite.cjs](../skills/minecraft-cosmic-material/scripts/freeze_suite.cjs)
- [scripts/make_motion_sheet.py](../skills/minecraft-cosmic-material/scripts/make_motion_sheet.py)
- [scripts/make_star_motion.py](../skills/minecraft-cosmic-material/scripts/make_star_motion.py)
- [scripts/preview_validation_support.cjs](../skills/minecraft-cosmic-material/scripts/preview_validation_support.cjs)
- [scripts/record_fantasy_motion.cjs](../skills/minecraft-cosmic-material/scripts/record_fantasy_motion.cjs)
- [scripts/serve_authoring.py](../skills/minecraft-cosmic-material/scripts/serve_authoring.py)
- [scripts/test_export_fantasy_pack.py](../skills/minecraft-cosmic-material/scripts/test_export_fantasy_pack.py)
- [scripts/test_ocean_spectrum.cjs](../skills/minecraft-cosmic-material/scripts/test_ocean_spectrum.cjs)
- [scripts/test_preview_server.py](../skills/minecraft-cosmic-material/scripts/test_preview_server.py)
- [scripts/test_preview_validation.cjs](../skills/minecraft-cosmic-material/scripts/test_preview_validation.cjs)
- [scripts/validate_fantasy.cjs](../skills/minecraft-cosmic-material/scripts/validate_fantasy.cjs)
- [scripts/validate_flow_continuity.py](../skills/minecraft-cosmic-material/scripts/validate_flow_continuity.py)
- [scripts/validate_flow_motion.py](../skills/minecraft-cosmic-material/scripts/validate_flow_motion.py)
- [scripts/validate_outline.cjs](../skills/minecraft-cosmic-material/scripts/validate_outline.cjs)
- [scripts/validate_preview.cjs](../skills/minecraft-cosmic-material/scripts/validate_preview.cjs)
- [scripts/validate_star_motion.cjs](../skills/minecraft-cosmic-material/scripts/validate_star_motion.cjs)
- [scripts/validate_water.cjs](../skills/minecraft-cosmic-material/scripts/validate_water.cjs)
- [scripts/validate_workshop.cjs](../skills/minecraft-cosmic-material/scripts/validate_workshop.cjs)
- [scripts/启动工作台.py](../skills/minecraft-cosmic-material/scripts/启动工作台.py)
说明与验证：

- [references/advanced-effects-study.md](../skills/minecraft-cosmic-material/references/advanced-effects-study.md)
- [references/algorithm.md](../skills/minecraft-cosmic-material/references/algorithm.md)
- [references/arcanevortex-study.md](../skills/minecraft-cosmic-material/references/arcanevortex-study.md)
- [references/authoring-workbench.md](../skills/minecraft-cosmic-material/references/authoring-workbench.md)
- [references/design.md](../skills/minecraft-cosmic-material/references/design.md)
- [references/fantasy-forge.md](../skills/minecraft-cosmic-material/references/fantasy-forge.md)
- [references/fantasy-materials.md](../skills/minecraft-cosmic-material/references/fantasy-materials.md)
- [references/forge-1.20.1-equipment.md](../skills/minecraft-cosmic-material/references/forge-1.20.1-equipment.md)
- [references/forge-1.20.1-runtime.md](../skills/minecraft-cosmic-material/references/forge-1.20.1-runtime.md)
- [references/integration.md](../skills/minecraft-cosmic-material/references/integration.md)
- [references/internal-flow.md](../skills/minecraft-cosmic-material/references/internal-flow.md)
- [references/item-outline-runtime.md](../skills/minecraft-cosmic-material/references/item-outline-runtime.md)
- [references/item-outline-study.md](../skills/minecraft-cosmic-material/references/item-outline-study.md)
- [references/item-outline-workbench.md](../skills/minecraft-cosmic-material/references/item-outline-workbench.md)
- [references/material-authoring.md](../skills/minecraft-cosmic-material/references/material-authoring.md)
- [references/material-recipes.md](../skills/minecraft-cosmic-material/references/material-recipes.md)
- [references/render-frameworks-study.md](../skills/minecraft-cosmic-material/references/render-frameworks-study.md)
- [references/sources.md](../skills/minecraft-cosmic-material/references/sources.md)
- [references/tv-snow-material.md](../skills/minecraft-cosmic-material/references/tv-snow-material.md)
- [references/validation.md](../skills/minecraft-cosmic-material/references/validation.md)
- [references/water-material.md](../skills/minecraft-cosmic-material/references/water-material.md)
随附资源：`authoring-lab`, `fantasy-lab`, `outline-lab`, `shield-presets`（16 个文件）。

## 天空、大气与天气 · `minecraft-cosmic-rendering`

从自然语言制作 Minecraft 程序天空、球形大气、世界云雾天光与生物群系雨雪，兼含双虹、冰晕、星云、巨型天体和两类动态远景黑洞，提供调参工作台及 Forge 1.20.1 原版玻璃水面接入。不用于近距离世界黑洞、世界生成或 Photon 粒子编辑。

[完整 Skill](../skills/minecraft-cosmic-rendering/SKILL.md)
工具脚本：

- [scripts/check-source-manifest.py](../skills/minecraft-cosmic-rendering/scripts/check-source-manifest.py)
- [scripts/check_validation.py](../skills/minecraft-cosmic-rendering/scripts/check_validation.py)
说明与验证：

- [references/advanced-sky.md](../skills/minecraft-cosmic-rendering/references/advanced-sky.md)
- [references/algorithms.md](../skills/minecraft-cosmic-rendering/references/algorithms.md)
- [references/authoring.md](../skills/minecraft-cosmic-rendering/references/authoring.md)
- [references/bliss-end-clouds-manifest.json](../skills/minecraft-cosmic-rendering/references/bliss-end-clouds-manifest.json)
- [references/bliss-end-clouds.md](../skills/minecraft-cosmic-rendering/references/bliss-end-clouds.md)
- [references/celestial-giants.md](../skills/minecraft-cosmic-rendering/references/celestial-giants.md)
- [references/coordinates.md](../skills/minecraft-cosmic-rendering/references/coordinates.md)
- [references/diagnostics.md](../skills/minecraft-cosmic-rendering/references/diagnostics.md)
- [references/evidence.md](../skills/minecraft-cosmic-rendering/references/evidence.md)
- [references/forge-1.20.1.md](../skills/minecraft-cosmic-rendering/references/forge-1.20.1.md)
- [references/sky-materials.md](../skills/minecraft-cosmic-rendering/references/sky-materials.md)
随附资源：`forge-1.20.1-lab`, `procedural-sky-lab`（127 个文件）。

## 碎晶折射与色散 · `minecraft-crystal-refraction`

从自然语言制作 Minecraft 碎晶折射、棱镜色散和裂隙玻璃材质，提供原创可调预览、GLSL 资源生成、物品内部场与世界背景两种接入方法。用于折射星空、水晶武器和空间扭曲；不用于仅改色的附魔流光或普通透明贴图。

[完整 Skill](../skills/minecraft-crystal-refraction/SKILL.md)
工具脚本：

- [scripts/build_shader.py](../skills/minecraft-crystal-refraction/scripts/build_shader.py)
- [scripts/serve_preview.py](../skills/minecraft-crystal-refraction/scripts/serve_preview.py)
- [scripts/validate_preview.cjs](../skills/minecraft-crystal-refraction/scripts/validate_preview.cjs)
- [scripts/validate_state.cjs](../skills/minecraft-crystal-refraction/scripts/validate_state.cjs)
说明与验证：

- [references/design-and-algorithm.md](../skills/minecraft-crystal-refraction/references/design-and-algorithm.md)
- [references/forge-1.20.1.md](../skills/minecraft-crystal-refraction/references/forge-1.20.1.md)
- [references/sources-and-status.md](../skills/minecraft-crystal-refraction/references/sources-and-status.md)
- [references/validation.md](../skills/minecraft-crystal-refraction/references/validation.md)
随附资源：`lab`（6 个文件）。

## 动态点光与局部照明 · `minecraft-dynamic-lighting`

制作 Minecraft 客户端动态点光与局部场景照明，处理位置重建、法线、光源数据、范围衰减及合成，附 Forge 1.20.1 地形深度重建示例；用于武器或粒子照亮场景。

[完整 Skill](../skills/minecraft-dynamic-lighting/SKILL.md)
工具脚本：

- [scripts/preview.py](../skills/minecraft-dynamic-lighting/scripts/preview.py)
- [scripts/scaffold.py](../skills/minecraft-dynamic-lighting/scripts/scaffold.py)
- [scripts/validate_recipe.py](../skills/minecraft-dynamic-lighting/scripts/validate_recipe.py)
说明与验证：

- [references/design.md](../skills/minecraft-dynamic-lighting/references/design.md)
- [references/integration.md](../skills/minecraft-dynamic-lighting/references/integration.md)
- [references/sources.md](../skills/minecraft-dynamic-lighting/references/sources.md)
- [references/validation.md](../skills/minecraft-dynamic-lighting/references/validation.md)
随附资源：`forge-1.20.1`, `workbench.js`（20 个文件）。

## 三维能量与幽气 · `minecraft-energy-vfx`

从自然语言或 energy-vfx/2 配方编辑并接入 Minecraft Forge 1.20.1 三维能量、幽气和白蓝烟纱特效。提供 48 个参数、20 个可编辑起点、共用 GLSL、严格 v1 升级、实际 ModelPart 与 baked 装备表面发散及固定文件命令桥。用于持续离体气团、冷焰、薄雾、卷曲细丝、蓄力爆发与局部 Bloom；不是资料台或网格流体求解器。

[完整 Skill](../skills/minecraft-energy-vfx/SKILL.md)
工具脚本：

- [scripts/apply_recipe.py](../skills/minecraft-energy-vfx/scripts/apply_recipe.py)
- [scripts/preview.py](../skills/minecraft-energy-vfx/scripts/preview.py)
- [scripts/scaffold.py](../skills/minecraft-energy-vfx/scripts/scaffold.py)
- [scripts/validate_recipe.py](../skills/minecraft-energy-vfx/scripts/validate_recipe.py)
说明与验证：

- [references/fullscreen-performance.md](../skills/minecraft-energy-vfx/references/fullscreen-performance.md)
- [references/ghost-sources.md](../skills/minecraft-energy-vfx/references/ghost-sources.md)
- [references/integration.md](../skills/minecraft-energy-vfx/references/integration.md)
- [references/motion-performance.md](../skills/minecraft-energy-vfx/references/motion-performance.md)
- [references/package-manifest.json](../skills/minecraft-energy-vfx/references/package-manifest.json)
- [references/performance.md](../skills/minecraft-energy-vfx/references/performance.md)
- [references/recipe.md](../skills/minecraft-energy-vfx/references/recipe.md)
- [references/third-person-zoom-performance.md](../skills/minecraft-energy-vfx/references/third-person-zoom-performance.md)
- [references/validation.md](../skills/minecraft-energy-vfx/references/validation.md)
随附资源：`lab`（100 个文件）。

## 能量算法与源码研究 · `minecraft-energy-vfx-research`

"分析影视或游戏能量参考，建立实时三维能量 VFX 的算法、源码与许可证图谱及 Minecraft Forge 1.20.1 学习路线，提供 HTML 研究工作台。用于流动、卷吸、体积、细丝、冲击波和合成管线研究；不把资料工作台当作已实现的渲染器。"

[完整 Skill](../skills/minecraft-energy-vfx-research/SKILL.md)
说明与验证：

- [references/catalog.json](../skills/minecraft-energy-vfx-research/references/catalog.json)
- [references/source-map.md](../skills/minecraft-energy-vfx-research/references/source-map.md)
随附资源：`research-workbench.html`（1 个文件）。

## 玻璃边缘光 · `minecraft-glass-rim`

从自然语言制作 Minecraft 玻璃边缘亮、水晶外壳与能量护罩的视角相关边缘光，提供原创 GLSL、三维可调预览和 Forge 1.20.1 接入方法。用于法线驱动的 rim/Fresnel 外观；不把它当作真实折射、二维轮廓提取、Halo 或屏幕 bloom。

[完整 Skill](../skills/minecraft-glass-rim/SKILL.md)
工具脚本：

- [scripts/serve_preview.py](../skills/minecraft-glass-rim/scripts/serve_preview.py)
- [scripts/validate_preview.cjs](../skills/minecraft-glass-rim/scripts/validate_preview.cjs)
说明与验证：

- [references/design.md](../skills/minecraft-glass-rim/references/design.md)
- [references/forge-1.20.1.md](../skills/minecraft-glass-rim/references/forge-1.20.1.md)
- [references/validation.md](../skills/minecraft-glass-rim/references/validation.md)
随附资源：`lab`, `rim.glsl`, `vendor`（5 个文件）。

## Halo 与物品装饰 · `minecraft-halo-decoration`

从自然语言制作 Minecraft 物品 Halo 光晕、旋转魔法环、放射星芒、呼吸与多层装饰，含 16px 小图专用配方、透明素材和动画预览，指导 Forge 1.20.1 接入及与 Cosmic、附魔流光共存。不用于内部宇宙算法、真实场景照明或屏幕 bloom。

[完整 Skill](../skills/minecraft-halo-decoration/SKILL.md)
工具脚本：

- [scripts/author_halo.py](../skills/minecraft-halo-decoration/scripts/author_halo.py)
- [scripts/compare_small.py](../skills/minecraft-halo-decoration/scripts/compare_small.py)
- [scripts/verify_authoring.py](../skills/minecraft-halo-decoration/scripts/verify_authoring.py)
说明与验证：

- [references/authoring.md](../skills/minecraft-halo-decoration/references/authoring.md)
- [references/forge-1.20.1.md](../skills/minecraft-halo-decoration/references/forge-1.20.1.md)
- [references/small-icons.md](../skills/minecraft-halo-decoration/references/small-icons.md)
- [references/sources.md](../skills/minecraft-halo-decoration/references/sources.md)
- [references/validation.md](../skills/minecraft-halo-decoration/references/validation.md)
随附资源：`presets`（5 个文件）。

## 原画线稿与屏幕渐变 · `minecraft-illustration-render`

为 Minecraft 场景制作实时白底彩色原画线稿、铅笔稿与连续屏幕渐变预设，含可调工作台和 Forge 1.20.1 接入。用于整场景风格化；不用于世界空间局部材质替换或单张贴图着色。

[完整 Skill](../skills/minecraft-illustration-render/SKILL.md)
说明与验证：

- [references/forge-1.20.1.md](../skills/minecraft-illustration-render/references/forge-1.20.1.md)
- [references/pipeline.md](../skills/minecraft-illustration-render/references/pipeline.md)
- [references/water-leaf-lines.md](../skills/minecraft-illustration-render/references/water-leaf-lines.md)
随附资源：`evidence`, `forge-1.20.1-lab`（50 个文件）。

## 物品内三维场景窗口 · `minecraft-item-scene-window`

为 Minecraft Forge 1.20.1 从自然语言制作物品内的真实三维场景窗口，支持自由场景构图、层级几何、动画、相机视差与自定义窗口遮罩，附可构建的离屏渲染工程。用于口袋世界、微缩景观、空间裂隙和镜面/世界视图的进一步接入；不把星空贴图或滚动 UV 当作真实场景。

[完整 Skill](../skills/minecraft-item-scene-window/SKILL.md)
工具脚本：

- [scripts/scaffold.py](../skills/minecraft-item-scene-window/scripts/scaffold.py)
- [scripts/validate_scene.py](../skills/minecraft-item-scene-window/scripts/validate_scene.py)
说明与验证：

- [references/architecture-sources.md](../skills/minecraft-item-scene-window/references/architecture-sources.md)
- [references/design.md](../skills/minecraft-item-scene-window/references/design.md)
- [references/forge-integration.md](../skills/minecraft-item-scene-window/references/forge-integration.md)
- [references/runtime-evidence.md](../skills/minecraft-item-scene-window/references/runtime-evidence.md)
- [references/scene-format.md](../skills/minecraft-item-scene-window/references/scene-format.md)
- [references/validation.md](../skills/minecraft-item-scene-window/references/validation.md)
- [references/evidence/contact-sheet.jpg](../skills/minecraft-item-scene-window/references/evidence/contact-sheet.jpg)
- [references/evidence/independent-validation.json](../skills/minecraft-item-scene-window/references/evidence/independent-validation.json)
- [references/evidence/results.json](../skills/minecraft-item-scene-window/references/evidence/results.json)
随附资源：`forge-1.20.1-template`（31 个文件）。

## 环绕电流与分形落雷 · `minecraft-lightning`

为 Minecraft Forge 1.20.1 / Java 17 制作可调环绕电流、分形闪电、集束落雷和击地放电，附 WebGL2 参数工作台、固定 tick 几何、渐变比例、稳定光晕、63字段配方与独立游戏验收入口。用于雷劫、物体充能和局部放电；不替代完整电磁或大气求解器。

[完整 Skill](../skills/minecraft-lightning/SKILL.md)
工具脚本：

- [scripts/author.cjs](../skills/minecraft-lightning/scripts/author.cjs)
- [scripts/check-workbench.cjs](../skills/minecraft-lightning/scripts/check-workbench.cjs)
说明与验证：

- [references/behavior-comparison.md](../skills/minecraft-lightning/references/behavior-comparison.md)
- [references/design.md](../skills/minecraft-lightning/references/design.md)
- [references/disk-compat-20261009.md](../skills/minecraft-lightning/references/disk-compat-20261009.md)
- [references/forge.md](../skills/minecraft-lightning/references/forge.md)
- [references/mature-rendering.md](../skills/minecraft-lightning/references/mature-rendering.md)
- [references/performance-height-limits.md](../skills/minecraft-lightning/references/performance-height-limits.md)
- [references/research.md](../skills/minecraft-lightning/references/research.md)
- [references/sources.md](../skills/minecraft-lightning/references/sources.md)
- [references/validation.md](../skills/minecraft-lightning/references/validation.md)
随附资源：`forge-1.20.1`, `presets`, `workbench`（73 个文件）。

## 世界空间区域材质 · `minecraft-local-environment-material`

保留 Minecraft 原世界几何与位置，在球形、路径或其他世界空间区域内替换环境表面材质，制作扩散、侵蚀、收缩与边缘效果。处理深度位置重建、对象筛选、区域叠加、透明层、生命周期及 Forge 1.20.1 接入；不用于跨维度门户、单纯全屏滤镜或粒子覆盖。

[完整 Skill](../skills/minecraft-local-environment-material/SKILL.md)
说明与验证：

- [references/architecture.md](../skills/minecraft-local-environment-material/references/architecture.md)
- [references/coordinates-regions.md](../skills/minecraft-local-environment-material/references/coordinates-regions.md)
- [references/forge-1.20.1.md](../skills/minecraft-local-environment-material/references/forge-1.20.1.md)
- [references/materials-transitions.md](../skills/minecraft-local-environment-material/references/materials-transitions.md)
- [references/runtime-evidence.json](../skills/minecraft-local-environment-material/references/runtime-evidence.json)
- [references/runtime-performance.md](../skills/minecraft-local-environment-material/references/runtime-performance.md)
- [references/source-evidence.json](../skills/minecraft-local-environment-material/references/source-evidence.json)
- [references/source-study.md](../skills/minecraft-local-environment-material/references/source-study.md)
- [references/surface-runtime-evidence.json](../skills/minecraft-local-environment-material/references/surface-runtime-evidence.json)
- [references/surface-source-evidence.json](../skills/minecraft-local-environment-material/references/surface-source-evidence.json)
- [references/surface-validation.md](../skills/minecraft-local-environment-material/references/surface-validation.md)
- [references/transparent-surfaces.md](../skills/minecraft-local-environment-material/references/transparent-surfaces.md)
- [references/validation.md](../skills/minecraft-local-environment-material/references/validation.md)
- [references/water-transparency-limits.md](../skills/minecraft-local-environment-material/references/water-transparency-limits.md)
随附资源：`forge-1.20.1-lab`, `validation`（27 个文件）。

## 局部异空间与二次场景 · `minecraft-local-subscene`

设计和实现 Minecraft 局部异空间与二次场景渲染，区分观看窗口、可进入视觉领域与真实第二世界，选择 FBO、Stencil、相机和深度策略，复用已实测的 Forge 1.20.1 示例工程。用于门、裂隙、球形领域内的独立场景，不用于给原世界地形换材质。

[完整 Skill](../skills/minecraft-local-subscene/SKILL.md)
工具脚本：

- [scripts/scaffold_inkrealm.py](../skills/minecraft-local-subscene/scripts/scaffold_inkrealm.py)
- [scripts/verify_bundle.py](../skills/minecraft-local-subscene/scripts/verify_bundle.py)
- [scripts/verify_inkrealm_evidence.py](../skills/minecraft-local-subscene/scripts/verify_inkrealm_evidence.py)
说明与验证：

- [references/architecture.md](../skills/minecraft-local-subscene/references/architecture.md)
- [references/forge-1.20.1.md](../skills/minecraft-local-subscene/references/forge-1.20.1.md)
- [references/geometry-depth-camera.md](../skills/minecraft-local-subscene/references/geometry-depth-camera.md)
- [references/lightweight-vfx.md](../skills/minecraft-local-subscene/references/lightweight-vfx.md)
- [references/performance-compatibility.md](../skills/minecraft-local-subscene/references/performance-compatibility.md)
- [references/probes.md](../skills/minecraft-local-subscene/references/probes.md)
- [references/runtime-inkrealm.md](../skills/minecraft-local-subscene/references/runtime-inkrealm.md)
- [references/secondary-world-lifecycle.md](../skills/minecraft-local-subscene/references/secondary-world-lifecycle.md)
- [references/source-evidence.json](../skills/minecraft-local-subscene/references/source-evidence.json)
- [references/source-index.md](../skills/minecraft-local-subscene/references/source-index.md)
- [references/source-study.md](../skills/minecraft-local-subscene/references/source-study.md)
- [references/validation.md](../skills/minecraft-local-subscene/references/validation.md)
- [references/evidence/bundle-assets.json](../skills/minecraft-local-subscene/references/evidence/bundle-assets.json)
- [references/evidence/circle.png](../skills/minecraft-local-subscene/references/evidence/circle.png)
- [references/evidence/gpu-reproduction.json](../skills/minecraft-local-subscene/references/evidence/gpu-reproduction.json)
- [references/evidence/gpu-validation.json](../skills/minecraft-local-subscene/references/evidence/gpu-validation.json)
- [references/evidence/irregular.png](../skills/minecraft-local-subscene/references/evidence/irregular.png)
- [references/evidence/pipeline.png](../skills/minecraft-local-subscene/references/evidence/pipeline.png)
- [references/evidence/plane.png](../skills/minecraft-local-subscene/references/evidence/plane.png)
- [references/evidence/rift.png](../skills/minecraft-local-subscene/references/evidence/rift.png)
- [references/evidence/sphere-eye-0.png](../skills/minecraft-local-subscene/references/evidence/sphere-eye-0.png)
- [references/evidence/sphere-eye-3.png](../skills/minecraft-local-subscene/references/evidence/sphere-eye-3.png)
- [references/evidence/inkrealm/deployment.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/deployment.json)
- [references/evidence/inkrealm/helper-validation.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/helper-validation.json)
- [references/evidence/inkrealm/scaffold-build.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/scaffold-build.json)
- [references/evidence/inkrealm/verification.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/verification.json)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-before.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-before.png)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-move-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-move-scene.png)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-move.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-move.json)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-reload-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-reload-scene.png)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-reload.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-reload.json)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-scene.png)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-turn-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-turn-scene.png)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-turn.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-turn.json)
- [references/evidence/inkrealm/crossing/20260929-100726-inside-world.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside-world.png)
- [references/evidence/inkrealm/crossing/20260929-100726-inside.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-inside.json)
- [references/evidence/inkrealm/crossing/20260929-100726-near-before.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-near-before.png)
- [references/evidence/inkrealm/crossing/20260929-100726-near-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-near-scene.png)
- [references/evidence/inkrealm/crossing/20260929-100726-near-world.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-near-world.png)
- [references/evidence/inkrealm/crossing/20260929-100726-near.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-near.json)
- [references/evidence/inkrealm/crossing/20260929-100726-returned-before.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-returned-before.png)
- [references/evidence/inkrealm/crossing/20260929-100726-returned-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-returned-scene.png)
- [references/evidence/inkrealm/crossing/20260929-100726-returned-world.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-returned-world.png)
- [references/evidence/inkrealm/crossing/20260929-100726-returned.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-returned.json)
- [references/evidence/inkrealm/crossing/20260929-100726-window-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-window-scene.png)
- [references/evidence/inkrealm/crossing/20260929-100726-window.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/20260929-100726-window.json)
- [references/evidence/inkrealm/crossing/session.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/crossing/session.json)
- [references/evidence/inkrealm/runtime/20260929-100841-foreground-before.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-foreground-before.png)
- [references/evidence/inkrealm/runtime/20260929-100841-foreground-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-foreground-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-foreground-world.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-foreground-world.png)
- [references/evidence/inkrealm/runtime/20260929-100841-foreground.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-foreground.json)
- [references/evidence/inkrealm/runtime/20260929-100841-front-before.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-front-before.png)
- [references/evidence/inkrealm/runtime/20260929-100841-front-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-front-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-front-world.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-front-world.png)
- [references/evidence/inkrealm/runtime/20260929-100841-front.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-front.json)
- [references/evidence/inkrealm/runtime/20260929-100841-high-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-high-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-high.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-high.json)
- [references/evidence/inkrealm/runtime/20260929-100841-left-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-left-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-left.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-left.json)
- [references/evidence/inkrealm/runtime/20260929-100841-low-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-low-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-low.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-low.json)
- [references/evidence/inkrealm/runtime/20260929-100841-recovered-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-recovered-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-recovered.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-recovered.json)
- [references/evidence/inkrealm/runtime/20260929-100841-reloaded-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-reloaded-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-reloaded.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-reloaded.json)
- [references/evidence/inkrealm/runtime/20260929-100841-right-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-right-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-right.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-right.json)
- [references/evidence/inkrealm/runtime/20260929-100841-time4-scene.png](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-time4-scene.png)
- [references/evidence/inkrealm/runtime/20260929-100841-time4.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/20260929-100841-time4.json)
- [references/evidence/inkrealm/runtime/session.json](../skills/minecraft-local-subscene/references/evidence/inkrealm/runtime/session.json)
随附资源：`forge-api-probe`, `forge-inkrealm-example`, `gpu-probe`（28 个文件）。

## 代码驱动粒子 · `minecraft-procedural-particles`

从自然语言制作 Minecraft 代码驱动的发射器、环绕、喷发和聚合粒子，提供稳定 tick 模拟、挂点与世界空间、生命周期、容量和 Forge 1.20.1 示例；用于 Java 或数据驱动运行库的粒子制作。

[完整 Skill](../skills/minecraft-procedural-particles/SKILL.md)
工具脚本：

- [scripts/check_core.py](../skills/minecraft-procedural-particles/scripts/check_core.py)
- [scripts/preview.py](../skills/minecraft-procedural-particles/scripts/preview.py)
- [scripts/scaffold.py](../skills/minecraft-procedural-particles/scripts/scaffold.py)
- [scripts/validate_recipe.py](../skills/minecraft-procedural-particles/scripts/validate_recipe.py)
说明与验证：

- [references/design.md](../skills/minecraft-procedural-particles/references/design.md)
- [references/integration.md](../skills/minecraft-procedural-particles/references/integration.md)
- [references/sources.md](../skills/minecraft-procedural-particles/references/sources.md)
- [references/validation.md](../skills/minecraft-procedural-particles/references/validation.md)
随附资源：`forge-1.20.1`, `workbench.js`（21 个文件）。

## 指定对象 Bloom · `minecraft-selective-bloom`

从自然语言制作 Minecraft 指定对象与材质区域的屏幕 Bloom，提供独立发光缓冲、分离模糊、深度约束与 Forge 1.20.1 原创示例；用于星点、符文和能量核的外扩光晕。

[完整 Skill](../skills/minecraft-selective-bloom/SKILL.md)
工具脚本：

- [scripts/preview.py](../skills/minecraft-selective-bloom/scripts/preview.py)
- [scripts/scaffold.py](../skills/minecraft-selective-bloom/scripts/scaffold.py)
- [scripts/validate_recipe.py](../skills/minecraft-selective-bloom/scripts/validate_recipe.py)
说明与验证：

- [references/design.md](../skills/minecraft-selective-bloom/references/design.md)
- [references/integration.md](../skills/minecraft-selective-bloom/references/integration.md)
- [references/sources.md](../skills/minecraft-selective-bloom/references/sources.md)
- [references/validation.md](../skills/minecraft-selective-bloom/references/validation.md)
随附资源：`forge-1.20.1`, `workbench.js`（23 个文件）。

## 能量护盾与破碎回应 · `minecraft-stylised-shield`

将 Unity Stylised Energy Shield Shader Graph 转换为 Minecraft Forge 1.20.1 的客户端护盾网格特效，附带共享 recipe.json、可自定义 HTML 工作台、六边形/扫描线/边缘光/受击波纹 GLSL 与 Java 17 工程。用于护盾、能量穹顶和全息防护罩；不替代真实折射、场景照明或全屏后处理。

[完整 Skill](../skills/minecraft-stylised-shield/SKILL.md)
说明与验证：

- [references/source-comparison.md](../skills/minecraft-stylised-shield/references/source-comparison.md)
随附资源：`forge-1.20.1`（32 个文件）。

## 武器拖尾与能量束 · `minecraft-trails-beams`

从自然语言制作 Minecraft 武器拖尾、刀光、轨迹残影、分叉能量束和多层光带。附离线 HTML 工作台、七种可编辑起点、严格配方与路径 JSON、原创几何内核、Forge 1.20.1 / Java 17 参考工程及验证。支持真实双挂点适配、任意路径、宽度色带、自定义纹理；不自动适配全部骨骼武器。

[完整 Skill](../skills/minecraft-trails-beams/SKILL.md)
工具脚本：

- [scripts/author_effect.py](../skills/minecraft-trails-beams/scripts/author_effect.py)
- [scripts/build_workbench_presets.py](../skills/minecraft-trails-beams/scripts/build_workbench_presets.py)
- [scripts/verify_authoring.py](../skills/minecraft-trails-beams/scripts/verify_authoring.py)
- [scripts/verify_runtime.py](../skills/minecraft-trails-beams/scripts/verify_runtime.py)
- [scripts/verify_workbench.cjs](../skills/minecraft-trails-beams/scripts/verify_workbench.cjs)
说明与验证：

- [references/authoring.md](../skills/minecraft-trails-beams/references/authoring.md)
- [references/evidence.md](../skills/minecraft-trails-beams/references/evidence.md)
- [references/forge-1.20.1.md](../skills/minecraft-trails-beams/references/forge-1.20.1.md)
- [references/path.md](../skills/minecraft-trails-beams/references/path.md)
- [references/recipe.md](../skills/minecraft-trails-beams/references/recipe.md)
- [references/sources.md](../skills/minecraft-trails-beams/references/sources.md)
- [references/validation.md](../skills/minecraft-trails-beams/references/validation.md)
- [references/workbench.md](../skills/minecraft-trails-beams/references/workbench.md)
随附资源：`forge-1.20.1`, `presets`, `workbench`（37 个文件）。

## 体积云雾、黑云与水龙卷 · `minecraft-volumetric-environment`

从自然语言制作 Minecraft 世界体积云雾、末地扭卷云与紫雾，附 Forge 1.20.1 基础后端；另含渡劫黑云、陆地龙卷、龙吸水与水龙卷的原创 WebGL 工作台。不用于天空极光、闪电枝条或物品气团。

[完整 Skill](../skills/minecraft-volumetric-environment/SKILL.md)
工具脚本：

- [scripts/author.py](../skills/minecraft-volumetric-environment/scripts/author.py)
说明与验证：

- [references/astralex-purple-fog.md](../skills/minecraft-volumetric-environment/references/astralex-purple-fog.md)
- [references/authoring.md](../skills/minecraft-volumetric-environment/references/authoring.md)
- [references/bliss-end-density.md](../skills/minecraft-volumetric-environment/references/bliss-end-density.md)
- [references/event-workbenches.md](../skills/minecraft-volumetric-environment/references/event-workbenches.md)
- [references/forge-1.20.1.md](../skills/minecraft-volumetric-environment/references/forge-1.20.1.md)
- [references/render-contracts.md](../skills/minecraft-volumetric-environment/references/render-contracts.md)
- [references/source-study.md](../skills/minecraft-volumetric-environment/references/source-study.md)
- [references/sources.json](../skills/minecraft-volumetric-environment/references/sources.json)
- [references/validation.md](../skills/minecraft-volumetric-environment/references/validation.md)
- [references/validation/astralex-study.json](../skills/minecraft-volumetric-environment/references/validation/astralex-study.json)
- [references/validation/bliss-density-study.json](../skills/minecraft-volumetric-environment/references/validation/bliss-density-study.json)
- [references/validation/end-distributions-gpu.json](../skills/minecraft-volumetric-environment/references/validation/end-distributions-gpu.json)
- [references/validation/forge-acceptance.json](../skills/minecraft-volumetric-environment/references/validation/forge-acceptance.json)
- [references/validation/forge-build.json](../skills/minecraft-volumetric-environment/references/validation/forge-build.json)
- [references/validation/forge-runtime.json](../skills/minecraft-volumetric-environment/references/validation/forge-runtime.json)
- [references/validation/forward-v2-fixture-assumption.json](../skills/minecraft-volumetric-environment/references/validation/forward-v2-fixture-assumption.json)
- [references/validation/forward-v2.json](../skills/minecraft-volumetric-environment/references/validation/forward-v2.json)
- [references/validation/forward.json](../skills/minecraft-volumetric-environment/references/validation/forward.json)
- [references/validation/gpu.json](../skills/minecraft-volumetric-environment/references/validation/gpu.json)
- [references/validation/metadata-export.json](../skills/minecraft-volumetric-environment/references/validation/metadata-export.json)
- [references/validation/portable-gpu.json](../skills/minecraft-volumetric-environment/references/validation/portable-gpu.json)
- [references/validation/protocol.json](../skills/minecraft-volumetric-environment/references/validation/protocol.json)
- [references/validation/sources.json](../skills/minecraft-volumetric-environment/references/validation/sources.json)
- [references/validation/tribulation.json](../skills/minecraft-volumetric-environment/references/validation/tribulation.json)
- [references/validation/vortex-bowl9-before-square-page.json](../skills/minecraft-volumetric-environment/references/validation/vortex-bowl9-before-square-page.json)
- [references/validation/vortex-continuity7-page.json](../skills/minecraft-volumetric-environment/references/validation/vortex-continuity7-page.json)
- [references/validation/vortex-continuity7-portable.json](../skills/minecraft-volumetric-environment/references/validation/vortex-continuity7-portable.json)
- [references/validation/vortex-layered-water8-page.json](../skills/minecraft-volumetric-environment/references/validation/vortex-layered-water8-page.json)
- [references/validation/vortex-layered-water8-portable.json](../skills/minecraft-volumetric-environment/references/validation/vortex-layered-water8-portable.json)
- [references/validation/vortex-portable-before-sampling-fix.json](../skills/minecraft-volumetric-environment/references/validation/vortex-portable-before-sampling-fix.json)
- [references/validation/vortex-portable.json](../skills/minecraft-volumetric-environment/references/validation/vortex-portable.json)
- [references/validation/vortex-revision7-timing.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision7-timing.json)
- [references/validation/vortex-revision7-visual.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision7-visual.json)
- [references/validation/vortex-revision7.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision7.json)
- [references/validation/vortex-revision8-timing.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision8-timing.json)
- [references/validation/vortex-revision8-visual.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision8-visual.json)
- [references/validation/vortex-revision8.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision8.json)
- [references/validation/vortex-revision9-timing.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision9-timing.json)
- [references/validation/vortex-revision9-visual-review.md](../skills/minecraft-volumetric-environment/references/validation/vortex-revision9-visual-review.md)
- [references/validation/vortex-revision9-visual.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision9-visual.json)
- [references/validation/vortex-revision9.json](../skills/minecraft-volumetric-environment/references/validation/vortex-revision9.json)
- [references/validation/vortex-sampling-diagnosis.json](../skills/minecraft-volumetric-environment/references/validation/vortex-sampling-diagnosis.json)
- [references/validation/vortex-water.json](../skills/minecraft-volumetric-environment/references/validation/vortex-water.json)
- [references/validation/vortex.json](../skills/minecraft-volumetric-environment/references/validation/vortex.json)
- [references/validation/workbench.json](../skills/minecraft-volumetric-environment/references/validation/workbench.json)
- [references/research/github-water-vortex-20261011/IMPLEMENTATION_NOTES.md](../skills/minecraft-volumetric-environment/references/research/github-water-vortex-20261011/IMPLEMENTATION_NOTES.md)
- [references/research/iterationT-storm-20261010/analysis-checks.json](../skills/minecraft-volumetric-environment/references/research/iterationT-storm-20261010/analysis-checks.json)
- [references/research/iterationT-storm-20261010/anchors.json](../skills/minecraft-volumetric-environment/references/research/iterationT-storm-20261010/anchors.json)
- [references/research/iterationT-storm-20261010/ITERATIONT_STORM_CLOUD_STUDY.md](../skills/minecraft-volumetric-environment/references/research/iterationT-storm-20261010/ITERATIONT_STORM_CLOUD_STUDY.md)
- [references/research/iterationT-storm-20261010/manifest.json](../skills/minecraft-volumetric-environment/references/research/iterationT-storm-20261010/manifest.json)
- [references/research/iterationT-storm-20261010/weather-parameters.csv](../skills/minecraft-volumetric-environment/references/research/iterationT-storm-20261010/weather-parameters.csv)
- [references/research/vortex-waterspout-20261011/cloud-iteration-source-manifest.json](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/cloud-iteration-source-manifest.json)
- [references/research/vortex-waterspout-20261011/CLOUD_ITERATION_RESEARCH.md](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/CLOUD_ITERATION_RESEARCH.md)
- [references/research/vortex-waterspout-20261011/ITERATION2_RESULT.md](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/ITERATION2_RESULT.md)
- [references/research/vortex-waterspout-20261011/sources.json](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/sources.json)
- [references/research/vortex-waterspout-20261011/STUDY.md](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/STUDY.md)
- [references/research/vortex-waterspout-20261011/visible-water-sources.json](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/visible-water-sources.json)
- [references/research/vortex-waterspout-20261011/VISIBLE_WATER_STUDY.md](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/VISIBLE_WATER_STUDY.md)
- [references/research/vortex-waterspout-20261011/WATER_BODY_REVISION.md](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/WATER_BODY_REVISION.md)
- [references/research/vortex-waterspout-20261011/WATER_FUNNEL_RESEARCH.md](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/WATER_FUNNEL_RESEARCH.md)
- [references/research/vortex-waterspout-20261011/WATER_ITERATION.md](../skills/minecraft-volumetric-environment/references/research/vortex-waterspout-20261011/WATER_ITERATION.md)
随附资源：`world-volume-lab`（68 个文件）。

## 高度场水波 · `minecraft-water-ripples`

从自然语言制作 Minecraft 高度场水波、落水涟漪、雨滴干涉和浅水回波，提供共享参数调试网页、预设、两种波动求解器和 Forge 1.20.1 可构建工程。用于可调式水面特效；不把二维波动系统当作完整流体、真实世界折射或焦散。

[完整 Skill](../skills/minecraft-water-ripples/SKILL.md)
工具脚本：

- [scripts/author.cjs](../skills/minecraft-water-ripples/scripts/author.cjs)
说明与验证：

- [references/forge.md](../skills/minecraft-water-ripples/references/forge.md)
- [references/parameters.md](../skills/minecraft-water-ripples/references/parameters.md)
- [references/research.md](../skills/minecraft-water-ripples/references/research.md)
随附资源：`forge-1.20.1`（26 个文件）。

## 体积特效性能优化 · `minecraft-vfx-performance`

优化 Minecraft 客户端体积特效的 GPU 开销，尤其第一人称近距离、全屏覆盖与外部光影合成；包含低分辨率积分、深度重建、包围盒裁剪和实机质量/耗时验证。用于已有渲染器的性能修复，不代替普通玩法开发或纯粒子数量调参。

[完整 Skill](../skills/minecraft-vfx-performance/SKILL.md)
说明与验证：

- [references/volume-depth-composite.md](../skills/minecraft-vfx-performance/references/volume-depth-composite.md)

## 工作台、研究索引与预览

下表路径相对 `rendering/`，统一使用启动器的编号打开。研究入口用于阅读和设计，不代表对应算法全部已实现。

| 编号 | 内容 | 路径 | 服务方式 |
|---|---|---|---|
| 1 | 天空 | [cosmic-sky-lab/workbench/index.html](../rendering/cosmic-sky-lab/workbench/index.html) | sky |
| 2 | 体积云雾 | [volumetric-environment-lab/workbench/index.html](../rendering/volumetric-environment-lab/workbench/index.html) | volume |
| 3 | 闪电 | [../skills/minecraft-lightning/assets/workbench/index.html](../rendering/../skills/minecraft-lightning/assets/workbench/index.html) | static |
| 4 | 黑洞 | [../skills/minecraft-black-hole/assets/preview/index.html](../rendering/../skills/minecraft-black-hole/assets/preview/index.html) | static |
| 5 | 能量特效 | [energy-vfx-lab/index.html](../rendering/energy-vfx-lab/index.html) | energy |
| 6 | 水波 | [water-ripple-lab/workbench/index.html](../rendering/water-ripple-lab/workbench/index.html) | water |
| 7 | 拖尾与光束 | [trail-beam-lab/workbench/index.html](../rendering/trail-beam-lab/workbench/index.html) | static |
| 8 | 奇幻材质 | [../skills/minecraft-cosmic-material/assets/fantasy-lab/index.html](../rendering/../skills/minecraft-cosmic-material/assets/fantasy-lab/index.html) | fantasy |
| 9 | 星空材质 | [../skills/minecraft-cosmic-material/assets/authoring-lab/index.html](../rendering/../skills/minecraft-cosmic-material/assets/authoring-lab/index.html) | cosmic |
| 10 | 描边 | [../skills/minecraft-cosmic-material/assets/outline-lab/index.html](../rendering/../skills/minecraft-cosmic-material/assets/outline-lab/index.html) | static |
| 11 | 能量护盾 | [../skills/minecraft-stylised-shield/assets/forge-1.20.1/workbench/index.html](../rendering/../skills/minecraft-stylised-shield/assets/forge-1.20.1/workbench/index.html) | static |
| 12 | 区域材质 | [local-environment-lab/workbench/index.html](../rendering/local-environment-lab/workbench/index.html) | static |
| 13 | 碎晶折射 | [../skills/minecraft-crystal-refraction/assets/lab/index.html](../rendering/../skills/minecraft-crystal-refraction/assets/lab/index.html) | static |
| 14 | 玻璃边光 | [../skills/minecraft-glass-rim/assets/lab/index.html](../rendering/../skills/minecraft-glass-rim/assets/lab/index.html) | static |
| 15 | 原画线稿 | [../skills/minecraft-illustration-render/assets/forge-1.20.1-lab/workbench/index.html](../rendering/../skills/minecraft-illustration-render/assets/forge-1.20.1-lab/workbench/index.html) | static |
| 16 | 天空预设 | [cosmic-sky-lab/workbench/sky-library.html](../rendering/cosmic-sky-lab/workbench/sky-library.html) | sky |
| 17 | 星空盾牌 | [../skills/minecraft-cosmic-material/assets/shield-presets/index.html](../rendering/../skills/minecraft-cosmic-material/assets/shield-presets/index.html) | static |
| 18 | 渡劫黑云 | [volumetric-environment-lab/workbench/tribulation/index.html](../rendering/volumetric-environment-lab/workbench/tribulation/index.html) | volume |
| 19 | 龙卷风 | [volumetric-environment-lab/workbench/vortex/index.html](../rendering/volumetric-environment-lab/workbench/vortex/index.html) | volume |
| 20 | 能量研究 | [energy-vfx-research/index.html](../rendering/energy-vfx-research/index.html) | research |
| 21 | 白洞研究 | [white-hole-research-20261007/index.html](../rendering/white-hole-research-20261007/index.html) | static |
| 22 | 水龙卷研究 | [research/github-water-vortex-20261011/index.html](../rendering/research/github-water-vortex-20261011/index.html) | static |
| 23 | 异空间验证 | [../skills/minecraft-local-subscene/assets/gpu-probe/index.html](../rendering/../skills/minecraft-local-subscene/assets/gpu-probe/index.html) | static |
| 24 | 动态照明预览 | [research/render-skill-authoring/validation/minecraft-dynamic-lighting/index.html](../rendering/research/render-skill-authoring/validation/minecraft-dynamic-lighting/index.html) | static |
| 25 | Bloom 预览 | [research/render-skill-authoring/validation/minecraft-selective-bloom/index.html](../rendering/research/render-skill-authoring/validation/minecraft-selective-bloom/index.html) | static |
| 26 | 粒子预览 | [research/render-skill-authoring/validation/minecraft-procedural-particles/index.html](../rendering/research/render-skill-authoring/validation/minecraft-procedural-particles/index.html) | static |
| 27 | 动画适配预览 | [research/render-skill-authoring/validation/minecraft-animated-material-adapter/index.html](../rendering/research/render-skill-authoring/validation/minecraft-animated-material-adapter/index.html) | static |
| 28 | 御体流光 | [spirit-guard-glint-lab/index.html](../rendering/spirit-guard-glint-lab/index.html) | static |
| 29 | 异火缠绕 | [fire-coil-lab/index.html](../rendering/fire-coil-lab/index.html) | firecoil |
| 30 | 炼丹炉特效 | [alchemy-vfx-lab/index.html](../rendering/alchemy-vfx-lab/index.html) | alchemy |
| 31 | 修仙特效参考库 | [energy-vfx-research/修仙特效参考库-20261013/index.html](../rendering/energy-vfx-research/修仙特效参考库-20261013/index.html) | static |
| 32 | 彩色流光工作台 | [../skills/minecraft-colored-glint/assets/workbench/index.html](../rendering/../skills/minecraft-colored-glint/assets/workbench/index.html) | static |

## 独立源码工程

- [cosmic-sky-lab](../rendering/cosmic-sky-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [volumetric-environment-lab](../rendering/volumetric-environment-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [energy-vfx-lab](../rendering/energy-vfx-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [water-ripple-lab](../rendering/water-ripple-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [trail-beam-lab](../rendering/trail-beam-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [local-environment-lab](../rendering/local-environment-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [spirit-guard-glint-lab](../rendering/spirit-guard-glint-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [fire-coil-lab](../rendering/fire-coil-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [alchemy-vfx-lab](../rendering/alchemy-vfx-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [vfx-palettes](../rendering/vfx-palettes/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [black-hole-lab](../rendering/black-hole-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [colored-glint-lab](../rendering/colored-glint-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [cosmic-material-lab](../rendering/cosmic-material-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [inkrealm-lab](../rendering/inkrealm-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [item-scene-window-lab](../rendering/item-scene-window-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [lightning-lab](../rendering/lightning-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [lab](../rendering/lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。
- [vfx-workbench-lab](../rendering/vfx-workbench-lab/)：开发工程、GLSL、配方及存在的 Forge 接入源码。构建环境与项目依赖见各工程说明；项目专用适配不是通用即装模组。

## 五行仙途中的特效开发和优化记录

本次收录 51 个相关报告目录，保留原始结论、失败尝试和测试数据。部分原始报告同时涉及 UI/玩法修复，保留上下文，不将这些内容计入渲染成果。源路径和版本号是当时的证据标识，不是当前安装步骤。

- [cloud-black-fix-20261014](wuxing-vfx-records/cloud-black-fix-20261014/)：2 个记录文件。
- [cloud-glyph-client](wuxing-vfx-records/cloud-glyph-client/)：3 个记录文件。
- [cloud-performance-source-check-20261011](wuxing-vfx-records/cloud-performance-source-check-20261011/)：4 个记录文件。
- [cloud-solas-20261014](wuxing-vfx-records/cloud-solas-20261014/)：2 个记录文件。
- [cold-sun-colors-20261014](wuxing-vfx-records/cold-sun-colors-20261014/)：7 个记录文件。
- [exotic-burn-anchor](wuxing-vfx-records/exotic-burn-anchor/)：6 个记录文件。
- [flight-first-person-20261014](wuxing-vfx-records/flight-first-person-20261014/)：8 个记录文件。
- [flight-flow-stability-20261014](wuxing-vfx-records/flight-flow-stability-20261014/)：8 个记录文件。
- [flight-hover-flow-20261014](wuxing-vfx-records/flight-hover-flow-20261014/)：8 个记录文件。
- [flight-surround-flow-20261014](wuxing-vfx-records/flight-surround-flow-20261014/)：10 个记录文件。
- [flight-view-flow-20261014](wuxing-vfx-records/flight-view-flow-20261014/)：14 个记录文件。
- [formation-particle-library-v1](wuxing-vfx-records/formation-particle-library-v1/)：3 个记录文件。
- [furnace-bottom-20261014](wuxing-vfx-records/furnace-bottom-20261014/)：1 个记录文件。
- [furnace-density-20261014](wuxing-vfx-records/furnace-density-20261014/)：14 个记录文件。
- [furnace-near-20261014](wuxing-vfx-records/furnace-near-20261014/)：4 个记录文件。
- [furnace-near-performance-20261014](wuxing-vfx-records/furnace-near-performance-20261014/)：11 个记录文件。
- [furnace-solas-20261014](wuxing-vfx-records/furnace-solas-20261014/)：2 个记录文件。
- [furnace-stability-20261014](wuxing-vfx-records/furnace-stability-20261014/)：24 个记录文件。
- [guard-cloud-20261013](wuxing-vfx-records/guard-cloud-20261013/)：7 个记录文件。
- [guard-impact-client](wuxing-vfx-records/guard-impact-client/)：2 个记录文件。
- [guard-solas-20261014](wuxing-vfx-records/guard-solas-20261014/)：1 个记录文件。
- [guard-tier-20261013](wuxing-vfx-records/guard-tier-20261013/)：7 个记录文件。
- [guard-tier-client](wuxing-vfx-records/guard-tier-client/)：3 个记录文件。
- [guard-tier-color-20261013](wuxing-vfx-records/guard-tier-color-20261013/)：3 个记录文件。
- [guard-world-fix-20261013](wuxing-vfx-records/guard-world-fix-20261013/)：14 个记录文件。
- [performance-20261014](wuxing-vfx-records/performance-20261014/)：28 个记录文件。
- [realm-flight-camera-blackout-20261009](wuxing-vfx-records/realm-flight-camera-blackout-20261009/)：23 个记录文件。
- [realm-flight-design-20261008](wuxing-vfx-records/realm-flight-design-20261008/)：12 个记录文件。
- [realm-flight-design-20261009](wuxing-vfx-records/realm-flight-design-20261009/)：2 个记录文件。
- [realm-flight-flow-20261009](wuxing-vfx-records/realm-flight-flow-20261009/)：39 个记录文件。
- [realm-flight-front-20261010](wuxing-vfx-records/realm-flight-front-20261010/)：52 个记录文件。
- [realm-flight-input-fix-20261008](wuxing-vfx-records/realm-flight-input-fix-20261008/)：18 个记录文件。
- [realm-flight-no-lance-20261012](wuxing-vfx-records/realm-flight-no-lance-20261012/)：1 个记录文件。
- [realm-flight-performance-20261010](wuxing-vfx-records/realm-flight-performance-20261010/)：13 个记录文件。
- [realm-flight-turn-seam-20261010](wuxing-vfx-records/realm-flight-turn-seam-20261010/)：17 个记录文件。
- [realm-meteor-complete-20261008](wuxing-vfx-records/realm-meteor-complete-20261008/)：23 个记录文件。
- [realm-meteor-reference-20261009](wuxing-vfx-records/realm-meteor-reference-20261009/)：127 个记录文件。
- [realm-meteor-sphere-20261009](wuxing-vfx-records/realm-meteor-sphere-20261009/)：19 个记录文件。
- [realm-meteor-tube-20261009](wuxing-vfx-records/realm-meteor-tube-20261009/)：6 个记录文件。
- [shield-impact-client](wuxing-vfx-records/shield-impact-client/)：4 个记录文件。
- [shield-shatter-20261013](wuxing-vfx-records/shield-shatter-20261013/)：4 个记录文件。
- [shield-shatter-client](wuxing-vfx-records/shield-shatter-client/)：4 个记录文件。
- [skill-particle-library-v1](wuxing-vfx-records/skill-particle-library-v1/)：3 个记录文件。
- [skill-particle-library-v2](wuxing-vfx-records/skill-particle-library-v2/)：3 个记录文件。
- [skill-particle-library-v3](wuxing-vfx-records/skill-particle-library-v3/)：8 个记录文件。
- [sword-flight-20261009](wuxing-vfx-records/sword-flight-20261009/)：3 个记录文件。
- [sword-trail-client](wuxing-vfx-records/sword-trail-client/)：5 个记录文件。
- [tribulation-install-20261010](wuxing-vfx-records/tribulation-install-20261010/)：12 个记录文件。
- [weapon-flame-0.2](wuxing-vfx-records/weapon-flame-0.2/)：1 个记录文件。
- [weapon-flame-layered-v2](wuxing-vfx-records/weapon-flame-layered-v2/)：2 个记录文件。
- [weapon-flame-modular-v1](wuxing-vfx-records/weapon-flame-modular-v1/)：1 个记录文件。

## 化虹、流星与起遁/到达 · `minecraft-huahong`

2026-10-19新增，V8研究原型。包含红黑烟、密集羽丝、赤焰、长虹、双流、液光、碎芒、扇流，以及独立起遁/到达控制。来源、许可证及浏览器/游戏边界分别记录，不新增角色或载体模型。

[Skill](../skills/minecraft-huahong/SKILL.md) · [工作台](../skills/minecraft-huahong/assets/prototype/README.md) · [验证](../skills/minecraft-huahong/references/validation.md)
