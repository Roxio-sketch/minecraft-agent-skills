package dev.alchemyvfx;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** QA fixtures exist only in a new ALchemy QA save and are excluded from production. */
@Mod.EventBusSubscriber(modid=AlchemyVfx.ID,value=Dist.CLIENT)
public final class AutoQa {
    private static final Path OUTPUT=Path.of(System.getProperty("alchemyvfx.qa.output","evidence/game"));
    private static final BlockPos CORE=new BlockPos(0,4,0);
    private static final boolean FIREBED=System.getProperty("alchemyvfx.qa.suite","acceptance").equals("firebed");
    private static final String[] CASES=FIREBED?new String[]{"closed-near-baseline","closed-near-firebed","closed-base-gap-firebed","closed-depth-occlusion"}:new String[]{"preheat-front","refine-front","condense-front","complete-burst","complete-reveal","refine-side","depth-occlusion","unformed-gated","reload","off-legacy-return"};
    private static boolean started,ready,done;
    private static int menuTicks,ticks,frames,scenario=-1,shaderBefore;
    private static CompletableFuture<Void> fixture,reload;
    private static final JsonArray records=new JsonArray();
    private static java.lang.reflect.Method cameraSetter;
    private static String worldName;
    private static volatile long expectedStart,expectedSeed;
    private static volatile boolean expectedBrewing,expectedResult;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try{
            mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if(!started){
                if(mc.screen instanceof AccessibilityOnboardingScreen&&mc.getOverlay()==null){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());}
                if(++menuTicks>2400)throw new IllegalStateException("QA menu timeout");
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||menuTicks<20)return;
                started=true;Files.createDirectories(OUTPUT);worldName="ALCHEMY_VFX_QA_"+System.currentTimeMillis();
                GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(worldName,new LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(17,false,false),registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(!ready){
                if(fixture==null){
                    mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.fovEffectScale().set(0.);mc.options.gamma().set(1.);mc.options.renderDistance().set(3);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);
                    fixture=CompletableFuture.runAsync(()->{try{build(mc.getSingleplayerServer());}catch(Exception failed){throw new RuntimeException(failed);}},mc.getSingleplayerServer());return;
                }
                if(!fixture.isDone())return;fixture.join();if(mc.level.getBlockEntity(CORE)==null)return;
                ready=true;fixture=null;advance();return;
            }
            if(fixture!=null&&fixture.isDone()){
                fixture.join();
                // The server future completes before its update packet reaches the client.
                // Let networking tick until the exact host snapshot arrives, then freeze the QA world.
                var be=mc.level.getBlockEntity(CORE);var f=HostAdapter.read(be,0);
                if(!FIREBED&&scenario==7){if(be==null||f!=null)return;}
                else if(f==null||f.brewing()!=expectedBrewing||f.hasResult()!=expectedResult
                        ||f.startTick()!=expectedStart||f.visualSeed()!=expectedSeed){
                    if(++ticks>2400)throw new IllegalStateException("Client snapshot timeout: "+CASES[scenario]);return;
                }
                fixture=null;Client.load();Client.active=FIREBED?scenario!=0:scenario!=9;Client.failed=false;
                Client.furnacesForQa();frames=0;ticks=0;
                // Freeze only this disposable QA world so every stage is visually comparable.
                mc.setScreen(new PauseScreen(true));
            }
            if(++ticks>2400)throw new IllegalStateException("QA timeout: "+CASES[scenario]);
            mc.player.setDeltaMovement(0,0,0);mc.player.getAbilities().flying=true;
        }catch(Exception failure){fail(failure);}
    }
    private static void build(MinecraftServer server)throws Exception{
        var level=server.overworld();level.setDayTime(6000);
        for(BlockPos p:BlockPos.betweenClosed(-12,3,-12,12,3,12))level.setBlockAndUpdate(p,Blocks.SMOOTH_STONE.defaultBlockState());
        for(BlockPos p:BlockPos.betweenClosed(-9,4,5,9,13,5))level.setBlockAndUpdate(p,Blocks.BLACK_CONCRETE.defaultBlockState());
        Class<?> structure=Class.forName(HostAdapter.HOST+"AlchemyFurnaceStructure");
        Block core=BuiltInRegistries.BLOCK.get(new ResourceLocation("wuxingxiantu","alchemy_furnace_mid"));
        level.setBlockAndUpdate(CORE,core.defaultBlockState());
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<4;y++){
            var state=(net.minecraft.world.level.block.state.BlockState)structure.getMethod("expected",int.class,int.class,int.class).invoke(null,x,z,y);
            if(state!=null)level.setBlockAndUpdate(CORE.offset(x,y,z),state);
        }
        structure.getMethod("refreshAround",Level.class,BlockPos.class).invoke(null,level,CORE);
        server.getPlayerList().getPlayers().forEach(player->{player.teleportTo(.5,5.5,-8);player.setYRot(0);player.setXRot(0);player.getAbilities().flying=true;player.onUpdateAbilities();});
    }
    private static void advance()throws Exception{
        if(++scenario>=CASES.length){done=true;Files.writeString(OUTPUT.resolve("game-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(records));Minecraft.getInstance().stop();return;}
        var mc=Minecraft.getInstance();mc.setScreen(null);ticks=frames=0;
        fixture=CompletableFuture.runAsync(()->{try{state(mc.getSingleplayerServer(),FIREBED?(scenario==3?6:1):scenario);}catch(Exception failure){throw new RuntimeException(failure);}},mc.getSingleplayerServer());
        if(!FIREBED&&scenario==8){shaderBefore=VolumeGpu.generation;reload=mc.reloadResourcePacks();}
    }
    private static void state(MinecraftServer server,int index)throws Exception{
        var level=server.overworld();var be=level.getBlockEntity(CORE);if(be==null)throw new IllegalStateException("Furnace fixture missing");
        Class<?> entity=be.getClass();
        Class.forName(HostAdapter.HOST+"network.service.AlchemyService").getMethod("cancelAlchemyAt",net.minecraft.server.level.ServerLevel.class,BlockPos.class).invoke(null,level,CORE);
        CompoundTag tag=be.saveWithFullMetadata();long now=level.getGameTime();
        boolean brewing=index!=3&&index!=4&&index!=7;
        tag.putBoolean("Brewing",brewing);tag.putBoolean("NeedsSpiritFuel",false);tag.putString("ActiveFireId","wuxingxiantu:molten_gold");
        tag.putLong("VisualSeed",17+index);tag.putLong("StartTick",now-(index==0?7500:index==2?85000:40000));tag.putLong("FinishTick",now+(index==0?92500:index==2?15000:60000));
        tag.putLong("CompletionTick",index==3?now-18:index==4?now-49:-1);tag.putInt("AlchemyOutcome",index==3||index==4?1:0);
        ItemStack pill=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("wuxingxiantu","aura_pill")));
        tag.put("CompletedResult",(index==3||index==4?pill:ItemStack.EMPTY).save(new CompoundTag()));
        UUID request=UUID.randomUUID();tag.putUUID("BrewingRequestId",request);entity.getMethod("load",CompoundTag.class).invoke(be,tag);
        if(brewing){
            Class<?> jobType=Class.forName(HostAdapter.HOST+"network.service.CraftingJobs$PendingAlchemy");
            Object job=jobType.getConstructors()[0].newInstance(server.getPlayerList().getPlayers().get(0).getUUID(),level.dimension(),CORE,tag.getLong("FinishTick"),pill,request,UUID.randomUUID(),2,new double[]{1,1,1,1,1},new ResourceLocation("wuxingxiantu","qa_fixture"),1.,0);
            Class<?> jobsType=Class.forName(HostAdapter.HOST+"network.service.CraftingJobs");Object jobs=jobsType.getMethod("get",MinecraftServer.class).invoke(null,server);jobsType.getMethod("add",jobType).invoke(jobs,job);
        }
        entity.getMethod("sync").invoke(be);
        expectedStart=tag.getLong("StartTick");expectedSeed=tag.getLong("VisualSeed");expectedBrewing=brewing;expectedResult=index==3||index==4;
        if(index==6){for(BlockPos p:BlockPos.betweenClosed(-1,4,-4,1,10,-4))level.setBlockAndUpdate(p,Blocks.IRON_BLOCK.defaultBlockState());}
        else for(BlockPos p:BlockPos.betweenClosed(-1,4,-4,1,10,-4))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
        if(index==7)level.destroyBlock(CORE.offset(2,2,2),false);
        if(index==8){build(server);state(server,1);}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        if(done||!ready||scenario<0)return;
        try{double yaw=(FIREBED?scenario==2:scenario==5)?(FIREBED?Math.PI:Math.PI*.5):0;Vec3 position;
            if(FIREBED&&scenario==2){position=new Vec3(1.3,4.40,5.6);}
            else if(FIREBED){double distance=scenario==3?9:5.1;position=new Vec3(.5,6.3,.5).add(Math.sin(yaw)*distance,0,-Math.cos(yaw)*distance);}
            else{Vec3 center=new Vec3(.5,6.25,.5);position=center.add(Math.sin(yaw)*9,1.1,-Math.cos(yaw)*9);}
            event.setYaw((float)(yaw*180/Math.PI));event.setPitch(FIREBED?(scenario==2?0:2):7);
            if(cameraSetter==null){cameraSetter=Camera.class.getDeclaredMethod("setPosition",Vec3.class);cameraSetter.setAccessible(true);}cameraSetter.invoke(event.getCamera(),position);
        }catch(Exception failure){fail(failure);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void capture(RenderLevelStageEvent event){
        if(done||!ready||fixture!=null||scenario<0||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();try{
            if(mc.getOverlay()!=null||++frames<12)return;if(!FIREBED&&scenario==8&&(reload==null||!reload.isDone()))return;
            Client.furnacesForQa();
            if(Client.failed||VolumeGpu.failed||VolumeGpu.lastGl!=0)throw new IllegalStateException("Renderer failed: "+Client.lastError);
            boolean volumeExpected=FIREBED?scenario!=0:scenario!=7&&scenario!=9;
            if(volumeExpected&&VolumeGpu.draws<1)throw new IllegalStateException("Expected a real volume draw");
            if(!volumeExpected&&VolumeGpu.draws!=0)throw new IllegalStateException("Gating failed");
            if(!FIREBED&&scenario==8&&VolumeGpu.generation<=shaderBefore)throw new IllegalStateException("Resource reload did not regenerate GPU shader");
            Path screenshot=OUTPUT.resolve(CASES[scenario]+".png");var target=mc.getMainRenderTarget();int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);com.mojang.blaze3d.platform.GlStateManager._bindTexture(target.getColorTextureId());
            try(NativeImage image=new NativeImage(target.width,target.height,false)){image.downloadTexture(0,false);image.flipY();image.writeToFile(screenshot);}finally{com.mojang.blaze3d.platform.GlStateManager._bindTexture(bound);}
            JsonObject record=Client.status();record.addProperty("case",CASES[scenario]);record.addProperty("world",worldName);record.addProperty("screenshot",screenshot.toString());record.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));
            if(FIREBED&&scenario==1){JsonObject probes=FieldProbe.run(Client.recipe,mc.level.getGameTime()*.05);record.add("fieldProbes",probes);Files.writeString(OUTPUT.resolve("field-probes.json"),new GsonBuilder().setPrettyPrinting().create().toJson(probes));if(probes.get("centerLowDensity").getAsDouble()<=.02)throw new IllegalStateException("No low center firebed density");if(probes.get("centerHighMaximumDensity").getAsDouble()!=0)throw new IllegalStateException("Center fire gas entered the raised solid floor");}
            records.add(record);advance();
        }catch(Exception failure){fail(failure);}
    }
    private static void fail(Exception failure){done=true;try{
        Files.createDirectories(OUTPUT);Files.writeString(OUTPUT.resolve("failure.txt"),failure.toString());
        JsonObject state=Client.status();var mc=Minecraft.getInstance();state.addProperty("scenario",scenario);state.addProperty("frames",frames);state.addProperty("qaTicks",ticks);
        if(mc.level!=null){var be=mc.level.getBlockEntity(CORE);state.addProperty("clientCoreState",mc.level.getBlockState(CORE).toString());state.addProperty("clientCoreEntity",be==null?"null":be.getClass().getName());if(be!=null)state.addProperty("clientCoreTag",be.getUpdateTag().toString());}
        state.addProperty("expectedStart",expectedStart);state.addProperty("expectedSeed",expectedSeed);state.addProperty("expectedBrewing",expectedBrewing);state.addProperty("expectedResult",expectedResult);
        Files.writeString(OUTPUT.resolve("failure-status.json"),new GsonBuilder().setPrettyPrinting().create().toJson(state));
    }catch(Exception ignored){}com.mojang.logging.LogUtils.getLogger().error("Alchemy independent QA failed",failure);Minecraft.getInstance().stop();}
    private AutoQa() {}
}
