package dev.vfxworkbench.effects.blackhole;
import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.core.ParamRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.StringArgumentType;
import static net.minecraft.commands.Commands.*;
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID,value=Dist.CLIENT)
public final class BlackHoleClient {
    static Vec3 center;
    static int coreColor;
    static long worldDraws;
    static String failure="";
    private static Object owner;
    public static void preview(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;sync();
        Vec3 eye=mc.player.getEyePosition(),end=eye.add(mc.player.getLookAngle().scale(BlackHoleWorld.distance));
        var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        center=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
            ?hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(
                ParamRegistry.state(BlackHoleSkillSchema.ID).getDouble("core")*BlackHoleWorld.scale+.05))
            :end;
        owner=mc.level;failure="";
    }
    public static void stop(){center=null;BlackHoleWorld.release();BlackHoleRender.resetClock();}
    private static void sync(){
        var p=ParamRegistry.state(BlackHoleSkillSchema.ID);
        coreColor=p.getColor("coreColor");BlackHoleWorld.scale=p.getFloat("scale");BlackHoleWorld.distance=p.getFloat("distance");BlackHoleWorld.lens=p.getBool("lens")?p.getFloat("lensStrength"):0;
        BlackHoleWorld.rotation=p.getFloat("speed");BlackHoleWorld.infall=p.getFloat("infallSpeed");BlackHoleWorld.exposure=p.getFloat("exposure");BlackHoleWorld.tilt=p.getFloat("tilt");
        BlackHoleWorld.halo=p.getFloat("halo");BlackHoleWorld.haloRing=p.getFloat("haloRing");BlackHoleWorld.haloDisk=p.getFloat("haloDisk");BlackHoleWorld.haloAura=p.getFloat("haloAura");
        BlackHoleParticles.enabled=p.getBool("particles");BlackHoleParticles.count=p.getInt("particleCount");BlackHoleParticles.speed=p.getFloat("particleSpeed");BlackHoleParticles.size=p.getFloat("particleSize");BlackHoleParticles.outer=p.getFloat("particleOuter");BlackHoleParticles.brightness=p.getFloat("particleBrightness");
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(owner!=mc.level){stop();owner=mc.level;}}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS){BlackHoleWorld.captureEnvironment(e);return;}
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES){BlackHoleWorld.captureCamera(e);return;}
        var mc=Minecraft.getInstance();if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||center==null||owner!=mc.level)return;
        sync();BlackHoleWorld.render(e);
        if(!failure.isEmpty()&&mc.player!=null){mc.player.displayClientMessage(Component.literal("黑洞渲染已暂停："+failure),false);stop();}
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
        var root=literal("blackhole").then(literal("world").executes(c->{preview();return 1;})).then(literal("off").executes(c->{stop();return 1;}));
        root.then(literal("form").then(argument("name",StringArgumentType.word()).suggests((c,b)->{for(String f:BlackHoleSkillSchema.FORMS)b.suggest(f);return b.buildFuture();}).executes(c->{String f=StringArgumentType.getString(c,"name");if(!BlackHoleSkillSchema.FORMS.contains(f)){c.getSource().sendFailure(Component.literal("形态：granular / dust / cinematic / ribbons / classic / gold / ice / violet / lens / crimson"));return 0;}ParamRegistry.state(BlackHoleSkillSchema.ID).set("form",f);if(center==null)preview();return 1;})));
        e.getDispatcher().register(root);
    }
}
