package dev.blackholelab;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.lang.reflect.Field;

/** One client-local artistic field, using independent current-frame color/depth copies. */
final class BlackHoleWorld {
    static float scale=1.6f,distance=14f,lens=.7f,lensRadius=2.4f,rotation=.8f,infall=.22f,exposure=.30f,tilt=0f;
    static float halo=1f,haloRing=1f,haloDisk=1f,haloAura=1f;
    static String probe;
    static net.minecraft.world.entity.Entity probeEntity;
    static int lastGlError;
    static double cpuMillis;
    private static TextureTarget snapshot;
    private static TextureTarget environment;
    private static boolean environmentReady;
    private static boolean frameEnvironmentValid;
    private static int foregroundTexture;
    private static final int MAX_FOREGROUND=64;
    private static final java.util.List<net.minecraft.world.phys.AABB> foregroundBounds=new java.util.ArrayList<>();
    private static final java.util.List<net.minecraft.world.entity.Entity> foregroundEntities=new java.util.ArrayList<>();
    private static final java.nio.FloatBuffer foregroundData=BufferUtils.createFloatBuffer(MAX_FOREGROUND*8);
    private static Matrix4f frameView,frameProjection;
    private static Vec3 frameCameraPosition;
    private static float framePartialTick;
    private static boolean localPlayerWhollyBehind,localPlayerProtected;
    static void captureCamera(RenderLevelStageEvent e){
        if(BlackHoleClient.center==null||BlackHoleRender.world==null||!BlackHoleClient.failure.isEmpty())return;
        frameView=new Matrix4f(e.getPoseStack().last().pose());
        frameProjection=new Matrix4f(e.getProjectionMatrix());
        frameCameraPosition=e.getCamera().getPosition();
        framePartialTick=e.getPartialTick();
    }
    static void captureEnvironment(RenderLevelStageEvent e){
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();environmentReady=false;
        if(BlackHoleClient.center==null||BlackHoleRender.world==null||!BlackHoleClient.failure.isEmpty())return;
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return;
        try(State state=new State()){
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            if(environment==null||environment.width!=main.width||environment.height!=main.height||environment.isStencilEnabled()!=main.isStencilEnabled()){
                if(environment!=null)environment.destroyBuffers();
                environment=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
                if(main.isStencilEnabled())environment.enableStencil();
                environment.setFilterMode(GL11.GL_LINEAR);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,environment.frameBufferId);
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            environmentReady=true;
        }
    }
    static void release(){
        if(snapshot!=null){snapshot.destroyBuffers();snapshot=null;}
        if(environment!=null){environment.destroyBuffers();environment=null;}environmentReady=false;
        if(foregroundTexture!=0){GlStateManager._deleteTexture(foregroundTexture);foregroundTexture=0;}foregroundBounds.clear();
        frameView=null;frameProjection=null;frameCameraPosition=null;
        BlackHoleParticles.release();
        BlackHoleCoreBloom.release();
    }
    private static void prepareForeground(Minecraft mc,Vec3 camera,Vec3 origin,float partialTick){
        boolean lensAffectsPlayer=lens>.00001f&&BlackHoleRecipe.state().getBool("lensAffectsPlayer");
        localPlayerWhollyBehind=false;localPlayerProtected=false;
        Vec3 eye=camera.add(origin),axis=BlackHoleClient.center.subtract(eye).normalize();
        double massDistance=BlackHoleClient.center.distanceTo(eye);
        var entities=foregroundEntities;entities.clear();
        for(var entity:mc.level.entitiesForRendering())if(entity!=mc.player)entities.add(entity);
        entities.sort(java.util.Comparator.comparingDouble(entity->entity.position().distanceToSqr(camera)));
        entities.add(0,mc.player);foregroundBounds.clear();
        for(var entity:entities){
            if(foregroundBounds.size()==MAX_FOREGROUND)break;
            Vec3 interpolated=entity.getPosition(partialTick);
            var box=entity.getBoundingBox().inflate(.35,.15,.35).move(interpolated.subtract(entity.position())).move(camera.scale(-1));
            var classificationBox=box;
            boolean cameraSubject=!mc.options.getCameraType().isFirstPerson()&&entity==mc.getCameraEntity();
            if(cameraSubject){
                // Collision bounds shrink in swimming/crouching poses and omit
                // animated arms, held equipment and the nearby actor shadow.
                // Depth differences still exclude the terrain in these bounds.
                double reach=Math.max(1.25,Math.max(box.getXsize(),Math.max(box.getYsize(),box.getZsize()))*.75);
                box=box.inflate(reach,1.25,reach);
            }
            double nearProjection=axis.x*(axis.x>=0?classificationBox.minX:classificationBox.maxX)
                +axis.y*(axis.y>=0?classificationBox.minY:classificationBox.maxY)
                +axis.z*(axis.z>=0?classificationBox.minZ:classificationBox.maxZ)-axis.dot(origin);
            // All players share the preference. Other actors keep the original
            // whole-entity rule; a non-player observer camera subject remains
            // protected. Expanded camera coverage still protects animated gear.
            boolean playerEntity=entity instanceof net.minecraft.world.entity.player.Player;
            boolean playerParticipates=playerEntity&&lensAffectsPlayer;
            boolean alwaysProtectSubject=(playerEntity&&!lensAffectsPlayer)||(cameraSubject&&!playerEntity);
            boolean whollyBehind=nearProjection>massDistance+.02;
            if(entity==mc.player){localPlayerWhollyBehind=whollyBehind;localPlayerProtected=!playerParticipates&&(alwaysProtectSubject||!whollyBehind);}
            // An explicit opt-in must not be vetoed by the whole-body mass-plane
            // filter. Keeping that filter here made the toggle inert during normal previews.
            if(playerParticipates||(!alwaysProtectSubject&&whollyBehind))continue;
            foregroundBounds.add(box);
        }
        entities.clear();var data=foregroundData;data.clear();
        for(var box:foregroundBounds){data.put((float)box.minX).put((float)box.minY).put((float)box.minZ).put(1f);data.put((float)box.maxX).put((float)box.maxY).put((float)box.maxZ).put(1f);}
        while(data.hasRemaining())data.put(0f);data.flip();
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        try(UnpackState unpack=new UnpackState()){
        if(foregroundTexture==0){
            foregroundTexture=GlStateManager._genTexture();GlStateManager._bindTexture(foregroundTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,MAX_FOREGROUND*2,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
        }else{
            GlStateManager._bindTexture(foregroundTexture);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,MAX_FOREGROUND*2,1,GL11.GL_RGBA,GL11.GL_FLOAT,data);
        }
        }
    }
    static void render(RenderLevelStageEvent e){
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        if(BlackHoleClient.center==null||BlackHoleRender.world==null||!BlackHoleClient.failure.isEmpty())return;
        if(frameView==null||frameProjection==null||frameCameraPosition==null)return;
        Matrix4f view=frameView,projection=frameProjection;
        Vec3 cameraPosition=frameCameraPosition;float partialTick=framePartialTick;
        frameView=null;frameProjection=null;frameCameraPosition=null;
        // This backend requires the main target; alternate shader pipelines need their own adapter.
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId||main.getDepthTextureId()<0)return;
        long start=System.nanoTime();
        try(State state=new State()){
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            if(snapshot==null||snapshot.width!=main.width||snapshot.height!=main.height||snapshot.isStencilEnabled()!=main.isStencilEnabled()){
                if(snapshot!=null)snapshot.destroyBuffers();BlackHoleParticles.release();
                snapshot=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
                if(main.isStencilEnabled())snapshot.enableStencil();
                snapshot.setFilterMode(GL11.GL_LINEAR);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,snapshot.frameBufferId);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Scene copy FBO incomplete");
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            main.bindWrite(true);
            // Camera-relative WORLD axes: the disk and particle orbits must not
            // rotate with the camera. The captured view includes Forge camera roll.
            Matrix4f vp=new Matrix4f(projection).mul(view);
            Matrix4f inverse=new Matrix4f(vp).invert();
            if(!inverse.isFinite())throw new IllegalStateException("Non-invertible world view projection");
            Vec3 origin=BlackHoleCamera.origin(inverse);
            Vec3 worldRelative=BlackHoleClient.center.subtract(cameraPosition);
            Vec3 relative=worldRelative;
            ShaderInstance s=BlackHoleRender.world;BlackHoleRender.material(s,BlackHoleClient.coreColor);
            s.safeGetUniform("ViewProjection").set(vp);s.safeGetUniform("InvViewProjection").set(inverse);
            s.safeGetUniform("CenterRelative").set((float)relative.x,(float)relative.y,(float)relative.z);
            s.safeGetUniform("CameraOriginRelative").set((float)origin.x,(float)origin.y,(float)origin.z);
            s.safeGetUniform("WorldScale").set(scale);s.safeGetUniform("LensStrength").set(lens);
            s.safeGetUniform("LensProbeMode").set(0f);
            boolean environmentValid=environmentReady&&environment!=null&&environment.width==main.width&&environment.height==main.height;
            frameEnvironmentValid=environmentValid;
            environmentReady=false;
            prepareForeground(mc,cameraPosition,origin,partialTick);s.safeGetUniform("ForegroundCount").set(foregroundBounds.size());
            s.safeGetUniform("EnvironmentReady").set(environmentValid?1f:0f);
            s.safeGetUniform("LensRadius").set(lensRadius);
            s.safeGetUniform("HaloStrength").set(halo);s.safeGetUniform("HaloRing").set(haloRing);s.safeGetUniform("HaloDisk").set(haloDisk);s.safeGetUniform("HaloAura").set(haloAura);
            s.safeGetUniform("uSpeed").set(rotation);s.safeGetUniform("uInfall").set(infall);
            s.safeGetUniform("uExposure").set(exposure);s.safeGetUniform("uTilt").set(tilt);

            RenderSystem.setShader(()->s);RenderSystem.setShaderTexture(0,snapshot.getColorTextureId());RenderSystem.setShaderTexture(1,snapshot.getDepthTextureId());
            RenderSystem.setShaderTexture(2,foregroundTexture);
            RenderSystem.setShaderTexture(3,environmentValid?environment.getColorTextureId():snapshot.getColorTextureId());
            RenderSystem.setShaderTexture(4,environmentValid?environment.getDepthTextureId():snapshot.getDepthTextureId());
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
            java.nio.ByteBuffer noLensColor=null,sourceCoverage=null;
            if(probe!=null){
                s.safeGetUniform("LensStrength").set(0f);drawField();BlackHoleCoreBloom.render(s,main,snapshot,vp,relative,origin,null);
                noLensColor=BufferUtils.createByteBuffer(main.width*main.height*4);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,noLensColor);
                Path folder=mc.gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(folder);
                try(NativeImage im=Screenshot.takeScreenshot(main)){im.writeToFile(folder.resolve(probe+"-no-lens.png"));}
                s.safeGetUniform("LensStrength").set(lens);
                s.safeGetUniform("LensProbeMode").set(2f);drawField();
                try(NativeImage im=Screenshot.takeScreenshot(main)){im.writeToFile(folder.resolve(probe+"-source-kind.png"));}
                var subject=probeEntity==null?mc.player:probeEntity;
                var playerBounds=subject.getBoundingBox().inflate(1.2).move(subject.getPosition(partialTick).subtract(subject.position()));
                s.safeGetUniform("ProbeBoundsMin").set((float)(playerBounds.minX-cameraPosition.x),(float)(playerBounds.minY-cameraPosition.y),(float)(playerBounds.minZ-cameraPosition.z));
                s.safeGetUniform("ProbeBoundsMax").set((float)(playerBounds.maxX-cameraPosition.x),(float)(playerBounds.maxY-cameraPosition.y),(float)(playerBounds.maxZ-cameraPosition.z));
                s.safeGetUniform("LensProbeMode").set(1f);drawField();
                sourceCoverage=BufferUtils.createByteBuffer(main.width*main.height*4);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,sourceCoverage);
                try(NativeImage im=Screenshot.takeScreenshot(main)){im.writeToFile(folder.resolve(probe+"-source-coverage.png"));}
                s.safeGetUniform("LensProbeMode").set(0f);
            }
            drawField();BlackHoleCoreBloom.render(s,main,snapshot,vp,relative,origin,probe);BlackHoleClient.worldDraws++;
            java.nio.ByteBuffer fieldColor=null;
            if(probe!=null){
                fieldColor=BufferUtils.createByteBuffer(main.width*main.height*4);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,fieldColor);
                Path folder=mc.gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(folder);
                try(NativeImage im=Screenshot.takeScreenshot(main)){im.writeToFile(folder.resolve(probe+"-field.png"));}
            }
            BlackHoleParticles.render(vp,view,relative,origin);
            lastGlError=GL11.glGetError();
            if(lastGlError!=GL11.GL_NO_ERROR)throw new IllegalStateException("World GL error "+lastGlError);
            if(probe!=null){String name=probe;probe=null;capture(name,main,vp,inverse,relative,origin,cameraPosition,partialTick,fieldColor,noLensColor,sourceCoverage);}
        }catch(Exception ex){BlackHoleClient.failure=ex.toString();System.err.println("[BLACK-HOLE] World disabled: "+ex);}
        cpuMillis=(System.nanoTime()-start)/1e6;
    }
    private static void drawField(){
        BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
    /** Explicit capture of the same frame before/after; not active in normal rendering. */
    private static void capture(String name,RenderTarget main,Matrix4f vp,Matrix4f inverse,Vec3 relative,Vec3 origin,Vec3 cameraPosition,float partialTick,java.nio.ByteBuffer fieldColor,java.nio.ByteBuffer noLensColor,java.nio.ByteBuffer sourceCoverage)throws Exception{
        Path out=Minecraft.getInstance().gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(out);
        int n=main.width*main.height;var before=BufferUtils.createByteBuffer(n*4);var after=BufferUtils.createByteBuffer(n*4);
        var bd=BufferUtils.createFloatBuffer(n);var ad=BufferUtils.createFloatBuffer(n);
        var environmentDepth=BufferUtils.createFloatBuffer(n);
        if(frameEnvironmentValid){
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,environment.frameBufferId);
            GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,environmentDepth);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,snapshot.frameBufferId);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,before);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,bd);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,after);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,ad);
        int changed=0,outside=0,foreground=0,foregroundChanged=0,particlePixels=0;
        int lensForeground=0,lensForegroundChanged=0,lensPixels=0;float depthDifference=0;Vector4f h=new Vector4f();
        int playerPixels=0,playerFrontPixels=0,playerLensChanged=0,playerFrontLensChanged=0;
        int foregroundEntityPixels=0,foregroundEntityChanged=0;
        int lensedPlayerPixels=0,movedLensedPlayerPixels=0;
        int actualEntityDepthPixels=0,actualEntityDepthChangedPixels=0,actualEntityDepthLensChangedPixels=0;
        var player=probeEntity==null?Minecraft.getInstance().player:probeEntity;
        var playerBounds=player==null?null:player.getBoundingBox().inflate(1.2).move(player.getPosition(partialTick).subtract(player.position()));
        Vec3 massRelative=relative.subtract(origin);
        double massDistance=massRelative.length();Vec3 massAxis=massRelative.normalize();
        var params=BlackHoleRecipe.state();
        double radius=Math.max(Math.max(BlackHoleRecipe.outer(params)*1.5,lensRadius),BlackHoleParticles.enabled?BlackHoleParticles.outer*1.02+BlackHoleParticles.size*6:0);
        if(params.get("objectMode").equals("white")){
            if(params.getFloat("jetStrength")>0)radius=Math.max(radius,Math.hypot(params.getFloat("jetLength")+params.getFloat("core")*1.1,params.getFloat("jetWidth")*3));
            if(params.getBool("shellEnabled")&&params.getFloat("shellStrength")>0)radius=Math.max(radius,params.getFloat("shellRadius")+params.getFloat("shellWidth")*3);
            if(params.getFloat("coreGlow")>0)radius=Math.max(radius,params.getFloat("core")*params.getFloat("coreGlowRadius"));
            if(params.getFloat("coreGlow")>0&&params.getFloat("coreEmission")>0)radius=Math.max(radius,params.getFloat("core")*params.getFloat("coreGlowRadius")*5);
        }
        radius*=scale;
        for(int i=0;i<n;i++){
            if((sourceCoverage.get(i*4)&255)>16)lensedPlayerPixels++;
            if((sourceCoverage.get(i*4+1)&255)>16)movedLensedPlayerPixels++;
            depthDifference=Math.max(depthDifference,Math.abs(bd.get(i)-ad.get(i)));
            boolean diff=false;for(int c=0;c<3;c++)if(Math.abs((before.get(i*4+c)&255)-(after.get(i*4+c)&255))>1)diff=true;
            if(diff)changed++;
            boolean lensDiff=false;for(int c=0;c<3;c++)if(Math.abs((noLensColor.get(i*4+c)&255)-(fieldColor.get(i*4+c)&255))>1)lensDiff=true;
            if(lensDiff)lensPixels++;
            boolean particleDiff=false;for(int c=0;c<3;c++)if(Math.abs((fieldColor.get(i*4+c)&255)-(after.get(i*4+c)&255))>1)particleDiff=true;
            if(particleDiff)particlePixels++;
            float x=((i%main.width)+.5f)/main.width*2-1,y=((i/main.width)+.5f)/main.height*2-1;
            h.set(x,y,1,1).mul(inverse);h.div(h.w);Vec3 dir=new Vec3(h.x,h.y,h.z).subtract(origin).normalize();
            if(bd.get(i)<.999999f){
                h.set(x,y,bd.get(i)*2-1,1).mul(inverse);h.div(h.w);
                Vec3 surface=new Vec3(h.x,h.y,h.z);boolean front=surface.subtract(origin).dot(massAxis)<=massDistance;
                boolean entityDepth=!frameEnvironmentValid||bd.get(i)<environmentDepth.get(i)-.0000002f;
                // In the isolated crossing regression all newly written depth is
                // the actor. This check deliberately does not reuse its render
                // bounds or foreground policy, so missed limbs cannot pass.
                if(frameEnvironmentValid&&entityDepth){
                    actualEntityDepthPixels++;
                    boolean fieldDiff=false;
                    for(int c=0;c<3;c++)if(Math.abs((before.get(i*4+c)&255)-(fieldColor.get(i*4+c)&255))>1)fieldDiff=true;
                    if(fieldDiff)actualEntityDepthChangedPixels++;
                    if(lensDiff)actualEntityDepthLensChangedPixels++;
                }
                if(entityDepth&&foregroundBounds.stream().anyMatch(box->box.contains(surface))){foregroundEntityPixels++;if(lensDiff)foregroundEntityChanged++;}
                if(front){lensForeground++;if(lensDiff)lensForegroundChanged++;}
                if(entityDepth&&playerBounds!=null&&playerBounds.contains(surface.add(cameraPosition))){
                    playerPixels++;if(lensDiff)playerLensChanged++;
                    if(front){playerFrontPixels++;if(lensDiff)playerFrontLensChanged++;}
                }
            }
            double b=-massRelative.dot(dir),disc=b*b-massRelative.lengthSqr()+radius*radius;
            if(disc<0){if(diff)outside++;continue;}
            double entry=-b-Math.sqrt(disc);
            if(bd.get(i)<.999999f){
                h.set(x,y,bd.get(i)*2-1,1).mul(inverse);h.div(h.w);
                if(new Vec3(h.x,h.y,h.z).subtract(origin).length()<Math.max(0,entry)-.02){foreground++;if(diff)foregroundChanged++;}
            }
        }
        try(NativeImage im=Screenshot.takeScreenshot(snapshot)){im.writeToFile(out.resolve(name+"-before.png"));}
        if(name.startsWith("motion-")&&frameEnvironmentValid)
            try(NativeImage im=Screenshot.takeScreenshot(environment)){im.writeToFile(out.resolve(name+"-terrain.png"));}
        try(NativeImage im=Screenshot.takeScreenshot(main)){im.writeToFile(out.resolve(name+"-after.png"));}
        JsonObject r=new JsonObject();r.addProperty("name",name);r.addProperty("minecraft","1.20.1");r.addProperty("forge",net.minecraftforge.versions.forge.ForgeVersion.getVersion());
        r.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));r.addProperty("width",main.width);r.addProperty("height",main.height);
        r.addProperty("changedPixels",changed);r.addProperty("outsideChangedPixels",outside);r.addProperty("foregroundPixels",foreground);r.addProperty("foregroundChangedPixels",foregroundChanged);r.addProperty("maxDepthDifference",depthDifference);
        r.addProperty("lensForegroundPixels",lensForeground);r.addProperty("lensForegroundChangedPixels",lensForegroundChanged);r.addProperty("lensChangedPixels",lensPixels);
        r.addProperty("lensModel","angular-environment-360/v4");r.addProperty("massDistance",massDistance);
        boolean lensAffectsPlayer=params.getBool("lensAffectsPlayer");
        r.addProperty("lensAffectsPlayer",lensAffectsPlayer);
        r.addProperty("playerLensActive",lensAffectsPlayer&&lens>.00001f);
        r.addProperty("localPlayerWhollyBehind",localPlayerWhollyBehind);r.addProperty("localPlayerProtected",localPlayerProtected);
        r.add("parameters",new Gson().toJsonTree(params.snapshot()));
        r.add("coreBloom",BlackHoleCoreBloom.metadata());
        if(player!=null){r.add("probeActorPosition",vector(player.getPosition(partialTick)));r.addProperty("probeActorPose",player.getPose().name());r.addProperty("probeActorYaw",player.getYRot());r.addProperty("probeActorPitch",player.getXRot());}
        r.addProperty("playerProtectionScope","all player entities; localPlayer metrics describe the local camera subject");
        r.addProperty("foregroundPolicy",lensAffectsPlayer&&lens>.00001f?"players explicitly participate regardless of mass-plane classification; non-player observer subject protected":"whole-entity; all players and non-player observer subject protected");
        r.addProperty("cameraMode",Minecraft.getInstance().options.getCameraType().name());
        r.addProperty("foregroundEntityPixels",foregroundEntityPixels);r.addProperty("foregroundEntityChangedPixels",foregroundEntityChanged);
        r.addProperty("environmentCapturedBeforeEntities",frameEnvironmentValid);
        r.addProperty("mainStencilEnabled",main.isStencilEnabled());
        r.addProperty("actualEntityDepthPixels",actualEntityDepthPixels);
        r.addProperty("actualEntityDepthChangedPixels",actualEntityDepthChangedPixels);
        r.addProperty("actualEntityDepthLensChangedPixels",actualEntityDepthLensChangedPixels);
        r.addProperty("playerPixels",playerPixels);r.addProperty("playerFrontPixels",playerFrontPixels);
        r.addProperty("playerLensChangedPixels",playerLensChanged);r.addProperty("playerFrontLensChangedPixels",playerFrontLensChanged);
        r.addProperty("lensedPlayerPixels",lensedPlayerPixels);r.addProperty("movedLensedPlayerPixels",movedLensedPlayerPixels);
        r.addProperty("scale",scale);r.addProperty("lens",lens);r.addProperty("rotation",rotation);r.addProperty("infall",infall);r.addProperty("frozenTicks",BlackHoleRender.frozenTime);r.addProperty("exposure",exposure);r.addProperty("tilt",tilt);
        r.addProperty("particlePixels",particlePixels);r.addProperty("particlesEnabled",BlackHoleParticles.enabled);r.addProperty("particleCount",BlackHoleParticles.count);
        r.addProperty("particleSpeed",BlackHoleParticles.speed);r.addProperty("particleDebug",BlackHoleParticles.debug);r.addProperty("particleDraws",BlackHoleParticles.draws);
        r.addProperty("halo",halo);r.addProperty("haloRing",haloRing);r.addProperty("haloDisk",haloDisk);r.addProperty("haloAura",haloAura);
        r.addProperty("camera",cameraPosition.toString());r.addProperty("center",BlackHoleClient.center.toString());r.addProperty("coreColor",String.format("#%06X",BlackHoleClient.coreColor));
        r.addProperty("cameraContract","perspective-eye-from-inverse-vp/v1");r.addProperty("capturedPartialTick",partialTick);
        r.add("cameraOriginRelative",vector(origin));r.add("effectiveCamera",vector(cameraPosition.add(origin)));
        var vpValues=new JsonArray();for(float value:vp.get(new float[16]))vpValues.add(value);r.add("viewProjection",vpValues);
        Vector4f centerClip=new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1).mul(vp);
        var projected=new JsonObject();projected.addProperty("clipW",centerClip.w);
        if(Math.abs(centerClip.w)>1e-8f){
            projected.addProperty("ndcX",centerClip.x/centerClip.w);projected.addProperty("ndcY",centerClip.y/centerClip.w);
            projected.addProperty("pixelX",(centerClip.x/centerClip.w*.5+.5)*main.width);
            projected.addProperty("pixelY",(centerClip.y/centerClip.w*.5+.5)*main.height);
        }
        r.add("projectedCenter",projected);
        r.addProperty("generation",BlackHoleRender.generation);r.addProperty("glError",GL11.glGetError());
        Files.writeString(out.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));
    }
    private static JsonArray vector(Vec3 value){var out=new JsonArray();out.add(value.x);out.add(value.y);out.add(value.z);return out;}
    /** Atlas uploads during reload leave sub-rectangle pixel-unpack offsets.
     * Our tightly packed float buffer must never inherit those offsets or a PBO.
     */
    private static final class UnpackState implements AutoCloseable{
        private static final int[] KEYS={GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_SWAP_BYTES,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES};
        private final int[] previous=new int[KEYS.length];
        private final int buffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        UnpackState(){
            for(int i=0;i<KEYS.length;i++)previous[i]=GL11.glGetInteger(KEYS[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            for(int key:KEYS)GL11.glPixelStorei(key,key==GL11.GL_UNPACK_ALIGNMENT?4:0);
        }
        public void close(){
            for(int i=0;i<KEYS.length;i++)GL11.glPixelStorei(KEYS[i],previous[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);
        }
    }
    private static final class State implements AutoCloseable{
        private static final Field BLEND_CACHE=ObfuscationReflectionHelper.findField(BlendMode.class,"f_85499_");
        final BlendMode previousBlend=readBlendCache();
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),eqr=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),eqa=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final int[] vp=new int[4],textures=new int[6],shaderTextures=new int[6];final float[] mask=new float[4],clear=new float[4];
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final ShaderInstance previous=RenderSystem.getShader();
        final double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,vp);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);for(int i=0;i<6;i++){shaderTextures[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
        public void close(){
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(vp[0],vp[1],vp[2],vp[3]);
            for(int i=0;i<6;i++){RenderSystem.setShaderTexture(i,shaderTextures[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(textures[i]);}GlStateManager._activeTexture(active);
            RenderSystem.setShader(()->previous);GlStateManager._glUseProgram(program);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.depthFunc(func);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(sr,dr,sa,da);GL20.glBlendEquationSeparate(eqr,eqa);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(mask[0]>.5f,mask[1]>.5f,mask[2]>.5f,mask[3]>.5f);
            GL11.glClearColor(clear[0],clear[1],clear[2],clear[3]);GL11.glClearDepth(clearDepth);
            // RenderType's manual GL state and ShaderInstance's cache are independent.
            try{BLEND_CACHE.set(null,previousBlend);}catch(IllegalAccessException ex){throw new IllegalStateException("Cannot restore ShaderInstance blend cache",ex);}
        }
        private static BlendMode readBlendCache(){
            try{return (BlendMode)BLEND_CACHE.get(null);}catch(IllegalAccessException ex){throw new IllegalStateException("Cannot save ShaderInstance blend cache",ex);}
        }
    }
}
