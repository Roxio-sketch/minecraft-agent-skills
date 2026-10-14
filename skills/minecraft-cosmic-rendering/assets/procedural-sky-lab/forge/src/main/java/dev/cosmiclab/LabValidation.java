package dev.cosmiclab;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.ShaderInstance;
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
    private static final boolean AUTO = Boolean.getBoolean("cosmiclab.autorun") && System.getProperty("cosmiclab.advancedCases", "").isEmpty();
    private static final Path OUTPUT = Path.of(System.getProperty("cosmiclab.output", "validation"));
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonArray RESULTS = new JsonArray();
    private static final ArrayDeque<Integer> QUERIES = new ArrayDeque<>();
    private static final List<Double> GPU_MS = new ArrayList<>();
    private static int activeQuery;
    private static int drawnProgram, boundProgramAtDraw;
    private static boolean started, configured, finished, waitingFinal;
    private static int titleTicks, caseIndex = -1, caseTicks, skyFrames;
    private static JsonObject record;
    private static int[] basePixels;
    private static int[] richBasePixels;
    private static final Map<String, int[]> ANIMATION_BASES = new HashMap<>();
    private static final Map<String, int[]> FEATURE_BASES = new HashMap<>();
    private static CompletableFuture<Void> reload;
    private static String worldName;
    private static final List<Case> ALL_CASES = List.of(
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
        new Case("terrain-occlusion", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
        new Case("nebula-animation-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("nebula-animation-time4", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("aurora-animation-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("aurora-animation-time12", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("meteor-animation-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("meteor-animation-time4", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("rich-sky-translate", 2, 2, 0, -25, 70, 960, 540, 512, 150, 512),
        new Case("twinkle-animation-time0", 1, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("twinkle-animation-time2", 1, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("celestial-off", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("celestial-animation-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("celestial-animation-time8", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("celestial-translate", 2, 2, 0, -25, 70, 960, 540, 512, 150, 512),
        new Case("celestial-rock", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("celestial-star", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("blackhole-off", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("blackhole-animation-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("blackhole-animation-time2", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("blackhole-lensing-zero", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("blackhole-lensing-one", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
        new Case("blackhole-translate", 2, 2, 0, -25, 70, 960, 540, 512, 150, 512),
        new Case("stars-horizon-low", 1, 1, 0, 0, 70, 960, 540, 0, 100, 0),
        new Case("stars-horizon-medium", 1, 2, 0, 0, 70, 960, 540, 0, 100, 0),
        new Case("stars-horizon-high", 1, 3, 0, 0, 70, 960, 540, 0, 100, 0)
    );
    private static final List<Case> CASES = selectedCases();
    private record Case(String name, int mode, int quality, float yaw, float pitch,
                        int fov, int width, int height, double x, double y, double z) {}

    /** Select a complete comparison pair without rerunning unrelated GPU captures. */
    private static List<Case> selectedCases() {
        String names = System.getProperty("cosmiclab.cases", "").trim();
        if (names.isEmpty()) return ALL_CASES;
        Set<String> requested = new HashSet<>(Arrays.asList(names.split(",")));
        // Opt-in ring regressions leave the existing 39-case baseline protocol intact.
        List<Case> available = new ArrayList<>(ALL_CASES);
        available.addAll(List.of(
            new Case("ring-offscreen-off", 2, 2, -68, -2, 70, 1072, 602, 0, 100, 0),
            new Case("ring-offscreen-on", 2, 2, -68, -2, 70, 1072, 602, 0, 100, 0),
            new Case("ring-visible-off", 2, 2, 24, -30, 70, 1072, 602, 0, 100, 0),
            new Case("ring-visible-on", 2, 2, 24, -30, 70, 1072, 602, 0, 100, 0)));
        available.addAll(List.of(
            new Case("atmo-day", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-sunset", 2, 2, 0, -5, 70, 960, 540, 0, 100, 0),
            new Case("atmo-twilight", 2, 2, 0, -5, 70, 960, 540, 0, 100, 0),
            new Case("atmo-night", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-moon-new", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-moon-full", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-moon-quarter", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-cloud-off", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-cloud-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-cloud-time4", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-cloud-low", 2, 1, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-cloud-high", 2, 3, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-cycle-time0", 2, 2, 0, -5, 70, 960, 540, 0, 100, 0),
            new Case("atmo-cycle-time30", 2, 2, 0, -5, 70, 960, 540, 0, 100, 0),
            new Case("atmo-translate", 2, 2, 0, -25, 70, 960, 540, 512, 150, 512),
            new Case("atmo-yaw90", 2, 2, 90, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-fov40", 2, 2, 0, -25, 40, 960, 540, 0, 100, 0),
            new Case("atmo-fov100", 2, 2, 0, -25, 100, 960, 540, 0, 100, 0),
            new Case("atmo-reload", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-wall", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("atmo-disabled-base", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("atmo-disabled-extreme", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0)));
        available.addAll(List.of(
            new Case("full-off", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-cloud-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-cloud-time8", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-cloud-translate", 2, 2, 0, -25, 70, 960, 540, 300, 100, 200),
            new Case("full-cloud-inside", 2, 2, 0, -10, 70, 960, 540, 0, 185, 0),
            new Case("full-cloud-above", 2, 2, 0, 25, 70, 960, 540, 0, 260, 0),
            new Case("full-cloud-low", 2, 1, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-cloud-high", 2, 3, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-shadow-off", 2, 2, 0, 45, 70, 960, 540, 0, 22, 0),
            new Case("full-shadow-on", 2, 2, 0, 45, 70, 960, 540, 0, 22, 0),
            new Case("full-shafts-off", 2, 2, 0, -5, 70, 960, 540, 0, 40, 0),
            new Case("full-shafts-on", 2, 2, 0, -5, 70, 960, 540, 0, 40, 0),
            new Case("full-fog-off", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("full-fog-on", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("full-rain-off", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("full-rain-time0", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("full-rain-time2", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("full-rain-world", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("full-blackhole2d-off", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-blackhole2d-time0", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-blackhole2d-time4", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-rainbow-off", 2, 2, 180, -12, 90, 960, 540, 0, 100, 0),
            new Case("full-rainbow", 2, 2, 180, -12, 90, 960, 540, 0, 100, 0),
            new Case("full-halo-off", 2, 2, 0, -25, 90, 960, 540, 0, 100, 0),
            new Case("full-halo", 2, 2, 0, -25, 90, 960, 540, 0, 100, 0),
            new Case("full-special-off", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-special", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0),
            new Case("full-occlusion", 2, 2, 0, 0, 70, 960, 540, 0, 8, 0),
            new Case("full-resize", 2, 2, 0, -25, 70, 800, 600, 0, 100, 0),
            new Case("full-fov100", 2, 2, 0, -25, 100, 960, 540, 0, 100, 0),
            new Case("full-reload", 2, 2, 0, -25, 70, 960, 540, 0, 100, 0)));
        List<Case> selected = available.stream().filter(c -> requested.contains(c.name)).toList();
        if (selected.size() != requested.size())
            throw new IllegalArgumentException("Unknown cosmiclab.cases selection: " + names);
        return selected;
    }

    /** Mouse input must not change the camera while testing only time or translation. */
    private static void holdValidationView(Minecraft mc, Case c) {
        mc.mouseHandler.releaseMouse();
        mc.player.setYRot(c.yaw); mc.player.setXRot(c.pitch);
        mc.player.yRotO = c.yaw; mc.player.xRotO = c.pitch;
    }

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
            if (reload != null) {
                if (!reload.isDone()) return;
                reload.join();
            }
            if (caseIndex >= 0) holdValidationView(mc, CASES.get(caseIndex));
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
        LabSky.recipe = LabSky.defaults.with(Map.of("quality", c.quality));
        LabSky.timeOverride = 0f;
        reload = null;
        configureRecipe(c);
        mc.options.fov().set(c.fov);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), c.width, c.height);
        mc.player.getAbilities().flying = true;
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setPos(c.x, c.y, c.z);
        mc.player.setYRot(c.yaw); mc.player.setXRot(c.pitch);
        mc.player.yRotO = c.yaw; mc.player.xRotO = c.pitch;
        holdValidationView(mc, c);
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
        record.addProperty("requestedTime", LabSky.timeOverride);
        record.add("recipe", LabSky.recipe.toJson());
        if (c.name.equals("direction-reload") || c.name.equals("atmo-reload") || c.name.equals("full-reload")) reload = mc.reloadResourcePacks();
    }

    private static Map<String, Object> isolated() {
        Map<String, Object> overrides = new HashMap<>();
        for (String key : new String[]{"starDensity", "starBrightness", "galaxyStrength", "horizonGlow",
                "nebulaStrength", "auroraStrength", "meteorRate", "celestialStrength", "ringStrength", "blackHoleStrength"})
            overrides.put(key, 0);
        return overrides;
    }

    private static void configureRecipe(Case c) {
        if (c.name.startsWith("full-")) { configureFull(c); return; }
        if (c.name.startsWith("atmo-")) {
            configureAtmosphere(c);
            return;
        }
        if (c.name.startsWith("ring-")) {
            // Screenshot-matching storm preset; both paired captures change only ringStrength.
            Map<String, Object> ring = new HashMap<>();
            ring.put("seed", 715); ring.put("exposure", 3); ring.put("horizonGlow", 1);
            ring.put("starDensity", 0.25); ring.put("galaxyStrength", 0.8); ring.put("auroraStrength", 0.25);
            ring.put("meteorRate", 8); ring.put("meteorBrightness", 3.6);
            ring.put("meteorSpeed", 1.2); ring.put("meteorLength", 0.26);
            ring.put("celestialSize", 9); ring.put("celestialYaw", -24); ring.put("celestialPitch", 30);
            ring.put("ringStrength", c.name.endsWith("on") ? 0.75 : 0);
            LabSky.recipe = LabSky.recipe.with(ring); LabSky.timeOverride = 2f;
            return;
        }
        boolean animation = c.name.contains("-animation-");
        boolean celestial = c.name.startsWith("celestial-");
        boolean blackhole = c.name.startsWith("blackhole-");
        if (!animation && !celestial && !blackhole) return;
        Map<String, Object> overrides = isolated();
        if (c.name.startsWith("nebula-")) {
            overrides.put("nebulaStrength", LabSky.defaults.value("nebulaStrength"));
            overrides.put("nebulaSpeed", LabSky.defaults.value("nebulaSpeed"));
        } else if (c.name.startsWith("aurora-")) {
            overrides.put("auroraStrength", 2); overrides.put("auroraSpeed", 0.55);
        } else if (c.name.startsWith("meteor-")) {
            overrides.put("meteorRate", 8);
        } else if (c.name.startsWith("twinkle-")) {
            overrides.put("starDensity", LabSky.defaults.value("starDensity"));
            overrides.put("starBrightness", LabSky.defaults.value("starBrightness"));
        } else if (celestial) {
            overrides.put("celestialStrength", c.name.equals("celestial-off") ? 0 : LabSky.defaults.value("celestialStrength"));
            overrides.put("celestialYaw", 0); overrides.put("celestialPitch", 25);
            overrides.put("ringStrength", LabSky.defaults.value("ringStrength"));
            if (c.name.equals("celestial-rock")) overrides.put("celestialType", 2);
            if (c.name.equals("celestial-star")) overrides.put("celestialType", 3);
        } else if (blackhole) {
            overrides.put("blackHoleStrength", c.name.equals("blackhole-off") ? 0 : 2);
            overrides.put("blackHoleYaw", 0); overrides.put("blackHolePitch", 25);
            if (c.name.startsWith("blackhole-lensing-")) {
                overrides.put("starDensity", LabSky.defaults.value("starDensity"));
                overrides.put("starBrightness", LabSky.defaults.value("starBrightness"));
                overrides.put("galaxyStrength", LabSky.defaults.value("galaxyStrength"));
                overrides.put("nebulaStrength", LabSky.defaults.value("nebulaStrength"));
                // Black disk colors remove disk/ring emission, isolating background ray bending.
                overrides.put("diskColorA", "#000000"); overrides.put("diskColorB", "#000000");
                overrides.put("lensingStrength", c.name.endsWith("zero") ? 0 : 1);
            }
        }
        if (animation) LabSky.timeOverride = Float.parseFloat(c.name.substring(c.name.lastIndexOf("time") + 4));
        LabSky.recipe = LabSky.recipe.with(overrides);
    }

    private static void configureFull(Case c) {
        Map<String, Object> p = isolated();
        p.put("atmosphereStrength", 1); p.put("sunYaw", 0); p.put("sunElevation", 25);
        p.put("sunBrightness", 1.5); p.put("moonBrightness", 0); p.put("cycleSpeed", 0);
        p.put("thinCloudStrength", 0); p.put("volumeCloudStrength", 0); p.put("fogStrength", 0);
        p.put("cloudShadowStrength", 0); p.put("lightShaftStrength", 0); p.put("rainAmount", 0);
        p.put("thunderAmount", 0); p.put("weatherFollowWorld", 0);
        p.put("volumeCloudBase", 150); p.put("volumeCloudThickness", 70); p.put("volumeCloudCoverage", .72);
        p.put("volumeCloudDensity", 1.2); p.put("volumeCloudSpeed", 1.2); p.put("volumeCloudEvolution", .5);
        p.put("volumeCloudScale", .005); p.put("volumeCloudDistance", 1100);
        p.put("fogBaseHeight", 64); p.put("fogDensity", .008); p.put("fogDistance", 512);
        if (c.name.startsWith("full-cloud-") || c.name.equals("full-resize") || c.name.equals("full-fov100") || c.name.equals("full-reload"))
            p.put("volumeCloudStrength", 1);
        if (c.name.equals("full-cloud-time8")) LabSky.timeOverride = 8;
        if (c.name.startsWith("full-shadow-")) {
            p.put("volumeCloudStrength", 1); p.put("sunElevation", 60);
            p.put("cloudShadowStrength", c.name.endsWith("on") ? 1 : 0);
        }
        if (c.name.startsWith("full-shafts-")) {
            p.put("volumeCloudStrength", 1); p.put("sunElevation", 10);
            p.put("lightShaftStrength", c.name.endsWith("on") ? 3 : 0); p.put("lightShaftDensity", .5);
        }
        if (c.name.startsWith("full-fog-")) p.put("fogStrength", c.name.endsWith("on") ? 1.5 : 0);
        if (c.name.startsWith("full-rain-")) {
            p.put("rainAmount", c.name.endsWith("off") ? 0 : 1); p.put("rainDensity", 2.8);
            p.put("rainBrightness", 1.5); p.put("rainLength", .4); p.put("rainSpeed", 1.4);
            if (c.name.endsWith("time2")) LabSky.timeOverride = 2;
            if (c.name.endsWith("world")) { p.put("weatherFollowWorld", 1); p.put("rainAmount", 0); }
        }
        if (c.name.startsWith("full-blackhole2d-")) {
            p.put("atmosphereStrength", 0); p.put("starDensity", .3); p.put("nebulaStrength", .8);
            p.put("blackHoleStyle", 2); p.put("blackHoleStrength", c.name.endsWith("off") ? 0 : 2);
            p.put("blackHoleSize", 10); p.put("blackHoleYaw", 0); p.put("blackHolePitch", 25);
            p.put("diskTilt", 82); p.put("blackHoleGlow", 1.3); p.put("blackHoleRingBrightness", 2);
            if (c.name.endsWith("time4")) LabSky.timeOverride = 4;
        }
        if (c.name.startsWith("full-rainbow")) { p.put("rainbowStrength", c.name.endsWith("off") ? 0 : 2); p.put("secondaryRainbow", 1); p.put("rainbowWetness", 1); p.put("sunElevation", 12); }
        if (c.name.equals("full-halo")) { p.put("haloStrength", 2); p.put("haloIce", 1); }
        if (c.name.equals("full-special")) { p.put("specialStrength", 2); p.put("specialStyle", 1); p.put("birdDensity", .8); }
        if (c.name.equals("full-occlusion")) { p.put("volumeCloudStrength", 1); p.put("volumeCloudBase", 32); p.put("volumeCloudThickness", 100); }
        LabSky.recipe = LabSky.recipe.with(p);
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            level.setWeatherParameters(10000, 10000, c.name.equals("full-rain-world"), false);
            level.setRainLevel(c.name.equals("full-rain-world") ? 1 : 0);
            level.setThunderLevel(0);
            // Isolated validation world only. Use actual server weather packets;
            // production rendering only reads the client's existing weather.
            boolean raining = c.name.equals("full-rain-world");
            server.getPlayerList().getPlayers().forEach(player -> {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        raining ? net.minecraft.network.protocol.game.ClientboundGameEventPacket.START_RAINING : net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, raining ? 1 : 0));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
            });
        });
    }

    private static void configureAtmosphere(Case c) {
        Map<String, Object> p = isolated();
        p.put("seed", 715); p.put("exposure", 1.1);
        p.put("atmosphereStrength", 1); p.put("turbidity", 3.2); p.put("rayleighStrength", 1.25);
        p.put("mieStrength", 0.5); p.put("sunBrightness", 1.6); p.put("sunElevation", 35);
        p.put("sunYaw", 0); p.put("sunSize", 0.6); p.put("cycleSpeed", 0);
        p.put("mieAnisotropy", 0.76); p.put("nightAmbient", 0.035); p.put("twilightStrength", 1.2);
        p.put("moonBrightness", 0); p.put("moonYaw", 0); p.put("moonElevation", 25); p.put("moonSize", 4);
        p.put("moonPhase", 0); p.put("moonDetail", 5); p.put("moonHalo", 0.25); p.put("moonSpeed", 0);
        p.put("thinCloudStrength", 0); p.put("thinCloudCoverage", 0.64); p.put("thinCloudScale", 1.2);
        p.put("thinCloudHeight", 1.3); p.put("thinCloudSpeed", 1.2); p.put("thinCloudWind", 28);
        p.put("thinCloudEvolution", 0.8); p.put("thinCloudSilver", 1.3);
        p.put("skyTint", "#c6defa"); p.put("sunColor", "#fff0d0"); p.put("moonColor", "#d5e3ff");
        p.put("thinCloudColor", "#e9efff");
        if (c.name.equals("atmo-sunset")) p.put("sunElevation", 2);
        if (c.name.equals("atmo-twilight")) p.put("sunElevation", -7);
        if (c.name.equals("atmo-night") || c.name.startsWith("atmo-moon-")) p.put("sunElevation", -18);
        if (c.name.equals("atmo-night")) {
            p.put("starDensity", 0.36); p.put("starBrightness", 1.4); p.put("moonBrightness", 1.1);
            p.put("thinCloudStrength", 0.75); p.put("thinCloudCoverage", 0.48);
        }
        if (c.name.startsWith("atmo-moon-")) {
            p.put("moonBrightness", 1.4); p.put("moonHalo", 0);
            p.put("moonPhase", c.name.endsWith("full") ? 0 : c.name.endsWith("new") ? 180 : 90);
        }
        if (c.name.startsWith("atmo-cloud-")) {
            p.put("sunBrightness", 0); p.put("sunElevation", 25); p.put("moonBrightness", 0);
            p.put("thinCloudStrength", c.name.endsWith("off") ? 0 : 1.3);
            if (c.name.endsWith("time4")) LabSky.timeOverride = 4;
        }
        if (c.name.startsWith("atmo-cycle-")) {
            p.put("sunElevation", -8); p.put("cycleSpeed", 2);
            if (c.name.endsWith("time30")) LabSky.timeOverride = 30;
        }
        if (c.name.startsWith("atmo-disabled-")) {
            p = new HashMap<>(); p.put("atmosphereStrength", 0);
            if (c.name.endsWith("extreme")) {
                p.put("sunBrightness", 5); p.put("moonBrightness", 3); p.put("thinCloudStrength", 2);
                p.put("sunElevation", 85); p.put("cycleSpeed", 2); p.put("thinCloudSpeed", 2);
                p.put("skyTint", "#ff0000"); p.put("sunColor", "#00ff00"); p.put("moonColor", "#0000ff");
                p.put("thinCloudColor", "#ff00ff");
            }
        }
        LabSky.recipe = LabSky.recipe.with(p);
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

    static void recordDrawProgram(int program) {
        if (!AUTO || finished || caseIndex < 0) return;
        drawnProgram = program;
        boundProgramAtDraw = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
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
            record.addProperty("actualTime", LabSky.actualTime);
            if (c.name.startsWith("atmo-")) recordAtmosphereUniforms();
            if (c.name.startsWith("full-")) {
                record.addProperty("skyDrawProgram", drawnProgram);
                record.addProperty("skyBoundProgramAtDraw", boundProgramAtDraw);
                record.add("skyGpuUniforms", gpuUniforms(LabSky.shader.getId(), SKY_UNIFORMS));
            }
            if (c.mode == 0) checkDirection(image, camera, projection);
            checkAnimationsAndRichTranslation(image, c);
            waitingFinal = true;
        } catch (Exception e) { fail(e); }
    }

    private static final String[] SKY_UNIFORMS = {
        "WorldWeather", "WeatherSettings", "WeatherMotion", "RainbowSettings", "HaloSettings",
        "SpecialSettings", "SpecialShape", "BlackHoleAppearance", "BlackHoleCinematic",
        "RainColor", "LightningColor", "SpecialColorA", "SpecialColorB"
    };
    private static final String[] ENV_UNIFORMS = {
        "InverseProjection", "InverseViewRotation", "CameraPosition", "WorldWeather", "Time", "Quality",
        "WeatherSettings", "WeatherMotion", "RainSettings", "FogSettings", "FogMotion", "VolumeCloudSettings",
        "VolumeCloudGeometry", "VolumeCloudMotion", "CloudLighting", "LightingDetail", "FogColor", "VolumeCloudColor", "RainColor",
        "SceneSampler", "DepthSampler"
    };
    private static JsonObject gpuUniforms(int program, String[] names) {
        JsonObject result = new JsonObject();
        for (String name : names) {
            int location = GL20.glGetUniformLocation(program, name);
            JsonObject uniform = new JsonObject(); uniform.addProperty("location", location);
            if (location >= 0) {
                int count = name.equals("InverseProjection") ? 16 : name.equals("InverseViewRotation") ? 9 :
                        name.equals("CameraPosition") || name.endsWith("Color") || name.equals("SpecialColorA") || name.equals("SpecialColorB") ? 3 :
                        name.equals("WorldWeather") ? 2 : name.equals("Time") || name.equals("Quality") || name.endsWith("Sampler") ? 1 : 4;
                if (name.equals("Quality") || name.endsWith("Sampler")) {
                    int[] values = new int[count]; GL20.glGetUniformiv(program, location, values); uniform.add("values", JSON.toJsonTree(values));
                } else {
                    float[] values = new float[count]; GL20.glGetUniformfv(program, location, values); uniform.add("values", JSON.toJsonTree(values));
                }
            }
            result.add(name, uniform);
        }
        return result;
    }

    private static boolean fullCapture() {
        return AUTO && !finished && caseIndex >= 0 && waitingFinal && CASES.get(caseIndex).name.startsWith("full-");
    }
    static void afterEnvironmentSkipped() {
        if (!fullCapture()) return;
        record.addProperty("environmentSkipped", true);
        try (NativeImage image = screenshot()) {
            String name = CASES.get(caseIndex).name;
            image.writeToFile(OUTPUT.resolve(name + "-scene.png")); image.writeToFile(OUTPUT.resolve(name + "-post.png"));
            FEATURE_BASES.put(name, pixels(image));
        } catch (Exception error) { fail(error); }
    }
    static void environmentFailure(Exception error) { if (AUTO) fail(error); LabAdvancedValidation.fail(error); }

    /** Read source/destination attachments and uniforms during the actual world post draw. */
    static void afterEnvironment(ShaderInstance shader, RenderTarget snapshot, RenderTarget main,
                                 Matrix4f projection, Matrix3f view, LabEnvironment.State previous) {
        if (!fullCapture()) return;
        try {
            String name = CASES.get(caseIndex).name;
            record.addProperty("environmentSkipped", false);
            record.addProperty("environmentProgram", shader.getId());
            record.addProperty("environmentBoundProgram", GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            record.addProperty("environmentGeneration", LabEnvironment.generation);
            record.addProperty("environmentDraws", LabEnvironment.draws);
            record.addProperty("snapshotFbo", snapshot.frameBufferId); record.addProperty("mainFbo", main.frameBufferId);
            record.addProperty("sceneTexture", snapshot.getColorTextureId()); record.addProperty("depthTexture", snapshot.getDepthTextureId());
            record.addProperty("mainColorTexture", main.getColorTextureId());
            record.add("environmentGpuUniforms", gpuUniforms(shader.getId(), ENV_UNIFORMS));
            try (NativeImage source = Screenshot.takeScreenshot(snapshot)) { source.writeToFile(OUTPUT.resolve(name + "-scene.png")); }
            try (NativeImage post = Screenshot.takeScreenshot(main)) {
                post.writeToFile(OUTPUT.resolve(name + "-post.png"));
                FEATURE_BASES.put(name, pixels(post));
                String compare = switch (name) {
                    case "full-cloud-time0" -> "full-off";
                    case "full-cloud-time8", "full-cloud-translate", "full-cloud-inside", "full-reload" -> "full-cloud-time0";
                    case "full-shadow-on" -> "full-shadow-off";
                    case "full-shafts-on" -> "full-shafts-off";
                    case "full-fog-on" -> "full-fog-off";
                    case "full-rain-time0" -> "full-rain-off";
                    case "full-rain-time2" -> "full-rain-time0";
                    default -> null;
                };
                if (compare != null) { record.addProperty("comparisonBase", compare); record.addProperty("changedPixelsFromBase", different(FEATURE_BASES.get(compare), pixels(post))); }
            }
            int count = main.width * main.height;
            var scene = org.lwjgl.BufferUtils.createByteBuffer(count * 4);
            var post = org.lwjgl.BufferUtils.createByteBuffer(count * 4);
            var sceneDepth = org.lwjgl.BufferUtils.createFloatBuffer(count);
            var postDepth = org.lwjgl.BufferUtils.createFloatBuffer(count);
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.frameBufferId);
            GL11.glReadPixels(0, 0, main.width, main.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, scene);
            GL11.glReadPixels(0, 0, main.width, main.height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, sceneDepth);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL11.glReadPixels(0, 0, main.width, main.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, post);
            GL11.glReadPixels(0, 0, main.width, main.height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, postDepth);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            int changed = 0, rgbaChanged = 0, geom = 0, sky = 0, geomChanged = 0, skyChanged = 0;
            float maxDepthDelta = 0; double minDistance = Double.POSITIVE_INFINITY, maxDistance = 0;
            org.joml.Vector4f position = new org.joml.Vector4f();
            for (int i = 0; i < count; i++) {
                // Screenshot.takeScreenshot forces alpha to 255; compare visible
                // RGB for independent PNG verification, retain RGBA separately.
                boolean difference = false;
                for (int channel = 0; channel < 3; channel++) if (scene.get(i * 4 + channel) != post.get(i * 4 + channel)) difference = true;
                if (difference || scene.get(i * 4 + 3) != post.get(i * 4 + 3)) rgbaChanged++;
                float d = sceneDepth.get(i); maxDepthDelta = Math.max(maxDepthDelta, Math.abs(d - postDepth.get(i)));
                if (d < .999999f) {
                    geom++; if (difference) geomChanged++;
                    position.set(((i % main.width) + .5f) / main.width * 2 - 1,
                            ((i / main.width) + .5f) / main.height * 2 - 1, d * 2 - 1, 1).mul(projection);
                    position.div(position.w); double distance = Math.sqrt(position.x * position.x + position.y * position.y + position.z * position.z);
                    minDistance = Math.min(minDistance, distance); maxDistance = Math.max(maxDistance, distance);
                } else { sky++; if (difference) skyChanged++; }
                if (difference) changed++;
            }
            record.addProperty("postChangedPixels", changed); record.addProperty("geometryPixels", geom); record.addProperty("skyPixels", sky);
            record.addProperty("postRgbaChangedPixels", rgbaChanged);
            record.addProperty("geometryChangedPixels", geomChanged); record.addProperty("skyChangedPixels", skyChanged);
            record.addProperty("maxDepthDifference", maxDepthDelta);
            if (geom > 0) { record.addProperty("minGeometryDistance", minDistance); record.addProperty("maxGeometryDistance", maxDistance); }
            record.addProperty("glErrorAfterEnvironment", GL11.glGetError());
        } catch (Exception error) { fail(error); }
    }

    static void afterEnvironmentRestore(LabEnvironment.State previous) {
        if (!fullCapture() || !record.has("environmentProgram")) return;
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean pass = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == previous.draw &&
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == previous.read && Arrays.equals(viewport, previous.viewport) &&
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == previous.program &&
                GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == previous.active &&
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST) == previous.depth && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) == previous.write &&
                GL11.glIsEnabled(GL11.GL_BLEND) == previous.blend && GL11.glIsEnabled(GL11.GL_CULL_FACE) == previous.cull &&
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) == previous.scissor &&
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) == previous.srcRgb && GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) == previous.dstRgb &&
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA) == previous.srcAlpha && GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) == previous.dstAlpha;
        var colorMask = org.lwjgl.BufferUtils.createByteBuffer(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        for (int i = 0; i < 4; i++) pass &= (colorMask.get(i) != 0) == previous.colorMask[i];
        for (int i = 0; i < 2; i++) {
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            pass &= GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == previous.bound[i] && RenderSystem.getShaderTexture(i) == previous.shaderTexture[i];
        }
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(previous.active);
        record.addProperty("environmentStateRestored", pass);
    }

    private static void checkDirection(NativeImage image, Camera camera, Matrix4f projection) {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = pixels(image);
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

    private static void checkAnimationsAndRichTranslation(NativeImage image, Case c) {
        if (c.name.startsWith("full-")) {
            if (c.name.equals("full-rainbow-off") || c.name.equals("full-halo-off") || c.name.equals("full-special-off")) FEATURE_BASES.put(c.name, pixels(image));
            if (c.name.equals("full-rainbow") || c.name.equals("full-halo") || c.name.equals("full-special"))
                compareFeature(image, c.name + "-off", "directionFeatureChangedPixels", "directionFeaturePass", false);
        }
        if (c.name.startsWith("full-blackhole2d-")) {
            FEATURE_BASES.put(c.name, pixels(image));
            if (!c.name.endsWith("off")) compareFeature(image, c.name.endsWith("time0") ? "full-blackhole2d-off" : "full-blackhole2d-time0",
                    "blackhole2dChangedPixels", "blackhole2dPass", false);
        }
        if (c.name.startsWith("atmo-")) {
            FEATURE_BASES.put(c.name, pixels(image));
            switch (c.name) {
                case "atmo-translate", "atmo-reload" -> compareFeature(image, "atmo-day",
                    "differentPixelsFromAtmosphereBase", "atmosphereInvariantPass", true);
                case "atmo-cloud-time0" -> compareFeature(image, "atmo-cloud-off",
                    "differentPixelsFromCloudOff", "thinCloudDisplayPass", false);
                case "atmo-cloud-time4" -> compareFeature(image, "atmo-cloud-time0",
                    "differentPixelsFromCloudTime0", "thinCloudAnimationPass", false);
                case "atmo-cycle-time30" -> compareFeature(image, "atmo-cycle-time0",
                    "differentPixelsFromCycleTime0", "dayCyclePass", false);
                case "atmo-moon-full", "atmo-moon-quarter" -> compareFeature(image, "atmo-moon-new",
                    "differentPixelsFromNewMoon", "moonPhasePass", false);
                case "atmo-disabled-extreme" -> compareFeature(image, "atmo-disabled-base",
                    "differentPixelsFromDisabledBase", "atmosphereDisabledPass", true);
            }
        }
        if (c.name.startsWith("ring-")) {
            String baseline = c.name.substring(0, c.name.lastIndexOf('-')) + "-off";
            if (c.name.endsWith("off")) FEATURE_BASES.put(baseline, pixels(image));
            else {
                long count = different(FEATURE_BASES.get(baseline), pixels(image));
                record.addProperty("differentPixelsFromRingOff", count);
                record.addProperty("ringRegressionPass", c.name.contains("offscreen") ? count == 0 : count > 100);
            }
        }
        if (c.name.equals("nebula-medium")) richBasePixels = pixels(image);
        if (c.name.equals("rich-sky-translate")) {
            long count = different(richBasePixels, pixels(image));
            record.addProperty("differentPixelsFromRichBase", count);
            record.addProperty("richTranslationPass", count == 0);
        }
        if (c.name.equals("celestial-off") || c.name.equals("blackhole-off") || c.name.equals("blackhole-lensing-zero"))
            FEATURE_BASES.put(c.name, pixels(image));
        if (c.name.equals("celestial-animation-time0")) {
            compareFeature(image, "celestial-off", "differentPixelsFromCelestialOff", "celestialDisplayPass", false);
            FEATURE_BASES.put("celestial-time0", pixels(image));
        }
        if (c.name.equals("blackhole-animation-time0")) {
            compareFeature(image, "blackhole-off", "differentPixelsFromBlackholeOff", "blackholeDisplayPass", false);
            FEATURE_BASES.put("blackhole-time0", pixels(image));
        }
        if (c.name.equals("blackhole-lensing-one"))
            compareFeature(image, "blackhole-lensing-zero", "differentPixelsFromLensingZero", "blackholeLensingPass", false);
        if (c.name.equals("celestial-translate"))
            compareFeature(image, "celestial-time0", "differentPixelsFromCelestialBase", "celestialTranslationPass", true);
        if (c.name.equals("blackhole-translate"))
            compareFeature(image, "blackhole-time0", "differentPixelsFromBlackholeBase", "blackholeTranslationPass", true);
        if (c.name.equals("celestial-rock") || c.name.equals("celestial-star"))
            compareFeature(image, "celestial-time0", "differentPixelsFromGasBody", "celestialSurfaceTypePass", false);
        if (!c.name.contains("-animation-")) return;
        String feature = c.name.substring(0, c.name.indexOf("-animation-"));
        if (c.name.endsWith("time0")) {
            ANIMATION_BASES.put(feature, pixels(image));
        } else {
            long count = different(ANIMATION_BASES.remove(feature), pixels(image));
            record.addProperty("animationFeature", feature);
            record.addProperty("differentPixelsFromAnimationBase", count);
            record.addProperty("animationPass", count > 0);
        }
    }

    /** Read back the program used for the actual draw, not merely JSON declarations. */
    private static void recordAtmosphereUniforms() {
        int program = LabSky.shader.getId();
        record.addProperty("drawShaderProgram", drawnProgram);
        record.addProperty("boundShaderProgramAtDraw", boundProgramAtDraw);
        JsonObject uniforms = new JsonObject();
        for (String name : new String[]{"AtmosphereSettings", "SunSettings", "DaySettings", "MoonSettings",
                "MoonSurface", "ThinCloudSettings", "ThinCloudMotion", "SkyTint", "SunColor", "MoonColor", "ThinCloudColor"}) {
            int location = GL20.glGetUniformLocation(program, name);
            JsonObject uniform = new JsonObject(); uniform.addProperty("location", location);
            if (location >= 0) {
                float[] values = new float[name.endsWith("Color") || name.equals("SkyTint") ? 3 : 4];
                GL20.glGetUniformfv(program, location, values);
                uniform.add("values", JSON.toJsonTree(values));
            }
            uniforms.add(name, uniform);
        }
        record.add("atmosphereGpuUniforms", uniforms);
    }

    private static void compareFeature(NativeImage image, String baseline, String countName, String passName, boolean identical) {
        long count = different(FEATURE_BASES.get(baseline), pixels(image));
        record.addProperty(countName, count);
        record.addProperty(passName, identical ? count == 0 : count > 0);
    }

    private static int[] pixels(NativeImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            pixels[y * width + x] = image.getPixelRGBA(x, y);
        return pixels;
    }
    private static long different(int[] first, int[] second) {
        if (first == null || first.length != second.length) return -1;
        long count = 0;
        for (int i = 0; i < first.length; i++) if (first[i] != second[i]) count++;
        return count;
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
        LabSky.mode = 2; LabSky.recipe = LabSky.defaults;
        LabSky.quality = LabSky.defaults.quality(); LabSky.frozen = false; LabSky.timeOverride = -1f;
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
        summary.addProperty("animationCasesPassed", RESULTS.asList().stream()
            .filter(e -> e.getAsJsonObject().has("animationPass") && e.getAsJsonObject().get("animationPass").getAsBoolean()).count());
        summary.addProperty("richTranslationCasesPassed", RESULTS.asList().stream()
            .filter(e -> e.getAsJsonObject().has("richTranslationPass") && e.getAsJsonObject().get("richTranslationPass").getAsBoolean()).count());
        for (String key : new String[]{"celestialDisplayPass", "blackholeDisplayPass", "blackholeLensingPass",
                "celestialTranslationPass", "blackholeTranslationPass", "celestialSurfaceTypePass"})
            summary.addProperty(key + "Count", RESULTS.asList().stream()
                .filter(e -> e.getAsJsonObject().has(key) && e.getAsJsonObject().get(key).getAsBoolean()).count());
        summary.addProperty("schema", SkyRecipe.SCHEMA);
        summary.addProperty("parameters", LabSky.defaults.toJson().getAsJsonObject("parameters").size());
        summary.addProperty("selectedCases", System.getProperty("cosmiclab.cases", ""));
        summary.addProperty("modVersion", "0.5.0");
        summary.addProperty("validationProtocol", CASES.stream().anyMatch(c -> c.name.startsWith("full-")) ? "cosmic-full-sky-forge/1" : CASES.stream().anyMatch(c -> c.name.startsWith("atmo-"))
            ? "cosmic-atmosphere-forge/1" : "cosmic-sky-forge-legacy/1");
        try { Files.writeString(OUTPUT.resolve("summary.json"), JSON.toJson(summary)); }
        catch (Exception e) { e.printStackTrace(); }
        System.out.println("[COSMICLAB] VALIDATION COMPLETE: " + JSON.toJson(summary));
        // Only explicitly requested lab clients close themselves; never touch other processes.
        if (Boolean.getBoolean("cosmiclab.exitOnComplete")) mc.stop();
    }
    private static void fail(Exception e) {
        finished = true;
        LabSky.timeOverride = -1f;
        e.printStackTrace();
        try { Files.createDirectories(OUTPUT); Files.writeString(OUTPUT.resolve("failure.txt"), e.toString()); }
        catch (Exception ignored) {}
    }
}
