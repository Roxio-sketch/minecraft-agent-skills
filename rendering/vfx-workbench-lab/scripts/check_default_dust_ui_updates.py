from pathlib import Path
root=Path(__file__).resolve().parents[1]
def edit(rel,fn):
 p=root/rel;s=p.read_text(encoding='utf-8');p.write_text(fn(s),encoding='utf-8')
def tests(s):
 s=s.replace('if(s.getDouble("tilt")!=0)throw new AssertionError("form preset disk is not horizontal: "+f);','if(s.getDouble("tilt")!=(f.equals("white-ring")?-1:0))throw new AssertionError("form tilt baseline: "+f);')
 s=s.replace('s.getDouble("tilt")!=0||!s.getBool("playing")||s.getDouble("time")!=0||!s.getBool("particles")','s.getDouble("tilt")!=-1||!s.getBool("playing")||s.getDouble("time")!=37862.8||s.getBool("particles")')
 s=s.replace('s.getDouble("coreEmission")!=4','s.getDouble("coreEmission")!=3.95').replace('s.getDouble("coreGlow")!=1.2','s.getDouble("coreGlow")!=.8').replace('s.getDouble("coreGlowRadius")!=2.6','s.getDouble("coreGlowRadius")!=1.05')
 s=s.replace('assetState.getInt("seed")!=17','assetState.getInt("seed")!=46271').replace('assetState.getDouble("distance")!=Double.parseDouble(s.schema.get("distance").defaultValue)','assetState.getDouble("distance")!=39||assetState.getDouble("scale")!=1.8')
 s=s.replace('s.getDouble("coreEmission")!=3.95||!s.getBool("particles")','s.getDouble("coreEmission")!=3.95||s.getBool("particles")')
 s=s.replace('!s.getBool("playing")||s.getDouble("time")!=0','!s.getBool("playing")||s.getDouble("time")!=37862.8')
 s=s.replace('parameters.size()!=74','parameters.size()!=75').replace('74-field','75-field')
 s=s.replace('s.getDouble("jetStrength")!=2.4||s.getDouble("jetLength")!=7','s.getDouble("jetStrength")!=0||s.getDouble("jetLength")!=6.201').replace('s.getDouble("lensStrength")!=.45','s.getDouble("lensStrength")!=.5')
 s=s.replace('parameters.remove("coreGlow");parameters.remove("coreGlowRadius");','parameters.remove("shellEnabled");parameters.remove("coreGlow");parameters.remove("coreGlowRadius");')
 s=s.replace('"shellColor","coreGlow","coreGlowRadius"))parameters.remove(field);','"shellColor","coreGlow","coreGlowRadius","shellEnabled"))parameters.remove(field);')
 s=s.replace('restored.getDouble("jetStrength")!=2.4','restored.getDouble("jetStrength")!=0')
 # The switch is an optional typed HOT field. Legacy recipes keep their explicit strength.
 s=s.replace('checkOutflowPreset(s);checkOldOutflowRecipe(s);checkOldCoreGlowRecipe(s);','checkOutflowPreset(s);checkOldOutflowRecipe(s);checkOldCoreGlowRecipe(s);checkShellSwitch(s);')
 idx=s.index(' private static void checkOutflowSchema')
 s=s[:idx]+''' private static void checkShellSwitch(ParamState s){
  var spec=s.schema.get("shellEnabled");
  if(spec==null||spec.type!=ParamType.BOOL||spec.reload!=ReloadMode.HOT||!spec.isVisible(s)||!s.getBool("shellEnabled"))throw new AssertionError("shell switch schema");
  var old=com.google.gson.JsonParser.parseString(BlackHoleSkillSchema.exportRecipe(s)).getAsJsonObject();var parameters=old.getAsJsonObject("parameters");parameters.remove("shellEnabled");
  s.set("shellEnabled","false");BlackHoleSkillSchema.loadRecipe(s,BlackHoleSkillSchema.decodeRecipe(old.toString(),s));
  if(!s.getBool("shellEnabled")||s.getDouble("shellStrength")!=.28)throw new AssertionError("74-field import preserves previously visible shells");
  var before=s.snapshot();s.set("shellEnabled","false");String json=BlackHoleSkillSchema.exportRecipe(s);var copy=new ParamState(s.schema);BlackHoleSkillSchema.loadRecipe(copy,BlackHoleSkillSchema.decodeRecipe(json,copy));
  if(copy.getBool("shellEnabled")||copy.getDouble("shellStrength")!=.28||copy.getDouble("shellSpeed")!=.7)throw new AssertionError("shell off retains parameters across export/import");
  for(String format:java.util.List.of("black-hole-preview/v1","black-hole-preview/v2","black-hole-world/v1"))expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\\"schema\\":\\""+format+"\\",\\"parameters\\":{\\"shellEnabled\\":false}}"));
  expectUnchanged(s,()->BlackHoleSkillSchema.importRecipe("{\\"schema\\":\\"black-hole-world/v2\\",\\"parameters\\":{\\"shellEnabled\\":\\"false\\"}}"));
  BlackHoleSkillSchema.loadRecipe(s,before);
 }

'''+s[idx:]
 return s
edit('src/test/java/dev/vfxworkbench/BlackHoleSchemaCheck.java',tests)
# Preserve old outflow/core test strengths explicitly. The real-default captures use new defaults.
def regression(s):
 s=s.replace('"white-hole-radiance/v2"','"white-hole-default-dust/v3"')
 s=s.replace('"wall-external-on-t8", "wall-external-off-t8"','"wall-external-on-t8", "wall-external-off-t8",\n        "dust-oblique-on-t8", "dust-oblique-density-zero-t8", "dust-top-on-t8", "dust-top-density-zero-t8", "dust-late-on", "dust-late-density-zero", "shell-toggle-restored-t8", "shell-toggle-disabled-t8", "shell-toggle-reenabled-t8", "user-default-live-time"')
 s=s.replace('p.set("scale", p.schema.get("scale").defaultValue); p.set("distance", p.schema.get("distance").defaultValue);','')
 s=s.replace('p.set("lensAffectsPlayer", "false");','')
 s=s.replace('configureWhite(); state().set("particles", "false"); state().set("lens", "false");\n        state().set("density", "0"); state().set("halo", "0"); state().set("shellStrength", "0"); state().set("coreGlow", "0");','configureWhite(); state().set("jetStrength","2.4"); state().set("jetLength","7"); state().set("particles", "false"); state().set("lens", "false");\n        state().set("density", "0"); state().set("halo", "0"); state().set("shellStrength", "0"); state().set("coreGlow", "0");')
 s=s.replace('private static void configureCoreGlowOnly() {\n        configureWhite();','private static void configureCoreGlowOnly() {\n        configureWhite(); state().set("core",".58"); state().set("coreEmission","4"); state().set("coreGlow","1.2"); state().set("coreGlowRadius","2.6");')
 s=s.replace('case SHELL_OFF -> { state().set("time", "8"); state().set("shellStrength", "0"); }','case SHELL_OFF -> { state().set("time", "8"); state().set("shellEnabled", "false"); }')
 s=s.replace('            default -> throw new IllegalStateException("Unknown fixture stage " + stage);','''            case 39,41,43 -> {
                state().set("form","dust");BlackHoleSkillSchema.applyForm(state(),"dust");
                state().set("playing","false");state().set("time",stage==43?"37862.8":"8");
                state().set("halo","0");state().set("lens","false");state().set("particles","false");
                BlackHoleRender.resetClock();useFloatingCenter();
                camera(mc,fixedCenter.add(0,stage==41?18:4,stage==41?-.2:-18),fixedCenter);
                check("dust-is-independent-of-particle-toggle-"+stage,!state().getBool("particles")&&state().get("diskStyle").equals("dust"));
            }
            case 40,42,44 -> state().set("density","0");
            case 45 -> {configureShellOnly();useFloatingCenter();camera(mc,fixedCenter.add(0,4,-18),fixedCenter);}
            case 46 -> {state().set("shellEnabled","false");check("shell-toggle-keeps-strength",state().getDouble("shellStrength")==.28);}
            case 47 -> state().set("shellEnabled","true");
            case 48 -> {configureWhite();state().set("time","37862.8");useFloatingCenter();camera(mc,fixedCenter.add(0,4,-39),fixedCenter);}
            default -> throw new IllegalStateException("Unknown fixture stage " + stage);''')
 # Add paired real-render differences, before final old tests.
 idx=s.index('                check("time-advances-visible-outflow"')
 s=s[:idx]+'''                int dustOblique=changedPixels(39,40),dustTop=changedPixels(41,42),dustLate=changedPixels(43,44);
                int switched=changedPixels(45,46),reenabled=changedPixels(45,47);
                comparisons.addProperty("dustObliqueContributionPixels",dustOblique);comparisons.addProperty("dustTopContributionPixels",dustTop);comparisons.addProperty("dustLateContributionPixels",dustLate);
                comparisons.addProperty("shellToggleContributionPixels",switched);comparisons.addProperty("shellReenabledDifferencePixels",reenabled);
                check("dust-visible-with-particles-off-oblique",dustOblique>100);check("dust-visible-top",dustTop>100);check("dust-visible-at-user-long-time",dustLate>100);
                check("shell-toggle-controls-real-render",switched>100);check("shell-toggle-restores-identical-render",reenabled==0);
'''+s[idx:]
 # Find existing helper's exact name for paired captures.
 return s
edit('src/main/java/dev/vfxworkbench/effects/blackhole/WhiteHoleRegression.java',regression)
print('Schema and Forge fixtures revised')
