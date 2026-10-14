package dev.cosmicmaterial;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
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
@Mod.EventBusSubscriber(modid=CosmicMaterial.ID,value=Dist.CLIENT)
public final class CosmicAudit {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] CASES={"gui-frozen","gui-time200","gui-reloaded","hand-right",
        "hand-left","third-front","third-back","bow-idle","bow-pulling","ground-and-frame"};
    private static final List<UUID> spawned = new ArrayList<>();
    private static final JsonArray results = new JsonArray();
    private static Path output;
    private static long requestStamp=-1;
    private static int pollTicks, index=-1, ticks, frames;
    private static boolean running, captureReady;
    private static boolean fantasyRun;
    private static boolean outlineRun;
    private static String fantasyFocus;
    private static final String[] FANTASY_IDS={"rune-vault","liquid-mithril","jade-veins",
        "gilded-porcelain","iridescent-silk","folded-ember"};
    private static CompletableFuture<?> pending;
    private static CompoundTag playerBackup;
    private static GameType oldMode;
    private static double oldX,oldY,oldZ;
    private static float oldYaw,oldPitch;
    private static boolean oldHide,oldPause,oldBob;
    private static HumanoidArm oldHand;
    private static CameraType oldCamera;
    private static int oldFov,oldSelectedSlot;
    private static int oldGuiScale,oldWidth,oldHeight;
    private static boolean oldFullscreen;
    private static JsonObject record;
    private static JsonObject restoration = new JsonObject();
    private static double testX,testY,testZ;

    private static Path control(Minecraft mc) {return mc.gameDirectory.toPath().resolve("cosmic-material-lab");}
    public static ItemStack sample(Item item,String palette,boolean blade,boolean foil) {
        ItemStack stack=new ItemStack(item);
        if (palette!=null) {
            CompoundTag material=new CompoundTag();
            material.putString("Palette",palette);
            if(blade) material.putString("Mask","blade");
            stack.getOrCreateTag().put("CosmicMaterial",material);
        }
        if(foil) stack.enchant(Enchantments.UNBREAKING,1);
        return stack;
    }
    public static ItemStack fantasySample(Item item,int mode,boolean blade,boolean foil) {
        ItemStack stack=sample(item,"violet",blade,foil);
        stack.getOrCreateTag().getCompound("CosmicMaterial").putString("Material",fantasyFocus==null?FANTASY_IDS[mode]:fantasyFocus);
        return stack;
    }
    public static ItemStack outlineSample(Item item,boolean foil) {
        ItemStack stack=sample(item,null,false,foil);
        CompoundTag outline=new CompoundTag();outline.putFloat("Width",.008f);
        outline.putInt("Type",0);outline.putInt("Color",0xff55e6ff);
        stack.getOrCreateTag().put("OutlineMaterial",outline);
        return stack;
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("cosmicmaterial")
            .then(literal("showcase").executes(c->{Minecraft.getInstance().setScreen(new Showcase());return 1;}))
            .then(literal("fantasy").executes(c->{fantasyFocus=null;Minecraft.getInstance().setScreen(new FantasyShowcase());return 1;}))
            .then(literal("outline").executes(c->{Minecraft.getInstance().setScreen(new OutlineScreen());return 1;}))
            .then(literal("verifyfantasy").executes(c->{start(Minecraft.getInstance(),true);return 1;}))
            .then(literal("verifyoutline").executes(c->{start(Minecraft.getInstance(),false,null,true);return 1;}))
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
                        JsonObject requestData=JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                        String action=requestData.get("action").getAsString();
                        Files.delete(request);
                        if(action.equals("run")&&!running)start(mc);
                        else if(action.equals("showcase")&&!running)mc.setScreen(new Showcase());
                        else if(action.equals("outline-showcase")&&!running)mc.setScreen(new OutlineScreen());
                        else if(action.equals("fantasy-run")&&!running)start(mc,true);
                        else if(action.equals("outline-run")&&!running)start(mc,false,null,true);
                        else if(action.equals("flow-run")&&!running)start(mc,true,"fantasy-flow");
                        else if(action.equals("material-run")&&!running) {
                            String material=requestData.get("material").getAsString();
                            if(FantasyPresets.get(material)==null)throw new IllegalArgumentException("Unknown material: "+material);
                            start(mc,true,material);
                        }
                        else if(action.equals("flow-showcase")&&!running){fantasyFocus="fantasy-flow";mc.setScreen(new FantasyShowcase());}
                        else if(action.equals("flow-continuity")&&!running)FlowContinuityAudit.start(mc);
                        else if(action.equals("flow-continuity-hd")&&!running)FlowContinuityAudit.start(mc,2560,1440);
                        else if(action.equals("flow-motion-hd")&&!running)FlowMotionAudit.start(mc);
                        else if(action.equals("material-motion-hd")&&!running)FlowMotionAudit.start(mc,requestData.get("material").getAsString());
                        else if(action.equals("material-continuity-hd")&&!running)FlowContinuityAudit.start(mc,2560,1440,requestData.get("material").getAsString());
                        else if(action.equals("recover-run")&&!running)recoverRun(mc,requestData.get("run").getAsString());
                        else if(action.equals("fantasy-showcase")&&!running){fantasyFocus=null;mc.setScreen(new FantasyShowcase());}
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
            if(++ticks>600)throw new IllegalStateException("Scenario timeout: "+CASES[index]);
        } catch(Exception e) {fail(mc,e);}
    }
    private static void start(Minecraft mc) {
        start(mc,false);
    }
    private static void recoverRun(Minecraft mc,String run) throws Exception {
        if(!run.matches("[0-9]{8}-[0-9]{6}")||mc.getSingleplayerServer()==null||mc.player==null||
            !mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))
            throw new IllegalArgumentException("Recovery requires a saved local audit and ASTRA_VFX_TEST.");
        Path directory=control(mc).resolve("fantasy-runs").resolve(run);
        if(Files.exists(directory.resolve("recovery.json")))throw new IllegalStateException("Run already recovered.");
        CompoundTag backup=NbtIo.readCompressed(directory.resolve("restore-player.nbt").toFile());
        UUID id=mc.player.getUUID();
        if(!backup.getUUID("UUID").equals(id))throw new IllegalArgumentException("Recovery player mismatch.");
        mc.getSingleplayerServer().submit(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            if(!backup.getString("Dimension").equals(player.serverLevel().dimension().location().toString()))
                throw new IllegalStateException("Recovery dimension mismatch.");
            var pos=backup.getList("Pos",6);var rotation=backup.getList("Rotation",5);
            double x=pos.getDouble(0),y=pos.getDouble(1),z=pos.getDouble(2);
            JsonArray removed=new JsonArray();
            // Earlier interrupted runs predate persistent UUID manifests. Match
            // their exact generated position, item material and entity flags.
            for(int i=0;i<2;i++) {
                double ex=x-1.1+i*2,ey=y+6.2,ez=z+2.6;
                var box=new net.minecraft.world.phys.AABB(ex-.01,ey-.01,ez-.01,ex+.01,ey+.01,ez+.01);
                Item expected=i==0?Items.DIAMOND_SWORD:Items.DIAMOND_BLOCK;
                for(var entity:player.serverLevel().getEntitiesOfClass(ItemEntity.class,box)) {
                    var stack=entity.getItem();
                    if(entity.isNoGravity()&&stack.is(expected)&&stack.hasTag()&&
                        stack.getTag().getCompound("CosmicMaterial").getString("Material").equals("fantasy-flow")) {
                        removed.add(entity.getUUID().toString());entity.discard();
                    }
                }
            }
            BlockPos framePos=BlockPos.containing(x,y+6,z+4);
            for(var entity:player.serverLevel().getEntitiesOfClass(ItemFrame.class,new net.minecraft.world.phys.AABB(framePos).inflate(1))) {
                var stack=entity.getItem();
                if(entity.getPos().equals(framePos)&&entity.isInvisible()&&entity.saveWithoutId(new CompoundTag()).getBoolean("Fixed")&&
                    stack.is(Items.DIAMOND_SWORD)&&stack.hasTag()&&
                    stack.getTag().getCompound("CosmicMaterial").getString("Material").equals("fantasy-flow")){
                    removed.add(entity.getUUID().toString());entity.discard();
                }
            }
            player.stopUsingItem();player.getInventory().load(backup.getList("Inventory",10));
            player.getInventory().selected=backup.getInt("SelectedItemSlot");
            player.setGameMode(GameType.byId(backup.getInt("playerGameType")));
            player.getAbilities().loadSaveData(backup);player.onUpdateAbilities();
            player.connection.teleport(x,y,z,rotation.getFloat(0),rotation.getFloat(1));
            player.containerMenu.broadcastChanges();
            JsonObject report=new JsonObject();report.add("removedTestEntities",removed);
            report.addProperty("inventory",player.getInventory().save(new ListTag()).equals(backup.getList("Inventory",10)));
            report.addProperty("selectedSlot",player.getInventory().selected==backup.getInt("SelectedItemSlot"));
            report.addProperty("gameMode",player.gameMode.getGameModeForPlayer().getId()==backup.getInt("playerGameType"));
            report.addProperty("position",player.getX()==x&&player.getY()==y&&player.getZ()==z);
            try{Files.writeString(directory.resolve("recovery.json"),JSON.toJson(report));}
            catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((result,error)->{
            if(error!=null)error.printStackTrace();
            else System.out.println("[COSMIC-MATERIAL] Interrupted audit restored: "+run);
        });
    }
    private static void start(Minecraft mc,boolean fantasy) {
        start(mc,fantasy,null);
    }
    private static void start(Minecraft mc,boolean fantasy,String focus) {
        start(mc,fantasy,focus,false);
    }
    private static void start(Minecraft mc,boolean fantasy,String focus,boolean outline) {
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)
            throw new IllegalStateException("Enter the selected local save before running verification.");
        if(!mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))
            throw new IllegalStateException("Verification is restricted to ASTRA_VFX_TEST.");
        try {
            fantasyRun=fantasy;
            outlineRun=outline;
            fantasyFocus=focus;
            output=control(mc).resolve(outline?"outline-runs":fantasy?"fantasy-runs":"runs")
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            Files.createDirectories(output);
            oldHide=mc.options.hideGui; oldPause=mc.options.pauseOnLostFocus;
            oldBob=mc.options.bobView().get(); oldHand=mc.options.mainHand().get();
            oldFov=mc.options.fov().get(); oldCamera=mc.options.getCameraType();
            oldSelectedSlot=mc.player.getInventory().selected;
            oldGuiScale=mc.options.guiScale().get();oldWidth=mc.getWindow().getWidth();oldHeight=mc.getWindow().getHeight();
            oldFullscreen=mc.getWindow().isFullscreen();
            if(oldFullscreen)mc.getWindow().toggleFullScreen();
            // Update Minecraft's stored windowed size too: a pending fullscreen
            // transition otherwise restores the launcher size on the next swap.
            mc.options.guiScale().set(2);mc.getWindow().setWindowed(1280,720);mc.resizeDisplay();
            mc.options.pauseOnLostFocus=false;mc.options.bobView().set(false);mc.options.fov().set(70);
            results.asList().clear();spawned.clear();CosmicRender.contexts.clear();CosmicRender.draws.clear();OutlineRender.draws.clear();
            restoration=new JsonObject();
            running=true;index=-1;CosmicRender.frozenTime=0;
            UUID id=mc.player.getUUID();
            pending=mc.getSingleplayerServer().submit(()->{
                ServerPlayer player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
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
                    ItemStack item=outlineRun?outlineSample(i==0?Items.DIAMOND_SWORD:Items.DIAMOND_BLOCK,false):
                        fantasyRun?fantasySample(i==0?Items.DIAMOND_SWORD:Items.DIAMOND_BLOCK,i,i==0,false):
                        sample(i==0?Items.DIAMOND_SWORD:Items.DIAMOND_BLOCK,i==0?"violet":"cyan",i==0,false);
                    ItemEntity entity=new ItemEntity(level,testX-1.1+i*2.0,testY+1.2,testZ+2.6,item);
                    entity.setNoGravity(true);entity.setDeltaMovement(0,0,0);
                    entity.setPickUpDelay(Integer.MAX_VALUE);
                    level.addFreshEntity(entity);spawned.add(entity.getUUID());
                }
                ItemFrame frame=new ItemFrame(level,BlockPos.containing(testX,testY+1,testZ+4),Direction.NORTH);
                frame.setItem(outlineRun?outlineSample(Items.DIAMOND_SWORD,false):
                    fantasyRun?fantasySample(Items.DIAMOND_SWORD,2,false,false):sample(Items.DIAMOND_SWORD,"gold",false,false));
                CompoundTag data=frame.saveWithoutId(new CompoundTag());
                data.putBoolean("Fixed",true);data.putBoolean("Invisible",true);
                frame.load(data);
                level.addFreshEntity(frame);spawned.add(frame.getUUID());
            });
            System.out.println("[COSMIC-MATERIAL] Runtime audit started: "+output);
        }catch(Exception e){fail(mc,e);}
    }
    private static void next(Minecraft mc) {
        index++;
        if(index>=CASES.length){finish(mc);return;}
        ticks=frames=0;captureReady=false;
        record=new JsonObject();
        record.addProperty("case",CASES[index]);record.addProperty("generationBefore",outlineRun?OutlineRender.generation:CosmicRender.generation);
        CosmicRender.frozenTime=index==1?200:0;
        mc.options.hideGui=false;
        mc.options.mainHand().set(HumanoidArm.RIGHT);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.keyUse.setDown(false);
        mc.player.stopUsingItem();
        if(index<3) {
            mc.setScreen(outlineRun?new OutlineScreen():fantasyRun?new FantasyShowcase():new Showcase());
            if(index==2)pending=mc.reloadResourcePacks();
        }else {
            mc.setScreen(null);
            mc.player.getInventory().selected=0;
            mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
            if(index==4)mc.options.mainHand().set(HumanoidArm.LEFT);
            if(index==5)mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if(index==6)mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if(index==9)mc.options.hideGui=true;
            UUID id=mc.player.getUUID();
            ItemStack item=index>=7&&index<=8?sample(Items.BOW,"violet",false,false):
                index==9?ItemStack.EMPTY:sample(Items.DIAMOND_SWORD,"violet",true,false);
            if(outlineRun&&index<9)item=outlineSample(index>=7?Items.BOW:Items.DIAMOND_SWORD,false);
            if(fantasyRun&&index<9) item=fantasySample(index>=7?Items.BOW:Items.DIAMOND_SWORD,index-3,index<7,false);
            ItemStack selected=item;
            pending=mc.getSingleplayerServer().submit(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                player.stopUsingItem();
                player.getInventory().selected=0;
                player.getInventory().setItem(0,selected.copy());
                player.getInventory().setItem(1,new ItemStack(Items.ARROW,64));
                player.containerMenu.broadcastChanges();
                player.connection.teleport(testX,testY,testZ,0,0);
            });
        }
        writeProgress("running");
        System.out.println("[COSMIC-MATERIAL] Case "+CASES[index]);
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!running||index<0||captureReady||pending!=null)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(mc.getOverlay()!=null||ticks<45||++frames<35)return;
            if(index==8&&(!mc.player.isUsingItem()||mc.player.getTicksUsingItem()<20))return;
            Path file=output.resolve(CASES[index]+".png");
            try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(file);
                record.addProperty("width",image.getWidth());record.addProperty("height",image.getHeight());
            }
            record.addProperty("generationAfter",outlineRun?OutlineRender.generation:CosmicRender.generation);
            record.addProperty("glError",GL11.glGetError());
            record.addProperty("time",CosmicRender.frozenTime);
            record.addProperty("playerUsingItem",mc.player.isUsingItem());
            record.addProperty("ticksUsingItem",mc.player.getTicksUsingItem());
            record.addProperty("camera",mc.options.getCameraType().name());
            record.addProperty("mainHand",mc.options.mainHand().get().name());
            if(fantasyRun) record.addProperty("material",fantasyFocus!=null?fantasyFocus:index>=3&&index<9?FANTASY_IDS[index-3]:"six-presets");
            record.add("contexts",JSON.toJsonTree(CosmicRender.contexts));
            record.add("draws",JSON.toJsonTree(CosmicRender.draws));
            if(outlineRun)record.add("outlineDraws",JSON.toJsonTree(OutlineRender.draws));
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
        running=false;CosmicRender.frozenTime=-1;mc.options.keyUse.setDown(false);mc.player.stopUsingItem();
        pending=restore(mc);
        pending.whenComplete((v,e)->mc.execute(()->{
            restoreOptions(mc);
            if(e!=null){writeError(e);return;}
            writeProgress("completed");
            System.out.println("[COSMIC-MATERIAL] Audit complete; player and test entities restored.");
        }));
    }
    private static CompletableFuture<?> restore(Minecraft mc) {
        if(playerBackup==null||mc.getSingleplayerServer()==null)return CompletableFuture.completedFuture(null);
        UUID id=mc.player.getUUID();
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
            restoration.addProperty("inventory",player.getInventory().save(new ListTag()).equals(playerBackup.getList("Inventory",10)));
            restoration.addProperty("selectedSlot",player.getInventory().selected==playerBackup.getInt("SelectedItemSlot"));
            restoration.addProperty("gameMode",player.gameMode.getGameModeForPlayer()==oldMode);
            restoration.addProperty("position",player.getX()==oldX&&player.getY()==oldY&&player.getZ()==oldZ);
            restoration.addProperty("testEntitiesRemoved",spawned.stream().allMatch(entityId->{
                var entity=player.serverLevel().getEntity(entityId);return entity==null||entity.isRemoved();}));
        });
    }
    private static void restoreOptions(Minecraft mc) {
        mc.options.keyUse.setDown(false);
        if(mc.player!=null){mc.player.stopUsingItem();mc.player.getInventory().selected=oldSelectedSlot;}
        mc.options.hideGui=oldHide;mc.options.pauseOnLostFocus=oldPause;
        mc.options.bobView().set(oldBob);mc.options.mainHand().set(oldHand);
        mc.options.fov().set(oldFov);mc.options.setCameraType(oldCamera);
        mc.options.guiScale().set(oldGuiScale);mc.getWindow().setWindowed(oldWidth,oldHeight);mc.resizeDisplay();
        if(oldFullscreen&&!mc.getWindow().isFullscreen()){mc.getWindow().toggleFullScreen();mc.resizeDisplay();}
        mc.setScreen(null);
    }
    private static void fail(Minecraft mc,Exception error) {
        System.err.println("[COSMIC-MATERIAL] Audit failure: "+error);
        error.printStackTrace();
        if(running) {
            running=false;CosmicRender.frozenTime=-1;
            restore(mc).whenComplete((v,e)->mc.execute(()->{restoreOptions(mc);writeError(error);}));
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
            data.addProperty("suite",outlineRun?"eight-corner-outline":fantasyRun?"fantasy-materials":"original-cosmic");
            if(fantasyFocus!=null)data.addProperty("focusMaterial",fantasyFocus);
            data.addProperty("minecraft","1.20.1");data.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));
            data.addProperty("glVersion",GL11.glGetString(GL11.GL_VERSION));
            data.addProperty("world","ASTRA_VFX_TEST");data.addProperty("shaderGeneration",outlineRun?OutlineRender.generation:CosmicRender.generation);
            data.addProperty("wrappedItemRoots",CosmicModels.wrappedCount);
            data.addProperty("selectedCase",index<CASES.length&&index>=0?CASES[index]:"done");
            data.addProperty("nativeEntityTestCount",spawned.size());
            data.add("testEntityIds",JSON.toJsonTree(spawned));
            data.add("restoration",restoration);
            data.add("results",results);
            data.add("contexts",JSON.toJsonTree(CosmicRender.contexts));
            data.add("draws",JSON.toJsonTree(CosmicRender.draws));
            if(outlineRun)data.add("outlineDraws",JSON.toJsonTree(OutlineRender.draws));
            Files.writeString(output.resolve("results.json"),JSON.toJson(data));
            Files.writeString(control(mc).resolve("status.json"),JSON.toJson(data));
        }catch(Exception ex){System.err.println("[COSMIC-MATERIAL] Evidence write failed: "+ex);}
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
        private final String[] labels={"Vanilla sword","Violet blade A","Gold full B","Violet blade A",
            "Vanilla cube","Cosmic 3D cube","Cosmic apple","Native glint",
            "Vanilla bow","Cosmic bow","3D stairs","Cosmic axe"};
        Showcase(){super(Component.literal("Cosmic Material Lab"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
            graphics.fill(0,0,width,height,0xFF10151E);
            graphics.drawCenteredString(font,"COSMIC MATERIAL / Forge 1.20.1",width/2,6,0xE4EEFF);
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
    private static final class FantasyShowcase extends Screen {
        private final String[] labels={"Rune vault","Liquid mithril","Jade veins","Gilded porcelain","Iridescent silk","Folded ember"};
        private final String[] flowLabels={"Sword / blade mask","Axe / full","Bow / full","Apple / full","Block / full","Armor item / GUI only"};
        private final Item[] flowItems={Items.DIAMOND_SWORD,Items.DIAMOND_AXE,Items.BOW,Items.APPLE,Items.DIAMOND_BLOCK,Items.NETHERITE_CHESTPLATE};
        FantasyShowcase(){super(Component.literal("Fantasy Material Lab"));}
        @Override public boolean isPauseScreen(){return false;}
        private void item(GuiGraphics g,ItemStack stack,float x,float y,float size) {
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(size/16f,size/16f,1);
            g.renderItem(stack,0,0);g.pose().popPose();
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            g.fill(0,0,width,height,0xFF121719);
            g.drawCenteredString(font,(fantasyFocus==null?"FANTASY MATERIALS":fantasyFocus)+" / Forge 1.20.1",width/2,7,0xE5EEEC);
            int cellW=width/3,cellH=(height-30)/2;
            for(int i=0;i<6;i++) {
                int x=(i%3)*cellW,y=28+(i/3)*cellH;
                g.fill(x+3,y,x+cellW-3,y+cellH-4,0xFF222B2E);
                float size=Math.min(cellW*0.52f,cellH-34);
                item(g,fantasySample(fantasyFocus==null?Items.DIAMOND_SWORD:flowItems[i],i,fantasyFocus==null||i==0,false),x+6,y+7,size);
                item(g,fantasySample(Items.DIAMOND_BLOCK,i,false,false),x+cellW*0.57f,y+14,cellW*0.35f);
                item(g,fantasySample(Items.APPLE,i,false,false),x+cellW*0.61f,y+cellH-48,16);
                item(g,fantasySample(Items.DIAMOND_SWORD,i,false,true),x+cellW*0.77f,y+cellH-57,32);
                g.drawCenteredString(font,fantasyFocus==null?labels[i]:flowLabels[i],x+cellW/2,y+cellH-17,0xD9E8E3);
            }
            g.flush();super.render(g,mouseX,mouseY,partial);
        }
    }
}
