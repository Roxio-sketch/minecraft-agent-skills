package dev.rendercraft.particlelab;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.*;
import org.joml.*;
import java.lang.Math;
import org.lwjgl.opengl.*;
final class Particles {
 static ParticleEngine engine;static ShaderInstance shader;
 static void register(RegisterShadersEvent e){try{e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(Demo.ID,"particle"),DefaultVertexFormat.POSITION_COLOR_TEX),s->shader=s);}catch(java.io.IOException ex){throw new RuntimeException(ex);}}
 static void reset(Recipe r){engine=new ParticleEngine(new ParticleEngine.Config(r.motion,r.capacity,r.rate,r.lifetime,r.speed,r.drag,r.gravity,r.orbit,r.seed));}
 static void clear(){if(engine!=null)engine.clear();engine=null;}
 static void tick(Recipe r){if(engine!=null)engine.tick();}
 static void render(RenderLevelStageEvent e){if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||shader==null||engine==null||engine.points().isEmpty())return;
  if(net.minecraft.client.Minecraft.useShaderTransparency())return;
  try(PassState state=new PassState()){
   RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE);
   RenderSystem.setShader(()->shader);var r=Demo.Client.recipe;var relative=Demo.Client.anchor.subtract(e.getCamera().getPosition());var matrix=new Matrix4f(e.getPoseStack().last().pose()).translate((float)relative.x,(float)relative.y,(float)relative.z);float partial=e.getPartialTick();var q=e.getCamera().rotation();var right=new Vector3f(r.size,0,0).rotate(q);var up=new Vector3f(0,r.size,0).rotate(q);
   BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);
   for(var p:engine.points()){float x=(float)(p.px+(p.x-p.px)*partial),y=(float)(p.py+(p.y-p.py)*partial),z=(float)(p.pz+(p.z-p.pz)*partial),t=(float)(p.age/p.life),alpha=(float)Math.min(1,p.age*10)*Math.max(0,1-t);
    for(int[] c:new int[][]{{-1,-1},{1,-1},{1,1},{-1,1}})b.vertex(matrix,x+right.x*c[0]+up.x*c[1],y+right.y*c[0]+up.y*c[1],z+right.z*c[0]+up.z*c[1]).color(r.color[0]*(1-t)+r.accent[0]*t,r.color[1]*(1-t)+r.accent[1]*t,r.color[2]*(1-t)+r.accent[2]*t,alpha).uv((c[0]+1)*.5f,(c[1]+1)*.5f).endVertex();
   }BufferUploader.drawWithShader(b.end());Gpu.count();
  }
 }
}
