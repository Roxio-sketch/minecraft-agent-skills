package dev.cosmiclab;

import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;

/** Renderer ownership is queried from the actual Iris API, not inferred from a loaded mod. */
final class LabRenderOwner {
    static String reason = "vanilla";
    static String packName = "", pipelineName = "vanilla";
    static int handoffs;
    private static Object api;
    private static Method packInUse, shadowPass;
    private static boolean apiTried, lastHandoff;

    static boolean embeddium() { return ModList.get().isLoaded("embeddium"); }
    static boolean handoff() {
        boolean owns = false;
        try {
            if (!apiTried) {
                apiTried = true;
                if (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris")) {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = type.getMethod("getInstance").invoke(null);
                    packInUse = type.getMethod("isShaderPackInUse"); shadowPass = type.getMethod("isRenderingShadowPass");
                }
            }
            owns = api != null && ((Boolean)packInUse.invoke(api) || (Boolean)shadowPass.invoke(api));
            if (api != null) {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                packName = String.valueOf(iris.getMethod("getCurrentPackName").invoke(null));
                Object manager = iris.getMethod("getPipelineManager").invoke(null);
                Object pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
                pipelineName = pipeline == null ? "unprepared" : pipeline.getClass().getName();
            }
            reason = owns ? "shaderpack-owner-fallback" : embeddium() ? "embeddium-legacy-post" : "vanilla";
        } catch (ReflectiveOperationException | LinkageError error) {
            // Unknown Iris API has explicit ownership rather than competing for its render targets.
            owns = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
            reason = "iris-api-unavailable-owner-fallback";
        }
        if (owns && !lastHandoff) { handoffs++; System.out.println("[COSMICLAB] " + reason + " pack=" + packName); }
        lastHandoff = owns; return owns;
    }
}
