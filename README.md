# Minecraft Agent Skills

面向 Codex 的 Minecraft skills，包含：

- **axolotl-launcher**：发现并启动 AXO / Axolotl Launcher 实例；可按明确指定的存档尝试 Forge quick-play。
- **minecraft-vfx-automation**：通过 MCP-HELPER 与兼容 Photon 的构建创建、检查和验证 VFX。

## 与 MCP-HELPER 的关系

VFX skill 把 MCP-HELPER 当作 Minecraft 客户端的 HTTP 控制接口。MCP-HELPER 当前公开版本提供基础 HTTP/SSE 功能；Photon 专用方法属于可选扩展，需要确认实际安装的构建支持对应操作。两个项目分别维护：

- [MCP-HELPER 模组](https://github.com/Roxio-sketch/MCP-HELPER)
- [本 skills 仓库](https://github.com/Roxio-sketch/minecraft-agent-skills)

## 安装

将 `skills` 下所需的 skill 文件夹复制到 Codex skills 目录：

```powershell
$skillsDir = if ($env:CODEX_HOME) { Join-Path $env:CODEX_HOME 'skills' } else { Join-Path $env:USERPROFILE '.codex\skills' }
Copy-Item .\skills\axolotl-launcher $skillsDir -Recurse
Copy-Item .\skills\minecraft-vfx-automation $skillsDir -Recurse
```

AXO skill 支持在其 `references/local-paths.json` 中填写本机启动器与实例库路径。仓库只提供空模板，不包含个人机器路径。VFX 脚本需要 Windows PowerShell 7；图像工作流另外需要 `scripts/requirements-photon.txt` 中列出的 Python 包。

## 仓库结构

```text
skills/
  axolotl-launcher/
  minecraft-vfx-automation/
```

各 skill 的具体约束、工作流和兼容性说明见对应的 `SKILL.md`。

## License

MIT
