# Boss 技能源码几何回放

入口：`tools/vfx_library/boss_geometry_v3.py`，公共函数 `build_geometry(root: Path) -> dict`。
直接运行脚本将当前源码结果写至同目录 `boss-geometry.json`。共 77 个技能、10 个 Boss、173 个明确输入场景；覆盖可用阶段，距离条件技能的近/中/远分支，旧足迹存在/不存在分支，以及选定追踪招式的移动目标样例。

## 提取方法

- `BossSkillPrograms.java`、`HeartDemonSkillPrograms.java`、`BossCombatCatalog.java`、`BossCombatGeometry.java` 从当前源码逐字复制到隔离回放目录；不使用旧 `build/classes`。
- `line/circle/column/cone/ring/zone/dash` 方法从当前 `BossCombatController.java` 原样注入；其余世界相关接口由明确输入的记录适配器提供。
- 独立调用 Java 17+ 编译器运行这些纯时间线。没有运行 Gradle，没有构建模组，没有启动或替换 Minecraft。
- 每个源文件有 SHA256；源码和适配器共同参与缓存摘要。关键判定表达式变化会使契约检查失败，避免静默采用旧逻辑。
- 14 项数据断言与 7 项直接执行生产 `BossCombatGeometry` 的判定断言通过。这不等同于实机验收。

## 消费数据

`skills[id].scenarios[].events` 包含 `hazard/cue/projectile/node/move/control`。`collision` 是真实判定尺寸；顶层 `radius/width` 保留技能传入的名义参数。距离单位为 Minecraft 格，时间单位为 tick。

技能从 tick 0 同步开招。调度器每 tick 只快照一次到期任务，因此 `later(0)` 在下一 tick 执行；投射物生成后会在同 tick 运行第一次移动。`path` 是 `[tick,[x,y,z]]` 数组，生成点和首次位移具有相同 tick；插值需跳过零长度时间段。`cancelAtTick` 表示从该 tick 开始已移除。

每场景还提供从 tick 0 到场景末尾的 `targetPath`，由同一 Java 适配器输出；播放器不需要另外重写目标运动函数。`rangeSummary` 包含真实判定尺寸、危险时段和有条件安全缺口。

地面坐标由源码的 `centerBlock.y+1.05` 平移至 0；场地中心 `centerBlock.x/z+0.5` 平移至 XZ 原点。场景明确假定 Boss 从场地中心起手，朝 +Z，初始目标通常位于 +Z 10 格；实际世界中 Boss 起点与场地中心可以不同。

## 不可忽略的差异

- 直线使用胶囊判定：全宽 `width`，两端为圆头；实际玩家中心半宽 `width/2+0.25`。例如 1.8 格名义全宽对应 2.3 格判定全宽。
- 环的 `radius` 为外半径，内半径为 `radius-width`；真实判定外扩/内缩 0.25 格。贴地波高度 0.65，可跳；普通环高 8，不能按同一高度处理。
- 耳魅声墙 7 格名义缺口，胶囊端帽和碰撞容差后，最窄截面净宽 4.7 格；意惑镜墙相应为 3.7 格。边界仍会命中，净安全区间是开区间。
- 彭矫水环实际为五段弦线逐圈收缩，不是完整圆环。源码注释声称 60° 安全楔形，但最内圈半径 6 格、胶囊半径 1.05 格时，连续保证安全楔形约 49.84°；30°方向的点实际会被圆头命中。应按五条原始胶囊段绘制，不应补满，也不能把注释当成精确安全区。
- 目魇/心魔凝视的 30 格圆仅为提示。实际伤害没有 30 格半径判断，而是玩家朝向眼位点积 >0.45 且视线未受阻。应表现视线机制，不能将提示圈当作伤害边界。
- 意惑念刃的两条斜线是 SAFE 色纯提示，只有中间直线伤害；心魔业火反照的初始收镜环也不等于即时伤害。

## 有限场景边界

目标运动与旧足迹是可复现的合成输入。真实地形、跨技能遗留区域、玩家击碎节点、移动碰撞、玩家受拉后的物理轨迹、网络延迟没有模拟。伤害冷却与每次施法预算不改变几何，未在此做伤害结算。所有世界相关假设记录在场景 `inputs/notes` 与全局 `limitations` 中。
