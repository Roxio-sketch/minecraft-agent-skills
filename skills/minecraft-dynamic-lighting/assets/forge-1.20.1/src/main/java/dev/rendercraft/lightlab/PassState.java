package dev.rendercraft.lightlab;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;
/** Scope for targets, 2D samplers and render-state touched by these demos. */
final class PassState implements AutoCloseable {
 final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
 final int[] vp=new int[4],bound=new int[2],shaderTex=new int[2]; final float[] mask=new float[4],color=RenderSystem.getShaderColor().clone();
 final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
 final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
 final ShaderInstance previous=RenderSystem.getShader();
 PassState(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,vp);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<2;i++){shaderTex[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);bound[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
 public void close(){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(vp[0],vp[1],vp[2],vp[3]);for(int i=0;i<2;i++){RenderSystem.setShaderTexture(i,shaderTex[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(bound[i]);}GlStateManager._activeTexture(active);RenderSystem.setShader(()->previous);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.depthFunc(func);RenderSystem.blendFuncSeparate(sr,dr,sa,da);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(mask[0]>.5,mask[1]>.5,mask[2]>.5,mask[3]>.5);}
}
