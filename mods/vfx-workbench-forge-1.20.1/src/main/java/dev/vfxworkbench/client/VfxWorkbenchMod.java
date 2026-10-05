package dev.vfxworkbench.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.effects.water.WaterSkillSchema;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod(VfxWorkbenchMod.MODID)
public final class VfxWorkbenchMod {
    public static final String MODID = "vfxworkbench";

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientMod {
        private ClientMod() {}

        public static final KeyMapping OPEN_WORKBENCH = new KeyMapping(
            "key.vfxworkbench.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "key.categories.vfxworkbench"
        );
        public static final KeyMapping TRIGGER_EFFECT = new KeyMapping(
            "key.vfxworkbench.trigger", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.vfxworkbench"
        );
        public static final KeyMapping STOP_EFFECT = new KeyMapping(
            "key.vfxworkbench.stop", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.vfxworkbench"
        );

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                WaterSkillSchema.register(); dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.register(); dev.vfxworkbench.effects.lightning.LightningSkillSchema.register();
                System.out.println("[VFXWORKBENCH] registered " + ParamRegistry.skills().size() + " skill schema(s)");
            });
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent e) {
            e.register(OPEN_WORKBENCH);
            e.register(TRIGGER_EFFECT);
            e.register(STOP_EFFECT);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            boolean open = consume(ClientMod.OPEN_WORKBENCH);
            boolean trigger = consume(ClientMod.TRIGGER_EFFECT);
            boolean stop = consume(ClientMod.STOP_EFFECT);
            Minecraft mc = Minecraft.getInstance();
            // Drain clicks in menus too so closing chat cannot replay an action.
            if (mc.screen != null || mc.player == null || mc.level == null) return;
            String selected = WorkbenchEffects.selectedSkill();
            if (stop && selected != null) {
                WorkbenchEffects.stop(selected);
                mc.player.displayClientMessage(Component.translatable("message.vfxworkbench.stopped", ParamRegistry.schema(selected).name), true);
            } else if (open) {
                mc.setScreen(new VfxWorkbenchScreen());
            } else if (trigger && selected != null) {
                WorkbenchEffects.trigger(selected, false);
                mc.player.displayClientMessage(Component.translatable("message.vfxworkbench.triggered", ParamRegistry.schema(selected).name), true);
            }
        }

        private static boolean consume(KeyMapping key) {
            boolean clicked = false;
            while (key.consumeClick()) clicked = true;
            return clicked;
        }
    }
}
