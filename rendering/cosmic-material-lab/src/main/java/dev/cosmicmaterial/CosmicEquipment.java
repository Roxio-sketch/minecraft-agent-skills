package dev.cosmicmaterial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import java.util.List;

/** Shared NBT contract for the worn-armor and vanilla-shield geometry adapters. */
public final class CosmicEquipment {
    private CosmicEquipment() {}

    public static boolean enabled(ItemStack stack) {
        return CosmicRender.shader != null && stack.hasTag() && stack.getTag().contains("CosmicMaterial", 10);
    }

    public static int palette(ItemStack stack) {
        String value = stack.getTag().getCompound("CosmicMaterial").getString("Palette");
        return value.equals("gold") ? 1 : value.equals("cyan") ? 2 : 0;
    }

    public static RenderType armor(ResourceLocation texture, ItemStack stack, EquipmentSlot slot) {
        return CosmicRender.externalType(texture, ItemDisplayContext.NONE, palette(stack), "armor_"+slot.name());
    }

    public static RenderType shield(ResourceLocation texture, ItemDisplayContext context, ItemStack stack) {
        if (!stack.is(Items.SHIELD)) throw new IllegalArgumentException("Expected vanilla shield");
        return CosmicRender.externalType(texture, context, palette(stack), framed(stack)?"shield_framed":"shield");
    }

    /** Opt-in shield preset: keep the vanilla metal rim while the field remains Cosmic. */
    public static boolean framed(ItemStack stack) {
        return enabled(stack) && "framed".equals(stack.getTag().getCompound("CosmicMaterial").getString("ShieldStyle"));
    }

    /** The patterned shield's vanilla path draws an opaque base before its symbols.
     * Draw our material as that base, then keep the native symbol layers. */
    public static void patternedShield(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
        ModelPart plate, Material base, List<Pair<Holder<BannerPattern>,DyeColor>> patterns,
        ItemDisplayContext context, ItemStack stack) {
        plate.render(pose,base.sprite().wrap(ItemRenderer.getFoilBufferDirect(buffers,
            shield(base.atlasLocation(),context,stack),true,stack.hasFoil())),light,overlay);
        // createPatterns puts the opaque banner base at index 0. The Cosmic
        // plate above replaces that base; later entries are the actual symbols.
        for(int i=1;i<Math.min(17,patterns.size());i++) {
            Pair<Holder<BannerPattern>,DyeColor> pattern=patterns.get(i);
            float[] tint=pattern.getSecond().getTextureDiffuseColors();
            pattern.getFirst().unwrapKey().map(Sheets::getShieldMaterial).ifPresent(material ->
                plate.render(pose,material.buffer(buffers,RenderType::entityNoOutline),light,overlay,
                    tint[0],tint[1],tint[2],1f));
        }
    }
}
