package dev.vfxworkbench.effects.blackhole;

import dev.vfxworkbench.core.*;
import java.util.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Shared world-space black/white-hole material presets and atomic recipe bridge. */
public final class BlackHoleSkillSchema {
    public static final String ID="black-hole";
    private static final String WHITE_CORE_EMISSION="3.95";
    private static final Set<String> OUTFLOW_FIELDS=Set.of("jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor");
    private static final Set<String> DISK_GLOW_FIELDS=Set.of("diskBloom","diskBloomRadius");
    private static final Set<String> CORE_GLOW_FIELDS=Set.of("coreGlow","coreGlowRadius");
    public static final List<String> FORMS=List.of("granular","dust","cinematic","ribbons","classic","recipe","gold","ice","violet","lens","crimson","white-ring");
    /** Curated UI presets; FORMS also accepts historical and imported recipes. */
    public static final List<String> PRESET_FORMS=List.of("granular","dust","ribbons","lens","white-ring");
    private static String appliedForm="granular";
    private static Map<String,String> legacyDefaults;
    public static void register(){
        var p=new ArrayList<ParamSpec>();
        p.add(ParamSpec.builder("form","形态预设",ParamType.ENUM).group("形态").enumValues(FORMS).defaultValue("granular").build());
        p.add(ParamSpec.builder("objectMode","天体模式",ParamType.ENUM).group("形态").enumValues(List.of("black","white"))
            .help("black 向内，white 向外，保持正质量透镜方向。白洞导出为 world/v2；网页黑洞工作台暂不支持白洞。")
            .defaultValue("black").build());
        num(p,"scale","世界尺寸","形态",1.6,.2,4,.05,false);
        num(p,"distance","放置距离","形态",14,6,64,1,false);
        num(p,"core","核心半径","形态",.58,.2,.95,.01,false);
        num(p,"inner","盘内半径","形态",.94,.22,3,.01,false);
        num(p,"outer","盘外半径","形态",2.9,2,32,.01,false);
        num(p,"thickness","盘面厚度","形态",.055,.045,.35,.005,false);
        num(p,"tilt","盘面倾角","形态",0,-90,90,1,false);
        num(p,"bend","射线偏折","光与材质",1.1,0,2,.02,false);
        num(p,"density","气体密度","光与材质",5,0,10,.1,false);
        num(p,"exposure","曝光","光与材质",.3,.01,2,.01,false);
        p.add(ParamSpec.builder("coreEmission","核心发光倍率",ParamType.FLOAT).group("光与材质").min(0).max(4).step(.05)
            .help("控制核心及其边缘颜色的辐射，并与白洞核心光芒联动；黑色核心仍为黑色，不控制盘面光晕。")
            .defaultValue("1").build());
        whiteNum(p,"coreGlow","核心光芒强度","光与材质",0,0,3,.05,"白洞专用：核心体积柔光与核心专用屏幕扩散的强度，与核心发光倍率联动；独立于盘晕和外扩光壳，不是场景动态光源，不会照亮地形。");
        whiteNum(p,"coreGlowRadius","核心光芒范围倍率","光与材质",1.8,1.05,4,.05,"白洞专用：核心体积柔光的世界冠层范围与核心专用屏幕扩散的投影视觉宽度，相对核心半径计算；独立于盘晕和外扩光壳，不是场景动态光源，不会照亮地形。");
        color(p,"coreColor","核心颜色","#000000");color(p,"hot","内盘颜色","#fff3c2");color(p,"cool","外盘颜色","#f04a0e");
        num(p,"speed","差速旋转","运动",.8,0,3,.02,false);
        p.add(ParamSpec.builder("infallSpeed","盘纹流速",ParamType.FLOAT).group("运动").min(0).max(2).step(.01)
            .help("盘面纹理的径向速度：black 向内，white 向外；与差速旋转和独立粒子速度分开。")
            .defaultValue("0.22").build());
        num(p,"seed","纹理种子","运动",17,0,65535,1,true);
        bool(p,"lens","扭曲世界背景","光与材质",true);
        p.add(ParamSpec.builder("lensAffectsPlayer","透镜影响玩家",ParamType.BOOL).group("光与材质")
            .help("关闭时保持玩家模型完整；开启后允许玩家模型参与透镜畸变，不受前后位置保护限制。")
            .visibleWhen(s->s.getBool("lens")).defaultValue("false").build());
        num(p,"lensStrength","背景透镜强度","光与材质",.7,0,1,.01,false);
        num(p,"lensRadius","背景透镜范围","光与材质",2.4,.2,12,.01,false);
        p.add(ParamSpec.builder("particles","盘面粒子",ParamType.BOOL).group("粒子")
            .help("独立盘面尘粒：black 从外侧螺旋吸入；white 从内侧出生并螺旋外散。两种模式都在出生和消失时淡出。")
            .defaultValue("true").build());
        num(p,"particleCount","粒子数量","粒子",640,0,2048,1,true);
        num(p,"particleSpeed","粒子速度","粒子",1,0,4,.05,false);
        num(p,"particleSize","粒子尺寸","粒子",.022,.005,.15,.001,false);
        num(p,"particleOuter","粒子外范围","粒子",5.4,3.2,8,.1,false);
        num(p,"particleBrightness","粒子亮度","粒子",1.25,0,5,.05,false);
        color(p,"particleCold","外粒子颜色","#f26318");color(p,"particleHot","内粒子颜色","#ffefb1");
        p.add(ParamSpec.builder("diskBloom","盘面发光扩散",ParamType.FLOAT).group("光晕").min(0).max(3).step(.05)
            .visibleWhen(s->s.get("diskStyle").equals("ribbons")&&s.get("objectMode").equals("black"))
            .help("层叠光带专用：只扩散盘面 HDR 辐射，不提取天空、地形或 UI；不代表照亮地形。").defaultValue("0").build());
        p.add(ParamSpec.builder("diskBloomRadius","盘面扩散范围",ParamType.FLOAT).group("光晕").min(.25).max(3).step(.05)
            .visibleWhen(s->s.get("diskStyle").equals("ribbons")&&s.get("objectMode").equals("black"))
            .help("相对盘外半径的投影计算发光扩散范围。").defaultValue("1").build());
        num(p,"halo","光晕总强度","光晕",1,0,3,.05,false);
        num(p,"haloRing","临界光环","光晕",1,0,3,.05,false);
        num(p,"haloDisk","盘面光晕","光晕",1,0,3,.05,false);
        num(p,"haloAura","外层光晕","光晕",1,0,3,.05,false);
        p.add(ParamSpec.builder("diskStyle","盘面样式",ParamType.ENUM).group("形态").enumValues(List.of("classic","granular","ribbons","dust","cinematic")).defaultValue("granular").build());
        String[] extras={"beaming|方向增亮|0|3|0","diskGradientPosition|盘面渐变位置|0|1|0.5","coreGradientStrength|核心边缘渐变|0|1|0","bandDetail|光带细节|1|24|12","dustScale|尘埃尺度|0.1|12|4","dustFill|尘埃密度|0|1|0.375","filament|细丝强度|0|3|0","spiral|螺旋强度|0|3|0.32","haloRadius|光环半径倍率|0.1|4|1","haloWidth|光环宽度|0.001|1|0.11","haloSharpness|光环锐度|0.1|12|2","haloGradientMix|光环渐变混合|0|1|1","haloDiskGradientMix|盘晕渐变混合|0|1|1","haloAuraGradientMix|外晕渐变混合|0|1|0","jetStrength|极向喷流强度|0|5|0","jetLength|喷流长度|0.5|16|5.5","jetWidth|喷流宽度|0.01|2|0.24"};
        for(String definition:extras){String[] d=definition.split("\\|");num(p,d[0],d[1],d[0].startsWith("halo")?"光晕":d[0].startsWith("jet")?"喷流":"光与材质",Double.parseDouble(d[4]),Double.parseDouble(d[2]),Double.parseDouble(d[3]),.001,false);}
        whiteNum(p,"jetSpeed","喷流输运速度","喷流",1,0,4,.05,"白洞专用：沿双极喷流向外输运脉冲；设为 0 保持当前相位，恢复后连续播放。");
        p.add(ParamSpec.builder("shellEnabled","外扩光圈开关",ParamType.BOOL).group("外扩光壳").help("单独开启或关闭循环外扩光圈；关闭后保留光圈参数，不影响核心光芒、盘面或透镜。").visibleWhen(v->v.get("objectMode").equals("white")).defaultValue("true").build());
        whiteNum(p,"shellStrength","光壳强度","外扩光壳",0,0,3,.05,"白洞专用：从核心向外扩张的光壳亮度；0 关闭光壳。");
        whiteNum(p,"shellSpeed","光壳扩张速度","外扩光壳",.7,0,4,.05,"白洞专用：光壳向外扩张的速度；设为 0 保持当前相位，恢复后连续播放。");
        whiteNum(p,"shellRadius","光壳最大半径","外扩光壳",5,1,8,.1,"白洞专用：光壳淡出前的最大半径，单位与核心、盘面半径一致。");
        whiteNum(p,"shellWidth","光壳厚度","外扩光壳",.12,.02,1,.01,"白洞专用：扩张光壳的径向厚度。");
        p.add(ParamSpec.builder("shellColor","光壳颜色",ParamType.COLOR).group("外扩光壳")
            .help("白洞专用：外扩光壳的发光颜色。").visibleWhen(s->s.get("objectMode").equals("white")).defaultValue("#72dfff").build());
        String[] tints={"diskMid|盘面中间色|#8b4dff","coreEdgeColor|核心边缘色|#000000","haloGradientStart|光环渐变起点|#120808","haloGradientEnd|光环渐变终点|#000000","haloDiskInnerColor|盘晕内侧色|#77746e","haloDiskOuterColor|盘晕外侧色|#a15add","haloAuraInnerColor|外晕内侧色|#e6c7a8","haloAuraOuterColor|外晕外侧色|#9ca6c9","jetBaseColor|喷流根部色|#8bd8ff","jetTipColor|喷流末端色|#d0c8fe"};
        for(String definition:tints){String[] d=definition.split("\\|");color(p,d[0],d[1],d[2]);}
        p.add(ParamSpec.builder("steps","射线采样档位",ParamType.ENUM).group("质量").enumValues(List.of("96","160","256")).defaultValue("160").build());
        bool(p,"playing","播放动画","运动",true);
        num(p,"time","动画时间","运动",0,0,100000,.1,false);
        legacyDefaults=new LinkedHashMap<>();
        for(var spec:p)legacyDefaults.put(spec.key,spec.defaultValue);
        p=recipeDefaults(p);
        ParamRegistry.register(new SkillSchema(ID,"黑洞 / 白洞",p));
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
            // Presets now start with a disk parallel to the ground. The
            // original recipe remains untouched and retains its importable tilt.
            if(values.has(spec.key)&&!spec.key.equals("tilt"))d=values.get(spec.key).getAsString();
            if(spec.key.equals("distance")&&values.has("viewDistance"))d=Double.toString(values.get("viewDistance").getAsDouble()*1.6);
            if(spec.key.equals("lens")&&values.has("lensBackground"))d=Boolean.toString(values.get("lensBackground").getAsDouble()>0);
            if((spec.key.equals("time")||spec.key.equals("playing"))&&root.has(spec.key))d=root.get(spec.key).getAsString();
            if(spec.type==ParamType.FLOAT||spec.type==ParamType.INT){double v=Double.parseDouble(d);if(!Double.isFinite(v)||v<spec.min||v>spec.max)throw new IllegalArgumentException("默认配方参数越界: "+spec.key);}
            if(spec.type==ParamType.ENUM&&!spec.enumValues.contains(d))throw new IllegalArgumentException("默认配方枚举无效: "+spec.key);
            if(spec.type==ParamType.COLOR&&!d.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("默认配方颜色无效: "+spec.key);
            if(spec.key.equals("scale"))d="1.6";
            if(spec.key.equals("distance"))d="32";
            if(spec.key.equals("core"))d="0.95";
            if(spec.key.equals("inner"))d="2.12";
            if(spec.key.equals("outer"))d="8";
            if(spec.key.equals("tilt"))d="0";
            result.add(ParamSpec.builder(spec.key,spec.label,spec.type).group(spec.group).help(spec.help).visibleWhen(spec::isVisible).min(spec.min).max(spec.max).step(spec.step).intStep(spec.intStep).enumValues(spec.enumValues).reload(spec.reload).defaultValue(d).build());
        }
        return result;
    }
    private static void num(List<ParamSpec> p,String key,String label,String group,double d,double lo,double hi,double step,boolean integer){p.add(ParamSpec.builder(key,label,integer?ParamType.INT:ParamType.FLOAT).group(group).min(lo).max(hi).step(step).intStep(integer).defaultValue(Double.toString(d)).build());}
    private static void whiteNum(List<ParamSpec> p,String key,String label,String group,double d,double lo,double hi,double step,String help){p.add(ParamSpec.builder(key,label,ParamType.FLOAT).group(group).min(lo).max(hi).step(step).help(help).visibleWhen(s->s.get("objectMode").equals("white")).defaultValue(Double.toString(d)).build());}
    private static void color(List<ParamSpec> p,String key,String label,String d){p.add(ParamSpec.builder(key,label,ParamType.COLOR).group("配色").defaultValue(d).build());}
    private static void bool(List<ParamSpec> p,String key,String label,String group,boolean d){p.add(ParamSpec.builder(key,label,ParamType.BOOL).group(group).defaultValue(Boolean.toString(d)).build());}
    /** Validate completely before applying; legacy recipes restore black-hole/player defaults. */
    public static Map<String,String> recipeParameters(Map<String,String> source){
        // Applying a form resets its appearance baseline. Always apply it
        // before explicit recipe fields, regardless of their JSON key order.
        var values=new LinkedHashMap<String,String>();if(source.containsKey("form"))values.put("form",source.get("form"));
        values.putAll(source);
        values.putIfAbsent("lensAffectsPlayer","false");
        boolean whitePreset="white-ring".equals(source.get("form"));
        values.putIfAbsent("objectMode",whitePreset?"white":"black");
        values.putIfAbsent("coreEmission",whitePreset?WHITE_CORE_EMISSION:"1");
        boolean partialRibbon="ribbons".equals(source.get("form"))&&source.size()==1;
        values.putIfAbsent("diskBloom",partialRibbon?".45":"0");values.putIfAbsent("diskBloomRadius","1");
        var schema=ParamRegistry.schema(ID);
        if(schema==null)throw new IllegalStateException("黑洞参数尚未注册");
        // A full pre-outflow world/v2 recipe describes its entire appearance.
        // Keep absent new effects disabled, while a partial white-ring request
        // deliberately selects the current upgraded preset.
        boolean completeOldAppearance=schema.params.stream().filter(spec->!OUTFLOW_FIELDS.contains(spec.key)&&!CORE_GLOW_FIELDS.contains(spec.key)&&!DISK_GLOW_FIELDS.contains(spec.key)&&!spec.key.equals("shellEnabled")).allMatch(spec->source.containsKey(spec.key));
        values.putIfAbsent("shellEnabled",whitePreset&&!completeOldAppearance?"false":"true");
        if(whitePreset&&!completeOldAppearance&&!source.containsKey("lensAffectsPlayer"))values.put("lensAffectsPlayer","true");
        if(!whitePreset||completeOldAppearance)for(String key:OUTFLOW_FIELDS)values.putIfAbsent(key,schema.get(key).defaultValue);
        // Both the original 66-field and outflow 72-field world/v2 exports
        // retain their existing core appearance when the new glow is absent.
        if(!whitePreset||completeOldAppearance)for(String key:CORE_GLOW_FIELDS)values.putIfAbsent(key,schema.get(key).defaultValue);
        for(var entry:values.entrySet()){
            var spec=schema.get(entry.getKey());if(spec==null)continue;
            String value=entry.getValue();
            if(value==null)throw new IllegalArgumentException("参数为空: "+spec.key);
            switch(spec.type){
                case FLOAT,INT->{
                    double number;
                    try{number=Double.parseDouble(value);}catch(NumberFormatException ex){throw new IllegalArgumentException("参数必须为数值: "+spec.key,ex);}
                    if(!Double.isFinite(number)||number<spec.min||number>spec.max)throw new IllegalArgumentException("参数越界: "+spec.key);
                    if(spec.type==ParamType.INT&&number!=Math.rint(number))throw new IllegalArgumentException("参数必须为整数: "+spec.key);
                }
                case BOOL->{if(!Set.of("true","false").contains(value))throw new IllegalArgumentException("参数必须为布尔值: "+spec.key);}
                case ENUM->{
                    // JSON numeric quality values may be written as 160.0.
                    if(spec.key.equals("steps")){
                        try{double number=Double.parseDouble(value);if(number==Math.rint(number))value=Integer.toString((int)number);}catch(NumberFormatException ignored){}
                        values.put(spec.key,value);
                    }
                    if(!spec.enumValues.contains(value))throw new IllegalArgumentException("未知参数选项: "+spec.key);
                }
                case COLOR->{if(!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("颜色格式无效: "+spec.key);}
                default->{}
            }
        }
        return values;
    }
    /** Stage form defaults and explicit values, then publish one complete live state. */
    public static void loadRecipe(ParamState state,Map<String,String> source){
        var values=recipeParameters(source);
        var staged=new ParamState(state.schema);
        staged.load(state.snapshot());
        if(values.containsKey("form"))applyForm(staged,values.get("form"));
        staged.load(values);
        // The live form listener must not reset explicit fields after commit.
        if(state==ParamRegistry.state(ID))appliedForm=staged.get("form");
        state.loadAtomic(staged.snapshot());
    }
    private static final Set<String> PREVIEW_METADATA=Set.of("name","particleMid","particleGradientPosition","skyColor","skyColorFar","skyGradientPosition","backgroundSpin","orbitSpeed","bloom","bloomRadius","skyStrength","yaw","pitch","mode","backgroundStyle","viewDistance","lensBackground","lensEnabled");
    /** vfx raw/saved values and world/v2 support the new mode; older formats remain historical. */
    public static Map<String,String> decodeRecipe(String text,ParamState state){
        JsonElement parsed=JsonParser.parseString(text);
        if(!parsed.isJsonObject())throw new IllegalArgumentException("配方必须是 JSON 对象");
        JsonObject root=parsed.getAsJsonObject();boolean strict=root.has("schema");boolean preview=false,legacyFormat=false;
        if(strict){
            if(!root.get("schema").isJsonPrimitive())throw new IllegalArgumentException("配方格式必须为文本");
            String format=root.get("schema").getAsString();
            if(!Set.of("black-hole-preview/v1","black-hole-preview/v2","black-hole-world/v1","black-hole-world/v2").contains(format))throw new IllegalArgumentException("未知黑洞配方格式: "+format);
            preview=format.startsWith("black-hole-preview/");
            legacyFormat=!format.equals("black-hole-world/v2");
        }
        JsonObject parameters=root;
        if(root.has("parameters")){
            if(!root.get("parameters").isJsonObject())throw new IllegalArgumentException("parameters 必须为对象");
            parameters=root.getAsJsonObject("parameters");
        }else if(root.has("values")){
            if(!root.get("values").isJsonObject())throw new IllegalArgumentException("values 必须为对象");
            parameters=root.getAsJsonObject("values");
        }else if(strict)throw new IllegalArgumentException("配方缺少 parameters");
        var values=new LinkedHashMap<String,String>();
        for(var entry:parameters.entrySet()){
            String key=entry.getKey();JsonElement value=entry.getValue();var spec=state.schema.get(key);
            if(legacyFormat&&(key.equals("objectMode")||key.equals("coreEmission")||OUTFLOW_FIELDS.contains(key)||CORE_GLOW_FIELDS.contains(key)||key.equals("shellEnabled")))throw new IllegalArgumentException("白洞字段需要 black-hole-world/v2 格式: "+key);
            if(strict&&spec==null&&!PREVIEW_METADATA.contains(key))throw new IllegalArgumentException("未知配方参数: "+key);
            if(!value.isJsonPrimitive())throw new IllegalArgumentException("参数必须为单值: "+key);
            if(strict&&spec!=null){
                var primitive=value.getAsJsonPrimitive();
                if((spec.type==ParamType.FLOAT||spec.type==ParamType.INT||key.equals("steps"))&&!primitive.isNumber())throw new IllegalArgumentException("参数必须为 JSON 数值: "+key);
                if(spec.type==ParamType.BOOL&&!primitive.isBoolean())throw new IllegalArgumentException("参数必须为 JSON 布尔值: "+key);
                if((spec.type==ParamType.COLOR||spec.type==ParamType.ENUM&&!key.equals("steps"))&&!primitive.isString())throw new IllegalArgumentException("参数必须为 JSON 文本: "+key);
            }
            values.put(key,value.getAsString());
        }
        if(legacyFormat&&"white-ring".equals(values.get("form")))throw new IllegalArgumentException("白洞预设需要 black-hole-world/v2 格式");
        if(preview){
            values.putIfAbsent("form","recipe");
        }
        for(String key:List.of("time","playing"))if(root!=parameters&&root.has(key)){
            JsonElement value=root.get(key);
            if(!value.isJsonPrimitive()||(strict&&(key.equals("time")?!value.getAsJsonPrimitive().isNumber():!value.getAsJsonPrimitive().isBoolean())))throw new IllegalArgumentException("动画字段格式无效: "+key);
            values.put(key,value.getAsString());
        }
        if(values.containsKey("viewDistance")&&!values.containsKey("distance")){
            double scale=values.containsKey("scale")?Double.parseDouble(values.get("scale")):state.getDouble("scale");
            values.put("distance",Double.toString(Double.parseDouble(values.get("viewDistance"))*scale));
        }
        if(values.containsKey("lensBackground")){
            double strength=Double.parseDouble(values.get("lensBackground"));
            if(!Double.isFinite(strength)||strength<0||strength>1)throw new IllegalArgumentException("背景透镜强度越界");
            values.putIfAbsent("lens",Boolean.toString(strength>0));values.putIfAbsent("lensStrength",Double.toString(strength));
        }
        if(values.containsKey("lensEnabled")){
            String enabled=values.get("lensEnabled");if(!Set.of("true","false").contains(enabled))throw new IllegalArgumentException("lensEnabled 必须为布尔值");
            if(enabled.equals("false"))values.put("lens","false");
        }
        return recipeParameters(values);
    }
    public static void importRecipe(String text){var state=ParamRegistry.state(ID);loadRecipe(state,decodeRecipe(text,state));}
    /** Typed world/v2 export. The legacy browser workbench does not consume white-hole recipes. */
    public static String exportRecipe(ParamState state){
        var parameters=new JsonObject();
        for(var entry:recipeParameters(state.snapshot()).entrySet()){
            var spec=state.schema.get(entry.getKey());if(spec==null)continue;
            if(spec.type==ParamType.BOOL)parameters.addProperty(spec.key,Boolean.parseBoolean(entry.getValue()));
            else if(spec.type==ParamType.INT||spec.key.equals("steps"))parameters.addProperty(spec.key,(int)Double.parseDouble(entry.getValue()));
            else if(spec.type==ParamType.FLOAT)parameters.addProperty(spec.key,Double.parseDouble(entry.getValue()));
            else parameters.addProperty(spec.key,entry.getValue());
        }
        var root=new JsonObject();root.addProperty("schema","black-hole-world/v2");root.add("parameters",parameters);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }
    public static void applyForm(ParamState s,String form){
        if(!FORMS.contains(form))throw new IllegalArgumentException("未知黑洞形态: "+form);
        // Apply the entire appearance baseline. Keep placement and the player lens preference.
        for(var p:s.schema.params)if(!Set.of("form","scale","distance","lensAffectsPlayer").contains(p.key))s.set(p.key,(form.equals("granular")||form.equals("dust")||form.equals("cinematic")||form.equals("ribbons")||form.equals("classic"))?p.defaultValue:legacyDefaults.get(p.key));
        switch(form){
            case "dust"->{
                try(var stream=BlackHoleSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/recipes/dust-default.json")){
                    if(stream==null)throw new IllegalStateException("缺少尘埃盘配方");
                    var values=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("parameters");
                    for(var entry:values.entrySet())if(s.schema.get(entry.getKey())!=null&&!Set.of("form","scale","distance","lensAffectsPlayer").contains(entry.getKey()))s.set(entry.getKey(),entry.getValue().getAsString());
                }catch(java.io.IOException ex){throw new IllegalStateException("无法读取尘埃盘配方",ex);}
            }
            case "cinematic"->{
                try(var stream=BlackHoleSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/recipes/cinematic-default.json")){
                    if(stream==null)throw new IllegalStateException("缺少电影光带配方");
                    var values=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("parameters");
                    for(var entry:values.entrySet())if(s.schema.get(entry.getKey())!=null&&!Set.of("form","scale","distance","lensAffectsPlayer").contains(entry.getKey()))s.set(entry.getKey(),entry.getValue().getAsString());
                }catch(java.io.IOException ex){throw new IllegalStateException("无法读取电影光带配方",ex);}
            }
            case "ribbons"->{
                try(var stream=BlackHoleSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/recipes/ribbons-default.json")){
                    if(stream==null)throw new IllegalStateException("缺少层叠光带配方");
                    var values=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("parameters");
                    for(var entry:values.entrySet())if(s.schema.get(entry.getKey())!=null&&!Set.of("form","scale","distance","lensAffectsPlayer").contains(entry.getKey()))s.set(entry.getKey(),entry.getValue().getAsString());
                }catch(java.io.IOException ex){throw new IllegalStateException("无法读取层叠光带配方",ex);}
            }
            case "classic"->{
                try(var stream=BlackHoleSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/recipes/classic-default.json")){
                    if(stream==null)throw new IllegalStateException("缺少程序光带配方");
                    var values=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("parameters");
                    for(var entry:values.entrySet())if(s.schema.get(entry.getKey())!=null&&!Set.of("form","scale","distance","lensAffectsPlayer").contains(entry.getKey()))s.set(entry.getKey(),entry.getValue().getAsString());
                }catch(java.io.IOException ex){throw new IllegalStateException("无法读取程序光带配方",ex);}
            }
            case "granular"->{s.set("diskStyle","granular");s.set("particles","false");}
            case "ice"->{s.set("outer","3.4");s.set("thickness",".045");s.set("hot","#d9ffff");s.set("cool","#087dff");s.set("particleCold","#129aff");s.set("particleHot","#d9ffff");s.set("speed",".4");s.set("bend",".65");}
            case "violet"->{s.set("core",".78");s.set("inner","1.05");s.set("thickness",".26");s.set("hot","#efcaff");s.set("cool","#792bff");s.set("particleCold","#792bff");s.set("particleHot","#efcaff");s.set("haloAura","2.2");s.set("speed",".3");}
            case "lens"->{s.set("core",".9");s.set("inner","1.12");s.set("density","0");s.set("halo","0");s.set("particles","false");s.set("bend","1.7");}
            case "crimson"->{s.set("core",".4");s.set("inner",".72");s.set("outer","2.4");s.set("thickness",".14");s.set("hot","#fff0be");s.set("cool","#f01835");s.set("particleCold","#ff2545");s.set("particleHot","#fff0be");s.set("speed","1.6");s.set("infallSpeed",".7");s.set("particleSpeed","1.8");}
            case "white-ring"->{
                // One canonical resource drives the preset and importable default.
                try(var stream=BlackHoleSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/recipes/white-ring-default.json")){
                    if(stream==null)throw new IllegalStateException("缺少白洞默认配方");
                    var values=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("parameters");
                    for(var entry:values.entrySet())s.set(entry.getKey(),entry.getValue().getAsString());
                }catch(java.io.IOException ex){throw new IllegalStateException("无法读取白洞默认配方",ex);}
            }
            default->{}
        }
        // User geometry baseline shared by public black-hole presets only.
        // Imported explicit parameters are applied after this baseline.
        if(PRESET_FORMS.contains(form)&&!form.equals("white-ring")){
            s.set("core",".95");s.set("inner","2.12");s.set("outer","8");s.set("tilt","0");
        }
    }
    public static float inner(ParamState s){return Math.max(s.getFloat("inner"),s.getFloat("core")+.02f);}
    public static float outer(ParamState s){return Math.max(s.getFloat("outer"),inner(s)+.5f);}
}
