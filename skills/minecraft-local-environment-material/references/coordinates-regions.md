# 深度重建、世界锚定与影响场

## 一帧数据契约

绑定同一帧/视图的 `{color, depth, viewport, P, Vrel, cameraDouble, normal?, category?, light?, fog, generation}`。保存矩阵值而不是活 PoseStack 引用；shader pack 的 jitter、非标准深度或 bobbing 也属于生产者矩阵。不能用用户 FOV 设置重新猜矩阵，也不能在绘制手部清深度以后读世界深度。

传统 OpenGL、标准 Z、深度范围 [0,1]、NDC Z 为 [-1,1] 时，像素中心 UV 的重建为：

```glsl
vec4 h = inverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
vec3 viewPosition = h.xyz / h.w;
vec3 relativePosition = (inverseRelativeView * vec4(viewPosition, 1.0)).xyz;
// CPU: centerRelative = (centerWorldDouble - cameraWorldDouble).toFloat();
vec3 regionPosition = relativePosition - centerRelative;
```

也可预先计算 `inverse(P * Vrel)`，GPU 只乘一次并除 w。相机平移若已在 V 中，不能再加 camera；camera-relative 顶点配 Vrel 时才需要最后加相机得到完整 world。点用 w=1、方向用 w=0，最后仅对齐次点做透视除法。不要先线性化 depth 再套上这套逆投影；线性化距离是另一表示。

深度清值不是可用于有限球判定的天空表面。标准背景 depth=1 时跳过；远处有效几何与清值难分时增加 coverage 标志，而非随意阈值删除远景。处理 MSAA、反向 Z、log-depth、clip-control、动态分辨率、屏幕原点翻转时先改契约，公式不是跨管线常量。

纹理采样用附件实际像素尺寸和 viewport 偏移；不要用 GUI 缩放后的尺寸。基础验证用 nearest 深度，不能跨轮廓线性插值深度制造不存在的表面。

## 空间锚定与精度

影响区域固定不代表纹理一定固定。分别选择：

- **区域锚定**：放置点固定，或跟随玩家/实体；模型原点、脚底、眼睛或命中偏移需明确。
- **材质锚定**：全世界统一原点、每一区域固定原点，或跟随施法者。跟随区域但保持世界材质相位可以避免走路拖拽图案。
- **方向式 Cosmic**：允许按相机方向观看一个方向场，但不要把相机 yaw/pitch 当世界区域位置。更强的表面粘附可用稳定的局部三维场/三平面采样。

世界坐标大时，不在 GPU 把两个巨大的 float 相减。CPU double 做 `center - camera` 和 `materialOrigin - camera`。对跨大区域的周期图案，可用整型 cell/high-low 原点或 CPU 高精度模周期；局部原点不得每帧随相机跳格而没有补偿相位。

数值验收不仅测 look rotation；还测平移、第三人称、FOV、bob、俯仰、屏幕边缘和大坐标。调试画 `fract(Pworld / grid)`、Mask、类别、法线；移动相机时同一世界点的调试值应保持定义的一致性。

## 区域中心数据来源

| 来源 | 应记录 | 常见失误 |
|---|---|---|
| 玩家 | owner UUID、所在维度、前后 tick 位置、anchor offset | 使用第三人称 camera 代替角色中心 |
| 实体 | UUID 而非长期 Entity 引用；插值与实体缺失策略 | 切世界后复用 entity numeric ID |
| 命中点 | 服务端/本地确认的 hit location、命中法线、释放 tick | 后续每帧重做 raycast 导致区域漂移 |
| 方块 | BlockPos 与 center/corner/face-offset 明确约定 | 把整数角点当方块中心 |
| 技能路径 | 同一维度的一组 double 坐标、时间、宽度 | 每帧采样无限累加，瞬移把两点连穿全图 |

视觉客户端不能替服务端决定伤害。需要多人一致时，服务端下发权威区域参数/时钟/维度，客户端只插值显示。

## 统一 SDF / influence 接口

令 p 为区域局部位置，负值在内。旋转 Box/圆柱用正交逆旋转得到局部位置；非均匀缩放下 SDF 数值不再是世界米，边缘宽度需换算。所有尺寸、平面厚度和噪声幅度用方块单位。

| 形状 | 定义 |
|---|---|
| 球 | `d = length(p) - radius` |
| 有限圆柱 | `q=(length(p.xz)-r, abs(p.y)-halfHeight)`；`length(max(q,0))+min(max(q.x,q.y),0)` |
| Box | `q=abs(p)-halfExtent`；`length(max(q,0))+min(max(q.x,max(q.y,q.z)),0)` |
| 半空间 | `d=dot(normal,p)-offset`，法线归一化；需范围上限 |
| 有厚度平面 | `d=abs(dot(normal,p)-offset)-halfThickness`；有限面可与 Box 相交 |
| 路径 | 每段 capsule 距离：投影到线段并 clamp，然后减半径；多段取 min |
| 噪声区域 | `d'=d + amplitude*(noise(Pstable*frequency,seed)-bias)`；不是精确距离场 |

零长度线段按球处理，分母设 epsilon。长路径先简化/分段剔除；不能让每个像素遍历无上限点列。噪声幅度必须扩张粗剔除 bounds，防止边缘突然消失。shape union=min、intersection=max、subtract=max(dA,-dB)；soft union 会改变边界，需要重新计算 bounds。

仅向内羽化的覆盖：`w=1-smoothstep(-feather,0,d)`。这是示例选择，羽化若允许外扩，应扩大影响 bounds，并在验收里定义“范围外”。边缘光与材质覆盖分开计算：例如 `edge=1-smoothstep(0,width,abs(d))`；不能为了光边而改写表面深度。

路径贴地不等于 capsule 自动遵守地表。可选：射线采样地表后记路径、限制高度带、用法线方向过滤，或对真实表面建立距离场。三维球会同时覆盖楼上/地下/背墙位于范围内的可见表面；需要沿地表传播时引入导航/体素/表面传播数据，明确不再只是欧氏距离。

## 法线与类别

有 normal buffer 时先确认 view/world 空间、格式与有效性；没有时 `cross(dFdx(P),dFdy(P))` 是可见平面的近似，轮廓和深度间断处错误，不代表原模型平滑法线。先获取导数再进入非一致分支，做零向量保护。高质量用边缘感知邻域或显式几何法线。

Depth/normal 仍不能辨识“方块 vs 实体”。分类必须来自阶段隔离、分类附件或原几何 draw。不同深度时间点的相等比较只适合有限启发式，不能作为稳健对象 ID。
