package dev.scenewindow;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.BufferUtils;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import static net.minecraft.commands.Commands.literal;

/** Opt-in audit. Only changes the selected test save's inventory temporarily. */
@Mod.EventBusSubscriber(modid=SceneWindow.ID,value=Dist.CLIENT)
public final class WindowAudit {
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final String[] CASES={"front","eye-left","eye-right","time-4","resolution-64","resolution-512","reloaded","hand-right","hand-left","third-person"};
    static boolean running, captured, oldPause; static int poll,index,ticks,oldW,oldH,oldScale;
    static CameraType oldCamera;static HumanoidArm oldHand;
    static Path output;static JsonArray records=new JsonArray();static JsonObject restoration=new JsonObject();
    static CompletableFuture<?> pending;static ListTag inventory;static int selected;
    static int initialGeneration;
    static Path root(){return Minecraft.getInstance().gameDirectory.toPath().resolve("item-scene-window-lab");}
    public static ItemStack sample(String scene){var item=new ItemStack(SceneWindow.WINDOW.get());item.getOrCreateTag().putString("Scene","scenewindow:"+scene);return item;}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event){event.getDispatcher().register(literal("scenewindow")
        .then(literal("preview").executes(c->{Minecraft.getInstance().setScreen(new Preview());return 1;}))
        .then(literal("verify").executes(c->{try{start();return 1;}catch(Exception e){fail(e);return 0;}})));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){WindowRenderer.release();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        try{
            if(++poll%20==0){
                Path file=root().resolve("request.json");
                if(Files.isRegularFile(file)){
                    JsonObject req=JsonParser.parseString(Files.readString(file)).getAsJsonObject();Files.delete(file);
                    if(!running){String action=req.get("action").getAsString();if(action.equals("run"))start();
                        else if(action.equals("preview"))Minecraft.getInstance().setScreen(new Preview());}
                }
            }
            if(!running)return;
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(index<0||captured){next();return;}
            if(++ticks>400)throw new IllegalStateException("Audit timeout");
        }catch(Exception e){fail(e);}
    }
    private static void start()throws Exception{
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))
            throw new IllegalStateException("Use the authorized local ASTRA_VFX_TEST save");
        output=root().resolve("runs/"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));Files.createDirectories(output);
        oldW=mc.getWindow().getWidth();oldH=mc.getWindow().getHeight();oldScale=mc.options.guiScale().get();
        oldPause=mc.options.pauseOnLostFocus;oldCamera=mc.options.getCameraType();oldHand=mc.options.mainHand().get();
        mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,720);mc.resizeDisplay();
        records=new JsonArray();restoration=new JsonObject();WindowRenderer.contexts.clear();WindowRenderer.contextEyes.clear();WindowRenderer.stateMismatches=0;
        initialGeneration=WindowRenderer.shaderGeneration;running=true;index=-1;
        var id=mc.player.getUUID();
        pending=mc.getSingleplayerServer().submit(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);inventory=p.getInventory().save(new ListTag());selected=p.getInventory().selected;
            CompoundTag saved=new CompoundTag();saved.put("Inventory",inventory.copy());saved.putInt("SelectedItemSlot",selected);
            try{NbtIo.writeCompressed(saved,output.resolve("restore-inventory.nbt").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static void next(){
        var mc=Minecraft.getInstance();index++;captured=false;ticks=0;
        if(index>=CASES.length){finish(null);return;}
        WindowRenderer.frozenTime=index==3?4:0;
        WindowRenderer.forcedEye=index==1?new Vector3f(-.6f,0,2.5f):index==2?new Vector3f(.6f,0,2.5f):null;
        WindowRenderer.forcedResolution=index==4?64:index==5?512:0;
        mc.options.mainHand().set(index==8?HumanoidArm.LEFT:HumanoidArm.RIGHT);
        mc.options.setCameraType(index==9?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        if(index<7){mc.setScreen(new Preview());if(index==6)pending=mc.reloadResourcePacks();}
        else{
            mc.setScreen(null);mc.player.getInventory().selected=0;var id=mc.player.getUUID();
            pending=mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                p.getInventory().selected=0;p.getInventory().setItem(0,sample("observatory"));p.containerMenu.broadcastChanges();});
        }
        write("running");
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!running||index<0||captured||pending!=null||ticks<35)return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            JsonObject row=new JsonObject();row.addProperty("case",CASES[index]);
            try(NativeImage img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(output.resolve(CASES[index]+".png"));}
            var target=WindowRenderer.lastTarget();
            if(target==null)throw new IllegalStateException("No FBO drawn");
            try(NativeImage img=Screenshot.takeScreenshot(target)){img.writeToFile(output.resolve(CASES[index]+"-scene.png"));}
            int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);
            var depth=BufferUtils.createFloatBuffer(target.width*target.height);GL11.glReadPixels(0,0,target.width,target.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);
            int geometry=0;java.util.Set<Integer> bins=new java.util.HashSet<>();
            for(int i=0;i<depth.capacity();i++){float d=depth.get(i);if(d<.999999f){geometry++;bins.add((int)(d*100000));}}
            row.addProperty("geometryDepthPixels",geometry);row.addProperty("depthBins",bins.size());
            row.addProperty("resolution",target.width);row.addProperty("fboStatus",WindowRenderer.lastFboStatus);
            row.addProperty("glError",GL11.glGetError());row.addProperty("stateMismatches",WindowRenderer.stateMismatches);
            row.addProperty("shaderGeneration",WindowRenderer.shaderGeneration);row.addProperty("sceneGeneration",Scenes.generation);
            row.addProperty("time",WindowRenderer.frozenTime);row.add("eye",JSON.toJsonTree(new float[]{WindowRenderer.lastEye.x,WindowRenderer.lastEye.y,WindowRenderer.lastEye.z}));
            row.add("contexts",JSON.toJsonTree(WindowRenderer.contexts));row.addProperty("sceneError",Scenes.lastError);
            row.add("contextEyes",JSON.toJsonTree(WindowRenderer.contextEyes));
            row.addProperty("framebufferWidth",mc.getMainRenderTarget().width);
            row.addProperty("framebufferHeight",mc.getMainRenderTarget().height);
            records.add(row);captured=true;write("running");
        }catch(Exception ex){fail(ex);}
    }
    private static void finish(Throwable error){
        running=false;var mc=Minecraft.getInstance();WindowRenderer.frozenTime=-1;WindowRenderer.forcedEye=null;WindowRenderer.forcedResolution=0;
        var id=mc.player.getUUID();
        pending=mc.getSingleplayerServer().submit(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            if(inventory!=null){p.getInventory().load(inventory);p.getInventory().selected=selected;p.containerMenu.broadcastChanges();
                restoration.addProperty("inventory",p.getInventory().save(new ListTag()).equals(inventory));restoration.addProperty("selectedSlot",p.getInventory().selected==selected);}
        }).whenComplete((v,e)->mc.execute(()->{
            mc.options.setCameraType(oldCamera);mc.options.mainHand().set(oldHand);mc.options.pauseOnLostFocus=oldPause;
            mc.player.getInventory().selected=selected;
            mc.options.guiScale().set(oldScale);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),oldW,oldH);mc.resizeDisplay();
            restoration.addProperty("options",mc.options.getCameraType()==oldCamera&&mc.options.mainHand().get()==oldHand&&mc.options.guiScale().get()==oldScale);
            mc.setScreen(new Preview());write(error==null&&e==null?"completed":"failed");
        }));
    }
    private static void fail(Throwable e){System.err.println("[SCENE-WINDOW] "+e);e.printStackTrace();
        if(output!=null)try{Files.writeString(output.resolve("error.txt"),e.toString());}catch(Exception ignored){}
        if(running)finish(e);
    }
    static void write(String status){try{
        JsonObject root=new JsonObject();root.addProperty("status",status);root.addProperty("minecraft","1.20.1");root.addProperty("forge",ForgeVersion.getVersion());
        root.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));root.addProperty("world","ASTRA_VFX_TEST");root.addProperty("initialShaderGeneration",initialGeneration);
        root.add("results",records);root.add("restoration",restoration);Files.writeString(output.resolve("results.json"),JSON.toJson(root));Files.writeString(root().resolve("status.json"),JSON.toJson(root));
    }catch(Exception ex){System.err.println(ex);}}
    public static class Preview extends Screen{
        Preview(){super(Component.literal("Item Scene Window"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff111824);g.drawCenteredString(font,"REAL SCENE WINDOWS / Forge 1.20.1",width/2,10,0xe7eeff);
            String[] names={"observatory","garden","gallery","keyhole"};
            float cell=(width-24)/4f, size=Math.min(cell-8,height-90);
            for(int i=0;i<4;i++){
                int x=(int)(12+i*cell),y=40;g.fill(x,y,x+(int)cell-4,y+(int)size+28,0xff202b3e);
                g.pose().pushPose();g.pose().translate(x+(cell-size)/2,y+3,0);g.pose().scale(size/16,size/16,1);
                g.renderItem(sample(names[i]),0,0);g.pose().popPose();g.drawCenteredString(font,names[i],x+(int)cell/2,y+(int)size+12,0xbad5ee);
            }
            g.renderItem(new ItemStack(Items.DIAMOND_SWORD),16,height-30);g.drawString(font,"Vanilla control | scene = real triangles + depth | Esc: return",40,height-24,0x9aabc4);
            g.flush();super.render(g,mx,my,partial);
        }
    }
}
