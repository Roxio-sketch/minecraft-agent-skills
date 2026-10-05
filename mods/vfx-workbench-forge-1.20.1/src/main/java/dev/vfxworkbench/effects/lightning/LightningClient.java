package dev.vfxworkbench.effects.lightning;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Mod.EventBusSubscriber(modid = dev.vfxworkbench.client.VfxWorkbenchMod.MODID, value = Dist.CLIENT)
public final class LightningClient {
    public static void preview() {
        String selected=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).get("mode");
        if(selected.equals("orbit"))startOrbit();else if(selected.equals("rain"))startRain(null);else startStrike(null);
    }
    public static void stop() { clear(); }
    private static LightningRecipe recipe = new LightningRecipe();
    private static OrbitField orbit;
    private static BoltGeometry bolt;
    private static List<RainGeometry.Bolt> rain;
    private static Vec3 anchor;
    private static double time;
    private static double fixedTime = -1;
    private static Object level;
    private static String mode = "orbit";

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("lightninglab")
            .then(Commands.literal("orbit").executes(c -> { startOrbit(); return 1; }))
            .then(Commands.literal("strike").executes(c -> { startStrike(null); return 1; })
                .then(Commands.argument("seed", LongArgumentType.longArg(Integer.MIN_VALUE, Integer.MAX_VALUE)).executes(c -> { startStrike(LongArgumentType.getLong(c, "seed")); return 1; })))
            .then(Commands.literal("random-strike").executes(c -> { startStrike((long)ThreadLocalRandom.current().nextInt()); return 1; }))
            .then(Commands.literal("rain").executes(c -> { startRain(null); return 1; }))
            .then(Commands.literal("random-rain").executes(c -> { startRain((long)ThreadLocalRandom.current().nextInt()); return 1; }))
            .then(Commands.literal("off").executes(c -> { clear(); return 1; }))
            .then(Commands.literal("reload").executes(c -> { if (reload()) { if (mode.equals("orbit")) startOrbit(); else if (mode.equals("rain")) startRain(recipe.seed); else startStrike(recipe.seed); } return 1; }))
            .then(Commands.literal("reload-file").executes(c -> {
                var mc=Minecraft.getInstance();
                try {
                    LightningSkillSchema.importRecipe(LightningSkillSchema.skillRecipeFile(mc.gameDirectory.toPath()));
                    preview();
                    c.getSource().sendSuccess(()->Component.literal("已载入 config/lightninglab/recipe.json 并重新触发"),false);
                    return 1;
                } catch(Exception ex) {
                    c.getSource().sendFailure(Component.literal("闪电配方未载入："+ex.getMessage()));
                    return 0;
                }
            }))
            .then(Commands.literal("mode").then(Commands.argument("value", StringArgumentType.word()).executes(c -> { String v=StringArgumentType.getString(c,"value"); if(!v.equals("orbit")&&!v.equals("strike")&&!v.equals("rain"))return 0; mode=v; dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).set("mode",v); return 1; }))));
    }
    private static java.util.Map<String,String> applied;
    private static boolean reload() { recipe=LightningSkillSchema.recipe(); mode=recipe.mode; applied=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).snapshot(); return true; }
    private static void refresh() {
        var now=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).snapshot();
        if(now.equals(applied))return;
        boolean visualOnly=applied!=null; for(var key:now.keySet())if(!java.util.Set.of("coreColor","glowColor","coreGain","glowGain","gradientEnabled","gradientStart","gradientEnd").contains(key)&&!java.util.Objects.equals(now.get(key),applied==null?null:applied.get(key)))visualOnly=false; boolean active=orbit!=null||bolt!=null||rain!=null;
        boolean modeChanged=applied!=null&&(!java.util.Objects.equals(now.get("mode"),applied.get("mode"))||!java.util.Objects.equals(now.get("preset"),applied.get("preset"))); reload(); if(anchor==null||!active)return; if(modeChanged){preview();return;} if(visualOnly){if(rain!=null)for(var b:rain){b.recipe().coreColor=recipe.coreColor;b.recipe().glowColor=recipe.glowColor;b.recipe().coreGain=recipe.coreGain;b.recipe().glowGain=recipe.glowGain;b.recipe().gradientEnabled=recipe.gradientEnabled;b.recipe().gradientStart=recipe.gradientStart;b.recipe().gradientEnd=recipe.gradientEnd;}return;}
        if(mode.equals("orbit")){orbit=new OrbitField(recipe);bolt=null;rain=null;}
        else if(mode.equals("strike")){bolt=BoltGeometry.generate(anchor.add(0,recipe.strikeHeight,0),anchor,recipe);orbit=null;rain=null;}
        else {rain=RainGeometry.generate(recipe,anchor,(start,end)->{
          var mc=Minecraft.getInstance();var hit=mc.level.clip(new ClipContext(start,new Vec3(end.x,mc.level.getMinBuildHeight(),end.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));return hit.getType()==HitResult.Type.BLOCK?hit.getLocation():end;
        });orbit=null;bolt=null;}
    }
    private static void startOrbit() { var mc=Minecraft.getInstance(); if(mc.player==null)return; if(!reload())return; fixedTime=-1; mode="orbit"; recipe.mode=mode; dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).set("mode",mode); applied=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).snapshot(); anchor=mc.player.position().add(0,1.0,0); orbit=new OrbitField(recipe); bolt=null; rain=null; time=0; level=mc.level; }
    private static void startStrike(Long seed) { var mc=Minecraft.getInstance(); if(mc.player==null)return; if(!reload())return; fixedTime=-1; mode="strike"; recipe.mode=mode; dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).set("mode",mode); applied=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).snapshot(); if(seed!=null){recipe.seed=seed;dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).set("seed",seed.toString());applied=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).snapshot();} anchor=mc.player.position(); Vec3 end=anchor; Vec3 start=end.add(0,recipe.strikeHeight,0); bolt=BoltGeometry.generate(start,end,recipe); orbit=null; rain=null; time=0; level=mc.level; mc.player.displayClientMessage(Component.literal("lightninglab seed="+recipe.seed), true); }
    private static void startRain(Long seed) { var mc=Minecraft.getInstance(); if(mc.player==null)return; if(!reload())return; fixedTime=-1; mode="rain"; recipe.mode=mode; dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).set("mode",mode); applied=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).snapshot(); if(seed!=null){recipe.seed=seed;dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).set("seed",seed.toString());applied=dev.vfxworkbench.core.ParamRegistry.state(LightningSkillSchema.ID).snapshot();} anchor=mc.player.position(); rain=RainGeometry.generate(recipe, anchor,(start,end)->{
            var hit=mc.level.clip(new ClipContext(start,new Vec3(end.x,Math.min(end.y-64,mc.level.getMinBuildHeight()),end.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            return hit.getType()==HitResult.Type.BLOCK?hit.getLocation():end;
        }); orbit=null; bolt=null; time=0; level=mc.level; mc.player.displayClientMessage(Component.literal("lightninglab rain seed="+recipe.seed), true); }
    private static void clear() { orbit=null; bolt=null; rain=null; anchor=null; time=0; fixedTime=-1; LightningHalo.release(); }
    /** Development verifier entry point; ordinary commands keep their normal tick clock. */
    static void previewAt(Vec3 position, double elapsedTicks) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        reload();anchor=position;time=0;fixedTime=elapsedTicks;level=mc.level;
        orbit=null;bolt=null;rain=null;
        if(mode.equals("orbit"))orbit=new OrbitField(recipe);
        else if(mode.equals("strike"))bolt=BoltGeometry.generate(anchor.add(0,recipe.strikeHeight,0),anchor,recipe);
        else rain=RainGeometry.generate(recipe,anchor,(start,end)->{
            var hit=mc.level.clip(new ClipContext(start,new Vec3(end.x,mc.level.getMinBuildHeight(),end.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            return hit.getType()==HitResult.Type.BLOCK?hit.getLocation():end;
        });
    }
    static java.util.Map<String,Object> probeStats() {
        var out=new java.util.LinkedHashMap<String,Object>();
        double ticks=fixedTime>=0?fixedTime:time;
        int generated=0,visible=0,impact=0;
        if(orbit!=null)generated=visible=orbit.sample(anchor,ticks).size();
        if(bolt!=null){generated=bolt.segments().size();visible=visibleSegments(bolt,recipe,ticks);}
        if(rain!=null)for(var child:rain){generated+=child.geometry().segments().size();visible+=visibleSegments(child.geometry(),child.recipe(),ticks-child.delay());impact+=child.impact().sample(ticks-child.delay()).size();}
        out.put("mode",mode);out.put("elapsedTicks",ticks);out.put("anchor",anchor);
        out.put("haloGlError",LightningHalo.frameGlError);out.put("haloPaths",LightningHalo.framePaths);
        out.put("haloBodyQuads",LightningHalo.frameBodyQuads);out.put("haloRoundQuads",LightningHalo.frameRoundQuads);out.put("haloCpuMillis",LightningHalo.frameCpuMillis);
        out.put("generatedMainSegments",generated);out.put("visibleMainSegments",visible);out.put("visibleImpactSegments",impact);out.put("recipe",recipe);
        return out;
    }
    private static int visibleSegments(BoltGeometry geometry,LightningRecipe r,double ticks){
        var env=new LightningEnvelope(r);if(env.brightness(ticks)<=0)return 0;
        double progress=env.propagation(ticks);
        return (int)geometry.segments().stream().filter(s->s.alongA()<progress).count();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc=Minecraft.getInstance(); if(e.phase!=TickEvent.Phase.END)return;
        if(mc.level!=level){clear();level=mc.level;} if(mc.isPaused())return; refresh(); if(anchor==null)return;
        if(fixedTime>=0)return;
        if(mode.equals("orbit")&&mc.player!=null)anchor=mc.player.position().add(0,1.0,0); time++;
        if(mode.equals("strike")&&bolt!=null&&new LightningEnvelope(recipe).finished(time)) bolt=null;
        if(mode.equals("rain")&&rain!=null&&RainGeometry.finished(rain,time)) rain=null;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||anchor==null)return;
        LightningHalo.beginFrame();
        Vec3 camera=e.getCamera().getPosition();
        double elapsed=fixedTime>=0?fixedTime:time+e.getPartialTick();
        if(orbit!=null) LightningRender.drawOrbit(orbit.sample(anchor,elapsed),recipe,e.getPoseStack(),camera);
        if(bolt!=null) LightningRender.drawBolt(bolt,recipe,elapsed,e.getPoseStack(),camera);
        if(rain!=null) for(RainGeometry.Bolt b:rain) {
            double localTime=elapsed-b.delay();
            LightningRender.drawBolt(b.geometry(),b.recipe(),localTime,e.getPoseStack(),camera);
            LightningRender.drawOrbit(b.impact().sample(localTime),b.recipe(),e.getPoseStack(),camera);
        }
        LightningHalo.endFrame();
    }
}
