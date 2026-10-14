package dev.cosmicmaterial;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Original implementation based on the observed eight-corner geometry technique. */
@Mod.EventBusSubscriber(modid=CosmicMaterial.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class OutlineRender extends RenderType {
    private OutlineRender() { super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    static ShaderInstance shader;
    public static int generation;
    public static final Map<String,Long> draws = new HashMap<>();
    private static final Map<Style,RenderType> TYPES = new HashMap<>();

    public record Style(float width,int mode,int color,int color1,int color2,int color3) {
        static Style from(CompoundTag tag) {
            float width=tag.contains("Width",99)?tag.getFloat("Width"):.008f;
            if(!Float.isFinite(width))width=.008f;
            width=Math.max(.001f,Math.min(.06f,width));
            int mode=tag.contains("Type",99)?tag.getInt("Type"):0;
            if(mode!=1&&mode!=3&&mode!=4)mode=0;
            return new Style(width,mode,
                tag.contains("Color",99)?tag.getInt("Color"):0xff55e6ff,
                tag.contains("Color1",99)?tag.getInt("Color1"):0xff00b8e8,
                tag.contains("Color2",99)?tag.getInt("Color2"):0xffec427a,
                tag.contains("Color3",99)?tag.getInt("Color3"):0xffffd873);
        }
        float width(ItemDisplayContext context) {
            if(context==ItemDisplayContext.GUI)return width+.0125f;
            if(context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)return width;
            return width+.0035f;
        }
    }

    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        TYPES.clear();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            new ResourceLocation(CosmicMaterial.ID,"outline"),DefaultVertexFormat.NEW_ENTITY),s->{shader=s;generation++;});
    }
    static RenderType type(Style style) {
        return TYPES.computeIfAbsent(style,k->RenderType.create("cosmicmaterial_outline_"+Integer.toHexString(k.hashCode()),
            DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,true,false,
            CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(()->prepare(k)))
                .setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS,false,false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                .createCompositeState(true)));
    }
    private static void setColor(String uniform,int argb) {
        shader.safeGetUniform(uniform).set(((argb>>>16)&255)/255f,((argb>>>8)&255)/255f,
            (argb&255)/255f,((argb>>>24)&255)/255f);
    }
    private static ShaderInstance prepare(Style style) {
        Minecraft mc=Minecraft.getInstance();
        float time=CosmicRender.frozenTime>=0?CosmicRender.frozenTime:
            mc.level==null?0:(mc.level.getGameTime()%1000000L)+mc.getFrameTime();
        shader.safeGetUniform("time").set(time);
        shader.safeGetUniform("mode").set(style.mode);
        setColor("outlineColor",style.color);
        setColor("color1",style.color1);
        setColor("color2",style.color2);
        setColor("color3",style.color3);
        draws.merge(Integer.toString(style.mode),1L,Long::sum);
        return shader;
    }
}
