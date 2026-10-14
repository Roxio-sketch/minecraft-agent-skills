package dev.energyvfx;

import com.google.gson.*;
import com.mojang.blaze3d.platform.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Fixed camera/packet/time comparison, in a newly created dev world only. */
@Mod.EventBusSubscriber(modid=EnergyVfx.ID,value=Dist.CLIENT)
public final class PerformanceQa {
 static final boolean ENABLED=Boolean.getBoolean("energyvfx.perfQa"),COMPARE=Boolean.getBoolean("energyvfx.perfCompare");
 static final boolean FLOW=Boolean.getBoolean("energyvfx.flowQa"),FIRST_PERSON=Boolean.getBoolean("energyvfx.firstPersonQa");
 static final boolean QUALITY_ONLY=Boolean.getBoolean("energyvfx.qualityQa");
 static final boolean MOTION=Boolean.getBoolean("energyvfx.motionQa");
 static final boolean BUILDUP=Boolean.getBoolean("energyvfx.buildupQa");
 static final boolean ZOOM=Boolean.getBoolean("energyvfx.zoomQa");
 static final Path OUTPUT=Path.of(System.getProperty("energyvfx.qa.output","validation")).resolve(FLOW?(FIRST_PERSON?"flow-first-person-":"flow-front-")+System.getProperty("energyvfx.qa.width","960"):"performance");
 static boolean ready,done,freeze,recording;static String label="setup";
 static boolean optimized;private static boolean started,initialized;
 private static int menuTicks,setupTicks,phase,frames;private static long began;
 private static CompletableFuture<Void> setup;private static String world;
 private static final String[] CASES={"off","baseline","optimized","baseline-repeat","optimized-repeat","baseline-bloom-off","optimized-bloom-off"};
 static float partial(float actual){return ENABLED&&freeze?.5f:actual;}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(!ENABLED||done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();try{
  if(ZOOM){ZoomMotionQa.tick(e);return;}if(BUILDUP){StationaryMotionQa.tick(e);return;}if(MOTION){MotionQa.tick(e);return;}
  if(began==0)began=System.nanoTime();if(System.nanoTime()-began>420_000_000_000L)throw new IllegalStateException("Performance QA exceeded seven minutes");
  if(!started){if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||++menuTicks<30)return;started=true;Files.createDirectories(OUTPUT);mc.options.pauseOnLostFocus=false;GLFW.glfwHideWindow(mc.getWindow().getWindow());if(FLOW){GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),Integer.getInteger("energyvfx.qa.width",960),Integer.getInteger("energyvfx.qa.height",540));}world="ENERGY_PERF_QA_"+System.currentTimeMillis();GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(43,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
  if(!initialized){initialized=true;mc.options.hideGui=!FIRST_PERSON;mc.options.pauseOnLostFocus=false;mc.options.bobView().set(false);mc.options.renderDistance().set(3);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(260);mc.options.setCameraType(FIRST_PERSON?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);mc.setScreen(null);setup=new CompletableFuture<>();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{try{var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);for(BlockPos p:BlockPos.betweenClosed(-8,4,-7,8,14,-7))level.setBlockAndUpdate(p,Blocks.GRAY_CONCRETE.defaultBlockState());var player=server.getPlayerList().getPlayer(id);player.getAbilities().flying=true;player.getAbilities().mayfly=true;player.onUpdateAbilities();player.teleportTo(.5,7,.5);player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));setup.complete(null);}catch(Exception x){setup.completeExceptionally(x);}});EnergyVfx.Client.owner=mc.level;EnergyVfx.Client.active=false;return;}
  if(!setup.isDone())return;setup.join();mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;mc.player.setYHeadRot(0);mc.player.yHeadRotO=0;mc.player.yBodyRot=0;mc.player.yBodyRotO=0;
  if(setupTicks++==0){if(!EnergyVfx.Client.start())throw new IllegalStateException("Emitter start failed");EnergyVfx.Client.recipe=Recipe.decode(Recipe.parse(Recipe.resource("presets/16.json")));EnergyVfx.Client.ticks=0;AuraEmitter.reset();AuraEmitter.bind(mc.player);}
  if(!ready&&EnergyVfx.Client.ticks>=100){freeze=true;ready=true;phase=0;if(QUALITY_ONLY){QualityQa.enabled=true;optimized=true;label="quality";}else beginCase();}
  if(QualityQa.enabled){if(EnergyVfx.Client.failed)throw new IllegalStateException("Quality renderer failed; see render log");QualityQa.tick();}
 }catch(Exception x){fail(x);}}
 private static void beginCase(){label=CASES[phase];frames=0;recording=false;optimized=label.startsWith("optimized");EnergyVfx.Client.active=phase!=0;if(phase>=5)EnergyVfx.Client.recipe.values[21]=0;PerfTrace.resetFrameClock();}
 @SubscribeEvent public static void capture(RenderLevelStageEvent e){if(!ENABLED||!ready||done||QualityQa.enabled||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;if(ZOOM){ZoomMotionQa.capture(e);return;}if(BUILDUP){StationaryMotionQa.capture(e);return;}if(MOTION){MotionQa.capture(e);return;}try{if(EnergyVfx.Client.failed||EnergyVfx.Client.lastGl!=0)throw new IllegalStateException("Renderer failed during "+label);frames++;if(frames==(FLOW?(FIRST_PERSON?30:60):100)){recording=true;PerfTrace.resetFrameClock();}if(frames<(FLOW?(FIRST_PERSON?90:200):360))return;if(PerfTrace.count(label)<(FLOW?(FIRST_PERSON?45:120):220))return;recording=false;screenshot(OUTPUT.resolve(label+".png"));write(false);if(++phase>=(FLOW?5:COMPARE?CASES.length:2)){done=true;write(true);Minecraft.getInstance().stop();return;}beginCase();}catch(Exception x){fail(x);}}
 static void screenshot(Path path)throws Exception{var target=Minecraft.getInstance().getMainRenderTarget();int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GlStateManager._bindTexture(target.getColorTextureId());try(NativeImage image=new NativeImage(target.width,target.height,false)){image.downloadTexture(0,false);image.flipY();image.writeToFile(path);}finally{GlStateManager._bindTexture(old);}}
 static void write(boolean complete)throws Exception{JsonObject report=PerfTrace.report();report.addProperty("completed",complete);report.addProperty("world",world);report.addProperty("scope","Isolated vanilla Forge dev world, fixed smoke packets/time/camera; no existing saves opened");report.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));var target=Minecraft.getInstance().getMainRenderTarget();report.addProperty("width",target.width);report.addProperty("height",target.height);report.addProperty("flowCacheTest",FLOW);report.addProperty("firstPerson",FIRST_PERSON);report.addProperty("cacheSupported",FlowCache.supported());report.addProperty("warmupFrames",FLOW?(FIRST_PERSON?30:60):100);report.addProperty("measurementFrames",FLOW?(FIRST_PERSON?60:140):260);report.addProperty("firstPersonThinned",AuraEmitter.firstPersonThinned);report.addProperty("nearEyeHidden",AuraEmitter.nearEyeHidden);report.addProperty("nearEyeFaded",AuraEmitter.nearEyeFaded);report.addProperty("uploadedPackets",AuraEmitter.uploadCount);report.addProperty("cacheActive",FlowCache.active);report.addProperty("packets",AuraEmitter.packetCount);report.addProperty("resolution",EnergyVfx.Client.recipe.values[37]);report.addProperty("steps",EnergyVfx.Client.recipe.values[36]);report.addProperty("volumeScreenCoverage",OptimizedGpu.lastCoverage);report.addProperty("sceneCopyCoverage",OptimizedGpu.lastCopyCoverage);report.addProperty("glError",EnergyVfx.Client.lastGl);Files.writeString(OUTPUT.resolve(COMPARE?"comparison.json":"baseline.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
 private static void fail(Exception x){done=true;try{Files.createDirectories(OUTPUT);Files.writeString(OUTPUT.resolve("failure.txt"),x.toString());write(false);}catch(Exception ignored){}com.mojang.logging.LogUtils.getLogger().error("Performance QA failed",x);Minecraft.getInstance().stop();}
}
