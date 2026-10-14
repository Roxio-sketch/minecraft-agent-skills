# 水波材质交付：澄浪水镜

2026-09-30。已研究用户提供的 `Water-main.zip` 和 `ocean-simulation-master.zip`，在现有工坊增加 `tidal-water / mode 12`；保留全部原有材质，现为十三种奇幻材质加三种内部星空。

[打开可调水波工坊](http://127.0.0.1:55937/?preset=tidal-water&rev=water)。当前服务 PID 28852，动态端口独占绑定；服务健康摘要以 `/__cosmic__/health` 的实时值为准。旧页面中的编辑状态不强制刷新。水波参数区提供“平静浅潭 / 青碧风浪 / 深海急浪”三个起点，可继续调参和导出。

## 外观与参数

水波使用两个固定种子的复高斯频谱域和 128×128 radix-2 逆 FFT：长浪域叠加短浪域，按有限水深色散随时间演化，再以共享窗口射线求交。浪峰收紧、解析法线、日光闪点、波峰泡沫和虚拟沙底折射都从同一高度场导出。现在的九个水面字段为波高、波长、风向、浪尖挤压、泡沫覆盖、水面粗糙度、水面不透明度、光照方位和光照仰角；还有细浪强度、风浪散角、水深、速度、颜色和曝光。浏览器 → 配方 JSON → 资源包 → Forge draw 的字段已完整传递。

这是一层虚拟水面材质窗口，保留物品几何和遮罩；没有 FFT、大海面几何位移、真实场景倒影、碰撞涟漪或泡沫历史模拟。数学、参考项目差异、许可边界和自然语言设计方法见 [水波制作研究](skill/minecraft-cosmic-material/references/water-material.md)。参考文件仅用于研究，未复制其实现到 skill。

![水波动态](skill-validation/water-release/motion-sheet.webp)

以上为固定时间六秒片段，20 samples/s，不是实时帧率测量，也不保证片段首尾无缝。三种水况配方与原图在 `skill-validation/water-release/{calm,windy,storm}.{json,png}`。

## 预览与技能验收

- 最新内核十三材质回归：`skill-validation/water-regression-delivery/validation.json`，121 项通过，包含真实 16/32 px 重渲染、零速度冻结、观察响应和重载配方。
- 水波按钮与六参数：`skill-validation/water-opacity-refined2/water-validation.json`，32 项通过。零波高静止、风向 0/360 完全一致、参数极值、导出导入及桌面/手机布局均通过。
- 独立安装技能执行：`skill-validation/water-installed/water-validation.json`，29 项通过。
- 原星空分组回归：`skill-validation/water-workshop/validation.json`，14 项通过。
- 资源包生成器：9 项单元测试通过，水波字段往返与缺失/越界/非有限/错误类型拒绝均覆盖。
- 服务测试 6 项通过；skill quick_validate 通过。新增 `validate_water.cjs` 随技能打包。

水波外观在最终图像中已检查：陡浪没有初稿的硬切法线亮斑，连续序列能追踪泡沫带传播及日光亮点变化。自动像素变化测试不作为美术评分。

## Forge 1.20.1 实测

已在用户授权的 ASTRA-TEST / ASTRA_VFX_TEST，Forge 47.4.10、Java 17、NVIDIA RTX 4060 Ti 上运行。生产与已部署 JAR SHA-256 相同：

`A3CE4FBA314F20361FFB61823ADA2E8A4BB44EFDB10F6B8C2683ADF132A01E33`

- 普通物品：`fantasy-runs/20260930-190731`，10 场景 / 21 项通过。GUI 剑、斧、弓、苹果、方块、护甲物品；第一/第三人称、左右手、满弓、掉落和展示框；资源重载后六种冻结样本均零差异。截图实际 1280×720。
- 玩家恢复：背包、选中槽、游戏模式、位置、测试实体清理，五项均 true。护甲只验证物品 GUI，没有穿戴认证。
- 真实游戏时钟动态：`motion-runs/20260930-190907`，13 项通过。实际 2560×1421，90 张原始 PNG；动态 52 帧，覆盖 5.8448224 秒，shader 时间跨度 5.844 秒。前后冻结均零差异。中位录制间隔 0.1227384 秒，是采样间隔，不是游戏 FPS。
- 冻结跨面：`continuity-runs/20260930-191045`，两个固定时刻各 16 种原生方块旋转，32 案例 / 16,682,478 个内部像素零差异；实际 2560×1421。它验证共享射线窗口的面衔接，不证明物体局部实体体积。

跑次根目录：`D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/cosmic-material-lab/`。三项汇总位于 `research/cosmic-material/water/*-suite-final.json`。

[观看真实游戏动态](D:/MC/库/red.ghs.axolotl/profiles/ASTRA-TEST/cosmic-material-lab/motion-runs/20260930-190907/live-motion.webp)。原生 PNG 保留高分辨率，WebP 按实际采样间隔缩放编码。

首次普通跑次 `20260930-190048` 保留为失败：画面实际 854×480，低于验收下限。修复测试驱动使用 Minecraft `Window.setWindowed` 同步窗口目标尺寸，避免延后的全屏转换重置为启动器尺寸，再完整重跑。没有降低判据。

## 使用

```mcfunction
/give @s minecraft:diamond_sword{CosmicMaterial:{Material:"tidal-water",Mask:"blade"}} 1
/give @s minecraft:diamond_block{CosmicMaterial:{Material:"tidal-water"}} 1
```

自定义配方从工坊导出，再运行：

```powershell
python scripts/export_fantasy_pack.py --recipe water.json --out water-pack.zip --id azure-water
```

启用资源包后重载，再使用新 ID。JSON 参数不直接嵌入 NBT；纯资源包必须与本次支持 mode 12 的 mod 一同使用。实验工程 `cosmic-material-lab` 已更新，技能包提供算法、预览、生成器和验证方法，不内嵌完整 Java 工程。

原 Cosmic P3 的偶发 67/87 像素、最大 1/255 差异仍未确认根因。本次水波冻结成功不关闭该问题。仍没有全物品、多 sprite、BEWLR、穿戴/骨骼或 Oculus/Iris 兼容认证。


## 2026-10-01 自然频谱修订

本次修订将短浪频谱归一化从 0.20 调到 0.10，将横向输运倍率从 2.1 调到 1.1，并改用粗糙度修正的水面 Fresnel；反射天空不再重复加入太阳盘。画面现在由宽缓长浪承载细碎风浪，高光集中在连续浪脊，减少液态金属式的白色碎斑。

- `skill-validation/fantasy-natural-final/validation.json`：十三种材质 121 项通过。
- `skill-validation/water-final-natural/water-validation.json`：水波 30 项通过。
- `skill-validation/water-natural-motion/motion-sheet.webp`：固定时间 6 秒、120 帧、20 samples/s；`motion-filmstrip.png` 保存 0/2/4 秒对照。
- `test_ocean_spectrum.cjs`：9 项通过，两个 128×128 频谱域的虚部残差小于 `2.4e-15`。
- 修订构建包 SHA-256：`65141FA3A44E942C87296A69954E0CA63A6E7EED8EE1BA1C11001CA8ECB70E5D`。

本次包已构建并复制到 ASTRA-TEST 的 mods 目录。由于 2026-10-01 启动 ASTRA-TEST 时客户端在 ModLauncher 初始化后退出，未产生新的 MCP-HELPER 端点和游戏截图；此前 `fantasy-runs/20260930-190731`、`motion-runs/20260930-190907`、`continuity-runs/20260930-191045` 的 Forge 结果属于上一包，不能转写为本次包的运行认证。原 Cosmic P3 仍单独保留。

光照位置新增 waterLightAzimuth 与 waterLightElevation 两个可调字段；水面窗口不再显示天空，缺少交点时继续使用水面采样。


## 2026-10-01 俯视水面修订

水波窗口现在固定为纯俯视水面：移除天空与地平线回退，所有材质面像素都落在同一连续水面上；旧 `waterTilt` 仅作为兼容字段保留，不再显示为控件。新增并实际绑定 `waterLightAzimuth`（光照方位）和 `waterLightElevation`（光照仰角），可直接改变水面高光位置。

`skill-validation/water-topdown-final3` 的水波专项 31 项通过；当前工坊服务为 http://127.0.0.1:52015/?preset=tidal-water&family=fantasy。最新 Forge JAR SHA-256：`EB7FBF688D55B86C4FC621B42168DB300671BBB775453D09430E9A8273CB758C`。本次构建包含 `fmWaterOpacity` 和 mode 13 电视雪花支持。

水面新增 `waterOpacity` / `fmWaterOpacity`，默认 0.90，范围 0.05–1.00。预览用它把水面与暗色底场混合，Forge 片元 alpha 也按该值参与 blend，因此低值会露出承载物或背景，高值保持完整水面覆盖。




