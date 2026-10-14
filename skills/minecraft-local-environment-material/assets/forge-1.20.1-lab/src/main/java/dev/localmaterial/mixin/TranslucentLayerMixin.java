// SPDX-License-Identifier: MIT
package dev.localmaterial.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.localmaterial.TranslucentMaterial;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla BLOCK chunk format only. Never replaces the layer, geometry or sort order. */
@Mixin(LevelRenderer.class)
public abstract class TranslucentLayerMixin {
    @Redirect(method="renderChunkLayer", at=@At(value="INVOKE",
        target="Lcom/mojang/blaze3d/systems/RenderSystem;getShader()Lnet/minecraft/client/renderer/ShaderInstance;", remap=false))
    private ShaderInstance localmaterial$shader(RenderType layer, PoseStack pose, double x, double y, double z, Matrix4f projection) {
        return TranslucentMaterial.select(RenderSystem.getShader(), layer, pose, projection, x, y, z);
    }

    @Redirect(method="renderChunkLayer", at=@At(value="INVOKE",
        target="Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void localmaterial$draw(VertexBuffer buffer) {
        TranslucentMaterial.recordDraw(buffer);
        buffer.draw();
    }

    @Inject(method="renderChunkLayer", at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/ShaderInstance;clear()V", shift=At.Shift.AFTER))
    private void localmaterial$finish(RenderType layer, PoseStack pose, double x, double y, double z, Matrix4f projection, CallbackInfo ci) {
        TranslucentMaterial.finishProbe();
    }
}
