package dev.inkrealm;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.joml.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.lang.Math;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** A fixed world-space gate and independent view-dependent procedural scene. */
public final class InkClient {
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    static ShaderInstance landscape,aperture;
    static TextureTarget scene,before;
    static Vec3 center,normal,right;
    static final float HW=5f,HH=3.5f;
    static int width=768,generation,draws,poll,stateErrors;
    static long opened;
    static float fixedTime=-1;
    static boolean failed,injectError,blocked;
    static boolean inside;
    static float previousSide=9;
    static int entries,exits;
    static String error="",capture,nonce="";
    static Path root(){return Minecraft.getInstance().gameDirectory.toPath().resolve("inkrealm-lab");}

    public static void initialize(){
        var bus=FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(InkClient::shaders);bus.addListener(InkClient::reload);
        MinecraftForge.EVENT_BUS.register(InkClient.class);
        blocked=ModList.get().isLoaded("oculus")||ModList.get().isLoaded("embeddium")||ModList.get().isLoaded("rubidium");
    }
    static void shaders(RegisterShadersEvent e){
        try{
            e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(InkRealm.ID,"landscape"),DefaultVertexFormat.POSITION_TEX),s->{landscape=s;generation++;failed=false;});
            e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(InkRealm.ID,"aperture"),DefaultVertexFormat.POSITION_TEX),s->aperture=s);
        }catch(Exception ex){failed=true;error=ex.toString();LOG.error("INK_REALM shader loading failed",ex);}
    }
    static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)r->RenderSystem.recordRenderCall(InkClient::release));}
    static void release(){if(scene!=null){scene.destroyBuffers();scene=null;}if(before!=null){before.destroyBuffers();before=null;}}
    static void open(){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        Vec3 dir=mc.player.getLookAngle();dir=new Vec3(dir.x,0,dir.z).normalize();
        if(dir.lengthSqr()<.5)dir=new Vec3(0,0,-1);
        normal=dir.scale(-1);right=new Vec3(0,1,0).cross(normal);
        center=mc.player.getEyePosition().add(dir.scale(9)).add(0,.25,0);
        opened=mc.level.getGameTime();fixedTime=-1;failed=false;error="";inside=false;previousSide=9;
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
        e.getDispatcher().register(Commands.literal("inkrealm")
            .then(Commands.literal("activate").executes(c->{open();return 1;}))
            .then(Commands.literal("off").executes(c->{center=null;inside=false;release();return 1;}))
            .then(Commands.literal("status").executes(c->{var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal("Ink Realm draws="+draws+" shader="+generation+" stateErrors="+stateErrors+" error="+error),false);return 1;})));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){center=null;inside=false;release();}
    @SubscribeEvent public static void world(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel().isClientSide()){center=null;inside=false;RenderSystem.recordRenderCall(InkClient::release);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(center!=null&&(mc.level==null||mc.player==null||!mc.player.isAlive())){center=null;inside=false;release();}
        if(++poll%10!=0)return;
        try{
            Files.createDirectories(root());Path request=root().resolve("request.json");
            if(Files.isRegularFile(request)){
                var r=JsonParser.parseString(Files.readString(request)).getAsJsonObject();Files.delete(request);
                nonce=r.has("nonce")?r.get("nonce").getAsString():"";
                String action=r.get("action").getAsString();
                switch(action){
                    case "open"->open();
                    case "off"->{center=null;inside=false;release();}
                    case "capture"->{capture=r.get("name").getAsString();if(!capture.matches("[a-z0-9_-]{1,60}"))throw new IllegalArgumentException("Invalid capture name");}
                    case "time"->fixedTime=r.get("value").getAsFloat();
                    case "resolution"->{width=Math.max(128,Math.min(1536,r.get("value").getAsInt()));release();}
                    case "reload"->mc.reloadResourcePacks();
                    case "inject_error"->injectError=true;
                    case "resume"->{failed=false;error="";}
                    default->throw new IllegalArgumentException("Unknown action "+action);
                }
            }
            writeStatus();
        }catch(Exception ex){error=ex.toString();LOG.error("INK_REALM request failed",ex);}
    }
    static void writeStatus()throws Exception{
        var mc=Minecraft.getInstance();JsonObject o=new JsonObject();
        o.addProperty("nonce",nonce);o.addProperty("enabled",center!=null);o.addProperty("failed",failed);o.addProperty("error",error);o.addProperty("blockedRenderer",blocked);
        o.addProperty("draws",draws);o.addProperty("shaderGeneration",generation);o.addProperty("stateErrors",stateErrors);
        o.addProperty("inside",inside);o.addProperty("entries",entries);o.addProperty("exits",exits);
        if(center!=null){o.add("portalCenter",JSON.toJsonTree(new double[]{center.x,center.y,center.z}));o.add("portalNormal",JSON.toJsonTree(new double[]{normal.x,normal.y,normal.z}));}
        o.addProperty("minecraft","1.20.1");o.addProperty("forge",net.minecraftforge.versions.forge.ForgeVersion.getVersion());
        o.addProperty("world",mc.getSingleplayerServer()==null?"":mc.getSingleplayerServer().getWorldData().getLevelName());
        o.addProperty("center",center==null?"":center.toString());o.addProperty("player",mc.player==null?"":mc.player.position().toString());
        Files.writeString(root().resolve("status.json"),JSON.toJson(o));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES||center==null||failed||blocked||landscape==null||aperture==null)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        RenderTarget main=mc.getMainRenderTarget();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return;
        Vec3 eyeWorld=e.getCamera().getPosition(),delta=eyeWorld.subtract(center);
        Vector3f eye=new Vector3f((float)delta.dot(right),(float)delta.y,(float)delta.dot(normal));
        boolean inOpening=eye.x*eye.x/(HW*HW)+eye.y*eye.y/(HH*HH)<.94*.94;
        if(!inside&&previousSide>.15f&&eye.z<=.15f&&inOpening){inside=true;entries++;mc.player.displayClientMessage(Component.literal("山河入梦 · 原路穿回入口，或 /inkrealm off 退出"),true);}
        else if(inside&&previousSide<=.15f&&eye.z>.15f&&inOpening){inside=false;exits++;mc.player.displayClientMessage(Component.literal("已回到主世界"),true);}
        previousSide=eye.z;
        if(!inside&&(eye.z<.12f||eye.z>160))return;
        Vec3 rel=center.subtract(eyeWorld);
        Matrix4f vp=new Matrix4f(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose());
        float time=fixedTime>=0?fixedTime:(mc.level.getGameTime()-opened+e.getPartialTick())/20f;
        String requested=capture;capture=null;JsonObject result=null;
        State state=new State();
        try(state){
            prepareCommon();
            if(requested!=null){ensureBefore(main);copyBefore(main);}
            renderScene(eye,time,vp,main);
            if(injectError){injectError=false;throw new IllegalStateException("Intentional one-shot restoration test");}
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,state.draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,state.read);
            RenderSystem.viewport(state.vp[0],state.vp[1],state.vp[2],state.vp[3]);
            if(state.srgb)GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(true);
            RenderSystem.setShader(()->aperture);RenderSystem.setShaderTexture(0,scene.getColorTextureId());
            aperture.safeGetUniform("ViewProjection").set(vp);aperture.safeGetUniform("SceneTime").set(time);
            aperture.safeGetUniform("Interior").set(inside?1:0);
            var b=Tesselator.getInstance().getBuilder();
            if(inside){b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();}
            else {b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);gateVertex(b,rel,-HW,-HH,0,0);gateVertex(b,rel,HW,-HH,1,0);gateVertex(b,rel,HW,HH,1,1);gateVertex(b,rel,-HW,HH,0,1);}
            BufferUploader.drawWithShader(b.end());draws++;
            if(requested!=null)result=probe(requested,main,vp,rel,eye,time);
            if(draws==1)LOG.info("INK_REALM first world draw {}x{} scene={}x{}",main.width,main.height,scene.width,scene.height);
        }catch(Exception ex){failed=true;error=ex.toString();LOG.error("INK_REALM effect disabled; incoming state restored",ex);}
        var mismatches=state.differences();stateErrors+=mismatches.size();
        if(result!=null){
            result.add("stateMismatches",JSON.toJsonTree(mismatches));result.addProperty("glErrorAfterRestore",GL11.glGetError());
            try{Files.writeString(root().resolve("captures/"+requested+".json"),JSON.toJson(result));writeStatus();}catch(Exception ex){LOG.error("INK_REALM evidence save failed",ex);}
        }
    }
    static void prepareCommon(){
        GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        RenderSystem.colorMask(true,true,true,true);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.depthMask(true);GL11.glDepthRange(0,1);
    }
    static void renderScene(Vector3f eye,float time,Matrix4f parentVP,RenderTarget main){
        int height=inside?Math.round((float)width*main.height/main.width):Math.round(width*HH/HW);
        if(scene==null||scene.width!=width||scene.height!=height){if(scene!=null)scene.destroyBuffers();scene=new TextureTarget(width,height,true,Minecraft.ON_OSX);scene.setFilterMode(GL11.GL_LINEAR);}
        scene.setClearColor(.89f,.905f,.845f,1);scene.clear(Minecraft.ON_OSX);scene.bindWrite(true);scene.checkStatus();
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_ALWAYS);RenderSystem.depthMask(true);
        float n=.05f,z=eye.z;
        Matrix4f worldToLocal=new Matrix4f().m00((float)right.x).m10((float)right.y).m20((float)right.z).m02((float)normal.x).m12((float)normal.y).m22((float)normal.z);
        Matrix4f p=inside?new Matrix4f(parentVP).mul(new Matrix4f(worldToLocal).invert()):new Matrix4f().setFrustum((-HW-eye.x)*n/z,(HW-eye.x)*n/z,(-HH-eye.y)*n/z,(HH-eye.y)*n/z,n,200);
        RenderSystem.setShader(()->landscape);
        landscape.safeGetUniform("Eye").set(eye.x,eye.y,eye.z);landscape.safeGetUniform("HalfSize").set(HW,HH);
        landscape.safeGetUniform("SceneProjection").set(p);landscape.safeGetUniform("SceneTime").set(time);
        landscape.safeGetUniform("Interior").set(inside?1:0);landscape.safeGetUniform("RayMatrix").set(new Matrix4f(worldToLocal).mul(new Matrix4f(parentVP).invert()));
        var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();BufferUploader.drawWithShader(b.end());
    }
    static void gateVertex(BufferBuilder b,Vec3 c,float x,float y,float u,float v){b.vertex(c.x+right.x*x,c.y+y,c.z+right.z*x).uv(u,v).endVertex();}
    static void ensureBefore(RenderTarget main){if(before==null||before.width!=main.width||before.height!=main.height){if(before!=null)before.destroyBuffers();before=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);}}
    static void copyBefore(RenderTarget main){
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,before.frameBufferId);
        GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
    }
    static JsonObject probe(String name,RenderTarget main,Matrix4f vp,Vec3 rel,Vector3f eye,float time)throws Exception{
        Path out=root().resolve("captures");Files.createDirectories(out);
        try(NativeImage i=Screenshot.takeScreenshot(before)){i.writeToFile(out.resolve(name+"-before.png"));}
        try(NativeImage i=Screenshot.takeScreenshot(main)){i.writeToFile(out.resolve(name+"-world.png"));}
        try(NativeImage i=Screenshot.takeScreenshot(scene)){i.writeToFile(out.resolve(name+"-scene.png"));}
        int count=main.width*main.height;
        FloatBuffer db=BufferUtils.createFloatBuffer(count),da=BufferUtils.createFloatBuffer(count);
        ByteBuffer cb=BufferUtils.createByteBuffer(count*4),ca=BufferUtils.createByteBuffer(count*4);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,before.frameBufferId);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,db);GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,cb);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,da);GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,ca);
        Matrix4f inv=new Matrix4f(vp).invert();Vector4f ray=new Vector4f(),clip=new Vector4f();
        int changed=0,outside=0,foreground=0,foregroundChanged=0,depthWrites=0;float maxEntranceError=0;
        for(int i=0;i<count;i++){
            boolean diff=false;for(int c=0;c<4;c++)if(cb.get(i*4+c)!=ca.get(i*4+c))diff=true;
            if(diff)changed++;
            if(inside)continue;
            ray.set(((i%main.width)+.5f)/main.width*2-1,((i/main.width)+.5f)/main.height*2-1,1,1).mul(inv);ray.div(ray.w);
            double denom=ray.x*normal.x+ray.y*normal.y+ray.z*normal.z;
            double t=rel.dot(normal)/denom;
            double x=ray.x*t-rel.x,y=ray.y*t-rel.y,z=ray.z*t-rel.z;
            double qx=(x*right.x+y*right.y+z*right.z)/HW,qy=y/HH,r=Math.sqrt(qx*qx+qy*qy);
            if((t<=0||r>1.02)&&diff)outside++;
            if(t>0&&r<.94){
                clip.set((float)(ray.x*t),(float)(ray.y*t),(float)(ray.z*t),1).mul(vp);float entry=clip.z/clip.w*.5f+.5f;
                if(db.get(i)<entry-1e-5){foreground++;if(diff)foregroundChanged++;}
                else if(diff){depthWrites++;maxEntranceError=Math.max(maxEntranceError,Math.abs(da.get(i)-entry));}
            }
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,scene.frameBufferId);
        FloatBuffer sd=BufferUtils.createFloatBuffer(scene.width*scene.height);GL11.glReadPixels(0,0,scene.width,scene.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,sd);
        int geometry=0;Set<Integer> bins=new HashSet<>();for(int i=0;i<sd.capacity();i++){float d=sd.get(i);if(d<.999999){geometry++;bins.add((int)(d*1000000));}}
        JsonObject o=new JsonObject();o.addProperty("case",name);o.addProperty("scope","Actual Minecraft world-space gate at AFTER_BLOCK_ENTITIES; independent procedural landscape");
        o.addProperty("viewMode",inside?"interior":"window");o.addProperty("entries",entries);o.addProperty("exits",exits);
        o.addProperty("minecraft","1.20.1");o.addProperty("forge",net.minecraftforge.versions.forge.ForgeVersion.getVersion());o.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));
        o.addProperty("width",main.width);o.addProperty("height",main.height);o.addProperty("sceneWidth",scene.width);o.addProperty("sceneHeight",scene.height);
        o.addProperty("changedPixels",changed);o.addProperty("outsideChangedPixels",outside);o.addProperty("foregroundPixels",foreground);o.addProperty("foregroundChangedPixels",foregroundChanged);
        o.addProperty("entranceDepthPixels",depthWrites);o.addProperty("maxEntranceDepthError",maxEntranceError);o.addProperty("sceneGeometryPixels",geometry);o.addProperty("sceneDepthBins",bins.size());
        o.addProperty("shaderGeneration",generation);o.addProperty("time",time);o.add("eye",JSON.toJsonTree(new float[]{eye.x,eye.y,eye.z}));o.addProperty("draws",draws);
        o.addProperty("fboStatus",GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER));o.addProperty("glError",GL11.glGetError());return o;
    }
    static final class State implements AutoCloseable {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int[] vp=new int[4],bound=new int[2],tex=new int[2];final float[] mask=new float[4],clear=new float[4];final double[] range=new double[2];
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST),srgb=GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),eqr=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),eqa=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final ShaderInstance shader=RenderSystem.getShader();final float[] color=RenderSystem.getShaderColor().clone();
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,vp);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);GL11.glGetDoublev(GL11.GL_DEPTH_RANGE,range);for(int i=0;i<2;i++){tex[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);bound[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
        public void close(){
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(vp[0],vp[1],vp[2],vp[3]);
            for(int i=0;i<2;i++){RenderSystem.setShaderTexture(i,tex[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(bound[i]);}GlStateManager._activeTexture(active);
            RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.depthFunc(func);GL11.glDepthRange(range[0],range[1]);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(sr,dr,sa,da);GL20.glBlendEquationSeparate(eqr,eqa);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();toggle(GL11.GL_SCISSOR_TEST,scissor);toggle(GL11.GL_STENCIL_TEST,stencil);toggle(GL30.GL_FRAMEBUFFER_SRGB,srgb);
            RenderSystem.colorMask(mask[0]>.5,mask[1]>.5,mask[2]>.5,mask[3]>.5);GlStateManager._clearColor(clear[0],clear[1],clear[2],clear[3]);
        }
        static void toggle(int cap,boolean value){if(value)GL11.glEnable(cap);else GL11.glDisable(cap);}
        List<String> differences(){List<String> out=new ArrayList<>();int[] actual=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,actual);
            if(!Arrays.equals(actual,vp))out.add("viewport");if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=draw)out.add("drawFbo");if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=read)out.add("readFbo");
            if(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)!=write)out.add("depthWrite");if(GL11.glGetInteger(GL11.GL_DEPTH_FUNC)!=func)out.add("depthFunc");
            if(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)!=depth)out.add("depthEnable");if(GL11.glIsEnabled(GL11.GL_BLEND)!=blend)out.add("blend");if(GL11.glIsEnabled(GL11.GL_CULL_FACE)!=cull)out.add("cull");if(GL11.glIsEnabled(GL11.GL_STENCIL_TEST)!=stencil)out.add("stencil");
            if(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=active)out.add("activeTexture");if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)out.add("program");return out;}
    }
}
