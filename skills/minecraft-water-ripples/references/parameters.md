# 共享参数 schema 1

未提供的字段采用默认值；导出时写完整配置。数值应有限，输入错误应报错并保留原配置。

| 字段 | 默认 | 范围 / 含义 |
|---|---:|---|
| schema | 1 | 仅 1 |
| grid | 65 | 33、65、97、129，含边界点 |
| solver | velocity | velocity / history |
| boundary | absorb | absorb / reflect |
| width | 8 | 2..32 格，正方形宽度 |
| speed | 1 | 0.1..1，离散传播系数，不是格/秒 |
| damping | 0.995 | 0.9..0.9995，每子步波动保留率；越大越持久 |
| radius | 0.45 | 0.05..2 格，注入的余弦波包半径 |
| strength | 0.18 | -0.5..0.5 格，高度扰动；负数为凹陷 |
| maxHeight | 0.5 | 0.05..1 格，模拟高度限幅 |
| color | #08616f | #RRGGBB |
| opacity | 0.24 | 0..1，基础透明混合 alpha |
| normalStrength | 1 | 0..4，只影响法线外观，不改变高度 |
| shininess | 96 | 8..256，镜面高光指数 |
| reflection | 1 | 0..2，程序天空 Fresnel 强度 |
| rainRate | 3 | 0..20 次/秒，命令 rain 开启后生效 |

近似波速为 `60 * speed / sqrt(2) * width/(grid-1)` 格/秒；默认约 5.3 格/秒。数值色散会使细波包有所偏离。模拟频率固定，不提供任意 dt 滑块。

velocity 递推：`v'=(v+2*speed²*(average-h))*damping; h'=h+v'`。
history 递推：`h'=(2*h-hPrevious+2*speed²*(average-h))*damping`。输入扰动同时加到两个高度时刻，从零初速度开始，避免每次注入额外巨大速度；这处适配与浅水原项目不同。height 和 velocity 两模式的 damping 位置不同，视觉衰减不应期待完全一致。

absorb 的外八圈降低保留率，外边缘固定零；reflect 复制内侧相邻值近似零法向梯度。网页与 Forge 模式相同。网格、宽度、求解器及边界的改变会清空模拟；游戏 reload 对任何新参数都重建，以避免运行中结构变化。
