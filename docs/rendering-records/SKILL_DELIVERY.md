# 星空渲染 Skill 交付

已创建 `minecraft-cosmic-rendering`，并安装到当前用户技能目录：

- [已安装的 SKILL.md](C:/Users/MAINTAINER/.codex/skills/minecraft-cosmic-rendering/SKILL.md)
- [项目内维护副本](E:/着色器渲染/skill/minecraft-cosmic-rendering/SKILL.md)

调用示例：

```text
使用 $minecraft-cosmic-rendering，检查当前模组星空随相机移动而漂移的原因，并验证修复。
```

也可用于研究现有 shader、制作银河背景、核对 Java/JSON/GLSL 接口、建立有限体积深度契约。技能限定已实测 API 为 Forge 1.20.1，其余版本要求在目标工程重新核对，不承诺通用光影兼容。

## 内容

入口说明按任务路由到五份参考：坐标与深度、算法与预算、Forge 接入、诊断与验收、参考案例与证据。附带独立实验模板和一个只读结果检查脚本。没有打包 ArcaneVortex 的代码、贴图或 JAR，也没有包含原机器的游戏存档、构建缓存、个人路径和专用启动脚本。

采用简短入口与按需加载参考、脚本和模板的结构，遵循 [OpenAI 官方技能结构说明](https://developers.openai.com/plugins/build/skills)。保留默认自动选择策略，同时提供显式 `$minecraft-cosmic-rendering` 调用提示。没有增加 MCP 依赖或创建额外插件。

## 已验证

- `quick_validate.py`：项目副本与安装副本通过 frontmatter、命名与未完成占位符检查。
- UI YAML：短说明长度、默认调用提示和自动选择设置通过检查。
- 全部 Markdown 资源链接可定位；未发现个人绝对路径或未完成占位符。
- 安装副本与项目副本逐文件 SHA-256 一致，记录在 [installed-manifest.json](E:/着色器渲染/skill-validation/installed-manifest.json)。
- 模板源码与第二阶段实测代码逐文件一致；复制到独立目录后 `gradlew build` 与重混淆通过，保留两个已知的 ResourceLocation 构造函数弃用警告。
- 结果检查器接受真实的 15 用例记录；14 项脚本测试通过，包含错误参考、漏用例、重复用例、假通过、错误像素、NaN、重载实例未变、平移漂移、GL 错误、遮挡颜色未变、截图缺失和非法 JSON 等情形。测试源码：[test_checker.py](E:/着色器渲染/skill-validation/test_checker.py)。

这里的“14 项测试”是检查器的自动化测试，不是 14 次新 GPU 实验。GPU 证据仍来自 [第二阶段实测](E:/着色器渲染/STARFIELD_RUNTIME_VALIDATION.md)，本次没有重跑相同 GPU 场景。技能任务路由已经人工检查，尚未做独立模型的行为评测；本地安装文件已核验，未声称桌面界面已经刷新发现结果。

## 维护原则

以项目内 `skill/minecraft-cosmic-rendering` 为维护副本，修改后先验证，再同步本地安装目录。不要直接在安装技能的 assets 中构建或创建游戏存档。新增加载器、体积模板或光影兼容结论时，应添加对应版本的实际证据，不从本次普通 Forge 天空实验推断。
