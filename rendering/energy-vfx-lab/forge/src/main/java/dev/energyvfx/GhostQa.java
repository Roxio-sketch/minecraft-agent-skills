package dev.energyvfx;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Opt-in blue aura QA in a freshly created dev world. Never opens existing saves. */
@Mod.EventBusSubscriber(modid = EnergyVfx.ID, value = Dist.CLIENT)
public final class GhostQa {
    private static final boolean ENABLED = Boolean.getBoolean("energyvfx.ghostQa");
    private static final Path OUTPUT = Path.of(System.getProperty("energyvfx.qa.output", "validation")).resolve("ghost");
    private static final String[] NAMES = {
            "effect-off", "blue-body-equipment", "movement-detached-trail",
            "gear-only", "first-person", "resource-reload", "moon-white-veil", "ice-blue-veil",
            "first-person-blue-veil"
    };
    private static final double START_X = .5, START_Y = 7, START_Z = .5;
    private static final JsonArray RESULTS = new JsonArray();
    private static boolean started, initialized, ready, done, pendingAdvance;
    private static int menuTicks, scenario, caseTicks, frames, initialDraws;
    private static double caseDriftPeak, caseAnchorDistancePeak;
    private static long beganNanos;
    private static String worldName;
    private static JsonObject ghostRaw;
    private static CompletableFuture<Void> setupFuture, reloadFuture;

    private GhostQa() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (beganNanos == 0) beganNanos = System.nanoTime();
            if (System.nanoTime() - beganNanos > 240_000_000_000L)
                throw new IllegalStateException("Ghost QA exceeded four minutes");

            if (!started) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || ++menuTicks < 30) return;
                started = true;
                Files.createDirectories(OUTPUT);
                mc.options.pauseOnLostFocus = false;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                worldName = "ENERGY_GHOST_QA_" + System.currentTimeMillis();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(43, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }

            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (!initialized) {
                initialized = true;
                mc.options.hideGui = true;
                mc.options.bobView().set(false);
                mc.options.fovEffectScale().set(0.);
                mc.options.renderDistance().set(3);
                mc.options.cloudStatus().set(CloudStatus.OFF);
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(60);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.setScreen(null);
                ghostRaw = Recipe.parse(Recipe.resource("ghost-blue.json")).getAsJsonObject();
                Recipe.decode(ghostRaw);
                setupFuture = new CompletableFuture<>();
                var server = mc.getSingleplayerServer();
                var playerId = mc.player.getUUID();
                server.execute(() -> {
                    try {
                        var level = server.overworld();
                        level.setDayTime(6000);
                        level.setWeatherParameters(12000, 0, false, false);
                        // A neutral backdrop belongs only to this newly created QA world.
                        for (BlockPos pos : BlockPos.betweenClosed(-7, 4, -7, 7, 13, -7))
                            level.setBlockAndUpdate(pos, Blocks.GRAY_CONCRETE.defaultBlockState());
                        var player = server.getPlayerList().getPlayer(playerId);
                        if (player == null) throw new IllegalStateException("QA server player missing");
                        player.getAbilities().flying = true;
                        player.getAbilities().mayfly = true;
                        player.onUpdateAbilities();
                        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                        player.teleportTo(START_X, START_Y, START_Z);
                        player.setYRot(0);
                        player.setXRot(0);
                        setupFuture.complete(null);
                    } catch (Exception exception) {
                        setupFuture.completeExceptionally(exception);
                    }
                });
                // Avoid automatic activation while the equipment packets are being synchronized.
                EnergyVfx.Client.owner = mc.level;
                EnergyVfx.Client.active = false;
                AuraEmitter.reset();
                return;
            }

            if (!ready) {
                if (!setupFuture.isDone()) return;
                setupFuture.join();
                if (mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                        || !mc.player.getMainHandItem().is(Items.DIAMOND_SWORD)) return;
                ready = true;
                beginCase(mc);
            }

            mc.options.pauseOnLostFocus = false;
            mc.player.getAbilities().flying = true;
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setYRot(0);
            mc.player.yRotO = 0;
            mc.player.setXRot(0);
            mc.player.xRotO = 0;
            mc.player.setYHeadRot(0);
            mc.player.yHeadRotO = 0;
            mc.player.yBodyRot = 0;
            mc.player.yBodyRotO = 0;
            if (++caseTicks > 1200) throw new IllegalStateException("Ghost QA scenario timeout: " + NAMES[scenario]);
            if (!Double.isFinite(AuraEmitter.maxDriftDistance) || !Double.isFinite(AuraEmitter.maxPacketDistance))
                throw new IllegalStateException("Non-finite packet motion in " + NAMES[scenario]);
            caseDriftPeak = Math.max(caseDriftPeak, AuraEmitter.maxDriftDistance);
            caseAnchorDistancePeak = Math.max(caseAnchorDistancePeak, AuraEmitter.maxPacketDistance);

            if (scenario == 2) {
                // Move the actual server player, then stop; previously emitted packets stay behind.
                double x = START_X + Math.min(caseTicks, 28) * .09;
                var server = mc.getSingleplayerServer();
                var playerId = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) player.teleportTo(x, START_Y, START_Z);
                });
            }
        } catch (Exception exception) {
            fail(exception);
        }
    }

    private static void applyGhost(Minecraft mc, JsonObject raw) throws Exception {
        Recipe next = Recipe.decode(raw);
        // start() reads config. Install the isolated test recipe after that read.
        if (!EnergyVfx.Client.start()) throw new IllegalStateException("Ghost QA could not start renderer");
        EnergyVfx.Client.recipe = next;
        EnergyVfx.Client.owner = mc.level;
        EnergyVfx.Client.ticks = 0;
        EnergyVfx.Client.active = true;
        EnergyVfx.Client.failed = false;
        AuraEmitter.reset();
        AuraEmitter.bind(mc.player);
        EnergyVfx.Client.anchor = AuraEmitter.anchor(0);
    }

    private static void beginCase(Minecraft mc) throws Exception {
        frames = 0;
        caseTicks = 0;
        caseDriftPeak = caseAnchorDistancePeak = 0;
        initialDraws = EnergyVfx.Client.draws;
        boolean firstPerson = scenario == 4 || scenario == 8;
        mc.options.setCameraType(firstPerson ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
        mc.options.hideGui = !firstPerson;
        if (scenario == 0) {
            EnergyVfx.Client.active = false;
            EnergyVfx.Client.failed = false;
            EnergyVfx.Client.owner = mc.level;
            AuraEmitter.reset();
            return;
        }
        JsonObject raw = ghostRaw.deepCopy();
        if (scenario >= 6) raw=Recipe.parse(Recipe.resource("presets/"+(scenario==6?15:16)+".json")).getAsJsonObject();
        if (scenario == 3) raw.getAsJsonObject("params").addProperty("attachment", 2);
        if (scenario == 4) {
            raw.getAsJsonObject("params").addProperty("firstPerson", .08);
            raw.getAsJsonObject("params").addProperty("density", 1.5);
        }
        applyGhost(mc, raw);
        if (scenario == 5) reloadFuture = mc.reloadResourcePacks();
    }

    @SubscribeEvent
    public static void capture(RenderLevelStageEvent event) {
        if (!ENABLED || done || !ready || pendingAdvance || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.getOverlay() != null) return;
            if (reloadFuture != null) {
                if (!reloadFuture.isDone()) return;
                reloadFuture.join();
                reloadFuture = null;
                // The reload listener reads the normal config; restore the blue test recipe afterwards.
                applyGhost(mc, ghostRaw.deepCopy());
                frames = 0;
                caseTicks = 0;
                caseDriftPeak = caseAnchorDistancePeak = 0;
                initialDraws = EnergyVfx.Client.draws;
                return;
            }
            if (++frames < 90 || caseTicks < 32) return;

            Path screenshot = OUTPUT.resolve(NAMES[scenario] + ".png");
            var target = mc.getMainRenderTarget();
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._bindTexture(target.getColorTextureId());
            try (NativeImage image = new NativeImage(target.width, target.height, false)) {
                image.downloadTexture(0, false);
                image.flipY();
                image.writeToFile(screenshot);
            } finally {
                GlStateManager._bindTexture(previousTexture);
            }

            JsonObject record = new JsonObject();
            record.addProperty("case", NAMES[scenario]);
            record.addProperty("world", worldName);
            record.addProperty("renderedFrames", frames);
            record.addProperty("clientTicks", caseTicks);
            record.addProperty("draws", EnergyVfx.Client.draws);
            record.addProperty("caseDraws", EnergyVfx.Client.draws - initialDraws);
            record.addProperty("glError", EnergyVfx.Client.lastGl);
            record.addProperty("failed", EnergyVfx.Client.failed);
            record.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER));
            record.addProperty("glVersion", GL11.glGetString(GL11.GL_VERSION));
            record.addProperty("camera", mc.options.getCameraType().name());
            record.addProperty("packetCount", AuraEmitter.packetCount);
            record.addProperty("bodyPacketCount", AuraEmitter.bodyPacketCount);
            record.addProperty("gearPacketCount", AuraEmitter.gearPacketCount);
            record.addProperty("uploadedPackets", AuraEmitter.uploadCount);
            record.addProperty("firstPersonThinned", AuraEmitter.firstPersonThinned);
            record.addProperty("nearEyeHidden", AuraEmitter.nearEyeHidden);
            record.addProperty("nearEyeFaded", AuraEmitter.nearEyeFaded);
            record.addProperty("flowCacheActive", FlowCache.active);
            record.addProperty("maxPacketDistance", AuraEmitter.maxPacketDistance);
            record.addProperty("maxDriftDistance", AuraEmitter.maxDriftDistance);
            record.addProperty("caseDriftPeak", caseDriftPeak);
            record.addProperty("caseAnchorDistancePeak", caseAnchorDistancePeak);
            record.addProperty("driftMeasurement", "meters from each packet's immutable world-space birth position");
            record.addProperty("equippedSources", AuraEmitter.equippedSources);
            record.addProperty("usingRenderedPose", AuraEmitter.usingRenderedPose);
            record.addProperty("bakedGearSources", AuraEmitter.bakedGearSources);
            record.addProperty("fallbackGearSources", AuraEmitter.fallbackGearSources);
            try {
                var sourceCount = AuraEmitter.class.getDeclaredField("sourceCount");
                sourceCount.setAccessible(true);
                record.addProperty("sourceCount", sourceCount.getInt(null));
            } catch (NoSuchFieldException ignored) {
                // The optional source count is not required by the renderer's public contract.
            }
            record.addProperty("playerX", mc.player.getX());
            record.addProperty("playerY", mc.player.getY());
            record.addProperty("playerZ", mc.player.getZ());
            if (EnergyVfx.Client.recipe != null) {
                record.addProperty("recipe", EnergyVfx.Client.recipe.name);
                record.addProperty("attachment", EnergyVfx.Client.recipe.values[39]);
                record.addProperty("firstPersonStrength", EnergyVfx.Client.recipe.values[46]);
                record.addProperty("volumeResolution", EnergyVfx.Client.recipe.values[37]);
                record.addProperty("raySteps", EnergyVfx.Client.recipe.values[36]);
            }
            JsonObject equipment = new JsonObject();
            for (EquipmentSlot slot : EquipmentSlot.values())
                equipment.addProperty(slot.getName(), BuiltInRegistries.ITEM.getKey(mc.player.getItemBySlot(slot).getItem()).toString());
            record.add("equipment", equipment);
            record.addProperty("screenshot", screenshot.toString());
            record.addProperty("afterGuiScreenshot", OUTPUT.resolve(NAMES[scenario] + "-after-gui.png").toString());
            record.addProperty("evidence", "Counters verify execution; screenshots require separate visual review");
            RESULTS.add(record);
            writeResults();

            if (EnergyVfx.Client.failed || EnergyVfx.Client.lastGl != 0)
                throw new IllegalStateException("Ghost renderer failed in " + NAMES[scenario]);
            if (scenario > 0 && (EnergyVfx.Client.draws <= initialDraws || AuraEmitter.packetCount == 0
                    || AuraEmitter.equippedSources == 0))
                throw new IllegalStateException("Ghost renderer or equipped emitter did not execute in " + NAMES[scenario]);
            // These fixed, moderate QA recipes must visibly detach without an unstable jump.
            // A distance to the player's anchor alone cannot prove motion after birth.
            if (scenario > 0 && (caseDriftPeak <= .05 || caseDriftPeak > 8))
                throw new IllegalStateException("Packet drift missing or unstable in " + NAMES[scenario] + ": " + caseDriftPeak);
            if (scenario == 0 && (AuraEmitter.packetCount != 0 || caseDriftPeak != 0))
                throw new IllegalStateException("Packets persisted while effect was off");
            if (scenario == 1 && (AuraEmitter.bodyPacketCount == 0 || AuraEmitter.gearPacketCount == 0))
                throw new IllegalStateException("Body and equipment emission were not both sampled");
            if (scenario == 2 && (mc.player.getX() - START_X < 2 || caseAnchorDistancePeak < 1.25))
                throw new IllegalStateException("Actual player movement or detached trail was not observed");
            if (scenario == 3 && (AuraEmitter.bodyPacketCount != 0 || AuraEmitter.gearPacketCount == 0))
                throw new IllegalStateException("Gear-only emission included body packets or omitted equipment");

            // Complete the hand/HUD render before changing the camera for the next case.
            pendingAdvance = true;
        } catch (Exception exception) {
            fail(exception);
        }
    }

    @SubscribeEvent
    public static void afterGui(TickEvent.RenderTickEvent event) {
        if (!ENABLED || done || !pendingAdvance || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            PerformanceQa.screenshot(OUTPUT.resolve(NAMES[scenario] + "-after-gui.png"));
            pendingAdvance = false;
            if (++scenario == NAMES.length) {
                done = true;
                JsonObject summary = new JsonObject();
                summary.addProperty("completed", true);
                summary.addProperty("world", worldName);
                summary.addProperty("cases", RESULTS.size());
                summary.addProperty("scope", "Fresh independent dev world; no existing saves opened");
                summary.addProperty("visualReviewRequired", true);
                Files.writeString(OUTPUT.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary));
                mc.stop();
                return;
            }
            beginCase(mc);
        } catch (Exception exception) {
            fail(exception);
        }
    }

    private static void writeResults() throws Exception {
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve("game-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
    }

    private static void fail(Exception exception) {
        done = true;
        try {
            writeResults();
            Files.writeString(OUTPUT.resolve("failure.txt"), exception.toString());
            JsonObject summary = new JsonObject();
            summary.addProperty("completed", false);
            summary.addProperty("world", worldName);
            summary.addProperty("case", NAMES[Math.min(scenario, NAMES.length - 1)]);
            summary.addProperty("failure", exception.toString());
            Files.writeString(OUTPUT.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary));
        } catch (Exception ignored) {}
        com.mojang.logging.LogUtils.getLogger().error("Blue ghost integration QA failed", exception);
        Minecraft.getInstance().stop();
    }
}
