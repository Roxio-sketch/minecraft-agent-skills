# 流星御空动作升级：独立 CPU 验证

依据：`C:/Users/MAINTAINER/Desktop/流星御空说明.md` 全文与用户最新角度答复。当前正常飞行为前倾 83°，身体相对航向上仰 7°；历史 12°/25°要求已由本次说明替代。

## 修改范围

- `src/main/java/cn/blockforge/generated/generatedmod/realmflight/FlightVisualDynamics.java`
- `src/main/java/cn/blockforge/generated/generatedmod/client/realmflight/RealmFlightVisuals.java`
- `src/regression/java/cn/blockforge/generated/generatedmod/RealmFlightRegression.java`

没有改飞行移动、境界数值、权限、网络包格式、相机事件、碰撞箱、皮肤几何或装备渲染层；没有隐藏玩家或生成替身。

## 集中参数与单位

所有可调动作常量在 `FlightVisualDynamics`；根变换角度为度，模型肩部和头部 Euler 目标为弧度，速度为方块/tick，时间为秒。

| 参数 | 当前值 | 行为 |
|---|---|---|
| 正常 / 高速 / 极速前倾 | 83° / 88° / 90° | 实际速度 / 该境界已有正常速度为 1 / 1.30 / 1.50 时取对应目标 |
| 正常 / 高速手臂外展 | 15° / 7° | 随速度平滑收紧，悬停恢复 0° |
| 手臂后掠 | 0.20 rad | 保留自然后掠，悬停恢复 0 |
| 头部航向上抬 | 正常 8° / 高速 5° | 在实际飞行平面中计算，身体侧倾后仍保持目标方向 |
| 头部转向提前 | 5°～12° | 实际水平转向角速度驱动，0.20 秒平滑 |
| 普通 / 极限侧倾 | 最大 35° / 45° | 由实际水平角速度和实际速度计算，不读取 A/D 设置固定姿态 |
| 左右臂转弯差 | 5°～10° | 内侧收紧、外侧展开；弱转向从 0 平滑进入 |
| 起飞 / 退出过渡 | 0.30 / 0.35 秒 | 固定 20 Hz tick；插值只用于渲染 |
| 侧倾进入 / 回正 | 0.20 / 0.30 秒 | 指定时长内完成目标变化的 90% |
| 视觉 yaw / pitch 最大步长 | 28° / 18°每 tick | 180°接缝使用连续相对 yaw |
| 旧可选相机侧倾数据范围 | 最大 24° | 保留原 `camera` 事件与配置；身体 45°通道独立 |
| 骨盆高度 | 0.7040625 方块 | `(1.501 - 12/16) * 0.9375`，绕原玩家真实模型骨盆旋转 |

境界正常速度由已有 `RealmFlightBalance.speed(realm,stage,false)` 读取，并按现有原版速度保留率 0.91 转成观察到的 tick 位移参考。远端复用已有视觉状态包中的境界/阶段，不增加发送频率。

根变换顺序为 `RY(-flightYaw) · RX(trajectoryPitch) · RZ(roll) · RX(leanPitch) · RY(bodyYaw)`，绕同一骨盆平移夹持；因此侧倾绕飞行轴旋转，历史尾迹与真实运动不受身体局部动作影响。头部世界方向先在飞行平面定义，再逆解身体侧倾与前倾；近水平玩家的可见头部偏航主要映射到模型 Z 轴，避免模型 Y 轴在俯卧补偿接近 90°时失效。

双腿保持并拢平行、三轴旋转回零，不播放行走摆腿。攻击、用物和已有心剑动作仍保留原手臂动作；帽、袖、裤、上衣复制最终原模型骨骼，护甲/持物仍使用既有模型接口。

## 独立实际验证

使用 `C:/tmp/temurin17/jdk-17.0.20.1+1/bin/javac.exe` 和同目录 `java.exe`，直接编译动作、外观、轨迹与 `RealmFlightRegression`，运行 `build/reference-flight-pose-regression/FlightPoseRunner.java`。未运行 Gradle 或安装 MOD。

实际输出：

```
Realm flight visual regression: speed poses, anticipatory head, angular banking, mirrored turn arms, six tiers and recovery passed
```

通过的数学及状态验证：

- 三个实际速度档 × 八个水平/斜飞/垂直航向，共 24 个完整渲染矩阵场景；躯干目标精确为 7°/2°/0°上仰，头部航向抬升精确为 8°/5°。
- 左右真实圆周转向、普通/极限速度；侧倾符号、肩部内倾方向、头部提前方向、转弯臂差与 world-space 头方向均一致。
- 四 tick 进入目标 90%、六 tick 回正 90%，直线侧向移动不会被误识别为转向。
- 35°/45°身体侧倾与原 24°相机数据通道分别受限。
- 真实模型骨盆点经过完整 vanilla/root 矩阵仍固定，涵盖俯仰及极限侧倾。
- 179°/-179°和部分姿态权重相对 180°接缝连续，无整圈跳变。
- 多次 30/60/120 FPS 渲染采样不会推进 tick 状态。
- 停悬恢复站姿与中性双臂，退出恢复原动作；无效数值保持有限。
- 已调节的高级境界球体半径、球形三轴和最长 28 方块尾迹合同。

本报告只确认 CPU 数学与 Java 17 编译。当前动作的真实客户端外观、Steve/Alex、盔甲/持物、第一/第三人称及多人最终验收由父任务后续客户端矩阵记录，不能用此前 12°/25°截图替代。

## 验证时源 SHA-256

| 文件 | SHA-256 |
|---|---|
| FlightVisualDynamics.java | `9EB6FB20E89F46DDED46AD78C2F16850DE12D580B2AB5320E26E02D68AD3563E` |
| RealmFlightVisuals.java | `2DEE93AC63B52969F2797F21177A63B6C9DABB745AC7A2D67B454E6A2103D81D` |
| RealmFlightRegression.java | `FBD30182856CEDABA0C77CA7AB94F16A42FB068EA08BFBABA5027071B490EFAF` |
| RealmFlightMovement.java（未修改） | `662184185A3B4F10970693969E54A49676B920EA56D77B7C52DB4AE8BC70F8C3` |
| RealmFlightControl.java（未修改） | `6DA3F776A279C057FA8C1AC7BAB89D4312CE1D9CF3002CBC7D828105C8871AFE` |
| RealmFlightBalance.java（未修改） | `B14C7F4F99A405D62DE23D21C9BAAB11A58FB690046B712F241C202A80F14F89` |
