package dev.vfxworkbench.effects.water;

import dev.vfxworkbench.client.VfxWorkbenchMod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VfxWorkbenchMod.MODID, value = Dist.CLIENT)
final class WaterCommand {
    private WaterCommand() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("waterripple")
            .then(Commands.literal("demo").executes(c -> {
                WaterEffect.create(c.getSource().getPosition().add(0, 0.08, 0).subtract(0, 0, 0));
                WaterEffect.impulseCenter();
                c.getSource().sendSuccess(() -> Component.literal("水面已创建，按 F6 打开调参界面。"), false);
                return 1;
            }))
            .then(Commands.literal("drop").executes(c -> {
                if (!WaterEffect.isActive()) { c.getSource().sendFailure(Component.literal("请先使用 /waterripple demo 创建水面。")); return 0; }
                WaterEffect.impulseCenter();
                return 1;
            }))
            .then(Commands.literal("reload").executes(c -> {
                if (!WaterEffect.reloadField()) { c.getSource().sendFailure(Component.literal("没有可重建的水面。")); return 0; }
                c.getSource().sendSuccess(() -> Component.literal("网格参数已应用，水面已重建。"), false);
                return 1;
            }))
            .then(Commands.literal("rain").executes(c -> { WaterEffect.toggleRain(); return 1; }))
            .then(Commands.literal("off").executes(c -> { WaterEffect.clear(); return 1; }))
        );
    }
}
