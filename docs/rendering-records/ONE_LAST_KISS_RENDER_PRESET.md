# 彩色原画线稿渲染预设（Forge 1.20.1）

## 交付

- 可构建工程：`local-environment-lab/`，版本 `0.3.0`；预设默认关闭。
- 游戏预设：`local-environment-lab/workbench/one-last-kiss.json`。装入 `<gameDir>/local-environment-lab/presets/one-last-kiss.json` 后，以 `/localmaterial sketch on` 开启、`reload` 热读配方、`off` 关闭。
- 渐变工作台：`local-environment-lab/workbench/`，运行 `python workbench/serve.py`，打开打印出的本地 URL。可调五个色标的位置/颜色、角度、跨度、偏移，并导入/导出同一 JSON。界面另提供线条敏感度、细节抑制、深度边界、明暗和粗细参数。
- 可复用 Skill：`skill/minecraft-illustration-render/`，包含管线决策、Forge 示例和同机位实测图。

用户给出的文字与插画是视觉参考：白纸留白、筛选出的结构线和贯穿屏幕的暖冷渐变。交付没有复制参考人物、静态图层或像素。

## 2026-10-02 漏边缺陷修复

查明旧代码把 cutout 渲染阶段当作叶片类别；原版草方块同样使用 cutoutMipped，因而整片地形边缘被误删。此前把这一现象解释成仅有颜色/深度的限制不准确。

修正版增加多尺度色差结构图和方向连续性判断，恢复草地台阶轮廓；不再按 cutout 阶段一律抹除。正式 JAR 的 [2560×1440 实图](research/one-last-kiss/edge-fix-final.png) 和 [854×480 实图](research/one-last-kiss/edge-fix-final-small.png) 可检查。仍有树叶碎纹和偏淡细线；这是改进版，尚不能称为所有场景边缘完整的最终质量。

## 验证

- Forge 47.4.10 / ASTRA-TEST / ASTRA_VFX_TEST：实际安装、重启、绘制，已移除临时调试资源包。最终 `generation=2 gl=0 failed=false debug=0`；控制模式已释放，预设开启供查看。
- 构建成功；构建与安装 JAR SHA-256 相同：`d8884011f3b825a266ece424d318530dc2d9c8044160834ff726cc4edba1ac0c`。
- 工作台共享最终片元着色器编译通过，角度/跨度改变图像哈希，JSON 导出和重置通过，无浏览器错误。工作台不生产真实深度/分类缓冲，不能替代上述实机图。
- 旧版 evidence 保留为历史，不作为本次验收。名为 `edge-fix-800.png` 的中途图实际是 2560×1440 且视角变化，排除为尺寸/同机位证据。
- Embeddium、Oculus、Rubidium、Fabulous 未适配。没有新增时域抗闪烁或精确叶片材质 ID；多次纹理采样的帧耗尚未量化。


## 后续精修

取消将连续坡面的深度梯度直接画成边线；以深度斜率转折、带宽容差的方向支持和局部结构密度筛选轮廓。树叶内部碎纹明显减少，台阶与斜边更连续；覆盖聚合改为密集加权采样。开发图：[854×480](research/one-last-kiss/refined-small.png)、[1280×720](research/one-last-kiss/refined-large.png)。仍存在少量碎纹、浅线和轮廓圆化；尚未加入时域稳定或性能计时。

本次精修 JAR SHA-256：`2c7f581807ad5bcc1a098bd3a95ca0ce07c8ebf5d309e06f67e3325a69a5534d`。上节哈希与图属于前一轮修复，保留为历史记录。

正式 JAR 验证：无调试资源包覆盖，2560×1440 原机位与转动视角均成功出图（refined-final-native.png、refined-rotated.png）。资源重载后 generation=2、draws=4799、gl=0、failed=false、debug=0；控制模式已释放。该验证确认出图与重载，未做动态逐帧抗闪烁或 GPU 计时验收。

## 当前硬线稿输出（用户要求清晰、无圆润线条）

已移除“覆盖图等值线直接着墨”：该路径会移动真实边界、把方块角部画圆并形成双轮廓。最终几何轮廓只由全分辨率深度斜率转折产生，半分辨率结构图仅辅助连续性/密度判断，不将模糊后的形状描回画面。颜色表面暗纹压至硬线阈值以下，色差只作弱补充；天空和工作台保留颜色结构回退。

线条覆盖通过 smoothstep(0.16,0.55,drawing) 收紧，降低模糊的低强度边缘；渐变色经过 1.25 次幂加深，少量背景明暗降至原来的 35%。这不是对最终图像做锐化/高斯模糊或圆形扩边。用户选择的色标仍控制渐变，但输出线色有此加深映射。

取舍：更少的表面纹理细节、更硬的像素线；亚像素锯齿、少量断线和运动闪动仍可能存在。不要为了遮盖这些问题重新加入圆润的模糊轮廓；真正的抗锯齿或时域稳定需要单独设计验证。

本次硬线稿 JAR：F184C71C292EE9053B833E9AF3380B3D683DDC66A1F1E5E9951E0D0FD5E41E56。前述哈希和效果图保留为历史版本。

## 短缺口、有限边缘覆盖和对象专用路线

本轮新增沿实际深度边线切线的短缺口补线和仅跨线法线方向的弱覆盖，保留深色尖锐线芯。固定机位 A/B：[旧版](research/one-last-kiss/bridge-before.png)、[补线](research/one-last-kiss/bridge-after.png)、[补线与边缘覆盖](research/one-last-kiss/bridge-aa30.png)。在代表性地形 ROI 内，新版比旧版多 1723 个强线像素、旧强线未跌出该阈值；边缘覆盖另增 396 个弱线像素。图像差异不能替代移动镜头的时域验收。

水面和树叶应提供独立的真实类别/表面信息。水面需在流体原绘制阶段保留 surface depth、alpha、流动 UV 和几何信息；叶片需与同处 cutoutMipped 的草方块分开，并以真实 cutout 几何保留外轮廓。具体数据流、质量档和兼容回退见 [专用线条方案](skill/minecraft-illustration-render/references/water-leaf-lines.md)。这两条专用类别路径目前是设计方案，未声称已在正式 JAR 中实现。
