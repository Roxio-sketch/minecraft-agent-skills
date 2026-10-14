package dev.lightninglab;

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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Mod.EventBusSubscriber(modid = LightningLab.MOD_ID, value = Dist.CLIENT)
public final class LightningClient {
    private static LightningRecipe recipe = new LightningRecipe();
    private static OrbitField orbit;
    private static BoltGeometry bolt;
    private static List<RainGeometry.Bolt> rain;
    private static Vec3 anchor;
    private static double time;
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
            .then(Commands.literal("mode").then(Commands.argument("value", StringArgumentType.word()).executes(c -> { String v=StringArgumentType.getString(c,"value"); if(!v.equals("orbit")&&!v.equals("strike")&&!v.equals("rain"))return 0; mode=v; return 1; }))));
    }
    private static boolean reload() {
        try { Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("config/lightninglab/recipe.json"); recipe = LightningRecipe.read(p); mode = recipe.mode; return true; }
        catch (Exception ex) { var player=Minecraft.getInstance().player; if(player!=null)player.displayClientMessage(Component.literal("闪电配置错误: "+ex.getMessage()),false); return false; }
    }
    private static void startOrbit() { var mc=Minecraft.getInstance(); if(mc.player==null)return; if(!reload())return; mode="orbit"; anchor=mc.player.position().add(0,1.0,0); orbit=new OrbitField(recipe); bolt=null; rain=null; time=0; level=mc.level; }
    private static void startStrike(Long seed) { var mc=Minecraft.getInstance(); if(mc.player==null)return; if(!reload())return; mode="strike"; if(seed!=null)recipe.seed=seed; anchor=mc.player.position(); Vec3 end=anchor; Vec3 start=end.add(0,recipe.strikeHeight,0); bolt=BoltGeometry.generate(start,end,recipe); orbit=null; rain=null; time=0; level=mc.level; mc.player.displayClientMessage(Component.literal("lightninglab seed="+recipe.seed), true); }
    private static void startRain(Long seed) { var mc=Minecraft.getInstance(); if(mc.player==null)return; if(!reload())return; mode="rain"; if(seed!=null)recipe.seed=seed; anchor=mc.player.position(); rain=RainGeometry.generate(recipe, anchor,(start,end)->{
            var hit=mc.level.clip(new ClipContext(start,new Vec3(end.x,Math.min(end.y-64,mc.level.getMinBuildHeight()),end.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            return hit.getType()==HitResult.Type.BLOCK?hit.getLocation():end;
        }); orbit=null; bolt=null; time=0; level=mc.level; mc.player.displayClientMessage(Component.literal("lightninglab rain seed="+recipe.seed), true); }
    private static void clear() { orbit=null; bolt=null; rain=null; anchor=null; time=0; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc=Minecraft.getInstance(); if(e.phase!=TickEvent.Phase.END)return;
        if(mc.level!=level){clear();level=mc.level;} if(mc.isPaused()||anchor==null)return;
        if(mode.equals("orbit")&&mc.player!=null)anchor=mc.player.position().add(0,1.0,0); time++;
        if(mode.equals("strike")&&bolt!=null&&new LightningEnvelope(recipe).finished(time)) bolt=null;
        if(mode.equals("rain")&&rain!=null&&RainGeometry.finished(rain,time)) rain=null;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||anchor==null)return;
        Vec3 camera=e.getCamera().getPosition();
        if(orbit!=null) LightningRender.drawOrbit(orbit.sample(anchor,time+e.getPartialTick()),recipe,e.getPoseStack(),camera);
        if(bolt!=null) LightningRender.drawBolt(bolt,recipe,time+e.getPartialTick(),e.getPoseStack(),camera);
        if(rain!=null) for(RainGeometry.Bolt b:rain) {
            double localTime=time+e.getPartialTick()-b.delay();
            LightningRender.drawBolt(b.geometry(),b.recipe(),localTime,e.getPoseStack(),camera);
            LightningRender.drawOrbit(b.impact().sample(localTime),b.recipe(),e.getPoseStack(),camera);
        }
    }
}
