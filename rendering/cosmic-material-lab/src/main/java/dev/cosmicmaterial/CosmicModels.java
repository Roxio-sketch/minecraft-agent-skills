package dev.cosmicmaterial;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=CosmicMaterial.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CosmicModels {
    public static int wrappedCount;
    private static final Map<BakedModel,BakedModel> CACHE = new IdentityHashMap<>();
    @SubscribeEvent public static void baked(ModelEvent.ModifyBakingResult event) {
        CACHE.clear();
        CosmicRender.clearTypes();
        wrappedCount = 0;
        event.getModels().replaceAll((key, model) -> {
            if (key instanceof ModelResourceLocation location && location.getVariant().equals("inventory")
                    && !model.isCustomRenderer()) {
                wrappedCount++;
                return wrap(model);
            }
            return model;
        });
        System.out.println("[COSMIC-MATERIAL] Wrapped item roots: " + wrappedCount);
    }
    private static BakedModel wrap(BakedModel model) {
        if (model == null || model instanceof Wrapped || model.isCustomRenderer()) return model;
        return CACHE.computeIfAbsent(model, m -> new Wrapped(m, ItemDisplayContext.NONE));
    }
    private static final class Wrapped extends BakedModelWrapper<BakedModel> {
        private final ItemDisplayContext context;
        private final ItemOverrides overrides;
        Wrapped(BakedModel model, ItemDisplayContext context) {
            super(model);
            this.context = context;
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel unused, ItemStack stack, ClientLevel world,
                                                   LivingEntity entity, int seed) {
                    BakedModel resolved = originalModel.getOverrides().resolve(originalModel, stack, world, entity, seed);
                    return wrap(resolved == null ? originalModel : resolved);
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean left) {
            BakedModel transformed = originalModel.applyTransform(context, pose, left);
            return new Wrapped(transformed, context);
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            List<BakedModel> original = originalModel.getRenderPasses(stack, fabulous);
            List<BakedModel> base = original;
            if (stack.hasTag() && stack.getTag().contains("CosmicMaterial", 10) && CosmicRender.shader != null) {
                var tag = stack.getTag().getCompound("CosmicMaterial");
                String palette = tag.getString("Palette");
                int color = palette.equals("gold") ? 1 : palette.equals("cyan") ? 2 : 0;
                boolean blade = stack.is(Items.DIAMOND_SWORD) && tag.getString("Mask").equals("blade");
                var fantasy = FantasyPresets.get(tag.getString("Material"));
                CosmicRender.contexts.merge(context.name(), 1L, Long::sum);
                base = original.stream().map(model -> (BakedModel)new MaterialPass(model,
                    CosmicRender.type(context,color,blade,fantasy,fantasy==null?null:model.getParticleIcon()))).toList();
            }
            if (!stack.hasTag() || !stack.getTag().contains("OutlineMaterial", 10) || OutlineRender.shader == null)
                return base;
            OutlineRender.Style style = OutlineRender.Style.from(stack.getTag().getCompound("OutlineMaterial"));
            boolean block = stack.getItem() instanceof BlockItem;
            List<BakedModel> passes = new ArrayList<>(base.size() + original.size());
            // Draw the translated geometry first so the source pass covers its interior.
            for (BakedModel model : original) passes.add(new OutlinePass(model, OutlineRender.type(style),
                style.width(context), block));
            passes.addAll(base);
            return passes;
        }
    }
    private static final class MaterialPass extends BakedModelWrapper<BakedModel> {
        private final RenderType type;
        MaterialPass(BakedModel model, RenderType type) { super(model); this.type=type; }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) { return List.of(type); }
    }
    private static final class OutlinePass extends BakedModelWrapper<BakedModel> {
        private final RenderType type;
        private final float width;
        private final boolean block;
        OutlinePass(BakedModel model, RenderType type, float width, boolean block) {
            super(model); this.type=type; this.width=width; this.block=block;
        }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) { return List.of(type); }
        @Override public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,
                                                   Direction side, RandomSource random) {
            if (!block && side != null) return List.of();
            List<BakedQuad> source = originalModel.getQuads(state, side, random);
            if (source.isEmpty()) return List.of();
            List<BakedQuad> shifted = new ArrayList<>(source.size() * 8);
            for (int sx : new int[]{-1,1}) for (int sy : new int[]{-1,1}) for (int sz : new int[]{-1,1}) {
                for (BakedQuad quad : source) {
                    Direction normal = side == null ? quad.getDirection() : side;
                    if (!block || side != null) {
                        if (normal.getStepX()*sx + normal.getStepY()*sy + normal.getStepZ()*sz <= 0) continue;
                    }
                    int[] data=quad.getVertices().clone();
                    int stride=data.length/4;
                    for (int vertex=0; vertex<4; vertex++) {
                        int i=vertex*stride;
                        data[i]=Float.floatToRawIntBits(Float.intBitsToFloat(data[i])+sx*width);
                        data[i+1]=Float.floatToRawIntBits(Float.intBitsToFloat(data[i+1])+sy*width);
                        data[i+2]=Float.floatToRawIntBits(Float.intBitsToFloat(data[i+2])+sz*width);
                    }
                    shifted.add(new BakedQuad(data,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade()));
                }
            }
            return shifted;
        }
    }
}
