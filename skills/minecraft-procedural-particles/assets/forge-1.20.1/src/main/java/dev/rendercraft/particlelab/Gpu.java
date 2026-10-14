package dev.rendercraft.particlelab;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.*;
final class Gpu {
 static void register(RegisterShadersEvent e,String name,java.util.function.Consumer<ShaderInstance> callback){try{e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(Demo.ID,name),DefaultVertexFormat.POSITION_TEX),callback);}catch(java.io.IOException ex){throw new RuntimeException(ex);}}
 static TextureTarget target(int w,int h,boolean depth){TextureTarget t=new TextureTarget(Math.max(1,w),Math.max(1,h),depth,Minecraft.ON_OSX);t.setFilterMode(GL11.GL_LINEAR);return t;}
 static void clear(TextureTarget t){GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);t.setClearColor(0,0,0,0);t.clear(Minecraft.ON_OSX);t.bindWrite(true);if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete target");}
 static void blit(RenderTarget from,RenderTarget to,int bits){GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,from.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,to.frameBufferId);GL30.glBlitFramebuffer(0,0,from.width,from.height,0,0,to.width,to.height,bits,GL11.GL_NEAREST);}
 static void full(ShaderInstance s,int tex0,int tex1){RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.colorMask(true,true,true,true);RenderSystem.setShader(()->s);RenderSystem.setShaderTexture(0,tex0);RenderSystem.setShaderTexture(1,tex1);BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();BufferUploader.drawWithShader(b.end());}
 static void count(){Demo.Client.draws++;Demo.Client.gl=GL11.glGetError();}
}
