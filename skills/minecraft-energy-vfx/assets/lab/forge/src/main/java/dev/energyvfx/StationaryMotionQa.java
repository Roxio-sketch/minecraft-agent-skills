package dev.energyvfx;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
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
 * Continuous accumulation -> startup -> movement -> stop reproduction in a fresh QA world.
 * PerformanceQa routes its tick/capture events here when energyvfx.buildupQa is enabled.
 * The emitter is bound once; neither the clock nor detached smoke is reset between rounds.
 */
final class StationaryMotionQa {
    static final boolean ENABLED = Boolean.getBoolean("energyvfx.buildupQa");
    private static final int REQUESTED_WIDTH = Integer.getInteger("energyvfx.qa.width", 2560);
    private static final int REQUESTED_HEIGHT = Integer.getInteger("energyvfx.qa.height", 1440);
    private static final Path OUTPUT = Path.of(System.getProperty("energyvfx.qa.output", "validation"))
            .resolve("buildup-back-" + REQUESTED_WIDTH);
    private static final String VARIANT = System.getProperty("energyvfx.buildupLabel",
            System.getProperty("energyvfx.buildupVariant", "current"));
    private static final String[] DIRECTIONS = {"forward", "backward", "forward", "backward"};
    private static final String[] STAGES = {"settling", "accumulation-late", "pre-start", "startup", "moving", "stopped"};
    private static final int SETTLE_TICKS = 400, MOVE_TICKS = 80, STOP_TICKS = 40;
    private static final int ROUND_TICKS = SETTLE_TICKS + MOVE_TICKS + STOP_TICKS;
    private static final int TOTAL_TICKS = ROUND_TICKS * DIRECTIONS.length;
    private static final double SPEED_PER_TICK = .215;
    private static final Vec3 ORIGIN = new Vec3(.5, 7, .5);
    private static final int FRAME_CAPACITY = 65536;
    private static final String[] FRAME_HEADERS = {
            "frameId", "elapsedMs", "timelineTick", "round", "tickInRound", "stage", "emitterTick", "partialTick",
            "playerX", "playerY", "playerZ", "cameraX", "cameraY", "cameraZ", "packets", "uploadedPackets",
            "sources", "volumeCoverage", "copyCoverage", "nearEyeHidden", "nearEyeFaded", "nearViewThinned",
            "nearViewWeight", "cameraPlayerDistance", "cameraInsideParcel", "cameraNearParcel", "minCameraParcelDistance",
            "frameIntervalMs", "renderedPose", "walkSpeed", "cacheActive", "glError", "framebufferWidth", "framebufferHeight",
            "volumeWidth", "volumeHeight", "maxUploadedRadius", "packetBoundsEnabled", "commandedZ", "resolution",
            "parcelScreenOverlap", "fullScreenParcels"
    };
    private static final String[] TICK_HEADERS = {
            "timelineTick", "elapsedMs", "round", "tickInRound", "stage", "emitterTick", "playerX", "playerY", "playerZ",
            "commandedZ", "clientStepMeters", "tickIntervalMs", "packets", "uploadedPackets", "sources", "renderedPose",
            "walkSpeed", "active", "frozen"
    };
    // Primitive preallocated buffers keep file I/O and per-frame JSON allocation out of measurement.
    private static final double[] FRAME_ROWS = new double[FRAME_CAPACITY * FRAME_HEADERS.length];
    private static final double[] TICK_ROWS = new double[DIRECTIONS.length * 180 * TICK_HEADERS.length];
    private static final JsonArray rounds = new JsonArray();
    private static final CameraParcels cameraParcels = new CameraParcels();
    private static boolean started, initialized, originConfirmed, timelineStarted, finishPending;
    private static int menuTicks, setupTicks, originTicks, timelineTicks, currentRound, currentTick, currentStage;
    private static int frameRows, tickRows, droppedFrameRows, drainedFrames, measuredFrames;
    private static long began, timelineBegan, previousTickNanos, previousFrameNanos;
    private static String world, recipePath, recipeSha256, currentLabel = "setup";
    private static Recipe recipe;
    private static float originalResolution;
    private static CompletableFuture<Void> setup;
    private static Vec3 previousPlayer, roundStart;
    private static double commandedZ = ORIGIN.z, roundTravel, totalTravel;
    private static int firstMovementObservedTick = -1;

    static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PerformanceQa.done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (began == 0) began = System.nanoTime();
            if (System.nanoTime() - began > 420_000_000_000L)
                throw new IllegalStateException("Accumulation/movement QA exceeded seven minutes");
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
                world = "ENERGY_BUILDUP_QA_" + System.currentTimeMillis();
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
                        setup.complete(null);
                    } catch (Exception error) { setup.completeExceptionally(error); }
                });
                return;
            }
            if (!setup.isDone()) return;
            setup.join();
            mc.player.getAbilities().flying = true;
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.setYRot(0);
            mc.player.yRotO = 0;
            mc.player.setXRot(0);
            mc.player.xRotO = 0;
            mc.player.setYHeadRot(0);
            mc.player.yHeadRotO = 0;
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
                // Single continuous emitter bind after the client confirms the starting origin.
                AuraEmitter.bind(mc.player);
                EnergyVfx.Client.anchor = AuraEmitter.anchor(0);
                EnergyVfx.Client.active = true;
                PerformanceQa.ready = true;
                PerformanceQa.freeze = false;
                PerformanceQa.optimized = true;
                PerformanceQa.recording = false;
                timelineStarted = true;
                timelineBegan = System.nanoTime();
                previousPlayer = roundStart = mc.player.position();
                PerfTrace.resetFrameClock();
            }
            if (finishPending) return;
            // Finalize on the client clock, not on a particular render frame: slow rendering may
            // process several client ticks in one frame and never render tick 519 itself.
            if (timelineTicks > 0 && timelineTicks % ROUND_TICKS == 0 && rounds.size() < timelineTicks / ROUND_TICKS)
                completeRound(mc);
            if (timelineTicks == TOTAL_TICKS) {
                PerformanceQa.recording = false;
                PerformanceQa.label = "timestamp-drain";
                finishPending = true;
                PerfTrace.resetFrameClock();
                return;
            }
            currentRound = timelineTicks / ROUND_TICKS;
            currentTick = timelineTicks % ROUND_TICKS;
            currentStage = stage(currentTick);
            String nextLabel = label(currentRound, currentStage);
            if (!nextLabel.equals(currentLabel)) {
                currentLabel = nextLabel;
                PerformanceQa.label = currentLabel;
                PerformanceQa.recording = currentStage > 0;
                PerfTrace.resetFrameClock();
            }
            long now = System.nanoTime();
            Vec3 position = mc.player.position();
            double step = previousPlayer == null ? 0 : position.distanceTo(previousPlayer);
            roundTravel += step;
            totalTravel += step;
            previousPlayer = position;
            if (currentTick >= SETTLE_TICKS && firstMovementObservedTick < 0 && position.distanceTo(roundStart) > .01)
                firstMovementObservedTick = currentTick;
            if (currentTick >= SETTLE_TICKS && currentTick < SETTLE_TICKS + MOVE_TICKS)
                commandedZ += (currentRound % 2 == 0 ? 1 : -1) * SPEED_PER_TICK;
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            double z = commandedZ;
            // No round-boundary teleport: each movement command is a continuous walking-sized step.
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) player.teleportTo(ORIGIN.x, ORIGIN.y, z);
            });
            if (currentStage > 0) recordTick(now, position, step);
            previousTickNanos = now;
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
                // Drain available asynchronous timestamps, never block waiting for an unavailable query.
                PerfTrace.collect();
                if (++drainedFrames < 24) return;
                verifyFramebuffer(mc);
                for (int round = 0; round < DIRECTIONS.length; round++)
                    for (int stage = 1; stage < STAGES.length; stage++)
                        if (PerfTrace.count(label(round, stage)) == 0)
                            throw new IllegalStateException("No asynchronous GPU frame completed for " + label(round, stage));
                PerformanceQa.screenshot(OUTPUT.resolve("after-final-stop.png"));
                write(true);
                PerformanceQa.done = true;
                mc.stop();
                return;
            }
            if (currentStage > 0) {
                verifyFramebuffer(mc);
                Vec3 camera = event.getCamera().getPosition();
                cameraParcels.measure(camera);
                double cameraDistance = camera.distanceTo(mc.player.getEyePosition(event.getPartialTick()));
                PerfTrace.value("simulatedPackets", AuraEmitter.packetCount);
                PerfTrace.value("uploadedPackets", AuraEmitter.uploadCount);
                PerfTrace.value("volumeCoverage", OptimizedGpu.lastCoverage);
                PerfTrace.value("copyCoverage", OptimizedGpu.lastCopyCoverage);
                PerfTrace.value("cameraPlayerDistance", cameraDistance);
                PerfTrace.value("cameraInsideParcel", cameraParcels.inside);
                PerfTrace.value("cameraNearParcel", cameraParcels.near);
                PerfTrace.value("minCameraParcelDistance", cameraParcels.minimum);
                PerfTrace.value("nearViewWeight", AuraEmitter.nearViewWeight);
                PerfTrace.value("nearViewThinned", AuraEmitter.nearViewThinned);
                PerfTrace.value("renderedPose", AuraEmitter.usingRenderedPose ? 1 : 0);
                PerfTrace.value("parcelScreenOverlap", PacketBounds.screenOverlap);
                PerfTrace.value("fullScreenParcels", PacketBounds.fullScreenPackets);
                recordFrame(now, frameInterval, event, camera, cameraDistance);
                ++measuredFrames;
            }
        } catch (Exception error) { fail(error); }
    }

    private static void completeRound(Minecraft mc) {
        Vec3 actual = mc.player.position();
        if (actual.distanceTo(new Vec3(ORIGIN.x, ORIGIN.y, commandedZ)) > .05 || roundTravel < 16)
            throw new IllegalStateException("Continuous client movement did not reach the expected target in round " + (currentRound + 1));
        JsonObject row = new JsonObject();
        row.addProperty("round", currentRound + 1);
        row.addProperty("direction", DIRECTIONS[currentRound]);
        row.addProperty("startZ", roundStart.z);
        row.addProperty("endZ", actual.z);
        row.addProperty("commandedEndZ", commandedZ);
        row.addProperty("travelledMeters", roundTravel);
        row.addProperty("firstMovementObservedTick", firstMovementObservedTick);
        row.addProperty("stationaryTicks", SETTLE_TICKS);
        row.addProperty("movementTicks", MOVE_TICKS);
        row.addProperty("stopTicks", STOP_TICKS);
        row.addProperty("clockAtEnd", EnergyVfx.Client.ticks);
        row.addProperty("packetsAtEnd", AuraEmitter.packetCount);
        row.addProperty("emitterResetBetweenRounds", false);
        rounds.add(row);
        roundTravel = 0;
        roundStart = actual;
        firstMovementObservedTick = -1;
    }

    private static int stage(int tick) {
        if (tick < 340) return 0;
        if (tick < 380) return 1;
        if (tick < 400) return 2;
        if (tick < 420) return 3;
        if (tick < 480) return 4;
        return 5;
    }

    private static String label(int round, int stage) {
        return DIRECTIONS[round] + "-" + (round / 2 + 1) + "-" + STAGES[stage];
    }

    private static void readRecipe() throws Exception {
        String specified = System.getProperty("energyvfx.buildupRecipe");
        if (specified == null || specified.isBlank())
            throw new IllegalArgumentException("energyvfx.buildupRecipe must point to an existing recipe; it is read only");
        Path path = Path.of(specified).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || Files.size(path) > 100_000)
            throw new IllegalArgumentException("QA recipe is missing or exceeds 100 KB");
        byte[] bytes = Files.readAllBytes(path);
        recipePath = path.toString();
        recipeSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        recipe = Recipe.decode(Recipe.parse(new String(bytes, StandardCharsets.UTF_8)));
        if (recipe.values[39] < .5) throw new IllegalArgumentException("Accumulation QA requires an attached body/gear recipe");
        originalResolution = recipe.values[37];
        recipe.values[37] = 1; // QA-only full-size volume; the source file is never changed.
    }

    private static void verifyFramebuffer(Minecraft mc) {
        var main = mc.getMainRenderTarget();
        if (main.width != REQUESTED_WIDTH || main.height != REQUESTED_HEIGHT)
            throw new IllegalStateException("Unexpected framebuffer: " + main.width + "x" + main.height);
        if (GLFW.glfwGetWindowMonitor(mc.getWindow().getWindow()) != 0)
            throw new IllegalStateException("This QA measures a hidden window, not exclusive fullscreen");
        if (EnergyVfx.Client.active && OptimizedGpu.width != 0 &&
                (OptimizedGpu.width != main.width || OptimizedGpu.height != main.height))
            throw new IllegalStateException("Full-resolution volume was not observed");
    }

    private static void recordTick(long now, Vec3 position, double step) {
        int p = tickRows++ * TICK_HEADERS.length;
        double[] d = TICK_ROWS;
        d[p++] = timelineTicks; d[p++] = (now - timelineBegan) / 1e6;
        d[p++] = currentRound + 1; d[p++] = currentTick; d[p++] = currentStage;
        d[p++] = EnergyVfx.Client.ticks; d[p++] = position.x; d[p++] = position.y; d[p++] = position.z;
        d[p++] = commandedZ; d[p++] = step; d[p++] = previousTickNanos == 0 ? 0 : (now - previousTickNanos) / 1e6;
        d[p++] = AuraEmitter.packetCount; d[p++] = AuraEmitter.uploadCount; d[p++] = AuraEmitter.sourceCount;
        d[p++] = AuraEmitter.usingRenderedPose ? 1 : 0; d[p++] = Minecraft.getInstance().player.walkAnimation.speed(1);
        d[p++] = EnergyVfx.Client.active ? 1 : 0; d[p] = PerformanceQa.freeze ? 1 : 0;
    }

    private static void recordFrame(long now, double frameInterval, RenderLevelStageEvent event, Vec3 camera, double cameraDistance) {
        if (frameRows == FRAME_CAPACITY) { ++droppedFrameRows; return; }
        Minecraft mc = Minecraft.getInstance();
        Vec3 player = mc.player.position();
        var main = mc.getMainRenderTarget();
        int p = frameRows++ * FRAME_HEADERS.length;
        double[] d = FRAME_ROWS;
        d[p++] = PerfTrace.frameId; d[p++] = (now - timelineBegan) / 1e6; d[p++] = timelineTicks - 1;
        d[p++] = currentRound + 1; d[p++] = currentTick; d[p++] = currentStage; d[p++] = EnergyVfx.Client.ticks;
        d[p++] = event.getPartialTick(); d[p++] = player.x; d[p++] = player.y; d[p++] = player.z;
        d[p++] = camera.x; d[p++] = camera.y; d[p++] = camera.z;
        d[p++] = AuraEmitter.packetCount; d[p++] = AuraEmitter.uploadCount; d[p++] = AuraEmitter.sourceCount;
        d[p++] = OptimizedGpu.lastCoverage; d[p++] = OptimizedGpu.lastCopyCoverage;
        d[p++] = AuraEmitter.nearEyeHidden; d[p++] = AuraEmitter.nearEyeFaded; d[p++] = AuraEmitter.nearViewThinned;
        d[p++] = AuraEmitter.nearViewWeight; d[p++] = cameraDistance; d[p++] = cameraParcels.inside; d[p++] = cameraParcels.near;
        d[p++] = cameraParcels.minimum; d[p++] = frameInterval; d[p++] = AuraEmitter.usingRenderedPose ? 1 : 0;
        d[p++] = mc.player.walkAnimation.speed(1); d[p++] = FlowCache.active ? 1 : 0; d[p++] = EnergyVfx.Client.lastGl;
        d[p++] = main.width; d[p++] = main.height; d[p++] = OptimizedGpu.width; d[p++] = OptimizedGpu.height;
        d[p++] = cameraParcels.maximumRadius; d[p++] = PacketBounds.enabled() ? 1 : 0;
        d[p++] = commandedZ; d[p++] = recipe.values[37];
        d[p++] = PacketBounds.screenOverlap; d[p] = PacketBounds.fullScreenPackets;
    }

    /** Same flow-aligned ellipsoid metric as the packet support test in the volume shader. */
    private static final class CameraParcels {
        int inside, near;
        double minimum, maximumRadius;
        void measure(Vec3 camera) {
            inside = near = 0;
            minimum = Double.POSITIVE_INFINITY;
            maximumRadius = 0;
            Vec3 anchor = EnergyVfx.Client.anchor;
            if (anchor == null) { minimum = -1; return; }
            double cx = camera.x - anchor.x, cy = camera.y - anchor.y, cz = camera.z - anchor.z;
            double stretch = recipe.values[1];
            for (int i = 0; i < AuraEmitter.uploadCount; i++) {
                int k = i * 4;
                double radius = AuraEmitter.uploadData[k + 3];
                if (!(radius > 0)) continue;
                double x = cx - AuraEmitter.uploadData[k], y = cy - AuraEmitter.uploadData[k + 1], z = cz - AuraEmitter.uploadData[k + 2];
                double fx = AuraEmitter.uploadFlows[k], fy = AuraEmitter.uploadFlows[k + 1], fz = AuraEmitter.uploadFlows[k + 2];
                double norm = Math.sqrt(fx * fx + fy * fy + fz * fz);
                double along = norm > .001 ? (x * fx + y * fy + z * fz) / norm : y;
                double distance = Math.sqrt(Math.max(0, x * x + y * y + z * z - along * along + along * along / (stretch * stretch))) / radius;
                if (distance <= 2.505) ++inside;
                if (distance <= 6) ++near;
                minimum = Math.min(minimum, distance);
                maximumRadius = Math.max(maximumRadius, radius);
            }
            if (!Double.isFinite(minimum)) minimum = -1;
        }
    }

    private static void write(boolean complete) throws Exception {
        JsonObject report = PerfTrace.report();
        report.addProperty("completed", complete);
        report.addProperty("variant", VARIANT);
        report.addProperty("directParcelsDisabled", Boolean.getBoolean("energyvfx.disableDirectParcels"));
        report.addProperty("parcelNearEyeEnabled", !Boolean.getBoolean("energyvfx.disableParcelNearEye"));
        report.addProperty("parcelNearEyeScope", "Local player emitter only; actual per-parcel flow-ellipsoid camera distance; normal third-person body fade 8-10 radii, gear fade 6-8; smoothly returns to prior close-view body 4-6 and gear 2.8-4; simulation, resolution and integration steps unchanged");
        report.addProperty("world", world);
        report.addProperty("scope", "Fresh isolated dev world; hidden window at requested framebuffer size, not exclusive fullscreen; one client-confirmed origin and one continuous emitter bind; actual continuous server/client movement with unfrozen time; no existing save is opened or changed");
        report.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER));
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        report.addProperty("width", main.width);
        report.addProperty("height", main.height);
        report.addProperty("requestedWidth", REQUESTED_WIDTH);
        report.addProperty("requestedHeight", REQUESTED_HEIGHT);
        report.addProperty("fullscreen", GLFW.glfwGetWindowMonitor(mc.getWindow().getWindow()) != 0);
        report.addProperty("windowVisible", GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE);
        report.addProperty("camera", mc.options.getCameraType().name());
        report.addProperty("originConfirmed", originConfirmed);
        report.addProperty("emitterBindCount", timelineStarted ? 1 : 0);
        report.addProperty("emitterResetBetweenRounds", false);
        report.addProperty("frozen", PerformanceQa.freeze);
        report.addProperty("timelineTicks", timelineTicks);
        report.addProperty("expectedTimelineTicks", TOTAL_TICKS);
        report.addProperty("emitterTicks", EnergyVfx.Client.ticks);
        report.addProperty("travelledMeters", totalTravel);
        report.addProperty("movementMetersPerTick", SPEED_PER_TICK);
        report.addProperty("stationaryTicksPerRound", SETTLE_TICKS);
        report.addProperty("movementTicksPerRound", MOVE_TICKS);
        report.addProperty("stoppedTicksPerRound", STOP_TICKS);
        report.addProperty("measurementBins", "340-379 accumulation-late; 380-399 pre-start; 400-419 startup; 420-479 moving; 480-519 stopped; all ranges inclusive");
        report.addProperty("frameDiagnostics", "frame-diagnostics.csv");
        report.addProperty("tickDiagnostics", "tick-diagnostics.csv");
        report.addProperty("gpuFrames", "gpu-frames.json");
        report.addProperty("stageColumn", "1=accumulation-late,2=pre-start,3=startup,4=moving,5=stopped");
        report.addProperty("diagnosticFrameRows", frameRows);
        report.addProperty("diagnosticTickRows", tickRows);
        report.addProperty("droppedDiagnosticFrames", droppedFrameRows);
        report.addProperty("diagnosticTiming", "CPU wall intervals recorded per frame/tick in primitive RAM buffers; CSV, GPU rows and final screenshot written after measurement; GPU rows contain only completed asynchronous timestamps and join frame-diagnostics.csv by frameId");
        report.addProperty("cameraInsideParcelMetric", "Uploaded support ellipsoid contains the camera when flow-aligned, stretch-normalized distance/radius <= 2.505; cameraNearParcel uses <= 6; -1 minimum indicates no uploaded parcel");
        report.addProperty("recipeReadOnlyPath", recipePath);
        report.addProperty("recipeSha256", recipeSha256);
        report.addProperty("recipeName", recipe == null ? "" : recipe.name);
        report.addProperty("originalResolution", originalResolution);
        report.addProperty("resolution", recipe == null ? 0 : recipe.values[37]);
        report.addProperty("steps", recipe == null ? 0 : recipe.values[36]);
        report.addProperty("simulationCapacity", AuraEmitter.CAPACITY);
        report.addProperty("emissionRate", recipe == null ? 0 : recipe.values[41]);
        report.addProperty("packetLifetimeSeconds", recipe == null ? 0 : recipe.values[42]);
        report.addProperty("equipment", "None; all six slots explicitly empty in the newly created QA world");
        report.addProperty("glError", EnergyVfx.Client.lastGl);
        report.add("movementRounds", rounds);
        Files.writeString(OUTPUT.resolve("comparison.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        writeCsv(OUTPUT.resolve("frame-diagnostics.csv"), FRAME_HEADERS, FRAME_ROWS, frameRows);
        writeCsv(OUTPUT.resolve("tick-diagnostics.csv"), TICK_HEADERS, TICK_ROWS, tickRows);
        Files.writeString(OUTPUT.resolve("gpu-frames.json"), new GsonBuilder().setPrettyPrinting().create().toJson(PerfTrace.rawFrames()));
    }

    private static void writeCsv(Path path, String[] headers, double[] data, int count) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(String.join(",", headers));
            writer.newLine();
            for (int row = 0; row < count; row++) {
                int offset = row * headers.length;
                for (int col = 0; col < headers.length; col++) {
                    if (col != 0) writer.write(',');
                    writer.write(Double.toString(data[offset + col]));
                }
                writer.newLine();
            }
        }
    }

    private static void fail(Exception error) {
        PerformanceQa.recording = false;
        PerformanceQa.done = true;
        try {
            Files.createDirectories(OUTPUT);
            write(false);
            Files.writeString(OUTPUT.resolve("failure.txt"), error.toString());
        } catch (Exception ignored) { }
        com.mojang.logging.LogUtils.getLogger().error("Accumulation/movement QA failed", error);
        Minecraft.getInstance().stop();
    }
}
