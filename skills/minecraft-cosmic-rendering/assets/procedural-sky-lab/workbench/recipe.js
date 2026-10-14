export const SCHEMA = 'cosmic-sky/6';
// This metadata is also consumed by the Python/Forge resource generator.
export const FIELDS = [
  {key:'giantBodyStyle',label:'二维巨型天体样式',group:'巨型天体',min:0,max:7,step:1,value:0,integer:true,options:{0:'保留普通天体',1:'气态巨行星',2:'冰封巨行星',3:'破碎月球',4:'辉光暗盘',5:'双恒星',6:'耀斑恒星',7:'旋涡暗星'}},
  {key:'giantBodyScale',label:'巨型天体尺寸倍率',group:'巨型天体',min:1,max:4,step:.01,value:1},
  {key:'giantBodySeparation',label:'伴星或碎片间距',group:'巨型天体',min:0,max:3,step:.01,value:1.2},
  {key:'giantBodyAtmosphere',label:'天体气层或外焰',group:'巨型天体',min:0,max:2,step:.01,value:.65},
  {"key":"atmosphereStyle","label":"大气模型","group":"物理大气","min":1,"max":2,"step":1,"value":1,"integer":true,"options":{"1":"解析美术大气","2":"球形散射LUT"}},
  {"key":"planetRadius","label":"行星半径 km","group":"物理大气","min":1000,"max":10000,"step":1,"value":6360},
  {"key":"atmosphereHeight","label":"大气厚度 km","group":"物理大气","min":20,"max":200,"step":1,"value":100},
  {"key":"rayleighHeight","label":"Rayleigh高度尺度 km","group":"物理大气","min":1,"max":20,"step":0.1,"value":8},
  {"key":"mieHeight","label":"Mie高度尺度 km","group":"物理大气","min":0.1,"max":8,"step":0.05,"value":1.2},
  {"key":"rayleighDensity","label":"Rayleigh密度","group":"物理大气","min":0,"max":5,"step":0.01,"value":1},
  {"key":"mieDensity","label":"Mie密度","group":"物理大气","min":0,"max":5,"step":0.01,"value":1},
  {"key":"ozoneDensity","label":"臭氧密度","group":"物理大气","min":0,"max":5,"step":0.01,"value":1},
  {"key":"groundAlbedo","label":"地表反照率","group":"物理大气","min":0,"max":0.9,"step":0.01,"value":0.1},
  {"key":"observerAltitude","label":"观察基准海拔 km","group":"物理大气","min":0,"max":200,"step":0.001,"value":0.07},
  {"key":"multipleScattering","label":"多次散射比例","group":"物理大气","min":0,"max":1,"step":0.01,"value":1},
  {"key":"groundLevel","label":"世界海平面 Y","group":"物理大气","min":-64,"max":512,"step":1,"value":64},
  {"key":"snowDensity","label":"雪花密度","group":"雨雪","min":0.1,"max":3,"step":0.01,"value":0.8},
  {"key":"snowSize","label":"雪花半径 m","group":"雨雪","min":0.01,"max":0.25,"step":0.001,"value":0.06},
  {"key":"snowSpeed","label":"降雪速度","group":"雨雪","min":0.05,"max":3,"step":0.01,"value":0.5},
  {"key":"snowBrightness","label":"雪花亮度","group":"雨雪","min":0,"max":2,"step":0.01,"value":0.9},
  {"key":"precipitationMode","label":"降水类型","group":"雨雪","min":0,"max":3,"step":1,"value":0,"integer":true,"options":{"0":"原雨幕","1":"按生物群系","2":"雨","3":"雪"}},
  {"key":"precipitationRoof","label":"屋顶遮挡降水","group":"雨雪","min":0,"max":1,"step":1,"value":0,"integer":true,"options":{"0":"关闭","1":"开启"}},
  {"key":"snowFlutter","label":"雪花飘摆","group":"雨雪","min":0,"max":2,"step":0.01,"value":0.6},
  {"key":"snowWindStrength","label":"降雪风力","group":"雨雪","min":0,"max":3,"step":0.01,"value":0.5},
  {"key":"nativeTransparentMode","label":"玻璃与水合成","group":"合成与性能","min":0,"max":1,"step":1,"value":0,"integer":true,"options":{"0":"最终场景合成","1":"按透明层分段"}},
  {"key":"environmentRenderScale","label":"环境渲染比例","group":"合成与性能","min":0.5,"max":1,"step":0.05,"value":1},
  {"key":"environmentDepthTolerance","label":"边缘深度容差","group":"合成与性能","min":0,"max":0.1,"step":0.001,"value":0.025},
  {"key":"snowColor","label":"雪花颜色","group":"色彩","color":true,"value":"#eef5ff"},

  {"key":"rainAmount","label":"雨量","group":"天气","min":0,"max":1,"step":0.01,"value":0},
  {"key":"thunderAmount","label":"雷暴强度","group":"天气","min":0,"max":1,"step":0.01,"value":0},
  {"key":"weatherSpeed","label":"天气演化速度","group":"天气","min":0,"max":3,"step":0.01,"value":1},
  {"key":"stormDarkening","label":"风暴遮光","group":"天气","min":0,"max":1,"step":0.01,"value":0.65},
  {"key":"rainDensity","label":"雨丝密度","group":"雨幕","min":0.1,"max":3,"step":0.01,"value":1},
  {"key":"rainSpeed","label":"雨滴速度","group":"雨幕","min":0.1,"max":3,"step":0.01,"value":1},
  {"key":"rainLength","label":"雨丝长度","group":"雨幕","min":0.03,"max":1,"step":0.01,"value":0.22},
  {"key":"rainBrightness","label":"雨丝亮度","group":"雨幕","min":0,"max":2,"step":0.01,"value":0.6},
  {"key":"rainWind","label":"降雨风向 °","group":"天气","min":-180,"max":180,"step":1,"value":25},
  {"key":"weatherFollowWorld","label":"跟随游戏天气","group":"天气","min":0,"max":1,"step":1,"value":0,"integer":true,"options":{"0":"使用配方天气","1":"跟随游戏天气"}},
  {"key":"lightningFrequency","label":"雷光频率","group":"天气","min":0,"max":3,"step":0.01,"value":0.7},
  {"key":"lightningDuration","label":"雷光持续秒","group":"天气","min":0.05,"max":1.5,"step":0.01,"value":0.28},
  {"key":"fogStrength","label":"场景雾强度","group":"雾与空气透视","min":0,"max":3,"step":0.01,"value":0},
  {"key":"fogDensity","label":"每米雾密度","group":"雾与空气透视","min":0,"max":0.03,"step":0.0001,"value":0.002},
  {"key":"fogHeightFalloff","label":"雾高度衰减","group":"雾与空气透视","min":0,"max":0.1,"step":0.001,"value":0.015},
  {"key":"fogBaseHeight","label":"雾层基准高度","group":"雾与空气透视","min":-128,"max":384,"step":1,"value":64},
  {"key":"fogNoise","label":"雾密度变化","group":"雾与空气透视","min":0,"max":1,"step":0.01,"value":0.3},
  {"key":"fogScale","label":"雾形尺度","group":"雾与空气透视","min":0.001,"max":0.05,"step":0.001,"value":0.008},
  {"key":"fogSpeed","label":"雾流动速度","group":"雾与空气透视","min":0,"max":3,"step":0.01,"value":0.4},
  {"key":"fogDistance","label":"最大雾积分距离","group":"雾与空气透视","min":16,"max":2048,"step":1,"value":512},
  {"key":"volumeCloudStrength","label":"体积云强度","group":"体积云","min":0,"max":2,"step":0.01,"value":0},
  {"key":"volumeCloudCoverage","label":"云海覆盖率","group":"体积云","min":0,"max":1,"step":0.01,"value":0.48},
  {"key":"volumeCloudDensity","label":"云体密度","group":"体积云","min":0,"max":3,"step":0.01,"value":0.8},
  {"key":"volumeCloudScale","label":"世界云形尺度","group":"体积云","min":0.001,"max":0.025,"step":0.0001,"value":0.004},
  {"key":"volumeCloudBase","label":"云底世界高度","group":"体积云","min":32,"max":384,"step":1,"value":150},
  {"key":"volumeCloudThickness","label":"云层厚度","group":"体积云","min":8,"max":200,"step":1,"value":70},
  {"key":"volumeCloudDistance","label":"云体最远距离","group":"体积云","min":64,"max":2048,"step":1,"value":1000},
  {"key":"volumeCloudErosion","label":"细节侵蚀","group":"体积云","min":0,"max":1,"step":0.01,"value":0.25},
  {"key":"volumeCloudSpeed","label":"云风速度","group":"体积云","min":0,"max":3,"step":0.01,"value":0.6},
  {"key":"volumeCloudWind","label":"云风向 °","group":"体积云","min":-180,"max":180,"step":1,"value":25},
  {"key":"volumeCloudEvolution","label":"云体演化","group":"体积云","min":0,"max":2,"step":0.01,"value":0.15},
  {"key":"volumeCloudAnisotropy","label":"云散射方向性","group":"体积云","min":0,"max":0.85,"step":0.01,"value":0.65},
  {"key":"volumeCloudAmbient","label":"云环境照明","group":"云影与天光","min":0,"max":2,"step":0.01,"value":0.6},
  {"key":"volumeCloudSunlight","label":"云太阳照明","group":"云影与天光","min":0,"max":3,"step":0.01,"value":1},
  {"key":"cloudShadowStrength","label":"地形云影强度","group":"云影与天光","min":0,"max":1,"step":0.01,"value":0},
  {"key":"lightShaftStrength","label":"云隙天光强度","group":"云影与天光","min":0,"max":3,"step":0.01,"value":0},
  {"key":"lightShaftDensity","label":"光柱介质密度","group":"云影与天光","min":0,"max":1,"step":0.01,"value":0.12},
  {"key":"lightShaftDistance","label":"光柱积分距离","group":"云影与天光","min":16,"max":1024,"step":1,"value":320},
  {"key":"cloudShadowSoftness","label":"云影柔度","group":"云影与天光","min":0.1,"max":4,"step":0.01,"value":1},
  {"key":"lightShaftAnisotropy","label":"天光方向性","group":"云影与天光","min":0,"max":0.9,"step":0.01,"value":0.75},
  {"key":"rainbowStrength","label":"主虹强度","group":"彩虹","min":0,"max":3,"step":0.01,"value":0},
  {"key":"rainbowWidth","label":"色带宽度 °","group":"彩虹","min":0.25,"max":4,"step":0.05,"value":1},
  {"key":"secondaryRainbow","label":"反虹强度","group":"彩虹","min":0,"max":1.5,"step":0.01,"value":0.55},
  {"key":"rainbowWetness","label":"空气水滴条件","group":"彩虹","min":0,"max":1,"step":0.01,"value":0.7},
  {"key":"haloStrength","label":"冰晶晕强度","group":"冰晶晕","min":0,"max":3,"step":0.01,"value":0},
  {"key":"haloWidth","label":"冰晕宽度 °","group":"冰晶晕","min":0.2,"max":4,"step":0.05,"value":1},
  {"key":"haloIce","label":"冰晶条件","group":"冰晶晕","min":0,"max":1,"step":0.01,"value":1},
  {"key":"haloMoon","label":"日晕或月晕","group":"冰晶晕","min":0,"max":1,"step":1,"value":0,"integer":true,"options":{"0":"太阳冰晶晕","1":"月亮冰晶晕"}},
  {"key":"specialStrength","label":"异界层强度","group":"异界天空","min":0,"max":3,"step":0.01,"value":0},
  {"key":"specialStyle","label":"异界样式","group":"异界天空","min":1,"max":3,"step":1,"value":1,"integer":true,"options":{"1":"空间裂幕","2":"赤焰风暴","3":"方向剪影"}},
  {"key":"specialScale","label":"异界形态尺度","group":"异界天空","min":0.3,"max":6,"step":0.01,"value":1},
  {"key":"specialSpeed","label":"异界演化速度","group":"异界天空","min":0,"max":3,"step":0.01,"value":0.6},
  {"key":"specialDistortion","label":"波域扭曲","group":"异界天空","min":0,"max":2,"step":0.01,"value":0.5},
  {"key":"specialBandWidth","label":"裂幕宽度","group":"异界天空","min":0.03,"max":0.8,"step":0.005,"value":0.25},
  {"key":"birdDensity","label":"远景鸟群密度","group":"异界天空","min":0,"max":1,"step":0.01,"value":0},
  {"key":"birdSpeed","label":"鸟群移动速度","group":"异界天空","min":0,"max":3,"step":0.01,"value":0.7},
  {"key":"blackHoleStyle","label":"黑洞二维或透镜类型","group":"黑洞","min":1,"max":2,"step":1,"value":1,"integer":true,"options":{"1":"曲率积分透镜","2":"二维电影光环"}},
  {"key":"blackHoleRingBrightness","label":"电影光环亮度","group":"黑洞","min":0,"max":4,"step":0.01,"value":1},
  {"key":"blackHoleRingWidth","label":"电影光环宽度","group":"黑洞","min":0.01,"max":0.25,"step":0.005,"value":0.06},
  {"key":"blackHoleGlow","label":"电影光晕强度","group":"黑洞","min":0,"max":3,"step":0.01,"value":0.9},
  {"key":"blackHoleArc","label":"弯曲光弧强度","group":"黑洞","min":0,"max":2,"step":0.01,"value":1},
  {"key":"blackHoleStreak","label":"横向亮盘强度","group":"黑洞","min":0,"max":3,"step":0.01,"value":1.1},
  {"key":"blackHoleContrast","label":"黑洞暗核对比","group":"黑洞","min":0,"max":2,"step":0.01,"value":1.1},
  {"key":"blackHoleNoise","label":"盘纹细节","group":"黑洞","min":0,"max":3,"step":0.01,"value":1},
  {"key":"fogColor","label":"雾颜色","group":"色彩","color":true,"value":"#8199b4"},
  {"key":"volumeCloudColor","label":"云体颜色","group":"色彩","color":true,"value":"#ffffff"},
  {"key":"rainColor","label":"雨丝颜色","group":"色彩","color":true,"value":"#b3c8dc"},
  {"key":"lightningColor","label":"雷光颜色","group":"色彩","color":true,"value":"#e1e6ff"},
  {"key":"specialColorA","label":"异界主色","group":"色彩","color":true,"value":"#5b389a"},
  {"key":"specialColorB","label":"异界辉光色","group":"色彩","color":true,"value":"#df9585"},

  {key:'seed',label:'星空种子',group:'整体',min:0,max:9999,step:1,value:2718,integer:true},
  {key:'quality',label:'采样质量',group:'整体',min:1,max:3,step:1,value:2,integer:true},
  {key:'exposure',label:'整体曝光',group:'整体',min:0.2,max:3,step:0.01,value:1.15},
  {key:'atmosphereStrength',label:'大气启用与强度',group:'大气',min:0,max:1,step:0.01,value:0},
  {key:'turbidity',label:'气溶胶浑浊度',group:'大气',min:1,max:10,step:0.05,value:2},
  {key:'rayleighStrength',label:'蓝天散射强度',group:'大气',min:0,max:4,step:0.01,value:1},
  {key:'mieStrength',label:'太阳附近散射',group:'大气',min:0,max:3,step:0.01,value:1},
  {key:'mieAnisotropy',label:'散射朝向集中度',group:'大气',min:0,max:0.9,step:0.01,value:0.76},
  {key:'nightAmbient',label:'夜间大气微光',group:'大气',min:0,max:0.3,step:0.005,value:0.04},
  {key:'twilightStrength',label:'晨昏晚霞强度',group:'大气',min:0,max:2,step:0.01,value:1},
  {key:'sunBrightness',label:'太阳亮度',group:'太阳',min:0,max:5,step:0.01,value:1.3},
  {key:'sunElevation',label:'太阳初始仰角 °',group:'太阳',min:-18,max:85,step:0.1,value:25},
  {key:'sunYaw',label:'太阳方位 °',group:'太阳',min:-180,max:180,step:1,value:15},
  {key:'sunSize',label:'太阳角半径 °',group:'太阳',min:0.15,max:4,step:0.05,value:0.45},
  {key:'cycleSpeed',label:'自动昼夜速度',group:'昼夜',min:0,max:2,step:0.01,value:0},
  {key:'moonBrightness',label:'月亮亮度',group:'月亮',min:0,max:3,step:0.01,value:0.8},
  {key:'moonYaw',label:'月亮方位 °',group:'月亮',min:-180,max:180,step:1,value:-130},
  {key:'moonElevation',label:'月亮初始仰角 °',group:'月亮',min:-18,max:85,step:0.1,value:30},
  {key:'moonSize',label:'月亮角半径 °',group:'月亮',min:0.15,max:6,step:0.05,value:1.2},
  {key:'moonPhase',label:'月相 °（0 为满月）',group:'月亮',min:-180,max:180,step:1,value:55},
  {key:'moonDetail',label:'月面细节',group:'月亮',min:1,max:12,step:0.05,value:4},
  {key:'moonHalo',label:'月亮外晕强度',group:'月亮',min:0,max:2,step:0.01,value:0.35},
  {key:'moonSpeed',label:'月面缓慢自转',group:'月亮',min:0,max:1,step:0.005,value:0.08},
  {key:'thinCloudStrength',label:'薄云强度',group:'薄云',min:0,max:2,step:0.01,value:0},
  {key:'thinCloudCoverage',label:'薄云覆盖率',group:'薄云',min:0,max:1,step:0.01,value:0.45},
  {key:'thinCloudScale',label:'薄云纹理尺度',group:'薄云',min:0.25,max:4,step:0.01,value:0.85},
  {key:'thinCloudHeight',label:'薄云远平面高度',group:'薄云',min:0.3,max:3,step:0.01,value:1},
  {key:'thinCloudSpeed',label:'薄云风速',group:'薄云',min:0,max:2,step:0.01,value:0.6},
  {key:'thinCloudWind',label:'薄云风向 °',group:'薄云',min:-180,max:180,step:1,value:25},
  {key:'thinCloudEvolution',label:'云形演化速度',group:'薄云',min:0,max:2,step:0.01,value:0.35},
  {key:'thinCloudSilver',label:'背光银边强度',group:'薄云',min:0,max:3,step:0.01,value:1},
  {key:'starDensity',label:'星点密度',group:'恒星',min:0,max:1,step:0.01,value:0.38},
  {key:'starBrightness',label:'星点亮度',group:'恒星',min:0,max:4,step:0.01,value:1.35},
  {key:'starSize',label:'星点大小',group:'恒星',min:0.3,max:2.5,step:0.01,value:1},
  {key:'twinkleAmount',label:'闪烁幅度',group:'恒星',min:0,max:1,step:0.01,value:0.65},
  {key:'twinkleSpeed',label:'闪烁速度',group:'恒星',min:0,max:4,step:0.01,value:1.4},
  {key:'galacticYaw',label:'银河方向',group:'银河',min:-180,max:180,step:1,value:25},
  {key:'galacticTilt',label:'银河倾角',group:'银河',min:-85,max:85,step:1,value:32},
  {key:'bandWidth',label:'银河宽度',group:'银河',min:0.04,max:0.8,step:0.005,value:0.26},
  {key:'galaxyStrength',label:'银河亮度',group:'银河',min:0,max:4,step:0.01,value:1.2},
  {key:'dustStrength',label:'暗尘遮光',group:'银河',min:0,max:3,step:0.01,value:1.35},
  {key:'nebulaStrength',label:'星云亮度',group:'星云',min:0,max:4,step:0.01,value:0.75},
  {key:'nebulaScale',label:'星云细碎度',group:'星云',min:1,max:10,step:0.05,value:3.5},
  {key:'nebulaSpeed',label:'星云流动',group:'星云',min:0,max:1,step:0.005,value:0.4},
  {key:'auroraStrength',label:'极光亮度',group:'极光',min:0,max:4,step:0.01,value:0.65},
  {key:'auroraHeight',label:'极光仰角',group:'极光',min:0.05,max:0.95,step:0.005,value:0.38},
  {key:'auroraWidth',label:'极光高度范围',group:'极光',min:0.02,max:0.45,step:0.005,value:0.2},
  {key:'auroraCurtains',label:'极光帘幕密度',group:'极光',min:0.3,max:3,step:0.01,value:1.1},
  {key:'auroraSpeed',label:'极光流动',group:'极光',min:0,max:3,step:0.01,value:0.35},
  {key:'meteorRate',label:'流星频度',group:'流星',min:0,max:8,step:0.1,value:1.8},
  {key:'meteorBrightness',label:'流星亮度',group:'流星',min:0,max:5,step:0.01,value:2.3},
  {key:'meteorSpeed',label:'流星速度',group:'流星',min:0.1,max:3,step:0.01,value:1},
  {key:'meteorLength',label:'流星尾长',group:'流星',min:0.02,max:0.4,step:0.005,value:0.16},
  {key:'horizonGlow',label:'地平线微光',group:'整体',min:0,max:1,step:0.01,value:0.08},
  {key:'celestialStrength',label:'天体亮度',group:'天体',min:0,max:3,step:0.01,value:1.1},
  {key:'celestialSize',label:'天体角半径 °',group:'天体',min:2,max:28,step:0.1,value:12},
  {key:'celestialYaw',label:'天体方位 °',group:'天体',min:-180,max:180,step:1,value:28},
  {key:'celestialPitch',label:'天体仰角 °',group:'天体',min:-70,max:85,step:1,value:35},
  {key:'celestialType',label:'天体表面',group:'天体',min:1,max:3,step:1,value:1,integer:true,options:{1:'气态巨行星',2:'岩质月球',3:'发光恒星'}},
  {key:'celestialPhase',label:'天体光照相位 °',group:'天体',min:-180,max:180,step:1,value:38},
  {key:'celestialDetail',label:'天体表面细节',group:'天体',min:1,max:12,step:0.05,value:4.2},
  {key:'celestialSpeed',label:'天体自转',group:'天体',min:0,max:1,step:0.005,value:0.15},
  {key:'ringStrength',label:'行星光环亮度',group:'天体',min:0,max:2,step:0.01,value:0.75},
  {key:'ringTilt',label:'光环倾角 °',group:'天体',min:0,max:85,step:1,value:62},
  {key:'ringWidth',label:'光环外径倍率',group:'天体',min:1.3,max:3,step:0.01,value:1.9},
  {key:'blackHoleStrength',label:'黑洞吸积盘亮度',group:'黑洞',min:0,max:4,step:0.01,value:0},
  {key:'blackHoleSize',label:'黑洞暗核角半径 °',group:'黑洞',min:1,max:16,step:0.1,value:6},
  {key:'blackHoleYaw',label:'黑洞方位 °',group:'黑洞',min:-180,max:180,step:1,value:-24},
  {key:'blackHolePitch',label:'黑洞仰角 °',group:'黑洞',min:-70,max:85,step:1,value:30},
  {key:'diskTilt',label:'吸积盘倾角 °',group:'黑洞',min:0,max:85,step:1,value:72},
  {key:'diskWidth',label:'吸积盘外径倍率',group:'黑洞',min:3.5,max:9,step:0.05,value:6.5},
  {key:'diskSpeed',label:'吸积盘旋转与内流',group:'黑洞',min:0,max:3,step:0.01,value:1},
  {key:'lensingStrength',label:'背景偏折强度',group:'黑洞',min:0,max:1.5,step:0.01,value:1},
  {key:'backgroundColor',label:'背景底色',group:'色彩',color:true,value:'#020511'},
  {key:'starCoolColor',label:'冷色恒星',group:'色彩',color:true,value:'#9acaff'},
  {key:'starWarmColor',label:'暖色恒星',group:'色彩',color:true,value:'#ffd8a6'},
  {key:'galaxyColor',label:'银河主色',group:'色彩',color:true,value:'#aebedc'},
  {key:'nebulaColorA',label:'星云色一',group:'色彩',color:true,value:'#3659b4'},
  {key:'nebulaColorB',label:'星云色二',group:'色彩',color:true,value:'#b75cbe'},
  {key:'auroraColorA',label:'极光下缘',group:'色彩',color:true,value:'#52e8b0'},
  {key:'auroraColorB',label:'极光上缘',group:'色彩',color:true,value:'#8460ef'},
  {key:'celestialColorA',label:'天体主色',group:'色彩',color:true,value:'#5475b7'},
  {key:'celestialColorB',label:'天体纹理色',group:'色彩',color:true,value:'#e0bcb9'},
  {key:'ringColor',label:'光环颜色',group:'色彩',color:true,value:'#b3c9ea'},
  {key:'diskColorA',label:'吸积盘外缘颜色',group:'色彩',color:true,value:'#db4a12'},
  {key:'diskColorB',label:'吸积盘内缘颜色',group:'色彩',color:true,value:'#fff1b7'},
  {key:'skyTint',label:'大气整体染色',group:'色彩',color:true,value:'#ffffff'},
  {key:'sunColor',label:'太阳色',group:'色彩',color:true,value:'#fff1d1'},
  {key:'moonColor',label:'月亮色',group:'色彩',color:true,value:'#bed4ee'},
  {key:'thinCloudColor',label:'薄云色',group:'色彩',color:true,value:'#edf1fa'},
];
export const LEGACY_V4_KEYS = ["rainAmount","thunderAmount","weatherSpeed","stormDarkening","rainDensity","rainSpeed","rainLength","rainBrightness","rainWind","weatherFollowWorld","lightningFrequency","lightningDuration","fogStrength","fogDensity","fogHeightFalloff","fogBaseHeight","fogNoise","fogScale","fogSpeed","fogDistance","volumeCloudStrength","volumeCloudCoverage","volumeCloudDensity","volumeCloudScale","volumeCloudBase","volumeCloudThickness","volumeCloudDistance","volumeCloudErosion","volumeCloudSpeed","volumeCloudWind","volumeCloudEvolution","volumeCloudAnisotropy","volumeCloudAmbient","volumeCloudSunlight","cloudShadowStrength","lightShaftStrength","lightShaftDensity","lightShaftDistance","cloudShadowSoftness","lightShaftAnisotropy","rainbowStrength","rainbowWidth","secondaryRainbow","rainbowWetness","haloStrength","haloWidth","haloIce","haloMoon","specialStrength","specialStyle","specialScale","specialSpeed","specialDistortion","specialBandWidth","birdDensity","birdSpeed","blackHoleStyle","blackHoleRingBrightness","blackHoleRingWidth","blackHoleGlow","blackHoleArc","blackHoleStreak","blackHoleContrast","blackHoleNoise","fogColor","volumeCloudColor","rainColor","lightningColor","specialColorA","specialColorB","seed","quality","exposure","atmosphereStrength","turbidity","rayleighStrength","mieStrength","mieAnisotropy","nightAmbient","twilightStrength","sunBrightness","sunElevation","sunYaw","sunSize","cycleSpeed","moonBrightness","moonYaw","moonElevation","moonSize","moonPhase","moonDetail","moonHalo","moonSpeed","thinCloudStrength","thinCloudCoverage","thinCloudScale","thinCloudHeight","thinCloudSpeed","thinCloudWind","thinCloudEvolution","thinCloudSilver","starDensity","starBrightness","starSize","twinkleAmount","twinkleSpeed","galacticYaw","galacticTilt","bandWidth","galaxyStrength","dustStrength","nebulaStrength","nebulaScale","nebulaSpeed","auroraStrength","auroraHeight","auroraWidth","auroraCurtains","auroraSpeed","meteorRate","meteorBrightness","meteorSpeed","meteorLength","horizonGlow","celestialStrength","celestialSize","celestialYaw","celestialPitch","celestialType","celestialPhase","celestialDetail","celestialSpeed","ringStrength","ringTilt","ringWidth","blackHoleStrength","blackHoleSize","blackHoleYaw","blackHolePitch","diskTilt","diskWidth","diskSpeed","lensingStrength","backgroundColor","starCoolColor","starWarmColor","galaxyColor","nebulaColorA","nebulaColorB","auroraColorA","auroraColorB","celestialColorA","celestialColorB","ringColor","diskColorA","diskColorB","skyTint","sunColor","moonColor","thinCloudColor"];
export const LEGACY_V3_KEYS = ["seed", "quality", "exposure", "atmosphereStrength", "turbidity", "rayleighStrength", "mieStrength", "mieAnisotropy", "nightAmbient", "twilightStrength", "sunBrightness", "sunElevation", "sunYaw", "sunSize", "cycleSpeed", "moonBrightness", "moonYaw", "moonElevation", "moonSize", "moonPhase", "moonDetail", "moonHalo", "moonSpeed", "thinCloudStrength", "thinCloudCoverage", "thinCloudScale", "thinCloudHeight", "thinCloudSpeed", "thinCloudWind", "thinCloudEvolution", "thinCloudSilver", "starDensity", "starBrightness", "starSize", "twinkleAmount", "twinkleSpeed", "galacticYaw", "galacticTilt", "bandWidth", "galaxyStrength", "dustStrength", "nebulaStrength", "nebulaScale", "nebulaSpeed", "auroraStrength", "auroraHeight", "auroraWidth", "auroraCurtains", "auroraSpeed", "meteorRate", "meteorBrightness", "meteorSpeed", "meteorLength", "horizonGlow", "celestialStrength", "celestialSize", "celestialYaw", "celestialPitch", "celestialType", "celestialPhase", "celestialDetail", "celestialSpeed", "ringStrength", "ringTilt", "ringWidth", "blackHoleStrength", "blackHoleSize", "blackHoleYaw", "blackHolePitch", "diskTilt", "diskWidth", "diskSpeed", "lensingStrength", "backgroundColor", "starCoolColor", "starWarmColor", "galaxyColor", "nebulaColorA", "nebulaColorB", "auroraColorA", "auroraColorB", "celestialColorA", "celestialColorB", "ringColor", "diskColorA", "diskColorB", "skyTint", "sunColor", "moonColor", "thinCloudColor"];
const ENVIRONMENT_GROUPS = new Set(['大气','太阳','昼夜','月亮','薄云']);
const ENVIRONMENT_COLORS = new Set(['skyTint','sunColor','moonColor','thinCloudColor']);
export const LEGACY_V2_KEYS = FIELDS.filter(f=>LEGACY_V3_KEYS.includes(f.key)&&!ENVIRONMENT_GROUPS.has(f.group)&&!ENVIRONMENT_COLORS.has(f.key)).map(f=>f.key);
export const LEGACY_V1_KEYS = FIELDS.filter(f=>LEGACY_V2_KEYS.includes(f.key)&&!['天体','黑洞'].includes(f.group)&&!['celestialColorA','celestialColorB','ringColor','diskColorA','diskColorB'].includes(f.key)).map(f=>f.key);
export const DEFAULT = Object.freeze(Object.fromEntries(FIELDS.map(f=>[f.key,f.value])));
export const LEGACY_V5_KEYS = Object.freeze(FIELDS.map(f=>f.key).filter(k=>!['giantBodyStyle','giantBodyScale','giantBodySeparation','giantBodyAtmosphere'].includes(k)));
export function makeRecipe(overrides={}) { return {schema:SCHEMA,parameters:{...DEFAULT,...overrides}}; }
export function validateRecipe(input) {
  if (!input || typeof input!=='object' || Array.isArray(input) || input.schema!==SCHEMA ||
      !input.parameters || typeof input.parameters!=='object' || Array.isArray(input.parameters)) throw new Error(`需要 ${SCHEMA} 配方`);
  if(Object.keys(input).some(k=>!['schema','parameters'].includes(k))) throw new Error('配方包含未知顶层字段');
  const p=input.parameters, expected=new Set(FIELDS.map(f=>f.key));
  if(Object.keys(p).some(k=>!expected.has(k))) throw new Error('配方包含未知参数');
  const clean={};
  for(const f of FIELDS) {
    const v=p[f.key];
    if(f.color) { if(typeof v!=='string'||!/^#[0-9a-fA-F]{6}$/.test(v)) throw new Error(`${f.label} 应为 #RRGGBB`); clean[f.key]=v.toLowerCase(); }
    else { if(typeof v!=='number'||!Number.isFinite(v)||v<f.min||v>f.max||(f.integer&&!Number.isInteger(v))) throw new Error(`${f.label} 应在 ${f.min}–${f.max} 范围内`); clean[f.key]=v; }
  }
  return {schema:SCHEMA,parameters:clean};
}
export function parseRecipe(text, {upgradeLegacy=false}={}) {
  if(typeof text!=='string') throw new Error('配方 JSON 需要文本');
  const parsed=JSON.parse(text);
  const tokens=text.match(/"(?:\\[\s\S]|[^"\\])*"|[{}\[\]:,]|[^\s{}\[\]:,]+/g)||[];
  let position=0;
  function scan() {
    const token=tokens[position++];
    if(token==='{') {
      const keys=new Set();
      while(tokens[position]!=='}') {
        const key=JSON.parse(tokens[position++]);
        if(keys.has(key))throw new Error(`JSON 包含重复字段：${key}`);
        keys.add(key);position++;scan();
        if(tokens[position]!==',')break;
        position++;
      }
      position++;
    }else if(token==='[') {
      while(tokens[position]!==']') {scan();if(tokens[position]!==',')break;position++;}
      position++;
    }
  }
  scan();
  if(upgradeLegacy && ['cosmic-sky/1','cosmic-sky/2','cosmic-sky/3','cosmic-sky/4','cosmic-sky/5'].includes(parsed?.schema)) {
    const first=parsed.schema==='cosmic-sky/1', third=['cosmic-sky/3','cosmic-sky/4','cosmic-sky/5'].includes(parsed.schema), oldKeys=parsed.schema==='cosmic-sky/5'?LEGACY_V5_KEYS:parsed.schema==='cosmic-sky/4'?LEGACY_V4_KEYS:third?LEGACY_V3_KEYS:first?LEGACY_V1_KEYS:LEGACY_V2_KEYS;
    if(Object.keys(parsed).length!==2 || !parsed.parameters || typeof parsed.parameters!=='object' || Array.isArray(parsed.parameters) || Object.keys(parsed.parameters).length!==oldKeys.length || oldKeys.some(k=>!Object.hasOwn(parsed.parameters,k))) throw new Error('旧版配方不完整或包含未知字段');
    return validateRecipe(makeRecipe({...parsed.parameters,...(first?{celestialStrength:0,ringStrength:0,blackHoleStrength:0}:{}),...(third?{}:{atmosphereStrength:0,thinCloudStrength:0})}));
  }
  return validateRecipe(parsed);
}
export const PRESETS = [
 {id:'milkyway',name:'静谧银河',description:'闪烁群星、流动星云与带环巨行星',recipe:makeRecipe()},
 {id:'aurora',name:'极地夜幕',description:'绿色帘幕、紫色上缘与冷色月球',recipe:makeRecipe({seed:108,galaxyStrength:0.55,nebulaStrength:0.25,auroraStrength:2,auroraHeight:0.42,auroraWidth:0.24,auroraSpeed:0.55,auroraCurtains:1.6,meteorRate:0.8,celestialType:2,celestialSize:10,celestialPhase:72,ringStrength:0,celestialColorA:'#6c8ca8',celestialColorB:'#c2d2df'})},
 {id:'rose',name:'玫瑰星海',description:'流动玫瑰星云、暖色银河和金色星环',recipe:makeRecipe({seed:932,auroraStrength:0,nebulaStrength:1.65,nebulaColorA:'#563796',nebulaColorB:'#db648c',galaxyColor:'#ffcda7',galacticTilt:-26,galacticYaw:-10,nebulaSpeed:0.5,starDensity:0.3,celestialColorA:'#ad6d76',celestialColorB:'#ffb3a0',ringColor:'#ffd3ab'})},
 {id:'deep',name:'深空群星',description:'低亮背景、闪烁星群与半明半暗的岩质天体',recipe:makeRecipe({seed:47,galaxyStrength:0.2,nebulaStrength:0.12,auroraStrength:0,horizonGlow:0,starBrightness:1.8,starDensity:0.55,meteorRate:0.5,celestialType:2,celestialSize:16,celestialPhase:100,ringStrength:0,celestialColorA:'#465676',celestialColorB:'#a5b1c5'})},
 {id:'storm',name:'流星夜',description:'强化流星频度，在巨行星与银河下观察尾迹',recipe:makeRecipe({seed:715,meteorRate:8,meteorBrightness:3.6,meteorSpeed:1.2,meteorLength:0.26,auroraStrength:0.25,galaxyStrength:0.8,starDensity:0.25,celestialSize:9,celestialYaw:-24,celestialPitch:30})},
 {id:'cyan',name:'青蓝星云',description:'流动蓝色云气、明亮星群与辉光恒星',recipe:makeRecipe({seed:619,nebulaStrength:1.4,nebulaScale:5,nebulaColorA:'#125e80',nebulaColorB:'#60adc9',galaxyColor:'#a8d2ef',auroraStrength:0.15,galacticTilt:48,galacticYaw:65,celestialType:3,celestialSize:8,celestialColorA:'#2c91e0',celestialColorB:'#c4f4ff',ringStrength:0})},
 {id:'blackhole',name:'黑洞脉动',description:'弯曲星光、旋转内流的吸积盘与远处巨行星',recipe:makeRecipe({seed:812,blackHoleStrength:1.8,blackHoleSize:7,blackHoleYaw:-16,blackHolePitch:28,diskSpeed:1.2,celestialSize:7,celestialYaw:33,celestialPitch:40,nebulaStrength:0.5,auroraStrength:0,galaxyStrength:0.65})},
 {id:'sunny',name:'晴空流云',english:'CLEAR DAY',color:'#8bd5ec',description:'蓝色大气、明亮太阳与随风缓慢演化的白色薄云',recipe:makeRecipe({atmosphereStrength:1,sunElevation:38,sunYaw:8,thinCloudStrength:0.65,thinCloudCoverage:0.24,celestialStrength:0,ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:0.25,galaxyStrength:0.25})},
 {id:'sunset',name:'落日金霞',english:'GOLDEN HOUR',color:'#eda579',description:'低角度暖色太阳、散射晚霞与橙金云边',recipe:makeRecipe({atmosphereStrength:1,sunElevation:4,sunYaw:5,sunSize:1.15,turbidity:3.8,twilightStrength:1.4,thinCloudStrength:0.85,thinCloudCoverage:0.42,thinCloudSilver:1.8,celestialStrength:0,ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:0.15,galaxyStrength:0.3,exposure:1.2})},
 {id:'twilight',name:'暮光初星',english:'BLUE HOUR',color:'#ac96d9',description:'太阳沉入地平线后的紫蓝暮光，群星逐渐显现',recipe:makeRecipe({atmosphereStrength:1,sunElevation:-7,sunYaw:15,twilightStrength:1.35,moonYaw:-8,moonElevation:30,moonPhase:110,thinCloudStrength:0.35,thinCloudCoverage:0.28,celestialStrength:0,ringStrength:0,auroraStrength:0,meteorRate:0.5,nebulaStrength:0.3,galaxyStrength:0.6,exposure:1.1})},
 {id:'moonlit',name:'月照薄云',english:'MOONLIT CLOUDS',color:'#a1bbdf',description:'程序半月与带有月光明暗的动态薄云和背景星群',recipe:makeRecipe({atmosphereStrength:1,sunElevation:-18,moonYaw:-5,moonElevation:34,moonSize:2.3,moonPhase:72,moonBrightness:1.25,nightAmbient:0.075,thinCloudStrength:0.85,thinCloudCoverage:0.5,thinCloudScale:1.2,thinCloudEvolution:0.5,celestialStrength:0,ringStrength:0,auroraStrength:0.12,meteorRate:0.5,nebulaStrength:0.25,galaxyStrength:0.5})},
 {id:'overcast',name:'阴天云幕',english:'OVERCAST',color:'#a4b3c2',description:'高覆盖薄云与浑浊散射的灰蓝色日间天空',recipe:makeRecipe({atmosphereStrength:1,sunElevation:24,turbidity:7,mieStrength:1.7,rayleighStrength:0.65,thinCloudStrength:1.6,thinCloudCoverage:0.83,thinCloudScale:0.6,thinCloudSpeed:0.85,thinCloudEvolution:0.55,thinCloudSilver:0.45,skyTint:'#c5d4dd',celestialStrength:0,ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:0.1,galaxyStrength:0.2})},
 {id:'daycycle',name:'昼夜轮转',english:'DAY & NIGHT',color:'#d6af9c',description:'播放时间推动日月轨道，连续观察蓝天、晚霞和星夜',recipe:makeRecipe({atmosphereStrength:1,sunElevation:25,sunYaw:0,cycleSpeed:1,moonYaw:180,moonElevation:-12,moonPhase:35,thinCloudStrength:0.6,thinCloudCoverage:0.36,celestialStrength:0,ringStrength:0,auroraStrength:0.2,meteorRate:0.8,nebulaStrength:0.4,galaxyStrength:0.7})}
 , {"id":"cumulus","name":"云海远航","english":"VOLUME CLOUDS","color":"#bcdced","description":"真实世界云层，随风演化并遮挡远景","preview":{"position":[0,70,0],"pitch":-24,"scene":true},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"volumeCloudStrength":1.1,"volumeCloudCoverage":0.48,"volumeCloudDensity":1.2,"cloudShadowStrength":0.55,"sunElevation":36,"volumeCloudScale":0.012,"volumeCloudBase":150,"volumeCloudThickness":110,"volumeCloudErosion":0.2,"volumeCloudAmbient":0.9,"volumeCloudSunlight":1.2})},
 {"id":"insidecloud","name":"穿云漫游","english":"INSIDE CLOUD","color":"#ccd3dc","description":"飞入有厚度的云体，观察近处密度和视差","preview":{"position":[0,210,0],"pitch":-16,"scene":true},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"volumeCloudStrength":1.2,"volumeCloudCoverage":0.5,"volumeCloudDensity":0.55,"volumeCloudThickness":100,"cloudShadowStrength":0.4,"sunElevation":36,"volumeCloudScale":0.01,"volumeCloudBase":140})},
 {"id":"weatherstorm","name":"雷雨风暴","english":"RAIN STORM","color":"#8b94bc","description":"三维雨丝、动态雷光与昏暗云层","preview":{"position":[0,64,0],"pitch":0,"scene":true},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"rainAmount":0.85,"thunderAmount":0.75,"volumeCloudStrength":1.2,"volumeCloudCoverage":0.82,"volumeCloudDensity":1.2,"fogStrength":0.85,"fogDensity":0.006,"sunElevation":20})},
 {"id":"rainbow","name":"雨后双虹","english":"DOUBLE RAINBOW","color":"#dfb988","description":"围绕反太阳方向的主虹与反向色序外虹","preview":{"position":[0,64,0],"pitch":-15,"scene":true},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0.2,"cycleSpeed":0,"rainbowStrength":1.7,"secondaryRainbow":0.75,"rainbowWetness":1,"sunElevation":18,"sunYaw":180,"thinCloudCoverage":0.15,"volumeCloudStrength":0.35,"volumeCloudCoverage":0.28})},
 {"id":"halo","name":"冰晶日晕","english":"ICE SUN HALO","color":"#dbc59e","description":"太阳周围22度冰晶色环，独立于普通光晕","preview":{"position":[0,64,0],"pitch":-28,"scene":false},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0.2,"cycleSpeed":0,"haloStrength":1.2,"haloIce":1,"sunElevation":28,"sunYaw":0,"thinCloudCoverage":0.18})},
 {"id":"moonhalo","name":"冰晶月晕","english":"ICE MOON HALO","color":"#a4b7da","description":"程序月亮周围的冰晶晕和夜空群星","preview":{"position":[0,64,0],"pitch":-32,"scene":false},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"haloStrength":1.4,"haloIce":1,"haloMoon":1,"sunElevation":-18,"moonYaw":0,"moonElevation":32,"moonSize":1.2,"moonPhase":0,"moonBrightness":1.7,"galaxyStrength":0.45,"nebulaStrength":0.3})},
 {"id":"rift","name":"异界裂幕","english":"DIMENSION RIFT","color":"#b58ddd","description":"原创波域裂幕与不断演化的紫金色天幕","preview":{"position":[0,64,0],"pitch":-18,"scene":false},recipe:makeRecipe({"atmosphereStrength":0,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"specialStrength":1.3,"specialStyle":1,"specialDistortion":0.8,"specialBandWidth":0.22,"nebulaStrength":0.4,"galaxyStrength":0.5})},
 {"id":"ember","name":"赤焰风暴","english":"EMBER SKY","color":"#df8f57","description":"暗红发光波域与浓雾的奇幻天空","preview":{"position":[0,64,0],"pitch":-8,"scene":true},recipe:makeRecipe({"atmosphereStrength":0.35,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"specialStrength":1.5,"specialStyle":2,"specialColorA":"#661f2d","specialColorB":"#ff8e3a","fogStrength":0.5,"fogDensity":0.004,"fogColor":"#8b3c30","sunElevation":-7,"nebulaStrength":0.15})},
 {"id":"birds","name":"归鸟晚霞","english":"BIRDS AT DUSK","color":"#d9a79d","description":"方向空间的动态远景鸟群剪影与金色晚霞","preview":{"position":[0,64,0],"pitch":-15,"scene":false},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0.25,"cycleSpeed":0,"specialStrength":1,"specialStyle":3,"birdDensity":0.65,"birdSpeed":0.8,"sunElevation":5,"sunYaw":10,"thinCloudCoverage":0.18})},
 {"id":"shafts","name":"云隙天光","english":"CLOUD LIGHT SHAFTS","color":"#c6caae","description":"云层透射调制天光，地面接收动态云影","preview":{"position":[0,64,0],"pitch":0,"scene":true},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"volumeCloudStrength":1.1,"volumeCloudCoverage":0.5,"cloudShadowStrength":0.8,"lightShaftStrength":2,"lightShaftDensity":0.18,"fogStrength":0.35,"fogDensity":0.001,"sunElevation":26,"sunYaw":0})},
 {"id":"fog","name":"雾海晨光","english":"HEIGHT FOG","color":"#a8c8ce","description":"真实场景深度和高度驱动的流动雾层","preview":{"position":[0,64,0],"pitch":0,"scene":true},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0.35,"cycleSpeed":0,"fogStrength":1.1,"fogDensity":0.01,"fogHeightFalloff":0.035,"fogBaseHeight":64,"fogNoise":0.55,"thinCloudCoverage":0.28,"sunElevation":12})},
 {"id":"cloudnight","name":"夜光云海","english":"MOONLIT VOLUME","color":"#8699cb","description":"程序月光照亮有世界厚度的动态云层","preview":{"position":[0,70,0],"pitch":-24,"scene":true},recipe:makeRecipe({"atmosphereStrength":1,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":0,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"volumeCloudStrength":0.85,"volumeCloudCoverage":0.48,"sunElevation":-18,"moonYaw":0,"moonElevation":35,"moonBrightness":1.6,"moonPhase":0,"nightAmbient":0.1,"volumeCloudDensity":0.8,"volumeCloudScale":0.012,"volumeCloudBase":150,"volumeCloudThickness":110,"volumeCloudErosion":0.2,"volumeCloudAmbient":1.1})},
 {"id":"cinematicblackhole","name":"金环黑洞","english":"CINEMATIC BLACK HOLE","color":"#ecc48b","description":"二维金白光环、弯曲亮弧与横贯暗核的流动吸积盘","preview":{"position":[0,64,0],"pitch":-18,"scene":false},recipe:makeRecipe({"atmosphereStrength":0,"celestialStrength":0,"ringStrength":0,"blackHoleStrength":1.65,"auroraStrength":0,"meteorRate":0,"thinCloudStrength":0,"cycleSpeed":0,"blackHoleStyle":2,"blackHoleSize":11,"blackHoleYaw":0,"blackHolePitch":18,"blackHoleRingBrightness":1.05,"blackHoleGlow":1.4,"blackHoleArc":1.1,"blackHoleStreak":1.25,"diskSpeed":0.85,"diskColorA":"#da872b","diskColorB":"#fff7d8","starBrightness":1.1,"starDensity":0.35,"nebulaStrength":1.1,"nebulaColorA":"#6c5723","nebulaColorB":"#987343","galaxyColor":"#c7a668","galaxyStrength":0.45,"galacticTilt":-24,"blackHoleNoise":1.4,"diskTilt":80,"starSize":0.65,"backgroundColor":"#0b0b08","starCoolColor":"#c6cec0","starWarmColor":"#dfc493"})}
 ,{id:'physicalsunny',name:'球形晴空',english:'PHYSICAL DAY',color:'#85cbea',description:'球形大气的蓝色分子散射、太阳光晕与真实高度变化',preview:{position:[0,70,0],pitch:-28,scene:false,biomePreset:'mixed'},recipe:makeRecipe({starBrightness:0,atmosphereStyle:2,atmosphereStrength:1,sunElevation:38,sunYaw:0,cycleSpeed:0,thinCloudStrength:0,celestialStrength:0,ringStrength:0,blackHoleStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.1,galaxyStrength:.15,observerAltitude:.07,multipleScattering:1,exposure:1.4})},
 {id:'physicalsunset',name:'散射日落',english:'PHYSICAL SUNSET',color:'#e6a46e',description:'日光沿弯曲大气长路径消光，呈现暖色地平线与渐变暮光',preview:{position:[0,70,0],pitch:-8,scene:false,biomePreset:'mixed'},recipe:makeRecipe({starBrightness:0,atmosphereStyle:2,atmosphereStrength:1,sunElevation:3,sunYaw:0,sunSize:.55,cycleSpeed:0,thinCloudStrength:.16,thinCloudCoverage:.2,celestialStrength:0,ringStrength:0,blackHoleStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.1,galaxyStrength:.2,mieDensity:1.2,multipleScattering:1,exposure:1.4})},
 {id:'orbital',name:'轨道边缘',english:'ORBITAL LIMB',color:'#829fc9',description:'150公里观察基准，向下看弯曲行星边缘与薄蓝大气层',preview:{position:[0,64,0],pitch:14,scene:false,biomePreset:'dry'},recipe:makeRecipe({atmosphereStyle:2,atmosphereStrength:1,observerAltitude:150,sunElevation:30,sunYaw:0,cycleSpeed:0,thinCloudStrength:0,celestialStrength:0,ringStrength:0,blackHoleStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.08,galaxyStrength:.12,starDensity:.25,starBrightness:.8,groundAlbedo:.1,multipleScattering:1,quality:3,exposure:1.35})},
 {id:'snowfall',name:'轻雪漫游',english:'GENTLE SNOW',color:'#bdd7e8',description:'随风飘摆的程序雪花，按演示气候与屋顶高度决定降雪区域',preview:{position:[0,70,0],pitch:-8,scene:true,biomePreset:'snow'},recipe:makeRecipe({starBrightness:0,atmosphereStyle:2,atmosphereStrength:1,precipitationMode:1,precipitationRoof:1,rainAmount:.8,weatherFollowWorld:0,snowDensity:1.1,snowSize:.065,snowSpeed:.55,snowBrightness:1.1,snowFlutter:.8,snowWindStrength:.35,sunElevation:28,thinCloudStrength:.3,thinCloudCoverage:.45,celestialStrength:0,ringStrength:0,blackHoleStrength:0,auroraStrength:0,meteorRate:0,cycleSpeed:0,nebulaStrength:.05,galaxyStrength:.1,fogStrength:.3,fogDensity:.0025,groundAlbedo:.35})},
 {id:'blizzard',name:'风雪云幕',english:'BLIZZARD',color:'#a9b7d0',description:'高密度飞雪、强风飘摆与低能见度雾层，可从屋顶下观察遮挡',preview:{position:[0,74,0],pitch:-8,scene:true,biomePreset:'snow'},recipe:makeRecipe({starBrightness:0,atmosphereStyle:2,atmosphereStrength:1,precipitationMode:1,precipitationRoof:1,rainAmount:1,thunderAmount:.25,weatherFollowWorld:0,snowDensity:2.5,snowSize:.09,snowSpeed:1.2,snowBrightness:1.3,snowFlutter:1.5,snowWindStrength:1.8,rainWind:35,sunElevation:20,mieDensity:2.4,thinCloudStrength:.6,thinCloudCoverage:.7,skyTint:'#bdccdf',fogStrength:.85,fogDensity:.0045,fogColor:'#c1ccd9',celestialStrength:0,ringStrength:0,blackHoleStrength:0,auroraStrength:0,meteorRate:0,cycleSpeed:0,nebulaStrength:0,galaxyStrength:0,environmentRenderScale:.75})},
 {id:'glassrain',name:'玻璃雨景',english:'RAIN THROUGH GLASS',color:'#92bbca',description:'透过玻璃看近处雨丝与远处雾景，比较透明层前后的分段合成',preview:{position:[0,70,0],pitch:0,scene:true,transparentScene:'glass',biomePreset:'rain'},recipe:makeRecipe({starBrightness:0,atmosphereStyle:2,atmosphereStrength:1,precipitationMode:1,precipitationRoof:1,nativeTransparentMode:1,rainAmount:.9,thunderAmount:.15,weatherFollowWorld:0,rainDensity:1.4,rainBrightness:.9,rainWind:10,sunElevation:23,mieDensity:1.5,thinCloudStrength:.5,thinCloudCoverage:.58,fogStrength:.4,fogDensity:.0035,celestialStrength:0,ringStrength:0,blackHoleStrength:0,auroraStrength:0,meteorRate:0,cycleSpeed:0,nebulaStrength:.04,galaxyStrength:.08,environmentRenderScale:.75})}
 ,{id:'gasgiant',name:'巨行星云带',english:'GIANT GAS WORLD',color:'#d3af90',description:'巨大的程序气体行星，旋转云带、风暴纹理与倾斜光环',preview:{position:[0,64,0],pitch:-22,scene:false},recipe:makeRecipe({giantBodyStyle:1,giantBodyScale:1.8,giantBodyAtmosphere:.6,celestialSize:18,celestialYaw:0,celestialPitch:22,celestialStrength:1.2,celestialPhase:38,celestialSpeed:.25,celestialColorA:'#74505b',celestialColorB:'#dfb180',ringStrength:.7,ringTilt:65,ringWidth:1.9,auroraStrength:0,meteorRate:0,nebulaStrength:.35,galaxyStrength:.45,blackHoleStrength:0})},
 {id:'icegiant',name:'冰封巨星',english:'FROZEN WORLD',color:'#a4d6e7',description:'程序冰面、裂纹和冷色边缘气层，保留世界方向中的天体位置',preview:{position:[0,64,0],pitch:-20,scene:false},recipe:makeRecipe({giantBodyStyle:2,giantBodyScale:1.8,giantBodyAtmosphere:.9,celestialSize:18,celestialYaw:0,celestialPitch:20,celestialStrength:1.3,celestialPhase:55,celestialSpeed:.1,celestialDetail:6,celestialColorA:'#47658e',celestialColorB:'#c1e9f3',ringStrength:.2,ringTilt:45,ringWidth:1.5,auroraStrength:.1,meteorRate:0,nebulaStrength:.3,galaxyStrength:.4,blackHoleStrength:0})},
 {id:'fragmentmoon',name:'破碎月轮',english:'FRAGMENTED MOON',color:'#b5a2bf',description:'岩质月球与周围独立碎片，调整间距观察有限角域边界',preview:{position:[0,64,0],pitch:-22,scene:false},recipe:makeRecipe({giantBodyStyle:3,giantBodyScale:1.55,giantBodySeparation:1.5,giantBodyAtmosphere:.2,celestialSize:16,celestialYaw:0,celestialPitch:22,celestialStrength:1.35,celestialPhase:45,celestialSpeed:.18,celestialColorA:'#52425f',celestialColorB:'#c6b5c2',ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.35,galaxyStrength:.35,blackHoleStrength:0})},
 {id:'glowingdarkdisk',name:'辉光暗盘',english:'DARK GLOW DISK',color:'#ce7aba',description:'受 Hysteria 末地暗盘与双色耀光构成启发的原创远景天体',preview:{position:[0,64,0],pitch:-20,scene:false},recipe:makeRecipe({giantBodyStyle:4,giantBodyScale:1.65,giantBodyAtmosphere:1.1,celestialSize:14,celestialYaw:0,celestialPitch:20,celestialStrength:1.15,celestialSpeed:.3,celestialColorA:'#b13b9e',celestialColorB:'#ffac7b',ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.35,galaxyStrength:.3,blackHoleStrength:0})},
 {id:'binarystars',name:'双星交辉',english:'BINARY STARS',color:'#bdc1eb',description:'两颗不同色温的程序恒星及动态表面，伴星间距可调',preview:{position:[0,64,0],pitch:-22,scene:false},recipe:makeRecipe({giantBodyStyle:5,giantBodyScale:1.35,giantBodySeparation:1.5,giantBodyAtmosphere:.9,celestialSize:13,celestialYaw:0,celestialPitch:22,celestialStrength:1.2,celestialSpeed:.35,celestialColorA:'#799fe0',celestialColorB:'#ffba75',ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.25,galaxyStrength:.4,blackHoleStrength:0})},
 {id:'flaringstar',name:'耀斑红星',english:'FLARING STAR',color:'#e89159',description:'受 Photon 末地橙色恒星与动态耀斑构成启发的原创巨大红星',preview:{position:[0,64,0],pitch:-20,scene:false},recipe:makeRecipe({giantBodyStyle:6,giantBodyScale:1.7,giantBodyAtmosphere:1.2,celestialSize:15,celestialYaw:0,celestialPitch:20,celestialStrength:1.3,celestialSpeed:.45,celestialDetail:5,celestialColorA:'#9e271c',celestialColorB:'#ffd48b',ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.25,galaxyStrength:.35,blackHoleStrength:0})},
 {id:'vortexdarkstar',name:'旋涡暗星',english:'VORTEX DARK STAR',color:'#aaa0d8',description:'受 Super Duper Vanilla 末地暗盘与旋转噪声环构成启发的原创天体',preview:{position:[0,64,0],pitch:-20,scene:false},recipe:makeRecipe({giantBodyStyle:7,giantBodyScale:1.75,giantBodyAtmosphere:1,celestialSize:14,celestialYaw:0,celestialPitch:20,celestialStrength:1.2,celestialSpeed:.4,celestialColorA:'#695794',celestialColorB:'#e1b4eb',ringStrength:0,auroraStrength:0,meteorRate:0,nebulaStrength:.35,galaxyStrength:.4,blackHoleStrength:0})}
];
export function rgb(hex) { return [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255); }
export const PACKS = {
 GiantBodySettings:['giantBodyStyle','giantBodyScale','giantBodySeparation','giantBodyAtmosphere'],
 "AtmosphereGeometry":["planetRadius","atmosphereHeight","rayleighHeight","mieHeight"],
 "AtmosphereComposition":["rayleighDensity","mieDensity","ozoneDensity","groundAlbedo"],
 "AtmospherePhysical":["atmosphereStyle","observerAltitude","multipleScattering","groundLevel"],
 "SnowSettings":["snowDensity","snowSize","snowSpeed","snowBrightness"],
 "PrecipitationSettings":["precipitationMode","precipitationRoof","snowFlutter","snowWindStrength"],
 "PipelineSettings":["nativeTransparentMode","environmentRenderScale","environmentDepthTolerance"],

 "WeatherSettings":["rainAmount", "thunderAmount", "weatherSpeed", "stormDarkening"],
 "RainSettings":["rainDensity", "rainSpeed", "rainLength", "rainBrightness"],
 "WeatherMotion":["rainWind", "weatherFollowWorld", "lightningFrequency", "lightningDuration"],
 "FogSettings":["fogStrength", "fogDensity", "fogHeightFalloff", "fogBaseHeight"],
 "FogMotion":["fogNoise", "fogScale", "fogSpeed", "fogDistance"],
 "VolumeCloudSettings":["volumeCloudStrength", "volumeCloudCoverage", "volumeCloudDensity", "volumeCloudScale"],
 "VolumeCloudGeometry":["volumeCloudBase", "volumeCloudThickness", "volumeCloudDistance", "volumeCloudErosion"],
 "VolumeCloudMotion":["volumeCloudSpeed", "volumeCloudWind", "volumeCloudEvolution", "volumeCloudAnisotropy"],
 "CloudLighting":["volumeCloudAmbient", "volumeCloudSunlight", "cloudShadowStrength", "lightShaftStrength"],
 "LightingDetail":["lightShaftDensity", "lightShaftDistance", "cloudShadowSoftness", "lightShaftAnisotropy"],
 "RainbowSettings":["rainbowStrength", "rainbowWidth", "secondaryRainbow", "rainbowWetness"],
 "HaloSettings":["haloStrength", "haloWidth", "haloIce", "haloMoon"],
 "SpecialSettings":["specialStrength", "specialStyle", "specialScale", "specialSpeed"],
 "SpecialShape":["specialDistortion", "specialBandWidth", "birdDensity", "birdSpeed"],
 "BlackHoleAppearance":["blackHoleStyle", "blackHoleRingBrightness", "blackHoleRingWidth", "blackHoleGlow"],
 "BlackHoleCinematic":["blackHoleArc", "blackHoleStreak", "blackHoleContrast", "blackHoleNoise"],

 StarSettings:['starDensity','starBrightness','starSize','twinkleAmount'],
 MotionSettings:['twinkleSpeed','nebulaSpeed','auroraSpeed','meteorSpeed'],
 GalaxySettings:['galacticYaw','galacticTilt','bandWidth','galaxyStrength'],
 CloudSettings:['nebulaStrength','nebulaScale','dustStrength','exposure'],
 AuroraSettings:['auroraStrength','auroraHeight','auroraWidth','auroraCurtains'],
 MeteorSettings:['meteorRate','meteorBrightness','meteorLength','horizonGlow'],
 BodySettings:['celestialStrength','celestialSize','celestialYaw','celestialPitch'],
 BodySurface:['celestialType','celestialPhase','celestialDetail','celestialSpeed'],
 RingSettings:['ringStrength','ringTilt','ringWidth'],
 BlackHoleSettings:['blackHoleStrength','blackHoleSize','blackHoleYaw','blackHolePitch'],
 DiskSettings:['diskTilt','diskWidth','diskSpeed','lensingStrength'],
 AtmosphereSettings:['atmosphereStrength','turbidity','rayleighStrength','mieStrength'],
 SunSettings:['sunBrightness','sunElevation','sunYaw','sunSize'],
 DaySettings:['cycleSpeed','mieAnisotropy','nightAmbient','twilightStrength'],
 MoonSettings:['moonBrightness','moonYaw','moonElevation','moonSize'],
 MoonSurface:['moonPhase','moonDetail','moonHalo','moonSpeed'],
 ThinCloudSettings:['thinCloudStrength','thinCloudCoverage','thinCloudScale','thinCloudHeight'],
 ThinCloudMotion:['thinCloudSpeed','thinCloudWind','thinCloudEvolution','thinCloudSilver'],
};
export const COLORS={SnowColor:"snowColor","FogColor":"fogColor","VolumeCloudColor":"volumeCloudColor","RainColor":"rainColor","LightningColor":"lightningColor","SpecialColorA":"specialColorA","SpecialColorB":"specialColorB",BackgroundColor:'backgroundColor',StarCoolColor:'starCoolColor',StarWarmColor:'starWarmColor',GalaxyColor:'galaxyColor',NebulaColorA:'nebulaColorA',NebulaColorB:'nebulaColorB',AuroraColorA:'auroraColorA',AuroraColorB:'auroraColorB',BodyColorA:'celestialColorA',BodyColorB:'celestialColorB',RingColor:'ringColor',DiskColorA:'diskColorA',DiskColorB:'diskColorB',SkyTint:'skyTint',SunColor:'sunColor',MoonColor:'moonColor',ThinCloudColor:'thinCloudColor'};
export const ANGLES=["rainWind","volumeCloudWind","rainbowWidth","haloWidth",'galacticYaw','galacticTilt','celestialSize','celestialYaw','celestialPitch','celestialPhase','ringTilt','blackHoleSize','blackHoleYaw','blackHolePitch','diskTilt','sunElevation','sunYaw','sunSize','moonYaw','moonElevation','moonSize','moonPhase','thinCloudWind'];
export function uniformValues(recipe) {
 const p=validateRecipe(recipe).parameters, values={Seed:p.seed,Quality:p.quality};
 for(const [u,keys]of Object.entries(PACKS)) values[u]=keys.map(k=>p[k]*(ANGLES.includes(k)?Math.PI/180:1));
 for(const [u,k]of Object.entries(COLORS)) values[u]=rgb(p[k]);
 return values;
}
