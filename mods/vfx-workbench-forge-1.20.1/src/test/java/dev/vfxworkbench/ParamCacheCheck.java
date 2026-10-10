package dev.vfxworkbench;

import dev.vfxworkbench.core.*;
import dev.vfxworkbench.effects.water.WaterSkillSchema;
import java.util.Map;

final class ParamCacheCheck {
    static void run() {
        var s=new ParamState(ParamRegistry.schema(WaterSkillSchema.ID));
        s.getDouble("speed");s.getFloat("speed");s.getColor("color");
        long revision=s.revision();s.set("speed","0.123456789");s.set("color","#123abc");
        if(s.revision()<=revision||s.getDouble("speed")!=Double.parseDouble("0.123456789")||s.getFloat("speed")!=(float)Double.parseDouble("0.123456789")||s.getColor("color")!=0x123abc)throw new AssertionError("stale set cache");
        s.loadAtomic(Map.of("speed","0.4","color","#ffffff"));
        if(s.getFloat("speed")!=(float).4||s.getColor("color")!=0xffffff)throw new AssertionError("stale atomic cache");
        s.resetToDefaults();
        if(s.getDouble("speed")!=Double.parseDouble(s.get("speed"))||s.getColor("color")!=Integer.parseInt(s.get("color").substring(1),16))throw new AssertionError("stale reset cache");
        s.set("color","bad");if(s.getColor("color")!=0x08616f)throw new AssertionError("color fallback changed");
        s.set("color","#335577");if(s.getColor("color")!=0x335577)throw new AssertionError("fallback poisoned cache");
        System.out.println("PASS: typed parameter caches invalidate on edits, atomic imports and defaults; float conversion and color fallback preserved");
    }
}
