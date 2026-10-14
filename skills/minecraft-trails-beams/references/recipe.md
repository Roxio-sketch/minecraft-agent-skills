# schema 1：真实解析的字段

完整示例在 assets/presets。Python 与 Java 均拒绝未知字段、无序节点和超预算值，无任意表达式执行。

| 字段 | 含义 |
|---|---|
| schema/name | 版本 1；设计名称 |
| section | ribbon 相机光带、cross 双片、tube 开口管、blade 双挂点历史 |
| sides | 3..64，tube 环周面数，其他截面不使用 |
| sampling.lifetime | 0.05..30 秒轨迹寿命；beam 由 expiresAt 控制 |
| sampling.minDistance | 0..5 方块；任一端位移达到阈值即可采样 |
| sampling.maxStep | 0.001..10 方块线性细分步长，必须 > minDistance |
| sampling.breakDistance | ≥maxStep，超出断带，不跨越插值 |
| sampling.maxPoints | 2..4096 硬上限，淘汰旧点 |
| layers | 1..16 个有序层，通常外壳在前、核心在后 |
| width | 全宽/直径（方块）；blade 中是双挂点宽度倍率 |
| blend | alpha/additive，均测深度、只写颜色 |
| profile | soft 柔边、solid 全覆盖、double 双边、dash 沿 U 分节、pulse 沿程脉冲、plasma 扰动、rune 符文栅格 |
| texture | 完整资源 ID，如 trailbeam:textures/weave.png；自制纹理放到生成工程 assets 下 |
| repeat | 每方块周期，0 固定沿程采样位置 |
| scroll | 每秒循环数，可正可负；U=弧长×repeat−time×scroll |
| gain | 0..16，RGB 倍率；不保证主 framebuffer 保留 HDR，不自动 bloom |
| fadePower | 0.01..16，轨迹 alpha×(1−age)^fadePower；beam age=0 |
| taper | 2..32 个 {at,value}，at 严格从 0 到 1；value 0..8，分段线性倍率 |
| colors | 2..32 个 {at,color,alpha}，位置 0..1、#RRGGBB、alpha 0..1 |

沿程 0→1 是轨迹存活最旧点→最新点或 beam 首端→末端。RGB 数值直接插值，没有额外线性化；需要颜色管理时统一主管线。年龄 fade 独立于沿程节点，存活区间变化会重新分布色带。

累计 UV 保留被删尾部长度。beam 本身长度变化时应另定义材质点锁定策略，当前按距离采样。预算是防意外高开销的起点，需要更多点/层时修改实现并验证，不静默截断。

```powershell
python scripts/author_effect.py --recipe assets/presets/helix.json --path assets/forge-1.20.1/src/main/resources/assets/trailbeam/paths/custom.json --out D:/Projects/my-energy-effect
python scripts/author_effect.py --recipe my-design.json --validate-only
```

输出目录必须不存在。生成配方与路径写入第三展示槽，并保留 `design-recipe.json`、`design-path.json`；固定刀光和双层束保留作对照。未提供 `--path` 时用模板内的默认路径。路径字段详见 [path.md](path.md)。

游戏读取 `<gameDir>/config/trailbeam/recipe.json` 和 `path.json` 为第三槽配置，`/trailbeam reload` 重新加载；错误不覆盖原有效设计。GLSL/纹理改动用 F3+T；Java 改动需生产 build+reobf 后重启。
