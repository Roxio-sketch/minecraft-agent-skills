package dev.vfxworkbench.effects.water;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.core.ParamState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;

/** GPU mesh renderer. All visual uniforms are read from ParamState every draw,
 *  so dragging a slider in the workbench changes the visible water instantly. */
@Mod.EventBusSubscriber(modid = VfxWorkbenchMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WaterRenderer extends RenderType {
    private WaterRenderer() { super("unused", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}); }

    private static ShaderInstance shader;
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(1048576));
    private static final String NS = "vfxworkbench";
    private static float[] heights=new float[0], normalX=new float[0], normalY=new float[0], normalZ=new float[0];
    private static final RenderType TYPE = create("water_ripple", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 1048576, false, true,
        CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false));

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent e) throws IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(NS, "water"), DefaultVertexFormat.POSITION_COLOR_TEX), s -> { shader = s; System.out.println("[VFXWORKBENCH] water shader compiled"); });
    }

    public static void draw(WaveField field, Vec3 origin, PoseStack pose, Vec3 camera, float partial) {
        if (shader == null) return;
        ParamState state = field.stateForRender();
        int rgb = state.getColor("color");
        shader.safeGetUniform("Tint").set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        shader.safeGetUniform("Opacity").set(state.getFloat("opacity"));
        shader.safeGetUniform("Shininess").set(state.getFloat("shininess"));
        shader.safeGetUniform("Reflection").set(state.getFloat("reflection"));

        float width = state.getFloat("width");
        float normalStrength = state.getFloat("normalStrength");
        VertexConsumer out = BUFFER.getBuffer(TYPE);
        Matrix4f matrix = pose.last().pose();
        int size = field.size;
        if(heights.length!=size*size){heights=new float[size*size];normalX=new float[size*size];normalY=new float[size*size];normalZ=new float[size*size];}
        double cell=width/(size-1);
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            int i=z*size+x;
            heights[i]=field.sample(x,z,partial);
            double dx=(field.sample(x+1,z,partial)-field.sample(x-1,z,partial))/(2*cell)*normalStrength;
            double dz=(field.sample(x,z+1,partial)-field.sample(x,z-1,partial))/(2*cell)*normalStrength;
            double norm=Math.sqrt(1+dx*dx+dz*dz);
            normalX[i]=(float)(.5-dx/norm*.5);normalY[i]=(float)(.5+.5/norm);normalZ[i]=(float)(.5-dz/norm*.5);
        }
        for (int z = 0; z < size - 1; z++) for (int x = 0; x < size - 1; x++) {
            emit(out, matrix, field, origin, camera, x, z, width, normalStrength, partial, size);
            emit(out, matrix, field, origin, camera, x, z + 1, width, normalStrength, partial, size);
            emit(out, matrix, field, origin, camera, x + 1, z + 1, width, normalStrength, partial, size);
            emit(out, matrix, field, origin, camera, x + 1, z, width, normalStrength, partial, size);
        }
        BUFFER.endBatch(TYPE);
    }

    private static void emit(VertexConsumer out, Matrix4f m, WaveField f, Vec3 o, Vec3 c, int x, int z, float width, float normalStrength, float t, int size) {
        int i=z*size+x;
        float u = x / (float) (size - 1), v = z / (float) (size - 1);
        out.vertex(m, (float) (o.x - c.x + (u - 0.5) * width), (float) (o.y - c.y + heights[i]), (float) (o.z - c.z + (v - 0.5) * width))
           .color(normalX[i],normalY[i],normalZ[i], 1f)
           .uv(u, v).endVertex();
    }
}
