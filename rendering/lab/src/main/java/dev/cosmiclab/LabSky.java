package dev.cosmiclab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.io.IOException;

@Mod.EventBusSubscriber(modid = CosmicLab.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LabSky extends DimensionSpecialEffects {
    static ShaderInstance shader;
    static int generation;
    static int mode = 2;
    static int quality = 2;
    static boolean enabled = true;
    static boolean frozen;
    private static final long START = System.nanoTime();

    public LabSky() { super(Float.NaN, true, SkyType.NORMAL, false, false); }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(CosmicLab.ID, "sky"), DefaultVertexFormat.POSITION), loaded -> {
            // Only store the instance delivered by the reload callback.
            shader = loaded;
            generation++;
            System.out.println("[COSMICLAB] Shader loaded, generation=" + generation);
        });
    }

    @SubscribeEvent
    public static void effects(RegisterDimensionSpecialEffectsEvent event) {
        // Deliberately replaces overworld skies in this isolated experimental mod.
        event.register(new ResourceLocation("minecraft", "overworld"), new LabSky());
    }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color.multiply(brightness * 0.94 + 0.06, brightness * 0.94 + 0.06, brightness * 0.91 + 0.09);
    }
    @Override public boolean isFoggyAt(int x, int z) { return false; }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack pose,
                             Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
        if (!enabled || shader == null) return false;
        if (foggy || camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return false;
        setupFog.run();
        ShaderInstance previous = RenderSystem.getShader();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        Matrix4f inverseProjection = new Matrix4f(projection).invert();
        // Remove all translation. Use the actual rendered view basis, including camera rotation.
        Matrix3f inverseViewRotation = new Matrix3f(pose.last().pose()).invert();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> shader);
            shader.getUniform("InverseProjection").set(inverseProjection);
            shader.getUniform("InverseViewRotation").set(inverseViewRotation);
            shader.getUniform("Mode").set(mode);
            shader.getUniform("Quality").set(quality);
            shader.getUniform("Time").set(frozen ? 0f : (System.nanoTime() - START) / 1_000_000_000f);
            LabValidation.beginGpu();
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            buffer.vertex(-1, -1, 1).endVertex();
            buffer.vertex(3, -1, 1).endVertex();
            buffer.vertex(-1, 3, 1).endVertex();
            BufferUploader.drawWithShader(buffer.end());
            LabValidation.endGpu();
            LabValidation.afterSky(camera, projection, inverseViewRotation);
        } finally {
            RenderSystem.depthMask(mask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previous);
        }
        return true;
    }

    @Mod.EventBusSubscriber(modid = CosmicLab.ID, value = Dist.CLIENT)
    public static final class Controls {
        @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
            var root = Commands.literal("cosmiclab");
            String[] modes = {"direction", "stars", "nebula"};
            for (int i = 0; i < modes.length; i++) {
                final int selected = i;
                root.then(Commands.literal(modes[i]).executes(ctx -> {
                    mode = selected; enabled = true;
                    ctx.getSource().sendSuccess(() -> Component.literal("Cosmic Lab: " + modes[selected]), false);
                    return 1;
                }));
            }
            root.then(Commands.literal("off").executes(ctx -> { enabled = false; return 1; }));
            root.then(Commands.literal("freeze").executes(ctx -> { frozen = !frozen; return 1; }));
            root.then(Commands.literal("quality").then(Commands.argument("value", IntegerArgumentType.integer(1, 3))
                    .executes(ctx -> { quality = IntegerArgumentType.getInteger(ctx, "value"); return 1; })));
            event.getDispatcher().register(root);
        }
    }
}
