package dev.cosmiclab;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.client.renderer.ShaderInstance;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Immutable, fully validated cosmic-sky/6 recipe. No partial/default filling. */
public final class SkyRecipe {
    public static final String SCHEMA = "cosmic-sky/6";
    private record Field(double min, double max, boolean integer, boolean color) {}
    private static final Map<String, Field> FIELDS = fields();
    private final Map<String, JsonPrimitive> parameters;

    private SkyRecipe(Map<String, JsonPrimitive> parameters) {
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    private static Map<String, Field> fields() {
        Map<String, Field> m = new LinkedHashMap<>();
        m.put("giantBodyStyle", new Field(0, 7, true, false));
        m.put("giantBodyScale", new Field(1, 4, false, false));
        m.put("giantBodySeparation", new Field(0, 3, false, false));
        m.put("giantBodyAtmosphere", new Field(0, 2, false, false));
        m.put("seed", new Field(0, 9999, true, false));
        m.put("quality", new Field(1, 3, true, false));
        m.put("exposure", new Field(0.2, 3, false, false));
        m.put("starDensity", new Field(0, 1, false, false));
        m.put("starBrightness", new Field(0, 4, false, false));
        m.put("starSize", new Field(0.3, 2.5, false, false));
        m.put("twinkleAmount", new Field(0, 1, false, false));
        m.put("twinkleSpeed", new Field(0, 4, false, false));
        m.put("galacticYaw", new Field(-180, 180, false, false));
        m.put("galacticTilt", new Field(-85, 85, false, false));
        m.put("bandWidth", new Field(0.04, 0.8, false, false));
        m.put("galaxyStrength", new Field(0, 4, false, false));
        m.put("dustStrength", new Field(0, 3, false, false));
        m.put("nebulaStrength", new Field(0, 4, false, false));
        m.put("nebulaScale", new Field(1, 10, false, false));
        m.put("nebulaSpeed", new Field(0, 1, false, false));
        m.put("auroraStrength", new Field(0, 4, false, false));
        m.put("auroraHeight", new Field(0.05, 0.95, false, false));
        m.put("auroraWidth", new Field(0.02, 0.45, false, false));
        m.put("auroraCurtains", new Field(0.3, 3, false, false));
        m.put("auroraSpeed", new Field(0, 3, false, false));
        m.put("meteorRate", new Field(0, 8, false, false));
        m.put("meteorBrightness", new Field(0, 5, false, false));
        m.put("meteorSpeed", new Field(0.1, 3, false, false));
        m.put("meteorLength", new Field(0.02, 0.4, false, false));
        m.put("horizonGlow", new Field(0, 1, false, false));
        m.put("celestialStrength", new Field(0, 3, false, false));
        m.put("celestialSize", new Field(2, 28, false, false));
        m.put("celestialYaw", new Field(-180, 180, false, false));
        m.put("celestialPitch", new Field(-70, 85, false, false));
        m.put("celestialType", new Field(1, 3, true, false));
        m.put("celestialPhase", new Field(-180, 180, false, false));
        m.put("celestialDetail", new Field(1, 12, false, false));
        m.put("celestialSpeed", new Field(0, 1, false, false));
        m.put("ringStrength", new Field(0, 2, false, false));
        m.put("ringTilt", new Field(0, 85, false, false));
        m.put("ringWidth", new Field(1.3, 3, false, false));
        m.put("blackHoleStrength", new Field(0, 4, false, false));
        m.put("blackHoleSize", new Field(1, 16, false, false));
        m.put("blackHoleYaw", new Field(-180, 180, false, false));
        m.put("blackHolePitch", new Field(-70, 85, false, false));
        m.put("diskTilt", new Field(0, 85, false, false));
        m.put("diskWidth", new Field(3.5, 9, false, false));
        m.put("diskSpeed", new Field(0, 3, false, false));
        m.put("lensingStrength", new Field(0, 1.5, false, false));
        m.put("atmosphereStrength", new Field(0, 1, false, false));
        m.put("turbidity", new Field(1, 10, false, false));
        m.put("rayleighStrength", new Field(0, 4, false, false));
        m.put("mieStrength", new Field(0, 3, false, false));
        m.put("sunBrightness", new Field(0, 5, false, false));
        m.put("sunElevation", new Field(-18, 85, false, false));
        m.put("sunYaw", new Field(-180, 180, false, false));
        m.put("sunSize", new Field(0.15, 4, false, false));
        m.put("cycleSpeed", new Field(0, 2, false, false));
        m.put("mieAnisotropy", new Field(0, 0.9, false, false));
        m.put("nightAmbient", new Field(0, 0.3, false, false));
        m.put("twilightStrength", new Field(0, 2, false, false));
        m.put("moonBrightness", new Field(0, 3, false, false));
        m.put("moonYaw", new Field(-180, 180, false, false));
        m.put("moonElevation", new Field(-18, 85, false, false));
        m.put("moonSize", new Field(0.15, 6, false, false));
        m.put("moonPhase", new Field(-180, 180, false, false));
        m.put("moonDetail", new Field(1, 12, false, false));
        m.put("moonHalo", new Field(0, 2, false, false));
        m.put("moonSpeed", new Field(0, 1, false, false));
        m.put("thinCloudStrength", new Field(0, 2, false, false));
        m.put("thinCloudCoverage", new Field(0, 1, false, false));
        m.put("thinCloudScale", new Field(0.25, 4, false, false));
        m.put("thinCloudHeight", new Field(0.3, 3, false, false));
        m.put("thinCloudSpeed", new Field(0, 2, false, false));
        m.put("thinCloudWind", new Field(-180, 180, false, false));
        m.put("thinCloudEvolution", new Field(0, 2, false, false));
        m.put("thinCloudSilver", new Field(0, 3, false, false));
        m.put("rainAmount", new Field(0, 1, false, false));
        m.put("thunderAmount", new Field(0, 1, false, false));
        m.put("weatherSpeed", new Field(0, 3, false, false));
        m.put("stormDarkening", new Field(0, 1, false, false));
        m.put("rainDensity", new Field(0.1, 3, false, false));
        m.put("rainSpeed", new Field(0.1, 3, false, false));
        m.put("rainLength", new Field(0.03, 1, false, false));
        m.put("rainBrightness", new Field(0, 2, false, false));
        m.put("rainWind", new Field(-180, 180, false, false));
        m.put("weatherFollowWorld", new Field(0, 1, true, false));
        m.put("lightningFrequency", new Field(0, 3, false, false));
        m.put("lightningDuration", new Field(0.05, 1.5, false, false));
        m.put("fogStrength", new Field(0, 3, false, false));
        m.put("fogDensity", new Field(0, 0.03, false, false));
        m.put("fogHeightFalloff", new Field(0, 0.1, false, false));
        m.put("fogBaseHeight", new Field(-128, 384, false, false));
        m.put("fogNoise", new Field(0, 1, false, false));
        m.put("fogScale", new Field(0.001, 0.05, false, false));
        m.put("fogSpeed", new Field(0, 3, false, false));
        m.put("fogDistance", new Field(16, 2048, false, false));
        m.put("volumeCloudStrength", new Field(0, 2, false, false));
        m.put("volumeCloudCoverage", new Field(0, 1, false, false));
        m.put("volumeCloudDensity", new Field(0, 3, false, false));
        m.put("volumeCloudScale", new Field(0.001, 0.025, false, false));
        m.put("volumeCloudBase", new Field(32, 384, false, false));
        m.put("volumeCloudThickness", new Field(8, 200, false, false));
        m.put("volumeCloudDistance", new Field(64, 2048, false, false));
        m.put("volumeCloudErosion", new Field(0, 1, false, false));
        m.put("volumeCloudSpeed", new Field(0, 3, false, false));
        m.put("volumeCloudWind", new Field(-180, 180, false, false));
        m.put("volumeCloudEvolution", new Field(0, 2, false, false));
        m.put("volumeCloudAnisotropy", new Field(0, 0.85, false, false));
        m.put("volumeCloudAmbient", new Field(0, 2, false, false));
        m.put("volumeCloudSunlight", new Field(0, 3, false, false));
        m.put("cloudShadowStrength", new Field(0, 1, false, false));
        m.put("lightShaftStrength", new Field(0, 3, false, false));
        m.put("lightShaftDensity", new Field(0, 1, false, false));
        m.put("lightShaftDistance", new Field(16, 1024, false, false));
        m.put("cloudShadowSoftness", new Field(0.1, 4, false, false));
        m.put("lightShaftAnisotropy", new Field(0, 0.9, false, false));
        m.put("rainbowStrength", new Field(0, 3, false, false));
        m.put("rainbowWidth", new Field(0.25, 4, false, false));
        m.put("secondaryRainbow", new Field(0, 1.5, false, false));
        m.put("rainbowWetness", new Field(0, 1, false, false));
        m.put("haloStrength", new Field(0, 3, false, false));
        m.put("haloWidth", new Field(0.2, 4, false, false));
        m.put("haloIce", new Field(0, 1, false, false));
        m.put("haloMoon", new Field(0, 1, true, false));
        m.put("specialStrength", new Field(0, 3, false, false));
        m.put("specialStyle", new Field(1, 3, true, false));
        m.put("specialScale", new Field(0.3, 6, false, false));
        m.put("specialSpeed", new Field(0, 3, false, false));
        m.put("specialDistortion", new Field(0, 2, false, false));
        m.put("specialBandWidth", new Field(0.03, 0.8, false, false));
        m.put("birdDensity", new Field(0, 1, false, false));
        m.put("birdSpeed", new Field(0, 3, false, false));
        m.put("blackHoleStyle", new Field(1, 2, true, false));
        m.put("blackHoleRingBrightness", new Field(0, 4, false, false));
        m.put("blackHoleRingWidth", new Field(0.01, 0.25, false, false));
        m.put("blackHoleGlow", new Field(0, 3, false, false));
        m.put("blackHoleArc", new Field(0, 2, false, false));
        m.put("blackHoleStreak", new Field(0, 3, false, false));
        m.put("blackHoleContrast", new Field(0, 2, false, false));
        m.put("blackHoleNoise", new Field(0, 3, false, false));
        for (String color : new String[]{"backgroundColor", "starCoolColor", "starWarmColor", "galaxyColor",
                "nebulaColorA", "nebulaColorB", "auroraColorA", "auroraColorB", "celestialColorA",
                "celestialColorB", "ringColor", "diskColorA", "diskColorB", "skyTint", "sunColor",
                "moonColor", "thinCloudColor", "fogColor", "volumeCloudColor", "rainColor", "lightningColor", "specialColorA", "specialColorB"})
            m.put(color, new Field(0, 0, false, true));
        m.put("atmosphereStyle", new Field(1, 2, true, false));
        m.put("planetRadius", new Field(1000, 10000, false, false));
        m.put("atmosphereHeight", new Field(20, 200, false, false));
        m.put("rayleighHeight", new Field(1, 20, false, false));
        m.put("mieHeight", new Field(0.1, 8, false, false));
        m.put("rayleighDensity", new Field(0, 5, false, false));
        m.put("mieDensity", new Field(0, 5, false, false));
        m.put("ozoneDensity", new Field(0, 5, false, false));
        m.put("groundAlbedo", new Field(0, 0.9, false, false));
        m.put("observerAltitude", new Field(0, 200, false, false));
        m.put("multipleScattering", new Field(0, 1, false, false));
        m.put("groundLevel", new Field(-64, 512, false, false));
        m.put("snowDensity", new Field(0.1, 3, false, false));
        m.put("snowSize", new Field(0.01, 0.25, false, false));
        m.put("snowSpeed", new Field(0.05, 3, false, false));
        m.put("snowBrightness", new Field(0, 2, false, false));
        m.put("precipitationMode", new Field(0, 3, true, false));
        m.put("precipitationRoof", new Field(0, 1, true, false));
        m.put("snowFlutter", new Field(0, 2, false, false));
        m.put("snowWindStrength", new Field(0, 3, false, false));
        m.put("nativeTransparentMode", new Field(0, 1, true, false));
        m.put("environmentRenderScale", new Field(0.5, 1, false, false));
        m.put("environmentDepthTolerance", new Field(0, 0.1, false, false));
        m.put("snowColor", new Field(0, 0, false, true));
        return Collections.unmodifiableMap(m);
    }

    public static SkyRecipe read(Reader source) throws IOException {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(false);
        Set<String> seen = new HashSet<>();
        Map<String, JsonPrimitive> parameters = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (!seen.add(key)) throw invalid("Duplicate field: " + key);
            switch (key) {
                case "schema" -> {
                    if (reader.peek() != JsonToken.STRING || !SCHEMA.equals(reader.nextString()))
                        throw invalid("Expected schema " + SCHEMA);
                }
                case "parameters" -> parameters = readParameters(reader);
                default -> throw invalid("Unknown recipe field: " + key);
            }
        }
        reader.endObject();
        if (!seen.equals(Set.of("schema", "parameters")) || parameters == null)
            throw invalid("Recipe needs exactly schema and parameters");
        if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON content");
        return new SkyRecipe(parameters);
    }

    private static Map<String, JsonPrimitive> readParameters(JsonReader reader) throws IOException {
        Map<String, JsonPrimitive> values = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            Field field = FIELDS.get(key);
            if (field == null) throw invalid("Unknown parameter: " + key);
            if (values.containsKey(key)) throw invalid("Duplicate parameter: " + key);
            if (field.color) {
                if (reader.peek() != JsonToken.STRING) throw invalid(key + " needs #RRGGBB");
                String value = reader.nextString();
                if (!value.matches("#[0-9a-fA-F]{6}")) throw invalid(key + " needs #RRGGBB");
                values.put(key, new JsonPrimitive(value.toLowerCase(java.util.Locale.ROOT)));
            } else {
                if (reader.peek() != JsonToken.NUMBER) throw invalid(key + " must be a number");
                double value = reader.nextDouble();
                if (!Double.isFinite(value) || value < field.min || value > field.max ||
                        (field.integer && value != Math.rint(value)))
                    throw invalid(key + " must be within " + field.min + ".." + field.max +
                        (field.integer ? " and an integer" : ""));
                values.put(key, new JsonPrimitive(value));
            }
        }
        reader.endObject();
        if (!values.keySet().equals(FIELDS.keySet())) {
            Set<String> missing = new java.util.LinkedHashSet<>(FIELDS.keySet());
            missing.removeAll(values.keySet());
            throw invalid("Missing parameters: " + missing);
        }
        return values;
    }

    private static IOException invalid(String message) { return new IOException(message); }
    float value(String key) { return parameters.get(key).getAsFloat(); }
    int quality() { return parameters.get("quality").getAsInt(); }

    SkyRecipe with(Map<String, ?> overrides) {
        JsonObject json = toJson();
        overrides.forEach((key, value) -> {
            if (value instanceof Number number) json.getAsJsonObject("parameters").addProperty(key, number);
            else if (value instanceof String color) json.getAsJsonObject("parameters").addProperty(key, color);
            else throw new IllegalArgumentException("Expected a numeric or color override: " + key);
        });
        try { return read(new StringReader(json.toString())); }
        catch (IOException error) { throw new IllegalArgumentException("Invalid recipe override", error); }
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject(), values = new JsonObject();
        FIELDS.keySet().forEach(key -> values.add(key, parameters.get(key)));
        root.addProperty("schema", SCHEMA);
        root.add("parameters", values);
        return root;
    }

    void upload(ShaderInstance shader, int quality) {
        shader.safeGetUniform("Quality").set(quality);
        shader.safeGetUniform("Seed").set(value("seed"));
        pack(shader, "GiantBodySettings", "giantBodyStyle", "giantBodyScale", "giantBodySeparation", "giantBodyAtmosphere");
        pack(shader, "StarSettings", "starDensity", "starBrightness", "starSize", "twinkleAmount");
        pack(shader, "MotionSettings", "twinkleSpeed", "nebulaSpeed", "auroraSpeed", "meteorSpeed");
        shader.safeGetUniform("GalaxySettings").set((float)Math.toRadians(value("galacticYaw")),
            (float)Math.toRadians(value("galacticTilt")), value("bandWidth"), value("galaxyStrength"));
        pack(shader, "CloudSettings", "nebulaStrength", "nebulaScale", "dustStrength", "exposure");
        pack(shader, "AuroraSettings", "auroraStrength", "auroraHeight", "auroraWidth", "auroraCurtains");
        pack(shader, "MeteorSettings", "meteorRate", "meteorBrightness", "meteorLength", "horizonGlow");
        shader.safeGetUniform("BodySettings").set(value("celestialStrength"), radians("celestialSize"),
            radians("celestialYaw"), radians("celestialPitch"));
        shader.safeGetUniform("BodySurface").set(value("celestialType"), radians("celestialPhase"),
            value("celestialDetail"), value("celestialSpeed"));
        shader.safeGetUniform("RingSettings").set(value("ringStrength"), radians("ringTilt"), value("ringWidth"));
        shader.safeGetUniform("BlackHoleSettings").set(value("blackHoleStrength"), radians("blackHoleSize"),
            radians("blackHoleYaw"), radians("blackHolePitch"));
        shader.safeGetUniform("DiskSettings").set(radians("diskTilt"), value("diskWidth"),
            value("diskSpeed"), value("lensingStrength"));
        pack(shader, "AtmosphereSettings", "atmosphereStrength", "turbidity", "rayleighStrength", "mieStrength");
        shader.safeGetUniform("SunSettings").set(value("sunBrightness"), radians("sunElevation"),
            radians("sunYaw"), radians("sunSize"));
        pack(shader, "DaySettings", "cycleSpeed", "mieAnisotropy", "nightAmbient", "twilightStrength");
        shader.safeGetUniform("MoonSettings").set(value("moonBrightness"), radians("moonYaw"),
            radians("moonElevation"), radians("moonSize"));
        shader.safeGetUniform("MoonSurface").set(radians("moonPhase"), value("moonDetail"),
            value("moonHalo"), value("moonSpeed"));
        pack(shader, "ThinCloudSettings", "thinCloudStrength", "thinCloudCoverage", "thinCloudScale", "thinCloudHeight");
        shader.safeGetUniform("ThinCloudMotion").set(value("thinCloudSpeed"), radians("thinCloudWind"),
            value("thinCloudEvolution"), value("thinCloudSilver"));
        color(shader, "BackgroundColor", "backgroundColor");
        color(shader, "StarCoolColor", "starCoolColor");
        color(shader, "StarWarmColor", "starWarmColor");
        color(shader, "GalaxyColor", "galaxyColor");
        color(shader, "NebulaColorA", "nebulaColorA");
        color(shader, "NebulaColorB", "nebulaColorB");
        color(shader, "AuroraColorA", "auroraColorA");
        color(shader, "AuroraColorB", "auroraColorB");
        color(shader, "BodyColorA", "celestialColorA");
        color(shader, "BodyColorB", "celestialColorB");
        color(shader, "RingColor", "ringColor");
        color(shader, "DiskColorA", "diskColorA");
        color(shader, "DiskColorB", "diskColorB");
        color(shader, "SkyTint", "skyTint");
        color(shader, "SunColor", "sunColor");
        color(shader, "MoonColor", "moonColor");
        color(shader, "ThinCloudColor", "thinCloudColor");
        shader.safeGetUniform("WeatherSettings").set(value("rainAmount"), value("thunderAmount"), value("weatherSpeed"), value("stormDarkening"));
        shader.safeGetUniform("RainSettings").set(value("rainDensity"), value("rainSpeed"), value("rainLength"), value("rainBrightness"));
        shader.safeGetUniform("WeatherMotion").set(radians("rainWind"), value("weatherFollowWorld"), value("lightningFrequency"), value("lightningDuration"));
        shader.safeGetUniform("FogSettings").set(value("fogStrength"), value("fogDensity"), value("fogHeightFalloff"), value("fogBaseHeight"));
        shader.safeGetUniform("FogMotion").set(value("fogNoise"), value("fogScale"), value("fogSpeed"), value("fogDistance"));
        shader.safeGetUniform("VolumeCloudSettings").set(value("volumeCloudStrength"), value("volumeCloudCoverage"), value("volumeCloudDensity"), value("volumeCloudScale"));
        shader.safeGetUniform("VolumeCloudGeometry").set(value("volumeCloudBase"), value("volumeCloudThickness"), value("volumeCloudDistance"), value("volumeCloudErosion"));
        shader.safeGetUniform("VolumeCloudMotion").set(value("volumeCloudSpeed"), radians("volumeCloudWind"), value("volumeCloudEvolution"), value("volumeCloudAnisotropy"));
        shader.safeGetUniform("CloudLighting").set(value("volumeCloudAmbient"), value("volumeCloudSunlight"), value("cloudShadowStrength"), value("lightShaftStrength"));
        shader.safeGetUniform("LightingDetail").set(value("lightShaftDensity"), value("lightShaftDistance"), value("cloudShadowSoftness"), value("lightShaftAnisotropy"));
        shader.safeGetUniform("RainbowSettings").set(value("rainbowStrength"), radians("rainbowWidth"), value("secondaryRainbow"), value("rainbowWetness"));
        shader.safeGetUniform("HaloSettings").set(value("haloStrength"), radians("haloWidth"), value("haloIce"), value("haloMoon"));
        shader.safeGetUniform("SpecialSettings").set(value("specialStrength"), value("specialStyle"), value("specialScale"), value("specialSpeed"));
        shader.safeGetUniform("SpecialShape").set(value("specialDistortion"), value("specialBandWidth"), value("birdDensity"), value("birdSpeed"));
        shader.safeGetUniform("BlackHoleAppearance").set(value("blackHoleStyle"), value("blackHoleRingBrightness"), value("blackHoleRingWidth"), value("blackHoleGlow"));
        shader.safeGetUniform("BlackHoleCinematic").set(value("blackHoleArc"), value("blackHoleStreak"), value("blackHoleContrast"), value("blackHoleNoise"));
        color(shader, "FogColor", "fogColor");
        color(shader, "VolumeCloudColor", "volumeCloudColor");
        color(shader, "RainColor", "rainColor");
        color(shader, "LightningColor", "lightningColor");
        color(shader, "SpecialColorA", "specialColorA");
        color(shader, "SpecialColorB", "specialColorB");
        shader.safeGetUniform("AtmosphereGeometry").set(value("planetRadius"), value("atmosphereHeight"), value("rayleighHeight"), value("mieHeight"));
        shader.safeGetUniform("AtmosphereComposition").set(value("rayleighDensity"), value("mieDensity"), value("ozoneDensity"), value("groundAlbedo"));
        shader.safeGetUniform("AtmospherePhysical").set(value("atmosphereStyle"), value("observerAltitude"), value("multipleScattering"), value("groundLevel"));
        shader.safeGetUniform("SnowSettings").set(value("snowDensity"), value("snowSize"), value("snowSpeed"), value("snowBrightness"));
        shader.safeGetUniform("PrecipitationSettings").set(value("precipitationMode"), value("precipitationRoof"), value("snowFlutter"), value("snowWindStrength"));
        shader.safeGetUniform("PipelineSettings").set(value("nativeTransparentMode"), value("environmentRenderScale"), value("environmentDepthTolerance"));
        color(shader, "SnowColor", "snowColor");
    }

    private float radians(String key) { return (float)Math.toRadians(value(key)); }

    private void pack(ShaderInstance shader, String uniform, String a, String b, String c, String d) {
        shader.safeGetUniform(uniform).set(value(a), value(b), value(c), value(d));
    }
    private void color(ShaderInstance shader, String uniform, String key) {
        int rgb = Integer.parseInt(parameters.get(key).getAsString().substring(1), 16);
        shader.safeGetUniform(uniform).set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }
}
