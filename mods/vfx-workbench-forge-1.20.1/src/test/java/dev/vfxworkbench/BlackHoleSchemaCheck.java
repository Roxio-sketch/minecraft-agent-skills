package dev.vfxworkbench;
import dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema;
import dev.vfxworkbench.core.*;
public final class BlackHoleSchemaCheck {
 public static void main(String[] args) throws java.io.IOException {
  dev.vfxworkbench.effects.blackhole.BlackHoleCameraCheck.run();
  BlackHoleSkillSchema.register();var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
  if(BlackHoleSkillSchema.PRESET_FORMS.size()!=5||new java.util.HashSet<>(BlackHoleSkillSchema.PRESET_FORMS).size()!=5
    ||!BlackHoleSkillSchema.FORMS.containsAll(BlackHoleSkillSchema.PRESET_FORMS)
    ||BlackHoleSkillSchema.PRESET_FORMS.contains("recipe"))throw new AssertionError("curated presets");
  for(String legacy:java.util.List.of("gold","ice","violet","crimson","recipe","classic","cinematic")){
   if(BlackHoleSkillSchema.PRESET_FORMS.contains(legacy))throw new AssertionError("duplicate UI preset: "+legacy);
   BlackHoleSkillSchema.loadRecipe(s,java.util.Map.of("form",legacy,"outer","6.73","hot","#123456"));
   if(!s.get("form").equals(legacy)||s.getDouble("outer")!=6.73||!s.get("hot").equals("#123456"))throw new AssertionError("legacy import overwritten: "+legacy);
  }
  s.resetToDefaults();
  BlackHoleSkillSchema.loadRecipe(s,java.util.Map.of("form","ribbons"));
  if(s.getDouble("diskBloom")!=.45)throw new AssertionError("partial ribbon preset diffusion");
  BlackHoleSkillSchema.loadRecipe(s,java.util.Map.of("form","recipe","diskStyle","ribbons","hot","#123456"));
  if(s.getDouble("diskBloom")!=0||!s.get("hot").equals("#123456"))throw new AssertionError("legacy ribbon appearance/diffusion opt-in");
  s.resetToDefaults();
  checkWhiteHoleRecipes(s);
  var diskTilt=s.schema.get("tilt");
  if(s.getDouble("tilt")!=0||diskTilt.min!=-90||diskTilt.max!=90)throw new AssertionError("horizontal disk default/range");
  var playerLens=s.schema.get("lensAffectsPlayer");
  if(playerLens==null||playerLens.type!=ParamType.BOOL||playerLens.reload!=ReloadMode.HOT||s.getBool("lensAffectsPlayer"))throw new AssertionError("player lens default/hot schema");
  s.set("lens","true");if(!playerLens.isVisible(s))throw new AssertionError("player lens visible with background lens");
  s.set("lensAffectsPlayer","true");s.set("lens","false");if(playerLens.isVisible(s)||!s.getBool("lensAffectsPlayer"))throw new AssertionError("player lens conditional UI loses preference");
  for(String f:BlackHoleSkillSchema.FORMS){s.set("form",f);if(!s.getBool("lensAffectsPlayer"))throw new AssertionError("form preset loses player lens preference: "+f);if(s.getDouble("tilt")!=(f.equals("white-ring")?-1:0))throw new AssertionError("form tilt baseline: "+f);if(!f.equals("white-ring")&&(s.getDouble("shellStrength")!=0||s.getDouble("coreGlow")!=0))throw new AssertionError("black form enables new white glow/shells: "+f);}
  var explicitRecipe=new java.util.LinkedHashMap<String,String>();explicitRecipe.put("tilt","-51");explicitRecipe.put("form","recipe");explicitRecipe.put("lensAffectsPlayer","true");
  BlackHoleSkillSchema.loadRecipe(s,explicitRecipe);
  if(s.getDouble("tilt")!=-51||!s.getBool("lensAffectsPlayer")||!s.get("form").equals("recipe"))throw new AssertionError("explicit imported tilt overwritten by form/key order");
  s.set("tilt","37");if(s.getDouble("tilt")!=37)throw new AssertionError("manual tilt edit ignored");s.set("tilt","-51");
  var exported=com.google.gson.JsonParser.parseString(PresetManager.exportJson(s)).getAsJsonObject();
  if(!exported.get("lensAffectsPlayer").getAsBoolean())throw new AssertionError("export loses player lens preference");
  var importedValues=new java.util.LinkedHashMap<String,String>();exported.entrySet().forEach(e->importedValues.put(e.getKey(),e.getValue().getAsString()));
  var imported=new ParamState(s.schema);BlackHoleSkillSchema.loadRecipe(imported,importedValues);
  if(!imported.getBool("lensAffectsPlayer")||imported.getDouble("tilt")!=-51)throw new AssertionError("import loses player lens preference/explicit tilt");
  importedValues.remove("lensAffectsPlayer");BlackHoleSkillSchema.loadRecipe(imported,importedValues);
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
  for(String f:BlackHoleSkillSchema.FORMS){s.set("form",f);if(BlackHoleSkillSchema.inner(s)<s.getFloat("core")+.019f)throw new AssertionError("core/disk gap");if(BlackHoleSkillSchema.outer(s)<BlackHoleSkillSchema.inner(s)+.499f)throw new AssertionError("disk width");}
  s.set("distance","32");s.set("scale","1.2");s.set("form","cinematic");
  if(!s.get("hot").equals("#fff5d3")||s.getDouble("density")!=7||s.getDouble("exposure")!=1.7
    ||s.getDouble("thickness")!=.075||!s.getBool("playing")||s.getDouble("distance")!=32||s.getDouble("scale")!=1.2)
   throw new AssertionError("cinematic independent luminous preset/placement");
  s.set("form","ribbons");
  if(!s.get("diskStyle").equals("ribbons")||!s.get("hot").equals("#b5ffff")||s.getDouble("beaming")!=1
    ||s.getDouble("density")!=5||s.getDouble("diskBloom")!=.45||s.getDouble("exposure")!=1.05||s.getDouble("bend")!=1.96||!s.getBool("playing"))
   throw new AssertionError("independent reference ribbons preset");
  s.set("form","classic");
  if(!s.get("diskStyle").equals("ribbons")||!s.get("hot").equals("#bdefff")||!s.get("cool").equals("#095f91")
    ||s.getDouble("bandDetail")!=5||s.getDouble("thickness")!=.065)throw new AssertionError("workbench program bands material");
  for(String form:BlackHoleSkillSchema.PRESET_FORMS)if(!form.equals("white-ring")){
   s.set("form",form);
   if(s.getDouble("core")!=.95||s.getDouble("inner")!=2.12||s.getDouble("outer")!=8||s.getDouble("tilt")!=0)
    throw new AssertionError("shared geometry: "+form);
  }
  s.set("form","ice");if(!s.get("cool").equals("#087dff")||s.getFloat("tilt")!=0)throw new AssertionError("ice preset");
  s.set("thickness",".19");if(s.getFloat("thickness")!=.19f)throw new AssertionError("editable preset");
  s.set("tilt","23");s.set("form","gold");if(s.getFloat("thickness")!=.055f||!s.get("cool").equals("#f04a0e")||s.getDouble("tilt")!=0)throw new AssertionError("full baseline/horizontal disk reset");
  s.set("form","lens");if(s.getBool("particles")||s.getFloat("density")!=0||s.getFloat("halo")!=0)throw new AssertionError("pure lens");
  s.resetToDefaults();if(s.getBool("particles")||!s.get("form").equals("granular"))throw new AssertionError("workbench default reset");
  if(s.getDouble("outer")!=8||s.getDouble("core")!=.95||s.getDouble("infallSpeed")!=.93||s.getInt("seed")!=35562||s.getInt("steps")!=256||s.getBool("playing"))throw new AssertionError("imported defaults");
  if(Math.abs(s.getDouble("time")-1071.9779999996465)>1e-9||Math.abs(s.getDouble("distance")-32)>1e-9)throw new AssertionError("time/view-distance mapping");
  var clock=new dev.vfxworkbench.effects.blackhole.BlackHoleClock();
  if(clock.sample(1071.978,false,100)!=clock.sample(1071.978,false,200))throw new AssertionError("paused recipe drift");
  clock.sample(1071.978,true,200);if(Math.abs(clock.sample(1071.978,true,203)-1074.978)>1e-8)throw new AssertionError("resume continuity");
  s.set("core",".95");s.set("inner",".55");if(BlackHoleSkillSchema.inner(s)<.969f)throw new AssertionError("runtime guard");
  System.out.println("PASS: white-hole editable core-radiance/outflow/shell controls, complete old 66/72-field world/v2 explicit parameter retention with absent glow disabled, atomic 77-field typed world/v2 and vfx preset roundtrip, rejected invalid/historical recipes, legacy black defaults, horizontal forms, explicit imported/manual tilt, animation clock, geometry guards and player-lens preference");
 }

 private static void checkWhiteHoleRecipes(ParamState s) throws java.io.IOException {
  var mode=s.schema.get("objectMode");var emission=s.schema.get("coreEmission");
  if(mode==null||mode.type!=ParamType.ENUM||!mode.enumValues.equals(java.util.List.of("black","white"))||mode.reload!=ReloadMode.HOT||!s.get("objectMode").equals("black"))throw new AssertionError("mode schema/legacy default");
  if(emission==null||emission.type!=ParamType.FLOAT||emission.min!=0||emission.max!=4||s.getDouble("coreEmission")!=1)throw new AssertionError("core emission schema/legacy default");
  if(!s.schema.get("infallSpeed").label.equals("盘纹流速")||!s.schema.get("particles").label.equals("盘面粒子")||s.schema.get("particles").help.isEmpty())throw new AssertionError("mode-aware motion UI");
  checkOutflowSchema(s);checkCoreGlowSchema(s);
  s.set("form","white-ring");
  if(!s.get("objectMode").equals("white")||s.getDouble("tilt")!=-1||!s.getBool("playing")||s.getDouble("time")!=37862.8||s.getBool("particles")||s.getInt("particleCount")!=640||s.getDouble("particleSpeed")!=1.3||s.getDouble("particleSize")!=.022||s.getDouble("particleOuter")!=6.2||s.getDouble("particleBrightness")!=1.6)throw new AssertionError("white-ring outward playable preset");
  checkOutflowPreset(s);
  if(!s.get("coreColor").equals("#eefaff")||!s.get("coreEdgeColor").equals("#58bfff")||s.getDouble("coreEmission")!=3.95||s.getDouble("coreGlow")!=.8||s.getDouble("coreGlowRadius")!=1.05||s.getDouble("coreGradientStrength")!=.65)throw new AssertionError("white-ring blue-white emissive core and independent core radiance");
  if(!s.get("particleHot").equals("#dffaff")||!s.get("particleCold").equals("#2787f3"))throw new AssertionError("white-ring spatial particle colors");
  try(var resource=BlackHoleSchemaCheck.class.getResourceAsStream("/assets/vfxworkbench/recipes/white-ring-default.json")){
   if(resource==null)throw new AssertionError("Missing importable white-ring default recipe");
   String recipe=new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   var asset=com.google.gson.JsonParser.parseString(recipe).getAsJsonObject();var assetParameters=asset.getAsJsonObject("parameters");
   if(!asset.get("schema").getAsString().equals("black-hole-world/v2")||assetParameters.size()!=s.schema.params.size()||!assetParameters.get("coreEmission").getAsJsonPrimitive().isNumber()||!assetParameters.get("playing").getAsJsonPrimitive().isBoolean())throw new AssertionError("Default white asset must be complete typed world/v2");
   var assetState=new ParamState(s.schema);BlackHoleSkillSchema.loadRecipe(assetState,BlackHoleSkillSchema.decodeRecipe(recipe,assetState));
   if(!sameValues(assetState,s.snapshot())||assetState.getInt("seed")!=46271||assetState.getDouble("particleSize")!=.022||assetState.getDouble("distance")!=39||assetState.getDouble("scale")!=1.8)throw new AssertionError("White asset was exported from edited test state or differs from fresh preset");
  }
  s.set("form","ice");BlackHoleSkillSchema.loadRecipe(s,java.util.Map.of("form","white-ring"));
  if(!s.get("objectMode").equals("white")||s.getDouble("coreEmission")!=3.95||s.getBool("particles"))throw new AssertionError("partial raw white-ring recipe retains preset mode/emission");
  checkOutflowPreset(s);
  s.set("form","ice");BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"form\":\"white-ring\"}}");
  if(!s.get("objectMode").equals("white")||s.getDouble("coreEmission")!=3.95||!s.getBool("playing")||s.getDouble("time")!=37862.8)throw new AssertionError("partial world/v2 white-ring recipe applies complete new preset");
  checkOutflowPreset(s);checkOldOutflowRecipe(s);checkOldCoreGlowRecipe(s);checkShellSwitch(s);
  s.set("lensAffectsPlayer","true");s.set("scale","2.1");s.set("distance","19");
  s.set("form","ice");
  if(!s.get("objectMode").equals("black")||s.getDouble("coreEmission")!=1||s.getDouble("scale")!=2.1||s.getDouble("distance")!=19||!s.getBool("lensAffectsPlayer"))throw new AssertionError("black form restores mode/emission, keeps placement/player preference");
  s.set("form","white-ring");s.set("tilt","-23");s.set("coreEmission","2.25");s.set("coreGlow","1.1");s.set("coreGlowRadius","2.1");s.set("particleSpeed",".7");
  var before=s.snapshot();
  String json=BlackHoleSkillSchema.exportRecipe(s);
  var root=com.google.gson.JsonParser.parseString(json).getAsJsonObject();var parameters=root.getAsJsonObject("parameters");
  if(!root.get("schema").getAsString().equals("black-hole-world/v2")||!parameters.get("coreEmission").getAsJsonPrimitive().isNumber()||!parameters.get("playing").getAsJsonPrimitive().isBoolean()||!parameters.get("steps").getAsJsonPrimitive().isNumber()||!parameters.get("objectMode").getAsString().equals("white"))throw new AssertionError("typed world/v2 export");
  for(String field:java.util.List.of("jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","coreGlow","coreGlowRadius"))if(!parameters.get(field).getAsJsonPrimitive().isNumber())throw new AssertionError("typed numeric white field: "+field);
  if(!parameters.get("shellColor").getAsJsonPrimitive().isString()||parameters.size()!=77)throw new AssertionError("complete 77-field typed white export");
  int[] notifications={0};boolean[] sawComplete={true};
  s.onHotChange(()->{notifications[0]++;sawComplete[0]&=s.get("objectMode").equals("white")&&s.getDouble("coreEmission")==2.25&&s.getDouble("coreGlow")==1.1&&s.getDouble("coreGlowRadius")==2.1&&s.getDouble("tilt")==-23;});
  BlackHoleSkillSchema.importRecipe(json);
  if(!sameValues(s,before)||notifications[0]!=1||!sawComplete[0])throw new AssertionError("world roundtrip must preserve typed values and notify once after complete explicit recipe; notifications="+notifications[0]+", complete="+sawComplete[0]);
  var invalid=new java.util.LinkedHashMap<String,String>();invalid.put("form","ice");invalid.put("objectMode","white");invalid.put("coreEmission","4.1");
  expectUnchanged(s,()->BlackHoleSkillSchema.loadRecipe(s,invalid));
  invalid.put("coreEmission","NaN");expectUnchanged(s,()->BlackHoleSkillSchema.loadRecipe(s,invalid));
  invalid.put("coreEmission","1");invalid.put("objectMode","negative");expectUnchanged(s,()->BlackHoleSkillSchema.loadRecipe(s,invalid));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"coreEmission\":\"2\"}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"particles\":\"true\"}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-preview/v2\",\"parameters\":{\"objectMode\":\"white\"}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-preview/v2\",\"parameters\":{\"coreEmission\":1}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-preview/v2\",\"parameters\":{\"form\":\"white-ring\"}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v1\",\"parameters\":{\"objectMode\":\"white\"}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v1\",\"parameters\":{\"coreEmission\":1}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v1\",\"parameters\":{\"form\":\"white-ring\"}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"unknownField\":0}}"));
  for(String format:java.util.List.of("black-hole-preview/v1","black-hole-preview/v2","black-hole-world/v1"))for(String field:java.util.List.of("jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor","coreGlow","coreGlowRadius")){
   String value=field.equals("shellColor")?"\"#72dfff\"":"1";
   expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\""+format+"\",\"parameters\":{\""+field+"\":"+value+"}}"));
  }
  for(String field:java.util.List.of("jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","coreGlow","coreGlowRadius")){
   var spec=s.schema.get(field);
   for(String value:java.util.List.of(Double.toString(spec.min-.001),Double.toString(spec.max+.001),"NaN","Infinity"))expectUnchanged(s,()->BlackHoleSkillSchema.loadRecipe(s,java.util.Map.of("form","ice",field,value)));
   expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\""+field+"\":\"1\"}}"));
  }
  expectUnchanged(s,()->BlackHoleSkillSchema.loadRecipe(s,java.util.Map.of("form","white-ring","shellColor","#xyz123")));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"shellColor\":123}}"));
  var legacy=new java.util.LinkedHashMap<String,String>();legacy.put("tilt","17");legacy.put("form","recipe");legacy.put("coreColor","#123456");
  int oldNotifications=notifications[0];
  BlackHoleSkillSchema.loadRecipe(s,legacy);
  if(!s.get("objectMode").equals("black")||s.getDouble("coreEmission")!=1||s.getDouble("tilt")!=17||!s.get("coreColor").equals("#123456")||s.getBool("lensAffectsPlayer")||legacy.containsKey("objectMode")||legacy.containsKey("coreEmission")||notifications[0]!=oldNotifications+1)throw new AssertionError("legacy vfx recipe atomic black/1/protected reset without mutating input");
  s.set("form","white-ring");
  BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v1\",\"parameters\":{\"coreColor\":\"#121212\",\"tilt\":13,\"time\":4,\"playing\":false}}");
  if(!s.get("objectMode").equals("black")||s.getDouble("coreEmission")!=1||s.getDouble("tilt")!=13||s.getBool("playing")||s.getDouble("time")!=4)throw new AssertionError("legacy world/v1 restores black/1");
  s.set("form","white-ring");
  BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-preview/v2\",\"parameters\":{\"coreColor\":\"#101010\",\"tilt\":-51,\"viewDistance\":12,\"lensBackground\":0.4,\"steps\":160.0},\"playing\":false,\"time\":9}");
  if(!s.get("objectMode").equals("black")||s.getDouble("coreEmission")!=1||s.getDouble("tilt")!=-51||s.getBool("playing")||s.getDouble("time")!=9||Math.abs(s.getDouble("distance")-12*s.getDouble("scale"))>1e-9||s.getDouble("lensStrength")!=.4||!s.getBool("lens"))throw new AssertionError("legacy structured preview resets black/1 and honors playback/mapping");
  var temp=java.nio.file.Files.createTempDirectory("vfx-whitehole-recipe-check-");
  try{
   s.set("form","white-ring");s.set("coreEmission","2.5");s.set("tilt","-31");
   PresetManager.save(temp,BlackHoleSkillSchema.ID,"white",s);var restored=new ParamState(s.schema);
   PresetManager.load(temp,BlackHoleSkillSchema.ID,"white",restored);
   if(!restored.snapshot().equals(s.snapshot()))throw new AssertionError("white saved vfx preset roundtrip");
   java.nio.file.Files.writeString(PresetManager.presetDir(temp,BlackHoleSkillSchema.ID).resolve("legacy.json"),"{\"values\":{\"form\":\"ice\",\"tilt\":23}}");
   PresetManager.load(temp,BlackHoleSkillSchema.ID,"legacy",restored);
   if(!restored.get("objectMode").equals("black")||restored.getDouble("coreEmission")!=1||restored.getDouble("tilt")!=23)throw new AssertionError("legacy saved vfx preset clears previous white mode");
   var badSnapshot=restored.snapshot();java.nio.file.Files.writeString(PresetManager.presetDir(temp,BlackHoleSkillSchema.ID).resolve("bad.json"),"{\"values\":{\"form\":\"white-ring\",\"coreEmission\":-1}}");
   expectUnchanged(restored,()->{try{PresetManager.load(temp,BlackHoleSkillSchema.ID,"bad",restored);}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}});
   if(!restored.snapshot().equals(badSnapshot))throw new AssertionError("invalid saved preset partially applied");
  }finally{
   try(var files=java.nio.file.Files.walk(temp)){for(var file:files.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(file);}
  }
  s.set("scale","1.6");s.resetToDefaults();
  if(!s.get("objectMode").equals("black")||s.getDouble("coreEmission")!=1||s.getDouble("shellStrength")!=0||s.getDouble("coreGlow")!=0||s.getBool("particles")||s.getBool("playing"))throw new AssertionError("default reset preserves historical black granular recipe");
 }

 private static void checkShellSwitch(ParamState s){
  var spec=s.schema.get("shellEnabled");
  if(spec==null||spec.type!=ParamType.BOOL||spec.reload!=ReloadMode.HOT||!spec.isVisible(s)||s.getBool("shellEnabled"))throw new AssertionError("shell switch schema");
  var old=com.google.gson.JsonParser.parseString(BlackHoleSkillSchema.exportRecipe(s)).getAsJsonObject();var parameters=old.getAsJsonObject("parameters");parameters.remove("shellEnabled");
  s.set("shellEnabled","false");BlackHoleSkillSchema.loadRecipe(s,BlackHoleSkillSchema.decodeRecipe(old.toString(),s));
  if(!s.getBool("shellEnabled")||s.getDouble("shellStrength")!=.28)throw new AssertionError("74-field import preserves previously visible shells");
  var before=s.snapshot();s.set("shellEnabled","false");String json=BlackHoleSkillSchema.exportRecipe(s);var copy=new ParamState(s.schema);BlackHoleSkillSchema.loadRecipe(copy,BlackHoleSkillSchema.decodeRecipe(json,copy));
  if(copy.getBool("shellEnabled")||copy.getDouble("shellStrength")!=.28||copy.getDouble("shellSpeed")!=.7)throw new AssertionError("shell off retains parameters across export/import");
  for(String format:java.util.List.of("black-hole-preview/v1","black-hole-preview/v2","black-hole-world/v1"))expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\""+format+"\",\"parameters\":{\"shellEnabled\":false}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\"schema\":\"black-hole-world/v2\",\"parameters\":{\"shellEnabled\":\"false\"}}"));
  BlackHoleSkillSchema.loadRecipe(s,before);
 }

 private static void checkOutflowSchema(ParamState s){
  var keys=java.util.List.of("jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor");
  double[][] numeric={{0,4,1},{0,3,0},{0,4,.7},{1,8,5},{.02,1,.12}};
  for(int i=0;i<keys.size();i++){
   var spec=s.schema.get(keys.get(i));
   if(spec==null||spec.reload!=ReloadMode.HOT||spec.isVisible(s)||!spec.help.contains("白洞专用"))throw new AssertionError("hidden/hot white-only UI field: "+keys.get(i));
   if(i<5&&(spec.type!=ParamType.FLOAT||spec.min!=numeric[i][0]||spec.max!=numeric[i][1]||s.getDouble(spec.key)!=numeric[i][2]))throw new AssertionError("outflow bounds/legacy default: "+spec.key);
   if(i>0&&!spec.group.equals("外扩光壳"))throw new AssertionError("light-shell UI group: "+spec.key);
  }
  if(!s.schema.get("jetSpeed").label.equals("喷流输运速度")||!s.schema.get("jetSpeed").help.contains("0 保持当前相位")||!s.schema.get("shellSpeed").help.contains("0 保持当前相位")||!s.get("shellColor").equals("#72dfff"))throw new AssertionError("outflow UI labels/color/freeze contract");
  var white=new ParamState(s.schema);white.set("objectMode","white");
  for(String key:keys)if(!white.schema.get(key).isVisible(white))throw new AssertionError("white-only UI is not visible in white mode: "+key);
 }

 private static void checkOutflowPreset(ParamState s){
  if(s.getDouble("jetStrength")!=0||s.getDouble("jetLength")!=6.201||s.getDouble("jetWidth")!=.5||s.getDouble("jetSpeed")!=1.15||!s.get("jetBaseColor").equals("#eaffff")||!s.get("jetTipColor").equals("#72dfff")||s.getDouble("shellStrength")!=.28||s.getDouble("shellSpeed")!=.7||s.getDouble("shellRadius")!=4.8||s.getDouble("shellWidth")!=.1||!s.get("shellColor").equals("#72dfff")||s.getDouble("lensStrength")!=.5)throw new AssertionError("white-ring must select visible outward jets/shells and positive lens preset");
  if(s.getDouble("coreEmission")!=3.95||s.getDouble("coreGlow")!=.8||s.getDouble("coreGlowRadius")!=1.05)throw new AssertionError("partial white-ring preset must select current emissive core with radiance");
 }

 private static void checkCoreGlowSchema(ParamState s){
  String[] keys={"coreGlow","coreGlowRadius"};String[] labels={"核心光芒强度","核心光芒范围倍率"};double[][] expected={{0,3,0},{1.05,4,1.8}};
  var white=new ParamState(s.schema);white.set("objectMode","white");
  for(int i=0;i<keys.length;i++){
   var spec=s.schema.get(keys[i]);
   if(spec==null||spec.type!=ParamType.FLOAT||spec.reload!=ReloadMode.HOT||!spec.label.equals(labels[i])||!spec.group.equals("光与材质")||spec.min!=expected[i][0]||spec.max!=expected[i][1]||s.getDouble(keys[i])!=expected[i][2]||spec.isVisible(s)||!spec.isVisible(white)||!spec.help.contains("白洞专用")||!spec.help.contains("不是场景动态光源")||!spec.help.contains("盘晕")||!spec.help.contains("外扩光壳")||!spec.help.contains("屏幕扩散")||!spec.help.contains("不会照亮地形"))throw new AssertionError("core radiance independent white-only HOT schema: "+keys[i]);
  }
  if(!s.schema.get("coreGlow").help.contains("核心体积柔光")||!s.schema.get("coreGlowRadius").help.contains("世界冠层")||!s.schema.get("coreGlowRadius").help.contains("投影视觉宽度"))throw new AssertionError("core radiance UI explains volume and screen-space radius contract");
 }

 private static void checkOldCoreGlowRecipe(ParamState source){
  var old=com.google.gson.JsonParser.parseString(BlackHoleSkillSchema.exportRecipe(source)).getAsJsonObject();var parameters=old.getAsJsonObject("parameters");
  parameters.remove("diskBloom");parameters.remove("diskBloomRadius");parameters.remove("shellEnabled");parameters.remove("coreGlow");parameters.remove("coreGlowRadius");parameters.addProperty("coreEmission",3.2);
  if(parameters.size()!=72)throw new AssertionError("pre-glow world/v2 fixture must contain the prior 72 fields");
  var restored=new ParamState(source.schema);BlackHoleSkillSchema.applyForm(restored,"white-ring");int[] calls={0};boolean[] complete={true};
  restored.onHotChange(()->{calls[0]++;complete[0]&=restored.getDouble("coreGlow")==0&&restored.getDouble("coreGlowRadius")==1.8&&restored.getDouble("coreEmission")==3.2&&restored.getDouble("shellStrength")==.28;});
  BlackHoleSkillSchema.loadRecipe(restored,BlackHoleSkillSchema.decodeRecipe(old.toString(),restored));
  if(!restored.get("objectMode").equals("white")||restored.getDouble("coreGlow")!=0||restored.getDouble("coreGlowRadius")!=1.8||restored.getDouble("coreEmission")!=3.2||restored.getDouble("shellStrength")!=.28||restored.getDouble("jetStrength")!=0||calls[0]!=1||!complete[0])throw new AssertionError("complete old 72-field world/v2 must not acquire core glow or change explicit radiance/outflow");
  if(parameters.has("coreGlow")||parameters.has("coreGlowRadius"))throw new AssertionError("old core-glow import mutated JSON");
 }

 private static void checkOldOutflowRecipe(ParamState source){
  var old=com.google.gson.JsonParser.parseString(BlackHoleSkillSchema.exportRecipe(source)).getAsJsonObject();var parameters=old.getAsJsonObject("parameters");
  for(String field:java.util.List.of("jetSpeed","shellStrength","shellSpeed","shellRadius","shellWidth","shellColor","coreGlow","coreGlowRadius","shellEnabled","diskBloom","diskBloomRadius"))parameters.remove(field);
  parameters.addProperty("coreEmission",3.2);
  parameters.addProperty("jetStrength",0);parameters.addProperty("particleSpeed",1.15);parameters.addProperty("particleSize",.02);parameters.addProperty("particleBrightness",1.3);parameters.addProperty("lensStrength",.7);
  if(parameters.size()!=66)throw new AssertionError("old world/v2 fixture must retain the complete prior 66 fields");
  var restored=new ParamState(source.schema);BlackHoleSkillSchema.applyForm(restored,"white-ring");int[] calls={0};boolean[] complete={true};
  restored.onHotChange(()->{calls[0]++;complete[0]&=restored.getDouble("coreGlow")==0&&restored.getDouble("shellStrength")==0&&restored.getDouble("jetStrength")==0&&restored.getDouble("particleSize")==.02;});
  var values=BlackHoleSkillSchema.decodeRecipe(old.toString(),restored);BlackHoleSkillSchema.loadRecipe(restored,values);
  if(!restored.get("objectMode").equals("white")||restored.getDouble("coreGlow")!=0||restored.getDouble("coreGlowRadius")!=1.8||restored.getDouble("coreEmission")!=3.2||restored.getDouble("shellStrength")!=0||restored.getDouble("jetSpeed")!=1||restored.getDouble("shellSpeed")!=.7||restored.getDouble("shellRadius")!=5||restored.getDouble("shellWidth")!=.12||!restored.get("shellColor").equals("#72dfff")||restored.getDouble("jetStrength")!=0||restored.getDouble("particleSpeed")!=1.15||restored.getDouble("particleSize")!=.02||restored.getDouble("particleBrightness")!=1.3||restored.getDouble("lensStrength")!=.7||calls[0]!=1||!complete[0])throw new AssertionError("complete old world/v2 white ring must retain prior appearance and publish one complete state");
  if(parameters.has("shellStrength")||parameters.has("jetSpeed"))throw new AssertionError("old world/v2 import mutated source JSON");
 }

 private static void expectUnchanged(ParamState state,Runnable load){
  var before=state.snapshot();
  try{load.run();throw new AssertionError("invalid recipe accepted");}catch(IllegalArgumentException expected){}
  if(!state.snapshot().equals(before))throw new AssertionError("invalid recipe changed live state");
 }

 /** JSON canonicalizes numeric spelling (.7→0.7) while preserving the actual value. */
 private static boolean sameValues(ParamState state,java.util.Map<String,String> expected){
  if(!state.snapshot().keySet().equals(expected.keySet()))return false;
  for(var entry:expected.entrySet()){
   var spec=state.schema.get(entry.getKey());
   if(spec.type==ParamType.FLOAT||spec.type==ParamType.INT){
    if(state.getDouble(spec.key)!=Double.parseDouble(entry.getValue()))return false;
   }else if(!state.get(spec.key).equals(entry.getValue()))return false;
  }
  return true;
 }

}
