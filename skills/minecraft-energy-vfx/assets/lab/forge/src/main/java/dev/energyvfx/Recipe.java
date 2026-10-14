package dev.energyvfx;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Strict, atomic consumer of the editor's full recipe. */
final class Recipe {
 final float[] values;final float[] a,b;final String name;
 Recipe(float[] values,float[] a,float[] b,String name){this.values=values;this.a=a;this.b=b;this.name=name;}
 static JsonElement parse(String s){return JsonParser.parseString(s);}
 static JsonArray fields()throws Exception{return parse(resource("recipe-schema.json")).getAsJsonArray();}
 static String resource(String name)throws Exception{try(var stream=Recipe.class.getResourceAsStream("/assets/energyvfx/"+name)){if(stream==null)throw new IllegalArgumentException("Missing resource "+name);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}}
 static Recipe load()throws Exception{
  Path path=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("energyvfx/recipe.json");
  if(Files.isRegularFile(path)&&Files.size(path)>100000)throw new IllegalArgumentException("Recipe exceeds 100 KB");
  return decode(parse(Files.isRegularFile(path)?Files.readString(path,StandardCharsets.UTF_8):resource("recipe.json")));
 }
 static void exact(JsonObject object,Set<String> allowed){if(!object.keySet().equals(allowed))throw new IllegalArgumentException("Missing or unknown recipe keys: "+object.keySet());}
 static String string(JsonObject object,String key){var p=object.get(key);if(!p.isJsonPrimitive()||!p.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string "+key);return p.getAsString();}
 static float[] rgb(String value){if(!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid color");float[] c=new float[3];for(int i=0;i<3;i++)c[i]=(float)Math.pow(Integer.parseInt(value.substring(1+i*2,3+i*2),16)/255.,2.2);return c;}
 static Recipe decode(JsonElement raw)throws Exception{
  var r=raw.getAsJsonObject();exact(r,Set.of("schema","name","color","secondary","params"));
  String version=string(r,"schema");boolean legacy=version.equals("energy-vfx/1");if(!legacy&&!version.equals("energy-vfx/2"))throw new IllegalArgumentException("Unsupported schema");
  String name=string(r,"name");if(name.isBlank()||name.length()>80)throw new IllegalArgumentException("Invalid name");
  JsonArray schema=fields();JsonObject params=r.getAsJsonObject("params");Set<String> keys=new HashSet<>();float[] v=new float[schema.size()];
  for(int i=0;i<schema.size();i++){JsonArray f=schema.get(i).getAsJsonArray();String k=f.get(0).getAsString();if(legacy&&i>=39){v[i]=f.get(6).getAsFloat();continue;}keys.add(k);var value=params.get(k);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected number "+k);double number=value.getAsDouble();double min=f.get(3).getAsDouble(),max=f.get(4).getAsDouble();if(!Double.isFinite(number)||number<min||number>max||(f.get(5).getAsDouble()==1&&number!=Math.floor(number)))throw new IllegalArgumentException("Out of range "+k);v[i]=(float)number;}
  exact(params,keys);return new Recipe(v,rgb(string(r,"color")),rgb(string(r,"secondary")),name);
 }
}
