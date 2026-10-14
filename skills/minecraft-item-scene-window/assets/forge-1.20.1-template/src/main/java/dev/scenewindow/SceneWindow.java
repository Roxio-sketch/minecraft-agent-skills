package dev.scenewindow;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import java.util.function.Consumer;

@Mod(SceneWindow.ID)
public final class SceneWindow {
    public static final String ID = "scenewindow";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<Item> WINDOW = ITEMS.register("window", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(new IClientItemExtensions() {
                @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return WindowRenderer.instance(); }
            });
        }
    });
    public SceneWindow() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
}
