# schema 1：演示光束路径

`path.json` 是独立于 `recipe.json` 的运动路径。Java 的 `Paths.parse` 严格解析它，未知字段或超预算值会被拒绝。它供画廊第三槽和 `/trailbeam world` 的示例束使用；正式武器既可以使用 `Paths.generate`，也可以向 `Effects.beam` 传任何世界坐标列表。

| 字段 | 范围与作用 |
|---|---|
| schema | 固定 1 |
| kind | `straight`、`arc`、`sine`、`helix`、`zigzag`、`lightning` |
| length | 0.1..32 方块 |
| amplitude | 0..8 方块，横向偏移上限尺度 |
| cycles | 0..32，沿束空间周期 |
| speed | -20..20，路径动画速度；不等于材质 scroll |
| seed | -1000000000..1000000000，确定性折线与分叉方向 |
| samples | 8..256，主束细分数；最终点数为 samples+1 |
| branches | 0..4，附加分叉数量 |
| branchLength | 0.05..1，相对主束长度的分叉延伸 |

```json
{"schema":1,"kind":"lightning","length":3.2,"amplitude":0.35,"cycles":2.7,"speed":0.8,"seed":1337,"samples":100,"branches":3,"branchLength":0.55}
```

`Paths.generate(spec, origin, forward, side, up, seconds)` 返回主束与分叉的世界空间 `Strand` 列表。输入基向量须非退化；代码将 forward 归一化、正交化 side，再用叉乘构造 up，以免宽度被缩放。更新同一个 emitter ID 可做动态束；每个分叉应使用独立 ID，并有自身 widthScale。闪电的随机切片按模拟时间量化，不在每次 render pass 重抽。Java 与 HTML 的同名模式应按相同字段解释。

这是可改的路径起点，不负责碰撞寻敌、服务器伤害、关节绑定或真实动画重采样。弯束需要更多采样时同时检查预算与屏幕成本；强行增加四边形数不能代替稳定时间与端点策略。
