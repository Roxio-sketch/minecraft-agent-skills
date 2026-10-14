# 玩家程序化天空与环境创作

[procedural-sky-lab](../assets/procedural-sky-lab) 提供 WebGL2 工作台及 Forge1.20.1 /47.4.22 /Java17 示例。0.6.0使用 `cosmic-sky/6`、188字段、39起点。原 `forge-1.20.1-lab` 和15用例checker独立保留，不认证扩展。

## 描述到配方

沿用目标版本、工程和天空路由。读取 `workbench/recipe.js` 的 FIELDS/PRESETS/PACKS/COLORS/ANGLES 获取真实范围、选项和度转弧度字段。完整配方保留所有188字段；颜色 `#RRGGBB`，整数/枚举按schema校验。选近似预设再改，不把某个起点固定为所有创作的默认。

| 目标 | 参数与承载 |
|---|---|
| 蓝天、晨昏、月相、薄云 | atmosphereStrength、太阳高度/方位、cycleSpeed、月相、薄云风速/演化；无限远方向天空 |
| 星空/银河/流动星云/极光 | 各层强度、色板、密度、尺度、独立速度；角域图案不称可进入体积 |
| 普通/七种巨型天体 | celestialType或giantBodyStyle=1–7、giantBodyScale/Separation/Atmosphere；复用天体位置、相位、自转、双色和环。见 [celestial-giants.md](celestial-giants.md) |
| 原动态黑洞 | blackHoleStyle=1、强度/位置/大小、盘速度/宽度/倾角、lensingStrength；有限曲率积分 |
| 二维电影黑洞 | blackHoleStyle=2；暗核、金白环、上下弯曲光弧与横贯吸积带；ringBrightness/Width、Glow、Arc、Streak、Contrast、Noise、盘双色和速度；lensingStrength偏折背景星点/银河/星云 |
| 能进入的积云 | volumeCloudStrength/Coverage/Density/Scale、Base/Thickness/Distance/Erosion、风与演化、Ambient/Sunlight；世界密度 |
| 雨雷/实际天气 | rainAmount/thunderAmount、雨丝速度/长度/风、雷频率/时长；weatherFollowWorld=1读取游戏雨雷 |
| 双虹 | rainbowStrength/Width/Wetness/secondaryRainbow；反太阳轴角域，主外色序相反 |
| 冰晕 | haloStrength/Width/Ice/Moon；日月约22°，与普通moonHalo分开 |
| 雾/云影/天光 | fogStrength/Density/高度衰减/噪声，cloudShadowStrength、lightShaftStrength/Density；实际场景深度 |
| 异界/鸟剪影 | specialStyle/Strength/Scale/Speed/Distortion、birdDensity/Speed；天空装饰 |

强度0关闭层；质量1/2/3改变预算，预览倍率只改像素数。时间、相机和测试场景属于预览，不写入配方。自动昼夜只影响美术日月，速度1约179.5秒一周期；月相0满月，±90半月，±180新月。

## 可携带工作台与升级

复制资产至目标工作目录，用 `python workbench/launch.py`。仅Python标准库，绑定localhost，可用 `--root`/`--no-browser`/`--check`。不要在安装skill资产内保存用户导出。便携代码不得硬编码原作者路径。

支持拖动、FOV、时间、PNG/JSON/资源ZIP、本机保存、39预设分类和188控件。世界预设启用程序地面/盒体及真实采样深度；WASD/QE、飞行按钮和xyz可以检查位置/进入云层。网页模拟雨雷只驱动跟随世界模式，在游戏读取真实输入。

浏览器显式升级完整/1(34)、/2(58)、/3(90)、/4(160)、/5(184)。/4保留原大气、云雾和雨幕，新增后端默认保留旧行为。原值保留，新增扩展默认关闭；/3保留原大气/薄云，/1/2沿用旧升级关闭新增大气，/1还关闭天体/环/黑洞。API和Java只认完整/6。重复键、未知/缺失字段、错误类型、越界非有限值拒绝；失败保留作品。随机端口会隔离origin存储，长期作品保存JSON。

## 五份原创GLSL与深度契约

依次拼接 `sky-core.glsl`、`sky-extra.glsl`、`atmosphere-physical.glsl`、`weather-core.glsl`、`environment-core.glsl`。前两份合成无限远天空，末份 `skyComposeEnvironment(scene, origin, direction, sceneDistance, hitGeometry)` 合成世界环境。WorldWeather为vec2实际雨雷。前景使用已曝光颜色/实际深度，不能误称线性HDR物理积分。球形大气、雨雪、原版玻璃/水与降分辨率的单位和数据契约见 [advanced-sky.md](advanced-sky.md)。未实现历史重投影。

天空方向去平移并每帧使用实际投影；Forge在天空入口保存本帧真实逆投影/逆view rotation，环境合成使用本帧矩阵及逆view平移，不能把已恢复为投影的event poseStack当view。projection中的bob平移需另求光学眼点，射线与有限表面距离都减去同一眼点，详见advanced-sky。复制主颜色/深度到自有TextureTarget，禁止读写同一纹理；inverse projection重建沿射线距离，不能直接用非线性depth乘距离。世界云按高度层求有限区间、地形深度截断。

体积云16/24/32视线步，太阳2/3/4；雾/天光12/20/28；雨丝48m局部有界DDA24/36/48。云/雨具有世界视差；方向天体、虹/晕/鸟无平移视差。表面云影为深度重建点的太阳透射调制，不替代完整场景光照。原版BLOCK透明层可以分段；实体/粒子/其他RenderType需另适配。解析大气和光学色带仍为美术近似；新RGB球形大气为有单位的透射及高阶近似，不是完整光谱/电磁求解器。

星点有限候选与核心/芒/晕的径向支撑保持一致。旧星中心cell内.12–.88，候选±1，贡献在.75–1平滑归零。行星环平行射线必须保留安全分母、有界半径导数、AA宽度上限与正向有限支撑；不能关闭正常层来掩盖断续细线。二维黑洞也先裁有限方向域，再计算投影/AA，视野外无无限横线。

曲率黑洞56/72/88步中点积分只偏折程序宇宙；电影类型为独立二维方向平面光带与程序纹理，并在有限域内重采样宇宙背景；普通天体仍用程序球面，新增巨天体用稳定方向平面。发光不是地形照明/屏幕Bloom，黑洞不吸实体；近距离世界透镜/粒子转独立黑洞技能。

## 生成、接入与验收

改 `recipe.js` 后 `node workbench/sync-metadata.mjs`，再 `python workbench/launch.py --generate-forge forge/src/main/resources`。生成sky/environment与默认配方，并从forge-support附加LUT、surface和重建资源；改源码时还需运行Forge的sync-advanced-resources.py，便携依赖模板随附；新增字段同步SkyRecipe.java校验/上传。角度由ANGLES明确转弧度，其他数值不要重复转；RingSettings与PipelineSettings为vec3，其余pack vec4，颜色vec3。JAR实际资源须和源生成结果一致。

资源ZIP需要示例MOD，不是独立Oculus/OptiFine包。JSON放config/cosmic-sky.json，`/cosmiclab load`；F3+T后 `/cosmiclab reset`使用资源默认；off恢复原版。网页暂停当前时间，游戏freeze固定0。生产工程按维度路由，不自动替换已有模组。启用程序降水替换原版降雨视觉，保留声音/tick/天气逻辑；气候模式按位置查询生物群系雨雪与屋顶。

0.6最终/6游戏验收使用 `forge/check-advanced-validation-v6.py`，41环境用例及新增天体用例分别采集到独立目录；需要Python Pillow和Java17。`check-advanced-validation.py` 对应冻结/5，0.5的 `check-full-sky-validation.py` 及31用例也是历史。run-lab.ps1仅退出自己的实验客户端。旧39 /2、26 /3分别使用冻结schema-v2-baseline/schema-v3-baseline，不能把新/6记录交历史checker。

实际验证世界视差/云内外/遮挡、风/演化/雨/雷动态、虹角度/反色、22°晕、两黑洞、方向/实际矩阵、天气输入、GPU活跃uniform、GL/depth/state、resize/FOV/重载；改小参数无需重复全部检查。成果证据在工程VALIDATION.md。历史0.4对/2旧7预设为6图完全一致、1图1像素1通道1LSB；不得改写历史为全精确。

其他GPU/版本、任意透明物体或4K性能需另验。Oculus开启包时交由它拥有渲染，关闭恢复Cosmic；不宣称任意包同时叠加。参考包静态研究，不复制受限shader/纹理，出处见sky-materials.md。

旧/5升级保留184值，新giantBodyStyle默认0。新配方188字段、39起点严格/6；旧版API不自动升级。巨天体主体角半径乘scale后上限67.6°，方向天体无平移视差。Bliss云内闪光另为 [研究](bliss-end-clouds.md)，不是可用配方字段。
