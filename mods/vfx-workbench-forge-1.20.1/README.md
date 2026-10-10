# VFX Workbench 0.1.0

Minecraft Forge 1.20.1，Java 17；开发构建使用 Forge 47.4.10。客户端调试模组，包含黑洞、闪电和现有水波实验。

- F6：打开参数界面；F7：触发最后选择的效果；F8：停止所选效果。按键可重绑，聊天、背包及菜单中不触发。
- 选择记录：`config/vfxworkbench/ui.json`；配方：`config/vfxworkbench/presets/<skill>/last.json`。
- 与 MCP-HELPER 同装时检查 F8 是否冲突，在 Minecraft 控制设置中重绑。

构建：设置 JAVA_HOME 到 Java 17 后运行 `./gradlew build`（Windows：`./gradlew.bat build`）。需要访问 Forge Maven。公开源码副本移除了本机临时目录和禁用证书检查的设置；不包含开发存档、日志及历史机器专用审计脚本。

随附 JAR 是原项目已构建的制品，本审查未从整理后的源码重新构建。保留 `LIGHTNING_THIRD_PARTY_NOTICES.md` 及 JAR 内第三方声明。独立 skill 模板的审计入口仍限制专用测试世界 ASTRA_VFX_TEST，不能直接在重要存档运行。

2026-10-10：同为内部版本 0.1.0 的更新制品，以 Release 日期区分；最新变更见 ../../docs/update-20261010.md。
