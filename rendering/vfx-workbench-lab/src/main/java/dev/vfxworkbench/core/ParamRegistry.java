package dev.vfxworkbench.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central registry: skills declare their schema once; the workbench UI reads
 *  it and renders controls auto-generated from ParamSpec. */
public final class ParamRegistry {
    private static final Map<String, SkillSchema> SCHEMAS = new LinkedHashMap<>();
    private static final Map<String, ParamState> STATES = new LinkedHashMap<>();

    private ParamRegistry() {}

    public static synchronized void register(SkillSchema schema) {
        SCHEMAS.put(schema.id, schema);
        STATES.putIfAbsent(schema.id, new ParamState(schema));
    }

    public static SkillSchema schema(String id) { return SCHEMAS.get(id); }
    public static ParamState state(String id) { return STATES.get(id); }
    public static List<SkillSchema> skills() { return new ArrayList<>(SCHEMAS.values()); }
    public static boolean has(String id) { return SCHEMAS.containsKey(id); }
}
