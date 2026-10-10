package dev.lightninglab;

import com.google.gson.*;
import java.util.List;

/** The portable skill JSON parser, independent of any MOD parameter UI. */
final class RecipeContractCheck {
    static void run() {
        Gson gson=new Gson();
        LightningRecipe baseline=new LightningRecipe().validate();
        JsonObject complete=gson.toJsonTree(baseline).getAsJsonObject();
        if(complete.size()!=63)throw new AssertionError("63-field recipe contract");
        same(gson,baseline,LightningRecipe.parse(complete),"typed round trip");
        JsonObject legacy=complete.deepCopy();legacy.remove("gradientTarget");legacy.remove("gradientCoreMode");
        same(gson,baseline,LightningRecipe.parse(legacy),"61-field legacy defaults");
        legacy.remove("gradientRatio");
        same(gson,baseline,LightningRecipe.parse(legacy),"60-field legacy defaults");
        JsonObject wrapper=new JsonObject(),strings=new JsonObject();
        for(var entry:complete.entrySet())strings.addProperty(entry.getKey(),entry.getValue().getAsString());
        strings.addProperty("preset","custom");wrapper.add("values",strings);
        same(gson,baseline,LightningRecipe.parse(wrapper),"legacy string wrapper");
        var custom=complete.deepCopy();custom.addProperty("mode","rain");custom.addProperty("rainStyle","cluster");
        custom.addProperty("rainCount",7);custom.addProperty("rainSpread",8.4);custom.addProperty("rainDepthSpread",1.7);
        custom.addProperty("rainStagger",.9);custom.addProperty("gradientRatio",72.5);
        var parsed=LightningRecipe.parse(custom);
        if(!custom.equals(gson.toJsonTree(parsed)))throw new AssertionError("import silently replaced custom cluster parameters");
        for(String bad:List.of("{\"gradientTarget\":\"branches\"}","{\"gradientCoreMode\":\"auto\"}","{\"gradientTarget\":null}","{\"gradientCoreMode\":1}")) {
            boolean rejected=false;
            try{LightningRecipe.parse(JsonParser.parseString(bad));}catch(RuntimeException expected){rejected=true;}
            if(!rejected)throw new AssertionError("invalid gradient mode accepted: "+bad);
        }
        for(String bad:List.of("[]","null","{\"unknown\":1}","{\"strikeHeight\":1025}","{\"strikeDisplacement\":12.1}","{\"strands\":1.5}","{\"seed\":2147483648}","{\"seed\":\"1.000000000000000001\"}","{\"durationTicks\":1}","{\"schema\":2}","{\"gradientEnabled\":1}","{\"glowGain\":true}","{\"coreColor\":1}","{\"branchDecay\":\"NaN\"}","{\"values\":[]}","{\"gradientRatio\":101}","{\"gradientRatio\":null}")) {
            boolean rejected=false;
            try{LightningRecipe.parse(JsonParser.parseString(bad));}catch(RuntimeException expected){rejected=true;}
            if(!rejected)throw new AssertionError("invalid recipe accepted: "+bad);
            same(gson,baseline,new LightningRecipe(),"invalid import mutated baseline");
        }
        System.out.println("PASS: portable 63-field typed round trip, 60+61-field defaults, legacy strings, preserved custom cluster, strict finite/range/integer/gradient mode validation");
        for(String count:List.of("1.5","\"1.5\"")){
            boolean rejected=false;
            try{LightningRecipe.parse(JsonParser.parseString("{\"restrikeCount\":"+count+"}"));}catch(IllegalArgumentException expected){rejected=true;}
            if(!rejected)throw new AssertionError("fractional restrike count differs from JS/UI integer contract");
        }
    }
    private static void same(Gson gson,LightningRecipe a,LightningRecipe b,String message){
        if(!gson.toJsonTree(a).equals(gson.toJsonTree(b)))throw new AssertionError(message);
    }
}
