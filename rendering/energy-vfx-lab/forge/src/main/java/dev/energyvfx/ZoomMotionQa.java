package dev.energyvfx;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

/**
 * Opt-in third-person camera-distance QA. It deliberately changes only a wall
 * in a fresh QA world: the wall shortens Minecraft's normal third-person camera
 * collision distance to roughly 2.5m, 1.5m, and 0.5m after smoke accumulation.
 */
final class ZoomMotionQa {
    static final boolean ENABLED = Boolean.getBoolean("energyvfx.zoomQa");
    private static final int REQUESTED_WIDTH = Integer.getInteger("energyvfx.qa.width", 2560);
    private static final int REQUESTED_HEIGHT = Integer.getInteger("energyvfx.qa.height", 1440);
    private static final Path OUTPUT = Path.of(System.getProperty("energyvfx.qa.output", "validation"))
            .resolve("zoom-back-" + REQUESTED_WIDTH);
    private static final int ACCUMULATION_TICKS = 400;
    private static final int STAGE_TICKS = 60;
    private static final int TOTAL_TICKS = ACCUMULATION_TICKS + STAGE_TICKS * 5;
    private static final String[] STAGES = {"far-4m", "near-2m", "near-1m", "near-0.5m", "far-return"};
    private static final int[] WALL_Z = {-8, -2, -1, 0, -8};
    private static final double[] CAMERA_DISTANCES = {4.0, 2.5, 1.5, 0.75, 4.0};
    private static java.lang.reflect.Method cameraSetter;
    private static final Vec3 ORIGIN = new Vec3(.5, 7, .5);
    private static final int FRAME_CAPACITY = 65536;
    private static final String[] FRAME_HEADERS = {
            "frameId", "elapsedMs", "timelineTick", "stage", "stageTick", "cameraDistanceMeters",
            "cameraX", "cameraY", "cameraZ", "cameraInsideParcel", "cameraNearParcel", "minCameraParcelDistance",
            "packets", "uploadedPackets", "volumeCoverage", "copyCoverage", "nearEyeHidden", "nearEyeFaded",
            "nearViewWeight", "parcelScreenOverlap", "fullScreenParcels", "frameIntervalMs", "cacheActive", "glError",
            "volumeWidth", "volumeHeight", "resolution", "steps"
    };
    private static final double[] FRAME_ROWS = new double[FRAME_CAPACITY * FRAME_HEADERS.length];
    private static boolean started, initialized, originConfirmed, timelineStarted, finishPending;
    private static int menuTicks, setupTicks, originTicks, timelineTicks, currentStage = -1;
    private static int frameRows, drainedFrames;
    private static long began, timelineBegan, previousFrameNanos;
    private static String world, recipePath, recipeSha256, currentLabel = "setup";
    private static Recipe recipe;
    private static CompletableFuture<Void> setup;
    private static int lastWallZ = Integer.MIN_VALUE;
    private static final CameraParcels cameraParcels = new CameraParcels();


    /** QA-only camera origin override; world geometry and player position stay unchanged. */
    static void forceCamera(Camera camera) {
        if (!ENABLED || !timelineStarted || camera == null || Minecraft.getInstance().player == null) return;
        double distance = currentStage < 0 ? 4.0 : CAMERA_DISTANCES[Math.min(currentStage, CAMERA_DISTANCES.length - 1)];
        Vec3 desired = Minecraft.getInstance().player.getEyePosition(1).add(0, 0, -distance);
        try {
            if (cameraSetter == null) {
                cameraSetter = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
                cameraSetter.setAccessible(true);
            }
            cameraSetter.invoke(camera, desired);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot override QA camera position", error);
        }
    }

    static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PerformanceQa.done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (began == 0) began = System.nanoTime();
            if (System.nanoTime() - began > 420_000_000_000L)
                throw new IllegalStateException("Zoom QA exceeded seven minutes");
            if (!started) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || ++menuTicks < 30) return;
                started = true;
                Files.createDirectories(OUTPUT);
                readRecipe();
                mc.options.pauseOnLostFocus = false;
                if (mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), REQUESTED_WIDTH, REQUESTED_HEIGHT);
                world = "ENERGY_ZOOM_QA_" + System.currentTimeMillis();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                                WorldDataConfiguration.DEFAULT), new WorldOptions(43, false, false),
                        r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (!initialized) {
                initialized = true;
                mc.options.hideGui = true;
                mc.options.bobView().set(false);
                mc.options.renderDistance().set(3);
                mc.options.cloudStatus().set(CloudStatus.OFF);
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(260);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.setScreen(null);
                EnergyVfx.Client.owner = mc.level;
                EnergyVfx.Client.active = false;
                AuraEmitter.reset();
                setup = new CompletableFuture<>();
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(() -> {
                    try {
                        var level = server.overworld();
                        level.setDayTime(6000);
                        level.setWeatherParameters(12000, 0, false, false);
                        var player = server.getPlayerList().getPlayer(id);
                        if (player == null) throw new IllegalStateException("QA player missing");
                        player.getAbilities().mayfly = player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        for (EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, ItemStack.EMPTY);
                        player.setDeltaMovement(0, 0, 0);
                        player.teleportTo(ORIGIN.x, ORIGIN.y, ORIGIN.z);
                        setWall(level, WALL_Z[0], Blocks.GRAY_CONCRETE.defaultBlockState());
                        setup.complete(null);
                    } catch (Exception error) { setup.completeExceptionally(error); }
                });
                return;
            }
            if (!setup.isDone()) return;
            setup.join();
            mc.player.getAbilities().flying = true;
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.setYRot(0); mc.player.yRotO = 0;
            mc.player.setXRot(0); mc.player.xRotO = 0;
            mc.player.setYHeadRot(0); mc.player.yHeadRotO = 0;
            mc.player.yBodyRot = mc.player.yBodyRotO = 0;
            if (!originConfirmed) {
                if (setupTicks++ < 40) return;
                if (mc.player.position().distanceTo(ORIGIN) > 1e-6) { originTicks = 0; return; }
                if (++originTicks < 3) return;
                verifyFramebuffer(mc);
                originConfirmed = true;
                EnergyVfx.Client.recipe = recipe;
                EnergyVfx.Client.ticks = 0;
                EnergyVfx.Client.failed = false;
                EnergyVfx.Client.owner = mc.level;
                AuraEmitter.bind(mc.player);
                EnergyVfx.Client.anchor = AuraEmitter.anchor(0);
                EnergyVfx.Client.active = true;
                PerformanceQa.ready = true;
                PerformanceQa.freeze = false;
                PerformanceQa.optimized = true;
                PerformanceQa.recording = false;
                timelineStarted = true;
                timelineBegan = System.nanoTime();
                PerfTrace.resetFrameClock();
            }
            if (finishPending) return;
            if (timelineTicks == TOTAL_TICKS) {
                PerformanceQa.recording = false;
                PerformanceQa.label = "timestamp-drain";
                finishPending = true;
                PerfTrace.resetFrameClock();
                return;
            }
            int nextStage = timelineTicks < ACCUMULATION_TICKS ? -1 : (timelineTicks - ACCUMULATION_TICKS) / STAGE_TICKS;
            int nextStageTick = timelineTicks < ACCUMULATION_TICKS ? timelineTicks : (timelineTicks - ACCUMULATION_TICKS) % STAGE_TICKS;
            if (nextStage != currentStage) {
                currentStage = nextStage;
                currentLabel = currentStage < 0 ? "accumulation" : STAGES[currentStage];
                PerformanceQa.label = currentLabel;
                PerformanceQa.recording = currentStage >= 0;
                PerfTrace.resetFrameClock();
                if (currentStage >= 0) scheduleWall(mc, WALL_Z[currentStage]);
            }
            // Keep the player fixed: this isolates camera-distance cost from movement cost.
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) player.teleportTo(ORIGIN.x, ORIGIN.y, ORIGIN.z);
            });
            ++timelineTicks;
        } catch (Exception error) { fail(error); }
    }

    static void capture(RenderLevelStageEvent event) {
        if (!timelineStarted || PerformanceQa.done || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (EnergyVfx.Client.failed || EnergyVfx.Client.lastGl != 0)
                throw new IllegalStateException("Renderer failed in " + currentLabel);
            long now = System.nanoTime();
            double frameInterval = previousFrameNanos == 0 ? 0 : (now - previousFrameNanos) / 1e6;
            previousFrameNanos = now;
            if (finishPending) {
                PerfTrace.collect();
                if (++drainedFrames < 24) return;
                verifyFramebuffer(mc);
                for (String stage : STAGES) if (PerfTrace.count(stage) == 0)
                    throw new IllegalStateException("No asynchronous GPU frame completed for " + stage);
                PerformanceQa.screenshot(OUTPUT.resolve("after-final.png"));
                write(true);
                PerformanceQa.done = true;
                mc.stop();
                return;
            }
            if (currentStage < 0) return;
            verifyFramebuffer(mc);
            Vec3 camera = event.getCamera().getPosition();
            cameraParcels.measure(camera);
            double distance = camera.distanceTo(mc.player.getEyePosition(event.getPartialTick()));
            PerfTrace.value("cameraDistanceMeters", distance);
            PerfTrace.value("cameraInsideParcel", cameraParcels.inside);
            PerfTrace.value("cameraNearParcel", cameraParcels.near);
            PerfTrace.value("minCameraParcelDistance", cameraParcels.minimum);
            PerfTrace.value("parcelScreenOverlap", PacketBounds.screenOverlap);
            PerfTrace.value("fullScreenParcels", PacketBounds.fullScreenPackets);
            recordFrame(now, frameInterval, camera, distance, timelineTicks - ACCUMULATION_TICKS - currentStage * STAGE_TICKS - 1);
        } catch (Exception error) { fail(error); }
    }

    private static void scheduleWall(Minecraft mc, int z) {
        if (z == lastWallZ) return;
        lastWallZ = z;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            for (int old : WALL_Z) if (old != z) setWall(level, old, Blocks.AIR.defaultBlockState());
            setWall(level, z, Blocks.GRAY_CONCRETE.defaultBlockState());
        });
    }

    private static void setWall(net.minecraft.world.level.Level level, int z, net.minecraft.world.level.block.state.BlockState state) {
        for (int x = -4; x <= 4; x++) for (int y = 4; y <= 12; y++)
            level.setBlockAndUpdate(new BlockPos(x, y, z), state);
    }

    private static void readRecipe() throws Exception {
        String specified = System.getProperty("energyvfx.zoomRecipe", System.getProperty("energyvfx.buildupRecipe"));
        if (specified == null || specified.isBlank()) throw new IllegalArgumentException("energyvfx.zoomRecipe must point to an existing recipe; it is read only");
        Path path = Path.of(specified).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || Files.size(path) > 100_000) throw new IllegalArgumentException("QA recipe is missing or exceeds 100 KB");
        byte[] bytes = Files.readAllBytes(path);
        recipePath = path.toString();
        recipeSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        recipe = Recipe.decode(Recipe.parse(new String(bytes, StandardCharsets.UTF_8)));
        if (recipe.values[39] < .5) throw new IllegalArgumentException("Zoom QA requires an attached body/gear recipe");
        recipe.values[37] = 1;
    }

    private static void verifyFramebuffer(Minecraft mc) {
        var main = mc.getMainRenderTarget();
        if (main.width != REQUESTED_WIDTH || main.height != REQUESTED_HEIGHT)
            throw new IllegalStateException("Unexpected framebuffer: " + main.width + "x" + main.height);
        if (GLFW.glfwGetWindowMonitor(mc.getWindow().getWindow()) != 0)
            throw new IllegalStateException("This QA measures a hidden window, not exclusive fullscreen");
    }

    private static void recordFrame(long now, double frameInterval, Vec3 camera, double distance, int stageTick) {
        if (frameRows >= FRAME_CAPACITY) return;
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        int p = frameRows++ * FRAME_HEADERS.length;
        double[] d = FRAME_ROWS;
        d[p++] = PerfTrace.frameId; d[p++] = (now - timelineBegan) / 1e6; d[p++] = timelineTicks - 1; d[p++] = currentStage;
        d[p++] = stageTick; d[p++] = distance; d[p++] = camera.x; d[p++] = camera.y; d[p++] = camera.z;
        d[p++] = cameraParcels.inside; d[p++] = cameraParcels.near; d[p++] = cameraParcels.minimum;
        d[p++] = AuraEmitter.packetCount; d[p++] = AuraEmitter.uploadCount; d[p++] = OptimizedGpu.lastCoverage; d[p++] = OptimizedGpu.lastCopyCoverage;
        d[p++] = AuraEmitter.nearEyeHidden; d[p++] = AuraEmitter.nearEyeFaded; d[p++] = AuraEmitter.nearViewWeight;
        d[p++] = PacketBounds.screenOverlap; d[p++] = PacketBounds.fullScreenPackets; d[p++] = frameInterval;
        d[p++] = FlowCache.active ? 1 : 0; d[p++] = EnergyVfx.Client.lastGl; d[p++] = OptimizedGpu.width; d[p++] = OptimizedGpu.height;
        d[p++] = recipe.values[37]; d[p] = recipe.values[36];
    }

    private static final class CameraParcels {
        int inside, near; double minimum;
        void measure(Vec3 camera) {
            inside = near = 0; minimum = Double.POSITIVE_INFINITY;
            Vec3 anchor = EnergyVfx.Client.anchor;
            if (anchor == null) { minimum = -1; return; }
            double cx = camera.x - anchor.x, cy = camera.y - anchor.y, cz = camera.z - anchor.z;
            double stretch = recipe.values[1];
            for (int i = 0; i < AuraEmitter.uploadCount; i++) {
                int k = i * 4; double radius = AuraEmitter.uploadData[k + 3]; if (!(radius > 0)) continue;
                double x = cx - AuraEmitter.uploadData[k], y = cy - AuraEmitter.uploadData[k + 1], z = cz - AuraEmitter.uploadData[k + 2];
                double fx = AuraEmitter.uploadFlows[k], fy = AuraEmitter.uploadFlows[k + 1], fz = AuraEmitter.uploadFlows[k + 2];
                double n = Math.sqrt(fx * fx + fy * fy + fz * fz); double along = n > .001 ? (x * fx + y * fy + z * fz) / n : y;
                double dist = Math.sqrt(Math.max(0, x * x + y * y + z * z - along * along + along * along / (stretch * stretch))) / radius;
                if (dist <= 2.505) inside++; if (dist <= 6) near++; minimum = Math.min(minimum, dist);
            }
            if (!Double.isFinite(minimum)) minimum = -1;
        }
    }

    private static void write(boolean complete) throws Exception {
        JsonObject report = PerfTrace.report();
        report.addProperty("completed", complete); report.addProperty("variant", "camera-distance-zoom");
        report.addProperty("world", world); report.addProperty("scope", "Fresh isolated Forge world; hidden 2560x1440 window; third-person-back camera; continuous smoke accumulation; QA-only camera origin distances 4.0/2.5/1.5/0.75/4.0; no existing save");
        report.addProperty("camera", Minecraft.getInstance().options.getCameraType().name()); report.addProperty("timelineTicks", timelineTicks); report.addProperty("expectedTimelineTicks", TOTAL_TICKS);
        report.addProperty("accumulationTicks", ACCUMULATION_TICKS); report.addProperty("stageTicks", STAGE_TICKS); report.addProperty("recipeReadOnlyPath", recipePath); report.addProperty("recipeSha256", recipeSha256);
        report.addProperty("simulationCapacity", AuraEmitter.CAPACITY); report.addProperty("resolution", recipe.values[37]); report.addProperty("steps", recipe.values[36]);
        report.addProperty("cameraDistanceMethod", "QA-only Camera.setPosition override; measured from RenderLevelStageEvent camera to player eye");
        report.addProperty("frameDiagnostics", "frame-diagnostics.csv"); report.addProperty("gpuFrames", "gpu-frames.json"); report.addProperty("diagnosticFrameRows", frameRows); report.addProperty("glError", EnergyVfx.Client.lastGl);
        Files.writeString(OUTPUT.resolve("comparison.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        writeCsv(OUTPUT.resolve("frame-diagnostics.csv"));
        Files.writeString(OUTPUT.resolve("gpu-frames.json"), new GsonBuilder().setPrettyPrinting().create().toJson(PerfTrace.rawFrames()));
    }

    private static void writeCsv(Path path) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(String.join(",", FRAME_HEADERS)); writer.newLine();
            for (int row = 0; row < frameRows; row++) {
                int offset = row * FRAME_HEADERS.length;
                for (int col = 0; col < FRAME_HEADERS.length; col++) { if (col != 0) writer.write(','); writer.write(Double.toString(FRAME_ROWS[offset + col])); }
                writer.newLine();
            }
        }
    }

    private static void fail(Exception error) {
        PerformanceQa.recording = false; PerformanceQa.done = true;
        try { Files.createDirectories(OUTPUT); write(false); Files.writeString(OUTPUT.resolve("failure.txt"), error.toString()); } catch (Exception ignored) { }
        com.mojang.logging.LogUtils.getLogger().error("Zoom QA failed", error);
        Minecraft.getInstance().stop();
    }
}




