package dev.vfxworkbench.client;

import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.effects.blackhole.BlackHoleClient;
import dev.vfxworkbench.effects.lightning.LightningClient;
import dev.vfxworkbench.effects.water.WaterEffect;
import net.minecraft.client.Minecraft;

/** Shared actions for the workbench buttons and rebindable gameplay keys. */
final class WorkbenchEffects {
    private WorkbenchEffects() {}

    static String selectedSkill() {
        String selected = WorkbenchPreferences.selectedSkill();
        if (selected != null && ParamRegistry.has(selected)) return selected;
        var skills = ParamRegistry.skills();
        return skills.isEmpty() ? null : skills.get(0).id;
    }

    static void trigger(String skill, boolean rebuild) {
        if ("lightning".equals(skill)) LightningClient.preview();
        else if ("black-hole".equals(skill)) BlackHoleClient.preview();
        else if ("water-ripples".equals(skill)) {
            if (rebuild) WaterEffect.reloadField();
            else WaterEffect.create(Minecraft.getInstance().player.position().add(0, .08, 0));
            WaterEffect.impulseCenter();
        }
    }

    static void stop(String skill) {
        if ("lightning".equals(skill)) LightningClient.stop();
        else if ("black-hole".equals(skill)) BlackHoleClient.stop();
        else if ("water-ripples".equals(skill)) WaterEffect.clear();
    }
}
