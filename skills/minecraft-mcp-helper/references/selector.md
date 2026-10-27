# 结构选择器

## 工具行为

通过 `/give <目标玩家> mcpmod:structure_selector` 提供工具；只有确认执行者时才用 @s。拿在手中：

- 对方块右键：将该坐标纳入当前最小包围盒，每次有效点击扩大边界。
- Shift+右键：撤销最后一次有效扩展；对空气使用也能撤销。
- 左键会取消挖掘，不是设置 Pos1。
- 普通右键空气不增加坐标。
- V 切换线框/填充预览；可能与其他模组按键冲突。

选择两端极值坐标即可包含中间体积，不必逐个点击方块。预览表示包围盒，不代表保存的 NBT 已验证。选区按玩家 UUID 保存，并区分维度。切换执行者/维度后先读 info。

## 命令

```mcfunction
/mcp selection info
/mcp selection clear
/mcp selection undo
/mcp selection preview
/mcp selection preview wireframe
/mcp selection preview filled
/mcp selection add
/mcp selection add <x> <y> <z>
/mcp selection add ahead <distance>
/mcp selection save <name>
/mcp selection save <name> entities
/mcp selection save <name> prefab
/mcp selection save <name> prefab entities
/mcp structure list
/mcp structure folder structures
/mcp structure folder prefabs
/mcp structure folder generated
/mcp structure place <id>
/mcp structure place <id> ahead <distance>
```

## AI 与玩家选区的映射

AI 通过自己的客户端操作工具，或以正确身份执行 selection add；不要为取得玩家选区抢占玩家键鼠。读取该身份的 selection info，记录维度与 min/max，并核对目标建筑完整边界。

HTTP save_structure 的 use_selection=true 读取的是该 HTTP 客户端本地玩家的 UUID，不能指定另一个玩家。主机 use_selection 不会读 LAN AI 客户端的选区；显式 pos1+pos2 会优先于 use_selection。

因此 LAN AI 的选区要保存到主机结构库：先在 AI 身份下读真实边界，再向已核实主机发送明确 pos1/pos2 的 save_structure，确认双方维度一致。不要凭空使用玩家名参数去切换选区。

保存前 list_structures 检查重名，明确是否含空气/实体、是否作为 prefab；保存后检查返回 ID、尺寸及对应 NBT。放置遵循建筑参考中的锚点、变换和实体规则。撤销选区只影响选择历史，不撤销已建造或放置的方块。
