# 内部星空制作台

工作台提供可编辑的原创 GLSL 内核、三份配方、实时参数、时间暂停、观察点拖动、示意剑刃窗口和 16/32 px 实际重渲染。它用于材质创作与视觉讨论，不是 Minecraft 渲染器或已完成的新 Forge 功能。

非星空的奇幻材质使用另一套 [fantasy-lab](fantasy-materials.md)，通过同一服务器的 `--lab fantasy` 启动；有七套结构和动态算法。这里的三配方与旧冻结问题保持独立记录。

## 运行

从当前 skill 所在目录运行（使用本机可用的 Python 3）：

```powershell
python scripts/serve_authoring.py
```

默认 `--port 0`，由操作系统分配空闲端口，浏览器必须打开终端实际打印的地址，不固定猜测 8768。服务器仅绑定 `127.0.0.1`，仅提供 `assets/authoring-lab` 下的文件；Windows 显式使用 `SO_EXCLUSIVEADDRUSE`，并关闭地址/端口复用。多个默认启动分别取得端口；显式指定的端口已占用时退出码为 2，不停止、不接管、不复用已有服务。关闭自己启动的服务用该终端的 Ctrl+C。

`--json` 输出启动记录；`/__cosmic__/health` 返回服务名、协议版本与当前资源 SHA-256。验证脚本使用这些信息拒绝其他制作台或不同版本，不能只用“端口有 HTTP 响应”确认目标。

Python 不在 PATH 时选择已有解释器；本机曾使用 `C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，其他机器不得假定这个路径存在。界面不需要网络、模型 API 或外部图片。

## 文件与真实接口

- [material.glsl](../assets/authoring-lab/material.glsl)：无贴图的密度场、四个星点层、发射/吸收；是共享语法子集内核，不是包含 main 的 Forge shader。
- [preview.js](../assets/authoring-lab/preview.js)：WebGL 2 host、虚拟相机、显示转换、控制与导出。
- [recipes.json](../assets/authoring-lab/recipes.json)：三种实际读取的配方；颜色为线性 RGB。
- [serve_authoring.py](../scripts/serve_authoring.py)：标准库本地服务器。
- [validate_preview.cjs](../scripts/validate_preview.cjs)：随 skill 打包的完整浏览器验收入口。
- [freeze_suite.cjs](../scripts/freeze_suite.cjs)：跨帧、三画布、三配方的零容差冻结检查与失败图保存。
- [preview_validation_support.cjs](../scripts/preview_validation_support.cjs)：临时服务、资源身份核验、浏览器发现与差异度量。

| 字段 | 内核输入与含义 |
|---|---|
| seed | 稳定布局种子，0–65535；不随时间重排 |
| density / starSize | 星格命中概率倍率 / 星核尺寸倍率；不是绝对颗数/米 |
| cloud / dust | 发光气体密度 / 暗尘消光倍率 |
| depth | 窗口后的虚拟材质长度，1–6；不是方块世界坐标距离 |
| parallax | 横向射线斜率倍率，0–1.5；0 取消平移视差 |
| scale | 密度场空间频率倍率，0.4–2 |
| speed | 云采样坐标的平移速度倍率；0 停止云运动 |
| starSpeed | 星点分层漂移与缓慢旋转倍率，0–1；0 固定星点位置 |
| twinkle | 逐星亮度包络强度，0–1；0 取消闪烁 |
| twinkleRate | 逐星闪烁频率倍率，0–3；0 固定各自当前公式的零时刻亮度，不停止星点漂移 |
| exposure | 预览显示曝光，不改变材质覆盖 alpha |
| steps | 8/16/24，体积积分采样数；四个星点层不随之增加 |
| void / cloudA / cloudB / starTint | 线性 RGB 底场、云暗部、云亮部、星点配色 |

时间单位为秒。拖动改变材质空间的虚拟观察原点，不移动 Minecraft 相机。星层均为正对虚拟窗口的平面，是适合创作讨论的近似，侧向极端角度和任意曲面仍需要自己的坐标适配。

导出文件中的 `schema: cosmic-authoring-preview/v1` 只在这个工具里定义。现有 Forge JAR 不会读取它；不得把它写成已经支持的 MaterialDefinition/NBT API。导出记录 seed、参数、层开关、视角与时间，方便下一次恢复设计依据；UI 当前不提供文件导入按钮。

## 星点动态与验收

星云输运、星点位置、星点亮度使用独立控制。原版本只有云坐标接入时间，星体位置静止，`twinkle=0.08–0.12` 的小幅变化又容易被高亮压缩隐藏。现在三个预设的 `starSpeed=0.30–0.42`、`twinkle=0.72–0.82`、`twinkleRate=0.8–1.0` 提供可辨认的创作起点；这些值不是 ArcaneVortex 原参数。

四个星层按深度使用不同输运速度和角速度，近层快、远层慢，另有有界的逐星小轨道。随机种子、星格身份、颜色、相位不随时间重新生成；连续移动采样域使星体穿过画面。邻域取样与像素足迹过滤避免格边或亚像素尺度下随机跳点。此模型仍是分层虚拟空间，不宣称为真正三维恒星模拟或参考的分形星海。

每颗星的固定哈希给出相位和频率，非负亮度包络具有足够低谷。调闪烁时同时观察星核和光晕，检查最终色调映射后的画面；单看 shader 中有 `sin(time)` 不证明可见。暂停停止统一时间，`speed=0 && starSpeed=0 && (twinkle=0 || twinkleRate=0)` 时给 GPU 规范化时间 0。主画布随 requestAnimationFrame 绘制，小图约每 100ms 更新；实际帧率取决于设备，不能把回调频率当性能认证。

`validate_star_motion.cjs` 自动关闭星云/暗尘，分别验证三预设在大图与 16/32px 中有星点动态；再记录 800×540、每秒20个固定时间样本、6秒的仅漂移、仅闪烁、完整材质片段。通过图像中的孤立星峰跟踪连续位移、定位固定位置的亮度起伏，附真实时钟动画循环与暂停检查。固定时间片段按材质秒播放，末尾重播有接缝，不是游戏实录、实时帧率或无缝循环。

```powershell
node scripts/validate_star_motion.cjs --python python --out ./star-motion-output
python scripts/make_star_motion.py ./star-motion-output
```

合成器需要 Pillow，输出单段动画、三栏对照和0/2/4秒接触图；WebP是便于展示的有损压缩，逐像素判定使用原始读回数据。原始 PNG、逐帧跟踪与判定保留在输出目录，脚本自身不依赖研究目录。完整暂停和重复冻结仍运行 `validate_preview.cjs`，不降低原来的零容差，也不因某次通过关闭历史 P3。

## 接入 Forge 1.20.1 需要做的工作

1. 选择方向场或局部有限体积契约；从实际模型与相机矩阵提供 `entry`、`ray` 和尺度，不抄预览的屏幕坐标。
2. 将内核放入新 shader，使用 GLSL 150 host、匹配的 VertexFormat、实际 mask 和 sampler。WebGL 的 `gl_VertexID` 全屏三角仅属于预览。
3. 将参数定义、默认值、验证、Java uniform、draw 时绑定和缓存键一起实现；循环有确定上限。
4. 用原 sprite alpha 保留几何轮廓，以区域 mask 混合原材质与新 RGB；体积内部透射率不是要求整个物品变半透明。
5. 确认 fog、lightmap、overlay、glint、gamma 和 framebuffer 合同；继承 [Forge 基线](forge-1.20.1-runtime.md) 的生命周期，但新算法重新实测。
6. 手持、大方块、窄剑刃、多材质同屏、资源重载、实际小图和时间/相机对照。若要屏幕外 bloom 或场景深度体积，另做对应 pass。

## 可复跑验证

只需要本 skill 目录、Python 3、Node.js、Playwright 和已安装的 Chromium 浏览器，不依赖 `research` 或其图片。使用已有 Node 环境，或令 `NODE_PATH` 指向含 `playwright` 的模块目录；脚本不会自动安装浏览器或依赖。Node.js 需提供全局 fetch / AbortSignal.timeout（本实现按 Node 18+ 接口使用）。

```powershell
python -B scripts/test_preview_server.py
node scripts/test_preview_validation.cjs
node scripts/validate_preview.cjs --python python --out ./validation-output
```

Python 不在 PATH 时，将 `--python` 换成已验证的解释器完整路径。浏览器自动使用本机已有的 Edge/Chrome 或 Playwright Chromium，也可 `--browser <可执行文件>`。不传 `--url` 时，脚本自行启动独占绑定的临时服务，在动态端口完成测试后关闭它及自己的浏览器；不会关闭其他运行中的服务。默认结果保存在系统临时目录的新子目录中，并打印 `validation.json` 完整路径。

已有新版制作台可用 `--url <终端打印的地址>`；先校验服务与四个资源文件的哈希，再打开固定时间测试页。旧版服务缺少健康端点时校验失败，应重新启动当前版本，不跳过身份检查。

完整检查包含原有材质行为，以及默认 30 轮冻结检查：三个配方轮换，800×540、16×16、32×32 三个画布，每轮分别比较零运动的不同请求时间与相同状态重绘，共 180 对。用 `--freeze-runs N` 控制轮数。另以真实动画循环点击暂停按钮验证时间和画面停止。

冻结判据始终是 **0 个像素变化**，不把 `1/255` 自动接受为通过。失败退出码非零，结果记录像素数、最大通道差（整数 0–255）、配方、实际 uTime、DITHER、GPU 和采样目标；首次冻结失败保存 `freeze-before.png`、`freeze-after.png`、`freeze-failure.json`。研究目录的旧入口仅转发到本技能脚本，不维护第二份实现。

## 冻结读回与已知边界

用户报告过 67 个像素、最大通道差 1/255 的偶发结果；本轮增强测试在旧默认 framebuffer 读回路径上记录过 87 个像素、最大通道差 1/255。关闭 DITHER 且实际 uTime=0 时仍复现，因此这两项不能单独被认定为历史根因。

本轮也对照了自有 RGBA8 framebuffer、解析式像素足迹、独立 RGBA32F 读回和相同帧连续读回。小型探针未稳定复现，但完整验收仍记录到偏差；这些实验没有建立单一因果关系。未解决问题的 framebuffer/足迹替代没有保留在正式 host 中。

当前保留的是明确的状态契约：完全静止的材质向 GPU 传规范化时间 0，显式关闭 DITHER，并记录浏览器版本与采样目标。另修正了负值进入 `pow(x,2)` 的 GLSL 未定义行为，使用 `x*x`；它也不是已确认的冻结根因。**冻结位级差异仍为开放问题**，遇到差异测试继续失败，不能将交付脚本或一次成功误记为消除该问题。

浏览器 ANGLE / D3D11 检查不等于 Forge OpenGL 实测。大图和小图仍需人工看过；数值通过不自动表示美术完成，也不是性能基准或全装备兼容认证。
