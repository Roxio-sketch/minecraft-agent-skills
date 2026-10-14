// SPDX-License-Identifier: MIT
package dev.localmaterial.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.localmaterial.SketchPreset;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After vanilla's optional hand draw, before its GUI setup and HUD. */
@Mixin(GameRenderer.class)
public abstract class AfterHandSketchMixin {
    @Inject(method="renderLevel", at=@At("TAIL"))
    private void localmaterial$afterHand(float partialTick,long finishTime,PoseStack pose,CallbackInfo ci){
        SketchPreset.renderAfterHand();
    }
}
