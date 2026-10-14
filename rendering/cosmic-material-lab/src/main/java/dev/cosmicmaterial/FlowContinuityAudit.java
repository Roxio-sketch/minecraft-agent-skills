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
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Compares native baked cube faces against a single full-window reference. */
@Mod.EventBusSubscriber(modid=CosmicMaterial.ID,value=Dist.CLIENT)
public final class FlowContinuityAudit extends Screen {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int ROTATIONS=16;
    private final Path output;
    private final String material;
    private final JsonArray results=new JsonArray();
    private final ItemStack cube=new ItemStack(Items.DIAMOND_BLOCK);
    private final boolean oldPause;
    private final float oldTime;
    private final int oldWidth,oldHeight;
    private final boolean resize;
    private final boolean oldFullscreen;
    private int index,settle;
    private boolean restored;

    public static void start(Minecraft mc) throws Exception {
        start(mc,0,0);
    }
    public static void start(Minecraft mc,int width,int height) throws Exception {
        start(mc,width,height,"fantasy-flow");
    }
    public static void start(Minecraft mc,int width,int height,String material) throws Exception {
        if(mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))
            throw new IllegalStateException("Continuity audit requires ASTRA_VFX_TEST.");
        if(FantasyPresets.get(material)==null)throw new IllegalArgumentException("Unknown material: "+material);
        mc.setScreen(new FlowContinuityAudit(mc,width,height,material));
    }
    private FlowContinuityAudit(Minecraft mc,int width,int height,String material) throws Exception {
        super(Component.literal("Fantasy Flow Continuity"));
        this.material=material;
        output=mc.gameDirectory.toPath().resolve("cosmic-material-lab/continuity-runs")
            .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(output);
        cube.getOrCreateTagElement("CosmicMaterial").putString("Material",material);
        oldTime=CosmicRender.frozenTime;oldPause=mc.options.pauseOnLostFocus;
        oldWidth=mc.getWindow().getWidth();oldHeight=mc.getWindow().getHeight();resize=width>0&&height>0;
        oldFullscreen=mc.getWindow().isFullscreen();
        if(resize&&oldFullscreen)mc.getWindow().toggleFullScreen();
        if(resize){mc.getWindow().setWindowed(width,height);mc.resizeDisplay();}
        mc.options.pauseOnLostFocus=false;CosmicRender.frozenTime=0;
        write("running");
    }
    private int slot(){return index%(ROTATIONS+1);}
    private float materialTime(){return index/(ROTATIONS+1)==0?0:200;}
    private float rotation(){return (slot()-1)*360f/ROTATIONS;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){
        if(!restored){
            restored=true;CosmicRender.frozenTime=oldTime;minecraft.options.pauseOnLostFocus=oldPause;
            if(resize){minecraft.getWindow().setWindowed(oldWidth,oldHeight);minecraft.resizeDisplay();}
            if(resize&&oldFullscreen&&!minecraft.getWindow().isFullscreen()){minecraft.getWindow().toggleFullScreen();minecraft.resizeDisplay();}
        }
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        CosmicRender.frozenTime=materialTime();
        g.fill(0,0,width,height,0xFF101416);g.flush();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1000000f);RenderSystem.setShaderFogEnd(1000001f);
        try {
            var pose=g.pose();pose.pushPose();
            if(slot()==0){
                // A single opaque quad uses the identical production shader and
                // framebuffer coordinates as every rotated baked-model face.
                var sprite=minecraft.getItemRenderer().getModel(cube,minecraft.level,null,0).getParticleIcon();
                var type=CosmicRender.type(ItemDisplayContext.GUI,0,false,FantasyPresets.get(material),sprite);
                var vertex=g.bufferSource().getBuffer(type);
                var matrix=pose.last().pose();
                float[][] corners={{0,height,sprite.getU0(),sprite.getV1()},
                    {width,height,sprite.getU1(),sprite.getV1()},
                    {width,0,sprite.getU1(),sprite.getV0()},{0,0,sprite.getU0(),sprite.getV0()}};
                for(var c:corners)vertex.vertex(matrix,c[0],c[1],100).color(255,255,255,255)
                    .uv(c[2],c[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(0,0,1).endVertex();
            }else{
                float scale=Math.min(width,height)*0.67f;
                pose.translate(width/2f,height/2f,200);
                pose.scale(scale,-scale,scale);
                pose.mulPose(Axis.XP.rotationDegrees(25));
                pose.mulPose(Axis.YP.rotationDegrees(rotation()));
                // GUI keeps the ray camera stable; the model still uses its
                // native baked quads and display transform through ItemRenderer.
                minecraft.getItemRenderer().renderStatic(cube,ItemDisplayContext.GUI,
                    LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,g.bufferSource(),minecraft.level,0);
            }
            g.flush();pose.popPose();
        }finally{RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);}
        settle++;
    }
    @SubscribeEvent public static void capture(TickEvent.RenderTickEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if(event.phase!=TickEvent.Phase.END||!(mc.screen instanceof FlowContinuityAudit audit)||audit.settle<3||mc.getOverlay()!=null)return;
        try {
            JsonObject record=new JsonObject();
            String name=String.format("%02d-%s.png",audit.index,audit.slot()==0?"reference":"cube");
            try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                image.writeToFile(audit.output.resolve(name));
                record.addProperty("width",image.getWidth());record.addProperty("height",image.getHeight());
            }
            record.addProperty("file",name);record.addProperty("reference",audit.slot()==0);
            record.addProperty("timeTicks",audit.materialTime());record.addProperty("rotationDegrees",audit.rotation());
            record.addProperty("glError",GL11.glGetError());record.addProperty("shaderGeneration",CosmicRender.generation);
            audit.results.add(record);audit.index++;audit.settle=0;
            if(audit.index==2*(ROTATIONS+1)){
                mc.setScreen(null);audit.write("completed");
                System.out.println("[COSMIC-MATERIAL] Cube continuity complete: "+audit.output);
            }else audit.write("running");
        }catch(Exception error){
            mc.setScreen(null);
            try{Files.writeString(audit.output.resolve("error.txt"),error.toString());audit.write("failed");}catch(Exception ignored){}
            error.printStackTrace();
        }
    }
    private void write(String status) throws Exception {
        JsonObject data=new JsonObject();data.addProperty("status",status);
        data.addProperty("suite","fantasy-flow-cube-continuity");data.addProperty("restored",restored);
        data.addProperty("material",material);
        data.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));data.add("frames",results);
        data.addProperty("coordinateContract","shared screen rays; GUI camera fixed; native baked cube rotations");
        Files.writeString(output.resolve("results.json"),JSON.toJson(data));
        Files.writeString(output.getParent().getParent().resolve("continuity-status.json"),JSON.toJson(data));
    }
}
