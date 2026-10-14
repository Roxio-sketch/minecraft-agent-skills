package dev.energyvfx;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.mojang.blaze3d.systems.RenderSystem;
/** Client world-space sample; commands never modify blocks or server state. */
@Mod(EnergyVfx.ID)
public final class EnergyVfx {
 public static final String ID="energyvfx";
 public EnergyVfx(){net.minecraftforge.fml.DistExecutor.safeRunWhenOn(Dist.CLIENT,()->Client::init);}
 public static final class Client {
  static Recipe recipe;static Vec3 anchor;static Object owner;static boolean active,failed;static long ticks;static int draws,lastGl;
  static void init(){MinecraftForge.EVENT_BUS.register(Client.class);FMLJavaModLoadingContext.get().getModEventBus().addListener(Client::reloadListeners);}
  static boolean load(){try{Recipe next=Recipe.load();recipe=next;failed=false;return true;}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("Energy recipe rejected; last valid recipe retained",ex);return false;}}
  static void reloadListeners(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)manager->{Runnable task=()->{Gpu.release();OptimizedGpu.release();load();};if(RenderSystem.isOnRenderThread())task.run();else RenderSystem.recordRenderCall(task::run);});}
  @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){e.getDispatcher().register(Commands.literal(ID)
   .then(Commands.literal("start").executes(c->{if(!start())return 0;c.getSource().sendSuccess(()->Component.literal("能量特效已启动："+recipe.name),false);return 1;}))
   .then(Commands.literal("reload").executes(c->{if(!load()){c.getSource().sendFailure(Component.literal("配方被拒绝，保留原参数。"));return 0;}ticks=0;AuraEmitter.reset();if(recipe.values[39]>.5&&Minecraft.getInstance().player!=null)AuraEmitter.bind(Minecraft.getInstance().player);c.getSource().sendSuccess(()->Component.literal("已应用全部参数："+recipe.name),false);return 1;}))
   .then(Commands.literal("off").executes(c->{active=false;return 1;}))
   .then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal("active="+active+" draws="+draws+" gl="+lastGl+" failed="+failed+" attachment="+(recipe==null?0:recipe.values[39])+" puffs="+AuraEmitter.packetCount+" gearSources="+AuraEmitter.equippedSources),false);return 1;})));}
  static boolean start(){var m=Minecraft.getInstance();if(m.player==null||m.level==null||!load())return false;ticks=0;active=true;failed=false;owner=m.level;AuraEmitter.reset();if(recipe.values[39]>.5){AuraEmitter.bind(m.player);anchor=AuraEmitter.anchor(0);}else anchor=m.player.getEyePosition().add(m.player.getLookAngle().scale(5));return true;}
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var m=Minecraft.getInstance();if(m.level==null){active=false;owner=null;ControlBridge.tick();return;}if(m.level!=owner&&m.player!=null){owner=m.level;active=false;AuraEmitter.reset();if(load()&&recipe.values[39]>.5)start();}if(active&&!m.isPaused()&&!PerformanceQa.freeze){ticks++;if(recipe.values[39]>.5){long cpuStart=System.nanoTime();AuraEmitter.tick();PerfTrace.cpu("emitterTickCpuMs",System.nanoTime()-cpuStart);}}ControlBridge.tick();}
  @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){active=false;owner=null;AuraEmitter.reset();Gpu.release();OptimizedGpu.release();}
  @SubscribeEvent public static void render(RenderLevelStageEvent e){if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!active||failed||recipe==null)return;try{if(QualityQa.enabled)QualityQa.render(e);else if(PerformanceQa.ENABLED&&!PerformanceQa.optimized&&!PerformanceQa.FLOW&&!PerformanceQa.MOTION&&!PerformanceQa.BUILDUP)Gpu.render(e);else OptimizedGpu.render(e);}catch(Exception ex){failed=true;com.mojang.logging.LogUtils.getLogger().error("Energy pass stopped; render state restored",ex);}}
  /** Target skill adapters call this from the client render thread with a world-space anchor. */
  public static boolean startAt(Vec3 position){if(!load())return false;anchor=position;owner=Minecraft.getInstance().level;ticks=0;active=true;return true;}
 }
}
