package dev.firecoil;

import com.google.gson.*;
import dev.firecoil.FireCore.V;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Vector3f;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local-player effect only. Simulation and command mutations run once on the client tick. */
@Mod.EventBusSubscriber(modid=FireCoil.ID,value=Dist.CLIENT)
public final class Client {
 static final FireCore core=new FireCore();
 static Recipe recipe;static Object owner;static boolean active,failed;static long ticks;
 static String lastError="";private static long nextPoll;private static String lastNonce="";
 private static JsonObject lastReply;
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 public static Path directory(){return FMLPaths.CONFIGDIR.get().resolve("firecoil");}
 static String bundledRecipe()throws Exception {try(var in=Client.class.getResourceAsStream("/assets/firecoil/recipe.json")){if(in==null)throw new IllegalStateException("Bundled recipe missing");return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 static boolean load(){try{
  Files.createDirectories(directory());Path path=directory().resolve("recipe.json");
  if(!Files.exists(path))Files.writeString(path,bundledRecipe(),StandardCharsets.UTF_8);
  if(Files.size(path)>16384)throw new IllegalArgumentException("Recipe exceeds 16 KB");
  Recipe next=Recipe.parse(Files.readString(path,StandardCharsets.UTF_8));
  recipe=next;core.clear();core.resetSeed(next.i("seed"));failed=false;lastError="";return true;
 }catch(Exception e){lastError=e.getMessage();com.mojang.logging.LogUtils.getLogger().warn("Fire coil recipe rejected; last valid retained: {}",lastError);return false;}}
 static boolean start(){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){lastError="Player and world required";return false;}if(!load())return false;owner=mc.level;core.world(owner);active=true;return true;}
 static void off(){active=false;}
 static JsonObject status(){var mc=Minecraft.getInstance();JsonObject s=new JsonObject();s.addProperty("runtime","firecoil/0.2.0");s.addProperty("active",active);s.addProperty("weaponDraws",WeaponAttachment.rendered);s.addProperty("weaponQuads",WeaponAttachment.quads);s.addProperty("weaponUnsupported",WeaponAttachment.unsupported);s.addProperty("weaponView",WeaponAttachment.lastView);s.addProperty("weaponItem",WeaponAttachment.lastItem);s.addProperty("worldLoaded",mc.level!=null);s.addProperty("paused",mc.isPaused());s.addProperty("particles",core.count());s.addProperty("capacity",FireCore.CAPACITY);s.addProperty("spawnRejected",core.spawnRejected);s.addProperty("teleports",core.teleports);s.addProperty("ticks",ticks);s.addProperty("renderedVertices",FireRender.renderedVertices);s.addProperty("renderCpuMs",FireRender.lastCpuMs);s.addProperty("volumeCpuMs",VolumeGpu.cpuMs);s.addProperty("volumeDraws",VolumeGpu.draws);s.addProperty("volumeCoverage",VolumeGpu.coverage);s.addProperty("visibleVolumeWisps",VolumeGpu.renderedWisps);s.addProperty("volumeWispLimit",24);s.addProperty("volumeGeneration",VolumeGpu.generation);s.addProperty("volumeSteps",recipe==null?0:recipe.i("segments"));s.addProperty("depthCopyTexture",VolumeGpu.depthTexture);s.addProperty("glError",VolumeGpu.lastGl!=0?VolumeGpu.lastGl:FireRender.lastGl);s.addProperty("failed",failed||FireRender.failed||VolumeGpu.failed);s.addProperty("shaderGeneration",FireRender.generation);s.addProperty("lastError",lastError);if(recipe!=null){s.addProperty("recipeHash",recipe.hash());s.add("recipe",recipe.json());}return s;}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(mc.level!=owner){owner=mc.level;core.world(owner);active=false;if(owner!=null&&mc.player!=null&&load())active=true;}
  if(mc.player!=null&&mc.level!=null&&recipe!=null&&!mc.isPaused()){
   core.tick(recipe,new V(mc.player.getX(),mc.player.getY(),mc.player.getZ()),-mc.player.yBodyRot*Math.PI/180,active,mc.options.getCameraType().isFirstPerson()?recipe.d("firstPerson"):1);ticks++;
  }
  bridge();
 }
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){active=false;owner=null;core.world(null);core.clear();VolumeGpu.release();}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||recipe==null||failed)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
  try{
   double partial=mc.isPaused()?0:e.getPartialTick();var player=mc.player;
   V anchor=new V(Mth.lerp(partial,player.xo,player.getX()),Mth.lerp(partial,player.yo,player.getY()),Mth.lerp(partial,player.zo,player.getZ()));
   var c=e.getCamera().getPosition();V camera=new V(c.x,c.y,c.z);
   Vector3f right=new Vector3f(1,0,0).rotate(e.getCamera().rotation()),up=new Vector3f(0,1,0).rotate(e.getCamera().rotation());
   double yaw=-Mth.rotLerp((float)partial,player.yBodyRotO,player.yBodyRot)*Math.PI/180;
   double strength=mc.options.getCameraType().isFirstPerson()?recipe.d("firstPerson"):1;
   VolumeGpu.render(e,recipe,core,anchor,yaw,partial,strength);
   FireRender.draw(Geometry.embers(core,camera,new V(right.x,right.y,right.z),new V(up.x,up.y,up.z),partial),recipe,e.getPoseStack(),camera,core.time()+partial*.05);
  }catch(Exception ex){failed=true;lastError=ex.toString();com.mojang.logging.LogUtils.getLogger().error("Fire coil render stopped",ex);}
 }
 @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){var root=Commands.literal(FireCoil.ID);for(String action:List.of("start","off","reload","status"))root.then(Commands.literal(action).executes(c->{try{action(action);c.getSource().sendSuccess(()->Component.literal("异火缠绕: "+status()),false);return 1;}catch(Exception ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}}));e.getDispatcher().register(root);}
 private static void action(String action){switch(action){case "status"->{}case "start"->{if(!start())throw new IllegalStateException(lastError);}case "off"->off();case "reload"->{if(!load())throw new IllegalArgumentException(lastError);}default->throw new IllegalArgumentException("Unknown firecoil action");}}
 private static String string(JsonObject j,String key){JsonElement v=j.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be string");return v.getAsString();}
 /** Fixed bridge; at most one request per tick, no arbitrary paths or game control. */
 private static void bridge(){long now=System.nanoTime();if(now<nextPoll)return;nextPoll=now+100_000_000L;try{
  Files.createDirectories(directory());Path path=directory().resolve("command.json");if(!Files.isRegularFile(path))return;
  String contents=Files.readString(path,StandardCharsets.UTF_8),nonce="";JsonObject result=new JsonObject();
  try{
   if(contents.length()>2048)throw new IllegalArgumentException("Command exceeds 2 KB");JsonElement value=JsonParser.parseString(contents);if(!value.isJsonObject())throw new IllegalArgumentException("Command must be object");JsonObject request=value.getAsJsonObject();
   if(!request.keySet().equals(Set.of("action","nonce")))throw new IllegalArgumentException("Command requires exactly action and nonce");
   nonce=string(request,"nonce");if(!nonce.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid nonce");
   if(nonce.equals(lastNonce)&&lastReply!=null)result=lastReply.deepCopy();else{action(string(request,"action"));result.addProperty("ok",true);}
  }catch(Exception ex){result.addProperty("ok",false);result.addProperty("error",ex.getMessage());}
  result.addProperty("nonce",nonce);result.add("status",status());write("result.json",result);lastNonce=nonce;lastReply=result.deepCopy();
  if(Files.readString(path,StandardCharsets.UTF_8).equals(contents))Files.deleteIfExists(path);
 }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().debug("Fire coil bridge could not update",ex);}}
 static void write(String name,JsonObject value)throws Exception {Path tmp=directory().resolve(name+".tmp"),target=directory().resolve(name);Files.writeString(tmp,GSON.toJson(value),StandardCharsets.UTF_8);try{Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}
}
