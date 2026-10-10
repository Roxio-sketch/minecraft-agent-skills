package dev.vfxworkbench.effects.lightning;

import com.google.gson.*;
import dev.vfxworkbench.core.ParamRegistry;
import java.util.List;

/** Layer isolation, legacy defaults and the shared UI/render tint contract. */
final class GradientLayerCheck {
    static void run() {
        var state=ParamRegistry.state(LightningSkillSchema.ID);
        var saved=state.snapshot();
        try {
            LightningRecipe r=new LightningRecipe();
            r.gradientEnabled=true;r.gradientRatio=31;
            r.coreColor="#FFFFFF";r.glowColor="#895A75";
            r.gradientStart="#E700F2";r.gradientEnd="#E3B026";
            for(String target:List.of("both","core","glow"))for(String mode:List.of("vivid","additive")) {
                r.gradientTarget=target;r.gradientCoreMode=mode;
                LightningSkillSchema.importRecipe(new Gson().toJson(r));
                if(LightningAppearance.vividCore(r)!=(!target.equals("glow")&&mode.equals("vivid")))throw new AssertionError("core blend selection");
                for(boolean halo:new boolean[]{false,true})for(double along:new double[]{0,.25,.5,.75,1}) {
                    boolean applies=target.equals("both")||target.equals(halo?"glow":"core");
                    if(LightningAppearance.usesGradient(r,halo)!=applies)throw new AssertionError("gradient layer isolation");
                    var color=LightningAppearance.colorAt(r,halo,along);
                    int packed=(Math.round(color[0]*255)<<16)|(Math.round(color[1]*255)<<8)|Math.round(color[2]*255);
                    if(packed!=LightningSkillSchema.previewColor(state,halo,along))throw new AssertionError("UI preview differs from actual material");
                    if(!applies&&packed!=(halo?0x895A75:0xFFFFFF))throw new AssertionError("unselected layer modified");
                }
                var json=JsonParser.parseString(LightningSkillSchema.exportRecipe()).getAsJsonObject();
                if(json.size()!=63||!json.get("gradientTarget").getAsString().equals(target)||!json.get("gradientCoreMode").getAsString().equals(mode))throw new AssertionError("new enum export");
            }
            r.gradientEnabled=false;
            if(LightningAppearance.vividCore(r))throw new AssertionError("vivid changed nongradient core");
            var legacy61=new Gson().toJsonTree(r).getAsJsonObject();legacy61.remove("gradientTarget");legacy61.remove("gradientCoreMode");
            var parsed=LightningRecipe.parse(legacy61);
            if(!parsed.gradientTarget.equals("both")||!parsed.gradientCoreMode.equals("vivid"))throw new AssertionError("legacy 61-field defaults");
            for(String bad:List.of("{\"gradientTarget\":\"branch\"}","{\"gradientCoreMode\":\"invalid\"}","{\"gradientTarget\":null}","{\"gradientCoreMode\":true}","{\"restrikeCount\":1.5}")) {
                var before=state.snapshot();boolean rejected=false;
                try{LightningSkillSchema.importRecipe(bad);}catch(RuntimeException expected){rejected=true;}
                if(!rejected||!before.equals(state.snapshot()))throw new AssertionError("invalid new enum import mutated live state");
            }
            var targetSpec=state.schema.get("gradientTarget");
            var modeSpec=state.schema.get("gradientCoreMode");
            state.setBool("gradientEnabled",false);
            if(targetSpec.isVisible(state)||modeSpec.isVisible(state))throw new AssertionError("disabled controls visible");
            state.setBool("gradientEnabled",true);state.set("gradientTarget","glow");
            if(!targetSpec.isVisible(state)||modeSpec.isVisible(state))throw new AssertionError("core mode visible for halo only");
            for(String selected:List.of("orbit","strike","rain")){
                state.set("mode",selected);
                for(String key:List.of("currentWidth","orbitAnimation","strands","samples"))
                    if(state.schema.get(key).isVisible(state)!=selected.equals("orbit"))throw new AssertionError("mode leaked: "+key);
                for(String key:List.of("strikeWidth","strikeHeight","branchAngle"))
                    if(state.schema.get(key).isVisible(state)==selected.equals("orbit"))throw new AssertionError("mode leaked: "+key);
                for(String key:List.of("rainCount","rainMaxBranches","rainStyle"))
                    if(state.schema.get(key).isVisible(state)!=selected.equals("rain"))throw new AssertionError("rain leaked: "+key);
                var groups=state.schema.params.stream().filter(p->p.isVisible(state)).map(p->p.group).distinct().toList();
                if(groups.indexOf("粗细")>=groups.indexOf("颜色与亮度"))throw new AssertionError("width/color order");
                if(!selected.equals("orbit")&&groups.indexOf("颜色与亮度")>=groups.indexOf("分叉"))throw new AssertionError("color/branch order");
            }
            state.set("preset","tribulation");var trib=LightningSkillSchema.recipe();
            var tree=BoltGeometry.generate(new net.minecraft.world.phys.Vec3(0,trib.strikeHeight,0),net.minecraft.world.phys.Vec3.ZERO,trib);
            long paths=tree.segments().stream().filter(segment->segment.depth()>0).map(BoltGeometry.Segment::mask).distinct().count();
            if(trib.strikeWidth>.025||trib.strikeHeight<80||paths<20||tree.segments().stream().noneMatch(segment->segment.depth()==1&&segment.alongA()>.7))throw new AssertionError("tribulation must be thin, tall and branched along the trunk: "+paths);
            System.out.println("PASS: thin 96-block tribulation, "+paths+" branching paths distributed along the trunk");
            try {
                var dir=java.nio.file.Files.createTempDirectory("lightning-preset-check-");
                var file=dev.vfxworkbench.core.PresetManager.presetDir(dir,LightningSkillSchema.ID).resolve("saved.json");
                state.set("preset","tribulation");state.set("strikeWidth","0.031");state.set("gradientRatio","23");
                String expected=LightningSkillSchema.exportRecipe();
                dev.vfxworkbench.core.PresetManager.save(dir,LightningSkillSchema.ID,"saved",state);
                state.resetToDefaults();dev.vfxworkbench.core.PresetManager.load(dir,LightningSkillSchema.ID,"saved",state);
                if(!expected.equals(LightningSkillSchema.exportRecipe()))throw new AssertionError("saved custom values overwritten by preset listener");
                var before=state.snapshot();var broken=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
                broken.getAsJsonObject("values").addProperty("strikeWidth","invalid");java.nio.file.Files.writeString(file,broken.toString());
                boolean rejected=false;try{dev.vfxworkbench.core.PresetManager.load(dir,LightningSkillSchema.ID,"saved",state);}catch(RuntimeException failure){rejected=true;}
                if(!rejected||!before.equals(state.snapshot()))throw new AssertionError("invalid saved preset partially mutated state");
                java.nio.file.Files.delete(file);java.nio.file.Files.delete(file.getParent());java.nio.file.Files.delete(file.getParent().getParent());java.nio.file.Files.delete(file.getParent().getParent().getParent());java.nio.file.Files.delete(dir.resolve("config"));java.nio.file.Files.delete(dir);
            }catch(java.io.IOException failure){throw new AssertionError("saved preset regression",failure);}
            System.out.println("PASS: gradient layer isolation, core blend selection, shared actual UI material swatches, old 61-field defaults and atomic 63-field enum validation");
        } finally { state.load(saved); }
    }
}
