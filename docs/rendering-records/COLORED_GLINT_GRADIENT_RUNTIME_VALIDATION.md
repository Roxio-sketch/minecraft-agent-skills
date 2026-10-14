# 自定义与渐变附魔流光：游戏复现结果

2026-09-29（Asia/Shanghai）在用户选择的 **ASTRA-TEST / ASTRA_VFX_TEST** 完成新四套配方实测。环境为 Minecraft 1.20.1、Forge 47.4.10、Java 17、NVIDIA RTX 4060 Ti。跑次 `20260929-023739`，15 场景原图与 73 项证据检查通过。

![四套配方在游戏内同屏显示](colored-glint-lab/evidence/20260929-023739/gui-colors-b.png)

## 本轮成果

| 配方 ID | 模式 | 配色与周期 |
|---|---|---|
| `rose_gold` | 自定义单色 | `#F5A7B8` |
| `aurora` | 空间渐变 | `#32FFD2` → `#4D8DFF` → `#DA56FF`，线性 RGB 插值、往返、斜向 |
| `rainbow_cycle` | 整体随时间变色 | 七色标闭合彩虹，HSV 最短路径，6 秒循环 |
| `ember_wave` | 流动渐变 | `#D61E45` → `#FF8D30` → `#FFE09A`，4 秒往返、2 条色带、强度 0.9 |

源配方保存在 `colored-glint-lab/recipes/`。它们是示范；skill 的 `author_glint.py` 仍接受任意合法 HEX 和 2–16 色标，支持色标占比、三种插值、方向、循环、周期与强度。

Lab 0.2.0 已构建、执行 reobfJar 并安装到选定实例。项目与安装 JAR 的 SHA-256 一致：

`17ebe3a68b0a3f9379dc6e34c0003c1efc906a6ecf2af6bc8f6c958851210679`

静态两配方使用生成 PNG。动态两配方使用中性载体及独立 shader；时间 uniform 在实际 draw setup 时设置。有限注册表共 57 层（11 套静态 × 5 模式，加 2 套动态 × direct）。

## 验证结果

- GUI 同屏四种配方外观可区分，各剑 ROI 均有 11,132 个像素随所选两帧变化（通道差 >2/255）。强制彩虹流光可见；两个无流光对照没有像素变化；未知 ID 与原版回退的差异不超过 1/255。
- 右手玫瑰金/彩虹、左手极光、第三人称余烬、满弓、掉落物与展示框均有对应截图和绘制上下文。满弓持续 61 tick，模型解析为 `minecraft:item/bow_pulling_2`。GUI 中还保留了 Cosmic 星空剑与方块叠加展示。
- 两次资源重载后，原版 direct 与两个动态 shader 实例均更换，配色仍可见。
- 实际 draw 时间采样覆盖 6 秒和 4 秒周期并记录回绕；暂停两次采样保持一致，继续后恢复推进。原版 glint UV 仍滚动，因此暂停只冻结新增的配色时间，不冻结整个画面。
- 15 个截图点的 GL error 均为 0；不是全帧 GPU 调试或性能基准结论。
- 背包、选中槽、模式、位置及三个测试实体的恢复检查均为 true。测试前完整存档备份在 `colored-glint-lab/evidence/gradient-preflight/ASTRA_VFX_TEST-before.zip`，旧 JAR 也已备份。

![手持、世界及暂停续播场景](colored-glint-lab/evidence/20260929-023739/gradient-runtime-contact-sheet.png)

## 证据与复跑

- [原始运行记录](colored-glint-lab/evidence/20260929-023739/results.json)
- [73 项检查结果](colored-glint-lab/evidence/20260929-023739/validation.json)
- [构建日志](colored-glint-lab/evidence/build-gradients.log)
- [部署记录](colored-glint-lab/evidence/gradient-deployment.json)
- [操作与命令](colored-glint-lab/README.md)
- [Skill 配色创作指南](skill/minecraft-colored-glint/references/authoring.md)

在实例中运行 `/coloredglint gradients` 打开实时展示；Esc 返回世界。`/coloredglint verify_gradients` 运行相同的 15 场景测试，会临时改变玩家状态，因此先备份存档。证据检查命令：

```powershell
python colored-glint-lab/scripts/validate_runtime.py colored-glint-lab/evidence/20260929-023739 --deployment colored-glint-lab/evidence/gradient-deployment.json
```

测试结束后恢复原显示设置并保留渐变展示屏。世界时间和可能解锁的配方/进度不整体回滚。

## 证据边界

新增动态 shader 当前仅接入 direct，其他 glint 模式保留原版效果。空间/流动配色使用滚动 glint UV，不是固定剑柄到剑尖的模型坐标。穿戴护甲、BEWLR、第三方骨骼渲染、即时 BufferSource、光影包和其他 Minecraft 版本未验证。完整周期有时间采样及离线 GPU 数学验证，没有逐帧录像验证每个周期接缝；也没有超长世界时间专项运行。

旧九色的 `20260929-011858` 证据保留独立；本轮只重新执行其证据检查（56 项通过），没有重新启动一次旧九色游戏验收。新生成器的 26 项回归、原调色器 12 项测试和 250 个离线 GPU 色值采样亦与游戏运行证据分别记录。
