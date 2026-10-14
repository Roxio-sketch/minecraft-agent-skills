package dev.coloredglint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

/** Data-only parsing: no client classes and no stack mutation. */
public record GlintStyle(String color, boolean force) {
    public static final String TAG = "ColoredGlint";
    public static final Set<String> COLORS = Set.of("red","blue","purple","green","yellow","orange","white","pink","aqua",
        "rose_gold","aurora","rainbow_cycle","ember_wave");
    public static final GlintStyle NONE = new GlintStyle("default", false);
    public static GlintStyle resolve(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (stack.isEmpty() || root == null || !root.contains(TAG, 10)) return NONE;
        CompoundTag data = root.getCompound(TAG);
        if (!data.contains("Color", 8)) return NONE;
        String color = data.getString("Color");
        if (!color.equals("default") && !COLORS.contains(color)) return NONE;
        return new GlintStyle(color, data.getBoolean("Force"));
    }
}
