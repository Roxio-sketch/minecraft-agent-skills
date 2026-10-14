package dev.localmaterial;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Research backend: one expanding terrain-only sphere, no block/world modification. */
@Mod(LocalMaterial.ID)
public final class LocalMaterial {
    public static final String ID="localmaterial";
    public LocalMaterial(){net.minecraftforge.fml.DistExecutor.safeRunWhenOn(Dist.CLIENT,()->Client::init);}
    public static final class Client {
        private static ShaderInstance shader;
        private static TextureTarget snapshot;
        static Vec3 center;
        private static long startTick;
        static int material;
        static boolean disabledByRenderer;
        private static boolean failed,held,probe;
        private static boolean terrainEnabled=true;
        private static int draws,generation,lastGlError;
        private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
        public static void init(){
            var bus=FMLJavaModLoadingContext.get().getModEventBus();
            bus.addListener(Client::registerShaders);
            bus.addListener(Client::registerReload);
            MinecraftForge.EVENT_BUS.register(Client.class);
            // Deliberate prototype gate; an unknown renderer must not silently be declared compatible.
            disabledByRenderer=ModList.get().isLoaded("oculus")||ModList.get().isLoaded("embeddium")||ModList.get().isLoaded("rubidium");
        }
        private static void registerShaders(RegisterShadersEvent e) {
            try {e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(ID,"region"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;generation++;failed=false;});}
            catch(java.io.IOException ex){shader=null;failed=true;LOG.error("Local material shader unavailable",ex);}
            TranslucentMaterial.register(e);
        }
        private static void registerReload(RegisterClientReloadListenersEvent e){
            e.registerReloadListener((ResourceManagerReloadListener)r->RenderSystem.recordRenderCall(Client::release));
        }
        private static void release(){if(snapshot!=null){snapshot.destroyBuffers();snapshot=null;}TranslucentMaterial.reset();}
        static float age(){var mc=Minecraft.getInstance();return mc.level==null?0:held?8f:(mc.level.getGameTime()-startTick+mc.getFrameTime())/20f;}
        static float radius(){float a=age();return 20f*Math.min(1,Math.max(0,a/4f))*Math.min(1,Math.max(0,(20-a)/4f));}
        @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
            e.getDispatcher().register(Commands.literal("localmaterial")
                .then(TranslucentMaterial.commands())
                .then(Commands.literal("terrain").then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{terrainEnabled=com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled");return 1;})))
                .then(Commands.literal("off").executes(c->{center=null;held=false;release();return 1;}))
                .then(Commands.literal("hold").then(Commands.argument("material",IntegerArgumentType.integer(0,2)).executes(c->{
                    var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||disabledByRenderer)return 0;
                    center=mc.player.position();startTick=mc.level.getGameTime();material=IntegerArgumentType.getInteger(c,"material");held=true;failed=false;return 1;
                })))
                .then(Commands.literal("probe").executes(c->{probe=true;return 1;}))
                .then(Commands.literal("sphere").then(Commands.argument("material",IntegerArgumentType.integer(0,2)).executes(c->{
                    var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return 0;
                    if(disabledByRenderer){mc.player.displayClientMessage(Component.literal("Local material prototype disabled: renderer adapter unvalidated."),false);return 0;}
                    center=mc.player.position();startTick=mc.level.getGameTime();material=IntegerArgumentType.getInteger(c,"material");held=false;failed=false;
                    LOG.info("LOCAL_MATERIAL start center={} material={}",center,material);return 1;
                })))
                .then(Commands.literal("status").executes(c->{var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal("Local material draws="+draws+" generation="+generation+" gl="+lastGlError+" blocked="+disabledByRenderer+" failed="+failed),false);return 1;})));
        }
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){center=null;release();}
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
            if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
            if(center!=null&&(mc.level==null||mc.player==null||!mc.player.isAlive()||(!held&&mc.level.getGameTime()-startTick>400))){center=null;held=false;release();}
            if(snapshot!=null&&mc.level==null)release();
        }
        @SubscribeEvent public static void world(net.minecraftforge.event.level.LevelEvent.Unload e){
            if(e.getLevel().isClientSide()){center=null;RenderSystem.recordRenderCall(Client::release);}
        }
        @SubscribeEvent public static void render(RenderLevelStageEvent e){
            if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS||center==null||shader==null||failed||disabledByRenderer||!terrainEnabled)return;
            var mc=Minecraft.getInstance();if(mc.level==null)return;
            RenderTarget main=mc.getMainRenderTarget();
            // This backend is only for a verified vanilla main target, before entities/transparency.
            if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId||main.getDepthTextureId()<0)return;
            float age=held?8f:(mc.level.getGameTime()-startTick+e.getPartialTick())/20f;
            float radius=20f*Math.min(1,Math.max(0,age/4f))*Math.min(1,Math.max(0,(20-age)/4f));
            if(radius<=.001f)return;
            try(State state=new State()){
                if(snapshot==null||snapshot.width!=main.width||snapshot.height!=main.height){release();snapshot=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);snapshot.setFilterMode(GL11.GL_NEAREST);}
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,snapshot.frameBufferId);
                if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Snapshot FBO incomplete");
                GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
                main.bindWrite(true);
                RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
                Vec3 relative=center.subtract(e.getCamera().getPosition());
                // Exactly matches LevelRenderer's chunk MODEL_VIEW_MATRIX / PROJECTION_MATRIX pair.
                Matrix4f inv=new Matrix4f(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose()).invert();
                if(!inv.isFinite())throw new IllegalStateException("Non-invertible view projection");
                shader.safeGetUniform("InvViewProjection").set(inv);
                shader.safeGetUniform("CenterRelative").set((float)relative.x,(float)relative.y,(float)relative.z);
                shader.safeGetUniform("Radius").set(radius);shader.safeGetUniform("Time").set(age);
                shader.safeGetUniform("Material").set(material);
                shader.safeGetUniform("FogStart").set(RenderSystem.getShaderFogStart());
                shader.safeGetUniform("FogEnd").set(RenderSystem.getShaderFogEnd());
                shader.safeGetUniform("FogShape").set(RenderSystem.getShaderFogShape().getIndex());
                var fog=RenderSystem.getShaderFogColor();shader.safeGetUniform("FogColor").set(fog[0],fog[1],fog[2],fog[3]);
                RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,snapshot.getColorTextureId());RenderSystem.setShaderTexture(1,snapshot.getDepthTextureId());
                BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
                b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();
                BufferUploader.drawWithShader(b.end());draws++;lastGlError=GL11.glGetError();
                if(probe){probe=false;captureProbe(main,inv,relative,radius,e);}
                if(draws==1)LOG.info("LOCAL_MATERIAL first draw {}x{}, gl={}",main.width,main.height,lastGlError);
            }catch(Exception ex){failed=true;LOG.error("Local material disabled after rendering error",ex);}
        }
        /** Explicit one-frame readback; never runs in normal rendering. Compares actual attachments. */
        private static void captureProbe(RenderTarget main,Matrix4f inverse,Vec3 relative,float radius,RenderLevelStageEvent event)throws Exception{
            int count=main.width*main.height;
            var beforeDepth=org.lwjgl.BufferUtils.createFloatBuffer(count);
            var afterDepth=org.lwjgl.BufferUtils.createFloatBuffer(count);
            var beforeColor=org.lwjgl.BufferUtils.createByteBuffer(count*4);
            var afterColor=org.lwjgl.BufferUtils.createByteBuffer(count*4);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,snapshot.frameBufferId);
            GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,beforeDepth);
            GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,beforeColor);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,afterDepth);
            GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,afterColor);
            int changed=0,outside=0,sky=0;float maxDepth=0;var h=new org.joml.Vector4f();
            for(int i=0;i<count;i++){
                float d=beforeDepth.get(i);maxDepth=Math.max(maxDepth,Math.abs(d-afterDepth.get(i)));
                boolean difference=false;for(int c=0;c<4;c++)if(beforeColor.get(i*4+c)!=afterColor.get(i*4+c))difference=true;
                if(!difference)continue;changed++;if(d>=1){sky++;continue;}
                float x=((i%main.width)+.5f)/main.width*2-1,y=((i/main.width)+.5f)/main.height*2-1;
                h.set(x,y,d*2-1,1).mul(inverse);h.div(h.w);
                double dx=h.x-relative.x,dy=h.y-relative.y,dz=h.z-relative.z;
                if(dx*dx+dy*dy+dz*dz>(radius+.002)*(radius+.002))outside++;
            }
            var out=new com.google.gson.JsonObject();out.addProperty("scope","Minecraft terrain-stage actual framebuffer readback");
            out.addProperty("minecraft","1.20.1");out.addProperty("forge",net.minecraftforge.versions.forge.ForgeVersion.getVersion());
            out.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));out.addProperty("width",main.width);out.addProperty("height",main.height);
            out.addProperty("material",material);out.addProperty("radius",radius);out.addProperty("generation",generation);out.addProperty("draws",draws);
            out.addProperty("changedPixels",changed);out.addProperty("outsideChangedPixels",outside);out.addProperty("skyChangedPixels",sky);
            out.addProperty("maxDepthDifference",maxDepth);out.addProperty("glError",GL11.glGetError());
            out.addProperty("center",center.toString());out.addProperty("camera",event.getCamera().getPosition().toString());
            var folder=Minecraft.getInstance().gameDirectory.toPath().resolve("local-environment-lab/probes");java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.writeString(folder.resolve("probe-"+System.currentTimeMillis()+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out));
            LOG.info("LOCAL_MATERIAL probe {}",out);
        }
        private static final class State implements AutoCloseable {
            final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            final int[] vp=new int[4],bound=new int[2],shaderTex=new int[2];final float[] mask=new float[4];
            final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            final ShaderInstance previous=RenderSystem.getShader();
            State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,vp);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<2;i++){shaderTex[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);bound[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
            public void close(){
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(vp[0],vp[1],vp[2],vp[3]);
                for(int i=0;i<2;i++){RenderSystem.setShaderTexture(i,shaderTex[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(bound[i]);}GlStateManager._activeTexture(active);RenderSystem.setShader(()->previous);
                if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);
                if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
                if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(mask[0]>.5f,mask[1]>.5f,mask[2]>.5f,mask[3]>.5f);
            }
        }
    }
}
