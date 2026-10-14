package dev.cosmicmaterial.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cosmicmaterial.CosmicEquipment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changes only the armor base and dye overlay passes; vanilla trim and glint remain intact. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Unique private ItemStack cosmicmaterial$currentArmor = ItemStack.EMPTY;
    @Unique private EquipmentSlot cosmicmaterial$currentSlot;

    @Inject(method="renderArmorPiece",at=@At("HEAD"))
    private void cosmicmaterial$begin(PoseStack pose,MultiBufferSource buffers,LivingEntity entity,
                                      EquipmentSlot slot,int light,HumanoidModel<?> model,CallbackInfo ci) {
        cosmicmaterial$currentArmor=entity.getItemBySlot(slot);
        cosmicmaterial$currentSlot=slot;
    }

    @Inject(method="renderArmorPiece",at=@At("RETURN"))
    private void cosmicmaterial$end(PoseStack pose,MultiBufferSource buffers,LivingEntity entity,
                                    EquipmentSlot slot,int light,HumanoidModel<?> model,CallbackInfo ci) {
        cosmicmaterial$currentArmor=ItemStack.EMPTY;
        cosmicmaterial$currentSlot=null;
    }

    @Redirect(method="renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer cosmicmaterial$armorBuffer(MultiBufferSource buffers,RenderType original,
        PoseStack pose,MultiBufferSource ignored,int light,ArmorItem armor,Model model,boolean inner,
        float red,float green,float blue,ResourceLocation texture) {
        ItemStack stack=cosmicmaterial$currentArmor;
        if(cosmicmaterial$currentSlot!=null && stack.getItem()==armor && CosmicEquipment.enabled(stack))
            return buffers.getBuffer(CosmicEquipment.armor(texture,stack,cosmicmaterial$currentSlot));
        return buffers.getBuffer(original);
    }
}
