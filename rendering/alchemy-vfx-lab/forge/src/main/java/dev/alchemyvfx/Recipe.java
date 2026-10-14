package dev.alchemyvfx;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Strict shared alchemy-vfx/2 recipe; reject malformed input and retain the last valid recipe. */
public final class Recipe {
    private static final Map<String,double[]> RANGES=new LinkedHashMap<>();
    static {
        range("faceHeatStrength",0.0,2.0);
        range("faceHeatHeight",0.2,2.0);
        range("faceHeatSpread",0.03,0.55);
        range("faceSparkRate",0.0,24.0);
        range("faceSparkLife",0.3,3.0);
        range("faceSparkSize",0.006,0.06);
        range("faceSparkSpeed",0.1,1.3);
        range("aromaStrength",0.0,2.0);
        range("aromaHeight",0.3,2.5);
        range("ingredientCount",0.0,12.0);
        range("ingredientSize",0.025,0.16);
        range("ingredientStrength",0.0,3.0);
        range("gatherSpeed",0.0,2.0);
        range("energyRingRadius",2.5,4.2);
        range("energyRingHeight",0.3,3.2);
        range("energyRingThickness",0.012,0.12);
        range("energyRingStrength",0.0,2.0);
        range("energyRingSpeed",-0.5,0.5);

        range("fireRadius",1.46,2.05);range("fireHeight",.2,1.8);range("fireStrength",0,3);
        range("vaporRadius",.10,.28);range("vaporHeight",.3,3);range("vaporSpread",0,1.2);range("vaporStrength",0,3);
        range("flowSpeed",0,2);range("turbulence",0,2);range("filamentWidth",.008,.12);range("filamentStrength",0,3);
        range("orbitRadius",.2,1.1);range("orbitSpeed",-.5,.5);range("coreRadius",.03,.35);range("coreStrength",0,4);
        range("burstRadius",.3,2.5);range("burstStrength",0,4);range("sigilRadius",2.52,4);range("sigilStrength",0,2);
        range("intensity",.2,3);range("opacity",0,1);range("seed",0,1000000);range("segments",24,512);
    }
    public static final List<String> COLORS=List.of("fireCore","fireMid","fireEdge","vaporCore","vaporMid","vaporEdge","coreColor","aromaColor","ingredientColor","energyRingColor");
    private final JsonObject value;
    private Recipe(JsonObject value) { this.value=value; }
    private static void range(String key,double low,double high) { RANGES.put(key,new double[]{low,high}); }
    public static Recipe parse(String contents) {
        JsonElement parsed=JsonParser.parseString(contents);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Recipe must be an object");
        JsonObject source=parsed.getAsJsonObject(),normalized=new JsonObject();
        Set<String> expected=new HashSet<>(RANGES.keySet());expected.addAll(COLORS);expected.add("schema");
        if(source.has("schema")&&string(source,"schema").equals("alchemy-vfx/1")){
            Set<String> legacy=new HashSet<>(expected);legacy.removeAll(List.of("faceHeatStrength","faceHeatHeight","faceHeatSpread","faceSparkRate","faceSparkLife","faceSparkSize","faceSparkSpeed","aromaStrength","aromaHeight","ingredientCount","ingredientSize","ingredientStrength","gatherSpeed","energyRingRadius","energyRingHeight","energyRingThickness","energyRingStrength","energyRingSpeed","aromaColor","ingredientColor","energyRingColor"));
            if(!source.keySet().equals(legacy))throw new IllegalArgumentException("v1 requires exactly 31 fields");
            source.addProperty("faceHeatStrength",0.32);
            source.addProperty("faceHeatHeight",0.85);
            source.addProperty("faceHeatSpread",0.16);
            source.addProperty("faceSparkRate",6);
            source.addProperty("faceSparkLife",1.3);
            source.addProperty("faceSparkSize",0.018);
            source.addProperty("faceSparkSpeed",0.48);
            source.addProperty("aromaStrength",0.24);
            source.addProperty("aromaHeight",1.35);
            source.addProperty("ingredientCount",6);
            source.addProperty("ingredientSize",0.072);
            source.addProperty("ingredientStrength",0.72);
            source.addProperty("gatherSpeed",0.65);
            source.addProperty("energyRingRadius",2.72);
            source.addProperty("energyRingHeight",1.28);
            source.addProperty("energyRingThickness",0.036);
            source.addProperty("energyRingStrength",0.28);
            source.addProperty("energyRingSpeed",0.13);
            source.addProperty("aromaColor","#b5dfbd");
            source.addProperty("ingredientColor","#ead4a3");
            source.addProperty("energyRingColor","#9ddfd4");
            source.addProperty("schema","alchemy-vfx/2");
        }
        if (!source.keySet().equals(expected)) throw new IllegalArgumentException("Recipe fields must be complete with no extras");
        if (!string(source,"schema").equals("alchemy-vfx/2")) throw new IllegalArgumentException("Only alchemy-vfx/2 is supported");
        normalized.addProperty("schema","alchemy-vfx/2");
        for (var entry:RANGES.entrySet()) {
            String key=entry.getKey();JsonElement element=source.get(key);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key+" must be number");
            double number=element.getAsDouble();double[] limit=entry.getValue();
            if (!Double.isFinite(number) || number<limit[0] || number>limit[1]
                    || ((key.equals("segments") || key.equals("seed") || key.equals("ingredientCount")) && number!=Math.rint(number)))
                throw new IllegalArgumentException(key+" out of range");
            normalized.addProperty(key,number);
        }
        for (String key:COLORS) { String color=string(source,key);if (!color.matches("#[A-Fa-f0-9]{6}")) throw new IllegalArgumentException(key+" must be #RRGGBB");normalized.addProperty(key,color.toLowerCase(Locale.ROOT)); }
        return new Recipe(normalized);
    }
    static String string(JsonObject object,String key) {
        JsonElement value=object.get(key);
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key+" must be string");
        return value.getAsString();
    }
    public double d(String key) { return value.get(key).getAsDouble(); }
    public int i(String key) { return value.get(key).getAsInt(); }
    public float[] color(String key) { int color=Integer.parseInt(value.get(key).getAsString().substring(1),16);return new float[]{((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f}; }
    public JsonObject json() { return value.deepCopy(); }
    public Set<String> numericKeys() { return Collections.unmodifiableSet(RANGES.keySet()); }
    public String hash() { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); } catch (Exception impossible) { throw new AssertionError(impossible); } }
}
