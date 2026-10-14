# 幽气性能优化：前两步

2026-10-02，运行版本 0.2.1。只实施测量与保留质量的裁切/缓存；未实施自适应采样或 GPU 流场缓存。

## 结果与范围

RTX 4060 Ti，Forge 1.20.1 / 47.4.10 / Java 17，1280 × 720 独立新建测试世界。全分辨率 resolution=1、steps=128、25 个冻结密度包；64 积分段的原始采样位置与参数保持相同。每例预热 100 帧、测量 260 帧。使用冻结时间、位置与相机进行两轮前后对照。

| 案例 | 体积 GPU 中位耗时 ms | VFX GPU 中位耗时 ms | CPU 提交中位耗时 ms |
|---|---:|---:|---:|
| baseline | 20.256 | 44.185 | 0.202 |
| optimized | 14.721 | 14.781 | 0.239 |
| baseline-repeat | 20.426 | 36.841 | 0.146 |
| optimized-repeat | 14.767 | 14.825 | 0.171 |
| baseline-bloom-off | 20.220 | 22.256 | 0.141 |
| optimized-bloom-off | 14.643 | 14.684 | 0.148 |

体积积分两轮中位耗时均值 20.341 → 14.744 ms，减少 27.5%。屏幕积分区域约占全屏 65.8%，场景复制区域约 70.5%。CPU 因保守投影计算略有增加；本次主要收益在 GPU。

完整平均值、p95/p99、关闭特效基线与帧间隔见 comparison.json。隐藏开发窗口的呈现与驱动调度使整帧间隔和跨 pass 计时波动较大，不将上述数字换算为用户游戏 FPS 或所有场景的性能承诺。

## 实施内容

- 依据已上传密度包的流向椭球支持范围投影屏幕矩形，保留过滤、Bloom 核及扰动采样所需边界；近裁剪面交叉或视点在范围内时回退全屏。
- 场景颜色/深度复制、体积、模糊和合成使用各自保守区域；完整快速清空中间目标，防止移动时残留历史烟/光晕。
- 射线先检查流向椭球；跳过完全为空的原始积分段，保留原 AABB、步长及段端点。密度、内部卷曲算法和气团模拟不变。
- Bloom=0 时跳过两个模糊 pass 与光晕纹理采样；缓存统一变量位置并复用上传数组。

## 质量与状态检查

同一渲染帧先保存原场景，执行原始渲染器，再恢复同一原场景执行优化渲染器。六例 RGB 输出逐像素完全相同（最大通道差=0），均 GL error=0，并恢复宿主非默认 Scissor box/开关：

| 案例 | RGB 最大通道差 | 积分区域比例 |
|---|---:|---:|
| blue-front | 0 | 0.6590 |
| white-front | 0 | 0.6590 |
| blue-back | 0 | 0.7673 |
| near-plane-first-person | 0 | 1.0000 |
| low-resolution-bloom | 0 | 0.6590 |
| empty-emitter | 0 | 0.0000 |

low-resolution-bloom 仅为已有低分辨率参数兼容性检查；用户配方与性能对照仍为全分辨率。近距离例回退全屏，空发射器区域为 0。已查看蓝色前后图与 Bloom 图，没有边界裁切差异。

原始证据：evidence/game/performance/comparison.json、quality-comparison.json、quality-*-before/after.png。测量日志：evidence/performance/comparison-run.log；质量日志：quality-run-v2.log。原始内核和宿主归档在 source-before，基准资源保留为 benchmark/shaders。

## 测量方法

性能诊断需显式 -PperfQa=true；生产运行不发出计时 query。24 环形槽使用 OpenGL timestamp query，仅在结果可用时读取，没有 glFinish 或同步等候。比较运行 droppedQueryFrames=0。

方法依据：[OpenGL timestamp query](https://wikis.khronos.org/opengl/GlQueryCounter)、[Timer Query](https://wikis.khronos.org/opengl/Timer_Query)、[Scissor Test](https://wikis.khronos.org/opengl/Scissor_Test)。

可复现：Java 17，在 forge 运行 gradlew.bat runClient --offline -PperfQa=true -PperfCompare=true -PqaWidth=1280 -PqaHeight=720；同帧画面对照将 perfCompare 换成 qualityQa=true。这些开关仅使用独立开发目录的新测试世界，不操作 ASTRA 用户存档。

## 回归、打包与 ASTRA 实装

优化渲染器在独立 ENERGY_GHOST_QA_1790934642551 完成八例人物发散（含移动、gear-only、第一人称、资源重载、白蓝烟纱），累计 650 次提交，全部 GL error=0、failed=false；六例空间效果累计 427 次提交，同样无错误。网页 12 个幽气起点、v1 升级、容量与相邻整数段检查通过。生产 build/reobfJar 通过，六份内核源/Forge/JAR 哈希一致；两个 ZIP 的 CRC 完整并排除测试世界、参考素材和构建缓存。

用户正常退出后已安装 0.2.1（旧 0.2.0 移到 config/energyvfx/backups），重启 ASTRA-TEST 进入原 ASTRA_VFX_TEST。JAR SHA-256 为 3573BDDB1E1D139FD12C2EF89E0C5E4FD0C64D288C6B3D2EC98C430E2240C536。原蓝色配方前后 SHA-256 均为 8152DA049E3F35F74B2A8D6CD4417BDA08554713E2CFD5EE56F2D4C495C26956；未修改参数、相机、装备或物品栏。

Helper http://127.0.0.1:9876 的监听进程与状态 PID 均为 14036，/debug 可访问，control_mode=false；get_world_info 返回 ASTRA_VFX_TEST。本次固定文件桥 nonce 确认 runtime=energyvfx/0.2.1、active=true、draws=3661、GL error=0、failed=false、25 个包，并继续绘制。当前用户第一人称区域回退全屏，未在用户实例进行前后帧率对照；27.5% 为前述独立第三人称冻结场景的体积积分收益。用户现场截图为游戏菜单，不能作为烟纱外观证据。工作台恢复原有播放与参数，不强制重载用户页面。

原始实装证据：evidence/performance/astra-install.json、astra-live.json、astra-runtime-check.json、astra-bridge-check.log、astra-launch.log。包完整性：package-checks.json。
