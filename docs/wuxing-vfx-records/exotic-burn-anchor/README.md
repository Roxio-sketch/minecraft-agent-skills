# 点燃定位最终证据

- red-orbit-review.png：最终橙红异火，原版生物渲染器，8 个环绕方向。
- blue-custom-orbit-review.png：最终蓝色异火，不触发 RenderLivingEvent 的自定义实体渲染器，8 个环绕方向。
- 每个图格上半为全景、下半为目标裁剪；绿色框仅为截图审阅标记，来自实体原点的投影，不会出现在游戏中。
- burn-anchor-final-*-0.png：附魔武器第一人称待机，无旧缠绕外观；*-3.png：实际攻击；*-14.png：到期熄灭。
- views.csv：帧序号、客户端 tick、相机 yaw、pitch、实体投影框 left/top/right/bottom。完整最终 30 帧留在 build/weapon-flame-client/captures。
- result.txt：隔离客户端的运行断言结果；artifact-verification.json：成品完整性与移除内容检查。

实现及验证边界见 docs/audits/current/20260928_BURN_ANCHOR_RETIRE_WRAP.md。
