# 蓝色幽气的可实现拓展

核查日期：2026-10-02。此文件是现有编辑器与 Forge 实现的技术依据，不是另一个工作台。六个蓝色起点仍通过同一套参数、三维气团密度场和光线积分渲染；用户可以继续编辑它们。

本轮新增核查了四个开放许可项目，并重新检查了一个已有的游丝参考。只借鉴可解释的算法与挂点结构；没有把 Unity 项目或来源不明的烟雾贴图打包进 Minecraft。下列源码均固定到提交，实际下载文件的 SHA-256 见 `ghost-references/<作者--仓库>/metadata.json`。

## 五个有用的参考

| 参考 | 固定提交 | 实际许可证据 | 能解决的问题 |
|---|---|---|---|
| [Smrvfx](https://github.com/keijiro/Smrvfx) | `1a6f21445599a49f717faac1e1e0ab69465834d1` | [Unlicense](https://github.com/keijiro/Smrvfx/blob/1a6f21445599a49f717faac1e1e0ab69465834d1/LICENSE) | 从动画人物表面持续发射，出生时继承表面运动 |
| [three.js-volume-renderer](https://github.com/Donitzo/three.js-volume-renderer) | `5bce88363582e5ac6f70b167f08a798d8094312e` | [MIT](https://github.com/Donitzo/three.js-volume-renderer/blob/5bce88363582e5ac6f70b167f08a798d8094312e/LICENSE) | 可编辑的三维烟雾密度、渐隐边缘、视点进入体积 |
| [SmokeGL](https://github.com/LucaAngioloni/SmokeGL) | `e6d7ed7c5dca32ff681f270afbef1313abde0818` | [MIT](https://github.com/LucaAngioloni/SmokeGL/blob/e6d7ed7c5dca32ff681f270afbef1313abde0818/LICENSE) | 冷焰与漂烟的连续出生、上升、展宽、年龄消散 |
| [Cloud-Renderer](https://github.com/jaafersheriff/Cloud-Renderer) | `00de0a33f6e6fe43c3287de5cba9228c70693110` | [MIT](https://github.com/jaafersheriff/Cloud-Renderer/blob/00de0a33f6e6fe43c3287de5cba9228c70693110/LICENSE) | 多个空间气团组成薄雾；局部球体与噪声层的结合 |
| [KvantSwarm](https://github.com/keijiro/KvantSwarm) | `932bb30f70942143c591070be0d1d1e9504125a2` | [README 内的 MIT 全文](https://github.com/keijiro/KvantSwarm/blob/932bb30f70942143c591070be0d1d1e9504125a2/README.md#license) | 流动游丝、头尾运动分离、运动历史与卷曲速度场 |

### 人物、装备表面发射：Smrvfx

已读 [Particles.vfx](https://github.com/keijiro/Smrvfx/blob/1a6f21445599a49f717faac1e1e0ab69465834d1/Assets/VFX/Particles.vfx) 和 [Lines.vfx](https://github.com/keijiro/Smrvfx/blob/1a6f21445599a49f717faac1e1e0ab69465834d1/Assets/VFX/Lines.vfx)。图中明确分组为表面采样、速度计算、首帧采用计算速度；线条图还包含湍流与低通处理。当前仓库是 Unity 自带 skinned mesh sampling 的示例，不能误称为独立可移植的骨骼采样库。

对本项目的直接启发是“出生位置随真实人物与装备动作变化，出生后的气团留在世界中继续漂散”。现有 `attachment`、`bodyWeight`、`gearWeight`、`inertia` 负责选择发射来源和运动继承。装备形状应由实际渲染变换或挂点提供；不能用人物周围固定球壳来冒充武器表面。更复杂的自定义骨骼物品仍需要实际模型适配。

### 蓝色薄雾：three.js-volume-renderer

已读 [VolumeRenderer.js](https://github.com/Donitzo/three.js-volume-renderer/blob/5bce88363582e5ac6f70b167f08a798d8094312e/VolumeRenderer.js) 的 ray-box 求交、盒内视点、步长、消光积分和前向透明合成，以及 [App.js](https://github.com/Donitzo/three.js-volume-renderer/blob/5bce88363582e5ac6f70b167f08a798d8094312e/App.js) 的 `Smoke`、`Sine Flow`、`Twister` 程序场起点。`Smoke` 是随时间变化的正弦组合，适合研究形态编辑，不是流体解算。

“玄霭”应以低发光、低细丝比例、较长寿命和较大的气团展宽形成暗蓝雾。用 `density`、`absorption`、`erosion`、`softness` 控制雾的厚薄与破碎；保持每步消光跟步长关联，避免提高采样步数后雾突然变厚。源码提供体积算法依据，不能替代 Minecraft 世界深度遮挡与渲染状态恢复的实测。

### 冷焰、魂烟：SmokeGL

已读 [index.html 内的 vertex_flame / vertex_smoke](https://github.com/LucaAngioloni/SmokeGL/blob/e6d7ed7c5dca32ff681f270afbef1313abde0818/index.html) 与 [Candela.js](https://github.com/LucaAngioloni/SmokeGL/blob/e6d7ed7c5dca32ff681f270afbef1313abde0818/js/Candela.js)。该实现以出生时间偏移和取模年龄持续生成，上升过程中改变半径与透明度；顶点属性保留独立的大小、角度与时间偏移。作者提供 [在线演示](https://lucaangioloni.github.io/SmokeGL/)，但本次核查依据是实际源码，并未将该演示帧率视为 Minecraft 性能证据。

“冥蓝”可用较强 `rise`、较高 `stretch`、较短 `puffLife` 形成上窜冷焰；“魂丝”以更强侵蚀和狭窄等值带形成断续亮丝。原项目画的是带 PNG 纹理的点精灵，本项目保留三维体积气团与密度积分，不移入那些贴图或改成朝相机的烟片。蓝色在这里是艺术色彩参数，不代表真实火焰温度。

### 离体空间气团：Cloud-Renderer

已读 [conetrace_frag.glsl](https://github.com/jaafersheriff/Cloud-Renderer/blob/00de0a33f6e6fe43c3287de5cba9228c70693110/res/conetrace_frag.glsl) 的球体求交、多层三维噪声、透明度累积与 cone tracing，及 [first_voxelize.glsl](https://github.com/jaafersheriff/Cloud-Renderer/blob/00de0a33f6e6fe43c3287de5cba9228c70693110/res/first_voxelize.glsl)、[second_voxelize.glsl](https://github.com/jaafersheriff/Cloud-Renderer/blob/00de0a33f6e6fe43c3287de5cba9228c70693110/res/second_voxelize.glsl) 的位置缓冲与体素写入。第一阶段中一段贯穿球体的写入循环已被注释，不能仅凭注释声称该代码实现完整填充。

可取的思路是让多个有大小、位置和寿命的空间气团共同组成薄雾，并用低频轮廓加高频噪声打散边缘。“霜息”把气团来源约束为装备，外溢后逐渐扩大、变淡。现有实现直接解析求值高斯包络，不需要引入全场景体素缓存。原项目使用 OpenGL 4.4 和体素光照；本轮没有移植 cone tracing，也不宣称已有雾体自阴影。

### 卷曲游丝：KvantSwarm

重新检查 [Kernel.shader](https://github.com/keijiro/KvantSwarm/blob/932bb30f70942143c591070be0d1d1e9504125a2/Assets/Kvant/Swarm/Shaders/Kernel.shader) 的 `dfnoise_field`、`frag_update_position`、`frag_update_velocity`。它用两个噪声梯度的叉积构造速度场，以浮点纹理保存头部与尾部历史，并分别处理吸引、阻尼和尾部漂流。这提供的是轨迹与运动结构，不是烟雾密度积分。

当前“幽纹”使用现有 `swirl`、`warp`、`filaments`、`sharpness` 加强卷曲和细丝；它的细丝来自三维等值带交集，尚不是带历史点的独立几何丝。若进一步制作从剑刃脱落的连续长丝，应增加实际轨迹缓冲和世界空间宽带几何，保持与体积烟层的遮挡一致。不能只提高 Bloom 后称为已实现轨迹。

## 本轮六个可编辑蓝色起点

以下数值来自本轮 `schema.js` 中的实际配方；其余字段继承“幽气 · 冰蓝发散”。颜色、亮度、寿命、展宽和发射源仍可编辑。

| 起点 | 当前实际参数重点 | 视觉用途 |
|---|---|---|
| 幽气 · 冰蓝发散 | `attachment=3`，蓝色 `#2388ff`，`density=2.5`，`filaments=0.58` | 身体与装备持续离体发散的基础状态 |
| 玄霭 · 蓝色薄雾 | `density=1`，`filaments=0.14`，`emission=0.7`，`puffLife=3.1`，`spread=0.34` | 暗蓝、轻薄、宽而慢的烟雾 |
| 冥蓝 · 冷焰 | `rise=1.35`，`stretch=2`，`emission=2.1`，`puffLife=1.8` | 较快上窜、带卷边的冷色火焰 |
| 魂丝 · 漂散 | `attachment=1`，`filaments=0.98`，`sharpness=22`，`outflow=0.5` | 从身体向外脱落的断续亮丝 |
| 霜息 · 装备溢散 | `attachment=2`，`bodyWeight=0`，`gearWeight=1.6`，`radius=0.85` | 从已穿戴、持有装备向外溢出的寒气 |
| 幽纹 · 卷曲游丝 | `filaments=0.94`，`sharpness=18`，`swirl=2.4`，`warp=0.8` | 更明显的卷曲和稀疏细丝结构 |

气团上限为 32；长寿命与生成率会共同占用这个容量。薄雾起点 `10 × 3.1 ≈ 31`，冷焰起点 `17 × 1.8 ≈ 31`，已经接近容量。提高两个参数时应检查实际活跃数量和截断行为，不能把界面可填数值等同于无限发射量。可见效果、深度遮挡、装备跟随、第一人称强度及重启后的配方加载，以本机游戏验证记录为准。

## 证据与复用范围

四个新项目的 README、完整根许可和关键源码保存在 `ghost-references` 中；目录内 `fetch.py` 是有数量与单文件大小限制的核查脚本。第五个参考沿用 `energy-vfx-research/sources/keijiro--KvantSwarm` 的固定提交快照。本轮未复制 GPL、未知许可的烟雾 shader，也未引入来源不明的素材。若后续直接复用 MIT 源码，必须随交付保留对应作者版权与许可；第三方依赖应分别核对自己的许可。
