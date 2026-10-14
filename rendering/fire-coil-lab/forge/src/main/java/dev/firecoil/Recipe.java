package dev.firecoil;

import com.google.gson.*;
import java.util.*;

/** Strict immutable 36-field input, with exact v1 migration. */
public final class Recipe {
 private static final LinkedHashMap<String,double[]> RANGES = new LinkedHashMap<>();
 static {
  range("attachment",0,2);range("weaponRadius",.01,.6);range("weaponWidth",.02,.5);range("weaponTurns",.25,5);range("weaponStrength",0,3);
  range("radius",.15,1.8);range("height",.2,3.5);range("turns",.25,5);range("strands",1,8);
  range("width",.04,.65);range("rotationSpeed",-2,2);range("riseSpeed",0,3);range("waveAmplitude",0,.3);
  range("waveSpeed",0,3);range("turbulence",0,2);range("tongueLength",.1,2);range("erosion",0,1);
  range("intensity",.2,4);range("opacity",0,1);range("emberRate",0,160);range("emberLife",.15,3);
  range("emberSize",.005,.1);range("wispRate",0,80);range("wispLife",.15,2);range("wispSize",.05,.6);
  range("outwardSpeed",0,2);range("lift",0,2.5);range("motionInheritance",0,1);range("seed",0,1000000);
  range("segments",24,512);range("rampTime",.05,2);range("firstPerson",0,1);
 }
 private static void range(String k,double min,double max){RANGES.put(k,new double[]{min,max});}
 private final JsonObject raw;
 private Recipe(JsonObject raw){this.raw=raw.deepCopy();}
 public static Recipe parse(String text) {
  JsonElement parsed=JsonParser.parseString(text);if(!parsed.isJsonObject())throw new IllegalArgumentException("recipe must be an object");
  JsonObject j=parsed.getAsJsonObject();Set<String> expected=new HashSet<>(RANGES.keySet());expected.addAll(List.of("schema","coreColor","midColor","edgeColor"));
  if(j.has("schema")&&string(j.get("schema"),"schema").equals("fire-coil/1")){
   Set<String> legacy=new HashSet<>(expected);legacy.removeAll(List.of("attachment","weaponRadius","weaponWidth","weaponTurns","weaponStrength"));
   if(!j.keySet().equals(legacy))throw new IllegalArgumentException("v1 recipe requires exactly 31 fields");
   j.addProperty("attachment",0);j.addProperty("weaponRadius",.022);j.addProperty("weaponWidth",.115);j.addProperty("weaponTurns",.88);j.addProperty("weaponStrength",1.35);j.addProperty("schema","fire-coil/2");
  }
  if(!j.keySet().equals(expected))throw new IllegalArgumentException("recipe fields differ: expected exactly 36 fields");
  if(!string(j.get("schema"),"schema").equals("fire-coil/2"))throw new IllegalArgumentException("schema must be fire-coil/2");
  for(var e:RANGES.entrySet()) {
   JsonElement v=j.get(e.getKey());if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(e.getKey()+" must be numeric");
   double n=v.getAsDouble();if(!Double.isFinite(n)||n<e.getValue()[0]||n>e.getValue()[1])throw new IllegalArgumentException(e.getKey()+" outside range");
   if(Set.of("strands","segments","seed","attachment").contains(e.getKey())&&n!=Math.rint(n))throw new IllegalArgumentException(e.getKey()+" must be integer");
  }
  for(String k:List.of("coreColor","midColor","edgeColor"))if(!string(j.get(k),k).matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException(k+" must be #RRGGBB");
  return new Recipe(j);
 }
 private static String string(JsonElement v,String k){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException(k+" must be string");return v.getAsString();}
 public double d(String k){return raw.get(k).getAsDouble();}
 public int i(String k){return raw.get(k).getAsInt();}
 public float[] color(String k){int h=Integer.parseInt(raw.get(k).getAsString().substring(1),16);return new float[]{((h>>16)&255)/255f,((h>>8)&255)/255f,(h&255)/255f};}
 public JsonObject json(){return raw.deepCopy();}
 public String hash(){try{byte[] h=java.security.MessageDigest.getInstance("SHA-256").digest(raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(h);}catch(Exception e){throw new IllegalStateException(e);}}
}
