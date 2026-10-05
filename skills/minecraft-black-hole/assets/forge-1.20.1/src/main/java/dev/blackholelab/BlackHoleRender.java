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
    }
    static float linear(int b){float c=b/255f;return c<=.04045f?c/12.92f:(float)Math.pow((c+.055f)/1.055f,2.4);}
    static void color(ShaderInstance s,String name,int rgb){s.safeGetUniform(name).set(linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255));}
    static void material(ShaderInstance s,int rgb){
        if(s==world||s==BlackHoleParticles.shader){worldMaterial(s,rgb);return;}
        Minecraft mc=Minecraft.getInstance();
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
    static void resetClock(){clock.reset();}
    static float[] color(int rgb){return new float[]{linear(rgb>>16&255),linear(rgb>>8&255),linear(rgb&255)};}
    private static final com.mojang.blaze3d.shaders.AbstractUniform NO_UNIFORM=new com.mojang.blaze3d.shaders.AbstractUniform();
    private static com.mojang.blaze3d.shaders.AbstractUniform optional(ShaderInstance s,String name){
        var uniform=s.getUniform(name);return uniform==null?NO_UNIFORM:uniform;
    }
    static void worldMaterial(ShaderInstance s,int rgb){
        var p=BlackHoleRecipe.state();var mc=Minecraft.getInstance();
        double now=mc.level==null?0:(mc.level.getGameTime()+mc.getFrameTime())/20.0;
        float t=frozenTime>=0?frozenTime/20f:(float)clock.sample(p.getDouble("time"),p.getBool("playing"),now);
        optional(s,"uTime").set(t);optional(s,"uCore").set(p.getFloat("core"));
        optional(s,"uInner").set(BlackHoleRecipe.inner(p));optional(s,"uOuter").set(BlackHoleRecipe.outer(p));
        for(String key: new String[]{"thickness","tilt","bend","density","speed","seed"})optional(s,"u"+Character.toUpperCase(key.charAt(0))+key.substring(1)).set(p.getFloat(key));
        optional(s,"uSteps").set(p.getInt("steps"));
        color(s,"uHot",p.getColor("hot"));color(s,"uCool",p.getColor("cool"));color(s,"uCoreColor",rgb);
        optional(s,"uDiskMid").set(color(p.getColor("diskMid")));
        // Halo and jet fields only exist in the world compositor; the particle
        // shader never declares them, so sending them there only produced
        // "could not find uniform" warnings every frame.
        if(s==world){
            optional(s,"uExposure").set(BlackHoleWorld.exposure);
            for(String key:new String[]{"beaming","diskGradientPosition","coreGradientStrength","bandDetail","dustScale","dustFill","filament","spiral","haloRadius","haloWidth","haloSharpness","haloGradientMix","haloDiskGradientMix","haloAuraGradientMix","jetStrength","jetLength","jetWidth"}){
                String n=Character.toUpperCase(key.charAt(0))+key.substring(1);optional(s,key.startsWith("jet")?n:"u"+n).set(p.getFloat(key));
            }
            color(s,"uCoreEdgeColor",p.getColor("coreEdgeColor"));
            for(String key:new String[]{"haloGradientStart","haloGradientEnd","haloDiskInnerColor","haloDiskOuterColor","haloAuraInnerColor","haloAuraOuterColor","jetBaseColor","jetTipColor"})color(s,"u"+Character.toUpperCase(key.charAt(0))+key.substring(1),p.getColor(key));
        }
        optional(s,"uGranular").set(switch(p.get("diskStyle")){case "granular"->1;case "ribbons"->2;case "dust"->3;case "cinematic"->4;default->0;});
        double elapsed=frozenTime>=0?frozenTime/20.0:clock.sample(p.getDouble("time"),p.getBool("playing"),now);
        double shear=elapsed*Math.abs((double)BlackHoleWorld.rotation);
        optional(s,"uOrbitPhase").set(phase(elapsed*(double)BlackHoleWorld.rotation,Math.PI*2));
        optional(s,"uRadialPhase").set(phase(elapsed*(double)BlackHoleWorld.infall,128));
        optional(s,"uClassicRadialPhase").set(phase(elapsed*(double)BlackHoleWorld.infall,Math.PI*2));
        optional(s,"uShearLocal").set(phase(shear,8));optional(s,"uShearParity").set((float)(Math.floor(shear/8)%2));
        optional(s,"uDustDriftA").set(phase(elapsed*(double)BlackHoleWorld.rotation*.35,12));optional(s,"uDustDriftB").set(phase(-elapsed*(double)BlackHoleWorld.rotation*.25,12));
    }
    private static float phase(double value,double period){return (float)(((value%period)+period)%period);}
}
