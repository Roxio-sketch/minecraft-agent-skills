package dev.trailbeam;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.*;

@Mod.EventBusSubscriber(modid="trailbeam",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BeamRender extends RenderType {
    private BeamRender(){super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    private static ShaderInstance alphaShader,addShader;
    private static final Map<Recipe.Layer,RenderType> types=new HashMap<>();
    private static final MultiBufferSource.BufferSource buffer=MultiBufferSource.immediate(new BufferBuilder(262144));
    public static int generation;
    public static long quadsDrawn;
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("trailbeam","beam"),DefaultVertexFormat.POSITION_COLOR_TEX),s->{alphaShader=s;generation++;types.clear();});
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("trailbeam","beam_add"),DefaultVertexFormat.POSITION_COLOR_TEX),s->{addShader=s;types.clear();});
    }
    public static void clearTypes(){types.clear();}
    private static RenderType type(Recipe.Layer l) {
        return types.computeIfAbsent(l,key->RenderType.create("trailbeam_"+types.size(),DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,l.blend().equals("alpha"),
            CompositeState.builder().setShaderState(new ShaderStateShard(()->{
                ShaderInstance shader=l.blend().equals("alpha")?alphaShader:addShader;
                shader.safeGetUniform("Profile").set(switch(l.profile()){case "solid"->1;case "double"->2;case "dash"->3;case "pulse"->4;case "plasma"->5;case "rune"->6;default->0;});
                shader.safeGetUniform("Gain").set((float)l.gain());return shader;
            })).setTextureState(new TextureStateShard(new ResourceLocation(l.texture()),false,false))
            .setTransparencyState(l.blend().equals("alpha")?TRANSLUCENT_TRANSPARENCY:ADDITIVE_TRANSPARENCY)
            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
    }
    /** Caller provides world-to-view pose and world camera. Subtract camera exactly once. */
    public static void draw(List<Geometry.Quad> mesh,Recipe.Layer layer,PoseStack pose,Geometry.V camera,double seconds) {
        if(alphaShader==null||addShader==null||mesh.isEmpty())return;
        RenderType type=type(layer);VertexConsumer out=buffer.getBuffer(type);Matrix4f matrix=pose.last().pose();
        try {for(Geometry.Quad q:mesh){emit(out,matrix,camera,q.a(),layer,seconds);emit(out,matrix,camera,q.b(),layer,seconds);emit(out,matrix,camera,q.c(),layer,seconds);emit(out,matrix,camera,q.d(),layer,seconds);quadsDrawn++;}}
        finally {buffer.endBatch(type);} // draw-time uniforms stay attached to this material
    }
    private static void emit(VertexConsumer out,Matrix4f matrix,Geometry.V camera,Geometry.Vertex v,Recipe.Layer l,double time) {
        Geometry.V p=v.position().sub(camera);float[] c=l.colorAt(v.along(),v.age());
        // Whole tile phase wraps on CPU to retain precision in long-running worlds.
        float u=(float)(v.u()*l.repeat()-(time*l.scroll()%1.0));
        out.vertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).color(c[0],c[1],c[2],c[3]).uv(u,(float)v.v()).endVertex();
    }
}
