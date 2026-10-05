package dev.vfxworkbench.effects.blackhole;
import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.client.VfxWorkbenchMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BlackHoleRender {
    static ShaderInstance world;
    static int generation;
    static float frozenTime=-1;
    private static final BlackHoleClock clock=new BlackHoleClock();
    static void resetClock(){clock.reset();}
    @SubscribeEvent public static void register(RegisterShadersEvent e)throws IOException{
        e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(VfxWorkbenchMod.MODID,"bh_world"),DefaultVertexFormat.POSITION_TEX),s->{world=s;generation++;BlackHoleWorld.release();System.out.println("[VFXWORKBENCH] black-hole world shader ready");});
        e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(VfxWorkbenchMod.MODID,"bh_particles"),DefaultVertexFormat.POSITION_TEX),s->{BlackHoleParticles.shader=s;BlackHoleParticles.release();System.out.println("[VFXWORKBENCH] black-hole particle shader ready");});
    }
    static float linear(int b){float c=b/255f;return c<=.04045f?c/12.92f:(float)Math.pow((c+.055f)/1.055f,2.4);}
    static void color(ShaderInstance s,String n,int rgb){s.safeGetUniform(n).set(linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255));}
    static float[] color(int rgb){return new float[]{linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255)};}
    static void material(ShaderInstance s,int rgb){
        var p=ParamRegistry.state(BlackHoleSkillSchema.ID);var mc=Minecraft.getInstance();
        double now=mc.level==null?0:(mc.level.getGameTime()+mc.getFrameTime())/20.0;
        float t=frozenTime>=0?frozenTime/20f:(float)clock.sample(p.getDouble("time"),p.getBool("playing"),now);
        s.safeGetUniform("uTime").set(t);s.safeGetUniform("uCore").set(p.getFloat("core"));
        s.safeGetUniform("uInner").set(BlackHoleSkillSchema.inner(p));s.safeGetUniform("uOuter").set(BlackHoleSkillSchema.outer(p));
        for(String key: new String[]{"thickness","tilt","bend","density","speed","seed"})s.safeGetUniform("u"+Character.toUpperCase(key.charAt(0))+key.substring(1)).set(p.getFloat(key));
        s.safeGetUniform("uSteps").set(p.getInt("steps"));
        color(s,"uHot",p.getColor("hot"));color(s,"uCool",p.getColor("cool"));color(s,"uCoreColor",rgb);
        s.safeGetUniform("uDiskMid").set(color(p.getColor("diskMid")));
        // Halo and jet fields only exist in the world compositor; the particle
        // shader never declares them, so sending them there only produced
        // "could not find uniform" warnings every frame.
        if(s==world){
            s.safeGetUniform("uExposure").set(p.getFloat("exposure"));
            for(String key:new String[]{"beaming","diskGradientPosition","coreGradientStrength","bandDetail","dustScale","dustFill","filament","spiral","haloRadius","haloWidth","haloSharpness","haloGradientMix","haloDiskGradientMix","haloAuraGradientMix","jetStrength","jetLength","jetWidth"}){
                String n=Character.toUpperCase(key.charAt(0))+key.substring(1);s.safeGetUniform(key.startsWith("jet")?n:"u"+n).set(p.getFloat(key));
            }
            color(s,"uCoreEdgeColor",p.getColor("coreEdgeColor"));
            for(String key:new String[]{"haloGradientStart","haloGradientEnd","haloDiskInnerColor","haloDiskOuterColor","haloAuraInnerColor","haloAuraOuterColor","jetBaseColor","jetTipColor"})color(s,"u"+Character.toUpperCase(key.charAt(0))+key.substring(1),p.getColor(key));
        }
        s.safeGetUniform("uGranular").set(switch(p.get("diskStyle")){case "granular"->1;case "ribbons"->2;case "dust"->3;case "cinematic"->4;default->0;});
        double elapsed=frozenTime>=0?frozenTime/20.0:clock.sample(p.getDouble("time"),p.getBool("playing"),now);
        double shear=elapsed*Math.abs(p.getDouble("speed"));
        s.safeGetUniform("uOrbitPhase").set(phase(elapsed*p.getDouble("speed"),Math.PI*2));
        s.safeGetUniform("uRadialPhase").set(phase(elapsed*p.getDouble("infallSpeed"),128));
        s.safeGetUniform("uClassicRadialPhase").set(phase(elapsed*p.getDouble("infallSpeed"),Math.PI*2));
        s.safeGetUniform("uShearLocal").set(phase(shear,8));s.safeGetUniform("uShearParity").set((float)(Math.floor(shear/8)%2));
        s.safeGetUniform("uDustDriftA").set(phase(elapsed*p.getDouble("speed")*.35,12));s.safeGetUniform("uDustDriftB").set(phase(-elapsed*p.getDouble("speed")*.25,12));
    }
    private static float phase(double value,double period){return (float)(((value%period)+period)%period);}
}
