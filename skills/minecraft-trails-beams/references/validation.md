# 证据与验收

先记录目标场景和预期，再按层验证，不要求所有任务都跑完整兼容矩阵。

## 行为与构建

- scripts/verify_authoring.py：自定义颜色、多节点曲线、图案/纹理、负流速、严格字段、输出目录保护、生成工程完整性。
- scripts/verify_workbench.cjs：可选 Playwright+浏览器检查工作台七种起点、编辑交互、导入导出、画布非空和截图。缺少浏览器时此项不等于通过。
- `path.json` 与 `recipe.json` 分开核对；必须用游戏配置加载导出结果，不能仅证明 Canvas 中看起来有分叉。
- 生成工程的 gradlew coreCheck：时间重复和倒退、距离细分、容量、瞬移断带、停发后过期、UV 淘汰连续、重复点、端向相机、blade 挂点、解析与 alpha 消散。
- gradlew build：实际 Forge 1.20.1 编译、coreCheck、生产 reobf。编译通过不证明 shader 或画面正确。

## 真实画面

1. 所需截面、颜色/宽度曲线、原纹理、叠加层分别看图。普通控制样本应不受影响。
2. 固定 t 看稳定性，推进 t 看路径/纹理各自变化；30/60/144 FPS 检查采样数量和外观。CPU 不变性测试不代替三档实际游戏观测。
3. 停止发射后自然消失；瞬移/跨维度/退出不连成长带；无 emitter 时不残留。
4. 相机沿束看、倒看、接近急弯，检查重复点和退化，不出现 NaN、爆宽或翻片；透明自交记录局限。
5. 遮挡物前/后、半透明世界、左右手/FOV/第三人称，覆盖用户要求的入口。光影包独立测试，不把 GL error=0 作为画面正确证明。
6. F3+T、窗口尺寸变化、世界重进、失焦暂停。程序 shader 的代次、错误日志与实际截图配合记录。
7. 统计同屏 emitter、点数/层数/覆盖、draw 与 CPU/GPU 成本；没有 benchmark 不报告 FPS 保证。

## 内置客户端视觉实验

`/trailbeam verify` 仅在 ASTRA_VFX_TEST 本地存档可运行；记录五个 framebuffer 截图和 results.json。也可写 `<gameDir>/trail-beam-lab/request.json` 为 `{"action":"verify"}`，客户端消费后删除。

它会临时切换 Screen 与 pauseOnLostFocus，结束恢复；几何是客户端示例，无实体/方块/背包修改。但游戏时间正常推进，不把存档描述为逐字节不变。运行前仍保留测试存档备份。

case 包括固定时间画廊、另一时间、世界解析刀刃/曲线束、资源重载后、关闭几何控制图。文件名 expired.png 是关闭几何控制图，**不是自然生命周期消散的独立 GPU 证明**；自然过期由核心行为检查覆盖，实际武器验收时仍要录制。

本实验的世界刀刃是数学函数生成的两挂点，无法证明任意武器、骨骼或第一人称接入。记录准确信息，测试没覆盖的内容不补写通过。

保存截图和 results.json 后可运行：

```powershell
python scripts/verify_runtime.py --run D:/Evidence/trailbeam-run --out D:/Evidence/trailbeam-validation.json
```

该脚本需要 Pillow；检查场景集合、截图尺寸、采样点 GL 错误、shader 重载代次、世界绘制和像素变化，还核对加载遮罩已退出、固定时间的重载前后画面（最多容许一个 8-bit 舍入步长）、关闭几何后的背景。统计通过不能证明画面美观、挂点准确或所有时刻无 GL 错误。

`reloadResourcePacks()` 的 future 完成不等于 LoadingOverlay 已消失。验收等待 `Minecraft.getOverlay()==null` 后重新累计稳定帧，不能只按帧数或 shader 代次截取；实际观察到过加载遮罩进入“重载通过”图片的误判。
