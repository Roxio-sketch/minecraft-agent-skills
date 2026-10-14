# 物品内三维场景窗口：Forge 1.20.1 验收

2026-09-29 最终运行 `20260929-084618` 已通过。目标为 Minecraft 1.20.1、Forge 47.4.10、Java 17；客户端为 ASTRA-TEST，存档为 ASTRA_VFX_TEST。

10 个场景完成，独立检查 21/21 通过，生产 JAR 与部署文件 SHA256 一致：

`054c07ea337bbbc43012ce395bda5d5b3296299e070d2d540778b1e86f27413a`

已查看实际 GUI、左右手和第三人称截图。最终模板包括可嵌套 box/任意三角 mesh/group、旋转动画、off-axis/fixed/orbit 相机和四种开口；其中 off-axis 路线、矩形/圆/菱形/PNG 遮罩已在本轮游戏中显示。

- [完整证据与限制](skill/minecraft-item-scene-window/references/runtime-evidence.md)
- [实际截图组合](skill/minecraft-item-scene-window/references/evidence/contact-sheet.jpg)
- [最终主画面](item-scene-window-lab/evidence/20260929-084618/front.png)
- [右手](item-scene-window-lab/evidence/20260929-084618/hand-right.png) / [左手](item-scene-window-lab/evidence/20260929-084618/hand-left.png) / [第三人称](item-scene-window-lab/evidence/20260929-084618/third-person.png)
- [原始数值](item-scene-window-lab/evidence/20260929-084618/results.json) / [独立验证](item-scene-window-lab/evidence/20260929-084618/independent-validation.json)
- [工程与构建说明](item-scene-window-lab/README.md)
- [可复用 skill](skill/minecraft-item-scene-window/SKILL.md)

这次认证限于专用 window 物品的微缩三维场景；任意物品适配、真实 Minecraft 世界子视图、递归镜面等仍需实现对应 adapter/backend 并重新验收。示例是几何语言演示，不是最终美术质量上限。
