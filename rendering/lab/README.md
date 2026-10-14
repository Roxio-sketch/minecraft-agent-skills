# 星空渲染最小实验

这是研究报告下一阶段的独立 Forge 客户端实验：真实天空入口 → 方向颜色 → 程序化星点 → 方向域 FBM 星云 → 三档质量。它替换主世界天空，可用命令关闭。没有使用 ArcaneVortex 的贴图、代码或运行依赖。

锁定环境：Minecraft 1.20.1、Forge 47.4.22、ForgeGradle 6.0.54、Gradle 8.8、Java 17；GLSL 150。Windows 启动脚本见 `run-lab.ps1`。

## 启动和复现

在本目录 PowerShell 中执行，JavaHome 替换成自己的 Java 17 目录：

```powershell
.\run-lab.ps1 -JavaHome 'D:\MC\meta\java_versions\mojang-java-runtime-gamma-windows-x64-89ce85ccb518' -BuildOnly
.\run-lab.ps1 -JavaHome 'D:\MC\meta\java_versions\mojang-java-runtime-gamma-windows-x64-89ce85ccb518' -AutoValidate
```

不带 `-AutoValidate` 则进入普通开发客户端菜单。自动验证会创建新的 `run/saves/COSMIC_LAB_<时间戳>` 超平坦存档，建立金墙，设置视角与画质，运行 15 个用例。每次运行覆盖 `validation/` 同名结果，存档以新名字保存。验证期间避免操作游戏视角；完成后游戏保持打开。

脚本仅为当前进程设置 Java 和临时目录，结束后恢复环境变量。它按 Mojang 版本元数据下载并校验 Windows x64 的 LWJGL 3.3.1 原生库，放到项目 `build/windows-natives`，绕过这台机器共享 Gradle 缓存中的 Linux classifier；不修改共享缓存。Java 17 的临时 socket 使用独立目录，以绕过本机默认临时路径触发的 loopback 错误。

通用 Gradle 命令：`gradlew.bat build`，`gradlew.bat runClient -PlabAuto=true`。若同样遇到错误，使用上述 Windows 脚本。`-PlabNativeDir=<目录>` 是可选本机原生库覆盖目录。

输出 JAR：`build/libs/cosmic-rendering-lab-0.1.0.jar`。这是实验用客户端模组；构建产物已重混淆，实测使用 Gradle 开发客户端。未单独验证成品 JAR 在启动器中的安装运行。

## 游戏命令

| 命令 | 行为 |
|---|---|
| `/cosmiclab direction` | 世界方向 RGB，诊断坐标 |
| `/cosmiclab stars` | 仅星点 |
| `/cosmiclab nebula` | 星点与星云 |
| `/cosmiclab quality 1` / `2` / `3` | 低、中、高采样预算 |
| `/cosmiclab freeze` | 切换时间归零冻结/实时闪烁 |
| `/cosmiclab off` | 返回原版天空 |

注意 `freeze` 是用于复现的时间归零开关，切换瞬间会跳变，不是连续暂停时钟。星云本身保持静态，时间只影响星点亮度。

## 文件导航

- `src/main/java/dev/cosmiclab/LabSky.java`：注册 shader、主世界天空钩子、矩阵上传、绘制及状态恢复。
- `src/main/resources/assets/cosmiclab/shaders/core/sky.{json,vsh,fsh}`：三方数据接口及效果。
- `src/main/java/dev/cosmiclab/LabValidation.java`：可选实机验证器、截图、像素核对和 GPU timer。
- `validation/results.json`：每个用例的相机、矩阵、截图尺寸、重载代数、像素误差和计时。
- `validation/summary.json`：本次环境和用例统计。
- `validation/*-sky.png`：天空 pass 后截图；`*-world.png`：帧结束截图。
- `validation-initial/`：第一轮验证器坐标约定错误的原始数据，保留作审计；正式结论以 `validation/` 为准。
- `../STARFIELD_RUNTIME_VALIDATION.md`：实测结果、方法和适用边界。

## 适用范围

当前替换所有使用 `minecraft:overworld` effects 的维度天空；与其他覆盖同一入口的模组可能冲突。水下/岩浆和 foggy 状态让回原版入口。没有实现昼夜混合、天气、太阳月亮、自定义维度选择、物理体积星云或 Oculus/光影兼容。

CPU 每帧查询 GL 状态和构造矩阵，适合研究验证；生产版本应在清楚管线状态契约后减少开销。GPU timer 仅包围天空绘制，不代表整帧 FPS，也不包括截图读取、CPU 状态查询和地形成本。

自行实现的 `src/` 与实验脚本适用 `LICENSE` 中的 MIT 许可；随官方 MDK 保留的 `LICENSE.txt` 是 Forge 的许可说明，Gradle Wrapper 和外部依赖遵守各自许可。参考 MOD 的提取证据不属于本实验的可分发资源。
