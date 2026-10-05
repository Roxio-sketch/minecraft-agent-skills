package dev.vfxworkbench.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Save / load / export for parameter presets. The export format is plain JSON
 *  and remains consumable by the production mod: values are raw strings. */
public final class PresetManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PresetManager() {}

    public static Path presetDir(Path gameDir, String skillId) {
        return gameDir.resolve("config").resolve("vfxworkbench").resolve("presets").resolve(skillId);
    }

    public static void save(Path gameDir, String skillId, String presetName, ParamState state) {
        try {
            Path dir = presetDir(gameDir, skillId);
            Files.createDirectories(dir);
            JsonObject root = new JsonObject();
            root.addProperty("preset", presetName);
            JsonObject values = new JsonObject();
            for (Map.Entry<String, String> e : state.snapshot().entrySet()) values.addProperty(e.getKey(), e.getValue());
            root.add("values", values);
            Files.writeString(dir.resolve(safe(presetName) + ".json"), GSON.toJson(root));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save preset: " + e.getMessage(), e);
        }
    }

    /** Loads a preset into the state. Returns the preset name on success. */
    public static String load(Path gameDir, String skillId, String presetName, ParamState state) throws IOException {
        Path file = presetDir(gameDir, skillId).resolve(safe(presetName) + ".json");
        // A first launch has no last.json yet. Treat that state as a normal
        // default reset instead of surfacing a file-not-found error from the UI.
        if (!Files.isRegularFile(file)) {
            state.resetToDefaults();
            return presetName;
        }
        JsonElement parsed = GSON.fromJson(Files.readString(file), JsonElement.class);
        if (!parsed.isJsonObject()) throw new IOException("预设文件必须是 JSON 对象");
        JsonObject root = parsed.getAsJsonObject();
        JsonObject values = root.has("values") && root.get("values").isJsonObject() ? root.getAsJsonObject("values") : root;
        Map<String, String> map = new LinkedHashMap<>();
        values.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        state.load(skillId.equals(dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.ID)
            ?dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.recipeParameters(map):map);
        return root.has("preset") ? root.get("preset").getAsString() : presetName;
    }

    public static String exportJson(ParamState state) {
        JsonObject values = new JsonObject();
        for (Map.Entry<String, String> e : state.snapshot().entrySet()) values.addProperty(e.getKey(), e.getValue());
        return GSON.toJson(values);
    }

    private static String safe(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
