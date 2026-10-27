---
name: minecraft-mcp-helper
description: "Use MCP-HELPER for Minecraft Forge 1.20.1 AI LAN participation, endpoint/debug discovery, building, structure selection and NBT libraries while preserving player controls. Applies to operating this mod, not general mod development."
---

# Minecraft MCP-HELPER

使用本模组进行 AI 入世、建筑、结构选择和运行诊断。规则基于 MCP-HELPER 1.0 源码；运行时先核对实际版本与能力。

## 连接与玩家保护

首先读 [连接与兼容](references/connection.md)。优先使用独立游戏目录、独立有效身份的 AI 客户端加入玩家开放的 LAN 世界。分别记录玩家主机和 AI 客户端的 HTTP 地址、PID、玩家身份、维度；游戏 LAN 端口与 HTTP 端口分开记录。

连接后先向用户报告实际可访问的 `/debug` 完整 URL，并注明是玩家主机还是 AI 客户端。没有验证的地址标为待验证，不能把默认 9876 当作实际端口。

玩家主机默认只读；用户授权的建筑可使用主机世界 API。键盘、鼠标、聊天输入、GUI 和相机操作应发送至独立 AI 客户端。共享鼠标模式不能保证不干涉玩家。保留玩家原有 control_mode 和界面状态。

无法启动独立客户端时，说明原因。已开放 LAN 的主机可用独立 FakePlayer helper 执行授权命令，但它不是 LAN 客户端，没有自己的客户端画面，也没有寻路。不能把 helper 的出现当作独立客户端已连接。

## 建筑与结构

建筑、扫描、批量放置、模板导入导出和回滚读 [建筑与结构库](references/building.md)。选区工具、玩家选区身份和预览读 [结构选择器](references/selector.md)。不要将普通 WorldEdit 的两点选择习惯套用到本模组。

先确定目标维度、边界、锚点、方块状态及实体策略。对已有建筑的替换先保存完整备份。LAN 客户端的本地 HTTP 服务不具备主机集成服务器的批量写入能力；此类操作明确路由到已核实的主机地址。

## API 操作

模组提供 HTTP/SSE；MCP stdio 桥接是另一层。桥接缺少某个工具时，核对实际 Java HTTP 分发器，可直接调用受支持的 HTTP 命令。多客户端运行时禁止依赖桥接器找到的第一个端口。

POST `/api/cmd` 使用 `{"cmd":"命令名","params":{}}`。每次变更前确认端点身份仍一致。HTTP 200 不等于成功，检查 JSON 的 error、ok 和任务状态。设置有限超时；变更超时先查询结果，不盲目重发。只完成用户请求的世界变更，不为验证 skill 自动改动存档。

输出结果包括实际连接/调试地址、所用角色、建筑或选区边界、完成情况及剩余限制。不要声称已经执行尚未执行的入世、放置或运行验证。
