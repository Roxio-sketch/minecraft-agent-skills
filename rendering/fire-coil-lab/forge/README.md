# Fire Coil / Forge 1.20.1

本项目是本地玩家异火缠绕的独立客户端后端。`firecoil` 命名空间不覆盖 `energyvfx`。当前构建与数值检查通过只证明技术基础；外观由上级工作台另行验收。

主体与离体火舌使用真实三维密度场，在身体局部坐标内逐射线积分，默认 320 步，可调 24..512 步；只在小余烬上使用点状精灵。离体模拟固定 20 Hz，暂停不推进，切换世界与超过 8 米传送会清空旧粒子。正常运行没有相机或世界修改。

模拟池最多 512 个离体粒子，但当前三维火舌渲染 ABI 最多 24 个，默认约 11 个火舌加 23 个余烬。状态分别报告 `particles`、`visibleVolumeWisps` 和 `volumeWispLimit`，不把池容量当作渲染数量。第一人称同时降低主体、现存火舌强度和出生强度。

严格消费 `fire-coil/1` 的 31 字段；缺失、未知、类型错误和越界参数被拒绝，保留上次有效配方。`segments` 在体积版中直接控制射线积分步数。`processResources` 前会从上一级同步 `current-recipe.json`、`fire.volume.core.glsl` 和余烬材质 `fire.frag.core.glsl`；两个核心都原样包入 wrapper 并附 SHA。

深度通过独立 depth-only FBO 拷贝，体积 pass 读取这个单独纹理，直通 alpha 合成到主目标并关闭深度写入；不读取正在写入的附件，不拷贝场景颜色，不做 Bloom。全分辨率渲染，仅使用保守屏幕范围裁剪；近裁面交叉时回退全屏范围，不削减质量。Fabulous 模式和替换主目标的外部 Shader 管线需要单独适配，当前会报告具体错误。

## 构建

```powershell
$env:JAVA_HOME = 'C:\tmp\temurin17\jdk-17.0.20.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\tmp -Dfile.encoding=UTF-8'
.\gradlew.bat build --offline --console=plain
```

Windows JDK 17 的 NIO Unix socket 在含中文路径时需要上面的 ASCII 临时目录参数；`JAVA_TOOL_OPTIONS` 也将参数传入 Gradle 的子进程。

生产 JAR：`build/libs/firecoil-0.1.0.jar`。数值证据：`evidence/core-check.json`。纯数值入口：`gradlew.bat coreCheck`。

## 游戏配置和命令

游戏目录内使用固定文件：

- `config/firecoil/recipe.json`：配方；首次加载缺失时写入内置默认配方。
- `config/firecoil/command.json`：例如 `{"action":"reload","nonce":"edit_001"}`。
- `config/firecoil/result.json`：回应原 nonce、成功/错误及状态。相同 nonce 不重复执行。

支持 `start`、`off`、`reload`、`status`，也可在客户端聊天栏执行 `/firecoil start` 等相同动作。`off` 让主体淡出并等待离体粒子自然过期。命令不支持任意文件路径、相机、物品栏或服务器命令。

## 独立游戏 QA（显式开启）

```powershell
.\gradlew.bat runClient --offline --console=plain -Pqa=true -PqaWidth=960 -PqaHeight=540
```

可按需要增加 `-PnativeDir=C:\实际的\natives`。必须使用本项目的 `forge/run`；不要把工作目录改成玩家实例。测试窗口主动隐藏，自动新建 `FIRE_COIL_QA_<时间戳>` 平坦世界。只在这个新世界内移动玩家、改变 QA 相机与放置背景墙。

测试场景：关闭基线、琥珀缠绕、默认蓝白缠绕、1 米近距、密度域内、运动、铁块遮挡、资源重载、第一人称、关闭并过期。普通对照和遮挡使用固定 4 米 QA 相机避免碰撞干扰，近距和域内也只修改 QA 相机；第一人称使用原版相机。输出 `evidence/game/*.png` 和 `game-results.json`；失败输出 `failure.txt`，可用 `-PqaOutput=evidence/自定义目录` 保留多次记录。未启用 `-Pqa=true` 时该流程不会执行。

不把这些检查作为全屏性能或最终外观通过的证明；本轮用户选择质量优先，尚未做性能验收。
