package dev.vfxworkbench;

import dev.vfxworkbench.core.*;
import dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema;
import dev.vfxworkbench.effects.lightning.LightningSkillSchema;
import java.util.Map;

final class RandomSchemesCheck {
    static void run(){
        BlackHoleSkillSchema.register();LightningSkillSchema.register();
        int count=0;
        for(String id:new String[]{"black-hole","lightning","water-ripples"}){
            var state=ParamRegistry.state(id);
            for(int mode=0;mode<(id.equals("lightning")?3:id.equals("black-hole")?2:1);mode++){
                state.resetToDefaults();
                if(id.equals("black-hole"))state.set("objectMode",mode==0?"black":"white");
                if(id.equals("lightning"))state.set("mode",new String[]{"orbit","strike","rain"}[mode]);
                var original=state.snapshot();
                for(long seed=0;seed<100;seed++){
                    var rolled=RandomSchemes.generate(state,seed);
                    if(!original.equals(state.snapshot())||!rolled.equals(RandomSchemes.generate(state,seed)))throw new AssertionError("random generation mutates state or is not reproducible");
                    for(var spec:state.schema.params){
                        String value=rolled.get(spec.key);if(value==null)throw new AssertionError("missing random field");
                        if(spec.type==ParamType.FLOAT||spec.type==ParamType.INT){double n=Double.parseDouble(value);if(!Double.isFinite(n)||n<spec.min||n>spec.max||(spec.type==ParamType.INT&&n!=Math.rint(n)))throw new AssertionError("random range: "+spec.key+"="+value);}
                        else if(spec.type==ParamType.ENUM&&!spec.enumValues.contains(value))throw new AssertionError("random enum");
                        else if(spec.type==ParamType.COLOR&&!value.matches("#[a-fA-F0-9]{6}"))throw new AssertionError("random color");
                    }
                    for(String key:new String[]{"objectMode","mode","scale","distance","triggerPlacement","triggerDistance","triggerHeightMode","triggerStartY","lensAffectsPlayer","shellEnabled","steps","grid","maxSegments","branchLimit"})
                        if(original.containsKey(key)&&!original.get(key).equals(rolled.get(key)))throw new AssertionError("random changed host preference/budget: "+key);
                    if(id.equals("black-hole")){
                        BlackHoleSkillSchema.recipeParameters(rolled);
                        if(Double.parseDouble(rolled.get("inner"))+1e-8<Double.parseDouble(rolled.get("core"))+.02||Double.parseDouble(rolled.get("outer"))<Double.parseDouble(rolled.get("inner"))+.5)throw new AssertionError("random disk geometry");
                    }else if(id.equals("lightning")){
                        var wrapper=new com.google.gson.JsonObject();wrapper.add("values",new com.google.gson.Gson().toJsonTree(rolled));
                        LightningSkillSchema.importRecipe(wrapper);
                        if(!state.get("mode").equals(original.get("mode"))||state.getDouble("triggerDistance")!=Double.parseDouble(original.get("triggerDistance")))throw new AssertionError("random import changed mode/placement");
                        state.loadAtomic(original);
                    }
                    count++;
                }
            }
            state.resetToDefaults();
        }
        System.out.println("PASS: "+count+" reproducible random schemes, parameter/geometry contracts, actual lightning importer, preserved placement/mode/budgets");
    }
}
