# 安装与运行

## 工作台

完整 clone 或解压完整源码 ZIP，保持 `rendering/` 与 `skills/` 同级。Python 3.10+，运行 `python rendering/启动全部工作台.py`。输入启动器显示的编号，可以连续打开多个入口。不要只下载单个 HTML：它可能需要相邻 JS、GLSL、JSON、模型或本地服务接口。

运行 `python rendering/启动全部工作台.py --check` 可复查所有入口的 HTTP 响应。运行 `python scripts/check_rendering_release.py` 检查 Skill 元数据、入口和 HTML 静态资源。该检查不证明 shader 视觉质量或游戏兼容。

主预览需要支持 WebGL2 的现代浏览器和 GPU；GLSL 编译错误以工作台实际提示为准。研究索引可阅读资料，不需要游戏。Halo 素材生成器需要 Pillow；部分验证脚本需要 Node.js 与 Playwright，普通预览不依赖这些测试工具。

## 安装 Skill

从 `skills/` 选择需要的目录，复制到 `$CODEX_HOME/skills` 或默认的 `~/.codex/skills`。保留完整 assets/references/scripts。已有同名 Skill 时先备份，阅读该 Skill 的 `SKILL.md`。不需要安装全部 Skills 才能使用某一个工作台。

## Forge 工程

主要目标 Minecraft 1.20.1 / Forge / Java 17。每个示例的 Forge/Gradle 版本以实际 build.gradle 和验证文件为准。优先用 Skill 提供的 scaffold 脚本生成独立工程，再运行 `gradlew.bat build`。依赖首次下载需要联网；生产安装前核对资源与 JAR 版本。服务端同步、伤害、任意骨骼装备和外部光影兼容没有通用保证。

`rendering/alchemy-vfx-lab/forge` 为五行仙途宿主的专用集成实验。QA host JAR 未分发；重跑 QA 需自行提供有授权的宿主构建。模型快照和审计按原记录保留，不能把实验适配描述成任意炼丹炉通用运行库。

## 本机可选配置

异火工作台默认只提供编辑预览，未配置游戏目录时不会连接开发者的游戏实例。连接自己安装对应运行库的实例：

```powershell
$env:MINECRAFT_GAME_DIR = 'D:\你的实例目录'
python .\rendering\启动全部工作台.py
```

或单独运行 `python rendering/fire-coil-lab/serve.py --game-dir <含 mods 的实例目录>`。页面中的历史 ASTRA-TEST 文字来自原试验，实际写入目标由你显式指定的目录决定。未安装正确版本运行库时应用接口拒绝写入。

能量研究的参考视频不分发。需要播放自己有权使用的本机视频时设置 `VFX_REFERENCE_VIDEO`；未配置时该页显示参考视频不可用，其他研究功能可用。

御体/彩色流光工作台不分发 Minecraft 原版护甲纹理；缺少纹理时使用示意底材。需要真实护甲纹理，可按 `minecraft-colored-glint/scripts/extract_armor_textures.py` 的说明从自己安装的合法 client.jar 提取。

## 研究资料与历史路径

原研究报告保留固定提交、哈希和当时本机路径，这些路径不是新用户安装配置。下载的第三方整库、商业参考视频和旧实验包未混入本次源码；外部项目从目录记录的官方/上游链接访问。对未运行上游工程的资料，仍标记为静态研究。

## 独立 Skill 的工作台（2026-10-21）

单独安装以下 Skill 时，工作台也在其目录内，无须另拷 `rendering/`：

| Skill | 随包入口 | 范围 |
| --- | --- | --- |
| minecraft-space-rift | `assets/space-rift-kit/workbench/index.html` | WebGL2 裂缝、14 预设、JSON 导入导出 |
| minecraft-dynamic-lighting | `assets/workbench/index.html` | 独立照明示意预览 |
| minecraft-selective-bloom | `assets/workbench/index.html` | 独立 Bloom 示意预览 |
| minecraft-procedural-particles | `assets/workbench/index.html` | 粒子预览 |
| minecraft-animated-material-adapter | `assets/workbench/index.html` | 动画挂点示意预览 |
| minecraft-local-environment-material | `assets/workbench/index.html` | 表面线稿/渐变预览；不模拟世界空间区域 |
| minecraft-lightning | `assets/workbench/fbm-study.html` | 补齐 fBm 研究页，正式三维工作台仍为 index.html |

裂缝运行 `python assets/space-rift-kit/workbench/serve.py`；其他页面可在 Skill 目录运行 `python -m http.server 8765 --bind 127.0.0.1`，按表中相对路径访问。统一启动器也已接入。

Halo 是透明素材/动画生成器，物品场景窗口是 Forge 三维离屏工程；当前没有可补传的浏览器工作台。AXO、Photon 自动化、性能诊断类 Skill 按各自命令与运行流程使用。不能用普通示意页替代这些 Skill 的实际功能。
