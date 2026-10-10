package dev.blackholelab;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.*;

@Mod.EventBusSubscriber(modid=BlackHoleLab.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BlackHoleRender extends RenderType {
    private BlackHoleRender(){super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    private static final String[] MATERIAL_FIELDS_0={"thickness","tilt","bend","density","speed","seed"};
    private static final String[] MATERIAL_FIELDS_1={"beaming","diskGradientPosition","coreGradientStrength","bandDetail","dustScale","dustFill","filament","spiral","haloRadius","haloWidth","haloSharpness","haloGradientMix","haloDiskGradientMix","haloAuraGradientMix","jetStrength","jetLength","jetWidth"};
    private static final String[] MATERIAL_FIELDS_2={"shellStrength","shellRadius","shellWidth","coreGlow","coreGlowRadius"};
    private static final String[] MATERIAL_FIELDS_3={"haloGradientStart","haloGradientEnd","haloDiskInnerColor","haloDiskOuterColor","haloAuraInnerColor","haloAuraOuterColor","jetBaseColor","jetTipColor"};
    private static final Map<String,String> UNIFORM_NAMES=new HashMap<>();
    static {
        for(String key:MATERIAL_FIELDS_0)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
        for(String key:MATERIAL_FIELDS_1)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
        for(String key:MATERIAL_FIELDS_2)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
        for(String key:MATERIAL_FIELDS_3)UNIFORM_NAMES.put(key,(key.startsWith("jet")?"":"u")+Character.toUpperCase(key.charAt(0))+key.substring(1));
    }
    static ShaderInstance shader, scene, world;
    public static int generation;
    public static float frozenTime=-1;
    public static final Map<String,Long> contexts=new TreeMap<>(),draws=new TreeMap<>();
    private record Key(ItemDisplayContext context,int rgb,boolean blade,ResourceLocation sprite){}
    private static final Map<Key,RenderType> TYPES=new LinkedHashMap<>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Key,RenderType> e){return size()>256;}
    };
    static void clearTypes(){TYPES.clear();}
    @SubscribeEvent public static void register(RegisterShadersEvent event)throws IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(BlackHoleLab.ID,"item"),DefaultVertexFormat.NEW_ENTITY),s->{shader=s;generation++;TYPES.clear();System.out.println("[BLACK-HOLE] Item shader generation="+generation);});
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(BlackHoleLab.ID,"scene"),DefaultVertexFormat.POSITION_TEX),s->{scene=s;System.out.println("[BLACK-HOLE] Scene shader ready");});
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(BlackHoleLab.ID,"world"),DefaultVertexFormat.POSITION_TEX),s->{world=s;BlackHoleWorld.release();System.out.println("[BLACK-HOLE] World lens shader ready");});
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(BlackHoleLab.ID,"particles"),DefaultVertexFormat.POSITION_TEX),s->{BlackHoleParticles.shader=s;BlackHoleParticles.release();System.out.println("[BLACK-HOLE] Infall particle shader ready");});
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(BlackHoleLab.ID,"core_blur"),DefaultVertexFormat.POSITION_TEX),s->{BlackHoleCoreBloom.blur=s;BlackHoleCoreBloom.release();});
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(BlackHoleLab.ID,"core_composite"),DefaultVertexFormat.POSITION_TEX),s->{BlackHoleCoreBloom.composite=s;BlackHoleCoreBloom.release();});
    }
    private static final float[] LINEAR_COLOR=linearTable();
    private static float[] linearTable(){float[] values=new float[256];for(int b=0;b<256;b++){float c=b/255f;values[b]=c<=.04045f?c/12.92f:(float)Math.pow((c+.055f)/1.055f,2.4);}return values;}
    static float linear(int b){return LINEAR_COLOR[b];}
    static void color(ShaderInstance s,String name,int rgb){s.safeGetUniform(name).set(linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255));}
    static void material(ShaderInstance s,int rgb){
        var mc=Minecraft.getInstance();
        var noise=mc.getTextureManager().getTexture(new ResourceLocation(BlackHoleLab.ID,"textures/effect/ribbon-noise.png"));
        noise.setFilter(true,false);
        int previousTexture=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(noise.getId());
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL11.GL_REPEAT);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL11.GL_REPEAT);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(previousTexture);s.setSampler("RibbonNoise",noise.getId());

        if(s==world||s==BlackHoleParticles.shader){worldMaterial(s,rgb);return;}
        float t=frozenTime>=0?frozenTime/20f:mc.level==null?0:((mc.level.getGameTime()%2000000L)+mc.getFrameTime())/20f;
        s.safeGetUniform("uTime").set(t);s.safeGetUniform("uCore").set(.58f);
        s.safeGetUniform("uInner").set(.94f);s.safeGetUniform("uOuter").set(2.9f);
        s.safeGetUniform("uThickness").set(.105f);s.safeGetUniform("uTilt").set(-12f);
        s.safeGetUniform("uBend").set(1.1f);s.safeGetUniform("uDensity").set(6f);
        s.safeGetUniform("uSpeed").set(.55f);s.safeGetUniform("uSeed").set(17f);
        s.safeGetUniform("uInfall").set(.22f);
        s.safeGetUniform("uSteps").set(160);s.safeGetUniform("uExposure").set(1.1f);
        color(s,"uHot",0xfff3c2);color(s,"uCool",0xf04a0e);color(s,"uCoreColor",rgb);
    }
    static RenderType type(ItemDisplayContext context,int rgb,boolean blade,ResourceLocation sprite){
        Key key=new Key(context,rgb&0xffffff,blade,sprite);
        return TYPES.computeIfAbsent(key,k->RenderType.create("blackhole_"+k.hashCode(),DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,true,false,
            CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(()->prepare(k)))
                .setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS,false,false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(true)));
    }
    private static ShaderInstance prepare(Key k){
        material(shader,k.rgb);
        TextureAtlasSprite sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(k.sprite);
        shader.safeGetUniform("uRect").set(sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1());
        shader.safeGetUniform("uMask").set(k.blade?1:0);
        draws.merge(k.context.name()+"/"+Integer.toHexString(k.rgb)+"/"+k.blade,1L,Long::sum);
        return shader;
    }
    private static final BlackHoleClock clock=new BlackHoleClock();
    private static final BlackHoleFlowClock whiteOrbit=new BlackHoleFlowClock(),whiteRadial=new BlackHoleFlowClock(),whiteParticle=new BlackHoleFlowClock(),whiteJet=new BlackHoleFlowClock(),whiteShell=new BlackHoleFlowClock();
    private static boolean lastWhite;
    static void resetClock(){clock.reset();whiteOrbit.reset();whiteRadial.reset();whiteParticle.reset();whiteJet.reset();whiteShell.reset();}
    static float[] color(int rgb){return new float[]{linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255)};}
    private static final com.mojang.blaze3d.shaders.AbstractUniform NO_UNIFORM=new com.mojang.blaze3d.shaders.AbstractUniform();
    private static com.mojang.blaze3d.shaders.AbstractUniform optional(ShaderInstance s,String name){
        var uniform=s.getUniform(name);return uniform==null?NO_UNIFORM:uniform;
    }
    static void worldMaterial(ShaderInstance s,int rgb){
        var p=BlackHoleRecipe.state();var mc=Minecraft.getInstance();
        double now=mc.level==null?0:(mc.level.getGameTime()+mc.getFrameTime())/20.0;
        double elapsed=frozenTime>=0?frozenTime/20.0:clock.sample(p.getDouble("time"),p.getBool("playing"),now);
        float t=(float)elapsed;
        optional(s,"uTime").set(t);optional(s,"uCore").set(p.getFloat("core"));
        boolean white=p.get("objectMode").equals("white");optional(s,"uObjectMode").set(white?1f:0f);
        optional(s,"uInner").set(BlackHoleRecipe.inner(p));optional(s,"uOuter").set(BlackHoleRecipe.outer(p));
        for(String key: MATERIAL_FIELDS_0)optional(s,UNIFORM_NAMES.get(key)).set(p.getFloat(key));
        optional(s,"uSteps").set(p.getInt("steps"));
        color(s,"uHot",p.getColor("hot"));color(s,"uCool",p.getColor("cool"));color(s,"uCoreColor",rgb);
        optional(s,"uDiskMid").set(color(p.getColor("diskMid")));
        // Halo and jet fields only exist in the world compositor; the particle
        // shader never declares them, so sending them there only produced
        // "could not find uniform" warnings every frame.
        if(s!=world){s.safeGetUniform("uDustScale").set(p.getFloat("dustScale"));s.safeGetUniform("uDustFill").set(p.getFloat("dustFill"));}
        if(s==world){
            optional(s,"uCoreEmission").set(p.getFloat("coreEmission"));
            optional(s,"uExposure").set(BlackHoleWorld.exposure);
            for(String key:MATERIAL_FIELDS_1){
                optional(s,UNIFORM_NAMES.get(key)).set(p.getFloat(key));
            }
            color(s,"uCoreEdgeColor",p.getColor("coreEdgeColor"));
            for(String key:MATERIAL_FIELDS_2)optional(s,UNIFORM_NAMES.get(key)).set(p.getFloat(key));
            color(s,"uShellColor",p.getColor("shellColor"));
            for(String key:MATERIAL_FIELDS_3)color(s,UNIFORM_NAMES.get(key),p.getColor(key));
        }
        optional(s,"uGranular").set(switch(p.get("diskStyle")){case "granular"->1;case "ribbons"->2;case "dust"->3;case "cinematic"->4;default->0;});
        if(white!=lastWhite){whiteOrbit.reset();whiteRadial.reset();whiteParticle.reset();whiteJet.reset();whiteShell.reset();lastWhite=white;}
        double orbit=elapsed*(double)BlackHoleWorld.rotation,radial=elapsed*(double)BlackHoleWorld.infall,particle=elapsed*(double)BlackHoleParticles.speed;
        double jet=elapsed*p.getDouble("jetSpeed"),shell=elapsed*p.getDouble("shellSpeed");
        if(white&&frozenTime<0){
            orbit=whiteOrbit.sample(elapsed,BlackHoleWorld.rotation,p.getDouble("time"));
            radial=whiteRadial.sample(elapsed,BlackHoleWorld.infall,p.getDouble("time"));
            particle=whiteParticle.sample(elapsed,BlackHoleParticles.speed,p.getDouble("time"));
            jet=whiteJet.sample(elapsed,p.getDouble("jetSpeed"),p.getDouble("time"));
            shell=whiteShell.sample(elapsed,p.getDouble("shellSpeed"),p.getDouble("time"));
        }
        if(s==world){optional(s,"uJetClock").set((float)jet);optional(s,"uShellClock").set((float)shell);}
        if(s==BlackHoleParticles.shader)optional(s,"uParticleClock").set((float)particle);
        double shear=Math.abs(orbit);
        optional(s,"uOrbitPhase").set(phase(orbit,Math.PI*2));
        float direction=white?-1f:1f;
        optional(s,"uRadialPhase").set(direction*phase(radial,128));
        optional(s,"uClassicRadialPhase").set(direction*phase(radial,Math.PI*2));
        optional(s,"uShearLocal").set(phase(shear,8));optional(s,"uShearParity").set((float)(Math.floor(shear/8)%2));
        optional(s,"uDustDriftA").set(phase(orbit*.35,12));optional(s,"uDustDriftB").set(phase(-orbit*.25,12));
    }
    private static float phase(double value,double period){return (float)(((value%period)+period)%period);}
}
