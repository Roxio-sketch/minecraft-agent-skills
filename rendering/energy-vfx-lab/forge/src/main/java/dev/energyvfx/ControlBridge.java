package dev.energyvfx;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

/** Fixed local VFX actions only: no GUI, camera, inventory, arbitrary file or server commands. */
final class ControlBridge {
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static long nextPoll;
 private static String lastNonce="";
 private static Path root(){return FMLPaths.CONFIGDIR.get().resolve("energyvfx");}
 static void tick(){
  long now=System.nanoTime();if(now<nextPoll)return;nextPoll=now+500_000_000L;
  try{
   Files.createDirectories(root());Path command=root().resolve("command.json");
   if(Files.isRegularFile(command)){
    String nonce="";JsonObject result=new JsonObject();
    try{
     if(Files.size(command)>2048)throw new IllegalArgumentException("Command exceeds 2 KB");
     JsonObject request=Recipe.parse(Files.readString(command,StandardCharsets.UTF_8)).getAsJsonObject();
     nonce=Recipe.string(request,"nonce");if(!nonce.matches("[a-zA-Z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid nonce");
     String action=Recipe.string(request,"action");Recipe.exact(request,action.equals("preset")?Set.of("action","nonce","preset"):Set.of("action","nonce"));
     if(!nonce.equals(lastNonce)){
      switch(action){
       case "status" -> {}
       case "off" -> {EnergyVfx.Client.active=false;AuraEmitter.reset();}
       case "start" -> {if(!EnergyVfx.Client.start())throw new IllegalStateException("Player/world or valid recipe required");}
       case "reload" -> {if(!EnergyVfx.Client.load())throw new IllegalArgumentException("Recipe rejected; last valid retained");resetEmission();}
       case "preset" -> {
        JsonElement p=request.get("preset");if(!p.isJsonPrimitive()||!p.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected integer preset");
        double index=p.getAsDouble();if(!Double.isFinite(index)||index!=Math.floor(index)||index<1||index>20)throw new IllegalArgumentException("Preset must be 1..20");
        Recipe next=Recipe.decode(Recipe.parse(Recipe.resource("presets/"+(int)index+".json")));
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)throw new IllegalStateException("Player/world required");
        EnergyVfx.Client.recipe=next;EnergyVfx.Client.active=true;EnergyVfx.Client.owner=mc.level;EnergyVfx.Client.failed=false;resetEmission();
        if(next.values[39]<.5)EnergyVfx.Client.anchor=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(5));
       }
       default -> throw new IllegalArgumentException("Unknown VFX action");
      }
      lastNonce=nonce;
     }
     result.addProperty("ok",true);
    }catch(Exception ex){result.addProperty("ok",false);result.addProperty("error",ex.getMessage());}
    result.addProperty("nonce",nonce);result.add("status",status());write("result.json",result);Files.deleteIfExists(command);
   }
   write("status.json",status());
  }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().debug("Local VFX bridge could not update",ex);}
 }
 private static void resetEmission(){EnergyVfx.Client.ticks=0;AuraEmitter.reset();var mc=Minecraft.getInstance();if(EnergyVfx.Client.recipe.values[39]>.5&&mc.player!=null){AuraEmitter.bind(mc.player);EnergyVfx.Client.anchor=AuraEmitter.anchor(0);}}
 private static JsonObject status(){
  var mc=Minecraft.getInstance();JsonObject s=new JsonObject();s.addProperty("runtime","energyvfx/0.2.5");s.addProperty("active",EnergyVfx.Client.active);s.addProperty("draws",EnergyVfx.Client.draws);s.addProperty("glError",EnergyVfx.Client.lastGl);s.addProperty("failed",EnergyVfx.Client.failed);s.addProperty("ticks",EnergyVfx.Client.ticks);s.addProperty("packets",AuraEmitter.packetCount);s.addProperty("surfaceTriangles",AuraEmitter.sourceCount);s.addProperty("equippedSlots",AuraEmitter.equippedSources);s.addProperty("renderedPose",AuraEmitter.usingRenderedPose);s.addProperty("bakedItems",AuraEmitter.bakedGearSources);s.addProperty("fallbackItems",AuraEmitter.fallbackGearSources);s.addProperty("camera",mc.options.getCameraType().name());s.addProperty("paused",mc.isPaused());s.addProperty("worldLoaded",mc.level!=null);if(mc.level!=null)s.addProperty("dimension",mc.level.dimension().location().toString());if(EnergyVfx.Client.recipe!=null){s.addProperty("recipe",EnergyVfx.Client.recipe.name);s.addProperty("attachment",EnergyVfx.Client.recipe.values[39]);}s.addProperty("volumeCoverage",OptimizedGpu.lastCoverage);s.addProperty("sceneCopyCoverage",OptimizedGpu.lastCopyCoverage);s.addProperty("framebufferWidth",mc.getMainRenderTarget().width);s.addProperty("framebufferHeight",mc.getMainRenderTarget().height);s.addProperty("uploadedPackets",AuraEmitter.uploadCount);s.addProperty("firstPersonThinned",AuraEmitter.firstPersonThinned);s.addProperty("nearEyeHidden",AuraEmitter.nearEyeHidden);s.addProperty("nearEyeFaded",AuraEmitter.nearEyeFaded);s.addProperty("flowCacheActive",FlowCache.active);s.addProperty("flowCacheSupported",FlowCache.supported());s.addProperty("nearViewThinned",AuraEmitter.nearViewThinned);s.addProperty("nearViewWeight",AuraEmitter.nearViewWeight);if(Double.isFinite(AuraEmitter.cameraDistance))s.addProperty("cameraDistanceMeters",AuraEmitter.cameraDistance);s.addProperty("packetBoundsEnabled",PacketBounds.enabled());return s;
 }
 private static void write(String filename,JsonObject value)throws Exception{Path tmp=root().resolve(filename+".tmp"),target=root().resolve(filename);Files.writeString(tmp,GSON.toJson(value),StandardCharsets.UTF_8);try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}
}
