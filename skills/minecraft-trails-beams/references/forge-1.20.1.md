# Forge 1.20.1 接入

随附工程固定 Java 17、Forge 47.4.10、ForgeGradle 6.0.54、Gradle 8.8、official 1.20.1。这与 Lodestone/Veil 1.21 的包名和 API 不同。模板未引用它们，直接使用 net.minecraftforge。

## 可复跑工程

从 scripts/author_effect.py 生成工程，在输出目录设置 JAVA_HOME 为 JDK 17：

```powershell
.\gradlew.bat --console plain build
.\gradlew.bat --console plain coreCheck
```

build 包括自定义 coreCheck，并在 jar 后运行 reobfJar；生产 JAR 在 build/libs。依赖已缓存时可 --offline，未缓存需正常下载，不关闭 TLS 校验。模板不配置 runClient，验证用生产 JAR 安装到明确的 Forge 测试实例；要用开发运行时可按目标工程添加 run 配置。

本机 JDK 与离线缓存的便利入口写在工作区运行报告，不能假设其他电脑具有相同路径。生成器不会覆盖已有目录，不自动选实例。

## 类的职责

| 文件 | 职责 |
|---|---|
| Geometry | 无 MC 依赖：双精度向量、历史、距离细分/断带/容量、累计 UV、ribbon/cross/tube/blade |
| Recipe | Gson 严格解析，有限数值和预算、色带/宽度曲线 |
| Paths | Gson 严格解析演示路径；六种确定性路径与最多四条分叉，返回世界空间点列 |
| Effects | 客户端 emitter id、轨迹/束生命周期、世界清理、按距离近似排序 |
| BeamRender | POSITION_COLOR_TEX shader、每层 RenderType、参数绑定与私有 BufferSource flush |
| ClientLab | 客户端命令、`recipe.json`/`path.json` 加载、示例路径、截图验收；不是正式武器适配器 |

新项目保持 shader registration、RenderType 和顶点输出格式三方一致；不要把 NEW_ENTITY consumer 直接用于该 POSITION_COLOR_TEX shader。注册在 MOD bus，世界绘制在 Forge bus 的 RenderLevelStageEvent.AFTER_PARTICLES。mod 的客户端订阅者限定 Dist.CLIENT，入口类不加载 MC 客户端类型。

世界绘制使用 event PoseStack 和当前相机世界位置；BeamRender 在顶点提交前减相机一次。无需再 pose.translate(-camera)。private BufferSource 不 flush 其他 mod 的缓冲。RenderType key 为 immutable Layer，不含逐帧 time；本次每层立即 flush，防止共享 uniform 串值。

## 真正的武器适配

给每个持有者/手/动作实例独立 UUID；例子中的数字由目标 renderer 提供，不能照抄为万能偏移：

```java
// Tick/固定采样器：从真实动画求出刃根和刃尖世界位置
double seconds = client.level.getGameTime() / 20.0;
Effects.sample(effectId, recipe, seconds, rootWorld, tipWorld);
// 停止挥击时停止 sample 即可，tick/render 仍调用 expire，让旧点消散。
// 明确取消、切世界/持有者移除：Effects.remove(effectId)。

// 实时束：路径可来自直线、样条、碰撞结果、自定义生成器
Effects.beam(beamId, beamRecipe, worldPath, seconds + 0.2);
// 持续发射时更新同一个 id，停止更新后到期消失。
```

若使用工作台导出的 `path.json`，先 `Paths.parse`，再每个模拟时刻调用 `Paths.generate(spec, origin, forward, side, up, seconds)`；为返回的每条 Strand 分配稳定 emitter ID。分叉是多条束，不能把不相连的点列表拼成一条；主束和分叉 `widthScale` 需分别应用到 recipe 宽度。示例游戏已这样渲染第三槽；正式接入需由调用方选择端点和碰撞终止。

Effects.world 接收世界身份并在改变时清空；示例还在 ClientLevel 对象更换时 clear，防止同维度重连残留。正式项目在 tick/卸载入口做同样的管理。所有调用在客户端渲染/主线程；网络事件先 enqueue 到客户端线程，不让 map 并发修改。

- **普通手持模型**：目标物品的动作变换、左右手、实体变换都要包含进挂点。不能只追踪玩家坐标。
- **骨骼武器**：读取实际骨骼 pose/socket，再统一到世界空间。GeckoLib/自定义动画库需单独 adapter，模板未包含它们。
- **第一人称**：既可在正确 hand pass 的局部空间绘制，也可把可识别的完整变换还原到统一空间；注意手部投影、FOV、摆动、缩放，不能把 GUI/第三人称认证外推。
- **GUI 旋转展示**：示例路径无需真实历史，可以用固定时刻重建；它只验证材质/几何。物品栏无限多格不得每格创建无界 emitter。

当前 History 通过线性插值细分两次已知姿态之间的段，**不恢复采样间丢失的曲率**。高速旋转要提高模拟采样频率或从动画函数重采样。同一个时间戳多次 add 被忽略，adapter 要一次给齐两个挂点。

动画库 adapter 可定义 `owner + hand + action` 作为稳定键，在动作允许的发射时间窗内求前/中/当前姿态对应的两个 socket。把姿态统一为世界坐标、按递增模拟时间送入 `Effects.sample`；关节脱离、跨维度或动作取消时停止并清理。不要把 Epic Fight 1.20.1 源码直接复制到 MIT 模板；这里只借用其架构判断，具体 API 还要按目标版本对齐。

## 光束命中与服务端

绘制深度只处理看不看得见，不会截断世界路径，也不提供游戏伤害。需要束停在墙上时按玩法做 block/entity raycast，服务端决定攻击结果，将必要端点、seed、开始时间传到客户端；不能信任客户端光束命中包自动造成伤害。同步数据量按实例变化控制，不逐顶点每帧广播。

## 材质组合

默认 alpha 为 straight-alpha，additive 使用 SRC_ALPHA/ONE；均 LEQUAL、COLOR_WRITE、NO_CULL。shader 输出不做 bloom。软边依赖 across=V 坐标，tube 可用 solid 或自定义 wrap 周向材质。纹理 U 在 shader 中 fract，图案接缝应自带周期性；mipmap/filter 需求另设采样状态。

**混合必须同时检查 RenderType 和 shader JSON。** 1.20.1 的 ShaderInstance.apply 会应用自身 BlendMode；仅设置 RenderType 透明状态，仍可能被 shader 的默认混合覆盖。本模板分别注册 `beam`（alpha）和 `beam_add`（additive），共用 GLSL；JSON 因子使用 `srcalpha`、`1-srcalpha`、`1`，新增材质时保持两侧一致。早期实机出现过“GL error=0，但柔边不透明”，因此验收必须看实际边缘和叠加区。

透明层在 emitter 级做远到近近似排序，不能解决所有互相穿插的半透明网格。每层 RenderType 支持透明 quad 排序，但多层/跨 emitter 的精确全局排序未认证。多物体/视线端对束/急转弯须看图。

客户端演示 `/trailbeam world` 在当前视角前方固定位置放解析轨迹，不改世界。它不是玩家手持武器的挂点验证。`/trailbeam off` 停止演示并清除 Effects；`/trailbeam showcase` 打开材质展示。
