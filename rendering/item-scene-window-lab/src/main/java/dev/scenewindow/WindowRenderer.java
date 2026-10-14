package dev.scenewindow;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.joml.*;
import org.lwjgl.opengl.*;
import java.util.*;
import java.lang.Math;

@Mod.EventBusSubscriber(modid=SceneWindow.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WindowRenderer extends BlockEntityWithoutLevelRenderer {
    private static WindowRenderer INSTANCE;
    private static ShaderInstance apertureShader;
    private static TextureTarget target;
    private static boolean rendering;
    public static float frozenTime=-1;
    public static Vector3f forcedEye;
    public static int forcedResolution;
    public static Scenes.Backend backend=Scenes.MESH_BACKEND;
    public static int shaderGeneration, renderedFrames, stateMismatches;
    public static final Map<String,Integer> contexts=new TreeMap<>();
    public static final Map<String,float[]> contextEyes=new TreeMap<>();
    public static Vector3f lastEye=new Vector3f();
    public static int lastFboStatus;
    private WindowRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    public static WindowRenderer instance(){if(INSTANCE==null)INSTANCE=new WindowRenderer();return INSTANCE;}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws java.io.IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(SceneWindow.ID,"aperture"),DefaultVertexFormat.POSITION_TEX),s->{apertureShader=s;shaderGeneration++;});
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)manager -> {Scenes.clear();RenderSystem.recordRenderCall(WindowRenderer::release);});
    }
    public static void release(){if(target!=null){target.destroyBuffers();target=null;}}
    public static com.mojang.blaze3d.pipeline.RenderTarget lastTarget(){return target;}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource source,int light,int overlay){
        if(rendering||apertureShader==null)return;
        var mc=Minecraft.getInstance();
        Scenes.Scene scene;
        try{
            ResourceLocation id=new ResourceLocation(stack.hasTag()&&stack.getTag().contains("Scene")?stack.getTag().getString("Scene"):"scenewindow:observatory");
            scene=Scenes.get(mc.getResourceManager(),id);
        }catch(Exception e){Scenes.lastError=e.toString();return;}
        // Drain the supplied buffer before the offscreen pass, then draw the FBO immediately.
        if(source instanceof MultiBufferSource.BufferSource buffer)buffer.endBatch();
        rendering=true;
        pose.pushPose();pose.translate(.5,.5,.5);
        try(State state=new State()){
            float seconds=frozenTime>=0?frozenTime:mc.level==null?0:(mc.level.getGameTime()%1000000L+mc.getFrameTime())/20f;
            Vector3f eye=new Vector3f(scene.eye());
            boolean back=false;
            if(forcedEye!=null)eye.set(forcedEye);
            else if(!scene.camera().equals("fixed")&&context!=ItemDisplayContext.GUI){
                Matrix4f full=new Matrix4f(state.view).mul(pose.last().pose());
                full.invert().transformPosition(0,0,0,eye);
                back=eye.z<0; if(back){pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));eye.x=-eye.x;eye.z=-eye.z;}
                eye.z=Math.max(.15f,eye.z);
            }
            if(scene.camera().equals("orbit"))eye.set(scene.eye().x+(float)Math.sin(seconds*.4)*.65f,scene.eye().y+(float)Math.cos(seconds*.3)*.3f,scene.eye().z);
            lastEye.set(eye);
            contextEyes.put(context.name(),new float[]{eye.x,eye.y,eye.z});
            renderScene(scene,eye,seconds);
            // Restore the caller's target and projection BEFORE compositing the item surface.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,state.draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,state.read);
            RenderSystem.viewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
            RenderSystem.setProjectionMatrix(state.projection,state.sorting);
            RenderSystem.getModelViewStack().setIdentity();RenderSystem.getModelViewStack().mulPoseMatrix(state.view);RenderSystem.applyModelViewMatrix();
            if(state.scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(true);
            RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
            RenderSystem.setShader(()->apertureShader);RenderSystem.setShaderTexture(0,target.getColorTextureId());
            if(scene.mask()!=null)RenderSystem.setShaderTexture(1,scene.mask());
            apertureShader.safeGetUniform("Shape").set(scene.shape());
            apertureShader.safeGetUniform("FrameColor").set(scene.frame()[0],scene.frame()[1],scene.frame()[2]);
            BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            var m=pose.last().pose();
            b.vertex(m,-.5f,-.5f,0).uv(0,0).endVertex();b.vertex(m,.5f,-.5f,0).uv(1,0).endVertex();
            b.vertex(m,.5f,.5f,0).uv(1,1).endVertex();b.vertex(m,-.5f,.5f,0).uv(0,1).endVertex();
            BufferUploader.drawWithShader(b.end());
            contexts.merge(context.name(),1,Integer::sum);renderedFrames++;
        }finally{pose.popPose();rendering=false;}
    }
    private static void renderScene(Scenes.Scene s,Vector3f eye,float seconds){
        int resolution=forcedResolution>0?forcedResolution:s.resolution();
        if(target==null||target.width!=resolution){
            release();target=new TextureTarget(resolution,resolution,true,Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
        target.setClearColor(s.background()[0],s.background()[1],s.background()[2],1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        lastFboStatus=GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        float n=.04f,z=eye.z;
        // Plane z=0, window corners +/-0.5. Camera stays parallel to the window.
        Matrix4f projection=new Matrix4f().setFrustum((-.5f-eye.x)*n/z,(.5f-eye.x)*n/z,(-.5f-eye.y)*n/z,(.5f-eye.y)*n/z,n,128);
        RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        var model=RenderSystem.getModelViewStack();model.setIdentity();model.translate(-eye.x,-eye.y,-eye.z);RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);
        backend.draw(s,seconds);
    }
    /** Exact touched-state scope. FBO is reused only because its consumer is drawn immediately. */
    private static final class State implements AutoCloseable{
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int[] viewport=new int[4];
        final Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix()),view=new Matrix4f(RenderSystem.getModelViewMatrix());
        final VertexSorting sorting=RenderSystem.getVertexSorting();
        final ShaderInstance shader=RenderSystem.getShader();
        final float[] color=RenderSystem.getShaderColor().clone();
        final int tex0=RenderSystem.getShaderTexture(0),tex1=RenderSystem.getShaderTexture(1);
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final float[] mask=new float[4];
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);RenderSystem.getModelViewStack().pushPose();}
        public void close(){
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            RenderSystem.getModelViewStack().popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShaderTexture(0,tex0);RenderSystem.setShaderTexture(1,tex1);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(tex0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);GlStateManager._bindTexture(tex1);GlStateManager._activeTexture(active);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(sr,dr,sa,da);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(mask[0]>.5f,mask[1]>.5f,mask[2]>.5f,mask[3]>.5f);
            if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=draw||GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)!=write)stateMismatches++;
        }
    }
}
