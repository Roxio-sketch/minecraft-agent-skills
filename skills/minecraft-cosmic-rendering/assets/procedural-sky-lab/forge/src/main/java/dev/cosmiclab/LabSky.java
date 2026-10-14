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
import org.lwjgl.opengl.GL20;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;

@Mod.EventBusSubscriber(modid = CosmicLab.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LabSky extends DimensionSpecialEffects {
    static ShaderInstance shader;
    static int generation;
    static int mode = 2;
    static int quality = 2;
    static boolean enabled = true;
    static boolean frozen;
    static float timeOverride = -1f;
    static float actualTime;
    static SkyRecipe defaults;
    static SkyRecipe recipe;
    // AFTER_LEVEL's event pose stack has already been restored by GameRenderer.
    // Keep the actual level camera pair from this frame's sky draw instead.
    static final Matrix4f frameInverseProjection = new Matrix4f();
    static final Matrix3f frameInverseViewRotation = new Matrix3f();
    static final org.joml.Vector3f frameInverseViewTranslation = new org.joml.Vector3f();
    static final org.joml.Vector3f frameOpticalEyeOffset = new org.joml.Vector3f();
    static boolean frameCameraReady;
    private static final long START = System.nanoTime();
    private final DimensionSpecialEffects vanilla = new DimensionSpecialEffects.OverworldEffects();

    public LabSky() { super(Float.NaN, true, SkyType.NORMAL, false, false); }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws IOException {
        final SkyRecipe loadedDefault;
        try (var reader = event.getResourceProvider().getResourceOrThrow(
                new ResourceLocation(CosmicLab.ID, "sky/default.json")).openAsReader()) {
            loadedDefault = SkyRecipe.read(reader);
        }
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(CosmicLab.ID, "sky"), DefaultVertexFormat.POSITION), loaded -> {
            // Only store the instance delivered by the reload callback.
            shader = loaded;
            boolean usingDefault = recipe == null || recipe == defaults;
            defaults = loadedDefault;
            if (usingDefault) { recipe = defaults; quality = recipe.quality(); }
            generation++;
            System.out.println("[COSMICLAB] Shader loaded, generation=" + generation);
        });
        LabEnvironment.register(event);
        LabAtmosphere.register(event);
        LabTranslucency.register(event);
    }

    @SubscribeEvent
    public static void effects(RegisterDimensionSpecialEffectsEvent event) {
        // Deliberately replaces overworld skies in this isolated experimental mod.
        event.register(new ResourceLocation("minecraft", "overworld"), new LabSky());
    }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        if (LabRenderOwner.handoff() || !enabled) return vanilla.getBrightnessDependentFogColor(color, brightness);
        return color.multiply(brightness * 0.94 + 0.06, brightness * 0.94 + 0.06, brightness * 0.91 + 0.09);
    }
    @Override public boolean isFoggyAt(int x, int z) { return false; }
    @Override public float getCloudHeight() { return LabRenderOwner.handoff() || !enabled ? vanilla.getCloudHeight() : Float.NaN; }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                                     net.minecraft.client.renderer.LightTexture lightTexture,
                                     double x, double y, double z) {
        if (!enabled || mode != 2 || shader == null || LabEnvironment.shader == null || LabEnvironment.failed || recipe == null || LabRenderOwner.handoff()) return false;
        if (Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return false;
        float rain = recipe.value("weatherFollowWorld") > .5f ? level.getRainLevel(partialTick) : recipe.value("rainAmount");
        // The active procedural rain replaces precipitation quads only. Vanilla
        // weather state, sounds, rain ticks and biome data are unchanged.
        return rain > 0 && (recipe.value("rainBrightness") > 0 || (recipe.value("precipitationMode") > 0 && recipe.value("snowBrightness") > 0));
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack pose,
                             Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
        if (!enabled || shader == null || LabRenderOwner.handoff()) return false;
        if (foggy || camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return false;
        setupFog.run();
        var skyState = LabAdvancedValidation.beginSkyState();
        LabEnvironment.State allState = new LabEnvironment.State();
        ShaderInstance previous = RenderSystem.getShader();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        Matrix4f inverseProjection = new Matrix4f(projection).invert();
        // Remove all translation. Use the actual rendered view basis, including camera rotation.
        Matrix3f inverseViewRotation = new Matrix3f(pose.last().pose()).invert();
        if (!inverseProjection.isFinite() || !inverseViewRotation.isFinite()) {
            frameCameraReady = false; allState.close(); return false;
        }
        frameInverseProjection.set(inverseProjection);
        frameInverseViewRotation.set(inverseViewRotation);
        new Matrix4f(pose.last().pose()).invert().getTranslation(frameInverseViewTranslation);
        var eye = inverseProjection.transform(new org.joml.Vector4f(0,0,1,0));
        if(!eye.isFinite()||Math.abs(eye.w)<1e-7f){frameCameraReady=false;allState.close();return false;}
        frameOpticalEyeOffset.set(eye.x/eye.w,eye.y/eye.w,eye.z/eye.w).mul(inverseViewRotation).add(frameInverseViewTranslation);
        frameCameraReady = true;
        try {
            actualTime = timeOverride >= 0 ? timeOverride : frozen ? 0f : (System.nanoTime() - START) / 1_000_000_000f;
            LabAtmosphere.prepare(camera.getPosition().y);
            float rain = recipe.value("weatherFollowWorld") > .5f ? level.getRainLevel(partialTick) : recipe.value("rainAmount");
            if (recipe.value("precipitationMode") > 0 && rain > 0)
                LabPrecipitation.prepare(level, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> shader);
            shader.getUniform("InverseProjection").set(inverseProjection);
            shader.getUniform("InverseViewRotation").set(inverseViewRotation);
            shader.getUniform("Mode").set(mode);
            recipe.upload(shader, quality);
            LabAtmosphere.bind(shader); LabPrecipitation.bind(shader);
            shader.safeGetUniform("CameraPosition").set((float)camera.getPosition().x,
                    (float)camera.getPosition().y, (float)camera.getPosition().z);
            shader.safeGetUniform("WorldWeather").set(level.getRainLevel(partialTick), level.getThunderLevel(partialTick));
            shader.getUniform("Time").set(actualTime);
            LabValidation.beginGpu();
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            buffer.vertex(-1, -1, 1).endVertex();
            buffer.vertex(3, -1, 1).endVertex();
            buffer.vertex(-1, 3, 1).endVertex();
            // All required uniforms are explicit above; this clip-space pass has
            // no model/view standard uniforms. Keep the actual GL program bound
            // through draw and diagnostic readback, then clear it as vanilla does.
            try {
                shader.apply();
                BufferUploader.draw(buffer.end());
                LabValidation.recordDrawProgram(shader.getId());
                LabAdvancedValidation.skyDraw(shader, camera, projection, inverseViewRotation);
            } finally { shader.clear(); }
            LabValidation.endGpu();
            LabValidation.afterSky(camera, projection, inverseViewRotation);
        } finally {
            RenderSystem.depthMask(mask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previous);
            com.mojang.blaze3d.platform.GlStateManager._glUseProgram(previousProgram);
            allState.close();
            LabAdvancedValidation.endState("sky", skyState);
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
                    .executes(ctx -> {
                        quality = IntegerArgumentType.getInteger(ctx, "value");
                        recipe = recipe.with(java.util.Map.of("quality", quality));
                        return 1;
                    })));
            root.then(Commands.literal("load").executes(ctx -> {
                var path = Minecraft.getInstance().gameDirectory.toPath().resolve("config/cosmic-sky.json");
                try (var reader = Files.newBufferedReader(path)) {
                    SkyRecipe loaded = SkyRecipe.read(reader);
                    // Replace only after the complete file has passed every schema check.
                    recipe = loaded; quality = loaded.quality(); enabled = true; mode = 2;
                    ctx.getSource().sendSuccess(() -> Component.literal("星空配方已加载: config/cosmic-sky.json"), false);
                    return 1;
                } catch (IOException | RuntimeException error) {
                    ctx.getSource().sendFailure(Component.literal("配方未加载: " + error.getMessage()));
                    return 0;
                }
            }));
            root.then(Commands.literal("reset").executes(ctx -> {
                recipe = defaults; quality = defaults.quality(); enabled = true; mode = 2;
                ctx.getSource().sendSuccess(() -> Component.literal("星空已恢复默认配方"), false);
                return 1;
            }));
            event.getDispatcher().register(root);
        }
    }
}
