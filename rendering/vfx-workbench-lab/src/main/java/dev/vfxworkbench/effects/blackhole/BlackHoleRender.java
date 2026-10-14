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
    private static final float[] LINEAR_COLOR=linearTable();
    private static float[] linearTable(){
        float[] values=new float[256];
        for(int b=0;b<256;b++){float c=b/255f;values[b]=c<=.04045f?c/12.92f:(float)Math.pow((c+.055f)/1.055f,2.4);}
        return values;
    }
    private static final String[] MATERIAL_FIELDS_0={"thickness","tilt","bend","density","speed","seed"};
    private static final String[] MATERIAL_FIELDS_1={"beaming","diskGradientPosition","coreGradientStrength","bandDetail","dustScale","dustFill","filament","spiral","haloRadius","haloWidth","haloSharpness","haloGradientMix","haloDiskGradientMix","haloAuraGradientMix","jetStrength","jetLength","jetWidth"};
    private static final String[] MATERIAL_FIELDS_2={"shellStrength","shellRadius","shellWidth"};
    private static final String[] MATERIAL_FIELDS_3={"haloGradientStart","haloGradientEnd","haloDiskInnerColor","haloDiskOuterColor","haloAuraInnerColor","haloAuraOuterColor","jetBaseColor","jetTipColor"};
    private static final java.util.Map<String,String> UNIFORM_NAMES=new java.util.HashMap<>();
    static {
        for(String key:MATERIAL_FIELDS_0)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
        for(String key:MATERIAL_FIELDS_1)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
        for(String key:MATERIAL_FIELDS_2)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
        for(String key:MATERIAL_FIELDS_3)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
    }
    private static final ResourceLocation RIBBON_NOISE=new ResourceLocation(VfxWorkbenchMod.MODID,"textures/effect/ribbon-noise.png");
    private static net.minecraft.client.renderer.texture.AbstractTexture configuredNoise;
    private static int configuredNoiseId=-1;
    static ShaderInstance world;
    static int generation;
    static float frozenTime=-1;
    private static final BlackHoleClock clock=new BlackHoleClock();
    private static final BlackHoleFlowClock whiteOrbit=new BlackHoleFlowClock(),whiteRadial=new BlackHoleFlowClock(),whiteParticle=new BlackHoleFlowClock(),whiteJet=new BlackHoleFlowClock(),whiteShell=new BlackHoleFlowClock();
    private static boolean lastWhite;
    static void resetClock(){clock.reset();whiteOrbit.reset();whiteRadial.reset();whiteParticle.reset();whiteJet.reset();whiteShell.reset();}
    @SubscribeEvent public static void register(RegisterShadersEvent e)throws IOException{
        e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(VfxWorkbenchMod.MODID,"bh_world"),DefaultVertexFormat.POSITION_TEX),s->{world=s;configuredNoise=null;configuredNoiseId=-1;generation++;BlackHoleWorld.release();System.out.println("[VFXWORKBENCH] black-hole world shader ready");});
        e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(VfxWorkbenchMod.MODID,"bh_particles"),DefaultVertexFormat.POSITION_TEX),s->{BlackHoleParticles.shader=s;BlackHoleParticles.release();System.out.println("[VFXWORKBENCH] black-hole particle shader ready");});
        e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(VfxWorkbenchMod.MODID,"bh_core_blur"),DefaultVertexFormat.POSITION_TEX),s->{BlackHoleCoreBloom.blur=s;BlackHoleCoreBloom.release();});
        e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(VfxWorkbenchMod.MODID,"bh_core_composite"),DefaultVertexFormat.POSITION_TEX),s->{BlackHoleCoreBloom.composite=s;BlackHoleCoreBloom.release();});
    }
    static float linear(int b){return LINEAR_COLOR[b];}
    static void color(ShaderInstance s,String n,int rgb){s.safeGetUniform(n).set(linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255));}
    static float[] color(int rgb){return new float[]{linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255)};}
    static void material(ShaderInstance s,int rgb){
        var p=ParamRegistry.state(BlackHoleSkillSchema.ID);var mc=Minecraft.getInstance();
        var noise=mc.getTextureManager().getTexture(RIBBON_NOISE);
        int noiseId=noise.getId();
        // This dedicated texture owns its sampler state. Reinitialize after
        // shader reload, texture replacement or GL object recreation.
        if(noise!=configuredNoise||noiseId!=configuredNoiseId){
            noise.setFilter(true,false);
            int previousTexture=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            try{
                com.mojang.blaze3d.platform.GlStateManager._bindTexture(noiseId);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL11.GL_REPEAT);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL11.GL_REPEAT);
            }finally{com.mojang.blaze3d.platform.GlStateManager._bindTexture(previousTexture);}
            configuredNoise=noise;configuredNoiseId=noiseId;
        }
        s.setSampler("RibbonNoise",noiseId);
        double now=mc.level==null?0:(mc.level.getGameTime()+mc.getFrameTime())/20.0;
        double elapsed=frozenTime>=0?frozenTime/20.0:clock.sample(p.getDouble("time"),p.getBool("playing"),now);
        float t=(float)elapsed;
        s.safeGetUniform("uTime").set(t);s.safeGetUniform("uCore").set(p.getFloat("core"));
        boolean white=p.get("objectMode").equals("white");
        s.safeGetUniform("uObjectMode").set(white?1f:0f);
        s.safeGetUniform("uInner").set(BlackHoleSkillSchema.inner(p));s.safeGetUniform("uOuter").set(BlackHoleSkillSchema.outer(p));
        for(String key: MATERIAL_FIELDS_0)s.safeGetUniform(UNIFORM_NAMES.get(key)).set(p.getFloat(key));
        // Dust also participates in particle occlusion and must use the same field.
        if(s!=world){s.safeGetUniform("uDustScale").set(p.getFloat("dustScale"));s.safeGetUniform("uDustFill").set(p.getFloat("dustFill"));}
        s.safeGetUniform("uSteps").set(p.getInt("steps"));
        color(s,"uHot",p.getColor("hot"));color(s,"uCool",p.getColor("cool"));color(s,"uCoreColor",rgb);
        color(s,"uDiskMid",p.getColor("diskMid"));
        // Halo and jet fields only exist in the world compositor; the particle
        // shader never declares them, so sending them there only produced
        // "could not find uniform" warnings every frame.
        if(s==world){
            s.safeGetUniform("uCoreEmission").set(p.getFloat("coreEmission"));
            s.safeGetUniform("uExposure").set(p.getFloat("exposure"));
            for(String key:MATERIAL_FIELDS_1){
                s.safeGetUniform(UNIFORM_NAMES.get(key)).set(p.getFloat(key));
            }
            color(s,"uCoreEdgeColor",p.getColor("coreEdgeColor"));
            s.safeGetUniform("uCoreGlow").set(p.getFloat("coreGlow"));s.safeGetUniform("uCoreGlowRadius").set(p.getFloat("coreGlowRadius"));
            for(String key:MATERIAL_FIELDS_2)s.safeGetUniform(UNIFORM_NAMES.get(key)).set(key.equals("shellStrength")&&!p.getBool("shellEnabled")?0f:p.getFloat(key));
            color(s,"uShellColor",p.getColor("shellColor"));
            for(String key:MATERIAL_FIELDS_3)color(s,UNIFORM_NAMES.get(key),p.getColor(key));
        }
        s.safeGetUniform("uGranular").set(switch(p.get("diskStyle")){case "granular"->1;case "ribbons"->2;case "dust"->3;case "cinematic"->4;default->0;});
        if(white!=lastWhite){whiteOrbit.reset();whiteRadial.reset();whiteParticle.reset();whiteJet.reset();whiteShell.reset();lastWhite=white;}
        double orbit=elapsed*p.getDouble("speed"),radial=elapsed*p.getDouble("infallSpeed"),particle=elapsed*p.getDouble("particleSpeed");
        double jet=elapsed*p.getDouble("jetSpeed"),shell=elapsed*p.getDouble("shellSpeed");
        if(white&&frozenTime<0){
            orbit=whiteOrbit.sample(elapsed,p.getDouble("speed"),p.getDouble("time"));
            radial=whiteRadial.sample(elapsed,p.getDouble("infallSpeed"),p.getDouble("time"));
            particle=whiteParticle.sample(elapsed,p.getDouble("particleSpeed"),p.getDouble("time"));
            jet=whiteJet.sample(elapsed,p.getDouble("jetSpeed"),p.getDouble("time"));
            shell=whiteShell.sample(elapsed,p.getDouble("shellSpeed"),p.getDouble("time"));
        }
        if(s==world){s.safeGetUniform("uJetClock").set((float)jet);s.safeGetUniform("uShellClock").set((float)shell);}
        if(s==BlackHoleParticles.shader)s.safeGetUniform("uParticleClock").set((float)particle);
        double shear=Math.abs(orbit);
        s.safeGetUniform("uOrbitPhase").set(phase(orbit,Math.PI*2));
        float direction=white?-1f:1f;
        s.safeGetUniform("uRadialPhase").set(direction*phase(radial,128));
        s.safeGetUniform("uClassicRadialPhase").set(direction*phase(radial,Math.PI*2));
        s.safeGetUniform("uShearLocal").set(phase(shear,8));s.safeGetUniform("uShearParity").set((float)(Math.floor(shear/8)%2));
        s.safeGetUniform("uDustDriftA").set(phase(orbit*.35,12));s.safeGetUniform("uDustDriftB").set(phase(-orbit*.25,12));
    }
    private static float phase(double value,double period){return (float)(((value%period)+period)%period);}
}
