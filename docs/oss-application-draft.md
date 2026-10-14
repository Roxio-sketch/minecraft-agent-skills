# Codex for Open Source 申请填写稿

核对日期：2026-10-14。主项目：https://github.com/Roxio-sketch/minecraft-agent-skills

## 基本字段

| 字段 | 填写内容 |
|---|---|
| 姓氏 / 名字 | 由申请人填写；官方未明确写必须是证件姓名，但条款要求信息准确完整并可能核验身份 |
| 电子邮箱 | 与 ChatGPT 账户关联的邮箱，待填写 |
| GitHub 用户名 | Roxio-sketch |
| GitHub 仓库 URL | https://github.com/Roxio-sketch/minecraft-agent-skills |
| 角色 | 主要维护者 |
| 支持意向 | 项目的 API 额度；Codex Security 仅在确有安全维护需求时选择 |
| OpenAI 组织 ID | 从 API 平台组织设置复制，待填写 |

## 为什么这个代码仓库符合要求？（248 字符）

我主要维护 Minecraft Agent Skills，目前有 2 个 GitHub Star。项目公开 23 个渲染与性能 Skill、32 个工作台/预览/研究入口，并保留启动器和 Photon 自动化工具。涵盖天空大气、体积云雾、物品材质、闪电、护盾、拖尾、粒子和局部空间，附 Forge 1.20.1 示例、来源许可与 51 组项目特效开发记录。生态价值在于把复杂的着色器和游戏验证经验转为其他开发者可复用的工具；我持续负责开发、文档、缺陷处理与发布。项目仍处于早期，尚无广泛采用证明。

## 如何使用 API 额度？（194 字符）

我计划将 API 额度用于开源维护：分类和复现用户报告，辅助审查 PR 中的 Java/GLSL、配方与资源一致性，检查渲染状态恢复和版本兼容，更新文档及发布说明，并建立固定种子、时间和相机的回归评估。自动化输出由我审核后合入，验证将区分静态、浏览器 GPU、独立 Forge 和实际用户实例。改进的脚本、测试和维护文档将公开回馈项目，现有工作台仍可在没有 API key 的情况下使用。

## 其他需要说明的事项（226 字符）

项目来自持续的 Minecraft 渲染开发和实机问题处理。我曾向 minecraft-mod-mcp 提交 Issue #36 和修复 PR #37，并进行游戏内验证。五行仙途炉底案例在 RTX 4060 Ti、1280×900 的特定近景测试中，将底火 GPU 绘制从约 83–86ms 降至 11–11.5ms；公开记录明确区分单 pass 与整游戏 FPS，并保留细节柔化和后续视觉问题。完整功能目录、来源许可、失败记录与维护计划均在仓库公开。

## 公开证据链接

- 完整功能、工具和记录目录：https://github.com/Roxio-sketch/minecraft-agent-skills/blob/main/docs/rendering-catalog.md
- 验证与限制：https://github.com/Roxio-sketch/minecraft-agent-skills/blob/main/docs/validation-and-evidence.md
- 维护和 API 计划：https://github.com/Roxio-sketch/minecraft-agent-skills/blob/main/docs/oss-maintainer-case.md
- 本次发布：https://github.com/Roxio-sketch/minecraft-agent-skills/releases/tag/rendering-suite-20261014
- 上游贡献：https://github.com/langyo/minecraft-mod-mcp/pull/37

## 填写前核对

Star 数以提交当天为准；没有可靠月下载量时不填写估算数字。23 为渲染/性能 Skill 数，另有 Photon 自动化与启动器两个 Skill；32 包括研究索引和基础验证预览，不应全部写成已经游戏实装的特效。51 为按原目录收录的报告组，不能描述为 51 个独立产品或全部成功实验。申请依据真实维护与可复用价值，入选由官方决定。

官方申请表：https://openai.com/zh-Hans-CN/form/codex-for-oss/
项目条款：https://learn.chatgpt.com/docs/codex-for-oss-terms
