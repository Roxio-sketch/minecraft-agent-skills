package dev.blackholelab;

import com.google.gson.*;

/** Meaningful math/config contracts; this does not claim GPU or framebuffer certification. */
public final class BlackHoleCheck {
    public static void main(String[] arguments){
        BlackHoleCameraCheck.run();
        BlackHoleFlowClockCheck.main(new String[0]);
        var old=JsonParser.parseString("{\"schema\":\"black-hole-preview/v2\",\"parameters\":{\"core\":0.58,\"inner\":0.94,\"outer\":2.9,\"tilt\":-51,\"steps\":256},\"time\":1071.978,\"playing\":false}").getAsJsonObject();
        var decoded=BlackHoleRecipe.decode(old);
        if(!decoded.get("objectMode").equals("black")||Double.parseDouble(decoded.get("coreEmission"))!=1.0)throw new AssertionError("Old recipe inherited white behavior");
        if(!decoded.get("tilt").equals("-51")||!decoded.get("playing").equals("false")||!decoded.get("lensAffectsPlayer").equals("false"))throw new AssertionError("Import changed explicit tilt/time or old player policy");
        if(!decoded.get("diskStyle").equals("classic")||!decoded.get("particles").equals("false")||!decoded.get("halo").equals("0")||!decoded.get("infallSpeed").equals("0"))throw new AssertionError("Old recipe inherited new showcase layers");
        if(!BlackHoleRecipe.state().get("tilt").equals("0"))throw new AssertionError("Fresh default must be horizontal");
        old.getAsJsonObject("parameters").addProperty("lensAffectsPlayer",true);
        if(!BlackHoleRecipe.decode(old).get("lensAffectsPlayer").equals("true"))throw new AssertionError("Explicit opt-in lost");
        var parameters=BlackHoleRecipe.encode(BlackHoleRecipe.decode(old));
        if(!parameters.get("core").getAsJsonPrimitive().isNumber()||!parameters.get("steps").getAsJsonPrimitive().isNumber()||!parameters.get("playing").getAsJsonPrimitive().isBoolean())throw new AssertionError("Export lost JSON scalar types");
        // The historical browser bridge remains world/v1 and carries no new fields.
        for(String field:new String[]{"objectMode","coreEmission","jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor","coreGlow","coreGlowRadius","shellEnabled"})parameters.remove(field);
        var exported=new JsonObject();exported.addProperty("schema","black-hole-world/v1");exported.add("parameters",parameters);
        parameters.addProperty("scale",1.6);parameters.addProperty("distance",14);parameters.addProperty("lens",true);parameters.addProperty("lensStrength",.7);
        if(arguments.length>0)try{java.nio.file.Files.writeString(java.nio.file.Path.of(arguments[0]),new GsonBuilder().setPrettyPrinting().create().toJson(exported));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        if(arguments.length>1)try{
            var browser=BlackHoleRecipe.decode(JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(arguments[1]))).getAsJsonObject());
            if(!browser.get("lensAffectsPlayer").equals("true")||Double.parseDouble(browser.get("scale"))!=1.6||Double.parseDouble(browser.get("distance"))!=14||Double.parseDouble(browser.get("tilt"))!=-42||Double.parseDouble(browser.get("speed"))!=2.2||!browser.get("hot").equals("#0044ff"))throw new AssertionError("Forge→browser edit→Forge lost world/common fields");
            System.out.println("PASS: Forge→workbench edit→Forge canonical bridge");
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        var roundtrip=BlackHoleRecipe.decode(exported);
        if(!roundtrip.get("lensAffectsPlayer").equals("true")||Double.parseDouble(roundtrip.get("tilt"))!=-51)throw new AssertionError("Forge typed export roundtrip lost fields");
        int rejected=0;
        for(String key:new String[]{"bloom","skyStrength","yaw","particleGradientPosition"}){
            var copy=old.deepCopy();copy.getAsJsonObject("parameters").addProperty(key,"not-a-number");
            try{BlackHoleRecipe.decode(copy);throw new AssertionError("Accepted invalid browser metadata "+key);}catch(IllegalArgumentException expected){rejected++;}
        }
        var metadata=old.deepCopy();metadata.getAsJsonObject("parameters").addProperty("bloom",1.2);metadata.getAsJsonObject("parameters").addProperty("skyStrength",.75);metadata.getAsJsonObject("parameters").addProperty("yaw",-.25);
        var encodedMetadata=BlackHoleRecipe.encode(BlackHoleRecipe.decode(metadata));
        if(!encodedMetadata.get("bloom").getAsJsonPrimitive().isNumber()||encodedMetadata.get("yaw").getAsDouble()!=-.25)throw new AssertionError("Validated metadata did not roundtrip");
        for(String invalid:new String[]{"tilt","density","particleCount"}){
            var copy=old.deepCopy();copy.getAsJsonObject("parameters").addProperty(invalid,invalid.equals("particleCount")?12.5:999);
            try{BlackHoleRecipe.decode(copy);throw new AssertionError("Accepted invalid "+invalid);}catch(IllegalArgumentException expected){rejected++;}
        }
        var overlap=old.deepCopy();overlap.getAsJsonObject("parameters").addProperty("inner",.58);
        try{BlackHoleRecipe.decode(overlap);throw new AssertionError("Accepted overlapping disk");}catch(IllegalArgumentException expected){rejected++;}
        var close=old.deepCopy();close.getAsJsonObject("parameters").addProperty("core",.55);close.getAsJsonObject("parameters").addProperty("inner",.57);
        if(!BlackHoleRecipe.decode(close).get("inner").equals("0.57"))throw new AssertionError("Exact decimal inner edge rejected");
        var bool=old.deepCopy();bool.getAsJsonObject("parameters").addProperty("lensAffectsPlayer","true");
        try{BlackHoleRecipe.decode(bool);throw new AssertionError("Accepted string boolean");}catch(IllegalArgumentException expected){rejected++;}
        System.out.println("PASS: independent recipe defaults, explicit tilt/clock/player preference, "+rejected+" invalid recipes rejected atomically");
        checkWhiteHole();
        checkExternalOutflow();
        checkCoreGlow();
        var full=new JsonObject();full.addProperty("schema","black-hole-world/v2");full.add("parameters",BlackHoleRecipe.encode(BlackHoleRecipe.whiteRing()));
        full.getAsJsonObject("parameters").remove("shellEnabled");if(!BlackHoleRecipe.decode(full).get("shellEnabled").equals("true"))throw new AssertionError("Old 74 field ring disabled");
        full.getAsJsonObject("parameters").addProperty("shellEnabled",false);var off=BlackHoleRecipe.decode(full);if(!off.get("shellEnabled").equals("false")||Double.parseDouble(off.get("shellStrength"))!=.28)throw new AssertionError("Shell off loses strength");
        full.getAsJsonObject("parameters").addProperty("shellEnabled","false");try{BlackHoleRecipe.decode(full);throw new AssertionError("String shell bool accepted");}catch(IllegalArgumentException expected){}

    }
    private static void checkWhiteHole(){
        var white=new JsonObject();white.addProperty("schema","black-hole-world/v2");white.add("parameters",BlackHoleRecipe.encode(BlackHoleRecipe.whiteRing()));
        var parameters=white.getAsJsonObject("parameters");
        if(!parameters.get("coreEmission").getAsJsonPrimitive().isNumber()||!parameters.get("particles").getAsJsonPrimitive().isBoolean())throw new AssertionError("White export lost scalar types");
        var decoded=BlackHoleRecipe.decode(white);
        if(!decoded.get("objectMode").equals("white")||Double.parseDouble(decoded.get("coreEmission"))!=3.95||!decoded.get("particleHot").equals("#dffaff")||!decoded.get("particleCold").equals("#2787f3")||!decoded.get("playing").equals("true")||Double.parseDouble(decoded.get("tilt"))!=-1)throw new AssertionError("White-ring behavior/color/playback roundtrip");
        var partial=JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"form\":\"white-ring\",\"tilt\":-31,\"coreEmission\":2.25}}").getAsJsonObject();
        var custom=BlackHoleRecipe.decode(partial);
        if(!custom.get("objectMode").equals("white")||Double.parseDouble(custom.get("coreEmission"))!=2.25||Double.parseDouble(custom.get("tilt"))!=-31||!custom.get("particles").equals("false"))throw new AssertionError("Partial white-ring preset/explicit overrides");
        var missing=BlackHoleRecipe.decode(JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{}}").getAsJsonObject());
        if(!missing.get("objectMode").equals("black")||Double.parseDouble(missing.get("coreEmission"))!=1)throw new AssertionError("Missing new fields must retain black defaults");
        var live=BlackHoleRecipe.state().snapshot();int rejected=0;
        for(String format:new String[]{"black-hole-preview/v1","black-hole-preview/v2","black-hole-world/v1"})for(String field:new String[]{"objectMode","coreEmission","form"}){
            var historical=JsonParser.parseString("{\"schema\":\""+format+"\",\"parameters\":{}}").getAsJsonObject();
            if(field.equals("coreEmission"))historical.getAsJsonObject("parameters").addProperty(field,1);
            else historical.getAsJsonObject("parameters").addProperty(field,field.equals("form")?"white-ring":"white");
            try{BlackHoleRecipe.decode(historical);throw new AssertionError("Historical format accepted white field "+format+"/"+field);}catch(IllegalArgumentException expected){rejected++;}
        }
        for(String value:new String[]{"-1","4.01","\"2\"","null"}){
            var invalid=JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"coreEmission\":"+value+"}}").getAsJsonObject();
            try{BlackHoleRecipe.decode(invalid);throw new AssertionError("Accepted invalid white emission "+value);}catch(IllegalArgumentException expected){rejected++;}
        }
        for(String value:new String[]{"\"grey\"","2","false"}){
            var invalid=JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"objectMode\":"+value+"}}").getAsJsonObject();
            try{BlackHoleRecipe.decode(invalid);throw new AssertionError("Accepted invalid object mode "+value);}catch(IllegalArgumentException expected){rejected++;}
        }
        if(!BlackHoleRecipe.state().snapshot().equals(live))throw new AssertionError("Decoder modified live rendering state");
        System.out.println("PASS: independent world/v2 white preset/typed roundtrip/partial defaults and "+rejected+" rejected historical/invalid white recipes");
    }
    private static void checkExternalOutflow(){
        String[] fields={"jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor","coreGlow","coreGlowRadius","shellEnabled"};
        var full=new JsonObject();full.addProperty("schema","black-hole-world/v2");full.add("parameters",BlackHoleRecipe.encode(BlackHoleRecipe.whiteRing()));
        var enhanced=BlackHoleRecipe.decode(full);
        if(enhanced.size()!=75||Double.parseDouble(enhanced.get("jetStrength"))!=0||Double.parseDouble(enhanced.get("shellStrength"))!=0.28||!enhanced.get("shellColor").equals("#72dfff"))throw new AssertionError("Enhanced white preset lost independent layers");
        for(String field:fields)if(!field.equals("shellColor")&&!field.equals("shellEnabled")&&!full.getAsJsonObject("parameters").get(field).getAsJsonPrimitive().isNumber())throw new AssertionError("Untyped external numeric "+field);
        var oldFull=full.deepCopy();for(String field:fields)oldFull.getAsJsonObject("parameters").remove(field);
        var legacy=BlackHoleRecipe.decode(oldFull);
        if(Double.parseDouble(legacy.get("shellStrength"))!=0||Double.parseDouble(legacy.get("jetSpeed"))!=1)throw new AssertionError("Old complete v2 enabled omitted new effects");
        var partial=BlackHoleRecipe.decode(JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"form\":\"white-ring\",\"shellStrength\":0.4}} ").getAsJsonObject());
        if(Double.parseDouble(partial.get("shellStrength"))!=.4||Double.parseDouble(partial.get("jetSpeed"))!=1.15)throw new AssertionError("Partial preset did not apply enhanced defaults/overrides");
        int rejected=0;var before=BlackHoleRecipe.state().snapshot();
        for(String schema:new String[]{"black-hole-preview/v1","black-hole-preview/v2","black-hole-world/v1"})for(String field:fields){
            var bad=new JsonObject();bad.addProperty("schema",schema);var p=new JsonObject();p.add(field,full.getAsJsonObject("parameters").get(field));bad.add("parameters",p);
            try{BlackHoleRecipe.decode(bad);throw new AssertionError("Historical outflow accepted "+schema+"/"+field);}catch(IllegalArgumentException expected){rejected++;}
        }
        String[][] invalid={{"jetSpeed","-0.01"},{"jetSpeed","4.01"},{"jetSpeed","\"1\""},{"shellStrength","3.01"},{"shellSpeed","4.01"},{"shellRadius","0.99"},{"shellRadius","8.01"},{"shellWidth","0.019"},{"shellWidth","1.01"},{"shellColor","\"#abc\""},{"shellColor","false"},{"shellColor","null"}};
        for(String[] item:invalid){var bad=JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\""+item[0]+"\":"+item[1]+"}}").getAsJsonObject();
            try{BlackHoleRecipe.decode(bad);throw new AssertionError("Invalid outflow accepted "+item[0]+"="+item[1]);}catch(IllegalArgumentException expected){rejected++;}}
        for(String[] limits:new String[][]{{"jetSpeed","0","4"},{"shellStrength","0","3"},{"shellSpeed","0","4"},{"shellRadius","1","8"},{"shellWidth","0.02","1"}})for(int i=1;i<3;i++){
            var edge=JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\""+limits[0]+"\":"+limits[i]+"}}").getAsJsonObject();
            if(Double.parseDouble(BlackHoleRecipe.decode(edge).get(limits[0]))!=Double.parseDouble(limits[i]))throw new AssertionError("Valid boundary rejected");}
        if(!BlackHoleRecipe.state().snapshot().equals(before))throw new AssertionError("Failed external decode altered live state");
        System.out.println("PASS: independent enhanced 75-field white preset, full-old-v2 compatibility, partial defaults, typed boundaries and "+rejected+" historical/invalid outflow rejections");
    }
    private static void checkCoreGlow(){
        var full=new JsonObject();full.addProperty("schema","black-hole-world/v2");full.add("parameters",BlackHoleRecipe.encode(BlackHoleRecipe.whiteRing()));
        var old72=full.deepCopy();old72.getAsJsonObject("parameters").remove("shellEnabled");old72.getAsJsonObject("parameters").remove("coreGlow");old72.getAsJsonObject("parameters").remove("coreGlowRadius");
        var legacy=BlackHoleRecipe.decode(old72);
        if(Double.parseDouble(legacy.get("coreGlow"))!=0||Double.parseDouble(legacy.get("coreGlowRadius"))!=1.8)throw new AssertionError("Old complete 72-field v2 enabled a new corona");
        var partial=BlackHoleRecipe.decode(JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"form\":\"white-ring\"}}").getAsJsonObject());
        if(Double.parseDouble(partial.get("coreEmission"))!=3.95||Double.parseDouble(partial.get("coreGlow"))!=.8||Double.parseDouble(partial.get("coreGlowRadius"))!=1.05)throw new AssertionError("Partial current white-ring failed to activate its luminous core");
        var before=BlackHoleRecipe.state().snapshot();int rejected=0;
        for(String[] item:new String[][]{{"coreGlow","-0.01"},{"coreGlow","3.01"},{"coreGlow","\".8\""},{"coreGlowRadius","1.04"},{"coreGlowRadius","4.01"},{"coreGlowRadius","null"}}){
            var invalid=JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\""+item[0]+"\":"+item[1]+"}}").getAsJsonObject();
            try{BlackHoleRecipe.decode(invalid);throw new AssertionError("Invalid core glow accepted "+item[0]);}catch(IllegalArgumentException expected){rejected++;}}
        for(String[] limits:new String[][]{{"coreGlow","0","3"},{"coreGlowRadius","1.05","4"}})for(int i=1;i<3;i++){
            var edge=JsonParser.parseString("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\""+limits[0]+"\":"+limits[i]+"}}").getAsJsonObject();
            if(Double.parseDouble(BlackHoleRecipe.decode(edge).get(limits[0]))!=Double.parseDouble(limits[i]))throw new AssertionError("Core-glow boundary rejected");}
        if(!BlackHoleRecipe.state().snapshot().equals(before))throw new AssertionError("Core glow decoder changed live state");
        System.out.println("PASS: independent core-corona partial defaults, old complete 72-field compatibility, 4 inclusive boundaries and "+rejected+" invalid atomic rejections");
    }
}
