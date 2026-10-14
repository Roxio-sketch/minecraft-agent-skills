# 二维巨型天体与电影黑洞透镜

使用工作台 `giantBodyStyle` 选择新的七类程序方向天体；0保留普通 `celestialType`。这些是无限远二维天幕，球面法线仅用于外观，没有近景平移视差或可进入体积。

| style | 外观 | 起点 |
|---:|---|---|
| 1 | 扁气体巨行星、旋转云带、涡眼与前后遮挡环 | gasgiant |
| 2 | 冰封巨行星、冰盖、裂纹、冷色气层 | icegiant |
| 3 | 破碎月球、透明裂隙、发光破口与轨道碎片 | fragmentmoon |
| 4 | 暗盘遮罩、双色反向流动辉光 | glowingdarkdisk |
| 5 | 两颗不同半径恒星、日冕与有界物质桥 | binarystars |
| 6 | 颗粒光球、临边昏暗、日冕与闭合耀斑环 | flaringstar |
| 7 | 暗盘、径向扭转、三臂旋涡与细环 | vortexdarkstar |

`GiantBodySettings=[giantBodyStyle,giantBodyScale,giantBodySeparation,giantBodyAtmosphere]`。复用天体分组的位置、原角半径、强度、相位、细节、自转和双色；类型1也用行星环参数。scale范围1–4；有效主体半径上限1.18rad（约67.6°），外部日冕/环另有有限支撑。separation范围0–3控制双星/碎片；atmosphere范围0–2控制边缘气层或外焰。自转速度0停止所有新增天体动画。

共享core声明uniform与forward declaration，`skyCelestial`按style>0调用extra中的 `skyGiantCelestial` 后返回。helper没有额外图片，使用原创hash/FBM。基底从yaw直接构造，在精确南北天顶也有限；有限投影导数、递增smoothstep、安全分母与外光窗控制防止远场横线。修改这些保护后应实际测最小/最大尺寸、天顶、正负π、域外和速度零。

## 两维电影黑洞引力透镜

`blackHoleStyle=2` 且黑洞强度>0、`lensingStrength>0`时，在同一稳定切平面上用有限径向逆映射重采样背景星点、银河与星云。`lensingStrength=0`保留电影黑洞环/盘，只关闭背景偏折。非支撑域直接回到原宇宙计算，不为全屏追加一遍背景；极光、流星、普通/巨型天体、大气、日月、薄云和世界介质保留原方向。

这是艺术化方向透镜，没有科学测地线、质量单位、真实多像路径或动态实体引力。类型1仍为独立有限曲率积分，不套电影映射。黑洞环发光不等于世界动态照明或屏幕Bloom。

验证透镜时关闭环、盘、光弧和暗核装饰，保持固定时间，让星点/银河/星云提供可对照结构；变化必须来自背景坐标，而非加亮环。测lens0旧输出、style1旧输出、域外0影响、局部映射有限性和实际调用。完整天空还有其他动画，跨时间动画冻结不能只关闭盘速度。

## 用户指定光影的准确来源

以下都是实际ZIP静态调用链研究，新增算法独立编写。参考包纹理或源码不进入skill资产。

| 包及SHA256 | 真正相关入口与构成 |
|---|---|
| Super Duper Vanilla `c1cd481ac7ec81b6cb82f162b235509568f6f1ab9e416ccfedaee15e79e1a795` | deferred1:279→skyRender:260，280–295暗盘、径向旋转、时间噪声环；world1/world:12–14默认类型2。noise.png256²RGBA；Unused celestialFunctions不是入口。对应原创类型7的视觉构成。 |
| Photon v1.3b `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` | d4_deferred_shading:300→sky:292–363，298–335橙色实体恒星与角向动态耀斑，350–352实际调用。noise.png512²RGBA；大气planet_radius不是可见巨行星。对应原创类型6。 |
| Hysteria Universal1.2.2 `96fd64f9058a964d7014933c54f8d5d8833fca0854b20a82f8c772c5838f76d6` | ZIP根Hysteria-main；deferred4:375→sky:305–324→draw_sun→drawEndSkyFeatures:242–299。洋红/橙反向耀光；END_BLACK_HOLE盘加零RGB，不遮原背景，也不重采样透镜。原创类型4另加真实暗盘遮罩。 |

三包入口未提供类型1/2/3/5的对应实现；这些是额外原创外观。包许可分别为FlameRender Studios1.6、Photon自定义许可、AGNYA，不能以局部MIT片段推定整包开放授权。后续换版本时重新固定ZIP与实际入口。

独立helper验证在RGBA32F中覆盖78案/113检查：七类开关/动态/速度0、正负π、天顶、极值与全天球域外。每类323669远场像素无新增贡献。它只验证隔离helper，最终工作台和Forge的数据上传、入口、资源重载与遮挡另有证据。工程VALIDATION记录最终版本，不能拿旧helper哈希替代集成后的core/extra哈希。
