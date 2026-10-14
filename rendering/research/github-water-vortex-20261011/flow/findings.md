# 水龙卷表面流动源码研究

研究日期：2026-10-11。这里的源码用于静态阅读，未运行上游工程。固定提交、逐文件 SHA256、实际阅读范围和函数行号见 [manifest.json](manifest.json)。收集的是精选源码与许可证，完整可运行项目和外部贴图未包含。

## 对当前问题的判断

“缺乏螺旋感”和“间隙太多”涉及不同层：前者需要宏观螺旋轮廓、沿轮廓流动的材料细节；后者首先取决于水体载体是否连续。这三份源码主要补足表面流动、法线、泡沫与折射，不会替我们生成完整水龙卷。以下迁移方式是根据代码提出的下一轮实验，尚未实装。

## three.js Water2：双相位流动与平面反射/折射

固定提交：`9b4a2ac29c63ccb43fd51c5661f2f873ac2c39b8`，选择 `master` 分支，不把它写成仓库当时的默认 HEAD。

实际读了 [Water2.js](three.js/examples/jsm/objects/Water2.js)、[Reflector.js](three.js/examples/jsm/objects/Reflector.js) 的渲染回调、[Refractor.js](three.js/examples/jsm/objects/Refractor.js) 的裁剪/渲染段，以及 [WaterRefractionShader.js](three.js/examples/jsm/shaders/WaterRefractionShader.js)。

- `Water2.updateFlow`（181）：两层相位相差半周期；片元段（343–375）从 flow map 的 RG 解码方向，以两次法线采样及周期权重混合，减轻重置跳变。
- 该代码继承 Mesh，接受已有 geometry；没有水体模拟、液滴合并或表面重建。法线仍按水平水面通道重排，不能不改坐标系就套到竖直水柱。
- `Reflector.onBeforeRender`（116）镜像相机，`Refractor.updateVirtualCamera`（163）以水面平面修改投影裁剪；渲染后恢复目标、XR/阴影开关和适用的 viewport。这是平面水的辅助渲染，曲面水龙卷需要另选折射/厚度方案。

下一轮迁移：在水柱的周向/高度坐标中定义流向，将双相位细节附着于同一个螺旋大形；世界时间直接计算相位以支持暂停/回放，避免照搬累计 delta 的状态。海面可以单独使用平面反射思路；水柱的透射则根据入水/出水距离处理。

源码许可证：[MIT](three.js/LICENSE)。保留其中的版权及许可文本；默认 normal map 的二进制文件没有收入本包。

## Waterways：弯曲载体、障碍流向与泡沫区域

固定提交：`cd2e2193fe2c7792efb4c35118ade3ad868538b4`。

实际读了 [river.shader](Waterways/addons/waterways/shaders/river.shader)、[river_manager.gd](Waterways/addons/waterways/river_manager.gd) 的生成与烘焙段、[water_helper_methods.gd](Waterways/addons/waterways/water_helper_methods.gd) 的网格/碰撞图函数、四个相关滤镜。其余 `.gd/.shader` 是保留的上下文源码，未作逐函数验证。

- `FlowUVW`（56）用相差半周期的两组 UV、三角形权重、整数周期 jump；两种采样尺度丰富近处水纹。贴图 A 通道给不同区域不同相位，减轻整片表面同步脉动。
- `fragment` 使用坡度、距离图与名为 pressure 的图控制流动强度。实际 [flow_pressure_pass.shader](Waterways/addons/waterways/shaders/filters/flow_pressure_pass.shader) 对一行碰撞占据值取平均；它是美术用烘焙指标，不是求解流体压力。
- `_generate_flowmap`（612）：世界碰撞采样→边缘扩张→梯度法线→切向流→模糊→泡沫/相位组合。`nm2flow` 在障碍边缘建立局部绕行方向。可借鉴到海面围绕吸水口的流向/泡沫分布。
- `generate_river_mesh`（74）沿曲线排列横向网格。横向向量为路径方向叉乘世界 UP：竖直段会退化，水柱必须换成稳定的随路径移动的局部标架。
- 折射段二次读取偏移坐标的深度，发现取到前景时退回原屏幕坐标；这条检查比仅扭曲背景 UV 更适合保护前景遮挡。但单层深度仍不足以表达多层自交水片。

下一轮迁移：沿竖直中心线构造连续周向载体，水纹跟随切向+上升流；只在主体外层/顶部生成少量撕裂水片。海面泡沫分布学习障碍图思路，改成吸水口的半径和局部速度指标，不能把“pressure map”当物理压力。

源码许可证：[MIT](Waterways/LICENSE)。源文件头写 `LICENSE.md`，该固定提交根文件实际叫 `LICENSE`；原文均保留。`river.shader` 的 `ease` 注释提到来自 `math_funcs.cpp`，没有明确固定其上游来源；若未来直接复用该 helper，应补查其来源。本次不复制进自研内核。

## Tiled Directional Flow：四组方向瓦片混合

固定提交：`386892cc3f6e21b4694b2777a9e7e66d47a3dd67`。

完整读了 [TiledDirectinalFlow.shader](Tiled-Directional-Flow/Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader)。文件名沿用上游拼写。

- `surf`（100）：每块按 flow map RG 构造旋转/缩放矩阵；B 表示波长尺度。四组半格偏移采样各有移动速度，按空间权重混合，减轻块间接缝与整体平移感。
- 混合法线后补偿向量互相平均造成的幅度损失；海面吸水口周边不同方向、不同速度的水纹可以参考这部分。
- 这份 shader 的 alpha 用于调制水纹与天空/底色混合，最终输出 alpha 是 1；它没有透明水片拓扑，更不会填补水柱孔洞。其折射在原注释中就被标为简化近似。

下一轮迁移：海面按极坐标切向/向心流组织方向块；水柱以稳定局部标架组织周向/上升流。瓦片方案与双相位方案先分别比较，不盲目叠加所有采样。

许可证：[根 LICENSE](Tiled-Directional-Flow/LICENSE) 包含 MIT 文本以及 Frans van Hoesel 2010 的 Creditware 声明；shader（49–59）也保留这份声明。按原范围保留两套文本与署名，不能简单改标为“纯 MIT 自研”。

## 下一次实现的优先级

1. 连续主体：先解决截面/纵向连通性，再添加不规则外层薄片。
2. 共同螺旋场：轮廓、粗水纹、泡沫运动使用一致的旋向、上升方向和节距。细法线可以有局部速度差。
   周向坐标另需处理 angle wrap：使用周期纹理、整周重复次数，或 sin/cos 周向嵌入；双相位和瓦片混合不自动消除圆柱的接缝。
3. 水体厚度与折射：分清海面平面和水柱曲面；使用实体穿行距离决定吸收，不以大面积透明裁剪塑形。
4. 飘散水滴：另用速度、寿命与尺寸，不以几片孤立带状网格代替连续水体。

FlowMap 是此处控制纹理运动的方向数据，本身不保证不可压缩或质量守恒。将它用于真实粒子速度时，需要单独选择密度约束/压力投影与边界处理。

这里前三项主要是程序构形/着色实验；真正水片断裂、合并与守恒需要另读 [液体模拟研究](../liquid/findings.md)。
