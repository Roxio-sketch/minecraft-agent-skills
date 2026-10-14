package dev.vfxworkbench.effects.lightning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfxworkbench.core.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** The full lightning skill contract, with a separate MOD default recipe. */
public final class LightningSkillSchema {
    public static final String ID = "lightning";
    public static final List<String> PRESET_CHOICES=List.of("custom","orbit","source","shortSource","ring","strike","cluster","rain","tribulation");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static boolean applying;
    private static String previousPreset, previousRainStyle;
    private static final Set<String> HOST_FIELDS = Set.of("triggerPlacement", "triggerDistance", "triggerHeightMode", "triggerStartY");

    public static void register() {
        if (ParamRegistry.has(ID)) return;
        LightningRecipe defaults = loadResource("recipe.json");
        var p = new ArrayList<ParamSpec>();
        enumeration(p, defaults, "mode", "效果模式", List.of("orbit", "strike", "rain"));
        p.add(ParamSpec.builder("triggerHeightMode", "高度基准", ParamType.ENUM).group("落雷形状")
            .enumValues(List.of("relativeGround", "absoluteWorld")).defaultValue("relativeGround")
            .visibleWhen(s -> !s.get("mode").equals("orbit"))
            .help("固定世界高度直接指定起点 Y；与玩家高度无关").build());
        p.add(ParamSpec.builder("triggerStartY", "落雷起点 Y（世界坐标）", ParamType.FLOAT).group("落雷形状")
            .min(-2048).max(8192).step(1).defaultValue("296")
            .visibleWhen(s -> !s.get("mode").equals("orbit") && s.get("triggerHeightMode").equals("absoluteWorld"))
            .help("起点必须高于落点至少 2 格；不会自动随玩家升降").build());
        p.add(ParamSpec.builder("triggerPlacement", "触发落点", ParamType.ENUM).group("模式与预设")
            .enumValues(List.of("self", "forward")).defaultValue("self")
            .visibleWhen(s -> !s.get("mode").equals("orbit"))
            .help("前方落点按玩家水平朝向放置；触发后固定在世界中").build());
        p.add(ParamSpec.builder("triggerDistance", "触发距离（格）", ParamType.FLOAT).group("模式与预设")
            .min(1).max(128).step(1).defaultValue("90")
            .visibleWhen(s -> !s.get("mode").equals("orbit") && s.get("triggerPlacement").equals("forward"))
            .help("水平距离；优先落到前方地面，找不到地面时使用玩家脚下高度").build());
        p.add(ParamSpec.builder("preset", "效果预设", ParamType.ENUM).group("模式与预设")
            .enumValues(List.of("custom", "source", "shortSource", "ring", "cluster", "orbit", "dense", "strike", "tribulation", "rain"))
            .defaultValue("custom").build());
        enumeration(p, defaults, "rainStyle", "落雷布局", List.of("spread", "cluster"));
        enumeration(p, defaults, "orbitAnimation", "环绕动画", List.of("grow", "spektr", "ring"));
        enumeration(p, defaults, "orbitDirection", "延伸方向", List.of("1", "-1"));
        number(p, defaults, "seed", "随机种子", Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        number(p, defaults, "strands", "电弧数量", 1, 32, true);
        number(p, defaults, "samples", "每条采样段数", 16, 256, true);
        number(p, defaults, "objectRadius", "工作台参考物半径", .1, 8, false);
        number(p, defaults, "orbitRadius", "环绕半径（格）", .1, 12, false);
        number(p, defaults, "orbitHeight", "环绕高度（格）", -8, 8, false);
        number(p, defaults, "rodLength", "附着通道长度（格）", .5, 32, false);
        number(p, defaults, "rodRadius", "附着通道半径（格）", .01, 2, false);
        number(p, defaults, "emitterSpread", "根部扩散（格）", 0, 2, false);
        number(p, defaults, "orbitWobble", "电弧抖动（格）", 0, 2, false);
        number(p, defaults, "orbitFlow", "噪声流速", -12, 12, false);
        number(p, defaults, "orbitNoiseFrequency", "噪声频率", .05, 8, false);
        number(p, defaults, "arcLength", "电弧长度（格）", .1, 32, false);
        number(p, defaults, "lengthRandomness", "长度随机度", 0, .95, false);
        number(p, defaults, "pulseInterval", "脉冲间隔（tick）", .5, 20, false);
        number(p, defaults, "throttle", "出现概率", 0, 1, false);
        number(p, defaults, "orbitExtendSpeed", "延伸速度（格/秒）", .1, 120, false);
        number(p, defaults, "currentWidth", "电流核心宽度（格）", .001, .4, false);
        number(p, defaults, "glowWidth", "光晕宽度（格）", .01, 2, false);
        number(p, defaults, "coreGain", "核心亮度", .1, 16, false);
        number(p, defaults, "glowGain", "光晕亮度", 0, 12, false);
        number(p, defaults, "strikeHeight", "落雷高度（格）", 2, 1024, false);
        number(p, defaults, "strikeWidth", "雷柱核心宽度（格）", .004, .4, false);
        number(p, defaults, "strikeDisplacement", "横向扰动（格）", .05, 12, false);
        number(p, defaults, "strikeRoughness", "分形粗糙度", .35, .85, false);
        number(p, defaults, "strikeGenerations", "细分层数", 3, 9, true);
        number(p, defaults, "maxSegments", "线段预算", 64, 10000, true);
        number(p, defaults, "branchProbability", "分叉概率", 0, 1, false);
        number(p, defaults, "branchDecay", "分叉衰减", .1, .95, false);
        number(p, defaults, "branchLength", "分叉长度比例", .05, 1, false);
        number(p, defaults, "branchAngle", "分叉角度（弧度）", .1, 1.5, false);
        number(p, defaults, "branchJitter", "分叉抖动比例", 0, 2, false);
        number(p, defaults, "maxBranchDepth", "分叉深度", 0, 4, true);
        number(p, defaults, "branchLimit", "分叉预算", 0, 128, true);
        number(p, defaults, "progressionTicks", "先行到达时间（tick）", .1, 8, false);
        number(p, defaults, "durationTicks", "总持续时间（tick）", 1, 40, false);
        number(p, defaults, "restrikeCount", "回击脉冲次数", 0, 4, true);
        number(p, defaults, "flicker", "闪烁强度", 0, 1, false);
        number(p, defaults, "rainCount", "落雷数量", 1, 24, true);
        number(p, defaults, "rainSpread", "落雷横向范围（格）", 1, 64, false);
        number(p, defaults, "rainDepthSpread", "落雷纵向范围（格）", 0, 32, false);
        number(p, defaults, "rainStagger", "错峰时间（tick）", 0, 12, false);
        number(p, defaults, "rainMaxBranchDepth", "群雷分叉深度", 0, 1, true);
        number(p, defaults, "rainMaxBranches", "单雷分叉预算", 0, 2, true);
        number(p, defaults, "rainBranchProbability", "群雷分叉概率", 0, .12, false);
        number(p, defaults, "impactRadius", "击地半径（格）", .1, 6, false);
        number(p, defaults, "impactDuration", "击地持续时间（tick）", 2, 40, false);
        number(p, defaults, "impactRays", "地面电弧数量", 1, 24, true);
        number(p, defaults, "impactSparks", "火花数量", 0, 32, true);
        toggle(p, defaults, "impactEnabled", "启用击地放电");
        toggle(p, defaults, "gradientEnabled", "启用沿程渐变");
        p.add(ParamSpec.builder("gradientTarget", "渐变作用层", ParamType.ENUM).group("颜色与亮度")
            .enumValues(List.of("both", "core", "glow")).visibleWhen(s -> s.getBool("gradientEnabled"))
            .help("主雷与分叉共用；分别选择核心、光晕或两层").defaultValue(defaults.gradientTarget).build());
        p.add(ParamSpec.builder("gradientCoreMode", "核心显色方式", ParamType.ENUM).group("颜色与亮度")
            .enumValues(List.of("vivid", "additive")).visibleWhen(s -> s.getBool("gradientEnabled") && !s.get("gradientTarget").equals("glow"))
            .help("保留色相：亮度增加覆盖，避免主雷冲白；传统叠加：高亮会冲白").defaultValue(defaults.gradientCoreMode).build());
        for (String key : List.of("coreColor", "glowColor", "gradientStart", "gradientEnd")) {
            String label = switch (key) {
                case "coreColor" -> "核心基础色";
                case "glowColor" -> "光晕基础色";
                case "gradientStart" -> "起始色（云端 / 起点）";
                default -> "终止色（地面 / 终点）";
            };
            p.add(ParamSpec.builder(key, label, ParamType.COLOR).group(group(key))
                .visibleWhen(s -> !key.startsWith("gradient") || s.getBool("gradientEnabled"))
                .help(key.endsWith("Color") ? "沿程渐变与此基础色相乘" : "沿每道电弧从起点到终点插值")
                .defaultValue(value(defaults, key)).build());
        }
        p.add(ParamSpec.builder("gradientRatio", "起始色占比（%）", ParamType.FLOAT).group("颜色与亮度")
            .visibleWhen(s -> s.getBool("gradientEnabled")).min(0).max(100).step(1).reload(ReloadMode.HOT)
            .help("比例越高，起始色覆盖越多；50% 保持原渐变")
            .defaultValue(value(defaults, "gradientRatio")).build());
        // Group order is deliberate; retain field order inside each group.
        var groups=List.of("模式与预设","粗细","颜色与亮度","分叉","环绕电流","落雷形状","多道落雷","时序","击地放电","质量与随机");
        var appearanceOrder=List.of("currentWidth","strikeWidth","glowWidth","coreColor","glowColor","coreGain","glowGain","gradientEnabled","gradientTarget","gradientStart","gradientEnd","gradientRatio","gradientCoreMode");
        p.sort(Comparator.<ParamSpec>comparingInt(spec -> groups.indexOf(spec.group))
            .thenComparingInt(spec -> appearanceOrder.contains(spec.key)?appearanceOrder.indexOf(spec.key):100));
        ParamRegistry.register(new SkillSchema(ID, "闪电与电流", p));
        installPresetListener();
    }

    private static LightningRecipe loadResource(String name) {
        try (var in = LightningSkillSchema.class.getResourceAsStream("/assets/vfxworkbench/lightning/" + name)) {
            if (in == null) throw new IllegalStateException("Missing lightning recipe: " + name);
            return LightningRecipe.parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("无法读取闪电配方：" + name, ex);
        }
    }

    private static String value(LightningRecipe recipe, String key) {
        try { return String.valueOf(LightningRecipe.class.getField(key).get(recipe)); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }

    private static void enumeration(List<ParamSpec> p, LightningRecipe defaults, String key, String label, List<String> options) {
        p.add(ParamSpec.builder(key, label, ParamType.ENUM).group(group(key)).visibleWhen(s -> visible(key, s))
            .enumValues(options).defaultValue(value(defaults, key)).build());
    }

    private static void toggle(List<ParamSpec> p, LightningRecipe defaults, String key, String label) {
        p.add(ParamSpec.builder(key, label, ParamType.BOOL).group(group(key)).visibleWhen(s -> visible(key, s))
            .defaultValue(value(defaults, key)).build());
    }

    private static void number(List<ParamSpec> p, LightningRecipe defaults, String key, String label, double lo, double hi, boolean integer) {
        p.add(ParamSpec.builder(key, label, integer ? ParamType.INT : ParamType.FLOAT).group(group(key))
            .visibleWhen(s -> visible(key, s)).min(lo).max(hi).step(integer ? 1 : .001).intStep(integer)
            .help(help(key)).defaultValue(value(defaults, key)).build());
    }

    private static String help(String key) {
        return switch (key) {
            case "objectRadius" -> "只用于 HTML 工作台参考物体；导出保留，不改变电弧几何";
            case "orbitExtendSpeed" -> "弧头沿中心线延长；噪声流速独立";
            case "throttle" -> "每个脉冲出现的概率";
            case "rainSpread", "rainDepthSpread" -> "集束布局为椭圆直径；范围布局使用各自分布范围";
            case "rainMaxBranches" -> "每道落雷的分叉总数硬上限";
            case "glowWidth" -> "独立控制光晕宽度；光晕亮度设为 0 可关闭光晕";
            case "progressionTicks" -> "先行通道到达终点的时间；20 tick = 1 秒";
            case "durationTicks" -> "从触发起计的总寿命，至少比先行时间长 2 tick";
            case "impactDuration" -> "从主干到达落点开始计时；20 tick = 1 秒";
            default -> "";
        };
    }

    private static String group(String key) {
        if(Set.of("mode","preset").contains(key))return "模式与预设";
        if(Set.of("currentWidth","strikeWidth","glowWidth").contains(key))return "粗细";
        if(key.startsWith("gradient")||Set.of("coreColor","glowColor","coreGain","glowGain").contains(key))return "颜色与亮度";
        if(key.startsWith("branch")||key.startsWith("rainMaxBranch")||key.equals("rainBranchProbability")||Set.of("maxBranchDepth").contains(key))return "分叉";
        if(key.startsWith("rain"))return "多道落雷";
        if(key.startsWith("impact"))return "击地放电";
        if(Set.of("progressionTicks","durationTicks","restrikeCount","flicker","pulseInterval","throttle").contains(key))return "时序";
        if(Set.of("seed","samples","strikeGenerations","maxSegments","objectRadius").contains(key))return "质量与随机";
        if(key.startsWith("strike"))return "落雷形状";
        return "环绕电流";
    }

    private static boolean visible(String key, ParamState s) {
        String mode=s.get("mode");boolean orbit=mode.equals("orbit"),rain=mode.equals("rain");
        if(key.equals("strikeHeight"))return !orbit && !s.get("triggerHeightMode").equals("absoluteWorld");
        if(key.startsWith("rain"))return rain;
        if(key.startsWith("impact"))return rain&&(key.equals("impactEnabled")||s.getBool("impactEnabled"));
        if(key.equals("maxBranchDepth")||key.equals("branchLimit"))return mode.equals("strike");
        if(key.startsWith("branch")||key.startsWith("strike")||Set.of("maxSegments","progressionTicks","durationTicks","restrikeCount","flicker").contains(key))return !orbit;
        if(key.startsWith("orbit")||Set.of("strands","samples","objectRadius","rodLength","rodRadius","emitterSpread","arcLength","lengthRandomness","pulseInterval","throttle","currentWidth").contains(key))return orbit&&orbitVisible(key,s.get("orbitAnimation"));
        return true;
    }

    private static boolean orbitVisible(String key, String animation) {
        if (key.equals("orbitRadius")) return animation.equals("ring");
        if (Set.of("rodLength", "rodRadius", "emitterSpread").contains(key) && animation.equals("ring")) return false;
        return !animation.equals("spektr") || !Set.of("orbitExtendSpeed", "orbitDirection", "emitterSpread", "rodRadius").contains(key);
    }

    private static void syncSelection(ParamState s) {
        previousPreset = s.get("preset");
        previousRainStyle = s.get("rainStyle");
    }

    private static void installPresetListener() {
        var s = ParamRegistry.state(ID);
        syncSelection(s);
        Runnable changed = () -> {
            if (applying) return;
            applying = true;
            try {
                if (!s.get("preset").equals(previousPreset) && !s.get("preset").equals("custom")) {
                    putRecipe(s, loadResource("presets/" + s.get("preset") + ".json"));
                    if(s.get("preset").equals("tribulation")) {
                        s.set("triggerPlacement", "forward");
                        s.set("triggerDistance", "90");
                    }
                } else {
                    if (!s.get("rainStyle").equals(previousRainStyle) && s.get("rainStyle").equals("cluster")) {
                        for (String item : List.of("rainCount=9", "rainSpread=3", "rainDepthSpread=2.4", "rainStagger=0.22", "rainMaxBranchDepth=0", "rainMaxBranches=0", "rainBranchProbability=0")) {
                            String[] entry = item.split("=");
                            s.set(entry[0], entry[1]);
                        }
                    }
                }
                if (s.getDouble("durationTicks") < s.getDouble("progressionTicks") + 2)
                    s.setDouble("durationTicks", s.getDouble("progressionTicks") + 2);
                syncSelection(s);
            } finally { applying = false; }
        };
        s.onHotChange(changed);
        s.onResourceChange(changed);
    }

    private static void putRecipe(ParamState state, LightningRecipe recipe) {
        // Prepare the complete map before mutating the live state.
        var values = new LinkedHashMap<String, String>();
        for (var spec : state.schema.params)
            if (!spec.key.equals("preset") && !HOST_FIELDS.contains(spec.key)) values.put(spec.key, value(recipe, spec.key));
        state.load(values);
    }

    /** Validate everything before applying; imported cluster values never invoke UI preset baselines. */
    public static LightningRecipe importRecipe(String json) {
        var input = JsonParser.parseString(json);
        if (!input.isJsonObject()) throw new IllegalArgumentException("JSON 必须是参数对象");
        return importRecipe(input.getAsJsonObject());
    }

    public static LightningRecipe importRecipe(JsonObject root) {
        JsonObject clean = root.deepCopy();
        JsonObject input = clean.has("values") ? clean.getAsJsonObject("values") : clean;
        var host = new LinkedHashMap<String,String>();
        if(input.has("triggerPlacement")) {
            String placement=input.remove("triggerPlacement").getAsString();
            if(!Set.of("self","forward").contains(placement))throw new IllegalArgumentException("无效触发落点");
            host.put("triggerPlacement",placement);
        }
        if(input.has("triggerDistance")) {
            double distance=input.remove("triggerDistance").getAsDouble();
            LightningPlacement.validateDistance(distance);
            host.put("triggerDistance",Double.toString(distance));
        }
        if(input.has("triggerHeightMode")) {
            String heightMode=input.remove("triggerHeightMode").getAsString();
            if(!Set.of("relativeGround","absoluteWorld").contains(heightMode))throw new IllegalArgumentException("无效高度基准");
            host.put("triggerHeightMode",heightMode);
        }
        if(input.has("triggerStartY")) {
            double y=input.remove("triggerStartY").getAsDouble();
            if(!Double.isFinite(y)||y < -2048||y > 8192)throw new IllegalArgumentException("起点 Y 必须在 -2048–8192 之间");
            host.put("triggerStartY",Double.toString(y));
        }
        LightningRecipe parsed = LightningRecipe.parse(clean);
        var s = ParamRegistry.state(ID);
        if (s == null) throw new IllegalStateException("Lightning schema is not registered");
        applying = true;
        try {
            putRecipe(s, parsed);
            s.loadAtomic(host);
            s.set("preset", "custom");
            syncSelection(s);
        } finally { applying = false; }
        return parsed;
    }

    public static Path skillRecipeFile(Path gameDirectory) {
        return gameDirectory.resolve("config").resolve("lightninglab").resolve("recipe.json");
    }

    public static LightningRecipe importRecipe(Path jsonFile) throws java.io.IOException {
        return importRecipe(Files.readString(jsonFile));
    }

    /** Actual layer tint, before brightness and scene compositing, shared with the renderer. */
    public static int previewColor(ParamState state, boolean halo, double along) {
        return LightningAppearance.materialColor(state.get(halo ? "glowColor" : "coreColor"),
            LightningAppearance.usesGradient(state.getBool("gradientEnabled"), state.get("gradientTarget"), halo),
            state.get("gradientStart"), state.get("gradientEnd"), state.getDouble("gradientRatio"), along);
    }

    /** Typed raw skill JSON; legacy imports receive the defaults for new gradient fields. */
    public static String exportRecipe() { return GSON.toJson(recipe()); }

    public static LightningRecipe recipe() {
        var values = new JsonObject();
        for (var entry : ParamRegistry.state(ID).snapshot().entrySet())
            if(!HOST_FIELDS.contains(entry.getKey()))values.addProperty(entry.getKey(), entry.getValue());
        return LightningRecipe.parse(values);
    }
}
