// SPDX-License-Identifier: MIT
package dev.localmaterial;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.*;

/** Optional vanilla chunk surface backend. No framebuffer sampling in the material pass. */
public final class TranslucentMaterial {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static ShaderInstance shader;
    private static boolean requested, eligibleResources, rectsReady;
    private static int scope; // 0 = water sprite contract; 1 = ALL chunk translucency (explicit opt-in)
    private static int draws, generation;
    private static String reason="basic";
    private static Probe probe;
    private static boolean probeRequested;
    private static final List<ResourceLocation> WATER=List.of(
        new ResourceLocation("minecraft","block/water_still"),
        new ResourceLocation("minecraft","block/water_flow"),
        new ResourceLocation("minecraft","block/water_overlay"));
    private static final float[][] rects=new float[3][4];

    public static LiteralArgumentBuilder<CommandSourceStack> commands(){
        return Commands.literal("surface")
            .then(Commands.literal("basic").executes(c->{requested=false;reset();say("surface: basic (original translucent materials)");return 1;}))
            .then(Commands.literal("material")
                .then(Commands.literal("water").executes(c->enable(0)))
                .then(Commands.literal("layer").executes(c->enable(1))))
            .then(Commands.literal("alien").executes(c->{say("surface: alien unavailable; requires renderer-owned fluid/refraction adapter. Mode unchanged.");return 0;}))
            .then(Commands.literal("probe").executes(c->{
                if(!requested||LocalMaterial.Client.center==null){say("surface probe: activate a region and material mode first");return 0;}
                probeRequested=true;say("surface probe queued for next eligible draw");return 1;
            }))
            .then(Commands.literal("status").executes(c->{say("surface requested="+requested+" scope="+(scope==0?"water-sprites":"all-chunk-translucent")+" draws="+draws+" generation="+generation+" reason="+reason);return 1;}));
    }
    private static int enable(int selected){requested=true;scope=selected;reason="awaiting eligible draw";say("surface material scope="+(scope==0?"water sprites":"ALL chunk translucency"));return 1;}
    private static void say(String s){LOG.info("LOCAL_SURFACE {}",s);var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal(s),false);}

    public static void register(RegisterShadersEvent event){
        shader=null;rectsReady=false;eligibleResources=false;
        try {
            // An unknown core-shader resource override may contain waves or other indispensable logic.
            String[] names={"core/rendertype_translucent.json","core/rendertype_translucent.vsh","core/rendertype_translucent.fsh","include/fog.glsl","include/light.glsl"};
            var expected=new java.util.Properties();
            try(var in=TranslucentMaterial.class.getResourceAsStream("/localmaterial-vanilla-contract.properties")){expected.load(in);}
            boolean verified=true;
            for(String name:names){
                var r=event.getResourceProvider().getResource(new ResourceLocation("minecraft","shaders/"+name)).orElseThrow();
                try(var in=r.open()){
                    String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                    if(!Arrays.asList(expected.getProperty(name,"").split(",")).contains(hash)){verified=false;LOG.warn("LOCAL_SURFACE resource contract mismatch {} pack={} expected={} actual={}",name,r.sourcePackId(),expected.getProperty(name),hash);}
                }
            }
            eligibleResources=verified;
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(LocalMaterial.ID,"surface"),DefaultVertexFormat.BLOCK),s->{shader=s;generation++;});
            reason=verified?"basic":"core shader resource override: adapter required";
        }catch(Exception ex){reason="surface shader load failed";LOG.error(reason,ex);}
    }
    public static void reset(){probeRequested=false;if(probe!=null){probe.close();probe=null;}rectsReady=false;}

    public static ShaderInstance select(ShaderInstance original,RenderType layer,PoseStack pose,Matrix4f projection,double x,double y,double z){
        if(layer!=RenderType.translucent()||!requested||LocalMaterial.Client.center==null)return original;
        var mc=Minecraft.getInstance();
        if(LocalMaterial.Client.disabledByRenderer){reason="renderer adapter unvalidated";return original;}
        if(Minecraft.useShaderTransparency()){reason="Fabulous target/resolve not validated: basic fallback";return original;}
        if(shader==null||!eligibleResources){reason="shader/resource contract unavailable";return original;}
        if(original!=GameRenderer.getRendertypeTranslucentShader()||original.getClass()!=ShaderInstance.class){reason="custom shader owner: adapter required";return original;}
        RenderTarget main=mc.getMainRenderTarget();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId){reason="non-main view target";return original;}
        float radius=LocalMaterial.Client.radius();if(radius<=.001f)return original;
        try{
            if(!rectsReady){
                var atlas=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
                for(int i=0;i<3;i++){
                    var sprite=atlas.apply(WATER.get(i));
                    if(!sprite.contents().name().equals(WATER.get(i)))throw new IllegalStateException("Missing water sprite "+WATER.get(i));
                    rects[i]=new float[]{sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1()};
                }
                rectsReady=true;
            }
            var center=LocalMaterial.Client.center;
            shader.safeGetUniform("CenterRelative").set((float)(center.x-x),(float)(center.y-y),(float)(center.z-z));
            shader.safeGetUniform("Radius").set(radius);
            shader.safeGetUniform("Time").set(LocalMaterial.Client.age());
            shader.safeGetUniform("Material").set(LocalMaterial.Client.material);
            shader.safeGetUniform("SurfaceScope").set(scope);
            for(int i=0;i<3;i++){float[] r=rects[i];shader.safeGetUniform("WaterRect"+i).set(r[0],r[1],r[2],r[3]);}
            if(probeRequested){
                probeRequested=false;
                probe=new Probe(main,original,new Matrix4f(pose.last().pose()),new Matrix4f(projection));
            }
            draws++;reason="active: original chunk geometry";return shader;
        }catch(Exception ex){requested=false;reason="surface exception: basic fallback";LOG.error(reason,ex);reset();return original;}
    }

    public static void recordDraw(VertexBuffer buffer){
        if(probe==null)return;
        var v=shader.CHUNK_OFFSET.getFloatBuffer();
        probe.draws.add(new Draw(buffer,v.get(0),v.get(1),v.get(2)));
    }
    public static void finishProbe(){
        if(probe==null)return;
        Probe active=probe;probe=null;
        try{active.finish();}catch(Exception ex){LOG.error("Surface A/B probe failed",ex);}
        finally{active.close();}
    }
    private record Draw(VertexBuffer buffer,float x,float y,float z){}

    /** Explicit diagnostic only: repeat identical VBO draws over identical pre-layer attachments.
     * Compare against the ORIGINAL vanilla program, then restore the materialized attachments.
     * Does not rerun world events, sorting, texture updates, entity draws or simulation. */
    private static final class Probe implements AutoCloseable {
        final RenderTarget main;final TextureTarget before,after;final ShaderInstance original;
        final Matrix4f view,projection;final List<Draw> draws=new ArrayList<>();
        final int initialRead,initialDraw;
        Probe(RenderTarget main,ShaderInstance original,Matrix4f view,Matrix4f projection){
            this.main=main;this.original=original;this.view=view;this.projection=projection;
            initialRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);initialDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            before=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
            after=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
            copy(main,before);main.bindWrite(false);
        }
        static void copy(RenderTarget src,RenderTarget dst){
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,src.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,dst.frameBufferId);
            GL30.glBlitFramebuffer(0,0,src.width,src.height,0,0,dst.width,dst.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
        }
        void finish()throws Exception{
            if(draws.isEmpty())throw new IllegalStateException("No captured VBO draws; probe cannot certify transparency");
            copy(main,after);
            try{
                copy(before,main);main.bindWrite(false);
                for(int i=0;i<12;i++)original.setSampler("Sampler"+i,RenderSystem.getShaderTexture(i));
                original.MODEL_VIEW_MATRIX.set(view);original.PROJECTION_MATRIX.set(projection);
                original.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
                original.FOG_START.set(RenderSystem.getShaderFogStart());original.FOG_END.set(RenderSystem.getShaderFogEnd());
                original.FOG_COLOR.set(RenderSystem.getShaderFogColor());original.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
                original.apply();
                for(Draw d:draws){original.CHUNK_OFFSET.set(d.x,d.y,d.z);original.CHUNK_OFFSET.upload();d.buffer.bind();d.buffer.draw();}
                original.CHUNK_OFFSET.set(0f,0f,0f);original.clear();VertexBuffer.unbind();
                int n=main.width*main.height;
                var a=org.lwjgl.BufferUtils.createByteBuffer(n*4);var b=org.lwjgl.BufferUtils.createByteBuffer(n*4);
                var da=org.lwjgl.BufferUtils.createFloatBuffer(n);var db=org.lwjgl.BufferUtils.createFloatBuffer(n);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,a);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,da);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,after.frameBufferId);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
                GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,db);
                int changed=0,alpha=0,depth=0;float maxDepth=0;
                for(int i=0;i<n;i++){
                    if(a.get(4*i)!=b.get(4*i)||a.get(4*i+1)!=b.get(4*i+1)||a.get(4*i+2)!=b.get(4*i+2))changed++;
                    if(a.get(4*i+3)!=b.get(4*i+3))alpha++;
                    float d=Math.abs(da.get(i)-db.get(i));if(d>0)depth++;maxDepth=Math.max(maxDepth,d);
                }
                var data=new com.google.gson.JsonObject();
                data.addProperty("scope","same-frame native translucent VBO replay: ORIGINAL vanilla shader versus material shader");
                data.addProperty("version","0.2.0");data.addProperty("surfaceScope",scope==0?"water-sprites":"all-chunk-translucent");
                data.addProperty("generation",generation);data.addProperty("chunkDraws",draws.size());
                data.addProperty("width",main.width);data.addProperty("height",main.height);
                data.addProperty("changedRgbPixels",changed);data.addProperty("changedAlphaPixels",alpha);
                data.addProperty("changedDepthPixels",depth);data.addProperty("maxDepthDifference",maxDepth);
                data.addProperty("glError",GL11.glGetError());data.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));
                data.addProperty("graphics",Minecraft.getInstance().options.graphicsMode().get().toString());
                data.addProperty("center",LocalMaterial.Client.center.toString());data.addProperty("radius",LocalMaterial.Client.radius());
                data.addProperty("material",LocalMaterial.Client.material);
                var folder=Minecraft.getInstance().gameDirectory.toPath().resolve("local-environment-lab/surface-probes");java.nio.file.Files.createDirectories(folder);
                java.nio.file.Files.writeString(folder.resolve("surface-"+System.currentTimeMillis()+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
                LOG.info("LOCAL_SURFACE probe {}",data);
            }finally{copy(after,main);main.bindWrite(false);}
        }
        public void close(){before.destroyBuffers();after.destroyBuffers();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,initialRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,initialDraw);}
    }
}
