from pathlib import Path
import json,shutil
root=Path(__file__).resolve().parents[1]
e=root/'evidence/white-hole-default-dust-ui-20261007'
skill=Path('C:/Users/MAINTAINER/.codex/skills/minecraft-black-hole')
e.mkdir(parents=True,exist_ok=True)
def edit(path,fn):
    old=path.read_text(encoding='utf-8');new=fn(old)
    rel=path.relative_to(root) if path.is_relative_to(root) else Path('skill')/path.relative_to(skill)
    backup=e/'source-before'/rel
    if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_text(old,encoding='utf-8')
    path.write_text(new,encoding='utf-8')
p=json.loads((root/'src/main/resources/assets/vfxworkbench/recipes/white-ring-default.json').read_text(encoding='utf-8'))['parameters']
p.update(dict(form='white-ring',objectMode='white',scale=1.8,distance=39.0,core=.28,inner=1.65,outer=5.27,thickness=.15,tilt=-1.0,bend=1.06,density=.6,exposure=.76,coreEmission=3.95,coreGlow=.8,coreGlowRadius=1.05,coreColor='#eefaff',hot='#dffaff',cool='#1265d8',speed=2.0,infallSpeed=1.46,seed=46271,lens=True,lensAffectsPlayer=True,lensStrength=.5,lensRadius=4.14,particles=False,particleCount=640,particleSpeed=1.3,particleSize=.022,particleOuter=6.2,particleBrightness=1.6,particleCold='#2787f3',particleHot='#dffaff',halo=0.0,haloRing=.6,haloDisk=.35,haloAura=.25,diskStyle='classic',beaming=0.0,diskGradientPosition=.52,coreGradientStrength=.65,bandDetail=12.0,dustScale=4.0,dustFill=.375,filament=.75,spiral=.7,haloRadius=1.0,haloWidth=.11,haloSharpness=2.0,haloGradientMix=1.0,haloDiskGradientMix=1.0,haloAuraGradientMix=0.0,jetStrength=0.0,jetLength=6.201,jetWidth=.5,jetSpeed=1.15,shellStrength=.28,shellSpeed=.7,shellRadius=4.8,shellWidth=.1,shellColor='#72dfff',diskMid='#51c7ff',coreEdgeColor='#58bfff',haloGradientStart='#dcf8ff',haloGradientEnd='#368eff',haloDiskInnerColor='#afeaff',haloDiskOuterColor='#1d70d4',haloAuraInnerColor='#bfeaff',haloAuraOuterColor='#2d67b0',jetBaseColor='#eaffff',jetTipColor='#72dfff',steps=160,playing=True,time=37862.8))
assert len(p)==74
(e/'user-white-default.json').write_text(json.dumps(dict(schema='black-hole-world/v2',parameters=p),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
p['shellEnabled']=True
edit(root/'src/main/resources/assets/vfxworkbench/recipes/white-ring-default.json',lambda _:json.dumps(dict(schema='black-hole-world/v2',parameters=p),ensure_ascii=False,indent=2)+'\n')
schema=root/'src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleSkillSchema.java'
def schema_edit(s):
    s=s.replace('WHITE_CORE_EMISSION="4"','WHITE_CORE_EMISSION="3.95"')
    s=s.replace('whiteNum(p,"shellStrength"', 'p.add(ParamSpec.builder("shellEnabled","外扩光圈开关",ParamType.BOOL).group("外扩光壳").help("单独开启或关闭循环外扩光圈；关闭后保留光圈参数，不影响核心光芒、盘面或透镜。").visibleWhen(v->v.get("objectMode").equals("white")).defaultValue("true").build());\n        whiteNum(p,"shellStrength"',1)
    s=s.replace('!CORE_GLOW_FIELDS.contains(spec.key))','!CORE_GLOW_FIELDS.contains(spec.key)&&!spec.key.equals("shellEnabled"))')
    s=s.replace('boolean whitePreset="white-ring".equals(source.get("form"));','boolean whitePreset="white-ring".equals(source.get("form"));\n        values.putIfAbsent("shellEnabled","true");')
    s=s.replace('||CORE_GLOW_FIELDS.contains(key)))','||CORE_GLOW_FIELDS.contains(key)||key.equals("shellEnabled")))')
    start=s.index('            case "white-ring"->{',s.index('public static void applyForm'))
    end=s.index('            default->{}',start)
    s=s[:start]+'''            case "white-ring"->{
                // One canonical resource drives the preset and importable default.
                try(var stream=BlackHoleSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/recipes/white-ring-default.json")){
                    if(stream==null)throw new IllegalStateException("缺少白洞默认配方");
                    var values=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("parameters");
                    for(var entry:values.entrySet())s.set(entry.getKey(),entry.getValue().getAsString());
                }catch(java.io.IOException ex){throw new IllegalStateException("无法读取白洞默认配方",ex);}
            }
'''+s[end:]
    return s
edit(schema,schema_edit)
render=root/'src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleRender.java'
edit(render,lambda s:s.replace('s.safeGetUniform("u"+Character.toUpperCase(key.charAt(0))+key.substring(1)).set(p.getFloat(key));','s.safeGetUniform("u"+Character.toUpperCase(key.charAt(0))+key.substring(1)).set(key.equals("shellStrength")&&!p.getBool("shellEnabled")?0f:p.getFloat(key));'))
edit(root/'src/main/java/dev/vfxworkbench/effects/blackhole/BlackHoleWorld.java',lambda s:s.replace('if(params.getFloat("shellStrength")>0)','if(params.getBool("shellEnabled")&&params.getFloat("shellStrength")>0)'))
# Shared dust fixes apply to browser, world and particle occlusion kernels.
def kernel_edit(s):
    s=s.replace('radial=smoothstep(uInner+1.12,uInner+1.72,radius)\n            *(1.0-smoothstep(uOuter-.8,uOuter,radius));','''// Scale the exclusion and edge fades to the actual annular width.
        // Absolute offsets previously erased most of a narrow dust disk.
        float span=max(uOuter-uInner,.01);
        radial=smoothstep(uInner+.08*span,uInner+.25*span,radius)
            *(1.0-smoothstep(uOuter-.20*span,uOuter,radius));''')
    s=s.replace('if(BH_DUST_STYLE)sigma*=.25;','if(BH_DUST_STYLE)sigma*=.85;')
    return s
edit(skill/'assets/preview/black-hole.glsl',kernel_edit)
def style_edit(s):
    s=s.replace('float field=1.0;\n    // Bounded','float field=0.0,weightSum=0.0;\n    // Bounded')
    s=s.replace('field*=.12+.88*n;','float weight=pow(.55,float(i));\n        field+=weight*n;weightSum+=weight;')
    s=s.replace('float porous=pow(smoothstep(.11,.34,field),2.2);','// Weighted octaves retain dark lanes without multiplying density to zero.\n    float porous=pow(smoothstep(.24,.68,field/max(weightSum,.001)),1.35);')
    return s
edit(skill/'assets/preview/styles.glsl',style_edit)
print('Defaults, shell switch and shared dust source updated')
