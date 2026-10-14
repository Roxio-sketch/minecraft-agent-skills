package dev.cosmiclab;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.io.IOException;

/** World-space atmosphere pass; current main color and depth are copied before sampling. */
@Mod.EventBusSubscriber(modid = CosmicLab.ID, value = Dist.CLIENT)
public final class LabEnvironment {
    static ShaderInstance shader;
    static ShaderInstance compositeShader;
    static TextureTarget snapshot;
    static TextureTarget reduced;
    static int generation, draws;
    static int earlyDraws, finalDraws, compositeDraws;
    static float requestedScale=1f, effectiveScale=1f;
    static String scaleReason="requested";
    static boolean failed;

    static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(CosmicLab.ID, "environment"), DefaultVertexFormat.POSITION), loaded -> {
            shader = loaded; generation++; failed = false; release();
            System.out.println("[COSMICLAB] World environment loaded, generation=" + generation);
        });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(CosmicLab.ID, "environment_composite"), DefaultVertexFormat.POSITION), loaded -> compositeShader = loaded);
    }

    static void release() {
        if (snapshot != null) { snapshot.destroyBuffers(); snapshot = null; }
        if (reduced != null) { reduced.destroyBuffers(); reduced = null; }
    }

    @Mod.EventBusSubscriber(modid = CosmicLab.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void listeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> RenderSystem.recordRenderCall(() -> {
                release(); LabAtmosphere.release(); LabPrecipitation.release();
            }));
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        release(); LabAtmosphere.release(); LabPrecipitation.release(); LabTranslucency.release(); LabGpuTimings.release();
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            LabSky.frameCameraReady = false; LabTranslucency.frameApplied = false; LabGpuTimings.poll();
        }
    }

    static boolean needed(float rain) {
        var p = LabSky.recipe;
        if (p == null) return false;
        return p.value("volumeCloudStrength") > 0 || p.value("fogStrength") > 0 ||
                p.value("cloudShadowStrength") > 0 || p.value("lightShaftStrength") > 0 ||
                ((p.value("rainBrightness") > 0 || (p.value("precipitationMode") > 0 && p.value("snowBrightness") > 0)) &&
                        (p.value("weatherFollowWorld") > .5f ? rain : p.value("rainAmount")) > 0);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || LabTranslucency.frameApplied) return;
        if (LabSky.recipe!=null&&LabSky.recipe.value("nativeTransparentMode")>0&&LabRenderOwner.embeddium()&&!LabRenderOwner.handoff())
            LabTranslucency.reason="embeddium-native-surface-bypass-legacy";
        renderPass(event.getCamera(), event.getPartialTick(), false);
    }

    static boolean renderPass(Camera cameraObject, float partialTick, boolean early) {
        if (!LabSky.enabled || LabSky.mode != 2 || shader == null || failed || !LabSky.frameCameraReady || LabRenderOwner.handoff()) return false;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD || cameraObject.getFluidInCamera() != FogType.NONE) return false;
        float rain = mc.level.getRainLevel(partialTick), thunder = mc.level.getThunderLevel(partialTick);
        if (!needed(rain)) { if (!early) LabValidation.afterEnvironmentSkipped(); return false; }
        RenderTarget main = mc.getMainRenderTarget();
        // A renderer using an unrelated framebuffer requires its own explicit adapter.
        if (main.getDepthTextureId() < 0 || (!early && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.frameBufferId)) return false;
        var stateEvidence=LabAdvancedValidation.beginState(early?"environment-opaque":"environment-final");
        State state = new State();
        try (state) {
            if (snapshot == null || snapshot.width != main.width || snapshot.height != main.height) {
                release(); snapshot = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                snapshot.setFilterMode(GL11.GL_NEAREST);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.frameBufferId);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Environment snapshot FBO incomplete");
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            requestedScale = LabSky.recipe.value("environmentRenderScale");
            var recipe = LabSky.recipe;
            float amount = recipe.value("weatherFollowWorld") > .5f ? rain : recipe.value("rainAmount");
            boolean tinySnow = amount > 0 && recipe.value("snowSize") < .04f && recipe.value("snowBrightness") > 0 &&
                    (recipe.value("precipitationMode") == 3 || (recipe.value("precipitationMode") == 1 && LabPrecipitation.ready && LabPrecipitation.snowCells > 0));
            float scale = effectiveScale = tinySnow ? 1f : requestedScale;
            scaleReason = tinySnow && requestedScale < 1f ? "tiny-snow-full-resolution" : "requested";
            RenderTarget output = main;
            if (scale < .999f) {
                int width = Math.max(1, Math.round(main.width * scale)), height = Math.max(1, Math.round(main.height * scale));
                if (reduced == null || reduced.width != width || reduced.height != height) {
                    if (reduced != null) reduced.destroyBuffers();
                    reduced = new TextureTarget(width, height, false, Minecraft.ON_OSX); reduced.setFilterMode(GL11.GL_NEAREST);
                }
                if (compositeShader == null) throw new IllegalStateException("Reduced environment composite program unavailable");
                output = reduced;
            }
            output.bindWrite(true);
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableBlend(); RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            Matrix4f inverseProjection = new Matrix4f(LabSky.frameInverseProjection);
            Matrix3f inverseView = new Matrix3f(LabSky.frameInverseViewRotation);
            if (!inverseProjection.isFinite() || !inverseView.isFinite()) throw new IllegalStateException("Non-invertible environment camera");
            shader.safeGetUniform("InverseProjection").set(inverseProjection);
            shader.safeGetUniform("InverseViewRotation").set(inverseView);
            shader.safeGetUniform("InverseViewTranslation").set(LabSky.frameInverseViewTranslation);
            var camera = cameraObject.getPosition();
            shader.safeGetUniform("CameraPosition").set((float)camera.x, (float)camera.y, (float)camera.z);
            shader.safeGetUniform("WorldWeather").set(rain, thunder);
            shader.safeGetUniform("Time").set(LabSky.actualTime);
            LabSky.recipe.upload(shader, LabSky.quality);
            shader.safeGetUniform("EnvironmentStage").set(0);
            shader.safeGetUniform("OutputDeltaEncoding").set(output == reduced ? 2 : 0);
            LabAtmosphere.bind(shader); LabPrecipitation.bind(shader);
            shader.setSampler("SceneSampler", snapshot.getColorTextureId());
            shader.setSampler("DepthSampler", snapshot.getDepthTextureId());
            RenderSystem.setShader(() -> shader);
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            buffer.vertex(-1, -1, 0).endVertex(); buffer.vertex(3, -1, 0).endVertex(); buffer.vertex(-1, 3, 0).endVertex();
            try {
                int query = LabGpuTimings.begin(early ? "environment-opaque" : "environment-final");
                try { shader.apply(); LabAdvancedValidation.environmentDraw(shader, early); BufferUploader.draw(buffer.end()); draws++; }
                finally { LabGpuTimings.end(early ? "environment-opaque" : "environment-final", query); }
                if (early) earlyDraws++; else finalDraws++;
                if (output == reduced) composite(main, inverseProjection);
                LabAdvancedValidation.environmentDepth(snapshot, main);
                if (!early) LabValidation.afterEnvironment(shader, snapshot, main, inverseProjection, inverseView, state);
            } finally { shader.clear(); }
        } catch (Exception error) {
            failed = true; error.printStackTrace(); LabValidation.environmentFailure(error);
        }
        LabAdvancedValidation.endState(early?"environment-opaque":"environment-final",stateEvidence);
        if (!early) LabValidation.afterEnvironmentRestore(state);
        return !failed;
    }

    private static void composite(RenderTarget main, Matrix4f inverseProjection) {
        main.bindWrite(true); RenderSystem.setShader(() -> compositeShader);
        compositeShader.safeGetUniform("InverseProjection").set(inverseProjection);
        compositeShader.safeGetUniform("LowSize").set((float)reduced.width, (float)reduced.height);
        compositeShader.safeGetUniform("DepthTolerance").set(LabSky.recipe.value("environmentDepthTolerance"));
        compositeShader.safeGetUniform("OutputDeltaEncoding").set(2);
        compositeShader.setSampler("SceneSampler", snapshot.getColorTextureId());
        compositeShader.setSampler("DepthSampler", snapshot.getDepthTextureId());
        compositeShader.setSampler("EnvironmentSampler", reduced.getColorTextureId());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder(); buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        buffer.vertex(-1,-1,0).endVertex(); buffer.vertex(3,-1,0).endVertex(); buffer.vertex(-1,3,0).endVertex();
        int query = LabGpuTimings.begin("environment-composite");
        try { compositeShader.apply(); BufferUploader.draw(buffer.end()); compositeDraws++; }
        finally { compositeShader.clear(); LabGpuTimings.end("environment-composite", query); }
    }

    /** Restore the GL state and Minecraft's cached texture/shader state, including the bound program. */
    static final class State implements AutoCloseable {
        final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        // Minecraft 1.20.1 caches exactly 12 units; our programs use at most 7.
        final int[] viewport = new int[4], bound = new int[12], shaderTexture = new int[12];
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final boolean[] colorMask = new boolean[4];
        final ShaderInstance previous = RenderSystem.getShader();
        State() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            var mask = org.lwjgl.BufferUtils.createByteBuffer(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
            for (int i = 0; i < bound.length; i++) { if (i < 12) shaderTexture[i] = RenderSystem.getShaderTexture(i); GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i); bound[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
            GlStateManager._activeTexture(active);
        }
        public void close() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            for (int i = 0; i < bound.length; i++) { if (i < 12) RenderSystem.setShaderTexture(i, shaderTexture[i]); GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i); GlStateManager._bindTexture(bound[i]); }
            GlStateManager._activeTexture(active); RenderSystem.setShader(() -> previous); GlStateManager._glUseProgram(program);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest(); RenderSystem.depthMask(write);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend(); if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        }
    }
}
