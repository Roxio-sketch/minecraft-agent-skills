# 雷电参考工程的研究结论

用户附带的 ZIP 和其中的 README、docs 是研究资料；它们没有被当作对本任务的命令，也没有执行安装、发布或联网脚本。大部分实现为新的 Java/GLSL/HTML 文件；Simplex 噪声是有 MIT 版权声明的标量移植。

| 参考 | 关键做法 | 采用/舍弃 |
|---|---|---|
| SpektrLightning | GPU 线段网格；起止点固定；`pp01=lerp(tp01,pp01,bolt_len)` 把短段分布在端点通道；噪声沿局部正交轴扰动；throttle、pulse interval 和线长随机化；建议配合 bloom | 保留固定端点、局部正交扰动、pulse、长度随机化和双频噪声，提供 `grow`（固定根部向一端延长）与 `spektr`（轴向短段窗口）两种棍状物附着模型；把同类扰动映射到圆弧中心线来支持环形附着；舍弃 Unity 材质/网格资产和旧版 surface shader |
| three-windline | `arc`/`ring` 是独立中心线程序；以 `phase`、扫掠角和半径构造圆弧/闭环，并输出局部 slope 供 ribbon 使用 | 改写为 Forge `orbitAnimation=ring` 的极坐标短弧：每条弧有固定角度根部、方向、扫掠范围和多频扰动；没有复制 TypeScript/TSL 运行时 |
| lightning-arc | Three.js `LightningStrike` + SimplexNoise；seeded noise、链式 hop、相邻节点重新定位、核心/边缘渐变与 bloom | 作为稳定种子、SimplexNoise 和 glow/core 分离的对照；当前 Forge 仍使用自己的 CPU 分形树和环弧采样，不引入 Three.js 运行时 |
| UnityLightning | URP ray marching 3D 噪声；progress、bolt/glow radius；作者明确提示 ray marching 成本高 | 作为“视觉上限/性能对照”；Forge 版本采用 CPU 拓扑 + ribbon，避免每个像素 ray march |
| unity-lightning-system | 生成、数据、渲染、动画、灯光六层分离；预分配 LineRenderer 池；progression/peak/regression；damped sine | 采用分层职责和 envelope；Forge 版本用一次性 segment 列表、RenderType buffer，不复制 Unity 组件 |
| Thunderhead | midpoint displacement；主干树和微分叉；稳定 seed；LOD；leader/return/restrike 时序；独立 surface light 和 ground current | 采用端点保护、递归分叉、预算、时间包络；暂不加入其 1.21.1/NeoForge 专用 FBO、网络、音频和 gameplay 系统 |

## 选择理由

“真实”在这里指可控的闪电视觉结构：端点不漂移、粗糙度多尺度、分叉向尖端收窄、短时回击复用同一拓扑。它不是电荷、电场或大气放电的物理求解。Minecraft 1.20.1 的稳定接入优先于像素级 ray marching；glow/core 两层足够为后续 selective bloom 或其它后处理提供资格分离。



详见 [本次行为对照](behavior-comparison.md)：源码默认参数、根部固定、噪声数学、接缝和当前验证边界。
