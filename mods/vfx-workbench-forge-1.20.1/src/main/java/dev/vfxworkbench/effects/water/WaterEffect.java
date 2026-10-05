package dev.vfxworkbench.effects.water;

import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.core.ParamState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** System managing the active wave patch: creation, impulses, rain, tick, and render dispatch. */
@Mod.EventBusSubscriber(modid = VfxWorkbenchMod.MODID, value = Dist.CLIENT)
public final class WaterEffect {
    private WaterEffect() {}

    private static WaveField field;
    private static Vec3 origin;
    private static boolean rain;
    private static double rainAccum;
    private static int ticks;
    private static Level level;

    public static ParamState state() { return ParamRegistry.state(WaterSkillSchema.ID); }

    public static boolean isActive() { return field != null; }

    public static void create(Vec3 pos) {
        origin = pos; level = Minecraft.getInstance().level;
        field = new WaveField(state());
    }

    public static void impulse(double x, double z, double radius, double strength) {
        if (field != null) field.impulseWorld(x,z,radius,strength);
    }

    public static void impulseCenter() {
        if (field == null) return;
        double width = state().getDouble("width");
        double radius = state().getDouble("radius");
        double strength = state().getDouble("strength");
        impulse(width / 2, width / 2, radius, strength);
    }

    public static void clear() { field = null; origin = null; rain = false; rainAccum = 0; ticks = 0; }

    public static boolean reloadField() {
        if (origin == null) return false;
        try {
            field = new WaveField(state());
            return true;
        } catch (Exception ex) {
            var p = Minecraft.getInstance().player;
            if (p != null) p.displayClientMessage(Component.literal("Wave rebuild error: " + ex.getMessage()), false);
            return false;
        }
    }

    public static void toggleRain() { rain = !rain; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END) return;
        if (mc.level != level) { clear(); level = mc.level; }
        if (field == null || mc.isPaused()) return;
        if (rain) {
            double rate = state().getDouble("rainRate");
            rainAccum += rate / 20.0;
            if (rainAccum >= 1) {
                rainAccum -= 1;
                double width = state().getDouble("width");
                double a = ticks * 2.399963;
                double r = width * 0.34 * Math.sqrt(((ticks * 37) % 101) / 101.0);
                double rad = state().getDouble("radius");
                double str = state().getDouble("strength");
                impulse(width / 2 + Math.cos(a) * r, width / 2 + Math.sin(a) * r, rad, str);
            }
        }
        field.tick();
        ticks++;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && field != null)
            WaterRenderer.draw(field, origin, e.getPoseStack(), e.getCamera().getPosition(), e.getPartialTick());
    }
}
