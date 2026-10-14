package dev.firecoil;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in, isolated fresh-world integration QA. No existing instance or save is opened. */
@Mod.EventBusSubscriber(modid=FireCoil.ID,value=Dist.CLIENT)
public final class AutoQa {
 static final boolean ENABLED=Boolean.getBoolean("firecoil.qa");
 private static final Path OUTPUT=Path.of(System.getProperty("firecoil.qa.output","evidence/game"));
 private static final String[] NAMES={"baseline","amber-body","blue-body","close-view","camera-inside-domain","moving","occlusion","reload","first-person","off-expired","weapon-third","weapon-swing","weapon-first","weapon-off"};
 private static boolean started,ready,done;private static int menuTicks,ticks,frames,scenario,shaderBefore;
 private static JsonObject defaults;private static CompletableFuture<Void> setup,reload;
 private static final JsonArray records=new JsonArray();private static java.lang.reflect.Method cameraSetter;
 private static String worldName;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(!ENABLED||done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();try{
  mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
  if(!started){if(mc.screen instanceof AccessibilityOnboardingScreen&&mc.getOverlay()==null){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());}
   if(++menuTicks>1200)throw new IllegalStateException("QA menu timeout: "+(mc.screen==null?"null":mc.screen.getClass().getSimpleName()));
   if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||menuTicks<20)return;started=true;Files.createDirectories(OUTPUT);worldName="FIRE_COIL_QA_"+System.currentTimeMillis();GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel(worldName,new LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
  if(!ready){if(setup==null){mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.fovEffectScale().set(0.);mc.options.gamma().set(1.);mc.options.renderDistance().set(3);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.setScreen(null);setup=CompletableFuture.runAsync(()->{var server=mc.getSingleplayerServer();var level=server.overworld();level.setDayTime(6000);for(BlockPos pos:BlockPos.betweenClosed(-7,3,-6,7,3,7))level.setBlockAndUpdate(pos,Blocks.SMOOTH_STONE.defaultBlockState());for(BlockPos pos:BlockPos.betweenClosed(-7,4,-5,7,10,-5))level.setBlockAndUpdate(pos,Blocks.BLACK_CONCRETE.defaultBlockState());for(int x=-3;x<=3;x+=3)level.setBlockAndUpdate(new BlockPos(x,6,1),Blocks.SEA_LANTERN.defaultBlockState());server.getPlayerList().getPlayers().forEach(p->{p.teleportTo(.5,4,.5);p.setYRot(0);p.setXRot(0);p.getAbilities().flying=true;p.onUpdateAbilities();});},mc.getSingleplayerServer());return;}if(!setup.isDone()||mc.player.position().distanceTo(new Vec3(.5,4,.5))>.5)return;setup.join();defaults=Recipe.parse(Client.bundledRecipe()).json();Client.recipe=Recipe.parse(defaults.toString());Client.core.clear();Client.active=false;ready=true;frames=0;ticks=0;if(Boolean.getBoolean("firecoil.qa.weaponOnly"))scenario=9;}
  mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.yBodyRot=0;mc.player.yBodyRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;if(scenario==11&&ticks%16==0)mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
  if(scenario==5){double x=.5+Math.sin(ticks*.065)*1.8;mc.player.setPos(x,4,.5);if(ticks%5==0)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().forEach(p->p.teleportTo(x,4,.5)));}
  if(++ticks>1200)throw new IllegalStateException("Scenario timeout: "+NAMES[scenario]);
 }catch(Exception ex){fail(ex);}}
 /** Changes only the QA camera; normal play and production runs never enter this method. */
 @SubscribeEvent public static void close(ViewportEvent.ComputeCameraAngles e){if(!ENABLED||done||!ready||(scenario==8||scenario==12||scenario==13))return;try{Camera camera=e.getCamera();var player=Minecraft.getInstance().player;var look=camera.getLookVector();double distance=scenario==3?1:4;Vec3 desired=scenario==4?player.getPosition((float)e.getPartialTick()).add(.3,.8,.5):player.getEyePosition((float)e.getPartialTick()).subtract(look.x()*distance,look.y()*distance,look.z()*distance);if(cameraSetter==null){cameraSetter=Camera.class.getDeclaredMethod("setPosition",Vec3.class);cameraSetter.setAccessible(true);}cameraSetter.invoke(camera,desired);}catch(Exception ex){fail(ex);}}
 @SubscribeEvent public static void capture(RenderLevelStageEvent e){if(!ENABLED||done||!ready||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||scenario>=10)return;captureShot(e.getCamera(),e.getPartialTick());}
 @SubscribeEvent public static void captureHand(RenderGuiEvent.Post event){if(ENABLED&&!done&&ready&&scenario>=10)captureShot(Minecraft.getInstance().gameRenderer.getMainCamera(),Minecraft.getInstance().getFrameTime());}
 private static void captureShot(Camera qaCamera,float partial){var mc=Minecraft.getInstance();try{
  if(mc.player==null||mc.getOverlay()!=null||ticks<80||++frames<120)return;if(scenario==7&&(reload==null||!reload.isDone()))return;
  if(Client.failed||FireRender.failed||VolumeGpu.failed||FireRender.lastGl!=0||VolumeGpu.lastGl!=0)throw new IllegalStateException("Renderer failed: "+Client.lastError);
  if(scenario>0&&scenario<9&&VolumeGpu.draws!=1)throw new IllegalStateException("No real volume pass rendered");
  if(scenario==7&&VolumeGpu.generation<=shaderBefore)throw new IllegalStateException("Volume shader did not recompile on reload");
  if((scenario==9||scenario==13)&&(Client.core.count()!=0||FireRender.renderedVertices!=0||VolumeGpu.draws!=0))throw new IllegalStateException("Off failed to drain effect");
  if(scenario>=10&&scenario<=12&&WeaponAttachment.rendered==0)throw new IllegalStateException("No real weapon pose draw: "+Client.lastError);
  Path screenshot=OUTPUT.resolve(NAMES[scenario]+".png");var target=mc.getMainRenderTarget();int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);com.mojang.blaze3d.platform.GlStateManager._bindTexture(target.getColorTextureId());try(NativeImage image=new NativeImage(target.width,target.height,false)){image.downloadTexture(0,false);image.flipY();image.writeToFile(screenshot);}finally{com.mojang.blaze3d.platform.GlStateManager._bindTexture(bound);}
  JsonObject record=Client.status();record.addProperty("case",NAMES[scenario]);record.addProperty("world",worldName);record.addProperty("cameraDistance",qaCamera.getPosition().distanceTo(mc.player.getEyePosition(partial)));record.addProperty("screenshot",screenshot.toString());record.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));records.add(record);
  if(++scenario>=NAMES.length){done=true;Files.writeString(OUTPUT.resolve("game-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(records));mc.stop();return;}frames=0;ticks=0;
  if(scenario==1){JsonObject r=defaults.deepCopy();r.addProperty("coreColor","#fff5c2");r.addProperty("midColor","#ffbc38");r.addProperty("edgeColor","#d93412");Client.recipe=Recipe.parse(r.toString());Client.core.resetSeed(Client.recipe.i("seed"));Client.active=true;}
  if(scenario==2)Client.recipe=Recipe.parse(defaults.toString());
  if(scenario==6){mc.getSingleplayerServer().execute(()->{mc.getSingleplayerServer().getPlayerList().getPlayers().forEach(p->p.teleportTo(.5,4,.5));mc.getSingleplayerServer().overworld().setBlockAndUpdate(new BlockPos(0,4,2),Blocks.IRON_BLOCK.defaultBlockState());});}
  if(scenario==7){mc.getSingleplayerServer().execute(()->{for(BlockPos pos:BlockPos.betweenClosed(0,4,2,0,7,2))mc.getSingleplayerServer().overworld().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());});shaderBefore=VolumeGpu.generation;reload=mc.reloadResourcePacks();}
  if(scenario==8)mc.options.setCameraType(CameraType.FIRST_PERSON);
  if(scenario==9){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);Client.off();}
  if(scenario==10){mc.options.hideGui=false;JsonObject wr=defaults.deepCopy();wr.addProperty("attachment",1);Client.recipe=Recipe.parse(wr.toString());Client.active=true;Client.core.clear();WeaponAttachment.rendered=0;mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().forEach(p->p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD))));}
  if(scenario==11)WeaponAttachment.rendered=0;
  if(scenario==12){WeaponAttachment.rendered=0;mc.options.setCameraType(CameraType.FIRST_PERSON);}
  if(scenario==13){WeaponAttachment.rendered=0;Client.off();}
 }catch(Exception ex){fail(ex);}}
 private static void fail(Exception ex){done=true;try{Files.createDirectories(OUTPUT);Files.writeString(OUTPUT.resolve("failure.txt"),ex.toString());}catch(Exception ignored){}com.mojang.logging.LogUtils.getLogger().error("Fire coil independent QA failed",ex);Minecraft.getInstance().stop();}
}
