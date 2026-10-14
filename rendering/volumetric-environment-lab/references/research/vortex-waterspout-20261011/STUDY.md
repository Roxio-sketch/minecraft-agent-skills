# 体积云龙卷风与龙吸水

2026-10-11。用户授权制作两种效果。来源与iterationT文件指纹见sources.json。

## 实际来源

- [NOAA/NWS](https://www.weather.gov/apx/waterspout)：水面斑点、螺旋纹、喷雾环、成熟凝结漏斗和消散。成熟漏斗可呈空心湍流外壳；不等于整柱海水被吸到云底。可见云/雾密度与空气速度是不同概念。
- [SideFX Vortex Force](https://www.sidefx.com/docs/houdini/nodes/dop/vortexforce.html)和[沿曲线属性](https://www.sidefx.com/docs/houdini/nodes/sop/vortexforceattribs)：沿开放中心曲线定义半径、切向速度、作用范围与lift；注意切向线速度和角速度相差半径因子。
- [Physically-Based Simulation of Tornadoes](https://cs.uwaterloo.ca/~jwlwan/papers/WanDing05.pdf)：不可压Navier–Stokes、边界流入和上升流、粒子映射密度、体素splatting。当前制作采用程序密度与射线积分，没有实现该论文流体求解器。
- [Epic体积云文档](https://dev.epicgames.com/documentation/unreal-engine/volumetric-clouds?application_version=4.27)：体积材质/3D纹理、ray marching和自阴影质量代价。使用原则，不复制引擎资源。
- RafaelColpani/UE5-Tornado仅查看作者README及组成/性能说明，未审阅Niagara二进制图、不推断具体算法、不复制资产。没有核验可再分发授权。

iterationT 3.2.0的提取文本未搜索到tornado、waterspout、vortex、龙卷、龙吸入口。WaterWaves.glsl:31起以多尺度移动2D噪声生成波形，:79起以高度差生成法线，:94起做视差坐标；Water_FS真实调用这些函数并引入Ripple。它可以提供水波视觉层次，不能视为龙吸水成品。相关Cloud/NUBIS研究沿用前一报告。

## 制作决策

将漏斗中心定义为随高度弯曲的世界路径，半径以底部/顶部/曲线指数控制。宽云帽与漏斗密度平滑叠加；三维噪声侵蚀轮廓，内部可保留稀薄芯，禁止仅旋转一张锥面贴图。

旋转使用噪声采样域的逆旋转，垂直流动使用y−rise*time；下部向心卷吸采用有限的径向域扰动。所有运动由秒数与固定seed计算，暂停可重放。属于艺术程序场，不满足流体质量守恒；没有完整压力、湿度或水汽凝结模拟。

体积沿真实解析地形/水面命中距离截断，当前L/T合同仍为scene*T+L，自阴影与可调消光分开。颜色保留深浅层次，既有云内闪光可选而不冒充雷电几何。

龙吸水水面是原创连续高度场：多尺度行进波、局部凹陷和隆起环、旋转螺旋纹及外扩波纹。实际射线求交高度，法线从高度差计算；水雾喷环用独立三维环形密度。云柱、水面和喷环共用世界中心、时间和旋转方向，但参数各自独立。

水面使用程序天空反射及Fresnel，当前没有任意场景真实反射/折射/焦散，没有浅水水平动量或FLIP求解。与现有水波skill的游戏网格接入是后续工作，不能把此次程序水面认证为已接入原版水方块。

## 制作与验收范围

在本chat工作台新增独立vortex页面，保留渡劫页。模式为陆地龙卷风和龙吸水；导出新vortex-cloud/1完整作品JSON与PNG，可暂停或固定时间。新增字段不塞入既有world-volume/2，旧Forge示例不自动识别新密度或水面。

验收应覆盖：零形成贡献、同时间重复绘制、密度与透射、旋转/上升单独关闭、内芯与实体前景遮挡、水面高度/法线与水面开关、喷雾环开关、JSON导出再导回、手机布局。WebGL截图只证明工作台实现，未运行原参考软件与游戏客户端。
