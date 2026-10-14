# 星空之外的天空研究与选型

用户要求拓展日光、云层、天气或特殊维度时，以已证实的算法和正确空间选择承载方式。现有 `procedural-sky-lab` 0.6.0 使用 `cosmic-sky/6`、188参数、39起点，覆盖方向天空与世界体积云/雨雪/雾/云影/天光，并有双虹、22°冰晕和二维电影黑洞。具体创作读 [authoring.md](authoring.md)，球形大气和原版透明入口读 [advanced-sky.md](advanced-sky.md)。资料库标明原创实作与参考源码；其他物理质量路线按工程选型。

## 选择路线

| 需求 | 起步路线 | 需要额外的契约 |
|---|---|---|
| 蓝天、日落、太阳/月相 | 解析大气或艺术方向色 + 角域日月 | 太阳方向、日周期、linear/HDR、一次tone-map |
| 高质量/动态异星大气 | Bruneton固定大气预计算或Hillaire小LUT | LUT坐标/格式/更新条件、长度单位、重载 |
| 薄云、卷云、夜光云 | 方向/远平面密度、2D FBM/纤维、两相循环流动 | 地平线平行交点、有限距离、风与演化 |
| 体积积云、云海、能穿入云层 | 有限云壳/体积、3D生成噪声、侵蚀和积分 | 世界位置、场景/云深度、步数上限、历史帧 |
| 雾、空气透视 | 有限视线散射与透射 | 场景深度、透明层与独立合成 |
| 云影、曙暮光 | 云沿太阳方向的透射/影图调制积分 | 地形入口、阴影图与光学厚度 |
| 雨后主虹/反虹 | 反太阳轴角域色带与天气显隐 | 云透射、地形遮挡和观察角度 |
| 冰晶日晕/月晕 | 日月轴约22°的有限色散环 | 冰晶条件、太阳/月亮亮度、角宽与遮挡 |
| 二维巨大黑洞/天体 | 天球方向平面、有限暗盘/光环/程序纹理 | 有限角域、防投影与导数发散、独立时间 |
| 异界天空/装饰 | 多方向波域、剪影、维度密度场 | 维度路由；近处体积与远景装饰分开 |

天空合成使用 `L_atmosphere + T_view * L_space`，在同一线性颜色空间做一次曝光/tone-map。不能把贴图采样、算法生成噪声和LUT都叫静态天空图片，也不能把生成纹理输入省略为“完全不使用纹理”。WebGL2可采样/上传3D纹理，但没有通用compute shader；共用生成数据时可做CPU/离线生成，不直接照搬HLSL compute。

## 有出处的优先参考

- [three.js Sky](https://github.com/mrdoob/three.js/blob/f0a67319f396dfeaa05480bbba557e6ac7e6d445/examples/jsm/objects/Sky.js)：该固定版本有解析日光和4 octave程序薄云，MIT。旧教程的Sky.js可能不含云。2D平面云没有可穿越厚度；`direction.xz/direction.y`近地平线需限制有效域/距离，避免再现无界导数细线。
- [Hillaire / Epic](https://github.com/sebh/UnrealEngineSkyAtmosphere/tree/183ead5bdacc701b3b626347a680a2f3cd3d4fbd)：动态小LUT、Sky-View与空气透视，原作MIT。当前代码为HLSL/DX11，z-up/公里/深度[0,1]要映射到MC；192×108、默认可变4–14步是此源码值，不能用论文表2的200×100/30步或论文计时冒充本地性能。
- [Bruneton](https://github.com/ebruneton/precomputed_atmospheric_scattering/tree/34f14e745cff948f4ca3157d1b62a445ffa7286f)：BSD-3-Clause，球形均匀大气的光谱、臭氧、多次散射。运行期太阳方向可变；物质参数变化需更新预计算。不是云/雨/完整云影系统。
- [Sebastian Lague](https://github.com/SebLague/Clouds/tree/fcc997c40d36c7bedf95a294cd2136b8c5127009) 与 [Tyler Dodds](https://github.com/TylerDodds/VolumetricCloudsTutorial/tree/216f465b68c547068d08c029b9bd8788a5f0f1fd)：MIT、有限盒/云壳、形状/侵蚀噪声和Beer/HG；前者主天气采样被注释、无历史重投影，后者有云型/天气输入与重投影。wetness不是雨滴实现，云内自阴影不是地形云影。
- [TileableVolumeNoise](https://github.com/sebh/TileableVolumeNoise/tree/1f879a26b89aaa4b5a7f112f14ec149d08057661)：原作MIT，离线周期Perlin/Worley与多频通道；实时采样生成的3D纹理。实质复制要保留原作和GLM/libtarga等各自通知；原Windows/C++子集不能直接当Java生成器。
- [Godot三类sky material](https://github.com/godotengine/godot/blob/c016f22009aa773f0b2e6fce72baec168746491f/scene/resources/3d/sky_material.cpp)：MIT，区分艺术渐变、解析大气、panorama图片。PhysicalSkyMaterial截断地平线下太阳能量，不是完整暮光；可选night_sky图片不能被隐去。
- [Unity HDRP](https://github.com/Unity-Technologies/Graphics/tree/a7e4c051d256a781ab362c64316b125a1e104694)：用于比较云图/LUT、稀疏步进、云影、两相薄云和froxel雾架构；Unity Companion限定Unity-dependent项目，不直接复制入Forge。名为PROCEDURAL的薄云分支仍有cloudMap图片输入。

Minecraft实际输入/pass的对照可看Solas、Photon、AstraLex、Hysteria、Bliss、Lux和Complementary。参考包按具体快照许可处理：Photon v1.3b本地自定义许可不能被当前网页MIT覆盖；Solas本地云函数已修改，不能认证上游算法；Complementary Reimagined云采图片且首次命中退出，不是完整纯程序密度积分；Lux末地波域有未初始化变量的静态疑点，移植思路时独立重写。

现有便携资料库中的 `sky-library-data.json` 收录18来源/12方向和243源码/光学锚点，不含第三方shader代码、纹理或论文。MIT/BSD仅覆盖其适用代码，论文图/外链图片不自动获得该许可；需要实际复用时仍按所取文件保留通知。限制许可的光影包用于理解方法和接口，不作为可分发shader资产。

## 已实现与研究边界

原创五份GLSL按顺序共享，方向天空一次曝光；世界环境在已曝光场景颜色/实际深度后期合成，属于美术近似。体积云为3D世界高度层，能进入且有平移视差；雾/雨/天光被实际深度截断，同一云密度生成太阳透射云影。新增球形RGB大气LUT、气候雨雪和原版BLOCK玻璃/水入口见 [advanced-sky.md](advanced-sky.md)。没有历史重投影或任意透明RenderType适配。

双虹分别锁定反太阳轴约41.6°/51.6°，外虹反色；22°晕锁定日月方向。光学基础见 [Atmospheric Optics](https://www.atoptics.org.uk/halo/circular.htm) 与 [22° halo形成](https://www.atoptics.org.uk/halo/circ1.htm)，只引用几何事实，不分发网站图文。

本地Solas V3.7b的 endNebula.glsl 有二维方向投影、暗盘/光子环/横向torus和时间滚动噪声；AstraLex存在透明图片式二维巨行星。它们证明远景二维表现这一实现类别，不证明原包已在本实验运行。Solas为已知本地修改快照，许可未确认；参考图片/代码没有复制入原创电影黑洞。

当前验证按工程VALIDATION.md和对应证据目录区分浏览器、独立shader、实际Forge。旧15基础模板、39 /2、26 /3和31 /4协议独立，不用历史通过记录认证新增效果。每次算法改动按风险测固定时间/参数、实际GPU上传、世界矩阵、云内外/长距离/离轴遮挡、动态、资源重载/resize/FOV、GL/depth/state。GPU单pass计时不等于整帧FPS，来源数或sample count不代替实测。

指定三包的实际天体入口及原创七类映射见 [celestial-giants.md](celestial-giants.md)。指定Bliss2.1.2与已有网络Bliss提交分别保留，见 [bliss-end-clouds.md](bliss-end-clouds.md)：中心空洞/环带世界雾、16指数样本、跨帧局部云闪光；源码研究不表示已接入。
