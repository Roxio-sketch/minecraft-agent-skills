package dev.cosmiclab;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.*;
import java.io.IOException;
import java.util.Arrays;

/** Same original physical GLSL and GPU LUT dimensions as the Web workbench. */
final class LabAtmosphere {
    static final String[] NAMES = {"transmittance", "multiple", "sky_view"};
    static final String[] SAMPLERS = {"AtmosphereTransmittance", "AtmosphereMultipleScattering", "AtmosphereSkyViewSampler"};
    static final int[] WIDTH = {256, 32, 512}, HEIGHT = {64, 32, 128};
    static final ShaderInstance[] programs = new ShaderInstance[3];
    static final Target[] targets = new Target[3];
    static final int[] builds = new int[3];
    static int generation;
    static float observerHeight, sunY;
    static boolean ready, failed;
    static String reason = "not-requested", mediumKey, viewKey;

    static void register(RegisterShadersEvent event) throws IOException {
        release(); failed = false;
        for (int i = 0; i < NAMES.length; i++) {
            final int pass = i;
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(CosmicLab.ID, "atmosphere_" + NAMES[i]), DefaultVertexFormat.POSITION),
                    loaded -> { programs[pass] = loaded; generation++; });
        }
    }

    static void release() {
        for (int i = 0; i < targets.length; i++) if (targets[i] != null) { targets[i].close(); targets[i] = null; }
        ready = false; mediumKey = viewKey = null;
    }

    static void prepare(double cameraY) {
        SkyRecipe p = LabSky.recipe;
        observerHeight = Math.max(p.value("observerAltitude") + ((float)cameraY - p.value("groundLevel")) * .001f, .001f);
        sunY = (float)Math.sin(Math.toRadians(p.value("sunElevation")) + LabSky.actualTime * p.value("cycleSpeed") * .035f);
        if (p.value("atmosphereStyle") != 2 || failed) { ready = false; return; }
        if (Arrays.stream(programs).anyMatch(s -> s == null)) { ready = false; reason = "generation-program-unavailable"; return; }
        String nextMedium = key(p, "planetRadius", "atmosphereHeight", "rayleighHeight", "mieHeight",
                "rayleighDensity", "mieDensity", "ozoneDensity", "groundAlbedo", "mieAnisotropy");
        String nextView = nextMedium + "/" + observerHeight + "/" + sunY + "/" + LabSky.quality + "/" +
                key(p, "multipleScattering", "sunBrightness") + "/" + p.toJson().getAsJsonObject("parameters").get("sunColor") +
                "/" + p.toJson().getAsJsonObject("parameters").get("skyTint");
        if (ready && nextMedium.equals(mediumKey) && nextView.equals(viewKey)) return;
        try (LabEnvironment.State state = new LabEnvironment.State()) {
            if (targets[0] == null) for (int i = 0; i < targets.length; i++) targets[i] = new Target(WIDTH[i], HEIGHT[i]);
            if (!nextMedium.equals(mediumKey)) { draw(0); draw(1); mediumKey = nextMedium; }
            draw(2); viewKey = nextView; ready = true; reason = "physical-luts-ready";
        } catch (Exception error) {
            failed = true; ready = false; reason = "physical-lut-failure"; error.printStackTrace();
            LabValidation.environmentFailure(error);
        }
    }

    private static String key(SkyRecipe recipe, String... names) {
        StringBuilder key = new StringBuilder(); for (String name : names) key.append(recipe.value(name)).append('/'); return key.toString();
    }

    static void bind(ShaderInstance shader) {
        shader.safeGetUniform("PhysicalObserverHeight").set(observerHeight);
        shader.safeGetUniform("AtmosphereLutReady").set(ready ? 1 : 0);
        shader.safeGetUniform("AtmosphereSkyViewReady").set(ready ? 1 : 0);
        shader.safeGetUniform("AtmosphereLutSun").set(0f, sunY, (float)Math.sqrt(Math.max(1f - sunY * sunY, 0f)));
        for (int i = 0; i < SAMPLERS.length; i++) shader.setSampler(SAMPLERS[i], targets[i] == null ? 0 : targets[i].texture);
    }

    private static void draw(int pass) {
        ShaderInstance shader = programs[pass];
        LabSky.recipe.upload(shader, LabSky.quality); bind(shader);
        shader.safeGetUniform("AtmosphereLutReady").set(1); shader.safeGetUniform("AtmosphereSkyViewReady").set(0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targets[pass].framebuffer);
        RenderSystem.viewport(0, 0, WIDTH[pass], HEIGHT[pass]);
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableBlend(); RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_SCISSOR_TEST); RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShader(() -> shader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder(); buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        buffer.vertex(-1, -1, 0).endVertex(); buffer.vertex(3, -1, 0).endVertex(); buffer.vertex(-1, 3, 0).endVertex();
        int query = LabGpuTimings.begin("atmosphere-" + NAMES[pass]);
        try { shader.apply(); LabAdvancedValidation.lutBuildDraw(shader,pass); BufferUploader.draw(buffer.end()); builds[pass]++; }
        finally { shader.clear(); LabGpuTimings.end("atmosphere-" + NAMES[pass], query); }
    }

    static final class Target implements AutoCloseable {
        final int texture, framebuffer;
        Target(int width, int height) {
            texture = GlStateManager._genTexture(); GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, 0L);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            framebuffer = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("RGBA16F atmosphere framebuffer incomplete");
        }
        public void close() { GlStateManager._deleteTexture(texture); GL30.glDeleteFramebuffers(framebuffer); }
    }
}
