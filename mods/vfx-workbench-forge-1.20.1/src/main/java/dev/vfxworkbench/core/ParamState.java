package dev.vfxworkbench.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Mutable runtime value store for one skill instance. Renderers read this
 * every frame, so HOT parameters never need a rebuild. RESOURCE/RESTART
 * parameters fire listeners so owners can rebuild or warn.
 *
 * Values are stored as strings to stay format-agnostic across Float/Int/Bool/
 * Enum/Color/Vector/Curve; accessor methods parse on demand.
 */
public final class ParamState {
    public final SkillSchema schema;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Set<Runnable> hotListeners = new CopyOnWriteArraySet<>();
    private final Set<Runnable> resourceListeners = new CopyOnWriteArraySet<>();

    public ParamState(SkillSchema schema) {
        this.schema = schema;
        resetToDefaults();
    }

    public void resetToDefaults() {
        values.clear();
        for (ParamSpec p : schema.params) values.put(p.key, p.defaultValue);
        notifyResource();
    }

    public String get(String key) { return values.getOrDefault(key, schema.get(key) == null ? "" : schema.get(key).defaultValue); }

    public double getDouble(String key) { return Double.parseDouble(get(key)); }
    public float getFloat(String key) { return (float) getDouble(key); }
    public int getInt(String key) { return (int) Math.round(getDouble(key)); }
    public boolean getBool(String key) { return Boolean.parseBoolean(get(key)); }
    public int getColor(String key) {
        String hex = get(key);
        if (hex != null && hex.startsWith("#") && hex.length() >= 7) {
            try { return Integer.parseInt(hex.substring(1, 7), 16); }
            catch (NumberFormatException ignored) { }
        }
        return 0x08616f;
    }

    public void set(String key, String value) {
        ParamSpec spec = schema.get(key);
        if (spec == null) throw new IllegalArgumentException("unknown param: " + key);
        values.put(key, value);
        if (spec.reload == ReloadMode.RESOURCE || spec.reload == ReloadMode.RESTART) {
            notifyResource();
        } else {
            for (Runnable r : hotListeners) r.run();
        }
    }

    public void setDouble(String key, double value) { set(key, Double.toString(value)); }
    public void setIntValue(String key, int value) { set(key, Integer.toString(value)); }
    public void setBool(String key, boolean value) { set(key, Boolean.toString(value)); }

    /** Callback when a hot (per-frame) parameter changes. */
    public void onHotChange(Runnable r) { hotListeners.add(r); }
    /** Callback when a parameter requiring rebuild changes. */
    public void onResourceChange(Runnable r) { resourceListeners.add(r); }

    private void notifyResource() { for (Runnable r : resourceListeners) r.run(); }

    public Map<String, String> snapshot() { return new LinkedHashMap<>(values); }

    public void load(Map<String, String> source) {
        for (Map.Entry<String, String> e : source.entrySet()) {
            if (schema.contains(e.getKey())) set(e.getKey(), e.getValue());
        }
    }
}
