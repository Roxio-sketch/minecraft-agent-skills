package dev.blackholelab;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;

/** Independent, atomic world-material recipe bridge; browser-only fields are retained as metadata. */
final class BlackHoleRecipe {
    private static final String WHITE_CORE_EMISSION="3.95";
    private static final Set<String> OUTFLOW_FIELDS=Set.of("shellEnabled","jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor","coreGlow","coreGlowRadius","diskBloom","diskBloomRadius");
    private static final BlackHoleRecipe STATE=new BlackHoleRecipe();
    private final Map<String,String> values=defaults();
    static BlackHoleRecipe state(){return STATE;}
    static float inner(BlackHoleRecipe p){return Math.max(p.getFloat("inner"),p.getFloat("core")+.02f);}
    static float outer(BlackHoleRecipe p){return Math.max(p.getFloat("outer"),inner(p)+.5f);}
    String get(String key){return values.get(key);}
    float getFloat(String key){return Float.parseFloat(get(key));}
    double getDouble(String key){return Double.parseDouble(get(key));}
    int getInt(String key){return (int)getDouble(key);}
    boolean getBool(String key){return Boolean.parseBoolean(get(key));}
    int getColor(String key){return Integer.parseInt(get(key).replace("#",""),16);}
    void setBoolean(String key,boolean value){values.put(key,Boolean.toString(value));}
    void set(String key,String value){values.put(key,value);apply();}
    // Pure shader parameters must not reset command-mutated rotation/tilt.
    void setMaterialValue(String key,String value){
        if(!Set.of("shellEnabled","jetStrength","jetLength","jetWidth","jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor","coreGlow","coreGlowRadius","diskBloom","diskBloomRadius").contains(key))throw new IllegalArgumentException("Unknown external material setting: "+key);
        values.put(key,value);
    }
    void resetToDefaults(){values.clear();values.putAll(defaults());BlackHoleWorld.scale=1.6f;BlackHoleWorld.distance=14;BlackHoleWorld.lens=.7f;BlackHoleWorld.halo=BlackHoleWorld.haloRing=BlackHoleWorld.haloDisk=BlackHoleWorld.haloAura=1;BlackHoleParticles.enabled=true;BlackHoleParticles.count=640;BlackHoleParticles.speed=1;BlackHoleParticles.size=.022f;BlackHoleParticles.outer=5.4f;BlackHoleParticles.brightness=1.25f;apply();BlackHoleRender.frozenTime=-1;BlackHoleRender.resetClock();}
    Map<String,String> snapshot(){return new LinkedHashMap<>(values);}
    private static Map<String,String> defaults(){
        var v=new LinkedHashMap<String,String>();v.put("shellEnabled","true");
        String[] numbers={"core=.58","inner=.94","outer=2.9","thickness=.055","tilt=0","bend=1.1","density=5","exposure=.30","speed=.8","infallSpeed=.22","seed=17","steps=160","time=0","lensRadius=2.4","beaming=0","diskGradientPosition=0","coreGradientStrength=0","coreEmission=1","coreGlow=0","coreGlowRadius=1.8","diskBloom=0","diskBloomRadius=1","bandDetail=12","dustScale=4","dustFill=.375","filament=0","spiral=.32","haloRadius=1","haloWidth=.11","haloSharpness=2","haloGradientMix=0","haloDiskGradientMix=0","haloAuraGradientMix=0","jetStrength=0","jetLength=5.5","jetWidth=.24","jetSpeed=1","shellStrength=0","shellSpeed=.7","shellRadius=5","shellWidth=.12"};
        for(String entry:numbers){String[] p=entry.split("=");v.put(p[0],p[1]);}
        String[] colors={"hot=#fff3c2","cool=#f04a0e","coreColor=#000000","diskMid=#e39a68","coreEdgeColor=#000000","particleCold=#f26318","particleHot=#ffefb1","haloGradientStart=#ffd697","haloGradientEnd=#8853ca","haloDiskInnerColor=#ffdfad","haloDiskOuterColor=#a15add","haloAuraInnerColor=#f0b070","haloAuraOuterColor=#516bc4","jetBaseColor=#8bd8ff","jetTipColor=#6852e3","shellColor=#72dfff"};
        for(String entry:colors){String[] p=entry.split("=");v.put(p[0],p[1]);}
        v.put("diskStyle","granular");v.put("playing","true");v.put("lensAffectsPlayer","false");v.put("objectMode","black");return v;
    }
    /** The same world-only outward preset as the production workbench's white-ring. */
    static Map<String,String> whiteRing(){
        var v=defaults();
        String[] fields={
            "form=white-ring",
            "objectMode=white",
            "scale=1.8",
            "distance=39.0",
            "core=0.28",
            "inner=1.65",
            "outer=5.27",
            "thickness=0.15",
            "tilt=-1.0",
            "bend=1.06",
            "density=0.6",
            "exposure=0.76",
            "coreEmission=3.95",
            "coreGlow=0.8",
            "coreGlowRadius=1.05",
            "coreColor=#eefaff",
            "hot=#dffaff",
            "cool=#1265d8",
            "speed=2.0",
            "infallSpeed=1.46",
            "seed=46271",
            "lens=true",
            "lensAffectsPlayer=true",
            "lensStrength=0.5",
            "lensRadius=4.14",
            "particles=false",
            "particleCount=640",
            "particleSpeed=1.3",
            "particleSize=0.022",
            "particleOuter=6.2",
            "particleBrightness=1.6",
            "particleCold=#2787f3",
            "particleHot=#dffaff",
            "halo=0.0",
            "haloRing=0.6",
            "haloDisk=0.35",
            "haloAura=0.25",
            "diskStyle=classic",
            "beaming=0.0",
            "diskGradientPosition=0.52",
            "coreGradientStrength=0.65",
            "bandDetail=12.0",
            "dustScale=4.0",
            "dustFill=0.375",
            "filament=0.75",
            "spiral=0.7",
            "haloRadius=1.0",
            "haloWidth=0.11",
            "haloSharpness=2.0",
            "haloGradientMix=1.0",
            "haloDiskGradientMix=1.0",
            "haloAuraGradientMix=0.0",
            "jetStrength=0.0",
            "jetLength=6.201",
            "jetWidth=0.5",
            "jetSpeed=1.15",
            "shellStrength=0.28",
            "shellSpeed=0.7",
            "shellRadius=4.8",
            "shellWidth=0.1",
            "shellColor=#72dfff",
            "diskMid=#51c7ff",
            "coreEdgeColor=#58bfff",
            "haloGradientStart=#dcf8ff",
            "haloGradientEnd=#368eff",
            "haloDiskInnerColor=#afeaff",
            "haloDiskOuterColor=#1d70d4",
            "haloAuraInnerColor=#bfeaff",
            "haloAuraOuterColor=#2d67b0",
            "jetBaseColor=#eaffff",
            "jetTipColor=#72dfff",
            "steps=160",
            "playing=true",
            "time=37862.8",
            "shellEnabled=false"
        };
        for(String field:fields){int separator=field.indexOf('=');v.put(field.substring(0,separator),field.substring(separator+1));}
        return v;
    }
    void useWhiteRing(){values.clear();values.putAll(whiteRing());apply();BlackHoleRender.frozenTime=-1;BlackHoleRender.resetClock();}
    private static final Map<String,double[]> RANGES=new LinkedHashMap<>();
    static {
        String[] definitions={"core,.2,.95","inner,.22,3","outer,2,32","thickness,.045,.35","tilt,-90,90","bend,0,2","density,0,10","exposure,.01,2.5","speed,0,3","infallSpeed,0,2","seed,0,65535","time,0,100000","lensRadius,.2,16","beaming,0,3","diskGradientPosition,0,1","coreGradientStrength,0,1","bandDetail,1,24","dustScale,.1,12","dustFill,0,1","filament,0,3","spiral,0,3","haloRadius,.1,4","haloWidth,.001,1","haloSharpness,.1,12","haloGradientMix,0,1","haloDiskGradientMix,0,1","haloAuraGradientMix,0,1","jetStrength,0,5","jetLength,.5,16","jetWidth,.01,2","jetSpeed,0,4","shellStrength,0,3","shellSpeed,0,4","shellRadius,1,8","shellWidth,.02,1","scale,.2,8","distance,2,128","lensStrength,0,1","lensBackground,0,1","viewDistance,1.25,80","particleCount,0,4096","particleSpeed,0,4","particleSize,.005,.15","particleOuter,3.2,8","particleBrightness,0,5","halo,0,3","haloRing,0,3","haloDisk,0,3","haloAura,0,3"};
        for(String entry:definitions){String[] d=entry.split(",");RANGES.put(d[0],new double[]{Double.parseDouble(d[1]),Double.parseDouble(d[2])});}
        RANGES.put("coreEmission",new double[]{0,4});
        RANGES.put("diskBloom",new double[]{0,3});RANGES.put("diskBloomRadius",new double[]{.25,3});
        RANGES.put("coreGlow",new double[]{0,3});RANGES.put("coreGlowRadius",new double[]{1.05,4});
        String[] browser={"steps,96,256","particleGradientPosition,0,1","skyGradientPosition,0,1","backgroundSpin,-1,1","orbitSpeed,-.3,.3","bloom,0,2","bloomRadius,.5,3","skyStrength,0,2","yaw,-3.141592653589793,3.141592653589793","pitch,-1.3,1.3"};
        for(String entry:browser){String[] d=entry.split(",");RANGES.put(d[0],new double[]{Double.parseDouble(d[1]),Double.parseDouble(d[2])});}
    }
    /** Decode completely before changing live rendering. Missing player preference retains protection. */
    static Map<String,String> decode(JsonObject root){
        if(!root.has("schema")||!Set.of("black-hole-preview/v1","black-hole-preview/v2","black-hole-world/v1","black-hole-world/v2").contains(root.get("schema").getAsString()))throw new IllegalArgumentException("Unknown black-hole recipe schema");
        if(!root.has("parameters")||!root.get("parameters").isJsonObject())throw new IllegalArgumentException("Recipe parameters must be an object");
        boolean modern=root.get("schema").getAsString().equals("black-hole-world/v2");
        var input=root.getAsJsonObject("parameters");
        boolean whitePreset=modern&&input.has("form")&&input.get("form").isJsonPrimitive()&&input.get("form").getAsString().equals("white-ring");
        var result=whitePreset?whiteRing():defaults();
        // Complete pre-outflow v2 files keep omitted new layers disabled.
        // A partial white-ring request deliberately selects today's preset.
        boolean completeOldAppearance=whiteRing().keySet().stream().filter(key->!OUTFLOW_FIELDS.contains(key)).allMatch(input::has);
        if(!whitePreset||completeOldAppearance)for(String key:OUTFLOW_FIELDS)result.put(key,defaults().get(key));
        if(root.get("schema").getAsString().startsWith("black-hole-preview/")){
            // Match the workbench's omitted-layer contract, independently of
            // the fresh Forge showcase's granular/particle defaults.
            result.put("diskStyle","classic");result.put("particles","false");result.put("halo","0");result.put("infallSpeed","0");result.put("lensEnabled","true");result.put("lensBackground","1");result.put("lensRadius","12");result.put("playing","false");
        }
        for(var entry:root.getAsJsonObject("parameters").entrySet()){
            String key=entry.getKey();JsonElement value=entry.getValue();
            if(!modern&&(Set.of("objectMode","coreEmission").contains(key)||OUTFLOW_FIELDS.contains(key)))throw new IllegalArgumentException("White-hole fields require black-hole-world/v2: "+key);
            if(!result.containsKey(key)&&!RANGES.containsKey(key)&&!Set.of("lens","lensEnabled","lensAffectsPlayer","playing","particles","particleMid","skyColor","skyColorFar","name","mode","backgroundStyle","form").contains(key))throw new IllegalArgumentException("Unknown recipe parameter: "+key);
            if(!value.isJsonPrimitive())throw new IllegalArgumentException("Expected scalar: "+key);
            if(key.equals("objectMode")&&(!value.getAsJsonPrimitive().isString()||!Set.of("black","white").contains(value.getAsString())))throw new IllegalArgumentException("Invalid object mode");
            if(RANGES.containsKey(key)){
                if(!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected numeric JSON value: "+key);
                double number=value.getAsDouble();double[] range=RANGES.get(key);
                if(!Double.isFinite(number)||number<range[0]||number>range[1])throw new IllegalArgumentException("Out of range: "+key);
                if(Set.of("seed","steps","particleCount","bandDetail").contains(key)&&number!=Math.rint(number))throw new IllegalArgumentException("Expected integer: "+key);
            }
            if((defaults().containsKey(key)&&defaults().get(key).startsWith("#")||Set.of("particleMid","skyColor","skyColorFar").contains(key))&&!value.getAsString().matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid color: "+key);
            if(Set.of("playing","lensAffectsPlayer","lens","lensEnabled","particles","shellEnabled").contains(key)&&!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean: "+key);
            result.put(key,value.getAsString());
        }
        if(root.has("time")){if(!root.get("time").isJsonPrimitive()||!root.get("time").getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected numeric time");double t=root.get("time").getAsDouble();if(!Double.isFinite(t)||t<0||t>100000)throw new IllegalArgumentException("Invalid time");result.put("time",Double.toString(t));}
        if(root.has("playing")){if(!root.get("playing").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected playing boolean");result.put("playing",root.get("playing").getAsString());}
        if(!Set.of("classic","granular","ribbons","dust","cinematic").contains(result.get("diskStyle")))throw new IllegalArgumentException("Unknown disk style");
        if(result.containsKey("mode")&&!Set.of("scene","blade").contains(result.get("mode")))throw new IllegalArgumentException("Unknown preview mode");
        if(result.containsKey("backgroundStyle")&&!Set.of("stars","nebula","black").contains(result.get("backgroundStyle")))throw new IllegalArgumentException("Unknown preview background");
        if(result.containsKey("form")&&(!Set.of("granular","dust","cinematic","ribbons","classic","recipe","gold","ice","violet","lens","crimson","white-ring").contains(result.get("form"))||!modern&&result.get("form").equals("white-ring")))throw new IllegalArgumentException("Unknown world form");
        if(result.containsKey("name")&&result.get("name").length()>120)throw new IllegalArgumentException("Recipe name too long");
        if(!Set.of("96","160","256","96.0","160.0","256.0").contains(result.get("steps")))throw new IllegalArgumentException("Invalid quality");
        if(Double.parseDouble(result.get("inner"))+1e-9<Double.parseDouble(result.get("core"))+.02||Double.parseDouble(result.get("outer"))<Double.parseDouble(result.get("inner"))+.5)throw new IllegalArgumentException("Disk bounds overlap core");
        return result;
    }
    static Path recipePath(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/blackholelab/recipe.json");}
    static void load(){
        try{
            var decoded=decode(JsonParser.parseString(Files.readString(recipePath())).getAsJsonObject());
            STATE.values.clear();STATE.values.putAll(decoded);STATE.apply();BlackHoleRender.frozenTime=-1;BlackHoleRender.resetClock();
        }catch(java.io.IOException ex){throw new IllegalStateException("Cannot read "+recipePath(),ex);}
    }
    private void apply(){
        BlackHoleWorld.tilt=getFloat("tilt");BlackHoleWorld.rotation=getFloat("speed");BlackHoleWorld.infall=getFloat("infallSpeed");BlackHoleWorld.exposure=getFloat("exposure");BlackHoleWorld.lensRadius=getFloat("lensRadius");BlackHoleClient.coreColor=getColor("coreColor");
        if(values.containsKey("scale"))BlackHoleWorld.scale=getFloat("scale");
        if(values.containsKey("distance"))BlackHoleWorld.distance=getFloat("distance");else if(values.containsKey("viewDistance"))BlackHoleWorld.distance=getFloat("viewDistance")*BlackHoleWorld.scale;
        if(values.containsKey("lensStrength"))BlackHoleWorld.lens=getFloat("lensStrength");else if(values.containsKey("lensBackground"))BlackHoleWorld.lens=getFloat("lensBackground");
        if((values.containsKey("lens")&&!getBool("lens"))||(values.containsKey("lensEnabled")&&!getBool("lensEnabled")))BlackHoleWorld.lens=0;
        if(values.containsKey("particles"))BlackHoleParticles.enabled=getBool("particles");
        if(values.containsKey("particleCount"))BlackHoleParticles.count=getInt("particleCount");
        if(values.containsKey("particleSpeed"))BlackHoleParticles.speed=getFloat("particleSpeed");
        if(values.containsKey("particleSize"))BlackHoleParticles.size=getFloat("particleSize");
        if(values.containsKey("particleOuter"))BlackHoleParticles.outer=getFloat("particleOuter");
        if(values.containsKey("particleBrightness"))BlackHoleParticles.brightness=getFloat("particleBrightness");
        if(values.containsKey("halo"))BlackHoleWorld.halo=getFloat("halo");if(values.containsKey("haloRing"))BlackHoleWorld.haloRing=getFloat("haloRing");if(values.containsKey("haloDisk"))BlackHoleWorld.haloDisk=getFloat("haloDisk");if(values.containsKey("haloAura"))BlackHoleWorld.haloAura=getFloat("haloAura");
    }
    static JsonObject encode(Map<String,String> values){
        var parameters=new JsonObject();
        var bools=Set.of("playing","lensAffectsPlayer","lens","lensEnabled","particles","shellEnabled");
        var browserNumbers=Set.of("steps","particleGradientPosition","skyGradientPosition","backgroundSpin","orbitSpeed","bloom","bloomRadius","skyStrength","yaw","pitch");
        values.forEach((key,value)->{
            if(bools.contains(key))parameters.addProperty(key,Boolean.parseBoolean(value));
            else if(RANGES.containsKey(key)||browserNumbers.contains(key))parameters.addProperty(key,Double.parseDouble(value));
            else parameters.addProperty(key,value);
        });
        return parameters;
    }
    static void save(){
        var root=new JsonObject();root.addProperty("schema","black-hole-world/v2");var parameters=encode(STATE.snapshot());
        parameters.addProperty("scale",BlackHoleWorld.scale);parameters.addProperty("distance",BlackHoleWorld.distance);parameters.addProperty("tilt",BlackHoleWorld.tilt);parameters.addProperty("speed",BlackHoleWorld.rotation);parameters.addProperty("infallSpeed",BlackHoleWorld.infall);parameters.addProperty("exposure",BlackHoleWorld.exposure);parameters.addProperty("lensRadius",BlackHoleWorld.lensRadius);parameters.addProperty("lensStrength",BlackHoleWorld.lens);parameters.addProperty("coreColor",String.format("#%06X",BlackHoleClient.coreColor));
        for(String key:List.of("playing","lensAffectsPlayer","lens","lensEnabled"))if(STATE.get(key)!=null)parameters.addProperty(key,STATE.getBool(key));
        parameters.addProperty("lens",BlackHoleWorld.lens>.00001f);if(STATE.get("lensEnabled")!=null)parameters.addProperty("lensEnabled",BlackHoleWorld.lens>.00001f);
        parameters.addProperty("particles",BlackHoleParticles.enabled);parameters.addProperty("particleCount",BlackHoleParticles.count);parameters.addProperty("particleSpeed",BlackHoleParticles.speed);parameters.addProperty("particleSize",BlackHoleParticles.size);parameters.addProperty("particleOuter",BlackHoleParticles.outer);parameters.addProperty("particleBrightness",BlackHoleParticles.brightness);
        parameters.addProperty("halo",BlackHoleWorld.halo);parameters.addProperty("haloRing",BlackHoleWorld.haloRing);parameters.addProperty("haloDisk",BlackHoleWorld.haloDisk);parameters.addProperty("haloAura",BlackHoleWorld.haloAura);
        root.add("parameters",parameters);
        try{Files.createDirectories(recipePath().getParent());Files.writeString(recipePath(),new GsonBuilder().setPrettyPrinting().create().toJson(root));}catch(java.io.IOException ex){throw new IllegalStateException("Cannot write "+recipePath(),ex);}
    }
}
