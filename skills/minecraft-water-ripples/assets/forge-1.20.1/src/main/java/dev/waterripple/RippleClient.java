package dev.waterripple;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid="waterripple", value=Dist.CLIENT)
public final class RippleClient {
    private static WaveField field;
    private static Vec3 origin;
    private static Object level;
    private static int ticks;
    private static boolean rain;
    private static double rainAccumulator;
    private static RippleRecipe recipe=new RippleRecipe();
    /** Client thread only. Creates an 8x8 block visual water patch centered at this point. */
    public static void create(Vec3 center) { origin=center; field=new WaveField(recipe); ticks=0; rainAccumulator=0; level=Minecraft.getInstance().level; }
    public static void create(Vec3 center,RippleRecipe parameters) { recipe=parameters.validate();create(center); }
    /** Client thread only. Inject an impulse in world XZ coordinates; height in blocks. */
    public static void impulse(double worldX,double worldZ,double radius,double height) {
        if(field==null) return;
        double cells=(field.size-1)/recipe.width;
        field.drop((worldX-origin.x+recipe.width/2)*cells,(worldZ-origin.z+recipe.width/2)*cells,radius*cells,height);
    }
    public static void clear() { field=null; origin=null; rain=false; }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(literal("waterripple")
            .then(literal("demo").executes(c->{var p=Minecraft.getInstance().player; if(p==null)return 0;
                if(!reload())return 0;create(p.position().add(0,0.08,0)); impulse(origin.x,origin.z,recipe.radius,recipe.strength); return 1;}))
            .then(literal("drop").executes(c->{if(origin==null)return 0; impulse(origin.x,origin.z,recipe.radius,recipe.strength);return 1;}))
            .then(literal("reload").executes(c->{if(!reload())return 0;if(origin!=null)create(origin);return 1;}))
            .then(literal("rain").executes(c->{rain=!rain;return 1;}))
            .then(literal("off").executes(c->{clear();return 1;})));
    }
    private static boolean reload() {
        try {recipe=RippleRecipe.read(Minecraft.getInstance().gameDirectory.toPath().resolve("config/waterripple/recipe.json"));return true;}
        catch(Exception ex){var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal("波纹配置错误: "+ex.getMessage()),false);return false;}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc=Minecraft.getInstance();
        if(e.phase!=TickEvent.Phase.END)return;
        if(mc.level!=level) { clear();level=mc.level; }
        if(field==null||mc.isPaused())return;
        if(rain)rainAccumulator+=recipe.rainRate/20;
        if(rainAccumulator>=1) {rainAccumulator-=1;double a=ticks*2.399963;double r=recipe.width*0.34*Math.sqrt(((ticks*37)%101)/101.0);
            impulse(origin.x+Math.cos(a)*r,origin.z+Math.sin(a)*r,recipe.radius,recipe.strength);}
        field.tick();ticks++;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && field!=null)
            RippleRender.draw(field,origin,e.getPoseStack(),e.getCamera().getPosition(),e.getPartialTick());
    }
}
