# 灵力御体 I–IV 实机分级验收

所有图像源于 `reports/guard-tier-client` 的13张真实GPU截图。`tools/analyze_guard_tier_capture.py` 验证全部帧、四次真实生产受击包、全部GL错误为0、世界绘制、四档逐件装备附魔读回，以及每档两个常态帧间可见云纹变化。

- `tier-N-preview.png`：该档常态A、常态B、真实受击三帧。
- `tier-N-motion.gif`：三张真实采样帧，间隔400ms；只循环真实帧，无插值或新增动作。
- `guard-tier-game-comparison.png`：四档相同shader clock的常态A。
- `guard-tier-real-hits.png`：四档真实受击包后6 tick画面。
- `guard-tier-mixed-slots.png`：头盔I、胸甲II、护腿III、靴子IV同一角色混装。
- `runtime-validation.json`：逐档像素变化、四次生产包、等级读回、原图SHA256、PNG原像素相等检查和GIF量化误差。
- `diagnostic-slot-crops.png` 与 `diagnostic-chest-II-mixed-III.png`：各slot及胸甲的原像素诊断对照。
- `diagnostic-chest-blue-diff.png` 与 `diagnostic-slot-analysis.json`：仅蓝纹RGB差>12的定位掩码与原图bbox，属于诊断可视化，不是游戏效果预览。

PNG游戏区域使用原帧1:1 RGB裁剪粘贴，不重采样、不调色、不美化、不绘制特效。标题仅位于截图之外。GIF最多256色，必然存在量化；RGB颜色验收以原始PNG与无损排版PNG为准。两帧常态加一帧受击为抽样展示，不代表完整连续录像。原镜头底边裁掉部分靴子，预览未补画。

本次仅验收Oculus缓冲世界中的铁甲正面；不借用此前guard-cloud测试的重载、遮挡、背面、背包或其他材质证据。外部光影包、任意第三方装备UV不在本次验收范围。
