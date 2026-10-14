package dev.coloredglint.mixin;
import com.mojang.blaze3d.vertex.*;
import dev.coloredglint.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
    private boolean coloredglint$foil(ItemStack stack) {
        return stack.hasFoil() || GlintStyle.resolve(stack).force();
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;getFoilBufferDirect(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer coloredglint$getFoilBufferDirect(MultiBufferSource source, RenderType type, boolean item, boolean foil,
            ItemStack stack, ItemDisplayContext context, boolean left, PoseStack matrices,
            MultiBufferSource enclosingSource, int light, int overlay, BakedModel model) {
        return ItemRenderer.getFoilBufferDirect(GlintLayers.forStack(source,stack,context),type,item, foil);
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;getFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer coloredglint$getFoilBuffer(MultiBufferSource source, RenderType type, boolean item, boolean foil,
            ItemStack stack, ItemDisplayContext context, boolean left, PoseStack matrices,
            MultiBufferSource enclosingSource, int light, int overlay, BakedModel model) {
        return ItemRenderer.getFoilBuffer(GlintLayers.forStack(source,stack,context),type,item, foil);
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;getCompassFoilBufferDirect(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer coloredglint$getCompassFoilBufferDirect(MultiBufferSource source, RenderType type, PoseStack.Pose pose,
            ItemStack stack, ItemDisplayContext context, boolean left, PoseStack matrices,
            MultiBufferSource enclosingSource, int light, int overlay, BakedModel model) {
        return ItemRenderer.getCompassFoilBufferDirect(GlintLayers.forStack(source,stack,context),type,pose);
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;getCompassFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer coloredglint$getCompassFoilBuffer(MultiBufferSource source, RenderType type, PoseStack.Pose pose,
            ItemStack stack, ItemDisplayContext context, boolean left, PoseStack matrices,
            MultiBufferSource enclosingSource, int light, int overlay, BakedModel model) {
        return ItemRenderer.getCompassFoilBuffer(GlintLayers.forStack(source,stack,context),type,pose);
    }
}
