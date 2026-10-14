package dev.worldvolume;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Strict current schema; legacy migration is explicit and validates before defaults. */
final class VolumeRecipe {
    static JsonObject schema, schemaV1, contract;
    private final Map<String, Double> values;
    final String name;
    private VolumeRecipe(String name, Map<String, Double> values) { this.name=name; this.values=Map.copyOf(values); }
    static VolumeRecipe read(Reader reader) { return validateRoot(parseStrict(reader),schema); }
    static VolumeRecipe upgradeV1(Reader reader) {
        VolumeRecipe old=validateRoot(parseStrict(reader),schemaV1);
        Map<String,Double> upgraded=new LinkedHashMap<>();
        schema.getAsJsonObject("fields").entrySet().forEach(e->upgraded.put(e.getKey(),e.getValue().getAsJsonObject().get("default").getAsDouble()));
        upgraded.putAll(old.values);upgraded.put("densityModel",0.0);
        VolumeRecipe result=new VolumeRecipe(old.name,upgraded);
        return validateRoot(result.json(),schema);
    }
    private static JsonObject parseStrict(Reader reader) {
        try(JsonReader in=new JsonReader(reader)) {
            in.setLenient(false);JsonElement element=parseValue(in,0);
            if(in.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing JSON data");
            if(!element.isJsonObject())throw new IllegalArgumentException("Recipe must be an object");
            return element.getAsJsonObject();
        } catch(IOException|NumberFormatException e) {throw new IllegalArgumentException("Malformed strict JSON: "+e.getMessage(),e);}
    }
    private static JsonElement parseValue(JsonReader in,int depth) throws IOException {
        if(depth>32)throw new IllegalArgumentException("JSON nesting exceeds recipe limit");
        return switch(in.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object=new JsonObject();in.beginObject();
                while(in.hasNext()){String key=in.nextName();if(object.has(key))throw new IllegalArgumentException("Duplicate JSON key: "+key);object.add(key,parseValue(in,depth+1));}
                in.endObject();yield object;
            }
            case BEGIN_ARRAY -> {JsonArray array=new JsonArray();in.beginArray();while(in.hasNext())array.add(parseValue(in,depth+1));in.endArray();yield array;}
            case STRING -> new JsonPrimitive(in.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(in.nextString()));
            case BOOLEAN -> new JsonPrimitive(in.nextBoolean());
            case NULL -> {in.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IllegalArgumentException("Unexpected JSON token: "+in.peek());
        };
    }
    private static VolumeRecipe validateRoot(JsonObject root,JsonObject expectedSchema) {
        for(String k:root.keySet())if(!Set.of("schema","name","parameters").contains(k))throw new IllegalArgumentException("Unknown recipe key: "+k);
        String expected=expectedSchema.get("schema").getAsString();
        if(!root.has("schema")||!root.get("schema").isJsonPrimitive()||!root.getAsJsonPrimitive("schema").isString()||!expected.equals(root.get("schema").getAsString()))throw new IllegalArgumentException("Expected "+expected+"; /1 requires explicit upgrade-v1");
        if(root.has("name")&&(!root.get("name").isJsonPrimitive()||!root.getAsJsonPrimitive("name").isString()||root.get("name").getAsString().codePointCount(0,root.get("name").getAsString().length())>120))throw new IllegalArgumentException("name must be a string of at most 120 characters");
        if(!root.has("parameters")||!root.get("parameters").isJsonObject())throw new IllegalArgumentException("parameters must be an object");
        JsonObject params=root.getAsJsonObject("parameters"),fields=expectedSchema.getAsJsonObject("fields");
        if(!params.keySet().equals(fields.keySet()))throw new IllegalArgumentException("Expected all "+fields.size()+" fields, no unknown fields");
        Map<String,Double> result=new LinkedHashMap<>();
        for(var entry:fields.entrySet()) {
            String key=entry.getKey();JsonElement v=params.get(key);JsonObject f=entry.getValue().getAsJsonObject();
            if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(key+" must be numeric");
            double n=v.getAsDouble();
            if(!Double.isFinite(n)||n<f.get("min").getAsDouble()||n>f.get("max").getAsDouble())throw new IllegalArgumentException(key+" outside finite range");
            if(f.get("type").getAsString().equals("integer")&&v.getAsBigDecimal().stripTrailingZeros().scale()>0)throw new IllegalArgumentException(key+" must be an integer");
            result.put(key,n);
        }
        if(result.get("shape")==2&&result.get("innerRadius")>=result.get("radius"))throw new IllegalArgumentException("Annulus innerRadius must be less than radius");
        if(result.get("flashDuration")>result.get("flashPeriod"))throw new IllegalArgumentException("flashDuration must not exceed flashPeriod");
        double sx=result.get("sunX"),sy=result.get("sunY"),sz=result.get("sunZ");
        if(Math.sqrt(sx*sx+sy*sy+sz*sz)<=1e-6)throw new IllegalArgumentException("Sun direction cannot be zero");
        return new VolumeRecipe(root.has("name")?root.get("name").getAsString():"Untitled",result);
    }
    double value(String key) { return values.get(key); }
    JsonObject json() {
        JsonObject o=new JsonObject();o.addProperty("schema",schema.get("schema").getAsString());o.addProperty("name",name);
        JsonObject params=new JsonObject();values.forEach(params::addProperty);o.add("parameters",params);return o;
    }
    VolumeRecipe with(Map<String,? extends Number> edits) {
        JsonObject o=json();edits.forEach((key,n)->o.getAsJsonObject("parameters").addProperty(key,n));
        return read(new StringReader(o.toString()));
    }
    void upload(ShaderInstance program,Vec3 camera) {
        for(var entry:contract.getAsJsonObject("uniforms").entrySet()) {
            String name=entry.getKey();JsonArray fields=entry.getValue().getAsJsonArray();float[] v=new float[fields.size()];
            for(int i=0;i<v.length;i++) {
                String key=fields.get(i).getAsString();double n=value(key);
                // Double-precision rebasing happens before the float conversion.
                if(key.equals("centerX"))n-=camera.x;
                if(key.equals("centerY")||key.equals("bottom")||key.equals("voidFloor"))n-=camera.y;
                if(key.equals("centerZ"))n-=camera.z;
                v[i]=(float)n;
            }
            var u=program.safeGetUniform(name);
            if(name.equals("ViewSteps")||name.equals("LightSteps"))u.set((int)v[0]);
            else if(v.length==1)u.set(v[0]);else if(v.length==3)u.set(v[0],v[1],v[2]);else u.set(v[0],v[1],v[2],v[3]);
        }
    }
}
