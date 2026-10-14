# 进阶完成与优化 0.6.0

用户在上一轮完成后要求继续完成剩余限制并优化。当前0.5.0主工作台和发布包保持可用，新工作在 next-0.6.0 隔离目录开发，最终验收后同步工作台、Forge、skill、桌面入口及包。

工作范围：有物理长度/介质契约的球形大气透射与多次散射LUT；按Minecraft实际生物群系和高度/屋顶遮盖的程序雨雪；原版透明玻璃/水的实际深度/分层合成；可获得的Oculus/Embeddium及至少一真实光影包的合作或明确降级入口；环境采样与GPU计时优化。

保留旧解析天空和雨效果，完整/4配方明确升级，新进阶选项默认关闭。新的真实性声明绑定数学/数值、独立WebGL与实际Forge证据。完整任意第三方透明shader或所有光影包的保证不从单一测试外推；遇到差异继续适配已选实际实现。

Root拥有schema/metadata、sky-core hook、生成wrapper、浏览器renderer/UI、总体交付；大气agent拥有新physical shader/LUT生成；天气agent拥有新weather shader/数据契约和环境优化；Forgeagent拥有Java管线/实际游戏/兼容与checkers。各自新证据保存 evidence/advanced-sky-20261006。
