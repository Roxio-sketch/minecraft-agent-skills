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
    private final Map<String,Double> doubles = new java.util.HashMap<>();
    private final Map<String,Float> floats = new java.util.HashMap<>();
    private final Map<String,Boolean> bools = new java.util.HashMap<>();
    private final Map<String,Integer> colors = new java.util.HashMap<>();
    private long revision;
    public long revision() { return revision; }
    private void invalidate(String key) { doubles.remove(key);floats.remove(key);bools.remove(key);colors.remove(key); }
    private void clearParsed() { doubles.clear();floats.clear();bools.clear();colors.clear(); }
    private final Set<Runnable> hotListeners = new CopyOnWriteArraySet<>();
    private final Set<Runnable> resourceListeners = new CopyOnWriteArraySet<>();

    public ParamState(SkillSchema schema) {
        this.schema = schema;
        resetToDefaults();
    }

    public void resetToDefaults() {
        values.clear();
        for (ParamSpec p : schema.params) values.put(p.key, p.defaultValue);
        clearParsed();revision++;
        notifyResource();
    }

    public String get(String key) { return values.getOrDefault(key, schema.get(key) == null ? "" : schema.get(key).defaultValue); }

    public double getDouble(String key) { Double value=doubles.get(key);if(value==null){value=Double.parseDouble(get(key));doubles.put(key,value);}return value; }
    public float getFloat(String key) { Float value=floats.get(key);if(value==null){value=(float)getDouble(key);floats.put(key,value);}return value; }
    public int getInt(String key) { return (int) Math.round(getDouble(key)); }
    public boolean getBool(String key) { Boolean value=bools.get(key);if(value==null){value=Boolean.parseBoolean(get(key));bools.put(key,value);}return value; }
    public int getColor(String key) {
        Integer value=colors.get(key);if(value==null){value=parseColor(key);colors.put(key,value);}return value;
    }
    private int parseColor(String key) {
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
        invalidate(key);revision++;
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

    /** Publish an already validated/staged batch, then notify once with the complete state. */
    public void loadAtomic(Map<String, String> source) {
        Map<String, String> next = new LinkedHashMap<>(values);
        boolean resource = false;
        for (Map.Entry<String, String> e : source.entrySet()) {
            ParamSpec spec = schema.get(e.getKey());
            if (spec == null) continue;
            if (e.getValue() == null) throw new IllegalArgumentException("null param: " + e.getKey());
            next.put(e.getKey(), e.getValue());
            resource |= spec.reload == ReloadMode.RESOURCE || spec.reload == ReloadMode.RESTART;
        }
        values.clear();values.putAll(next);
        clearParsed();revision++;
        if (resource) notifyResource();
        else for (Runnable r : hotListeners) r.run();
    }
}
