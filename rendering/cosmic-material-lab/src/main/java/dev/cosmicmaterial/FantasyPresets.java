package dev.cosmicmaterial;

import com.google.gson.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses the same preset resource as the authoring preview. */
final class FantasyPresets {
    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();
    record Color(float r, float g, float b) {
        void bind(ShaderInstance shader, String name) { shader.safeGetUniform(name).set(r, g, b); }
    }
    record Preset(String id, int mode, float seed, float scale, float speed, float detail,
                  float warp, float emission, float depth, float parallax, float exposure,
                  Color base, Color accent, Color highlight, float waveHeight, float waveLength,
                  float windAngle, float choppiness, float foam, float roughness, float waterOpacity, float waterTilt,
                  float waterLightAzimuth, float waterLightElevation) {
        void bind(ShaderInstance shader, float seconds) {
            shader.safeGetUniform("fmMode").set(mode);
            shader.safeGetUniform("fmTime").set(speed == 0 ? 0 : seconds);
            shader.safeGetUniform("fmSeed").set(seed);
            shader.safeGetUniform("fmScale").set(scale);
            shader.safeGetUniform("fmSpeed").set(speed);
            shader.safeGetUniform("fmDetail").set(detail);
            shader.safeGetUniform("fmWarp").set(warp);
            shader.safeGetUniform("fmEmission").set(emission);
            shader.safeGetUniform("fmDepth").set(depth);
            shader.safeGetUniform("fmParallax").set(parallax);
            shader.safeGetUniform("fmExposure").set(exposure);
            shader.safeGetUniform("fmWaveHeight").set(waveHeight);
            shader.safeGetUniform("fmWaveLength").set(waveLength);
            shader.safeGetUniform("fmWindAngle").set(windAngle);
            shader.safeGetUniform("fmChoppiness").set(choppiness);
            shader.safeGetUniform("fmFoam").set(foam);
            shader.safeGetUniform("fmRoughness").set(roughness);
            shader.safeGetUniform("fmWaterOpacity").set(waterOpacity);
            shader.safeGetUniform("fmWaterTilt").set(waterTilt);
            shader.safeGetUniform("fmWaterLightAzimuth").set(waterLightAzimuth);
            shader.safeGetUniform("fmWaterLightElevation").set(waterLightElevation);
            if(mode==12)OceanSpectrum.bind(this,shader,seconds);
            base.bind(shader, "fmBase");
            accent.bind(shader, "fmAccent");
            highlight.bind(shader, "fmHighlight");
        }
    }
    static Preset get(String id) { return PRESETS.get(id); }
    static void reload(ResourceProvider resources) throws IOException {
        Map<String, Preset> next = new LinkedHashMap<>();
        var resource = resources.getResourceOrThrow(new ResourceLocation(CosmicMaterial.ID, "fantasy-recipes.json"));
        try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.get("schema").getAsString().equals("fantasy-material/v1"))
                throw new IOException("Unknown fantasy recipe schema");
            for (JsonElement element : root.getAsJsonArray("presets")) {
                JsonObject p = element.getAsJsonObject();
                String id=p.get("id").getAsString();
                if (!id.matches("[a-z0-9_-]+")) throw new IOException("Invalid fantasy preset ID: " + id);
                int mode = integer(p,"mode",0,13);
                Preset preset = new Preset(id, mode, integer(p,"seed",0,65535),
                    value(p,"scale",0.3f,3), value(p,"speed",0,1), value(p,"detail",0.2f,2),
                    value(p,"warp",0,2), value(p,"emission",0,3), value(p,"depth",0.5f,6),
                    value(p,"parallax",0,1.5f), value(p,"exposure",0.3f,2.5f),
                    color(p,"base"), color(p,"accent"), color(p,"highlight"),
                    mode == 12 ? value(p,"waveHeight",0,.65f) : .30f,
                    mode == 12 ? value(p,"waveLength",.55f,6) : 2.1f,
                    mode == 12 ? value(p,"windAngle",0,360) : 35,
                    mode == 12 ? value(p,"choppiness",0,1) : .78f,
                    mode == 12 ? value(p,"foam",0,1) : .55f,
                    mode == 12 ? value(p,"roughness",.04f,.55f) : .16f,
                    mode == 12 ? value(p,"waterOpacity",.05f,1) : .90f,
                    mode == 12 ? value(p,"waterTilt",0,1) : .85f,
                    mode == 12 ? value(p,"waterLightAzimuth",0,360) : 250,
                    mode == 12 ? value(p,"waterLightElevation",-.2f,1.4f) : .62f);
                if (next.put(preset.id, preset) != null) throw new IOException("Duplicate fantasy preset");
            }
        } catch (RuntimeException error) { throw new IOException("Invalid fantasy recipes", error); }
        OceanSpectrum.clear();PRESETS.clear(); PRESETS.putAll(next);
        System.out.println("[COSMIC-MATERIAL] Fantasy presets loaded: " + PRESETS.keySet());
    }
    private static float value(JsonObject p, String key, float minimum, float maximum) {
        if (!p.get(key).isJsonPrimitive() || !p.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be a number");
        float result = p.get(key).getAsFloat();
        if (!Float.isFinite(result) || result < minimum || result > maximum)
            throw new IllegalArgumentException(key + " outside [" + minimum + "," + maximum + "]");
        return result;
    }
    private static int integer(JsonObject p, String key, int minimum, int maximum) {
        float result=value(p,key,minimum,maximum);
        if (result != Math.floor(result)) throw new IllegalArgumentException(key + " must be an integer");
        return (int)result;
    }
    private static Color color(JsonObject p, String key) {
        JsonArray c = p.getAsJsonArray(key);
        if (c.size() != 3) throw new IllegalArgumentException(key + " requires three components");
        float[] values = new float[3];
        for (int i=0; i<3; i++) {
            values[i] = c.get(i).getAsFloat();
            if (!Float.isFinite(values[i]) || values[i] < 0 || values[i] > 1)
                throw new IllegalArgumentException(key + " component outside [0,1]");
        }
        return new Color(values[0], values[1], values[2]);
    }
}
