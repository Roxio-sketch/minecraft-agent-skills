# 内部翻卷与 FantasyBlock

当用户指向 ArcaneVortex 的幻想方块时，目标是材质内部具有可持续追踪的云壁、褶皱、洞隙和纵深流动。先对齐这种空间结构。液态金属反射、瓷缝亮流和织物起伏各自成立，但不等价于内部空间。

## 参考的实际机制

`FantasyBlockEntityRender → getChaosShader → useType=1 → cloudRaymarch`。原始 GLSL 保留在用户提供 JAR 的本机研究目录，不随 skill 或原创 JAR 打包。`TimeSystem` 返回 seconds/20，renderer 再乘100，所以 uniform 实际为5×秒（默认 timeScale=1）。本地参考动画只改 GLSL 版本/精度和全屏宿主，不代表原 mod 方块运行；合成 yaw/pitch 也应公开。

参考的重点是共享观察点、发散射线、三维多尺度扰动、薄层附近的精细累计、随时间展开的空间结构。不要仅凭 shader 文件名或静帧推断绑定和动态。

## 原创幻流云廊

配方 `fantasy-flow`，mode 6，在制作台默认选中。源码在 `assets/fantasy-lab/material.glsl` 的 `fmFlowDomain`、`fmFantasyFlow`。

- 双侧云壁限定内部走廊，六个尺度的耦合正弦剪切持续改变三维场。大结构和微褶皱使用不同相位速度。
- 所有像素共享虚拟观察点；射线发散后穿过不同深度。观察点随时间沿 z 前进，云壁同时形变，前后轮廓持续改变。
- 最多100步自适应采样；近层发光随接近程度增强，深度吸收压低远层。接近辉光是美术模型，不是能量守恒的物理体积散射或真实流体模拟。
- 带宽包含像素足迹；尾段裁切按覆盖比例累计，不能用被截短的极小步长作发光分母，否则低 depth 可能出现白斑。
- 配方颜色控制主体暗部、云壁冷色、暖亮部。`detail` 控制光脉聚集，`warp` 控制翻卷，`depth` 是行进上限且受步数预算约束，`scale` 在此模式控制视角张角。

默认 speed=0.5 对应内核 t=秒。时间分别进入域形变、沿 z 输运、光脉和色场，不能把它们全部理解为 UV 速度。需要“云壁慢卷、内部光流很快”时拆出独立时间倍率和配方字段；当前全局 speed 不具备独立控制能力。

## 坐标契约

积分内核是 `fmFantasyFlowRay(origin, direction, footprint, time)`。同一幅图中的像素共享 origin，只有 direction 随像素变化。不要在每个面上重新构造 origin 或开口 UV。

浏览器制作台仍用 `fantasyMaterial(entry, ray, footprint)`：mode 6 从 `ray.xy/ray.z - entry.xy/3` 提取虚拟观察偏移。因此默认观察下宿主提供 `ray=normalize(vec3(entry.xy/3,1))`。这是二维制作开口的坐标契约。

Forge mode 6 使用 `fmFantasyWindow(screenUv, cameraAngles, footprint)`：`screenUv=(gl_FragCoord.xy/screenSize-.5)*2`，x 乘宽高比。GUI 相机角为0，世界上下文用相机 yaw/pitch，观察响应乘 parallax。所有面采样同一个场，atlas UV 只参与 alpha 与可选覆盖遮罩。分辨率绑定必须使用 `Uniform.set(float,float)`，Java 的整数重载会访问错误的 uniform 缓冲。

这是参考 FantasyBlock 的共享视线窗口，方块几何只裁剪可见区域；固定时间、固定相机时旋转方块，场保持连续，轮廓改变。它不是绑定到方块局部坐标、随方块一起旋转的实体体积。张角由 scale 决定，未按 Minecraft 动态 FOV 重建真实投影；没有第二个 Minecraft 世界。mode 0–5 仍使用 primary-sprite 开口，不能继承 mode 6 的跨面保证。多 sprite 遮罩、BEWLR、穿戴护甲与骨骼模型需另做适配。

## 从描述到设计

先确定云壁的空间拓扑，再配色。例如“裂缝里面是两片慢慢卷开的寒焰，近处暗，里面有金色光脉”，保留共享视点与两侧云壁，减小整体速度和暖色占比，让发光沿内部褶皱分布。若描述变成“环形旋涡”则重写云壁场为环形/管状距离场，并重新检查视点和行进路径；只旋转整幅画面不能算新空间。

检查同一块轮廓在连续1–3秒内如何移动、变形、露出后层。若只有亮暗闪动，补空间形变；若整幅图平移，检查是否只改最终 UV；若远近没有差别，检查每像素是否各自充当了观察原点。浓雾遮住全部结构时先减发光和前层吸收，不直接加速。

## 可复跑验证

从 skill 根目录执行，Python + Node + Playwright + Chromium 依赖沿用制作台说明：

```powershell
python scripts/serve_authoring.py --lab fantasy
node scripts/validate_fantasy.cjs --python python --out ./flow-validation
node scripts/record_fantasy_motion.cjs --python python --preset fantasy-flow --out ./flow-motion
python scripts/make_motion_sheet.py ./flow-motion
```

服务独占动态端口，只使用启动时返回的 URL。现有页面不会热更新；保留用户未导出的页面，另开更新页或先导出再刷新，核实默认选中“幻流云廊”和十二个奇幻预设。录制是每秒20次的固定时间采样，不是实时性能测试，片段首尾也不保证无缝。

自动检查编译、可见像素、动画/种子/观察响应、16/32px、配方往返和180次零容差冻结比较；它不判定美术质量。原 Cosmic 的偶发1LSB差异仍是单独未闭合问题。

Forge 运行使用新生产 JAR，NBT 为 `CosmicMaterial:{Material:"fantasy-flow",Mask:"blade"}`（blade 遮罩仅适用于当前钻石剑）。测试接口 `{"action":"flow-run"}` 写入游戏目录 `cosmic-material-lab/request.json`，限定 ASTRA_VFX_TEST，记录10种状态和恢复结果。`flow-showcase` 展示新材质样品。工程与运行边界见 [fantasy-forge.md](fantasy-forge.md)。

跨面验收使用 `{"action":"flow-continuity"}`，不改玩家背包或世界；在两个冻结时刻各渲染16个原生 baked cube 角度，并渲染同一生产 shader 的单面完整窗口作为参照。`flow-continuity-hd` 请求2560×1440窗口并在结束后恢复尺寸；窗口管理器可能限制高度，以results.json的实际像素尺寸为准。使用随包脚本：

```powershell
python scripts/validate_flow_continuity.py RUN --jar BUILT_JAR --installed INSTALLED_JAR
```

依赖 Pillow 与 numpy。比较覆盖内部的所有像素，包括各面的公共边；只剔除最外轮廓2像素，不放宽色差容限。脚本生成 validation.json、continuity.webp 和接触图。这一验证检查实际 Forge 图元渲染，不能用纯二维预览代替；也不证明相机/FOV的物理正确性。

跨面测试的两个时间点都是冻结时间，它不是连续动态验收。用户关心高分辨率流动时，另用 `{"action":"flow-motion-hd"}`：固定原生方块和相机，冻结2秒、以真实游戏tick+partialTick播放6秒、再冻结2秒。记录实际framebuffer尺寸、每帧 `time`/`fmTime` uniform、墙钟时间和截图耗时；结束后恢复窗口及时间控制。随包脚本为：

```powershell
python scripts/validate_flow_motion.py RUN --jar BUILT_JAR --installed INSTALLED_JAR
```

该脚本检查前后冻结像素零差异、live uniform递增与真实时间跨度、连续内部像素变化，按实际采样间隔生成 `live-motion.webp`。时间单位核对按Java实际的float32除法执行，不把大游戏时间下正常的float表示精度误判为时钟错误；冻结像素仍要求零容差。PNG编码由两个后台线程处理，原生图像队列最多4张。动画是原始高分辨率PNG的缩小展示；GPU读回与后台编码仍可能影响帧间隔，录制采样率不能当作游戏FPS。不得用两个时间点有变化来代替此连续片段。

2026-09-30 本机854×480初测32个旋转案例、1850624个内部像素零差异。高分辨率曾出现每案例1–4个像素的1LSB偏差；完整替换不再执行 `mix(base,mapped,1)`，无影响的overlay也跳过混合。修正后的2560×1421实测32个案例、16682478个内部像素零差异，证据 `continuity-runs/20260930-030546`。这不能解释原Cosmic P3历史根因。

普通回归10场景/21项通过，采样GL error=0，重载六处裁切零差异。最新JAR和运行证据见工程交付文档。护甲图标不构成穿戴效果认证，用户尚未确认美术效果达到参考。

连续动态另有 `motion-runs/20260930-032132`：2560×1440，真实游戏时钟的6秒阶段取得49帧，采样跨度5.81355秒，对应shader时间跨度5.814秒。前后冻结像素零差异，13项通过。录制中位间隔0.11745秒，不能写成60FPS或20FPS实录；动画按实际间隔播放。
