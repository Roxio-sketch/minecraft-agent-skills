package dev.blackholelab;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.file.*;

/** Core-only HDR source, optical diffusion and an independent final scene copy.
 * Runs inside BlackHoleWorld's complete GL state scope; never writes depth.
 */
final class BlackHoleCoreBloom {
    static ShaderInstance blur,composite;
    private static TextureTarget emission,ping,near,wide,base;
    private static boolean active,diskSource;
    private static float radiusPixels,sourcePeak;
    private static int sourcePixels,sourceFormat,sourceWidth,sourceHeight,contributionPixels;
    private static long draws;
    static void release(){
        for(TextureTarget t:new TextureTarget[]{emission,ping,near,wide,base})if(t!=null)t.destroyBuffers();
        emission=ping=near=wide=base=null;active=false;sourcePixels=0;sourcePeak=0;
    }
    static JsonObject metadata(){
        JsonObject r=new JsonObject();r.addProperty("active",active);r.addProperty("sourcePixels",sourcePixels);
        r.addProperty("sourcePeak",sourcePeak);r.addProperty("radiusPixels",radiusPixels);
        r.addProperty("width",sourceWidth);r.addProperty("height",sourceHeight);r.addProperty("contributionPixels",contributionPixels);
        r.addProperty("format",sourceFormat);r.addProperty("draws",draws);
        r.addProperty("sourcePolicy",diskSource?"visible ribbon disk HDR radiance only; no terrain, sky, actors or UI":"captured core radiance after disk transmission; same foreground and artistic terrain policy as visible core");
        r.addProperty("opticalSpillCanCoverForeground",true);return r;
    }
    static void render(ShaderInstance world,RenderTarget main,TextureTarget scene,
                       Matrix4f vp,Vec3 relative,Vec3 origin,String probe)throws Exception{
        var p=BlackHoleRecipe.state();
        active=false;sourcePixels=0;sourcePeak=0;radiusPixels=0;contributionPixels=0;
        sourceWidth=Math.max(1,(main.width+1)/2);sourceHeight=Math.max(1,(main.height+1)/2);
        diskSource=p.get("objectMode").equals("black")&&p.get("diskStyle").equals("ribbons")&&p.getFloat("diskBloom")>.0001f;
        // Do not allocate or alter black mode, disabled/dark sources or the old
        // complete recipes, whose optional coreGlow value remains zero.
        if(!diskSource&&(!p.get("objectMode").equals("white")||p.getFloat("coreGlow")<=.0001f
            ||p.getFloat("coreEmission")<=.0001f||p.getFloat("exposure")<=0
            ||(p.getColor("coreColor")&0xffffff)==0)){captureEmpty(probe);return;}
        if(blur==null||composite==null)throw new IllegalStateException("White core diffusion shaders unavailable");
        double cameraDistance=relative.distanceTo(origin);
        if(cameraDistance<p.getFloat("core")*BlackHoleWorld.scale*1.01){captureEmpty(probe);return;}
        Vector4f center=new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1).mul(vp);
        if(center.w<=.001f){captureEmpty(probe);return;}
        // Projection scale follows the real core instead of a fixed screen
        // radius. The eye offset includes bob/hurt translation captured by VP.
        float eyeDistance=(float)cameraDistance;
        float projectionY=(float)Math.sqrt(vp.m01()*vp.m01()+vp.m11()*vp.m11()+vp.m21()*vp.m21());
        float corePixels=projectionY*main.height*.5f*p.getFloat("core")*BlackHoleWorld.scale/Math.max(eyeDistance,.01f);
        radiusPixels=Math.max(.6f,Math.min(main.height*.28f,corePixels*(p.getFloat("coreGlowRadius")-1)*.32f));
        if(diskSource){
            float diskPixels=projectionY*main.height*.5f*BlackHoleRecipe.outer(p)*BlackHoleWorld.scale/Math.max(eyeDistance,.01f);
            radiusPixels=Math.max(.8f,Math.min(main.height*.08f,diskPixels*.035f*p.getFloat("diskBloomRadius")));
        }
        int w=sourceWidth,h=sourceHeight;
        int[] textures=new int[6];for(int i=0;i<6;i++)textures[i]=RenderSystem.getShaderTexture(i);
        boolean stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        try{
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            if(base==null||base.width!=main.width||base.height!=main.height){
                release();base=target(main.width,main.height,false);
                emission=target(w,h,true);ping=target(w,h,true);near=target(w,h,true);wide=target(w,h,true);
            }
            active=true;
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();
            RenderSystem.colorMask(true,true,true,true);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            emission.bindWrite(true);RenderSystem.setShader(()->world);
            world.safeGetUniform("LensProbeMode").set(diskSource?4f:3f);full();world.safeGetUniform("LensProbeMode").set(0f);
            if(probe!=null)captureSource(probe);
            float uvX=radiusPixels/main.width,uvY=radiusPixels/main.height;
            pass(emission,ping,uvX,0);pass(ping,near,0,uvY);
            pass(emission,ping,uvX*2.5f,0);pass(ping,wide,0,uvY*2.5f);
            // Preserve the original scene/depth snapshot used by probes and by
            // the mass/particle passes. Composite samples a different target.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,base.frameBufferId);
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            java.nio.ByteBuffer before=null;
            if(probe!=null){
                before=BufferUtils.createByteBuffer(main.width*main.height*4);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,before);
                Path folder=Minecraft.getInstance().gameDirectory.toPath().resolve("black-hole-lab/world-probes");
                try(NativeImage im=net.minecraft.client.Screenshot.takeScreenshot(main)){im.writeToFile(folder.resolve(probe+"-pre-core-bloom.png"));}
            }
            main.bindWrite(true);RenderSystem.setShader(()->composite);
            RenderSystem.setShaderTexture(0,base.getColorTextureId());RenderSystem.setShaderTexture(1,near.getColorTextureId());
            RenderSystem.setShaderTexture(2,wide.getColorTextureId());
            RenderSystem.setShaderTexture(3,emission.getColorTextureId());
            composite.safeGetUniform("DiskSource").set(diskSource?1f:0f);
            composite.safeGetUniform("Intensity").set(p.getFloat(diskSource?"diskBloom":"coreGlow"));
            composite.safeGetUniform("GlareCenter").set(center.x/center.w*.5f+.5f,center.y/center.w*.5f+.5f);
            composite.safeGetUniform("GlareScale").set(Math.max(1,corePixels*p.getFloat("coreGlowRadius")*1.6f)/main.width,
                                                       Math.max(1,corePixels*p.getFloat("coreGlowRadius")*1.6f)/main.height);
            composite.safeGetUniform("Exposure").set(p.getFloat("exposure"));full();draws++;
            if(before!=null){
                var after=BufferUtils.createByteBuffer(main.width*main.height*4);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,after);
                for(int i=0;i<main.width*main.height;i++)for(int c=0;c<3;c++)if(Math.abs((after.get(i*4+c)&255)-(before.get(i*4+c)&255))>1){contributionPixels++;break;}
            }
        }finally{
            world.safeGetUniform("LensProbeMode").set(0f);main.bindWrite(true);
            RenderSystem.setShader(()->world);for(int i=0;i<6;i++)RenderSystem.setShaderTexture(i,textures[i]);
            if(stencil)GL11.glEnable(GL11.GL_STENCIL_TEST);else GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }
    private static TextureTarget target(int w,int h,boolean hdr){
        // Atlas and PBO state survives resource reload: every texture allocation
        // must be independent of those client upload settings.
        try(UnpackScope unpack=new UnpackScope()){
            TextureTarget t=new TextureTarget(w,h,false,Minecraft.ON_OSX);t.setFilterMode(GL11.GL_LINEAR);
            if(hdr){
                GlStateManager._bindTexture(t.getColorTextureId());
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
                sourceFormat=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
                if(sourceFormat!=GL30.GL_RGBA16F)throw new IllegalStateException("Core target did not allocate RGBA16F");
            }
            t.bindWrite(true);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Core diffusion target incomplete");
            return t;
        }
    }
    private static void pass(TextureTarget source,TextureTarget destination,float x,float y){
        destination.bindWrite(true);RenderSystem.setShader(()->blur);
        RenderSystem.setShaderTexture(0,source.getColorTextureId());blur.safeGetUniform("Direction").set(x,y);full();
    }
    private static void full(){
        BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
    private static void captureSource(String probe)throws Exception{
        int n=emission.width*emission.height;var pixels=BufferUtils.createFloatBuffer(n*4);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,emission.frameBufferId);
        GL11.glReadPixels(0,0,emission.width,emission.height,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        Path folder=Minecraft.getInstance().gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(folder);
        try(NativeImage im=new NativeImage(emission.width,emission.height,false)){
            for(int i=0;i<n;i++){
                float r=pixels.get(i*4),g=pixels.get(i*4+1),b=pixels.get(i*4+2);
                sourcePeak=Math.max(sourcePeak,Math.max(r,Math.max(g,b)));if(Math.max(r,Math.max(g,b))>.00001f)sourcePixels++;
                int red=display(r),green=display(g),blue=display(b);
                im.setPixelRGBA(i%emission.width,emission.height-1-i/emission.width,0xff000000|blue<<16|green<<8|red);
            }
            im.writeToFile(folder.resolve(probe+"-core-emission.png"));
        }
    }
    private static int display(float value){return Math.round(255*(float)Math.pow(Math.max(0,1-Math.exp(-Math.max(0,value))),1/2.2));}
    private static void captureEmpty(String probe)throws Exception{
        if(probe==null)return;
        Path folder=Minecraft.getInstance().gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(folder);
        try(NativeImage im=new NativeImage(sourceWidth,sourceHeight,false)){
            for(int y=0;y<sourceHeight;y++)for(int x=0;x<sourceWidth;x++)im.setPixelRGBA(x,y,0xff000000);
            im.writeToFile(folder.resolve(probe+"-core-emission.png"));
        }
    }
    private static final class UnpackScope implements AutoCloseable{
        private static final int[] KEYS={GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_SWAP_BYTES,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES};
        private final int[] values=new int[KEYS.length];private final int buffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        UnpackScope(){for(int i=0;i<KEYS.length;i++)values[i]=GL11.glGetInteger(KEYS[i]);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);for(int key:KEYS)GL11.glPixelStorei(key,key==GL11.GL_UNPACK_ALIGNMENT?4:0);}
        public void close(){for(int i=0;i<KEYS.length;i++)GL11.glPixelStorei(KEYS[i],values[i]);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);}
    }
}
