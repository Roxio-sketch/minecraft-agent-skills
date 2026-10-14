package dev.cosmiclab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.*;

/** Native BLOCK surfaces keep geometry, light, alpha, sorting, depth and Fabulous targets. */
public final class LabTranslucency {
    static ShaderInstance shader;
    static boolean resourceContract, frameApplied;
    static int generation, selections, vboDraws, fallbacks;
    static String reason = "legacy-final";
    private static LabEnvironment.State layerState;
    private static int gpuQuery;
    private static com.google.gson.JsonObject nativeStateEvidence;

    static void register(RegisterShadersEvent event) {
        release(); resourceContract = false;
        try {
            Properties expected = new Properties();
            try (var in = LabTranslucency.class.getResourceAsStream("/cosmiclab-vanilla-contract.properties")) { expected.load(in); }
            boolean valid = true;
            for (String name : expected.stringPropertyNames()) {
                var resource = event.getResourceProvider().getResourceOrThrow(new ResourceLocation("minecraft", "shaders/" + name));
                try (var input = resource.open()) {
                    String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                    if (!Arrays.asList(expected.getProperty(name).split(",")).contains(hash)) {
                        valid = false; System.out.println("[COSMICLAB] native surface resource override " + name + " pack=" + resource.sourcePackId());
                    }
                }
            }
            resourceContract = valid;
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(CosmicLab.ID, "surface"),
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK), loaded -> { shader = loaded; generation++; });
            reason = valid ? "native-ready" : "resource-owner-legacy-fallback";
        } catch (Exception error) {
            shader = null; reason = "surface-program-unavailable-legacy-fallback";
            error.printStackTrace();
        }
    }

    static void release() { frameApplied = false; if (layerState != null) { layerState.close(); layerState = null; } }

    public static void begin(RenderType layer) {
        if (layer != RenderType.translucent() || LabSky.recipe == null) return;
        if (LabSky.recipe.value("nativeTransparentMode") == 0) { reason = "legacy-final"; return; }
        if (LabRenderOwner.handoff()) { reason = "shaderpack-owner-fallback"; return; }
        if (LabRenderOwner.embeddium()) { reason = "embeddium-native-surface-bypass-legacy"; fallbacks++; return; }
        ShaderInstance original = GameRenderer.getRendertypeTranslucentShader();
        if (shader == null || !resourceContract || original == null || original.getClass() != ShaderInstance.class) {
            String detail="surface="+(shader==null?"null":shader.getClass().getName())+" original="+(original==null?"null":original.getClass().getName())+" contract="+resourceContract;
            if(!reason.equals("surface-owner-legacy-fallback/"+detail))System.out.println("[COSMICLAB] native fallback "+detail);
            reason = "surface-owner-legacy-fallback/"+detail; fallbacks++; return;
        }
        if (!LabSky.enabled || LabSky.mode != 2 || !LabSky.frameCameraReady) return;
        if (Minecraft.useShaderTransparency() && Minecraft.getInstance().levelRenderer.getTranslucentTarget() == null) {
            reason="fabulous-target-warming"; return;
        }
        nativeStateEvidence=LabAdvancedValidation.beginState("native-translucent");
        layerState = new LabEnvironment.State();
        var mc = Minecraft.getInstance();
        frameApplied = LabEnvironment.renderPass(mc.gameRenderer.getMainCamera(), mc.getFrameTime(), true);
        if (frameApplied) reason = Minecraft.useShaderTransparency() ? "native-fabulous" : "native-fancy";
    }

    public static ShaderInstance select(ShaderInstance original, RenderType layer, PoseStack pose, Matrix4f projection,
                                        double x, double y, double z) {
        if (layer != RenderType.translucent() || !frameApplied) return original;
        var mc = Minecraft.getInstance();
        int target = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int expected = Minecraft.useShaderTransparency() ? mc.levelRenderer.getTranslucentTarget().frameBufferId : mc.getMainRenderTarget().frameBufferId;
        if (original != GameRenderer.getRendertypeTranslucentShader() || original.getClass() != ShaderInstance.class || target != expected) {
            rollback(); reason = "changed-surface-owner-legacy-fallback"; return original;
        }
        LabSky.recipe.upload(shader, LabSky.quality);
        var p = mc.gameRenderer.getMainCamera().getPosition();
        shader.safeGetUniform("CameraPosition").set((float)p.x, (float)p.y, (float)p.z);
        shader.safeGetUniform("OpticalEyeOffset").set(LabSky.frameOpticalEyeOffset);
        shader.safeGetUniform("WorldWeather").set(mc.level.getRainLevel(mc.getFrameTime()), mc.level.getThunderLevel(mc.getFrameTime()));
        shader.safeGetUniform("Time").set(LabSky.actualTime); shader.safeGetUniform("EnvironmentStage").set(0);
        int rgb = Integer.parseInt(LabSky.recipe.toJson().getAsJsonObject("parameters").get("fogColor").getAsString().substring(1), 16);
        shader.safeGetUniform("EnvironmentFogColor").set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        LabAtmosphere.bind(shader); LabPrecipitation.bind(shader);
        selections++; gpuQuery = LabGpuTimings.begin("native-translucent"); return shader;
    }

    public static void recordDraw(RenderType layer) {
        if (layer == RenderType.translucent()) {
            if (frameApplied) vboDraws++;
            LabAdvancedValidation.surfaceDraw();
        }
    }

    private static void rollback() {
        if (frameApplied && LabEnvironment.snapshot != null) {
            try (LabEnvironment.State state = new LabEnvironment.State()) {
                var main = Minecraft.getInstance().getMainRenderTarget();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, LabEnvironment.snapshot.frameBufferId);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
                GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            }
        }
        frameApplied = false; fallbacks++;
    }

    public static void end() {
        if (gpuQuery != 0) { LabGpuTimings.end("native-translucent", gpuQuery); gpuQuery = 0; }
        if (layerState != null) { layerState.close(); layerState = null; }
        LabAdvancedValidation.endState("native-translucent",nativeStateEvidence);nativeStateEvidence=null;
    }
}
