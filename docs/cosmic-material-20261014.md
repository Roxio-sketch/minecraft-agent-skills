# Cosmic 材质 Skill 与盾牌工作台 · 2026-10-14

发布“星空材质渲染”聊天中已打包的交付，共 59 个 Skill 文件；ZIP 与原交付的 SHA-256 一致。

- `skills/minecraft-cosmic-material`：物品内部星空、奇幻材质、模型接入、穿戴防具与盾牌的 Forge 1.20.1 研究和验证方法；包含星空、奇幻材质、描边和盾牌工作台。
- `workbenches/cosmic-shield-workbench/index.html`：无需安装依赖，直接用浏览器打开；四种盾牌覆盖预设、三套配色及 `/give` 指令复制。
- 完整 Skill 安装：把 ZIP 中的 `minecraft-cosmic-material` 文件夹复制到 Codex skills 目录。星空/奇幻材质预览可用 Python 运行 `scripts/serve_authoring.py --lab fantasy`，按终端输出打开实际端口。

盾牌 Canvas 是设计预览；指令需要提供对应实现的 Cosmic Material Lab，不能仅安装 Skill 就改变原版游戏渲染。Forge 实测范围与第三方模型适配边界见 Skill 的 references 文档。

发布的是 Skill 和工作台，未附实验模组 JAR、第三方研究源码或原始素材。部分研究文档保留本机证据路径，用于说明开发来源；这些外部实验工程未随包提供。原创资源沿用仓库 MIT 许可，随包 Lucide 保留其版权通知；研究对象的许可仍归其各自权利人。

本次校验 ZIP 完整性、交付哈希和 Skill 格式；未重新进行游戏运行验收。
