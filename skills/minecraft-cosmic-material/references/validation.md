# 验收与排障

证据层级必须分开：源码阅读、静态数据检查、编译、GPU 加载、实际物品画面、光影兼容。五份原始工程仅静态分析；后续独立 Forge 1.20.1 实验已完成生产构建、GPU 运行和代表性物品画面验证，见 [实测基线](forge-1.20.1-runtime.md)。新增设计仍须验证实际修改的入口与效果，不能仅凭已有样本继承“通过”。

ArcaneVortex 增加了字节码绑定分析和四个改编片元的独立 WebGL 观察，不代表原 JAR 游戏实测。原创制作台的浏览器检查也单独记录。具体边界见 [研究](arcanevortex-study.md) 与 [制作台](authoring-workbench.md)。

制作台验收入口现随 skill 打包为 `scripts/validate_preview.cjs`。位级冻结稳定性仍有已复现的 1/255 通道偏差，依制作台说明单独记录为开放问题；不放宽零容差、不把历史单次通过扩大为稳定性保证，也不将这一项失败笼统解释为所有材质渲染不可用。

## 材质本身的视觉验收

将用户的形容词转换成可检查关系，依 [material-authoring.md](material-authoring.md) 逐项看图：主形是否落在覆盖窗口内、留黑是否足够、远近星能否区分、暗尘是否遮住后方、动作是否符合所选空间。避免只检查“有彩色像素”。

固定 seed/time/view 做迭代；时间变化和视角变化分开观察；关闭各层排查相互淹没。缩到 16/32 px 需实际重渲染而非高分辨率后缩图。声明某个质量档只是降低成本时，检查采样数变化没有同时大幅改变亮度/透明度。对窄剑刃和方块表面分别看构图。

## 实施后的代表性样本

按声明支持的类别选样本，不要求每个小改动重跑全部类别：

| 用例 | 要发现的问题 |
|---|---|
| 普通二维物品 + 局部灰阶 mask | R/alpha 混淆，轮廓/柄部污染 |
| 完整三维 cube/自定义 mesh | 平面 mask 壳无法贴合，漏背面/侧面 |
| 弓/弩所有动作状态 | override 被 wrapper 丢失，动画阶段错位 |
| 盾牌/三叉戟/箱子类动态物品 | 绕过 baked quads 的路径、重复 renderer |
| 染色、附魔、多层与半透明物品 | tint、glint、排序与深度缺失 |
| 穿戴护甲和动画肢体 | NEW_ENTITY 格式、sprite.wrap、实体与相机方向 |
| 同画面 A-B-A：不同 palette/opacity | global uniform 串值和缓存污染 |

Forge 1.20.1 的原版式穿戴防具与原版盾牌现有独立 [九场景运行证据](forge-1.20.1-equipment.md)：四槽正/背面、GUI、第一人称普通/纹章/边框盾、举盾和资源重载；该结果不扩展到表中三叉戟、箱子、第三方盔甲层或骨骼载体。

每个代表样本覆盖实际承诺的 GUI、第一/第三人称左右手、GROUND、FIXED/物品展示框、HEAD 等上下文。不要把“全物品”转换成只测 GUI 图标。

弓的验证需要记录实际使用 tick 和最终 override sprite：单独调用客户端 `startUsingItem` 可能被服务端重置，出现 `isUsingItem=true` 但 tick=0 的假蓄力。本地实测基线通过正常 `gameMode.useItem` / 持续 use 输入获得 41 tick 与 `bow_pulling_2`；静止和满弓截图不等于覆盖全部中间帧。自动化结束时释放 use，并停止客户端和服务端使用状态，避免留下使用键或意外发射。

## 坐标、资源和生命周期

- 固定 time，旋转相机/物品，改变 FOV 与 GUI scale，移动观察位置：结果应符合所选空间锚定语义，而不是一律要求无变化。
- 平移物体时，“另一个空间的窗口”和“跟着物体的局部宇宙”本来就不同；先写预期，再比较。
- 资源重载后更换 atlas 布局，检查 UV、mask、动画帧与 shader 实例更新；检查正在排队的旧 draw packet 被清除。
- 离开世界/主菜单预览无 player 或 level，shader 编译失败、缺失 mask/图集 sprite 均应有可诊断回退。
- 测承载物自遮挡、地形遮挡与其他物品相互遮挡；透明 mask 像素不应挡住后续对象。

## 常见现象

| 现象 | 优先检查 |
|---|---|
| 只有底图没有 Cosmic | 注册/回调/实际入口、uniform opacity/scale、mask.r、EQUAL 深度是否已有对应面 |
| 黑紫格或整块星体矩形 | atlas 未收录、取错 namespace、透明图 RGB 错误、cosmicuvs 全零 |
| GUI 正常手持异常 | GUI 特例 scale、相机方向、顶点格式、Iris defer |
| 同物品切换位置后变色 | 共享 uniform 延迟绘制、全局静态上下文、资源代次 |
| 正面有效侧面无效 | processFrames 壳与真实三维模型不同；重用原 quads |
| 转头星空双倍旋转 | CPU PoseStack/ModelView 与 yaw/pitch 重复旋转 |
| 穿墙或边缘亮线 | 无深度、LEQUAL 偏移太大、全覆盖 mask 脱离底层轮廓 |
| 重载后花屏 | 旧 sprite UV、旧 uniform、模型缓存没有失效 |

## 光影兼容与性能

Neo 快照的 Iris 路径将部分手持层延迟到 AFTER_LEVEL；仅保存矩阵并不足以证明正确，还需确认该帧调用顺序、实际 framebuffer、深度附件、viewport、第一人称绘制时机和 tone mapping。不要把“检测到 Iris 类”当作通过所有 shader pack。分别记录基础环境和具体 Iris/Oculus/渲染加速 mod 版本与 pack。

有 deferred queue 时，恢复状态放在 finally 中，异常也清队列；捕获会变化的参数与必要的 ItemStack 数据快照；检查 enqueue 和 drain 是否在同一帧，不让上一帧手部姿态污染下一帧。单物品 draw 的矩阵必须在 flush 完成前有效。

性能至少记录 GPU/驱动、分辨率、质量层数、GUI 物品数/屏幕覆盖、CPU 烘焙成本、draw 数与 GPU 时间。先测纯基础模型，再测 cosmic，区分 CPU 与 GPU 增量。既测手持近景，也测满箱物品。不能从静态 16 层推算一个可信 FPS。
