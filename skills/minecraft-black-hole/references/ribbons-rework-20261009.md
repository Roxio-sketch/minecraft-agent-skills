# 层叠光带调整 · 2026-10-09

参考为 WittyScrap/unity-black-hole-shader，本地快照 Assets/Black Hole/BlackHole.shader 的 sample_accretion_disk / accretion_disk：径向拉伸噪声、统一角向平流、内外缘衰减和侧向 Doppler 明暗。原创实现未复制其噪声贴图。

移除程序光带主菜单项，历史 form=classic 和显式 diskStyle 参数继续允许导入。层叠光带改为一维多尺度径向随机纹理，角向只弱调亮度，不再用两组余弦载波和相位扰动形成编织网格。一维噪声每频段只有两次 hash，保留固定 seed 和有界运动时钟。

层叠光带独立预设为 ribbons-default.json。盘面加入连续侧向明暗，移除 2.08*core 的额外近核图像过滤，实际核心捕获、地形遮挡和射线积分仍生效。不宣称这是相对论精确求解器或 Unity 渲染器逐像素复刻。

网页与宿主使用相同盘纹。网页背景和 Bloom 仍不同于真实世界；浏览器验证不替代 Minecraft 运行验收。
