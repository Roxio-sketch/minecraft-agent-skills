package research.subscene;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Compile-only API anchors. Never install or call as a complete secondary world renderer. */
public final class ForgeRouteProbe {
    public static TextureTarget createOwnedTarget(int width,int height,boolean stencil) {
        RenderSystem.assertOnRenderThreadOrInit();
        TextureTarget target=new TextureTarget(Math.max(1,width),Math.max(1,height),true,Minecraft.ON_OSX);
        try { if(stencil)target.enableStencil();target.checkStatus();return target; }
        catch(RuntimeException e){target.destroyBuffers();throw e;}
    }
    public static void resizeOwnedTarget(TextureTarget target,int width,int height){
        RenderSystem.assertOnRenderThreadOrInit();target.resize(Math.max(1,width),Math.max(1,height),Minecraft.ON_OSX);target.checkStatus();
    }
    public static boolean atOpaqueBoundary(RenderLevelStageEvent event){
        return event.getStage()==RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
    }
    public static final class SecondaryCamera extends Camera {
        public void place(ClientLevel level,net.minecraft.world.entity.Entity eye,float tick,double x,double y,double z,float yaw,float pitch){
            setup(level,eye,false,false,tick);setPosition(x,y,z);setRotation(yaw,pitch);
        }
    }
    /** Signature anchor only. Caller must already isolate world, renderer, camera, buffers and effects. */
    public static void renderPreparedWorld(LevelRenderer renderer,PoseStack pose,float partial,long finishNanos,Camera camera,LightTexture light,Matrix4f projection){
        renderer.renderLevel(pose,partial,finishNanos,false,camera,Minecraft.getInstance().gameRenderer,light,projection);
    }
}
