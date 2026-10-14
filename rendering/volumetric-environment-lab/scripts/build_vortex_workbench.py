from pathlib import Path
import shutil,json
root=Path('E:/着色器渲染/volumetric-environment-lab/workbench');out=root/'vortex';out.mkdir(exist_ok=True)
for n in ['preview.js','volume-core.glsl','index.html','style.css','schema.json','contract.json','presets.json']:shutil.copy2(root/n,out/n)
specs={
'mode':('介质模式',0,1,1,1),'duration':('形成时长（秒）',1,90,1,18),
'bottomRadius':('漏斗底部半径',2,40,.5,8),'topRadius':('漏斗顶部半径',10,100,.5,48),'profile':('漏斗收束曲线',.4,4,.05,1.7),'hollow':('稀薄内芯比例',0,.8,.01,.28),
'rotation':('旋转速度（弧度/秒）',-3,3,.05,.85),'rise':('上升速度（格/秒）',0,35,.5,10),'inflow':('底部卷吸',0,1.5,.01,.45),'bend':('轴线弯曲幅度',0,40,.5,12),'turbulence':('翻卷扰动',0,2,.01,.65),'canopyDensity':('上方云帽密度',0,2,.01,.45),
'ringRadius':('喷雾 / 尘雾环半径',5,70,.5,24),'ringWidth':('喷环宽度',2,25,.5,9),'sprayDensity':('喷雾 / 尘雾密度',0,4,.01,1.1),'sprayHeight':('卷起高度',3,45,.5,15),
'waterDepth':('水面中心凹陷',0,6,.1,2.5),'waveAmp':('水波高度',0,2,.05,.7),'waveSpeed':('水波传播速度',0,6,.1,2.2),'foam':('螺旋泡沫强度',0,1,.01,.7)
}
(out/'vortex-schema.json').write_text(json.dumps({'schema':'vortex-cloud/1','fields':{k:{'label':v[0],'min':v[1],'max':v[2],'step':v[3],'default':v[4]} for k,v in specs.items()}},ensure_ascii=False,indent=2),encoding='utf-8')
presets=json.loads((out/'presets.json').read_text(encoding='utf-8'));r={k:v for k,v in presets[0].items() if k!='id'};r['name']='龙吸水 · 雾柱与喷雾环';r['parameters'].update(shape=0,centerX=0,centerY=95,centerZ=0,radius=150,bottom=0,height=190,density=1.65,coverage=.86,scale=.052,erosion=.5,extinction=.045,ambient=.48,sunGain=1.2,mediumR=.53,mediumG=.61,mediumB=.7,flashEnabled=0,flashStrength=10,flashRadius=100,flashPlacement=.15,flashPeriod=5,flashDuration=.7,mediumGain=1.2,surfaceGain=.25,emission=0,viewSteps=80,lightSteps=4,speed=.4)
(out/'initial-recipe.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
core=(out/'volume-core.glsl').read_text(encoding='utf-8')
core=core.replace('uniform float Time;','''uniform float Time;
uniform vec4 VortexShape; // bottomRadius, topRadius, profile, hollow
uniform vec4 VortexFlow; // rotation radians/sec, upward units/sec, bend, turbulence
uniform vec4 VortexRing; // ring radius, width, density, height
uniform vec4 VortexWater; // water depth, waves amplitude, waves speed, foam
uniform vec4 VortexLife; // duration, inward warp, canopy density, mode
float vortexProgress(){return clamp(max(Time,0.0)/VortexLife.x,0.0,1.0);}
''')
a=core.index('float veSupport(');b=core.index('float veEndDensity(',a)
core=core[:a]+'''float veSupport(vec3 world){
    vec3 p=world-Bounds.xyz;float y=world.y-Layer.x;
    return (1.0-smoothstep(Bounds.w*.93,Bounds.w,length(p.xz)))*step(0.0,y)*step(y,Layer.y);
}
'''+core[b:]
a=core.index('float veDensity(');b=core.index('bool veSphereInterval(',a)
core=core[:a]+'''vec2 vortexAxis(float h){
    return VortexFlow.z*sin(h*3.14159265)*vec2(sin(h*4.0+Time*.13),cos(h*3.0+Time*.11));
}
float veDensity(vec3 world){
    float support=veSupport(world);if(support<=0.0||Density.x<=0.0)return 0.0;
    float progress=vortexProgress();if(progress<=0.0)return 0.0;
    float fullHeight=Layer.y*.84;float y=world.y-Layer.x;float h=clamp(y/fullHeight,0.0,1.0);
    vec2 p=world.xz-Bounds.xz-vortexAxis(h);
    float radius=mix(VortexShape.x,VortexShape.y,pow(h,VortexShape.z));
    float r=length(p), normalized=r/max(radius,1.0);
    float omega=VortexFlow.x/(.75+normalized*normalized*.25);
    float angle=-omega*Time-h*8.0;
    vec2 q=mat2(cos(angle),sin(angle),-sin(angle),cos(angle))*p;
    q*=1.0+VortexLife.y*(1.0-h)*(.5+.5*sin(r*.09+Time*.8));
    vec3 np=vec3(q.x,y-VortexFlow.y*Time,q.y)*Density.z+vec3(Seed*.013,0.0,Seed*.019);
    vec3 warp=veCurl(np*.7)*VortexFlow.w*.28;
    float n=veFbm(np+warp);
    float theta=atan(p.y,p.x);
    float helix=.5+.5*sin(theta*4.0+h*22.0-Time*VortexFlow.x*4.0);
    float roughR=normalized+(n-.5)*VortexFlow.w*.22;
    float envelope=1.0-smoothstep(.72,1.05,roughR);
    if(VortexShape.w>0.0)envelope*=smoothstep(VortexShape.w*.35,VortexShape.w,normalized);
    float reveal=VortexLife.w>.5?smoothstep(h*.72,h*.72+.25,progress):smoothstep((1.0-h)*.72,(1.0-h)*.72+.25,progress);
    float topFade=1.0-smoothstep(fullHeight*.98,fullHeight*1.08,y);
    float body=envelope*(.32+.68*smoothstep(.24,.72,n))*(.62+.38*helix)*reveal*topFade;
    float baseRadius=VortexRing.x+y*.23;
    float ring=exp(-pow((length(world.xz-Bounds.xz)-baseRadius)/max(VortexRing.y,1.0),2.0));
    ring*=exp(-pow(y/max(VortexRing.w,1.0),1.3));
    ring*=.3+.7*veFbm(vec3(q.x,y-Time*VortexFlow.y*.7,q.y)*.09+Seed*.02);
    ring*=VortexRing.z*smoothstep(.03,.35,progress);
    vec3 cp=world-vec3(Bounds.x,Layer.x+fullHeight,Bounds.z);
    float canopy=(1.0-smoothstep(Bounds.w*.52,Bounds.w*.92,length(cp.xz)))*exp(-pow(cp.y/max(Layer.y*.09,1.0),2.0));
    canopy*=VortexLife.z*(.25+.75*veFbm(vec3(q.x,cp.y,q.y)*.026))*smoothstep(0.0,.3,progress);
    return (body+ring+canopy)*Density.x*support;
}
'''+core[b:]
core=core.replace('envelope*Flash.w);','envelope*Flash.w*smoothstep(.6,1.0,vortexProgress()));')
(out/'volume-core.glsl').write_text(core,encoding='utf-8')
print(out)
