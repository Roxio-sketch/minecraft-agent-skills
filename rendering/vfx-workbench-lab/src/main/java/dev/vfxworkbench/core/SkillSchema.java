package dev.vfxworkbench.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A named collection of parameter specs belonging to one skill. */
public final class SkillSchema {
    public final String id;          // e.g. "water-ripples"
    public final String name;        // e.g. "Water Ripples"
    public final List<ParamSpec> params;
    private final Map<String, ParamSpec> byKey;

    public SkillSchema(String id, String name, List<ParamSpec> params) {
        this.id = id;
        this.name = name;
        this.params = List.copyOf(params);
        Map<String, ParamSpec> map = new LinkedHashMap<>();
        for (ParamSpec p : params) map.put(p.key, p);
        this.byKey = Map.copyOf(map);
    }

    public ParamSpec get(String key) { return byKey.get(key); }
    public boolean contains(String key) { return byKey.containsKey(key); }

    public static SkillSchema of(String id, String name, ParamSpec... params) {
        List<ParamSpec> list = new ArrayList<>();
        for (ParamSpec p : params) list.add(p);
        return new SkillSchema(id, name, list);
    }
}
