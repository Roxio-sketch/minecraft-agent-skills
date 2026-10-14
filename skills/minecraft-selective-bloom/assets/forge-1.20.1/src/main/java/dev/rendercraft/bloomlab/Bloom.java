package dev.rendercraft.bloomlab;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraftforge.client.event.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
/** Selected client-local glowing marker only. Does not select arbitrary native items. */
final class Bloom {
 static TextureTarget emission,a,b,base;static ShaderInstance blur,composite;
 static void register(RegisterShadersEvent e){Gpu.register(e,"blur",s->blur=s);Gpu.register(e,"composite",s->composite=s);}
 static void release(){for(TextureTarget t:new TextureTarget[]{emission,a,b,base})if(t!=null)t.destroyBuffers();emission=a=b=base=null;}
 static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||blur==null||composite==null)return;
  var mc=Minecraft.getInstance();RenderTarget main=mc.getMainRenderTarget();if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId||main.getDepthTextureId()<0||Minecraft.useShaderTransparency())return;
  try(PassState state=new PassState()){
   if(emission==null||emission.width!=main.width||emission.height!=main.height){release();emission=Gpu.target(main.width,main.height,true);base=Gpu.target(main.width,main.height,false);a=Gpu.target(main.width/2,main.height/2,false);b=Gpu.target(main.width/2,main.height/2,false);}
   Gpu.blit(main,base,GL11.GL_COLOR_BUFFER_BIT);Gpu.clear(emission);Gpu.blit(main,emission,GL11.GL_DEPTH_BUFFER_BIT);emission.bindWrite(true);
   RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
   var relative=Demo.Client.anchor.subtract(e.getCamera().getPosition());Matrix4f pose=new Matrix4f(e.getPoseStack().last().pose()).translate((float)relative.x,(float)relative.y,(float)relative.z);
   float[] c=Demo.Client.recipe.color;float r=.25f;BufferBuilder mesh=Tesselator.getInstance().getBuilder();mesh.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
   // Camera-facing selected emitter; depth is tested against an independent scene snapshot.
   var q=e.getCamera().rotation();var right=new org.joml.Vector3f(r,0,0).rotate(q);var up=new org.joml.Vector3f(0,r,0).rotate(q);
   for(int[] v:new int[][]{{-1,-1},{1,-1},{1,1},{-1,-1},{1,1},{-1,1}}){mesh.vertex(pose,right.x*v[0]+up.x*v[1],right.y*v[0]+up.y*v[1],right.z*v[0]+up.z*v[1]).color(c[0],c[1],c[2],1).endVertex();}
   BufferUploader.drawWithShader(mesh.end());
   int texture=emission.getColorTextureId();
   for(int i=0;i<Demo.Client.recipe.passes;i++){
    Gpu.clear(a);blur.safeGetUniform("Direction").set(Demo.Client.recipe.width/(i==0?emission.width:b.width),0);Gpu.full(blur,texture,0);
    Gpu.clear(b);blur.safeGetUniform("Direction").set(0,Demo.Client.recipe.width/a.height);Gpu.full(blur,a.getColorTextureId(),0);texture=b.getColorTextureId();
   }
   main.bindWrite(true);composite.safeGetUniform("Intensity").set(Demo.Client.recipe.intensity);Gpu.full(composite,base.getColorTextureId(),texture);Gpu.count();
  }
 }
}
