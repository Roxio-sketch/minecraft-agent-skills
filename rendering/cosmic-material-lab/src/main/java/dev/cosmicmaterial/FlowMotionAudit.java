package dev.cosmicmaterial;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Real game-clock recording with fixed geometry and frozen bookends. */
@Mod.EventBusSubscriber(modid=CosmicMaterial.ID,value=Dist.CLIENT)
public final class FlowMotionAudit extends Screen {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final ItemStack cube=new ItemStack(Items.DIAMOND_BLOCK);
    private final JsonArray frames=new JsonArray();
    private final Path output;
    private final String material;
    private final int oldWidth,oldHeight;
    private final boolean oldPause;
    private final boolean oldFullscreen;
    private final float oldTime;
    private final long start;
    private long lastCapture;
    private float lastLiveTicks;
    private String phase="warmup";
    private boolean restored;
    private boolean finishing;
    private final ExecutorService writer=Executors.newFixedThreadPool(2);
    private final List<CompletableFuture<Void>> writes=new ArrayList<>();

    public static void start(Minecraft mc) throws Exception {
        start(mc,"fantasy-flow");
    }
    public static void start(Minecraft mc,String material) throws Exception {
        if(mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))
            throw new IllegalStateException("Motion audit requires ASTRA_VFX_TEST.");
        if(FantasyPresets.get(material)==null)throw new IllegalArgumentException("Unknown material: "+material);
        mc.setScreen(new FlowMotionAudit(mc,material));
    }
    private FlowMotionAudit(Minecraft mc,String material) throws Exception {
        super(Component.literal("Fantasy Flow Motion"));
        this.material=material;
        output=mc.gameDirectory.toPath().resolve("cosmic-material-lab/motion-runs")
            .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(output);
        cube.getOrCreateTagElement("CosmicMaterial").putString("Material",material);
        oldWidth=mc.getWindow().getWidth();oldHeight=mc.getWindow().getHeight();
        oldFullscreen=mc.getWindow().isFullscreen();
        if(oldFullscreen)mc.getWindow().toggleFullScreen();
        oldPause=mc.options.pauseOnLostFocus;oldTime=CosmicRender.frozenTime;
        mc.options.pauseOnLostFocus=false;CosmicRender.frozenTime=0;
        mc.getWindow().setWindowed(2560,1440);mc.resizeDisplay();
        start=System.nanoTime();write("running");
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){
        if(restored)return;restored=true;
        writer.shutdown();
        CosmicRender.frozenTime=oldTime;minecraft.options.pauseOnLostFocus=oldPause;
        minecraft.getWindow().setWindowed(oldWidth,oldHeight);minecraft.resizeDisplay();
        if(oldFullscreen&&!minecraft.getWindow().isFullscreen()){minecraft.getWindow().toggleFullScreen();minecraft.resizeDisplay();}
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        double elapsed=(System.nanoTime()-start)/1e9;
        if(elapsed<3){phase=elapsed<1?"warmup":"frozen-before";CosmicRender.frozenTime=0;}
        else if(elapsed<9){phase="live";CosmicRender.frozenTime=-1;}
        else{phase="frozen-after";CosmicRender.frozenTime=lastLiveTicks;}
        g.fill(0,0,width,height,0xFF101416);g.flush();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1000000f);RenderSystem.setShaderFogEnd(1000001f);
        try{
            var pose=g.pose();pose.pushPose();
            float scale=Math.min(width,height)*0.82f;
            pose.translate(width/2f,height/2f,200);pose.scale(scale,-scale,scale);
            pose.mulPose(Axis.XP.rotationDegrees(25));pose.mulPose(Axis.YP.rotationDegrees(35));
            minecraft.getItemRenderer().renderStatic(cube,ItemDisplayContext.GUI,LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,pose,g.bufferSource(),minecraft.level,0);
            g.flush();pose.popPose();
            if(phase.equals("live"))lastLiveTicks=CosmicRender.shader.getUniform("time").getFloatBuffer().get(0);
        }finally{RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);}
    }
    @SubscribeEvent public static void capture(TickEvent.RenderTickEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if(event.phase!=TickEvent.Phase.END||!(mc.screen instanceof FlowMotionAudit audit)||audit.finishing||mc.getOverlay()!=null)return;
        long now=System.nanoTime();double elapsed=(now-audit.start)/1e9;
        if(elapsed<1||now-audit.lastCapture<50000000L)return;
        try{
            if(elapsed>=11){
                audit.finishing=true;
                CompletableFuture.allOf(audit.writes.toArray(CompletableFuture[]::new)).whenComplete((unused,error)->mc.execute(()->{
                    mc.setScreen(null);
                    try{
                        if(error!=null)Files.writeString(audit.output.resolve("error.txt"),error.toString());
                        audit.write(error==null?"completed":"failed");
                    }catch(Exception e){e.printStackTrace();}
                }));
                return;
            }
            // Bound native image memory while PNG encoding runs off the render thread.
            if(audit.writes.stream().filter(write->!write.isDone()).count()>=4)return;
            audit.lastCapture=now;
            JsonObject record=new JsonObject();
            String name=String.format("%03d.png",audit.frames.size());
            record.addProperty("file",name);record.addProperty("phase",audit.phase);
            record.addProperty("wallSeconds",elapsed);record.addProperty("frozenTime",CosmicRender.frozenTime);
            record.addProperty("shaderSeconds",CosmicRender.shader.getUniform("fmTime").getFloatBuffer().get(0));
            record.addProperty("shaderTicks",CosmicRender.shader.getUniform("time").getFloatBuffer().get(0));
            record.addProperty("gameTicks",mc.level.getGameTime());
            record.addProperty("glError",GL11.glGetError());
            NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget());
            record.addProperty("width",image.getWidth());record.addProperty("height",image.getHeight());
            audit.writes.add(CompletableFuture.runAsync(()->{
                try(image){image.writeToFile(audit.output.resolve(name));}
                catch(Exception error){throw new CompletionException(error);}
            },audit.writer));
            record.addProperty("captureMilliseconds",(System.nanoTime()-now)/1e6);
            audit.frames.add(record);audit.write("running");
        }catch(Exception error){
            mc.setScreen(null);
            try{Files.writeString(audit.output.resolve("error.txt"),error.toString());audit.write("failed");}catch(Exception ignored){}
            error.printStackTrace();
        }
    }
    private void write(String status) throws Exception {
        JsonObject data=new JsonObject();data.addProperty("status",status);data.addProperty("restored",restored);
        data.addProperty("suite","fantasy-flow-live-motion");data.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));
        data.addProperty("material",material);
        data.addProperty("clock","Actual game tick + partial tick; no synthetic time during live phase");
        data.addProperty("geometry","Fixed native diamond block, GUI camera, fixed rotation");data.add("frames",frames);
        Files.writeString(output.resolve("results.json"),JSON.toJson(data));
        Files.writeString(output.getParent().getParent().resolve("motion-status.json"),JSON.toJson(data));
    }
}
