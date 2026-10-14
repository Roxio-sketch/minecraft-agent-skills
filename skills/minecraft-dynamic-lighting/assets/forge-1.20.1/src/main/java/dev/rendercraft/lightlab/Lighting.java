package dev.rendercraft.lightlab;
import com.mojang.blaze3d.pipeline.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
final class Lighting {
 static TextureTarget snapshot;static ShaderInstance shader;
 static void register(RegisterShadersEvent e){Gpu.register(e,"light",s->shader=s);}
 static void release(){if(snapshot!=null)snapshot.destroyBuffers();snapshot=null;}
 static void render(RenderLevelStageEvent e){if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS||shader==null)return;
  var main=Minecraft.getInstance().getMainRenderTarget();if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId||main.getDepthTextureId()<0||Minecraft.useShaderTransparency())return;
  try(PassState state=new PassState()){
   if(snapshot==null||snapshot.width!=main.width||snapshot.height!=main.height){release();snapshot=Gpu.target(main.width,main.height,true);}
   Gpu.blit(main,snapshot,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);main.bindWrite(true);
   var inverse=new Matrix4f(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose()).invert();if(!inverse.isFinite())throw new IllegalStateException("View projection singular");
   var center=Demo.Client.anchor.subtract(e.getCamera().getPosition());var r=Demo.Client.recipe;shader.safeGetUniform("InverseVP").set(inverse);shader.safeGetUniform("LightRelative").set((float)center.x,(float)center.y,(float)center.z);shader.safeGetUniform("LightColor").set(r.color[0],r.color[1],r.color[2]);shader.safeGetUniform("Radius").set(r.radius);shader.safeGetUniform("Intensity").set(r.intensity);shader.safeGetUniform("Texel").set(1f/main.width,1f/main.height);
   Gpu.full(shader,snapshot.getColorTextureId(),snapshot.getDepthTextureId());Gpu.count();
  }
 }
}
