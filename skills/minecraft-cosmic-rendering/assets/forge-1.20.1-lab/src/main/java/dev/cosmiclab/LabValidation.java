package dev.cosmiclab;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in, local-only integration experiment. Never opens a pre-existing world. */
@Mod.EventBusSubscriber(modid = CosmicLab.ID, value = Dist.CLIENT)
public final class LabValidation {
    private static final boolean AUTO = Boolean.getBoolean("cosmiclab.autorun");
    private static final Path OUTPUT = Path.of(System.getProperty("cosmiclab.output", "validation"));
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonArray RESULTS = new JsonArray();
    private static final ArrayDeque<Integer> QUERIES = new ArrayDeque<>();
    private static final List<Double> GPU_MS = new ArrayList<>();
    private static int activeQuery;
    private static boolean started, configured, finished, waitingFinal;
    private static int titleTicks, caseIndex = -1, caseTicks, skyFrames;
    private static JsonObject record;
    private static int[] basePixels;
    private static CompletableFuture<Void> reload;
    private static String worldName;
    private static final List<Case> CASES = List.of(
        new Case("direction-base", 0, 2, 0, 0, 70, 960, 540, 0, 100, 0),
        new Case("direction-translate", 0, 2, 0, 0, 70, 960, 540, 512, 150, 512),
        new Case("direction-yaw90", 0, 2, 90, 0, 70, 960, 540, 0, 100, 0),
        new Case("direction-pitch-up", 0, 2, 0, -45, 70, 960, 540, 0, 100, 0),
        new Case("direction-fov40", 0, 2, 0, 0, 40, 960, 540, 0, 100, 0),
        new Case("direction-fov100", 0, 2, 0, 0, 100, 960, 540, 0, 100, 0),
        new Case("direction-resize", 0, 2, 0, 0, 70, 800, 600, 0, 100, 0),
        new Case("direction-reload", 0, 2, 0, 0, 70, 960, 540, 0, 100, 0),
        new Case("stars-low", 1, 1, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("stars-medium", 1, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("stars-high", 1, 3, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("nebula-low", 2, 1, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("nebula-medium", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("nebula-high", 2, 3, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("terrain-occlusion", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0)
    );
    private record Case(String name, int mode, int quality, float yaw, float pitch,
                        int fov, int width, int height, double x, double y, double z) {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!AUTO || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || ++titleTicks < 20) return;
                started = true;
                Files.createDirectories(OUTPUT);
                worldName = "COSMIC_LAB_" + System.currentTimeMillis();
                System.out.println("[COSMICLAB] Creating isolated world " + worldName);
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                    new LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20260928L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (!configured) {
                configured = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.bobView().set(false);
                mc.options.fovEffectScale().set(0.0);
                mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(5);
                mc.options.cloudStatus().set(CloudStatus.OFF);
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(120);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                LabSky.frozen = true;
                mc.setScreen(null);
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var level = server.overworld();
                    level.setDayTime(6000);
                    for (BlockPos p : BlockPos.betweenClosed(-6, 4, 16, 6, 12, 16))
                        level.setBlockAndUpdate(p, Blocks.GOLD_BLOCK.defaultBlockState());
                    server.getPlayerList().getPlayers().forEach(p -> {
                        p.getAbilities().mayfly = true;
                        p.getAbilities().flying = true;
                        p.onUpdateAbilities();
                    });
                });
                next(mc);
            }
            if (reload != null && !reload.isDone()) return;
            caseTicks++;
            if (caseTicks > 1200) throw new IllegalStateException("Scenario timed out: " + CASES.get(caseIndex).name);
        } catch (Exception e) { fail(e); }
    }

    private static void next(Minecraft mc) {
        caseIndex++;
        if (caseIndex >= CASES.size()) { finish(mc); return; }
        Case c = CASES.get(caseIndex);
        System.out.println("[COSMICLAB] Scenario " + c.name);
        caseTicks = skyFrames = 0;
        waitingFinal = false;
        GPU_MS.clear();
        while (!QUERIES.isEmpty()) GL15.glDeleteQueries(QUERIES.remove());
        LabSky.mode = c.mode; LabSky.quality = c.quality;
        mc.options.fov().set(c.fov);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), c.width, c.height);
        mc.player.getAbilities().flying = true;
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setPos(c.x, c.y, c.z);
        mc.player.setYRot(c.yaw); mc.player.setXRot(c.pitch);
        mc.player.yRotO = c.yaw; mc.player.xRotO = c.pitch;
        mc.player.xo = c.x; mc.player.yo = c.y; mc.player.zo = c.z;
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.connection.teleport(c.x, c.y, c.z, c.yaw, c.pitch);
        });
        record = new JsonObject();
        record.addProperty("name", c.name);
        record.addProperty("mode", c.mode);
        record.addProperty("quality", c.quality);
        record.addProperty("requestedFov", c.fov);
        record.addProperty("generationBefore", LabSky.generation);
        if (c.name.equals("direction-reload")) reload = mc.reloadResourcePacks();
    }

    static void beginGpu() {
        if (!AUTO || finished || caseIndex < 0) return;
        while (!QUERIES.isEmpty() && GL15.glGetQueryObjecti(QUERIES.peek(), GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            int query = QUERIES.remove();
            long nanos = GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT);
            if (skyFrames >= 30) GPU_MS.add(nanos / 1_000_000.0);
            GL15.glDeleteQueries(query);
        }
        if (QUERIES.size() < 8) {
            activeQuery = GL15.glGenQueries();
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, activeQuery);
        }
    }
    static void endGpu() {
        if (activeQuery != 0) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            QUERIES.add(activeQuery);
            activeQuery = 0;
        }
    }

    static void afterSky(Camera camera, Matrix4f projection, Matrix3f rotation) {
        if (!AUTO || finished || caseIndex < 0 || waitingFinal) return;
        skyFrames++;
        if (caseTicks < 50 || skyFrames < 90 || (reload != null && !reload.isDone())) return;
        try (NativeImage image = screenshot()) {
            Case c = CASES.get(caseIndex);
            image.writeToFile(OUTPUT.resolve(c.name + "-sky.png"));
            record.addProperty("width", image.getWidth());
            record.addProperty("height", image.getHeight());
            record.addProperty("generationAfter", LabSky.generation);
            record.addProperty("skyFrames", skyFrames);
            record.addProperty("skyCenterPixelABGR", Integer.toUnsignedString(image.getPixelRGBA(image.getWidth()/2, image.getHeight()/2)));
            record.add("cameraPosition", array(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z));
            record.add("cameraLook", array(camera.getLookVector().x, camera.getLookVector().y, camera.getLookVector().z));
            record.add("projection", JSON.toJsonTree(projection.get(new float[16])));
            record.add("inverseViewRotation", JSON.toJsonTree(rotation.get(new float[9])));
            List<Double> sorted = GPU_MS.stream().sorted().toList();
            record.addProperty("gpuSampleCount", sorted.size());
            if (!sorted.isEmpty()) {
                record.addProperty("skyGpuMedianMs", sorted.get(sorted.size() / 2));
                record.addProperty("skyGpuP95Ms", sorted.get((int)((sorted.size() - 1) * 0.95)));
            }
            record.addProperty("glErrorAfterSky", GL11.glGetError());
            if (c.mode == 0) checkDirection(image, camera, projection);
            waitingFinal = true;
        } catch (Exception e) { fail(e); }
    }

    private static void checkDirection(NativeImage image, Camera camera, Matrix4f projection) {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            pixels[y * width + x] = image.getPixelRGBA(x, y);
        double maxError = 0;
        JsonArray samples = new JsonArray();
        // Independent reference: Camera's look/up/left basis, not the GLSL matrices.
        // Camera uses +Z forward and +X left at zero yaw; OpenGL eye space uses -Z.
        for (double u : new double[]{0.1, 0.5, 0.9}) for (double v : new double[]{0.1, 0.5, 0.9}) {
            int x = (int)(u * width), y = (int)(v * height);
            float ndcX = 2f * (x + 0.5f) / width - 1;
            float ndcY = 1 - 2f * (y + 0.5f) / height;
            Vector3f ray = new Vector3f(camera.getLookVector())
                .fma(-ndcX / projection.m00(), camera.getLeftVector())
                .fma(ndcY / projection.m11(), camera.getUpVector()).normalize();
            int packed = pixels[y * width + x];
            double[] actual = {(packed & 255) / 255.0, ((packed >> 8) & 255) / 255.0, ((packed >> 16) & 255) / 255.0};
            double[] expected = {ray.x * 0.5 + 0.5, ray.y * 0.5 + 0.5, ray.z * 0.5 + 0.5};
            for (int k = 0; k < 3; k++) maxError = Math.max(maxError, Math.abs(actual[k] - expected[k]));
            JsonObject sample = new JsonObject();
            sample.add("pixel", array(x, y)); sample.add("actual", array(actual)); sample.add("expected", array(expected));
            samples.add(sample);
        }
        record.addProperty("directionMaxError", maxError);
        record.addProperty("directionPass", maxError <= 3.0 / 255);
        record.add("directionSamples", samples);
        String name = CASES.get(caseIndex).name;
        if (name.equals("direction-base")) basePixels = pixels;
        if ((name.equals("direction-translate") || name.equals("direction-reload")) && basePixels != null) {
            long different = 0;
            if (basePixels.length != pixels.length) different = -1;
            else for (int i = 0; i < pixels.length; i++) if (pixels[i] != basePixels[i]) different++;
            record.addProperty("differentPixelsFromBase", different);
            record.addProperty("identicalToBase", different == 0);
        }
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (!AUTO || finished || !waitingFinal || event.phase != TickEvent.Phase.END) return;
        try (NativeImage image = screenshot()) {
            Case c = CASES.get(caseIndex);
            image.writeToFile(OUTPUT.resolve(c.name + "-world.png"));
            record.addProperty("worldCenterPixelABGR", Integer.toUnsignedString(image.getPixelRGBA(image.getWidth()/2, image.getHeight()/2)));
            RESULTS.add(record);
            Files.writeString(OUTPUT.resolve("results.json"), JSON.toJson(RESULTS));
            waitingFinal = false;
            next(Minecraft.getInstance());
        } catch (Exception e) { fail(e); }
    }

    private static NativeImage screenshot() {
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        NativeImage result = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        RenderSystem.bindTexture(texture);
        return result;
    }
    private static JsonArray array(double... values) {
        JsonArray a = new JsonArray(); for (double value : values) a.add(value); return a;
    }
    private static void finish(Minecraft mc) {
        finished = true;
        while (!QUERIES.isEmpty()) GL15.glDeleteQueries(QUERIES.remove());
        LabSky.mode = 2; LabSky.quality = 2; LabSky.frozen = false;
        mc.options.hideGui = false;
        JsonObject summary = new JsonObject();
        summary.addProperty("world", worldName);
        summary.addProperty("minecraft", "1.20.1");
        summary.addProperty("forge", "47.4.22");
        summary.addProperty("gpu", GL11.glGetString(GL11.GL_RENDERER));
        summary.addProperty("glVersion", GL11.glGetString(GL11.GL_VERSION));
        summary.addProperty("glslVersion", GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
        summary.addProperty("casesCompleted", RESULTS.size());
        summary.addProperty("directionCasesPassed", RESULTS.asList().stream()
            .filter(e -> e.getAsJsonObject().has("directionPass") && e.getAsJsonObject().get("directionPass").getAsBoolean()).count());
        try { Files.writeString(OUTPUT.resolve("summary.json"), JSON.toJson(summary)); }
        catch (Exception e) { e.printStackTrace(); }
        System.out.println("[COSMICLAB] VALIDATION COMPLETE: " + JSON.toJson(summary));
    }
    private static void fail(Exception e) {
        finished = true;
        e.printStackTrace();
        try { Files.createDirectories(OUTPUT); Files.writeString(OUTPUT.resolve("failure.txt"), e.toString()); }
        catch (Exception ignored) {}
    }
}
