# WittyScrap 层叠光带参考实现 · 2026-10-09

源仓库 WittyScrap/unity-black-hole-shader，Copyright (c) 2025 Francesco Litrico，MIT。噪声图与算法改写现在属于有署名的移植；不是此前的纯启发式条纹替代。LICENSE 随素材与 Forge JAR 分发。

## 生效路径

黑洞模式 diskStyle=ribbons 使用 bhTraceRibbon。极坐标 noise UV 用 outFade 映射径向，固定纹理及线性双线性采样、Unity sRGB 解码、统一角向运动；内缘 1-pow(outFade,100)，外缘 outFade。每个真实 forward plane crossing 累加辐射/覆盖，穿过核心保留前景辐射。按参考径向逆平方加速度追踪，uBend 控制强度。核心有限半径、正向交点、世界坐标、前景保护及深度生命周期为宿主适配。白洞、尘埃、电影盘保留原求解器。

盘面多重图像必须由弯曲光线的盘交点产生，不用屏幕贴环。复制参考不等于相对论精确模拟；用户尺寸/外半径、背景与摄影参数改变形态和图像比例，不能宣称不同场景逐像素等价。

## 发光

diskBloom 0–3、diskBloomRadius .25–3，仅层叠黑洞显示控件。HDR RGBA16F source 只记录盘面辐射，不抽取地形、天空、UI 或核心。独立双尺度模糊；核心 source mask 防止光溢出填满黑核；复用白洞扩散资源但禁用白洞星芒。线性曝光的指数合成保留细纹，避免 LDR 加法夹白。新预设 1.1/1，旧完整配方缺省 0/1，白洞新增字段默认 0/1。

## 验证

正式宿主在 ASTRA-TEST 独立 VFX-Ribbon-Reference-Check 存档运行：蓝/橙、斜视/顶视/侧视、277.85 秒、Bloom 开关、density=0、效果停止。每图 GL=0、主深度变化=0，并记录 sourcePixels/sourcePeak/contributionPixels；密度零必须无选中源及扩散贡献。独立模板只通过构建，不继承宿主实机认证。

模板输入需同步 world-kernel、world-layers、world_main、mature-world/particles.json、mature-core_composite、Java recipe/core bloom/render、MIT 图和许可证。工作台浏览器 RibbonNoise 必须绑定独立纹理单元，世界完整状态恢复含第 5 单元；不得只同步 GLSL 后遗失 sampler 或默认字段。


## 后续视觉校正（2026-10-09）

层叠光带新增美术化方向响应：亮侧方向反转，方向增益使用幂曲线以保留暗侧纹理；速度对方向增益设置下限，旋转暂停不会熄灭盘面。该响应不是上游 Doppler 公式的逐式复现。盘面径向温度分布收窄，高温色不再覆盖整盘。预设 Bloom 降至 0.45、范围 0.65，仅影响层叠黑洞；白洞分支不受影响。实机证据以最新 ribbon-reference-probes 为准，GL/深度通过不代表像素复刻通过。
