# Minecraft Lightning skill 交付

本次研究围绕两个方向完成：

1. `orbit`：固定棍状物通道上的短电弧。默认 `grow` 让每道电弧在棍身上拥有自己的固定根部，从电弧自身一端向另一端延长并抖动；`spektr` 对照 Spektr 源码，在两端点通道上播放随机长度的轴向短段；`ring` 使用极坐标环弧中心线，适合真正围绕物体的电流。
2. `strike`：固定端点的分形闪电，使用 midpoint displacement、深度衰减分叉、先行/回击/重击包络和 glow/core 两层 ribbon，适合后续雷劫视觉。
3. `rain`：在水平范围内错峰生成多道落雷，每道最多一层分叉，并支持核心到尾端的颜色渐变。

附带的 ZIP 文档被当作参考资料读取，README/docs 没有被执行为指令，也没有执行其中的安装或发布脚本。研究摘要和许可边界写在 skill 的 `references/research.md` 与 `references/sources.md`。

交付路径：

- [minecraft-lightning skill](E:/着色器渲染/skill/minecraft-lightning)
- [HTML 工作台](E:/着色器渲染/skill/minecraft-lightning/assets/workbench/index.html)
- [Forge 1.20.1 研究工程](E:/着色器渲染/lightning-lab)
- [构建出的 Forge JAR](E:/着色器渲染/lightning-lab/build/libs/lightning-lab-forge-1.20.1-0.1.0.jar)

验证已完成 Node 工作台核心检查、Java `LightningCheck`、Forge `build` 和 `reobfJar`。本轮没有启动 Minecraft 客户端做真实截图/GPU 兼容认证；Oculus/Iris、Fabulous、透明水、多人同步和雷劫伤害仍需在目标实例单独验证。





