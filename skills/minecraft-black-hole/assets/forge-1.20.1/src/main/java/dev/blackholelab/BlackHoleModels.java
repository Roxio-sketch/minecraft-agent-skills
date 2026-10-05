package dev.blackholelab;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=BlackHoleLab.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BlackHoleModels {
    public static int wrappedCount;
    private static final Map<BakedModel,BakedModel> CACHE=new IdentityHashMap<>();
    @SubscribeEvent public static void baked(ModelEvent.ModifyBakingResult e){
        CACHE.clear();BlackHoleRender.clearTypes();wrappedCount=0;
        e.getModels().replaceAll((key,model)->{if(key instanceof ModelResourceLocation r&&r.getVariant().equals("inventory")&&!model.isCustomRenderer()){wrappedCount++;return wrap(model);}return model;});
    }
    private static BakedModel wrap(BakedModel m){return m==null||m instanceof Wrapped||m.isCustomRenderer()?m:CACHE.computeIfAbsent(m,v->new Wrapped(v,ItemDisplayContext.NONE));}
    private static final class Wrapped extends BakedModelWrapper<BakedModel>{
        private final ItemDisplayContext context;
        Wrapped(BakedModel m,ItemDisplayContext context){super(m);this.context=context;}
        @Override public ItemOverrides getOverrides(){return new ItemOverrides(){
            @Override public BakedModel resolve(BakedModel ignored,ItemStack stack,ClientLevel world,LivingEntity entity,int seed){
                BakedModel resolved=originalModel.getOverrides().resolve(originalModel,stack,world,entity,seed);
                return wrap(resolved==null?originalModel:resolved);
            }
        };}
        @Override public BakedModel applyTransform(ItemDisplayContext c,PoseStack pose,boolean left){return new Wrapped(originalModel.applyTransform(c,pose,left),c);}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){
            List<BakedModel> passes=originalModel.getRenderPasses(stack,fabulous);
            if(!stack.hasTag()||!stack.getTag().contains("BlackHoleMaterial",10)||BlackHoleRender.shader==null)return passes;
            var tag=stack.getTag().getCompound("BlackHoleMaterial");
            int rgb=tag.contains("CoreColor",99)?tag.getInt("CoreColor")&0xffffff:switch(tag.getString("Palette")){case "violet"->0x7d3cff;case "cyan"->0x21d9ff;default->0;};
            boolean blade=stack.is(Items.DIAMOND_SWORD)&&tag.getString("Mask").equals("blade");
            List<BakedModel> output=new ArrayList<>();
            for(BakedModel pass:passes){
                Set<ResourceLocation> sprites=new LinkedHashSet<>();
                for(Direction side:Direction.values())collect(pass,side,sprites);
                collect(pass,null,sprites);
                if(sprites.isEmpty()){output.add(pass);continue;}
                for(ResourceLocation sprite:sprites)output.add(new MaterialPass(pass,BlackHoleRender.type(context,rgb,blade,sprite),sprite));
            }
            BlackHoleRender.contexts.merge(context.name(),1L,Long::sum);return output;
        }
    }
    private static void collect(BakedModel model,Direction side,Set<ResourceLocation> sprites){
        for(BakedQuad q:model.getQuads(null,side,RandomSource.create(42),ModelData.EMPTY,null))sprites.add(q.getSprite().contents().name());
    }
    private static final class MaterialPass extends BakedModelWrapper<BakedModel>{
        private final RenderType type;private final ResourceLocation sprite;
        MaterialPass(BakedModel model,RenderType type,ResourceLocation sprite){super(model);this.type=type;this.sprite=sprite;}
        @Override public List<RenderType> getRenderTypes(ItemStack stack,boolean fabulous){return List.of(type);}
        private List<BakedQuad> filter(List<BakedQuad> quads){return quads.stream().filter(q->q.getSprite().contents().name().equals(sprite)).toList();}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return filter(originalModel.getQuads(state,side,random));}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType){return filter(originalModel.getQuads(state,side,random,data,null));}
    }
}
