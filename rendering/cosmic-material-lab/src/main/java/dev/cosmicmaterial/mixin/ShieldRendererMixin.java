package dev.cosmicmaterial.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import dev.cosmicmaterial.CosmicEquipment;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/** Reuses the vanilla shield ModelPart, atlas sprite wrapper, display transforms and foil consumer. */
@Mixin(BlockEntityWithoutLevelRenderer.class)
public abstract class ShieldRendererMixin {
    @Redirect(method="renderByItem",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/model/ShieldModel;renderType(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType cosmicmaterial$shieldType(ShieldModel model,ResourceLocation atlas,
        ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(stack.is(Items.SHIELD) && CosmicEquipment.enabled(stack))
            return CosmicEquipment.shield(atlas,context,stack);
        return model.renderType(atlas);
    }

    @Redirect(method="renderByItem",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLjava/util/List;Z)V"))
    private void cosmicmaterial$shieldPatterns(PoseStack pose,MultiBufferSource buffers,int light,int overlay,
        ModelPart plate,Material base,boolean banner,List<Pair<Holder<BannerPattern>,DyeColor>> patterns,boolean foil,
        ItemStack stack,ItemDisplayContext context,PoseStack enclosingPose,MultiBufferSource enclosingBuffers,
        int enclosingLight,int enclosingOverlay) {
        if(stack.is(Items.SHIELD)&&CosmicEquipment.enabled(stack))
            CosmicEquipment.patternedShield(pose,buffers,light,overlay,plate,base,patterns,context,stack);
        else BannerRenderer.renderPatterns(pose,buffers,light,overlay,plate,base,banner,patterns,foil);
    }
}
