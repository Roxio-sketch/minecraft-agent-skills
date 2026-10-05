package dev.blackholelab;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static net.minecraft.commands.Commands.literal;

/** Opt-in validation restricted to the user-selected local test save. */
@Mod.EventBusSubscriber(modid=BlackHoleLab.ID,value=Dist.CLIENT)
public final class BlackHoleAudit {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] CASES={"gui-frozen","gui-time200","gui-reloaded","hand-right",
        "hand-left","third-front","third-back","bow-idle","bow-pulling","ground-and-frame"};
    private static final List<UUID> spawned = new ArrayList<>();
    private static final JsonArray results = new JsonArray();
    private static Path output;
    private static long requestStamp=-1;
    private static int pollTicks, index=-1, ticks, frames;
    private static boolean running, restoring, captureReady, optionsCaptured;
    private static CompletableFuture<?> pending;
    private static CompoundTag playerBackup;
    private static UUID backupPlayer;
    private static ItemStack expectedMainHand=ItemStack.EMPTY;
    private static final Map<String,Long> contextsBefore=new TreeMap<>();
    private static GameType oldMode;
    private static double oldX,oldY,oldZ;
    private static float oldYaw,oldPitch;
    private static boolean oldHide,oldPause,oldBob;
    private static HumanoidArm oldHand;
    private static CameraType oldCamera;
    private static int oldFov;
    private static int oldGuiScale,oldWidth,oldHeight;
    private static JsonObject record;
    private static JsonObject restoration = new JsonObject();
    private static double testX,testY,testZ;

    private static Path control(Minecraft mc) {return mc.gameDirectory.toPath().resolve("black-hole-lab");}
    public static ItemStack sample(Item item,String palette,boolean blade,boolean foil) {
        ItemStack stack=new ItemStack(item);
        if (palette!=null) {
            CompoundTag material=new CompoundTag();
            material.putString("Palette",palette);
            if(blade) material.putString("Mask","blade");
            stack.getOrCreateTag().put("BlackHoleMaterial",material);
        }
        if(foil) stack.enchant(Enchantments.UNBREAKING,1);
        return stack;
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("blackhole")
            .then(literal("items").executes(c->{Minecraft.getInstance().setScreen(new Showcase());return 1;}))
            .then(literal("verify").executes(c->{start(Minecraft.getInstance());return 1;})));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if (++pollTicks%20==0) {
                Path request=control(mc).resolve("request.json");
                if(Files.isRegularFile(request)) {
                    long stamp=Files.getLastModifiedTime(request).toMillis();
                    if(stamp!=requestStamp) {
                        requestStamp=stamp;
                        String action=JsonParser.parseString(Files.readString(request)).getAsJsonObject().get("action").getAsString();
                        Files.delete(request);
                        if(action.equals("run")&&!running&&!restoring)start(mc);
                        else if(action.equals("showcase")&&!running&&!restoring)mc.setScreen(new Showcase());
                    }
                }
            }
            if(!running)return;
            if(pending!=null) {
                if(!pending.isDone())return;
                pending.join(); pending=null;
            }
            if(index<0) {next(mc);return;}
            if(captureReady) {next(mc);return;}
            if(index==8&&ticks>=8) {
                mc.options.keyUse.setDown(true);
                if(!mc.player.isUsingItem()&&mc.player.getMainHandItem().is(Items.BOW))
                    mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }
            if(++ticks>600)throw new IllegalStateException("Scenario timeout: "+CASES[index]
                +"; selectedSlot="+mc.player.getInventory().selected
                +"; mainHand="+BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem())
                +"; using="+mc.player.isUsingItem()+"; useTicks="+mc.player.getTicksUsingItem());
        } catch(Exception e) {fail(mc,e);}
    }
    private static void start(Minecraft mc) {
        if(running||restoring)throw new IllegalStateException("Verification is already running or restoring.");
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)
            throw new IllegalStateException("Enter the selected local save before running verification.");
        if(!mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))
            throw new IllegalStateException("Verification is restricted to ASTRA_VFX_TEST.");
        // Player Inventory NBT excludes open-container, cursor and crafting-grid stacks.
        // Never close a live item menu as part of the audit or silently drop these stacks.
        if(mc.screen instanceof AbstractContainerScreen<?>||mc.player.containerMenu!=mc.player.inventoryMenu
                ||!mc.player.containerMenu.getCarried().isEmpty())
            throw new IllegalStateException("Close item menus and place the cursor stack before verification.");
        for(int slot=1;slot<=4;slot++)if(mc.player.inventoryMenu.getSlot(slot).hasItem())
            throw new IllegalStateException("Empty the player crafting grid before verification.");
        try {
            playerBackup=null;backupPlayer=mc.player.getUUID();pending=null;optionsCaptured=false;
            output=control(mc).resolve("runs").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            Files.createDirectories(output);
            oldHide=mc.options.hideGui; oldPause=mc.options.pauseOnLostFocus;
            oldBob=mc.options.bobView().get(); oldHand=mc.options.mainHand().get();
            oldFov=mc.options.fov().get(); oldCamera=mc.options.getCameraType();
            oldGuiScale=mc.options.guiScale().get();oldWidth=mc.getWindow().getWidth();oldHeight=mc.getWindow().getHeight();
            optionsCaptured=true;
            mc.options.guiScale().set(2);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,720);mc.resizeDisplay();
            mc.options.pauseOnLostFocus=false;mc.options.bobView().set(false);mc.options.fov().set(70);
            results.asList().clear();spawned.clear();BlackHoleRender.contexts.clear();BlackHoleRender.draws.clear();
            restoration=new JsonObject();
            running=true;index=-1;BlackHoleRender.frozenTime=0;
            UUID id=backupPlayer;
            pending=mc.getSingleplayerServer().submit(()->{
                ServerPlayer player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                if(player==null)throw new IllegalStateException("Player unavailable before snapshot.");
                playerBackup=player.saveWithoutId(new CompoundTag());
                oldMode=player.gameMode.getGameModeForPlayer();
                oldX=player.getX();oldY=player.getY();oldZ=player.getZ();
                oldYaw=player.getYRot();oldPitch=player.getXRot();
                try {NbtIo.writeCompressed(playerBackup,output.resolve("restore-player.nbt").toFile());}
                catch(Exception ex){throw new RuntimeException(ex);}
                testX=oldX;testY=oldY+5;testZ=oldZ;
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying=true;player.onUpdateAbilities();
                player.connection.teleport(testX,testY,testZ,0,0);
                var level=player.serverLevel();
                for(int i=0;i<2;i++) {
                    ItemStack item=sample(i==0?Items.DIAMOND_SWORD:Items.DIAMOND_BLOCK,i==0?"violet":"cyan",i==0,false);
                    ItemEntity entity=new ItemEntity(level,testX-1.1+i*2.0,testY+1.2,testZ+2.6,item);
                    entity.setNoGravity(true);entity.setDeltaMovement(0,0,0);
                    entity.setPickUpDelay(Integer.MAX_VALUE);
                    level.addFreshEntity(entity);spawned.add(entity.getUUID());
                }
                ItemFrame frame=new ItemFrame(level,BlockPos.containing(testX,testY+1,testZ+4),Direction.NORTH);
                frame.setItem(sample(Items.DIAMOND_SWORD,"gold",false,false));
                CompoundTag data=frame.saveWithoutId(new CompoundTag());
                data.putBoolean("Fixed",true);data.putBoolean("Invisible",true);
                frame.load(data);
                level.addFreshEntity(frame);spawned.add(frame.getUUID());
            });
            System.out.println("[BLACK-HOLE] Runtime audit started: "+output);
        }catch(Exception e){fail(mc,e);}
    }
    private static void next(Minecraft mc) {
        index++;
        if(index>=CASES.length){finish(mc);return;}
        ticks=frames=0;captureReady=false;
        record=new JsonObject();
        record.addProperty("case",CASES[index]);record.addProperty("generationBefore",BlackHoleRender.generation);
        contextsBefore.clear();contextsBefore.putAll(BlackHoleRender.contexts);
        expectedMainHand=ItemStack.EMPTY;
        BlackHoleRender.frozenTime=index==1?200:0;
        mc.options.hideGui=false;
        mc.options.mainHand().set(HumanoidArm.RIGHT);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.keyUse.setDown(false);
        mc.player.stopUsingItem();
        if(index<3) {
            mc.setScreen(new Showcase());
            if(index==2)pending=mc.reloadResourcePacks();
        }else {
            mc.setScreen(null);
            mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
            if(index==4)mc.options.mainHand().set(HumanoidArm.LEFT);
            if(index==5)mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if(index==6)mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if(index==9)mc.options.hideGui=true;
            UUID id=mc.player.getUUID();
            ItemStack item=index>=7&&index<=8?sample(Items.BOW,"violet",false,false):
                index==9?ItemStack.EMPTY:sample(Items.DIAMOND_SWORD,"violet",true,false);
            expectedMainHand=item.copy();
            // broadcastChanges synchronizes stacks, not the selected hotbar index.
            // Set the client immediately and send the authoritative selection as well.
            mc.player.getInventory().selected=0;
            pending=mc.getSingleplayerServer().submit(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                player.stopUsingItem();
                player.getInventory().selected=0;
                player.getInventory().setItem(0,item.copy());
                player.getInventory().setItem(1,new ItemStack(Items.ARROW,64));
                player.containerMenu.broadcastChanges();
                player.connection.send(new ClientboundSetCarriedItemPacket(0));
                player.connection.teleport(testX,testY,testZ,0,0);
            });
        }
        writeProgress("running");
        System.out.println("[BLACK-HOLE] Case "+CASES[index]);
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!running||index<0||captureReady||pending!=null)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(mc.getOverlay()!=null||ticks<45||++frames<35)return;
            if(index>=3) {
                if(mc.player.getInventory().selected!=0||!ItemStack.matches(expectedMainHand,mc.player.getMainHandItem()))
                    throw new IllegalStateException("Case "+CASES[index]+" has wrong client main hand; selectedSlot="
                        +mc.player.getInventory().selected+"; actual="+mc.player.getMainHandItem()
                        +"; expected="+expectedMainHand);
                String context=switch(index) {
                    case 4->"FIRST_PERSON_LEFT_HAND";
                    case 5,6->"THIRD_PERSON_RIGHT_HAND";
                    case 9->null;
                    default->"FIRST_PERSON_RIGHT_HAND";
                };
                if(context!=null&&BlackHoleRender.contexts.getOrDefault(context,0L)<=contextsBefore.getOrDefault(context,0L))
                    throw new IllegalStateException("Case "+CASES[index]+" did not render context "+context);
            }
            if(index==8&&(!mc.player.isUsingItem()||mc.player.getTicksUsingItem()<20))return;
            Path file=output.resolve(CASES[index]+".png");
            try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(file);
                record.addProperty("width",image.getWidth());record.addProperty("height",image.getHeight());
            }
            record.addProperty("generationAfter",BlackHoleRender.generation);
            record.addProperty("glError",GL11.glGetError());
            record.addProperty("time",BlackHoleRender.frozenTime);
            record.addProperty("playerUsingItem",mc.player.isUsingItem());
            record.addProperty("ticksUsingItem",mc.player.getTicksUsingItem());
            record.addProperty("selectedSlot",mc.player.getInventory().selected);
            record.addProperty("mainHandItem",BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
            record.addProperty("camera",mc.options.getCameraType().name());
            record.addProperty("mainHand",mc.options.mainHand().get().name());
            record.add("contexts",JSON.toJsonTree(BlackHoleRender.contexts));
            record.add("draws",JSON.toJsonTree(BlackHoleRender.draws));
            if(!mc.player.getMainHandItem().isEmpty()) {
                var model=mc.getItemRenderer().getModel(mc.player.getMainHandItem(),mc.level,mc.player,0);
                Set<String> sprites=new TreeSet<>();
                for(var direction:Direction.values())
                    model.getQuads(null,direction,RandomSource.create(42)).forEach(q->sprites.add(q.getSprite().contents().name().toString()));
                model.getQuads(null,null,RandomSource.create(42)).forEach(q->sprites.add(q.getSprite().contents().name().toString()));
                record.add("resolvedSprites",JSON.toJsonTree(sprites));
                if(index==8&&!sprites.contains("minecraft:item/bow_pulling_2"))
                    throw new IllegalStateException("Full draw did not resolve bow_pulling_2: "+sprites);
            }
            results.add(record);captureReady=true;writeProgress("running");
        }catch(Exception e){fail(mc,e);}
    }
    private static void finish(Minecraft mc) {
        running=false;restoring=true;BlackHoleRender.frozenTime=-1;mc.options.keyUse.setDown(false);mc.player.stopUsingItem();
        pending=restore(mc);
        pending.whenComplete((v,e)->mc.execute(()->{
            restoreOptions(mc);restoring=false;
            if(e!=null){writeError(e);return;}
            writeProgress("completed");
            System.out.println("[BLACK-HOLE] Audit complete; player and test entities restored.");
        }));
    }
    private static CompletableFuture<?> restore(Minecraft mc) {
        if(playerBackup==null)return CompletableFuture.completedFuture(null);
        if(mc.getSingleplayerServer()==null)return CompletableFuture.failedFuture(
            new IllegalStateException("Server unavailable during restore; use restore-player.nbt."));
        UUID id=backupPlayer;
        return mc.getSingleplayerServer().submit(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            if(player==null)throw new IllegalStateException("Player unavailable during restore.");
            player.stopUsingItem();
            for(UUID entityId:spawned) {
                var entity=player.serverLevel().getEntity(entityId);
                if(entity!=null)entity.discard();
            }
            player.getInventory().load(playerBackup.getList("Inventory",10));
            player.getInventory().selected=playerBackup.getInt("SelectedItemSlot");
            player.setGameMode(oldMode);
            player.getAbilities().loadSaveData(playerBackup);
            player.onUpdateAbilities();
            player.connection.teleport(oldX,oldY,oldZ,oldYaw,oldPitch);
            player.containerMenu.broadcastChanges();
            player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
            restoration.addProperty("inventory",player.getInventory().save(new ListTag()).equals(playerBackup.getList("Inventory",10)));
            restoration.addProperty("selectedSlot",player.getInventory().selected==playerBackup.getInt("SelectedItemSlot"));
            restoration.addProperty("gameMode",player.gameMode.getGameModeForPlayer()==oldMode);
            restoration.addProperty("position",player.getX()==oldX&&player.getY()==oldY&&player.getZ()==oldZ);
            restoration.addProperty("rotation",player.getYRot()==oldYaw&&player.getXRot()==oldPitch);
            CompoundTag abilities=new CompoundTag();player.getAbilities().addSaveData(abilities);
            restoration.addProperty("abilities",abilities.getCompound("abilities").equals(playerBackup.getCompound("abilities")));
            restoration.addProperty("testEntitiesRemoved",spawned.stream().allMatch(entityId->{
                var entity=player.serverLevel().getEntity(entityId);return entity==null||entity.isRemoved();}));
            for(var entry:restoration.entrySet())if(!entry.getValue().getAsBoolean())
                throw new IllegalStateException("Restore verification failed: "+entry.getKey());
        });
    }
    private static void restoreOptions(Minecraft mc) {
        if(!optionsCaptured)return;
        mc.options.keyUse.setDown(false);
        if(mc.player!=null) {
            mc.player.stopUsingItem();
            if(playerBackup!=null&&mc.player.getUUID().equals(backupPlayer))
                mc.player.getInventory().selected=playerBackup.getInt("SelectedItemSlot");
        }
        mc.options.hideGui=oldHide;mc.options.pauseOnLostFocus=oldPause;
        mc.options.bobView().set(oldBob);mc.options.mainHand().set(oldHand);
        mc.options.fov().set(oldFov);mc.options.setCameraType(oldCamera);
        mc.options.guiScale().set(oldGuiScale);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),oldWidth,oldHeight);mc.resizeDisplay();
        mc.setScreen(null);
    }
    private static void fail(Minecraft mc,Exception error) {
        System.err.println("[BLACK-HOLE] Audit failure: "+error);
        error.printStackTrace();
        if(running) {
            running=false;restoring=true;BlackHoleRender.frozenTime=-1;
            restore(mc).whenComplete((v,e)->mc.execute(()->{
                restoreOptions(mc);restoring=false;
                if(e!=null)restoration.addProperty("error",e.toString());
                writeError(error);
            }));
        } else if(output!=null)writeError(error);
    }
    private static void writeError(Throwable error) {
        try {Files.writeString(output.resolve("error.txt"),error.toString());writeProgress("failed");}
        catch(Exception ignored){}
    }
    private static void writeProgress(String status) {
        if(output==null)return;
        try {
            Minecraft mc=Minecraft.getInstance();
            JsonObject data=new JsonObject();
            data.addProperty("status",status);data.addProperty("forge",ForgeVersion.getVersion());
            data.addProperty("minecraft","1.20.1");data.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));
            data.addProperty("glVersion",GL11.glGetString(GL11.GL_VERSION));
            data.addProperty("world","ASTRA_VFX_TEST");data.addProperty("shaderGeneration",BlackHoleRender.generation);
            data.addProperty("wrappedItemRoots",BlackHoleModels.wrappedCount);
            data.addProperty("selectedCase",index<CASES.length&&index>=0?CASES[index]:"done");
            data.addProperty("nativeEntityTestCount",spawned.size());
            data.add("testEntityIds",JSON.toJsonTree(spawned));
            data.add("restoration",restoration);
            data.add("results",results);
            data.add("contexts",JSON.toJsonTree(BlackHoleRender.contexts));
            data.add("draws",JSON.toJsonTree(BlackHoleRender.draws));
            Files.writeString(output.resolve("results.json"),JSON.toJson(data));
            Files.writeString(control(mc).resolve("status.json"),JSON.toJson(data));
        }catch(Exception ex){System.err.println("[BLACK-HOLE] Evidence write failed: "+ex);}
    }
    private static final class Showcase extends Screen {
        private final ItemStack[] samples={
            sample(Items.DIAMOND_SWORD,null,false,false),sample(Items.DIAMOND_SWORD,"violet",true,false),
            sample(Items.DIAMOND_SWORD,"gold",false,false),sample(Items.DIAMOND_SWORD,"violet",true,false),
            sample(Items.DIAMOND_BLOCK,null,false,false),sample(Items.DIAMOND_BLOCK,"cyan",false,false),
            sample(Items.APPLE,"violet",false,false),sample(Items.DIAMOND_SWORD,"violet",true,true),
            sample(Items.BOW,null,false,false),sample(Items.BOW,"violet",false,false),
            sample(Items.OAK_STAIRS,"gold",false,false),sample(Items.DIAMOND_AXE,"cyan",false,false)
        };
        private final String[] labels={"Vanilla sword","Purple core A","Black core B","Purple core A",
            "Vanilla cube","Black-hole 3D cube","Black-hole apple","Native glint",
            "Vanilla bow","Black-hole bow","3D stairs","Black-hole axe"};
        Showcase(){super(Component.literal("Black Hole Material Lab"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
            graphics.fill(0,0,width,height,0xFF10151E);
            graphics.drawCenteredString(font,"BLACK HOLE / Forge 1.20.1",width/2,6,0xE4EEFF);
            int top=27,cellW=width/4,cellH=(height-top-10)/3;
            for(int i=0;i<samples.length;i++) {
                int x=(i%4)*cellW,y=top+(i/4)*cellH;
                graphics.fill(x+2,y, x+cellW-2,y+cellH-2,0xFF1B2330);
                float size=Math.max(16,Math.min(cellW-12,cellH-15));
                graphics.pose().pushPose();
                graphics.pose().translate(x+cellW/2f-size/2f,y+2,0);
                graphics.pose().scale(size/16f,size/16f,1);
                graphics.renderItem(samples[i],0,0);
                graphics.pose().popPose();
                graphics.drawCenteredString(font,labels[i],x+cellW/2,y+cellH-11,0xBCC9E0);
            }
            graphics.flush();
            super.render(graphics,mouseX,mouseY,partial);
        }
    }
}
