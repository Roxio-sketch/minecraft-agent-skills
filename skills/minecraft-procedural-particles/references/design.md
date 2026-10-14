# 设计与实际配方

## 设计转换

“炸开的金色碎星”：burst、球方向速度、重力/阻尼、由亮核到暖色消退；“魔法环绕”：稳定身份与环形初始位置、连续旋转、轻微高度扰动；“被奇点吸入”：有限外壳初始化、中心加速度、进入内核后终止。每种运动都有不同结构，不能只换色。

区分 rate（每秒发射）与 count/burst、最大同时存活容量、总持续时间和单粒子寿命。平均存活量可由 rate×平均寿命估计，再为峰值留预算，实测溢出策略。容量不足时丢弃新粒子、淘汰旧粒子或降 rate 由任务决定。

## 本样例字段

motion：orbit/burst/fountain/attract；capacity：1..16384；rate：0..4096，burst 分支将 ceil(rate) 作为一次爆发数量，单位差异必须解释；lifetime：秒，单粒子随机为 0.75..1.25 倍。

speed：速度或 attract 加速度尺度；drag：指数阻尼系数；gravity：世界 Y 加速度；orbit：弧度/秒；size：billboard 半尺寸，方块；color/accent：由粒子年龄混合的 RGB；seed：Java long。

例子不含碰撞、子发射器、任意网格或网络同步。需要时增加接口。Canvas 工作台是独立构图预览，与 Java 使用不同 RNG；同 seed 不承诺位置逐粒一致。固定 tick 的 Java 核是可单独测试的真实实现。

## 时空与生命周期

模拟 20Hz，只在非暂停 tick 推进；render 用 previous/current 和 partialTick 插值。粒子出生初始化 previous=current；不能每渲染帧推进 age 或追加 rate 数量。跨世界/瞬移使 emitter local 到 world 的契约改变时，明确重置、断开或转换。

世界空间出生后保持原轨迹；局部空间随发射器移动；出生时转换为世界空间的自由尾迹是第三种行为。挂点必须来自对应动作帧，不能拿 GUI pose 代替手持世界位置。

配置加载须拒绝未知字段、不合法预算和非有限值。停止可立即清理或停止出生等待存活粒子消退；示例 off 是立即清空，持续最多 1200 tick。API/网络触发只传语义事件与必要 seed，按需求防重复并限制容量。
