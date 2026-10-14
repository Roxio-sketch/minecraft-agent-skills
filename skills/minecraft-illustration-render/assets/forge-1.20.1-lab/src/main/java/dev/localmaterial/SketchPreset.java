// SPDX-License-Identifier: MIT
package dev.localmaterial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Optional whole-scene illustration preset. The gradient is deliberately screen-space. */
public final class SketchPreset {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static ShaderInstance shader;
    private static ShaderInstance maskShader;
    private static TextureTarget snapshot;
    private static TextureTarget worldDepth;
    private static TextureTarget solidDepth,cutoutDepth;
    private static TextureTarget maskTarget;
    private static boolean enabled,failed,worldDepthReady,solidReady,cutoutReady;
    private static int draws,generation,lastGlError,debugMode;
    private static Preset preset=Preset.defaults();

    public static LiteralArgumentBuilder<CommandSourceStack> commands(){
        return Commands.literal("sketch")
            .then(Commands.literal("on").executes(c->{
                if(LocalMaterial.Client.disabledByRenderer){say("sketch: renderer adapter unvalidated");return 0;}
                if(!load()){return 0;}enabled=true;failed=false;say("sketch: on (One Last Kiss line-art preset)");return 1;
            }))
            .then(Commands.literal("off").executes(c->{off();say("sketch: off");return 1;}))
            .then(Commands.literal("reload").executes(c->{if(!load())return 0;say("sketch: preset reloaded");return 1;}))
            .then(Commands.literal("status").executes(c->{say("sketch enabled="+enabled+" draws="+draws+" generation="+generation+" gl="+lastGlError+" failed="+failed+" debug="+debugMode);return 1;}))
            .then(Commands.literal("debug")
                .then(Commands.literal("0").executes(c->setDebug(0)))
                .then(Commands.literal("1").executes(c->setDebug(1)))
                .then(Commands.literal("2").executes(c->setDebug(2)))
                .then(Commands.literal("3").executes(c->setDebug(3)))
                .then(Commands.literal("4").executes(c->setDebug(4)))
                .then(Commands.literal("5").executes(c->setDebug(5)))
                .then(Commands.literal("6").executes(c->setDebug(6))));
    }
    private static int setDebug(int mode){debugMode=mode;say("sketch debug="+mode);return 1;}
    private static void say(String message){LOG.info("LOCAL_SKETCH {}",message);var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal(message),false);}
    private static Path config(){return Minecraft.getInstance().gameDirectory.toPath().resolve("local-environment-lab/presets/one-last-kiss.json");}
    private static boolean load(){
        try{
            Path path=config();
            preset=Files.exists(path)?Preset.parse(Files.readString(path)):Preset.defaults();
            return true;
        }catch(Exception ex){say("sketch preset invalid: "+ex.getMessage());LOG.warn("Sketch preset load failed",ex);return false;}
    }
    public static void register(RegisterShadersEvent event){
        try{event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(LocalMaterial.ID,"sketch"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;generation++;failed=false;});}
        catch(IOException ex){shader=null;failed=true;LOG.error("Sketch shader unavailable",ex);}
        try{event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(LocalMaterial.ID,"sketch_mask"),DefaultVertexFormat.POSITION_TEX),s->maskShader=s);}
        catch(IOException ex){maskShader=null;failed=true;LOG.error("Sketch cutout-mask shader unavailable",ex);}
    }
    public static void off(){enabled=false;if(RenderSystem.isOnRenderThread())reset();else RenderSystem.recordRenderCall(SketchPreset::reset);}
    public static void reset(){
        worldDepthReady=false;solidReady=false;cutoutReady=false;
        if(snapshot!=null){snapshot.destroyBuffers();snapshot=null;}
        if(worldDepth!=null){worldDepth.destroyBuffers();worldDepth=null;}
        if(solidDepth!=null){solidDepth.destroyBuffers();solidDepth=null;}
        if(cutoutDepth!=null){cutoutDepth.destroyBuffers();cutoutDepth=null;}
        if(maskTarget!=null){maskTarget.destroyBuffers();maskTarget=null;}
    }

    @SubscribeEvent public static void captureWorldDepth(RenderLevelStageEvent event){
        var stage=event.getStage();
        if(stage!=RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS&&stage!=RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS&&stage!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        if(!enabled||shader==null||failed||LocalMaterial.Client.disabledByRenderer)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(Minecraft.useShaderTransparency())return; // Fabulous target/resolve requires its own validation.
        RenderTarget main=mc.getMainRenderTarget();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId||main.getDepthTextureId()<0)return;
        try(State state=new State()){
            if(stage==RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS){
                solidReady=false;cutoutReady=false;worldDepthReady=false;
                if(solidDepth!=null&&(solidDepth.width!=main.width||solidDepth.height!=main.height))reset();
            }
            TextureTarget target;
            if(stage==RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS){
                if(solidDepth==null){solidDepth=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);solidDepth.setFilterMode(GL11.GL_NEAREST);}
                target=solidDepth;
            }else if(stage==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS){
                if(!solidReady)return;
                if(cutoutDepth==null){cutoutDepth=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);cutoutDepth.setFilterMode(GL11.GL_NEAREST);}
                target=cutoutDepth;
            }else{
                if(!solidReady||!cutoutReady)return;
                if(worldDepth==null){worldDepth=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);worldDepth.setFilterMode(GL11.GL_NEAREST);}
                target=worldDepth;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Sketch world-depth FBO incomplete");
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            if(stage==RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)solidReady=true;
            else if(stage==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)cutoutReady=true;
            else worldDepthReady=true;
        }catch(Exception ex){failed=true;worldDepthReady=false;LOG.error("Sketch depth capture failed at "+stage,ex);}
    }

    /** Called by the optional GameRenderer tail hook, after the first-person hand. */
    public static void renderAfterHand(){
        if(!worldDepthReady||!enabled||shader==null||maskShader==null||failed)return;
        worldDepthReady=false;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        RenderTarget main=mc.getMainRenderTarget();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId||main.getDepthTextureId()<0)return;
        try(State state=new State()){
            if(snapshot==null||snapshot.width!=main.width||snapshot.height!=main.height){if(snapshot!=null)snapshot.destroyBuffers();snapshot=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);snapshot.setFilterMode(GL11.GL_NEAREST);}
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,snapshot.frameBufferId);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Sketch color/hand-depth FBO incomplete");
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);
            int mw=Math.max(1,main.width/2),mh=Math.max(1,main.height/2);
            if(maskTarget==null||maskTarget.width!=mw||maskTarget.height!=mh){
                if(maskTarget!=null)maskTarget.destroyBuffers();
                maskTarget=new TextureTarget(mw,mh,false,Minecraft.ON_OSX);maskTarget.setFilterMode(GL11.GL_LINEAR);
            }
            maskTarget.bindWrite(true);
            maskShader.safeGetUniform("TexelSize").set(1f/main.width,1f/main.height);
            maskShader.safeGetUniform("DetailSuppression").set(preset.detailSuppression);
            maskShader.safeGetUniform("LineWeight").set(preset.lineWeight);
            RenderSystem.setShader(()->maskShader);
            RenderSystem.setShaderTexture(0,solidDepth.getDepthTextureId());
            RenderSystem.setShaderTexture(1,cutoutDepth.getDepthTextureId());
            RenderSystem.setShaderTexture(2,worldDepth.getDepthTextureId());
            RenderSystem.setShaderTexture(3,snapshot.getColorTextureId());
            drawTriangle();
            main.bindWrite(true);
            shader.safeGetUniform("TexelSize").set(1f/main.width,1f/main.height);
            for(int i=0;i<5;i++){
                float[] s=preset.stops[i];
                shader.safeGetUniform("Stop"+i).set(s[0],s[1],s[2],s[3]);
            }
            float a=(float)Math.toRadians(preset.angleDeg);
            shader.safeGetUniform("GradientAxis").set((float)Math.cos(a),(float)Math.sin(a));
            shader.safeGetUniform("GradientScale").set(preset.scale);
            shader.safeGetUniform("GradientOffset").set(preset.offset);
            shader.safeGetUniform("EdgeSensitivity").set(preset.edgeSensitivity);
            shader.safeGetUniform("DetailSuppression").set(preset.detailSuppression);
            shader.safeGetUniform("DepthStrength").set(preset.depthStrength);
            shader.safeGetUniform("ShadeAmount").set(preset.shadeAmount);
            shader.safeGetUniform("LineWeight").set(preset.lineWeight);
            shader.safeGetUniform("DebugMode").set((float)debugMode);
            RenderSystem.setShader(()->shader);
            RenderSystem.setShaderTexture(0,snapshot.getColorTextureId());
            RenderSystem.setShaderTexture(1,worldDepth.getDepthTextureId());
            RenderSystem.setShaderTexture(2,snapshot.getDepthTextureId());
            RenderSystem.setShaderTexture(3,solidDepth.getDepthTextureId());
            RenderSystem.setShaderTexture(4,cutoutDepth.getDepthTextureId());
            RenderSystem.setShaderTexture(5,maskTarget.getColorTextureId());
            drawTriangle();draws++;lastGlError=GL11.glGetError();
            if(lastGlError!=GL11.GL_NO_ERROR)throw new IllegalStateException("OpenGL "+lastGlError);
            if(draws==1)LOG.info("LOCAL_SKETCH first draw {}x{}",main.width,main.height);
        }catch(Exception ex){failed=true;LOG.error("Sketch preset disabled after rendering error",ex);}
    }
    private static void drawTriangle(){
        BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    private record Preset(float[][] stops,float angleDeg,float scale,float offset,float edgeSensitivity,float detailSuppression,float depthStrength,float shadeAmount,float lineWeight){
        static Preset defaults(){return new Preset(new float[][]{
            {.95f,.40f,.10f,0f},{.97f,.23f,.31f,.24f},{.81f,.23f,.62f,.49f},{.41f,.34f,.78f,.74f},{.11f,.77f,.88f,1f}
        },0,1,0,.58f,.88f,1f,.08f,1);}
        static Preset parse(String input){
            JsonObject root=JsonParser.parseString(input).getAsJsonObject();
            if(!"olk-sketch/v1".equals(root.get("schema").getAsString()))throw new IllegalArgumentException("schema must be olk-sketch/v1");
            JsonObject gradient=root.getAsJsonObject("gradient");
            var array=gradient.getAsJsonArray("stops");
            if(array.size()!=5)throw new IllegalArgumentException("exactly five stops required");
            float[][] stops=new float[5][4];
            for(int i=0;i<5;i++){
                var entry=array.get(i).getAsJsonObject();float at=bounded(entry.get("at").getAsFloat(),0,1,"stop position");
                String hex=entry.get("color").getAsString();
                if(!hex.matches("#[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("color must be #RRGGBB");
                int rgb=Integer.parseInt(hex.substring(1),16);
                stops[i]=new float[]{((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,at};
                if(i>0&&at<=stops[i-1][3])throw new IllegalArgumentException("stop positions must ascend");
            }
            if(stops[0][3]!=0||stops[4][3]!=1)throw new IllegalArgumentException("first/last stops must be 0/1");
            JsonObject line=root.getAsJsonObject("line");
            return new Preset(stops,
                bounded(gradient.get("angleDeg").getAsFloat(),-180,180,"angle"),
                bounded(gradient.get("scale").getAsFloat(),.25f,3,"scale"),
                bounded(gradient.get("offset").getAsFloat(),-.5f,.5f,"offset"),
                bounded(line.get("edgeSensitivity").getAsFloat(),0,1,"edgeSensitivity"),
                bounded(line.get("detailSuppression").getAsFloat(),0,1,"detailSuppression"),
                bounded(line.get("depthStrength").getAsFloat(),0,1,"depthStrength"),
                bounded(line.get("shadeAmount").getAsFloat(),0,1,"shadeAmount"),
                bounded(line.get("lineWeight").getAsFloat(),.5f,2.5f,"lineWeight"));
        }
        private static float bounded(float v,float lo,float hi,String name){if(!Float.isFinite(v)||v<lo||v>hi)throw new IllegalArgumentException(name+" out of range");return v;}
    }

    private static final class State implements AutoCloseable {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int[] viewport=new int[4],bound=new int[6],shaderTex=new int[6];final float[] mask=new float[4];
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final ShaderInstance previous=RenderSystem.getShader();
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);for(int i=0;i<6;i++){shaderTex[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);bound[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}GlStateManager._activeTexture(active);}
        public void close(){
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<6;i++){RenderSystem.setShaderTexture(i,shaderTex[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(bound[i]);}GlStateManager._activeTexture(active);RenderSystem.setShader(()->previous);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);RenderSystem.colorMask(mask[0]>.5f,mask[1]>.5f,mask[2]>.5f,mask[3]>.5f);
        }
    }
}
