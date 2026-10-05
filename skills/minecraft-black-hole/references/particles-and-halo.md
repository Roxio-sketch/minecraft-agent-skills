# 粒子与分层光晕


用于用户要求“颗粒吸入、旋转吸积盘、同时有光晕”的设计。随附 Forge 世界示例已组合各层，可按需求分别控制。

## 参考分工


`unity-black-hole-shader-main.zip` 提供弯曲射线反复穿越盘面时的亮环和层次读感。当前实现将这种层次关系转成原创局部体积发光，不搬运 Unity 源码、skybox 或依赖其静态 Cubemap。完整来源和许可见 [来源对照](source-comparison.md)。

## 三种运动分别设计

| 层 | 当前实现 | 设计入口 |
|---|---|---|
| 颗粒盘结构 | 24 格角向周期，三尺度 cell noise，暗间隙与亮结点 | `scripts/disk_structure.glsl` |
| 盘面旋转 | `theta-time*rotation*1.2/r^1.5`，内圈更快 | 世界 rotation，默认 0.8 |
| 盘纹向内 | `r+mod(time*infall,128)`；正值使固定纹理特征半径下降 | 世界 infall，默认 0.22 |
| 独立粒子 | 固定身份 VBO，GPU 更新寿命/位置，变半径螺旋 | `scripts/particles.vsh`、`BlackHoleParticles` |

约 78% 粒子组从薄盘附近产生，其余从更外层补给。每 8 个为一组，相关的相位、角度和寿命形成小簇；组内少量扰动避免完全重合。当前 640 个 sprite，默认外部补给半径 5.4、尺寸 0.022，长度随寿命增加并沿投影速度方向拉伸。

粒子采用 `q=r^1.5` 随寿命线性下降，角度积分为 `omegaFactor*period/(q0-q1)*log(q0/q)`。不能改成 `time*omega(currentRadius)`，后者对变半径轨道产生错误角速度。出生淡入、接近核心先淡出，重生不可闪现；GPU 验证应追踪同一 ID 并排除本帧重生者。

粒子 sprite 使用实际地形深度，不写深度；片元调用黑洞求解器、在粒子距离处截断，以遮掉核心后和盘后的亮点。局限是粒子中心仍直接投影，未求解粒子多重像。它们是视觉尘埃，不吸取实体、方块或物品。

## 三层光晕

| 层 | 空间形状 | 默认乘数 |
|---|---|---|
| haloRing | 盘内缘附近的窄环，亮而薄 | 1 |
| haloDisk | 贴近盘的较宽暖色辐射 | 1 |
| haloAura | 较暗的外围柔光 | 1 |

`halo` 是总强度，默认 1，各层范围 0–3。`haloEmission` 位于 `scripts/world-halo.glsl`，沿弯曲射线按步长积分，并乘剩余透射率。核心截断和地形遮挡仍有效；不要最后在屏幕上叠不透明圆环。总亮度过高会掩盖颗粒和旋转，先降曝光/haloDisk 再调粒子亮度。默认世界曝光 0.30。

世界光晕还接入每层独立双色/渐变，力度 0 使用盘的 `uHot/uCool`；粒子有独立 `ParticleHot/ParticleCold`，在 `BlackHoleParticles` 内以 HEX 经线性化传入。核心色不影响两者。改变风格时同时设计亮度关系，不必限制为金红或物理黑体色。

## 精细控制与扩展

常用游戏命令见 [Forge 接入](forge-integration.md)。以下是示例现有开发桥，不是预览配方格式：将完整 JSON 原子写入选定 gameDir 下 `black-hole-lab/command.json`，客户端每秒处理一次，等待 `command-result.json` 的新回执后再发下一条。

```json
{"action":"settings","particleCount":640,"particleSpeed":1,"particleSize":0.022,"particleOuter":5.4,"particleBrightness":1.25,"halo":1,"haloRing":1,"haloDisk":0.65,"haloAura":0.8}
```

粒子数量 0–4096、速度 0–4、尺寸 0.005–0.15、外层半径 3.2–8、亮度 0–5。`particles` 是布尔值；`rotation`、`infall` 不改变粒子速度。`time` 为秒，0–86400 固定画面，-1 恢复动画。无跨进程请求 ID，仅用于单操作者本地开发；未知字段/原子参数验证不是此桥的现有保证，不把它发布为通用配方 API。

常用设计：密集盘内尘埃提高数量并减尺寸；外部螺旋增加补给半径和外层占比；清楚旋转降低光晕/曝光、增粗中尺度亮暗结构；柔和星云增宽 haloAura、保留暗核和盘内间隙。改变源码后重新生成 shader 并构建。

## 验证组合结果

分别关掉粒子、halo、rotation、infall。固定相机和时间比较盘层，追踪粒子 ID 的半径/角度，检查前后核心和地形遮挡，再观察完整组合动画。只看全帧变化不能判断是背景、水、盘还是粒子在运动。

内置 Forge probe 保存同帧 `before`（真实场景）、`field`（盘+光晕）、`after`（再加粒子）。粒子证据取 after-field，不能只靠 draw count。光晕单层开关应观察内缘、盘侧、外围分别变亮。WebGL v3.2 已包含组合层及剑刃 mask，使用程序背景而非真实世界；Forge 物品 GUI 尚未接入这些层。各宿主分别取证。
