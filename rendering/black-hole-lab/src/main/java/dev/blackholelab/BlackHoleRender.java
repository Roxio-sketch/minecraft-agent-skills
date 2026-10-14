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
}
