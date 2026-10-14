package dev.waterripple;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.io.IOException;

@Mod.EventBusSubscriber(modid="waterripple",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class RippleRender extends RenderType {
    private RippleRender(){super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    private static ShaderInstance shader;
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(1048576));
    private static final RenderType TYPE=create("water_ripple",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,1048576,false,true,
        CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("waterripple","water"),DefaultVertexFormat.POSITION_COLOR_TEX),s->shader=s);
    }
    public static void draw(WaveField f,Vec3 origin,PoseStack pose,Vec3 camera,float partial) {
        if(shader==null)return;
        RippleRecipe r=f.recipe;int rgb=Integer.parseInt(r.color.substring(1),16);
        shader.safeGetUniform("Tint").set(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);
        shader.safeGetUniform("Opacity").set((float)r.opacity);
        shader.safeGetUniform("Shininess").set((float)r.shininess);
        shader.safeGetUniform("Reflection").set((float)r.reflection);
        VertexConsumer out=BUFFER.getBuffer(TYPE); Matrix4f matrix=pose.last().pose();
        for(int z=0;z<f.size-1;z++)for(int x=0;x<f.size-1;x++) {
            emit(out,matrix,f,origin,camera,x,z,partial);emit(out,matrix,f,origin,camera,x,z+1,partial);
            emit(out,matrix,f,origin,camera,x+1,z+1,partial);emit(out,matrix,f,origin,camera,x+1,z,partial);
        }
        BUFFER.endBatch(TYPE);
    }
    private static void emit(VertexConsumer out,Matrix4f m,WaveField f,Vec3 o,Vec3 c,int x,int z,float t) {
        double cell=f.recipe.width/(f.size-1);
        double dx=(f.sample(x+1,z,t)-f.sample(x-1,z,t))/(2*cell)*f.recipe.normalStrength, dz=(f.sample(x,z+1,t)-f.sample(x,z-1,t))/(2*cell)*f.recipe.normalStrength;
        double norm=Math.sqrt(1+dx*dx+dz*dz);
        float u=x/(float)(f.size-1),v=z/(float)(f.size-1);
        out.vertex(m,(float)(o.x-c.x+(u-0.5)*f.recipe.width),(float)(o.y-c.y+f.sample(x,z,t)),(float)(o.z-c.z+(v-0.5)*f.recipe.width))
            .color((float)(0.5-dx/norm*0.5),(float)(0.5+0.5/norm),(float)(0.5-dz/norm*0.5),1f).uv(u,v).endVertex();
    }
}
