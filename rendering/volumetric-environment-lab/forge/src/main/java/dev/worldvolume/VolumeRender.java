package dev.worldvolume;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import java.io.*;
import java.nio.file.*;

/** Finite world media. Never samples an attachment on the active output FBO. */
@Mod.EventBusSubscriber(modid=WorldVolume.ID,value=Dist.CLIENT)
public final class VolumeRender {
    static ShaderInstance shader;
    static TextureTarget snapshot;
    static VolumeRecipe defaults,recipe;
    static boolean enabled=true,failed,frameReady,frozen;
    static String reason="not-loaded";
    static int generation,draws;
    static float fixedTime,actualTime;
    static long clockStart=System.nanoTime();
    static final Matrix4f inverseProjection=new Matrix4f();
    static final Matrix3f inverseRotation=new Matrix3f();
    static final Vector3f inverseTranslation=new Vector3f();
    static final Path CONFIG=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("world-volume.json");

    @Mod.EventBusSubscriber(modid=WorldVolume.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws IOException {
            try {
            var provider=event.getResourceProvider();
            try(var r=provider.getResourceOrThrow(new ResourceLocation(WorldVolume.ID,"volume/schema.json")).openAsReader()){VolumeRecipe.schema=JsonParser.parseReader(r).getAsJsonObject();}
            try(var r=provider.getResourceOrThrow(new ResourceLocation(WorldVolume.ID,"volume/schema-v1.json")).openAsReader()){VolumeRecipe.schemaV1=JsonParser.parseReader(r).getAsJsonObject();}
            try(var r=provider.getResourceOrThrow(new ResourceLocation(WorldVolume.ID,"volume/contract.json")).openAsReader()){VolumeRecipe.contract=JsonParser.parseReader(r).getAsJsonObject();}
            VolumeRecipe loaded;
            try(var r=provider.getResourceOrThrow(new ResourceLocation(WorldVolume.ID,"volume/default.json")).openAsReader()){loaded=VolumeRecipe.read(r);}
            final VolumeRecipe newDefault=loaded;
            event.registerShader(new ShaderInstance(provider,new ResourceLocation(WorldVolume.ID,"world_volume"),DefaultVertexFormat.POSITION),s->{
                shader=s;defaults=newDefault;if(recipe==null)recipe=defaults;
                generation++;failed=false;release();
                try{if(Files.exists(CONFIG))loadConfig();}catch(Exception e){System.err.println("[WORLDVOLUME] Invalid config kept previous recipe: "+e);}
                reason="ready";System.out.println("[WORLDVOLUME] Shader loaded generation="+generation+" renderer="+GL11.glGetString(GL11.GL_RENDERER));
            });
            } catch (Exception e) { shader=null;failed=true;reason="shader-load-failed";release();System.err.println("[WORLDVOLUME] Custom resource load disabled volume pass: "+e);VolumeValidation.fail(e); }
        }
        @SubscribeEvent public static void reloadListeners(RegisterClientReloadListenersEvent event){
            event.registerReloadListener((ResourceManagerReloadListener)manager->RenderSystem.recordRenderCall(VolumeRender::release));
        }
    }
    static void loadConfig() throws IOException {try(var reader=Files.newBufferedReader(CONFIG)){VolumeRecipe loaded=VolumeRecipe.read(reader);recipe=loaded;}}
    static void upgradeV1Config() throws IOException {try(var reader=Files.newBufferedReader(CONFIG)){VolumeRecipe loaded=VolumeRecipe.upgradeV1(reader);recipe=loaded;}}
    static void release(){if(snapshot!=null){snapshot.destroyBuffers();snapshot=null;}}
    static float time(){return frozen?fixedTime:fixedTime+(System.nanoTime()-clockStart)/1_000_000_000f;}
    static void setTime(float seconds){fixedTime=Math.max(0,seconds);clockStart=System.nanoTime();frozen=true;}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("worldvolume")
            .then(Commands.literal("on").executes(c->{enabled=true;return say("Enabled");}))
            .then(Commands.literal("off").executes(c->{enabled=false;return say("Disabled");}))
            .then(Commands.literal("reload").executes(c->{try{loadConfig();failed=false;return say("Loaded "+CONFIG);}catch(Exception e){return say("Rejected config: "+e.getMessage());}}))
            .then(Commands.literal("upgrade-v1").executes(c->{try{upgradeV1Config();return say("Legacy recipe explicitly upgraded for this session; use /2 config for reload.");}catch(Exception e){return say("Rejected legacy recipe: "+e.getMessage());}}))
            .then(Commands.literal("time").then(Commands.argument("seconds",FloatArgumentType.floatArg(0)).executes(c->{setTime(FloatArgumentType.getFloat(c,"seconds"));return say("Frozen Time="+fixedTime);})))
            .then(Commands.literal("freeze").executes(c->{setTime(time());return say("Frozen Time="+fixedTime);}))
            .then(Commands.literal("play").executes(c->{fixedTime=time();clockStart=System.nanoTime();frozen=false;return say("Playing from Time="+fixedTime);}))
            .then(Commands.literal("status").executes(c->say(reason+"; generation="+generation+"; draws="+draws+"; Time="+time()))));
    }
    private static int say(String text){Minecraft.getInstance().gui.getChat().addMessage(Component.literal("[WorldVolume] "+text));return 1;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){release();frameReady=false;}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.START)frameReady=false;}
    @SubscribeEvent public static void stage(RenderLevelStageEvent event){
        // BEFORE/AFTER_LEVEL's pose is restored. AFTER_SKY still has the actual
        // level camera basis, including view bob and camera effects.
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){
            inverseProjection.set(event.getProjectionMatrix()).invert();
            Matrix4f invView=new Matrix4f(event.getPoseStack().last().pose()).invert();
            inverseRotation.set(invView);invView.getTranslation(inverseTranslation);
            frameReady=inverseProjection.isFinite()&&inverseRotation.isFinite()&&inverseTranslation.isFinite();return;
        }
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();
        if(!enabled||failed||shader==null||recipe==null||!frameReady||mc.level==null){if(!failed)reason="disabled-or-no-camera";return;}
        // Unknown framegraph ownership is deliberately handed back to renderer.
        if(ModList.get().isLoaded("oculus")||ModList.get().isLoaded("embeddium")||ModList.get().isLoaded("rubidium")){reason="unsupported-render-owner";return;}
        if(event.getCamera().getFluidInCamera()!=FogType.NONE){reason="camera-submerged";return;}
        RenderTarget main=mc.getMainRenderTarget();
        if(main.getDepthTextureId()<0||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId){reason="unexpected-framebuffer";return;}
        State state=new State();
        boolean capture=VolumeValidation.wantCapture();
        try(state){
            if(snapshot==null||snapshot.width!=main.width||snapshot.height!=main.height){release();snapshot=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);snapshot.setFilterMode(GL11.GL_NEAREST);}
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,snapshot.frameBufferId);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Snapshot framebuffer incomplete");
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            main.bindWrite(true);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
            shader.safeGetUniform("InverseProjection").set(inverseProjection);
            shader.safeGetUniform("InverseViewRotation").set(inverseRotation);
            shader.safeGetUniform("InverseViewTranslation").set(inverseTranslation);
            actualTime=time();shader.safeGetUniform("Time").set(actualTime);
            recipe.upload(shader,event.getCamera().getPosition());
            shader.setSampler("SceneSampler",snapshot.getColorTextureId());shader.setSampler("DepthSampler",snapshot.getDepthTextureId());
            RenderSystem.setShader(()->shader);
            BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION);
            b.vertex(-1,-1,0).endVertex();b.vertex(3,-1,0).endVertex();b.vertex(-1,3,0).endVertex();
            int query=VolumeValidation.beginGpu();
            try{shader.apply();if(capture)VolumeValidation.drawBindings(shader.getId(),snapshot,main);BufferUploader.draw(b.end());draws++;reason="drawn";}
            finally{VolumeValidation.endGpu(query);shader.clear();}
            if(capture)VolumeValidation.capture(snapshot,main,event.getCamera().getPosition());
        }catch(Exception e){failed=true;reason="draw-failed";e.printStackTrace();VolumeValidation.fail(e);}
        if(capture)VolumeValidation.restored(state.matches());
    }
    /** Both real GL bindings and Minecraft's texture/program caches are restored. */
    static final class State implements AutoCloseable {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int[] viewport=new int[4],bound=new int[12],textures=new int[12];
        final int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),arrayBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),elementBuffer=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final int srcRGB=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRGB=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final boolean[] color=new boolean[4];final ShaderInstance previous=RenderSystem.getShader();
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);var mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<4;i++)color[i]=mask.get(i)!=0;for(int i=0;i<12;i++){textures[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);bound[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
        public void close(){
            BufferUploader.invalidate();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,arrayBuffer);GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,elementBuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<12;i++){RenderSystem.setShaderTexture(i,textures[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(bound[i]);}GlStateManager._activeTexture(active);RenderSystem.setShader(()->previous);GlStateManager._glUseProgram(program);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.blendFuncSeparate(srcRGB,dstRGB,srcA,dstA);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(color[0],color[1],color[2],color[3]);
        }
        boolean matches(){
            int[] vp=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,vp);
            boolean ok=draw==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&read==GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)&&active==GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)&&program==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)&&java.util.Arrays.equals(viewport,vp)&&depth==GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&write==GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&blend==GL11.glIsEnabled(GL11.GL_BLEND)&&cull==GL11.glIsEnabled(GL11.GL_CULL_FACE)&&scissor==GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            ok &= vao==GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)&&arrayBuffer==GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)&&elementBuffer==GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            ok &= srcRGB==GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)&&dstRGB==GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)&&srcA==GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)&&dstA==GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)&&previous==RenderSystem.getShader();
            var mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<4;i++)ok &=(mask.get(i)!=0)==color[i];
            for(int i=0;i<12;i++){ok &=RenderSystem.getShaderTexture(i)==textures[i];GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);ok &=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==bound[i];}GlStateManager._activeTexture(active);
            return ok;
        }
    }
}
