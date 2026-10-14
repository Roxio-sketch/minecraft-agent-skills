package dev.vfxworkbench.effects.blackhole;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.client.VfxWorkbenchScreen;
import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.core.ParamState;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Real Forge shader fixture, enabled only by -PwhiteHoleRegression=true. */
@Mod.EventBusSubscriber(modid = VfxWorkbenchMod.MODID, value = Dist.CLIENT)
public final class WhiteHoleRegression {
    private static final String SAVE = "VFX-WhiteHole-Outflow-Check";
    private static final String FIXTURE = "white-hole-default-dust/v3";
    private static final String[] CASES = {
        "default-ground-oblique-t8", "default-ground-oblique-t9_2", "default-ground-top-t8", "default-ground-side-t8",
        "paused-t8-a", "paused-t8-b", "speed-hot-before", "speed-zero-a", "speed-zero-b", "speed-hot-restored",
        "fixed-center-left", "fixed-center-right", "legacy-black", "effect-off",
        "repreview", "resource-reload", "workbench-form", "dirty-unpack",
        "default-floating-bipolar-t8", "default-floating-bipolar-t9_2",
        "jet-only-t8", "jet-only-t9_2", "jet-only-off-t8",
        "shell-only-t8", "shell-only-t9_2", "shell-only-off-t8",
        "default-floating-tilt45-t8", "shell-interior-t8",
        "core-glow-only-on-t8", "core-glow-only-off-t8", "core-glow-only-gain-zero-t8",
        "core-radiance-far-on-t8", "core-radiance-far-off-t8", "core-radiance-far-gain-zero-t8",
        "core-radiance-resized-on-t8", "core-radiance-resized-off-t8", "core-radiance-size-restored-on-t8",
        "wall-external-on-t8", "wall-external-off-t8",
        "dust-oblique-on-t8", "dust-oblique-density-zero-t8", "dust-top-on-t8", "dust-top-density-zero-t8", "dust-late-on", "dust-late-density-zero", "shell-toggle-restored-t8", "shell-toggle-disabled-t8", "shell-toggle-reenabled-t8", "user-default-live-time"
    };
    private static final int ZERO_START = 7, HOT_RESTORE = 9, LEGACY = 12, OFF = 13, REPREVIEW = 14, RELOAD = 15, UI = 16, DIRTY_UNPACK = 17;
    private static final int FLOAT_T8 = 18, FLOAT_T9 = 19, JET_T8 = 20, JET_T9 = 21, JET_OFF = 22,
        SHELL_T8 = 23, SHELL_T9 = 24, SHELL_OFF = 25, TILT45 = 26, INTERIOR = 27,
        CORE_GLOW_ON = 28, CORE_GLOW_OFF = 29, CORE_GAIN_ZERO = 30,
        CORE_FAR_ON = 31, CORE_FAR_OFF = 32, CORE_FAR_ZERO = 33,
        CORE_RESIZE_ON = 34, CORE_RESIZE_OFF = 35, CORE_RESTORE_ON = 36, WALL_ON = 37, WALL_OFF = 38;
    private static final int[] UNPACK_KEYS = {GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS,
        GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_SWAP_BYTES, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES};
    private static final String[] UNPACK_NAMES = {"rowLength", "skipRows", "skipPixels", "alignment", "swapBytes", "imageHeight", "skipImages"};
    private static final JsonArray checks = new JsonArray(), captures = new JsonArray();
    private static boolean opened, setup, done, optionsSaved;
    private static volatile boolean sceneReady;
    private static volatile Throwable sceneFailure;
    private static int ticks, stage, phase, stageTicks, initialGeneration;
    private static int oldDistance, oldGuiScale, oldWindowWidth, oldWindowHeight, oldWindowX, oldWindowY;
    private static boolean oldBob, oldHide, oldPause, oldShadows, oldFullscreen;
    private static CloudStatus oldClouds;
    private static CameraType oldCamera;
    private static Vec3 fixedCenter, groundCenter, expectedHit, eyePosition;
    private static float targetYaw, targetPitch;
    private static Path folder, probeFolder, preferenceFile;
    private static byte[] preferenceBackup;
    private static Map<String, String> parameterBackup;
    private static Map<String, String> whiteMotionDefaults;
    private static String placementKind = "real-ground-preview";
    private static String pendingFrame, pendingScreenshot, expectedProbe;
    private static CompletableFuture<Void> resourceReload;
    private static CompletableFuture<Void> sceneEdit;
    private static int propagationTicks;
    private static int wallZ,wallXMin,wallXMax,wallYMin,wallYMax;
    private static long offWorldDraws, offParticleDraws;
    private static int[] unpackOriginal, unpackExpected;
    private static int unpackBuffer;

    private WhiteHoleRegression() {}
    private static final boolean ENABLED=Boolean.getBoolean("vfxworkbench.whiteHoleRegression")
        ||Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("config/vfxworkbench/run-whitehole-regression.once"));
    private static boolean enabled() { return ENABLED; }
    private static ParamState state() { return ParamRegistry.state(BlackHoleSkillSchema.ID); }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!opened) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                opened = true;
                folder = mc.gameDirectory.toPath().resolve("white-hole-outflow-lab/game-probes");
                probeFolder = mc.gameDirectory.toPath().resolve("black-hole-lab/world-probes");
                Files.createDirectories(folder);
                Files.createDirectories(probeFolder);
                preferenceFile = mc.gameDirectory.toPath().resolve("config/vfxworkbench/ui.json");
                if (Files.isRegularFile(preferenceFile)) preferenceBackup = Files.readAllBytes(preferenceFile);
                parameterBackup = state().snapshot();
                check("only-opt-in-client-fixture", mc.level == null && mc.player == null);
                if (Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))
                    mc.createWorldOpenFlows().loadLevel(mc.screen, SAVE);
                else mc.createWorldOpenFlows().createFreshLevel(SAVE,
                    new LevelSettings("白洞外流验收", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20261007L, false, false), WorldPresets::createNormalWorldDimensions);
                System.out.println("[WHITE-HOLE] BEGIN save=" + SAVE + " cases=" + CASES.length);
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (!setup) { setup(mc); return; }
            if (sceneFailure != null) throw new IllegalStateException("Fixture scene setup failed", sceneFailure);
            // Large host modpacks can still deliver rebuilt chunk lighting after
            // the server completed fixture blocks. Let those initial scene uploads
            // settle before demanding pixel-exact paused-frame comparisons.
            if (!sceneReady || ++ticks < 600) return;
            if(stage==0&&mc.level.getChunkSource().getLightEngine().hasLightWork())return;
            if (stage >= CASES.length) { finish(mc, null); return; }
            if (++stageTicks > 600) throw new IllegalStateException("Fixture timed out at " + CASES[stage]);
            if (phase == 0) { beginCase(mc); phase = 1; return; }
            if (stage != UI) holdCamera(mc);
            if (resourceReload != null) {
                if (!resourceReload.isDone() || mc.getOverlay() != null) return;
                resourceReload.join(); resourceReload = null;
                check("resource-reload-new-world-shader", BlackHoleRender.generation > initialGeneration);
                check("resource-reload-particle-shader-ready", BlackHoleParticles.shader != null);
                phase = 1; stageTicks = 0; return;
            }
            if (sceneEdit != null) {
                if (!sceneEdit.isDone()) return;
                sceneEdit.join(); sceneEdit = null; propagationTicks = 12;
                check("wall-edit-finished-on-fixture-server", true);
            }
            if (propagationTicks > 0) { propagationTicks--; return; }
            if (!BlackHoleClient.failure.isEmpty()) throw new IllegalStateException(BlackHoleClient.failure);
            if (stage == HOT_RESTORE && phase == 4) {
                // Let the live material clock consume playing=false first. Restore
                // speeds only after its elapsed time has stopped, without resetting it.
                for (var entry : whiteMotionDefaults.entrySet()) state().set(entry.getKey(), entry.getValue());
            }
            // Camera moves and resource reloads also trigger vanilla/modpack fog
            // transitions. Settle the first image of exact-repeat pairs fully.
            int capturePhase=(stage==0||stage==4||stage==RELOAD||stage==45)?120:22;
            if (phase++ == capturePhase) {
                if (stage == OFF) {
                    check("off-center-cleared", BlackHoleClient.center == null);
                    check("off-world-render-stopped", BlackHoleClient.worldDraws == offWorldDraws);
                    check("off-particle-render-stopped", BlackHoleParticles.draws == offParticleDraws);
                    check("off-gl-error-zero", GL11.glGetError() == GL11.GL_NO_ERROR);
                    pendingScreenshot = name();
                } else if (stage == UI) {
                    pendingScreenshot = name();
                } else {
                    check(name() + "-fixed-center", fixedCenter.equals(BlackHoleClient.center));
                    pendingFrame = name(); expectedProbe = name(); BlackHoleWorld.probe = name();
                    System.out.println("[WHITE-HOLE] CAPTURE " + name());
                }
            }
            if (phase < capturePhase+12 || pendingFrame != null || pendingScreenshot != null || BlackHoleWorld.probe != null) return;
            if (expectedProbe != null) { verifyProbe(expectedProbe); expectedProbe = null; }
            if (stage == UI) {
                check("f6-closes-white-hole-workbench", mc.screen.keyPressed(GLFW.GLFW_KEY_F6, 0, 0));
                check("f6-workbench-returned-to-world", !(mc.screen instanceof VfxWorkbenchScreen));
            }
            stage++; phase = 0; stageTicks = 0;
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void setup(Minecraft mc) {
        setup = true;
        oldBob = mc.options.bobView().get(); oldHide = mc.options.hideGui;
        oldPause = mc.options.pauseOnLostFocus; oldShadows = mc.options.entityShadows().get();
        oldClouds = mc.options.cloudStatus().get(); oldCamera = mc.options.getCameraType();
        oldDistance = mc.options.renderDistance().get(); oldGuiScale = mc.options.guiScale().get();
        var window = mc.getWindow();
        oldWindowWidth = window.getScreenWidth(); oldWindowHeight = window.getScreenHeight();
        oldWindowX = window.getX(); oldWindowY = window.getY(); oldFullscreen = window.isFullscreen();
        optionsSaved = true;
        if (oldFullscreen) window.toggleFullScreen();
        mc.options.pauseOnLostFocus = false; mc.options.hideGui = true;
        mc.options.bobView().set(false); mc.options.entityShadows().set(false);
        mc.options.cloudStatus().set(CloudStatus.OFF); mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.renderDistance().set(4); mc.options.guiScale().set(2); mc.resizeDisplay();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var server = mc.getSingleplayerServer(); var level = server.overworld();
                // This method is reached only after opening the fixture's explicitly named save.
                level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                var remove = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
                for (var entity : level.getAllEntities()) if (!(entity instanceof net.minecraft.world.entity.player.Player)) remove.add(entity);
                for (var entity : remove) entity.discard();
                for (int x = -22; x <= 22; x++) for (int z = -10; z <= 32; z++) {
                    level.setBlock(new BlockPos(x, 119, z), ((Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1) == 0
                        ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
                    for (int y = 120; y <= 150; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                for (int x = -22; x <= 22; x++) for (int y = 120; y <= 138; y++)
                    level.setBlock(new BlockPos(x, y, 32), ((Math.floorDiv(x, 2) + Math.floorDiv(y, 2)) & 1) == 0
                        ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
                for (var player : server.getPlayerList().getPlayers()) {
                    player.setGameMode(GameType.SPECTATOR); player.setNoGravity(true);
                    player.teleportTo(level, 0, 127, 0, 0, 32);
                }
                sceneReady = true;
            } catch (Throwable failure) { sceneFailure = failure; }
        });
    }

    private static void beginCase(Minecraft mc) throws Exception {
        mc.setScreen(null);
        if (stage == 0) {
            check("world-shader-ready", BlackHoleRender.world != null);
            check("particle-shader-ready", BlackHoleParticles.shader != null);
            configureWhite();
            mc.player.setPos(0, 127, 0);
            float pitch = (float) Math.toDegrees(Math.atan2(mc.player.getEyePosition().y - 120, 14));
            mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
            Vec3 eye = mc.player.getEyePosition(), direction = mc.player.getLookAngle();
            expectedHit = eye.add(direction.scale((120 - eye.y) / direction.y));
            Vec3 analyticCenter = expectedHit.add(0, state().getDouble("core") * state().getDouble("scale") + .05, 0);
            BlackHoleClient.preview(); fixedCenter = BlackHoleClient.center;
            groundCenter = fixedCenter;
            check("real-ground-preview-anchor", fixedCenter != null && fixedCenter.distanceTo(analyticCenter) < .0001);
            check("white-preset-user-tilt", state().getDouble("tilt") == -1);
        }
        switch (stage) {
            case 0, 4, 6, ZERO_START, 10, REPREVIEW, RELOAD, DIRTY_UNPACK -> {
                if (stage == 4) { configureWhite(); state().set("playing", "false"); }
                if (stage == 6) state().set("playing", "false");
                if (stage == ZERO_START) {
                    // HOT edits after the paused t=8 capture must freeze its current
                    // material phases and identities, rather than jump to phase zero.
                    state().set("playing", "false"); state().set("speed", "0"); state().set("infallSpeed", "0");
                    state().set("particleSpeed", "0"); state().set("jetSpeed", "0"); state().set("shellSpeed", "0"); state().set("playing", "true");
                }
                if (stage == REPREVIEW) { configureWhite(); repreviewAtSameAnchor(mc); }
                if (stage == RELOAD) {
                    initialGeneration = BlackHoleRender.generation;
                    resourceReload = mc.reloadResourcePacks();
                }
                camera(mc, fixedCenter.add(stage == 10 ? -.7 : 0, 7.6, -14), fixedCenter);
            }
            case 1 -> { state().set("time", "9.2"); camera(mc, fixedCenter.add(0, 7.6, -14), fixedCenter); }
            case 2 -> { state().set("time", "8"); camera(mc, fixedCenter.add(0, 12, 0), fixedCenter); }
            case 3 -> camera(mc, fixedCenter.add(0, .01, -14), fixedCenter);
            case 5, 8 -> { /* Preserve the preceding clock and configuration for repeatability. */ }
            case HOT_RESTORE -> state().set("playing", "false");
            case 11 -> camera(mc, fixedCenter.add(.7, 7.6, -14), fixedCenter);
            case LEGACY -> {
                state().set("form", "classic"); state().set("objectMode", "black");
                state().set("coreEmission", "0"); state().set("coreColor", "#000000");
                state().set("coreEdgeColor", "#000000"); state().set("coreGradientStrength", "0");
                state().set("playing", "false"); state().set("time", "8");
                state().set("density", "0"); state().set("halo", "0"); state().set("particles", "false");
                state().set("jetStrength", "0");
                camera(mc, fixedCenter.add(0, 7.6, -14), fixedCenter);
            }
            case OFF -> {
                BlackHoleClient.stop(); offWorldDraws = BlackHoleClient.worldDraws; offParticleDraws = BlackHoleParticles.draws;
            }
            case UI -> {
                check("workbench-keeps-existing-project", ParamRegistry.has(BlackHoleSkillSchema.ID));
                check("white-form-is-selected", state().get("form").equals("white-ring"));
                mc.setScreen(new VfxWorkbenchScreen(BlackHoleSkillSchema.ID));
                var rows = mc.screen.children().stream().filter(child -> child.getClass().getSimpleName().equals("ScrollableRows"))
                    .map(child -> (AbstractWidget) child).findFirst().orElseThrow();
                mc.screen.setFocused(rows); mc.screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
                for (int i = 0; i < 200 && !"form".equals(field(rows, "focusKey")); i++) {
                    String key = (String) field(rows, "focusKey");
                    if (key != null && key.startsWith("@") && ((Set<?>) field(rows, "collapsed")).contains(key.substring(1)))
                        mc.screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
                    mc.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                }
                check("white-form-keyboard-focused", "form".equals(field(rows, "focusKey")));
                mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                check("white-form-dropdown-open", "form".equals(field(rows, "enumKey")));
            }
            case FLOAT_T8 -> {
                configureWhite(); useFloatingCenter();
                camera(mc, fixedCenter.add(0, 4, -18), fixedCenter);
                check("floating-center-ten-blocks-above-real-ground-anchor", fixedCenter.equals(groundCenter.add(0, 10, 0)));
            }
            case FLOAT_T9 -> state().set("time", "9.2");
            case JET_T8 -> {
                configureJetOnly(); useFloatingCenter(); camera(mc, fixedCenter.add(0, 4, -18), fixedCenter);
            }
            case JET_T9 -> state().set("time", "9.2");
            case JET_OFF -> { state().set("time", "8"); state().set("jetStrength", "0"); }
            case SHELL_T8 -> {
                configureShellOnly(); useFloatingCenter(); camera(mc, fixedCenter.add(0, 4, -18), fixedCenter);
            }
            case SHELL_T9 -> state().set("time", "9.2");
            case SHELL_OFF -> { state().set("time", "8"); state().set("shellEnabled", "false"); }
            case TILT45 -> {
                configureWhite(); useFloatingCenter(); state().set("tilt", "45");
                camera(mc, fixedCenter.add(0, 4, -18), fixedCenter);
                check("tilted-outflow-follows-disk-axis", state().getDouble("tilt") == 45);
            }
            case INTERIOR -> {
                configureShellOnly(); useFloatingCenter();
                double localOffset = 1.2 * state().getDouble("scale");
                Vec3 eye = fixedCenter.add(localOffset, 0, 0);
                camera(mc, eye, eye.add(0, 0, 10));
                check("shell-interior-eye-outside-core", eye.distanceTo(fixedCenter) > state().getDouble("core") * state().getDouble("scale"));
                check("shell-interior-eye-within-t8-nearest-shell", eye.distanceTo(fixedCenter) < 2.0528 * state().getDouble("scale"));
            }
            case CORE_GLOW_ON -> {
                configureCoreGlowOnly(); useFloatingCenter(); camera(mc, fixedCenter.add(0, 4, -18), fixedCenter);
                check("core-glow-isolated-from-disk-shell-jets-halo-and-particles", state().getDouble("density") == 0
                    && state().getDouble("jetStrength") == 0 && state().getDouble("shellStrength") == 0
                    && state().getDouble("halo") == 0 && !state().getBool("particles") && !state().getBool("lens"));
            }
            case CORE_GLOW_OFF -> state().set("coreGlow", "0");
            case CORE_GAIN_ZERO -> { state().set("coreGlow", "1.2"); state().set("coreEmission", "0"); }
            case CORE_FAR_ON -> {
                configureCoreGlowOnly(); useFloatingCenter();
                double distance = state().getDouble("distance");
                camera(mc, fixedCenter.add(0, 0, -distance), fixedCenter);
                check("core-radiance-far-eye-uses-real-default-placement-distance", Math.abs(eyePosition.distanceTo(fixedCenter) - distance) < .0001);
            }
            case CORE_FAR_OFF -> state().set("coreGlow", "0");
            case CORE_FAR_ZERO -> { state().set("coreGlow", "1.2"); state().set("coreEmission", "0"); }
            case CORE_RESIZE_ON -> {
                configureCoreGlowOnly(); useFloatingCenter(); camera(mc, fixedCenter.add(0, 4, -18), fixedCenter);
                // This opt-in client owns its development window; preserve the original
                // dimensions/fullscreen preference and exercise actual FBO reconstruction.
                mc.getWindow().setWindowed(oldWindowWidth == 960 ? 800 : 960, oldWindowHeight == 540 ? 600 : 540);
                mc.resizeDisplay();
            }
            case CORE_RESIZE_OFF -> state().set("coreGlow", "0");
            case CORE_RESTORE_ON -> {
                mc.getWindow().setWindowed(oldWindowWidth, oldWindowHeight); mc.resizeDisplay();
                configureCoreGlowOnly(); useFloatingCenter(); camera(mc, fixedCenter.add(0, 4, -18), fixedCenter);
            }
            case WALL_ON -> {
                configureWhite(); useFloatingCenter(); state().set("particles", "false"); state().set("lens", "false");
                state().set("shellEnabled","true");state().set("jetStrength","2.4");state().set("jetLength","7");
                // The core follows the established artistic core/terrain policy.
                // Compare only physically depth-clipped jets/shells, with core
                // radiance disabled in both captures; do not claim source=0 here.
                state().set("coreGlow", "0");
                double support = Math.max(Math.hypot(state().getDouble("jetLength"), state().getDouble("jetWidth") * 3.6),
                    state().getDouble("shellRadius") + 3 * state().getDouble("shellWidth")) * state().getDouble("scale");
                camera(mc, fixedCenter.add(0, 3, -support - 12), fixedCenter);
                wallZ = (int) Math.floor(fixedCenter.z - support - 2);
                wallXMin=(int)Math.floor(fixedCenter.x-support-2);wallXMax=(int)Math.ceil(fixedCenter.x+support+2);
                wallYMin=(int)Math.floor(fixedCenter.y-support-2);wallYMax=(int)Math.ceil(fixedCenter.y+support+2);
                sceneEdit = new CompletableFuture<>();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var level = mc.getSingleplayerServer().overworld();
                        for (int x = wallXMin; x <= wallXMax; x++) for (int y = wallYMin; y <= wallYMax; y++)
                            level.setBlock(new BlockPos(x, y, wallZ), ((Math.floorDiv(x, 2) + Math.floorDiv(y, 2)) & 1) == 0
                                ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
                        sceneEdit.complete(null);
                    } catch (Throwable failure) { sceneEdit.completeExceptionally(failure); }
                });
                check("wall-precedes-complete-depth-clipped-jet-shell-support", wallZ + 1 < fixedCenter.z - support && eyePosition.z < wallZ);
                check("wall-covers-depth-clipped-jet-shell-support-in-x-and-y", fixedCenter.x - support > wallXMin && fixedCenter.x + support < wallXMax+1 && fixedCenter.y - support > wallYMin && fixedCenter.y + support < wallYMax+1);
            }
            case WALL_OFF -> { state().set("jetStrength", "0"); state().set("shellStrength", "0"); state().set("coreGlow", "0"); }
            case 39,41,43 -> {
                if(stage==39){
                    sceneEdit=new CompletableFuture<>();mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();for(int x=wallXMin;x<=wallXMax;x++)for(int y=wallYMin;y<=wallYMax;y++)level.setBlock(new BlockPos(x,y,wallZ),Blocks.AIR.defaultBlockState(),3);sceneEdit.complete(null);}catch(Throwable ex){sceneEdit.completeExceptionally(ex);}});
                }
                state().set("form","dust");BlackHoleSkillSchema.applyForm(state(),"dust");
                state().set("playing","false");state().set("time",stage==43?"37862.8":"8");
                state().set("halo","0");state().set("lens","false");state().set("particles","false");
                BlackHoleRender.resetClock();useFloatingCenter();
                camera(mc,fixedCenter.add(0,stage==41?18:4,stage==41?-.2:-18),fixedCenter);
                check("dust-is-independent-of-particle-toggle-"+stage,!state().getBool("particles")&&state().get("diskStyle").equals("dust"));
            }
            case 40,42,44 -> state().set("density","0");
            case 45 -> {configureShellOnly();useFloatingCenter();camera(mc,fixedCenter.add(0,4,-18),fixedCenter);}
            case 46 -> {state().set("shellEnabled","false");check("shell-toggle-keeps-strength",state().getDouble("shellStrength")==.28);}
            case 47 -> state().set("shellEnabled","true");
            case 48 -> {configureWhite();state().set("time","37862.8");useFloatingCenter();camera(mc,fixedCenter.add(0,4,-39),fixedCenter);}
            default -> throw new IllegalStateException("Unknown fixture stage " + stage);
        }
    }

    private static void configureWhite() {
        var p = state(); p.set("form", "white-ring"); BlackHoleSkillSchema.applyForm(p, "white-ring");
        
        p.set("playing", "false"); p.set("time", "8");
        
        whiteMotionDefaults = new LinkedHashMap<>();
        for (String key : Set.of("speed", "infallSpeed", "particleSpeed", "jetSpeed", "shellSpeed")) whiteMotionDefaults.put(key, p.get(key));
        BlackHoleRender.frozenTime = -1; BlackHoleRender.resetClock(); BlackHoleParticles.debug = 0;
    }

    private static void configureJetOnly() {
        configureWhite(); state().set("jetStrength","2.4"); state().set("jetLength","7"); state().set("particles", "false"); state().set("lens", "false");
        state().set("density", "0"); state().set("halo", "0"); state().set("shellStrength", "0"); state().set("coreGlow", "0");
    }

    private static void configureShellOnly() {
        configureWhite(); state().set("shellEnabled","true"); state().set("particles", "false"); state().set("lens", "false");
        state().set("density", "0"); state().set("halo", "0"); state().set("jetStrength", "0"); state().set("coreGlow", "0");
    }

    private static void configureCoreGlowOnly() {
        configureWhite(); state().set("core",".58"); state().set("coreEmission","4"); state().set("coreGlow","1.2"); state().set("coreGlowRadius","2.6"); state().set("particles", "false"); state().set("lens", "false");
        state().set("density", "0"); state().set("halo", "0"); state().set("jetStrength", "0"); state().set("shellStrength", "0");
    }

    private static void useFloatingCenter() {
        // Explicit fixture-only world placement. Ground anchoring was tested by
        // the earlier real preview stages; this avoids hiding the lower jet underground.
        fixedCenter = groundCenter.add(0, 10, 0); BlackHoleClient.center = fixedCenter;
        placementKind = "fixture-manual-floating-center-from-real-ground-anchor-plus-y10";
    }

    private static void repreviewAtSameAnchor(Minecraft mc) {
        mc.player.setPos(0, 127, 0);
        float pitch = (float) Math.toDegrees(Math.atan2(mc.player.getEyePosition().y - 120, expectedHit.z));
        mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
        BlackHoleClient.preview();
        check("white-repreview-restores-anchor", fixedCenter.distanceTo(BlackHoleClient.center) < .0001);
        // Preserve the freshly placed value; do not overwrite it with the earlier expected center.
        fixedCenter = BlackHoleClient.center;
        placementKind = "real-ground-preview";
    }

    private static void camera(Minecraft mc, Vec3 eye, Vec3 target) {
        eyePosition = eye;
        Vec3 direction = target.subtract(eye);
        targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        targetPitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        Vec3 position = eye.add(0, -mc.player.getEyeHeight(), 0);
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            for (var player : server.getPlayerList().getPlayers())
                player.teleportTo(server.overworld(), position.x, position.y, position.z, targetYaw, targetPitch);
        });
        holdCamera(mc);
    }

    private static void holdCamera(Minecraft mc) {
        if (eyePosition == null) return;
        mc.player.setPos(eyePosition.add(0, -mc.player.getEyeHeight(), 0));
        mc.player.setDeltaMovement(Vec3.ZERO); mc.player.setNoGravity(true);
        mc.player.setYRot(targetYaw); mc.player.yRotO = targetYaw;
        mc.player.setXRot(targetPitch); mc.player.xRotO = targetPitch;
    }

    private static String caseName(int index) { return "white-outflow-" + CASES[index]; }
    private static String name() { return caseName(stage); }
    private static Object field(Object instance, String key) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(instance);
    }
    private static void check(String name, boolean passed) {
        JsonObject result = new JsonObject(); result.addProperty("name", name); result.addProperty("passed", passed); checks.add(result);
        if (!passed) throw new IllegalStateException("White-hole fixture check failed: " + name);
    }

    /** Camera and parameters are recorded before the same frame's AFTER_LEVEL compositor. */
    @SubscribeEvent
    public static void frame(RenderLevelStageEvent event) {
        if (!enabled() || done || pendingFrame == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); String name = pendingFrame; pendingFrame = null;
        try {
            var data = new LinkedHashMap<String, Object>();
            data.put("fixture", FIXTURE); data.put("name", name); data.put("save", SAVE);
            data.put("seed", state().getDouble("seed")); data.put("configuredTimeSeconds", state().getDouble("time"));
            data.put("playing", state().getBool("playing")); data.put("frozenTicks", BlackHoleRender.frozenTime);
            data.put("fixedCenter", fixedCenter); data.put("centerWasFixed", fixedCenter.equals(BlackHoleClient.center));
            data.put("placementKind", placementKind); data.put("groundPreviewCenter", groundCenter);
            data.put("appearanceKind", stage == 0 || stage == 1 || stage == 2 || stage == 3 || stage == FLOAT_T8 || stage == FLOAT_T9
                ? "production-white-ring-default-except-paused-time-and-world-placement" : "explicit-regression-configuration");
            if (stage == INTERIOR) data.put("interiorCameraLocalRadius", 1.2);
            if (stage == WALL_ON || stage == WALL_OFF) {
                data.put("wallMinimum", new BlockPos(-14, 119, wallZ)); data.put("wallMaximum", new BlockPos(14, 150, wallZ));
                data.put("wallEditThread", "integrated server; completion then 12 client ticks plus standard capture settling");
            }
            data.put("groundHit", expectedHit); data.put("rawCamera", event.getCamera().getPosition());
            data.put("playerPosition", mc.player.position()); data.put("pitch", mc.player.getXRot()); data.put("yaw", mc.player.getYRot());
            data.put("terrainViewProjection", new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).get(new float[16]));
            data.put("worldShaderLoaded", BlackHoleRender.world != null); data.put("particleShaderLoaded", BlackHoleParticles.shader != null);
            data.put("parameters", state().snapshot()); data.put("worldDraws", BlackHoleClient.worldDraws); data.put("particleDraws", BlackHoleParticles.draws);
            if (stage == DIRTY_UNPACK) {
                unpackOriginal = new int[UNPACK_KEYS.length]; unpackExpected = new int[UNPACK_KEYS.length];
                unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
                for (int i = 0; i < UNPACK_KEYS.length; i++) unpackOriginal[i] = GL11.glGetInteger(UNPACK_KEYS[i]);
                System.arraycopy(unpackOriginal, 0, unpackExpected, 0, UNPACK_KEYS.length);
                unpackExpected[0] = 1024; unpackExpected[1] = 3; unpackExpected[2] = 11;
                unpackExpected[3] = 8; unpackExpected[4] = 1;
                for (int i = 0; i < UNPACK_KEYS.length; i++) GL11.glPixelStorei(UNPACK_KEYS[i], unpackExpected[i]);
                var polluted = new LinkedHashMap<String, Object>();
                for (int i = 0; i < UNPACK_KEYS.length; i++) polluted.put(UNPACK_NAMES[i], unpackExpected[i]);
                polluted.put("pixelUnpackBuffer", unpackBuffer); data.put("inheritedUnpackState", polluted);
                data.put("unpackInjectionStage", "AFTER_PARTICLES before actual AFTER_LEVEL foreground texture upload");
            }
            Files.writeString(folder.resolve(name + "-fixture.json"), new GsonBuilder().setPrettyPrinting().create().toJson(data));
        } catch (Throwable failure) { finish(mc, failure); }
    }

    /** Observe production restoration in the same frame, then return external state even on failure. */
    @SubscribeEvent
    public static void unpackFrameEnd(TickEvent.RenderTickEvent event) {
        if (!enabled() || done || unpackOriginal == null || event.phase != TickEvent.Phase.END) return;
        Throwable failure = null;
        try {
            for (int i = 0; i < UNPACK_KEYS.length; i++)
                check("dirty-unpack-restored-" + UNPACK_NAMES[i], GL11.glGetInteger(UNPACK_KEYS[i]) == unpackExpected[i]);
            check("dirty-unpack-restored-pbo-binding", GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == unpackBuffer);
            check("dirty-unpack-gl-error-zero", GL11.glGetError() == GL11.GL_NO_ERROR);
        } catch (Throwable exception) { failure = exception; }
        finally { restoreUnpack(); }
        if (failure != null) finish(Minecraft.getInstance(), failure);
    }

    private static void restoreUnpack() {
        if (unpackOriginal == null) return;
        for (int i = 0; i < UNPACK_KEYS.length; i++) GL11.glPixelStorei(UNPACK_KEYS[i], unpackOriginal[i]);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        unpackOriginal = null; unpackExpected = null;
    }

    @SubscribeEvent
    public static void screenshot(TickEvent.RenderTickEvent event) {
        if (!enabled() || done || pendingScreenshot == null || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance(); String name = pendingScreenshot; pendingScreenshot = null;
        try {
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(folder.resolve(name + ".png")); }
            JsonObject capture = new JsonObject(); capture.addProperty("name", name); capture.addProperty("kind", stage == OFF ? "effect-off" : "real-workbench-ui");
            capture.addProperty("worldShaderLoaded", BlackHoleRender.world != null); capture.addProperty("particleShaderLoaded", BlackHoleParticles.shader != null);
            capture.addProperty("width", mc.getMainRenderTarget().width); capture.addProperty("height", mc.getMainRenderTarget().height);
            capture.add("parameters", new GsonBuilder().create().toJsonTree(state().snapshot())); captures.add(capture);
            Files.writeString(folder.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(capture));
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void verifyProbe(String name) throws Exception {
        Path file = probeFolder.resolve(name + ".json");
        check(name + "-same-frame-world-probe-written", Files.isRegularFile(file));
        JsonObject probe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        check(name + "-gl-error-zero", probe.get("glError").getAsInt() == GL11.GL_NO_ERROR);
        check(name + "-depth-unmodified", probe.get("maxDepthDifference").getAsDouble() == 0);
        if (stage < WALL_ON) check(name + "-visible-world-effect", probe.get("changedPixels").getAsInt() > 100);
        check(name + "-actual-camera-contract", probe.get("cameraContract").getAsString().equals("perspective-eye-from-inverse-vp/v1"));
        var actualEye = probe.getAsJsonArray("effectiveCamera");
        Vec3 reconstructedEye = new Vec3(actualEye.get(0).getAsDouble(), actualEye.get(1).getAsDouble(), actualEye.get(2).getAsDouble());
        check(name + "-actual-camera-matches-held-eye", reconstructedEye.distanceTo(eyePosition) < .002);
        if (probe.getAsJsonObject("parameters").get("particles").getAsString().equals("true"))
            check(name + "-particle-pass-visible", probe.get("particlePixels").getAsInt() > 0);
        verifyCoreBloomProbe(name, probe);
        JsonObject capture = new JsonObject(); capture.addProperty("name", name); capture.addProperty("kind", "real-world-and-particle-shader");
        capture.addProperty("worldProbe", file.toString()); capture.addProperty("changedPixels", probe.get("changedPixels").getAsInt());
        capture.addProperty("particlePixels", probe.get("particlePixels").getAsInt()); capture.addProperty("generation", probe.get("generation").getAsInt());
        capture.addProperty("glError", probe.get("glError").getAsInt()); capture.add("coreBloom", probe.getAsJsonObject("coreBloom").deepCopy()); captures.add(capture);
    }

    private static void verifyCoreBloomProbe(String name, JsonObject probe) throws Exception {
        check(name + "-core-bloom-actual-frame-metadata", probe.has("coreBloom") && probe.get("coreBloom").isJsonObject());
        JsonObject bloom = probe.getAsJsonObject("coreBloom");
        int width = probe.get("width").getAsInt(), height = probe.get("height").getAsInt();
        check(name + "-core-bloom-hdr-half-resolution-contract", bloom.get("format").getAsInt() == 34842
            && bloom.get("width").getAsInt() == (width + 1) / 2 && bloom.get("height").getAsInt() == (height + 1) / 2);
        boolean expectedActive = state().get("objectMode").equals("white") && state().getDouble("coreGlow") > 0
            && state().getDouble("coreEmission") > 0;
        check(name + "-core-bloom-mode-and-gain-contract", bloom.get("active").getAsBoolean() == expectedActive);
        if (expectedActive) {
            check(name + "-core-bloom-retains-hdr-core-source", bloom.get("sourcePixels").getAsLong() > 0 && bloom.get("sourcePeak").getAsDouble() > 1);
            check(name + "-core-bloom-projects-positive-screen-radius", bloom.get("radiusPixels").getAsDouble() > 0);
        } else {
            check(name + "-core-bloom-disabled-has-no-source", bloom.get("sourcePixels").getAsLong() == 0 && bloom.get("sourcePeak").getAsDouble() == 0);
            check(name + "-core-bloom-disabled-has-no-compositor-contribution", bloom.get("contributionPixels").getAsLong() == 0);
        }
        if (stage == CORE_GLOW_ON || stage == CORE_FAR_ON || stage == CORE_RESIZE_ON || stage == CORE_RESTORE_ON)
            check(name + "-core-bloom-actual-compositor-adds-visible-pixels", bloom.get("contributionPixels").getAsLong() > 25);
        check(name + "-core-emission-image-written-this-probe", Files.isRegularFile(probeFolder.resolve(name + "-core-emission.png")));
        try (NativeImage source = probeImage(name, "-core-emission.png")) {
            check(name + "-core-emission-image-matches-actual-source-target", source.getWidth() == bloom.get("width").getAsInt()
                && source.getHeight() == bloom.get("height").getAsInt());
            long nonzero = 0;
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
                if ((source.getPixelRGBA(x, y) & 0x00ffffff) != 0) nonzero++;
            check(name + "-core-emission-image-agrees-with-source-activation", expectedActive ? nonzero > 0 : nonzero == 0);
        }
        if (stage == CORE_RESIZE_ON) {
            JsonObject previous = readProbe(caseName(CORE_GLOW_ON));
            check("core-bloom-resize-reconstructs-actual-framebuffer-size", width != previous.get("width").getAsInt() || height != previous.get("height").getAsInt());
        }
        if (stage == CORE_RESTORE_ON) {
            JsonObject original = readProbe(caseName(CORE_GLOW_ON));
            check("core-bloom-resize-restores-original-framebuffer-size", width == original.get("width").getAsInt() && height == original.get("height").getAsInt());
        }
    }

    private static JsonObject readProbe(String name) throws Exception {
        return JsonParser.parseString(Files.readString(probeFolder.resolve(name + ".json"))).getAsJsonObject();
    }

    private static long pixelDifference(String a, String b, String suffix) throws Exception {
        try (var inA = Files.newInputStream(probeFolder.resolve(a + suffix)); var inB = Files.newInputStream(probeFolder.resolve(b + suffix));
             NativeImage imA = NativeImage.read(inA); NativeImage imB = NativeImage.read(inB);
             var sceneAStream=Files.newInputStream(probeFolder.resolve(a+"-before.png"));
             var sceneBStream=Files.newInputStream(probeFolder.resolve(b+"-before.png"));
             NativeImage sceneA=NativeImage.read(sceneAStream);NativeImage sceneB=NativeImage.read(sceneBStream)) {
            if (imA.getWidth() != imB.getWidth() || imA.getHeight() != imB.getHeight()) throw new IllegalStateException("Capture dimensions changed");
            long changed = 0;
            for (int y = 0; y < imA.getHeight(); y++) for (int x = 0; x < imA.getWidth(); x++) {
                int first=imA.getPixelRGBA(x,y),second=imB.getPixelRGBA(x,y);
                if(first==second)continue;
                // Ignore only scene changes that the effect leaves byte-for-byte
                // untouched in BOTH frames. Lensed, emissive and particle pixels
                // remain subject to the original strict comparison.
                if(first==sceneA.getPixelRGBA(x,y)&&second==sceneB.getPixelRGBA(x,y))continue;
                changed++;
            }
            return changed;
        }
    }

    private static double coreLuminance(String name) throws Exception {
        var probe = JsonParser.parseString(Files.readString(probeFolder.resolve(name + ".json"))).getAsJsonObject();
        var point = probe.getAsJsonObject("projectedCenter");
        try (var stream = Files.newInputStream(probeFolder.resolve(name + "-after.png")); NativeImage image = NativeImage.read(stream)) {
            int cx = (int) Math.round(point.get("pixelX").getAsDouble());
            int cy = image.getHeight() - 1 - (int) Math.round(point.get("pixelY").getAsDouble());
            double sum = 0; int count = 0;
            for (int y = Math.max(0, cy - 2); y <= Math.min(image.getHeight() - 1, cy + 2); y++)
                for (int x = Math.max(0, cx - 2); x <= Math.min(image.getWidth() - 1, cx + 2); x++) {
                    int rgba = image.getPixelRGBA(x, y); sum += ((rgba & 255) + ((rgba >> 8) & 255) + ((rgba >> 16) & 255)) / 3.0; count++;
                }
            return sum / Math.max(1, count);
        }
    }

    private static NativeImage probeImage(String name, String suffix) throws Exception {
        try (var stream = Files.newInputStream(probeFolder.resolve(name + suffix))) { return NativeImage.read(stream); }
    }

    private static boolean rgbChanged(int first, int second) {
        for (int shift = 0; shift < 24; shift += 8) if (Math.abs(((first >> shift) & 255) - ((second >> shift) & 255)) > 1) return true;
        return false;
    }

    /** Gain zero changes the body to black. Compare the glow outside the actual
     * body bounds, rather than misclassifying that expected body change as glow. */
    private static long zeroGainOuterContribution(JsonObject comparisons, int offIndex, int zeroIndex, String label) throws Exception {
        try (NativeImage body = probeImage(caseName(offIndex), "-field.png");
             NativeImage bodyScene = probeImage(caseName(offIndex), "-before.png");
             NativeImage zero = probeImage(caseName(zeroIndex), "-field.png");
             NativeImage zeroScene = probeImage(caseName(zeroIndex), "-before.png")) {
            int width = body.getWidth(), height = body.getHeight();
            check(label + "-core-radiance-masked-comparison-dimensions-stable", bodyScene.getWidth() == width && bodyScene.getHeight() == height
                && zero.getWidth() == width && zero.getHeight() == height && zeroScene.getWidth() == width && zeroScene.getHeight() == height);
            int minX = width, minY = height, maxX = -1, maxY = -1;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if (rgbChanged(body.getPixelRGBA(x, y), bodyScene.getPixelRGBA(x, y))) {
                minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
            check(label + "-radiance-off-body-mask-is-localized-and-nonempty", maxX >= minX && maxY >= minY && maxX - minX < width / 3 && maxY - minY < height / 3);
            minX = Math.max(0, minX - 4); minY = Math.max(0, minY - 4);
            maxX = Math.min(width - 1, maxX + 4); maxY = Math.min(height - 1, maxY + 4);
            JsonArray bounds = new JsonArray(); bounds.add(minX); bounds.add(minY); bounds.add(maxX); bounds.add(maxY);
            comparisons.add(label + "ZeroGainExcludedBodyPixelBounds", bounds);
            comparisons.addProperty(label + "ZeroGainMaskPolicy", "actual gain4/radiance-off body versus scene RGB bounds expanded 4 pixels; alpha ignored; remaining background RGB tolerance 1");
            long changed = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                if ((x < minX || x > maxX || y < minY || y > maxY) && rgbChanged(zero.getPixelRGBA(x, y), zeroScene.getPixelRGBA(x, y))) changed++;
            return changed;
        }
    }

    /** Source is captured before blur in the actual HDR half-resolution target.
     * A visible on/off difference beyond its mapped bounds proves screen diffusion,
     * rather than mistaking an emissive sphere or its volume corona for Bloom. */
    private static void verifyCoreRadiancePixels(JsonObject comparisons, int onIndex, int offIndex, String label) throws Exception {
        try (NativeImage source = probeImage(caseName(onIndex), "-core-emission.png");
             NativeImage on = probeImage(caseName(onIndex), "-field.png");
             NativeImage preBloom = probeImage(caseName(onIndex), "-pre-core-bloom.png");
             NativeImage off = probeImage(caseName(offIndex), "-field.png")) {
            check(label + "-radiance-on-off-frame-dimensions-stable", on.getWidth() == off.getWidth() && on.getHeight() == off.getHeight()
                && on.getWidth() == preBloom.getWidth() && on.getHeight() == preBloom.getHeight());
            int minX = source.getWidth(), minY = source.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
                if ((source.getPixelRGBA(x, y) & 0x00ffffff) != 0) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            check(label + "-radiance-source-pixel-bounds-nonempty", maxX >= minX && maxY >= minY);
            double scaleX = (double) on.getWidth() / source.getWidth(), scaleY = (double) on.getHeight() / source.getHeight();
            int mappedMinX = Math.max(0, (int) Math.floor(minX * scaleX) - 2);
            int mappedMinY = Math.max(0, (int) Math.floor(minY * scaleY) - 2);
            int mappedMaxX = Math.min(on.getWidth() - 1, (int) Math.ceil((maxX + 1) * scaleX) + 2);
            int mappedMaxY = Math.min(on.getHeight() - 1, (int) Math.ceil((maxY + 1) * scaleY) + 2);
            long beyondSource = 0;
            for (int y = 0; y < on.getHeight(); y++) for (int x = 0; x < on.getWidth(); x++)
                if ((x < mappedMinX || x > mappedMaxX || y < mappedMinY || y > mappedMaxY)
                    && rgbChanged(on.getPixelRGBA(x, y), preBloom.getPixelRGBA(x, y))) beyondSource++;
            JsonArray bounds = new JsonArray(); bounds.add(mappedMinX); bounds.add(mappedMinY); bounds.add(mappedMaxX); bounds.add(mappedMaxY);
            comparisons.add(label + "MappedHdrSourcePixelBounds", bounds);
            comparisons.addProperty(label + "CoreDiffusionOutsideSourcePixels", beyondSource);
            comparisons.addProperty(label + "CoreDiffusionComparisonPolicy", "same-frame final field versus pre-core-bloom image outside mapped unblurred HDR source bounds plus 2 pixels; volume corona already present in both; RGB tolerance 1");
            check(label + "-core-screen-diffusion-visible-outside-unblurred-source", beyondSource > 25);
            JsonObject point = readProbe(caseName(onIndex)).getAsJsonObject("projectedCenter");
            int cx = (int) Math.round(point.get("pixelX").getAsDouble());
            int cy = on.getHeight() - 1 - (int) Math.round(point.get("pixelY").getAsDouble());
            double sum = 0; int count = 0, minimumChannel = 255;
            for (int y = Math.max(0, cy - 2); y <= Math.min(on.getHeight() - 1, cy + 2); y++)
                for (int x = Math.max(0, cx - 2); x <= Math.min(on.getWidth() - 1, cx + 2); x++) {
                    int rgba = on.getPixelRGBA(x, y);
                    for (int shift = 0; shift < 24; shift += 8) {
                        int channel = (rgba >> shift) & 255; sum += channel; count++; minimumChannel = Math.min(minimumChannel, channel);
                    }
                }
            double mean = sum / Math.max(1, count);
            comparisons.addProperty(label + "LuminousCoreMeanRgb", mean); comparisons.addProperty(label + "LuminousCoreMinimumChannel", minimumChannel);
            check(label + "-core-itself-reads-as-bright-white-light", mean > 200 && minimumChannel > 180);
        }
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return; done = true;
        JsonObject comparisons = new JsonObject();
        try {
            if (failure == null) {
                long evolving = pixelDifference(caseName(0), caseName(1), "-after.png");
                long paused = pixelDifference(caseName(4), caseName(5), "-after.png");
                long hotFreeze = pixelDifference(caseName(6), caseName(ZERO_START), "-after.png");
                long zeroSpeed = pixelDifference(caseName(ZERO_START), caseName(8), "-after.png");
                long hotRestore = pixelDifference(caseName(6), caseName(HOT_RESTORE), "-after.png");
                long dirtyUnpack = pixelDifference(caseName(RELOAD), caseName(DIRTY_UNPACK), "-after.png");
                long floating = pixelDifference(caseName(FLOAT_T8), caseName(FLOAT_T9), "-after.png");
                long jetMotion = pixelDifference(caseName(JET_T8), caseName(JET_T9), "-field.png");
                long jetEnabled = pixelDifference(caseName(JET_T8), caseName(JET_OFF), "-field.png");
                long shellMotion = pixelDifference(caseName(SHELL_T8), caseName(SHELL_T9), "-field.png");
                long shellEnabled = pixelDifference(caseName(SHELL_T8), caseName(SHELL_OFF), "-field.png");
                long coreGlowEnabled = pixelDifference(caseName(CORE_GLOW_ON), caseName(CORE_GLOW_OFF), "-field.png");
                long zeroGainGlow = zeroGainOuterContribution(comparisons, CORE_GLOW_OFF, CORE_GAIN_ZERO, "near");
                long farZeroGainGlow = zeroGainOuterContribution(comparisons, CORE_FAR_OFF, CORE_FAR_ZERO, "far");
                long farRadiance = pixelDifference(caseName(CORE_FAR_ON), caseName(CORE_FAR_OFF), "-field.png");
                long resizedRadiance = pixelDifference(caseName(CORE_RESIZE_ON), caseName(CORE_RESIZE_OFF), "-field.png");
                long restoredRadiance = pixelDifference(caseName(CORE_GLOW_ON), caseName(CORE_RESTORE_ON), "-field.png");
                long wallExternal = pixelDifference(caseName(WALL_ON), caseName(WALL_OFF), "-field.png");
                double whiteCore = coreLuminance(caseName(0)), blackCore = coreLuminance(caseName(LEGACY));
                double zeroGainCore = coreLuminance(caseName(CORE_GAIN_ZERO));
                comparisons.addProperty("timePairChangedPixels", evolving); comparisons.addProperty("pausedPairChangedPixels", paused);
                comparisons.addProperty("zeroSpeedPairChangedPixels", zeroSpeed); comparisons.addProperty("whiteCoreMeanRgb", whiteCore); comparisons.addProperty("blackCoreMeanRgb", blackCore);
                comparisons.addProperty("hotFreezeChangedPixels", hotFreeze); comparisons.addProperty("hotRestoreChangedPixels", hotRestore);
                comparisons.addProperty("dirtyUnpackChangedPixels", dirtyUnpack);
                comparisons.addProperty("floatingTimePairChangedPixels", floating);
                comparisons.addProperty("jetOnlyTimePairChangedPixels", jetMotion); comparisons.addProperty("jetEnabledContributionPixels", jetEnabled);
                comparisons.addProperty("shellOnlyTimePairChangedPixels", shellMotion); comparisons.addProperty("shellEnabledContributionPixels", shellEnabled);
                comparisons.addProperty("coreGlowEnabledContributionPixels", coreGlowEnabled);
                comparisons.addProperty("coreGainZeroOutsideBodyContributionPixels", zeroGainGlow); comparisons.addProperty("coreGainZeroMeanRgb", zeroGainCore);
                comparisons.addProperty("farCoreGainZeroOutsideBodyContributionPixels", farZeroGainGlow);
                comparisons.addProperty("farCoreRadianceContributionPixels", farRadiance);
                comparisons.addProperty("resizedCoreRadianceContributionPixels", resizedRadiance);
                comparisons.addProperty("restoredCoreRadianceChangedPixels", restoredRadiance);
                comparisons.addProperty("wallOccludedJetShellContributionPixels", wallExternal);
                comparisons.addProperty("wallComparisonPolicy", "core radiance disabled in both frames; depth-clipped jet/shell differences only; core artwork retains its established terrain policy");
                long dustOblique=pixelDifference(caseName(39),caseName(40),"-field.png"),dustTop=pixelDifference(caseName(41),caseName(42),"-field.png"),dustLate=pixelDifference(caseName(43),caseName(44),"-field.png");
                long switched=pixelDifference(caseName(45),caseName(46),"-field.png"),reenabled=pixelDifference(caseName(45),caseName(47),"-field.png");
                comparisons.addProperty("dustObliqueContributionPixels",dustOblique);comparisons.addProperty("dustTopContributionPixels",dustTop);comparisons.addProperty("dustLateContributionPixels",dustLate);
                comparisons.addProperty("shellToggleContributionPixels",switched);comparisons.addProperty("shellReenabledDifferencePixels",reenabled);
                check("dust-visible-with-particles-off-oblique",dustOblique>100);check("dust-visible-top",dustTop>100);check("dust-visible-at-user-long-time",dustLate>100);
                check("shell-toggle-controls-real-render",switched>100);check("shell-toggle-restores-identical-render",reenabled==0);
                check("time-advances-visible-outflow", evolving > 100);
                check("paused-frame-repeatable", paused == 0);
                check("all-motion-speed-zero-repeatable", zeroSpeed == 0);
                check("hot-speed-zero-keeps-existing-phases", hotFreeze == 0);
                check("hot-speed-restore-keeps-existing-phases", hotRestore == 0);
                check("dirty-unpack-keeps-standard-frame", dirtyUnpack == 0);
                check("white-core-emission-visible", whiteCore > 80);
                check("legacy-core-remains-black", blackCore < 35);
                check("floating-bipolar-default-evolves-visibly", floating > 100);
                check("jet-only-transports-visibly", jetMotion > 100);
                check("jet-enabled-adds-visible-contribution", jetEnabled > 100);
                check("shell-only-expands-visibly", shellMotion > 100);
                check("shell-enabled-adds-visible-contribution", shellEnabled > 100);
                check("core-glow-adds-visible-contribution-without-shell-halo-disk-jets-or-particles", coreGlowEnabled > 100);
                check("core-gain-zero-removes-external-core-glow", zeroGainGlow == 0);
                check("core-gain-zero-body-remains-black", zeroGainCore < 35);
                check("far-core-radiance-adds-visible-contribution", farRadiance > 100);
                check("far-core-gain-zero-removes-external-core-radiance", farZeroGainGlow == 0);
                check("resized-core-radiance-adds-visible-contribution", resizedRadiance > 100);
                check("restoring-framebuffer-size-restores-identical-core-radiance", restoredRadiance == 0);
                verifyCoreRadiancePixels(comparisons, CORE_GLOW_ON, CORE_GLOW_OFF, "near");
                verifyCoreRadiancePixels(comparisons, CORE_FAR_ON, CORE_FAR_OFF, "far");
                verifyCoreRadiancePixels(comparisons, CORE_RESIZE_ON, CORE_RESIZE_OFF, "resized");
                check("wall-occludes-depth-clipped-jet-shell-contribution", wallExternal == 0);
            }
        } catch (Throwable validationFailure) { failure = validationFailure; }
        try {
            restoreUnpack(); mc.setScreen(null); BlackHoleClient.stop(); BlackHoleRender.frozenTime = -1; BlackHoleParticles.debug = 0;
            if (parameterBackup != null) state().load(BlackHoleSkillSchema.recipeParameters(parameterBackup));
            if (optionsSaved) {
                mc.options.bobView().set(oldBob); mc.options.hideGui = oldHide; mc.options.pauseOnLostFocus = oldPause;
                mc.options.entityShadows().set(oldShadows); mc.options.cloudStatus().set(oldClouds);
                mc.options.setCameraType(oldCamera); mc.options.renderDistance().set(oldDistance); mc.options.guiScale().set(oldGuiScale);
                mc.getWindow().setWindowed(oldWindowWidth, oldWindowHeight);
                GLFW.glfwSetWindowPos(mc.getWindow().getWindow(), oldWindowX, oldWindowY);
                if (oldFullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
                mc.resizeDisplay();
            }
            if (preferenceFile != null) {
                if (preferenceBackup == null) Files.deleteIfExists(preferenceFile); else Files.write(preferenceFile, preferenceBackup);
            }
            JsonObject report = new JsonObject(); report.addProperty("fixture", FIXTURE);
            report.addProperty("status", failure == null ? "passed" : "failed"); report.addProperty("save", SAVE);
            report.addProperty("caseCount", CASES.length); report.addProperty("completedCaseCount", stage);
            report.addProperty("worldProbeDirectory", probeFolder == null ? "" : probeFolder.toString());
            report.addProperty("onlyFixtureSaveChanged", true); report.addProperty("optionsAndParametersRestored", optionsSaved);
            report.addProperty("windowDimensionsAndFullscreenPreferenceRestored", optionsSaved);
            report.addProperty("coreRadianceVerification", "actual core-only GL_RGBA16F source, isolated near/far and rebuilt viewport on/off captures, source-footprint exterior diffusion, core brightness, gain-zero and black-mode no-source");
            report.addProperty("wallOcclusionScope", "depth-clipped jets/shells with core radiance off in both captures; core artistic terrain policy is not covered by this comparison");
            report.add("checks", checks); report.add("captures", captures); report.add("comparisons", comparisons);
            if (failure != null) { report.addProperty("failure", failure.toString()); failure.printStackTrace(); }
            if (folder != null) Files.writeString(folder.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("[WHITE-HOLE] " + (failure == null ? "COMPLETE" : "FAILED: " + failure));
        } catch (Throwable restorationFailure) { restorationFailure.printStackTrace(); }
        finally { mc.stop(); }
    }
}
