# 随附探针运行

路径相对 Skill 根目录。GPU/API 探针是研究基线；可安装 MOD 源码单独位于 `assets/forge-inkrealm-example`，运行方式见 [Ink Realm](runtime-inkrealm.md)。

## WebGL2 GPU

`assets/gpu-probe/index.html` 含独立主/子 synthetic scene、FBO/stencil 两后端、窗口形状与球体入口；`validate.cjs` 自动保存 JSON 和截图。

需要 Node、可用的 Playwright 包、支持 WebGL2 的 Chromium/Edge。可以使用机器上已有的 Node 依赖；无需把运行时绝对路径写死进脚本。运行示例：

```powershell
$env:SUBSCENE_BROWSER='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
# 如模块不在当前 Node 搜索路径，设置 NODE_PATH 到已安装依赖目录。
node assets/gpu-probe/validate.cjs path/to/new-evidence
```

无 SUBSCENE_BROWSER 时使用 Playwright 的默认 Chromium，需相应浏览器已安装。输出 JSON 记录实际 browser/renderer，失败返回非零退出码；软件 renderer 也可能通过原理测试，不代表硬件性能。每次改算法后用新输出目录，避免覆盖历史证据。

可直接浏览 HTML 查看画面。其 `subscene` API 服务于测试，不是 Forge RenderSystem API。球体图 `sphere-eye-3.png` 和 `sphere-eye-0.png` 区别外部入口与内部全视野策略。

## Forge API + 数学

`assets/forge-api-probe` 是 Java 17 / Forge 47.4.10 / FG 6.0.54 项目，内含 Gradle 8.8 wrapper。缓存齐全时：

```powershell
Set-Location assets/forge-api-probe
# JAVA_HOME 指向 Java 17。
.\gradlew.bat --offline --console plain compileJava contracts
```

首次获取依赖时移除 `--offline` 并允许正常 Gradle 下载。Linux/macOS 使用 `bash gradlew`。网络/缓存失败与 Java API 编译失败分开记录，不为凑通过静默改成另一个 Forge 版本。

Windows 某些执行上下文需要给 JVM 指定短的可写 Unix-domain socket 临时目录：`-Djdk.net.unixdomain.tmpdir=<任务专用目录>`。这是本机曾用过的环境规避，不是 Forge 渲染要求；不覆盖用户 JAVA_TOOL_OPTIONS 的其他必要配置。

`contracts` 输出 `CONTRACTS_PASSED=20` 才是此数学基线完成。`ForgeRouteProbe` 仅签名探针，没有 mods.toml，不部署其 JAR。要得到游戏证据，使用真正的模组；可从随附 Ink Realm 源码生成工程。

## 证据完整性

`scripts/verify_bundle.py <skill-directory>` 检查文档本地链接、固定研究/实测记录与关键文件的打包完整性。`scripts/verify_inkrealm_evidence.py` 使用 Pillow/numpy 独立核对已保存的山水截图、视差和状态记录，详见 [验收](validation.md)。两者都不重新运行 GPU、Gradle 或 Minecraft，也不自动认证修改后的场景。
