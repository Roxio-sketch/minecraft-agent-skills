# 作品与配方

资产根为 `assets/world-volume-lab`。`scripts/author.py` 复制到新目录，并把完整配方写入 `recipe.json`、工作台 `initial-recipe.json` 和 Forge 开发实例配置。没有覆盖非空目录的模式。用项目可用的 Python 运行；运行时只需标准库。WebGL2 工作台走 HTTP，不能直接以 file URL 打开。

## 从文字确定参数

| 诉求 | 起点 | 优先控制 |
|---|---|---|
| 大片日照云海 | cloud-sea | bottom / height / radius / coverage，云顶可见构图 |
| 贴地冷雾 | ground-mist | bottom / height / density / extinction，近物可见程度 |
| 空洞外旋转风暴 | alien-storm | shape=2 / innerRadius / curl / speed / flashPeriod |
| 发光雾团 | luminous-pocket | shape=1 / centerY / radius / emission，闪光默认关闭 |
| 蓝白云内闪动并照亮地形 | cloud-flash | flashStrength / flashRadius / mediumGain / surfaceGain |
| 低角度太阳穿云雾 | sun-shafts | sunXYZ / anisotropy / density / sunGain；没有地形光线阴影 |
| 蓝灰末地扭卷云墙 | end-vortex | verticalStretch / twistAmount / cavityRadius / torusWidth / voidDensity |
| 紫色流动光雾 | purple-light-fog | density / extinction / emission / emissionSpeed；亮度运动与遮蔽分别调整 |

`Flow` 的风向 X/Z 与速度相乘，不会自动归一化。`curl` 是解析旋度场对噪声域的艺术扰动，不是气象模拟。噪声取区域中心相对位置，区域整体移动时图案随区域移动。区域固定在世界时，相机移动不会带动图案。

形状 0 为有限圆柱高度云层，1 为球形，2 为有中央圆柱孔的高度云层。球形忽略 bottom/height/innerRadius，环形忽略 centerY。`radius` 是水平或球形外半径，单位方块。近似积分的极限光学深度会提前退出，降低步数会有条带或漏采样，不能称无损。

## 协议

`workbench/schema.json` 是六十二字段的范围、默认值、整数类型与标签来源；`contract.json` 是打包 uniform 与函数契约。配方外层仅允许 `schema`、可选 `name`、`parameters`。schema 为 `world-volume/2`，所有字段完整。完整旧 /1 的五十值可通过显式升级保留，新十二个末地字段取默认且 densityModel=0；缺字段或多字段的旧配方拒绝升级。

```text
python <作品>/scripts/recipe.py upgrade --input <旧配方.json> --out <新配方.json>
```

`densityModel=1` 的 `verticalStretch` 控制纵向相干尺度，`twistAmount` 为每格高度扭角、`twistSpeed` 为秒级旋转；`cavityRadius/cavityAspect` 减去中心椭球，`torusRadius/torusWidth` 减去三维环槽，不是增添正密度云环。`voidDensity/voidFloor/voidFalloff` 在疏空后补下层密度；`nearClearRadius` 仅对相机视线采样设置密度上限，原始世界密度与光路透射保持独立。

`densityModel=2` 的基础薄雾密度由区域/覆盖率控制，世界噪声与秒级波动只调源亮度，不能偷改透射率。它是原创有限世界介质，学习 AstraLex smoker 的亮度分布；原包实际极光、方向背景、距离雾和非Beer光束合成的不同路线见专项研究。

Python 与工作台严格解析均拒绝重复 JSON 键（包括转义后同名的键）。整数不接受 boolean。环形内半径小于外半径，闪光持续不超过周期，太阳方向必须非零。RGB 表示线性源颜色，不是 HEX 的直接通道；需要 HEX 时先转到线性 RGB。

```text
python <作品>/scripts/recipe.py validate <作品>/recipe.json
python <作品>/scripts/recipe.py export --input <作品>/recipe.json --out <新导出目录>
```

导出包含原字节共享 GLSL、Forge 150 顶点/片元 wrapper 与 uniform 描述、完整配方和 pack.mcmeta。ZIP 不含第三方参考光影源码/纹理。安装配套后端后仍需放置 config JSON；资源包不会自动把根目录 world-volume.json 写入实例配置。

shader 描述中的 uniform 初值为 schema fallback 默认值，作者参数保存在完整配方 JSON 中，由配套后端在每次 shader.apply() 前上传。核对作品时分别检查描述的字段/类型/默认值和配方的作者值。

网页改参数会立即渲染；导出 JSON 后用于游戏配置。工作台展示的是解析地面、盒子或浮岛的实际射线命中距离，测试地形不是 Minecraft 截图。JSON 保存介质参数，不保存相机或播放时间；导入会重置当前视角的相机。复核画面时恢复同一相机与秒数，再比较同种子配方；同一输入无历史状态依赖。

## 修改实现

共享 GLSL 位于 `workbench/volume-core.glsl`；浏览器直接读同一文件，Forge 构建任务嵌入同一源。修改后重新构建并绑定新哈希，不借旧验证结果。增加字段时同时更新 schema、contract、Python/JS/Java 校验与导出；需兼容旧配方则定义新 schema 和显式迁移，不能在既有 schema 下重释旧字段。
