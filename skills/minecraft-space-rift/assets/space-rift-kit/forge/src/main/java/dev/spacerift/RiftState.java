package dev.spacerift;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;
/** Narrow scope: saves every state touched by this renderer, including failures during target creation. */
final class RiftState implements AutoCloseable {
    final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    final int[] viewport=new int[4],textures=new int[12],tracked=new int[12];final float[] mask=new float[4],color=RenderSystem.getShaderColor().clone();
    final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
    final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),srcRGB=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRGB=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),eqRGB=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),eqA=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    final ShaderInstance shader=RenderSystem.getShader();
    RiftState(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<12;i++){tracked[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
    public void close(){
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        for(int i=0;i<12;i++){RenderSystem.setShaderTexture(i,tracked[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(textures[i]);}GlStateManager._activeTexture(active);
        RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
        if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
        if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(srcRGB,dstRGB,srcA,dstA);GL20.glBlendEquationSeparate(eqRGB,eqA);
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();RenderSystem.colorMask(mask[0]>.5,mask[1]>.5,mask[2]>.5,mask[3]>.5);
    }
}
