package dev.coloredglint.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.coloredglint.GlintLayers;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.SortedMap;

@Mixin(RenderBuffers.class)
public abstract class RenderBuffersMixin {
    @Accessor("fixedBuffers")
    protected abstract SortedMap<RenderType,BufferBuilder> coloredglint$buffers();
    @Inject(method="<init>",at=@At("RETURN"))
    private void coloredglint$register(CallbackInfo ci) { GlintLayers.registerBuffers(coloredglint$buffers()); }
}
