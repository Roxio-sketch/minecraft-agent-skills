# 验收与来源

2026-09-30：独立 Three.js 预览的 11 项自动检查通过，桌面 1280×800 与手机 390×844 截图已人工检查。测试使用本机 Edge/Playwright；GL 错误采样为 0。证据在 `E:/着色器渲染/research/glass-rim/evidence/validation.json`，同目录 desktop.png 和 mobile.png。未构建或运行 Forge 玻璃边缘亮 mod，不能继承其他技能的游戏认证。

来源启发：用户 ArcaneVortex 0.6.8 的 rendertype_glass_sphere 顶点/片元 shader，原归档 SHA-256 为 27c5d799a488a418a588b978bb77da6bd48bbe48d8bc5c6a11cfb7b6f60a8b97。其片元按法线和视线点积产生边缘权重；不是场景折射。原项目 All Rights Reserved，本技能未复制其源码或素材。

附带 rim.glsl 和制作台为独立实现。Three.js 是 MIT 第三方依赖，许可随 vendor 保存。预览的透明球、方块和网格不是 Minecraft 截图。

独立验收：球面边缘比中心明显；power 加大使带变窄；gain=0 去掉边缘颜色；alpha 全零恢复背景；time 固定可复现；pulseAmount=0 无时间变化；开启脉动会变化。旋转硬边方块与非均匀缩放检查法线，正交模式对平面使用恒定视线。使用实际 16/32 px framebuffer 渲染小图，不用大图缩放冒充。

运行 scripts/validate_preview.cjs，参数为 URL 与输出目录；需要 Node、Playwright 和 Chromium。保存 GPU 画面、像素差异及 desktop/mobile 截图。技能格式检查不替代这些观察。

Forge 验收另行记录：矩阵、格式、uniform、透明排序、遮挡、资源重载、GUI/手持/穿戴范围、其他透明模型与 shader pack。壳消失后无残影，后续普通物品颜色不污染。没有实测的类别标为未验证。
