package dev.cosmiclab.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.cosmiclab.LabTranslucency;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class NativeTranslucentMixin {
    @Inject(method="renderChunkLayer", at=@At("HEAD"))
    private void cosmiclab$before(RenderType layer, PoseStack pose, double x, double y, double z, Matrix4f projection, CallbackInfo callback) {
        LabTranslucency.begin(layer);
    }
    @Redirect(method="renderChunkLayer", require=0, at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/systems/RenderSystem;getShader()Lnet/minecraft/client/renderer/ShaderInstance;", remap=false))
    private ShaderInstance cosmiclab$surface(RenderType layer, PoseStack pose, double x, double y, double z, Matrix4f projection) {
        return LabTranslucency.select(RenderSystem.getShader(), layer, pose, projection, x, y, z);
    }
    @Redirect(method="renderChunkLayer", require=0, at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void cosmiclab$draw(VertexBuffer buffer, RenderType layer, PoseStack pose, double x, double y, double z, Matrix4f projection) {
        LabTranslucency.recordDraw(layer); buffer.draw();
    }
    @Inject(method="renderChunkLayer", at=@At("RETURN"))
    private void cosmiclab$after(RenderType layer, PoseStack pose, double x, double y, double z, Matrix4f projection, CallbackInfo callback) {
        if (layer == RenderType.translucent()) LabTranslucency.end();
    }
}
