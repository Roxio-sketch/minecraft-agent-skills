# 实际用户存档：水面内飞行零速诊断

检查日期：2026-10-08。检查仅读取玩家 NBT 与其当前位置区块，没有修改用户存档，也没有操作用户客户端。

## 直接字段证据

来源：`D:/MC/库/red.ghs.axolotl/profiles/测试 (1)/saves/新的世界 (1)/playerdata/5a7527b3-3454-403e-9efe-c284fed6e726.dat`。

文件最后保存时间：2026-10-08 22:02:28（Asia/Shanghai）。以下是保存当刻的数据，不能视作检查时的即时游戏状态。

| 字段 | 保存值 |
|---|---|
| `Pos` | `[-1564.8094369213466, 62.55750422210346, 1951.305311326612]` |
| `Dimension` | `minecraft:overworld`（对应所读取的主世界 `region` 目录） |
| `Motion` | `[0.0, 0.0, 0.0]` |
| `OnGround` | `0` |
| `FallFlying` | `0` |
| `playerGameType` | `0`（生存） |
| `abilities.mayfly` | `1` |
| `abilities.flying` | `1` |
| `abilities.flySpeed` | `0.0` |
| `abilities.walkSpeed` | `0.10000000149011612` |
| `CultivationData.Realm / CultivationStage` | `8 / 3` |
| `CultivationData.Aura / MaxMana` | `1100000 / 1100000` |
| `CultivationData.ActiveDivineArt` | 空字符串 |
| `CultivationData.Meditating` | `0` |
| `CultivationData.HasDivineSoulOrigin / HasDivineStepOrigin` | `0 / 0` |
| `CultivationData.mindscape.state` | `NONE` |
| `CultivationData.ActivePossessedForm` | `minecraft:allay`（记忆形态，当前没有激活变身） |

保存数据中有 `wuxingxiantu:realm_flight` 效果；没有元神出窍、移魂寄魄或法天象地效果。`ForgeData` 中没有 `wuxingxiantu_soul_anchor` 或 `wuxingxiantu_divine_abilities` 快照。未复制完整 NBT、物品清单或其他存档信息。

## 对应位置的方块证据

读取了该保存位置对应的 `region/r.-4.3.mca`，区块坐标为 `(-98, 121)`。按普通玩家宽度 `0.6`，水平包围盒为：

- X：`[-1565.1094369213465, -1564.5094369213466]`，覆盖方块 X `-1566` 和 `-1565`。
- Z：`[1951.005311326612, 1951.6053113266119]`，覆盖方块 Z `1951`。

这两列中，Y `60、61、62` 均为 `minecraft:water`，属性 `level=0`；Y `63、64、65` 均为 `minecraft:air`。源水上方为空气，通常水面高度为 `62 + 8/9 = 62.8888889`。玩家脚部 Y `62.5575042`，因此包围盒下部浸入水约 `0.3314` 米。

这组保存位置与方块数据支持运行时 `isInWater=true`，且包围盒周围并无固体封闭碰撞。NBT 没有直接保存 `isInWater` 布尔值，所以这里是依据区块与包围盒的推导。

## 原版/Forge 1.20.1 方法证据

只读检查使用 `javap -c -p`，目标为本地 Forge 映射 jar：

`C:/Users/MAINTAINER/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_official_1.20.1/forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`。

| 方法 | 已核对的行为 |
|---|---|
| `Player.isAffectedByFluids()` | `abilities.flying=true` 时返回 `false`。 |
| `Player.isPushedByFluid()` | `abilities.flying=true` 时返回 `false`。 |
| `LivingEntity.travel(Vec3)` | 水中游泳加速度分支先检查 `isAffectedByFluids()`；飞行玩家会绕过该分支。 |
| `LivingEntity.aiStep()` | 普通水中跳跃同样先检查 `isAffectedByFluids()`；飞行时不会用游泳跳跃补偿零速。 |
| `LivingEntity.getFrictionInfluencedSpeed(float)` | `onGround=false` 时返回 `getFlyingSpeed()`；这里动态分派到 `Player.getFlyingSpeed()`。 |
| `Player.getFlyingSpeed()` | 飞行且未骑乘时读取 `abilities.getFlyingSpeed()`；冲刺至多乘二，零值仍为零。 |
| `LocalPlayer.aiStep()` | 飞行上升/下降的额外竖直力为输入方向乘 `abilities.getFlyingSpeed() * 3`，零值不产生升降力。 |
| `Player.travel(Vec3)` | 飞行分支调用 `LivingEntity.travel` 后，将竖直速度设回进入前竖直速度乘 `0.6`；初始竖直速度为零时不会靠重力退出悬停。 |

## 旧代码中的可达故障路径

1. 旧实现允许服务端 `RealmFlightControl.tick` 把 `abilities.flySpeed` 写成零，并同步/保存。该实际存档直接证明零值曾被持久化。
2. `RealmFlightControl.eligible` 因 `isInWater()` 拒绝水中的玩家；客户端因此不执行自定义输入与速度模拟，而进入 `release`。
3. 旧 `release` 只有 `STATES.remove(player) != null` 时才恢复零速。读入已保存的零速玩家、但还没有对应 State 时，此恢复会被跳过。
4. 同时保持 `flying=true`：原版绕过游泳力，水平飞行加速度与竖直升降力均为零；保存的 `Motion` 也为零。由此可以得到全方向失效的确定代码路径。
5. 退出飞行后，`isAffectedByFluids()` 返回真，原版游泳/走路可再次提供移动。这与用户描述“所有方向失效，退出后能走路”相符。

此路径必须与“空气中仍由自定义 controller 接管”区分。单独设置零 `flySpeed` 并不必然阻止已有动量或自定义 `setDeltaMovement`，所以空气 fixture 的旧版仍有位移，不否定这里的水中 fallback 故障。

## 结论与限定

已确认的事实是：实际用户存档同时包含飞行开启、零飞行速度、零动量，以及包围盒下部浸水的位置；旧代码确实存在与该组合对应的失效路径。

存档是保存当刻的快照，尚不能证明用户最初反馈瞬间必定已满足“无 State”等所有运行条件，也不能从存档恢复临时客户端按键或 State。该结果足以支持构造水中、零速、无 State 的回归 fixture，并验证既定补丁的恢复路径；不应扩大为已实证的第三方 mod 输入冲突。
