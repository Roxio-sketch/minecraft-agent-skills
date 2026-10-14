# 星空渲染：第二阶段最小运行验证

日期：2026-09-28。承接 [第一阶段研究](STARFIELD_RENDERING_RESEARCH.md) 第 20 节。目标是把“真实天空入口、相机契约、星点、星云与质量预算”做成能够编译、运行、截图和核对的独立实验，为后续 Skill 提供实测依据。本阶段没有创建或安装 Skill。

## 1. 本次交付与证据边界

实验工程：[lab/README.md](lab/README.md)。核心实现：[LabSky.java](lab/src/main/java/dev/cosmiclab/LabSky.java)、[sky.fsh](lab/src/main/resources/assets/cosmiclab/shaders/core/sky.fsh)。验证器：[LabValidation.java](lab/src/main/java/dev/cosmiclab/LabValidation.java)。

这是自行实现的最小天空背景，没有加载 ArcaneVortex，也没有复制其着色器或贴图。因此本次结果证明的是**这条独立接入路线**，不把第一阶段参考 MOD 中未运行的分支改记为“运行通过”。

构建使用 Java 17、Minecraft 1.20.1、Forge 47.4.22、官方 mappings 1.20.1、ForgeGradle 6.0.54、Gradle 8.8。GLSL 声明版本 150。运行 GPU 为 NVIDIA GeForce RTX 4060 Ti，驱动报告 OpenGL 4.6.0 NVIDIA 617.14 / GLSL 4.60；这不等于已在所有 GLSL 150 硬件上验证。

构建与重混淆已通过，生成 [cosmic-rendering-lab-0.1.0.jar](lab/build/libs/cosmic-rendering-lab-0.1.0.jar)。实测是 Gradle 开发客户端，尚未另做启动器安装 JAR 的验收。Gradle 的 `test NO-SOURCE` 表示没有 JUnit 测试，不能替代下述实机用例。

## 2. 接入链与状态契约

```text
MOD 总线 RegisterShadersEvent
  → 普通 ShaderInstance + POSITION 顶点格式
  → 重载回调更新实例、递增 generation

RegisterDimensionSpecialEffectsEvent
  → 注册 minecraft:overworld effects
  → Forge DimensionSpecialEffects.renderSky
  → 当前投影与视图旋转上传
  → 一个全屏三角形
  → 返回 true，由此替代原版天空
  → 后续地形继续正常绘制
```

入口使用 Forge 的维度天空扩展，不依赖 Mixin，也不是放在世界中的发光方块。它替换所有引用 `minecraft:overworld` effects 的维度；生产模组应为自己的维度/配置做范围控制。`off` 返回 false，让原版继续绘制；foggy 或相机处于流体时也让回原版路径。这些回退分支本轮没有单独做视觉验收。

当前 pass 不写深度、禁用深度测试、混合和剔除。绘制后在 finally 中恢复 depth mask、depth test、blend enable、blend factors、cull 和 RenderSystem shader 引用。没有清空地形深度、申请附加 framebuffer 或使用场景深度纹理。本轮确认金墙最终覆盖天空，但未用 GPU 抓帧穷举所有驱动状态恢复。

## 3. 世界方向如何恢复

全屏三角形输出屏幕 UV，片元使用当前投影矩阵的逆矩阵重建眼空间射线：

```glsl
vec4 p = InverseProjection * vec4(screenUV * 2.0 - 1.0, -1.0, 1.0);
vec3 d = normalize(InverseViewRotation * (p.xyz / p.w));
```

`InverseViewRotation` 来自天空回调当前 PoseStack 的 3×3 部分求逆，忽略平移。FOV、宽高比使用实际渲染投影，不用固定 zoom，也不根据玩家 yaw/pitch 另造半角矩阵。方向模式输出 `d * 0.5 + 0.5`，因此相机正视 +Z 时，中心应接近 RGB `(0.5,0.5,1)`；yaw=90° 时朝 -X，中心接近 `(0,0.5,0.5)`。

独立 CPU 参考使用 Camera 的 look/up/left 三个向量，避免重用 shader 的逆矩阵算法：

```text
ndcX = 2 * (pixelX + 0.5) / framebufferWidth - 1
ndcY = 1 - 2 * (pixelY + 0.5) / framebufferHeight
ray = normalize(look - left * ndcX/P00 + up * ndcY/P11)
expectedRGB = ray * 0.5 + 0.5
```

每个方向用例检查九个分布在画面的像素，容差为每通道 3/255；另外对平移和重载做整张 960×540 图片的精确像素比较。读取的是 framebuffer 像素，非桌面截图或 GUI 尺寸。

第一轮验证器曾把 OpenGL `-Z` 射线直接乘 Camera quaternion，产生几乎 1.0 的通道误差。检查发现 Camera 零角度下朝 +Z，left 朝 +X；实际 shader 中心与 `cameraLook` 一致。改为上述基向量参考后通过。第一轮 [results.json](lab/validation-initial/results.json) 保留，此修正没有为“让测试通过”改动 shader 的输出。

![方向诊断截图](E:/着色器渲染/lab/validation/direction-base-sky.png)

## 4. 星点算法与质量档

使用三维方向域格子，`p = worldDirection * frequency`。每个片元枚举当前格及 26 个邻格，用确定性 hash 生成中心、半径、颜色与闪烁相位，以高斯核计算亮度。邻格查询避免星点跨格时被直接切断；不使用经纬度 UV，所以不引入经度展开接缝或极区 UV 拉伸。

这里的“星点”是球面方向在三维格子随机场中的采样斑点，不是物理恒星目录，也不是体积星云。半径与 `dFdx/dFdy` 推导的像素覆盖宽度取最大，并用半径平方比补偿亮度，缓解高频星点的亚像素消失。不能据此宣称所有 FOV、分辨率和运动速度下完全没有闪烁；邻域截断和采样滤波仍需更广泛验证。

| 质量 | 星点频率层 | 星点邻格求值数/片元 | 星云 warp/dust FBM octave |
|---|---|---:|---|
| 1 低 | 75 | 27 | 3 / 4 |
| 2 中 | 75、170 | 54 | 4 / 5 |
| 3 高 | 75、170、350 | 81 | 5 / 6 |

时间只改变星点亮度，不改变位置。测试把 Time 固定为 0，避免将闪烁当作坐标漂移。当前方案在低分辨率下高档星点明显更密，尚未做电影风格的密度、亮星层和色彩美术精修。

## 5. 星云层

在相同世界方向上计算三维 value noise 与 FBM：第一组 FBM 提供 domain warp，第二组生成云状密度；倾斜带状权重约束银河分布，再用紫蓝渐变着色。最终使用 `1-exp(-color*1.35)` 压缩亮度。

这是无限远的程序化天空背景，不使用 raymarch、体积光传输、深度采样或 HDR framebuffer。相机移动时没有局部体积视差属于该目标的预期行为。要做能飞入的星云，需要另外建立体积范围、积分、步长和深度契约，不能直接把本模板称为体积渲染。

![高档星点和星云，实际客户端 framebuffer](E:/着色器渲染/lab/validation/nebula-high-sky.png)

## 6. 实机验证方法

自动模式在项目 `lab/run` 创建独立超平坦世界，不打开用户既有存档。固定白天、关闭天气轮转和刷怪，创造飞行，关闭视角摇晃、FOV 动效、云和 VSync；render distance=4、simulation distance=5，帧率上限 120。大多数场景为 960×540，resize 场景为 800×600。

每次切换等待至少 50 个客户端 tick 和 90 次天空绘制。平移比较 `(0,100,0)` 与 `(512,150,512)`，相机记录包含眼睛高度；旋转比较 yaw 0/90° 和 pitch 0/-45°；FOV 比较 40/70/100；资源重载调用客户端 reloadResourcePacks 并等待完成。后续分别记录星点与星云三档，最后在玩家 y=8、面前 z=16 的金墙检查遮挡。

每个用例输出天空 pass 后和整帧结束两张 PNG，共 30 张。全量数据：[results.json](lab/validation/results.json)；环境汇总：[summary.json](lab/validation/summary.json)。最终运行日志保存在 [client.log](lab/validation/client.log)。

GPU 测量采用异步 `GL_TIME_ELAPSED`，最多挂起 8 个 query，前约 30 次天空绘制作为预热。计时范围是全屏三角形提交/绘制，不包含截图读取、CPU GL 状态查询、地形与整个帧。未锁定 GPU 频率，结果用于本机预算观察，不是严格横向基准；不同质量结果可能受到功耗/频率和系统负载影响。

## 7. 最终结果

最终世界：`COSMIC_LAB_1790601952875`。15 个用例完成，30 张截图齐全；8 个方向用例全部通过。全部用例天空绘制后的 `glGetError()` 为 0，最终客户端日志没有 ERROR 条目。

| 验证项 | 实测结果 |
|---|---|
| 方向、旋转、FOV、resize 共 8 个方向用例 | 九点采样最大通道误差不超过 0.002134（约 0.544/255），低于 3/255 容差 |
| 相机平移 | 960×540 全图不同像素数 0 |
| 资源重载 | generation 从 1 变为 2；重载后与基准全图不同像素数 0 |
| 窗口变化 | framebuffer 实际记录从 960×540 变为 800×600，方向核对通过 |
| 星点三档 | 均显示；增加质量时星点层数/密度增加，截图已检查 |
| 星云三档 | 均显示，紫蓝色带状云层与星点叠加，截图已检查 |
| 金墙遮挡 | 整帧截图可见完整金墙覆盖背景；中心像素从天空值 4278912005 变为 4280129719（无符号 ABGR） |

单次运行的天空 pass GPU 计时如下，单位 ms。不得据此推算整帧 FPS：

| 模式 | 质量 | 样本数 | 中位数 | P95 |
|---|---|---:|---:|---:|
| 星点 | 低 | 259 | 0.581 | 0.598 |
| 星点 | 中 | 262 | 0.928 | 0.969 |
| 星点 | 高 | 250 | 1.174 | 1.242 |
| 星点+星云 | 低 | 246 | 0.621 | 0.740 |
| 星点+星云 | 中 | 252 | 1.110 | 1.176 |
| 星点+星云 | 高 | 247 | 1.339 | 1.384 |

方向诊断模式中位数约 0.028–0.030 ms。当前低到高档的天空成本增加符合预算变化，但这是 RTX 4060 Ti、960×540 下的单次记录；不外推到 4K 或集显，也不归因所有差异于某一个算法操作。

![最终帧：金墙正确遮挡星云](E:/着色器渲染/lab/validation/terrain-occlusion-world.png)

## 8. 环境问题及可复现处理

- Java 17 启动 Gradle 时出现 `Unable to establish loopback connection`，堆栈落在临时 Unix-domain socket；IPv4 preference 无效。使用当前进程的 `jdk.net.unixdomain.tmpdir` 指向短路径后可以构建。报告只记录有效规避，不把它推断成系统网络故障。
- 共享 ForgeGradle `net.minecraft:client:1.20.1` 缓存 POM 包含 `natives-linux`。Windows 启动缺少 `lwjgl.dll`。项目脚本从 Mojang 元数据取得 Windows x64 3.3.1 原生库，校验 SHA-1，再通过可选 `org.lwjgl.librarypath` 使用本地目录；不修改共享缓存。
- 测试器原设 simulation distance=4，客户端报告低于允许范围；已改为 5 并用最终脚本完整复跑。该错误和 CPU 参考坐标错误分别属于测试配置/验证器，不是星点算法编译失败。
- 当前编译仍有两个 Forge 对旧 ResourceLocation 构造函数的弃用警告；不影响此锁定版本构建与运行，迁移版本时应重新核对 API。

启动脚本已走过 BuildOnly 与 AutoValidate 路线；说明与完整命令见 [lab/README.md](lab/README.md)。

## 9. 能进入后续 Skill 的结论

1. **先证明入口**：注册资源、创建实例、调用实际天空 hook、提交 draw 四项分别有证据；有 shader 文件不能证明天空已接入。
2. **先验证方向**：实际投影和 Camera 基向量形成相互独立的核对，平移、旋转、FOV、窗口尺寸与重载是低成本且有区分力的用例。
3. **写清坐标约定**：Camera 的 forward/left 与 OpenGL eye-space 不能凭直觉混用；验证器本身也可能出错。
4. **分开无限远与有限体积**：方向域星云不需要相机位置或场景深度；体积 raymarch 需要另一套契约。
5. **预算要具体**：质量档应改变实际采样次数，并用 GPU timer 说明测量边界；不把单 pass 毫秒值换算成整机 FPS 保证。
6. **重载要实测**：确认新实例 generation 变化并比较固定时间画面，不能仅凭注册回调存在宣称支持重载。
7. **证据分层**：编译成功、GPU 运行、像素核对、视觉检查与性能测量各自说明覆盖范围；保留失败记录，避免将修复过程抹成从未出错。

尚未覆盖：Oculus/光影包、多模组同时替换天空、第三人称/相机模组、视角摇晃和受伤/传送门效果、流体和天气、透明物体、完整全天球接缝巡检、长时间动画稳定性、4K/集显/其他驱动与生产性能优化。

下一阶段可以据此编写 Skill，把已实测的 Forge 1.20.1 接入与诊断流程放入对应 reference，把本项目作为可选实验模板；参考 MOD 的有限体积与兼容分支继续标记为未验证。Skill 不应宣称已经解决上面这些未覆盖事项。

后续进展（2026-09-28）：正式技能 `minecraft-cosmic-rendering` 已创建并安装。见 [第三阶段技能交付](SKILL_DELIVERY.md)。本报告保留第二阶段记录；技能包含经核验的实验模板、按需参考与结果检查器。
