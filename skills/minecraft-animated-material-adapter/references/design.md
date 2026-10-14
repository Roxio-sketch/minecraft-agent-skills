# 设计与实际配方

## 把描述映射到空间

“纹路跟着刀刃弯曲”：bind/bone-local 数据随姿态变换；“刀里是一片稳定宇宙”：明确物体局部虚拟眼位；“表面流动金属”：可以物体或骨骼空间构造场。运动位置和程序场的锚点是不同输入。

选择区域：命名 ModelPart、材质槽、纹理 mask、mesh 属性。任意区域选择不是通过物品名字猜测。不要重设已经求值的目标动画；示例自己拥有模型，因此每帧 resetPose 后重新求值不会覆盖其他 mod 动画。

## 本样例字段

color：程序纹路 RGB；amplitude：blade zRot 摆幅，弧度 0..π；frequency：摆动 Hz。 shader Time 仍独立推进，frequency=0 只停止部件摆动，不能冒充冻结所有材质动态。

例子默认将同一材质用于 grip/blade，骨骼局部坐标各自生成图案。按区域保留原材质时，在 CPU adapter 中按 part 分派 RenderType，并正确乘入各 part 的祖先变换；现有三个字段没有材质区域配置。

## 适配器契约

FrameContext(frame/time/context/pose/light/overlay) → AnimationSnapshot → GeometryRegion → MaterialSelection → DrawPacket。保存会变动的数据，不跨帧长期持有共享 PoseStack 或旧 uniform。实际模型不是帧间不可变时不要缓存动画后的顶点作为永久 bind mesh。

采样空间分为：bind-space（姿态前）、bone-local（每部件）、posed object-space（变形后）、world-space、view-space。每一种都有用途；纹理与几何 UV 不能互相冒充。刚性部件法线使用对应 normal matrix；多权重 skinning 必须查目标库如何组合位置、法线和切线。

挂点沿同一骨架/动画结果求值：root → 祖先 → 当前骨 → local attachment。示例 lastTip 是当前 renderByItem pose 空间的刀尖，不是通用世界坐标。用于拖尾需辨认手持场景、外部模型视图和世界原点，避免把 GUI 挂点写入世界轨迹。
