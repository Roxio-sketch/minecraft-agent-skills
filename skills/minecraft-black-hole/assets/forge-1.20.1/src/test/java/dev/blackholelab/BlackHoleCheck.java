package dev.blackholelab;

import com.google.gson.*;

/** Meaningful math/config contracts; this does not claim GPU or framebuffer certification. */
public final class BlackHoleCheck {
    public static void main(String[] arguments){
        BlackHoleCameraCheck.run();
        var old=JsonParser.parseString("{\"schema\":\"black-hole-preview/v2\",\"parameters\":{\"core\":0.58,\"inner\":0.94,\"outer\":2.9,\"tilt\":-51,\"steps\":256},\"time\":1071.978,\"playing\":false}").getAsJsonObject();
        var decoded=BlackHoleRecipe.decode(old);
        if(!decoded.get("tilt").equals("-51")||!decoded.get("playing").equals("false")||!decoded.get("lensAffectsPlayer").equals("false"))throw new AssertionError("Import changed explicit tilt/time or old player policy");
        if(!decoded.get("diskStyle").equals("classic")||!decoded.get("particles").equals("false")||!decoded.get("halo").equals("0")||!decoded.get("infallSpeed").equals("0"))throw new AssertionError("Old recipe inherited new showcase layers");
        if(!BlackHoleRecipe.state().get("tilt").equals("0"))throw new AssertionError("Fresh default must be horizontal");
        old.getAsJsonObject("parameters").addProperty("lensAffectsPlayer",true);
        if(!BlackHoleRecipe.decode(old).get("lensAffectsPlayer").equals("true"))throw new AssertionError("Explicit opt-in lost");
        var parameters=BlackHoleRecipe.encode(BlackHoleRecipe.decode(old));
        if(!parameters.get("core").getAsJsonPrimitive().isNumber()||!parameters.get("steps").getAsJsonPrimitive().isNumber()||!parameters.get("playing").getAsJsonPrimitive().isBoolean())throw new AssertionError("Export lost JSON scalar types");
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
        var overlap=old.deepCopy();overlap.getAsJsonObject("parameters").addProperty("inner",.6);
        try{BlackHoleRecipe.decode(overlap);throw new AssertionError("Accepted overlapping disk");}catch(IllegalArgumentException expected){rejected++;}
        var bool=old.deepCopy();bool.getAsJsonObject("parameters").addProperty("lensAffectsPlayer","true");
        try{BlackHoleRecipe.decode(bool);throw new AssertionError("Accepted string boolean");}catch(IllegalArgumentException expected){rejected++;}
        System.out.println("PASS: independent recipe defaults, explicit tilt/clock/player preference, "+rejected+" invalid recipes rejected atomically");
    }
}
