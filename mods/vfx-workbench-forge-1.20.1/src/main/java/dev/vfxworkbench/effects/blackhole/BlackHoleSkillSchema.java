package dev.vfxworkbench.effects.blackhole;

import dev.vfxworkbench.core.*;
import java.util.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** World-space presets of the existing black-hole kernel, with editable shape. */
public final class BlackHoleSkillSchema {
    public static final String ID="black-hole";
    public static final List<String> FORMS=List.of("granular","dust","cinematic","ribbons","classic","recipe","gold","ice","violet","lens","crimson");
    private static String appliedForm="granular";
    private static Map<String,String> legacyDefaults;
    public static void register(){
        var p=new ArrayList<ParamSpec>();
        p.add(ParamSpec.builder("form","形态预设",ParamType.ENUM).group("形态").enumValues(FORMS).defaultValue("granular").build());
        num(p,"scale","世界尺寸","形态",1.6,.2,4,.05,false);
        num(p,"distance","放置距离","形态",14,6,64,1,false);
        num(p,"core","核心半径","形态",.58,.2,.95,.01,false);
        num(p,"inner","盘内半径","形态",.94,.55,1.65,.01,false);
        num(p,"outer","盘外半径","形态",2.9,2,8,.01,false);
        num(p,"thickness","盘面厚度","形态",.055,.045,.35,.005,false);
        num(p,"tilt","盘面倾角","形态",0,-90,90,1,false);
        num(p,"bend","射线偏折","光与材质",1.1,0,2,.02,false);
        num(p,"density","气体密度","光与材质",5,0,10,.1,false);
        num(p,"exposure","曝光","光与材质",.3,.01,2,.01,false);
        color(p,"coreColor","核心颜色","#000000");color(p,"hot","内盘颜色","#fff3c2");color(p,"cool","外盘颜色","#f04a0e");
        num(p,"speed","差速旋转","运动",.8,0,3,.02,false);
        num(p,"infallSpeed","盘纹内流","运动",.22,0,2,.01,false);
        num(p,"seed","纹理种子","运动",17,0,65535,1,true);
        bool(p,"lens","扭曲世界背景","光与材质",true);
        p.add(ParamSpec.builder("lensAffectsPlayer","透镜影响玩家",ParamType.BOOL).group("光与材质")
            .help("关闭时保持玩家模型完整；开启后允许玩家模型参与透镜畸变，不受前后位置保护限制。")
            .visibleWhen(s->s.getBool("lens")).defaultValue("false").build());
        num(p,"lensStrength","背景透镜强度","光与材质",.7,0,1,.01,false);
        num(p,"lensRadius","背景透镜范围","光与材质",2.4,.2,12,.01,false);
        bool(p,"particles","吸入粒子","粒子",true);
        num(p,"particleCount","粒子数量","粒子",640,0,2048,1,true);
        num(p,"particleSpeed","粒子速度","粒子",1,0,4,.05,false);
        num(p,"particleSize","粒子尺寸","粒子",.022,.005,.15,.001,false);
        num(p,"particleOuter","粒子外范围","粒子",5.4,3.2,8,.1,false);
        num(p,"particleBrightness","粒子亮度","粒子",1.25,0,5,.05,false);
        color(p,"particleCold","外粒子颜色","#f26318");color(p,"particleHot","内粒子颜色","#ffefb1");
        num(p,"halo","光晕总强度","光晕",1,0,3,.05,false);
        num(p,"haloRing","临界光环","光晕",1,0,3,.05,false);
        num(p,"haloDisk","盘面光晕","光晕",1,0,3,.05,false);
        num(p,"haloAura","外层光晕","光晕",1,0,3,.05,false);
        p.add(ParamSpec.builder("diskStyle","盘面样式",ParamType.ENUM).group("形态").enumValues(List.of("classic","granular","ribbons","dust","cinematic")).defaultValue("granular").build());
        String[] extras={"beaming|方向增亮|0|3|0","diskGradientPosition|盘面渐变位置|0|1|0.5","coreGradientStrength|核心边缘渐变|0|1|0","bandDetail|光带细节|1|24|12","dustScale|尘埃尺度|0.1|12|4","dustFill|尘埃密度|0|1|0.375","filament|细丝强度|0|3|0","spiral|螺旋强度|0|3|0.32","haloRadius|光环半径倍率|0.1|4|1","haloWidth|光环宽度|0.001|1|0.11","haloSharpness|光环锐度|0.1|12|2","haloGradientMix|光环渐变混合|0|1|1","haloDiskGradientMix|盘晕渐变混合|0|1|1","haloAuraGradientMix|外晕渐变混合|0|1|0","jetStrength|极向喷流强度|0|5|0","jetLength|喷流长度|0.5|16|5.5","jetWidth|喷流宽度|0.01|2|0.24"};
        for(String definition:extras){String[] d=definition.split("\\|");num(p,d[0],d[1],d[0].startsWith("halo")?"光晕":d[0].startsWith("jet")?"喷流":"光与材质",Double.parseDouble(d[4]),Double.parseDouble(d[2]),Double.parseDouble(d[3]),.001,false);}
        String[] tints={"diskMid|盘面中间色|#8b4dff","coreEdgeColor|核心边缘色|#000000","haloGradientStart|光环渐变起点|#120808","haloGradientEnd|光环渐变终点|#000000","haloDiskInnerColor|盘晕内侧色|#77746e","haloDiskOuterColor|盘晕外侧色|#a15add","haloAuraInnerColor|外晕内侧色|#e6c7a8","haloAuraOuterColor|外晕外侧色|#9ca6c9","jetBaseColor|喷流根部色|#8bd8ff","jetTipColor|喷流末端色|#d0c8fe"};
        for(String definition:tints){String[] d=definition.split("\\|");color(p,d[0],d[1],d[2]);}
        p.add(ParamSpec.builder("steps","射线采样档位",ParamType.ENUM).group("质量").enumValues(List.of("96","160","256")).defaultValue("160").build());
        bool(p,"playing","播放动画","运动",true);
        num(p,"time","动画时间","运动",0,0,100000,.1,false);
        legacyDefaults=new LinkedHashMap<>();
        for(var spec:p)legacyDefaults.put(spec.key,spec.defaultValue);
        p=recipeDefaults(p);
        ParamRegistry.register(new SkillSchema(ID,"黑洞与吸积盘",p));
        appliedForm="granular";
        var state=ParamRegistry.state(ID);
        Runnable changed=()->{String form=state.get("form");if(!form.equals(appliedForm)){appliedForm=form;applyForm(state,form);}};
        state.onHotChange(changed);state.onResourceChange(changed);
    }
    private static ArrayList<ParamSpec> recipeDefaults(List<ParamSpec> specs){
        JsonObject root;
        try(var stream=BlackHoleSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/recipes/black-hole-default.json")){
            if(stream==null)throw new IllegalStateException("缺少黑洞默认配方");
            root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException ex){throw new IllegalStateException("无法读取黑洞默认配方",ex);}
        if(!root.get("schema").getAsString().equals("black-hole-preview/v2"))throw new IllegalArgumentException("黑洞默认配方格式不匹配");
        var values=root.getAsJsonObject("parameters");var result=new ArrayList<ParamSpec>();
        for(var spec:specs){
            String d=spec.defaultValue;
            // Presets now start with a disk perpendicular to the ground. The
            // original recipe remains untouched and retains its importable tilt.
            if(values.has(spec.key)&&!spec.key.equals("tilt"))d=values.get(spec.key).getAsString();
            if(spec.key.equals("distance")&&values.has("viewDistance"))d=Double.toString(values.get("viewDistance").getAsDouble()*1.6);
            if(spec.key.equals("lens")&&values.has("lensBackground"))d=Boolean.toString(values.get("lensBackground").getAsDouble()>0);
            if((spec.key.equals("time")||spec.key.equals("playing"))&&root.has(spec.key))d=root.get(spec.key).getAsString();
            if(spec.type==ParamType.FLOAT||spec.type==ParamType.INT){double v=Double.parseDouble(d);if(!Double.isFinite(v)||v<spec.min||v>spec.max)throw new IllegalArgumentException("默认配方参数越界: "+spec.key);}
            if(spec.type==ParamType.ENUM&&!spec.enumValues.contains(d))throw new IllegalArgumentException("默认配方枚举无效: "+spec.key);
            if(spec.type==ParamType.COLOR&&!d.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("默认配方颜色无效: "+spec.key);
            result.add(ParamSpec.builder(spec.key,spec.label,spec.type).group(spec.group).help(spec.help).visibleWhen(spec::isVisible).min(spec.min).max(spec.max).step(spec.step).intStep(spec.intStep).enumValues(spec.enumValues).reload(spec.reload).defaultValue(d).build());
        }
        return result;
    }
    private static void num(List<ParamSpec> p,String key,String label,String group,double d,double lo,double hi,double step,boolean integer){p.add(ParamSpec.builder(key,label,integer?ParamType.INT:ParamType.FLOAT).group(group).min(lo).max(hi).step(step).intStep(integer).defaultValue(Double.toString(d)).build());}
    private static void color(List<ParamSpec> p,String key,String label,String d){p.add(ParamSpec.builder(key,label,ParamType.COLOR).group("配色").defaultValue(d).build());}
    private static void bool(List<ParamSpec> p,String key,String label,String group,boolean d){p.add(ParamSpec.builder(key,label,ParamType.BOOL).group(group).defaultValue(Boolean.toString(d)).build());}
    /** Older recipes omitted this preference; loading one restores its original protected-player behavior. */
    public static Map<String,String> recipeParameters(Map<String,String> source){
        // Applying a form resets its appearance baseline. Always apply it
        // before explicit recipe fields, regardless of their JSON key order.
        var values=new LinkedHashMap<String,String>();if(source.containsKey("form"))values.put("form",source.get("form"));
        values.putAll(source);values.putIfAbsent("lensAffectsPlayer","false");return values;
    }
    public static void applyForm(ParamState s,String form){
        if(!FORMS.contains(form))throw new IllegalArgumentException("未知黑洞形态: "+form);
        // Apply the entire appearance baseline. Keep placement and the player lens preference.
        for(var p:s.schema.params)if(!Set.of("form","scale","distance","lensAffectsPlayer").contains(p.key))s.set(p.key,(form.equals("granular")||form.equals("dust")||form.equals("cinematic")||form.equals("ribbons")||form.equals("classic"))?p.defaultValue:legacyDefaults.get(p.key));
        switch(form){
            case "dust"->{s.set("diskStyle","dust");s.set("particles","false");s.set("halo","1.2");}
            case "cinematic"->{s.set("diskStyle","cinematic");s.set("particles","false");s.set("filament","1");s.set("spiral",".5");}
            case "ribbons"->{s.set("diskStyle","ribbons");s.set("particles","false");s.set("filament","1.4");}
            case "classic"->{s.set("diskStyle","classic");}
            case "granular"->{s.set("diskStyle","granular");s.set("particles","false");}
            case "ice"->{s.set("outer","3.4");s.set("thickness",".045");s.set("hot","#d9ffff");s.set("cool","#087dff");s.set("particleCold","#129aff");s.set("particleHot","#d9ffff");s.set("speed",".4");s.set("bend",".65");}
            case "violet"->{s.set("core",".78");s.set("inner","1.05");s.set("thickness",".26");s.set("hot","#efcaff");s.set("cool","#792bff");s.set("particleCold","#792bff");s.set("particleHot","#efcaff");s.set("haloAura","2.2");s.set("speed",".3");}
            case "lens"->{s.set("core",".9");s.set("inner","1.12");s.set("density","0");s.set("halo","0");s.set("particles","false");s.set("bend","1.7");}
            case "crimson"->{s.set("core",".4");s.set("inner",".72");s.set("outer","2.4");s.set("thickness",".14");s.set("hot","#fff0be");s.set("cool","#f01835");s.set("particleCold","#ff2545");s.set("particleHot","#fff0be");s.set("speed","1.6");s.set("infallSpeed",".7");s.set("particleSpeed","1.8");}
            default->{}
        }
    }
    public static float inner(ParamState s){return Math.max(s.getFloat("inner"),s.getFloat("core")+.12f);}
    public static float outer(ParamState s){return Math.max(s.getFloat("outer"),inner(s)+.5f);}
}
