# 连接、debug 与兼容

## 明确端点

从日志、现有配置或已知监听进程确定候选 HTTP 地址。默认 9876，被占用可向下回退到 9000；配置可以覆盖。针对候选地址 GET `/api/status`，有限超时约 4 秒；核对 type=minecraft-mod、version、build_revision、loader、pid、port 和可用性字段。

PowerShell 只读示例（地址仅为示例，必须替换）：

```powershell
$helperBase = 'http://127.0.0.1:9876'
$status = Invoke-RestMethod "$helperBase/api/status" -TimeoutSec 4
$connection = Invoke-RestMethod "$helperBase/api/cmd" -Method Post -ContentType 'application/json' -Body '{"cmd":"get_connection_info"}' -TimeoutSec 6
$status
$connection
```

再读取 get_player_info、get_world_info，记录对应客户端身份及维度。状态里的 http_url 固定使用回环地址；跨机器时保留已验证请求的主机名/IP，结合实际 port 构造 `/debug`，验证可访问后报告。localhost 指的是请求方自己的机器。主机与 AI 客户端各报告一条地址，不把 LAN 游戏端口拼成 debug URL。

## 独立 AI 客户端优先

1. 读取主机 get_connection_info，确认 singleplayer_server、lan_published、lan_port 和当前玩家。
2. 用户要求加入世界而尚未发布时，可 open_to_lan，显式指定 allow_cheats=false，除非用户要求开启作弊。源码默认 true；返回实际端口，已发布时可能忽略请求端口。此操作还会创建/公布创造模式、OP 的 helper，报告该副作用。
3. 使用独立游戏目录及独立有效多人身份启动匹配 Minecraft/Forge/模组集的客户端，连接实际主机 IP:LAN端口。不要更换玩家账号、复用玩家目录或关闭玩家窗口。
4. launch_minecraft 的 server/server_port 可以用于加入，但启动器可能使用全局账号、自动安装版本/JAR；先核实隔离配置。不能通过随意关闭认证来解决身份冲突。
5. 固定 AI HTTP 地址和 PID，读取玩家/世界信息，并结合主机加入记录确认进入同一世界。进程启动、标题画面和 HTTP 服务上线都不等于入世成功。

AI 客户端可执行自己的 GUI/移动操作。主机批量建造及结构保存需要集成服务器，明确使用主机端点；不要将这些能力归给 LAN 客户端。

## helper 与命令执行身份

helper_info 返回 present、name、uuid、位置、维度、game_mode、op、independent_body。helper 依赖已开放 LAN 的集成服务器。helper_move 是直接移动/传送，helper_goto_player 是传送到玩家，不提供寻路。

helper_command 以 helper 身份执行；execute_command 默认以客户端实际玩家执行，只有 as_helper=true 才切换 helper。@s 指执行者；给物品、传送和选择命令先确认执行者。主机截图仍是玩家相机，helper 没有自己的画面。F9 状态界面可能触发 helper 操作，读状态优先 API。

## 兼容与诊断

- 发布 JAR：Minecraft 1.20.1、Forge 47.x（构建 47.4.0）、Java 17。历史目录不能证明当前 JAR 支持 Fabric、NeoForge 或其他 MC 版本。
- /api/status 中 helper_available、structure_available 受集成服务器/世界加载影响；菜单和 LAN 客户端不具备全部能力。
- /api/events 是 SSE，最多 4 个连接、约 300 秒生命周期、15 秒心跳；断开后有限重连，避免占满槽位。/api/calls 可查调用，/api/screenshot 可读截图；约 2 秒缓存可能导致连续截图相同。
- Photon 是可选反射集成；已知本地组合 Photon 1.1.17 + LDLib 不代表任意版本均兼容。先核实实际加载版本和功能，详细 VFX 操作仅在可用时参考 minecraft-vfx-automation。
- HTTP200 中 error 或 ok=false 仍是失败。读请求约 6 秒，长变更约 25 秒；超时先检查世界/任务/日志状态再决定重试。
- 连接失败先区分端口错误、身份选错、未加载世界、无集成服务器、模组缺失、版本冲突和进程启动问题；不要自动覆盖玩家配置或重装模组。
