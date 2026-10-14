# 区域、深度与相机

## 区域为何只显示另一套场景

对父视图每个像素建立三项：区域 coverage、父空间入口深度、子场景颜色。只有像素属于区域，且入口不被父空间更近的不透明表面挡住，才允许显示子颜色。Stencil 可记录这个集合；也可使用 aperture mesh、独立 mask 或解析求交。

不要把主世界已有颜色按球体投影换色。这仍是环境材质化/后处理；独立 scene source 应能改变几何、天空、空间层次和相机响应。

## 平面、不规则面与裂隙

- 平面 Portal：使用局部二维多边形的三角网格，映射到世界坐标。多个三角形共享同一 aperture，不重复积累 alpha。
- 带孔不规则窗口：对孔洞正确三角化或用单独 mask/discard。包围矩形只用于 culling/scissor，不能替代真实 aperture。IP SpecialFlatPortalShape 就是 mesh 区域加平面裁剪。
- 裂隙：由中心曲线与宽度函数产生 ribbon，边界动画与开口共享 seed/time；处理自交、尖端退化和局部朝向。屏幕空间裂纹装饰不能决定世界空间入口深度。
- 非平面区域：三角网格仍可限制可见像素；一个 oblique plane 无法表达球面/任意封闭体的内外。使用解析 ray interval、多个 clipping 约束，或目标几何着色器的区域测试。

Mask 的过滤要有策略：内部硬归属用于 depth/stencil，外沿软羽化用于装饰。MSAA、alpha-to-coverage 和 mask 下采样均可能改变覆盖；不要把抗锯齿颜色 alpha 直接当几何深度。

## 球体的射线区间

在统一空间中 `p(t)=o+t*d`，球心 `c`、半径 `r`：

```text
m = o-c
a = dot(d,d), b = dot(m,d), k = dot(m,m)-r*r
D = b*b-a*k
if a≈0 or D<0: miss
tEnter=(-b-sqrt(D))/a; tExit=(-b+sqrt(D))/a
```

不要默认被缩放矩阵变换后的 d 仍是单位向量。若重新归一化，就必须重新解释 t 的单位。只保留与相机有效 near/far 区间相交的段；near 距离是 eye-space z 平面，不是所有视线相同的球半径距离。

外部视角通常入口在 tEnter。内部相机时 tEnter<0，不能继续画被背面剔除的球壳当近端 mask。选择：内部全视野进入领域；或有限区间 `[near,tExit]`。生成 ray 用真实逆投影和 camera basis；圆形屏幕 mask 不是球体证明。

有限体积若主世界最近表面在区间内，必须去掉它并取得出口之后的内容。单张主 color+nearest depth 没有保存被挡住的后层，不能重建所有出口后景。路线包括：主世界几何 pass 进行 volume clipping，分段再渲染，或额外深度层/更完整场景数据。透明层还需相应分类。球内子场景也要按出口截断，而非无限贴上去。

## 深度域

OpenGL 常规 perspective、NDC z∈[-1,1]、depth range [0,1]、非 reversed-Z，near=n、far=f、正的 eye-space 距离 z 时：

```text
d = f/(f-n) - f*n/((f-n)*z)
z = f*n / (f-(f-n)*d)
```

只用于该约定。oblique、reversed-Z、自定义 depth range、shader-pack 投影或正交相机应使用各自的逆投影重建，不硬套公式。主子 FBO 的 raw depth 若 near、far、投影不同不可直接 min/blit 比较。

### 入口深度合成

最稳妥的“窗口”规则：child texture 提供颜色，父空间 aperture 的位置提供写回深度。

1. 保留父深度中入口前的前景。
2. 以父投影画 aperture、启用父 depth test，把子颜色显示在可见入口。
3. 写入口深度，防止随后绘制的父空间后景覆盖窗口。
4. 后续更近的父前景仍可通过测试。边界装饰不要任意写满整个球的深度。

这是一种有意的深度封口：不能让子物体直接伸出入口与父物体交错。穿界实体需分割/裁剪后分别绘制两份，或单独设计映射深度与排序。

### 子深度重投影

需要真实位置相互作用时：child depth → child NDC → inverse(child projection/view) → destination world → inverse(portal mapping) → source world → parent view/projection → parent depth。必须保存相应矩阵和有效深度标记，处理天空、越界、负 w、遮挡孔洞与透明层。变化的 camera/FOV 令旧缓存失效。复制原始 child depth 不是重投影。

## Stencil 直接嵌套

适用于 owned stencil 域、受控清屏与 renderer。以下是算法契约，不是粘贴即可运行的 OpenGL 代码：

```text
parentLayer = current layer
draw aperture using parent camera and parent depth
  color write off; stencil EQUAL parent; depth-pass INCR
  only actually visible aperture pixels acquire child layer
push child context
  stencil EQUAL child; stencil write off
  clear child-region depth via a masked draw at far depth
  render child scene (including children within budget)
pop child context
  restore parent camera/projection/clipping before drawing aperture
  color write off; stencil EQUAL child; depth ALWAYS; draw parent entrance depth
  collapse owned child stencil values to parent without changing color/depth
restore the incoming GL/Minecraft state
```

在相同像素的重叠三角形中，EQUAL parent 配合 INCR 可阻止重复递增。Stencil 比较掩码和写掩码不同；嵌套 content 阶段不要意外写 stencil。比较函数 `LESS` 的含义是 ref < buffer value。

`glClear` 忽略 stencil test；它受 scissor 和相关 write mask 等状态约束。要只清 aperture，使用 stencil 限制的全屏三角形写 far depth，不能直接 clear 整个主目标。进入下一层前若已有父内容，确保 far 值与该视图 depth 约定一致。

8-bit stencil 不意味着可以经济地递归 255 层。还要预留其他使用者的 stencil 域，禁止静默清掉其他 MOD 的位；不能协调时使用 owned FBO/mask。当前显示目标不带 stencil 时不能在帧中重分配丢掉主深度。

多入口采用确定性排序并让父空间 depth 决定可见性；nearest-first 常用于避免被近窗挡住的远窗继续消耗预算。相交/共面入口需规则化 owner、深度容差和稳定 tie-break，不用无限 polygon offset 掩盖冲突。

## Framebuffer 合成

1. 为子视图租赁 color+depth 目标；需要 stencil 才加 stencil，核对 completeness。
2. 保存调用者的 read/draw FBO、viewport、projection 以及实际修改的所有状态；绑定 owned target、设明确 viewport、清其自有附件。
3. 以第二 Camera 的矩阵渲染 source，flush 该视图所属 buffers。
4. 恢复父 context，再用父相机画区域合成。采样子 color；根据入口封口或重投影策略处理父深度。
5. 目标在本帧消费者结束之后归还池。递归的子层不能覆盖父层尚待采样的附件。

子视图采用全屏比例投影时，采样坐标来自父屏幕 NDC 的 projective mapping；窗口局部 UV 直接铺满会错过空间对应关系。也可构造 off-axis frustum，使 aperture 恰好对应整个子目标；此时使用配套映射。降分辨率只改像素数，不改 FOV/aspect。scissor 裁剪、cropped projection 与采样偏移必须一致。

## Camera / FOV / clipping

- Portal mapping 可写成 `x' = dest + A*(x-origin)`；位置包含平移，方向只应用线性部分。A 的坐标朝向/180°翻转由入口出口约定决定，不额外盲加旋转。
- 将 look/up 一起变换并建立稳定正交基。uniform scale 改变空间尺度，det(A)<0 改变镜像奇偶与 front-face winding；非均匀 scale 会使普通 camera 姿态不足以表达映射，需一般投影或明确限制。
- 保留实际帧的 FOV、aspect、camera bobbing/zoom 策略。不要每个窗口独立硬编码“70°”；FOV 失配会让空间在入口滑动。
- 裁剪平面从一个空间到另一个空间遵守 inverse-transpose。oblique near plane 适合平面入口，避免目标入口前的实体漏进窗口；关注平面朝向、dot 接近零及镜面。
- near 太小降低远处深度精度，太大切掉近入口；far 应依据子场景预算而非无限远。near 穿过 aperture 时需要裁剪/封口策略，不能依赖背面网格一直可见。
- 大世界用 double 计算世界位置差，上传 camera-relative float；不要直接把数千万方块世界坐标放进 shader float 后再相减。

## 穿越状态与内外投影

平面入口可进入时，维护离散的 `outside / inside / disabled` 状态，和入口局部坐标中的上一帧眼位。穿越决定应先于“距离入口太近/位于背面”的外部绘制拒绝，否则相机跨过 near 阈值时只会丢失窗口。

```text
check world/player/lifecycle validity
transform camera motion to gate local coordinates
update entry/exit state using intersection with the actual opening
if outside: apply outside visibility tests, render aperture-matched off-axis view
if inside: render full viewport rays using actual camera orientation and projection
compose with the depth policy for that mode; restore incoming state
```

- **外部**：子目标可对齐入口的 off-axis frustum，窗口局部 UV 与其配套。眼位趋近入口时投影放大是预期，但分辨率不足会模糊，应按屏占预算提升或过渡。
- **内部**：从父视图真实 inverse projection/view 得到射线，转换进子空间基，按主 viewport aspect 生成 FBO；相机平移也映射到子空间。不能把窗口的整个 UV 图继续铺满屏幕，不能固定朝向。
- **深度策略**：内部全替换可在受控原版阶段合成并写近深度，以抑制随后父场景透明层。手/UI、其他 MOD 后绘制内容需另行决定和测试；这不是通用 shader-pack 策略。离开恢复父空间入口深度合成。
- **生命周期**：正常资源重载可保留进入状态并重建 GPU 资源；关闭、死亡、换世界和退出使状态失效。异常先恢复渲染状态，再按明确策略暂停/关闭，保留退出入口。

Ink Realm 0.2.0 已测的是静止平面门、按相机 signed side 越过 `0.15`、当前眼位位于缩小的椭圆内的判定。它修复了旧版 `eye.z < 0.12` 直接 return 的问题，**没有验证高速/移动门的完整穿越**，这些数值不是通用阈值。

需要高速运动/窄裂隙时，使用运动段与平面的交点 `t=d0/(d0-d1)`，要求 `0≤t≤1`，在交点测试真实 aperture，不能只检查本帧终点。需要抑制抖动时维护带滞回的稳定侧和 pending crossing，真正跨到目标侧才提交；若当前帧停在滞回带内，保留交点信息，避免下一帧漏检。移动入口使用前后两帧相对变换；瞬移、第三人称 camera 与玩家身体穿越的语义单独指定。以上是扩展路线，不是随附样例的既有能力。

## 透明、Z-Fighting 与绘制阶段

先完成主 opaque，合成窗口并建立入口深度，再处理适当的主透明层，能保持常见前景关系。已经混进父 color 的玻璃/粒子无法靠最后一次深度比较完整分离。Fabulous targets、OIT、折射、水面要专门适配；`AFTER_LEVEL` 不自动解决这些问题。

避免共面边框与封口争抢深度：给边框真实厚度或受控偏移，保持窗口深度一致；少量 bias 有单位和上限，不能跨过本该遮挡的前景。软边界不等于软深度。子雾/天空与父雾的职责独立，避免后阶段再给子颜色套一次错误的父世界雾。
