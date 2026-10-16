package dev.spacerift;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.joml.*;
import org.lwjgl.opengl.*;
import java.lang.Math;
import java.nio.file.*;
import java.util.*;

/** World-space planar cuts, not a traversable second dimension. All API calls are render-thread only. */
public final class RiftRenderer {
    private static final Map<UUID,Rift> RIFTS=new LinkedHashMap<>();
    private static ShaderInstance shader;
    private static TextureTarget snapshot;
    private static boolean failed;
    /** Optional external scene FBO adapter. The provider owns/reloads/destroys its texture. */
    @FunctionalInterface public interface InteriorSource {int render(RenderLevelStageEvent event,int width,int height);}
    private static InteriorSource interiorSource;
    private static int interiorTexture;
    public static void interiorSource(InteriorSource source){RenderSystem.assertOnRenderThread();interiorSource=source;}
    private static Object levelIdentity;
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final class Rift {
        Vec3 center;Quaternionf rotation;float scale;RiftParameters p;RiftGeometry geometry;String key="";float fixedTime=Float.NaN;long born;boolean sequence;
        Rift(Vec3 c,Quaternionf q,float s,RiftParameters p){center=c;rotation=new Quaternionf(q);scale=s;this.p=p.copy();born=Minecraft.getInstance().level.getGameTime();}
    }
    public static void put(UUID id,Vec3 center,Quaternionf rotation,float scale,RiftParameters p){
        RenderSystem.assertOnRenderThread();if(!Float.isFinite(scale)||scale<=0||scale>128)throw new IllegalArgumentException("scale must be (0,128]");
        if(!Double.isFinite(center.x)||!Double.isFinite(center.y)||!Double.isFinite(center.z)||!Float.isFinite(rotation.lengthSquared())||rotation.lengthSquared()<1e-8)throw new IllegalArgumentException("Invalid transform");
        if(!RIFTS.containsKey(id)&&RIFTS.size()>=32)throw new IllegalStateException("Rift budget: 32");
        RIFTS.put(id,new Rift(center,new Quaternionf(rotation).normalize(),scale,p));levelIdentity=Minecraft.getInstance().level;
    }
    public static void parameters(UUID id,RiftParameters p){RenderSystem.assertOnRenderThread();if(RIFTS.containsKey(id))RIFTS.get(id).p=p.copy();}
    public static void time(UUID id,float seconds){RenderSystem.assertOnRenderThread();if(!Float.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid time");if(RIFTS.containsKey(id))RIFTS.get(id).fixedTime=seconds;}
    public static void playSequence(UUID id){RenderSystem.assertOnRenderThread();if(RIFTS.containsKey(id)){var r=RIFTS.get(id);r.p.set("u_sequenceEnabled",1);r.p.set("u_sequence",0);r.born=Minecraft.getInstance().level.getGameTime();r.sequence=true;}}
    public static void remove(UUID id){RenderSystem.assertOnRenderThread();RIFTS.remove(id);}
    public static void clear(){RenderSystem.assertOnRenderThread();RIFTS.clear();release();}
    public static void initialize(){var bus=FMLJavaModLoadingContext.get().getModEventBus();bus.addListener(RiftRenderer::shaders);bus.addListener(RiftRenderer::reload);MinecraftForge.EVENT_BUS.register(RiftRenderer.class);}
    private static void shaders(RegisterShadersEvent e){try{e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("spacerift","rift"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;failed=false;});}catch(Exception ex){failed=true;LOG.error("Space Rift shader registration",ex);}}
    private static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)r->RenderSystem.recordRenderCall(RiftRenderer::release));}
    private static void release(){if(snapshot!=null){snapshot.destroyBuffers();snapshot=null;}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();levelIdentity=null;}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
        e.getDispatcher().register(Commands.literal("spacerift")
            .then(Commands.literal("demo").executes(c->{var mc=Minecraft.getInstance();if(mc.player==null)return 0;
                var p=new RiftParameters();var look=mc.player.getLookAngle();var q=new Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f((float)-look.x,(float)-look.y,(float)-look.z));
                clear();put(new UUID(0,1),mc.player.getEyePosition().add(look.scale(6)),q,2.5f,p);return 1;}))
            .then(Commands.literal("sequence").executes(c->{try{var mc=Minecraft.getInstance();if(mc.player==null)return 0;
                String text;try(var in=RiftRenderer.class.getResourceAsStream("/assets/spacerift/presets/sky.json")){text=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
                var p=RiftParameters.fromJson(text);var look=mc.player.getLookAngle();var q=new Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f((float)-look.x,(float)-look.y,(float)-look.z));
                clear();var id=new UUID(0,1);put(id,mc.player.getEyePosition().add(look.scale(16)),q,6,p);playSequence(id);return 1;
            }catch(Exception ex){LOG.error("Space Rift sequence",ex);return 0;}}))
            .then(Commands.literal("load").executes(c->{try{var mc=Minecraft.getInstance();var path=mc.gameDirectory.toPath().resolve("config/space-rift.json");var p=RiftParameters.fromJson(Files.readString(path));for(var r:RIFTS.values())r.p=p.copy();mc.player.displayClientMessage(Component.literal("Space Rift parameters loaded"),false);return 1;}catch(Exception ex){LOG.error("Invalid config/space-rift.json",ex);return 0;}}))
            .then(Commands.literal("off").executes(c->{clear();return 1;})));
    }
    private static float bound(Rift r){var p=r.p;return (p.get("u_length")*.5f+p.get("u_bend")*1.8f+p.get("u_width")*7+p.get("u_branchLength")*2+.6f)*r.scale;}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level!=levelIdentity||mc.player==null||!mc.player.isAlive()){RIFTS.clear();release();levelIdentity=mc.level;return;}
        if(failed||shader==null||RIFTS.isEmpty())return;
        // Snapshot backend explicitly supports vanilla Fast/Fancy. Shaderpack/Fabulous require adapters.
        if(ModList.get().isLoaded("oculus")||mc.options.graphicsMode().get()==net.minecraft.client.GraphicsStatus.FABULOUS)return;
        var main=mc.getMainRenderTarget();if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return;
        Vec3 eye=e.getCamera().getPosition();var visible=new ArrayList<Rift>();
        for(var r:RIFTS.values()){
            if(r.sequence)r.p.set("u_sequence",Math.min(1,(mc.level.getGameTime()-r.born+e.getPartialTick())/160f*r.p.get("u_speed")));
            var p=r.p;if(p.get("u_opacity")<=0||p.get("u_progress")<=0||p.get("u_open")<=0||p.get("u_shrink")>=1)continue;
            float b=bound(r);if(eye.distanceToSqr(r.center)>(192+b)*(192+b))continue;
            if(!e.getFrustum().isVisible(new net.minecraft.world.phys.AABB(r.center.x-b,r.center.y-b,r.center.z-b,r.center.x+b,r.center.y+b,r.center.z+b)))continue;
            visible.add(r);
        }
        if(visible.isEmpty())return;visible.sort(Comparator.comparingDouble((Rift r)->eye.distanceToSqr(r.center)).reversed());
        var vp=new Matrix4f(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose());
        try(var state=new RiftState()){
            interiorTexture=0;
            if(interiorSource!=null&&visible.stream().anyMatch(r->r.p.get("u_interiorMode")>1.5f))interiorTexture=interiorSource.render(e,main.width,main.height);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,state.draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,state.read);
            RenderSystem.viewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
            if(snapshot==null||snapshot.width!=main.width||snapshot.height!=main.height){release();snapshot=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);snapshot.setFilterMode(GL11.GL_LINEAR);}
            RenderSystem.colorMask(true,true,true,true);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
            var previous=new ArrayList<float[]>();boolean copied=false;
            for(var r:visible){
                float[] rect=screenRect(r,eye,vp);boolean overlap=false;for(var prev:previous)if(rect[0]<prev[2]&&rect[2]>prev[0]&&rect[1]<prev[3]&&rect[3]>prev[1])overlap=true;
                if(!copied||overlap){copyScene(main);copied=true;}previous.add(rect);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,state.draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,state.read);
                RenderSystem.viewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
                draw(r,eye,vp,main,e.getPartialTick(),false);
                if(r.p.get("u_opacity")>.98f){RenderSystem.colorMask(false,false,false,false);RenderSystem.depthMask(true);try{draw(r,eye,vp,main,e.getPartialTick(),true);}finally{RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(false);}}
            }
        }catch(Exception ex){failed=true;LOG.error("Space Rift disabled after render failure",ex);}
    }
    private static void copyScene(RenderTarget main){GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,snapshot.frameBufferId);GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);}
    private static float[] screenRect(Rift r,Vec3 eye,Matrix4f vp){
        float[] out={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        float b=bound(r)/r.scale;for(int i=0;i<4;i++){
            var p=new Vector3f((i%2==0?-1:1)*b,(i<2?-1:1)*b,0).rotate(r.rotation).mul(r.scale);
            var c=new Vector4f(p.x+(float)(r.center.x-eye.x),p.y+(float)(r.center.y-eye.y),p.z+(float)(r.center.z-eye.z),1).mul(vp);
            if(c.w<=.001)return new float[]{-100,-100,100,100};float x=c.x/c.w,y=c.y/c.w;out[0]=Math.min(out[0],x);out[1]=Math.min(out[1],y);out[2]=Math.max(out[2],x);out[3]=Math.max(out[3],y);
        }return out;
    }
    private static void draw(Rift r,Vec3 eye,Matrix4f vp,RenderTarget target,float partial,boolean depth){
        var p=r.p;String key=RiftGeometry.key(p);if(!key.equals(r.key)){r.geometry=new RiftGeometry(p);r.key=key;}
        RenderSystem.setShader(()->shader);shader.setSampler("SceneColor",snapshot.getColorTextureId());
        shader.setSampler("InteriorColor",interiorTexture==0?snapshot.getColorTextureId():interiorTexture);
        shader.safeGetUniform("u_hasInterior").set(interiorTexture==0?0f:1f);
        shader.safeGetUniform("ViewProjection").set(vp);shader.safeGetUniform("ScreenSize").set((float)target.width,(float)target.height);
        float time=Float.isFinite(r.fixedTime)?r.fixedTime:p.get("u_time")+(Minecraft.getInstance().level.getGameTime()-r.born+partial)/20f;
        shader.safeGetUniform("u_time").set(time);shader.safeGetUniform("u_depthOnly").set(depth?1f:0f);
        for(var entry:p.values.entrySet())if(!entry.getKey().equals("u_time"))shader.safeGetUniform(entry.getKey()).set(entry.getValue());
        // Actual observer direction in the arbitrarily oriented rift plane.
        // Preview tilt controls are overridden by camera parallax in Minecraft.
        var localEye=new Vector3f((float)(eye.x-r.center.x),(float)(eye.y-r.center.y),(float)(eye.z-r.center.z)).rotate(new Quaternionf(r.rotation).conjugate());
        float eyeZ=Math.max(.15f,Math.abs(localEye.z));
        shader.safeGetUniform("u_viewTiltX").set(Math.max(-1.5f,Math.min(1.5f,localEye.x/eyeZ)));
        shader.safeGetUniform("u_viewTiltY").set(Math.max(-1.5f,Math.min(1.5f,localEye.y/eyeZ)));
        color("u_edgeColor",p.edge);color("u_insideColor",p.inside);shader.apply();
        GL20.glUniform4fv(GL20.glGetUniformLocation(shader.getId(),"u_spine[0]"),r.geometry.spine);
        GL20.glUniform4fv(GL20.glGetUniformLocation(shader.getId(),"u_branchA[0]"),r.geometry.a);
        GL20.glUniform4fv(GL20.glGetUniformLocation(shader.getId(),"u_branchB[0]"),r.geometry.b);
        float b=bound(r)/r.scale;var builder=Tesselator.getInstance().getBuilder();builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
        vertex(builder,r,eye,-b,-b);vertex(builder,r,eye,b,-b);vertex(builder,r,eye,b,b);vertex(builder,r,eye,-b,b);BufferUploader.drawWithShader(builder.end());
    }
    private static void color(String name,String hex){int n=Integer.parseInt(hex.substring(1),16);shader.safeGetUniform(name).set(((n>>16)&255)/255f,((n>>8)&255)/255f,(n&255)/255f);}
    private static void vertex(BufferBuilder b,Rift r,Vec3 eye,float x,float y){var p=new Vector3f(x,y,0).rotate(r.rotation).mul(r.scale);b.vertex(p.x+r.center.x-eye.x,p.y+r.center.y-eye.y,p.z+r.center.z-eye.z).uv(x,y).endVertex();}
}
