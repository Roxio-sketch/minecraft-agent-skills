package dev.firecoil;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.List;
import dev.firecoil.FireCore.V;

@Mod.EventBusSubscriber(modid=FireCoil.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FireRender extends RenderType {
 private FireRender(){super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
 private static ShaderInstance shader;
 private static final MultiBufferSource.BufferSource buffer=MultiBufferSource.immediate(new BufferBuilder(524288));
 private static final RenderType TYPE=RenderType.create("firecoil",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,524288,false,true,
  CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
  .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
 public static int generation,renderedVertices,lastGl;public static boolean failed;
 public static double lastCpuMs;
 @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws IOException {
  e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(FireCoil.ID,"fire"),DefaultVertexFormat.POSITION_COLOR_TEX),s->{shader=s;generation++;failed=false;Client.failed=false;VolumeGpu.release();});
 }
 public static void draw(List<Geometry.Quad> mesh,Recipe recipe,PoseStack pose,V camera,double time) {
  renderedVertices=0;if(shader==null||mesh.isEmpty())return;long begin=System.nanoTime();
  shader.safeGetUniform("Time").set((float)(time%4096));
  for(String name:List.of("RiseSpeed","Turbulence","TongueLength","Erosion","Intensity","Opacity"))shader.safeGetUniform(name).set((float)recipe.d(Character.toLowerCase(name.charAt(0))+name.substring(1)));
  for(String name:List.of("CoreColor","MidColor","EdgeColor")){float[] c=recipe.color(Character.toLowerCase(name.charAt(0))+name.substring(1));shader.safeGetUniform(name).set(c[0],c[1],c[2]);}
  VertexConsumer out=buffer.getBuffer(TYPE);Matrix4f matrix=pose.last().pose();
  try{for(Geometry.Quad q:mesh){emit(out,matrix,camera,q.a());emit(out,matrix,camera,q.b());emit(out,matrix,camera,q.c());emit(out,matrix,camera,q.d());}}
  finally{buffer.endBatch(TYPE);lastCpuMs=(System.nanoTime()-begin)/1e6;lastGl=org.lwjgl.opengl.GL11.glGetError();failed=lastGl!=0;}
 }
 private static void emit(VertexConsumer out,Matrix4f matrix,V camera,Geometry.Vertex v) {
  V p=v.position().sub(camera);out.vertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).color(v.kind(),v.age(),v.seed(),v.strength()).uv(v.u(),v.v()).endVertex();renderedVertices++;
 }
}
