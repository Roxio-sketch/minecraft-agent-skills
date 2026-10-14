package dev.energyvfx;

import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Live 20 Hz movement in a fresh world; compares the installed path with packet bounds. */
final class MotionQa {
 private static final String[] CASES={"off-moving","baseline-moving","optimized-moving","baseline-moving-repeat","optimized-moving-repeat","baseline-standing","optimized-standing"};
 private static final boolean NEAR=Boolean.getBoolean("energyvfx.motionNearCamera");
 private static final Path OUTPUT=Path.of(System.getProperty("energyvfx.qa.output","validation")).resolve("motion-"+(NEAR?"near-":"")+(PerformanceQa.FIRST_PERSON?"first-":"back-")+System.getProperty("energyvfx.qa.width","960"));
 private static boolean started,initialized;private static int menu,setupTicks,phase,caseTicks,frames;private static long began;private static String world;
 private static CompletableFuture<Void> setup,originSetup;private static double distance;private static net.minecraft.world.phys.Vec3 previous,startPosition;private static boolean originPending;private static int originTicks;
 private static final JsonArray cases=new JsonArray();
 static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||PerformanceQa.done)return;var mc=Minecraft.getInstance();try{
  if(began==0)began=System.nanoTime();if(System.nanoTime()-began>420_000_000_000L)throw new IllegalStateException("Motion QA exceeded seven minutes");
  if(!started){if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||++menu<30)return;started=true;Files.createDirectories(OUTPUT);mc.options.pauseOnLostFocus=false;GLFW.glfwHideWindow(mc.getWindow().getWindow());GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),Integer.getInteger("energyvfx.qa.width",960),Integer.getInteger("energyvfx.qa.height",540));
   world="ENERGY_MOTION_QA_"+System.currentTimeMillis();GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
   mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(43,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
  if(!initialized){initialized=true;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.renderDistance().set(3);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(260);mc.options.setCameraType(PerformanceQa.FIRST_PERSON?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);mc.setScreen(null);
   setup=new CompletableFuture<>();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{try{var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);if(NEAR)for(var pos:net.minecraft.core.BlockPos.betweenClosed(-12,4,0,12,12,0))level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.GRAY_CONCRETE.defaultBlockState());var p=server.getPlayerList().getPlayer(id);p.getAbilities().mayfly=p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(.5,7,NEAR?1.7:6.5);setup.complete(null);}catch(Exception x){setup.completeExceptionally(x);}});EnergyVfx.Client.owner=mc.level;EnergyVfx.Client.active=false;return;}
  if(!setup.isDone())return;setup.join();mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(0,0,0);
  mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;mc.player.setYHeadRot(0);mc.player.yHeadRotO=0;mc.player.yBodyRot=mc.player.yBodyRotO=0;
  if(!PerformanceQa.ready){if(setupTicks++<40)return;PerformanceQa.ready=true;PerformanceQa.freeze=false;phase=0;begin(mc);}
  if(originPending){if(!originSetup.isDone())return;originSetup.join();if(mc.player.position().distanceTo(startPosition)>1e-6){originTicks=0;return;}if(++originTicks<3)return;
   if(!EnergyVfx.Client.start())throw new IllegalStateException("Motion renderer start failed");EnergyVfx.Client.recipe=Recipe.decode(Recipe.parse(Recipe.resource("presets/16.json")));EnergyVfx.Client.ticks=0;AuraEmitter.reset();AuraEmitter.bind(mc.player);EnergyVfx.Client.active=phase!=0;originPending=false;previous=mc.player.position();PerfTrace.resetFrameClock();}
  ++caseTicks;double theta=phase<5?caseTicks*.05*.65:0,x=.5+Math.sin(theta)*6,z=NEAR?1.7:.5+Math.cos(theta)*6;
  var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);if(p!=null)p.teleportTo(x,7,z);});
  var position=mc.player.position();if(previous!=null)distance+=position.distanceTo(previous);previous=position;
  if(caseTicks>=80&&!PerformanceQa.recording){distance=0;previous=position;PerformanceQa.recording=true;PerfTrace.resetFrameClock();}
 }catch(Exception x){fail(x);}}
 private static void begin(Minecraft mc)throws Exception{PerformanceQa.label=CASES[phase];PerformanceQa.optimized=PerformanceQa.label.startsWith("optimized");PerformanceQa.recording=false;caseTicks=frames=0;distance=0;previous=null;
  EnergyVfx.Client.active=false;AuraEmitter.reset();originPending=true;originTicks=0;startPosition=new net.minecraft.world.phys.Vec3(.5,7,NEAR?1.7:6.5);originSetup=new CompletableFuture<>();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{try{var p=server.getPlayerList().getPlayer(id);if(p==null)throw new IllegalStateException("QA player missing");p.setDeltaMovement(0,0,0);p.teleportTo(startPosition.x,startPosition.y,startPosition.z);originSetup.complete(null);}catch(Exception x){originSetup.completeExceptionally(x);}});PerfTrace.resetFrameClock();}
 static void capture(RenderLevelStageEvent e){if(!PerformanceQa.ready||PerformanceQa.done||originPending||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;try{
  if(EnergyVfx.Client.failed||EnergyVfx.Client.lastGl!=0)throw new IllegalStateException("Motion renderer failed in "+PerformanceQa.label);++frames;
  PerfTrace.value("uploadedPackets",AuraEmitter.uploadCount);PerfTrace.value("volumeCoverage",OptimizedGpu.lastCoverage);
  PerfTrace.value("cameraDistanceMeters",e.getCamera().getPosition().distanceTo(Minecraft.getInstance().player.getEyePosition(e.getPartialTick())));
  PerfTrace.value("nearViewWeight",AuraEmitter.nearViewWeight);PerfTrace.value("nearViewThinned",AuraEmitter.nearViewThinned);
  if(caseTicks<280||PerfTrace.count(PerformanceQa.label)<40)return;PerformanceQa.recording=false;
  PerformanceQa.screenshot(OUTPUT.resolve(PerformanceQa.label+".png"));JsonObject row=new JsonObject();row.addProperty("case",PerformanceQa.label);row.addProperty("clientTicks",caseTicks);row.addProperty("renderedFrames",frames);row.addProperty("travelledMeters",distance);row.addProperty("packets",AuraEmitter.packetCount);row.addProperty("glError",EnergyVfx.Client.lastGl);row.addProperty("warmupTicks",80);row.addProperty("measurementTicks",200);row.addProperty("originConfirmed",true);JsonArray origin=new JsonArray();origin.add(startPosition.x);origin.add(startPosition.y);origin.add(startPosition.z);row.add("startPosition",origin);cases.add(row);
  if(phase<5&&distance<5)throw new IllegalStateException("Actual client player movement was not observed");
  write(false);if(++phase==CASES.length){PerformanceQa.done=true;write(true);Minecraft.getInstance().stop();}else begin(Minecraft.getInstance());
 }catch(Exception x){fail(x);}}
 private static void write(boolean complete)throws Exception{JsonObject r=PerfTrace.report();r.addProperty("completed",complete);r.addProperty("world",world);r.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));var target=Minecraft.getInstance().getMainRenderTarget();r.addProperty("width",target.width);r.addProperty("height",target.height);r.addProperty("camera",Minecraft.getInstance().options.getCameraType().name());r.addProperty("scope","Fresh dev world; identical client-confirmed starting origin before emitter bind; actual client/server movement, unfrozen emitter/time, 80 tick warmup then 200 tick measurement; travelledMeters covers measurement only; other running applications may contend for GPU");r.add("movementCases",cases);Files.writeString(OUTPUT.resolve("comparison.json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));}
 private static void fail(Exception x){PerformanceQa.done=true;try{Files.createDirectories(OUTPUT);write(false);Files.writeString(OUTPUT.resolve("failure.txt"),x.toString());}catch(Exception ignored){}com.mojang.logging.LogUtils.getLogger().error("Motion QA failed",x);Minecraft.getInstance().stop();}
}
