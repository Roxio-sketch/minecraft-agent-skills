package dev.rendercraft.particlelab;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.Gson;
import org.lwjgl.opengl.GL11;
@Mod(Demo.ID)
public final class Demo {
 public static final String ID="particlelab",MODE="particles";
 public Demo(){ net.minecraftforge.fml.DistExecutor.safeRunWhenOn(Dist.CLIENT,()->Client::init);}
 public static final class Client {
  static boolean active,failed,blocked;static Vec3 anchor;static Recipe recipe=new Recipe();static long ticks;static int draws,generation,gl;
  static void init(){var b=FMLJavaModLoadingContext.get().getModEventBus();b.addListener(Client::shaders);b.addListener(Client::reload);MinecraftForge.EVENT_BUS.register(Client.class);blocked=ModList.get().isLoaded("oculus")||ModList.get().isLoaded("embeddium")||ModList.get().isLoaded("rubidium");}
  static void load(){try(var r=Minecraft.getInstance().getResourceManager().openAsReader(new ResourceLocation(ID,"rendercraft/recipe.json"))){var object=com.google.gson.JsonParser.parseReader(r).getAsJsonObject();var allowed=java.util.Set.of("color","accent","rate","lifetime","capacity","size","speed","drag","gravity","orbit","seed","motion");for(String key:object.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unsupported recipe field: "+key);Recipe next=new Gson().fromJson(object,Recipe.class);next.validate();recipe=next;}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("Rendercraft recipe invalid; retained last valid recipe",ex);}}
  static void shaders(RegisterShadersEvent e){load(); Particles.register(e); generation++;failed=false;}
  static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)r->RenderSystem.recordRenderCall(()->{Particles.clear(); load();active=false;}));}
  @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){e.getDispatcher().register(Commands.literal(ID).then(Commands.literal("start").executes(c->{var m=Minecraft.getInstance();if(m.player==null||m.level==null)return 0;if(blocked&&!MODE.equals("adapter")){c.getSource().sendFailure(Component.literal("Demo requires verified vanilla renderer; current acceleration/shader adapter unvalidated."));return 0;}load();anchor=m.player.position().add(m.player.getLookAngle().scale(3)).add(0,1,0);ticks=0;failed=false;active=true;Particles.reset(recipe); return 1;})).then(Commands.literal("off").executes(c->{active=false;Particles.clear(); return 1;})).then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal(ID+" active="+active+" draws="+draws+" generation="+generation+" gl="+gl+" failed="+failed+" blocked="+blocked),false);return 1;})));}
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var m=Minecraft.getInstance();if(m.level==null){active=false;return;}if(active&&!m.isPaused()){ticks++;Particles.tick(recipe); if(ticks>1200){active=false;Particles.clear();}}}
  @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){active=false;Particles.clear();}
  @SubscribeEvent public static void render(RenderLevelStageEvent e){if(!active||failed||anchor==null||blocked)return;try{Particles.render(e);}catch(Exception ex){failed=true;com.mojang.logging.LogUtils.getLogger().error("Rendercraft pass stopped",ex);}}
  static float time(float partial){return (ticks+partial)/20f;}
 }
}
