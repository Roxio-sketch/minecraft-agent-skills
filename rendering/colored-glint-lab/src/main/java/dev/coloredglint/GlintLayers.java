package dev.coloredglint;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Stable recipe/mode identities; animated uniforms are sampled only at actual draw setup. */
public final class GlintLayers extends RenderType {
    private GlintLayers() { super("unused",DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    private record Mode(String name, RenderType origin, ShaderStateShard shader, TexturingStateShard texturing, boolean itemTarget) {}
    private static final List<Mode> MODES = List.of(
        new Mode("direct", RenderType.glintDirect(), RENDERTYPE_GLINT_DIRECT_SHADER, GLINT_TEXTURING, false),
        new Mode("normal", RenderType.glint(), RENDERTYPE_GLINT_SHADER, GLINT_TEXTURING, false),
        new Mode("translucent", RenderType.glintTranslucent(), RENDERTYPE_GLINT_TRANSLUCENT_SHADER, GLINT_TEXTURING, true),
        new Mode("entity", RenderType.entityGlint(), RENDERTYPE_ENTITY_GLINT_SHADER, ENTITY_GLINT_TEXTURING, true),
        new Mode("entity_direct", RenderType.entityGlintDirect(), RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER, ENTITY_GLINT_TEXTURING, false)
    );
    private static final Map<String, Map<RenderType, RenderType>> TYPES = new LinkedHashMap<>();
    public static final Map<String, Long> selections = new TreeMap<>();
    public static final Map<String, Long> draws = new TreeMap<>();
    public static boolean auditEnabled;
    static {
        for (String color : new TreeSet<>(GlintStyle.COLORS)) {
            Map<RenderType, RenderType> modes = new IdentityHashMap<>();
            for (Mode mode : MODES) {
                // The generated animated shader matches the direct contract only.
                // Other branches retain their original RenderType until separately adapted.
                if (GlintShaders.animated(color) && !mode.name().equals("direct")) continue;
                String label = color + "/" + mode.name();
                ShaderStateShard shader = GlintShaders.animated(color)
                    ? new ShaderStateShard(() -> GlintShaders.atDraw(color)) : mode.shader();
                var state = CompositeState.builder().setShaderState(shader)
                    .setTextureState(new TextureStateShard(new ResourceLocation(ColoredGlint.ID,"textures/misc/glint_item_"+color+".png"),true,false))
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setDepthTestState(EQUAL_DEPTH_TEST)
                    .setTransparencyState(GLINT_TRANSPARENCY).setTexturingState(mode.texturing())
                    .setLayeringState(new LayeringStateShard("glint_audit",()->{
                        if(auditEnabled)draws.merge(label,1L,Long::sum);
                    },()->{}));
                if (mode.itemTarget()) state.setOutputState(ITEM_ENTITY_TARGET);
                modes.put(mode.origin(),RenderType.create("coloredglint_"+color+"_"+mode.name(),
                    DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,256,false,false,state.createCompositeState(false)));
            }
            TYPES.put(color,modes);
        }
    }
    public static void registerBuffers(Map<RenderType,BufferBuilder> buffers) {
        for (Map<RenderType,RenderType> modes : TYPES.values())
            for (Mode mode : MODES) {
                RenderType type=modes.get(mode.origin());
                if(type==null)continue;
                buffers.computeIfAbsent(type,k->new BufferBuilder(k.bufferSize()));
            }
        System.out.println("[COLORED-GLINT] Registered "+TYPES.values().stream().mapToInt(Map::size).sum()+" fixed recipe/mode buffers");
    }
    public static MultiBufferSource forStack(MultiBufferSource source,ItemStack stack,ItemDisplayContext context) {
        GlintStyle style=GlintStyle.resolve(stack);
        if(GlintShaders.animated(style.color())&&!GlintShaders.ready(style.color()))return source;
        Map<RenderType,RenderType> modes=TYPES.get(style.color());
        if (modes==null) return source;
        // The closure holds immutable appearance selection; nested renders need no shared current-stack state.
        return type->{
            RenderType replacement=modes.get(type);
            if(replacement==null)return source.getBuffer(type);
            if(auditEnabled)selections.merge(context.name()+"/"+style.color(),1L,Long::sum);
            return source.getBuffer(replacement);
        };
    }
}
