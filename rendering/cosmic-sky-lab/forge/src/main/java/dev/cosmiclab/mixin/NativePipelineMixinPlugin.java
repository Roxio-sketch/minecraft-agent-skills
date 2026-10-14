package dev.cosmiclab.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** The native BLOCK hook belongs only to the vanilla chunk renderer. */
public final class NativePipelineMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName) {
        if(!mixinClassName.endsWith(".NativeTranslucentMixin"))return true;
        var mods=FMLLoader.getLoadingModList();
        boolean overrides=mods!=null&&mods.getMods().stream().anyMatch(m->Set.of("embeddium","rubidium","sodium").contains(m.getModId()));
        System.out.println("[COSMICLAB] Native BLOCK mixin "+(overrides?"skipped for external chunk renderer":"enabled for vanilla chunk renderer"));
        return !overrides;
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
