package dev.shieldfx;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Arrays;
import java.util.List;
import dev.shieldfx.ShieldGeometry.*;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid="shieldfx",value=Dist.CLIENT)
public final class ShieldClient {
    private static Vec3 origin;private static Object level;private static int ticks,slot;private static ShieldRecipe recipe=new ShieldRecipe();private static boolean active;
    private static final ShieldLifecycle lifecycle=new ShieldLifecycle();
    private static final ShieldSparks sparks=new ShieldSparks();
    private static final V[] hits=new V[6];private static final float[] hitAge=new float[6];
    private static List<Triangle> mesh=List.of();private static List<Patch> fragments=List.of();
    static {resetImpacts();}
    private static void resetImpacts(){Arrays.fill(hits,new V(0,0,2));Arrays.fill(hitAge,99);slot=0;fragments=List.of();sparks.clear();}
    public static void create(Vec3 center){origin=center;active=true;ticks=0;level=Minecraft.getInstance().level;lifecycle.reset(recipe.life);resetImpacts();mesh=ShieldGeometry.mesh(recipe);}
    public static void clear(){active=false;origin=null;mesh=List.of();resetImpacts();lifecycle.reset(recipe.life);}
    /** Client-only VFX hook; receiving real damage remains the host mod's responsibility. */
    public static void hitAt(float u,float v){
        if(!active)return;u=Math.max(0,Math.min(1,u));v=Math.max(0,Math.min(1,v));
        // Center UV for a complete sphere otherwise lies on the far side: use the +Z face.
        if(recipe.shape.equals("sphere")&&Math.abs(u-.5f)<1e-6)u=.25f;
        Vertex surface=ShieldGeometry.surface(recipe.shape,recipe.depth,u,v);V point=surface.pos();
        ShieldLifecycle.State previous=lifecycle.state();if(!lifecycle.hit(recipe.damage,recipe.shatterDuration))return;
        hits[slot]=point;hitAge[slot]=0;slot=(slot+1)%6;
        sparks.spawn(point,surface.normal(),recipe,ticks/20.0,lifecycle.state()==ShieldLifecycle.State.SHATTERING);
        if(previous==ShieldLifecycle.State.INTACT&&lifecycle.state()==ShieldLifecycle.State.SHATTERING){
            List<Triangle> frozen=ShieldGeometry.freezeSurface(mesh,recipe,ticks/20.0);
            fragments=ShieldGeometry.fracture(frozen,point,recipe,20261012L+ticks);
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
        e.getDispatcher().register(literal("shieldfx").then(literal("demo").executes(c->{var p=Minecraft.getInstance().player;if(p==null||!reload())return 0;create(p.position().add(0,1.2,0));return 1;})).then(literal("reload").executes(c->reload()?1:0)).then(literal("off").executes(c->{clear();return 1;})).then(literal("hit").executes(c->{hitAt(.5f,.5f);return 1;})));
    }
    private static boolean reload(){
        try {ShieldRecipe next=ShieldRecipe.read(Minecraft.getInstance().gameDirectory.toPath().resolve("config/shieldfx/recipe.json"));recipe=next;if(active){Vec3 position=origin;create(position);}return true;}
        catch(Exception ex){var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal("ShieldFX 配置错误: "+ex.getMessage()),false);return false;}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();if(mc.level!=level){clear();level=mc.level;}
        if(active&&!mc.isPaused()){ticks++;for(int i=0;i<6;i++)if(hitAge[i]<99)hitAge[i]+=1f/20f;lifecycle.tick(1.0/20);sparks.tick(ticks/20.0);
            if(lifecycle.state()==ShieldLifecycle.State.BROKEN){fragments=List.of();Arrays.fill(hitAge,99);}}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){if(active&&e.getStage()==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS){float partial=Minecraft.getInstance().isPaused()?0:e.getPartialTick();ShieldRender.draw(recipe,origin,e.getPoseStack(),e.getCamera().getPosition(),ticks/20f+partial/20f,lifecycle,mesh,fragments,hits,hitAge,sparks.particles(),partial);}}
}
