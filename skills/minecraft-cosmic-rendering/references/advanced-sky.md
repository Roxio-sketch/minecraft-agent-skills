# 球形大气、气候雨雪与原版透明层

本参考对应可携带工作台 0.6.0、完整 `cosmic-sky/6` 的188字段与39起点，目标 MC1.20.1 / Forge47.4.22 / Java17。旧26预设保留原值；`atmosphereStyle=1`、`precipitationMode=0`、`nativeTransparentMode=0`、`environmentRenderScale=1` 保留既有入口。需要新行为时明确设置对应字段。

## 大气和造表

`atmosphereStyle=2` 使用原创RGB球形介质：Rayleigh/Mie指数高度、臭氧25±15km帐篷层、Beer透射、Lambertian地面反馈，以及各向同性高阶散射几何级数近似。依据Hillaire式5–11和Bruneton坐标映射独立编写；不是完整光谱大气或逐阶收敛的多重散射求解器。太阳强度和天体光度是相对RGB，美术宇宙层没有绝对辐射标定。

几何长度为km，消光系数为km⁻¹。地球默认半径6360km、大气厚100km，分子/微粒标高8/1.2km。宿主观察高度为 `observerAltitude + (cameraY-groundLevel)*.001`，最低.001km；允许在大气外观察，不能把高度钳回大气内部。面向行星的方向按球相交处理，太阳和月盘不能继续使用旧平地 `sun.y` 裁剪。

| GPU表 | 格式与预算 | 重建条件 |
|---|---|---|
| 透射 | 256×64 RGBA16F，64步 | 半径/厚度/标高/介质改变 |
| 高阶散射 | 32×32 RGBA16F，32方向×24步 | 介质或Mie各向异性改变 |
| 天空图集 | 512×128 RGBA16F，左右各256列存L/T，24/40/64步 | 高度、太阳高度、光色/强度、质量等改变 |

总计663552字节（648KiB）。观察方位旋转、固定太阳下时间推进直接查表。日月轮转改变太阳高度时仅重建天空表。正确拼接顺序为 core → extra → physical → weather → environment；core调用 `skyPhysicalReady()`，两张就绪flag均为1才选新后端。不要在运行时缓存函数里保留直接积分分支：实测ANGLE会展平，使查表路径仍承担积分开销。直接积分仅用于造表和诊断。浮点缓冲不可用时保留解析路径并报告原因。

造表函数 `createAtmosphereLuts` 的 update/bind/getTelemetry/dispose 是浏览器参考；Forge对应 `LabAtmosphere`，资源重载后清理表/句柄并使用新ShaderInstance。无每帧readback。独立缓存计时、造表成本和整帧FPS分开报告。

浏览器场景程序按巨天体样式0–7和黑洞路由0–2惰性编译，最多24种，重选已有组合复用缓存。仅专化分派选择，强度仍实时上传。正强度路由必须保留GLSL中的强度条件：最小正Float32可在uniform读回时为正、在GPU算术比较时被当作零，CPU不能代替该条件。只有关闭路由可折叠为关闭分支。修改源码锚点时让专化器明确失败，避免悄悄生成错误程序；失败变体缓存错误并停止重复帧尝试，重建renderer才重试。首次选中仍可能同步阻塞，编译时间不能当作帧耗时。

## 雨雪的世界数据

`precipitationMode`：0旧雨幕，1生物群系，2强制雨，3强制雪。降水量使用既有rainAmount或实际WorldWeather；`precipitationRoof=1`检查屋顶。游戏对每个位置读取 `Biome.getPrecipitationAt(BlockPos)`，它包含降水许可、冷到下雪和高度噪声；不能仅以camera的baseTemperature给全屏染雪。

| transient数据 | 内容 |
|---|---|
| WeatherVolumeSampler | 64×64 RGBA32F，16³、8m体素，Y切片4×4排布；R雨、G雪、A已加载 |
| WorldPrecipOrigin/Geometry | 3D世界原点；[8,16,4,ready] |
| WorldPrecipPresence | 区域内雨/雪许可是否存在，用于跳过空场或单种降水 |
| SurfaceSampler | 128×128 RGBA32F、1m列；R MOTION_BLOCKING屋顶顶面、G已加载 |
| WorldSurfaceGrid | [originX,originZ,1,128] |

缓存跟随相机附近128m区域，降水射线限48m；未加载信息按未知处理，避免屋内漏雨。数据更新按移动/tick预算，不在每帧做16384次世界查询。网页 `weather-data.js` 只是明确标出的演示气候和盒体屋顶，并不冒充Minecraft世界。

雨丝/雪花由有界世界网格遍历、有限粒子支撑、风和飘摆计算。在旋转/平移的独立网格中散布，避免所有格面出现空条带；气候与屋顶仍在粒子真实世界位置查询。质量和AA影响预算与细节。模式0保留旧算法，不用新粒子替换旧作品。

## 透明合成与降分辨率

最终混合后的RGB无法反推玻璃各层。原版BLOCK透明路径在原VBO draw前合成不透明背景，再对原玻璃/水面按实际距离应用同一个环境算子，保留UV、光照、原版fog、排序、alpha、深度写入和Fabulous目标/resolve。背景与表面都包含降水，之后跳过重复的AFTER_LEVEL。不能把背景设成只有雾而删掉玻璃后方雨。

原版着色器/资源/顶点布局或目标不符时，整条路径回到最终合成并记录原因。这里只覆盖原版BLOCK玻璃/水；实体、粒子和自定义透明RenderType需要自己的适配。启用Oculus光影时明确交由实际Iris管线拥有天空/环境，恢复它需要的原版云入口；关闭光影恢复Cosmic。Embeddium绕过原版ShaderInstance时仅禁用此BLOCK适配，按实际目标验证普通环境。该交接不意味着与任意第三方光影同时叠加物理效果。

深度重建使用本帧实际逆投影、逆view rotation及逆view translation。1.20.1走路bob还会把平移写入projection；仅检查view translation为零不能证明没有bob。令 `eyeH=InverseProjection*vec4(0,0,1,0)`，透视投影的光学眼点为 `eyeView=eyeH.xyz/eyeH.w`。无限天空射线取反投影近点减eyeView；世界origin为 `CameraPosition + InverseViewRotation*eyeView + InverseViewTranslation`，表面距离使用重建世界点减同一眼点。不能直接normalize反投影近点，也不能把光学眼点平移重复计入有限距离。正交或非标准投影另推导。

环境比例.5–1只降低环境采样；基底纹理和透明表面仍保持完整分辨率。低pass存有符号环境delta，浮点用RGBA16F；8bit缓存编码零点为128/255。上采样按相对深度拒绝跨轮廓样本，无可用深度候选时保留基底，禁止把天空delta漏到1px前景。低分辨率会改变细节和亮度，不称无损。实际存在细雪且snowSize<.04时强制完整分辨率，telemetry同时显示请求和实际倍率。

游戏验证使用独立advanced checker及其真实GL、纹理、层/VBO和owner记录，不把旧15/39/26/31历史用例通过扩展为本版证明。参考光影包只用于研究；五份算法GLSL原创，Minecraft原版surface wrapper的来源与契约另列在模板内。
