package dev.vfxworkbench;
import dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema;
import dev.vfxworkbench.core.*;
public final class BlackHoleSchemaCheck {
 public static void main(String[] args) throws java.io.IOException {
  dev.vfxworkbench.effects.blackhole.BlackHoleCameraCheck.run();
  BlackHoleSkillSchema.register();var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
  var diskTilt=s.schema.get("tilt");
  if(s.getDouble("tilt")!=0||diskTilt.min!=-90||diskTilt.max!=90)throw new AssertionError("horizontal disk default/range");
  var playerLens=s.schema.get("lensAffectsPlayer");
  if(playerLens==null||playerLens.type!=ParamType.BOOL||playerLens.reload!=ReloadMode.HOT||s.getBool("lensAffectsPlayer"))throw new AssertionError("player lens default/hot schema");
  s.set("lens","true");if(!playerLens.isVisible(s))throw new AssertionError("player lens visible with background lens");
  s.set("lensAffectsPlayer","true");s.set("lens","false");if(playerLens.isVisible(s)||!s.getBool("lensAffectsPlayer"))throw new AssertionError("player lens conditional UI loses preference");
  for(String f:BlackHoleSkillSchema.FORMS){s.set("form",f);if(!s.getBool("lensAffectsPlayer"))throw new AssertionError("form preset loses player lens preference: "+f);if(s.getDouble("tilt")!=0)throw new AssertionError("form preset disk is not horizontal: "+f);}
  var explicitRecipe=new java.util.LinkedHashMap<String,String>();explicitRecipe.put("tilt","-51");explicitRecipe.put("form","recipe");explicitRecipe.put("lensAffectsPlayer","true");
  s.load(BlackHoleSkillSchema.recipeParameters(explicitRecipe));
  if(s.getDouble("tilt")!=-51||!s.getBool("lensAffectsPlayer")||!s.get("form").equals("recipe"))throw new AssertionError("explicit imported tilt overwritten by form/key order");
  s.set("tilt","37");if(s.getDouble("tilt")!=37)throw new AssertionError("manual tilt edit ignored");s.set("tilt","-51");
  var exported=com.google.gson.JsonParser.parseString(PresetManager.exportJson(s)).getAsJsonObject();
  if(!exported.get("lensAffectsPlayer").getAsBoolean())throw new AssertionError("export loses player lens preference");
  var importedValues=new java.util.LinkedHashMap<String,String>();exported.entrySet().forEach(e->importedValues.put(e.getKey(),e.getValue().getAsString()));
  var imported=new ParamState(s.schema);imported.load(BlackHoleSkillSchema.recipeParameters(importedValues));
  if(!imported.getBool("lensAffectsPlayer")||imported.getDouble("tilt")!=-51)throw new AssertionError("import loses player lens preference/explicit tilt");
  importedValues.remove("lensAffectsPlayer");imported.load(BlackHoleSkillSchema.recipeParameters(importedValues));
  if(imported.getBool("lensAffectsPlayer")||importedValues.containsKey("lensAffectsPlayer"))throw new AssertionError("legacy recipe must default false without modifying input");
  var temp=java.nio.file.Files.createTempDirectory("vfx-blackhole-player-lens-check-");
  try{
   PresetManager.save(temp,BlackHoleSkillSchema.ID,"player-lens",s);var restored=new ParamState(s.schema);
   PresetManager.load(temp,BlackHoleSkillSchema.ID,"player-lens",restored);
   if(!restored.getBool("lensAffectsPlayer")||restored.getDouble("tilt")!=-51)throw new AssertionError("saved preset loses player lens preference/explicit tilt");
   java.nio.file.Files.writeString(PresetManager.presetDir(temp,BlackHoleSkillSchema.ID).resolve("legacy.json"),"{\"values\":{\"form\":\"granular\"}}");
   PresetManager.load(temp,BlackHoleSkillSchema.ID,"legacy",restored);
   if(restored.getBool("lensAffectsPlayer"))throw new AssertionError("legacy saved preset must restore player protection");
  }finally{
   try(var files=java.nio.file.Files.walk(temp)){for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(file);}
  }
  s.resetToDefaults();if(s.getBool("lensAffectsPlayer")||s.getDouble("tilt")!=0)throw new AssertionError("default reset must protect player/use horizontal disk");
  for(String f:BlackHoleSkillSchema.FORMS){s.set("form",f);if(BlackHoleSkillSchema.inner(s)<s.getFloat("core")+.119f)throw new AssertionError("core/disk gap");if(BlackHoleSkillSchema.outer(s)<BlackHoleSkillSchema.inner(s)+.499f)throw new AssertionError("disk width");}
  s.set("form","ice");if(!s.get("cool").equals("#087dff")||s.getFloat("tilt")!=0)throw new AssertionError("ice preset");
  s.set("thickness",".19");if(s.getFloat("thickness")!=.19f)throw new AssertionError("editable preset");
  s.set("tilt","23");s.set("form","gold");if(s.getFloat("thickness")!=.055f||!s.get("cool").equals("#f04a0e")||s.getDouble("tilt")!=0)throw new AssertionError("full baseline/horizontal disk reset");
  s.set("form","lens");if(s.getBool("particles")||s.getFloat("density")!=0||s.getFloat("halo")!=0)throw new AssertionError("pure lens");
  s.resetToDefaults();if(s.getBool("particles")||!s.get("form").equals("granular"))throw new AssertionError("workbench default reset");
  if(s.getDouble("outer")!=8||s.getDouble("core")!=.2||s.getDouble("infallSpeed")!=.93||s.getInt("seed")!=35562||s.getInt("steps")!=256||s.getBool("playing"))throw new AssertionError("imported defaults");
  if(Math.abs(s.getDouble("time")-1071.9779999996465)>1e-9||Math.abs(s.getDouble("distance")-26.576)>1e-9)throw new AssertionError("time/view-distance mapping");
  var clock=new dev.vfxworkbench.effects.blackhole.BlackHoleClock();
  if(clock.sample(1071.978,false,100)!=clock.sample(1071.978,false,200))throw new AssertionError("paused recipe drift");
  clock.sample(1071.978,true,200);if(Math.abs(clock.sample(1071.978,true,203)-1074.978)>1e-8)throw new AssertionError("resume continuity");
  s.set("core",".95");s.set("inner",".55");if(BlackHoleSkillSchema.inner(s)<1.069f)throw new AssertionError("runtime guard");
  System.out.println("PASS: black-hole horizontal form defaults, explicit imported/manual tilt, editable parameters, default reset, animation clock, core/disk geometry guards and conditional player-lens preference/preset/recipe roundtrip");
 }

}
