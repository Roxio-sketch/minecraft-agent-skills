package dev.cosmicmaterial;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.*;

@Mod.EventBusSubscriber(modid=CosmicMaterial.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CosmicRender extends RenderType {
    // Extend only to use protected vanilla render-state constants.
    private CosmicRender() { super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    static ShaderInstance shader;
    public static int generation;
    public static float frozenTime = -1;
    public static final Map<String,Long> contexts = new TreeMap<>();
    public static final Map<String,Long> draws = new TreeMap<>();
    private static final Map<Key,RenderType> TYPES = new HashMap<>();
    private static final float[] UVS = new float[40];
    private static final float[] BASE = new float[4], MASK = new float[4];
    private record Key(ItemDisplayContext context,int palette,boolean blade,FantasyPresets.Preset fantasy,
                       float u0,float v0,float u1,float v1,ResourceLocation texture,String surface) {}
    static void clearTypes() { TYPES.clear(); }
    private static ResourceLocation id(String path) { return new ResourceLocation(CosmicMaterial.ID,path); }
    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        FantasyPresets.reload(event.getResourceProvider());
        clearTypes();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),id("cosmic"),DefaultVertexFormat.NEW_ENTITY),
            instance -> {
                shader=instance; generation++;
                System.out.println("[COSMIC-MATERIAL] GPU shader loaded, generation=" + generation);
            });
    }
    private static void rect(TextureAtlasSprite sprite,float[] target,int offset) {
        target[offset]=sprite.getU0(); target[offset+1]=sprite.getV0();
        target[offset+2]=sprite.getU1(); target[offset+3]=sprite.getV1();
    }
    @SubscribeEvent public static void stitched(TextureStitchEvent.Post event) {
        if (!event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) return;
        for (int i=0;i<10;i++) rect(event.getAtlas().getSprite(id("cosmic/cosmic_"+i)),UVS,i*4);
        rect(event.getAtlas().getSprite(new ResourceLocation("minecraft","item/diamond_sword")),BASE,0);
        rect(event.getAtlas().getSprite(id("mask/blade")),MASK,0);
        System.out.println("[COSMIC-MATERIAL] Atlas refreshed; 10 sprites + blade mask");
    }
    static RenderType type(ItemDisplayContext context,int palette,boolean blade) {
        return type(context,palette,blade,null,null);
    }
    static RenderType type(ItemDisplayContext context,int palette,boolean blade,FantasyPresets.Preset fantasy,
                           TextureAtlasSprite sprite) {
        Key key=new Key(context,palette,blade,fantasy,sprite==null?0:sprite.getU0(),sprite==null?0:sprite.getV0(),
            sprite==null?1:sprite.getU1(),sprite==null?1:sprite.getV1(),InventoryMenu.BLOCK_ATLAS,"item");
        return TYPES.computeIfAbsent(key,CosmicRender::createType);
    }
    public static RenderType externalType(ResourceLocation texture,ItemDisplayContext context,int palette,String surface) {
        return TYPES.computeIfAbsent(new Key(context,palette,false,null,0,0,1,1,texture,surface),CosmicRender::createType);
    }
    private static RenderType createType(Key k) {
        return RenderType.create("cosmicmaterial_"+k.surface+"_"+k.context.name()+"_"+k.palette+"_"+k.blade,
            DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,true,false,
            CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(()->prepare(k)))
                .setTextureState(new RenderStateShard.TextureStateShard(k.texture,false,false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                .createCompositeState(true));
    }
    private static ShaderInstance prepare(Key key) {
        Minecraft mc=Minecraft.getInstance();
        boolean gui=key.context==ItemDisplayContext.GUI;
        float time=frozenTime>=0?frozenTime:
            mc.level==null?0:(mc.level.getGameTime()%1000000L)+mc.getFrameTime();
        shader.safeGetUniform("time").set(time);
        shader.safeGetUniform("yaw").set(gui?0:(float)Math.toRadians(mc.gameRenderer.getMainCamera().getYRot()));
        shader.safeGetUniform("pitch").set(gui?0:-(float)Math.toRadians(mc.gameRenderer.getMainCamera().getXRot()));
        shader.safeGetUniform("screenSize").set((float)mc.getWindow().getWidth(),(float)mc.getWindow().getHeight());
        shader.safeGetUniform("externalScale").set(gui?100f:1f);
        shader.safeGetUniform("opacity").set(1f);
        shader.safeGetUniform("cosmicuvs").set(UVS);
        shader.safeGetUniform("baseRect").set(BASE);
        shader.safeGetUniform("maskRect").set(MASK);
        shader.safeGetUniform("maskMode").set(key.blade?1:0);
        shader.safeGetUniform("shieldRimMode").set(key.surface.equals("shield_framed")?1:0);
        shader.safeGetUniform("paletteMode").set(key.palette);
        shader.setSampler("CosmicAtlas",mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS));
        shader.safeGetUniform("fmMode").set(-1);
        shader.safeGetUniform("fmGui").set(gui?1:0);
        if(key.fantasy!=null) {
            key.fantasy.bind(shader,time/20f);
            shader.safeGetUniform("fmSpriteRect").set(key.u0,key.v0,key.u1,key.v1);
        }
        draws.merge(key.surface+"/"+key.context.name()+"/"+key.palette+"/"+key.blade+
            (key.fantasy==null?"":"/"+key.fantasy.id()),1L,Long::sum);
        return shader;
    }
}
