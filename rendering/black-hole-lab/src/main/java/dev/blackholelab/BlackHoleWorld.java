package dev.blackholelab;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.file.*;

/** One client-local artistic field, using independent current-frame color/depth copies. */
final class BlackHoleWorld {
    static float scale=1.6f,distance=14f,lens=.7f,lensRadius=2.4f,rotation=.8f,infall=.22f,exposure=.30f,tilt=-12f;
    static float halo=1f,haloRing=1f,haloDisk=1f,haloAura=1f;
    static String probe;
    static int lastGlError;
    static double cpuMillis;
    private static TextureTarget snapshot;
    static void release(){if(snapshot!=null){snapshot.destroyBuffers();snapshot=null;}BlackHoleParticles.release();}
    static void render(RenderLevelStageEvent e){
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        if(BlackHoleClient.center==null||BlackHoleRender.world==null||!BlackHoleClient.failure.isEmpty())return;
        // This backend requires the main target; alternate shader pipelines need their own adapter.
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId||main.getDepthTextureId()<0)return;
        long start=System.nanoTime();
        try(State state=new State()){
            if(snapshot==null||snapshot.width!=main.width||snapshot.height!=main.height){
                release();snapshot=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
                snapshot.setFilterMode(GL11.GL_NEAREST);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,snapshot.frameBufferId);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Scene copy FBO incomplete");
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            main.bindWrite(true);
            Matrix4f vp=new Matrix4f(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose());
            Matrix4f inverse=new Matrix4f(vp).invert();
            if(!inverse.isFinite())throw new IllegalStateException("Non-invertible world view projection");
            Vec3 relative=BlackHoleClient.center.subtract(e.getCamera().getPosition());
            ShaderInstance s=BlackHoleRender.world;BlackHoleRender.material(s,BlackHoleClient.coreColor);
            s.safeGetUniform("ViewProjection").set(vp);s.safeGetUniform("InvViewProjection").set(inverse);
            s.safeGetUniform("CenterRelative").set((float)relative.x,(float)relative.y,(float)relative.z);
            s.safeGetUniform("WorldScale").set(scale);s.safeGetUniform("LensStrength").set(lens);s.safeGetUniform("LensRadius").set(lensRadius);
            s.safeGetUniform("HaloStrength").set(halo);s.safeGetUniform("HaloRing").set(haloRing);s.safeGetUniform("HaloDisk").set(haloDisk);s.safeGetUniform("HaloAura").set(haloAura);
            s.safeGetUniform("uSpeed").set(rotation);s.safeGetUniform("uInfall").set(infall);
            s.safeGetUniform("uExposure").set(exposure);s.safeGetUniform("uTilt").set(tilt);
            s.safeGetUniform("uThickness").set(.055f);s.safeGetUniform("uDensity").set(5f);
            RenderSystem.setShader(()->s);RenderSystem.setShaderTexture(0,snapshot.getColorTextureId());RenderSystem.setShaderTexture(1,snapshot.getDepthTextureId());
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
            BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
            b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();
            BufferUploader.drawWithShader(b.end());BlackHoleClient.worldDraws++;
            java.nio.ByteBuffer fieldColor=null;
            if(probe!=null){
                fieldColor=BufferUtils.createByteBuffer(main.width*main.height*4);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,fieldColor);
                Path folder=mc.gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(folder);
                try(NativeImage im=Screenshot.takeScreenshot(main)){im.writeToFile(folder.resolve(probe+"-field.png"));}
            }
            BlackHoleParticles.render(vp,e.getPoseStack().last().pose(),relative);
            lastGlError=GL11.glGetError();
            if(lastGlError!=GL11.GL_NO_ERROR)throw new IllegalStateException("World GL error "+lastGlError);
            if(probe!=null){String name=probe;probe=null;capture(name,main,inverse,relative,e,fieldColor);}
        }catch(Exception ex){BlackHoleClient.failure=ex.toString();System.err.println("[BLACK-HOLE] World disabled: "+ex);}
        cpuMillis=(System.nanoTime()-start)/1e6;
    }
    /** Explicit capture of the same frame before/after; not active in normal rendering. */
    private static void capture(String name,RenderTarget main,Matrix4f inverse,Vec3 relative,RenderLevelStageEvent e,java.nio.ByteBuffer fieldColor)throws Exception{
        Path out=Minecraft.getInstance().gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(out);
        int n=main.width*main.height;var before=BufferUtils.createByteBuffer(n*4);var after=BufferUtils.createByteBuffer(n*4);
        var bd=BufferUtils.createFloatBuffer(n);var ad=BufferUtils.createFloatBuffer(n);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,snapshot.frameBufferId);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,before);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,bd);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,after);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,ad);
        int changed=0,outside=0,foreground=0,foregroundChanged=0,particlePixels=0;float depthDifference=0;Vector4f h=new Vector4f();
        double radius=Math.max(2.9*1.5,BlackHoleParticles.enabled?BlackHoleParticles.outer*1.02+BlackHoleParticles.size*6:0)*scale;
        for(int i=0;i<n;i++){
            depthDifference=Math.max(depthDifference,Math.abs(bd.get(i)-ad.get(i)));
            boolean diff=false;for(int c=0;c<3;c++)if(Math.abs((before.get(i*4+c)&255)-(after.get(i*4+c)&255))>1)diff=true;
            if(diff)changed++;
            boolean particleDiff=false;for(int c=0;c<3;c++)if(Math.abs((fieldColor.get(i*4+c)&255)-(after.get(i*4+c)&255))>1)particleDiff=true;
            if(particleDiff)particlePixels++;
            float x=((i%main.width)+.5f)/main.width*2-1,y=((i/main.width)+.5f)/main.height*2-1;
            h.set(x,y,1,1).mul(inverse);h.div(h.w);Vec3 dir=new Vec3(h.x,h.y,h.z).normalize();
            double b=-relative.dot(dir),disc=b*b-relative.lengthSqr()+radius*radius;
            if(disc<0){if(diff)outside++;continue;}
            double entry=-b-Math.sqrt(disc);
            if(bd.get(i)<.999999f){
                h.set(x,y,bd.get(i)*2-1,1).mul(inverse);h.div(h.w);
                if(new Vec3(h.x,h.y,h.z).length()<Math.max(0,entry)-.02){foreground++;if(diff)foregroundChanged++;}
            }
        }
        try(NativeImage im=Screenshot.takeScreenshot(snapshot)){im.writeToFile(out.resolve(name+"-before.png"));}
        try(NativeImage im=Screenshot.takeScreenshot(main)){im.writeToFile(out.resolve(name+"-after.png"));}
        JsonObject r=new JsonObject();r.addProperty("name",name);r.addProperty("minecraft","1.20.1");r.addProperty("forge",net.minecraftforge.versions.forge.ForgeVersion.getVersion());
        r.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));r.addProperty("width",main.width);r.addProperty("height",main.height);
        r.addProperty("changedPixels",changed);r.addProperty("outsideChangedPixels",outside);r.addProperty("foregroundPixels",foreground);r.addProperty("foregroundChangedPixels",foregroundChanged);r.addProperty("maxDepthDifference",depthDifference);
        r.addProperty("scale",scale);r.addProperty("lens",lens);r.addProperty("lensRadius",lensRadius);r.addProperty("rotation",rotation);r.addProperty("infall",infall);r.addProperty("frozenTicks",BlackHoleRender.frozenTime);r.addProperty("exposure",exposure);r.addProperty("tilt",tilt);
        r.addProperty("particlePixels",particlePixels);r.addProperty("particlesEnabled",BlackHoleParticles.enabled);r.addProperty("particleCount",BlackHoleParticles.count);
        r.addProperty("particleSpeed",BlackHoleParticles.speed);r.addProperty("particleDebug",BlackHoleParticles.debug);r.addProperty("particleDraws",BlackHoleParticles.draws);
        r.addProperty("halo",halo);r.addProperty("haloRing",haloRing);r.addProperty("haloDisk",haloDisk);r.addProperty("haloAura",haloAura);
        r.addProperty("camera",e.getCamera().getPosition().toString());r.addProperty("center",BlackHoleClient.center.toString());r.addProperty("coreColor",String.format("#%06X",BlackHoleClient.coreColor));
        r.addProperty("generation",BlackHoleRender.generation);r.addProperty("glError",GL11.glGetError());
        Files.writeString(out.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));
    }
    private static final class State implements AutoCloseable{
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),eqr=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),eqa=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final int[] vp=new int[4],textures=new int[3],shaderTextures=new int[3];final float[] mask=new float[4];
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final ShaderInstance previous=RenderSystem.getShader();
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,vp);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<3;i++){shaderTextures[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
        public void close(){
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(vp[0],vp[1],vp[2],vp[3]);
            for(int i=0;i<3;i++){RenderSystem.setShaderTexture(i,shaderTextures[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(textures[i]);}GlStateManager._activeTexture(active);
            RenderSystem.setShader(()->previous);GlStateManager._glUseProgram(program);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(sr,dr,sa,da);GL20.glBlendEquationSeparate(eqr,eqa);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(mask[0]>.5f,mask[1]>.5f,mask[2]>.5f,mask[3]>.5f);
        }
    }
}
