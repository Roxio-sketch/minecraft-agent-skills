package dev.vfxworkbench.effects.lightning;

import dev.vfxworkbench.core.ParamRegistry;
import net.minecraft.world.phys.Vec3;
import com.google.gson.JsonParser;

final class PlacementCheck {
    static void run() {
        var state=ParamRegistry.state(LightningSkillSchema.ID);
        var saved=state.snapshot();
        try {
            Vec3 feet=new Vec3(15,72,-21);
            for(int yaw=-720;yaw<=720;yaw+=15)for(double distance:new double[]{1,90,128}) {
                Vec3 target=LightningPlacement.forward(feet,yaw,distance);
                if(Math.abs(target.distanceTo(feet)-distance)>1e-9||target.y!=feet.y)
                    throw new AssertionError("horizontal trigger distance");
            }
            state.set("preset","custom");state.set("preset","tribulation");
            if(!state.get("triggerPlacement").equals("forward")||state.getDouble("triggerDistance")!=90)
                throw new AssertionError("tribulation placement");
            if(JsonParser.parseString(LightningSkillSchema.exportRecipe()).getAsJsonObject().size()!=63)
                throw new AssertionError("host placement leaked into skill contract");
            var input=JsonParser.parseString(LightningSkillSchema.exportRecipe()).getAsJsonObject();
            input.addProperty("triggerPlacement","self");input.addProperty("triggerDistance",45);
            LightningSkillSchema.importRecipe(input);
            if(!state.get("triggerPlacement").equals("self")||state.getDouble("triggerDistance")!=45)
                throw new AssertionError("placement restore");
            for(double invalid:new double[]{0,129,Double.NaN,Double.POSITIVE_INFINITY}) {
                input.addProperty("triggerDistance",invalid);
                var before=state.snapshot();boolean rejected=false;
                try{LightningSkillSchema.importRecipe(input);}catch(RuntimeException ex){rejected=true;}
                if(!rejected||!before.equals(state.snapshot()))throw new AssertionError("invalid placement mutated state");
            }
            state.set("mode","orbit");
            if(state.schema.get("triggerPlacement").isVisible(state)||state.schema.get("triggerDistance").isVisible(state))
                throw new AssertionError("orbit placement visibility");
            state.set("mode","strike");state.set("triggerPlacement","self");
            if(state.schema.get("triggerDistance").isVisible(state))throw new AssertionError("self distance visibility");
            state.set("triggerHeightMode","absoluteWorld");state.set("triggerStartY","300");
            if(state.schema.get("strikeHeight").isVisible(state)||!state.schema.get("triggerStartY").isVisible(state))
                throw new AssertionError("absolute/relative height visibility");
            var rainRecipe=new LightningRecipe();rainRecipe.mode="rain";
            for(double groundY:new double[]{10,72,200}) {
                var rain=RainGeometry.generate(rainRecipe,new Vec3(0,groundY,0),(start,end)->end,300);
                if(rain.isEmpty()||rain.stream().anyMatch(b->Math.abs(b.geometry().segments().get(0).a().y-300)>1e-9))
                    throw new AssertionError("absolute start drifted with anchor altitude");
            }
            if(!RainGeometry.generate(rainRecipe,new Vec3(0,300,0),(start,end)->end,200).isEmpty())
                throw new AssertionError("below-ground start generated inverted rain");
            System.out.println("PASS: trigger placement, 291 yaw/distance cases, validation, host/skill separation and visibility");
        } finally {state.loadAtomic(saved);}
    }
}
