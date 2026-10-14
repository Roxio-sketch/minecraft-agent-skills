# 液体求解、连续表面与飞溅源码整理

整理日期：2026-10-11。当前只做源码研究；没有执行、编译或安装这些上游工程，也没有改动水龙卷工作台。共获取 3 个固定提交的 40 个源码、README 与 LICENSE 文件，363,739 字节。每个下载文件的 URL、提交、字节数和 SHA256 在 [manifest.json](manifest.json)。这些是精选阅读副本，依赖和入口文件不齐，不能视为可运行工程。

针对“缺乏螺旋感、间隙太多”，需要分别处理**运动轨迹**与**连续水体占据范围**。这 3 个工程提供液体运动、粒子表面和三角网格方法；它们没有现成的水龙卷预设。下面的改进方向是根据源码作出的推导，尚未在当前工作台实现。

| 项目 | 实际方法 | 可学习内容 | 对当前水龙卷的用途与限制 |
| --- | --- | --- | --- |
| [dli/fluid](https://github.com/dli/fluid/tree/ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab) | WebGL 粒子/网格 PIC-FLIP，网格压力投影，RK2 粒子推进 | 连续速度场如何驱动持久粒子，外力注入与边界 | 适合研究旋转上升的体积运动；现有渲染是粒子球面与 AO，不是连续折射水片 |
| [Particles4All](https://github.com/matsuoka-601/Particles4All/tree/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0) | WebGPU PBF，邻域约束，表面张力，各向异性粒子核与屏幕空间水面 | 聚合水体、薄片拉伸、表面平滑、深度/厚度分离 | 对连续水体与水花最直接；需 WebGPU compute/storage buffers，多层透明表面不能只依靠单层屏幕深度 |
| [splashsurf](https://github.com/InteractiveComputerGraphics/splashsurf/tree/56d3e6ac444cf8540c238a4391483955a9484210) | Rust CPU 粒子密度重建、Marching Cubes、加权网格平滑 | 将相邻水粒子重建为有厚度的连续三角表面 | 适合离线生成水片网格或研究 GPU 移植；它是重建工具，不是流体求解器，也没有提供实时性能保证 |

## 1. dli/fluid：先形成速度，再推动水体

固定提交 `ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab`，提交日期 2016-06-29。MIT，版权 David Li；保留 [LICENSE](dli-fluid/LICENSE)。

实际顺序在 [`Simulator.prototype.simulate` 第 274–587 行](https://github.com/dli/fluid/blob/ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab/simulator.js#L274-L587)：粒子速度转网格、保存旧网格、添加外力、边界处理、散度/压力投影、网格速度回传粒子、推进位置。压力做 50 次 Jacobi 迭代，随后减去压力梯度；这是影响整个体积的运动过程，不能用移动高光替代。

[`transfertoparticles.frag` 第 35–49 行](https://github.com/dli/fluid/blob/ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab/shaders/transfertoparticles.frag#L35-L49)把“直接取新网格速度”的 PIC 与“旧粒子速度加网格增量”的 FLIP 混合。默认 `flipness=0.99` 在 `simulator.js` 第 62 行。较多 FLIP 保留运动细节，但这一默认值不能直接当作我们水龙卷的稳定参数。

[`advect.frag` 第 39–58 行](https://github.com/dli/fluid/blob/ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab/shaders/advect.frag#L39-L58)先估算半步位置，再采样半步速度；此外还有随速度变化的随机位置扰动。源码的 CFL 截断只是注释，不能称为已实施的 CFL 自适应求解。粒子最后被夹在箱形边界内。

**与螺旋感的对应：**可在 [`addforce.frag` 第 26–41 行](https://github.com/dli/fluid/blob/ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab/shaders/addforce.frag#L26-L41)的网格外力步骤研究加入切向、向心和上升驱动，使位置真实绕轴推进。上游此处只有重力与鼠标推力，水龙卷速度场须自行设计。螺旋间距取决于一周旋转时间与同期上升距离；只增加整体旋转时间项，可能仍看不到明确的斜向水纹或形体层次。

**与孔洞的对应：**该渲染逐个绘制球面实例，源码没有将球体统一为液体网格；[`renderer.js` 第 274–317 行](https://github.com/dli/fluid/blob/ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab/renderer.js#L274-L317)与 [`composite.frag` 第 27–70 行](https://github.com/dli/fluid/blob/ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab/shaders/composite.frag#L27-L70)是球体法线、速度着色、AO/阴影合成。因此直接移植这个 renderer 会保留颗粒感，不能作为修复连续水片间隙的充分方案。

能力要求从代码可见：半浮点纹理与线性采样、浮点粒子纹理、多次 render-to-texture、粒子往网格的加法混合。它是较老的 WebGL 管线，现代 WebGL2 仍需逐项验证格式/混合/扩展支持。

## 2. Particles4All：运动求解与表面重建分开

固定提交 `58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0`，提交日期 2026-08-24。MIT，版权 matsuoka-601；保留 [LICENSE](particles4all/LICENSE)。README 引用的全景属于另一资产；本次未下载 HDR、视频或图像。

[`sim.js` 第 473–595 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/sim.js#L473-L595)先预测位置，整理邻域网格，多轮 `lambda`/`delta` 约束迭代，随后由修正后的位置反推速度，再处理 XSPH 和可选表面张力。可研究这个架构，让旋涡驱动作用在预测速度上，同时由邻域约束保持液体团块。

**约束不能凭空补洞。**[`lambdaWGSL` 第 477–542 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/wgsl.js#L477-L542)按邻域核计算密度，且仅在压缩约束 `C > 0` 时产生非零 lambda。稀疏处不会因此自动获得水量。还要由发射分布、粒子间距、支持半径和持续补水控制占据范围。表面张力的凝聚/曲率力在 [`tensionWGSL` 第 290–334 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/wgsl.js#L290-L334)，不能把它理解成任意距离的水片焊接。

lambda 为 0 不等于完全没有位移：后续 delta 中的 `sCorr` 等项仍可移动粒子。这里的结论是不会凭空添加水量，并非所有稀疏粒子静止。

**连续水片最值得读：**[`anisoWGSL` 第 66–157 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/aniso_wgsl.js#L66-L157)。它从邻域加权均值和协方差求核方向，用特征值限制比例、最大拉伸与最小尺度，再按邻居数量在各向异性/各向同性之间混合。比统一画等半径球更适合紧密的薄层和拉伸团块，也避免孤立水滴被无限拉成长针。但这仍是基于局部粒子分布的渲染核，不能弥补不存在的粒子。

**屏幕滤波不会填补轮廓外的真空。**[`ssfr_wgsl.js` 第 242–246 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/ssfr_wgsl.js#L242-L246)遇到空深度直接返回；`narrowRange` 第 219–233 行控制过滤时跨深度层的权重。该滤波使已有水面更平滑，不能把一大块空像素自动接成连续水柱。现有间隙问题需要先修复占据分布，再调滤波，而不能全靠后处理模糊。

粒子/椭球自身的覆盖半径仍会改变轮廓；“空像素不填补”专指上述过滤步骤。

[`FluidSSFR` 第 58–78 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/ssfr.js#L58-L78)将前表面深度、加法累计厚度、平滑与合成拆成独立通道。椭球深度交点在 [`hitEllipsoid` 第 92–112 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/ssfr_wgsl.js#L92-L112)；厚度 pass 第 150–160 行实际采用独立球形弦长。不要误称该厚度是完整多层各向异性水体的精确透射路径。

合成 [`ssfr_composite_wgsl.js` 第 281–330 行](https://github.com/matsuoka-601/Particles4All/blob/58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0/src/ssfr_composite_wgsl.js#L281-L330)从平滑深度重建位置/法线，结合 Fresnel、折射、厚度吸收与 GGX。只存最近水面深度，因此多层互相包裹的水片仍有表达限制。

**飞溅：**稀疏粒子可保持为独立滴，密集部分用各向异性表面。该选集没有专门的白水发射器或“水片断裂 → 雾沫”分类系统；不能把它称为完整泡沫/喷雾资产。需另行规定脱离主体的阈值、生命周期、速度继承与容量。

能力要求：WGSL compute、storage buffers、邻域排序与 prefix scan、多次离屏水面 passes。当前 WebGL2 工作台不能直接运行这些 WGSL；Minecraft Forge 也不能直接把 WebGPU 工程接入原渲染器。

## 3. splashsurf：从占据场提取有连接关系的表面

固定提交 `56d3e6ac444cf8540c238a4391483955a9484210`，提交日期 2026-06-02。MIT，版权 Fabian Löschner；保留 [LICENSE](splashsurf/LICENSE)。它读取已有粒子数据，不负责求解粒子的运动。

[`reconstruct_single_surface_append` 第 149–191 行](https://github.com/InteractiveComputerGraphics/splashsurf/blob/56d3e6ac444cf8540c238a4391483955a9484210/splashsurf_lib/src/reconstruction.rs#L149-L191)的顺序是邻域/粒子密度、稀疏标量场、等值面三角化。关键在 [`particle_support_loop` 第 677–728 行](https://github.com/InteractiveComputerGraphics/splashsurf/blob/56d3e6ac444cf8540c238a4391483955a9484210/splashsurf_lib/src/density_map.rs#L677-L728)：每粒子的贡献按 `粒子质量 / 粒子密度` 得到体积，再把核函数加到网格点。相邻粒子贡献累加后提取共同表面，比独立可见水条更适合连续水量。

[`triangulate_density_map_append` 第 113–125 行](https://github.com/InteractiveComputerGraphics/splashsurf/blob/56d3e6ac444cf8540c238a4391483955a9484210/splashsurf_lib/src/marching_cubes.rs#L113-L125)提取指定阈值的等值面。支持半径、网格分辨率、阈值必须一起考虑：核互不覆盖或薄片低于网格尺度时，三角化不能保住连续薄片。这个选集没有给我们当前尺度验证过的参数。

源码还直接处理边界孔洞风险：[`SparseDensityMapGenerator::try_new` 第 600–609 行](https://github.com/InteractiveComputerGraphics/splashsurf/blob/56d3e6ac444cf8540c238a4391483955a9484210/splashsurf_lib/src/density_map.rs#L600-L609)将允许粒子存在的域向内缩一个核支持边距，并忽略落入边距的粒子，以避免支持被边界截断形成开口。域过小会丢失这些粒子的水量；实现并非保留靠边粒子后截断其核。未来生成水龙卷离线网格时，重建域必须包住最外层飞溅与核半径，不能刚好裁到水体边缘。

[`par_laplacian_smoothing_inplace` 第 17–52 行](https://github.com/InteractiveComputerGraphics/splashsurf/blob/56d3e6ac444cf8540c238a4391483955a9484210/splashsurf_lib/src/postprocessing.rs#L17-L52)按每顶点权重控制向邻域平均值移动的比例。可学习保护薄片边缘/细部的平滑强度；单独使用该函数并不保证体积守恒，也不能宣称会修复任意断面。其 README 还说明多线程/哈希输出不确定，离线结果需按网格质量验证，不能预设字节级重放一致。

这是 Rust CPU、多线程重建路径。适合对比离线生成水片的网格轮廓，或把算法拆出研究 GPU 方案；直接逐帧在游戏中调用的耗时未测。

## 建议后续迭代顺序（本次只整理）

1. 用一个有体积的连续主体承载水量，检查纵向每一层的覆盖；多层不规则水片与飞溅作为边缘细节，避免宽孔洞贯穿主体。选择隐式共同场或粒子共同表面，不要把局部水片的随机遮罩同时用作主体删除遮罩。
2. 将螺旋运动落实到世界位置/形变或持久粒子轨迹；控制角速度、上升速度和随高度的相位，使一周内的上升距离形成可读的螺旋。再让细水纹、泡沫和碎滴继承相同速度方向。
3. 近景先验证密集水体的连续轮廓，再添加离体滴；独立滴不能承担“填满主体”的职责。对应的源码路线优先读 Particles4All 的 `anisoWGSL` 和 splashsurf 的共同密度场。
4. 按原平台检查资源预算。要做求解器时先做小范围独立试验；较快的艺术化路径可以只学习连续场/各向异性表面原则，不必直接移植整个粒子求解器。

以上属于应用推导，不是上游提供的成品龙吸水素材。3 份 MIT LICENSE 覆盖本次选取的对应上游代码；依赖、论文、外链 HDR/视频和未下载资产须分别核对许可。精选文件保持上游原字节，未去掉版权声明。
