package dev.coloredglint;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.*;

/** Finite recipe registry. Minecraft owns shader disposal/replacement during reload. */
@Mod.EventBusSubscriber(modid=ColoredGlint.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GlintShaders {
    private static final Map<String,Double> PERIODS=Map.of("rainbow_cycle",6.0,"ember_wave",4.0);
    private static final Map<String,ShaderInstance> SHADERS=new HashMap<>();
    private static ClientLevel clockLevel;
    private static double sampledTicks;
    public static final Map<String,Float> lastDrawSeconds=new TreeMap<>();

    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        for(String id:new TreeSet<>(PERIODS.keySet())) {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(ColoredGlint.ID,"glint_"+id),DefaultVertexFormat.POSITION_TEX),
                loaded->{
                    if(loaded.getUniform("EffectTimeSeconds")==null)
                        throw new IllegalStateException("Missing EffectTimeSeconds in "+id);
                    SHADERS.put(id,loaded);
                    System.out.println("[COLORED-GLINT] Loaded animated shader "+id);
                });
        }
    }
    public static boolean animated(String id){return PERIODS.containsKey(id);}
    public static boolean ready(String id){return SHADERS.containsKey(id);}
    public static Map<String,Integer> identities(){
        Map<String,Integer> ids=new TreeMap<>();
        SHADERS.forEach((id,shader)->ids.put(id,System.identityHashCode(shader)));
        return ids;
    }
    public static ShaderInstance atDraw(String id){
        ShaderInstance shader=Objects.requireNonNull(SHADERS.get(id),"Glint shader not loaded: "+id);
        Minecraft mc=Minecraft.getInstance();
        if(clockLevel!=mc.level){clockLevel=mc.level;sampledTicks=0;}
        if(mc.level!=null&&!mc.isPaused())sampledTicks=(double)mc.level.getGameTime()+mc.getFrameTime();
        // Reduce in double before conversion, including in long-lived worlds.
        float seconds=(float)((sampledTicks/20.0)%PERIODS.get(id));
        shader.getUniform("EffectTimeSeconds").set(seconds);
        if(GlintLayers.auditEnabled)lastDrawSeconds.put(id,seconds);
        return shader;
    }
}
