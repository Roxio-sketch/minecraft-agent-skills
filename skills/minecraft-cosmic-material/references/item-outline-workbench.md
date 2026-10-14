# 物品描边可视化工作台

推荐运行 `assets/outline-lab/启动工作台.py`，它会按启动器所在目录启动本地 HTTP 服务并打开浏览器；也可直接打开 `index.html`。启动器会读取同目录可选的 `启动参数.txt`，自动分配空闲端口、禁用缓存、给每次地址追加启动时间戳，并在资源缺失或 404 时打印实际路径。此页显著标出“二维草图”边界。Lucide 图标许可随 assets 保存。原创剑、斧、环形饰物由 Canvas 绘制，也可导入透明 PNG（最大 4 MB、单边 2048px）。需要看真实八角几何效果时，使用 [Forge 1.20.1 实测预览](item-outline-runtime.md) 的 `/cosmicmaterial outline`。

工作台提供纯色、三色流动、星海、整体彩虹四种模式；支持描边宽度、透明度、二维旋转、三色选择、尺度、速度、主体/描边独立显示、播放暂停和 0–60 秒定位。动画每 60 秒回到起点，不保证回放接缝连续。预设只是起点，参数可自由修改。

主预览为透明 256×256，背景棋盘不写入 PNG；16×16 与 32×32 各自重新生成模型投影与边缘，而非缩小主截图。宽度按 32px 画布基准缩放；超细边在小图里会受抗锯齿与采样影响。PNG 导出保存当前主预览及当前图层开关，可关主体单独导出描边。

## 与 ArcaneVortex 的区别

该工具是原创二维形态预览：向八个平面邻方向重绘 alpha，再减去原 alpha，形成外部描边。它不是原 JAR 的 `(±d,±d,±d)` 三维几何平移、离散面筛选或 LEQUAL 深度合成。宽度是预览像素基准，不等于原 Java 的 0.008 模型位移；旋转是画布平面旋转，不是三维观察角度。

三色流动、星海和彩虹是原创 Canvas 颜色函数，不是原 GLSL 逐像素移植。此网页没有实现背景折射、Sobel、法线外扩、Forge RenderType 或 bloom。核心八角几何和 shader 已在独立 Forge 实验中实现并对若干物品与场景实测，见 [item-outline-runtime.md](item-outline-runtime.md)；不要以此二维预览图证明任意三维物品兼容。实验尚未与原 JAR 作同场景像素对比。

## 配方

JSON 的 schema 为 `minecraft-item-outline-preview/v1`，parameters 中包含 shape、mode、width、opacity、angle、scale、speed、colors、body、edge、time、image。三色必须是三个 HEX；custom 模型需要内嵌 PNG data URI。文件导入验证枚举、范围、布尔值与资源上限，非法导入不覆盖当前配方。

导出包含当前时间与自定义 PNG，因此可以在另一台机器完整恢复。导入后暂停，以便比较同一帧。现有 Forge JAR 不会读取这份 JSON；它是材质制作记录，不是已接通的 NBT 或游戏配置。绘制背景与播放状态属于 UI，不保存在配方中。

## 验证

使用本地 Node.js、Playwright 与已安装 Chromium：

```text
node scripts/validate_outline.cjs <output-directory>
```

可用 NODE_PATH 指向现有 Playwright 模块，COSMIC_BROWSER 指定浏览器路径。脚本通过 file URL 打开工作台，检查非空像素、暂停零差异、宽度、时间变化、零宽透明、配方恢复、非法色板、PNG 导入与导出、16/32 原生尺寸、手机溢出和浏览器错误。桌面/手机截图与 validation.json 保存在输出目录；本机首次结果位于 E:/着色器渲染/research/item-outline/preview-validation。
