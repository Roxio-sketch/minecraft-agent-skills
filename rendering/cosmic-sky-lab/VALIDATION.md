# 星空工坊 0.6.0 验证

本文件记录0.6.0的实际验收范围。目标MC1.20.1 / Forge47.4.22 / Java17；本轮GPU为RTX4060Ti，浏览器使用ANGLE D3D11。最终配方为cosmic-sky/6，188字段、39预设。此前/5的184字段/32预设证据独立保留。当前证据在 [advanced-sky-20261006](evidence/advanced-sky-20261006)，失败记录保留。

## 大气、雨雪与浏览器

[大气GPU](evidence/advanced-sky-20261006/atmosphere/gpu-validation.json)11项、[独立数值参考](evidence/advanced-sky-20261006/atmosphere/numerical-reference.json)5项、[GPU与参考比较](evidence/advanced-sky-20261006/atmosphere/gpu-numerical-comparison.json)3项通过。验证球相交、km单位、Rayleigh/Mie/臭氧、透射、高阶各向同性近似、地面反馈、介质/太阳/高度缓存失效、重载与极值有限性。三张RGBA16F表共648KiB。

独立1024×640、Q2、每种16个GPU样本，直接天空积分中位.613376ms，暖缓存查询.011264ms；不含每次造表，不等于Minecraft整帧FPS。最初在同一运行时shader保留动态直接积分分支的性能失败也保留，最终将直接路径移至造表/诊断，避免ANGLE展开导致查询仍承担积分开销。

[雨雪算法](evidence/advanced-sky-20261006/weather/weather-report.json)151项通过：世界位置、独立旋转网格、风/下落/飘摆动态、有限粒子支撑、雨雪气候、高度变化、屋顶、地形截断、极值有限性和旧入口。独立640×360、Q2、常量背景的GPU中位：强制雨.411ms、雪.441ms、混合1.043ms、干燥区域.0056ms。早期含背景开销的4–7ms记录保留，不作为降水内核预算。

[实际浏览器合成](evidence/advanced-sky-20261006/weather/renderer-weather-report.json)22项与[delta重采样](evidence/advanced-sky-20261006/weather/delta-upsampling-report.json)4项通过。零效果full/half精确一致，细小雪花受保护时full/请求half精确一致；RGBA16F存有符号delta，字节回退的零点为128/255。无符合深度的样本返回零delta。降分辨率有真实质量损失，未采到的细边可能少受雾，不能声称无损。

[独立源码审查](evidence/advanced-sky-20261006/review/review.md)发现Forge低分辨率归一化极小深度权重会泄漏天空delta到细前景。修复为4×容差硬拒绝；同一GPU夹具从64/64前景泄漏降至0/64，GL错误0。失败夹具保留。

[此前/5 UI](evidence/advanced-sky-20261006/ui/ui-validation.json)11项通过：184控件、32起点、六个新起点实际GPU、四种场景/五种演示气候、完整/4升级保留160值、390px布局与渲染状态。最终/6的188控件、39起点、巨天体与/5升级另立集成报告。资料库真实下载与最终生产服务验收单独记录。

[最终浏览器验收](evidence/advanced-sky-20261006/giant-lens-v6/active-guard-final/optimized-conclusion.json)通过13项界面合同、188控件/39起点、完整/5导入原值保留、七类主体动态及速度0冻结、真实PNG/JSON/27成员资源ZIP。按样式与黑洞路由缓存场景程序，重选已有组合不再编译；新浏览器profile、禁用Chromium GPU shader磁盘缓存的首屏12.554秒，七种无黑洞巨天体首次程序构建3.02–4.21秒，电影黑洞15.81秒。同类组合仍可首次同步等待；未声称清除NVIDIA驱动缓存，也不将编译时间当作FPS。

专化只改分派选择，五份共享算法保持原字节。修复前CPU把最小正Float32的GPU强度条件折成启用，实际RGBA8最多92色阶、RGBA32F最多.3609偏差。修复为正强度路由保留原GLSL条件，仅关闭路由折叠关闭条件。修复后两种最小Float32路由与原完整程序的RGBA8/32F均精确；三组正常黑洞路由也均精确。失败报告完整保留。

整场景严格逐字节诊断仍为false：七巨天体的RGBA8六组精确，旋涡暗星3通道/1LSB；部分RGBA32F有微差。原始报告ok=false，必要合同的限定结论ok=true，没有放宽容差或宣称全部画面完全一致。源码审查和真实注入故障另核对shader/program清理、失败变体缓存、停止重复帧、拒绝旧画面导出与重载恢复；不外推为驱动context loss认证。

## 二维天体、背景透镜与来源学习

[七种巨天体helper](evidence/advanced-sky-20261006/giant-bodies/README.md)在独立768×512 RGBA32F中完成78案/113检查：七类开关、8秒动态、速度0、正负π同方向、南北天顶、最大参数和全天球域外。每类323669远场像素无新增贡献；raw float二进制独立解码通过。新增样式分别为气体巨行星、冰封巨行星、破碎月球、辉光暗盘、双恒星、耀斑恒星、旋涡暗星。最终集成后的core/extra另由工作台与Forge验证，helper哈希不代替集成源哈希。

[电影透镜](evidence/advanced-sky-20261006/cinematic-lens/README.md)的必要合同通过：lens0与改前电影类型精确、曲率类型1与改前精确；实际RGBA8域外123572像素0差异。映射只重采样星点、银河、星云，即使装饰发光全关仍有背景变形；九组浮点极值有限、GL错误0，同一固定Time8三次draw精确。

透镜早期GL1282来自探针sampler/attachment反馈绑定，原失败保留。最终该/5透镜快照的额外strict诊断仍失败：黑洞关闭比较8通道/1LSB、各速度0跨时间14通道/1LSB，raw报告保持ok=false，必要合同与额外诊断分别列出。没有提高容差或改写为全部逐字节一致。最终/6新增天体与实际电影透镜另有完整数据上传和真实draw验证。

[Bliss末地研究](../research/sky-materials-user-20261006/Bliss-End-clouds.md)与32源码锚点经指定ZIP哈希/CRC/范围哈希复核。世界密度中的中心球形空洞、环形扣密度和外围风暴，与colortex4持久状态驱动的随机云内局部闪光分别记录；真实深度积分、地形和透明表面光照的入口也已追踪。这是静态研究，未运行Bliss，也未把其云闪光标为本项目已实现。

指定Super Duper Vanilla、Photon1.3b、Hysteria1.2.2分别提供动态径向噪声环、橙色实体耀斑恒星、双色反向辉光暗盘的参考构成。三个入口未发现新类型1/2/3/5的对应实现，不将额外原创天体归因给它们。Hysteria原暗盘只加零RGB、不遮已有背景，也无引力透镜。参考源码与纹理不进入skill/发行包。最终资料库18来源、12方向、243锚点。

## 游戏与投影

[最终/6 Forge验收](evidence/advanced-sky-20261006/forge/v6/README.md)三个范围均实际重新采集，共71个场景、54520项独立检查、78份原始浮点纹理，0错误。41个原生环境场景、26个天体/透镜场景、4个固定版本兼容场景分别绑定实际加载的12个class和25个资源；67对GL状态快照完整恢复。类字节与冻结开发映射ZIP比较，资源同时与生产重混淆JAR比较，不声称两种class mappings字节相同。

生产JAR SHA256为 `7aba236bc51a2bd1e2f6788c8832df9f0cda62362663356e43e6784f5a934de6`。浏览器宿主之后的编译优化没有改动五份GLSL、Forge Java或实际资源。旧宿主按原报告哈希保存在 `giant-lens-v6/before-optimization/environment-preview.js`；历史52个源指纹中其余51个保持一致。优化后的浏览器另有源哈希绑定验证，不把Forge实测扩大为浏览器宿主证明。

七类天体均在实际游戏时间0/2秒对照中改变。气体天体与电影黑洞的资源重载精确；气体天体世界平移和背面支持域对照精确。FOV70°→100°宽度比.587654与理论.587544符合。电影透镜开关有131079个至少3LSB变化像素，固定背景下吸积盘动态有52931个。实际截图见 [总览](evidence/advanced-sky-20261006/forge/schema6-celestial-minecraft-overview.png)。

固定兼容对象为Oculus1.8.0、Embeddium0.3.31、Photon1.3b。开启/重载光影时Cosmic GPU绘制与构建增量为零；关闭后恢复明确的Embeddium legacy环境路径，两次关闭截图精确。原生透明hook在加载期避让Embeddium；不能外推全部光影包。

960×540的记录云内场景，medium环境pass中位5.151ms，half为1.916ms加合成.019ms，3/4为2.959ms加合成.017ms；每段512个GPU timer样本。此结果描述固定场景的pass，不能作为整帧FPS或跨硬件保证。

[native-corrected](evidence/advanced-sky-20261006/forge/native-corrected-check.json)为此前/5完整41用例验收。初次41采集在AFTER_LEVEL NORMAL优先级截图，实际截图早于最终合成，独立checker拒绝天气和低分辨率证据；更改为LOWEST后完整重新采集。原失败未被覆盖，完整active/inactive uniform、sampler、viewport、attachment、VBO/VAO、稳定绘制窗口及float纹理另有记录。最终/6重新运行41环境用例、26天体用例和4兼容用例，不能用历史/5通过替代。

1.20.1的走路bob把平移写入projection，view translation为零不表示没有摇晃。天空近点需减投影光学眼点；有限环境和原版透明表面使用同一个世界origin与距离。[独立63项投影代数](evidence/advanced-sky-20261006/protocol/optical-eye-validation.json)通过，真实游戏另核验投影和surface数据，不能将数学用例代替GPU证据。

原版BLOCK玻璃和水分别验证Fancy/Fabulous、原几何、UV/光照/alpha/排序、背景与表面ALL环境、最后跳过重复合成、主深度和GL状态恢复。其他实体、粒子和自定义透明RenderType不在该适配范围。

## 旧作品与证据范围

[旧环境兼容结论](evidence/advanced-sky-20261006/weather/compatibility-conclusion.json)保留strictByteExact=false：完整26顺序的一轮25精确，fog有381颜色通道、最大20LSB差异，合成前SceneColor已有391通道、最大35LSB差异。相同SceneColor/SceneDepth输入各自实际环境program时，shafts/fog/cloudnight逐字节一致；旧26参数及共有实际GPUuniform均一致。重复fog在探针后也恢复精确，现象与ANGLE对程序棋盘floor边界的动态舍入一致，没有修改历史基线或扩大容差。

原始该轮depth最大值只计算首100000像素，changed count覆盖全图；原记录如实标注，未来诊断脚本已改为全图。上述比较不证明所有视角或GPU的字节一致。

[最终/6配方协议](evidence/advanced-sky-20261006/protocol-v6/protocol-validation.json)11656项检查通过，jarVerified=true。JS/Python/Java的188字段、范围、四分量映射、实际API/资源ZIP、旧/1–/5显式升级与原值保留均核对；报告绑定当前浏览器宿主4a35bb…与最终JAR。此前90564…宿主的报告和输出完整保存，不覆盖历史。

[skill独立试作](evidence/advanced-sky-20261006/forward-v6/forward-report.json)完成霜紫冰封远星、橙蓝双星暮光和紫白电影透镜三作，共180项检查。实际WebGL渲染、640×360 PNG、先改seed再导回JSON、三个27成员资源ZIP的CRC和逐字节内容均通过；固定时间重复帧精确，两巨天体速度0冻结精确，黑洞透镜开关产生明确背景变化。来源绑定维护skill、独立快照、复制工作台及实际HTTP响应；三作未另跑Minecraft，不扩展既有游戏验收范围。

原15基础模板及19文件保持独立。历史0.4相对/2的7图为6精确、1图1像素1通道1LSB，原passed=false保留；0.5的31用例、旧协议与来源核实也保留，不能充为0.6测试。

[正式服务资料库检查](evidence/advanced-sky-20261006/ui/library-final-validation.json)24项通过：18来源、12效果方向、下载真实索引、390px布局、预设链接与正式API资源逐字节匹配。[最终交付审计](evidence/advanced-sky-20261006/delivery/delivery-validation.json)1110项通过：三个发行物CRC/哈希/完整内容、便携启动及资源生成、已安装skill逐字节一致、两个桌面启动器、实际运行来源与报告绑定、旧19文件和玩家导出保留。正式服务已更新为/6，未覆盖旧失败报告。

球形大气是有单位的RGB介质与高阶近似，未实现完整光谱/逐阶收敛求解；世界环境在已曝光scene上合成。Oculus光影开启时交由它拥有天空环境，关闭后恢复Cosmic，非任意shaderpack同时叠加。无历史重投影、任意透明物体深度认证、其他加载器/版本/GPU或4K性能认证。
