# Forge 1.20.1 / v0.2.3 接入

Java 17，模板目标 Forge 47.4.10。Mod ID `energyvfx`，package `dev.energyvfx`；生产文件 `energyvfx-0.2.4.jar`。这是客户端运行代码，不向服务器注册内容。实际通过范围见 validation.md。

## 配方与渲染

配方优先级：`<实际游戏目录>/config/energyvfx/recipe.json` > `assets/energyvfx/recipe.json`。v2 严格读取全部 48 字段；v1 仅在精确包含原 39 字段且全部有效时补默认值升级。非法文件保留最后有效对象。`reload` 重新读取数值并重置发射时钟；F3+T 释放 GPU 资源后重新读取配方，下一帧重新编译 GLSL。

网页 schema.js 和 recipe-schema.json 定义唯一字段顺序。field、scene、volume、blur、composite、noise 的文件内容与 Forge 资源相同，宿主提供 GLSL 版本与输出绑定；aura.js 是网页世界空间气团预览，AuraEmitter 是实际游戏挂点与气团模拟。网页人物是观察代理模型，不能据此宣称任意装备几何完全相同。

原生 `AFTER_PARTICLES`：复制主颜色与深度 → 低分辨率 RGBA16F MRT（体积光/透射率与 emission）→ 横、纵分离模糊 → 主 target 全分辨率合成。深度经逆 view-projection 重建后截断积分；相机进入体积从射线 t=0 起步。主深度只读，采样输入与合成输出分离。尺寸改变、重载、退出世界均释放对应资源，异常停止后恢复 GL 状态。

0.2.2 在体积积分前使用 OpenGL 4.3 compute 缓存六个 RK2 时刻的局部涡旋系数，RGBA32F 格点直接读取，原解析 curl、密度与薄层积分保留；超出格点范围或无 OpenGL 4.3 时走解析回退。flow-cache.glsl 是第七份原生 shader，六份网页共用 shader 保持同步。缓存不能当作流体模拟或屏幕模糊。

0.2.3 的 PacketBounds 按各包流向椭球的支撑范围取保守 AABB，投影八个角点与跨越透视 w=0 的边，再取可见包区域的并集。完全在相机后方的包提供空区域；退化或非有限投影回退全屏。片元先检查各包的屏幕范围，再执行原支撑判断与密度积分，防止已经离体并落到相机后方的拖烟把整个 pass 扩大到全屏。边界向外保留像素余量；Bloom 的合成与中间模糊区域同时计入核半径和按主 target / 体积 target 比例缩放的线性采样边距。此路径不改变采样、分辨率、模拟数量或内部运动；同帧质量测试独立于下面的近距可见性策略。

空间效果沿用 `EnergyVfx.Client.startAt(Vec3)`。不要每帧调用该方法，否则会重复读取配方并重置时间。持续玩家幽气选择 attachment=1/2/3，由 AuraEmitter.bind 绑定实际玩家；它在出生时采样表面，出生后保留世界位置，并根据 outflow、rise、swirl 与 inertia 继续运动。

## 真实表面与预算

无绘制 player layer 记录人物的真实渲染矩阵与动画 ModelPart 方块表面。普通 ArmorItem 采样对应动作中的内外盔甲模型；普通持有物品采样 baked quad，保留手臂、第三人称物品显示变换及左右手关系。持有钻石剑和普通钻石/铁盔甲属于独立 QA 的实际目标。

非标准盔甲、非 ArmorItem 穿戴栈，以及 BEWLR/GeckoLib 动态模型不能自动解析其全部几何。fallback 从实际装备挂点附近发射，并记录 fallbackItems；这些目标需要在原模型渲染入口提供表面数据。不要把 fallback 计数为零之外的未知情况宣称为完整几何适配。

全局最多 32 个三维密度包，约 puffRate × puffLife 同时存活；过量出生会受容量限制。源表面面积参与采样，bodyWeight 与 gearWeight 控制两类强度。firstPerson 默认低自身强度；第一人称不绘制自身完整第三人称模型，其发射采用动作与挂点 fallback，状态明确记录 renderedPose=false。

本地玩家第一人称额外启用固定出生选择与近眼平滑淡出，减少身体/盔甲遮屏，优先保留持有装备。0.2.3 将相同策略扩展至第三人称相机被墙体等环境推近玩家时：以相机到玩家眼睛的世界距离计算权重，≥3 米时权重为 0、保留完整烟量，≤1.8 米时权重为 1、使用出生时固定的子集和 nearFade，中间 smoothstep 连续过渡。中间权重对其余包减弱强度，权重达到 1 后跳过其上传；子集不随帧重新抽样。全部包继续物理模拟，uploadedPackets 与 packets 不必相同。该策略改变自身近距可见烟量，不能把含此策略的性能收益说成同画质的缓存或裁剪加速。0.2.3 的移动验证见 motion-performance.md；fullscreen-performance.md 保留 0.2.2 的第一人称历史对照。

多角色实例、服务器技能同步、伤害与命中判定由宿主项目实现。新实例应统一 emission/Bloom 与预算，避免每个角色重复整屏合成。当前气团不碰撞、不投射场景光，薄纱是梯度归一化等值面形成的体积密度，不是网格流体求解或独立布料。

## 固定文件命令桥

桥只读取固定 `config/energyvfx/command.json`，写固定 `result.json` 与 `status.json`，每次用新的 nonce。命令不接受路径、任意服务器命令、GUI 控制、相机或物品栏操作。保持 MCP-HELPER `control_mode=false`，无需切换控制权。

```json
{"nonce":"生成一个新的UUIDhex","action":"reload"}
```

允许 action 为 reload、start、off、status、preset。除 preset 外请求必须精确含 nonce/action；preset 还必须有 JSON 整数 `preset: 1..20`。例如 `{"nonce":"另一新的UUIDhex","action":"preset","preset":16}` 切换冰蓝卷烟。preset 只切换运行中配方，不覆盖保存文件；后续 reload/start 会读取固定 recipe.json。reload 保留当前 active 状态，off 后用 start 恢复。

桥响应为 `{"nonce":"同一值","ok":true,"status":{...}}`；拒绝时 ok=false 并提供 error。status 包括 runtime、active、draws、glError、failed、packets、surfaceTriangles、equippedSlots、renderedPose、bakedItems、fallbackItems、camera、paused、worldLoaded 和当前配方。不能把旧 nonce 的成功结果当成本次执行完成。

0.2.2 还提供 framebufferWidth/Height、uploadedPackets、firstPersonThinned、nearEyeHidden/Faded、flowCacheActive/Supported，分别记录实际主 target 大小、绘制数、第一人称筛选与缓存状态。这些字段不是 GPU 时间或 FPS。

0.2.3 增加 nearViewThinned、nearViewWeight、cameraDistanceMeters 与 packetBoundsEnabled。nearViewThinned 是当帧由自身近距固定子集筛掉的包数，第一人称时也计入 firstPersonThinned；nearViewWeight 是实际应用的近距策略权重（0..1）；cameraDistanceMeters 是相机到本地玩家眼睛的距离，仅有限时写出；packetBoundsEnabled 表示逐包屏幕裁剪路径是否启用。淡出与筛选只改变绘制，packets 仍记录模拟中的数量。诊断时同时读取相机模式、实际帧缓冲、上传数及近距权重，不能根据 packetBoundsEnabled 或 flowCacheActive 推算 GPU 时间。

脚本用法：

```text
python -X utf8 scripts/apply_recipe.py --recipe <已保存配方> --game-dir <实际游戏目录> --reload
python -X utf8 scripts/apply_recipe.py --game-dir <实际游戏目录> --action status
python -X utf8 scripts/apply_recipe.py --game-dir <实际游戏目录> --action preset --preset 16
```

首次加 `--install-runtime`，安装或替换 JAR 后重启；仅更新运行库可不传 --recipe，保留当前参数；仅改参数时使用 --reload。脚本备份配方和替换前 JAR，并把已知 v0.1.0/v0.2.0/v0.2.1/v0.2.2 移出 mods 至固定备份目录。其他未知 EnergyVFX JAR 拒绝迁移以防重复 mod ID。等待超时仍保留已保存配方，但不得声称游戏已经应用。

Fabulous 或主 target 被替换时拒绝。Oculus/Iris、Shimmer、Veil、Photon 共存及透明无深度写入对象需要在实际组合验证；独立 dev client 通过不代表用户实例的所有管线通过。


### v0.2.4 camera-parcel fade

第三人称背视角的近镜头保护按每个本地玩家离体气团的实际流向椭球距离计算，不再只看相机到玩家眼睛的距离。body 在 8–10 个包半径内连续淡出，gear 在 6–8 个包半径内连续淡出，并与原近眼预算平滑衔接；远离相机的烟包不受影响。所有包仍参与 20 Hz 模拟，resolution、steps、寿命、容量与积分采样保持不变。由于镜头很近时可见烟量会减少，不能把该路径标为同画质优化。
