package dev.energyvfx;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.*;
import java.nio.file.*;
/** Opt-in integration QA. Creates a new isolated flat world; never opens user saves. */
@Mod.EventBusSubscriber(modid=EnergyVfx.ID,value=Dist.CLIENT)
public final class AutoQa {
 static final boolean ENABLED=Boolean.getBoolean("energyvfx.qa");
 static final Path OUTPUT=Path.of(System.getProperty("energyvfx.qa.output","validation"));
 static boolean started,ready,done;static int age,frame,scenario;static final JsonArray results=new JsonArray();
 static final String[] names={"baseline","energy-blue","energy-orange","occlusion","reload","camera-inside"};
 static JsonObject defaultRaw;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(!ENABLED||done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();try{
  if(!started){if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||++age<30)return;started=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());Files.createDirectories(OUTPUT);String world="ENERGY_VFX_QA_"+System.currentTimeMillis();GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
  if(!ready){ready=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.fovEffectScale().set(0.);mc.options.renderDistance().set(3);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);mc.setScreen(null);mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();level.setDayTime(6000);for(BlockPos p:BlockPos.betweenClosed(-5,4,8,5,14,8))level.setBlockAndUpdate(p,Blocks.GOLD_BLOCK.defaultBlockState());mc.getSingleplayerServer().getPlayerList().getPlayers().forEach(p->{p.getAbilities().flying=true;p.getAbilities().mayfly=true;p.onUpdateAbilities();p.teleportTo(.5,8,.5);p.setYRot(0);p.setXRot(0);});});defaultRaw=Recipe.parse(Recipe.resource("recipe.json")).getAsJsonObject();EnergyVfx.Client.load();EnergyVfx.Client.active=false;frame=0;age=0;}
  mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
  if(++age>1200)throw new IllegalStateException("QA scenario timeout");
 }catch(Exception ex){fail(ex);}}
 @SubscribeEvent public static void capture(RenderLevelStageEvent e){if(!ENABLED||done||!ready||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;var mc=Minecraft.getInstance();try{
  if(mc.player==null||mc.getOverlay()!=null||++frame<80)return;
  Path screenshot=OUTPUT.resolve(names[scenario]+".png");var target=mc.getMainRenderTarget();int tex=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
  com.mojang.blaze3d.platform.GlStateManager._bindTexture(target.getColorTextureId());
  try(NativeImage image=new NativeImage(target.width,target.height,false)){image.downloadTexture(0,false);image.flipY();image.writeToFile(screenshot);}finally{com.mojang.blaze3d.platform.GlStateManager._bindTexture(tex);}
  JsonObject record=new JsonObject();record.addProperty("case",names[scenario]);record.addProperty("draws",EnergyVfx.Client.draws);record.addProperty("glError",EnergyVfx.Client.lastGl);record.addProperty("failed",EnergyVfx.Client.failed);record.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));record.addProperty("screenshot",screenshot.toString());results.add(record);
  if(EnergyVfx.Client.failed||EnergyVfx.Client.lastGl!=0)throw new IllegalStateException("Energy renderer failed");
  if(++scenario>=names.length){done=true;Files.writeString(OUTPUT.resolve("game-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));mc.stop();return;}
  frame=0;age=0;
  if(scenario==1){EnergyVfx.Client.startAt(mc.player.getEyePosition().add(0,0,5));}
  if(scenario==2){JsonObject r=defaultRaw.deepCopy();r.addProperty("color","#ff9c32");r.addProperty("secondary","#ff4524");EnergyVfx.Client.recipe=Recipe.decode(r);}
  if(scenario==3){mc.getSingleplayerServer().execute(()->{for(BlockPos p:BlockPos.betweenClosed(0,8,3,0,11,3))mc.getSingleplayerServer().overworld().setBlockAndUpdate(p,Blocks.IRON_BLOCK.defaultBlockState());});}
  if(scenario==4){mc.reloadResourcePacks();}
  if(scenario==5){EnergyVfx.Client.anchor=mc.player.getEyePosition().add(0,0,.1);}
 }catch(Exception ex){fail(ex);}}
 static void fail(Exception ex){done=true;try{Files.createDirectories(OUTPUT);Files.writeString(OUTPUT.resolve("failure.txt"),ex.toString());}catch(Exception ignored){}com.mojang.logging.LogUtils.getLogger().error("Energy integration QA failed",ex);Minecraft.getInstance().stop();}
}
