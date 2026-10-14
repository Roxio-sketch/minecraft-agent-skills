package dev.alchemyvfx;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fixed file bridge and read-only furnace rendering. Client commands never control alchemy jobs. */
@Mod.EventBusSubscriber(modid=AlchemyVfx.ID,value=Dist.CLIENT)
public final class Client {
    static Recipe recipe;
    static Object owner;
    static boolean active=true,failed;
    static long ticks;
    static String lastError="";
    static final Set<Object> claimed=Collections.newSetFromMap(new IdentityHashMap<>());
    private static List<HostAdapter.Furnace> furnaces=List.of();
    private static long nextPoll;
    private static String lastNonce="";
    private static JsonObject lastReply;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static Path directory() { return FMLPaths.CONFIGDIR.get().resolve(AlchemyVfx.ID); }
    static String bundledRecipe() throws Exception {
        try(var input=Client.class.getResourceAsStream("/assets/alchemyvfx/recipe.json")) {
            if(input==null) throw new IllegalStateException("Bundled recipe missing");
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    static boolean load() {
        try {
            Files.createDirectories(directory());Path path=directory().resolve("recipe.json");
            if (!Files.exists(path)) Files.writeString(path,bundledRecipe(),StandardCharsets.UTF_8);
            if (Files.size(path)>16384) throw new IllegalArgumentException("Recipe exceeds 16 KB");
            Recipe next=Recipe.parse(Files.readString(path,StandardCharsets.UTF_8));
            recipe=next;failed=false;lastError="";return true;
        } catch(Exception failure) { lastError=failure.getMessage();com.mojang.logging.LogUtils.getLogger().warn("Alchemy recipe rejected; last valid retained: {}",lastError);return false; }
    }
    static boolean start() { if(!load())return false;active=true;return true; }
    static void off() { active=false;claimed.clear(); }
    static void furnacesForQa() { furnaces=HostAdapter.loaded(0); }
    static JsonObject status() {
        var mc=Minecraft.getInstance();JsonObject value=new JsonObject();
        value.addProperty("runtime","alchemyvfx/0.2.0");value.addProperty("active",active);value.addProperty("worldLoaded",mc.level!=null);
        value.addProperty("paused",mc.isPaused());value.addProperty("hostAvailable",HostAdapter.available());
        value.addProperty("hostHookInstalled",HostAdapter.hookInstalled());
        value.addProperty("integration",HostAdapter.hookInstalled()?"hook_v1":HostAdapter.available()?"hook_missing_legacy_retained":"host_absent");
        value.addProperty("formedActiveFurnaces",furnaces.size());value.addProperty("claimedFurnaces",claimed.size());
        value.addProperty("ticks",ticks);value.addProperty("failed",failed||VolumeGpu.failed);
        value.addProperty("lastError",lastError);value.addProperty("hostError",HostAdapter.lastError);
        value.addProperty("volumeDraws",VolumeGpu.draws);value.addProperty("volumeGeneration",VolumeGpu.generation);
        value.addProperty("volumeSteps",recipe==null?0:recipe.i("segments"));value.addProperty("volumeCpuMs",VolumeGpu.cpuMs);
        value.addProperty("volumeCoverage",VolumeGpu.coverage);value.addProperty("glError",VolumeGpu.lastGl);
        value.addProperty("depthCopyTexture",VolumeGpu.depthTexture);value.addProperty("resolutionScale",1.0);value.addProperty("automaticLod",false);
        if(recipe!=null){value.addProperty("recipeHash",recipe.hash());value.add("recipe",recipe.json());}
        JsonArray states=new JsonArray();for(var f:furnaces){JsonObject state=new JsonObject();state.addProperty("core",f.core().toShortString());state.addProperty("brewing",f.brewing());state.addProperty("startTick",f.startTick());state.addProperty("finishTick",f.finishTick());state.addProperty("hasResult",f.hasResult());state.addProperty("completionAge",f.completionAge());state.addProperty("activeFireId",f.activeFireId());state.addProperty("tier",f.tier());states.add(state);}value.add("furnaces",states);
        return value;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level!=owner){owner=mc.level;claimed.clear();furnaces=List.of();if(recipe==null)load();}
        if(mc.level!=null&&mc.player!=null&&!mc.isPaused()){ticks++;if(ticks%10==0)furnaces=HostAdapter.loaded(0);}
        bridge();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        claimed.clear();VolumeGpu.draws=0;
        if(!active||recipe==null||failed||!HostAdapter.hookInstalled())return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        try {
            var ordered=new ArrayList<>(furnaces);var eye=event.getCamera().getPosition();ordered.sort(Comparator.comparingDouble((HostAdapter.Furnace f)->f.anchor().distanceToSqr(eye)).reversed());
            for(var remembered:ordered){var furnace=HostAdapter.read(remembered.entity(),event.getPartialTick());if(furnace==null||!furnace.visible())continue;
                if(VolumeGpu.render(event,recipe,furnace))claimed.add(furnace.entity());}
        }catch(Exception failure){failed=true;lastError=failure.toString();com.mojang.logging.LogUtils.getLogger().error("Alchemy volume render stopped",failure);}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { owner=null;claimed.clear();furnaces=List.of();VolumeGpu.release(); }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var root=Commands.literal(AlchemyVfx.ID);for(String action:List.of("start","off","reload","status"))root.then(Commands.literal(action).executes(context->{try{action(action);context.getSource().sendSuccess(()->Component.literal("炼丹体积: "+status()),false);return 1;}catch(Exception failure){context.getSource().sendFailure(Component.literal(failure.getMessage()));return 0;}}));event.getDispatcher().register(root);
    }
    private static void action(String action) {
        switch(action){case "status"->{}case "start"->{if(!start())throw new IllegalStateException(lastError);}case "off"->off();case "reload"->{if(!load())throw new IllegalArgumentException(lastError);}default->throw new IllegalArgumentException("Unknown alchemyvfx action");}
    }
    /** Exactly action/nonce; no paths, camera commands, world editing or host-state mutation. */
    private static void bridge() {
        long now=System.nanoTime();if(now<nextPoll)return;nextPoll=now+100_000_000L;
        try {
            Files.createDirectories(directory());Path path=directory().resolve("command.json");if(!Files.isRegularFile(path))return;
            if(Files.size(path)>2048)throw new IllegalArgumentException("Command exceeds 2 KB");
            String contents=Files.readString(path,StandardCharsets.UTF_8),nonce="";JsonObject result=new JsonObject();
            try {
                JsonElement value=JsonParser.parseString(contents);if(!value.isJsonObject())throw new IllegalArgumentException("Command must be object");
                JsonObject request=value.getAsJsonObject();if(!request.keySet().equals(Set.of("action","nonce")))throw new IllegalArgumentException("Command requires exactly action and nonce");
                nonce=Recipe.string(request,"nonce");if(!nonce.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid nonce");
                if(nonce.equals(lastNonce)&&lastReply!=null)result=lastReply.deepCopy();else{action(Recipe.string(request,"action"));result.addProperty("ok",true);}
            } catch(Exception invalid){result.addProperty("ok",false);result.addProperty("error",invalid.getMessage());}
            result.addProperty("nonce",nonce);result.add("status",status());write("result.json",result);lastNonce=nonce;lastReply=result.deepCopy();
            if(Files.readString(path,StandardCharsets.UTF_8).equals(contents))Files.deleteIfExists(path);
        } catch(Exception failure){com.mojang.logging.LogUtils.getLogger().debug("Alchemy bridge could not update",failure);}
    }
    static void write(String name,JsonObject value)throws Exception {
        Path temporary=directory().resolve(name+".tmp"),target=directory().resolve(name);
        Files.writeString(temporary,GSON.toJson(value),StandardCharsets.UTF_8);
        try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException fallback){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    private Client() {}
}
