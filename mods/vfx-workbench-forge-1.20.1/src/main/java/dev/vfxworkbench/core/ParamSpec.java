package dev.vfxworkbench.core;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A single declared parameter. Skill code registers these once at startup; the
 * UI is generated from them and never needs a per-skill widget implementation.
 */
public final class ParamSpec {
    public final String key;          // stable identifier, e.g. "speed"
    public final String label;        // human readable label
    public final ParamType type;
    public final ReloadMode reload;   // hot / resource / restart classification
    public final String group;        // UI grouping, e.g. "Wave", "Surface"
    public final String help;         // one-line description
    private final Predicate<ParamState> visibility;

    // numeric bounds
    public final double min, max, step;
    public final boolean intStep;

    // enum options
    public final List<String> enumValues;

    // default value, encoded as a compact string ("1.0", "true", "#RRGGBB", "rain")
    public final String defaultValue;

    private ParamSpec(Builder b) {
        this.key = b.key;
        this.label = b.label;
        this.type = b.type;
        this.reload = b.reload;
        this.group = b.group;
        this.help = b.help;
        this.visibility = b.visibility;
        this.min = b.min;
        this.max = b.max;
        this.step = b.step;
        this.intStep = b.intStep;
        this.enumValues = b.enumValues;
        this.defaultValue = b.defaultValue;
    }

    public boolean isVisible(ParamState state) { return visibility.test(state); }

    public String display(String value) {
        switch (type) {
            case BOOL: return value == null ? "false" : value;
            case ENUM: return value == null ? defaultValue : value;
            default: return value == null ? "?" : value;
        }
    }

    public static final class Builder {
        private String key = ""; private String label = ""; private ParamType type = ParamType.FLOAT;
        private ReloadMode reload = ReloadMode.HOT; private String group = "通用参数"; private String help = "";
        private double min = 0; private double max = 1; private double step = 0.01;
        private boolean intStep = false;
        private List<String> enumValues = List.of();
        private String defaultValue = "0";
        private Predicate<ParamState> visibility = state -> true;

        public Builder key(String v) { key = v; return this; }
        public Builder label(String v) { label = v; return this; }
        public Builder type(ParamType v) { type = v; return this; }
        public Builder reload(ReloadMode v) { reload = v; return this; }
        public Builder group(String v) { group = v; return this; }
        public Builder help(String v) { help = v; return this; }
        public Builder visibleWhen(Predicate<ParamState> v) { visibility = java.util.Objects.requireNonNull(v); return this; }
        public Builder min(double v) { min = v; return this; }
        public Builder max(double v) { max = v; return this; }
        public Builder step(double v) { step = v; return this; }
        public Builder intStep(boolean v) { intStep = v; return this; }
        public Builder enumValues(List<String> v) { enumValues = List.copyOf(v); return this; }
        public Builder defaultValue(String v) { defaultValue = v; return this; }
        public ParamSpec build() { return new ParamSpec(this); }
    }

    public static Builder builder(String key, String label, ParamType type) {
        return new Builder().key(key).label(label).type(type);
    }

    /** Convenience for the common float slider case. */
    public static ParamSpec hotFloat(String key, String label, String group, double min, double max, double step, double def) {
        return builder(key, label, ParamType.FLOAT).group(group).min(min).max(max).step(step)
                .defaultValue(Double.toString(def)).build();
    }

    public static ParamSpec hotInt(String key, String label, String group, int min, int max, int def) {
        return builder(key, label, ParamType.INT).group(group).min(min).max(max).step(1).intStep(true)
                .defaultValue(Integer.toString(def)).build();
    }
}
