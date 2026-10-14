当前生产版本0.2.0，最新四场景证据在evidence/game-extension-v2，核心SHA与配方按evidence/build-verification.json。原946中心火床保留，新增四窗气流/火星/药香、炉内素材光团与三维环流；不改宿主炼丹规则和材料ItemRenderer。

# 炼丹炉体积运行库

Minecraft 1.20.1 / Forge 47.4.10 / Java 17，独立 modid `alchemyvfx`。

本库只读取五行仙途已有的 `AlchemyFurnaceBlockEntity`；不注册炉体、配方、炼丹任务、背包或网络包，不打包宿主 `wuxingxiantu` 类和资产。六品级共用宿主 5×5×4 炉体尺寸。宿主物品、炉体模型、投料 Offering 和炼丹规则继续由宿主负责。

`HostAdapter` 用公开方法读取 `brewing / startTick / finishTick / hasResult / completionAge / activeFireId`，通过 `AlchemyFurnaceBlock.isFormed` 门控。核心锚点为 `(core.x+.5, core.y, core.z+.5)`，朝向采用 `AlchemyFurnaceStructure.inwardOf` 与 `AlchemyFurnaceVisualLayout.rotation`。只扫描已加载客户端区块，不触发加载请求。

客户端默认启用；仅在宿主安装 [接管补丁](integration/host-volume-hook.patch) 后绘制。补丁提供 `externalVolumeHookVersion()==1`，并在旧 `drawFurnace` 入口询问新库 `HostHook.claims`。只有当该炉本帧成功绘制新体积时才抑制旧特效；库缺席、关闭、配方未加载或渲染故障时，旧效果回退。宿主 Offering 效果与材料/成品 ItemRenderer 不在接管范围内。

当前适配针对原版 Fancy/Fast 主渲染目标。Fabulous 或替换主 FBO 的 shader pack 会显式失败并回退宿主效果，不能将未验证路径当作兼容。

## 配置与固定桥

首次载入创建 `config/alchemyvfx/recipe.json`。配方必须完整匹配 `alchemy-vfx/2`（52字段）；完整v1可严格升级；非法配方保留最近合法版本。

游戏客户端命令：`/alchemyvfx start`、`off`、`reload`、`status`。这些动作只控制本库，不启动或取消炼丹。

固定路径 `config/alchemyvfx/command.json`：

```json
{"action":"reload","nonce":"author-001"}
```

返回固定路径 `result.json`，包含 `ok`、`nonce`、`status` 与错误（如有）。不允许任意文件路径、相机指令、世界编辑或宿主状态写入。状态含配方、SHA-256、真实炉状态、接管钩子、GL 错误、GPU 世代及画质精度。

## 构建

```powershell
$env:JAVA_HOME = 'C:/tmp/temurin17/jdk-17.0.20.1+1'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
# 本机已有的 Windows Pipe/Selector 修复；只对本次构建进程应用。
$env:JAVA_TOOL_OPTIONS = '--patch-module=java.base=C:/tmp/jdkpatch'
.\gradlew.bat build --offline
```

`processResources` 从上层 `current-recipe.json` 与 `alchemy.volume.core.glsl` 同步资源，并记录共享 core SHA-256。体积积分保留全分辨率；默认 400 步，最高 512 步，不自动降低画质或引入 LOD。仅复制单独深度纹理，用真实三维局部坐标积分；不读取场景颜色，深度截断防止透墙，并恢复所改 GL 状态。

## 隔离 QA

```powershell
.\gradlew.bat patchQaHost --offline
.\gradlew.bat runClient --offline -Pqa=true -PqaHost=true -PqaOutput=evidence/game
```

`patchQaHost` 只读默认 `E:/五行仙途/build/libs/wuxingxiantu-0.2.jar`，在本项目 `qa-libs/` 生成带同等钩子的 QA JAR 并记录原始 SHA-256。不会写宿主源码或原始 JAR。`-PhostJar=绝对路径` 可显式指定另一个输入。QA 依赖使用 ForgeGradle 反混淆，宿主本身提供模型与资产；`qaHost` 与 `qaClient` 源集永不进入发布 JAR。

测试窗口隐藏，仅新建 `forge/run/saves/ALCHEMY_VFX_QA_<时间戳>`。测试夹具在这个新世界生成真实宿主结构与状态，并创建独立未来任务以保持炼制状态；相机、世界暂停和方块变化只在 QA 源集执行。不会打开或改变用户存档、相机或启动器实例。

测试覆盖预热、炼化、凝丹、成丹、侧视、深度遮挡、拆炉门控、资源重载及关闭回退；截图与状态写入 `evidence/game`。测试等待服务器更新确实到达客户端，校验准确的起始 tick、种子、炼制/结果状态后才暂停，不把服务器任务完成当作客户端同步完成。生产配方与任何 QA 状态写入是不同路径。

以下为旧c7bad的历史接入证据，不作为当前美术验收：2026-10-13 已通过 1280×720 / 320 步 / RTX 4060 Ti 的十场景实机验收，见 [完整状态记录](evidence/game-bottom320-sync/game-results.json)。有效特效场景均 1 个真实体积 draw、1 个宿主接管；拆炉与关闭均 0 个新体积 draw。资源重载使 GPU 世代 1→2，全部场景 GL 错误 0。共享 core SHA-256 为 `c7bad179ad42aec39258390677d924f930b364d522c05e633e777b827797d878`。

火床专项 QA 用 `-PqaSuite=firebed`：拍摄同一近距机位关闭/开启新库的对照、从真实炉底空隙观察内部的低位镜头及铁墙遮挡。模型与场景深度保持完整。另用 QA 专属 RGBA32F 1×1 FBO 直接调用部署资源中的 `sampleAlchemy`，记录中心与周边 30 个三维位置的颜色/密度；此数据是场函数数值测量，不是场景截图，不据此声称被实体几何挡住的位置应透出。
