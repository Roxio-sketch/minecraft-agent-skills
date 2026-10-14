"""Maintain the portable world-volume/1 parameter schema and editable presets."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
fields = {}
def add(group, uniform, names, defaults, mins, maxs, steps, labels, integer=()):
    for i, name in enumerate(names):
        fields[name] = dict(group=group, uniform=uniform, component=i,
                            default=defaults[i], min=mins[i], max=maxs[i], step=steps[i],
                            label=labels[i], type="integer" if name in integer else "number")

add("区域", "Bounds", ["centerX","centerY","centerZ","radius"], [0,75,0,180], [-30000000,-2048,-30000000,1], [30000000,2048,30000000,4096], [1,1,1,1], ["中心 X","中心 Y","中心 Z","外半径（格）"])
add("区域", "Layer", ["bottom","height","shape","innerRadius"], [35,100,0,25], [-2048,1,0,0], [2048,2048,2,4096], [1,1,1,1], ["云底高度","厚度（格）","形状：0云层 / 1球 / 2环","中央空洞半径"], ["shape"])
add("密度", "Density", ["density","coverage","scale","erosion"], [0.65,0.66,0.018,0.25], [0,0,0.001,0], [5,1,0.25,1], [0.01,0.01,0.001,0.01], ["密度","覆盖率","世界噪声频率","细节侵蚀"])
add("运动", "Flow", ["windX","windZ","speed","curl"], [0.8,0.35,1,0.5], [-1,-1,0,0], [1,1,20,4], [0.01,0.01,0.1,0.05], ["风向 X","风向 Z","平流速度（格/秒）","域扭卷强度"])
add("散射", "Optics", ["extinction","anisotropy","ambient","sunGain"], [0.045,0.45,0.32,1], [0,0,0,0], [0.5,0.9,4,8], [0.001,0.01,0.01,0.05], ["消光系数（每格）","前向散射","环境光","太阳光强度"])
add("自发光", "Glow", ["emission","emissionScale","emissionThreshold","emissionSpeed"], [0,2.2,0.45,0.3], [0,0.1,0,0], [8,8,0.99,5], [0.05,0.1,0.01,0.05], ["介质自发光","发光噪声频率倍数","发光阈值","发光变化速度"])
add("闪光", "Flash", ["flashEnabled","flashPeriod","flashDuration","flashStrength"], [1,7,0.9,4], [0,0.25,0.05,0], [1,60,4,30], [1,0.1,0.05,0.1], ["间歇闪光开关","事件周期（秒）","闪光持续（秒）","闪光光源强度"], ["flashEnabled"])
add("闪光", "FlashShape", ["flashRadius","flashPlacement","mediumGain","surfaceGain"], [65,0.65,1,0.5], [1,0,0,0], [2048,0.95,8,8], [1,0.01,0.05,0.05], ["局部光半径（格）","事件位置分布","照亮云雾","照亮表面"])
for group, uniform, prefix, default in [("颜色","MediumColor","medium",[0.55,0.63,0.8]),("自发光","EmissionColor","emission",[0.35,0.08,0.8]),("闪光","FlashColor","flash",[0.55,0.65,1]),("太阳","SunColor","sun",[1,0.85,0.65])]:
    add(group,uniform,[prefix+c for c in "RGB"],default,[0,0,0],[4,4,4],[0.01]*3,[uniform+" "+c for c in "RGB"])
add("太阳", "SunDirection", ["sunX","sunY","sunZ"], [0.4,0.8,-0.3], [-1]*3, [1]*3, [0.01]*3, ["太阳方向 X","太阳方向 Y","太阳方向 Z"])
add("质量", "Seed", ["seed"], [17], [0], [65535], [1], ["随机种子"], ["seed"])
add("质量", "ViewSteps", ["viewSteps"], [48], [8], [96], [1], ["视线步数"], ["viewSteps"])
add("质量", "LightSteps", ["lightSteps"], [4], [1], [12], [1], ["光线步数"], ["lightSteps"])

add("末地分布", "EndWarp", ["densityModel","verticalStretch","twistAmount","twistSpeed"], [0,24,0.014,0.02], [0,1,0,-0.2], [2,64,0.15,0.2], [1,1,0.001,0.001], ["分布：0云团 / 1扭卷末地 / 2紫光薄雾","纵向拉伸","每格高度扭角","扭转速度（弧度/秒）"], ["densityModel"])
add("末地分布", "EndCarve", ["cavityRadius","cavityAspect","torusRadius","torusWidth"], [75,0.8,150,85], [0,0.1,0,0], [1024,4,2048,1024], [1,0.05,1,1], ["中心椭球疏空半径","椭球高度缩放","疏空环槽主半径","疏空环槽截面半径"])
add("末地分布", "EndDepth", ["voidDensity","voidFloor","voidFalloff","nearClearRadius"], [0.35,-60,0.7,80], [0,-2048,0.01,0], [5,2048,4,256], [0.01,1,0.01,1], ["下层雾密度","下层雾基准高度","下层雾上方衰减","近相机清理范围"])

schema = dict(schema="world-volume/2",version="0.2.0",fields=fields)
root = ROOT / "workbench"
root.mkdir(parents=True,exist_ok=True)
(root/"schema.json").write_text(json.dumps(schema,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
presets=[]
def preset(id,name,changes):
    p={key:field["default"] for key,field in fields.items()}
    p.update(changes)
    presets.append(dict(id=id,schema="world-volume/2",name=name,parameters=p))
preset("cloud-sea","日照云海",dict(bottom=30,height=75,coverage=0.72,flashEnabled=0,ambient=0.45,mediumR=0.85,mediumG=0.9,mediumB=1))
preset("ground-mist","贴地冷雾",dict(bottom=-3,height=35,radius=200,density=0.22,coverage=0.85,scale=0.03,erosion=0.1,flashEnabled=0,sunGain=0.3,anisotropy=0.2))
preset("alien-storm","异界环形风暴",dict(shape=2,bottom=5,height=120,innerRadius=42,coverage=0.82,density=1.1,curl=1.8,speed=3,ambient=0.12,emission=0.4,mediumR=0.17,mediumG=0.12,mediumB=0.3,flashPeriod=5,flashStrength=8,surfaceGain=0.9))
preset("luminous-pocket","紫色发光雾团",dict(shape=1,centerY=58,radius=65,density=0.4,coverage=0.88,emission=2,flashEnabled=0,sunGain=0.2,ambient=0.1))
preset("cloud-flash","云内蓝白闪光",dict(bottom=8,height=105,coverage=0.88,density=0.7,ambient=0.12,sunGain=0.18,flashPeriod=4,flashDuration=0.8,flashStrength=10,flashRadius=85,surfaceGain=1.2,mediumGain=1.8))
preset("sun-shafts","晨光穿雾",dict(bottom=-5,height=110,coverage=0.55,density=0.32,extinction=0.025,anisotropy=0.8,sunX=-0.7,sunY=0.35,sunZ=-0.1,sunGain=3,ambient=0.08,flashEnabled=0))
preset("end-vortex","末地蓝灰扭卷云",dict(densityModel=1,shape=1,centerY=100,radius=900,scale=0.018,coverage=0.60,density=1.1,erosion=0.22,verticalStretch=24,twistAmount=0.014,twistSpeed=0.02,extinction=0.009,ambient=0.28,sunGain=0.15,mediumR=0.23,mediumG=0.34,mediumB=0.48,flashR=0.5,flashG=0.75,flashB=1,flashPeriod=5,flashRadius=250,flashPlacement=0.3,flashStrength=10,surfaceGain=0.45,voidDensity=0.8))
preset("purple-light-fog","紫色光雾分布学习",dict(densityModel=2,shape=0,bottom=-80,height=380,radius=800,density=0.12,coverage=1,extinction=0.012,scale=0.018,speed=0.8,flashEnabled=0,ambient=0.8,sunGain=0.1,mediumR=0.62,mediumG=0.3,mediumB=0.85,emission=0.12,emissionR=0.8,emissionG=0.32,emissionB=1,verticalStretch=5,nearClearRadius=0,voidDensity=0))
(root/"presets.json").write_text(json.dumps(presets,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(json.dumps(dict(fields=len(fields),presets=len(presets),root=str(root))))
