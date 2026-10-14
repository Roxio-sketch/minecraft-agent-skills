package dev.cosmicmaterial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Save-specific live acceptance test for worn armor and the vanilla shield. */
@Mod.EventBusSubscriber(modid=CosmicMaterial.ID,value=Dist.CLIENT)
public final class EquipmentAudit {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final String[] CASES={"equipment-gui","armor-back","armor-front","shield-hand",
        "shield-blocking","shield-patterned","shield-framed","shield-framed-patterned","equipment-reloaded"};
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final Item[] ITEMS={Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS};
    private static final String[] PALETTES={"violet","gold","cyan","violet"};
    private static final JsonArray FRAMES=new JsonArray();
    private static Path output;
    private static CompletableFuture<?> pending;
    private static CompoundTag backup;
    private static GameType oldMode;
    private static double oldX,oldY,oldZ;
    private static float oldYaw,oldPitch;
    private static boolean running,captured,oldHide,oldPause,oldBob;
    private static CameraType oldCamera;
    private static int index=-1,ticks,frames,oldWidth,oldHeight,oldGuiScale;
    private static long poll;
    private static UUID playerId;
    private static JsonObject restoration=new JsonObject();

    private static ItemStack framedShield() {
        ItemStack stack=CosmicAudit.sample(Items.SHIELD,"cyan",false,true);
        stack.getTag().getCompound("CosmicMaterial").putString("ShieldStyle","framed");
        return stack;
    }

    private static ItemStack patternedShield(boolean framed) {
        ItemStack stack=framed?framedShield():CosmicAudit.sample(Items.SHIELD,"cyan",false,true);
        CompoundTag data=new CompoundTag();data.putInt("Base",DyeColor.RED.getId());
        CompoundTag stripe=new CompoundTag();stripe.putString("Pattern","bs");
        stripe.putInt("Color",DyeColor.WHITE.getId());
        ListTag patterns=new ListTag();patterns.add(stripe);data.put("Patterns",patterns);
        stack.getOrCreateTag().put("BlockEntityTag",data);
        return stack;
    }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cosmicmaterialequipment")
            .then(Commands.literal("showcase").executes(c->{Minecraft.getInstance().setScreen(new Showcase());return 1;}))
            .then(Commands.literal("verify").executes(c->{
                try{start(Minecraft.getInstance());return 1;}
                catch(Exception e){throw new IllegalStateException(e);}
            })));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(++poll%20==0 && !running) {
                Path request=mc.gameDirectory.toPath().resolve("cosmic-material-lab/equipment-request.json");
                if(Files.isRegularFile(request)) {
                    String action=JSON.fromJson(Files.readString(request),JsonObject.class).get("action").getAsString();
                    Files.delete(request);
                    if(action.equals("run"))start(mc);
                    else if(action.equals("showcase"))mc.setScreen(new Showcase());
                }
            }
            if(!running)return;
            if(pending!=null) {
                if(!pending.isDone())return;
                pending.join();pending=null;
            }
            if(index<0||captured){advance(mc);return;}
            if(index==4&&ticks>=8) {
                mc.options.keyUse.setDown(true);
                if(!mc.player.isUsingItem())mc.gameMode.useItem(mc.player,InteractionHand.OFF_HAND);
            }
            if(++ticks>500)throw new IllegalStateException("Equipment scenario timeout: "+CASES[index]);
        }catch(Exception e){fail(mc,e);}
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!running||index<0||captured||pending!=null)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(mc.getOverlay()!=null||ticks<35||++frames<30)return;
            if(index==4&&(!mc.player.isUsingItem()||mc.player.getTicksUsingItem()<15))return;
            JsonObject frame=new JsonObject();frame.addProperty("case",CASES[index]);
            try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(output.resolve(CASES[index]+".png"));
                frame.addProperty("width",image.getWidth());frame.addProperty("height",image.getHeight());
            }
            frame.addProperty("camera",mc.options.getCameraType().name());
            frame.addProperty("usingShield",mc.player.isUsingItem()&&mc.player.getUseItem().is(Items.SHIELD));
            frame.addProperty("ticksUsing",mc.player.getTicksUsingItem());
            frame.addProperty("glError",GL11.glGetError());
            frame.addProperty("shaderGeneration",CosmicRender.generation);
            frame.add("draws",JSON.toJsonTree(CosmicRender.draws));
            FRAMES.add(frame);captured=true;write("running");
        }catch(Exception e){fail(mc,e);}
    }

    private static void start(Minecraft mc) throws Exception {
        if(mc.player==null||mc.getSingleplayerServer()==null||
           !mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))
            throw new IllegalStateException("Equipment audit requires local ASTRA_VFX_TEST.");
        if(running)throw new IllegalStateException("Equipment audit already running.");
        output=mc.gameDirectory.toPath().resolve("cosmic-material-lab/equipment-runs")
            .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(output);
        playerId=mc.player.getUUID();
        oldCamera=mc.options.getCameraType();oldHide=mc.options.hideGui;
        oldPause=mc.options.pauseOnLostFocus;oldBob=mc.options.bobView().get();
        oldWidth=mc.getWindow().getWidth();oldHeight=mc.getWindow().getHeight();oldGuiScale=mc.options.guiScale().get();
        mc.options.pauseOnLostFocus=false;mc.options.bobView().set(false);
        mc.options.guiScale().set(2);mc.getWindow().setWindowed(1280,720);mc.resizeDisplay();
        FRAMES.asList().clear();CosmicRender.draws.clear();restoration=new JsonObject();
        index=-1;running=true;CosmicRender.frozenTime=0;
        pending=mc.getSingleplayerServer().submit(()->{
            ServerPlayer player=mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            backup=player.saveWithoutId(new CompoundTag());
            oldMode=player.gameMode.getGameModeForPlayer();
            oldX=player.getX();oldY=player.getY();oldZ=player.getZ();
            oldYaw=player.getYRot();oldPitch=player.getXRot();
            try{NbtIo.writeCompressed(backup,output.resolve("restore-player.nbt").toFile());}
            catch(Exception e){throw new RuntimeException(e);}
            for(int i=0;i<4;i++)player.setItemSlot(SLOTS[i],CosmicAudit.sample(ITEMS[i],PALETTES[i],false,i==0));
            player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND,CosmicAudit.sample(Items.SHIELD,"cyan",false,true));
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying=true;player.onUpdateAbilities();
            player.connection.teleport(oldX,oldY+8,oldZ,0,0);
            player.containerMenu.broadcastChanges();
        });
        write("running");
    }

    private static void advance(Minecraft mc) {
        if(++index>=CASES.length){finish(mc);return;}
        ticks=frames=0;captured=false;mc.options.keyUse.setDown(false);mc.player.stopUsingItem();
        mc.options.hideGui=index==1||index==2;
        mc.options.setCameraType(index==2?CameraType.THIRD_PERSON_FRONT:
            index==1?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
        mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
        mc.setScreen(index==0||index==8?new Showcase():null);
        if(index>=5&&index<=7)pending=mc.getSingleplayerServer().submit(()->{
            ServerPlayer player=mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.stopUsingItem();player.setItemSlot(EquipmentSlot.OFFHAND,
                index==5?patternedShield(false):index==6?framedShield():patternedShield(true));
            player.containerMenu.broadcastChanges();
        });
        if(index==8)pending=mc.reloadResourcePacks();
    }

    private static void finish(Minecraft mc) {
        running=false;CosmicRender.frozenTime=-1;mc.options.keyUse.setDown(false);mc.player.stopUsingItem();
        restore(mc).whenComplete((v,error)->mc.execute(()->{
            restoreOptions(mc);
            if(error!=null){writeError(error);return;}
            write("completed");
        }));
    }

    private static CompletableFuture<?> restore(Minecraft mc) {
        if(backup==null||mc.getSingleplayerServer()==null)return CompletableFuture.completedFuture(null);
        return mc.getSingleplayerServer().submit(()->{
            ServerPlayer player=mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            if(player==null)throw new IllegalStateException("Player unavailable during equipment restore.");
            player.stopUsingItem();
            player.getInventory().load(backup.getList("Inventory",10));
            player.getInventory().selected=backup.getInt("SelectedItemSlot");
            player.setGameMode(oldMode);
            player.getAbilities().loadSaveData(backup);player.onUpdateAbilities();
            player.connection.teleport(oldX,oldY,oldZ,oldYaw,oldPitch);
            player.containerMenu.broadcastChanges();
            restoration.addProperty("inventory",player.getInventory().save(new ListTag()).equals(backup.getList("Inventory",10)));
            restoration.addProperty("selectedSlot",player.getInventory().selected==backup.getInt("SelectedItemSlot"));
            restoration.addProperty("gameMode",player.gameMode.getGameModeForPlayer()==oldMode);
            restoration.addProperty("position",player.getX()==oldX&&player.getY()==oldY&&player.getZ()==oldZ);
        });
    }

    private static void restoreOptions(Minecraft mc) {
        mc.options.keyUse.setDown(false);
        if(mc.player!=null)mc.player.stopUsingItem();
        mc.options.hideGui=oldHide;mc.options.pauseOnLostFocus=oldPause;mc.options.bobView().set(oldBob);
        mc.options.setCameraType(oldCamera);mc.options.guiScale().set(oldGuiScale);
        mc.getWindow().setWindowed(oldWidth,oldHeight);mc.resizeDisplay();mc.setScreen(null);
    }

    private static void fail(Minecraft mc,Throwable error) {
        running=false;CosmicRender.frozenTime=-1;
        error.printStackTrace();
        restore(mc).whenComplete((v,e)->mc.execute(()->{restoreOptions(mc);writeError(error);}));
    }

    private static void writeError(Throwable error) {
        try{Files.writeString(output.resolve("error.txt"),error.toString());write("failed");}
        catch(Exception ignored){}
    }

    private static void write(String status) {
        if(output==null)return;
        try {
            JsonObject data=new JsonObject();data.addProperty("status",status);
            data.addProperty("minecraft","1.20.1");data.addProperty("forge",ForgeVersion.getVersion());
            data.addProperty("world","ASTRA_VFX_TEST");data.addProperty("shaderGeneration",CosmicRender.generation);
            data.add("frames",FRAMES);data.add("draws",JSON.toJsonTree(CosmicRender.draws));
            data.add("restoration",restoration);
            Files.writeString(output.resolve("results.json"),JSON.toJson(data));
        }catch(Exception e){System.err.println("[COSMIC-MATERIAL] Equipment evidence write failed: "+e);}
    }

    private static final class Showcase extends Screen {
        private final ItemStack[] samples={
            CosmicAudit.sample(Items.DIAMOND_HELMET,"violet",false,true),
            CosmicAudit.sample(Items.DIAMOND_CHESTPLATE,"gold",false,false),
            CosmicAudit.sample(Items.DIAMOND_LEGGINGS,"cyan",false,false),
            CosmicAudit.sample(Items.DIAMOND_BOOTS,"violet",false,false),
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            CosmicAudit.sample(Items.SHIELD,"cyan",false,true),framedShield(),
            patternedShield(false),patternedShield(true),new ItemStack(Items.SHIELD)
        };
        private final String[] labels={"Helmet / glint","Chestplate","Leggings","Boots","Vanilla armor",
            "Full Cosmic","Framed Cosmic","Patterned full","Patterned framed","Vanilla shield"};
        Showcase(){super(Component.literal("Cosmic equipment"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xFF10151E);
            g.drawCenteredString(font,"COSMIC ARMOR + SHIELD / Forge 1.20.1",width/2,7,0xE4EEFF);
            int w=width/5,h=(height-35)/2;
            for(int i=0;i<samples.length;i++) {
                int x=i%5*w,y=30+i/5*h;
                g.fill(x+3,y,x+w-3,y+h-4,0xFF1B2330);
                float size=Math.min(w-20,h-30);
                g.pose().pushPose();g.pose().translate(x+w/2f-size/2,y+5,0);
                g.pose().scale(size/16,size/16,1);g.renderItem(samples[i],0,0);g.pose().popPose();
                g.drawCenteredString(font,labels[i],x+w/2,y+h-18,0xC6D2E6);
            }
            g.flush();super.render(g,mx,my,partial);
        }
    }
}
