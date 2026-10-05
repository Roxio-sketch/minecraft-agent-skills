package dev.vfxworkbench.effects.water;

import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.core.ParamSpec;
import dev.vfxworkbench.core.ParamType;
import dev.vfxworkbench.core.ReloadMode;
import dev.vfxworkbench.core.SkillSchema;

import java.util.List;

/** Registers the Water Ripples parameter schema with the workbench registry. */
public final class WaterSkillSchema {
    public static final String ID = "water-ripples";

    private WaterSkillSchema() {}

    public static void register() {
        SkillSchema schema = SkillSchema.of(ID, "水波涟漪",
            num("grid", "网格分辨率", "波动模拟", "Grid resolution including boundary cells.", 65, 33, 129, 1, ReloadMode.RESOURCE, true),
            en("solver", "求解器", "波动模拟", "velocity keeps height+velocity; history keeps two heights.", "velocity", List.of("velocity", "history"), ReloadMode.RESOURCE),
            en("boundary", "边界模式", "波动模拟", "absorb adds a sponge edge; reflect mirrors near-edge values.", "absorb", List.of("absorb", "reflect"), ReloadMode.RESOURCE),
            num("width", "水面宽度", "波动模拟", "Patch side length in blocks.", 8.0, 2, 32, 0.5, ReloadMode.HOT, false),
            num("speed", "传播速度", "波动模拟", "Discrete propagation coefficient.", 1.0, 0.1, 1, 0.01, ReloadMode.HOT, false),
            num("damping", "波动保留率", "波动模拟", "Per-substep wave retention factor.", 0.995, 0.9, 0.9995, 0.0005, ReloadMode.HOT, false),
            num("radius", "扰动半径", "扰动交互", "Injected cosine-wave-packet radius in blocks.", 0.45, 0.05, 2, 0.05, ReloadMode.HOT, false),
            num("strength", "扰动强度", "扰动交互", "Disturbance height; negative makes a depression.", 0.18, -0.5, 0.5, 0.01, ReloadMode.HOT, false),
            num("maxHeight", "最大振幅", "波动模拟", "Simulation amplitude clamp.", 0.5, 0.05, 1, 0.05, ReloadMode.HOT, false),
            col("color", "水面颜色", "水面材质", "Base water tint.", "#08616f", ReloadMode.HOT),
            num("opacity", "透明度", "水面材质", "Base alpha blend factor.", 0.24, 0, 1, 0.01, ReloadMode.HOT, false),
            num("normalStrength", "法线强度", "水面材质", "Visual-only normal exaggeration.", 1.0, 0, 4, 0.1, ReloadMode.HOT, false),
            num("shininess", "高光锐度", "水面材质", "Specular highlight exponent.", 96.0, 8, 256, 1.0, ReloadMode.HOT, false),
            num("reflection", "天空反射", "水面材质", "Procedural sky Fresnel strength.", 1.0, 0, 2, 0.05, ReloadMode.HOT, false),
            num("rainRate", "每秒雨滴", "扰动交互", "Drops per second when rain is on.", 3.0, 0, 20, 0.5, ReloadMode.HOT, false)
        );
        ParamRegistry.register(schema);
    }

    private static ParamSpec num(String key, String label, String group, String help, double def, double min, double max, double step, ReloadMode reload, boolean intStep) {
        ParamType type = intStep ? ParamType.INT : ParamType.FLOAT;
        return ParamSpec.builder(key, label, type).group(group).help(help).min(min).max(max).step(step).intStep(intStep)
            .defaultValue(intStep ? Integer.toString((int) def) : Double.toString(def)).reload(reload).build();
    }

    private static ParamSpec en(String key, String label, String group, String help, String def, List<String> values, ReloadMode reload) {
        return ParamSpec.builder(key, label, ParamType.ENUM).group(group).help(help).enumValues(values)
            .defaultValue(def).reload(reload).build();
    }

    private static ParamSpec col(String key, String label, String group, String help, String def, ReloadMode reload) {
        return ParamSpec.builder(key, label, ParamType.COLOR).group(group).help(help).defaultValue(def).reload(reload).build();
    }
}
